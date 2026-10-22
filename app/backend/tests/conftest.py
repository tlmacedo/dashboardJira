import os
import sys
from pathlib import Path

os.environ["JIRADASH_MODE"] = "demo"
os.environ["DATABASE_URL"] = "sqlite:///" + str(Path(__file__).parent / "test.db")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


import pytest  # noqa: E402


@pytest.fixture(scope="session")
def client():
    """Um único app/banco de demonstração para toda a sessão de testes."""
    from fastapi.testclient import TestClient
    db = Path(__file__).parent / "test.db"
    if db.exists():
        db.unlink()
    from jiradash.main import app
    with TestClient(app) as c:
        yield c


# ---------------------------------------------------------------------------------------------
# Fixtures de integração com o Jira simulado (banco SQLite temporário; nunca o banco do demo)
# ---------------------------------------------------------------------------------------------
import pytest  # noqa: E402


@pytest.fixture
def sleeps(monkeypatch):
    """Substitui time.sleep (backoff do cliente) e devolve a lista de esperas pedidas."""
    pedidas: list[float] = []
    monkeypatch.setattr("time.sleep", lambda s: pedidas.append(s))
    return pedidas


@pytest.fixture
def jira_env(tmp_path, monkeypatch, sleeps):
    """Fábrica: env = jira_env(flavor='cloud', n_issues=60, **cfg) -> objeto com db, mock, client, syncer()."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from jiradash import cache, teams
    from jiradash.db import Base
    from jiradash.jira.client import JiraClient
    from jiradash.jira.sync import Syncer
    from mock_jira import MockConfig, MockJira

    monkeypatch.setattr(cache, "TTL", 0)  # sem cache entre bancos diferentes
    cache.invalidate()
    made = []

    class Env:
        pass

    def factory(flavor="cloud", **cfg):
        env = Env()
        env.engine = create_engine(f"sqlite:///{tmp_path / f'int_{len(made)}.db'}", connect_args={"check_same_thread": False})
        Base.metadata.create_all(env.engine)
        env.Session = sessionmaker(bind=env.engine, expire_on_commit=False)
        env.db = env.Session()
        teams.bootstrap(env.db)
        env.mock = MockJira(MockConfig(flavor=flavor, **cfg))
        env.new_client = lambda **kw: JiraClient(flavor, env.mock.base_url, kw.pop("token", env.mock.token), env.mock.email,
                                                  transport=env.mock.transport(), **kw)
        env.client = env.new_client()
        env.syncer = lambda: Syncer(env.db, env.client)
        env.scope = "project is not EMPTY"
        env.sync = lambda full=False: env.syncer().run(env.scope, full=full)
        made.append(env)
        return env

    yield factory
    for e in made:
        e.db.close()
        e.engine.dispose()
