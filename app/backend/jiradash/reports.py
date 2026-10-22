"""Relatórios gerenciais avançados: metas de prazo, checklist x review e integridade dos resultados."""
from __future__ import annotations

from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from .analytics import Analytics, prio_rank
from .config import settings
from .metrics import core as m

TZ = ZoneInfo(settings.tz)
UTC = ZoneInfo("UTC")


def local(dt: datetime) -> datetime:
    return dt.replace(tzinfo=UTC).astimezone(TZ)


def parse_due(v: str | None) -> date | None:
    if not v:
        return None
    for fmt in ("%Y-%m-%d", "%d/%b/%y", "%d/%m/%Y"):
        try:
            return datetime.strptime(v[:11].strip() if fmt == "%d/%b/%y" else v[:10], fmt).date()
        except ValueError:
            continue
    return None


def original_due(r: m.IssueRec) -> date | None:
    """Primeiro vencimento conhecido: o 'de' da primeira mudança, ou o primeiro valor atribuído."""
    evs = sorted((e for e in r.events if e.field == "duedate"), key=lambda e: e.at)
    if not evs:
        return r.due
    return parse_due(evs[0].from_value) or parse_due(evs[0].to_value)


def r1(v):
    return None if v is None else round(v, 1)


def bucket_of(delta: int) -> str:
    """delta = dia da resolução − vencimento (em dias de calendário, fuso local)."""
    if delta <= -3:
        return "D-3 ou antes"
    return {-2: "D-2", -1: "D-1", 0: "No dia (D)"}.get(delta, "Atrasada 1-3 d" if delta <= 3 else "Atrasada > 3 d")


BUCKETS = ["D-3 ou antes", "D-2", "D-1", "No dia (D)", "Atrasada 1-3 d", "Atrasada > 3 d"]


def resolved_local_date(r: m.IssueRec) -> date:
    return local(r.resolved).date()


def delta_days(r: m.IssueRec, due: date | None) -> int | None:
    if not r.resolved or not due:
        return None
    return (resolved_local_date(r) - due).days


def period_key(d: date, gran: str) -> str:
    if gran == "month":
        return d.strftime("%Y-%m")
    if gran == "day":
        return d.isoformat()
    return m.week_start(d).isoformat()


# ====================================================================== metas e prazos
def goals_view(a: Analytics, granularity: str = "week") -> dict:
    start = a.f.date_from or a.today - timedelta(days=89)
    end = a.f.date_to or a.today
    rs = [r for r in a.done_ok(a.closed_recs(start, end)) if r.type not in ("Epic",)]
    with_due = [r for r in rs if r.due]
    tot = len(with_due)

    def counts(rows, due_of=lambda r: r.due):
        c = Counter()
        for r in rows:
            d = delta_days(r, due_of(r))
            if d is not None:
                c[bucket_of(d)] += 1
        return c

    def pct_on_time(rows, due_of=lambda r: r.due):
        ok = [delta_days(r, due_of(r)) for r in rows if due_of(r)]
        return round(100 * sum(1 for d in ok if d <= 0) / len(ok), 1) if ok else None

    def early_pct(rows):
        ok = [delta_days(r, r.due) for r in rows if r.due]
        return round(100 * sum(1 for d in ok if d <= -1) / len(ok), 1) if ok else None

    cur = counts(with_due)
    kp = {"concluidas": len(rs), "com_vencimento": tot, "sem_vencimento": len(rs) - tot,
          "no_prazo_pct": pct_on_time(with_due), "no_prazo_data_original_pct": pct_on_time(with_due, original_due),
          "antecipadas_pct": early_pct(with_due),
          "d1_d2_pct": round(100 * (cur["D-1"] + cur["D-2"]) / tot, 1) if tot else None,
          "no_dia_pct": round(100 * cur["No dia (D)"] / tot, 1) if tot else None,
          "atrasadas_pct": round(100 * (cur["Atrasada 1-3 d"] + cur["Atrasada > 3 d"]) / tot, 1) if tot else None}
    series = defaultdict(Counter)
    for r in with_due:
        series[period_key(resolved_local_date(r), granularity)][bucket_of(delta_days(r, r.due))] += 1
    serie = [{"periodo": k, "total": sum(v.values()), **{b: v.get(b, 0) for b in BUCKETS},
              "no_prazo_pct": round(100 * sum(v.get(b, 0) for b in BUCKETS[:4]) / max(1, sum(v.values())), 1)} for k, v in sorted(series.items())]

    def grp(keyf, names=lambda k: k):
        d = defaultdict(list)
        for r in with_due:
            d[keyf(r)].append(r)
        out = []
        for k, rows in d.items():
            c = counts(rows)
            out.append({"chave": k, "nome": names(k), "total": len(rows), **{b: c.get(b, 0) for b in BUCKETS},
                        "no_prazo_pct": pct_on_time(rows), "no_prazo_data_original_pct": pct_on_time(rows, original_due),
                        "antecipadas_pct": early_pct(rows)})
        return sorted(out, key=lambda x: str(x["nome"]))

    meta_ok = a.goals["on_time_pct"]
    pessoas = grp(lambda r: r.assignee_id, a.name)
    for p in pessoas:
        p["meta_atingida"] = p["no_prazo_pct"] is not None and p["no_prazo_pct"] >= meta_ok
        p["queda_data_original"] = (None if p["no_prazo_pct"] is None or p["no_prazo_data_original_pct"] is None
                                    else round(p["no_prazo_pct"] - p["no_prazo_data_original_pct"], 1))
    return {"periodo": {"de": start.isoformat(), "ate": end.isoformat()}, "granularidade": granularity, "buckets": BUCKETS,
            "metas": {"no_prazo_pct": meta_ok, "antecipadas_pct": a.goals["early_pct"]}, "kpis": kp,
            "distribuicao": [{"bucket": b, "qtd": cur.get(b, 0)} for b in BUCKETS], "serie": serie,
            "por_tipo": grp(lambda r: r.type), "por_pessoa": pessoas, "por_squad": grp(lambda r: r.squad_id, a.squad_name),
            "por_prioridade": sorted(grp(lambda r: r.priority), key=lambda x: prio_rank(x["nome"])),
            "nota": "Dia da resolução em fuso local menos dia do vencimento. 'Data original' usa o vencimento antes de qualquer prorrogação."}


