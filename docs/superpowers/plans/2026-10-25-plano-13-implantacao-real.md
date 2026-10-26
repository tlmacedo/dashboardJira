# Fase 13: Implantação real e entrada em produção — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Para o ClineSR, isso equivale à seção 4 de `docs/guia-execucao-outra-ia.md`.

**Objetivo (Goal):** Levar o painel de `127.0.0.1` com dados de demonstração para um ambiente real da empresa, atrás do proxy, com HTTPS, login SSO/OIDC no provedor escolhido, imagem versionada e publicada num registro, segredos num cofre, homologação com `DASHBOARD_DEV_MODE=false`, testes de fumaça e um caminho de reversão ensaiado. A entrada em produção acontece **com `JIRA_LIVE_ENABLED=false`**; ligar a flag em produção é decisão do usuário e abre o Plano 14.

**Arquitetura (Architecture):** O plano separa o que não depende da infraestrutura do que depende dela.
- **Código com testes (offline):** estratégia de extração do username por perfil de provedor (atrás do `OidcAuthenticator` do Plano 8), confiança nos cabeçalhos `X-Forwarded-*` só vindos do proxy, guardas de produção que derrubam a subida, rota de versão, rótulos OCI na imagem, manifestos do destino escolhido (VM ou k8s) com testes estáticos, scripts de fumaça e de ensaio de reversão.
- **Runbook verificável:** decisões com a infraestrutura, criação dos segredos no cofre, publicação da imagem, primeira subida, fumaça, reversão. Cada etapa tem um "como conferir" objetivo.

Nenhum teste automático fala com provedor OIDC real, registro real, proxy real ou Jira: emissores falsos por `MockEngine`, hosts `.invalid`, manifestos lidos como YAML.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1 (`ktor-server-forwarded-header`, `ktor-server-auth`, `-auth-jwt`, `-sessions`), Flyway, PostgreSQL + Testcontainers 2.0.3, Ktor `MockEngine`; Docker e Docker Compose; GitHub Actions (workflow do Plano 9); manifestos Kubernetes (só se o destino for k8s); `sh` + `curl` nos scripts.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-13-implantacao-real.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 3, 4.3, 4.6 "Produção × desenvolvimento" e "Ambiente", 9 e 11). Lacuna de origem: `docs/superpowers/cobertura-do-projeto.md`, C5.

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere na `main` que as Fases 8, 9, 11 e 12 foram mescladas e que os nomes abaixo existem. Os Planos 9 a 12 podem ter dado outro nome à mesma capacidade: nesse caso, use o nome da `main`, atualize só os nomes deste plano e registre `Ruling:` no arquivo de progresso.

| Nome ou capacidade | Origem |
|---|---|
| `OidcSettings`, `OidcAuthenticator`, `rotasDeLogin`, cookie `painel_sessao`, variáveis `OIDC_*` e `SESSION_SECRET`, `producaoSemOidcNaoSobe` | `security/` (Plano 8, T2) |
| `ROTAS_PUBLICAS`, `InventarioDeRotasTest.inventarioConhecido` | Plano 8, T1 |
| Perfil `prod` do `app/docker-compose.yml`, `app/scripts/verificar-imagem.sh`, `ComposeTest`, `docs/implantacao.md` | Plano 8, T7 |
| `/api/meta` e o teste `metaNaoMudou` | Plano 1 e Plano 4A, T8 |
| Workflow de CI `.github/workflows/ci.yml` (testes do backend com Testcontainers, frontend, `test-guard.sh`, varredura de dados pessoais) e o teste estático `CiWorkflowTest` | Plano 9, T2 |
| Plugin `CabecalhosDeSeguranca` (CSP com `font-src 'self'`, `X-Frame-Options`, `Referrer-Policy`, `Strict-Transport-Security` fora do dev mode), `ProtecaoCsrf` com `DASHBOARD_ORIGEM_PUBLICA` (`https://` obrigatório fora do dev mode), `ErroDto`, 401 tratado na SPA, imagem com `DASHBOARD_DEV_MODE=false` por padrão, imagens base por digest | Plano 9, T3 a T6 |
| Logs JSON sem segredos, métricas, readiness com banco, alertas de sincronização | Plano 12 (confirmar nomes) |
| Validador de configuração (por exemplo `painel validar-config`) | Plano 12 (confirmar o nome) |
| Backup agendado e teste de restauração do PostgreSQL; runbook diário e de incidentes; rotação de segredos | Plano 12 (confirmar nomes e caminhos) |
| Trava de LGPD que mantém Auditoria e Integridade desligadas até o aceite do DPO | Plano 11 (confirmar o nome da variável) |
| `DASHBOARD_DEMO_DATA`, `demoEmProducaoDerrubaSubida` | Plano 4A, T10 |

## Restrições globais (Global Constraints)

