# Pai/subtarefa, divergência de due date e automação em Jira Data Center (referência 2026; alvo 9.12 LTS e 10.x)

Legenda: [NATIVO] = sem app; [APP] = requer app; [CLOUD] = só Cloud, não usar no DC. "não confirmado" = não verificado em fonte.

## 1. Listar subtarefas com o pai e comparar due dates (JQL nativo e Issue Navigator)

### Takeaway
O JQL nativo do DC lista subtarefas (`issuetype in subtaskIssueTypes()`) e filtra por pai só via chave (`parent = KEY`). Não compara dois campos, então não compara o duedate da subtarefa com o do pai. A comparação exige ScriptRunner, outro app ou exportação (CSV/Excel).

### Cited Findings
- [NATIVO] `subtaskIssueTypes()`: "Perform searches based on issues that are sub-tasks". Campo suportado: Type; operadores IN/NOT IN. Exemplo: `issuetype in subtaskIssueTypes()`. — [Functions reference (DC)](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html)
- [NATIVO] Campo `parent`: "Only available if sub-tasks have been enabled". Busca as subtarefas de uma issue por chave ou ID. Operadores `=`, `!=`, `IN`, `NOT IN`. Sem funções suportadas. Exemplo: `parent = TEST-1234`. Não suporta `IS`/`IS NOT` nem operadores de comparação. — [Fields reference (a página indexada é a do DC 11.3)](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- Como o campo `parent` aceita só chave/ID, não dá para filtrar subtarefas pelo tipo ou por campos do pai. É um pedido aberto, "Gathering Interest", 63 votos, criado em 2014, atualizado em fev/2026, sem versão de correção. — [JRASERVER-38798](https://jira.atlassian.com/browse/JRASERVER-38798)
- [NATIVO] A documentação oficial diz: "it is not possible to compare two fields in JQL". Logo não existe `duedate > parent.duedate` em JQL nativo. — [Fields reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html)
- [NATIVO] Campo `due`/`dueDate`: compara só com datas/funções (`now()`, `startOfWeek()` etc.) e formato `"yyyy-MM-dd"` ou relativo (`"1d"`). A data não tem hora. — mesma página acima.
- A página de funções do DC não lista `parentEpic()` nem nenhuma função de pai/épico/subtarefa além de `subtaskIssueTypes()` e `standardIssueTypes()`. Também não lista função que compare datas entre issues. — [Functions reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html)
- Ressalva: as páginas indexadas são de "jirasoftwareserver" (última versão, DC 11.3 para a de campos). Nenhuma seção de `parent` traz nota de versão. Não encontrei a versão exata em que `parent` surgiu. O campo existe desde o Jira Server (a snippet da JRASERVER-38798 de 2014 já o cita).

### Inferences
- Para 9.12 e 10.x, `parent = KEY` e `issuetype in subtaskIssueTypes()` devem funcionar, porque a doc DC os descreve sem restrição de versão. Isso não foi testado nem documentado versão a versão.
- Para listar subtarefa e pai juntos de forma nativa, o caminho é uma consulta `issuetype in subtaskIssueTypes()` com a coluna Parent e o duedate de cada issue, exportando para CSV. O duedate do pai só entra se a exportação trouxer o pai como linha própria (ex.: segunda consulta pelas chaves dos pais).
- Em automação (seção 4), `parent = {{issue.key}}` funciona como JQL com smart value (fonte na seção 4).

### Gaps
- Colunas do Issue Navigator ('Parent', 'Sub-tasks', 'T') e opções de visualização: não pesquisei a doc de colunas, então não confirmado.
- `parentEpic`: não aparece na lista de funções do DC. Pode ser função de apps ou de Cloud, não confirmado.
- Versão em que `parent` passou a ser pesquisável: não confirmado.

## 2. ScriptRunner for Jira Data Center (JQL functions, scripted fields, botão, listeners)

### Takeaway
[APP] No ScriptRunner DC, `subtasksOf("jql")` e `parentsOf("jql")` filtram subtarefas/pais por critério do outro lado, mas `dateCompare` compara datas na mesma issue. A comparação duedate subtarefa x pai, por JQL, não está documentada. Alternativas documentadas: scripted field (data do pai exposta na subtarefa, com searcher), botão (web item) e listener.

### Cited Findings
- Funções listadas (prefixo `issueFunction`, exigem `in`/`not in`): `parentsOf` ("Get the parents of issues matched by the subquery"), `subtasksOf` ("Get the subtasks of issues matched by the subquery"), `hasSubtasks` ("Find issues with subtasks"), `dateCompare` ("Compare two dates on the same issue"). — [Included JQL Functions](https://docs.adaptavist.com/sr4js/latest/features/jql-functions/included-jql-functions)
- `issueFunction in subtasksOf("subquery")`; `subtasksOf("")` = subtarefas de qualquer issue. Exemplos: `issueFunction in subtasksOf("project = SSP") AND resolution is empty`; `issueFunction in subtasksOf("resolution = Fixed") AND status = Open`. — [Sub-tasks](https://docs.adaptavist.com/sr4js/latest/features/jql-functions/included-jql-functions/sub-tasks)
- `issueFunction in parentsOf("subquery")`: "pai" = qualquer issue com subtarefas. Não cobre Epic-Story. Exemplo: `resolution is not empty AND issueFunction in parentsOf("project = SSP AND resolution is empty")`. — mesma página.
- `issueFunction in hasSubtasks()`. — mesma página.
- `dateCompare`: `issueFunction in dateCompare("Subquery", "date comparison expression")`. Operadores `<`, `>`, `<=`, `>=`, `=`; `!=` só no ScriptRunner Cloud. Campos: `created`, `dueDate`, `resolutionDate`, `firstCommented`, `lastCommented` e Date/Date Time Picker. Exemplos oficiais: `issueFunction in dateCompare("", "resolutionDate > dueDate")`, `issueFunction in dateCompare("", "resolutionDate < dueDate +1w")`, `issueFunction in dateCompare("project in myProjects()", "resolutionDate > dueDate")`. — [Date](https://docs.adaptavist.com/sr4js/latest/features/jql-functions/included-jql-functions/date)
- O exemplo `issueFunction in dateCompare("", "duedate > startOfMonth()")` sugerido na pergunta não consta na página: não confirmado. Os exemplos comparam campos da mesma issue (ou com offset `+1w`).
- Função de data entre pai e subtarefa (nome tipo `subtaskDateCompare`): a lista da página índice não contém nenhuma. Não confirmado, não existe na doc consultada.
- Composição possível, não documentada como caso de uso: `subtasksOf` e `dateCompare` são aplicáveis na mesma consulta, mas cada uma avalia um critério separado. Nenhuma doc mostra a comparação "subtarefa.duedate vs pai.duedate".
- Scripted fields: "Script fields can be searched only if they have a searcher attached"; a escolha de Template/Searcher deve ser feita antes (trocar pode exigir reindex completo); valores são calculados na exibição/indexação; para entrar no índice JQL é preciso reindex em background. — [Script Field Tips](https://docs.adaptavist.com/sr4js/latest/features/script-fields/script-field-tips/), [Custom Script Field](https://docs.adaptavist.com/sr4js/latest/features/script-fields/custom-script-field/)
- Script fragments (web item): em ScriptRunner, UI Fragments > Create Fragment > Custom web item. Pode "Run code and display a dialog" ou "Navigate to a link". Endpoints em `/rest/scriptrunner/latest/custom/<endpoint>`. A seção `operations-top-level` garante o contexto de issue. Scripts 8.x e 9.x usam `javax.ws.rs`; 10.x+ usa `jakarta.ws.rs`. Condições limitam por grupo/papel/label. — [Web item (9.x)](https://docs.adaptavist.com/sr4js/9.x/features/fragments/web-item)
- Listeners: reagem a eventos como Issue Updated e, diferente de post-functions, a edições fora de transição; há listeners prontos (ex.: "Version Synchroniser") e listeners Groovy customizados. — [Listeners](https://docs.adaptavist.com/sr4js/latest/features/listeners)
- Compatibilidade: a doc diz que só funcionam as versões marcadas como compatíveis no Marketplace para Jira 9, 10 e 11. Jira 10 exige JDK 17 no mínimo. — [Compatibility with Jira](https://docs.adaptavist.com/sr4js/latest/get-started/update-scriptrunner/compatibility-with-jira)

### Inferences
- Para o objetivo (divergência), o desenho viável com ScriptRunner é um scripted field ("Data do pai") do tipo Date com searcher, ou um listener que sincroniza. Isso é inferência: nenhuma doc consultada mostra esse exemplo.

### Gaps
- Qual versão do ScriptRunner roda no 9.12 e no 10.x: remete ao Marketplace (não consultado), não confirmado.
- Exemplos de scripted field lendo o pai: não confirmados (só vi a página de dicas e a de visão geral).
- Detalhes de listener (escopo, condição): a página consultada não os traz.

## 3. JMWE (Jira Misc Workflow Extensions) no Data Center

### Takeaway
A documentação encontrada indica que o recurso "copiar campo do pai" foi descontinuado e substituído por "Copy issue fields", que tem como origem o "parent of the current sub-task". Essa informação vem do espaço Cloud da documentação. A página DC equivalente e o post-function "Update any issue field" não foram confirmados.

### Cited Findings
- "Copy field value from parent issue (Deprecated)": copia campo do pai para a subtarefa; substituído por "Clear fields" e "Copy issue fields" (que oferece mais opções de origem/destino). A página está no espaço JMWEC (título "JMWE for Jira Cloud"). — [Copy field value from parent issue (Deprecated)](https://appfire.atlassian.net/wiki/spaces/JMWEC/pages/466257573/Copy+field+value+from+parent+issue+Deprecated), resumo da busca; página parcialmente lida
- "Copy issue fields": fontes incluem issue atual (padrão), subtarefas da issue atual, pai da subtarefa atual, issues do épico/pai do épico. — [Copy issue fields (espaço JMWEC)](https://appfire.atlassian.net/wiki/spaces/JMWEC/pages/466323304), conforme resumo da busca, página não aberta por mim
- Release notes de JMWE para Jira Data Center 9.1 (26/11/2024) citam a reativação de "Copy sub-tasks" no Create/Clone issue. Isso prova que há JMWE para DC em 2024, não que "Copy issue fields" é igual ao Cloud. — [Release notes November 26, 2024](https://appfire.atlassian.net/wiki/spaces/JMWE/pages/1515126875/Release+notes+November+26+2024)
- Post-functions de JMWE rodam em transições de workflow (documentação do tipo); botão manual: não confirmado.

### Gaps
- "Update any issue field" em DC, página DC de "Copy issue fields" e campo due date: não confirmados. A tentativa de abrir uma página innovalog retornou 404.
- Compatibilidade JMWE com 9.12/10.x no Marketplace: não verificado.

## 4. Automation for Jira em Data Center (embutido, gatilhos, manual, ramificação, ações, smart values, limites)

### Takeaway
Embutido no Jira Software DC desde a 9.0 (jun/2022) e disponível como app gratuito do Marketplace nas versões anteriores. Tem gatilho Manual, Agendado (JQL), Incoming webhook, Field value changed e outros. O ramo "sub-tasks" permite copiar um valor do pai para todas as subtarefas, e o gatilho manual cobre o botão sob demanda.

### Cited Findings
- Embutido: "Automation for Jira is integrated into Jira Software Data Center 9.0", disponível em projetos business/software/service. Para versões anteriores, o app é gratuito no Marketplace. — [Jira Software 9.0.x release notes](https://confluence.atlassian.com/jirasoftware/jira-software-9-0-x-release-notes-1142227613.html); [Automate your project](https://confluence.atlassian.com/jirasoftwareserver0903/automate-your-project-1178870888.html)
- A partir do Jira DC 10.0, as novidades de automação aparecem nas release notes do Jira DC, só a versão embutida é atualizada. 9.2 foi a última versão do app no Marketplace. Automation 9.0.0 (ago/2023) adicionou Lookup issues e Create variable (licença DC), secret keys e allowlist ativa por padrão. — [Jira Data Center automation release notes](https://confluence.atlassian.com/automation/jira-automation-release-notes-1018767858.html)
- Gatilhos (23 na página): Field value changed (filtro por campo/regex e por Create/Edit/Transition/Assign), Incoming webhook, Issue created, Issue transitioned (transição específica ou qualquer), Issue updated (exclui link, assignee e workflow), Manual ("Lets users start a rule from the issue view... refine which groups can manually trigger"), Scheduled (intervalo fixo ou Cron, com ou sem JQL). — [Jira automation triggers](https://confluence.atlassian.com/automation/triggers-993924804.html)
- Manual trigger: aparece no menu "More" da issue; é possível restringir por grupos; ao terminar mostra mensagem de sucesso/falha e a página precisa ser recarregada para ver as mudanças. Atenção: página datada de 2022. — [Manually run a rule against an issue](https://confluence.atlassian.com/display/AUTOMATION/Manually+run+a+rule+against+an+issue); [versão 7.4](https://confluence.atlassian.com/automation074/manually-run-a-rule-against-an-issue-1141481082.html)
- [CLOUD] No Cloud o gatilho manual foi movido para o menu "Actions" (anúncio citado em AUTO-1781). Não aplicar ao DC. — [AUTO-1781](https://jira.atlassian.com/browse/AUTO-1781)
- Ramificação (branch) em issues relacionadas: sub-tasks, parent, epic, stories, linked issues, issues de sprint/versão e JQL. Não há branches aninhados, nem If/Else dentro de branch. Em várias issues os branches rodam em paralelo. Em uma única issue rodam inline. — [Working with related issues](https://confluence.atlassian.com/automation074/working-with-related-issues-1141481085.html)
- `{{triggerIssue}}`: issue que iniciou a regra, somente leitura, usada dentro de branches onde `{{issue}}` é a relacionada. — mesma página; [Smart values - issues](https://confluence.atlassian.com/automation/jira-smart-values-issues-993924860.html)
- `{{issue.parent}}`: se a issue é subtarefa, refere-se ao pai e dá acesso aos campos do pai (ex.: `{{issue.parent.key}}`). — [Smart values - issues](https://confluence.atlassian.com/automation/jira-smart-values-issues-993924860.html)
- Exemplo oficial de sincronização pai -> subtarefas: gatilho Issue transitioned, branch para sub-tasks, ação Transition issue. Também cita copiar valores de campo para subtarefas. (Automation 7.2 Server; versão antiga.) — [Synchronize parent and sub-tasks issues](https://confluence.atlassian.com/automation072/synchronize-parent-and-sub-tasks-issues-1056675738.html)
- Em rule DC (Jira Software 8.0+), o Edit issue dentro de branch pode usar "Copy from Trigger Issue" para copiar o campo do gatilho; desmarcar "Only include issues that have changed since the last time this rule executed". — [KB: copiar campo de pai para filho](https://support.atlassian.com/automation/kb/automation-for-jira-writing-a-rule-that-copies-a-field-from-a-parent-to-a/)
- Smart values de data: `{{now}}`, `{{now.plusDays(7).jqlDate}}`, `{{now.minusDays(1)}}`, `.jiraDate` (ex.: 1979-11-01), `.shortDate`, `.format("dd/MM/yyyy")`. Datas aparecem no fuso padrão do Jira. A página usa `{{issue.dueDate.format("dd/MM/yyyy")}}` (D maiúsculo). — [Date and time](https://confluence.atlassian.com/automation/date-and-time-functions-993924864.html)
- Smart values `{{initiator}}`, `{{fromStatus}}`/`{{toStatus}}`: não aparecem na página de issues. Ela mostra o status anterior via `{{#changelog.status}}{{fromString}}{{/}}` e, para field change, `{{fieldChange.fromString}}`/`{{fieldChange.toString}}`. — [Smart values - issues](https://confluence.atlassian.com/automation/jira-smart-values-issues-993924860.html). `initiator`: não confirmado.
- Limites (todos configuráveis via `/rest/cb-automation/latest/configuration/property`): JQL searched issues 1000 (`max.issues.per.search`); daily processing time 3600 s; rule rate 2 por 5 s; 5000 execuções/hora; items queued per rule 25000; total queued 100000; max loop depth 10; scheduled JQL issue limit 1000 (soft); branch immediate priority item limit 10; webhook timeout 30000 ms; thread pool 6 por nó. Regras acima dos limites são "THROTTLED" no audit log. A página não diferencia DC/Cloud (só os thread pools falam de nós DC). — [Automation service limits](https://confluence.atlassian.com/automation/automation-service-limits-993924705.html)
- Ator e permissões: a regra roda como um ator; o erro típico é falta de permissão Edit Issues em todos os projetos do escopo. Em DC o ator padrão (papel atlassian-addons-project-access) pode precisar de permissão no esquema. Pode-se trocar o ator para um usuário específico ou "User who triggered the event". — [KB: rule actor missing permissions](https://confluence.atlassian.com/automationkb/cannot-enable-or-save-automation-rule-due-to-the-error-either-you-or-the-rule-actor-for-this-rule-is-missing-some-required-permissions-1095246109.html); [Resolve 'Actor Permission' Automation Error in Jira DC](https://support.atlassian.com/jira/kb/resolve-actor-permission-automation-error-in-jira-data-center/)
- Edit issue não consegue alterar o DueDate se o ator não tem permissão de editar a issue; o audit pode mostrar "Edited issue successfully, however some of the set fields aren't available" (bug citado como JRACLOUD-83386, é bug Cloud no resumo da busca). — [JRACLOUD-83386](https://jira.atlassian.com/browse/JRACLOUD-83386)
- Ações citadas na doc DC: Edit issue, Lookup issues, Create variable, Create sub-tasks (só summary). Contagem de subtarefas com Lookup + Edit issue vale para Jira 9.11.0+. — [KB: contar subtarefas](https://confluence.atlassian.com/automationkb/automation-for-jira-how-to-create-a-rule-that-counts-the-number-of-sub-tasks-that-belong-to-an-issue-1489470218.html)

### Inferences
- Regra proposta: gatilho Manual na issue pai, branch "Sub-tasks", ação Edit issue com Due date = `{{triggerIssue.duedate}}` (ou `{{triggerIssue.dueDate}}`; a grafia correta não foi confirmada em DC, a doc usa `dueDate`). Para o pai ser o gatilho, o botão aparece no pai. Para botão na subtarefa: gatilho Manual na subtarefa, ação com `{{issue.parent.dueDate}}`.
- Para limitar à divergência, usar condição "Advanced compare" ou JQL no branch. A doc consultada não lista a condição "Advanced compare" em DC, então isso é não confirmado.
- Em 9.12 LTS a automação já está embutida (9.0+), então a regra funciona sem app.

### Gaps
- Nomes oficiais e disponibilidade de "Issue fields condition" e "Advanced compare condition" em DC: não confirmados.
- Permissão exata para alterar due date ("Schedule Issues") e papel do ator na ação Edit issue: não confirmada (a KB cita Edit Issues).
- Quem pode executar o manual trigger além de "grupos": só consta restrição por grupo (doc de 2022).
- Texto do "Automation" no menu da issue em 10.x: não confirmado.
- Limites de issues por branch: a doc dá 1000 por busca JQL; limite específico "por branch" não confirmado.

## 5. Incoming webhook trigger (método, segredo, GET)

### Takeaway
Documentado apenas como POST, com URL única. Segredo e GET: não confirmados na documentação DC.

### Cited Findings
- "Provides a unique URL... make a HTTP Post request from your custom scripts"; dados em `{{webhookData}}`. Sem menção a segredo nem a GET. — [Jira automation triggers](https://confluence.atlassian.com/automation/triggers-993924804.html)
- KB: `{{webhookData}}` retorna a raiz do JSON do POST; fallback `{{webhookResponse.body}}`; aplica-se a Cloud e DC. — [Use incoming webhooks with smart values](https://support.atlassian.com/jira/kb/use-incoming-webhooks-with-smart-values-in-automation-for-jira/)
- Receber regra em DC com `Send web request` POST da outra instância. — [Automate Issue Creation Across Jira DC Instances](https://support.atlassian.com/jira/kb/automate-issue-creation-across-jira-data-center-instances/)
- Um relato (Cloud) diz que um branch JQL no gatilho webhook não executa por rodar como usuário anônimo (AUT-547). Status em DC: não confirmado. — [Community thread](https://community.atlassian.com/forums/Automation-questions/Branching-on-JQL-failes-for-Incoming-Webhook-Trigger-rule/qaq-p/2016563)
- O release notes 9.0.0 citam "secret keys" e allowlist ativa por padrão, mas isso se refere a Send web request/segredos, não ao segredo do gatilho. — [Release notes](https://confluence.atlassian.com/automation/jira-automation-release-notes-1018767858.html)

### Inferences
- Um link simples (GET) provavelmente não dispara a regra, já que só POST está documentado. Isso é inferência, não teste. Um botão no navegador exigiria um POST (por exemplo via ScriptRunner web item/endpoint ou um formulário), e o gatilho Manual é mais simples.

### Gaps
- Aceitação de GET, cabeçalho de segredo e autenticação no DC: não confirmados.

## 6. Editar label/campo sem mudança: gera histórico ou altera `updated`?

### Takeaway
Não encontrei fonte oficial DC que responda. Há apenas indícios indiretos.

### Cited Findings
- Pedido Cloud afirma que qualquer alteração (UI, REST, app) atualiza o campo Updated e que não há atualização "silenciosa". Não é doc DC. — [JRACLOUD-97196](https://jira.atlassian.com/browse/JRACLOUD-97196)
- Edição de comentário atualiza `updated` sem registro no History (thread Server 7.11). — [Community](https://community.atlassian.com/forums/Jira-questions/Changes-in-the-issue-are-not-showing-up-in-the-history-logs-but/qaa-p/1220090)
- Alterações via importação CSV de campos custom não aparecem no History. — [Atlassian KB](https://ja.confluence.atlassian.com/jirakb/no-change-history-when-updating-issues-via-csv-665225382.html)

### Gaps
- Comportamento de gravar o mesmo valor (label igual, data igual) em DC 9.12/10.x: não confirmado. Recomendação: testar em uma issue e comparar History e `updated` antes e depois.
