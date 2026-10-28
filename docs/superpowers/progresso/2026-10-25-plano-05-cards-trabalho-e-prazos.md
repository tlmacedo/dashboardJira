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
| 4 Filtros, escopo, rota genérica de card e kit de tela | Concluída (backend 1039, frontend 156; 0 falhas) | ver `git log` | `FiltrosDeCard`, `Escopo`, `CardDeLeitura`/`CartaoDeLeitura`/`rotaDeCard` (adaptação ao 17a); `shared/{CardPage,Filtros,useCard}` |

## Rulings

- Ruling: `CardDeLeitura.calcular` devolve `ResultadoDeCard(dto, detalhar)` e NÃO tem `kinds`/`detalhar(e, kind)` separados — o drill é a rota genérica `GET /api/cartoes/{id}/tasks?segmento=` do 17a; `CartaoDeLeitura` (um `CartaoRegistrado`) chama o MESMO cálculo para `valores` e `drill`, o que garante número == total do drill. A rota `/api/drill/{id}` do plano NÃO existe. Custo se errado: criar `/api/drill/*` sobre `ResultadoDeCard.detalhar`.
- Ruling: `FiltroInvalidoException` e `AcessoNegadoException` do plano viraram `ErroHttp(400)` e `ErroHttp(403)` (o mecanismo que `executar` já traduz em status sem corpo) — um tipo de erro só no pacote `cards`. Custo se errado: trocar o `throw` por exceções próprias.
- Ruling: `TempoEmStatusRoutes` NÃO foi refatorado para usar `FiltrosDeCard`/`escopoDe` (o plano manda, "sem mudar respostas") — a leitura dele tem parâmetros próprios (`tipo` único, `semOutliers`, período de 90 dias com `de = ate - 90`) e dezenas de testes de contrato; trocar é risco sem ganho para o 5a. Os cards novos usam o kit novo. Custo se errado: migrar depois, com `TempoEmStatusRoutesTest` como rede.
- Ruling: `KpiCard` e `DrillDrawer` do plano NÃO foram criados — o 17a já tem `Cartao` e `GavetaDeTasks` (spec 17, seção 6). O kit novo é `CardPage` (moldura, estados 403/400/erro e avisos), `Filtros` (URL) e `useCard`. Os testes `kpiMostraSimboloETexto`, `kpiClicavelAbreGaveta` e `escFechaAGavetaEDevolveFoco` do plano já existem em `Cartao.test.tsx` e `GavetaDeTasks.test.tsx`.
- Ruling: período padrão com `periodoPadraoDias = N` vale `de = ate - (N - 1)`, ambos inclusivos (N dias de Manaus, como a Auditoria); `maxDias` também é inclusivo. Custo se errado: ajustar uma linha em `FiltrosDeCard.ler`.
- Ruling: `WorkStore` ganhou `pessoas(instance)` (responsáveis e donos anteriores) porque o escopo precisa das pessoas que existem ANTES de carregar as issues do card. Custo: um método.
- Ruling: `testContextComBanco` aceita `statusMap` e `calendar` próprios (para o TRB-1, que tem mapa e feriados próprios); o padrão continua nos `.exemplo`.

- Ruling: o `CardDeLeitura`/`rotaDeCard` do plano é adaptado à interface `CartaoRegistrado` do 17a (spec 17, seção 6) — o plano 5 foi escrito antes do contrato do cartão — custo se errado: reescrever os adaptadores de `cards/trabalho/`.
