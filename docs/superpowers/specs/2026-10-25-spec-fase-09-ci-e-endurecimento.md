# Spec da Fase 9: CI e endurecimento

Data: 25/10/2026 (reescrita em 27/10/2026 para SQLite embutido, sem Docker, sem PostgreSQL, sem Testcontainers)
Plano: [`docs/superpowers/plans/2026-10-25-plano-09-ci-e-endurecimento.md`](../plans/2026-10-25-plano-09-ci-e-endurecimento.md) (12 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.6 "Produção × desenvolvimento" e "Ambiente", 10)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (lacunas C1, A3 e M8; tabela B.4)
Adendo de plataforma: [`docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`](../plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md) (vale sobre qualquer trecho antigo)
Depende de: Plano 4A mesclado na `main` (já está). Não depende do Plano 4; a ordem entre os dois é o ponto de parada 1.

> **Precedência:** `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > adendo de plataforma > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira (autorização por consulta), substituída em 25/10 pela flag única, e fala de PostgreSQL e Docker, substituídos em 26/10 pelo SQLite embutido.

## 1. Intenção e porquê

Vários planos vão ser executados e mesclados por outra IA. Hoje nada impede que um deles quebre a suíte, publique um nome real ou um token, ou desligue uma trava: **não existe CI** (o repositório não tem a pasta `.github/`, nem no histórico recente). A regra "a `main` está sempre verde" depende de alguém lembrar de rodar tudo à mão.

Ao mesmo tempo, as revisões dos Planos 1 a 3A deixaram itens Minor que, juntos, viram risco real assim que o painel sair de `127.0.0.1`:
- sem CSP nem `X-Frame-Options` (clickjacking);
- sem proteção CSRF (o Plano 7 cria o primeiro `POST`, e o Plano 8 cria o logout);
- a SPA não sabe o que fazer com uma sessão vencida;
- o pacote instalado (`installDist`), iniciado sem o arquivo de variáveis, sobe em modo desenvolvimento, ou seja, **todos os dados sem login**, porque o padrão de `DASHBOARD_DEV_MODE` é `true`;
- nada impede que um PR reintroduza Docker, PostgreSQL ou Testcontainers, que a empresa bloqueia.

Esta fase cria a barreira automática primeiro e fecha esses riscos antes que os planos seguintes construam em cima deles. O CI usa **só JDK 21 e Node 22/pnpm**: o banco dos testes é um arquivo SQLite temporário, então não há serviço, contêiner nem imagem em lugar nenhum.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **CI:** workflow do GitHub Actions com quatro jobs (`backend` com `./gradlew test`; `frontend` com lint, `pnpm vitest run`, `pnpm build` e verificação de CSP; `guarda` com os testes dos hooks `.claude/hooks/test-guard.sh` e `.clinerules/hooks/test-PreToolUse.sh`; `varredura` de dados pessoais e segredos), que roda **em todo pull request** e em todo push na `main` e falha o PR se qualquer um falhar.
- **Exigido antes do merge:** os quatro checks são exigidos pela proteção da `main`, que o **usuário** aplica no GitHub (passo a passo em `docs/operacao/ci.md`). A IA não altera configuração do repositório.
- **Varredura de dados pessoais e segredos:** um script que roda igual no CI e na máquina (inclusive no Git Bash do Windows), olha o que o PR acrescenta e as mensagens de commit, e falha sem nunca imprimir o que achou.
- **Cabeçalhos de segurança:** CSP estrita e os demais cabeçalhos em toda resposta, sem duplicar o que o servidor estático já escreve.
- **CSRF:** proteção em todo método que altera estado, presente e futuro, com código de erro claro.
- **Sessão expirada:** um 401 na SPA mostra um aviso com volta segura ao login; o `fetch` fica num único arquivo.
- **Pacote instalável seguro:** o pacote `installDist` sobe em modo produção quando ninguém define o modo, e o modelo `app/.env.example` nunca traz o modo dev ligado (teste de documentação), no lugar do antigo "imagem com modo dev desligado".
- **Plataforma protegida:** um teste impede o retorno de Docker, compose, imagem, PostgreSQL, H2 e Testcontainers.
- **Minor técnicos que ainda valem:** acesso público por lista de permissão, `module` sem parâmetro enganoso, servidor estático (ocultos, symlink, HEAD), busca só com `JqlQuery` e varredura da fábrica sem brechas.

### Não objetivos

- Implantação real como serviço do SO, publicação do pacote, provedor OIDC, proxy e TLS (Plano 13).
- Observabilidade, backup do SQLite e validador de configuração (Plano 12).
- Identidade visual, piscada do tema e cor da marca (Plano 15).
- Aplicar a proteção da `main` no GitHub: o plano só documenta. Aplicar é efeito fora do repositório e cabe ao usuário (ponto de parada 3).
- Resolver SHAs, criar segredos ou ligar qualquer opção de segurança do repositório.

### Antiobjetivos (falha mesmo que "funcione")

- Um job de CI ou um teste que acesse o Jira, ou um workflow que declare segredo `JIRA_*`.
- Qualquer serviço, contêiner, imagem, Docker, PostgreSQL, H2 ou Testcontainers no workflow, no build ou nos testes.
- Uma varredura que imprime no log do CI o nome, o e-mail ou o token que encontrou.
- CSP afrouxada com `unsafe-inline` ou `unsafe-eval` para "fazer funcionar".
- Proteção CSRF desligada, contornada com `continue-on-error`, ou isenta para uma rota por conveniência de teste.
- Um job com nome diferente do check exigido, ou com filtro de `paths`, que faria a proteção da `main` esperar para sempre (ou ser contornada).
- Mudar a assinatura do `module` de forma que os planos seguintes deixem de compilar.
- Mudar o padrão de `DASHBOARD_DEV_MODE` do desenvolvimento sem decisão do usuário (ponto de parada 5).
- Uma migração nova nesta fase.
- Texto de plano, spec, teste ou documento que grave a flag do Jira ligada em forma de atribuição, ou que cite o arquivo de variáveis real.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`). Com ela desligada, ninguém acessa o Jira por caminho nenhum: app, agendador, IA, pessoa, MCP, `curl` ou script. Só o usuário muda a flag, e ela vale somente no arquivo de variáveis do app (variável de terminal ou de sistema com o mesmo nome é ignorada). Testes e CI usam sempre fixtures e `MockEngine`; o workflow fixa a flag em `"false"` e não declara segredo `JIRA_*`.

