# Referência JQL (Jira Data Center) para dashboard de monitoramento de time

Nota de método: as páginas oficiais em confluence.atlassian.com/jirasoftwareserver estão hoje renderizadas como "Jira Software Data Center 11.3" (a mesma página serve 9.x/10.x/11.x; o histórico de versões por item NÃO aparece nelas). Por isso, "desde qual versão" fica como "não confirmado" na maioria dos itens. Leitura feita via extração automática de texto (WebFetch); trechos truncados/garbled estão sinalizados. Rótulos: [OFICIAL] = página de documentação Atlassian; [SECUNDÁRIO] = JAC/community/busca.

Fontes oficiais usadas:
- Campos: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html (318 mil caracteres; li 0–300000, o trecho 100000–200000 e 300000+ veio incompleto)
- Operadores: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html
- Palavras-chave: https://confluence.atlassian.com/jiracoreserver/advanced-searching-keywords-reference-939937720.html
- Funções: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html
- Visão geral: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html
- (URL 939938747 é "Search syntax for text fields", não operadores.)

## Campos (sintaxe, operadores, valores)

### Takeaway
A maioria dos campos pedidos é pesquisável e documentada. NÃO confirmados na documentação oficial lida: `parentEpic`, `aggregatetimespent`/"Σ Time Spent" (fonte secundária indica não pesquisável) e a seção completa de `workRatio`/`level`/`votes` além do que está abaixo.

