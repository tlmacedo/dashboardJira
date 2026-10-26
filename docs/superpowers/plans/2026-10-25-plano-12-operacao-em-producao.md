# Fase 12: Operação em produção — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Dar ao painel o que a operação diária precisa sem depender de quem escreveu o código: logs estruturados sem segredos, verificação de prontidão (readiness) com o banco, métricas numa porta interna, alertas operacionais (sincronização suspensa ou atrasada, reconciliação suspeita, retenção, registro de acesso falhando, PAT perto de vencer, calendário do ano seguinte, backup atrasado, restauração não testada), validador de configuração antes da subida, backup agendado do PostgreSQL com teste automatizado de restauração, rotação de segredos e os runbooks de operação diária e de incidentes.

**Arquitetura (Architecture):** Tudo dentro do processo atual e do compose, sem serviço novo além de um contêiner de backup no perfil `prod`. Os logs saem em JSON por um encoder próprio do Logback, que mascara segredos antes de escrever. Um segundo conector do Netty, numa porta interna, serve só `/metrics` (Micrometer + Prometheus). Um `AvaliadorDeAlertas` puro lê o estado já existente (`sync_run`, `sync_state`, contadores, configuração, calendário e a tabela nova `backup_run`) e alimenta três saídas: a rota do gerente, uma métrica por alerta e uma linha de log na mudança de estado. O validador de configuração é um subcomando do mesmo binário (`painel validar-config`), que reaproveita os carregadores da subida. O backup é um script POSIX que roda no contêiner `postgres` do perfil `prod`, registra cada execução e é testado com Testcontainers.

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1 (`ktor-server-call-id`, `ktor-server-call-logging`, `ktor-server-metrics-micrometer`, todos na versão do Ktor), Micrometer com registro Prometheus (versão fixa resolvida na execução), Logback 1.5.20, PostgreSQL 16 + Flyway + Testcontainers 2.0.3; React 19 + TypeScript só no aviso de alertas; Docker Compose; `sh`.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-12-operacao-em-producao.md`. **Spec mestra:** seções 4.6 ("Produção × desenvolvimento", "Volume e atualidade", "Ambiente"), 5.4 (retenção), 11. **Auditoria:** `docs/superpowers/cobertura-do-projeto.md` (lacunas A1, A2 e A7; B.5 backup, observabilidade, configuração administrativa e runbooks). **Implantação:** `docs/implantacao.md` (Plano 8, T7).

**Quando executar:** depois do Plano 8 mesclado (e, portanto, dos Planos 9, 10 e 11).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que o Plano 8 foi mesclado e que estes nomes existem. Se algum mudou, atualizar só os nomes deste plano e registrar `Ruling:` no progresso.

| Nome | Origem |
|---|---|
| `main()`, `Application.module(...)`, `embeddedServer(Netty, port = 8080)` | `Application.kt` |
| `AppContext.load(settings)`, `AppContext.close()` e os carregadores dos YAML (`RoleDirectory`, `StatusMap`, calendário, `LabelsDeAtraso`, `RegrasDeAuditoria`, regras de risco, previsão, planejamento, esforço, Checklist × Review, integridade, distribuição, `FieldDictionary`, fichas) | Planos 1 a 7 |
| `Settings.fromEnv`, `Settings.mascararUrl`, `SyncSettings`, `LgpdSettings`, `RetencaoSettings`, `OidcSettings` | Planos 1, 4A, 11 e 8 |
| `SyncStateStore` (`ultimaPorTipo`, `suspensa`, `cursor`, `falhasSeguidas`), `SyncMode`, `SyncKind`, `RunStatus`, `Agendador` | Plano 4A |
| `AccessLog`, `AccessLogException`, `Retention.purge` | Planos 3, 4A e 11 |
| `rotasPublicas` (`security/AccessControl.kt`), `ROTAS_PUBLICAS` e `inventarioConhecido` (`seguranca/InventarioDeRotasTest.kt`) | Planos 1, 9 e 8 |
| `Sessao.kt`, cookie `painel_sessao`, `SESSION_SECRET` (≥ 32 bytes em base64) | Plano 8, T2 |
| Perfil `prod` do compose, `ComposeTest`, `app/scripts/verificar-imagem.sh`, `docs/implantacao.md` | Plano 8, T7 |
| `BusinessCalendar` e o calendário de feriados móveis (2026 a 2030) | Plano 1 |
| `.github/workflows/ci.yml`, `CiWorkflowTest`, `ErroDto`, `CodigosDeMensagem`, `EstadoDaTela` | Planos 9 e 10 |
| `PostgresDeTeste`, `GeradorSintetico` | Planos 9 (T10) e 8 (T6) |
| Último número de migração na `main` | `resources/db/migration/` |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Este plano não acessa o Jira: nenhum alerta, métrica, validador ou script faz requisição ao Jira. O alerta de PAT usa só a **data informada pelo operador** (`SYNC_PAT_VALIDADE_<INSTANCIA>`); nunca consulta o Jira para descobrir a validade.
- **Rede para registros de pacotes e imagens: permitida** (mesma regra da decisão 4 do Plano 8): dependências Gradle novas listadas no Stack e imagens base. **Nunca** para o Jira, para um provedor OIDC real nem para um destino de alerta ou de backup real nos testes.
- **Logs e métricas sem segredos nem dados pessoais:** nada de token, senha, `SESSION_SECRET`, cookie, cabeçalho `Authorization`, query string, JQL, chave de issue, username ou texto livre em log de servidor e em rótulo de métrica. O `access_log` (tabela) continua sendo o único registro de quem consultou o quê.
- **Métricas só na porta interna:** `/metrics` nunca responde na porta 8080.
- **Readiness pública mínima:** responde só `{"pronto": true|false, "codigo": ...}`, sem versão, host, nomes de banco ou detalhes.
- **Backup contém dados pessoais:** arquivos com permissão `600`, volume próprio, nunca dentro da imagem nem do repositório; nenhum teste envia backup para fora da máquina.
- Variáveis novas começam com `PAINEL_`, `DASHBOARD_`, `SYNC_` ou `BACKUP_` (nunca `JIRA_`). Nenhuma liga a sincronização.
- **Migração:** uma, `V{n}__execucao_de_backup.sql`, com `n` = próximo número livre (esperado V12, ou V13 se o Plano 8 tiver criado a de índices; o 4A ocupa V4 a V6).
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(operacao): ...`, `build(docker): ...`, `docs(operacao): ...`).

## Foco da revisão (Review Focus)

