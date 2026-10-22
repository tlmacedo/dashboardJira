"""Previsão: Monte Carlo sobre throughput semanal e previsão condicional por idade."""
from __future__ import annotations

from datetime import date, timedelta

import numpy as np

MIN_WEEKS = 8  # abaixo disso o resultado recebe selo de baixa confiança


def monte_carlo_when(weekly_throughput: list[int], remaining_items: int, trials: int = 10000,
                     seed: int = 42, max_weeks: int = 104) -> dict:
    """Quantas semanas até concluir `remaining_items`? Amostra com reposição o throughput histórico."""
    hist = [int(x) for x in weekly_throughput]
    if remaining_items <= 0:
        return {"weeks": {"p50": 0, "p85": 0, "p95": 0}, "confidence": "alta", "samples": len(hist)}
    if not hist or sum(hist) == 0:
        return {"weeks": None, "confidence": "sem dados", "samples": len(hist)}
    rng = np.random.default_rng(seed)
    draws = rng.choice(hist, size=(trials, max_weeks), replace=True)
    cum = np.cumsum(draws, axis=1)
    reached = cum >= remaining_items
    weeks = np.where(reached.any(axis=1), reached.argmax(axis=1) + 1, max_weeks)
    return {
        "weeks": {f"p{p}": int(np.percentile(weeks, p)) for p in (50, 85, 95)},
        "confidence": "baixa" if len(hist) < MIN_WEEKS else "ok",
        "samples": len(hist),
    }


def monte_carlo_how_many(weekly_throughput: list[int], weeks_ahead: int, trials: int = 10000,
                         seed: int = 42) -> dict:
    """Quantos itens até daqui a `weeks_ahead` semanas? Percentis conservadores (P85 = 15º percentil)."""
    hist = [int(x) for x in weekly_throughput]
    if not hist:
        return {"items": None, "samples": 0}
    rng = np.random.default_rng(seed)
    totals = rng.choice(hist, size=(trials, max(1, weeks_ahead)), replace=True).sum(axis=1)
    return {
        "items": {"p50": int(np.percentile(totals, 50)), "p85": int(np.percentile(totals, 15)),
                  "p95": int(np.percentile(totals, 5))},
        "samples": len(hist),
    }


def remaining_given_age(cycle_times_days: list[float], age_days: float, min_n: int = 5) -> dict:
    """Previsão condicional: dos itens históricos que passaram da idade atual, quanto faltou?"""
    longer = sorted(c for c in cycle_times_days if c > age_days)
    if len(longer) < min_n:
        return {"p50": None, "p85": None, "n": len(longer), "note": "poucos itens mais longos que a idade atual"}
    rem = [c - age_days for c in longer]
    return {"p50": round(float(np.percentile(rem, 50)), 1), "p85": round(float(np.percentile(rem, 85)), 1),
            "n": len(longer), "note": ""}


def add_days(d: date, n: float) -> date:
    return d + timedelta(days=int(round(n)))
