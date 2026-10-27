# Fase 8: Preparação final, acessibilidade, pacote de produção e remoção do `legacy/` — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

> **Plataforma (adendo de 27/10):** SQLite embutido, **sem Docker, sem PostgreSQL, sem Testcontainers**. Este plano foi ajustado ao `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`, que vence qualquer trecho antigo. Se algum passo pedir contêiner, imagem, compose ou servidor de banco, não o execute: aplique a seção 5 do adendo e registre `Ruling:` no arquivo de progresso.

**Objetivo (Goal):** Fechar a etapa offline. O painel fica pronto para implantar: todas as rotas cobertas por papel e `access_log`, SSO/OIDC atrás de uma interface testada com um emissor falso, WCAG AA verificado nos três temas, desempenho medido na escala da spec **sobre o SQLite em arquivo** (leitura concorrente e escrita durante leitura incluídas), **pacote de produção** (`installDist` + build do frontend, sem contêiner) com guia de implantação, e documentos da 1ª conexão atualizados. Depois da conferência de equivalência card a card, aprovada pelo usuário, o app Python em `legacy/` é removido.

**Arquitetura (Architecture):** Nenhum card novo. A fase adiciona:
- testes transversais: inventário de rotas, contraste dos tokens, teclado, carga sintética sobre SQLite e varredura de referências;
- um `OidcAuthenticator` real que implementa a interface `Authenticator`, validado contra um emissor OIDC falso no próprio teste (descoberta, JWKS e token por `MockEngine`, chaves RSA geradas em memória);
- scripts que montam e verificam o pacote de produção (`montar-pacote` e `verificar-pacote`, em PowerShell e `sh`);
- o documento de equivalência por ID do relatório.

O **serviço do sistema operacional**, o backup e a restauração do SQLite ficam no Plano 12; a implantação real (proxy, TLS, cofre, atualização e reversão ensaiadas) fica no Plano 13. Este plano só entrega o pacote, a verificação dele e o guia de implantação que os dois planos estendem.

A remoção do `legacy/` é a última tarefa de código e tem uma porta de aprovação.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1 (`ktor-server-auth`, `ktor-server-auth-jwt`, `ktor-server-sessions`, acrescentados em `app/backend/gradle/libs.versions.toml` e `app/backend/build.gradle.kts`), SQLite embutido (`sqlite-jdbc`, Hikari, Flyway), Ktor `MockEngine`; React + TypeScript (Vite, Vitest, Testing Library). Requisitos: JDK 21 e Node 22 com pnpm. Nada de Docker, PostgreSQL, Testcontainers, H2 ou mock de banco.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.3, 4.5, 4.6 "Produção × desenvolvimento", "Volume e atualidade", "Ambiente", "Oráculo de números", 5.5, 8 item 8, 9, 10, 11), lida com o adendo de 27/10 (no que fala de PostgreSQL, contêiner e Testcontainers, vale o adendo). Spec da fase: `docs/superpowers/specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md`. Inventário: `docs/analise/relatorio-cards-app-atual.md` (todas as IDs; seção 8). Visual: `docs/design/plano-visual-frontend.md` (seções 2, 3, 7 e 8).

## Comandos de referência (Windows 11 PowerShell é o principal)

O executor é o ClineSR no Windows 11. Cada comando abaixo parte da **raiz do repositório**. No PowerShell 5.1 não existe `&&`: use `;` e confira `$?` (ou `$LASTEXITCODE` para programas). Para voltar à raiz: `Set-Location (git rev-parse --show-toplevel)`.

| Ação | Windows 11 (PowerShell) | `sh` (Linux/macOS/Git Bash) |
|---|---|---|
| Backend, uma classe | `cd app\backend; .\gradlew.bat test --tests '*Classe*'` | `cd app/backend && ./gradlew test --tests '*Classe*'` |
| Backend, suíte completa | `cd app\backend; .\gradlew.bat test` | `cd app/backend && ./gradlew test` |
| Backend, carga (T6) | `cd app\backend; .\gradlew.bat testCarga` | `cd app/backend && ./gradlew testCarga` |
| Frontend, uma pasta | `cd app\frontend; pnpm vitest run src/a11y` | `cd app/frontend && pnpm vitest run src/a11y` |
| Frontend, suíte e build | `cd app\frontend; pnpm vitest run; if ($?) { pnpm build }` | `cd app/frontend && pnpm vitest run && pnpm build` |
| Orçamento do bundle (T6) | `cd app\frontend; pnpm build; if ($?) { node scripts\orcamento-bundle.mjs }` | `cd app/frontend && pnpm build && node scripts/orcamento-bundle.mjs` |
| Trava do hook | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1` e `sh .claude/hooks/test-guard.sh` (Git Bash; exige `jq`: se faltar, registrar e perguntar ao usuário) | `sh .claude/hooks/test-guard.sh` |
| Montar o pacote (T7) | `cd app; powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1` | `cd app && sh scripts/montar-pacote.sh` |
| Verificar o pacote (T7) | `cd app; powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1` | `cd app && sh scripts/verificar-pacote.sh` |

Se a suíte do backend exigir qualquer coisa além do JDK 21, é defeito do plano: pare e pergunte (adendo, seção 3).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere na `main` que as Fases 4A, 9 (CI sem Docker), 4, 5, 6, 7, 10 e 11 foram mescladas (ordem do índice dos planos) e que estes nomes existem. Se algum nome mudou, atualiza só os nomes deste plano e registra no `progress.md`. Confira também `ls app/backend/src/main/resources/db/migration` (hoje só existe `V1__base.sql`; a próxima migração livre é a `V2`, a menos que outro PR já tenha chegado).

| Nome | Origem |
|---|---|
| `Authenticator`, `DevAuthenticator`, `OidcAuthenticator` (stub sem parâmetros, em `Authenticator.kt`), `principalFor`, `installAccessControl`, `requireRole`, `Principal`, `Role` | `security/` (Planos 1 e 3A) |
| `rotasPublicas` (privado em `security/AccessControl.kt`, lista de segmentos de `/api/health` e `/api/meta`) e, se o Plano 9 já o expôs, `ROTAS_PUBLICAS` | Planos 1 e 9 |
| `Application.module(settings, context, authenticator, agendador)` e `autenticadorPadrao(settings)` em `Application.kt`; `GET /api/health` e `GET /api/meta` | Plano 1 |
| `Settings.fromEnv` (com `toString` sem segredos), `ArquivoEnv` (lê o arquivo de variáveis da subida), `AppContext.load` | `config/`, `AppContext.kt` |
| `Database` (`urlDeArquivo`, `connect`, `migrate`), `TravaDeProcesso`, `AccessLog`, `SqliteIssueStore` (`upsert`), `SqliteSyncStateStore` | `db/`, `sync/` |
| `BancoEmArquivo`, `testContextComBanco(settings, banco, ...)`, `AppContext.contarAccessLog()`, `AppContext.limparAccessLog()` | `src/test/kotlin/br/sidia/painel/TestContextComBanco.kt` |
| `executarAuditado`, `rotaDeCard`, `CardDeLeitura` | Planos 4 e 5 |
| `CompatLegado*Test` (Tempo em status em `domain/CompatLegadoTest.kt`; Trabalho, Capacidade, Checklist e Distribuição nos Planos 4 a 7) e os documentos `docs/analise/diferencas-esperadas-*.md` | Planos 3 a 7 |
| `PreparacaoDocsTest` (em `jql/`), `docs/jira/checklist-primeira-conexao.md`, `docs/jira/matriz-compatibilidade.md`, `docs/jira/runbook-sincronizacao.md`, `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest` | Planos 2 e 4A |
| `tokens.css` (`:root` = Claro, `[data-theme='dark']` = Escuro, `[data-theme='sidia']` = Sidia), `ThemeProvider` (temas `light`, `dark`, `sidia`), `Shell`, `nav.ts`, `i18n/pt.json` e `i18n/en.json` | `app/frontend/src/` (Planos 1, 3 e 5) |
| `app/iniciar.sh`, `app/iniciar.ps1`, `app/.env.example`, `app/README.md` | Plano 3A e troca de 26/10 |
| `.claude/hooks/guard.sh`, `.claude/hooks/test-guard.sh` | Planos 1 e 4A |
| `app/backend/settings.gradle.kts` com `rootProject.name = "painel"` (o `installDist` gera `build/install/painel/bin/painel` e `painel.bat`) | `app/backend/` |

## Restrições globais (Global Constraints)

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira por caminho nenhum; com `true`, o acesso fica liberado, e só o usuário muda a flag. Este plano não precisa de acesso ao Jira: testes e verificações rodam offline, qualquer que seja o valor da flag. Nenhum passo, script ou documento deste plano liga a flag.
- **Nenhum teste automático acessa o Jira real nem um provedor OIDC real, mesmo com a flag ligada.** Os testes usam fixtures, `MockEngine` e hosts `.invalid` (`https://jira.exemplo.invalid`, `https://sso.exemplo.invalid`); o emissor OIDC dos testes é falso e roda no próprio teste. As travas existentes continuam verdes: `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest` (Plano 4A) e `sh .claude/hooks/test-guard.sh`. Nenhuma variável nova começa com `JIRA_`.
- **Sem contêiner e sem servidor de banco** (regra 6 do `CLAUDE.md`; adendo, seção 4): nenhum Dockerfile, compose, `.dockerignore`, imagem, Testcontainers, PostgreSQL ou H2, nem verificação que dependa deles. Testes de banco usam `BancoEmArquivo` e `testContextComBanco`.
- **Rede para registros de pacotes é permitida:** dependências Maven/Gradle (`ktor-server-auth`, `-auth-jwt`, `-sessions`) e npm, quando não estiverem em cache (a empresa pode exigir o espelho interno: Plano 0). **Nunca** para o Jira (vale a flag) nem para provedor OIDC real. Este plano não pede dependência npm nova.
- Os papéis vêm **sempre** do `RoleDirectory` na data de hoje, nunca de claims do token. Com `DASHBOARD_DEV_MODE=false`, a inicialização falha fechada se o OIDC não estiver configurado.
- WCAG AA: texto ≥ 4,5:1, texto grande e elementos gráficos ou bordas de componente ≥ 3:1, nos três temas. Cor nunca é o único sinal. Foco visível de 2 px. `Esc` fecha gaveta e janela. `prefers-reduced-motion` é respeitado.
- Escala da spec 4.6: dezenas de pessoas e dezenas de milhares de issues. O teste de carga usa dados sintéticos, **num arquivo SQLite temporário**, e fica fora da suíte padrão (`@Tag("carga")`).
- **Escrita única no SQLite:** um painel por arquivo de banco (`TravaDeProcesso`), um gravador por vez (WAL), leituras não bloqueiam. Transação de escrita é curta e nunca fica aberta durante chamada de rede (adendo, seção 2, linha "Concorrência").
- Configs reais (`equipes.yaml`, `ausencias.yaml`, `papeis.yaml`, `squads.yaml`, e os demais YAML reais ignorados pelo git), arquivos de variáveis reais e arquivos de banco **nunca entram no git nem no pacote de produção**.
- **PONTO DE PARADA OBRIGATÓRIO: remoção do `legacy/` só depois que o usuário aprovar o documento de equivalência** (decisão 1). O executor pergunta ao usuário, em pergunta com opções, ao fim da Tarefa 3; sem um "sim" explícito, a Tarefa 9 não é executada. Antes da remoção, o último commit que contém `legacy/` recebe a tag `legacy-final`.
- Commits em Conventional Commits pt-BR (`feat(seguranca): ...`, `test(a11y): ...`, `perf: ...`, `build(pacote): ...`, `docs: ...`, `chore: remover legacy`). Comentários em pt-BR. Dados fictícios: nomes como `ana.silva` e `bruno`, domínio `.invalid`.