1. **Exceção com token na mensagem** (o driver ou o cliente HTTP ecoa a URL com credencial ou um cabeçalho): o log JSON sai com `***`, e a quebra de linha no texto não cria uma segunda entrada → `excecaoComTokenSaiMascarada` e `quebraDeLinhaNaoCriaEntrada` (T1).
2. **Banco caiu, mas o processo está vivo:** `/api/health` continua 200 (liveness) e `/api/health/pronto` responde 503 `BANCO_INDISPONIVEL` em até 3 s, sem travar → `prontoDetectaBancoForaEmAte3s` (T2).
3. **`/metrics` pedido na porta pública** (proxy repassando tudo): 404 → `metricasNaoRespondemNaPortaPublica` (T4).
4. **Sincronização parada sem ninguém abrir a tela** (suspensa por 401 numa sexta à noite): o alerta `SYNC_SUSPENSA` aparece na rota, na métrica e no log uma única vez por mudança de estado → `suspensaoViraAlertaUmaVez` (T5).
5. **YAML real com erro** (ausência com id que não está em `papeis.yaml`, feriado com data inválida): o validador aponta arquivo e código **sem imprimir o valor** (que pode ser nome de pessoa) e sai com 1 → `erroSemValorNoTexto` (T3).
6. **Backup que "roda" mas não restaura** (arquivo truncado): o script confere com `pg_restore --list` antes de registrar `OK`, e o teste de restauração compara contagens e somas de verificação → `backupTruncadoNaoViraOk` (T7) e `restauracaoIgualAoOriginal` (T8).

---

## Arquivos (mapa)

```
app/backend/src/main/resources/{logback.xml, db/migration/V{n}__execucao_de_backup.sql}
app/backend/src/main/kotlin/br/sidia/painel/
  operacao/{EncoderJsonSeguro.kt, MascaraDeLog.kt, Prontidao.kt, Metricas.kt, ServidorDeMetricas.kt}
  operacao/{AvaliadorDeAlertas.kt, Alerta.kt, AlertasRoutes.kt, BackupRunStore.kt}
  operacao/{ValidadorDeConfig.kt, ValidarConfigMain.kt}
  security/{Sessao.kt, AccessControl.kt}, config/Settings.kt, Application.kt
app/backend/gradle/libs.versions.toml, app/backend/build.gradle.kts
app/frontend/src/shell/{AvisoDeAlertas.tsx, AvisoDeAlertas.test.tsx}, app/frontend/src/api/client.ts, i18n
app/scripts/{validar-config.sh}, app/scripts/backup/{backup.sh, backup-loop.sh, verificar-restauracao.sh}
app/docker-compose.yml (perfil prod: backup e métricas), app/Dockerfile (HEALTHCHECK), app/.env.example
ops/prometheus/alertas.yml
.github/workflows/ci.yml (validação das configurações de exemplo)
docs/operacao/{configuracao.md, observabilidade.md, backup-e-restauracao.md, runbook-diario.md, runbook-incidentes.md, rotacao-de-segredos.md}
docs/implantacao.md, app/README.md, HANDOFF.md
```

---

### Tarefa 1: Logs estruturados sem segredos, com id de requisição

**Arquivos:** Criar `operacao/EncoderJsonSeguro.kt`, `operacao/MascaraDeLog.kt`; Modificar `resources/logback.xml`, `Application.kt` (`CallId` e `CallLogging`), `config/Settings.kt` (registra os segredos carregados na máscara), `gradle/libs.versions.toml` e `build.gradle.kts` (`ktor-server-call-id`, `ktor-server-call-logging` em `ktor = "3.3.1"`), `app/.env.example` (`PAINEL_LOG_FORMATO`); Teste `operacao/MascaraDeLogTest.kt`, `operacao/EncoderJsonSeguroTest.kt`, `operacao/LogDeRequisicaoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  object MascaraDeLog {
      fun registrarSegredos(valores: Collection<String>)   // tokens do Jira, DATABASE_PASSWORD, SESSION_SECRET(_ANTERIOR), OIDC_CLIENT_SECRET; ignora < 8 caracteres
      fun mascarar(texto: String): String                  // segredos registrados (também em forma URL, JSON e base64, como o JiraClient do Plano 9) + padrões abaixo
  }
  class EncoderJsonSeguro : EncoderBase<ILoggingEvent>()   // uma linha JSON por evento, UTF-8
  ```
  Padrões mascarados (sempre, mesmo sem registro): `Bearer <x>`, `Basic <x>`, `Authorization: <x>`, `(password|senha|secret|token|pat|apikey|api_key)\s*[=:]\s*<x>`, credenciais em URL (`//<usuario>:<senha>@` → `//***@`), `Cookie:`/`Set-Cookie:` com `painel_sessao=<x>`. A máscara troca só o valor por `***`.
- Formato do evento: `{"ts":"<ISO-8601 UTC>","nivel":"INFO","logger":"...","msg":"<mascarado>","requisicao":"<id>"?,"exc":{"classe":"...","msg":"<mascarado>","pilha":["Classe.metodo(Arquivo.kt:12)", ...até 20]}?}`. Strings escapadas como JSON (CR, LF e aspas viram escapes; nada quebra a linha).
- `logback.xml`: dois appenders (`JSON` com `EncoderJsonSeguro`; `TEXTO` com o padrão atual passando pela mesma máscara por um `MessageConverter` próprio), escolhidos por `<appender-ref ref="${PAINEL_LOG_FORMATO:-JSON}"/>`. Valores: `JSON` (padrão) e `TEXTO` (o README sugere `TEXTO` para desenvolvimento local). Valor inválido cai em `JSON`.
- `CallId`: aceita `X-Request-Id` do proxy só se casar `^[A-Za-z0-9-]{8,64}$`; senão gera UUID; devolve no cabeçalho `X-Request-Id`; põe no MDC como `requisicao`.
- `CallLogging` (nível INFO): uma linha por requisição com `metodo`, `rota` (modelo da rota do Ktor, por exemplo `/api/tarefa/{key}`; sem rota casada → `desconhecida`), `status`, `duracaoMs` e `requisicao`. **Sem** query string, usuário, cabeçalhos ou corpo. `/api/health*` e estáticos em DEBUG (fora do INFO).

- [ ] **Passo 1: Testes que falham:**
  - `MascaraDeLogTest`: `bearerEBasicMascarados`; `senhaEmChaveValorMascarada` (`password=abc12345` e `senha: abc12345`); `credencialEmUrlMascarada` (`https://u:p@h/x` → `https://***@h/x`); `segredoRegistradoMascaradoTambemEscapado` (token registrado aparece em `%2F` e `\/` → `***`); `cookieDeSessaoMascarado`; `textoComumIntacto`.
  - `EncoderJsonSeguroTest`: `umaLinhaJsonValidaPorEvento`; `quebraDeLinhaNaoCriaEntrada` (msg `"a\nINFO falso"` → uma linha, `\n` escapado); `excecaoComTokenSaiMascarada` (exceção com `Bearer abcdefghijklmnop` na mensagem → `***` em `exc.msg`); `pilhaLimitadaA20`; `mdcDaRequisicaoNoEvento`.
  - `LogDeRequisicaoTest` (com appender de lista em memória): `linhaPorRequisicaoComModeloDaRota` (`GET /api/tarefa/TG-1?instancia=samsung` → `rota=/api/tarefa/{key}`, sem `TG-1`, sem `instancia`); `semUsuarioNoLog` (principal `ana` → nenhum `ana` nas linhas); `requestIdDoProxyAceitoSoValido` (`X-Request-Id: ../../x` → gerado; `abc-12345678` → mantido); `healthNaoPoluiInfo`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*MascaraDeLogTest*' --tests '*EncoderJsonSeguroTest*' --tests '*LogDeRequisicaoTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa; subir o painel em modo dev e conferir uma linha JSON por requisição no `docker compose logs` (registrar no progresso, sem copiar valores).
