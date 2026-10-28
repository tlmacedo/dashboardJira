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
| 7 Metas e prazos (domínio e API) + regra única de "no prazo" | Concluída (backend 1117, 0 falhas) | ver `git log` | `NoPrazo.noUniverso`/`classificar`, `WorkStore.candidatasNoPrazo`, cartões `MT-01` a `MT-14`, `RegrasDeMetas` (`metas.yaml`, obrigatório em produção), `RegraUnicaNoPrazoTest` `(5, 4, 5)` nas três telas |
| 6 Fechadas (domínio e API) | Concluída (backend 1086, 0 falhas) | `7683e33` | Cartões `FE-01` a `FE-12`; ficha `fechadas.card.yaml` sem exclusão de status |
| 5 Abertas e Negligenciadas (domínio e API) | Concluída (backend 1066, 0 falhas) | `ca51f9f` | Cartões `AB-01/02/04 a 09` e `NG-01 a 06` no `catalogoPadrao()`; coluna e filtro de "último movimento real" (I-06) |
| 4 Filtros, escopo, rota genérica de card e kit de tela | Concluída (backend 1039, frontend 156; 0 falhas) | `c05cd6e` | `FiltrosDeCard`, `Escopo`, `CardDeLeitura`/`CartaoDeLeitura`/`rotaDeCard` (adaptação ao 17a); `shared/{CardPage,Filtros,useCard}` |

## Rulings

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
