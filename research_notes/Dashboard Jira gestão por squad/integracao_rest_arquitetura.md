# Integração técnica com Jira (Cloud e Data Center) para backend de dashboard

Estado em 2026-10-22. Fontes primárias consultadas: developer.atlassian.com (rate limiting, webhooks, changelog), confluence.atlassian.com (PAT DC). Páginas de referência REST v3 (api-group-*) são SPA e vieram truncadas no fetch; detalhes de endpoints vêm de buscas/espelhos e estão marcados. Não repete JQL/changelog de /home/user/dashboardJira/reports/.

## 1. Autenticação e identificação de usuário (Cloud vs Data Center)

### Takeaway
Cloud: Basic (email + API token) em `https://{site}.atlassian.net/rest/api/3`, ou OAuth 2.0 3LO via `api.atlassian.com/ex/jira/{cloudId}`; identificação por `accountId`. DC: PAT com `Authorization: Bearer` (8.14+), identificação por `name`/`key`. Importante: tráfego por API token (Basic) está fora da nova cota por pontos do Cloud.

### Cited Findings
- Cloud, API token usado como senha em Basic com o email da conta (`curl -u email:token`); tokens têm expiração (relatos: 1 a 365 dias, padrão 1 ano); tokens com escopo (scoped) devem ser chamados por `api.atlassian.com/ex/jira/{cloudId}/...`, não pelo domínio do site. Fontes secundárias (blogs), não confirmadas na doc oficial — [Titan Apps](https://titanapps.io/blog/jira-api-token-how-to-create-manage-and-secure-your-authentication), [Knit](https://getknit.dev/blog/jira-api-key)
- Cloud OAuth 2.0 3LO: authorize com `audience=api.atlassian.com` e `offline_access`; troca em `https://auth.atlassian.com/oauth/token`; obter cloudId em `https://api.atlassian.com/oauth/token/accessible-resources`; chamar `https://api.atlassian.com/ex/jira/{cloudid}/{api}`; usar o domínio do site com token 3LO é base URL errada — [Nango](https://nango.dev/blog/jira-oauth-refresh-token-invalid-grant)
- Refresh token 3LO rotativo: gravar sempre o novo refresh token retornado, evitar refresh concorrente; expiração por 90 dias de inatividade (fonte secundária); relatos de fórum de falha após ~30 dias da cadeia — conflito não resolvido — [Nango](https://nango.dev/blog/jira-oauth-refresh-token-invalid-grant), [Community Atlassian](https://community.developer.atlassian.com/t/expiry-of-refresh-token-jira-cloud-oauth2-3lo-grants/28294)
- Cloud usa `accountId` no lugar de username/userKey desde a migração de privacidade (GDPR; prazo 29/04/2019); email só aparece se o usuário permitir — [Migration guide](https://developer.atlassian.com/cloud/jira/platform/deprecation-notice-user-privacy-api-migration-guide/), [Community](https://community.developer.atlassian.com/t/jira-cloud-rest-api-privacy-updates-and-migration-guide/24166?page=4)
- DC: Personal Access Token disponível em Jira Core/Software 8.14+ (JSM 4.15+); enviado como `Authorization: Bearer <token>`; mesmas permissões do dono; validade máxima padrão 365 dias (`datlassian.pats.max.tokens.expiry.days`); máx. 10 tokens/usuário (padrão); admin pode revogar/ver último uso; criação via `POST /rest/pat/latest/tokens` — [Atlassian PAT](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html)
- Rovo MCP Server: endpoint `https://mcp.atlassian.com/v1/mcp`, OAuth 2.1 ou API token (headless, se admin habilitar); GA reportado em 2026-02-04 (fonte secundária); sem limites de taxa publicados — apenas citar como alternativa para agentes, não para ETL — [Atlassian Rovo MCP](https://developer.atlassian.com/cloud/rovo-mcp/), [Scalekit](https://www.scalekit.com/blog/atlassian-mcp-vs-api)

### Inferences
- Para um backend de dashboard single-tenant, conta de serviço + API token (Cloud) ou PAT (DC) é o caminho mais simples; 3LO só se for produto multi-tenant/instalável. Basic fica fora da cota por pontos (ver seção 4), vantagem prática.
- Modelar `pessoa` com chave externa genérica: `accountId` (Cloud) ou `key`/`name` (DC), mais campo `origem`. Não usar email como chave.
- Agendar rotação/alerta de expiração do token (365 dias) como tarefa operacional.

### Gaps
- Não confirmei na doc oficial o limite máximo de expiração de API token Cloud nem a data de expiração forçada de tokens legados (blog cita 14/03–12/05/2026).
- Não confirmei comportamento exato de `name`/`key` no DC 10/11 (deprecação, privacidade); busca só retornou material Cloud.
- Não verifiquei a data de fim de suporte do DC 9.12 e lista de mudanças REST do DC 11.x.

## 2. Busca de issues, paginação, changelog, campos e Agile API

### Takeaway
Cloud: `GET/POST /rest/api/3/search` foi removido (aviso 31/10/2024, remoção efetiva 01/05/2025 segundo KB da Atlassian); usar `/rest/api/3/search/jql` com `nextPageToken`, sem `total`, sem paginação paralela e com JQL obrigatoriamente limitado. DC continua com `/rest/api/2/search` (startAt/maxResults).

### Cited Findings
- Endpoint legado removido; datas: anúncio 31/10/2024, remoção 01/05/2025 (GET e POST `/rest/api/3/search`) — [KB Atlassian](https://confluence.atlassian.com/display/JIRAKB/Run+JQL+search+query+using+Jira+Cloud+REST+API)
- Novos endpoints: `/search/jql`, `/search/approximate-count`, `/expression/evaluate`, `/issue/bulkfetch`; removidos: GET/POST `/search`, POST `/search/id`, POST `/expression/eval` — [RFC-61](https://community.developer.atlassian.com/t/rfc-61-evolving-search-capabilities-addressing-scalability-with-a-new-enhanced-search-api/83027)
- `nextPageToken` substitui `startAt`; páginas não podem ser buscadas em paralelo; token expira em 7 dias e cada resposta devolve novo token; `maxResults` é teto (pode retornar menos) — [RFC-61](https://community.developer.atlassian.com/t/rfc-61-evolving-search-capabilities-addressing-scalability-with-a-new-enhanced-search-api/83027)
- JQL ilimitado (vazio ou só `order by`) retorna 400; recomendação: limitar, ex. `updated > -1m` — [RFC-61](https://community.developer.atlassian.com/t/rfc-61-evolving-search-capabilities-addressing-scalability-with-a-new-enhanced-search-api/83027)
- Consistência eventual: resultados podem não refletir escrita recente; `reconcileIssues: [ids]` no corpo exige consistência forte para issues específicas — [RFC-61](https://community.developer.atlassian.com/t/rfc-61-evolving-search-capabilities-addressing-scalability-with-a-new-enhanced-search-api/83027)
- Sem `total`: contagem exige `/search/approximate-count` — [GitLab MR](https://gitlab.com/gitlab-org/gitlab/-/merge_requests/206815)
- Changelog de 08/10/2026: consultas com funções JQL customizadas inválidas passam a retornar 400 imediato em `/search/jql` — [Jira Cloud changelog](https://developer.atlassian.com/cloud/jira/platform/changelog/)
- Changelog de 07/10/2026: busca por user entity properties em voter/watcher/creator depreciada, remoção 16/11/2026 (assignee/reporter continuam) — [Jira Cloud changelog](https://developer.atlassian.com/cloud/jira/platform/changelog/)
- Bulk changelog: `POST /rest/api/{2|3}/changelog/bulkfetch` com `issueIdsOrKeys` (até 1000 issues), `fieldIds` (até 10), `maxResults`, `nextPageToken`; relato de duplicatas dependente de page size (JRACLOUD-94906) — deduplicar por id do changelog — [jira.js source](https://app.unpkg.com/@esm-js/jira.js@5.1.0/files/src/version3/issues.ts), [JRACLOUD-94906](https://jira.atlassian.com/browse/JRACLOUD-94906)
- `POST /rest/api/3/issue/bulkfetch` (até 1000 issues, segundo post da comunidade, sem confirmar na doc) — [Community](https://community.atlassian.com/forums/Jira-Cloud-Admins-articles/1-000-Issues-One-Call-Bulk-Fetch-Gets-a-10-Boost/ba-p/3281867)
- Atlassian considera tamanhos de página padrão/máximo como não contratuais; ler o tamanho efetivo da resposta — [KB maxResults](https://confluence.atlassian.com/jirakb/changing-maxresults-parameter-for-jira-rest-api-779160706.html)
- Bugs reportados de paginação em clientes/MCP (isLast, token repetido) — não confirmados por fonte Atlassian — [atlassian-mcp-server#118](https://github.com/atlassian/atlassian-mcp-server/issues/118)
- Agile: `/rest/agile/1.0/board/{id}/issue`, `/sprint/{id}/issue` com startAt/maxResults/fields; board listing padrão 50, issues do board até 100 por página (cliente comunitário); sem permissão no board retorna vazio; Sprint é custom field identificado por `com.pyxis.greenhopper.jira:gh-sprint` (ID varia por site); no endpoint de plataforma vem em string difícil de parsear, preferir Agile API — [Jira Software REST](https://docs.atlassian.com/jira-software/REST/2017-09-06T07:06:51Z/), [Yext](https://help.yext.com/hc/en-us/articles/51261110934939-Jira)
- DC 10.4 introduziu nova Search API (Lucene -> OpenSearch); 11.2 suporta OpenSearch em produção; admins podem definir máximo de resultados, limite de tempo de JQL e circuit breaker de memória (buscas podem falhar com SearchException) — [Adaptavist guide](https://docs.adaptavist.com/sr4js/latest/release-notes/breaking-changes/jira-11-search-api-upgrade-guide), [Jira 11.2 notes](https://confluence.atlassian.com/jirasoftware/jira-software-11-2-x-release-notes-1653834634.html)

### Inferences
- Descoberta de campos: chamar `GET /rest/api/{2|3}/field` na inicialização e mapear por `name`/`schema.custom` (Story Points, Sprint, Epic Link/Parent, Flagged, Team) para IDs; persistir mapeamento por instância. Isso é prática padrão, os IDs variam por site (confirmado para Sprint).
- Fluxo Cloud eficiente: `search/jql` com `fields=key` (só IDs, página grande) -> `issue/bulkfetch` para campos -> `changelog/bulkfetch` para histórico, em vez de `expand=changelog` por issue (que trunca histórico longo; checar no report de changelog existente).
- Como não há paralelismo dentro de uma mesma consulta, paralelizar particionando JQL (por projeto/janela de `updated`).
- Camada de adaptador `JiraClient` com duas implementações (Cloud v3 token / DC v2 startAt) retornando o mesmo DTO.

### Gaps
- Limites exatos de `maxResults` de `search/jql` (valor máximo) e comportamento com `fields=*all`/`expand=changelog` não confirmados (páginas de referência truncaram).
- Endpoint `changelog/list` (por IDs de changelog) e escopos OAuth exigidos não confirmados.
- Endpoint de configuração de board/estimation (campo de story points por board) não verificado.
- Estado atual dos bugs de paginação do `/search/jql` não confirmado.

## 3. Worklogs

### Takeaway
Fluxo incremental Cloud: `GET /worklog/updated?since=<ms>` -> seguir `nextPage` até `lastPage` -> `POST /worklog/list` em lotes de até 1000 IDs; deletados via `/worklog/deleted`. Tempos em segundos (`timeSpentSeconds`).

### Cited Findings
- `GET /rest/api/3/worklog/updated`: 1000 worklogs por página, ordem cronológica, `since` em milissegundos Unix, `nextPage`/`lastPage`, não retorna worklogs atualizados no último minuto — [Qodex mirror](https://developers.qodex.ai/atlassian-jira/jira-reference/worklog/get-ids-of-updated-worklogs), [Withone mirror](https://withone.ai/knowledge/jira/conn_mod_def%3A%3AGJ4qd4nvXCU%3A%3A0Xd4XHsSTjmniQK2MpqVqA)
- `POST /rest/api/3/worklog/list`: máx. 1000 IDs (400 se vazio ou >1000); visibilidade pode omitir worklogs mesmo com ID conhecido — [Qodex mirror](https://developers.qodex.ai/atlassian-jira/jira-reference/worklog/get-ids-of-updated-worklogs)
- Webhooks `worklog_created/updated/deleted` existem no Cloud — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Campos de issue em segundos (`timeoriginalestimate`, `timeestimate`, `timespent`, `aggregate*`) — conhecimento prévio, SEM fonte citável obtida nesta sessão.

### Inferences
- Persistir cursor `worklog_since` com margem de sobreposição (ex.: recuar 2-5 min por causa do atraso de 1 min) e fazer upsert por `worklog.id`; tratar `worklog/deleted` para soft-delete.
- Agregar horas por `author.accountId` + `started` (data), não por `created`/`updated`.

### Gaps
- Não obtive a doc oficial v3 dos worklogs (espelhos de terceiros); `/worklog/deleted` e DC (`/rest/api/2/issue/{key}/worklog`; DC não tem equivalente confirmado de `worklog/updated`) não verificados.
- Semântica de `aggregate*` vs. não-agregado não citada.

## 4. Rate limits e custo

### Takeaway
Cloud: desde 02/03/2026, cota por pontos/hora + burst por segundo + limite de escrita por issue, aplicados a apps Forge, Connect e OAuth 3LO; tráfego por API token NÃO é afetado pela cota por pontos (continua sob limites de burst). DC: limites configurados pelo admin.

### Cited Findings
- Enforcement da cota por pontos e em camadas: 02/03/2026 (doc atualizada em 21/10/2026); aplica a Forge, Connect, OAuth 2.0 3LO; API token excluído; GraphQL depois — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Custo: 1 ponto base + 1 por objeto core (issues, projetos) lido, 2 por objeto de identidade (usuários, grupos, roles, permissões); escritas 1 ponto; ex.: 8 membros de grupo = 17 pontos — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Tier 1 (pool global): 65.000 pts/h; Tier 2 por tenant (sob revisão): Free 65k; Standard 100k+10×usuários; Premium 130k+20×usuários; Enterprise 150k+30×usuários; teto 500k/h; reset no início de cada hora UTC — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Burst por tenant por endpoint (token bucket): GET 100/s, POST 100/s, PUT/DELETE 50/s; `GET issue/{key}` 150/s — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Escrita na mesma issue: 20 por 2 s e 100 por 30 s — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- 429 traz `Retry-After`, `X-RateLimit-Reset` (ISO 8601), `RateLimit-Reason` (`jira-quota-global-based`, `jira-quota-tenant-based`, `jira-burst-based`, `jira-per-issue-on-write`); informativos: `X-RateLimit-Limit/Remaining/NearLimit`; headers `Beta-RateLimit-Policy`/`Beta-RateLimit` informativos; estouro de cota = 429 até a próxima hora; 5xx/503 podem trazer `Retry-After` — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Backoff recomendado: respeitar `Retry-After` como mínimo, repetir só requisições idempotentes, exponencial (início 2-5 s, teto ~30 s, ~4 tentativas) com jitter 0,7-1,3; reagir por razão (quota = pausar tudo; burst = só aquele endpoint); usar webhooks, ETags, campos mínimos, espalhar jobs e compartilhar estado de rate limit entre threads/nós; não rodar testes de rate limit contra tenants — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Reclamações de que a API de permissões consome pontos muito rápido (~30 checks/h relatado) — [Community 2026 point-based](https://community.developer.atlassian.com/t/2026-point-based-rate-limits/97828/137)
- DC: limites definidos pelo admin (limite padrão por usuário, exceções); 429 sem garantia de `Retry-After`; relato de 429 persistente com Bearer e `X-RateLimit-*` = 0 (limite administrativo) — [DC rate limiting 9.11](https://confluence.atlassian.com/adminjiraserver0911/adjusting-your-code-for-rate-limiting-1318891292.html), [Community](https://community.developer.atlassian.com/t/inquiry-about-persistent-429-error-on-rest-api-requests/87370)

### Inferences
- Se a conta de serviço usar API token Cloud, a cota por pontos não se aplica hoje, mas o design deve ser tolerante caso Atlassian mude; evitar chamadas a usuários/permissões (2 pontos por objeto).
- Concorrência baixa e fixa (ex.: 3-5 workers, semáforo global por tenant), contador compartilhado (Redis/DB) para 429 e pausa global quando `RateLimit-Reason` for de cota.
- DC: pedir ao admin uma isenção de rate limit para a conta de serviço e agendar cargas pesadas fora do horário comercial.

### Gaps
- Não confirmei se a exclusão de API token da cota por pontos permanece em 2026-10 além do que a página diz; não verifiquei limites padrão do DC 10/11.
- Datas de GraphQL "a anunciar".

## 5. Webhooks e sincronização incremental

### Takeaway
Combinar webhooks (baixa latência) com polling incremental por `updated >= watermark - margem` e reconciliação periódica; processamento idempotente por upsert e deduplicação por `X-Atlassian-Webhook-Identifier`.

### Cited Findings
- Tipos Cloud: admin webhooks (`/rest/webhooks/1.0/webhook`, com `secret` opcional), Connect/descriptor, dinâmicos (`/rest/api/3/webhook`, apenas Connect e OAuth2) — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Webhooks dinâmicos expiram em 30 dias (estender via PUT "Extend webhook life"; expirados ficam por até 3 meses); limites: 100 por app/tenant (Connect), 5 por app/usuário/tenant (OAuth); JQL restrito (`=`, `!=`, `IN`, `NOT IN` em issueKey, project, issuetype, status, priority, assignee, reporter etc.) — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Eventos: `jira:issue_created/updated/deleted`, `worklog_created/updated/deleted`; `changelog` no payload só em `jira:issue_updated` — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Retentativas (408, 409, 425, 429, 5xx, timeout): até 5, intervalo ~5-15 min; dedupe com `X-Atlassian-Webhook-Identifier`; `X-Atlassian-Webhook-Retry`; meta de entrega 30 s (primary) / 15 min (secondary); concorrência 20/10 por tenant-host — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Assinatura: com secret, header `X-Hub-Signature` (`method=signature`, normalmente sha256) = HMAC do corpo bruto — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- DC: webhooks configurados em Admin > System > Webhooks ou REST, com filtro JQL; eventos `jira:issue_updated`, `worklog_updated`; relato (draft antigo) de `jira:worklog_updated` deprecado; sem assinatura documentada nas fontes; retry do DC não documentado nas fontes — [Webhooks DC](https://developer.atlassian.com/server/jira/platform/webhooks/), [Hookdeck](https://hookdeck.com/webhooks/platforms/guide-to-jira-webhooks-features-and-best-practices.md)
- Consistência eventual do `search/jql` — ver seção 2 (RFC-61)

### Inferences
- Algoritmo: `watermark` = max(`updated`) processado; consulta `project in (...) AND updated >= watermark - 5min ORDER BY updated ASC`; upsert por `issue.id`; reconciliação diária/semanal (varredura de IDs/`approximate-count` por projeto, comparação com banco) para capturar deletadas (marcar `deleted_at` quando ausente e `GET issue` retorna 404) e movidas (key muda, `id` permanece: usar `id` como chave primária, `key` mutável; guardar histórico de keys).
- Webhook só enfileira o `issue.id`; um worker rebusca via API (evita confiar em payload parcial/fora de ordem). Responder 2xx rápido, validar HMAC.
- Como webhooks admin exigem permissão de admin, e dinâmicos exigem app OAuth/Connect, para conta com API token o polling pode ser o único caminho sem admin.

### Gaps
- Retry/entrega e assinatura de webhooks no DC não confirmados; comportamento de issues movidas em webhooks não verificado; detalhes de endpoint de worklogs deletados e de issues deletadas em API (sem endpoint "deleted issues" no Cloud conhecido, não citado).

## 6. Arquitetura de referência e modelo de dados

### Takeaway
(Recomendação própria, não é fato citado.) Serviço FastAPI + SQLAlchemy + PostgreSQL (SQLite só para dev), worker de sincronização separado, tabelas normalizadas com chave externa de origem e snapshots diários para CFD.

### Cited Findings
- Atlassian recomenda webhooks em vez de polling, cache com ETag, campos mínimos e compartilhar estado de rate limit entre nós — [Rate limiting Cloud](https://developer.atlassian.com/cloud/jira/platform/rate-limiting/)
- Verificação HMAC de webhooks e armazenamento seguro do secret (não recuperável) — [Webhooks Cloud](https://developer.atlassian.com/cloud/jira/platform/webhooks/)
- Admin DC pode listar/revogar PATs e ver último uso (útil para auditoria) — [PAT DC](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html)

### Inferences
- Componentes: (1) `jira_client` com adaptadores Cloud/DC; (2) `sync` jobs (APScheduler/Celery/RQ): incremental a cada 5-15 min, worklog incremental, reconciliação noturna, snapshot diário; (3) endpoint `/webhooks/jira` (valida HMAC, enfileira); (4) API do dashboard com RBAC; (5) cache (Redis ou materialized views) para agregações.
- Tabelas mínimas: `issue(id PK, key, project, type, status, status_category, assignee_ref, squad_id, story_points, sprint_id, epic_id, flagged, created, updated, resolved, deleted_at, raw_json)`; `status_transition(issue_id, from, to, at, author_ref, changelog_id UNIQUE)`; `worklog(id PK, issue_id, author_ref, started, seconds, updated, deleted_at)`; `person(id, source, external_id [accountId|key], display_name, active, team_id)`; `team/squad`; `absence(person_id, from, to, tipo)` (fonte externa/manual, não do Jira); `assignment_suggestion(issue_id, suggested_person_id, score, motivo, status, decided_by, decided_at)`; `daily_snapshot(date, scope, status, count, points)` para CFD; `sync_state(job, cursor/watermark, last_run, status)`; `audit_log(actor, action, entity, at, detail)`; `field_map(instance, logical_name, field_id)`.
- Segurança: token em secret manager/variáveis de ambiente, criptografia em repouso, nunca em logs; conta de serviço com privilégio mínimo; RBAC no dashboard (admin, gestor de squad, leitor) com escopo por squad; auditoria de ações (sugestões aceitas, edição de ausência).
- Retenção de `raw_json` das issues permite reprocessar sem nova chamada.
- Atribuição sugerida é só sugestão; escrita no Jira (Assign Issues) deve ser ação explícita e auditada.

### Gaps
- Nenhuma fonte citada define arquitetura de referência; todo o item acima é recomendação do pesquisador.

## 7. Permissões da conta de serviço e segurança de issues

### Takeaway
A conta só enxerga o que suas permissões permitem; dados ficam incompletos silenciosamente (board vazio, worklogs omitidos). Nomes exatos de permissões (Browse Projects, View Worklogs / Work on issues, Assign Issues) não foram confirmados por fonte nesta sessão.

### Cited Findings
- Worklog: visibilidade pode omitir worklogs mesmo em `worklog/list` com IDs válidos — [Qodex mirror](https://developers.qodex.ai/atlassian-jira/jira-reference/worklog/get-ids-of-updated-worklogs)
- Agile: sem permissão de ver o board, nenhuma issue é retornada — [Yext](https://help.yext.com/hc/en-us/articles/51261110934939-Jira)
- Escopos OAuth necessários variam por endpoint (ex.: `read:board-scope:jira-software`, `read:issue-details:jira`) — [Yext](https://help.yext.com/hc/en-us/articles/51261110934939-Jira)
- PAT herda as permissões do dono — [PAT DC](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html)
- Rovo MCP respeita IP allowlist e controles de admin — [Scalekit](https://www.scalekit.com/blog/atlassian-mcp-vs-api)

### Inferences
- Conceder à conta de serviço, via permission scheme dos projetos-alvo: Browse Projects, View Worklogs ("View all worklogs"; confirmar nome), e Assign Issues apenas se houver escrita; sem Admin. Para usuários/grupos, "Browse users and groups" (global).
- Issue security levels: a conta de serviço precisa estar em níveis de segurança para ver issues protegidas; o dashboard deve decidir se exibe issues restritas e registrar `security_level` no modelo para filtrar por RBAC do dashboard (não expor a quem não teria acesso no Jira).
- Validar cobertura: comparar contagem de `approximate-count` do admin vs. conta de serviço.

### Gaps
- Nomes oficiais e doc de permissões (Cloud e DC), campo `security` e tratamento de issue security não pesquisados em fonte primária.
- Endpoints de usuários/grupos (`/user/search`, `/group/member`, `/user/assignable/search`) e seus custos/limites não verificados.