## Foco da revisão (Review Focus)

1. **Rota nova esquecida sem papel ou sem `access_log`.** Uma rota criada numa fase anterior fora do padrão quebra o teste que enumera todas as rotas → `todaRotaApiTemPapelERegistro` (T1).
2. **Token OIDC válido com claim `roles: [GERENTE]` de quem é USUARIO no diretório.** A claim é ignorada e a pessoa vê só o que é dela → `claimDePapelIgnorada` (T2).
3. **Produção sem OIDC configurado** ou com o autenticador de desenvolvimento: o serviço não sobe e cita a variável que falta → `producaoSemOidcNaoSobe` (T2).
4. **Texto secundário e símbolo `●` de atenção no tema Escuro.** São os pares mais perto do limite → `contrasteDosTokensNosTresTemas` (T4).
5. **Escrita bloqueando leitura no SQLite.** Leitores esgotando o pool de 4 conexões e deixando o gravador da sincronização esperar além do `busy_timeout` (15 s), ou uma transação de escrita aberta travando as leituras → `escritaDuranteLeituraNaoBloqueiaLeitores`, `gravadoresConcorrentesSeSerializamSemErro`, `leituraConcorrenteDentroDoOrcamento` (T6).
6. **Pacote de produção com configuração real, arquivo de variáveis, banco ou `legacy/` dentro**, ou artefato de contêiner reintroduzido por cópia de trecho antigo → `verificar-pacote` e `semArtefatosDeContainer` (T7).
7. **Referência ao `legacy/` sobrando** em hook, script, teste ou documento vivo depois da remoção → `nadaReferenciaLegacy` (T9).
8. **Remoção do `legacy/` sem o "sim" do usuário** ao documento de equivalência, ou documento de `docs/jira/` que ainda segue a regra antiga de acesso (substituída em 25/10) no lugar da flag → pré-condições da T9 registradas no `HANDOFF.md`, e `cadaPassoDoChecklistConfirmaAFlag` e `checklistSemRegraAntiga` (Plano 4A, T11) verdes, conferidos na T8.

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/security/{Oidc.kt,OidcSettings.kt,Sessao.kt}
app/backend/src/main/resources/db/migration/V{próxima livre}__indices_de_leitura.sql   # só se a Tarefa 6 pedir
app/backend/gradle/libs.versions.toml, app/backend/build.gradle.kts                    # Ktor auth/sessions (T2); testCarga (T6)
app/backend/src/test/kotlin/br/sidia/painel/{seguranca/InventarioDeRotasTest.kt, seguranca/OidcTest.kt,
  carga/GeradorSintetico.kt, carga/CargaTest.kt, implantacao/PacoteDeProducaoTest.kt,
  docs/EquivalenciaDocTest.kt, docs/ReferenciasLegacyTest.kt, docs/GuiaDoPainelDocTest.kt}
