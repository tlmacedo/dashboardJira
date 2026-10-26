# Spec da Fase 13: Implantação real e entrada em produção

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-13-implantacao-real.md`](../plans/2026-10-25-plano-13-implantacao-real.md) (13 tarefas, um PR; as T7 e T8 são alternativas: só uma é executada)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.3, 4.6 "Produção × desenvolvimento" e "Ambiente", 9, 11)
Depende de: Fases 8, 9, 11 e 12 mescladas na `main`
Lacuna de origem: [`cobertura-do-projeto.md`](../cobertura-do-projeto.md), C5

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra de 24/10 ainda traz a regra antiga de acesso ao Jira; vale a regra da flag única (seção 3).

## 1. Intenção e porquê

Ao fim da Fase 8 o painel está pronto para implantar, mas roda só em `127.0.0.1`, com dados de demonstração e um login OIDC testado contra um emissor falso. A spec mestra exige o provedor real "antes de qualquer uso com dados reais" e o contêiner "atrás do proxy da empresa". Esta fase fecha essa distância: escolhe com o usuário o provedor, o destino, o registro, o cofre e o TLS; implementa e testa offline tudo o que não depende da infraestrutura; e transforma o resto em runbook com verificação objetiva em cada etapa.

A fase termina com o painel em produção, login real, HTTPS, imagem rastreável por digest, reversão ensaiada e **a flag do Jira desligada**. Ligar a flag é do usuário e abre a Fase 14.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Username correto no login:** a claim do provedor, pela estratégia configurada, resulta no username do Jira em minúsculas.
- **Proxy confiável:** `X-Forwarded-*` aceitos só da faixa do proxy; `redirect_uri` sempre `https` e dentro de `DASHBOARD_ORIGEM_PUBLICA` (Plano 9, obrigatória em produção); cookie `Secure`.
- **Guardas de produção:** a subida falha com demonstração, emissor `http`, segredo curto, proxy não configurado ou trava de LGPD ausente; Flyway `clean` desligado.
- **Rastreabilidade:** `/api/versao`, rótulos OCI, tag `<versao>-<commit>`, implantação por digest.
- **Publicação:** job do CI que publica só em tag, depois dos testes.
- **Destino:** manifestos da VM **ou** do k8s, com testes estáticos.
- **Segredos:** documentados, no cofre, com rotação.
- **Atualização e reversão:** backup antes de migrar, ensaio local de reversão.
- **Homologação e produção:** runbook com fumaça; produção com `JIRA_LIVE_ENABLED=false`.

### Não objetivos

- Ligar a flag do Jira, validar números ou calibrar heurísticas (Fase 14).
- Observabilidade, backup agendado, validador de configuração e runbooks diários (Fase 12; esta fase só consome).
- CI, CSP e CSRF (Fase 9; esta fase só acrescenta o job de publicação).
- Alta disponibilidade, múltiplas réplicas ou banco gerenciado além do que o destino escolhido oferecer.

### Antiobjetivos (falha mesmo que "funcione")

- Login que casa uma pessoa com outra (estratégia em cascata, domínio ignorado sem regra).
- Papel vindo de claim do token.
- Acreditar em `X-Forwarded-*` de qualquer origem.
- Produção com modo de desenvolvimento, demonstração ou emissor `http`.
- Host interno, IP, client id, nome de cofre, URL de registro ou segredo no repositório.
- Teste automático falando com provedor, registro, proxy ou Jira reais.
- Publicar imagem, criar segredo, subir serviço ou ligar a flag sem o "sim" do usuário.
- Reversão que sobe a imagem anterior sobre o banco já migrado.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Com `false`, ninguém acessa o Jira por caminho nenhum; com `true`, o acesso fica liberado, seguindo o checklist da primeira conexão. **Só o usuário muda a flag.** Nenhum teste automático acessa o Jira real.

**Visibilidade por papel.** USUARIO vê o seu; SQUAD_LEADER, o seu squad; COORDENADOR e GERENTE, todos. O papel vem do `RoleDirectory` vigente. Integridade só do gerente, sempre auditada.

**Sem ranking de pessoas; pt-BR e inglês; WCAG AA; a cor nunca é o único sinal.** Esta fase não muda telas, mas a fumaça confere a ausência da faixa de desenvolvimento.

**Configs reais fora do git e da imagem.** Entram por volume ou objeto do destino, somente leitura.

**DPO.** Auditoria e Integridade com dados reais só depois do aceite do DPO (trava do Plano 11). A subida em produção exige a trava declarada.

**Ambiente.** Docker ativo; banco real por Testcontainers; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- Hosts em documentos e testes só `.invalid` ou marcadores `<...>`; IPs só de faixas de documentação.
- Rede permitida para build (Maven/Gradle, npm, imagens base). Acesso ao ambiente da empresa só nas etapas de runbook autorizadas.
- Flyway só avança. `cleanDisabled(true)` fora dos testes.
- Variáveis novas com prefixo `DASHBOARD_` ou `OIDC_`.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | Login de produção por SSO/OIDC genérico atrás de interface; provedor real antes de dados reais | [usuário/design] spec mestra 4.3 e 4.6 |
| U2 | Um contêiner servindo API e SPA atrás do proxy da empresa | [design aceito] spec mestra 4.6 |
| U3 | Flag do Jira: única trava; só o usuário muda | [usuário] `CLAUDE.md`, 25/10 |
| U4 | Repositório privado; nenhum dado interno publicado | [usuário] guia, seção 6 |
| D1 | Uma estratégia de username por instalação, sem cascata | [valor padrão 25/10, reversível] |
| D2 | `X-Forwarded-*` só da faixa `DASHBOARD_PROXY_CONFIAVEL` | [valor padrão 25/10, reversível] |
| D3 | Versão em `/api/versao` (sessão exigida); `/api/meta` inalterada | [valor padrão 25/10, reversível] |
| D4 | Versão `AAAA.MM.N`; tag `<versao>-<commit>`; implantação por digest | [valor padrão 25/10, reversível] |
| D5 | `replicas: 1` no k8s | [valor padrão 25/10, reversível] |
| D6 | Reversão = restaurar o backup anterior à migração + imagem anterior | [valor padrão 25/10, reversível] |
| D7 | Produção sobe com `JIRA_LIVE_ENABLED=false` | [decorre de U3] |

## 5. O que fica para quem implementa

- Biblioteca de leitura de CIDR (ou código próprio pequeno) e a forma de integrar com o plugin de cabeçalhos encaminhados do Ktor.
- Formato interno do `versao.properties` e a forma de o Gradle gerá-lo.
- Texto dos runbooks, desde que cada etapa tenha "como conferir".
- Escolhas em aberto: registrar `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-13-implantacao-real.md`.

## 6. Critérios de aceitação verificáveis

1. **Decisões (T1).** `ImplantacaoDocTest.decisoesTemCadaPergunta` e `decisoesSemHostReal` passam; as cinco respostas estão no documento com data.
2. **Username (T2).** `./gradlew test --tests '*ExtratorDeUsernameTest*' --tests '*OidcTest*'` passa, incluindo `keycloakPreferredUsername`, `entraUpnParteLocal`, `adfsContaWindows`, `estrategiaNaoCasaComOutraPessoa`, `estrategiaInvalidaNaoSobe` e `claimDePapelIgnorada`.
3. **Proxy (T3).** `ProxyConfiavelTest` passa: `forwardedDeOrigemNaoConfiavelIgnorado`, `forwardedDoProxyAceito`, `redirectUriHttpNaoSobeEmProducao`, `cookieSecureAtrasDoProxy`.
4. **Guardas (T4).** `GuardasDeProducaoTest` passa, incluindo `flywayCleanDesligadoEmProducao` e `mensagemSemValor`.
5. **Versão (T5).** `VersaoRoutesTest` e `ImagemOciTest` passam; `metaNaoMudou` e `inventarioConhecido` verdes.
6. **Publicação (T6).** `WorkflowPublicacaoTest` passa; a resposta do usuário sobre o registro está registrada.
7. **Destino (T7 ou T8).** O teste do destino escolhido passa; o outro está registrado como pulado.
8. **Segredos (T9).** `segredosCitaCadaVariavelSecreta` e `envExampleSemValor` passam.
9. **Reversão (T10).** `sh app/scripts/ensaio-reversao.sh` termina com `REVERSAO_OK`.
10. **Fumaça (T11, T12).** `fumaca.sh` termina com `FUMACA_OK` em homologação e em produção (registro no `HANDOFF.md` sem host).
11. **Documentos (T13).** `implantacaoSemHostRealNemSegredo` passa; suítes completas, `pnpm build` e `test-guard.sh` verdes.
12. **Fim.** Varredura de dados pessoais e hosts antes do push; PR para a `main`; nada mesclado sem o usuário mandar.

## 7. Riscos, pendências e pontos de parada

**Pontos de parada (perguntar ao usuário, com opções; a recomendada primeiro):**
- **Provedor OIDC** (T1): Keycloak (Recomendada, se existir), Entra ID, ADFS, outro OIDC padrão.
- **Destino** (T1): VM com compose (Recomendada), Kubernetes.
- **Registro** (T1): interno (Recomendada), GHCR privado, sem registro.
- **Cofre** (T1): cofre corporativo (Recomendada), Secrets do k8s, arquivo `600` no servidor.
- **TLS** (T1): termina no proxy (Recomendada), até o contêiner.
- **Publicar a primeira imagem** (T6), **subir em homologação** (T11), **subir em produção** (T12).
- **Flag do Jira em produção** (T12): manter `false` e seguir a Fase 14 (Recomendada); o usuário liga agora; manter desligada.

**Riscos:**
- Username do provedor diferente do Jira para parte das pessoas (ex.: contas com sufixo). Mitigação: a T2 recusa em vez de adivinhar; o Plano 14 confere os ids de `papeis.yaml`.
- Proxy que não envia `X-Forwarded-Proto`. Mitigação: `redirect_uri` fixo em `https` por configuração; a fumaça detecta.
- Política da empresa contra imagem fora da rede. Mitigação: opção de registro interno.
- Nomes dos Planos 9 a 12 diferentes dos previstos. Mitigação: "Estado herdado" e `Ruling:`.

## 8. Glossário do que esta fase consome

| Nome | Arquivo | Origem |
|---|---|---|
| `OidcSettings`, `OidcAuthenticator`, `rotasDeLogin`, `painel_sessao` | `security/` | [Plano 8] T2 |
| `ROTAS_PUBLICAS`, `InventarioDeRotasTest` | `src/test/.../seguranca/` | [Plano 8] T1 |
| Perfil `prod`, `verificar-imagem.sh`, `ComposeTest`, `docs/implantacao.md` | `app/`, `docs/` | [Plano 8] T7 |
| `/api/meta`, `metaNaoMudou` | `Application.kt`, testes do 4A | [main] Plano 1; [Plano 4A] T8 |
| `.github/workflows/ci.yml`, `CiWorkflowTest`, `CabecalhosDeSeguranca` (CSP `font-src 'self'`, HSTS), `ProtecaoCsrf`, `DASHBOARD_ORIGEM_PUBLICA`, imagem com dev mode desligado por padrão | `.github/workflows/`, `http/` | [Plano 9] T2 a T6 |
| Readiness, logs JSON, alertas, validador de configuração, backup agendado, rotação de segredos | backend, `docs/` | [Plano 12] (confirmar nomes) |
| Trava de LGPD de Auditoria e Integridade | backend | [Plano 11] (confirmar o nome) |
| `DASHBOARD_DEMO_DATA` | `config/Settings.kt` | [Plano 4A] T10 |

## 9. Referência ao plano

Execute [`2026-10-25-plano-13-implantacao-real.md`](../plans/2026-10-25-plano-13-implantacao-real.md) tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). Os pontos de parada da seção 7 bloqueiam só as etapas que dependem deles; as demais tarefas seguem.
