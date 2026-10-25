"""Detalhamento: dado um card ou item de card (tipo + valores), devolve as tasks que o compõem.

Cada `kind` reproduz o critério da tela de origem (mesmos filtros), para que o número do card
e a quantidade de tasks da janela de detalhe sejam sempre iguais.
"""
from __future__ import annotations

from datetime import date, datetime, timedelta
from typing import Callable

from . import reports
from .analytics import STARTED, Analytics
from .metrics import core as m
from .models import Absence

MAX_ROWS = 1000
Item = tuple  # (IssueRec, extra: str | None)
KINDS: dict[str, Callable] = {}


def kind(name: str):
    def deco(fn):
        KINDS[name] = fn
        return fn
    return deco


def pid(v: str) -> int | None:
    return None if v in ("", "None", "null", "0") else int(v)


def window(a: Analytics, days: int) -> tuple[date, date]:
    return a.f.date_from or a.today - timedelta(days=days - 1), a.f.date_to or a.today


def pairs_of(rs):
    return [(r, None) for r in rs]


# ------------------------------------------------------------------ abertas
@kind("abertas")
def _abertas(a, v1, v2):
    return "Issues abertas", "Todas as issues abertas no recorte atual.", pairs_of(a.open_recs())


@kind("em_andamento")
def _em_andamento(a, v1, v2):
    return "Issues em andamento", "Em status ativo, de espera ou bloqueio (WIP).", pairs_of(r for r in a.open_recs() if r.category in STARTED)


@kind("vencidas")
def _vencidas(a, v1, v2):
    return "Issues vencidas", "Abertas com vencimento anterior a hoje.", pairs_of(r for r in a.open_recs() if r.due and r.due < a.today)


@kind("bloqueadas")
def _bloqueadas(a, v1, v2):
    return "Issues bloqueadas", "Em status de bloqueio ou com impedimento sinalizado.", pairs_of(r for r in a.open_recs() if r.category == "blocked" or r.flagged)


@kind("negligenciadas")
def _negligenciadas(a, v1, v2):
    return "Em andamento sem atualização", "Em andamento e sem mudança há mais de 7 dias.", pairs_of(a.neglected_sets()["sem_atualizacao"])


@kind("neglig2")
def _neglig2(a, v1, v2):
    g = a.neglected_sets()
    seen, out = set(), []
    for k in ("sem_atualizacao", "paradas_no_status"):
        for r in g[k]:
            if r.key not in seen:
                seen.add(r.key)
                out.append((r, "sem atualização" if k == "sem_atualizacao" else "parada no status"))
    return "Negligenciadas", "Sem atualização há mais de 7 dias ou paradas no mesmo status por tempo acima do normal.", out


@kind("status")
def _status(a, v1, v2):
    return f"Abertas em “{v1}”", "Issues abertas neste status.", pairs_of(r for r in a.open_recs() if r.status == v1)


@kind("prioridade")
def _prioridade(a, v1, v2):
    return f"Abertas com prioridade {v1}", "", pairs_of(r for r in a.open_recs() if r.priority == v1)


@kind("idade")
def _idade(a, v1, v2):
    lo, hi = {"0-7 d": (0, 7), "8-14 d": (7.0001, 14), "15-30 d": (14.0001, 30), "> 30 d": (30.0001, 1e9)}.get(v1, (0, 1e9))
    return f"Abertas com idade {v1}", "Idade desde o início do trabalho (ou criação).", pairs_of(
        r for r in a.open_recs() if lo <= m.age_s(r, a.now, a.cat_of) / m.DAY <= hi)


@kind("vencimento")
def _vencimento(a, v1, v2):
    def ok(r):
        if v1 == "sem vencimento":
            return r.due is None
        if v1 == "vencidas":
            return bool(r.due and r.due < a.today)
        if v1 == "vence em 7 d":
            return bool(r.due and 0 <= (r.due - a.today).days <= 7)
        return bool(r.due and (r.due - a.today).days > 7)
    return f"Abertas: vencimento “{v1}”", "", pairs_of(r for r in a.open_recs() if ok(r))


@kind("acima_p85")
def _acima_p85(a, v1, v2):
    p85 = m.pct(a.cycle_hist_days(), 85)
    return "Em andamento acima do P85", f"Idade maior que o P85 histórico ({p85:.1f} d)." if p85 else "", pairs_of(
        r for r in a.open_recs() if p85 and r.category in STARTED and m.age_s(r, a.now, a.cat_of) / m.DAY > p85)


