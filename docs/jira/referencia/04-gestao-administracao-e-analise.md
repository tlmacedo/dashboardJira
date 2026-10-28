# Jira Data Center: gestão, administração e análise de dados

Referência local baseada em documentação pública (pesquisa na web, sem acesso a nenhuma instância Jira). Nomes fictícios: usuário `ana.silva`, projeto `TG`. Escopo: **Jira Data Center** (as páginas de auditoria e de time tracking lidas descrevem a versão 11.3). Onde algo é só Cloud, está dito.

Convenção nas Observações: "confirmado" = lido na página citada; "secundário" = visto em resultado de busca, KB ou comunidade, não na página oficial; "conhecimento geral" = prática conhecida do produto, não relida nesta pesquisa (validar no servidor real).

---

## 1. Workflows e resolução

### Status e transições
**Finalidade:** definir o estado de uma issue e os caminhos permitidos entre estados.
**Como fazer / comando:**
Administração > Issues > Workflows (editor de workflow); no JQL: `status = "In Progress"`.
**Observações:** confirmado: uma issue tem um único status por vez; status são globais (renomear afeta todos os workflows que o usam); transição é de mão única (ir e voltar exige duas). Os nomes dos status variam por projeto/workflow.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/working-with-workflows-938847362.html
**Uso possível no nosso painel:** vocabulário de colunas de fluxo (mapeamento status para etapa) e tempo em cada status via changelog.

### Categorias de status (To Do / In Progress / Done)
**Finalidade:** agrupar status em três categorias fixas, independentes dos nomes locais.
**Como fazer / comando:**
```
statusCategory = "In Progress"
statusCategory != Done
```
Administração > Issues > Statuses (escolher a categoria ao criar/editar o status).
**Observações:** secundário: categorias padrão To Do, In Progress, Done; nomes não customizáveis; a cor exibida pode diferir entre versões. Na API, o status traz `statusCategory` (`key`, `name`, `id`).
**Fonte:** https://confluence.atlassian.com/adminjiraserver0817/defining-status-field-values-1071815986.html
**Uso possível no nosso painel:** classificar "aberta / em andamento / concluída" sem depender de nomes de status; base da regra "Concluída = categoria done + resolution".

### Resolução (resolution)
**Finalidade:** campo separado do status que indica se a issue está resolvida e como.
**Como fazer / comando:**
```
resolution = Unresolved
resolution IS NOT EMPTY AND resolved >= -30d
```
**Observações:** confirmado: issue sem resolução é considerada aberta; com valor (ex.: Fixed) é considerada fechada. Para forçar, a resolução é definida na transição (post function ou tela). Divergência (status done sem resolution, ou o inverso) é caso real de dados sujos.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/working-with-workflows-938847362.html
**Uso possível no nosso painel:** card de Qualidade dos dados (status done sem resolution e vice-versa).

### Condições, validadores e post functions
**Finalidade:** controlar quem pode fazer uma transição (condições), o que deve ser verdade (validadores) e o que acontece depois (post functions).
**Como fazer / comando:**
Administração > Issues > Workflows > editar > selecionar a transição > abas Conditions / Validators / Post functions.
**Observações:** confirmado só que a página de workflows cita os conceitos e remete à configuração avançada (a página de detalhe não foi lida: a URL tentada deu 404). Conhecimento geral: condição "Only Assignee"; validador de permissão; post functions "Update issue field" (ex.: limpar resolution ao reabrir) e atribuição automática. Mudanças feitas por post function aparecem no changelog em nome do usuário que executou a transição.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/working-with-workflows-938847362.html
**Uso possível no nosso painel:** entender por que certas alterações aparecem no histórico (automáticas vs. manuais) na auditoria.

