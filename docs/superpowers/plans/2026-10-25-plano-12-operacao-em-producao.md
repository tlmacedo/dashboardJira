# Fase 12: Operação em produção — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.
>
> **Plataforma (adendo de 27/10):** SQLite em arquivo, sem Docker, sem PostgreSQL, sem Testcontainers (`docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`; ele vence qualquer trecho antigo). Este plano foi reescrito em 27/10 para essa realidade: o painel roda como processo/serviço do sistema operacional (pacote `./gradlew installDist` + `pnpm build`, definido no Plano 13) em Windows 11 e/ou Linux.

**Objetivo (Goal):** Dar ao painel o que a operação diária precisa sem depender de quem escreveu o código: logs estruturados sem segredos (na saída do processo e, se configurado, em arquivo com rotação), verificação de prontidão (readiness) que confere o arquivo do banco, as migrações, o espaço em disco e a trava de processo, métricas numa porta interna, alertas operacionais com saída em texto para arquivo e log (sincronização suspensa ou atrasada, reconciliação suspeita, retenção, registro de acesso falhando, PAT perto de vencer, calendário do ano seguinte, disco baixo, banco suspeito, backup atrasado, restauração não testada), validador de configuração antes da subida, backup agendado do SQLite (cópia consistente, conferida, com retenção e restauração testada de forma automática), rotação de segredos e os runbooks de operação diária e de incidentes para Windows (PowerShell) e `sh`.

**Arquitetura (Architecture):** Tudo dentro do processo atual, sem serviço novo e sem ferramenta externa. Os logs saem em JSON por um encoder próprio do Logback que mascara segredos antes de escrever; como o Logback inicia antes de o painel ler o arquivo de variáveis, um `ConfiguradorDeLog` reaplica a configuração no começo do `main`. Um segundo conector do Netty, numa porta interna (padrão `127.0.0.1:9464`), serve só `/metrics` (Micrometer + Prometheus). Um `AvaliadorDeAlertas` puro lê o estado já existente (`sync_run`, `sync_state`, contadores, configuração, calendário, disco e a tabela nova `backup_run`) e alimenta quatro saídas: a rota do gerente, uma métrica por alerta, uma linha no log e uma linha no arquivo de alertas (`SaidaDeAlertas`), todas só na mudança de estado. O backup é um `ServicoDeBackup` Kotlin: `VACUUM INTO` para um arquivo novo numa pasta configurável fora do repositório, conferência (`PRAGMA integrity_check`, `PRAGMA foreign_key_check`, versão do esquema e SHA-256), renomeação atômica, retenção das N cópias mais recentes e registro em `backup_run`. Ele roda por uma `TarefaDeOperacao` no próprio `Agendador` (sem cron nem Agendador de Tarefas do SO) e, logo depois de cada cópia, o `VerificadorDeRestauracao` restaura o arquivo num diretório temporário, confere integridade, migrações e contagens por tabela, e apaga o temporário. O mesmo binário ganha subcomandos (`validar-config`, `backup-agora`, `verificar-restauracao`) que não sobem o servidor.

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1 (`ktor-server-call-id`, `ktor-server-call-logging`, `ktor-server-metrics-micrometer`, todos na versão do Ktor), Micrometer com registro Prometheus (versão fixa resolvida na execução), Logback 1.5.20, SQLite (`sqlite-jdbc` 3.50.3.0, já no build) + Flyway 11.14.0 + HikariCP 7.0.2 (já no build); React 19 + TypeScript só no aviso de alertas; PowerShell e `sh` só nos scripts finos e nos runbooks. Nenhuma dependência de Docker, PostgreSQL ou Testcontainers.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-12-operacao-em-producao.md`. **Spec mestra:** seções 4.6 ("Produção × desenvolvimento", "Volume e atualidade", "Ambiente"), 5.4 (retenção), 11. **Auditoria:** `docs/superpowers/cobertura-do-projeto.md` (lacunas A1, A2 e A7; B.5 backup, observabilidade, configuração administrativa e runbooks). **Implantação:** `docs/implantacao.md` (Plano 8, T7 reescrito) e o Plano 13.

**Quando executar:** depois do Plano 8 mesclado (e, portanto, dos Planos 9, 10 e 11).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que o Plano 8 foi mesclado e que estes nomes existem. Se algum mudou, atualizar só os nomes deste plano e registrar `Ruling:` no progresso. Os itens da coluna "Hoje" já foram conferidos no código em 27/10.

| Nome | Origem | Hoje |
|---|---|---|
| `main()`, `Application.module(settingsIgnorado, context, authenticator, agendador)`, `embeddedServer(Netty, port = 8080)`; o arquivo de variáveis é lido por `ArquivoEnv.ler` (`PAINEL_ENV_FILE` ou `./.env`) e combinado por `ArquivoEnv.combinarComFlagSoDoArquivo` | `Application.kt`, `config/ArquivoEnv.kt` | existe |
| `AppContext.load(settings, clock)`, `AppContext.close()`, `aoFechar(...)`; os carregadores dos YAML (`resolver`, `lerYaml`, `exigirGerenteVigente`, `carregarFichas`, ainda privados) e os modelos `RoleDirectory`, `StatusMap`, `BusinessCalendar`, `LabelsDeAtraso`, `RegrasDeAuditoria`, `FieldDictionary`, `VinculosConfig`, fichas | `AppContext.kt` e Planos 1 a 7 | existe (os de equipes, ausências e regras dos Planos 5 a 7 podem ainda não existir: o validador cobre o que existir) |
| `Database.urlDeArquivo`, `Database.connect(url, poolSize)`, `Database.migrate(ds)`; pragmas por conexão (WAL, `busy_timeout` 15 s, `foreign_keys`, `synchronous=NORMAL`, `transaction_mode=IMMEDIATE`) | `db/Database.kt` | existe |
| `TravaDeProcesso.adquirir(arquivoDoBanco)` (trava de SO em `<banco>.lock`); `AppContext` guarda a trava em `travaDeProcesso: AutoCloseable?` | `db/TravaDeProcesso.kt`, `AppContext.kt` | existe |
| `Settings.fromEnv`, `Settings.mascararUrl`, `SyncSettings`, `jiraInstances` (`JiraInstanceSettings.token`), `databasePath`, `configDir`, `zone`, `retentionMonths`, `accessLogRetentionMonths`; `Settings.toString` sem segredos | `config/Settings.kt` | existe |
| `SyncStateStore` (`ultimaPorTipo(instance?)`, `suspensa`, `cursor`, `falhasSeguidas`, `fecharOrfas`), `SqliteSyncStateStore`, `RunStatus`, `RegistroDeExecucao`, `INSTANCIA_RETENCAO`, `SyncKind` (`INCREMENTAL`, `RECONCILIACAO`, `RETENCAO`; o `CHECK` de `sync_run` fecha nesses três) | `sync/SyncStateStore.kt`, `sync/SyncGate.kt` | existe |
| `Agendador(tarefas, estado, clock, passo, dormir)`, `TarefaAgendada`, `Agenda.diariaDevida`, `montarAgendador(ctx, clock, dormir)` (retenção sempre; tarefas do Jira só com a flag ligada) | `sync/Agendador.kt` | existe |
| `AccessLog.record`, `AccessLogException`, `Retention(ds, calendar).purge(today, months, accessLogMonths)` (lista `PURGAS`) | `db/AccessLog.kt`, `db/Retention.kt` | existe |
| `BusinessCalendar.movableHolidays: Set<LocalDate>`, `fixedHolidays` | `time/BusinessCalendar.kt` | existe |
| `rotasSyncStatus(ctx, estado, auditoria)` (papel conferido dentro da rota, toda chamada vai para o `access_log`, sem registro responde 503); `rotasPublicas` (conjunto privado de segmentos em `security/AccessControl.kt`, hoje `api/health` e `api/meta`) | `sync/SyncStatusRoutes.kt`, `security/AccessControl.kt` | existe |
| `BancoEmArquivo`, `testContextComBanco(settings, banco, ...)`, `Database.connect(Database.urlDeArquivo(banco.novoArquivo()))` + `Database.migrate(ds)` | `src/test/.../TestContextComBanco.kt` | existe |
| Nome do pacote: `rootProject.name = "painel"`, então `./gradlew installDist` gera `build/install/painel/bin/painel` (e `painel.bat`) | `settings.gradle.kts` | existe |
| `Sessao.kt`, `OidcSettings`, cookie `painel_sessao`, `SESSION_SECRET` (≥ 32 bytes em base64) | Plano 8, T2 | confirmar |
| `GeradorSintetico` (dados sintéticos, agora sobre SQLite), `ROTAS_PUBLICAS` e `inventarioConhecido` (`seguranca/InventarioDeRotasTest.kt`) | Planos 8 (T1, T6) e 9 | confirmar |
| `docs/implantacao.md` (pacote e serviço), escrito pelo Plano 8 (T7 reescrito) e completado pelo Plano 13 | Planos 8 e 13 | confirmar |
| `.github/workflows/ci.yml` (só JDK 21 e Node 22/pnpm), `CiWorkflowTest`, `ErroDto`, `CodigosDeMensagem`, `EstadoDaTela` | Planos 9 e 10 | confirmar |
| Último número de migração na `main` | `resources/db/migration/` (hoje só `V1__base.sql`) | confirmar com `ls` |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`). Só o usuário muda a flag, e ela vale somente no `app/.env`. Este plano não acessa o Jira: nenhum alerta, métrica, validador, backup ou script faz requisição ao Jira. O alerta de PAT usa só a **data informada pelo operador** (`SYNC_PAT_VALIDADE_<INSTANCIA>`); nunca consulta o Jira para descobrir a validade. Nenhum teste deste plano liga a flag no ambiente real: testes usam `Settings` montado em memória e `MockEngine` contador.
- **Plataforma:** sem Docker, contêiner, imagem, PostgreSQL, H2, `pg_dump` ou Testcontainers (adendo, seção 4). O que o plano antigo fazia com contêiner está nos equivalentes da seção 5 do adendo. Se alguma tarefa pedir algo proibido, aplicar a seção 5 e registrar `Ruling:`.
- **Rede:** permitida só para baixar dependências Gradle listadas no Stack. **Nunca** para o Jira, para um provedor OIDC real nem para um destino de alerta ou de backup real nos testes.
- **Logs e métricas sem segredos nem dados pessoais:** nada de token, `SESSION_SECRET`, cookie, cabeçalho `Authorization`, query string, JQL, chave de issue, username, caminho de arquivo ou texto livre em log de servidor e em rótulo de métrica. O `access_log` (tabela) continua sendo o único registro de quem consultou o quê.
- **Métricas só na porta interna:** `/metrics` nunca responde na porta 8080.
- **Readiness pública mínima:** responde só `{"pronto": true|false, "codigo": ...}`, sem versão, host, caminho do banco ou detalhes.
- **Backup contém dados pessoais:** arquivos com permissão `600` onde o sistema suportar (POSIX; no Windows, pasta com acesso só da conta do serviço, ver o runbook), pasta **fora do repositório** e fora da pasta do pacote, nunca versionada; nenhum teste envia backup para fora da máquina. O painel não cifra: a cifragem em repouso é do volume (BitLocker no Windows, LUKS no Linux), ver ponto de parada 2.
- **Uma só escrita por vez:** o backup usa `VACUUM INTO` (leitura consistente em WAL) e nunca segura transação de escrita aberta; a verificação da restauração trabalha só em arquivo temporário, nunca no banco vivo.
- Variáveis novas começam com `PAINEL_`, `DASHBOARD_`, `SYNC_`, `SESSION_` ou `BACKUP_` (nunca `JIRA_`); nenhuma liga a sincronização; todas entram em `app/.env.example` comentadas e sem valor de segredo.
- **Migração:** uma, `V{n}__execucao_de_backup.sql`, com `n` = próximo número livre no momento da execução (hoje seria `V2`, mas os planos 4A a 11 podem ter criado outras antes: confira `ls app/backend/src/main/resources/db/migration`). Nunca editar migração mesclada.
- **Hook:** o hook barra a IA de editar certos arquivos de configuração do Claude Code e de ambiente. Nenhuma tarefa deste plano precisa deles; se algum passo esbarrar no hook, entregar o trecho pronto ao usuário e perguntar (pergunta com opções).
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(operacao): ...`, `build(operacao): ...`, `docs(operacao): ...`). Dados de exemplo e testes só com nomes fictícios (`ana.silva`, `bruno`) e domínio `.invalid`.

## Foco da revisão (Review Focus)

1. **Exceção com token na mensagem** (o driver ou o cliente HTTP ecoa a URL com credencial ou um cabeçalho): o log JSON sai com `***`, e a quebra de linha no texto não cria uma segunda entrada → `excecaoComTokenSaiMascarada` e `quebraDeLinhaNaoCriaEntrada` (T1).
2. **Variáveis de log só chegam depois do Logback:** o formato e a pasta de log vêm do `app/.env`, lido depois de o Logback iniciar; sem o `ConfiguradorDeLog` o valor seria ignorado em silêncio → `configuradorAplicaFormatoTexto` e `configuradorGravaArquivoComRotacao` (T1).
3. **Banco inacessível, mas o processo está vivo:** `/api/health` continua 200 (liveness) e `/api/health/pronto` responde 503 em até 3 s, sem travar nem esperar o pool (a sonda abre conexão própria com `busy_timeout` curto) → `prontoDetectaBancoIndisponivelEmAte3s` e `prontoNaoEsperaOPoolEsgotado` (T2).
4. **Disco cheio e trava perdida** derrubam a prontidão com código próprio → `discoBaixo503` e `travaPerdida503` (T2).
5. **`/metrics` pedido na porta pública:** 404 → `metricasNaoRespondemNaPortaPublica` (T4).
6. **Sincronização parada sem ninguém abrir a tela** (suspensa por 401 numa sexta à noite): o alerta `SYNC_SUSPENSA` aparece na rota, na métrica, no log e no arquivo de alertas uma única vez por mudança de estado → `suspensaoViraAlertaUmaVez` (T5).
7. **YAML real com erro:** o validador aponta arquivo e código **sem imprimir o valor** (que pode ser nome de pessoa) e sai com 1 → `erroSemValorNoTexto` (T3).
8. **Backup que "roda" mas não restaura** (cópia truncada ou corrompida): a cópia só vira `.db` e `OK` depois de `integrity_check`, `foreign_key_check`, versão do esquema e SHA-256; arquivo parcial nunca fica na pasta → `copiaCorrompidaNaoViraOk` e `parcialNaoSobra` (T7), `restauracaoIgualAoOriginal` (T8).
9. **Retenção apagando o que não é backup:** só arquivos com o nome exato `painel-aaaa-MM-dd-HHmmss.db` são candidatos, e nunca o único `OK` mais recente → `retencaoSoApagaNomesDeBackup` e `retencaoPreservaUltimoOk` (T7).
10. **Restauração com resíduos do WAL:** restaurar por cima de um banco com `-wal`/`-shm` antigos corrompe; o runbook manda parar o serviço e mover os três arquivos → `restauracaoCitaWalEShm` (T10/T11).

---

## Arquivos (mapa)

```
app/backend/src/main/resources/{logback.xml, db/migration/V{n}__execucao_de_backup.sql}
app/backend/src/main/kotlin/br/sidia/painel/
  operacao/{EncoderJsonSeguro.kt, MascaraDeLog.kt, ConfiguradorDeLog.kt, Prontidao.kt, VerificacaoDeDisco.kt}
  operacao/{Metricas.kt, ServidorDeMetricas.kt, OperacaoSettings.kt, ComandosDeOperacao.kt}
  operacao/{AvaliadorDeAlertas.kt, Alerta.kt, AlertasRoutes.kt, SaidaDeAlertas.kt, LacoDeAlertas.kt, BackupRunStore.kt}
  operacao/{ValidadorDeConfig.kt, ServicoDeBackup.kt, VerificadorDeRestauracao.kt}
  sync/Agendador.kt (TarefaDeOperacao), db/{Database.kt, Retention.kt, TravaDeProcesso.kt}
  security/{Sessao.kt, AccessControl.kt}, config/Settings.kt, AppContext.kt, Application.kt
