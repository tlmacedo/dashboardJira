# Fase 9: CI e endurecimento — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.
>
> **Reescrito em 27/10/2026** para a plataforma atual (SQLite embutido, sem Docker, sem PostgreSQL, sem Testcontainers). Vale o adendo `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` sobre qualquer trecho antigo. A versão anterior tinha 11 tarefas; esta tem 12 (ver "Itens descartados" e "Autoavaliação").

**Objetivo (Goal):** Pôr uma barreira automática antes de cada merge: um workflow do GitHub Actions que usa só JDK 21 (Temurin) e Node 22/pnpm, roda em **todo pull request** e em todo push na `main`, e executa o backend (`./gradlew test`, com SQLite em arquivo temporário), o frontend (`pnpm lint`, `pnpm vitest run`, `pnpm build`, verificação de CSP), os testes dos hooks e a varredura de dados pessoais e segredos que falha o build. A proteção da `main` que torna esses quatro checks obrigatórios é **ação do usuário no GitHub** (passo a passo em `docs/operacao/ci.md`). Em seguida, fechar os riscos web e de empacotamento que eram Minor adiados: CSP e cabeçalhos de segurança, proteção CSRF em todo método que altera estado, sessão expirada tratada na tela, pacote `installDist` que sobe em modo produção por padrão, modelo de variáveis `app/.env.example` que nunca traz o modo dev ligado (com teste de documentação), barreira automática contra o retorno de Docker, PostgreSQL e Testcontainers, e os Minor técnicos dos Planos 1 a 3A que ainda valem.

**Arquitetura (Architecture):** Nada de card novo. O CI é um workflow único (`.github/workflows/ci.yml`) com quatro jobs independentes (`backend`, `frontend`, `guarda`, `varredura`), sem serviços, sem contêiner e sem segredo além do opcional `VARREDURA_NOMES`, verificado por um teste estático do YAML. A varredura é um script POSIX (`scripts/ci/varrer-dados-pessoais.sh`) que roda igual no CI e na máquina (Git Bash no Windows), com testes próprios em shell. No backend entram dois plugins Ktor: `CabecalhosDeSeguranca` (em toda resposta) e `ProtecaoCsrf` (em todo método mutante, com cabeçalho próprio e conferência de `Origin`/`Sec-Fetch-Site`), um `ErroDto` por código para respostas de erro, e um `PerfilDeExecucao` que faz o pacote instalado (`bin/painel`) subir em modo produção quando ninguém definiu o modo. No frontend, o cliente HTTP ganha `getJson`/`enviarJson` (único lugar com `fetch`; manda o cabeçalho anti-CSRF) e um tratamento global de 401 que mostra "sessão expirada".

**Stack (Tech Stack):** GitHub Actions (runner `ubuntu-latest` hospedado pelo GitHub; **sem** serviços, Docker ou Testcontainers), JDK 21 (Temurin), Kotlin 2.2.20 + Ktor 3.3.1, kaml 0.104.0, SQLite embutido (sqlite-jdbc 3.50.3.0, Flyway 11.14.0, Hikari) com testes em arquivo temporário (`BancoEmArquivo`); React 19 + TypeScript (Vite 8, Vitest 5, Testing Library, oxlint), Node 22, pnpm 9.12.3 (corepack); `sh` (POSIX) e `jq` para os scripts e testes de hook; Windows 11 PowerShell como shell principal do executor.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-09-ci-e-endurecimento.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.6 "Produção × desenvolvimento" e "Ambiente", 10). **Auditoria de cobertura:** `docs/superpowers/cobertura-do-projeto.md` (lacunas C1, A3 e M8; B.4). **Origem dos Minor:** `HANDOFF.md` ("Minor adiados") e os ledgers locais `.superpowers/sdd/2026-10-2*-plano-0*/progress.md` (só leitura; git-ignorados) e `docs/superpowers/progresso/2026-10-25-plano-04a-sincronizacao.md`.

**Quando executar:** depois do Plano 4A na `main` (já está). Este plano **não depende do Plano 4** (cujas Tarefas 1 a 3 já estão na `main`); a única interação é a lista de rotas mutantes da Tarefa 4, que reflete a `main` do momento. **Não cria migração** (o esquema só tem a `V1__base.sql`; a próxima livre continua sendo a `V2`; confira `ls app/backend/src/main/resources/db/migration`).

**Ponto de parada: perguntar ao usuário (antes da Tarefa 1) — ordem de execução.** O índice dos planos manda executar o Plano 9 antes do Plano 4; o `HANDOFF.md` manda continuar o Plano 4 pela Tarefa 4 e só depois o Plano 9. Opções:
1. **Plano 9 agora, antes de continuar o Plano 4 (Recomendada).** Por quê: cada PR seguinte já nasce com a barreira (CI e varredura) e com o CSRF/CSP no lugar. Consequência: a Tarefa 4 do Plano 4 espera este PR; os testes dele passam a rodar no CI.
2. **Terminar o Plano 4 e só depois o Plano 9.** Por quê: não interrompe o que está em andamento. Consequência: os PRs do Plano 4 restante saem sem CI; este plano precisa então rebasear sobre as rotas novas do Plano 4 (a lista da Tarefa 4 muda).
3. **Plano 9 em paralelo ao Plano 4, em outra branch.** Por quê: duas frentes. Consequência: conflitos em `Application.kt` e no `build.gradle.kts`; quem mesclar depois reconcilia. Só se houver duas pessoas ou IAs.

Sem resposta, seguir a opção 1 (reversível).

## Convenção dos comandos (Windows 11 PowerShell é o principal; `sh` para macOS/Linux e para o CI)

Todos os comandos partem da **raiz do repositório**. O PowerShell 5.1 não tem `&&`: use `;` e confira o resultado (`BUILD SUCCESSFUL`, código de saída). `Push-Location`/`Pop-Location` evitam `cd` solto.

| O quê | PowerShell | sh |
|---|---|---|
| Backend, suíte completa | `Push-Location app\backend; .\gradlew.bat test; Pop-Location` | `cd app/backend && ./gradlew test` |
| Backend, testes por nome | `Push-Location app\backend; .\gradlew.bat test --tests "*NomeTest*"; Pop-Location` | `cd app/backend && ./gradlew test --tests '*NomeTest*'` |
| Frontend, suíte completa | `Push-Location app\frontend; pnpm lint; pnpm vitest run; pnpm build; Pop-Location` | `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build` |
| Frontend, por pasta | `Push-Location app\frontend; pnpm vitest run src/api; Pop-Location` | `cd app/frontend && pnpm vitest run src/api` |
| Scripts `sh` do repositório | `sh scripts/ci/test-varredura.sh` | `sh scripts/ci/test-varredura.sh` |
| Teste do hook do Claude Code | `sh .claude/hooks/test-guard.sh` | `sh .claude/hooks/test-guard.sh` |
| Teste do hook do Cline | `sh .clinerules/hooks/test-PreToolUse.sh` e `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1` | `sh .clinerules/hooks/test-PreToolUse.sh` |

No Windows o `sh`, o `jq` e o `grep` vêm do Git for Windows (`$env:ProgramFiles\Git\usr\bin`). Se o PowerShell não reconhecer `sh`, use `& "$env:ProgramFiles\Git\bin\sh.exe" <script>`. O `jq` é necessário para os testes de hook (instale com `winget install jqlang.jq`, ou use o do Git Bash; no CI ele já existe). Nos scripts `sh` do repositório use classes POSIX (`[[:space:]]`), nunca `\s`, `\d` ou `\b` (o `grep -E` do Git for Windows e o do macOS divergem).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que estes nomes existem. Se algum mudou, atualizar só os nomes deste plano e registrar `Ruling:` no arquivo de progresso. **Não existem hoje** `.github/`, `scripts/ci/`, `app/frontend/scripts/` nem `docs/operacao/`: este plano cria todos.

