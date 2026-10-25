# Fundação do Painel de Squads (Fase 1) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas `- [ ]`.

**Objetivo:** Deixar de pé o esqueleto do novo app (Ktor + PostgreSQL + React/Vite) com flags, fuso de Manaus e dias úteis, papéis com vigência, filtro por papel em todas as rotas, banco com retenção, cliente Jira com trava e a casca do frontend, tudo testado offline.

**Arquitetura:** Monorepo em `app/` (`backend/`, `frontend/`, `config/`). O app Python atual vai para `legacy/` com `git mv` (histórico preservado) e serve de referência de números até a Fase 8. Backend em Kotlin/Ktor com Exposed + Flyway; o frontend é uma SPA React servida pelo próprio Ktor em produção. O Jira só é acessado por `JiraClient`, que passa por `LiveGuard`.

**Stack:** Kotlin 2.x, Ktor 3.x (server e client), kotlinx.serialization, Exposed, Flyway, HikariCP, PostgreSQL 16, JUnit 5 + kotlin.test, Ktor `testApplication` e `MockEngine`, Testcontainers (PostgreSQL), React 19 + TypeScript, Vite, Vitest + Testing Library, pnpm. Versões: fixar em `app/backend/gradle/libs.versions.toml` e `app/frontend/package.json` na hora, usando as estáveis mais recentes.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.2, 4.3, 4.6, 5.3, 5.4).

## Restrições globais

- **Nenhuma chamada ao Jira real.** `JIRA_LIVE_ENABLED=false` por padrão; testes nunca usam rede (só `MockEngine`). O hook `.claude/hooks/guard.sh` continua valendo.
- `DASHBOARD_DEV_MODE=true` por padrão; `retentionMonths=24` por padrão; fuso `America/Manaus` (UTC−4, sem horário de verão).
- Pacote raiz Kotlin: `br.sidia.painel`. Comentários de código em pt-BR.
- Configs com dados pessoais reais nunca entram no git: usar só `*.exemplo.yaml` e `*.demo.yaml` (os nomes reais estão no `.gitignore`).
- Datas e dias: dia civil no fuso de Manaus em qualquer cálculo; dias úteis por padrão.
- Sem ranking de pessoas; listas de pessoas em ordem alfabética.

## Foco da revisão

- Resolução às 23:59 e às 00:00 de Manaus (virada de dia) cai no dia civil correto.
- Feriado que cai em fim de semana não duplica desconto; feriado de segunda (12/10) zera o dia.
- Pessoa com papel que terminou ontem ou começa amanhã: vigência por data, inclusive nas pontas.
- `DASHBOARD_DEV_MODE=true` com `JIRA_LIVE_ENABLED=true`: sobe, mas a faixa de aviso sinaliza dados reais sem login.
- Retenção exatamente no limite de 24 meses: o dia do limite fica, o anterior sai.

---

## Arquivos (mapa)

```
legacy/                                  # app Python movido (git mv de app/ atual)
app/backend/settings.gradle.kts          # projeto Gradle
app/backend/build.gradle.kts
app/backend/gradle/libs.versions.toml
app/backend/src/main/kotlin/br/sidia/painel/
  Application.kt                         # módulo Ktor, rotas, SPA estática
  config/Settings.kt                     # flags e variáveis de ambiente
  time/BusinessCalendar.kt               # fuso Manaus, dias úteis
  security/Role.kt, RoleDirectory.kt, Visibility.kt, Authenticator.kt, AccessControl.kt
  jira/JiraInstanceConfig.kt, LiveGuard.kt, JiraClient.kt, JiraModels.kt
  db/Database.kt, Retention.kt
app/backend/src/main/resources/db/migration/V1__base.sql
app/backend/src/test/kotlin/br/sidia/painel/...   # um arquivo de teste por unidade acima
app/config/papeis.exemplo.yaml, calendario.exemplo.yaml
app/frontend/                            # Vite + React + TS (src/theme, src/i18n, src/nav, src/shell)
app/Dockerfile, app/docker-compose.yml
```

---

