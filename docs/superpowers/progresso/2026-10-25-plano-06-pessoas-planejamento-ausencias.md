# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-06-pessoas-planejamento-ausencias.md

Bloco **6a** (Tarefas 1 a 9) na branch `feat/pessoas-planejamento-6a`, a partir da `main` `b8d203b` (Plano 5b mesclado, PR #34). O bloco 6b (Tarefas 10 a 16) NÃO está neste arquivo ainda.

Ponto de partida (main `b8d203b`): backend 1224 testes, 0 falhas.

## Estado por tarefa

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 1 Equipes, ausências e regras de planejamento em YAML | Concluída (backend 1244, 0 falhas) | ver `git log` | `pessoas/{Equipes,Ausencias,RegrasDePlanejamento,LeituraEstrita}.kt`; modelos `equipes/ausencias/planejamento.exemplo.yaml`; `AppContext` ganhou `equipes`, `ausencias`, `regrasPlanejamento` (obrigatórios em produção) |

## Rulings

- Ruling: nomes herdados conferidos na `main` (Plano 5b): `IssueSnapshot`, `WorkStore`, `ConsultaDeTrabalho`, `Situacao`, `toAuditIssue`, `Fluxo`, `FiltrosDeCard`, `Escopo`, `escopoDe`, `RiscoCalculator`, `PrevisaoDeTermino`, `PrevisaoCalculator`, `SerieSemanal`, `StatusMap.ordem`, `TimelineBuilder.intervals`, `AuditTimeline.responsavelEm/vencimentoEm`, `BusinessCalendar`, `RoleDirectory` existem. Já NÃO existem como o plano diz: `CardDeLeitura`/`rotaDeCard`/`KpiCard`/`DrillDrawer` (o 17a trocou por `CartaoDeLeitura`, `Cartao`, `GavetaDeTasks` e o drill genérico `/api/cartoes/{id}/tasks`; ver Ruling do Plano 5) — cada cartão novo daqui se registra no `catalogoPadrao()`.
- Ruling: T1 — o YAML de equipes usa a chave raiz `membros:` e o de ausências `ausencias:` (o plano não fixa o formato); `fracao` de ausência é opcional (padrão 1.0). Custo se errado: renomear a chave raiz nos dois leitores e nos modelos.
- Ruling: T1 — `RoleDirectory` ganhou `conhece(userId)` (uma linha) porque nenhum método existente diz se um id consta no diretório em qualquer data; `rolesOf` depende da vigência. Custo: remover o método e validar por `squadOf`/`rolesOf` numa data.
- Ruling: T1 — `Ausencias.mais` (simulador) NÃO revalida a sobreposição: a hipótese pode cruzar uma ausência real e então o dia vale a MAIOR fração. A carga do YAML continua recusando sobreposição da mesma pessoa. Custo se errado: validar também em `mais` e fazer o simulador recusar a hipótese cruzada.
- Ruling: T1 — as mensagens de erro de `equipes.yaml` e `ausencias.yaml` chegam inteiras à falha da subida (`lerYamlCitandoItem` no `AppContext`), porque o plano pede que a carga falhe "citando o id/linha". Elas citam o arquivo, o id (username do Jira, que já é público dentro do painel) e o item `#N`; nunca o valor de `motivo` nem outro valor lido. Os demais YAML continuam citando só o tipo da exceção. Custo se errado: trocar `lerYamlCitandoItem` por `lerYaml`.
- Ruling: T1 — a leitura dos testes de T1 foi escrita junto da implementação (stubs sobrescritos antes de ver o vermelho); a prova de que os testes mordem foi feita por mutação (remover a checagem de id no `Equipes`, a recusa de `motivo` e `folga < ocupacaoAtencao`: derrubam `membroForaDoPapeisFalha`, `ausenciaSemMotivo` e `regrasEstritasELimites`). `arquivosReaisNoGitignore` passa de primeira porque o `.gitignore` já cita os arquivos reais (decisão anterior); a mutação equivalente é apagar a linha.