@kind("risco")
def _risco(a, v1, v2):
    lvl = {"medio": "médio"}.get(v1, v1)
    return f"Issues com risco {lvl}", "Pontuação de risco explicável; abra a issue para ver os fatores.", pairs_of(a.open_recs()), (lambda row: row["nivel_risco"] == lvl)


@kind("neglig")
def _neglig(a, v1, v2):
    names = {"sem_atualizacao": "Sem atualização", "sem_responsavel": "Sem responsável", "vencidas": "Vencidas", "bloqueadas": "Bloqueadas",
             "paradas_no_status": "Paradas no status", "a_fazer_antigas": "A fazer há mais de 30 dias"}
    return names.get(v1, v1), "Critério da tela Negligenciadas.", pairs_of(a.neglected_sets().get(v1, []))


@kind("qualidade")
def _qualidade(a, v1, v2):
    return f"Qualidade: {v1.replace('_', ' ')}", "Issues abertas com este problema de dados.", pairs_of(a.quality_sets().get(v1, []))


@kind("epico")
def _epico(a, v1, v2):
    rs = [r for r in a.load_all() if r.epic_key == v1 and r.type not in a.review_types]
    if v2 == "abertos":
        rs = [r for r in rs if r.category != "done"]
    elif v2 == "concluidos":
        rs = [r for r in rs if r.category == "done"]
    return f"Épico {v1}" + (f" ({v2})" if v2 else ""), "", pairs_of(rs)


# ------------------------------------------------------------------ fechadas
def _closed_in(a, days=30):
    s, e = window(a, days)
    return s, e, a.closed_recs(s, e)


