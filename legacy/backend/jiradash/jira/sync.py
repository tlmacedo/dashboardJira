"""Sincronização Jira → banco: incremental com marca d'água − 5 min, idempotente por id de changelog."""
from __future__ import annotations

import logging
import re
from collections import Counter
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy.orm import Session

from ..config import load_teams
from ..models import (AssigneeChange, ChangeEvent, Issue, Person, Squad, StatusTransition, SyncState, Worklog)
from .client import JiraClient, JiraError

log = logging.getLogger("jiradash.sync")
BASE_FIELDS = ["summary", "issuetype", "priority", "status", "resolution", "assignee", "reporter", "created",
               "updated", "resolutiondate", "duedate", "timeoriginalestimate", "timeestimate", "timespent",
               "labels", "components", "parent", "project"]


# nome do campo no changelog -> nome interno ("Due Date" no DC; "duedate" no Cloud)
TRACKED_FIELDS = {"duedate": "duedate", "Due Date": "duedate", "labels": "labels", "timeoriginalestimate": "timeoriginalestimate",
                  "Original Estimate": "timeoriginalestimate", "priority": "priority", "resolution": "resolution"}


def parse_dt(s: str | None) -> datetime | None:
    """Datas do Jira vêm como 2026-10-01T10:00:00.000+0000; guardamos em UTC sem fuso."""
    if not s:
        return None
    s = s.replace("Z", "+0000")
    if len(s) > 5 and s[-3] == ":":  # +00:00 -> +0000
        s = s[:-3] + s[-2:]
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z"):
        try:
            return datetime.strptime(s, fmt).astimezone(timezone.utc).replace(tzinfo=None)
        except ValueError:
            continue
    raise ValueError(f"data inválida: {s}")


_MESES = {"jan": 1, "fev": 2, "feb": 2, "mar": 3, "abr": 4, "apr": 4, "mai": 5, "may": 5, "jun": 6, "jul": 7, "ago": 8,
          "aug": 8, "set": 9, "sep": 9, "out": 10, "oct": 10, "nov": 11, "dez": 12, "dec": 12}


def norm_date(v: str | None) -> str | None:
    """Normaliza datas de vencimento do changelog para YYYY-MM-DD.

    Vêm como 2026-10-05 (Cloud, DC em from/to), '2026-10-05 00:00:00.0' ou no formato de exibição do
    idioma do servidor ('05/Oct/26', '05/out/26', '05/10/2026'). Valor irreconhecível é guardado como veio."""
    if not v:
        return None
    v = str(v).strip()
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", v)
    if m:
        return f"{m[1]}-{m[2]}-{m[3]}"
    m = re.match(r"(\d{1,2})/([A-Za-zçÇ]{3})[A-Za-zçÇ]*\.?/(\d{2,4})", v)
    if m and m[2].lower() in _MESES:
        y = int(m[3]) + (2000 if len(m[3]) == 2 else 0)
        return f"{y:04d}-{_MESES[m[2].lower()]:02d}-{int(m[1]):02d}"
    m = re.match(r"(\d{1,2})/(\d{1,2})/(\d{4})", v)
    if m:
        return f"{m[3]}-{int(m[2]):02d}-{int(m[1]):02d}"
    return v[:300]


def user_ref(flavor: str, u: dict | None) -> str | None:
    if not u:
        return None
    return u.get("accountId") if flavor == "cloud" else (u.get("name") or u.get("key"))