### Propriedades de transição e de status
**Finalidade:** guardar metadados chave/valor em status e transições (ex.: `jira.issue.editable`).
**Como fazer / comando:**
Workflow em modo Diagram/Text > selecionar status ou transição > Properties.
**Observações:** conhecimento geral, não relido.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/working-with-workflows-938847362.html
**Uso possível no nosso painel:** nenhum direto; explica bloqueios de edição por status.

---

## 2. Esquemas, campos e tipos

### Esquemas do projeto (visão geral)
**Finalidade:** conjuntos reutilizáveis que definem permissões, notificações, workflow, tipos de issue, configuração de campos e telas por projeto.
**Como fazer / comando:**
Projeto > Project settings (um esquema de cada tipo associado); Administração > Issues para editar os esquemas.
**Observações:** conhecimento geral. Esquemas: Permission, Notification, Workflow, Issue Type, Issue Type Screen, Field Configuration. Projetos diferentes podem ter workflows e nomes de status diferentes, o que importa para consultas multi-projeto.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/managing-project-permissions-938847145.html
**Uso possível no nosso painel:** nenhum direto; explica variação de vocabulário entre projetos.

### Permission scheme e permissões de projeto
**Finalidade:** definir quem pode fazer o quê em cada projeto.
**Como fazer / comando:**
Administração > Issues > Permission schemes; ou Project settings > Permissions.
**Observações:** confirmado: Browse Projects (ver/pesquisar); Administer Projects (papéis, componentes, versões); Edit Issues (editar issues; o Due Date foi separado); Assign Issues; Schedule Issues (data de vencimento); Resolve Issues (exige Transition Issues); Close Issues (exige Transition e Resolve); Transition Issues; Link Issues; Modify Reporter; Work On Issues (worklog); Edit All Worklogs. Concedidas a usuários, grupos, papéis de projeto, papéis de issue (Reporter, Project Lead, Current Assignee), Anyone ou campos de seleção de usuário/grupo.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/managing-project-permissions-938847145.html
**Uso possível no nosso painel:** regra de auditoria: quem tinha permissão Assign/Schedule ao mudar responsável/vencimento; alerta quando alguém sem o papel esperado altera.

### Grupos e papéis de projeto
**Finalidade:** agrupar pessoas globalmente (grupos) ou por projeto (papéis) para conceder permissões.
**Como fazer / comando:**
Administração > User management > Groups; Administração > System > Project roles; membros por projeto em Project settings > People.
**Observações:** conhecimento geral. No JQL: `assignee in membersOf("tg-basic")` (a função `membersOf()` consta na lista de funções).
**Fonte:** https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937709.html
**Uso possível no nosso painel:** montar o time a partir de um grupo do Jira (alternativa ao YAML), a confirmar na primeira conexão.

### Campos personalizados (tipos, contextos, ids)
**Finalidade:** adicionar campos próprios (texto, número, data, seleção, usuário etc.) com contexto por projeto/tipo de issue.
**Como fazer / comando:**
Administração > Issues > Custom fields. Na API o campo é `customfield_NNNNN`; no JQL, pelo nome (`"Story Points" > 3`) ou `cf[NNNNN]`. Listar:
```
GET /rest/api/2/field
```
**Observações:** o id é único por instância e o nome não. A resposta de `/field` traz `clauseNames` (inclui `cf[NNNNN]`); o KB oficial usa `latest/field`. Para versões anteriores à 8.4 o id vinha do createmeta (secundário).
**Fonte:** https://confluence.atlassian.com/jirakb/how-to-find-any-custom-field-s-ids-744522503.html
**Uso possível no nosso painel:** mapear Story Points, Sprint, Epic Link, campo de Review para ids reais em configuração.

### Contextos de campo e otimizador
**Finalidade:** restringir onde cada campo aparece e reduzir custo de indexação/consulta.
**Como fazer / comando:**
Administração > Issues > Custom fields > Actions > Configure; página "Custom fields optimizer".
**Observações:** confirmado: muitos campos com contexto global prejudicam desempenho; o optimizer converte para contextos por projeto, e o projeto perde o campo até ser adicionado ao novo contexto.
**Fonte:** https://confluence.atlassian.com/adminjiraserver102/custom-fields-with-global-contexts-1473876743.html
**Uso possível no nosso painel:** nenhum direto; explica campo ausente em um projeto.

