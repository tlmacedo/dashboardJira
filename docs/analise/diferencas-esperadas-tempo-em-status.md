# Tempo em status: diferenças esperadas em relação ao app atual

Compara o card Tempo em status do app Python (`legacy/backend/jiradash/analytics.py::time_in_status`, na tag `legacy-final`, apoiado em `metrics/core.py::status_durations` e `pct`) com o novo `TimeInStatusCalculator` (Kotlin). Diferenças listadas aqui são intencionais; qualquer outra divergência é defeito. Todas as cifras usam o cenário TG-1 (usuários fictícios `ana` e `bruno`), coberto por `TimeInStatusCalculatorTest` e `CompatLegadoTest`.

## Cenário TG-1

Criada em 05/10 (12:00 UTC). A Fazer até 06/10; Em andamento de 06/10 a 09/10; Em revisão de 09/10 a 12/10 (feriado em 12/10 no calendário de teste); Concluída em 12/10 com resolution "Done". Responsável: `ana` até 08/10, `bruno` depois.

## O que é igual (conferido no código do legado)

- Tempo corrido por status, somando reentradas, da criação até a resolução (`status_durations`).
- Status da categoria "done" ficam fora das somas (`cat_of.get(s) != "done"`).
- Eficiência de fluxo = soma dos status "active" ÷ soma de todos os status não concluídos (o legado divide por `total or 1`; o novo devolve `null` sem dados).
- Percentil por interpolação linear (`numpy.percentile`); mediana e P85.
- Parada além do normal: permanência corrente > max(2 d, 1,5 × mediana do status).

Fórmula conferida em `CompatLegadoTest.modoCorridoReproduzAFormulaDoAppAtual` (modo `CORRIDO`): A Fazer 1,0 d, Em andamento 3,0 d, Em revisão 3,0 d, eficiência 3/7 = 0,428571. Nenhuma divergência em relação ao enunciado da tarefa (ativo = categoria active; Concluído ignorado; unidade em dias). Única diferença de apresentação: o legado devolve a eficiência em percentual com uma casa (`eficiencia_fluxo_pct` = 42,9); o novo devolve a fração (0,428571).

## Tabela de diferenças esperadas

| Assunto | App atual | Novo | Efeito | Exemplo TG-1 |
|---|---|---|---|---|
| Fuso | Datas em UTC (`resolved.date()`) | America/Manaus (UTC-4) | Tasks resolvidas nas primeiras horas do dia em UTC caem no dia anterior em Manaus; muda o período do filtro e a contagem de dias úteis | 12:00 UTC = 08:00 em Manaus, mesmo dia; o corte de dia de uma transição às 02:00 UTC cairia no dia anterior |
| Unidade | Sempre dias corridos | Dias úteis por padrão, com opção de corridos | Fins de semana e feriados deixam de contar; medianas diminuem | Em revisão 09/10 a 12/10: 3,0 corridos × 0,666667 úteis; A Fazer 1,0 e Em andamento 3,0 nas duas unidades |
| Eficiência de fluxo | 3/7 (42,9 %), calculada em corridos | 0,642857 em úteis (3,0 ÷ 4,666667); 3/7 só em `CORRIDO` | Em úteis o tempo parado no fim de semana sai do denominador e a eficiência sobe | 0,428571 corridos × 0,642857 úteis |
| Concluída | Categoria "done" do status (e filtro `done_ok` por resolução negativa) | Categoria done **e** `resolution` preenchida **e** data de resolução; divergência vai para Qualidade dos dados | Task em "Concluído" sem resolution deixa de entrar nas médias | TG-1 entra (done + "Done"); a mesma task com `resolution = null` fica fora de `analyzed` |
| Posse por responsável | Todo o tempo da task vai para o responsável atual (`assignee_id` final) | Tempo repartido por responsável vigente em cada trecho | O mapa pessoa × status passa a refletir quem de fato segurou a task | Em andamento (3 d): `ana` 2,0 e `bruno` 1,0 (legado: `bruno` 3,0) |
| Mediana | `numpy.percentile` (interpolação linear) | Interpolação linear implementada em `Percentiles` | Sem diferença esperada; documentado para evitar regressão | Duas tasks com 1 d e 3 d: mediana 2,0 em ambos |
| Mapa de status | Status sem categoria viram `None` e seguem nos cálculos | Mapa obrigatório: status sem mapeamento gera `UnmappedStatusException` | Falha explícita em vez de número silenciosamente errado; o mapa precisa ser completo | Status "Revisando" fora do mapa interrompe o cálculo (teste `statusSemMapaPropagaExcecao`) |
| Status DONE | Ignorado nas somas (`!= "done"`) | Ignorado nas somas (igual) | Sem diferença; registrado porque o enunciado original pedia conferir | "Concluído" não aparece em `perStatus` do TG-1 |
| Filtro OS Version | Não existe | Filtro por OS Version nas concluídas e abertas | Escopo menor que o do legado quando aplicado; sem filtro, mesmo escopo | Sem filtro: TG-1 analisada; com OS Version diferente da task, `analyzed` vazio |
| Visibilidade por papel | Todos veem tudo | Usuário vê o próprio, Squad Leader o seu squad, coordenador e gerente todos | Os números do card dependem de quem consulta; a visão completa só para quem tem papel | `bruno` sem papel de liderança vê só as próprias tasks (dev mode: todas) |

## Como validar

- `./gradlew test --tests '*CompatLegadoTest*'` (equivalência em `CORRIDO`).
- `./gradlew test --tests '*TimeInStatusCalculatorTest*'` (valores em úteis e demais regras).