app/frontend/src/a11y/{contraste.ts, contraste.test.ts, teclado.test.tsx, rotulos.test.tsx}
app/frontend/scripts/orcamento-bundle.mjs
app/scripts/{montar-pacote.ps1, montar-pacote.sh, verificar-pacote.ps1, verificar-pacote.sh}
app/.env.example (variáveis OIDC comentadas e sem valor), .gitignore (pasta do pacote)
docs/analise/equivalencia-por-card.md, docs/implantacao.md, docs/manual/guia-do-painel.md, docs/design/revisao-final.md
docs/jira/{checklist-primeira-conexao.md, matriz-compatibilidade.md}, CLAUDE.md, README.md, app/README.md, PRODUCT.md, HANDOFF.md
```

---

### Tarefa 1: Inventário de rotas, papel e registro de acesso

**Arquivos:** Criar `seguranca/InventarioDeRotasTest.kt`. Modificar `Application.kt` só se o teste achar uma rota fora do padrão.

**Interfaces:**
- Consome: o `module(settings, context, authenticator)`, `requireRole`, `executarAuditado`, `testContextComBanco(settings, banco)` e `contarAccessLog()`.
- Produz `val ROTAS_PUBLICAS: Set<String> = setOf("/api/health", "/api/meta")`, mais os estáticos da SPA (se o Plano 9 já expôs a constante, reutilize-a; hoje o conjunto é `private val rotasPublicas` em `security/AccessControl.kt`, escrito como listas de segmentos). A Tarefa 2 acrescenta `/auth/login` e `/auth/callback` (públicas) e `/auth/logout` (com sessão). O teste percorre a árvore de rotas do Ktor (`application.plugin(Routing)` e os filhos recursivos) e monta a lista `(método, caminho)` de tudo que está sob `/api`.

- [ ] **Passo 1: Testes que falham:**
  - `todaRotaApiTemPapelERegistro`. Para cada rota fora de `ROTAS_PUBLICAS`:
    - sem principal → 401 ou 403;
    - USUARIO numa rota de liderança → 403;
    - cada chamada grava exatamente 1 linha em `access_log`, inclusive as negadas.
  - `inventarioConhecido`. A lista de rotas é igual a uma lista esperada versionada no teste, para que uma rota nova obrigue a atualizar o teste e os papéis.
  - `postSoOndeEsperado`. Os únicos `POST` são `/api/distribuicao/decisao` (Plano 7), além dos de autenticação da Tarefa 2.
  - `integridadeEAcessosSoGerente`.
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*InventarioDeRotasTest*'` (sh: `cd app/backend && ./gradlew test --tests '*InventarioDeRotasTest*'`). Resultado esperado: FALHA, ou PASSA se tudo já estiver conforme. Se passar de primeira, conferir a lista esperada contra os Planos 3 a 7.
- [ ] **Passo 3: Corrigir** as rotas apontadas, sem mudar respostas.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte completa (`.\gradlew.bat test`).
- [ ] **Passo 5: Commit** `test(seguranca): inventário de rotas com papel e registro de acesso`.

---

### Tarefa 2: SSO/OIDC atrás da interface, sem provedor real

**Arquivos:** Criar `security/OidcSettings.kt`, `security/Oidc.kt` (substitui o stub `OidcAuthenticator` de `Authenticator.kt`) e `security/Sessao.kt`. Modificar `config/Settings.kt` (campo `oidc: OidcSettings?`, lido por `Settings.fromEnv`; o `toString` mostra só "configurado" ou "ausente"), `Application.kt` (`autenticadorPadrao` passa a receber o `AppContext`, porque o autenticador precisa de `roles` e `clock`), `app/.env.example` (variáveis comentadas e sem valor, com explicação em pt-BR), `app/backend/gradle/libs.versions.toml` e `app/backend/build.gradle.kts` (`ktor-server-auth`, `ktor-server-auth-jwt`, `ktor-server-sessions`, se ainda não estiverem no build; versão do Ktor, `3.3.1`) e o `Shell` do frontend (botão "Sair" fora do modo desenvolvimento). Teste: `seguranca/OidcTest.kt` e `config/SettingsTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class OidcSettings(val issuer: String, val clientId: String, val clientSecret: String?, val redirectUri: String,
      val claimUsername: String = "preferred_username", val escopos: List<String> = listOf("openid", "profile"),
      val segredoDaSessao: ByteArray, val duracaoDaSessao: Duration = 8.hours)
  class OidcAuthenticator(cfg: OidcSettings, roles: RoleDirectory, clock: Clock, http: HttpClient) : Authenticator
  fun Route.rotasDeLogin(cfg: OidcSettings, auditoria: AccessLog)   // GET /auth/login, GET /auth/callback, POST /auth/logout
  ```
- Variáveis (lidas como as demais, por `Settings.fromEnv`, a partir do arquivo de variáveis da subida (`ArquivoEnv`) e do ambiente do processo):

  | Variável | Obrigatória | Regra |
  |---|---|---|
  | `OIDC_ISSUER` | sim | URL `https` |
  | `OIDC_CLIENT_ID` | sim | — |
  | `OIDC_CLIENT_SECRET` | não | — |
  | `OIDC_REDIRECT_URI` | sim | — |
  | `OIDC_CLAIM_USERNAME` | não | — |
  | `SESSION_SECRET` | sim | ≥ 32 bytes, em base64 |

  Em produção, o arquivo de variáveis real fica fora do repositório e do pacote, com permissão restrita à conta do serviço (detalhe no Plano 13). Nada de valor real em `app/.env.example`.
- Comportamento:
  - Fluxo *authorization code* com PKCE, `state` e `nonce`.
  - O `id_token` é validado com o JWKS da descoberta (`/.well-known/openid-configuration`): assinatura RS256, `iss`, `aud`, `exp` e `nonce`.
  - O username é `normalizarUsername(claim)`. Os papéis saem de `principalFor(username, roles, hoje)`.
  - Cookie de sessão `painel_sessao` com `HttpOnly`, `Secure`, `SameSite=Lax`, assinado e cifrado. O painel escuta atrás do proxy da empresa, que termina o TLS (Plano 13).
  - Login e logout gravam `access_log` sem token.
  - `DASHBOARD_DEV_MODE=false` sem as variáveis obrigatórias → `IllegalStateException` na inicialização (dentro de `Settings.fromEnv`, antes de `AppContext.load`), citando só o nome da variável que falta.
  - O provedor concreto continua a definir com a infraestrutura da empresa (spec 9).

- [ ] **Passo 1: Testes que falham.** O emissor falso responde descoberta, JWKS e token por `MockEngine`, com chave RSA gerada no teste. Os testes que precisam de banco usam `testContextComBanco(settings, banco)`.
  - `tokenValidoViraPrincipalDoDiretorio`: `preferred_username` igual a `Ana.Silva` resulta em `ana.silva`, com os papéis do diretório.
  - `claimDePapelIgnorada`: um token com `roles: [GERENTE]` de quem é USUARIO no diretório resulta em `roles == {USUARIO}`.
  - `assinaturaInvalida401`, `expirado401`, `issuerErrado401`, `audienceErrada401`, `nonceOuStateErrado401`.
  - `usuarioSemPapelVigente403`.
  - `cookieComFlagsSeguras`.
  - `logoutLimpaSessao`.
  - `producaoSemOidcNaoSobe` (em `SettingsTest`).
  - `settingsToStringSemSegredos`: `Settings.toString()` não contém o valor de `SESSION_SECRET` nem de `OIDC_CLIENT_SECRET`.
  - `devModeContinuaSemLogin`.
  - `nenhumaRequisicaoParaHostReal`: o `MockEngine` registra só `sso.exemplo.invalid`.
  - `logNaoGuardaToken`.
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*OidcTest*' --tests '*SettingsTest*'` (sh: `./gradlew test --tests '*OidcTest*' --tests '*SettingsTest*'`). Resultado esperado: FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte completa e a trava do hook (`sh .claude/hooks/test-guard.sh`; no Windows, também o `test-PreToolUse.ps1` da tabela de comandos).
- [ ] **Passo 5: Commit** `feat(seguranca): SSO/OIDC genérico atrás da interface, testado com emissor falso`.

---

### Tarefa 3: Conferência de equivalência por card

**Arquivos:** Criar `docs/analise/equivalencia-por-card.md` e `docs/EquivalenciaDocTest.kt`. Modificar os `CompatLegado*Test` se faltar algum caso apontado pelo documento.

