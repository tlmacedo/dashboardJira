# Tempo, worklog e estimativas no Jira Data Center (JQL e relatórios)

Data de referência: 2026. Alvo: Jira DC 9.12 LTS e 10.x. Observação: nos resultados apareceram listagens de Marketplace com Jira DC 11.x, então a linha 11.x já existe em 2026.
Limitação da pesquisa: a página de referência de campos do JQL (Server/DC) é enorme e a ferramenta de leitura só devolveu trechos. A seção dos campos Σ não foi extraída dessa página. Tudo o que está marcado "não confirmado" não foi verificado em fonte primária.

## 1. Campos de tempo: significado, busca em JQL, soma

### Takeaway
`timeSpent`, `originalEstimate`, `remainingEstimate` e `workRatio` são pesquisáveis em JQL. Os campos Σ (agregados) existem para exibição, mas são marcados como não pesquisáveis. Nenhum deles pode ser SOMADO por JQL: JQL só filtra issues.

### Cited Findings
- `timeSpent` (DURATION) exige time tracking habilitado. Semântica: "issues em que o time spent é igual a um valor". Unidades `w`, `d`, `h`, `m`. Exemplo oficial: `timeSpent > 5d` — [Fields reference (Server/DC)](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `originalEstimate` (alias `timeOriginalEstimate`) e `remainingEstimate` (alias `timeEstimate`), tipo DURATION, sem autocomplete. Operadores `=, !=, >, >=, <, <=, IS, IS NOT, IN, NOT IN`. Os operadores `~`, `!~`, `WAS` e `CHANGED` não são suportados. Exemplos: `originalEstimate > 2d`, `remainingEstimate > 4h` — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- Unidades no JQL são `w/d/h/m`. Segundos não são mencionados na página como formato de entrada de JQL. A conversão padrão é 1w = 5d e 1d = 8h, ajustável nas configurações — [Logging work](https://confluence.atlassian.com/jirasoftwareserver/logging-work-on-issues-939938944.html); [Configuring time tracking](https://confluence.atlassian.com/adminjiraserver/configuring-time-tracking-938847808.html)
- `workRatio` (NUMBER, operadores listados `=` e `!=` na página; o restante da seção foi cortado na leitura). Fórmula: `workRatio = (timeSpent / originalEstimate) x 100` — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html). A KB do Atlassian mostra `workratio > 100` mas declara aplicar-se somente ao Cloud, não a Server/DC — [KB](https://support.atlassian.com/jira/kb/how-to-search-for-issues-where-the-time-spent-is-more-than-the-original-estimate/). A KB também diz que só funciona com os campos nativos.
- Se Original Estimate = 0 (e não vazio), registrar trabalho gera work ratio gigantesco (bug aberto) — [JRA-26751 / JRA-60194 citados em busca](https://jira.atlassian.com/browse/JRA-60194)
- Work ratio vazio no pai quando só as subtarefas têm valores (bug reportado, não resolvido nos resultados) — [JRASERVER-28961](https://jira.atlassian.com/browse/JRASERVER-28961)
- Σ Time Spent (`aggregatetimespent`) aparece em listas e dashboards somando subtarefas ao pai, mas o campo está marcado "Searchable: False". Sugestão "Make Aggregate Fields Searchable in JQL" está em "Gathering Interest", sem versão de correção, aberta em 24/07/2026 no projeto JIRAAUTOSERVER (o ticket cita Jira Server, não menciona DC explicitamente) — [JIRAAUTOSERVER-1231](https://jira.atlassian.com/browse/JIRAAUTOSERVER-1231)
- Σ Original/Remaining/Time Spent não aparecem no export XML nem na macro Jira Issues quando a issue não tem subtarefas, mas o REST `/rest/api/2/search` devolve `aggregatetimeoriginalestimate` (ex.: 7200) para uma issue sem subtarefas. Bug "Gathering Impact", baixa prioridade — [JRASERVER-21182](https://jira.atlassian.com/browse/JRASERVER-21182)
- JRASERVER-961 e JRASERVER-3676 ("Enable searching of issues based on Time Tracking values"): fechados, sem declaração útil da Atlassian na página — [JRASERVER-961](https://jira.atlassian.com/browse/JRASERVER-961)
- `Time Tracking` por intervalo de datas não é suportado no campo (só valor) — [JRA-34527](https://jira.atlassian.com/browse/JRA-34527) (resultado de busca, página não aberta)

### Inferences
- Para `timespent > 0` e `originalEstimate is EMPTY`: `IS EMPTY` consta dos operadores listados, então `originalEstimate is EMPTY` é sintaxe válida. Não encontrei exemplo oficial literal.
- Não encontrei evidência de alias JQL para `aggregatetimespent`; como o campo é não pesquisável, filtros devem usar apenas campos próprios (`timeSpent`) e somar fora do JQL (REST, export, eazyBI).

### Gaps
- Texto oficial das seções Σ Original Estimate / Σ Remaining Estimate / Σ Time Spent da página de referência JQL: não confirmado (página truncada na leitura). Vale abrir manualmente.
- Se `workRatio` aceita `>`/`<` em DC 9.12/10.x: não confirmado (a página lista `=`/`!=` no trecho lido; a KB com `>` é de Cloud).
- Aceitação de segundos como valor em JQL: não confirmado.

## 2. Agregação Σ nas subtarefas e evitar dupla contagem

### Takeaway
Σ = tempo do próprio pai + subtarefas (segundo fontes secundárias). Para "só tarefas" use `timeSpent` no filtro de tarefas (próprio tempo); para "só subtarefas" `timeSpent` com `issuetype in subTaskIssueTypes()`; para "combinado" some `timeSpent` de tarefas + subtarefas (nunca Σ do pai mais as subtarefas).

### Cited Findings
- Doc oficial: "Choosing to include sub-tasks displays the aggregated time of an issue and all its sub-tasks." Sem fórmula nem notação Σ na página — [Logging work](https://confluence.atlassian.com/jirasoftwareserver/logging-work-on-issues-939938944.html)
- Exemplo comunitário: história com 4h própria e subtarefas 3+2+1h dá Σ = 10h (fonte secundária) — [Atlassian Community](https://community.atlassian.com/forums/discussion/3033062/understanding-time-spent-vs-%CE%A3-time-spent-in-jira)
- Bug antigo (Jira 5.2.6): Σ Original Estimate do pai inclui o estimate do próprio pai — [JRA-31770](https://jira.atlassian.com/browse/JRA-31770) (listado em busca, não aberto)
- No CSV, a coluna "Time Spent" traz só o tempo direto; para o rollup é preciso adicionar "Σ Time Spent" ao navegador antes de exportar (KB marcada como Cloud em uma versão) — [KB](https://support.atlassian.com/jira/kb/unable-to-view-the-time-spent-rollup-from-the-subtasks-for-the-story-in-the-csv-export/)
- Relato de divergência entre navegador e CSV do Σ Time Spent (Cloud) — [JRACLOUD-80618](https://jira.atlassian.com/browse/JRACLOUD-80618)
- Σ não soma filhos de Epic em projetos clássicos (Cloud) — [JSWCLOUD-18871](https://jira.atlassian.com/browse/JSWCLOUD-18871)
- Time Tracking Report tem "Sub-task Inclusion" para escolher quais subtarefas entram — [Jira 6.1 Time Tracking Report](https://confluence.atlassian.com/jira061/jira-user-s-guide/generating-reports/time-tracking-report) (antigo)
- Configurações que afetam: Hours per day, Days per week, Time format (pretty/days/hours), Default unit, Legacy Mode, Copy comment to work description. Mudar só com time tracking desativado; desativar/reativar não apaga dados. Legacy Mode: original estimate só antes do primeiro worklog. Página não menciona "time tracking provider" (isso é Cloud) — [Configuring time tracking (DC)](https://confluence.atlassian.com/adminjiraserver/configuring-time-tracking-938847808.html)
- Opções de ajuste de remaining estimate ao logar: Adjust Automatically, Leave unset, Use existing, Set to, Reduce by — [Logging work](https://confluence.atlassian.com/jirasoftwareserver/logging-work-on-issues-939938944.html)

### Inferences
- Como JQL retorna issues e o `timeSpent` é por issue, um card "combinado" feito com `timeSpent` somado sobre tarefas e subtarefas (qualquer projeto, cada issue uma vez) evita dupla contagem. Usar Σ do pai junto com as subtarefas contaria duas vezes.
- Se uma subtarefa ficar fora do filtro, Σ do pai ainda a inclui (Σ é calculado independentemente do filtro, inferência) — não confirmado em fonte.
- Prefixo de dupla contagem: a mesma regra vale no eazyBI (ver seção 6).

### Gaps
- Comportamento do Σ com subtarefa em outro projeto: não confirmado.
- Valores `Hours per day`/`Days per week` do ambiente do usuário afetam a conversão de `d`/`w`; usar `h`/`m` nos filtros evita ambiguidade (inferência).

## 3. JQL de worklog

### Takeaway
`worklogAuthor`, `worklogDate`, `worklogComment` retornam issues (não worklogs). Se condições em cláusulas diferentes são satisfeitas pelo mesmo worklog ou por worklogs distintos: não confirmado oficialmente.

### Cited Findings
- `worklogAuthor` (USER, autocomplete, `=, !=, IN, NOT IN`): "issues em que um usuário registrou trabalho". Exemplos oficiais incluem `worklogAuthor in membersOf("Jira-software-users")`, ou seja, `membersOf` é válido — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `worklogComment` (TEXT, `~`, `!~`) — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- `worklogDate` (DATE, `=, !=, >, >=, <, <=, IN, NOT IN`), formatos `"yyyy/MM/dd"`, `"yyyy-MM-dd"` ou relativo `"-1d"`; "search results are stored as a local date in the search index" — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- Nomes em minúsculas (`worklogdate`) não são tratados como a cláusula padrão, perdendo otimização e deixando a busca lenta; usar camelCase exato — [JRASERVER-64315](https://jira.atlassian.com/browse/JRASERVER-64315) (resumo de busca)
- Consulta combinada `worklogAuthor = x && worklogDate = "2018-07-26"` é usada na comunidade, e relatos de resultados inesperados com `startOfMonth()` — [Community](https://community.atlassian.com/forums/Jira-questions/How-to-get-worklogs-of-a-user-from-jql-search/qaq-p/521108) (secundária)

### Inferences
- Como JQL devolve issues, o resultado não diz quais worklogs bateram nem soma horas; para horas por pessoa e período é preciso API de worklog/relatório. Cada cláusula de worklog parece avaliada de forma independente no nível da issue (por analogia, não confirmado); não assumir correlação pessoa+data no mesmo worklog.
- Dica de alvo: `timeSpent` de uma issue é o total histórico, não o do período. Cards por período precisam de dados por worklog.

### Gaps
- Semântica exata de combinação por worklog vs issue: não confirmado.
- Fuso horário: apenas "data local no índice"; qual fuso (servidor/usuário): não confirmado.
- Impacto de desempenho de `worklogAuthor in membersOf(...)`: não confirmado.

## 4. Relatórios que somam horas em DC

### Takeaway
Nativamente: Time Tracking Report (por versão) e relatórios de Workload; nenhum fornece soma por pessoa e período de forma nativa e documentada. Timesheet é de app (Tempo e outros).

### Cited Findings
- Time Tracking Report: "informações de time tracking das issues de uma versão", com Original Estimate, Remaining, Time Spent e Accuracy, linha final com agregado da versão; exige time tracking habilitado — [Reporting (Software 10.5)](https://confluence.atlassian.com/jirasoftwareserver105/reporting-1540237329.html); [Jira 6.1 Time Tracking Report](https://confluence.atlassian.com/jira061/jira-user-s-guide/generating-reports/time-tracking-report)
- User Workload Report, Version Workload Report e Workload Pie Chart Report: trabalho alocado/restante por usuário, versão ou projeto/filtro (baseado em estimativa) — [Reporting](https://confluence.atlassian.com/jirasoftwareserver105/reporting-1540237329.html)
- Gadget Workload Pie Chart exige o Jira Charting App. Filter Results, Two Dimensional Filter Statistics e Pie Chart existem; a página de gadgets não lista gadget de timesheet nem de time tracking — [Gadgets](https://confluence.atlassian.com/spaces/JIRASOFTWARESERVER103/pages/1489806499/Gadgets+for+Jira+applications)
- REST `/rest/api/2/search` devolve campos agregados (ex.: `aggregatetimeoriginalestimate`), em segundos — [JRASERVER-21182](https://jira.atlassian.com/browse/JRASERVER-21182) (unidade em segundos é inferência do valor 7200 para 2h)
- Timesheet em DC vem de apps: Tempo Timesheets (gadgets Timesheet, User Timesheet, My Work) — [Tempo docs DC](https://tempo-io.atlassian.net/wiki/spaces/HCTIMESHEETSDC/pages/3389950897); outras apps no Marketplace, ex.: Simple Timesheet & Worklog — [Marketplace](https://marketplace.atlassian.com/apps/1219487/simple-timesheet-worklog-gadget)

### Inferences
- Soma por pessoa e período: apenas apps/eazyBI. Nativo: nenhum confirmado.

### Gaps
- Totais de coluna no Issue Navigator: não confirmado (não encontrei evidência de linha de total).
- Se o Pie Chart agrega campos numéricos como Time Spent (há comentário em JSWSERVER-10424 sobre feature flag): não confirmado — [JSWSERVER-10424](https://jira.atlassian.com/browse/JSWSERVER-10424).
- Opções de export (CSV/Excel) do DC 9.12/10.x: parcial; ver KB de CSV acima, versão DC não confirmada.
- Timesheet nativo em DC: não encontrado.

## 5. Time in status

### Takeaway
Não encontrei relatório nativo de time in status no DC (ausência de evidência, não prova). Alternativas vêm de apps.

### Cited Findings
- Time in Status (SaaSJet), 7 tipos de relatório, compatível com DC 11.0.0–11.3.1 no listing — [Marketplace 1219732](https://marketplace.atlassian.com/apps/1219732/time-in-status); docs DC — [SaaSJet TIS DC](https://help.saasjet.com/tisjdc/reports-of-time-in-status-for-jira-data-center)
- Time in Status Reports for Jira (RVS), DC 11.0–11.3.x — [Marketplace 1226187](https://marketplace.atlassian.com/apps/1226187/time-in-status-reports-for-jira/version-history) (faixas inconsistentes entre fontes)
- Cycle Time Gadgets: Time in Status, DC 8.20.0–11.3.7 — [Marketplace 1235431](https://marketplace.atlassian.com/apps/1235431/cycle-time-gadgets-time-in-status-trend-report-for-jira)
- Time Between Statuses (SaaSJet) tem comparação Cloud vs DC com menos funções no DC — [SaaSJet](https://help.saasjet.com/tbsdc/time-between-statuses-for-jira-data-center-data-ce)
- Cumulative Flow Diagram e Control Chart (Jira Software) são os relatórios nativos de fluxo; mostram status ao longo do tempo/cycle time, não tempo por status por issue — [Reporting](https://confluence.atlassian.com/jirasoftwareserver105/reporting-1540237329.html)

### Gaps
- ScriptRunner e JMWE com evidência de Marketplace/vendor para DC: não confirmado (a busca não retornou páginas deles).
- Afirmação formal da Atlassian de que não existe relatório nativo: não encontrada.
- eazyBI: suporte a DC é confirmado na licença (seção 6); medida de time in status: não confirmada.

## 6. eazyBI para Jira Data Center

### Takeaway
eazyBI expõe Hours spent (por data do worklog, por usuário) e Original estimated hours (por data de criação) e é a rota mais viável para somar horas por pessoa e período. Licença via Marketplace por faixa de usuários do Jira; preços não confirmados.

### Cited Findings
- Medidas: Hours spent (dimensão de tempo = data de início do worklog; pode separar por "Logged by"), Original estimated hours (data de criação), Remaining estimated hours (vencimento se aberta, resolução se resolvida), variantes resolved/closed, Issues with hours spent — [eazyBI time tracking measures](https://docs.eazybi.com/eazybi/data-import/data-from-jira/time-tracking-measures)
- Medidas "with sub-tasks" (Original estimated hours, Remaining, Hours spent) devolvem totais no nível do pai incluindo subtarefas; para subtarefas o valor é sempre zero. As medidas padrão incluem valores de todos os níveis conforme o contexto — mesma página. Para evitar dupla contagem, usar uma ou outra família.
- Medidas adicionais habilitadas em Import options > Additional options; a página fala de eazyBI for Jira e não trata DC especificamente — mesma página.
- eazyBI usa worklogs do Jira; estimativa sem worklog não é reconhecida para tempo gasto (comunidade, secundária) — [community.eazybi.com](https://community.eazybi.com/t/eazybi-hours-spent-incorrect-calculation/1506)
- Licença DC: vendida via Marketplace, anual, faixa conforme usuários do Jira (ex.: 1200 usuários exige faixa 2000). Dados da busca: sem novas licenças DC após março 2026 e sem renovações após março 2028 — [eazyBI DC licensing](https://docs.eazybi.com/eazybi/getting-started/eazybi-for-jira-licensing/eazybi-for-jira-data-center-licensing) (resumo de busca; a página retornou 404 ao abrir diretamente neste caminho `/eazybijira/...`)

### Gaps
- Preço do DC: não confirmado (docs apontam para o Marketplace).
- Frequência de importação, filtros de página para data/equipe/tipo de issue: não confirmado (página de import options retornou 404).
- Histórico de estimativa original requer script no DC (comunidade) — [community.eazybi.com](https://community.eazybi.com/t/cannot-get-in-eazybi-the-values-from-the-original-estimate-field-in-jira-tickets/9665)

## Recomendação operacional para os cards (derivada, não fonte)
- Tarefas só: `issuetype in standardIssueTypes() AND timespent > 0`; Subtarefas só: `issuetype in subTaskIssueTypes() AND timespent > 0`; combinado: união dos dois somando `timespent` por issue (REST `fields=timespent`, segundos). Não somar `aggregatetimespent` junto.
- Período e pessoa: exige worklogs (REST de worklog, app ou eazyBI), pois `timespent` é total acumulado.
