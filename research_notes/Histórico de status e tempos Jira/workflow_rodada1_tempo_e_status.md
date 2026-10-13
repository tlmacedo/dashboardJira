# Resultado do workflow deep-research (rodada 1 de tempo e status)

Fonte: workflow de pesquisa com verificação por votação (21 confirmadas, 4 derrubadas). Registro bruto convertido para nota.

## Resumo

No Jira Data Center, o JQL nativo só consulta histórico para seis campos (Assignee, Fix Version, Priority, Reporter, Resolution e Status), com os operadores CHANGED e WAS (e variantes), e os predicados FROM, TO, BY, AFTER, BEFORE, DURING e ON. Ele só diz quais issues tiveram uma mudança. Não calcula tempo em status, tempo de ciclo nem contagem de passagens. Para esses números, a documentação confirma o changelog completo via REST (GET /rest/api/2/issue/{key}?expand=changelog), que traz quem mudou e quando e permite montar o dashboard fora do Jira. Entre os apps, o Jira Charting (gadgets Average Time in Status e Average Number of Times in Status) não é nativo e exige um campo Time in Status e reindexação completa. Time in Status (Timepiece), Status Time Reports 2.3.x, Cycle Time Gadgets e eazyBI declaram compatibilidade com 9.12 LTS e 10.x. Sobre fim de venda de licenças DC e sobre as alternativas sem app (Automation, listeners, Power BI, Grafana, banco de dados) e o desempenho em instâncias grandes, nenhuma afirmação foi confirmada nesta rodada.

## Achados verificados

### 1. (confiança high)
JQL nativo do DC: WAS, WAS IN, WAS NOT, WAS NOT IN e CHANGED só funcionam com seis campos (Assignee, Fix Version, Priority, Reporter, Resolution, Status). CHANGED aceita os predicados FROM, TO, BY, AFTER, BEFORE, DURING e ON e eles podem ser combinados. Exemplos documentados: status CHANGED FROM "In Progress" TO Open e priority CHANGED BY freddo BEFORE endOfWeek() AFTER startOfWeek(). Isso filtra transições específicas, por autor e por período.

**Evidência:** Votação 3-0 nas duas afirmações. A página de operadores (documentação 11.3) lista os seis campos para cada operador de histórico e os predicados do CHANGED. Não há histórico de versão, então 9.12 e 10.x não foram confirmados explicitamente. Os nomes de status com várias palavras devem vir entre aspas.

**Fontes:** https://confluence.atlassian.com/spaces/JIRASOFTWARESERVER/pages/939938745/Advanced+searching+-+operators+reference

### 2. (confiança medium)
A página geral de Advanced searching só nomeia CHANGED, WAS, WAS IN, WAS NOT e WAS NOT IN e remete à página de referência de operadores. O único exemplo de histórico de status nela é 'status changed to Closed after -1d' (e as variantes -2d e -24h), usado para ilustrar a diferença entre busca por dia e por hora, não como modelo de sintaxe.

**Evidência:** Votação 2-1 nas duas afirmações. Os verificadores confirmaram que a página não documenta a sintaxe de DURING, BY, FROM, TO e ON. As palavras from, to, on e by aparecem só como reservadas. A sintaxe validada de WAS e WAS IN está só na página de operadores, e a afirmação sobre ela foi refutada na votação (1-2).

**Fontes:** https://confluence.atlassian.com/jirasoftwareserver/advanced-searching-939938733.html

### 3. (confiança high)
Changelog via REST: no DC, GET /rest/api/2/issue/{key}?expand=changelog devolve o histórico da issue (quem mudou, quando, de qual valor para qual). O artigo oficial se aplica só ao Data Center e cobre todas as versões 8 e 9 (Jira Software e Service Management). Não há confirmação explícita para 10.x.

**Evidência:** Votação 3-0 nas duas afirmações. O KB, atualizado em 25/09/2025, traz a URL exata. Um verificador lembrou, sem refazer a consulta, que o changelog embutido pode ser truncado em históricos longos e que /rest/api/2/issue/{key}/changelog é paginado. Isso não foi confirmado nesta rodada e deve ser validado.

**Fontes:** https://support.atlassian.com/jira/kb/how-to-analyze-the-history-or-changelog-of-an-issue-in-jira/

