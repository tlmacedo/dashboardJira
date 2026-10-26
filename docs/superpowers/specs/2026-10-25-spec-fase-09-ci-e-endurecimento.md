# Spec da Fase 9: CI e endurecimento

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-09-ci-e-endurecimento.md`](../plans/2026-10-25-plano-09-ci-e-endurecimento.md) (11 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 3, 4.6 "Produção × desenvolvimento" e "Ambiente", 10)
Auditoria: [`docs/superpowers/cobertura-do-projeto.md`](../cobertura-do-projeto.md) (lacunas C1, A3 e M8; tabela B.4)
Depende de: Plano 4A mesclado na `main`. Roda **antes** dos Planos 4, 5, 6, 7 e 8.

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano > spec mestra de 24/10. A spec mestra ainda traz a regra antiga de acesso ao Jira (autorização por consulta), substituída em 25/10 pela flag única.

## 1. Intenção e porquê

Seis planos vão ser executados e mesclados por outra IA. Hoje nada impede que um deles quebre a suíte, publique um nome real ou um token, ou desligue uma trava: não existe CI no repositório nem no histórico. A regra "a `main` está sempre verde" depende de alguém lembrar de rodar tudo à mão.

Ao mesmo tempo, as revisões dos Planos 1 a 3A deixaram itens Minor que, juntos, viram risco real assim que o painel sair de `127.0.0.1`:
- sem CSP nem `X-Frame-Options` (clickjacking);
- sem proteção CSRF (o Plano 7 cria o primeiro `POST`, e o Plano 8 cria o logout);
- a SPA não sabe o que fazer com uma sessão vencida;
- a imagem Docker sobe em modo desenvolvimento quando ninguém define a variável, ou seja, todos os dados sem login.

Esta fase cria a barreira automática primeiro e fecha esses riscos antes que os planos seguintes construam em cima deles.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- **CI:** workflow do GitHub Actions com quatro jobs (backend com Testcontainers, frontend com lint, testes, build e verificação de CSP, hook `test-guard.sh` e varredura), que falha o PR se qualquer um falhar.
- **Varredura de dados pessoais e segredos:** um script que roda igual no CI e na máquina, olha o que o PR acrescenta e as mensagens de commit, e falha sem nunca imprimir o que achou.
- **Cabeçalhos de segurança:** CSP estrita e os demais cabeçalhos em toda resposta.
- **CSRF:** proteção em todo método que altera estado, presente e futuro, com código de erro claro.
- **Sessão expirada:** um 401 na SPA mostra um aviso com volta segura ao login.
- **Imagem:** modo produção por padrão, imagens base por digest e `.dockerignore` por lista de permissão.
- **Minor técnicos que ainda valem:** acesso público por lista de permissão, `module` sem parâmetro enganoso, servidor estático (ocultos, symlink, HEAD), busca tipada, espera cancelável, token escapado mascarado, log sem quebra de linha injetada, varredura da fábrica sem brechas e `RetentionTest` estável.

### Não objetivos

- Implantação real, provedor OIDC, proxy e TLS (Plano 13).
- Observabilidade, backup e validador de configuração (Plano 12).
- Identidade visual, piscada do tema e cor da marca (Plano 15).
- Aplicar a proteção da `main` no GitHub: o plano só documenta. Aplicar é efeito fora do repositório e cabe ao usuário (ponto de parada 2).

### Antiobjetivos (falha mesmo que "funcione")

- Um job de CI ou um teste que acesse o Jira, ou um workflow que declare segredo `JIRA_*`.
- Uma varredura que imprime no log público do CI o nome, o e-mail ou o token que encontrou.
- CSP afrouxada com `unsafe-inline` ou `unsafe-eval` para "fazer funcionar".
- Proteção CSRF desligada, contornada com `continue-on-error`, ou isenta para uma rota por conveniência de teste.
- Mudar a assinatura do `module` de forma que os Planos 4 a 8, já escritos, deixem de compilar.
- Uma migração nova nesta fase, que deslocaria a numeração dos planos seguintes (V7 em diante).

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10). Com `false`, ninguém acessa o Jira por caminho nenhum: app, agendador, IA, pessoa, MCP, `curl` ou script. Só o usuário muda a flag. Testes e CI usam sempre fixtures e `MockEngine`; o workflow fixa a flag em `"false"`.

**Dados pessoais.** Fixtures, testes, docs e commits só com nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`). Configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `.env`) nunca entram no git nem na imagem. O repositório continua **PRIVADO**, porque o histórico tem usernames reais.

