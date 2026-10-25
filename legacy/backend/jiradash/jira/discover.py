"""Descoberta no Jira para preencher config/equipes.yaml sem digitar tudo à mão."""
from __future__ import annotations

from .client import JiraClient

CATEGORY_MAP = {"new": "todo", "indeterminate": "active", "done": "done"}


def discover(client: JiraClient, project_keys: list[str] | None = None) -> dict:
    projects = client.get("/project")
    if isinstance(projects, dict):  # alguns endpoints paginam em {"values": [...]}
        projects = projects.get("values", [])
    if project_keys:
        projects = [p for p in projects if p["key"] in project_keys]
    keys = [p["key"] for p in projects]
    statuses = client.get("/status")
    types = client.get("/issuetype")
    users: dict[str, dict] = {}
    for k in keys:
        try:
            params = {"projectKeys": k, "maxResults": 200}
            for u in client.get("/user/assignable/multiProject", **params):
                ref = u.get("accountId") if client.flavor == "cloud" else (u.get("name") or u.get("key"))
                if ref and u.get("active", True) and u.get("accountType", "atlassian") != "app":
                    users.setdefault(ref, {"ref": ref, "name": u.get("displayName") or ref, "projects": []})["projects"].append(k)
        except Exception as e:  # permissões variam por projeto; segue com os demais
            users.setdefault("_erro_" + k, {"ref": "", "name": f"(não foi possível listar usuários de {k}: {e})", "projects": [k]})
    smap: dict[str, str] = {}
    for s in statuses:
        cat = (s.get("statusCategory") or {}).get("key", "")
        smap.setdefault(s["name"], CATEGORY_MAP.get(cat, "active"))
    return {"projects": [{"key": p["key"], "name": p.get("name", "")} for p in projects], "status_map": smap,
            "issue_types": sorted({t["name"] for t in types}), "users": list(users.values())}


def draft_yaml(info: dict) -> str:
    """Rascunho do equipes.yaml: um squad por projeto, com todos os usuários atribuíveis (ajuste à mão)."""
    import yaml
    squads = []
    for p in info["projects"]:
        members = [{"ref": u["ref"], "name": u["name"], "skills": [], "weekly_hours": 40}
                   for u in info["users"] if p["key"] in u["projects"] and u["ref"]]
        squads.append({"key": p["key"].lower(), "name": p["name"] or p["key"],
                       "manager": {"ref": "PREENCHER-REF-DO-GERENTE", "name": "PREENCHER"}, "projects": [p["key"]],
                       "wip_limit_per_person": 3, "members": members})
    doc = {"status_map": info["status_map"], "workflow_order": [], "negative_resolutions": ["Cancelado", "Duplicado", "Won't Do", "Não será feito"],
           "status_wip_limits": {}, "default_issue_hours": 6,
           "jql_scope": "project in (%s)" % ", ".join(p["key"] for p in info["projects"]),
           "checklist_types": ["Checklist"], "review_types": ["Review"], "goals": {"on_time_pct": 90, "early_pct": 35},
           "squads": squads, "absences": [], "holidays": []}
    head = ("# RASCUNHO gerado por `python -m jiradash discover`. Revise antes de usar:\n"
            "# 1) preencha o gerente de cada squad; 2) remova de cada squad quem não é da equipe;\n"
            "# 3) ajuste status_map e workflow_order (ordem do fluxo, do primeiro ao último status);\n"
            f"# 4) tipos de issue no seu Jira: {', '.join(info['issue_types'])}\n")
    return head + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False)
