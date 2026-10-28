# JQL do Jira Data Center: do básico ao avançado

Referência local, em pt-BR, de JQL (Jira Query Language) para **Jira Data Center** (a empresa usa Data Center, não Cloud).

## Como ler este documento

- **Origem do conteúdo:** pesquisa na documentação pública Atlassian (somente leitura na web, sem acessar nenhuma instância de Jira). As páginas consultadas aparecem como "Jira Software Data Center 11.3" na documentação; o texto das páginas de referência (campos, operadores, funções, palavras-chave) muda pouco entre 8.x, 9.x, 10.x e 11.x, mas **confirme na versão instalada** (`matriz-compatibilidade.md`, nesta pasta de docs, é o lugar para registrar a versão real).
- **Texto parafraseado.** Exemplos de JQL marcados **[doc]** vêm da documentação (às vezes com chave/nome trocado por um fictício). Exemplos marcados **[composto]** foram montados por mim a partir das regras da documentação. Na seção 11 (Padrões prontos), todos são **[composto]**, salvo indicação.
- **NENHUM exemplo foi testado em servidor real.** Valide cada consulta contra o Jira antes de depender dela (só depois que o usuário liberar o acesso ao Jira, conforme o `CLAUDE.md`).
- Nomes fictícios: usuário `ana.silva`, `bruno.lima`, `carla.souza`, grupo `squad-alfa`, projeto `TG`.
- Itens marcados **[Cloud]** existem apenas no Jira Cloud ou se comportam diferente lá; não use no Data Center.
- Nomes de status/resolução/link (`Done`, `In Progress`, `Reopened`, `Code Review`, `blocks`) são exemplos; os nomes reais estão no workflow da empresa e em `Filtros_Status_tool.txt`.

Páginas-base (confluence.atlassian.com, Data Center 11.3 quando a página informa):

- Advanced searching: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html
- Fields reference: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html
- Operators reference: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html
- Functions reference: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html
- Keywords reference: https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html
- Search syntax for text fields: https://confluence.atlassian.com/jirasoftwareserver/search-syntax-for-text-fields-939938747.html

Sumário: 1 Estrutura; 2 Campos; 3 Operadores; 4 Palavras-chave; 5 Funções; 6 Datas; 7 Aspas e reservados; 8 Ordenação; 9 Histórico; 10 Texto; 11 Padrões prontos.

---

## 1. Estrutura de uma consulta

### Cláusula (campo operador valor)
**Finalidade:** Unidade básica do JQL: um campo, um operador e um ou mais valores ou funções.
**Exemplo:**
```jql
project = "TG"
```
**Observações:** [doc] Não existe `SELECT`; é um filtro com sintaxe parecida com SQL. Funções (ex.: `currentUser()`) podem substituir o valor. Uma consulta que não cabe na busca básica não volta do avançado para o básico.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

### Combinar cláusulas e precedência
**Finalidade:** Juntar cláusulas com `AND`/`OR` e controlar a ordem de avaliação com parênteses.
**Exemplo:**
```jql
status = "In Progress" AND (project = "TG" OR assignee = ana.silva)
```
**Observações:** [doc] `AND` agrupa antes de `OR`; sem parênteses, `a OR b AND c` é lido como `a OR (b AND c)`. Parênteses em volta de cláusulas só com `AND` não mudam nada. Em filtros de gestão, use parênteses sempre.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