**Dados pessoais.** Fixtures, testes, docs e commits só com nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`). Configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, o arquivo de variáveis real, arquivos de banco) nunca entram no git nem no pacote. O repositório continua **PRIVADO**, porque o histórico tem usernames reais.

**Segredos.** Token, senha e URL com credencial nunca aparecem em `toString`, log, mensagem de erro, resposta HTTP, relatório nem log de CI.

**Fail-closed.** Configuração obrigatória ausente derruba a subida em produção; só o modo dev cai nos modelos `.exemplo`.

**Plataforma.** JDK 21 e Node 22 com pnpm; banco SQLite em arquivo (`app/data/painel.db`); testes com SQLite real em arquivo temporário (`BancoEmArquivo`); nunca Docker, servidor de banco, H2 ou mock de banco. Comentários e commits em pt-BR. Executor principal: Windows 11 PowerShell; os comandos têm sempre a forma PowerShell e a forma `sh`.

### 3.2 Restrições específicas desta fase

- **Rede:** permitida para registros de pacotes (Maven Central, plugins do Gradle, npm) e para o GitHub (`gh`, Actions). Nunca para o Jira nem para provedor OIDC real.
- **Versões:** Node 22, pnpm 9.12.3 via corepack e JDK 21 (Temurin). Nenhuma dependência npm ou Gradle nova (por isso o `HEAD` do servidor estático é uma rota própria, sem o plugin `AutoHeadResponse`).
- **Workflow:** `permissions: contents: read`; sem `pull_request_target`; sem `${{ }}` dentro de `run:`; checkout sem credencial persistida; o único segredo é o opcional `VARREDURA_NOMES`.
- **Variáveis novas** começam com `DASHBOARD_` ou `PAINEL_`. Esta fase cria `DASHBOARD_ORIGEM_PUBLICA` (opcional) e a propriedade de JVM `painel.perfil` (fixada só pelos scripts do pacote).
- **Sem migração.** O esquema só tem a `V1__base.sql`; a próxima livre continua sendo a `V2`.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava de acesso ao Jira; só o usuário a liga | [usuário, 25/10] |
| U2 | Configs reais fora do git e do pacote; repositório privado | [usuário] |
| U3 | Sem Docker, sem PostgreSQL, sem Testcontainers; banco SQLite embutido | [usuário, 26/10] |
| D1 | **CI no GitHub Actions, runner `ubuntu-latest` hospedado pelo GitHub**, só JDK 21 e Node 22/pnpm, com quatro jobs: `backend`, `frontend`, `guarda`, `varredura`; roda em todo PR e em push na `main` | [valor padrão, reversível; ponto de parada 2] |
| D2 | **Varredura:** linhas adicionadas no diff do PR e mensagens de commit; categorias `ARQUIVO_PROIBIDO` (inclui arquivos de banco local), `EMAIL`, `SEGREDO`, `HOST_INTERNO`, `NOME_PROIBIDO`; nomes proibidos só em `scripts/ci/nomes-proibidos.local.txt` (git-ignorado) ou no segredo `VARREDURA_NOMES`; saída só com categoria, arquivo e linha | [valor padrão, reversível] |
| D3 | **CSRF:** cabeçalho `X-Painel-CSRF: 1` obrigatório em POST, PUT, PATCH e DELETE, mais `Sec-Fetch-Site` e `Origin` conferidos quando presentes; 403 com `ErroDto("CSRF_AUSENTE")` ou `ErroDto("ORIGEM_INVALIDA")`; negação registrada em `access_log` | [valor padrão, reversível] |
| D4 | **CSP:** `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`; `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP `same-origin`; `no-store` na API; HSTS só fora do modo dev | [valor padrão, reversível] |
| D5 | **Pacote instalável:** `bin/painel` e `bin/painel.bat` passam `-Dpainel.perfil=producao`; nesse perfil a ausência de `DASHBOARD_DEV_MODE` vale `false`; `iniciar.*` e `./gradlew run` continuam em modo dev. O modelo `app/.env.example` não liga modo dev nem demonstração (teste de documentação). Substitui o antigo "imagem com modo dev desligado, digests e `.dockerignore` por lista de permissão" | [valor padrão, reversível; ponto de parada 5] |
| D6 | **`module` compatível:** o primeiro parâmetro vira `Settings? = null`, sem remover nem reordenar | [valor padrão, reversível] |
| D7 | **Acesso público por lista de permissão:** só `rotasPublicas`, a raiz e a casca estática com segmentos `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` (no máximo 8 segmentos, nunca `api` ou `auth` no primeiro); `/auth/*` só é público se estiver em `rotasPublicas` | [valor padrão, reversível] |
| D8 | **Minor tratados aqui:** `settingsIgnorado` opcional; inverter `exigeAutenticacao`; `resolverDentro` com symlink; arquivos ocultos do `dist`; HEAD em estático; CSP e `X-Frame-Options`; `SearchRequest` só com `JqlQuery`; varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado). **Já resolvidos antes, com teste:** `delay` do retry com cancelamento (`delayDoRetryRespeitaCancelamento`), máscara do token escapado em JSON, URL, HTML e base64 (`padraoEscapado`), mensagem de exceção com CR/LF (`amostra`). **Descartados por serem só de Docker:** `.dockerignore` por lista de permissão, imagens por digest, imagem com `DASHBOARD_DEV_MODE=true` (o equivalente é a D5), `RetentionTest` intermitente (era "Could not connect to Ryuk" do Testcontainers; deixou de existir com a saída do Docker). **Ficam para outros planos:** contraste de `--line` (Plano 8, T4); piscada do tema e `#1329A0` × `#0023A4` (Plano 15); `jira_sidia` (futuro) | [valor padrão, reversível] |
| D9 | **Actions** por SHA de commit quando a execução conseguir resolvê-lo com `gh`; senão por tag maior (`@v4`), com `Ruling` | [valor padrão, reversível] |
| D10 | **Checks exigíveis:** nome do job igual ao id, sem `paths`, gatilhos `pull_request` (sem filtro) e `push` na `main`; teste estático garante | [valor padrão, reversível] |
| D11 | **Barreira de plataforma:** `PlataformaSemDockerTest` falha se aparecer arquivo de contêiner, dependência de servidor de banco ou de Testcontainers, ou `docker`/`services` no workflow | [valor padrão, reversível] |