### Tipos de issue e sub-tarefas
**Finalidade:** classificar o trabalho (Task, Bug, Story, Epic, Sub-task) e quebrar em sub-tarefas.
**Como fazer / comando:**
```
issuetype = Sub-task
issuetype in standardIssueTypes()
parent = TG-100
```
Administração > Issues > Issue types / Sub-tasks.
**Observações:** conhecimento geral. Sub-tarefas têm status e assignee próprios; a API traz `fields.parent` e `fields.subtasks`. Vínculo com Epic usa Epic Link (clássico) ou Parent Link (Advanced Roadmaps), com ids que variam.
**Fonte:** https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937709.html
**Uso possível no nosso painel:** decidir se sub-tarefas contam em carga/WIP; agrupar por Epic.

### Versões e componentes
**Finalidade:** marcar releases (fixVersion/affectedVersion) e áreas do produto (component).
**Como fazer / comando:**
```
fixVersion = "1.4" AND resolution = Unresolved
component = "Backend"
```
Project settings > Versions / Components.
**Observações:** confirmado que Administer Projects gerencia componentes e versões e que Resolve Issues permite definir Fix Version; o restante é conhecimento geral.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/managing-project-permissions-938847145.html
**Uso possível no nosso painel:** filtro opcional por versão/componente; entregas por release.

---

## 3. Links entre issues

### Tipos de link e direções
**Finalidade:** relacionar issues (bloqueio, duplicidade, clone).
**Como fazer / comando:**
Administração > Issues > Issue linking. Na API: `fields.issuelinks[]` com `type{name,inward,outward}` e `outwardIssue` ou `inwardIssue`.
**Observações:** confirmado: cada tipo tem nome, descrição outward e descrição inward. Padrões: Relates (relates to / relates to), Duplicate (duplicates / is duplicated by), Blocks (blocks / is blocked by), Cloners (clones / is cloned by). Na API, `outwardIssue` presente no link significa que a issue consultada é a origem da descrição outward (ela "blocks" a outra), e `inwardIssue` o contrário. Tipos podem ter sido customizados (ex.: um tipo para Review); confirmar no servidor. Não apagar o tipo de clone.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/configuring-issue-linking-938847862.html
**Uso possível no nosso painel:** card de bloqueios e de issues em revisão (tipo de link da Review); aging de bloqueadas.

### Consultar links no JQL
**Finalidade:** achar issues com links de determinado tipo.
**Como fazer / comando:**
```
issueLinkType = "Blocks"
issue in linkedIssues(TG-100, "is blocked by")
```
**Observações:** `linkedIssues()` consta na lista de funções (confirmado); `issueLinkType` é conhecimento geral. O tipo "Blocks" tem nome igual à descrição outward, o que dificulta filtrar só pela descrição (apontado na documentação, secundário).
**Fonte:** https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937709.html
**Uso possível no nosso painel:** lista de impedimentos entre tasks do time.

---

## 4. Rastreamento de tempo

### Configuração do time tracking
**Finalidade:** habilitar estimativas e registro de tempo, e definir o dia de trabalho.
**Como fazer / comando:**
Administração > Issues > Time tracking (desativar para alterar, depois reativar).
**Observações:** confirmado (DC 11.3): horas por dia e dias por semana (aceita fração); formato de exibição (pretty/days/hours); unidade padrão; Legacy Mode (impede mudar a estimativa original após haver worklog); opção de copiar o comentário para a descrição do worklog (restringe a visibilidade). Mudar a configuração afeta o índice: reindexar. Exige permissão "Work On Issues"; o campo Time Tracking precisa estar na tela. Dados não se perdem ao desativar.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/configuring-time-tracking-938847808.html
**Uso possível no nosso painel:** converter segundos em dias coerentes com a jornada do Jira (ex.: 8 h/dia).