- [ ] **Passo 5: Commit** `feat(operacao): logs estruturados em JSON com máscara de segredos e id de requisição`.

---

### Tarefa 2: Prontidão (readiness) com banco, migrações e configuração

**Arquivos:** Criar `operacao/Prontidao.kt`; Modificar `Application.kt`, `security/AccessControl.kt` (`rotasPublicas` ganha `/api/health/pronto`), `seguranca/InventarioDeRotasTest.kt` (`ROTAS_PUBLICAS` e `inventarioConhecido`), `app/Dockerfile` (`HEALTHCHECK` em `/api/health/pronto`), `app/docker-compose.yml` (healthcheck do perfil `prod`), `implantacao/ComposeTest.kt`; Teste `operacao/ProntidaoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class MotivoNaoPronto { BANCO_INDISPONIVEL, MIGRACAO_PENDENTE, CONFIG_INVALIDA }
  @Serializable data class ProntidaoDto(val pronto: Boolean, val codigo: String? = null)
  class Prontidao(private val ds: DataSource?, private val flyway: () -> Boolean /* sem pendentes */, private val clock: Clock,
                  private val tempoMaximo: Duration = 2.seconds) {
      suspend fun avaliar(): ProntidaoDto   // SELECT 1 com timeout de consulta; resultado guardado por 5 s para não sobrecarregar o banco
  }
  ```
  `GET /api/health/pronto` (público): 200 `{"pronto":true}` ou 503 `{"pronto":false,"codigo":"BANCO_INDISPONIVEL"}`. `/api/health` não muda (liveness: o processo responde). Sem `dataSource` (só testes) → 503 `BANCO_INDISPONIVEL`.

- [ ] **Passo 1: Testes que falham:** `prontoComBancoEMigracoes` (200); `prontoDetectaBancoForaEmAte3s` (Testcontainers: parar o contêiner; resposta 503 em menos de 3 s, medido); `migracaoPendente503` (`flyway` falso); `respostaSemDetalhes` (corpo só com `pronto` e `codigo`); `healthContinua200ComBancoFora`; `prontoEhPublico` (sem principal, também em produção); `InventarioDeRotasTest` atualizado e verde; `ComposeTest.healthcheckUsaPronto`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ProntidaoTest*' --tests '*InventarioDeRotasTest*' --tests '*ComposeTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `docker compose build` e `docker inspect` mostram o `HEALTHCHECK` novo.
- [ ] **Passo 5: Commit** `feat(operacao): verificação de prontidão com banco e migrações`.

---

### Tarefa 3: Validador de configuração antes da subida

**PONTO DE PARADA (antes do Passo 3, só para o documento): onde ficam os YAML reais e quem os edita** (spec, ponto de parada 3). O código não depende da resposta; o documento `docs/operacao/configuracao.md` sim.

**Arquivos:** Criar `operacao/ValidadorDeConfig.kt`, `operacao/ValidarConfigMain.kt`, `app/scripts/validar-config.sh`, `docs/operacao/configuracao.md`; Modificar `Application.kt` (`main(args)`: `validar-config` antes de qualquer subida), `.github/workflows/ci.yml` (passo no job `backend`: validar `app/config` de exemplo em modo dev), `ci/CiWorkflowTest.kt`; Teste `operacao/ValidadorDeConfigTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class ProblemaDeConfig(val codigo: String, val arquivo: String, val linha: Int? = null)   // nunca o valor
  data class ResultadoDaValidacao(val problemas: List<ProblemaDeConfig>, val arquivosOk: List<String>) { val ok: Boolean }
  object ValidadorDeConfig { fun validar(env: Map<String, String>, dir: Path, hoje: LocalDate): ResultadoDaValidacao }
  fun validarConfigMain(args: List<String>, env: Map<String, String> = System.getenv(), saida: (String) -> Unit = ::println): Int  // 0 ok, 1 problema, 2 uso
  ```
  Uso: `painel validar-config [--dir <caminho>]` (padrão `PAINEL_CONFIG_DIR`). **Não abre o banco, não sobe o servidor, não cria cliente Jira.** Saída: `OK <arquivo>` por arquivo válido e `ERRO <codigo> <arquivo>[:<linha>]` por problema; resumo final `VALIDACAO OK` ou `VALIDACAO FALHOU <n>`.
- Verificações (cada uma com código próprio):
  - `SETTINGS_INVALIDO` (o `Settings.fromEnv` falha; a linha cita só o nome da variável que o erro cita);
  - carga estrita de cada YAML pelo carregador da subida (`YAML_INVALIDO`, `CHAVE_DESCONHECIDA`, `ARQUIVO_AUSENTE`; em produção, modelo `.exemplo` no lugar do real → `EXEMPLO_EM_PRODUCAO`);
  - `PAPEIS_SEM_GERENTE_VIGENTE`; `ID_RESERVADO`;
  - cruzamentos: pessoa em `ausencias.yaml` ou `equipes.yaml` que não existe em `papeis.yaml` → `PESSOA_DESCONHECIDA`; squad de `equipes.yaml` sem líder vigente em `papeis.yaml` → `SQUAD_SEM_LIDER`; ausência com `ate < de` → `PERIODO_INVERTIDO`;
  - calendário: ano atual e o seguinte com feriados → senão `CALENDARIO_SEM_ANO` (com a linha do arquivo);
  - `status_map.yaml` cobre todo status citado nas fichas → senão `STATUS_DA_FICHA_SEM_MAPA`;
  - `jira_campos.yaml`: `customId` no formato `customfield_<n>` → senão `CUSTOM_ID_INVALIDO`;
  - LGPD: `DASHBOARD_DPO_ACEITE=true` sem referência válida → `ACEITE_SEM_REFERENCIA` (já falha no `Settings`; aqui só o código).
- `validar-config.sh <pasta>`: roda a imagem com a pasta montada `:ro` e `--entrypoint /app/bin/painel` `validar-config --dir /app/config`, repassando só as variáveis listadas no `.env.example` que estiverem definidas.
- `docs/operacao/configuracao.md`: lista dos YAML (o que cada um guarda, se tem dado pessoal, quem edita, com que frequência); o fluxo de edição conforme a resposta do ponto de parada; "validar antes de reiniciar" com o comando; **sem recarga a quente**: toda mudança vale depois de reiniciar (`docker compose --profile prod restart painel`); como reverter (versão anterior do arquivo).