## 5. O que fica para quem implementa

- A forma interna dos scripts (POSIX `sh`, `grep -E` com classes POSIX, `git`), desde que passem nos casos de `test-varredura.sh` e nunca imprimam o termo achado.
- Os hosts públicos acrescentados a `padroes-permitidos.txt` depois da primeira rodada `--arvore`. Só hosts públicos; host da empresa ou dado pessoal é ponto de parada 4.
- Como percorrer a árvore de rotas do Ktor no `todoMetodoMutanteExigeCsrf` (a mesma técnica que o Plano 8, T1 vai usar).
- Como o `HEAD` do servidor estático responde sem corpo, desde que sem dependência nova.
- O texto do aviso de sessão expirada em inglês, desde que equivalente ao pt-BR do plano.
- Como o teste do pacote executa o processo no Windows (`cmd /c`) e no Linux, desde que o teste falhe fechado e termine em até 60 s.

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Workflow com quatro jobs, flag fixada em `false`, só leitura, versões fixas, sem serviços nem contêiner | `CiWorkflowTest` (T2, T3) |
| Roda em todo PR e em push na `main`; nome do job = check exigível; sem filtro de caminho | `gatilhosSaoPullRequestEPushNaMain`, `nomesDosJobsSaoOsChecksObrigatorios` (T2) |
| O primeiro PR desta fase roda os quatro jobs no GitHub e todos ficam verdes | T12, Passo 6 (link do PR no progresso) |
| Os quatro checks são exigidos antes do merge | Ação do usuário (ponto de parada 3); conferência com `gh api repos/{owner}/{repo}/rules/branches/main` e PR de teste (T2, T12) |
| A varredura acha e-mail, segredo, host interno, arquivo proibido (inclusive banco local) e nome proibido, e não imprime o termo | `test-varredura.sh`: `emailRealFalha`, `tokenFalha`, `hostInternoFalha`, `arquivoProibidoFalha`, `bancoLocalFalha`, `nomeProibidoPorVariavel`, `naoImprimeOTermoEncontrado` (T1) |
| O e-mail de autor e as linhas removidas não contam | `emailDeAutorIgnorado`, `linhaRemovidaNaoConta` (T1) |
| Toda resposta tem CSP e os demais cabeçalhos, uma vez cada; HSTS só em produção | `todaRespostaTemOsCabecalhos`, `cabecalhosAparecemUmaVez`, `hstsSoForaDoDevMode` (T3) |
| O build do frontend é compatível com a CSP | `verificar-csp.mjs` no CI; `verificar-csp.test.ts` (T3) |
| Método mutante sem cabeçalho, de outra origem ou cross-site → 403 com código, handler não roda, `access_log` gravado | `postSemCabecalhoEh403SemExecutar`, `origemDeOutroSiteEh403`, `secFetchSiteCrossSiteEh403`, `negacaoGravaAccessLog` (T4) |
| Toda rota mutante futura fica protegida | `todoMetodoMutanteExigeCsrf` (T4) |
| 401 mostra "sessão expirada" com volta só para caminho interno; 403 não; só `client.ts` tem `fetch` | `respostas401DisparamSessaoExpirada`, `respostas403NaoDisparam`, `voltaSoAceitaCaminhoInterno`, `nenhumFetchForaDoCliente` (T5) |
| O pacote `installDist` sobe em modo produção por padrão, falha fechado sem configuração real e não leva config nem banco | `PerfilDeExecucaoTest`, `PacoteInstalavelTest` (`scriptsFixamOPerfilDeProducao`, `pacoteSemConfiguracaoFalhaFechado`, `pacoteNaoLevaConfigNemBanco`) (T6) |
| O modelo de variáveis nunca liga o modo dev e o pacote lido com ele sobe em produção | `ModeloDeVariaveisTest`: `modeloNaoLigaModoDevNemDemonstracao`, `modeloSobeEmProducaoNoPacote` (T7) |
| Formas não previstas de URL exigem login; a SPA continua carregando | `formasNaoPrevistasExigemLogin`, `casaDaSpaContinuaPublica` (T8) |
| Symlink para fora e arquivo oculto não são servidos | `symlinkParaForaDaRaizEhNulo`, `arquivoOcultoDaSpa404` (T8) |
| `module` compila nas duas formas | `moduleSemSettingsCompila`, `moduleComSettingsPosicionalCompila` (T8) |
| Busca só com `JqlQuery`, inclusive a conferência de instância | `buscaSoAceitaJqlQuery`, `buscaDeOutraInstanciaPorSearchRequestEhRecusada` (T9) |
| A varredura da fábrica pega alias, `typealias`, referência e `copy` aninhado | `VarreduraDeFontesTest` (T10) |
| Docker, compose, PostgreSQL, H2 e Testcontainers não voltam | `PlataformaSemDockerTest` (T11) |
| Travas existentes continuam verdes | `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest`, `test-guard.sh`, `test-PreToolUse.sh` (T9, T10, T12) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1: perguntar ao usuário — ordem de execução (antes da T1).** O índice dos planos manda executar o Plano 9 antes do Plano 4; o `HANDOFF.md` manda continuar o Plano 4 pela Tarefa 4 e só depois o Plano 9. Opções:
1. **Plano 9 agora, antes de continuar o Plano 4 (Recomendada).** Por quê: cada PR seguinte já nasce com CI, varredura, CSRF e CSP. Consequência: a Tarefa 4 do Plano 4 espera este PR.
2. **Terminar o Plano 4 e só depois o Plano 9.** Por quê: não interrompe o que está em andamento. Consequência: os PRs restantes do Plano 4 saem sem CI, e a lista de rotas da T4 deste plano muda.
3. **Em paralelo, em outra branch.** Por quê: duas frentes. Consequência: conflitos em `Application.kt` e `build.gradle.kts`; quem mesclar depois reconcilia.

