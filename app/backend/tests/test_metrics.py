from datetime import date, datetime, timedelta

from jiradash.metrics import core as m
from jiradash.metrics import capacity as cap
from jiradash.metrics import forecast as fc
from jiradash.metrics import risk as rk

CAT = {"A Fazer": "todo", "Em Andamento": "active", "Em Revisão": "wait", "Bloqueado": "blocked", "Concluído": "done"}
T0 = datetime(2026, 10, 1, 9, 0)


def rec(**kw):
    base = dict(id=1, key="X-1", summary="s", project="X", type="Tarefa", priority="Média", status="Concluído",
                category="done", resolution="Resolvido", assignee_id=1, squad_id=1, created=T0, updated=T0,
                resolved=T0 + timedelta(days=10), due=None, original_estimate_s=None, remaining_s=None,
                time_spent_s=0, story_points=None)
    base.update(kw)
    return m.IssueRec(**base)


def test_status_durations_somam_reentradas():
    r = rec(transitions=[
        m.Transition(T0 + timedelta(days=1), "A Fazer", "Em Andamento"),
        m.Transition(T0 + timedelta(days=4), "Em Andamento", "Em Revisão"),
        m.Transition(T0 + timedelta(days=5), "Em Revisão", "Em Andamento"),
        m.Transition(T0 + timedelta(days=8), "Em Andamento", "Concluído"),
    ], resolved=T0 + timedelta(days=8))
    d = m.status_durations(r, T0 + timedelta(days=20))
    assert d["A Fazer"] == 1 * m.DAY
    assert d["Em Andamento"] == 6 * m.DAY  # 3 + 3
    assert d["Em Revisão"] == 1 * m.DAY
    assert m.cycle_time_s(r, CAT) == 7 * m.DAY
    assert m.lead_time_s(r) == 8 * m.DAY


def test_reabertura_e_devolucao():
    order = ["A Fazer", "Em Andamento", "Em Revisão", "Concluído"]
    r = rec(transitions=[
        m.Transition(T0, "A Fazer", "Em Andamento"), m.Transition(T0, "Em Andamento", "Em Revisão"),
        m.Transition(T0, "Em Revisão", "Em Andamento"),  # devolução
        m.Transition(T0, "Em Andamento", "Concluído"), m.Transition(T0, "Concluído", "Em Andamento"),  # reabertura
    ])
    assert m.return_count(r, order, CAT) == 1
    assert m.reopen_count(r, CAT) == 1


def test_tempo_ativo_por_pessoa_com_troca_de_responsavel():
    r = rec(assignee_id=2, transitions=[
        m.Transition(T0, "A Fazer", "Em Andamento"), m.Transition(T0 + timedelta(days=4), "Em Andamento", "Concluído")],
        resolved=T0 + timedelta(days=4), assignee_changes=[(T0 + timedelta(days=1), 1, 2)])
    at = m.active_time_by_person(r, T0 + timedelta(days=9), CAT)
    assert at[1] == 1 * m.DAY and at[2] == 3 * m.DAY


def test_percentil_e_jain():
    assert m.pct([], 50) is None
    assert m.pct([1, 2, 3, 4], 50) == 2.5
    assert m.jain_index([1, 1, 1, 1]) == 1.0
    assert m.jain_index([4, 0, 0, 0]) == 0.25


def test_monte_carlo_deterministico_e_monotono():
    a = fc.monte_carlo_when([5, 6, 4, 5, 7, 5, 6, 4, 5, 5], 50)
    b = fc.monte_carlo_when([5, 6, 4, 5, 7, 5, 6, 4, 5, 5], 50)
    assert a == b and a["weeks"]["p50"] <= a["weeks"]["p85"] <= a["weeks"]["p95"]
    assert 9 <= a["weeks"]["p50"] <= 11
    assert fc.monte_carlo_when([3, 4], 10)["confidence"] == "baixa"
    assert fc.monte_carlo_when([0, 0], 10)["weeks"] is None
    assert fc.monte_carlo_when([1], 0)["weeks"]["p50"] == 0


def test_capacidade_desconta_feriado_e_ausencia():
    p = cap.PersonCap(1, "A", weekly_hours=40, focus=0.5, absences=[(date(2026, 11, 3), date(2026, 11, 4), 1.0)])
    hol = {date(2026, 11, 2)}  # segunda: Finados
    w = cap.weekly_capacity(p, date(2026, 11, 2), 1, hol)[0]
    assert w["workdays_nominal"] == 4
    assert w["workdays_net"] == 2.0
    assert w["hours"] == 2 * 8 * 0.5 * 1.0 * 1  # 2 dias úteis líquidos × 4 h


def test_agenda_preenche_semanas_na_ordem():
    p = cap.PersonCap(1, "A", weekly_hours=40, focus=1.0)
    weeks = cap.weekly_capacity(p, date(2026, 11, 2), 3, set())
    out = cap.schedule([("a", 30), ("b", 30), ("c", 500)], weeks, date(2026, 11, 2))
    assert out["a"] < out["b"] and out["c"] is None


def test_risco_explicavel():
    hist = [3.0, 4.0, 5.0, 6.0, 7.0]
    r = rec(category="active", status="Em Andamento", resolved=None, resolution=None, due=date(2026, 10, 5),
            updated=T0, transitions=[m.Transition(T0, "A Fazer", "Em Andamento")])
    out = rk.risk_for(r, T0 + timedelta(days=20), CAT, hist, {"Em Andamento": 2.0})
    why = " ".join(f["why"] for f in out["factors"])
    assert out["level"] == "alto" and "P85" in why and "vencida" in why and "sem atualização" in why