- **Jira:** a única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Este plano não acessa o Jira. A produção sobe com `JIRA_LIVE_ENABLED=false`; **só o usuário** liga a flag em produção (Tarefa 12).
- **Nenhum teste automático acessa provedor OIDC real, registro de imagens real, proxy real ou Jira**, mesmo com a flag `true`. Emissores falsos por `MockEngine` com chaves RSA geradas no teste; hosts sempre `.invalid` (`https://sso.exemplo.invalid`, `painel.exemplo.invalid`, `registro.exemplo.invalid`).
- **Nenhum host interno, IP, nome de cofre, nome de cluster, URL de registro, client id ou segredo real entra no repositório.** Os documentos usam marcadores (`<URL_DO_PROVEDOR>`, `<REGISTRO>`); os valores reais ficam no cofre ou no ambiente do servidor. O teste `implantacaoSemHostRealNemSegredo` (T13) varre `docs/implantacao*` e `deploy/`.
- **Rede permitida:** registros de pacotes e de imagens (Maven/Gradle, npm, Docker Hub) para build, como no Plano 8. Acesso ao ambiente da empresa (servidor, cluster, registro, provedor OIDC) **só nas etapas de runbook**, depois do ponto de parada que as autoriza, e nunca ao Jira.
- **Efeito fora do repositório** (publicar imagem, criar segredo no cofre, aplicar manifesto, abrir chamado à infraestrutura, subir serviço) só depois de "Ponto de parada: perguntar ao usuário" respondido com "sim".
- Os papéis vêm **sempre** do `RoleDirectory` vigente, nunca de claims. O username vem de uma claim configurada e precisa ser **igual ao username do Jira em minúsculas** (o `id` de `papeis.yaml`).
- Configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `jira_campos.yaml`, `status_map.yaml`, `calendario.yaml`) nunca entram no git nem na imagem; entram por volume ou objeto do destino, somente leitura.
- Flyway só avança: reversão = restaurar o backup feito **antes** da migração e subir a imagem anterior. `cleanDisabled` sempre `true` fora dos testes.
- Variáveis novas começam com `DASHBOARD_` ou `OIDC_` (nunca `JIRA_`). Commits em Conventional Commits pt-BR (`feat(seguranca): ...`, `build(deploy): ...`, `docs(implantacao): ...`). Comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Username do provedor diferente do username do Jira** (UPN `ana.silva@empresa.invalid`, `DOMINIO\ana.silva`, maiúsculas): a pessoa entra sem papel (403) ou, pior, casa com outra pessoa → `entraUpnParteLocal`, `adfsContaWindows`, `estrategiaNaoCasaComOutraPessoa` (T2).
2. **`X-Forwarded-Proto`/`-For`/`-Host` forjado por cliente direto**, sem passar pelo proxy: o painel acredita em HTTPS falso, monta `redirect_uri` com host do atacante ou grava IP falso no `access_log` → `forwardedDeOrigemNaoConfiavelIgnorado` (T3).
3. **Produção subindo com modo de desenvolvimento, demonstração, emissor `http` ou Flyway `clean` habilitado** → `producaoComDevModeNaoSobe`, `issuerHttpNaoSobe`, `flywayCleanDesligadoEmProducao` (T4).
4. **Rota de versão vazando segredo ou mudando `/api/meta`** → `versaoSemSegredos`, `metaNaoMudou` continua verde (T5).
5. **Manifesto do destino com segredo literal, root, imagem por tag mutável ou porta pública** → testes estáticos da T7 ou da T8.
6. **Reversão que perde dados** (migração nova aplicada, imagem anterior sobe sobre o banco migrado) → `ensaio-reversao.sh` com banco restaurado do backup anterior à migração (T10).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  security/ExtratorDeUsername.kt           # estratégia por perfil de provedor (T2)
  security/ProxyConfiavel.kt               # X-Forwarded-* só do proxy (T3)
  config/GuardasDeProducao.kt              # recusa de subida em produção (T4)
  cards/VersaoRoutes.kt                    # GET /api/versao (T5)
app/backend/src/test/kotlin/br/sidia/painel/
  seguranca/{ExtratorDeUsernameTest.kt, ProxyConfiavelTest.kt}
  config/GuardasDeProducaoTest.kt, cards/VersaoRoutesTest.kt
  implantacao/{ImagemOciTest.kt, WorkflowPublicacaoTest.kt, DeployVmTest.kt | DeployK8sTest.kt, ScriptsDeImplantacaoTest.kt, ImplantacaoDocTest.kt}
