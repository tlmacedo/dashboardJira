# Spec da Fase 16: Escrita no Jira pelo painel (vencimento e labels)

> **FUTURO — não executar sem decisão do usuário.** Esta fase muda a política de segurança do projeto: hoje o painel é somente leitura e nunca escreve no Jira. Nada desta spec é implementado antes de o usuário responder os pontos de parada (seção 7; Tarefa 1 do plano).

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-16-escrita-no-jira-pelo-painel.md`](../plans/2026-10-25-plano-16-escrita-no-jira-pelo-painel.md) (14 tarefas, um PR; a Tarefa 1 é o ponto de parada)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 2 "Não objetivos", 3, 4.3, 7.1 a 7.3, 10)
Spec da auditoria: [`2026-10-25-spec-fase-04-auditoria.md`](2026-10-25-spec-fase-04-auditoria.md) (quem pode alterar, `PROPRIO_RESPONSAVEL`, Task × Review)
Depende de: Fases 4A, 4, 5, 8, 9 e 10 (gaveta universal `TarefaDrawer`) mescladas na `main`; execução recomendada só depois da Fase 14 (leitura validada em produção) e do aceite do DPO. A trava de LGPD do Plano 11 é confirmada na execução.

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra de 24/10 ainda traz a regra antiga de acesso ao Jira e lista "escrita desligada" como não objetivo; esta fase, **se aprovada**, revoga esse não objetivo só para vencimento e labels, pelo fluxo descrito aqui.

Esta spec é autossuficiente: quem a lê não precisa do histórico das conversas que a geraram.

## 1. Intenção e porquê

Pedido do usuário em 25/10: *"futuramente vamos nos planejar para fazer esses ajustes direto pelo nosso dashboard: correções de due dates, adição, remoção ou ajuste de label; escrever e/ou atualizar na task do Jira pelo nosso dashboard, individualmente ou em lote."*

A auditoria (Fase 4) e a Divergência Task × Review mostram o que está errado, mas a correção hoje exige abrir cada task no Jira. Corrigir pelo painel encurta esse ciclo. Mas também transforma um painel que **só lê** num sistema que **altera dados do time** no Jira. Por isso a fase é desenhada para que:

- escrever seja **difícil de fazer por acidente**: duas flags, prévia, confirmação digitada, limite de lote;
- escrever seja **impossível de esconder**: registro imutável e a auditoria reconhecendo o que o painel fez;
- a escrita **nunca amplie** quem pode alterar o quê: as regras da auditoria, cruzadas com a visão.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Corrigir `duedate` (definir uma data) e `labels` (adicionar, remover, trocar) de uma ou de várias issues, até o limite do lote.
- Fluxo obrigatório **prévia (sem escrita) → confirmação explícita com motivo → execução**, com CSV da prévia.
- **Conflito:** a execução compara o valor atual com o valor esperado da prévia e não sobrescreve o que mudou.
- **Idempotência:** confirmar duas vezes (duplo clique, reenvio) escreve uma vez só.
- **Registro imutável** de cada tentativa e resultado: quem, quando, antes, pedido, observado, papel e motivo.
- **Reversão** da última alteração de um campo enquanto o valor atual for o gravado.
- **Permissões** iguais às da auditoria, avaliadas hoje em Manaus, cruzadas com a `Visibility`.
- **Ritmo:** limite de taxa, `Retry-After`, um lote por vez.
- **Auditoria coerente:** a alteração feita pela conta de serviço a pedido do painel é atribuída à pessoa real; a que não tem registro vira sinal.
- **Trava dupla provada:** com `JIRA_LIVE_ENABLED=false` **ou** `JIRA_WRITE_ENABLED=false`, 0 requisições de escrita.
- Hook do Claude, regras do Cline, guia, checklist da primeira escrita e RIPD para o DPO.

### Não objetivos

- Trocar responsável, status, transição, resolução, comentário, resumo, descrição, estimativa ou qualquer outro campo (salvo a decisão 3 da seção 7 ampliar o escopo).
- Remover o vencimento de uma issue.
- Criar ou apagar issues, labels do projeto ou links.
- Escrita a partir de ferramentas de IA (MCP da Atlassian, `curl`, scripts): só o painel escreve, pelo fluxo desta spec.
- Ligar `JIRA_WRITE_ENABLED` em qualquer ambiente (só o usuário).
- Desfazer qualquer alteração além da última do campo, ou alterações feitas fora do painel.

### Antiobjetivos (falha mesmo que "funcione")

- Uma única requisição de escrita com qualquer das duas flags `false`, inclusive em retry, depois de a flag ser desligada no meio do lote.
- A escrita ligada em modo de desenvolvimento (o `dev` sem login é GERENTE).
- PUT sem prévia válida do mesmo usuário, ou com confirmação genérica ("OK", "sim") em vez do texto com a contagem.
- Escrever duas vezes a mesma alteração por repetição automática depois de falha de rede ou 5xx.
- Sobrescrever um valor que mudou no Jira depois da prévia.
- Permissão de escrita mais larga que a regra da auditoria (o próprio responsável alterando a própria task) ou que a visão (issue fora da visão revelada ou alterada).
- Alteração da conta de serviço escondida da auditoria, ou marcada como "não autorizada" quando foi pedida por quem podia.
- Linha do registro de escrita alterada ou apagada fora da retenção.
- Token de escrita em log, erro, `toString`, resposta ou relatório; motivo gravado em `access_log`.
- Ranking de pessoas no histórico de alterações.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem nesta fase)

- **Acesso ao Jira.** `JIRA_LIVE_ENABLED` é a única trava de acesso (regra 1 do `CLAUDE.md`). Com `false`, ninguém acessa o Jira, por caminho nenhum. **Só o usuário muda a flag.**
- **Escrita.** Exige, além disso, `JIRA_WRITE_ENABLED=true`, uma flag **própria, sempre separada da de leitura e `false` por padrão**. Com qualquer uma das duas `false`, nenhuma escrita acontece. Só o usuário liga a flag de escrita.
- **Testes.** Nenhum teste automático acessa o Jira real, mesmo com as flags `true`: `MockEngine` e hosts `.invalid`.
- **Visibilidade por papel.** `/api` nega por padrão. O papel vem do `RoleDirectory` vigente; USUARIO vê o seu, SQUAD_LEADER o seu squad, COORDENADOR e GERENTE todos.
- **Quem pode alterar** (spec mestra 7.1): Reporter (PL), coordenadores e gerente, ou Squad Leader do squad do responsável; o próprio responsável não (`PROPRIO_RESPONSAVEL`, decisão 3 do Plano 4).
- **Sem ranking de pessoas.** Pt-BR e inglês. WCAG AA nos temas Claro, Escuro e Sidia. A cor nunca é o único sinal.
- **Fuso America/Manaus** para todo "dia" e para a vigência de papéis.
- **LGPD:** dados pessoais só depois do aceite do DPO. Fixtures só com nomes fictícios (`.invalid`).
- **Configs reais fora do git.** Banco real nos testes (Testcontainers). Fail-closed em produção.
- Conversa, comentários e commits em pt-BR (Conventional Commits).

### 3.2 Restrições específicas desta fase

- O escritor fica no pacote `escrita/`, fora de `jira/`. É o **único** arquivo de `src/main` com `HttpMethod.Put`, e só a sua fábrica o constrói.
- O escritor não reusa o caminho HTTP do leitor. `requisitarJson` exige corpo JSON e quebraria com o 204 vazio; `executarComRetry` repete 5xx e falhas de rede.
- A subida falha com a escrita ligada e qualquer destes casos: leitura desligada, modo de desenvolvimento, credencial de escrita ausente, ou credencial de escrita igual à de leitura.
- Toda rota mutante tem CSRF (Plano 9) e é só JSON. Nenhum `GET` muda estado. Toda rota grava `access_log`, sem o motivo.
- Migração nova com o próximo número livre; nunca editar migração mesclada.
- `JIRA_WRITE_ENABLED` usa o prefixo `JIRA_` por decisão do usuário. É exceção à convenção do Plano 13 ("variáveis novas com `DASHBOARD_` ou `OIDC_`") e fica ao lado das credenciais `JIRA_SAMSUNG_*`, que o hook já barra em comandos.

## 4. Decisões

| # | Decisão | Origem |
|---|---|---|
| U1 | Escrita exige `JIRA_LIVE_ENABLED=true` **e** `JIRA_WRITE_ENABLED=true`, separadas; a de escrita é `false` por padrão e só o usuário liga | [usuário] 25/10 |
| U2 | O painel segue somente leitura até a aprovação; plano e spec marcados "FUTURO", primeiro passo é perguntar | [usuário] 25/10 |
| U3 | Campos do pedido: vencimento e labels (adicionar, remover, ajustar), individual ou em lote | [usuário] 25/10 |
| U4 | Quem pode alterar = regras da auditoria (spec mestra 7.1; `PROPRIO_RESPONSAVEL`) | [usuário] spec mestra e Plano 4, decisão 3 |
| U5 | Flag do Jira: única trava de acesso; testes nunca acessam o Jira real | [usuário] `CLAUDE.md`, 25/10 |
| D1 | Escritor em pacote próprio, fora de `jira/`; garantias de somente leitura dos Planos 6 e 7 inalteradas | [valor padrão 25/10, reversível] |
| D2 | Sem repetição automática da escrita, exceto 429 com `Retry-After` até 60 s; incerto = reler, nunca reenviar | [valor padrão 25/10, reversível] |
| D3 | Remover vencimento fora da primeira versão; correção Task × Review ignora Task sem vencimento; reversão que removeria vencimento é recusada | [valor padrão 25/10, reversível] |
| D4 | Escrita proibida em `DASHBOARD_DEV_MODE=true` (a subida falha) | [valor padrão 25/10, reversível] |
| D5 | Prévia vale 15 min; confirmação digitando `CONFIRMAR <n> ALTERACOES`; chave de idempotência por abertura do modal | [valor padrão 25/10, reversível] |
| D6 | `escrita_jira` só de acréscimo (gatilho); a retenção é o único caminho de apagar | [valor padrão 25/10, reversível] |
| D7 | Ritmo: 1 PUT/s, até 30/min, um lote por vez por instância, sem paralelismo | [valor padrão 25/10, reversível] |
| D8 | Auditoria atribui à pessoa real a alteração da conta de serviço casada com `escrita_jira` (mesmo campo e valor, janela de 5 min); sem casamento, sinal `ALTERACAO_PELA_CONTA_DE_SERVICO_SEM_REGISTRO` | [valor padrão 25/10, reversível] |
| D9 | A prévia avisa os sinais de auditoria que a mudança vai gerar (ex.: label que justifica depois do vencimento), sem bloquear | [valor padrão 25/10, reversível] |
| D10 | Prévia e execução releem a issue no Jira (não confiam no banco local); depois de aplicar, pedem a ressincronização da chave | [valor padrão 25/10, reversível] |
| P1 a P9 | Seguir ou não e quando; credencial; campos além do pedido; quem escreve e por onde; limite do lote; labels permitidas; notificações; LGPD e retenção; primeira escrita real | [usuário] pendentes (seção 7) |

**Fatos da API do Jira DC usados e ainda não validados** (sem acesso à rede; validados no checklist da primeira escrita):
- `PUT /rest/api/2/issue/{key}` responde 204 sem corpo;
- labels mudam por `update.labels` com `add`/`remove`;
- o vencimento muda por `fields.duedate` no formato `aaaa-mm-dd`;
- alterar o vencimento exige a permissão Schedule Issues;
- `notifyUsers=false` exige perfil de administrador;
- não há trava otimista (`If-Match`) na edição de issue.

## 5. O que fica para quem implementa

- A estrutura interna do `ExecutorDeEscrita` e do `EscritaStore` (SQL parametrizado), com as assinaturas do plano.
- A forma do gatilho de imutabilidade e de como a retenção o libera (variável de sessão ou papel de banco próprio), desde que nenhum outro caminho apague ou altere.
- Os textos de i18n, o layout fino do modal e da gaveta dentro do kit existente.
- O nome final da variável da credencial de escrita, desde que nunca apareça em log nem em comando.
- Escolhas que o plano deixa abertas: a mais segura e reversível, registrada como `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-16-escrita-no-jira-pelo-painel.md`.

## 6. Critérios de aceitação verificáveis

| Critério | Prova (teste do plano) |
|---|---|
| Decisões do usuário registradas antes de qualquer código | `decisoesTemCadaPergunta`, `decisoesSemHostNemNomeReal` (T1) |
| Flag de escrita `false` por padrão; subida fail-closed | `escritaDesligadaPorPadrao`, `escritaExigeLeituraNaSubida`, `escritaComDevModeNaoSobe`, `escritaSemCredencialNaoSobe`, `tokenDeEscritaIgualAoDeLeituraNaoSobe`, `copyDaFlagDeEscritaProibidaEmSrcMain` (T2) |
| Trava dupla lida a cada requisição | `writeGuardExigeAsDuasFlags`, `writeGuardLeAFlagACadaCheck` (T2) |
| **Com qualquer flag `false`, 0 requisições de escrita** | `quatroCombinacoesDeFlag`, `guardAntesDeCadaTentativa` (T3); `flagsDesligadasZeroRequisicoesNaApi` (T11) |
| Só o escritor faz PUT; garantias de leitura inalteradas | `putSoNoEscritor` (T3); `SemEscritaNoJiraTest.nenhumCaminhoEscreveNoJira` (Plano 7) verde sem mudança |
| Sem repetição cega; `Retry-After` respeitado | `falhaDeRedeViraIncertaSemRepetir`, `retryAfterRespeitado`, `retryAfterAcimaDoTetoInterrompe` (T3) |
| Corpo só com campos permitidos; erros sem segredo nem dado pessoal | `corpoDoPutSoComCamposPermitidos`, `erroSemTokenNemCorpo`, `lerEstadoNaoGuardaDadoPessoal` (T3); `campoForaDaListaRecusado`, `removerVencimentoNaoExiste`, `labelForaDaListaRecusada`, `motivoObrigatorio` (T4) |
| Registro imutável; retenção como único caminho de apagar | `registroImutavel`, `retencaoEhOUnicoCaminhoDeApagar`, `chaveDeIdempotenciaUnica` (T5) |
| Permissão = regras da auditoria ∩ visão | `reporterPode`, `squadLeaderDoResponsavelPode`, `slDeOutroSquadNaoPode`, `proprioResponsavelNaoPode`, `vigenciaDeHojeEmManaus`, `foraDaVisaoNaoVazaNemEscreve`, `devModeNaoEscreve` (T6) |
| Prévia sem escrita, com limite, sinais previstos e CSV seguro | `previaNaoFazPut`, `loteAcimaDoLimite400`, `taskSemVencimentoForaDaCorrecao`, `sinalPrevistoLabelAposVencimento`, `previaExpira`, `csvNeutralizaFormulas` (T7) |
| Confirmação explícita, idempotência e conflito | `textoDeConfirmacaoComContagemErrada400`, `segundaConfirmacaoNaoRepete`, `conflitoQuandoValorMudouDesdeAPrevia`, `permissaoReavaliadaNaExecucao`, `falhaAoGravarAuditoriaNaoEscreve`, `incertaReleEDecide`, `flagDesligadaNoMeioParaORestante` (T8) |
| Ritmo e um lote por vez | `intervaloMinimoEntrePuts`, `tetoPorMinuto`, `doisLotesSimultaneos409`, `semParalelismo` (T9) |
| Reversão segura | `desfazQuandoValorAtualEhOGravado`, `naoDesfazSeMudou`, `soAUltimaAlteracaoDoCampo`, `reversaoGravaNovaLinha`, `reversaoTambemExigeFlags` (T10) |
| API com CSRF, só JSON, `access_log` sem motivo | `todaRotaMutanteExigeCsrf`, `getNuncaAltera`, `conteudoNaoJson415`, `accessLogSemMotivo`, `historicoSemRanking`, `falhaNoAccessLog503SemExecutar`, `inventarioConhecido` (T11) |
| Auditoria coerente com a conta de serviço | `escritaDoPainelAtribuidaAPessoaReal`, `contaDeServicoSemRegistroViraSinal`, `foraDaJanelaNaoCasa` (T12) |
| Telas sem escrita quando desligada; proteção contra lote acidental | `semBotoesComEscritaDesligada`, `confirmacaoExigeDigitarONumero`, `escFechaSemExecutar`, `selecaoLimitadaAoLote`, `paresSemVencimentoDaTaskSemAcao`, `simbolosAlemDaCor`, `mesmaChaveNoReenvio`, `i18nCompleto`, `semRankingDePessoas` (T13) |
| Hook, regras das IAs, RIPD e checklist | `test-guard.sh` com os casos novos; `checklistDaPrimeiraEscritaCompleto`, `matrizTemAsLinhasDaEscrita`, `ripdExigeAceiteAntesDaFlag`, `regrasDasIasCitamAsDuasFlags`, `documentosSemHostReal` (T14) |
| Suítes completas verdes | `./gradlew test`, `pnpm vitest run`, `pnpm build`, `sh .claude/hooks/test-guard.sh` |

## 7. Riscos, pendências e pontos de parada

**Pontos de parada: perguntar ao usuário** (Tarefa 1; uma pergunta por assunto; a recomendada primeiro; texto completo, com porquê e consequência, no plano):

1. **Seguir com a escrita?**
   - Aprovar agora e executar depois da Fase 14 e do aceite do DPO (Recomendada).
   - Executar já, offline, com as flags desligadas.
   - Não fazer: manter só leitura.
2. **Credencial** (o token do SSO/OIDC não é credencial do Jira):
   - Conta de serviço dedicada só para escrita, com permissão mínima (Recomendada).
   - OAuth 2.0 do próprio Jira DC por pessoa (app link; depende da versão e de um administrador do Jira).
   - Token pessoal (PAT) por pessoa (não recomendado).
   - Reusar o token de leitura (não recomendado).
3. **Campos:**
   - Só vencimento e labels (Recomendada).
   - Mais responsável.
   - Mais status/transição (não recomendado).
4. **Quem escreve e por onde:**
   - Regras da auditoria; coordenação e gerência por Divergência e Auditoria, PL e Squad Leader pela gaveta da issue nos cards que já veem (Recomendada).
   - Só coordenação e gerência.
   - Só o gerente.
5. **Lote:**
   - Até 20 issues, com confirmação digitando o número (Recomendada).
   - Até 50.
   - Sem lote.
6. **Labels:**
   - Só as de uma lista em YAML (Recomendada).
   - Qualquer label com formato válido.
7. **Notificações:**
   - Padrão do Jira (Recomendada).
   - `notifyUsers=false` (exige perfil de administrador; assumido, não validado).
8. **LGPD e retenção:**
   - DPO antes de ligar a flag, com retenção provisória de 24 meses (Recomendada).
   - Sem prazo.
   - 12 meses.
9. **Primeira escrita real:**
   - O usuário escolhe uma issue de teste e segue o checklist (Recomendada).
   - Direto numa divergência real.

**Riscos:**
- **Janela entre reler e escrever** (o Jira DC não tem trava otimista na edição). Mitigação: releitura imediata antes do PUT e conferência depois; o resultado divergente vira `INCERTA` e o lote para.
- **Autoria no Jira é a conta de serviço** (com a credencial recomendada). Mitigação: `escrita_jira` e a atribuição na auditoria (T12); a gaveta mostra "pelo painel, a pedido de".
- **Uso do token da conta de serviço fora do painel.** Mitigação: o sinal `ALTERACAO_PELA_CONTA_DE_SERVICO_SEM_REGISTRO`, o token num cofre (Plano 13) e a rotação.
- **E-mails em massa do Jira num lote.** Mitigação: o limite do lote e a decisão 7.
- **Fatos da API não validados** (204, `update.labels`, Schedule Issues, `notifyUsers`). Mitigação: o checklist da primeira escrita numa issue de teste; qualquer diferença desliga a flag.
- **Nomes dos Planos 4, 4A, 8 e 9 diferentes dos previstos; Planos 10 e 11 ainda não escritos.** Mitigação: a tabela "Estado herdado" do plano e `Ruling:`.
- **LGPD:** o motivo é texto livre e pode conter dado pessoal. Mitigação: limite de 500 caracteres, o aviso na tela, nunca em `access_log`, a retenção decidida com o DPO.

## 8. Glossário (nomes que esta fase consome)

| Nome | Onde | O que é |
|---|---|---|
| `LiveGuard`, `JiraClientFactory`, `JiraInstanceConfig`, `RetryPolicy`, `padraoEscapado` | `jira/` [main] | Trava de leitura, fábrica única do leitor, configuração mascarada, política de retry, máscara de segredos |
| `SomenteAFabricaCriaClientesJira` | testes [main] | Teste estático que varre `src/main`; esta fase acrescenta o caso da flag de escrita |
| `Settings` | `config/Settings.kt` [main] | Flags e instâncias; `copy` das flags proibido em `src/main` |
| `RoleDirectory`, `Visibility`, `Principal`, `Role`, `requireRole`, `normalizarUsername` | `security/` [main] | Papéis com vigência e visão por papel |
| `AccessLog` | `db/AccessLog.kt` [main] | Registro de toda consulta; falha = 503 |
| `Autorizacao`, `CodigoSinal`, `LabelsDeAtraso`, `RegrasDeAuditoria`, `ParTaskReview`, `executarAuditado`, tela Divergência, `shared/csv.ts` | Plano 4 | Regra de quem pode alterar na data, sinais, labels de atraso, pares Task × Review, CSV seguro |
| `ChangeEventRecord`, `change_event`, `Syncer`, `OrcamentoDeRequisicoes` | Planos 3 e 4A | Eventos do changelog, sincronização de uma chave, orçamento de requisições (confirmar) |
| `JqlValues.issueKey` | Plano 4A, T3 | Validação de chave de issue |
| `OidcAuthenticator`, `InventarioDeRotasTest` | Plano 8 | Login real e inventário de rotas |
| `TarefaDrawer`, `/api/tarefa/{key}` | Plano 10, T5 e T6 | Gaveta universal da task, filtrada pela visão; ponto de entrada da escrita para PL e Squad Leader |
| `ProtecaoCsrf`, `CABECALHO_CSRF`, `ErroDto`, `comCsrf`, `todoMetodoMutanteExigeCsrf`, `enviarJson`, `ErroDaApi` | Plano 9, T4 | CSRF em métodos mutantes e cliente da SPA |
| `SemEscritaNoJiraTest` | Plano 7, T11 | Garantia de que o pacote `jira/` não escreve |
| `SomenteLeituraTest` | Plano 6, T16 | Garantia de somente leitura de ausências |

Termos desta fase:
- **Intenção de alteração:** o que se quer mudar numa issue (`DefinirVencimento`, `AdicionarLabel`, `RemoverLabel`, `TrocarLabel`).
- **Prévia (dry-run):** leitura do estado atual e cálculo de antes e depois, sem escrever.
- **Valor esperado:** o "antes" gravado na prévia; se o Jira divergir dele na execução, é **conflito**.
- **Chave de idempotência:** identificador da confirmação; repetir a mesma chave não escreve de novo.
- **Conta de serviço:** usuário técnico do Jira usado só para escrever.
- **Incerta:** escrita cujo resultado não se sabe (rede, 5xx); é relida, nunca reenviada.
- **Trava dupla:** `JIRA_LIVE_ENABLED` e `JIRA_WRITE_ENABLED`, ambas exigidas.

## 9. Referência ao plano

Execute [`2026-10-25-plano-16-escrita-no-jira-pelo-painel.md`](../plans/2026-10-25-plano-16-escrita-no-jira-pelo-painel.md) **só depois da resposta do usuário à Tarefa 1**, tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). Ao fim: PR para a `main`, nada mesclado sem o usuário mandar, e `JIRA_WRITE_ENABLED` continua `false` em todo ambiente até o usuário ligá-la, depois do aceite do DPO e seguindo o checklist da primeira escrita.
