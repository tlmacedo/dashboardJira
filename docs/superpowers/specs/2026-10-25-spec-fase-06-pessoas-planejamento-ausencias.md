# Spec da Fase 6: Pessoas, planejamento e ausências

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-06-pessoas-planejamento-ausencias.md`](../plans/2026-10-25-plano-06-pessoas-planejamento-ausencias.md) (16 tarefas, blocos 6a e 6b, um PR por bloco)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 4.2, 4.3, 4.4, 5.4, 6.2, 6.3, 8 item 6, 9)
Depende de: Fases 1 a 3 (na `main`), 4A, 4 e 5 (mescladas antes desta)

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra de 24/10 ainda descreve a regra antiga de acesso ao Jira (autorização por consulta); **ela foi substituída em 25/10** pela regra da flag única descrita abaixo. Onde a spec mestra e esta divergirem sobre o acesso ao Jira, vale esta spec (e o `CLAUDE.md`).

## 1. Intenção e porquê

A gestão do time TG-Basic precisa saber, por pessoa e por squad, **quanto trabalho existe, quanta capacidade real há** (descontando foco, alocação, feriados e ausências) e **o que vai atrasar por causa disso**. O app Python atual tem esses cards com defeitos conhecidos: mostra 990% quando a capacidade é zero, deixa issue vencida inflar a semana atual, usa 6 h fixas ou pontos × 4 h para issue sem estimativa e ordena pessoas por carga (ranking). Esta fase recria os cards de Carga, Capacidade e Gargalos e cria os cards novos aprovados pela spec mestra (Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana, Tendência, Posse, Esforço, Cobertura, Simulador, Competências, Revisões pendentes, Carga por tipo/OS), com ausências e feriados **lidos de YAML, somente leitura**.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos
- Carregar equipes (horas, foco, alocação, competências), ausências (sem motivo) e regras de planejamento de YAML estrito, versionado só como `*.exemplo.yaml`.
- Um motor puro de capacidade: `HorasDaIssue` → `Capacidade` → `Demanda`/`Ocupacao` → `Agenda`, reaproveitado por todos os cards e ligado a Riscos e Previsão da Fase 5.
- Bloco 6a: Carga por pessoa, Capacidade e ausências, Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana (folga real), Gargalos e fluxo, Tendência de ocupação.
- Bloco 6b: worklog sincronizado sem comentário, Posse da task, Esforço por task fechada (três componentes e índice), Cobertura por ausência, Simulador de ausência, Matriz de competências, Revisões pendentes por revisor, Carga por tipo/OS.
- Documento de diferenças esperadas com o app atual (`docs/analise/diferencas-esperadas-pessoas-e-planejamento.md`).

### Não objetivos
- Cadastro, edição ou aprovação de ausências pela interface (spec mestra 4.4). O antigo card de "aprovação com impacto" vira o **Simulador**, que não grava nada.
- Calendário por pessoa (há um calendário único do time).
- Calibração dos pesos do esforço e do foco com dados reais (fica para depois da 1ª conexão real).
- Retrato semanal da carga gravado no banco (ver decisão D1).

### Antiobjetivos (falha mesmo que "funcione")
- Mostrar "990%" ou qualquer porcentagem quando a capacidade é zero; dividir por zero.
- Ordenar pessoas por carga, folga, ocupação ou esforço; dizer "maior/menor ocupação".
- Um USUARIO ver a linha, o Jain ou a capacidade agregada que revele colegas.
- Gravar motivo de ausência, comentário de worklog ou qualquer texto livre do Jira.
- Alguma rota escrever em `equipes.yaml`, `ausencias.yaml` ou `papeis.yaml`.
- Acessar o Jira com `JIRA_LIVE_ENABLED=false`, ou um teste automático acessar o Jira real.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)
- **Acesso ao Jira: a única trava é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). `false` (padrão e estado atual): proibido acessar qualquer servidor Jira, por qualquer caminho (app, agendador, MCP, `curl`, navegador, script), para qualquer IA ou pessoa. `true`: acesso liberado, seguindo `docs/jira/checklist-primeira-conexao.md` na primeira conexão. **Só o usuário muda a flag**; nenhuma IA a liga. Os planos dizem `JIRA_LIVE_ENABLED=false` para o ambiente de execução e testes; **testes automáticos usam sempre fixtures e `MockEngine` (`https://jira.exemplo.invalid`), mesmo com a flag `true`.**
- **Sem ranking de pessoas.** Toda tabela de pessoas em **ordem alfabética**; nada de posição, "melhor/pior" ou ordenação por desempenho.
- **Visibilidade por papel** (`Visibility` + `RoleDirectory` vigente na data): USUARIO vê só o próprio; SQUAD_LEADER o próprio e o seu squad; COORDENADOR e GERENTE veem todos. Parâmetros (`pessoa=`, `squad=`, `kind=`) nunca ampliam a visão. Agregados (Jain, ocupação do squad, capacidade do squad) só sobre o conjunto visível e `null` com menos de 2 pessoas. Com `DASHBOARD_DEV_MODE=true`, principal `dev` com papel GERENTE, sem login, faixa vermelha.
- **Integridade só para o gerente e sempre auditada** (Fase 7). Nada desta fase expõe sinais de Integridade.
- **Concluída = categoria de status `DONE` e `resolution` preenchida.**
- **Atraso justificado: duas métricas lado a lado**, "no prazo" e "no prazo ajustado" (labels em YAML, Plano 4). Esta fase **não calcula** "no prazo" nem "no prazo ajustado": essas métricas vêm da Fase 5 (Metas e prazos, `NoPrazo` do Plano 4) e não devem ser acrescentadas aos cards desta fase (Vencimentos mostra folga real, não taxa de prazo).
- **Fuso America/Manaus** em todo "dia"; **dias úteis por padrão** (feriados de `calendario.yaml`), dias corridos opcionais (`unit=corrido`, na URL). Semana de segunda a domingo.
- **Interface:** WCAG AA nos três temas (Claro, Escuro, Sidia); cor nunca é o único sinal (`▲ ● ✓ ⏸` com texto); pt-BR e inglês completos.
- **Auditoria de consulta:** toda rota de dados grava `access_log` (inclusive as negadas); falha ao gravar → 503 sem dados.
- **DPO antes do uso real:** ausências, carga e esforço por pessoa são dados pessoais; validar a base legal com o DPO antes de usar com dados reais. Até lá, só fixtures e modo demonstração.
- **Configs reais fora do git e da imagem** (`equipes.yaml`, `ausencias.yaml`, `papeis.yaml`, `squads.yaml`); versionar só os modelos `.exemplo`. Em produção o arquivo real é obrigatório (falha na subida citando o caminho); só o modo desenvolvimento cai no modelo.
- Sem Docker: banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`; nunca H2 nem mock). Comentários em pt-BR; commits Conventional Commits em pt-BR.

### 3.2 Restrições específicas desta fase
- **Capacidade por dia útil** = horas semanais ÷ 5 × foco × alocação × (1 − fração de ausência); fim de semana e feriado = 0. Foco nulo usa `focoPadrao`.
- **Capacidade zero → "sem capacidade"** (`Ocupacao.SemCapacidade`), com a demanda mostrada; nunca porcentagem.
- **Issue vencida** (`duedate < hoje`) sai das semanas e soma em **"atraso acumulado (h)"**.
- **Horas da issue:** restante do Jira → estimativa − registrado (mínimo `horasMinimas`) → horas padrão **por tipo** (YAML) → `horasPadrao`. As duas últimas marcam `horasAssumidas` (confiança menor).
- **Posse:** cada pessoa responde pelo tempo em que ficou com a task (spec mestra 6.3); métricas por pessoa (esforço, tempo) usam a fração de posse. Pessoa fora do visível aparece como `null` ("outra pessoa"), nunca pelo nome.
- **Ausência não tem motivo:** a chave `motivo` é recusada na carga do YAML e nunca aparece em DTO.
- **Worklog:** só `jiraId`, autor (username normalizado; `JIRAUSER*` traduzido), `started` e segundos. `comment`, `updateAuthor` e `self` nunca são lidos; `raw` não guarda `worklog`. Retenção pela `started` (24 meses, configurável).
- Heurísticas (foco, horas por tipo, limiares, WIP, pesos do esforço) em YAML versionado; a versão sai em toda resposta (`versaoRegras`).
- Todo número clicável abre as tasks que o compõem, **com o mesmo tamanho**; status sem mapeamento → 200 com aviso.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | Ausências e feriados em YAML versionado, somente leitura; sem cadastro nem aprovação; simulador no lugar da aprovação | [usuário] spec mestra 4.4 |
| U2 | Capacidade zero = "sem capacidade"; vencidas viram atraso acumulado; horas padrão por tipo em YAML | [usuário] spec mestra 4.2 |
| U3 | Esforço por task fechada = três componentes (peso do tipo, labels `TEST-*`, horas do worklog) e índice composto com pesos em YAML (`pesoTipo + testes × pesoTeste + horas × fatorHora`, proposta inicial) | [usuário] spec mestra 4.2 e 9 |
| U4 | Posse proporcional ao tempo com a task | [usuário] spec mestra 6.3 |
| U5 | Os 11 cards novos da spec mestra 6.2 | [usuário] spec mestra 6.2 |
| U6 | Retenção do histórico (inclui worklog) configurável, padrão 24 meses | [usuário] spec mestra 4.6 e 5.4 |
| U7 | Identidade: `id` em `papeis.yaml` = username do Jira = `assignee`, minúsculas; ids reservados `dev`, `system`, `admin`, `root`, `anonymous` | [usuário] `CLAUDE.md` |
| U8 | Acesso ao Jira só pela flag `JIRA_LIVE_ENABLED`; só o usuário a muda | [usuário] `CLAUDE.md`, 25/10 |
| U9 | Dias corridos nas regras de "perto do vencimento" e "2 dias de posse" da auditoria (Plano 4, decisão 4); label sob auditoria continua contando no "no prazo ajustado" (Plano 4, decisão 5) | [usuário] Plano 4 |
| D1 | **Tendência de ocupação reconstruída do changelog com as horas atuais**, com aviso `HORAS_ATUAIS_NO_PASSADO`; sem tabela de retrato semanal | [valor padrão 25/10, reversível] |
| D2 | **Candidatos da Cobertura:** só pessoas visíveis do mesmo squad na data, em ordem alfabética, com a folga ao lado (sem ordenar por folga) | [valor padrão 25/10, reversível] |
| D3 | **Semana atual na capacidade: de hoje até domingo** (o tempo que já passou não conta como livre) | [valor padrão 25/10, reversível] |
| D4 | Carga: `acimaDaCapacidade` (> `ocupacaoAlta`) e `comFolga` (< `folga`) no lugar de "maior/menor ocupação" (CP-02) | [design do plano, decorre de "sem ranking"] |
| D5 | Capacidade, Tendência, Cobertura e Simulador só para liderança (SQUAD_LEADER, COORDENADOR, GERENTE); Carga para todos (USUARIO vê a própria linha) | [design do plano] |

"Reversível" quer dizer: o usuário pode mudar depois, e a mudança fica contida no card ou na regra citada.

## 5. O que fica para quem implementa

- Nomes internos de funções auxiliares, organização de SQL e consultas em lote, desde que as **assinaturas públicas do plano** fiquem exatamente iguais.
- Mensagens de erro de carga do YAML (precisam citar arquivo, chave ou id/linha; sem stack trace genérico).
- Número da migração do worklog (`V{n}__worklog_por_instancia.sql`): o próximo livre na `main` (hoje só existe a V1, então a V2); nunca editar migração mesclada.
- Layout fino das telas dentro do plano visual (densa nas telas de trabalho; Calendário conforme o wireframe 6.3; Esforço conforme 6.5).
- Escolhas onde o plano deixa margem: decidir a mais segura e reversível e registrar como `Ruling: <o que> — <por quê> — <custo se errado>` no arquivo de progresso `docs/superpowers/progresso/2026-10-25-plano-06-pessoas-planejamento-ausencias.md` (o plano cita `progress.md`; vale o caminho do guia). As instruções "USE superpowers:..." do plano equivalem, para o ClineSR, à seção 4 do guia.

## 6. Critérios de aceitação verificáveis

Todos sem Docker: só JDK 21 e SQLite real em arquivo temporário. Os nomes são os testes do plano; cada um precisa falhar antes da implementação pelo motivo certo.

1. **Configuração (T1):** `cd app/backend && ./gradlew test --tests '*EquipesTest*' --tests '*AusenciasTest*' --tests '*RegrasDePlanejamentoTest*' --tests '*AppContextTest*' --tests '*ArquivosReaisForaDoGitTest*'` passa, incluindo `ausenciaSemMotivo`, `ausenciaDePessoaDesconhecidaFalha`, `producaoSemArquivoRealFalha`, `arquivosReaisNoGitignore`.
2. **Capacidade sem sentinela (T2, T3):** `semanaSemCapacidade` (`carla` semana de 02/11 → `0.0` e `SemCapacidade` com demanda 3,0 h), `vencidaViraAtrasoAcumulado` (TG-205 → 6,0 h, `horasAssumidas=true`), `semanaAtualDeHojeAteDomingo`, `jain` (`[0.5, 1.0]` → `0.9`; um valor → `null`).
3. **Agenda ligada à Fase 5 (T4):** `terminoPelaCapacidade` (TG-202 → 06/11, `atrasa=true`), `semAusenciaNaoAtrasa`, `previsaoAposVencimentoNoRisco`, `previsaoPorIssuePelaCapacidade`.
4. **Visibilidade e sem ranking (T5, T6, T7):** `usuarioNaoVeAgregadoDoSquad`, `calendarioDoUsuarioSoAPropriaLinha`, `linhasAlfabeticasSemPosicao`, `contagensNoLugarDeExtremos`, `capacidadeSoLideranca`, `tendenciaSoLideranca`, `pessoasAlfabeticas`; `drillTemOMesmoTamanhoDoCard` em cada rota.
5. **Ausência sem motivo na resposta:** `ausenciasSemMotivoNaResposta` (JSON sem a chave `motivo`).
6. **Worklog sem comentário (T10):** `worklogSemComentario`, `rawNaoGuardaWorklog`, `autorJirauserTraduzido`, `worklogIdempotente`, `liveDesligadoNaoBuscaWorklog` (0 requisições com live `false`), `orcamentoContaRequisicaoDeWorklog`; suíte completa com `SomenteAFabricaCriaClientesJira` e `sh .claude/hooks/test-guard.sh` verdes.
7. **Posse e esforço (T11, T12):** `posseDoTG1` (`ana` 3,0 dias úteis e fração 0,642857), `pessoaInvisivelViraNula`, `indiceDaTask` (14,5), `porPessoaPelaPosse` (`ana` 9,321429; `bruno` 5,178571), `versaoNaResposta` (`"esforço v1"`).
8. **Simulador não grava (T13):** `simuladorNaoGrava` (contagem das tabelas e conteúdo de `ausencias.yaml` iguais), `simuladorPeriodoLongo400` (61 dias), `simuladorPessoaForaDoVisivel403`, `candidatosAlfabeticosSemOrdenarPorFolga`.
9. **Somente leitura (T16):** `nenhumaRotaEscreveAusenciasOuEquipes`, `nenhumCodigoGravaOsYamlDePessoas`.
10. **Frontend (T8, T15):** `cd app/frontend && pnpm vitest run` e `pnpm build` passam, com `cargaSemCapacidadeEmTexto`, `cargaSemColunaDePosicao`, `calendarioLegendaComSimbolos`, `simuladorSoUsaGet`, `coberturaCandidatosAlfabeticos`, `esforcoTresComponentesEIndice`; chaves pt/en sem faltas; uma rodada de capturas a 1440 e 390 px nos três temas.
11. **Diferenças (T9, T16):** `CompatLegadoCapacidadeTest` (capacidade 5,6 h como `legacy/backend/jiradash/metrics/capacity.py`; Jain com teto 3,0) e `DiferencasPessoasDocTest` (cita CP-01..03, CA-01..09, GA-01..04 e os cards novos).
12. **Fim de cada bloco:** suítes completas das duas camadas verdes; varredura de dados pessoais antes do push (guia, seção 6); PR para a `main`, sem merge até o usuário mandar.

## 7. Riscos e pendências

- **Endpoint e paginação do worklog no DC não validados** (`GET /rest/api/2/issue/{key}/worklog`); conferir na 1ª conexão real, depois que o usuário ligar a flag. Registrar em `docs/jira/checklist-primeira-conexao.md` e na matriz.
- **Calibração** de foco, horas por tipo e pesos do esforço só com dados reais.
- **Tendência usa horas de hoje no passado** (D1): aviso obrigatório na resposta e na tela.
- **Sinais "estimativa reduzida" e "worklog retroativo"** (spec mestra 7.2) continuam **sem dono** nas Fases 4 a 8, mesmo com o worklog sincronizado nesta fase; levar ao usuário como pendência no `HANDOFF.md`.
- Base legal com o DPO pendente para uso real.
- Nomes herdados das Fases 4A, 4 e 5 podem ter mudado na execução: conferir antes da Tarefa 1 (seção 8).

## 8. Glossário do que esta fase consome

Legenda: **[main]** existe hoje na `main`; **[Plano N]** previsto pelo plano N (confirmar o nome na `main` antes da Tarefa 1 e, se mudou, atualizar só o nome no plano e registrar no progresso).

| Nome | Arquivo | Origem |
|---|---|---|
| `BusinessCalendar` (`businessDays`, `localDate`, `isBusinessDay`) | `app/backend/src/main/kotlin/br/sidia/painel/time/BusinessCalendar.kt` | [main] Plano 1 |
| `RoleDirectory` (`membersOf`, `squadOf`, `rolesOf`), `Role`, `Visibility`, `AccessControl`, `Authenticator`, `Usernames` | `.../security/` | [main] Planos 1 e 3A |
| `AccessLog` | `.../db/AccessLog.kt` | [main] Plano 3 |
| `Retention` | `.../db/Retention.kt` | [main] Plano 1; agendada no Plano 4A |
| `StatusMap`, `StatusCategory` | `.../domain/StatusMap.kt`, `StatusCategory.kt` | [main] Plano 3; `StatusMap.ordem` no Plano 5 |
| `TimelineBuilder.intervals`, `IssueTimeline`, `TimeInStatusCalculator`, `TimeUnitMode`, `Percentiles` | `.../domain/` | [main] Plano 3 (cenário TG-1) |
| `Syncer`, `ChangelogParser`, `IssueStore`/`SqliteIssueStore`, `SanitizadorRaw` | `.../sync/` | [main] Plano 3 |
| `JiraClient`, `JiraClientFactory` (construtor `internal` com `MockEngine`), `LiveGuard` | `.../jira/` | [main] Planos 1 e 3A |
| `AppContext`, `testContext`, `testContextComBanco` | `.../AppContext.kt`; `src/test/.../TestContext.kt`, `TestContextComBanco.kt` | [main] |
| `AvisoDto`, `DrillRow` | `.../cards/TempoEmStatusDto.kt` | [main] Plano 3 |
| `ChavesDeUsuario.coletar`, `OrcamentoDeRequisicoes`, `SyncGate`, ficha `app/config/cards/sincronizacao.card.yaml` | `.../sync/` | [Plano 4A] |
| `AuditTimeline.responsavelEm/vencimentoEm`, `RegrasDeAuditoria.tiposReview`, `ctx.regrasAuditoria` | `.../auditoria/` | [Plano 4] Tarefas 1 e 2 |
| `executarAuditado` | `.../cards/RotaAuditada.kt` | [Plano 4] Tarefa 7 |
| `IssueSnapshot`, `WorkStore`, `ConsultaDeTrabalho`, `Situacao`, `toAuditIssue` | `.../trabalho/` | [Plano 5] Tarefa 2 |
| `Fluxo` (`emAndamento`, `inicioAtivo`, `semanaDe`, `idadeDias`, `faixasPorTipo`) | `.../trabalho/Fluxo.kt` | [Plano 5] Tarefa 3 |
| `FiltrosDeCard`, `Escopo`, `escopoDe`, `CardDeLeitura`, `rotaDeCard`, `EntradaDeCard` | `.../cards/` | [Plano 5] Tarefa 4 |
| `RiscoCalculator`, `PrevisaoDeTermino`, `PrevisaoCalculator` (coluna PV-06) | `.../cards/riscos/`, `.../cards/previsao/` | [Plano 5] Tarefas 11 e 12 |
| `CardPage`, `KpiCard`, `DrillDrawer`, `useCard`, `Filtros` | `app/frontend/src/shared/` | [Plano 5] Tarefa 4 |
| `shared/csv.ts` (`linhaFiltrosCsv`) | `app/frontend/src/shared/csv.ts` | [Plano 4] |
| `Shell`, `nav.ts`, `t()`, i18n `pt.json`/`en.json` | `app/frontend/src/shell/`, `nav/`, `i18n/` | [main] |
| Padrão de tela | `app/frontend/src/screens/TempoEmStatus/` | [main] Plano 3 |

**Esta fase produz** (consumidos pela Fase 7): `Equipes`, `Ausencias`, `RegrasDePlanejamento`, `HorasDaIssue`, `Capacidade`, `Demanda`, `Ocupacao`, `Jain`, `CargaDaPessoa`, `Agenda`, `WorklogRecord`/`WorklogStore`, `PosseCalculator`/`PosseDePessoa`, `RegrasDeEsforco`/`EsforcoCalculator`, e os DTOs `OcupacaoDto`, `LinhaCargaDto`, `VencimentosDto`, `AusenciaDto`.

## 9. Referência ao plano

Executar [`2026-10-25-plano-06-pessoas-planejamento-ausencias.md`](../plans/2026-10-25-plano-06-pessoas-planejamento-ausencias.md) tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). Bloco 6a (Tarefas 1 a 9) e bloco 6b (Tarefas 10 a 16) são PRs separados, cada um partindo da `main` atualizada.
