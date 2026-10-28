# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-05-cards-trabalho-e-prazos.md

Bloco **5a** (Tarefas 1 a 9) na branch `feat/cards-trabalho-5a`, a partir da `main` com o Plano 4 (Tarefas 4 a 9) e o 17a. O bloco 5b (Tarefas 10 a 14) NÃO está neste arquivo ainda.

Ponto de partida (main `fa8493c`): backend 986 testes, frontend 143 (números do HANDOFF), 0 falhas.

## Estado por tarefa

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 0 Minor do Plano 4: rota da negação CSRF sem chave de issue | Concluída | `b1a75dd` | `registrarNegacaoCsrf` usa `rotaParaRegistro` |
| 1 Campos de trabalho, ordem do fluxo e resoluções negativas | Concluída (backend 1000 testes, 0 falhas) | `d4882df` | Migração `V2__campos_de_trabalho.sql`; `CamposDeTrabalhoExtrator`; `StatusMap.ordem`/`ehResolucaoNegativa`; `upsert` ganha `idsDeTrabalho` |
| 2 `IssueSnapshot` e `WorkStore` | Concluída | `1ef5ea6` | `trabalho/`; cenário TRB-1 em `src/test/.../trabalho/Trb1.kt` |
| 3 `Fluxo` | Concluída | `c133360` | `AvisoDto.AMOSTRA_PEQUENA` |
| 7 Metas e prazos (domínio e API) + regra única de "no prazo" | Concluída (backend 1117, 0 falhas) | `b3e6253` | `NoPrazo.noUniverso`/`classificar`, `WorkStore.candidatasNoPrazo`, cartões `MT-01` a `MT-14`, `RegrasDeMetas` (`metas.yaml`, obrigatório em produção), `RegraUnicaNoPrazoTest` `(5, 4, 5)` nas três telas |
| 9 Consistência entre telas e diferenças esperadas | Concluída (backend 1125 sem cache, frontend 193; 0 falhas) | ver `git log` | `ConsistenciaEntreTelasTest`, `CompatLegadoTrabalhoTest`, `DiferencasTrabalhoDocTest`, `docs/analise/diferencas-esperadas-trabalho-e-prazos.md` |
| 8 Telas Abertas, Negligenciadas, Fechadas e Metas | Concluída (frontend 193, 0 falhas; build ok) | `dff2a27` | Rotas no `App.tsx`; `ContagensEmBarras`, `TabelaOrdenavel`; mock do Vite com os 40 cartões |
| 6 Fechadas (domínio e API) | Concluída (backend 1086, 0 falhas) | `7683e33` | Cartões `FE-01` a `FE-12`; ficha `fechadas.card.yaml` sem exclusão de status |
| 5 Abertas e Negligenciadas (domínio e API) | Concluída (backend 1066, 0 falhas) | `ca51f9f` | Cartões `AB-01/02/04 a 09` e `NG-01 a 06` no `catalogoPadrao()`; coluna e filtro de "último movimento real" (I-06) |
| 4 Filtros, escopo, rota genérica de card e kit de tela | Concluída (backend 1039, frontend 156; 0 falhas) | `c05cd6e` | `FiltrosDeCard`, `Escopo`, `CardDeLeitura`/`CartaoDeLeitura`/`rotaDeCard` (adaptação ao 17a); `shared/{CardPage,Filtros,useCard}` |

## Correções da revisão final do 5a

