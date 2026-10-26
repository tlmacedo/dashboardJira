# Fase 9: CI e endurecimento — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Pôr uma barreira automática antes de cada merge (GitHub Actions com backend em Testcontainers, frontend, `test-guard.sh` e varredura de dados pessoais e segredos que falha o build) e fechar os riscos web e de imagem que eram Minor adiados: CSP e cabeçalhos de segurança, proteção CSRF em todo método que altera estado, sessão expirada tratada na tela, imagem com `DASHBOARD_DEV_MODE=false` por padrão, imagens base por digest, `.dockerignore` por lista de permissão e os Minor técnicos dos Planos 1 a 3A que ainda valem.

**Arquitetura (Architecture):** Nada de card novo. O CI é um workflow único (`.github/workflows/ci.yml`) com quatro jobs independentes, verificado por um teste estático do YAML. A varredura é um script POSIX (`scripts/ci/varrer-dados-pessoais.sh`) que roda igual no CI e na máquina, com testes próprios em shell. No backend entram dois plugins Ktor: `CabecalhosDeSeguranca` (em toda resposta) e `ProtecaoCsrf` (em todo método mutante, com cabeçalho próprio e conferência de `Origin`/`Sec-Fetch-Site`), e um `ErroDto` por código para respostas de erro. No frontend, o cliente HTTP ganha `enviarJson` (manda o cabeçalho anti-CSRF) e um tratamento global de 401 que mostra "sessão expirada".

**Stack (Tech Stack):** GitHub Actions (runners `ubuntu-latest` hospedados pelo GitHub, Docker já disponível), Kotlin 2.2.20 + Ktor 3.3.1, kaml 0.104.0, Testcontainers 2.0.3, PostgreSQL 16; React 19 + TypeScript (Vite 8, Vitest 5, Testing Library), pnpm 9.12.3 (corepack), Node 22, JDK 21; `sh`/`jq` para scripts.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-09-ci-e-endurecimento.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.6 "Produção × desenvolvimento" e "Ambiente", 10). **Auditoria de cobertura:** `docs/superpowers/cobertura-do-projeto.md` (lacunas C1, A3 e M8; B.4). **Origem dos Minor:** `HANDOFF.md` ("Minor adiados") e os ledgers locais `.superpowers/sdd/2026-10-2*-plano-0*/progress.md` e `.../2026-10-24-plano-03-tempo-em-status/final-review.md` (só leitura; git-ignorados).

**Quando executar:** logo depois do Plano 4A estar na `main` e **antes** dos Planos 4, 5, 6, 7 e 8. Não cria migração (de propósito: assim não desloca a numeração dos planos seguintes; o 4A termina em V6).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que o Plano 4A foi mesclado e que estes nomes existem. Se algum mudou, atualizar só os nomes deste plano e registrar `Ruling:` no arquivo de progresso.

| Nome | Origem |
|---|---|
| `Application.module(settingsIgnorado: Settings, context: AppContext, authenticator: Authenticator = ..., agendador: Agendador? = null)` | `Application.kt` (Plano 1; `agendador` do 4A, T7) |
| `installAccessControl(settings, authenticator, aoNegarSemPapel)`, `exigeAutenticacao(uri)`, `segmentosNormalizados(uri)`, `rotasPublicas` | `security/AccessControl.kt` (Plano 1) |
| `registrarNegacaoSemPapel(auditoria, context, call, principal)` | `cards/` (Plano 3) |
| `spaEstatica(dir)`, `resolverDentro(raiz, segmentos)` (privada) | `StaticSpa.kt` (Plano 1) |
| `SearchRequest(jql: String, fields, expandChangelog, startAt, maxResults)`, `JqlQuery(instance, jql, fields, expandChangelog)` | `jira/JiraModels.kt`, `jql/CardSpec.kt` |
| `JiraClient(..., delay: suspend (Duration) -> Unit)`, `mascarar(texto)` | `jira/JiraClient.kt` |
| `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest` | testes (Planos 3A e 4A) |
| `RetentionTest` | `db/RetentionTest.kt` (alterado no 4A, T7) |
| `fetchMe`, `fetchMeta` | `app/frontend/src/api/client.ts` |
| `.claude/hooks/test-guard.sh` (com `jf`/`jt`/`jx` do 4A, T11) | Plano 4A |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira por caminho nenhum; só o usuário muda a flag. **Nenhum job de CI, teste ou script deste plano acessa o Jira**, qualquer que seja o valor da flag: o workflow fixa `JIRA_LIVE_ENABLED: "false"` no nível do workflow e não declara nenhum segredo `JIRA_*`.
- **Rede para registros de pacotes, imagens e para o GitHub: permitida** (mesma regra da decisão 4 do Plano 8): baixar imagens base do Docker (inclusive para descobrir digests), dependências Maven/Gradle e npm, e usar `gh` para PR e para conferir a visibilidade do repositório. **Nunca** para o Jira nem para um provedor OIDC real.
- Nenhuma variável nova começa com `JIRA_` (o hook barra). Variáveis novas começam com `DASHBOARD_`.
- O repositório deve continuar **PRIVADO** (o histórico tem usernames reais). O CI nunca imprime termo de dado pessoal encontrado; só arquivo, linha e categoria.
- Nada de `pull_request_target`, nada de `permissions: write-all`: o workflow roda com `permissions: contents: read`.
- Versões fixas como no projeto: Node 22, pnpm 9.12.3 via corepack, JDK 21 (Temurin), PostgreSQL 16. Actions referenciadas por tag de versão maior (`@v4`) e, se a execução conseguir resolver, por SHA de commit com a tag em comentário (Tarefa 2).
- Nenhuma mudança de resposta de rota existente além dos cabeçalhos novos. Nenhuma dependência npm nova; nenhuma dependência Gradle nova.
- **Assinatura do `module` preservada:** os Planos 4 a 8 citam `module(settings, context, authenticator)`; a Tarefa 7 só torna o primeiro parâmetro opcional.
- Comentários em pt-BR; commits Conventional Commits pt-BR (`ci: ...`, `feat(seguranca): ...`, `fix(seguranca): ...`, `build(docker): ...`, `test: ...`, `docs: ...`); `git add` só dos arquivos da tarefa (nunca `-A`; `legacy/.env.example` fica fora).

## Foco da revisão (Review Focus)