Sem resposta, seguir a opção 1.

**PONTO DE PARADA 2: perguntar ao usuário — onde o CI roda (T2, antes do Passo 3).** Opções:
1. **GitHub Actions com runner `ubuntu-latest` hospedado pelo GitHub (Recomendada).** Por quê: sem infraestrutura nova; o job só precisa de JDK 21 e Node 22. Consequência: o repositório privado consome minutos da cota da conta (poucos minutos por PR); o código passa pelos servidores do GitHub, onde já está hospedado.
2. **Opção 1 mais um job do backend em `windows-latest`.** Por quê: o executor trabalha em Windows 11; pega diferença de caminho, fim de linha e link simbólico. Consequência: o dobro de minutos no job do Windows; o nome do check muda e a proteção da `main` precisa listar os dois.
3. **Runner self-hosted da empresa.** Por quê: só se a política da empresa proibir runners externos. Consequência: alguém da infraestrutura instala e mantém o runner (JDK 21, Node 22, `jq`); atrasa o primeiro CI verde.
4. **Sem CI no GitHub; só um script local `sh scripts/ci/rodar-tudo.sh`.** Por quê: custo zero. Consequência: a barreira volta a depender de alguém lembrar de rodar; a lacuna C1 continua aberta.

Sem resposta, seguir a opção 1, que é reversível.

