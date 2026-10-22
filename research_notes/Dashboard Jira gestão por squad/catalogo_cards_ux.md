# Catálogo de telas e cards para dashboard Jira por Squad/time/pessoa/projeto + boas práticas de UX

Nota de método: ~14 chamadas de busca/fetch. Itens marcados "INFERÊNCIA" são de minha autoria, não de fontes. Não consegui fontes primárias para Nave, Geekbot, Hygieia, Tempo Timesheets (detalhe), LinearB (detalhe) nem NN/g sobre hierarquia (ver Gaps).

## 1. Quais telas/cards existem em ferramentas maduras (e filtros típicos)

### Takeaway
O Jira nativo cobre bem fluxo (CFD, Control Chart), sprint (Burndown/Velocity/Sprint Report) e agregações simples (Pie, Two-Dimensional, Created vs Resolved, Workload), mas é fraco em hierarquia squad/pessoa, comparação de períodos e qualidade de dados; ferramentas externas (eazyBI, ActionableAgile, Tempo, Swarmia/Jellyfish/LinearB) preenchem lacunas com forecast probabilístico, capacidade e investimento por categoria.

### Cited Findings
**Relatórios nativos Jira Software Cloud** — [Atlassian: Generate a report](https://support.atlassian.com/jira-software-cloud/docs/generate-a-report/)
- Scrum: Burndown, Burnup, Sprint Report (trabalho concluído ou devolvido ao backlog; revela sobrecompromisso/scope creep), Velocity, Epic Report, Epic Burndown, Release Burndown, Version Report (projeta data de release).
- Scrum e Kanban: Control Chart (cycle time por espaço/versão/sprint; indica se o passado prevê o futuro) e Cumulative Flow Diagram (status ao longo do tempo; gargalos).
- Relatórios gerais: Average Age, Created vs Resolved, Pie Chart (agrupa por campo, ex. responsável), Recently Created, Resolution Time, Single Level Group By (limite 1.000 itens), Time Since, Time Tracking (estimado vs gasto, acurácia; exige time tracking ativado), User Workload (itens não resolvidos e carga restante por usuário), Version Workload, Workload Pie Chart.
- Em Confluence: Change Log e Status Report (pizza por status, prioridade, componente, tipo). Gantt, Timesheet e SQL são de Marketplace.
- Filtros do pie e Single Level Group By: por campo agrupador; nota: terminologia mudou para "work items".

**Gadgets de dashboard (lista Jira Data Center; Cloud pode diferir)** — [Atlassian: Gadgets](https://confluence.atlassian.com/jiracoreserver0910/gadgets-for-jira-applications-1282247432.html)
- Assigned to Me, Bubble Chart (dias aberto x comentários x participantes), Created vs Resolved, Filter Results, Issue Statistics (agrupado por campo), Issues In Progress, Pie Chart, Two Dimensional Filter Statistics (tabela linha x coluna configurável), Average Age, Heat Map, Jira Road Map, Recently Created, Resolution Time, Time Since, Sprint Burndown, Sprint Health (barra colorida com concluído e mudança de escopo), Days Remaining in Sprint, Version Report, Agile Wallboard.
- Jira Charting App: Average Time in Status, Average Number of Times in Status (útil para reabertura/ping-pong), Time to First Response, Workload Pie Chart.
- Entrada de todos os gadgets: projeto ou filtro salvo (JQL). Daí a necessidade de filtros por squad/componente/versão/etc. serem JQL.

**eazyBI** — [Marketplace](https://marketplace.atlassian.com/apps/1211051/eazybi-for-jira-reports-charts-and-dashboards)
- Templates prontos e biblioteca de centenas de relatórios de demo; dashboards interativos; gadgets para dashboards Jira; cálculos de tendência e sparklines; heatmaps e trendlines; editor MDX; fontes extras (SQL, REST, CSV). Vendor-authored.

**ActionableAgile** — [Marketplace](https://marketplace.atlassian.com/apps/1216661/actionableagile-analytics-kanban-agile-metrics-forecasts); [Scrum.org](https://www.scrum.org/resources/blog/want-be-more-predictable-do-these-two-things-every-day)
- Cycle Time Scatterplot (data de conclusão x cycle time, com percentis), Aging WIP (idade de cada item aberto por etapa do workflow, detecta travados), Monte Carlo "When" e "How Many". Ressalva: não funcionam bem em sistema instável.

**Jira Plans (Advanced Roadmaps)** — [Atlassian](https://www.atlassian.com/software/jira/guides/advanced-roadmaps/overview)
- Timeline, Summary, Dependencies view (filtra por time, sprint, release), Teams view (capacidade por pessoa), planejamento de sprint com aviso de sobrecarga, cenários em sandbox. Disponível em Premium/Enterprise.

**Tempo Capacity Planner (ex-Planner)** — [Marketplace](https://marketplace.atlassian.com/apps/1211881/tempo-planner-resource-planning)
- Relatório capacidade x carga, alocação por disponibilidade/skill, timeline com dependências, feriados e calendários Google/O365, planejado x realizado com Timesheets. Vendor-authored.

**Swarmia** — [Investment Balance](https://help.swarmia.com/configuration/investment-balance); [Cycle time](https://help.swarmia.com/metrics-and-definitions/issue-cycle-time/defining-issue-lifecycle-and-cycle-time)
- Investment Balance: categorias mutuamente exclusivas, herdadas do épico pai; meta de categorização >80%, destaca "uncategorized"; conta contribuições dos membros do time inclusive em issues de outros times. Status do Jira normalizados em To Do/In Progress/Done-Won't Do. Working Agreements com limites numéricos (ex. bugs fechados em 3 dias) e alerta em Slack/Teams; limites de WIP.

**LinearB** — [Help](https://linearb.helpdocs.io/article/yk1r2ejk0v-building-custom-investment-reports-in-linear-b)
- Widgets padrão de dashboard custom: Bugs in Progress, Completed Bugs, Completed Stories, Custom; relatórios custom de investimento exigem PM tool conectada.

**Jellyfish** — [Jellyfish library](https://jellyfish.co/library/developer-productivity/dashboard/); [Tempo comparativo (concorrente)](https://www.tempo.io/blog/swarmia-vs-jellyfish)
- Foco executivo: alocação de esforço por iniciativa, distribuição de investimento, capitalização, cycle time, goal tracking. Afirma excluir métricas por pessoa (LOC/commits).

### Inferences
- INFERÊNCIA: o conjunto mínimo de filtros universal (todos os gadgets aceitam JQL/filtro): Squad, Projeto, Período, Tipo, Prioridade, Pessoa, Componente, Versão (fixVersion), Label, Sprint, Épico, mais Status category.
- INFERÊNCIA: Average Time in Status + Average Number of Times in Status são a base nativa para "reabertos/devolvidos".
- INFERÊNCIA: categoria de investimento (estilo Swarmia) mapeia bem para tipo de issue + label (feature/bug/dívida técnica/KTLO).

### Gaps
- Nave, Geekbot e Hygieia: não pesquisados a fundo; sem fonte citável. Conhecimento geral (não verificado): Nave foca em métricas de fluxo/previsão para Jira; Geekbot em standups assíncronos no Slack; Hygieia (Capital One) é dashboard open source de pipeline DevOps, de pouca relevância para Jira gerencial.
- Lista de gadgets é de Jira Data Center; não confirmei o conjunto exato em Jira Cloud em 2026-10. Tempo Timesheets não detalhado.
- Os dois URLs support.atlassian.com de burndown/gadgets retornaram 404.

## 2. Famílias de cards pedidas (abertos, negligenciados, fechados, negativos, workload, ausências, previsão, qualidade, saúde, tendência)

### Takeaway
Quase todo card pode ser derivado de JQL + changelog (tempo em status, reaberturas); o que o Jira não fornece nativamente é a visão de ausências/capacidade (precisa de Tempo, Plans ou fonte externa) e o histórico "devolvido/redistribuído" (só via changelog/histórico de assignee).

### Cited Findings
- Aging WIP e Average Age tratam "idade" de abertos: [ActionableAgile](https://marketplace.atlassian.com/apps/1216661/actionableagile-analytics-kanban-agile-metrics-forecasts), [Atlassian reports](https://support.atlassian.com/jira-software-cloud/docs/generate-a-report/).
- Time Since mede itens por data de campo (updated/due) = base para "sem atualização" e "vencidos": [Atlassian reports](https://support.atlassian.com/jira-software-cloud/docs/generate-a-report/).
- Average Time in Status / Number of Times in Status = tempo por status e reentrada: [Gadgets](https://confluence.atlassian.com/jiracoreserver0910/gadgets-for-jira-applications-1282247432.html).
- Time Tracking Report = acurácia de estimativa (estimado x gasto): [Atlassian reports](https://support.atlassian.com/jira-software-cloud/docs/generate-a-report/).
- User Workload Report = carga por pessoa (apenas não resolvidas): idem.
- Sprint Report/Sprint Health = escopo adicionado, devolvido ao backlog: idem + Gadgets.
- Previsão: Monte Carlo (When/How Many), Version Report, Release/Epic Burndown: fontes acima.
- Capacidade/ausências: Plans Teams view e Tempo (feriados, calendários): [Atlassian Plans](https://www.atlassian.com/software/jira/guides/advanced-roadmaps/overview), [Tempo](https://marketplace.atlassian.com/apps/1211881/tempo-planner-resource-planning).
- Working agreements com limites (ex. bug em 3 dias) como base de "SLA interno" e semáforos: [Swarmia (via busca)](https://www.swarmia.com/changelog/2020-09-28-issue-insights) — snippet de busca, não verificado integralmente.
- Meta de categorização >80% como indicador de qualidade de dados: [Swarmia](https://help.swarmia.com/configuration/investment-balance).

### Inferences
- INFERÊNCIA: "Cancelado/Won't do" exige mapear resolution (Won't Do, Duplicate, Cannot Reproduce) e excluir de throughput (Swarmia separa Done de Won't Do).
- INFERÊNCIA: "Devolvido" = transição de status para trás (ex. Review→In Progress, QA→Dev) ou mudança de assignee; contar eventos no changelog.
- INFERÊNCIA: "Atrasado" = due date < hoje e statusCategory != Done; "estouro" = worklog/estimativa original > 1,0 (ou >1,25 como amarelo).
- INFERÊNCIA: ausências não existem no Jira; requer tabela própria (cadastro de férias/folgas) ou integração com Tempo/calendário.

### Gaps
- Sem fonte para fórmula padrão de "devolvido"; é convenção local.

## 3. Boas práticas de UX para dashboards gerenciais

### Takeaway
Dashboard = informação crítica "num relance", com poucas métricas por tela, codificação por posição/comprimento (barras, linhas, bullet), cor nunca como único canal, e foco em tendência de time, não ranking de pessoas.

### Cited Findings
- Dashboards focam em uma tarefa: comunicar informação crítica; evitar pizza/donut/treemap/gauge radial para quantidades; evitar 3D; não usar cor para magnitude; combinar cor com forma/posição; operacional (acionável, escaneável) x analítico — [NN/g](https://www.nngroup.com/articles/dashboards-preattentive/).
- Bullet graph (Few) substitui gauges: medida única + alvo + faixas qualitativas; sparklines mostram tendência mas ruins para comparação e distorcem se eixo Y não começa em zero — [Wikipedia: Bullet graph](https://en.wikipedia.org/wiki/Bullet_graph), [Dundas](https://dundas.com/support/blog/the-darlings-of-dashboards), [busca sobre Few](https://www.theinformationlab.co.uk/community/blog/information-dashboard-design-stephen) (fontes secundárias).
- Few: dashboard em tela única, usado diária/semanalmente — [Information Lab](https://www.theinformationlab.co.uk/community/blog/information-dashboard-design-stephen).
- WCAG 1.4.1: cor não pode ser único meio de transmitir informação (texto/ícone/padrão junto) — [W3C Use of Color](https://www.w3.org/WAI/WCAG20/Understanding/use-of-color); WCAG 1.4.11: 3:1 de contraste para gráficos/componentes de UI — [W3C Non-text Contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast).
- Blogs genéricos: 5-7 métricas chave, mais importante no topo, tratar períodos sem dados/divisão por zero, paleta de 3-4 cores — [FusionCharts](https://www.fusioncharts.com/blog/dashboard-design-best-practices/) e similares (qualidade baixa/média).
- Métricas individuais e leaderboards geram competição e gaming; DORA é por time; mostrar tendências semanas/meses — [Jellyfish](https://jellyfish.co/library/developer-productivity/dashboard/), [DevOps.com](https://devops.com/should-you-measure-developer-productivity), [Kinetic](https://kineticdev.substack.com/p/dora-really-needs-some-space) (opinião de praticantes).

### Inferences (recomendações de design, INFERÊNCIA)
- Hierarquia 3 níveis: Visão geral (KPIs + semáforo + sparkline) → Tela temática com tabela → Issue (link para `https://<site>.atlassian.net/browse/KEY`, nova aba).
- Semáforo acessível: verde/âmbar/vermelho + ícone (✓ ! ✕) + texto; paleta segura para daltonismo (azul/laranja); contraste ≥3:1.
- Cada KPI: valor, delta vs período anterior (seta + %), sparkline 8-12 pontos com eixo explícito, e meta/faixa (bullet).
- Tabelas ordenáveis, com colunas fixas, paginação, "ver no Jira" (JQL equivalente copiável) e exportação CSV com os filtros ativos no nome/cabeçalho.
- Filtros persistentes na URL (query string: squad, period, project, type, prio, person, comp, version, label, sprint, epic) + "salvar visão"; chips de filtros ativos visíveis.
- Estados vazios explicativos ("Nenhum item vencido nos últimos 30 dias — ótimo" vs "Sem dados: filtro/mapeamento do squad vazio"), diferenciar zero de dado ausente.
- Evitar ranking humilhante: mostrar distribuição/carga sem ordenar por "pior"; ordenação padrão alfabética ou por carga relativa à capacidade; sem "top ofensores" nominais no nível gerente-geral; métricas de pessoa só como contexto de conversa 1:1, com visão da pessoa mostrando apenas os próprios dados e anotações de contexto.
- Modo "gerente" (squad, agregado, risco) vs "pessoa" (minha fila, meus vencidos, meus bloqueios, meu histórico); permissão por papel.
- Comparar sempre com período anterior de mesma duração e mostrar n pequeno (amostra < 10: aviso de baixa confiança).

### Gaps
- NN/g não retornou artigo sobre hierarquia/cards (somente o de preattentive); Few lido só por fontes secundárias; nada sobre persistência de filtros via URL ou export CSV em fontes primárias (práticas gerais de UX, INFERÊNCIA).

## 4. Hierarquia organizacional e mapeamento Squad ≠ Projeto Jira

### Takeaway
Modele Squad como entidade própria (tabela squad + membros + regras de escopo), não como atributo do projeto; resolva escopo por um conjunto de regras combináveis (projeto, componente, campo Team, grupo/lista de membros).

### Cited Findings
- Swarmia conta contribuições de membros do time inclusive em issues de outros times (equipe definida por pessoas) e herda categoria do épico pai — [Swarmia](https://help.swarmia.com/configuration/investment-balance).
- Jira Plans tem Teams view que atribui indivíduos a times com capacidade — [Atlassian Plans](https://www.atlassian.com/software/jira/guides/advanced-roadmaps/overview).
- Gadgets/relatórios do Jira recebem projeto ou filtro; Version Workload e Workload por espaço/filtro — [Atlassian](https://support.atlassian.com/jira-software-cloud/docs/generate-a-report/).

### Inferences (INFERÊNCIA)
- Org → Squad → Pessoa, com relação N:N Pessoa–Squad (com % alocação e vigência de/até) e N:N Squad–Projeto.
- Estratégias de mapeamento, em ordem de robustez: (1) campo Team (Plans/Atlassian Teams) no issue; (2) componente; (3) lista explícita de membros (assignee in (...)) — melhor para contar trabalho por quem faz; (4) grupo Jira; (5) projeto inteiro. Permitir regra composta com AND/OR e exclusões, e fallback "sem squad" visível (qualidade de dados).
- Pessoa em múltiplos squads: dividir capacidade por alocação %; evitar contar a mesma issue duas vezes em totais da organização (dedupe no nível Org).
- Guardar histórico de pertencimento (data) para períodos passados fecharem consistentes após mudança de squad.

### Gaps
- Sem fonte comparando as estratégias; recomendação própria.

## 5. O que mais é importante (não pedido)

### Takeaway
Os extras de maior valor são qualidade de dados, bloqueios/dependências, bugs por severidade/SLA, mix de investimento (dívida técnica), carga de reuniões e auditoria de redistribuição.

### Cited Findings
- Investment mix (feature/bug/dívida) e uncategorized como qualidade de dado — [Swarmia](https://help.swarmia.com/configuration/investment-balance).
- Working agreements/limites numéricos (SLA interno) — [Swarmia changelog](https://www.swarmia.com/changelog/2020-09-28-issue-insights).
- Dependências filtráveis por time/sprint/release — [Atlassian Plans](https://www.atlassian.com/software/jira/guides/advanced-roadmaps/overview).
- Planejado x realizado e feriados/calendários — [Tempo](https://marketplace.atlassian.com/apps/1211881/tempo-planner-resource-planning).
- Time to First Response (SLA de resposta) — [Gadgets](https://confluence.atlassian.com/jiracoreserver0910/gadgets-for-jira-applications-1282247432.html).

### Inferences (INFERÊNCIA)
- Carga de reuniões: só via integração calendário (Google/O365); tratar como redutor de capacidade, não como métrica de pessoa exposta.
- Auditoria de redistribuição: log de mudanças de assignee/squad/sprint com quem, quando, de→para (alimenta card "reatribuições" e protege contra manipulação).
- Notas de contexto: anotação por semana/squad/pessoa (férias, incidente, onboarding) exibida como marcador nos gráficos; evita interpretação injusta de quedas.
- Bugs por severidade e idade vs SLA; dívida técnica % do throughput; taxa de bug escapado (bugs criados após release / itens entregues).

### Gaps
- Sem fonte primária para carga de reuniões em ferramentas de Jira.

## 6. Proposta final (INFERÊNCIA/síntese): 18 telas e 50 cards

Filtros globais (todas as telas, na URL): Org/Squad, Pessoa, Projeto, Período (7/30/90d, sprint, custom) + comparação com período anterior, Tipo, Prioridade, Componente, Versão, Label, Sprint, Épico. Convenção: [P0] essencial, [P1] importante, [P2] depois.

### Telas
1. Visão geral da Org (grade de squads com semáforo) [P0]
2. Resumo do Squad (home) [P0]
3. Abertos (carteira atual) [P0]
4. Negligenciados / Higiene [P0]
5. Fechados no período [P0]
6. Negativos (qualidade do fluxo) [P0]
7. Workload por pessoa [P0]
8. Capacidade e ausências futuras [P1]
9. Previsões e riscos [P1]
10. Fluxo (CFD, cycle time, aging) [P1]
11. Sprint / Iteração [P1]
12. Épicos e entregas (versão/release) [P1]
13. Qualidade de dados [P0]
14. Bugs e SLA [P1]
15. Bloqueios e dependências [P1]
16. Investimento / mix de trabalho [P2]
17. Visão da Pessoa ("Meu painel") [P0]
18. Auditoria e notas de contexto [P2]

### Cards (nome | objetivo | fórmula resumida | filtros específicos além dos globais)
**Visão geral / Resumo do Squad**
1. Semáforo do squad | saúde num relance | agrega regras (vencidos%, bloqueados, WIP vs limite, dados ruins) → verde/âmbar/vermelho + ícone | squad
2. Throughput semanal + sparkline | entrega recente | count(resolved, não Won't Do)/semana, delta vs anterior | tipo
3. Cycle time P50/P85 | previsibilidade | percentil de (resolved − primeiro "In Progress") | tipo, prioridade
4. WIP atual vs limite | foco | count(statusCategory=In Progress) / limite | pessoa
5. Criados vs resolvidos | backlog crescendo? | Σcriados − Σresolvidos por semana (Jira: Created vs Resolved) | tipo, prioridade
6. Tendência semanal/mensal de saúde | evolução | série dos KPIs 1-5, 12 períodos | —
**Abertos**
7. Abertos por status | distribuição | count por status/statusCategory | todos
8. Abertos por idade (faixas) | envelhecimento | bucket(hoje−created): 0-7, 8-30, 31-90, >90 | tipo, prioridade
9. Abertos por prioridade | exposição | count por prioridade x status | —
10. Abertos por vencimento | prazo | vencidos / ≤7d / >7d / sem data | pessoa
11. Aging WIP | itens parados no fluxo | idade no status atual vs P85 de cycle time | status
**Negligenciados**
12. Sem atualização | esquecidos | statusCategory≠Done AND updated < −N dias (N=7/14/30) | prioridade, pessoa
13. Sem responsável | sem dono | assignee is EMPTY AND ≠Done | tipo
14. Vencidos | atraso | due < hoje AND ≠Done, com dias de atraso | pessoa
15. Bloqueados | impedimentos | flag/label/link "is blocked by" abertos, dias bloqueado | motivo
**Fechados**
16. Fechados no período | volume | count(resolved no período), delta vs anterior | tipo, pessoa
17. Tempo até fechar | rapidez | média/P50/P85 de resolution time (lead e cycle) | tipo, prioridade
18. Fechados por pessoa (distribuição, sem ranking) | quem entrega, contexto | count por assignee ao lado de capacidade | pessoa
19. Entregue no prazo | cumprimento | resolved ≤ due / total com due | —
**Negativos** (cada um com drill: tempo por status e responsável)
20. Reabertos | retrabalho | transições Done→não-Done; taxa = reabertos/fechados | tipo, pessoa
21. Devolvidos | retrabalho de fluxo | transições para trás (Review/QA→Dev) e nº de entradas por status (Avg Number of Times in Status) | status
22. Cancelados / Won't do | desperdício | resolution ∈ {Won't Do, Duplicate, Cannot Reproduce} / total; tempo gasto antes de cancelar | tipo
23. Atrasados após entrega (lead time acima de SLA) | prazo | resolved > due, dias de atraso | pessoa
24. Estouro de estimativa | acurácia | timespent / originalestimate; faixas >1,25, >1,5 | pessoa, épico
25. Tempo por status (Avg Time in Status) | gargalo | média de horas por status, por responsável | status, tipo
**Workload / Capacidade**
26. Carga por pessoa | balanceamento | Σ remaining estimate (ou nº de itens) abertos por assignee vs capacidade | squad, sprint
27. Carga vs capacidade (futuro 4-8 semanas) | sobrecarga | carga planejada / (capacidade − ausências − reuniões) | pessoa
28. Calendário de ausências | disponibilidade | férias/folgas/feriados por pessoa/semana | pessoa
29. Itens sem dono por faixa de risco e concentração | single point of failure | itens críticos cujo único responsável é X; % da carga em 1 pessoa | componente
**Previsão e risco**
30. Forecast Monte Carlo (When/How many) | previsão | simulação de throughput histórico (ex. 1.000 rodadas), P50/P85/P95 | épico, versão
31. Release/versão em risco | data | restante / velocidade vs data alvo (Version Report) | versão
32. Burndown/burnup da sprint | meta da sprint | restante por dia vs ideal; escopo adicionado | sprint
33. Velocity / commit vs entregue | previsibilidade | últimos 6 sprints, concluído/comprometido | sprint
34. Épicos em risco | prazo | % concluído vs % tempo decorrido, sem estimativa | épico
**Fluxo**
35. CFD | gargalo | contagem acumulada por status/dia | tipo
36. Scatterplot de cycle time | outliers | ponto por item (data x dias) com P50/85/95 | tipo
37. Distribuição de tipos de trabalho | mix | % feature/bug/dívida/suporte (investimento) | label, épico
**Qualidade de dados**
38. Sem estimativa | planejamento | count(≠Done AND estimate EMPTY) | tipo, pessoa
39. Sem worklog | tempo | resolved sem timespent | pessoa
40. Sem épico | rastreio | ≠Done AND epic EMPTY | tipo
41. Sem componente/versão/prioridade | roteamento | campos vazios | —
42. Sem squad (órfãos) | escopo | issues fora de qualquer regra de mapeamento | projeto
43. Score de qualidade de dados | resumo | % issues com todos campos obrigatórios (meta >80% como Swarmia) | squad
**Bugs / SLA / Dependências / Outros**
44. Bugs abertos por severidade e idade | risco | count por severidade x faixa de idade | componente
45. Bugs vs SLA | cumprimento | % resolvidos dentro do prazo por prioridade (ex. alvo 3 dias) | prioridade
46. Bug escapado | qualidade | bugs pós-release / entregas | versão
47. Dependências externas | risco | links "blocked by" entre squads, dias pendente | squad dono
48. Dívida técnica | saúde | % throughput com label/tipo dívida; backlog de dívida | épico
49. Reatribuições (auditoria) | transparência | log de mudança de assignee/sprint/squad: quem, quando, de→para | pessoa
50. Notas de contexto | interpretação | anotações por data exibidas como marcadores nos gráficos | squad, pessoa
**Visão da Pessoa**: minha fila ordenada por vencimento/prioridade, meus vencidos/bloqueados, meu WIP, meu histórico próprio (sem comparação com colegas) — reutiliza cards 10, 12, 14, 15, 16, 26 com filtro pessoa=eu.

### Gaps
- Fórmulas e limiares (N dias, faixas, meta de 80%) são sugestões a calibrar com o time, não padrões de mercado.
