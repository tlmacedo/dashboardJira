"""Sincronização Jira → banco: incremental com marca d'água − 5 min, idempotente por id de changelog."""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy.orm import Session

from ..config import load_teams
from ..models import (AssigneeChange, Issue, Person, Squad, StatusTransition, SyncState, Worklog)
from .client import JiraClient

log = logging.getLogger("jiradash.sync")
BASE_FIELDS = ["summary", "issuetype", "priority", "status", "resolution", "assignee", "reporter", "created",
               "updated", "resolutiondate", "duedate", "timeoriginalestimate", "timeestimate", "timespent",
               "labels", "components", "parent", "project"]


def parse_dt(s: str | None) -> datetime | None:
    """Datas do Jira vêm como 2026-10-01T10:00:00.000+0000; guardamos em UTC sem fuso."""
    if not s:
        return None
    s = s.replace("Z", "+0000")
    if len(s) > 5 and s[-3] == ":":  # +00:00 -> +0000
        s = s[:-3] + s[-2:]
    for fmt in ("%Y-%m-%dT%H:%M:%S.%f%z", "%Y-%m-%dT%H:%M:%S%z"):
        try:
            return datetime.strptime(s, fmt).astimezone(timezone.utc).replace(tzinfo=None)
        except ValueError:
            continue
    raise ValueError(f"data inválida: {s}")


def user_ref(flavor: str, u: dict | None) -> str | None:
    if not u:
        return None
    return u.get("accountId") if flavor == "cloud" else (u.get("name") or u.get("key"))