### Estrutura completa (filtro + ORDER BY)
**Finalidade:** Forma geral: condições e, no fim, a ordenação.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND assignee = ana.silva ORDER BY duedate ASC, priority DESC
```
**Observações:** [composto] O `ORDER BY` vem depois de todas as condições e não pode ser seguido de outras condições.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

---

## 2. Campos

Resumo da página de campos (a lista exata de operadores por campo está lá). Prefira **ID** a nome quando o nome pode mudar ou repetir (componente, versão, tipo, status, prioridade).

### project
**Finalidade:** Filtrar pelo projeto (chave, nome ou ID).
**Exemplo:**
```jql
project = TG
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN`. Se uma chave coincide com o nome de outro projeto, a chave prevalece. `IN` aceita `projectsLeadByUser()`, `projectsWhereUserHasPermission()`, `projectsWhereUserHasRole()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### issuetype (alias type)
**Finalidade:** Filtrar pelo tipo de issue (Bug, Task, Story, Sub-task...).
**Exemplo:**
```jql
issuetype IN (Bug, Task)
```
**Observações:** [doc] Pelo ID é mais seguro (`issuetype = 2`): IDs são únicos e imutáveis. A captura da página deixou dúvida sobre `IS/IS NOT`; `=`, `!=`, `IN`, `NOT IN` são certos. Funções: `standardIssueTypes()`, `subtaskIssueTypes()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### status
**Finalidade:** Filtrar pelo status atual (ou passado, com `WAS`/`CHANGED`).
**Exemplo:**
```jql
status IN ("To Do", "In Progress")
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN WAS WAS IN WAS NOT WAS NOT IN CHANGED`. Os operadores `WAS` aceitam só **nome** do status (e o ID). Nome com espaço exige aspas.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### statusCategory
**Finalidade:** Filtrar pela categoria do status (To Do, In Progress, Done), independente do nome do status no workflow.
**Exemplo:**
```jql
statusCategory != Done
```
**Observações:** [doc] Operadores: `= != IN NOT IN`. Não aceita `IS`, `WAS` nem `CHANGED`. Nomes: `"To Do"`, `"In Progress"`, `Done`. Ideal para "aberta/concluída" quando cada squad tem workflow diferente.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### resolution
**Finalidade:** Filtrar pela resolução (Done, Won't Do...); `Unresolved` é o atalho para "sem resolução".
**Exemplo:**
```jql
resolution = Unresolved
```
**Observações:** [doc] Operadores: `= != < > <= >= IS IS NOT IN NOT IN WAS WAS IN WAS NOT WAS NOT IN CHANGED`. Equivalente: `resolution IS EMPTY`. Regra do projeto: "concluída" = categoria Done **e** resolution preenchida; a divergência vai para Qualidade dos dados (padrão 19).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### assignee
**Finalidade:** Filtrar pelo responsável atual (ou anterior).
**Exemplo:**
```jql
assignee = ana.silva
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN WAS WAS IN WAS NOT WAS NOT IN CHANGED`. A doc diz que aceita nome, ID ou e-mail; `=`/`!=` aceitam `currentUser()`, `IN` aceita `membersOf()`. No Data Center o identificador é o username; no Cloud é o accountId **[Cloud]**. `assignee != x` não traz issues sem responsável.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### reporter
**Finalidade:** Filtrar por quem reportou a issue.
**Exemplo:**
```jql
reporter = bruno.lima
```
**Observações:** [doc] Mesmos operadores e regras do `assignee` (inclui `WAS` e `CHANGED`). No fluxo do projeto o Reporter costuma ser o PL.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### creator
**Finalidade:** Filtrar por quem criou a issue (pode diferir do reporter).
**Exemplo:**
```jql
creator = ana.silva
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN WAS WAS IN WAS NOT WAS NOT IN` (sem `CHANGED`). Campo presente nas versões recentes; confirme na versão instalada.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### created
**Finalidade:** Filtrar pela data de criação.
**Exemplo:**
```jql
created >= startOfWeek()
```
**Observações:** [doc] Operadores: `= != < > <= >= IS IS NOT IN NOT IN`. Aceita data absoluta (`"2024/01/01"`, `"2024-01-01 09:30"`) e relativa (`"-30m"`, `"-2w"`). Sempre entre aspas (número sem aspas vira milissegundos desde 1970). Ver seção 6.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### updated
**Finalidade:** Filtrar pela data da última atualização (qualquer mudança).
**Exemplo:**
```jql
updated < "-2w"
```
**Observações:** [doc] Operadores: `= != > >= < <=`. Mesmos formatos de `created`. Comentário e worklog também atualizam a issue.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### resolved (resolutionDate)
**Finalidade:** Filtrar pela data/hora em que a issue foi resolvida.
**Exemplo:**
```jql
resolved >= startOfWeek() AND resolved <= endOfWeek()
```
**Observações:** [doc] Mesmos operadores/formatos de `created`. Só existe com resolução; limpar a resolução ao reabrir esvazia o campo (`resolved IS EMPTY`).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### due (duedate)
**Finalidade:** Filtrar pela data de vencimento.
**Exemplo:**
```jql
due < now() AND statusCategory != Done
```
**Observações:** [doc] Mesmos operadores de `created`, mas **só data, sem hora**; relativos só em `w` e `d`. `due` e `duedate` funcionam. Para "antes de hoje" use `startOfDay()`. `duedate IS EMPTY` acha sem vencimento.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### labels
**Finalidade:** Filtrar por rótulos.
**Exemplo:**
```jql
labels IN (atraso_justificado, bloqueio_externo)
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN`. Sem `~`, sem `WAS`, sem `CHANGED`: o histórico de labels **não** é pesquisável por JQL nativo. `labels IS EMPTY` acha issues sem rótulo; `labels != x` não traz issues sem nenhum rótulo.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### priority
**Finalidade:** Filtrar por prioridade (comparável pela ordem configurada).
**Exemplo:**
```jql
priority >= High
```
**Observações:** [doc] Operadores incluem `< > <= >=`, `WAS*` e `CHANGED`. A comparação segue a ordem das prioridades no Jira; use ID quando possível.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### parent
**Finalidade:** Achar sub-tarefas de uma issue.
**Exemplo:**
```jql
parent = TG-1234
```
**Observações:** [doc] Operadores: `= != IN NOT IN`. Requer sub-tarefas habilitadas. No Data Center cobre sub-tarefa -> pai; ligação épico -> issue usa `"Epic Link"`. No Cloud `parent` também cobre épico -> filhos **[Cloud]**.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### "Epic Link"
**Finalidade:** Achar issues vinculadas a um épico (Jira Software).
**Exemplo:**
```jql
"Epic Link" = TG-31
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN`; aceita chave, nome ou ID. `"Epic Link" IS EMPTY` acha issues sem épico. No Cloud foi substituído por `parent` **[Cloud]**.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### fixVersion
**Finalidade:** Filtrar pela versão de correção/entrega.
**Exemplo:**
```jql
fixVersion IN unreleasedVersions(TG)
```
**Observações:** [doc] Operadores: `= != < > <= >= ~ !~ IS IS NOT IN NOT IN WAS WAS IN WAS NOT WAS NOT IN CHANGED`. Comparações usam a ordem das versões do projeto. Funções de versão na seção 5.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### component
**Finalidade:** Filtrar por componente do projeto.
**Exemplo:**
```jql
component = "Backend"
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN`. Use ID se o nome se repete entre projetos. `IN` aceita `componentsLeadByUser()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### sprint
**Finalidade:** Filtrar por sprint (Jira Software).
**Exemplo:**
```jql
sprint IN openSprints() AND project = TG
```
**Observações:** [doc] Operadores: `= != IS IS NOT IN NOT IN`. Aceita nome (parcial funciona) ou ID; prefira ID. `sprint IS EMPTY` = fora de sprint.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### text
**Finalidade:** Busca de texto livre em vários campos de uma vez (resumo, descrição, ambiente, comentários, campos de texto livre).
**Exemplo:**
```jql
text ~ "integração"
```
**Observações:** [doc] Só aceita `~`. Mais lento e menos preciso que apontar o campo (`summary ~`, `description ~`). Ver seção 10.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### summary, description, environment
**Finalidade:** Busca de texto em um campo específico.
**Exemplo:**
```jql
summary ~ "Error saving file"
```
**Observações:** [doc] Operadores: `~ !~ IS IS NOT`. Sintaxe de texto (curingas, fuzzy, proximidade). Não usar `=`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### comment
**Finalidade:** Buscar issues cujos comentários contêm um texto.
**Exemplo:**
```jql
comment ~ "login failed"
```
**Observações:** [doc] Só `~` e `!~`. Frase exata com aspas escapadas: `comment ~ "\"login failed\""`. Não filtra por autor/data do comentário.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### worklogDate
**Finalidade:** Filtrar pela data em que foi lançado tempo (worklog).
**Exemplo:**
```jql
worklogDate > "2024/01/01" AND worklogDate < "2024/02/01"
```
**Observações:** [doc] Operadores: `= != > >= < <= IN NOT IN`; sem `IS`/`WAS`. Formatos `yyyy/MM/dd` ou `yyyy-MM-dd` e relativos `"-1d"`/`"-1w"`; a data é guardada como data local, **sem ajuste de fuso**. Requer time tracking habilitado.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### worklogAuthor
**Finalidade:** Achar issues em que um usuário lançou tempo.
**Exemplo:**
```jql
worklogAuthor = ana.silva AND worklogDate >= startOfMonth()
```
**Observações:** [doc] Operadores: `= != IN NOT IN`; aceita `currentUser()` e `membersOf()`. Exige permissão de ver worklogs. **Pegadinha [composto]:** as duas cláusulas são avaliadas de forma independente na mesma issue (worklog da Ana em outro mês + worklog de outra pessoa neste mês também casa). Horas por pessoa/dia vêm da API de worklog, não do JQL.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### timespent
**Finalidade:** Filtrar pelo tempo gasto registrado.
**Exemplo:**
```jql
timespent > 5d
```
**Observações:** [doc] Requer time tracking. Unidades `w`, `d`, `h`, `m`. A captura da tabela de operadores ficou confusa; comparações e `IS EMPTY` devem funcionar, confirme.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### originalEstimate
**Finalidade:** Filtrar pela estimativa original.
**Exemplo:**
```jql
originalEstimate > 2d
```
**Observações:** [doc] Operadores: `= != < > <= >= IS IS NOT IN NOT IN`. Requer time tracking. `originalEstimate IS EMPTY` acha issues sem estimativa. O dia de trabalho segue a configuração do Jira (ex.: 8h), não 24h.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### remainingEstimate
**Finalidade:** Filtrar pela estimativa restante.
**Exemplo:**
```jql
remainingEstimate > 4h
```
**Observações:** [doc] Mesmos operadores de `originalEstimate`. Requer time tracking.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### issueLinkType
**Finalidade:** Achar issues que têm um tipo de link (blocks, relates...), sem dizer a qual issue.
**Exemplo:**
```jql
issueLinkType = "blocks"
```
**Observações:** [doc] Operadores: `= != IN NOT IN`. `!=` também devolve issues sem nenhum link. Só links para issues da mesma instância. Para links com uma issue específica use `linkedIssues()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### issue in linkedIssues()
**Finalidade:** Achar as issues ligadas a uma issue específica (opcionalmente por tipo de link).
**Exemplo:**
```jql
issue IN linkedIssues(TG-123, "is reviewed by")
```
**Observações:** [doc] O nome do link é **sensível a maiúsculas**; use o texto da direção como aparece no Jira. O tipo de link de review depende da configuração da empresa.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### issuekey (key, id, issue)
**Finalidade:** Filtrar por chave/ID da issue.
**Exemplo:**
```jql
issuekey IN (TG-25, TG-12)
```
**Observações:** [doc] Operadores: `= != < > <= >= IS IS NOT IN NOT IN`. `IN` aceita `issueHistory()`, `linkedIssues()`, `updatedBy()`, `votedIssues()`, `watchedIssues()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### Campo personalizado (cf[ID])
**Finalidade:** Filtrar por campo customizado, de preferência pelo ID.
**Exemplo:**
```jql
cf[10003] IN ("Londres", "Paris")
```
**Observações:** [doc] Operadores dependem do tipo (número/data: comparação; seleção: `= != IS IN`; texto: `~ !~ IS`). **`WAS` e `CHANGED` não funcionam em campos customizados.** O ID evita ambiguidade com campos de sistema homônimos (o de sistema vence). O ID acima é fictício.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### Outros campos úteis
**Finalidade:** Atalhos para checagens de higiene.
**Exemplo:**
```jql
attachments IS NOT EMPTY OR watcher = currentUser() OR votes >= 3
```
**Observações:** [doc] `attachments` aceita só `IS`/`IS NOT`. `watcher`/`voter` usam `=` e `IN membersOf()` e exigem permissão de ver observadores/votantes. `votes`, `watchers`, `workratio` são numéricos (`workratio` = tempo gasto / estimativa original x 100). `lastViewed` é por usuário. `filter = <id>` reaproveita filtro salvo. `level` só com segurança por nível de issue.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