app/backend/gradle/libs.versions.toml, app/backend/build.gradle.kts
app/frontend/src/shell/{AvisoDeAlertas.tsx, AvisoDeAlertas.test.tsx}, app/frontend/src/api/client.ts, i18n
app/scripts/{painel-cli.sh, painel-cli.ps1}
ops/prometheus/alertas.yml   (opcional, só se a infraestrutura tiver Prometheus)
.github/workflows/ci.yml (validação das configurações de exemplo)
docs/operacao/{configuracao.md, observabilidade.md, backup-e-restauracao.md, runbook-diario.md, runbook-incidentes.md, rotacao-de-segredos.md}
docs/implantacao.md, app/README.md, app/.env.example, HANDOFF.md
```

---

### Tarefa 1: Logs estruturados sem segredos, com id de requisição

**Arquivos:** Criar `operacao/EncoderJsonSeguro.kt`, `operacao/MascaraDeLog.kt`, `operacao/ConfiguradorDeLog.kt`; Modificar `resources/logback.xml`, `Application.kt` (`CallId` e `CallLogging` no `module`; `ConfiguradorDeLog.aplicar(...)` logo depois de ler o arquivo de variáveis em `main`; registro dos segredos na máscara), `config/Settings.kt` (`fun valoresSecretos(): List<String>`: tokens de `jiraInstances` e, quando existirem, os segredos do OIDC e da sessão do Plano 8), `gradle/libs.versions.toml` e `build.gradle.kts` (`ktor-server-call-id`, `ktor-server-call-logging` em `ktor = "3.3.1"`), `app/.env.example` (`PAINEL_LOG_FORMATO`, `PAINEL_LOG_DIR`); Teste `operacao/MascaraDeLogTest.kt`, `operacao/EncoderJsonSeguroTest.kt`, `operacao/ConfiguradorDeLogTest.kt`, `operacao/LogDeRequisicaoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  object MascaraDeLog {
      fun registrarSegredos(valores: Collection<String>)   // tokens do Jira, SESSION_SECRET(_ANTERIOR), OIDC_CLIENT_SECRET, webhook de alerta; ignora < 8 caracteres
      fun mascarar(texto: String): String                  // segredos registrados (também em forma URL, JSON e base64, como o JiraClient) + padrões abaixo
  }
  class EncoderJsonSeguro : EncoderBase<ILoggingEvent>()   // uma linha JSON por evento, UTF-8
  object ConfiguradorDeLog {
      /** formato: "JSON" (padrão) ou "TEXTO"; valor inválido cai em JSON. dir: pasta do arquivo de log ou null (só saída padrão). */
      fun aplicar(formato: String?, dir: Path?)
  }
  ```
  Padrões mascarados (sempre, mesmo sem registro): `Bearer <x>`, `Basic <x>`, `Authorization: <x>`, `(password|senha|secret|token|pat|apikey|api_key)\s*[=:]\s*<x>`, credenciais em URL (`//<usuario>:<senha>@` → `//***@`), `Cookie:`/`Set-Cookie:` com `painel_sessao=<x>`. A máscara troca só o valor por `***`.
- Formato do evento: `{"ts":"<ISO-8601 UTC>","nivel":"INFO","logger":"...","msg":"<mascarado>","requisicao":"<id>"?,"exc":{"classe":"...","msg":"<mascarado>","pilha":["Classe.metodo(Arquivo.kt:12)", ...até 20]}?}`. Strings escapadas como JSON (CR, LF e aspas viram escapes; nada quebra a linha).
- `logback.xml`: `<appender name="JSON">` (`ConsoleAppender` com `EncoderJsonSeguro`), `<appender name="TEXTO">` (o padrão atual, passando pela mesma máscara por um `MessageConverter` próprio) e `<appender name="ARQUIVO">` (`RollingFileAppender`, `SizeAndTimeBasedRollingPolicy`, `painel.log` em `${PAINEL_LOG_DIR}`, 20 MB por arquivo, 14 dias de histórico, teto total de 500 MB, mesmo encoder do formato escolhido). Os `appender-ref` do `root` usam `${PAINEL_LOG_FORMATO:-JSON}` e `${PAINEL_LOG_ARQUIVO_REF:-NADA}` (`NADA` = `ch.qos.logback.core.helpers.NOPAppender`). **O Logback inicia antes de o painel ler o `app/.env`** (o `private val log` de nível de arquivo em `Application.kt` é criado antes de o `main` rodar): por isso `ConfiguradorDeLog.aplicar` grava as duas propriedades no `LoggerContext` (`putProperty`), faz `reset()` e relê o `logback.xml` com o `JoranConfigurator`. O `main` chama `aplicar` assim que combina ambiente e arquivo; as poucas linhas anteriores saem no formato padrão (JSON). Sem `PAINEL_LOG_DIR`, `PAINEL_LOG_ARQUIVO_REF` fica `NADA` e nada é gravado em arquivo. Falha ao criar a pasta de log: aviso na saída padrão e o painel continua só com a saída padrão.
- `CallId`: aceita `X-Request-Id` do proxy só se casar `^[A-Za-z0-9-]{8,64}$`; senão gera UUID; devolve no cabeçalho `X-Request-Id`; põe no MDC como `requisicao`.
- `CallLogging` (nível INFO): uma linha por requisição com `metodo`, `rota` (modelo da rota do Ktor, por exemplo `/api/tarefa/{key}`; sem rota casada → `desconhecida`), `status`, `duracaoMs` e `requisicao`. **Sem** query string, usuário, cabeçalhos ou corpo. `/api/health*` e estáticos em DEBUG (fora do INFO).