app/Dockerfile (rótulos OCI e build args), app/.env.example
.github/workflows/ci.yml  (job de publicação, Plano 9)
deploy/vm/{compose.prod.yml, README.md}   ou   deploy/k8s/{deployment.yaml, service.yaml, ingress.yaml, networkpolicy.yaml, README.md}
app/scripts/{fumaca.sh, ensaio-reversao.sh}
docs/implantacao.md, docs/implantacao/{decisoes.md, runbook-primeira-subida.md, segredos.md, reversao.md}
HANDOFF.md, docs/superpowers/plans/2026-10-24-indice-dos-planos.md
```

---

### Tarefa 1: Decisões com a infraestrutura (ponto de parada)

**Arquivos:** Criar `docs/implantacao/decisoes.md` (só marcadores e escolhas, sem valores reais); Teste `implantacao/ImplantacaoDocTest.kt` (`decisoesSemHostReal`, `decisoesTemCadaPergunta`).

**Interfaces:** Produz o registro das decisões que as Tarefas 2 a 12 consomem: `perfilOidc`, `destino`, `registro`, `cofre`, `terminacaoTls`, `proxyCidr` (como marcador).

- [ ] **Passo 1: Teste que falha:** `decisoesTemCadaPergunta` (o documento tem uma seção para cada uma das cinco perguntas abaixo, com a resposta e a data); `decisoesSemHostReal` (nenhuma URL fora de `.invalid`, nenhum IP, nenhum `customfield`, nenhum e-mail).
- [ ] **Passo 2: Ponto de parada: perguntar ao usuário** (uma pergunta por assunto, opções na ordem abaixo, a primeira marcada "(Recomendada)"):
  1. **Provedor OIDC.**
     - *Keycloak da empresa (Recomendada, se existir):* claim `preferred_username` costuma ser o username de rede, igual ao do Jira DC; configuração direta; consequência: estratégia `CLAIM_DIRETO`.
     - *Microsoft Entra ID (Azure AD):* comum em empresas com Microsoft 365; o `preferred_username` é um UPN com domínio; consequência: estratégia `PARTE_LOCAL_DO_EMAIL` e conferência de que a parte local é igual ao username do Jira para todas as pessoas do `papeis.yaml`.
     - *ADFS (Windows Server):* claim `winaccountname` ou `upn`; consequência: estratégia `CONTA_WINDOWS` (`DOMINIO\usuario` → `usuario`).
     - *Outro provedor OIDC padrão (Okta, Google Workspace, Ping):* consequência: estratégia escolhida pela claim que o provedor emitir; teste de forma igual aos outros.
  2. **Destino.**
     - *VM com Docker Compose (Recomendada):* um contêiner + PostgreSQL, igual ao perfil `prod` do Plano 8; menor custo de operação para dezenas de usuários; consequência: executar a T7 e pular a T8.
     - *Kubernetes da empresa:* escala e padrão corporativo, mas exige namespace, Ingress, NetworkPolicy e banco gerenciado ou StatefulSet; consequência: executar a T8 e pular a T7.
  3. **Registro de imagens.**
     - *Registro interno da empresa (Harbor, Artifactory, Nexus) (Recomendada):* a imagem não sai da rede; consequência: credencial de push só no CI, como segredo.
     - *GitHub Container Registry privado:* simples, ligado ao repositório; consequência: confirmar que a política da empresa permite imagem fora da rede.
     - *Sem registro (build no servidor):* consequência: sem digest reprodutível; reversão mais lenta; só para homologação.
  4. **Cofre de segredos.**
     - *Cofre corporativo (Vault, Azure Key Vault, CyberArk) (Recomendada):* rotação e auditoria prontas.
     - *Secrets do Kubernetes* (só com destino k8s): cifrados no etcd se o cluster estiver configurado assim.
     - *Arquivo de ambiente no servidor* (`/etc/painel/painel.env`, dono root, modo `600`): consequência: rotação manual, documentada no Plano 12.
  5. **TLS e proxy.**
     - *Proxy da empresa termina o TLS e encaminha HTTP interno (Recomendada):* o painel confia em `X-Forwarded-*` só da faixa do proxy (T3).
     - *TLS até o contêiner:* exige certificado montado e renovação; consequência: Tarefa extra de certificado, registrada como `Ruling:`.
- [ ] **Passo 3: Registrar** cada resposta e a data no documento (com marcadores no lugar de hosts) e no arquivo de progresso. Sem resposta para o provedor, seguir com as Tarefas 3 a 6 e 9 a 10 (não dependem dele) e voltar à T2 quando houver.
- [ ] **Passo 4: Rodar** `cd app/backend && ./gradlew test --tests '*ImplantacaoDocTest*'` → PASSA. **Passo 5: Commit** `docs(implantacao): decisões de provedor, destino, registro, cofre e TLS`.

---

### Tarefa 2: Username pelo perfil do provedor

**Arquivos:** Criar `security/ExtratorDeUsername.kt`; Modificar `security/OidcSettings.kt`, `security/Oidc.kt`, `config/Settings.kt`, `app/.env.example`; Teste `seguranca/ExtratorDeUsernameTest.kt`, `seguranca/OidcTest.kt` (casos novos).

**Interfaces:**
- Produz:
  ```kotlin
  enum class EstrategiaDeUsername { CLAIM_DIRETO, PARTE_LOCAL_DO_EMAIL, CONTA_WINDOWS }
  object ExtratorDeUsername {
      // Devolve o username normalizado (normalizarUsername) ou null se a claim faltar ou ficar vazia.
      fun extrair(claims: Map<String, Any?>, claim: String, estrategia: EstrategiaDeUsername): String?
  }
  // OidcSettings ganha: val estrategiaUsername: EstrategiaDeUsername = CLAIM_DIRETO
  ```
- Variável `OIDC_USERNAME_ESTRATEGIA` (`claim_direto`, `parte_local_do_email`, `conta_windows`; ausente = `claim_direto`; inválida derruba a subida citando só o nome da variável).
- `PARTE_LOCAL_DO_EMAIL`: exige exatamente um `@`; a parte local passa por `normalizarUsername`. `CONTA_WINDOWS`: exige exatamente um `\`; usa o que vem depois. Formato diferente do esperado → `null` → 401 com `access_log` (`motivo = USERNAME_ILEGIVEL`), nunca "adivinhar".
- Uma única estratégia por instalação; não há tentativa em cascata (evita casar com outra pessoa).

- [ ] **Passo 1: Testes que falham** (emissores falsos por `MockEngine`, um por formato de provedor, todos em `sso.exemplo.invalid`):
  - `keycloakPreferredUsername`: `preferred_username = "Ana.Silva"` → `ana.silva`, papéis do diretório.
  - `entraUpnParteLocal`: `preferred_username = "ana.silva@empresa.invalid"` com `parte_local_do_email` → `ana.silva`.
  - `adfsContaWindows`: `winaccountname = "EMPRESA\\ana.silva"` com `conta_windows` → `ana.silva`.
  - `estrategiaNaoCasaComOutraPessoa`: `ana.silva@outro.invalid` com `claim_direto` → username `ana.silva@outro.invalid`, fora do diretório → 403; nunca `ana.silva`.
  - `doisArrobasIlegivel401`, `claimAusente401`, `estrategiaInvalidaNaoSobe`.
  - `claimDePapelIgnorada` (Plano 8) continua verde com as três estratégias.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ExtratorDeUsernameTest*' --tests '*OidcTest*' --tests '*SettingsTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(seguranca): username pelo perfil do provedor OIDC`.