- I1 corrigido: `Fluxo.exigirStatusMapeados` é a checagem comum de Fechadas e Metas (candidatas por vencimento + resolvidas); status fora do mapa numa candidata resolvida antes do período agora vira o aviso bloqueante nas duas telas (testes em `FechadasRoutesTest` e `ConsistenciaEntreTelasTest`).
- M2 corrigido: `faixaDeIdade` com limite superior inclusivo (`<= 7`, `<= 14`, `<= 30`), com testes de borda.
- M4 corrigido: `ContratoDeCartoesTest.cadaTelaDeTrabalhoTemParPositivoNaDemonstracao`. Ruling: a demonstração NÃO foi ampliada; FE e MT vinham zerados porque `config/status_map.exemplo.yaml` não mapeava `Cancelled` (status da fixture) e o aviso bloqueante zerava os cartões; o modelo agora mapeia `Cancelled: done`. Custo se errado: remover a linha e ampliar a demonstração.
- Minor ADIADOS (registrados, sem correção neste PR):
  - M1: a `V2` não é retroativa (issues já gravadas ficam com `priority` NULL até atualizar; `temCamposDeTrabalho` vira verdadeiro com UMA issue com prioridade e o aviso some; AB-06/MT-12 mostram "(sem)" inflado). Em banco novo não ocorre; a recarga completa do Plano 14b, Tarefa 7, é obrigatória se houver dados anteriores (registrado também em `docs/jira/checklist-primeira-conexao.md` e no plano 14).
  - M3: a coluna "prazo" de FE-12 classifica também concluídas com vencimento fora do período; a linha pode divergir do numerador de FE-05.
  - M5: squad inexistente dá 400 e squad de outro líder dá 200 vazio (permite enumerar nomes de squad).
  - M6: issue com `updated` nulo nunca entra em NG-01, sem aviso.
  - M7: a tabela por squad de Metas usa o squad da posse na resolução.
  - M8: capturas visuais das telas pendentes (sem navegador no ambiente).

## Bloco 5b (Tarefas 10 a 14), branch `feat/cards-trabalho-5b`

Estado final: backend 1220 testes (`--rerun-tasks`), frontend 231, `pnpm build` ok, varredura de dados pessoais limpa; 0 falhas. Revisão: autorrevisão por tarefa (sem subagente, por regra do despacho); a revisão final independente e o PR ficam para o orquestrador.

Ponto de partida (main `ad17750`, 5a mesclado): backend 1129 testes, 0 falhas.

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 10 Negativas (domínio e API) | Concluída (backend 1146, 0 falhas) | ver `git log` | Cartões `NE-01` a `NE-08` e `NE-10` (o NE-09 é do Plano 10); cenário NEG-1 em `src/test/.../trabalho/Neg1.kt` |

| 11 Riscos (regras em YAML, domínio e API) | Concluída (backend 1165, 0 falhas) | ver `git log` | Cartões `RI-01` a `RI-03`; `risco.exemplo.yaml` + `AppContext.regrasDeRisco` (obrigatório em produção); `ContratoDeCartoesTest` agora exige par positivo também em NE e RI |

| 12 Previsão e Épicos (Monte Carlo com taxa de chegada) | Concluída (backend 1216, 0 falhas) | ver `git log` | Cartões `PV-01` a `PV-06` e `EP-01`; `SerieSemanal` (função única de entradas, saídas e canceladas por semana de Manaus); `previsao.exemplo.yaml` + `AppContext.regrasDePrevisao` (obrigatório em produção) |

| 13 Telas Negativas, Riscos, Previsão e Épicos | Concluída (frontend 231, 0 falhas; `pnpm build` ok; backend 1216, 0 falhas) | ver `git log` | Rotas no `App.tsx`; `FatoresDoRisco` (gaveta de pontos por fator); mock do Vite com os 4 cards e os cartões NE/RI/PV/EP |

| 14 Diferenças esperadas (bloco 5b) | Concluída (backend 1220, 0 falhas) | ver `git log` | Seção 5b em `docs/analise/diferencas-esperadas-trabalho-e-prazos.md`; `CompatLegadoTrabalhoTest` confere `forecast.py` e `risk.py` (pulado se `legacy/` sumir); `DiferencasTrabalhoDocTest` exige NE-01..NE-10, RI, PV e EP-01; HANDOFF, índice e roteiro (A4) atualizados |

### Minor ADIADOS do 5b (registrados, sem correção neste PR)

