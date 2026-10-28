# Trabalho e prazos (Abertas, Negligenciadas, Fechadas e Metas): diferenças esperadas em relação ao app atual

Blocos 5a (cartões `AB`, `NG`, `FE` e `MT`) e 5b (`NE`, `RI`, `PV` e `EP`, na última parte deste documento) do Plano 5. Compara os cartões do app Python (`legacy/`, inventário em `docs/analise/relatorio-cards-app-atual.md`) com as telas novas. Diferença listada aqui é intencional; qualquer outra divergência é defeito. Exemplos usam o cenário fictício TRB-1 (pessoas `ana`, `bruno` e `carla`; relógio de sexta 30/10/2026; feriados 12/10 e 02/11). Nada foi conferido no Jira real: a primeira conexão segue `docs/jira/checklist-primeira-conexao.md`.

Legenda: **igual** (mesma conta do legado, conferida no código), **diferença esperada** (com o motivo) e **novo** (o app atual não tinha).

## Diferenças que valem para vários cartões

| Tema | App atual | Novo | Efeito (TRB-1) |
|---|---|---|---|
| Fuso | Datas em UTC | America/Manaus para todo "dia" (resolução, vencimento, semana) | A TG-120, resolvida às 03h de 16/10 UTC (23h de 15/10 em Manaus) e com vencimento em 15/10, está no prazo; pelo dia em UTC seria atrasada. Prova: `ConsistenciaEntreTelasTest` |
| Dias úteis × corridos | Tempo sempre corrido | Dias úteis por padrão (sem sábado, domingo e feriados), com a opção "dias corridos" (`unit=corrido`) | Ciclo P50 do TRB-1: 6,5 em dias corridos (igual ao legado, `CompatLegadoTrabalhoTest`) e menor em dias úteis |
| Concluída | Categoria "done" do status | Categoria `done` **e** `resolution`; "done" sem `resolution` vai para Qualidade dos dados (aviso `DONE_SEM_RESOLUTION`) e sai dos números | A TG-116 (Closed sem `resolution`) não entra em Fechadas nem em Metas |
| Cancelada | Contava como concluída | `resolution` negativa (`resolucoesNegativas`, por exemplo "Won't Do") ou status de cancelamento: fica fora de Fechadas e do universo de Metas, aparece só em Canceladas | A TG-115 (Won't Do) conta como cancelada, não como entrega |
| Epic | Misturado às demais | Fora do universo de "no prazo" e das telas de trabalho | A TG-151 (Epic) não muda nenhum número |
| Atraso justificado | Ausente: todo atraso era atraso | "No prazo" e "no prazo ajustado" lado a lado; o ajustado conta também a task atrasada com label que justifica (`LATE_SENT`, `LATE_REVIEW`, em `labels_atraso.yaml`) | A TG-113 (4 dias de atraso com `LATE_SENT`) fica só no ajustado: 4 no prazo, 5 no ajustado |
| Regra única de "no prazo" | Cada tela contava do seu jeito | Uma só regra (`NoPrazo.noUniverso`, candidatas lidas pelo vencimento) para Auditoria, Fechadas e Metas | As três telas dão (5, 4, 5): universo, no prazo, no prazo ajustado |
| Escopo de quem vê | Todos veem tudo | Usuário vê o próprio; Squad Leader, o seu squad; coordenador e gerente, todos. Fila sem dono só para coordenação e gerente. Agregados e tabelas só com o visível | O histórico de 16 semanas (P50/P85) segue o escopo de quem consulta |
| Horas por pessoa | Estimado × registrado do worklog | Estimativa e tempo gasto do campo da issue; **sem worklog até a Fase 6** (traço, nunca zero) | FE-11 e FE-12 mostram "—" quando falta o dado |
| Pessoas | Podiam ser ordenadas por desempenho | Tabelas de pessoas em ordem alfabética, sem posição nem ranking | FE-11 e MT-14 |
| Acesso | Sem registro | Toda consulta (200, 400, 403) vai para o `access_log`, sem chave de issue na rota | Igual à Auditoria |

## Abertas (AB)

| ID | Veredito | Detalhe |
|---|---|---|
| AB-01 | igual | Abertas = fora da categoria `done`; o Epic não entra (diferença do quadro acima). |
| AB-02 | diferença esperada | P50 histórica do ciclo das concluídas em 16 semanas, **por tipo** (o legado usava uma faixa só). Cartão junto com o AB-03 (mesmas concluídas por tipo, segmento `historico:<tipo>`). Amostra com menos de 5 itens mostra o aviso `AMOSTRA_PEQUENA`. |
| AB-03 | diferença esperada | P85 histórica, por tipo, junto do AB-02. O histórico segue o escopo de quem consulta, mas não os filtros de tipo e prioridade (os percentis já são por tipo). |
| AB-04 | diferença esperada | Acima do P85 **do próprio tipo**, só em andamento (a TG-102, `Open` há 45 dias, fica fora). Tipo sem histórico não entra (aviso `SEM_HISTORICO_DO_TIPO`). |
| AB-05 | igual | Contagem por status na ordem do fluxo (`ordem` do `status_map`); sem ordem configurada, ordem alfabética com o aviso `ORDEM_DO_FLUXO_AUSENTE`. |
| AB-06 | diferença esperada | Por prioridade; enquanto nenhuma issue da base tem prioridade sincronizada, vem vazio com o aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS`. |
| AB-07 | igual | Faixas 0–7, 8–14, 15–30 e > 30 dias; idade desde a primeira entrada em status ativo, de espera ou bloqueado, ou a criação. Em dias corridos reproduz o legado (`CompatLegadoTrabalhoTest.faixasDeIdadeComoOLegado`); em dias úteis é menor. |
| AB-08 | igual | Vencidas, vence em 7 dias, depois, sem vencimento; "hoje" é o dia de Manaus. |
| AB-09 | diferença esperada | Tabela ordenável; a coluna de responsável vem vazia (traço) fora da visão de quem consulta; horas restantes só com estimativa. |

## Negligenciadas (NG)

| ID | Veredito | Detalhe |
|---|---|---|
| NG-01 | diferença esperada | **Sem atualização** (campo `updated` do Jira há mais de 7 dias) e **Paradas no status** (tempo no status) ficam em listas separadas: são recortes diferentes, a mesma task pode estar nas duas. |
| NG-02 | novo no escopo | Sem responsável: fila só para coordenação e gerente. |
| NG-03 | igual | Vencidas (vencimento anterior a hoje de Manaus). |
| NG-04 | diferença esperada | Bloqueadas incluem a flag de impedimento (`flagged`) além da categoria `BLOCKED`. |
| NG-05 | diferença esperada | Paradas no status: limiar `max(3 d, 2 × mediana do status em 16 semanas)`, em dias úteis por padrão. |
| NG-06 | igual | A fazer há mais de 30 dias. |

Novo nas seis listas: a coluna **último movimento** (último evento real do changelog: status, responsável, vencimento, labels ou resolução; a criação se nunca mudou) e o filtro "sem movimento real" (`movimento=semReal`). O `updated` do Jira muda por comentário e por sincronização e não substitui o NG-01.

## Fechadas (FE)

| ID | Veredito | Detalhe |
|---|---|---|
| FE-01 | diferença esperada | Concluídas = categoria `done` + `resolution` positiva, resolvidas no período (dia de Manaus). |
| FE-02 | diferença esperada | Pontos entregues somam só as concluídas com pontos sincronizados; sem nenhuma, traço (não zero). |
| FE-03 | diferença esperada | Ciclo P50/P85 por primeira entrada em ativo/espera/bloqueado até a resolução, em dias úteis por padrão. |
| FE-04 | igual | Lead time (criação até a resolução), mesmos percentis. |
| FE-05 | diferença esperada | No prazo e no prazo ajustado lado a lado; universo pela regra única, lido pelo vencimento. |
| FE-06 | diferença esperada | Canceladas, mais as fechadas sem `resolution` (o mesmo "fechada sem ser entrega"). |
| FE-07 | diferença esperada | Entregas por semana de segunda a domingo, semana de Manaus; a semana parcial vazia do começo é cortada. |
| FE-08 | igual | Dispersão do ciclo com P50, P85 e P95. |
| FE-09 | igual | Por tipo. |
| FE-10 | igual | Por projeto. |
| FE-11 | diferença esperada | Finalizou × participou usa o responsável **no instante** de cada evento; sem worklog (horas só do campo da issue); ordem alfabética. |
| FE-12 | diferença esperada | Tabela de concluídas com a situação do prazo em símbolo e texto (✓ no prazo, ● ajustado); até 500 linhas. |

## Metas (MT)

| ID | Veredito | Detalhe |
|---|---|---|
| MT-01 | diferença esperada | No prazo contra a meta (`metas.yaml`, obrigatório em produção), pela regra única. Universo: vencimento no período, sem canceladas nem Epic. |
| MT-02 | igual | No prazo pela data original (1º vencimento do changelog); o alerta de datas remarcadas só aparece quando o backend manda. |
| MT-03 | igual | Antecipadas (Δ ≤ −1), meta própria. |
| MT-04 | igual | Em D-1 ou D-2. |
| MT-05 | igual | No dia (D). |
| MT-06 | igual | Atrasadas. |
| MT-07 | igual | Sem vencimento (o Epic não entra). |
| MT-08 | diferença esperada | Entregas por período **pelo vencimento** (o mesmo critério do universo, então a série cabe no período); só períodos com tasks. |
| MT-09 | diferença esperada | Percentual no prazo por período contra a meta; o ajustado vem ao lado. |
| MT-10 | igual | Distribuição pelas faixas D-3 ou antes, D-2, D-1, D, atraso 1–3 dias e > 3 dias, em dias de calendário (`CompatLegadoTrabalhoTest.metasDeltaComoOLegado`). |
| MT-11 | novo na forma | Tabela por tipo, com pílula de símbolo e texto (✓ na meta, ● perto, ▲ abaixo). |
| MT-12 | novo na forma | Tabela por prioridade. |
| MT-13 | novo na forma | Tabela por squad. |
| MT-14 | diferença esperada | Tabela por pessoa, em ordem alfabética, sem posição. |

# Bloco 5b: Negativas, Riscos, Previsão e Épicos

Compara os cartões `NE`, `RI`, `PV` e `EP` do app Python com as telas novas. Exemplos usam os cenários fictícios NEG-1 (Negativas, período 01/10 a 30/10) e TRB-1 (demais). As diferenças gerais do bloco 5a (fuso de Manaus, dias úteis, concluída por categoria e `resolution`, cancelada, atraso justificado, escopo de quem vê, pessoas em ordem alfabética, acesso auditado) valem aqui também.

## Diferenças que valem para vários cartões do 5b

| Tema | App atual | Novo | Efeito (cenário) |
|---|---|---|---|
| Eventos pela data do changelog | Reabertas, devolvidas e trocas filtravam o período pela data da **última atualização** da task (achado 3 do relatório), e perdiam eventos antigos já atualizados depois | O período vale para a data do **evento** no changelog (a transição de status ou a troca de responsável) | A TG-133 foi reaberta em 20/09 e atualizada em 25/10: não conta em outubro (`NegativasCalculatorTest.reabertaForaDoPeriodoNaoConta`) e conta em setembro |
| Taxa de chegada | Monte Carlo assumia que nenhum item novo entra (achado 6) | Por semana, também se sorteia a chegada (tasks criadas); `comChegada: false` em `previsao.yaml` reproduz o app anterior | Chegada igual ou maior que a vazão nunca converge: "mais de 104 semanas" em vez de um número inventado |
| Amostra própria | A previsão do épico era participação do épico × vazão média do time (achado 7, aproximação grosseira) | Só a vazão do próprio épico, e só com um mínimo de entregas dele no histórico (`minItensEpico`, padrão 5); sem amostra própria é traço | Épico com 4 entregas: "—"; com 8 (1 por semana) e 3 abertas: 3 semanas |
| Pesos e regras em YAML | Pesos do risco e parâmetros da previsão no código | `risco.yaml` e `previsao.yaml` (modelos `.exemplo`, versão em toda resposta, obrigatórios em produção) | `versaoRegras` na resposta |
| Séries semanais | Cada tela contava as semanas do seu jeito | Uma função só, `SerieSemanal` (entradas por criação, saídas por conclusão positiva, canceladas à parte; semana de segunda a domingo de Manaus), compartilhada pela Previsão e pelo futuro "Fluxo da semana" | Teste de regressão do resultado do Monte Carlo |
| Estado em símbolo e texto | Cor | `▲` acima de 20% nas taxas, `▲ ●` nos níveis de risco, `✓ ● ■` na confiança | Sempre com o texto ao lado |

## Negativas (NE)

| ID | Veredito | Detalhe |
|---|---|---|
| NE-01 | diferença esperada | Reabertas: transição de status de categoria `done` para outra, pela **data do evento**. Cada task conta uma vez. |
| NE-02 | diferença esperada | Devolvidas: transição para um status de posição menor na `ordem` do fluxo (`status_map.yaml`), pela data do evento. **A reabertura não conta como devolvida** (no NEG-1 a TG-131 volta de `Closed` para `In Progress` e é só "reaberta"). Sem `ordem` configurada: 0 com o aviso `ORDEM_DO_FLUXO_AUSENTE`. |
| NE-03 | igual | Canceladas (`resolution` de cancelamento), pela data de resolução em Manaus. |
| NE-04 | diferença esperada | Concluídas com atraso e, ao lado, as **sem justificativa** (sem label de `labels_atraso.yaml`); a conta de atraso é a regra única de "no prazo" (`NoPrazo.classificar`). Lê as resolvidas no período (não o universo por vencimento de Metas). |
| NE-05 | igual | Abertas vencidas: vencimento anterior a hoje de Manaus. |
| NE-06 | diferença esperada | Estouro de estimativa: gasto acima de 125% da estimativa original, só com estimativa maior que zero (campos da própria issue; sem worklog até a Fase 6), **só nas concluídas do período**. O legado contava também tasks abertas com gasto acima de 125%. |
| NE-07 | diferença esperada | Muitas trocas: 3 ou mais mudanças de responsável **dentro do período**, pela data de cada troca no changelog (o legado olhava a task inteira pela data da última atualização, achado 3). **A primeira atribuição e a desatribuição também contam como troca** (mudança de/para "sem responsável"); o legado só contava trocas com origem e destino não nulos e diferentes. O mesmo vale para o fator `MUITAS_TROCAS` do risco (RI-02). |
| NE-08 | diferença esperada | Taxas de reabertura, devolução, atraso e atraso sem justificativa sobre as concluídas do período; acima de 20% a tela marca `▲` com texto. O cartão abre as tasks do numerador (segmentos `taxa:*`). |
| NE-09 | adiado | "Maior tempo" por status nas listas: continua no Plano 10, Tarefa 10 (`maiorTempoPorStatus`). Ainda não existe na tela. |
| NE-10 | diferença esperada | Por responsável, em ordem alfabética: cada evento vai para quem era o responsável **no instante** do evento (a reabertura da TG-131 é da `ana`, mesmo a task tendo passado ao `bruno` depois). Só pessoas da sua visão; a task só aparece para quem a vê hoje. |

## Riscos (RI)

| ID | Veredito | Detalhe |
|---|---|---|
| RI-01 | igual | Risco alto (60 pontos ou mais) e médio (30 ou mais), com os pesos do legado conferidos no código (`CompatLegadoTrabalhoTest.riscoComPesosDoLegado`); total limitado a 100. No TRB-1: TG-101 = 85 e TG-103 = 63 (alto), TG-102 = 30 (médio). |
| RI-02 | diferença esperada | A lista mostra só alto e médio, com o fator principal e a gaveta de pontos por fator; o **nível baixo** fica no drill `nivel:baixo` para conferência (o legado ocultava score < 30 sem como conferir; achado 13). A idade é comparada com o P50/P85 **do próprio tipo**, em dias úteis, e só pontua com histórico do tipo. Parada, sem atualização e bloqueada usam as regras de Negligenciadas: a parada tem piso de 3 dias úteis e vale sem mediana de status (o legado usava 2 dias e exigia a mediana); "sem atualização" vale só para tasks em andamento (o legado contava toda aberta). O fator "sem estimativa" não pontua enquanto a base não tem campos de trabalho sincronizados (aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS`). |
| RI-03 | diferença esperada | Sinais por pessoa em **proporção** (tasks em risco sobre abertas da pessoa), em ordem alfabética e só com a sua visão; o legado contava fatores de issues em risco, sem proporção ao volume (achado 13). |

