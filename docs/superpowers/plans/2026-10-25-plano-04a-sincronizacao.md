# Sincronização periódica com o Jira (Plano 4A) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Montar o `Syncer` em produção atrás de um **modo de sincronização desligado por padrão**: carga inicial, incremental, reconciliação diária e retenção agendadas no ciclo de vida do Ktor, com cada execução que fala com o Jira exigindo `JIRA_LIVE_ENABLED=true` **e** uma autorização explícita registrada. A execução deste plano é 100% offline (MockEngine e fixtures).

**Arquitetura (Architecture):** Um `SyncGate` puro decide se uma execução pode falar com o Jira (flag, modo, autorização por execução, suspensão por erro). O `SyncService` orquestra trava entre réplicas, registro em `sync_run`, cursor em `sync_state` e o `Syncer` (agora em fluxo, sem carregar tudo em memória). Um `Agendador` único por processo roda as tarefas devidas em sequência (sem sobreposição), com relógio e espera injetáveis, e é iniciado/cancelado pelos eventos do Ktor. Toda consulta sai de `CardQueryFactory` com a ficha `sincronizacao.card.yaml`.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kotlinx.coroutines (sem `kotlinx-coroutines-test`: o tempo é injetado), PostgreSQL + Flyway, Testcontainers 2.0.3, Ktor `MockEngine`; React + TypeScript (Vite, Vitest) só na tela de status.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.6 "Volume e atualidade", 5.3, 5.4, 10). Origem das pendências: `.superpowers/sdd/2026-10-24-plano-03-tempo-em-status/final-review.md` (M3, M4, M5, M7) e `progress.md` (Minor adiados da Tarefa 4 e 5). Consome as interfaces do Plano 3: `Syncer`, `IssueStore`/`PostgresIssueStore`, `ChangelogParser`, `SanitizadorRaw`, `Retention`, `JiraClientFactory`, `CardQueryFactory`, `AccessLog`, `testContextComBanco`.

## Restrições globais (Global Constraints)

- **Nenhum acesso ao Jira.** `JIRA_LIVE_ENABLED=false` é o padrão. Testes com "live ligado" usam SEMPRE o construtor `internal` de `JiraClientFactory(settings, http = { HttpClient(MockEngine) })` com `Settings(jiraLiveEnabled = true, jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", BEARER, null, "token-de-teste-123")))`; nunca o motor CIO. Hook `BLOQUEADO` = parar e relatar; arquivos que citem variáveis do Jira são escritos com Write/Edit, nunca com heredoc.
- **Modo de sincronização padrão `desligado`.** Mesmo com `JIRA_LIVE_ENABLED=true`, nenhuma execução sai sem autorização registrada (`SYNC_AUTORIZACAO_REF` + `SYNC_AUTORIZACAO_ATE`); no modo `manual` cada execução consome uma unidade de `SYNC_AUTORIZACAO_EXECUCOES`.
- Variáveis novas começam com `SYNC_` (nunca `JIRA_`, que o hook barra). Nenhum `Settings.copy(` em `src/main` (o teste `SomenteAFabricaCriaClientesJira` varre).
- JQL só por `JqlBuilder`/`CardQueryFactory`; nenhum texto JQL montado à mão nem guardado em banco ou log.
- `sync_run`, logs e respostas guardam só **códigos** (`HTTP_401`, `REDE`, `DADO_MALFORMADO`...), nunca texto de exceção, token, URL, JQL ou usernames.
- Fuso America/Manaus para dias; retenção `RETENTION_MONTHS` (padrão 24).
- Docker ativo (`sh .claude/hooks/ensure-docker.sh`); banco real por Testcontainers, nunca H2 ou mock.
- **Migração:** use o próximo número livre na hora da execução (`V4` se o Plano 4 ainda não tiver mesclado uma migração; senão `V5`). Nunca editar migração já mesclada.
- **Ordem com o Plano 4:** os planos são independentes. Se o Plano 4 já estiver na `main`, a Tarefa 6 aplica o filtro `removida_em IS NULL` também em `PostgresAuditStore`.
- Commits em Conventional Commits pt-BR (`feat(sync): ...`, `fix(sync): ...`, `docs(jira): ...`); comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Falha no meio de uma execução** (dado malformado na 3ª de 3 issues, queda, cancelamento): o cursor fica no `updated` da última issue gravada e a reexecução não duplica nada → `falhaNoMeioNaoAvancaCursorAlemDoCommit` (Tarefa 5).
2. **Reconciliação com resposta vazia ou pequena demais** (permissão perdida, ficha errada): nada é marcado como removido → `reconciliacaoAbortaQuandoJiraDevolvePoucoDemais` (Tarefa 6).
3. **401, 403 ou 429 esgotado:** a execução para, a instância fica suspensa e NÃO tenta de novo até nova autorização (regra do checklist da 1ª conexão) → `erro401SuspendeSemNovaTentativa` (Tarefa 5).
4. **Fuso e precisão de minuto do JQL:** issue atualizada no mesmo minuto do cursor não se perde → `cursorComSobreposicaoNaoPerdeMesmoMinuto` (Tarefa 5).
5. **Ficha de sincronização mais larga que a do card** (canceladas, Review, Epic na base): nada disso vaza para o card Tempo em status → `cardIgnoraTipoEStatusForaDaFicha` (Tarefa 8).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  sync/SyncSettings.kt, SyncGate.kt          # modo, autorização por execução, decisão
  sync/SyncStateStore.kt                     # cursor, sync_run, trava entre réplicas, chaves de usuário
  sync/ChavesDeUsuario.kt                    # JIRAUSER* -> username, coletado antes da sanitização
  sync/Syncer.kt, ChangelogParser.kt, IssueStore.kt   # fluxo, jira_id, remoção lógica
  sync/SyncService.kt                        # incremental, carga inicial, reconciliação
  sync/Agendador.kt                          # job único, ciclos, relógio injetável
  sync/SyncStatusRoutes.kt, SyncStatusDto.kt # GET /api/sync/status (GERENTE)
  jira/OrcamentoDeRequisicoes.kt             # teto de requisições por execução
  jql/JqlValues.kt, JqlBuilder.kt, CardSpec.kt, CardQueryFactory.kt
  db/Retention.kt, AppContext.kt, Application.kt, config/Settings.kt
