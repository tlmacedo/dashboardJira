# Resultados dos testes de carga

Gerado por `python -m tests.load.run` em 2026-10-22 05:13. Máquina: 4 vCPU, Python 3.13.16. Servidor: uvicorn, 1 worker, SQLite, cliente e servidor na mesma máquina (o cliente também consome CPU).

## Demo (~900 issues), cache ligado (TTL 60 s, padrão)

Cache de payloads TTL = 60 s; warm-up de todos os endpoints: 6.8 s. Dataset: demo do próprio produto

### Visão por nível de concorrência

| Usuários | requisições | RPS | p50 (ms) | p95 (ms) | p99 (ms) | erros (%) |
|--:|--:|--:|--:|--:|--:|--:|
| 1 | 102 | 5.03 | 110 | 513 | 1026 | 0.0 |
| 5 | 52 | 2.41 | 111 | 10315 | 10860 | 0.0 |
| 20 | 54 | 0.78 | 650 | 64651 | 65262 | 18.5 |
| 50 | 53 | 0.63 | 31108 | 79703 | 80488 | 54.7 |

#### 1 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 2 | 0.1 | 27 | 348 | 348 | 348 | 0.0 |
| capacity | 7 | 0.35 | 20 | 300 | 300 | 300 | 0.0 |
| closed | 7 | 0.35 | 111 | 513 | 513 | 513 | 0.0 |
| distribution/pending | 11 | 0.54 | 316 | 421 | 421 | 421 | 0.0 |
| forecast | 6 | 0.3 | 18 | 422 | 422 | 422 | 0.0 |
| goals | 7 | 0.35 | 88 | 492 | 492 | 492 | 0.0 |
| integrity | 3 | 0.15 | 123 | 382 | 382 | 382 | 0.0 |
| negative | 6 | 0.3 | 139 | 483 | 483 | 483 | 0.0 |
| open | 17 | 0.84 | 24 | 312 | 402 | 402 | 0.0 |
| overview | 22 | 1.09 | 23 | 1026 | 2088 | 2088 | 0.0 |
| people | 7 | 0.35 | 137 | 458 | 458 | 458 | 0.0 |
| review | 7 | 0.35 | 115 | 411 | 411 | 411 | 0.0 |

#### 5 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 2 | 0.09 | 51 | 4595 | 4595 | 4595 | 0.0 |
| capacity | 4 | 0.19 | 4932 | 10610 | 10610 | 10610 | 0.0 |
| closed | 6 | 0.28 | 73 | 4951 | 4951 | 4951 | 0.0 |
| distribution/pending | 3 | 0.14 | 5600 | 10315 | 10315 | 10315 | 0.0 |
| forecast | 5 | 0.23 | 119 | 3897 | 3897 | 3897 | 0.0 |
| goals | 2 | 0.09 | 38 | 88 | 88 | 88 | 0.0 |
| integrity | 1 | 0.05 | 12329 | 12329 | 12329 | 12329 | 0.0 |
| negative | 5 | 0.23 | 127 | 10102 | 10102 | 10102 | 0.0 |
| open | 8 | 0.37 | 66 | 4270 | 4270 | 4270 | 0.0 |
| overview | 10 | 0.46 | 60 | 10860 | 10860 | 10860 | 0.0 |
| people | 3 | 0.14 | 111 | 123 | 123 | 123 | 0.0 |
| review | 3 | 0.14 | 111 | 5062 | 5062 | 5062 | 0.0 |

#### 20 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 2 | 0.03 | 499 | 19203 | 19203 | 19203 | 0.0 |
| capacity | 8 | 0.12 | 846 | 65506 | 65506 | 65506 | 25.0 |
| closed | 7 | 0.1 | 576 | 59807 | 59807 | 59807 | 0.0 |
| distribution/pending | 3 | 0.04 | 41804 | 47450 | 47450 | 47450 | 66.7 |
| forecast | 7 | 0.1 | 13484 | 64651 | 64651 | 64651 | 14.3 |
| goals | 3 | 0.04 | 703 | 21066 | 21066 | 21066 | 0.0 |
| integrity | 1 | 0.01 | 21806 | 21806 | 21806 | 21806 | 100.0 |
| negative | 3 | 0.04 | 396 | 625 | 625 | 625 | 0.0 |
| open | 6 | 0.09 | 31472 | 64737 | 64737 | 64737 | 50.0 |
| overview | 7 | 0.1 | 456 | 53185 | 53185 | 53185 | 14.3 |
| people | 3 | 0.04 | 649 | 31920 | 31920 | 31920 | 0.0 |
| review | 4 | 0.06 | 564 | 650 | 650 | 650 | 0.0 |