### Campos de estimativa e tempo gasto (JQL e API)
**Finalidade:** ler estimativa original, restante e tempo gasto.
**Como fazer / comando:**
```
timeoriginalestimate > 0
timespent IS EMPTY
workratio > 100
```
API: `fields.timetracking{originalEstimate, remainingEstimate, timeSpent}`, `fields.timespent` e `fields.aggregatetimespent` (em segundos).
**Observações:** conhecimento geral. "Aggregate" soma sub-tarefas; `workratio` é gasto/estimado em porcentagem.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/configuring-time-tracking-938847808.html
**Uso possível no nosso painel:** estimado x realizado; carga em horas quando o time estima.

### Worklogs
**Finalidade:** registrar e consultar trabalho feito por pessoa e dia.
**Como fazer / comando:**
```
worklogAuthor = "ana.silva" AND worklogDate >= startOfWeek()
GET /rest/api/2/issue/TG-100/worklog
```
**Observações:** conhecimento geral: o worklog tem `author`, `started`, `timeSpentSeconds`. Edit All Worklogs permite alterar o de terceiros (confirmado). `startOfWeek()` confirmado.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/managing-project-permissions-938847145.html
**Uso possível no nosso painel:** horas por pessoa; dias sem worklog como sinal auxiliar de ausência (nunca prova).

---

## 5. Histórico, changelog e auditoria

### Aba History e changelog da issue
**Finalidade:** ver quem mudou o quê e quando em cada campo.
**Como fazer / comando:**
Na issue: aba "History". API:
```
GET /rest/api/2/issue/TG-100?expand=changelog
POST /rest/api/2/search   {"jql":"project = TG","expand":["changelog"],"maxResults":50}
```
**Observações:** secundário: cada entrada tem `author`, `created` e `items[]` com `field`, `fromString`, `toString`, `from`, `to`. Na busca com expand=changelog a lista pode vir truncada (relato do Cloud: 100 entradas); existe `GET /rest/api/2/issue/{key}/changelog` paginado (a confirmar em DC). A página oficial de DC não foi lida.
**Fonte:** https://support.atlassian.com/jira/kb/how-to-analyze-the-history-or-changelog-of-an-issue-in-jira/
**Uso possível no nosso painel:** base da auditoria (troca de responsável, vencimento, labels) e do tempo por status.

### Mudanças de status a partir do changelog
**Finalidade:** reconstruir a linha do tempo de status para lead/cycle time.
**Como fazer / comando:**
```
para cada history em changelog.histories ordenado por created:
  para cada item em history.items onde field == "status":
    registrar (created, fromString, toString)
```
**Observações:** conhecimento geral. Tempo entre `created` da issue e a primeira entrada em "In Progress" é a espera; até a resolução, lead time. Renomear status não reescreve o histórico antigo (os ids `from/to` permanecem).
**Fonte:** https://support.atlassian.com/jira/kb/how-to-analyze-the-history-or-changelog-of-an-issue-in-jira/
**Uso possível no nosso painel:** cálculo de lead/cycle time e aging por status.

### Operadores de histórico no JQL (WAS, CHANGED)
**Finalidade:** buscar issues por valores passados ou mudanças em um campo.
**Como fazer / comando:**
```
assignee CHANGED AFTER -7d
assignee WAS "ana.silva"
due CHANGED BY "ana.silva" DURING (startOfWeek(), now())
status CHANGED FROM "In Progress" TO "Done" AFTER startOfMonth()
```
**Observações:** secundário: predicados AFTER, BEFORE, BY, DURING, ON, FROM, TO; suporte limitado a certos campos (assignee, fixVersion, priority, reporter, resolution, status e outros conforme a versão; conferir `due` e `labels`). Sem predicado, em bases grandes, é pesado. Bug conhecido de resultados errados com histórico corrompido (JRASERVER-78497).
**Fonte:** https://support.atlassian.com/jira-work-management/docs/advanced-search-reference-jql-operators/
**Uso possível no nosso painel:** pré-filtro barato, no servidor, das issues que mudaram responsável/vencimento recentemente.

