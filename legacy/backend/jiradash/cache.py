"""Cache em memória com TTL para os payloads das telas. Invalida em qualquer escrita (sync, decisões)."""
from __future__ import annotations

import os
import threading
import time

TTL = float(os.getenv("JIRADASH_CACHE_TTL", "60"))
_lock = threading.Lock()
_store: dict[tuple, tuple[float, object]] = {}
_gen = 0
_inflight: dict[tuple, threading.Lock] = {}


def get_or_compute(key: tuple, fn):
    if TTL <= 0:
        return fn()
    now = time.time()
    k = (_gen, *key)
    with _lock:
        hit = _store.get(k)
        if hit and now - hit[0] < TTL:
            return hit[1]
    # "single-flight": quem chega com o cache frio espera o primeiro calcular em vez de recalcular em paralelo
    with _lock:
        flight = _inflight.setdefault(k, threading.Lock())
    with flight:
        with _lock:
            hit = _store.get(k)
            if hit and time.time() - hit[0] < TTL:
                return hit[1]
        val = fn()
        with _lock:
            if len(_store) > 500:
                _store.clear()
            _store[k] = (time.time(), val)
            _inflight.pop(k, None)
    return val


def invalidate() -> None:
    global _gen
    with _lock:
        _gen += 1
        _store.clear()
