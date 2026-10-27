# Fase 13: Implantação real e entrada em produção — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Para o ClineSR, isso equivale à seção 4 de `docs/guia-execucao-outra-ia.md`. **Reescrito em 27/10/2026** para a plataforma SQLite embutido, sem Docker, sem PostgreSQL, sem Kubernetes e sem registro de imagens (`docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`, que vence qualquer trecho antigo).

**Objetivo (Goal):** Levar o painel de `127.0.0.1` com dados de demonstração para uma máquina da empresa (Windows 11 ou Linux): o **pacote** do painel (`./gradlew installDist` + build do frontend) versionado, publicado como artefato de release do GitHub (zip com checksum SHA-256), instalado como **serviço do sistema operacional** com conta sem privilégio, com a porta publicada só em `127.0.0.1` atrás do proxy reverso da empresa com TLS, `DASHBOARD_DEV_MODE` desligado (o painel então exige o login SSO/OIDC no provedor escolhido), segredos em arquivo com permissão restrita (ou cofre da empresa), atualização e reversão ensaiadas, homologação e fumaça pós-implantação. A entrada em produção acontece **com a flag do Jira desligada** (`false`); ligá-la em produção é decisão do usuário e abre o Plano 14.

**Arquitetura (Architecture):** O plano separa o que não depende da infraestrutura do que depende dela.
- **Código com testes (offline):** estratégia de extração do username por perfil de provedor (atrás do `OidcAuthenticator` do Plano 8), confiança nos cabeçalhos `X-Forwarded-*` só vindos do proxy, escuta do servidor restrita ao endereço configurado, guardas de produção que derrubam a subida, versão do pacote em `/api/meta`.
- **Pacote e scripts (testados de forma estática e por ensaio local):** montagem do zip e do checksum, job de publicação no CI, instalação como serviço (Windows: NSSM ou Agendador de Tarefas; Linux: systemd), atualização e reversão por troca de pasta de versão, fumaça.
- **Runbook verificável:** decisões com a infraestrutura, criação da conta e dos segredos, primeira subida, fumaça, produção. Cada etapa tem um "como conferir" objetivo.
- **Divisão em PR e execução na máquina:** as Tarefas 1 a 10 e 13 (código, scripts e documentos) formam **um PR**. A criação da tag e do release (T7, Passo 5), a homologação (T11, a partir do Passo 5) e a produção (T12) só acontecem **depois do merge, que o usuário manda**; o registro delas vai num PR curto de documentação (`docs/implantacao-registro`).

Nenhum teste automático fala com provedor OIDC real, proxy real, GitHub real ou Jira: emissores falsos por `MockEngine`, hosts `.invalid`, documentos e scripts lidos como texto/YAML, banco SQLite em arquivo temporário.

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1, SQLite embutido + Flyway 11, Gradle `application` (`installDist`) e tarefa `Zip`, Ktor `MockEngine`; PowerShell 5.1/7 (principal) e `sh` nos scripts; GitHub Actions (JDK 21, Node 22, pnpm; fluxo do Plano 9, sem Docker); NSSM ou Agendador de Tarefas (Windows) e systemd (Linux). Na máquina de destino basta JRE/JDK 21; Node, pnpm e Gradle ficam só na máquina que monta o pacote.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-13-implantacao-real.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.3, 4.6 "Produção × desenvolvimento" e "Ambiente", 9 e 11; onde falar de contêiner, vale o adendo). Lacuna de origem: `docs/superpowers/cobertura-do-projeto.md`, C5. **Cuidados do PC corporativo (proxy, certificado, política do PowerShell):** `docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md` (Fase 0).

**Convenção de comandos.** O executor está no Windows 11 (PowerShell); cada comando traz o equivalente `sh`. Testes do backend rodam a partir de `app\backend` (`.\gradlew.bat test --tests '*X*'`; sh: `./gradlew test --tests '*X*'`). Suíte completa do backend: `.\gradlew.bat test`. Frontend, a partir de `app\frontend`: `pnpm test` e `pnpm build`. Teste do hook de proteção: Windows `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1`; sh `sh .claude/hooks/test-guard.sh`. Scripts desta fase em PowerShell são chamados com `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <script>` (a política do PC é tratada no Plano 0).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere na `main` que os Planos 8, 9, 11 e 12 **na versão reescrita para SQLite sem Docker** foram mesclados e que os nomes abaixo existem. Se algum plano ainda falar de contêiner, imagem ou PostgreSQL, é defeito do plano: parar e perguntar. Se um nome mudou, use o nome da `main`, atualize só os nomes deste plano e registre `Ruling:` no arquivo de progresso (`docs/superpowers/progresso/2026-10-25-plano-13-implantacao-real.md`).

**Conferido no código em 27/10/2026 (já existe):**

| Nome ou capacidade | Onde |
|---|---|
| `main()`: lê `PAINEL_ENV_FILE` (ou `.\.env`), `ArquivoEnv.combinarComFlagSoDoArquivo`, `Settings.fromEnv`, `AppContext.load`, `prepararDemonstracao`, `montarAgendador`; **`embeddedServer(Netty, port = 8080)` sem `host`** (escuta em todas as interfaces) | `Application.kt` |
| `Application.module(settingsIgnorado, context, authenticator, agendador)`; `MetaResposta(devMode, liveEnabled, zone, banner)`; `GET /api/health` e `GET /api/meta` públicos | `Application.kt` |
| `Settings` (campos `dashboardDevMode`, `dashboardDemoData`, `databasePath`, `configDir`, `staticDir`), `Settings.fromEnv`, `Settings.mascararUrl` | `config/Settings.kt` |
| `ArquivoEnv` (a flag do Jira vale só no arquivo de variáveis; variável de terminal ou do sistema é ignorada) | `config/ArquivoEnv.kt` |
| `Database.urlDeArquivo`, `Database.connect`, `Database.migrate` (Flyway em `classpath:db/migration`, hoje só `V1__base.sql`; sem `cleanDisabled` explícito) | `db/Database.kt` |
| `TravaDeProcesso` (um painel por arquivo de banco, `<banco>.lock`), `AppContext.close()` (para o agendador e fecha o pool) | `db/`, `AppContext.kt` |
| Em produção `AppContext.load` exige, em `PAINEL_CONFIG_DIR`: `papeis.yaml` (com GERENTE vigente), `calendario.yaml`, `status_map.yaml`, `vinculos.yaml`, `labels_atraso.yaml`, `auditoria.yaml`; `jira_campos.yaml` e `cards/` só com a flag ligada | `AppContext.kt` |
| `normalizarUsername`, `principalFor`, `DevAuthenticator`, `installAccessControl`, `requireRole`; ids reservados (`dev`, `system`, `admin`, `root`, `anonymous`) | `security/` |
| `rootProject.name = "painel"`, plugin `application` (`mainClass = br.sidia.painel.ApplicationKt`), `version = "0.1.0"`: `installDist` gera `build/install/painel/bin/painel` e `painel.bat` | `app/backend/build.gradle.kts`, `settings.gradle.kts` |
| `app/iniciar.ps1` e `app/iniciar.sh` (só desenvolvimento: `gradlew run` a partir de `app/`) | `app/` |
| `BancoEmArquivo`, `testContextComBanco(settings, banco)`, `TestPaths` (`configDir`, `mainSrcDir`) | `src/test/kotlin/br/sidia/painel/` |
| Cliente de `GET /api/meta` (`fetchMeta`, tipo `Meta`) e `Shell.tsx` (faixa de desenvolvimento) | `app/frontend/src/api/client.ts`, `shell/` |

**A confirmar (vêm dos planos reescritos):**

| Nome ou capacidade | Origem |
|---|---|
| `OidcSettings`, `OidcAuthenticator`, `rotasDeLogin` (`/auth/login`, `/auth/callback`, `/auth/logout`), cookie `painel_sessao`, variáveis `OIDC_*` e `SESSION_SECRET` (≥ 32 bytes em base64), `producaoSemOidcNaoSobe` | Plano 8, T2 |
| `ROTAS_PUBLICAS`, `InventarioDeRotasTest.inventarioConhecido` | Plano 8, T1 |
| Guia `docs/implantacao.md` e a verificação do pacote de produção (se o Plano 8 já criou um script de empacotamento, a T6 deste plano o **estende** em vez de criar outro: `Ruling:`) | Plano 8, T7 |
| Workflow `.github/workflows/ci.yml` (JDK 21 e Node 22/pnpm, sem Docker; testes do backend, frontend, `test-guard.sh`, varredura de dados pessoais) e o teste estático `CiWorkflowTest`; nomes dos jobs de teste | Plano 9, T2 |
| `CabecalhosDeSeguranca` (CSP, `X-Frame-Options`, `Referrer-Policy`, HSTS fora do dev mode), `ProtecaoCsrf` com `DASHBOARD_ORIGEM_PUBLICA` (`https://` obrigatório fora do dev mode), `ErroDto`, 401 tratado na SPA | Plano 9, T3 a T5 |
| Trava do DPO: `LgpdSettings`, `TravaDpo`, variáveis `DASHBOARD_DPO_ACEITE` e `DASHBOARD_DPO_ACEITE_REF` (ausente = bloqueada) | Plano 11, T1 |
| Readiness (`/api/health/pronto`), validador de configuração (`painel validar-config`), logs JSON sem segredos, métricas em porta interna (se o conector existir, também deve escutar só no loopback: T4), alertas | Plano 12, T1 a T5 |
| Backup do SQLite (`VACUUM INTO` ou API de backup online) para pasta fora do repositório, teste de restauração, rotação de segredos, runbooks diário e de incidentes | Plano 12, T7 a T11 (confirmar nomes e caminhos) |
| `DASHBOARD_DEMO_DATA` recusado fora do dev mode (já em `Settings.fromEnv`) | Plano 4A, T10 |

## Restrições globais (Global Constraints)