## Previsão (PV)

| ID | Veredito | Detalhe |
|---|---|---|
| PV-01 | igual | Backlog: abertas fora da categoria `done`, só as que o papel vê. |
| PV-02 | diferença esperada | Termina em P50, P85 e P95 (semanas e data), pelo Monte Carlo **com taxa de chegada**; datas = segunda-feira da k-ésima semana inteira depois da semana em curso; mais que o limite (104 semanas) é "mais de 104 semanas". Percentis como o `np.percentile` do legado (interpolação linear); a diferença é a chegada. O cartão abre o backlog (o numerador). |
| PV-03 | diferença esperada | Confiança pelas **semanas com dado** (baixa abaixo de 8, média até 16, alta com 16), e não só pelo tamanho da amostra; vem com símbolo e texto. O cartão abre as entregas da janela. |
| PV-04 | diferença esperada | Vazão e chegada por semana fechada (segunda a domingo de Manaus); cada semana abre as suas tasks. A semana em curso não entra. |
| PV-05 | igual | Quantos itens em 2, 4 e 8 semanas, com percentis conservadores (P85 = 15º percentil, P95 = 5º). Projeção sem tasks por trás: sem gaveta. |
| PV-06 | pendente da Fase 6 | Previsão por task: o que falta pelo histórico de ciclos do mesmo tipo maiores que a idade (mínimo de 5, como o legado), só para tasks em andamento. As colunas **pela capacidade** e **Atrasa?** chegam na Fase 6 (Agenda, `PrevisaoDeTermino`). |

