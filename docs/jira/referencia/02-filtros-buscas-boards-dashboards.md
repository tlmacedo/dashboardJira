# Jira Data Center: buscas, filtros, quadros, dashboards e relatórios

Referência local, escrita a partir de documentação pública (somente leitura na web), sem acesso a nenhuma instância Jira. Alvo: **Jira Software Data Center** (a empresa não usa Cloud). Paráfrase própria; nomes fictícios (`ana.silva`, projeto `TG`).

Versões consultadas: páginas da doc 11.2 (dez/2025) e 11.3 (a doc "atual" já mostra 11.3), e 10.2/10.4 para os relatórios (a doc 11.2 não os expôs). O comportamento dos relatórios é considerado estável entre 10.x e 11.x, **mas isso não foi confirmado página a página**. Itens que existem só no Cloud estão marcados `[Cloud]`.

Convenção: "Ideia para o nosso painel" aponta o card/tela do Painel de Squads (fluxo, prazos, carga, ausências, auditoria) que poderia ter equivalente, usando dados que o app já sincroniza.

Sumário: 1. Busca e navegador de issues; 2. Filtros salvos; 3. Ações em lote e exportação; 4. Quadros; 5. Dashboards e gadgets; 6. Relatórios do Jira Software; 7. Planos (Advanced Roadmaps); 8. Notificações e assinaturas; 9. Lacunas.

---

## 1. Busca básica, rápida, avançada e navegador de issues

### Busca rápida (quick search)
**Finalidade:** Achar uma issue pela chave ou por texto livre a partir da caixa Search do cabeçalho.
**Como fazer / comando:**
```
TG-107          -> Enter abre a issue (maiúsculas/minúsculas tanto faz)
107             -> dentro do projeto TG, abre TG-107
meu aberto bug  -> "smart querying": vira filtro (my, r:, overdue, created:-1w, c:, v:, ff:)
```
Atalhos do smart querying: `my` (atribuídas a mim), `r:me` / `r:ana.silva` / `r:none` (reporter), `overdue`, `created:`/`updated:`/`due:` com `-1w`, `1w`, `-1d,1w` (sem espaços), `c:` componente, `v:`/`ff:` versão afetada/fix (por palavra inteira; `*` como curinga).
**Observações:** Os resultados instantâneos cobrem resumo, descrição e projetos; comentários, fuzzy e curinga só aparecem com Enter. Tipos/projetos com espaço ou hífen podem ser mal interpretados (vira busca de texto): use JQL. O modo pode ser trocado para "Text" nas preferências do usuário. Pesquisas simultâneas pesadas podem degradar o servidor.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/quick-searching-1688903115.html

### Busca básica (formulário)
**Finalidade:** Montar consultas sem escrever JQL, escolhendo campos num formulário.
**Como fazer / comando:**
```
Issues > Search for issues   (New filter limpa; "Basic" volta do modo avançado)
```
**Observações:** A tradução entre básica e avançada é bidirecional só para consultas simples. Não voltam para a básica: `OR` (exceto lista `IN`), `NOT`, `EMPTY`, `!=`, `IS`, `IS NOT`, `>`, `>=`, `<`, `<=`, e campos dependentes de projeto sem condição de `project`. Campo cinza = contexto removido (ignorado); exclamação vermelha = valor inválido para o projeto (ignorado). Admin pode desligar a atualização automática (aí clica-se em Update).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/basic-searching-1688903092.html

### Busca avançada (JQL): estrutura
**Finalidade:** Consultar com a Jira Query Language: campo + operador + valor/função, ligados por palavras-chave.
**Como fazer / comando:**
```
project = TG AND assignee = currentUser() AND resolution = EMPTY ORDER BY duedate ASC
status = Resolved AND (project = TG OR assignee = ana.silva)
text ~ "relatório pré-pouso" AND created > startOfYear()
(assignee != currentUser() OR assignee is EMPTY)
```
**Observações:** `AND` tem precedência sobre `OR`; use parênteses. `!=` e `NOT IN` não retornam campos vazios: some `OR campo IS EMPTY`. `1d` começa à meia-noite do servidor; `24h` conta 24 horas para trás. Palavras e caracteres reservados exigem aspas; caracteres especiais em campos de texto exigem escape com duas barras invertidas. `~` (contains) aceita Lucene/curinga em resumo, descrição, ambiente, comentários e alguns campos custom. O autocompletar mostra só 15 sugestões e pode estar desligado pelo admin.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/advanced-searching-1688903122.html