- [ ] **Passo 1: Testes que falham:**
  - `MascaraDeLogTest`: `bearerEBasicMascarados`; `senhaEmChaveValorMascarada` (`password=abc12345` e `senha: abc12345`); `credencialEmUrlMascarada` (`https://u:p@h.invalid/x` → `https://***@h.invalid/x`); `segredoRegistradoMascaradoTambemEscapado` (token registrado aparece em `%2F` e `\/` → `***`); `cookieDeSessaoMascarado`; `textoComumIntacto`; `valorSecretoDoSettingsRegistrado` (`Settings` com instância fictícia `JIRA_SAMSUNG_*` montada em memória → o token sai de `valoresSecretos()` e é mascarado).
  - `EncoderJsonSeguroTest`: `umaLinhaJsonValidaPorEvento`; `quebraDeLinhaNaoCriaEntrada` (msg `"a\nINFO falso"` → uma linha, `\n` escapado); `excecaoComTokenSaiMascarada` (exceção com `Bearer abcdefghijklmnop` na mensagem → `***` em `exc.msg`); `pilhaLimitadaA20`; `mdcDaRequisicaoNoEvento`.
  - `ConfiguradorDeLogTest` (appender de lista; restaurar a configuração original no `@AfterEach`): `configuradorAplicaFormatoTexto` (`aplicar("TEXTO", null)` → linha não começa com `{`); `formatoInvalidoCaiEmJson`; `configuradorGravaArquivoComRotacao` (pasta temporária; `aplicar("JSON", pasta)`; um `log.info` → `painel.log` existe, uma linha JSON); `pastaImpossivelNaoDerruba` (caminho dentro de um arquivo comum → `aplicar` não lança e a saída padrão continua).
  - `LogDeRequisicaoTest`: `linhaPorRequisicaoComModeloDaRota` (`GET /api/tarefa/TG-1?instancia=samsung` → `rota=/api/tarefa/{key}`, sem `TG-1`, sem `instancia`); `semUsuarioNoLog` (principal `ana.silva` → nenhum `ana.silva` nas linhas); `requestIdDoProxyAceitoSoValido` (`X-Request-Id: ../../x` → gerado; `abc-12345678` → mantido); `healthNaoPoluiInfo`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*MascaraDeLogTest*' --tests '*EncoderJsonSeguroTest*' --tests '*ConfiguradorDeLogTest*' --tests '*LogDeRequisicaoTest*'` (Windows: `.\gradlew.bat test --tests ...`) → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa; subir o painel em modo dev (`cd app && sh iniciar.sh`) e conferir uma linha JSON por requisição na saída do processo, e `PAINEL_LOG_FORMATO=TEXTO` no `app/.env.example` documentado para uso local (registrar no progresso, sem copiar valores).
- [ ] **Passo 5: Commit** `feat(operacao): logs estruturados em JSON com máscara de segredos e id de requisição`.

---

### Tarefa 2: Prontidão (readiness): banco, migrações, disco e trava de processo

**Arquivos:** Criar `operacao/Prontidao.kt`, `operacao/VerificacaoDeDisco.kt`; Modificar `Application.kt` (rota `/api/health/pronto`), `AppContext.kt` (`travaDeProcesso` passa a ser `TravaDeProcesso?`; novo `fun travaValida(): Boolean` = `true` se não há trava, caso só de testes), `db/TravaDeProcesso.kt` (`val valida: Boolean`: `trava.isValid && canal.isOpen`), `db/Database.kt` (`fun migracoesPendentes(ds: DataSource): Int` com `Flyway.info().pending()`), `config/Settings.kt` (`PAINEL_DISCO_MIN_MB`, via `OperacaoSettings`), `security/AccessControl.kt` (`rotasPublicas` ganha `listOf("api", "health", "pronto")`), `seguranca/InventarioDeRotasTest.kt` (`ROTAS_PUBLICAS` e `inventarioConhecido`, se existir), `app/.env.example`; Teste `operacao/ProntidaoTest.kt`, `db/TravaDeProcessoTest.kt` (caso novo), `db/DatabaseTest.kt` (caso novo).

**Interfaces:**
- Produz:
  ```kotlin
  data class OperacaoSettings(val logFormato: String? = null, val logDir: Path? = null, val discoMinMb: Int = 500 /* 50..1_000_000 */, /* demais campos nas T4, T5 e T7 */)
  enum class MotivoNaoPronto { BANCO_INDISPONIVEL, MIGRACAO_PENDENTE, DISCO_BAIXO, TRAVA_PERDIDA }
  @Serializable data class ProntidaoDto(val pronto: Boolean, val codigo: String? = null)
  object VerificacaoDeDisco { fun livreBytes(pasta: Path): Long }   // FileStore.usableSpace; pasta inexistente sobe ao pai existente
  class Prontidao(
      private val arquivoDoBanco: Path,
      private val jdbcUrlDeSonda: String,                 // Database.urlDeArquivo(arquivo) + "?busy_timeout=1500&mode=ro"; conexão PRÓPRIA, fora do pool
      private val pendentes: () -> Int,                   // { Database.migracoesPendentes(ds) }
      private val travaValida: () -> Boolean,             // { context.travaValida() }
      private val discoMinBytes: Long,
      private val clock: Clock,
      private val tempoMaximo: Duration = 2.seconds,
  ) { suspend fun avaliar(): ProntidaoDto }               // resultado guardado por 5 s; ordem de checagem abaixo
  ```
  Ordem: (1) arquivo existe, `Files.isWritable` no arquivo e na pasta (o WAL exige escrever na pasta) e `SELECT 1 FROM flyway_schema_history LIMIT 1` pela conexão de sonda, tudo dentro de `withTimeoutOrNull(tempoMaximo)` em `Dispatchers.IO` → `BANCO_INDISPONIVEL`; (2) `pendentes() > 0` → `MIGRACAO_PENDENTE`; (3) `VerificacaoDeDisco.livreBytes(pasta do banco) < discoMinBytes` → `DISCO_BAIXO`; (4) `!travaValida()` → `TRAVA_PERDIDA`. **Decisão:** a prontidão não tenta uma escrita de teste (um gravador ocupado por poucos segundos, como a retenção, geraria falso alarme); "gravável" é conferido por permissão do arquivo e da pasta, e a escrita real é provada a cada sincronização e a cada backup.
  `GET /api/health/pronto` (público): 200 `{"pronto":true}` ou 503 `{"pronto":false,"codigo":"BANCO_INDISPONIVEL"}`. `/api/health` não muda (liveness: o processo responde). Sem `dataSource` (só testes) → 503 `BANCO_INDISPONIVEL`.

- [ ] **Passo 1: Testes que falham** (SQLite real em arquivo temporário, `BancoEmArquivo`):
  - `prontoComBancoMigracoesDiscoETrava` (200); `prontoDetectaBancoIndisponivelEmAte3s` (apaga o arquivo do banco, ou o troca por uma pasta; 503 `BANCO_INDISPONIVEL` em menos de 3 s, medido); `prontoNaoEsperaOPoolEsgotado` (4 conexões do pool seguradas pelo teste; a prontidão responde 200 em menos de 3 s porque a sonda não usa o pool); `migracaoPendente503` (`pendentes = { 1 }`); `discoBaixo503` (`discoMinBytes` maior que o livre → `DISCO_BAIXO`); `travaPerdida503` (`travaValida = { false }`); `respostaSemDetalhes` (corpo só com `pronto` e `codigo`, sem caminho nem versão); `healthContinua200ComBancoIndisponivel`; `prontoEhPublico` (sem principal, também com `dashboardDevMode = false`); `resultadoGuardadoPor5s` (relógio injetado: dentro de 5 s a segunda chamada não reconsulta); `InventarioDeRotasTest` atualizado e verde (se existir).
  - `TravaDeProcessoTest.valida` (travada: `true`; depois de `close()`: `false`). `DatabaseTest.migracoesPendentes` (banco recém-migrado: 0).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ProntidaoTest*' --tests '*TravaDeProcessoTest*' --tests '*DatabaseTest*' --tests '*InventarioDeRotasTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; subir o painel local e conferir `GET http://127.0.0.1:8080/api/health/pronto` → 200 `{"pronto":true}`.
- [ ] **Passo 5: Commit** `feat(operacao): verificação de prontidão com banco, migrações, disco e trava de processo`.

---

### Tarefa 3: Subcomandos de operação e validador de configuração antes da subida

**PONTO DE PARADA (antes do Passo 3, só para o documento): onde ficam os YAML reais e quem os edita** (spec, ponto de parada 3). O código não depende da resposta; o documento `docs/operacao/configuracao.md` sim.

**Arquivos:** Criar `operacao/ComandosDeOperacao.kt`, `operacao/ValidadorDeConfig.kt`, `app/scripts/painel-cli.sh`, `app/scripts/painel-cli.ps1`, `docs/operacao/configuracao.md`; Modificar `Application.kt` (`fun main(args: Array<String>)`: se o primeiro argumento for um subcomando, delega a `ComandosDeOperacao` e sai com o código dele, **antes** de abrir banco, trava ou servidor), `AppContext.kt` (extrair de `AppContext.load` a parte que só lê arquivos, sem tocar banco nem trava, para `fun carregarConfiguracao(settings, clock): ConfiguracaoCarregada`; o `load` passa a chamá-la; **refatoração sem mudar comportamento**, protegida pelo `AppContextTest` existente, que deve continuar verde antes e depois), `.github/workflows/ci.yml` (passo no job `backend`: validar `app/config` de exemplo em modo dev), `ci/CiWorkflowTest.kt`; Teste `operacao/ValidadorDeConfigTest.kt`, `operacao/ComandosDeOperacaoTest.kt`.

**Interfaces:**
- Consome: `ArquivoEnv.ler`, `ArquivoEnv.combinarComFlagSoDoArquivo`, `Settings.fromEnv`, `AppContext.carregarConfiguracao` (nova, extraída), `BusinessCalendar.movableHolidays`.
- Produz:
  ```kotlin
  data class ProblemaDeConfig(val codigo: String, val arquivo: String, val linha: Int? = null)   // nunca o valor
  data class ResultadoDaValidacao(val problemas: List<ProblemaDeConfig>, val arquivosOk: List<String>) { val ok: Boolean }
  object ValidadorDeConfig { fun validar(env: Map<String, String>, dir: Path, hoje: LocalDate): ResultadoDaValidacao }
  /** 0 ok, 1 problema, 2 uso. Subcomandos: validar-config, backup-agora (T7), verificar-restauracao (T8). */
  fun comandoDeOperacao(args: List<String>, env: Map<String, String>, saida: (String) -> Unit = ::println): Int?   // null = não é subcomando (sobe o servidor)
  ```
  Uso: `painel validar-config [--dir <caminho>]` (padrão `PAINEL_CONFIG_DIR`). **Não abre o banco, não toma a trava, não sobe o servidor, não cria cliente Jira.** Saída: `OK <arquivo>` por arquivo válido e `ERRO <codigo> <arquivo>[:<linha>]` por problema; resumo final `VALIDACAO OK` ou `VALIDACAO FALHOU <n>`.
- Verificações (cada uma com código próprio):
  - `SETTINGS_INVALIDO` (o `Settings.fromEnv` falha; a linha cita só o nome da variável que o erro cita);
  - carga estrita de cada YAML pelo mesmo caminho da subida (`YAML_INVALIDO`, `CHAVE_DESCONHECIDA`, `ARQUIVO_AUSENTE`; em produção, modelo `.exemplo` no lugar do real → `EXEMPLO_EM_PRODUCAO`);
  - `PAPEIS_SEM_GERENTE_VIGENTE`; `ID_RESERVADO`;
  - cruzamentos (quando os arquivos existirem, Planos 5 a 7): pessoa em `ausencias.yaml` ou `equipes.yaml` que não existe em `papeis.yaml` → `PESSOA_DESCONHECIDA`; squad de `equipes.yaml` sem líder vigente em `papeis.yaml` → `SQUAD_SEM_LIDER`; ausência com `ate < de` → `PERIODO_INVERTIDO`;
  - calendário: feriados móveis do ano atual e do seguinte (`movableHolidays.any { it.year == ano }`) → senão `CALENDARIO_SEM_ANO` (com a linha do arquivo);
  - `status_map.yaml` cobre todo status citado nas fichas → senão `STATUS_DA_FICHA_SEM_MAPA`;
  - `jira_campos.yaml`: `customId` no formato `customfield_<n>` → senão `CUSTOM_ID_INVALIDO`;
  - ambiente de produção (`DASHBOARD_DEV_MODE` falso): `DATABASE_PATH` com pasta existente e gravável → senão `BANCO_PASTA_SEM_ESCRITA`; a T7 acrescenta as verificações de `BACKUP_*`.
