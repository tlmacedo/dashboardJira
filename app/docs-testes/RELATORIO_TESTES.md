# Relatório de testes: integração com o Jira (simulado), filtros e carga

Data da execução: 2026-10-22. Máquina: 4 vCPU, Python 3.13, SQLite. Tudo o que está abaixo como "medido" foi rodado de fato; o que não foi possível simular está na seção "Lacunas".

## 1. O que foi simulado

| Item | Arquivo | O que faz |
|---|---|---|
| Jira simulado (Cloud `/rest/api/3` e Data Center `/rest/api/2`) | `backend/tests/mock_jira.py` | App FastAPI com dataset determinístico (seed), configurável: N issues, épicos, Checklist com subtarefa Review (`fields.parent`), histórico de status/assignee/vencimento/labels/estimativa/prioridade/resolução, worklogs, ex-funcionário que só existe no histórico. Cloud: `/myself`, `/field`, `POST /search/jql` (nextPageToken/isLast), `POST /changelog/bulkfetch` (com **históricos duplicados** e paginação cujo último bloco se repete), `GET /issue/{key}/worklog` (startAt/total), `/user/assignable/search`, `PUT /issue/{key}/assignee`, `/issue/{key}/changelog`. DC: `/myself`, `/field`, `GET /search` (startAt/maxResults/total, `expand=changelog`, **changelog truncado** opcional), assignee `{"name":...}`, `/issue/{key}?expand=changelog`. Também: 429 com Retry-After (injetado ou periódico), 503 intermitente, JQL mínimo (`project is not EMPTY`, `project in`, `key in (...)`, `updated >= "yyyy-MM-dd HH:mm"` interpretado no **fuso do usuário**, `ORDER BY`), `key in` estrito (400 para chave inexistente, como o Jira Server), Basic no Cloud e Bearer no DC (401 caso contrário), datas com offset configurável (`2026-10-01T10:00:00.000-0300`). Serve por transporte síncrono (TestClient) ou por thread uvicorn em porta livre (`MockServer`). |
| Integração Syncer ponta a ponta | `backend/tests/test_sync_integration.py` | 67 testes (Cloud e DC) em SQLite temporário (nunca o banco do demo). |
| Rotas de análise sobre dados sincronizados | `backend/tests/test_rotas_pos_sync.py` | App real (`jiradash.main.app`) com `get_db` apontado para o banco sincronizado a partir do simulado (Cloud e DC), incluindo `POST /api/sync` em modo `jira`. |
| Filtros e pesquisas | `backend/tests/test_filtros_simulados.py` | Todos os `GET /api/*` (descobertos pelo OpenAPI) × 16 combinações de filtros (vazias, incompatíveis, inexistentes, período futuro/antigo/invertido) + 3 filtros inválidos (esperado 422) + consistência (soma por squad, filtro restringe, período vazio zera, NaN/inf). |
| Carga | `backend/tests/load/` (`run.py`, `gen_big.py`) | Fora do pytest. Sobe uvicorn (1 worker) em porta livre, N usuários simultâneos (threads + httpx keep-alive) com mistura ponderada de 12 endpoints e filtros variados; dataset grande sintético. Há um teste de fumaça das ferramentas (`tests/test_load_smoke.py`). |

## 2. Como rodar (comandos exatos)

```bash
cd /home/user/dashboardJira/app/backend
pip install -r requirements.txt   # ou /home/user/dashboardJira/requirements.txt se o ambiente foi recriado

python3 -m pytest -q                                   # tudo (padrão): ~4 min 45 s
python3 -m pytest -q tests/test_sync_integration.py    # integração com o Jira simulado (~35 s)
python3 -m pytest -q tests/test_rotas_pos_sync.py      # rotas sobre dados sincronizados (~16 s)
python3 -m pytest -q tests/test_filtros_simulados.py   # matriz de filtros no demo (~2 min 45 s)

# carga (não roda no pytest):
python3 -m tests.load.run                       # demo (cache ligado e desligado) + dataset grande; ~25 min
python3 -m tests.load.run --skip-big            # só demo (~7 min)
python3 -m tests.load.run --skip-demo --big-timeout 240   # só dataset grande
python3 -m tests.load.gen_big --db /tmp/big.db --issues 20000   # só gerar o banco grande (~9 s)
```