### JQL: operadores e histórico (WAS / CHANGED)
**Finalidade:** Perguntar o que uma issue foi ou o que mudou nela (base para auditoria e tempo em status).
**Como fazer / comando:**
```
status WAS "In Progress"
status WAS IN ("Resolved","In Progress") BEFORE "2026/02/20"
status CHANGED FROM "In Progress" TO "Done" AFTER startOfWeek()
assignee CHANGED BY ana.silva DURING (startOfMonth(), endOfMonth())
priority CHANGED
```
Predicados: `AFTER`, `BEFORE`, `ON`, `BY`, `DURING (a,b)`; `FROM`/`TO` só com `CHANGED`. Demais operadores: `= != > >= < <= IN, NOT IN, ~, !~, IS, IS NOT`.
**Observações:** O histórico só funciona para **Assignee, Fix Version, Priority, Reporter, Resolution e Status**. Não há `CHANGED` para `duedate` nem para labels: troca de vencimento e de labels só aparece no histórico da issue (changelog), não em JQL. Isso justifica o app guardar o changelog por conta própria (auditoria).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/advanced-searching-operators-reference-1688903142.html

### JQL: campos úteis à gestão
**Finalidade:** Saber quais campos usar para prazo, fluxo e carga.
**Como fazer / comando:**
```
statusCategory = Done          (To Do / In Progress / Done, independe do nome do status)
resolution = Fixed             resolved >= -30d
due < now() AND resolution = EMPTY      (atrasadas)
due >= startOfWeek() AND due <= endOfWeek()
labels = atraso-justificado    labels is EMPTY
sprint in openSprints()        "Epic Link" = TG-10      parent = TG-10
lastViewed > -1d               originalEstimate > 4h    (exige time tracking)
```
**Observações:** `due` é só data (sem hora). `created` assume meia-noite se não houver hora. `statusCategory`, `resolved`, `due`, `labels`, `sprint` não aceitam `WAS/CHANGED`. Busca por `status`/`resolution` aceita nome ou ID (por ID é mais seguro em resolução). Campos de projeto/tipo específico só aparecem com `project` na consulta. A página de campos é enorme; só os primeiros 100 mil caracteres foram lidos (watcher, voter, worklogDate e updated não foram verificados aqui).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/advanced-searching-fields-reference-1688903139.html

### JQL: funções úteis
**Finalidade:** Tornar consultas dinâmicas (pessoa atual, janelas de tempo, sprints, versões).
**Como fazer / comando:**
```
assignee in membersOf("tg-basic") AND created > startOfWeek()
assignee = currentUser()                   (resolvido por destinatário em assinaturas)
due < now()                                 duedate <= endOfWeek("+1")
sprint in openSprints()   closedSprints()   futureSprints()
fixVersion in unreleasedVersions(TG)        fixVersion = earliestUnreleasedVersion(TG)
issue in linkedIssues(TG-10, "blocks")      (tipo de link diferencia maiúscula)
issue in updatedBy(ana.silva, "-7d")       issue in watchedIssues()
```
Também: `startOf/endOf` de Day/Week/Month/Year (com deslocamento, ex.: `"-1"`), `standardIssueTypes()`, `subtaskIssueTypes()`, `projectsLeadByUser()`, `lastLogin()`, `votedIssues()`, `issueHistory()`.
**Observações:** `endOfWeek()` depende do locale do usuário. Funções `currentUser`, `watchedIssues`, `votedIssues` exigem usuário logado. Funções de Service Management (`approver()`, `breached()`) só existem com JSM. `membersOf` funciona com `IN`/`NOT IN`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/advanced-searching-functions-reference-1688903143.html

### Navegador de issues: lista, detalhe, colunas e ordenação
**Finalidade:** Ver, ordenar e agir sobre os resultados de uma busca.
**Como fazer / comando:**
```
Issues > Search for issues > alternar List / Detail; menu Columns; clicar no cabeçalho ordena
Teclas j / k navegam entre issues
```
**Observações:** Há três níveis de configuração de colunas: padrão do sistema (admin), **Meus padrões** e **do filtro** (este sobrepõe o pessoal). Colunas de campos de projeto/tipo específicos só aparecem se o filtro limitar a eles. Em modo avançado, ordenar pela coluna ajusta o `ORDER BY`. Colunas Images e agregados de subtarefa não ordenam. Os resultados são estáveis: uma issue editada que deixa de casar continua na lista até recarregar.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/working-with-search-results-1688903208.html

---

## 2. Filtros salvos