# ====================================================================== checklist x review
TECH_LABELS = {"review"}


def pair_row(a: Analytics, p: m.IssueRec, r: m.IssueRec) -> dict:
    now = a.now
    pl, rl = set(p.labels) - TECH_LABELS, set(r.labels) - TECH_LABELS
    p_cyc, r_cyc = m.cycle_time_s(p, a.cat_of), m.cycle_time_s(r, a.cat_of)
    r_active_h = sum(v for k, v in m.status_durations(r, now).items() if a.cat_of.get(k) == "active") / 3600
    due_gap = (r.due - p.due).days if r.due and p.due else None
    res_gap = ((p.resolved - r.resolved).total_seconds() / 86400) if p.resolved and r.resolved else None
    flags = []
    if due_gap is not None and due_gap > 0:
        flags.append("review vence depois do checklist")
    if p.resolved and (not r.resolved or r.resolved > p.resolved):
        flags.append("checklist fechado antes da review")
    if p.assignee_id and p.assignee_id == r.assignee_id:
        flags.append("mesma pessoa no checklist e na review")
    if r.resolved and r_active_h < 0.5 and m.reopen_count(p, a.cat_of):
        flags.append("review muito curta e checklist reaberto")
    if not r.resolved and r.due and r.due < a.today:
        flags.append("review vencida")
    if r.due and r.resolved and local(r.resolved).date() > r.due:
        flags.append("review atrasada")
    return {
        "checklist": p.key, "review": r.key, "resumo": p.summary[:70], "squad": a.squad_name(p.squad_id),
        "checklist_resp": a.name(p.assignee_id), "review_resp": a.name(r.assignee_id),
        "checklist_resp_id": p.assignee_id, "review_resp_id": r.assignee_id,
        "criacao_checklist": p.created.date().isoformat(), "criacao_review": r.created.date().isoformat(),
        "atraso_criacao_review_h": round((r.created - p.created).total_seconds() / 3600, 1),
        "venc_checklist": p.due.isoformat() if p.due else None, "venc_review": r.due.isoformat() if r.due else None,
        "dif_vencimento_d": due_gap, "resolvido_checklist": p.resolved.date().isoformat() if p.resolved else None,
        "resolvido_review": r.resolved.date().isoformat() if r.resolved else None,
        "review_antes_do_checklist_d": round(res_gap, 1) if res_gap is not None else None,
        "ciclo_checklist_d": m.days(p_cyc), "ciclo_review_d": m.days(r_cyc), "review_ativa_h": round(r_active_h, 1),
        "review_pct_do_ciclo": round(100 * (r_cyc or 0) / p_cyc, 1) if p_cyc and r_cyc else None,
        "labels_comuns": sorted(pl & rl), "so_checklist": sorted(pl - rl), "so_review": sorted(rl - pl),
        "reaberturas_checklist": m.reopen_count(p, a.cat_of), "devolucoes_checklist": m.return_count(p, a.order, a.cat_of),
        "status_checklist": p.status, "status_review": r.status, "alertas": flags,
        "horas_review": round(r.time_spent_s / 3600, 1), "horas_checklist": round(p.time_spent_s / 3600, 1),
        "prio": p.priority,
        "checklist_no_prazo": (delta_days(p, p.due) <= 0) if p.resolved and p.due else None,
        "review_no_prazo": (delta_days(r, r.due) <= 0) if r.resolved and r.due else None,
    }