Saída da carga: `app/docs-testes/resultados_carga.md` e `.json` (execuções parciais são mescladas por cenário).

## 3. Resultado do pytest padrão

`python3 -m pytest -q` em `app/backend`: **590 passed, 19 xfailed (609 testes), 283 s**. Os 19 xfailed são bugs reais registrados com motivo (seção 5), não falhas ocultas. (Antes deste trabalho: 42 testes.)

Parte da contagem vem de testes de outras pessoas que entraram na pasta durante o trabalho (`test_discover.py`, `test_reports.py`); os meus são: integração 67, rotas pós-sync 64 (62 + 2 xfail), filtros 425 (incl. 18 xfail), fumaça de carga 2.

## 4. Resultados de carga (números medidos)

Detalhe completo, por endpoint e nível, em `resultados_carga.md`. Servidor: uvicorn com **1 worker**, cliente na mesma máquina.

### 4.1 Demo (~900 issues), concorrência (mistura realista de 12 endpoints, 20 s por nível)

| Cenário | Usuários | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Erros (%) |
|---|--:|--:|--:|--:|--:|--:|
| Cache ligado (TTL 60 s, padrão) | 1 | 5,03 | 110 | 513 | 1026 | 0 |
| | 5 | 2,41 | 111 | 10315 | 10860 | 0 |
| | 20 | 0,78 | 650 | 64651 | 65262 | 18,5 |
| | 50 | 0,63 | 31108 | 79703 | 80488 | 54,7 |
| Cache desligado (TTL 0) | 1 | 2,55 | 362 | 779 | n/d | 0 |
| | 5 | 0,43 | 11267 | 24072 | n/d | 0 |
| | 20 | 0,27 | 67269 | 71843 | n/d | 15,0 |
| | 50 | 0,63 | 31558 | 79148 | n/d | 52,7 |

Os "erros" em 20 e 50 usuários são, em sua maioria, estouro do timeout de 120 s do cliente (requisições na fila do servidor), não respostas 500. Com cache desligado, o throughput **cai** quando a concorrência sobe (2,55 → 0,43 rps): as requisições competem pela CPU (GIL, cada uma carrega e processa todas as issues em Python) e o RPS por usuário colapsa. Com cache ligado também cai, por "avalanche" de cache frio: 5 usuários pedindo o mesmo painel ao mesmo tempo calculam todos, em paralelo, o mesmo payload (`cache.get_or_compute` não deduplica cálculos em andamento). Observação: o comportamento acima foi medido em uma única execução por cenário (uma primeira execução, descartada por ter sido interrompida, deu números do mesmo perfil: 4,57 → 1,71 → 0,90 → 0,79 rps); não há repetição estatística.

Latência sequencial (1 por vez, cache desligado, 12 requisições por endpoint) no demo: overview p50 **1910 ms** (único acima de 1 s); open 360, closed 405, negative 346, people 327, capacity 305, forecast 357, goals 319, review 322, integrity 117 (4 erros 500, ver bug B2), bottlenecks 384, distribution/pending 341 ms.

### 4.2 Dataset grande (22.012 issues, 104.525 transições, 47.609 eventos, 42.601 worklogs, 2.068 abertas, 107+9 pessoas), cache desligado

Um servidor novo por endpoint, uma requisição sem filtro e uma com squad (n=2). Concorrência **não** foi medida neste volume (cada requisição leva dezenas de segundos).

| Endpoint | com squad (ms) | sem filtro (ms) | > 1 s |
|---|--:|--:|:--:|
| overview | timeout > 240 000 | timeout > 240 000 | SIM |
| open | 7001 | 31175 | SIM |
| closed | 5827 | 23602 | SIM |
| negative | 6774 | 23099 | SIM |
| people | 6270 | 22557 | SIM |
| capacity | 5898 | 24034 | SIM |
| forecast | 6935 | 22745 | SIM |
| goals | 7343 | 24461 | SIM |
| review | 6101 | 22161 | SIM |
| integrity | 6422 | 20989 | SIM |
| bottlenecks | 7163 | 23609 | SIM |
| distribution/pending | 5627 | 16907 | SIM |

