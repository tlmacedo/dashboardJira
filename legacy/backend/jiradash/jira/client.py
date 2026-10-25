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
    """Erro do Jira com mensagem em português; `status` guarda o código HTTP quando houve resposta."""

    def __init__(self, message: str, status: int | None = None):
        super().__init__(message)
        self.status = status


def explain_http_error(flavor: str, method: str, path: str, resp: httpx.Response) -> str:
    """Traduz o erro HTTP numa mensagem acionável (o texto bruto do Jira vai no fim)."""
    code = resp.status_code
    raw = " ".join((resp.text or "").split())[:300]
    cred = ("Cloud: confira JIRA_EMAIL e JIRA_TOKEN (API token da conta, não a senha)." if flavor == "cloud"
            else "Data Center: confira JIRA_TOKEN (Personal Access Token ativo e não expirado).")
    if code == 401:
        head = f"O Jira recusou as credenciais (401) em {method} {path}. {cred}"
    elif code == 403:
        why = (resp.headers.get("X-Seraph-Loginreason") or "").upper()
        extra = (" O Jira pede CAPTCHA após várias tentativas falhas: entre uma vez pelo navegador com a conta de serviço."
                 if "CAPTCHA" in why else "")
        head = (f"Sem permissão (403) em {method} {path}: a conta de serviço precisa de 'Browse Projects' e 'View Worklogs' "
                f"nos projetos do jql_scope.{extra}")
    elif code == 404:
        head = (f"Não encontrado (404) em {method} {path}: confira JIRA_BASE_URL (Cloud: https://empresa.atlassian.net; "
                "Data Center: inclua o context path, se houver) e se a issue/projeto existe e é visível para a conta.")
    elif code == 400:
        head = f"O Jira rejeitou a requisição (400) em {method} {path}"
    else:
        head = f"Erro {code} do Jira em {method} {path}"
    return f"{head} | resposta: {raw}" if raw else head


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

    # ---- núcleo HTTP com backoff em 429/5xx e falhas de rede (respeita Retry-After) ----
    def _sleep_for(self, attempt: int, resp: httpx.Response | None = None) -> float:
        ra = resp.headers.get("Retry-After") if resp is not None else None
        wait = min(300.0, float(ra)) if ra and ra.replace(".", "", 1).isdigit() else min(60, 2 ** attempt)
        return wait * random.uniform(0.7, 1.3)  # jitter recomendado pela Atlassian

    def _request(self, method: str, path: str, **kw) -> httpx.Response:
        last_net: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                resp = self.http.request(method, path, **kw)
            except httpx.TransportError as e:  # DNS, conexão recusada, timeout...
                last_net = e
                if attempt < self.max_retries:
                    wait = self._sleep_for(attempt)
                    log.warning("Falha de conexão com o Jira em %s (%s); nova tentativa em %.1fs", path, e.__class__.__name__, wait)
                    time.sleep(wait)
                    continue
                raise JiraError(f"Falha de conexão com o Jira ({self.base}) em {method} {path}: {e.__class__.__name__}: {e}. "
                                "Confira JIRA_BASE_URL, rede/proxy e certificados.") from e
            if resp.status_code in (429, 502, 503, 504) and attempt < self.max_retries:
                wait = self._sleep_for(attempt, resp)
                log.warning("Jira %s em %s; nova tentativa em %.1fs", resp.status_code, path, wait)
                time.sleep(wait)
                continue
            if resp.status_code >= 400:
                raise JiraError(explain_http_error(self.flavor, method, path, resp), status=resp.status_code)
            return resp
        raise JiraError(f"{method} {path}: tentativas esgotadas ({last_net})")

    def _json(self, resp: httpx.Response, path: str):
        try:
            return resp.json()
        except ValueError as e:
            ct = resp.headers.get("content-type", "?")
            raise JiraError(f"A resposta de {path} não é JSON (content-type: {ct}). Provável JIRA_BASE_URL errada, "
                            "proxy ou página de login SSO no meio do caminho.") from e

    def get(self, path: str, **params) -> dict | list:
        return self._json(self._request("GET", self.api + path, params=params), path)

    # ---- descoberta ----
    def myself(self) -> dict:
        return self.get("/myself")

    def user_timezone(self) -> str:
        """Fuso do usuário do Jira (o JQL `updated >= "yyyy-MM-dd HH:mm"` é interpretado nele)."""
        return (self.myself() or {}).get("timeZone") or "UTC"

    def fields(self) -> list[dict]:
        return self.get("/field")

    def discover_field_ids(self) -> dict[str, str | None]:
        """IDs de campos personalizados mudam por instância: descobre por nome."""
        want = {"story_points": ("story points", "story point estimate"), "sprint": ("sprint",),
                "flagged": ("flagged", "impediment"), "epic_link": ("epic link",)}
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
                data = self._json(self._request("POST", self.api + "/search/jql", json=body), "/search/jql")
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
                data = self._json(self._request("GET", self.api + "/search", params=params), "/search")
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
            for i in issues:  # DC já veio com expand=changelog na busca (pode vir truncado)
                cl = i.get("changelog") or {}
                hs = cl.get("histories", [])
                if cl.get("total", len(hs)) > len(hs):
                    log.info("changelog de %s truncado (%d de %d): buscando completo", i.get("key"), len(hs), cl["total"])
                    hs = self.full_changelog(i["key"])
                out[i["id"]] = hs
            return out
        for k in range(0, len(ids), 1000):
            token = None
            while True:
                body = {"issueIdsOrKeys": ids[k:k + 1000], "fieldIds": ["status", "assignee", "duedate", "labels", "timeoriginalestimate", "priority", "resolution"], "maxResults": 1000}
                if token:
                    body["nextPageToken"] = token
                data = self._json(self._request("POST", self.api + "/changelog/bulkfetch", json=body), "/changelog/bulkfetch")
                for blk in data.get("issueChangeLogs", []):
                    out.setdefault(str(blk["issueId"]), []).extend(blk.get("changeHistories", []))
                token = data.get("nextPageToken")
                if not token:
                    break
        return out

    def full_changelog(self, issue_key: str) -> list[dict]:
        """Histórico completo de uma issue (paginado). Se o servidor não tem /changelog, usa expand=changelog na issue."""
        out, start = [], 0
        try:
            while True:
                data = self.get(f"/issue/{issue_key}/changelog", startAt=start, maxResults=100)
                page = data.get("values") if "values" in data else data.get("histories", [])
                out.extend(page)
                start += len(page)
                if not page or data.get("isLast") or start >= data.get("total", start):
                    return out
        except JiraError as e:
            if e.status not in (404, 405):
                raise
        data = self.get(f"/issue/{issue_key}", expand="changelog", fields="summary")
        return (data.get("changelog") or {}).get("histories", [])

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