- `painel-cli.sh` / `painel-cli.ps1`: ficam em `app/scripts/`, localizam o pacote em `PAINEL_HOME` (padrão `app/backend/build/install/painel`), entram em `app/` (para que `config/`, `data/` e o arquivo de variáveis relativos funcionem) e executam `bin/painel` (`bin\painel.bat` no Windows) repassando os argumentos e o código de saída. Sem Docker, sem rede.
- `docs/operacao/configuracao.md`: lista dos YAML (o que cada um guarda, se tem dado pessoal, quem edita, com que frequência); o fluxo de edição conforme a resposta do ponto de parada; "validar antes de reiniciar" com os comandos (`sh app/scripts/painel-cli.sh validar-config` e `.\app\scripts\painel-cli.ps1 validar-config`); **sem recarga a quente**: toda mudança vale depois de reiniciar o serviço (comando no runbook, T10); como reverter (versão anterior do arquivo).

- [ ] **Passo 1: Testes que falham** (pastas temporárias com YAML fictícios):
  - `AppContextTest` atual roda verde ANTES da extração (linha de base) e continua verde depois.
  - `exemplosPassamEmDev` (pasta `app/config` com `DASHBOARD_DEV_MODE` verdadeiro → `ok`).
  - `exemploEmProducaoFalha`.
  - `pessoaDesconhecidaNaAusencia` (com a linha); `squadSemLider`; `periodoInvertido`; `calendarioSemAnoSeguinte` (hoje `2030-11-02`, calendário até 2030 → `CALENDARIO_SEM_ANO`); `statusDaFichaSemMapa`; `customIdInvalido`; `papeisSemGerente`; `bancoEmPastaSemEscrita` (só POSIX; `Assumptions`).
  - `erroSemValorNoTexto` (ausência de `joao.ficticio.x`, desconhecido → a saída não contém `joao.ficticio.x`).
  - `naoAbreBancoNemJira` (nenhum arquivo de banco criado na pasta temporária, nenhum `.lock`, e `MockEngine` contador com 0 requisições).
  - `codigosDeSaida` (0, 1 e 2 com argumento desconhecido); `semSubcomandoNaoInterceptaASubida` (`comandoDeOperacao(emptyList(), ...)` → `null`).
  - `scriptsDeCliExistemESemDocker` (os dois arquivos existem, repassam `$@`/`@args` e não contêm `docker`).
  - `CiWorkflowTest.backendValidaConfigDeExemplo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ValidadorDeConfigTest*' --tests '*ComandosDeOperacaoTest*' --tests '*AppContextTest*' --tests '*CiWorkflowTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (extração do `carregarConfiguracao` primeiro, com a suíte verde; depois o validador) e escrever `configuracao.md` conforme a resposta.
- [ ] **Passo 4: Rodar** → PASSA; `cd app/backend && ./gradlew installDist` e, a partir de `app/`, `sh scripts/painel-cli.sh validar-config --dir config` (PowerShell: `.\scripts\painel-cli.ps1 validar-config --dir config`) com o modo dev ligado → `VALIDACAO OK`.
- [ ] **Passo 5: Commit** `feat(operacao): subcomandos de operação e validador de configuração antes da subida`.

---

### Tarefa 4: Métricas Prometheus numa porta interna

**Arquivos:** Criar `operacao/Metricas.kt`, `operacao/ServidorDeMetricas.kt`; Modificar `Application.kt` (`main`: segundo conector do Netty; o conector público continua como hoje), `gradle/libs.versions.toml` e `build.gradle.kts` (`ktor-server-metrics-micrometer` 3.3.1 e `micrometer-registry-prometheus` em versão exata resolvida na execução: a última 1.x estável no Maven Central, registrada no progresso), `sync/SyncService.kt`, `db/AccessLog.kt` e `db/Retention.kt` (incrementam contadores, por um ponto de injeção opcional que não muda as assinaturas atuais), `config/Settings.kt` (`PAINEL_METRICAS_PORTA`, `PAINEL_METRICAS_HOST` em `OperacaoSettings`), `app/.env.example`; Teste `operacao/MetricasTest.kt`, `operacao/ServidorDeMetricasTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  class Metricas(val registro: PrometheusMeterRegistry) {
      fun execucaoDeSync(instancia: String, tipo: SyncKind, status: RunStatus)
      fun falhaDeAccessLog()
      fun retencaoRemoveu(categoria: String, n: Int)
      fun alertaAtivo(codigo: String, ativo: Boolean)
      fun backup(status: StatusDeBackup, tamanhoBytes: Long?)      // usado na T7
      fun discoLivre(volume: String /* "banco" | "backup" */, bytes: Long)
  }
  fun Application.servidorDeMetricas(metricas: Metricas)   // só GET /metrics; qualquer outro caminho → 404
  ```
  `PAINEL_METRICAS_PORTA` (padrão `9464`; 1024..65535, diferente de 8080; `0` desliga o servidor de métricas) e `PAINEL_METRICAS_HOST` (padrão `127.0.0.1`). Uma máquina de coleta em outro host só enxerga a porta se o operador mudar o host **e** liberar a porta no firewall só para o coletor; isso é decisão do Plano 13. O roteamento confere `call.request.local.localPort`: `/metrics` só responde na porta de métricas.
- Métricas (rótulos fechados; nenhum username, chave, squad, JQL, caminho ou URL):

  | Nome | Tipo | Rótulos |
  |---|---|---|
  | `painel_http_requisicoes_total`, `painel_http_duracao_segundos` | contador, histograma | `rota` (modelo), `metodo`, `status` |
  | `painel_sync_execucoes_total` | contador | `instancia`, `tipo`, `status` |
  | `painel_sync_ultima_ok_timestamp_segundos` | gauge | `instancia`, `tipo` |
  | `painel_sync_suspensa` | gauge (0/1) | `instancia` |
  | `painel_sync_cursor_idade_segundos` | gauge | `instancia` |
  | `painel_access_log_falhas_total` | contador | — |
  | `painel_retencao_removidas_total` | contador | `categoria` |
  | `painel_alerta_ativo` | gauge (0/1) | `codigo` |
  | `painel_backup_execucoes_total` | contador | `status` |
  | `painel_backup_ultimo_ok_timestamp_segundos`, `painel_backup_tamanho_bytes` | gauge | — |
  | `painel_disco_livre_bytes` | gauge | `volume` |
  | JVM, GC, threads, Hikari | binders padrão do Micrometer | os do binder |

  `instancia` só aceita os ids de `INSTANCIAS_CONHECIDAS`.

- [ ] **Passo 1: Testes que falham:** `metricasNaPortaInterna` (módulo de métricas isolado em `testApplication` e, em `ServidorDeMetricasTest`, um `embeddedServer(Netty)` real com duas portas livres escolhidas pelo teste → 200, `text/plain; version=0.0.4`); `metricasNaoRespondemNaPortaPublica` (a porta pública → 404); `outrosCaminhosNaPortaInterna404`; `rotuloDeRotaEhModelo` (`/api/tarefa/TG-1` → `rota="/api/tarefa/{key}"`); `semDadoPessoalNosRotulos` (depois de chamadas como `ana.silva` com `pessoa=ana.silva` → o texto de `/metrics` não contém `ana.silva`, `TG-1`, `https://` nem o caminho do banco); `syncIncrementaContador`; `falhaDeAccessLogConta`; `portaIgualA8080Falha`; `portaZeroDesliga`; `discoELivreSemCaminho` (rótulo `volume="banco"`, sem caminho).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*MetricasTest*' --tests '*ServidorDeMetricasTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa; subir o painel local e conferir `GET http://127.0.0.1:9464/metrics` (200) e `GET http://127.0.0.1:8080/metrics` (404).
- [ ] **Passo 5: Commit** `feat(operacao): métricas Prometheus em porta interna, sem dados pessoais`.

---

### Tarefa 5: Alertas operacionais (avaliação, rota, métrica, log e arquivo de alertas)

**PONTO DE PARADA (antes do Passo 3, só para a entrega externa): canal dos alertas** (spec, ponto de parada 1). A avaliação, a rota do gerente, a métrica, o log e o arquivo de alertas são feitos em qualquer resposta.

**Arquivos:** Criar `resources/db/migration/V{n}__execucao_de_backup.sql`, `operacao/Alerta.kt`, `operacao/AvaliadorDeAlertas.kt`, `operacao/AlertasRoutes.kt`, `operacao/SaidaDeAlertas.kt`, `operacao/LacoDeAlertas.kt`, `operacao/BackupRunStore.kt`, `ops/prometheus/alertas.yml` (opcional); Modificar `Application.kt` (`LacoDeAlertas(avaliador, clock, dormir)` a cada 5 min, iniciado em `ApplicationStarted` e cancelado em `ApplicationStopping`, como o `Agendador`; **não** é um `SyncKind` novo, para não mexer no `CHECK` de `sync_run`; sem Jira), `config/Settings.kt` (`SYNC_PAT_VALIDADE_<INSTANCIA>` e `PAINEL_ALERTAS_ARQUIVO` em `OperacaoSettings`), `http/CodigosDeMensagem.kt` (se existir), `db/Retention.kt` (nova linha em `PURGAS`: `DELETE FROM backup_run WHERE started_at < ?`, pelo limite geral `retentionMonths`), `app/.env.example`; Teste `operacao/AvaliadorDeAlertasTest.kt`, `operacao/AlertasRoutesTest.kt`, `operacao/BackupRunStoreTest.kt`, `operacao/SaidaDeAlertasTest.kt`, `db/RetentionTest.kt` (`backupRunAntigoApagado`).

**Interfaces:**
- Migração (SQLite; confirmar `n`):
  ```sql
  CREATE TABLE backup_run (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      tipo          TEXT    NOT NULL CHECK (tipo IN ('BACKUP', 'RESTAURACAO')),
      started_at    INTEGER NOT NULL,            -- milissegundos UTC
      finished_at   INTEGER,
      status        TEXT    NOT NULL CHECK (status IN ('OK', 'FALHA')),
      arquivo       TEXT,                        -- só o NOME (painel-aaaa-MM-dd-HHmmss.db), nunca o caminho
      tamanho_bytes INTEGER,
      sha256        TEXT,
      codigo_erro   TEXT
  );
  CREATE INDEX idx_backup_run_tipo_started ON backup_run (tipo, started_at DESC);
  ```
  A retenção do painel apaga linhas com mais de `RETENTION_MONTHS`. Observação: a linha de um backup é gravada **depois** da cópia, então a própria cópia não a contém; ao restaurar, o `backup_run` volta ao estado daquela hora (o runbook manda rodar `backup-agora` logo após restaurar).