- **Jira:** a única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Este plano não acessa o Jira. A produção sobe com `JIRA_LIVE_ENABLED=false`; **só o usuário** liga a flag em produção (Tarefa 12). Nenhum texto, script ou modelo deste plano liga a flag.
- **Nenhum teste automático acessa provedor OIDC real, proxy real, GitHub real ou Jira**, mesmo com a flag ligada pelo usuário. Emissores falsos por `MockEngine` com chaves RSA geradas no teste; hosts sempre `.invalid` (`https://sso.exemplo.invalid`, `https://painel.exemplo.invalid`).
- **Nenhum host interno, IP, usuário real, nome de conta de serviço real, caminho de cofre, client id ou segredo real entra no repositório.** Os documentos usam marcadores (`<URL_DO_PROVEDOR>`, `<HOST_DE_HOMOLOGACAO>`) e contas fictícias (`.\svc-painel`, `ana.silva`); os valores reais ficam no arquivo de variáveis do servidor ou no cofre. O teste `implantacaoSemHostRealNemSegredo` (T13) varre `docs/implantacao.md`, `docs/implantacao/`, `deploy/` e `app/scripts/`.
- **Arquivos `.env` reais não são citados nem editados.** O arquivo de variáveis de produção chama-se `painel.env`, existe só no servidor e nunca no repositório nem no pacote; o repositório só traz o modelo `deploy/modelos/painel.env.exemplo` e `app/.env.example`.
- **Rede permitida:** espelhos de pacotes (Maven/Gradle, npm) para montar o pacote, como no Plano 8; GitHub (`gh`, tag, release) só nas etapas autorizadas da T7. Acesso ao ambiente da empresa (servidor, proxy, provedor OIDC) **só nas etapas de runbook**, depois do ponto de parada que as autoriza.
- **Efeito fora do repositório** (criar tag e release, criar conta de serviço, instalar serviço, mudar firewall, pedir proxy e certificado à infraestrutura, subir serviço) só depois de "Ponto de parada: perguntar ao usuário" respondido com "sim".
- Os papéis vêm **sempre** do `RoleDirectory` vigente, nunca de claims. O username vem de uma claim configurada e precisa ser **igual ao username do Jira em minúsculas** (o `id` de `papeis.yaml`); ids reservados nunca ganham papel.
- Configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `jira_campos.yaml`, `status_map.yaml`, `calendario.yaml`, `vinculos.yaml`) nunca entram no git nem no pacote; ficam na pasta de dados do servidor, fora do pacote, com leitura somente para a conta do serviço.
- Flyway só avança e `cleanDisabled` fica `true` fora dos testes. **Só um painel por arquivo de banco** (`TravaDeProcesso`): atualização e reversão sempre param o serviço antes de trocar o pacote. Reversão = voltar o pacote anterior; se a versão nova trouxe migração sem volta, restaurar o backup feito **antes** de atualizar.
- Variáveis novas começam com `DASHBOARD_` ou `OIDC_` (nunca `JIRA_`). Commits em Conventional Commits pt-BR (`feat(seguranca): ...`, `build(implantacao): ...`, `docs(implantacao): ...`). Comentários em pt-BR. Scripts PowerShell com `Set-StrictMode -Version Latest` e `$ErrorActionPreference = 'Stop'`; scripts `sh` com `set -eu`. Nenhum script recebe senha por argumento nem a imprime.
- Proibido (adendo, seção 4): Dockerfile, compose, imagem, registro de contêiner, Kubernetes, Testcontainers, PostgreSQL, H2. Se uma tarefa pedir qualquer um, não execute a parte e registre `Ruling:`.

## Foco da revisão (Review Focus)

1. **Username do provedor diferente do username do Jira** (UPN `ana.silva@empresa.invalid`, `DOMINIO\ana.silva`, maiúsculas, id reservado `admin`): a pessoa entra sem papel (403) ou, pior, casa com outra pessoa → `entraUpnParteLocal`, `adfsContaWindows`, `estrategiaNaoCasaComOutraPessoa`, `idReservadoNaoViraPapel` (T2).
2. **`X-Forwarded-Proto`/`-For`/`-Host` forjado** por cliente que não passou pelo proxy, ou `X-Forwarded-For` com entrada forjada pelo cliente e acrescentada pelo proxy: o painel acredita em HTTPS falso, monta `redirect_uri` com host do atacante ou grava IP falso no `access_log` → `forwardedDeOrigemNaoConfiavelIgnorado`, `xffForjadoPeloClienteNaoVence` (T3).
3. **Produção subindo insegura:** modo de desenvolvimento, demonstração, emissor `http`, porta aberta a todas as interfaces, caminho relativo que cria um banco vazio no lugar errado, Flyway `clean` habilitado → `GuardasDeProducao` e `flywayCleanDesligado` (T4).
4. **Rota pública de metadados vazando informação** (commit, caminho, host) ou quebrando o cliente → `metaSemCommitNemCaminho`, `metaMantemCamposAnteriores` (T5).
5. **Pacote levando config real, `painel.env`, banco ou segredo** → `verificarPacoteRecusaConfigReal` e a prova negativa (T6); **conta do serviço com privilégio ou arquivo de segredos legível por todos** → `windowsNaoUsaContaPrivilegiada`, `unitSystemdEndurecida`, `aclDoArquivoDeVariaveis` (T8, T9).
6. **Atualização que perde dados ou sobrescreve a versão em uso:** trocar o pacote sem parar o serviço, sem backup antes da troca, ou reversão que sobe o pacote anterior sobre um esquema incompatível → ordem verificada em `atualizarParaAntesDeTrocar`, `ReversaoComBackupTest` e `ensaio-troca` (T10).
7. **Fumaça que passa sem provar nada** (aceitar qualquer 200, não conferir a versão nem o endereço de escuta) → `fumacaConfereCadaItem` (T11).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  security/ExtratorDeUsername.kt        # estratégia por perfil de provedor (T2)
  security/ProxyConfiavel.kt            # X-Forwarded-* só do proxy (T3)
  config/GuardasDeProducao.kt           # recusa de subida em produção (T4)
  VersaoDoPacote.kt                     # lê versao.properties (T5)
  config/Settings.kt, Application.kt, db/Database.kt, AppContext.kt (modificados)
app/backend/build.gradle.kts            # versão, versao.properties, montarPacote (T5, T6)
app/backend/src/test/kotlin/br/sidia/painel/
  TestPaths.kt (repoDir)
  security/{ExtratorDeUsernameTest.kt, ProxyConfiavelTest.kt, OidcTest.kt (casos novos)}
  config/{GuardasDeProducaoTest.kt, SettingsTest.kt (casos novos)}, VersaoDoPacoteTest.kt, MetaTest.kt (atualizado)
  implantacao/{ImplantacaoDocTest.kt, PacoteDeImplantacaoTest.kt, WorkflowPublicacaoTest.kt, ServicoTest.kt,
               ScriptsDeImplantacaoTest.kt, ReversaoComBackupTest.kt}
app/frontend/src/{api/client.ts, shell/Shell.tsx, shell/Shell.test.tsx, i18n/pt.json, i18n/en.json}
app/scripts/{empacotar.ps1, empacotar.sh, verificar-pacote.ps1, verificar-pacote.sh}
app/.env.example
.github/workflows/ci.yml                # job publicar-pacote (Plano 9)
deploy/windows/{comum.ps1, instalar-servico.ps1, desinstalar-servico.ps1, atualizar.ps1, reverter.ps1,
                verificar-instalacao.ps1, verificar-logs.ps1, gerar-session-secret.ps1, fumaca.ps1,
                ensaio-troca.ps1, ensaio-local.ps1}
deploy/linux/{comum.sh, painel.service, instalar.sh, atualizar.sh, reverter.sh, verificar-instalacao.sh,
              verificar-logs.sh, gerar-session-secret.sh, fumaca.sh, ensaio-troca.sh, ensaio-local.sh}
deploy/modelos/painel.env.exemplo
docs/implantacao.md, docs/implantacao/{decisoes.md, servico-windows.md, servico-linux.md, proxy-e-tls.md,
  segredos.md, atualizacao.md, reversao.md, publicacao.md, runbook-primeira-subida.md}