### Audit log (log de auditoria)
**Finalidade:** registrar atividades administrativas e de configuração (não é o histórico de campos das issues).
**Como fazer / comando:**
Administração > System > Audit log; logs por projeto em Project settings. Exportar CSV (até 100 000 eventos). API:
```
GET /rest/auditing/1.0/events
```
**Observações:** confirmado (DC 11.3): cobertura Off / Base / Advanced (só DC) / Full; cada evento pode trazer IP de origem, nó e método; retenção padrão de 3 anos em instalações novas e 20 anos em migradas, teto de 10 milhões de registros; arquivos de log por nó (até 100 de 100 MB). Permissão: administrador do sistema ou Jira Administrator; admin de projeto vê o log do projeto (Administer + Browse). `plugin.audit.log.view.sysadmin.only=true` restringe. Secundário: o endpoint antigo `/rest/api/2/auditing/record` foi substituído (desde 8.8); parâmetros de data não confirmados.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/auditing-in-jira-applications-938847740.html
**Uso possível no nosso painel:** nenhum para alterações de campos de issues (isso vem do changelog); útil só para alterações de workflow/permissão que expliquem mudanças de comportamento.

### Changelog de issue versus audit log
**Finalidade:** evitar usar a fonte errada na auditoria de alterações.
**Como fazer / comando:**
Campos de issue (assignee, due date, labels): changelog (`expand=changelog`). Configuração (workflow, permissão, campos): Audit log.
**Observações:** conclusão derivada das duas fontes; confirmar no servidor se eventos de issue aparecem no Audit log em cobertura Full.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/auditing-in-jira-applications-938847740.html
**Uso possível no nosso painel:** define que a aba de auditoria lê o changelog.

---

## 6. Busca, índice e desempenho

### Campos de data e pessoas no JQL
**Finalidade:** montar consultas de prazo, fluxo e carga.
**Como fazer / comando:**
```
project = TG AND assignee = "ana.silva" AND resolution = Unresolved
due < startOfDay() AND resolution = Unresolved
resolved >= startOfMonth() AND resolved <= endOfMonth()
created >= -30d
updated >= -1d
```
**Observações:** funções de data confirmadas: `startOfDay/Week/Month/Year()`, `endOf...()`, `now()`; também `currentUser()`, `membersOf()`, `linkedIssues()`, `watchedIssues()`. `due` é só data; `created`, `updated`, `resolved` têm hora (confirmar o fuso, America/Manaus). Em DC o assignee no JQL costuma ser o username.
**Fonte:** https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937709.html
**Uso possível no nosso painel:** consultas base de atrasadas, vencendo e concluídas no período.

### Paginação e limites da API de busca
**Finalidade:** buscar muitas issues sem estourar limites.
**Como fazer / comando:**
```
POST /rest/api/2/search
{"jql":"project = TG ORDER BY key","startAt":0,"maxResults":100,"fields":["summary","status","assignee","duedate"]}
```
**Observações:** conhecimento geral: `maxResults` é limitado por configuração do servidor (padrão do teto: 1000); iterar `startAt` até `total`; restringir `fields`; pedir `expand=changelog` só quando necessário; ordenar por campo estável.
**Fonte:** https://developer.atlassian.com/server/jira/platform/rest/v10002/intro
**Uso possível no nosso painel:** parâmetros do cliente de sincronização.