**Segredos.** Token, senha e URL com credencial nunca aparecem em `toString`, log, mensagem de erro, resposta HTTP, relatório nem log de CI.

**Fail-closed.** Configuração obrigatória ausente derruba a subida em produção; só o modo dev cai nos modelos `.exemplo`.

**Ambiente.** Docker ativo; banco real por Testcontainers, nunca H2 ou mock; comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- **Rede:** permitida para registros de pacotes e imagens (inclusive para descobrir digests) e para o GitHub (`gh`, Actions). Nunca para o Jira nem para provedor OIDC real. É a mesma regra da decisão 4 do Plano 8, antecipada aqui.
- **Versões:** Node 22, pnpm 9.12.3 via corepack, JDK 21 (Temurin) e PostgreSQL 16, as mesmas do `Dockerfile` e do compose. Nenhuma dependência npm ou Gradle nova.
- **Workflow:** `permissions: contents: read`; sem `pull_request_target`; o único segredo é o opcional `VARREDURA_NOMES`.
- **Variáveis novas** começam com `DASHBOARD_`. Esta fase cria só uma: `DASHBOARD_ORIGEM_PUBLICA`.
- **Sem migração.**

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | A flag `JIRA_LIVE_ENABLED` é a única trava de acesso ao Jira; só o usuário a liga | [usuário, 25/10] |
| U2 | Configs reais fora do git e da imagem; repositório privado | [usuário] |
| D1 | **CI no GitHub Actions, runners `ubuntu-latest` hospedados pelo GitHub**, com quatro jobs: `backend`, `frontend`, `guarda`, `varredura` | [valor padrão 25/10, reversível; ponto de parada 1] |
| D2 | **Varredura:** linhas adicionadas no diff do PR e mensagens de commit; categorias `ARQUIVO_PROIBIDO`, `EMAIL`, `SEGREDO`, `HOST_INTERNO`, `NOME_PROIBIDO`; nomes proibidos só em `scripts/ci/nomes-proibidos.local.txt` (git-ignorado) ou no segredo `VARREDURA_NOMES`; saída só com categoria, arquivo e linha | [valor padrão 25/10, reversível] |
| D3 | **CSRF:** cabeçalho `X-Painel-CSRF: 1` obrigatório em POST, PUT, PATCH e DELETE, mais `Sec-Fetch-Site` e `Origin` conferidos quando presentes; 403 com `ErroDto("CSRF_AUSENTE")` ou `ErroDto("ORIGEM_INVALIDA")`; negação registrada em `access_log` | [valor padrão 25/10, reversível] |
| D4 | **CSP:** `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`; `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP `same-origin`; `no-store` na API; HSTS só fora do modo dev | [valor padrão 25/10, reversível] |
| D5 | **Imagem:** `DASHBOARD_DEV_MODE=false` por padrão; o compose local liga o modo dev explicitamente; `FROM` e `postgres` por digest resolvido na execução; `.dockerignore` começa com `*` | [valor padrão 25/10, reversível] |
| D6 | **`module` compatível:** o primeiro parâmetro vira `Settings? = null`, sem remover nem reordenar | [valor padrão 25/10, reversível] |
| D7 | **Acesso público por lista de permissão:** só `rotasPublicas` e a casca estática com segmentos `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$` (no máximo 8 segmentos, nunca `api` ou `auth` no primeiro) | [valor padrão 25/10, reversível] |
| D8 | **Minor tratados aqui** (lista de referência): `.dockerignore` por permissão; `settingsIgnorado`; varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado); inverter `exigeAutenticacao`; `delay` do retry com cancelamento; máscara do token escapado em JSON e URL; `resolverDentro` com symlink; arquivos ocultos do `dist`; HEAD em estático; CSP e `X-Frame-Options`; imagens por digest; `DASHBOARD_DEV_MODE=true` na imagem; `SearchRequest(jql: String)`; `RetentionTest` intermitente; mensagem de exceção com CR/LF (Plano 2). **Ficam para outros planos:** contraste de `--line` (Plano 8, T4); piscada do tema e `#1329A0` × `#0023A4` (Plano 15); `jira_sidia` (futuro) | [valor padrão 25/10, reversível] |
| D9 | **Actions** por SHA de commit quando a execução conseguir resolvê-lo com `gh`; senão por tag maior (`@v4`), com `Ruling` | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- A forma interna dos scripts (POSIX `sh`, `grep -E`, `git`), desde que passem nos casos de `test-varredura.sh` e nunca imprimam o termo achado.
- Os hosts públicos acrescentados a `padroes-permitidos.txt` depois da primeira rodada `--arvore`. Só hosts públicos; host da empresa ou dado pessoal é ponto de parada 3.
- A causa do `RetentionTest` intermitente e a correção, desde que o laço de 10 execuções fique verde com o Ryuk ligado.
- O texto do aviso de sessão expirada em inglês, desde que equivalente ao pt-BR do plano.
- Como percorrer a árvore de rotas do Ktor no `todoMetodoMutanteExigeCsrf` (a mesma técnica que o Plano 8, T1 vai usar).