app/backend/src/main/resources/db/migration/V4__sincronizacao.sql
app/config/cards/sincronizacao.card.yaml, app/config/jira_campos.exemplo.yaml
app/frontend/src/screens/Sincronizacao/{Sincronizacao.tsx,api.ts,Sincronizacao.test.tsx}
docs/jira/runbook-sincronizacao.md
```

---

### Tarefa 1: Modo de sincronização e autorização por execução

**Arquivos:** Criar `sync/SyncSettings.kt`, `sync/SyncGate.kt`; Modificar `config/Settings.kt` (campo `sync`), `app/.env.example` e `app/docker-compose.yml` (variáveis sem valor, comentadas em pt-BR), `.claude/hooks/test-guard.sh` (caso novo); Teste `sync/SyncGateTest.kt`, `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class SyncMode { DESLIGADO, MANUAL, PERIODICO }
  enum class SyncKind { INCREMENTAL, RECONCILIACAO, RETENCAO }      // carga inicial = INCREMENTAL sem cursor
  data class SyncAuthorization(val ref: String, val ate: Instant, val maxExecucoes: Int)
  data class SyncSettings(
      val mode: SyncMode = SyncMode.DESLIGADO, val instancias: List<String> = listOf("samsung"),
      val tipos: Set<SyncKind> = setOf(SyncKind.INCREMENTAL, SyncKind.RECONCILIACAO),
      val autorizacao: SyncAuthorization? = null, val intervalo: Duration = 10.minutes,
      val horaReconciliacao: LocalTime = LocalTime.of(3, 0), val horaRetencao: LocalTime = LocalTime.of(4, 0),
      val maxItens: Int = 50_000, val maxRequisicoes: Int = 2_000, val sobreposicao: Duration = 5.minutes,
      val fusoDoServidor: ZoneId = ZoneId.of("America/Manaus"), val tempoMaximo: Duration = 60.minutes,
      val minimoReconciliacao: Double = 0.5,
  )
  enum class MotivoNegacao { LIVE_DESLIGADO, MODO_DESLIGADO, SEM_AUTORIZACAO, AUTORIZACAO_EXPIRADA,
      AUTORIZACAO_LONGA_DEMAIS, EXECUCOES_ESGOTADAS, INSTANCIA_NAO_AUTORIZADA, TIPO_NAO_AUTORIZADO, SUSPENSA_POR_ERRO }
  sealed interface GateDecision { data class Liberada(val ref: String) : GateDecision; data class Negada(val motivo: MotivoNegacao) : GateDecision }
  class SyncGate(private val liveEnabled: Boolean, private val cfg: SyncSettings) {
      fun podeAgendar(agora: Instant): Boolean
      fun avaliar(kind: SyncKind, instance: String, agora: Instant, execucoesUsadas: Int, suspensa: Boolean): GateDecision
  }
  ```
  `Settings.sync: SyncSettings` lido em `fromEnv` (vazio = padrão, como as demais): `SYNC_MODE` (`desligado|manual|periodico`), `SYNC_INSTANCIAS` (lista separada por vírgula; ids de `INSTANCIAS_CONHECIDAS`), `SYNC_TIPOS` (`incremental,reconciliacao`), `SYNC_AUTORIZACAO_REF` (1 a 80 de `[A-Za-z0-9._:-]`), `SYNC_AUTORIZACAO_ATE` (ISO-8601 com offset), `SYNC_AUTORIZACAO_EXECUCOES` (1..20, padrão 1), `SYNC_INTERVALO_MIN` (5..1440), `SYNC_HORA_RECONCILIACAO` e `SYNC_HORA_RETENCAO` (`HH:mm`), `SYNC_MAX_ITENS` (1..200000), `SYNC_MAX_REQUISICOES` (1..20000), `SYNC_SOBREPOSICAO_MIN` (1..60), `SYNC_FUSO_SERVIDOR` (ZoneId), `SYNC_TEMPO_MAX_MIN` (1..240). REF sem ATE (ou o contrário) → `IllegalArgumentException` citando só o nome que falta.
  Regras do `avaliar` (nesta ordem): `kind == RETENCAO` → `IllegalArgumentException` (retenção não fala com o Jira e não passa pelo gate); `!liveEnabled` → `LIVE_DESLIGADO`; `DESLIGADO` → `MODO_DESLIGADO`; instância fora de `instancias` → `INSTANCIA_NAO_AUTORIZADA`; tipo fora de `tipos` → `TIPO_NAO_AUTORIZADO`; sem autorização → `SEM_AUTORIZACAO`; `agora >= ate` → `AUTORIZACAO_EXPIRADA`; `ate > agora + 7 dias` → `AUTORIZACAO_LONGA_DEMAIS`; `suspensa` → `SUSPENSA_POR_ERRO`; `MANUAL` e `execucoesUsadas >= maxExecucoes` → `EXECUCOES_ESGOTADAS`; senão `Liberada(ref)`. `PERIODICO` não conta execuções. `podeAgendar` = `avaliar(INCREMENTAL ou RECONCILIACAO, primeira instância, agora, 0, false) is Liberada` para algum tipo.

- [ ] **Passo 1: Testes que falham** (`agora = 2026-10-30T12:00:00Z`, ref `pergunta-2026-10-30-1`, `ate = 2026-10-30T18:00:00Z`):
  - `padraoEhDesligado`: `Settings.fromEnv(emptyMap()).sync.mode == DESLIGADO` e `autorizacao == null`; `SYNC_MODE=""` também.
  - `liveDesligadoNegaMesmoComAutorizacao`: live `false`, `MANUAL`, autorização válida → `Negada(LIVE_DESLIGADO)` e `podeAgendar == false`.
  - `modoDesligadoNega`, `semAutorizacaoNega`, `instanciaForaDaListaNega` (`"sidia"` com `SYNC_INSTANCIAS=samsung`), `tipoForaDaListaNega` (`SYNC_TIPOS=reconciliacao`, pedido `INCREMENTAL`), `suspensaNega`.
  - `autorizacaoExpiradaNega`: `agora = ate` → `AUTORIZACAO_EXPIRADA`; `agora = ate - 1 s` → `Liberada`.
  - `autorizacaoLongaDemaisNega`: `ate = agora + 7 d + 1 s` → `AUTORIZACAO_LONGA_DEMAIS`; `agora + 7 d` → `Liberada`.
  - `manualConsomeUmaExecucao`: `maxExecucoes=1`: usadas 0 → `Liberada("pergunta-2026-10-30-1")`; usadas 1 → `EXECUCOES_ESGOTADAS`.
  - `periodicoNaoContaExecucoes`: usadas 50 → `Liberada`.
  - `retencaoNaoPassaPeloGate`: `avaliar(RETENCAO, ...)` lança `IllegalArgumentException`.
  - `SettingsTest`: `syncLeTodasAsVariaveis`; `refSemAteFalha` (mensagem cita `SYNC_AUTORIZACAO_ATE` e não contém a ref); `syncModeInvalidoFalha`; `refComCaractereInvalidoFalha` (`"a b"`); `limitesForaDoIntervaloFalham` (`SYNC_INTERVALO_MIN=4`, `SYNC_MAX_ITENS=200001`).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*SyncGateTest*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar.** `test-guard.sh`: `SYNC_MODE=manual ./gradlew test` libera; os casos atuais continuam.
- [ ] **Passo 4: Rodar** os testes e `sh .claude/hooks/test-guard.sh` → PASSAM. **Passo 5: Commit** `feat(sync): modo de sincronização desligado por padrão e autorização por execução`.

---

### Tarefa 2: Migração e estado da sincronização

**Arquivos:** Criar `resources/db/migration/V4__sincronizacao.sql`, `sync/SyncStateStore.kt`; Teste `sync/SyncStateStoreTest.kt`.

**Interfaces:**
- Migração: `sync_state` ganha `ultima_reconciliacao timestamptz`, `suspensa_codigo text`, `suspensa_ref text`; nova `sync_run(id identity, instance text NOT NULL, kind text NOT NULL CHECK (kind IN ('INCREMENTAL','RECONCILIACAO','RETENCAO')), started_at timestamptz NOT NULL, finished_at timestamptz, status text NOT NULL CHECK (status IN ('EM_ANDAMENTO','OK','FALHA','CANCELADA')), autorizacao_ref text, issues int NOT NULL DEFAULT 0, eventos int NOT NULL DEFAULT 0, requisicoes int NOT NULL DEFAULT 0, incompletas int NOT NULL DEFAULT 0, usuarios_nao_resolvidos int NOT NULL DEFAULT 0, removidas int NOT NULL DEFAULT 0, codigo_erro text, http_status int)` com índice `(instance, kind, started_at DESC)`; `issue` ganha `jira_id bigint` e `removida_em timestamptz`, com índice único parcial `(instance, jira_id) WHERE jira_id IS NOT NULL`; nova `jira_user_key(instance text, user_key text, username text NOT NULL, visto_em timestamptz NOT NULL, PRIMARY KEY (instance, user_key))`.
- Produz:
  ```kotlin
  enum class RunStatus { EM_ANDAMENTO, OK, FALHA, CANCELADA }
  data class RunResumo(val issues: Int = 0, val eventos: Int = 0, val requisicoes: Int = 0, val incompletas: Int = 0,
                       val usuariosNaoResolvidos: Int = 0, val removidas: Int = 0)
  data class RegistroDeExecucao(val id: Long, val kind: SyncKind, val status: RunStatus, val startedAt: Instant,
      val finishedAt: Instant?, val resumo: RunResumo, val codigoErro: String?, val httpStatus: Int?)
  interface RepositorioDeChaves { fun chavesDeUsuario(instance: String): Map<String, String>
      fun gravarChavesDeUsuario(instance: String, mapa: Map<String, String>, at: Instant) }
  interface SyncStateStore : RepositorioDeChaves {
      fun cursor(instance: String): Instant?
      fun avancarCursor(instance: String, novo: Instant)                  // monotônico (GREATEST): nunca recua
      fun iniciar(instance: String, kind: SyncKind, ref: String?, at: Instant): Long
      fun concluir(runId: Long, status: RunStatus, resumo: RunResumo, codigoErro: String?, httpStatus: Int?, at: Instant)
      fun execucoesUsadas(ref: String): Int
      fun falhasSeguidas(instance: String, kind: SyncKind): Int
      fun ultimas(instance: String, limite: Int = 20): List<RegistroDeExecucao>
      fun ultimaPorTipo(instance: String?): Map<SyncKind, RegistroDeExecucao>  // null = execuções sem instância (retenção)
      fun suspender(instance: String, ref: String, codigo: String)
      fun suspensa(instance: String, ref: String): String?                 // só vale para a MESMA ref
      fun marcarReconciliacao(instance: String, at: Instant); fun ultimaReconciliacao(instance: String): Instant?
      suspend fun <T> comTrava(instance: String, bloco: suspend () -> T): T?   // pg_try_advisory_lock numa conexão dedicada; null se ocupada
      fun fecharOrfas(at: Instant): Int                                    // EM_ANDAMENTO antigas viram FALHA, código INTERROMPIDA
  }
  class PostgresSyncStateStore(private val ds: DataSource) : SyncStateStore
  ```
  A retenção (instância nula) grava `instance = '-'`.

- [ ] **Passo 1: Testes que falham** (Testcontainers):
  - `migracaoAplicaEmBancoPopulado`: banco com V1..V3 e linhas em `issue`/`change_event`; após migrar, linhas preservadas, `jira_id` e `removida_em` nulos.
  - `cursorAusenteEhNulo`; `cursorAvancaENuncaRecua`: avançar `2026-10-30T12:20Z` e depois `12:10Z` → `12:20Z`.
  - `execucaoGuardaSoCodigos`: `concluir(FALHA, ..., "HTTP_503", 503)`; a tabela não tem coluna de texto livre além de `codigo_erro`/`autorizacao_ref`.
  - `execucoesUsadasContaPorRef` (2 com `ref-a`, 1 com `ref-b` → 2 e 1).
  - `falhasSeguidasZeraAposOk` (FALHA, FALHA → 2; OK; FALHA → 1).
  - `suspensaoValeSoParaAMesmaRef`: suspender com `ref-a` → `suspensa(ref-a) == "HTTP_401"`, `suspensa(ref-b) == null`.
  - `travaExclusivaEntreDuasConexoes`: com a trava tomada num bloco suspenso, uma segunda chamada devolve `null`; depois de liberada, obtém.
  - `orfasViramFalhaInterrompida`; `chavesDeUsuarioUpsert` (`JIRAUSER10` → `ana`, depois → `bruno`: vence o último, `visto_em` atualizado).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncStateStoreTest*'` → FALHA. **Passo 3: Implementar** (SQL parametrizado; transação por método). **Passo 4: Rodar** → PASSA e a suíte inteira (`./gradlew cleanTest test`, conferir os XML). **Passo 5: Commit** `feat(sync): migração e estado da sincronização (cursor, execuções, trava)`.