---

## 3. Operadores

### = e !=
**Finalidade:** Igual e diferente (valor exato).
**Exemplo:**
```jql
reporter = bruno.lima AND assignee != ana.silva
```
**Observações:** [doc] Não servem para campos de texto (use `~`). **`!=` não traz issues com o campo vazio**: `assignee != ana.silva OR assignee IS EMPTY`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### > >= < <=
**Finalidade:** Comparações em campos ordenáveis (datas, versões, prioridade, números, estimativas).
**Exemplo:**
```jql
created >= "-5d" AND votes < 4
```
**Observações:** [doc] Não valem para campos de texto.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### IN e NOT IN
**Finalidade:** Casar com qualquer valor de uma lista (ou com nenhum).
**Exemplo:**
```jql
assignee IN (ana.silva, bruno.lima) AND status NOT IN (Done, Closed)
```
**Observações:** [doc] Até **3000 operandos**; acima disso a chamada GET da API falha com HTTP 400 no Tomcat (use POST em `/search`). `NOT IN` não traz campos vazios.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### ~ e !~
**Finalidade:** "Contém" (busca de texto) e "não contém".
**Exemplo:**
```jql
summary ~ "win*"
```
**Observações:** [doc] Campos de texto e de versão. Em texto aceita a sintaxe de busca. Em versão é casamento exato com curingas. `!~` não traz campo vazio: `fixVersion !~ "9.0" OR fixVersion IS EMPTY`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### IS e IS NOT
**Finalidade:** Testar vazio/preenchido (só com `EMPTY` ou `NULL`).
**Exemplo:**
```jql
duedate IS EMPTY AND assignee IS NOT EMPTY
```
**Observações:** [doc] Só em campos que aceitam `IS`. Não escreva `= EMPTY`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### WAS e WAS NOT
**Finalidade:** O campo tem (ou teve) o valor / nunca teve o valor.
**Exemplo:**
```jql
status WAS "In Progress"
```
**Observações:** [doc] Só nos campos `assignee, reporter, fixVersion, priority, resolution, status` (a página de campos mostra `creator` também com `WAS`). Casa nome (como era na mudança) e ID. Aceita `BY`, `AFTER`, `BEFORE`, `ON`, `DURING`. `WAS` inclui o valor atual.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### WAS IN e WAS NOT IN
**Finalidade:** Teve (ou nunca teve) qualquer um dos valores da lista.
**Exemplo:**
```jql
status WAS IN ("Resolved", "In Progress") BEFORE "2024/02/20"
```
**Observações:** [doc] Mesmas restrições de campos e predicados do `WAS`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### CHANGED
**Finalidade:** O valor do campo foi alterado (opcionalmente de/para um valor, por quem, quando).
**Exemplo:**
```jql
status CHANGED FROM "In Progress" TO "Open"
```
**Observações:** [doc] Aceita os predicados do `WAS` mais `FROM` e `TO`. Só nos campos com histórico. **`duedate`, `labels`, `summary` e campos customizados não suportam `CHANGED`/`WAS` no JQL nativo do Data Center.**
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### Predicados FROM / TO / BY / AFTER / BEFORE / ON / DURING
**Finalidade:** Restringir o histórico (valor anterior, valor novo, autor da mudança, janela de tempo).
**Exemplo:**
```jql
status CHANGED TO Done BY ana.silva DURING (startOfWeek(), now())
```
**Observações:** [doc] `AFTER`/`BEFORE`/`ON` recebem uma data; `DURING` recebe um par `(início, fim)`. **Pegadinha da doc:** `"-1d"` começa à 00:00 do fuso do servidor, enquanto `"-24h"` conta 24 horas exatas para trás. `BY` aceita username ou `currentUser()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

---

## 4. Palavras-chave

### AND / OR / NOT
**Finalidade:** Combinar ou negar cláusulas.
**Exemplo:**
```jql
NOT (reporter = ana.silva OR reporter = bruno.lima)
```
**Observações:** [doc] `NOT` nega uma cláusula ou grupo entre parênteses; para uma só cláusula é melhor `!=`, `!~`, `NOT IN` ou `IS NOT`. Para vários valores do mesmo campo prefira `IN` a vários `OR`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

### EMPTY / NULL
**Finalidade:** Representam "sem valor" (equivalentes).
**Exemplo:**
```jql
duedate IS EMPTY
```
**Observações:** [doc] Só com campos que aceitam `IS`/`IS NOT`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

### ORDER BY / ASC / DESC
**Finalidade:** Ordenar o resultado por um ou mais campos.
**Exemplo:**
```jql
project = TG ORDER BY duedate ASC, priority DESC, created ASC
```
**Observações:** [doc] Sem `ASC`/`DESC` vale a ordem padrão do campo. Ordenar por componente ou versão agrupa primeiro por projeto. `ASC`/`DESC` só existem depois de um campo do `ORDER BY`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

---

## 5. Funções

Funções **(JSM)** (aprovações, SLA) só existem com Jira Service Management e ficam fora desta referência.

### currentUser()
**Finalidade:** Substitui o usuário logado.
**Exemplo:**
```jql
assignee = currentUser() AND statusCategory != Done
```
**Observações:** [doc] Não serve para filtros usados por anônimos. O painel usa uma conta de serviço, então `currentUser()` seria sempre ela: no painel use usernames explícitos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### membersOf(grupo)
**Finalidade:** Expande os membros de um grupo do Jira.
**Exemplo:**
```jql
assignee IN membersOf("squad-alfa")
```
**Observações:** [doc] Vale em `assignee`, `reporter`, `watcher`, `worklogAuthor` com `IN`/`NOT IN`. O painel define squads em `equipes.yaml`, não em grupos do Jira.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### now()
**Finalidade:** Data/hora atual.
**Exemplo:**
```jql
duedate < now() AND statusCategory != Done
```
**Observações:** [doc] Em campos só-data (`due`) compara com a data de hoje.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### startOfDay / startOfWeek / startOfMonth / startOfYear
**Finalidade:** Início do dia/semana/mês/ano atual, com deslocamento opcional.
**Exemplo:**
```jql
created > startOfMonth("-1M")
```
**Observações:** [doc] Sintaxe `startOfWeek([+/-]valor[unidade])`; unidades `y M w d h m`; sem unidade vale a natural da função (`startOfWeek("-1")` = semana passada). O **início da semana depende da localidade**: confirme antes de usar em relatórios semanais.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### endOfDay / endOfWeek / endOfMonth / endOfYear
**Finalidade:** Fim do dia/semana/mês/ano atual, com deslocamento opcional.
**Exemplo:**
```jql
duedate >= startOfDay() AND duedate <= endOfWeek()
```
**Observações:** [doc] Mesma sintaxe e pegadinha de localidade das funções `startOf*`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### openSprints() / closedSprints() / futureSprints()
**Finalidade:** Sprints ainda não concluídas, concluídas e ainda não iniciadas.
**Exemplo:**
```jql
sprint IN openSprints() AND assignee = ana.silva
```
**Observações:** [doc] Requer Jira Software. Segundo a doc, `openSprints()` = não concluídas; `futureSprints()` = não iniciadas.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### linkedIssues(chave [, tipo])
**Finalidade:** Issues ligadas a uma issue, opcionalmente por tipo de link.
**Exemplo:**
```jql
issue IN linkedIssues(TG-123, "is duplicated by")
```
**Observações:** [doc] Tipo de link sensível a maiúsculas; sem o 2º argumento traz todos os tipos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### issueHistory()
**Finalidade:** Issues vistas recentemente pelo usuário logado.
**Exemplo:**
```jql
issue IN issueHistory() AND assignee = currentUser()
```
**Observações:** [doc] Até 60 issues; histórico com mais de 90 dias é apagado. Depende do usuário; inútil para sincronização automática.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### watchedIssues() / votedIssues()
**Finalidade:** Issues que o usuário logado observa / votou.
**Exemplo:**
```jql
issue IN watchedIssues() AND statusCategory != Done
```
**Observações:** [doc] Só para usuários logados.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### lastLogin() / currentLogin()
**Finalidade:** Comparar datas com o início da sessão anterior / atual do usuário.
**Exemplo:**
```jql
updated > lastLogin()
```
**Observações:** [doc] Dependem do usuário logado; evite em automações.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### releasedVersions() / unreleasedVersions()
**Finalidade:** Versões liberadas / não liberadas de um projeto (ou de todos, sem argumento).
**Exemplo:**
```jql
fixVersion IN unreleasedVersions(TG)
```
**Observações:** [doc] Usadas com `IN` em campos de versão.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### latestReleasedVersion() / earliestUnreleasedVersion()
**Finalidade:** Última versão liberada / próxima versão não liberada de um projeto.
**Exemplo:**
```jql
fixVersion = earliestUnreleasedVersion(TG)
```
**Observações:** [doc] Baseadas na **ordem das versões**, não nas datas de lançamento.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### standardIssueTypes() / subtaskIssueTypes()
**Finalidade:** Tipos que não são sub-tarefa / tipos que são sub-tarefa.
**Exemplo:**
```jql
issuetype IN standardIssueTypes() AND project = TG
```
**Observações:** [doc] Úteis para contar só tarefas "pai" sem listar tipos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### cascadeOption(pai [, filho])
**Finalidade:** Casar camadas de um campo de seleção em cascata.
**Exemplo:**
```jql
cf[10100] IN cascadeOption("Brasil", "Manaus")
```
**Observações:** [doc] `none` casa camada vazia. O ID do campo é fictício.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### componentsLeadByUser() / projectsLeadByUser()
**Finalidade:** Issues de componentes / projetos liderados por um usuário (padrão: o logado).
**Exemplo:**
```jql
project IN projectsLeadByUser(ana.silva)
```
**Observações:** [doc] Também existem `projectsWhereUserHasPermission(perm)` e `projectsWhereUserHasRole(papel)`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### updatedBy(usuário [, de [, até]])
**Finalidade:** Issues atualizadas por um usuário (inclui criar issue e editar comentário), em um intervalo.
**Exemplo:**
```jql
issuekey IN updatedBy(ana.silva, "-8d")
```
**Observações:** [doc] Datas `"yyyy/MM/dd"`, `"yyyy-MM-dd"` ou relativas; intervalos menores que um dia arredondam para um dia. Presente nas versões recentes; confirme na instalada.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### issuesWithRemoteLinksByGlobalId()
**Finalidade:** Issues com links remotos de determinado ID global (1 a 100 IDs).
**Exemplo:**
```jql
issue IN issuesWithRemoteLinksByGlobalId(abc, def)
```
**Observações:** [doc] Uso raro; só para integrações.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

---

## 6. Datas e aritmética de datas

### Datas absolutas
**Finalidade:** Escrever uma data fixa.
**Exemplo:**
```jql
created >= "2024/01/01" AND created < "2024/02/01"
```
**Observações:** [doc] Formatos: `"yyyy/MM/dd HH:mm"`, `"yyyy-MM-dd HH:mm"`, `"yyyy/MM/dd"`, `"yyyy-MM-dd"`. Sem hora vale 00:00. **Sempre entre aspas**. `due` e `worklogDate` não têm hora.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### Datas relativas
**Finalidade:** Valores relativos a agora.
**Exemplo:**
```jql
updated >= "-30m" OR created >= "-1w" OR resolved >= "-2d"
```
**Observações:** [doc] Unidades `w` semanas, `d` dias, `h` horas, `m` minutos (padrão). `-` volta no tempo; `+` avança (omitido = `+`). Combinações como `"-4w 2d"` aparecem na doc de operadores.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### "-1d" versus "-24h"
**Finalidade:** Escolher entre "desde 00:00 de ontem" e "últimas 24 horas".
**Exemplo:**
```jql
status CHANGED AFTER "-24h"
```
**Observações:** [doc] `-1d` começa à 00:00 no fuso do servidor; `-24h` conta 24 horas exatas. O fuso do projeto é America/Manaus, mas o do servidor pode ser outro: confirme.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

### Aritmética em funções de data
**Finalidade:** Deslocar o início/fim de períodos.
**Exemplo:**
```jql
created >= startOfWeek("-1") AND created < startOfWeek()
```
**Observações:** [doc] Unidades `y M w d h m` (`M` maiúsculo = mês, `m` minúsculo = minuto). Sem unidade vale a natural da função. `endOfMonth("+15d")` é aceito.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

---

## 7. Aspas e caracteres reservados

### Quando usar aspas
**Finalidade:** Permitir valores com espaço, reservados ou caracteres especiais.
**Exemplo:**
```jql
status = "In Progress" AND labels = "atraso-justificado" AND component = "Tarefas e Rotinas"
```
**Observações:** [doc] Aspas simples ou duplas. Exigem aspas: espaço `+ . , ; ? | * / % ^ $ # @ [ ]` e palavras reservadas (como `and`, `order`, `select`; a lista fica embutida em `JqlStringSupportImpl.java` e não é publicada na documentação). Hífen em label: use aspas por segurança.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

