# Spec da Fase 7: Checklist × Review, Integridade, Distribuição, Governança e telas de início

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md`](../plans/2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md) (18 tarefas, blocos 7a e 7b, um PR por bloco)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 2, 4.2 "Central de atenção" e "Heurísticas", 4.3, 4.6 "Subtask Review", 7.2, 7.3, 8 item 7, 10)
Depende de: Fases 1 a 3 (na `main`), 4A, 4, 5 e 6 (mescladas antes desta)

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra de 24/10 ainda descreve a regra antiga de acesso ao Jira (autorização por consulta); **ela foi substituída em 25/10** pela regra da flag única descrita abaixo. Onde divergirem sobre o acesso ao Jira, vale esta spec (e o `CLAUDE.md`).

## 1. Intenção e porquê

Esta fase porta as telas restantes do app atual que dependem de tudo o que veio antes, e cria as telas de início por papel:

- **Checklist × Review**: mostra se a review de cada checklist foi feita a tempo, por outra pessoa e com duração plausível. Hoje o app atual usa `summary ~ Review` e mostra "quem atrasa quem" entre colegas.
- **Integridade**: são 12 sinais de manipulação de prazo e fluxo. É o card mais sensível do painel, porque trata indício sobre pessoas. Por isso fica só com o gerente, toda consulta é registrada e a tela avisa que é indício, não prova.
- **Distribuição e Rebalanceamento**: sugerem quem deve pegar cada task e o que mover de quem está sobrecarregado. Ficam em **modo sombra**: nada é escrito no Jira, e só a decisão do líder é guardada no painel.
- **Governança**: Qualidade dos dados (incluindo a divergência done × resolution), registro de acesso, decisões e configuração em leitura.
- **Central de atenção, Visão do squad e Meu painel**: a porta de entrada de cada papel. Cada número delas tem de ser o mesmo do card de destino.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos
- Pares Checklist × Review pelo mesmo pareamento da Divergência Task × Review (Plano 4), alertas por par, estatísticas, lift de labels e aprendizados com amostra mínima.
- Os 12 sinais de Integridade, com gravidade 1 a 3, nível por pessoa (ALTO, MÉDIO, NORMAL) e impacto nas metas, numa API só do gerente e sempre auditada.
- Motores puros de Distribuição e Rebalanceamento (com histerese), decisões gravadas numa tabela do painel, com 409 para decisão repetida.
- Qualidade dos dados com as 7 verificações do app atual, mais `DONE_SEM_RESOLUTION`, `RESOLUTION_SEM_DONE`, `STATUS_SEM_MAPEAMENTO` e `CAMPO_INDISPONIVEL`.
- Registro de acesso (AU-02) e Configuração em leitura (nome, versão, hash e origem de cada YAML, sem dados pessoais).
- Central de atenção (COORDENADOR e GERENTE), Visão do squad, Meu painel e início por papel.
- Documento `docs/analise/diferencas-esperadas-checklist-integridade-governanca.md`.

### Não objetivos
- **Escrita no Jira** (atribuir, mover, comentar). As sugestões nunca saem do painel.
- Ranking individual de pessoas, em qualquer forma.
- Sinal `ESTIMATIVA_AUMENTADA` ativo. Ele fica inativo, porque não há valor de estimativa em `change_event`.
- Triagem ou workflow sobre sinais de Integridade. Não há tabela de achados, e os sinais são calculados na consulta.

### Antiobjetivos (falha mesmo que "funcione")
- COORDENADOR, SQUAD_LEADER ou USUARIO verem qualquer dado de Integridade por **qualquer caminho**: rota, drill, CSV, URL direta ou item da Central.
- Consulta à Integridade ou ao Registro de acesso que não fique gravada, ou dados devolvidos quando a gravação falhou.
- Apresentar indício de Integridade como prova de má-fé.
- Lista de candidatos, pessoas ou pares ordenada por score, pontos, carga ou atraso; matriz "quem atrasa quem" entre colegas.
- Número da Central diferente do número do card de destino.
- Qualquer `PUT`, `PATCH` ou `DELETE` no pacote `jira`, ou `POST` fora de `/search`.
- Acessar o Jira com `JIRA_LIVE_ENABLED=false`, ou um teste automático acessar o Jira real.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é a flag `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`, 25/10).
- `false` (padrão): é proibido acessar o Jira por qualquer caminho, seja app, agendador, MCP, `curl`, navegador ou script, e por qualquer IA ou pessoa.
- `true`: o acesso fica liberado, seguindo `docs/jira/checklist-primeira-conexao.md` na primeira conexão.
- Só o usuário muda a flag.
- Testes automáticos usam sempre fixtures, builders em memória e `MockEngine`, mesmo com a flag `true`.

**Sem ranking de pessoas.** Toda tabela de pessoas fica em **ordem alfabética**. Fatores de candidatos também ficam em ordem alfabética, nunca por score.

**Visibilidade por papel.** Vale o `RoleDirectory` vigente na data.
- USUARIO vê só o próprio.
- SQUAD_LEADER vê o próprio e o seu squad.
- COORDENADOR e GERENTE veem todos.
- Parâmetros nunca ampliam a visão.
- Agregados são calculados só sobre o que é visível.

**Integridade só para o gerente e sempre auditada.**
- Toda resposta (200, 400, 403, 404) grava `access_log`.
- Se a gravação falhar, a resposta é 503, sem dados.
- Toda resposta e toda tela levam os avisos fixos "Indício, não prova" e "Validar a base legal com o DPO antes do uso real".

**Concluída** = categoria `DONE` **e** `resolution` preenchida. A divergência entre as duas aparece em Qualidade dos dados.

**Atraso justificado.** "No prazo" e "no prazo ajustado" aparecem lado a lado nos KPIs da Central (vindos de `MetasCalculator`, Fase 5). O `impactoMetasPp` da Integridade é outra coisa: `(taxaNoPrazo − taxaOriginal) × 100`, a diferença entre o prazo atual e o prazo pela **data original** do vencimento (IN-06). O `MeuPainelDto` do plano não traz as duas métricas (ver a seção 7).

**Tempo.**
- Fuso America/Manaus em todo "dia". "Fechada no limite" é medida no fuso de Manaus, e um lote cruzando a meia-noite de Manaus conta como lote.
- Dias úteis por padrão.

**Interface.**
- WCAG AA nos três temas.
- A cor nunca é o único sinal (`▲ ● ✓`, sempre com texto).
- pt-BR e inglês completos.
- Layout denso nas telas de trabalho e editorial na Central, no Meu painel e na Visão do squad.

**DPO antes do uso real.** Integridade, Registro de acesso e decisões tratam dados pessoais. Até a validação com o DPO, só fixtures e modo demonstração.

**Configs reais fora do git.** Versionar só `*.exemplo.yaml`. Heurísticas ficam em YAML versionado, e a versão sai em toda resposta.

**Ambiente e convenções.**
- Docker ativo, com banco real por Testcontainers.
- Migração no próximo número livre.
- Comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

**Checklist × Review.**
- Os tipos de review vêm **só** de `ctx.regrasAuditoria.tiposReview` (fonte única).
- Os pares saem de `TaskReview.pares`, e o pai é o campo `parent`.
- Os filtros se aplicam ao pai.
- A visibilidade vale pelo responsável do pai **ou** da review.

**Distribuição e Rebalanceamento.**
- Elegível: capacidade de 4 semanas acima de `capacidadeMinimaH`.
- O rebalanceamento move só issues não iniciadas, até `maxMovimentos`.
- O destino precisa ter todas as competências e ficar pelo menos 20 pp abaixo da origem (histerese).
- `SemCapacidade` nunca é destino.

**Decisões.**
- `TROCADA` exige motivo (1 a 500 caracteres, saneado) e `escolhido` visível.
- A unicidade vem de `chave_sugestao` (repetida = 409).
- O `access_log` do POST guarda só `tipo` e `decisao`, nunca o texto do motivo.
- O Squad Leader decide só sobre o próprio squad. Fora dele, a resposta é 403, e a tentativa fica registrada.

**Central.**
- "Sem atualização" e "Paradas no status" são **dois itens separados**.
- O item `INDICIO_ALTO` só existe no DTO do GERENTE.
- Cada item leva a rota e o `kind` do card de destino.

**Meu painel.**
- Mostra sempre o próprio usuário. O parâmetro `pessoa=` é ignorado para USUARIO.
- O Squad Leader pode escolher uma pessoa visível do seu squad.

**Configuração e Registro de acesso.**
- São só do GERENTE.
- A Configuração **não traz** nomes, usernames, hosts nem tokens.
- O Registro de acesso aceita no máximo 90 dias, em páginas de 200, e a própria consulta é registrada.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | Integridade só para o gerente; consulta auditada; indício, não prova; DPO antes do uso real | [usuário] spec mestra 4.3, 7.3 e 3 |
| U2 | Os 12 sinais de Integridade do app atual mantidos, com gravidade 1 a 3 | [usuário] spec mestra 7.2 |
| U3 | Central com "Sem atualização" e "Paradas no status" separados | [usuário] spec mestra 4.2 |
| U4 | Escrita no Jira desligada; ranking individual é não objetivo | [usuário] spec mestra 2 |
| U5 | Heurísticas (limiares da Integridade, pesos da distribuição) em configuração versionada, com a versão nos resultados | [usuário] spec mestra 4.2 |
| U6 | Visibilidade da auditoria: coordenador e gerente; sinais calculados na consulta, sem tabela de achados nem triagem; estimativa e worklog fora da auditoria | [usuário] Plano 4, decisões 1, 2 e 7 |
| U7 | Label sob auditoria continua contando no "no prazo ajustado" | [usuário] Plano 4, decisão 5 |
| U8 | Acesso ao Jira só pela flag `JIRA_LIVE_ENABLED`, que só o usuário muda | [usuário] `CLAUDE.md`, 25/10 |
| D1 | **Distribuição (DI-04) sem ranking:** o sugerido e os fatores de todos os candidatos em ordem alfabética, sem coluna de posição | [valor padrão 25/10, reversível] |
| D2 | **CR-12 "Quem atrasa quem" vira "atrasos em cadeia":** contagem de pares com drill, sem nomes nem pares de pessoas | [valor padrão 25/10, reversível] |
| D3 | **A pessoa de um sinal de Integridade é o autor do evento que o dispara.** Sem autor, vale o responsável no instante. O responsável sempre aparece no detalhe | [valor padrão 25/10, reversível] |
| D4 | **Decisões de Distribuição e Rebalanceamento gravadas na tabela local `decisao_distribuicao`**, auditada e sem escrita no Jira | [valor padrão 25/10, reversível] |
| D5 | `ESTIMATIVA_AUMENTADA` inativo, com aviso `SINAL_SEM_DADO`, até uma decisão futura do usuário ampliar os campos com valor em `change_event` | [design do plano; decorre de U6] |
| D6 | Pareamento único com a Divergência (via `toAuditIssue` e `TaskReview.pares`); review pelo tipo `Checklist Review`, e não por `summary ~ Review` | [design do plano; spec mestra 4.6] |

## 5. O que fica para quem implementa

**Código e banco.**
- Organização interna dos detectores e das consultas, mantendo as **assinaturas públicas do plano**.
- Função de hash da `chave_sugestao`, que deve ser estável e documentada no código.
- Número das migrações.

**Interface.** Textos das frases de aprendizado, que são códigos traduzidos no frontend, e o layout fino das telas dentro do plano visual: seções 4, 6.1 e 6.5.

**Escolhas em aberto.** Quando o plano deixar margem, escolha a opção mais segura e reversível e registre como `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md`.

**Equivalências para o ClineSR.**
- O plano cita `progress.md`; vale o caminho do guia, acima.
- "USE superpowers:..." equivale à seção 4 do guia.

**Vocabulário a confirmar na 1ª conexão real.** `labelsCriticidade` e `prioridadePadrao` ficam como no exemplo do YAML até essa conexão.

## 6. Critérios de aceitação verificáveis

Os critérios supõem Docker ativo. Cada teste citado precisa falhar antes de existir a implementação, e pelo motivo certo.

**Bloco 7a (Tarefas 1 a 8)**

1. **Pares CR, Tarefas 1 a 3.** Testes: `quatroParesESemReview`, `alertasDoCenario`, `tiposReviewDaAuditoria`, `atrasosEmCadeiaSemPessoas` (resultado sem campo de pessoa), `liftComAmostraPequena`, `drillTemOMesmoTamanhoDoCard` (`pares=4`, `semReview=1`, `comAlerta=3`) e `usuarioVeParDoQualParticipa`.
2. **Sinais de Integridade, Tarefas 4 e 5.** Testes com limites exatos:
   - `trocaAntesDeFechar`: 1 h 59 min dispara; 2 h 01 min não.
   - `criadaEFechadaRapido`: 14 min dispara; 15 min não.
   - `fechamentoEmLote`: 6 dispara; 5 não.
   - `loteCruzandoMeiaNoite`.
   - `fechadaNoLimitePeloFusoDeManaus`: `2026-10-16T02:30Z` dispara; `01:59Z` não.
   - `resgatePorProrrogacao`: não soma com `VENCIMENTO_PRORROGADO`.
   - `estimativaSemDadoViraAviso`.
   - `criticidadeRemovidaPertoDoFim`.
3. **Níveis, Tarefa 6.** Testes: `niveisDoCenario` (mediana 0,416667; `ana` MÉDIO), `altoExigeOsGraves`, `pisoDaMediana`, `pessoasAlfabeticas` e `impactoNasMetas` (25,0 pp).
4. **Integridade restrita, Tarefa 7.**
   - `integridadeSoGerenteEmTodasAsRotas`: 403 para COORDENADOR, SQUAD_LEADER e USUARIO nas 3 rotas.
   - `todaConsultaGravaAccessLog`: 200, 400, 403 e 404.
   - `falhaAoRegistrar503SemDados`, `avisosFixos`, `periodoMaximo180` e `catalogoSemPessoas`.
   - No frontend: `faixasFixasPtEn` e `pessoasAlfabeticasSemColunaDePontosOrdenavel`.
5. **Diferenças 7a, Tarefa 8.** `CompatLegadoChecklistTest` confere as regras contra `legacy/backend/jiradash/analytics.py`. `DiferencasChecklistIntegridadeDocTest` exige que o documento cite CR-01..14, IN-01..08 e os 12 nomes de `SinalIntegridade`.

**Bloco 7b (Tarefas 9 a 18)**

6. **Motores, Tarefas 9 e 10.**
   - `scoreDosCandidatos`: `ana` 0,416667, `bruno` 1,6, `carla` 0,6; o sugerido é `ana`.
   - `fatoresEmOrdemAlfabetica`, `atualizaCargaEntreItens`, `produtividadeLimitada`, `histerese`, `semCapacidadeNuncaEhDestino` e `abaixoDoGatilhoNaoMove`.
7. **Decisões, Tarefa 11.** Testes: `registraUmaVez`, `decisaoDuplicada409`, `trocadaExigeMotivo`, `squadLeaderSoNoProprioSquad` (403 registrado), `postGravaAccessLogSemOCorpoDoMotivo` e `SemEscritaNoJiraTest.nenhumCaminhoEscreveNoJira`.
8. **Governança, Tarefas 13 e 14.**
   - `doneSemResolution`, `campoIndisponivel`, `faixasDeEstado` (10% ok, 30% atenção, 31% problema) e `soGerente`.
   - `configuracaoSemDadosPessoais`: o JSON não traz nenhum id de papéis, nem `https://`, nem `token`.
   - `consultaAoRegistroTambemERegistrada` e `periodoMaximo90`.