def review_pairs(a: Analytics):
    allr = a.load_all()
    by_key = {r.key: r for r in allr}
    start = a.f.date_from or a.today - timedelta(days=89)
    end = a.f.date_to or a.today
    pairs, orphans = [], []
    child_of: dict[str, list[m.IssueRec]] = defaultdict(list)
    for r in allr:
        if r.type in a.review_types and r.parent_key:
            child_of[r.parent_key].append(r)
    scoped = {r.key for r in a.recs()}  # respeita filtros de pessoa/tipo/prioridade aplicados ao checklist
    for p in allr:
        if p.type not in a.checklist_types or p.key not in scoped:
            continue
        ref = p.resolved.date() if p.resolved else p.created.date()
        if not (start <= ref <= end) and p.resolved:
            continue
        kids = child_of.get(p.key, [])
        if not kids:
            orphans.append(p)
            continue
        for r in kids:
            pairs.append((p, r))
    rows = [pair_row(a, p, r) for p, r in pairs]
    return start, end, pairs, rows, orphans


def review_view(a: Analytics) -> dict:
    start, end, pairs, rows, orphans = review_pairs(a)
    n = len(rows)
    done = [x for x in rows if x["ciclo_review_d"] is not None]

    def pct(k, rows_=None):
        rows_ = rows if rows_ is None else rows_
        return round(100 * k / len(rows_), 1) if rows_ else None

    lag = [x["atraso_criacao_review_h"] for x in rows]
    before = [x["review_antes_do_checklist_d"] for x in rows if x["review_antes_do_checklist_d"] is not None]
    # aprendizado 1: duração ativa da review x reabertura do checklist
    bands = [("< 1 h", 0, 1), ("1 a 4 h", 1, 4), ("4 a 12 h", 4, 12), ("≥ 12 h", 12, 1e9)]
    dur_tab = []
    for name, lo, hi in bands:
        sub = [x for x in done if lo <= x["review_ativa_h"] < hi]
        dur_tab.append({"faixa": name, "n": len(sub), "reabertura_pct": pct(sum(1 for x in sub if x["reaberturas_checklist"]), sub),
                        "devolucao_pct": pct(sum(1 for x in sub if x["devolucoes_checklist"]), sub),
                        "atraso_checklist_pct": pct(sum(1 for x in sub if x["checklist_no_prazo"] is False), sub)})
    # aprendizado 2: labels
    lab = defaultdict(lambda: {"n": 0, "reab": 0, "atraso": 0, "ciclo": []})
    base_reab = sum(1 for x in rows if x["reaberturas_checklist"]) / max(1, n)
    for p, r in pairs:
        row = next(x for x in rows if x["review"] == r.key)
        for l in (set(p.labels) | set(r.labels)) - TECH_LABELS | ({"review-" + l for l in set(r.labels) - set(p.labels) - TECH_LABELS} if False else set()):
            d = lab[l]
            d["n"] += 1
            d["reab"] += 1 if row["reaberturas_checklist"] else 0
            d["atraso"] += 1 if row["checklist_no_prazo"] is False else 0
            if row["ciclo_checklist_d"] is not None:
                d["ciclo"].append(row["ciclo_checklist_d"])
    labels_tab = []
    for l, d in lab.items():
        rr = d["reab"] / d["n"]
        labels_tab.append({"label": l, "n": d["n"], "reabertura_pct": round(100 * rr, 1), "atraso_pct": round(100 * d["atraso"] / d["n"], 1),
                           "ciclo_p50_d": round(m.pct(d["ciclo"], 50), 1) if d["ciclo"] else None,
                           "lift_reabertura": round(rr / base_reab, 2) if base_reab else None})
    labels_tab.sort(key=lambda x: -(x["lift_reabertura"] or 0))
    diff_c = Counter()
    for x in rows:
        diff_c.update({("só no checklist", l) for l in x["so_checklist"]})
        diff_c.update({("só na review", l) for l in x["so_review"]})
    diffs = [{"onde": k[0], "label": k[1], "pares": v} for k, v in diff_c.most_common(14)]
    # insights em texto (somente quando há amostra suficiente)
    ins = []
    short, longr = dur_tab[0], dur_tab[-1]
    if short["n"] >= 5 and longr["n"] >= 5 and short["reabertura_pct"] is not None and longr["reabertura_pct"] is not None:
        ins.append(f"Reviews com menos de 1 h ativa: checklist reaberto em {short['reabertura_pct']}% dos casos, contra {longr['reabertura_pct']}% quando a review passa de 12 h.")
    same = [x for x in rows if "mesma pessoa no checklist e na review" in x["alertas"]]
    if same:
        ins.append(f"{len(same)} pares ({pct(len(same))}%) têm a mesma pessoa no checklist e na review: não há revisão independente.")
    late_due = [x for x in rows if (x["dif_vencimento_d"] or 0) > 0]
    if late_due:
        ins.append(f"{len(late_due)} reviews ({pct(len(late_due))}%) vencem depois do checklist pai: o prazo da review não cabe no do checklist.")
    closed_first = [x for x in rows if "checklist fechado antes da review" in x["alertas"]]
    if closed_first:
        ins.append(f"{len(closed_first)} checklists foram fechados antes da review terminar.")
    top = [l for l in labels_tab if l["n"] >= 5 and (l["lift_reabertura"] or 0) >= 1.5]
    if top:
        ins.append("Labels associadas a mais reabertura do checklist: " + ", ".join(f"{l['label']} ({l['lift_reabertura']}×)" for l in top[:3]) + ".")
    if orphans:
        ins.append(f"{len(orphans)} checklists do período não têm review filha.")
    # contingência atraso review x atraso checklist
    rl_ = [x for x in rows if x["review_no_prazo"] is not None and x["checklist_no_prazo"] is not None]
    cont = {"review_atrasada_checklist_atrasado_pct": pct(sum(1 for x in rl_ if not x["review_no_prazo"] and not x["checklist_no_prazo"]), [x for x in rl_ if not x["review_no_prazo"]]),
            "review_no_prazo_checklist_atrasado_pct": pct(sum(1 for x in rl_ if x["review_no_prazo"] and not x["checklist_no_prazo"]), [x for x in rl_ if x["review_no_prazo"]])}
    kp = {"pares": n, "checklists_sem_review": len(orphans),
          "review_ciclo_p50_d": r1(m.pct([x["ciclo_review_d"] for x in done], 50)) if done else None,
          "checklist_ciclo_p50_d": r1(m.pct([x["ciclo_checklist_d"] for x in rows if x["ciclo_checklist_d"] is not None], 50)),
          "review_pct_do_ciclo_p50": r1(m.pct([x["review_pct_do_ciclo"] for x in rows if x["review_pct_do_ciclo"] is not None], 50)),
          "criacao_review_p50_h": r1(m.pct(lag, 50)),
          "review_antes_do_pai_pct": pct(sum(1 for d in before if d >= 0), [1] * len(before)) if before else None,
          "mesma_pessoa_pct": pct(len(same)), "venc_review_depois_pct": pct(len(late_due)), "pai_fechado_antes_pct": pct(len(closed_first)),
          "com_alerta_pct": pct(sum(1 for x in rows if x["alertas"]))}
    return {"periodo": {"de": start.isoformat(), "ate": end.isoformat()}, "kpis": kp, "aprendizados": ins,
            "por_duracao_review": dur_tab, "labels": labels_tab[:25], "diferencas_labels": diffs, "contingencia": cont,
            "pares": sorted(rows, key=lambda x: (-len(x["alertas"]), x["checklist"]))[:400],
            "sem_review": [{"key": p.key, "resumo": p.summary[:70], "resp": a.name(p.assignee_id), "status": p.status} for p in orphans][:100]}


