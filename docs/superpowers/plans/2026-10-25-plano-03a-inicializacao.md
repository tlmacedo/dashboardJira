# Inicialização da aplicação (Plano 3A, a "Tarefa 0" do Plano 3) — Plano de implementação

> **Nota (27/10):** executado antes da saída do Docker/PostgreSQL; o banco hoje é SQLite (ver `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`). Onde o texto abaixo diz PostgreSQL, Docker ou Testcontainers, vale o adendo.

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo:** Ligar na subida do app as peças já entregues (calendário, papéis, banco, cliente Jira) e fechar as lacunas apontadas pelas revisões finais dos Planos 1 e 2, para que o Plano 3 (card Tempo em status) só consuma um contexto pronto.

**Arquitetura:** Um `AppContext` imutável é montado por `AppContext.load(settings)` (lê YAML de `configDir`, abre o banco e migra, cria a fábrica do cliente Jira) e injetado no `Application.module`. Testes passam um contexto montado à mão. Um único ponto cria `JiraClient` (`JiraClientFactory`) e é ele que liga o `LiveGuard` ao `Settings`.

**Stack:** igual aos Planos 1 e 2.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.3, 4.6, 5.3, 5.4, 7.1). Origem: relatórios da revisão final dos Planos 1 e 2 (ledgers em `.superpowers/sdd/2026-10-24-plano-0{1,2}-*/progress.md`).

## Restrições globais

