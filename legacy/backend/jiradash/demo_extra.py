"""Dados FICTÍCIOS adicionais do demo: pares checklist/review, mudanças de vencimento/labels e padrões de adulteração.

Os padrões de adulteração são atribuídos a pessoas inventadas só para exercitar a tela de integridade.
"""
from __future__ import annotations

import random
from datetime import datetime, timedelta

from sqlalchemy.orm import Session

from .models import AssigneeChange, ChangeEvent, Issue, Person, SquadMember, StatusTransition, Worklog

LABELS = ["cliente", "urgente", "regressao", "sla", "legado", "seguranca"]
GAMERS = {
    "fabio.nunes": "prazo_empurrado", "diego.rocha": "fechamento_instantaneo", "tiago.lopes": "reabre_e_fecha",
    "rafael.melo": "apropriacao", "otavio.braga": "fechamento_em_lote",
}


def _next_seq(db: Session, proj: str) -> int:
    mx = 0
    for (k,) in db.query(Issue.key).filter(Issue.project == proj):
        try:
            mx = max(mx, int(k.split("-")[1]))
        except (IndexError, ValueError):
            pass
    return mx


def seed_extras(db: Session, rng: random.Random, now: datetime) -> None:
    people = {p.jira_ref: p for p in db.query(Person).all()}
    by_id = {p.id: p for p in people.values()}
    members: dict[int, list[Person]] = {}
    for sm in db.query(SquadMember).all():
        members.setdefault(sm.squad_id, []).append(by_id[sm.person_id])
    issues = db.query(Issue).all()
    nid = [800000]

    def new_id() -> int:
        nid[0] += 1
        return nid[0]

    # ---- vencimento das concluídas: distribuição realista em relação à data de resolução (D-3 ou antes ... atrasada)
    deltas = [(-5, .15), (-4, .08), (-3, .07), (-2, .14), (-1, .16), (0, .24), (1, .05), (2, .04), (3, .02), (6, .05)]
    late_heavy = [(-3, .10), (-2, .06), (-1, .08), (0, .18), (1, .16), (2, .14), (3, .10), (6, .18)]
    for i in issues:
        if i.resolved and i.due and i.status_category == "done":
            heavy = by_id.get(i.assignee_id) is not None and by_id[i.assignee_id].jira_ref == "fabio.nunes"
            d = rng.choices([x for x, _ in (late_heavy if heavy else deltas)], [w for _, w in (late_heavy if heavy else deltas)])[0]
            if d == -5:
                d = -rng.randint(3, 9)
            i.due = max(i.created.date(), i.resolved.date() - timedelta(days=d))
    # ---- labels e mudanças de vencimento "normais" (≈7% das issues com vencimento são prorrogadas)
    for i in issues:
        if rng.random() < 0.35:
            i.labels = list({*(i.labels or []), rng.choice(LABELS)})
    for i in issues:
        if i.due and rng.random() < 0.07 and i.created < now - timedelta(days=5):
            old = i.due
            new = old + timedelta(days=rng.randint(2, 7))
            at = datetime.combine(old - timedelta(days=rng.randint(0, 6)), datetime.min.time()) + timedelta(hours=rng.uniform(9, 17))
            at = max(at, i.created + timedelta(hours=1))
            who = (people[next(iter(people))] if False else None)
            mem = members.get(i.squad_id, [])
            author = rng.choice(mem) if mem else None
            i.events.append(ChangeEvent(changelog_id=f"{i.id}-d{rng.randint(0, 9999)}", at=at, field="duedate",
                                        from_value=old.isoformat(), to_value=new.isoformat(), author_id=author.id if author else None))
            i.due = new
    db.flush()

    # ---- pares checklist (pai) / review (filha)
    closed = [i for i in issues if i.resolved and i.type in ("Tarefa", "História") and i.resolution == "Resolvido"
              and not any(e.field == "duedate" for e in i.events)]
    rng.shuffle(closed)
    for sq_id, mem in members.items():
        sq_closed = [i for i in closed if i.squad_id == sq_id][:int(0.28 * len([x for x in closed if x.squad_id == sq_id]))]
        seq = {}
        for parent in sq_closed:
            proj = parent.project
            seq[proj] = seq.get(proj, _next_seq(db, proj)) + 1
            parent.type = "Checklist"
            trs = sorted(parent.transitions, key=lambda t: t.at)
            rev_in = next((t.at for t in trs if t.to_status == "Em Revisão"), None)
            rev_out = next((t.at for k, t in enumerate(trs) if rev_in and t.from_status == "Em Revisão"), None)
            reopened = any(t.from_status == "Concluído" and t.to_status != "Concluído" for t in trs)
            base_out = rev_out or (parent.resolved - timedelta(hours=rng.uniform(2, 30)))
            base_in = rev_in or (base_out - timedelta(days=rng.uniform(0.3, 1.5)))
            superficial = rng.random() < (0.7 if reopened else 0.15)
            dur = rng.uniform(0.03, 0.25) if superficial else rng.uniform(0.4, 2.5)
            r_done = min(parent.resolved, base_out + timedelta(hours=rng.uniform(-2, 6)))
            r_start = max(parent.created + timedelta(hours=2), r_done - timedelta(days=dur))
            r_created = parent.created + timedelta(hours=rng.uniform(0.2, 12))
            r_created = min(r_created, r_start - timedelta(minutes=5))
            if r_done <= r_start:
                r_done = r_start + timedelta(hours=1)
            reviewers = [m for m in mem if m.id != parent.assignee_id and m.role != "gerente"]
            reviewer = rng.choice(reviewers) if reviewers and rng.random() < 0.88 else by_id.get(parent.assignee_id)
            due = (parent.due - timedelta(days=rng.randint(1, 3))) if parent.due else None
            if due and rng.random() < 0.25:
                due = parent.due + timedelta(days=rng.randint(1, 4))  # review com vencimento depois do pai: ponto de atenção
            labels = [l for l in (parent.labels or []) if rng.random() < 0.6] + ["review"]
            if reopened and rng.random() < 0.6:
                labels.append("retrabalho")
            if superficial and rng.random() < 0.5:
                labels.append("revisao-rapida")
            if rng.random() < 0.3:
                labels.append("revisao-cruzada")
            rev = Issue(
                id=new_id(), key=f"{proj}-{seq[proj]}", project=proj, summary=f"Review: {parent.summary}"[:500], type="Review",
                priority=parent.priority, status="Concluído", status_category="done", resolution="Resolvido",
                assignee_id=reviewer.id if reviewer else None, reporter_id=parent.reporter_id, squad_id=parent.squad_id,
                created=r_created, updated=r_done, resolved=r_done, due=due, original_estimate_s=int(rng.choice([1, 2, 3]) * 3600),
                story_points=None, labels=sorted(set(labels)), components=[], epic_key=parent.epic_key, parent_key=parent.key,
                time_spent_s=int(max(0.2, dur * rng.uniform(2, 4)) * 3600))
            rev.transitions = [
                StatusTransition(changelog_id=f"{rev.id}-0", at=r_created + timedelta(minutes=10), from_status="Backlog", to_status="A Fazer", author_id=parent.reporter_id),
                StatusTransition(changelog_id=f"{rev.id}-1", at=r_start, from_status="A Fazer", to_status="Em Andamento", author_id=rev.assignee_id),
                StatusTransition(changelog_id=f"{rev.id}-2", at=r_done, from_status="Em Andamento", to_status="Concluído", author_id=rev.assignee_id)]
            rev.worklogs = [Worklog(id=rev.id * 10, author_id=rev.assignee_id, started=r_start, seconds=rev.time_spent_s)]
            db.add(rev)
    # reviews abertas para pais ainda em revisão
    open_wait = [i for i in issues if i.status == "Em Revisão" and i.type in ("Tarefa", "História")][:12]
    for parent in open_wait:
        proj = parent.project
        seqn = _next_seq(db, proj) + 1
        parent.type = "Checklist"
        mem = [m for m in members.get(parent.squad_id, []) if m.id != parent.assignee_id and m.role != "gerente"]
        reviewer = rng.choice(mem) if mem else None
        c = parent.created + timedelta(hours=rng.uniform(1, 10))
        st = rng.choice(["A Fazer", "A Fazer", "Em Andamento"])
        rev = Issue(id=new_id(), key=f"{proj}-{seqn}", project=proj, summary=f"Review: {parent.summary}"[:500], type="Review",
                    priority=parent.priority, status=st, status_category="todo" if st == "A Fazer" else "active",
                    assignee_id=reviewer.id if reviewer else None, reporter_id=parent.reporter_id, squad_id=parent.squad_id,
                    created=c, updated=now - timedelta(days=rng.uniform(0, 4)), due=(parent.due - timedelta(days=1)) if parent.due else None,
                    original_estimate_s=7200, labels=["review"], components=[], epic_key=parent.epic_key, parent_key=parent.key, time_spent_s=0)
        rev.transitions = [StatusTransition(changelog_id=f"{rev.id}-0", at=c + timedelta(minutes=10), from_status="Backlog", to_status="A Fazer", author_id=parent.reporter_id)]
        if st == "Em Andamento":
            rev.transitions.append(StatusTransition(changelog_id=f"{rev.id}-1", at=now - timedelta(days=rng.uniform(0.5, 3)), from_status="A Fazer", to_status="Em Andamento", author_id=rev.assignee_id))
        db.add(rev)
    db.flush()

    # ---- padrões de adulteração (fictícios, atribuídos a pessoas do demo)
    def done_tr(i: Issue):
        return next((t for t in sorted(i.transitions, key=lambda t: t.at, reverse=True) if t.to_status == "Concluído"), None)

    for ref, pattern in GAMERS.items():
        p = people.get(ref)
        if not p:
            continue
        mine = [i for i in issues if i.assignee_id == p.id and i.resolved and i.type != "Review" and i.resolution == "Resolvido"]
        sq = next((i.squad_id for i in mine), None)
        if pattern == "prazo_empurrado":
            for i in mine:
                if not i.due:
                    continue
                late = i.resolved.date() > i.due
                if (late and rng.random() < 0.85) or (not late and rng.random() < 0.2):
                    old = i.due
                    new = max(old, i.resolved.date()) + timedelta(days=rng.randint(0, 1))
                    at = datetime.combine(old, datetime.min.time()) - timedelta(hours=rng.uniform(1, 40))
                    at = max(at, i.created + timedelta(hours=1))
                    i.events.append(ChangeEvent(changelog_id=f"{i.id}-g{rng.randint(0, 99999)}", at=at, field="duedate",
                                                from_value=old.isoformat(), to_value=new.isoformat(), author_id=p.id))
                    i.due = new
        elif pattern == "fechamento_instantaneo":
            proj = next((i.project for i in mine), "PAY")
            seq = _next_seq(db, proj)
            for k in range(16):
                seq += 1
                c = now - timedelta(days=rng.uniform(1, 60), hours=rng.uniform(0, 8))
                r = c + timedelta(minutes=rng.uniform(1, 9))
                x = Issue(id=new_id(), key=f"{proj}-{seq}", project=proj, summary=f"Ajuste rápido #{seq}", type="Tarefa", priority="Média",
                          status="Concluído", status_category="done", resolution="Resolvido", assignee_id=p.id, reporter_id=p.id, squad_id=sq,
                          created=c, updated=r, resolved=r, due=r.date() + timedelta(days=rng.randint(0, 3)), original_estimate_s=3 * 3600,
                          story_points=2.0, labels=[], components=[], epic_key=None, time_spent_s=0)
                x.transitions = [StatusTransition(changelog_id=f"{x.id}-0", at=r, from_status="Backlog", to_status="Concluído", author_id=p.id)]
                db.add(x)
        elif pattern == "reabre_e_fecha":
            for i in rng.sample(mine, min(12, len(mine))):
                t = done_tr(i)
                if not t:
                    continue
                a = t.at + timedelta(minutes=rng.uniform(5, 40))
                b = a + timedelta(minutes=rng.uniform(2, 20))
                i.transitions.append(StatusTransition(changelog_id=f"{i.id}-rc{rng.randint(0, 99999)}", at=a, from_status="Concluído", to_status="Em Andamento", author_id=p.id))
                i.transitions.append(StatusTransition(changelog_id=f"{i.id}-rd{rng.randint(0, 99999)}", at=b, from_status="Em Andamento", to_status="Concluído", author_id=p.id))
                i.resolved = b
                i.updated = b
        elif pattern == "apropriacao":
            others = [i for i in issues if i.resolved and i.assignee_id and i.assignee_id != p.id and i.squad_id == sq and i.type != "Review"
                      and i.resolution == "Resolvido"]
            for i in rng.sample(others, min(14, len(others))):
                t = done_tr(i)
                if not t:
                    continue
                prev = i.assignee_id
                i.assignee_changes.append(AssigneeChange(changelog_id=f"{i.id}-ap{rng.randint(0, 99999)}", at=t.at - timedelta(minutes=rng.uniform(5, 50)),
                                                         from_person_id=prev, to_person_id=p.id))
                t.author_id = p.id
                i.assignee_id = p.id
        elif pattern == "fechamento_em_lote":
            ordered = sorted(mine, key=lambda i: i.resolved)
            for g in range(3):
                grp = ordered[g * 9:(g + 1) * 9]
                if len(grp) < 6:
                    break
                T = max(i.resolved for i in grp)
                T = T.replace(hour=22, minute=40 + g, second=0)
                for k, i in enumerate(grp):
                    t = done_tr(i)
                    if not t:
                        continue
                    t.at = T + timedelta(seconds=15 * k)
                    t.author_id = p.id
                    i.resolved = t.at
                    i.updated = t.at
    db.flush()
