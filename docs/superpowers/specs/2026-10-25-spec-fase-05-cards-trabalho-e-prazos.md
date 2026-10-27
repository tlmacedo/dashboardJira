# Spec da fase 5: Cards de trabalho e prazos

Data: 25/10/2026
Plano de implementação: [`docs/superpowers/plans/2026-10-25-plano-05-cards-trabalho-e-prazos.md`](../plans/2026-10-25-plano-05-cards-trabalho-e-prazos.md) (bloco 5a: Tarefas 1 a 9; bloco 5b: Tarefas 10 a 14; um PR por bloco)
Spec mestra do projeto: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 4.2, 4.3, 5.2, 6.1, 8 item 5, 10). Inventário e fórmulas do app atual: `docs/analise/relatorio-cards-app-atual.md`. Visual: `docs/design/plano-visual-frontend.md` (seções 5 e 7).

**Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano. A spec mestra (seções 2 e 3) ainda fala em "autorização expressa por pesquisa"; isso foi **substituído em 25/10** pela regra da flag única (seção 3). Onde houver conflito, vale a regra nova.

Esta spec é autossuficiente: quem a lê não precisa do histórico das conversas que a geraram.

## 1. Intenção e porquê

O app Python atual (`legacy/`) tem cards de trabalho e de resultado (Abertas, Negligenciadas, Fechadas, Metas, Negativas, Riscos, Previsão, Épicos) com números que divergem entre telas (fuso UTC × Manaus, contagens duplicadas, "concluída" só pelo status) e sem atraso justificado. Esta fase os recria sobre os dados já sincronizados no SQLite, com **uma regra só** para "dia", "concluída" e "no prazo" em todas as telas, percentis por tipo de issue, eventos contados pela data do changelog e previsão Monte Carlo com taxa de chegada.

Ela também cria o **modelo de leitura único** (`IssueSnapshot` e `WorkStore`), as métricas transversais (`Fluxo`) e a **rota genérica de card** (`CardDeLeitura`/`rotaDeCard`), que as Fases 6 e 7 reaproveitam.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Bloco 5a: campos de trabalho (prioridade, estimativas, pontos, épico, flag, componentes), ordem do fluxo e resoluções negativas; `IssueSnapshot`/`WorkStore`; `Fluxo`; filtros comuns, escopo visível, rota genérica e kit de tela; cards Abertas, Negligenciadas, Fechadas e Metas; consistência entre telas e documento de diferenças.
- Bloco 5b: Negativas (eventos pela data do changelog), Riscos (pesos em YAML), Previsão (Monte Carlo com chegada) e Épicos (previsão só com amostra própria); telas; documento de diferenças.
- Todo número clicável abre as tasks que o compõem, com o mesmo tamanho.

### Não objetivos

- Meu painel, Visão geral e Central de atenção (Fase 7).
- Colunas "pela capacidade" e "Atrasa?" da previsão por issue, e horas do worklog por pessoa (Fase 6).
- Distribuição da fila sem dono para o Squad Leader (Fase 7).
- Qualquer conexão real ao Jira.

### Antiobjetivos (falha mesmo que "funcione")

- Um papel receber número calculado com tasks que ele não vê (inclusive P50/P85, throughput e Monte Carlo).
- O mesmo item cair em dias diferentes em Fechadas, Metas e Auditoria.
- "0 sem estimativa" quando o dado simplesmente não existe na base (tem de ser aviso).
- Evento antigo contado no período por causa do `updated` recente.
- Previsão que inventa uma data quando o backlog não converge, ou que lança exceção.
- Tabela de pessoas ordenada por desempenho, com coluna de posição ou "melhor/pior".
- Status não mapeado derrubando o card com 500.

## 3. Restrições

### Regras transversais do projeto (valem nesta fase)