**PONTO DE PARADA 3: perguntar ao usuário — proteção da `main` (T2 e T12, Passo 7).** Aplicar a regra no GitHub é efeito fora do repositório e é **ação do usuário**; a IA nunca a aplica. O passo a passo (conjunto de regras *ruleset* da `main`, checks `backend`, `frontend`, `guarda` e `varredura` obrigatórios, PR obrigatório, sem push direto nem exclusão, ramo atualizado antes de mesclar) está em `docs/operacao/ci.md`. Opções:
1. **O usuário aplica pelo GitHub com o passo a passo (Recomendada).** Por quê: a regra só vale se os quatro checks forem obrigatórios. Consequência: nenhum PR entra na `main` com CI vermelho, nem por engano da IA.
2. **O plano da conta não permite a regra em repositório privado: seguir só por processo.** Por quê: sem alternativa técnica (e tornar o repositório público está fora de questão por causa do histórico). Consequência: o CI avisa, mas não impede; a IA só mescla depois de conferir `gh pr checks` verde (condição 5 do fluxo de entrega), por disciplina; registrar no `HANDOFF.md`.
3. **Deixar sem proteção por enquanto.** Por quê: menos atrito enquanto só uma pessoa mescla. Consequência: igual à opção 2, sem o hábito de conferir; registrar no `HANDOFF.md`.

