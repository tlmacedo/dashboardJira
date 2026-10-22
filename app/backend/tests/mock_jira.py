"""Servidor Jira SIMULADO (Cloud /rest/api/3 e Data Center /rest/api/2) para testes de integração e carga.

Não é o Jira real: imita o formato documentado das respostas e alguns comportamentos conhecidos
(paginação por nextPageToken/startAt, bulk fetch de changelog com duplicatas, 429 com Retry-After,
503 intermitente, JQL mínimo, autenticação Basic/Bearer, changelog truncado no DC, `key in (...)` estrito).
Tudo é determinístico para um mesmo `MockConfig(seed=...)`.

Uso típico:

    mock = MockJira(MockConfig(flavor="cloud", n_issues=60))
    client = JiraClient("cloud", mock.base_url, mock.token, mock.email, transport=mock.transport())

ou, para falar HTTP de verdade (thread uvicorn em porta livre): `with MockServer(mock) as url: ...`.
"""
from __future__ import annotations

import base64
import json
import random
import re
import socket
import threading
import time
import zlib
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import httpx
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

PROJECTS = ["PAY", "PLT", "CX"]
ROSTER = {
    "PAY": ["ana.souza", "bruno.alves", "carla.mendes"],
    "PLT": ["igor.martins", "julia.ramos", "kleber.dias"],
    "CX": ["nadia.cruz", "otavio.braga", "paula.teles"],
}
DISPLAY = {
    "ana.souza": "Ana Souza", "bruno.alves": "Bruno Alves", "carla.mendes": "Carla Mendes",
    "igor.martins": "Igor Martins", "julia.ramos": "Júlia Ramos", "kleber.dias": "Kléber Dias",
    "nadia.cruz": "Nádia Cruz", "otavio.braga": "Otávio Braga", "paula.teles": "Paula Teles",
    "ex.silva": "Ex Silva (desligado)", "marcos.lima": "Marcos Lima",
}
FLOW = ["Backlog", "A Fazer", "Em Andamento", "Em Revisão", "Em Teste", "Concluído"]
CATEGORY = {"Backlog": "new", "A Fazer": "new", "Em Andamento": "indeterminate", "Em Revisão": "indeterminate",
            "Em Teste": "indeterminate", "Bloqueado": "indeterminate", "Concluído": "done", "Cancelado": "done"}
STATUS_ID = {n: str(10000 + i) for i, n in enumerate(list(CATEGORY) + ["Aguardando QA"])}
ENDS = ["Backlog", "A Fazer", "Em Andamento", "Em Revisão", "Em Teste", "Bloqueado", "Concluído", "Cancelado"]
END_W = [10, 10, 20, 8, 7, 5, 32, 8]
LABELS = ["backend", "pix", "review", "urgente", "skill:backend", "regressao", "critico"]
SP_FIELD, SPRINT_FIELD, FLAG_FIELD, EPIC_FIELD = "customfield_10016", "customfield_10020", "customfield_10021", "customfield_10014"
TRACKED_IDS = {"status", "assignee", "duedate", "labels", "timeoriginalestimate", "priority", "resolution"}

DEFAULT_EMAIL, DEFAULT_TOKEN = "svc@exemplo.com", "token-secreto"


@dataclass
class MockConfig:
    flavor: str = "cloud"                 # cloud | dc
    n_issues: int = 90                    # issues "de nível superior" (reviews e épicos vêm além)
    seed: int = 7
    now: datetime | None = None           # instante "agora" do dataset (UTC ingênuo); padrão: hora cheia atual
    user_tz: str = "America/Sao_Paulo"    # fuso do usuário do Jira (JQL e datas são interpretados nele)
    date_offset_minutes: int | None = None  # offset com que as datas são RENDERIZADAS; None = o do user_tz
    email: str = DEFAULT_EMAIL
    token: str = DEFAULT_TOKEN
    require_auth: bool = True
    with_components: bool = False         # sorteia componentes pix/cartoes
    checklist_review_ratio: float = 0.5   # fração de Checklists que ganham subtarefa Review
    # paginação / limites do servidor
    search_page_cap: int = 100            # teto de maxResults na busca
    bulk_page_histories: int = 0          # >0: pagina o bulkfetch por nº de históricos (0 = tudo numa página)
    worklog_page_cap: int = 1000
    # comportamentos "sujos"
    dup_histories: bool = True            # históricos duplicados (bulkfetch Cloud e changelog DC)
    dc_changelog_cap: int = 0             # >0: expand=changelog da busca DC vem truncado a N históricos
    dc_changelog_endpoint: bool = True    # DC expõe /issue/{key}/changelog?
    dc_assignee_name_only: bool = False   # DC: item de assignee só com fromString/toString (nome de exibição)
    anonymous_author_ratio: float = 0.05  # fração de históricos sem autor (automação)
    strict_key_in: bool = True            # key in (...) com chave inexistente -> 400 (como o Jira Server)
    # falhas periódicas (determinísticas, por contador de requisições à API, exceto /myself e /field)
    rate_limit_every: int = 0             # a cada N requisições: 429 + Retry-After
    retry_after: float = 1.0
    unavailable_every: int = 0            # a cada M requisições: 503 (sem Retry-After)


