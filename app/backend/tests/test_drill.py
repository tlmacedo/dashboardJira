"""Detalhamento: o número de cada card deve ser igual à quantidade de tasks da janela aberta ao clicar nele."""
import pytest


def total(client, kind, v1="", v2="", **filtros):
    r = client.get("/api/drill", params={"kind": kind, "v1": v1, "v2": v2, **filtros})
    assert r.status_code == 200, r.text[:200]
    return r.json()["total"]


def test_kind_desconhecido_e_parametro_invalido_dao_422(client):
    assert client.get("/api/drill", params={"kind": "nao_existe"}).status_code == 422
    assert client.get("/api/drill", params={"kind": "p_abertas", "v1": "abc"}).status_code == 422
    assert client.get("/api/drill", params={"kind": "p_semana", "v1": "1", "v2": "lixo"}).status_code == 422


def test_cards_da_visao_geral_batem_com_o_detalhe(client):
    c = client.get("/api/overview").json()["cards"]
    assert total(client, "abertas") == c["abertas"]
    assert total(client, "em_andamento") == c["em_andamento"]
    assert total(client, "vencidas") == c["vencidas"]
    assert total(client, "bloqueadas") == c["bloqueadas"]
    assert total(client, "negligenciadas") == c["negligenciadas"]
    assert total(client, "risco", "alto") == c["risco_alto"]
    assert total(client, "risco", "medio") == c["risco_medio"]


def test_abertas_por_status_prioridade_idade_vencimento_somam_o_total(client):
    d = client.get("/api/open").json()
    assert sum(total(client, "status", x["status"]) for x in d["by_status"]) == d["total"]
    assert sum(total(client, "prioridade", x["priority"]) for x in d["by_priority"]) == d["total"]
    assert sum(total(client, "idade", x["bucket"]) for x in d["age_buckets"]) == d["total"]
    assert sum(total(client, "vencimento", x["bucket"]) for x in d["due_buckets"]) == d["total"]


def test_fechadas_e_negativas(client):
    d = client.get("/api/closed").json()
    assert total(client, "fechadas") == d["totais"]["concluidas"]
    assert total(client, "canceladas") == d["totais"]["canceladas_ou_descartadas"]
    assert sum(total(client, "fechadas_tipo", x["type"]) for x in d["by_type"]) == d["totais"]["concluidas"]
    assert sum(total(client, "fechadas_projeto", x["project"]) for x in d["by_project"]) == d["totais"]["concluidas"]
    for p in d["pessoas"][:5]:
        assert total(client, "fechadas_pessoa", str(p["person_id"])) == p["finalizadas"]
        assert total(client, "fechadas_pessoa", str(p["person_id"]), "participou") == p["participou"]
    n = client.get("/api/negative").json()
    for k, v in n["counts"].items():
        assert total(client, "neg", k) == v, k


def test_pessoas_negligenciadas_qualidade_e_gargalos(client):
    for p in client.get("/api/people").json()["pessoas"][:6]:
        assert total(client, "p_abertas", str(p["person_id"])) == p["abertas"]
        assert total(client, "p_abertas", str(p["person_id"]), "wip") == p["wip"]
        assert total(client, "p_abertas", str(p["person_id"]), "vencidas") == p["vencidas"]
    for k, v in client.get("/api/neglected").json()["counts"].items():
        assert total(client, "neglig", k) == v, k
    q = client.get("/api/quality").json()
    for ch in q["checks"]:
        assert total(client, "qualidade", ch["id"]) == ch["qtd"], ch["id"]
    for e in client.get("/api/bottlenecks").json()["estados"]:
        assert total(client, "etapa", e["status"]) == e["itens"]


def test_metas_cada_faixa_bate_e_dimensoes_somam(client):
    g = client.get("/api/goals").json()
    for x in g["distribuicao"]:
        assert total(client, "meta", x["bucket"]) == x["qtd"], x["bucket"]
    assert total(client, "meta", "all") == g["kpis"]["com_vencimento"]
    assert total(client, "meta", "sem_venc") == g["kpis"]["sem_vencimento"]
    for t in g["por_tipo"]:
        assert total(client, "meta", "all", f"tipo={t['nome']}") == t["total"]
    for s in g["serie"][:4]:
        assert total(client, "meta", "all", f"periodo={s['periodo']}&g=week") == s["total"]
    p = g["por_pessoa"][0]
    assert total(client, "meta", "all", f"pessoa={p['chave']}") == p["total"]
    assert total(client, "meta", "atrasadas") + total(client, "meta", "no_prazo") == g["kpis"]["com_vencimento"]


def test_review_integridade_epicos_e_capacidade(client):
    r = client.get("/api/review").json()
    assert total(client, "r_pares", "all") == 2 * r["kpis"]["pares"]
    assert total(client, "r_sem_review") == r["kpis"]["checklists_sem_review"]
    a = client.get("/api/integrity").json()
    assert total(client, "i_sinal") == a["resumo"]["issues_afetadas"]
    for s in a["resumo"]["por_sinal"]:
        assert total(client, "i_sinal", s["sinal"]) >= 1
    ep = client.get("/api/epics").json()["epicos"][0]
    assert total(client, "epico", ep["epico"]) == ep["itens"]
    assert total(client, "epico", ep["epico"], "abertos") == ep["abertos"]
    cap = client.get("/api/capacity").json()
    assert total(client, "slips") == len(cap["atrasos_previstos"])
    pess = cap["pessoas"][0]
    assert total(client, "p_semana", str(pess["person_id"]), cap["semanas"][0]) >= 0


def test_central_de_atencao_tem_drill_valido(client):
    for it in client.get("/api/alerts").json()["itens"]:
        if it["drill"]:
            assert total(client, *it["drill"]) >= 0


def test_detalhe_respeita_filtros_e_traz_colunas(client):
    todos = total(client, "abertas")
    pay = client.get("/api/drill", params={"kind": "abertas", "squad": "pagamentos"}).json()
    assert 0 < pay["total"] < todos and {i["squad"] for i in pay["issues"]} == {"Pagamentos"}
    row = pay["issues"][0]
    assert {"key", "summary", "assignee", "status", "priority", "idade_d", "created"} <= set(row)