**Interfaces:**
- Consome todos os `CompatLegado*Test` e os documentos de diferenças esperadas das Fases 3 a 7.
- Produz uma tabela com uma linha por ID do relatório:
  - `ID`;
  - `Card novo` (rota);
  - `Situação`, que é uma de `equivalente`, `diferença esperada`, `reformulado (sem ranking)`, `removido` ou `novo`;
  - `Prova` (classe e método de teste);
  - `Documento` (link para as diferenças esperadas).
- Os cards novos da spec 6.2 entram como `novo`.

- [ ] **Passo 1: Testes que falham:**
  - `todoIdDoRelatorioTemLinha`: lê `relatorio-cards-app-atual.md`, extrai os IDs com a regex `[A-Z]{2}-\d{2}` e confere que cada um aparece no documento. QD, AU e SI entram pelo prefixo.
  - `todaProvaExiste`: cada `Classe.metodo` citado existe em `src/test`, conferido por reflexão sobre as classes compiladas.
  - `situacaoValida`.
  - `nenhumaLinhaSemProvaSalvoRemovido`.
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*EquivalenciaDocTest*'` (sh: `./gradlew test --tests '*EquivalenciaDocTest*'`). Resultado esperado: FALHA.
- [ ] **Passo 3: Escrever** o documento a partir dos documentos de diferenças.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA. Rodar `.\gradlew.bat test --tests '*CompatLegado*'` com o `legacy/` ainda presente, para conferir as fórmulas citadas.
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário (OBRIGATÓRIO).** Apresentar o resumo (quantos equivalentes, diferenças, reformulados e removidos) e o link do documento como pergunta com opções, a recomendada primeiro:
  1. "Aprovar e remover o `legacy/` na Tarefa 9 (Recomendada)": o código antigo fica na tag `legacy-final` e o repositório deixa de ter duas implementações.
  2. "Aprovar com ajustes (indicar quais)": o executor aplica os ajustes ao documento e pergunta de novo antes da Tarefa 9.
  3. "Ainda não; manter o `legacy/`": a Tarefa 9 fica de fora deste PR e a pendência vai ao `HANDOFF.md`.

  Registrar a resposta e a data no `HANDOFF.md` e no arquivo de progresso. Sem aprovação, seguir com as Tarefas 4 a 8 e 10 e pular a 9 (ver a Tarefa 9).
- [ ] **Passo 6: Commit** `docs: equivalência por card com o app atual`.

---

### Tarefa 4: Acessibilidade WCAG AA

**Arquivos:** Criar `app/frontend/src/a11y/contraste.ts`, `contraste.test.ts`, `teclado.test.tsx` e `rotulos.test.tsx`. Modificar `theme/tokens.css` (só valores que reprovarem), o `Shell` (link "Pular para o conteúdo", `<main>`, `<nav aria-label>`), o `DrillDrawer` e o `CardPage`, se algum teste apontar.

**Interfaces:**
- Produz `function contraste(corA: string, corB: string): number`, com a fórmula de luminância relativa da WCAG 2.x, em `a11y/contraste.ts`.
- O teste lê os tokens de cada tema direto do `tokens.css`: Claro (`:root`), Escuro (`[data-theme='dark']`) e Sidia (`[data-theme='sidia']`); os nomes de tema no código são `light`, `dark` e `sidia`. Verifica estes pares (nomes dos tokens entre parênteses; confira em `tokens.css`):

  | Par | Mínimo |
  |---|---|
  | texto (`--text`) / fundo (`--bg`) | 4,5 |
  | texto secundário (`--text2`) / fundo | 4,5 |
  | texto / superfície (`--surface`) | 4,5 |
  | ação primária (`--primary`) / fundo; `--on-primary` / `--primary` | 4,5 |
  | ok, atenção e problema (`--ok`, `--warn`, `--bad`, como texto) / fundo | 4,5 |
  | símbolo `✓` `●` `▲` (mesmas cores) / superfície | 3,0 |
  | foco (`--focus`) / fundo | 3,0 |
  | texto do menu (`--menu-text`, `--menu-text2`) / menu (`--menu`), tema Sidia | 4,5 |

  Os valores de referência estão no plano visual, seção 3.
- Nenhuma dependência nova: os rótulos e papéis ARIA são conferidos com Testing Library, e não com axe.

- [ ] **Passo 1: Testes que falham:**
  - `contrasteDosTokensNosTresTemas`: a mensagem de falha cita tema, par e razão.
  - `laranjaEVerdeDaMarcaNuncaComoTexto`: `#F58634` e `#13DC14` não aparecem como `color` de texto sobre fundo claro.
  - `focoVisivelDoisPixels`: o CSS tem `:focus-visible` com `outline` de 2 px.
  - `reducedMotion`: há regra `@media (prefers-reduced-motion: reduce)`.
  - `pularParaOConteudo`: é o primeiro foco e leva ao `<main>`.
  - `tabPercorreMenuFiltrosKpisTabela`.
  - `escFechaGavetaEDevolveFoco`, em todas as telas com gaveta.
  - `kpisTemNomeAcessivel`: `getByRole('button', {name: /Vencidas.*18/})`.
  - `tabelasComCabecalho`: `th` com `scope`.
  - `estadoNuncaSoPorCor`: cada célula com classe de estado tem um símbolo e um texto.
  - `celular390MenuViraBotao`.
  - `paridadeDeChavesPtEn`: já existe desde o Plano 5 (`i18n/pt.json` e `i18n/en.json`); rodar.
- [ ] **Passo 2: Rodar** `cd app\frontend; pnpm vitest run src/a11y` (sh: `cd app/frontend && pnpm vitest run src/a11y`). Resultado esperado: FALHA.
- [ ] **Passo 3: Corrigir** tokens e componentes apontados.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte inteira e `pnpm build` (`cd app\frontend; pnpm vitest run; if ($?) { pnpm build }`).
- [ ] **Passo 5: Commit** `test(a11y): contraste dos três temas, teclado e rótulos`.

---

### Tarefa 5: Revisão visual dos três temas e responsividade

**Arquivos:** Criar `docs/design/revisao-final.md` (achados e correções). Modificar CSS e telas só onde a captura apontar.

**Interfaces:** Consome todas as telas do menu.

- [ ] **Passo 1: Captura.** Subir o painel em modo demonstração, **com um banco descartável** para não tocar nos dados locais, e fazer **uma** rodada de capturas com o Chrome em modo headless (sem dependência nova):
  - Windows: `cd app; $env:DASHBOARD_DEMO_DATA='true'; $env:DATABASE_PATH='data/revisao.db'; .\iniciar.ps1` (sh: `cd app && DASHBOARD_DEMO_DATA=true DATABASE_PATH=data/revisao.db sh iniciar.sh`). A demonstração só sobe com o modo desenvolvimento ligado e a flag do Jira desligada no arquivo de variáveis (README do app); nenhuma requisição ao Jira é feita.
  - Capturas: todas as telas do `nav.ts`; larguras de 1440 e 390 px; temas Claro, Escuro e Sidia (escolhidos no seletor do `Shell`, preferência `painel.tema`); pt-BR e inglês só na Central, no Meu painel e na Auditoria.
  - Ao fim, pare o painel e apague `app/data/revisao.db` (e `-wal`, `-shm`).
- [ ] **Passo 2: Conferir** cada captura contra o plano visual:
  - gramática única da seção 5 (cabeçalho, filtros em chips, KPIs, no máximo dois gráficos e uma tabela);
  - densa nas telas de trabalho e editorial nas de leitura;
  - faixa vermelha do modo desenvolvimento;
  - textos sem corte em inglês;
  - tabelas com rolagem dentro do cartão a 390 px.
- [ ] **Passo 3: Corrigir** tudo de uma vez e registrar no documento.
- [ ] **Passo 4: Fazer** no máximo mais uma rodada de confirmação; rodar a suíte do frontend e `pnpm build`.
- [ ] **Passo 5: Commit** `fix(ui): revisão visual final dos três temas e do celular`.