def pair_detail(a: Analytics, parent_key: str) -> dict:
    allr = {r.key: r for r in a.load_all()}
    p = allr.get(parent_key)
    if not p:
        return {}
    kids = [r for r in allr.values() if r.parent_key == parent_key and r.type in a.review_types]
    def tl(r):
        return [{"quando": local(t.at).strftime("%d/%m/%Y %H:%M"), "de": t.from_status, "para": t.to_status, "quem": a.name(t.author_id)}
                for t in sorted(r.transitions, key=lambda t: t.at)]
    return {"checklist": {"key": p.key, "labels": p.labels, "timeline": tl(p), "status_d": {k: round(v / m.DAY, 1) for k, v in m.status_durations(p, a.now).items()}},
            "reviews": [{"key": r.key, "labels": r.labels, "timeline": tl(r), "status_d": {k: round(v / m.DAY, 1) for k, v in m.status_durations(r, a.now).items()},
                         **pair_row(a, p, r)} for r in kids]}


# ====================================================================== integridade
SIGNALS = {
    "prazo_prorrogado": ("Vencimento prorrogado", "O vencimento foi movido para mais tarde. Mais grave se feito perto do prazo ou se a entrega estava atrasada pela data original."),
    "prazo_resgatado": ("Atraso 'resgatado' por prorrogação", "Pela data original a entrega estaria atrasada, mas a prorrogação a fez parecer no prazo."),
    "prazo_definido_tarde": ("Vencimento definido tardiamente", "O vencimento só foi preenchido perto da conclusão, após o trabalho quase pronto."),
    "fechamento_instantaneo": ("Criada e fechada em minutos", "Issue resolvida poucos minutos após a criação, sem tempo de trabalho."),
    "pulou_fluxo": ("Pulou o fluxo", "Foi de a fazer direto para concluído, sem passar por etapa de trabalho."),
    "reabre_e_fecha": ("Reabre e fecha rapidamente", "Issue reaberta e fechada de novo em menos de 2 h, o que renova a data de resolução."),
    "apropriacao": ("Troca de responsável logo antes de fechar", "Alguém assumiu a issue pouco antes de concluí-la, ficando com o crédito do trabalho de outra pessoa."),
    "fechamento_em_lote": ("Fechamento em lote", "Várias issues concluídas em poucos minutos pela mesma pessoa."),
    "estimativa_aumentada": ("Estimativa aumentada durante o trabalho", "A estimativa original subiu depois do início, o que melhora a acurácia aparente."),
    "label_removida": ("Label de criticidade removida perto do fim", "Label como urgente/sla/regressão removida pouco antes de fechar ou após o vencimento."),
    "fechada_por_outro": ("Fechada por quem não é o responsável", "Quem concluiu a issue não era o responsável atual."),
    "ultimo_minuto": ("Fechada no último minuto do prazo", "Resolvida entre 22h e 24h do dia do vencimento (sinal fraco, só relevante em volume)."),
}
SEVERITY = {"prazo_resgatado": 3, "apropriacao": 3, "fechamento_instantaneo": 3, "reabre_e_fecha": 3, "fechamento_em_lote": 3,
            "pulou_fluxo": 2, "prazo_prorrogado": 2, "prazo_definido_tarde": 2, "estimativa_aumentada": 2, "label_removida": 2,
            "fechada_por_outro": 1, "ultimo_minuto": 1}