.gitignore, HANDOFF.md, docs/superpowers/plans/2026-10-24-indice-dos-planos.md
```

---

### Tarefa 1: Decisões com a infraestrutura (ponto de parada)

**Arquivos:** Criar `docs/implantacao/decisoes.md` (só marcadores e escolhas, sem valores reais); Modificar `app/backend/build.gradle.kts` (propriedade de teste `painel.repoDir` = raiz do repositório, por caminho absoluto, como as já existentes; se o Plano 8 ou 9 já criou uma equivalente, usar a dele e registrar `Ruling:`) e `src/test/kotlin/br/sidia/painel/TestPaths.kt` (`val repoDir: Path`); Teste `implantacao/ImplantacaoDocTest.kt` (`decisoesSemHostReal`, `decisoesTemCadaPergunta`).

**Interfaces:** Produz o registro das decisões que as Tarefas 2 a 12 consomem: `perfilOidc` (`CLAIM_DIRETO` | `PARTE_LOCAL_DO_EMAIL` | `CONTA_WINDOWS`), `maquina` (`WINDOWS` | `LINUX`), `gerenciador` (`NSSM` | `TAREFA` | `SYSTEMD`), `proxy` (`LOCAL` | `REMOTO`, com marcador do IP/faixa), `segredos` (`ARQUIVO` | `COFRE`), `entrega` (`COPIA` | `GH_RELEASE`).

- [ ] **Passo 1: Teste que falha:** `decisoesTemCadaPergunta` (o documento tem uma seção para cada uma das cinco perguntas abaixo, com a resposta e a data); `decisoesSemHostReal` (nenhuma URL fora de `.invalid`, nenhum IP fora de `127.0.0.1`, `::1` e das faixas de documentação `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` e `10.0.0.0/24`, nenhum `customfield`, nenhum e-mail fora de `.invalid`).
- [ ] **Passo 2: Ponto de parada: perguntar ao usuário** (uma pergunta por assunto, opções na ordem abaixo, a primeira marcada "(Recomendada)", cada uma com o porquê e a consequência):
  1. **Provedor OIDC.**
     - *Keycloak da empresa (Recomendada, se existir):* o `preferred_username` costuma ser o username de rede, igual ao do Jira Data Center; configuração direta; consequência: estratégia `CLAIM_DIRETO`.
     - *Microsoft Entra ID (Azure AD):* comum em empresas com Microsoft 365; o `preferred_username` é um UPN com domínio; consequência: estratégia `PARTE_LOCAL_DO_EMAIL` e conferência de que a parte local é igual ao username do Jira para todas as pessoas do `papeis.yaml`.
     - *ADFS (Windows Server):* claim `winaccountname` ou `upn`; consequência: estratégia `CONTA_WINDOWS` (`DOMINIO\usuario` vira `usuario`).
     - *Outro provedor OIDC padrão (Okta, Google Workspace, Ping):* consequência: estratégia escolhida pela claim que o provedor emitir; teste de forma igual aos outros.
  2. **Máquina e gerenciador do serviço.**
     - *Windows com NSSM (Recomendada se a empresa aprovar o NSSM):* binário único, reinício automático, log com rotação e parada graciosa (Ctrl+C chega ao JVM e fecha banco e agendador); consequência: a infraestrutura precisa aprovar e hospedar o `nssm.exe`; executar o ramo `Nssm` da T8.
     - *Windows com Agendador de Tarefas na inicialização:* sem software extra; consequência: parada brusca (o processo é encerrado, o SQLite em WAL tolera, a trava de arquivo some sozinha) e log sem rotação nativa; executar o ramo `Tarefa` da T8. Observação: `New-Service` sozinho **não** serve para o `painel.bat`, pois um script não responde ao gerenciador de serviços; só serve para registrar um executável de serviço (NSSM ou WinSW).
     - *Linux com systemd:* endurecimento nativo da unidade; consequência: executar o ramo Linux da T8.
  3. **Proxy reverso e TLS (decisão com a infraestrutura).**
     - *Proxy da empresa na mesma máquina, painel só em `127.0.0.1` (Recomendada):* a porta nunca fica exposta; consequência: `DASHBOARD_PROXY_CONFIAVEL=127.0.0.1/32,::1/128`.
     - *Proxy em outra máquina:* o painel escuta num único endereço interno (nunca em todas as interfaces) e o firewall do SO só aceita o IP do proxy; consequência: `DASHBOARD_BIND` e `DASHBOARD_PROXY_CONFIAVEL` com os marcadores do IP; regra de firewall pedida à infraestrutura; registrar `Ruling:`.
     - *TLS até o painel (sem proxy):* o painel não terminaria o TLS hoje (exigiria suporte a keystore no código); consequência: não recomendada; vira tarefa nova em outro plano.
     - Em qualquer opção a infraestrutura entrega: certificado, nome público (`<HOST_PUBLICO>`) e o proxy configurado conforme `docs/implantacao/proxy-e-tls.md` (T8).
  4. **Segredos.**
     - *Arquivo `painel.env` com permissão restrita (Recomendada):* poucos segredos (`SESSION_SECRET` e `OIDC_CLIENT_SECRET`; tokens do Jira só se o usuário ligar a flag); sem dependência externa; consequência: rotação manual, documentada no Plano 12.
     - *Cofre da empresa:* rotação e auditoria prontas; consequência: a infraestrutura fornece um script de pré-início que grava o arquivo com a mesma permissão a cada subida (a T9 documenta o contrato).
  5. **Como o pacote chega à máquina.**
     - *Cópia manual do zip e do `.sha256` baixados em outra máquina (Recomendada):* nenhum token do GitHub no servidor; consequência: passo manual a cada versão.
     - *Download direto do release com `gh` na máquina:* automatiza a atualização; consequência: servidor com acesso ao GitHub e token só de leitura guardado nele.
- [ ] **Passo 3: Registrar** cada resposta e a data no documento (com marcadores no lugar de hosts) e no arquivo de progresso. Sem resposta para o provedor, seguir com as Tarefas 3 a 7 (não dependem dele) e voltar à T2 quando houver; sem resposta sobre o gerenciador, implementar os dois ramos Windows e o Linux da T8.
- [ ] **Passo 4: Rodar** `.\gradlew.bat test --tests '*ImplantacaoDocTest*'` (sh: `./gradlew test --tests '*ImplantacaoDocTest*'`) → PASSA. **Passo 5: Commit** `docs(implantacao): decisões de provedor, máquina, proxy, segredos e entrega`.

---

### Tarefa 2: Username pelo perfil do provedor

**Arquivos:** Criar `security/ExtratorDeUsername.kt`; Modificar `security/OidcSettings.kt`, `security/Oidc.kt` (Plano 8), `config/Settings.kt`, `app/.env.example`; Teste `security/ExtratorDeUsernameTest.kt`, `security/OidcTest.kt` (casos novos), `config/SettingsTest.kt` (casos novos).

**Interfaces:**
- Consome: `normalizarUsername` (`security/Usernames.kt`), `principalFor`, `RoleDirectory`, `OidcAuthenticator` e `OidcSettings` (Plano 8).
- Produz:
  ```kotlin
  enum class EstrategiaDeUsername { CLAIM_DIRETO, PARTE_LOCAL_DO_EMAIL, CONTA_WINDOWS }
  object ExtratorDeUsername {
      // Devolve o username normalizado (normalizarUsername) ou null se a claim faltar, ficar vazia ou fugir do formato.
      fun extrair(claims: Map<String, Any?>, claim: String, estrategia: EstrategiaDeUsername): String?
  }
  // OidcSettings ganha: val estrategiaUsername: EstrategiaDeUsername = EstrategiaDeUsername.CLAIM_DIRETO
  ```
- Variável `OIDC_USERNAME_ESTRATEGIA` (`claim_direto`, `parte_local_do_email`, `conta_windows`; ausente ou vazia = `claim_direto`; inválida derruba a subida citando só o nome da variável).
- `PARTE_LOCAL_DO_EMAIL`: exige exatamente um `@`; a parte local passa por `normalizarUsername`. `CONTA_WINDOWS`: exige exatamente um `\`; usa o que vem depois. Formato diferente do esperado: `null`, 401 com `access_log` (`motivo = USERNAME_ILEGIVEL`), nunca "adivinhar".
- Uma única estratégia por instalação; não há tentativa em cascata (evita casar com outra pessoa).

- [ ] **Passo 1: Testes que falham** (emissores falsos por `MockEngine`, um por formato de provedor, todos em `sso.exemplo.invalid`):
  - `keycloakPreferredUsername`: `preferred_username = "Ana.Silva"` → `ana.silva`, papéis do diretório.
  - `entraUpnParteLocal`: `preferred_username = "Ana.Silva@Empresa.INVALID"` com `parte_local_do_email` → `ana.silva`.
  - `adfsContaWindows`: `winaccountname = "EMPRESA\\ana.silva"` com `conta_windows` → `ana.silva`.
  - `estrategiaNaoCasaComOutraPessoa`: `ana.silva@outro.invalid` com `claim_direto` → username `ana.silva@outro.invalid`, fora do diretório → 403; nunca `ana.silva`.
  - `idReservadoNaoViraPapel`: claim `Admin` → `admin`, sem papel → 403 (nunca o gerente implícito do modo de desenvolvimento).
  - `doisArrobasIlegivel401`, `duasBarrasIlegivel401`, `claimAusente401`, `estrategiaInvalidaNaoSobe` (a mensagem cita `OIDC_USERNAME_ESTRATEGIA` e não o valor).
  - `claimDePapelIgnorada` (Plano 8) continua verde com as três estratégias.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ExtratorDeUsernameTest*' --tests '*OidcTest*' --tests '*SettingsTest*'` (sh: `./gradlew test --tests ...`) → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, e a suíte completa (`.\gradlew.bat test`). **Passo 5: Commit** `feat(seguranca): username pelo perfil do provedor OIDC`.

---

### Tarefa 3: Proxy confiável e HTTPS atrás do proxy

**Arquivos:** Criar `security/ProxyConfiavel.kt`; Modificar `Application.kt` (instala o plugin), `config/Settings.kt`, `security/Oidc.kt` (o `redirect_uri` vem **só** de `OIDC_REDIRECT_URI`, nunca de cabeçalho), `app/.env.example`; Teste `security/ProxyConfiavelTest.kt`, `config/SettingsTest.kt` (casos novos).

**Interfaces:**
- Produz:
  ```kotlin
  class FaixaCidr private constructor(/* rede e prefixo */) {
      fun contem(ip: String): Boolean
      companion object { fun parse(texto: String): FaixaCidr }   // IPv4 e IPv6; inválido: IllegalArgumentException sem o valor
  }
  data class OrigemDaChamada(val ip: String, val https: Boolean, val host: String)
  fun resolverOrigem(ipDaConexao: String, cabecalho: (String) -> String?, confiaveis: List<FaixaCidr>, hostDaRequisicao: String): OrigemDaChamada
  fun Application.instalarProxyConfiavel(
      confiaveis: List<FaixaCidr>,
      origemDaConexao: (ApplicationCall) -> String = { it.request.local.remoteAddress },   // ponto de injeção para testes
  )
  ```
- `Settings.proxyConfiavel: List<FaixaCidr>` lida de `DASHBOARD_PROXY_CONFIAVEL` (lista de CIDR separada por vírgula; vazia = nenhum proxy confiável; CIDR inválido derruba a subida citando só o nome da variável). A obrigatoriedade em produção é da T4.
- Regra: os cabeçalhos `X-Forwarded-Proto`, `-For` e `-Host` só valem quando o IP da **conexão** está numa faixa confiável; caso contrário são ignorados. `X-Forwarded-For` é lido **da direita para a esquerda**, pulando endereços confiáveis; o primeiro não confiável é o cliente (o cliente pode ter forjado entradas à esquerda; o proxy acrescenta o endereço real à direita). O `access_log` grava o IP já resolvido.
- O `redirect_uri` do login nunca é montado a partir de `Host`/`X-Forwarded-*`; o cookie `painel_sessao` continua `Secure`, `HttpOnly`, `SameSite=Lax`.
- Como ajustar `call.request.origin` (esquema, host, IP remoto): conferir na versão 3.3.1 do Ktor o recurso `mutableOriginConnectionPoint` (usado pelo plugin `XForwardedHeaders`) e em qual módulo ele está; se exigir `ktor-server-forwarded-header`, declarar a dependência em `libs.versions.toml` e `build.gradle.kts`. A lista de confiança é código próprio, porque o plugin do Ktor não tem lista de CIDR.

- [ ] **Passo 1: Testes que falham:**
  - `forwardedDeOrigemNaoConfiavelIgnorado`: conexão `203.0.113.9` fora da faixa, `X-Forwarded-Proto: https`, `X-Forwarded-Host: atacante.invalid` → o painel vê `http`, o host original e o IP `203.0.113.9`; o login não monta `redirect_uri` com `atacante.invalid`.
  - `forwardedDoProxyAceito`: conexão `10.0.0.5` em `10.0.0.0/24`, `X-Forwarded-Proto: https`, `X-Forwarded-For: 203.0.113.9` → `https` e IP `203.0.113.9` no `access_log`.
  - `proxyLocalLoopback`: conexão `127.0.0.1` e `::1` com `127.0.0.1/32,::1/128` → cabeçalhos aceitos.
  - `xffForjadoPeloClienteNaoVence`: conexão `10.0.0.5` (proxy), `X-Forwarded-For: 198.51.100.7, 203.0.113.50` → IP `203.0.113.50`.
  - `cadeiaDeProxiesConfiaveis`: conexão `10.0.0.5`, `X-Forwarded-For: 203.0.113.9, 10.0.0.7` → IP `203.0.113.9`.
  - `redirectUriNuncaVemDeCabecalho`: com proxy confiável e `X-Forwarded-Host: atacante.invalid`, o `Location` do `/auth/login` começa pelo `OIDC_ISSUER` configurado e leva `redirect_uri` igual a `OIDC_REDIRECT_URI`.
  - `cookieSecureAtrasDoProxy` (resposta do callback com `Secure`, `HttpOnly`, `SameSite=Lax`).
  - `cidrInvalidoNaoSobe` (mensagem só com o nome da variável), `faixaVaziaNaoConfiaEmNinguem`, `devModeNaoExigeProxy`.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ProxyConfiavelTest*' --tests '*SettingsTest*'` (sh: `./gradlew test --tests ...`) → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(seguranca): cabeçalhos do proxy só de origem confiável`.