---

### Tarefa 6: Desempenho na escala da spec, sobre SQLite em arquivo

**Contexto de plataforma:** o banco é um arquivo SQLite em modo WAL, com **um gravador por vez**, leituras que não bloqueiam, `busy_timeout` de 15 s e pool de 4 conexões (`DATABASE_POOL_SIZE`). Os orçamentos abaixo medem exatamente isso: o painel lendo muito enquanto a sincronização escreve.

**Arquivos:** Criar `carga/GeradorSintetico.kt` e `carga/CargaTest.kt`, com `@Tag("carga")`. Modificar `app/backend/build.gradle.kts`: a tarefa `test` passa a excluir a tag (`useJUnitPlatform { excludeTags("carga") }`) e uma nova tarefa `testCarga` (tipo `Test`) roda só a tag `carga`, com `maxHeapSize = "512m"`, o mesmo `testClassesDirs` e `classpath` do `test`, e as **mesmas** `environment("JIRA_LIVE_ENABLED", "false")` e `systemProperty` (`painel.configDir`, `painel.mainSrcDir`), que não são herdadas. Criar `resources/db/migration/V{próxima livre}__indices_de_leitura.sql` **só se** um orçamento estourar (confirme o número com `ls app/backend/src/main/resources/db/migration`; hoje seria a `V2`; nunca edite migração mesclada). Criar `app/frontend/scripts/orcamento-bundle.mjs`.

**Interfaces:**
- Produz `object GeradorSintetico { fun popular(ds: DataSource, semente: Long, issues: Int = 30_000, eventosPorIssue: Int = 10, pessoas: Int = 60, worklogs: Int = 50_000) }`.
  - Os dados são fictícios e determinísticos, com usernames `dev.sint001` a `dev.sint060`, chaves `SINT-n` e instância `samsung`.
  - Grava direto nas tabelas do `V1__base.sql` (`person`, `issue`, `change_event`, `worklog`) com `PreparedStatement` em lote, **uma transação por lote** (por exemplo 2.000 issues), instantes em milissegundos UTC, `duedate` em texto ISO, `labels` em JSON.
  - Cria o `RoleDirectory` de teste com 6 squads: gera um YAML temporário no formato de `papeis.exemplo.yaml` e carrega com `RoleDirectory.fromYaml(arquivo)`.
  - Expõe também `fun lotesDeSincronizacao(semente: Long, quantidade: Int): List<...>` com issues no formato que o `SqliteIssueStore.upsert` aceita, para simular o gravador da sincronização pelo caminho real.
- Contexto de medição: `testContextComBanco(settings, banco)` com o `BancoEmArquivo` da classe (arquivo temporário, apagado no `@AfterAll`), população uma vez no `@BeforeAll`, HTTP por `testApplication` e autenticador de teste que devolve GERENTE ou SQUAD_LEADER. Chamadas simultâneas por `Dispatchers.IO`. Os testes que escrevem rodam por último (`@TestMethodOrder`), porque alteram os dados.
- Orçamentos, medidos localmente em 5 execuções depois de 1 de aquecimento. Registre no `progress.md` a máquina de referência (CPU, memória, SSD ou HDD) junto dos tempos; os valores valem para o PC da empresa (Plano 0):

  | Alvo | Orçamento |
  |---|---|
  | cada `GET /api/cards/*` e `/api/drill/*` (um cliente) | p95 < 2 s |
  | `/api/central` (um cliente) | p95 < 3 s |
  | leitura concorrente: 8 clientes simultâneos (o dobro do pool) por 20 s, mistura de cards | p95 < 4 s, zero resposta 5xx, zero `SQLITE_BUSY` |
  | escrita durante leitura: 4 clientes lendo enquanto a sincronização simulada grava 2.000 issues com eventos pelo `SqliteIssueStore.upsert` | leituras p95 < 4 s; gravação termina em < 60 s sem exceção |
  | dois gravadores ao mesmo tempo | ambos terminam, sem `SQLITE_BUSY` (serializam pelo `busy_timeout`) |
  | transação de escrita aberta por 2 s numa conexão | leituras seguem respondendo (< 2 s cada) |
  | memória do processo de teste (`-Xmx512m`) | sem `OutOfMemoryError`; heap < 512 MB |
  | bundle principal do frontend (gzip) | < 300 KB |