### Criar, executar, editar, clonar e favoritar um filtro
**Finalidade:** Guardar uma busca para reusar, compartilhar, assinar e alimentar quadros e gadgets.
**Como fazer / comando:**
```
Criar:    rodar a busca > link "Save as" acima dos resultados > nome > Submit
Executar: Issues > Search for issues > escolher na lista da esquerda (Meus abertos, Reportados por mim,
          Vistos recentemente, Todas) ou "Find filters"
Editar detalhes: Manage Filters > aba "My" > engrenagem > Edit > Save
Editar critérios: abrir o filtro, mudar critérios ("Edited"), Save (sobrescreve) ou Discard changes
Clonar:   seta ao lado de Save > "Save as"
Favoritar: estrela ao lado do nome
```
**Observações:** O filtro novo entra nos favoritos e herda o compartilhamento da preferência do perfil (padrão: privado). Recently Viewed abre em avançado (tem `ORDER BY`). A ordem de colunas salva vale para o filtro; o export Excel usa a ordem do filtro, não a pessoal (salve uma cópia com sua ordem antes). E-mails em texto puro ignoram a ordem de colunas. A aba "Popular" de Manage Filters lista filtros muito favoritados.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/saving-your-search-as-a-filter-1688903146.html

### Compartilhar um filtro e permissões
**Finalidade:** Dar a outros acesso de leitura ou de edição ao filtro.
**Como fazer / comando:**
```
Manage Filters > My > engrenagem > Edit > "Add Viewers" / "Add Editors" > escolher usuário, grupo,
projeto ou papel de projeto > Add > Save
(ou: abrir o filtro > Details > Edit Permissions)
```
**Observações:** Exige a permissão global **Create Shared Object**; sem ela a seção de compartilhamento nem aparece. Só se compartilha com grupos/papéis dos quais se participa. Editor precisa pertencer a todos os grupos com quem o filtro é compartilhado. Quem só vê não edita: clona ou pede ao admin trocar o dono. Admins Jira gerenciam filtros alheios. **Importante para nós:** quadro, dashboard e gadgets dependem das permissões do filtro; sem acesso ao filtro, o usuário não vê as issues.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/saving-your-search-as-a-filter-1688903146.html

### Filtros que dependem do usuário atual
**Finalidade:** Um mesmo filtro compartilhado mostrar a cada pessoa os seus próprios dados.
**Como fazer / comando:**
```
assignee = currentUser() AND resolution = EMPTY ORDER BY duedate
reporter = currentUser()
assignee in membersOf("tg-basic")
```
**Observações:** `currentUser()` é avaliado para quem executa; em assinaturas por e-mail é avaliado por destinatário (exceto listas de distribuição). Em gadgets de dashboard compartilhado, cada visitante vê o seu resultado, e quem não tem acesso ao filtro vê vazio. Isso espelha o nosso modelo de acesso (usuário vê só o próprio; Squad Leader o squad).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/working-with-search-results-1688903208.html

---

## 3. Ações em lote e exportação

### Exportar resultados (CSV, HTML, XML, Word, RSS, impressão)
**Finalidade:** Levar o resultado de uma busca para fora do Jira.
**Como fazer / comando:**
```
Resultados > Export > CSV (todos os campos | campos atuais) / HTML / XML / Word / Imprimir / Conteúdo completo / RSS
```
**Observações:** CSV "todos os campos" repete colunas por comentário e anexo; o delimitador é escolhido, a menos que o admin desligue a pergunta (aí usa vírgula). Excel: não consta na página 11.2 de resultados, mas a página de filtros menciona export Excel (usa colunas do filtro). XML pode ser embutido na macro "Jira issues" do Confluence; o parâmetro `field` limita campos. `tempMax` na URL controla quantas issues saem; exportar centenas é desencorajado. RSS: `os_authType=basic` pede login quando há dados restritos. Para o nosso app, a API REST de busca seria o caminho programático, mas só depois que o usuário liberar o acesso ao Jira (ver CLAUDE.md).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/working-with-search-results-1688903208.html

### Alteração em lote (Bulk change)
**Finalidade:** Aplicar uma operação a muitas issues de uma só vez.
**Como fazer / comando:**
```
Buscar > Tools > Bulk change > selecionar issues > escolher operação > preencher > revisar > Confirm
(no Backlog: Shift+clique e botão direito)
Operações: Edit, Move, Transition, Delete, Watch/Stop watching, Archive (Archive: só licença DC)
```
**Observações:** Exige a permissão global **Bulk Change** mais a permissão de projeto da operação. Limite de **1.000 issues**. Summary, Description, Resolution, Project e campos de time tracking não são editáveis em lote. Transição: uma por vez e por grupo de mesmo workflow. Sprint: só sprints ativas e futuras. "Send notification" só aparece para quem é admin de todos os projetos envolvidos. Nada é gravado antes de Confirm. **Para nós:** troca em lote de responsável/vencimento/labels é exatamente o caso que a auditoria deve detectar.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/editing-multiple-issues-at-the-same-time-939938937.html

