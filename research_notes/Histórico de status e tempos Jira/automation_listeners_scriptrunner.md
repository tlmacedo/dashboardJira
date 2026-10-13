# Tempo em status e contadores de mudança no Jira Data Center: Automation, listeners, ScriptRunner, JMWE

Observação geral: as páginas de Automation para DC são versionadas por espaço (automation0800 = DC 8.0, automation111 = DC 11.1, automation112 = DC 11.2). Não encontrei páginas rotuladas especificamente "9.12 LTS" ou "10.x"; trato as páginas como representativas do Automation bundled e marco o que é inferência. Parte do conteúdo foi lido via resumo automático de página (WebFetch), então termos exatos devem ser reconfirmados em teste real.

## 1. Automation for Jira em DC: triggers, smart values, datas, matemática, variáveis

### Takeaway
No DC há documentação oficial para os triggers Issue transitioned, Field value changed e Issue created, para `{{changelog.status}}`, `{{fieldChange}}`, `{{now}}`, `.diff(...)` e `{{#=}}`. Os smart values `{{fromStatus.name}}`, `{{toStatus.name}}`, `{{transition.from_status}}` e `{{transition.to_status}}` NÃO aparecem na documentação DC que consegui ler (não confirmado). `.asMinutes` também não é documentado; o documentado é `.diff(...).minutes`.