CRIT_LABELS = {"urgente", "sla", "regressao"}


def integrity_core(a: Analytics):
    start = a.f.date_from or a.today - timedelta(days=89)
    end = a.f.date_to or a.today
    pool = [r for r in a.recs() if r.type not in a.review_types or True]
    cat = a.cat_of
    ev: list[dict] = []  # evidências: {sinal, issue, pessoa, quando, detalhe}
    resolved_in = [r for r in pool if r.resolved and start <= r.resolved.date() <= end and not m.is_negative_resolution(r, a.negative)]

    def add(sig: str, r: m.IssueRec, who: int | None, at: datetime, detail: str):
        ev.append({"sinal": sig, "issue": r.key, "resumo": r.summary[:60], "pessoa_id": who, "pessoa": a.name(who) if who else "—",
                   "quando": local(at).strftime("%d/%m/%Y %H:%M"), "detalhe": detail, "gravidade": SEVERITY[sig], "squad_id": r.squad_id})

    for r in pool:
        trs = sorted(r.transitions, key=lambda t: t.at)
        done_trs = [t for t in trs if cat.get(t.to_status) == "done"]
        fin = done_trs[-1] if done_trs else None
        in_period = bool(r.resolved and start <= r.resolved.date() <= end)
        # ---- vencimento
        due_evs = sorted((e for e in r.events if e.field == "duedate"), key=lambda e: e.at)
        for e in due_evs:
            old, new = parse_due(e.from_value), parse_due(e.to_value)
            if not (start <= e.at.date() <= end) and not in_period:
                continue
            if old and new and new > old:
                near = (local(e.at).date() >= old - timedelta(days=2))
                rescued = bool(r.resolved and resolved_local_date(r) > old and resolved_local_date(r) <= new)
                if rescued:
                    add("prazo_resgatado", r, e.author_id, e.at, f"vencimento {old:%d/%m} → {new:%d/%m}; resolvida em {resolved_local_date(r):%d/%m}")
                elif near:
                    add("prazo_prorrogado", r, e.author_id, e.at, f"vencimento {old:%d/%m} → {new:%d/%m} (a {max(0, (old - local(e.at).date()).days)} d do prazo)")
            elif not old and new and r.resolved:
                lead = (r.resolved - r.created).total_seconds()
                if lead > 0 and (e.at - r.created).total_seconds() / lead > 0.7 and in_period:
                    add("prazo_definido_tarde", r, e.author_id, e.at, f"vencimento {new:%d/%m} definido a {100 * (e.at - r.created).total_seconds() / lead:.0f}% do ciclo")
        if not in_period or not fin:
            continue
        who_fin = fin.author_id or r.assignee_id
        # ---- instantâneo / pulou fluxo
        life_min = (r.resolved - r.created).total_seconds() / 60
        started = any(cat.get(t.to_status) in ("active", "wait", "blocked") for t in trs)
        if life_min < 15:
            add("fechamento_instantaneo", r, who_fin, r.resolved, f"fechada {life_min:.0f} min após a criação")
        elif not started and r.time_spent_s == 0:
            add("pulou_fluxo", r, who_fin, r.resolved, "do status inicial direto para concluído")
        # ---- reabre e fecha
        for i, t in enumerate(trs):
            if cat.get(t.from_status) == "done" and cat.get(t.to_status) != "done":
                re_close = next((x for x in trs[i + 1:] if cat.get(x.to_status) == "done"), None)
                prev_done = max((x.at for x in trs[:i] if cat.get(x.to_status) == "done"), default=None)
                if re_close and (re_close.at - t.at) < timedelta(hours=2) and prev_done and (t.at - prev_done) < timedelta(hours=2):
                    add("reabre_e_fecha", r, re_close.author_id or t.author_id, re_close.at, f"reaberta e refechada em {(re_close.at - t.at).total_seconds() / 60:.0f} min")
                    break
        # ---- apropriação
        for at, frm, to in sorted(r.assignee_changes):
            if to and frm and to != frm and fin.at - at <= timedelta(hours=2) and at <= fin.at and fin.author_id == to:
                add("apropriacao", r, to, at, f"{a.name(frm)} → {a.name(to)} {(fin.at - at).total_seconds() / 60:.0f} min antes de concluir")
                break
        # ---- estimativa
        first_active = next((t.at for t in trs if cat.get(t.to_status) == "active"), None)
        for e in (x for x in r.events if x.field == "timeoriginalestimate"):
            try:
                old, new = float(e.from_value or 0), float(e.to_value or 0)
            except ValueError:
                continue
            if first_active and e.at > first_active and old and new >= 1.3 * old:
                add("estimativa_aumentada", r, e.author_id, e.at, f"estimativa {old:g} → {new:g} depois de iniciar")
        # ---- labels
        for e in (x for x in r.events if x.field == "labels"):
            removed = (set((e.from_value or "").split()) - set((e.to_value or "").split())) & CRIT_LABELS
            late = r.due and local(e.at).date() > r.due
            if removed and (late or (r.resolved - e.at) < timedelta(days=3)):
                add("label_removida", r, e.author_id, e.at, f"removida: {', '.join(sorted(removed))}")
        # ---- fechada por outro / último minuto
        if fin.author_id and r.assignee_id and fin.author_id != r.assignee_id and not any(
                c[2] == fin.author_id for c in r.assignee_changes):
            mgr = {s.manager_id for s in a.squads.values()}
            if fin.author_id not in mgr:
                add("fechada_por_outro", r, fin.author_id, fin.at, f"concluída por {a.name(fin.author_id)}; responsável: {a.name(r.assignee_id)}")
        lr = local(r.resolved)
        if r.due and lr.date() == r.due and lr.hour >= 22:
            add("ultimo_minuto", r, who_fin, r.resolved, f"concluída às {lr:%H:%M} do dia do vencimento")
    # ---- lote
    by_author: dict[int, list[tuple[datetime, m.IssueRec]]] = defaultdict(list)
    for r in resolved_in:
        fin = next((t for t in sorted(r.transitions, key=lambda t: t.at, reverse=True) if cat.get(t.to_status) == "done"), None)
        if fin and (fin.author_id or r.assignee_id):
            by_author[fin.author_id or r.assignee_id].append((fin.at, r))
    for pid, lst in by_author.items():
        lst.sort(key=lambda x: x[0])
        i = 0
        while i < len(lst):
            j = i
            while j + 1 < len(lst) and (lst[j + 1][0] - lst[i][0]) <= timedelta(minutes=10):
                j += 1
            if j - i + 1 >= 6:
                for _, r in lst[i:j + 1]:
                    add("fechamento_em_lote", r, pid, lst[i][0], f"{j - i + 1} issues concluídas em {(lst[j][0] - lst[i][0]).total_seconds() / 60:.0f} min")
                i = j + 1
            else:
                i += 1
    return start, end, ev, resolved_in