1. **Varredura que vaza o que encontra:** um nome proibido aparece no diff; o log do CI não pode mostrar o nome, só `arquivo:linha` e a categoria → `naoImprimeOTermoEncontrado` (T1).
2. **POST de outra origem com cookie de sessão válido** (CSRF clássico, formulário em site externo): 403 com `CSRF_AUSENTE` ou `ORIGEM_INVALIDA`, registrado em `access_log`, e o handler nunca roda → `postSemCabecalhoEh403SemExecutar` e `origemDeOutroSiteEh403` (T4).
3. **Rota mutante criada depois por outro plano sem a proteção** (Plano 7 `POST /api/distribuicao/decisao`, Plano 8 `POST /auth/logout`): o teste que percorre a árvore de rotas exige a proteção em todo método diferente de GET/HEAD/OPTIONS → `todoMetodoMutanteExigeCsrf` (T4).
4. **CSP que quebra a SPA em produção** (script ou estilo inline gerado pelo build): o build é conferido contra a política → `verificar-csp.mjs` no CI e `cspCompativelComOBuild` (T3).
5. **Imagem rodada sem variáveis** (`docker run` direto): sobe com `DASHBOARD_DEV_MODE=false` e, portanto, exige OIDC (ou falha fechada), nunca "todos os dados sem login" → `imagemPadraoSemDevMode` (T6).
6. **Parâmetro `volta` do login apontando para outro site** (`//evil.example`): a SPA só aceita caminho interno → `voltaSoAceitaCaminhoInterno` (T5).

---

## Arquivos (mapa)

```
.github/workflows/ci.yml
scripts/ci/{varrer-dados-pessoais.sh, test-varredura.sh, padroes-permitidos.txt}
app/backend/src/main/kotlin/br/sidia/painel/
  http/ErroDto.kt, http/CabecalhosDeSeguranca.kt, http/ProtecaoCsrf.kt
  security/AccessControl.kt, StaticSpa.kt, Application.kt
  jira/JiraModels.kt, jira/JiraClient.kt, jql/CardQueryFactory.kt, sync/SyncService.kt (chamadas de busca)
app/backend/src/test/kotlin/br/sidia/painel/
  ci/CiWorkflowTest.kt, ci/ImagemDockerTest.kt
  http/CabecalhosDeSegurancaTest.kt, http/ProtecaoCsrfTest.kt, TestHttp.kt
  security/AccessControlTest.kt, SpaTest.kt, StaticSpaResolverTest.kt
  jira/JiraClientRobustezTest.kt, jira/SomenteAFabricaCriaClientesJira.kt, jira/VarreduraDeFontesTest.kt
  db/RetentionTest.kt, PostgresDeTeste.kt
app/frontend/src/api/{client.ts, client.test.ts}, app/frontend/src/shell/{SessaoExpirada.tsx, SessaoExpirada.test.tsx}
app/frontend/scripts/verificar-csp.mjs
app/Dockerfile, app/.dockerignore, app/docker-compose.yml, app/.env.example, app/README.md
docs/operacao/ci.md
```

---

### Tarefa 1: Varredura de dados pessoais e segredos

**Arquivos:** Criar `scripts/ci/varrer-dados-pessoais.sh`, `scripts/ci/test-varredura.sh`, `scripts/ci/padroes-permitidos.txt`; Modificar `.gitignore` (linha `scripts/ci/nomes-proibidos.local.txt`).

**Interfaces:**
- Produz o script `sh scripts/ci/varrer-dados-pessoais.sh [--base <ref>] [--arvore]`:
  - **modo PR (padrão, `--base origin/main`)**: varre só as **linhas adicionadas** de `git diff <base>...HEAD` e as **mensagens** de `git log <base>..HEAD --format=%B`. Nunca varre e-mail de autor (`%ae`), que é real e legítimo.
  - **modo `--arvore`**: varre todos os arquivos rastreados (`git ls-files`), para uso local e relatório; o CI usa o modo PR.
  - Saída: uma linha por achado no formato `ACHADO <categoria> <arquivo>:<linha>` (no log de commits: `ACHADO <categoria> commit:<hash curto>`); **nunca o texto encontrado**. Código de saída 0 sem achados, 1 com achados, 2 em erro de uso.
