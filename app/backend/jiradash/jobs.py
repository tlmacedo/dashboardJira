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


def run_sync(db: Session, full: bool = False, client: JiraClient | None = None) -> dict:
    """Sincroniza (incremental ou completa + reconciliação) e invalida o cache dos payloads."""
    from . import cache
    cfg = load_teams()
    jql = cfg.get("jql_scope") or "project is not EMPTY"
    s = Syncer(db, client or make_client())
    try:
        out = s.run(jql, full=full)
        out["removidas"] = s.reconcile_open() if full else 0
    finally:
        cache.invalidate()
    return out


def sync_keys(db: Session, keys: list[str], client: JiraClient | None = None) -> None:
    """Webhook: sincroniza só as issues citadas; chave que o Jira não conhece mais (excluída) é marcada como removida."""
    from . import cache
    from .jira.client import JiraError
    from .models import Issue
    from datetime import datetime
    s = Syncer(db, client or make_client())
    try:
        s.run("key in (%s)" % ",".join(keys), full=True)
    except JiraError as e:
        if e.status != 400 or "does not exist" not in str(e):
            raise
        for iss in db.query(Issue).filter(Issue.key.in_(keys)):
            iss.deleted_at = datetime.utcnow()
        db.commit()
    finally:
        cache.invalidate()


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
            except Exception:
                log.exception("falha na sincronização")
            time.sleep(settings.sync_interval_min * 60)

    threading.Thread(target=loop, daemon=True, name="jira-sync").start()
