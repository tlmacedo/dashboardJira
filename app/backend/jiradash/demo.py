"""Gerador de dados FICTÍCIOS para demonstração (datas relativas a hoje, determinístico)."""
from __future__ import annotations

import math
import random
from datetime import date, datetime, timedelta

from sqlalchemy.orm import Session

from .models import (Absence, AssigneeChange, Issue, Person, Squad, SquadMember, StatusTransition, Worklog)

TOPICS = {
    "PAY": ["Pix: conciliação diária", "Antifraude: regra de velocidade", "Cartão: tokenização", "Boleto: segunda via",
            "Estorno parcial", "Webhook de liquidação", "Limites por perfil", "Relatório de chargeback", "Split de pagamento",
            "Retentativa de cobrança", "Conciliação de adquirente", "Taxa de câmbio", "Extrato exportável"],
    "PLT": ["Migrar pipeline de CI", "Observabilidade de filas", "Rotação de segredos", "Autoscaling do cluster", "Backup e restauração",
            "Hardening de imagens", "Custo de nuvem", "Gateway de API", "Cache distribuído", "Política de logs"],
    "CX": ["Nova tela de onboarding", "Acessibilidade do checkout", "Push de status do pedido", "Chat de suporte", "Tela de perfil",
           "Pesquisa NPS in-app", "Fluxo de recuperação de senha", "Modo escuro", "Carrossel de ofertas", "Tela de faturas"],
}
TYPES = [("História", 0.5), ("Tarefa", 0.2), ("Bug", 0.25), ("Spike", 0.05)]
PRIOS = [("Crítica", 0.05), ("Alta", 0.2), ("Média", 0.55), ("Baixa", 0.2)]
SPS = [1, 2, 3, 5, 8]
SPW = [0.15, 0.3, 0.3, 0.17, 0.08]
SPEED = {  # fator de velocidade por pessoa (1 = média); só gera diferenças plausíveis
    "ana.souza": 1.2, "bruno.alves": 1.0, "carla.mendes": 0.9, "diego.rocha": 0.8, "elisa.prado": 1.1, "fabio.nunes": 0.7,
    "igor.martins": 1.1, "julia.ramos": 1.0, "kleber.dias": 0.9, "larissa.pinto": 1.2, "mateus.farias": 0.75,
    "nadia.cruz": 1.0, "otavio.braga": 0.85, "paula.teles": 1.1, "rafael.melo": 0.95, "sara.vieira": 1.0, "tiago.lopes": 0.8,
}


def wchoice(rng: random.Random, pairs):
    r, acc = rng.random(), 0.0
    for v, w in pairs:
        acc += w
        if r <= acc:
            return v
    return pairs[-1][0]