- Categorias e regras (valor padrão 25/10, reversível):

  | Categoria | Regra |
  |---|---|
  | `ARQUIVO_PROIBIDO` | arquivo rastreado (em qualquer modo) chamado `.env`, `.env.*` (exceto `.env.example`), `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `status_map.yaml`, `jira_campos.yaml` (sem `.exemplo`/`.demo`), `Filtros_Status_tool.txt`, `settings.local.json` |
  | `EMAIL` | `[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}` cujo domínio não está em `padroes-permitidos.txt` |
  | `SEGREDO` | `Bearer [A-Za-z0-9._~+/=-]{20,}`, `ghp_`/`gho_`/`github_pat_` seguidos de 20+ caracteres, `AKIA[0-9A-Z]{16}`, `-----BEGIN [A-Z ]*PRIVATE KEY-----`, `(password|senha|secret|token)\s*[:=]\s*["']?[^\s"'$<{]{8,}` (sem diferenciar maiúsculas; valores `<...>`, `${...}`, `***`, vazios e valores que contêm `teste`, `exemplo`, `ficticio` ou `invalid` não contam, para não acusar os testes existentes, como `token-de-teste-123`) |
  | `HOST_INTERNO` | URL `https?://` cujo host não termina em `.invalid`, `.example`, `localhost`, `127.0.0.1` nem está em `padroes-permitidos.txt` |
  | `NOME_PROIBIDO` | cada linha não vazia de `scripts/ci/nomes-proibidos.local.txt` (git-ignorado; só local) ou da variável de ambiente `VARREDURA_NOMES` (uma por linha; no CI vem de um segredo opcional do repositório), comparada como palavra inteira, sem diferenciar maiúsculas |

- `padroes-permitidos.txt` (versionado, uma entrada por linha, comentários com `#`): domínios de e-mail `exemplo.invalid`, `example.com`, `users.noreply.github.com`, `anthropic.com` (linha `Co-Authored-By`); hosts públicos de documentação e de pacotes que já aparecem no repositório (a execução roda `--arvore` uma vez e acrescenta só hosts **públicos** que achar, como `github.com`, `docs.atlassian.com`, `registry.npmjs.org`; nunca um host da empresa). Linhas de `docs/superpowers/` (histórico) e de `legacy/` ficam fora do modo `--arvore`.

- [ ] **Passo 1: Testes que falham** (`scripts/ci/test-varredura.sh`, no estilo do `test-guard.sh`: cria um repositório git temporário em `mktemp -d`, faz um commit base, adiciona o caso e confere o código de saída e a saída):
  - `semAchadosSaiZero`: diff com código Kotlin comum → saída 0, nada impresso além do resumo.
  - `emailRealFalha` (`fulano@empresa.test` adicionado → 1, `ACHADO EMAIL arquivo.md:1`); `emailFicticioPassa` (`ana@exemplo.invalid` → 0).
  - `tokenFalha` (`Authorization: Bearer abcdefghijklmnopqrstuvwxyz123` → `SEGREDO`); `placeholderPassa` (`token: <seu-token>` e `senha=${POSTGRES_PASSWORD}` → 0); `valorDeTestePassa` (`token = "token-de-teste-123"` → 0).
  - `chavePrivadaFalha`; `hostInternoFalha` (`https://servidor.intranet.test/x` → `HOST_INTERNO`); `hostInvalidPassa` (`https://servidor.exemplo.invalid` → 0).
  - `arquivoProibidoFalha` (`app/config/papeis.yaml` adicionado → `ARQUIVO_PROIBIDO`); `exemploPassa` (`papeis.exemplo.yaml` → 0).
  - `nomeProibidoPorVariavel` (`VARREDURA_NOMES=joao.real` e o diff com `joao.real` → `NOME_PROIBIDO`); `nomeProibidoPorArquivoLocal`.
  - `naoImprimeOTermoEncontrado`: nos casos acima, a saída não contém `joao.real`, `fulano@empresa.test` nem o token.
  - `mensagemDeCommitVarrida` (mensagem com e-mail de domínio não permitido → `ACHADO EMAIL commit:<hash>`); `emailDeAutorIgnorado` (commit com `--author="X <real@empresa.test>"` e conteúdo limpo → 0).
  - **O próprio `test-varredura.sh` não pode conter esses valores por extenso** (senão a varredura acha o arquivo de teste no PR): monte cada valor em tempo de execução por concatenação (`printf '%s@%s' fulano empresa.test`, `printf 'Bea%s %s' rer abcdef...`), e não acrescente exceção em `padroes-permitidos.txt` para isso. Domínios de exemplo nos testes: `.test` (RFC 2606) para o que deve ser achado; `.invalid` para o que deve passar.
  - `linhaRemovidaNaoConta` (remover uma linha com e-mail real → 0).
- [ ] **Passo 2: Rodar** `sh scripts/ci/test-varredura.sh` → FALHA (script inexistente).
- [ ] **Passo 3: Implementar** `varrer-dados-pessoais.sh` em POSIX `sh` + `grep -E` + `git` (sem Python, sem `jq`). Escrever os arquivos com Write/Edit, nunca com heredoc no Bash.
- [ ] **Passo 4: Rodar** `sh scripts/ci/test-varredura.sh` → PASSA. Rodar `sh scripts/ci/varrer-dados-pessoais.sh --arvore` uma vez e tratar cada achado: host público → `padroes-permitidos.txt`; **qualquer achado de dado pessoal ou host da empresa no conteúdo atual → PARAR e perguntar ao usuário** (ponto de parada 3 da spec), sem copiar o termo para lugar nenhum.
- [ ] **Passo 5: Commit** `ci: varredura de dados pessoais e segredos que falha o build`.

---

### Tarefa 2: Workflow de CI no GitHub Actions

**Arquivos:** Criar `.github/workflows/ci.yml`, `app/backend/src/test/kotlin/br/sidia/painel/ci/CiWorkflowTest.kt`, `docs/operacao/ci.md`; Modificar `app/backend/build.gradle.kts` (systemProperty `painel.repoDir` = raiz do repositório, por caminho absoluto, como o `painel.configDir`).

**PONTO DE PARADA (antes do Passo 3): perguntar ao usuário onde o CI roda** (spec, ponto de parada 1). Sem resposta, seguir com a opção recomendada, que é reversível.

**Interfaces:**
- Produz `.github/workflows/ci.yml`:
  - `name: CI`; gatilhos `pull_request` (qualquer branch) e `push` em `main`; `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }`; `permissions: { contents: read }`; `env: { JIRA_LIVE_ENABLED: "false", DASHBOARD_DEV_MODE: "true" }` no nível do workflow.
  - Job `backend` (`ubuntu-latest`, `timeout-minutes: 30`): `actions/checkout@v4`; `actions/setup-java@v4` (`distribution: temurin`, `java-version: 21`, `cache: gradle`); `docker info` (prova que há Docker para o Testcontainers); `cd app/backend && ./gradlew --no-daemon test`; em falha, `actions/upload-artifact@v4` com `app/backend/build/reports/tests/` (retenção 7 dias).
  - Job `frontend` (`timeout-minutes: 15`): checkout; `actions/setup-node@v4` (`node-version: 22`); `corepack enable && corepack prepare pnpm@9.12.3 --activate`; `pnpm install --frozen-lockfile`; `pnpm lint`; `pnpm vitest run`; `pnpm build`; `node scripts/verificar-csp.mjs` (criado na Tarefa 3; até lá o passo não existe) — todos com `working-directory: app/frontend`.
  - Job `guarda` (`timeout-minutes: 5`): checkout; `sudo apt-get install -y jq` só se `jq` faltar; `sh .claude/hooks/test-guard.sh`.
  - Job `varredura` (`timeout-minutes: 5`): checkout com `fetch-depth: 0`; em `pull_request`, `sh scripts/ci/varrer-dados-pessoais.sh --base origin/${{ github.base_ref }}`; em `push`, `--base HEAD~1`; variável `VARREDURA_NOMES: ${{ secrets.VARREDURA_NOMES }}` (segredo opcional; vazio = só as regras genéricas); `sh scripts/ci/test-varredura.sh`.
  - Nenhuma referência a `secrets.` além de `VARREDURA_NOMES`; nenhum `pull_request_target`; nenhum `JIRA_` além da flag `"false"`.
- SHAs das actions: **resolver na execução**. Se `gh api repos/actions/checkout/git/ref/tags/v4` (e as demais) responder, fixar `uses: actions/checkout@<sha> # v4`; senão manter `@v4` e registrar `Ruling: actions por tag maior (sem rede para o GitHub) — custo se errado: tag movida por terceiro`. Não escrever SHA de memória.
- `docs/operacao/ci.md`: o que cada job prova; como rodar o mesmo localmente (comandos); o segredo opcional `VARREDURA_NOMES` (o usuário cria em *Settings → Secrets*; nunca no repositório); a regra de proteção da `main` recomendada (checks `backend`, `frontend`, `guarda` e `varredura` obrigatórios, PR obrigatório, sem push direto) e o comando `gh api` que a aplica, **marcado "só o usuário executa ou autoriza"** (ponto de parada 2 da spec).

- [ ] **Passo 1: Testes que falham** (`CiWorkflowTest`, lê o YAML com `Yaml.default.parseToYamlNode`, sem rede):
  - `quatroJobsObrigatorios`: `backend`, `frontend`, `guarda`, `varredura`.
  - `flagDoJiraFixadaEmFalseNoWorkflow`: `env.JIRA_LIVE_ENABLED == "false"`; nenhum outro `JIRA_` no arquivo inteiro (texto).
  - `permissaoSoLeitura`: `permissions.contents == "read"` e nenhuma outra chave em `permissions`.
  - `semPullRequestTarget` e `soSegredoDaVarredura` (regex `secrets\.([A-Z_]+)` → só `VARREDURA_NOMES`).
  - `versoesFixas`: `java-version` 21, `node-version` 22, `pnpm@9.12.3`, `--frozen-lockfile`.
  - `backendRodaATestSuiteInteira`: um passo contém `./gradlew` e `test` e nenhum `-x test` nem `--tests`.
  - `frontendRodaLintTestesEBuild`.
  - `timeoutsDefinidos`: todo job tem `timeout-minutes`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*CiWorkflowTest*'` → FALHA.
- [ ] **Passo 3: Escrever** o workflow e o `docs/operacao/ci.md`.
- [ ] **Passo 4: Rodar** de novo → PASSA. Se `actionlint` estiver instalado na máquina, rodar `actionlint .github/workflows/ci.yml` (opcional; não instalar).
- [ ] **Passo 5: Commit** `ci: workflow com backend, frontend, guarda do hook e varredura`. A prova real do workflow é o primeiro PR deste plano ficar verde no GitHub (Tarefa 11).

---

### Tarefa 3: Cabeçalhos de segurança e CSP

**Arquivos:** Criar `http/CabecalhosDeSeguranca.kt`, `app/frontend/scripts/verificar-csp.mjs`; Modificar `Application.kt` (instala o plugin antes do roteamento), `.github/workflows/ci.yml` (passo `node scripts/verificar-csp.mjs` no job `frontend`); Teste `http/CabecalhosDeSegurancaTest.kt`, `ci/CiWorkflowTest.kt` (caso novo).

**Interfaces:**
- Produz `fun cabecalhosDeSeguranca(devMode: Boolean): ApplicationPlugin<Unit>` (plugin `createApplicationPlugin("CabecalhosDeSeguranca")` que acrescenta em **toda** resposta, inclusive 401, 403, 404 e estáticos):

  | Cabeçalho | Valor |
  |---|---|
  | `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
  | `X-Frame-Options` | `DENY` |
  | `X-Content-Type-Options` | `nosniff` |
  | `Referrer-Policy` | `no-referrer` |
  | `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |
  | `Cross-Origin-Opener-Policy` | `same-origin` |
  | `Cache-Control` | `no-store` **só** em respostas de `/api/*` e `/auth/*` |
  | `Strict-Transport-Security` | `max-age=31536000` **só** com `devMode == false` |

  Sem `'unsafe-inline'` nem `'unsafe-eval'` (valor padrão 25/10, reversível). O React aplica `style={}` pelo CSSOM, que a CSP não bloqueia. Se o servidor de desenvolvimento do Vite precisar de outra política, ela fica no `vite.config.ts`, nunca no backend.
- Produz `app/frontend/scripts/verificar-csp.mjs`: lê `dist/index.html` e todo `.html` de `dist/`; falha (saída 1) se houver `<script>` sem `src`, atributo `on*=` (manipulador inline), `style="` em HTML, `javascript:` ou `<link>`/`<script>` com URL absoluta fora da origem. Imprime arquivo e regra, nunca o conteúdo.

- [ ] **Passo 1: Testes que falham:**
  - `todaRespostaTemOsCabecalhos`: `/api/health`, `/api/meta`, `/` (SPA), um arquivo de `assets/`, uma rota `/api/inexistente` (404) e uma rota protegida sem principal (401) → todos os cabeçalhos da tabela (menos HSTS em dev).
  - `cspSemUnsafeInline` (o valor não contém `unsafe-`); `frameAncestorsNone`.
  - `hstsSoForaDoDevMode` (dev → ausente; `devMode=false` com autenticador de teste → presente).
  - `noStoreSoNaApi` (`/api/meta` → `no-store`; `/assets/x.js` → sem `no-store`).
  - `CiWorkflowTest.frontendVerificaCsp` (o job `frontend` roda `verificar-csp.mjs` depois do `build`).
  - `verificar-csp.mjs`: rodar contra um `dist` de teste em diretório temporário criado pelo próprio teste Vitest `scripts/verificar-csp.test.ts` (casos: `index.html` do Vite → passa; `<script>alert(1)</script>` → falha; `onclick=` → falha).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*CabecalhosDeSegurancaTest*' --tests '*CiWorkflowTest*'` e `cd app/frontend && pnpm vitest run scripts` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; `pnpm build && node scripts/verificar-csp.mjs` → saída 0 (prova `cspCompativelComOBuild`). Subir o painel em modo dev e abrir uma tela no navegador uma vez, conferindo o console sem violação de CSP (registrar no progresso).
- [ ] **Passo 5: Commit** `feat(seguranca): CSP e cabeçalhos de segurança em toda resposta`.

---

### Tarefa 4: Proteção CSRF em todo método mutante e `ErroDto`

**Arquivos:** Criar `http/ErroDto.kt`, `http/ProtecaoCsrf.kt`, `app/backend/src/test/kotlin/br/sidia/painel/TestHttp.kt`; Modificar `Application.kt` (instala depois do controle de acesso, com o mesmo callback de registro), `app/frontend/src/api/client.ts`; Teste `http/ProtecaoCsrfTest.kt`, `app/frontend/src/api/client.test.ts`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class ErroDto(val codigo: String, val params: Map<String, String> = emptyMap())
  const val CABECALHO_CSRF = "X-Painel-CSRF"            // valor exigido: "1"
  data class ConfigCsrf(val origemPublica: String?)      // DASHBOARD_ORIGEM_PUBLICA (ex.: https://painel.empresa.invalid); vazio = compara com o Host
  fun protecaoCsrf(cfg: ConfigCsrf, aoNegar: suspend (ApplicationCall, codigo: String) -> Unit): ApplicationPlugin<Unit>
  fun metodoMutante(m: HttpMethod): Boolean              // tudo menos GET, HEAD e OPTIONS
  ```
  ```kotlin
  // test: TestHttp.kt (helper compartilhado pelos planos seguintes)
  fun HttpRequestBuilder.comCsrf(origem: String = "http://localhost")   // cabeçalho X-Painel-CSRF: 1 e Origin
  ```
- Regra (valor padrão 25/10, reversível; ver spec D3): para todo método mutante, em qualquer caminho (`/api/**` e `/auth/**`):
  1. sem `X-Painel-CSRF: 1` → 403 `ErroDto("CSRF_AUSENTE")`;
  2. `Sec-Fetch-Site` presente e diferente de `same-origin` e `none` → 403 `ErroDto("ORIGEM_INVALIDA")`;
  3. `Origin` presente e diferente da origem esperada (`DASHBOARD_ORIGEM_PUBLICA`, ou `<esquema>://<Host>` da requisição) → 403 `ErroDto("ORIGEM_INVALIDA")`;
  4. senão, segue para o handler.

  O handler **nunca** roda quando negado. A negação chama `aoNegar`, que grava `access_log` (rota sem query string, filtro `motivo=<codigo>`) quando há banco; sem principal resolvido, o `user_id` é `anonymous`. Sem CORS: o painel não envia `Access-Control-Allow-*`, então nenhum site externo consegue mandar o cabeçalho próprio.
- `Settings`: `DASHBOARD_ORIGEM_PUBLICA` (opcional; precisa começar com `https://` fora do dev mode; inválida → `IllegalArgumentException` citando só o nome).
- Frontend: `export async function enviarJson<T>(metodo: 'POST'|'PUT'|'PATCH'|'DELETE', url: string, corpo?: unknown): Promise<T>` em `client.ts`, com `X-Painel-CSRF: 1`, `Content-Type: application/json` e `credentials: 'same-origin'`; erro com `ErroDto` vira `class ErroDaApi extends Error { status: number; codigo: string | null; params: Record<string,string> }`.

- [ ] **Passo 1: Testes que falham** (`ProtecaoCsrfTest` instala no módulo de teste uma rota `POST /api/teste-csrf` que incrementa um contador; nunca em `src/main`):
  - `postSemCabecalhoEh403SemExecutar` (corpo `{"codigo":"CSRF_AUSENTE"}`, contador 0).
  - `postComCabecalhoPassa` (`comCsrf()` → 200, contador 1).
  - `origemDeOutroSiteEh403` (`Origin: https://evil.example` com o cabeçalho → `ORIGEM_INVALIDA`); `secFetchSiteCrossSiteEh403`; `semOriginESemSecFetchPassa` (clientes não navegadores com o cabeçalho).
  - `origemPublicaConfiguradaVale` (`DASHBOARD_ORIGEM_PUBLICA=https://painel.exemplo.invalid`; `Origin` igual → 200; `Host` diferente não importa).
  - `getNaoExigeCabecalho`.
  - `negacaoGravaAccessLog` (contexto com banco: 1 linha com `filters` contendo `motivo` e sem o corpo).
  - `todoMetodoMutanteExigeCsrf`: percorre a árvore de rotas do módulo real (`application.plugin(Routing)`, recursivo) e, para cada rota com método mutante, faz a chamada sem o cabeçalho e espera 403 `CSRF_AUSENTE`. O teste também afirma que o conjunto de rotas mutantes encontrado é **igual** a uma lista esperada versionada no próprio teste (`val MUTANTES_ESPERADAS = emptySet<Pair<HttpMethod, String>>()` hoje); o Plano 7 acrescenta `POST /api/distribuicao/decisao` e o Plano 8 `POST /auth/logout`, para que uma rota mutante nova obrigue a atualizar a lista e prove a proteção. Para não ser um teste vazio enquanto a lista real é vazia, o mesmo teste monta o módulo com a rota de teste `POST /api/teste-csrf` e confere que o percurso da árvore a encontra e a nega.
  - `SettingsTest.origemPublicaInvalidaFalha` (`http://` com `DASHBOARD_DEV_MODE=false`).
  - Frontend: `enviarJsonMandaCabecalhoCsrf`; `erroDaApiTrazCodigo` (403 com `{"codigo":"ORIGEM_INVALIDA"}` → `ErroDaApi.codigo == "ORIGEM_INVALIDA"`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ProtecaoCsrfTest*' --tests '*SettingsTest*'` e `cd app/frontend && pnpm vitest run src/api` → FALHA.
- [ ] **Passo 3: Implementar.** Documentar `DASHBOARD_ORIGEM_PUBLICA` em `app/.env.example` (sem valor, comentado em pt-BR).
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa das duas camadas.
- [ ] **Passo 5: Commit** `feat(seguranca): proteção CSRF em todo método mutante, com código de erro`.

---

### Tarefa 5: Sessão expirada na tela

**Arquivos:** Criar `app/frontend/src/shell/SessaoExpirada.tsx`, `app/frontend/src/shell/SessaoExpirada.test.tsx`; Modificar `app/frontend/src/api/client.ts` (evento global), `app/frontend/src/main.tsx` (`QueryClient` com `queryCache`/`mutationCache` `onError`), `app/frontend/src/shell/Shell.tsx`, `app/frontend/src/i18n/{pt,en}.json`.

**Interfaces:**
- Produz em `client.ts`:
  ```ts
  export const EVENTO_SESSAO_EXPIRADA = 'painel:sessao-expirada'
  export function avisarSeSessaoExpirou(status: number): void      // 401 → window.dispatchEvent(new Event(EVENTO_SESSAO_EXPIRADA))
  export function caminhoDeVolta(atual: string): string | null      // só caminho interno: começa com "/", não com "//" nem "/\\", sem esquema; senão null
  export function urlDeLogin(volta: string | null): string          // "/auth/login" ou "/auth/login?volta=<encodeURIComponent>"
  ```
  Toda função de `client.ts` que faz `fetch` chama `avisarSeSessaoExpirou(resposta.status)` antes de lançar.
- `SessaoExpirada`: diálogo `role="alertdialog"` com foco no botão "Entrar de novo" (link para `urlDeLogin(caminhoDeVolta(location.pathname + location.search))`), texto "Sua sessão expirou. Entre de novo para continuar; os filtros da tela ficam na URL." (en: "Your session has expired. Sign in again to continue; the screen filters stay in the URL."). Não aparece em `devMode` (lido de `/api/meta`). 403 **não** dispara o diálogo (é falta de permissão, tratada pela tela).
- O backend de `/auth/login?volta=` é do Plano 8 (T2): ele também precisa aceitar só caminho interno (registrado nos riscos da spec 9 como impacto no Plano 8).

- [ ] **Passo 1: Testes que falham:**
  - `respostas401DisparamSessaoExpirada` (mock de `fetch` com 401 em `fetchMe` → evento disparado uma vez).
  - `respostas403NaoDisparam`.
  - `telaMostraAvisoComLinkDeLogin` (pt e en; o link é `/auth/login?volta=%2Fauditoria%3Fde%3D2026-10-01`).
  - `voltaSoAceitaCaminhoInterno`: `//evil.example`, `https://evil.example`, `/\\evil.example`, `javascript:alert(1)` → `null`; `/auditoria?de=2026-10-01` → igual.
  - `focoNoBotaoEEscNaoFecha` (o diálogo não some sem ação: a sessão realmente acabou).
  - `devModeNaoMostra`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shell src/api` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; `pnpm build`.
- [ ] **Passo 5: Commit** `feat(ui): aviso de sessão expirada com volta segura ao login`.

---

### Tarefa 6: Imagem sem modo dev por padrão, digests e `.dockerignore` por lista de permissão

**Arquivos:** Modificar `app/Dockerfile`, `app/.dockerignore`, `app/docker-compose.yml`, `app/README.md`; Criar `ci/ImagemDockerTest.kt`.

**Interfaces:**
- `Dockerfile`: `ENV ... DASHBOARD_DEV_MODE=false` (e `JIRA_LIVE_ENABLED=false`); cada `FROM` com digest `imagem:tag@sha256:<digest>`, mantendo a tag para leitura. Digests **resolvidos na execução** com `docker buildx imagetools inspect <imagem:tag>` (ou `docker pull` + `docker inspect --format '{{index .RepoDigests 0}}'`); nunca escritos de memória. O mesmo para `postgres:16` no compose.
- `docker-compose.yml`: o serviço local continua com `DASHBOARD_DEV_MODE: ${DASHBOARD_DEV_MODE:-true}` (desenvolvimento explícito no compose, nunca na imagem); comentário em pt-BR explicando a diferença.
- `.dockerignore` por lista de permissão (valor padrão 25/10, reversível):
  ```
  *
  !Dockerfile
  !frontend/
  !backend/
  !config/*.exemplo.yaml
  !config/*.demo.yaml
  !config/calendario.yaml
  !config/cards/
  ```
  seguido das exclusões de artefatos dentro das pastas liberadas (`**/node_modules`, `**/dist`, `**/build`, `**/.gradle`, `**/.kotlin`, `**/.env*`) e, por defesa, de novo `config/papeis.yaml`, `config/equipes.yaml`, `config/ausencias.yaml`, `config/squads.yaml`, `config/status_map.yaml`, `config/jira_campos.yaml`.
- `README.md` (app): a imagem sobe em modo produção por padrão; o compose local liga o modo dev; `docker run` sem variáveis falha fechado sem OIDC (depois do Plano 8) ou com `OidcAuthenticator` que nega (antes dele).

- [ ] **Passo 1: Testes que falham** (`ImagemDockerTest`, estático, lê os arquivos por `painel.repoDir`):
  - `imagemPadraoSemDevMode` (o `ENV` final tem `DASHBOARD_DEV_MODE=false` e `JIRA_LIVE_ENABLED=false`).
  - `todoFromComDigest` (regex `^FROM \S+@sha256:[0-9a-f]{64}`) e `postgresDoComposeComDigest`.
  - `dockerignoreComecaNegandoTudo` (primeira linha útil `*`).
  - `configRealNuncaReincluida` (nenhuma linha `!` casa `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `status_map.yaml` nem `jira_campos.yaml`).
  - `composeLocalLigaDevModeExplicitamente`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ImagemDockerTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (resolver os digests; pull de imagens permitido).