class Syncer:
    def __init__(self, db: Session, client: JiraClient, field_ids: dict[str, str | None] | None = None):
        self.db, self.client = db, client
        cfg = load_teams()
        self.status_map: dict[str, str] = cfg.get("status_map", {})
        self.squads = {s.id: s for s in db.query(Squad).all()}
        self.fields = field_ids or client.discover_field_ids()
        self.people: dict[str, Person] = {p.jira_ref: p for p in db.query(Person).all()}
        self.unmapped: Counter = Counter()

    # ---- pessoas ----
    def person(self, u: dict | None) -> Person | None:
        ref = user_ref(self.client.flavor, u)
        if not ref:
            return None
        p = self.people.get(ref)
        if p is None:
            p = Person(jira_ref=ref, name=u.get("displayName") or ref, email=u.get("emailAddress"),
                       active=u.get("active", True), role="externo")
            self.db.add(p)
            self.db.flush()
            self.people[ref] = p
        return p

    def squad_for(self, project: str, components: list[str]) -> int | None:
        """Squad cujo componente casa tem prioridade; senão o squad do projeto sem restrição de componente."""
        generic = None
        for s in self.squads.values():
            if project not in (s.projects or []):
                continue
            if not s.components:
                generic = generic if generic is not None else s.id
            elif set(s.components) & set(components):
                return s.id
        return generic

    # ---- uma issue ----
    def upsert_issue(self, raw: dict, histories: list[dict]) -> Issue:
        f = raw["fields"]
        status = f["status"]["name"]
        cat = self.status_map.get(status)
        if cat is None:  # status fora do status_map do YAML: usa a categoria do próprio Jira e avisa
            self.unmapped[status] += 1
            cat = {"new": "todo", "indeterminate": "active", "done": "done"}.get(
                f["status"].get("statusCategory", {}).get("key", ""), "active")
        comps = [c["name"] for c in f.get("components") or []]
        assignee, reporter = self.person(f.get("assignee")), self.person(f.get("reporter"))
        iss = self.db.get(Issue, int(raw["id"]))
        if iss is None:
            iss = Issue(id=int(raw["id"]))
            self.db.add(iss)
        iss.key = raw["key"]
        iss.project = f["project"]["key"]
        iss.summary = (f.get("summary") or "")[:500]
        iss.type = f["issuetype"]["name"]
        iss.priority = (f.get("priority") or {}).get("name", "Medium")
        iss.status, iss.status_category = status, cat
        iss.resolution = (f.get("resolution") or {}).get("name")
        iss.assignee_id = assignee.id if assignee else None
        iss.reporter_id = reporter.id if reporter else None
        iss.created, iss.updated = parse_dt(f["created"]), parse_dt(f["updated"])
        iss.resolved = parse_dt(f.get("resolutiondate"))
        iss.due = datetime.strptime(f["duedate"], "%Y-%m-%d").date() if f.get("duedate") else None
        iss.original_estimate_s, iss.remaining_s = f.get("timeoriginalestimate"), f.get("timeestimate")
        iss.time_spent_s = f.get("timespent") or 0
        sp = self.fields.get("story_points")
        iss.story_points = f.get(sp) if sp else None
        iss.labels, iss.components = f.get("labels") or [], comps
        parent = f.get("parent") or {}
        iss.parent_key = parent.get("key")
        ep = self.fields.get("epic_link")  # DC: o vínculo com o épico é o campo 'Epic Link'
        if parent.get("fields", {}).get("issuetype", {}).get("name") == "Epic":
            iss.epic_key = parent.get("key")
        elif ep and f.get(ep):
            iss.epic_key = f[ep]
        elif not parent:
            iss.epic_key = None  # sem pai e sem Epic Link: não pertence a épico (ou saiu dele)
        fl = self.fields.get("flagged")
        iss.flagged = bool(fl and f.get(fl))
        iss.squad_id = self.squad_for(iss.project, comps)
        iss.deleted_at = None
        self.db.flush()
        self._apply_changelog(iss, histories)
        return iss

    def _apply_changelog(self, iss: Issue, histories: list[dict]) -> None:
        seen = {t.changelog_id for t in iss.transitions} | {a.changelog_id for a in iss.assignee_changes} | {e.changelog_id for e in iss.events}
        for h in histories:
            at = parse_dt(h["created"])
            author_id = (self.person(h.get("author")) or Person(id=None)).id  # autor ausente (automação) -> None
            for n, it in enumerate(h.get("items", [])):
                cid = f"{iss.id}-{h['id']}-{n}"
                if cid in seen:  # duplicatas no bulk fetch / changelog: deduplica pelo id
                    continue
                seen.add(cid)
                fld = it.get("field")
                if fld == "status":
                    iss.transitions.append(StatusTransition(
                        changelog_id=cid, at=at, from_status=it.get("fromString"), to_status=it.get("toString"), author_id=author_id))
                elif fld in TRACKED_FIELDS:
                    name = TRACKED_FIELDS[fld]
                    if name == "duedate":  # Cloud: datas em fromString/toString; DC: from/to ISO e strings localizadas
                        fv = norm_date(it.get("from")) or norm_date(it.get("fromString"))
                        tv = norm_date(it.get("to")) or norm_date(it.get("toString"))
                    elif name == "labels":  # lista separada por espaço em fromString/toString
                        fv, tv = it.get("fromString"), it.get("toString")
                    elif name == "timeoriginalestimate":  # segundos
                        fv = it.get("from") if it.get("from") not in (None, "") else it.get("fromString")
                        tv = it.get("to") if it.get("to") not in (None, "") else it.get("toString")
                    else:
                        fv, tv = it.get("fromString"), it.get("toString")
                    iss.events.append(ChangeEvent(changelog_id=cid, at=at, field=name, from_value=fv, to_value=tv, author_id=author_id))
                elif fld == "assignee":
                    iss.assignee_changes.append(AssigneeChange(
                        changelog_id=cid, at=at,
                        from_person_id=self._by_ref(it.get("from"), it.get("fromString")),
                        to_person_id=self._by_ref(it.get("to"), it.get("toString"))))

    def _by_ref(self, ref: str | None, name: str | None = None) -> int | None:
        """Casa a pessoa do histórico por referência (accountId/username); ex-membros que só existem no
        histórico viram Person para não perder a ponta da mudança; sem referência, tenta pelo nome de exibição."""
        if ref:
            p = self.people.get(ref)
            if p is None:
                p = Person(jira_ref=ref, name=name or ref, active=True, role="externo")
                self.db.add(p)
                self.db.flush()
                self.people[ref] = p
            return p.id
        if name:
            p = next((x for x in self.people.values() if x.name == name), None)
            return p.id if p else None
        return None

    def _sync_worklogs(self, iss: Issue) -> None:
        """Espelha os worklogs: inclui novos, atualiza editados e remove os apagados no Jira."""
        if not iss.time_spent_s and not iss.worklogs:
            return  # nada registrado lá nem aqui: evita uma chamada por issue
        have = {w.id: w for w in iss.worklogs}
        remote: set[int] = set()
        for w in self.client.worklogs(iss.key):
            wid = int(w["id"])
            remote.add(wid)
            started = parse_dt(w["started"])
            if wid in have:
                cur = have[wid]
                cur.seconds, cur.started = w["timeSpentSeconds"], started
                continue
            a = self.person(w.get("author"))
            iss.worklogs.append(Worklog(id=wid, author_id=a.id if a else None, started=started, seconds=w["timeSpentSeconds"]))
        for wid, w in have.items():
            if wid not in remote:
                iss.worklogs.remove(w)

    # ---- orquestração ----
    def _since_clause(self, wm: SyncState) -> str:
        """Marca d'água − 5 min no fuso do usuário do Jira: o JQL interpreta `updated >= "yyyy-MM-dd HH:mm"` nesse fuso."""
        since = datetime.fromisoformat(wm.value).replace(tzinfo=timezone.utc) - timedelta(minutes=5)
        try:
            tz = ZoneInfo(self.client.user_timezone())
        except Exception:  # fuso desconhecido: UTC (pior caso, relê um pouco mais)
            tz = timezone.utc
            log.warning("fuso do usuário do Jira indisponível; usando UTC na marca d'água")
        return f"updated >= \"{since.astimezone(tz):%Y-%m-%d %H:%M}\""

    def run(self, jql_scope: str, full: bool = False) -> dict:
        wm = self.db.query(SyncState).filter_by(key="watermark").first()
        started = datetime.utcnow()
        jql = jql_scope
        if wm and wm.value and not full:
            jql = f"({jql_scope}) AND {self._since_clause(wm)}"
        fields = BASE_FIELDS + [v for v in self.fields.values() if v]
        raw = list(self.client.search(jql + " ORDER BY updated ASC" if "order by" not in jql.lower() else jql,
                                      fields, expand_changelog=True))
        hist = self.client.changelogs(raw)
        self.unmapped.clear()
        for r in raw:
            iss = self.upsert_issue(r, hist.get(r["id"], []))
            self._sync_worklogs(iss)
        if wm is None:
            wm = SyncState(key="watermark", value="")
            self.db.add(wm)
        wm.value = started.isoformat()
        self.db.commit()
        if self.unmapped:
            log.warning("status sem mapeamento em status_map (usei a categoria do Jira): %s", dict(self.unmapped))
        log.info("sync: %d issues", len(raw))
        return {"issues": len(raw), "since": jql, "finished": datetime.utcnow().isoformat(),
                "status_sem_mapeamento": dict(self.unmapped)}

    def reconcile_open(self) -> int:
        """Marca como removidas as issues abertas locais que o Jira não retorna mais (excluídas/sem acesso).

        Compara por id (a chave muda quando a issue é movida de projeto). O Jira responde 400 a `key in (...)` com
        chave inexistente ("An issue with key 'X' does not exist for field 'key'"): as chaves citadas são
        tiradas da consulta e contadas como removidas."""
        rows = self.db.query(Issue.id, Issue.key).filter(Issue.status_category != "done", Issue.deleted_at.is_(None)).all()
        alive: set[int] = set()
        for i in range(0, len(rows), 100):
            pend = [k for _, k in rows[i:i + 100]]
            while pend:
                try:
                    alive |= {int(r["id"]) for r in self.client.search(f"key in ({','.join(pend)})", ["summary"])}
                    break
                except JiraError as e:
                    bad = set(re.findall(r"[Ii]ssue with key '([^']+)' does not exist", str(e)))
                    if e.status != 400 or not (bad & set(pend)):
                        raise
                    pend = [k for k in pend if k not in bad]
        gone = 0
        ids = {iid for iid, _ in rows} - alive
        if ids:
            for iss in self.db.query(Issue).filter(Issue.id.in_(ids)):
                iss.deleted_at = datetime.utcnow()
                gone += 1
        self.db.commit()
        return gone
