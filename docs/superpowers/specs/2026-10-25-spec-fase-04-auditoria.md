# Spec da fase 4: Auditoria de vencimento, responsável e labels de atraso

Data: 25/10/2026
Plano de implementação: [`docs/superpowers/plans/2026-10-25-plano-04-auditoria.md`](../plans/2026-10-25-plano-04-auditoria.md) (Tarefas 1 a 9)
Spec mestra do projeto: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 4.2, 4.3, 7, 9 e 10)

**Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra (seções 2 e 3) ainda fala em "autorização expressa por pesquisa"; isso foi **substituído em 25/10** pela regra da flag única (seção 3). Onde houver conflito, vale a regra nova.

Esta spec é autossuficiente: quem a lê não precisa do histórico das conversas que a geraram.

## 1. Intenção e porquê

A gestão do time precisa enxergar alterações que escondem atraso: vencimento empurrado por quem não podia, responsável trocado às vésperas do prazo, label de "atraso justificado" colocada depois que a task fechou. A regra de negócio (spec mestra 7.1) diz que **só o Reporter da task (o PL), os coordenadores e o gerente, ou o Squad Leader do squad do responsável** podem trocar responsável, vencimento e labels de atraso, **com os papéis vigentes na data da alteração**.

Esta fase detecta essas alterações no changelog já gravado no banco (`change_event`), mostra-as como **indício, não prova**, em telas restritas a coordenação e gerência, com gaveta de histórico campo a campo, e entrega o card Task × Review (vencimentos divergentes) e as métricas "no prazo" e "no prazo ajustado", reaproveitadas pela Fase 5.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Reconstruir o estado de cada issue em qualquer instante (responsável, vencimento, labels, fechada ou não) a partir do changelog.
- Decidir quem estava autorizado na **data civil de Manaus** de cada alteração, com vigência de papéis.
- Detectar 11 sinais (seção 4) com gravidade e limites em YAML versionado; a versão das regras sai em toda resposta.
- Task × Review: pares, percentual divergente, alerta no evento que gera a divergência.
- "No prazo" e "no prazo ajustado" lado a lado, com o contador "justificadas sob auditoria".
- API e duas telas React densas (Auditoria e Divergência), com drill do mesmo tamanho do número e CSV seguro.
- Documento de diferenças com o app atual.

### Não objetivos

- Os 12 sinais de **Integridade** (Fase 7, só gerente).
- "Estimativa reduzida" e "worklog retroativo" (sem valor de estimativa no changelog e sem worklog sincronizado).
- Histórico de reporter e conteúdo de descrição/título (o changelog só guarda o nome desses campos).
- Tabela de achados, triagem ou migração para sinais (são calculados na consulta).
- Qualquer conexão real ao Jira.

### Antiobjetivos (falha mesmo que "funcione")

- Apresentar indício como prova de má-fé (falta do aviso fixo em alguma tela ou resposta).
- `USUARIO` ou `SQUAD_LEADER` vendo um sinal de auditoria por qualquer rota.
- Autor ausente, de automação ou `JIRAUSER*` não resolvido virar "autorizado", ou derrubar a tela.
- Contagem ou ordenação por pessoa (ranking).
- Data avaliada em UTC em vez de Manaus (alteração às 23:30 de Manaus mudando de dia).
- Formato inesperado de `duedate` sumir em silêncio ou dar 500.
- Consulta à auditoria sem registro em `access_log`.

## 3. Restrições

### Regras transversais do projeto (valem nesta fase)

