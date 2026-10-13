# REST changelog, worklog e JQL de histórico no Jira Data Center (9.12 LTS e 10.x)

Fontes principais (abreviações usadas abaixo):
- REF912 = https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/ (referência REST da plataforma, lida integralmente via download e extração de texto)
- OPS107 = https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html (Jira Software Data Center/Server, "last modified Mar 8, 2023")
- FLD107 = https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-fields-reference-1587940830.html
- FLDCORE = https://confluence.atlassian.com/jiracore/advanced-searching-fields-reference-1528201843.html
- Nota de escopo: as URLs de referência REST para 10.x testadas (10.0.0, 10.1.0, 10.2.0, 10.3.0, 10.3.1, 10.4.0, 10.5.0, 10.6.0, 10.7.0 em docs.atlassian.com/software/jira/docs/api/REST/<v>/) retornaram HTTP 404 em 2026-10-12. Não confirmei referência REST publicada para 10.x; tudo sobre REST abaixo é verificado apenas em 9.12.0. Para 10.x: "não confirmado" (assume-se continuidade, sem prova).

## 1. Histórico de status via REST: expand=changelog e endpoint dedicado /changelog

### Takeaway
No Data Center (REF912) o histórico vem apenas embutido com `expand=changelog` (campo `changelog.histories[]`, com startAt/maxResults/total no esquema). Não existe `GET /rest/api/2/issue/{key}/changelog` na referência 9.12.0; a sugestão para criá-lo (JRASERVER-59998) continua "Gathering Interest", sem versão de correção. O tamanho/truncamento do changelog embutido no DC não está documentado.