### 4. (confiança medium)
Gadgets Jira Charting: Average Time in Status, Average Number of Times in Status, Time to First Response e Workload Pie Chart dependem do app Jira Charting e não são nativos. O Average Time in Status exige um campo personalizado do tipo 'Time in Status' (um só, nomeado Time in Status), o KB manda não adicioná-lo a telas de transição e exige reindexação completa (a reindexação em segundo plano não vale). Há divergência entre as fontes sobre adicionar o campo a todas as telas ou a nenhuma tela de transição.

**Evidência:** A lista dos gadgets teve votação 3-0 (página do DC 11.3, com seletor de versões até 9.12). As afirmações sobre o campo personalizado e a reindexação tiveram 3-0 e 2-1. A verificação do KB 776998092 mencionou 'adicionar à todas as telas', e a do KB 'How to setup' citou 'Do not add the field to any (transition) screens', o que se contradiz. Foi citado também um artigo de suporte dizendo que o plugin nunca foi compatível com o DC, o que conflita com a listagem do Marketplace (app 288, DC 10.0.0 a 10.7.4, versão 5.0.0 com suporte ao Jira 10). A compatibilidade com 9.12 não foi confirmada. A afirmação de que o gadget cobre só issues resolvidas nos últimos 30 dias foi refutada (0-3), e a de que nenhum gadget nativo mede tempo por status também (0-3, o item refutado é a parte sobre os gadgets nativos descritos).

**Fontes:** https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html, https://confluence.atlassian.com/jirakb/how-to-use-average-time-in-status-gadget-776998092.html, https://confluence.atlassian.com/display/JIRAKB/How+to+setup+Average+Time+in+Status+Gadget

### 5. (confiança high)
Time in Status (Timepiece, app 1211756) declara compatibilidade com Jira DC 8.0.0 a 11.3.12 em oito versões DC recentes (6.5.8.884 a 6.5.15.87, a última de 02/10/2026), o que cobre 9.12 LTS e 10.x.

**Evidência:** Votação 3-0. É metadado do Marketplace, ou seja, compatibilidade declarada pelo fornecedor, não teste independente. O limite superior muda a cada versão (uma busca mostrou 11.3.10 numa versão anterior). Reconferir ao decidir.

**Fontes:** https://marketplace.atlassian.com/apps/1211756/time-in-status/version-history

### 6. (confiança medium)
Time in Status Reports for Jira (app 1226187): a versão mais recente (1.0.19, 07/08/2026) declara DC 11.0.0 a 11.3.12 e não cobre 9.12 nem 10.x. As versões 1.0.14 (10/07/2025, DC 7.1.0 a 10.7.2) e 1.0.8 (7.1.0 a 9.13.1) cobrem essas linhas. Quem está em 9.12 ou 10.x depende de versões antigas, e não foi checado se o Marketplace as oferece para novas instalações.

**Evidência:** Votação 2-1 para a afirmação sobre a 1.0.19 e 3-0 sobre a 1.0.14. A página não identifica o fornecedor.

**Fontes:** https://marketplace.atlassian.com/apps/1226187/time-in-status-reports-for-jira/version-history

### 7. (confiança high)
Status Time Reports - Time in Status (app 1221826) mantém duas linhas paralelas: 2.3.x para DC 8.0.0 a 10.7.4 (a 2.3.3 saiu em 07/10/2026) e 3.0.x para DC 11.0.0 a 11.3.12. O histórico lista 51 versões DC. Para 9.12 LTS e 10.x, vale a linha 2.3.x, e a cobertura de 10.x vai só até 10.7.4.

**Evidência:** Votação 3-0 nas duas afirmações. Os lançamentos de outubro de 2026 apenas adicionam suporte ao Jira Cloud Migration Assistant, então a manutenção ativa é uma inferência moderada.

**Fontes:** https://marketplace.atlassian.com/apps/1221826/status-time-reports-time-in-status/version-history

### 8. (confiança medium)
Cycle Time Gadgets - Time in Status Trend Report (Broken Build, app 1235431) declara DC 8.20.0 a 11.3.12 (versão 2.6.0, 14/09/2026). Oferece tempo em status (absoluto, razão e média), tempo até SLA e tempo por assignee. O Marketplace o mostra como Partner Supported e com poucas instalações (cerca de 11).

**Evidência:** Votação 3-0 nas duas afirmações, mas baseada na própria listagem do fornecedor (texto de marketing). Sem detalhe do cálculo das métricas e sem status Data Center Approved. A afirmação de que o app 'Cycle Time Gadget for Jira' (1235338) seria só Cloud foi refutada (1-2), então ele não deve ser descartado sem checagem.