---

### Tarefa 4: Escuta restrita e guardas de produção

**Arquivos:** Criar `config/GuardasDeProducao.kt`; Modificar `config/Settings.kt` (`dashboardBind`, `dashboardPort`), `Application.kt` (a função que monta o servidor usa host e porta das configurações; as guardas rodam em `main()` logo depois de `Settings.fromEnv` e **antes** de `AppContext.load`, para falhar antes de abrir o banco e a trava), `db/Database.kt` (Flyway com `cleanDisabled(true)`, extraído para `Database.flyway(ds)`), `app/.env.example`; Teste `config/GuardasDeProducaoTest.kt`, `config/SettingsTest.kt` (casos novos), `db/DatabaseTest.kt` (ou o arquivo de teste de banco vizinho).

**Interfaces:**
- Consome: `Settings`, o mapa efetivo de variáveis de `main()` (`ArquivoEnv.combinarComFlagSoDoArquivo(...)`), `FaixaCidr` (T3).
- Produz:
  ```kotlin
  // Settings ganha: dashboardBind: String = "127.0.0.1" (DASHBOARD_BIND, IP literal), dashboardPort: Int = 8080 (DASHBOARD_PORT, 1..65535)
  class ViolacaoDeProducao(val codigos: List<String>) : IllegalStateException("Subida recusada em produção: " + codigos.joinToString(", "))
  object GuardasDeProducao { fun verificar(s: Settings, env: Map<String, String>) }   // não faz nada em modo de desenvolvimento
  object Database { fun flyway(ds: DataSource): Flyway /* locations classpath:db/migration, cleanDisabled(true) */ ; fun migrate(ds: DataSource) }
  ```
- Códigos (nunca o valor): `DEMO_EM_PRODUCAO` (`DASHBOARD_DEMO_DATA` ligado; reforça `Settings.fromEnv`); `OIDC_ISSUER_SEM_HTTPS`; `REDIRECT_URI_SEM_HTTPS`; `ORIGEM_PUBLICA_AUSENTE` (`DASHBOARD_ORIGEM_PUBLICA` do Plano 9 ausente ou sem `https://`); `REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA`; `SESSION_SECRET_CURTO` (ausente, base64 inválido ou menos de 32 bytes); `PROXY_NAO_CONFIGURADO` (`DASHBOARD_PROXY_CONFIAVEL` vazia); `ESCUTA_ABERTA_A_TODAS` (`0.0.0.0`, `::` ou vazio); `CAMINHO_RELATIVO` (`DATABASE_PATH`, `PAINEL_CONFIG_DIR` ou `PAINEL_STATIC_DIR` não absolutos: um serviço iniciado na pasta errada criaria um banco vazio sem avisar); `TRAVA_DPO_NAO_DECLARADA` (`DASHBOARD_DPO_ACEITE` ausente; declarar `false` é válido).
- O padrão da escuta passa a ser `127.0.0.1` em **todos** os modos (hoje o Netty escuta em todas as interfaces). O conector de métricas do Plano 12, se existir, usa o mesmo endereço (ou outro loopback).
- As guardas leem as variáveis de OIDC, sessão, origem pública, proxy e DPO direto do mapa `env`, para não depender do formato interno que o Plano 8 deu a `OidcSettings`.

- [ ] **Passo 1: Testes que falham** (caminhos absolutos montados a partir de `System.getProperty("java.io.tmpdir")`, para valerem em Windows e Linux):
  - `producaoComDemoNaoSobe`, `issuerHttpNaoSobe`, `redirectUriHttpNaoSobe`, `origemPublicaAusenteNaoSobe`, `redirectUriForaDaOrigemPublicaNaoSobe`, `segredoCurtoNaoSobe` (31 bytes), `segredoBase64InvalidoNaoSobe`, `proxyAusenteNaoSobe`, `escutaAbertaNaoSobe` (`0.0.0.0` e `::`), `caminhoRelativoNaoSobe` (cada uma das três variáveis), `travaDpoNaoDeclaradaNaoSobe`.
  - `producaoValidaSobe`: todas as variáveis com valores fictícios válidos (`https://sso.exemplo.invalid`, `https://painel.exemplo.invalid`, `127.0.0.1/32`, segredo de 48 bytes em base64, `DASHBOARD_DPO_ACEITE=false`).
  - `listaTodasAsViolacoes` (duas violações no mesmo cenário aparecem juntas, em ordem estável), `mensagemSemValor` (a exceção não contém o segredo, a URL nem o caminho), `devModeIgnoraGuardas`.
  - `escutaPadraoEhLoopback` (`Settings()` → `127.0.0.1` e `8080`), `bindInvalidoNaoSobe` (nome de host em vez de IP), `portaForaDaFaixaNaoSobe` (`0` e `70000`; mensagens só com o nome da variável).
  - `flywayCleanDesligado`: `Database.flyway(ds).configuration.isCleanDisabled == true` e `flyway.clean()` lança `FlywayException`.
  - `servidorEscutaSoNoLoopback`: extrair de `main()` uma função que devolve o servidor ainda parado (`servidorDoPainel(context, agendador)`); subir com `DASHBOARD_BIND=127.0.0.1` e uma porta livre (obtida com `ServerSocket(0)`), conferir em `engine.resolvedConnectors()` (suspend; conferir o nome na versão do Ktor) que o host é `127.0.0.1`, e parar.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*GuardasDeProducaoTest*' --tests '*SettingsTest*' --tests '*DatabaseTest*'` (sh: `./gradlew test --tests ...`) → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. Conferir que `app\iniciar.ps1` (modo desenvolvimento) continua subindo em `http://127.0.0.1:8080`. **Passo 5: Commit** `feat(config): escuta em loopback e guardas que impedem subida insegura em produção`.

---

### Tarefa 5: Versão do pacote em `/api/meta`

**Arquivos:** Criar `VersaoDoPacote.kt`; Modificar `app/backend/build.gradle.kts` (versão, geração de `versao.properties`), `Application.kt` (`MetaResposta`, parâmetro `versao` do `module` com valor padrão no fim da lista, linha de log na subida), `app/frontend/src/api/client.ts` (tipo `Meta`), `app/frontend/src/shell/Shell.tsx` (rodapé com o rótulo), `app/frontend/src/i18n/pt.json` e `en.json` (chave nova, padrão dos vizinhos); Teste `VersaoDoPacoteTest.kt`, `MetaTest.kt` (atualizado), `app/frontend/src/shell/Shell.test.tsx` (caso novo).

**Interfaces:**
- Produz:
  ```kotlin
  data class Versao(val versao: String, val commit: String, val construidaEm: String) {
      val rotulo: String   // "v2026.11.1"; "dev" quando a versão é "0.0.0-dev"
  }
  object VersaoDoPacote { val atual: Versao }   // lê o recurso versao.properties do classpath; ausente: Versao("0.0.0-dev", "desconhecido", "")
  @Serializable data class MetaResposta(val devMode: Boolean, val liveEnabled: Boolean, val zone: String, val banner: String?,
                                        val versao: String, val rotuloDeVersao: String)
  ```
- Gradle: `-Pversao=2026.11.1` (formato `AAAA.MM.N`, mês 01 a 12, `N` ≥ 1; qualquer outro valor falha o build; sem a propriedade, `0.0.0-dev`), `-Pcommit=<curto>` (padrão: `git rev-parse --short=7 HEAD`; sem git, `desconhecido`). A tarefa `gerarVersaoProperties` grava `versao`, `commit` e `construidaEm` (ISO-8601 UTC) em `build/generated/versao/versao.properties`, que entra como pasta de recursos do `main`.
- `/api/meta` é **pública**: expõe só `versao` e `rotuloDeVersao`. O commit e a data de build ficam no recurso, no `VERSAO.txt` do pacote e numa linha de log da subida, nunca na resposta. Os campos antigos continuam, na mesma ordem. Isto substitui a decisão de 25/10 de manter `/api/meta` intacta (pedido da reescrita de 27/10).

