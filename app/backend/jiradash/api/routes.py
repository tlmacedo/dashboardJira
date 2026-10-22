from __future__ import annotations

import csv
import hashlib
import hmac
import io
import os
from datetime import date, datetime

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from ..analytics import Analytics, Filters
from ..config import settings
from ..db import get_db
from ..models import AssignmentSuggestion, AuditLog, Issue, Person, SyncState

router = APIRouter(prefix="/api")


def auth(x_api_key: str | None = Header(default=None)):
    if settings.api_key and x_api_key != settings.api_key:
        raise HTTPException(401, "chave de API ausente ou inválida")


def actor(x_user: str | None = Header(default=None)) -> str:
    return x_user or "gerente"


def flt(squad: str | None = None, project: str | None = None, person: int | None = None, type: str | None = None,
        priority: str | None = None, date_from: date | None = None, date_to: date | None = None) -> Filters:
    return Filters(squad or None, project or None, person, type or None, priority or None, date_from, date_to)


def A(db: Session = Depends(get_db), f: Filters = Depends(flt)) -> Analytics:
    return Analytics(db, f)


guard = [Depends(auth)]


@router.get("/health")
def health():
    return {"ok": True, "mode": settings.mode, "write_enabled": settings.allow_jira_write}


@router.get("/meta", dependencies=guard)
def meta(a: Analytics = Depends(A)):
    return a.meta()


for _name, _fn in [("overview", "overview"), ("open", "open_view"), ("neglected", "neglected"), ("closed", "closed_view"),
                   ("negative", "negative_view"), ("time-in-status", "time_in_status"), ("people", "people_view"),
                   ("risks", "risks"), ("forecast", "forecast_view"), ("bottlenecks", "bottlenecks"),
                   ("capacity", "capacity_view"), ("quality", "quality")]:
    def _make(fn):
        def handler(a: Analytics = Depends(A)):
            return getattr(a, fn)()
        return handler
    router.add_api_route(f"/{_name}", _make(_fn), methods=["GET"], dependencies=guard, name=_name)


# ---------------------------------------------------------------- distribuição
class SuggestIn(BaseModel):
    issue_keys: list[str] | None = None
    squad: str | None = None


class DecideIn(BaseModel):
    action: str  # aprovar | rejeitar
    person_id: int | None = None  # override: outra pessoa
    override_reason: str | None = None


def _log(db: Session, who: str, action: str, detail: dict):
    db.add(AuditLog(at=datetime.utcnow(), actor=who, action=action, detail=detail))


@router.get("/distribution/pending", dependencies=guard)
def dist_pending(a: Analytics = Depends(A)):
    return {"issues": a.pending_distribution()}


@router.post("/distribution/suggest", dependencies=guard)
def dist_suggest(body: SuggestIn, db: Session = Depends(get_db), who: str = Depends(actor)):
    a = Analytics(db, Filters(squad=body.squad))
    res = a.suggest(body.issue_keys)
    saved = 0
    for sq in res["squads"]:
        sid = next(s.id for s in a.squads.values() if s.key == sq["squad_key"])
        for x in sq["sugestoes"]:
            if not x["suggested"]:
                continue
            db.query(AssignmentSuggestion).filter_by(issue_key=x["issue_key"], status="pendente").delete()
            cur = db.query(Issue).filter_by(key=x["issue_key"]).first()
            db.add(AssignmentSuggestion(created_at=datetime.utcnow(), kind="novo", issue_key=x["issue_key"], squad_id=sid,
                                        from_person_id=cur.assignee_id if cur else None, suggested_person_id=x["suggested"],
                                        candidates=x["ranking"], reason=x["reason"]))
            saved += 1
    _log(db, who, "gerar_sugestoes", {"qtd": saved, "versao_motor": res["engine_version"]})
    db.commit()
    return {**res, "salvas": saved}


@router.get("/distribution/suggestions", dependencies=guard)
def dist_list(status: str | None = None, db: Session = Depends(get_db)):
    q = db.query(AssignmentSuggestion).order_by(AssignmentSuggestion.id.desc())
    if status:
        q = q.filter_by(status=status)
    names = {p.id: p.name for p in db.query(Person).all()}
    issues = {i.key: i.summary for i in db.query(Issue).filter(Issue.key.in_([s.issue_key for s in q.limit(300)])).all()}
    return [{"id": s.id, "criada": s.created_at.isoformat(), "tipo": s.kind, "issue": s.issue_key, "resumo": issues.get(s.issue_key, ""),
             "de": names.get(s.from_person_id), "sugerido": names.get(s.suggested_person_id), "status": s.status,
             "decidido_por": s.decided_by, "final": names.get(s.final_person_id), "motivo_override": s.override_reason,
             "motivo": s.reason, "ranking": s.candidates[:5]} for s in q.limit(300)]


