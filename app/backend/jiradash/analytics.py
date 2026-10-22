"""Camada de análise: lê o banco, monta registros leves e devolve os payloads de cada tela."""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta

from sqlalchemy.orm import Session, joinedload

from .config import load_teams
from .distribution import engine as eng
from .metrics import capacity as cap
from .metrics import core as m
from .metrics import forecast as fc
from .metrics import risk as rk
from .models import Absence, Holiday, Issue, Person, Squad, SquadMember

PRIO = {"highest": 0, "crítica": 0, "critica": 0, "blocker": 0, "high": 1, "alta": 1, "medium": 2, "média": 2,
        "media": 2, "low": 3, "baixa": 3, "lowest": 4, "mínima": 4, "minima": 4}
STARTED = ("active", "wait", "blocked")


def prio_rank(p: str) -> int:
    return PRIO.get((p or "").lower(), 2)


@dataclass
class Filters:
    squad: str | None = None
    project: str | None = None
    person: int | None = None
    type: str | None = None
    priority: str | None = None
    date_from: date | None = None
    date_to: date | None = None


class Analytics:
    def __init__(self, db: Session, flt: Filters | None = None, now: datetime | None = None):
        self.db = db
        self.f = flt or Filters()
        self.now = now or datetime.utcnow()
        self.today = self.now.date()
        cfg = load_teams()
        self.cat_of: dict[str, str] = cfg.get("status_map", {})
        self.order: list[str] = cfg.get("workflow_order", [])
        self.negative = set(cfg.get("negative_resolutions", ["Won't Do", "Cancelado", "Duplicado"]))
        self.status_wip_limits: dict[str, int] = cfg.get("status_wip_limits", {})
        self.hours_fallback = float(cfg.get("default_issue_hours", 6))
        self.cfg = cfg
        self.review_types = set(cfg.get("review_types", ["Review"]))
        self.checklist_types = set(cfg.get("checklist_types", ["Checklist"]))
        self.goals = {"on_time_pct": 90.0, "early_pct": 35.0, **(cfg.get("goals") or {})}
        self.people = {p.id: p for p in db.query(Person).all()}
        self.squads = {s.id: s for s in db.query(Squad).all()}
        self.holidays = {h.day for h in db.query(Holiday).all()}
        self._recs: list[m.IssueRec] | None = None
        self._all: list[m.IssueRec] | None = None

    # ------------------------------------------------------------------ carga
    def _squad_id(self) -> int | None:
        if not self.f.squad:
            return None
        s = next((s for s in self.squads.values() if s.key == self.f.squad), None)
        return s.id if s else -1

    def _to_rec(self, i: Issue) -> m.IssueRec:
        return m.IssueRec(
            id=i.id, key=i.key, summary=i.summary, project=i.project, type=i.type, priority=i.priority,
            status=i.status, category=i.status_category, resolution=i.resolution, assignee_id=i.assignee_id,
            squad_id=i.squad_id, created=i.created, updated=i.updated, resolved=i.resolved, due=i.due,
            original_estimate_s=i.original_estimate_s, remaining_s=i.remaining_s, time_spent_s=i.time_spent_s,
            story_points=i.story_points, labels=i.labels or [], components=i.components or [],
            epic_key=i.epic_key, flagged=i.flagged, parent_key=i.parent_key, reporter_id=i.reporter_id,
            transitions=[m.Transition(t.at, t.from_status, t.to_status, t.author_id) for t in i.transitions],
            events=[m.Event(e.at, e.field, e.from_value, e.to_value, e.author_id) for e in i.events],
            assignee_changes=[(a.at, a.from_person_id, a.to_person_id) for a in i.assignee_changes])

    def load_all(self) -> list[m.IssueRec]:
        """Todas as issues (sem filtro de pessoa/tipo), para referências históricas do squad."""
        if self._all is None:
            q = self.db.query(Issue).options(joinedload(Issue.transitions), joinedload(Issue.assignee_changes), joinedload(Issue.events)) \
                .filter(Issue.deleted_at.is_(None))
            sid = self._squad_id()
            if sid is not None:
                q = q.filter(Issue.squad_id == sid)
            if self.f.project:
                q = q.filter(Issue.project == self.f.project)
            self._all = [self._to_rec(i) for i in q.all()]
        return self._all

    def recs(self) -> list[m.IssueRec]:
        if self._recs is None:
            out = self.load_all()
            f = self.f
            if f.person:
                out = [r for r in out if r.assignee_id == f.person]
            if f.type:
                out = [r for r in out if r.type == f.type]
            if f.priority:
                out = [r for r in out if r.priority == f.priority]
            self._recs = out
        return self._recs

    def open_recs(self) -> list[m.IssueRec]:
        return [r for r in self.recs() if r.category != "done"]

    def closed_recs(self, start: date | None = None, end: date | None = None) -> list[m.IssueRec]:
        start = start or self.f.date_from or self.today - timedelta(days=30)
        end = end or self.f.date_to or self.today
        return [r for r in self.recs() if r.resolved and start <= r.resolved.date() <= end]

    def done_ok(self, rs: list[m.IssueRec]) -> list[m.IssueRec]:
        """Concluídas com resolução positiva (exclui canceladas/duplicadas) para métricas de entrega."""
        return [r for r in rs if not m.is_negative_resolution(r, self.negative)]

    def name(self, pid: int | None) -> str:
        return self.people[pid].name if pid in self.people else "Sem responsável"

    def squad_name(self, sid: int | None) -> str:
        return self.squads[sid].name if sid in self.squads else "—"

    # ------------------------------------------------------------------ referências
    def cycle_hist_days(self, rs: list[m.IssueRec] | None = None, weeks: int = 16) -> list[float]:
        since = self.today - timedelta(weeks=weeks)
        base = rs if rs is not None else self.load_all()
        out = []
        for r in self.done_ok([x for x in base if x.resolved and x.resolved.date() >= since]):
            c = m.cycle_time_s(r, self.cat_of)
            if c is not None:
                out.append(c / m.DAY)
        return out

    def status_median_days(self) -> dict[str, float]:
        by: dict[str, list[float]] = defaultdict(list)
        for r in self.load_all():
            if r.resolved and r.resolved.date() >= self.today - timedelta(weeks=16):
                for s, sec in m.status_durations(r, self.now).items():
                    by[s].append(sec / m.DAY)
        return {s: float(m.pct(v, 50)) for s, v in by.items() if v}

    def throughput_series(self, weeks: int = 12, rs: list[m.IssueRec] | None = None) -> list[tuple[date, int]]:
        end = self.today
        start = m.week_start(end) - timedelta(weeks=weeks - 1)
        base = self.done_ok(rs if rs is not None else self.recs())
        return m.weekly_throughput(base, start, end)

    # ------------------------------------------------------------------ capacidade
    def _person_caps(self, person_ids: set[int] | None = None) -> dict[int, cap.PersonCap]:
        ab: dict[int, list] = defaultdict(list)
        for a in self.db.query(Absence).all():
            ab[a.person_id].append((a.start, a.end, a.partial))
        alloc: dict[int, float] = defaultdict(float)
        sid = self._squad_id()
        q = self.db.query(SquadMember)
        if sid is not None:
            q = q.filter(SquadMember.squad_id == sid)
        members = q.all()
        for sm in members:
            alloc[sm.person_id] += sm.allocation
        out = {}
        for pid, p in self.people.items():
            if not p.active or (person_ids is not None and pid not in person_ids):
                continue
            out[pid] = cap.PersonCap(pid, p.name, p.weekly_hours, p.focus_factor,
                                     min(1.0, alloc.get(pid, 1.0)) if sid is not None else 1.0, ab.get(pid, []))
        return out

    def member_ids(self) -> set[int]:
        sid = self._squad_id()
        q = self.db.query(SquadMember.person_id)
        if sid is not None:
            q = q.filter(SquadMember.squad_id == sid)
        ids = {pid for (pid,) in q.all()}
        if self.f.person:
            ids &= {self.f.person}
        return {i for i in ids if self.people.get(i) and self.people[i].role != "gerente"} or ids

    def issue_hours(self, r: m.IssueRec) -> float:
        return cap.issue_hours(r.remaining_s, r.original_estimate_s, r.time_spent_s, r.story_points, self.hours_fallback)

    def plan(self, horizon_weeks: int = 12, ignore_absences: bool = False) -> dict:
        """Agenda a fila de cada pessoa na sua capacidade semanal: data prevista por issue e carga por semana."""
        caps = self._person_caps(self.member_ids())
        if ignore_absences:
            for pc in caps.values():
                pc.absences = []
        by_person: dict[int, list[m.IssueRec]] = defaultdict(list)
        for r in self.load_all():
            if r.category != "done" and r.assignee_id in caps:
                by_person[r.assignee_id].append(r)
        out = {"weeks": [], "people": {}, "predicted": {}}
        for pid, pc in caps.items():
            weeks = cap.weekly_capacity(pc, self.today, horizon_weeks, self.holidays)
            q = sorted(by_person.get(pid, []), key=lambda r: (0 if r.category in STARTED else 1, prio_rank(r.priority),
                                                              r.due or date.max, r.created))
            pred = cap.schedule([(r.key, self.issue_hours(r)) for r in q], weeks, self.today)
            # demanda semanal: cada issue espalha suas horas da semana atual até a semana do vencimento
            # (sem vencimento: 2 semanas se já iniciada, 4 se não iniciada). Mostra picos e efeito de ausências.
            demand = [0.0] * len(weeks)
            w0 = date.fromisoformat(weeks[0]["week"])
            for r in q:
                h = self.issue_hours(r)
                if r.due:
                    last = max(1, min(len(weeks) - 1, (r.due - w0).days // 7))  # janela mínima de 2 semanas
                else:
                    last = 1 if r.category in STARTED else 3
                last = min(last, len(weeks) - 1)
                share = h / (last + 1)
                for wi in range(last + 1):
                    demand[wi] += share
            out["people"][pid] = {"weeks": weeks, "load": [round(x, 1) for x in demand], "excess_h": 0.0,
                                  "total_h": round(sum(self.issue_hours(r) for r in q), 1)}
            out["predicted"].update(pred)
            out["weeks"] = [w["week"] for w in weeks]
        return out

    # ------------------------------------------------------------------ cartões/telas
    def meta(self) -> dict:
        cfg = load_teams()
        rs = self.db.query(Issue.project, Issue.type, Issue.priority).distinct().all()
        return {
            "squads": [{"key": s.key, "name": s.name, "projects": s.projects} for s in self.squads.values()],
            "people": [{"id": p.id, "name": p.name, "role": p.role} for p in sorted(self.people.values(), key=lambda p: p.name)],
            "projects": sorted({p for p, _, _ in rs}), "types": sorted({t for _, t, _ in rs}),
            "priorities": sorted({p for _, _, p in rs}, key=prio_rank), "statuses": cfg.get("workflow_order", []),
            "today": self.today.isoformat(), "mode": __import__("os").getenv("JIRADASH_MODE", "demo"),
        }

    def _trend(self, now_v: float | None, prev_v: float | None) -> dict:
        if now_v is None or prev_v in (None, 0):
            return {"value": now_v, "delta_pct": None}
        return {"value": now_v, "delta_pct": round((now_v - prev_v) / prev_v * 100, 1)}

    def overview(self) -> dict:
        p30 = self.closed_recs(self.today - timedelta(days=29), self.today)
        p60 = self.closed_recs(self.today - timedelta(days=59), self.today - timedelta(days=30))
        ok30, ok60 = self.done_ok(p30), self.done_ok(p60)
        open_ = self.open_recs()
        cyc = [c / m.DAY for r in ok30 if (c := m.cycle_time_s(r, self.cat_of)) is not None]
        cyc_prev = [c / m.DAY for r in ok60 if (c := m.cycle_time_s(r, self.cat_of)) is not None]
        hist = self.cycle_hist_days()
        plan = self.plan()
        risk_counts = Counter()
        for r in open_:
            sc = rk.risk_for(r, self.now, self.cat_of, hist, self.status_median_days(), plan["predicted"].get(r.key))["level"]
            risk_counts[sc] += 1
        overdue = [r for r in open_ if r.due and r.due < self.today]
        neglected = [r for r in open_ if (self.now - r.updated).days > 7 and r.category in STARTED]
        tp = self.throughput_series(12)
        remaining = len(open_)
        mc = fc.monte_carlo_when([n for _, n in tp], remaining)
        wip = [r for r in open_ if r.category in STARTED]
        util = self.utilization_rows(plan)
        return {
            "cards": {
                "abertas": len(open_), "em_andamento": len(wip),
                "concluidas_30d": self._trend(len(ok30), len(ok60)),
                "ciclo_p50_d": self._trend(m.pct(cyc, 50) and round(m.pct(cyc, 50), 1), m.pct(cyc_prev, 50) and round(m.pct(cyc_prev, 50), 1)),
                "ciclo_p85_d": round(m.pct(cyc, 85), 1) if cyc else None,
                "vencidas": len(overdue), "negligenciadas": len(neglected),
                "bloqueadas": sum(1 for r in open_ if r.category == "blocked" or r.flagged),
                "risco_alto": risk_counts["alto"], "risco_medio": risk_counts["médio"],
                "no_prazo_pct": self._on_time_pct(p30),
            },
            "throughput": [{"week": w.isoformat(), "count": n} for w, n in tp],
            "forecast_backlog": mc, "equidade": self.fairness_from(util),
            "by_squad": self.by_squad(),
        }

    def _on_time_pct(self, rs: list[m.IssueRec]) -> float | None:
        v = [m.on_time(r) for r in rs if m.on_time(r) is not None]
        return round(100 * sum(v) / len(v), 1) if v else None

    def by_squad(self) -> list[dict]:
        out = []
        for s in self.squads.values():
            if self.f.squad and s.key != self.f.squad:
                continue
            a = Analytics(self.db, Filters(squad=s.key, project=self.f.project, person=self.f.person, type=self.f.type,
                                           priority=self.f.priority), self.now)
            op = a.open_recs()
            cl = a.done_ok(a.closed_recs(self.today - timedelta(days=29), self.today))
            plan = a.plan()
            hist = a.cycle_hist_days()
            smed = a.status_median_days()
            high = sum(1 for r in op if rk.risk_for(r, self.now, self.cat_of, hist, smed, plan["predicted"].get(r.key))["level"] == "alto")
            tp = [n for _, n in a.throughput_series(12)]
            util = a.utilization_rows(plan)
            out.append({"key": s.key, "name": s.name, "abertas": len(op), "concluidas_30d": len(cl),
                        "vencidas": sum(1 for r in op if r.due and r.due < self.today), "risco_alto": high,
                        "throughput": tp, "equidade": a.fairness_from(util),
                        "ocupacao_media": round(sum(u["utilizacao_4s"] for u in util) / len(util), 2) if util else None})
        return out

    def issue_row(self, r: m.IssueRec, hist=None, smed=None, plan=None) -> dict:
        hist = hist if hist is not None else self.cycle_hist_days()
        smed = smed if smed is not None else self.status_median_days()
        pred = (plan or {}).get("predicted", {}).get(r.key)
        risk = rk.risk_for(r, self.now, self.cat_of, hist, smed, pred)
        return {
            "key": r.key, "summary": r.summary, "project": r.project, "type": r.type, "priority": r.priority,
            "status": r.status, "category": r.category, "assignee": self.name(r.assignee_id),
            "assignee_id": r.assignee_id, "squad": self.squad_name(r.squad_id),
            "created": r.created.date().isoformat(), "updated": r.updated.date().isoformat(),
            "due": r.due.isoformat() if r.due else None,
            "idade_d": round(m.age_s(r, self.now, self.cat_of) / m.DAY, 1),
            "no_status_d": round(m.time_in_current_status_s(r, self.now) / m.DAY, 1),
            "estimado_h": round(r.original_estimate_s / 3600, 1) if r.original_estimate_s else None,
            "gasto_h": round(r.time_spent_s / 3600, 1), "pontos": r.story_points,
            "previsao": pred.isoformat() if pred else None, "risco": risk["score"], "nivel_risco": risk["level"],
            "fatores_risco": risk["factors"], "reaberturas": m.reopen_count(r, self.cat_of),
            "devolucoes": m.return_count(r, self.order, self.cat_of), "trocas_resp": m.reassign_count(r),
            "bloqueada": r.category == "blocked" or r.flagged, "epic": r.epic_key,
        }

    def open_view(self) -> dict:
        op = self.open_recs()
        hist, smed, plan = self.cycle_hist_days(), self.status_median_days(), self.plan()
        rows = [self.issue_row(r, hist, smed, plan) for r in op]
        by_status = Counter(r.status for r in op)
        by_prio = Counter(r.priority for r in op)
        buckets = Counter()
        for row in rows:
            d = row["idade_d"]
            buckets["0-7 d" if d <= 7 else "8-14 d" if d <= 14 else "15-30 d" if d <= 30 else "> 30 d"] += 1
        due = Counter()
        for r in op:
            if not r.due:
                due["sem vencimento"] += 1
            elif r.due < self.today:
                due["vencidas"] += 1
            elif (r.due - self.today).days <= 7:
                due["vence em 7 d"] += 1
            else:
                due["depois"] += 1
        order = self.order or sorted(by_status)
        return {
            "total": len(op),
            "by_status": [{"status": s, "count": by_status.get(s, 0), "category": self.cat_of.get(s)} for s in order if s in by_status or self.cat_of.get(s) != "done"],
            "by_priority": [{"priority": p, "count": n} for p, n in sorted(by_prio.items(), key=lambda kv: prio_rank(kv[0]))],
            "age_buckets": [{"bucket": b, "count": buckets.get(b, 0)} for b in ("0-7 d", "8-14 d", "15-30 d", "> 30 d")],
            "due_buckets": [{"bucket": k, "count": due.get(k, 0)} for k in ("vencidas", "vence em 7 d", "depois", "sem vencimento")],
            "aging": {"p50": m.pct(hist, 50) and round(m.pct(hist, 50), 1), "p85": m.pct(hist, 85) and round(m.pct(hist, 85), 1),
                      "p95": m.pct(hist, 95) and round(m.pct(hist, 95), 1)},
            "issues": sorted(rows, key=lambda x: -x["idade_d"]),
        }

    def neglected(self, idle_days: int = 7) -> dict:
        op = self.open_recs()
        hist, smed, plan = self.cycle_hist_days(), self.status_median_days(), self.plan()
        row = lambda r: self.issue_row(r, hist, smed, plan)  # noqa: E731
        groups = {
            "sem_atualizacao": [r for r in op if (self.now - r.updated).days > idle_days and r.category in STARTED],
            "sem_responsavel": [r for r in op if r.assignee_id is None],
            "vencidas": [r for r in op if r.due and r.due < self.today],
            "bloqueadas": [r for r in op if r.category == "blocked" or r.flagged],
            "paradas_no_status": [r for r in op if r.category in STARTED and m.time_in_current_status_s(r, self.now) / m.DAY >
                                  max(3.0, 2 * smed.get(r.status, 1.5))],
            "a_fazer_antigas": [r for r in op if r.category == "todo" and (self.now - r.created).days > 30],
        }
        return {"idle_days": idle_days,
                "counts": {k: len(v) for k, v in groups.items()},
                "groups": {k: sorted([row(r) for r in v], key=lambda x: -x["idade_d"])[:100] for k, v in groups.items()}}

    def _closed_row(self, r: m.IssueRec) -> dict:
        row = self.issue_row(r, [], {}, None)
        dur = m.status_durations(r, self.now)
        row.update({
            "lead_d": m.days(m.lead_time_s(r)), "ciclo_d": m.days(m.cycle_time_s(r, self.cat_of)),
            "resolved": r.resolved.date().isoformat() if r.resolved else None, "resolution": r.resolution,
            "tempo_por_status_d": {s: round(v / m.DAY, 1) for s, v in sorted(dur.items(), key=lambda kv: -kv[1])},
            "no_prazo": m.on_time(r), "razao_estimativa": round(x, 2) if (x := m.estimate_ratio(r)) else None,
        })
        return row

    def closed_view(self) -> dict:
        start = self.f.date_from or self.today - timedelta(days=29)
        end = self.f.date_to or self.today
        allc = self.closed_recs(start, end)
        ok = self.done_ok(allc)
        cyc = [c / m.DAY for r in ok if (c := m.cycle_time_s(r, self.cat_of)) is not None]
        lead = [m.lead_time_s(r) / m.DAY for r in ok]
        by_person: dict[int | None, dict] = {}
        for r in ok:
            at = m.active_time_by_person(r, self.now, self.cat_of)
            for pid in set(at) | {r.assignee_id}:
                d = by_person.setdefault(pid, {"person_id": pid, "nome": self.name(pid), "finalizadas": 0, "participou": 0,
                                               "pontos": 0.0, "ciclos": [], "ativo_h": 0.0, "estimado_h": 0.0, "gasto_h": 0.0})
                d["participou"] += 1
                d["ativo_h"] += at.get(pid, 0) / 3600
                if pid == r.assignee_id:
                    d["finalizadas"] += 1
                    d["pontos"] += r.story_points or 0
                    d["estimado_h"] += (r.original_estimate_s or 0) / 3600
                    d["gasto_h"] += r.time_spent_s / 3600
                    if (c := m.cycle_time_s(r, self.cat_of)) is not None:
                        d["ciclos"].append(c / m.DAY)
        people = []
        for d in by_person.values():
            people.append({"person_id": d["person_id"], "nome": d["nome"], "finalizadas": d["finalizadas"],
                           "participou": d["participou"], "pontos": d["pontos"],
                           "ciclo_p50_d": round(m.pct(d["ciclos"], 50), 1) if d["ciclos"] else None,
                           "ciclo_p85_d": round(m.pct(d["ciclos"], 85), 1) if d["ciclos"] else None,
                           "tempo_ativo_h": round(d["ativo_h"], 1), "estimado_h": round(d["estimado_h"], 1),
                           "gasto_h": round(d["gasto_h"], 1)})
        people.sort(key=lambda x: x["nome"])  # ordem alfabética de propósito: sem ranking
        by_type = Counter(r.type for r in ok)
        by_proj = Counter(r.project for r in ok)
        return {
            "periodo": {"de": start.isoformat(), "ate": end.isoformat()},
            "totais": {"concluidas": len(ok), "canceladas_ou_descartadas": len(allc) - len(ok),
                       "pontos": sum(r.story_points or 0 for r in ok),
                       "no_prazo_pct": self._on_time_pct(ok)},
            "ciclo": {"p50": m.pct(cyc, 50) and round(m.pct(cyc, 50), 1), "p85": m.pct(cyc, 85) and round(m.pct(cyc, 85), 1),
                      "p95": m.pct(cyc, 95) and round(m.pct(cyc, 95), 1), "media": m.mean(cyc) and round(m.mean(cyc), 1), "n": len(cyc)},
            "lead": {"p50": m.pct(lead, 50) and round(m.pct(lead, 50), 1), "p85": m.pct(lead, 85) and round(m.pct(lead, 85), 1), "n": len(lead)},
            "throughput": [{"week": w.isoformat(), "count": n} for w, n in m.weekly_throughput(ok, start, end)],
            "scatter": [{"key": r.key, "dia": r.resolved.date().isoformat(), "ciclo_d": round(c / m.DAY, 1), "type": r.type}
                        for r in ok if (c := m.cycle_time_s(r, self.cat_of)) is not None],
            "by_type": [{"type": t, "count": n} for t, n in by_type.most_common()],
            "by_project": [{"project": p, "count": n} for p, n in by_proj.most_common()],
            "pessoas": people,
            "issues": sorted([self._closed_row(r) for r in allc], key=lambda x: x["resolved"], reverse=True)[:300],
        }

    def negative_view(self) -> dict:
        start = self.f.date_from or self.today - timedelta(days=89)
        end = self.f.date_to or self.today
        pool = self.recs()
        closed = [r for r in pool if r.resolved and start <= r.resolved.date() <= end]
        cats = {
            "reabertas": [r for r in pool if m.reopen_count(r, self.cat_of) and (r.updated.date() >= start)],
            "devolvidas": [r for r in pool if m.return_count(r, self.order, self.cat_of) and (r.updated.date() >= start)],
            "canceladas_descartadas": [r for r in closed if m.is_negative_resolution(r, self.negative)],
            "concluidas_atrasadas": [r for r in closed if m.on_time(r) is False and not m.is_negative_resolution(r, self.negative)],
            "abertas_vencidas": [r for r in pool if r.category != "done" and r.due and r.due < self.today],
            "estouro_estimativa": [r for r in pool if (x := m.estimate_ratio(r)) and x > 1.25 and
                                   (r.category != "done" or (r.resolved and r.resolved.date() >= start))],
            "muitas_trocas_responsavel": [r for r in pool if m.reassign_count(r) >= 3 and r.updated.date() >= start],
        }
        rows = {k: sorted([self._closed_row(r) if r.resolved else self.issue_row(r, [], {}, None) for r in v],
                          key=lambda x: x["key"])[:150] for k, v in cats.items()}
        for k, v in cats.items():
            for row, r in zip(rows[k], sorted(v, key=lambda x: x.key)[:150]):
                row.setdefault("tempo_por_status_d", {s: round(sec / m.DAY, 1) for s, sec in
                                                      sorted(m.status_durations(r, self.now).items(), key=lambda kv: -kv[1])})
        denom = max(1, len(self.done_ok(closed)))
        by_person: dict[int | None, Counter] = defaultdict(Counter)
        for k, v in cats.items():
            for r in v:
                by_person[r.assignee_id][k] += 1
        return {
            "periodo": {"de": start.isoformat(), "ate": end.isoformat()},
            "counts": {k: len(v) for k, v in cats.items()},
            "taxas": {"reabertura_pct": round(100 * len(cats["reabertas"]) / denom, 1),
                      "devolucao_pct": round(100 * len(cats["devolvidas"]) / denom, 1),
                      "atraso_pct": round(100 * len(cats["concluidas_atrasadas"]) / denom, 1)},
            "por_responsavel": sorted([{"person_id": pid, "nome": self.name(pid), **dict(c)} for pid, c in by_person.items()],
                                      key=lambda x: x["nome"]),
            "groups": rows,
        }

    def time_in_status(self) -> dict:
        start = self.f.date_from or self.today - timedelta(days=89)
        end = self.f.date_to or self.today
        closed = self.done_ok(self.closed_recs(start, end))
        order = self.order or []
        agg: dict[str, list[float]] = defaultdict(list)
        per_person: dict[int | None, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
        for r in closed:
            for s, sec in m.status_durations(r, self.now).items():
                if self.cat_of.get(s) != "done":
                    agg[s].append(sec / m.DAY)
                    per_person[r.assignee_id][s].append(sec / m.DAY)
        statuses = [s for s in order if s in agg] + [s for s in agg if s not in order]
        total = sum(sum(v) for v in agg.values()) or 1
        stats = [{"status": s, "categoria": self.cat_of.get(s), "n": len(agg[s]), "media_d": round(m.mean(agg[s]), 2),
                  "p50_d": round(m.pct(agg[s], 50), 2), "p85_d": round(m.pct(agg[s], 85), 2),
                  "participacao_pct": round(100 * sum(agg[s]) / total, 1)} for s in statuses]
        active = sum(sum(agg[s]) for s in statuses if self.cat_of.get(s) == "active")
        matrix = [{"person_id": pid, "nome": self.name(pid), "valores": {s: round(m.pct(v[s], 50), 1) for s in statuses if v.get(s)}}
                  for pid, v in per_person.items()]
        open_stuck = []
        smed = self.status_median_days()
        for r in self.open_recs():
            d = m.time_in_current_status_s(r, self.now) / m.DAY
            if r.category in STARTED and d > max(2.0, 1.5 * smed.get(r.status, 1)):
                open_stuck.append({"key": r.key, "summary": r.summary, "status": r.status, "dias": round(d, 1),
                                   "mediana_status_d": round(smed.get(r.status, 0), 1), "assignee": self.name(r.assignee_id)})
        return {"periodo": {"de": start.isoformat(), "ate": end.isoformat()}, "n": len(closed), "status": stats,
                "eficiencia_fluxo_pct": round(100 * active / total, 1),
                "matriz": sorted(matrix, key=lambda x: x["nome"]), "colunas": statuses,
                "abertas_paradas": sorted(open_stuck, key=lambda x: -x["dias"])[:50]}

    def utilization_rows(self, plan: dict | None = None) -> list[dict]:
        plan = plan or self.plan()
        out = []
        sid = self._squad_id()
        open_by: dict[int, list[m.IssueRec]] = defaultdict(list)
        for r in self.load_all():
            if r.category != "done" and r.assignee_id:
                open_by[r.assignee_id].append(r)
        for pid, pp in plan["people"].items():
            w4 = pp["weeks"][:4]
            cap4 = sum(w["hours"] for w in w4)
            load4 = sum(pp["load"][:4])  # demanda das 4 próximas semanas (por vencimento); > capacidade = sobrecarga
            mine = open_by.get(pid, [])
            absent4 = sum(w["absent_days"] for w in w4)
            out.append({
                "person_id": pid, "nome": self.name(pid), "abertas": len(mine),
                "wip": sum(1 for r in mine if r.category in STARTED), "horas_restantes": pp["total_h"],
                "capacidade_4s_h": round(cap4, 1), "carga_4s_h": round(load4, 1),
                "utilizacao_4s": round(load4 / cap4, 2) if cap4 > 0.5 else (9.9 if load4 else 0.0),
                "dias_ausencia_4s": round(absent4, 1),
                "excesso_h": pp.get("excess_h", 0.0),
                "semanas": [{"semana": w["week"], "capacidade_h": w["hours"], "carga_h": l,
                             "ocupacao": round(l / w["hours"], 2) if w["hours"] > 0.5 else (9.9 if l > 0.5 else 0.0),
                             "ausente_dias": w["absent_days"]} for w, l in zip(pp["weeks"], pp["load"])],
            })
        return sorted(out, key=lambda x: x["nome"])

    @staticmethod
    def fairness_from(util: list[dict]) -> dict:
        v = [min(u["utilizacao_4s"], 3) for u in util if u["capacidade_4s_h"] > 0.5]
        j = m.jain_index(v)
        return {"jain": j, "max": round(max(v), 2) if v else None, "min": round(min(v), 2) if v else None}

    def people_view(self) -> dict:
        plan = self.plan()
        util = self.utilization_rows(plan)
        wl30: dict[int, float] = defaultdict(float)
        since = self.now - timedelta(days=30)
        from .models import Worklog
        for w in self.db.query(Worklog).filter(Worklog.started >= since):
            if w.author_id:
                wl30[w.author_id] += w.seconds / 3600
        hist, smed = self.cycle_hist_days(), self.status_median_days()
        p85 = m.pct(hist, 85)
        for u in util:
            mine = [r for r in self.load_all() if r.assignee_id == u["person_id"] and r.category != "done"]
            u["horas_registradas_30d"] = round(wl30.get(u["person_id"], 0), 1)
            u["itens_acima_p85"] = sum(1 for r in mine if p85 and m.age_s(r, self.now, self.cat_of) / m.DAY > p85)
            u["itens_risco_alto"] = sum(1 for r in mine if rk.risk_for(r, self.now, self.cat_of, hist, smed, plan["predicted"].get(r.key))["level"] == "alto")
            u["vencidas"] = sum(1 for r in mine if r.due and r.due < self.today)
        return {"pessoas": util, "equidade": self.fairness_from(util), "ordem": "alfabética (sem ranking)"}

    def risks(self) -> dict:
        hist, smed, plan = self.cycle_hist_days(), self.status_median_days(), self.plan()
        rows = [self.issue_row(r, hist, smed, plan) for r in self.open_recs()]
        rows = [r for r in rows if r["risco"] >= 30]
        rows.sort(key=lambda r: -r["risco"])
        sig: dict[int | None, Counter] = defaultdict(Counter)
        for r in rows:
            sig[r["assignee_id"]]["alto" if r["nivel_risco"] == "alto" else "medio"] += 1
            for f in r["fatores_risco"]:
                if "idade" in f["why"]:
                    sig[r["assignee_id"]]["idade"] += 1
                if "parada" in f["why"]:
                    sig[r["assignee_id"]]["parada"] += 1
                if "estimado" in f["why"]:
                    sig[r["assignee_id"]]["estouro"] += 1
        return {"issues": rows[:200], "resumo": Counter(r["nivel_risco"] for r in rows),
                "sinais_por_pessoa": sorted([{"person_id": pid, "nome": self.name(pid), **dict(c)} for pid, c in sig.items()],
                                            key=lambda x: x["nome"]),
                "nota": "Sinais para conversa e apoio, não para avaliação. Pesos heurísticos a calibrar."}

    def forecast_view(self) -> dict:
        op = self.open_recs()
        tp = self.throughput_series(16)
        hist = [n for _, n in tp]
        hist_nonfuture = hist
        mc = fc.monte_carlo_when(hist_nonfuture, len(op))
        how = {f"{w}s": fc.monte_carlo_how_many(hist_nonfuture, w) for w in (2, 4, 8)}
        plan = self.plan()
        cyc = self.cycle_hist_days()
        rows = []
        for r in op:
            if r.category not in STARTED:
                continue
            age = m.age_s(r, self.now, self.cat_of) / m.DAY
            cond = fc.remaining_given_age(cyc, age)
            pred = plan["predicted"].get(r.key)
            rows.append({
                "key": r.key, "summary": r.summary, "assignee": self.name(r.assignee_id), "status": r.status,
                "idade_d": round(age, 1), "restante_p50_d": cond["p50"], "restante_p85_d": cond["p85"],
                "previsao_p50": fc.add_days(self.today, cond["p50"]).isoformat() if cond["p50"] is not None else None,
                "previsao_p85": fc.add_days(self.today, cond["p85"]).isoformat() if cond["p85"] is not None else None,
                "previsao_capacidade": pred.isoformat() if pred else None,
                "vencimento": r.due.isoformat() if r.due else None,
                "atrasa": bool(r.due and ((pred and pred > r.due) or (cond["p85"] is not None and fc.add_days(self.today, cond["p85"]) > r.due))),
                "base": cond["n"]})
        w = mc.get("weeks")
        return {
            "backlog_aberto": len(op), "throughput_semanal": [{"week": d.isoformat(), "count": n} for d, n in tp],
            "quando_termina": {**mc, "datas": {k: (self.today + timedelta(weeks=v)).isoformat() for k, v in w.items()} if w else None},
            "quantos_ate": how, "issues": sorted(rows, key=lambda x: (not x["atrasa"], -x["idade_d"]))[:150],
            "metodo": "Monte Carlo (10.000 simulações) sobre o throughput semanal; previsão por issue = distribuição "
                      "condicional de itens históricos mais longos que a idade atual; previsão por capacidade = fila da pessoa "
                      "preenchida pelas horas líquidas (ausências e feriados).",
        }

    def bottlenecks(self, days_back: int = 60) -> dict:
        recs = self.recs()
        order = self.order or sorted({r.status for r in recs})
        open_order = [s for s in order if self.cat_of.get(s) != "done"]
        days_list = [self.today - timedelta(days=days_back - i) for i in range(days_back + 1)]
        cfd: list[dict] = []
        events: dict[int, list[tuple[datetime, str]]] = {}
        for r in recs:
            ev = [(r.created, (sorted(r.transitions, key=lambda t: t.at)[0].from_status if r.transitions else r.status))]
            ev += [(t.at, t.to_status) for t in sorted(r.transitions, key=lambda t: t.at)]
            events[r.id] = ev
        for d in days_list:
            eod = datetime.combine(d, datetime.max.time())
            counts: Counter = Counter()
            for r in recs:
                st = None
                for at, s in events[r.id]:
                    if at <= eod:
                        st = s
                    else:
                        break
                if st is not None:
                    counts[st] += 1
            cfd.append({"day": d.isoformat(), **{s: counts.get(s, 0) for s in order}})
        cur = Counter(r.status for r in self.open_recs())
        smed = self.status_median_days()
        wait = []
        for s in open_order:
            limit = self.status_wip_limits.get(s)
            first = cfd[max(0, len(cfd) - 15)].get(s, 0)
            last = cfd[-1].get(s, 0)
            growth = last - first
            ratio = (cur.get(s, 0) / limit) if limit else None
            flag = (ratio is not None and ratio > 1) or (growth >= max(3, 0.3 * max(first, 1)) and self.cat_of.get(s) in ("wait", "active"))
            wait.append({"status": s, "categoria": self.cat_of.get(s), "itens": cur.get(s, 0), "limite_wip": limit,
                         "uso_limite": round(ratio, 2) if ratio is not None else None,
                         "mediana_historica_d": round(smed.get(s, 0), 1), "variacao_15d": growth, "gargalo": bool(flag)})
        util = self.utilization_rows()
        return {"cfd": cfd, "status_order": order, "estados": wait,
                "sobrecarregados": [u for u in util if u["utilizacao_4s"] > 1.1], "ociosos": [u for u in util if 0 < u["capacidade_4s_h"] and u["utilizacao_4s"] < 0.5],
                "nota": "Gargalo = status acima do limite de WIP ou cuja banda no CFD cresce nos últimos 15 dias."}

    def capacity_view(self, weeks: int = 8) -> dict:
        plan = self.plan(max(weeks, 4))
        rows = self.utilization_rows(plan)
        alerts = []
        pers = self.people
        for u in rows:
            p = pers[u["person_id"]]
            day_h = p.weekly_hours / 5 * p.focus_factor
            if u["utilizacao_4s"] > 1.1:
                alerts.append({"person_id": u["person_id"], "nome": u["nome"], "tipo": "carga", "semana": u["semanas"][0]["semana"],
                               "ocupacao": u["utilizacao_4s"], "ausente_dias": 0.0,
                               "detalhe": f"demanda de {u['carga_4s_h']:.0f} h para {u['capacidade_4s_h']:.0f} h de capacidade em 4 semanas"})
            for s in u["semanas"][:weeks]:
                if s["ausente_dias"] >= 2 and s["carga_h"] > 0.5:
                    alerts.append({"person_id": u["person_id"], "nome": u["nome"], "tipo": "ausência", "semana": s["semana"],
                                   "ocupacao": u["utilizacao_4s"], "ausente_dias": s["ausente_dias"],
                                   "detalhe": f"{s['ausente_dias']:.0f} dia(s) fora com trabalho agendado: ~{s['ausente_dias'] * day_h:.0f} h a replanejar"})
        abs_rows = []
        sid_members = self.member_ids()
        for a in self.db.query(Absence).filter(Absence.end >= self.today).order_by(Absence.start):
            if a.person_id in sid_members:
                abs_rows.append({"nome": self.name(a.person_id), "inicio": a.start.isoformat(), "fim": a.end.isoformat(),
                                 "tipo": a.kind, "dias_uteis": sum(1 for k in range((a.end - a.start).days + 1)
                                                                   if cap.is_workday(a.start + timedelta(days=k), self.holidays))})
        hol = [{"dia": h.isoformat(), "nome": self.db.query(Holiday).filter_by(day=h).first().name}
               for h in sorted(self.holidays) if self.today <= h <= self.today + timedelta(weeks=weeks)]
        # atrasos previstos: data pela capacidade depois do vencimento; separa o efeito das ausências
        free = self.plan(weeks + 4, ignore_absences=True)
        plan_long = self.plan(weeks + 4)
        slips = []
        for r in self.open_recs():
            if not r.due or r.due < self.today or r.assignee_id not in plan_long["people"]:
                continue
            real, sem_aus = plan_long["predicted"].get(r.key), free["predicted"].get(r.key)
            late = real is None or real > r.due
            if late:
                slips.append({"key": r.key, "summary": r.summary, "assignee": self.name(r.assignee_id),
                              "assignee_id": r.assignee_id, "vencimento": r.due.isoformat(),
                              "previsao": real.isoformat() if real else "além do horizonte",
                              "por_ausencia": bool(sem_aus and sem_aus <= r.due), "priority": r.priority,
                              "squad": self.squad_name(r.squad_id)})
        return {"semanas": plan["weeks"][:weeks], "pessoas": rows, "alertas": alerts, "ausencias": abs_rows, "feriados": hol,
                "equidade": self.fairness_from(rows), "atrasos_previstos": sorted(slips, key=lambda x: x["vencimento"])[:100]}

    def quality(self) -> dict:
        op = self.open_recs()
        n = max(1, len(op))
        started = [r for r in op if r.category in STARTED]
        checks = {
            "sem_estimativa": [r for r in op if r.original_estimate_s is None and r.story_points is None],
            "sem_responsavel": [r for r in op if r.assignee_id is None],
            "sem_vencimento": [r for r in op if r.due is None],
            "sem_epico": [r for r in op if not r.epic_key],
            "em_andamento_sem_horas": [r for r in started if r.time_spent_s == 0 and (self.now - r.created).days > 3],
            "sem_squad": [r for r in op if r.squad_id is None],
            "prioridade_padrao": [r for r in op if (r.priority or "").lower() in ("medium", "média")],
        }
        return {"total_abertas": len(op),
                "checks": [{"id": k, "qtd": len(v), "pct": round(100 * len(v) / n, 1), "exemplos": [r.key for r in v[:8]]} for k, v in checks.items()],
                "nota": "Previsão e carga dependem de estimativa; sem ela o sistema usa horas-padrão e marca baixa confiança."}

    # ------------------------------------------------------------------ distribuição
    def candidates(self, squad_id: int, extra_skills_for: m.IssueRec | None = None) -> list[eng.Candidate]:
        sq = self.squads[squad_id]
        a = Analytics(self.db, Filters(squad=sq.key), self.now)
        plan = a.plan()
        tp_by: Counter = Counter()
        since = self.today - timedelta(weeks=12)
        for r in a.done_ok([x for x in a.load_all() if x.resolved and x.resolved.date() >= since]):
            if r.assignee_id:
                tp_by[r.assignee_id] += 1
        ids = a.member_ids()
        ids = {i for i in ids if self.people[i].role != "gerente"}
        avg = (sum(tp_by[i] for i in ids) / len(ids)) if ids else 0
        last_rank = {}
        recent = sorted([r for r in a.load_all() if r.assignee_id in ids], key=lambda r: r.created)
        for k, r in enumerate(recent):
            last_rank[r.assignee_id] = k
        out = []
        for pid in ids:
            pp = plan["people"].get(pid)
            if not pp:
                continue
            h = pp["weeks"][:4]
            cap4 = sum(w["hours"] for w in h)
            mine = [r for r in a.load_all() if r.assignee_id == pid and r.category != "done"]
            aff = {r.epic_key for r in a.load_all() if r.assignee_id == pid and r.epic_key} | \
                  {c for r in a.load_all() if r.assignee_id == pid for c in r.components}
            share = (tp_by[pid] / avg) if avg else 1.0
            # suavização: média ponderada com 1,0 (poucos dados puxam para neutro)
            shrink = min(1.0, sum(tp_by.values()) / 40)
            out.append(eng.Candidate(
                person_id=pid, name=self.people[pid].name, capacity_h=cap4, load_h=sum(pp["load"][:4]),
                wip=sum(1 for r in mine if r.category in STARTED), wip_limit=sq.wip_limit_per_person,
                skills=set(self.people[pid].skills or []), affinity_keys=aff,
                throughput_share=1.0 * (1 - shrink) + share * shrink, last_assigned_rank=last_rank.get(pid, -1)))
        return out

    def pending_distribution(self) -> list[dict]:
        """Issues abertas ainda não iniciadas, sem responsável ou no nome do gerente do squad."""
        out = []
        for s in self.squads.values():
            if self._squad_id() not in (None, s.id):
                continue
            a = Analytics(self.db, Filters(squad=s.key), self.now)
            for r in a.load_all():
                if r.category == "todo" and r.assignee_id in (None, s.manager_id):
                    out.append({**a.issue_row(r, [], {}, None), "gerente": self.name(s.manager_id), "squad_key": s.key,
                                "horas": round(a.issue_hours(r), 1)})
        return sorted(out, key=lambda x: (prio_rank(x["priority"]), x["created"]))

    def suggest(self, issue_keys: list[str] | None = None) -> dict:
        pend = self.pending_distribution()
        if issue_keys:
            pend = [p for p in pend if p["key"] in issue_keys]
        by_squad: dict[str, list[dict]] = defaultdict(list)
        for p in pend:
            by_squad[p["squad_key"]].append(p)
        results = []
        for key, items in by_squad.items():
            sq = next(s for s in self.squads.values() if s.key == key)
            cands = self.candidates(sq.id)
            works = []
            recs = {r.key: r for r in Analytics(self.db, Filters(squad=key), self.now).load_all()}
            for it in items:
                r = recs[it["key"]]
                works.append(eng.NewWork(it["key"], it["horas"], {l[6:] for l in r.labels if l.startswith("skill:")},
                                         ({r.epic_key} if r.epic_key else set()) | set(r.components)))
            before = eng.fairness(cands)
            res = eng.distribute(cands, works)
            # equidade depois: reaplica as atribuições
            after_c = [eng.Candidate(**{**c.__dict__}) for c in cands]
            idx = {c.person_id: c for c in after_c}
            for w, x in zip(works, res):
                if x["suggested"]:
                    idx[x["suggested"]].load_h += w.hours
            for x, it in zip(res, items):
                x.update({"summary": it["summary"], "priority": it["priority"], "horas": it["horas"], "squad_key": key,
                          "sugerido": self.name(x["suggested"]) if x["suggested"] else None})
            results.append({"squad": sq.name, "squad_key": key, "sugestoes": res,
                            "equidade_antes": before, "equidade_depois": eng.fairness(after_c)})
        return {"squads": results, "engine_version": eng.ENGINE_VERSION,
                "aviso": "Sugestão calculada, nada é gravado no Jira até um gerente aprovar."}

    def rebalance_suggestions(self, squad_key: str | None = None) -> dict:
        res = []
        for s in self.squads.values():
            if squad_key and s.key != squad_key:
                continue
            a = Analytics(self.db, Filters(squad=s.key), self.now)
            cands = a.candidates(s.id)
            movable = []
            for r in a.load_all():
                if r.category == "todo" and r.assignee_id in {c.person_id for c in cands}:
                    movable.append({"key": r.key, "hours": a.issue_hours(r), "owner_id": r.assignee_id,
                                    "skills_required": [l[6:] for l in r.labels if l.startswith("skill:")],
                                    "affinity_keys": ([r.epic_key] if r.epic_key else []) + list(r.components)})
            before = eng.fairness(cands)
            moves = eng.rebalance(cands, movable)
            after = [eng.Candidate(**{**c.__dict__}) for c in cands]
            idx = {c.person_id: c for c in after}
            for mv in moves:
                idx[mv["from_person_id"]].load_h -= mv["hours"]
                idx[mv["to_person_id"]].load_h += mv["hours"]
            for mv in moves:
                mv["de"], mv["para"] = self.name(mv["from_person_id"]), self.name(mv["to_person_id"])
            res.append({"squad": s.name, "squad_key": s.key, "movimentos": moves, "equidade_antes": before,
                        "equidade_depois": eng.fairness(after),
                        "gatilho": "utilização projetada > 110% nas próximas 4 semanas (inclui efeito de ausências)"})
        return {"squads": res, "engine_version": eng.ENGINE_VERSION}