**Todos os 12 endpoints passam de 1 s neste volume.** O cache de 60 s esconde isso para o segundo acesso, mas o primeiro (e o primeiro após cada sincronização, que invalida o cache) leva 17 a 31 s, e `overview` não terminou em 4 minutos.

### 4.3 Causa provável da lentidão (apenas descrição; não alterei `analytics.py`)

Perfil (cProfile, dataset de 3.339 issues, uma chamada por endpoint, inflado pelo profiler):

1. **`Analytics.load_all` (analytics.py:79-90)** faz `joinedload` de três coleções ao mesmo tempo (`transitions`, `assignee_changes`, `events`). O SQL devolve o produto cartesiano das três por issue (linhas = transições × mudanças × eventos), e `_fetchall_impl` do SQLAlchemy domina (2,6 a 3,6 s dos 3 a 4 s de cada endpoint). Todo endpoint carrega **todas** as issues do escopo mesmo para pedir só as abertas ou só 30 dias de fechadas. Sugestões: `selectinload` no lugar de `joinedload`; filtrar no SQL por `resolved >= início` ou `status_category != 'done'` antes de materializar; carregar histórico só das issues necessárias.
2. **`Analytics.status_median_days` (analytics.py:134)**, chamado por `overview`: 321 chamadas (uma por issue aberta, no teste de 3 mil) cada uma recalculando `metrics.core.status_durations` sobre todas as issues (948 mil chamadas, 15,5 s de 30,6 s). É O(abertas × issues): explica o `overview` quadrático (> 240 s com 22 mil). Calcular a mediana por status uma única vez e reutilizar.
3. **`Analytics.plan` (analytics.py:182)** recarrega/agenda a fila de cada pessoa e é chamado por `people`, `capacity`, `overview`; reaproveitar o resultado dentro do mesmo pedido.
4. **Sem deduplicação de cálculo concorrente no cache (`cache.py`)**: avalanche de cache frio (ver 4.1). Um lock por chave resolveria.
5. Execução em um worker, SQLite, tudo em Python puro: sob concorrência as requisições disputam o GIL. Ajuda: mais workers (cada um com seu cache; um cache compartilhado seria melhor), pré-aquecimento do cache após o sync.

## 5. Bugs encontrados

### 5.1 Corrigidos (código em `jira/*.py` e `jobs.py`)

