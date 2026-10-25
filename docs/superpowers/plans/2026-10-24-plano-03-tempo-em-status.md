# Fatia vertical: card Tempo em status (Fase 3) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo:** Entregar o card **Tempo em status** de ponta a ponta (sincronização simulada, cálculo, API com filtro por papel, detalhamento e tela), validando a arquitetura antes de portar os demais cards.

**Arquitetura:** `Syncer` lê issues com changelog por `JiraClient` (só com `MockEngine` e fixtures) e grava no PostgreSQL de forma idempotente. Um domínio puro (`TimelineBuilder`, `TimeInStatusCalculator`) transforma o histórico em intervalos por status e por responsável. A rota `/api/cards/tempo-em-status` aplica `Visibility` e devolve o DTO; `/api/drill/tempo-em-status` devolve as tasks que compõem cada número. A tela React consome as duas.

**Stack:** igual aos Planos 01 e 02.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 4.2, 5.4, 6.1, 6.3). Depende dos Planos 01 (calendário, papéis, cliente, banco) e 02 (`CardQueryFactory`, ficha `tempo-em-status.card.yaml`).

## Pré-requisito: Tarefa 0 de inicialização da aplicação (definida pela revisão final do Plano 1)

O Plano 1 entregou as peças (banco, calendário, papéis, cliente Jira, controle de acesso), mas **nada as liga na subida do app**, e este plano consome todas elas. Antes da Tarefa 1 deste plano, detalhar e executar uma **Tarefa 0** com, no mínimo:
- `Application.module` carrega `BusinessCalendar` (de `configDir/calendario*.yaml`) e `RoleDirectory` (de `configDir/papeis.yaml`, caindo em `papeis.exemplo.yaml` só em modo desenvolvimento), abre o `DataSource`, roda `Database.migrate` e fecha o pool ao encerrar (tamanho do pool configurável).
- `Settings` passa a ler `DATABASE_USER` e `DATABASE_PASSWORD`; `toString` do `Settings` nunca expõe credenciais; decidir "variável vazia = padrão".
- Fábrica única do cliente Jira: `JiraClientFactory.create(settings, instance)` valida `https` (ou `http` só em localhost), token não vazio e cria o `LiveGuard` a partir de `settings.jiraLiveEnabled`; o `Application` nunca monta um `JiraClient` por outro caminho. Corrigir também no cliente: `total` ausente na resposta é erro (não 0), teto de iterações em `searchAll`, `fields` vazio falha na chamada, falhas de rede viram `JiraException`.
- `Principal.roles` sempre derivado do `RoleDirectory` na data de hoje; validação de vigências de `USUARIO` sobrepostas no carregamento; `isReporterAuthorized` e GERENTE: seguir a decisão do usuário registrada no ledger.
- `logback.xml` com nível INFO.
Cada item com teste, no mesmo formato das tarefas deste plano; só depois seguir para a Tarefa 1.

## Restrições globais

- Nenhuma chamada ao Jira real; fixtures em `src/test/resources/fixtures/dc/`.
- Dia civil e dias úteis de Manaus; **dias úteis por padrão**, `unit=corrido` alterna.
- Percentil por interpolação linear (tipo 7 / `numpy` padrão): `P(p) = v[i] + (h - i)(v[i+1] - v[i])` com `h = (n-1)p`.
- Concluída = categoria `DONE` **e** `resolution` preenchida; status fora do mapa → `UnmappedStatusException` (o card mostra aviso em vez de adivinhar).
- Cada pessoa responde pelo tempo em que ficou com a task (posse); ordem alfabética nas tabelas de pessoas; sem ranking.
- Todo número do card abre as tasks que o compõem, com o mesmo tamanho da lista.

## Foco da revisão