| Nome | Origem |
|---|---|
| `main()` (lê o arquivo de variáveis com `ArquivoEnv.ler` e `ArquivoEnv.combinarComFlagSoDoArquivo`, `Settings.fromEnv`, `AppContext.load`, `embeddedServer(Netty, port = 8080)`) e `Application.module(settingsIgnorado: Settings, context: AppContext, authenticator: Authenticator = autenticadorPadrao(context.settings), agendador: Agendador? = null)` | `Application.kt` |
| `installAccessControl(settings, authenticator, aoNegarSemPapel)`, `exigeAutenticacao(uri)` e `segmentosNormalizados(uri)` (internas), `rotasPublicas` (privada), `requireRole`, `ApplicationCall.principal` | `security/AccessControl.kt` |
| `registrarNegacaoSemPapel(auditoria, ctx, call, principal)` | `cards/TempoEmStatusRoutes.kt` |
| `AccessLog(ds).record(userId, route, filters, at)`, `AccessLogException` | `db/AccessLog.kt` |
| `spaEstatica(raiz)`, `resolverDentro` (privada), `perigoso`, `temExtensao`; pacote `br.sidia.painel` | `StaticSpa.kt` |
| `Settings` (data class; `fromEnv(env)`; `dashboardDevMode` padrão `true`; `toString` sem segredos), `ArquivoEnv` (`ler`, `interpretar` interna, `combinarComFlagSoDoArquivo`) | `config/Settings.kt`, `config/ArquivoEnv.kt` |
| `SearchRequest(jql: String, fields, expandChangelog, startAt, maxResults)`; `JqlQuery(instance, jql, fields, expandChangelog)`; `JiraClient.search(query)`, `searchAll(query)`, `count(query)` e as versões `internal` por `SearchRequest`; `RetryPolicy.delay`; `padraoEscapado`; `amostra(v, max)` | `jira/JiraModels.kt`, `jql/CardSpec.kt`, `jira/JiraClient.kt`, `jql/JqlValues.kt` |
| `SomenteAFabricaCriaClientesJira` (classe de teste que varre `src/main` por nome de arquivo), `FlagDesligadaZeroRequisicoesTest` | testes (Planos 3A e 4A) |
| `BancoEmArquivo`, `testContextComBanco(settings, banco, ...)`, `testContext(settings)`, `TestPaths.configDir`, `TestPaths.mainSrcDir` | `src/test/.../TestContextComBanco.kt`, `TestContext.kt`, `TestPaths.kt` |
| `PreparacaoDocsTest` (modelo de teste de documentação que lê arquivos do repositório) | `jql/PreparacaoDocsTest.kt` |
| `rootProject.name = "painel"`; `application { mainClass = "br.sidia.painel.ApplicationKt" }`; `tasks.test` com `environment("JIRA_LIVE_ENABLED", "false")` e as propriedades `painel.configDir` e `painel.mainSrcDir` | `app/backend/settings.gradle.kts`, `build.gradle.kts` |
| `fetchMe`, `fetchMeta` | `app/frontend/src/api/client.ts` |
| `ApiError(status)` e `pegar` (único `fetch` das telas de tempo), `fetchSyncStatus` (outro `fetch`), `QueryClient` em `App.tsx`, `Shell.tsx` (consulta de `meta`) | `screens/TempoEmStatus/api.ts`, `screens/Sincronizacao/api.ts`, `App.tsx`, `shell/Shell.tsx` |
| `.claude/hooks/test-guard.sh` e `.clinerules/hooks/test-PreToolUse.sh` (ambos saem com código diferente de 0 se algum caso falhar; precisam de `jq`; modo 100755 no git), `.clinerules/hooks/test-PreToolUse.ps1` | hooks |
| `app/.env.example` (a linha ativa do acesso ao Jira vale `false`), `app/iniciar.sh`, `app/iniciar.ps1`, `app/README.md` | `app/` |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`). Com ela desligada, ninguém acessa o Jira por caminho nenhum; só o usuário a muda. **Nenhum job de CI, teste ou script deste plano acessa o Jira**, qualquer que seja o valor da flag: o workflow fixa a flag em `"false"` no nível do workflow, não declara nenhum segredo `JIRA_*`, e o painel só lê a flag do arquivo de variáveis do app (que não existe no CI: linha ausente = desligado). **Nenhum texto deste plano, dos testes ou da documentação grava a flag ligada em forma de atribuição** (o hook barra); escreva "a flag ligada" por extenso.
- **Plataforma (adendo):** sem Docker, sem Dockerfile, sem compose, sem `.dockerignore`, sem imagem, sem digest, sem Testcontainers, sem PostgreSQL, sem H2, sem serviços no workflow. Banco de teste = SQLite real em arquivo temporário (`BancoEmArquivo`, `testContextComBanco`). A Tarefa 11 deixa isso garantido por teste.
- **Rede:** permitida para registros de pacotes (Maven Central, plugins do Gradle, npm) e para o GitHub (`gh`, Actions). **Nunca** para o Jira nem para um provedor OIDC real. Em PC corporativo com proxy ou espelho, ver a Fase 0 (adendo, seção 6).
- Nenhuma variável nova começa com `JIRA_` (o hook barra). Variáveis novas começam com `DASHBOARD_` ou `PAINEL_`.
- O repositório deve continuar **PRIVADO** (o histórico tem usernames reais). O CI nunca imprime termo de dado pessoal encontrado; só arquivo, linha e categoria.
- O workflow roda com `permissions: contents: read`; nada de `pull_request_target`, de `permissions: write-all`, de expressão `${{ }}` dentro de `run:` (entrada vem por `env:`), nem de credencial persistida no checkout.
- **A IA não altera a configuração do repositório no GitHub** (proteção da `main`, segredos, regras): isso é ação do usuário, documentada em `docs/operacao/ci.md`.
- Versões fixas como no projeto: Node 22, pnpm 9.12.3 via corepack, JDK 21 (Temurin). Actions referenciadas por tag de versão maior (`@v4`) e, se a execução conseguir resolver, por SHA de commit com a tag em comentário (Tarefa 2).
- Nenhuma mudança de resposta de rota existente além dos cabeçalhos novos. Nenhuma dependência npm nova; nenhuma dependência Gradle nova.
- **Assinatura do `module` preservada:** os planos seguintes citam `module(settings, context, authenticator)`; a Tarefa 8 só torna o primeiro parâmetro opcional.
- Fim de linha: o `.gitattributes` já fixa LF nos `.sh`; a Tarefa 1 acrescenta LF para `.github/**` e `scripts/ci/*.txt` (um `\r` no fim de linha quebra a leitura das listas pelo `sh`).
- Comentários em pt-BR; commits Conventional Commits pt-BR (`ci: ...`, `feat(seguranca): ...`, `fix(seguranca): ...`, `build: ...`, `test: ...`, `docs: ...`); `git add` só dos arquivos da tarefa (nunca `-A`; `legacy/.env.example` fica fora). Criar e editar arquivos com Write/Edit, nunca com heredoc ou Python no Bash.

## Itens descartados do plano anterior (e por quê)

| Item antigo | Motivo | Substituto |
|---|---|---|
| Tarefa 6: `Dockerfile` com modo dev desligado, `FROM` por digest, `.dockerignore` por lista de permissão, compose local, `ImagemDockerTest`, `docker compose build` | Não há Docker, imagem nem compose (CLAUDE.md, regra 6; adendo, seções 4 e 5) | Tarefa 6 (pacote `installDist` em modo produção por padrão) e Tarefa 7 (modelo de variáveis seguro, com teste) |
| Tarefa 10: `PostgresDeTeste`, Ryuk, `RetentionTest` intermitente | A intermitência era do Testcontainers ("Could not connect to Ryuk") e deixou de existir com a saída do Docker (`HANDOFF.md`); `RetentionTest` já usa `BancoEmArquivo` | Tarefa 11: teste que impede o retorno de Docker, PostgreSQL e Testcontainers. O laço de 10 execuções do `RetentionTest` vira apenas uma conferência na Tarefa 12 |
| CI: passo `docker info`, "Docker já vem no runner", relatório de falha de contêiner, runner self-hosted "com Docker" | Nenhum serviço nem contêiner | Workflow só com JDK e Node |
| Tarefa 8: máscara do token escapado, espera do retry cancelável, `sanearParaLog` | Já existem e têm teste: `padraoEscapado` (`tokenEscapadoEmJsonUrlEHtmlEhMascarado`, `base64DoBasicComBarraEMaisEscapadoEhMascarado`), `delayDoRetryRespeitaCancelamento`, `amostra()` em `InvalidJqlValueException` | Tarefa 9 fica só com a busca tipada (`SearchRequest` por `JqlQuery`) |
| `PostgresDeTeste` no job `backend`; "banco compartilhado entre testes" | Cada teste já cria um arquivo SQLite novo | — |

## Foco da revisão (Review Focus)

1. **Varredura que vaza o que encontra:** um nome proibido aparece no diff; o log do CI não pode mostrar o nome, só `arquivo:linha` e a categoria → `naoImprimeOTermoEncontrado` (T1).
2. **Check obrigatório que nunca roda ou muda de nome:** a proteção da `main` espera para sempre (ou deixa passar) se o job for renomeado, ganhar filtro de `paths` ou deixar de rodar em PR → `nomesDosJobsSaoOsChecksObrigatorios` e `gatilhosSaoPullRequestEPushNaMain` (T2).
3. **POST de outra origem com cookie de sessão válido** (CSRF clássico, formulário em site externo): 403 com `CSRF_AUSENTE` ou `ORIGEM_INVALIDA`, registrado em `access_log`, e o handler nunca roda → `postSemCabecalhoEh403SemExecutar` e `origemDeOutroSiteEh403` (T4).
4. **Rota mutante criada depois por outro plano sem a proteção** (Plano 7 `POST /api/distribuicao/decisao`, Plano 8 `POST /auth/logout`): o teste que percorre a árvore de rotas exige a proteção em todo método diferente de GET/HEAD/OPTIONS → `todoMetodoMutanteExigeCsrf` (T4).
5. **CSP que quebra a SPA em produção** (script ou estilo inline gerado pelo build) e **cabeçalho duplicado** (`nosniff` já era escrito pelo servidor estático): o build é conferido contra a política → `verificar-csp.mjs` no CI e `cspCompativelComOBuild` (T3).
6. **Pacote instalado e iniciado sem variáveis** (serviço do SO sem o arquivo de variáveis): sobe em modo produção, falha fechada por falta de configuração real, nunca "todos os dados sem login" → `pacoteSemVariaveisSobeEmModoProducao` e `pacoteSemConfiguracaoFalhaFechado` (T6).
7. **Modelo de variáveis copiado para produção com o modo dev ligado** → `modeloNaoLigaModoDevNemDemonstracao` (T7).
8. **Parâmetro `volta` do login apontando para outro site** (`//evil.example`): a SPA só aceita caminho interno → `voltaSoAceitaCaminhoInterno` (T5).
9. **Docker, PostgreSQL ou Testcontainers voltando por um PR** → `PlataformaSemDockerTest` (T11).

---

## Arquivos (mapa)

```
.github/workflows/ci.yml
.gitattributes, .gitignore
scripts/ci/{varrer-dados-pessoais.sh, test-varredura.sh, padroes-permitidos.txt}
app/backend/build.gradle.kts
app/backend/src/main/kotlin/br/sidia/painel/
  Application.kt, StaticSpa.kt
  config/PerfilDeExecucao.kt, config/Settings.kt
  http/ErroDto.kt, http/CabecalhosDeSeguranca.kt, http/ProtecaoCsrf.kt
  security/AccessControl.kt
  jira/JiraModels.kt, jira/JiraClient.kt
app/backend/src/test/kotlin/br/sidia/painel/
  TestPaths.kt, TestHttp.kt, SpaTest.kt, StaticSpaResolverTest.kt
  ci/CiWorkflowTest.kt, ci/PacoteInstalavelTest.kt, ci/PlataformaSemDockerTest.kt
  config/PerfilDeExecucaoTest.kt, config/SettingsTest.kt
  docs/ModeloDeVariaveisTest.kt
  http/CabecalhosDeSegurancaTest.kt, http/ProtecaoCsrfTest.kt
  security/AccessControlTest.kt
  jira/JiraClientTest.kt, JiraClientPagingTest.kt, JiraClientFactoryTest.kt, JiraClientRobustezTest.kt, TestJira.kt
  jira/VarreduraDeFontes.kt, jira/VarreduraDeFontesTest.kt, jira/SomenteAFabricaCriaClientesJira.kt
app/frontend/src/api/{client.ts, client.test.ts}
app/frontend/src/shell/{SessaoExpirada.tsx, SessaoExpirada.test.tsx, Shell.tsx}
app/frontend/src/screens/{TempoEmStatus/api.ts, Sincronizacao/api.ts}
app/frontend/src/i18n/{pt,en}.json
app/frontend/scripts/{verificar-csp.mjs, verificar-csp.test.ts}
app/.env.example, app/README.md
docs/operacao/ci.md
HANDOFF.md
```

---

### Tarefa 1: Varredura de dados pessoais e segredos

**Arquivos:** Criar `scripts/ci/varrer-dados-pessoais.sh`, `scripts/ci/test-varredura.sh`, `scripts/ci/padroes-permitidos.txt`; Modificar `.gitignore` (linha `scripts/ci/nomes-proibidos.local.txt`) e `.gitattributes` (`.github/** text eol=lf` e `scripts/ci/*.txt text eol=lf`).

**Interfaces:**
- Produz o script `sh scripts/ci/varrer-dados-pessoais.sh [--base <ref>] [--arvore]`:
  - **modo PR (padrão, `--base origin/main`)**: varre só as **linhas adicionadas** de `git diff <base>...HEAD` e as **mensagens** de `git log <base>..HEAD --format=%B`. Nunca varre e-mail de autor (`%ae`), que é real e legítimo. Localmente, rode antes `git fetch origin main` (no CI o checkout é completo).
  - **modo `--arvore`**: varre todos os arquivos rastreados (`git ls-files`), para uso local e relatório; o CI usa o modo PR.
  - Saída: uma linha por achado no formato `ACHADO <categoria> <arquivo>:<linha>` (no log de commits: `ACHADO <categoria> commit:<hash curto>`); **nunca o texto encontrado**. Última linha: `VARREDURA OK` ou `VARREDURA FALHOU: <n> achado(s)`. Código de saída 0 sem achados, 1 com achados, 2 em erro de uso.
- Categorias e regras (valor padrão, reversível; escrever as expressões com classes POSIX):

  | Categoria | Regra |
  |---|---|
  | `ARQUIVO_PROIBIDO` | arquivo (em qualquer modo) chamado `.env`, `.env.*` (exceto `.env.example`), `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `calendario.yaml`, `status_map.yaml`, `jira_campos.yaml`, `vinculos.yaml` (sem `.exemplo`/`.demo` no nome), `Filtros_Status_tool.txt`, `settings.local.json`, e arquivos de banco local: `*.db`, `*.db-wal`, `*.db-shm`, `*.db.lock` |
  | `EMAIL` | endereço de e-mail cujo domínio não está em `padroes-permitidos.txt` |
  | `SEGREDO` | cabeçalho `Bearer` seguido de 20 ou mais caracteres de token; prefixos `ghp_`, `gho_` e `github_pat_` seguidos de 20 ou mais caracteres; chave de acesso da AWS (`AKIA` + 16); linha de chave privada PEM; atribuição a `password`, `senha`, `secret` ou `token` (com `:` ou `=`) de um valor de 8 ou mais caracteres, sem diferenciar maiúsculas. Não contam: valores `<...>`, `${...}`, `***`, vazios, e valores que contêm `teste`, `exemplo`, `ficticio` ou `invalid` (para não acusar os testes existentes) |
  | `HOST_INTERNO` | URL `http://` ou `https://` cujo host não termina em `.invalid`, `.example`, `localhost` nem é `127.0.0.1`, e não está em `padroes-permitidos.txt` |
  | `NOME_PROIBIDO` | cada linha não vazia de `scripts/ci/nomes-proibidos.local.txt` (git-ignorado; só local) ou da variável de ambiente `VARREDURA_NOMES` (uma por linha; no CI vem de um segredo opcional do repositório), comparada como palavra inteira, sem diferenciar maiúsculas |

- `padroes-permitidos.txt` (versionado, uma entrada por linha, comentários com `#`): domínios de e-mail `exemplo.invalid`, `example.com`, `users.noreply.github.com` e `anthropic.com` (linha `Co-Authored-By` dos commits); hosts públicos de documentação e de pacotes que já aparecem no repositório (a execução roda `--arvore` uma vez e acrescenta só hosts **públicos** que achar, como `github.com`, `registry.npmjs.org`, `repo.maven.apache.org`, `plugins.gradle.org`, `services.gradle.org`; nunca um host da empresa). Linhas de `docs/superpowers/` (histórico) e de `legacy/` ficam fora do modo `--arvore`.

- [ ] **Passo 1: Testes que falham** (`scripts/ci/test-varredura.sh`, no estilo do `.claude/hooks/test-guard.sh`: cria um repositório git temporário em `mktemp -d`, faz um commit base, adiciona o caso e confere o código de saída e a saída):
  - `semAchadosSaiZero`: diff com código Kotlin comum → saída 0, só o resumo `VARREDURA OK`.
  - `emailRealFalha` (e-mail de domínio `.test` adicionado → 1, `ACHADO EMAIL arquivo.md:1`); `emailFicticioPassa` (domínio `exemplo.invalid` → 0).
  - `tokenFalha` (cabeçalho `Authorization` com `Bearer` e 25 ou mais caracteres → `SEGREDO`); `placeholderPassa` (valor `<seu-token>` e `${SENHA_DO_TESTE}` → 0); `valorDeTestePassa` (atribuição de token com valor contendo `teste` → 0).
  - `chavePrivadaFalha`; `hostInternoFalha` (URL `https` com host `.test` → `HOST_INTERNO`); `hostInvalidPassa` (host `.invalid` → 0).
  - `arquivoProibidoFalha` (`app/config/papeis.yaml` adicionado → `ARQUIVO_PROIBIDO`); `exemploPassa` (`papeis.exemplo.yaml` → 0); `bancoLocalFalha` (`app/data/painel.db` → `ARQUIVO_PROIBIDO`); `modeloDeVariaveisPassa` (`app/.env.example` → 0).
  - `nomeProibidoPorVariavel` (`VARREDURA_NOMES` com um nome de teste e o diff com esse nome → `NOME_PROIBIDO`); `nomeProibidoPorArquivoLocal`.
  - `naoImprimeOTermoEncontrado`: nos casos acima, a saída não contém o nome, o e-mail nem o token.
  - `mensagemDeCommitVarrida` (mensagem com e-mail de domínio não permitido → `ACHADO EMAIL commit:<hash>`); `emailDeAutorIgnorado` (commit com `--author` de domínio `.test` e conteúdo limpo → 0).
  - `linhaRemovidaNaoConta` (remover uma linha que tinha e-mail real → 0).
  - **O próprio `test-varredura.sh` não pode conter esses valores por extenso** (senão a varredura acha o arquivo de teste no PR): monte cada valor em tempo de execução por concatenação (`printf '%s@%s' parte1 parte2`), e não acrescente exceção em `padroes-permitidos.txt` para isso. Domínios de exemplo nos testes: `.test` (RFC 2606) para o que deve ser achado; `.invalid` para o que deve passar. **O mesmo vale para este plano, a spec e a documentação:** nada de e-mail, token ou host `.test` escritos por extenso.
- [ ] **Passo 2: Rodar** `sh scripts/ci/test-varredura.sh` (igual no PowerShell e no sh) → FALHA (script inexistente).
- [ ] **Passo 3: Implementar** `varrer-dados-pessoais.sh` em POSIX `sh` + `grep -E` + `git` (sem Python, sem `jq`), com classes POSIX. Escrever os arquivos com Write/Edit, nunca com heredoc no Bash.
- [ ] **Passo 4: Rodar** `sh scripts/ci/test-varredura.sh` → PASSA. Rodar `sh scripts/ci/varrer-dados-pessoais.sh --arvore` uma vez e tratar cada achado: host público → `padroes-permitidos.txt`; **qualquer achado de dado pessoal ou host da empresa no conteúdo atual → Ponto de parada: perguntar ao usuário** (spec, ponto de parada 4), sem copiar o termo para lugar nenhum.
- [ ] **Passo 5: Commit** `ci: varredura de dados pessoais e segredos que falha o build`.

---

### Tarefa 2: Workflow de CI no GitHub Actions

**Arquivos:** Criar `.github/workflows/ci.yml`, `app/backend/src/test/kotlin/br/sidia/painel/ci/CiWorkflowTest.kt`, `docs/operacao/ci.md`; Modificar `app/backend/build.gradle.kts` (systemProperty `painel.repoDir` = raiz do repositório, por caminho absoluto, como o `painel.configDir`), `app/backend/src/test/kotlin/br/sidia/painel/TestPaths.kt` (`val repoDir: Path`).

**Ponto de parada: perguntar ao usuário (antes do Passo 3) — onde o CI roda** (spec, ponto de parada 2). Opções:
1. **GitHub Actions com runner `ubuntu-latest` hospedado pelo GitHub (Recomendada).** Por quê: não exige infraestrutura; o job precisa só de JDK 21 e Node 22. Consequência: o repositório privado consome minutos da cota da conta (a suíte leva poucos minutos por PR); o código passa pelos servidores do GitHub, onde já está hospedado.
2. **Opção 1 mais um job do backend em `windows-latest`.** Por quê: o executor trabalha em Windows 11; pega diferença de caminho, fim de linha e link simbólico que o Linux não vê. Consequência: Windows custa o dobro de minutos; o nome do check vira `backend (windows-latest)` e a proteção da `main` precisa listar os dois.
3. **Runner self-hosted da empresa.** Por quê: só se a política da empresa proibir runners externos. Consequência: alguém da infraestrutura instala e mantém o runner (JDK 21, Node 22, `jq`); atrasa o primeiro CI verde.
4. **Sem CI no GitHub; só um script local `sh scripts/ci/rodar-tudo.sh`.** Por quê: custo zero. Consequência: a barreira volta a depender de alguém lembrar de rodar; a lacuna C1 da auditoria continua aberta.

Sem resposta, seguir a opção 1, que é reversível.

**Interfaces:**
- Produz `.github/workflows/ci.yml`. Esqueleto (a fonte da verdade são os testes abaixo):
  ```yaml
  name: CI
  on:
    pull_request:
    push:
      branches: [main]
  concurrency:
    group: ci-${{ github.ref }}
    cancel-in-progress: true
  permissions:
    contents: read
  env:
    JIRA_LIVE_ENABLED: "false"
  jobs:
    backend:
      name: backend
      runs-on: ubuntu-latest
      timeout-minutes: 30
      steps:
        - uses: actions/checkout@v4
          with: { persist-credentials: false }
        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: "21"
            cache: gradle
            cache-dependency-path: |
              app/backend/**/*.gradle*
              app/backend/gradle/libs.versions.toml
              app/backend/gradle/wrapper/gradle-wrapper.properties
        - name: Testes do backend (SQLite em arquivo temporário)
          working-directory: app/backend
          run: ./gradlew --no-daemon test
        - if: failure()
          uses: actions/upload-artifact@v4
          with: { name: relatorio-backend, path: app/backend/build/reports/tests/, retention-days: 7 }
    frontend:   # name: frontend; timeout-minutes: 15
      # checkout; setup-node (node-version: "22"); run "corepack enable" e "corepack prepare pnpm@9.12.3 --activate";
      # em working-directory app/frontend: pnpm install --frozen-lockfile; pnpm lint; pnpm vitest run; pnpm build
      # (a Tarefa 3 acrescenta, depois do build: node scripts/verificar-csp.mjs)
    guarda:     # name: guarda; timeout-minutes: 5
      # checkout; "command -v jq >/dev/null 2>&1 || sudo apt-get install -y jq";
      # sh .claude/hooks/test-guard.sh; sh .clinerules/hooks/test-PreToolUse.sh
    varredura:  # name: varredura; timeout-minutes: 5; checkout com fetch-depth: 0
      # env do passo: VARREDURA_NOMES (do segredo opcional de mesmo nome) e BASE_REF (de github.base_ref)
      # sh scripts/ci/test-varredura.sh
      # em pull_request: sh scripts/ci/varrer-dados-pessoais.sh --base "origin/$BASE_REF"
      # em push na main: sh scripts/ci/varrer-dados-pessoais.sh --base HEAD~1
  ```
  O workflow **não** define `DASHBOARD_DEV_MODE` (o teste do pacote da Tarefa 6 precisa partir de ambiente limpo). Nomes de job iguais ao id: são os **nomes dos checks** que a proteção da `main` vai exigir.
- SHAs das actions: **resolver na execução**. Se `gh api repos/actions/checkout/git/ref/tags/v4 --jq .object.sha` (e as demais) responder, fixar `uses: actions/checkout@<sha> # v4`; se `.object.type` vier `tag`, desreferenciar com `gh api repos/actions/checkout/git/tags/<sha> --jq .object.sha`. Se não responder, manter `@v4` e registrar `Ruling: actions por tag maior (sem rede para o GitHub) — custo se errado: tag movida por terceiro`. Não escrever SHA de memória.
- `docs/operacao/ci.md` (em pt-BR, sem URL de host da empresa, sem e-mail): (1) o que cada job prova; (2) como rodar o mesmo localmente (tabela PowerShell e sh da "Convenção dos comandos"); (3) o segredo opcional `VARREDURA_NOMES` (o usuário cria em *Settings → Secrets and variables → Actions → New repository secret*; nunca no repositório; um nome por linha); (4) **"Proteção da `main`: ação do USUÁRIO no GitHub"**, passo a passo (abaixo); (5) como ler um check vermelho sem expor dados (a varredura mostra só categoria, arquivo e linha).
  - **Proteção da `main` (o usuário faz; a IA nunca altera configuração do repositório):**
    1. Pré-requisito: o workflow já rodou pelo menos uma vez (o PR da Tarefa 12), senão o GitHub não oferece os nomes `backend`, `frontend`, `guarda` e `varredura` na busca de checks.
    2. No GitHub: repositório → *Settings* → *Rules* → *Rulesets* → *New ruleset* → *New branch ruleset*. Nome: `main protegida`. *Enforcement status*: `Active`.
    3. *Target branches* → *Add target* → *Include default branch*.
    4. Regras: marcar *Restrict deletions*; *Block force pushes*; *Require a pull request before merging* (com *Required approvals* em `0`, pois quem abre e quem mescla é a mesma pessoa; subir para `1` só se houver outro revisor); *Require status checks to pass* → *Add checks* → escolher `backend`, `frontend`, `guarda` e `varredura`; marcar *Require branches to be up to date before merging* (Recomendada: o fluxo é sequencial, então custa pouco e impede mesclar um PR testado contra uma `main` antiga).
    5. *Bypass list*: deixar vazia (o usuário também fica sujeito à regra; para uma emergência, desative o ruleset por alguns minutos e reative). Salvar com *Create*.
    6. Se o GitHub avisar que a regra não está disponível para repositório privado nesse plano da conta, a proteção automática não existe: use a alternativa de processo (a IA nunca mescla; o usuário só mescla depois de `gh pr checks <número>` mostrar os quatro checks verdes) e registre isso no `HANDOFF.md`. **Nunca** tornar o repositório público para contornar (o histórico tem usernames reais).
    7. Conferir (somente leitura): `gh api repos/{owner}/{repo}/rules/branches/main` lista as regras ativas (mesmo comando no PowerShell e no sh; `{owner}` e `{repo}` são preenchidos pelo `gh` dentro do clone). Aceite: abrir um PR de teste e ver os quatro checks marcados como *Required* com o botão de mesclar bloqueado enquanto estiverem pendentes ou vermelhos.