- Produz:
  ```kotlin
  enum class TipoDeBackup { BACKUP, RESTAURACAO }
  enum class StatusDeBackup { OK, FALHA }
  data class RegistroDeBackup(val tipo: TipoDeBackup, val status: StatusDeBackup, val inicio: Instant, val fim: Instant?,
                              val arquivo: String?, val tamanhoBytes: Long?, val sha256: String?, val codigoErro: String?)
  interface BackupRunStore {
      fun registrar(r: RegistroDeBackup)
      fun ultima(tipo: TipoDeBackup, status: StatusDeBackup? = null): RegistroDeBackup?
      fun porArquivo(nome: String): RegistroDeBackup?
  }
  class SqliteBackupRunStore(ds: DataSource) : BackupRunStore
  enum class CodigoAlerta { SYNC_SUSPENSA, SYNC_ATRASADA, RECONCILIACAO_SUSPEITA, RECONCILIACAO_ATRASADA, RETENCAO_FALHOU,
      ACCESS_LOG_FALHANDO, PAT_EXPIRANDO, PAT_EXPIRADO, CALENDARIO_SEM_PROXIMO_ANO, DISCO_BAIXO, BANCO_SUSPEITO,
      BACKUP_ATRASADO, RESTAURACAO_NAO_TESTADA, MODO_DEV_COM_JIRA_LIGADO }   // 14 códigos
  enum class GravidadeAlerta { ATENCAO, CRITICO }
  data class Alerta(val codigo: CodigoAlerta, val gravidade: GravidadeAlerta, val instancia: String?, val desde: Instant)
  interface SaidaDeAlertas { fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant) }
  class ArquivoDeAlertas(arquivo: Path, fuso: ZoneId) : SaidaDeAlertas   // anexa uma linha de texto; passa de 5 MB → renomeia para <arquivo>.1 (substitui o anterior); falha de escrita só loga a classe da exceção
  class AvaliadorDeAlertas(estado: SyncStateStore, backups: BackupRunStore, metricas: Metricas, settings: Settings,
                           calendario: BusinessCalendar, discoLivre: () -> Long, saidas: List<SaidaDeAlertas>, clock: Clock) {
      fun avaliar(): List<Alerta>           // ordem: CRITICO antes de ATENCAO, depois o código
      fun avaliarEPublicar(): List<Alerta>  // atualiza painel_alerta_ativo, loga WARN "ALERTA <codigo> ATIVO|RESOLVIDO" e chama as saídas só na mudança de estado
  }
  ```
  Formato da linha do arquivo de alertas (texto, sem dado pessoal, sem caminho): `2026-10-30T08:00:00-04:00 ATIVO CRITICO SYNC_SUSPENSA samsung` (instância omitida quando não há). `PAINEL_ALERTAS_ARQUIVO` vazio = só log, métrica e rota.
- Regras (valor padrão, reversível; relógio injetado; dias em Manaus):

  | Código | Condição | Gravidade |
  |---|---|---|
  | `SYNC_SUSPENSA` | `estado.suspensa(inst) != null` | CRITICO |
  | `SYNC_ATRASADA` | flag do Jira ligada e último `INCREMENTAL` `OK` há mais de 3 × `SYNC_INTERVALO_MIN` (ou nunca, depois de 1 h no ar) | ATENCAO |
  | `RECONCILIACAO_SUSPEITA` | última `RECONCILIACAO` com `codigoErro == RECONCILIACAO_SUSPEITA` | CRITICO |
  | `RECONCILIACAO_ATRASADA` | flag do Jira ligada e última `RECONCILIACAO` `OK` há mais de 26 h | ATENCAO |
  | `RETENCAO_FALHOU` | última `RETENCAO` `FALHA`, ou nenhuma `OK` há mais de 26 h | ATENCAO |
  | `ACCESS_LOG_FALHANDO` | contador de falhas cresceu nos últimos 60 min | CRITICO |
  | `PAT_EXPIRANDO` / `PAT_EXPIRADO` | `SYNC_PAT_VALIDADE_<INST>` (data `aaaa-mm-dd`) a 14 dias ou menos / já passou; só com a flag ligada | ATENCAO / CRITICO |
  | `CALENDARIO_SEM_PROXIMO_ANO` | a partir de 1º de novembro, `calendario.movableHolidays` não tem data do ano seguinte | ATENCAO |
  | `DISCO_BAIXO` | `discoLivre()` (pasta do banco) abaixo de `PAINEL_DISCO_MIN_MB` | CRITICO |
  | `BANCO_SUSPEITO` | última `BACKUP` com `codigoErro == INTEGRIDADE_FALHOU` (a cópia do banco vivo não passou na conferência) | CRITICO |
  | `BACKUP_ATRASADO` | produção (`DASHBOARD_DEV_MODE` falso) e nenhum `BACKUP` `OK` há mais de 26 h | CRITICO |
  | `RESTAURACAO_NAO_TESTADA` | produção e nenhuma `RESTAURACAO` `OK` há mais de 3 dias (a verificação roda a cada backup; 3 dias tolera um fim de semana); CRITICO se a última `RESTAURACAO` for `FALHA`, senão ATENCAO | ATENCAO / CRITICO |
  | `MODO_DEV_COM_JIRA_LIGADO` | modo desenvolvimento ligado e a flag do Jira ligada | CRITICO |

- `GET /api/operacao/alertas` (só GERENTE; mesmo padrão de `rotasSyncStatus`: papel conferido dentro da rota, toda chamada, inclusive a negada, vai para o `access_log`, e sem registro responde 503): `{"alertas":[{"codigo","gravidade","instancia","desde"}]}`.
- `ops/prometheus/alertas.yml` (**opcional**, só para quem tiver Prometheus/Alertmanager; o painel não o lê nem depende dele): grupo `painel` com `PainelAlertaAtivo` (`max by (codigo) (painel_alerta_ativo) > 0` por 5 min, rótulo `gravidade` copiado) e `PainelForaDoAr` (`up{job="painel"} == 0` por 5 min); `PainelNaoPronto` (sonda HTTP de `/api/health/pronto`) comentado como opcional.
- Entrega externa conforme o ponto de parada: opção recomendada = arquivo de alertas + log + faixa na tela, sem serviço externo; webhook só se o usuário escolher (subtarefa descrita na spec).

- [ ] **Passo 1: Testes que falham** (relógio fixo `2026-10-30T12:00Z`; estado em SQLite real em arquivo temporário):
  - um teste por código: `suspensaoViraAlerta`, `syncAtrasadaDepoisDe3Intervalos` (29 min → não; 31 min → sim, com intervalo 10), `reconciliacaoSuspeita`, `reconciliacaoAtrasada26h`, `retencaoFalhou`, `accessLogFalhando`, `patExpirandoEm14Dias` (validade `2026-11-13` → sim; `2026-11-14` → não), `patExpirado`, `patSemFlagNaoAlerta`, `calendarioSemProximoAno` (hoje `2030-11-01`), `discoBaixo`, `bancoSuspeito`, `backupAtrasadoSoEmProducao`, `restauracaoNaoTestada3Dias`, `restauracaoFalhouEhCritica`, `modoDevComJiraLigado`.
  - `suspensaoViraAlertaUmaVez` (três avaliações seguidas → um único log `ATIVO` e uma única linha no arquivo; depois de limpar → um `RESOLVIDO`).
  - `metricaDeAlertaAcompanha`.
  - `SaidaDeAlertasTest`: `linhaDeTextoSemDadoPessoal`; `rotacionaAcimaDe5Mb`; `falhaDeEscritaNaoDerrubaAvaliacao` (arquivo em pasta inexistente e sem permissão).
  - `AlertasRoutesTest`: `soGerente` (os demais papéis → 403, registrados); `semDadosPessoais`; `semRegistroSemResposta` (falha no `access_log` → 503).
  - `BackupRunStoreTest`: `registraELeUltimaOk`; `porArquivoAcha`; `migracaoCriaTabelaEIndice`.
  - `avaliacaoNaoFazRequisicao` (flag ligada na `Settings` em memória, `MockEngine` contador → 0).
  - `alertasYmlValido` (lê o YAML, se existir: grupo `painel`, regras com `expr` não vazia).
  - `todoCodigoTemSecaoNoRunbook` fica na T10.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AvaliadorDeAlertasTest*' --tests '*AlertasRoutesTest*' --tests '*BackupRunStoreTest*' --tests '*SaidaDeAlertasTest*' --tests '*RetentionTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (e o webhook só se a resposta pedir).
- [ ] **Passo 4: Rodar** → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `feat(operacao): alertas operacionais com rota do gerente, métrica, log e arquivo de alertas`.

---

### Tarefa 6: Aviso de alertas na tela do gerente

**Arquivos:** Criar `app/frontend/src/shell/AvisoDeAlertas.tsx`, `AvisoDeAlertas.test.tsx`; Modificar `shell/Shell.tsx`, `api/client.ts` (`fetchAlertas`), `screens/Sincronizacao/Sincronizacao.tsx` (lista de alertas no topo), i18n.

**Interfaces:**
- `AvisoDeAlertas`: só para GERENTE; consulta `/api/operacao/alertas` a cada 5 min (TanStack Query, `refetchInterval`); com alertas, faixa `role="status"` no topo: `▲ <n> alerta(s) crítico(s)` e `● <n> de atenção`, com link para `/sincronizacao#alertas`; cada alerta traduzido por código (`alertas.<CODIGO>`, 14 códigos), com a instância e "desde <data e hora de Manaus>". Sem alertas, nada aparece. Erro na consulta não mostra a faixa (e não dispara a sessão expirada além do que o Plano 9 já faz).

- [ ] **Passo 1: Testes que falham:** `faixaSoParaGerente`; `criticoComTrianguloETexto` (pt e en; símbolo e texto); `semAlertasSemFaixa`; `linkParaSincronizacao`; `todoCodigoDeAlertaTraduzido` (lista fixa igual ao enum do backend, 14 códigos; o teste do Plano 10 `todoCodigoTemTraducaoPtEEn` passa a cobrir `alertas.*`).
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shell src/screens/Sincronizacao` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build && node scripts/verificar-csp.mjs` (se o script do Plano 9 existir).
- [ ] **Passo 5: Commit** `feat(ui): aviso de alertas operacionais para o gerente`.

---

### Tarefa 7: Backup agendado do SQLite (cópia consistente, conferida, com retenção)

**PONTO DE PARADA (antes do Passo 3, só para a cópia fora do servidor): onde os backups ficam além da pasta local e a cifragem** (spec, ponto de parada 2). O backup local agendado e conferido é feito em qualquer resposta.

**Arquivos:** Criar `operacao/ServicoDeBackup.kt`, `docs/operacao/backup-e-restauracao.md`; Modificar `sync/Agendador.kt` (nova `TarefaDeOperacao` e parâmetro opcional `operacoes: List<TarefaDeOperacao> = emptyList()` no `Agendador`, rodadas em `ciclo()` depois das `tarefas`, com o mesmo isolamento de exceções; **sem tocar** `SyncKind` nem o `CHECK` de `sync_run`; `montarAgendador` inclui o backup quando `settings.operacao.backup.dir != null`), `operacao/ComandosDeOperacao.kt` (subcomando `backup-agora`), `operacao/ValidadorDeConfig.kt` (verificações de `BACKUP_*`), `config/Settings.kt` (`BackupSettings` em `OperacaoSettings`), `app/.env.example`; Teste `operacao/ServicoDeBackupTest.kt`, `sync/AgendadorTest.kt` (casos novos), `operacao/ValidadorDeConfigTest.kt` (casos novos).

