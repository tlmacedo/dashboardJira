# Previsão de entrega, planejamento de capacidade com ausências e detecção de gargalos

Legenda: "Cited Findings" = confirmado em fonte citada (várias são secundárias; indicado). "Inferences" = proposta minha, NÃO confirmada em fonte. Estado: 2026-10-22.

## 1. Previsão (Monte Carlo, percentis, aging, regressão/EWMA, poucos dados, confiança)

### Takeaway
O padrão da comunidade Kanban é Monte Carlo sobre throughput histórico (amostragem com reposição) para "quando termina N itens / quantos itens até a data", expresso em percentis (P50 = ponto médio, P85 = compromisso usual, P95 = custo alto de atraso), mais o gráfico Aging WIP comparando idade atual do item com os percentis históricos de cycle time. Com poucos dados (<~10-20 amostras) a previsão é frágil e deve ser rotulada como tal.

### Cited Findings
- Método "quando": usar 12-16 semanas de throughput semanal, amostrar com reposição até zerar os itens restantes, ~10.000 trials, ler percentis do histograma de semanas de conclusão (blog secundário) — [ResumeLens](https://www.resumelens.org/blog/kanban/monte-carlo-forecasting-kanban)
- P50 como marcador de ponto médio (não compromisso); P85 como limiar mais usado para prometer; P95 quando atraso é caro (guia de treinamento, secundário) — [Pro Kanban Forecasting Pocket Guide](https://prokanban.org/forecasting-pocket-guide); atribuição da recomendação de P85 a Vacanti NÃO verificada.
- ActionableAgile tem gráfico "Monte Carlo: When" ("quando estará pronto?", ex.: 85% de chance de terminar até uma data = 85% dos trials terminaram nessa data ou antes) — [55 Degrees AA docs](https://55degrees.atlassian.net/wiki/spaces/AAS/pages/2405826697/Monte+Carlo+When) (só o título carregou; descrição vem do resumo da busca).
- Scatterplot de cycle time: cada ponto é item concluído (x = data de conclusão, y = cycle time); linhas de percentil servem para criar SLE (Service Level Expectation), p.ex. "85% dos itens terminam em 20 dias ou menos" — [AA Cycle Time Scatterplot](https://55degrees.atlassian.net/wiki/spaces/AAS/pages/2324987905/Cycle+Time+Scatterplot); o Kanban Guide usa o mesmo exemplo ("85% em oito dias ou menos") — [Scrum.org Kanban Guide (via busca)](https://www.scrum.org/node/17070)
- Aging WIP: um ponto por item aberto, y = idade, x = estágio atual; sobrepor as linhas de percentil do scatterplot mostra risco de estourar o SLE; ex.: item cedo no fluxo já com 16 dias é improvável cumprir SLE de 20 dias — [AA Aging WIP](https://55degrees.atlassian.net/wiki/spaces/ActionableAgile/pages/386039901)
- Doc legada do AA exigia >=10 itens concluídos para habilitar controles de percentil de ritmo — mesma fonte acima.
- Estudo acadêmico (ACM 2021): acurácia melhor com >=20 pontos históricos (via busca; não aberto na íntegra) — [ACM/Unpaywall](https://unpaywall.org/10.1145%2F3412841.3442030). Outra ferramenta exige >=8 semanas — [Oculai](https://support.oculai.de/en/articles/621732-how-the-forecast-works-monte-carlo-simulation) (regra de fornecedor).
- Métricas de fluxo: WIP, cycle time, throughput, work item age (indicador líder, só itens abertos; cycle time é lagging) — [Kanban Guide for Scrum Teams](https://nkdagility.com/the-kanban-guide-for-scrum-teams/)
- Lei de Little: WIP médio = throughput médio x cycle time médio; premissas: chegada ~ saída, todo trabalho iniciado termina, WIP início ~ fim, idade média do WIP estável, unidades consistentes — [Nave/Vacanti-style (busca)](https://getnave.com/blog/?p=596)
- Não encontrei fonte para Magennis dando mínimo de amostras (gap).

### Inferences
Fórmulas/pseudocódigo (proposta, implementável em Python/JS):
```
# throughput semanal (ou diário) histórico T[] dos últimos 12-16 sem, apenas semanas "normais"
when(N_restante, T, trials=10000):
  for t in trials: n=N; w=0; while n>0: n-=choice(T); w+=1; res.append(w)
  return percentis(res, [50,85,95])      # data = hoje + w * semana
howmany(data_alvo, T): soma de choice(T) x semanas; P85 de "quantos" = percentil 15 da distribuição (o inverso!)
# por issue aberta (aging):
P(terminar em <= d dias | idade a) ≈ (#hist com CT>=a e CT<=a+d)/(#hist com CT>=a)   # condicional, evita usar CT total
risco_idade = idade / P85_CT_da_coluna_ou_tipo
```
- Ajustar Monte Carlo a ausências: amostrar throughput POR PESSOA-DIA (throughput / dias úteis-pessoa) e multiplicar pela capacidade futura de cada semana, em vez de assumir semana "média" (proposta; não vi isso em fonte).
- Segmentar CT por tipo (bug/história/tarefa) e excluir outliers só com justificativa; tamanho de item pouco variável é premissa implícita.
- EWMA: `ewma_t = a*x_t + (1-a)*ewma_{t-1}`, a≈0.3, para tendência de throughput; regressão linear sobre CFD "Done" serve só como linha de tendência rápida. Ambos sem intervalo de confiança, então preferir Monte Carlo e usar EWMA para alerta de mudança de regime.
- Confiança: exibir P50/P85/P95 como datas, nunca data única; com <10 semanas ou <20 itens, exibir selo "baixa confiança" e faixa maior (usar P95).

### Gaps
- Texto de Vacanti (livro) e Magennis não acessados diretamente; independência/autocorrelação das amostras não confirmada em fonte.
- Páginas do AA (Monte Carlo When) retornaram só títulos no fetch.

## 2. Sinais de dificuldade/risco por pessoa/issue e score explicável

### Takeaway
Nenhuma fonte pesquisada define um "score de risco" padrão; a base confirmada é idade vs percentis de cycle time (Aging WIP). Os demais sinais e o score composto são proposta de engenharia.

### Cited Findings
- Idade do item vs linhas de percentil (P85 etc.) é o sinal oficial de risco no Aging WIP — [AA Aging WIP](https://55degrees.atlassian.net/wiki/spaces/ActionableAgile/pages/386039901)
- Work item age é indicador líder para itens abertos — [Kanban Guide for Scrum Teams](https://nkdagility.com/the-kanban-guide-for-scrum-teams/)
- Fluxo parado: linha de "Done" plana no CFD indica bloqueio — [Cumulative Flow Diagram](https://concepts.dsebastien.net/concept/cumulative-flow-diagram/)

### Inferences
Score aditivo e explicável (pontos, cada regra gera uma "razão" textual para o card):
```
idade > P85 do tipo        +30 (>P95: +40)     razão: "idade 23d > P85 (18d)"
parado no status > X dias  +20 (X = P85 do tempo-no-status histórico)
sem update > 5 dias úteis  +10
reaberturas >= 1           +10/cada (máx 20)
transferências de assignee >= 3   +10
worklog > estimativa       +15 (>150%: +25)
flag impedido/bloqueio     +20 (+ dias bloqueado)
due date <= 3 dias úteis e idade > P50 do tipo   +20; vencido +30
score = min(100, soma);  faixas: <30 ok, 30-59 atenção, >=60 risco
```
- Fonte de dados Jira: changelog (status, assignee, resolution reabertura, Flagged), worklog, `updated`, `duedate`, `timeoriginalestimate`.
- Por pessoa: agregar % de itens abertos em risco e WIP por pessoa; tratar como sinal de apoio ao fluxo, não de avaliação individual (risco de uso indevido; recomendação minha).
- Pesos são heurísticos e devem ser calibrados com histórico (ex.: backtest: o score alto precedeu atraso?).

### Gaps
- Sem fonte para pesos/limiares; sem fonte sobre taxas de reabertura/transferência como preditores.

## 3. Gargalos (TOC, CFD, WIP por status, utilização, Little, heatmap)

### Takeaway
CFD (bandas que alargam relativamente às vizinhas, em estágios que deveriam ser estreitos) + WIP por status vs limite + idade por coluna localizam o acúmulo; a lei de Little liga WIP e tempo. Heatmap de carga vs capacidade por semana é desenho próprio.

### Cited Findings
- Banda alargando = fila crescendo no estágio; sinal é divergência da banda vs vizinhas; bandas paralelas e de largura estável = fluxo saudável; "Ready/Backlog" larga pode ser buffer saudável; Done deve crescer — [Cumulative Flow Diagram (dsebastien)](https://concepts.dsebastien.net/concept/cumulative-flow-diagram/), [Kanban Zone](https://kanbanzone.com/2019/power-of-cumulative-flow-diagram/), [Screenful](https://screenful.com/blog/2019-12); fontes de fornecedores divergem em tratar toda expansão como gargalo.
- Lei de Little: WIP = TP x CT; se CT é alto, reduzir WIP primeiro — [Kanban Guide for Scrum Teams](https://nkdagility.com/the-kanban-guide-for-scrum-teams/)
- Ação típica sugerida: reduzir entrada ou adicionar capacidade (fornecedor) — [GitScrum](https://docs.gitscrum.com/en/best-practices/how-to-read-gitscrum-cumulative-flow-diagram)

### Inferences
- Calcular CFD por dia: `count(issues com status_em_data(d) >= etapa_k)` a partir do changelog. Gargalo = etapa onde (entradas - saídas) na janela > 0 de forma sustentada: `delta_k = Δ(banda_k) / banda_k` nas últimas 2-4 semanas.
- Little por etapa: `CT_etapa ≈ WIP_etapa / TP_etapa`; WIP efetivo vs limite: `ocupação = WIP/limite`; alerta >100%.
- Teoria das Restrições (Goldratt: identificar, explorar, subordinar, elevar, repetir): não encontrei fonte citável nesta rodada; tratar como conhecimento geral, não verificado aqui.
- Utilização por pessoa/skill: `carga = soma(estimativa_restante dos itens abertos atribuídos) / capacidade_líquida`; >100% = sobrecarga; skill escasso = quase-gargalo mesmo com média do squad OK.
- Heatmap: linhas = pessoas/skills, colunas = semanas futuras, valor = carga/capacidade; cor >100% vermelho. Distribuir a estimativa restante de cada issue entre hoje e due date (ou P85 de término) em dias úteis.

### Gaps
- Sem fontes primárias sobre TOC, Jira Align ou Nave (cards/gargalo); dados não obtidos.

## 4. Capacidade e ausências (modelo, feriados BR, onde guardar, fórmulas, rebalanceamento)

### Takeaway
Jira Plans usa semana fixa de 40h e não modela folga nativamente; BrasilAPI fornece feriados nacionais; ausências devem viver numa tabela própria/ICS/Tempo. Capacidade líquida = dias úteis x horas x fator de foco - ausências.

### Cited Findings
- BrasilAPI: `GET https://brasilapi.com.br/api/feriados/v1/{ano}` retorna lista com `date` (YYYY-MM-DD), `name`, tipo; para 2026 listou 14 entradas: 01-01, 02-16 e 02-17 (Carnaval), 04-03 (Sexta-feira Santa), 04-05 (Páscoa, domingo), 04-21, 05-01, 06-04 (Corpus Christi), 09-07, 10-12, 11-02, 11-15 (domingo), 11-20, 12-25 — [BrasilAPI 2026](https://brasilapi.com.br/api/feriados/v1/2026) (docs: https://brasilapi.com.br/docs, não carregou). Formato exato de campos conferido apenas pela tabela resumida do fetch.
- Plans (capacidade individual, Premium/Enterprise): grade semanal, alocação em horas/dias/%, visões People/Work, base 40h/semana; "configurações customizadas de tempo/dias úteis ainda não são refletidas"; folga = entrada de texto livre ("Leave") alocada manualmente; importação CSV — [Plan individual capacity](https://support.atlassian.com/jira-software-cloud/docs/plan-individual-capacity-for-your-team/)
- Capacidade de sprint no Plans: scrum com estimativa em tempo = capacidade semanal x semanas (40h x 2 = 80h); story points padrão 30; Kanban só estimativa de tempo e iteração fixa de 1 semana — [Capacity scrum vs kanban](https://support.atlassian.com/jira-software-cloud/docs/capacity-in-scrum-vs-kanban-teams-in-advanced-roadmaps/)
- Dias não úteis do board (Working days) afetam burndown/sprint report, mas não a capacidade de Plans — [Configure working days](https://support.atlassian.com/jira-software-cloud/docs/configure-working-days/); [Individual capacity](https://support.atlassian.com/jira-software-cloud/docs/plan-individual-capacity-for-your-team/)
- Tempo (Capacity Planner/Planner): usuários planejam suas férias, admins configuram holiday schemes e workload schemes, compara planejado (Planner) vs real (Timesheets), exibe eventos Google/O365 — [Tempo Marketplace](https://marketplace.atlassian.com/apps/1211881/tempo-planner-resource-planning), [Tempo Help](https://help.tempo.io/planner/latest/get-started-with-tempo-planner)
- Time off e feriados ajustam capacidade em apps de terceiros (ex.: Sprint planning with capacity planning) — [Marketplace](https://marketplace.atlassian.com/apps/1219264/scrum-sprint-planning-with-capacity-planning-for-jira)

### Inferences
Fórmulas:
```
dias_úteis(sem) = seg-sex - feriados_nacionais - feriados_locais(cidade/UF)
cap_líquida(p,sem) = (dias_úteis - dias_ausência(p,sem)) x h_dia(p) x fator_foco   # foco 0.6-0.8 típico (faixa dada pelo pedido; sem fonte)
cap_squad(sem) = Σ_p cap_líquida(p,sem)
carga(p,sem)   = Σ_issues alocação_semana(issue,p)      # restante distribuído até due/término P85
sobrecarga(p,sem) = max(0, carga/cap_líquida - 1);  flag se > 0.1
```
- Cautela BR: Carnaval (seg/ter) e Corpus Christi aparecem na BrasilAPI, mas são tecnicamente ponto facultativo nacional (conhecimento próprio, não confirmado aqui); torne "contar como feriado" configurável. Feriados estaduais/municipais não vêm da BrasilAPI — manter tabela própria. Sábados/domingos já excluídos; Páscoa e 15/11 caem em dia não útil em 2026.
- Onde guardar ausências: (a) tabela própria `ausencias(pessoa, inicio, fim, tipo, parcial%)` - recomendada; (b) CSV versionado; (c) ICS/Google Calendar de equipe lido por API (eventos "Férias - Nome"); (d) Tempo Planner via API se já houver licença. Jira Cloud não tem nativo.
- Rebalanceamento: ao detectar sobrecarga futura `p,sem`: listar issues de p com vencimento/término projetado na semana, ordenar por (risco, due); sugerir reatribuir a quem tenha folga >= horas restantes e skill compatível (matching por label/componente); alertar também se o item vai cruzar a ausência (término P85 > início da folga).
- Na previsão Monte Carlo, reduzir throughput esperado semana a semana por `cap_squad(sem)/cap_squad_média`.

### Gaps
- Fator de foco 60-80%: sem fonte citável; definição de "overallocation" no Plans não documentada na página lida.
- Docs oficiais de Plans sobre alertas de overallocation e Tempo API de ausências não consultados.

## 5. Como ferramentas maduras apresentam (cards/visões)

### Takeaway
ActionableAgile mostra percentis/SLE, Aging WIP e Monte Carlo com data por confiança; Plans/Tempo mostram grade semanal pessoa x semana com capacidade restante e alocação.

### Cited Findings
- AA: scatterplot + SLE, Aging WIP com linhas de percentil, Monte Carlo When/How Many — links na seção 1.
- Plans: visões People (alocado vs disponível) e Work; sprint com capacidade em horas/pontos — links na seção 4.
- Tempo: planos por usuário, esquemas de feriado/carga, planejado vs real — links na seção 4.

### Inferences
- Card sugerido por issue: idade + barra com marcas P50/P85/P95, status parado há X dias, score com 2-3 razões; card por pessoa: carga/capacidade das próximas 4 semanas + ausências marcadas; card de squad: data P50/P85/P95 do backlog e semanas em sobrecarga.

### Gaps
- Nave e Jira Align: nenhuma fonte consultada; não afirmo o que mostram.
- Detalhe de UI de cards do AA/Plans além das descrições acima não verificado.