### Cited Findings
- `status`: operadores `=, !=, IS, IS NOT, IN, NOT IN, WAS, WAS IN, WAS NOT, WAS NOT IN, CHANGED`; sem funções; WAS só funciona com nome, não ID. Ex.: `status = Open`, `status WAS Open` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `statusCategory`: agrupa status em To Do, In Progress, Done; aliases New, Indeterminate, Complete; operadores `=, !=, IN, NOT IN`; ex.: `statusCategory not in ("To Do", "Done")`; categorias não podem ser adicionadas/removidas — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `resolution`: `resolution = unresolved` (ou `resolution is empty`, exemplo na página de operadores); operadores `=, !=, >, >=, <, <=, IS, IS NOT, IN, NOT IN`; a página de campos lista `WAS` e `CHANGED` como NÃO suportados para resolution, mas a página de operadores lista Resolution entre campos de CHANGED/WAS (contradição interna da documentação — testar na instância); recomenda ID em vez de nome — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html); [OFICIAL operators](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html)
- `assignee`: busca por nome completo/ID/e-mail (e-mail entre aspas); `=, !=, IS, IS NOT, IN, NOT IN`, WAS-família e CHANGED; `membersOf()` com IN/NOT IN, `currentUser()` com `=`/`!=`; `~`, `>`... não suportados. `assignee != x` exclui não atribuídas: usar `or assignee is EMPTY` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html); [OFICIAL advanced searching](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html)
- `reporter`: igual a assignee (WAS-família e CHANGED). `creator`: WAS-família sim, CHANGED não — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `issuetype`/`type`: alias `issueType`; só `=`, `!=` na seção lida (IN/NOT IN também são usados em exemplo: `issueType in (Bug,Improvement)`); sem funções; mais seguro por ID — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `parent`: "Sub-tasks de uma issue", só se sub-tarefas estão habilitadas; `=, !=, IN, NOT IN`; sem funções; ex.: `parent = TEST-1234`. Documentação de campos NÃO diz desde qual versão; não aceita filtrar por atributos do pai (só chave) — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html); [SECUNDÁRIO sobre limite](https://jira.atlassian.com/browse/JRASERVER-38798)
- `parentEpic`: NÃO existe entrada na doc oficial lida. Para épico usar `"epic link"` (`=, !=, IS, IS NOT, IN, NOT IN`; aceita nome do épico, chave ou ID; ex.: `"epic link" = Jupiter`). Em 10.x/11.x o campo pode ter outro tratamento: não confirmado — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `duedate` (alias `dueDate`): só data, sem hora; `=, !=, >, >=, <, <=, IS, IS NOT, IN, NOT IN`; funções de data aceitas; relativo só `w` e `d`; ex.: `duedate < now() and resolution is empty` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html); [OFICIAL operators](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html)
- `created` (alias `createdDate`), `updated` (alias `updatedDate`; `=, !=, >, >=, <, <=`), `resolved` (alias `resolutionDate`; IS/IS NOT inclusos): formatos `"yyyy/MM/dd HH:mm"`, `"yyyy-MM-dd HH:mm"`, `"yyyy/MM/dd"`, `"yyyy-MM-dd"`; relativos `w d h m` (padrão `m`); sem hora = meia-noite; usa o fuso configurado; sem aspas o número vira milissegundos desde epoch. Funções: `currentLogin, lastLogin, now, startOf/endOf Day/Week/Month/Year`. Ex.: `resolved > -1h` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `labels`: `=, !=, IS, IS NOT, IN, NOT IN`; `labels is EMPTY` para sem label; `~` não listado — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `project`: nome, chave ou ID; `=, !=, IS, IS NOT, IN, NOT IN`; funções `projectsLeadByUser, projectsWhereUserHasPermission, projectsWhereUserHasRole`; chave tem precedência sobre nome igual — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `originalEstimate` (alias `timeOriginalEstimate`), `remainingEstimate` (alias `timeEstimate`), `timeSpent`: tipo DURATION, só com time tracking ativo; unidades `w d h m`; `=, !=, >, >=, <, <=, IS, IS NOT, IN, NOT IN`; ex.: `originalEstimate > 2d`, `remainingEstimate > 4h`, `timeSpent > 5d` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `aggregatetimespent` / "Σ Time Spent": ausente da doc oficial de campos lida. Pedido [JIRAAUTOSERVER-1231 "Make Aggregate Fields Searchable in JQL"](https://jira.atlassian.com/browse/JIRAAUTOSERVER-1231) (SECUNDÁRIO, apareceu na busca; conteúdo da página não aberto, só o resumo do buscador diz que os campos estão "Searchable: False"). Conclusão provisória: não pesquisáveis em JQL; disponíveis apenas como coluna/REST.
- `worklogAuthor`: USER; `=, !=, IN, NOT IN`; `membersOf()` (IN/NOT IN), `currentUser()` (=/!=); só com time tracking; exige permissão "Time Tracking" salvo seus próprios worklogs; ex.: `worklogAuthor = currentUser()` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `worklogDate`: DATE; `=, !=, >, >=, <, <=, IN, NOT IN`; formatos `"yyyy/MM/dd"`, `"yyyy-MM-dd"`, relativos só `w` e `d` (aspas obrigatórias); funções `currentLogin, lastLogin, now, startOf/endOf Day/Week/Month/Year`; "armazenado como data local no índice"; ex.: `worklogDate > "-1d"`. IMPORTANTE: filtra issues que têm algum worklog na data, não soma horas por pessoa/dia — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `worklogComment`: TEXT; só `~`, `!~` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `sprint`: nome (completo/parcial) ou ID; `=, !=, IS, IS NOT, IN, NOT IN`; funções `openSprints()`, `closedSprints()` (e `futureSprints()` na página de funções); ex.: `sprint is not empty` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `fixVersion`: nome ou ID; `=, !=, >, >=, <, <=, ~, !~, IS, IS NOT, IN, NOT IN`, WAS-família, CHANGED; funções `releasedVersions, latestReleasedVersion, unreleasedVersions, earliestUnreleasedVersion` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `component`: `=, !=, IS, IS NOT, IN, NOT IN`; função `componentsLeadByUser()`; sem WAS/CHANGED — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `lastViewed`: DATE; `=, !=, >, >=, <, <=, IS, IS NOT, IN, NOT IN`; funções de data; ex.: `lastViewed > "-1d"` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `level`: só com segurança de issue ativa; `=, !=, IS, IS NOT, IN, NOT IN`; mais seguro por ID — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `votes`: NUMBER; `=, !=, >=...`; sem funções; `votes is not empty` válido; ex.: `votes >= 12` — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `workRatio`: NUMBER; fórmula `(timeSpent / originalEstimate) x 100`; `=`, `!=` confirmados; lista completa truncada (comparadores `> >= < <=` provavelmente, não confirmado) — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `filter` (aliases `request, savedFilter, searchRequest`): por nome ou ID; só `=, !=, IN, NOT IN`; doc recomenda ID porque nomes mudam; referência circular proibida; "an unnamed link statement in your typed query will override an ORDER BY statement in the saved filter" — [OFICIAL fields](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)

### Inferences
- Para o dashboard, usar `filter = <ID>` e não nome (nomes repetidos/renomeados). A doc não descreve explicitamente o comportamento com nomes duplicados nem permissões do filtro aninhado (usuário sem acesso ao filtro provavelmente recebe erro: não confirmado).
- Para somar horas, JQL só filtra issues; soma deve vir da API (`worklog`/`timespent`) no dashboard.

### Gaps
- Versão de introdução/alteração de `parent`, `statusCategory`, `worklogDate` etc. em 9.12/10.x: não confirmado (release notes não consultadas).
- `aggregatetimespent`: pesquisabilidade só por fonte secundária, não confirmada oficialmente.
- Entradas de `votes`/`workRatio`/`aggregate` na doc oficial (trecho 100000–200000 e 300000+ da página não lido por completo).

## Funções

### Takeaway
Existem: `membersOf`, `standardIssueTypes`, `subtaskIssueTypes`, `startOf/endOf Day/Week/Month/Year`, `now`, `currentUser`, `updatedBy`, `openSprints`, `linkedIssues`, `issuesWithRemoteLinksByGlobalId`, `releasedVersions`. `startOfQuarter` NÃO existe na doc oficial (confirmado por ausência).

### Cited Findings
- Offset: formato `"(+/-)nn(y|M|w|d|h|m)"`; sinal padrão `+`; unidade padrão = período natural da função (`startOfDay` d, `startOfWeek` w, `startOfMonth` M, `startOfYear` y). Ex.: `startOfDay("-3d")`, `startOfMonth("+14d")` = dia 15, `endOfYear("+3M")`. `startOfMonth(-2)` sem aspas: unidade padrão M, logo -2 meses (inferência da regra de unidade padrão; o exemplo literal não está na doc; recomenda-se `startOfMonth("-2M")`) — [OFICIAL functions](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html)
- `startOfQuarter()` / `endOfQuarter()`: não aparecem na página de funções — [OFICIAL functions](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html)
- `startOfWeek`: "the result depends upon your locale" (segunda na Europa, domingo nos EUA, segundo a página) — mesma fonte. O locale é do usuário que executa; conta de serviço do dashboard deve ter locale fixo.
- `now()`, `currentLogin()`, `lastLogin()`: operadores `= != > >= < <=`; predicados WAS/CHANGED só em predicates — mesma fonte.
- `currentUser()`: campos Assignee, Reporter, Voter, Watcher, User; `=`/`!=`; só usuários logados — mesma fonte.
- `membersOf(Group)`: IN/NOT IN em campos de usuário; ex.: `assignee in membersOf("Jira-administrators")` — mesma fonte.
- `standardIssueTypes()` (tipos não-subtask) e `subtaskIssueTypes()` (grafia na página; `subTaskIssueTypes` do pedido não aparece com essa capitalização; sensibilidade a maiúsculas no nome da função não confirmada): campo Type, IN/NOT IN — mesma fonte.
- `updatedBy(user[, dateFrom[, dateTo]])`: campo issuekey, IN/NOT IN; datas `"yyyy/MM/dd"`, `"yyyy-MM-dd"` ou relativas (`"-8d"`); "update" inclui criação, edição de campos e comentários — mesma fonte.
- `openSprints()`, `closedSprints()`, `futureSprints()`: campo Sprint, IN/NOT IN; issue pode estar em sprint fechada e aberta ao mesmo tempo — mesma fonte.
- `linkedIssues(key[, linkType])`: tipo de link case-sensitive; `issuesWithRemoteLinksByGlobalId(id[,...])`: 1 a 100 IDs; `releasedVersions([project])`, `unreleasedVersions`, `latestReleasedVersion(project)`, `earliestUnreleasedVersion(project)`; `issueHistory()` (até 60), `watchedIssues()`, `votedIssues()` — mesma fonte.
- Funções de JSM (SLA, approvals) existem na página; só relevantes com Jira Service Management — mesma fonte.

### Inferences
- Para "mês passado/trimestre" sem startOfQuarter, usar datas absolutas calculadas pelo dashboard ou `startOfMonth("-2M")` e `endOfMonth("-1M")`.

### Gaps
- Versão de introdução de cada função: não confirmado.

## Operadores e palavras-chave

### Takeaway
`CHANGED` só funciona em Assignee, Fix Version, Priority, Reporter, Resolution, Status (doc de operadores). Se a criação conta como mudança: a doc NÃO diz.

### Cited Findings
- CHANGED: predicados `AFTER "date"`, `BEFORE "date"`, `BY "username"`, `DURING ("date1","date2")`, `ON "date"`, `FROM "old"`, `TO "new"`. Exemplos: `status CHANGED FROM "In Progress" TO "Open"`, `priority CHANGED BY freddo BEFORE endOfWeek() AFTER startOfWeek()` — [OFICIAL operators](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html)
- WAS: "currently have or previously had" o valor; predicados AFTER, BEFORE, BY, DURING, ON (sem FROM/TO). `WAS IN` equivale a vários WAS; `WAS NOT` = nunca teve; `WAS NOT IN` ex.: `status WAS NOT IN ("Resolved","In Progress")`; casa também por ID do valor — mesma fonte.
- `NOT`: nega cláusulas individuais ou complexas, ex.: `not (reporter = jsmith or reporter = jbrown)` — [OFICIAL keywords](https://confluence.atlassian.com/jiracoreserver/advanced-searching-keywords-reference-939937720.html). Logo `NOT (status CHANGED AFTER -14d)` é sintaticamente plausível, mas a combinação exata não está na doc; usar `AFTER "-14d"` (aspas) e testar. Alternativa documentada: `NOT status CHANGED AFTER "-14d"` equivalente: não confirmado.
- Criação como mudança: doc oficial silenciosa. Fonte secundária (Cloud, [JRACLOUD-65520](https://jira.atlassian.com/browse/JRACLOUD-65520) via resumo de busca): WAS com predicado anterior à primeira alteração não retorna; sugere o valor inicial não é transição. Não confirmado em DC; recomenda-se testar.
- IS/IS NOT: só com `EMPTY` ou `NULL`; `fixVersion is empty`; nem todo campo suporta — [OFICIAL operators](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html)
- `~`/`!~`: só campos de versão e texto; `!=` não casa campos vazios (`component != fred` exclui sem componente); `field != value` equivale a `NOT field = value` — mesma fonte.
- Relativas: `-14d`, `-1w` aceitas em campos de data (unidades `w d h m`; `y`/`M` só em offsets de funções). `1d` começa à meia-noite do fuso do servidor, diferente de `24h` — [OFICIAL advanced searching](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html)
- ORDER BY: `order by created, priority desc`; vários campos separados por vírgula; ordenar por Components/Versions agrupa por projeto — [OFICIAL keywords](https://confluence.atlassian.com/jiracoreserver/advanced-searching-keywords-reference-939937720.html)
- Precedência: AND antes de OR; parênteses sobrepõem; campos vazios não são retornados salvo `OR campo is EMPTY` — [OFICIAL advanced searching](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html)
- Campos com espaço entre aspas (`"epic link"`); caracteres reservados exigem aspas — mesma fonte.
- Filtro aninhado: ver seção de campos (`filter = ID` recomendado; ORDER BY do filtro é sobrescrito por ORDER BY digitado).
- Comparar dois campos (ex.: duedate da subtask vs. pai): doc oficial atual não declara nem mostra; todos os exemplos usam literal/função. Fonte secundária: [JRASERVER-64403](https://jira.atlassian.com/browse/JRASERVER-64403) (fechado como duplicata de JRASERVER-20727, "Future Consideration", não entregue) e docs antigas de JIRA 4.x ("not possible to compare two fields"). Conclusão: não suportado nativamente; só via apps (ScriptRunner/JSU etc.) — [SECUNDÁRIO](https://jira.atlassian.com/browse/JRASERVER-20727)

### Inferences
- Para "issues sem mudança de status nos últimos 14 dias" a forma mais segura: `statusCategory != Done AND NOT (status CHANGED AFTER "-14d")` ou, alternativamente, `updated < "-14d"` (aproximação, pois updated muda por qualquer edição).

### Gaps
- Máx. tamanho de query/URL e dicas de desempenho para DC: a página "Advanced searching" não os traz; não confirmado (nada oficial encontrado; REST usa POST /search para consultas longas, não verificado).
- Fuso/locale em worklogDate: doc diz que a data é armazenada como data local no índice; efeito do fuso do usuário: não confirmado. `startOfWeek` depende do locale (confirmado).
- Marcação Cloud-only: nada encontrado nas páginas DC que seja Cloud-only entre os itens acima; funções de Cloud como `startOfQuarter` não existem aqui.