## 6. Critérios de aceitação verificáveis

| Critério | Prova |
|---|---|
| Workflow com quatro jobs, flag fixada em `false`, só leitura, versões fixas | `CiWorkflowTest` (T2, T3) |
| O primeiro PR desta fase roda os quatro jobs no GitHub e todos ficam verdes | T11, Passo 4 (link do PR no progresso) |
| A varredura acha e-mail, segredo, host interno, arquivo proibido e nome proibido, e não imprime o termo | `test-varredura.sh`: `emailRealFalha`, `tokenFalha`, `hostInternoFalha`, `arquivoProibidoFalha`, `nomeProibidoPorVariavel`, `naoImprimeOTermoEncontrado` (T1) |
| O e-mail de autor e as linhas removidas não contam | `emailDeAutorIgnorado`, `linhaRemovidaNaoConta` (T1) |
| Toda resposta tem CSP e os demais cabeçalhos; HSTS só em produção | `todaRespostaTemOsCabecalhos`, `hstsSoForaDoDevMode` (T3) |
| O build do frontend é compatível com a CSP | `verificar-csp.mjs` no CI; `verificar-csp.test.ts` (T3) |
| Método mutante sem cabeçalho, de outra origem ou cross-site → 403 com código, handler não roda, `access_log` gravado | `postSemCabecalhoEh403SemExecutar`, `origemDeOutroSiteEh403`, `secFetchSiteCrossSiteEh403`, `negacaoGravaAccessLog` (T4) |
| Toda rota mutante futura fica protegida | `todoMetodoMutanteExigeCsrf` (T4) |
| 401 mostra "sessão expirada" com volta só para caminho interno; 403 não | `respostas401DisparamSessaoExpirada`, `respostas403NaoDisparam`, `voltaSoAceitaCaminhoInterno` (T5) |
| Imagem em modo produção por padrão, por digest, sem YAML real | `imagemPadraoSemDevMode`, `todoFromComDigest`, `configRealNuncaReincluida` (T6) |
| Formas não previstas de URL exigem login; a SPA continua carregando | `formasNaoPrevistasExigemLogin`, `casaDaSpaContinuaPublica` (T7) |
| Symlink para fora e arquivo oculto não são servidos | `symlinkParaForaDaRaizEhNulo`, `arquivoOcultoDaSpa404` (T7) |
| `module` compila nas duas formas | `moduleSemSettingsCompila`, `moduleComSettingsPosicionalCompila` (T7) |
| Busca só com `JqlQuery`; cancelamento sem nova tentativa; token escapado mascarado; log sem CR/LF injetado | `buscaSoAceitaJqlQuery`, `retryCanceladoDuranteEsperaNaoTentaDeNovo`, `tokenEscapadoEmUrlEhMascarado`, `tokenEscapadoEmJsonEhMascarado`, `mensagemComQuebraDeLinhaNaoInjetaLog` (T8) |
| A varredura da fábrica pega alias, `typealias`, referência e `copy` aninhado | `VarreduraDeFontesTest` (T9) |
| `RetentionTest` verde em 10 execuções seguidas | laço da T10, Passo 4 |
| Travas existentes continuam verdes | `SomenteAFabricaCriaClientesJira`, `FlagDesligadaZeroRequisicoesTest`, `test-guard.sh` (T8, T9, T11) |