- [ ] **Passo 1: Testes que falham** (pastas temporárias com YAML fictícios):
  - `exemplosPassamEmDev` (pasta `app/config` com `DASHBOARD_DEV_MODE=true` → `ok`).
  - `exemploEmProducaoFalha`.
  - `pessoaDesconhecidaNaAusencia` (com a linha); `squadSemLider`; `periodoInvertido`; `calendarioSemAnoSeguinte` (hoje `2030-11-02`, calendário até 2030 → `CALENDARIO_SEM_ANO`); `statusDaFichaSemMapa`; `customIdInvalido`; `papeisSemGerente`.
  - `erroSemValorNoTexto` (ausência de `joao.ficticio.x` desconhecido → a saída não contém `joao.ficticio.x`).
  - `naoAbreBancoNemJira` (sem `DATABASE_URL` alcançável e com `MockEngine` contador: 0 conexões, 0 requisições).
  - `codigosDeSaida` (0, 1 e 2 com argumento desconhecido).
  - `CiWorkflowTest.backendValidaConfigDeExemplo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ValidadorDeConfigTest*' --tests '*CiWorkflowTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e escrever `configuracao.md` conforme a resposta.
- [ ] **Passo 4: Rodar** → PASSA; `./gradlew installDist && build/install/painel/bin/painel validar-config --dir ../config` com `DASHBOARD_DEV_MODE=true` → `VALIDACAO OK`.
- [ ] **Passo 5: Commit** `feat(operacao): validador de configuração antes da subida`.

---

### Tarefa 4: Métricas Prometheus numa porta interna

**Arquivos:** Criar `operacao/Metricas.kt`, `operacao/ServidorDeMetricas.kt`; Modificar `Application.kt` (`main`: segundo conector), `gradle/libs.versions.toml` e `build.gradle.kts` (`ktor-server-metrics-micrometer` 3.3.1 e `micrometer-registry-prometheus` em versão exata resolvida na execução: a última 1.x estável no Maven Central, registrada no progresso), `sync/SyncService.kt` e `db/AccessLog.kt` e `db/Retention.kt` (incrementam contadores), `app/docker-compose.yml` (perfil `prod`: porta interna sem `ports`), `app/.env.example`; Teste `operacao/MetricasTest.kt`.

**Nota (hook):** o hook bloqueia a IA de editar o compose: entregar o trecho pronto ao usuário e perguntar (pergunta com opções).

**Interfaces:**
- Produz:
  ```kotlin
  class Metricas(val registro: PrometheusMeterRegistry) {
      fun execucaoDeSync(instancia: String, tipo: SyncKind, status: RunStatus)
      fun falhaDeAccessLog()
      fun retencaoRemoveu(categoria: String, n: Int)
      fun alertaAtivo(codigo: String, ativo: Boolean)
  }
  fun Application.servidorDeMetricas(metricas: Metricas)   // só GET /metrics; qualquer outro caminho → 404
  ```
  `PAINEL_METRICAS_PORTA` (padrão `9464`; 1024..65535, diferente de 8080) e `PAINEL_METRICAS_HOST` (padrão `127.0.0.1`; no perfil `prod` do compose, `0.0.0.0` só na rede interna, sem `ports`). O roteamento confere `call.request.local.localPort`: `/metrics` só responde na porta de métricas.
- Métricas (rótulos fechados; nenhum username, chave, squad, JQL ou URL):

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
  | JVM, GC, threads, Hikari | binders padrão do Micrometer | os do binder |

  `instancia` só aceita os ids de `INSTANCIAS_CONHECIDAS`.

- [ ] **Passo 1: Testes que falham:** `metricasNaPortaInterna` (`testApplication` com dois conectores ou o módulo de métricas isolado → 200, `text/plain; version=0.0.4`); `metricasNaoRespondemNaPortaPublica` (8080 → 404); `outrosCaminhosNaPortaInterna404`; `rotuloDeRotaEhModelo` (`/api/tarefa/TG-1` → `rota="/api/tarefa/{key}"`); `semDadoPessoalNosRotulos` (depois de chamadas como `ana` com `pessoa=ana` → o texto de `/metrics` não contém `ana`, `TG-1` nem `https://`); `syncIncrementaContador`; `falhaDeAccessLogConta`; `portaIgualA8080Falha`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*MetricasTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `feat(operacao): métricas Prometheus em porta interna, sem dados pessoais`.

---

### Tarefa 5: Alertas operacionais (avaliação, rota, métrica e log)

**PONTO DE PARADA (antes do Passo 3, só para a entrega externa): canal dos alertas** (spec, ponto de parada 1). A avaliação, a rota do gerente, a métrica e o log são feitos em qualquer resposta.

**Arquivos:** Criar `resources/db/migration/V{n}__execucao_de_backup.sql`, `operacao/Alerta.kt`, `operacao/AvaliadorDeAlertas.kt`, `operacao/AlertasRoutes.kt`, `operacao/BackupRunStore.kt`, `ops/prometheus/alertas.yml`; Modificar `Application.kt` (avaliação periódica a cada 5 min num laço próprio, `LacoDeAlertas(avaliador, clock, dormir)`, iniciado em `ApplicationStarted` e cancelado em `ApplicationStopping`, como o `Agendador`; **não** é um `SyncKind` novo, para não mexer no `CHECK` de `sync_run`; sem Jira), `config/Settings.kt` (`SYNC_PAT_VALIDADE_<INSTANCIA>`), `http/CodigosDeMensagem.kt`, `db/Retention.kt` (apaga `backup_run` pela `RETENTION_EXECUCOES_MONTHS`), `app/.env.example`; Teste `operacao/AvaliadorDeAlertasTest.kt`, `operacao/AlertasRoutesTest.kt`, `operacao/BackupRunStoreTest.kt`, `db/RetentionTest.kt` (`backupRunAntigoApagado`).