- [ ] **Passo 1: Testes que falham** (`CiWorkflowTest`, lê o YAML com `Yaml.default.parseToYamlNode` do kaml, sem rede; o YAML é 1.2, então a chave `on` é texto):
  - `quatroJobsObrigatorios`: `backend`, `frontend`, `guarda`, `varredura`, e nenhum outro.
  - `nomesDosJobsSaoOsChecksObrigatorios`: `name` de cada job igual ao id; nenhum `paths`, `paths-ignore` ou `branches-ignore` em lugar nenhum (filtro faria o check obrigatório nunca aparecer).
  - `gatilhosSaoPullRequestEPushNaMain`: `on` tem exatamente `pull_request` (sem filtro de branch) e `push` com `branches == [main]`.
  - `flagDoJiraFixadaEmFalseNoWorkflow`: `env.JIRA_LIVE_ENABLED == "false"`; nenhum outro `JIRA_` no arquivo inteiro (texto); nenhum `secrets.JIRA`.
  - `permissaoSoLeitura`: `permissions.contents == "read"` e nenhuma outra chave em `permissions`.
  - `semPullRequestTarget` e `soSegredoDaVarredura` (regex `secrets\.([A-Z_]+)` → só `VARREDURA_NOMES`).
  - `semExpressaoDentroDeRun`: nenhum valor de `run` contém `${{`. `checkoutSemCredencialPersistida`: todo `actions/checkout` tem `persist-credentials: false`.
  - `actionsPorVersaoFixa`: todo `uses:` termina em `@v<n>` ou `@<sha de 40 hexadecimais>`; nunca `@main`/`@master`.
  - `versoesFixas`: `java-version` 21 com `temurin`, `node-version` 22, `pnpm@9.12.3`, `--frozen-lockfile`.
  - `semServicosNemContainers`: nenhum job tem `services` nem `container`.
  - `backendRodaASuiteInteira`: um passo contém `./gradlew` e `test` e nenhum `-x test` nem `--tests`.
  - `frontendRodaLintTestesEBuild`; `guardaRodaOsDoisTestesDeHook` (`test-guard.sh` e `test-PreToolUse.sh`); `varreduraRodaTesteEScan` (`fetch-depth: 0`, `test-varredura.sh` e `varrer-dados-pessoais.sh`).
  - `timeoutsDefinidos`: todo job tem `timeout-minutes`.