## 7. Riscos, pendências e pontos de parada

**PONTO DE PARADA 1 — onde o CI roda (T2, antes do Passo 3).** Pergunta ao usuário, com opções:
1. **GitHub Actions com runners hospedados pelo GitHub (Recomendada).** Por quê: Docker já vem no runner, então os testes com Testcontainers rodam sem infraestrutura nova. Consequência: o repositório privado consome minutos da cota da conta (a suíte leva poucos minutos por PR); o código passa pelos servidores do GitHub, onde já está hospedado.
2. **Runner self-hosted da empresa.** Por quê: só se a política da empresa proibir runners externos. Consequência: alguém da infraestrutura instala e mantém o runner com Docker; o plano troca `runs-on` e ganha um passo de limpeza de contêineres. Atrasa o primeiro CI verde.
3. **Sem CI no GitHub; só um script local `sh scripts/ci/rodar-tudo.sh`.** Por quê: custo zero de infraestrutura. Consequência: a barreira volta a depender de alguém lembrar de rodar; a lacuna C1 da auditoria continua aberta.

Sem resposta, seguir com a opção 1, que é reversível.

**PONTO DE PARADA 2 — proteção da `main` (T2 e T11).** Aplicar a regra no GitHub é efeito fora do repositório. Pergunta ao usuário:
1. **O usuário aplica pelo GitHub ou autoriza o comando `gh api` documentado em `docs/operacao/ci.md` (Recomendada).** Por quê: a regra só vale se os quatro checks forem obrigatórios. Consequência: nenhum PR entra na `main` com CI vermelho, nem por engano da IA.
2. **Deixar sem proteção por enquanto.** Por quê: menos atrito enquanto só uma pessoa mescla. Consequência: o CI avisa, mas não impede; registrar no `HANDOFF.md`.

A IA nunca aplica a proteção por conta própria.

**PONTO DE PARADA 3 — achado de dado pessoal no conteúdo atual (T1, Passo 4).** Se a varredura `--arvore` achar nome real, e-mail real ou host da empresa em arquivo já versionado, a IA não corrige sozinha nem copia o termo. Pergunta ao usuário:
1. **Remover do arquivo atual numa tarefa à parte e registrar o histórico como pendência (Recomendada).** Consequência: a árvore fica limpa; o histórico continua com o dado (repositório privado).
2. **Acrescentar uma exceção pontual em `padroes-permitidos.txt`.** Consequência: só se o usuário confirmar que não é dado pessoal (por exemplo, vocabulário de campo).
3. **Limpar também o histórico.** Consequência: reescrita de histórico e *force push*, ação irreversível fora deste plano; vira tarefa própria com aprovação explícita.