| # | Bug | Onde | Correção | Teste que cobre |
|---|---|---|---|---|
| C1 | Sincronização incremental usava a marca d'água em UTC no JQL `updated >= "yyyy-MM-dd HH:mm"`, que o Jira interpreta no **fuso do usuário**. Com usuário em UTC−3 a janela ficava 3 h no futuro e issues alteradas nas últimas 3 h não vinham. Dado perdido silenciosamente. | `sync.py` `_since_clause` (l. 228), `client.py` `user_timezone` (l. 117) | Converte a marca d'água para o fuso do `timeZone` de `/myself` | `test_incremental_respeita_fuso_do_usuario_jira` |
| C2 | `reconcile_open` quebrava com 400 quando o Jira recusa `key in (...)` com chave inexistente (justamente as removidas); só funciona com chaves existentes | `sync.py` `reconcile_open` (l. 263) | Lê as chaves citadas no erro, tira da consulta e conta como removidas | `test_reconcile_marca_issues_removidas` |
| C3 | `reconcile_open` comparava por **chave**: issue movida de projeto (chave nova) era marcada como removida | idem | Compara por id | `test_reconcile_nao_remove_issue_movida_de_projeto` |
| C4 | Ex-funcionário que só aparece no histórico de responsável virava `None` (perdia a ponta da mudança) | `sync.py` `_by_ref` (l. 191) | Cria `Person` a partir de from/to + nome | `test_ex_funcionario_no_historico_vira_pessoa` |
| C5 | Changelog truncado no DC (`expand=changelog` na busca traz `total` > histories): histórico incompleto sem aviso | `client.py` `changelogs` / `full_changelog` (l. 190) | Detecta `total > len(histories)` e busca `/issue/{key}/changelog` (ou `expand=changelog` na issue se 404) | `test_changelog_truncado_no_dc_*` |
| C6 | Worklog editado ou apagado no Jira nunca refletia (só inseria novos; pulava issue com `timespent` 0 mesmo havendo worklogs locais) | `sync.py` `_sync_worklogs` (l. 207) | Atualiza, remove e verifica issue que zerou | `test_worklog_editado_e_removido_reflete_no_incremental` |
| C7 | Squad por componente: ordem do YAML decidia (squad "curinga" do projeto, listado antes, ganhava do squad de componente) | `sync.py` `squad_for` (l. 97) | Componente casado tem prioridade, depois o squad do projeto | `test_squad_por_componente` |
| C8 | DC: `epic_key` nunca preenchido (vínculo é o campo "Epic Link"); sem pai, `epic_key` ficava velho | `client.py` `discover_field_ids` (l. 127), `sync.py` (l. 143) | Descobre `Epic Link` e usa; limpa quando sem pai | `test_epic_key_preenchido` |
| C9 | Datas de vencimento do changelog em formatos diferentes (`2026-10-05`, `05/Oct/26`, `05/out/26`, `2026-10-05 00:00:00.0`) gravadas cruas; `parse_due` de `reports.py` só entende alguns | `sync.py` `norm_date` (l. 46) | Normaliza tudo para `YYYY-MM-DD` | `test_duedate_dc_normalizado_para_iso` |
| C10 | Status fora do `status_map` passava em silêncio | `sync.py` (l. 115, 257) | Usa a categoria do Jira, loga aviso e devolve `status_sem_mapeamento` no resultado do sync | `test_status_nao_mapeado_*` |
| C11 | Erros HTTP sem orientação ("-> 401: ..."); resposta HTML/SSO virava `JSONDecodeError`; erro de rede escapava como exceção do httpx | `client.py` `explain_http_error` (l. 31), `_request` (l. 78), `_json` (l. 102) | Mensagens em português por código (401/403/404/400, CAPTCHA), `JiraError.status`, retry em erro de rede, Retry-After limitado a 300 s | `test_401_*`, `test_403_*`, `test_resposta_html_*`, `test_erro_de_rede_*` |
| C12 | `run_sync`/`sync_keys` não aceitavam cliente injetado, não invalidavam o cache dos payloads e o webhook de issue excluída dava 500 | `jobs.py` (l. 21, 35) | Parâmetro `client`, `cache.invalidate()` no próprio `run_sync`, issue inexistente vira `deleted_at` | `test_jobs_*` |

Já estavam corretos e foram confirmados por teste: dedupe de históricos duplicados do bulk fetch, autor ausente (`author_id` None), labels como string separada por espaço, estimativa, `parent` em `fields.parent` (subtarefa Review), conversão de fuso das datas (-0300, +0530 etc.), backoff com Retry-After e exponencial, atomicidade (falha no meio não grava carga parcial), idempotência (2 cargas completas + 1 incremental sem duplicar).

### 5.2 Pendentes (fora do meu escopo; descritos para quem edita esses arquivos)

| # | Bug | Onde | Efeito | Teste (xfail) |
|---|---|---|---|---|
| B1 | `review_view` faz `round(None)` quando há pares mas nenhum ciclo no período | `reports.py:266` | `/api/review` e `/api/alerts` respondem **500** com período futuro, antigo, invertido ou de 1 dia | `test_filtros_simulados.py` (review/alerts × periodo_*) |
| B2 | `integrity` grava `vars(a.f)` com objetos `date` no `AuditLog.detail` (JSON) | `api/routes.py:94` | `/api/integrity` com `date_from` ou `date_to` responde **500** (a tela de integridade filtrada por período quebra) | `test_filtros_simulados.py` (integrity × periodo_*), `test_rotas_pos_sync.py::test_integridade_com_periodo` |
| B3 | `schedule` acessa `cap_by_week[wi]` com `wi == len(...)` quando um item de 0 h vem depois de a fila esgotar o horizonte de 12 semanas | `metrics/capacity.py:92` | **500 em `overview`, `open`, `people`, `capacity`, `forecast`, `bottlenecks`** assim que uma pessoa tem mais fila que 12 semanas de capacidade (reproduzido com 9 pessoas e 3,3 mil abertas) | `test_schedule_com_horizonte_esgotado` |
| B4 | Intervalo invertido (`date_from > date_to`) não é validado | `api/routes.py` `flt` | responde 200 com zeros; idealmente 422 ou aviso (o teste aceita 200 ou 422) | `test_periodo_invertido_e_tratado_como_vazio_ou_422` |
| B5 | `datetime.utcnow()` deprecated em todo o código (inclusive `sync.py`) | vários | só aviso hoje; quebra em versões futuras do Python | n/a |