9. **Início, Tarefas 15 a 17.**
   - API: `itensBatemComAsTelasDeDestino`, `semAtualizacaoEParadasSeparadas`, `centralDoCoordenadorSemItemDeIntegridade`, `centralDoGerenteComIndicioAlto` (com `access_log`), `meuPainelIgnoraParametroPessoaDoUsuario`, `squadLeaderVePessoaDoSquad` e `numerosIguaisAosCards`.
   - Frontend: `inicioPorPapel` (GERENTE → `/central`, SQUAD_LEADER → `/squad`, USUARIO → `/meu`).
10. **Frontend.** `cd app/frontend && pnpm vitest run` e `pnpm build` passam. Testes: `faixaModoSombra`, `fatoresAlfabeticosSemPosicao`, `trocarExigeMotivo` e `menuDecisoesSoCoordenacaoEGerente`. Fazer uma rodada de capturas a 1440 e 390 px nos três temas.
11. **Diferenças 7b, Tarefa 18.**
    - `CompatLegadoDistribuicaoTest` confere contra `legacy/backend/jiradash/distribution/engine.py` os pesos, a produtividade 0,85–1,15, o gatilho de 110%, os 10 movimentos e os 20 pp.
    - O documento cita VG, MP, DI, RB, QD, AU e SI.