- [ ] **Passo 2: Rodar** os testes → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*CiWorkflowTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*CiWorkflowTest*'`
- [ ] **Passo 3: Escrever** o workflow, o `docs/operacao/ci.md` e a propriedade `painel.repoDir` (`rootProject.layout.projectDirectory.dir("../..")`, caminho canônico) com `TestPaths.repoDir`.
- [ ] **Passo 4: Rodar** de novo → PASSA. Rodar `pnpm lint` uma vez localmente: se o oxlint acusar algo já existente, **não** desligar a regra nem tirar o passo do CI; corrigir em commit à parte ou perguntar ao usuário. Se `actionlint` estiver instalado na máquina, rodar `actionlint .github/workflows/ci.yml` (opcional; não instalar).
- [ ] **Passo 5: Commit** `ci: workflow com backend, frontend, guarda dos hooks e varredura`. A prova real do workflow é o primeiro PR deste plano ficar verde no GitHub (Tarefa 12).

---

### Tarefa 3: Cabeçalhos de segurança e CSP

**Arquivos:** Criar `http/CabecalhosDeSeguranca.kt`, `app/frontend/scripts/verificar-csp.mjs`; Modificar `Application.kt` (instala o plugin antes do roteamento), `StaticSpa.kt` (remove o `X-Content-Type-Options` escrito à mão: o plugin passa a escrevê-lo em toda resposta), `.github/workflows/ci.yml` (passo `node scripts/verificar-csp.mjs` no job `frontend`, depois do build); Teste `http/CabecalhosDeSegurancaTest.kt`, `ci/CiWorkflowTest.kt` (caso novo), `app/frontend/scripts/verificar-csp.test.ts`.

**Interfaces:**
- Produz `fun cabecalhosDeSeguranca(devMode: Boolean): ApplicationPlugin<Unit>` (plugin `createApplicationPlugin("CabecalhosDeSeguranca")` que acrescenta em **toda** resposta, inclusive 401, 403, 404 e estáticos; cada cabeçalho aparece **uma vez**):

  | Cabeçalho | Valor |
  |---|---|
  | `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
  | `X-Frame-Options` | `DENY` |
  | `X-Content-Type-Options` | `nosniff` |
  | `Referrer-Policy` | `no-referrer` |
  | `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |
  | `Cross-Origin-Opener-Policy` | `same-origin` |
  | `Cache-Control` | `no-store` **só** em respostas de `/api/*` e `/auth/*` que ainda não definiram o cabeçalho (o servidor estático define o seu) |
  | `Strict-Transport-Security` | `max-age=31536000` **só** com `devMode == false` |

  Sem `'unsafe-inline'` nem `'unsafe-eval'` (valor padrão, reversível). O React aplica `style={}` pelo CSSOM (hoje só nas barras de `TempoEmStatus.tsx`), que a CSP não bloqueia; o que ela bloqueia é `<style>` e `style="..."` escritos no HTML. Se o servidor de desenvolvimento do Vite precisar de outra política, ela fica no `vite.config.ts`, nunca no backend. Instalação em `Application.kt`: `install(cabecalhosDeSeguranca(settings.dashboardDevMode))` logo depois do `ContentNegotiation`.
- Produz `app/frontend/scripts/verificar-csp.mjs [pastaDist]` (padrão `dist`): lê todo `.html` da pasta; falha (saída 1) se houver `<script>` sem `src`, atributo `on*=` (manipulador inline), `style="` em HTML, `<style>`, `javascript:` ou `<link>`/`<script>` com URL absoluta fora da origem. Imprime arquivo e regra, nunca o conteúdo.

- [ ] **Passo 1: Testes que falham:**
  - `todaRespostaTemOsCabecalhos`: `/api/health`, `/api/meta`, `/` (SPA), um arquivo de `assets/`, uma rota `/api/inexistente` (404) e uma rota protegida sem principal (401) → todos os cabeçalhos da tabela (menos HSTS em dev).
  - `cabecalhosAparecemUmaVez`: `nosniff` e `Cache-Control` sem valor repetido (regressão do servidor estático).
  - `cspSemUnsafeInline` (o valor não contém `unsafe-`); `frameAncestorsNone`.
  - `hstsSoForaDoDevMode` (dev → ausente; `devMode=false` com autenticador de teste → presente).
  - `noStoreSoNaApi` (`/api/meta` → `no-store`; `/assets/app.js` → o `Cache-Control` do servidor estático, sem `no-store`).
  - `CiWorkflowTest.frontendVerificaCsp` (o job `frontend` roda `verificar-csp.mjs` depois do `build`).
  - `verificar-csp.test.ts` (com `// @vitest-environment node`): cria um `dist` em pasta temporária e roda `node scripts/verificar-csp.mjs <pasta>` por `child_process.spawnSync(process.execPath, ...)`. Casos: `index.html` no formato do Vite (`<script type="module" crossorigin src="/assets/x.js">` e `<link rel="stylesheet" href="/assets/x.css">`) → passa; `<script>alert(1)</script>` → falha; `onclick=` → falha; `style="color:red"` → falha; `<script src="https://cdn.exemplo.invalid/x.js">` → falha.
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*CabecalhosDeSegurancaTest*" --tests "*CiWorkflowTest*"; Pop-Location; Push-Location app\frontend; pnpm vitest run scripts; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*CabecalhosDeSegurancaTest*' --tests '*CiWorkflowTest*' && cd ../frontend && pnpm vitest run scripts`
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; depois `pnpm build` e `node scripts/verificar-csp.mjs` dentro de `app/frontend` → saída 0 (prova `cspCompativelComOBuild`). Subir o painel em modo dev (`cd app; .\iniciar.ps1` / `cd app && sh iniciar.sh`), abrir `http://127.0.0.1:8080` no navegador e conferir que o console não mostra violação de CSP (registrar no progresso). Conferir os cabeçalhos: PowerShell `(Invoke-WebRequest http://127.0.0.1:8080/api/meta).Headers`; sh `curl -sI http://127.0.0.1:8080/api/meta`.
- [ ] **Passo 5: Commit** `feat(seguranca): CSP e cabeçalhos de segurança em toda resposta`.