@dataclass
class MHist:
    id: str
    at: datetime
    author: str | None
    items: list[dict]                     # {field, from, to} em valores neutros


@dataclass
class MWork:
    id: int
    author: str | None
    started: datetime
    seconds: int


@dataclass
class MIssue:
    id: int
    key: str
    project: str
    type: str
    summary: str
    created: datetime
    status: str
    priority: str = "Medium"
    resolution: str | None = None
    resolved: datetime | None = None
    assignee: str | None = None
    reporter: str | None = None
    due: date | None = None
    labels: list = field(default_factory=list)
    components: list = field(default_factory=list)
    parent: str | None = None
    epic: str | None = None
    estimate: int | None = None
    story_points: float | None = None
    flagged: bool = False
    histories: list[MHist] = field(default_factory=list)
    worklogs: list[MWork] = field(default_factory=list)
    updated: datetime | None = None
    old_keys: list = field(default_factory=list)
    deleted: bool = False
    extra_status_category: str | None = None  # para status fora da tabela CATEGORY

    @property
    def spent(self) -> int:
        return sum(w.seconds for w in self.worklogs)


def _fmt_item_dt(dt: datetime, offset_min: int) -> str:
    loc = dt + timedelta(minutes=offset_min)
    sign = "+" if offset_min >= 0 else "-"
    a = abs(offset_min)
    return f"{loc:%Y-%m-%dT%H:%M:%S}.{loc.microsecond // 1000:03d}{sign}{a // 60:02d}{a % 60:02d}"