class Syncer:
    def __init__(self, db: Session, client: JiraClient, field_ids: dict[str, str | None] | None = None):
        self.db, self.client = db, client
        cfg = load_teams()
        self.status_map: dict[str, str] = cfg.get("status_map", {})
        self.squads = {s.id: s for s in db.query(Squad).all()}
        self.fields = field_ids or client.discover_field_ids()
        self.people: dict[str, Person] = {p.jira_ref: p for p in db.query(Person).all()}

    # ---- pessoas ----
    def person(self, u: dict | None) -> Person | None:
        ref = user_ref(self.client.flavor, u)
        if not ref:
            return None
        p = self.people.get(ref)
        if p is None:
            p = Person(jira_ref=ref, name=u.get("displayName") or ref, email=u.get("emailAddress"),
                       active=u.get("active", True), role="externo")
            self.db.add(p)
            self.db.flush()
            self.people[ref] = p
        return p

    def squad_for(self, project: str, components: list[str]) -> int | None:
        best = None
        for s in self.squads.values():
            if project in (s.projects or []):
                if s.components and not (set(s.components) & set(components)):
                    best = best or None
                    continue
                return s.id
        return best

    # ---- uma issue ----
    def upsert_issue(self, raw: dict, histories: list[dict]) -> Issue:
        f = raw["fields"]
        status = f["status"]["name"]
        cat = self.status_map.get(status) or {"new": "todo", "indeterminate": "active", "done": "done"}.get(
            f["status"].get("statusCategory", {}).get("key", ""), "active")
        comps = [c["name"] for c in f.get("components") or []]
        assignee, reporter = self.person(f.get("assignee")), self.person(f.get("reporter"))
        iss = self.db.get(Issue, int(raw["id"]))
        if iss is None:
            iss = Issue(id=int(raw["id"]))
            self.db.add(iss)
        iss.key = raw["key"]
        iss.project = f["project"]["key"]
        iss.summary = (f.get("summary") or "")[:500]
        iss.type = f["issuetype"]["name"]
        iss.priority = (f.get("priority") or {}).get("name", "Medium")
        iss.status, iss.status_category = status, cat
        iss.resolution = (f.get("resolution") or {}).get("name")
        iss.assignee_id = assignee.id if assignee else None
        iss.reporter_id = reporter.id if reporter else None
        iss.created, iss.updated = parse_dt(f["created"]), parse_dt(f["updated"])
        iss.resolved = parse_dt(f.get("resolutiondate"))
        iss.due = datetime.strptime(f["duedate"], "%Y-%m-%d").date() if f.get("duedate") else None
        iss.original_estimate_s, iss.remaining_s = f.get("timeoriginalestimate"), f.get("timeestimate")
        iss.time_spent_s = f.get("timespent") or 0
        sp = self.fields.get("story_points")
        iss.story_points = f.get(sp) if sp else None
        iss.labels, iss.components = f.get("labels") or [], comps
        iss.parent_key = (f.get("parent") or {}).get("key")
        iss.epic_key = iss.parent_key if (f.get("parent") or {}).get("fields", {}).get("issuetype", {}).get("name") == "Epic" else iss.epic_key
        fl = self.fields.get("flagged")
        iss.flagged = bool(fl and f.get(fl))
        iss.squad_id = self.squad_for(iss.project, comps)
        iss.deleted_at = None
        self.db.flush()
        self._apply_changelog(iss, histories)
        return iss

    def _apply_changelog(self, iss: Issue, histories: list[dict]) -> None:
        seen = {t.changelog_id for t in iss.transitions} | {a.changelog_id for a in iss.assignee_changes}
        for h in histories:
            at = parse_dt(h["created"])
            for n, it in enumerate(h.get("items", [])):
                cid = f"{iss.id}-{h['id']}-{n}"
                if cid in seen:  # há relato de duplicatas no bulk fetch: deduplica pelo id
                    continue
                seen.add(cid)
                if it.get("field") == "status":
                    iss.transitions.append(StatusTransition(
                        changelog_id=cid, at=at, from_status=it.get("fromString"), to_status=it.get("toString"),
                        author_id=(self.person(h.get("author")) or Person(id=None)).id))
                elif it.get("field") == "assignee":
                    iss.assignee_changes.append(AssigneeChange(
                        changelog_id=cid, at=at,
                        from_person_id=self._by_ref(it.get("from") or it.get("fromString")),
                        to_person_id=self._by_ref(it.get("to") or it.get("toString"))))

    def _by_ref(self, ref: str | None) -> int | None:
        p = self.people.get(ref) if ref else None
        if p is None and ref:  # DC entrega o nome de exibição em fromString; tenta casar por nome
            p = next((x for x in self.people.values() if x.name == ref), None)
        return p.id if p else None

    def _sync_worklogs(self, iss: Issue) -> None:
        if not iss.time_spent_s:
            return
        have = {w.id for w in iss.worklogs}
        for w in self.client.worklogs(iss.key):
            wid = int(w["id"])
            if wid in have:
                continue
            a = self.person(w.get("author"))
            iss.worklogs.append(Worklog(id=wid, author_id=a.id if a else None,
                                        started=parse_dt(w["started"]), seconds=w["timeSpentSeconds"]))

    # ---- orquestração ----
    def run(self, jql_scope: str, full: bool = False) -> dict:
        wm = self.db.query(SyncState).filter_by(key="watermark").first()
        started = datetime.utcnow()
        jql = jql_scope
        if wm and not full:
            since = datetime.fromisoformat(wm.value) - timedelta(minutes=5)
            jql = f"({jql_scope}) AND updated >= \"{since:%Y-%m-%d %H:%M}\""
        fields = BASE_FIELDS + [v for v in self.fields.values() if v]
        raw = list(self.client.search(jql + " ORDER BY updated ASC" if "order by" not in jql.lower() else jql,
                                      fields, expand_changelog=True))
        hist = self.client.changelogs(raw)
        for r in raw:
            iss = self.upsert_issue(r, hist.get(r["id"], []))
            self._sync_worklogs(iss)
        if wm is None:
            wm = SyncState(key="watermark", value="")
            self.db.add(wm)
        wm.value = started.isoformat()
        self.db.commit()
        log.info("sync: %d issues", len(raw))
        return {"issues": len(raw), "since": jql, "finished": datetime.utcnow().isoformat()}

    def reconcile_open(self) -> int:
        """Marca como removidas as issues abertas locais que o Jira não retorna mais (excluídas/movidas)."""
        keys = [k for (k,) in self.db.query(Issue.key).filter(Issue.status_category != "done",
                                                                Issue.deleted_at.is_(None)).all()]
        alive: set[str] = set()
        for i in range(0, len(keys), 100):
            chunk = ",".join(keys[i:i + 100])
            alive |= {r["key"] for r in self.client.search(f"key in ({chunk})", ["summary"])}
        gone = 0
        for iss in self.db.query(Issue).filter(Issue.key.in_(set(keys) - alive)):
            iss.deleted_at = datetime.utcnow()
            gone += 1
        self.db.commit()
        return gone
