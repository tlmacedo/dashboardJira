import httpx
import yaml

from jiradash.jira.client import JiraClient
from jiradash.jira.discover import discover, draft_yaml


def handler(req: httpx.Request) -> httpx.Response:
    p = req.url.path
    if p.endswith("/project"):
        return httpx.Response(200, json=[{"key": "PAY", "name": "Pagamentos"}, {"key": "CX", "name": "Experiência"}])
    if p.endswith("/status"):
        return httpx.Response(200, json=[{"name": "A Fazer", "statusCategory": {"key": "new"}},
                                         {"name": "Em andamento", "statusCategory": {"key": "indeterminate"}},
                                         {"name": "Concluído", "statusCategory": {"key": "done"}}])
    if p.endswith("/issuetype"):
        return httpx.Response(200, json=[{"name": "Task"}, {"name": "Review"}, {"name": "Checklist"}])
    if p.endswith("/multiProject"):
        k = req.url.params["projectKeys"]
        return httpx.Response(200, json=[{"accountId": f"acc-{k}-1", "displayName": f"Ana {k}", "active": True},
                                         {"accountId": "bot", "displayName": "Automação", "accountType": "app"}])
    return httpx.Response(404)


def test_discover_e_rascunho_yaml_valido():
    c = JiraClient("cloud", "https://x.atlassian.net", "t", "e", transport=httpx.MockTransport(handler))
    info = discover(c)
    assert [p["key"] for p in info["projects"]] == ["PAY", "CX"]
    assert info["status_map"] == {"A Fazer": "todo", "Em andamento": "active", "Concluído": "done"}
    assert all(u["ref"] != "bot" for u in info["users"])  # contas de aplicativo ficam de fora
    doc = yaml.safe_load(draft_yaml(info))
    assert len(doc["squads"]) == 2 and doc["jql_scope"] == "project in (PAY, CX)"
    assert doc["squads"][0]["members"][0]["ref"] == "acc-PAY-1"
    c2 = discover(c, ["CX"])
    assert [p["key"] for p in c2["projects"]] == ["CX"]