### Tarefa 1: Mover o app Python e criar o esqueleto Gradle com `/api/health`

**Arquivos:**
- Mover: todo o conteúdo atual de `app/` para `legacy/` (`git mv`).
- Criar: `app/backend/settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `src/main/kotlin/br/sidia/painel/Application.kt`
- Teste: `app/backend/src/test/kotlin/br/sidia/painel/HealthTest.kt`

**Interfaces:**
- Produz: `fun Application.module(settings: Settings = Settings.fromEnv())` (módulo Ktor) e `GET /api/health` → `{"ok":true}`.

- [ ] **Passo 1: Mover o legado.** `git mv app legacy && mkdir -p app/backend app/config` e commit "Move app Python para legacy/". Verificar: `git status` só mostra renomeações.
- [ ] **Passo 2: Gerar o wrapper Gradle.** Se `gradle` não existir: `brew install gradle`. Em `app/backend`: `gradle wrapper`. Escrever `settings.gradle.kts` (nome `painel`) e `build.gradle.kts` com os plugins Kotlin JVM, serialization e application; JVM toolchain 21.
- [ ] **Passo 3: Teste que falha.** `HealthTest.healthRetornaOk`: `testApplication { application { module(Settings.fromEnv(emptyMap())) }; val r = client.get("/api/health"); assertEquals(HttpStatusCode.OK, r.status); assertEquals("""{"ok":true}""", r.bodyAsText()) }`.
- [ ] **Passo 4: Rodar e ver falhar.** `cd app/backend && ./gradlew test --tests '*HealthTest*'` → FALHA (módulo não existe).
- [ ] **Passo 5: Implementar** `Application.module` com `ContentNegotiation` (JSON) e a rota. `Settings` ainda não existe: criar a classe mínima na Tarefa 2; aqui usar um `Settings` vazio de uma linha só para compilar.
- [ ] **Passo 6: Rodar e ver passar.** `./gradlew test --tests '*HealthTest*'` → PASSA.
- [ ] **Passo 7: Commit.** `git add app/backend && git commit -m "Esqueleto Gradle/Ktor com /api/health"`.

---

### Tarefa 2: Flags e `/api/meta`

**Arquivos:**
- Criar: `config/Settings.kt`
- Modificar: `Application.kt`
- Teste: `config/SettingsTest.kt`, `MetaTest.kt`

**Interfaces:**
- Produz: `data class Settings(val jiraLiveEnabled: Boolean, val dashboardDevMode: Boolean, val retentionMonths: Int, val zone: ZoneId, val configDir: Path, val databaseUrl: String)` e `Settings.fromEnv(env: Map<String, String> = System.getenv()): Settings`. Variáveis: `JIRA_LIVE_ENABLED`, `DASHBOARD_DEV_MODE`, `RETENTION_MONTHS`, `PAINEL_TZ`, `PAINEL_CONFIG_DIR`, `DATABASE_URL`.
- Produz: `GET /api/meta` → `{"devMode":Boolean,"liveEnabled":Boolean,"zone":"America/Manaus","banner":String?}`. `banner` é `null` em produção; em dev é `"MODO DESENVOLVIMENTO"`; em dev com `liveEnabled` é `"MODO DESENVOLVIMENTO: dados reais do Jira sem login"`.

- [ ] **Passo 1: Testes que falham** (nomes e asserções):
  - `padroesSaoSeguros`: `Settings.fromEnv(emptyMap())` → `jiraLiveEnabled=false`, `dashboardDevMode=true`, `retentionMonths=24`, `zone=ZoneId.of("America/Manaus")`.
  - `leVariaveis`: `mapOf("JIRA_LIVE_ENABLED" to "true","DASHBOARD_DEV_MODE" to "false","RETENTION_MONTHS" to "12")` → valores correspondentes.
  - `valorInvalidoFalha`: `RETENTION_MONTHS=abc` lança `IllegalArgumentException` com o nome da variável na mensagem.
  - `metaEmProducaoNaoTemBanner`, `metaEmDevTemBanner`, `metaDevComLiveTemBannerDeDadosReais` (chamando `/api/meta` com cada configuração).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SettingsTest*' --tests '*MetaTest*'` → FALHA.
