# Referência local: REST API do Jira Data Center

Referência em pt-BR, do básico ao avançado, da **Jira Platform REST v2** (`/rest/api/2`) e da **Jira Software/Agile REST 1.0** (`/rest/agile/1.0`) no **Jira Data Center (DC)**, com foco no que o painel (Kotlin/Ktor, somente leitura) usa ou poderia usar.

- Origem: documentação pública (pesquisa na web em 28/10/2026). **Nenhuma instância de Jira foi acessada.** Todos os exemplos são fictícios.
- Texto parafraseado; confira sempre a fonte indicada em cada entrada.
- Versões lidas: referência oficial **9.12.0** (Platform e Agile), páginas de administração do DC 11.3 (limite de taxa), PAT (8.14+), webhooks (9.x e 10.x). A referência 10.3.0 não abriu (404 na URL testada) e o portal `developer.atlassian.com/server/jira/platform/rest/v11002/` só entregou o menu (conteúdo renderizado por script). Onde não deu para confirmar, a entrada diz **[não confirmado]**.
- Legenda: **DC** = Data Center/Server; **Cloud** = Jira Cloud (não é o nosso alvo).

## Diferenças DC x Cloud que mais pegam

| Tema | DC (nosso alvo) | Cloud |
|---|---|---|
| Identidade de usuário | `name` (username) e `key` (chave interna, ex.: `JIRAUSER10000`); parâmetros `username`/`key` | `accountId` |
| Autenticação | Basic (usuário+senha/token), **PAT Bearer** (8.14+), OAuth 1.0a/2.0, cookie de sessão | Basic com e-mail+API token, OAuth 2.0 |
| Paginação | `startAt`/`maxResults`, resposta com `total` (opcional, pode mudar) | Idem na v2/v3; busca nova usa token (`nextPageToken`) |
| Changelog de uma issue | Só `expand=changelog` em `issue/{key}` ou `search` (ver entrada) | Existe `issue/{key}/changelog` paginado |
| Limite de taxa | Token bucket por usuário e **por nó**, opcional, ligado pelo administrador | Modelo por pontos/cotas |
| Usuários | `/user/search?username=` | `/user/search?query=` |

## Convenções gerais (valem para quase todas as entradas)

- **Base:** `https://<host-do-jira>/<contexto>/rest/api/2/...`. O contexto (`/jira`) existe em algumas instalações.
- **Paginação:** parâmetros `startAt` (a partir de 0) e `maxResults`; respostas paginadas trazem `startAt`, `maxResults`, `total` e uma lista (`issues` na busca, `values` nos endpoints paginados). A documentação avisa: a página pode vir vazia, o servidor pode devolver menos itens que o pedido, e `total` é opcional e pode variar entre chamadas. **Avance pelo número de itens realmente recebidos**, não pelo `maxResults` pedido.
- **Expansão:** `expand=a,b` (lista separada por vírgula; notação de ponto para aninhados). Os nomes expansíveis aparecem na propriedade `expand` do objeto pai.
- **Ordenação:** alguns recursos aceitam `orderBy` (`-campo` = decrescente).
- **Erros:** corpo JSON com `errorMessages` (lista), `errors` (mapa campo -> mensagem) e `status`.
- **CSRF:** endpoints que aceitam `multipart/form-data` só processam com `X-Atlassian-Token: no-check`. Para leitura (GET/POST de busca em JSON) não é necessário.
- **Cabeçalho `X-AUSERNAME`:** a resposta informa o usuário autenticado ou `anonymous`. Útil para detectar credencial que "não pegou" (o DC permite acesso anônimo e não devolve desafio 401 padrão).

---

# 1. Autenticação e cabeçalhos

### Basic (usuário + senha ou token)
**Finalidade:** autenticar chamadas REST enviando `usuário:segredo` em Base64 a cada requisição.
**Requisição:**
```http
GET /rest/api/2/myself
Authorization: Basic <base64(usuario:segredo)>
Accept: application/json
```
**Corpo / resposta (campos que importam):** não há corpo próprio; o cabeçalho vai em toda chamada.
**Observações:** a documentação classifica Basic como a opção menos segura (credencial em toda requisição), adequada a scripts e bots. Como o DC permite acesso anônimo, alguns clientes HTTP só enviam o cabeçalho depois de um desafio que o Jira não emite; envie o cabeçalho de forma preemptiva (o nosso cliente faz isso). **CAPTCHA:** após várias falhas de login seguidas, o Jira passa a exigir CAPTCHA e **a autenticação REST falha** até alguém resolver pela interface; a resposta traz `X-Seraph-LoginReason: AUTHENTICATION_DENIED` (login negado antes de checar a senha). Nunca repita tentativas com credencial errada em laço. Versão: todas.
**Fonte:** https://developer.atlassian.com/server/jira/platform/basic-authentication/

### Bearer com Personal Access Token (PAT)
**Finalidade:** autenticar com um token pessoal revogável, sem expor a senha.
**Requisição:**
```http
GET /rest/api/2/myself
Authorization: Bearer <token-fictício-AbCdEf123>
```
**Corpo / resposta (campos que importam):** nenhum; o usuário é inferido do token. Criação pela interface (perfil > Personal access tokens) ou `POST /rest/pat/latest/tokens` com `{"name":"painel","expirationDuration":90}` (autenticado com Basic ou outro PAT; não cria token para outro usuário).
**Observações:** disponível a partir do **Jira 8.14** (Core/Software). O token aparece uma única vez. Expiração opcional (padrão de sistema: máximo 365 dias, 10 tokens por usuário). O alerta "expira em breve" surge 5 dias antes; token expirado não reativa. O token tem as mesmas permissões do dono (por isso o ideal é um usuário de serviço somente leitura). Administradores com licença DC veem e revogam tokens do site. **Só DC**: no Cloud não existe PAT assim.
**Fonte:** https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html