---

### Tarefa 4: Proteção CSRF em todo método mutante e `ErroDto`

**Arquivos:** Criar `http/ErroDto.kt`, `http/ProtecaoCsrf.kt`, `app/backend/src/test/kotlin/br/sidia/painel/TestHttp.kt`; Modificar `Application.kt` (instala depois do controle de acesso e do `ContentNegotiation`, com o mesmo estilo de gancho de registro), `security/AccessControl.kt` (acrescenta `internal fun ApplicationCall.principalOuNulo(): Principal?`, para o gancho ler o usuário sem lançar), `config/Settings.kt` (campo `origemPublica` e leitura de `DASHBOARD_ORIGEM_PUBLICA`), `app/.env.example` (variável comentada, sem valor), `app/frontend/src/api/client.ts`, `app/frontend/src/screens/TempoEmStatus/api.ts` (`ApiError` passa a estender `ErroDaApi`); Teste `http/ProtecaoCsrfTest.kt`, `config/SettingsTest.kt`, `app/frontend/src/api/client.test.ts`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class ErroDto(val codigo: String, val params: Map<String, String> = emptyMap())
  const val CABECALHO_CSRF = "X-Painel-CSRF"            // valor exigido: "1"
  data class ConfigCsrf(val origemPublica: String?)      // DASHBOARD_ORIGEM_PUBLICA (ex.: https://painel.empresa.invalid); nula = compara com o Host
  fun protecaoCsrf(cfg: ConfigCsrf, aoNegar: suspend (ApplicationCall, codigo: String) -> Unit): ApplicationPlugin<Unit>
  fun metodoMutante(m: HttpMethod): Boolean              // tudo menos GET, HEAD e OPTIONS
  ```
  ```kotlin
  // test: TestHttp.kt (helper compartilhado pelos planos seguintes)
  fun HttpRequestBuilder.comCsrf(origem: String = "http://localhost")   // cabeçalho X-Painel-CSRF: 1 e Origin
  ```
- Regra (valor padrão, reversível; ver spec D3): para todo método mutante, em qualquer caminho (`/api/**` e `/auth/**`):
  1. sem `X-Painel-CSRF: 1` → 403 `ErroDto("CSRF_AUSENTE")`;
  2. `Sec-Fetch-Site` presente e diferente de `same-origin` e `none` → 403 `ErroDto("ORIGEM_INVALIDA")`;
  3. `Origin` presente (inclusive o texto `null`) e diferente da origem esperada (`DASHBOARD_ORIGEM_PUBLICA`, ou `<esquema>://<Host>` da requisição) → 403 `ErroDto("ORIGEM_INVALIDA")`;
  4. senão, segue para o handler.

  O handler **nunca** roda quando negado. A negação chama `aoNegar`, que grava em `access_log` (via `AccessLog.record`; rota sem query string; filtro `motivo=<codigo>`; nunca o corpo) quando há banco; sem principal resolvido (`principalOuNulo() == null`), o `user_id` é `anonymous`. Falha ao gravar a auditoria é registrada no log (só o tipo da exceção) e não impede o 403. Sem CORS: o painel não envia `Access-Control-Allow-*`, então nenhum site externo consegue mandar o cabeçalho próprio.
- `Settings`: campo `origemPublica: String? = null` (no fim da lista, com valor padrão, para não quebrar chamadas nomeadas). `DASHBOARD_ORIGEM_PUBLICA` é opcional; normalizada para `esquema://host[:porta]` em minúsculas, sem barra final; precisa ser `https` fora do modo dev; sem caminho, consulta, fragmento nem credencial; inválida → `IllegalArgumentException` citando só o nome da variável. Entra no `toString` (não é segredo).
- Frontend (`client.ts`):
  ```ts
  export class ErroDaApi extends Error { status: number; codigo: string | null; params: Record<string, string> }
  export async function enviarJson<T>(metodo: 'POST' | 'PUT' | 'PATCH' | 'DELETE', url: string, corpo?: unknown): Promise<T>
  ```
  `enviarJson` manda `X-Painel-CSRF: 1`, `Content-Type: application/json` e `credentials: 'same-origin'`; resposta de erro com `ErroDto` vira `ErroDaApi` com `codigo`. `ApiError(status)` (usado pelas telas atuais) passa a `extends ErroDaApi`, mantendo o construtor, para `instanceof ApiError` e `instanceof ErroDaApi` valerem juntos.

- [ ] **Passo 1: Testes que falham** (`ProtecaoCsrfTest` instala no módulo de teste uma rota `POST /api/teste-csrf` que incrementa um contador; nunca em `src/main`):
  - `postSemCabecalhoEh403SemExecutar` (corpo `{"codigo":"CSRF_AUSENTE"}`, contador 0).
  - `postComCabecalhoPassa` (`comCsrf()` → 200, contador 1).
  - `origemDeOutroSiteEh403` (`Origin: https://evil.example` com o cabeçalho → `ORIGEM_INVALIDA`); `originNullEh403`; `secFetchSiteCrossSiteEh403`; `secFetchSiteSameSiteEh403`; `semOriginESemSecFetchPassa` (clientes que não são navegadores, com o cabeçalho).
  - `origemPublicaConfiguradaVale` (`DASHBOARD_ORIGEM_PUBLICA=https://painel.exemplo.invalid`; `Origin` igual → 200; `Host` diferente não importa).
  - `getNaoExigeCabecalho`; `putPatchEDeleteTambemExigem`.
  - `negacaoGravaAccessLog` (contexto com banco, `testContextComBanco(settings, banco)`: 1 linha com `filters` contendo `motivo` e sem o corpo; usuário `anonymous` sem principal); `falhaDaAuditoriaNaoImpedeOs403`.
  - `todoMetodoMutanteExigeCsrf`: percorre a árvore de rotas do módulo real (`application.plugin(RoutingRoot)` e os filhos recursivos; conferir no JAR do Ktor 3.3.1 os nomes de `Route.children`, `HttpMethodRouteSelector` e dos seletores de segmento; se a API pública não bastar, usar o `toString()` da rota e documentar no progresso) e, para cada rota com método mutante, faz a chamada sem o cabeçalho e espera 403 `CSRF_AUSENTE`. O teste também afirma que o conjunto de rotas mutantes encontrado é **igual** a uma lista esperada versionada no próprio teste (`val MUTANTES_ESPERADAS = emptySet<Pair<HttpMethod, String>>()` hoje: a `main` não tem rota mutante); o Plano 7 acrescenta `POST /api/distribuicao/decisao` e o Plano 8 `POST /auth/logout`, para que uma rota mutante nova obrigue a atualizar a lista e prove a proteção. Para não ser um teste vazio enquanto a lista real é vazia, o mesmo teste monta o módulo com a rota de teste `POST /api/teste-csrf` e confere que o percurso da árvore a encontra e a nega.
  - `SettingsTest`: `origemPublicaInvalidaFalha` (`http://painel.exemplo.invalid` com `DASHBOARD_DEV_MODE=false`; com caminho; com credencial), `origemPublicaVazioUsaPadrao`, `origemPublicaNormalizada`.
  - Frontend (`client.test.ts`): `enviarJsonMandaCabecalhoCsrf`; `erroDaApiTrazCodigo` (403 com `{"codigo":"ORIGEM_INVALIDA"}` → `ErroDaApi.codigo == "ORIGEM_INVALIDA"`); `apiErrorContinuaSendoErroDaApi`.
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*ProtecaoCsrfTest*" --tests "*SettingsTest*"; Pop-Location; Push-Location app\frontend; pnpm vitest run src/api; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*ProtecaoCsrfTest*' --tests '*SettingsTest*' && cd ../frontend && pnpm vitest run src/api`
- [ ] **Passo 3: Implementar.** Documentar `DASHBOARD_ORIGEM_PUBLICA` em `app/.env.example` (linha comentada, sem valor, texto em pt-BR: obrigatória atrás de proxy; o Plano 13 a torna obrigatória em produção).
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa das duas camadas (comandos da "Convenção dos comandos").
- [ ] **Passo 5: Commit** `feat(seguranca): proteção CSRF em todo método mutante, com código de erro`.

---

### Tarefa 5: Sessão expirada na tela

**Arquivos:** Criar `app/frontend/src/shell/SessaoExpirada.tsx`, `app/frontend/src/shell/SessaoExpirada.test.tsx`; Modificar `app/frontend/src/api/client.ts` (evento global e `getJson`), `app/frontend/src/screens/TempoEmStatus/api.ts` (`pegar` passa a usar `getJson`), `app/frontend/src/screens/Sincronizacao/api.ts` (`fetchSyncStatus` idem), `app/frontend/src/shell/Shell.tsx` (renderiza o diálogo), `app/frontend/src/i18n/{pt,en}.json`; Teste `app/frontend/src/api/client.test.ts`.

**Interfaces:**
- Produz em `client.ts`:
  ```ts
  export const EVENTO_SESSAO_EXPIRADA = 'painel:sessao-expirada'
  export function avisarSeSessaoExpirou(status: number): void      // 401 → window.dispatchEvent(new Event(EVENTO_SESSAO_EXPIRADA))
  export function caminhoDeVolta(atual: string): string | null      // só caminho interno: começa com "/", não com "//" nem "/\\", sem esquema; senão null
  export function urlDeLogin(volta: string | null): string          // "/auth/login" ou "/auth/login?volta=<encodeURIComponent>"
  export async function getJson<T>(url: string): Promise<T>        // GET com Accept: application/json; erro vira ErroDaApi (Tarefa 4)
  ```
  **`client.ts` passa a ser o único arquivo com `fetch`.** `fetchMe`, `fetchMeta`, `enviarJson`, `pegar` (tempo em status) e `fetchSyncStatus` usam as funções dele e chamam `avisarSeSessaoExpirou(resposta.status)` antes de lançar.
- `SessaoExpirada`: diálogo `role="alertdialog"` com foco no botão "Entrar de novo" (link para `urlDeLogin(caminhoDeVolta(location.pathname + location.search))`), texto "Sua sessão expirou. Entre de novo para continuar; os filtros da tela ficam na URL." (en: "Your session has expired. Sign in again to continue; the screen filters stay in the URL."). Não aparece em `devMode` (lido de `/api/meta`). 403 **não** dispara o diálogo (é falta de permissão, tratada pela tela).
- O backend de `/auth/login?volta=` é do Plano 8 (T2): ele também precisa aceitar só caminho interno (impacto registrado na spec). Enquanto o Plano 8 não existe, o link não leva a um login de verdade; é aceitável porque, sem provedor, nenhuma sessão chega a existir fora do modo dev.

- [ ] **Passo 1: Testes que falham:**
  - `respostas401DisparamSessaoExpirada` (mock de `fetch` com 401 em `fetchMe` e em `fetchSyncStatus` → evento disparado uma vez cada).
  - `respostas403NaoDisparam`.
  - `nenhumFetchForaDoCliente`: varre `src/**/*.ts(x)` (menos testes e `api/client.ts`) e não acha `fetch(` (use `import.meta.glob` com `?raw` ou `node:fs`, o que compilar com `tsc -b`).
  - `telaMostraAvisoComLinkDeLogin` (pt e en; o link é `/auth/login?volta=%2Fauditoria%3Fde%3D2026-10-01`).
  - `voltaSoAceitaCaminhoInterno`: `//evil.example`, `https://evil.example`, `/\\evil.example`, `javascript:alert(1)` → `null`; `/auditoria?de=2026-10-01` → igual.
  - `focoNoBotaoEEscNaoFecha` (o diálogo não some sem ação: a sessão realmente acabou).
  - `devModeNaoMostra`.
  - As suítes das telas existentes (`TempoEmStatus.test.tsx`, `Sincronizacao.test.tsx`) continuam verdes sem mudar as asserções.
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\frontend; pnpm vitest run src/shell src/api src/screens; Pop-Location`
  - sh: `cd app/frontend && pnpm vitest run src/shell src/api src/screens`
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; `pnpm lint` e `pnpm build` (comandos da "Convenção dos comandos").
- [ ] **Passo 5: Commit** `feat(ui): aviso de sessão expirada com volta segura ao login`.

---

### Tarefa 6: Pacote `installDist` que sobe em modo produção por padrão

**Contexto:** `Settings.dashboardDevMode` vale `true` quando ninguém define a variável (decisão do usuário, regra do `CLAUDE.md` para o desenvolvimento). Num pacote instalado como serviço (`./gradlew installDist`), um arquivo de variáveis esquecido faria o painel subir **sem login e com todos os dados**. Esta tarefa é o equivalente do antigo "imagem sobe com o modo dev desligado": só o pacote instalado muda o padrão; `iniciar.ps1`, `iniciar.sh` e `./gradlew run` continuam em modo dev.

**Ponto de parada: perguntar ao usuário (antes do Passo 3) — como o pacote decide o modo** (spec, ponto de parada 5). Opções:
1. **Perfil de produção só nos scripts do pacote (Recomendada).** `bin/painel` e `bin/painel.bat` passam `-Dpainel.perfil=producao`; `main()` trata a ausência de `DASHBOARD_DEV_MODE` como `false` nesse perfil. Por quê: não mexe no padrão do desenvolvimento nem nos testes. Consequência: quem quiser modo dev no pacote precisa escrever a linha explicitamente (faixa vermelha continua).
2. **Mudar o padrão global do `Settings` para `false`.** Por quê: um único comportamento. Consequência: contraria a regra atual do `CLAUDE.md`, quebra os testes e telas que dependem de `Settings()` e obriga `iniciar.*` a definir o modo dev; é decisão de produto do usuário.
3. **Só documentar, sem trava.** Por quê: zero código. Consequência: o pacote sem arquivo de variáveis sobe com todos os dados sem login; o risco fica por conta do operador.

Sem resposta, seguir a opção 1 (reversível: remover a propriedade do script).

**Arquivos:** Criar `app/backend/src/main/kotlin/br/sidia/painel/config/PerfilDeExecucao.kt`, `app/backend/src/test/kotlin/br/sidia/painel/config/PerfilDeExecucaoTest.kt`, `app/backend/src/test/kotlin/br/sidia/painel/ci/PacoteInstalavelTest.kt`; Modificar `Application.kt` (`main`), `app/backend/build.gradle.kts` (`tasks.startScripts`, `tasks.test` depende de `installDist`, propriedade `painel.installDir`), `TestPaths.kt` (`installDir`).

**Interfaces:**
- Produz:
  ```kotlin
  object PerfilDeExecucao {
      const val PROPRIEDADE = "painel.perfil"
      const val PRODUCAO = "producao"
      /** Sem perfil: devolve [env] igual. Perfil "producao": se DASHBOARD_DEV_MODE estiver ausente ou vazia, acrescenta "false"; valor explícito vale. Outro perfil: IllegalArgumentException citando só o nome da propriedade. Nunca acrescenta nem altera variáveis JIRA_*. */
      fun aplicar(perfil: String?, env: Map<String, String>): Map<String, String>
  }
  ```
  `main()`: `val env = PerfilDeExecucao.aplicar(System.getProperty(PerfilDeExecucao.PROPRIEDADE), ArquivoEnv.combinarComFlagSoDoArquivo(...))` e `Settings.fromEnv(env)`.
- Gradle: `tasks.startScripts { defaultJvmOpts = listOf("-Dpainel.perfil=producao") }` (só os scripts do pacote; o `run` usa `applicationDefaultJvmArgs` e fica sem a propriedade); `tasks.test { dependsOn(tasks.installDist); systemProperty("painel.installDir", <destino do installDist>) }`.

- [ ] **Passo 1: Testes que falham:**
  - `PerfilDeExecucaoTest`: `semPerfilNaoMudaNada`; `producaoDesligaModoDevQuandoAusente` (`Settings.fromEnv(aplicar("producao", emptyMap())).dashboardDevMode == false`); `producaoTrataVazioComoAusente`; `producaoRespeitaValorExplicito` (valor explícito de modo dev continua valendo; decisão registrada); `perfilDesconhecidoFalhaCitandoSoOMome`; `producaoNaoMexeNaFlagDoJira` (o mapa de saída não ganha chave `JIRA_*`).
  - `PacoteInstalavelTest` (lê `TestPaths.installDir`):
    - `pacoteTemScriptsELibs`: `bin/painel`, `bin/painel.bat` e, em `lib/`, o jar do painel e os jars `sqlite-jdbc` e `flyway-core`.
    - `migracoesVaoDentroDoJar`: o jar do painel contém `db/migration/V1__base.sql`, `logback.xml` e `demo/manifesto.yaml` (abrir com `java.util.zip.ZipFile`).
    - `pacoteNaoLevaConfigNemBanco`: nenhum arquivo do pacote chamado `.env*`, `*.db*`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `calendario.yaml`, `status_map.yaml`, `jira_campos.yaml` nem `vinculos.yaml` (equivalente do antigo `configRealNuncaReincluida`).
    - `scriptsFixamOPerfilDeProducao`: os dois scripts contêm `-Dpainel.perfil=producao`.
    - `pacoteSemVariaveisSobeEmModoProducao` e `pacoteSemConfiguracaoFalhaFechado` (um só teste de processo, com tempo máximo de 60 s): executa `bin/painel` (no Windows, `cmd /c bin\painel.bat`) em uma pasta temporária vazia, com `JAVA_HOME` igual ao `java.home` do teste, `PAINEL_ENV_FILE` apontando para um arquivo inexistente, `DATABASE_PATH` na pasta temporária e **sem** nenhuma variável `DASHBOARD_*` nem `JIRA_*` herdada; espera código de saída diferente de 0, a saída contendo "obrigatório em produção" (falta de configuração real) e **sem** a faixa de modo desenvolvimento nem o aviso de servidor escutando. A porta 8080 nunca é aberta.
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*PerfilDeExecucaoTest*" --tests "*PacoteInstalavelTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*PerfilDeExecucaoTest*' --tests '*PacoteInstalavelTest*'`
- [ ] **Passo 3: Implementar** (depois da resposta do ponto de parada).
- [ ] **Passo 4: Rodar** de novo → PASSA. Conferência manual (sem Jira, só o painel local): gerar o pacote e ver o caminho dos scripts.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat installDist; Pop-Location; Get-ChildItem app\backend\build\install\painel\bin`
  - sh: `cd app/backend && ./gradlew installDist && ls build/install/painel/bin`
  - Subir pelo modo de desenvolvimento (`cd app; .\iniciar.ps1` / `sh iniciar.sh`) e conferir `devMode: true` em `/api/meta` (PowerShell `Invoke-RestMethod http://127.0.0.1:8080/api/meta`; sh `curl -s http://127.0.0.1:8080/api/meta`), provando que o padrão do desenvolvimento não mudou.
- [ ] **Passo 5: Commit** `build: pacote instalado sobe em modo produção por padrão`.

---

### Tarefa 7: Modelo de variáveis e README coerentes com produção (teste de documentação)

**Arquivos:** Modificar `app/.env.example`, `app/README.md`; Criar `app/backend/src/test/kotlin/br/sidia/painel/docs/ModeloDeVariaveisTest.kt`.

**Interfaces:**
- Consome: `ArquivoEnv.interpretar(linhas)` (devolve só as atribuições ativas, ignora comentário), `Settings.fromEnv`, `PerfilDeExecucao.aplicar` (Tarefa 6), `TestPaths.repoDir`.
- Produz o teste de documentação e os textos:
  - `app/.env.example`: o bloco do modo dev explica, em pt-BR, que **o modelo copiado para produção nunca liga o modo dev**; `DASHBOARD_DEV_MODE` e `DASHBOARD_DEMO_DATA` ficam comentados; a única linha ativa do acesso ao Jira continua em `false`; `DASHBOARD_ORIGEM_PUBLICA` documentada (Tarefa 4); aviso de que o pacote instalado sobe em modo produção quando a variável falta e de que a linha explícita de modo dev, num pacote, mostra a faixa vermelha.
  - `app/README.md`: tabela de flags com o padrão de `DASHBOARD_DEV_MODE` separado por forma de subida (`true` em `iniciar.*` e `./gradlew run`; `false` no pacote instalado); seção "Pacote instalável" com os comandos de `pnpm build` e `installDist` e os caminhos do pacote (`app/backend/build/install/painel/bin/painel`); a frase do aviso de segurança atualizada.

- [ ] **Passo 1: Testes que falham** (`ModeloDeVariaveisTest`):
  - `modeloNaoLigaModoDevNemDemonstracao`: no mapa interpretado, `DASHBOARD_DEV_MODE` e `DASHBOARD_DEMO_DATA` são ausentes ou `false`.
  - `modeloSobeEmProducaoNoPacote`: `Settings.fromEnv(PerfilDeExecucao.aplicar("producao", modelo)).dashboardDevMode == false`.
  - `modeloMantemOJiraDesligado`: o valor ativo da flag do Jira é `false`.
  - `modeloNaoTrazSegredoNemEnderecoReal`: nenhuma variável ativa de instância (`*_URL`, `*_USER`, `*_TOKEN`), de segredo (`*_SECRET`) ou de senha com valor; nenhum texto `http://` ou `https://` no arquivo cujo host não termine em `.invalid`, `.example` nem seja `localhost`/`127.0.0.1`.
  - `modeloDocumentaOrigemPublicaEPacote`: o texto cita `DASHBOARD_ORIGEM_PUBLICA` e "pacote instalado".
  - `readmeDiferenciaOModoDevDoPacote`: o `README.md` cita `installDist`, `DASHBOARD_DEV_MODE` e "pacote instalado".
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*ModeloDeVariaveisTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*ModeloDeVariaveisTest*'`
- [ ] **Passo 3: Editar** o modelo e o README (Write/Edit; não abrir nem editar o arquivo de variáveis real).
- [ ] **Passo 4: Rodar** de novo → PASSA; `sh scripts/ci/varrer-dados-pessoais.sh --base origin/main` → 0.
- [ ] **Passo 5: Commit** `docs: modelo de variáveis e README sem modo dev em produção, com teste`.

---

### Tarefa 8: Controle de acesso por lista de permissão, `module` compatível e servidor estático

**Arquivos:** Modificar `security/AccessControl.kt`, `Application.kt`, `StaticSpa.kt`; Criar `StaticSpaResolverTest.kt`; Teste `security/AccessControlTest.kt`, `SpaTest.kt`.

**Interfaces:**
- `rotasPublicas` passa de `private` a `internal` (os Planos 8 e 12 acrescentam itens nessa mesma lista). `exigeAutenticacao(uri)` passa a ser `!ehPublica(uri)`, com `internal fun ehPublica(uri: String): Boolean` verdadeiro **só** para:
  1. `segmentos in rotasPublicas` (hoje `/api/health` e `/api/meta`);
  2. a raiz `/` (segmentos vazios);
  3. casca estática: primeiro segmento diferente de `api` e de `auth` (sem diferenciar maiúsculas), **todo** segmento casa `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` (logo, nada começa com `.`, nada vazio, nada com `%`, `\`, NUL ou `..`), no máximo 8 segmentos, e a URI começa com `/` e não com `//`.

  Mesmo comportamento observável de hoje para as rotas atuais; as diferenças são que qualquer forma não prevista passa a exigir login (negar por padrão em todo o servidor) e que `/auth/*` deixa de ser público por omissão (hoje `/auth/qualquer` cai na regra da "casca"). O Plano 8 acrescenta `/auth/login` e `/auth/callback` a `rotasPublicas`.
- `module`: o primeiro parâmetro vira `settingsIgnorado: Settings? = null` (posicional continua compilando; chamadas novas podem usar `module(context = ctx, ...)`). KDoc existente mantida: "a configuração efetiva é `context.settings`". Sem mudar a ordem nem os demais parâmetros.
- `StaticSpa.kt`: `resolverDentro` vira `internal` para teste; arquivo cujo nome começa com `.` em qualquer segmento → 404; `HEAD` em arquivo estático responde 200 com os mesmos cabeçalhos do `GET` e sem corpo (rota `head("/{...}")` própria; **sem** o plugin `AutoHeadResponse`, que exigiria dependência nova).

- [ ] **Passo 1: Testes que falham:**
  - `AccessControlTest`: `formasNaoPrevistasExigemLogin` (`/.env`, `/assets/.oculto`, `/a/b/c/d/e/f/g/h/i`, `/x%2Fy`, `/auth/qualquer` → exige); `casaDaSpaContinuaPublica` (`/`, `/auditoria`, `/assets/index-abc123.js`, `/favicon.svg`, `/squads/alpha/pessoas` → pública); os casos atuais (`/%61pi/x`, `//api/x`, `/API/x`, `/api/x/../health`) continuam exigindo; `rotaPublicaDoPlano8PodeSerAcrescentada` (adicionar `/auth/login` à lista em teste e ver `ehPublica` verdadeiro).
  - `StaticSpaResolverTest`: `symlinkParaForaDaRaizEhNulo` (cria raiz temporária com link simbólico para um arquivo fora → `null`; **no Windows a criação de link simbólico exige privilégio:** se `Files.createSymbolicLink` lançar `FileSystemException` ou `UnsupportedOperationException`, o teste usa `Assumptions.assumeTrue(false)` e fica ignorado, nunca verde por engano; o CI em Linux o executa); `symlinkDentroDaRaizResolve` (idem); `arquivoOcultoEhNulo`; `caminhoInvalidoEhNulo`.
  - `SpaTest`: `arquivoOcultoDaSpa404` (`.oculto` dentro de `dist`), `headEmAssetResponde200SemCorpo`.
  - `moduleSemSettingsCompila` (`testApplication { application { module(context = ctx) } }` → `/api/health` 200) e `moduleComSettingsPosicionalCompila` (forma antiga).
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*AccessControlTest*" --tests "*StaticSpaResolverTest*" --tests "*SpaTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*AccessControlTest*' --tests '*StaticSpaResolverTest*' --tests '*SpaTest*'`
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa do backend.
- [ ] **Passo 5: Commit** `fix(seguranca): acesso público por lista de permissão e endurecimento do servidor estático`.

---

### Tarefa 9: Cliente Jira — busca só com `JqlQuery`

**Nota:** a máscara do token escapado, a espera do retry cancelável e o saneamento de quebra de linha em mensagens já existem e têm teste (ver "Itens descartados"); esta tarefa fecha só o que falta do Minor "`SearchRequest(jql: String)` sem tipo". Todos os testes desta tarefa usam `MockEngine`: nenhuma requisição real, qualquer que seja a flag.

**Arquivos:** Modificar `jira/JiraModels.kt`, `jira/JiraClient.kt`; Criar `src/test/.../jira/TestJira.kt` (helper `fun req(jql: String = "x", fields: List<String> = listOf("summary"), ...)`); ajustar `JiraClientTest.kt`, `JiraClientPagingTest.kt`, `JiraClientFactoryTest.kt`, `JiraClientRobustezTest.kt`. Em `src/main` o único construtor de `SearchRequest(` fora de `JiraModels.kt` está em `JiraClient.kt` (conferir com `Grep`).

**Interfaces:**
- `data class SearchRequest(val query: JqlQuery, val startAt: Int = 0, val maxResults: Int = 100)`, com `val jql get() = query.jql`, `val fields get() = query.fields`, `val expandChangelog get() = query.expandChangelog`. Nenhum `SearchRequest` aceita `String` de JQL: só `JqlQuery`, que o `CardQueryFactory`/`JqlBuilder` produzem. `JiraClient.search(req)` e `count` internos passam a conferir `req.query.instance == config.id` (hoje a conferência só existe nas versões públicas por `JqlQuery`), fechando o desvio por `SearchRequest`.

- [ ] **Passo 1: Testes que falham:**
  - `buscaSoAceitaJqlQuery` (reflexão: o construtor público de `SearchRequest` não tem parâmetro `String` chamado `jql`, e tem um parâmetro do tipo `JqlQuery`).
  - `buscaDeOutraInstanciaPorSearchRequestEhRecusada` (`IllegalArgumentException` citando só os ids).
  - Os testes existentes de paginação, robustez, fábrica e `FlagDesligadaZeroRequisicoesTest` continuam verdes com o tipo novo (a troca nos testes é mecânica, pelo helper `req(...)`).
- [ ] **Passo 2: Rodar** → FALHA (compilação ou asserção).
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*JiraClient*" --tests "*JqlBuilderTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*JiraClient*' --tests '*JqlBuilderTest*'`
- [ ] **Passo 3: Implementar** e ajustar os chamadores.
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa; `FlagDesligadaZeroRequisicoesTest` e `SomenteAFabricaCriaClientesJira` verdes.
- [ ] **Passo 5: Commit** `fix(jira): busca só com JQL tipado`.

---

### Tarefa 10: Varredura da fábrica sem brechas (alias, `typealias`, referência e `copy` aninhado)

**Arquivos:** Criar `jira/VarreduraDeFontes.kt` **em `src/test`** (função pura), `jira/VarreduraDeFontesTest.kt`; Modificar `jira/SomenteAFabricaCriaClientesJira.kt` (passa a usar a função).

**Interfaces:**
- Produz (só em teste):
  ```kotlin
  data class Violacao(val arquivo: String, val linha: Int, val regra: String)
  fun varrerFontes(fontes: Map<String, String>, permitidos: Set<String>): List<Violacao>
  ```
  Regras: `JIRA_CLIENT_CONSTRUIDO` (`JiraClient(` fora dos permitidos, inclusive com espaço antes do parêntese e com nome qualificado `br.sidia.painel.jira.JiraClient(`); `JIRA_CLIENT_REFERENCIA` (`::JiraClient`); `JIRA_CLIENT_ALIAS` (`import ...JiraClient as X` e `typealias X = JiraClient`, e depois `X(` em qualquer arquivo); `HTTP_CLIENT_CONSTRUIDO` (`HttpClient(` fora dos permitidos); `SETTINGS_COPY` (`.copy(` aplicado a `Settings` ou a `settings`, inclusive aninhado como `ctx.settings.copy(` e `x.copy(settings = s.copy(`). Comentários de linha e de bloco são ignorados. A chave do mapa é o nome do arquivo (sem separador de pasta, para valer igual no Windows).
- Permitidos continuam os mesmos de hoje: `JiraClientFactory.kt` (que constrói `JiraClient(` e `HttpClient(`) e `JiraClient.kt` (que define `createJiraHttpClient`); `AppContext.kt` constrói a fábrica. Conferir a lista real com `Grep` antes de fixá-la.

- [ ] **Passo 1: Testes que falham** (`VarreduraDeFontesTest`, fontes sintéticas em memória): `construcaoDireta`, `construcaoQualificada`, `referenciaDeConstrutor`, `importComAlias`, `typealiasDepoisUsado`, `httpClientForaDaFabrica`, `copyDeSettingsAninhado`, `comentarioNaoConta`, `fabricaPermitida` (sem violação).
- [ ] **Passo 2: Rodar** → FALHA.
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*VarreduraDeFontesTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*VarreduraDeFontesTest*'`
- [ ] **Passo 3: Implementar** e trocar o corpo de `SomenteAFabricaCriaClientesJira` para chamar `varrerFontes` sobre `TestPaths.mainSrcDir`, mantendo os quatro testes atuais (`varreduraEncontraOsFontesDeProducao`, `jiraClientSoEhConstruidoNaFabrica`, `fabricaSoEhConstruidaNoAppContext`, `copyDeJiraLiveEnabledNaoApareceEmProducao`).
- [ ] **Passo 4: Rodar** `--tests "*VarreduraDeFontesTest*" --tests "*SomenteAFabricaCriaClientesJira*"` (sh: aspas simples) → PASSA (o código real continua sem violação).
- [ ] **Passo 5: Commit** `test(jira): varredura da fábrica cobre alias, typealias, referência e copy aninhado`.

---

### Tarefa 11: Barreira contra o retorno de Docker, PostgreSQL e Testcontainers

**Contexto:** a regra 6 do `CLAUDE.md` proíbe reintroduzir contêiner, servidor de banco, H2 ou mock de banco. Hoje só o texto protege; esta tarefa põe um teste no CI (substitui o antigo `PostgresDeTeste` e o `ImagemDockerTest`).

**Arquivos:** Criar `app/backend/src/test/kotlin/br/sidia/painel/ci/PlataformaSemDockerTest.kt`.

**Interfaces:**
- Consome `TestPaths.repoDir`. Produz o teste. Os termos proibidos são montados por concatenação no próprio arquivo (para ele não acusar a si mesmo) e o arquivo é excluído da varredura por nome. Pastas ignoradas: `node_modules`, `build`, `dist`, `.gradle`, `.kotlin`, `.git`, `data`. Não varre `docs/` (a documentação cita o que é proibido) nem `legacy/`.

- [ ] **Passo 1: Testes que falham** (podem passar de primeira se o repositório já estiver conforme; nesse caso, provar o poder do teste com uma mutação: criar um arquivo `Dockerfile` temporário em `app/`, ver o teste falhar e apagá-lo):
  - `semArquivosDeContainer`: nenhum arquivo chamado `Dockerfile*`, `Containerfile`, `docker-compose*`, `compose*.yml`/`.yaml` nem `.dockerignore` sob `app/`, `.github/` e a raiz.
  - `buildSemDependenciaDeServidorDeBanco`: `app/backend/build.gradle.kts`, `app/backend/gradle/libs.versions.toml` e `app/frontend/package.json` não contêm Testcontainers, PostgreSQL nem H2.
  - `codigoSemDriverNemContainerDeBanco`: nenhum `.kt` ou `.sql` sob `app/backend/src` contém `org.testcontainers`, `PostgreSQLContainer`, `org.postgresql`, `jdbc:postgresql`, `org.h2` nem `jdbc:h2`.
  - `workflowSemDockerNemServicos`: nenhum arquivo de `.github/` contém `docker`, `testcontainers`, `postgres`, `services:`, `container:` nem `image:` (sem diferenciar maiúsculas).
  - `scriptsDeSubidaSemDocker`: `app/iniciar.sh` e `app/iniciar.ps1` não executam `docker` (padrão de comando: `docker compose`, `docker run`, `docker build`, `docker-compose`); comentários "sem Docker" são permitidos.
- [ ] **Passo 2: Rodar** (e a mutação acima).
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test --tests "*PlataformaSemDockerTest*"; Pop-Location`
  - sh: `cd app/backend && ./gradlew test --tests '*PlataformaSemDockerTest*'`
- [ ] **Passo 3: Implementar** o que faltar (nada em produção; só o teste).
- [ ] **Passo 4: Rodar** de novo → PASSA (sem o arquivo da mutação); suíte completa.
- [ ] **Passo 5: Commit** `test: barreira contra o retorno de Docker, PostgreSQL e Testcontainers`.

---

### Tarefa 12: Fechamento, Minor restantes e primeiro CI verde

**Arquivos:** Modificar `HANDOFF.md` (seção "Minor adiados": cada item com "resolvido no Plano 9, Tarefa N", "já resolvido antes (com o teste)" ou "descartado com a saída do Docker"), `app/README.md` (seção "CI"), `docs/operacao/ci.md`.

**Interfaces:** nenhuma de código. Tabela dos Minor (a lista de referência está na spec, seção 4, D8) com estado final.

- [ ] **Passo 1: Suítes completas** (todas com saída 0):
  - PowerShell: `Push-Location app\backend; .\gradlew.bat test; Pop-Location; Push-Location app\frontend; pnpm lint; pnpm vitest run; pnpm build; node scripts/verificar-csp.mjs; Pop-Location; sh .claude/hooks/test-guard.sh; sh .clinerules/hooks/test-PreToolUse.sh; powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1; sh scripts/ci/test-varredura.sh; sh scripts/ci/varrer-dados-pessoais.sh --base origin/main`
  - sh: `cd app/backend && ./gradlew test && cd ../frontend && pnpm lint && pnpm vitest run && pnpm build && node scripts/verificar-csp.mjs && cd ../.. && sh .claude/hooks/test-guard.sh && sh .clinerules/hooks/test-PreToolUse.sh && sh scripts/ci/test-varredura.sh && sh scripts/ci/varrer-dados-pessoais.sh --base origin/main`
  - Conferência extra, sem custo: repetir `RetentionTest` 10 vezes e anotar quantas falhas (esperado 0; se falhar, abrir tarefa à parte, não mexer aqui). PowerShell: `1..10 | ForEach-Object { Push-Location app\backend; .\gradlew.bat test --tests "*RetentionTest*" --rerun-tasks -q; if ($LASTEXITCODE -ne 0) { "FALHOU $_" }; Pop-Location }`; sh: `cd app/backend && for i in 1 2 3 4 5 6 7 8 9 10; do ./gradlew test --tests '*RetentionTest*' --rerun-tasks -q || echo FALHOU $i; done`.
- [ ] **Passo 2: Atualizar** o `HANDOFF.md` e o README (seção "CI": o que roda, como rodar igual localmente, link para `docs/operacao/ci.md`), e o índice dos planos se ainda disser "11 tarefas" para a Fase 9.
- [ ] **Passo 3: Commit** `docs: fechamento do Plano 9 e estado dos Minor adiados`.
- [ ] **Passo 4: Conferir a visibilidade:** `gh repo view --json visibility` (igual nos dois shells) → `PRIVATE`. Se vier `PUBLIC`, **Ponto de parada: perguntar ao usuário** (guia, seção 6) antes de qualquer push. Rodar a varredura local de novo sobre o que será publicado (`sh scripts/ci/varrer-dados-pessoais.sh --base origin/main`).
- [ ] **Passo 5: Push e PR** (fluxo do guia, seção 4): `git push -u origin <branch>` e `gh pr create --base main` com descrição em pt-BR.
- [ ] **Passo 6: Conferir no PR** que os quatro jobs rodaram e ficaram verdes (`gh pr checks <número>` ou a aba *Checks*). Se um job falhar só no GitHub, corrigir na branch; nunca desligar o job nem marcar `continue-on-error`. Registrar o link do PR no progresso.
- [ ] **Passo 7: Ponto de parada: perguntar ao usuário — proteção da `main`** (spec, ponto de parada 3). Entregar o passo a passo de `docs/operacao/ci.md` e perguntar:
  1. **O usuário aplica a regra pelo GitHub, com o passo a passo (Recomendada).** Por quê: só vale se os quatro checks forem obrigatórios, e configuração do repositório é ação do usuário. Consequência: nenhum PR entra na `main` com CI vermelho, nem por engano da IA.
  2. **Plano da conta não permite a regra em repositório privado: seguir só por processo.** Por quê: sem alternativa técnica (e tornar o repositório público está fora de questão). Consequência: o CI avisa, mas não impede; a IA nunca mescla e o usuário confere `gh pr checks` antes de mesclar; registrar no `HANDOFF.md`.
  3. **Deixar sem proteção por enquanto.** Por quê: menos atrito enquanto só uma pessoa mescla. Consequência: igual à opção 2, sem o hábito de conferir; registrar no `HANDOFF.md`.

  A IA **nunca** aplica a proteção por conta própria. Depois do merge (feito pelo usuário), voltar à `main` e fazer `git pull`.

---

## Autoavaliação

**Cobertura (auditoria e pedido):**

| Item | Tarefa |
|---|---|
| C1: CI com backend (SQLite em arquivo temporário), frontend, `test-guard.sh`, `test-PreToolUse.sh` e varredura que falha o build, em todo PR | T1, T2, T12 |
| Check exigido antes do merge (proteção da `main`, ação do usuário, com passo a passo) | T2 (`docs/operacao/ci.md`), T12 (passo 7) |
| A3: CSP, X-Frame-Options, Referrer-Policy e demais cabeçalhos | T3 |
| A3: CSRF no único POST (e em qualquer outro que vier) | T4 |
| A3: sessão expirada na SPA | T5 |
| A3: "imagem com `DASHBOARD_DEV_MODE=false`, digest e `.dockerignore`" → equivalente para `installDist` + `app/.env.example` | T6, T7 |
| M8: `settingsIgnorado`, inverter `exigeAutenticacao`, `resolverDentro` com symlink, arquivos ocultos | T8 |
| M8: `SearchRequest` tipado | T9 |
| M8: varredura da fábrica (alias, `typealias`, `::`, `copy` aninhado) | T10 |
| M8: `delay` do retry com cancelamento, token escapado, log injection | já feitos, com teste (ver "Itens descartados") |
| M8: `RetentionTest` intermitente | descartado (era do Ryuk); conferência na T12 |
| Plataforma: nada de Docker/PostgreSQL/Testcontainers no CI nem no repositório | T2 (`semServicosNemContainers`), T11 |

**Consistência de tipos:** `ErroDto` (T4) é o mesmo que o Plano 10 usa para mensagens por código; `CABECALHO_CSRF` e `comCsrf()` (T4) são os nomes que os testes de `POST` dos Planos 7, 8 e 16 devem usar; `ErroDaApi`, `enviarJson`, `getJson` e `EVENTO_SESSAO_EXPIRADA` (T4, T5) são os do frontend dos Planos 10 e 16; `ehPublica`/`rotasPublicas` (T8) é a lista que os Planos 8 (rotas `/auth/*`) e 12 (readiness) estendem; `SearchRequest(query: JqlQuery, ...)` (T9) é o que os planos de sincronização e de worklog passam a chamar; `TestPaths.repoDir` (T2) e `TestPaths.installDir` (T6) são os caminhos que os testes de documentação e de pacote dos Planos 8, 12 e 13 reutilizam; `PerfilDeExecucao` (T6) é o que o Plano 13 usa para o serviço do SO.

**Fora deste plano:** piscada do tema no primeiro render e decisão `#1329A0` × `#0023A4` (identidade final, Plano 15); contraste de `--line` (Plano 8, T4); implantação real do pacote como serviço, publicação do pacote e provedor OIDC (Plano 13); backup do SQLite e readiness (Plano 12); `jira_sidia` (futuro). Um job de publicação do pacote, se o Plano 13 o quiser, entra no mesmo `ci.yml`.

## Decisões (valor padrão recomendado, reversível)

1. **CI no GitHub Actions com runner `ubuntu-latest` hospedado pelo GitHub**, só com JDK 21 e Node 22/pnpm, até o usuário dizer o contrário (ponto de parada 2 da spec). Windows ou self-hosted só se pedido.
2. **Quatro checks com nome igual ao id do job, sem filtro de `paths`**, para a proteção da `main` poder exigi-los; a proteção é aplicada pelo usuário.
3. **Varredura sobre o diff do PR e as mensagens de commit**, não sobre a árvore inteira nem sobre o e-mail de autor; nomes proibidos só em arquivo local git-ignorado ou em segredo opcional do repositório; a saída nunca mostra o termo.
4. **CSRF por cabeçalho próprio + conferência de `Origin`/`Sec-Fetch-Site`**, sem cookie de token: não depende da sessão do Plano 8 e funciona igual em dev e produção.
5. **CSP estrita sem `unsafe-inline`**; HSTS só fora do modo dev.
6. **Pacote `installDist` em modo produção por padrão (perfil de produção nos scripts do pacote)**; o desenvolvimento continua em modo dev (ponto de parada 5 da spec).
7. **`module` compatível** (primeiro parâmetro opcional) em vez de remover o parâmetro: não quebra os textos dos planos seguintes.
8. **Actions por tag maior quando o SHA não puder ser resolvido**, registrado como `Ruling`.
9. **Docker, PostgreSQL e Testcontainers barrados por teste** (T11), não só por texto.