---

### Tarefa 3: Ficha de sincronização, JQL com hora, orçamento de requisições e carga do dicionário

**Arquivos:** Criar `app/config/cards/sincronizacao.card.yaml`, `jira/OrcamentoDeRequisicoes.kt`; Modificar `jql/JqlValues.kt`, `jql/JqlBuilder.kt`, `jql/CardSpec.kt`, `jql/CardQueryFactory.kt`, `jira/JiraClient.kt`, `jira/JiraModels.kt`, `app/config/jira_campos.exemplo.yaml` (campo `key`, TEXT), `AppContext.kt`, `src/test/.../TestContext.kt`, `TestContextComBanco.kt`; Teste `jql/JqlBuilderTest.kt`, `jql/CardQueryFactoryTest.kt`, `jira/JiraClientRobustezTest.kt`, `AppContextTest.kt`.

**Interfaces:**
- `JqlValues.jqlDateTime(at: Instant, zone: ZoneId): String` → `"aaaa-MM-dd HH:mm"` no fuso dado, minuto truncado para baixo; `JqlValues.issueKey(v: String): String` aceita `[A-Z][A-Z0-9_]{0,19}-[1-9][0-9]{0,8}`, senão `InvalidJqlValueException("chave", v)`.
- `JqlBuilder.dateTimeAtLeast(field: String, at: Instant, zone: ZoneId): JqlBuilder` (só DATE) → `updated >= "2026-10-30 08:05"`; `JqlBuilder.keysIn(keys: List<String>): JqlBuilder` → `key in ("TG-1", "TG-20")`, 1 a 100 chaves.
- `TIPOS_DE_PARAMETRO += "atualizadoDesde", "chaves"`; `CAMPO_DO_PARAMETRO["atualizadoDesde"] = "updated"`, `["chaves"] = "key"`; em `CardSpecs`, `TIPOS_DO_CAMPO` ganha `"atualizadoDesde" to setOf(DATE)` e `"chaves" to setOf(TEXT)` (sem isso o `ensaiar` lança `NoSuchElementException` na carga) e `validar`/`FichaYaml` aceitam `ordenarPorAtualizacao`; valor de `atualizadoDesde`: `data class AtualizadoDesde(val at: Instant, val fusoDoServidor: ZoneId)`; de `chaves`: `List<String>`. Ficha ganha `ordenarPorAtualizacao: Boolean = false` → `ORDER BY updated ASC, key ASC`.
- `CardQueryFactory.buildLeve(spec: CardSpec, params: Map<String, Any>): JqlQuery` → mesmo JQL, `fields = ["key", "updated"]`, `expandChangelog = false`.
- `sincronizacao.card.yaml`: `instancia: samsung`; `filtrosFixos: {project: ["TG-Basic"], issuetype: ["Checklist", "Checklist TG", "Checklist Review", "Feature check", "TG Feature check", "Task"]}` (valor inicial; ver Decisões); **sem `filtrosExcluidos`** (canceladas entram: a auditoria precisa); `campos: [OS Version, SKU Type, Checklist Type]`; `parametros: [{nome: desde, tipo: atualizadoDesde}, {nome: chaves, tipo: chaves, obrigatorio: false}]`; `expandChangelog: true`; `ordenarPorAtualizacao: true`.
- `class OrcamentoDeRequisicoes(val max: Int) : AbstractCoroutineContextElement(Key) { fun consumir(); val usadas: Int; companion object Key : CoroutineContext.Key<OrcamentoDeRequisicoes> }` e `class OrcamentoEsgotadoException : RuntimeException("orçamento de requisições esgotado")`. `JiraClient` chama `currentCoroutineContext()[OrcamentoDeRequisicoes]?.consumir()` logo depois de `LiveGuard.check()` em TODA tentativa (inclusive retry). `JiraException` passa a `open class`; o teto de itens lança `TetoDeItensExcedidoException(limite: Int, total: Int) : JiraException` (o teste `maxItensEstouraComJiraException` continua passando).
- `AppContext` ganha `val campos: FieldDictionary` e `val fichas: Map<String, CardSpec>` (por `id`), lidos de `configDir/jira_campos.yaml` (fallback `.exemplo` em dev; em produção o real é obrigatório quando `settings.sync.mode != DESLIGADO`) e `configDir/cards/*.card.yaml`. `testContext`/`testContextComBanco` passam a carregar os mesmos modelos, e `testContextComBanco` ganha o parâmetro `jira: JiraClientFactory = JiraClientFactory(settings)` para os testes injetarem a fábrica `internal` com MockEngine.