### Escape em campos de texto
**Finalidade:** Procurar literalmente caracteres que a busca de texto trata como operador.
**Exemplo:**
```jql
summary ~ "\\[Rotina\\]"
```
**Observações:** [doc] Em campos de texto, caracteres especiais da sintaxe precisam de **duas barras invertidas** (a doc mostra `version = "\[example\]"`). Os caracteres `+ - & | ! ( ) { } [ ] ^ ~ * ? \ :` não são indexados, então não dá para pesquisá-los como texto.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

---

## 8. Ordenação

### ORDER BY com vários campos
**Finalidade:** Ordenar por vários critérios, em sequência.
**Exemplo:**
```jql
project = TG AND statusCategory != Done ORDER BY assignee ASC, duedate ASC, priority DESC
```
**Observações:** [composto] Tabelas de pessoas no projeto seguem ordem alfabética (sem ranking); a ordenação final pode ser feita no cliente.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

---

## 9. Histórico de campos (changed / was)

### Quais campos têm histórico
**Finalidade:** Saber de antemão o que dá para auditar por JQL.
**Exemplo:**
```jql
assignee CHANGED AFTER startOfWeek()
```
**Observações:** [doc] `WAS`/`CHANGED` valem para `assignee, reporter, fixVersion, priority, resolution, status` (e `creator` com `WAS`). **Não valem** para `duedate`, `labels`, `summary`, campos customizados, `sprint`. [composto] Para auditar vencimento e labels é preciso ler o **changelog** de cada issue pela API REST (`expand=changelog`), na sincronização do app.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### Status em um intervalo (base do tempo em status)
**Finalidade:** Achar issues que estiveram num status durante uma janela.
**Exemplo:**
```jql
status WAS "In Progress" DURING ("2024/03/01", "2024/03/08")
```
**Observações:** [composto] O JQL diz *se* passou pelo status na janela, não *quanto tempo*. A duração exata se calcula no app a partir do changelog.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