@router.post("/distribution/{sid}/decide", dependencies=guard)
def dist_decide(sid: int, body: DecideIn, db: Session = Depends(get_db), who: str = Depends(actor)):
    s = db.get(AssignmentSuggestion, sid)
    if not s or s.status != "pendente":
        raise HTTPException(404, "sugestão inexistente ou já decidida")
    s.decided_by, s.decided_at = who, datetime.utcnow()
    if body.action == "rejeitar":
        s.status = "rejeitada"
        s.override_reason = body.override_reason
        _log(db, who, "rejeitar_sugestao", {"id": sid, "issue": s.issue_key})
        db.commit()
        return {"status": s.status}
    final = body.person_id or s.suggested_person_id
    if final != s.suggested_person_id and not body.override_reason:
        raise HTTPException(422, "informe o motivo ao escolher outra pessoa (trilha de auditoria)")
    s.final_person_id, s.override_reason = final, body.override_reason
    person = db.get(Person, final)
    issue = db.query(Issue).filter_by(key=s.issue_key).first()
    applied = "registrada (modo sombra: nada foi gravado no Jira)"
    if settings.mode == "demo":
        issue.assignee_id = final
        applied = "aplicada nos dados de demonstração"
        s.status = "aplicada"
    elif settings.allow_jira_write:
        from ..jira.client import JiraClient
        c = JiraClient(settings.jira_flavor, settings.jira_base_url, settings.jira_token, settings.jira_email)
        if not c.assignable(issue.key, person.jira_ref):
            raise HTTPException(409, f"{person.name} não é atribuível nesta issue (permissão/segurança)")
        c.assign(issue.key, person.jira_ref)
        issue.assignee_id = final
        s.status, applied = "aplicada", "gravada no Jira"
    else:
        s.status = "aprovada"
    _log(db, who, "aprovar_sugestao", {"id": sid, "issue": s.issue_key, "sugerido": s.suggested_person_id, "final": final,
                                       "override": body.override_reason, "efeito": applied})
    db.commit()
    return {"status": s.status, "efeito": applied}


@router.post("/rebalance/suggest", dependencies=guard)
def rebalance(squad: str | None = None, db: Session = Depends(get_db), who: str = Depends(actor)):
    a = Analytics(db)
    res = a.rebalance_suggestions(squad)
    n = 0
    for sq in res["squads"]:
        sid = next(s.id for s in a.squads.values() if s.key == sq["squad_key"])
        for mv in sq["movimentos"]:
            db.query(AssignmentSuggestion).filter_by(issue_key=mv["issue_key"], status="pendente").delete()
            db.add(AssignmentSuggestion(created_at=datetime.utcnow(), kind="rebalanceamento", issue_key=mv["issue_key"], squad_id=sid,
                                        from_person_id=mv["from_person_id"], suggested_person_id=mv["to_person_id"],
                                        candidates=mv["ranking"], reason=mv["reason"]))
            n += 1
    _log(db, who, "gerar_rebalanceamento", {"movimentos": n})
    db.commit()
    return {**res, "salvas": n}


@router.get("/audit", dependencies=guard)
def audit(limit: int = 200, db: Session = Depends(get_db)):
    return [{"quando": r.at.isoformat(), "quem": r.actor, "acao": r.action, "detalhe": r.detail}
            for r in db.query(AuditLog).order_by(AuditLog.id.desc()).limit(limit)]


# ---------------------------------------------------------------- sincronização
@router.get("/sync/status", dependencies=guard)
def sync_status(db: Session = Depends(get_db)):
    wm = db.query(SyncState).filter_by(key="watermark").first()
    return {"mode": settings.mode, "flavor": settings.jira_flavor, "jira": settings.jira_base_url or None,
            "ultima_sincronizacao": wm.value if wm else None, "intervalo_min": settings.sync_interval_min,
            "escrita_no_jira": settings.allow_jira_write}


@router.post("/sync", dependencies=guard)
def sync_now(full: bool = False, db: Session = Depends(get_db), who: str = Depends(actor)):
    if settings.mode == "demo":
        raise HTTPException(400, "modo demonstração: não há servidor Jira configurado")
    from ..jobs import run_sync
    out = run_sync(db, full)
    _log(db, who, "sincronizar", out)
    db.commit()
    return out


@router.post("/webhook/jira")
async def webhook(request: Request, db: Session = Depends(get_db)):
    """Webhook como otimização: valida HMAC (se houver segredo) e só sincroniza a issue citada."""
    body = await request.body()
    secret = os.getenv("JIRADASH_WEBHOOK_SECRET", "")
    if secret:
        sig = request.headers.get("X-Hub-Signature", "")
        want = "sha256=" + hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, want):
            raise HTTPException(401, "assinatura inválida")
    if settings.mode == "demo":
        return {"ignorado": "modo demonstração"}
    import json
    key = (json.loads(body or b"{}").get("issue") or {}).get("key")
    if key:
        from ..jobs import sync_keys
        sync_keys(db, [key])
    return {"ok": True, "issue": key}


# ---------------------------------------------------------------- exportação CSV
@router.get("/export/{view}.csv", dependencies=guard)
def export(view: str, a: Analytics = Depends(A)):
    if view == "open":
        rows = a.open_view()["issues"]
    elif view == "closed":
        rows = a.closed_view()["issues"]
    elif view == "risks":
        rows = a.risks()["issues"]
    else:
        raise HTTPException(404, "visão desconhecida")
    cols = ["key", "summary", "squad", "assignee", "status", "priority", "type", "created", "due", "idade_d", "no_status_d",
            "estimado_h", "gasto_h", "risco", "nivel_risco", "previsao"]
    buf = io.StringIO()
    buf.write("﻿")
    w = csv.writer(buf, delimiter=";")
    f = a.f
    w.writerow([f"# filtros: squad={f.squad} projeto={f.project} pessoa={f.person} tipo={f.type} prioridade={f.priority}"])
    w.writerow(cols)
    for r in rows:
        w.writerow([str(r.get(c, "")).replace(".", ",") if isinstance(r.get(c), float) else r.get(c, "") for c in cols])
    buf.seek(0)
    return StreamingResponse(iter([buf.getvalue()]), media_type="text/csv; charset=utf-8",
                             headers={"Content-Disposition": f'attachment; filename="{view}.csv"'})
