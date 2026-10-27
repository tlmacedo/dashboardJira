# Spec da Fase 13: Implantação real e entrada em produção

Data: 25/10/2026 (reescrita em 27/10/2026 para SQLite embutido, sem Docker, sem PostgreSQL, sem Kubernetes e sem registro de imagens)
Plano: [`docs/superpowers/plans/2026-10-25-plano-13-implantacao-real.md`](../plans/2026-10-25-plano-13-implantacao-real.md) (13 tarefas; um PR para as Tarefas 1 a 10 e 13, mais um PR curto de registro das etapas de runbook)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.3, 4.6 "Produção × desenvolvimento" e "Ambiente", 9, 11; onde ela fala de contêiner, vale o adendo)
Adendo de plataforma: [`docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`](../plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md) (vale sobre qualquer trecho antigo; seção 5 dá os equivalentes)
Depende de: Planos 8, 9, 11 e 12 (versões reescritas para SQLite sem Docker) mesclados na `main`
Lacuna de origem: [`cobertura-do-projeto.md`](../cobertura-do-projeto.md), C5

> **Precedência:** `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > adendo de plataforma > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira; vale a regra da flag única (seção 3).

## 1. Intenção e porquê

Ao fim da Fase 8 o painel está pronto para implantar, mas roda só em `127.0.0.1`, com dados de demonstração e um login OIDC testado contra um emissor falso. A spec mestra exige o provedor real "antes de qualquer uso com dados reais" e o painel "atrás do proxy da empresa". A empresa bloqueia Docker e PostgreSQL: o painel é um processo JVM com banco SQLite em arquivo, então "implantar" significa entregar um **pacote** (`./gradlew installDist` + build do frontend) numa máquina da empresa (Windows 11 ou Linux) e mantê-lo rodando como serviço do sistema operacional.

Esta fase fecha a distância: escolhe com o usuário o provedor, a máquina e o gerenciador do serviço, o proxy e o TLS, o modo de guardar segredos e a forma de entrega; implementa e testa offline tudo o que não depende da infraestrutura; e transforma o resto em runbook com verificação objetiva em cada etapa.

A fase termina com o painel em produção, login real, HTTPS no proxy, pacote rastreável por versão e SHA-256, atualização e reversão ensaiadas e **a flag do Jira desligada**. Ligar a flag é do usuário e abre a Fase 14.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **Username correto no login:** a claim do provedor, pela estratégia configurada, resulta no username do Jira em minúsculas.
- **Proxy confiável:** `X-Forwarded-*` aceitos só da faixa do proxy, `X-Forwarded-For` lido da direita; `redirect_uri` fixo em `OIDC_REDIRECT_URI` (https, dentro de `DASHBOARD_ORIGEM_PUBLICA` do Plano 9); cookie `Secure`.
- **Escuta restrita:** o painel escuta em `127.0.0.1` por padrão (`DASHBOARD_BIND`, `DASHBOARD_PORT`); em produção, nunca em todas as interfaces.
- **Guardas de produção:** a subida falha com demonstração, emissor ou `redirect_uri` sem https, origem pública ausente, segredo de sessão curto, proxy não configurado, escuta aberta, caminho relativo ou trava do DPO não declarada; Flyway `clean` desligado.
- **Rastreabilidade:** versão `AAAA.MM.N` no pacote e em `/api/meta`.
- **Pacote:** zip reprodutível com checksum SHA-256, sem config real, sem segredo, sem banco.
- **Publicação:** job do CI que cria o release do GitHub só em tag, depois dos testes.
- **Serviço do SO:** Windows (NSSM ou Agendador de Tarefas) e Linux (systemd), com conta sem privilégio, pasta de dados separada do pacote e ACLs mínimas.
- **Segredos:** em `painel.env` com permissão restrita (ou cofre da empresa), documentados, com verificação de que não vão para o log.
- **Atualização e reversão:** parar o serviço, backup do banco, trocar o ponteiro `atual`, subir, fumaça; reversão volta o pacote anterior (e restaura o backup quando a migração não tem volta); ensaio automatizado.
- **Homologação e produção:** runbook com fumaça; produção com a flag do Jira desligada.

### Não objetivos

- Ligar a flag do Jira, validar números ou calibrar heurísticas (Fase 14).
- Observabilidade, backup agendado, validador de configuração e runbooks diários (Fase 12; esta fase só consome).
- CI, CSP e CSRF (Fase 9; esta fase só acrescenta o job de publicação).
- Alta disponibilidade, múltiplos painéis ou balanceamento: só um painel por arquivo de banco (`TravaDeProcesso`).
- Terminar TLS dentro do painel (exigiria suporte a keystore); o TLS é do proxy da empresa.
- Imagens, contêineres, orquestradores e registros de imagem (proibidos pelo adendo).

### Antiobjetivos (falha mesmo que "funcione")

- Login que casa uma pessoa com outra (estratégia em cascata, domínio ignorado sem regra).
- Papel vindo de claim do token; id reservado (`admin`, `root`...) ganhando papel.
- Acreditar em `X-Forwarded-*` de qualquer origem, ou em entradas de `X-Forwarded-For` que o cliente forjou.
- Produção com modo de desenvolvimento, demonstração, emissor `http` ou porta aberta a todas as interfaces.
- Serviço rodando como administrador, `LocalSystem` ou root; `painel.env` legível por todos.
- Host interno, IP, usuário real, nome de conta, client id ou segredo no repositório; config real, `painel.env` ou banco dentro do pacote.
- Teste automático falando com provedor, proxy, GitHub ou Jira reais.
- Criar tag, instalar serviço, mudar firewall ou ligar a flag sem o "sim" do usuário.
- Trocar o pacote com o serviço no ar, atualizar sem backup do banco ou sobrescrever a pasta da versão em uso.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Com `false`, ninguém acessa o Jira por caminho nenhum; com a flag ligada pelo usuário, o acesso fica liberado, seguindo o checklist da primeira conexão. **Só o usuário muda a flag.** A flag vale somente no arquivo de variáveis (em produção, o `painel.env` do servidor); variável de terminal ou do sistema é ignorada. Nenhum teste automático acessa o Jira real.

**Visibilidade por papel.** USUARIO vê o seu; SQUAD_LEADER, o seu squad; COORDENADOR e GERENTE, todos. O papel vem do `RoleDirectory` vigente. Integridade só do gerente, sempre auditada.

**Sem ranking de pessoas; pt-BR e inglês; WCAG AA; a cor nunca é o único sinal.** Esta fase só acrescenta um rodapé com o rótulo da versão.

**Configs reais fora do git e do pacote.** Ficam na pasta de dados do servidor, somente leitura para a conta do serviço.

**DPO.** Auditoria e Integridade com dados reais só depois do aceite do DPO (trava do Plano 11). A subida em produção exige a trava declarada.

**Plataforma.** JDK 21 no servidor; SQLite em arquivo (`DATABASE_PATH`); sem Docker, sem servidor de banco; banco de teste é SQLite real em arquivo temporário (`BancoEmArquivo`); comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- Hosts em documentos e testes só `.invalid` ou marcadores `<...>`; IPs só de `127.0.0.1`, `::1` e das faixas de documentação (`192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`, `10.0.0.0/24`).
- O arquivo de variáveis de produção chama-se `painel.env` e só existe no servidor; o repositório traz apenas modelos (`deploy/modelos/painel.env.exemplo`, `app/.env.example`).
- Rede permitida para montar o pacote (espelhos Maven/Gradle e npm); GitHub só nas etapas autorizadas; ambiente da empresa só nas etapas de runbook.
- Flyway só avança; `cleanDisabled(true)` fora dos testes; reversão = pacote anterior (+ backup do banco quando a migração não tem volta).
- Variáveis novas com prefixo `DASHBOARD_` ou `OIDC_`.
- Scripts sem senha em argumento nem em log; PowerShell é o principal, com equivalente `sh`.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | Login de produção por SSO/OIDC genérico atrás de interface; provedor real antes de dados reais | [usuário/design] spec mestra 4.3 e 4.6 |
| U2 | Painel como processo JVM com SQLite embutido, sem Docker nem servidor de banco; entrega por pacote e serviço do SO; porta só em `127.0.0.1` atrás do proxy da empresa com TLS | [usuário] 26/10 e adendo de 27/10 |
| U3 | Flag do Jira: única trava; só o usuário muda | [usuário] `CLAUDE.md`, 25/10 |
| U4 | Repositório privado; nenhum dado interno publicado | [usuário] guia, seção 6 |
| D1 | Uma estratégia de username por instalação, sem cascata | [valor padrão, reversível] |
| D2 | `X-Forwarded-*` só da faixa `DASHBOARD_PROXY_CONFIAVEL`; `X-Forwarded-For` lido da direita | [valor padrão, reversível] |
| D3 | Versão em `/api/meta` (`versao`, `rotuloDeVersao`), sem commit nem data; substitui a decisão de 25/10 de manter `/api/meta` intacta | [reescrita de 27/10, reversível] |
| D4 | Versão `AAAA.MM.N`; tag `vAAAA.MM.N`; pacote `painel-AAAA.MM.N.zip` com `.sha256` | [valor padrão, reversível] |
| D5 | Cada versão numa pasta; `atual` aponta para a ativa; atualização e reversão trocam o ponteiro | [valor padrão, reversível] |
| D6 | Escuta padrão em `127.0.0.1:8080` em todos os modos; produção recusa escuta em todas as interfaces e caminhos relativos | [valor padrão, reversível] |
| D7 | Atualização sem reversão automática; `-RestaurarBanco` obrigatório quando a versão nova declarar migração sem volta | [valor padrão, reversível] |
| D8 | Segredos em `painel.env` com permissão restrita como padrão; cofre como opção | [valor padrão, reversível] |
| D9 | Entrega do pacote por cópia do zip como padrão (sem token do GitHub no servidor) | [valor padrão, reversível] |
| D10 | Produção sobe com `JIRA_LIVE_ENABLED=false` | [decorre de U3] |

## 5. O que fica para quem implementa

- Biblioteca de leitura de CIDR (ou código próprio pequeno) e a forma de ajustar `call.request.origin` no Ktor 3.3.1 (`mutableOriginConnectionPoint`; conferir o módulo).
- Formato interno do `versao.properties` e a forma de o Gradle gerá-lo e de `montarPacote` montar o zip.
- Texto dos scripts e dos runbooks, desde que cada etapa tenha "como conferir" e os testes estáticos do plano passem.
- Nomes que os Planos 8, 9, 11 e 12 deram às capacidades que esta fase consome (tabela da seção 8).
- Escolhas em aberto: registrar `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-13-implantacao-real.md`.

## 6. Critérios de aceitação verificáveis

1. **Decisões (T1).** `ImplantacaoDocTest.decisoesTemCadaPergunta` e `decisoesSemHostReal` passam; as cinco respostas estão no documento com data.
2. **Username (T2).** `.\gradlew.bat test --tests '*ExtratorDeUsernameTest*' --tests '*OidcTest*'` passa, incluindo `keycloakPreferredUsername`, `entraUpnParteLocal`, `adfsContaWindows`, `estrategiaNaoCasaComOutraPessoa`, `idReservadoNaoViraPapel`, `estrategiaInvalidaNaoSobe` e `claimDePapelIgnorada`.
3. **Proxy (T3).** `ProxyConfiavelTest` passa: `forwardedDeOrigemNaoConfiavelIgnorado`, `forwardedDoProxyAceito`, `xffForjadoPeloClienteNaoVence`, `redirectUriNuncaVemDeCabecalho`, `cookieSecureAtrasDoProxy`.
4. **Escuta e guardas (T4).** `GuardasDeProducaoTest` passa, incluindo `escutaAbertaNaoSobe`, `caminhoRelativoNaoSobe`, `listaTodasAsViolacoes`, `mensagemSemValor`, `servidorEscutaSoNoLoopback` e `flywayCleanDesligado`.
5. **Versão (T5).** `VersaoDoPacoteTest` e `MetaTest` passam (`metaTemVersao`, `metaSemCommitNemCaminho`); `Shell.test.tsx` mostra o rodapé; `-Pversao=2026.13.1` falha o build.
6. **Pacote (T6).** `PacoteDeImplantacaoTest` passa; `empacotar.ps1 -Versao 2026.11.1` termina com `PACOTE_OK`; a prova negativa (config real ou `.env` acrescentado ao zip) faz `verificar-pacote` falhar.
7. **Publicação (T7).** `WorkflowPublicacaoTest` e `CiWorkflowTest` passam; a resposta do usuário sobre a tag está registrada.
8. **Serviço (T8).** `ServicoTest` passa; `instalar-servico.ps1 ... -WhatIf` lista os passos sem executar; na máquina, `verificar-instalacao` termina com `INSTALACAO_OK`.
9. **Segredos (T9).** `segredosCitaCadaVariavelSecreta`, `envExampleSemValor` e `aclDoArquivoDeVariaveis` passam; na máquina, `verificar-logs` termina com `LOG_LIMPO`.
10. **Atualização e reversão (T10).** `ScriptsDeImplantacaoTest` e `ReversaoComBackupTest` passam; `ensaio-troca` termina com `TROCA_OK` e `REVERSAO_OK`; `atualizar` segue a ordem parar, backup, trocar, iniciar, fumaça.
11. **Fumaça (T11, T12).** `ensaio-local` termina com `ENSAIO_LOCAL_OK`; `fumaca` termina com `FUMACA_OK` em homologação e em produção (registro no `HANDOFF.md` sem host).
12. **Documentos (T13).** `implantacaoSemHostRealNemSegredo` e `implantacaoSemPlataformaAntiga` passam; suítes completas, `pnpm build` e o teste do hook verdes.
13. **Fim.** Varredura de dados pessoais e hosts antes do push; PR para a `main`; mescla pelo fluxo de entrega (`docs/superpowers/fluxo-de-entrega.md`, 7 condições).

## 7. Riscos, pendências e pontos de parada

**Pontos de parada (perguntar ao usuário, com opções; a recomendada primeiro):**
- **Provedor OIDC** (T1): Keycloak (Recomendada, se existir), Entra ID, ADFS, outro OIDC padrão.
- **Máquina e gerenciador** (T1): Windows com NSSM (Recomendada), Windows com Agendador de Tarefas, Linux com systemd.
- **Proxy e TLS** (T1): proxy da empresa na mesma máquina, painel em `127.0.0.1` (Recomendada); proxy em outra máquina com firewall; TLS no painel (não recomendada, exigiria código novo).
- **Segredos** (T1): arquivo `painel.env` com permissão restrita (Recomendada), cofre da empresa.
- **Entrega do pacote** (T1): cópia manual do zip e do `.sha256` (Recomendada), download do release com `gh`.
- **Criar a tag e o release** (T7), **instalar em homologação** (T11), **subir em produção** (T12).
- **Flag do Jira em produção** (T12): manter `false` e seguir a Fase 14 (Recomendada); o usuário liga agora; manter desligada.

**Riscos:**
- Username do provedor diferente do Jira para parte das pessoas (contas com sufixo). Mitigação: a T2 recusa em vez de adivinhar; o Plano 14 confere os ids de `papeis.yaml`.
- Proxy que não substitui `X-Forwarded-For` ou não envia `X-Forwarded-Proto`. Mitigação: leitura da direita para a esquerda, `redirect_uri` fixo em configuração e fumaça que confere o `Location` do login; `proxy-e-tls.md` diz o que pedir.
- Conta de serviço sem o direito "fazer logon como serviço" ou política de grupo do PC corporativo impedindo `New-Service`/NSSM/tarefa. Mitigação: três gerenciadores previstos; `-WhatIf` e o ensaio em homologação antes de produção; Plano 0.
- `painel.bat` não responde ao gerenciador de serviços do Windows. Mitigação: NSSM (ou Agendador de Tarefas); `New-Service` só para registrar um wrapper.
- Migração nova incompatível com o pacote anterior. Mitigação: backup antes de atualizar (T10), declaração na PR (T7) e `-RestaurarBanco`.
- Dois painéis no mesmo banco por erro de operação. Mitigação: `TravaDeProcesso` recusa a segunda subida; os scripts param o serviço antes de trocar o pacote.
- Nomes dos Planos 8, 9, 11 e 12 diferentes dos previstos. Mitigação: "Estado herdado" e `Ruling:`.

## 8. Glossário do que esta fase consome

| Nome | Arquivo | Origem |
|---|---|---|
| `main()` (`PAINEL_ENV_FILE`, `ArquivoEnv`, `Settings.fromEnv`, `AppContext.load`), `MetaResposta`, `module(...)` | `Application.kt` | [main] Planos 1, 3A e 4A |
| `Settings`, `ArquivoEnv`, `Database.migrate`, `TravaDeProcesso`, `AppContext` | `config/`, `db/`, `AppContext.kt` | [main] Planos 3A e a troca para SQLite (3B) |
| `rootProject.name = "painel"`, plugin `application`, `installDist` | `app/backend/build.gradle.kts` | [main] |
| `BancoEmArquivo`, `testContextComBanco`, `TestPaths` | `src/test/kotlin/br/sidia/painel/` | [main] |
| `OidcSettings`, `OidcAuthenticator`, `rotasDeLogin`, `painel_sessao`, `SESSION_SECRET` | `security/` | [Plano 8] T2 |
| `ROTAS_PUBLICAS`, `InventarioDeRotasTest` | `src/test/.../security/` | [Plano 8] T1 |
| `docs/implantacao.md` e a verificação do pacote | `docs/`, `app/scripts/` | [Plano 8] T7 |
| `.github/workflows/ci.yml`, `CiWorkflowTest`, `CabecalhosDeSeguranca`, `ProtecaoCsrf`, `DASHBOARD_ORIGEM_PUBLICA` | `.github/workflows/`, `http/` | [Plano 9] T2 a T5 |
| `LgpdSettings`, `TravaDpo`, `DASHBOARD_DPO_ACEITE` | `lgpd/` | [Plano 11] T1 |
| Readiness, `painel validar-config`, logs JSON, métricas, backup do SQLite e restauração, rotação de segredos | backend, `app/scripts/`, `docs/operacao/` | [Plano 12] (confirmar nomes) |
| `DASHBOARD_DEMO_DATA` | `config/Settings.kt` | [main] Plano 4A, T10 |

## 9. Referência ao plano

Execute [`2026-10-25-plano-13-implantacao-real.md`](../plans/2026-10-25-plano-13-implantacao-real.md) tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). Os pontos de parada da seção 7 bloqueiam só as etapas que dependem deles; as demais tarefas seguem. As etapas que mexem fora do repositório (tag, homologação, produção) só acontecem depois do merge, que o usuário manda.