### Cabeçalhos úteis e o `X-Atlassian-Token`
**Finalidade:** listar os cabeçalhos de requisição e de resposta que importam para um cliente de leitura.
**Requisição:**
```http
GET /rest/api/2/search?jql=project%3DDEMO&maxResults=1
Accept: application/json
Content-Type: application/json
X-Atlassian-Token: no-check
```
**Corpo / resposta (campos que importam):** de resposta: `X-AUSERNAME`, `X-Seraph-LoginReason`, `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Interval-Seconds`, `X-RateLimit-FillRate`.
**Observações:** `X-Atlassian-Token: no-check` só é exigido em upload multipart (anexos); em leitura é dispensável (não o envie sem necessidade). A introdução da referência 9.12 cita também `POST /rest/auth/1/session` para login por cookie (evite: cookie de sessão também entra no limite de taxa e complica o ciclo de vida). Não siga redirecionamentos automaticamente com a credencial (o nosso cliente desliga `followRedirects`).
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

---

# 2. Plataforma (`/rest/api/2`)

### myself
**Finalidade:** validar a credencial e obter o usuário autenticado (teste de conexão).
**Requisição:**
```http
GET /rest/api/2/myself
```
**Corpo / resposta (campos que importam):**
```json
{"name":"usuario.exemplo","key":"JIRAUSER10001","displayName":"Usuária Exemplo","emailAddress":"x@exemplo.invalid","active":true,"timeZone":"America/Manaus"}
```
**Observações:** não permite anônimo (401 se a credencial for inválida). Existem `PUT /myself` (e-mail/nome, exige senha) e `PUT /myself/password`: **nunca** chame (somos somente leitura). DC devolve `name`/`key`; Cloud devolve `accountId`. Versão: 9.12 confirmado.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### serverInfo
**Finalidade:** ler versão, build e hora do servidor (compatibilidade e fuso).
**Requisição:**
```http
GET /rest/api/2/serverInfo?doHealthCheck=false
```
**Corpo / resposta (campos que importam):**
```json
{"baseUrl":"https://jira.exemplo.invalid","version":"9.12.4","versionNumbers":[9,12,4],"deploymentType":"Server","buildNumber":912004,"serverTime":"2026-10-28T10:00:00.000-0400","serverTitle":"Exemplo"}
```
**Observações:** `doHealthCheck=true` roda verificações e custa mais; deixe falso. Use `versionNumbers` para decidir recursos (PAT a partir de 8.14, webhooks com filtros em maiúsculas a partir de 10.x). `serverTime` ajuda a calibrar o JQL de `updated` (o JQL interpreta datas no fuso do usuário/servidor). Pode exigir autenticação conforme a configuração.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### field (campos e custom fields)
**Finalidade:** listar todos os campos visíveis (sistema e personalizados) para mapear nome -> `customfield_NNNNN`.
**Requisição:**
```http
GET /rest/api/2/field
```
**Corpo / resposta (campos que importam):**
```json
[{"id":"customfield_10500","name":"OS Version","custom":true,"orderable":true,"navigable":true,"searchable":true,"clauseNames":["cf[10500]","OS Version"],"schema":{"type":"string","custom":"com.atlassian.jira.plugin.system.customfieldtypes:textfield","customId":10500}}]
```
**Observações:** o id do custom field **muda de uma instância para outra** (por isso o nosso dicionário de campos é por instância). `clauseNames` dá o nome usável no JQL. `schema.custom` diz o tipo (select, multiselect, user, datetime...). Lista só o que o usuário enxerga. `POST /field` cria campo (não usar). Existe também `GET /customFields` (paginado, padrão 50, experimental) e `GET /customFields/{id}/options`.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### status
**Finalidade:** listar os status do workflow e a categoria de cada um.
**Requisição:**
```http
GET /rest/api/2/status
GET /rest/api/2/status/{idOrName}
GET /rest/api/2/status/page?startAt=0&maxResults=100&query=&projectIds=&issueTypeIds=
```
**Corpo / resposta (campos que importam):**
```json
[{"id":"10001","name":"Em Revisão","statusCategory":{"id":4,"key":"indeterminate","name":"In Progress"}}]
```
**Observações:** `status/page` é **experimental** (padrão 100/página). Para saber quais status valem em cada tipo de issue de um projeto, use `project/{key}/statuses`. Nomes de status são locais da instância (por isso o arquivo de vocabulário de filtros). Versão: 9.12.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### statuscategory
**Finalidade:** obter as categorias fixas (To Do, In Progress, Done) que sustentam a regra "concluída".
**Requisição:**
```http
GET /rest/api/2/statuscategory
GET /rest/api/2/statuscategory/{idOrKey}
```
**Corpo / resposta (campos que importam):**
```json
[{"id":2,"key":"new","name":"To Do"},{"id":4,"key":"indeterminate","name":"In Progress"},{"id":3,"key":"done","name":"Done"}]
```
**Observações:** as chaves `new`, `indeterminate` e `done` são estáveis entre instâncias; os nomes exibidos podem ser traduzidos. No JQL: `statusCategory = Done`. Nossa regra de produto (status em "done" **e** `resolution`) usa esta categoria.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### priority
**Finalidade:** listar as prioridades da instância.
**Requisição:**
```http
GET /rest/api/2/priority
GET /rest/api/2/priority/page?startAt=0&maxResults=100
```
**Corpo / resposta (campos que importam):** `[{"id":"2","name":"High","iconUrl":"..."}]`.
**Observações:** `priority/page` é experimental (padrão 100). A ordem retornada reflete a ordem configurada (da mais alta à mais baixa). Detalhes da lista simples: **[não confirmado em 9.12; o texto da página não trouxe o trecho de `GET /priority`]**, mas o recurso é antigo e estável.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### resolution
**Finalidade:** listar as resoluções (Done, Won't Do, Duplicate...) usadas na regra de conclusão.
**Requisição:**
```http
GET /rest/api/2/resolution
GET /rest/api/2/resolution/{id}
GET /rest/api/2/resolution/page?startAt=0&maxResults=100&query=
```
**Corpo / resposta (campos que importam):** `[{"id":"10000","name":"Done","description":"..."}]`; a versão paginada traz `startAt`, `maxResults`, `total`, `isLast`, `nextPage`, `values`.
**Observações:** `resolution/page` é experimental. No JQL, `resolution = Unresolved` (ou `resolution is EMPTY`) separa abertas de resolvidas. 404 se o id não existe ou não é visível.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### issuetype
**Finalidade:** listar tipos de issue (Task, Bug, Sub-task...).
**Requisição:**
```http
GET /rest/api/2/issuetype
GET /rest/api/2/issuetype/{id}
GET /rest/api/2/issuetype/page?startAt=0&maxResults=100&query=&projectIds=
```
**Corpo / resposta (campos que importam):** `[{"id":"10002","name":"Task","subtask":false}]`.
**Observações:** a lista simples devolve só o que o usuário enxerga. `page` é experimental (padrão 100). Os endpoints de criação/alteração/remoção existem; não usar.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### project
**Finalidade:** listar projetos visíveis e ler os dados de um projeto.
**Requisição:**
```http
GET /rest/api/2/project?expand=lead&includeArchived=false
GET /rest/api/2/project/{projectIdOrKey}
GET /rest/api/2/project/{projectIdOrKey}/components
```
**Corpo / resposta (campos que importam):**
```json
[{"id":"10100","key":"DEMO","name":"Projeto Demo","projectTypeKey":"software","lead":{"name":"lider.exemplo"}}]
```
**Observações:** `GET /project` **não é paginado** (só `recent`, máx. 20, `includeArchived`, `browseArchive`); em instâncias grandes a resposta é longa. 404 se o projeto não existe ou não é visível (o Jira não distingue, de propósito).
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### project/{key}/statuses
**Finalidade:** obter, por tipo de issue, os status possíveis do workflow de um projeto.
**Requisição:**
```http
GET /rest/api/2/project/DEMO/statuses
```
**Corpo / resposta (campos que importam):**
```json
[{"id":"10002","name":"Task","subtask":false,"statuses":[{"id":"1","name":"Aberto","statusCategory":{"key":"new"}}]}]
```
**Observações:** sem paginação. Boa fonte para validar o vocabulário de `Filtros_Status_tool.txt` sem varrer issues.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### search (GET e POST)
**Finalidade:** buscar issues por JQL com seleção de campos, expansão e paginação (é o endpoint central do painel).
**Requisição:**
```http
GET  /rest/api/2/search?jql=project%3DDEMO%20AND%20updated%20%3E%3D%20-1d&startAt=0&maxResults=100&fields=key,summary,status&expand=changelog&validateQuery=true
POST /rest/api/2/search
Content-Type: application/json
```
**Corpo / resposta (campos que importam):**
```json
// corpo do POST
{"jql":"project = DEMO ORDER BY updated ASC","startAt":0,"maxResults":100,"fields":["key","summary","status","updated"],"validateQuery":true}
// resposta
{"startAt":0,"maxResults":100,"total":2,"issues":[{"id":"10001","key":"DEMO-1","fields":{"summary":"..."},"changelog":{"startAt":0,"maxResults":2,"total":2,"histories":[]}}]}
```
**Observações:**
- `fields`: lista (no GET, separada por vírgula); `*all`, `*navigable`; prefixo `-` exclui. **Peça só o necessário.**
- `expand`: `changelog` (traz `changelog.histories`), `names`, `schema`, `renderedFields`, `operations`, `editmeta`, `transitions`. **Na referência 9.12 resumida, o corpo do POST lista `jql`, `startAt`, `maxResults`, `fields`, `validateQuery`, sem `expand`** [não confirmado se o POST aceita `expand` no corpo; o nosso cliente envia `"expand":["changelog"]` no POST, o que deve ser validado na primeira conexão, vendo se `changelog` volta na resposta]. No GET, `expand` é parâmetro documentado.
- `validateQuery` (padrão `true`): com JQL inválido devolve 400 com mensagem; `false`/`warn`/`none` mudam o rigor. Mantenha `true` para falhar claramente.
- **Limite de `maxResults`:** padrão 50. O teto é definido pelo administrador (`jira.search.views.default.max` e `jira.search.views.max.limit`); pedir mais que o teto **não dá erro: o servidor devolve menos** (o texto de suporte cita 1000 como valor comum). Por isso a paginação deve avançar pelo que veio. Os valores exatos dependem da instância [não confirmado para a nossa].
- Usar POST quando o JQL for longo (URL grande).
- JQL com `key in (...)` citando chave inexistente responde **400** no DC.
- Paginação profunda por `startAt` pode pular ou repetir issues se houver edições durante a varredura (ordene por `updated ASC, key ASC` e reconcilie).
- Cloud: a busca nova usa `/rest/api/3/search/jql` com token; **não** se aplica ao DC.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/ e https://support.atlassian.com/jira/kb/how-to-use-the-maxresults-api-parameter-for-jira-issue-search-rest-api/

### issue/{key}
**Finalidade:** ler uma issue (campos, e opcionalmente histórico e metadados).
**Requisição:**
```http
GET /rest/api/2/issue/DEMO-1?fields=summary,status,assignee,duedate&expand=changelog,renderedFields,names&properties=*all
```
**Corpo / resposta (campos que importam):**
```json
{"id":"10001","key":"DEMO-1","fields":{"summary":"...","status":{"name":"Aberto"},"assignee":{"name":"usuario.exemplo","key":"JIRAUSER10001"},"duedate":"2026-11-10"},"names":{"duedate":"Due date"},"changelog":{"startAt":0,"maxResults":100,"total":3,"histories":[{"id":"1","author":{"name":"x"},"created":"2026-10-01T09:00:00.000-0400","items":[{"field":"status","fromString":"Aberto","toString":"Em Revisão"}]}]}}
```
**Observações:** `fields` padrão = `*all`. `expand` aceito: `renderedFields`, `names`, `schema`, `transitions`, `operations`, `editmeta`, `changelog`, `versionedRepresentations`. `properties` não vem por padrão (`*all` ou chaves). `names` mapeia id -> nome exibido (útil com custom fields). `renderedFields` devolve HTML (pesado; evitar). **O changelog embutido pode ser truncado** (sua `total` maior que `histories.length`): ver a entrada seguinte. Chaves de issue podem mudar (issue movida), o `id` não.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### issue/{key}/changelog
**Finalidade:** (Cloud) listar o histórico de alterações de uma issue de forma paginada.
**Requisição:**
```http
GET /rest/api/2/issue/DEMO-1/changelog?startAt=0&maxResults=100
```
**Corpo / resposta (campos que importam):** Cloud: `{"startAt":0,"maxResults":100,"total":3,"values":[{"id":"1","author":{...},"created":"...","items":[...]}]}`.
**Observações:** **ALERTA: não consta na referência oficial do DC 9.12.0.** A leitura do trecho de issues (comentários, worklog, transições, propriedades, links...) e uma busca complementar não acharam esse recurso; a documentação diz que o histórico vem por `expand=changelog` em `issue/{key}` ou em `search`. Fontes de comunidade tratam o endpoint paginado como exclusivo do Cloud. Nas versões 10.x/11.x do DC **não foi possível confirmar** (a referência 10.3.0 deu 404 e o portal novo só entregou o menu). **Alternativa segura no DC:** `GET /rest/api/2/issue/{key}?expand=changelog&fields=key,updated` (o changelog embutido na issue; em 9.x a paginação interna do changelog por `expand` não é documentada, então confirme na primeira conexão se vem completo ou truncado em 100). Ver "Como o nosso painel usa" (risco).
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/ (ausência) e https://community.atlassian.com/forums/Jira-questions/Rest-API-limiting-changelog-history-results-to-100-even-if/qaq-p/1466525

### issue/{key}/worklog
**Finalidade:** ler os lançamentos de horas de uma issue.
**Requisição:**
```http
GET /rest/api/2/issue/DEMO-1/worklog
GET /rest/api/2/issue/DEMO-1/worklog/{id}
```
**Corpo / resposta (campos que importam):**
```json
{"startAt":0,"maxResults":1,"total":1,"worklogs":[{"id":"501","author":{"name":"usuario.exemplo"},"started":"2026-10-20T09:00:00.000-0400","timeSpentSeconds":7200,"comment":"..."}]}
```
**Observações:** a resposta tem `startAt/maxResults/total`, mas os parâmetros de paginação desse endpoint **não aparecem na referência 9.12** [não confirmado]. Também há `timetracking` no próprio `fields` da issue, mais barato para totais.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### issue/{key}/comment
**Finalidade:** ler comentários de uma issue.
**Requisição:**
```http
GET /rest/api/2/issue/DEMO-1/comment?startAt=0&maxResults=50&orderBy=created&expand=renderedBody
```
**Corpo / resposta (campos que importam):** `{"startAt":0,"maxResults":50,"total":1,"comments":[{"id":"7","author":{"name":"x"},"body":"texto","created":"..."}]}`.
**Observações:** padrão `maxResults=50`. `renderedBody` só se precisar de HTML. Comentários podem conter dado pessoal: o painel não deve guardá-los sem necessidade.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### issueLink e issueLinkType
**Finalidade:** consultar vínculos entre issues e os tipos de vínculo (relates, blocks, duplicates).
**Requisição:**
```http
GET /rest/api/2/issueLink/{linkId}
GET /rest/api/2/issueLinkType
GET /rest/api/2/issueLinkType/{issueLinkTypeId}
```
**Corpo / resposta (campos que importam):**
```json
{"issueLinkTypes":[{"id":"10000","name":"Blocks","inward":"is blocked by","outward":"blocks"}]}
```
**Observações:** não existe "listar links" geral; os vínculos de uma issue vêm no campo `issuelinks` de `fields` (cada item com `type`, `inwardIssue` ou `outwardIssue`). POST/DELETE em `issueLink` alteram dados (proibido para nós).
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### user, user/search, user/assignable/search
**Finalidade:** resolver pessoas (username/key/nome/e-mail) e saber quem pode receber uma issue.
**Requisição:**
```http
GET /rest/api/2/user?username=usuario.exemplo
GET /rest/api/2/user?key=JIRAUSER10001&includeDeleted=false
GET /rest/api/2/user/search?username=exemplo&startAt=0&maxResults=50&includeActive=true&includeInactive=false
GET /rest/api/2/user/assignable/search?project=DEMO&username=exemplo&maxResults=50
```
**Corpo / resposta (campos que importam):** `{"name":"usuario.exemplo","key":"JIRAUSER10001","displayName":"...","active":true,"timeZone":"America/Manaus"}`.
**Observações:** DC usa `username`/`key` (Cloud: `accountId`). `user/search`: padrão 50, máximo 1000; casa por username, nome ou e-mail. `user/assignable/search`: máximo 100; exige `project` ou `issueKey` (senão 400). `user/picker` (padrão 50, máx. 1000) é para autocompletar. Para o painel, o `key` (`JIRAUSERnnnnn`) aparece em históricos antigos de `assignee`, por isso o mapa chave -> username. O e-mail só aparece conforme a visibilidade do perfil.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### filter e filter/favourite
**Finalidade:** ler filtros salvos (JQL reaproveitável) do usuário.
**Requisição:**
```http
GET /rest/api/2/filter/favourite
GET /rest/api/2/filter/{id}
GET /rest/api/2/filter/my   [não confirmado em 9.12]
```
**Corpo / resposta (campos que importam):** `{"id":"12345","name":"Fila do time","jql":"project = DEMO AND resolution = Unresolved","owner":{"name":"x"},"searchUrl":"...","viewUrl":"..."}`.
**Observações:** a referência não lista `maxResults` para filtros (sem paginação). `expand` existe (`subscriptions`, `sharePermissions`) [valores não listados na referência]. Para listar filtros de outros, só pela interface/outros caminhos; no DC não há um "buscar filtros" público confirmado. PUT/POST/DELETE alteram dados (proibido).
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### jql/autocompletedata e suggestions
**Finalidade:** descobrir campos, funções e palavras reservadas do JQL, e valores sugeridos para um campo.
**Requisição:**
```http
GET /rest/api/2/jql/autocompletedata
GET /rest/api/2/jql/autocompletedata/suggestions?fieldName=status&fieldValue=Em
```
**Corpo / resposta (campos que importam):** `{"visibleFieldNames":[{"value":"status","displayName":"status","orderable":"true","searchable":"true","operators":["=","!="]}],"visibleFunctionNames":[...],"jqlReservedWords":[...]}`; sugestões: `{"results":[{"value":"Em Revisão","displayName":"<b>Em</b> Revisão"}]}`.
**Observações:** `suggestions` aceita `fieldName`, `fieldValue`, `predicateName`, `predicateValue` (predicados só para `by`, `from`, `to`). Útil para validar o vocabulário (status, labels, componentes) sem varrer issues. **`jql/parse` não consta na referência 9.12** (existe no Cloud); no DC, valide o JQL com `search?validateQuery=true&maxResults=0` [maxResults=0 não confirmado: use 1].
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### versions e components
**Finalidade:** ler versões e componentes de um projeto (útil para "OS Version"/releases).
**Requisição:**
```http
GET /rest/api/2/project/DEMO/versions
GET /rest/api/2/project/DEMO/components
GET /rest/api/2/version/{id}
GET /rest/api/2/component/{id}
GET /rest/api/2/component/page?projectIds=10100&startAt=0&maxResults=100
```
**Corpo / resposta (campos que importam):** `{"id":"10200","name":"1.4.0","released":false,"releaseDate":"2026-12-01"}`; componente: `{"id":"10300","name":"API","lead":{...}}`.
**Observações:** `component/page` é experimental (padrão 100). `project/{key}/versions` e `version/{id}` **[não confirmados em 9.12 pela leitura parcial]**, mas são recursos clássicos. POST/PUT/DELETE alteram dados.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### dashboard
**Finalidade:** listar os dashboards do Jira visíveis ao usuário (inventário, não dados).
**Requisição:**
```http
GET /rest/api/2/dashboard?filter=favourite&startAt=0&maxResults=20
GET /rest/api/2/dashboard/{id}
```
**Corpo / resposta (campos que importam):** `{"startAt":0,"maxResults":20,"total":1,"dashboards":[{"id":"10400","name":"Time","view":"...","isFavourite":true}]}`.
**Observações:** a doc avisa que o servidor pode devolver menos que `maxResults`; olhe o valor devolvido. O conteúdo dos gadgets não é exposto como dado. Baixa utilidade para o painel.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### Outros de leitura úteis (descobertos)
**Finalidade:** apoios de diagnóstico e administração somente leitura.
**Requisição:**
```http
GET /rest/api/2/mypermissions?projectKey=DEMO
GET /rest/api/2/application-properties?key=jira.search.views.default.max
GET /rest/api/2/configuration
GET /rest/api/2/group/member?groupname=time-exemplo&startAt=0&maxResults=50
GET /rest/api/2/worklog/updated?since=<epoch-ms>   [não confirmado em 9.12]
```
**Corpo / resposta (campos que importam):** `mypermissions` -> `{"permissions":{"BROWSE_PROJECTS":{"havePermission":true}}}`; `group/member` -> `values` com usuários.
**Observações:** `mypermissions` mostra o que a credencial pode (confirma "somente leitura" na prática). `application-properties` pode revelar limites de busca (muitas chaves exigem admin). `group/member`: padrão e máximo 50. `/auditing/record` é **obsoleto**. `/worklog/updated` e `/worklog/deleted` **não apareceram** na leitura de 9.12.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

---

# 3. Jira Software / Agile (`/rest/agile/1.0`) - Jira Software 9.12

Convenção: respostas paginadas trazem `values`, `startAt`, `maxResults`, `isLast` e `total` (opcional). **Há relato de bug no DC (JSWSERVER-15503) de que `maxResults` acima de 50 é ignorado nos endpoints agile**; o relato lido não dizia se foi corrigido. Avance pelo que vier. Relato de suporte (set/2026) indica, para Jira Software 11.3.12+, um teto de resultados nas buscas de issues de quadro (`jira.search.platform.max.result.window`, padrão 10.000) [não confirmado na nossa versão].

### board
**Finalidade:** listar e ler quadros (Scrum/Kanban).
**Requisição:**
```http
GET /rest/agile/1.0/board?startAt=0&maxResults=50&type=scrum&name=time&projectKeyOrId=DEMO
GET /rest/agile/1.0/board/{boardId}
```
**Corpo / resposta (campos que importam):** `{"values":[{"id":12,"name":"Quadro Demo","type":"scrum","location":{"projectKey":"DEMO"}}],"isLast":true}`.
**Observações:** `type` = `scrum`/`kanban`/`simple`. Só quadros visíveis ao usuário. POST/DELETE criam/apagam quadros (proibido).
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

### board/{id}/issue
**Finalidade:** listar as issues que aparecem num quadro (respeita o filtro do quadro).
**Requisição:**
```http
GET /rest/agile/1.0/board/12/issue?startAt=0&maxResults=50&jql=assignee%3D%22usuario.exemplo%22&validateQuery=true&fields=key,summary,status&expand=changelog
```
**Corpo / resposta (campos que importam):** `{"startAt":0,"maxResults":50,"total":2,"issues":[{"key":"DEMO-1","fields":{...}}]}`.
**Observações:** parâmetros: `startAt`, `maxResults`, `jql` (refina), `validateQuery`, `fields`, `expand`. O resultado é a interseção do filtro do quadro com o `jql` passado. Para o painel, a busca por JQL é mais previsível.
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

### board/{id}/sprint e sprint/{id}/issue
**Finalidade:** listar sprints de um quadro e as issues de uma sprint.
**Requisição:**
```http
GET /rest/agile/1.0/board/12/sprint?startAt=0&maxResults=50&state=active,future
GET /rest/agile/1.0/sprint/{sprintId}
GET /rest/agile/1.0/sprint/{sprintId}/issue?startAt=0&maxResults=50&jql=&fields=key,status&expand=
```
**Corpo / resposta (campos que importam):** `{"values":[{"id":34,"state":"active","name":"Sprint 12","startDate":"2026-10-19T12:00:00.000Z","endDate":"2026-11-02T12:00:00.000Z","goal":"..."}]}`.
**Observações:** `state` = `future`, `active`, `closed` (lista separada por vírgula). Quadros Kanban não têm sprints (a chamada retorna erro 400). Criar/alterar sprint (POST/PUT) e mover issues (POST) são escrita: proibido.
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

### board/{id}/configuration
**Finalidade:** ler a configuração do quadro (colunas -> status, filtro, estimativa, ranking).
**Requisição:**
```http
GET /rest/agile/1.0/board/12/configuration
```
**Corpo / resposta (campos que importam):** `{"id":12,"name":"Quadro Demo","filter":{"id":"12345"},"columnConfig":{"columns":[{"name":"Fazendo","statuses":[{"id":"3"}]}]},"estimation":{"type":"field","field":{"fieldId":"customfield_10600"}},"ranking":{"rankCustomFieldId":10700}}`.
**Observações:** sem parâmetros. A tabela coluna -> status é a melhor fonte do fluxo "como o time enxerga" o andamento, e `filter.id` leva ao JQL do quadro via `filter/{id}`.
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

### epic e board/{id}/epic
**Finalidade:** ler épicos e suas issues.
**Requisição:**
```http
GET /rest/agile/1.0/epic/{epicIdOrKey}
GET /rest/agile/1.0/epic/{epicIdOrKey}/issue?startAt=0&maxResults=50&jql=&fields=
GET /rest/agile/1.0/epic/none/issue
GET /rest/agile/1.0/board/12/epic?startAt=0&maxResults=50&done=false
GET /rest/agile/1.0/board/12/epic/{epicId}/issue
```
**Corpo / resposta (campos que importam):** `{"id":77,"key":"DEMO-9","name":"Épico","done":false}`.
**Observações:** `epic/none/issue` devolve as issues sem épico. No JQL clássico do DC o vínculo é `"Epic Link" = DEMO-9`; o campo é custom e varia por instância.
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

### board/{id}/backlog
**Finalidade:** listar as issues do backlog de um quadro Scrum.
**Requisição:**
```http
GET /rest/agile/1.0/board/12/backlog?startAt=0&maxResults=50&jql=&validateQuery=true&fields=key,summary&expand=
```
**Corpo / resposta (campos que importam):** igual a `board/{id}/issue`.
**Observações:** só Scrum (Kanban não tem backlog separado). `POST /backlog/issue` move issues ao backlog (escrita, proibido).
**Fonte:** https://docs.atlassian.com/jira-software/REST/9.12.0/

---

# 4. Webhooks (eventos de issue)

### Webhooks do DC
**Finalidade:** receber notificações push de mudanças em vez de consultar periodicamente.
**Requisição:**
```http
POST /rest/webhooks/1.0/webhook          (9.x)
POST /rest/jira-webhook/1.0/webhooks     (10.x, segundo a página)
GET  /rest/webhooks/1.0/webhook
```
**Corpo / resposta (campos que importam):**
```json
{"name":"painel-exemplo","url":"https://painel.exemplo.invalid/hook","events":["jira:issue_created","jira:issue_updated","jira:issue_deleted"],"filters":{"issue-related-events-section":"project = DEMO"},"excludeBody":false}
```
Entrega: `webhookEvent`, `timestamp`, `issue` (sempre), `user`, e `changelog` só em `jira:issue_updated`; `comment` quando há comentário.
**Observações:** **exige permissão de administrador do Jira** (a conta somente leitura do painel **não** poderia registrar). Eventos de issue: `jira:issue_created`, `jira:issue_updated`, `jira:issue_deleted`, `jira:worklog_updated` (inclui exclusão). Há também eventos de link, comentário, projeto, versão, sprint, quadro. O filtro JQL roda a cada evento (campos personalizados lentos pesam). Entrega assíncrona a partir do DC 10.0.0; HTTP 200 = sucesso, qualquer outro código conta como falha; a página **não descreve reentrega automática** nem **segredo/HMAC**. No 10.x o formato dos campos em `configuration` muda (`FILTERS`, `DESCRIPTION`, `EXCLUDE_BODY` em maiúsculas). Um receptor público exige tratar autenticidade por conta própria. Versão: 9.x e 10.x.
**Fonte:** https://developer.atlassian.com/server/jira/platform/webhooks/

---

# 5. Erros comuns, limite de taxa e CAPTCHA

### Códigos HTTP frequentes
**Finalidade:** saber como reagir a cada resposta.
**Requisição:**
```http
(qualquer chamada)
```
**Corpo / resposta (campos que importam):** `{"errorMessages":["..."],"errors":{},"status":400}` (nem todo erro tem corpo JSON; páginas HTML de login ou proxy aparecem em 200/302/5xx).
**Observações:**
- **400:** JQL inválido (`validateQuery`), `key in (...)` com chave inexistente, parâmetro faltando (`user/assignable` sem `project`).
- **401:** credencial inválida/expirada (PAT vencido). Não repetir em laço (risco de CAPTCHA no Basic).
- **403:** sem permissão ou **CAPTCHA** exigido (`X-Seraph-LoginReason`); também permissão de projeto.
- **404:** recurso inexistente **ou invisível** (issue sem permissão responde 404); ou endpoint inexistente na versão (ex.: `changelog` no DC).
- **429:** limite de taxa; respeitar `Retry-After`.
- **5xx/502/503/504:** instabilidade ou proxy; repetir com espera.
- **302/redirecionamento:** URL base errada ou SSO na frente; não siga com a credencial.
**Fonte:** https://docs.atlassian.com/software/jira/docs/api/REST/9.12.0/

### Limite de taxa do DC (rate limiting)
**Finalidade:** entender e respeitar o controle de carga do servidor.
**Requisição:**
```http
(qualquer chamada; os cabeçalhos vêm na resposta)
```
**Corpo / resposta (campos que importam):** cabeçalhos `X-RateLimit-Limit` (capacidade do balde), `X-RateLimit-Remaining` (fichas restantes, nem sempre exato), `X-RateLimit-Interval-Seconds`, `X-RateLimit-FillRate` (fichas por intervalo) e `Retry-After` (segundos; `0` = pode seguir).
**Observações:** limita só requisições **externas** de REST autenticadas por Basic, OAuth ou cookie `JSESSIONID`. Modelo de balde de fichas: 1 ficha = 1 requisição; reabastece a taxa constante; "max requests" é a capacidade (permite rajada após ociosidade). Sem fichas: **429**. O administrador liga em Administração > Sistema > Limite de taxa (o padrão não está declarado; assuma que pode estar ligado). Há exceções por usuário (ilimitado, bloquear, personalizado; levam até 1 min), lista de URLs e de consumidores OAuth. **Vale por nó, não pelo cluster.** Estratégias recomendadas pela Atlassian: (1) backoff exponencial (começar em ~1 s, dobrar; teto a definir); (2) esperar o `Retry-After` (+ até 20% de variação aleatória); (3) ajustar a taxa lendo todos os cabeçalhos, que é a de menor impacto e indicada para alto volume. Mantenha concorrência baixa (o painel é sequencial). Versões: o recurso existe desde o DC 8.6 (anúncio da comunidade), a página lida é a do 11.3.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/improving-instance-stability-with-rate-limiting-983794911.html e https://confluence.atlassian.com/adminjiraserver/adjusting-your-code-for-rate-limiting-987143384.html

### Boas práticas de carga
**Finalidade:** reduzir o impacto no Jira de produção e a chance de bloqueio.
**Requisição:**
```http
POST /rest/api/2/search   {"jql":"project = DEMO AND updated >= \"2026-10-28 09:55\" ORDER BY updated ASC, key ASC","fields":["key","updated","status"],"maxResults":100}
```
**Corpo / resposta (campos que importam):** n/a.
**Observações:**
1. **Paginar** sempre e avançar por itens recebidos; ordenar de forma estável (`updated ASC, key ASC`).
2. **`fields` mínimos**; evite `*all`, `renderedFields` e `expand` pesado onde não precisar.
3. **Incremental** com `updated >= cursor - sobreposição` (alguns minutos): o JQL de data tem resolução de **minuto** e no fuso do usuário/servidor, então a sobreposição evita perder edições do mesmo minuto; o armazenamento deve ser idempotente (upsert).
4. **Reconciliar** de tempos em tempos (lista leve `key`+`updated`+`id`): detecta issues apagadas, movidas (mesmo `id`, outra chave) e itens pulados pela paginação por `startAt`.
5. **Intervalos** generosos (minutos), um job por vez, **orçamento de requisições** por execução e teto de itens.
6. Trate `Retry-After` e 429; **suspenda** em 401/403 (evita CAPTCHA e bloqueio de conta).
7. Prefira horários de menor uso para cargas grandes; não paralelize.
8. Cache de dados estáveis (status, campos, prioridades, resoluções): buscar poucas vezes ao dia.
**Fonte:** https://support.atlassian.com/jira/kb/how-to-use-the-maxresults-api-parameter-for-jira-issue-search-rest-api/ e https://confluence.atlassian.com/adminjiraserver/adjusting-your-code-for-rate-limiting-987143384.html

---

# 6. Como o nosso painel usa

Código lido: `app/backend/src/main/kotlin/br/sidia/painel/jira/` (`JiraClient.kt`, `JiraModels.kt`, `OrcamentoDeRequisicoes.kt`, `JiraInstanceConfig.kt`) e `.../sync/` (`SyncService.kt`, `Syncer.kt`, `SyncSettings.kt`), além de `jql/CardQueryFactory.kt`.

| Endpoint / tema | Onde está no código | Como é usado |
|---|---|---|
| `POST /rest/api/2/search` | `JiraClient.search(SearchRequest)`; `searchAll` (paginação); `count` (`maxResults=1`, lê `total`) | Corpo JSON com `jql`, `maxResults`, `startAt`, `fields` (obrigatório, nunca vazio) e `expand:["changelog"]` quando a ficha pede. `pageSize` de 1 a 5000 (padrão 1000), `maxItens` até 200.000 (padrão 50.000; teto estoura `TetoDeItensExcedidoException`). Avança por `issues.size` recebido e para em página vazia; exige `total` numérico. |
| `expand=changelog` no search | `Syncer.embutidos`/`truncado` | Usa o changelog embutido; se faltar ou `total` > embutido, considera **truncado**. |
| `GET /rest/api/2/issue/{key}/changelog` | `JiraClient.fullChangelog` (páginas de 100, teto de 10.000 itens) | Chamado pelo `Syncer` só quando o changelog embutido está truncado. **RISCO:** esse endpoint **não consta na referência do DC 9.12** (ver entrada). Se não existir na instância, a resposta será 404 e o `JiraException(404)` interromperá a sincronização com `HTTP_404`. Validar na primeira conexão (checklist) e, se confirmado, trocar por `issue/{key}?expand=changelog` ou, para issues muito longas, tratar como "incompleta". |
| Autenticação Basic/Bearer | `JiraClient.authorization`, `JiraAuthKind` (`BEARER`, `BASIC`) | `Authorization: Bearer <PAT>` ou `Basic base64(usuario:token)`; `Accept: application/json`; sem redirecionamento; segredos mascarados em mensagens de erro (inclusive formas escapadas). |
| 429, 502, 503, 504 e falhas de rede | `JiraClient.executarComRetry`, `RetryPolicy` | Até 5 novas tentativas; espera pelo `Retry-After` (limitado a 120 s) ou backoff exponencial até 30 s. O guard (`LiveGuard`) e o orçamento são consultados antes de **cada** tentativa. Não lê os cabeçalhos `X-RateLimit-*`. |
| Orçamento de requisições | `OrcamentoDeRequisicoes`, `SyncSettings.maxRequisicoes` (2.000) | Teto por execução, inclui as repetições. |
| Incremental com sobreposição | `SyncService.sincronizar`, `SyncSettings.sobreposicao` (5 min), `intervalo` (10 min), `fusoDoServidor` (America/Manaus) | `desde = max(cursor - sobreposição, limite de retenção)` aplicado ao JQL via `AtualizadoDesde`; cursor só avança após gravar a issue. |
| Reconciliação | `SyncService.reconciliar`/`confirmarRemocoes`/`listaLeve` | Lista leve (`key`, `updated`, `id` no topo) para detectar removidas e movidas; confirma por `key in (...)` em lotes de 100; trata o **400** do DC para chave inexistente consultando uma a uma. Aborta se a remoção passar da fração limite. |
| `fields` mínimos | `CardQueryFactory` (ex.: `issuelinks` entre os mínimos; lista leve = `key`,`updated`) | Pede só o necessário por ficha. |
| Suspensão por status | `SyncService.fechar` | 401/403/429 suspendem a instância até reiniciar; 3 falhas seguidas do incremental também (evita CAPTCHA e martelar o servidor). |
| Chaves de usuário (`key`/`name`) | `ChavesDeUsuario`, `RepositorioDeChaves` | Mapeia a `key` interna (`JIRAUSER...`) do changelog para o username, extraída das issues recebidas; não chama `user`. |

Não há hoje chamadas a `myself`, `serverInfo`, `field`, `status`, `priority`, `resolution`, `project`, `user`, `filter`, `jql/autocompletedata`, nem a nada de `/rest/agile/1.0` ou webhooks (confirmado por busca de `rest/api` e `rest/agile` no backend: só `search` e `changelog`).

# 7. O que ainda não usamos mas poderia

1. **`myself` e `serverInfo`** no teste de conexão e no checklist da primeira conexão: validam credencial, versão (decide PAT, webhooks, formato) e hora do servidor (calibra `updated >=`). Baixo custo, alto ganho.
2. **`field`** para descobrir o `customfield_NNNNN` de "OS Version" e demais campos por instância, em vez de depender só de dicionário manual; `names` com `expand` na busca como alternativa.
3. **`status`, `statuscategory`, `resolution`, `priority`, `project/{key}/statuses`** para validar o vocabulário dos arquivos de filtro e a regra "concluída = categoria done + resolution", detectando divergências (Qualidade dos dados) sem varrer issues.
4. **`mypermissions`** para comprovar que a credencial é somente leitura e que enxerga os projetos esperados.
5. **Cabeçalhos `X-RateLimit-*` / `Retry-After`** (estratégia 3 da Atlassian): ajustar o ritmo antes do 429, em vez de só reagir.
6. **`jql/autocompletedata/suggestions`** para checar valores de labels/status/componentes antes de rodar o JQL.
7. **Agile (`board/{id}/configuration`, `sprint`)** para o mapa coluna -> status e o calendário de sprints, se o time passar a medir por sprint.
8. **`issue/{key}?expand=changelog`** como substituto do `fullChangelog` caso o endpoint paginado não exista (ver risco acima).
9. **`user/search`** para resolver `key` -> username quando o mapa estiver incompleto (`usuariosNaoResolvidos`), com `maxResults` pequeno e cache.
10. **Webhooks** (`jira:issue_updated`) para reduzir a latência, mas exigem conta administradora, receptor acessível e autenticidade própria: provavelmente fora do escopo por ora.
11. **`worklog` / `timetracking`** para carga por pessoa, se o produto vier a precisar de horas lançadas.

---

# Lacunas e pontos a confirmar na primeira conexão

- Existência de `issue/{key}/changelog` no DC da empresa (versão exata via `serverInfo`).
- Se o `POST /search` aceita `expand` no corpo (o código assume que sim).
- Teto real de `maxResults` da instância (`jira.search.views.default.max` / `max.limit`) e se o limite de taxa está ligado e com quais valores.
- Paginação do `worklog`, `priority` simples, `version`, `jql/parse`, `filter/my`, `worklog/updated`: não confirmados na leitura parcial da referência 9.12.
- Referências 10.x e 11.x do DC: portal novo (`developer.atlassian.com/server/jira/platform/rest/v11002/`) só retornou o menu; `docs.atlassian.com/.../10.3.0/` deu 404.