**Interfaces:**
- Migração: `CREATE TABLE backup_run (id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, tipo text NOT NULL CHECK (tipo IN ('BACKUP','RESTAURACAO')), started_at timestamptz NOT NULL, finished_at timestamptz, status text NOT NULL CHECK (status IN ('OK','FALHA')), tamanho_bytes bigint, codigo_erro text)` e índice `(tipo, started_at DESC)`. Sem nome de arquivo nem caminho. A retenção apaga linhas com mais de `RETENTION_EXECUCOES_MONTHS`.
- Produz:
  ```kotlin
  enum class CodigoAlerta { SYNC_SUSPENSA, SYNC_ATRASADA, RECONCILIACAO_SUSPEITA, RECONCILIACAO_ATRASADA, RETENCAO_FALHOU,
      ACCESS_LOG_FALHANDO, PAT_EXPIRANDO, PAT_EXPIRADO, CALENDARIO_SEM_PROXIMO_ANO, BACKUP_ATRASADO, RESTAURACAO_NAO_TESTADA,
      MODO_DEV_COM_JIRA_LIGADO }
  enum class GravidadeAlerta { ATENCAO, CRITICO }
  data class Alerta(val codigo: CodigoAlerta, val gravidade: GravidadeAlerta, val instancia: String?, val desde: Instant)
  interface BackupRunStore { fun ultima(tipo: String, status: String? = null): Instant?; fun registrar(tipo: String, status: String, inicio: Instant, fim: Instant, tamanho: Long?, codigoErro: String?) }
  class AvaliadorDeAlertas(estado: SyncStateStore, backups: BackupRunStore, metricas: Metricas, settings: Settings, calendario: BusinessCalendar, clock: Clock) {
      fun avaliar(): List<Alerta>        // ordem: CRITICO antes de ATENCAO, depois o código
      fun avaliarEPublicar(): List<Alerta>  // atualiza painel_alerta_ativo e loga WARN "ALERTA <codigo> ATIVO|RESOLVIDO" só na mudança de estado
  }
  ```
- Regras (valor padrão 25/10, reversível; relógio injetado; dias em Manaus):

  | Código | Condição | Gravidade |
  |---|---|---|
  | `SYNC_SUSPENSA` | `estado.suspensa(inst) != null` | CRITICO |
  | `SYNC_ATRASADA` | modo `PERIODICO` e último `INCREMENTAL` `OK` há mais de 3 × `SYNC_INTERVALO_MIN` (ou nunca, depois de 1 h no ar) | ATENCAO |
  | `RECONCILIACAO_SUSPEITA` | última `RECONCILIACAO` com `codigoErro == RECONCILIACAO_SUSPEITA` | CRITICO |
  | `RECONCILIACAO_ATRASADA` | modo `PERIODICO` e última `RECONCILIACAO` `OK` há mais de 26 h | ATENCAO |
  | `RETENCAO_FALHOU` | última `RETENCAO` `FALHA`, ou nenhuma `OK` há mais de 26 h | ATENCAO |
  | `ACCESS_LOG_FALHANDO` | contador de falhas cresceu nos últimos 60 min | CRITICO |
  | `PAT_EXPIRANDO` / `PAT_EXPIRADO` | `SYNC_PAT_VALIDADE_<INST>` (data `aaaa-mm-dd`) a 14 dias ou menos / já passou; só com a flag `true` | ATENCAO / CRITICO |
  | `CALENDARIO_SEM_PROXIMO_ANO` | a partir de 1º de novembro, o calendário não tem feriados do ano seguinte | ATENCAO |
  | `BACKUP_ATRASADO` | perfil de produção (`DASHBOARD_DEV_MODE=false`) e nenhum `BACKUP` `OK` há mais de 26 h | CRITICO |
  | `RESTAURACAO_NAO_TESTADA` | produção e nenhuma `RESTAURACAO` `OK` há mais de 35 dias | ATENCAO |
  | `MODO_DEV_COM_JIRA_LIGADO` | `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=true` | CRITICO |

- `GET /api/operacao/alertas` (só GERENTE; `executarAuditado`): `{"alertas":[{"codigo","gravidade","instancia","desde"}]}`.
- `ops/prometheus/alertas.yml`: grupo `painel` com `PainelAlertaAtivo` (`max by (codigo) (painel_alerta_ativo) > 0` por 5 min, rótulo `gravidade` copiado), `PainelForaDoAr` (`up{job="painel"} == 0` por 5 min) e `PainelNaoPronto` (sonda HTTP de `/api/health/pronto`, se a infraestrutura tiver blackbox; comentado como opcional). Arquivo para a infraestrutura carregar; o painel não o lê.
- Entrega externa conforme o ponto de parada: opção recomendada = só este arquivo (Alertmanager da infraestrutura); webhook só se o usuário escolher (subtarefa descrita na spec).

- [ ] **Passo 1: Testes que falham** (relógio fixo `2026-10-30T12:00Z`; estado em memória ou Testcontainers):
  - um teste por código: `suspensaoViraAlerta`, `syncAtrasadaDepoisDe3Intervalos` (29 min → não; 31 min → sim, com intervalo 10), `reconciliacaoSuspeita`, `reconciliacaoAtrasada26h`, `retencaoFalhou`, `accessLogFalhando`, `patExpirandoEm14Dias` (validade `2026-11-13` → sim; `2026-11-14` → não), `patExpirado`, `patSemFlagNaoAlerta`, `calendarioSemProximoAno` (hoje `2030-11-01`), `backupAtrasadoSoEmProducao`, `restauracaoNaoTestada35Dias`, `modoDevComJiraLigado`.
  - `suspensaoViraAlertaUmaVez` (três avaliações seguidas → um único log `ATIVO`; depois de limpar → um `RESOLVIDO`).
  - `metricaDeAlertaAcompanha`.
  - `AlertasRoutesTest`: `soGerente` (os demais papéis → 403, registrados); `semDadosPessoais`.
  - `BackupRunStoreTest`: `registraELeUltimaOk`.
  - `avaliacaoNaoFazRequisicao` (flag `true`, `MockEngine` contador → 0).
  - `alertasYmlValido` (lê o YAML: grupo `painel`, três regras, `expr` não vazia).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AvaliadorDeAlertasTest*' --tests '*AlertasRoutesTest*' --tests '*BackupRunStoreTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (e o webhook só se a resposta pedir).
- [ ] **Passo 4: Rodar** → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `feat(operacao): alertas operacionais com rota do gerente, métrica e regras para o Prometheus`.

---

### Tarefa 6: Aviso de alertas na tela do gerente

**Arquivos:** Criar `app/frontend/src/shell/AvisoDeAlertas.tsx`, `AvisoDeAlertas.test.tsx`; Modificar `shell/Shell.tsx`, `api/client.ts` (`fetchAlertas`), `screens/Sincronizacao/Sincronizacao.tsx` (lista de alertas no topo), i18n.

**Interfaces:**
- `AvisoDeAlertas`: só para GERENTE; consulta `/api/operacao/alertas` a cada 5 min (TanStack Query, `refetchInterval`); com alertas, faixa `role="status"` no topo: `▲ <n> alerta(s) crítico(s)` e `● <n> de atenção`, com link para `/sincronizacao#alertas`; cada alerta traduzido por código (`alertas.<CODIGO>`), com a instância e "desde <data e hora de Manaus>". Sem alertas, nada aparece. Erro na consulta não mostra a faixa (e não dispara a sessão expirada além do que o Plano 9 já faz).

- [ ] **Passo 1: Testes que falham:** `faixaSoParaGerente`; `criticoComTrianguloETexto` (pt e en; símbolo e texto); `semAlertasSemFaixa`; `linkParaSincronizacao`; `todoCodigoDeAlertaTraduzido` (lista fixa igual ao enum do backend; o teste do Plano 10 `todoCodigoTemTraducaoPtEEn` passa a cobrir `alertas.*`).
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shell src/screens/Sincronizacao` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build && node scripts/verificar-csp.mjs`.
- [ ] **Passo 5: Commit** `feat(ui): aviso de alertas operacionais para o gerente`.