### Índice e reindexação
**Finalidade:** o índice sustenta a busca JQL; índice defasado dá resultados incorretos.
**Como fazer / comando:**
Administração > System > Indexing (Background ou Lock Jira and rebuild); reindex por API descrito em KB da Atlassian.
**Observações:** secundário: background reindex reconstrói entrada a entrada com o Jira no ar; foreground apaga e recria; em cluster a recomendação é reindexar um nó fora do balanceador; no OpenSearch a distinção some. Mudanças de time tracking pedem reindex (confirmado). Efeito para nós: dados recém-alterados podem demorar a aparecer no JQL; a leitura de uma issue por chave vem do banco.
**Fonte:** https://confluence.atlassian.com/jirakb/jira-indexing-faq-776654790.html
**Uso possível no nosso painel:** explicar defasagem entre busca e detalhe; alertar se contagens divergirem.

### Boas práticas de consultas pesadas
**Finalidade:** reduzir carga no Jira ao sincronizar.
**Como fazer / comando:**
- Sincronização incremental: `updated >= último_sync` com `project` fixo.
- Evitar `WAS`/`CHANGED` sem predicado e `text ~` em bases grandes.
- Pedir campos mínimos, paginar, espaçar chamadas, um cliente por vez.
- Buscar o changelog só das issues que mudaram.
**Observações:** parte secundária, parte conhecimento geral; o KB de webhooks alerta que filtros JQL lentos aumentam a latência de todos os eventos.
**Fonte:** https://support.atlassian.com/jira/kb/best-practices-on-working-with-webhooks-in-jira-data-center/
**Uso possível no nosso painel:** desenho do agendador de sincronização incremental.

---

## 7. Exportação e relatórios de dados