---

## 10. Busca por texto

### Curingas
**Finalidade:** Procurar prefixos e padrões.
**Exemplo:**
```jql
summary ~ "integra*" OR summary ~ "te?te"
```
**Observações:** [doc] `?` = um caractere; `*` = zero ou mais. Em JQL, o curinga vai **dentro das aspas**. Curinga no início da palavra tende a ser lento ou não suportado (não confirmado pela doc lida).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/search-syntax-for-text-fields-939938747.html

### Frase exata
**Finalidade:** Casar uma sequência de palavras.
**Exemplo:**
```jql
text ~ "\"Jira Software\""
```
**Observações:** [doc] Aspas internas escapadas com barra invertida.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/search-syntax-for-text-fields-939938747.html

### Fuzzy e proximidade
**Finalidade:** Tolerar erro de grafia (`~` após a palavra) e exigir palavras próximas (`~N` após a frase).
**Exemplo:**
```jql
summary ~ "roam~" OR description ~ "\"atlassian Jira\"~10"
```
**Observações:** [doc] Fuzzy casa grafias parecidas ("roam~" acha "foam"). `"a b"~10` = até 10 palavras de distância. A busca usa **stemming** e ignora palavras comuns ("and", "the", "not"), então negações podem vir "erradas".
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/search-syntax-for-text-fields-939938747.html

