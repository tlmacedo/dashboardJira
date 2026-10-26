# Sincronização periódica com o Jira (Plano 4A) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Montar o `Syncer` em produção com **toda** a sincronização (carga inicial, incremental, reconciliação diária, retenção, agendador no ciclo de vida do Ktor e status), tendo como **única trava** de acesso ao Jira DC a flag `JIRA_LIVE_ENABLED` (o `LiveGuard` já existente): com `false` (padrão) nenhum caminho fala com o Jira (cliente, agendador, carga, reconciliação) e o status mostra "desligado pela flag"; só o usuário muda a flag para `true`, e então a sincronização periódica roda sozinha pelo agendador. A execução deste plano é 100% offline (MockEngine e fixtures), com prova de 0 requisições com a flag `false`, inclusive pelo agendador. Fecha com três complementos: **dados de demonstração** fictícios para ver o painel com números sem Jira, só em desenvolvimento (Tarefa 10), o **hook do Claude e o checklist alinhados à flag** (Tarefa 11) e os **vínculos entre issues** "Blocks / is blocked by" (`fields.issuelinks`) gravados em `issue_link`, que ligam cada Review (`Checklist Review`) à sua task para o card Task × Review do Plano 4 (Tarefa 12, executada por último). São 12 tarefas.

**Arquitetura (Architecture):** Um `SyncGate` puro decide se uma execução pode falar com o Jira (flag, instância configurada, suspensão por erro); o modo (`DESLIGADO`/`PERIODICO`) é **derivado da flag**, nunca de outra variável. O `SyncService` orquestra trava entre réplicas, registro em `sync_run`, cursor em `sync_state` e o `Syncer` (agora em fluxo, sem carregar tudo em memória). Um `Agendador` único por processo roda as tarefas devidas em sequência (sem sobreposição), com relógio e espera injetáveis, e é iniciado/cancelado pelos eventos do Ktor. Toda consulta sai de `CardQueryFactory` com a ficha `sincronizacao.card.yaml`.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kotlinx.coroutines (sem `kotlinx-coroutines-test`: o tempo é injetado), PostgreSQL + Flyway, Testcontainers 2.0.3, Ktor `MockEngine`; React + TypeScript (Vite, Vitest) só na tela de status.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-04a-sincronizacao.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.6 "Volume e atualidade", 5.3, 5.4, 10; onde ela fala em "autorização expressa por pesquisa", vale a regra 1 do `CLAUDE.md` reescrita em 25/10: a flag é a única trava). Origem das pendências: `.superpowers/sdd/2026-10-24-plano-03-tempo-em-status/final-review.md` (M3, M4, M5, M7) e `progress.md` (Minor adiados da Tarefa 4 e 5). Consome as interfaces do Plano 3: `Syncer`, `IssueStore`/`PostgresIssueStore`, `ChangelogParser`, `SanitizadorRaw`, `Retention`, `JiraClientFactory`, `CardQueryFactory`, `AccessLog`, `testContextComBanco`.

## Restrições globais (Global Constraints)

- **Nenhum acesso ao Jira.** `JIRA_LIVE_ENABLED=false` é o padrão. Testes com "live ligado" usam SEMPRE o construtor `internal` de `JiraClientFactory(settings, http = { HttpClient(MockEngine) })` com `Settings(jiraLiveEnabled = true, jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", BEARER, null, "token-de-teste-123")))`; nunca o motor CIO. Hook `BLOQUEADO` = parar e relatar; arquivos que citem variáveis do Jira são escritos com Write/Edit, nunca com heredoc.
- **A flag é a única trava.** Modo derivado de `JIRA_LIVE_ENABLED`: `false` (padrão, inclusive com o ambiente vazio) = `DESLIGADO`, sem nenhuma requisição por nenhum caminho; `true` = `PERIODICO`, o agendador roda sozinho. Não existe contador de autorização, tabela de autorizações nem modo manual; nenhuma variável `SYNC_*` liga a sincronização (elas só ajustam intervalos e limites ou restringem instâncias). Só o usuário muda a flag.
- **A mesma flag vale para pessoas e IAs (regra 1 do `CLAUDE.md`, reescrita em 25/10):** com `JIRA_LIVE_ENABLED=false` é proibido acessar o Jira por qualquer caminho (app, agendador, MCP da Atlassian, `curl`, navegador, script), de qualquer IA ou pessoa; com `true` o acesso fica liberado, sem autorização adicional por pesquisa (a regra antiga foi removida). Só o usuário muda a flag; nenhuma IA a liga, nem dentro de um comando. O primeiro ciclo real segue `docs/jira/checklist-primeira-conexao.md` como **procedimento cuidadoso recomendado** (ordem, limites e quando parar), não como pedido de autorização (Tarefas 9 e 11). Testes automáticos usam sempre MockEngine, mesmo com a flag `true` no ambiente.
- Com a flag `false`, a **retenção** continua agendada: ela só apaga linhas do banco local e nunca fala com o Jira.
- Variáveis novas começam com `SYNC_` ou `DASHBOARD_` (nunca `JIRA_`, que o hook barra). Nenhum `Settings.copy(` em `src/main` (o teste `SomenteAFabricaCriaClientesJira` varre).
- JQL só por `JqlBuilder`/`CardQueryFactory`; nenhum texto JQL montado à mão nem guardado em banco ou log.
- `sync_run`, logs e respostas guardam só **códigos** (`HTTP_401`, `REDE`, `DADO_MALFORMADO`...), nunca texto de exceção, token, URL, JQL ou usernames.
- Fuso America/Manaus para dias; retenção `RETENTION_MONTHS` (padrão 24).
- Docker ativo (`sh .claude/hooks/ensure-docker.sh`); banco real por Testcontainers, nunca H2 ou mock.
- **Migrações:** este plano tem duas. `V4__sincronizacao.sql` (Tarefa 2) e `V5__marcador_de_falhas.sql` (correção da Tarefa 2, já na branch de implementação) existem; a Tarefa 12 cria `V6__vinculos_entre_issues.sql`. **Regra que vale sempre:** confirmar a próxima numeração livre em `src/main/resources/db/migration` na hora da execução (se outro plano tiver mesclado uma migração antes, a da Tarefa 12 sobe para o número seguinte; os Planos 5, 6 e 8 já usam `V{n}` e seguem a mesma regra). Nunca editar migração já mesclada.
- **Ordem com o Plano 4:** o Plano 4 depende deste (usa `JqlValues.issueKey`, `AvisoDto.params`, `removida_em`, os dados de demonstração, o teste renomeado na Tarefa 11 e `issue_link`/`IssueLinkStore`/`ParesTaskReview` da Tarefa 12) e é executado **depois do 4A completo, inclusive a Tarefa 12**; é o Plano 4 que aplica `removida_em IS NULL` no `PostgresAuditStore`.
- Commits em Conventional Commits pt-BR (`feat(sync): ...`, `fix(sync): ...`, `docs(jira): ...`); comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Falha no meio de uma execução** (dado malformado na 3ª de 3 issues, queda, cancelamento): o cursor fica no `updated` da última issue gravada e a reexecução não duplica nada → `falhaNoMeioNaoAvancaCursorAlemDoCommit` (Tarefa 5).
2. **Reconciliação com resposta vazia ou pequena demais** (permissão perdida, ficha errada): se a remoção passaria de 50% da base local (ou o Jira vier vazio com base local), nada é marcado como removido → `reconciliacaoAbortaQuandoJiraDevolvePoucoDemais` (Tarefa 6).
3. **401, 403 ou 429 esgotado:** a execução para, a instância fica suspensa e NÃO tenta de novo até o usuário reiniciar o app (ou religar a flag, o que também exige reinício) → `erro401SuspendeSemNovaTentativa` (Tarefa 5) e `reinicioLimpaSuspensao` (Tarefa 7).
4. **Fuso e precisão de minuto do JQL:** issue atualizada no mesmo minuto do cursor não se perde → `cursorComSobreposicaoNaoPerdeMesmoMinuto` (Tarefa 5).
5. **Ficha de sincronização mais larga que a do card** (canceladas, Review, Epic na base): nada disso vaza para o card Tempo em status → `cardIgnoraTipoEStatusForaDaFicha` (Tarefa 8).
6. **Flag `false` = 0 requisições por qualquer caminho**, inclusive o agendador rodando um dia simulado: só a retenção executa, o provedor CIO nunca é invocado → `agendadorComFlagFalseUmDiaSimulado` (Tarefa 7), somando aos casos de `FlagDesligadaZeroRequisicoesTest` das Tarefas 1, 5 e 6.
7. **Dados de demonstração nunca em produção nem misturados com dados reais:** `DASHBOARD_DEMO_DATA=true` com `DASHBOARD_DEV_MODE=false` (ou com `JIRA_LIVE_ENABLED=true`) derruba a subida; o carregador não usa cliente Jira (0 requisições) e recusa banco com issues que não são de demonstração → `demoEmProducaoDerrubaSubida`, `demoComFlagLigadaDerrubaSubida`, `demoNaoFazRequisicao`, `demoRecusaBancoComDadosReais` (Tarefa 10).
8. **Hook do Claude travado por padrão:** com a flag ausente, `false` ou inválida no ambiente do hook, tudo o que o hook barra hoje continua barrado (inclusive ligar a flag dentro do comando); só `true` libera → casos `trava:` e `liberado:` de `test-guard.sh` (Tarefa 11).
9. **Vínculo que pertence às duas pontas** (o mesmo link chega pelo JSON da task e pelo da Review, em direções opostas; uma issue sincronizada sem o campo `issuelinks`; Review ligada a duas tasks): uma linha só por link, campo ausente nunca apaga vínculo, desempate pela ordem natural de chaves (TG-9 antes de TG-10) → `linkVistoPelasDuasPontasEhUmaLinha`, `campoAusenteNaoApagaVinculos`, `ambiguidadeUsaChaveNaturalMenor` (Tarefa 12).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  sync/SyncSettings.kt, SyncGate.kt          # intervalos e limites, modo derivado da flag, decisão
  sync/SyncStateStore.kt                     # cursor, sync_run, trava entre réplicas, chaves de usuário
  sync/ChavesDeUsuario.kt                    # JIRAUSER* -> username, coletado antes da sanitização
  sync/Syncer.kt, ChangelogParser.kt, IssueStore.kt   # fluxo, jira_id, remoção lógica
  sync/SyncService.kt                        # incremental, carga inicial, reconciliação
  sync/Agendador.kt                          # job único, ciclos, relógio injetável
  sync/SyncStatusRoutes.kt, SyncStatusDto.kt # GET /api/sync/status (GERENTE)
  jira/OrcamentoDeRequisicoes.kt             # teto de requisições por execução
  jql/JqlValues.kt, JqlBuilder.kt, CardSpec.kt, CardQueryFactory.kt
  db/Retention.kt, AppContext.kt, Application.kt, config/Settings.kt
