# Progresso — plano: docs/superpowers/plans/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md

Branch: `feat/contrato-do-cartao` (bloco 17a = Tarefas 1 a 7). Executor: Claude Code (subagente implementador), sem o usuário presente; pontos de parada resolvidos pela opção Recomendada e registrados como `Ruling:`. O 17b (Tarefas 8 a 13) fica para depois do 14b (ponto de parada PP5).

## Ponto de partida (main em 4a8fde5)

- Backend `./gradlew test --rerun-tasks`: 861 testes, 0 falhas. Frontend `pnpm vitest run`: 74 testes, 0 falhas.

## Checklist (17a)

- [x] Tarefa 1: registro de cartões e catálogo por papel (testes: backend 869 totais, 0 falhas; +8 em `CatalogoDeCartoesTest`)
- [ ] Tarefa 2: drill genérico e teste de contrato
- [ ] Tarefa 3: componente `Cartao` e ajuda (ⓘ)
- [ ] Tarefa 4: gaveta de tasks compartilhada e "Abrir no Jira"
- [ ] Tarefa 5: migrar Tempo em status e Sincronização para o contrato
- [ ] Tarefa 6: tela Mapa de cartões
- [ ] Tarefa 7: revisão visual, acessibilidade e fechamento do 17a

## Estado herdado (conferido no código)

| Nome do plano | Nome real | Observação |
|---|---|---|
| `TempoEmStatusRoutes.kt`, `TempoEmStatusDto.kt`, rota de drill com `kind` | iguais, em `cards/` | Rota antiga: `GET /api/drill/tempo-em-status?kind=...` |
| `TempoEmStatus.tsx` (função local `Kpi`) | função local chama-se `KpiBotao` (dentro de `Kpis`) | |
| `DrillDrawer.tsx`, `csv.ts`, `format.ts`, `api.ts` | iguais, em `screens/TempoEmStatus/` | |
| `Sincronizacao.tsx` | igual | A tela não tem "KPIs" propriamente: faixa de modo, blocos por instância e retenção |
| `nav.ts`, `Shell`, `Menu`, `RoleContext`, `i18n/*.json`, `LangProvider` | iguais | |
| `AccessLog`, `executarAuditado`, `Visibility`, `Papel` | `AccessLog` e `Visibility` iguais; `executarAuditado` chama-se `executar` (privada em `TempoEmStatusRoutes.kt`); `Papel` chama-se `Role` | |
| `FonteDoDado`, `EntradaDeCartao`, `LinhaDeDrill` | não existiam: criados nesta fase | `LinhaDeDrill` é alias de `DrillRow` |

## Rulings

- Ruling: `papeis: Set<Papel>` do plano vira `Set<Role>` (nome real do enum) — o código já usa `Role` em toda parte — custo se errado: nenhum (renomear).
- Ruling: `FonteDoDado` tem 6 valores (ISSUE, CHANGELOG, ISSUE_LINK, WORKLOG, SINCRONIZACAO, CONFIGURACAO) e `LinhaDeDrill` é `typealias` de `DrillRow` — o plano cita os dois tipos sem defini-los; reaproveitar `DrillRow` evita duas formas de linha — custo se errado: trocar o alias por classe própria.
- Ruling: `EntradaDeCartao(ctx, store, principal, parametros)` leva os parâmetros de URL (ktor `Parameters`) — valores e drill usam o mesmo `lerFiltros`, o que garante "número = drill" com os mesmos filtros — custo se errado: nenhum.
- Ruling: o catálogo registra TS-01 a TS-07 e também SY-01 a SY-03 (Sincronização, só GERENTE, `temDrill=false`) já na Tarefa 1 — a spec (seção 8) exige "todos os cartões atuais" e a Tarefa 5 precisa de metadados para migrar a Sincronização — custo se errado: remover 3 cartões e 18 chaves de texto.
- Ruling: TS-05 ("Onde o tempo é gasto") registrado à parte de TS-04, com os mesmos segmentos (a tela mostra os dois na mesma seção) — o inventário lista os dois — custo se errado: remover um registro.
- Ruling: TS-02 (eficiência, um percentual) abre as tasks analisadas (numerador e denominador), conforme a regra 5 do contrato — antes não era clicável.
- Ruling: `executarAuditado` do plano é `executar` (privada) em `TempoEmStatusRoutes.kt`; passou a `internal`, aceita bloco `suspend` e responde `ErroDto` quando o `ErroHttp` leva código — necessário para o 400 com código `SEGMENTO_INVALIDO` e para a rota do catálogo reutilizar a mesma auditoria — custo se errado: nenhum.
- Ruling: o papel que filtra o catálogo vem de `papeisVigentes` (diretório de hoje; exceção só do `dev`), a mesma regra de `/api/me`, e não do `principal.roles` bruto.

## Minor adiados

(nenhum até agora)
