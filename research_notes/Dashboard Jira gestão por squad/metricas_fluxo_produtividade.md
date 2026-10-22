# Métricas de fluxo e produtividade de times no Jira (dashboard por squad/pessoa)

Data da pesquisa: 2026-10-22. Legenda: itens em "Cited Findings" foram confirmados na fonte citada; tudo em "Inferences" é raciocínio meu (fórmulas JQL/SQL, mapeamento para campos do Jira) e NÃO está confirmado em fonte. Fontes secundárias/vendor estão sinalizadas. Pesquisa de JQL e histórico de status já existe em /home/user/dashboardJira/reports/ e não foi repetida.

## 1. Definições exatas e fórmulas (lead time, cycle time, fila/ativo, throughput, WIP, aging WIP, flow efficiency)

### Takeaway
O Kanban Guide dá as quatro definições-base (WIP, Throughput, Work Item Age, Cycle Time) e o Jira (control chart, template de Atlassian Analytics) as implementa com escolhas específicas de status que o dashboard precisa replicar de forma explícita. Flow efficiency não tem fórmula única: o denominador varia entre fontes.

### Cited Findings
- WIP = "número de itens iniciados mas não finalizados"; Throughput = contagem exata de itens finalizados por unidade de tempo; Work Item Age = tempo decorrido entre o início do item e a data atual; Cycle Time = tempo decorrido entre início e fim do item (citações traduzidas) — [Kanban Guide](https://kanbanguides.org/english/)
- Service Level Expectation (SLE) = previsão de quanto um item deve levar de iniciado a finalizado, com duas partes (período + probabilidade), ex.: "85% dos itens serão finalizados em 8 dias ou menos"; deve vir de dados históricos de cycle time — [Kanban Guide](https://kanbanguides.org/english/)
- Work Item Age é indicador antecedente (leading) para trabalho travado; cycle time é retroativo (lagging); Throughput não é ponderado por tamanho, diferente de velocity em story points — resumo de fontes secundárias em busca; ver [Kanban Pocket Guide cap. 6](https://prokanban.org/blog/the-kanban-pocket-guide-chapter-6-the-basic-metrics-of-flow) (fonte secundária, não aberta na íntegra)
- Jira Software Cloud, Control Chart: cycle time = tempo trabalhando no item, normalmente do início até a conclusão, e "trabalho reaberto soma ao total"; lead time começa quando o item é registrado (criação), não quando o trabalho começa; os status contados são escolhidos na configuração "Columns" do gráfico — [Atlassian Control Chart (Cloud)](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-control-chart/)
- Control Chart: a média móvel é baseada em itens (não em tempo): janela = item + X antes + X depois, X = 20% dos itens exibidos, número ímpar, mínimo 5; cada ponto é um item posicionado pela data em que saiu pela última vez de um status selecionado; sugere Quick Filters para excluir outliers (por label), resoluções diferentes de "Fixed" (duplicados) e itens em andamento — [Atlassian Control Chart (Cloud)](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-control-chart/); versão Server com exemplo numérico (5 issues → 3,8 dias cycle; 6,4 dias lead): [Atlassian Control Chart (Server)](https://confluence.atlassian.com/jirasoftwareserver0912/control-chart-1346049466.html)
- Template "Flow metrics (Jira work types view)" do Atlassian Analytics: Lead time = criação até conclusão, em dias; se o item entra e sai de Done várias vezes usa-se a ÚLTIMA transição para Done; só entram itens cujo status atual é Done. Cycle time = primeira transição para "In progress" até a conclusão (mesma regra de Done). WIP = itens iniciados e ainda ativos ou esperando (mostra WIP médio por mês). Exige mapear status "In progress", "Done" e "Active" (subconjunto de In progress). Flow efficiency = tempo ativo / cycle time total, por tipo de trabalho — [Atlassian Analytics, template Flow metrics](https://support.atlassian.com/analytics/docs/flow-metrics-jira-issue-types-view-dashboard-template). A página não dá fórmulas explícitas.
- Flow efficiency: valor comumente atribuído a Vacanti de 5–15% para times novos em Lean/que não observam fluxo vem de blog (Businessmap); não confirmado em texto do próprio Vacanti. Definições divergem: valor_agregado/lead time; tempo_de_trabalho/(trabalho+espera) com tempo bloqueado fora do numerador (SwiftKanban); In Progress/(In Progress+Buffer) (KanbanZone) — [Businessmap](https://businessmap.io/blog/how-to-measure-the-flow-efficiency-of-a-process-on-a-kanban-board), [Nimblework](https://nimblework.com/blog/flow-efficiency), [KanbanZone](https://kanbanzone.com/product/kanban-metrics/flow-efficiency/) (todas vendors)
- Lei de Little: WIP = Throughput × Cycle Time; premissas listadas pela Planview a partir de Vacanti: chegada média = saída média, todo trabalho iniciado será concluído, WIP inicial ≈ final no intervalo, idade média do WIP estável, unidades consistentes; violações reduzem a precisão mas não invalidam totalmente — [Planview Viz FAQ](https://success.planview.com/Planview_Viz/FAQs/General/How_does_Viz_use_Little's_Law%3F), [ThinkInsights](https://thinkinsights.net/leadership/littles-law) (secundárias)
- eazyBI importa por padrão "Average resolution days" (lead time, criação→resolução) e "Average closing days" (criação→fechamento); cycle time por "ciclos" configuráveis por status (Progress cycle padrão; medidas Issues in Progress, Issues Progress ended, Average Progress days) e há recurso "Issue cycles" no app Cloud — [eazyBI Samples Cycle Time](https://docs.eazybi.com/eazybi/learn-more/learn-eazybi-through-sample-reports/samples-cycle-time), [Work in Progress and resolution overview](https://docs.eazybi.com/eazybi/learn-more/learn-eazybi-through-sample-reports/samples-cycle-time/work-in-progress-and-resolution-overview)
- LinearB (somente Git): cycle time = Coding + Pickup + Review + Deploy; recomenda medir pelo percentil 75 para evitar outliers — [LinearB help](https://linearb.helpdocs.io/article/0vif1ihmgc-how-is-cycle-time-calculated). Swarmia: PR cycle time = em progresso + em revisão + até merge — [Swarmia](https://help.swarmia.com/features/metrics/pull-request-cycle-time)

### Inferences
- Fórmulas operacionais sugeridas a partir do changelog (não vêm de fonte): lead_time = resolutiondate − created; cycle_time = resolutiondate (ou última entrada em Done) − primeira entrada em status de categoria "In Progress"; tempo_ativo = soma das durações em status mapeados como "ativos" (ex.: Em desenvolvimento, Em teste); tempo_fila = soma das durações em status de espera (ex.: Aguardando revisão, Pronto para QA) dentro do cycle time; flow_efficiency = tempo_ativo / cycle_time (itens concluídos), agregada como soma(ativo)/soma(cycle) e não média das razões individuais.
- WIP(t) = contagem de itens com (início ≤ t) e (fim > t ou sem fim); Aging WIP = now − primeiro início para itens com WIP(t), comparado com P50/P85/P95 do cycle time histórico do mesmo squad/tipo (padrão visto em apps de marketplace; ver Q5).
- Decisões que o dashboard precisa documentar: definir a data de "início" (primeira entrada na categoria In Progress vs. entrada em status específico), tratamento de reabertura (Atlassian soma; Analytics usa última entrada em Done), uso de `resolutiondate` vs. status Done (podem divergir; resolução pode ser limpa na reabertura).
- Little's Law serve como checagem de sanidade do dashboard (WIP médio ≈ throughput × cycle médio) e não como previsão confiável se o sistema não estiver estável.

### Gaps
- Não consegui abrir o texto do livro de Vacanti nem fonte primária para flow efficiency e a faixa 5–15%; só secundárias.
- Documentação eazyBI do cubo Jira Issues (lista completa de medidas) retornou 404; nomes exatos de medidas além dos citados não confirmados.
- Kanban Pocket Guide e o texto da Atlassian "agile metrics" não foram lidos na íntegra (página da Atlassian retornou só navegação).

## 2. Retrabalho/reabertura, devolução entre status, bug escape, estimativa, predictability, velocity, prazo, backlog growth, bloqueio, negligenciadas

### Takeaway
Poucas dessas métricas têm definição normativa em fonte primária; as confirmadas são velocity/commitment (Atlassian) e os conceitos DORA de rework/change fail. As demais (reopen rate, taxa de devolução, acurácia, prazo) devem ser definidas pela casa e documentadas, derivadas do changelog.

### Cited Findings
- Velocity Chart (Jira Cloud): "Commitment" = soma da estimativa dos itens no sprint quando ele inicia (itens adicionados depois e mudanças de estimativa NÃO entram); "Completed" = estimativa concluída no fim do sprint (inclui mudanças de escopo posteriores); completo = status mapeado na coluna mais à direita do board; subtarefas não contam (só estimativa do pai); estatística pode ser story points, original estimate (tempo), contagem ou campo numérico; velocity = média do completado nos sprints recentes; disponível só em espaços company-managed template Scrum — [Atlassian Velocity chart](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-velocity-chart/)
- DORA define: Change fail rate = % de deployments que exigem intervenção imediata (rollback/hotfix); Deployment rework rate = % de deployments não planejados resultantes de incidente em produção; Failed deployment recovery time = tempo para recuperar de deploy com falha — [DORA guide](https://dora.dev/guides/dora-metrics/). Rework rate foi adicionado como quinta métrica (relatório 2024/2025), segundo resumo secundário — [CD Foundation](https://cd.foundation/blog/2025/10/16/dora-5-metrics/)
- Atlassian orienta filtros para excluir resoluções ≠ Fixed (duplicados) no control chart — [Atlassian Control Chart](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-control-chart/)
- Kanban Guide trata tempo bloqueado/fila como diagnóstico separado segundo resumo secundário (Open Guide to Kanban: queueing time distinto de blocked time) — [OneUptime (secundária)](https://oneuptime.com/blog/post/2026-08-12-flow-metrics-cycle-queue-throughput-work-item-age/markdown); sem confirmação em fonte primária

### Inferences
Definições propostas (a validar com o negócio):
- Reopen rate = itens que transitaram de Done→não-Done pelo menos 1x no período / itens que entraram em Done no período (changelog, campo status). Registrar também nº de reaberturas por item.
- Taxa de devolução entre status = nº de transições "para trás" (ex.: Em revisão→Em desenvolvimento; Em teste→Em desenvolvimento) / nº total de transições para frente do mesmo par; exige mapa ordinal de status do workflow por projeto.
- Bug escape rate = bugs criados após release/produção (ex.: tipo Bug com label/campo "Origem=Produção") / (bugs encontrados antes + depois) ou / itens entregues no período; depende de campo próprio — o Jira não fornece nativamente (não confirmado em fonte).
- Acurácia de estimativa = realizado (soma de timespent/worklog) / original estimate por item; reportar mediana e P85 da razão (não média), e a % de itens dentro de ±25% (limiar arbitrário). Para story points não há "realizado" em horas; correlacionar SP com cycle time por faixa.
- Commitment reliability = SP (ou itens) concluídos do compromisso inicial / SP comprometidos no início (segue a lógica do Velocity Chart); predictability = desvio-padrão ou coeficiente de variação do throughput/velocity nas últimas N semanas/sprints.
- Prazo (due date adherence) = itens concluídos com resolutiondate ≤ duedate / itens concluídos com duedate; separar "vencidas abertas" (duedate < hoje e sem resolução).
- Backlog growth = criados − resolvidos por período (equivalente ao Created vs Resolved); tendência acumulada.
- Tempo bloqueado = soma das durações com flag "Impediment" ou em status Blocked (changelog do campo Flagged/status); % do cycle time.
- Negligenciadas = abertas com `updated` < now − N dias, sem assignee, ou duedate vencida (JQL equivalente já coberto nos reports existentes).

### Gaps
- Sem fonte primária para reopen rate, taxa de devolução, bug escape rate e acurácia de estimativa aplicáveis ao Jira; fórmulas acima são minhas.
- Não localizei definição oficial Atlassian/eazyBI de "predictability" ou "commitment reliability".

## 3. Produtividade por quantidade, tempo e responsabilidade; atribuição com várias trocas de responsável

### Takeaway
Nenhuma fonte primária encontrada trata da atribuição individual em issues com múltiplos responsáveis; Jira guarda assignee apenas como valor atual + histórico no changelog, e worklog tem autor próprio. Isso torna a atribuição por pessoa uma decisão de modelagem que deve ser explícita e preferencialmente evitada como ranking.

### Cited Findings
- SPACE (Forsgren et al., ACM Queue 2021): produtividade de desenvolvedor "não pode ser medida por uma única métrica ou dimensão" e não se resume ao nível de atividade individual — [Microsoft Research (resumo)](https://www.microsoft.com/en-us/research/publication/the-space-of-developer-productivity-theres-more-to-it-than-you-think/). As cinco dimensões (Satisfaction, Performance, Activity, Communication, Efficiency/Flow) não puderam ser confirmadas nesta página (só abstract); são de conhecimento prévio meu — ver artigo completo em [ACM Queue](https://queue.acm.org/detail.cfm?id=3454124) (retornou 403 ao acessar).
- Velocity Chart do Jira ignora subtarefas (só pai conta), o que afeta quem executou subtarefas — [Atlassian Velocity chart](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-velocity-chart/)
- Swarmia mantém visão de equipe por padrão e dados individuais privados ao indivíduo (fonte secundária/comparativo) — [Pensero (terceiro)](https://pensero.ai/blog/swarmia-alternatives-pull-request-metrics); LinearB teve problemas de rollout ao comparar indivíduos dentro de times (mesma fonte, parcial)

### Inferences
- Quatro "papéis" possíveis e o que cada um mede: assignee atual (quem está com o item agora; distorce para itens reatribuídos antes do fechamento), assignee no momento de cada transição (reconstruído do changelog; mais justo para tempo ativo), autor da transição (quem moveu — pode ser o gestor/scrum master, não o executor), autor do worklog (única fonte de esforço individual real, mas depende de disciplina de apontamento), reporter (quem pediu; não é produtividade).
- Método recomendado para multi-troca: atribuir tempo por intervalo (tempo em status ativo × assignee vigente naquele intervalo, via changelog de assignee); atribuir contagem/throughput de forma fracionada ou ao assignee no momento da entrada em Done, e divulgar a regra. Para esforço, preferir worklog por autor.
- Throughput por pessoa é frágil: itens de tamanhos diferentes, trabalho colaborativo (pair, revisão) invisível; usar como diagnóstico de carga (WIP por pessoa, itens envelhecidos por pessoa) e não como nota.

### Gaps
- Nenhuma fonte encontrada sobre tratamento de reatribuições múltiplas; recomendações acima são inferência.

## 4. Percentis vs médias, outliers, itens sem dado, normalização por tamanho/tipo

### Takeaway
A literatura de fluxo (Kanban Guide, Vacanti, Atlassian) usa percentis do histórico de cycle time (SLE tipo P85) e trata variância como sinal; normalização por tamanho em story points é desencorajada em favor de contar itens e segmentar por tipo.

### Cited Findings
- SLE do Kanban Guide é expresso em percentil ("85% em ≤ 8 dias") derivado do histórico — [Kanban Guide](https://kanbanguides.org/english/)
- Atlassian: quanto menor a variância do cycle time, maior a confiança em usar média/mediana para prever — [Atlassian Control Chart (Server)](https://confluence.atlassian.com/jirasoftwareserver0912/control-chart-1346049466.html)
- LinearB sugere usar P75 do cycle time para atenuar outliers — [LinearB](https://linearb.helpdocs.io/article/0vif1ihmgc-how-is-cycle-time-calculated)
- Vacanti contesta o mito de que itens precisam ter o mesmo tamanho (resenha do livro) — [Thoughtworks](https://www.thoughtworks.com/insights/blog/book-review-actionable-agile-metrics-predictability)
- Apps de marketplace colorem Aging WIP pelos percentis históricos (verde < P50, âmbar P50–P85, vermelho > P85) e oferecem throughput/lead time com P50/85/95 — [Broken Build/Atlassian Marketplace](https://marketplace.atlassian.com/apps/924938813/wip-aging-reports-run-charts-kanban-scrum-safe-in-jira), [Flow Metrics & Forecasting](https://marketplace.atlassian.com/apps/3298816930/flow-metrics-forecasting-cycle-time-kanban-for-jira) (vendors)
- Atlassian recomenda excluir resolução ≠ Fixed e marcar outliers por label (control chart) — [Atlassian](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-control-chart/)

### Inferences
- Reportar P50 (típico), P85 (SLE/compromisso) e P95 (cauda); média só como apoio. Mostrar n (nº de itens) ao lado de cada percentil e suprimir/avisar quando n < ~20–30 (limiar prático meu, não de fonte).
- Itens sem dado: sem data de início (nunca passaram por In Progress, ex.: criados direto em Done) → fora do cycle time, mas dentro do lead time; sem estimativa → fora da acurácia, reportar % sem estimativa como métrica de higiene; sem resolutiondate mas em Done → sinal de inconsistência de workflow. Nunca imputar zero.
- Normalização: segmentar por issue type (Bug/Story/Task) e, se necessário, por faixa de tamanho (P/M/G) em vez de dividir tempo por story points; comparar cada item ao percentil do seu próprio tipo/squad.

### Gaps
- Não obtive texto de Vacanti sobre percentis e a "falácia da média"; apenas resenha. Limiares de n mínimo e ±25% são heurísticas minhas.

## 5. Riscos e ética: Goodhart, gamificação, ranking; Atlassian/DORA/Kanban Guide/Actionable Agile; LGPD

### Takeaway
DORA diz explicitamente para não transformar métricas em metas nem ranquear times e evitar métrica única; SPACE e as vendors de engineering intelligence convergem em medir times, não pessoas. Para LGPD, só obtive fontes jurídicas secundárias: o tratamento de dado de desempenho exige finalidade, necessidade, transparência e base legal documentada; não achei orientação específica da ANPD.

### Cited Findings
- DORA: metas definidas sobre métricas induzem manipulação (Goodhart); usar várias métricas com tensão saudável ("one metric to rule them all" é armadilha); métricas servem para melhorar ao longo do tempo, não para ranquear times; comparar sistemas muito diferentes engana; recomenda propriedade compartilhada das métricas; o guia não trata de medir indivíduos — [DORA guide](https://dora.dev/guides/dora-metrics/)
- Lei de Goodhart: "quando uma medida vira meta, deixa de ser boa medida"; exemplo de linhas de código por dia; velocity inflada quando a gestão pressiona por mais story points — [Laws of Software Engineering](https://lawsofsoftwareengineering.com/laws/goodharts-law/), [Axify](https://axify.io/blog/goodhart-law), [Third Bit](https://third-bit.com/ds4se/intro/) (secundárias). LeadDev nota que mesmo conjuntos de métricas (ex.: EngThrive da Microsoft, focado em saúde do time) podem ser manipulados — [LeadDev](https://leaddev.com/reporting/is-microsofts-engthrive-framework-immune-to-goodharts-law)
- SPACE: produtividade não se reduz a uma métrica nem à atividade individual — [Microsoft Research](https://www.microsoft.com/en-us/research/publication/the-space-of-developer-productivity-theres-more-to-it-than-you-think/)
- Vendors de engineering analytics (Swarmia, LinearB, Jellyfish) se posicionam como nível de time/executivo; Pensero lista leaderboards individuais como prática a evitar (blog de concorrente, marketing) — [Pensero](https://pensero.ai/blog/swarmia-alternatives-pull-request-metrics). Nenhuma fonte independente testando o efeito de ranking individual foi encontrada.
- LGPD (via artigos jurídicos secundários): legítimo interesse não é automático e exige teste de balanceamento documentado, caso a caso (art. 10; a ANPD pode pedir relatório de impacto, art. 10 §3º); alternativas de base legal incluem execução de contrato; fiscalização deve ter finalidade legítima, necessidade, transparência e proporcionalidade; minimização (art. 6º); direitos do titular de acesso, correção e eliminação (art. 18); recomenda-se RIPD para sistemas potencialmente invasivos — [Migalhas](https://www.migalhas.com.br/depeso/327743/impactos-da-lgpd-no-monitoramento-de-empregados), [Âmbito Jurídico](https://ambitojuridico.com.br/monitoramento-de-empregados-e-lgpd-quais-sao-os-limites/), [Jus.com.br](https://jus.com.br/artigos/114955/lgpd-na-pratica-desafios-e-tendencias-atuais-da-protecao-de-dados-no-brasil/2), [Levy & Salomão (PDF)](https://www.levysalomao.com.br/files/publicacao/anexo/20190830120043_bj---monitoramento-de-empregados-sob-a-lei-geral-de-protecao-de-dados-do-brasil.pdf)

### Inferences
- Métrica de desempenho atribuída a funcionário identificado é dado pessoal; usos que alimentem avaliação/promoção/desligamento aumentam o risco jurídico (art. 20, revisão de decisões automatizadas, é conhecimento prévio meu e não pôde ser verificado: o texto da lei não carregou). Sugestão: mostrar a todos as mesmas definições (transparência), permitir que a pessoa veja seus próprios dados, restringir acesso por papel, definir retenção, registrar finalidade ("melhoria de fluxo do time"), fazer teste de legítimo interesse e consultar jurídico/DPO.
- Salvaguardas de desenho: priorizar visões por squad; visão por pessoa apenas como carga/itens envelhecidos (diagnóstico), sem ranking nem score composto; pares de métricas balanceadas (ex.: throughput + reopen rate + idade do WIP); não usar story points como alvo entre times; mudar/rotacionar métricas e revisar sinais de gaming (itens fatiados artificialmente, status movidos sem trabalho, worklog inflado).
- Em Jira, o gaming mais provável: quebrar cards para inflar throughput, mover cards em lote para Done, fechar e reabrir como novo item (esconde retrabalho), preencher estimativa só no fim.

### Gaps
- Não encontrei guia oficial da ANPD sobre desempenho/monitoramento de empregados; texto da LGPD (planalto.gov.br e gov.br/anpd) não pôde ser carregado, então números de artigos acima vêm de artigos secundários ou de memória e precisam de conferência.
- Não encontrei recomendação específica da Atlassian sobre medir times e não pessoas; Kanban Guide e Vacanti também não foram verificados nesse ponto.
- DORA 2025: números de benchmark vêm de resumos de vendors e conflitam entre si; não usar sem consultar o relatório original.

## 6. Cards e visões padrão em ferramentas maduras

### Takeaway
O conjunto recorrente é: Control chart/scatterplot de cycle time com percentis, Cumulative Flow, Throughput (run chart/histograma), Aging WIP com bandas de percentil, WIP trend, Created vs Resolved, Velocity/commitment e flow efficiency por tipo. Aging WIP e flow efficiency não são gadgets nativos do Jira Cloud (segundo busca), exigem Atlassian Analytics, eazyBI ou apps.

### Cited Findings
- Jira Software nativo: Control Chart, Velocity Chart (Scrum), (Cumulative Flow e Burndown também existem, mas não foram abertas nesta rodada) — [Control Chart](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-control-chart/), [Velocity](https://support.atlassian.com/jira-software-cloud/docs/view-and-understand-the-velocity-chart/)
- Atlassian Analytics: template "Flow metrics (Jira work types view)" com cycle time, WIP, flow efficiency, throughput, lead time e work profile — [Atlassian Analytics](https://support.atlassian.com/analytics/docs/flow-metrics-jira-issue-types-view-dashboard-template)
- eazyBI: relatórios de exemplo Cycle Time (In Progress cycle, Progress cycle days, WIP e resolução, started vs ended) e Lead time (resolução média e tendência) — [eazyBI](https://docs.eazybi.com/eazybi/learn-more/learn-eazybi-through-sample-reports/samples-cycle-time)
- Marketplace Jira: gadgets de Lead Time, Cycle Time, Throughput, Work Distribution (Flow Metrics Charts); Aging WIP com bandas P50/P85; "Actionable Flow Metrics" com flow efficiency, WIP age, WIP age table, throughput; throughput e lead time P50/85/95 — [Flow Metrics Charts](https://divimdevelopment.atlassian.net/wiki/spaces/FMCFJC/pages/2760015874/Flow+Metrics+Charts+for+Jira+Cloud+-+User+Guide), [Actionable Flow Metrics](https://bojaprod.atlassian.net/wiki/x/ZoC1g), [Flow Metrics & Forecasting](https://marketplace.atlassian.com/apps/3298816930/flow-metrics-forecasting-cycle-time-kanban-for-jira). Atenção: um resumo diz que a listagem define flow efficiency como espera/total, o que parece inverso; conferir na fonte.
- LinearB: cycle time em 4 fases + benchmarks (fonte Git, não Jira); Swarmia: PR cycle time e "working agreements" (WIP limit de PRs, merge < X dias) — [LinearB](https://linearb.helpdocs.io/article/0vif1ihmgc-how-is-cycle-time-calculated), [Swarmia](https://help.swarmia.com/features/metrics/pull-request-cycle-time)
- Jellyfish: foco em alocação de investimento e dashboards executivos (marketing do vendor) — [Jellyfish vs LinearB](https://jellyfish.co/?p=32786)

### Inferences
- Layout sugerido para o dashboard por squad: (1) KPIs com P50/P85 de cycle time e throughput semanal; (2) scatterplot de cycle time com linhas de percentil; (3) Aging WIP por status com bandas; (4) WIP atual vs limite; (5) CFD; (6) Created vs Resolved; (7) flow efficiency e tempo bloqueado; (8) reopen/devolução/bug escape como qualidade; (9) commitment reliability se Scrum; (10) lista de negligenciadas (ação, não ranking). Visão por pessoa limitada a WIP e itens envelhecidos.

### Gaps
- Não verifiquei documentação de Advanced Roadmaps/Plans, Nave, Jellyfish nem Actionable Agile (acesso 403 ou conteúdo não localizado); o que se diz sobre Nave vem apenas de buscas indiretas.
- Estado de produtos em 2026-10 pode ter mudado; a data dos resultados de marketplace (ex.: FlowDeck 3.1.0, set/2026) vem de resumo de busca, não confirmada.