---

## 4. Quadros (Scrum e Kanban)

### Consulta (filtro) do quadro
**Finalidade:** Definir quais issues entram no quadro, nos relatórios e nos planos.
**Como fazer / comando:**
```
Board > Configure > General > nome do filtro salvo (trocar) | Edit Filter Query (JQL) | Edit Filter Shares (nome/compartilhamento)
project = TG AND component = "Basic" ORDER BY Rank ASC
```
**Observações:** Só admin Jira ou admin do quadro configura. Todo relatório do quadro (burndown, CFD, controle, velocidade...) considera **apenas** issues que o filtro retorna: mudar o filtro muda a história. Quem não vê o filtro não vê as issues. Em Kanban existe ainda o **sub-filtro** (padrão `fixVersion in unreleasedVersions() OR fixVersion is EMPTY`, que esconde issues liberadas no quadro mas não nos relatórios) e o campo "Hide completed issues" (por padrão esconde Done sem atualização há 2 semanas, por desempenho). A exigência de `ORDER BY Rank` para ranking não está nesta página (consta em "Enabling ranking").
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/configuring-filters-1688899530.html

### Quick filters com JQL
**Finalidade:** Botões acima do quadro que restringem os cartões visíveis por JQL.
**Como fazer / comando:**
```
Board > Configure > Quick Filters > Name + JQL (+ Description) > Add
Padrões: "Only My Issues" = assignee = currentUser()    "Recently Updated" = updatedDate >= -1d
Exemplos: assignee in membersOf("tg-basic")   type = "Bug"   savedfilter = "Atrasadas TG"
          due < now() AND resolution = EMPTY   labels = atraso-justificado
```
**Observações:** Só admin do quadro/Jira. Arrastar a alça reordena (o primeiro fica mais à esquerda); novos entram no topo. Sem limite documentado. O mesmo conceito também filtra o Control Chart e o CFD. `[Cloud]` a versão Cloud tem página própria.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/configuring-quick-filters-1688899568.html

### Swimlanes (raias) por consulta, responsável, épico ou história
**Finalidade:** Agrupar cartões horizontalmente no Sprint ativo ou no Kanban.
**Como fazer / comando:**
```
Board > Configure > Swimlanes > "Base Swimlanes on": Queries | Stories | Assignees | Epics | No Swimlanes
Queries: Name + JQL > Add. Padrão: "Expedite" (priority = Blocker) e "Everything Else" (não removível)
Exemplos: due <= "24h"    priority = Major    project = TG AND component = "UI"
```
**Observações:** `ORDER BY` no JQL da raia é ignorado (vale o rank). Raias por Assignees permitem ver carga por pessoa no quadro; "Unassigned" fica acima ou abaixo. Raias por Epics: reordena-se arrastando épicos no Backlog. Raias por Stories: cada pai com suas subtarefas. Só admin configura.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/configuring-swimlanes-1688899560.html

### Colunas, mapeamento de status e limites de WIP
**Finalidade:** Mapear status do workflow em colunas e restringir trabalho em andamento.
**Como fazer / comando:**
```
Board > Configure > Columns: arrastar status entre colunas e "Unmapped"; campos Min/Max no topo da coluna;
"Column Constraint": Issue Count | Issue Count excluding sub-tasks | None; opção "Days in column"
```
**Observações:** A coluna mais à esquerda = novo (azul), a mais à direita = sucesso/Done (verde), as do meio = em andamento (amarelo). Esses extremos definem "To Do" e "Done" nos relatórios. Status não mapeado **fica fora dos relatórios e do quadro**. Máximo estourado = coluna vermelha; mínimo não atingido = amarela; apenas sinalizam, não bloqueiam. Contagem inclui issues escondidas por quick filter. "Days in column" (pontos no cartão, até 32 dias, cumulativo) pode pesar em instâncias grandes (~300 mil issues). Kanban tem a coluna **Backlog** (mais à esquerda): épicos vão para painel próprio. Padrões: Scrum To Do/In Progress/Done; Kanban Backlog/Selected for Development/In Progress/Done. No Jira Software o termo "WIP limit" não é usado: chama-se constraint.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/configuring-columns-1688899540.html

---

## 5. Dashboards e gadgets