def seed_demo(db: Session, now: datetime | None = None, seed: int = 7) -> dict:
    rng = random.Random(seed)
    now = now or datetime.utcnow().replace(microsecond=0)
    today = now.date()
    squads = db.query(Squad).all()
    people = {p.jira_ref: p for p in db.query(Person).all()}
    n_issues = 0
    iid = 10000
    for sq in squads:
        proj = sq.projects[0]
        members = [sm.person for sm in db.query(SquadMember).filter_by(squad_id=sq.id)]
        mgr = db.get(Person, sq.manager_id)
        seq = 100
        # --- histórico: ~5 chegadas por pessoa por semana nas últimas 21 semanas
        total = int(len(members) * 2.4 * 21)
        for _ in range(total):
            created = now - timedelta(days=rng.uniform(0, 147), hours=rng.uniform(0, 8))
            owner = rng.choice(members)
            speed = SPEED.get(owner.jira_ref, 1.0)
            sp = rng.choices(SPS, SPW)[0]
            typ = wchoice(rng, TYPES)
            if typ == "Bug":
                sp = rng.choice([1, 2, 3, 5])
            prio = wchoice(rng, PRIOS)
            # esperas e durações (dias), calda longa
            wait_todo = rng.expovariate(1 / 5.0) + (rng.uniform(15, 50) if rng.random() < 0.05 else 0)
            dev = max(0.3, rng.lognormvariate(math.log(0.9 * sp + 0.8), 0.45) / speed)
            review = rng.expovariate(1 / 1.2) + 0.2
            test = rng.expovariate(1 / 0.8) + 0.1 if typ != "Spike" else 0.0
            blocked = rng.uniform(1, 6) if rng.random() < 0.09 else 0.0
            path: list[tuple[str, float]] = [("A Fazer", wait_todo), ("Em Andamento", dev)]
            if blocked:
                path += [("Bloqueado", blocked), ("Em Andamento", dev * 0.4)]
            path += [("Em Revisão", review)]
            if rng.random() < 0.13:  # devolução: volta da revisão para o desenvolvimento
                path += [("Em Andamento", dev * 0.35), ("Em Revisão", review * 0.6)]
            if test:
                path += [("Em Teste", test)]
            outcome = "Concluído"
            resolution = "Resolvido"
            r = rng.random()
            if r < 0.04:
                outcome, resolution = "Cancelado", rng.choice(["Cancelado", "Duplicado", "Não será feito"])
                path = path[:rng.randint(1, min(3, len(path)))]
            reopen = outcome == "Concluído" and rng.random() < 0.06
            seq += 1
            iid += 1
            key = f"{proj}-{seq}"
            est_h = round(sp * 4 * rng.uniform(0.7, 1.4), 1) if rng.random() < 0.82 else None
            no_sp = est_h is None and rng.random() < 0.7
            due = (created + timedelta(days=rng.uniform(8, 35))).date() if rng.random() < 0.7 else None
            issue = Issue(id=iid, key=key, project=proj, summary=f"{rng.choice(TOPICS[proj])} #{seq}", type=typ,
                          priority=prio, status="Backlog", status_category="todo", assignee_id=owner.id, reporter_id=mgr.id,
                          squad_id=sq.id, created=created, updated=created, due=due,
                          original_estimate_s=int(est_h * 3600) if est_h else None, story_points=None if no_sp else float(sp),
                          labels=[f"skill:{rng.choice(owner.skills)}"] if rng.random() < 0.3 else [],
                          components=[], epic_key=f"{proj}-E{rng.randint(1, 6)}" if rng.random() < 0.75 else None)
            db.add(issue)
            trs, completed, end_at = _build(created, path, now, rng)
            _apply(db, issue, trs, completed, end_at, outcome, resolution, reopen, now, rng, members, owner)
            n_issues += 1
        # --- backlog novo, aberto no nome do gerente (para a tela de distribuição)
        for k in range(rng.randint(9, 12)):
            seq += 1
            iid += 1
            created = now - timedelta(days=rng.uniform(0.1, 6))
            sp = rng.choices(SPS, SPW)[0]
            typ = wchoice(rng, TYPES)
            skill = rng.choice([s for m_ in members for s in m_.skills])
            db.add(Issue(id=iid, key=f"{proj}-{seq}", project=proj, summary=f"{rng.choice(TOPICS[proj])} (nova) #{seq}", type=typ,
                         priority=wchoice(rng, PRIOS), status="A Fazer", status_category="todo",
                         assignee_id=mgr.id if rng.random() < 0.85 else None, reporter_id=mgr.id, squad_id=sq.id,
                         created=created, updated=created, due=(today + timedelta(days=rng.randint(7, 40))) if rng.random() < 0.7 else None,
                         original_estimate_s=int(sp * 4 * 3600), story_points=float(sp), labels=[f"skill:{skill}"] if rng.random() < 0.5 else [],
                         components=[], epic_key=f"{proj}-E{rng.randint(1, 6)}"))
            n_issues += 1
    db.flush()
    from .demo_extra import seed_extras
    seed_extras(db, rng, now)
    _overload_and_absences(db, rng, today, now)
    db.commit()
    return {"issues": n_issues, "now": now.isoformat()}


def _build(created: datetime, path, now: datetime, rng: random.Random):
    """Transições a partir do caminho; para em 'agora'. Retorna (transições, concluiu?, instante final)."""
    out = [(created + timedelta(hours=rng.uniform(0.2, 3)), "Backlog", "A Fazer")]
    t = out[0][0] + timedelta(days=path[0][1])
    cur = "A Fazer"
    for st, dur in path[1:]:
        if t > now:
            return out, False, None
        out.append((t, cur, st))
        cur = st
        t += timedelta(days=dur)
    if t > now:
        return out, False, None
    return out, True, t


