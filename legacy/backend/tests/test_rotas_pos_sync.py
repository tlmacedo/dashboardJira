"""As rotas de análise (/api/*) sobre dados vindos do Jira SIMULADO (Cloud e DC), via Syncer, em SQLite temporário."""
from __future__ import annotations

from datetime import datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from jiradash.db import get_db
from jiradash.models import Issue, Squad

FLAVORS = ["cloud", "dc"]
GETS = ["meta", "overview", "open", "neglected", "closed", "negative", "time-in-status", "people", "risks", "forecast",
        "bottlenecks", "capacity", "quality", "goals", "review", "epics", "integrity", "alerts", "distribution/pending",
        "distribution/suggestions", "audit", "sync/status", "health"]


@pytest.fixture(scope="module", params=FLAVORS)
def api(request, tmp_path_factory):
    """Sincroniza o simulado uma vez por variante e expõe o app real com o get_db apontado para o banco temporário."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from jiradash import cache, teams
    from jiradash.db import Base
    from jiradash.jira.client import JiraClient
    from jiradash.jira.sync import Syncer
    from jiradash.main import app
    from mock_jira import MockConfig, MockJira

    mp = pytest.MonkeyPatch()
    mp.setattr(cache, "TTL", 0)
    cache.invalidate()
    eng = create_engine(f"sqlite:///{tmp_path_factory.mktemp('api') / 'r.db'}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(eng)
    Session = sessionmaker(bind=eng, expire_on_commit=False)
    with Session() as db:
        teams.bootstrap(db)
        mock = MockJira(MockConfig(flavor=request.param, n_issues=240, seed=11))
        c = JiraClient(request.param, mock.base_url, mock.token, mock.email, transport=mock.transport())
        Syncer(db, c).run("project is not EMPTY", full=True)

    def _db():
        s = Session()
        try:
            yield s
        finally:
            s.close()

    app.dependency_overrides[get_db] = _db
    tc = TestClient(app)  # sem 'with': não dispara o lifespan (que semearia o banco do demo)
    tc.mock, tc.Session = mock, Session
    yield tc
    app.dependency_overrides.pop(get_db, None)
    mp.undo()
    eng.dispose()


@pytest.mark.parametrize("path", GETS)
def test_todas_as_rotas_respondem_200(api, path):
    r = api.get(f"/api/{path}")
    assert r.status_code == 200, r.text[:300]
    assert r.json() is not None


def test_abertas_batem_com_o_banco(api):
    with api.Session() as db:
        abertas = db.query(Issue).filter(Issue.status_category != "done", Issue.deleted_at.is_(None)).count()
    assert api.get("/api/open").json()["total"] == abertas == api.mock.expected()["abertas"]
    assert api.get("/api/overview").json()["cards"]["abertas"] == abertas


def test_soma_por_squad_e_igual_ao_total(api):
    total = api.get("/api/open").json()["total"]
    squads = [s["key"] for s in api.get("/api/meta").json()["squads"]]
    assert len(squads) == 3
    por = {s: api.get(f"/api/open?squad={s}").json() for s in squads}
    assert sum(p["total"] for p in por.values()) == total
    for s, p in por.items():
        nome = next(x["name"] for x in api.get("/api/meta").json()["squads"] if x["key"] == s)
        assert {i["squad"] for i in p["issues"]} <= {nome}


def test_fechadas_30d_batem_com_o_banco(api):
    corte = (datetime.utcnow() - timedelta(days=29)).date()  # a tela usa hoje−29 .. hoje (30 dias corridos)
    with api.Session() as db:
        n = sum(1 for i in db.query(Issue).filter(Issue.resolved.isnot(None), Issue.deleted_at.is_(None)).all()
                if i.resolved.date() >= corte and i.resolved.date() <= datetime.utcnow().date())
    closed = api.get("/api/closed").json()
    assert len(closed["issues"]) == n > 0
    assert closed["totais"]["concluidas"] + closed["totais"]["canceladas_ou_descartadas"] == n


def test_filtro_de_pessoa_e_de_tipo(api):
    pessoas = api.get("/api/people").json()["pessoas"]
    p = next(x for x in pessoas if x["abertas"] > 0)
    r = api.get(f"/api/open?person={p['person_id']}").json()
    assert r["total"] == p["abertas"] and {i["assignee"] for i in r["issues"]} == {p["nome"]}
    t = api.get("/api/open?type=Bug").json()
    assert 0 < t["total"] < api.get("/api/open").json()["total"] and {i["type"] for i in t["issues"]} == {"Bug"}


def test_revisao_enxerga_pares_checklist_review(api):
    r = api.get("/api/review").json()
    assert r["pares"], "nenhum par checklist/review depois do sync (parent_key não chegou?)"
    mock = api.mock
    pais = {i.parent for i in mock.live() if i.type == "Review"}
    assert {p["checklist"] for p in r["pares"]} <= pais


def test_integridade_ve_mudancas_de_vencimento_e_labels(api):
    r = api.get("/api/integrity").json()
    assert r["catalogo"] is not None and r["resumo"] is not None


def test_integridade_com_periodo(api):
    assert api.get("/api/integrity?date_from=2000-01-01").status_code == 200


def test_pessoas_em_ordem_alfabetica(api):
    nomes = [p["nome"] for p in api.get("/api/people").json()["pessoas"]]
    assert nomes == sorted(nomes)


def test_sync_pela_rota_em_modo_jira(api, tmp_path, monkeypatch):
    """POST /api/sync → jobs.run_sync → Syncer (cliente trocado pelo simulado) e cache invalidado."""
    import shutil

    from jiradash import config, jobs
    from jiradash.config import settings
    from jiradash.jira.client import JiraClient

    d = tmp_path / "cfg"
    d.mkdir()
    shutil.copy(config.CONFIG_DIR / "equipes.demo.yaml", d / "equipes.yaml")
    monkeypatch.setattr(config, "CONFIG_DIR", d)
    monkeypatch.setattr(settings, "mode", "jira")
    m = api.mock
    monkeypatch.setattr(jobs, "make_client", lambda: JiraClient(m.flavor, m.base_url, m.token, m.email, transport=m.transport()))
    r = api.post("/api/sync?full=true")
    assert r.status_code == 200, r.text
    assert r.json()["issues"] == m.expected()["issues"] and r.json()["removidas"] == 0
    assert api.get("/api/sync/status").json()["ultima_sincronizacao"]