## Épicos (EP)

| ID | Veredito | Detalhe |
|---|---|---|
| EP-01 | diferença esperada | Itens sem os tipos de review **e sem as canceladas**; progresso = concluídos ÷ itens; a task aberta mais antiga, vencidos e pontos. A previsão (P85) usa só a **amostra própria** do épico e, sem ela, é traço com legenda. Respeita a visão (squad e pessoa); tipo e prioridade são ignorados, como no legado. Sem o campo de épico (`Epic Link`, "a confirmar" na 1ª conexão) a tabela vem vazia com o aviso `EPICO_INDISPONIVEL`. Os segmentos do drill usam um código derivado da chave do épico, para a chave não ir para o `access_log`. |

## Como foi conferido

- `ConsistenciaEntreTelasTest`: o mesmo "no prazo" em Auditoria, Fechadas e Metas (inclusive com cancelada e Epic no cenário), a TG-120 no dia 15/10 de Manaus, e a mesma semana em Fechadas e Tempo em status.
- `CompatLegadoTrabalhoTest`: ciclo corrido P50 de 6,5, faixas de idade e faixas de entrega contra o vencimento, conforme `legacy/backend/jiradash/metrics/core.py`, `analytics.py` e `reports.py`; no 5b, o Monte Carlo sem chegada (20 itens a 5 por semana = 4 semanas), o restante dada a idade (mínimo de 5) e os pesos e limiares do risco, lidos de `forecast.py` e `risk.py` (o teste é pulado se `legacy/` deixar de existir na Fase 8).
- Limite: a captura visual das telas não foi feita neste ambiente (sem navegador); fica para a revisão do usuário. Nada foi conferido no Jira real.