def integrity_view(a: Analytics) -> dict:
    start, end, ev, resolved_in = integrity_core(a)
    # ---- agregação por pessoa
    denom: Counter = Counter()
    ok_cur: Counter = Counter()
    ok_orig: Counter = Counter()
    n_due: Counter = Counter()
    for r in resolved_in:
        who = r.assignee_id
        denom[who] += 1
        if r.due:
            n_due[who] += 1
            ok_cur[who] += 1 if delta_days(r, r.due) <= 0 else 0
            od = original_due(r)
            ok_orig[who] += 1 if od and delta_days(r, od) <= 0 else 0
    pts: Counter = Counter()
    per: dict[int | None, Counter] = defaultdict(Counter)
    ev_by: dict[int | None, list] = defaultdict(list)
    for e in ev:
        per[e["pessoa_id"]][e["sinal"]] += 1
        pts[e["pessoa_id"]] += e["gravidade"]
        ev_by[e["pessoa_id"]].append(e)
    people = []
    ids = {p for p in list(denom) + list(pts) if p}
    rates = {pid: pts[pid] / max(denom[pid], 8) for pid in ids}
    import statistics
    med = statistics.median(rates.values()) if rates else 0
    base = max(med, 0.05)
    for pid in sorted(ids, key=lambda i: a.name(i)):
        rate = rates[pid]
        ratio = rate / base
        strong = sum(c for s, c in per[pid].items() if SEVERITY[s] >= 3)
        if pts[pid] >= 12 and ratio >= 3 and strong >= 3:
            lvl = "alto"
        elif pts[pid] >= 6 and ratio >= 2:
            lvl = "médio"
        else:
            lvl = "baixo"
        cur = round(100 * ok_cur[pid] / n_due[pid], 1) if n_due[pid] else None
        orig = round(100 * ok_orig[pid] / n_due[pid], 1) if n_due[pid] else None
        people.append({"person_id": pid, "nome": a.name(pid), "concluidas": denom[pid], "pontos": pts[pid], "taxa": round(rate, 2),
                       "vezes_a_mediana": round(ratio, 1), "nivel": lvl, "sinais": dict(per[pid]),
                       "no_prazo_pct": cur, "no_prazo_data_original_pct": orig,
                       "impacto_na_meta_pp": round(cur - orig, 1) if cur is not None and orig is not None else None})
    by_signal = Counter(e["sinal"] for e in ev)
    return {"periodo": {"de": start.isoformat(), "ate": end.isoformat()}, "mediana_taxa": round(med, 2),
            "resumo": {"evidencias": len(ev), "pessoas_alto": sum(1 for p in people if p["nivel"] == "alto"),
                       "pessoas_medio": sum(1 for p in people if p["nivel"] == "médio"),
                       "issues_afetadas": len({e["issue"] for e in ev}),
                       "por_sinal": [{"sinal": s, "nome": SIGNALS[s][0], "qtd": c, "explicacao": SIGNALS[s][1], "gravidade": SEVERITY[s]} for s, c in by_signal.most_common()]},
            "pessoas": people, "evidencias": sorted(ev, key=lambda e: (-e["gravidade"], e["pessoa"]))[:600],
            "catalogo": [{"sinal": k, "nome": v[0], "explicacao": v[1], "gravidade": SEVERITY[k]} for k, v in SIGNALS.items()],
            "regras_nivel": "alto = ≥ 12 pontos, ≥ 3× a mediana da equipe e ≥ 3 sinais graves; médio = ≥ 6 pontos e ≥ 2× a mediana.",
            "aviso": "Indícios para revisão humana, não prova de má-fé. Conferir cada evidência no Jira, ouvir a pessoa e considerar o contexto "
                     "(ex.: prorrogação autorizada pelo cliente). Não use como nota de desempenho."}