**PONTO DE PARADA 4: perguntar ao usuário — achado de dado pessoal no conteúdo atual (T1, Passo 4).** Se a varredura `--arvore` achar nome real, e-mail real ou host da empresa em arquivo já versionado, a IA não corrige sozinha nem copia o termo. Opções:
1. **Remover do arquivo atual numa tarefa à parte e registrar o histórico como pendência (Recomendada).** Consequência: a árvore fica limpa; o histórico continua com o dado (repositório privado).
2. **Acrescentar uma exceção pontual em `padroes-permitidos.txt`.** Consequência: só se o usuário confirmar que não é dado pessoal (por exemplo, vocabulário de campo).
3. **Limpar também o histórico.** Consequência: reescrita de histórico e *force push*, ação irreversível fora deste plano; vira tarefa própria com aprovação explícita.

**PONTO DE PARADA 5: perguntar ao usuário — como o pacote decide o modo (T6, antes do Passo 3).** O padrão de `DASHBOARD_DEV_MODE` é `true` (regra do `CLAUDE.md` para o desenvolvimento). Opções:
1. **Perfil de produção só nos scripts do pacote (Recomendada).** `bin/painel` e `bin/painel.bat` passam `-Dpainel.perfil=producao`; nesse perfil a variável ausente vale `false`. Por quê: protege o serviço instalado sem tocar no desenvolvimento nem nos testes. Consequência: modo dev no pacote exige a linha explícita (com a faixa vermelha).
2. **Mudar o padrão global do `Settings` para `false`.** Por quê: um único comportamento. Consequência: contraria a regra atual, quebra testes e telas que dependem de `Settings()` e obriga `iniciar.*` a definir o modo dev.
3. **Só documentar, sem trava.** Por quê: zero código. Consequência: o pacote sem o arquivo de variáveis sobe com todos os dados sem login.

Sem resposta, seguir a opção 1 (reversível: remover a propriedade do script).

**Impacto nos planos seguintes (não editados aqui):**
- **Planos 7 e 8 (e 16):** os testes de `POST` (`registraUmaVez`, `decisaoDuplicada409`, `trocadaExigeMotivo`, `logoutLimpaSessao`) precisam usar `comCsrf()` de `TestHttp.kt`. Sem isso eles recebem 403 `CSRF_AUSENTE`, e o código do erro diz exatamente o que falta. O frontend deve enviar por `enviarJson`.
- **Plano 8, T1:** `/auth/login` e `/auth/callback` entram na lista `rotasPublicas` de `AccessControl.kt` (D7); a regra "primeiro segmento nunca `auth`" faz com que só as rotas `/auth/*` explicitamente listadas sejam públicas.
- **Plano 8, T2:** `/auth/login?volta=` deve aceitar só caminho interno, com a mesma regra de `caminhoDeVolta` (T5). O `POST /auth/logout` fica coberto pelo CSRF. A T7 do Plano 8 (perfil `prod` do compose, `verificar-imagem.sh`, `ComposeTest`) deixa de existir na forma antiga: o equivalente é o pacote e o teste `PacoteInstalavelTest` desta fase.
- **Plano 12:** o job `backend` pode ganhar um passo que roda `painel validar-config` sobre os modelos de exemplo (o pacote é gerado pelo `installDist` da T6); `PostgresDeTeste` não existe mais; `cadaVariavelDoEnvExampleEstaDocumentada` convive com `ModeloDeVariaveisTest`.
- **Plano 13:** não há "job de publicação de imagem"; se quiser publicar o pacote, acrescenta um job ao mesmo `ci.yml` (com o teste `CiWorkflowTest` atualizado) e usa `PerfilDeExecucao` e `DASHBOARD_ORIGEM_PUBLICA` (obrigatória em produção).
- **Plano 10:** consome `ErroDto`, `ErroDaApi`, `enviarJson`, `getJson` e `EVENTO_SESSAO_EXPIRADA`; as telas novas usam `getJson`, nunca `fetch` direto (o teste `nenhumFetchForaDoCliente` barra).
- **Todos os planos:** um PR só é mesclado com os quatro jobs verdes.