- **Nenhuma chamada ao Jira real**: todos os testes usam `MockEngine`; `JIRA_LIVE_ENABLED=false` por padrão. O hook `.claude/hooks/guard.sh` continua valendo; arquivos que citem variáveis de conexão do Jira são criados com Write/Edit, nunca com heredoc no Bash; hook `BLOQUEADO` = parar e relatar.
- Docker sempre ativo (`sh .claude/hooks/ensure-docker.sh`); testes de banco com PostgreSQL real (Testcontainers), nunca H2 ou mock.
- Configs com dados pessoais reais nunca no git (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`.
- Comentários em pt-BR; pacote `br.sidia.painel`; `Principal.roles` SEMPRE derivado do `RoleDirectory` na data de hoje, nunca de claims do token.
- Variáveis de ambiente nunca aparecem em `toString`, log nem mensagem de erro com o valor secreto.

## Foco da revisão

- Subida em produção sem `papeis.yaml` real deve falhar com mensagem clara; em desenvolvimento cai no `.exemplo` com aviso.
- Variável de ambiente vazia (comum em docker-compose) significa "usar o padrão", igual para texto, número e booleano.
- Fábrica do cliente Jira com `JIRA_LIVE_ENABLED=false`: nenhuma credencial exigida e nenhum cliente consegue enviar requisição.
- Resposta do Jira sem `total`, página vazia e laço sem fim; falha de rede e timeout viram `JiraException`.
- Vigências de `USUARIO` sobrepostas com squads diferentes no mesmo dia: falha no carregamento.

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  config/Settings.kt                  # DATABASE_USER/PASSWORD, "vazio = padrão", instâncias do Jira, toString seguro
  AppContext.kt                       # carga de calendário, papéis, banco, fábrica Jira
  Application.kt                      # module(settings, context, authenticator) e main com encerramento limpo
  security/Authenticator.kt           # principalFor(userId, directory, today)
  security/RoleDirectory.kt           # validação de vigências sobrepostas e de ids (username do Jira; padrão por cargo abandonado)
  jira/JiraClientFactory.kt           # única criação de JiraClient
  jira/JiraClient.kt                  # correções (total ausente, teto de páginas, rede, timeout, JSON)
  jql/JqlBuilder.kt, CardQueryFactory.kt
app/backend/src/main/resources/logback.xml
.claude/hooks/guard.sh, test-guard.sh # cobrir todas as variáveis JIRA_*
```

---

### Tarefa 1: `Settings` — banco, instâncias do Jira, "vazio = padrão" e `toString` seguro

**Arquivos:** Modificar `config/Settings.kt`, `.claude/hooks/guard.sh`, `.claude/hooks/test-guard.sh`, `app/.env.example`, `app/docker-compose.yml`; Teste `config/SettingsTest.kt`.

**Interfaces:**
- Produz, em `Settings`: `databaseUser: String`, `databasePassword: String` (variáveis `DATABASE_USER` e `DATABASE_PASSWORD`), `jiraInstances: Map<String, JiraInstanceSettings>` com `data class JiraInstanceSettings(val baseUrl: String, val auth: JiraAuthKind, val username: String?, val token: String)` (variáveis `JIRA_SAMSUNG_URL`, `JIRA_SAMSUNG_TOKEN` para a instância `samsung` com `BEARER`; `JIRA_SIDIA_URL`, `JIRA_SIDIA_USER`, `JIRA_SIDIA_TOKEN` para `sidia` com `BASIC`). Uma instância só entra no mapa quando TODAS as suas variáveis estão preenchidas; se algumas estão e outras não, `IllegalArgumentException` citando as variáveis que faltam (sem mostrar valores).
- `toString()` de `Settings` e de `JiraInstanceSettings` NUNCA contém `databasePassword` nem `token` (substituídos por `***`) e mascara usuário/senha embutidos em `databaseUrl` (por exemplo `jdbc:postgresql://u:p@h/db`).
- `Settings.fromEnv`: variável presente mas VAZIA ou só espaços = padrão, para texto, número, booleano, caminho e fuso (hoje booleano e número vazio falham).

- [ ] **Passo 1: Testes que falham** (`SettingsTest`): `vazioUsaPadraoParaTodos` (`JIRA_LIVE_ENABLED=""`, `RETENTION_MONTHS="  "`, `PAINEL_TZ=""`, `PAINEL_CONFIG_DIR=""`, `DATABASE_URL=""`, `PAINEL_STATIC_DIR=""` → padrões; revise o teste atual que esperava falha para `PAINEL_STATIC_DIR` vazio); `lePAINELBanco` (usuário e senha); `toStringNaoVazaSegredos` (senha, token e `u:p@` somem do texto); `instanciaSamsungCompleta` e `instanciaSidiaCompleta` entram no mapa; `instanciaIncompletaFalha` (só URL: mensagem cita a variável que falta e não mostra valor); `semInstanciasMapaVazio`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar** em `Settings.kt` (nova classe `JiraInstanceSettings` no mesmo pacote; `JiraAuthKind` já existe em `jira/`).
- [ ] **Passo 4: Hook:** `guard.sh` modo `jira` passa a barrar QUALQUER `JIRA_[A-Z0-9_]*` (hoje só `JIRA_(BASE_URL|TOKEN|EMAIL|SIDIA)`), mantendo liberados `JIRA_LIVE_ENABLED=false`? Não: `JIRA_LIVE_ENABLED` já é barrado com valor `1|true` em outra regra; decida e teste em `test-guard.sh` (`echo $JIRA_SAMSUNG_TOKEN` bloqueia; `ls` libera; `JIRA_LIVE_ENABLED=false ./gradlew test` libera).
- [ ] **Passo 5: `app/.env.example` e `docker-compose.yml`:** acrescente as novas variáveis SEM valores (comentadas em pt-BR); no compose, `DATABASE_USER` e `DATABASE_PASSWORD` vêm do ambiente; as variáveis do Jira ficam fora do compose por padrão.
- [ ] **Passo 6: Rodar** os testes de `Settings` e `sh .claude/hooks/test-guard.sh` → PASSAM. **Commit** `git commit -m "Settings com banco, instâncias do Jira, vazio = padrão e toString seguro"`.

---

### Tarefa 2: Cliente Jira — fábrica única e correções

**Arquivos:** Criar `jira/JiraClientFactory.kt`; Modificar `jira/JiraClient.kt`, `build.gradle.kts`/`libs.versions.toml` (remover `ktor-client-content-negotiation` se sobrar); Teste `jira/JiraClientFactoryTest.kt`, `jira/JiraClientRobustezTest.kt`.

**Interfaces:**
- Produz `class JiraClientFactory(private val settings: Settings, private val http: (JiraInstanceConfig) -> HttpClient = ::createJiraHttpClient)` com `fun create(instanceId: String): JiraClient`:
  - cria o `LiveGuard(settings.jiraLiveEnabled)` e passa ao cliente; é o ÚNICO ponto que constrói `JiraClient` no código de produção;
  - com `jiraLiveEnabled=false`: não exige credencial; devolve um cliente que não envia nada (qualquer chamada lança `JiraLiveDisabledException`);
  - com `jiraLiveEnabled=true`: exige a instância configurada (`IllegalStateException` citando só o id), `baseUrl` com `https` (ou `http` apenas para `localhost`/`127.0.0.1`), `token` não vazio e, para `BASIC`, `username` sem `:`;
  - instância desconhecida: `IllegalArgumentException` com o id.
- Correções em `JiraClient`:
  - resposta de busca SEM o campo `total` → `JiraException` (nunca assume 0), em `search`, `searchAll`, `count` e `fullChangelog`;
  - `searchAll` com teto de páginas (`MAX_PAGINAS = 10_000 / pageSize` arredondado para cima, no mínimo 1) → `JiraException` ao estourar; `fields` vazio falha NA CHAMADA de `searchAll` (hoje só na coleta);
  - falhas de rede (`IOException`, timeout de conexão/leitura) são repetidas pela política de retry e, esgotadas, viram `JiraException(status = 0)` com mensagem sem o token; instale `HttpTimeout` (connect 10 s, request 30 s) na fábrica `createJiraHttpClient`;
  - resposta 200 que não é JSON (por exemplo página de login) → `JiraException` com o status e um resumo saneado, não `SerializationException`;
  - `createJiraHttpClient` não instala `ContentNegotiation` à toa.

- [ ] **Passo 1: Testes que falham** (só `MockEngine`): `liveDesligadoNaoExigeCredencialENaoEnvia` (0 requisições, `JiraLiveDisabledException`); `liveLigadoExigeHttps`; `httpSoEmLocalhost`; `tokenVazioFalha`; `basicSemUsuarioFalha`; `instanciaDesconhecidaFalha`; `totalAusenteViraErro` (search, searchAll, count, fullChangelog); `tetoDePaginas` (servidor que sempre devolve página cheia e `total` crescente → `JiraException`); `fieldsVazioFalhaNaChamadaDeSearchAll`; `falhaDeRedeEhRepetidaEViraJiraException` (MockEngine lança `IOException` em 3 tentativas e sucesso na 4ª; e esgotando as tentativas → `JiraException(0)` sem token na mensagem); `respostaNaoJsonViraJiraException`; `guardChecadoAntesDeCadaTentativaDoRetry` (guard desliga no meio via `LiveGuard` injetável de teste: nenhuma requisição após o desligamento).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraClient*'` → FALHA.
- [ ] **Passo 3: Implementar** a fábrica e as correções; manter todos os testes anteriores de `JiraClientTest` e `JiraClientPagingTest`.
- [ ] **Passo 4: Rodar** → PASSA. **Commit** `git commit -m "Fábrica única do cliente Jira e correções de robustez"`.

---

### Tarefa 3: `AppContext`, papéis derivados do diretório e subida do app

**Arquivos:** Criar `AppContext.kt`, `logback.xml`; Modificar `Application.kt`, `security/Authenticator.kt`, `security/RoleDirectory.kt`; Teste `AppContextTest.kt`, `security/RoleDirectoryValidacaoTest.kt`, `security/PrincipalTest.kt`.

**Interfaces:**
- Produz `class AppContext(val settings: Settings, val calendar: BusinessCalendar, val roles: RoleDirectory, val dataSource: javax.sql.DataSource?, val jira: JiraClientFactory) : AutoCloseable` e `companion object { fun load(settings: Settings, clock: Clock = Clock.system(settings.zone)): AppContext }`:
  - lê `configDir/calendario.yaml` e `configDir/papeis.yaml`; se o arquivo real não existe: em `dashboardDevMode=true` cai em `calendario.exemplo.yaml` / `papeis.exemplo.yaml` com `log.warn` explicando; em produção (`dashboardDevMode=false`) falha com `IllegalStateException` citando o caminho esperado;
  - abre o banco com `Database.connect(settings.databaseUrl, settings.databaseUser, settings.databasePassword)` e roda `Database.migrate`; falha de conexão: `IllegalStateException` com mensagem sem senha (em qualquer modo; o app não sobe sem banco); com `dashboardDevMode=false` e `databasePassword` vazio a subida FALHA com `IllegalStateException` ("DATABASE_PASSWORD obrigatório em produção", sem valor); em modo desenvolvimento só avisa (achado da revisão da Tarefa 1; teste `producaoComSenhaVaziaFalha` e `devComSenhaVaziaApenasAvisa`);
  - `close()` fecha o pool (`HikariDataSource`).
- `fun Application.module(settings: Settings = Settings.fromEnv(), context: AppContext, authenticator: Authenticator = ...)`: usa `context.roles`/`context.calendar` onde precisar; `main()` cria o contexto com `AppContext.load(Settings.fromEnv())` e registra encerramento (`Runtime.addShutdownHook` ou o evento `ApplicationStopped`) que chama `context.close()`. Os testes existentes (`HealthTest`, `MetaTest`, `AccessControlTest`, `SpaTest`) passam a montar um contexto de teste sem banco (`dataSource = null`) por uma função auxiliar `testContext(settings)` em `src/test/kotlin/br/sidia/painel/TestContext.kt`.
- `fun principalFor(userId: String, directory: RoleDirectory, today: LocalDate): Principal` em `security/Authenticator.kt`: papéis SEMPRE de `directory.rolesOf(userId, today)`; usuário desconhecido recebe `setOf(Role.USUARIO)` apenas se o `DevAuthenticator`/provedor decidir; a função em si devolve conjunto vazio para id desconhecido (fail-closed). `DevAuthenticator` usa `principalFor("dev", ...)` somente se existir no diretório; senão mantém o `Principal("dev", {GERENTE})` de desenvolvimento (documentado em pt-BR) — nunca em produção.
- `RoleDirectory.fromYaml` passa a validar no carregamento: (a) o id como username do Jira (regra de `JqlValues.username`; o padrão antigo `ge|co|sl|us-NNN` foi abandonado, e `dev`, `system`, `admin`, `root`, `anonymous` são reservados); (b) vigências de `USUARIO` com squads diferentes que se sobrepõem em qualquer dia → `IllegalArgumentException` citando o id e o intervalo; (c) `SQUAD_LEADER` e `USUARIO` sem squad onde é obrigatório.
- `logback.xml` com nível `INFO` (e `WARN` para bibliotecas ruidosas), sem imprimir cabeçalhos ou corpos de requisição.

- [ ] **Passo 1: Testes que falham:** `producaoSemPapeisFalha`; `devSemPapeisUsaExemploComAviso`; `producaoSemCalendarioFalha`; `bancoIndisponivelFalhaSemSenhaNaMensagem` (URL inválida/porta fechada, mensagem não contém a senha); `carregaComBancoRealETestcontainers` (PostgreSQL real: contexto sobe, migra e `close()` fecha o pool); `principalVemDoDiretorio` (id conhecido → papéis do diretório na data; id desconhecido → vazio; papel encerrado ontem não vale hoje); `idForaDoPadraoFalha`; `vigenciaUsuarioSobrepostaFalha` e `vigenciaUsuarioAdjacenteOk` (pontas inclusivas: 30/06 e 01/07 não se sobrepõem); `moduloSobeComContextoDeTeste`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AppContextTest*' --tests '*RoleDirectoryValidacaoTest*' --tests '*PrincipalTest*'` → FALHA.
- [ ] **Passo 3: Implementar**; atualizar os testes existentes para `testContext`.
- [ ] **Passo 4: Rodar** a suíte completa (`./gradlew cleanTest test`; confira a contagem pelos XML em `build/test-results/test/`) → PASSA. **Commit** `git commit -m "AppContext na subida, papéis derivados do diretório e validações de papéis"`.

---

### Tarefa 4: Minor do Plano 2 que o Plano 3 consome

**Arquivos:** Modificar `jql/JqlBuilder.kt`, `jql/CardQueryFactory.kt`, `app/config/cards/tempo-em-status.card.yaml`, `docs/jira/matriz-compatibilidade.md`; Teste `jql/JqlBuilderTest.kt`, `jql/CardQueryFactoryTest.kt`, `jql/PreparacaoDocsTest.kt`.

- [ ] **Passo 1: Testes que falham:** `inListEmCampoUserSaiComAspas` (`inList("assignee", ["x"])` → `assignee in ("x")`; também `notInList` e `equalsTo`); `fieldsUsaCustomfieldNoLugarDeNomeECfN` (campo com `customId: cf[12416]` aparece em `fields` como `customfield_12416`; campo sem `customId` aparece pelo nome; sem duplicatas); testes de tipo de campo para `os` e `tipo` (campo TEXT/DATE apontado por esses parâmetros falha na carga da ficha); `orderByRepetidoFalha` (`IllegalArgumentException` citando o campo); `PreparacaoDocsTest` por seção: cada passo `### Passo` do checklist contém "autorização expressa", nenhum documento contém `https?://` com host real nem `Bearer ` seguido de valor, e a matriz tem a linha de `fields` por id.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*jql*'` → FALHA.
- [ ] **Passo 3: Implementar**; corrigir o comentário da ficha `tempo-em-status.card.yaml` (linha que diz `resolved <= ate` passa a dizer `resolved < dia seguinte (ate é inclusivo)`); trocar nomes de pessoas reais restantes no texto histórico de `docs/superpowers/plans/2026-10-24-plano-02-dicionario-e-jql.md` por `dev.alfa`, `dev.beta`, `dev.gama`; atualizar a matriz com a linha de `fields` por id.
- [ ] **Passo 4: Rodar** a suíte completa → PASSA. **Commit** `git commit -m "Minor do Plano 2: usernames com aspas em USER, fields por id, orderBy único e testes"`.

---

## Autoavaliação

- **Cobertura das revisões finais:** Plano 1 (I1 URL absoluta já corrigida; I2 LiveGuard ligado ao Settings = Tarefa 2; I3 bootstrap/banco/papéis/calendário = Tarefa 3; DATABASE_USER/PASSWORD, toString, vazio = padrão, logback = Tarefas 1 e 3; Minor `total` ausente, teto, falhas de rede, JSON, https/token = Tarefa 2; vigências sobrepostas e `Principal` do diretório = Tarefa 3). Plano 2 (Minor consumidos pelo Plano 3 = Tarefa 4).
- **Ficam para depois:** acessibilidade e CSP (Fase 8), JQL tipado em `SearchRequest` (Plano 3, quando o `Syncer` existir), provedor OIDC real, digest de imagens.