- **Flag do Jira (regra 1 do `CLAUDE.md`, reescrita em 25/10):** `JIRA_LIVE_ENABLED` é a **única trava**. `false` (padrão e estado atual): proibido acessar qualquer servidor Jira por qualquer caminho (app, MCP da Atlassian, `curl`, navegador, script), de qualquer IA ou pessoa. `true`: acesso liberado, sem pedido de autorização por pesquisa; a primeira conexão segue `docs/jira/checklist-primeira-conexao.md`. **Só o usuário muda a flag.** Esta fase não precisa do Jira: só fixtures, builders em memória e `MockEngine`. Para ver as telas com números, use os dados de demonstração (`DASHBOARD_DEMO_DATA=true`, Plano 4A, Tarefa 10).
- **Visibilidade por papel:** `/api` nega por padrão; `USUARIO` vê só o próprio; `SQUAD_LEADER` o próprio e o seu squad; `COORDENADOR` e `GERENTE` veem todos; issue sem responsável ou com responsável fora de `papeis.yaml` só para coordenação e gerente. Parâmetros (`pessoa`, `squad`, `kind`) nunca ampliam a visão; pessoa fora do visível = 403.
- **Agregados seguem a visibilidade:** todo número é calculado só sobre o que o papel vê; para `USUARIO`, só as tasks da própria pessoa.
- **Sem ranking de pessoas:** tabelas por pessoa em ordem alfabética, sem "maior/menor"; contagem por pessoa atribuída pela posse no instante do evento.
- **Concluída** = categoria `DONE` **e** `resolution` **e** `resolved`; resolução negativa (lista em `status_map`) = cancelada; divergência vira aviso `DONE_SEM_RESOLUTION` (Qualidade dos dados, Fase 7).
- **Atraso justificado:** "no prazo" e "no prazo ajustado" lado a lado, com **uma única regra compartilhada** por Auditoria (Plano 4), Fechadas e Metas: `NoPrazo.noUniverso` (concluída positiva, resolução não negativa, tipo diferente de `Epic`, vencimento em `[de, ate]`) e `NoPrazo.calcular`, consolidadas no Plano 5, Tarefa 7; as candidatas são lidas por vencimento. Labels de `labels_atraso.yaml`.
- **Fuso America/Manaus** para todo "dia"; **dias úteis por padrão**, `unit=corrido` alterna e fica na URL; semana = segunda a domingo de Manaus.
- **Interface:** temas Claro, Escuro e Sidia; pt-BR e inglês completos (teste de paridade de chaves); WCAG AA; a cor nunca é o único sinal (`✓`, `●`, `▲` com texto); foco e `Esc` na gaveta; CSV com neutralização de fórmula.
- **Dados pessoais:** fixtures só com nomes fictícios; nunca editar nem commitar configs reais.
- **Banco real nos testes** (SQLite em arquivo temporário); **fail-closed** em produção (YAML real obrigatório; `.exemplo` só em dev).
- Conversa e comentários em pt-BR; commits em Conventional Commits pt-BR (`feat(cards): ...`).

### Restrições técnicas da fase

- JQL só por `CardQueryFactory`; cada card tem ficha `app/config/cards/<id>.card.yaml` com teste de snapshot do JQL. A leitura no banco aplica `FiltroDeFicha.de(ficha)` e `removida_em IS NULL`.
- Heurísticas (pesos do risco, parâmetros da previsão, metas) em YAML versionado; `versaoRegras` em toda resposta.
- Monte Carlo com semente injetável; em produção a semente vem do dia de Manaus e dos filtros (card, drill e recarga no mesmo dia dão o mesmo número).
- Status fora do mapa → 200 com `AvisoDto(STATUS_SEM_MAPEAMENTO, bloqueante)` e números nulos.
- Uma migração (Tarefa 1), com o próximo número livre na execução; nunca editar migração mesclada.
- Antes da Tarefa 1, conferir no código da `main` cada nome do glossário (seção 8); se o 4A ou o 4 renomearam algo, atualizar só os nomes no plano e registrar.

