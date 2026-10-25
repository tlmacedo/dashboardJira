from datetime import date, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from jiradash import reports
from jiradash.main import app
from jiradash.metrics import core as m


def test_bucket_of_cobre_d_menos_n():
    assert reports.bucket_of(-7) == "D-3 ou antes" and reports.bucket_of(-2) == "D-2" and reports.bucket_of(-1) == "D-1"
    assert reports.bucket_of(0) == "No dia (D)" and reports.bucket_of(2) == "Atrasada 1-3 d" and reports.bucket_of(9) == "Atrasada > 3 d"


def test_parse_due_formatos():
    assert reports.parse_due("2026-10-05") == date(2026, 10, 5)
    assert reports.parse_due("05/10/2026") == date(2026, 10, 5)
    assert reports.parse_due(None) is None and reports.parse_due("lixo") is None


def _rec(**kw):
    base = dict(id=1, key="X-1", summary="s", project="X", type="Tarefa", priority="Média", status="Concluído", category="done",
                resolution="Resolvido", assignee_id=1, squad_id=1, created=datetime(2026, 10, 1), updated=datetime(2026, 10, 9),
                resolved=datetime(2026, 10, 9, 15), due=date(2026, 10, 10), original_estimate_s=None, remaining_s=None,
                time_spent_s=0, story_points=None)
    base.update(kw)
    return m.IssueRec(**base)


def test_data_original_ignora_prorrogacao():
    r = _rec(due=date(2026, 10, 10), events=[m.Event(datetime(2026, 10, 7), "duedate", "2026-10-05", "2026-10-10", 1)])
    assert reports.original_due(r) == date(2026, 10, 5)
    assert reports.original_due(_rec()) == date(2026, 10, 10)


def test_metas_somam_e_respeitam_filtros(client):
    g = client.get("/api/goals").json()
    assert sum(x["qtd"] for x in g["distribuicao"]) == g["kpis"]["com_vencimento"]
    assert g["kpis"]["no_prazo_pct"] >= g["kpis"]["no_prazo_data_original_pct"] - 0.01  # prorrogar nunca piora o prazo atual
    for gran in ("day", "week", "month"):
        s = client.get(f"/api/goals?granularity={gran}").json()["serie"]
        assert sum(x["total"] for x in s) == g["kpis"]["com_vencimento"]
    vazio = client.get("/api/goals?date_from=2020-01-01&date_to=2020-01-02").json()
    assert vazio["kpis"]["concluidas"] == 0 and vazio["kpis"]["no_prazo_pct"] is None


def test_integridade_destaca_padroes_plantados_no_demo(client):
    d = client.get("/api/integrity").json()
    alto = {p["nome"] for p in d["pessoas"] if p["nivel"] == "alto"}
    plantados = {"Fábio Nunes", "Diego Rocha", "Tiago Lopes", "Rafael Melo", "Otávio Braga"}
    assert plantados <= alto, f"não detectados: {plantados - alto}"
    assert len(alto) <= 6  # poucos falsos positivos
    fabio = next(p for p in d["pessoas"] if p["nome"] == "Fábio Nunes")
    assert fabio["impacto_na_meta_pp"] > 10 and "prazo_resgatado" in fabio["sinais"]
    assert all(e["detalhe"] and e["issue"] for e in d["evidencias"])
    assert "revisão humana" in d["aviso"]


def test_acesso_a_integridade_e_auditado(client):
    client.get("/api/integrity", headers={"X-User": "ana.gestora"})
    log = client.get("/api/audit").json()
    assert any(l["acao"] == "consultar_integridade" and l["quem"] == "ana.gestora" for l in log)


def test_review_pares_e_aprendizados(client):
    d = client.get("/api/review").json()
    assert d["kpis"]["pares"] > 20
    p = d["pares"][0]
    assert p["checklist"] != p["review"] and "so_review" in p and "dif_vencimento_d" in p
    # labels em comum + só de um lado são disjuntas
    for x in d["pares"]:
        assert not (set(x["labels_comuns"]) & set(x["so_checklist"])) and not (set(x["labels_comuns"]) & set(x["so_review"]))
    det = client.get(f"/api/review/pair?key={p['checklist']}").json()
    assert det["checklist"]["timeline"] and det["reviews"][0]["timeline"]
    assert client.get("/api/review?squad=inexistente").json()["kpis"]["pares"] == 0


def test_epicos_e_central_de_atencao(client):
    e = client.get("/api/epics").json()["epicos"]
    assert e and all(0 <= x["progresso_pct"] <= 100 for x in e)
    a = client.get("/api/alerts").json()["itens"]
    assert len(a) >= 10 and all(i["tela"] for i in a)