- [ ] **Passo 1: Testes que falham:**
  - `dataHoraNoFusoDoServidor`: `2026-10-30T12:05:59Z` em America/Manaus → `"2026-10-30 08:05"`; em UTC → `"2026-10-30 12:05"`.
  - `dateTimeAtLeastSoEmDate` (em `summary` → `IllegalArgumentException`).
  - `chavesValidas`: `["TG-1","TG-20"]` → `key in ("TG-1", "TG-20")`; `"tg-1"`, `"TG-0"`, `"TG-1) or (1=1"` → `InvalidJqlValueException`; lista vazia e 101 chaves → `IllegalArgumentException`.
  - `fichaSincronizacaoGeraJqlEsperado` (snapshot): `project in ("TG-Basic") AND issuetype in ("Checklist", "Checklist TG", "Checklist Review", "Feature check", "TG Feature check", "Task") AND updated >= "2026-10-30 08:05" ORDER BY updated ASC, key ASC`; `expandChangelog == true`; com `customId: cf[12416]` no dicionário, `fields` contém `customfield_12416`.
  - `fichaComChavesAcrescentaKeyIn`; `buildLeveSoPedeKeyEUpdated` (`fields == ["key","updated"]`, `expandChangelog == false`).
  - `orcamentoBarraAQuartaRequisicao`: `withContext(OrcamentoDeRequisicoes(3))` e MockEngine que sempre devolve 503 → exatamente 3 requisições e `OrcamentoEsgotadoException`.
  - `tetoDeItensEhJiraException` (`TetoDeItensExcedidoException` é `JiraException`).
  - `appContextCarregaDicionarioEFichas` (dev sem arquivos reais → `fichas.keys` contém `tempo-em-status` e `sincronizacao`); `producaoComSyncLigadoExigeDicionarioReal`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*jql*' --tests '*JiraClientRobustez*' --tests '*AppContextTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): ficha de sincronização, JQL com hora, orçamento de requisições e dicionário no contexto`.

---

### Tarefa 4: `Syncer` em fluxo, chaves internas de usuário e issues incompletas

**Arquivos:** Criar `sync/ChavesDeUsuario.kt`, `src/test/resources/fixtures/dc/search-jirauser-keys.json`; Modificar `sync/Syncer.kt`, `sync/ChangelogParser.kt`, `sync/IssueStore.kt` (`upsert` grava `jira_id` e limpa `removida_em`); Teste `sync/ChavesDeUsuarioTest.kt`, `sync/ChangelogParserTest.kt`, `sync/SyncerTest.kt`.

**Interfaces:**
- Consome: `RepositorioDeChaves` (Tarefa 2), `OrcamentoDeRequisicoes` (Tarefa 3).
- Produz:
  ```kotlin
  object ChavesDeUsuario {
      fun ehChaveInterna(v: String): Boolean                              // ^JIRAUSER\d+$ (sem diferenciar maiúsculas)
      fun coletar(issue: JsonObject, historicos: List<JsonObject>): Map<String, String>  // key -> username normalizado
  }
  fun ChangelogParser.toEvents(issue: JsonObject, changelog: List<JsonObject>, chaves: Map<String, String> = emptyMap()): List<ChangeEventRecord>
  data class IssueGravada(val key: String, val updated: Instant?)
  data class SyncSummary(val issues: Int, val events: Int, val incompletas: Int = 0, val usuariosNaoResolvidos: Int = 0)
  class Syncer(client, store, instance, retentionMonths, clock, zone, campos,
               private val chaves: RepositorioDeChaves? = null, private val maxItens: Int = 50_000) {
      suspend fun run(query: JqlQuery, aoGravar: suspend (IssueGravada) -> Unit = {}): SyncSummary
  }
  ```
  `coletar` lê objetos com `name` e `key` em `fields.assignee`, `fields.reporter`, `fields.creator` e `histories[].author` do JSON **recebido**, antes de `SanitizadorRaw` (que apaga `key`). No `assignee` do changelog, `from`/`to` que é chave interna é traduzido pelo mapa (o da issue somado ao persistido); sem tradução, fica o valor original **sem normalizar** (`JIRAUSER99`, maiúsculo, nunca casa com username) e conta em `usuariosNaoResolvidos`. `run` usa `client.searchAll(query, maxItens = maxItens).collect { ... }` (sem `toList`): grava cada issue em sua transação e só então chama `aoGravar`. Issue sem `created` (ou sem `summary`) é gravada e conta em `incompletas` (continua fora de `timelines`, como hoje), sem exceção.

- [ ] **Passo 1: Fixture** com 3 issues: uma com `assignee {name: "Ana.Silva", key: "JIRAUSER10500"}` e changelog `to: "JIRAUSER10500"`; uma com `from: "JIRAUSER99"` sem objeto correspondente; uma sem `created`.
- [ ] **Passo 2: Testes que falham:**
  - `assigneeComChaveInternaViraUsername`: evento `to == "ana.silva"` e posse da linha do tempo com `ana.silva`.
  - `chaveInternaSemMapaFicaMarcadaEContada`: `from == "JIRAUSER99"` e `usuariosNaoResolvidos == 1`.
  - `mapaVemDoJsonAntesDaSanitizacao`: depois do `run`, `issue.raw` não tem `key` de usuário e `chavesDeUsuario("samsung")["JIRAUSER10500"] == "ana.silva"`.
  - `mapaPersistidoResolveExecucaoSeguinte`: 2ª execução sem o objeto na issue ainda traduz `JIRAUSER10500`.
  - `processaEmFluxo`: 3 páginas de 2 issues (`pageSize=2`); o registro de ordem mostra a gravação da 1ª issue ANTES da requisição da 2ª página; `aoGravar` recebe as chaves na ordem.
  - `issueSemCreatedContaComoIncompleta` (`incompletas == 1`, sem exceção).
  - `jiraIdGravadoEReentradaLimpaRemovida` (linha com `removida_em` preenchido volta a nulo no upsert).
  - Os testes atuais (`syncerEhIdempotente`, `liveDesligadoNaoFazRequisicao`, `changelogTruncadoBuscaOCompleto`, `naoReinsereEventoMaisAntigoQueOCorteDeRetencao`) continuam.
- [ ] **Passo 3: Rodar** `./gradlew test --tests '*SyncerTest*' --tests '*ChangelogParserTest*' --tests '*ChavesDeUsuarioTest*'` → FALHA. **Passo 4: Implementar.** **Passo 5: Rodar** → PASSA. **Passo 6: Commit** `feat(sync): syncer em fluxo, tradução de chaves JIRAUSER e contagem de incompletas`.

---

### Tarefa 5: `SyncService` — carga inicial e incremental com cursor, retomada e registro

**Arquivos:** Criar `sync/SyncService.kt`; Teste `sync/SyncServiceTest.kt` (Testcontainers; TODO teste monta `testContextComBanco(..., jira = JiraClientFactory(settingsLive, http = { HttpClient(MockEngine) }))` com o construtor `internal`, e um teste `provedorRealNuncaEhChamado` afirma que o provedor padrão (CIO) não é invocado: a varredura `SomenteAFabricaCriaClientesJira` só olha `src/main`).

**Interfaces:**
- Consome: `SyncGate` (T1), `SyncStateStore` (T2), `AppContext.fichas`/`campos`, `CardQueryFactory`, `OrcamentoDeRequisicoes` (T3), `Syncer` (T4), `Retention.limite`.
- Produz:
  ```kotlin
  sealed interface ResultadoExecucao {
      data class Executada(val runId: Long, val status: RunStatus, val resumo: RunResumo, val codigoErro: String?) : ResultadoExecucao
      data class Negada(val motivo: MotivoNegacao) : ResultadoExecucao
      data object Ocupada : ResultadoExecucao
  }
  class SyncService(private val ctx: AppContext, private val store: PostgresIssueStore,
                    private val estado: SyncStateStore, private val gate: SyncGate) {
      suspend fun incremental(instance: String): ResultadoExecucao
      suspend fun reconciliar(instance: String): ResultadoExecucao       // Tarefa 6
      fun limiteDeRetencao(): Instant                                    // Retention.limite(hoje Manaus, retentionMonths)
  }
  ```
  Fluxo de `incremental`: gate (`execucoesUsadas(ref)`, `suspensa(instance, ref) != null`) → `Negada` **sem requisição e sem linha em `sync_run`**; `comTrava` (`null` → `Ocupada`); `iniciar`; `desde = max((cursor ?: limiteDeRetencao()) - sobreposicao, limiteDeRetencao())`; query = `CardQueryFactory(ctx.campos).build(ctx.fichas["sincronizacao"], mapOf("desde" to AtualizadoDesde(desde, cfg.fusoDoServidor)))`; `withTimeout(tempoMaximo) { withContext(OrcamentoDeRequisicoes(maxRequisicoes)) { syncer.run(query) { estado.avancarCursor(instance, it.updated) } } }`; `concluir`. Issue gravada com `updated` nulo não move o cursor (já conta em `incompletas`). Códigos de erro: `JiraException` 401/403/429 → `HTTP_401`/`HTTP_403`/`HTTP_429` **e** `suspender`; 5xx → `HTTP_5XX`; status 0 → `REDE`; `TetoDeItensExcedidoException` → `TETO_ITENS`; `OrcamentoEsgotadoException` → `ORCAMENTO_REQUISICOES`; timeout → `TEMPO_ESGOTADO`; dado malformado → `DADO_MALFORMADO`; outro → `ERRO_INTERNO`; 3 `FALHA` seguidas do mesmo tipo → `suspender(..., "FALHAS_SEGUIDAS")`. Cancelamento → `CANCELADA` (gravado em `NonCancellable`) e relança. Nenhum texto de exceção é gravado nem logado.

- [ ] **Passo 1: Testes que falham** (hoje `2026-10-30` em Manaus, `RETENTION_MONTHS=24`, sobreposição 5 min, fuso do servidor Manaus; o MockEngine registra o corpo de cada requisição):
  - `gateFechadoNaoFazRequisicaoNemRegistra`: fábrica com live `false` → `Negada(LIVE_DESLIGADO)`, 0 requisições, `sync_run` vazio.
  - `cargaInicialUsaLimiteDeRetencao`: sem cursor → JQL enviado contém `updated >= "2024-10-30 00:00"`.
  - `incrementalUsaCursorMenosSobreposicao`: cursor `2026-10-30T12:05:00Z` → `updated >= "2026-10-30 08:00"`.
  - `cursorAvancaParaOMaiorUpdatedGravado`: issues com `updated` `12:10Z` e `12:20Z` → cursor `12:20Z`, `Executada(OK, RunResumo(issues = 2, ...))`; `semIssuesNaoMexeNoCursor`.
  - `falhaNoMeioNaoAvancaCursorAlemDoCommit`: 3 issues, a 3ª com data malformada → `FALHA`/`DADO_MALFORMADO`, cursor = `updated` da 2ª; reexecução com a fixture corrigida → `change_event` sem duplicatas e cursor = 3ª.
  - `cursorComSobreposicaoNaoPerdeMesmoMinuto`: A (`12:05:10Z`) gravada; B (`12:05:40Z`) só aparece na execução seguinte, cujo JQL tem `>= "2026-10-30 08:00"` → B gravada.
  - `manualPermiteUmaExecucaoPorAutorizacao`: `maxExecucoes=1` → 1ª `Executada`, 2ª `Negada(EXECUCOES_ESGOTADAS)` sem requisição; outra ref → liberada.
  - `orcamentoEsgotadoViraFalhaComCodigo`: `maxRequisicoes=1` com 2 páginas → `ORCAMENTO_REQUISICOES`, cursor na última issue da 1ª página.
  - `erro401SuspendeSemNovaTentativa`: 401 → `FALHA`/`HTTP_401`, exatamente 1 requisição; chamada seguinte → `Negada(SUSPENSA_POR_ERRO)` sem requisição; nova ref libera. Mesmo para 403 e para 429 após o retry esgotado.
  - `tresFalhasSeguidasSuspendem` (503 em 3 execuções → a 4ª é `SUSPENSA_POR_ERRO`).
  - `duasChamadasConcorrentesNaoExecutamJuntas`: trava ocupada → `Ocupada`, sem requisição extra.
  - `canceladaRegistraCancelada` (cancelar durante a 2ª página).
  - `registroNaoGuardaTextoDeErro`: `JiraException` com mensagem `segredo-xyz` → nenhuma coluna de `sync_run` contém `segredo-xyz`.
  - `tempoMaximoViraFalha` (`tempoMaximo=1 s`, MockEngine com `delay(2 s)` → `TEMPO_ESGOTADO`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncServiceTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): carga inicial e incremental com cursor, retomada e registro por códigos`.