### Cited Findings
- Em REF912, `GET /rest/api/2/issue/{issueIdOrKey}` aceita `fields`, `expand`, `properties`, `updateHistory`; `expand=changelog` é descrito como "history of all changes of the given issue" — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- Esquema do changelog em REF912: `changelog {startAt, maxResults, total, histories[]}`; cada history tem `id`, `author` (user: self, name, key, emailAddress, displayName, active, timeZone), `created` (string), `items[]` e `historyMetadata`; cada item tem `field`, `fieldtype`, `from`, `fromString`, `to`, `toString` — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- Busca textual por `issue/{issueIdOrKey}/changelog` no HTML completo de REF912 não retornou nenhuma ocorrência (só `issue/{issueIdOrKey}/worklog...`); portanto o endpoint dedicado não consta na referência 9.12.0 — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- JRASERVER-59998 (Server/DC, componente REST API): "Limit changelog results to 20 entries and add a /changelog endpoint for full history". Texto do autor: "Currently full changelog is returned when using expand=changelog", com risco de OutOfMemory e timeout em /search ou /issue. Status "Gathering Interest", Unresolved, sem Fix Version, criado 29/02/2016, atualizado 15/08/2026 — [JRASERVER-59998](https://jira.atlassian.com/browse/JRASERVER-59998). A contraparte Cloud (JRACLOUD-59998) aparece como corrigida — [JRACLOUD-59998](https://jira.atlassian.com/browse/JRACLOUD-59998)
- JRASERVER-71168 (DC): pedido para parâmetros que filtrem o changelog em `expand=changelog` (por campo, valor, de/para, período); motivação: ETL para Tableau, o changelog é grande parte do payload. Unresolved, "Gathering Interest" — [JRASERVER-71168](https://jira.atlassian.com/browse/JRASERVER-71168)
- KB oficial DC ("All versions of Jira 8 and 9"): histórico obtido com `https://Jira-base-URL/rest/api/2/issue/JIRA-12345?expand=changelog` e parse de `.changelog.histories[]`; o artigo não menciona paginação nem truncamento — [KB changelog](https://support.atlassian.com/jira/kb/how-to-analyze-the-history-or-changelog-of-an-issue-in-jira/)
- O limite de 100 entradas do changelog embutido (com `startAt` para paginar) vem de uma pergunta de comunidade sobre Cloud; um comentarista observa que é Cloud, não DC (fonte secundária, não aplicável ao DC) — [Community](https://community.atlassian.com/forums/Jira-questions/Rest-API-limiting-changelog-history-results-to-100-even-if/qaq-p/1466525)
- Exemplo JSON de uso (padrão do esquema acima), para filtrar apenas transições de status no cliente: iterar `changelog.histories[]`, e dentro de cada `items[]` manter onde `field == "status"`; use `created` como data/hora da transição e `fromString`/`toString` como nomes (e `from`/`to` como IDs de status) — derivado do esquema em [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)

### Inferences
- Como o DC retorna "full changelog" (JRASERVER-59998) e o esquema traz `startAt/maxResults/total`, é provável que em DC o embutido venha completo, sem paginação efetiva; isto é inferência, não documentado.
- Como não há filtro server-side de changelog (JRASERVER-71168 aberto), o filtro `field == "status"` precisa ser feito no cliente ou via banco/ETL.

### Gaps
- Tamanho de página/truncamento do changelog embutido no DC 9.12/10.x: não confirmado.
- Existência do endpoint `/issue/{key}/changelog` em 10.x: não confirmado (referência REST 10.x não localizada; JRASERVER-59998 sugere que ainda não existe, ticket atualizado em ago/2026).
- Ordem de classificação de `histories` (asc/desc) no DC: não confirmado (a discussão encontrada é sobre Cloud).

## 2. Busca em massa: /rest/api/2/search com expand=changelog

### Takeaway
`GET/POST /rest/api/2/search` aceita `expand=changelog`, `startAt`, `maxResults` (padrão 50) e `fields`. O teto de `maxResults` é a propriedade `jira.search.views.default.max`; acima disso os resultados são truncados.

### Cited Findings
- REF912 `search`: parâmetros `jql`, `startAt` (base 0), `maxResults` (default 50; "The maximum allowable value is dictated by the Jira property 'jira.search.views.default.max'. If you specify a value that is higher than this number, your search results will be truncated"), `validateQuery` (default true), `fields` (padrão: todos os navegáveis), `expand` (lista separada por vírgulas). Para expandir o changelog em todos os issues do resultado é preciso incluir "changelog" em `expand` — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- O valor padrão 1000 de `jira.search.views.default.max` informado no briefing: não verifiquei em fonte oficial neste levantamento — não confirmado.
- A mesma referência traz o esquema de changelog dentro de cada issue do resultado de busca (mesma estrutura de histories/items) — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- Para listas grandes de operandos (WAS IN/WAS NOT IN até 3000 operandos), o GET falha com HTTP 400 no Tomcat acima do limite; Atlassian recomenda `POST /search` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Guia oficial de desempenho de JQL: restringir por projeto reduz verificações de permissão; ordenação e funções JQL com muitos IDs encarecem a busca; `filter IN` são buscas aninhadas e devem ser evitadas — [KB desempenho JQL](https://support.atlassian.com/jira/kb/factors-contributing-to-jql-performance-in-jira-server-and-data-center/)
- Risco de memória/timeout com changelog completo em /search ou /issue — [JRASERVER-59998](https://jira.atlassian.com/browse/JRASERVER-59998)

### Inferences
- Estratégia de extração em massa: JQL com `updated >= "<último corte>"` e `project = X`, `fields=status,created,resolutiondate`, `expand=changelog`, lotes pequenos (por exemplo 50 a 100) com paginação por `startAt` até `total`, filtrando `field == "status"` no cliente; carga incremental por `updated`. É recomendação derivada das fontes acima, não prescrição oficial.
- Se `startAt + maxResults` superar `total`, parar; ordenar por chave estável (`ORDER BY id`/`key`) para evitar duplicatas durante paginação (boa prática, não encontrada em fonte oficial).

### Gaps
- Valor padrão efetivo de `jira.search.views.default.max` (1000?) e se 10.x manteve: não confirmado.
- Guia oficial de desempenho específico para `expand=changelog` em lote: não encontrado.
- Remoção/depreciação de `/rest/api/2/search` em 10.x: não confirmado.

## 3. Worklogs no Data Center

### Takeaway
No DC 9.12 existem `GET /rest/api/2/issue/{key}/worklog`, e os endpoints em massa `GET /rest/api/2/worklog/updated`, `GET /rest/api/2/worklog/deleted` e `POST /rest/api/2/worklog/list` (todos presentes em REF912). Versão de introdução: não confirmado.

### Cited Findings
- `GET /rest/api/2/issue/{issueIdOrKey}/worklog`: "Returns all work logs for an issue. Note: Work logs won't be returned if the Log work field is hidden for the project." A resposta de exemplo contém `startAt`, `maxResults`, `total`, `worklogs[]`, mas a referência não lista parâmetros de consulta de paginação — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- Campos do worklog (esquema): `self`, `author`, `updateAuthor`, `comment`, `created`, `updated`, `visibility {type: group|role, value}`, `started`, `timeSpent`, `timeSpentSeconds`, `id`, `issueId`; usuário: `self, name, key, emailAddress, displayName, active, timeZone` — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- `GET /rest/api/2/worklog/updated?since=<unix ms>` ("since" tipo long, default 0): retorna ids e `updatedTime` dos worklogs atualizados; "limited to 1000 elements"; "will not return worklogs updated during last minute"; resposta com `values[]{worklogId, updatedTime}`, `since`, `until`, `lastPage`/`isLastPage`, `self`, `nextPage` (esquema marca `isLastPage` como required; o exemplo mostra `lastPage`, inconsistência da própria doc) — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- `GET /rest/api/2/worklog/deleted?since=`: mesmos limites (1000, exclui o último minuto); retorna `worklogId` e `updatedTime` de excluídos — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- `POST /rest/api/2/worklog/list` com corpo `{"ids":[1,2,5,10]}`: "Only worklogs to which the calling user has permissions, will be included in the result. The returns set of worklogs is limited to 1000 elements"; HTTP 400 se mais de 1000 ids ou null — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)
- Observação: esses três endpoints ficam em `/rest/api/2/worklog/...` (não sob `/issue/`) — [REF912](https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/)

### Inferences
- Para o dashboard: carga inicial por JQL (`worklogDate`/`updated`) e depois sincronização incremental com `worklog/updated` (guardar `until` como próximo `since`, seguindo `nextPage` até `lastPage`), buscar detalhes com `worklog/list` em lotes de até 1000 ids, e tratar exclusões com `worklog/deleted`.
- Permissões: o permissão exata (Browse Projects, View All Worklogs etc.) não foi verificada; sabe-se só que `worklog/list` filtra pelo que o usuário pode ver.

### Gaps
- Versão em que `worklog/updated`/`list` surgiram no DC: não confirmado.
- Permissões exatas requeridas (página de permissões 404 na tentativa): não confirmado.
- Paginação (`startAt`/`maxResults`) real no `GET issue/worklog` no DC: não documentado nos parâmetros; não confirmado.
- Existência/mudanças em 10.x: não confirmado.

## 4. Autenticação e rate limiting no DC

### Takeaway
PATs existem desde Jira 8.14 (Bearer). Basic auth é desencorajada exceto para scripts pessoais/bots; OAuth é a recomendada para integrações. O rate limiting do DC usa tokens e cabeçalhos X-RateLimit com 429.

### Cited Findings
- PAT: disponível em Jira Core/Software 8.14+ (Service Management 4.15+); uso `Authorization: Bearer <token>`, sem usuário; o token tem as permissões do usuário; padrões: máx. 10 tokens por usuário, expiração máxima 365 dias, nome até 40 caracteres, admin pode alterar por propriedades de sistema; token expirado não reativa; removido após 30 dias por padrão — [Using PATs](https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html)
- Métodos REST: OAuth (recomendado), Basic ("recomendamos que você não use basic authentication, exceto para ferramentas como scripts pessoais ou bots"), cookie — [Security overview](https://developer.atlassian.com/server/jira/platform/security-overview/)
- Basic auth: credenciais a cada requisição; CAPTCHA após falhas de login bloqueia autenticação via REST (header `X-Seraph-LoginReason: AUTHENTICATION_DENIED`) — [Basic auth](https://developer.atlassian.com/server/jira/platform/basic-authentication/)
- Rate limiting (guia "Adjusting your code for rate limiting", documentado para DC 9.11 e versões anteriores listadas, incluindo 8.19/8.22): status 429; cabeçalhos `X-RateLimit-Limit`, `X-RateLimit-Remaining` (pode não ser exato), `X-RateLimit-Interval-Seconds`, `X-RateLimit-FillRate`, `retry-after`; práticas: baixa concorrência, backoff exponencial (começar em 1 s, dobrar, teto como 20 min), respeitar `retry-after` com jitter de até 20%, ou ajuste de taxa lendo os cabeçalhos — [Adjusting your code (9.11)](https://confluence.atlassian.com/adminjiraserver0911/adjusting-your-code-for-rate-limiting-1318891292.html); lista de versões em [busca](https://confluence.atlassian.com/adminjiraserver0819/adjusting-your-code-for-rate-limiting-1085207510.html)
- A página 9.11 não informa a versão de introdução; o briefing indicava 8.20+, mas existe página de 8.19 (URL jirasoftwareserver 0819), o que sugere introdução anterior ou igual a 8.19 — [Adjusting your code (8.19)](https://confluence.atlassian.com/adminjiraserver0819/adjusting-your-code-for-rate-limiting-1085207510.html) (conteúdo dessa página não lido; inferência pelo título/URL)

### Inferences
- Para o BI: usar PAT de uma conta de serviço com permissão de leitura nos projetos, Bearer, concorrência 1 a 2 e tratamento de 429/`retry-after`.

### Gaps
- Versão exata de introdução do rate limiting e página de configuração do admin: não confirmado (tentativa de URL retornou 404).
- Regras de isenção (allowlist) do rate limiting: não confirmado.

## 5. JQL de histórico: sintaxe e semântica

### Takeaway
Operadores de histórico (WAS, WAS IN, WAS NOT, WAS NOT IN, CHANGED) valem apenas para Assignee, Fix Version, Priority, Reporter, Resolution e Status. Predicados: AFTER, BEFORE, BY, DURING, ON (e FROM/TO só com CHANGED). Datas entre aspas; timezone é o do usuário.

### Cited Findings
- WAS: "currently have or previously had a specified value"; predicados `AFTER "date"`, `BEFORE "date"`, `BY "username"`, `DURING ("date1","date2")`, `ON "date"`; casa o nome do valor como estava configurado na data da mudança e também o ID (ex. "4" casa "Resolved"); só para Assignee, Fix Version, Priority, Reporter, Resolution, Status — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Exemplos oficiais: `status WAS "In Progress"`; `status WAS "Resolved" BY jsmith BEFORE "2011/02/20"`; `status WAS "Resolved" BY jsmith DURING ("2010/01/01","2011/01/01")` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- WAS IN: lista entre parênteses; equivale a vários WAS com OR (`status WAS IN ("Resolved","Closed")` = `status WAS "Resolved" OR status WAS "Closed"`); exemplo `status WAS IN ("Resolved","In Progress")`. WAS NOT IN: "never been one of" os valores; equivale a WAS NOT com AND; exemplos `status WAS NOT IN ("Resolved","In Progress")` e `... BEFORE "2011/02/20"` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- WAS NOT: "never had" o valor; exemplos `status WAS NOT "In Progress"` e `status WAS NOT "In Progress" BEFORE "2011/02/20"` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- CHANGED: predicados AFTER, BEFORE, BY, DURING, ON, FROM "oldvalue", TO "newvalue"; exemplos `assignee CHANGED`; `status CHANGED FROM "In Progress" TO "Open"`; `priority CHANGED BY freddo BEFORE endOfWeek() AFTER startOfWeek()` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- WAS IN/WAS NOT IN: até 3000 operandos; acima disso o GET dá HTTP 400 no Tomcat, usar `POST /search` — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Campo Status: "It is safer to search by status ID than status name"; "The WAS, WAS NOT, WAS IN and WAS NOT IN operators can only be used with the name, not the ID" (texto da página FLDCORE/jiracore; contradiz o texto de OPS107, que diz que WAS casa também o ID) — [FLDCORE](https://confluence.atlassian.com/jiracore/advanced-searching-fields-reference-1528201843.html); contradição com [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Resolution: nas tabelas de campos (FLD107 e FLDCORE) Resolution lista WAS/WAS IN/WAS NOT/WAS NOT IN/CHANGED como suportados, e OPS107 também inclui Resolution na lista. Não encontrei, nessas versões, a contradição interna citada no briefing; um pedido de recurso antigo (JRASERVER-63292) diz que CHANGED se limitava a Assignee, Fix Version, Priority, Reporter, Resolution e Status — [FLD107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-fields-reference-1587940830.html), [JRASERVER-63292](https://jira.atlassian.com/browse/JRASERVER-63292) (conteúdo visto só em snippet de busca)
- Campos sem suporte a WAS/CHANGED (exemplos oficiais): affectedVersion, approvals, attachments, category, comment, component, created, updated — [FLD107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-fields-reference-1587940830.html)
- Datas: campos de data aceitam `"yyyy/MM/dd HH:mm"`, `"yyyy-MM-dd HH:mm"`, `"yyyy/MM/dd"`, `"yyyy-MM-dd"` ou relativas com w, d, h, m (default m); usar aspas, senão o número é interpretado como milissegundos desde a época; resultados "relative to your configured time zone (which is by default the Jira instance's time zone)"; sem hora, assume-se meia-noite — [FLD107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-fields-reference-1587940830.html) (isto é a regra para os campos de data como created; que os predicados do WAS/CHANGED herdam o mesmo parser é inferência, não afirmado na página)
- `NOT status CHANGED AFTER -120d` aparece em resposta de comunidade como funcionando, e NOT CHANGED como operador não existe (pedido JRA-26469, Cloud) — [Pedido NOT CHANGED](https://jira.atlassian.com/browse/JRA-26469) (fonte secundária/Cloud; sintaxe `NOT (status CHANGED AFTER "-14d")` em DC: não confirmado)
- Funções de data (startOfMonth(), endOfWeek(), etc.) são aceitas nos exemplos oficiais de CHANGED com BEFORE/AFTER — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Desempenho de JQL: nenhum KB oficial dedicado a WAS/CHANGED encontrado; KB geral cita ordenação, filter IN e funções — [KB desempenho JQL](https://support.atlassian.com/jira/kb/factors-contributing-to-jql-performance-in-jira-server-and-data-center/). Existe recomendação do produto Portfolio sobre `jira.safeguards.indexing.issue.changelogs` acima de 100 aumentar o índice — [Portfolio support](https://support.atlassian.com/portfolio-insights/docs/reduce-the-limit-for-change-history-items/) (relevância para DC: não confirmado)

### JQL validados (sintaxe oficial; semântica de data conforme acima)
```
status WAS "In Progress" DURING ("2026-01-01","2026-01-31")
status WAS "In Progress" BEFORE "2026-01-31"
status CHANGED TO "Done" AFTER startOfMonth()
status CHANGED FROM "In Progress" TO "Done" DURING ("2026-01-01","2026-01-31")
status WAS NOT IN ("Resolved","Closed")
```
- Exemplos com `BEFORE "-30d"` e `NOT (status CHANGED AFTER "-14d")` não aparecem na documentação oficial lida; a relativa com aspas é admitida pela regra de datas, mas o uso nos predicados: não confirmado.

### Inferences
- Issue nunca alterado e `status WAS "status inicial"`: a doc diz "currently have or previously had", então casaria pelo valor atual; se o valor inicial (na criação) conta como histórico quando não há changelog: não confirmado.
- Dashboards: filtros salvos com WAS/CHANGED funcionam como qualquer JQL (a doc não restringe), mas não há orientação oficial de desempenho; testar em volume.

### Gaps
- Se criação conta como valor inicial para WAS; se `NOT (... CHANGED ...)` é válido em DC; timezone exata dos predicados; existência da "contradição sobre Resolution" (não reproduzida nas versões lidas); diferenças específicas 9.12 vs 10.x: todos não confirmados. Páginas do operador para 9.12 e 10.x não foram obtidas (URLs tentadas 404); usei a página jirasoftwareserver107 (Mar/2023).

## 6. Contagem de "vezes em um status" e alternativas

### Takeaway
Nenhuma fonte oficial lida documenta contagem de transições via JQL; os operadores só filtram issues, não contam. Alternativas: extrair changelog via REST e agregar no cliente.

### Cited Findings
- O JQL retorna issues que satisfazem a cláusula, sem contagem de ocorrências; os operadores de histórico só combinam valor/data/autor — [OPS107](https://confluence.atlassian.com/jirasoftwareserver107/advanced-searching-operators-reference-1587940833.html)
- Alternativa documentada (DC): `expand=changelog` e processamento do JSON — [KB changelog](https://support.atlassian.com/jira/kb/how-to-analyze-the-history-or-changelog-of-an-issue-in-jira/); consulta direta ao banco (tabelas `changegroup` e `changeitem`) por admins, citada em resultado de busca sobre o assunto — [Community](https://community.atlassian.com/forums/App-Central-articles/Beyond-JQL-How-to-Query-Reconstruct-and-Export-Jira-Issue/ba-p/3255580) (corpo da página não retornou; apenas snippet, não confirmado)

### Inferences
- Tempo em status = diferença entre `created` de transições consecutivas (e `created` do issue para o primeiro status); contagem = número de itens `field=="status"` com `toString == X`.

### Gaps
- Apps do Marketplace (ex. time in status) não pesquisados.