## 4. Decisões já tomadas

### Do usuário (spec mestra) e das fases anteriores

- Taxa de chegada ligada por padrão na previsão (`comChegada: true`); `false` reproduz o app atual.
- Previsão por épico só com amostra própria; senão "—".
- "Sem atualização" e "Paradas no status" são duas listas separadas.
- Eventos (reabertas, devolvidas, trocas) pela data do evento no changelog.
- Percentis por tipo de issue; "parada" só para issue em andamento.

### Valor padrão recomendado (25/10, reversível)

1. **Issues sem responsável** (NG-02 e abertas sem dono): só coordenador e gerente veem, como no Tempo em status. O Squad Leader fica sem ver a fila sem dono até a Distribuição da Fase 7. Reverter exigiria regra nova de squad por tipo/projeto em YAML.
2. **P50/P85 do histórico para `USUARIO`:** só com as tasks da própria pessoa; faixa com menos de `AMOSTRA_MINIMA = 5` itens continua visível, com o aviso informativo `AMOSTRA_PEQUENA` (`params`: `tipo`, `n`, `minimo`). Reverter (squad inteiro) seria mais estável, mas revelaria um agregado de colegas.

### Outros padrões do plano (YAML versionado)

- `metas.exemplo.yaml`: meta no prazo 0,90; antecipadas 0,35; alerta de data original 5 pp; faixa de atenção 10 pp.
- `risco.exemplo.yaml`: pesos do app atual (por exemplo `IDADE_ACIMA_P85 30`, `VENCIDA 25`), `alto: 60`, `medio: 30`, `venceEmDias: 3`, `estouro: 1.25`, `trocas: 3`, `diasSemAtualizacao: 7`.
- `previsao.exemplo.yaml`: 16 semanas de histórico, 10.000 sorteios, limite de 104 semanas, confiança baixa < 8 semanas, média < 16, alta = 16; `minItensEpico: 5`.
- Negligenciadas: sem atualização > 7 dias corridos; "a fazer" antiga > 30 dias corridos; parada > `max(3, 2 × mediana do status)`.

## 5. O que fica para quem implementa

- Estrutura interna dos calculadores e do `SqliteWorkStore`, com as assinaturas exatas do plano.
- Biblioteca de gráficos (no máximo dois gráficos por tela), textos de i18n e layout fino dentro do kit (`CardPage`, `KpiCard`, `DrillDrawer`).
- Mock de desenvolvimento do Vite (dados fictícios só no servidor de dev).
- Conferir no `legacy/` cada fórmula antes de aceitar um teste de compatibilidade.
- Escolhas que o plano deixa abertas: a mais segura e reversível, registrada como `Ruling` no arquivo de progresso.

## 6. Critérios de aceitação (verificáveis)