#### 50 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 3 | 0.04 | 31186 | 80488 | 80488 | 80488 | 66.7 |
| capacity | 5 | 0.06 | 64457 | 80315 | 80315 | 80315 | 20.0 |
| closed | 9 | 0.11 | 31263 | 73612 | 73612 | 73612 | 55.6 |
| distribution/pending | 1 | 0.01 | 31120 | 31120 | 31120 | 31120 | 100.0 |
| forecast | 6 | 0.07 | 30602 | 62459 | 62459 | 62459 | 50.0 |
| goals | 3 | 0.04 | 55778 | 65251 | 65251 | 65251 | 33.3 |
| integrity | 3 | 0.04 | 30761 | 30839 | 30839 | 30839 | 100.0 |
| negative | 2 | 0.02 | 61946 | 66848 | 66848 | 66848 | 0.0 |
| open | 6 | 0.07 | 30711 | 59001 | 59001 | 59001 | 83.3 |
| overview | 9 | 0.11 | 30803 | 81237 | 81237 | 81237 | 66.7 |
| people | 2 | 0.02 | 9625 | 64551 | 64551 | 64551 | 0.0 |
| review | 4 | 0.05 | 74763 | 79703 | 79703 | 79703 | 50.0 |

## Demo (~900 issues), cache desligado (TTL 0)

Cache de payloads TTL = 0 s; warm-up de todos os endpoints: 6.2 s. Dataset: demo do próprio produto

### Latência sequencial por endpoint (1 requisição por vez, filtros variados, sem cache)

| Endpoint | n | p50 (ms) | p95 (ms) | máx (ms) | erros | timeouts | > 1 s? |
|---|--:|--:|--:|--:|--:|--:|:--:|
| overview | 12 | 1910 | 2064 | 2072 | 0 | 0 | SIM |
| open | 12 | 360 | 471 | 491 | 0 | 0 |  |
| closed | 12 | 405 | 509 | 533 | 0 | 0 |  |
| negative | 12 | 346 | 476 | 497 | 0 | 0 |  |
| people | 12 | 327 | 467 | 501 | 0 | 0 |  |
| capacity | 12 | 305 | 437 | 447 | 0 | 0 |  |
| forecast | 12 | 357 | 483 | 489 | 0 | 0 |  |
| goals | 12 | 319 | 414 | 419 | 0 | 0 |  |
| review | 12 | 322 | 450 | 466 | 0 | 0 |  |
| integrity | 12 | 117 | 373 | 419 | 4 | 0 |  |
| bottlenecks | 12 | 384 | 528 | 588 | 0 | 0 |  |
| distribution/pending | 12 | 341 | 432 | 462 | 0 | 0 |  |

### Visão por nível de concorrência

| Usuários | requisições | RPS | p50 (ms) | p95 (ms) | p99 (ms) | erros (%) |
|--:|--:|--:|--:|--:|--:|--:|
| 1 | 52 | 2.55 | 362 | 779 | 1070 | 0.0 |
| 5 | 11 | 0.43 | 11267 | 24072 | 24072 | 0.0 |
| 20 | 20 | 0.27 | 67269 | 71843 | 72291 | 15.0 |
| 50 | 55 | 0.63 | 31558 | 79148 | 83162 | 52.7 |