- **Flag do Jira (regra 1 do `CLAUDE.md`, reescrita em 25/10):** `JIRA_LIVE_ENABLED` é a **única trava**. `false` (padrão e estado atual): proibido acessar qualquer servidor Jira por qualquer caminho (app, MCP da Atlassian, `curl`, navegador, script), de qualquer IA ou pessoa. `true`: acesso liberado, sem pedido de autorização por pesquisa; a primeira conexão segue `docs/jira/checklist-primeira-conexao.md`. **Só o usuário muda a flag.** Esta fase não precisa do Jira: só fixtures e builders em memória.
- **Visibilidade por papel:** `/api` nega por padrão. Telas e rotas de auditoria só para `COORDENADOR` e `GERENTE`. Com `DASHBOARD_DEV_MODE=true`, o principal local `dev` já é `GERENTE` e vê tudo **sem ramo especial no código**. Integridade continua só do gerente (Fase 7).
- **Sem ranking de pessoas:** listas por data e chave; nenhuma contagem agregada por pessoa; filtro de pessoa em ordem alfabética.
- **Concluída** = categoria `done` **e** `resolution` preenchida; issue `done` sem `resolution` fica fora de "no prazo".
- **Atraso justificado:** "no prazo" (resolvida até o vencimento, dia de Manaus) e "no prazo ajustado" (no prazo **ou** com label que justifica) lado a lado; labels em `labels_atraso.yaml` (curinga só no fim, sem diferenciar maiúsculas).
- **Fuso America/Manaus** para todo "dia"; **dias úteis** são o padrão das métricas do projeto, mas a auditoria usa **dias corridos** por decisão do usuário (configurável).
- **Interface:** temas Claro, Escuro e Sidia; pt-BR e inglês completos; WCAG AA; a cor nunca é o único sinal (`✓` autorizado, `▲` auditoria/não autorizado, `●` alerta, sempre com texto).
- **LGPD:** auditoria trata dados pessoais. Até a base legal ser confirmada com o DPO, usar só fixtures e o **modo de demonstração** (`DASHBOARD_DEMO_DATA=true`, entregue no Plano 4A, Tarefa 10, que traz casos fictícios para cada sinal).
- **Dados pessoais:** só nomes fictícios em fixtures (`.invalid`); `change_event` guarda valor só dos campos permitidos; nunca editar nem commitar configs reais.
- **Banco real nos testes** (Testcontainers); **fail-closed** em produção (YAML real obrigatório; `.exemplo` só em dev).
- Conversa e comentários em pt-BR; commits em Conventional Commits pt-BR (`feat(auditoria): ...`).

### Restrições técnicas da fase

- Sem migração. Se o Plano 4A já estiver na `main` (ordem prevista), a leitura acrescenta `removida_em IS NULL` e o teste `auditoriaIgnoraIssueRemovida`.
- Toda consulta (200, 400, 403, 404) grava `access_log`; falha ao gravar = 503 sem dados.
- Período padrão de 30 dias de Manaus, `ate` inclusivo, máximo 180 dias; data inválida ou `de > ate` = 400, nunca 500.
- No histórico, campo fora da lista de permissão sai com `de`/`para` nulos e `valorGuardado=false`.

## 4. Decisões já tomadas

### Do usuário (25/10)

1. Telas e rotas de auditoria para coordenador e gerente (e o `dev` local, que já é gerente); usuário comum e Squad Leader não veem sinais.
2. Sinais calculados na consulta: sem tabela de achados, sem migração para sinais, sem triagem.
3. Squad Leader que é o responsável e altera o próprio vencimento ou a própria posse conta como **não autorizado** (`PROPRIO_RESPONSAVEL`).
4. "Perto do vencimento" e "2 dias de posse" em **dias corridos** (`unidade: corrido`); a configuração aceita dias úteis.
5. Label sob auditoria continua contando no "no prazo ajustado", com o contador "justificadas sob auditoria" ao lado.
6. Primeira definição do vencimento (vazio → data) não é alteração.
7. Estimativa reduzida e worklog retroativo ficam fora.
8. LGPD: confirmar a base legal com o DPO antes de usar com dados reais; até lá, fixtures e demonstração.

### Valor padrão recomendado (do plano, reversível)

- `auditoria.exemplo.yaml`: `pertoDoVencimentoDias: 2`, `posseAntesDoVencimentoDias: 2`, `passaBolaTrocas: 3`, `statusCancelamento: [Cancelled, Canceled, Dropped]`, `tiposReview: ["Checklist Review"]`; gravidade 3 para vencimento alterado/removido, responsável não autorizado, passa-bola e label após vencimento, 1 para Task × Review, 2 para os demais.
- `labels_atraso.exemplo.yaml`: justificativas `ONHOLDBY-*`, `LATE_SENT`, `LATE_REVIEW`, `DUEDATE_NEGOTIATED`, `BI-CONSIDERCANCELLED`; tardias `LATE_SENT`, `LATE_REVIEW`.
- Reporter (PL): a autorização usa o reporter **atual**; havendo evento de `reporter`, aviso `REPORTER_ALTERADO`.

### Os 11 sinais (`CodigoSinal`)

