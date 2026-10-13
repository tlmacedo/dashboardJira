# Gadgets nativos de dashboard do Jira Data Center e colunas do Issue Navigator: opções confirmadas e lacunas

Data de referência: 2026. Resultado central: nas docs 9.12/9.13 (Server/DC) **não existem páginas por gadget** (explica os 404); só há a lista "Gadgets for Jira applications". As opções detalhadas por gadget só existem em páginas de Jira 4.0 a 6.4, rotuladas abaixo. Nenhuma página 8.x/9.x/10.x com opções por gadget foi encontrada.

## Existência de páginas por gadget em 8.x/9.x/10.x (por que deu 404)

### Takeaway
As docs DC 9.12 e 9.13 listam os gadgets, mas não linkam páginas individuais. As URLs sugeridas (ex.: jirasoftwareserver/adding-the-pie-chart-gadget-...) retornaram 404 e nenhuma busca achou equivalente moderno.

### Cited Findings
- Doc Jira Server/DC 9.12 "Adding and customizing gadgets" só aponta para "Gadgets for Jira applications" e "Configuring dashboards", sem páginas por gadget — [9.12](https://confluence.atlassian.com/jirasoftwareserver0912/adding-and-customizing-gadgets-1346050005.html)
- Doc DC 9.13 "Using dashboard gadgets": "a página não linka páginas individuais por gadget"; única referência é "All Atlassian Gadgets" (wiki GADGETS, mínimo "4.0 and later") — [9.13](https://confluence.atlassian.com/adminjiraserver0913/using-dashboard-gadgets-1353588019.html); [All Atlassian Gadgets](https://confluence.atlassian.com/spaces/GADGETS/pages/211061004/All+Atlassian+Gadgets) (só descrição e versão mínima, sem opções)
- Tentativa direta em `jirasoftwareserver/adding-the-pie-chart-gadget-939938786.html`, `.../gadgets-939938856.html`, `.../adding-and-customizing-gadgets-939938819.html` e `jirasoftwareserver0912/configuring-dashboards-1346050007.html`: 404 (IDs de página supostos por mim; não confirmam inexistência do conteúdo).

### Inferences
- As opções de configuração por gadget devem ser tratadas como "documentadas até Jira 6.4; DC atual provavelmente igual, mas não confirmado em doc".

### Gaps
- Nenhuma doc oficial 8.x/9.x/10.x com opções por gadget encontrada. Validar na tela de configuração de um DC real.

## Catálogo de gadgets em DC 9.12 (Heat Map, Labels, Charting)

### Takeaway
Heat Map e Labels Gadget existem em Jira Core/Software/JSM DC 9.12. Gadgets de Charting (Average Time in Status etc.) são listados como exigindo o Jira Charting App.

### Cited Findings
- (Doc 9.12) Labels Gadget: "Shows a list of all labels used in a selected project." — [9.12 Gadgets](https://confluence.atlassian.com/jirasoftwareserver0912/gadgets-for-jira-applications-1346050012.html)
- (9.12) Heat Map: "Shows the relative weighting of a selected field's values in issues of a selected project or filter" (ex.: prioridade) — mesma página
- (9.12) Pie Chart: "aggregated data on issues based on a selected filter"; Issue Statistics: "issue statistics returned from a selected project or saved filter (grouped by a selected field)"; Two Dimensional Filter Statistics: tabela com eixos configuráveis; Filter Results: "results of a selected issue filter"; Created vs. Resolved: criados e resolvidos por período, resolvido se a data de resolução cair no período — mesma página
- (9.12) Average Age Chart: idade média de não resolvidas (sem Resolution = não resolvida), unidades de tempo configuráveis; Resolution Time: tempo médio da criação à resolução, issues sem resolução são ignoradas; Time Since Chart: campo (Created, Updated, Due, Resolved, custom) numa data; Recently Created Chart: taxa de criação e quantas foram resolvidas — mesma página
- (9.12) Gadgets de Charting, "requires the Jira Charting App": Average Number of Times in Status, Average Time in Status, Time to First Response, Workload Pie Chart — mesma página
- (9.12) Outros úteis: Bubble Chart, Favorite Filters, Sprint Burndown/Sprint Health/Version Report (Software) — mesma página
- (9.13) Lista de pré-instalados difere do 9.12 (inclui Administration, Bugzilla ID Search, "Text*", sem Heat Map/Labels/Bubble no texto extraído) — [9.13](https://confluence.atlassian.com/adminjiraserver0913/using-dashboard-gadgets-1353588019.html). Conflito: a extração do 9.13 pode estar incompleta; 9.12 é mais detalhada.
- Wiki GADGETS: Heat Map mínimo Jira 4.1; Labels mínimo 4.2 — [All Atlassian Gadgets](https://confluence.atlassian.com/spaces/GADGETS/pages/211061004/All+Atlassian+Gadgets) (leitura parcial: 100 mil de 129 mil caracteres)
- Jira Charting App (app 288): v4.0.1 (2024-02-12) primeira com suporte DC, Jira DC 9.6.0 a 9.17.5; v5.0.0 (2024-08-22) DC 10.0.0 a 10.7.4; v7.0.0 (2025-12-09) Jira 11 — [Marketplace](https://marketplace.atlassian.com/apps/288/jira-charting-plugin/version-history). Fonte via resumo de busca; faixas de versão 11 divergem entre cópias (11.3.0 vs 11.3.11).
- KB antiga afirma que o plugin "nunca foi compatível com Jira DC" (erro 500 no 9.4); anterior ao 4.0.1 — [KB](https://support.atlassian.com/jira/kb/average-time-in-status-chart-stopped-working-after-jira-upgrade/) (apenas snippet lido, não aberta)

### Gaps
- Pontos de Charting já aprendidos não repetidos (reindex, campo "Time in status").

## Pie Chart: Statistic Type, período, contagem/percentual, statusCategory, custom field

### Takeaway
Doc (4.0 e 6.4) só lista: Project or Saved Filter, Statistic Type, Refresh Interval. Não há opção de período. Lista completa de Statistic Types não documentada.

### Cited Findings
- (Jira 4.0) Campos: Project or Saved Filter; Statistic Type ("select the field on which the pie chart will be based"); Refresh Interval (never/15 min/30 min/1 h/2 h) — [4.0](https://confluence.atlassian.com/x/NCW6D)
- (Jira 6.4) Idêntico — [6.4](https://confluence.atlassian.com/display/JIRA064/Adding+the+Pie+Chart+Gadget)
- Sugestão JRASERVER-37545 (DC/Server, "Gathering Interest", sem fix): Statistic Type oferece, entre outros, Epic Status e Epic/Theme; pede Status Type (categoria de status), campos de data e grupo de usuário, logo **agrupar por statusCategory não é suportado** (item solicitado e não resolvido) — [JRASERVER-37545](https://jira.atlassian.com/browse/JRASERVER-37545). Datas da página: criada 2014; estado atual em 2026 não verificado além do texto lido.
- Resultado de busca (secundário, não aberto): campos de texto livre não selecionáveis; labels, versões e selects funcionam — [JRASERVER-66870](https://jira.atlassian.com/browse/JRASERVER-66870) (snippet)
- Bug JRA-63358: custom field como Statistic Type falha (snippet; fix declarado para Cloud) — [JRA-63358](https://jira.atlassian.com/browse/JRA-63358)

### Inferences
- Para contar por categoria de status é preciso filtros separados (`statusCategory = "In Progress"` etc.), um por fatia/cartão.

### Gaps
- Lista completa de Statistic Types e exibição de contagem vs percentual: não confirmado em doc oficial.

## Issue Statistics e Two Dimensional Filter Statistics

### Takeaway
Two Dimensional tem Show Totals (totais de linha e coluna) e Number of Results; Issue Statistics não teve página oficial lida.

### Cited Findings
- (Jira 5.0 e 4.4 e 4.0) Two Dimensional: Saved Filter; X Axis; Y Axis (campos de issue); Sort By (Natural/Total); Sort Direction; Show Totals (totais de linha e coluna); Number of Results (máx. de linhas ou colunas por página); Refresh Interval — [5.0](https://confluence.atlassian.com/x/lhrzE); [4.4](https://confluence.atlassian.com/x/1QfuDw); [4.0](https://confluence.atlassian.com/x/e4xDDw)
- JSW-13785 (DC): doc não explica quais campos podem ser escolhidos nos eixos; o gadget aplica todos os campos suportados na instância — [JSW-13785](https://jira.atlassian.com/browse/JSW-13785) (snippet)
- Totais de campo numérico (somar horas, por exemplo) exigem app de terceiros (sumUp, Two Dimensional Statics) — [sumUp](https://docs.apps.decadis.net/sumdc/sumup-gadgets) (secundário)
- (9.12) Issue Statistics: agrupado por um campo selecionado — [9.12](https://confluence.atlassian.com/jirasoftwareserver0912/gadgets-for-jira-applications-1346050012.html)

### Gaps
- Issue Statistics: tipos de estatística, opção "Show" e exibir só total: não confirmado (página oficial não aberta).

## Filter Results: colunas, total, máximo

### Takeaway
Opções (6.4): filtro, Number of Results (máx. issues por página), Fields to display (colunas próprias do gadget, reordenáveis), Refresh. Sem menção a total de issues.

### Cited Findings
- (Jira 6.4) Saved Filter; Number of Results ("maximum number of issues ... per page"); Fields to display ("select the issue fields to display as columns. Drag and drop to re-order"); Refresh Interval. A página não menciona contagem total — [6.4](https://confluence.atlassian.com/display/JIRA064/Adding+the+Filter+Results+Gadget)
- (Jira 4.0) "Default Columns" = Issue Type, Key, Summary (snippet) — [4.0](https://confluence.atlassian.com/display/jira040/adding+the+filter+results+gadget)
- Bug JRASERVER-78349: gadget "Filter results" congela em certos cenários — [link](https://jira.atlassian.com/browse/JRASERVER-78349) (só título)

### Inferences
- Colunas são configuradas no gadget ("Fields to display"), não herdadas do filtro (doc 6.4 não cita colunas do filtro).

### Gaps
- Valor máximo de Number of Results; lista exata de colunas (Parent, Σ Time Spent, custom fields...); exibição do total: não confirmado.

## Created vs Resolved, Resolution Time, Average Age

### Takeaway
Created vs Resolved (6.2) tem Period, Days Previously, Cumulative Totals, trend e versões.

### Cited Findings
- (Jira 6.2, ja.confluence) Project or Saved Filter; Period (hora, dia, semana, quarter, ano; no 6.1 a lista inclui também Monthly por snippet); Days Previously; Cumulative Totals? (Yes/No); Display the Trend of Unresolved?; Display Versions? (all/only major "x.x"/none); Refresh — [6.2](https://ja.confluence.atlassian.com/jira062/adding-the-created-vs-resolved-gadget-588581621.html); [6.1](https://confluence.atlassian.com/display/JIRA061/Adding+the+Created+vs+Resolved+Gadget) (snippet)
- (9.12) Resolution Time = criação até resolução; Average Age considera "sem Resolution" como não resolvida — [9.12](https://confluence.atlassian.com/jirasoftwareserver0912/gadgets-for-jira-applications-1346050012.html)

### Gaps
- Opções de Resolution Time e Average Age (período, unidades): páginas não abertas. Divergência Monthly/lista de períodos entre 6.1 e 6.2 não resolvida.

## Fatos de dashboard: layout, refresh, filtros globais, permissões

### Takeaway
Refresh: 15 min, nunca, 30 min, 1 h, 2 h por gadget; sem filtro global nos docs. Usuário precisa de acesso ao dashboard, ao filtro e aos projetos.

### Cited Findings
- Refresh Interval never/15/30/60/120 min (docs 4.0 a 6.4, acima). KB DC: alguns gadgets têm refresh de 15 min não alterável na UI; global via tabela `gadgetuserpreference` (admin, reinício) — [KB](https://support.atlassian.com/jira/kb/how-to-update-auto-refresh-interval-in-gadgets-globally/)
- JRASERVER-79012 (DC): abas ociosas só atualizam ao recuperar foco após 15 min; sem controle de frequência por gadget; JRASERVER-77656 corrigido em DC 9.12.15 e 10.2.0 — [JRASERVER-79012](https://jira.atlassian.com/browse/JRASERVER-79012)
- Gadget maximizar/expandir/reordenar; dashboard padrão exige admin; mudança em dashboard compartilhado é visível a todos — [9.12](https://confluence.atlassian.com/jirasoftwareserver0912/adding-and-customizing-gadgets-1346050005.html)
- Permissões: três camadas (dashboard, filtro de cada gadget, projetos do filtro); sem todas, o gadget retorna sem resultados. Filtro privado só o dono altera; admin pode trocar dono — [KB](https://support.atlassian.com/jira/kb/gadget-on-shared-dashboard-returns-could-not-be-retrieved-error/) (snippet; plataforma da KB não verificada). A KB irmã "Filter for this gadget could not be retrieved" é Cloud only — [KB Cloud](https://support.atlassian.com/jira/kb/filter-for-this-gadget-could-not-be-retrieved-error-in-jira-dashboard/)
- JRASERVER-45227: filtro privado em dashboard compartilhado gera mensagem enganosa; workaround: dar permissão ao filtro — [link](https://jira.atlassian.com/browse/JRASERVER-45227) (snippet)

### Gaps
- Ausência de filtro global: nenhuma página afirma explicitamente; apenas não aparece nas docs lidas (não confirmado). Layouts disponíveis e número máximo recomendado de gadgets: não confirmado (página "Configuring dashboards" 404).

## Issue Navigator: colunas, totais, exportação CSV com Σ

### Takeaway
Pouco confirmado; é o maior buraco desta rodada.

### Cited Findings
- JRACLOUD-80618 (Cloud, não DC): "Σ Time Spent" no navegador não soma filhos de Epic, mas no CSV/Excel soma — [link](https://jira.atlassian.com/browse/JRACLOUD-80618) (snippet)
- Data Pipeline DC exporta `original_estimate` em segundos — [schema 9.16](https://confluence.atlassian.com/adminjiraserver0916/data-pipeline-export-schema-1402673033.html) (snippet)

### Gaps
- Colunas da lista, totais de coluna, CSV com campos Σ em DC, limite de linhas do export: não confirmado.