# ====================================================================== épicos
def epics_view(a: Analytics) -> dict:
    allr = a.load_all()
    by: dict[str, list[m.IssueRec]] = defaultdict(list)
    for r in allr:
        if r.epic_key and r.type not in a.review_types:
            by[r.epic_key].append(r)
    tp = [n for _, n in a.throughput_series(12)]
    weekly = (sum(tp) / len(tp)) if tp else 0
    rows = []
    for k, rs in by.items():
        done = [r for r in rs if r.category == "done" and not m.is_negative_resolution(r, a.negative)]
        op = [r for r in rs if r.category != "done"]
        pts_t = sum(r.story_points or 0 for r in rs)
        pts_d = sum(r.story_points or 0 for r in done)
        oldest = max((m.age_s(r, a.now, a.cat_of) / m.DAY for r in op), default=0)
        overdue = sum(1 for r in op if r.due and r.due < a.today)
        share = len(rs) / max(1, len(allr))
        eta_w = (len(op) / (weekly * max(share, 0.02))) if weekly and op else 0
        rows.append({"epico": k, "squads": sorted({a.squad_name(r.squad_id) for r in rs}), "itens": len(rs), "concluidos": len(done), "abertos": len(op),
                     "progresso_pct": round(100 * len(done) / len(rs), 1), "pontos": pts_t, "pontos_concluidos": pts_d,
                     "mais_antigo_d": round(oldest, 1), "vencidos": overdue, "previsao_semanas": round(min(eta_w, 52), 1) if op else 0,
                     "responsaveis": len({r.assignee_id for r in op if r.assignee_id})})
    return {"epicos": sorted(rows, key=lambda x: x["epico"]), "nota": "Previsão por épico: abertos ÷ (throughput médio × participação do épico no total). Estimativa grosseira."}


