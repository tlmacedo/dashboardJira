"""Carrega equipes, pessoas, ausências e feriados do YAML para o banco (idempotente)."""
from __future__ import annotations

from datetime import date

import httpx
from sqlalchemy.orm import Session

from .config import load_teams
from .models import Absence, Holiday, Person, Squad, SquadMember

# Feriados nacionais fixos + Sexta-feira Santa (Carnaval/Corpus Christi são ponto facultativo: configure no YAML)
NATIONAL = {
    2026: [("01-01", "Confraternização Universal"), ("04-03", "Sexta-feira Santa"), ("04-21", "Tiradentes"),
           ("05-01", "Dia do Trabalho"), ("09-07", "Independência"), ("10-12", "N. Sra. Aparecida"),
           ("11-02", "Finados"), ("11-15", "Proclamação da República"), ("11-20", "Consciência Negra"),
           ("12-25", "Natal")],
    2027: [("01-01", "Confraternização Universal"), ("03-26", "Sexta-feira Santa"), ("04-21", "Tiradentes"),
           ("05-01", "Dia do Trabalho"), ("09-07", "Independência"), ("10-12", "N. Sra. Aparecida"),
           ("11-02", "Finados"), ("11-15", "Proclamação da República"), ("11-20", "Consciência Negra"),
           ("12-25", "Natal")],
}


def _d(v) -> date:
    return v if isinstance(v, date) else date.fromisoformat(str(v))


def fetch_brasilapi(year: int) -> list[tuple[date, str]]:
    r = httpx.get(f"https://brasilapi.com.br/api/feriados/v1/{year}", timeout=10)
    r.raise_for_status()
    return [(date.fromisoformat(x["date"]), x["name"]) for x in r.json()]


def bootstrap(db: Session, online_holidays: bool = False) -> dict:
    cfg = load_teams()
    people: dict[str, Person] = {p.jira_ref: p for p in db.query(Person).all()}

    def person(spec: dict, role: str = "membro") -> Person:
        p = people.get(spec["ref"])
        if p is None:
            p = Person(jira_ref=spec["ref"], name=spec.get("name", spec["ref"]))
            db.add(p)
            people[spec["ref"]] = p
        p.name = spec.get("name", p.name)
        p.email = spec.get("email", p.email)
        p.skills = spec.get("skills", p.skills or [])
        p.weekly_hours = float(spec.get("weekly_hours", p.weekly_hours or 40))
        p.focus_factor = float(spec.get("focus_factor", p.focus_factor or 0.7))
        p.role = spec.get("role", role)
        p.active = spec.get("active", True)
        db.flush()
        return p

    squads = 0
    for s in cfg.get("squads", []):
        sq = db.query(Squad).filter_by(key=s["key"]).first() or Squad(key=s["key"], name=s["name"])
        db.add(sq)
        sq.name, sq.projects = s["name"], s.get("projects", [])
        sq.components = s.get("components", [])
        sq.wip_limit_per_person = int(s.get("wip_limit_per_person", 3))
        mgr = s.get("manager")
        if mgr:
            sq.manager_id = person(mgr if isinstance(mgr, dict) else {"ref": mgr}, "gerente").id
        db.flush()
        db.query(SquadMember).filter_by(squad_id=sq.id).delete()
        for m in s.get("members", []):
            p = person(m)
            db.add(SquadMember(squad_id=sq.id, person_id=p.id, allocation=float(m.get("allocation", 1.0)),
                               start=_d(m["start"]) if m.get("start") else None,
                               end=_d(m["end"]) if m.get("end") else None))
        squads += 1
    db.flush()
    from .config import settings
    if "absences" in cfg or settings.mode != "demo":  # no demo, as ausências geradas pelo seed não são apagadas
        db.query(Absence).delete()
    for a in cfg.get("absences", []):
        p = people.get(a["person"])
        if p:
            db.add(Absence(person_id=p.id, start=_d(a["start"]), end=_d(a["end"]),
                           kind=a.get("kind", "ausência"), partial=float(a.get("partial", 1.0))))
    have = {h.day for h in db.query(Holiday).all()}
    extra = [(_d(h["date"]), h["name"]) for h in cfg.get("holidays", [])]
    nat = [(date.fromisoformat(f"{y}-{md}"), n) for y, lst in NATIONAL.items() for md, n in lst]
    if online_holidays:
        for y in (date.today().year, date.today().year + 1):
            try:
                nat += fetch_brasilapi(y)
            except Exception:
                pass  # mantém a tabela local
    for d, n in nat + extra:
        if d not in have:
            db.add(Holiday(day=d, name=n))
            have.add(d)
    db.commit()
    return {"squads": squads, "people": len(people)}