**Impacto nos planos seguintes (já escritos, não editados aqui):**
- **Planos 7 e 8:** os testes de `POST` (`registraUmaVez`, `decisaoDuplicada409`, `trocadaExigeMotivo`, `logoutLimpaSessao`) precisam usar `comCsrf()` de `TestHttp.kt`. Sem isso eles recebem 403 `CSRF_AUSENTE`, e o código do erro diz exatamente o que falta. O frontend do Plano 7 deve enviar a decisão por `enviarJson`.
- **Plano 8, T1:** `ROTAS_PUBLICAS` e as rotas `/auth/login` e `/auth/callback` entram na lista `rotasPublicas` de `AccessControl.kt` (D7). A regra "primeiro segmento nunca `auth`" faz com que só as rotas `/auth/*` explicitamente listadas sejam públicas.
- **Plano 8, T2:** `/auth/login?volta=` deve aceitar só caminho interno, com a mesma regra de `caminhoDeVolta` (T5). O `POST /auth/logout` fica coberto pelo CSRF.
- **Plano 8, T7:** o perfil `prod` do compose parte de uma imagem que já vem com `DASHBOARD_DEV_MODE=false`; o teste `prodSemDevMode` continua válido.
- **Todos os planos:** um PR só é mesclado com os quatro jobs verdes.

**Riscos:**
- *Docker no runner do GitHub mais lento ou instável.* Mitigação: `timeout-minutes` e o relatório de testes como artefato; nunca `continue-on-error`.
- *CSP quebra algo que o teste não vê* (por exemplo, uma biblioteca de gráfico do Plano 10 que injete estilo). Mitigação: `verificar-csp.mjs` no CI e a conferência manual no navegador (T3, Passo 4).
- *Falso positivo da varredura* em documentação pública. Mitigação: `padroes-permitidos.txt` versionado e revisado no PR.

**Migrações:** esta fase não cria nenhuma. Sequência esperada até o Plano 12, em execução na ordem do índice: V4, V5 e V6 (4A: sincronização, marcador de falhas, vínculos entre issues), V7 (5a), V8 (6b), V9 (7b), V10 (10), V11 (11, conforme o ponto de parada dele), V12 (8, só se o orçamento estourar), e a próxima livre para o 12. O Plano 16 (futuro) também usa "próximo número livre". A auditoria previa V8 para o Plano 8; como todos os planos usam "próximo número livre", não há conflito em execução sequencial.

## 8. Glossário do que esta fase consome

| Nome | O que é | Origem |
|---|---|---|
| `LiveGuard`, `JiraClientFactory` | trava e única fábrica de clientes Jira | Planos 1 e 3A |
| `FlagDesligadaZeroRequisicoesTest` | prova de 0 requisições com a flag `false` | Plano 4A |
| `test-guard.sh` | testes do hook `guard.sh` (com `jf`, `jt`, `jx`) | Planos 1 e 4A |
| `installAccessControl`, `exigeAutenticacao`, `rotasPublicas` | negar por padrão em `/api` | Plano 1 |
| `registrarNegacaoSemPapel`, `AccessLog` | registro de consultas negadas | Plano 3 |
| `spaEstatica`, `resolverDentro` | servidor da SPA | Plano 1 |
| `SearchRequest`, `JqlQuery`, `CardQueryFactory` | busca no Jira e JQL tipado | Planos 2 e 3 |
| `Agendador`, `module(..., agendador)` | agendador da sincronização | Plano 4A |
| `ErroDto` (novo) | erro por código, base das mensagens i18n do Plano 10 | esta fase |
| `comCsrf()` (novo) | helper de teste para métodos mutantes | esta fase |

## 9. Referência ao plano

[`docs/superpowers/plans/2026-10-25-plano-09-ci-e-endurecimento.md`](../plans/2026-10-25-plano-09-ci-e-endurecimento.md): 11 tarefas, em um PR a partir da `main` com o 4A mesclado.