---

### Tarefa 7: Backup agendado do PostgreSQL

**PONTO DE PARADA (antes do Passo 3, só para o destino externo e a cifragem): onde os backups ficam fora do servidor** (spec, ponto de parada 2). O backup local agendado é feito em qualquer resposta.

**Arquivos:** Criar `app/scripts/backup/backup.sh`, `app/scripts/backup/backup-loop.sh`, `docs/operacao/backup-e-restauracao.md`; Modificar `app/docker-compose.yml` (perfil `prod`: serviço `backup` e volume `backups`), `app/.env.example`, `implantacao/ComposeTest.kt`; Teste `operacao/BackupScriptTest.kt`.

**Nota (hook):** o hook bloqueia a IA de editar o compose: entregar o trecho pronto ao usuário e perguntar (pergunta com opções).

**Interfaces:**
- `backup.sh` (POSIX `sh`, roda na imagem `postgres:16` com o mesmo digest do serviço `postgres`):
  1. `umask 077`; destino `${BACKUP_DIR:-/backups}/painel-<aaaa-mm-dd>-<HHMM>.dump` (hora de Manaus, `TZ=America/Manaus`);
  2. `pg_dump -Fc --no-owner --no-privileges` com `PGHOST`, `PGUSER`, `PGDATABASE` e `PGPASSWORD` do ambiente (nunca na linha de comando);
  3. `pg_restore --list <arquivo> > /dev/null` (confere que o arquivo é legível); falha → apaga o arquivo e registra `FALHA` com `codigo_erro` `ARQUIVO_INVALIDO`;
  4. registra em `backup_run` (`psql -v ON_ERROR_STOP=1` com parâmetros `:'...'`): `BACKUP`, `OK`, início, fim, tamanho;
  5. apaga dumps com mais de `BACKUP_RETENCAO_DIAS` (padrão 14, 1..365);
  6. saída só com códigos e tamanhos, nunca a senha nem o caminho completo do host.
- `backup-loop.sh`: espera até `BACKUP_HORA` (padrão `02:00` de Manaus, `HH:mm`), roda `backup.sh`, dorme até o dia seguinte; `BACKUP_UMA_VEZ=1` roda uma vez e sai (para testes e para o runbook).
- Serviço `backup` no perfil `prod`: `image` igual ao `postgres` (digest), `entrypoint: ["sh", "/scripts/backup-loop.sh"]`, `volumes: [backups:/backups, ./scripts/backup:/scripts:ro]`, `environment` com `PG*` (senha de `${POSTGRES_PASSWORD:?}`), `BACKUP_HORA`, `BACKUP_RETENCAO_DIAS`, `TZ`; `read_only: true`, `tmpfs: /tmp`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`, **sem `ports`**, `depends_on: postgres (service_healthy)`.
- `docs/operacao/backup-e-restauracao.md`: o que é guardado, onde, por quanto tempo, quem acessa (contém dados pessoais), a cópia para fora do servidor conforme a resposta, como restaurar (comandos), como testar a restauração (T8) e a ligação com o runbook de incidentes.

- [ ] **Passo 1: Testes que falham:**
  - `BackupScriptTest` (Testcontainers `postgres:16`; copia os scripts para o contêiner com `withCopyFileToContainer`; executa com `execInContainer`): `backupGeraArquivoLegivelERegistraOk` (banco com Flyway aplicado e dados sintéticos pequenos; `BACKUP_UMA_VEZ=1` → um `.dump`, `pg_restore --list` ok, 1 linha `BACKUP OK` com tamanho > 0); `permissao600` (`stat -c %a` → `600`); `backupTruncadoNaoViraOk` (variável de teste `BACKUP_TESTE_TRUNCAR=1` corta o arquivo antes da conferência → `FALHA ARQUIVO_INVALIDO`, arquivo apagado); `retencaoApagaAntigos` (arquivos com `touch -d` de 15 dias → apagados com retenção 14); `saidaSemSenha` (a saída não contém o valor de `PGPASSWORD`).
  - `ComposeTest`: `backupNoPerfilProdSemPortas`, `backupComMesmoDigestDoPostgres`, `backupEndurecido` (`read_only`, `cap_drop`, `no-new-privileges`), `volumeDeBackupSeparado`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*BackupScriptTest*' --tests '*ComposeTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e escrever o documento conforme a resposta.
- [ ] **Passo 4: Rodar** → PASSA. Subir `docker compose --profile prod up -d` com valores descartáveis e `BACKUP_UMA_VEZ=1` no serviço de backup (ou `docker compose run --rm -e BACKUP_UMA_VEZ=1 backup`), conferir o arquivo no volume e `docker compose down -v`.
- [ ] **Passo 5: Commit** `build(docker): backup agendado do PostgreSQL com conferência e retenção`.

---

### Tarefa 8: Teste automatizado de restauração

**Arquivos:** Criar `app/scripts/backup/verificar-restauracao.sh`, `operacao/BackupRestauracaoTest.kt`; Modificar `docs/operacao/backup-e-restauracao.md`.

**Interfaces:**
- `BackupRestauracaoTest` (Testcontainers, dois contêineres `postgres:16` na mesma `Network`):
  1. contêiner A: Flyway até a última versão e dados com `GeradorSintetico.popular(ds, semente = 42, issues = 500, eventosPorIssue = 5, pessoas = 12, worklogs = 800)`, mais linhas em `access_log`, `sync_run` e `decisao_distribuicao`;
  2. `backup.sh` em A;
  3. contêiner B vazio; `pg_restore --no-owner --no-privileges -d <B>` do arquivo de A;
  4. compara, para cada tabela do esquema `public`: contagem de linhas e `md5(string_agg(<colunas em texto> ORDER BY id))`;
  5. Flyway `validate()` em B (mesmas migrações, sem pendências);
  6. `AppContext` com o `DataSource` de B: `/api/health/pronto` → 200 e uma rota de card como GERENTE → 200 com os mesmos números que em A.
- `verificar-restauracao.sh [<arquivo>]` (produção; padrão: o dump mais recente do volume): sobe um contêiner `postgres:16` temporário **sem portas e sem volume persistente**, restaura, compara as contagens por tabela com o banco de produção (só números), registra `RESTAURACAO OK` ou `FALHA` em `backup_run` do banco de produção, remove o contêiner. Saída só com nomes de tabela e números.

- [ ] **Passo 1: Testes que falham:** `restauracaoIgualAoOriginal`; `flywayValidaNoRestaurado`; `painelSobeNoRestaurado`; `numerosIguaisNoCard`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*BackupRestauracaoTest*'` → FALHA.
- [ ] **Passo 3: Implementar** o script e o teste.
- [ ] **Passo 4: Rodar** → PASSA. Rodar `verificar-restauracao.sh` uma vez contra o compose `prod` local descartável do Passo 4 da T7 (registrar no progresso só "OK" e as contagens).
- [ ] **Passo 5: Commit** `test(operacao): restauração do backup verificada contra o original`.