- [ ] **Passo 4: Rodar** de novo → PASSA. `cd app && docker compose build` → sucesso; `docker run --rm --entrypoint ls <imagem> /app/config` não lista nenhum YAML real. Rodar `POSTGRES_PASSWORD=<descartável> docker compose up -d`, abrir `/api/meta` (`devMode: true` pelo compose) e `docker compose down -v`.
- [ ] **Passo 5: Commit** `build(docker): imagem sem modo dev por padrão, imagens por digest e dockerignore por permissão`.

---

### Tarefa 7: Controle de acesso por lista de permissão, `module` compatível e servidor estático

**Arquivos:** Modificar `security/AccessControl.kt`, `Application.kt`, `StaticSpa.kt`; Criar `StaticSpaResolverTest.kt`; Teste `security/AccessControlTest.kt`, `SpaTest.kt`.

**Interfaces:**
- `exigeAutenticacao(uri)` passa a ser `!ehPublica(uri)`, com `internal fun ehPublica(uri: String): Boolean` verdadeiro **só** para:
  1. `segmentos in rotasPublicas` (hoje `/api/health` e `/api/meta`; os Planos 8 e 12 acrescentam itens nessa mesma lista);
  2. casca estática: primeiro segmento diferente de `api` e de `auth` (sem diferenciar maiúsculas), **todo** segmento casa `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` (logo, nada começa com `.`, nada vazio, nada com `%`, `\`, NUL ou `..`), no máximo 8 segmentos, e a URI começa com `/` e não com `//`.

  Mesmo comportamento observável de hoje para as rotas atuais; a diferença é que qualquer forma não prevista passa a exigir login (negar por padrão em todo o servidor).
- `module`: o primeiro parâmetro vira `settingsIgnorado: Settings? = null` (posicional continua compilando; chamadas novas podem usar `module(context = ctx, ...)`). KDoc: "ignorado; a configuração efetiva é `context.settings`". Sem mudar a ordem nem os demais parâmetros.
- `StaticSpa.kt`: `resolverDentro` vira `internal` para teste; arquivo cujo nome começa com `.` em qualquer segmento → 404; `HEAD` em arquivo estático responde como `GET` sem corpo.

- [ ] **Passo 1: Testes que falham:**
  - `AccessControlTest`: `formasNaoPrevistasExigemLogin` (`/.env`, `/assets/.oculto`, `/a/b/c/d/e/f/g/h/i`, `/x%2Fy`, `/auth/qualquer` → exige); `casaDaSpaContinuaPublica` (`/`, `/auditoria`, `/assets/index-abc123.js`, `/favicon.svg` → pública); os casos atuais (`/%61pi/x`, `//api/x`, `/API/x`, `/api/x/../health`) continuam exigindo.
  - `StaticSpaResolverTest`: `symlinkParaForaDaRaizEhNulo` (cria raiz temporária com link simbólico para um arquivo fora → `null`); `symlinkDentroDaRaizResolve`; `arquivoOcultoEhNulo`; `caminhoInvalidoEhNulo`.
  - `SpaTest`: `arquivoOcultoDaSpa404` (`.oculto` dentro de `dist`), `headEmAssetResponde200SemCorpo`.
  - `moduleSemSettingsCompila` (`testApplication { application { module(context = ctx) } }` → `/api/health` 200) e `moduleComSettingsPosicionalCompila` (forma antiga).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AccessControlTest*' --tests '*StaticSpaResolverTest*' --tests '*SpaTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa.
- [ ] **Passo 5: Commit** `fix(seguranca): acesso público por lista de permissão e endurecimento do servidor estático`.

---

### Tarefa 8: Cliente Jira — busca tipada, espera cancelável e máscara de token escapado

**Arquivos:** Modificar `jira/JiraModels.kt`, `jira/JiraClient.kt`, `jql/CardQueryFactory.kt` e todo chamador de `SearchRequest(` em `src/main` (por exemplo `sync/SyncService.kt` e `sync/Syncer.kt`); Teste `jira/JiraClientRobustezTest.kt`, `jira/JiraClientTest.kt`, `jira/JiraClientPagingTest.kt`.

**Interfaces:**
- `data class SearchRequest(val query: JqlQuery, val startAt: Int = 0, val maxResults: Int = 100)`, com `val jql get() = query.jql`, `val fields get() = query.fields`, `val expandChangelog get() = query.expandChangelog`. Nenhum `SearchRequest` aceita `String` de JQL: só `JqlQuery`, que só o `CardQueryFactory`/`JqlBuilder` produzem (o construtor de `JqlQuery` fica `internal` ao módulo, se ainda não for). Nos testes, `JqlQuery` sai de `CardQueryFactory` com fichas de teste.
- `mascarar(texto)` passa a mascarar também o token **escapado**: forma URL (`URLEncoder.encode(token, UTF_8)` e `%`-maiúsculo/minúsculo), forma JSON (`/` como `\/`, aspas escapadas) e base64 de `usuario:token` (Basic). Continua sem mascarar tokens com menos de 8 caracteres (regra atual).
- Mensagens de exceção do cliente e do `JqlBuilder` que incluem valor recebido passam por `sanearParaLog(v: String): String` (controle, CR e LF viram `?`; até 80 caracteres) — Minor do Plano 2 (injeção em log).
- A espera do retry continua injetável (`delay`); cancelamento durante a espera propaga `CancellationException` e não faz nova tentativa.

- [ ] **Passo 1: Testes que falham:**
  - `buscaSoAceitaJqlQuery` (teste de compilação por reflexão: o construtor público de `SearchRequest` não tem parâmetro `String` chamado `jql`).
  - `retryCanceladoDuranteEsperaNaoTentaDeNovo`: `MockEngine` responde 503; `delay` falso suspende num `CompletableDeferred`; cancelar o `Job` → `CancellationException`, contador de requisições = 1.
  - `tokenEscapadoEmUrlEhMascarado` (token `abc/def+ghi=123456` em `abc%2Fdef%2Bghi%3D123456` e `abc%2fdef%2bghi%3d123456` → `***`); `tokenEscapadoEmJsonEhMascarado` (`abc\/def+ghi=123456`); `basicEmBase64EhMascarado`.
  - `mensagemComQuebraDeLinhaNaoInjetaLog` (valor `"a\nINFO falso"` → mensagem sem `\n`).
  - Os testes existentes de paginação e robustez continuam verdes com o tipo novo.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JiraClient*' --tests '*JqlBuilderTest*'` → FALHA (compilação ou asserção).
- [ ] **Passo 3: Implementar** e ajustar os chamadores.
- [ ] **Passo 4: Rodar** de novo → PASSA; suíte completa; `FlagDesligadaZeroRequisicoesTest` e `SomenteAFabricaCriaClientesJira` verdes.
- [ ] **Passo 5: Commit** `fix(jira): busca só com JQL tipado, espera cancelável e máscara de token escapado`.

---

### Tarefa 9: Varredura da fábrica sem brechas (alias, `typealias`, referência e `copy` aninhado)

**Arquivos:** Criar `jira/VarreduraDeFontes.kt` **em `src/test`** (função pura), `jira/VarreduraDeFontesTest.kt`; Modificar `jira/SomenteAFabricaCriaClientesJira.kt` (passa a usar a função).

**Interfaces:**
- Produz (só em teste):
  ```kotlin
  data class Violacao(val arquivo: String, val linha: Int, val regra: String)
  fun varrerFontes(fontes: Map<String, String>, permitidos: Set<String>): List<Violacao>
  ```
  Regras: `JIRA_CLIENT_CONSTRUIDO` (`JiraClient(` fora dos permitidos, inclusive com espaço antes do parêntese e com nome qualificado `br.sidia.painel.jira.JiraClient(`); `JIRA_CLIENT_REFERENCIA` (`::JiraClient`); `JIRA_CLIENT_ALIAS` (`import ...JiraClient as X` e `typealias X = JiraClient`, e depois `X(` em qualquer arquivo); `HTTP_CLIENT_CONSTRUIDO` (`HttpClient(` fora dos permitidos); `SETTINGS_COPY` (`.copy(` aplicado a `Settings` ou a `settings`, inclusive aninhado como `ctx.settings.copy(` e `x.copy(settings = s.copy(`). Comentários de linha e de bloco são ignorados.
- Permitidos continuam os mesmos de hoje (`JiraClientFactory.kt` e `createJiraHttpClient`).

- [ ] **Passo 1: Testes que falham** (`VarreduraDeFontesTest`, fontes sintéticas em memória): `construcaoDireta`, `construcaoQualificada`, `referenciaDeConstrutor`, `importComAlias`, `typealiasDepoisUsado`, `httpClientForaDaFabrica`, `copyDeSettingsAninhado`, `comentarioNaoConta`, `fabricaPermitida` (sem violação).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*VarreduraDeFontesTest*'` → FALHA.
- [ ] **Passo 3: Implementar** e trocar o corpo de `SomenteAFabricaCriaClientesJira` para chamar `varrerFontes` sobre `painel.mainSrcDir`.
- [ ] **Passo 4: Rodar** `./gradlew test --tests '*VarreduraDeFontesTest*' --tests '*SomenteAFabricaCriaClientesJira*'` → PASSA (o código real continua sem violação).
- [ ] **Passo 5: Commit** `test(jira): varredura da fábrica cobre alias, typealias, referência e copy aninhado`.

---

### Tarefa 10: `RetentionTest` estável (Testcontainers e Ryuk)

**Arquivos:** Criar `PostgresDeTeste.kt` (test); Modificar `db/RetentionTest.kt`, `TestContextComBanco.kt` e os testes que sobem contêiner próprio, se a causa estiver neles.

**Interfaces:**
- Produz `object PostgresDeTeste { val container: PostgreSQLContainer; fun dataSourceNovo(): DataSource; fun limpar(ds: DataSource) }`: **um** contêiner `postgres:16` (mesmo digest do compose) por JVM de teste, iniciado sob demanda, Flyway aplicado uma vez por banco; cada classe de teste cria um banco novo (`CREATE DATABASE t_<uuid>`) ou usa `TRUNCATE ... RESTART IDENTITY CASCADE` entre testes. O Ryuk continua **ligado** (é ele que limpa contêineres órfãos).
- Relógio de todos os testes de retenção injetado (nada de `Instant.now()` no teste nem na regra).

- [ ] **Passo 1: Reproduzir** com `superpowers:systematic-debugging`: rodar `cd app/backend && for i in 1 2 3 4 5 6 7 8 9 10; do ./gradlew test --tests '*RetentionTest*' --rerun-tasks -q || echo FALHOU $i; done` e guardar no progresso quantas falhas e a mensagem (só a classe da exceção e a primeira linha, sem dados). Hipóteses a conferir, nesta ordem: dependência de relógio real; banco compartilhado com outra classe em paralelo; contêiner parado pelo Ryuk entre classes por `@Container` estático em classes diferentes.
- [ ] **Passo 2: Teste que falha:** `retencaoIndependeDoRelogioReal` (relógio fixo em `2026-10-30T12:00Z` e outro em `2027-03-01`; mesmos dados relativos → mesmas contagens) e o caso de concorrência que a reprodução apontar.
- [ ] **Passo 3: Corrigir** a causa achada; migrar `RetentionTest` e `TestContextComBanco` para `PostgresDeTeste`.
- [ ] **Passo 4: Rodar** o laço de 10 execuções → 0 falhas; suíte completa (`./gradlew cleanTest test`, conferir os XML: 0 falhas, 0 erros).
- [ ] **Passo 5: Commit** `test: banco de teste compartilhado e RetentionTest estável`.

---

### Tarefa 11: Fechamento, Minor restantes e primeiro CI verde

**Arquivos:** Modificar `HANDOFF.md` (seção "Minor adiados": cada item com "resolvido no Plano 9, Tarefa N" ou "fica para o Plano X"), `app/README.md` (seção "CI"), `docs/operacao/ci.md`.

**Interfaces:** nenhuma de código. Tabela dos Minor (a lista de referência está na spec, seção 4, D8) com estado final.

- [ ] **Passo 1: Suítes completas:** `cd app/backend && ./gradlew test`; `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build && node scripts/verificar-csp.mjs`; `sh .claude/hooks/test-guard.sh`; `sh scripts/ci/test-varredura.sh`; `sh scripts/ci/varrer-dados-pessoais.sh --base origin/main` → todos com saída 0.
- [ ] **Passo 2: Atualizar** o `HANDOFF.md` e o README.
- [ ] **Passo 3: Conferir a visibilidade:** `gh repo view tlmacedo/dashboardJira --json visibility` → `PRIVATE`. Se vier `PUBLIC`, **PARAR** e perguntar ao usuário (guia, seção 6) antes de qualquer push.
- [ ] **Passo 4: Push e PR** (fluxo do guia, seção 4). Conferir no PR que os quatro jobs rodaram e ficaram verdes. Se um job falhar só no GitHub (ex.: Docker do runner), corrigir na branch; nunca desligar o job nem marcar `continue-on-error`.
- [ ] **Passo 5: Commit** `docs: fechamento do Plano 9 e estado dos Minor adiados`.

---

## Autoavaliação

**Cobertura (auditoria e pedido):**

| Item | Tarefa |
|---|---|
| C1: CI com backend (Testcontainers), frontend, `test-guard.sh` e varredura que falha o build | T1, T2, T11 |
| A3: CSP, X-Frame-Options, Referrer-Policy e demais cabeçalhos | T3 |
| A3: CSRF no único POST (e em qualquer outro que vier) | T4 |
| A3: sessão expirada na SPA | T5 |
| A3: imagem com `DASHBOARD_DEV_MODE=false`, digest, `.dockerignore` por permissão | T6 |
| M8: `settingsIgnorado`, inverter `exigeAutenticacao`, `resolverDentro` com symlink, arquivos ocultos | T7 |
| M8: `SearchRequest` tipado, `delay` do retry com cancelamento, máscara do token escapado, log injection | T8 |
| M8: varredura da fábrica (alias, `typealias`, `::`, `copy` aninhado) | T9 |
| M8: `RetentionTest` intermitente | T10 |
| Proteção da `main` documentada | T2 (`docs/operacao/ci.md`), aplicação só pelo usuário |

**Consistência de tipos:** `ErroDto` (T4) é o mesmo que o Plano 10 usa para mensagens por código; `CABECALHO_CSRF` e `comCsrf()` (T4) são os nomes que os testes de `POST` dos Planos 7 e 8 devem usar; `ehPublica`/`rotasPublicas` (T7) é a lista que os Planos 8 (rotas `/auth/*`) e 12 (readiness) estendem; `SearchRequest(query: JqlQuery, ...)` (T8) é o que o 4A e o 6 (worklog) passam a chamar.

**Fora deste plano:** piscada do tema no primeiro render e decisão `#1329A0` × `#0023A4` (identidade final, Plano 15); contraste de `--line` (Plano 8, T4); implantação real e provedor OIDC (Plano 13); `jira_sidia` (futuro).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **CI no GitHub Actions com runners hospedados pelo GitHub**, até o usuário dizer o contrário (ponto de parada 1). Self-hosted só se a empresa exigir.
2. **Varredura sobre o diff do PR e as mensagens de commit**, não sobre a árvore inteira nem sobre o e-mail de autor; nomes proibidos só em arquivo local git-ignorado ou em segredo opcional do repositório; a saída nunca mostra o termo.
3. **CSRF por cabeçalho próprio + conferência de `Origin`/`Sec-Fetch-Site`**, sem cookie de token: não depende da sessão do Plano 8 e funciona igual em dev e produção.
4. **CSP estrita sem `unsafe-inline`**; HSTS só fora do modo dev.
5. **Imagem em modo produção por padrão**; o modo dev fica explícito no compose local.
6. **`module` compatível** (primeiro parâmetro opcional) em vez de remover o parâmetro: não quebra os textos dos Planos 4 a 8.
7. **Actions por tag maior quando o SHA não puder ser resolvido**, registrado como `Ruling`.