### Cited Findings
- Issue transitioned: "Listen for a specific transition to a status you choose, or for any transition in the workflow"; a página não lista smart values específicos para esse trigger. Disponível no "Server Lite". Página da versão DC 8.0. — [Jira automation triggers (DC 8.0)](https://confluence.atlassian.com/automation0800/jira-automation-triggers-1189780166.html)
- Field value changed: escolhe-se um ou mais campos (ou regex nos nomes), pode limitar a operações create/edit/transition/assign; smart value `{{fieldChange}}`. Não disponível no Server Lite. — [Jira automation triggers (DC 8.0)](https://confluence.atlassian.com/automation0800/jira-automation-triggers-1189780166.html)
- Issue created: sem opções de configuração documentadas; smart value `{{issue}}`. — mesma página acima.
- Configuração From status / To status no trigger Issue transitioned: aparece só em resposta de comunidade vinda de thread Server ("You can specify the To and From statuses in the trigger") — secundário, verificar na UI da sua versão. Ver resultado de busca em [Jira automation triggers](https://confluence.atlassian.com/automation0800/jira-automation-triggers-1189780166.html) (a página oficial só descreve "to a status").
- `{{changelog}}`: "Jira Triggers that edit an issue" a expõem, contendo "All the fields that were changed"; "this is only available for some triggers". Exemplos oficiais: `{{#changelog.status}}{{fromString}}{{/}}` (nome do status anterior) e `{{#changelog.status}}{{from}}{{/}}` (ID anterior). A forma `{{changelog.status.first.fromString}}` NÃO está nessa página (não confirmado como sintaxe documentada; é a mesma estrutura de lista, inferência). — [Jira smart values - issues (DC 8.0)](https://confluence.atlassian.com/automation0800/jira-smart-values-issues-1189780241.html)
- `{{fieldChange.fromString}}`/`{{fieldChange.toString}}` (valores de exibição) e `{{fieldChange.from}}`/`{{fieldChange.to}}` (brutos); "`{{fieldChange}}` only contains the first changed value". — mesma página.
- `{{issue.status.name}}` e `{{issue.created.format("dd/MM/yyyy")}}` documentados. — mesma página.
- `{{fromStatus}}`, `{{toStatus}}`, `{{transition.*}}`: não constam da página de smart values de issues do DC. — mesma página (ausência). Em Cloud existem `{{fromStatus}}`/`{{toStatus}}`/`{{transition}}` para o trigger Issue transitioned, mas não verifiquei fonte oficial nesta sessão: tratar como Cloud-only/não confirmado para DC.
- KB oficial confirma o padrão com Field value changed sobre Status guardando `{{fieldChange.fromString}}` (nome) e `{{fieldChange.from}}` (ID) em campos de texto; aplica-se a Jira Software DC 9.12 e posteriores. — [How to transition to the previous status ... (KB)](https://confluence.atlassian.com/jirakb/how-to-transition-to-the-previous-status-of-an-issue-using-automation-rules-in-jira-1236599919.html)
- Datas (DC 11.1): `{{now}}`, `{{now.plusDays(7).jqlDate}}`; `diff`: `{{now.diff(issue.created).days}}`, `{{now.diff(issue.created).abs.days}}`; unidades: millis, seconds, minutes, hours, days, weeks, months, years, businessDays, prettyPrint, abs. Funções plus/minus para Millis, Seconds, Minutes, Hours, Days, Weeks, Months, Years, BusinessDays (`{{now.plusMinutes(1)}}`, `{{now.minusMinutes(1)}}`). Dias úteis = "Monday to Friday, 9am to 6pm". Não há `asMinutes`. — [Jira smart values - date and time (DC 11.1)](https://confluence.atlassian.com/automation111/jira-smart-values-date-and-time-1652460730.html)
- Matemática (DC 11.1): `{{#=}}1 + 4{{/}}` com funções, operadores e constantes. — [Jira smart values - math (DC 11.1)](https://confluence.atlassian.com/automation111/jira-smart-values-triggers-1652460732.html) (a URL usada retornou a página de math; a página de triggers é outra). `{{math}}` como forma separada: não confirmado.
- Re-fetch issue data EXISTE no DC: ação que atualiza os dados da issue; necessária porque variáveis/Copy from Issue guardam valores do momento do disparo e uma segunda edição do mesmo campo pode sobrescrever a primeira; colocar antes da 2ª edição em diante (não antes da primeira). Aplica-se a Jira Software DC 8.0.0+ e Automation 7.0.0+. — [KB: rule to edit the field multiple times fails](https://support.atlassian.com/automation/kb/rule-to-edit-the-field-multiple-times-fails/)
- Create variable existe no DC, introduzida no Automation for Jira 9.0.1 (segundo KB). — [KB Lookup Issues / Create variable (DC)](https://support.atlassian.com/jira/kb/how-to-use-lookup-issues-to-retrieve-a-single-custom-field-value-in-automation-for-jira-data-center). Relato de comunidade (anedótico): no DC a variável pode não ser tipo texto, exigindo `trim()` para usar funções — [Community](https://community.atlassian.com/forums/Jira-questions/Error-when-put-variables-in-function-REPLACE/qaq-p/2846564).
- Edit issue em campos Number/Date (valor por smart value): sintaxe específica por tipo de campo não verificada em doc DC (não confirmado).

### Inferences
- Receita viável no DC: trigger Issue transitioned (ou Field value changed em Status) -> Edit issue no campo Date Time de "entrada no status" (`{{now}}`) e, na saída, calcular `{{issue.<campo entrada>.diff(now).hours}}` (ou `{{#=}}` para somar ao acumulado), gravar em Number; usar Re-fetch issue data entre edições do mesmo campo. Usar `.hours`/`.minutes` em vez de `.asMinutes`.
- Contador de entradas: `{{#=}}{{issue.<campo contador>}} + 1{{/}}` em Edit issue; tratar valor vazio inicial (não verificado).
- Como `{{fieldChange}}` só contém o primeiro valor alterado, o trigger Field value changed em Status é o caminho mais robusto para obter from/to.

### Gaps
- Smart values `fromStatus/toStatus/transition.*` em DC: não encontrados na doc DC. Páginas DC 9.12/10.x específicas não localizadas. `{{math}}` e sintaxe de Edit issue para Number/Date: não confirmados.

## 2. Limites, auditoria, ator da regra e performance

### Takeaway
Limites padrão documentados (página DC 11.2): 5000 execuções/hora, 3600 s de processamento/dia por regra, 1000 issues por busca JQL, loop depth 10. Throttling desabilita a regra e aparece no audit log.

### Cited Findings
- Tabela (DC 11.2): `max.issues.per.search` 1000; `max.processing.time.per.day` 3600 s; `rule.rate.per.five.second` 2; `max.rules.per.hour` 5000; `max.queued.items.per.rule` 25000; `max.queued.items` 100000; `max.rule.execution.loop.depth` 10; `branch.rule.immediate.priority.item.limit` 10; `outgoing.webhook.timeout.ms` 30000; `regex.timeout` 30000; thread pool 6 por nó; limite JQL agendado "soft" de 1000 (acima: máx. 4 agendamentos em 24 h). — [Automation service limits (DC 11.2)](https://confluence.atlassian.com/automation112/automation-service-limits-1688902311.html). Valores podem diferir em 9.12/10.x (não verificado; páginas históricas existem: [automation073](https://confluence.atlassian.com/automation073/service-limits-1093013429.html)).
- Regras acima do limite são throttled/desabilitadas, com status THROTTLED no audit log; loops marcados como LOOP. Regras que inserem muitos itens prejudicam performance. — [Service limits](https://confluence.atlassian.com/display/AUTOMATION073/Service+limits)
- Ator da regra: ações de apps terceiros (ex.: ScriptRunner "run a script") exigem ator com permissão global Jira Administrator; falhas de permissão aparecem no audit log com pouco detalhe. — [Failures in Automation rules involving third-party actions](https://confluence.atlassian.com/automationkb/failures-in-automation-for-jira-rules-involving-third-party-actions-1402413221.html); [Resolve 'Actor Permission' error in DC](https://support.atlassian.com/jira/kb/resolve-actor-permission-automation-error-in-jira-data-center/)

### Inferences
- Edições feitas pelo ator da regra aparecem no History como feitas por esse usuário e precisam de permissão de edição no projeto (inferência a partir dos KBs de permissão).

### Gaps
- Timeout por regra além do tempo diário: não documentado na fonte lida.

## 3. Campos customizados: Number e Date Time Picker, JQL, reindex

### Takeaway
Number Field e Date Time Picker são os tipos adequados e pesquisáveis em JQL desde que o searcher/template esteja configurado e não seja "None". Requisitos exatos de reindex ao adicionar contexto não confirmados em documentação oficial.

### Cited Findings
- Campos configurados como não pesquisáveis não aparecem em resultados JQL; para Date Time Picker o template suportado é Date Time Range Picker. — [Roadmaps KB](https://confluence.atlassian.com/roadmaps-kb/custom-field-values-don-t-show-in-advanced-roadmaps-plans-jira-data-center-1026039917.html)
- Number Field usa o searcher "Number range"; após mudar o searcher é necessário reindexar (Administration > System > Indexing). Com "Free text searcher" a ordenação pode ficar incorreta. — [KB JQL order](https://support.atlassian.com/jira/kb/jql-search-query-is-not-displaying-asc-desc-order-properly-on-custom-field/)
- Number fields não entram na busca de texto rápida. — [KB](https://support.atlassian.com/jira/kb/unable-to-quick-search-values-contained-in-number-custom-fields)
- Muitos custom fields impactam índice e tempo de indexação; DC tem visão "Custom field indexing" por nó. — [Developer community](https://community.developer.atlassian.com/t/changes-to-custom-fields-implementation-requiring-action-from-you/38780)
- JQL nativo: `status CHANGED` e `status WAS` (predicados AFTER/BEFORE/BY/DURING/ON; CHANGED também FROM/TO). Campos suportados: Assignee, Fix Version, Priority, Reporter, Resolution, Status. Isso permite filtrar por histórico de status, mas não calcula duração nem contagem. — [Advanced searching: operators reference](https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html)

### Inferences
- `"Horas em Em andamento" > 8` deve funcionar com Number Field + searcher Number range (inferência de que o operador `>` é suportado pelo searcher; doc de operadores não lida).

### Gaps
- Reindex ao adicionar campo/alterar contexto: sem fonte oficial direta encontrada.

## 4. Edição com valor idêntico: histórico e `updated`

### Takeaway
Não encontrei declaração oficial. Evidências indicam que edição via UI com mesmo valor normalmente não gera entrada de histórico, mas há exceções via API/post-functions.

### Cited Findings
- Bug oficial: quando tela de transição e post function definem o mesmo valor, a mudança não é capturada no histórico. — [JRASERVER-66091](https://jira.atlassian.com/browse/JRASERVER-66091); similar para Resolution: [JRASERVER-78674](https://jira.atlassian.com/browse/JRASERVER-78674)
- App (Insight) gerou entrada de histórico sem mudança real. — [JSDSERVER-11115](https://jira.atlassian.com/browse/JSDSERVER-11115)
- Comentário de comunidade: UI com valor igual não cria histórico, incerto para API. — [Developer community](https://community.developer.atlassian.com/t/jira-custom-field-update-mechanism/63486)

### Inferences
- Evite depender de "gravar o mesmo valor" para forçar histórico ou atualizar `updated`; testar no ambiente.

### Gaps
- Efeito sobre `updated`: não confirmado.

## 5. ScriptRunner for Jira DC

### Takeaway
A documentação lista listeners (script custom com `event`/`bundle`), post-function de script customizado, script field built-in "Time of Last Status Change" e um exemplo de tempo em status via histórico. Não encontrei JQL function de histórico de status; as listadas incluem `dateCompare` e `expression`.

### Cited Findings
- Custom listener: escolhe-se evento (ex.: Issue Updated), script inline/arquivo; `event` é `IssueEvent`; `bundle` disponível; desde Jira 6.3.10 eventos vêm em bundles, o listener pode disparar mais que o esperado, então checar valores anteriores. — [Custom Listener](https://docs.adaptavist.com/sr4js/latest/features/listeners/custom-listener)
- Post-function de script custom: ordem importa; antes da função interna "update change history/store issue" `issue.status.name` retorna o status anterior, depois retorna o destino; atualizar campos só é possível antes dessa função interna. — [Custom Post Functions](https://docs.adaptavist.com/sr4js/latest/features/workflows/post-functions/custom-post-functions)
- Script field built-in "Time of Last Status Change" (aparece em Dates); transição para o mesmo status conta como mudança. — [Time of Last Status Change](https://docs.adaptavist.com/sr4js/latest/features/script-fields/built-in-script-fields/time-of-last-status-change)
- Exemplo "count the time an issue was in a particular status" (Groovy, soma/subtrai milissegundos a partir do histórico, retorna segundos; compatível com Jira 8.0-8.14 e SR 6.18.0, script field calculado na visualização). — [ScriptRunner example](https://www.scriptrunnerhq.com/help/example-scripts/count-the-time-an-issue-was-in-a-particular-status-onPrem) (domínio scriptrunnerhq.com, não docs.adaptavist.com). Uso de `ChangeHistoryManager.getChangeItemsForField` aparece só em resumo de busca/comunidade; não confirmado em docs.adaptavist.com.
- JQL functions incluídas: `dateCompare` (ex.: `issueFunction in dateCompare("", "resolutionDate > dueDate")`) e `expression`; nenhuma função de histórico de status encontrada. — [Included JQL Functions](https://docs.adaptavist.com/sr4js/latest/features/jql-functions/included-jql-functions); [dateCompare](https://docs.adaptavist.com/sr4js/latest/features/jql-functions/included-jql-functions/date)

### Inferences
- Script field calculado na visualização não é armazenado nem facilmente pesquisável em JQL (campos de script têm searcher próprio; não verificado), logo para filtro/dashboard é preferível gravar em Number via listener/post-function ou Automation.
- Caminho por listener: no Issue Updated, ler `event.changeLog` para itens de status e somar a um Number.

### Gaps
- Compatibilidade ScriptRunner x Jira 9.12/10.x (marketplace) não verificada; exemplos de `ChangeHistoryManager` em docs.adaptavist.com não localizados.

## 6. JMWE em DC (Appfire)

### Cited Findings
- "Set field value" está deprecated e substituído por "Set issue fields" (Nunjucks/JSON, execução condicional, issues relacionadas). — [Set field value (Deprecated)](https://appfire.atlassian.net/wiki/spaces/JMWE/pages/461964442/Set+Field+value+Deprecated); [Set issue fields](https://appfire.atlassian.net/wiki/spaces/JMWEC/pages/465504849/Set+issue+fields) (esta fica no espaço JMWEC, possivelmente Cloud: verificar).
- "Copy issue fields" no DC ganhou opção "Use raw value" (release notes 26/11/2024). — [Release notes](https://appfire.atlassian.net/wiki/spaces/JMWE/pages/1515126875/Release+notes+November+26+2024)
- "Update any issue field": não encontrado.

### Gaps
- Disponibilidade e nomes exatos no DC 9.12/10.x: não confirmado.

## 7. Recursos nativos

### Cited Findings
- JQL `status CHANGED/WAS` pesquisa histórico (ver seção 3). O histórico na aba History não é pesquisável por duração (inferência; sem fonte). Resolved date: não pesquisada.

### Gaps
- Resolved/`resolutiondate`: sem fonte coletada.
