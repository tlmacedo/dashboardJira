import json

import httpx
import pytest

from jiradash.jira.client import JiraClient, JiraError
from jiradash.jira.sync import parse_dt, user_ref


def handler_cloud(request: httpx.Request) -> httpx.Response:
    p = request.url.path
    if p == "/rest/api/3/search/jql":
        body = json.loads(request.content)
        if "nextPageToken" not in body:
            return httpx.Response(200, json={"issues": [{"id": "1", "key": "A-1"}], "nextPageToken": "t2"})
        return httpx.Response(200, json={"issues": [{"id": "2", "key": "A-2"}], "isLast": True})
    if p == "/rest/api/3/changelog/bulkfetch":
        return httpx.Response(200, json={"issueChangeLogs": [{"issueId": "1", "changeHistories": [{"id": "9", "created": "2026-10-01T10:00:00.000+0000", "items": []}]}]})
    if p == "/rest/api/3/field":
        return httpx.Response(200, json=[{"id": "customfield_10016", "name": "Story Points"}, {"id": "customfield_10020", "name": "Sprint"}])
    return httpx.Response(404)


def test_cloud_pagina_por_token_e_usa_basic():
    c = JiraClient("cloud", "https://x.atlassian.net", "tok", "a@b.com", transport=httpx.MockTransport(handler_cloud))
    keys = [i["key"] for i in c.search("project = A", ["summary"])]
    assert keys == ["A-1", "A-2"]
    assert c.http.headers["Authorization"].startswith("Basic ")
    assert c.changelogs([{"id": "1"}, {"id": "2"}])["1"][0]["id"] == "9"
    assert c.discover_field_ids()["story_points"] == "customfield_10016"


def test_dc_usa_bearer_e_start_at():
    calls = []

    def h(request):
        calls.append(dict(request.url.params))
        start = int(request.url.params["startAt"])
        issues = [{"id": str(start + 1), "key": f"D-{start + 1}", "changelog": {"histories": [{"id": "1"}]}}]
        return httpx.Response(200, json={"issues": issues, "total": 2})

    c = JiraClient("dc", "https://jira.local", "pat", transport=httpx.MockTransport(h))
    assert c.http.headers["Authorization"] == "Bearer pat"
    got = list(c.search("project = D", ["summary"], expand_changelog=True))
    assert len(got) == 2 and calls[0]["expand"] == "changelog"
    assert c.changelogs(got)["1"] == [{"id": "1"}]


def test_429_respeita_retry_after(monkeypatch):
    sleeps, n = [], {"i": 0}
    monkeypatch.setattr("time.sleep", lambda s: sleeps.append(s))

    def h(request):
        n["i"] += 1
        return httpx.Response(429, headers={"Retry-After": "2"}) if n["i"] == 1 else httpx.Response(200, json={"ok": 1})

    c = JiraClient("cloud", "https://x", "t", "e", transport=httpx.MockTransport(h))
    assert c.get("/myself") == {"ok": 1} and 1.3 <= sleeps[0] <= 2.7


def test_erro_http_vira_jiraerror():
    c = JiraClient("dc", "https://x", "t", transport=httpx.MockTransport(lambda r: httpx.Response(403, text="no")), max_retries=0)
    with pytest.raises(JiraError):
        c.get("/myself")


def test_assign_payload_por_variante():
    seen = {}

    def h(request):
        seen[request.url.host] = json.loads(request.content)
        return httpx.Response(204)

    JiraClient("cloud", "https://cloud.x", "t", "e", transport=httpx.MockTransport(h)).assign("A-1", "acc-1")
    JiraClient("dc", "https://dc.x", "t", transport=httpx.MockTransport(h)).assign("A-1", "ana")
    assert seen["cloud.x"] == {"accountId": "acc-1"} and seen["dc.x"] == {"name": "ana"}


def test_parse_datas_e_refs():
    assert parse_dt("2026-10-01T10:00:00.000+0000").hour == 10
    assert parse_dt("2026-10-01T07:00:00.000-0300").hour == 10  # convertido para UTC
    assert user_ref("cloud", {"accountId": "a", "name": "n"}) == "a"
    assert user_ref("dc", {"name": "ana"}) == "ana"