def _apply(db, issue, trs, completed, end_at, outcome, resolution, reopen, now, rng, members, owner):
    changes = list(trs)
    finished = False
    issue.time_spent_s = 0
    if completed:
        last_state = changes[-1][2]
        changes.append((end_at, last_state, outcome))
        issue.resolved, issue.resolution, finished = end_at, resolution, True
        if reopen:
            ro = end_at + timedelta(days=rng.uniform(1, 6))
            if ro < now:
                changes.append((ro, outcome, "Em Andamento"))
                re_done = ro + timedelta(days=rng.uniform(0.5, 3))
                if re_done < now:
                    changes.append((re_done, "Em Andamento", "Concluído"))
                    issue.resolved, issue.resolution = re_done, "Resolvido"
                else:
                    issue.resolved, issue.resolution, finished = None, None, False
    final = changes[-1][2]
    issue.status = final
    issue.status_category = {"Backlog": "todo", "A Fazer": "todo", "Em Andamento": "active", "Em Revisão": "wait",
                             "Em Teste": "active", "Bloqueado": "blocked", "Concluído": "done", "Cancelado": "done"}[final]
    issue.updated = changes[-1][0] if changes else issue.created
    if issue.status_category != "done" and issue.due and rng.random() < 0.85:
        issue.due = (now + timedelta(days=rng.uniform(2, 45))).date()
    if not finished and issue.status_category != "done":
        if rng.random() < 0.85 or issue.status_category == "todo":
            issue.updated = max(issue.updated, now - timedelta(days=rng.expovariate(1 / 2.5)))
        issue.updated = min(issue.updated, now)
    if issue.status_category == "done" and outcome == "Cancelado":
        issue.status = "Cancelado"
    for k, (at, a, b) in enumerate(changes):
        issue.transitions.append(StatusTransition(changelog_id=f"{issue.id}-{k}", at=at, from_status=a, to_status=b, author_id=owner.id))
    # primeira atribuição + troca de responsável ocasional
    if rng.random() < 0.1 and len(members) > 1 and changes:
        other = rng.choice([m for m in members if m.id != owner.id])
        at = changes[0][0] + timedelta(hours=rng.uniform(1, 40))
        if at < now:
            issue.assignee_changes.append(AssigneeChange(changelog_id=f"{issue.id}-a0", at=at, from_person_id=owner.id, to_person_id=other.id))
            issue.assignee_id = other.id
            if rng.random() < 0.3 and len(members) > 2:
                third = rng.choice([m for m in members if m.id not in (owner.id, other.id)])
                at2 = at + timedelta(days=rng.uniform(1, 5))
                if at2 < now:
                    issue.assignee_changes.append(AssigneeChange(changelog_id=f"{issue.id}-a1", at=at2, from_person_id=other.id, to_person_id=third.id))
                    issue.assignee_id = third.id
    for tr in issue.transitions:  # quem move o status é o responsável final (no Jira real vem do changelog)
        tr.author_id = issue.assignee_id
    # horas registradas: ~2,8 h/dia em andamento, com 15% das pessoas/issues sem registro (qualidade de dados)
    active_days = 0.0
    prev_t, prev_s = issue.created, "A Fazer"
    for at, a, b in changes:
        if prev_s in ("Em Andamento", "Em Teste"):
            active_days += (at - prev_t).total_seconds() / 86400
        prev_t, prev_s = at, b
    if prev_s in ("Em Andamento", "Em Teste"):
        active_days += (now - prev_t).total_seconds() / 86400
    if active_days > 0 and rng.random() > 0.15:
        sec = int(active_days * rng.uniform(1.3, 2.4) * 3600)
        issue.time_spent_s = sec
        who = issue.assignee_id or owner.id
        started = (changes[1][0] if len(changes) > 1 else issue.created) + timedelta(hours=1)
        issue.worklogs.append(Worklog(id=issue.id * 10, author_id=who, started=min(started, now - timedelta(hours=1)), seconds=sec))
        # últimos 30 dias: divide em registros diários para o gráfico de horas
    if issue.status_category != "done":
        if issue.original_estimate_s:
            issue.remaining_s = max(1800, issue.original_estimate_s - issue.time_spent_s) if issue.status_category != "todo" else issue.original_estimate_s
        # alguns itens estouram a estimativa
    if finished and issue.original_estimate_s and rng.random() < 0.18:
        issue.time_spent_s = int(issue.original_estimate_s * rng.uniform(1.3, 2.1))


def _overload_and_absences(db: Session, rng: random.Random, today: date, now: datetime) -> None:
    ref = {p.jira_ref: p for p in db.query(Person).all()}
    # férias futuras e licenças curtas: produzem alertas de capacidade e rebalanceamento
    plans = [("ana.souza", 9, 14, "ferias"), ("fabio.nunes", 3, 5, "licenca"), ("kleber.dias", 16, 12, "ferias"),
             ("larissa.pinto", 30, 10, "ferias"), ("paula.teles", 6, 4, "licenca"), ("tiago.lopes", 21, 10, "ferias"),
             ("sara.vieira", 12, 3, "folga"), ("carla.mendes", 40, 14, "ferias")]
    for r, start, ndays, kind in plans:
        p = ref.get(r)
        if p:
            s = today + timedelta(days=start)
            db.add(Absence(person_id=p.id, start=s, end=s + timedelta(days=ndays - 1), kind=kind))
    # sobrecarga: duas pessoas recebem fila grande ainda não iniciada
    for r, extra in (("ana.souza", 4), ("rafael.melo", 5), ("igor.martins", 5)):
        p = ref.get(r)
        if not p:
            continue
        sq = db.query(SquadMember).filter_by(person_id=p.id).first().squad_id
        proj = db.get(Squad, sq).projects[0]
        base = db.query(Issue).filter(Issue.project == proj).count() + 900
        for k in range(extra):
            base += 1
            sp = rng.choice([3, 5, 8])
            db.add(Issue(id=500000 + p.id * 100 + k, key=f"{proj}-{base}", project=proj, summary=f"Demanda acumulada #{base}",
                         type="História", priority=rng.choice(["Alta", "Média"]), status="A Fazer", status_category="todo",
                         assignee_id=p.id, reporter_id=p.id, squad_id=sq, created=now - timedelta(days=rng.uniform(3, 12)),
                         updated=now - timedelta(days=rng.uniform(0, 3)), due=today + timedelta(days=rng.randint(10, 24)),
                         original_estimate_s=sp * 4 * 3600, story_points=float(sp), labels=[], components=[], epic_key=f"{proj}-E1"))