- [ ] **Passo 3: Implementar** `Settings` e a rota; registrar um aviso de log no startup quando `dashboardDevMode && jiraLiveEnabled`.
- [ ] **Passo 4: Rodar** os mesmos testes → PASSAM.
- [ ] **Passo 5: Commit** `git commit -m "Flags de ambiente e /api/meta"`.

---

### Tarefa 3: Calendário de Manaus e dias úteis

**Arquivos:**
- Criar: `time/BusinessCalendar.kt`, `app/config/calendario.exemplo.yaml` (feriados nacionais 2026 e feriados de Manaus; formato `feriados: [{data: 2026-10-12, nome: ...}]`)
- Teste: `time/BusinessCalendarTest.kt`

**Interfaces:**
- Produz:
  ```kotlin
  class BusinessCalendar(val zone: ZoneId, holidays: Set<LocalDate>) {
      fun localDate(instant: Instant): LocalDate
      fun isBusinessDay(date: LocalDate): Boolean
      fun businessDays(from: Instant, to: Instant): Double   // soma da fração de cada dia útil local coberto
      fun calendarDays(from: Instant, to: Instant): Double   // Duration / 24h
      companion object { fun fromYaml(path: Path, zone: ZoneId): BusinessCalendar }
  }
  ```

- [ ] **Passo 1: Testes que falham** (feriado de 12/10/2026 carregado; `zone=America/Manaus`):
  - `viradaDeDia`: `localDate(2026-10-31T03:59:00Z) == 2026-10-30` e `localDate(2026-10-31T04:00:00Z) == 2026-10-31`.
  - `diaUtil`: sexta 09/10 é útil; sábado 10/10 e domingo 11/10 não; segunda 12/10 (feriado) não.
  - `businessDaysSegundaAteTerca`: de `2026-10-05T12:00Z` (seg 08:00) a `2026-10-06T12:00Z` (ter 08:00) → `1.0` (tolerância 1e-6).
  - `businessDaysComFeriadoNaSegunda`: de `2026-10-09T12:00Z` (sex 08:00) a `2026-10-12T12:00Z` (seg 08:00, feriado) → `0.666667` e `calendarDays` → `3.0`.
  - `feriadoNoFimDeSemanaNaoDuplica`: feriado em sábado não altera o resultado de um intervalo de sexta a segunda.
  - `intervaloInvertidoFalha`: `from > to` lança `IllegalArgumentException`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*BusinessCalendarTest*'` → FALHA.
- [ ] **Passo 3: Implementar** convertendo para `ZonedDateTime` e somando, dia a dia, a fração de 24h dentro do intervalo em cada dia útil.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Calendário de Manaus com dias úteis"`.

---

### Tarefa 4: Papéis com vigência e visibilidade

**Arquivos:**
- Criar: `security/Role.kt`, `security/RoleDirectory.kt`, `security/Visibility.kt`, `app/config/papeis.exemplo.yaml`
- Teste: `security/RoleDirectoryTest.kt`, `security/VisibilityTest.kt`

**Interfaces:**
- Produz:
  ```kotlin
  enum class Role { USUARIO, SQUAD_LEADER, COORDENADOR, GERENTE }
  data class Vigencia(val de: LocalDate?, val ate: LocalDate?) { fun contem(d: LocalDate): Boolean } // pontas inclusivas; null = sem limite
  class RoleDirectory {
      fun rolesOf(userId: String, date: LocalDate): Set<Role>
      fun squadOf(userId: String, date: LocalDate): String?
      fun membersOf(squad: String, date: LocalDate): Set<String>
      fun squadsLedBy(userId: String, date: LocalDate): Set<String>
      fun isReporterAuthorized(reporterId: String, actorId: String, assigneeId: String?, date: LocalDate): Boolean
      companion object { fun fromYaml(path: Path): RoleDirectory }
  }
  data class Principal(val userId: String, val roles: Set<Role>)
  object Visibility { fun visibleUserIds(p: Principal, dir: RoleDirectory, today: LocalDate, allUsers: Set<String>): Set<String> }
  ```
  `isReporterAuthorized` é `true` quando `actorId == reporterId`, ou o ator tem papel `COORDENADOR`/`GERENTE` na data, ou é `SQUAD_LEADER` do squad do `assigneeId` na data. É usado pela Fase 4.