#### 1 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 2 | 0.1 | 357 | 415 | 415 | 415 | 0.0 |
| capacity | 2 | 0.1 | 313 | 435 | 435 | 435 | 0.0 |
| closed | 3 | 0.15 | 131 | 410 | 410 | 410 | 0.0 |
| distribution/pending | 5 | 0.24 | 308 | 390 | 390 | 390 | 0.0 |
| forecast | 3 | 0.15 | 362 | 539 | 539 | 539 | 0.0 |
| goals | 3 | 0.15 | 298 | 471 | 471 | 471 | 0.0 |
| integrity | 1 | 0.05 | 278 | 278 | 278 | 278 | 0.0 |
| negative | 4 | 0.2 | 297 | 492 | 492 | 492 | 0.0 |
| open | 10 | 0.49 | 295 | 482 | 482 | 482 | 0.0 |
| overview | 12 | 0.59 | 549 | 1070 | 2142 | 2142 | 0.0 |
| people | 5 | 0.24 | 324 | 381 | 381 | 381 | 0.0 |
| review | 2 | 0.1 | 116 | 404 | 404 | 404 | 0.0 |

#### 5 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| capacity | 2 | 0.08 | 5122 | 8868 | 8868 | 8868 | 0.0 |
| closed | 2 | 0.08 | 7556 | 12687 | 12687 | 12687 | 0.0 |
| forecast | 1 | 0.04 | 11917 | 11917 | 11917 | 11917 | 0.0 |
| goals | 1 | 0.04 | 3954 | 3954 | 3954 | 3954 | 0.0 |
| negative | 1 | 0.04 | 11267 | 11267 | 11267 | 11267 | 0.0 |
| overview | 2 | 0.08 | 6357 | 24072 | 24072 | 24072 | 0.0 |
| people | 1 | 0.04 | 14024 | 14024 | 14024 | 14024 | 0.0 |
| review | 1 | 0.04 | 12263 | 12263 | 12263 | 12263 | 0.0 |

#### 20 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| capacity | 3 | 0.04 | 67269 | 70572 | 70572 | 70572 | 0.0 |
| closed | 4 | 0.05 | 71216 | 71843 | 71843 | 71843 | 50.0 |
| forecast | 3 | 0.04 | 31069 | 68964 | 68964 | 68964 | 0.0 |
| goals | 2 | 0.03 | 30463 | 70118 | 70118 | 70118 | 50.0 |
| negative | 1 | 0.01 | 64882 | 64882 | 64882 | 64882 | 0.0 |
| open | 1 | 0.01 | 30961 | 30961 | 30961 | 30961 | 0.0 |
| overview | 3 | 0.04 | 71392 | 72291 | 72291 | 72291 | 0.0 |
| people | 1 | 0.01 | 27783 | 27783 | 27783 | 27783 | 0.0 |
| review | 2 | 0.03 | 68878 | 69480 | 69480 | 69480 | 0.0 |

#### 50 usuário(s) simultâneo(s), por endpoint

| Endpoint | n | RPS | p50 | p95 | p99 | máx | erros (%) |
|---|--:|--:|--:|--:|--:|--:|--:|
| bottlenecks | 3 | 0.03 | 31488 | 31638 | 31638 | 31638 | 66.7 |
| capacity | 4 | 0.05 | 42399 | 63812 | 63812 | 63812 | 25.0 |
| closed | 11 | 0.13 | 31600 | 77848 | 77848 | 77848 | 63.6 |
| distribution/pending | 1 | 0.01 | 31444 | 31444 | 31444 | 31444 | 100.0 |
| forecast | 6 | 0.07 | 31582 | 64474 | 64474 | 64474 | 50.0 |
| goals | 3 | 0.03 | 31534 | 79148 | 79148 | 79148 | 33.3 |
| integrity | 3 | 0.03 | 31099 | 31548 | 31548 | 31548 | 100.0 |
| negative | 2 | 0.02 | 2087 | 57744 | 57744 | 57744 | 0.0 |
| open | 7 | 0.08 | 31308 | 80787 | 80787 | 80787 | 57.1 |
| overview | 9 | 0.1 | 31618 | 83335 | 83335 | 83335 | 55.6 |
| people | 1 | 0.01 | 9528 | 9528 | 9528 | 9528 | 0.0 |
| review | 5 | 0.06 | 44614 | 76256 | 76256 | 76256 | 40.0 |