---

### Tarefa 6: Reconciliação diária e remoção lógica

**Arquivos:** Modificar `sync/SyncService.kt` (`reconciliar`), `sync/IssueStore.kt` (`PostgresIssueStore` implementa `IssueIndex`; `removida_em IS NULL` em `timelines` e `temOsVersion`); se o Plano 4 já estiver na `main`, também `auditoria/AuditStore.kt`; Teste `sync/ReconciliacaoTest.kt`, `sync/SyncerTest.kt`.

**Interfaces:**
- Produz (interface nova; `IssueStore` não muda):
  ```kotlin
  data class IndiceIssue(val id: Long, val jiraId: Long?, val key: String, val updated: Instant?)
  interface IssueIndex {
      fun indice(instance: String, atualizadasDesde: Instant): List<IndiceIssue>   // ativas, updated >= corte
      fun marcarRemovidas(instance: String, ids: Set<Long>, at: Instant): Int
      fun renomear(instance: String, jiraId: Long, novaChave: String)
  }
  ```
  `reconciliar`: gate `RECONCILIACAO`; lista leve = `buildLeve(ficha, {desde: AtualizadoDesde(limiteDeRetencao(), fuso)})` (o universo é limitado pelo corte de retenção, então issues purgadas não voltam); universo local = `indice(instance, limiteDeRetencao())`. Guarda contra remoção em massa: se a lista do Jira tiver menos que `minimoReconciliacao × local` (com local ≥ 20) ou vier vazia com local > 0 → `FALHA`/`RECONCILIACAO_SUSPEITA` e nada é removido. Depois: mesma `jira_id` com chave nova → `renomear`; local ausente no Jira → `marcarRemovidas` (soft delete; eventos ficam até a retenção); `updated` do Jira maior que o local, ou chave ausente na base → rebusca com changelog por `chaves` em lotes de 100 (`Syncer.run`); `marcarReconciliacao`; resumo com `removidas`.