- Task que nunca saiu de "A Fazer" (sem transição) e task aberta sem nenhuma mudança de responsável.
- Reentrada no mesmo status (A → B → A): os tempos somam, não se sobrescrevem.
- Troca de responsável no mesmo instante de uma troca de status (empate de timestamps).
- Changelog em que o assignee vem só como `fromString/toString` (nome de exibição) em vez de `from/to`.
- Usuário comum pedindo `pessoa=` de outra pessoa: recebe 403 ou lista vazia, nunca dados alheios.

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  domain/StatusCategory.kt, StatusMap.kt, IssueTimeline.kt      # modelos e carga do mapa de status
  domain/TimelineBuilder.kt                                      # intervalos por status e responsável
  domain/Percentiles.kt                                          # percentil tipo 7
  domain/TimeInStatusCalculator.kt                               # estatísticas do card
  sync/ChangelogParser.kt, Syncer.kt, IssueStore.kt              # leitura do changelog DC e gravação
  cards/TempoEmStatusRoutes.kt, TempoEmStatusDto.kt              # API do card e detalhamento
app/config/equipes.demo.yaml, status_map.exemplo.yaml
app/backend/src/test/resources/fixtures/dc/search-tg-basic-changelog.json
app/backend/src/test/kotlin/br/sidia/painel/{domain,sync,cards}/*Test.kt
app/frontend/src/screens/TempoEmStatus/{TempoEmStatus.tsx,api.ts,DrillDrawer.tsx}
docs/analise/diferencas-esperadas-tempo-em-status.md
```

---

### Tarefa 1: Modelos, mapa de status e linha do tempo por status e responsável

**Arquivos:**
- Criar: `domain/StatusCategory.kt`, `domain/StatusMap.kt`, `domain/IssueTimeline.kt`, `domain/TimelineBuilder.kt`, `app/config/status_map.exemplo.yaml`
- Teste: `domain/TimelineBuilderTest.kt`

**Interfaces:**
- Produz:
  ```kotlin
  enum class StatusCategory { TODO, ACTIVE, WAIT, BLOCKED, DONE }
  class UnmappedStatusException(val status: String) : RuntimeException("Status sem mapeamento: $status")
  class StatusMap(private val byName: Map<String, StatusCategory>) {
      fun category(status: String): StatusCategory
      companion object { fun fromYaml(path: Path): StatusMap }   // status_map: {"A Fazer": todo, ...}
  }
  data class Transition(val at: Instant, val from: String?, val to: String, val authorRef: String?)
  data class AssigneeChange(val at: Instant, val to: String?)
  data class IssueTimeline(val key: String, val summary: String, val issueType: String, val created: Instant,
      val initialStatus: String, val initialAssignee: String?, val transitions: List<Transition>,
      val assigneeChanges: List<AssigneeChange>, val resolved: Instant?, val resolution: String?,
      val currentStatus: String, val currentAssignee: String?, val labels: List<String> = emptyList())
  data class StatusInterval(val status: String, val category: StatusCategory, val start: Instant, val end: Instant, val assignee: String?)
  object TimelineBuilder { fun intervals(t: IssueTimeline, now: Instant, map: StatusMap): List<StatusInterval> }
  fun IssueTimeline.firstEntered(status: String): Instant?
  fun IssueTimeline.lastLeft(status: String): Instant?
  ```
  `intervals`: parte de `created` com `initialStatus` e `initialAssignee`; cada `Transition` fecha o intervalo e abre outro; cada `AssigneeChange` corta o intervalo corrente sem mudar o status; termina em `resolved` (ou `now` se aberta). Empate de timestamps: a troca de status é aplicada antes da troca de responsável.

- [ ] **Passo 1: Testes que falham** (fuso Manaus; cenário **TG-1**: criada `2026-10-05T12:00Z` em "A Fazer" com responsável `ana`; `2026-10-06T12:00Z` → "Em andamento"; `2026-10-08T12:00Z` responsável passa a `bruno`; `2026-10-09T12:00Z` → "Em revisão"; `2026-10-12T12:00Z` → "Concluído", resolvida nesse instante):
  - `cortaPorStatusEPorResponsavel`: 5 intervalos: A Fazer/ana; Em andamento/ana; Em andamento/bruno; Em revisão/bruno; (o "Concluído" não gera intervalo).
  - `reentradaNoMesmoStatusSomaDepois`: A → B → A gera dois intervalos de A (a soma é feita pelo calculador, Tarefa 3).
  - `semTransicaoFicaTodoNoMesmoStatus`: issue sem `transitions` aberta gera 1 intervalo de `created` até `now`.
  - `empateDeTimestampAplicaStatusAntes`: troca de status e de responsável no mesmo instante resulta em intervalo novo já com o responsável novo.
  - `primeiraEntradaEUltimaSaida`: `firstEntered("Em andamento") == 2026-10-06T12:00Z` e `lastLeft("Em andamento") == 2026-10-09T12:00Z`; status nunca visitado → `null`.
  - `statusForaDoMapaLanca`: `UnmappedStatusException("Revisando")`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TimelineBuilderTest*'` → FALHA.
- [ ] **Passo 3: Implementar** a varredura ordenada por `(at, tipo)` com status antes de responsável.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Linha do tempo por status e responsável"`.

---

### Tarefa 2: Percentis e estatísticas básicas

**Arquivos:** Criar `domain/Percentiles.kt`; Teste `domain/PercentilesTest.kt`.

**Interfaces:** Produz `object Percentiles { fun of(values: List<Double>, p: Double): Double?; fun median(values: List<Double>): Double? }` (lista vazia → `null`; `p` em 0..1, fora disso `IllegalArgumentException`).

- [ ] **Passo 1: Testes que falham:** `p50DeUmADez`: valores 1..10 → `5.5`; `p85DeUmADez` → `8.65` (tolerância 1e-9); `umSoValor`: `[7.0]` → qualquer `p` dá `7.0`; `listaVazia` → `null`; `pForaDoIntervaloFalha`; `ordemDeEntradaNaoImporta`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PercentilesTest*'` → FALHA. **Passo 3: Implementar** com ordenação e interpolação linear. **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Percentis por interpolação linear"`.

---

### Tarefa 3: `TimeInStatusCalculator`

**Arquivos:** Criar `domain/TimeInStatusCalculator.kt`; Teste `domain/TimeInStatusCalculatorTest.kt`.

**Interfaces:**
- Consome: `BusinessCalendar` (Plano 01), `TimelineBuilder`, `Percentiles`, `StatusMap`.
- Produz:
  ```kotlin
  enum class TimeUnitMode { UTIL, CORRIDO }
  data class StatusStat(val status: String, val category: StatusCategory, val median: Double, val p85: Double, val n: Int)
  data class PersonStatus(val person: String, val status: String, val median: Double, val n: Int)
  data class StuckIssue(val key: String, val summary: String, val assignee: String?, val status: String, val daysInStatus: Double, val median: Double)
  data class TimeInStatusResult(val analyzed: List<String>, val flowEfficiency: Double?, val perStatus: List<StatusStat>,
      val perPersonStatus: List<PersonStatus>, val stuck: List<StuckIssue>, val unit: TimeUnitMode)
  class TimeInStatusCalculator(private val calendar: BusinessCalendar, private val map: StatusMap) {
      fun compute(closed: List<IssueTimeline>, open: List<IssueTimeline>, now: Instant, unit: TimeUnitMode = TimeUnitMode.UTIL,
                  excludeOutlierDays: Int? = null, stuckFactor: Double = 1.5, stuckMinDays: Double = 2.0): TimeInStatusResult
  }
  ```
  Regras: `analyzed` = chaves das concluídas (`DONE` + `resolution` preenchida) não excluídas como outlier; `excludeOutlierDays=N` exclui a task cujo ciclo (criação → resolução) em dias corridos é `> N` (padrão `null`: não exclui). Tempo por status = soma de **todos** os intervalos do status (reentradas somam) na unidade pedida. `flowEfficiency` = tempo em categoria `ACTIVE` ÷ tempo em todas as categorias exceto `DONE`, somados sobre as analisadas (`null` se o denominador for 0). `perPersonStatus` usa a posse (intervalos cortados por responsável), em ordem alfabética de pessoa. `stuck` = abertas cujo tempo no status atual `> max(stuckMinDays, stuckFactor × mediana do status entre as analisadas)`; se não há mediana do status, usa só `stuckMinDays`.

- [ ] **Passo 1: Testes que falham** (cenário **TG-1** da Tarefa 1; `now=2026-10-23T12:00Z`):
  - `tempoPorStatusEmDiasUteis`: A Fazer `1.0`, Em andamento `3.0`, Em revisão `0.666667` (tol. 1e-6; **12/10 é feriado**).
  - `tempoPorStatusEmDiasCorridos` (`CORRIDO`): `1.0`, `3.0`, `3.0`.
  - `eficienciaDeFluxo`: em dias úteis `3.0 / 4.666667 = 0.642857`.
  - `posseDivideOTempo`: ana em Em andamento `2.0`; bruno em Em andamento `1.0` e em Em revisão `0.666667`; ana em A Fazer `1.0`.
  - `reentradaSoma`: issue A→B→A com 1 d + 2 d em A → `3.0` (corridos).
  - `outlierExcluidoSoQuandoPedido`: task com ciclo de 40 d corridos entra em `analyzed` por padrão e some com `excludeOutlierDays=30`.
  - `paradaAlemDoNormal`: aberta em "Em revisão" desde `2026-10-14T12:00Z` → `daysInStatus=7.0`, mediana `0.666667` do status → limite `max(2.0, 1.0)=2.0` → listada; outra com 1,5 dia **não** é listada.
  - `abertaSemTransicaoNaoQuebra`; `semConcluidasDevolveNulosSemErro` (`flowEfficiency == null`, listas vazias).
  - `statusSemMapaPropagaExcecao`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TimeInStatusCalculatorTest*'` → FALHA.
- [ ] **Passo 3: Implementar** usando `calendar.businessDays/calendarDays` por intervalo.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Calculador de tempo em status com posse por pessoa"`.

---

### Tarefa 4: Leitura do changelog do DC e gravação idempotente

**Arquivos:**
- Criar: `sync/ChangelogParser.kt`, `sync/IssueStore.kt`, `sync/Syncer.kt`, `src/test/resources/fixtures/dc/search-tg-basic-changelog.json`
- Teste: `sync/ChangelogParserTest.kt`, `sync/SyncerTest.kt` (PostgreSQL por Testcontainers; `MockEngine`)

**Interfaces:**
- Consome: `JiraClient.searchAll`, `JiraClient.fullChangelog` (Plano 01), `JqlQuery` (Plano 02), tabelas da migração `V1__base.sql`.
- Produz:
  ```kotlin
  object ChangelogParser {
      fun toTimeline(issue: JsonObject, changelog: List<JsonObject>): IssueTimeline   // histories[{id, created, author{name}, items[{field, from, to, fromString, toString}]}]
      fun toEvents(issue: JsonObject, changelog: List<JsonObject>): List<ChangeEventRecord>
  }
  data class ChangeEventRecord(val changelogId: String, val issueKey: String, val field: String, val from: String?, val to: String?, val at: Instant, val authorRef: String?)
  interface IssueStore {
      fun upsert(issue: JsonObject, events: List<ChangeEventRecord>, instance: String)
      fun timelines(instance: String, closedFrom: Instant?, closedTo: Instant?, openOnly: Boolean): List<IssueTimeline>
  }
  class Syncer(private val client: JiraClient, private val store: IssueStore, private val instance: String) {
      suspend fun run(query: JqlQuery): SyncSummary          // searchAll(...) e, por issue com changelog truncado, fullChangelog
  }
  data class SyncSummary(val issues: Int, val events: Int)
  ```
  Para `assignee`, usar `from/to` quando presentes; senão `fromString/toString`. Idempotência por `changelogId` (chave única da tabela).

- [ ] **Passo 1: Fixture.** Escrever o JSON de resposta do DC com 4 issues (2 concluídas, 1 aberta parada, 1 com `changelog.total` maior que o número de históricos) e uma segunda resposta com o changelog completo dessa última.
- [ ] **Passo 2: Testes que falham:**
  - `statusViraTransicao`: item `field="status"` gera `Transition(from,to,at,author)`.
  - `assigneeComFromToEUsaUsername`; `assigneeSoComStringsUsaNomeDeExibicao` (cenário citado nos riscos da spec).
  - `datasComOffsetViramInstant`: `"2026-10-05T08:00:00.000-0400"` → `2026-10-05T12:00:00Z`.
  - `syncerEhIdempotente`: rodar `run` duas vezes → `issue` e `change_event` com a mesma contagem (`SyncSummary(4, N)` e tabelas iguais).
  - `changelogTruncadoBuscaOCompleto`: o `MockEngine` recebe a requisição de `/issue/{key}/changelog` e o evento extra é gravado.
  - `liveDesligadoNaoFazRequisicao`: com `LiveGuard(false)` o `Syncer.run` falha com `JiraLiveDisabledException` e 0 requisições (a trava vale também aqui).
- [ ] **Passo 3: Rodar** `./gradlew test --tests '*ChangelogParserTest*' --tests '*SyncerTest*'` → FALHA.
- [ ] **Passo 4: Implementar** (upsert com `ON CONFLICT (changelog_id) DO NOTHING` e `ON CONFLICT (key) DO UPDATE` para a issue).
- [ ] **Passo 5: Rodar** → PASSA. **Passo 6: Commit** `git commit -m "Sincronização simulada do changelog do DC, idempotente"`.

---

### Tarefa 5: API do card e detalhamento com filtro por papel

**Arquivos:**
- Criar: `cards/TempoEmStatusDto.kt`, `cards/TempoEmStatusRoutes.kt`
- Modificar: `Application.kt` (registrar rotas)
- Teste: `cards/TempoEmStatusRoutesTest.kt`

**Interfaces:**
- Consome: `IssueStore.timelines`, `TimeInStatusCalculator`, `Visibility`, `RoleDirectory`, `installAccessControl`, tabela `access_log`.
- Produz:
  ```
  GET /api/cards/tempo-em-status?squad=&pessoa=&tipo=&os=&de=aaaa-mm-dd&ate=aaaa-mm-dd&unit=util|corrido&semOutliers=true|false
  GET /api/drill/tempo-em-status?kind=analisadas|paradas|status:<nome>|pessoa:<id>:status:<nome>&(mesmos filtros)
  ```
  `TempoEmStatusDto(analisadas: Int, eficienciaFluxo: Double?, paradas: Int, porStatus: List<StatusStat>, pessoaStatus: List<PersonStatus>, abertasParadas: List<StuckIssue>, unit: String, avisos: List<String>)`; `DrillRow(key: String, resumo: String, responsavel: String?, status: String, detalhe: String)`. Padrão de período: últimos 90 dias (fuso Manaus). `avisos` traz `"status sem mapeamento: X"` quando `UnmappedStatusException`. Filtro: só entram issues com **algum intervalo** de responsável em `Visibility.visibleUserIds`; `pessoa=` fora do visível → 403.

- [ ] **Passo 1: Testes que falham** (autenticador de teste por papel; dados do cenário TG-1 mais um segundo squad):
  - `usuarioVeSoOProprio`: `USUARIO` `ana` recebe `analisadas` contando só tasks em que ela teve posse; `pessoaStatus` só tem linhas dela.
  - `squadLeaderVeSeuSquad`: `SQUAD_LEADER` do squad A não vê tasks só do squad B.
  - `gerenteVeTodos`.
  - `pessoaDeOutroDoUsuarioComumRetorna403`.
  - `unidadePadraoEUtil`: sem `unit` o DTO tem `unit="util"`; com `unit=corrido` os números são os dos corridos.
  - `periodoPadraoNoventaDias`: sem `de/ate` considera concluídas nos 90 dias até hoje (relógio injetável).
  - `drillTemOMesmoTamanhoDoCard`: para `kind=analisadas` o tamanho da lista == `analisadas`; para `kind=paradas` == `paradas`; para `status:Em revisão` == `porStatus["Em revisão"].n`.
  - `statusNaoMapeadoVaiParaAvisos`: resposta 200 com `avisos` não vazio e números nulos/zerados, nunca 500.
  - `consultaEhRegistrada`: cada chamada grava uma linha em `access_log` (usuário, rota, filtros).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TempoEmStatusRoutesTest*'` → FALHA.
- [ ] **Passo 3: Implementar** reaproveitando `Visibility` e o calculador; relógio e calendário injetados.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "API do card Tempo em status e detalhamento"`.

---

### Tarefa 6: Tela Tempo em status (React)

**Arquivos:**
- Criar: `app/frontend/src/screens/TempoEmStatus/{TempoEmStatus.tsx,api.ts,DrillDrawer.tsx}`; rota no `nav.ts`
- Teste: `TempoEmStatus.test.tsx`

**Interfaces:**
- Consome: `GET /api/cards/tempo-em-status` e `/api/drill/tempo-em-status` (Tarefa 5); `t()`, `ThemeProvider`, `Shell` (Plano 01).
- Produz: `export function TempoEmStatus(): JSX.Element`; tipos `TempoEmStatusDto` e `DrillRow` espelhando o backend; `fetchTempoEmStatus(params): Promise<TempoEmStatusDto>`.
  Layout **denso** (plano visual, seção 5 e protótipo A): cabeçalho com filtros em chips e botão CSV; 3 KPIs (Tasks analisadas, Eficiência de fluxo, Paradas além do normal); barras mediana/P85 por status; mapa pessoa × status (≤ 3 d ✓, 3–6 d ●, > 6 d ▲); tabela de abertas paradas; alternância Dias úteis/Dias corridos que muda `unit` na URL.

- [ ] **Passo 1: Testes que falham** (Vitest + Testing Library, `fetch` simulado):
  - `rendersKpis`: mostra 236, 31% e 17 a partir do DTO simulado, com `role="button"` nos KPIs clicáveis.
  - `alternarUnidadeRecarrega`: clicar "Dias corridos" chama a API com `unit=corrido`.
  - `clicarNoKpiAbreAGaveta`: abre `DrillDrawer` e mostra tantas linhas quanto o KPI indica.
  - `avisoDeStatusNaoMapeado`: com `avisos` não vazio, o aviso aparece e os KPIs mostram "—".
  - `semPermissaoMostraMensagem`: resposta 403 exibe a mensagem de papel, não quebra.
  - `simbolosAlemDaCor`: células do mapa trazem `✓`, `●` ou `▲` como texto.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/TempoEmStatus` → FALHA.
- [ ] **Passo 3: Implementar** com TanStack Query; filtros e unidade na URL.
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build` sem erros.
- [ ] **Passo 5: Captura de verificação.** Subir backend e frontend em modo demo e fazer **uma** rodada de capturas (Chrome headless: 1440 e 390 px; temas claro, escuro e Sidia); corrigir tudo de uma vez; no máximo mais uma rodada de confirmação.
- [ ] **Passo 6: Commit** `git commit -m "Tela Tempo em status"`.

---

### Tarefa 7: Diferenças esperadas e compatibilidade com o app atual

**Arquivos:**
- Criar: `docs/analise/diferencas-esperadas-tempo-em-status.md`
- Teste: `domain/CompatLegadoTest.kt`

**Interfaces:** Consome `TimeInStatusCalculator` (Tarefa 3). Produz um documento e um teste de equivalência.

- [ ] **Passo 1: Teste que falha.** `CompatLegadoTest.modoCorridoReproduzAFormulaDoAppAtual`: para o cenário TG-1, em `CORRIDO`, os valores por status e a eficiência de fluxo seguem a fórmula do app Python do relatório (`legacy/backend/jiradash/analytics.py::time_in_status`, tempo corrido por status, eficiência = ativo ÷ não concluídos) → `1.0 / 3.0 / 3.0` e `3.0/7.0`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompatLegadoTest*'` → FALHA ou PASSA (se a Tarefa 3 já cobrir). Se passar de primeira, conferir manualmente a fórmula no código legado antes de aceitar.
- [ ] **Passo 3: Escrever o documento** com a tabela "diferença esperada": fuso (Manaus × UTC), dias úteis × corridos, concluída (categoria + `resolution`), posse proporcional, mediana por interpolação linear, mapa de status obrigatório; cada linha com o efeito e o exemplo numérico do cenário TG-1.
- [ ] **Passo 4: Commit** `git commit -m "Diferenças esperadas e teste de compatibilidade do Tempo em status"`.

---

## Autoavaliação

- **Cobertura da spec:** 4.2 (fuso, dias úteis, concluída, posse, percentis), 5.4 (sincronização idempotente), 6.1 (primeira entrada/última saída, outliers opcionais), 6.3 (posse), critérios de sucesso (número do card = lista do detalhamento, visibilidade por papel, trava). Percentis por tipo de issue e demais cards ficam na Fase 5.
- **Consistência de tipos:** `IssueTimeline` (T1) é produzido por `ChangelogParser` (T4) e consumido por `TimeInStatusCalculator` (T3) e pela rota (T5); `TempoEmStatusDto` (T5) tem o espelho no frontend (T6).
- **Pendências deliberadas:** valores reais de campos (`from/to` do assignee no DC) só na 1ª conexão autorizada; o teste cobre os dois formatos.