## Dataset grande (22012 issues), cache desligado

Cache de payloads TTL = 0 s; warm-up de todos os endpoints: 0.0 s. Dataset: 22012 issues, 104525 transições, 47609 eventos, 42601 worklogs, 2068 abertas, 107 pessoas extras + 9 do YAML.

### Latência sequencial por endpoint (1 requisição por vez, filtros variados, sem cache)

| Endpoint | n | p50 (ms) | p95 (ms) | máx (ms) | erros | timeouts | > 1 s? |
|---|--:|--:|--:|--:|--:|--:|:--:|
| overview | 0 | None | None | None | 0 | 1 (> 240 s) | SIM |
| open | 2 | 7001 | 31175 | 31175 | 0 | 0 | SIM |
| closed | 2 | 5827 | 23602 | 23602 | 0 | 0 | SIM |
| negative | 2 | 6774 | 23099 | 23099 | 0 | 0 | SIM |
| people | 2 | 6270 | 22557 | 22557 | 0 | 0 | SIM |
| capacity | 2 | 5898 | 24034 | 24034 | 0 | 0 | SIM |
| forecast | 2 | 6935 | 22745 | 22745 | 0 | 0 | SIM |
| goals | 2 | 7343 | 24461 | 24461 | 0 | 0 | SIM |
| review | 2 | 6101 | 22161 | 22161 | 0 | 0 | SIM |
| integrity | 2 | 6422 | 20989 | 20989 | 0 | 0 | SIM |
| bottlenecks | 2 | 7163 | 23609 | 23609 | 0 | 0 | SIM |
| distribution/pending | 2 | 5627 | 16907 | 16907 | 0 | 0 | SIM |

Dataset grande: um servidor novo por endpoint, 2 requisições (sem filtro e com squad); como n=2, o p50 é a mais rápida (com squad) e p95/máx é a sem filtro.

### Visão por nível de concorrência

| Usuários | requisições | RPS | p50 (ms) | p95 (ms) | p99 (ms) | erros (%) |
|--:|--:|--:|--:|--:|--:|--:|

---

## Reexecução após otimizações (dataset grande, 22.012 issues, cache desligado, 1 usuário)

Mudanças aplicadas depois do primeiro relatório: `selectinload` no lugar de três `joinedload` (sem produto cartesiano), janela de histórico (`history_days`, padrão 270 dias), memoização por requisição de `status_median_days`, `cycle_hist_days` e `plan`, e cache de payloads com "single-flight" (quem chega com cache frio espera o primeiro calcular).

| Endpoint | Antes (p50) | Depois (p50) | Depois (1ª chamada) |
|---|--:|--:|--:|
| overview | não terminou em 240 s | 6,1 s | 25,1 s |
| open | 17-31 s | 3,4 s | 13,5 s |
| bottlenecks | 17-31 s | 3,7 s | 10,6 s |
| people | 17-31 s | 2,8 s | 9,8 s |
| capacity | 17-31 s | 2,3 s | 8,0 s |
| forecast | 17-31 s | 2,7 s | 8,9 s |
| closed / negative / review / goals / integrity | 17-31 s | 2,4-2,9 s | 8,6-9,0 s |
| distribution/pending | 17-31 s | 2,2 s | 7,0 s |

Leitura honesta: melhorou uma ordem de grandeza, mas **com 22 mil issues e 107 pessoas ainda é lento sem cache** (primeira chamada de 7 a 25 s). Com o cache de 60 s as chamadas seguintes são imediatas, e com ~900 issues (demo) as telas levaram de dezenas a centenas de ms, e no máximo ~2 s (overview) sem cache; entre 900 e 22 mil issues o tempo cresce de forma aproximadamente linear, sem medição intermediária. Para volumes grandes: PostgreSQL, vários workers (`uvicorn --workers 4`), `JIRADASH_CACHE_TTL` maior e, se necessário, pré-aquecer o cache após cada sincronização. A concorrência no volume grande não foi remedida depois das mudanças.