# ====================================================================== central de atenção
def alerts_view(a: Analytics) -> dict:
    """Resumo do que pede ação hoje, cada item com a tela de destino. Reaproveita as demais visões."""
    ov = a.overview()["cards"]
    cap = a.capacity_view()
    q = a.quality()
    ngv = a.neglected()
    ng = dict(ngv["counts"])
    ng["sem_atualizacao"] = len({r["key"] for g in ("sem_atualizacao", "paradas_no_status") for r in ngv["groups"][g]})  # sem dupla contagem
    ng["paradas_no_status"] = 0
    rv = review_view(a)
    ig = integrity_view(a)
    gl = goals_view(a)
    over = [p for p in cap["pessoas"] if p["utilizacao_4s"] > 1.1]
    idle = [p for p in cap["pessoas"] if 0 < p["capacidade_4s_h"] and p["utilizacao_4s"] < 0.5]
    soon = [x for x in cap["ausencias"] if x["inicio"] <= (a.today + timedelta(days=14)).isoformat()]
    est = next((c for c in q["checks"] if c["id"] == "sem_estimativa"), {"pct": 0})
    items = [
        ("Vencidas", ov["vencidas"], "bad", "aberta", "abertas depois do vencimento", ["vencidas"]),
        ("Risco alto", ov["risco_alto"], "bad", "riscos", "issues com pontuação ≥ 60", ["risco","alto"]),
        ("Pessoas acima da capacidade", len(over), "bad", "capacidade", "demanda das próximas 4 semanas > 110%", ["sobrecarregados"]),
        ("Atrasos previstos pela capacidade", len(cap["atrasos_previstos"]), "bad", "capacidade", "terminam depois do vencimento", ["slips"]),
        ("Negligenciadas", ng["sem_atualizacao"], "warn", "negligenciadas", "sem atualização ou paradas no status", ["neglig2"]),
        ("Sem responsável", ng["sem_responsavel"], "warn", "negligenciadas", "abertas sem pessoa atribuída", ["neglig","sem_responsavel"]),
        ("Bloqueadas", ng["bloqueadas"], "warn", "negligenciadas", "status de bloqueio ou impedimento", ["bloqueadas"]),
        ("Ausências nos próximos 14 dias", len(soon), "info", "capacidade", "avalie rebalancear antes", ["ausentes14"]),
        ("Pessoas com folga", len(idle), "info", "distribuicao", "candidatas a receber trabalho", None),
        ("Meta de prazo", None if gl["kpis"]["no_prazo_pct"] is None else f"{gl['kpis']['no_prazo_pct']}%", "bad" if (gl["kpis"]["no_prazo_pct"] or 100) < gl["metas"]["no_prazo_pct"] else "ok", "metas", f"meta {gl['metas']['no_prazo_pct']}%", ["meta","atrasadas"]),
        ("Pares checklist×review com alerta", rv["kpis"]["com_alerta_pct"] if rv["kpis"]["com_alerta_pct"] is None else f"{rv['kpis']['com_alerta_pct']}%", "warn", "review", "vencimento, revisor e fechamento", ["r_pares","com_alerta"]),
        ("Pessoas com indícios de adulteração (alto)", ig["resumo"]["pessoas_alto"], "bad", "integridade", "revisão humana necessária", ["i_alto","alto"]),
        ("Issues sem estimativa", f"{est['pct']}%", "warn" if est["pct"] > 20 else "ok", "qualidade", "enfraquece previsões", ["qualidade","sem_estimativa"]),
    ]
    return {"itens": [{"titulo": t, "valor": v, "tom": (tone if v not in (0, "0%", None) else "ok"), "tela": scr, "detalhe": d, "drill": dr} for t, v, tone, scr, d, dr in items]}