---

### Tarefa 9: Rotação de segredos sem derrubar sessões

**Arquivos:** Modificar `security/Sessao.kt` (Plano 8), `security/OidcSettings.kt`, `config/Settings.kt` (`SESSION_SECRET_ANTERIOR`), `app/.env.example`; Criar `docs/operacao/rotacao-de-segredos.md`; Teste `seguranca/SessaoRotacaoTest.kt`, `config/SettingsTest.kt`.

**Interfaces:**
- `OidcSettings` ganha `val segredoAnterior: ByteArray? = null` (de `SESSION_SECRET_ANTERIOR`, mesma regra de ≥ 32 bytes em base64; igual ao atual → `IllegalArgumentException` citando só os nomes).
- `Sessao`: o cookie é sempre **escrito** com o segredo atual; na **leitura**, tenta o atual e, se falhar a autenticação, o anterior; sessão lida com o anterior é regravada com o atual na mesma resposta (`Set-Cookie`). Sem o anterior, comportamento do Plano 8.
- `rotacao-de-segredos.md`, um procedimento por segredo, cada um com "quando", "como", "como conferir" e "como desfazer":
  - `SESSION_SECRET`: mover o atual para `SESSION_SECRET_ANTERIOR`, gerar o novo (`openssl rand -base64 48`), reiniciar; depois de `duracaoDaSessao` (8 h), remover o anterior e reiniciar. Para derrubar todas as sessões (incidente), trocar sem o anterior.
  - PAT do Jira: **feito pelo usuário** no Jira; atualizar a variável da instância e `SYNC_PAT_VALIDADE_<INST>`; reiniciar; conferir `/api/sync/status`. Nenhuma IA gera nem lê o PAT.
  - Senha do banco: `ALTER ROLE ... PASSWORD` dentro do contêiner, atualizar `POSTGRES_PASSWORD`/`DATABASE_PASSWORD` no cofre, reiniciar painel e backup.
  - `OIDC_CLIENT_SECRET`: com a equipe do provedor (Plano 13).

- [ ] **Passo 1: Testes que falham:** `sessaoComSegredoAnteriorAindaValida`; `sessaoLidaComAnteriorEhRegravadaComAtual`; `sessaoNovaUsaSegredoAtual`; `semAnteriorComoAntes` (`logoutLimpaSessao` e `cookieComFlagsSeguras` do Plano 8 continuam verdes); `anteriorIgualAoAtualFalha`; `anteriorCurtoFalha`; `segredoAnteriorRegistradoNaMascara` (T1).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SessaoRotacaoTest*' --tests '*OidcTest*' --tests '*SettingsTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e escrever o documento.
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(seguranca): rotação do segredo da sessão sem derrubar quem está logado`.

---

### Tarefa 10: Runbook de operação diária

**Arquivos:** Criar `docs/operacao/runbook-diario.md`, `docs/operacao/observabilidade.md`, `app/backend/src/test/kotlin/br/sidia/painel/docs/RunbooksDocTest.kt`.

**Interfaces:**
- `observabilidade.md`: formato dos logs (campos), como filtrar por `requisicao`, a lista de métricas, as regras de `ops/prometheus/alertas.yml`, a porta interna e como a infraestrutura coleta.
- `runbook-diario.md`, seções com títulos fixos (o teste lê os títulos):
  - **Rotina:** diária (faixa de alertas e `/sincronizacao`), semanal (backups no volume, `WARN` nos logs, ausências da semana atualizadas), mensal (`verificar-restauracao.sh`), anual (feriados do ano seguinte até 31/10, validade do PAT).
  - **Um item por `CodigoAlerta`** (`### ALERTA <CODIGO>`): o que significa, como confirmar, o que fazer, quando escalar e para quem. Ex.: `SYNC_SUSPENSA` → ler o código em `/api/sync/status`; 401/403 → PAT inválido ou sem permissão (usuário renova o PAT); 429 → esperar e reiniciar fora do horário; sempre "reiniciar o app retoma" (Plano 4A).
  - **Entrada e saída de pessoas:** editar `papeis.yaml` e `equipes.yaml` encerrando a vigência (`ate`) em vez de apagar a linha (a auditoria usa a vigência na data); validar; reiniciar.
  - **Ausências:** atualização semanal de `ausencias.yaml` (sem motivo), validar, reiniciar.
  - **Feriados do ano seguinte:** acrescentar ao calendário até 31/10 (o alerta começa em 1º/11); feriados municipais de Manaus a confirmar a cada ano.
  - **Pedido do titular** (Plano 11): consulta (tela Meus dados); eliminação pelo canal do DPO, com o procedimento técnico (o que pode ser apagado, o que fica por obrigação, registro fora do painel).
  - **Atualizar e reverter versão:** link para `docs/implantacao.md` (Plano 8) e o cuidado com migrações (reverter = restaurar o backup anterior à migração; nunca editar migração).
  - **Ligar e desligar o Jira:** só o usuário muda `JIRA_LIVE_ENABLED`; link para o runbook da sincronização (Plano 4A).
- `RunbooksDocTest`:
  - `cadaAlertaTemSecao` (cada valor de `CodigoAlerta` aparece como `### ALERTA <CODIGO>` no runbook diário);
  - `cadaVariavelDoEnvExampleEstaDocumentada` (cada variável de `app/.env.example` aparece em `docs/operacao/*.md`, `docs/implantacao.md` ou `app/README.md`);
  - `semHostRealTokenNemEmail` (como o `documentosDeJiraNaoTemHostRealTokenNemEmail`);
  - `jiraSoPeloUsuario` (o runbook diz que só o usuário muda `JIRA_LIVE_ENABLED` e não contém "autorização expressa").

- [ ] **Passo 1: Escrever o teste** e rodar `./gradlew test --tests '*RunbooksDocTest*'` → FALHA.
- [ ] **Passo 2: Escrever** os documentos.
- [ ] **Passo 3: Rodar** → PASSA.
- [ ] **Passo 4: Revisar** os passos de cada alerta contra o código (cada comando citado existe; cada caminho existe).
- [ ] **Passo 5: Commit** `docs(operacao): runbook de operação diária e observabilidade`.

---

### Tarefa 11: Runbook de incidentes

**Arquivos:** Criar `docs/operacao/runbook-incidentes.md`; Modificar `docs/RunbooksDocTest.kt`, `docs/lgpd/relatorio-para-o-dpo.md` (seção 10 aponta para cá).

