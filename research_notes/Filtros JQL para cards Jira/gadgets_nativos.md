# Gadgets nativos de dashboard do Jira Data Center: catálogo e formato de filtro/JQL

Nota geral: a página oficial atual (DC 11.3) foi lida; as páginas por gadget específicas de DC 9.12/10.x não foram acessíveis (404), então opções detalhadas vêm de páginas de versões antigas (Jira 4.x–6.4) e de páginas sem versão identificada. Marcado como "não confirmado" quando aplicável. Nomes de UI em português: não encontrados em fonte oficial.

## Quais gadgets existem, quais são nativos e quais vêm de apps

### Takeaway
A página oficial "Gadgets for Jira applications" (Data Center 11.3) lista 43 gadgets; 4 gadgets de gráficos exigem o app Jira Charting (incluindo Workload Pie Chart); Timesheet e Time Tracking Report NÃO estão nessa lista nativa.

### Cited Findings
- Sem app (Jira Core): Assigned to Me, Bubble Chart, Created vs. Resolved Chart, Favorite Filters, Filter Results, Introduction, Issue Statistics, Issues In Progress, Labels Gadget (lista labels de um projeto), Pie Chart, Quick Links, Two Dimensional Filter Statistics, Voted Issues, Watched Issues — [Gadgets for Jira applications](https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html)
- Sem app (Atlassian Gadgets): Activity Stream, Average Age Chart, Heat Map, Jira Road Map, Projects, Recently Created Chart, Resolution Time, Time Since Chart, Wallboard Spacer Gadget — mesma fonte. Heat Map é nativo (mostra peso relativo dos valores de um campo, ex.: prioridade, em projeto ou filtro).
- Somente Jira Software: Agile Wallboard, Days Remaining in Sprint, Sprint Burndown, Sprint Health, Version Report — mesma fonte.
- Exigem o app Jira Charting (JSW e JSM): Average Number of Times in Status, Average Time in Status, Time to First Response, Workload Pie Chart — mesma fonte. Confirmado também em resultado de busca: Workload Pie Chart em Server/DC exige Jira Charting App.
- Outros: Rich Text (substitui o gadget Text, obsoleto; sem HTML arbitrário), Test Sessions, Jira Issues Calendar (exige app Jira Calendar), Confluence Page Viewer (exige application link para Confluence DC) — mesma fonte.
- Bamboo (Bamboo Charts, Plan Summary Chart, Plans, Clover Coverage) exigem plugin/OAuth do Bamboo; FishEye/Crucible Charts exigem app FishEye configurado — mesma fonte.
- Timesheet gadget não aparece na lista nativa. Existe "Time Sheet" no app de terceiros Tempo "Timesheet Reports and Gadgets" (gadgets Project Pivot, Time Sheet, Timespent, Worked Time Chart) — [Tempo DC docs](https://help.tempo.io/trg-dc/latest/timesheet-reports-and-gadgets) (via busca; página não lida integralmente). O Simple Reports (Marketplace) também oferece "Simple Timesheet Gadget" — [Marketplace](https://marketplace.atlassian.com/apps/1219703/time-tracking-estimation-report-dashboard-gadget-for-jira).
- Versão de introdução de cada gadget no DC: não consta na página oficial; não confirmado.

### Inferences
- Para somar horas (Time Spent) em dashboard, é preciso app de terceiros (Tempo, Decadis sumUp, Simple Reports); nativo não cobre.

### Gaps
- Nomes em português da UI e versão de introdução por gadget: não encontrados.
- Gadget "Text" original: substituído por Rich Text na versão 11.3; em 9.12 não confirmado.

## Pie Chart, Issue Statistics e Two Dimensional Filter Statistics (statistic types, %, soma, totais)

### Takeaway
Pie Chart e Issue Statistics agrupam por um campo ("Statistic Type") sobre projeto ou filtro salvo, contando issues; Two Dimensional tem "Show Totals" (totais de linha e coluna). Nenhuma fonte oficial menciona percentual nem soma de campo numérico nos gadgets nativos.

### Cited Findings
- Pie Chart: "Project or Saved Filter", "Statistic Type" (campo base do gráfico) e "Refresh Interval" (nunca/15 min/30 min/1 h/2 h); a página não menciona percentuais; clicar numa fatia mostra as issues — [Adding the Pie Chart Gadget (JIRA 6.4)](https://ja.confluence.atlassian.com/display/JIRA064/Adding+the+Pie+Chart+Gadget). Lista de statistic types não documentada ali. DC 11.3 descreve apenas "agregado de dados para um filtro" — página oficial acima.
- Issue Statistics: projeto ou filtro salvo; Statistic Type (exemplos: Assignee, Component, Priority, Resolution); "Sort By" Natural/Total; opção "Show Resolved Issue Statistics" (Sim/Não — Não = só issues sem resolução) — resultado de busca citando [Adding the Issue Statistics Gadget (JIRA 6.3)](https://confluence.atlassian.com/spaces/JIRA063/pages/683542479/Adding+the+Issue+Statistics+Gadget) (resumo de busca, página não lida na íntegra).
- Two Dimensional Filter Statistics: Saved Filter, X Axis, Y Axis, Sort By (Natural/Total), Sort Direction (Ascending/Descending), Show Totals (totais de linha e coluna), Number of Results (máx. linhas/colunas por página), Refresh Interval. Campos customizados suportados de fábrica só dos tipos Group picker, Multi select, User picker, Select list, Version picker. Percentuais não mencionados — [página Atlassian](https://confluence.atlassian.com/x/lhrzE) e [outra versão](https://confluence.atlassian.com/x/1QfuDw) (versão do produto da página não identificada; a segunda é de 2010, mas ambas coincidem).
- Documentação não lista quais campos são selecionáveis nos eixos; sugestão aberta pedindo isso, e pedidos relacionados para campos de estimativa, Date, Text e story points — [JRASERVER-61653 / JSW-13785](https://jira.atlassian.com/browse/JSW-13785) (Gathering Interest, 44 votos, atualizado 01/07/2025).
- Soma de numérico/totais com soma: oferecidos por apps (Decadis sumUp adiciona coluna e linha de total para campo numérico) — [Decadis](https://docs.apps.decadis.net/sumdc/two-dimensional-filter-statistics-gadget) (via busca).
- Agrupar por statusCategory: não confirmado em nenhuma fonte.

### Inferences
- Como o pedido de campos de estimativa/story points está aberto como suggestion, é razoável concluir que os eixos/statistic types nativos contam issues e não somam campos numéricos (inferência, não afirmação oficial).
- Percentuais: sem suporte documentado; para percentual, recorrer à receita de dois cards (contagem parcial / total) fora do gadget.

### Gaps
- Lista exata dos statistic types em DC 9.12/10.x e se "Status Category" aparece: não confirmado.
- Se o Pie Chart nativo exibe % na legenda/tooltip: não confirmado.

## Filter Results

### Takeaway
Configuração documentada: filtro salvo, número de resultados por página, campos a exibir (reordenáveis) e intervalo de atualização. Sem total documentado.

### Cited Findings
- Opções (JIRA 6.4): Saved Filter; Number of Results (máx. issues por página); Fields to display (arrastar para reordenar); Refresh Interval — [Adding the Filter Results Gadget](https://confluence.atlassian.com/display/JIRA064/Adding+the+Filter+Results+Gadget). A página não menciona totais nem ordenação.
- Gadget aceita só filtro salvo (não JQL direto); apps como "Show Saved Filter with Columns" permitem JQL direto — [Marketplace](https://marketplace.atlassian.com/apps/5228/show-saved-filter-with-columns-for-jira) (via busca).
- Decadis sumUp "Filter Results" é descrito como o gadget nativo estendido com linha de total — [Decadis](https://docs.apps.decadis.net/sumdc/sumup-gadgets) (via busca), o que indica ausência de linha de total nativa.
- Se o filtro é apagado ou tem permissão alterada, o gadget falha com "The filter configured for this gadget could not be retrieved"; mensagem do link: "The requested filter doesn't exist or is private"; correção: recriar filtro ou pedir ao dono restaurar acesso (aplica-se a Jira Software DC) — [Atlassian KB](https://support.atlassian.com/jira/kb/jira-dashboard-gadget-fails-to-load-with-error-the-filter-configured-for-this-gadget-could-not-be-retrieved-please-verify-it-is-still-valid-on-the-issue-navigator).
- Caso de gadget vazio enquanto o filtro retorna resultados; workaround é recriar o gadget — [Atlassian KB](https://support.atlassian.com/jira/kb/filter-results-are-empty-in-dashboard-gadget-but-not-in-the-filter-page/) (via busca; detalhes não lidos).

### Inferences
- A visibilidade das colunas Time Spent, Σ Time Spent, Original Estimate, Parent, Due Date em 9.12/10.x depende da lista de campos do gadget; não verificada.

### Gaps
- Colunas disponíveis (incluindo Σ Time Spent e Parent), limite máximo de Number of Results e ordenação em DC 9.12/10.x: não confirmado.

## Gadgets de tempo/idade (Created vs Resolved, Resolution Time, Average Age, Recently Created, Time Since, Bubble Chart)

### Takeaway
Todos nativos, sobre projeto ou filtro, contando ou medindo tempo; úteis para cards temporais.

### Cited Findings
Todos da [página oficial DC 11.3](https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html):
- Created vs. Resolved: projeto ou filtro; modo cumulativo ou contagem; resolvida se a data de resolução cai no período.
- Average Age: unidades horas a anos; não resolvida = campo Resolution vazio; idade = hoje menos criação.
- Resolution Time: média de created até resolved; issues sem data de resolução excluídas.
- Time Since: campo de data Created, Updated, Due, Resolved ou custom; conta issues com a data em cada intervalo.
- Bubble Chart: período, tamanho da bolha, refresh automático a cada 15 min, escala log; só as 200 primeiras issues abertas.
- Recently Created Chart: taxa de criação e quantas já resolvidas; sem opções listadas.
- Sprint Health: não mostra progresso de Remaining Estimate ou Time Spent; Sprint Burndown: eixo vertical é a estatística de estimativa do board e só mostra sprints não concluídos.

### Gaps
- Número máximo de períodos e opções de "days previously" de cada gráfico: não lidos.

## Timesheet gadget e Time Tracking Report

### Takeaway
Não encontrei Timesheet gadget nativo no DC; os resultados apontam para apps (Tempo, Simple Reports). Opções de configuração não confirmadas.

### Cited Findings
- Ver seção 1: ausência na lista oficial; apps Tempo e Simple Reports.
- "Time Tracking Report" como relatório de projeto do Jira Software (não gadget): não verificado nesta pesquisa.

### Gaps
- Opções (filtro, intervalo, agrupamento, só issues com worklog, subtarefas) e o que soma: não confirmado; necessária verificação nos docs de "Time tracking report" do DC.

## Limitações de dashboard (layout, refresh, compartilhamento, permissão)

### Takeaway
Pouco confirmado oficialmente para 9.12/10.x. Refresh é por gadget; o gadget depende de permissão do filtro subjacente; não há filtro global no dashboard (não confirmado por fonte oficial).

### Cited Findings
- Intervalo de atualização por gadget: nunca/15 min/30 min/1 h/2 h (várias páginas acima). Bubble Chart tem refresh automático de 15 min.
- Layout (1, 2, 3 colunas, 70/30): guia de terceiros, não oficial — [TemperStack](https://www.temperstack.com/learn/jira/build-custom-dashboards/) (via busca; não lido).
- Favorite Filters pode deixar o Jira mais lento em instâncias com muitos usuários/filtros (nota da versão 6.3) — [Using Dashboard Gadgets](https://confluence.atlassian.com/display/JIRA063/Using+Dashboard+Gadgets).
- Compartilhamento: quem compartilha precisa ser membro do grupo/papel de destino (bug) — [JRASERVER-61158](https://jira.atlassian.com/browse/JRASERVER-61158) (via busca). Não existe permissão separada de edição para filtros/dashboards (pedido fechado como duplicado) — via busca, não confirmado.
- Gadget com filtro sem permissão ao espectador: ver KB na seção Filter Results.

### Inferences
- Para um dashboard compartilhado funcionar, o filtro precisa estar compartilhado com ao menos o mesmo público do dashboard.

### Gaps
- "Sem filtros globais de dashboard" e opções oficiais de layout/auto-refresh em 9.12/10.x: não confirmado em fonte oficial (apps como Quick Filters for Dashboards existem justamente para isso — [CodeFortyNine](https://documentation.codefortynine.com/quick-filters-for-jira-dashboards/quick-filter-results), via busca).

## Receitas de cards e percentuais

### Takeaway
Receitas inferidas, sem fonte oficial de receitas.

### Inferences
- Card de contagem: Two Dimensional Filter Statistics ou Issue Statistics sobre um filtro salvo com o JQL do card e "Show Totals"/total; ou Filter Results com Number of Results pequeno, lendo o total de issues exibido (total exibido: não confirmado).
- Percentual: sem suporte documentado; calcular fora do Jira ou usar apps (sumUp).
- Cada card = um filtro salvo compartilhado com o público do dashboard.

### Gaps
- Nenhuma receita oficial encontrada.
