"""Simulação de filtros e pesquisas: todos os GET /api/* com combinações de filtros (válidas, vazias e inválidas)
sobre o dataset do modo demonstração. Bugs reais encontrados ficam como xfail com o motivo (strict=False)."""
from __future__ import annotations

import math
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from jiradash.main import app

HOJE = date.today()
D = lambda n: (HOJE - timedelta(days=n)).isoformat()

# nome -> parâmetros de query
COMBOS = {
    "sem_filtro": {},
    "squad": {"squad": "pagamentos"},
    "projeto": {"project": "CX"},
    "squad_projeto_incompativeis": {"squad": "pagamentos", "project": "CX"},
    "squad_inexistente": {"squad": "naoexiste"},
    "pessoa_inexistente": {"person": 99999},
    "tipo_prioridade": {"type": "Bug", "priority": "High"},
    "tipo_inexistente": {"type": "Foo"},
    "periodo_30d": {"date_from": D(30), "date_to": D(0)},
    "periodo_futuro": {"date_from": "2030-01-01", "date_to": "2030-02-01"},
    "periodo_invertido": {"date_from": D(0), "date_to": D(60)},
    "periodo_um_dia": {"date_from": D(0), "date_to": D(0)},
    "periodo_antigo": {"date_from": "1999-01-01", "date_to": "1999-01-02"},
    "so_date_from": {"date_from": D(45)},
    "so_date_to": {"date_to": D(45)},
    "tudo_junto": {"squad": "plataforma", "type": "Bug", "priority": "High", "date_from": D(90), "date_to": D(0)},
}
INVALIDOS = {"pessoa_texto": {"person": "abc"}, "data_impossivel": {"date_from": "2026-13-45"}, "data_texto": {"date_to": "ontem"}}

# (endpoint, combo) -> motivo do bug conhecido (descoberto por esta suíte)
BUGS = {}
for _c in ("periodo_futuro", "periodo_invertido", "periodo_um_dia", "periodo_antigo"):
    BUGS[("/api/review", _c)] = BUGS[("/api/alerts", _c)] = (
        "BUG reports.py:266 review_view: round(None) quando há pares mas nenhum ciclo no período -> 500")
for _c in ("periodo_30d", "periodo_futuro", "periodo_invertido", "periodo_um_dia", "periodo_antigo", "so_date_from", "so_date_to", "tudo_junto"):
    BUGS[("/api/integrity", _c)] = ("BUG api/routes.py integrity: date_from/date_to vão crus para AuditLog.detail (JSON) -> "
                                    "TypeError: date is not JSON serializable -> 500")


@pytest.fixture(scope="module")
def client():
    from jiradash.db import engine
    from pathlib import Path
    engine.dispose()
    db = Path(__file__).parent / "test.db"
    if db.exists():
        db.unlink()
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    engine.dispose()


def endpoints():
    out = []
    for p, d in app.openapi()["paths"].items():
        if "get" not in d or not p.startswith("/api"):
            continue
        if p == "/api/review/pair":
            continue  # exige ?key=; coberto em teste próprio
        if "{view}" in p:
            out += [p.replace("{view}", v) for v in ("open", "closed", "risks")]
        else:
            out.append(p)
    return out


ENDPOINTS = endpoints()
LEVES = ["/api/overview", "/api/open", "/api/closed", "/api/negative", "/api/people", "/api/capacity", "/api/forecast",
         "/api/goals", "/api/review", "/api/integrity", "/api/bottlenecks", "/api/distribution/pending"]


def nao_finito(o, caminho=""):
    if isinstance(o, float) and not math.isfinite(o):
        return caminho
    if isinstance(o, dict):
        for k, v in o.items():
            if (r := nao_finito(v, f"{caminho}/{k}")):
                return r
    if isinstance(o, list):
        for i, v in enumerate(o[:300]):
            if (r := nao_finito(v, f"{caminho}[{i}]")):
                return r


def casos():
    for ep in ENDPOINTS:
        for nome in COMBOS:
            if ep in ("/api/health", "/api/audit", "/api/sync/status") and nome != "sem_filtro":
                continue
            marks = []
            if (ep, nome) in BUGS:
                marks = [pytest.mark.xfail(reason=BUGS[(ep, nome)], strict=False)]
            yield pytest.param(ep, nome, marks=marks, id=f"{ep.removeprefix('/api/')}-{nome}")


@pytest.mark.parametrize("ep,nome", list(casos()))
def test_endpoint_com_filtros_responde_200_sem_nan(client, ep, nome):
    r = client.get(ep, params=COMBOS[nome])
    assert r.status_code == 200, f"{r.status_code}: {r.text[:200]}"
    if ep.endswith(".csv"):
        assert "﻿" in r.text[:5] or r.text.startswith("#") or "key" in r.text
    else:
        assert nao_finito(r.json()) is None, f"NaN/inf em {nao_finito(r.json())}"


@pytest.mark.parametrize("nome", list(INVALIDOS))
@pytest.mark.parametrize("ep", LEVES)
def test_filtro_invalido_da_422_e_nunca_500(client, ep, nome):
    assert client.get(ep, params=INVALIDOS[nome]).status_code == 422