**Fontes:** https://marketplace.atlassian.com/apps/1235431/cycle-time-gadgets-time-in-status-trend-report-for-jira

### 9. (confiança high)
eazyBI importa o histórico de mudanças e calcula tempo em status ('Days in transition status' é registrado quando a issue sai do status; 'Average days in transition status' agrega muitas issues). Também conta quantas vezes issues entram ou saem de um status (por exemplo, reaberturas) com as dimensões 'Transition Status' e 'Transition' e as medidas de issues distintas. A versão 9.0.0 para Jira DC (17/06/2026) removeu o suporte ao Java 8 e a versões do Jira anteriores a 9.12.

**Evidência:** Votação 3-0 nas três afirmações. A documentação é do fornecedor, o que é fonte primária para o comportamento do produto. A página não diz o que acontece com issues ainda no status. Modelo de licença e anúncio de fim de venda DC não foram verificados.

**Fontes:** https://docs.eazybi.com/eazybi/data-import/data-from-jira/import-issue-change-history, https://docs.eazybi.com/eazybi/set-up-and-administer/atlassian-data-center/changelog-eazybi-for-jira

## Ressalvas
Todas as páginas oficiais da Atlassian lidas descrevem o DC 11.3 e não trazem histórico de versão, então nada foi confirmado especificamente para 9.12 ou 10.x além das faixas de compatibilidade do Marketplace, que são declaradas pelos fornecedores e não testadas de forma independente. Os limites superiores mudam a cada versão dos apps (as datas vão até outubro de 2026). Várias verificações foram feitas por sumarizador de páginas e três tiveram votação dividida (2-1). Há uma contradição não resolvida sobre as telas do campo Time in Status. Não foram confirmados nesta rodada: ScriptRunner e outros apps, modelo de licença e preços, anúncio de fim de venda de licenças DC (inclusive o do eazyBI), Automation for Jira como fonte de campos personalizados, listeners e post-functions, integração com Power BI, Grafana ou banco de dados, paginação e limites do changelog via REST, e cuidados de desempenho em instâncias grandes. Esses pontos ficam sem fonte oficial nesta síntese e devem ser tratados como não confirmados.

## Perguntas em aberto
- O eazyBI (e outros apps) encerrou a venda de novas licenças Data Center? Isso precisa ser conferido na página do Marketplace e nos comunicados do fornecedor.
- O changelog embutido em expand=changelog é truncado em issues com histórico longo, e qual é a paginação e o limite de /rest/api/2/issue/{key}/changelog nas versões 9.12 e 10.x?
- A sintaxe de WAS, WAS IN, WAS NOT e DURING se comporta igual em 9.12 e 10.x? E como lidar com a lacuna de que nenhum JQL nativo calcula duração em status?
- Quais são os custos de desempenho (reindexação completa, carga de REST em lote, campos personalizados) em instâncias grandes, e como funcionam o ScriptRunner e a Automation para guardar timestamps de transição?

## Afirmações derrubadas na verificação
- (1-2) WAS e WAS IN encontram issues que têm ou já tiveram o valor. Já WAS NOT e WAS NOT IN encontram as que nunca tiveram o valor. Os predicados BY, BEFORE e DURING podem ser combinados. — https://confluence.atlassian.com/spaces/JIRASOFTWARESERVER/pages/939938745/Advanced+searching+-+operators+reference
- (0-3) O gadget mostra o tempo médio por status apenas de issues resolvidas nos últimos 30 dias (janela móvel fixa). — https://confluence.atlassian.com/jirakb/how-to-use-average-time-in-status-gadget-776998092.html
- (0-3) Entre os gadgets nativos de estatística, Resolution Time e Average Age Chart medem tempo apenas até a resolução ou idade de issues não resolvidas. Nenhum gadget nativo descrito mede o tempo em cada status. — https://confluence.atlassian.com/jirasoftwareserver/gadgets-for-jira-applications-939939037.html
- (1-2) A listagem do Marketplace informa compatibilidade apenas com Jira Cloud; não há menção a Data Center nem versões suportadas, logo o app não tem compatibilidade DC confirmada por esta fonte. — https://marketplace.atlassian.com/apps/1235338/cycle-time-gadget-for-jira
