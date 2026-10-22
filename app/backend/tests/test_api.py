import pytest
from fastapi.testclient import TestClient

from jiradash.main import app


@pytest.mark.parametrize("path", ["meta", "overview", "open", "neglected", "closed", "negative", "time-in-status", "people",
                                  "risks", "forecast", "bottlenecks", "capacity", "quality", "distribution/pending", "audit"])
def test_endpoints_respondem(client, path):
    assert client.get(f"/api/{path}").status_code == 200


def test_filtro_por_squad_restringe(client):
    todos = client.get("/api/open").json()["total"]
    pay = client.get("/api/open?squad=pagamentos").json()
    assert 0 < pay["total"] < todos
    assert {i["squad"] for i in pay["issues"]} == {"Pagamentos"}


def test_pessoas_em_ordem_alfabetica_sem_ranking(client):
    nomes = [p["nome"] for p in client.get("/api/people").json()["pessoas"]]
    assert nomes == sorted(nomes)


def test_fluxo_sugerir_aprovar_e_auditar(client):
    res = client.post("/api/distribution/suggest", json={"squad": "pagamentos"}).json()
    assert res["salvas"] > 0
    sug = client.get("/api/distribution/suggestions?status=pendente").json()[0]
    # trocar de pessoa exige motivo
    pessoas = client.get("/api/meta").json()["people"]
    outro = next(p for p in pessoas if p["name"] != sug["sugerido"] and p["role"] != "gerente")
    assert client.post(f"/api/distribution/{sug['id']}/decide", json={"action": "aprovar", "person_id": outro["id"]}).status_code == 422
    ok = client.post(f"/api/distribution/{sug['id']}/decide", json={"action": "aprovar"}, headers={"X-User": "marcos"})
    assert ok.status_code == 200 and ok.json()["status"] == "aplicada"
    assert client.post(f"/api/distribution/{sug['id']}/decide", json={"action": "aprovar"}).status_code == 404
    log = client.get("/api/audit").json()
    assert log[0]["acao"] == "aprovar_sugestao" and log[0]["quem"] == "marcos"


def test_rebalanceamento_melhora_equidade(client):
    res = client.post("/api/rebalance/suggest").json()
    movidos = [s for s in res["squads"] if s["movimentos"]]
    assert movidos
    for s in movidos:
        assert s["equidade_depois"]["jain"] >= s["equidade_antes"]["jain"]


def test_csv_exporta_com_filtros(client):
    r = client.get("/api/export/open.csv?squad=pagamentos")
    assert r.status_code == 200 and "squad=pagamentos" in r.text.splitlines()[0].lstrip("﻿")


def test_chave_de_api(client, monkeypatch):
    from jiradash.config import settings
    monkeypatch.setattr(settings, "api_key", "segredo")
    assert client.get("/api/overview").status_code == 401
    assert client.get("/api/overview", headers={"X-API-Key": "segredo"}).status_code == 200