**Riscos:**
- *Minutos de CI do repositório privado.* Mitigação: `timeout-minutes`, `concurrency` com cancelamento do que ficou velho e relatório de testes como artefato só em falha; nunca `continue-on-error`.
- *CSP quebra algo que o teste não vê* (por exemplo, uma biblioteca de gráfico do Plano 10 que injete estilo). Mitigação: `verificar-csp.mjs` no CI e a conferência manual no navegador (T3, Passo 4).
- *Falso positivo da varredura* em documentação pública. Mitigação: `padroes-permitidos.txt` versionado e revisado no PR.
- *`jq` ausente no PC do executor* (testes de hook). Mitigação: `winget install jqlang.jq` ou o `jq` do Git Bash; no CI já existe.
- *Teste de link simbólico no Windows sem privilégio.* Mitigação: o teste se ignora (nunca fica verde por engano) e o CI em Linux o executa.
- *Link "Entrar de novo" sem provedor.* Até o Plano 8, o login real não existe; aceitável porque fora do modo dev nenhuma sessão chega a existir.

**Migrações:** esta fase não cria nenhuma. O esquema só tem a `V1__base.sql`; a próxima livre é a `V2` (confira `app/backend/src/main/resources/db/migration` no momento da execução). Os números `V4` a `V13` que ainda aparecem em planos antigos são apenas rótulos (adendo, seção 7).

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `LiveGuard`, `JiraClientFactory` | trava e única fábrica de clientes Jira | Planos 1 e 3A |
| `FlagDesligadaZeroRequisicoesTest` | prova de 0 requisições com a flag desligada | Plano 4A |
| `test-guard.sh`, `test-PreToolUse.sh` (e `.ps1`) | testes dos hooks do Claude Code e do Cline | Planos 1 e 4A, porte para o Cline |
| `installAccessControl`, `exigeAutenticacao`, `rotasPublicas` | negar por padrão em `/api` | Plano 1 |
| `registrarNegacaoSemPapel`, `AccessLog` | registro de consultas negadas | Plano 3 |
| `spaEstatica`, `resolverDentro` | servidor da SPA | Plano 1 |
| `SearchRequest`, `JqlQuery`, `CardQueryFactory` | busca no Jira e JQL tipado | Planos 2 e 3 |
| `Agendador`, `module(..., agendador)` | agendador da sincronização | Plano 4A |
| `ArquivoEnv`, `Settings.fromEnv`, `main()` | leitura do arquivo de variáveis (a flag do Jira só vale nele) e das variáveis | Plano 3A e saída do Docker |
| `BancoEmArquivo`, `testContextComBanco`, `TestPaths` | testes com SQLite em arquivo temporário | saída do Docker |
| `PreparacaoDocsTest` | modelo de teste de documentação | Plano 2 |
| `ErroDto` (novo) | erro por código, base das mensagens i18n do Plano 10 | esta fase |
| `comCsrf()` (novo) | helper de teste para métodos mutantes | esta fase |
| `PerfilDeExecucao`, `TestPaths.repoDir`, `TestPaths.installDir` (novos) | perfil do pacote instalado e caminhos de teste | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-09-ci-e-endurecimento.md`](../plans/2026-10-25-plano-09-ci-e-endurecimento.md): 12 tarefas, em um PR a partir da `main` atualizada (T1 varredura; T2 workflow; T3 cabeçalhos e CSP; T4 CSRF e `ErroDto`; T5 sessão expirada; T6 pacote em modo produção; T7 modelo de variáveis; T8 acesso por lista de permissão; T9 busca tipada; T10 varredura da fábrica; T11 barreira de plataforma; T12 fechamento e primeiro CI verde).
