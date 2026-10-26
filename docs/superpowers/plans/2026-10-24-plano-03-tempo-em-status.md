# Fatia vertical: card Tempo em status (Fase 3) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo:** Entregar o card **Tempo em status** de ponta a ponta (sincronização simulada, cálculo, API com filtro por papel, detalhamento e tela), validando a arquitetura antes de portar os demais cards.

**Arquitetura:** `Syncer` lê issues com changelog por `JiraClient` (só com `MockEngine` e fixtures) e grava no PostgreSQL de forma idempotente. Um domínio puro (`TimelineBuilder`, `TimeInStatusCalculator`) transforma o histórico em intervalos por status e por responsável. A rota `/api/cards/tempo-em-status` aplica `Visibility` e devolve o DTO; `/api/drill/tempo-em-status` devolve as tasks que compõem cada número. A tela React consome as duas.

**Stack:** igual aos Planos 01 e 02.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 4.2, 5.4, 6.1, 6.3). Depende dos Planos 01 (calendário, papéis, cliente, banco) e 02 (`CardQueryFactory`, ficha `tempo-em-status.card.yaml`).

## Estado herdado do Plano 3A (já executado) e o que falta (Tarefas 0A e 0B)

A inicialização da aplicação (calendário, papéis, banco, fábrica única do cliente Jira, controle de acesso) foi executada no plano [`2026-10-25-plano-03a-inicializacao.md`](2026-10-25-plano-03a-inicializacao.md). O que este plano **assume como pronto**:
- `AppContext(settings, calendar, roles, dataSource, jira)` com `load`; `module(settings, context, authenticator)`; `testContext(settings)` sem banco em `src/test/kotlin/br/sidia/painel/TestContext.kt`; `TestPaths.configDir`.
- **Identidade decidida: `id` da pessoa em `papeis.yaml` = username do Jira = `assignee`.** `Visibility.visibleUserIds(principal, roles, hoje, allUsers)` e a auditoria comparam por igualdade. Os principais de teste deste plano são usernames (`ana`, `bruno`, `carla`, `lider.alfa`, `coord.um`, `gerente.exemplo`), montados em um `RoleDirectory` de teste em memória (regras do `RoleDirectory`: id `[a-z0-9._-]`, squad obrigatório para USUARIO e SQUAD_LEADER, vigências).
- `JiraClientFactory.create(instanceId)` é o ÚNICO criador de `JiraClient` em produção; com `JIRA_LIVE_ENABLED=false` devolve um cliente que não envia nada e não exige credencial. O `Syncer` recebe o cliente de `context.jira.create(...)`, nunca o constrói (um teste varre `src/main`).
- `JqlBuilder`/`CardQueryFactory.build(spec, params): JqlQuery` (usernames entre aspas; `resolved < dia seguinte`; `fields` por id).

**O que falta antes das tarefas de domínio** (Tarefas 0A e 0B, abaixo): cache e fechamento dos clientes HTTP da fábrica; busca tipada por `JqlQuery`; teto de itens para a reconciliação; teste do `delay` do retry com cancelamento; máscara do token escapado; `testContextComBanco`; gravação do `access_log`; normalização de username na fronteira.

---

### Tarefa 0A: Cliente Jira — cache, fechamento, busca tipada, teto de itens e máscara escapada

**Arquivos:** Modificar `jira/JiraClientFactory.kt`, `jira/JiraClient.kt`, `AppContext.kt`; Teste `jira/JiraClientFactoryCacheTest.kt`, `jira/JiraClientRobustezTest.kt` (acrescentar).

**Interfaces:**
- `JiraClientFactory.create(instanceId)` passa a devolver SEMPRE a mesma instância para o mesmo id (cache por instância, thread-safe); `JiraClientFactory : AutoCloseable` com `close()` que fecha todos os `HttpClient` criados (idempotente); `AppContext.close()` chama `jira.close()` ANTES de fechar o pool do banco.
- `JiraClient.search(query: JqlQuery)`, `searchAll(query: JqlQuery, pageSize: Int = 1000, maxItens: Int = 50_000): Flow<JsonObject>` e `count(query: JqlQuery)`; as versões com `SearchRequest(jql: String)` passam a ser `internal` (só testes e a própria classe); a `JqlQuery` já carrega `fields` e `expandChangelog`. `maxItens` (padrão 50.000, 1..200.000) substitui o teto fixo de 10.000 itens; estourar → `JiraException` citando o limite e o `total`.
- Máscara do token também na forma ESCAPADA: JSON (`\/`, `\u00xx`), URL-encoded (`%2B`...) e HTML (`&#43;`), em `details` e mensagens.

