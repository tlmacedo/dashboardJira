"""Tarefas de sincronização (agendadas ou sob demanda). O processo web nunca chama o Jira para montar um card."""
from __future__ import annotations

import logging
import threading
import time

from sqlalchemy.orm import Session

from .config import load_teams, settings
from .jira.client import JiraClient
from .jira.sync import Syncer

log = logging.getLogger("jiradash.jobs")


def make_client() -> JiraClient:
    return JiraClient(settings.jira_flavor, settings.jira_base_url, settings.jira_token, settings.jira_email)


def run_sync(db: Session, full: bool = False) -> dict:
    cfg = load_teams()
    jql = cfg.get("jql_scope") or "project is not EMPTY"
    s = Syncer(db, make_client())
    out = s.run(jql, full=full)
    out["removidas"] = s.reconcile_open() if full else 0
    return out


def sync_keys(db: Session, keys: list[str]) -> None:
    s = Syncer(db, make_client())
    s.run("key in (%s)" % ",".join(keys), full=True)


def start_scheduler(session_factory) -> None:
    """Agendador simples em thread (APScheduler/Celery entram quando houver mais de um tenant)."""
    if settings.mode == "demo" or not settings.jira_base_url:
        return

    def loop():
        n = 0
        while True:
            try:
                with session_factory() as db:
                    n += 1
                    run_sync(db, full=(n % 144 == 0))  # reconciliação completa ~1x por dia com intervalo de 10 min
                    from . import cache
                    cache.invalidate()
            except Exception:
                log.exception("falha na sincronização")
            time.sleep(settings.sync_interval_min * 60)

    threading.Thread(target=loop, daemon=True, name="jira-sync").start()