- M1: o card Épicos lê TODAS as issues do escopo (qualquer situação) para calcular o progresso; com base grande pode pesar. Alternativa: filtro de épico no `WorkStore`. Arquivo: `cards/epicos/CardEpicos.kt`.
- M2: Negativas e Riscos seguem a visão pelo responsável ATUAL (`permiteAtual`): o evento atribuído a um ex-responsável só aparece para quem vê a task hoje. Alternativa: `permitePosse` nas listas de evento (ver o Ruling de Negativas).
- M3: `FatoresDoRisco` repete a lógica de foco e Tab da `GavetaDeTasks`; extrair um hook (`useGavetaModal`) quando houver uma terceira gaveta.
- M4: a demonstração (`DadosDeDemonstracao`) não tem épicos: `ContratoDeCartoesTest.cadaTelaDeTrabalhoTemParPositivoNaDemonstracao` cobre NE, RI e PV, mas não EP (o `PrevisaoEpicosRoutesTest` cobre EP-01 com fixture própria). Ampliar a demonstração com `Epic Link` é trabalho do Plano 14b/demonstração.
- M5: as semanas de 2, 4 e 8 do PV-05 e o mínimo de 5 ciclos do PV-06 estão no código; só os parâmetros de `previsao.yaml` são configuráveis.
- M6: devolvidas ignoram transições em que `from` ou `to` estão fora da `ordem` (ex.: `On Hold`); só status da ordem entram.
- M7: `TempoEmStatusRoutes` continua sem usar `FiltrosDeCard`/`escopoDe` (herdado do 5a).
- M8: captura visual das quatro telas (1440 e 390 px, três temas) pendente: sem navegador no ambiente.

### Rulings do 5b

- Ruling: telas do 5b (T13) — o Passo 5 do plano (captura visual, 1440 e 390 px, três temas) NÃO foi executado: o ambiente não tem navegador (mesma pendência do 5a, M8). Custo se errado: nenhum no código; falta a conferência visual do usuário.
- Ruling: telas do 5b (T13) — o `PrevisaoDto` ganhou o campo `unit` (`util`/`corrido`), que a T12 esqueceu: a tela precisa dele para rotular idade e restante do PV-06. Riscos: a "gaveta com os pontos por fator" é a `FatoresDoRisco` (mesmo comportamento da `GavetaDeTasks`: foco, Tab preso, Esc devolve o foco), porque os pontos por fator não são tasks do drill; o `detalhe` da gaveta de tasks também traz os fatores em texto (pt). Negativas: a taxa leva `▲` com "Acima de 20%" ou `✓` com "Até 20%" (o limite sai do DTO); sem concluídas é traço com "Sem base". Previsão: "Mais de N semanas" quando as semanas são nulas e há backlog; "Sem backlog" com backlog 0; PV-05 sem gaveta. Épicos: legenda explica o traço (nunca a vazão do time).