**Interfaces:**
- Seções fixas: **Severidades** (S1 dados pessoais expostos ou painel fora do ar em horário de uso; S2 números errados ou sincronização parada há mais de 1 dia; S3 o resto) e quem decide (gerente; DPO em todo incidente com dado pessoal); **Registro** (modelo de registro do incidente: quando, o que, quem foi avisado, ações, sem copiar dado pessoal); e um playbook por cenário (`### INCIDENTE <NOME>`), cada um com **conter → investigar → corrigir → comunicar → aprender**:
  - `PAINEL_FORA_DO_AR` (prontidão 503; logs pela `requisicao`; banco; reiniciar; restaurar se preciso);
  - `BANCO_PERDIDO_OU_CORROMPIDO` (parar o painel; restaurar o último backup que passou na verificação; o que se perde entre o backup e a falha; com a flag ligada, a sincronização repõe o que vem do Jira; decisões e `access_log` desde o backup se perdem: registrar);
  - `SUSPEITA_DE_VAZAMENTO` (conter: tirar a porta do proxy ou parar o painel; trocar `SESSION_SECRET` **sem** o anterior para derrubar sessões; preservar `access_log` e logs; o usuário renova o PAT; investigar pela tela Registro de acesso; avisar o DPO imediatamente; comunicação à ANPD e aos titulares **conforme a regulamentação vigente, a confirmar com o DPO**);
  - `ACESSO_INDEVIDO` (alguém viu o que não devia: conferir `papeis.yaml` e vigências, `access_log`, corrigir, avisar o DPO);
  - `TOKEN_DO_JIRA_EXPOSTO` (o usuário desliga a flag e revoga o PAT no Jira; nenhuma IA acessa o Jira para isso; varredura do histórico; trocar);
  - `NUMEROS_ERRADOS` (comparar com o Jira só com a flag ligada pelo usuário; conferir `status_map`, ficha e avisos de qualidade dos dados; registrar diferença; o Plano 14 trata a validação completa);
  - `MODO_DEV_EM_PRODUCAO` (alerta `MODO_DEV_COM_JIRA_LIGADO` ou faixa vermelha em produção: desligar o modo dev e reiniciar imediatamente; tratar como possível exposição: seguir `SUSPEITA_DE_VAZAMENTO`).
- `RunbooksDocTest` ganha `cadaIncidenteTemAsCincoEtapas` e `incidenteComDadoPessoalAvisaODpo`, e `prazoDaAnpdNaoFixado` (o texto não contém "em 72 horas", "em 3 dias" nem outro prazo numérico junto de "ANPD").

- [ ] **Passo 1: Testes que falham:** os três acima.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RunbooksDocTest*'` → FALHA.
- [ ] **Passo 3: Escrever** o runbook.
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `docs(operacao): runbook de incidentes`.

---

### Tarefa 12: Fechamento

**Arquivos:** Modificar `docs/implantacao.md` (links para `docs/operacao/*`, variáveis novas, serviço de backup, porta de métricas), `app/README.md`, `app/.env.example` (todas as variáveis novas, sem valor de segredo), `HANDOFF.md`.

**Interfaces:** nenhuma de código.

- [ ] **Passo 1: Suítes completas:** `cd app/backend && ./gradlew test`; `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build && node scripts/verificar-csp.mjs`; `sh .claude/hooks/test-guard.sh`; varredura do Plano 9; `sh app/scripts/verificar-imagem.sh` (Plano 8) → todos verdes.
- [ ] **Passo 2: Subida de conferência** do perfil `prod` com valores descartáveis: painel recusa subir sem OIDC (Plano 8) — conferir então em modo dev local: logs em JSON, `/api/health/pronto` 200, `/metrics` só na porta interna (`docker compose exec painel` + `wget -qO- 127.0.0.1:9464/metrics | head`), `validar-config.sh app/config` com dev → OK. `docker compose down -v`.
- [ ] **Passo 3: Atualizar** o `HANDOFF.md` com as respostas dos três pontos de parada (e datas) e as pendências de infraestrutura (coleta das métricas, carga das regras de alerta, cópia dos backups para fora do servidor) para o Plano 13.
- [ ] **Passo 4: Push e PR** pelo fluxo do guia; CI verde.
- [ ] **Passo 5: Commit** `docs: fechamento do Plano 12 (operação em produção)`.

---

## Autoavaliação

**Cobertura (pedido e auditoria):**

| Item | Tarefa |
|---|---|
| A2: logs estruturados sem segredos (com teste) | T1 |
| A2: health/readiness com banco | T2 |
| A2: métricas | T4 |
| A2: alertas (sync suspensa, `RECONCILIACAO_SUSPEITA`, PAT expirando) e mais | T5, T6 |
| A1: validador de configuração na subida e no CI; procedimento de edição dos YAML | T3 |
| B.5: backup agendado | T7 |
| B.5: teste de restauração | T8 |
| A7: rotação de PAT e `SESSION_SECRET` | T9 |
| A7: runbook diário (feriados de cada ano, entrada e saída de pessoas) | T10 |
| A7: runbook de incidentes (suspeita de vazamento, restauração) | T11 |

**Consistência de tipos:** `Metricas` (T4) é usada pelo `AvaliadorDeAlertas` (T5); `BackupRunStore` e a tabela `backup_run` (T5) são escritas pelos scripts (T7, T8) e lidas pelos alertas (T5); `CodigoAlerta` (T5) é o que a tela (T6), as regras do Prometheus (T5) e o `RunbooksDocTest` (T10) enumeram; `MascaraDeLog.registrarSegredos` (T1) recebe também o `SESSION_SECRET_ANTERIOR` (T9); `ProntidaoDto` (T2) é o alvo do `HEALTHCHECK` e do compose.

**Fora deste plano:** implantação real (destino, proxy, TLS, cofre, coleta das métricas e carga das regras pela infraestrutura: Plano 13); validação dos números com dados reais (Plano 14); notificação por e-mail (não escolhida por padrão).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Logs em JSON por padrão**, com encoder próprio e máscara de segredos; `TEXTO` só por variável; nenhum usuário no log do servidor.
2. **Readiness separada da liveness**; o `HEALTHCHECK` da imagem passa a usar a readiness.
3. **Métricas Prometheus numa porta interna própria** (9464), nunca na 8080.
4. **Alertas avaliados dentro do painel** a cada 5 min, publicados em rota, métrica e log; entrega externa pelo Alertmanager da infraestrutura (ponto de parada 1).
5. **Backup diário às 02:00 de Manaus** com `pg_dump -Fc`, conferência por `pg_restore --list`, retenção de 14 dias no volume e registro em `backup_run`; cópia para fora do servidor pela infraestrutura (ponto de parada 2).
6. **Restauração testada** na suíte (Testcontainers) e mensalmente em produção pelo script, com alerta depois de 35 dias.
7. **Configuração sem recarga a quente**: validar, reiniciar (ponto de parada 3 para onde os arquivos ficam).
8. **Rotação do `SESSION_SECRET` com segredo anterior aceito só para leitura** durante uma duração de sessão.