**Interfaces:**
- Consome: `Database.urlDeArquivo`, `Database.connect`, `BackupRunStore` (T5), `Metricas.backup` (T4), `VerificacaoDeDisco` (T2), `Agenda.diariaDevida`, relógio injetado.
- Produz:
  ```kotlin
  data class BackupSettings(val dir: Path? = null /* BACKUP_DIR; vazio = backup desligado */,
                            val hora: LocalTime = LocalTime.of(2, 0) /* BACKUP_HORA, HH:mm no fuso do painel (Manaus) */,
                            val retencaoCopias: Int = 14 /* BACKUP_RETENCAO_COPIAS, 1..365 */)
  data class TarefaDeOperacao(val nome: String, val devida: suspend (agora: Instant) -> Boolean, val executar: suspend () -> Unit)
  sealed interface ResultadoDoBackup {
      data class Ok(val arquivo: Path, val tamanhoBytes: Long, val sha256: String) : ResultadoDoBackup
      data class Falha(val codigo: String) : ResultadoDoBackup   // PASTA_SEM_ESCRITA, DISCO_INSUFICIENTE, COPIA_FALHOU, INTEGRIDADE_FALHOU, ERRO_INTERNO
  }
  class ServicoDeBackup(ds: DataSource, arquivoDoBanco: Path, cfg: BackupSettings, runs: BackupRunStore, metricas: Metricas?, clock: Clock, zone: ZoneId) {
      fun executar(): ResultadoDoBackup   // bloqueante; chamar em Dispatchers.IO
  }
  ```
  `executar()`:
  1. cria `BACKUP_DIR` se faltar e confere escrita; espaço livre ≥ 1,2 × tamanho do arquivo do banco (+ WAL) → senão `DISCO_INSUFICIENTE`;
  2. nome `painel-aaaa-MM-dd-HHmmss.db` (hora do fuso do painel); copia para `<nome>.parcial` com `VACUUM INTO ?` (parâmetro; se o driver recusar parâmetro, usar literal com aspas simples dobradas e registrar `Ruling:`), por uma conexão do pool com `autoCommit = true` (o `VACUUM` não roda dentro de transação) e sem segurar nenhuma transação de escrita;
  3. conferência do `.parcial`, aberto por conexão própria somente leitura: `PRAGMA integrity_check` devolve exatamente `ok`; `PRAGMA foreign_key_check` vazio; última versão de `flyway_schema_history` igual à do banco vivo → senão apaga o `.parcial` e falha com `INTEGRIDADE_FALHOU`;
  4. SHA-256 do arquivo; permissão `600` em POSIX (`PosixFilePermissions`; em sistema sem POSIX, ignora e o runbook cobre a pasta); renomeação atômica `.parcial` → `.db` (`Files.move` com `ATOMIC_MOVE`);
  5. registra `BACKUP OK` em `backup_run` (nome, tamanho, SHA-256, início, fim) e atualiza as métricas; falha registra `BACKUP FALHA` com `codigo_erro`; se nem o registro for possível, só loga a classe da exceção;
  6. retenção: dentre os arquivos da pasta cujo nome casa **exatamente** `^painel-\d{4}-\d{2}-\d{2}-\d{6}\.db$`, mantém os `retencaoCopias` mais recentes (o nome ordena por data) e apaga os demais, **nunca** o `OK` mais recente conhecido; qualquer outro arquivo da pasta é ignorado.
  A saída em log tem só código, tamanho e nome do arquivo (nunca o caminho completo). A tarefa agendada: `devida = Agenda.diariaDevida` sobre a última execução `BACKUP` (qualquer status) de `BackupRunStore`, com `BACKUP_HORA`; ao fim de cada backup `OK`, chama o `VerificadorDeRestauracao` (T8).
  `painel backup-agora` (subcomando): abre o banco **sem a trava e sem migrar** (funciona com o serviço rodando, porque `VACUUM INTO` só lê), executa `ServicoDeBackup` + verificação da T8, imprime `BACKUP OK <nome> <tamanho>` ou `BACKUP FALHOU <codigo>` e sai com 0 ou 1; sem `BACKUP_DIR`, sai com 2 e explica.
- Validador (acréscimo à T3): em produção, `BACKUP_DIR` ausente → `BACKUP_DIR_AUSENTE`; pasta dentro de uma árvore com `.git` → `BACKUP_DIR_DENTRO_DO_REPOSITORIO`; pasta igual à do banco ou dentro dela → `BACKUP_DIR_NA_PASTA_DO_BANCO` (perder o disco perderia banco e cópias juntos); sem escrita → `BACKUP_DIR_SEM_ESCRITA`; `BACKUP_HORA` e `BACKUP_RETENCAO_COPIAS` inválidos → `SETTINGS_INVALIDO`.
- `docs/operacao/backup-e-restauracao.md`: o que é guardado, onde, por quanto tempo, quem acessa (contém dados pessoais), permissões por sistema (`chmod 700` na pasta no Linux; `icacls` no Windows para a conta do serviço), a cópia para fora do servidor conforme a resposta, a cifragem em repouso do volume, como fazer um backup na hora (`painel-cli ... backup-agora`), como restaurar (comandos, T11), como a restauração é testada (T8) e a ligação com o runbook de incidentes.

- [ ] **Passo 1: Testes que falham** (SQLite real em arquivo temporário; pastas temporárias):
  - `ServicoDeBackupTest`: `copiaLegivelEIgualAoOriginal` (banco com Flyway e linhas sintéticas; o `.db` abre e tem as mesmas linhas; uma linha `BACKUP OK` com tamanho > 0 e SHA-256 de 64 caracteres); `copiaConsistenteComEscritaEmParalelo` (uma thread grava em loop durante o backup; a cópia passa em `integrity_check`); `permissao600` (POSIX; `Assumptions.assumeTrue`); `parcialNaoSobra` (qualquer falha deixa a pasta sem `.parcial`); `copiaCorrompidaNaoViraOk` (ponto de injeção de teste que trunca o `.parcial` antes da conferência → `FALHA INTEGRIDADE_FALHOU`, arquivo apagado, nenhum `.db` novo); `pastaSemEscritaFalhaComCodigo`; `discoInsuficienteFalhaComCodigo` (função de espaço livre injetada); `retencaoSoApagaNomesDeBackup` (com 20 cópias, retenção 14, mais um `leia-me.txt` e um `painel-qualquer.db`: ficam 14 cópias e os dois outros arquivos); `retencaoPreservaUltimoOk` (retenção 1, mais recente com `FALHA` registrada: o último `OK` é preservado); `saidaSemCaminhoNemSegredo` (log e registro não contêm o caminho da pasta).
  - `AgendadorTest` (casos novos): `operacaoDevidaRodaEUmaFalhaNaoParaOutras`; `backupDiarioNaHoraConfigurada` (relógio fixo em Manaus: antes de 02:00 não; depois, sim; já feito no dia, não); `semBackupDirNaoEntraNoAgendador`.
  - `ValidadorDeConfigTest` (novos): `backupDirAusenteEmProducao`, `backupDirDentroDoRepositorio`, `backupDirNaPastaDoBanco`.
  - `ComandosDeOperacaoTest`: `backupAgoraSemDirSaiCom2`; `backupAgoraComServicoRodando` (a trava do banco tomada pelo teste; o subcomando ainda copia).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ServicoDeBackupTest*' --tests '*AgendadorTest*' --tests '*ValidadorDeConfigTest*' --tests '*ComandosDeOperacaoTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e escrever o documento conforme a resposta.
- [ ] **Passo 4: Rodar** → PASSA. Com o painel local (modo dev, pasta temporária fora do repositório em `BACKUP_DIR`), `sh scripts/painel-cli.sh backup-agora` → `BACKUP OK`; conferir o arquivo na pasta e apagá-lo depois.
- [ ] **Passo 5: Commit** `feat(operacao): backup agendado do SQLite com conferência, retenção e registro`.

---

### Tarefa 8: Teste automatizado de restauração

**Arquivos:** Criar `operacao/VerificadorDeRestauracao.kt`, `operacao/VerificadorDeRestauracaoTest.kt`, `operacao/BackupRestauracaoTest.kt`; Modificar `operacao/ComandosDeOperacao.kt` (subcomando `verificar-restauracao`), `sync/Agendador.kt`/`montarAgendador` (a verificação roda logo depois de cada backup `OK`), `TestContextComBanco.kt` (parâmetro opcional `arquivoExistente: Path? = null` para montar o `AppContext` de teste sobre um banco já pronto), `docs/operacao/backup-e-restauracao.md`.

**Interfaces:**
- Consome: `ServicoDeBackup` (T7), `BackupRunStore` (T5), `Database.connect`, `Database.migracoesPendentes` (T2), `Prontidao` (T2), `GeradorSintetico` (Plano 8, T6; se não aceitar SQLite, o teste insere os dados diretamente), `testContextComBanco`.
- Produz:
  ```kotlin
  data class ResultadoDaVerificacao(val ok: Boolean, val tabelas: Map<String, Long> /* nome → linhas */, val codigoErro: String? /* COPIA_ALTERADA, INTEGRIDADE_FALHOU, MIGRACAO_PENDENTE, CONTAGEM_DIVERGENTE, ERRO_INTERNO */)
  class VerificadorDeRestauracao(runs: BackupRunStore, metricas: Metricas?, clock: Clock, tmp: () -> Path = { Files.createTempDirectory("painel-restauracao-") }) {
      fun verificar(backup: Path): ResultadoDaVerificacao   // bloqueante
  }
  ```
  `verificar`:
  1. se `backup_run` tem o nome do arquivo, o SHA-256 atual do arquivo confere com o registrado → senão `COPIA_ALTERADA`;
  2. copia o arquivo para um diretório **temporário** com o nome `painel.db` (nunca toca o banco vivo nem a pasta de backup), abre com `Database.connect`, **sem migrar**;
  3. `integrity_check` = `ok` → senão `INTEGRIDADE_FALHOU`; `Database.migracoesPendentes(ds) == 0` → senão `MIGRACAO_PENDENTE` (backup de uma versão antiga do esquema: o runbook trata a ordem de restaurar e subir o pacote correspondente);
  4. para cada tabela de `sqlite_master` (exceto `sqlite_%`): contagem de linhas e SHA-256 do conteúdo (linhas em ordem de `rowid`, colunas em texto) calculados no restaurado e no arquivo de backup → diferença = `CONTAGEM_DIVERGENTE`;
  5. apaga o diretório temporário (inclusive `-wal` e `-shm`) mesmo na falha; registra `RESTAURACAO OK|FALHA` em `backup_run` (com o nome do arquivo); a saída só tem nomes de tabela e números.
  `painel verificar-restauracao [<arquivo>]` (padrão: a cópia mais recente da pasta): imprime `RESTAURACAO OK <n> tabelas` mais uma linha `<tabela> <linhas>` por tabela, ou `RESTAURACAO FALHOU <codigo>`; sai com 0, 1 ou 2.
  **Comparação com o banco vivo:** no teste da suíte (origem parada), o restaurado é igual à origem tabela a tabela; em produção, a origem continua recebendo gravações, então o comando só **informa** a diferença de contagem para o banco vivo (informativo) e falha apenas pela comparação cópia × restaurado.