### Exportação do Issue Navigator
**Finalidade:** extrair resultados de busca para planilha.
**Como fazer / comando:**
Issue Navigator > Export > CSV (todos os campos ou campos atuais) e outros formatos conforme a versão.
**Observações:** conhecimento geral (nenhuma página oficial lida): há limite de linhas configurável; datas saem no formato do usuário; o CSV não traz o histórico.
**Fonte:** nenhuma página oficial lida nesta pesquisa (ver https://confluence.atlassian.com/jiracoreserver/advanced-searching-functions-reference-939937709.html para as funções usadas no filtro)
**Uso possível no nosso painel:** gerar fixtures a partir de um export, sem acesso ao vivo.

### Consulta direto em planilha
**Finalidade:** alimentar Excel/Sheets com dados do Jira.
**Como fazer / comando:**
Em DC o caminho usual é CSV exportado ou apps de marketplace; o recurso de consulta em planilha é Cloud/apps.
**Observações:** só Cloud ou apps de terceiros; não verificado em fonte oficial.
**Fonte:** nenhuma página oficial lida nesta pesquisa
**Uso possível no nosso painel:** nenhum.

---

## 8. Medições de fluxo

### Definições (lead time, cycle time, throughput, WIP, aging)
**Finalidade:** padronizar métricas.
**Como fazer / comando:**
- Lead time: `resolved - created`.
- Cycle time: tempo nos status "de trabalho". A documentação do Control Chart o define como o tempo efetivamente trabalhando na issue, incluindo retrabalho por reabertura, dependente das colunas selecionadas.
- Throughput: contagem de issues resolvidas por período (`resolved >= ... AND resolved <= ...`).
- WIP: contagem de issues com `statusCategory = "In Progress"` por pessoa/time.
- Aging: agora menos a entrada no status atual (changelog).
**Observações:** confirmado para cycle time e lead time; demais por definição usual.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/control-chart-938845628.html
**Uso possível no nosso painel:** cards de fluxo (lead/cycle, vazão, WIP, itens envelhecidos).

### Control Chart nativo
**Finalidade:** ver cycle time por issue com média móvel e desvio padrão.
**Como fazer / comando:**
Quadro > Reports > Control Chart; escolher período, colunas e Quick Filters.
**Observações:** confirmado: a média móvel é por issues (20% das exibidas, ímpar, mínimo 5) e não por tempo (versões antigas: janela de 9 issues); escala linear abaixo de 30 dias e raiz cúbica a partir de 30. Depende das colunas do quadro. Bug conhecido com formato de data com hífen (JSWSERVER-10230, secundário).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/control-chart-938845628.html
**Uso possível no nosso painel:** conferência cruzada dos nossos cálculos de cycle time.

### Cumulative Flow Diagram (CFD)
**Finalidade:** visualizar quantidade de issues por status ao longo do tempo para achar gargalos.
**Como fazer / comando:**
Quadro > Reports > Cumulative Flow Diagram.
**Observações:** secundário: área empilhada por status; relatos de inexatidão (JSWSERVER-2450) e pedido de gadget (GHS-5662). Para reproduzir: contar por dia as issues em cada status reconstruído do changelog. A página de uso do CFD não foi lida (URL tentada deu 404).
**Fonte:** https://confluence.atlassian.com/display/JIRASOFTWARESERVER078/Reporting
**Uso possível no nosso painel:** gráfico de acúmulo por etapa, calculado por nós a partir do changelog.

### Métricas por JQL e changelog
**Finalidade:** calcular fluxo sem depender dos relatórios do quadro.
**Como fazer / comando:**
```
# Vazão das últimas 4 semanas
project = TG AND resolved >= -28d ORDER BY resolved
# Quem iniciou trabalho nos últimos 7 dias
status CHANGED TO "In Progress" AFTER -7d
# Em andamento sem atualização
statusCategory = "In Progress" AND updated <= -5d
```
**Observações:** `updated` muda com qualquer alteração (comentário, label), logo é indicador fraco de "parada". Dias úteis exigem feriados e ausências (YAML do projeto, não do Jira).
**Fonte:** https://support.atlassian.com/jira-work-management/docs/advanced-search-reference-jql-operators/
**Uso possível no nosso painel:** cards de vazão, abertas sem movimento, atrasadas.

---

## 9. Ausências e capacidade

### Capacidade no Advanced Roadmaps (Plans)
**Finalidade:** planejar capacidade por equipe e iteração.
**Como fazer / comando:**
Plans (Advanced Roadmaps, incluído no Jira Software DC) > Teams > capacidade por sprint.
**Observações:** secundário: capacidade é por equipe; sem ausência por pessoa nem calendário de feriados por pessoa; a gestão de capacidade individual não é suportada na interface melhorada (Advanced Roadmaps DC 3.29); pedido aberto JRACLOUD-93550.
**Fonte:** https://confluence.atlassian.com/advancedroadmapsserver0329/managing-teams-1021219053.html
**Uso possível no nosso painel:** justifica manter ausências e feriados em YAML versionado.

### Férias e ausências (não nativas)
**Finalidade:** registrar quem está ausente.
**Como fazer / comando:**
Não há recurso nativo de férias no Jira DC. Alternativas: apps de marketplace (ex.: ActivityTimeline), issues de "ausência" ou calendário externo.
**Observações:** secundário (comunidade Atlassian); confirmar se o time usa issues de ausência.
**Fonte:** https://community.atlassian.com/forums/Advanced-Planning-in-Jira/PTO-Vacation-accounting-in-a-Plan/td-p/1648231
**Uso possível no nosso painel:** ausências permanecem no YAML; opcionalmente ler issues de ausência, se existirem.

---

## 10. Automação, notificações, webhooks, usuários

### Automação nativa
**Finalidade:** regras "quando/se/então" (preencher campo, comentar, atribuir).
**Como fazer / comando:**
Project settings > Automation; global em Administração > System > Global automation.
**Observações:** secundário: limites de serviço configuráveis em DC: JQL agendado retorna no máximo 1000 issues por padrão (`short.scheduled.interval.issue.limit`); teto de 5000 execuções por hora; detecção de loop em 10 níveis; uma execução simultânea de regra agendada. A versão embutida no DC 10/11 não foi confirmada.
**Fonte:** https://confluence.atlassian.com/automation111/automation-service-limits-1652460774.html
**Uso possível no nosso painel:** entender mudanças automáticas no changelog (autor = usuário da regra).

### Webhooks
**Finalidade:** o Jira avisa um endpoint quando uma issue muda.
**Como fazer / comando:**
Administração > System > WebHooks (eventos como `jira:issue_updated`, com filtro JQL opcional).
**Observações:** secundário: o JQL do filtro roda a cada evento; filtros lentos atrasam; a partir do DC 10.0 o envio é assíncrono. Nomes de campos no registro via REST variam entre fontes: validar.
**Fonte:** https://confluence.atlassian.com/jirakb/best-practices-on-working-with-webhooks-in-jira-1180143465.html
**Uso possível no nosso painel:** alternativa futura ao polling (exige rede alcançável).

### Notificações
**Finalidade:** definir e-mails por evento (criada, atribuída, comentada).
**Como fazer / comando:**
Administração > Issues > Notification schemes.
**Observações:** conhecimento geral; página não lida.
**Fonte:** nenhuma página oficial lida nesta pesquisa
**Uso possível no nosso painel:** nenhum.

### Usuários inativos e licenças
**Finalidade:** controlar licenças e preservar histórico.
**Como fazer / comando:**
Administração > User management > Users (desativar em vez de excluir).
**Observações:** fonte apenas de fornecedor (não oficial): desativar libera licença e mantém o histórico; excluir quebra menções e histórico. Issues podem continuar atribuídas a usuário inativo.
**Fonte:** https://votazz.co/2026/01/14/jira-inactive-users-license-audit/
**Uso possível no nosso painel:** regra de Qualidade dos dados: responsável inativo.

### SLA e Jira Service Management
**Finalidade:** metas de tempo em filas de atendimento.
**Como fazer / comando:**
Somente em projetos JSM.
**Observações:** fora do escopo do painel de tasks; não pesquisado.
**Fonte:** nenhuma página lida
**Uso possível no nosso painel:** nenhum.

---

## Perguntas que só o servidor real responde

1. Versão exata do Jira DC (afeta endpoints, limites e operadores) e a URL base/contexto.
2. Nomes reais dos status por workflow/projeto e a quais categorias pertencem; há status de espera ou bloqueio?
3. Todas as issues "done" têm `resolution`? Existe status done sem resolution (ou o inverso)?
4. Ids dos campos personalizados (`customfield_NNNNN`): Story Points, Sprint, Epic Link/Parent Link, campo de Review, labels de atraso.
5. Tipo e direção do link da Review (nome do tipo, texto outward/inward) e do bloqueio.
6. O assignee no JQL e na API é username (`ana.silva`), chave ou outro identificador? Bate com `papeis.yaml`?
7. Fuso de `created/updated/resolved` e do usuário de serviço (America/Manaus).
8. Teto de `maxResults` da busca e se o changelog vem truncado na busca.
9. Existe `GET /issue/{key}/changelog` paginado nesta versão?
10. O changelog registra `duedate`, `assignee` e `labels` com `fromString/toString` esperados? Quem é o autor de mudanças feitas por automação?
11. Quais JQL com `WAS/CHANGED` funcionam, em quais campos, e quão lentos são?
12. Horas por dia e dias por semana do time tracking; o time usa estimativa e worklog?
13. Permissões do usuário de serviço (Browse, ver histórico) e se há issue-level security escondendo issues.
14. Nível de cobertura do Audit log e se o usuário de serviço pode lê-lo (provavelmente desnecessário).
15. Há automações, webhooks ou apps (ScriptRunner, Tempo, ActivityTimeline) que mudam campos ou guardam ausências?
16. Há usuários inativos como responsáveis? Sub-tarefas devem contar na carga?
17. Frequência e janela de reindexação; defasagem da busca em relação ao detalhe da issue.