| Critério | Prova (teste do plano) |
|---|---|
| Campos de trabalho extraídos, ausência vira nulo sem exceção | `extraiPrioridadeEEstimativas`, `campoCustomSemIdFicaNulo`, `fichaDeSincronizacaoPedeCamposNovos` (T1) |
| Leitura por instância, período `[de, ate)`, removidas fora | `abertasEResolvidas`, `fronteiraDoPeriodo`, `removidaNaoAparece`, `instanciaSeparada` (T2) |
| Concluída exige `resolution`; ciclo em úteis e corridos; fronteira de Manaus | `concluidaExigeResolution`, `cicloEmUteisECorridos`, `resolucaoNaFronteiraDeManaus` (T3) |
| Amostra pequena avisa sem esconder o número | `amostraPequenaGeraAviso` (T3), `historicoDoUsuarioSoComAsProprias` (T5) |
| Agregado só com o visível; sem responsável só para coordenação | `agregadoSoComOVisivel`, `semResponsavelSoParaCoordenacao`, `pessoaForaDoVisivel403` (T4) |
| Filtros inválidos dão 400; status não mapeado dá 200 com aviso; toda consulta auditada | `datasInvalidasDao400`, `statusSemMapaDa200ComAviso`, `consultaGravaAccessLog` (T4) |
| Abertas e Negligenciadas com listas separadas e P85 do próprio tipo | `contagensDeAbertas`, `acimaDoP85UsaOProprioTipo`, `negligenciadasSeparadas`, `camposDeTrabalhoIndisponiveisViraAviso` (T5) |
| Fechadas e Metas com no prazo e ajustado, data original e pessoas em ordem alfabética | `noPrazoEAjustado` (T6), `metasDoCenario`, `dataOriginal`, `tabelaPorPessoaAlfabetica` (T7) |
| Drill do mesmo tamanho do número em todo card | `drillTemOMesmoTamanhoDoCard` (T5, T6, T7, T10), `drillNivel` (T11), `drillBacklogMesmoTamanho` (T12) |
| Mesmo "no prazo" e mesmo dia em três telas | `RegraUnicaNoPrazoTest.mesmoUniversoComCanceladaEpicEResolvidaAntes` (T7: cancelada com vencimento, Epic e resolvida antes do período → `(5, 4, 5)` em Auditoria, Fechadas e Metas), `canceladaComVencimentoForaDoUniverso`, `epicForaDoUniverso` (T7), `mesmoNoPrazoEmTresTelas`, `mesmaSemanaEmFechadasETempoEmStatus` (T9) |
| Eventos pela data do changelog | `reabertaForaDoPeriodoNaoConta`, `porResponsavelNoInstanteDoEvento` (T10) |
| Riscos com pesos versionados e fatores exclusivos | `riscoDoCenario`, `fatoresExclusivos`, `regrasEstritas` (T11) |
| Previsão estável, conservadora e honesta quando não converge; só com o visível | `chegadaIgualAVazaoNaoConverge`, `mesmaSementeMesmoResultado`, `confiancaPelaAmostra`, `previsaoDoUsuarioSoComAsProprias` (T12) |
| Épicos só com amostra própria | `semAmostraPropriaTraco`, `comAmostraPropria` (T12) |
| Telas com símbolos além da cor, "—" no bloqueante, sem coluna de posição | `metasPilulaComSimboloETexto`, `avisoBloqueanteMostraTraco`, `tabelaPorPessoaAlfabeticaSemColunaDePosicao` (T8), `sinaisPorPessoaAlfabeticosSemPosicao` (T13) |
| Paridade pt/en | `paridadeDeChavesPtEn` (T4) |
| Diferenças documentadas por ID | `documentoCitaOsIds` (T9 e T14) |
| Suítes completas verdes | `./gradlew test`, `pnpm vitest run`, `pnpm build` |

## 7. Riscos e pendências

- **`Story Points`, `Epic Link` e `Flagged`** estão "a confirmar" na primeira conexão; sem eles os cards mostram aviso (`CAMPOS_DE_TRABALHO_INDISPONIVEIS`, `EPICO_INDISPONIVEL`), não zero.
- **Base sincronizada antes da Tarefa 1** tem prioridade e estimativas nulas: o aviso cobre; a próxima sincronização preenche.
- **Ordem do fluxo** (`status_map.ordem`) só existe com o mapa real; sem ela, "devolvidas" = 0 com aviso `ORDEM_DO_FLUXO_AUSENTE`.
- **Amostras pequenas** para `USUARIO` tornam P50/P85 instáveis; o aviso deixa isso explícito.
- **Dependência de nomes** dos Planos 4A e 4 (glossário): renomeações precisam ser conferidas antes da Tarefa 1.
- Pendências da Fase 6: "pela capacidade", "Atrasa?", horas do worklog por pessoa; `PrevisaoDeTermino` aqui sempre devolve `null`.

## 8. Glossário (nomes das fases anteriores que esta fase consome)