- [ ] **Passo 1: Testes que falham:** `createDevolveAMesmaInstancia` (duas chamadas, mesmo objeto; instâncias diferentes → objetos diferentes); `closeFechaOsHttpClients` e `closeEhIdempotente` (usa provedor injetável que registra o fechamento); `appContextCloseFechaClientesAntesDoPool` (ordem registrada); `searchAllComJqlQueryUsaFieldsEExpand` (corpo enviado tem os `fields` e o `expand=changelog` da query); `maxItensEstouraComJiraException` (total 60.000 com padrão → erro; com `maxItens=100_000` passa em páginas); `maxItensInvalidoFalha` (0 e 200_001); `tokenEscapadoEmJsonUrlEHtmlEhMascarado`; `delayDoRetryRespeitaCancelamento` (cancelar a coroutine durante o `delay` interrompe sem nova requisição).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraClient*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, e a suíte completa (`cleanTest test`; confira os XML). **Passo 5: Commit** `git commit -m "Cliente Jira: cache e fechamento, busca por JqlQuery, teto de itens e máscara do token escapado"`.

---

### Tarefa 0B: Contexto de teste com banco, `access_log` e normalização de username

**Arquivos:** Criar `db/AccessLog.kt`, `security/Usernames.kt`, `src/test/kotlin/br/sidia/painel/TestContextComBanco.kt`; Modificar `security/Authenticator.kt`; Teste `db/AccessLogTest.kt`, `security/UsernamesTest.kt`.

**Interfaces:**
- `class AccessLog(private val ds: DataSource) { fun record(userId: String, route: String, filters: Map<String, String>, at: Instant) }` grava em `access_log(user_id, route, filters, at)`; `filters` em JSON; valores saneados (sem controle) e truncados em 200 caracteres; nunca grava token nem corpo.
- `fun normalizarUsername(raw: String): String` (`trim().lowercase(Locale.ROOT)`) usado na fronteira: `Principal.userId`, reporter e assignee vindos do Jira ou do login; id vazio → `IllegalArgumentException`.
- `fun testContextComBanco(settings, container): AppContext` com PostgreSQL real (Testcontainers compartilhado por classe) e `access_log` limpo entre testes.

- [ ] **Passo 1: Testes que falham:** `gravaUmaLinhaPorConsulta`; `filtrosSaneadosETruncados` (valor com `\n` e 500 caracteres); `naoGravaSegredos` (filtros com chave `token`/`password` são descartados); `normalizaCaixaEEspacos` (`" Ana.Silva "` → `ana.silva`); `usernameVazioFalha`; `principalUsaUsernameNormalizado`.
- [ ] **Passo 2: Rodar** os testes → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `git commit -m "access_log, normalização de username e contexto de teste com banco"`.

---

### (Tarefas 1 a 7: domínio, sincronização, API e tela)

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
      suspend fun run(query: JqlQuery): SyncSummary          // client.searchAll(query) e, por issue com changelog truncado, fullChangelog
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
  - `liveDesligadoNaoFazRequisicao`: o cliente vem de `JiraClientFactory(settings com jiraLiveEnabled=false).create("samsung")` (não de `JiraClient(...)`): `Syncer.run` falha com `JiraLiveDisabledException` e 0 requisições (a trava vale também aqui).
  - `syncerUsaUsernameNormalizado`: assignee `Ana.Silva` do changelog fica `ana.silva` na linha do tempo e na gravação (`normalizarUsername`, Tarefa 0B).
  - Nos demais testes do `Syncer`, o cliente é criado com `MockEngine` pelo construtor `internal` de `JiraClient` (permitido: o teste está no mesmo módulo; a varredura só olha `src/main`).
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
- Consome: `IssueStore.timelines`, `TimeInStatusCalculator`, `Visibility`, `RoleDirectory`, `installAccessControl`, `AccessLog` (Tarefa 0B), `normalizarUsername`.
- Produz também `GET /api/me` → `{"userId":"ana","roles":["USUARIO"]}` (papéis do `RoleDirectory` na data de hoje, nunca de claims; usado pelo frontend para montar o menu em produção; exige principal; em modo desenvolvimento devolve o principal `dev` com `GERENTE`).
- Produz:
  ```
  GET /api/cards/tempo-em-status?squad=&pessoa=&tipo=&os=&de=aaaa-mm-dd&ate=aaaa-mm-dd&unit=util|corrido&semOutliers=true|false
  GET /api/drill/tempo-em-status?kind=analisadas|paradas|status:<nome>|pessoa:<id>:status:<nome>&(mesmos filtros)
  ```
  `TempoEmStatusDto(analisadas: Int, eficienciaFluxo: Double?, paradas: Int, porStatus: List<StatusStat>, pessoaStatus: List<PersonStatus>, abertasParadas: List<StuckIssue>, unit: String, avisos: List<String>)`; `DrillRow(key: String, resumo: String, responsavel: String?, status: String, detalhe: String)`. Padrão de período: últimos 90 dias (fuso Manaus). `avisos` traz `"status sem mapeamento: X"` quando `UnmappedStatusException`. Filtro: só entram issues com **algum intervalo** de responsável em `Visibility.visibleUserIds`; `pessoa=` fora do visível → 403.