### Booleanos, obrigatório/excluído e peso
**Finalidade:** Combinar termos dentro de uma busca de texto.
**Exemplo:**
```jql
text ~ "+relatório -rascunho" OR summary ~ "atlassian^4 Jira"
```
**Observações:** [doc] Operadores em MAIÚSCULAS (`AND`, `OR`, `NOT`, `&&`, `||`, `!`); `+` obrigatório, `-` excluído; `^N` aumenta o peso. `NOT` não pode ficar sozinho. Não iniciar a busca com `(`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/search-syntax-for-text-fields-939938747.html

---

## 11. Padrões prontos para gestão de equipe

Todos **[composto]** a partir da documentação; **nenhum testado em servidor real**. Troque `TG`, `ana.silva` e os nomes de status/links pelos reais. Convenção: "aberta" = `statusCategory != Done` (o painel considera concluída a categoria Done **e** `resolution` preenchida).

### 1. Abertas por responsável
**Finalidade:** Carga atual de uma pessoa.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND assignee = ana.silva ORDER BY duedate ASC
```
**Observações:** Base de qualquer visão de carga.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 2. Abertas do squad (lista de usuários)
**Finalidade:** Carga do squad numa só consulta.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND assignee IN (ana.silva, bruno.lima, carla.souza) ORDER BY assignee ASC, duedate ASC
```
**Observações:** Lista explícita (vinda de `equipes.yaml`) evita depender de grupos do Jira.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 3. Atrasadas (vencimento passado)
**Finalidade:** Issues abertas com vencimento antes de hoje.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND duedate < startOfDay()
```
**Observações:** `due` é só data; `< startOfDay()` exclui as que vencem hoje.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 4. Atrasadas por responsável
**Finalidade:** Atraso de uma pessoa.
**Exemplo:**
```jql
project = TG AND assignee = ana.silva AND statusCategory != Done AND duedate < startOfDay() ORDER BY duedate ASC
```
**Observações:** O projeto não faz ranking de pessoas; use para acompanhamento, não para comparação.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 5. Vencem nos próximos 7 dias
**Finalidade:** Alerta de prazo próximo.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND duedate >= startOfDay() AND duedate <= endOfDay("+7d")
```
**Observações:** No JQL são dias corridos; dias úteis são cálculo do app.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 6. Vencem esta semana
**Finalidade:** Planejamento semanal.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND duedate >= startOfWeek() AND duedate <= endOfWeek()
```
**Observações:** O início da semana depende da localidade do Jira.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 7. Paradas há N dias (sem atualização)
**Finalidade:** Achar trabalho esquecido.
**Exemplo:**
```jql
project = TG AND statusCategory = "In Progress" AND updated <= "-7d" ORDER BY updated ASC
```
**Observações:** Qualquer atividade (comentário, worklog) renova `updated`. Troque `7` por N.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 8. Paradas no mesmo status há mais de N dias
**Finalidade:** Estagnação por status (mais fiel que `updated`).
**Exemplo:**
```jql
project = TG AND status = "In Progress" AND NOT (status CHANGED AFTER "-7d")
```
**Observações:** Negar um `CHANGED` com `NOT (...)` é composição minha; a doc lida não mostra esse uso. Confirmar em servidor real.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 9. Resolvidas na semana
**Finalidade:** Entregas da semana.
**Exemplo:**
```jql
project = TG AND statusCategory = Done AND resolved >= startOfWeek() AND resolved <= endOfWeek()
```
**Observações:** O `statusCategory = Done` junto de `resolved` espelha a regra de "concluída".
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 10. Resolvidas no mês anterior
**Finalidade:** Fechamento mensal.
**Exemplo:**
```jql
project = TG AND resolved >= startOfMonth("-1M") AND resolved < startOfMonth()
```
**Observações:** `M` maiúsculo = mês.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 11. Resolvidas por pessoa num período
**Finalidade:** Entregas de alguém numa janela.
**Exemplo:**
```jql
project = TG AND assignee = ana.silva AND resolved >= "2024/03/01" AND resolved < "2024/04/01"
```
**Observações:** Usa o responsável **atual**; se foi trocado depois, a issue aparece para outra pessoa.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 12. Resolvidas depois do vencimento (fora do prazo)
**Finalidade:** Reunir as entregas da janela para checar atraso.
**Exemplo:**
```jql
project = TG AND resolution IS NOT EMPTY AND duedate IS NOT EMPTY AND resolved >= "2024/03/01" AND resolved < "2024/04/01"
```
**Observações:** O JQL **não compara dois campos** entre si (não existe `resolved > duedate`). A comparação resolved x duedate é feita no app, com atenção ao fuso (America/Manaus) por `resolved` ter hora e `due` não.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 13. Resolvidas no prazo ou antes (por vencimento na janela)
**Finalidade:** Conjunto de entregas com vencimento no período, para checar prazo.
**Exemplo:**
```jql
project = TG AND resolution IS NOT EMPTY AND duedate >= "2024/03/01" AND duedate < "2024/04/01"
```
**Observações:** Mesmo limite do item 12.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 14. Reabertas (saíram de Done)
**Finalidade:** Retrabalho: issues que saíram de Done.
**Exemplo:**
```jql
project = TG AND status CHANGED FROM Done
```
**Observações:** Troque `Done` pelo(s) status final(is) real(is). Com janela: `status CHANGED FROM Done AFTER startOfMonth()`. Alternativa: `status WAS Done AND statusCategory != Done`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 15. Reabertas por nome de status
**Finalidade:** Quando o workflow tem um status "Reopened".
**Exemplo:**
```jql
project = TG AND status CHANGED TO Reopened DURING (startOfMonth(), now())
```
**Observações:** Nome fictício; veja o workflow real.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 16. Sem responsável
**Finalidade:** Trabalho sem dono.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND assignee IS EMPTY
```
**Observações:** `assignee != x` não traz estas issues.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 17. Sem estimativa original
**Finalidade:** Higiene de planejamento.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND originalEstimate IS EMPTY
```
**Observações:** Requer time tracking habilitado.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 18. Sem vencimento
**Finalidade:** Higiene de prazo.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND duedate IS EMPTY
```
**Observações:** Issues sem vencimento não entram nas métricas de prazo.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-keywords-reference-939938744.html

