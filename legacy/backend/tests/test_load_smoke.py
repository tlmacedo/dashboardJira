"""Fumaça das ferramentas de carga (a carga pesada em si fica fora do pytest: python -m tests.load.run)."""
from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from jiradash.models import Issue, StatusTransition, Worklog


def test_gerador_do_dataset_grande_cria_linhas_coerentes(tmp_path):
    from tests.load.gen_big import generate
    info = generate(str(tmp_path / "g.db"), n_issues=150, seed=3)
    eng = create_engine(f"sqlite:///{tmp_path / 'g.db'}")
    with Session(eng) as db:
        assert db.scalar(select(func.count()).select_from(Issue)) == info["issues"] >= 150
        assert db.scalar(select(func.count()).select_from(StatusTransition)) == info["transicoes"] > 0
        assert db.scalar(select(func.count()).select_from(Worklog)) == info["worklogs"]
        assert db.scalar(select(func.count()).select_from(Issue).where(Issue.assignee_id.is_(None), Issue.status_category != "done")) >= 0
    eng.dispose()


def test_percentis_e_resumo():
    from tests.load.run import pct, summarize
    assert pct([1, 2, 3, 4, 100], 50) == 3 and pct([], 50) is None
    r = summarize([("a", 0.1, True), ("a", 0.3, False), ("b", 0.2, True)], 1.0)
    assert r["total"]["n"] == 3 and r["endpoints"]["a"]["erro_pct"] == 50.0