- [ ] **Passo 1: Testes que falham** (contexto com banco da Tarefa 0B; `RoleDirectory` de teste em memória: `ana` e `bruno` USUARIO do squad Alfa, `lider.alfa` SQUAD_LEADER do Alfa, `carla` USUARIO do squad Beta, `coord.um` COORDENADOR, `gerente.exemplo` GERENTE; autenticador de teste que devolve `principalFor(id, diretório, hoje)`; dados do cenário TG-1 mais um segundo squad):
  - `meDevolveOsPapeisDoDiretorio`: `GET /api/me` para `ana` → `roles=["USUARIO"]`; papel encerrado ontem → 403 (principal sem papel).
  - `parametroSquadVemDoDiretorio`: `squad=Alfa` filtra pelas issues cujos assignees são os membros do squad Alfa NA DATA (via `RoleDirectory.membersOf`), nunca por lista digitada; squad inexistente → 400.
  - `usuarioVeSoOProprio`: `USUARIO` `ana` recebe `analisadas` contando só tasks em que ela teve posse; `pessoaStatus` só tem linhas dela.
  - `squadLeaderVeSeuSquad`: `SQUAD_LEADER` do squad A não vê tasks só do squad B.
  - `gerenteVeTodos`.
  - `pessoaDeOutroDoUsuarioComumRetorna403`.
  - `unidadePadraoEUtil`: sem `unit` o DTO tem `unit="util"`; com `unit=corrido` os números são os dos corridos.
  - `periodoPadraoNoventaDias`: sem `de/ate` considera concluídas nos 90 dias até hoje (relógio injetável).
  - `drillTemOMesmoTamanhoDoCard`: para `kind=analisadas` o tamanho da lista == `analisadas`; para `kind=paradas` == `paradas`; para `status:Em revisão` == `porStatus["Em revisão"].n`.
  - `statusNaoMapeadoVaiParaAvisos`: resposta 200 com `avisos` não vazio e números nulos/zerados, nunca 500.
  - `consultaEhRegistrada`: cada chamada (card, drill e `/api/me`) grava uma linha em `access_log` via `AccessLog.record` (usuário normalizado, rota, filtros saneados), inclusive as negadas com 403; chamadas públicas (`/api/health`, `/api/meta`, estático) NÃO gravam.
  - `issueDeAssigneeForaDoDiretorioSoParaCoordenacao`: uma task cujo assignee não está no diretório aparece para `coord.um` e `gerente.exemplo` e nunca para `ana` nem `lider.alfa`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TempoEmStatusRoutesTest*'` → FALHA.
- [ ] **Passo 3: Implementar** reaproveitando `Visibility` e o calculador; relógio e calendário injetados.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "API do card Tempo em status e detalhamento"`.

---

### Tarefa 6: Tela Tempo em status (React)

**Arquivos:**
- Criar: `app/frontend/src/screens/TempoEmStatus/{TempoEmStatus.tsx,api.ts,DrillDrawer.tsx}`; rota no `nav.ts`
- Teste: `TempoEmStatus.test.tsx`

**Interfaces:**
- Consome: `GET /api/cards/tempo-em-status`, `/api/drill/tempo-em-status` e `GET /api/me` (Tarefa 5); `t()`, `ThemeProvider`, `Shell` (Plano 01). O `Shell` passa a obter o papel efetivo de `/api/me` fora do modo desenvolvimento (hoje cai sempre em USUARIO), mantendo USUARIO como padrão enquanto carrega ou se falhar.
- Produz: `export function TempoEmStatus(): JSX.Element`; tipos `TempoEmStatusDto` e `DrillRow` espelhando o backend; `fetchTempoEmStatus(params): Promise<TempoEmStatusDto>`.
  Layout **denso** (plano visual, seção 5 e protótipo A): cabeçalho com filtros em chips e botão CSV; 3 KPIs (Tasks analisadas, Eficiência de fluxo, Paradas além do normal); barras mediana/P85 por status; mapa pessoa × status (≤ 3 d ✓, 3–6 d ●, > 6 d ▲); tabela de abertas paradas; alternância Dias úteis/Dias corridos que muda `unit` na URL.

- [ ] **Passo 1: Testes que falham** (Vitest + Testing Library, `fetch` simulado):
  - `rendersKpis`: mostra 236, 31% e 17 a partir do DTO simulado, com `role="button"` nos KPIs clicáveis.
  - `alternarUnidadeRecarrega`: clicar "Dias corridos" chama a API com `unit=corrido`.
  - `clicarNoKpiAbreAGaveta`: abre `DrillDrawer` e mostra tantas linhas quanto o KPI indica.
  - `avisoDeStatusNaoMapeado`: com `avisos` não vazio, o aviso aparece e os KPIs mostram "—".
  - `semPermissaoMostraMensagem`: resposta 403 exibe a mensagem de papel, não quebra.
  - `menuUsaPapelDeApiMeEmProducao`: com `/api/meta` `devMode:false` e `/api/me` `{roles:["COORDENADOR"]}` o menu mostra Auditoria e NÃO mostra Integridade; com `/api/me` falhando (403/500) o papel efetivo é USUARIO (menor privilégio) e o menu mostra só o que o USUARIO vê; o papel salvo em `localStorage` continua sem efeito em produção; em modo desenvolvimento o seletor de simulação segue valendo (Tarefa 9 do Plano 1).
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