- [ ] **Passo 1: Testes que falham:**
  - `VersaoDoPacoteTest`: `semRecursoVolta0_0_0Dev`, `leOsTresCampos`, `rotuloComV`, `rotuloDevParaBuildLocal`.
  - `MetaTest` (injeta `Versao("2026.11.1", "abc1234", "2026-11-03T12:00:00Z")` no `module`): `metaTemVersao` (`{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null,"versao":"2026.11.1","rotuloDeVersao":"v2026.11.1"}`), `metaSemCommitNemCaminho` (o corpo não contém `abc1234`, a data de build, `\`, nem `/`), `metaMantemCamposAnteriores` (os quatro testes existentes passam a esperar o JSON com os dois campos novos no fim), `metaContinuaPublico` (sem sessão → 200).
  - `buildValidaFormatoDaVersao`: o `build.gradle.kts` contém a expressão regular `^\d{4}\.(0[1-9]|1[0-2])\.[1-9]\d*$` (teste estático) e não aceita `-Pversao=2026.13.1` (conferido no Passo 4).
  - `Shell.test.tsx`: `rodapeMostraRotuloDeVersao` (com `/api/meta` simulado trazendo `rotuloDeVersao: "v2026.11.1"` o texto aparece num `<footer>`); sem o campo (resposta antiga) o rodapé some, sem erro.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*VersaoDoPacoteTest*' --tests '*MetaTest*'` (sh: `./gradlew test --tests ...`) e, em `app\frontend`, `pnpm test` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA; suítes completas; `.\gradlew.bat installDist -Pversao=2026.11.1 -Pcommit=abc1234` e conferir `build\install\painel\lib\painel-2026.11.1.jar`; `.\gradlew.bat installDist -Pversao=2026.13.1` → FALHA com mensagem clara. **Passo 5: Commit** `feat(build): versão do pacote em /api/meta e no rodapé`.

---

### Tarefa 6: Empacotamento (zip e SHA-256)

**Arquivos:** Modificar `app/backend/build.gradle.kts` (tarefa `montarPacote`; `applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")`); Criar `app/scripts/empacotar.ps1`, `empacotar.sh`, `verificar-pacote.ps1`, `verificar-pacote.sh`; Teste `implantacao/PacoteDeImplantacaoTest.kt`.

**Interfaces:**
- `montarPacote` (tipo `Zip`, depende de `installDist`): gera `app/backend/build/pacote/painel-<versao>.zip` com a pasta raiz `painel-<versao>/` contendo:
  - `bin/painel`, `bin/painel.bat`, `lib/*.jar` (do `installDist`);
  - `frontend/dist/**` (de `app/frontend/dist`; a tarefa **falha** se `index.html` não existir);
  - `config-exemplo/*.exemplo.yaml`, `config-exemplo/*.demo.yaml` e `config-exemplo/cards/*.card.yaml` (de `app/config`; **nada além disso**);
  - `implantacao/windows/`, `implantacao/linux/`, `implantacao/modelos/` (de `deploy/`) e `docs/` (de `docs/implantacao.md` e `docs/implantacao/`);
  - `VERSAO.txt` (versão, commit curto e data de build) e `LEIA-ME.txt` (três linhas apontando para `docs/implantacao.md`).
  - Zip reprodutível: `isPreserveFileTimestamps = false` e `isReproducibleFileOrder = true` (mesma entrada, mesmo SHA-256).
- `app/scripts/empacotar.ps1 -Versao '2026.11.1'` (sh: `sh app/scripts/empacotar.sh 2026.11.1`): em `app\frontend` roda `pnpm install --frozen-lockfile` e `pnpm build`; em `app\backend` roda `.\gradlew.bat installDist montarPacote "-Pversao=<versao>" "-Pcommit=<curto>"`; grava `painel-<versao>.zip.sha256` ao lado do zip no formato `<sha256-minúsculo>  painel-<versao>.zip` (dois espaços, aceito por `sha256sum -c`); chama `verificar-pacote`. Saída final: `PACOTE_OK painel-<versao>.zip <sha256>`.
- `verificar-pacote.ps1 -Zip <caminho>` (sh: `sh app/scripts/verificar-pacote.sh <zip>`): confere o SHA-256 contra o arquivo `.sha256`; exige as entradas `bin/painel`, `bin/painel.bat`, `lib/`, `frontend/dist/index.html`, `config-exemplo/papeis.exemplo.yaml`, `implantacao/windows/instalar-servico.ps1`, `implantacao/linux/painel.service`, `VERSAO.txt`; **recusa** qualquer entrada chamada `.env`, `painel.env`, `*.db`, `*.db-wal`, `*.db-shm`, `*.lock`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `status_map.yaml`, `jira_campos.yaml`, `calendario.yaml`, `vinculos.yaml`, `labels_atraso.yaml`, `auditoria.yaml`, `Filtros_Status_tool.txt`, ou pastas `legacy/`, `node_modules/`, `.git/`; tamanho máximo 200 MB. SHA-256: `Get-FileHash -Algorithm SHA256` no PowerShell; `sha256sum` (ou `shasum -a 256`) no sh.

- [ ] **Passo 1: Testes que falham** (estáticos, leem o `build.gradle.kts` e os scripts como texto): `montarPacoteSoIncluiModelos` (o bloco do `config-exemplo` usa `*.exemplo.yaml`, `*.demo.yaml` e `cards/*.card.yaml` e não cita nenhum nome real), `montarPacoteFalhaSemFrontend`, `zipReprodutivel`, `scriptsCalculamSha256`, `verificarPacoteRecusaConfigReal` (a lista de nomes proibidos do script contém todos os acima), `empacotarSemDocker`, `empacotarValidaFormatoDaVersao`.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*PacoteDeImplantacaoTest*'` → FALHA. **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA. Depois, de verdade: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File app\scripts\empacotar.ps1 -Versao 2026.11.1` (sh: `sh app/scripts/empacotar.sh 2026.11.1`) → `PACOTE_OK`. Abrir o zip e conferir `VERSAO.txt`.
- [ ] **Passo 5: Prova negativa** (em pasta temporária, nunca no repositório): copiar o zip para `$env:TEMP`, acrescentar um arquivo `config-exemplo\papeis.yaml` e rodar `verificar-pacote.ps1` → deve falhar citando o nome; repetir com um `.env` falso. Registrar no progresso.
- [ ] **Passo 6: Commit** `build(implantacao): pacote zip com SHA-256 e verificação`.

---

### Tarefa 7: Publicação do pacote pelo CI

**Arquivos:** Modificar `.github/workflows/ci.yml` (Plano 9; job `publicar-pacote`); Criar `docs/implantacao/publicacao.md`; Teste `implantacao/WorkflowPublicacaoTest.kt` (lê o YAML do workflow com `kaml`; o `CiWorkflowTest` do Plano 9 continua verde).

**Interfaces:**
- Job `publicar-pacote`: gatilho `push` de tag `v*` (e `if: startsWith(github.ref, 'refs/tags/v')`); `needs` com os jobs de teste do Plano 9 (nomes a confirmar); `runs-on: ubuntu-latest`; JDK 21 (Temurin), Node 22 e pnpm como no Plano 9; passos: validar a tag contra `^v\d{4}\.(0[1-9]|1[0-2])\.[1-9]\d*$`; `sh app/scripts/empacotar.sh "${GITHUB_REF_NAME#v}"`; criar o release com `gh release create "$GITHUB_REF_NAME" <zip> <zip>.sha256 --title "Painel de Squads $GITHUB_REF_NAME" --notes-file docs/implantacao/publicacao.md --verify-tag`; escrever o SHA-256 em `$GITHUB_STEP_SUMMARY`.
- `permissions: contents: read` no topo do workflow e `contents: write` **só** neste job; credencial só `${{ secrets.GITHUB_TOKEN }}` (nenhum segredo novo).
- `docs/implantacao/publicacao.md`: como numerar (`AAAA.MM.N`), como criar a tag (`git tag v2026.11.1; git push origin v2026.11.1`, só após o "sim" do passo 5 e depois do merge), como baixar e verificar o pacote (`gh release download v2026.11.1 --pattern 'painel-*'` na máquina do operador; verificação: `(Get-FileHash .\painel-2026.11.1.zip -Algorithm SHA256).Hash.ToLower()` igual ao primeiro campo do `.sha256`; sh: `sha256sum -c painel-2026.11.1.zip.sha256`), e a regra de que toda PR que cria migração declara na descrição se o pacote anterior roda sobre o esquema novo (usada pela T10).

- [ ] **Passo 1: Testes que falham:** `publicaSoEmTag`, `publicaDepoisDosTestes`, `permissoesMinimas` (`contents: read` no topo; `contents: write` só no job de publicação), `semSegredoLiteral` (só `secrets.GITHUB_TOKEN`), `validaFormatoDaTag`, `usaOsScriptsDoRepositorio`, `anexaZipEChecksum`, `imprimeSha256NoResumo`, `semDocker` (o workflow não cita `docker`, `ghcr`, `registry`).
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*WorkflowPublicacaoTest*' --tests '*CiWorkflowTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário** (só depois do merge da fase, pois a tag é efeito fora do repositório). Opções:
  - *Criar a tag `v2026.MM.1` e deixar o CI publicar o release (Recomendada):* o pacote fica com o SHA-256 registrado e a T11 usa exatamente esse zip.
  - *Só montar o pacote localmente (T6) e entregá-lo por cópia:* sem release no GitHub; o job fica inerte; a rastreabilidade fica no `VERSAO.txt` e no `.sha256` guardados com o zip.
  - *Não publicar agora:* homologação só com o ensaio local da T11.
  Registrar a resposta.
- [ ] **Passo 6: Commit** `build(ci): publicação do pacote por tag com SHA-256`.

---

### Tarefa 8: Serviço do sistema operacional (Windows e Linux)

**Arquivos:** Criar `deploy/windows/{comum.ps1, instalar-servico.ps1, desinstalar-servico.ps1, verificar-instalacao.ps1}`, `deploy/linux/{comum.sh, painel.service, instalar.sh, verificar-instalacao.sh}`, `deploy/modelos/painel.env.exemplo`, `docs/implantacao/{servico-windows.md, servico-linux.md, proxy-e-tls.md}`; Teste `implantacao/ServicoTest.kt`.

**Nota:** esta tarefa substitui as antigas alternativas de VM com compose e de Kubernetes. O ramo executado depende da decisão 2 da T1; sem resposta, entregue os dois ramos do Windows e o Linux.

**Interfaces:**
- Disposição no servidor (Windows; Linux entre parênteses):
  - `C:\Painel\app\<versao>\` (`/opt/painel/<versao>/`): pacote extraído, somente leitura para a conta do serviço; `C:\Painel\app\atual` é uma junção (`/opt/painel/atual`, link simbólico) para a versão ativa.
  - `C:\Painel\dados\` (`/var/lib/painel/`): pasta de trabalho do serviço; contém `data\painel.db`, `painel.env` e `config\` (Linux: `/etc/painel/painel.env`, `/etc/painel/config/`, banco em `/var/lib/painel/painel.db`).
  - `C:\Painel\backups\` (`/var/backups/painel/`), `C:\Painel\logs\` (Linux: `journalctl -u painel`), `C:\Painel\recebidos\` (zip e `.sha256` recebidos).
- `instalar-servico.ps1 -Raiz 'C:\Painel' -Pacote 'C:\Painel\recebidos\painel-2026.11.1.zip' -Gerenciador Nssm|Tarefa -Conta '.\svc-painel' [-ContaGerenciada] [-Nssm 'C:\Ferramentas\nssm.exe'] [-JavaHome '<pasta do JDK 21>'] [-Iniciar]`: exige PowerShell elevado (`#Requires -RunAsAdministrator`) e suporta `-WhatIf`; verifica o SHA-256, extrai em `app\<versao>`, cria `atual`, a pasta de dados, os logs e as ACLs, registra o serviço **sem iniciá-lo** (a menos de `-Iniciar`, que exige `painel.env` e `config\` prontos). A senha da conta é pedida por `Get-Credential` (nunca por argumento nem gravada); com `-ContaGerenciada` (conta gerenciada de grupo, sem senha) não se pede senha. Saída: `OK <passo>` por etapa e `SERVICO_INSTALADO`.
  - ACLs (SID em vez de nome, para valer em Windows em qualquer idioma): `icacls "C:\Painel\app" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "<conta>:(OI)(CI)RX"`; `icacls "C:\Painel\dados" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "<conta>:(OI)(CI)M"`; `icacls "C:\Painel\dados\config" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "<conta>:(OI)(CI)RX"`; `icacls "C:\Painel\logs" /inheritance:r /grant:r "*S-1-5-32-544:(OI)(CI)F" "<conta>:(OI)(CI)M"`. O `painel.env` é tratado na T9.
  - Ramo `Nssm`: `nssm install Painel "C:\Painel\app\atual\bin\painel.bat"`; `nssm set Painel AppDirectory "C:\Painel\dados"`; `nssm set Painel AppEnvironmentExtra "PAINEL_ENV_FILE=C:\Painel\dados\painel.env" "JAVA_HOME=<JavaHome>" "JAVA_OPTS=-Xmx512m"`; `nssm set Painel AppStdout "C:\Painel\logs\painel.log"`; `nssm set Painel AppStderr "C:\Painel\logs\painel.log"`; `nssm set Painel AppRotateFiles 1`; `nssm set Painel AppRotateBytes 10485760`; `nssm set Painel Start SERVICE_AUTO_START`; `nssm set Painel AppExit Default Restart`; `nssm set Painel AppRestartDelay 10000`; `nssm set Painel AppStopMethodConsole 30000`; `nssm set Painel ObjectName "<conta>"` com a credencial. O `-Xmx512m` é valor inicial: ajustar depois de medir (Planos 8 e 12).
  - Ramo `Tarefa`: `Register-ScheduledTask` com gatilho `AtStartup`, ação `cmd.exe /c "cd /d C:\Painel\dados && set PAINEL_ENV_FILE=C:\Painel\dados\painel.env&& set JAVA_HOME=<JavaHome>&& C:\Painel\app\atual\bin\painel.bat >> C:\Painel\logs\painel.log 2>&1"`, principal a conta informada com `-LogonType Password`, `-RunLevel Limited`, configurações `-RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew`. Parada com `Stop-ScheduledTask` (brusca; documentar).
- `deploy/linux/painel.service` (unidade): `User=painel`, `Group=painel`, `WorkingDirectory=/var/lib/painel`, `Environment=PAINEL_ENV_FILE=/etc/painel/painel.env`, `Environment=JAVA_OPTS=-Xmx512m`, `ExecStart=/opt/painel/atual/bin/painel`, `Restart=on-failure`, `RestartSec=10`, `TimeoutStopSec=30`, `NoNewPrivileges=yes`, `ProtectSystem=strict`, `ProtectHome=yes`, `PrivateTmp=yes`, `PrivateDevices=yes`, `ProtectKernelTunables=yes`, `ProtectControlGroups=yes`, `RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX`, `CapabilityBoundingSet=` (vazio), `UMask=0077`, `ReadWritePaths=/var/lib/painel` (acrescentar a pasta de backup se o backup do Plano 12 rodar dentro do processo), `ReadOnlyPaths=/etc/painel /opt/painel`, `[Install] WantedBy=multi-user.target`. `instalar.sh <zip>` (como root; `sudo sh deploy/linux/instalar.sh /caminho/painel-2026.11.1.zip`): cria o usuário `painel` (`useradd --system --home-dir /var/lib/painel --shell /usr/sbin/nologin painel`), as pastas (`install -d -o painel -g painel -m 750 /var/lib/painel`; `install -d -o root -g painel -m 750 /etc/painel`), extrai, cria o link `atual`, instala a unidade e roda `systemctl daemon-reload` (sem iniciar).
- `deploy/modelos/painel.env.exemplo`: `DASHBOARD_DEV_MODE=false`, `DASHBOARD_DEMO_DATA=false`, `JIRA_LIVE_ENABLED=false`, `DASHBOARD_BIND=127.0.0.1`, `DASHBOARD_PORT=8080`, `DASHBOARD_ORIGEM_PUBLICA=https://painel.exemplo.invalid`, `DASHBOARD_PROXY_CONFIAVEL=127.0.0.1/32,::1/128`, `DASHBOARD_DPO_ACEITE=false`, `OIDC_ISSUER=https://sso.exemplo.invalid/realms/exemplo`, `OIDC_CLIENT_ID=painel`, `OIDC_CLIENT_SECRET=` (vazio), `OIDC_REDIRECT_URI=https://painel.exemplo.invalid/auth/callback`, `OIDC_USERNAME_ESTRATEGIA=claim_direto`, `SESSION_SECRET=` (vazio), `DATABASE_PATH`, `PAINEL_CONFIG_DIR`, `PAINEL_STATIC_DIR` (caminhos absolutos do Windows, com a versão Linux em comentário).
- `verificar-instalacao.ps1 -Raiz 'C:\Painel' [-Porta 8080] [-EnderecoEsperado 127.0.0.1]` (sh: `sh deploy/linux/verificar-instalacao.sh`): confere (1) serviço ou tarefa existe, em inicialização automática; (2) roda com a conta informada e **não** como `LocalSystem`; (3) a conta não pertence ao grupo `*S-1-5-32-544` (`Get-LocalGroupMember -SID S-1-5-32-544`); (4) `atual` aponta para uma pasta de versão com `bin\painel.bat`; (5) `dados\config` tem `papeis.yaml`, `calendario.yaml`, `status_map.yaml`, `vinculos.yaml`, `labels_atraso.yaml` e `auditoria.yaml`; (6) a porta escuta **só** em `-EnderecoEsperado` (`Get-NetTCPConnection -State Listen -LocalPort 8080`; Linux `ss -ltnH 'sport = :8080'`); (7) `java -version` principal 21 no `JAVA_HOME` do serviço. Uma linha `OK`/`FALHOU <nome>` por item; final `INSTALACAO_OK`.
- `docs/implantacao/proxy-e-tls.md`: o que pedir à infraestrutura. O proxy termina o TLS no `<HOST_PUBLICO>` e encaminha para `http://127.0.0.1:8080` (ou o endereço da decisão 3); **substitui** (não acrescenta) `X-Forwarded-For` pelo endereço real do cliente e envia `X-Forwarded-Proto: https` e `X-Forwarded-Host`; repassa tudo sob `/` (SPA, `/api`, `/auth`); não faz cache de `/api` nem de `/auth`; verificação de saúde em `GET /api/health` (pública, sem login); tempo limite de leitura de pelo menos 60 s; HSTS vem do painel (Plano 9).

- [ ] **Passo 1: Testes que falham** (estáticos):
  - `unitSystemdEndurecida`: lê `painel.service` como INI e confere as chaves acima; nenhuma linha `User=root`; nenhum `SESSION_SECRET`, `TOKEN` ou senha literal; `ExecStart` aponta para `/opt/painel/atual/bin/painel`.
  - `windowsNaoUsaContaPrivilegiada`: `instalar-servico.ps1` não contém `LocalSystem`, `Administrator` como conta, nem `-Verb RunAs`; exige `-Conta`; tem `SupportsShouldProcess` e `#Requires -RunAsAdministrator`; usa `*S-1-5-32-544`; nenhum parâmetro chamado `Senha` ou `Password`.
  - `windowsTemOsDoisGerenciadores` (`Nssm` e `Tarefa`), `nssmParaGracioso` (`AppStopMethodConsole`), `reinicioAutomatico` (`AppExit Default Restart` e `RestartCount`).
  - `modeloDeProducaoEhSeguro`: `painel.env.exemplo` tem `DASHBOARD_DEV_MODE=false`, `DASHBOARD_DEMO_DATA=false`, `JIRA_LIVE_ENABLED=false`, `DASHBOARD_BIND=127.0.0.1`, `DASHBOARD_DPO_ACEITE=false`, só hosts `.invalid`, e `SESSION_SECRET=` e `OIDC_CLIENT_SECRET=` vazios.
  - `verificarInstalacaoConfereCadaItem` (os sete itens), `semDocker`, `proxyDocConfereCabecalhos` (`proxy-e-tls.md` cita `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, `/api/health`).
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ServicoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Ensaio local sem efeito no sistema:** `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\instalar-servico.ps1 -Raiz $env:TEMP\ensaio-painel -Pacote <zip da T6> -Gerenciador Nssm -Conta .\svc-painel -WhatIf` → lista os passos sem executar; sh (Linux): `sh deploy/linux/instalar.sh --simular <zip>` imprime os comandos sem executar. Registrar no progresso.
- [ ] **Passo 6: Commit** `build(implantacao): serviço do sistema operacional com conta sem privilégio`.

---

### Tarefa 9: Segredos e arquivo de variáveis protegido

**Arquivos:** Criar `docs/implantacao/segredos.md`, `deploy/windows/{gerar-session-secret.ps1, verificar-logs.ps1}`, `deploy/linux/{gerar-session-secret.sh, verificar-logs.sh}`; Modificar `app/.env.example` (toda variável nova deste plano, sem valor, comentada em pt-BR), `.gitignore` (acrescentar `**/painel.env`), `deploy/windows/verificar-instalacao.ps1` e `deploy/linux/verificar-instalacao.sh` (item de permissão do arquivo); Teste `implantacao/ImplantacaoDocTest.kt` (casos novos), `implantacao/ServicoTest.kt` (caso novo).

**Interfaces:**
- `segredos.md` lista cada segredo (`SESSION_SECRET`, `OIDC_CLIENT_SECRET`; `JIRA_SAMSUNG_TOKEN` e `JIRA_SIDIA_TOKEN` só quando o usuário decidir ligar a flag, Plano 14; **não existe senha de banco**), **onde** fica (arquivo `painel.env` ou cofre, conforme a T1), quem cria, como gerar, como injetar e como rotacionar (aponta para o runbook de rotação do Plano 12; confirmar o caminho).
- Permissão do `painel.env`: Windows `icacls "C:\Painel\dados\painel.env" /inheritance:r /grant:r "*S-1-5-32-544:F" "<conta>:R"` (a conta só lê); Linux `chown root:painel /etc/painel/painel.env; chmod 640 /etc/painel/painel.env` (o processo lê, não altera). Nenhuma entrada para `Everyone`, `BUILTIN\Users` ou `Authenticated Users` (SIDs `*S-1-1-0`, `*S-1-5-32-545`, `*S-1-5-11`).
- `gerar-session-secret.ps1 -Arquivo <painel.env> [-Rotacionar]` (sh: `sh deploy/linux/gerar-session-secret.sh /etc/painel/painel.env`): gera 48 bytes de `System.Security.Cryptography.RandomNumberGenerator` (Linux: `openssl rand -base64 48`), grava a linha `SESSION_SECRET=` direto no arquivo **sem imprimir o valor**, recusa se já houver valor (a menos de `-Rotacionar`, que avisa que as sessões abertas serão encerradas, ou segue o procedimento do Plano 12 se ele permitir rotação sem derrubar sessões).
- `verificar-logs.ps1 -Raiz 'C:\Painel'` (sh: `sh deploy/linux/verificar-logs.sh`): para cada variável de `painel.env` cujo nome contém `SECRET`, `PASSWORD` ou `TOKEN` e tem valor, procura esse valor nos logs (`C:\Painel\logs\*.log`; Linux `journalctl -u painel --no-pager`) e imprime só `LOG_LIMPO` ou `LOG_COM_SEGREDO <NOME_DA_VARIAVEL>` (nunca o valor).
- Contrato do cofre (se a T1 escolheu cofre): a infraestrutura fornece um comando de pré-início (NSSM: `AppEvents`/`Start/Pre`; systemd: `ExecStartPre=`) que grava o `painel.env` com as permissões acima antes de cada subida; o painel não conhece o cofre.

- [ ] **Passo 1: Testes que falham:** `segredosCitaCadaVariavelSecreta` (lê `app/.env.example` e `deploy/modelos/painel.env.exemplo`; toda variável cujo nome contém `SECRET`, `PASSWORD` ou `TOKEN` aparece em `segredos.md`), `envExampleSemValor` (nenhuma dessas com valor), `modeloDeProducaoSemValorDeSegredo`, `segredosApontaRotacao`, `gitignoreCobrePainelEnv`, `aclDoArquivoDeVariaveis` (o script de instalação e a verificação citam os SIDs acima e `chmod 640`), `geradorNaoImprimeOSegredo` (o script não tem `Write-Host`, `echo` nem `Write-Output` com a variável do segredo), `verificarLogsNaoImprimeValor`.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ImplantacaoDocTest*' --tests '*ServicoTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Runbook (efeito fora do repositório, depois do "sim" do usuário):** copiar o modelo para `C:\Painel\dados\painel.env` (Linux `/etc/painel/painel.env`), ajustar os marcadores, rodar o gerador do `SESSION_SECRET`, colocar o `OIDC_CLIENT_SECRET` entregue pela infraestrutura (editor aberto como administrador; nunca em argumento de comando), aplicar as permissões. **Como conferir:** `verificar-instalacao` passa o item de permissão; o painel sobe sem `SESSION_SECRET_CURTO`; `verificar-logs` termina com `LOG_LIMPO`.
- [ ] **Passo 6: Commit** `docs(implantacao): segredos, permissão do arquivo de variáveis e verificação de logs`.

---

### Tarefa 10: Atualização e reversão ensaiadas

**Arquivos:** Criar `deploy/windows/{atualizar.ps1, reverter.ps1, ensaio-troca.ps1}`, `deploy/linux/{atualizar.sh, reverter.sh, ensaio-troca.sh}`, `docs/implantacao/{atualizacao.md, reversao.md}`; Modificar `deploy/windows/comum.ps1` e `deploy/linux/comum.sh` (funções compartilhadas); Teste `implantacao/ScriptsDeImplantacaoTest.kt`, `implantacao/ReversaoComBackupTest.kt`.

**Interfaces:**
- Funções comuns (`comum.ps1`; `comum.sh` equivalente): `Confirmar-Sha256 -Zip`, `Expandir-Pacote -Zip -Raiz` (extrai em `app\<versao>.tmp`, confere a estrutura e renomeia para `app\<versao>`; **recusa** se `app\<versao>` já existir), `Trocar-Atual -Raiz -Versao` (recria a junção/link `atual`), `Parar-Painel`, `Iniciar-Painel`, `Esperar-Saude -Url -TimeoutSeg`.
- `atualizar.ps1 -Raiz 'C:\Painel' -Pacote <zip> [-SemServico]` (sh: `sh deploy/linux/atualizar.sh /opt/painel <zip>`), nesta ordem: (1) SHA-256; (2) extrair em pasta nova (o pacote em uso fica intacto); (3) registrar a versão atual em `dados\versao-anterior.txt`; (4) **parar o serviço** e esperar o fim do processo (a trava `<banco>.lock` some); (5) **backup do banco** pelo mecanismo do Plano 12 (rotulado `pre-<versao>`); se falhar, religar a versão atual e abortar; (6) trocar `atual`; (7) iniciar e esperar `/api/health` (as migrações rodam na subida); (8) `fumaca`. Falha em (7) ou (8): imprime `FALHOU: execute reverter` com o comando exato, **sem reversão automática** (uma reversão automática depois de migração poderia piorar o estado). `-SemServico` pula (4), (5) e (7) e só serve ao ensaio.
- `reverter.ps1 -Raiz 'C:\Painel' [-RestaurarBanco] [-Backup <arquivo>]` (sh: `sh deploy/linux/reverter.sh /opt/painel [--restaurar-banco] [--backup <arquivo>]`): para o serviço; com `-RestaurarBanco`, guarda o banco atual ao lado (`painel.db.migrado-<data>`), apaga `-wal` e `-shm` e restaura o backup `pre-<versao>` pelo procedimento do Plano 12; aponta `atual` para a versão de `versao-anterior.txt`; inicia; fumaça. Regra: **`-RestaurarBanco` é obrigatório** quando a descrição da PR da versão nova declarou "migração sem volta" ou quando a versão anterior não sobe sobre o esquema novo; dados gravados depois do backup são perdidos (dizer isso em `reversao.md`).
- `ensaio-troca.ps1` (sh: `ensaio-troca.sh`): em pasta temporária (`New-TemporaryFile`/`mktemp -d`, removida num `finally`/`trap`), cria dois "pacotes" de mentira (`2026.11.1` e `2026.11.2`, só com `bin\painel.bat` de marcador), roda `atualizar -SemServico` e depois `reverter` e confere o destino de `atual` em cada etapa. Saída final `TROCA_OK` e `REVERSAO_OK`.
- `ReversaoComBackupTest` (Kotlin, SQLite em arquivo temporário com `BancoEmArquivo`): migra com `Database.migrate`, grava dados fictícios, faz o backup com `VACUUM INTO` (ou a classe de backup do Plano 12, se existir), aplica a "migração do pacote novo" com Flyway lendo uma pasta temporária (`filesystem:`) com `V9999__ensaio.sql` (cria a tabela `ensaio_futuro`; **a migração nunca existe no repositório**), restaura o backup por cópia de arquivo e confere os dados.

- [ ] **Passo 1: Testes que falham:**
  - `ScriptsDeImplantacaoTest`: `atualizarVerificaSha256AntesDeExtrair`, `atualizarParaAntesDeTrocar` (no texto do script, as posições seguem a ordem: parar < backup < trocar `atual` < iniciar < fumaça), `atualizarNaoSobrescrevePastaDeVersao`, `atualizarNaoRevertePorConta` (o script não chama `reverter`), `reverterCitaRestaurarBanco`, `reverterGuardaOBancoMigrado`, `ensaioUsaSoPastaTemporaria` (usa `TEMP`/`mktemp -d` e remove ao fim), `scriptsSemHostFixo`, `semDocker`.
  - `ReversaoComBackupTest`: `restauracaoVoltaOsDadosAnterioresAMigracao`, `historicoNaoTemAMigracaoDeEnsaio` (`flyway_schema_history` sem `9999`), `tabelaFuturaNaoExisteDepoisDeRestaurar`, `bancoRestauradoSobeComOPacoteAtual` (`Database.migrate` do pacote atual sobre o banco restaurado, sem erro).
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ScriptsDeImplantacaoTest*' --tests '*ReversaoComBackupTest*'` → FALHA. **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA, e `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\ensaio-troca.ps1` (sh: `sh deploy/linux/ensaio-troca.sh`) → `TROCA_OK` e `REVERSAO_OK`.
- [ ] **Passo 5: Descobrir e documentar o comportamento real:** num teste exploratório (descartável, fora do repositório), rodar `Database.migrate` do pacote atual sobre um banco que já tem a migração de ensaio e registrar no progresso se o Flyway aceita (o padrão do Flyway ignora migrações aplicadas e desconhecidas) ou recusa. `reversao.md` cita o resultado observado e reafirma a regra: o pacote anterior pode subir sobre um esquema novo, mas só funciona se a migração for compatível; na dúvida, restaurar o backup.
- [ ] **Passo 6: Commit** `build(implantacao): atualização com backup e reversão ensaiadas`.

---

### Tarefa 11: Homologação e fumaça pós-implantação

**Arquivos:** Criar `deploy/windows/{fumaca.ps1, ensaio-local.ps1}`, `deploy/linux/{fumaca.sh, ensaio-local.sh}`, `docs/implantacao/runbook-primeira-subida.md`; Teste `implantacao/ScriptsDeImplantacaoTest.kt` (casos novos).

**Interfaces:**
- `fumaca.ps1 -UrlBase 'http://127.0.0.1:8080' [-VersaoEsperada 2026.11.1] [-UrlPublica 'https://<HOST_PUBLICO>'] [-EmissorEsperado 'https://sso.exemplo.invalid']` (sh: `sh deploy/linux/fumaca.sh <url-base> [versao] [url-publica]`): usa só `Invoke-WebRequest -UseBasicParsing` (PowerShell) ou `curl` (sh), sai com código diferente de 0 na primeira falha, imprime `OK <nome>`, `FALHOU <nome>` ou `PULADO_SEM_EMISSOR <nome>` e termina com `FUMACA_OK`. Confere:
  1. `GET /api/health` → 200;
  2. `GET /api/health/pronto` → 200 (Plano 12; pulado, com aviso, se o plano usou outro nome: `Ruling:`);
  3. `GET /api/meta` → `devMode` falso, `banner` nulo e `versao` igual a `-VersaoEsperada`;
  4. `GET /api/cards/tempo-em-status` sem sessão → 401 (em PowerShell 5.1, ler o código no `catch` da exceção);
  5. `GET /auth/login` → 302 (com `-MaximumRedirection 0`) para o emissor configurado, com `code_challenge` e `state` na query; nas verificações que dependem do emissor real, `PULADO_SEM_EMISSOR`;
  6. cabeçalhos do Plano 9 na resposta da página inicial: `Content-Security-Policy`, `X-Frame-Options`, `Referrer-Policy`; `Strict-Transport-Security` quando `-UrlPublica` for `https`;
  7. a porta escuta só no endereço esperado (reaproveita o item da `verificar-instalacao`) quando rodada na própria máquina;
  8. **fora do loopback:** `-UrlPublica` responde 200 em `/api/health` e `Location` do login usa `https`.
- `ensaio-local.ps1 -Zip <pacote da T6>` (sh: `ensaio-local.sh <zip>`): numa pasta temporária, extrai o pacote, cria `config` copiando os modelos `*.exemplo.yaml` para os nomes reais **e** escrevendo um `papeis.yaml` fictício (formato de `papeis.exemplo.yaml`, uma pessoa `ana.silva` como GERENTE sem data de fim), monta um `painel.env` de teste com `DASHBOARD_DEV_MODE=false`, `JIRA_LIVE_ENABLED=false`, `DASHBOARD_PORT=18080`, `DASHBOARD_BIND=127.0.0.1`, emissor `https://sso.exemplo.invalid`, `SESSION_SECRET` gerado na hora (nunca impresso) e caminhos absolutos da pasta temporária, sobe `bin\painel.bat` em segundo plano, espera `/api/health`, roda a fumaça (itens dependentes do emissor saem `PULADO_SEM_EMISSOR`) e para o processo e apaga a pasta num `finally`. Saída final `ENSAIO_LOCAL_OK`. Se o Plano 8 exigir o emissor alcançável já na subida, o ensaio usa um servidor falso local e registra `Ruling:`.
- `runbook-primeira-subida.md` (homologação; a seção de produção é da T12): pré-requisitos (T1 a T10; JDK 21 instalado; conta do serviço criada), pacote recebido e SHA-256 conferido, `instalar-servico.ps1`, configs reais em `dados\config` (`papeis.yaml` real; os demais a partir de `config-exemplo\`, ajustados: `status_map.yaml` com os status reais), `painel.env` pronto (T9), `painel validar-config --dir C:\Painel\dados\config` do Plano 12 (confirmar o nome) → `VALIDACAO OK`, `verificar-instalacao`, iniciar (`Start-Service Painel`; sh: `systemctl start painel`), `fumaca`, login de uma pessoa de cada papel (USUARIO, SQUAD_LEADER, COORDENADOR, GERENTE) conferindo o menu do papel; Integridade e Auditoria aparecem bloqueadas pela trava do DPO (Plano 11) enquanto o aceite não existir. Cada etapa tem "Como conferir".

- [ ] **Passo 1: Testes que falham:** `fumacaConfereCadaItem` (o script contém cada verificação acima), `fumacaSemHostFixo` (a URL vem só de argumento), `fumacaSaiNaPrimeiraFalha`, `ensaioLocalUsaPortaPropria` (`DASHBOARD_PORT=18080`), `ensaioLocalNaoImprimeSegredo`, `ensaioLocalApagaAPasta`, `runbookTemComoConferirEmCadaEtapa` (cada título de etapa seguido de "Como conferir").
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ScriptsDeImplantacaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, e `powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy\windows\ensaio-local.ps1 -Zip <zip da T6>` (sh: `sh deploy/linux/ensaio-local.sh <zip>`) → `ENSAIO_LOCAL_OK`.
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário** antes de instalar no ambiente de homologação da empresa (depois do merge e da tag). Opções:
  - *Instalar em homologação agora (Recomendada):* valida provedor, proxy, TLS e conta de serviço reais sem dados do Jira e sem a flag.
  - *Esperar a infraestrutura liberar a máquina, o proxy e o certificado (registrar a data prevista):* nenhuma mudança fora do repositório.
  - *Ir direto para produção (não recomendado):* sem ensaio no ambiente real.
- [ ] **Passo 6: Runbook (depois do "sim"):** executar o runbook; **como conferir:** `fumaca` termina com `FUMACA_OK` contra `http://127.0.0.1:8080` na própria máquina **e** contra `https://<HOST_DE_HOMOLOGACAO>`; `verificar-instalacao` termina com `INSTALACAO_OK`; registrar data, versão e SHA-256 do pacote no `HANDOFF.md` (sem host).
- [ ] **Passo 7: Commit** `build(implantacao): fumaça, ensaio local e runbook da primeira subida`.

---

### Tarefa 12: Entrada em produção com a flag desligada

**Arquivos:** Modificar `docs/implantacao/runbook-primeira-subida.md` (seção de produção), `HANDOFF.md`. (Executada depois do merge, em PR curto de documentação.)

**Interfaces:** Mesmo runbook da T11 no servidor de produção, com o **mesmo pacote** (mesmo SHA-256) homologado.

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** antes da subida em produção. Opções:
  - *Subir em produção com `JIRA_LIVE_ENABLED=false` (Recomendada):* o painel fica no ar com login real e sem dados, pronto para o Plano 14.
  - *Adiar (registrar o motivo e a data):* nada muda fora do repositório.
- [ ] **Passo 2: Runbook (depois do "sim"):** instalar o mesmo zip, configs reais, `painel.env` de produção, `verificar-instalacao` → `INSTALACAO_OK`, iniciar, `fumaca -UrlBase http://127.0.0.1:8080 -UrlPublica https://<HOST_PUBLICO> -VersaoEsperada <versao>` → `FUMACA_OK`; conferir o backup agendado do Plano 12 (primeira execução registrada), os alertas (a sincronização aparece "desligada pela flag", sem alerta falso) e `verificar-logs` → `LOG_LIMPO`.
- [ ] **Passo 3: Ponto de parada: perguntar ao usuário** sobre a flag do Jira em produção. Opções:
  - *Manter `false` e seguir o Plano 14 a partir do Passo 1 do checklist, com o usuário ligando a flag quando quiser (Recomendada):* a flag só vale no `painel.env` do servidor, e o painel precisa ser reiniciado depois de qualquer mudança.
  - *O usuário liga agora, seguindo `docs/jira/checklist-primeira-conexao.md` e o runbook da sincronização:* a ligação é dele; a IA só acompanha.
  - *Manter desligada por tempo indeterminado (registrar o motivo).*
  **A IA executora nunca liga a flag.**
- [ ] **Passo 4: Registrar** data, versão, SHA-256 e a resposta no `HANDOFF.md` e no progresso. **Passo 5: Commit** `docs(implantacao): entrada em produção registrada`.

---

### Tarefa 13: Documentação de implantação final

**Arquivos:** Modificar `docs/implantacao.md` (índice que aponta para `docs/implantacao/*`, `deploy/` e `app/scripts/`), `app/README.md` (seção de produção: escuta, `DASHBOARD_BIND`, `DASHBOARD_PORT`, pacote, remover a frase de que o login "é previsto em plano posterior" quando o Plano 8 estiver na `main`), `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado da Fase 13: pacote e serviço, sem VM nem Kubernetes), `HANDOFF.md`; Teste `implantacao/ImplantacaoDocTest.kt` (casos novos).

- [ ] **Passo 1: Testes que falham:** `implantacaoSemHostRealNemSegredo` (varre `docs/implantacao.md`, `docs/implantacao/`, `deploy/` e `app/scripts/`: só hosts `.invalid` ou marcadores `<...>`; nenhum IP fora de `127.0.0.1`, `::1`, `0.0.0.0` (nos exemplos de recusa) e das faixas de documentação; nenhum token; nenhum caminho de usuário real), `implantacaoCitaCadaDocumento` (o índice cita todos os documentos e pastas), `implantacaoSemPlataformaAntiga` (nenhum texto cita contêiner, imagem, compose, Kubernetes, PostgreSQL ou Testcontainers, salvo na frase que os proíbe), `documentoDeImplantacaoCitaBackupERestauracao` (Plano 8/12) continua verde.
- [ ] **Passo 2: Rodar** `.\gradlew.bat test --tests '*ImplantacaoDocTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA e suítes completas (backend e frontend), `pnpm build` e o teste do hook (Windows `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1`; sh `sh .claude/hooks/test-guard.sh`).
- [ ] **Passo 5: Commit** `docs(implantacao): guia de implantação final`.

---

## Autoavaliação

**Cobertura da spec:**

| Requisito | Tarefas |
|---|---|
| Spec 4.6: provedor OIDC real antes de qualquer uso com dados reais | T1, T2, T11 |
| Spec 4.6: painel atrás do proxy da empresa, porta só em loopback, TLS no proxy | T3, T4, T8, T11 |
| Spec 4.3: papel só do `RoleDirectory` | T2 (`claimDePapelIgnorada` e `idReservadoNaoViraPapel`) |
| Cobertura C5: versão, publicação do pacote, segredos, homologação, fumaça, reversão | T5, T6, T7, T9, T10, T11 |
| Spec 3 e 10: nenhuma requisição ao Jira com a flag `false` | T12 (produção sobe com `false`) |
| Spec 11: DPO antes do uso real | T4 (`TRAVA_DPO_NAO_DECLARADA`), T11 |
| Adendo, seção 5: pacote + serviço do SO com conta sem privilégio; backup antes de atualizar; reversão pelo pacote anterior | T6, T8, T10 |

**Consistência de tipos:** `EstrategiaDeUsername` (T2) entra em `OidcSettings` do Plano 8 sem mudar a assinatura de `OidcAuthenticator`; `FaixaCidr` (T3) é consumida por `Settings` e por `GuardasDeProducao` (T4, código `PROXY_NAO_CONFIGURADO`); `Versao` (T5) alimenta `MetaResposta`, o `VERSAO.txt` do pacote (T6) e a fumaça (`-VersaoEsperada`, T11); o zip e o `.sha256` da T6 são o que o job da T7, o instalador (T8), o atualizador (T10) e a fumaça (T11) consomem; a regra "um painel por arquivo de banco" (`TravaDeProcesso`) justifica parar o serviço antes de trocar `atual` (T10).

**Pendências deliberadas:** escolha do provedor, da máquina e do gerenciador, do proxy e TLS, dos segredos e da entrega (T1); criação da tag e do release (T7), instalação em homologação (T11) e em produção (T12); ligar a flag em produção (Plano 14).

**Contagem:** 13 tarefas, um PR para as Tarefas 1 a 10 e 13, mais um PR curto de registro para as etapas de runbook das Tarefas 7, 11 e 12.

## Decisões (27/10, valor padrão recomendado, reversível)

1. **Uma estratégia de username por instalação, sem cascata** (T2). Alternativa descartada: tentar várias claims em sequência.
2. **Cabeçalhos do proxy só de faixa confiável e `X-Forwarded-For` lido da direita** (T3). Alternativa descartada: confiar em `X-Forwarded-*` de qualquer origem.
3. **Versão em `/api/meta` (`versao` e `rotuloDeVersao`), sem commit nem data** (T5). A rota é pública; o commit fica no `VERSAO.txt` e no log. Substitui a decisão de 25/10 de manter `/api/meta` inalterada.
4. **Versão `AAAA.MM.N`, tag `vAAAA.MM.N`, pacote `painel-AAAA.MM.N.zip` com `.sha256`** (T5 a T7). Alternativa descartada: semver manual.
5. **Cada versão numa pasta e `atual` como junção/link** (T8, T10). A atualização e a reversão trocam o ponteiro; o pacote em uso nunca é sobrescrito.
6. **Escuta padrão em `127.0.0.1` e porta 8080 em todos os modos; produção recusa escuta em todas as interfaces** (T4). Alternativa descartada: depender só do firewall.
7. **Caminhos absolutos obrigatórios em produção** (T4). Evita banco vazio criado na pasta errada por um serviço mal configurado.
8. **Atualização sem reversão automática** (T10). Depois de migração, a decisão de restaurar o banco é humana.
9. **Reversão = pacote anterior; banco restaurado só quando a versão nova trouxe migração sem volta** (T10). O Flyway só avança; não há migração "para baixo".
10. **Segredos em `painel.env` com permissão restrita como padrão; cofre é opção** (T9). Poucos segredos e nenhuma senha de banco.
11. **Entrega por cópia do zip como padrão** (T1). Evita guardar token do GitHub no servidor.
12. **Release criado pelo CI com o `GITHUB_TOKEN` da própria execução, sem segredo novo** (T7).
13. **Produção sobe com `JIRA_LIVE_ENABLED=false`** (T12). Só o usuário liga a flag.

Decisões que **dependem do usuário** (pontos de parada): provedor OIDC, máquina e gerenciador do serviço, proxy e TLS, segredos e entrega do pacote (T1); criar a tag e o release (T7); instalar em homologação (T11) e em produção (T12); flag do Jira em produção (T12).