- [ ] **Passo 1: Testes que falham:**
  - `issueApagadaNoJiraViraRemovida`: local TG-1..TG-4, Jira devolve TG-1..TG-3 → TG-4 com `removida_em = agora`, fora de `timelines`, `change_event` mantido.
  - `issueQueVoltaLimpaRemocao` (próximo upsert limpa `removida_em`).
  - `issueMovidaManteHistoricoPeloJiraId`: `jira_id 10004` era TG-4 e volta como TG-40 → renomeada, mesmos eventos.
  - `divergenteEhRebuscadaComChangelog`: local TG-2 `updated 10:00Z`, Jira `11:00Z` → requisição com `key in ("TG-2")` e `expand=changelog`; evento novo gravado.
  - `reconciliacaoAbortaQuandoJiraDevolvePoucoDemais`: local 40, Jira 10 → `RECONCILIACAO_SUSPEITA`, 0 removidas; Jira vazio com local 3 → idem.
  - `reconciliacaoNaoReinsereIssuePurgada`: issue resolvida e atualizada em 2024-01 e purgada; a consulta leve tem `updated >= "2024-10-30 00:00"` e a issue não volta.
  - `foraDoUniversoLocalNaoEhRemovida`: issue aberta com `updated` 2023 (antes do corte) não é marcada.
  - `timelinesIgnoraRemovidas`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ReconciliacaoTest*' --tests '*SyncerTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(sync): reconciliação diária com remoção lógica e guarda contra remoção em massa`.

---

### Tarefa 7: Agendador no ciclo de vida do Ktor e retenção agendada

**Arquivos:** Criar `sync/Agendador.kt`; Modificar `Application.kt` (`module(..., agendador: Agendador? = null)`; `main` usa `montarAgendador(context)`), `db/Retention.kt` (KDoc e escopo); Teste `sync/AgendadorTest.kt`, `db/RetentionTest.kt` (o caso que afirma que `issue` e `access_log` nunca são tocados muda).

**Interfaces:**
- Produz:
  ```kotlin
  data class TarefaAgendada(val kind: SyncKind, val instance: String?, val devida: (agora: Instant, ultima: RegistroDeExecucao?) -> Boolean,
                            val executar: suspend () -> Unit)
  object Agenda {
      fun incrementalDevida(agora: Instant, ultima: RegistroDeExecucao?, intervalo: Duration): Boolean
      fun diariaDevida(agora: Instant, ultima: RegistroDeExecucao?, hora: LocalTime, zone: ZoneId): Boolean
  }
  class Agendador(private val tarefas: List<TarefaAgendada>, private val estado: SyncStateStore, private val clock: Clock,
                  private val passo: Duration = 1.minutes, private val dormir: suspend (Duration) -> Unit = { delay(it) }) {
      val tipos: List<SyncKind>
      fun iniciar(scope: CoroutineScope): Job          // um único Job; segunda chamada → IllegalStateException
      suspend fun ciclo()                              // tarefas devidas, em sequência; falha de uma não para as outras
      suspend fun parar()                              // cancelAndJoin
  }
  fun montarAgendador(ctx: AppContext): Agendador?    // null sem dataSource; RETENCAO sempre; INCREMENTAL/RECONCILIACAO por instância só se SyncGate.podeAgendar(agora)
  ```
  `Retention.purge` passa a apagar também: `issue` com `resolved < limite` **e** `updated < limite` (eventos em cascata; abertas nunca), `access_log` com `at < limite`, `sync_run` com `started_at < limite`. Cada purga gera um `sync_run` `RETENCAO` (instância `-`) com `removidas`. No `module`: `ApplicationStarted` → `estado.fecharOrfas(agora)` e `agendador.iniciar(application)`; `ApplicationStopping` → `parar()`.

- [ ] **Passo 1: Testes que falham:**
  - `incrementalDevidaACadaIntervalo`: última `12:00Z`, 10 min: `12:09:59Z` → falso, `12:10Z` → verdadeiro; nunca executada → verdadeiro.
  - `diariaUmaVezPorDiaDeManausDepoisDaHora` (03:00 Manaus = 07:00Z): `2026-10-30T06:59Z` → falso; `07:00Z` → verdadeiro; executada `2026-10-30T07:01Z` → falso até `2026-10-31T07:00Z`; `2026-10-31T03:30Z` ainda é dia 30 em Manaus → falso.
  - `cicloNaoSobrepoe`: tarefa presa num `CompletableDeferred`; um segundo `ciclo()` concorrente não a executa (contador 1).
  - `umSoJob`; `pararCancelaEAguarda`; `falhaDeUmaTarefaNaoParaOAgendador`.
  - `relogioInjetavel`: `dormir` falso que avança um `Clock` mutável em 10 min → 3 voltas executam 3 incrementais sem tempo real.
  - `gateFechadoSoAgendaRetencao`: `montarAgendador` com live `false` (ou modo `DESLIGADO`) → `tipos == [RETENCAO]` e 0 requisições ao MockEngine.
  - `moduloSemAgendadorNaoIniciaNada` (testes de rota existentes seguem iguais).
  - `RetentionTest.retencaoApagaIssueResolvidaAntigaEAccessLog` (limite `2024-10-30`, Manaus): issue `resolved 2024-09-01`, `updated 2024-09-02` → apagada com seus eventos; issue aberta antiga → fica; resolvida antiga mas `updated 2025-01-10` → fica; `access_log` de `2024-10-29T12:00Z` → apagado; de `2024-10-30T04:00Z` (00:00 Manaus) → fica. `retencaoApagaSyncRunAntigo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AgendadorTest*' --tests '*RetentionTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): agendador único no ciclo do Ktor e retenção de issue, access_log e execuções`.

---

### Tarefa 8: Status do sincronizador, filtros de leitura e pendências do Plano 3

**Arquivos:** Criar `sync/SyncStatusRoutes.kt`, `sync/SyncStatusDto.kt`, `app/frontend/src/screens/Sincronizacao/{Sincronizacao.tsx,api.ts,Sincronizacao.test.tsx}`; Modificar `Application.kt`, `cards/TempoEmStatusRoutes.kt`, `cards/TempoEmStatusDto.kt`, `sync/IssueStore.kt`, `app/frontend/src/App.tsx`, `app/frontend/src/screens/TempoEmStatus/TempoEmStatus.tsx`, `app/frontend/src/i18n/{pt,en}.json`; Teste `sync/SyncStatusRoutesTest.kt`, `cards/TempoEmStatusRoutesTest.kt`, `TempoEmStatus.test.tsx`.

**Interfaces:**
- `GET /api/sync/status`: `requireRole(GERENTE)`; toda chamada (inclusive 403) grava `access_log`. `/api/meta` NÃO muda.
  ```kotlin
  @Serializable data class SyncStatusDto(val modo: String, val liveEnabled: Boolean, val instancias: List<InstanciaStatusDto>, val retencao: ExecucaoDto?)
  @Serializable data class InstanciaStatusDto(val instancia: String, val gate: String /* "LIBERADA" ou MotivoNegacao */, val autorizacao: AutorizacaoDto?,
      val cursor: String?, val ultimaReconciliacao: String?, val suspensa: String?, val execucoes: List<ExecucaoDto>)
  @Serializable data class AutorizacaoDto(val ref: String, val ate: String, val usadas: Int, val max: Int)
  @Serializable data class ExecucaoDto(val tipo: String, val status: String, val inicio: String, val fim: String?, val issues: Int, val eventos: Int,
      val requisicoes: Int, val incompletas: Int, val usuariosNaoResolvidos: Int, val removidas: Int, val codigoErro: String?)
  ```
  Nunca: URL, token, usuário de serviço, JQL, mensagem de exceção.
- `data class FiltroDeFicha(val tipos: Set<String>, val statusExcluidos: Set<String>) { companion object { fun de(spec: CardSpec): FiltroDeFicha } }` e `IssueStore.timelines(..., filtro: FiltroDeFicha? = null)` (SQL: `issue_type = ANY(?)` e `status <> ALL(?)`, sem diferenciar maiúsculas). O projeto fica fora do filtro de leitura (na base é a chave, na ficha pode ser o nome); a instância já separa. A rota do card passa `FiltroDeFicha.de(ctx.fichas["tempo-em-status"])`.
- `AvisoDto(codigo, gravidade, mensagem, params: Map<String, String> = emptyMap())`: `STATUS_SEM_MAPEAMENTO` leva `params["status"]`; novo informativo `CONCLUSAO_DIVERGENTE` com `params["n"]` (status `DONE` sem `resolution`, ou `resolution` sem `DONE`; spec 4.2 manda para Qualidade dos dados). `openOnly=true` passa a ser `resolved IS NULL OR resolution IS NULL`; a rota tira as `DONE`+`resolution` e conta as divergentes.
- M5: o conjunto de candidatos do drill inclui `detalhe.pessoa`.
- Frontend: tela `/sincronizacao` (menu já existe, só GERENTE), densa, somente leitura: por instância, gate, autorização (ref, até, usadas/max), cursor, suspensão e as últimas execuções com `✓ OK`, `▲ FALHA`, `● CANCELADA` (texto traduzido do código). `TempoEmStatus.tsx` lê `params.status` em vez de extrair da mensagem.

- [ ] **Passo 1: Testes que falham:**
  - `statusSoParaGerente`: GERENTE 200; COORDENADOR, SQUAD_LEADER e USUARIO 403; todas gravam `access_log`.
  - `statusNaoVazaSegredos`: com token `token-de-teste-123`, host `jira.exemplo.invalid` e ref → corpo sem token nem host, com a ref e os códigos.
  - `statusMostraMotivoDoGate`: live `false` → `gate == "LIVE_DESLIGADO"`; `metaNaoMudou` (corpo de `/api/meta` igual ao atual).
  - `cardIgnoraTipoEStatusForaDaFicha`: na base, um Epic e uma task Cancelled de `ana` → nem card nem drill de `ana` os contam.
  - `drillDaPropriaPessoaSemIssuesDaListaVazia` (M5): `kind=pessoa:ana:status:Em revisão` sem issues → 200 `[]`.
  - `concluidaSemResolutionViraAvisoDivergente`: `DONE` sem `resolution` → `CONCLUSAO_DIVERGENTE` com `n="1"`, fora de analisadas e de paradas.
  - `avisoTrazParametroDoStatus`: `params["status"] == "Revisando"`.
  - Frontend: `avisoUsaParametroEmVezDaMensagem` (mensagem vazia, `params.status="Revisando"` → texto i18n com "Revisando"); `telaSincronizacaoMostraCodigos` (`FALHA HTTP_401` → texto traduzido e `▲`; `OK` → `✓`); `telaSincronizacao403MostraMensagemDePapel`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncStatusRoutesTest*' --tests '*TempoEmStatusRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA; `pnpm build` sem erros. **Passo 5: Commit** `feat(sync): status do sincronizador para o gerente, filtros da ficha na leitura e pendências do Plano 3`.

---

### Tarefa 9: Runbook de ligar a sincronização e documentação

**Arquivos:** Criar `docs/jira/runbook-sincronizacao.md`; Modificar `docs/jira/checklist-primeira-conexao.md` (seção "Depois dos cinco passos" aponta para o runbook), `docs/jira/matriz-compatibilidade.md` (linhas: `updated >= "aaaa-MM-dd HH:mm"` no fuso do usuário do token; chave `JIRAUSER*` no `from/to` do assignee; `key in (...)`), `app/README.md` (variáveis `SYNC_*` e o modo desligado), `.gitignore` (`**/config/status_map.yaml` e `**/config/jira_campos.yaml`, se ausentes), `HANDOFF.md`; Teste `jql/PreparacaoDocsTest.kt`.

**Conteúdo do runbook:** pré-requisitos (passos 1 a 5 do checklist concluídos e registrados: `timeZone` do `/myself` → `SYNC_FUSO_SERVIDOR`; `customId` no `jira_campos.yaml` real; `status_map.yaml` real); cada passo abaixo exige **autorização expressa** do usuário e é UMA execução: **Passo A** carga de teste com `SYNC_MODE=manual`, `SYNC_TIPOS=incremental`, `SYNC_AUTORIZACAO_EXECUCOES=1`, `SYNC_MAX_ITENS=50`, `SYNC_MAX_REQUISICOES=5`, `SYNC_AUTORIZACAO_ATE` em poucas horas; conferir `/api/sync/status` e o card; **Passo B** carga completa (limites padrão, nova ref); **Passo C** reconciliação (`SYNC_TIPOS=reconciliacao`, nova ref); **Passo D** periódico (`SYNC_MODE=periodico`) só depois de o usuário remover a regra temporária. Quando parar: qualquer `HTTP_401/403/429/5XX`, `RECONCILIACAO_SUSPEITA`, `usuariosNaoResolvidos > 0` sem explicação, `incompletas > 0`. Como desligar: `SYNC_MODE=desligado` e reiniciar. Nenhum host, token ou username no documento.

- [ ] **Passo 1: Testes que falham** (`PreparacaoDocsTest`): `runbookExigeAutorizacaoExpressaEmCadaPasso` (cada `### Passo` do runbook contém "autorização expressa"); `runbookCitaOChecklist` (link para `checklist-primeira-conexao.md`); `documentosDeJiraNaoTemHostRealTokenNemEmail` já cobre o arquivo novo (todo `.md` de `docs/jira`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PreparacaoDocsTest*'` → FALHA. **Passo 3: Escrever** os documentos e o HANDOFF (estado do Plano 4A, pendências que restam). **Passo 4: Rodar** → PASSA e suíte completa (backend e frontend). **Passo 5: Commit** `docs(jira): runbook de ligar a sincronização e variáveis SYNC_`.

