from jiradash.distribution import engine as eng


def cand(pid, cap=100, load=0, wip=0, skills=(), aff=(), share=1.0, rank=0):
    return eng.Candidate(pid, f"P{pid}", cap, load, wip, 3, set(skills), set(aff), share, rank)


def test_escolhe_menor_carga_relativa():
    r = eng.distribute([cand(1, 100, 80), cand(2, 100, 20), cand(3, 50, 20)], [eng.NewWork("K-1", 10)])
    assert r[0]["suggested"] == 2


def test_distribui_igualitariamente_sem_concentrar():
    cands = [cand(i, 100, 0, rank=i) for i in range(4)]
    works = [eng.NewWork(f"K-{k}", 10) for k in range(8)]
    out = eng.distribute(cands, works)
    cnt = {}
    for o in out:
        cnt[o["suggested"]] = cnt.get(o["suggested"], 0) + 1
    assert set(cnt.values()) == {2}


def test_ausencia_total_exclui_candidato():
    r = eng.distribute([cand(1, 0, 0), cand(2, 100, 90)], [eng.NewWork("K-1", 5)])
    assert r[0]["suggested"] == 2


def test_competencia_e_afinidade_pesam():
    cands = [cand(1, 100, 30, skills=["pix"]), cand(2, 100, 20)]
    r = eng.distribute(cands, [eng.NewWork("K-1", 5, skills_required={"pix"})])
    assert r[0]["suggested"] == 1
    cands = [cand(1, 100, 25, aff=["E-1"]), cand(2, 100, 20)]
    r = eng.distribute(cands, [eng.NewWork("K-1", 5, affinity_keys={"E-1"})])
    assert r[0]["suggested"] == 1


def test_produtividade_tem_teto_e_nao_pune_quem_entrega_mais():
    assert eng.capacity_weight(3.0) == eng.CAP_WEIGHT_MAX
    assert eng.capacity_weight(0.1) == eng.CAP_WEIGHT_MIN
    # quem entrega 3x mais não recebe mais de ~15% de capacidade extra
    r = eng.rank([cand(1, 100, 50, share=3.0), cand(2, 100, 50, share=1.0)], eng.NewWork("K", 10))
    assert r[0]["person_id"] == 1 and r[0]["score"] > 0.5 * r[1]["score"]


def test_rebalanceamento_move_o_minimo_e_respeita_competencia():
    cands = [cand(1, 40, 80), cand(2, 40, 10), cand(3, 40, 10, skills=["x"])]
    movable = [{"key": f"K-{i}", "hours": 10, "owner_id": 1, "skills_required": [], "affinity_keys": []} for i in range(6)]
    moves = eng.rebalance(cands, movable)
    assert 0 < len(moves) <= 4 and all(m["from_person_id"] == 1 for m in moves)
    cands = [cand(1, 40, 80), cand(2, 40, 10)]
    only_x = [{"key": "K-9", "hours": 40, "owner_id": 1, "skills_required": ["x"], "affinity_keys": []}]
    assert eng.rebalance(cands, only_x) == []


def test_histerese_evita_troca_sem_ganho():
    cands = [cand(1, 40, 46), cand(2, 40, 44)]
    movable = [{"key": "K-1", "hours": 2, "owner_id": 1}]
    assert eng.rebalance(cands, movable) == []