---

### Tarefa 3: Proxy confiável e HTTPS atrás do proxy

**Arquivos:** Criar `security/ProxyConfiavel.kt`; Modificar `Application.kt`, `config/Settings.kt`, `security/Oidc.kt` (`redirect_uri` sempre `https` em produção), `app/.env.example`, `app/build.gradle.kts` (`ktor-server-forwarded-header`, se faltar); Teste `seguranca/ProxyConfiavelTest.kt`.

**Interfaces:**
- Variável `DASHBOARD_PROXY_CONFIAVEL` (lista de CIDR separada por vírgula; obrigatória com `DASHBOARD_DEV_MODE=false`; inválida derruba a subida citando só o nome).
- `fun Application.instalarProxyConfiavel(cidrs: List<String>)`: aplica `X-Forwarded-Proto`, `-For` e `-Host` **só** quando o IP de origem da conexão está numa faixa confiável; caso contrário, ignora os cabeçalhos. O `access_log` grava o IP já resolvido.
- `OIDC_REDIRECT_URI` com `http://` em produção derruba a subida. O cookie `painel_sessao` continua `Secure`.
- Atrás do proxy, o `Host` que chega ao contêiner pode ser interno: em produção `DASHBOARD_ORIGEM_PUBLICA` (Plano 9) passa a ser **obrigatória**, e `OIDC_REDIRECT_URI` precisa começar com ela (`redirectUriForaDaOrigemPublicaNaoSobe`).

- [ ] **Passo 1: Testes que falham:**
  - `forwardedDeOrigemNaoConfiavelIgnorado`: origem `203.0.113.9` fora da faixa, `X-Forwarded-Proto: https`, `X-Forwarded-Host: atacante.invalid` → o painel vê `http` e o host original; o login não monta `redirect_uri` com `atacante.invalid`.
  - `forwardedDoProxyAceito`: origem `10.0.0.5` em `10.0.0.0/24` → `https` e IP do cliente no `access_log`.
  - `redirectUriHttpNaoSobeEmProducao`, `proxyAusenteNaoSobeEmProducao`, `cidrInvalidoNaoSobe`.
  - `cookieSecureAtrasDoProxy` (resposta do callback com `Secure`, `HttpOnly`, `SameSite=Lax`).
  - `redirectUriForaDaOrigemPublicaNaoSobe`; `origemPublicaAusenteNaoSobeEmProducao`.
  - `devModeNaoExigeProxy`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ProxyConfiavelTest*' --tests '*SettingsTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(seguranca): cabeçalhos do proxy só de origem confiável`.

---

### Tarefa 4: Guardas de produção

**Arquivos:** Criar `config/GuardasDeProducao.kt`; Modificar `Application.kt` (chamada na subida, antes de abrir a porta), `db/Database.kt` (Flyway `cleanDisabled(true)` fora dos testes); Teste `config/GuardasDeProducaoTest.kt`.

**Interfaces:**
- Produz `object GuardasDeProducao { fun verificar(s: Settings) }`, chamado quando `DASHBOARD_DEV_MODE=false`. Cada violação lança `IllegalStateException` com o **código** da violação, sem valor:
  - `DEMO_EM_PRODUCAO` (`DASHBOARD_DEMO_DATA=true`; reforça o teste do 4A);
  - `OIDC_ISSUER_SEM_HTTPS`;
  - `SESSION_SECRET_CURTO` (< 32 bytes);
  - `TRAVA_LGPD_AUSENTE` (a variável da trava do Plano 11 não declarada; conferir o nome na `main`);
  - `PROXY_NAO_CONFIGURADO` (T3).
- A imagem continua aceitando `DASHBOARD_DEV_MODE=true` só para o uso local documentado; a recusa vale para a combinação, não para a variável isolada.

- [ ] **Passo 1: Testes que falham:** `producaoComDemoNaoSobe`, `issuerHttpNaoSobe`, `segredoCurtoNaoSobe`, `travaLgpdAusenteNaoSobe`, `producaoValidaSobe` (todas as variáveis com valores fictícios válidos), `mensagemSemValor` (a exceção não contém o valor do segredo nem a URL), `flywayCleanDesligadoEmProducao` (o `Flyway` configurado tem `isCleanDisabled == true`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*GuardasDeProducaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(config): guardas que impedem subida insegura em produção`.

