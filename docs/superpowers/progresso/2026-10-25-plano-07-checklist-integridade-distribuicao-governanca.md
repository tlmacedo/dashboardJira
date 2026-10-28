# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md

Bloco **7a** (Tarefas 1 a 8: Checklist × Review e Integridade) na branch `feat/checklist-review-integridade`, a partir da `main` `6c83d61` (Plano 6b mesclado, PR #39). O bloco 7b (Tarefas 9 a 18) NÃO está neste arquivo ainda. Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. Roda EM PARALELO com o Plano 8 (OIDC, desempenho, pacote): arquivos compartilhados tocados o mínimo possível (ver "Arquivos compartilhados" no fim).

Ponto de partida (main `6c83d61`): backend `./gradlew test --rerun-tasks` 1856 testes, 0 falhas, 1 ignorado.

## Estado por tarefa (7a)

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 1 Pares Checklist × Review e alertas por par | Concluída | ver `git log` | `checklist/{RegrasChecklistReview,ParesChecklistReview}.kt`, `checklist_review.exemplo.yaml`, `AppContext.regrasChecklistReview` |
| 2 Estatísticas, aprendizados e labels do Checklist × Review | Concluída | ver `git log` | `checklist/EstatisticasChecklistReview.kt` (resumo, lift, faixas, diferenças de labels, atrasos em cadeia, aprendizados) |

## Estado herdado (conferido no código)

| Nome do plano | Nome real | Observação |
|---|---|---|
| `IssueSnapshot`, `WorkStore`, `toAuditIssue`, `Fluxo` | iguais, em `trabalho/` | |
| `CardDeLeitura`, `rotaDeCard`, `FiltrosDeCard`, `escopoDe` | iguais, em `cards/`; a gaveta e o drill são o contrato do 17a (`CartaoDeLeitura`, `GET /api/cartoes/{id}/tasks`) | `/api/drill/*` do plano NÃO existe |
| `KpiCard`, `DrillDrawer` | `Cartao` e `GavetaDeTasks` (`shared/`) | |
| `TaskReview.pares(issues, vinculos)`, `ParesLidos`, `AuditStore.paresTaskReview` | iguais, em `auditoria/` | |
| `ParDeChaves`, `ParesTaskReview.resolver`, `VinculosConfig`, `IssueLinkStore.ativos` | iguais, em `sync/` | |
| `executarAuditado`, `AccessLog`, `AvisoDto`, `DrillRow`, `ORDEM_NATURAL_DE_CHAVES` | iguais | `executarAuditado` está em `cards/RotaAuditada.kt` |
| `MetasCalculator.calcular(...)` do plano | `MetasCalculator`/`MetasDto` em `cards/metas/` | conferido na Tarefa 6 |
| `ParesLidos.ambiguas` / `semVinculos` → avisos `VINCULO_AMBIGUO` e `SEM_VINCULOS` | `AvisoDto.VINCULO_AMBIGUO` e `AvisoDto.VINCULOS_INDISPONIVEIS` | o aviso do plano `SEM_VINCULOS` já existe com o nome `VINCULOS_INDISPONIVEIS` |

## Rulings (7a)

- Ruling: `ParesChecklistReview.semReview(issues, vinculos)` — o plano escreve `semReview(issues)`, mas sem os vínculos resolvidos não há como saber quem tem review; o segundo parâmetro é obrigatório. Custo se errado: nenhum (renomear).
- Ruling: `tiposReview` do construtor de `ParesChecklistReview` tem efeito real: o lado review do par precisa ter tipo nessa lista (a da auditoria, fonte única), sem diferenciar maiúsculas; assim `tiposReviewDaAuditoria` prova que a lista manda. `horasAtivasReview` é sempre calculado (0,0 quando a review nunca esteve em status ativo); o tipo `Double?` do plano fica reservado para "sem histórico".
- Ruling: `RegrasChecklistReview` tem `PADRAO` igual ao modelo (conferido por teste) e `AppContext.regrasChecklistReview` tem esse padrão, para os contextos de teste não mudarem; em produção o `checklist_review.yaml` real é obrigatório, em dev cai no `.exemplo` (como `esforco.yaml`). A leitura é estrita (`lerEstrito`: o erro cita o arquivo e nunca o valor nem o nome de chave lido). Custo se errado: tornar o arquivo opcional em produção.
- Ruling: `AppContextTest` ganhou `checklist_review` nas listas de arquivos reais/modelos e um caso de YAML inválido sem vazar o valor lido.
- Ruling: T2 — `atrasosEmCadeia` (CR-12 sem nomes) = pares em que a review foi resolvida (dia de Manaus) DEPOIS do vencimento do pai. O texto do plano ("a review atrasada adiou o fechamento do pai") não bate com o P2 do CR-1, em que o pai fechou ANTES da review; a definição acima dá P2 = 1 (como o plano espera) e P1, P3 e P4 = 0. A review ainda aberta fica de fora (já aparece como `REVIEW_VENCIDA`). Custo se errado: trocar a condição em `EstatisticasChecklistReview.resumir`.
- Ruling: T2 — `resumir` ganhou o parâmetro opcional `unit` (padrão dias úteis, como o resto do painel); ciclo e razão usam `Fluxo.cicloDias` (do início ativo à resolução) só nas reviews concluídas positivas. Percentuais são frações (0,25), o DTO converte.
- Ruling: T2 — o par de `diferencasDeLabels` é `("pai:<label>"|"review:<label>", pares)`, no máximo 14, mais comuns primeiro; os `aprendizados` são `CODIGO|chave=valor|...` (`REVIEW_CURTA_REABRE_MAIS`, `MESMA_PESSOA_SEM_REVISAO_INDEPENDENTE`, `REVIEW_VENCE_DEPOIS_DO_PAI`, `PAI_FECHADO_ANTES_DA_REVIEW` e `LABEL_REABRE_MAIS`); só saem com amostra (faixa e label com `n >= amostraMinima`; as frases descritivas com `pares >= amostraMinima`). A label técnica `review` (rótulo do app anterior) fica fora do lift; label com lift >= 1,5 e `n` suficiente vira frase (até 3). Lift nulo quando nenhum pai foi reaberto. Rótulos de label ordenados por lift são rótulos, nunca pessoas.