- [ ] **Passo 1: Testes que falham:**
  - `VerificadorDeRestauracaoTest`: `restauraEConfereUmaCopiaBoa` (`ok`, tabelas e contagens); `copiaAlteradaDetectada` (um byte mudado depois do registro → `COPIA_ALTERADA`); `copiaTruncadaFalha`; `migracaoPendenteFalha` (cópia de um banco com uma migração a menos); `temporarioApagadoMesmoNaFalha`; `bancoVivoIntactoEmEscrita` (o arquivo e o `-wal` do vivo não mudam de tamanho por causa da verificação); `saidaSemDadosPessoais` (linha sintética com `ana.silva` em `access_log` → o nome não aparece na saída nem no registro).
  - `BackupRestauracaoTest` (fluxo completo, SQLite real): `restauracaoIgualAoOriginal` (origem com `GeradorSintetico.popular(ds, semente = 42, issues = 500, eventosPorIssue = 5, pessoas = 12, worklogs = 800)`, mais linhas em `access_log`, `sync_run` e na tabela de decisões, se existir; backup; restauração; contagem e SHA-256 por tabela iguais); `migracoesSemPendencia`; `painelSobeNoRestaurado` (`AppContext` de teste sobre o arquivo restaurado; `/api/health/pronto` → 200); `numerosIguaisNoCard` (uma rota de card como GERENTE devolve os mesmos números na origem e no restaurado); `verificacaoAposBackupRegistraRestauracaoOk` (o agendamento: um backup `OK` é seguido de uma linha `RESTAURACAO OK`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*VerificadorDeRestauracaoTest*' --tests '*BackupRestauracaoTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA. Com o painel local da T7, `sh scripts/painel-cli.sh verificar-restauracao` → `RESTAURACAO OK` (registrar no progresso só "OK" e as contagens).
- [ ] **Passo 5: Commit** `test(operacao): restauração do backup verificada contra o original`.

---

### Tarefa 9: Rotação de segredos sem derrubar sessões

**Arquivos:** Modificar `security/Sessao.kt` (Plano 8), `security/OidcSettings.kt`, `config/Settings.kt` (`SESSION_SECRET_ANTERIOR`; também entra em `valoresSecretos()`), `app/.env.example`; Criar `docs/operacao/rotacao-de-segredos.md`; Teste `seguranca/SessaoRotacaoTest.kt`, `config/SettingsTest.kt`.

**Interfaces:**
- `OidcSettings` ganha `val segredoAnterior: ByteArray? = null` (de `SESSION_SECRET_ANTERIOR`, mesma regra de ≥ 32 bytes em base64; igual ao atual → `IllegalArgumentException` citando só os nomes).
- `Sessao`: o cookie é sempre **escrito** com o segredo atual; na **leitura**, tenta o atual e, se falhar a autenticação, o anterior; sessão lida com o anterior é regravada com o atual na mesma resposta (`Set-Cookie`). Sem o anterior, comportamento do Plano 8.
- `rotacao-de-segredos.md`, um procedimento por segredo, cada um com "quando", "como", "como conferir" e "como desfazer", com os comandos em PowerShell e `sh`, e a regra de que o **usuário** edita o arquivo de variáveis do serviço (`app/.env` ou o indicado por `PAINEL_ENV_FILE`; nenhuma IA abre esse arquivo):
  - `SESSION_SECRET`: mover o atual para `SESSION_SECRET_ANTERIOR`, gerar o novo (`openssl rand -base64 48` no Linux; no PowerShell, `[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])` ou o `RandomNumberGenerator`, a confirmar na execução), reiniciar o serviço; depois de `duracaoDaSessao` (8 h), remover o anterior e reiniciar. Para derrubar todas as sessões (incidente), trocar **sem** o anterior.
  - PAT do Jira: **feito pelo usuário** no Jira; atualizar a variável da instância e `SYNC_PAT_VALIDADE_<INST>`; reiniciar; conferir `/api/sync/status`. Nenhuma IA gera nem lê o PAT.
  - `OIDC_CLIENT_SECRET`: com a equipe do provedor (Plano 13); reiniciar.
  - Permissões do arquivo de variáveis: só a conta do serviço lê (`chmod 600` / `icacls`); o banco SQLite não tem senha, então não há rotação de senha de banco (o acesso é por permissão do arquivo e da pasta).

- [ ] **Passo 1: Testes que falham:** `sessaoComSegredoAnteriorAindaValida`; `sessaoLidaComAnteriorEhRegravadaComAtual`; `sessaoNovaUsaSegredoAtual`; `semAnteriorComoAntes` (`logoutLimpaSessao` e `cookieComFlagsSeguras` do Plano 8 continuam verdes); `anteriorIgualAoAtualFalha`; `anteriorCurtoFalha`; `segredoAnteriorRegistradoNaMascara` (T1).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SessaoRotacaoTest*' --tests '*OidcTest*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e escrever o documento.
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(seguranca): rotação do segredo da sessão sem derrubar quem está logado`.

---

### Tarefa 10: Runbook de operação diária e observabilidade

**Arquivos:** Criar `docs/operacao/runbook-diario.md`, `docs/operacao/observabilidade.md`, `app/backend/src/test/kotlin/br/sidia/painel/docs/RunbooksDocTest.kt`.

**Interfaces:**
- `observabilidade.md`: formato dos logs (campos), onde estão (saída do processo/serviço e, com `PAINEL_LOG_DIR`, o arquivo `painel.log` com rotação), como filtrar por `requisicao`, a lista de métricas, o arquivo de alertas (`PAINEL_ALERTAS_ARQUIVO`), as regras opcionais de `ops/prometheus/alertas.yml`, a porta interna e como uma infraestrutura que quiser coletar faz isso.
- `runbook-diario.md`: **sempre com os dois sistemas** (bloco PowerShell e bloco `sh` lado a lado). O nome do serviço é o definido no Plano 13 (aqui `painel-squads`; ajustar ao nome real). Seções com títulos fixos (o teste lê os títulos):
  - **Rotina:** diária (faixa de alertas, `/sincronizacao` e o arquivo de alertas), semanal (cópias na pasta de backup, `WARN` nos logs, ausências da semana atualizadas), mensal (`verificar-restauracao` manual de conferência e espaço em disco), anual (feriados do ano seguinte até 31/10, validade do PAT).
  - **Comandos do dia a dia** (`### COMANDO <nome>`): ver o estado (`Get-Service painel-squads` / `systemctl status painel-squads`); ver logs (`Get-Content "$env:PAINEL_LOG_DIR\painel.log" -Tail 100 -Wait` / `journalctl -u painel-squads -n 100 -f` ou `tail -n 100 -f "$PAINEL_LOG_DIR/painel.log"`); achar uma requisição (`Select-String -Path ... -Pattern '"requisicao":"<id>"'` / `grep '"requisicao":"<id>"'`); reiniciar o serviço (`Restart-Service` / `systemctl restart`); conferir a prontidão (`Invoke-RestMethod http://127.0.0.1:8080/api/health/pronto` / `curl -fsS http://127.0.0.1:8080/api/health/pronto`); validar a configuração antes de reiniciar (T3); backup na hora (T7); verificar a restauração (T8); espaço em disco (`Get-PSDrive` / `df -h`).
  - **Um item por `CodigoAlerta`** (`### ALERTA <CODIGO>`, 14 itens): o que significa, como confirmar, o que fazer, quando escalar e para quem. Ex.: `SYNC_SUSPENSA` → ler o código em `/api/sync/status`; 401/403 → PAT inválido ou sem permissão (usuário renova o PAT); 429 → esperar e reiniciar fora do horário; sempre "reiniciar o serviço retoma" (Plano 4A). `DISCO_BAIXO` → liberar espaço (logs antigos, cópias de backup excedentes; nunca apagar `painel.db`, `-wal` ou `-shm`). `BANCO_SUSPEITO` → ir ao incidente `BANCO_PERDIDO_OU_CORROMPIDO` (T11). `BACKUP_ATRASADO` → rodar `backup-agora`, ler o código da última falha em `backup_run`/log.
  - **Entrada e saída de pessoas:** editar `papeis.yaml` e `equipes.yaml` encerrando a vigência (`ate`) em vez de apagar a linha (a auditoria usa a vigência na data); validar; reiniciar.
  - **Ausências:** atualização semanal de `ausencias.yaml` (sem motivo), validar, reiniciar.
  - **Feriados do ano seguinte:** acrescentar ao calendário até 31/10 (o alerta começa em 1º/11); feriados municipais de Manaus a confirmar a cada ano.
  - **Pedido do titular** (Plano 11): consulta (tela Meus dados); eliminação pelo canal do DPO, com o procedimento técnico (o que pode ser apagado, o que fica por obrigação, registro fora do painel, e o fato de que as cópias de backup antigas ainda contêm o dado até saírem da retenção).
  - **Atualizar e reverter versão:** passos curtos e link para `docs/implantacao.md` (Planos 8 e 13): backup na hora, parar o serviço, trocar o pacote, subir, conferir a prontidão; reverter = voltar o pacote anterior; se a nova versão trouxe migração incompatível com o pacote anterior, reverter = restaurar o backup tirado antes (nunca editar migração).
  - **Ligar e desligar o Jira:** só o usuário muda a flag, no `app/.env`; link para o runbook da sincronização (Plano 4A).
- `RunbooksDocTest`:
  - `cadaAlertaTemSecao` (cada valor de `CodigoAlerta` aparece como `### ALERTA <CODIGO>` no runbook diário);
  - `cadaVariavelDoEnvExampleEstaDocumentada` (cada variável de `app/.env.example`, comentada ou não, aparece em `docs/operacao/*.md`, `docs/implantacao.md` ou `app/README.md`);
  - `semHostRealTokenNemEmail` (como o `documentosDeJiraNaoTemHostRealTokenNemEmail`);
  - `jiraSoPeloUsuario` (o runbook diz que só o usuário muda a flag e não contém "autorização expressa");
  - `cadaSistemaTemOsDoisBlocos` (cada `### COMANDO` tem um bloco `powershell` e um `sh`);
  - `semDockerNemPostgres` (nenhum `docs/operacao/*.md` contém `docker`, `pg_dump` nem `Testcontainers`, a não ser na linha que diz que não existem);
  - `comandosCitadosExistem` (os subcomandos `validar-config`, `backup-agora`, `verificar-restauracao` e os scripts `app/scripts/painel-cli.sh` e `.ps1` existem).

- [ ] **Passo 1: Escrever o teste** e rodar `./gradlew test --tests '*RunbooksDocTest*'` → FALHA.
- [ ] **Passo 2: Escrever** os documentos.
- [ ] **Passo 3: Rodar** → PASSA.
- [ ] **Passo 4: Revisar** os passos de cada alerta contra o código (cada comando citado existe; cada caminho existe; cada código de erro citado é o do código).
- [ ] **Passo 5: Commit** `docs(operacao): runbook de operação diária e observabilidade`.

---

### Tarefa 11: Runbook de incidentes

**Arquivos:** Criar `docs/operacao/runbook-incidentes.md`; Modificar `RunbooksDocTest.kt`, `docs/lgpd/relatorio-para-o-dpo.md` (seção 10 aponta para cá).

**Interfaces:**
- Seções fixas: **Severidades** (S1 dados pessoais expostos ou painel fora do ar em horário de uso; S2 números errados ou sincronização parada há mais de 1 dia; S3 o resto) e quem decide (gerente; DPO em todo incidente com dado pessoal); **Registro** (modelo de registro do incidente: quando, o que, quem foi avisado, ações, sem copiar dado pessoal); e um playbook por cenário (`### INCIDENTE <NOME>`), cada um com **conter → investigar → corrigir → comunicar → aprender**, sempre com blocos PowerShell e `sh`:
  - `PAINEL_FORA_DO_AR` (prontidão 503 com o código `BANCO_INDISPONIVEL`, `MIGRACAO_PENDENTE`, `DISCO_BAIXO` ou `TRAVA_PERDIDA`; logs pela `requisicao`; reiniciar o serviço; **disco cheio**: liberar espaço em logs antigos e cópias excedentes, nunca no banco; **trava `.lock` órfã**: o arquivo `painel.db.lock` pode ficar depois de uma queda e isso é normal, porque a trava de verdade é do sistema operacional e some com o processo; se a subida for recusada com "já existe outro painel", conferir primeiro se há mesmo outro processo (`Get-CimInstance Win32_Process -Filter "Name='java.exe'"` / `pgrep -af painel`), parar o serviço duplicado; só com **nenhum** processo do painel vivo e a recusa persistindo, apagar `painel.db.lock` e subir de novo; nunca apagar `painel.db`, `-wal` ou `-shm`);
  - `BANCO_PERDIDO_OU_CORROMPIDO` (parar o serviço; **não** apagar nada; mover `painel.db`, `painel.db-wal` e `painel.db-shm` para uma pasta de quarentena fora do repositório; escolher a cópia íntegra mais recente: rodar `verificar-restauracao <cópia>` nas mais recentes até uma dar `RESTAURACAO OK`; copiar essa cópia para o caminho de `DATABASE_PATH` com o nome `painel.db`, **sem** `-wal` e `-shm`; ajustar a permissão do arquivo para a conta do serviço; subir o serviço; conferir `/api/health/pronto`; rodar `backup-agora` para criar um ponto novo; o que se perde entre o backup e a falha: com a flag ligada pelo usuário, a sincronização repõe o que vem do Jira; decisões e `access_log` desde o backup se perdem: registrar no incidente; se o pacote em uso é mais novo que a cópia, a subida aplica as migrações que faltam; se for mais antigo, trocar o pacote antes);
  - `SUSPEITA_DE_VAZAMENTO` (conter: tirar a porta do proxy/firewall ou parar o serviço; trocar `SESSION_SECRET` **sem** o anterior para derrubar sessões; preservar `access_log`, logs e uma cópia do banco como evidência (`backup-agora`, copiada para a quarentena); o usuário renova o PAT; investigar pela tela Registro de acesso; avisar o DPO imediatamente; comunicação à ANPD e aos titulares **conforme a regulamentação vigente, a confirmar com o DPO**);
  - `ACESSO_INDEVIDO` (alguém viu o que não devia: conferir `papeis.yaml` e vigências, `access_log`, corrigir, avisar o DPO);
  - `TOKEN_DO_JIRA_EXPOSTO` (o usuário desliga a flag e revoga o PAT no Jira; nenhuma IA acessa o Jira para isso; varredura do histórico e das cópias de backup; trocar);
  - `BACKUP_VAZADO_OU_PERDIDO` (a pasta de backup foi exposta ou o disco foi perdido: tratar como `SUSPEITA_DE_VAZAMENTO`, listar o período coberto pelas cópias, trocar o que estiver nas cópias que for segredo, rever permissões da pasta, avisar o DPO);
  - `NUMEROS_ERRADOS` (comparar com o Jira só com a flag ligada pelo usuário; conferir `status_map`, ficha e avisos de qualidade dos dados; registrar diferença; o Plano 14 trata a validação completa);
  - `MODO_DEV_EM_PRODUCAO` (alerta `MODO_DEV_COM_JIRA_LIGADO` ou faixa vermelha em produção: desligar o modo dev e reiniciar imediatamente; tratar como possível exposição: seguir `SUSPEITA_DE_VAZAMENTO`).
- `RunbooksDocTest` ganha `cadaIncidenteTemAsCincoEtapas`, `incidenteComDadoPessoalAvisaODpo`, `prazoDaAnpdNaoFixado` (o texto não contém "em 72 horas", "em 3 dias" nem outro prazo numérico junto de "ANPD"), `restauracaoCitaWalEShm` (o playbook de banco perdido cita `-wal` e `-shm`) e `travaOrfaNuncaApagaOBanco` (o texto da trava órfã não manda apagar `painel.db`).

- [ ] **Passo 1: Testes que falham:** os cinco acima.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RunbooksDocTest*'` → FALHA.
- [ ] **Passo 3: Escrever** o runbook.
- [ ] **Passo 4: Rodar** → PASSA. Ensaiar uma vez, num banco de teste fora do repositório, o playbook de banco corrompido (corromper uma cópia, restaurar a anterior, subir o painel local) e registrar no progresso só "OK".
- [ ] **Passo 5: Commit** `docs(operacao): runbook de incidentes`.

---

### Tarefa 12: Fechamento

**Arquivos:** Modificar `docs/implantacao.md` (links para `docs/operacao/*`, variáveis novas, backup agendado, porta de métricas), `app/README.md` (tabela de variáveis novas e os subcomandos), `app/.env.example` (todas as variáveis novas, sem valor de segredo), `HANDOFF.md`.

**Interfaces:** nenhuma de código.

- [ ] **Passo 1: Suítes completas:** `cd app/backend && ./gradlew test` (Windows: `.\gradlew.bat test`); `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build && node scripts/verificar-csp.mjs`; `sh .claude/hooks/test-guard.sh`; varredura do Plano 9 → todos verdes. A suíte do backend não pode exigir nada além de JDK 21 (adendo, seção 3).
- [ ] **Passo 2: Subida de conferência** como serviço simulado, sem instalar nada no SO: `cd app/backend && ./gradlew installDist`, subir `build/install/painel/bin/painel` a partir de `app/` com o modo dev ligado, `PAINEL_LOG_DIR` e `BACKUP_DIR` apontando para pastas temporárias fora do repositório e `PAINEL_ALERTAS_ARQUIVO` numa delas: logs em JSON e arquivo `painel.log`; `/api/health/pronto` 200; `/metrics` só na porta interna; `validar-config` → OK; `backup-agora` → OK e `verificar-restauracao` → OK; apagar as pastas temporárias. A subida em modo produção real (OIDC e demais exigências) é do Plano 13.
- [ ] **Passo 3: Atualizar** o `HANDOFF.md` com as respostas dos três pontos de parada (e datas) e as pendências para o Plano 13 (nome e instalação do serviço no Windows e no Linux, pasta de backup definitiva e permissões, cópia dos backups para fora do servidor, coleta opcional das métricas, local dos YAML reais).
- [ ] **Passo 4: Push e PR** pelo fluxo do guia; CI verde.
- [ ] **Passo 5: Commit** `docs: fechamento do Plano 12 (operação em produção)`.

---

## Autoavaliação

**Cobertura (pedido e auditoria):**

| Item | Tarefa |
|---|---|
| A2: logs estruturados sem segredos (com teste) | T1 |
| A2: health/readiness com banco, migrações, disco e trava | T2 |
| A2: métricas | T4 |
| A2: alertas (sync suspensa, `RECONCILIACAO_SUSPEITA`, PAT expirando, disco, backup) e saída em arquivo/log | T5, T6 |
| A1: validador de configuração na subida e no CI; procedimento de edição dos YAML | T3 |
| B.5: backup agendado do SQLite (`VACUUM INTO`, retenção de N cópias, integridade) | T7 |
| B.5: teste de restauração automatizado em arquivo temporário | T8 |
| A7: rotação de `SESSION_SECRET` e de PAT | T9 |
| A7: runbook diário (Windows e `sh`; feriados de cada ano, entrada e saída de pessoas) | T10 |
| A7: runbook de incidentes (banco corrompido, disco cheio, trava órfã, suspeita de vazamento, restauração) | T11 |

**Consistência de tipos e dependências:** `OperacaoSettings` nasce na T2 e ganha campos nas T4 (métricas), T5 (alertas) e T7 (backup); `Metricas` (T4) é usada pelo `AvaliadorDeAlertas` (T5) e pelo `ServicoDeBackup` (T7); `BackupRunStore` e a tabela `backup_run` (T5) são escritos pelo backup (T7) e pela verificação (T8) e lidos pelos alertas (T5); `VerificacaoDeDisco` e `Database.migracoesPendentes` (T2) são usados pelos alertas (T5), pelo backup (T7) e pela verificação (T8); `comandoDeOperacao` e `painel-cli` (T3) ganham `backup-agora` (T7) e `verificar-restauracao` (T8); `TarefaDeOperacao` no `Agendador` (T7) chama o `VerificadorDeRestauracao` (T8); `CodigoAlerta` (T5, 14 códigos) é o que a tela (T6) e o `RunbooksDocTest` (T10) enumeram; `MascaraDeLog.registrarSegredos` (T1) recebe também o `SESSION_SECRET_ANTERIOR` (T9). Ordem: T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12; T6 só depende da rota da T5; T9 é independente de T4 a T8; T10 e T11 dependem de todos os comandos existirem.

**Fora deste plano:** instalação do serviço no SO, proxy, TLS, cofre de segredos, coleta das métricas e cópia dos backups para fora do servidor (Plano 13); validação dos números com dados reais (Plano 14); notificação por e-mail (não escolhida por padrão); cifragem do arquivo de backup pelo painel (ponto de parada 2).

## Decisões (valor padrão recomendado, reversível; reescritas em 27/10 para SQLite sem Docker)

1. **Logs em JSON por padrão**, com encoder próprio e máscara de segredos; `TEXTO` só por variável; arquivo com rotação só se `PAINEL_LOG_DIR` existir; nenhum usuário no log do servidor; `ConfiguradorDeLog` reaplica o Logback depois de ler o arquivo de variáveis.
2. **Readiness separada da liveness**: confere arquivo acessível e gravável por permissão, migrações, espaço em disco e trava de processo, com sonda própria fora do pool e sem escrita de teste.
3. **Métricas Prometheus numa porta interna própria** (9464, `127.0.0.1`), nunca na 8080; `0` desliga.
4. **Alertas avaliados dentro do painel** a cada 5 min, publicados em rota, métrica, log e **arquivo de texto**; entrega externa só se o usuário escolher (ponto de parada 1); `alertas.yml` do Prometheus é opcional.
5. **Backup diário às 02:00 de Manaus** por `VACUUM INTO`, conferência (`integrity_check`, `foreign_key_check`, versão do esquema, SHA-256), renomeação atômica, retenção de 14 cópias e registro em `backup_run`, agendado pelo `Agendador` do painel; pasta fora do repositório e fora da pasta do banco, obrigatória em produção.
6. **Restauração testada** em arquivo temporário depois de cada backup (e por `verificar-restauracao` sob demanda), com alerta depois de 3 dias sem teste `OK`.
7. **Configuração sem recarga a quente**: validar, reiniciar o serviço (ponto de parada 3 para onde os arquivos ficam).
8. **Rotação do `SESSION_SECRET` com segredo anterior aceito só para leitura** durante uma duração de sessão; não há senha de banco para rotacionar.
9. **Subcomandos no mesmo binário** (`validar-config`, `backup-agora`, `verificar-restauracao`), sem tomar a trava do banco e sem subir o servidor, chamados por `app/scripts/painel-cli.sh` e `.ps1`.
