# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-05-cards-trabalho-e-prazos.md

Bloco **5a** (Tarefas 1 a 9) na branch `feat/cards-trabalho-5a`, a partir da `main` com o Plano 4 (Tarefas 4 a 9) e o 17a. O bloco 5b (Tarefas 10 a 14) NÃO está neste arquivo ainda.

Ponto de partida (main `fa8493c`): backend 986 testes, frontend 143 (números do HANDOFF), 0 falhas.

## Estado por tarefa

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 0 Minor do Plano 4: rota da negação CSRF sem chave de issue | Concluída | `b1a75dd` | `registrarNegacaoCsrf` usa `rotaParaRegistro` |
| 1 Campos de trabalho, ordem do fluxo e resoluções negativas | Concluída (backend 1000 testes, 0 falhas) | `d4882df` | Migração `V2__campos_de_trabalho.sql`; `CamposDeTrabalhoExtrator`; `StatusMap.ordem`/`ehResolucaoNegativa`; `upsert` ganha `idsDeTrabalho` |
| 2 `IssueSnapshot` e `WorkStore` | Concluída | `1ef5ea6` | `trabalho/`; cenário TRB-1 em `src/test/.../trabalho/Trb1.kt` |

## Rulings

- Ruling: o `CardDeLeitura`/`rotaDeCard` do plano é adaptado à interface `CartaoRegistrado` do 17a (spec 17, seção 6) — o plano 5 foi escrito antes do contrato do cartão — custo se errado: reescrever os adaptadores de `cards/trabalho/`.