# ----------------------------------------------------------------------------- consistência
def meta(client):
    return client.get("/api/meta").json()


def test_squad_restringe_issues_e_soma_bate(client):
    sq = meta(client)["squads"]
    total = client.get("/api/open").json()["total"]
    soma = 0
    for s in sq:
        p = client.get("/api/open", params={"squad": s["key"]}).json()
        assert {i["squad"] for i in p["issues"]} <= {s["name"]}
        assert p["total"] == len(p["issues"]) or p["total"] >= len(p["issues"])
        soma += p["total"]
    assert soma == total


def test_soma_por_squad_fechadas_negativas_e_visao_geral(client):
    sq = meta(client)["squads"]
    g = client.get("/api/closed").json()["totais"]
    parts = [client.get("/api/closed", params={"squad": s["key"]}).json()["totais"] for s in sq]
    assert sum(p["concluidas"] for p in parts) == g["concluidas"]
    assert sum(p["canceladas_ou_descartadas"] for p in parts) == g["canceladas_ou_descartadas"]
    ov = client.get("/api/overview").json()["cards"]["abertas"]
    assert sum(client.get("/api/overview", params={"squad": s["key"]}).json()["cards"]["abertas"] for s in sq) == ov


def test_projeto_e_pessoa_restringem(client):
    for pj in meta(client)["projects"]:
        key = pj["key"] if isinstance(pj, dict) else pj
        issues = client.get("/api/open", params={"project": key}).json()["issues"]
        assert issues and {i["key"].split("-")[0] for i in issues} == {key}
    pessoas = [p for p in client.get("/api/people").json()["pessoas"] if p["abertas"] > 0][:5]
    for p in pessoas:
        r = client.get("/api/open", params={"person": p["person_id"]}).json()
        assert r["total"] == p["abertas"]


def test_squad_e_pessoa_de_outro_squad_da_vazio(client):
    sq = meta(client)["squads"]
    pessoa = next(p for p in client.get("/api/people").json()["pessoas"] if p["abertas"] > 0)
    outro = client.get("/api/open", params={"person": pessoa["person_id"]}).json()["issues"][0]["squad"]
    chave = next(s["key"] for s in sq if s["name"] != outro)
    assert client.get("/api/open", params={"person": pessoa["person_id"], "squad": chave}).json()["total"] == 0


@pytest.mark.parametrize("combo", ["squad_inexistente", "pessoa_inexistente", "tipo_inexistente", "squad_projeto_incompativeis"])
def test_filtros_sem_resultado_dao_zero(client, combo):
    assert client.get("/api/open", params=COMBOS[combo]).json()["total"] == 0
    t = client.get("/api/closed", params=COMBOS[combo]).json()["totais"]
    assert t["concluidas"] == 0 and t["canceladas_ou_descartadas"] == 0
    ov = client.get("/api/overview", params=COMBOS[combo])
    assert ov.status_code == 200 and ov.json()["cards"]["abertas"] == 0


@pytest.mark.parametrize("combo", ["periodo_futuro", "periodo_antigo", "periodo_invertido"])
def test_periodo_vazio_zera_sem_excecao(client, combo):
    c = client.get("/api/closed", params=COMBOS[combo])
    assert c.status_code == 200
    j = c.json()
    assert j["issues"] == [] and j["totais"]["concluidas"] == 0
    n = client.get("/api/negative", params=COMBOS[combo])
    assert n.status_code == 200
    assert client.get("/api/goals", params=COMBOS[combo]).status_code == 200


def test_periodo_invertido_e_tratado_como_vazio_ou_422(client):
    r = client.get("/api/closed", params=COMBOS["periodo_invertido"])
    assert r.status_code in (200, 422)


def test_periodo_estreito_e_subconjunto_do_largo(client):
    largo = {i["key"] for i in client.get("/api/closed", params={"date_from": D(60), "date_to": D(0)}).json()["issues"]}
    estreito = {i["key"] for i in client.get("/api/closed", params={"date_from": D(10), "date_to": D(0)}).json()["issues"]}
    assert estreito and estreito < largo


def test_tipo_e_prioridade_restringem(client):
    for tp in ("Bug", "Task"):
        issues = client.get("/api/open", params={"type": tp}).json()["issues"]
        assert {i["type"] for i in issues} <= {tp}
    issues = client.get("/api/open", params={"priority": "High"}).json()["issues"]
    assert {i["priority"] for i in issues} <= {"High"}


def test_goals_granularidades_e_invalida(client):
    for g in ("day", "week", "month", "xyz"):
        assert client.get("/api/goals", params={"granularity": g}).status_code == 200


def test_review_pair_chave_inexistente_nao_da_500(client):
    r = client.get("/api/review/pair", params={"key": "NAO-1"})
    assert r.status_code in (200, 404), r.text[:200]


def test_csv_leva_os_filtros_no_cabecalho(client):
    r = client.get("/api/export/open.csv", params={"squad": "pagamentos", "type": "Bug"})
    cab = r.text.splitlines()[0]
    assert "squad=pagamentos" in cab and "tipo=Bug" in cab
    assert client.get("/api/export/naoexiste.csv").status_code == 404
