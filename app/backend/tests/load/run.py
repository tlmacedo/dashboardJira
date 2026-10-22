"""Teste de carga da API real (uvicorn em subprocesso, modo demo). Fora do pytest padrão.

    cd app/backend
    python -m tests.load.run                      # tudo: demo (cache ligado e desligado) + dataset grande
    python -m tests.load.run --skip-big           # só o dataset demo
    python -m tests.load.run --users 1,5,20,50 --duration 20 --big-issues 20000 --out ../docs-testes

Para cada nível de usuários simultâneos (threads com httpx keep-alive, cada um com uma mistura ponderada de endpoints e
filtros) mede RPS, p50/p95/p99 e taxa de erro por endpoint. Grava resultados_carga.md e resultados_carga.json em --out.
O servidor sobe com UM worker (o gargalo medido é o de um processo; em produção use vários workers).
"""
from __future__ import annotations

import argparse
import json
import os
import random
import socket
import statistics
import subprocess
import sys
import tempfile
import threading
import time
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

import httpx

BACKEND = Path(__file__).resolve().parents[2]
ENDPOINTS = [("overview", "/api/overview", 20), ("open", "/api/open", 14), ("closed", "/api/closed", 12), ("negative", "/api/negative", 5),
             ("people", "/api/people", 8), ("capacity", "/api/capacity", 7), ("forecast", "/api/forecast", 6), ("goals", "/api/goals", 6),
             ("review", "/api/review", 5), ("integrity", "/api/integrity", 4), ("bottlenecks", "/api/bottlenecks", 6),
             ("distribution/pending", "/api/distribution/pending", 7)]


def free_port() -> int:
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