| Código | Regra resumida | Resultado |
|---|---|---|
| `VENCIMENTO_ALTERADO_NAO_AUTORIZADO` | `from` e `to` legíveis e diferentes, autor não autorizado | auditoria |
| `VENCIMENTO_REMOVIDO` | `from` preenchido e `to` nulo, qualquer autor | auditoria |
| `VENCIMENTO_TASK_REVIEW_DIVERGENTE` | evento que deixa Task e Review com vencimentos diferentes | alerta |
| `RESPONSAVEL_TROCADO_NAO_AUTORIZADO` | troca por não autorizado | auditoria |
| `PASSA_BOLA` | ≥ 3 trocas perto do vencimento, ou volta a quem já teve; um por issue | auditoria |
| `TROCA_PERTO_DO_VENCIMENTO` | troca a até 2 dias do vencimento por não autorizado | auditoria |
| `LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO` | label que justifica, depois de fechada, por não autorizado | auditoria |
| `LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO` | idem pelo próprio responsável | auditoria; alerta se autorizado |
| `LABEL_TARDIA_COM_POSSE_ANTECIPADA` | `LATE_SENT`/`LATE_REVIEW` com posse desde 2 dias ou mais antes do vencimento | auditoria |
| `LABEL_APOS_VENCIMENTO` | label que justifica depois do dia do vencimento, qualquer autor | auditoria |
| `CANCELAMENTO_SUSPEITO` | cancelamento depois do vencimento ou por não autorizado | auditoria |

## 5. O que fica para quem implementa

- Estrutura interna dos detectores e do `PostgresAuditStore` (SQL parametrizado, eventos em lote), com as assinaturas exatas do plano.
- Textos de i18n dos códigos, motivos e avisos (pt-BR e inglês), e o layout fino das telas densas dentro do kit existente.
- O mock de desenvolvimento do Vite (dados fictícios só no servidor de dev, fora do bundle).
- Escolhas que o plano deixa abertas: a mais segura e reversível, registrada como `Ruling` no arquivo de progresso.

## 6. Critérios de aceitação (verificáveis)

| Critério | Prova (teste do plano) |
|---|---|
| Labels e regras em YAML estrito, com todo código tendo regra | `curingaSoNoFim`, `arquivoEstrito`, `todoCodigoTemRegra`, `producaoSemArquivoRealFalha` (T1) |
| Estado da issue no instante e os dois formatos de `duedate` | `responsavelEVencimentoNoInstante`, `vencimentoNosDoisFormatos` (T2) |
| Autorização pela vigência na data civil de Manaus | `dataCivilDeManaus`, `vigenciaTerminouOntem`, `proprioResponsavelNaoAutorizadoMesmoSendoLider`, `autorDesconhecidoNaoEhAutorizado` (T2) |
| Sinais de vencimento, responsável, passa-bola e cancelamento com fronteiras | `alteradoPeloResponsavelGeraAuditoria`, `removidoGeraMesmoPorCoordenador`, `passaBolaTresTrocasNaJanela`, `limiteDaJanela`, `autorJirauserNaoResolvidoAparece` (T3) |
| Sinais de labels, com fronteira de Manaus | `labelAposVencimentoFronteira`, `tardiaComPosseAntecipada`, `responsavelAutorizadoViraAlerta` (T4) |
| Task × Review, no prazo e ajustado, todos os 11 códigos detectáveis | `quatroParesUmDivergente`, `noPrazoEAjustado`, `justificadaSobAuditoriaContaNoAjustado`, `todoCodigoDetectadoNoCenarioCompleto` (T5) |
| Dado ruim vira aviso, não 500 | `vencimentoIlegivelViraAviso`, `statusSemMapaIgnoraIssueEAvisa` (T5) |
| Leitura no banco por instância, com período `[de, ate)` | `fronteiraDoPeriodo`, `instanciaSeparada`, `trazTodosOsEventosDaIssue` (T6) |
| Só coordenação e gerente; toda consulta auditada; aviso fixo | `soCoordenacaoEGerente`, `todaConsultaGravaAccessLog`, `avisoFixoIndicioNaoProva`, `devModeSemLogin` (T7) |
| Drill do mesmo tamanho do número; sem ranking | `drillTemOMesmoTamanhoDoCard`, `ordenadoPorDataSemRanking` (T7) |
| Telas com aviso, símbolos além da cor, CSV seguro, sem ranking | `mostraAvisoFixo`, `simbolosAlemDaCor`, `csvNeutralizaFormulas`, `semRankingDePessoas` (T8) |
| Documento cita todo código de sinal | `documentoCitaTodosOsCodigos` (T9) |
| Suítes completas verdes | `./gradlew test`, `pnpm vitest run`, `pnpm build` |