### 19. Concluídas sem resolution (qualidade dos dados)
**Finalidade:** Divergência categoria Done x resolution.
**Exemplo:**
```jql
project = TG AND statusCategory = Done AND resolution IS EMPTY
```
**Observações:** Vai para Qualidade dos dados. Inverso: `statusCategory != Done AND resolution IS NOT EMPTY`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 20. Em andamento sem tempo lançado
**Finalidade:** Higiene de acompanhamento.
**Exemplo:**
```jql
project = TG AND statusCategory = "In Progress" AND timespent IS EMPTY
```
**Observações:** Só útil se a equipe lança tempo.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 21. Review ligada por link
**Finalidade:** Achar issues de review ligadas a uma tarefa.
**Exemplo:**
```jql
issue IN linkedIssues(TG-123, "is reviewed by")
```
**Observações:** O nome do tipo de link depende da configuração da empresa (sensível a maiúsculas). Para qualquer issue com link do tipo: `issueLinkType = "reviews"` (nome fictício).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 22. Abertas com link do tipo blocks
**Finalidade:** Bloqueios abertos.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND issueLinkType = "blocks"
```
**Observações:** `issueLinkType` casa o tipo de link, não a direção.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 23. Passaram por um status numa janela
**Finalidade:** Base para tempo em status (ex.: em code review).
**Exemplo:**
```jql
project = TG AND status WAS "Code Review" DURING ("2024/03/01", "2024/03/15")
```
**Observações:** Só diz que passou; a duração vem do changelog (seção 9).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 24. Entraram em um status por alguém
**Finalidade:** Quem moveu a issue para um status.
**Exemplo:**
```jql
project = TG AND status CHANGED TO "In Progress" BY ana.silva AFTER startOfWeek()
```
**Observações:** `BY` aceita o username de quem fez a transição.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 25. Mudanças de responsável por período
**Finalidade:** Auditoria de troca de responsável.
**Exemplo:**
```jql
project = TG AND assignee CHANGED DURING (startOfWeek(), now())
```
**Observações:** Quem trocou: `assignee CHANGED BY bruno.lima AFTER startOfWeek()`. De/para: `assignee CHANGED FROM ana.silva TO bruno.lima`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 26. Recebidas por repasse
**Finalidade:** Quem recebeu issues repassadas.
**Exemplo:**
```jql
project = TG AND assignee CHANGED TO bruno.lima AFTER "-14d"
```
**Observações:** Combina com a regra de auditoria (só Reporter, coordenadores, gerente ou Squad Leader podem trocar responsável; os demais casos geram alerta).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 27. Mudanças de vencimento por período (candidatas)
**Finalidade:** Restringir quais issues precisam ter o changelog lido para achar mudanças de prazo.
**Exemplo:**
```jql
project = TG AND updated >= startOfWeek() AND duedate IS NOT EMPTY
```
**Observações:** **O JQL nativo não tem `duedate CHANGED`.** O filtro só restringe candidatas; valor antigo/novo e autor vêm do changelog pela API REST. Plugins de terceiros podem adicionar funções de histórico, mas não devem ser assumidos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 28. Mudanças de labels por período (candidatas)
**Finalidade:** Auditoria de labels de atraso justificado.
**Exemplo:**
```jql
project = TG AND labels IN (atraso_justificado) AND updated >= startOfWeek()
```
**Observações:** Mesma limitação do item 27: `labels` não tem `CHANGED`/`WAS`. Quem pôs/tirou a label e quando vem do changelog. Labels removidas não aparecem neste filtro.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 29. Atraso justificado (por label)
**Finalidade:** Atrasadas que têm a label de justificativa.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND duedate < startOfDay() AND labels IN (atraso_justificado)
```
**Observações:** Os nomes de labels ficam em YAML no projeto; o nome aqui é fictício. Para as sem justificativa: `AND (labels IS EMPTY OR labels NOT IN (atraso_justificado))`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 30. Worklog por pessoa
**Finalidade:** Issues em que alguém lançou tempo no período.
**Exemplo:**
```jql
project = TG AND worklogAuthor = ana.silva AND worklogDate >= startOfMonth() AND worklogDate <= endOfMonth()
```
**Observações:** Atenção à pegadinha de `worklogAuthor` (seção 2): as cláusulas são independentes. Horas exatas vêm da API de worklog.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 31. Criadas na semana por reporter
**Finalidade:** Demanda nova por quem abre.
**Exemplo:**
```jql
project = TG AND reporter = bruno.lima AND created >= startOfWeek()
```
**Observações:** Para PL, o Reporter é referência do fluxo.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 32. Backlog envelhecido
**Finalidade:** Criadas e não resolvidas há mais de 30 dias.
**Exemplo:**
```jql
project = TG AND resolution = Unresolved AND created <= "-30d" ORDER BY created ASC
```
**Observações:** `"-30d"` = 30 dias corridos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 33. Alta prioridade abertas
**Finalidade:** Foco em urgências.
**Exemplo:**
```jql
project = TG AND statusCategory != Done AND priority IN (Highest, High) ORDER BY priority DESC, duedate ASC
```
**Observações:** Nomes de prioridade variam por instalação.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 34. Mudança de prioridade recente
**Finalidade:** Escaladas de prioridade.
**Exemplo:**
```jql
project = TG AND priority CHANGED AFTER "-7d"
```
**Observações:** `priority` aceita `CHANGED`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-operators-reference-939938745.html