### Criar, copiar, compartilhar e exibir dashboards (wallboard)
**Finalidade:** Montar páginas com gadgets, pessoais ou compartilhadas.
**Como fazer / comando:**
```
Dashboards > Manage Dashboards (editar, copiar, favoritar, excluir, compartilhar)
More > Create Dashboard | Copy Dashboard | Edit/Share Dashboard | Edit layout | View as Wallboard
```
**Observações:** O padrão é privado, salvo preferência do perfil. Só admin personaliza o dashboard padrão. Gadgets são amarrados a filtros: o dashboard compartilhado precisa de filtros compartilhados, e cada visitante vê conforme as próprias permissões. Wallboard: fundo preto e rotação de gadgets (slideshow) para TV. A permissão **Create Shared Object** para compartilhar vem de material de comunidade/filtros, não da página de dashboards 11.2.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/configuring-dashboards-1688903439.html

### Adicionar e personalizar gadgets
**Finalidade:** Incluir, mover, maximizar e remover gadgets.
**Como fazer / comando:**
```
Dashboards > (seu dashboard) > Add Gadget > escolher no diretório > configurar
Botão "More" no cabeçalho do gadget: expandir/recolher, excluir; botão maximizar (nem todo gadget tem tela cheia)
```
**Observações:** Adicionar ao dashboard padrão exige admin do sistema. Gadgets customizados (URL) exigem admin ("Manage gadgets"). A preferência de tela cheia fica em cookie, não no servidor. Dashboard compartilhado editável: quem pode editar altera para todos.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/adding-and-customizing-gadgets-939939033.html

### Gadget Filter Results
**Finalidade:** Listar as issues de um filtro, com colunas escolhidas, dentro do dashboard.
**Como fazer / comando:** Add Gadget > Filter Results > escolher filtro salvo > número de linhas e colunas.
**Observações:** Os dados vêm do filtro (precisa estar compartilhado). Catálogo oficial: "Gadgets for Jira applications".
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Lista "Atrasadas" na tela Prazos, com `duedate`, `assignee`, `status` das issues vencidas e sem `resolution`.

### Gadget Two Dimensional Filter Statistics
**Finalidade:** Tabela cruzando dois campos (ex.: responsável x status) para um filtro.
**Como fazer / comando:** Add Gadget > Two Dimensional Filter Statistics > filtro > eixo X (ex.: Status) > eixo Y (ex.: Assignee) > ordenação e número de linhas.
**Observações:** Não suporta campos de estimativa (pedido de melhoria JRA-35903, segundo resultado de busca); limitado a campos que o Jira sabe agrupar. Cruzar assignee x statusCategory mostra carga.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Matriz de carga na tela Carga: pessoa (ordem alfabética) x status/categoria, com contagem de issues abertas.