## 7. Riscos e pendências

- **Formato real** de `duedate`, `labels` (separador em `toString`) e do `from/to` do assignee só se confirma na primeira conexão (checklist, Passo 4); o leitor aceita os dois formatos conhecidos de `duedate`.
- **Reporter como PL** é premissa a confirmar no Jira real; reporter alterado só gera aviso.
- **LGPD/DPO:** base legal pendente; até lá, só fixtures e demonstração.
- **Nome de teste trocado no Plano 4A:** a Tarefa 9 do plano 4 diz que `PreparacaoDocsTest` continua passando com `cadaPassoDoChecklistExigeAutorizacaoExpressa`. Depois do Plano 4A (Tarefa 11) esse teste se chama **`cadaPassoDoChecklistConfirmaAFlag`**, e `checklistETemTodasAsChamadas` passou a contar "Confirme `JIRA_LIVE_ENABLED=true`". Ao escrever o Passo 4 do checklist na Tarefa 9, use a frase "Confirme `JIRA_LIVE_ENABLED=true` e rode o passo", nunca "peça autorização expressa".
- **`RotaAuditada`** extrai o `executar` do Tempo em status: risco de mudar comportamento; os testes do Tempo em status precisam seguir verdes.

## 8. Glossário (nomes das fases anteriores que esta fase consome)

| Nome | Onde | O que é |
|---|---|---|
| `ChangeEventRecord`, `ChangelogParser` | `sync/ChangelogParser.kt` (Plano 3; `toEvents(issue, changelog, chaves)` desde o 4A) | Eventos do changelog; valor só de `status`, `assignee`, `resolution`, `duedate`, `labels`, `OS Version` |
| `IssueStore`, `PostgresIssueStore` | `sync/IssueStore.kt` | Modelo de SQL; `issue` única por `(instance, key)`; `removida_em` desde o 4A |
| `StatusMap`, `StatusCategory`, `UnmappedStatusException` | `domain/StatusMap.kt` | Mapa de status para categorias (`TODO`, `ACTIVE`, `WAIT`, `BLOCKED`, `DONE`) |
| `BusinessCalendar` | `time/BusinessCalendar.kt` | Calendário de Manaus, dias úteis e corridos |
| `RoleDirectory` (`rolesOf`, `squadOf`, `squadsLedBy`, `isReporterAuthorized`) | `security/RoleDirectory.kt` | Papéis com vigência por datas |
| `Visibility`, `requireRole`, `Principal`, `Role` | `security/` | Visão e acesso por papel |
| `AccessLog` | `db/AccessLog.kt` | Registro de toda consulta |
| `AvisoDto` (com `params`), `DrillRow` | `cards/TempoEmStatusDto.kt` | Avisos com código e gravidade; linhas de drill |
| `ORDEM_NATURAL_DE_CHAVES` | `domain/` | Ordem natural de chaves de issue |
| `testContextComBanco` | testes | Contexto com PostgreSQL (Testcontainers) |
| `FiltroDeFicha`, `ctx.fichas` | Plano 4A (T3, T8) | Filtro de leitura pela ficha do card |
| `DadosDeDemonstracao`, `DASHBOARD_DEMO_DATA` | Plano 4A (T10) | Dados fictícios com casos de cada sinal, para ver a tela sem Jira |
| `PreparacaoDocsTest.cadaPassoDoChecklistConfirmaAFlag` | Plano 4A (T11) | Substituto de `cadaPassoDoChecklistExigeAutorizacaoExpressa` |
| Frontend: `Shell`, `t()`, `DrillDrawer`, `linhaFiltrosCsv`, `format.ts` | `app/frontend/src/` (Plano 3) | Kit de tela; o CSV vai para `shared/csv.ts` nesta fase |

Nomes que esta fase **produz** e a Fase 5 consome: `LabelsDeAtraso`, `NoPrazo`, `NoPrazoResultado`, `AuditIssue`, `AuditTimeline.responsavelEm`, `RegrasDeAuditoria.tiposReview`, `executarAuditado` (`cards/RotaAuditada.kt`), `shared/csv.ts`, `ctx.labelsDeAtraso`, `ctx.regrasAuditoria`.

## 9. Referência

Plano: `docs/superpowers/plans/2026-10-25-plano-04-auditoria.md`. Depende do Plano 4A (executado antes). Índice: `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`. Guia para outra IA: `docs/guia-execucao-outra-ia.md`. Regras e sinais de origem: spec mestra, seção 7.