- [ ] **Passo 1: Testes que falham:**
  - `vigenciaPontasInclusivas`: `Vigencia(2026-03-01, 2026-09-30).contem(2026-09-30)` é `true`; `contem(2026-10-01)` é `false`.
  - `papelEncerradoOntem`: Squad Leader com `ate=ontem` não tem o papel hoje, tinha ontem.
  - `membrosDoSquadNaData`: pessoa que mudou de squad em 01/07 aparece em `membersOf("A", 2026-06-30)` e em `membersOf("B", 2026-07-01)`.
  - `usuarioVeSoOProprio`, `squadLeaderVeOProprioESeuSquad`, `coordenadorEGerenteVeemTodos` (`visibleUserIds` com `allUsers` de 6 pessoas).
  - `autorizacaoDaAuditoria`: Squad Leader do squad do responsável é autorizado; Squad Leader de outro squad não; o próprio responsável (que não é reporter) não.
  - `yamlDemoCarrega`: `papeis.exemplo.yaml` carrega sem erro e é coerente (nenhuma vigência com `de > ate`; `de > ate` lança `IllegalArgumentException`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RoleDirectoryTest*' --tests '*VisibilityTest*'` → FALHA.
- [ ] **Passo 3: Implementar** com a leitura YAML via `kaml` ou `snakeyaml-engine` (o que for mais simples com kotlinx.serialization). Esquema do YAML: `pessoas: [{id, nome, papeis: [{papel, de?, ate?, squad?}] }]`.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Papéis com vigência e visibilidade por papel"`.

---

### Tarefa 5: Autenticação, controle de acesso e consulta auditada

**Arquivos:**
- Criar: `security/Authenticator.kt`, `security/AccessControl.kt`
- Modificar: `Application.kt`
- Teste: `security/AccessControlTest.kt`

**Interfaces:**
- Consome: `Settings` (Tarefa 2), `Role`, `Principal` (Tarefa 4).
- Produz:
  ```kotlin
  interface Authenticator { suspend fun authenticate(call: ApplicationCall): Principal? }
  class DevAuthenticator : Authenticator          // sempre Principal("dev", setOf(Role.GERENTE))
  class OidcAuthenticator : Authenticator         // stub: sempre null até o provedor real ser definido
  fun Application.installAccessControl(settings: Settings, authenticator: Authenticator)
  val ApplicationCall.principal: Principal        // lança se não autenticado
  fun Route.requireRole(vararg roles: Role, build: Route.() -> Unit)
  ```
  Toda rota sob `/api/` exceto `/api/health` e `/api/meta` exige principal. Em `dashboardDevMode=true` usa `DevAuthenticator`; senão `OidcAuthenticator`.

- [ ] **Passo 1: Testes que falham:**
  - `devModeNaoExigeLogin`: com `dashboardDevMode=true`, `GET /api/ping-protegido` → 200.
  - `producaoSemTokenRetorna401`: com `dashboardDevMode=false`, mesma rota → 401; `/api/health` e `/api/meta` → 200.
  - `requireRoleNega403`: autenticador de teste que devolve `USUARIO`; rota com `requireRole(GERENTE)` → 403.
  - `requireRoleLibera`: autenticador que devolve `GERENTE` → 200.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AccessControlTest*'` → FALHA.
- [ ] **Passo 3: Implementar** com um plugin de rota do Ktor que resolve o principal uma vez por chamada.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Controle de acesso por papel e modo desenvolvimento"`.

---

### Tarefa 6: Banco, migração base e retenção

**Arquivos:**
- Criar: `db/Database.kt`, `db/Retention.kt`, `src/main/resources/db/migration/V1__base.sql`
- Teste: `db/RetentionTest.kt` (usa Testcontainers PostgreSQL)

**Interfaces:**
- Produz:
  ```kotlin
  object Database { fun connect(url: String, user: String?, password: String?): javax.sql.DataSource; fun migrate(ds: DataSource) }
  class Retention(private val ds: DataSource, private val calendar: BusinessCalendar) {
      fun purge(today: LocalDate, months: Int): Int   // remove eventos anteriores a today.minusMonths(months); devolve linhas removidas
  }
  ```
- Migração `V1__base.sql` cria: `person(id, jira_ref unique, name, active)`, `issue(id, key unique, instance, project, issue_type, status, resolution, assignee_ref, reporter_ref, created, updated, resolved, duedate, labels text[], parent_key, estimate_s, spent_s, raw jsonb)`, `change_event(id, changelog_id unique, issue_id, field, from_value, to_value, at, author_ref)`, `worklog(id, issue_id, author_ref, started, seconds)`, `sync_state(instance, watermark)`, `access_log(id, user_id, route, filters, at)`. Índices em `change_event(issue_id, at)` e `issue(status, resolved)`.

- [ ] **Passo 1: Testes que falham:**
  - `migracaoCriaTabelas`: após `migrate`, `information_schema` lista as 6 tabelas.
  - `retencaoRemoveSoOQueExcedeOLimite`: com `today=2026-10-24`, `months=24`, evento em `2024-10-24` permanece e evento em `2024-10-23` é removido; retorno `1`.
  - `retencaoNaoMexeEmIssues`: a tabela `issue` não perde linhas.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RetentionTest*'` → FALHA (precisa de Docker ligado).
- [ ] **Passo 3: Implementar** com HikariCP, Flyway e SQL direto na `Retention`.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Banco PostgreSQL, migração base e retenção de 24 meses"`.

---

### Tarefa 7: Cliente Jira com trava `LiveGuard`

**Arquivos:**
- Criar: `jira/JiraInstanceConfig.kt`, `jira/LiveGuard.kt`, `jira/JiraModels.kt`, `jira/JiraClient.kt`
- Teste: `jira/JiraClientTest.kt` (só `MockEngine`)

**Interfaces:**
- Produz:
  ```kotlin
  enum class JiraAuthKind { BEARER, BASIC }
  data class JiraInstanceConfig(val id: String, val baseUrl: String, val auth: JiraAuthKind, val token: String, val username: String? = null)
  class JiraLiveDisabledException(message: String) : RuntimeException(message)
  class LiveGuard(private val enabled: Boolean) { fun check() }    // lança JiraLiveDisabledException se !enabled
  class JiraException(val status: Int, message: String, val details: String?) : RuntimeException(message)
  data class SearchRequest(val jql: String, val fields: List<String>, val expandChangelog: Boolean = false, val startAt: Int = 0, val maxResults: Int = 100)
  data class SearchPage(val total: Int, val startAt: Int, val issues: List<kotlinx.serialization.json.JsonObject>)
  class JiraClient(config: JiraInstanceConfig, http: HttpClient, guard: LiveGuard, retry: RetryPolicy = RetryPolicy()) {
      suspend fun search(req: SearchRequest): SearchPage
  }
  ```
  `search` faz `POST {baseUrl}/rest/api/2/search` com corpo `{jql, maxResults, startAt, fields[], expand[]}`, timeout de 30 s, cabeçalho `Authorization: Bearer <token>` (BEARER) ou `Basic base64(usuario:token)` (BASIC). `fields` vazio é rejeitado com `IllegalArgumentException`. `LiveGuard.check()` é chamado antes de **toda** requisição.

- [ ] **Passo 1: Testes que falham:**
  - `guardBloqueiaSemLive`: cliente com `LiveGuard(false)`: `search` lança `JiraLiveDisabledException` e o `MockEngine` registra **0** requisições.
  - `enviaPostComCorpoEBearer`: a requisição capturada é `POST /rest/api/2/search`, corpo com `jql`, `fields`, `maxResults`, `startAt`, e cabeçalho `Authorization: Bearer abc`.
  - `usaBasicParaSidia`: instância `BASIC` envia `Authorization: Basic ` + base64 de `usuario:token`.
  - `fieldsVazioFalha`: `SearchRequest(jql="x", fields=emptyList())` → `IllegalArgumentException`.
  - `erroPreservaStatus`: resposta 400 com `{"errorMessages":["JQL inválido"]}` → `JiraException(status=400)` com mensagem contendo `JQL inválido`.
  - `retryEm429`: duas respostas 429 e depois 200 → sucesso, 3 requisições; `Retry-After` respeitado (relógio injetável, sem dormir de verdade).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraClientTest*'` → FALHA.
- [ ] **Passo 3: Implementar**; `RetryPolicy(maxRetries=5, backoff: (Int) -> Duration)` com `delay` injetável para testes.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Cliente Jira com trava LiveGuard, retry e erro estruturado"`.

---

### Tarefa 8: Paginação, contagem barata e changelog completo

**Arquivos:**
- Modificar: `jira/JiraClient.kt`
- Teste: `jira/JiraClientPagingTest.kt`

**Interfaces:**
- Consome: Tarefa 7.
- Produz:
  ```kotlin
  fun JiraClient.searchAll(req: SearchRequest, pageSize: Int = 1000): Flow<JsonObject>   // pageSize máx. 5000
  suspend fun JiraClient.count(jql: String): Int                                          // maxResults=1, lê total
  suspend fun JiraClient.fullChangelog(issueKey: String): List<JsonObject>                // GET /rest/api/2/issue/{key}/changelog, paginado
  ```

- [ ] **Passo 1: Testes que falham:**
  - `searchAllPercorreTodasAsPaginas`: `total=2500`, `pageSize=1000` → 3 requisições com `startAt` 0, 1000, 2000; o flow emite 2500 issues.
  - `pageSizeAcimaDeCincoMilFalha`.
  - `countUsaMaxResultsUm`: a requisição tem `maxResults=1` e o método devolve o `total` da resposta.
  - `changelogTruncadoBuscaCompleto`: issue com `changelog.total=120` e 100 históricos → `fullChangelog` faz 2 páginas e devolve 120.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraClientPagingTest*'` → FALHA.
- [ ] **Passo 3: Implementar** loops sequenciais por `startAt` até o `total`.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Paginação, contagem por total e changelog completo no cliente Jira"`.

---

### Tarefa 9: Casca do frontend (temas, idioma, menu por papel, faixa de desenvolvimento)

**Arquivos:**
- Criar: `app/frontend/` (Vite React TS), `src/theme/tokens.css`, `src/theme/ThemeProvider.tsx`, `src/i18n/{pt.json,en.json,index.ts}`, `src/nav/nav.ts`, `src/shell/{Shell.tsx,DevBanner.tsx,Menu.tsx}`, `src/api/client.ts`
- Teste: `src/nav/nav.test.ts`, `src/shell/Shell.test.tsx`

**Interfaces:**
- Produz:
  ```ts
  type Role = 'USUARIO' | 'SQUAD_LEADER' | 'COORDENADOR' | 'GERENTE'
  type NavItem = { id: string; group: GroupId; labelKey: string; roles: Role[] | 'all'; route: string }
  export function visibleNav(role: Role[]): NavGroup[]       // grupos vazios são omitidos
  export function t(key: string, lang: 'pt' | 'en'): string  // chave ausente devolve a própria chave
  ```
  Temas `light | dark | sidia` via `data-theme` na raiz; tokens e valores do plano visual (`docs/design/plano-visual-frontend.md`, seção 3) e dos protótipos em `docs/design/prototipo/`. Persistir tema e idioma em `localStorage` dentro de `try/catch`. Menu com os 8 grupos do plano visual; a casca busca `/api/meta` para a faixa.

- [ ] **Passo 1: Criar o projeto.** `cd app && pnpm create vite frontend --template react-ts && cd frontend && pnpm add react-router-dom @tanstack/react-query && pnpm add -D vitest @testing-library/react @testing-library/jest-dom jsdom`.
- [ ] **Passo 2: Testes que falham:**
  - `usuarioSoVeMeuPainel` (em `nav.test.ts`): `visibleNav(['USUARIO'])` contém `meu` e **não** contém `auditoria` nem `integridade`.
  - `integridadeSoParaGerente`: só `GERENTE` vê `integridade`; `COORDENADOR` vê `auditoria` mas não `integridade`.
  - `gruposVaziosOmitidos`.
  - `chaveAusenteDevolveAChave`: `t('nao.existe','pt') === 'nao.existe'`.
  - `bannerAparece` (em `Shell.test.tsx`): com `/api/meta` simulado `{devMode:true, banner:"MODO DESENVOLVIMENTO"}` o texto aparece com `role="status"`; com `banner:null` não aparece.
  - `trocaDeTemaGravaNaRaiz`: clicar em "Sidia" define `document.documentElement.dataset.theme === 'sidia'`.
- [ ] **Passo 3: Rodar** `cd app/frontend && pnpm vitest run` → FALHA.
- [ ] **Passo 4: Implementar** os módulos acima; reaproveitar o CSS dos protótipos.
- [ ] **Passo 5: Rodar** `pnpm vitest run` → PASSA; `pnpm build` → sem erros.
- [ ] **Passo 6: Commit** `git commit -m "Casca do frontend: temas, idioma, menu por papel e faixa de desenvolvimento"`.

---

### Tarefa 10: Servir a SPA, Docker e verificação ponta a ponta local

**Arquivos:**
- Modificar: `Application.kt` (servir `frontend/dist` em `/` com `fallback` para `index.html`)
- Criar: `app/Dockerfile`, `app/docker-compose.yml` (serviços `painel` e `postgres`; `POSTGRES_PASSWORD` obrigatório, sem senha padrão), `app/.env.example`, `app/README.md`
- Teste: `SpaTest.kt`

- [ ] **Passo 1: Teste que falha.** `SpaTest.servidorEntregaIndexEFallback`: com `frontend/dist/index.html` de teste, `GET /` e `GET /rota-qualquer` devolvem o HTML; `GET /api/inexistente` devolve 404 (não o HTML).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SpaTest*'` → FALHA.
- [ ] **Passo 3: Implementar** a rota estática com `staticFiles` e fallback; diretório vem de `PAINEL_STATIC_DIR` (padrão `frontend/dist`).
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Dockerfile em dois estágios** (build do frontend com pnpm e do backend com Gradle; imagem final só com JRE 21); `docker-compose.yml` com `JIRA_LIVE_ENABLED=false` e `DASHBOARD_DEV_MODE=true` como padrão.
- [ ] **Passo 6: Verificação local.** `cd app && docker compose up --build -d`; depois `curl -s localhost:8080/api/health` → `{"ok":true}` e `curl -s localhost:8080/api/meta` → contém `"liveEnabled":false`. Abrir `http://localhost:8080` e ver a faixa vermelha e o menu. `docker compose down`.
- [ ] **Passo 7: Atualizar `CLAUDE.md`** (nova estrutura `app/` e `legacy/`) e fazer o commit `git commit -m "Servir SPA, Docker e README; atualiza CLAUDE.md"`.

---

## Autoavaliação

- **Cobertura da spec:** 3 (restrições: flags, fuso, trava), 4.2 (fuso, dias úteis), 4.3 e 4.6 (papéis, vigência, filtro por papel, OIDC atrás de interface), 5.3 (cliente: Tarefas 7 e 8), 5.4 (retenção: Tarefa 6; sincronização fica na Fase 3). Dicionário, JQL e auditoria ficam nas Fases 2 e 4.
- **Consistência de tipos:** `Settings` (T2) é consumido em T5 e T10; `Role`/`Principal` (T4) em T5 e no frontend (T9); `JiraClient.search` (T7) é estendido em T8.
- **Pendências deliberadas:** provedor OIDC real; `legacy/` removido só na Fase 8.