**Fim de cada bloco**

12. As suítes completas das duas camadas ficam verdes, inclusive `SomenteAFabricaCriaClientesJira` e `sh .claude/hooks/test-guard.sh`. Antes do push, varrer dados pessoais. Abrir PR para a `main` e não mesclar sem o usuário mandar.

## 7. Riscos e pendências

**Sem dono hoje.**
- **`ESTIMATIVA_AUMENTADA` sem dado.** Depende de uma decisão futura do usuário de gravar o valor de estimativas em `change_event`.
- **Sinais "estimativa reduzida" e "worklog retroativo"** (spec mestra 7.2). Nenhuma das Fases 4 a 8 os assume. Registrar no `HANDOFF.md`.
- **Meu painel sem "no prazo" e "no prazo ajustado".** A spec mestra 6.1 pede as duas métricas no Meu painel, mas o `MeuPainelDto` da Tarefa 16 não tem esses campos. Não mudar a assinatura por conta própria: levar ao usuário como pergunta com opções ("acrescentar os dois campos ao `MeuPainelDto` nesta fase (Recomendada)" ou "deixar para depois e registrar no `HANDOFF.md`") e registrar a resposta no progresso.

**Depende da 1ª conexão real ou da calibração.**
- **Vocabulário real** de `labelsCriticidade`, `prioridadePadrao` e do tipo `Checklist Review`. Confirmar na 1ª conexão real, depois que o usuário ligar a flag.
- **Limiares de Integridade e pesos da distribuição.** Vieram do app atual, e a calibração só acontece com dados reais.

