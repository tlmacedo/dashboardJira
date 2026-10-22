"""Motor de distribuição: módulo puro, determinístico e testável (ver relatório, seção de distribuição).

Princípios:
- sugere e o gerente aprova; nunca escreve no Jira por conta própria;
- justiça = carga normalizada pela capacidade (não contagem bruta);
- produtividade entra só como peso de capacidade, suavizado e com teto (não pune quem entrega mais);
- cada sugestão traz os fatores do score, para explicabilidade e auditoria.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from ..metrics.core import jain_index

ENGINE_VERSION = "1"

W_LOAD = 1.0       # carga projetada ÷ capacidade do horizonte
W_WIP = 0.5        # penalidade proporcional ao WIP atual
W_WIP_OVER = 1.0   # penalidade fixa por estourar o limite de WIP
W_SKILL_MISS = 0.6 # por competência exigida que falta
W_AFFINITY = 0.15  # bônus por já ter trabalhado no mesmo épico/componente
CAP_WEIGHT_MIN, CAP_WEIGHT_MAX = 0.85, 1.15  # teto/piso do peso de produtividade


@dataclass
class Candidate:
    person_id: int
    name: str
    capacity_h: float          # capacidade líquida no horizonte (já desconta ausências/feriados)
    load_h: float              # horas já comprometidas no horizonte
    wip: int                   # itens em andamento
    wip_limit: int = 3
    skills: set[str] = field(default_factory=set)
    affinity_keys: set[str] = field(default_factory=set)  # épicos/componentes já trabalhados
    throughput_share: float = 1.0  # throughput relativo (1.0 = média do squad), suavizado antes de chegar aqui
    last_assigned_rank: int = 0    # menor = atribuído há mais tempo (desempate round-robin)


@dataclass
class NewWork:
    key: str
    hours: float
    skills_required: set[str] = field(default_factory=set)
    affinity_keys: set[str] = field(default_factory=set)


def capacity_weight(throughput_share: float) -> float:
    return max(CAP_WEIGHT_MIN, min(CAP_WEIGHT_MAX, throughput_share))


def score(c: Candidate, w: NewWork) -> dict:
    eff_cap = max(0.1, c.capacity_h * capacity_weight(c.throughput_share))
    f_load = W_LOAD * (c.load_h + w.hours) / eff_cap
    f_wip = W_WIP * (c.wip / max(1, c.wip_limit)) + (W_WIP_OVER if c.wip >= c.wip_limit else 0.0)
    missing = sorted(w.skills_required - c.skills)
    f_skill = W_SKILL_MISS * len(missing)
    hit = sorted(w.affinity_keys & c.affinity_keys)
    f_aff = -W_AFFINITY if hit else 0.0
    total = f_load + f_wip + f_skill + f_aff
    return {
        "person_id": c.person_id, "name": c.name, "score": round(total, 3),
        "factors": {
            "carga_projetada": round(f_load, 3), "wip": round(f_wip, 3),
            "competencia_faltante": round(f_skill, 3), "afinidade": round(f_aff, 3),
        },
        "detail": {
            "capacidade_h": round(c.capacity_h, 1), "carga_h": round(c.load_h, 1),
            "peso_capacidade": round(capacity_weight(c.throughput_share), 2),
            "wip": c.wip, "limite_wip": c.wip_limit,
            "competencias_faltando": missing, "afinidade_com": hit,
        },
        "eligible": c.capacity_h > 0.5,
    }


def rank(cands: list[Candidate], w: NewWork) -> list[dict]:
    rows = [score(c, w) for c in cands]
    order = {c.person_id: c.last_assigned_rank for c in cands}
    rows.sort(key=lambda r: (not r["eligible"], r["score"], order[r["person_id"]], r["person_id"]))
    return rows


def distribute(cands: list[Candidate], works: list[NewWork]) -> list[dict]:
    """Guloso com releitura de carga: cada atribuição atualiza a carga antes da próxima.

    Itens maiores primeiro (reduz o desequilíbrio final); a ordem de saída segue a entrada.
    """
    state = {c.person_id: Candidate(**{**c.__dict__}) for c in cands}
    results: dict[str, dict] = {}
    for w in sorted(works, key=lambda x: -x.hours):
        ranking = rank(list(state.values()), w)
        if not ranking or not ranking[0]["eligible"]:
            results[w.key] = {"issue_key": w.key, "suggested": None, "ranking": ranking,
                              "reason": "ninguém com capacidade disponível no horizonte"}
            continue
        best = ranking[0]
        chosen = state[best["person_id"]]
        chosen.load_h += w.hours
        chosen.wip += 0  # novo item entra em 'a fazer'; WIP muda só ao iniciar
        chosen.last_assigned_rank = max(s.last_assigned_rank for s in state.values()) + 1
        results[w.key] = {"issue_key": w.key, "suggested": best["person_id"], "ranking": ranking,
                          "reason": _reason(best, ranking)}
    return [results[w.key] for w in works]


def _reason(best: dict, ranking: list[dict]) -> str:
    d, f = best["detail"], best["factors"]
    util = (d["carga_h"] / d["capacidade_h"]) if d["capacidade_h"] else 0
    parts = [f"menor score ({best['score']:.2f})", f"carga atual {d['carga_h']:.0f} h de {d['capacidade_h']:.0f} h ({util:.0%})"]
    if d["afinidade_com"]:
        parts.append(f"afinidade com {', '.join(d['afinidade_com'])}")
    if len(ranking) > 1:
        parts.append(f"2º colocado: {ranking[1]['name']} ({ranking[1]['score']:.2f})")
    return "; ".join(parts)


def fairness(cands: list[Candidate]) -> dict:
    """Equidade da carga relativa (carga ÷ capacidade) pelo índice de Jain, e dispersão."""
    ratios = [c.load_h / c.capacity_h for c in cands if c.capacity_h > 0.5]
    if not ratios:
        return {"jain": None, "max": None, "min": None}
    return {"jain": jain_index(ratios), "max": round(max(ratios), 2), "min": round(min(ratios), 2)}


def rebalance(cands: list[Candidate], movable: list[dict], high: float = 1.1, target: float = 1.0,
              hysteresis: float = 0.2, max_moves: int = 10) -> list[dict]:
    """Move o mínimo de issues não iniciadas de quem está acima de `high` para quem tem folga.

    movable: [{key, hours, owner_id, skills_required, affinity_keys}], só itens ainda não iniciados.
    Histerese: só move se a diferença de utilização entre origem e destino superar `hysteresis`.
    """
    state = {c.person_id: Candidate(**{**c.__dict__}) for c in cands}
    moves: list[dict] = []

    def util(c: Candidate) -> float:
        return c.load_h / c.capacity_h if c.capacity_h > 0.5 else (9.9 if c.load_h > 0 else 0.0)

    for _ in range(max_moves):
        over = [c for c in state.values() if util(c) > high]
        if not over:
            break
        src = max(over, key=util)
        pool = sorted((m for m in movable if m["owner_id"] == src.person_id and not m.get("_moved")),
                      key=lambda m: -m["hours"])
        done = False
        for m in pool:
            others = [c for c in state.values() if c.person_id != src.person_id]
            w = NewWork(m["key"], m["hours"], set(m.get("skills_required", [])), set(m.get("affinity_keys", [])))
            ranking = [r for r in rank(others, w) if r["eligible"] and not r["detail"]["competencias_faltando"]]
            if not ranking:
                continue
            dst = state[ranking[0]["person_id"]]
            if util(src) - (dst.load_h + m["hours"]) / max(dst.capacity_h, 0.1) < hysteresis:
                continue
            src.load_h -= m["hours"]
            dst.load_h += m["hours"]
            m["_moved"] = True
            moves.append({
                "issue_key": m["key"], "from_person_id": src.person_id, "to_person_id": dst.person_id,
                "hours": m["hours"], "ranking": ranking,
                "reason": f"{src.name} projetado em {util(src)+m['hours']/max(src.capacity_h,0.1):.0%} da capacidade; "
                          f"{dst.name} fica em {util(dst):.0%} após o movimento",
            })
            done = True
            break
        if not done:
            break
        if util(src) <= target:
            continue
    return moves