app/backend/src/main/resources/db/migration/V4__sincronizacao.sql      # Tarefa 2 (V5__marcador_de_falhas.sql: correção da Tarefa 2)
app/backend/src/main/resources/db/migration/V6__vinculos_entre_issues.sql   # Tarefa 12 (número livre confirmado na execução)
app/backend/src/main/kotlin/br/sidia/painel/sync/{IssueLinks.kt,IssueLinkStore.kt,VinculosTaskReview.kt}  # Tarefa 12
app/config/vinculos.exemplo.yaml                                         # Tarefa 12: tipo de link e tipos Review
app/config/cards/sincronizacao.card.yaml, app/config/jira_campos.exemplo.yaml
app/frontend/src/screens/Sincronizacao/{Sincronizacao.tsx,api.ts,Sincronizacao.test.tsx}
docs/jira/runbook-sincronizacao.md
app/backend/src/main/kotlin/br/sidia/painel/demo/DadosDeDemonstracao.kt   # Tarefa 10: carga fictícia sem Jira
app/backend/src/main/resources/demo/{manifesto.yaml,samsung.json,sidia.json} # Tarefa 10: fixtures fictícias
.claude/hooks/{guard.sh,test-guard.sh,ensure-docker.sh}                   # Tarefa 11: hook consciente da flag
docs/jira/checklist-primeira-conexao.md                                  # Tarefa 11: confirma a flag em vez de pedir autorização
```

---

### Tarefa 1: Configuração da sincronização, modo derivado da flag e prova de 0 requisições

**Arquivos:** Criar `sync/SyncSettings.kt`, `sync/SyncGate.kt`; Modificar `config/Settings.kt` (campo `sync`), `app/.env.example` e `app/docker-compose.yml` (variáveis `SYNC_*` sem valor, comentadas em pt-BR, com a nota de que nenhuma delas liga a sincronização), `.claude/hooks/test-guard.sh` (caso novo); Teste `sync/SyncGateTest.kt`, `sync/FlagDesligadaZeroRequisicoesTest.kt` (criado aqui; as Tarefas 5, 6 e 7 acrescentam casos), `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class SyncMode { DESLIGADO, PERIODICO; companion object { fun de(liveEnabled: Boolean): SyncMode } }  // derivado SÓ da flag
  enum class SyncKind { INCREMENTAL, RECONCILIACAO, RETENCAO }      // carga inicial = INCREMENTAL sem cursor
  data class SyncSettings(
      val instancias: List<String> = listOf("samsung"), val intervalo: Duration = 10.minutes,
      val horaReconciliacao: LocalTime = LocalTime.of(3, 0), val horaRetencao: LocalTime = LocalTime.of(4, 0),
      val maxItens: Int = 50_000, val maxRequisicoes: Int = 2_000, val sobreposicao: Duration = 5.minutes,
      val fusoDoServidor: ZoneId = ZoneId.of("America/Manaus"), val tempoMaximo: Duration = 60.minutes,
      val maxFracaoRemocao: Double = 0.5,                           // reconciliação nunca remove mais que isso da base local
  )
  enum class MotivoNegacao { DESLIGADO_PELA_FLAG, INSTANCIA_NAO_CONFIGURADA, SUSPENSA_POR_ERRO }
  sealed interface GateDecision { data object Liberada : GateDecision; data class Negada(val motivo: MotivoNegacao) : GateDecision }
  class SyncGate(private val liveEnabled: Boolean, private val cfg: SyncSettings) {
      val modo: SyncMode                                            // SyncMode.de(liveEnabled)
      fun podeAgendar(): Boolean                                    // == liveEnabled
      fun avaliar(kind: SyncKind, instance: String, suspensa: Boolean): GateDecision
  }
  ```
  `SyncSettings` não tem campo de modo nem de autorização: o modo é `SyncMode.de(settings.jiraLiveEnabled)`. `Settings.sync: SyncSettings` lido em `fromEnv` (vazio = padrão, como as demais): `SYNC_INSTANCIAS` (lista separada por vírgula; ids de `INSTANCIAS_CONHECIDAS`; só restringe), `SYNC_INTERVALO_MIN` (5..1440), `SYNC_HORA_RECONCILIACAO` e `SYNC_HORA_RETENCAO` (`HH:mm`), `SYNC_MAX_ITENS` (1..200000), `SYNC_MAX_REQUISICOES` (1..20000), `SYNC_SOBREPOSICAO_MIN` (1..60), `SYNC_FUSO_SERVIDOR` (ZoneId; padrão Manaus até o `timeZone` do token ser lido), `SYNC_TEMPO_MAX_MIN` (1..240). Variável fora da lista (ex.: um `SYNC_MODE` esquecido) é ignorada, como as demais desconhecidas, e nunca liga nada.
  Relógio injetável: `SyncService` e `Agendador` recebem `java.time.Clock` (padrão `Clock.systemUTC()` só em `main`); nenhum `Instant.now()` direto em `sync/`.
  Regras do `avaliar` (nesta ordem): `kind == RETENCAO` → `IllegalArgumentException` (retenção não fala com o Jira e não passa pelo gate); `!liveEnabled` → `DESLIGADO_PELA_FLAG`; instância fora de `instancias` → `INSTANCIA_NAO_CONFIGURADA`; `suspensa` → `SUSPENSA_POR_ERRO`; senão `Liberada`.

- [ ] **Passo 1: Testes que falham:**
  - `padraoEhDesligado`: `Settings.fromEnv(emptyMap())` → `jiraLiveEnabled == false` e `SyncGate(...).modo == DESLIGADO`; `podeAgendar() == false`.
  - `flagLigadaEhPeriodico`: live `true` → `modo == PERIODICO`, `podeAgendar() == true`, `avaliar(INCREMENTAL, "samsung", false) == Liberada`.
  - `flagDesligadaNegaTodoTipo`: live `false` → `INCREMENTAL` e `RECONCILIACAO` → `Negada(DESLIGADO_PELA_FLAG)` para toda instância.
  - `instanciaForaDaListaNega` (`"sidia"` com `SYNC_INSTANCIAS=samsung`) e `suspensaNega` (live `true`).
  - `retencaoNaoPassaPeloGate`: `avaliar(RETENCAO, ...)` lança `IllegalArgumentException`.
  - `FlagDesligadaZeroRequisicoesTest` (casos desta tarefa): `nenhumaVariavelSyncLigaASincronizacao` (todas as `SYNC_*` com valores válidos, mais `SYNC_MODE=periodico` e `SYNC_AUTORIZACAO_REF=x`, com o ambiente sem a flag → `modo == DESLIGADO` e gate negado); `fabricaComFlagFalseNaoFazRequisicao` (`JiraClientFactory` `internal` com live `false` e MockEngine contador → `LiveGuard` barra e o contador fica em 0).
  - `SettingsTest`: `syncLeTodasAsVariaveis`; `limitesForaDoIntervaloFalham` (`SYNC_INTERVALO_MIN=4`, `SYNC_MAX_ITENS=200001`, mensagem cita só o nome da variável); `instanciaDesconhecidaFalha`; `horaInvalidaFalha` (`SYNC_HORA_RECONCILIACAO=25:00`).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*SyncGateTest*' --tests '*FlagDesligadaZeroRequisicoesTest*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar.** `test-guard.sh`: `SYNC_INTERVALO_MIN=10 ./gradlew test` libera; os casos atuais continuam.
- [ ] **Passo 4: Rodar** os testes e `sh .claude/hooks/test-guard.sh` → PASSAM. **Passo 5: Commit** `feat(sync): configuração da sincronização com modo derivado só da flag do Jira`.

---

### Tarefa 2: Migração e estado da sincronização

**Arquivos:** Criar `resources/db/migration/V4__sincronizacao.sql`, `sync/SyncStateStore.kt`; Teste `sync/SyncStateStoreTest.kt`.

**Interfaces:**
- Migração: `sync_state` ganha `ultima_reconciliacao timestamptz`, `suspensa_codigo text`, `suspensa_em timestamptz`; nova `sync_run(id identity, instance text NOT NULL, kind text NOT NULL CHECK (kind IN ('INCREMENTAL','RECONCILIACAO','RETENCAO')), started_at timestamptz NOT NULL, finished_at timestamptz, status text NOT NULL CHECK (status IN ('EM_ANDAMENTO','OK','FALHA','CANCELADA')), issues int NOT NULL DEFAULT 0, eventos int NOT NULL DEFAULT 0, requisicoes int NOT NULL DEFAULT 0, incompletas int NOT NULL DEFAULT 0, usuarios_nao_resolvidos int NOT NULL DEFAULT 0, removidas int NOT NULL DEFAULT 0, codigo_erro text, http_status int)` com índice `(instance, kind, started_at DESC)`; `issue` ganha `jira_id bigint` e `removida_em timestamptz`, com índice único parcial `(instance, jira_id) WHERE jira_id IS NOT NULL`; nova `jira_user_key(instance text, user_key text, username text NOT NULL, visto_em timestamptz NOT NULL, PRIMARY KEY (instance, user_key))`.
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
      fun iniciar(instance: String, kind: SyncKind, at: Instant): Long
      fun concluir(runId: Long, status: RunStatus, resumo: RunResumo, codigoErro: String?, httpStatus: Int?, at: Instant)
      fun falhasSeguidas(instance: String, kind: SyncKind): Int
      fun ultimas(instance: String, limite: Int = 20): List<RegistroDeExecucao>
      fun ultimaPorTipo(instance: String?): Map<SyncKind, RegistroDeExecucao>  // null = execuções sem instância (retenção)
      fun suspender(instance: String, codigo: String, at: Instant)
      fun suspensa(instance: String): String?                              // código da suspensão ou null
      fun limparSuspensoes(): Int                                          // chamado no início do app (reinício = religar)
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
  - `execucaoGuardaSoCodigos`: `concluir(FALHA, ..., "HTTP_503", 503)`; a tabela não tem coluna de texto livre além de `codigo_erro`.
  - `falhasSeguidasZeraAposOk` (FALHA, FALHA → 2; OK; FALHA → 1).
  - `suspensaoPersisteAteLimpar`: `suspender("samsung", "HTTP_401", at)` → `suspensa("samsung") == "HTTP_401"` (vale também lida por outra conexão, como outra réplica); `limparSuspensoes() == 1` → `suspensa == null`.
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
- `sincronizacao.card.yaml`: `instancia: samsung`; `filtrosFixos: {project: ["TG-Basic"], issuetype: ["Checklist", "Checklist TG", "Checklist Review", "Feature check", "TG Feature check", "Task"]}` (Epic fora; Decisão do usuário (25/10)); **sem `filtrosExcluidos`** (canceladas entram: a auditoria precisa); `campos: [OS Version, SKU Type, Checklist Type]`; `parametros: [{nome: desde, tipo: atualizadoDesde}, {nome: chaves, tipo: chaves, obrigatorio: false}]`; `expandChangelog: true`; `ordenarPorAtualizacao: true`.
- `class OrcamentoDeRequisicoes(val max: Int) : AbstractCoroutineContextElement(Key) { fun consumir(); val usadas: Int; companion object Key : CoroutineContext.Key<OrcamentoDeRequisicoes> }` e `class OrcamentoEsgotadoException : RuntimeException("orçamento de requisições esgotado")`. `JiraClient` chama `currentCoroutineContext()[OrcamentoDeRequisicoes]?.consumir()` logo depois de `LiveGuard.check()` em TODA tentativa (inclusive retry). `JiraException` passa a `open class`; o teto de itens lança `TetoDeItensExcedidoException(limite: Int, total: Int) : JiraException` (o teste `maxItensEstouraComJiraException` continua passando).
- `AppContext` ganha `val campos: FieldDictionary` e `val fichas: Map<String, CardSpec>` (por `id`), lidos de `configDir/jira_campos.yaml` (fallback `.exemplo` em dev; em produção o real é obrigatório quando `settings.jiraLiveEnabled == true`) e `configDir/cards/*.card.yaml`. `testContext`/`testContextComBanco` passam a carregar os mesmos modelos, e `testContextComBanco` ganha o parâmetro `jira: JiraClientFactory = JiraClientFactory(settings)` para os testes injetarem a fábrica `internal` com MockEngine.

- [ ] **Passo 1: Testes que falham:**
  - `dataHoraNoFusoDoServidor`: `2026-10-30T12:05:59Z` em America/Manaus → `"2026-10-30 08:05"`; em UTC → `"2026-10-30 12:05"`.
  - `dateTimeAtLeastSoEmDate` (em `summary` → `IllegalArgumentException`).
  - `chavesValidas`: `["TG-1","TG-20"]` → `key in ("TG-1", "TG-20")`; `"tg-1"`, `"TG-0"`, `"TG-1) or (1=1"` → `InvalidJqlValueException`; lista vazia e 101 chaves → `IllegalArgumentException`.
  - `fichaSincronizacaoGeraJqlEsperado` (snapshot): `project in ("TG-Basic") AND issuetype in ("Checklist", "Checklist TG", "Checklist Review", "Feature check", "TG Feature check", "Task") AND updated >= "2026-10-30 08:05" ORDER BY updated ASC, key ASC`; `expandChangelog == true`; com `customId: cf[12416]` no dicionário, `fields` contém `customfield_12416`.
  - `fichaComChavesAcrescentaKeyIn`; `buildLeveSoPedeKeyEUpdated` (`fields == ["key","updated"]`, `expandChangelog == false`).
  - `orcamentoBarraAQuartaRequisicao`: `withContext(OrcamentoDeRequisicoes(3))` e MockEngine que sempre devolve 503 → exatamente 3 requisições e `OrcamentoEsgotadoException`.
  - `tetoDeItensEhJiraException` (`TetoDeItensExcedidoException` é `JiraException`).
  - `appContextCarregaDicionarioEFichas` (dev sem arquivos reais → `fichas.keys` contém `tempo-em-status` e `sincronizacao`); `producaoComFlagLigadaExigeDicionarioReal`.
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

**Arquivos:** Criar `sync/SyncService.kt`; Teste `sync/SyncServiceTest.kt`, `sync/FlagDesligadaZeroRequisicoesTest.kt` (caso novo) (Testcontainers; TODO teste monta `testContextComBanco(..., jira = JiraClientFactory(settingsLive, http = { HttpClient(MockEngine) }))` com o construtor `internal`, e um teste `provedorRealNuncaEhChamado` afirma que o provedor padrão (CIO) não é invocado: a varredura `SomenteAFabricaCriaClientesJira` só olha `src/main`).

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
                    private val estado: SyncStateStore, private val gate: SyncGate, private val clock: Clock) {
      suspend fun incremental(instance: String): ResultadoExecucao
      suspend fun reconciliar(instance: String): ResultadoExecucao       // Tarefa 6
      fun limiteDeRetencao(): Instant                                    // Retention.limite(hoje Manaus, retentionMonths)
  }
  ```
  Fluxo de `incremental`: gate (`avaliar(INCREMENTAL, instance, suspensa(instance) != null)`) → `Negada` **sem requisição e sem linha em `sync_run`** (com a flag `false`, `Negada(DESLIGADO_PELA_FLAG)`); `comTrava` (`null` → `Ocupada`); `iniciar`; `desde = max((cursor ?: limiteDeRetencao()) - sobreposicao, limiteDeRetencao())`; query = `CardQueryFactory(ctx.campos).build(ctx.fichas["sincronizacao"], mapOf("desde" to AtualizadoDesde(desde, cfg.fusoDoServidor)))`; `withTimeout(tempoMaximo) { withContext(OrcamentoDeRequisicoes(maxRequisicoes)) { syncer.run(query) { estado.avancarCursor(instance, it.updated) } } }`; `concluir`. Issue gravada com `updated` nulo não move o cursor (já conta em `incompletas`). Códigos de erro: `JiraException` 401/403/429 → `HTTP_401`/`HTTP_403`/`HTTP_429` **e** `suspender` (até o reinício do app; ver Tarefa 7); 5xx → `HTTP_5XX`; status 0 → `REDE`; `TetoDeItensExcedidoException` → `TETO_ITENS`; `OrcamentoEsgotadoException` → `ORCAMENTO_REQUISICOES`; timeout → `TEMPO_ESGOTADO`; dado malformado → `DADO_MALFORMADO`; outro → `ERRO_INTERNO`; 3 `FALHA` seguidas do mesmo tipo → `suspender(instance, "FALHAS_SEGUIDAS", agora)`, com a mesma regra (só o reinício limpa). Cancelamento → `CANCELADA` (gravado em `NonCancellable`) e relança. Nenhum texto de exceção é gravado nem logado.

- [ ] **Passo 1: Testes que falham** (hoje `2026-10-30` em Manaus, `RETENTION_MONTHS=24`, sobreposição 5 min, fuso do servidor Manaus; o MockEngine registra o corpo de cada requisição):
  - `FlagDesligadaZeroRequisicoesTest.cargaInicialEIncrementalComFlagFalse`: settings e fábrica com live `false`, sem cursor (carga inicial) e com cursor (incremental) → `Negada(DESLIGADO_PELA_FLAG)`, 0 requisições no MockEngine, `sync_run` vazio, cursor intocado.
  - `cargaInicialUsaLimiteDeRetencao`: sem cursor → JQL enviado contém `updated >= "2024-10-30 00:00"`.
  - `incrementalUsaCursorMenosSobreposicao`: cursor `2026-10-30T12:05:00Z` → `updated >= "2026-10-30 08:00"`.
  - `cursorAvancaParaOMaiorUpdatedGravado`: issues com `updated` `12:10Z` e `12:20Z` → cursor `12:20Z`, `Executada(OK, RunResumo(issues = 2, ...))`; `semIssuesNaoMexeNoCursor`.
  - `falhaNoMeioNaoAvancaCursorAlemDoCommit`: 3 issues, a 3ª com data malformada → `FALHA`/`DADO_MALFORMADO`, cursor = `updated` da 2ª; reexecução com a fixture corrigida → `change_event` sem duplicatas e cursor = 3ª.
  - `cursorComSobreposicaoNaoPerdeMesmoMinuto`: A (`12:05:10Z`) gravada; B (`12:05:40Z`) só aparece na execução seguinte, cujo JQL tem `>= "2026-10-30 08:00"` → B gravada.
  - `flagLigadaExecutaSemContador`: live `true` → 5 chamadas seguidas de `incremental` são todas `Executada` (não há limite de execuções).
  - `orcamentoEsgotadoViraFalhaComCodigo`: `maxRequisicoes=1` com 2 páginas → `ORCAMENTO_REQUISICOES`, cursor na última issue da 1ª página.
  - `erro401SuspendeSemNovaTentativa`: 401 → `FALHA`/`HTTP_401`, exatamente 1 requisição; chamada seguinte → `Negada(SUSPENSA_POR_ERRO)` sem requisição; só depois de `estado.limparSuspensoes()` (o que o reinício faz) volta a liberar. Mesmo para 403 e para 429 após o retry esgotado.
  - `tresFalhasSeguidasSuspendem` (503 em 3 execuções → a 4ª é `SUSPENSA_POR_ERRO`).
  - `duasChamadasConcorrentesNaoExecutamJuntas`: trava ocupada → `Ocupada`, sem requisição extra.
  - `canceladaRegistraCancelada` (cancelar durante a 2ª página).
  - `registroNaoGuardaTextoDeErro`: `JiraException` com mensagem `segredo-xyz` → nenhuma coluna de `sync_run` contém `segredo-xyz`.
  - `tempoMaximoViraFalha` (`tempoMaximo=1 s`, MockEngine com `delay(2 s)` → `TEMPO_ESGOTADO`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncServiceTest*' --tests '*FlagDesligadaZeroRequisicoesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): carga inicial e incremental com cursor, retomada e registro por códigos`.

---

### Tarefa 6: Reconciliação diária e remoção lógica

**Arquivos:** Modificar `sync/SyncService.kt` (`reconciliar`), `sync/IssueStore.kt` (`PostgresIssueStore` implementa `IssueIndex`; `removida_em IS NULL` em `timelines` e `temOsVersion`); se o Plano 4 já estiver na `main`, também `auditoria/AuditStore.kt`; Teste `sync/ReconciliacaoTest.kt`, `sync/SyncerTest.kt`, `sync/FlagDesligadaZeroRequisicoesTest.kt` (caso novo).

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
  `reconciliar`: gate `RECONCILIACAO`; lista leve = `buildLeve(ficha, {desde: AtualizadoDesde(limiteDeRetencao(), fuso)})` (o universo é limitado pelo corte de retenção, então issues purgadas não voltam); universo local = `indice(instance, limiteDeRetencao())`. Guarda contra remoção em massa (sem piso mínimo de base): calcula primeiro as remoções; se `removidas > maxFracaoRemocao × local` (mais de 50% da base local) ou a lista do Jira vier vazia com local > 0 → `FALHA`/`RECONCILIACAO_SUSPEITA` e nada é removido, renomeado nem rebuscado. Depois: mesma `jira_id` com chave nova → `renomear`; local ausente no Jira → `marcarRemovidas` (soft delete; eventos ficam até a retenção); `updated` do Jira maior que o local, ou chave ausente na base → rebusca com changelog por `chaves` em lotes de 100 (`Syncer.run`); `marcarReconciliacao`; resumo com `removidas`.

- [ ] **Passo 1: Testes que falham:**
  - `issueApagadaNoJiraViraRemovida`: local TG-1..TG-4, Jira devolve TG-1..TG-3 → TG-4 com `removida_em = agora`, fora de `timelines`, `change_event` mantido.
  - `issueQueVoltaLimpaRemocao` (próximo upsert limpa `removida_em`).
  - `issueMovidaManteHistoricoPeloJiraId`: `jira_id 10004` era TG-4 e volta como TG-40 → renomeada, mesmos eventos.
  - `divergenteEhRebuscadaComChangelog`: local TG-2 `updated 10:00Z`, Jira `11:00Z` → requisição com `key in ("TG-2")` e `expand=changelog`; evento novo gravado.
  - `reconciliacaoAbortaQuandoJiraDevolvePoucoDemais`: local 40, Jira 19 (removeria 21 = 52,5%) → `RECONCILIACAO_SUSPEITA`, 0 removidas; local 3, Jira 1 (removeria 2) → idem; Jira vazio com local 3 → idem.
  - `remocaoDeExatamenteMetadePassa`: local 40, Jira 20 (remove 20 = 50%) → `OK`, `removidas == 20`.
  - `FlagDesligadaZeroRequisicoesTest.reconciliacaoComFlagFalse`: live `false` → `Negada(DESLIGADO_PELA_FLAG)`, 0 requisições, nenhuma `removida_em` preenchida.
  - `reconciliacaoNaoReinsereIssuePurgada`: issue resolvida e atualizada em 2024-01 e purgada; a consulta leve tem `updated >= "2024-10-30 00:00"` e a issue não volta.
  - `foraDoUniversoLocalNaoEhRemovida`: issue aberta com `updated` 2023 (antes do corte) não é marcada.
  - `timelinesIgnoraRemovidas`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ReconciliacaoTest*' --tests '*SyncerTest*' --tests '*FlagDesligadaZeroRequisicoesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(sync): reconciliação diária com remoção lógica e guarda contra remoção em massa`.

---

### Tarefa 7: Agendador no ciclo de vida do Ktor e retenção agendada

**Arquivos:** Criar `sync/Agendador.kt`; Modificar `Application.kt` (`module(..., agendador: Agendador? = null)`; `main` usa `montarAgendador(context)`), `db/Retention.kt` (KDoc e escopo); Teste `sync/AgendadorTest.kt`, `sync/FlagDesligadaZeroRequisicoesTest.kt` (caso novo), `db/RetentionTest.kt` (o caso que afirma que `issue` e `access_log` nunca são tocados muda).

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
  fun montarAgendador(ctx: AppContext, clock: Clock = Clock.systemUTC()): Agendador?
      // null sem dataSource; RETENCAO sempre (só banco local); INCREMENTAL/RECONCILIACAO por instância só se SyncGate.podeAgendar()
  ```
  Com a flag `false`, o agendador existe só com a retenção; mesmo que uma tarefa de Jira fosse montada por engano, o `SyncService` reavalia o gate a cada execução e o `LiveGuard` barra no cliente (três camadas).
  `Retention.purge` passa a apagar também: `issue` com `resolved < limite` **e** `updated < limite` (eventos em cascata; abertas nunca), `access_log` com `at < limite`, `sync_run` com `started_at < limite`. Cada purga gera um `sync_run` `RETENCAO` (instância `-`) com `removidas`. No `module`: `ApplicationStarted` → `estado.fecharOrfas(agora)`, `estado.limparSuspensoes()` (reiniciar ou religar a flag tira a suspensão por 401/403/429 ou por falhas seguidas) e `agendador.iniciar(application)`; `ApplicationStopping` → `parar()`.

- [ ] **Passo 1: Testes que falham:**
  - `incrementalDevidaACadaIntervalo`: última `12:00Z`, 10 min: `12:09:59Z` → falso, `12:10Z` → verdadeiro; nunca executada → verdadeiro.
  - `diariaUmaVezPorDiaDeManausDepoisDaHora` (03:00 Manaus = 07:00Z): `2026-10-30T06:59Z` → falso; `07:00Z` → verdadeiro; executada `2026-10-30T07:01Z` → falso até `2026-10-31T07:00Z`; `2026-10-31T03:30Z` ainda é dia 30 em Manaus → falso.
  - `cicloNaoSobrepoe`: tarefa presa num `CompletableDeferred`; um segundo `ciclo()` concorrente não a executa (contador 1).
  - `umSoJob`; `pararCancelaEAguarda`; `falhaDeUmaTarefaNaoParaOAgendador`.
  - `relogioInjetavel`: `dormir` falso que avança um `Clock` mutável em 10 min → 3 voltas executam 3 incrementais sem tempo real.
  - `gateFechadoSoAgendaRetencao`: `montarAgendador` com live `false` → `tipos == [RETENCAO]`; com live `true` → `[INCREMENTAL, RECONCILIACAO, RETENCAO]` para `samsung`.
  - `FlagDesligadaZeroRequisicoesTest.agendadorComFlagFalseUmDiaSimulado`: live `false`, fábrica `internal` com MockEngine contador e provedor CIO substituído por um que falha o teste se chamado; `dormir` falso avança o `Clock` em passos de 1 min por 24 h (cobre 03:00 e 04:00 de Manaus) → 0 requisições, provedor CIO nunca invocado, `sync_run` só com linhas `RETENCAO` (uma).
  - `reinicioLimpaSuspensao`: instância suspensa com `HTTP_401`; subir o `module` (evento `ApplicationStarted`) → `suspensa("samsung") == null`.
  - `moduloSemAgendadorNaoIniciaNada` (testes de rota existentes seguem iguais).
  - `RetentionTest.retencaoApagaIssueResolvidaAntigaEAccessLog` (limite `2024-10-30`, Manaus): issue `resolved 2024-09-01`, `updated 2024-09-02` → apagada com seus eventos; issue aberta antiga → fica; resolvida antiga mas `updated 2025-01-10` → fica; `access_log` de `2024-10-29T12:00Z` → apagado; de `2024-10-30T04:00Z` (00:00 Manaus) → fica. `retencaoApagaSyncRunAntigo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AgendadorTest*' --tests '*RetentionTest*' --tests '*FlagDesligadaZeroRequisicoesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(sync): agendador único no ciclo do Ktor e retenção de issue, access_log e execuções`.

---

### Tarefa 8: Status do sincronizador, filtros de leitura e pendências do Plano 3

**Arquivos:** Criar `sync/SyncStatusRoutes.kt`, `sync/SyncStatusDto.kt`, `app/frontend/src/screens/Sincronizacao/{Sincronizacao.tsx,api.ts,Sincronizacao.test.tsx}`; Modificar `Application.kt`, `cards/TempoEmStatusRoutes.kt`, `cards/TempoEmStatusDto.kt`, `sync/IssueStore.kt`, `app/frontend/src/App.tsx`, `app/frontend/src/screens/TempoEmStatus/TempoEmStatus.tsx`, `app/frontend/src/i18n/{pt,en}.json`; Teste `sync/SyncStatusRoutesTest.kt`, `cards/TempoEmStatusRoutesTest.kt`, `TempoEmStatus.test.tsx`.

**Interfaces:**
- `GET /api/sync/status`: `requireRole(GERENTE)`; toda chamada (inclusive 403) grava `access_log`. `/api/meta` NÃO muda.
  ```kotlin
  @Serializable data class SyncStatusDto(val modo: String /* "DESLIGADO_PELA_FLAG" ou "PERIODICO" */, val liveEnabled: Boolean,
      val instancias: List<InstanciaStatusDto>, val retencao: ExecucaoDto?)
  @Serializable data class InstanciaStatusDto(val instancia: String, val gate: String /* "LIBERADA" ou MotivoNegacao */,
      val cursor: String?, val ultimaReconciliacao: String?, val suspensa: String?, val execucoes: List<ExecucaoDto>)
  @Serializable data class ExecucaoDto(val tipo: String, val status: String, val inicio: String, val fim: String?, val issues: Int, val eventos: Int,
      val requisicoes: Int, val incompletas: Int, val usuariosNaoResolvidos: Int, val removidas: Int, val codigoErro: String?)
  ```
  Nunca: URL, token, usuário de serviço, JQL, mensagem de exceção.
- `data class FiltroDeFicha(val tipos: Set<String>, val statusExcluidos: Set<String>) { companion object { fun de(spec: CardSpec): FiltroDeFicha } }` e `IssueStore.timelines(..., filtro: FiltroDeFicha? = null)` (SQL: `issue_type = ANY(?)` e `status <> ALL(?)`, sem diferenciar maiúsculas). O projeto fica fora do filtro de leitura (na base é a chave, na ficha pode ser o nome); a instância já separa. A rota do card passa `FiltroDeFicha.de(ctx.fichas["tempo-em-status"])`.
- `AvisoDto(codigo, gravidade, mensagem, params: Map<String, String> = emptyMap())`: `STATUS_SEM_MAPEAMENTO` leva `params["status"]`; novo informativo `CONCLUSAO_DIVERGENTE` com `params["n"]` (status `DONE` sem `resolution`, ou `resolution` sem `DONE`; spec 4.2 manda para Qualidade dos dados). `openOnly=true` passa a ser `resolved IS NULL OR resolution IS NULL`; a rota tira as `DONE`+`resolution` e conta as divergentes.
- M5: o conjunto de candidatos do drill inclui `detalhe.pessoa`.
- Frontend: tela `/sincronizacao` (menu já existe, só GERENTE), densa, somente leitura: faixa no topo com o modo (`● Desligado pela flag` quando `modo == DESLIGADO_PELA_FLAG`, com a nota "só o usuário liga a flag; reinício necessário"); por instância, gate, cursor, suspensão (com a nota "reinicie o app para retomar") e as últimas execuções com `✓ OK`, `▲ FALHA`, `● CANCELADA` (texto traduzido do código). `TempoEmStatus.tsx` lê `params.status` em vez de extrair da mensagem.

- [ ] **Passo 1: Testes que falham:**
  - `statusSoParaGerente`: GERENTE 200; COORDENADOR, SQUAD_LEADER e USUARIO 403; todas gravam `access_log`.
  - `statusNaoVazaSegredos`: com token `token-de-teste-123` e host `jira.exemplo.invalid` → corpo sem token nem host, com os códigos.
  - `statusMostraDesligadoPelaFlag`: live `false` → `modo == "DESLIGADO_PELA_FLAG"` e `gate == "DESLIGADO_PELA_FLAG"` em toda instância; live `true` → `modo == "PERIODICO"`; `metaNaoMudou` (corpo de `/api/meta` igual ao atual).
  - `cardIgnoraTipoEStatusForaDaFicha`: na base, um Epic e uma task Cancelled de `ana` → nem card nem drill de `ana` os contam.
  - `drillDaPropriaPessoaSemIssuesDaListaVazia` (M5): `kind=pessoa:ana:status:Em revisão` sem issues → 200 `[]`.
  - `concluidaSemResolutionViraAvisoDivergente`: `DONE` sem `resolution` → `CONCLUSAO_DIVERGENTE` com `n="1"`, fora de analisadas e de paradas.
  - `avisoTrazParametroDoStatus`: `params["status"] == "Revisando"`.
  - Frontend: `avisoUsaParametroEmVezDaMensagem` (mensagem vazia, `params.status="Revisando"` → texto i18n com "Revisando"); `telaSincronizacaoMostraCodigos` (`FALHA HTTP_401` → texto traduzido e `▲`; `OK` → `✓`); `telaSincronizacaoMostraDesligadoPelaFlag` (`modo=DESLIGADO_PELA_FLAG` → "Desligado pela flag" com `●`, pt e en); `telaSincronizacao403MostraMensagemDePapel`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SyncStatusRoutesTest*' --tests '*TempoEmStatusRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA; `pnpm build` sem erros. **Passo 5: Commit** `feat(sync): status do sincronizador para o gerente, filtros da ficha na leitura e pendências do Plano 3`.

---

### Tarefa 9: Runbook de ligar a sincronização e documentação

**Arquivos:** Criar `docs/jira/runbook-sincronizacao.md`; Modificar `docs/jira/checklist-primeira-conexao.md` (só a seção "Depois dos cinco passos", que aponta para o runbook; a reescrita do checklist é da Tarefa 11), `docs/jira/matriz-compatibilidade.md` (linhas: `updated >= "aaaa-MM-dd HH:mm"` no fuso do usuário do token; chave `JIRAUSER*` no `from/to` do assignee; `key in (...)`), `app/README.md` (variáveis `SYNC_*`, que só ajustam limites; a flag como única chave de liga/desliga; a linha da tabela de flags "Só altere com autorização expressa" vira "Só o usuário liga; `false` proíbe qualquer acesso ao Jira"), `app/.env.example` (o comentário "Só altere com autorização expressa" vira "Só o usuário liga (regra 1 do CLAUDE.md)"), `app/docker-compose.yml` (o comentário "Para habilitar (com autorização)" vira "Para habilitar (depois que o usuário ligar a flag)"), `.gitignore` (`**/config/status_map.yaml` e `**/config/jira_campos.yaml`, se ausentes), `HANDOFF.md`; Teste `jql/PreparacaoDocsTest.kt`.

**Conteúdo do runbook** (títulos `### Etapa`, nunca `### Passo`, para não confundir com os passos do checklist):
- **Aviso no topo:** a flag `JIRA_LIVE_ENABLED` é a **única trava** de acesso ao Jira (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém (app, agendador, IA, pessoa, MCP, `curl`, script) acessa o Jira; com `true`, o app sincroniza sozinho e as ferramentas ficam liberadas. Só o **usuário** muda a flag; nenhuma IA a liga. O primeiro ciclo real segue `docs/jira/checklist-primeira-conexao.md` como procedimento cuidadoso recomendado.
- **Pré-requisitos:** passos 1 a 5 do checklist concluídos e registrados (`timeZone` do `/myself` → `SYNC_FUSO_SERVIDOR`; `customId` no `jira_campos.yaml` real; `status_map.yaml` real).
- **Etapa 1, primeiro ciclo (pequeno):** o usuário liga a flag com `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5`, reinicia; o agendador faz a carga inicial sozinho; conferir `/api/sync/status` e o card. **Etapa 2, limites padrão:** tirar os limites e reiniciar; carga completa e incrementais a cada `SYNC_INTERVALO_MIN`. **Etapa 3, reconciliação:** conferir a primeira reconciliação diária (03:00 de Manaus) no status.
- **Quando parar** (flag `false` e reiniciar): qualquer `HTTP_401/403/429/5XX`, `RECONCILIACAO_SUSPEITA`, `usuariosNaoResolvidos > 0` sem explicação, `incompletas > 0`. Suspensão: o app para sozinho após 401/403/429 (ou 3 falhas seguidas) e só retoma ao reiniciar.
- **Como desligar:** `JIRA_LIVE_ENABLED=false` e reiniciar (nenhuma outra variável liga ou desliga a sincronização). Nenhum host, token ou username no documento.

- [ ] **Passo 1: Testes que falham** (`PreparacaoDocsTest`): `runbookAvisaQueAFlagEhAUnicaTrava` (contém "única trava" e `JIRA_LIVE_ENABLED`; **não** contém "autorização expressa"); `runbookCitaOChecklist` (link para `checklist-primeira-conexao.md`); `runbookSoLigaPelaFlag` (contém `JIRA_LIVE_ENABLED`; não contém `SYNC_MODE` nem `SYNC_AUTORIZACAO`); `readmeSemRegraAntiga` (`app/README.md` e `app/.env.example` não contêm "autorização expressa"); `documentosDeJiraNaoTemHostRealTokenNemEmail` já cobre o arquivo novo (todo `.md` de `docs/jira`); `checklistETemTodasAsChamadas` e `cadaPassoDoChecklistExigeAutorizacaoExpressa` continuam passando **até a Tarefa 11**, que reescreve o checklist e troca esses dois testes.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PreparacaoDocsTest*'` → FALHA. **Passo 3: Escrever** os documentos e o HANDOFF (estado do Plano 4A, pendências que restam). **Passo 4: Rodar** → PASSA e suíte completa (backend e frontend). **Passo 5: Commit** `docs(jira): runbook de ligar a sincronização e variáveis SYNC_`.

---

### Tarefa 10: Dados de demonstração (painel com números sem Jira)

**Por quê:** hoje o painel sobe vazio (nenhuma issue no banco enquanto a flag está `false`), e o usuário não consegue ver o Tempo em status nem, depois do Plano 4, a Auditoria com números. Esta tarefa carrega, **na subida e sem tocar no Jira**, issues e changelog **fictícios**, só em desenvolvimento.

**Arquivos:** Criar `demo/DadosDeDemonstracao.kt`, `src/main/resources/demo/manifesto.yaml`, `src/main/resources/demo/samsung.json`, `src/main/resources/demo/sidia.json`; Modificar `config/Settings.kt` (`dashboardDemoData`), `Application.kt` (`main` chama `prepararDemonstracao`; texto da faixa), `app/docker-compose.yml` (`DASHBOARD_DEMO_DATA: ${DASHBOARD_DEMO_DATA:-false}`, comentado em pt-BR), `app/.env.example` (`DASHBOARD_DEMO_DATA=false`, com a nota "só com DASHBOARD_DEV_MODE=true e JIRA_LIVE_ENABLED=false"), `app/README.md` (seção "Ver o painel hoje em 3 comandos" e linha na tabela de flags), `docs/jira/runbook-sincronizacao.md` (pré-requisito: banco sem dados de demonstração), `.claude/hooks/test-guard.sh` (caso novo); Teste `demo/DadosDeDemonstracaoTest.kt`, `demo/FixturesDeDemoTest.kt`, `config/SettingsTest.kt`, `MetaTest.kt`.

**Interfaces:**
- Consome: `ChavesDeUsuario.coletar` e `ChangelogParser.toEvents(issue, changelog, chaves)` (T4), `PostgresIssueStore.upsert` (grava `jira_id` desde a T4), `FiltroDeFicha` e a rota do Tempo em status (T8), `testContextComBanco` (T3).
- Produz:
  ```kotlin
  // config/Settings.kt
  val dashboardDemoData: Boolean = false     // DASHBOARD_DEMO_DATA; "true"/"false" como as demais flags (vazio = padrão)
  // Settings.fromEnv falha (IllegalArgumentException citando só os NOMES das variáveis) quando:
  //   DASHBOARD_DEMO_DATA=true e DASHBOARD_DEV_MODE=false  -> "DASHBOARD_DEMO_DATA exige DASHBOARD_DEV_MODE=true"
  //   DASHBOARD_DEMO_DATA=true e JIRA_LIVE_ENABLED=true    -> "DASHBOARD_DEMO_DATA exige JIRA_LIVE_ENABLED=false"

  // demo/DadosDeDemonstracao.kt
  data class InstanciaDemo(val arquivo: String, val prefixo: String, val osVersionCustomId: String?)
  data class ManifestoDemo(val versao: Int, val ancora: LocalDate, val instancias: Map<String, InstanciaDemo>)
  data class ResumoDemo(val instancia: String, val issues: Int, val eventos: Int)
  class DemoRecusadaException(val codigo: String) : IllegalStateException("dados de demonstração recusados: $codigo")
  class DadosDeDemonstracao(private val ds: DataSource, private val store: PostgresIssueStore,
                            private val clock: Clock, private val zone: ZoneId, private val pasta: String = "demo") {
      fun carregar(): List<ResumoDemo>          // idempotente; uma transação por instância
      fun existemDados(): Boolean               // alguma issue com chave de prefixo de demonstração, em qualquer instância
      companion object { fun deslocamentoDias(ancora: LocalDate, hoje: LocalDate): Long }   // floor((hoje − ancora) / 7) × 7; pode ser negativo
  }
  fun prepararDemonstracao(ctx: AppContext)    // chamado em main logo depois de AppContext.load
  ```
- Regras:
  1. **Sem Jira, pelo caminho real de gravação:** cada issue do JSON passa por `ChavesDeUsuario.coletar` → `ChangelogParser.toEvents` → `store.upsert(issue, eventos, instancia, osVersionCustomId)`. A classe não recebe nem cita `JiraClient`, `JiraClientFactory` nem `HttpClient`.
  2. **Datas relativas a hoje, nunca no futuro:** as fixtures têm datas absolutas em torno de `ancora: 2026-10-30` (sexta). O carregador soma `deslocamentoDias(ancora, hoje de Manaus)` a todo `created`, `updated`, `resolved`, `duedate`, `histories[].created` e aos valores de `duedate` no changelog (`from`, `to`, `fromString`, `toString`). O deslocamento é em **semanas inteiras arredondadas para baixo** (pode ser negativo: em 25/10/2026 é −7), então a âncora deslocada cai sempre entre hoje − 6 dias e hoje, e o dia da semana é preservado. Todo **instante** das fixtures (`created`, `updated`, `resolved`, `histories[].created`) fica no máximo em `ancora − 1 dia` às 23:59 de Manaus; só `duedate` pode ser posterior. As datas ficam fixas durante a semana, mas o "agora" anda todo dia: por isso os casos sensíveis a limiar contado a partir de agora (7 dias sem atualização, parada há 10 dias úteis, "a fazer" há 30 dias, vencida) ficam a pelo menos 7 dias do limiar, e a lista onde caem não muda dentro da semana. Valor padrão recomendado, reversível.
  2a. **Vocabulário das fixtures:** todo status usado está em `app/config/status_map.exemplo.yaml` (por exemplo `To Do`, `In Progress`, `In Review`, `Blocked`, `Done`, `Closed`) e todo tipo está na ficha `sincronizacao.card.yaml`; as únicas exceções deliberadas são o `Epic` e o status `Cancelled`, que a ficha do Tempo em status exclui. Sem isso o card responde `STATUS_SEM_MAPEAMENTO` (bloqueante).
  3. **Idempotente por substituição:** por instância, numa transação, apaga `issue` com `instance = ?` e `key LIKE '<prefixo>-%'` (eventos em cascata) e grava de novo. Duas subidas seguidas deixam o banco igual. Prefixos: `DEMO` (instância `samsung`) e `DEMOCAT` (instância `sidia`).
  4. **Nunca mistura com dados reais (fail-closed):** se a instância tiver alguma issue cuja chave **não** tem o prefixo de demonstração, `carregar` lança `DemoRecusadaException("BANCO_COM_DADOS_REAIS")` antes de apagar qualquer linha, e a subida falha com a orientação "use um volume novo (`docker compose down -v`)". Com `JIRA_LIVE_ENABLED=true` e `existemDados() == true`, `prepararDemonstracao` derruba a subida com `BANCO_COM_DADOS_DE_DEMONSTRACAO` (nada é apagado automaticamente; o runbook manda usar um volume novo antes de ligar a flag). Valores padrão recomendados.
  5. **Defesa em profundidade:** `prepararDemonstracao` só chama `carregar` com `dashboardDevMode && dashboardDemoData && !jiraLiveEnabled` (o `Settings` já recusa as outras combinações). Log: só contagens por instância.
  6. **Faixa:** com dados de demonstração, `banner = "MODO DESENVOLVIMENTO: dados fictícios de demonstração"`; sem eles, `/api/meta` fica igual (o `metaNaoMudou` da T8 segue verde). Sem campo novo no `MetaResposta`.
- **Cenário das fixtures** (só nomes fictícios; pessoas = os `id` de `app/config/papeis.exemplo.yaml`, para a visibilidade por papel funcionar no modo dev; hosts só `jira.demo.invalid`; nenhum `emailAddress`, `displayName` real nem avatar). Instância `samsung`, projeto `TG-Basic`, cerca de 30 issues `DEMO-1..`, dois squads (Alfa: `dev.alfa1`, `dev.alfa2`, `lider.alfa`; Beta: `dev.beta1`, `lider.beta`), Reporter (PL) `pl.alfa`/`pl.beta` (fora do diretório, o que é válido), coordenação `coord.um`:
  - concluídas positivas (categoria done **e** `resolution`) no prazo, com atraso e com atraso justificado (`LATE_SENT`, `ONHOLDBY-PL`), em semanas diferentes e com dois valores de `OS Version`;
  - uma cancelada (`Won't Do`), uma `Closed` sem `resolution` (divergente), um `Epic` e uma task `Cancelled` (fora da ficha: o card não os conta);
  - abertas paradas: uma em `In Progress` há mais de 10 dias úteis, uma em `Blocked`, uma sem atualização há mais de 7 dias, uma sem responsável e uma com responsável fora do diretório (`fora.do.diretorio`, visível só para coordenação e gerente);
  - trocas de responsável: uma por usuário comum (não autorizado), uma pelo Squad Leader do squad (autorizado), um passa-bola (3 trocas a até 2 dias do vencimento) e uma volta a quem já teve;
  - trocas de vencimento: uma pelo próprio responsável, uma pelo Reporter, uma remoção, uma primeira definição; itens de `duedate` com `from` `aaaa-mm-dd` **e** `fromString` `aaaa-mm-dd 00:00:00.0` (os dois formatos que o Plano 4 lê);
  - labels de atraso: uma colocada depois do fechamento por não autorizado, uma pelo próprio responsável depois do fechamento, uma depois do vencimento e uma `LATE_SENT` com posse antecipada;
  - dois pares Task × `Checklist Review` ligados pelo link `Blocks` em `fields.issuelinks` (nunca pelo campo `parent`), um em cada direção (a Review bloqueia a task; a task bloqueia a Review), um com vencimentos iguais e outro divergente (a Tarefa 12 grava esses links e acrescenta a conferência ao `cenarioCobreAuditoriaETempoEmStatus`);
  - uma chave `JIRAUSER*` resolvida pelo objeto da issue e outra sem resolução.
  Instância `sidia`, projeto `CAT`, 5 issues `DEMOCAT-1..5` só para provar o isolamento por instância (nenhum card lê a `sidia` ainda; ela não aparece nas telas).

- [ ] **Passo 1: Testes que falham:**
  - `SettingsTest`: `demoPadraoEhFalse`; `demoEmProducaoDerrubaSubida` (`DASHBOARD_DEV_MODE=false` + `DASHBOARD_DEMO_DATA=true` → exceção que cita só nomes de variáveis); `demoComFlagLigadaDerrubaSubida` (flag `true` + demo `true` → exceção); `demoValorInvalidoFalha` (`DASHBOARD_DEMO_DATA=sim`).
  - `DadosDeDemonstracaoTest` (Testcontainers, banco real): `carregaAsDuasInstancias` (contagens de issues e eventos iguais às das fixtures; chaves `DEMOCAT-*` só na `sidia`); `recarregarEhIdempotente` (duas cargas → mesmas contagens de `issue` e `change_event`); `datasDeslocadasEmSemanasInteiras` (relógio em âncora + 10 dias → deslocamento 7; âncora + 14 → 14; âncora − 5 → −7; âncora → 0; dia da semana de `created` preservado); `nenhumInstanteNoFuturo` (relógio antes, na e depois da âncora → todo `created`, `updated`, `resolved` e evento gravado ≤ agora); `demoNaoFazRequisicao` (contexto com a fábrica `internal` e MockEngine contador → 0 requisições depois de `prepararDemonstracao`; o código-fonte de `demo/` não cita `JiraClient`, `JiraClientFactory` nem `HttpClient`); `demoRecusaBancoComDadosReais` (issue `TG-1` gravada antes na `samsung` → `DemoRecusadaException("BANCO_COM_DADOS_REAIS")` e nenhuma linha apagada); `flagLigadaComDadosDeDemoDerrubaSubida`; `tempoEmStatusMostraNumerosComDemo` (rota do card como `dev` → issues analisadas > 0, sem aviso bloqueante; o `Epic` e a `Cancelled` não entram).
  - `FixturesDeDemoTest` (sem banco): `vocabularioDasFixturesEstaNoMapaENaFicha` (regra 2a); `instantesAteAVesperaDaAncora` (regra 2); `fixturesSemDadosPessoais` (nenhum `@`, `emailAddress`, `avatarUrls` nem host fora de `.invalid`; toda pessoa é um `id` de `papeis.exemplo.yaml`, `pl.*`, `fora.do.diretorio` ou `JIRAUSER*`); `cenarioCobreAuditoriaETempoEmStatus` (estrutural, lendo os eventos: existe cada caso da lista acima, para que o Plano 4 encontre sinais sem depender do código dele).
  - `MetaTest.faixaComDadosDeDemonstracao` (demo ligado → texto da faixa acima); `metaNaoMudou` continua verde com demo desligado.
  - `test-guard.sh`: `DASHBOARD_DEMO_DATA=true docker compose up --build -d` → liberado (0).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*DadosDeDemonstracaoTest*' --tests '*FixturesDeDemoTest*' --tests '*SettingsTest*' --tests '*MetaTest*'` → FALHA. **Passo 3: Implementar** e escrever as fixtures. **Passo 4: Rodar** → PASSA, suíte completa e `sh .claude/hooks/test-guard.sh`.
- [ ] **Passo 5: README, seção "Ver o painel hoje em 3 comandos"** (a partir da raiz do repositório):
  ```sh
  cd app
  POSTGRES_PASSWORD=<valor descartável> DASHBOARD_DEMO_DATA=true docker compose up --build -d
  open http://127.0.0.1:8080        # Linux: xdg-open; ou abra o endereço no navegador
  ```
  Notas logo abaixo: dados 100% fictícios e faixa vermelha; o PostgreSQL fixa a senha na **primeira** criação do volume (reusar o mesmo valor nas subidas seguintes ou recomeçar com `docker compose down -v`); `docker compose down -v` também é obrigatório antes de ligar a flag do Jira (o painel recusa subir com a flag ligada e dados de demonstração no banco); se a flag `JIRA_LIVE_ENABLED` estiver ligada no terminal (ou no ambiente do Claude Code, Tarefa 11), o compose a repassa e a subida com demonstração **falha de propósito**: desligue-a nesse terminal antes.
- [ ] **Passo 6: Conferência manual** (opcional, uma rodada): subir com os 3 comandos e ver números no Tempo em status. **Passo 7: Commit** `feat(demo): dados fictícios de demonstração sem Jira, recusados em produção`.

---

### Tarefa 11: Hook do Claude consciente da flag e checklist sem pedido de autorização

**Por quê:** a regra 1 do `CLAUDE.md` (25/10) faz da flag `JIRA_LIVE_ENABLED` a única trava, para pessoas e IAs. O `guard.sh` (hook do Claude Code) ainda barra todo acesso ao Jira sempre, e o checklist ainda manda "pedir autorização expressa" a cada passo. Esta tarefa alinha os dois à regra nova, sem afrouxar nada enquanto a flag estiver `false`.

**Arquivos:** Modificar `.claude/hooks/guard.sh`, `.claude/hooks/test-guard.sh`, `.claude/hooks/ensure-docker.sh` (só comentários, se citarem a regra antiga), `docs/jira/checklist-primeira-conexao.md` (reescrita), `.gitignore` (`.claude/settings.local.json`: hoje só o ignore global de uma máquina o cobre), `app/backend/build.gradle.kts` (flag fixada em `false` na tarefa `test`), `HANDOFF.md` ("Dica do hook"); Teste `jql/PreparacaoDocsTest.kt`, `config/SettingsTest.kt`, `.claude/hooks/test-guard.sh`.

**Interfaces (comportamento do hook):**
- **Leitura da flag:** o hook lê `JIRA_LIVE_ENABLED` do **próprio ambiente** (o do processo do Claude Code), com a mesma regra do `Settings`: espaços nas pontas ignorados, `true`/`false` sem diferenciar maiúsculas. **Só `true` libera.** Ausente, vazia, `false` ou valor inválido = **travado**.
- **Travado** (modo `jira`): tudo o que o hook barra hoje continua barrado: ferramentas MCP da Atlassian, variáveis `JIRA_*` (exceto a própria flag com `false`), URLs com `jira`/`atlassian`, ferramentas de rede junto de "jira"/"atlassian", `jiradash check|sync|serve` e **ligar a flag dentro do comando** (`JIRA_LIVE_ENABLED=true ...`: só o usuário liga a flag). As mensagens passam a dizer "com JIRA_LIVE_ENABLED=false nenhum acesso ao Jira; só o usuário liga a flag", sem "autorização expressa".
- **Liberado** (modo `jira`): as regras acima deixam de bloquear. **Continua bloqueado (valor padrão recomendado, reversível):** exibir segredo no terminal, isto é, comando que começa (ou tem um trecho depois de `;`, `&&`, `||` ou `|`) com `echo`, `printf`, `printenv`, `env`, `set` ou `declare` e cita uma variável `JIRA_*` com `TOKEN`, `PASS`, `SENHA` ou `SECRET` no nome, ou `env`/`printenv`/`set` sem argumento canalizado para `grep` de "jira". Usar o token como argumento de outro comando (`curl -H "Authorization: Bearer $JIRA_SAMSUNG_TOKEN" ...`) é liberado.
- **Modo `arquivos`:** não muda, com qualquer valor da flag.
- **Como o usuário liga a flag do hook** (documentar no `HANDOFF.md` e no topo do `guard.sh`): (a) exportar a variável no terminal antes de abrir o Claude Code, ou (b) `env` em `.claude/settings.local.json`, que o `.gitignore` do repositório passa a ignorar (hoje só o ignore global de uma máquina o cobre). Nunca no `.claude/settings.json` versionado. A mudança vale depois de reabrir a sessão. Na execução, conferir que o hook enxerga a variável definida em (b); se não enxergar, documentar só (a) e registrar `Ruling`. **Atenção (documentar com essas palavras):** nas duas formas a flag entra no ambiente do próprio Claude Code e é **herdada por todo comando que ele roda**: `docker compose up` a repassa ao painel (`${JIRA_LIVE_ENABLED:-false}`) e `./gradlew` a recebe. Na prática, ligar a flag do hook liga a do app em tudo o que o Claude Code lançar.
- **Testes isolados do ambiente (valor padrão recomendado, reversível):** a tarefa `test` do Gradle fixa `JIRA_LIVE_ENABLED=false` para o processo de teste (`tasks.test { environment("JIRA_LIVE_ENABLED", "false") }` em `app/backend/build.gradle.kts`, arquivo que esta tarefa também modifica), e o teste `suiteNaoHerdaAFlagDoAmbiente` (em `config/SettingsTest.kt`) afirma que `System.getenv("JIRA_LIVE_ENABLED")` vale `"false"` dentro da suíte. É isso que garante "testes usam MockEngine mesmo com a flag `true` no ambiente".
- **Checklist reescrito** (`docs/jira/checklist-primeira-conexao.md`), mesma ordem e mesmos cuidados: título "Checklist da primeira conexão ao Jira"; abertura "procedimento cuidadoso recomendado; a flag é a única trava; com `false` nenhum passo pode ser executado"; em cada passo, "Peça autorização expressa antes" vira "**Confirme `JIRA_LIVE_ENABLED=true` e rode o passo.**"; ficam iguais: uma instância por vez, token só em variável de ambiente, parar no primeiro 401/403/429/5xx/CAPTCHA sem repetir, `maxResults=1` no Passo 3 (mostrar e registrar o JQL de cada ficha antes de enviar), uma só issue no Passo 4, 4b só se 4a não trouxer o changelog completo, não copiar dados pessoais nem respostas brutas, registrar cada resultado na matriz, o passo extra dos ids de `papeis.yaml`. "Depois dos cinco passos": a sincronização periódica segue o runbook com a flag ligada; voltar ao modo offline = o usuário desliga a flag. Nenhum host real, token nem e-mail.

- [ ] **Passo 1: Testes que falham:**
  - `test-guard.sh`: a função base `caso` passa a rodar o guard com a flag **removida** do ambiente (`env -u JIRA_LIVE_ENABLED`) por padrão, para nenhum caso (inclusive o do MCP, os falsos positivos e o limite documentado, que chamam `caso` direto) depender do terminal de quem roda; helpers novos `jf` (flag `false`), `jt` (flag `true`) e `jx` (flag `sim`, inválida) definem o valor explicitamente. Casos `trava:`: todos os casos atuais de `j` continuam com o mesmo resultado; com `jf` e `jx`, `curl -s https://jira.exemplo.invalid/rest/api/2/myself` → 2, MCP Atlassian → 2, `JIRA_LIVE_ENABLED=true ./gradlew test` → 2. Casos `liberado:` com `jt`: o mesmo `curl` → 0; MCP Atlassian → 0; `JIRA_LIVE_ENABLED=true ./gradlew test` → 0; `curl -s -H "Authorization: Bearer $JIRA_SAMSUNG_TOKEN" https://jira.exemplo.invalid/rest/api/2/myself` → 0; `echo $JIRA_SAMSUNG_TOKEN` → 2; `printenv JIRA_SIDIA_TOKEN` → 2; `env | grep -i jira` → 2; com `jt` e o valor `" TRUE "` → liberado. Modo `arquivos` com `jt`: `git add .env` → 2 e `Edit .env` → 2 (nada muda).
  - `SettingsTest.suiteNaoHerdaAFlagDoAmbiente` (rodar também com a flag `true` exportada no terminal: continua `"false"` na suíte).
  - `PreparacaoDocsTest`: `checklistETemTodasAsChamadas` mantém as chamadas exigidas e troca a contagem para ao menos 5 ocorrências de "Confirme `JIRA_LIVE_ENABLED=true`"; `cadaPassoDoChecklistExigeAutorizacaoExpressa` é **substituído** por `cadaPassoDoChecklistConfirmaAFlag` (toda seção `Passo` contém "Confirme `JIRA_LIVE_ENABLED=true`"); novo `checklistSemRegraAntiga` (não contém "autorização expressa" nem "por pesquisa"); `documentosDeJiraNaoTemHostRealTokenNemEmail` e os testes do runbook (T9) seguem verdes.
- [ ] **Passo 2: Rodar** `sh .claude/hooks/test-guard.sh` e `cd app/backend && ./gradlew test --tests '*PreparacaoDocsTest*'` → FALHA. **Passo 3: Implementar** o hook (comentários do topo: regra nova, limite honesto de "trava de acidente, não de má-fé", como ligar a flag do hook) e reescrever o checklist com Write/Edit (nunca heredoc). **Passo 4: Rodar** → PASSA e suíte completa do backend. **Passo 5: Commit** `chore(hooks): hook consciente da flag do Jira e checklist sem pedido de autorização`.

---

### Tarefa 12: Vínculos entre issues (Blocks / is blocked by)

**Por quê:** Decisão do usuário (25/10): a Review (`Checklist Review`) se liga à sua task pelo **link de issue "Blocks / is blocked by"** (`fields.issuelinks` do Jira), e **não** pelo campo `parent`. O card Task × Review do Plano 4 (e, depois dele, o Checklist × Review do Plano 7) lê os pares desta tarefa. `issue.parent_key` continua gravado, mas só para hierarquia/Epic: não define par. Executada **por último no 4A** (depois da Tarefa 11), porque estende o que as Tarefas 3, 4, 6, 7 e 10 criaram.

**Arquivos:** Criar `resources/db/migration/V6__vinculos_entre_issues.sql` (número livre confirmado na execução; ver Restrições globais), `sync/IssueLinks.kt`, `sync/IssueLinkStore.kt`, `sync/VinculosTaskReview.kt`, `app/config/vinculos.exemplo.yaml`, `src/test/resources/fixtures/dc/search-issuelinks.json`; Modificar `jql/CardQueryFactory.kt` (`issuelinks` na lista `minimos` de `build`; `buildLeve` não muda), `sync/Syncer.kt` e `sync/IssueStore.kt` (`upsert` grava os vínculos na mesma transação; `renomear` da Tarefa 6 atualiza as pontas), `db/Retention.kt` (Tarefa 7), `demo/DadosDeDemonstracao.kt` e `resources/demo/samsung.json` (Tarefa 10), `AppContext.kt` (`vinculos`), `TestContext.kt`, `TestContextComBanco.kt`, `.gitignore` (`**/config/vinculos.yaml`, se ausente), `docs/jira/checklist-primeira-conexao.md` (um item no Passo 4), `docs/jira/matriz-compatibilidade.md` (linha de `issuelinks`); Teste `sync/IssueLinksTest.kt`, `sync/IssueLinkStoreTest.kt`, `sync/VinculosTaskReviewTest.kt`, `sync/SyncerTest.kt`, `sync/ReconciliacaoTest.kt`, `db/RetentionTest.kt`, `jql/CardQueryFactoryTest.kt`, `AppContextTest.kt`, `demo/DadosDeDemonstracaoTest.kt`, `demo/FixturesDeDemoTest.kt`.

**Interfaces:**
- **Migração** (sem FK para `issue`: a outra ponta pode não estar na base, por exemplo fora da ficha ou de outro projeto):
  ```sql
  CREATE TABLE issue_link (
      instance    text NOT NULL,
      from_key    text NOT NULL,              -- ponta outward ("blocks"); em tipo simétrico, a menor chave natural
      to_key      text NOT NULL,              -- ponta inward ("is blocked by")
      link_type   text NOT NULL,              -- type.name do Jira (ex.: "Blocks")
      direction   text NOT NULL CHECK (direction IN ('OUTWARD', 'SIMETRICO')),
      visto_em    timestamptz NOT NULL,
      removida_em timestamptz,
      PRIMARY KEY (instance, from_key, to_key, link_type),
      CHECK (from_key <> to_key)
  );
  CREATE INDEX issue_link_to ON issue_link (instance, to_key);
  CREATE INDEX issue_link_tipo_ativo ON issue_link (instance, link_type) WHERE removida_em IS NULL;
  ```
  **Uma linha por link do Jira.** O link pertence às **duas pontas**: chegue pelo JSON da task ou pelo da Review, vira a mesma linha (chave natural `(instance, from_key, to_key, link_type)`, sempre normalizada com `from_key` = ponta outward). `direction` registra a convenção: `OUTWARD` = "`from_key` [type.outward] `to_key`" (ex.: "TG-2 blocks TG-1"); `SIMETRICO` = tipo cujo `inward` é igual ao `outward` (ex.: "relates to"), em que `from_key` é a menor chave por `ORDEM_NATURAL_DE_CHAVES`.
- **Semântica do DC (por extenso):** em `fields.issuelinks[]`, cada item traz `type {name, inward, outward}` e **uma** das pontas: `outwardIssue {key}` presente = "**esta issue** [type.outward] `outwardIssue`" → `(from = esta, to = outwardIssue.key)`; `inwardIssue {key}` presente = "`inwardIssue` [type.outward] **esta issue**" → `(from = inwardIssue.key, to = esta)`. Ex.: TG-2 (Review) com `outwardIssue TG-1` e TG-1 (task) com `inwardIssue TG-2`, tipo `Blocks` → a mesma linha `(TG-2, TG-1, Blocks, OUTWARD)`.
- Produz:
  ```kotlin
  enum class DirecaoVinculo { OUTWARD, SIMETRICO }
  data class VinculoRecord(val fromKey: String, val toKey: String, val tipo: String, val direcao: DirecaoVinculo)
  object IssueLinks {
      // null = campo `issuelinks` ausente no JSON (não mexe nos vínculos gravados); lista vazia = a issue não tem vínculos
      fun ler(issueKey: String, issue: JsonObject): List<VinculoRecord>?
  }
  interface IssueLinkStore {
      fun ativos(instance: String, tipo: String): List<VinculoRecord>              // removida_em IS NULL; tipo sem diferenciar maiúsculas
      fun daIssue(instance: String, key: String): List<VinculoRecord>              // ativos em que a issue é qualquer ponta
  }
  class PostgresIssueLinkStore(private val ds: DataSource) : IssueLinkStore {
      internal fun substituir(conn: Connection, instance: String, issueKey: String, vinculos: List<VinculoRecord>, at: Instant)
  }
  data class VinculosConfig(val versao: Int, val tipoDeLink: String = "Blocks", val tiposReview: Set<String>,
                            val tiposTask: Set<String> = emptySet()) {  // vazio = todo tipo fora de tiposReview
      fun ehReview(tipo: String): Boolean; fun ehTask(tipo: String): Boolean
      companion object { fun fromYaml(path: Path): VinculosConfig }  // estrito; tiposReview vazio falha; tipo em Review e Task ao mesmo tempo falha
  }
  data class ParDeChaves(val task: String, val review: String)
  data class ResolucaoDePares(val pares: List<ParDeChaves>, val ambiguas: Int, val ignorados: Int)
  object ParesTaskReview {
      // tipoDe: chave -> issuetype das issues ATIVAS da instância; ponta ausente do mapa = ignorada
      fun resolver(vinculos: List<VinculoRecord>, tipoDe: Map<String, String>, cfg: VinculosConfig): ResolucaoDePares
  }
  ```
  `IssueLinks.ler` lê o JSON **recebido**, antes do `SanitizadorRaw` (como `ChavesDeUsuario`), e guarda **só** chaves e `type.name`: nada de `summary`, `status`, `priority` nem outro dado da outra ponta (nenhum dado pessoal). Item malformado (sem `type.name`, sem nenhuma ou com as duas pontas, chave fora do padrão de `JqlValues.issueKey`, vínculo da issue com ela mesma) é ignorado, sem exceção; o log leva só a contagem. `PostgresIssueStore.upsert` ganha o parâmetro `vinculos: List<VinculoRecord>? = null` e chama `substituir` **na mesma transação** da issue: com `null`, não mexe; com lista, as linhas ativas em que a issue é **qualquer ponta** e que não estão na lista recebem `removida_em = at` (o link sumiu no Jira); as da lista são inseridas ou reativadas (`ON CONFLICT ... DO UPDATE SET removida_em = NULL, visto_em = at, direction = EXCLUDED.direction`). Idempotente: o mesmo JSON duas vezes não muda nada além de `visto_em`. Na reconciliação (Tarefa 6), `IssueIndex.renomear(instance, jiraId, novaChave)` passa a atualizar também `from_key`/`to_key` de `issue_link`, na mesma transação. A retenção (Tarefa 7) apaga os vínculos em que uma ponta foi purgada nessa execução e os com `removida_em < limite`. A recarga da demonstração (Tarefa 10) apaga, na mesma transação, os vínculos com pontas de prefixo `DEMO-`/`DEMOCAT-` (não há cascata) e, ao regravar cada issue, chama `IssueLinks.ler` sobre o JSON da fixture e passa o resultado ao `upsert(..., vinculos = ...)` (sem isso a demo ficaria sem pares e o card cairia em `VINCULOS_INDISPONIVEIS`). Instâncias isoladas: nenhuma operação cruza `instance`.
  `issuelinks` entra na lista `minimos` de `CardQueryFactory.build` (é campo de sistema; não vai em `campos:` da ficha, que só aceita nomes do dicionário), então a ficha `sincronizacao` passa a pedi-lo; `buildLeve` continua `["key", "updated"]`.
- **Configuração** `app/config/vinculos.exemplo.yaml` (versionado; o real `vinculos.yaml` fica fora do git; fallback `.exemplo` só em dev, como os demais; `AppContext` ganha `val vinculos: VinculosConfig`):
  ```yaml
  versao: 1
  tipoDeLink: Blocks              # type.name do link no Jira; confirmar na 1ª conexão (checklist, Passo 4)
  tiposReview: ["Checklist Review"]
  tiposTask: []                   # vazio = qualquer tipo da ficha de sincronização fora de tiposReview
  ```
  **As duas direções valem:** a Review pode ser a que bloqueia ou a bloqueada. Regras do `resolver`, nesta ordem: (1) só vínculos do `tipoDeLink` (sem diferenciar maiúsculas); (2) as duas pontas precisam estar em `tipoDe` (issue ativa na base), senão `ignorados += 1`; (3) é candidato **só** o vínculo com exatamente uma ponta Review e a outra task: Review × Review e task × task (um bloqueio de verdade entre tasks) são ignorados; (4) **desempate:** para cada Review, fica a task de **menor chave por `ORDEM_NATURAL_DE_CHAVES`** (nunca ordem de texto: TG-9 vem antes de TG-10); depois, para cada task que sobrou com mais de uma Review, fica a Review de menor chave; cada candidato descartado soma 1 em `ambiguas`; (5) nunca dois pares iguais. O consumidor (Plano 4) transforma `ambiguas > 0` no aviso informativo `VINCULO_AMBIGUO` (`params["n"]`). Valor padrão recomendado (25/10, reversível).

- [ ] **Passo 1: Fixture** `search-issuelinks.json` (instância `samsung`, só `.invalid`, sem dados pessoais): TG-1 (Task) e TG-2 (Checklist Review) com `Blocks` visto pelas duas pontas (TG-2 `outwardIssue TG-1`; TG-1 `inwardIssue TG-2`); TG-3 (Task) bloqueia TG-4 (Checklist Review) (direção oposta); TG-5 e TG-6 (duas Tasks) com `Blocks`; TG-7 com `Relates` (`inward == outward`); um item malformado; TG-8 sem o campo `issuelinks`.
- [ ] **Passo 2: Testes que falham:**
  - `IssueLinksTest`: `outwardIssueViraOrigem` (TG-2 com `outwardIssue TG-1` → `(TG-2, TG-1, Blocks, OUTWARD)`); `inwardIssueViraDestino` (TG-1 com `inwardIssue TG-2` → o mesmo registro); `tipoSimetricoOrdenaPelaChaveNatural` (`Relates` entre TG-10 e TG-9 → `from = TG-9`, `SIMETRICO`); `campoAusenteDevolveNulo` e `listaVaziaDevolveVazia`; `itemMalformadoIgnorado` (sem `type.name`, sem ponta, chave `TG-1) or (1=1`, auto-vínculo → nada, sem exceção); `naoGuardaDadosDaOutraPonta` (o registro só tem chaves, tipo e direção).
  - `IssueLinkStoreTest` (Testcontainers): `linkVistoPelasDuasPontasEhUmaLinha` (upsert de TG-1 e de TG-2 → 1 linha); `vinculosIdempotentes` (mesmo JSON duas vezes → mesma contagem e nenhuma `removida_em`); `linkRemovidoGanhaRemovidaEm` (TG-2 volta sem o link → `removida_em = agora`, fora de `ativos`); `linkQueVoltaLimpaRemocao`; `campoAusenteNaoApagaVinculos` (upsert de TG-1 com JSON sem `issuelinks` → vínculo continua ativo); `vinculosPorInstanciaIsolados` (o mesmo par na `sidia` não aparece na `samsung`, e remover na `samsung` não toca a `sidia`).
  - `VinculosTaskReviewTest`: `reviewQueBloqueiaATask` (TG-2 bloqueia TG-1 → `ParDeChaves(TG-1, TG-2)`); `reviewBloqueadaPelaTask` (TG-3 bloqueia TG-4 → `ParDeChaves(TG-3, TG-4)`); `blocksEntreDuasTasksIgnorado`; `reviewComReviewIgnorado`; `outroTipoDeLinkIgnorado` (`Relates`); `pontaForaDaBaseIgnorada` (`ignorados == 1`); `ambiguidadeUsaChaveNaturalMenor` (Review TG-50 ligada a TG-9 e TG-10 → par com TG-9, `ambiguas == 1`); `taskComDuasReviewsUsaChaveMenor` (task TG-1 com TG-20 e TG-3 Reviews → par com TG-3, `ambiguas == 1`); `semDuplicar`; `vinculosEstrito` (chave desconhecida, `tiposReview` vazio e tipo nas duas listas → falha citando a chave); `appContextCarregaVinculosComFallbackDeDev`.
  - `SyncerTest.syncerGravaVinculosDaFixture` (contagens esperadas em `issue_link`; `issue.raw` igual ao de antes); `CardQueryFactoryTest.fichaSincronizacaoPedeIssuelinks` (o snapshot de `fields` de `fichaSincronizacaoGeraJqlEsperado` passa a conter `issuelinks`; `buildLeveSoPedeKeyEUpdated` continua).
  - `ReconciliacaoTest.renomearAtualizaAsPontasDoVinculo` (`jira_id 10004` era TG-4, volta como TG-40 → o vínculo com TG-3 passa a TG-40); `RetentionTest.retencaoApagaVinculosDaIssuePurgada`.
  - `DadosDeDemonstracaoTest.carregaVinculosDaDemo` (depois de `carregar`, `issue_link` tem os dois pares `Blocks` da demo, um em cada direção, e `ParesTaskReview.resolver` devolve 2 pares); `DadosDeDemonstracaoTest.recarregarEhIdempotente` passa a contar também `issue_link` (contagem > 0, igual nas duas cargas); `FixturesDeDemoTest.cenarioCobreAuditoriaETempoEmStatus` confere os dois pares `Blocks` da demo (um em cada direção, um divergente) e nenhum par pelo `parent`.
  - `FlagDesligadaZeroRequisicoesTest` segue verde (nada aqui faz requisição).
- [ ] **Passo 3: Rodar** `./gradlew test --tests '*IssueLinks*' --tests '*IssueLinkStore*' --tests '*VinculosTaskReview*' --tests '*SyncerTest*' --tests '*CardQueryFactoryTest*' --tests '*ReconciliacaoTest*' --tests '*RetentionTest*' --tests '*DadosDeDemonstracao*' --tests '*FixturesDeDemo*' --tests '*AppContextTest*'` → FALHA. **Passo 4: Implementar** (SQL parametrizado). **Passo 5: Documentos:** no `checklist-primeira-conexao.md`, **dentro do Passo 4** (a issue com changelog já traz `issuelinks`; não criar seção "Passo" nova, para o `cadaPassoDoChecklistConfirmaAFlag` seguir verde), um item "conferir em `fields.issuelinks` o nome exato do tipo (`type.name`, esperado `Blocks`), os textos `inward`/`outward` (esperados `is blocked by`/`blocks`) e em que direção a Review aparece em relação à task; se criar ou remover um link altera o `updated` das **duas** issues (se não alterar, o incremental não percebe a mudança: registrar e tratar como divergência na matriz); se o nome diferir, ajustar o `vinculos.yaml` real; registrar só o tipo e a direção na matriz, sem chaves nem resumo de issues reais"; na matriz, a linha de `issuelinks` ("não validado"). **Passo 6: Rodar** → PASSA, suíte completa e `PreparacaoDocsTest`. **Passo 7: Commit** `feat(sync): vínculos entre issues (Blocks) para ligar Review e task`.

---

## Autoavaliação

- **Cobertura:** spec 5.4 (incremental com marca d'água, reconciliação diária, retenção de 24 meses agendada), 4.6 (10 minutos e reconciliação diária, dezenas de milhares de issues: fluxo, teto de itens e de requisições), 5.3 (retry e `Retry-After` do cliente, mantidos), 3 e 10 (nenhuma requisição com a flag `false` por nenhum caminho, provada em `FlagDesligadaZeroRequisicoesTest`; a flag é a única trava). Final review: M3 (montagem), M4 (retenção de `issue` e `access_log` e agendamento), M5 (drill), M7 (ficha × vocabulário, via filtro de leitura); Minor da Tarefa 4 (JIRAUSER, memória, `aberta` × done+resolution, `summary/created` ausentes) e avisos por código com parâmetros. M1, M2, M6, I1 e I2 já corrigidos na PR #9 (M6 vira regra de commit).
- **Consistência:** `SyncKind`, `SyncMode`, `MotivoNegacao` (`DESLIGADO_PELA_FLAG`), `RunStatus`, `RunResumo` e `RegistroDeExecucao` (T1/T2) são os mesmos no serviço (T5/T6), no agendador (T7) e no DTO (T8); `AtualizadoDesde` e a ficha `sincronizacao` (T3) são a única fonte do JQL de T5 e T6.
- **Vínculos (T12):** `issue_link` (migração `V6`, número livre confirmado na execução) é escrito só pelo `upsert` (T4), por `renomear` (T6), pela retenção (T7) e pela recarga da demo (T10); `ParesTaskReview.resolver` e `VinculosConfig` são a única fonte do pareamento Task × Review do Plano 4 (e, por ele, do Plano 7). `parent_key` segue gravado só para hierarquia/Epic. O plano tem 12 tarefas.
- **Dados de demonstração e hook (T10, T11):** a T10 reaproveita o caminho real de gravação (T4) sem cliente Jira e não cria migração (as migrações do plano são a da T2, `V4`, sua correção `V5` e a da T12); a T11 troca, no `PreparacaoDocsTest`, `cadaPassoDoChecklistExigeAutorizacaoExpressa` por `cadaPassoDoChecklistConfirmaAFlag`, nome único usado também pelos Planos 4 (Tarefa 9) e 8 (Tarefa 8).
- **Fora deste plano:** escrita no Jira (Plano 16, `2026-10-25-plano-16-escrita-no-jira-pelo-painel.md`); worklog (sem dado sincronizado); `jira_sidia` para usernames diferentes na Sidia; múltiplas instâncias além de `samsung` (a configuração aceita, os testes cobrem só uma).

## Decisões

1. **Decisão do usuário (25/10): a flag é a única trava.** Sem contador de autorização por execução, sem tabela de autorizações e sem modo manual. Com `JIRA_LIVE_ENABLED=false` nenhum acesso ao Jira DC (cliente, agendador, carga, reconciliação) e o status mostra "desligado pela flag"; só o usuário muda a flag para `true`, e então a sincronização periódica roda sozinha. Todo o plano é implementado e testado offline (MockEngine), com prova de 0 requisições com a flag `false`, inclusive pelo agendador (T1, T5, T6, T7). A mesma flag vale para IAs e pessoas (regra 1 do `CLAUDE.md`, reescrita em 25/10; a antiga autorização por pesquisa foi removida): runbook (T9), hook do Claude e checklist (T11).
2. **Decisão do usuário (25/10): universo da ficha de sincronização.** Tipos Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check e Task; Epic fora; canceladas incluídas (T3).
3. **Decisão do usuário (25/10): fuso do JQL.** `SYNC_FUSO_SERVIDOR` padrão America/Manaus até ler o `timeZone` do usuário do token no Passo 1 do checklist (T1, T9).
4. **Decisão do usuário (25/10): retenção do `access_log`.** Mesmo `RETENTION_MONTHS` (24) (T7).
5. **Decisão do usuário (25/10): retenção de `issue`.** Issues resolvidas e sem atualização há mais de 24 meses são apagadas, com seus eventos; abertas nunca (T7).
6. **Decisão do usuário (25/10): suspensão.** Após 401/403/429 (e, pela mesma regra, 3 falhas seguidas), a instância fica suspensa até o usuário reiniciar o app ou religar a flag (T5, T7).
7. **Decisão do usuário (25/10): status do sincronizador.** Só o gerente (T8).
8. **Decisão do usuário (25/10): proteção na reconciliação.** Nunca remover mais de 50% da base local numa reconciliação; acima disso, `RECONCILIACAO_SUSPEITA` e nada é removido (T6).
9. **Decisão do usuário (25/10): dados de demonstração.** Com `DASHBOARD_DEV_MODE=true` e `DASHBOARD_DEMO_DATA=true` (padrão `false`), o painel carrega na subida, sem tocar no Jira, issues e changelog fictícios (duas instâncias, dois squads, cenário rico), de forma idempotente; `DASHBOARD_DEV_MODE=false` com demo `true` derruba a subida; teste com banco real; README com os 3 comandos (T10). **Valores padrão recomendados (25/10, reversíveis):** demo com a flag do Jira `true` também derruba a subida; datas deslocadas em semanas inteiras até hoje; substituição pelo prefixo `DEMO`/`DEMOCAT`; recusa de banco com issues reais; com a flag ligada e dados de demonstração no banco, a subida falha (nada é apagado sozinho); faixa "dados fictícios de demonstração".
10. **Decisão do usuário (25/10): hook do Claude consciente da flag.** `guard.sh` libera o acesso ao Jira só com `JIRA_LIVE_ENABLED=true` no ambiente do hook e continua bloqueando com `false` ou ausente; o checklist troca "peça autorização expressa" por "confirme `JIRA_LIVE_ENABLED=true` e rode o passo", mantendo ordem e cuidados (T11). **Valores padrão recomendados (25/10, reversíveis):** valor inválido = travado; ligar a flag dentro de um comando continua barrado com a trava fechada; com a flag ligada, exibir token ou senha no terminal continua barrado; a flag do hook fica no ambiente de abertura do Claude Code ou no `.claude/settings.local.json` git-ignorado, nunca no `settings.json` versionado (e, por herança, também liga a flag do app no que o Claude Code lançar); a tarefa `test` do Gradle fixa a flag em `false`.
11. **Decisão do usuário (25/10): Review ligada à task pelo link "Blocks / is blocked by".** O par Task × Review vem de `fields.issuelinks` (tabela `issue_link`), não do campo `parent` (`parent_key` fica só para hierarquia/Epic); as duas direções valem; no painel, a ação é a lista de divergências com link direto para a task no Jira (Plano 4); escrita no Jira pelo painel é futura (Plano 16) (T12). **Valores padrão recomendados (25/10, reversíveis):** tipo `Blocks` e tipos Review (`Checklist Review`) em `vinculos.yaml`; uma linha por link, normalizada pela ponta outward; campo `issuelinks` ausente não apaga vínculo; ambiguidade (Review com mais de uma task, ou task com mais de uma Review) gera aviso informativo e fica o par de menor chave natural, sem duplicar; o nome exato do tipo e a direção se confirmam na primeira conexão (checklist, Passo 4).