---

### Tarefa 5: Versão da aplicação e rótulos da imagem

**Arquivos:** Criar `cards/VersaoRoutes.kt`; Modificar `Application.kt`, `app/backend/build.gradle.kts` (gera `versao.properties` com versão, commit curto e data de build), `app/Dockerfile` (`ARG VERSAO`, `ARG COMMIT`, rótulos `org.opencontainers.image.version`, `.revision`, `.created`, `.source`), `seguranca/InventarioDeRotasTest.kt` (rota nova na lista); Teste `cards/VersaoRoutesTest.kt`, `implantacao/ImagemOciTest.kt`.

**Interfaces:**
- `GET /api/versao` → `@Serializable data class VersaoDto(val versao: String, val commit: String, val construidaEm: String)`; exige sessão (qualquer papel); grava `access_log`. **`/api/meta` não muda** (o teste `metaNaoMudou` do 4A continua verde).
- Versão no formato `AAAA.MM.N` (ex.: `2026.11.1`); a tag de imagem é `<versao>-<commit>`; implantação sempre por **digest**.

- [ ] **Passo 1: Testes que falham:** `versaoExigeSessao` (sem principal → 401), `versaoDevolveCampos`, `versaoSemSegredos` (corpo sem host, token, URL), `metaNaoMudou` (continua), `inventarioConhecido` (atualizado com `/api/versao`), `ImagemOciTest.dockerfileTemRotulosOci` (lê o `Dockerfile` como texto: os quatro rótulos e os dois `ARG`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*VersaoRoutesTest*' --tests '*ImagemOciTest*' --tests '*InventarioDeRotasTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e `sh app/scripts/verificar-imagem.sh`. **Passo 5: Commit** `feat(build): versão da aplicação e rótulos OCI na imagem`.

---

### Tarefa 6: Publicação da imagem pelo CI

**Arquivos:** Modificar `.github/workflows/ci.yml` (Plano 9; job `publicar-imagem`); Criar `docs/implantacao/publicacao.md`; Teste `implantacao/WorkflowPublicacaoTest.kt` (lê o YAML do workflow; o `CiWorkflowTest` do Plano 9 continua verde).

**Interfaces:**
- Job `publicar-imagem`: roda **só** em tag `v*` e depois dos jobs de teste do Plano 9; faz build com `VERSAO` e `COMMIT`; push para `${{ vars.REGISTRO_IMAGEM }}` com credencial em `${{ secrets.* }}`; imprime o **digest** no resumo do job.
- Enquanto `vars.REGISTRO_IMAGEM` não existir, o job termina sem push (condição `if:`), sem falhar o CI.

- [ ] **Passo 1: Testes que falham:** `publicaSoEmTag`, `publicaDepoisDosTestes` (`needs` contém os jobs de teste), `semSegredoLiteral` (nenhum valor de credencial; só `secrets.`), `semRegistroNaoPublica` (`if:` com `vars.REGISTRO_IMAGEM != ''`), `imprimeDigest`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*WorkflowPublicacaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário** antes de configurar o registro no GitHub e de criar a primeira tag (efeito fora do repositório). Opções: "Configurar `REGISTRO_IMAGEM` e o segredo de push agora e criar a tag `v2026.MM.1` (Recomendada): a imagem fica publicada com digest e a T11 pode usá-la"; "Configurar depois: o job fica inerte e a homologação usa build local no servidor (T11 sem digest)"; "Não publicar: só homologação local". Registrar a resposta.
- [ ] **Passo 6: Commit** `build(ci): publicação da imagem por tag com digest`.

---

### Tarefa 7: Destino VM com Docker Compose (só se a T1 escolheu VM)

**Arquivos:** Criar `deploy/vm/compose.prod.yml` e `deploy/vm/README.md`; Teste `implantacao/DeployVmTest.kt`.

**Interfaces:**
- `compose.prod.yml` estende o perfil `prod` do Plano 8 e **não faz build**: `image: <REGISTRO>/painel@sha256:<DIGEST>` (marcadores substituídos no servidor por variável `PAINEL_IMAGEM`); `env_file: /etc/painel/painel.env` (fora do repositório, modo `600`); configs reais por volume `:ro` de `/etc/painel/config/`; porta só na interface que o proxy alcança (`${PAINEL_BIND:-127.0.0.1}:8080`); PostgreSQL sem `ports` e com volume nomeado; `restart: unless-stopped`; `read_only`, `cap_drop: [ALL]`, `no-new-privileges`, usuário `10001`.
- O README tem as etapas da VM (instalar Docker, criar `/etc/painel`, permissões, `docker compose -f ... up -d`, como ver logs), com "como conferir" em cada uma.

- [ ] **Passo 1: Testes que falham:** `imagemPorDigest` (o campo `image` usa `@sha256:` ou `${PAINEL_IMAGEM}`; nunca `:latest`), `semBuild`, `envFileForaDoRepositorio`, `configsSoLeitura`, `bancoSemPortaPublica`, `endurecido` (mesmas chaves do `perfilProdEndurecido` do Plano 8), `semValorDeSegredo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DeployVmTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e `docker compose -f app/docker-compose.yml -f deploy/vm/compose.prod.yml config` (só validação, sem subir). **Passo 5: Commit** `build(deploy): implantação em VM com compose por digest`.

Se a T1 escolheu k8s: não executar; registrar `Ruling: T7 pulada (destino k8s)`.

---

### Tarefa 8: Destino Kubernetes (só se a T1 escolheu k8s)

**Arquivos:** Criar `deploy/k8s/{deployment.yaml, service.yaml, ingress.yaml, networkpolicy.yaml, README.md}`; Teste `implantacao/DeployK8sTest.kt` (lê os YAML com o mesmo parser estrito usado nos demais testes de YAML).

**Interfaces:**
- `Deployment` com `replicas: 1` (valor padrão 25/10, reversível: a trava entre réplicas do 4A permite subir para 2 depois de medir); `image` por digest; `securityContext` com `runAsNonRoot: true`, `runAsUser: 10001`, `readOnlyRootFilesystem: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`; `emptyDir` em `/tmp`; `readinessProbe` na rota de readiness do Plano 12 e `livenessProbe` em `/api/health`; variáveis secretas por `secretKeyRef` (nomes, nunca valores); configs reais por `Secret` montado `readOnly` em `/app/config/` (criado fora do git).
- `Ingress` só com `tls:` (o segredo do certificado é referência); `NetworkPolicy` permite entrada só do controlador de Ingress e saída só para o banco, o provedor OIDC e (quando o usuário ligar a flag) o Jira.

- [ ] **Passo 1: Testes que falham:** `naoRodaComoRoot`, `sistemaDeArquivosSoLeitura`, `imagemPorDigest`, `semSegredoLiteral` (nenhum `value:` em variável cujo nome contém `SECRET`, `PASSWORD`, `TOKEN`), `probesConfiguradas`, `ingressSoComTls`, `networkPolicyRestringeEntrada`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DeployK8sTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `build(deploy): manifestos Kubernetes endurecidos`.

Se a T1 escolheu VM: não executar; registrar `Ruling: T8 pulada (destino VM)`.

---

### Tarefa 9: Segredos no cofre e rotação

**Arquivos:** Criar `docs/implantacao/segredos.md`; Modificar `app/.env.example` (toda variável nova deste plano, sem valor, comentada em pt-BR); Teste `implantacao/ImplantacaoDocTest.kt` (casos novos).

**Interfaces:** O documento lista cada segredo (`DATABASE_PASSWORD`, `SESSION_SECRET`, `OIDC_CLIENT_SECRET`, tokens do Jira por instância), **onde** fica no cofre escolhido na T1 (marcador), quem cria, como gerar (`openssl rand -base64 48` para `SESSION_SECRET`), como injetar no destino e como rotacionar (aponta para o runbook de rotação do Plano 12; conferir o caminho). Os tokens do Jira só são criados quando o usuário decidir ligar a flag (Plano 14).

- [ ] **Passo 1: Testes que falham:** `segredosCitaCadaVariavelSecreta` (lê `app/.env.example` e exige uma linha por variável cujo nome contém `SECRET`, `PASSWORD` ou `TOKEN`), `envExampleSemValor` (nenhuma dessas com valor), `segredosApontaRotacao`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ImplantacaoDocTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Runbook (efeito fora do repositório, depois de "sim" do usuário):** criar os segredos no cofre com valores gerados na hora; **como conferir:** o destino lê cada variável (a subida não falha por variável ausente) e nenhum valor aparece em log (`docker logs` ou `kubectl logs` sem o valor gerado).
- [ ] **Passo 6: Commit** `docs(implantacao): segredos no cofre e rotação`.

---

### Tarefa 10: Atualização e reversão ensaiadas

**Arquivos:** Criar `app/scripts/ensaio-reversao.sh`, `docs/implantacao/reversao.md`; Teste `implantacao/ScriptsDeImplantacaoTest.kt`.

**Interfaces:**
- Procedimento de atualização: (1) backup do banco pelo mecanismo do Plano 12 **antes** de trocar a imagem, com o digest da imagem atual anotado; (2) subir a imagem nova (o Flyway migra); (3) fumaça (T11). Reversão: parar o painel, restaurar o backup da etapa 1, subir a imagem anterior pelo digest anotado, fumaça.
- `ensaio-reversao.sh` roda **local**, com Docker e senha descartável: sobe a versão `N` (imagem local), grava dados fictícios, faz backup, sobe `N+1` com uma migração de ensaio criada em diretório temporário (nunca no repositório), restaura o backup, sobe `N` de novo e confere que os dados fictícios voltaram e que `flyway_schema_history` não tem a migração de ensaio. Termina com `docker compose down -v`.

- [ ] **Passo 1: Testes que falham:** `ensaioNaoCriaMigracaoNoRepositorio` (o script só escreve em `mktemp -d`), `ensaioDerrubaOAmbiente` (`down -v` no `trap`), `reversaoCitaBackupAntesDaMigracao`, `reversaoCitaDigestAnterior`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ScriptsDeImplantacaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, e `sh app/scripts/ensaio-reversao.sh` → saída final `REVERSAO_OK`.
- [ ] **Passo 5: Commit** `build(deploy): atualização com backup e reversão ensaiada`.

---

### Tarefa 11: Homologação e testes de fumaça

**Arquivos:** Criar `app/scripts/fumaca.sh`, `docs/implantacao/runbook-primeira-subida.md`; Teste `implantacao/ScriptsDeImplantacaoTest.kt` (casos novos).

**Interfaces:**
- `fumaca.sh <url-base>` usa só `curl` e sai com código diferente de 0 na primeira falha. Confere: `/api/health` 200; `/api/versao` sem sessão → 401; `/api/cards/tempo-em-status` sem sessão → 401; `/auth/login` → 302 para o emissor configurado com `code_challenge` e `state`; cabeçalhos do `CabecalhosDeSeguranca` do Plano 9 (CSP, `X-Frame-Options`, `Referrer-Policy`, `Strict-Transport-Security`) presentes; `/api/meta` responde `devMode: false` e `banner: null` (a faixa de desenvolvimento é desenhada no cliente a partir dessa resposta).
- Runbook da primeira subida (homologação): pré-requisitos (T1 a T10), configs reais montadas (o validador do Plano 12 roda antes e passa), `DASHBOARD_DEV_MODE=false`, `JIRA_LIVE_ENABLED=false`, `DASHBOARD_DEMO_DATA=false`, trava de LGPD do Plano 11 ligada; subir; `fumaca.sh`; login de uma pessoa de cada papel (USUARIO, SQUAD_LEADER, COORDENADOR, GERENTE) confere o menu do papel; Integridade e Auditoria aparecem bloqueadas pela trava de LGPD.

- [ ] **Passo 1: Testes que falham:** `fumacaConfereCadaItem` (o script contém cada verificação acima), `fumacaSemHostFixo` (a URL vem só do argumento), `runbookTemComoConferirEmCadaEtapa`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ScriptsDeImplantacaoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, e `sh app/scripts/fumaca.sh http://127.0.0.1:8080` contra o compose local em modo produção com emissor `.invalid` (as verificações que dependem do emissor real ficam marcadas `PULADO_SEM_EMISSOR`).
- [ ] **Passo 5: Ponto de parada: perguntar ao usuário** antes de subir no ambiente de homologação da empresa. Opções: "Subir em homologação agora (Recomendada): valida provedor, proxy e TLS reais sem dados do Jira"; "Esperar a infraestrutura liberar o ambiente (registrar a data prevista)"; "Ir direto para produção (não recomendado: sem ensaio no ambiente real)".
- [ ] **Passo 6: Runbook (depois do "sim"):** executar o runbook; **como conferir:** `fumaca.sh https://<host-de-homologacao>` termina com `FUMACA_OK`; registrar data, versão e digest no `HANDOFF.md` (sem host).
- [ ] **Passo 7: Commit** `build(deploy): testes de fumaça e runbook da primeira subida`.

---

### Tarefa 12: Entrada em produção com a flag desligada

**Arquivos:** Modificar `docs/implantacao/runbook-primeira-subida.md` (seção de produção), `HANDOFF.md`.

**Interfaces:** Mesmo runbook da T11 no ambiente de produção, com o mesmo digest homologado.

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** antes da subida em produção. Opções: "Subir em produção com `JIRA_LIVE_ENABLED=false` (Recomendada): o painel fica no ar com login real e sem dados, pronto para o Plano 14"; "Adiar (registrar o motivo e a data)".
- [ ] **Passo 2: Runbook (depois do "sim"):** subir; `fumaca.sh https://<host-de-producao>` → `FUMACA_OK`; conferir o backup agendado do Plano 12 (primeira execução registrada) e os alertas (sincronização aparece "desligada pela flag", sem alerta falso).
- [ ] **Passo 3: Ponto de parada: perguntar ao usuário** sobre a flag do Jira em produção. Opções: "Manter `false` e seguir o Plano 14 a partir do Passo 1 do checklist, com o usuário ligando a flag quando quiser (Recomendada)"; "O usuário liga agora, seguindo o checklist da primeira conexão e o runbook da sincronização"; "Manter desligada por tempo indeterminado (registrar o motivo)". **A IA executora nunca liga a flag.**
- [ ] **Passo 4: Registrar** data, versão, digest e a resposta no `HANDOFF.md` e no progresso. **Passo 5: Commit** `docs(implantacao): entrada em produção registrada`.

---

### Tarefa 13: Documentação de implantação final

**Arquivos:** Modificar `docs/implantacao.md` (índice que aponta para `docs/implantacao/*` e `deploy/`), `app/README.md` (seção produção), `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado da fase 13), `HANDOFF.md`; Teste `implantacao/ImplantacaoDocTest.kt` (casos novos).

- [ ] **Passo 1: Testes que falham:** `implantacaoSemHostRealNemSegredo` (varre `docs/implantacao.md`, `docs/implantacao/`, `deploy/`, `app/scripts/`: só hosts `.invalid` ou marcadores `<...>`, nenhum IP fora de faixas de documentação `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` e `10.0.0.0/24` dos exemplos, nenhum token), `implantacaoCitaCadaDocumento`, `documentoDeImplantacaoCitaBackupERestauracao` (Plano 8) continua verde.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ImplantacaoDocTest*' --tests '*ComposeTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA e suítes completas (backend e frontend), `pnpm build`, `sh .claude/hooks/test-guard.sh`.
- [ ] **Passo 5: Commit** `docs(implantacao): guia de implantação final`.

---

## Autoavaliação

**Cobertura da spec:**

| Requisito | Tarefas |
|---|---|
| Spec 4.6: provedor OIDC real antes de qualquer uso com dados reais | T1, T2, T11 |
| Spec 4.6: contêiner atrás do proxy da empresa | T3, T7 ou T8, T11 |
| Spec 4.3: papel só do `RoleDirectory` | T2 (`claimDePapelIgnorada` mantido) |
| Cobertura C5: registro e versão da imagem, cofre, homologação, fumaça, reversão | T5, T6, T9, T10, T11 |
| Spec 3 e 10: nenhuma requisição ao Jira com a flag `false` | T12 (produção sobe com `false`) |
| Spec 11: DPO antes do uso real | T4 (`TRAVA_LGPD_AUSENTE`), T11 |

**Consistência de tipos:** `EstrategiaDeUsername` (T2) entra em `OidcSettings` do Plano 8 sem mudar a assinatura de `OidcAuthenticator`; `GuardasDeProducao` (T4) usa os códigos da T3 (`PROXY_NAO_CONFIGURADO`); `/api/versao` (T5) entra no `inventarioConhecido` do Plano 8; o digest impresso pela T6 é o que a T7/T8, a T10 e a T11 consomem.

**Pendências deliberadas:** escolha do provedor, destino, registro, cofre e TLS (T1); publicação e subidas (T6, T11, T12); ligar a flag em produção (Plano 14).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Uma estratégia de username por instalação, sem cascata** (T2). Evita casar uma pessoa com outra. Alternativa descartada: tentar várias claims em sequência.
2. **Cabeçalhos do proxy só de faixa confiável** (T3). Alternativa descartada: confiar em `X-Forwarded-*` de qualquer origem.
3. **Versão em rota nova `/api/versao`, sem mudar `/api/meta`** (T5). Mantém o teste `metaNaoMudou` do 4A.
4. **Versão `AAAA.MM.N` e implantação por digest** (T5, T6). Alternativa descartada: semver manual e tag mutável.
5. **`replicas: 1` no k8s** (T8). Reversível depois de medir; a trava entre réplicas do 4A já existe.
6. **Reversão = backup antes da migração + imagem anterior** (T10). O Flyway só avança; não há migração "para baixo".
7. **Produção sobe com `JIRA_LIVE_ENABLED=false`** (T12). Só o usuário liga a flag.

Decisões que **dependem do usuário** (pontos de parada): provedor OIDC, destino, registro, cofre e TLS (T1); publicar a primeira imagem (T6); subir em homologação (T11) e em produção (T12); flag do Jira em produção (T12).
