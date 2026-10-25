"""Gerador de dataset sintético GRANDE (padrão 20.000 issues com histórico) direto num SQLite, reaproveitando models.py.

Uso:  python -m tests.load.gen_big --db /tmp/big.db --issues 20000

Reaproveita o gerador do Jira simulado (tests/mock_jira.py) e grava as linhas por insert em lote, sem passar pelo Syncer.
Para parecer um backlog real, a maior parte das issues está concluída (~12% abertas); só há 9 pessoas (as do YAML demo),
logo a carga por pessoa é irreal; serve para medir custo de CPU/memória dos endpoints, não para validar números.
"""
from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(BACKEND), str(BACKEND / "tests")]


def generate(db_path: str, n_issues: int = 20000, seed: int = 42, open_share: str = "baixo", extra_per_squad: int = 30) -> dict:
    os.environ.setdefault("JIRADASH_MODE", "demo")
    os.environ["DATABASE_URL"] = f"sqlite:///{db_path}"
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    import mock_jira
    from jiradash import teams
    from jiradash.db import Base
    import zlib

    from jiradash.models import (AssigneeChange, ChangeEvent, Issue, Person, Squad, SquadMember, StatusTransition, Worklog)

    if open_share == "baixo":  # Backlog, A Fazer, Em Andamento, Em Revisão, Em Teste, Bloqueado, Concluído, Cancelado
        mock_jira.END_W[:] = [2, 2, 2, 1, 1, 1, 86, 5]
    t0 = time.time()
    m = mock_jira.MockJira(mock_jira.MockConfig(flavor="cloud", n_issues=n_issues, seed=seed, anonymous_author_ratio=0.05))
    t_gen = time.time() - t0
    p = Path(db_path)
    if p.exists():
        p.unlink()
    eng = create_engine(f"sqlite:///{db_path}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(eng)
    Session = sessionmaker(bind=eng, expire_on_commit=False)
    with Session() as db:
        teams.bootstrap(db)
        refs = {x.jira_ref: x.id for x in db.query(Person).all()}
        sq = {pr: s.id for s in db.query(Squad).all() for pr in (s.projects or [])}
        for ref in {"ex.silva", "svc.painel", "marcos.lima"} - set(refs):
            pe = Person(jira_ref=ref, name=mock_jira.DISPLAY.get(ref, ref), role="externo")
            db.add(pe)
            db.flush()
            refs[ref] = pe.id
        # time grande: pessoas extras por squad (sem isso 20 mil issues em 9 pessoas estouram o horizonte de 12 semanas)
        pool: dict[int, list[int]] = {}
        for s_ in db.query(Squad).all():
            ids = [mm.person_id for mm in db.query(SquadMember).filter_by(squad_id=s_.id).all()]
            for k in range(extra_per_squad):
                pe = Person(jira_ref=f"dev.{s_.key}.{k:02d}", name=f"Dev {s_.name} {k:02d}", role="membro", skills=["backend"])
                db.add(pe)
                db.flush()
                db.add(SquadMember(squad_id=s_.id, person_id=pe.id, allocation=1.0))
                ids.append(pe.id)
            pool[s_.id] = ids
        special = {"ex.silva", "svc.painel", "marcos.lima"}

        def pid(ref, it):
            if not ref:
                return None
            if ref in special:
                return refs[ref]
            ids = pool[sq[it.project]]
            return ids[zlib.crc32(f"{it.id}:{ref}".encode()) % len(ids)]

        cat = {"new": "todo", "indeterminate": "active", "done": "done"}
        status_cat = {"Backlog": "todo", "A Fazer": "todo", "Em Andamento": "active", "Em Revisão": "wait", "Em Teste": "active",
                      "Bloqueado": "blocked", "Concluído": "done", "Cancelado": "done"}
        iss, tr, ac, ev, wl = [], [], [], [], []
        for it in m.issues:
            iss.append(dict(
                id=it.id, key=it.key, project=it.project, summary=it.summary, type=it.type, priority=it.priority, status=it.status,
                status_category=status_cat.get(it.status, "active"), resolution=it.resolution, assignee_id=pid(it.assignee, it),
                reporter_id=pid(it.reporter, it), squad_id=sq.get(it.project), created=it.created, updated=it.updated,
                resolved=it.resolved, due=it.due, original_estimate_s=it.estimate,
                remaining_s=max(it.estimate - it.spent, 0) if it.estimate else None, time_spent_s=it.spent,
                story_points=it.story_points, labels=it.labels, components=it.components, epic_key=it.epic, parent_key=it.parent,
                flagged=it.flagged, deleted_at=None))
            for h in it.histories:
                for n, x in enumerate(h.items):
                    cid = f"{it.id}-{h.id}-{n}"
                    a = pid(h.author, it)
                    f = x["field"]
                    if f == "status":
                        tr.append(dict(changelog_id=cid, issue_id=it.id, at=h.at, from_status=x["from"], to_status=x["to"], author_id=a))
                    elif f == "assignee":
                        ac.append(dict(changelog_id=cid, issue_id=it.id, at=h.at, from_person_id=pid(x["from"], it), to_person_id=pid(x["to"], it)))
                    else:
                        j = lambda v: " ".join(v) if isinstance(v, list) else v
                        ev.append(dict(changelog_id=cid, issue_id=it.id, at=h.at, field=f, from_value=j(x["from"]), to_value=j(x["to"]), author_id=a))
            for w in it.worklogs:
                wl.append(dict(id=w.id, issue_id=it.id, author_id=pid(w.author, it), started=w.started, seconds=w.seconds))
        for model, rows in ((Issue, iss), (StatusTransition, tr), (AssigneeChange, ac), (ChangeEvent, ev), (Worklog, wl)):
            for i in range(0, len(rows), 5000):
                db.bulk_insert_mappings(model, rows[i:i + 5000])
        db.commit()
    eng.dispose()
    return {"db": db_path, "issues": len(iss), "transicoes": len(tr), "mudancas_responsavel": len(ac), "eventos": len(ev),
            "worklogs": len(wl), "pessoas": sum(len(v) for v in pool.values()), "abertas": sum(1 for r in iss if r["status_category"] != "done"),
            "segundos_geracao": round(t_gen, 1), "segundos_total": round(time.time() - t0, 1)}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True)
    ap.add_argument("--issues", type=int, default=20000)
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()
    print(generate(a.db, a.issues, a.seed))