- Ruling: Previsão (T12) — `SerieSemanal` (`trabalho/SerieSemanal.kt`) é a função ÚNICA das séries semanais: `calcular` devolve por semana (segunda a domingo de Manaus) as ENTRADAS (por `created`), as SAÍDAS (conclusão positiva por `resolved`) e as CANCELADAS à parte, com as tasks de cada número; `ultimaSemanaFechada(hoje)` dá a última semana fechada. `Fluxo.throughputSemanal`/`chegadasSemanais` agora delegam a `SerieSemanal.contar` (mesmo resultado; os testes do 5a seguem verdes). O 17b ("Fluxo da semana") deve chamar `SerieSemanal.calcular`. Regressão: `PrevisaoCalculatorTest.mesmaSerieEMesmoMonteCarloQueAntes` e `SerieSemanalTest.mesmaSerieQueOFluxoPorContagem` (mutação conferida: trocar `concluidaPositiva` por `resolved != null` derruba 6 testes).
- Ruling: Previsão (T12) — percentis do Monte Carlo como o `np.percentile` do legado (interpolação linear e truncamento para inteiro); como o sorteio que não converge vale `limite + 1`, um percentil acima do limite (interpolado inclusive) vira `null` ("mais de 104 semanas"). Com vazão vazia ou só zeros também é `null`; backlog 0 dá `0, 0, 0`.
- Ruling: Previsão (T12) — o sorteio usa só as semanas a partir da PRIMEIRA com atividade (entrada, saída ou cancelamento); os zeros anteriores à base não são histórico. `semanasComDado` é esse número e a confiança sai dele (baixa < 8, média < 16, alta). O DTO ganhou `semanasComDado`, `semanasHistorico`, `limiteSemanas`, `comChegada` e `entregasNaJanela`, e o aviso informativo `SEM_VAZAO_HISTORICA` (sem entrega na janela).
- Ruling: Previsão (T12) — data = segunda-feira da k-ésima semana INTEIRA depois da semana em curso (a semana de hoje não entra na vazão); backlog 0 não tem data. Custo se errado: trocar `plusWeeks(k)` por `plusWeeks(k - 1)` em `PrevisaoCalculator.quandoDto`.
- Ruling: Previsão (T12) — o histórico lido vai da primeira semana da janela até agora (sem limite superior): quem foi criado na janela e entregue na semana em curso precisa estar na lista para a chegada contar tudo. Ficha `previsao` = 3 tipos Checklist (o backlog de Abertas) SEM excluir status (as canceladas formam a série à parte). PV-06 só lista tasks EM ANDAMENTO (o ciclo começa na primeira entrada em andamento) e usa a unidade do card (`unit`).
- Ruling: Previsão (T12) — cartões: PV-02 abre o backlog (o numerador da previsão); PV-03 abre as entregas da janela (segmento `vazao`); PV-05 ("quantos em 2/4/8 semanas") é projeção sem tasks por trás e fica SEM gaveta (`temDrill = false`; `metaDeTrabalho` ganhou o parâmetro `temDrill`). Custo se errado: dar ao PV-05 o segmento `vazao`.
- Ruling: Épicos (T12) — itens = tasks com épico, sem os tipos de review (`regrasAuditoria.tiposReview`) e SEM as canceladas; progresso com seis casas; `maisAntigo` = criação da task aberta mais antiga; a previsão (P85) usa a vazão do próprio épico da primeira entrega dele à última semana fechada e só com `minItensEpico` entregas dele na janela. O card respeita a visão (squad e pessoa inclusive) mas IGNORA tipo e prioridade, como o legado. Os segmentos do drill usam um id POSICIONAL (`epico:<n>:itens|concluidos|vencidos`, n = posição 1-based do épico na lista ordenada por chave, a mesma do card) porque o `segmento` vai para o `access_log` e a chave de issue, ou algo derivável dela, nunca deve ir (a primeira versão usava SHA-256 truncado, que se recupera por enumeração: Important I1 da revisão final, corrigido). Custo/reversão: o índice muda quando muda a lista de épicos da visão; serve só ao drill do mesmo instante (índice fora do intervalo dá 400 `SEGMENTO_INVALIDO`). Sem segredo novo. Ficha `epicos` = 6 tipos, qualquer situação (lê todas as issues do escopo: custo de leitura a observar com a base real).