| Nome | Origem |
|---|---|
| `IssueTimeline`, `TimelineBuilder.intervals`, `StatusInterval`, `firstEntered`, `lastLeft`, `ORDEM_NATURAL_DE_CHAVES` | `domain/IssueTimeline.kt`, `domain/TimelineBuilder.kt` (Plano 3) |
| `StatusMap`, `StatusCategory`, `UnmappedStatusException` | `domain/StatusMap.kt` (Plano 3) |
| `Percentiles.of/median`, `TimeUnitMode { UTIL, CORRIDO }` | `domain/Percentiles.kt`, `domain/TimeInStatusCalculator.kt` (Plano 3) |
| `BusinessCalendar.businessDays/calendarDays/localDate/isBusinessDay` | `time/BusinessCalendar.kt` (Plano 1) |
| `ChangeEventRecord`, `ChangelogParser`, `SqliteIssueStore.upsert`, `OsVersionExtrator` | `sync/` (Plano 3) |
| `AvisoDto` (com `params` desde o 4A), `DrillRow` | `cards/TempoEmStatusDto.kt` (Plano 3) |
| `Visibility.visibleUserIds`, `RoleDirectory.membersOf/squadOf/squadsLedBy`, `Principal`, `Role` | `security/` (Plano 1) |
| `AccessLog.record`, `testContextComBanco` | `db/AccessLog.kt`, testes (Plano 3) |
| `executarAuditado` | `cards/RotaAuditada.kt` (Plano 4, Tarefa 7) |
| `LabelsDeAtraso.justifica`, `NoPrazo.calcular`, `AuditIssue`, `AuditTimeline.responsavelEm`, `ctx.labelsDeAtraso`, `regrasAuditoria.tiposReview` | `domain/LabelsDeAtraso.kt`, `domain/NoPrazo.kt`, `auditoria/` (Plano 4) |
| `FiltroDeFicha.de(spec)`, `ctx.fichas`, `ctx.campos`, ficha `sincronizacao.card.yaml`, coluna `issue.removida_em` | Plano 4A (Tarefas 3, 6 e 8) |
| `DadosDeDemonstracao`, `DASHBOARD_DEMO_DATA` | Plano 4A (Tarefa 10) |
| `shared/csv.ts` (`linhaFiltrosCsv`), `Shell`, `t()` | frontend (Planos 3 e 4) |

Nomes que esta fase **produz** e as Fases 6 e 7 consomem: `IssueSnapshot`, `WorkStore`/`SqliteWorkStore`, `Fluxo` (inclusive `AMOSTRA_MINIMA` e `avisosDeAmostra`), `MonteCarlo`, `FiltrosDeCard`, `Escopo`/`escopoDe`, `CardDeLeitura`/`rotaDeCard`, `NoPrazo.noUniverso` e `NoPrazo.calcular` (regra única de "no prazo"), `WorkStore.candidatasNoPrazo` (única leitura das candidatas, usada por Fechadas e por `MetasCalculator.calcular(candidatasNoPrazo, resolvidasNoPeriodo, ...)`), `NoPrazo.classificar`, `ConsultaDeTrabalho.vencimentoDe/vencimentoAte`, `PrevisaoDeTermino`, kit `CardPage`/`KpiCard`/`useCard`/`Filtros`/`shared/DrillDrawer`.

**Cenário de teste TRB-1** (relógio sexta 30/10/2026, 08:00 de Manaus; feriados 12/10 e 02/11; pessoas `ana`, `bruno` do Alfa, `carla` do Beta, `lider.alfa`, `coord.um`, `gerente.exemplo`): definido por inteiro no plano, antes do bloco 5a. É a base de todos os números esperados.

## 9. Referência

Plano: `docs/superpowers/plans/2026-10-25-plano-05-cards-trabalho-e-prazos.md`. Depende dos Planos 4A e 4. Índice: `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`. Guia para outra IA: `docs/guia-execucao-outra-ia.md`. Inventário dos cards: `docs/analise/relatorio-cards-app-atual.md`.
