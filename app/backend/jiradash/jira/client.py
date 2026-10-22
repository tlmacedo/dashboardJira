"""Cliente REST do Jira com adaptadores Cloud e Data Center.

Diferenças tratadas (ver relatório, seção de integração):
- Cloud: Basic (e-mail + API token), /rest/api/3, busca /search/jql com nextPageToken, accountId.
- Data Center: PAT (Bearer), /rest/api/2, busca /search com startAt, identificação por name/key.
Os formatos foram escritos conforme a documentação pública; valide contra a sua instância
(`python -m jiradash.cli check`) antes de confiar nos números.
"""
from __future__ import annotations

import base64
import logging
import random
import time
from typing import Iterator

import httpx

log = logging.getLogger("jiradash.jira")
PAGE = 100


class JiraError(RuntimeError):
    pass


class JiraClient:
    def __init__(self, flavor: str, base_url: str, token: str, email: str = "",
                 transport: httpx.BaseTransport | None = None, max_retries: int = 5):
        if flavor not in ("cloud", "dc"):
            raise ValueError("flavor deve ser 'cloud' ou 'dc'")
        self.flavor = flavor
        self.base = base_url.rstrip("/")
        self.api = "/rest/api/3" if flavor == "cloud" else "/rest/api/2"
        self.max_retries = max_retries
        headers = {"Accept": "application/json"}
        if flavor == "cloud":
            raw = base64.b64encode(f"{email}:{token}".encode()).decode()
            headers["Authorization"] = f"Basic {raw}"
        else:
            headers["Authorization"] = f"Bearer {token}"
        self.http = httpx.Client(base_url=self.base, headers=headers, timeout=60, transport=transport)

    # ---- núcleo HTTP com backoff em 429/5xx (respeita Retry-After) ----
    def _request(self, method: str, path: str, **kw) -> httpx.Response:
        for attempt in range(self.max_retries + 1):
            resp = self.http.request(method, path, **kw)
            if resp.status_code in (429, 502, 503, 504) and attempt < self.max_retries:
                ra = resp.headers.get("Retry-After")
                wait = float(ra) if ra and ra.replace(".", "", 1).isdigit() else min(60, 2 ** attempt)
                wait *= random.uniform(0.7, 1.3)  # jitter recomendado pela Atlassian
                log.warning("Jira %s em %s; nova tentativa em %.1fs", resp.status_code, path, wait)
                time.sleep(wait)
                continue
            if resp.status_code >= 400:
                raise JiraError(f"{method} {path} -> {resp.status_code}: {resp.text[:300]}")
            return resp
        raise JiraError(f"{method} {path}: tentativas esgotadas")

    def get(self, path: str, **params) -> dict | list:
        return self._request("GET", self.api + path, params=params).json()

    # ---- descoberta ----
    def myself(self) -> dict:
        return self.get("/myself")

    def fields(self) -> list[dict]:
        return self.get("/field")

    def discover_field_ids(self) -> dict[str, str | None]:
        """IDs de campos personalizados mudam por instância: descobre por nome."""
        want = {"story_points": ("story points", "story point estimate"), "sprint": ("sprint",),
                "flagged": ("flagged", "impediment")}
        out: dict[str, str | None] = {k: None for k in want}
        for f in self.fields():
            name = (f.get("name") or "").lower()
            for k, names in want.items():
                if out[k] is None and name in names:
                    out[k] = f["id"]
        return out

    # ---- busca ----
    def search(self, jql: str, fields: list[str], expand_changelog: bool = False) -> Iterator[dict]:
        if self.flavor == "cloud":
            token = None
            while True:
                body = {"jql": jql, "fields": fields, "maxResults": PAGE}
                if token:
                    body["nextPageToken"] = token
                data = self._request("POST", self.api + "/search/jql", json=body).json()
                yield from data.get("issues", [])
                token = data.get("nextPageToken")
                if not token or data.get("isLast"):
                    return
        else:
            start = 0
            while True:
                params = {"jql": jql, "fields": ",".join(fields), "startAt": start, "maxResults": PAGE}
                if expand_changelog:
                    params["expand"] = "changelog"
                data = self._request("GET", self.api + "/search", params=params).json()
                issues = data.get("issues", [])
                yield from issues
                start += len(issues)
                if not issues or start >= data.get("total", 0):
                    return

    # ---- changelog ----
    def changelogs(self, issues: list[dict]) -> dict[str, list[dict]]:
        """{issue_id: [history,...]} com histories no formato {id, created, author, items[]}."""
        ids = [i["id"] for i in issues]
        out: dict[str, list[dict]] = {i: [] for i in ids}
        if self.flavor == "dc":
            for i in issues:  # DC já veio com expand=changelog na busca
                out[i["id"]] = (i.get("changelog") or {}).get("histories", [])
            return out
        for k in range(0, len(ids), 1000):
            token = None
            while True:
                body = {"issueIdsOrKeys": ids[k:k + 1000], "fieldIds": ["status", "assignee"], "maxResults": 1000}
                if token:
                    body["nextPageToken"] = token
                data = self._request("POST", self.api + "/changelog/bulkfetch", json=body).json()
                for blk in data.get("issueChangeLogs", []):
                    out.setdefault(str(blk["issueId"]), []).extend(blk.get("changeHistories", []))
                token = data.get("nextPageToken")
                if not token:
                    break
        return out

    # ---- worklog ----
    def worklogs(self, issue_key: str) -> list[dict]:
        out, start = [], 0
        while True:
            data = self.get(f"/issue/{issue_key}/worklog", startAt=start, maxResults=1000)
            wl = data.get("worklogs", [])
            out.extend(wl)
            start += len(wl)
            if not wl or start >= data.get("total", 0):
                return out

    # ---- escrita (somente com JIRADASH_ALLOW_WRITE=1) ----
    def assignable(self, issue_key: str, ref: str) -> bool:
        users = self.get("/user/assignable/search", issueKey=issue_key,
                         **({"accountId": ref} if self.flavor == "cloud" else {"username": ref}))
        if self.flavor == "cloud":
            return any(u.get("accountId") == ref for u in users)
        return any(ref in (u.get("name"), u.get("key")) for u in users)

    def assign(self, issue_key: str, ref: str) -> None:
        body = {"accountId": ref} if self.flavor == "cloud" else {"name": ref}
        self._request("PUT", f"{self.api}/issue/{issue_key}/assignee", json=body)

    def issue_url(self, key: str) -> str:
        return f"{self.base}/browse/{key}"
