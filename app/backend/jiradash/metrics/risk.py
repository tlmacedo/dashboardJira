"""Score de risco explicável por issue aberta. Pesos são heurísticos (INFERÊNCIA, calibrar com histórico)."""
from __future__ import annotations

from datetime import date, datetime

from .core import DAY, IssueRec, age_s, pct, reassign_count, reopen_count, time_in_current_status_s

LEVELS = [(60, "alto"), (30, "médio"), (0, "baixo")]


def level(score: int) -> str:
    for lim, name in LEVELS:
        if score >= lim:
            return name
    return "baixo"


def risk_for(rec: IssueRec, now: datetime, cat_of: dict[str, str], ct_hist_days: list[float],
             status_median_days: dict[str, float], predicted_finish: date | None = None) -> dict:
    factors: list[dict] = []

    def add(points: int, why: str):
        factors.append({"points": points, "why": why})

    age_d = age_s(rec, now, cat_of) / DAY
    p85 = pct(ct_hist_days, 85)
    p50 = pct(ct_hist_days, 50)
    if p85 is not None and age_d > p85:
        add(30, f"idade {age_d:.0f} d acima do P85 histórico ({p85:.0f} d)")
    elif p50 is not None and age_d > p50:
        add(10, f"idade {age_d:.0f} d acima da mediana histórica ({p50:.0f} d)")

    stuck = time_in_current_status_s(rec, now) / DAY
    med = status_median_days.get(rec.status)
    if med and stuck > max(2.0, 2 * med):
        add(15, f"parada há {stuck:.0f} d em '{rec.status}' (mediana do status: {med:.1f} d)")

    today = now.date()
    if rec.due:
        if rec.due < today:
            add(25, f"vencida há {(today - rec.due).days} d")
        elif predicted_finish and predicted_finish > rec.due:
            add(20, f"previsão {predicted_finish:%d/%m/%Y} depois do vencimento {rec.due:%d/%m/%Y}")
        elif (rec.due - today).days <= 3:
            add(8, f"vence em {(rec.due - today).days} d")

    if rec.category == "blocked" or rec.flagged:
        add(15, "bloqueada/impedimento sinalizado")
    if reopen_count(rec, cat_of):
        add(10, f"reaberta {reopen_count(rec, cat_of)}x")
    if reassign_count(rec) >= 3:
        add(10, f"{reassign_count(rec)} trocas de responsável")
    if rec.original_estimate_s and rec.time_spent_s > 1.25 * rec.original_estimate_s:
        add(15, f"horas registradas {rec.time_spent_s/3600:.0f} h acima de 125% do estimado ({rec.original_estimate_s/3600:.0f} h)")
    idle = (now - rec.updated).total_seconds() / DAY
    if idle > 7:
        add(10, f"sem atualização há {idle:.0f} d")
    if rec.original_estimate_s is None and rec.story_points is None:
        add(5, "sem estimativa (previsão menos confiável)")

    score = min(100, sum(f["points"] for f in factors))
    return {"score": score, "level": level(score), "factors": sorted(factors, key=lambda f: -f["points"])}