- Ruling: Riscos (T11) — o score segue o `risk.py` do app anterior: total limitado a 100; SEM_ESTIMATIVA só quando não há estimativa NEM pontos; REABERTA = alguma vez saiu de DONE; trocas = total de mudanças de responsável; bloqueada = categoria BLOCKED ou flag. Diferenças deliberadas (a documentar na T14): a parada usa o piso de 3 dias úteis e vale sem mediana (regra de Negligenciadas; o legado usava 2 dias e exigia mediana) e SEM_ATUALIZACAO vale só para tasks em andamento (NG-01; o legado contava toda aberta). Custo se errado: trocar `NegligenciadasCalculator` por contas próprias.
- Ruling: Riscos (T11) — base sem prioridade em nenhuma issue (`temCamposDeTrabalho` falso) tira o fator SEM_ESTIMATIVA e mostra o aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS`, para o card não punir "sem estimativa" por falta de sincronização (mesmo cuidado do Foco da revisão 3). A `RiscoCalculator.avaliar` ganhou o parâmetro opcional `camposDeTrabalho`.
- Ruling: Riscos (T11) — `risco.yaml` entrou no `AppContext.load` (`regrasDeRisco`, último parâmetro com padrão); em produção o arquivo real é obrigatório e o `.exemplo` só em dev. Pendência de implantação: criar `risco.yaml` a partir do modelo. O DTO ganhou `baixo`, `emRisco`, `limiteAlto` e `limiteMedio` (o drill `nivel:baixo` precisa de número; a tela mostra os limiares). Ficha `riscos` = a de Abertas (3 tipos Checklist, sem Cancelled/Canceled/Dropped).

- Ruling: Negativas (T10) — `devolvida` NÃO inclui a reabertura (de categoria DONE para outra): a reabertura de TG-131 volta na ordem do fluxo, mas o plano espera `devolvidas = 1` (só TG-132) e `reabertas = 1`; os dois conceitos não se sobrepõem. Custo se errado: tirar a guarda `else if` do calculador (a reaberta passaria a contar também como devolvida).
- Ruling: Negativas (T10) — `NegativasCalculator.calcular/analisar` ganhou os parâmetros `agora` (para "abertas vencidas", que é o retrato de hoje) e `unit` (devolvido no DTO); o plano não tinha o relógio na assinatura. Custo: nenhum.
- Ruling: Negativas (T10) — a visão é pelo responsável ATUAL (`permiteAtual`), como Fechadas e Metas; a atribuição por pessoa usa o dono no instante do evento, mas só aparece para quem vê a task hoje (ex.: a reabertura de TG-131, feita quando era da `ana`, não aparece para a `ana` depois que passou ao `bruno`; o gerente e o squad leader dos dois a veem). Custo se errado: trocar para `permitePosse` nas listas de evento.
- Ruling: Negativas (T10) — período padrão de 90 dias (como Fechadas); atraso e estouro valem pelas resolvidas NO período (não pelo universo por vencimento de "no prazo"), mas a conta é a mesma regra única (`NoPrazo.classificar`). Ficha com os mesmos 6 tipos da sincronização e sem exclusão de status.
- Ruling: Negativas (T10) — o NE-08 (taxas) abre as tasks dos NUMERADORES (segmentos `taxa:reabertura|devolucao|atraso|atrasoSemJustificativa`, as mesmas listas dos cartões de contagem) e a base `concluidas`; o NE-09 ("maior tempo" por status) continua no Plano 10, T10.

## Rulings

- Ruling: na Tarefa 9 a TG-120 é resolvida às 03h UTC (não 03h30), porque o helper de fixtures `Trb1.t` só aceita hora cheia; o efeito (23h de 15/10 em Manaus) é o mesmo. Os testes de consistência e de compatibilidade com o legado passaram na primeira execução (conferem comportamento já implementado nas Tarefas 5 a 7); só os testes do documento falharam antes de escrevê-lo. A "mesma semana" de Tempo em status é checada pelo drill `TS-01` com a semana como período, pois aquele card não tem série semanal.

- Ruling: o Passo 5 do plano (captura visual das telas) NÃO foi executado: o ambiente não tem navegador. Fica como pendência para a revisão visual do usuário. Custo se errado: nenhum no código; só falta a conferência visual.
- Ruling: as "bar tables" (`ContagensEmBarras`) contam como tabelas, não como gráficos, para respeitar o limite de dois gráficos por tela. `granularidade` (Metas) e `movimento` (Negligenciadas) ficam fora do componente `Filtros` genérico e têm controle próprio na tela, indo à URL. Nenhuma tela mostra Epic. Os testes de tela foram escritos junto da implementação (vermelho-antes só verificado por mutação).

- Ruling: `CardDeLeitura.calcular` devolve `ResultadoDeCard(dto, detalhar)` e NÃO tem `kinds`/`detalhar(e, kind)` separados — o drill é a rota genérica `GET /api/cartoes/{id}/tasks?segmento=` do 17a; `CartaoDeLeitura` (um `CartaoRegistrado`) chama o MESMO cálculo para `valores` e `drill`, o que garante número == total do drill. A rota `/api/drill/{id}` do plano NÃO existe. Custo se errado: criar `/api/drill/*` sobre `ResultadoDeCard.detalhar`.
- Ruling: `FiltroInvalidoException` e `AcessoNegadoException` do plano viraram `ErroHttp(400)` e `ErroHttp(403)` (o mecanismo que `executar` já traduz em status sem corpo) — um tipo de erro só no pacote `cards`. Custo se errado: trocar o `throw` por exceções próprias.
- Ruling: `TempoEmStatusRoutes` NÃO foi refatorado para usar `FiltrosDeCard`/`escopoDe` (o plano manda, "sem mudar respostas") — a leitura dele tem parâmetros próprios (`tipo` único, `semOutliers`, período de 90 dias com `de = ate - 90`) e dezenas de testes de contrato; trocar é risco sem ganho para o 5a. Os cards novos usam o kit novo. Custo se errado: migrar depois, com `TempoEmStatusRoutesTest` como rede.
- Ruling: `KpiCard` e `DrillDrawer` do plano NÃO foram criados — o 17a já tem `Cartao` e `GavetaDeTasks` (spec 17, seção 6). O kit novo é `CardPage` (moldura, estados 403/400/erro e avisos), `Filtros` (URL) e `useCard`. Os testes `kpiMostraSimboloETexto`, `kpiClicavelAbreGaveta` e `escFechaAGavetaEDevolveFoco` do plano já existem em `Cartao.test.tsx` e `GavetaDeTasks.test.tsx`.
- Ruling: período padrão com `periodoPadraoDias = N` vale `de = ate - (N - 1)`, ambos inclusivos (N dias de Manaus, como a Auditoria); `maxDias` também é inclusivo. Custo se errado: ajustar uma linha em `FiltrosDeCard.ler`.
- Ruling: `WorkStore` ganhou `pessoas(instance)` (responsáveis e donos anteriores) porque o escopo precisa das pessoas que existem ANTES de carregar as issues do card. Custo: um método.
- Ruling: cartões de Abertas — o inventário tem AB-01 a AB-09; o AB-03 (P85 histórica) divide o cartão com o AB-02 (as duas contam as mesmas concluídas por tipo, segmento `historico:<tipo>`). Os demais têm um cartão cada. Custo se errado: registrar o AB-03 como cópia do AB-02.
- Ruling: AB-04 "acima do P85" conta SÓ issues em andamento (inventário, "em andamento com idade > P85"); a TG-102 (Open, 45 dias) fica fora mesmo sendo mais velha que o P85 do tipo. Tipo sem histórico não entra (aviso `SEM_HISTORICO_DO_TIPO`, com `n`).
- Ruling: o histórico de 16 semanas (P50/P85 e medianas por status) segue o ESCOPO de quem consulta (visibilidade, squad e pessoa), mas não os filtros de tipo e prioridade (os percentis já são por tipo). Custo se errado: aplicar `filtros.aceita` também ao histórico.
- Ruling: NG-04 "bloqueadas" inclui a flag de impedimento (`flagged`) além da categoria BLOCKED (o plano diz "categoria BLOCKED"); sem a flag sincronizada (campo "a confirmar") o resultado é o do plano. Custo se errado: tirar `|| it.flagged`.
- Ruling: Negligenciadas leva as linhas das seis listas na resposta (até 100 cada; a contagem e a gaveta trazem todas), com `ultimoMovimento` (campo, dia de Manaus, dias corridos) e `semMovimentoReal`. O filtro "sem movimento real" é o parâmetro `movimento=semReal` (outro valor é 400); vale para as seis listas, para as contagens e para o drill (cartões `NG-01 a 06`). O último movimento real é a última mudança de status, responsável, vencimento, labels ou resolução no changelog (criação se nunca mudou), não o `updated`. NG-01 continua pelo `updated`.
- Ruling: o DTO de Negligenciadas ganhou `listas`, `diasSemAtualizacao`, `semMovimentoReal` e `unit` além dos seis contadores do plano (a tela precisa das linhas e do limiar).
- Ruling: `porSemana` de Fechadas vai da semana do `de` à do `ate`, cortando a primeira só se ela for PARCIAL (começa antes do `de`) e vazia; assim o TRB-1 dá `[10-05, 10-12, 10-19, 10-26]` como o plano e uma entrega nos primeiros dias do período (antes da primeira segunda-feira) nunca some da série. Custo se errado: remover o `drop(1)`.
- Ruling: Fechadas ganhou `comPontos` (tasks por trás de "Pontos entregues"), `linhas` (tabela FE-12, até 500) e o cartão FE-06 junta `canceladas` e `semResolution` (as duas são "fechada sem ser entrega"); o plano não tinha onde abrir `semResolution`. `semResolution` vem de `comEventoEntre(status)` no período, valendo com `resolved` nulo ou não.
- Ruling: as fichas `fechadas` e `metas` leem os MESMOS 6 tipos da ficha de sincronização (Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check, Task), e não só os 3 Checklist de `abertas`/`negligenciadas`/`tempo-em-status`: a Auditoria (Plano 4) lê tudo o que a sincronização grava, e o `RegraUnicaNoPrazoTest` do plano usa a TG-152 do tipo `Task`; com 3 tipos as três telas divergiriam em produção assim que houvesse um Feature check ou Task concluído. Custo se errado: reduzir `issuetype` nas duas fichas (e o teste de snapshot do JQL) e, então, passar a mesma lista de tipos à Auditoria.
- Ruling: `NoPrazo.noUniverso` NÃO propaga status fora do mapa (devolve falso, como já fazia a Auditoria, que o relata à parte); Metas e Fechadas validam o status das candidatas antes e viram o aviso bloqueante `STATUS_SEM_MAPEAMENTO`. Custo se errado: tirar a validação e ficar com universo parcial silencioso.
- Ruling: gavetas de Metas — o segmento do "ajustado" é `noPrazoAjustado` (o plano chama `ajustado`), igual ao de Fechadas e ao do cartão AU-03. A série `porPeriodo` agrupa pelo VENCIMENTO (o mesmo critério do universo, então a série cabe no período) e só traz períodos com tasks. `semVencimento` exclui Epic como o universo.
- Ruling: `metas.yaml` entrou no `AppContext.load` (`regrasDeMetas`, último parâmetro com padrão para não quebrar os construtores posicionais dos testes); em produção o arquivo real é obrigatório e o `.exemplo` só em dev, como `auditoria.yaml`. `AppContextTest` ganhou `metas` nas listas de arquivos; `app/README.md` cita o arquivo. Pendência de implantação: criar `metas.yaml` a partir do modelo.
- Ruling: no T6 as candidatas de "no prazo" são as resolvidas no período; a regra única por vencimento (candidatas lidas por `duedate`) entra na Tarefa 7 como o plano manda.
- Ruling: `EntradaDeCard` ganhou `parametros` (URL bruta) para o card ler parâmetros próprios (`movimento`, depois `granularidade`) e validar com 400.
- Ruling: `testContextComBanco` aceita `statusMap` e `calendar` próprios (para o TRB-1, que tem mapa e feriados próprios); o padrão continua nos `.exemplo`.

- Ruling: o `CardDeLeitura`/`rotaDeCard` do plano é adaptado à interface `CartaoRegistrado` do 17a (spec 17, seção 6) — o plano 5 foi escrito antes do contrato do cartão — custo se errado: reescrever os adaptadores de `cards/trabalho/`.