Observação: no demo, `integrity` apareceu com 4 respostas 500 nas 12 requisições sequenciais da carga porque os filtros de período entram na mistura (bug B2).

## 6. Lacunas (o que NÃO foi possível simular ou não foi feito)

- **Não há Jira real**: tudo é o formato documentado reescrito por mim. Comportamentos que supus e podem divergir do Jira real: `key in (...)` com chave inexistente devolve 400 (documentado para Jira Server, **não confirmado no Cloud**); formato dos itens de `duedate`/`labels`/estimativa no changelog; existência de `/issue/{key}/changelog` no DC (há fallback); `timeZone` em `/myself`; limite de 10 `fieldIds` no bulkfetch; ordem dos históricos.
- **Não testados**: ADF/campos ricos, campos Sprint e Story Points reais (só IDs fictícios), permissões por projeto, issue security levels, `/worklog/updated` (o cliente busca worklog por issue, uma chamada por issue com horas: custo O(issues) em carga completa; não otimizado), webhooks reais, rate limit adaptativo do Cloud (cost-based), paginação com mais de 5.000 resultados por JQL, SSO/OAuth, proxy corporativo/certificados.
- **PostgreSQL, Docker e o agendador** (`start_scheduler`) não foram exercitados; tudo em SQLite.
- Carga: um worker, cliente e servidor na mesma máquina (o cliente consome CPU), uma execução por cenário (sem repetição estatística), concorrência **não** medida no dataset grande, `overview` no grande não terminou em 240 s (limite que escolhi; o tempo real é maior e desconhecido). Dataset grande é sintético e tem distribuição própria (86% concluídas, 107+9 pessoas); os números mostram escala de custo, não previsão para o seu Jira. A carga **da sincronização** (Syncer contra 20 mil issues) não foi medida; só o teste de 429 periódico com ~100 issues.
- Frontend não foi testado (fora do escopo).
- O pytest completo leva ~4 min 45 s (a matriz de filtros do demo recalcula cada combinação; o cache de 60 s não ajuda porque cada combinação é distinta). `tests/test.db` é recriado pelos testes de API/filtros.

## 7. Resumo

Mock de Jira Cloud/DC criado e usado em 67 testes de integração (carga completa, incremental, reconciliação, 429/503, 401/403, fuso, changelog sujo), 64 testes de rotas sobre dados sincronizados e 425 de filtros. Foram corrigidos 12 problemas em `jira/*.py` e `jobs.py` (o mais grave: sync incremental perdia alterações por fuso). Três bugs 500 pendentes em arquivos de outros (`reports.py:266`, `routes.py:94`, `capacity.py:92`) e lentidão severa em volume (todos os endpoints > 1 s com 22 mil issues; `overview` > 4 min), com causas prováveis descritas. Pytest: 590 passaram, 19 xfail (bugs documentados).

---

## Atualização: bugs pendentes corrigidos depois deste relatório

Os quatro itens que estavam pendentes em arquivos de outra pessoa foram corrigidos: `reports.py` (`round(None)` em `/api/review` e `/api/alerts`), `api/routes.py` (data crua no log de auditoria de `/api/integrity`), `metrics/capacity.py` (`schedule` com item de 0 h após esgotar o horizonte) e a validação de intervalo invertido (`date_from > date_to` agora responde 422). Os testes marcados como `xfail` desses casos passam a ser "xpass" (sem falha). Também foram corrigidas inconsistências apontadas na revisão do manual (fuso local em "no prazo", filtros por tela, sugestões filtradas por squad, central de atenção sem dupla contagem). Os números de carga atualizados estão no fim de `resultados_carga.md`.