@kind("fechadas")
def _fechadas(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    ok = a.done_ok(rs)
    return "Issues concluídas", f"Resolvidas de {s:%d/%m/%Y} a {e:%d/%m/%Y} (exclui canceladas e duplicadas).", pairs_of(ok)


@kind("canceladas")
def _canceladas(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    ok = {r.key for r in a.done_ok(rs)}
    return "Canceladas/descartadas", f"Resolução negativa de {s:%d/%m/%Y} a {e:%d/%m/%Y}.", pairs_of(r for r in rs if r.key not in ok)


@kind("fechadas_tipo")
def _fechadas_tipo(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    return f"Concluídas do tipo {v1}", "", pairs_of(r for r in a.done_ok(rs) if r.type == v1)


@kind("fechadas_projeto")
def _fechadas_projeto(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    return f"Concluídas no projeto {v1}", "", pairs_of(r for r in a.done_ok(rs) if r.project == v1)


@kind("fechadas_semana")
def _fechadas_semana(a, v1, v2):
    w = date.fromisoformat(v1)
    rs = [r for r in a.done_ok([r for r in a.recs() if r.resolved]) if m.week_start(reports.resolved_local_date(r)) == w]
    return f"Concluídas na semana de {w:%d/%m/%Y}", "", pairs_of(rs)


@kind("fechadas_pessoa")
def _fechadas_pessoa(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    p = pid(v1)
    ok = a.done_ok(rs)
    if v2 == "participou":
        sel = [r for r in ok if p in m.active_time_by_person(r, a.now, a.cat_of) or r.assignee_id == p]
        return f"Concluídas em que {a.name(p)} participou", "Responsável final ou com tempo ativo na issue.", pairs_of(sel)
    return f"Concluídas por {a.name(p)}", "Responsável no momento da conclusão.", pairs_of(r for r in ok if r.assignee_id == p)


@kind("ciclo_acima")
def _ciclo_acima(a, v1, v2):
    s, e, rs = _closed_in(a, 30)
    ok = a.done_ok(rs)
    cyc = [c / m.DAY for r in ok if (c := m.cycle_time_s(r, a.cat_of)) is not None]
    lim = m.pct(cyc, float(v1 or 85))
    return f"Concluídas com ciclo acima do P{v1 or 85}", f"Ciclo maior que {lim:.1f} d." if lim else "", [
        (r, f"ciclo {c / m.DAY:.1f} d") for r in ok if (c := m.cycle_time_s(r, a.cat_of)) is not None and lim and c / m.DAY > lim]


# ------------------------------------------------------------------ negativas
@kind("neg")
def _neg(a, v1, v2):
    names = {"reabertas": "Reabertas", "devolvidas": "Devolvidas", "canceladas_descartadas": "Canceladas/descartadas",
             "concluidas_atrasadas": "Concluídas com atraso", "abertas_vencidas": "Abertas vencidas",
             "estouro_estimativa": "Estouro de estimativa", "muitas_trocas_responsavel": "Muitas trocas de responsável"}
    s, e, cats, closed = a.negative_sets()
    rs = cats.get(v1, [])
    if v2:
        rs = [r for r in rs if r.assignee_id == pid(v2)]
    t = names.get(v1, v1) + (f" — {a.name(pid(v2))}" if v2 else "")
    return t, f"Período {s:%d/%m/%Y} a {e:%d/%m/%Y}.", pairs_of(rs)


@kind("tempo_status")
def _tempo_status(a, v1, v2):
    s, e = window(a, 90)
    out = []
    for r in a.done_ok(a.closed_recs(s, e)):
        if v2 and r.assignee_id != pid(v2):
            continue
        d = m.status_durations(r, a.now).get(v1, 0) / m.DAY
        if d > 0:
            out.append((r, d))
    out.sort(key=lambda x: -x[1])
    return f"Tempo em “{v1}”" + (f" — {a.name(pid(v2))}" if v2 else ""), f"Concluídas de {s:%d/%m/%Y} a {e:%d/%m/%Y}, da que mais ficou para a que menos ficou no status.", [
        (r, f"{d:.1f} d em {v1}") for r, d in out]


# ------------------------------------------------------------------ pessoas e capacidade
@kind("p_abertas")
def _p_abertas(a, v1, v2):
    p = pid(v1)
    mine = [r for r in a.load_all() if r.assignee_id == p and r.category != "done"]
    hist = m.pct(a.cycle_hist_days(), 85)
    if v2 == "wip":
        mine, t = [r for r in mine if r.category in STARTED], "em andamento"
    elif v2 == "vencidas":
        mine, t = [r for r in mine if r.due and r.due < a.today], "vencidas"
    elif v2 == "p85":
        mine, t = [r for r in mine if hist and m.age_s(r, a.now, a.cat_of) / m.DAY > hist], "acima do P85"
    else:
        t = "abertas"
    return f"{a.name(p)}: issues {t}", "", pairs_of(mine), ((lambda row: row["nivel_risco"] == "alto") if v2 == "risco" else None)


@kind("p_semana")
def _p_semana(a, v1, v2):
    p, wk = pid(v1), date.fromisoformat(v2)
    plan = a.plan(12)
    weeks = plan["weeks"]
    if wk.isoformat() not in weeks:
        return "Semana fora do horizonte", "", []
    idx = weeks.index(wk.isoformat())
    w0 = date.fromisoformat(weeks[0])
    out = []
    for r in a.load_all():
        if r.assignee_id != p or r.category == "done":
            continue
        last = max(1, min(len(weeks) - 1, (r.due - w0).days // 7)) if r.due else (1 if r.category in STARTED else 3)
        last = min(last, len(weeks) - 1)
        if idx <= last:
            out.append((r, f"{a.issue_hours(r) / (last + 1):.1f} h nesta semana"))
    return f"{a.name(p)}: demanda da semana de {wk:%d/%m/%Y}", "Cada issue espalha suas horas da semana atual até a semana do vencimento.", out


@kind("slips")
def _slips(a, v1, v2):
    long_, free = a.plan(12), a.plan(12, True)
    out = []
    for r in a.open_recs():
        if not r.due or r.due < a.today or r.assignee_id not in long_["people"]:
            continue
        real, sem = long_["predicted"].get(r.key), free["predicted"].get(r.key)
        if real is None or real > r.due:
            cause = "ausência" if (sem and sem <= r.due) else "carga"
            if v1 and v1 != cause:
                continue
            out.append((r, (f"previsão {real:%d/%m/%Y}" if real else "previsão além do horizonte") + f" · causa: {cause}"))
    return "Atrasos previstos pela capacidade", "Pela fila e horas líquidas da pessoa, terminam depois do vencimento.", out


@kind("sobrecarregados")
def _sobrecarregados(a, v1, v2):
    over = {u["person_id"]: u for u in a.utilization_rows() if u["utilizacao_4s"] > 1.1}
    return "Issues de quem está acima da capacidade", "Pessoas com demanda das próximas 4 semanas acima de 110% da capacidade.", [
        (r, f"{over[r.assignee_id]['nome']}: {over[r.assignee_id]['utilizacao_4s'] * 100:.0f}%") for r in a.load_all()
        if r.category != "done" and r.assignee_id in over]


@kind("ausentes14")
def _ausentes14(a, v1, v2):
    lim = a.today + timedelta(days=14)
    ids = {x.person_id for x in a.db.query(Absence).filter(Absence.end >= a.today, Absence.start <= lim)} & a.member_ids()
    return "Issues de quem se ausenta nos próximos 14 dias", "Candidatas a replanejar ou rebalancear.", [
        (r, a.name(r.assignee_id)) for r in a.load_all() if r.category != "done" and r.assignee_id in ids]


@kind("etapa")
def _etapa(a, v1, v2):
    return f"Etapa “{v1}”", "Issues abertas nesta etapa do fluxo.", pairs_of(r for r in a.open_recs() if r.status == v1)


# ------------------------------------------------------------------ metas e prazos
@kind("meta")
def _meta(a, v1, v2):
    start = a.f.date_from or a.today - timedelta(days=89)
    end = a.f.date_to or a.today
    rs = [r for r in a.done_ok(a.closed_recs(start, end)) if r.type != "Epic"]
    q = dict(x.split("=", 1) for x in (v2 or "").split("&") if "=" in x)
    if "tipo" in q:
        rs = [r for r in rs if r.type == q["tipo"]]
    if "prioridade" in q:
        rs = [r for r in rs if r.priority == q["prioridade"]]
    if "pessoa" in q:
        rs = [r for r in rs if r.assignee_id == pid(q["pessoa"])]
    if "squad" in q:
        rs = [r for r in rs if a.squad_name(r.squad_id) == q["squad"]]
    if "periodo" in q:
        g = q.get("g", "week")
        rs = [r for r in rs if r.due and reports.period_key(reports.resolved_local_date(r), g) == q["periodo"]]
    out = []
    for r in rs:
        d = reports.delta_days(r, r.due)
        od = reports.delta_days(r, reports.original_due(r))
        if d is None:
            sel = v1 == "sem_venc"
            lab = "sem vencimento"
        else:
            b = reports.bucket_of(d)
            lab = b + ("" if od == d else f" (pela data original: {reports.bucket_of(od)})" if od is not None else "")
            sel = (v1 in ("all", "") or (v1 == "no_prazo" and d <= 0) or (v1 == "antecipadas" and d <= -1) or (v1 == "d1d2" and d in (-1, -2))
                   or (v1 == "no_dia" and d == 0) or (v1 == "atrasadas" and d > 0) or (v1 == b)
                   or (v1 == "orig_no_prazo" and od is not None and od <= 0)
                   or (v1 == "queda_original" and d <= 0 and od is not None and od > 0))
        if sel:
            out.append((r, lab))
    titles = {"no_prazo": "Concluídas no prazo", "antecipadas": "Antecipadas (D-1 ou antes)", "d1d2": "Entregues em D-1 ou D-2", "no_dia": "Entregues no dia do vencimento",
              "atrasadas": "Concluídas com atraso", "sem_venc": "Concluídas sem vencimento", "orig_no_prazo": "No prazo pela data original",
              "queda_original": "No prazo só por causa de prorrogação", "all": "Concluídas com vencimento"}
    def pretty(k, v):
        if k == "periodo":
            return {"week": "semana de ", "month": "mês ", "day": "dia "}.get(q.get("g", "week"), "") + (date.fromisoformat(v).strftime("%d/%m/%Y") if len(v) == 10 else v)
        if k == "pessoa":
            return a.name(pid(v))
        return v
    return titles.get(v1, f"Entregas: {v1}") + (f" · {' · '.join(pretty(k, v) for k, v in q.items() if k != 'g')}" if q else ""), \
        f"Resolvidas de {start:%d/%m/%Y} a {end:%d/%m/%Y}; a coluna Detalhe mostra o dia da entrega em relação ao vencimento.", out


# ------------------------------------------------------------------ checklist x review
BANDS = {"< 1 h": (0, 1), "1 a 4 h": (1, 4), "4 a 12 h": (4, 12), "≥ 12 h": (12, 1e9)}


@kind("r_pares")
def _r_pares(a, v1, v2):
    start, end, pairs, rows, orphans = reports.review_pairs(a)
    sel = []
    for (p, r), x in zip(pairs, rows):
        ok = {"all": True, "": True, "com_alerta": bool(x["alertas"]), "alerta": v2 in x["alertas"],
              "label": v2 in set(p.labels) | set(r.labels), "so_checklist": v2 in x["so_checklist"], "so_review": v2 in x["so_review"],
              "faixa": v2 in BANDS and x["ciclo_review_d"] is not None and BANDS[v2][0] <= x["review_ativa_h"] < BANDS[v2][1],
              "reaberto": bool(x["reaberturas_checklist"]),
              "pessoa": v1 == "pessoa" and pid(v2) in (x["checklist_resp_id"], x["review_resp_id"])}.get(v1, True)
        if ok:
            sel.append((p, r, x))
    out = []
    for p, r, x in sel:
        al = ("; ".join(x["alertas"])) if x["alertas"] else "sem alertas"
        out.append((p, f"checklist · review {r.key} · {al}"))
        out.append((r, f"review de {p.key} · Δ venc. {x['dif_vencimento_d'] if x['dif_vencimento_d'] is not None else '—'} d"))
    t = {"all": "Pares checklist + review", "com_alerta": "Pares com alerta", "alerta": f"Pares: {v2}", "label": f"Pares com a label “{v2}”",
         "so_checklist": f"Label “{v2}” só no checklist", "so_review": f"Label “{v2}” só na review", "faixa": f"Pares com review ativa {v2}",
         "reaberto": "Pares com checklist reaberto", "pessoa": f"Pares de {a.name(pid(v2))}" if v1 == "pessoa" else ""}.get(v1, "Pares")
    return t, f"{len(sel)} pares; cada par aparece como duas linhas (checklist e review).", out


@kind("r_sem_review")
def _r_sem_review(a, v1, v2):
    start, end, pairs, rows, orphans = reports.review_pairs(a)
    return "Checklists sem review filha", "", pairs_of(orphans)


# ------------------------------------------------------------------ integridade
@kind("i_sinal")
def _i_sinal(a, v1, v2):
    start, end, ev, _ = reports.integrity_core(a)
    return _evidence_items(a, [e for e in ev if (not v1 or e["sinal"] == v1) and (not v2 or str(e["pessoa_id"]) == v2)],
                           (reports.SIGNALS.get(v1, ("Evidências",))[0]) + (f" — {a.name(pid(v2))}" if v2 else ""))


@kind("i_pessoa")
def _i_pessoa(a, v1, v2):
    start, end, ev, _ = reports.integrity_core(a)
    return _evidence_items(a, [e for e in ev if str(e["pessoa_id"]) == v1], f"Evidências de {a.name(pid(v1))}")


@kind("i_alto")
def _i_alto(a, v1, v2):
    d = reports.integrity_view(a)
    ids = {p["person_id"] for p in d["pessoas"] if p["nivel"] == ("alto" if v1 in ("", "alto") else v1)}
    start, end, ev, _ = reports.integrity_core(a)
    return _evidence_items(a, [e for e in ev if e["pessoa_id"] in ids], f"Evidências de pessoas em atenção {v1 or 'alta'}")


def _evidence_items(a, evs, title):
    by = {r.key: r for r in a.load_all()}
    grouped: dict[str, list[str]] = {}
    for e in evs:
        grouped.setdefault(e["issue"], []).append(f"{reports.SIGNALS[e['sinal']][0]} ({e['pessoa']}): {e['detalhe']}")
    items = [(by[k], " | ".join(v)) for k, v in grouped.items() if k in by]
    return title, (f"{len(evs)} evidências em {len(items)} issues. Indícios para revisão humana; confira o histórico no Jira antes de concluir qualquer coisa. "
                   "A coluna Detalhe lista cada evidência."), items


# ------------------------------------------------------------------ execução
def run(a: Analytics, kind_name: str, v1: str = "", v2: str = "") -> dict:
    fn = KINDS.get(kind_name)
    if fn is None:
        raise KeyError(kind_name)
    res = fn(a, v1, v2)
    title, desc, items = res[0], res[1], list(res[2])
    rowfilter = res[3] if len(res) > 3 else None
    hist, smed, plan = a.cycle_hist_days(), a.status_median_days(), a.plan()
    seen: set[str] = set()
    rows = []
    for r, extra in items:
        if r.key in seen:
            continue
        seen.add(r.key)
        row = a._closed_row(r) if r.resolved else a.issue_row(r, hist, smed, plan)
        if r.resolved:
            row["nivel_risco"], row["risco"] = None, None
        if rowfilter and not rowfilter(row):
            continue
        row["extra"] = extra
        row["tipo"] = r.type
        rows.append(row)
    return {"kind": kind_name, "v1": v1, "v2": v2, "titulo": title, "descricao": desc, "total": len(rows),
            "truncado": len(rows) > MAX_ROWS, "issues": rows[:MAX_ROWS],
            "filtros": {k: str(v) for k, v in vars(a.f).items() if v}}