### 35. Atualizadas por uma pessoa recentemente
**Finalidade:** Atividade de alguém.
**Exemplo:**
```jql
issuekey IN updatedBy(ana.silva, "-7d")
```
**Observações:** `updatedBy` conta qualquer atualização, inclusive criar issue e comentar.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 36. Issues da sprint ativa
**Finalidade:** Foco na sprint corrente.
**Exemplo:**
```jql
project = TG AND sprint IN openSprints() AND statusCategory != Done
```
**Observações:** Requer Jira Software; equipes sem sprint não usam.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-functions-reference-939938746.html

### 37. Filhas de um épico
**Finalidade:** Escopo de um épico.
**Exemplo:**
```jql
"Epic Link" = TG-31 ORDER BY status ASC
```
**Observações:** No Data Center usa `"Epic Link"`; no Cloud seria `parent` **[Cloud]**.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 38. Sub-tarefas abertas de uma tarefa
**Finalidade:** Checar pendências antes de concluir.
**Exemplo:**
```jql
parent = TG-1234 AND statusCategory != Done
```
**Observações:** Requer sub-tarefas habilitadas.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 39. Sincronização incremental (para o app)
**Finalidade:** Trazer só o que mudou desde a última sincronização.
**Exemplo:**
```jql
project = TG AND updated >= "2024/03/01 08:00" ORDER BY updated ASC
```
**Observações:** O horário segue o fuso do usuário/servidor; guarde o instante da última sincronização com margem de sobreposição (ex.: 5 minutos) e deduplique pela chave. Use paginação.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-fields-reference-939938743.html

### 40. Entrada e saída da semana (fluxo)
**Finalidade:** Criadas ou resolvidas na semana.
**Exemplo:**
```jql
project = TG AND (created >= startOfWeek() OR resolved >= startOfWeek())
```
**Observações:** Dois números por semana (entrada x saída) dão a tendência do fluxo.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

---

## Lacunas e cuidados

- Nada foi executado em Jira real. Os padrões 8, 12, 13, 21 e 27 a 29 merecem atenção especial na primeira validação.
- `creator`, `updatedBy()` e o suporte de `WAS` em alguns campos dependem da versão do Data Center; registre a versão real em `matriz-compatibilidade.md`.
- Não há JQL nativo para comparar dois campos (`resolved` x `duedate`), para histórico de `duedate`/`labels`, nem para somar horas por pessoa/dia; essas partes pertencem ao app (changelog e API de worklog).
- Partes das páginas de campos foram truncadas na leitura (tabela de `timespent`, `workratio`); marcado onde aplicável.
- Páginas de support.atlassian.com (Cloud) não foram usadas; toda a base é a documentação Server/Data Center.
