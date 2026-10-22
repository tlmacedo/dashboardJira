"""Capacidade líquida por semana (dias úteis − feriados − ausências) e agenda por capacidade."""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta

from .core import week_start

DEFAULT_HOURS_PER_POINT = 4.0  # fallback quando não há estimativa em horas (configurável)


@dataclass
class PersonCap:
    id: int
    name: str
    weekly_hours: float = 40.0
    focus: float = 0.7
    allocation: float = 1.0
    absences: list[tuple[date, date, float]] = field(default_factory=list)  # (início, fim, fração do dia)


def is_workday(d: date, holidays: set[date]) -> bool:
    return d.weekday() < 5 and d not in holidays


def absence_fraction(p: PersonCap, d: date) -> float:
    f = 0.0
    for s, e, frac in p.absences:
        if s <= d <= e:
            f = max(f, frac)
    return f


def day_capacity_h(p: PersonCap, d: date, holidays: set[date]) -> float:
    if not is_workday(d, holidays):
        return 0.0
    base = p.weekly_hours / 5.0 * p.focus * p.allocation
    return base * (1.0 - absence_fraction(p, d))


def weekly_capacity(p: PersonCap, start: date, weeks: int, holidays: set[date]) -> list[dict]:
    out = []
    w0 = week_start(start)
    for i in range(weeks):
        w = w0 + timedelta(days=7 * i)
        days_ = [w + timedelta(days=k) for k in range(7)]
        # a semana corrente só conta dias de hoje em diante
        days_ = [d for d in days_ if d >= start]
        nominal = sum(1 for d in [w + timedelta(days=k) for k in range(7)] if is_workday(d, holidays))
        net_days = sum(1 - absence_fraction(p, d) for d in days_ if is_workday(d, holidays))
        out.append({
            "week": w.isoformat(),
            "hours": round(sum(day_capacity_h(p, d, holidays) for d in days_), 1),
            "workdays_nominal": nominal,
            "workdays_net": round(net_days, 1),
            "absent_days": round(sum(absence_fraction(p, d) for d in days_ if is_workday(d, holidays)), 1),
        })
    return out


def issue_hours(remaining_s: int | None, original_s: int | None, spent_s: int, points: float | None,
                fallback_h: float = 6.0) -> float:
    """Horas restantes estimadas: restante > original−gasto > pontos×h/ponto > fallback."""
    if remaining_s is not None:
        return remaining_s / 3600.0
    if original_s:
        return max(0.5, (original_s - spent_s) / 3600.0)
    if points:
        return points * DEFAULT_HOURS_PER_POINT
    return fallback_h


def schedule(items: list[tuple[str, float]], cap_by_week: list[dict], start: date) -> dict[str, date | None]:
    """Preenche a capacidade semanal na ordem recebida; devolve data prevista de término por item.

    A data cai proporcionalmente dentro da semana em que o item termina. None = fora do horizonte.
    """
    remaining = [w["hours"] for w in cap_by_week]
    out: dict[str, date | None] = {}
    wi = 0
    for key, h in items:
        need = h
        while need > 1e-9 and wi < len(remaining):
            take = min(need, remaining[wi])
            remaining[wi] -= take
            need -= take
            if need > 1e-9:
                wi += 1
        if need > 1e-9:
            out[key] = None
        else:
            w0 = date.fromisoformat(cap_by_week[wi]["week"])
            total = cap_by_week[wi]["hours"] or 1.0
            used = total - remaining[wi]
            offset = min(4, int(4 * used / total + 0.999))
            d = max(start, w0 + timedelta(days=max(0, offset - 1)))
            while d.weekday() >= 5:
                d += timedelta(days=1)
            out[key] = d
    return out
