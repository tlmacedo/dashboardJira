# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-06-pessoas-planejamento-ausencias.md

Bloco **6a** (Tarefas 1 a 9) na branch `feat/pessoas-planejamento-6a`, a partir da `main` `b8d203b` (Plano 5b mesclado, PR #34). O bloco 6b (Tarefas 10 a 16) NÃO está neste arquivo ainda.

Ponto de partida (main `b8d203b`): backend 1224 testes, 0 falhas.

## Estado por tarefa

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 1 Equipes, ausências e regras de planejamento em YAML | Concluída (backend 1244, 0 falhas) | ver `git log` | `pessoas/{Equipes,Ausencias,RegrasDePlanejamento,LeituraEstrita}.kt`; modelos `equipes/ausencias/planejamento.exemplo.yaml`; `AppContext` ganhou `equipes`, `ausencias`, `regrasPlanejamento` (obrigatórios em produção) |
| 2 Horas por issue e capacidade líquida | Concluída (18 testes novos, 0 falhas; vermelho visto com stubs) | ver `git log` | `pessoas/{HorasDaIssue,Capacidade}.kt`; cenário CAP-1 em `src/test/.../pessoas/Cap1.kt` |
| 3 Demanda semanal, ocupação, atraso acumulado e Jain | Concluída (12 testes novos, 0 falhas; vermelho visto com stub) | ver `git log` | `pessoas/Demanda.kt` (`Ocupacao`, `SemanaDeCarga`, `CargaDaPessoa`, `Demanda`, `Jain`) |
| 4 Agenda e ligação com Riscos e Previsão | Concluída (backend 1288, 0 falhas; vermelho visto com stub) | ver `git log` | `pessoas/{Agenda,MotorDePessoas}.kt`; Riscos passa a receber `agenda.comoPrevisao`; PV-06 ganhou `pelaCapacidade` e `atrasa` no DTO (a coluna na tela entra na T8) |

## Rulings

- Ruling: T4 — o plano manda acrescentar os casos em `RiscoCalculatorTest` e `PrevisaoCalculatorTest`; os casos novos ficaram em arquivos novos (`RiscoComAgendaTest`, `PrevisaoComCapacidadeTest`) para não editar arquivos de teste que outros blocos tocam. Custo: nenhum.
- Ruling: T4 — o exemplo do plano para o PV-06 ("TG-202 → pelaCapacidade 2026-11-06, atrasa=true") usa uma task `Open`, mas o PV-06 só lista task EM ANDAMENTO (Ruling do 5b). O teste coloca a TG-202 em `In Progress`. Custo se errado: listar também as não iniciadas no PV-06.
- Ruling: T4 — a fila de cada pessoa na Agenda de Riscos e de Previsão usa TODAS as abertas dela dentro da visão, SEM os filtros de tipo, prioridade e OS do card (uma segunda leitura do `WorkStore`), senão filtrar por tipo adiantaria o término das demais. Custo se errado: passar as abertas já filtradas (uma consulta a menos, previsão enganosa com filtro).
- Ruling: T4 — `Agendamento.atrasa` quando NÃO cabe no horizonte (`termino == null`): `true` se o vencimento está dentro do horizonte, `false` se está além dele (não dá para afirmar). Sem vencimento nunca atrasa. A fila considera "iniciada" quem tem `Fluxo.inicioAtivo != null`, a mesma regra da Demanda. A ordem de prioridade é a mesma de Abertas e Metas (`Highest` a `Lowest`); a lista existe em 3 lugares (Minor: extrair).
- Ruling: T4 — `PrevisaoIssueDto.atrasa` fica nulo quando a task não tem vencimento ou a previsão não cabe no horizonte. `Agenda.agendarTodas` (além do `comoPrevisao` do plano) devolve os agendamentos por chave para a Previsão.

- Ruling: T3 — `Demanda.carga` filtra por `assignee == pessoa` e descarta categoria DONE dentro dela (o chamador pode passar todas as abertas). `horasAssumidas` vale se QUALQUER issue da pessoa (inclusive a vencida) teve horas assumidas. Semana sem capacidade E sem demanda também é `SemCapacidade` (a tela mostra "sem capacidade"; o Jain a ignora). Sem vencimento NÃO aplica o mínimo de semanas (usa o valor configurado). O que passa do horizonte (vencimento distante) fica fora das semanas mostradas. Custo se errado: ajustar uma linha em `Demanda.carga`.

- Ruling: T2 — restante do Jira igual a ZERO não vale como informação (cai para estimativa − registrado ou horas por tipo). O plano diz só "`remainingS` primeiro"; o Jira zera o restante ao estourar a estimativa e a task segue aberta, e contar 0 h tiraria a task da demanda. Custo se errado: trocar `takeIf { it > 0 }` por `!= null` em `HorasDaIssue.de`.
- Ruling: T2 — o tipo da issue casa com `horasPorTipo` sem diferenciar maiúsculas e sem espaços nas pontas. Custo: nenhum.

- Ruling: nomes herdados conferidos na `main` (Plano 5b): `IssueSnapshot`, `WorkStore`, `ConsultaDeTrabalho`, `Situacao`, `toAuditIssue`, `Fluxo`, `FiltrosDeCard`, `Escopo`, `escopoDe`, `RiscoCalculator`, `PrevisaoDeTermino`, `PrevisaoCalculator`, `SerieSemanal`, `StatusMap.ordem`, `TimelineBuilder.intervals`, `AuditTimeline.responsavelEm/vencimentoEm`, `BusinessCalendar`, `RoleDirectory` existem. Já NÃO existem como o plano diz: `CardDeLeitura`/`rotaDeCard`/`KpiCard`/`DrillDrawer` (o 17a trocou por `CartaoDeLeitura`, `Cartao`, `GavetaDeTasks` e o drill genérico `/api/cartoes/{id}/tasks`; ver Ruling do Plano 5) — cada cartão novo daqui se registra no `catalogoPadrao()`.
- Ruling: T1 — o YAML de equipes usa a chave raiz `membros:` e o de ausências `ausencias:` (o plano não fixa o formato); `fracao` de ausência é opcional (padrão 1.0). Custo se errado: renomear a chave raiz nos dois leitores e nos modelos.
- Ruling: T1 — `RoleDirectory` ganhou `conhece(userId)` (uma linha) porque nenhum método existente diz se um id consta no diretório em qualquer data; `rolesOf` depende da vigência. Custo: remover o método e validar por `squadOf`/`rolesOf` numa data.
- Ruling: T1 — `Ausencias.mais` (simulador) NÃO revalida a sobreposição: a hipótese pode cruzar uma ausência real e então o dia vale a MAIOR fração. A carga do YAML continua recusando sobreposição da mesma pessoa. Custo se errado: validar também em `mais` e fazer o simulador recusar a hipótese cruzada.
- Ruling: T1 — as mensagens de erro de `equipes.yaml` e `ausencias.yaml` chegam inteiras à falha da subida (`lerYamlCitandoItem` no `AppContext`), porque o plano pede que a carga falhe "citando o id/linha". Elas citam o arquivo, o id (username do Jira, que já é público dentro do painel) e o item `#N`; nunca o valor de `motivo` nem outro valor lido. Os demais YAML continuam citando só o tipo da exceção. Custo se errado: trocar `lerYamlCitandoItem` por `lerYaml`.
- Ruling: T1 — a leitura dos testes de T1 foi escrita junto da implementação (stubs sobrescritos antes de ver o vermelho); a prova de que os testes mordem foi feita por mutação (remover a checagem de id no `Equipes`, a recusa de `motivo` e `folga < ocupacaoAtencao`: derrubam `membroForaDoPapeisFalha`, `ausenciaSemMotivo` e `regrasEstritasELimites`). `arquivosReaisNoGitignore` passa de primeira porque o `.gitignore` já cita os arquivos reais (decisão anterior); a mutação equivalente é apagar a linha.
