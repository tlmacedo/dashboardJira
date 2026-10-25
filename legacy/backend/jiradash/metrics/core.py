"""Métricas de fluxo sobre registros leves (sem acesso ao banco), para serem testáveis.

Definições (inferência desta pesquisa, ver relatório; ajuste ao seu fluxo em equipes.yaml):
- lead time: criação até a resolução.
- cycle time: primeira entrada em categoria 'active' até a resolução.
- idade (work item age): agora menos a primeira entrada em 'active' (ou criação, se ainda não iniciou).
- reabertura: transição de categoria 'done' para outra categoria.
- devolução: transição para status anterior na ordem do fluxo (workflow_order).
- tempo calendário (não útil). Horário útil é uma evolução possível.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from typing import Iterable, Sequence

import numpy as np

DAY = 86400.0


def local_date(dt: datetime) -> date:
    """Data civil no fuso configurado (JIRADASH_TZ). Datas do banco estão em UTC sem fuso."""
    from datetime import timezone
    from zoneinfo import ZoneInfo

    from ..config import settings
    return dt.replace(tzinfo=timezone.utc).astimezone(ZoneInfo(settings.tz)).date()


@dataclass
class Transition:
    at: datetime
    from_status: str | None
    to_status: str
    author_id: int | None = None


@dataclass
class Event:
    at: datetime
    field: str
    from_value: str | None
    to_value: str | None
    author_id: int | None = None


@dataclass
class AssigneeInterval:
    person_id: int | None
    start: datetime
    end: datetime


@dataclass
class IssueRec:
    id: int
    key: str
    summary: str
    project: str
    type: str
    priority: str
    status: str
    category: str
    resolution: str | None
    assignee_id: int | None
    squad_id: int | None
    created: datetime
    updated: datetime
    resolved: datetime | None
    due: date | None
    original_estimate_s: int | None
    remaining_s: int | None
    time_spent_s: int
    story_points: float | None
    labels: list = field(default_factory=list)
    components: list = field(default_factory=list)
    epic_key: str | None = None
    flagged: bool = False
    transitions: list[Transition] = field(default_factory=list)
    assignee_changes: list[tuple[datetime, int | None, int | None]] = field(default_factory=list)
    parent_key: str | None = None
    events: list[Event] = field(default_factory=list)
    reporter_id: int | None = None


def pct(values: Sequence[float], p: float) -> float | None:
    """Percentil com interpolação linear; None se não houver dados."""
    if not len(values):
        return None
    return float(np.percentile(np.asarray(values, dtype=float), p))


def mean(values: Sequence[float]) -> float | None:
    return float(np.mean(values)) if len(values) else None


def days(seconds: float | None) -> float | None:
    return None if seconds is None else round(seconds / DAY, 2)


def status_durations(rec: IssueRec, now: datetime) -> dict[str, float]:
    """Segundos em cada status, somando reentradas. Parte da criação e vai até resolução/agora."""
    out: dict[str, float] = {}
    end_total = rec.resolved if (rec.category == "done" and rec.resolved) else now
    trs = sorted(rec.transitions, key=lambda t: t.at)
    if not trs:
        out[rec.status] = max(0.0, (end_total - rec.created).total_seconds())
        return out
    cur = trs[0].from_status or "?"
    since = rec.created
    for t in trs:
        out[cur] = out.get(cur, 0.0) + max(0.0, (t.at - since).total_seconds())
        cur, since = t.to_status, t.at
    out[cur] = out.get(cur, 0.0) + max(0.0, (end_total - since).total_seconds())
    return out


def first_active_at(rec: IssueRec, cat_of: dict[str, str]) -> datetime | None:
    for t in sorted(rec.transitions, key=lambda t: t.at):
        if cat_of.get(t.to_status) in ("active", "wait", "blocked"):
            return t.at
    return None


def lead_time_s(rec: IssueRec) -> float | None:
    return (rec.resolved - rec.created).total_seconds() if rec.resolved else None


def cycle_time_s(rec: IssueRec, cat_of: dict[str, str]) -> float | None:
    if not rec.resolved:
        return None
    start = first_active_at(rec, cat_of)
    return (rec.resolved - start).total_seconds() if start else None


def age_s(rec: IssueRec, now: datetime, cat_of: dict[str, str]) -> float:
    start = first_active_at(rec, cat_of) or rec.created
    return max(0.0, (now - start).total_seconds())


def time_in_current_status_s(rec: IssueRec, now: datetime) -> float:
    last = max((t.at for t in rec.transitions), default=rec.created)
    return max(0.0, (now - last).total_seconds())


def reopen_count(rec: IssueRec, cat_of: dict[str, str]) -> int:
    return sum(
        1 for t in rec.transitions
        if t.from_status and cat_of.get(t.from_status) == "done" and cat_of.get(t.to_status) != "done"
    )


def return_count(rec: IssueRec, order: list[str], cat_of: dict[str, str]) -> int:
    """Devoluções: volta a um status anterior do fluxo (exclui reabertura, contada à parte)."""
    idx = {s: i for i, s in enumerate(order)}
    n = 0
    for t in rec.transitions:
        if t.from_status in idx and t.to_status in idx and idx[t.to_status] < idx[t.from_status]:
            if cat_of.get(t.from_status) != "done":
                n += 1
    return n


def reassign_count(rec: IssueRec) -> int:
    return sum(1 for _, a, b in rec.assignee_changes if a is not None and b is not None and a != b)


def assignee_intervals(rec: IssueRec, now: datetime) -> list[AssigneeInterval]:
    """Reconstrói quem era o responsável em cada intervalo a partir das trocas."""
    end_total = rec.resolved or now
    changes = sorted(rec.assignee_changes, key=lambda c: c[0])
    out: list[AssigneeInterval] = []
    if not changes:
        return [AssigneeInterval(rec.assignee_id, rec.created, end_total)]
    cur, since = changes[0][1], rec.created
    for at, _, to in changes:
        out.append(AssigneeInterval(cur, since, at))
        cur, since = to, at
    out.append(AssigneeInterval(cur, since, end_total))
    return out


def active_time_by_person(rec: IssueRec, now: datetime, cat_of: dict[str, str]) -> dict[int, float]:
    """Segundos em status 'active' atribuídos a quem era responsável naquele momento."""
    out: dict[int, float] = {}
    trs = sorted(rec.transitions, key=lambda t: t.at)
    segs: list[tuple[datetime, datetime]] = []
    end_total = rec.resolved if (rec.category == "done" and rec.resolved) else now
    cur = trs[0].from_status if trs else rec.status
    since = rec.created
    for t in trs:
        if cat_of.get(cur) == "active":
            segs.append((since, t.at))
        cur, since = t.to_status, t.at
    if cat_of.get(cur) == "active":
        segs.append((since, end_total))
    for s, e in segs:
        for iv in assignee_intervals(rec, now):
            lo, hi = max(s, iv.start), min(e, iv.end)
            if hi > lo and iv.person_id is not None:
                out[iv.person_id] = out.get(iv.person_id, 0.0) + (hi - lo).total_seconds()
    return out


def week_start(d: date) -> date:
    return d - timedelta(days=d.weekday())


def weekly_throughput(recs: Iterable[IssueRec], start: date, end: date) -> list[tuple[date, int]]:
    """Itens concluídos por semana (segunda a domingo) entre start e end."""
    weeks: dict[date, int] = {}
    w = week_start(start)
    while w <= end:
        weeks[w] = 0
        w += timedelta(days=7)
    for r in recs:
        if r.resolved and start <= r.resolved.date() <= end:
            weeks[week_start(r.resolved.date())] = weeks.get(week_start(r.resolved.date()), 0) + 1
    return sorted(weeks.items())


def is_negative_resolution(rec: IssueRec, negative: set[str]) -> bool:
    return bool(rec.resolution and rec.resolution in negative)


def estimate_ratio(rec: IssueRec) -> float | None:
    """Realizado ÷ estimado; None sem estimativa ou sem horas registradas."""
    if rec.original_estimate_s and rec.time_spent_s:
        return rec.time_spent_s / rec.original_estimate_s
    return None


def on_time(rec: IssueRec) -> bool | None:
    if rec.resolved is None or rec.due is None:
        return None
    return local_date(rec.resolved) <= rec.due


def jain_index(values: Sequence[float]) -> float | None:
    """Índice de Jain (Σx)² / (n·Σx²): 1 = distribuição perfeitamente igual."""
    xs = [float(v) for v in values]
    if not xs or sum(x * x for x in xs) == 0:
        return None
    return round(sum(xs) ** 2 / (len(xs) * sum(x * x for x in xs)), 3)