class Server:
    def __init__(self, db_path: str | None, cache_ttl: float, workers: int = 1):
        self.port = free_port()
        env = {**os.environ, "JIRADASH_MODE": "demo", "JIRADASH_CACHE_TTL": str(cache_ttl), "PYTHONWARNINGS": "ignore"}
        if db_path:
            env["DATABASE_URL"] = f"sqlite:///{db_path}"
        self.proc = subprocess.Popen([sys.executable, "-m", "uvicorn", "jiradash.main:app", "--host", "127.0.0.1", "--port", str(self.port),
                                      "--workers", str(workers), "--log-level", "error"], cwd=BACKEND, env=env,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.url = f"http://127.0.0.1:{self.port}"

    def wait(self, timeout=180):
        t = time.time()
        while time.time() - t < timeout:
            try:
                if httpx.get(self.url + "/api/health", timeout=2).status_code == 200:
                    return
            except Exception:
                time.sleep(0.3)
        raise RuntimeError("servidor não subiu")

    def stop(self):
        self.proc.terminate()
        try:
            self.proc.wait(10)
        except Exception:
            self.proc.kill()


def pct(v, p):
    if not v:
        return None
    v = sorted(v)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


def summarize(samples: list[tuple[str, float, bool]], dur: float) -> dict:
    by = defaultdict(list)
    err = defaultdict(int)
    for n, lat, ok in samples:
        by[n].append(lat)
        err[n] += (not ok)
    rows = {}
    for n, v in by.items():
        rows[n] = {"n": len(v), "rps": round(len(v) / dur, 2), "p50_ms": round(pct(v, 50) * 1000), "p95_ms": round(pct(v, 95) * 1000),
                   "p99_ms": round(pct(v, 99) * 1000), "max_ms": round(max(v) * 1000), "erros": err[n], "erro_pct": round(100 * err[n] / len(v), 1)}
    allv = [s[1] for s in samples]
    tot = {"n": len(allv), "rps": round(len(allv) / dur, 2), "p50_ms": round(pct(allv, 50) * 1000) if allv else None,
           "p95_ms": round(pct(allv, 95) * 1000) if allv else None, "p99_ms": round(pct(allv, 99) * 1000) if allv else None,
           "erros": sum(err.values()), "erro_pct": round(100 * sum(err.values()) / max(1, len(allv)), 1)}
    return {"total": tot, "endpoints": dict(sorted(rows.items()))}


def variantes(meta: dict, rng: random.Random) -> list[dict]:
    hoje = date.today()
    sq = [s["key"] for s in meta["squads"]]
    pj = [p["key"] if isinstance(p, dict) else p for p in meta["projects"]]
    pe = [p["id"] for p in meta["people"] if p.get("role") != "gerente"][:12]
    return [{}, {}, {"squad": rng.choice(sq)}, {"squad": rng.choice(sq)}, {"project": rng.choice(pj)},
            {"person": rng.choice(pe)}, {"type": "Bug"}, {"priority": "High"},
            {"date_from": (hoje - timedelta(days=60)).isoformat(), "date_to": hoje.isoformat()},
            {"squad": rng.choice(sq), "date_from": (hoje - timedelta(days=14)).isoformat()}]


def user_loop(url: str, deadline: float, seed: int, meta: dict, out: list, timeout: float):
    rng = random.Random(seed)
    var = variantes(meta, rng)
    names = [(n, p) for n, p, _ in ENDPOINTS]
    weights = [w for _, _, w in ENDPOINTS]
    mine = []
    with httpx.Client(base_url=url, timeout=timeout) as c:
        while time.time() < deadline:
            (n, p), = rng.choices(names, weights)
            params = rng.choice(var)
            t = time.perf_counter()
            try:
                ok = c.get(p, params=params).status_code == 200
            except Exception:
                ok = False
            mine.append((n, time.perf_counter() - t, ok))
    out.extend(mine)


def run_level(srv: Server, users: int, duration: float, meta: dict, timeout=120) -> dict:
    out: list = []
    deadline = time.time() + duration
    th = [threading.Thread(target=user_loop, args=(srv.url, deadline, 1000 + i, meta, out, timeout)) for i in range(users)]
    t0 = time.time()
    [t.start() for t in th]
    [t.join() for t in th]
    return summarize(out, time.time() - t0)


def warmup(srv: Server, meta: dict):
    with httpx.Client(base_url=srv.url, timeout=300) as c:
        for _, p, _ in ENDPOINTS:
            c.get(p)


def sequencial(srv: Server, meta: dict, reps=2) -> dict:
    """Latência de cada endpoint, uma requisição por vez, com cada variante de filtro (cache deve estar desligado)."""
    rng = random.Random(7)
    var = variantes(meta, rng)[::2] + [variantes(meta, rng)[8]]
    res = {}
    with httpx.Client(base_url=srv.url, timeout=600) as c:
        for n, p, _ in ENDPOINTS:
            lats, err = [], 0
            for params in var:
                for _ in range(reps):
                    t = time.perf_counter()
                    try:
                        r = c.get(p, params=params)
                        err += r.status_code != 200
                    except Exception:
                        err += 1
                    lats.append(time.perf_counter() - t)
            res[n] = {"n": len(lats), "p50_ms": round(pct(lats, 50) * 1000), "p95_ms": round(pct(lats, 95) * 1000),
                      "max_ms": round(max(lats) * 1000), "erros": err}
    return res


def cenario(nome: str, db: str | None, ttl: float, users: list[int], duration: float, seq: bool) -> dict:
    print(f"== cenário: {nome} (cache TTL={ttl})", flush=True)
    srv = Server(db, ttl)
    try:
        srv.wait()
        meta = httpx.get(srv.url + "/api/meta", timeout=60).json()
        t = time.time()
        warmup(srv, meta)
        out = {"nome": nome, "cache_ttl": ttl, "warmup_s": round(time.time() - t, 1), "issues": None, "niveis": {}}
        if seq:
            out["sequencial"] = sequencial(srv, meta)
            print("   sequencial ok", flush=True)
        for u in users:
            out["niveis"][str(u)] = run_level(srv, u, duration, meta)
            tt = out["niveis"][str(u)]["total"]
            print(f"   {u:>3} usuários: {tt['rps']} rps  p50={tt['p50_ms']}ms p95={tt['p95_ms']}ms erros={tt['erro_pct']}%", flush=True)
        return out
    finally:
        srv.stop()


def markdown(res: dict) -> str:
    L = ["# Resultados dos testes de carga", "",
         f"Gerado por `python -m tests.load.run` em {res['quando']}. Máquina: {res['maquina']}. Servidor: uvicorn, 1 worker, SQLite, "
         "cliente e servidor na mesma máquina (o cliente também consome CPU).", ""]
    for sc in res["cenarios"]:
        L += [f"## {sc['nome']}", "", f"Cache de payloads TTL = {sc['cache_ttl']} s; warm-up de todos os endpoints: {sc['warmup_s']} s. "
              + (f"Dataset: {sc['dataset']}" if sc.get("dataset") else ""), ""]
        if sc.get("sequencial"):
            L += ["### Latência sequencial por endpoint (1 requisição por vez, filtros variados, sem cache)", "",
                  "| Endpoint | n | p50 (ms) | p95 (ms) | máx (ms) | erros | > 1 s? |", "|---|--:|--:|--:|--:|--:|:--:|"]
            for n, r in sc["sequencial"].items():
                L.append(f"| {n} | {r['n']} | {r['p50_ms']} | {r['p95_ms']} | {r['max_ms']} | {r['erros']} | {'SIM' if r['p50_ms'] > 1000 else ''} |")
            L.append("")
        L += ["### Visão por nível de concorrência", "", "| Usuários | requisições | RPS | p50 (ms) | p95 (ms) | p99 (ms) | erros (%) |", "|--:|--:|--:|--:|--:|--:|--:|"]
        for u, r in sc["niveis"].items():
            t = r["total"]
            L.append(f"| {u} | {t['n']} | {t['rps']} | {t['p50_ms']} | {t['p95_ms']} | {t['p99_ms']} | {t['erro_pct']} |")
        L.append("")
        for u, r in sc["niveis"].items():
            L += [f"#### {u} usuário(s) simultâneo(s), por endpoint", "", "| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |", "|---|--:|--:|--:|--:|--:|--:|--:|"]
            for n, e in r["endpoints"].items():
                L.append(f"| {n} | {e['n']} | {e['rps']} | {e['p50_ms']} | {e['p95_ms']} | {e['p99_ms']} | {e['max_ms']} | {e['erro_pct']} |")
            L.append("")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--users", default="1,5,20,50")
    ap.add_argument("--duration", type=float, default=20, help="segundos por nível (demo)")
    ap.add_argument("--big-duration", type=float, default=30)
    ap.add_argument("--big-users", default="1,5")
    ap.add_argument("--big-issues", type=int, default=20000)
    ap.add_argument("--skip-big", action="store_true")
    ap.add_argument("--out", default=str(BACKEND.parent / "docs-testes"))
    a = ap.parse_args()
    users = [int(x) for x in a.users.split(",")]
    res = {"quando": time.strftime("%Y-%m-%d %H:%M"), "maquina": f"{os.cpu_count()} vCPU, Python {sys.version.split()[0]}", "cenarios": []}
    tmp = Path(tempfile.mkdtemp(prefix="jiradash_load_"))
    # demo: banco novo temporário (nunca o data/demo.db)
    for nome, ttl in (("Demo (~900 issues), cache ligado (TTL 60 s, padrão)", 60), ("Demo (~900 issues), cache desligado (TTL 0)", 0)):
        sc = cenario(nome, str(tmp / f"demo_{int(ttl)}.db"), ttl, users, a.duration, seq=(ttl == 0))
        sc["dataset"] = "demo do próprio produto"
        res["cenarios"].append(sc)
    if not a.skip_big:
        from tests.load.gen_big import generate
        info = generate(str(tmp / "big.db"), a.big_issues)
        print("dataset grande:", info, flush=True)
        sc = cenario(f"Dataset grande ({info['issues']} issues), cache desligado", str(tmp / "big.db"), 0,
                     [int(x) for x in a.big_users.split(",")], a.big_duration, seq=True)
        sc["dataset"] = (f"{info['issues']} issues, {info['transicoes']} transições, {info['eventos']} eventos, "
                         f"{info['worklogs']} worklogs, {info['abertas']} abertas.")
        res["dataset_grande"] = info
        res["cenarios"].append(sc)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "resultados_carga.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
    (out / "resultados_carga.md").write_text(markdown(res), encoding="utf-8")
    print("gravado em", out)


if __name__ == "__main__":
    main()
