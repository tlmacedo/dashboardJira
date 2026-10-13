# BI externo, desempenho e ciclo de vida do Jira Data Center (histórico de status, tempo em status, worklog)

Data de referência: 2026-10-12. Escopo: somente Data Center (9.12 LTS e 10.x). Observação: todo SQL/JQL abaixo é de autoria nossa (não é da Atlassian), salvo indicação.

## 1. Consulta direta ao banco de dados do Jira DC

### Takeaway
Não encontrei nenhuma página atual da Atlassian que declare suporte a consultas de leitura no banco para relatórios. A documentação oficial desencoraja o acesso direto, e a leitura do histórico é recomendada via API. As tabelas changegroup/changeitem são documentadas pela Atlassian, então o SQL é viável tecnicamente, mas por conta e risco, preferencialmente em réplica.

### Cited Findings
- changegroup: colunas ID, issueid, AUTHOR (null se não logado), CREATED; cada alteração feita por usuário insere um registro. changeitem: ID, groupid, FIELDTYPE, FIELD (ex.: `status`), OLDVALUE/NEWVALUE (ID da entidade), OLDSTRING/NEWSTRING (nome, mantido para exibir o histórico mesmo se a entidade for removida). Um changegroup tem um ou mais changeitem. — [Database change history (developer.atlassian.com)](https://developer.atlassian.com/server/jira/platform/database-change-history/)
- A mesma página usa como exemplo uma mudança de `status` de Open (ID 1) para Closed (ID 6), e informa que changegroup.issueid deve referenciar um ID válido de `jiraissue`. — [Database change history](https://developer.atlassian.com/server/jira/platform/database-change-history/)
- A mesma página afirma que a forma recomendada de ler o histórico é a API `changeHistoryManager.getChangeHistoriesForUser(...)`, e não consultas diretas. — [Database change history](https://developer.atlassian.com/server/jira/platform/database-change-history/)
- KBs antigas (Jira 4.4/5.0) dizem: "Direct database modifications are not supported by Atlassian" (modificações, não leitura); a aplicação assume acesso exclusivo ao esquema e há cache, então mudanças externas podem não aparecer. Exemplo: [How Come JIRA does Not Show Direct SQL Data Modifications](https://confluence.atlassian.com/spaces/JIRA044/pages/267257285/How+Come+JIRA+does+Not+Show+Direct+SQL+Data+Modifications). Não verifiquei uma versão para 9.12/10.x.
- Resposta de comunidade (2018, opinião, não oficial): o esquema não foi desenhado para relatórios; recomenda REST ou apps de relatório. — [Community](https://community.atlassian.com/forums/Jira-questions/How-to-fetch-data-from-Jira/qaa-p/924913)
- Pedido de recurso JRASERVER-67064 (status "Gathering Interest", criado em 03/Abr/2018, atualizado em 12/Ago/2026, sem fix version): relata que ferramentas como Tableau conectam direto ao banco e contornam a segurança de projeto/issue; pede réplica somente leitura. Sem resposta oficial da Atlassian sobre suporte. Relacionado: JRASERVER-68008 (nó de relatório somente leitura). — [JRASERVER-67064](https://jira.atlassian.com/browse/JRASERVER-67064)
- Índices compostos `chggroup_issue_id` e `chgitem_group_field` foram criados em changegroup/changeitem para acelerar joins (relato em bug de desempenho). — [JRASERVER-63002](https://jira.atlassian.com/browse/JRASERVER-63002)

### Inferences
- Autoria nossa (não Atlassian), esboço para tempo em status; validar nomes de colunas na versão alvo e em ambiente de teste. `changeitem.field = 'status'`, `created` vem de `changegroup`:
```sql
-- Autoria nossa (não Atlassian). PostgreSQL; ajustar para o SGBD.
WITH st AS (
  SELECT g.issueid, g.created, ci.oldstring, ci.newstring
  FROM changegroup g
  JOIN changeitem ci ON ci.groupid = g.id
  WHERE ci.field = 'status'
)
SELECT issueid, newstring AS status, created AS entrou_em,
       LEAD(created) OVER (PARTITION BY issueid ORDER BY created) AS saiu_em
FROM st;
```
  Para o status inicial (antes da 1ª transição) usar `jiraissue.created` com `oldstring` da primeira mudança; para issues sem transição, o status atual vem de `jiraissue.issuestatus` (ID) com join em `issuestatus`. A última linha de cada issue tem `saiu_em` nulo (usar now()). Tempo em horário útil/calendário exige lógica adicional.
- Risco prático: schema não é contrato de API (pode mudar entre versões), carga em produção e bypass da segurança de projetos; mitigar com réplica de leitura, usuário somente leitura e ETL incremental por `changegroup.created`. Isso é inferência; a Atlassian não documenta essa prática.

### Gaps
- Declaração oficial atual (9.12/10.x) sobre leitura direta do banco: não confirmado.
- Colunas da tabela `worklog` (timeworked, startdate, author, issueid): a página developer.atlassian.com/server/jira/platform/database-worklog/ retornou 404; não confirmado em fonte oficial.
- Página "Database schema" oficial por versão (jiraissue, issuestatus, project): não verificada nesta pesquisa.

## 2. Extração via REST e conectores BI

### Takeaway
A via suportada é REST (`/rest/api/2/search` com expand=changelog, endpoints de worklog) com paginação. Existem conectores de Marketplace com listagem de Data Center para Power BI/Tableau e um plugin Grafana Enterprise com provedor "Jira Data Center / Jira Server".

### Cited Findings
- Grafana: o Jira data source lista suporte a Jira Data Center; é plugin Enterprise (exige Grafana Cloud ou licença Enterprise) e requer Grafana 11.0+; o provedor para on-premises é "Jira Data Center / Jira Server". — [Jira data source](https://grafana.com/docs/plugins/grafana-jira-datasource/latest/) e [Configure](https://grafana.com/docs/plugins/grafana-jira-datasource/latest/configure/)
- Power BI Connector for Jira (Marketplace): lista Jira Data Center 11.0.0 - 11.3.12 "and more", Jira Server 9.4.0 - 10.7.4; menciona suporte a Power Query; a página nomeia a vendor como Tempo Software (resultado de busca anterior atribuía o app a outra vendor; confirmar na listagem). — [Marketplace](https://marketplace.atlassian.com/apps/1221150/power-bi-connector-for-jira)
- Tableau Connector for Jira lista Data Center 11.0.0 - 11.3.8; Tableau Connector Pro lista versões diferentes em páginas distintas (inconsistente); feed OData/WDC 2.0 descrito como deprecated. — [Tableau Connector for Jira](https://marketplace.atlassian.com/apps/1221376/tableau-connector-for-jira), [Tableau Connector Pro](https://marketplace.atlassian.com/apps/1214641/tableau-connector-pro-for-jira)
- "Microsoft Power BI & Tableau Connector (DOIT-BI)": listagem cita Data Center 8.14.1 - 9.17.3 (faixa antiga) e trial de 30 dias. — [Documentação do app](https://deiser-apps.atlassian.net/wiki/spaces/PROJECTRAKCLOUD/pages/1639504/Microsoft+PowerBI+Tableau+and+Google+Data+Studio+connector+DOIT-BI)
- A busca REST retorna 50 itens por padrão; `maxResults` é limitado por `jira.search.views.default.max` (padrão 1000). — [KB maxResults](https://support.atlassian.com/jira/kb/how-to-use-the-maxresults-api-parameter-for-jira-issue-search-rest-api/) e [KB default.max](https://support.atlassian.com/jira/kb/should-i-change-the-jirasearchviewsdefaultmax-parameter-to-unlimited-to-get-all-results-from-a-search-each-time/)
- O campo `worklog` retornado dentro do search/issue é truncado (relato de 20 entradas); usar `/rest/api/2/issue/<key>/worklog`. — [JRA-34746](https://jira.atlassian.com/browse/JRA-34746) e [thread dev community](https://community.developer.atlassian.com/t/failed-to-get-more-than-the-maxresults-limit-for-worklogs-for-an-issue-through-rest-api/78720)
- Doc Tempo (DC): o campo worklog do search não é filtrado por worklogDate/worklogAuthor da consulta; filtrar no cliente. — [Tempo Time Tracker DC REST](https://help.tempo.io/trg-dc/latest/rest-api-integrations)
- Referência REST DC 10.x: [v10002 intro](https://developer.atlassian.com/server/jira/platform/rest/v10002/intro) (paginação startAt/maxResults).

### Inferences
- Padrão de ETL incremental (nossa sugestão): JQL `updated >= "-1d"` com `expand=changelog`, páginas de ~100–500, depois worklog por issue para as issues alteradas. Se o changelog embutido vier truncado, buscar `/rest/api/2/issue/{key}?expand=changelog` ou o endpoint de changelog paginado (comportamento em DC não confirmado por fonte oficial aqui).
- Power BI com conector web genérico (Web.Contents) contra REST DC é possível tecnicamente; não encontrei doc da Microsoft específica para Jira DC.

### Gaps
- Endpoints `worklog/updated`, `worklog/list`, `worklog/deleted` na REST DC 10.x: a página de referência não foi lida com sucesso; não confirmado.
- Datasource Infinity (JSON) do Grafana para Jira DC: não verificado.
- Conector Excel/Power Query oficial da Atlassian: não encontrado.

## 3. Limites, rate limiting e desempenho

### Takeaway
Rate limiting afeta só chamadas REST externas (HTTP 429), com exceção por usuário configurável. Para bulk, paginar com limites moderados, não elevar `default.max` sem necessidade, preferir janelas incrementais e agendar fora do pico.

### Cited Findings
- Rate limiting: só requisições REST externas (Basic, OAuth, cookie JSESSIONID); token bucket; excedente retorna 429; limite por nó; admin em Administration > System > Rate limiting (permitir ilimitado, bloquear, limitar); exceções por usuário (não por grupo), efeito em até 1 minuto; cabeçalhos `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Interval-Seconds`, `X-RateLimit-FillRate`, `retry-after`; propriedades de allowlist `com.atlassian.ratelimiting.whitelisted-url-patterns` e `...whitelisted-oauth-consumers`. A página não indica valores padrão e remete a "Adjusting your code for rate limiting". Sugere definir limite com base no maior consumidor diário no access log (x2/x3 para integrações críticas). — [Rate limiting (10.7)](https://confluence.atlassian.com/adminjiraserver107/improving-instance-stability-with-rate-limiting-1595115636.html); versão genérica: [adminjiraserver](https://confluence.atlassian.com/adminjiraserver/improving-instance-stability-with-rate-limiting-983794911.html)
- `jira.search.views.default.max` (soft, padrão 1000) e `jira.search.views.max.limit` (hard; excedê-lo gera 403; deve ser >= soft). — [Limiting the number of issues returned](https://confluence.atlassian.com/adminjiraserver100/limiting-the-number-of-issues-returned-from-a-search-view-such-as-an-rss-feed-1442846190.html)
- KB (DC): recomenda não alterar `default.max` sem forte motivo; resposta com 100.000 issues pode "fill up your Jira memory space". — [KB](https://support.atlassian.com/jira/kb/should-i-change-the-jirasearchviewsdefaultmax-parameter-to-unlimited-to-get-all-results-from-a-search-each-time/)
- Reindex completo: nó em reindex reporta "maintenance" ao `/status` e não atende usuários; GC >= 97% sem Full GC; guia aplica-se a DC 8 e 9; melhor velocidade observada em 9.4.latest e 9.12.6; Lucene e OpenSearch mencionados. — [Optimize Full Reindex](https://confluence.atlassian.com/jirakb/how-to-troubleshoot-and-optimize-the-full-reindex-in-jira-1333990428.html)
- Índice Lucene depende de I/O de disco; índice em disco local rápido, evitar NFS. — [Troubleshoot index problems DC](https://confluence.atlassian.com/jirakb/troubleshoot-index-problems-in-jira-data-center-203394752.html)
- Jira Stats e logs (LOCALQ, DBR, INDEX-REPLAY) para diagnóstico de DC. — [Troubleshooting Jira performance using Jira Stats](https://support.atlassian.com/jira/kb/troubleshooting-jira-performance-using-jira-stats/)
- Boards lentos: simplificar JQL e remover campos customizados não usados. — [KB Kanban](https://support.atlassian.com/jira/kb/kanban-boards-are-slow-to-load-in-jira-software/)
- Gadgets (fonte de terceiro, não Atlassian): limitar gadgets por dashboard e aumentar intervalo de refresh em instâncias grandes. — [Appfire Dashboard Hub](https://appfire.atlassian.net/wiki/spaces/RDD/pages/2548858976/Best+practices+for+scaling+Dashboard+Hub+in+large+Jira+Data+Center+instances)

### Inferences
- Para o dashboard externo, a conta de serviço deve ter exceção de rate limit dedicada e usar cabeçalho `retry-after` com backoff. Cargas de ETL agendadas fora do horário comercial evitam competir com reindex e usuários.

### Gaps
- Distinção reindex background vs foreground: o trecho lido não detalha o impacto do background; não confirmado.
- Guia oficial "Jira Data Center performance" (instâncias grandes) e "Troubleshooting slow dashboards/gadgets" específico: não localizados como páginas únicas.
- Desempenho de JQL com cláusulas de histórico (`WAS`, `CHANGED`) e campos customizados: sem fonte oficial coletada; não confirmado.
- Confirmação de que as páginas 9.12 e 10.x do rate limiting são idênticas: só 10.7 foi lida.

## 4. Fim de venda e fim de vida do Jira Data Center

### Takeaway
A Atlassian anunciou (8 de setembro de 2025, segundo eazyBI) cronograma de encerramento do Data Center: fim de vendas a novos clientes em 30/03/2026, fim de vendas/expansões a clientes existentes em 30/03/2028, fim de vida em 28/03/2029.

### Cited Findings
- Datas (efetivas às 23:59 PST): 16/12/2025 sem novas submissões de apps DC no Marketplace; 30/03/2026 fim de vendas para novos clientes; 30/03/2028 clientes existentes não podem comprar produtos/apps nem expansões; 28/03/2029 fim de vida: suporte e correções terminam, assinaturas DC e apps associados expiram (somente leitura). Exceção: manutenção estendida mediante custo, só por exceção e para certos clientes. Bitbucket e Bamboo DC (Hybrid License) e Jira Align DC não estão incluídos no EOL. — [atlassian.com/migration](https://www.atlassian.com/migration); também [atlassian.com/licensing/data-center-end-of-life](https://www.atlassian.com/licensing/data-center-end-of-life)
- Texto no blog Atlassian: "Data Center end of life. All Data Center licenses and associated Marketplace app licenses will expire and become read-only." — [Atlassian blog](https://www.atlassian.com/blog/announcements/atlassian-ascend)
- Data do anúncio, 8/set/2025: citada pela eazyBI, não verificada em página da Atlassian. — [eazyBI DC licensing](https://docs.eazybi.com/eazybi/getting-started/eazybi-for-jira-licensing/eazybi-for-jira-data-center-licensing)
- Vendors: eazyBI diz estar "committed to Data Center – until the end of Internet" (post de 9/10/2025), com ressalva de que não pode estender a data de somente leitura de março de 2029. — [eazyBI blog](https://aod.eazybi.com/blog/our-data-center-commitment-until-the-end-of-internet). ScriptRunner (11/09/2025): "fully committed to providing support and ongoing maintenance for our Data Center products" até o fim de vida. — [ScriptRunner](https://www.scriptrunnerhq.com/inspiration/blog/scriptrunner-support-for-data-center-end-of-life). Tempo: o post apenas repete as datas da Atlassian e menciona migração dos próprios produtos para Cloud; sem compromisso específico para Timesheets DC. — [Tempo blog](https://www.tempo.io/blog/time-to-talk-cloud-what-atlassian-ending-data-center-means-for-you)
- Appfire (JMWE): nenhuma declaração localizada.

### Inferences
- Um dashboard novo para DC tem horizonte de uso até 03/2029; planejar caminho de migração para Cloud (APIs diferentes) no desenho.
- Hoje (10/2026) novos clientes não compram mais DC; clientes existentes ainda podem renovar/expandir até 03/2028.

### Gaps
- Fim de venda por app para Appfire/JMWE: não confirmado. Tempo: não confirmado. Datas de apps do Marketplace divergem entre fontes de terceiros (16/12/2025 vs 26/03/2026); a página da Atlassian indica 16/12/2025.

## 5. eazyBI para Jira Data Center

### Takeaway
Licença do app via Atlassian Marketplace, por faixa de usuários Jira, anual. Importação diária incremental por padrão, com opção de histórico de mudanças; DC permite cron exato.

### Cited Findings
- Licenciamento: vendido pelo Marketplace; faixas por número de usuários Jira (não de eazyBI), cluster e nó único cobrados igual (exemplo: 1.200 usuários exigem a faixa de 2.000); termo de um ano, renovação ao preço cheio; carência de 60 dias se o preço mudar; reembolso em 30 dias; descontos acadêmicos etc. Sem preços citados aqui. Não indica versão mínima do Jira. — [eazyBI DC licensing](https://docs.eazybi.com/eazybi/getting-started/eazybi-for-jira-licensing/eazybi-for-jira-data-center-licensing)
- Agendamento: padrão diário (24 h após a última importação bem-sucedida), desligável, horário fixo com fuso, ou cron; no DC (a partir da 9.0) cron exato, com opções mais frequentes (ex.: a cada 10 min, 6 h); falha tenta de novo após 10 min até 9 vezes e depois envia e-mail. — [Data from Jira](https://docs.eazybi.com/eazybi/data-import/data-from-jira)
- Primeira importação é completa; as agendadas são incrementais; mudar opções (projetos, campos) força reimportação completa. — [Data from Jira](https://docs.eazybi.com/eazybi/data-import/data-from-jira)
- Histórico: opção "Import issue change history" (seção Statuses and history), necessária também para mudanças de campos customizados; muitos campos customizados podem deixar relatórios lentos. — [Data from Jira](https://docs.eazybi.com/eazybi/data-import/data-from-jira)
- Release notes 4.5/4.6: DC suporta nós dedicados do cluster para tarefas eazyBI. — [Release notes](https://eazybi.com/blog/eazybi-4-5-4-6-release-notes)

### Gaps
- Versão mínima do Jira por release do eazyBI: não confirmado (os últimos 7.439 caracteres da página de importação não foram lidos).
- Custo/desempenho de importar históricos grandes: sem números oficiais; não confirmado.
- Preços: não coletados, de propósito.