- [ ] **Passo 1: Testes que falham:**
  - `cardsDentroDoOrcamento`: mede cada card como GERENTE e como SQUAD_LEADER.
  - `centralDentroDoOrcamento`.
  - `drillMesmoTamanhoNaCarga`: amostra 5 cards.
  - `leituraConcorrenteDentroDoOrcamento`.
  - `escritaDuranteLeituraNaoBloqueiaLeitores`.
  - `gravadoresConcorrentesSeSerializamSemErro`.
  - `leituraSegueDuranteTransacaoDeEscritaAberta`.
  - `walVoltaAoTamanhoAposCheckpoint`: depois da carga, `PRAGMA wal_checkpoint(TRUNCATE)` deixa o arquivo `-wal` com menos de 1 MB; registra o tamanho do banco.
  - `orcamentoDoBundle`: o script lê `dist/` e falha acima do limite.
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat testCarga` (sh: `./gradlew testCarga`) e `cd app\frontend; pnpm build; if ($?) { node scripts\orcamento-bundle.mjs }` (sh: `pnpm build && node scripts/orcamento-bundle.mjs`), e registrar os tempos.
- [ ] **Passo 3: Se algo estourar,** atacar nesta ordem e medir de novo:
  1. índices de leitura na migração (próxima livre). O banco já tem `idx_change_event_issue_at (issue_id, at)`, `idx_issue_status_resolved (status, resolved)`, `idx_worklog_started (started)` e `issue_os_version_idx`; candidatos novos: `issue(instance, resolved)`, `issue(instance, assignee_ref)`, `issue(instance, removida_em)`, `change_event(field, at)` e `worklog(issue_id, started)`. Confirme cada um com `EXPLAIN QUERY PLAN` (o plano deve mostrar `USING INDEX`, e não `SCAN issue`) e acrescente o teste `consultasCriticasUsamIndice`. Se o plano ignorar o índice por falta de estatística, meça com e sem `ANALYZE` e registre `Ruling:`;
  2. consultas em lote;
  3. *code splitting* por tela no frontend;
  4. só para a concorrência: se o pool de 4 conexões for o gargalo, medir com `DATABASE_POOL_SIZE` maior (a variável já existe) e registrar `Ruling:` com o valor recomendado; mais conexões não criam mais gravadores, só mais leitores.

  Cache de resultados só se, depois dos passos acima, algum orçamento ainda estourar (decisão 3). Nesse caso, a chave do cache inclui o escopo visível do principal (papel, pessoas visíveis), os filtros e a versão das regras, para que um papel nunca receba o número calculado para outro; registrar como `Ruling:` no arquivo de progresso.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também a suíte padrão (`.\gradlew.bat test`) e confirmar que a tag `carga` fica fora dela.
- [ ] **Passo 5: Commit** `perf: orçamento de tempo dos cards e do bundle na escala da spec, sobre SQLite`.

---

### Tarefa 7: Pacote de produção (`installDist` + build do frontend) e guia de implantação sem contêiner

**Escopo:** esta tarefa entrega o **pacote** e a **verificação** dele. O serviço do sistema operacional, o backup consistente do SQLite (`VACUUM INTO` ou API de backup online) e a restauração testada são do **Plano 12**; a implantação real (proxy, TLS, cofre de segredos, atualização e reversão ensaiadas) é do **Plano 13**. O guia só aponta para eles por número.

**Arquivos:** Criar `app/scripts/montar-pacote.ps1` e `montar-pacote.sh`, `app/scripts/verificar-pacote.ps1` e `verificar-pacote.sh`, `docs/implantacao.md` e o teste estático `implantacao/PacoteDeProducaoTest.kt`. Modificar `app/.env.example` (variáveis `OIDC_*` e `SESSION_SECRET` comentadas e sem valor, se a Tarefa 2 ainda não o fez), `.gitignore` (pasta `app/pacote/`) e `app/README.md` (seção curta "Produção" apontando para `docs/implantacao.md`).

**Interfaces:**
- `montar-pacote` (a partir de `app/`):
  1. `pnpm install --frozen-lockfile` e `pnpm build` em `frontend/`;
  2. `gradlew installDist` em `backend/` (saída: `backend/build/install/painel/bin/painel` e `painel.bat`, e `lib/*.jar`);
  3. recria `app/pacote/painel/` (apaga só dentro de `app/pacote/`, conferindo o caminho antes) com:
     ```
     bin/            # painel, painel.bat
     lib/            # jars
     frontend/dist/  # build da SPA
     config/         # só os modelos *.exemplo.yaml (e *.demo.yaml) e a pasta cards/
     .env.example    # modelo; nunca um arquivo de variáveis real
     VERSAO.txt      # versão do build.gradle.kts, commit curto (git rev-parse --short HEAD) e data
     LEIA-ME.txt     # como rodar (abaixo)
     ```
- Como rodar o pacote (a pasta de trabalho é a raiz do pacote, porque `config`, `frontend/dist` e `data` são caminhos relativos). **Dados, configuração e arquivo de variáveis reais ficam FORA da pasta do pacote**, para que trocar o pacote não apague nada:
  - Windows 11: `$env:PAINEL_CONFIG_DIR='<pasta dos YAML reais>'; $env:DATABASE_PATH='<pasta de dados>\painel.db'; $env:PAINEL_ENV_FILE='<arquivo de variáveis>'; Set-Location <pasta do pacote>; .\bin\painel.bat`
  - `sh`: `PAINEL_CONFIG_DIR=... DATABASE_PATH=... PAINEL_ENV_FILE=... ./bin/painel` a partir da pasta do pacote.
  - Exige JDK 21 (`JAVA_HOME`). O painel escuta na porta 8080 (fixa em `Application.kt`).
- `verificar-pacote` (a partir de `app/`; falha com código diferente de 0 e mensagem curta):
  - **Conteúdo:** no pacote não pode haver nenhum YAML real (`equipes`, `ausencias`, `papeis`, `squads`, `calendario`, `status_map`, `jira_campos`, `vinculos` sem o sufixo `.exemplo` ou `.demo`), nenhum arquivo de variáveis além de `.env.example`, nenhum `*.db`, `*.db-wal`, `*.db-shm` ou `*.lock`, nem a pasta `legacy/`;
  - **Estrutura:** existem `bin/painel` e `bin/painel.bat`, o jar do painel em `lib/` e `frontend/dist/index.html`;
  - **Fumaça (usa uma pasta temporária para o banco, o arquivo de variáveis de teste e a porta 8080; aborta com aviso se a porta já estiver em uso):**
    1. modo produção sem OIDC (`DASHBOARD_DEV_MODE=false`): o processo termina com código diferente de 0 e a saída cita `OIDC_ISSUER`, sem nenhum valor de segredo;
    2. modo desenvolvimento (padrão): `GET http://127.0.0.1:8080/api/health` devolve `{"ok":true}`; o script encerra o processo (no Windows, `taskkill /T /F` na árvore do processo).
  - O arquivo de variáveis de teste não contém a flag do Jira (linha ausente = desligada); o script nunca a liga.
- `docs/implantacao.md` cobre:
  - o que é o pacote, como montar e verificar (comandos da tabela do início);
  - variáveis e pastas: `PAINEL_CONFIG_DIR`, `DATABASE_PATH`, `PAINEL_ENV_FILE`, `PAINEL_STATIC_DIR`, `DATABASE_POOL_SIZE`, `RETENTION_MONTHS`, `ACCESS_LOG_RETENTION_MONTHS`; por que ficam fora do pacote;
  - como ligar o OIDC (`OIDC_*`, `SESSION_SECRET`) e a falha fechada em produção;
  - a ordem segura de ligar o Jira (link para `docs/jira/runbook-sincronizacao.md` e `docs/jira/checklist-primeira-conexao.md`); quem liga é só o usuário;
  - um painel por arquivo de banco (`painel.db.lock`);
  - **serviço do sistema operacional, backup e restauração do SQLite:** remete ao Plano 12; **atualização, reversão, proxy, TLS e cofre:** remete ao Plano 13. Regra de reversão: o esquema só avança por migração, então toda migração nova deve ser compatível com o pacote anterior ou ter backup antes;
  - o que pedir à infraestrutura (provedor OIDC, proxy, TLS, conta de serviço sem privilégio, regra de firewall);
  - validação com o DPO antes do uso real (Integridade, auditoria, retenção).

**Ponto de parada: perguntar ao usuário (endereço de escuta).** Hoje o painel escuta em todas as interfaces (`embeddedServer(Netty, port = 8080)`, sem host) e o README do app diz que quem restringe o acesso é o firewall. Pergunta com opções:
1. "Acrescentar a variável `PAINEL_HOST` com padrão `127.0.0.1` (Recomendada)": seguro por padrão, vale também no PC do desenvolvedor; exige uma mudança pequena em `Application.kt` e em `Settings`, com o teste `escutaSoEmLocalhostPorPadrao`; o proxy na mesma máquina continua funcionando, e outra máquina exigiria configurar o valor.
2. "Só documentar a regra de firewall do Windows": nenhuma mudança de código; depende de a regra existir e de alguém conferi-la; o risco é expor o painel por engano.
3. "Deixar para o Plano 13 (proxy confiável)": nada muda agora; o guia desta tarefa avisa que a porta fica aberta a toda a rede da máquina até lá.

Registrar a resposta e a data no `HANDOFF.md` e no progresso. Se a opção 1 for aprovada, a tarefa também modifica `Application.kt` e `Settings.kt` e `.env.example`; as demais seguem sem mudança de código.

- [ ] **Passo 1: Testes que falham** (`PacoteDeProducaoTest`, estático, lê texto e não monta o pacote):
  - `scriptsDeMontagemEVerificacaoNosDoisSistemas`: existem os quatro scripts.
  - `montagemCopiaSoModelos`: os scripts de montagem só copiam `*.exemplo.yaml`, `*.demo.yaml`, `cards/` e `.env.example`; não citam arquivo de variáveis real nem `*.db`.
  - `pastaDoPacoteIgnoradaPeloGit`: `.gitignore` tem `app/pacote/`.
  - `envExemploSemValoresDeSegredo`: em `app/.env.example`, `OIDC_*` e `SESSION_SECRET` estão comentadas e sem valor; nenhuma variável `*_SECRET`, `*_TOKEN` ou `*_PASSWORD` tem valor.
  - `semArtefatosDeContainer`: não existe `Dockerfile`, `docker-compose*`, `compose*.yml` nem `.dockerignore` em `app/` nem na raiz do repositório (ficam de fora `legacy/` até a Tarefa 9, `docs/superpowers/`, `.claude/`, `node_modules` e `build`).
  - `documentoDeImplantacaoCobreOsTopicos`: `docs/implantacao.md` cita `installDist`, `montar-pacote`, `verificar-pacote`, `PAINEL_CONFIG_DIR`, `DATABASE_PATH`, `OIDC_ISSUER`, `SESSION_SECRET`, `RETENTION_MONTHS`, `painel.db.lock`, o checklist e o runbook, e os Planos 12 e 13.
  - `documentoSemComandosDeContainerNemPostgres`: o documento não contém `docker compose`, `docker run`, `Dockerfile`, `pg_dump`, `pg_restore` nem `Testcontainers`.
  - `escutaSoEmLocalhostPorPadrao` (só se a opção 1 do ponto de parada for aprovada).
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*PacoteDeProducaoTest*'` (sh: `./gradlew test --tests '*PacoteDeProducaoTest*'`). Resultado esperado: FALHA.
- [ ] **Passo 3: Implementar** scripts, documento, `.gitignore` e `.env.example`.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA.
- [ ] **Passo 5: Montar e verificar o pacote:** `cd app; powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\montar-pacote.ps1` e depois `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\verificar-pacote.ps1` (sh: `cd app && sh scripts/montar-pacote.sh && sh scripts/verificar-pacote.sh`). Resultado esperado: conteúdo sem configuração real, arquivo de variáveis nem banco; produção sem `OIDC_ISSUER` recusa subir; `/api/health` responde em desenvolvimento. Dependências de pacotes baixadas do registro ou do espelho são permitidas; nenhum acesso ao Jira.
- [ ] **Passo 6: Commit** `build(pacote): pacote de produção verificável e guia de implantação sem contêiner`.

---

### Tarefa 8: Documentos de preparação da 1ª conexão

**Arquivos:** Modificar `docs/jira/checklist-primeira-conexao.md`, `docs/jira/matriz-compatibilidade.md`, `docs/jira/runbook-sincronizacao.md` (Plano 4A), `app/config/jira_campos.exemplo.yaml` (finalidades) e `app/backend/src/test/kotlin/br/sidia/painel/jql/PreparacaoDocsTest.kt`.

**Regra de acesso nos documentos:** desde 25/10 a única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Os documentos deixam a regra antiga de acesso (substituída em 25/10): cada passo do checklist exige a flag ligada, **só pelo usuário**, e segue a ordem do checklist na primeira conexão. O Plano 4A (Tarefa 11) já reescreveu o checklist e trocou o teste por `cadaPassoDoChecklistConfirmaAFlag`; esta tarefa **não cria outro nome**: confere que esse teste e `checklistSemRegraAntiga` passam e estende a checagem "sem regra antiga" aos demais `.md` de `docs/jira/`. Os documentos também não podem mandar usar contêiner, Docker nem PostgreSQL (o painel roda como pacote, com SQLite; ver `docs/implantacao.md`).

**Interfaces:**
- Consome o dicionário, as fichas de todos os cards e os endpoints usados pelo `JiraClient`: `search`, `changelog`, `worklog`, `field` e `myself`.
- O checklist passa a listar, com "o que conferir" e "quando parar":
  - cada campo "a confirmar" (`Story Points`, `Epic Link`, `Flagged`, `cf[12416]`, `cf[11412]`, OS Version e os demais do TG-Basic);
  - o vocabulário de `priority`, `labelsCriticidade` e `prioridadePadrao`;
  - a `ordem` do fluxo e as resoluções negativas;
  - o formato do worklog;
  - o `from/to` do assignee no changelog;
  - o Reporter como PL (spec 11).

- [ ] **Passo 1: Testes que falham:**
  - `checklistCitaCadaCampoAConfirmar`: cada campo do dicionário com `finalidade` "a confirmar" aparece no checklist.
  - `matrizCitaCadaEndpoint`.
  - `cadaFichaUsaSoCamposDoDicionario`: carrega todas as `cards/*.card.yaml`.
  - `cadaPassoDoChecklistConfirmaAFlag` (criado no Plano 4A, T11; **não** criar outro nome): continua passando. Cada seção de passo do checklist contém "Confirme que a flag de acesso ao Jira está ligada" (frase sem o nome da flag, para o hook deixar editar); o documento diz que só o usuário muda a flag.
  - `documentosDeJiraSemRegraAntiga` (novo; estende o `checklistSemRegraAntiga` do 4A): nenhum documento de `docs/jira/` contém "autorização expressa" nem "por pesquisa". Se ainda existir no mesmo arquivo algum teste que exija essa expressão (por exemplo um `runbookAvisaRegraDoAssistente` de versão antiga do Plano 4A), ele é removido ou trocado pela checagem da flag.
  - `documentosDeJiraSemContainerNemPostgres`: nenhum documento de `docs/jira/` manda usar `docker`, `docker compose` ou PostgreSQL.
  - `semHostRealNemToken`.
  - `testesNaoAcessamOJiraReal`: os documentos dizem que testes automáticos usam só fixtures e `MockEngine`, mesmo com a flag ligada (a prova técnica continua nos testes `SomenteAFabricaCriaClientesJira` e `FlagDesligadaZeroRequisicoesTest`).
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*PreparacaoDocsTest*'` (sh: `./gradlew test --tests '*PreparacaoDocsTest*'`). Resultado esperado: FALHA.
- [ ] **Passo 3: Atualizar** os documentos.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA.
- [ ] **Passo 5: Commit** `docs(jira): checklist da 1ª conexão e matriz de compatibilidade finais`.

---

### Tarefa 9: Remoção do `legacy/`

**Ponto de parada: perguntar ao usuário (OBRIGATÓRIO). Pré-condições (parar se faltar alguma):**
- o usuário aprovou, com um "sim" explícito registrado no `HANDOFF.md`, o documento de equivalência (Tarefa 3, Passo 5; decisão 1);
- todos os `CompatLegado*Test` estão verdes;
- a alteração local em `legacy/.env.example` foi resolvida com o usuário, também em pergunta com opções: "descartar (Recomendada)" (o arquivo é só um modelo e o código antigo fica na tag) ou "guardar o valor em outro lugar antes" (nada se perde, mas exige um destino).

**Se o usuário ainda não aprovou:** não executar esta tarefa. Concluir as demais, abrir o PR sem a remoção e registrar no `HANDOFF.md` a pendência "remoção do `legacy/` aguardando aprovação do documento de equivalência", com data. A remoção vira um PR próprio depois do "sim".

**Arquivos:**
- Remover `legacy/` (inclusive o `Dockerfile` e o `docker-compose.yml` que ele traz: somem junto, sem tocar em nada fora dele).
- Modificar `.gitignore` (linha `legacy/data/`), `CLAUDE.md` (tabela "Onde está cada coisa" e "Estrutura e comandos"), `README.md`, `app/README.md`, `HANDOFF.md`, `PRODUCT.md` ("Evidence on Hand"), `docs/analise/diferencas-esperadas-*.md`, `docs/guia-execucao-outra-ia.md`, e `.claude/hooks/guard.sh` e `test-guard.sh` se citarem `legacy`.
- Modificar os comentários dos `CompatLegado*Test`: a referência passa de `legacy/...` para "`legacy/...` na tag `legacy-final`".
- Criar `docs/ReferenciasLegacyTest.kt`.
- O `semArtefatosDeContainer` (T7) deixa de precisar da exceção de `legacy/`.

**Interfaces:** os documentos vivos citam o código antigo como "tag `legacy-final`". Os planos e specs em `docs/superpowers/` são histórico e ficam como estão.

- [ ] **Passo 1: Criar a tag** `git tag legacy-final` no commit atual, que ainda contém `legacy/`. A tag sobe junto com o push do PR.
- [ ] **Passo 2: Teste que falha.** `nadaReferenciaLegacy` varre `app/`, `.claude/hooks/`, `CLAUDE.md`, `README.md`, `PRODUCT.md`, `HANDOFF.md` e `docs/`, exceto `docs/superpowers/` e o próprio documento de equivalência. Nenhum caminho `legacy/` pode aparecer sem a menção `legacy-final`. O teste confere também `nenhumCodigoLeLegacy` em `src/main` e `src/test`.
- [ ] **Passo 3: Remover** com `git rm -r legacy/` e atualizar as referências.
- [ ] **Passo 4: Rodar** as suítes completas do backend e do frontend (`cd app\backend; .\gradlew.bat test`; `cd app\frontend; pnpm vitest run; if ($?) { pnpm build }`), `sh .claude/hooks/test-guard.sh` e o pacote (`montar-pacote` e `verificar-pacote` da Tarefa 7, comandos da tabela do início). Resultado esperado: PASSA. Não há verificação com Docker.
- [ ] **Passo 5: Commit** `chore: remover o app Python legado (preservado na tag legacy-final)`.

---

### Tarefa 10: Documentação final e guia do painel

**Arquivos:** Criar `docs/manual/guia-do-painel.md` e `docs/GuiaDoPainelDocTest.kt`. Modificar `CLAUDE.md`, `app/README.md`, `PRODUCT.md` (Evidence on Hand e Capabilities), `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado final das fases) e `HANDOFF.md`.

**Interfaces:**
- O guia tem uma seção por item do `nav.ts`, com:
  - a pergunta que a tela responde;
  - quem vê (papéis);
  - de onde vêm os números (ficha `cards/<id>.card.yaml`, fórmula e aceite);
  - os avisos possíveis;
  - como abrir o detalhamento.
- O guia é em pt-BR e aponta para `docs/implantacao.md` para quem instala. A versão em inglês fica fora do escopo.

- [ ] **Passo 1: Testes que falham:**
  - `guiaCitaCadaItemDoMenu`: lê os ids de `nav.ts`.
  - `guiaCitaCadaFicha`.
  - `claudeMdSemCaminhoInexistente`: cada caminho em crase na tabela "Onde está cada coisa" existe.
- [ ] **Passo 2: Rodar** `cd app\backend; .\gradlew.bat test --tests '*GuiaDoPainelDocTest*'` (sh: `./gradlew test --tests '*GuiaDoPainelDocTest*'`). Resultado esperado: FALHA.
- [ ] **Passo 3: Escrever** o guia e atualizar os documentos.
- [ ] **Passo 4: Rodar** de novo e esperar PASSA; rodar também as suítes completas.
- [ ] **Passo 5: Commit** `docs: guia do painel e documentação final da etapa offline`.

---

## O que os Planos 12 e 13 herdam deste plano

Os Planos 12 (operação) e 13 (implantação real) são reescritos em paralelo, sem contêiner. Nomes que eles podem consumir:

| Nome | Onde |
|---|---|
| `OidcSettings`, `OidcAuthenticator`, `rotasDeLogin`, cookie `painel_sessao`, `OIDC_*`, `SESSION_SECRET`, `producaoSemOidcNaoSobe` | T2 |
| `ROTAS_PUBLICAS`, `InventarioDeRotasTest.inventarioConhecido` | T1 |
| `app/scripts/montar-pacote.{ps1,sh}`, `app/scripts/verificar-pacote.{ps1,sh}`, `PacoteDeProducaoTest`, `docs/implantacao.md` (substituem o perfil `prod` do compose, o `ComposeTest` e o `verificar-imagem.sh` dos textos antigos) | T7 |
| `GeradorSintetico`, tarefa `testCarga`, orçamentos de leitura concorrente e de escrita durante leitura | T6 |
| Migração de índices (próxima livre, só se a T6 pedir): o Plano 12 confirma o número livre na execução | T6 |

## Autoavaliação

**Cobertura da spec:**

| Requisito | Tarefas |
|---|---|
| Spec 10: nenhuma requisição ao Jira | T2, T8, `test-guard` |
| Spec 10: cada papel vê só o permitido em todas as rotas | T1 |
| Spec 10: três temas e AA nos pares de cor | T4, T5 |
| Spec 10: documentos de preparação | T8 |
| Spec 4.6: provedor OIDC genérico atrás de interface | T2 |
| Spec 4.6: volume e atualidade (escala sobre o banco em arquivo, escrita única, leitura concorrente) | T6 |
| Spec 4.6: ambiente atrás do proxy (pacote de produção sem contêiner) | T7; serviço e backup no Plano 12; implantação real no Plano 13 |
| Spec 4.6: oráculo de números | T3 |
| Spec 4.1: o app Python é substituído e o histórico fica no git | T9 |
| Spec 11: riscos levados ao checklist | T8 |
| Relatório, seção 8 item 2: oráculo antes de aposentar o Python | T3 antes de T9 |
| Adendo de 27/10: nada de Docker, PostgreSQL, Testcontainers | T6, T7 (`semArtefatosDeContainer`), T8, T9 |

**Consistência de tipos:** o `OidcAuthenticator` (T2) implementa o `Authenticator` existente e usa `principalFor`. `ROTAS_PUBLICAS` (T1) inclui as rotas `/auth/*` da T2. O `GeradorSintetico` (T6) grava pelas mesmas tabelas que o `SqliteIssueStore` (esquema de `V1__base.sql`). Os testes de banco usam `BancoEmArquivo` e `testContextComBanco`, nunca H2 nem mock.

**Pendências deliberadas:**
- o provedor OIDC concreto, o logo limpo e a fonte da Sidia;
- a calibração das heurísticas com dados reais;
- a validação com o DPO;
- serviço do SO, backup e restauração do SQLite (Plano 12) e implantação real (Plano 13).

Todas ficam registradas no `HANDOFF.md`.

## Decisões (25/10, valor padrão recomendado, reversível; ajustadas em 27/10)

O usuário delegou estas escolhas em 25/10; vale a opção que o plano recomendava. Cada uma pode ser revertida depois por decisão do usuário. A spec da fase é `docs/superpowers/specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md`.

1. **Remover o `legacy/` depois que o usuário aprovar o documento de equivalência card a card.** O código antigo fica recuperável pela tag `legacy-final`, e o repositório deixa de ter duas implementações. **A aprovação do documento continua com o usuário: é ponto de parada obrigatório** (Tarefa 3, Passo 5; pré-condições da Tarefa 9). O executor pergunta e não remove nada sem o "sim". Alternativa descartada: manter o `legacy/` até a 1ª conexão real.
2. **Oráculo de equivalência: cenários pequenos por card, com a fórmula do app atual conferida no código** (padrão dos `CompatLegado*Test`; offline e rápido; Tarefa 3). Alternativa descartada: rodar o app Python em modo demonstração e comparar agregados.
3. **Desempenho: primeiro índices, depois consultas em lote e *code splitting*; cache só se ainda faltar**, com chave pelo escopo visível do principal e registrado como `Ruling:` (Tarefa 6, Passo 3). Evita números velhos e vazamento entre papéis. Alternativa descartada: cache de 60 s por card como no app atual.
4. **Rede para registros de pacotes: permitida, nunca ao Jira.** O executor pode baixar dependências do Maven/Gradle (OIDC/JWT/sessões do Ktor) e do npm quando não estiverem em cache, sem perguntar (Restrições globais; Tarefas 2 e 7). Acesso ao Jira continua regido só pela flag `JIRA_LIVE_ENABLED`, e nenhum teste automático acessa o Jira real ou um provedor OIDC real.
5. **Plataforma sem contêiner (26/10, adendo de 27/10): o produto é um pacote (`installDist` + build do frontend) rodando como um processo, com SQLite em arquivo.** Substitui a imagem endurecida do plano de 25/10. Serviço e backup: Plano 12; implantação real: Plano 13. Alternativa descartada: qualquer contêiner (a empresa bloqueia Docker).
6. **Desempenho medido sobre SQLite em arquivo, com escrita única e WAL**, incluindo leitura concorrente e escrita durante leitura (Tarefa 6). O pool de conexões só aumenta se a medição mostrar que ele é o gargalo, e isso é registrado como `Ruling:`.
7. **Endereço de escuta do painel: pergunta ao usuário na Tarefa 7** (padrão `127.0.0.1` por `PAINEL_HOST`, recomendado; ou só firewall; ou deixar ao Plano 13). Ainda sem resposta.