class MockJira:
    """Estado do Jira simulado + regras. O app FastAPI (`.app`) apenas expõe este estado por HTTP."""

    def __init__(self, cfg: MockConfig | None = None):
        self.cfg = cfg or MockConfig()
        self.rng = random.Random(self.cfg.seed)
        self.now = self.cfg.now or datetime.utcnow().replace(minute=0, second=0, microsecond=0)
        self.clock = datetime.utcnow          # usada pelas mutações (touch/transition/...)
        self.issues: list[MIssue] = []
        self.calls: list[dict] = []
        self.faults: list[dict] = []
        self._lock = threading.Lock()
        self._counter = 0
        self._hist_seq = 0
        self._wl_seq = 50000
        self._next_id = 10000
        self._proj_n = {"PAY": 100, "PLT": 200, "CX": 300}
        self.tz = ZoneInfo(self.cfg.user_tz)
        self._generate()
        self.app = create_app(self)

    # ------------------------------------------------------------------ propriedades
    @property
    def flavor(self) -> str:
        return self.cfg.flavor

    @property
    def email(self) -> str:
        return self.cfg.email

    @property
    def token(self) -> str:
        return self.cfg.token

    @property
    def base_url(self) -> str:
        return "http://jira.simulado"

    @property
    def api(self) -> str:
        return "/rest/api/3" if self.flavor == "cloud" else "/rest/api/2"

    @property
    def offset_min(self) -> int:
        if self.cfg.date_offset_minutes is not None:
            return self.cfg.date_offset_minutes
        return int(self.tz.utcoffset(self.now.replace(tzinfo=timezone.utc)).total_seconds() // 60)

    def transport(self) -> httpx.MockTransport:
        return sync_transport(self.app)

    # ------------------------------------------------------------------ geração do dataset
    def _hid(self) -> str:
        self._hist_seq += 1
        return str(900000 + self._hist_seq)

    def _make(self, typ: str, project: str, *, parent: str | None = None, created: datetime | None = None,
              end: str | None = None, epic: str | None = None) -> MIssue:
        rng = self.rng
        self._proj_n[project] += 1
        iid = self._next_id
        self._next_id += 1
        created = created or (self.now - timedelta(days=rng.randint(5, 120), hours=rng.randint(0, 23)))
        avail_h = max(2.0, (self.now - created).total_seconds() / 3600 - 1)
        roster = ROSTER[project]
        end = end or rng.choices(ENDS, END_W)[0]
        it = MIssue(id=iid, key=f"{project}-{self._proj_n[project]}", project=project, type=typ,
                    summary=f"{typ} {project} #{iid}: tarefa simulada", created=created, status="Backlog",
                    priority=rng.choice(["Highest", "High", "Medium", "Medium", "Low"]),
                    reporter=rng.choice(roster), parent=parent, epic=epic)
        # --- responsável
        r = rng.random()
        initial = None
        later: list[tuple[str | None, str | None]] = []  # (de, para) em histórico
        if r < 0.10:
            pass
        elif r < 0.60:
            initial = rng.choice(roster)
        elif r < 0.80:
            later.append((None, rng.choice(roster)))
        else:
            first = "ex.silva" if rng.random() < 0.15 else rng.choice(roster)
            initial = first
            later.append((first, rng.choice([x for x in roster if x != first])))
        it.assignee = later[-1][1] if later else initial
        # --- caminho de status
        if end == "Bloqueado":
            seq = FLOW[1:3] + ["Bloqueado"]
        elif end == "Cancelado":
            seq = FLOW[1:rng.randint(1, 3)] + ["Cancelado"]
        elif end == "Backlog":
            seq = []
        else:
            seq = FLOW[1:FLOW.index(end) + 1]
        if end == "Concluído" and rng.random() < 0.12:
            seq += ["Em Andamento", "Concluído"]
        step_cap = min(70.0, avail_h / (len(seq) + 3))
        t = created
        hs: list[MHist] = []
        prev = "Backlog"
        for st in seq:
            t = t + timedelta(hours=rng.uniform(1, max(1.5, step_cap)))
            items = [{"field": "status", "from": prev, "to": st}]
            if st in ("Concluído", "Cancelado"):
                res = "Concluído" if st == "Concluído" else "Cancelado"
                items.append({"field": "resolution", "from": None, "to": res})
                it.resolution, it.resolved = res, t
            elif it.resolution:  # reabertura
                items.append({"field": "resolution", "from": it.resolution, "to": None})
                it.resolution, it.resolved = None, None
            hs.append(MHist(self._hid(), t, self._author(it), items))
            prev = st
        it.status = prev
        # --- responsável em histórico
        for frm, to in later:
            at = created + timedelta(hours=rng.uniform(0.2, min(48, avail_h / 2)))
            hs.append(MHist(self._hid(), at, rng.choice(roster), [{"field": "assignee", "from": frm, "to": to}]))
        # --- vencimento
        if rng.random() < 0.7:
            due = (created + timedelta(days=rng.randint(5, 30))).date()
            it.due = due
            for _ in range(rng.choice([1, 1, 2]) if rng.random() < 0.6 else 0):
                new = due + timedelta(days=rng.randint(2, 12))
                at = created + timedelta(hours=rng.uniform(1, avail_h * 0.9))
                hs.append(MHist(self._hid(), at, self._author(it), [{"field": "duedate", "from": due.isoformat(), "to": new.isoformat()}]))
                due = new
            it.due = due
        # --- labels
        lab = sorted(rng.sample(LABELS, rng.randint(0, 3)))
        if rng.random() < 0.3:
            extra = rng.choice([x for x in LABELS if x not in lab] or ["extra"])
            at = created + timedelta(hours=rng.uniform(1, avail_h * 0.9))
            new = sorted(lab + [extra]) if rng.random() < 0.6 or not lab else sorted(lab[1:])
            hs.append(MHist(self._hid(), at, self._author(it), [{"field": "labels", "from": list(lab), "to": list(new)}]))
            lab = new
        it.labels = lab
        # --- estimativa
        if rng.random() < 0.8:
            it.estimate = 3600 * rng.randint(1, 16)
            if rng.random() < 0.15:
                new = int(it.estimate * rng.choice([1.5, 2]))
                at = created + timedelta(hours=rng.uniform(1, avail_h * 0.9))
                hs.append(MHist(self._hid(), at, self._author(it), [{"field": "timeoriginalestimate", "from": str(it.estimate), "to": str(new)}]))
                it.estimate = new
        # --- prioridade
        if rng.random() < 0.10:
            new = rng.choice(["Highest", "High", "Medium", "Low"])
            if new != it.priority:
                at = created + timedelta(hours=rng.uniform(1, avail_h * 0.9))
                hs.append(MHist(self._hid(), at, self._author(it), [{"field": "priority", "from": it.priority, "to": new}]))
                it.priority = new
        # --- extras de campos
        it.story_points = float(rng.choice([1, 2, 3, 5, 8])) if rng.random() < 0.5 else None
        it.flagged = it.status == "Bloqueado"
        if self.cfg.with_components and rng.random() < 0.6:
            it.components = [rng.choice(["pix", "cartoes"])]
        # --- worklogs
        if it.status not in ("Backlog", "A Fazer") or rng.random() < 0.2:
            for _ in range(rng.randint(0, 4)):
                self._wl_seq += 1
                st = created + timedelta(hours=rng.uniform(1, avail_h))
                it.worklogs.append(MWork(self._wl_seq, it.assignee or rng.choice(roster), st, 1800 * rng.randint(1, 8)))
        # ordena históricos por data e renumera ids em ordem cronológica
        hs.sort(key=lambda h: h.at)
        for n, h in enumerate(hs):
            h.id = str(900000 + iid * 10 + n)
        it.histories = hs
        it.updated = max([created] + [h.at for h in hs] + [w.started for w in it.worklogs])
        return it

    def _author(self, it: MIssue) -> str | None:
        if self.rng.random() < self.cfg.anonymous_author_ratio:
            return None
        return self.rng.choice(ROSTER[it.project] + ["marcos.lima"])

    def _generate(self) -> None:
        for p in PROJECTS:  # um épico por projeto
            ep = self._make("Epic", p, created=self.now - timedelta(days=150), end="Em Andamento")
            ep.histories, ep.worklogs, ep.assignee, ep.due = [], [], None, None
            ep.key = f"{p}-1"
            self._proj_n[p] -= 1
            ep.updated = ep.created
            self.issues.append(ep)
        epics = {i.project: i.key for i in self.issues}
        for i in range(self.cfg.n_issues):
            p = PROJECTS[i % 3]
            typ = self.rng.choices(["Task", "Bug", "Story", "Checklist"], [40, 20, 20, 20])[0]
            ep = epics[p] if typ in ("Task", "Story") and self.rng.random() < 0.25 else None
            it = self._make(typ, p, epic=ep, parent=ep)
            self.issues.append(it)
            if typ == "Checklist" and self.rng.random() < self.cfg.checklist_review_ratio:
                rv = self._make("Review", p, parent=it.key, created=it.created + timedelta(hours=self.rng.randint(1, 30)))
                rv.labels = list(it.labels)
                self.issues.append(rv)

    # ------------------------------------------------------------------ consultas
    def by_key(self, key: str, include_deleted: bool = False) -> MIssue | None:
        for i in self.issues:
            if (i.key == key or key in i.old_keys) and (include_deleted or not i.deleted):
                return i
        return None

    def live(self) -> list[MIssue]:
        return [i for i in self.issues if not i.deleted]

    def category(self, it: MIssue) -> str:
        return it.extra_status_category or CATEGORY.get(it.status, "indeterminate")

    def expected(self) -> dict:
        """Contagens esperadas depois de uma carga completa (já sem as duplicatas)."""
        ev: dict[str, int] = {}
        tr = ac = wl = 0
        for i in self.live():
            for h in i.histories:
                for x in h.items:
                    f = x["field"]
                    if f == "status":
                        tr += 1
                    elif f == "assignee":
                        ac += 1
                    else:
                        ev[f] = ev.get(f, 0) + 1
            wl += len(i.worklogs)
        live = self.live()
        return {"issues": len(live), "transitions": tr, "assignee_changes": ac, "events": ev, "events_total": sum(ev.values()),
                "worklogs": wl, "abertas": sum(1 for i in live if self.category(i) != "done"),
                "reviews": sum(1 for i in live if i.type == "Review"),
                "por_projeto": {p: sum(1 for i in live if i.project == p) for p in PROJECTS}}

    # ------------------------------------------------------------------ mutações (simulam o Jira mudando)
    def _bump(self, it: MIssue, at: datetime | None = None) -> datetime:
        at = at or self.clock()
        it.updated = at
        return at

    def transition(self, key: str, to: str, author: str | None = "ana.souza", category: str | None = None) -> None:
        it = self.by_key(key)
        at = self._bump(it)
        items = [{"field": "status", "from": it.status, "to": to}]
        if to in ("Concluído", "Cancelado"):
            it.resolution, it.resolved = ("Concluído" if to == "Concluído" else "Cancelado"), at
            items.append({"field": "resolution", "from": None, "to": it.resolution})
        it.histories.append(MHist(self._hid() + "m", at, author, items))
        it.status = to
        if category:
            it.extra_status_category = category

    def reassign(self, key: str, ref: str | None, author: str | None = "marcos.lima") -> None:
        it = self.by_key(key)
        at = self._bump(it)
        it.histories.append(MHist(self._hid() + "m", at, author, [{"field": "assignee", "from": it.assignee, "to": ref}]))
        it.assignee = ref

    def set_due(self, key: str, new: date, author: str | None = "ana.souza") -> None:
        it = self.by_key(key)
        at = self._bump(it)
        it.histories.append(MHist(self._hid() + "m", at, author, [{"field": "duedate", "from": it.due.isoformat() if it.due else None, "to": new.isoformat()}]))
        it.due = new

    def add_worklog(self, key: str, seconds: int, author: str = "ana.souza") -> MWork:
        it = self.by_key(key)
        self._wl_seq += 1
        w = MWork(self._wl_seq, author, self.clock(), seconds)
        it.worklogs.append(w)
        self._bump(it)
        return w

    def delete_issue(self, key: str) -> None:
        self.by_key(key).deleted = True

    def move_issue(self, key: str, new_key: str) -> None:
        it = self.by_key(key)
        it.old_keys.append(it.key)
        it.key = new_key
        it.project = new_key.split("-")[0]
        self._bump(it)

    def fail_next(self, path_contains: str, status: int, *, times: int = 1, retry_after: float | None = None,
                  method: str | None = None) -> None:
        self.faults.append({"match": path_contains, "status": status, "times": times, "retry_after": retry_after, "method": method})

    def calls_to(self, fragment: str) -> list[dict]:
        return [c for c in self.calls if fragment in c["path"]]

    # ------------------------------------------------------------------ renderização por variante
    def user(self, ref: str | None) -> dict | None:
        if not ref:
            return None
        name = DISPLAY.get(ref, ref)
        if self.flavor == "cloud":
            return {"accountId": ref, "displayName": name, "emailAddress": f"{ref}@exemplo.com", "active": ref != "ex.silva",
                    "accountType": "atlassian"}
        return {"name": ref, "key": "JIRAUSER" + str(zlib.crc32(ref.encode()) % 100000), "displayName": name,
                "emailAddress": f"{ref}@exemplo.com", "active": ref != "ex.silva"}

    def dt(self, d: datetime | None) -> str | None:
        return _fmt_item_dt(d, self.offset_min) if d else None

    def render_item(self, x: dict) -> dict:
        f = x["field"]
        a, b = x["from"], x["to"]
        if f == "status":
            return {"field": "status", "fieldtype": "jira", "fieldId": "status", "from": STATUS_ID.get(a), "fromString": a,
                    "to": STATUS_ID.get(b), "toString": b}
        if f == "assignee":
            base = {"field": "assignee", "fieldtype": "jira", "fieldId": "assignee",
                    "fromString": DISPLAY.get(a, a) if a else None, "toString": DISPLAY.get(b, b) if b else None}
            if self.flavor == "dc" and self.cfg.dc_assignee_name_only:
                return {**base, "from": None, "to": None}
            return {**base, "from": a, "to": b}
        if f == "duedate":
            if self.flavor == "cloud":  # Cloud: datas ISO em fromString/toString
                return {"field": "duedate", "fieldtype": "jira", "fieldId": "duedate", "from": None, "fromString": a, "to": None, "toString": b}
            loc = lambda s: date.fromisoformat(s).strftime("%d/%b/%y") if s else None  # DC: from/to ISO, strings no formato de exibição
            return {"field": "duedate", "fieldtype": "jira", "from": a, "fromString": loc(a), "to": b, "toString": loc(b)}
        if f == "labels":
            return {"field": "labels", "fieldtype": "jira", "fieldId": "labels", "from": None, "fromString": " ".join(a) if a else "",
                    "to": None, "toString": " ".join(b) if b else ""}
        if f == "timeoriginalestimate":
            return {"field": "timeoriginalestimate", "fieldtype": "jira", "fieldId": "timeoriginalestimate", "from": a, "fromString": a,
                    "to": b, "toString": b}
        if f == "resolution":
            return {"field": "resolution", "fieldtype": "jira", "fieldId": "resolution", "from": None, "fromString": a, "to": None, "toString": b}
        return {"field": f, "fieldtype": "jira", "fieldId": f, "from": a, "fromString": a, "to": b, "toString": b}

    def render_history(self, h: MHist, only_ids: set[str] | None = None) -> dict:
        items = [self.render_item(x) for x in h.items if only_ids is None or x["field"] in only_ids]
        out = {"id": h.id, "created": self.dt(h.at), "items": items}
        if h.author:
            out["author"] = self.user(h.author)
        return out

    def histories_of(self, it: MIssue, only_ids: set[str] | None = None, dup: bool = False) -> list[dict]:
        out = []
        for n, h in enumerate(it.histories):
            r = self.render_history(h, only_ids)
            if not r["items"]:
                continue
            out.append(r)
            if dup and self.cfg.dup_histories and n % 4 == 0:
                out.append(json.loads(json.dumps(r)))  # duplicata idêntica
        return out

    def render_fields(self, it: MIssue, want: set[str]) -> dict:
        spent = it.spent
        parent = None
        if it.parent:
            p = self.by_key(it.parent)
            parent = {"id": str(p.id) if p else "0", "key": it.parent,
                      "fields": {"summary": p.summary if p else "", "status": {"name": p.status if p else ""},
                                 "issuetype": {"name": p.type if p else "Task"}}}
        allf = {
            "summary": it.summary,
            "issuetype": {"name": it.type, "subtask": it.type == "Review"},
            "priority": {"name": it.priority},
            "status": {"name": it.status, "id": STATUS_ID.get(it.status, "19999"),
                       "statusCategory": {"key": self.category(it), "name": self.category(it)}},
            "resolution": {"name": it.resolution} if it.resolution else None,
            "assignee": self.user(it.assignee), "reporter": self.user(it.reporter),
            "created": self.dt(it.created), "updated": self.dt(it.updated), "resolutiondate": self.dt(it.resolved),
            "duedate": it.due.isoformat() if it.due else None,
            "timeoriginalestimate": it.estimate,
            "timeestimate": max(it.estimate - spent, 0) if it.estimate else None,
            "timespent": spent or None,
            "labels": list(it.labels), "components": [{"name": c} for c in it.components],
            "project": {"key": it.project, "name": it.project},
            SP_FIELD: it.story_points, SPRINT_FIELD: None, FLAG_FIELD: [{"value": "Impediment"}] if it.flagged else None,
        }
        if parent:
            allf["parent"] = parent
        if self.flavor == "dc" and it.epic:
            allf[EPIC_FIELD] = it.epic
            allf.pop("parent", None) if it.type != "Review" else None  # DC: parent só em subtarefas
        if "*all" in want or "*navigable" in want:
            return allf
        return {k: v for k, v in allf.items() if k in want}

    # ------------------------------------------------------------------ JQL mínimo
    def jql_filter(self, jql: str) -> tuple[list[MIssue], str, bool]:
        """Devolve (issues, campo_ordem, desc). Levanta ValueError("msg") para JQL inválido."""
        s = jql.strip()
        order, desc = "updated", False
        m = re.search(r"\s+order\s+by\s+(\w+)(?:\s+(asc|desc))?\s*$", s, re.I)
        if m:
            order, desc = m.group(1).lower(), (m.group(2) or "asc").lower() == "desc"
            s = s[:m.start()]
        pool = self._eval(s.strip())
        return sorted(pool, key=lambda i: (getattr(i, "updated") if order == "updated" else
                                           i.created if order == "created" else i.id, i.id), reverse=desc), order, desc

    def _split_and(self, s: str) -> list[str]:
        parts, depth, cur, i, q = [], 0, "", 0, False
        while i < len(s):
            c = s[i]
            if c == '"':
                q = not q
            if not q:
                depth += (c == "(") - (c == ")")
                if depth == 0 and re.match(r"\s+AND\s+", s[i:], re.I):
                    parts.append(cur)
                    cur = ""
                    i += re.match(r"\s+AND\s+", s[i:], re.I).end()
                    continue
            cur += c
            i += 1
        parts.append(cur)
        return [p.strip() for p in parts if p.strip()]

    def _eval(self, s: str) -> list[MIssue]:
        s = s.strip()
        while s.startswith("(") and s.endswith(")") and self._balanced(s[1:-1]):
            s = s[1:-1].strip()
        parts = self._split_and(s)
        if len(parts) > 1:
            pool = self.live()
            for p in parts:
                ids = {i.id for i in self._eval(p)}
                pool = [i for i in pool if i.id in ids]
            return pool
        atom = parts[0] if parts else ""
        live = self.live()
        if re.fullmatch(r"project\s+is\s+not\s+empty", atom, re.I):
            return live
        m = re.fullmatch(r"project\s+(?:in\s*\(([^)]*)\)|=\s*\"?([\w]+)\"?)", atom, re.I)
        if m:
            ps = {x.strip().strip('"') for x in (m.group(1) or m.group(2)).split(",")}
            return [i for i in live if i.project in ps]
        m = re.fullmatch(r"(?:key|issuekey|issue)\s+in\s*\(([^)]*)\)", atom, re.I)
        if m:
            keys = [x.strip().strip('"') for x in m.group(1).split(",") if x.strip()]
            out, missing = [], []
            for k in keys:
                it = self.by_key(k)
                (out if it else missing).append(it or k)
            if missing and self.cfg.strict_key_in:
                raise ValueError(f"An issue with key '{missing[0]}' does not exist for field 'key'.")
            return out
        m = re.fullmatch(r"updated\s*>=\s*\"(\d{4}-\d{2}-\d{2})(?:[ T](\d{2}:\d{2}))?\"", atom, re.I)
        if m:
            loc = datetime.fromisoformat(f"{m.group(1)} {m.group(2) or '00:00'}").replace(tzinfo=self.tz)
            thr = loc.astimezone(timezone.utc).replace(tzinfo=None)
            return [i for i in live if i.updated.replace(second=0, microsecond=0) >= thr]
        raise ValueError(f"Error in the JQL Query: cláusula não suportada pelo simulador: {atom!r}")

    @staticmethod
    def _balanced(s: str) -> bool:
        d = 0
        for c in s:
            d += (c == "(") - (c == ")")
            if d < 0:
                return False
        return d == 0


# ====================================================================== transporte sync sobre ASGI
def sync_transport(app: FastAPI) -> httpx.MockTransport:
    """httpx.Client é síncrono e ASGITransport é assíncrono: encaminha cada requisição ao app via TestClient."""
    from starlette.testclient import TestClient
    tc = TestClient(app, raise_server_exceptions=False)
    tc.__enter__()

    def handler(req: httpx.Request) -> httpx.Response:
        url = req.url.raw_path.decode()
        hdrs = {k: v for k, v in req.headers.items() if k.lower() not in ("host", "content-length")}
        r = tc.request(req.method, url, headers=hdrs, content=req.read())
        h = {k: v for k, v in r.headers.items() if k.lower() not in ("content-encoding", "content-length", "transfer-encoding")}
        return httpx.Response(r.status_code, headers=h, content=r.content)

    return httpx.MockTransport(handler)


class MockServer:
    """Sobe o app numa thread uvicorn em porta livre (HTTP real em 127.0.0.1)."""

    def __init__(self, mock: MockJira):
        import uvicorn
        s = socket.socket()
        s.bind(("127.0.0.1", 0))
        self.port = s.getsockname()[1]
        s.close()
        self.server = uvicorn.Server(uvicorn.Config(mock.app, host="127.0.0.1", port=self.port, log_level="error"))
        self.thread = threading.Thread(target=self.server.run, daemon=True)

    def __enter__(self) -> str:
        self.thread.start()
        for _ in range(100):
            if self.server.started:
                break
            time.sleep(0.05)
        return f"http://127.0.0.1:{self.port}"

    def __exit__(self, *a):
        self.server.should_exit = True
        self.thread.join(5)


# ====================================================================== app FastAPI
def create_app(mock: MockJira) -> FastAPI:
    app = FastAPI(title="Jira simulado")
    cfg = mock.cfg
    api = mock.api

    def err(status: int, msg: str, headers: dict | None = None) -> JSONResponse:
        return JSONResponse({"errorMessages": [msg], "errors": {}}, status_code=status, headers=headers or {})

    def authed(request: Request) -> bool:
        if not cfg.require_auth:
            return True
        h = request.headers.get("authorization", "")
        if mock.flavor == "cloud":
            want = "Basic " + base64.b64encode(f"{cfg.email}:{cfg.token}".encode()).decode()
        else:
            want = f"Bearer {cfg.token}"
        return h == want

    @app.middleware("http")
    async def gate(request: Request, call_next):
        path = request.url.path
        with mock._lock:
            mock.calls.append({"method": request.method, "path": path, "query": str(request.url.query), "t": time.time()})
        if not authed(request):
            return JSONResponse({"message": "Client must be authenticated to access this resource."}, status_code=401,
                                headers={"WWW-Authenticate": 'Basic realm="Jira"', "X-Seraph-Loginreason": "AUTHENTICATED_FAILED"})
        with mock._lock:
            for f in mock.faults:
                if f["times"] > 0 and f["match"] in path and (not f["method"] or f["method"] == request.method):
                    f["times"] -= 1
                    hd = {"Retry-After": str(f["retry_after"])} if f["retry_after"] is not None else {}
                    return err(f["status"], f"falha injetada ({f['status']})", hd)
            if not path.endswith(("/myself", "/field")):
                mock._counter += 1
                n = mock._counter
                if cfg.rate_limit_every and n % cfg.rate_limit_every == 0:
                    return err(429, "Rate limit exceeded", {"Retry-After": str(cfg.retry_after)})
                if cfg.unavailable_every and n % cfg.unavailable_every == 0:
                    return err(503, "Service Unavailable")
        return await call_next(request)

    # ---------------------------------------------------------------- descoberta
    @app.get(f"{api}/myself")
    def myself():
        u = mock.user("svc.painel") or {}
        return {**u, "displayName": "Conta de serviço", "timeZone": cfg.user_tz, "locale": "pt_BR"}

    @app.get(f"{api}/field")
    def fields():
        base = [{"id": "summary", "name": "Summary"}, {"id": "status", "name": "Status"},
                {"id": SP_FIELD, "name": "Story Points" if mock.flavor == "dc" else "Story point estimate"},
                {"id": SPRINT_FIELD, "name": "Sprint"}, {"id": FLAG_FIELD, "name": "Flagged"}]
        if mock.flavor == "dc":
            base.append({"id": EPIC_FIELD, "name": "Epic Link"})
        return base

    # ---------------------------------------------------------------- busca
    def _page(items: list, start: int, size: int):
        return items[start:start + size]

    if mock.flavor == "cloud":
        @app.post(f"{api}/search/jql")
        async def search_jql(request: Request):
            body = await request.json()
            try:
                issues, _, _ = mock.jql_filter(body.get("jql", ""))
            except ValueError as e:
                return err(400, str(e))
            size = min(int(body.get("maxResults", 50)), cfg.search_page_cap)
            start = 0
            tok = body.get("nextPageToken")
            if tok:
                if not tok.startswith("tok-"):
                    return err(400, "nextPageToken inválido")
                start = int(tok[4:])
            want = set(body.get("fields") or ["id"])
            page = _page(issues, start, size)
            out = {"issues": [{"id": str(i.id), "key": i.key, "fields": mock.render_fields(i, want)} for i in page]}
            if start + size < len(issues):
                out["nextPageToken"] = f"tok-{start + size}"
            else:
                out["isLast"] = True
            return out

        @app.post(f"{api}/changelog/bulkfetch")
        async def bulk(request: Request):
            body = await request.json()
            ids = body.get("issueIdsOrKeys") or []
            fids = body.get("fieldIds") or []
            if not ids or len(ids) > 1000:
                return err(400, "issueIdsOrKeys deve ter entre 1 e 1000 itens")
            if len(fids) > 10:
                return err(400, "fieldIds aceita no máximo 10 campos")
            only = set(fids) & TRACKED_IDS if fids else None
            blocks = []
            for x in ids:
                it = next((i for i in mock.live() if str(i.id) == str(x) or i.key == x), None)
                if it:
                    hs = mock.histories_of(it, only, dup=True)
                    if hs:
                        blocks.append({"issueId": str(it.id), "changeHistories": hs})
            cap = cfg.bulk_page_histories
            start = 0
            tok = body.get("nextPageToken")
            if tok:
                start = int(tok.split("-")[1])
            if not cap:
                return {"issueChangeLogs": blocks}
            # paginação por nº de históricos; o último bloco da página volta repetido no começo da seguinte (duplicata real)
            out, n, k = [], 0, start
            while k < len(blocks) and (n == 0 or n + len(blocks[k]["changeHistories"]) <= cap):
                out.append(blocks[k])
                n += len(blocks[k]["changeHistories"])
                k += 1
            res: dict = {"issueChangeLogs": out}
            if k < len(blocks):
                res["nextPageToken"] = f"bk-{max(k - 1, start + 1) if cfg.dup_histories else k}"
            return res
    else:
        @app.get(f"{api}/search")
        def search(request: Request):
            q = request.query_params
            try:
                issues, _, _ = mock.jql_filter(q.get("jql", ""))
            except ValueError as e:
                return err(400, str(e))
            start = int(q.get("startAt", 0))
            size = min(int(q.get("maxResults", 50)), cfg.search_page_cap)
            want = set((q.get("fields") or "id").split(","))
            expand = (q.get("expand") or "").split(",")
            out = []
            for i in _page(issues, start, size):
                d = {"id": str(i.id), "key": i.key, "fields": mock.render_fields(i, want)}
                if "changelog" in expand:
                    hs = mock.histories_of(i, dup=True)
                    total = len(hs)
                    if cfg.dc_changelog_cap and total > cfg.dc_changelog_cap:
                        hs = hs[:cfg.dc_changelog_cap]
                    d["changelog"] = {"startAt": 0, "maxResults": len(hs), "total": total, "histories": hs}
                out.append(d)
            return {"startAt": start, "maxResults": size, "total": len(issues), "issues": out}

    # ---------------------------------------------------------------- issue
    @app.get(api + "/issue/{key}")
    def issue_get(key: str, request: Request):
        it = mock.by_key(key)
        if not it:
            return err(404, "Issue does not exist or you do not have permission to see it.")
        want = set((request.query_params.get("fields") or "*all").split(","))
        d = {"id": str(it.id), "key": it.key, "fields": mock.render_fields(it, want)}
        if "changelog" in (request.query_params.get("expand") or ""):
            hs = mock.histories_of(it, dup=True)
            d["changelog"] = {"startAt": 0, "maxResults": len(hs), "total": len(hs), "histories": hs}
        return d

    @app.get(api + "/issue/{key}/changelog")
    def issue_changelog(key: str, startAt: int = 0, maxResults: int = 100):
        if mock.flavor == "dc" and not cfg.dc_changelog_endpoint:
            return err(404, "Not Found")
        it = mock.by_key(key)
        if not it:
            return err(404, "Issue does not exist or you do not have permission to see it.")
        hs = mock.histories_of(it, dup=True)
        maxResults = min(maxResults, 100)
        page = hs[startAt:startAt + maxResults]
        key_name = "values" if mock.flavor == "cloud" else "histories"
        return {"startAt": startAt, "maxResults": maxResults, "total": len(hs), "isLast": startAt + maxResults >= len(hs), key_name: page}

    @app.get(api + "/issue/{key}/worklog")
    def worklog(key: str, startAt: int = 0, maxResults: int = 1048576):
        it = mock.by_key(key)
        if not it:
            return err(404, "Issue does not exist or you do not have permission to see it.")
        maxResults = min(maxResults, cfg.worklog_page_cap)
        ws = sorted(it.worklogs, key=lambda w: w.id)
        page = ws[startAt:startAt + maxResults]
        return {"startAt": startAt, "maxResults": maxResults, "total": len(ws),
                "worklogs": [{"id": str(w.id), "issueId": str(it.id), "author": mock.user(w.author), "started": mock.dt(w.started),
                              "timeSpentSeconds": w.seconds, "timeSpent": f"{w.seconds // 60}m"} for w in page]}

    # ---------------------------------------------------------------- escrita
    @app.get(api + "/user/assignable/search")
    def assignable(request: Request):
        q = request.query_params
        it = mock.by_key(q.get("issueKey", ""))
        if not it:
            return err(404, "Issue does not exist")
        users = [mock.user(r) for r in ROSTER[it.project] if r != "ex.silva"]
        ref = q.get("accountId") or q.get("username")
        if ref:
            users = [u for u in users if ref in (u.get("accountId"), u.get("name"))]
        return users

    @app.put(api + "/issue/{key}/assignee")
    async def assign(key: str, request: Request):
        it = mock.by_key(key)
        if not it:
            return err(404, "Issue does not exist or you do not have permission to see it.")
        body = await request.json()
        ref = body.get("accountId") if mock.flavor == "cloud" else body.get("name")
        if ref not in ROSTER[it.project]:
            return err(400, f"Usuário '{ref}' não pode ser atribuído a esta issue")
        mock.reassign(key, ref, author="svc.painel")
        return Response(status_code=204)

    return app