**Dependências externas.**
- **Base legal com o DPO** para Integridade, Registro de acesso e decisões. Até lá, só fixtures e demo.
- **Central compõe calculadores de 4 fases.** Uma mudança de regra num card muda a Central. Por isso existe o teste `itensBatemComAsTelasDeDestino`.
- **Nomes herdados das Fases 4A a 6.** Conferir antes da Tarefa 1 (seção 8).

## 8. Glossário do que esta fase consome

Legenda: **[main]** existe hoje na `main`; **[Plano N]** é previsto pelo plano N. Para os itens [Plano N], confirmar o nome na `main` antes da Tarefa 1; se mudou, atualizar só o nome no plano e registrar no progresso.

| Nome | Arquivo | Origem |
|---|---|---|
| `RoleDirectory`, `Role`, `Visibility`, `AccessControl`, `requireRole`, `Usernames` | `app/backend/src/main/kotlin/br/sidia/painel/security/` | [main] Planos 1 e 3A |
| `AccessLog` (esta fase acrescenta `listar`) | `.../db/AccessLog.kt` | [main] Plano 3 |
| `BusinessCalendar` | `.../time/BusinessCalendar.kt` | [main] Plano 1 |
| `StatusMap`, `StatusCategory`, `TimelineBuilder`, `Percentiles` | `.../domain/` | [main] Plano 3; `StatusMap.ordem/ehResolucaoNegativa` no Plano 5 |
| `JiraClient`, `JiraClientFactory`, `LiveGuard` | `.../jira/` | [main] |
| `AvisoDto`, `DrillRow`, `ORDEM_NATURAL_DE_CHAVES` | `.../cards/TempoEmStatusDto.kt`, `domain/` | [main] Plano 3 |
| `AppContext`, `testContextComBanco` | `.../AppContext.kt`, `src/test/.../TestContextComBanco.kt` | [main] |
| `SyncStatusDto`, `GET /api/sync/status` (tela Sincronização: SI-01, SI-02) | `.../sync/` | [Plano 4A] Tarefa 8 |
| `TaskReview.pares/resumo`, `ParTaskReview`, `AuditTimeline`, `AuditCalculator`, `Sinal`, `Detector`, `RegrasDeAuditoria.tiposReview/statusCancelamento` | `.../auditoria/` | [Plano 4] |
| `LabelsDeAtraso`, `NoPrazo.classificar` | `.../domain/LabelsDeAtraso.kt`, `domain/NoPrazo.kt` | [Plano 4] |
| `executarAuditado` | `.../cards/RotaAuditada.kt` | [Plano 4] Tarefa 7 |
| `IssueSnapshot`, `WorkStore`, `toAuditIssue`, `Fluxo` | `.../trabalho/` | [Plano 5] Tarefas 2 e 3 |
| `CardDeLeitura`, `rotaDeCard`, `FiltrosDeCard`, `escopoDe`, `EntradaDeCard` | `.../cards/` | [Plano 5] Tarefa 4 |
| `MetasCalculator`/`MetasDto`, `RiscoCalculator`, `NegligenciadasCalculator`, `AbertasCalculator`, `PrevisaoCalculator`, `MonteCarlo` | `.../cards/*`, `trabalho/MonteCarlo.kt` | [Plano 5] |
| `Equipes`, `Ausencias`, `RegrasDePlanejamento`, `HorasDaIssue`, `Capacidade`, `Demanda`, `Ocupacao`, `CargaDaPessoa`, `Jain`, `Agenda` | `.../pessoas/` | [Plano 6] Tarefas 1 a 4 |
| `EsforcoCalculator`, `PosseCalculator`, `LinhaCargaDto`, `VencimentosDto`, `AusenciaDto`, `OcupacaoDto` | `.../cards/*`, `pessoas/` | [Plano 6] |
| Kit de tela `CardPage`, `KpiCard`, `DrillDrawer`, `useCard`; `shared/csv.ts` | `app/frontend/src/shared/` | [Planos 4 e 5] |
| `Shell`, `nav.ts` (`inicioDoPapel`, grupos), i18n | `app/frontend/src/shell/`, `nav/`, `i18n/` | [main] |

**O que esta fase produz para a Fase 8:**
- as rotas `/api/integridade*`, `/api/distribuicao*`, `/api/rebalanceamento`, `/api/governanca/*`, `/api/central`, `/api/squad` e `/api/meu-painel`;
- o único `POST` de dados, `/api/distribuicao/decisao`;
- os testes `CompatLegadoChecklistTest` e `CompatLegadoDistribuicaoTest`.

## 9. Referência ao plano

Execute [`2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md`](../plans/2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md) uma tarefa por vez, com TDD, seguindo `docs/guia-execucao-outra-ia.md` (seções 4 a 8).

O bloco 7a (Tarefas 1 a 8) e o bloco 7b (Tarefas 9 a 18) são PRs separados. Cada um parte da `main` atualizada.