---

## Autoavaliação

- **Cobertura:** spec 5.4 (incremental com marca d'água, reconciliação diária, retenção de 24 meses agendada), 4.6 (10 minutos e reconciliação diária, dezenas de milhares de issues: fluxo, teto de itens e de requisições), 5.3 (retry e `Retry-After` do cliente, mantidos), 3 e 10 (nenhuma requisição com a flag `false`; gate e autorização por execução). Final review: M3 (montagem), M4 (retenção de `issue` e `access_log` e agendamento), M5 (drill), M7 (ficha × vocabulário, via filtro de leitura); Minor da Tarefa 4 (JIRAUSER, memória, `aberta` × done+resolution, `summary/created` ausentes) e avisos por código com parâmetros. M1, M2, M6, I1 e I2 já corrigidos na PR #9 (M6 vira regra de commit).
- **Consistência:** `SyncKind`, `RunStatus`, `RunResumo` e `RegistroDeExecucao` (T1/T2) são os mesmos no serviço (T5/T6), no agendador (T7) e no DTO (T8); `AtualizadoDesde` e a ficha `sincronizacao` (T3) são a única fonte do JQL de T5 e T6.
- **Fora deste plano:** escrita no Jira; worklog (sem dado sincronizado); `jira_sidia` para usernames diferentes na Sidia; múltiplas instâncias além de `samsung` (a configuração aceita, os testes cobrem só uma).

## Decisões que precisam do usuário

1. **Semântica da autorização:** por execução no modo `manual` com contador (recomendada) ou janela de tempo no `periodico` desde já.
2. **Universo da ficha de sincronização:** tipos de issue incluídos (inicial: Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check, Task; Epic fora) e canceladas incluídas (recomendado, a auditoria precisa).
3. **Fuso do JQL:** `SYNC_FUSO_SERVIDOR` igual ao `timeZone` do usuário do token, lido no Passo 1 do checklist (padrão Manaus até lá).
4. **Retenção do `access_log`:** mesmo `RETENTION_MONTHS` (24) até o DPO decidir (recomendado) ou prazo próprio.
5. **Retenção de `issue`:** apagar a resolvida e sem atualização há mais de 24 meses (recomendado) ou só agregar.
6. **Suspensão em 401/403/429:** persistente até nova autorização (recomendado) ou só até reiniciar o app.
7. **Quem vê o status do sincronizador:** só o gerente, como no menu atual (recomendado), ou também coordenadores.
8. **Limiar da guarda contra remoção em massa:** 50% da base local (recomendado) ou outro valor.