### Gadget Created vs Resolved Chart
**Finalidade:** Comparar issues criadas e resolvidas por período (o backlog cresce ou encolhe).
**Como fazer / comando:** Add Gadget > Created vs Resolved Chart > projeto ou filtro > período e agrupamento (dia/semana/mês) > cumulativo ou contagem simples.
**Observações:** Vermelho = criadas mais que resolvidas; verde = o contrário. "Resolvida" depende de `resolution`, não do status.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html (cores: https://confluence.atlassian.com/jirasoftwareserver102/reporting-1473873950.html)
**Ideia para o nosso painel:** Card de Fluxo "entrada x saída" semanal por squad, com `created` e `resolved` das issues.

### Gadget Pie Chart
**Finalidade:** Distribuição das issues de um filtro por um campo (status, responsável, prioridade...).
**Como fazer / comando:** Add Gadget > Pie Chart > filtro > "Statistic type".
**Observações:** Agregação simples por campo. No nosso painel a cor nunca é o único sinal.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Distribuição por status/categoria de status das issues em aberto do squad na visão geral.

### Gadget Average Age Chart
**Finalidade:** Idade média das issues não resolvidas ao longo do tempo (backlog envelhecendo).
**Como fazer / comando:** Add Gadget > Average Age Chart > projeto/filtro > período e unidade (horas a anos).
**Observações:** "Não resolvida" = campo Resolution vazio.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Card Fluxo "idade do backlog": média de dias entre `created` e hoje das issues sem `resolution`.

### Gadget Resolution Time
**Finalidade:** Tendência do tempo médio para resolver issues.
**Como fazer / comando:** Add Gadget > Resolution Time > projeto/filtro > período e unidade.
**Observações:** Issues sem data de resolução ficam de fora.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Card Fluxo "lead time" (`resolved` menos `created`, em dias úteis por padrão) por mês.

### Gadget Time Since Chart
**Finalidade:** Quantas issues tiveram um campo de data (Created, Updated, Due, Resolved, custom) numa data.
**Como fazer / comando:** Add Gadget > Time Since Chart > filtro > campo de data > período.
**Observações:** Útil com Updated para achar issues paradas.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Alerta "sem atualização há N dias" na tela Fluxo, com `updated` das issues abertas.

### Gadget Heat Map
**Finalidade:** Peso relativo dos valores de um campo (ex.: prioridade) num projeto ou filtro.
**Como fazer / comando:** Add Gadget > Heat Map > projeto/filtro > campo.
**Observações:** Representação por tamanho/cor; nós precisamos de texto/símbolo junto.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Mapa de calor pessoa x semana na tela Carga/Ausências, cruzando issues com vencimento e dias de ausência.

### Gadget Issue Statistics
**Finalidade:** Contagem de issues de um projeto/filtro agrupadas por um campo.
**Como fazer / comando:** Add Gadget > Issue Statistics > filtro > "Statistic type" > ordenar.
**Observações:** Versão enxuta do Two Dimensional (uma dimensão).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Totais por status/responsável como números grandes nos cards de topo.

### Gadget Activity Stream
**Finalidade:** Feed de atividade recente (comentários, transições, edições).
**Como fazer / comando:** Add Gadget > Activity Stream > filtrar por projeto/usuário/ação.
**Observações:** Sem opções detalhadas na doc consultada.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Linha do tempo de Auditoria com troca de responsável, vencimento e labels (changelog), indicando quem mudou.

### Gadgets Sprint Health, Sprint Burndown, Days Remaining in Sprint, Agile Wallboard (Jira Software)
**Finalidade:** Acompanhar a sprint ativa no dashboard.
**Como fazer / comando:** Add Gadget > escolher o gadget > selecionar o quadro/sprint.
**Observações:** Sprint Health mostra barras coloridas (trabalho concluído pela estatística de estimativa, mudanças de escopo, bloqueadores em To Do e In Progress); Sprint Burndown só para sprints não concluídas; Days Remaining = dias úteis restantes. Dependem de quadro Scrum.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Card "dias úteis restantes" por prazo/sprint, descontando feriados e ausências do YAML.

### Gadget Version Report e Road Map (Jira Software)
**Finalidade:** Projeção de data de liberação de uma versão e versões a vencer no período.
**Como fazer / comando:** Add Gadget > Version Report / Jira Road Map > projeto e versão.
**Observações:** Ver o relatório Version Report na seção 6.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Só aplicável se o time usar `fixVersion`; senão, projeção de término por vencimento.

### Gadgets Assigned to Me, Watched Issues, Favorite Filters
**Finalidade:** Atalhos pessoais para issues atribuídas, observadas e filtros favoritos.
**Como fazer / comando:** Add Gadget > (nome).
**Observações:** Dependem de `currentUser()`.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Tela "Minha visão" para o usuário comum (somente as próprias issues).

### Gadget Control Chart
**Finalidade:** Versão gadget do gráfico de controle. A página de gadgets consultada **não o lista** (consta como relatório de quadro; a existência do gadget não foi confirmada nesta fonte).
**Como fazer / comando:** Ver "Control Chart" na seção 6.
**Observações:** Lacuna registrada.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
**Ideia para o nosso painel:** Ver Control Chart.

---

## 6. Relatórios do Jira Software

Tudo vem de **Board > Reports** e considera só issues do filtro do quadro. "To Do" = status na coluna mais à esquerda; "Done" = status na coluna mais à direita; status não mapeado fica de fora. Páginas citadas são da doc 10.2 (as de 11.x não foram localizadas); nada indica mudança de conceito.

### Burndown Chart (Scrum)
**Finalidade:** Mostrar o trabalho restante na sprint e se a meta será atingida.
**Como fazer / comando:** Board > Reports > Burndown Chart > escolher a sprint e a estatística de estimativa (pontos, horas, contagem).
**Observações:** Linha de guia cinza = ritmo esperado; ausência da guia sugere sprint iniciada sem issues. Só Scrum. Estimativa inconsistente e mapeamento errado distorcem. Dias não úteis vêm de "Configuring working days".
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/burndown-chart-1473873143.html
**Ideia para o nosso painel:** Card Prazos "restante x ideal" por squad usando contagem de issues abertas e dias úteis até o vencimento.

### Burnup Chart (Scrum)
**Finalidade:** Comparar trabalho concluído com o escopo total, expondo aumento de escopo.
**Como fazer / comando:** Board > Reports > Burnup Chart > sprint e medida vertical.
**Observações:** Linha vermelha = escopo; verde = concluído; picos no escopo = itens adicionados. Pontos de subtarefa não contam.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/burnup-chart-1473873135.html
**Ideia para o nosso painel:** Card Fluxo "escopo x concluído" que também mostra issues inseridas depois do início (`created` posterior à data de corte).

### Sprint Report (Scrum)
**Finalidade:** Listar o que foi concluído ou adicionado em cada sprint, com o gráfico de restante.
**Como fazer / comando:** Board > Reports > Sprint Report > sprint.
**Observações:** Issues adicionadas após o início levam asterisco. Só conta o concluído durante a sprint; a linha verde exige time tracking. Só Scrum. A página 10.2 não detalha seções "removidas/não concluídas".
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/sprint-report-1473873292.html
**Ideia para o nosso painel:** Resumo por ciclo: concluídas, não concluídas e inseridas depois, por squad.

### Velocity Chart (Scrum)
**Finalidade:** Comparar comprometido x concluído por sprint e prever a capacidade.
**Como fazer / comando:** Board > Reports > Velocity Chart (padrão últimos 3 meses; intervalo vai na URL).
**Observações:** Estimativa: pontos, horas, contagem ou campo numérico. Até 120 sprints e 25.000 issues. Cada usuário vê estimativas só das issues que pode acessar. Precisa de ao menos uma sprint concluída.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/velocity-chart-1473873317.html
**Ideia para o nosso painel:** Card Carga "vazão" (issues concluídas por semana por squad), nunca ranking de pessoas.

### Cumulative Flow Diagram (CFD)
**Finalidade:** Mostrar issues por coluna ao longo do tempo para achar gargalos.
**Como fazer / comando:** Board > Reports > Cumulative Flow Diagram > "Refine report" (filtros) > intervalo.
**Observações:** Faixa que alarga na vertical = gargalo na coluna. Eixo X tempo, Y número de issues. A página 10.2 não diz se há diferença Scrum x Kanban (o relatório existe nos dois, segundo o índice de relatórios).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/cumulative-flow-diagram-1473873222.html
**Ideia para o nosso painel:** Card Fluxo: contagem diária de issues por categoria de status (precisa snapshot diário no SQLite, já que o Jira só dá o estado atual; o histórico vem do changelog).

### Control Chart
**Finalidade:** Cycle time e lead time por issue, com média móvel e desvio padrão.
**Como fazer / comando:** Board > Reports > Control Chart > escolher colunas (define cycle ou lead time), período e quick filters.
**Observações:** Cycle time = tempo ativo; lead time = do registro até concluir. Média móvel usa 20% das issues (mínimo 5, ímpar). Escala linear até 30 dias, depois raiz cúbica. Outliers e resoluções diferentes de Fixed distorcem a média (use quick filter). Não funciona no IE8.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/control-chart-1473873162.html
**Ideia para o nosso painel:** Card Fluxo "tempo de ciclo" com a data de entrada em "In Progress" (changelog de status) até `resolved`, por mês.

### Epic Report e Epic Burndown (Scrum)
**Finalidade:** Progresso de um épico (completas, incompletas, sem estimativa) e previsão de sprints restantes.
**Como fazer / comando:** Board > Reports > Epic Report / Epic Burndown > escolher épico.
**Observações:** Burndown prevê pela média das últimas 3 sprints; o rótulo "% unestimated" fica vermelho acima de 30%. Só épicos de projetos incluídos no filtro. Epic Report só Scrum.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/epic-report-1473873243.html e https://confluence.atlassian.com/jirasoftwareserver102/epic-burndown-1473873231.html
**Ideia para o nosso painel:** Visão por épico (`Epic Link`/`parent`): % concluído e issues sem vencimento.

### Release Burndown e Version Report (Scrum)
**Finalidade:** Acompanhar uma versão e prever a liberação.
**Como fazer / comando:** Board > Reports > Release Burndown / Version Report > escolher versão.
**Observações:** Release Burndown prevê sprints restantes (média das 3 últimas); Version Report usa velocidade média, com datas otimista/pessimista (+/-10%) e só prevê após 10% do trabalho concluído; subtarefas excluídas; versões arquivadas não aparecem.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/release-burndown-1473873252.html e https://confluence.atlassian.com/jirasoftwareserver102/version-report-1473873333.html
**Ideia para o nosso painel:** Só se o time usar `fixVersion`; senão, ignorar.

### Relatórios gerais de análise (Pie, Created vs Resolved, Average Age, Resolution Time, Time Since, Workload...)
**Finalidade:** Análises por projeto ou filtro fora do quadro.
**Como fazer / comando:** Projeto > Reports (ou menu Reports do quadro) > escolher relatório.
**Observações:** User Workload, Version Workload, Workload Pie e Time Tracking exigem time tracking. User Workload mostra issues não resolvidas e trabalho restante por projeto de um usuário. Relatórios do Confluence (Change Log, Status Report) exigem integração.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver102/reporting-1473873950.html
**Ideia para o nosso painel:** Carga por pessoa (alfabética) com horas restantes só se o time registrar estimativa; hoje a contagem de issues abertas é suficiente.

---

## 7. Planos (Advanced Roadmaps)

### Advanced Roadmaps (Plans)
**Finalidade:** Planejamento multi-equipe com cronograma, dependências, capacidade e releases.
**Como fazer / comando:**
```
Plans > Create plan > nome > acesso (Private | Open, padrão Open) > issue sources (projetos, quadros, filtros)
> (opcional) exclusion rules > Create
```
**Observações:** Incluído no Jira Software Data Center a partir da 8.15 (versões anteriores: app grátis). Limites: **5.000 issues por plano, 100 projetos, 50 equipes recomendadas**. Visibilidade depende da permissão do filtro da fonte. Permissão do plano prevalece sobre a global. Há avisos (Warnings), capacidade/velocidade, dependências e releases (páginas listadas, não lidas). Atlassian recomenda migrar ao Cloud para novos recursos de planejamento; suporte DC até 28/03/2029 (aviso do site).
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver106/plans-in-advanced-roadmaps-1573490495.html e https://confluence.atlassian.com/jirasoftwareserver112/get-started-with-advanced-roadmaps-1688899942.html
**Ideia para o nosso painel:** Linha do tempo de prazos por pessoa/squad usando `duedate` e ausências do YAML; é o equivalente de "capacidade".

---

## 8. Notificações e assinaturas

### Assinatura de filtro por e-mail
**Finalidade:** Receber periodicamente os resultados de um filtro salvo.
**Como fazer / comando:**
```
Abrir o filtro > Details (ao lado do nome) > Subscribe > frequência (ou Advanced com expressão cron) > Subscribe
```
**Observações:** Só os **primeiros 200 resultados** são enviados. Destinatários podem ser você ou grupos dos quais participa (exige a permissão global "Manage Group Filter Subscriptions"). A busca roda por destinatário (`currentUser()` personalizado), o que pesa em grupos grandes. Alternativa: feed RSS do resultado.
**Fonte:** https://confluence.atlassian.com/jirasoftwareserver112/working-with-search-results-1688903208.html
**Ideia para o nosso painel:** Resumo semanal por e-mail de atrasos por squad (fora do escopo do app hoje); no Jira, serve como fonte alternativa sem acesso à API.

### Acompanhar issues (watchers), votos e links entre issues
**Finalidade:** Receber e-mail a cada atualização de uma issue; relacionar issues.
**Como fazer / comando:** Issue > People > Watchers > Start watching (ou bulk "Watch"); links: Issue > Link (tipos padrão: relates to, duplicates, blocks, clones).
**Observações:** **Não foi localizada uma página 11.x do Jira Software DC para watchers/notificações; o texto vem de resultados de busca (admin e KB) e não está verificado.** A permissão "Manage Watcher List" e o esquema de notificação do projeto controlam quem recebe. O tipo de link "Clones" não deve ser editado. A JQL `linkedIssues()` e `issueLinkType` buscam por links.
**Fonte:** https://confluence.atlassian.com/adminjiraserver/configuring-issue-linking-938847862.html (links); https://confluence.atlassian.com/jirakb/watchers-on-issue-not-receiving-notifications-after-comments-317949176.html (watchers)
**Ideia para o nosso painel:** Alerta de dependência: issue bloqueada por outra (`blocks`) com vencimento anterior ao da bloqueadora.

---

## 9. Lacunas e cuidados

- Watchers, votos e esquemas de notificação em DC 11.x: sem página oficial confirmada.
- Gadget Control Chart: não consta no catálogo de gadgets consultado.
- Relatórios 10.2/10.4 em vez de 11.x; Excel como formato de export não detalhado na página 11.2 de resultados.
- Página de campos JQL: leitura parcial (100 mil de ~317 mil caracteres).
- Exigência de `ORDER BY Rank` e da permissão "Create Shared Object" para dashboards: vêm de outras páginas, citadas como tal.
- Nada de `support.atlassian.com`/`atlassian.com/software/jira` foi usado (conteúdo Cloud, carrega só a casca).
