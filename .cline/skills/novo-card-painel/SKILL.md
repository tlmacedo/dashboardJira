---
name: novo-card-painel
description: Use ao criar ou alterar um card (métrica) do Painel de Squads, seja a API Kotlin do card, a ficha YAML em app/config/cards ou a tela React. Mostra o padrão de referência (card Tempo em status, Plano 3) e o que cada camada precisa ter (JQL pela ficha, visibilidade por papel, access_log, avisos, drill, CSV seguro, i18n e temas).
---

# Novo card do Painel de Squads

Skill própria do projeto. O card **Tempo em status** (Plano 3, `docs/superpowers/plans/2026-10-24-plano-03-tempo-em-status.md`) é o exemplo completo de uma fatia: copie o padrão dele em vez de inventar outro.

## Arquivos de referência

| Camada | Referência |
|---|---|
| Ficha do card (tipos, status, filtros) | `app/config/cards/tempo-em-status.card.yaml` |
| Consulta | `app/backend/src/main/kotlin/br/sidia/painel/jql/CardQueryFactory.kt` (JQL só daqui ou do `JqlBuilder`) |
| Domínio e cálculo | `app/backend/src/main/kotlin/br/sidia/painel/domain/` |
| Rota e DTO | `app/backend/src/main/kotlin/br/sidia/painel/cards/TempoEmStatusRoutes.kt`, `TempoEmStatusDto.kt` |
| Teste da rota | `app/backend/src/test/kotlin/br/sidia/painel/cards/TempoEmStatusRoutesTest.kt` |
| Tela | `app/frontend/src/screens/TempoEmStatus/` (`TempoEmStatus.tsx`, `api.ts`, `format.ts`, teste) |
| Contrato do cartão (Plano 17a) | backend: `cards/contrato/` (`CartaoMeta`, `CartaoRegistrado`, `CatalogoDeCartoes`, drill genérico `GET /api/cartoes/{id}/tasks`) e `cards/TempoEmStatusCartoes.kt` como exemplo de registro; frontend: `shared/Cartao.tsx`, `shared/GavetaDeTasks.tsx`, `shared/csv.ts`, `shared/useCatalogo.ts` |

**Todo cartão novo se registra no `CatalogoDeCartoes` (`catalogoPadrao()`), usa o componente `Cartao` e abre a `GavetaDeTasks`; o número do cartão tem de ser o total do drill (`ContratoDeCartoesTest` percorre o registro) e os textos "título", "o que mostra" e "como calcula" entram em `pt.json` e `en.json`. Não crie `KpiCard`, gaveta nem rota de drill própria.**

## O que todo card precisa

1. **Spec da fase primeiro:** a regra de cálculo vem da spec da fase (`docs/superpowers/specs/2026-10-25-spec-fase-NN-*.md`) e do inventário `docs/analise/relatorio-cards-app-atual.md`. Divergência com o app atual vira registro no documento de diferenças, não silêncio.
2. **Consulta:** ficha YAML do card + `CardQueryFactory`. Nenhum texto JQL montado à mão, guardado em banco ou em log.
3. **Visibilidade:** a rota aplica `Visibility` do papel; parâmetros (`pessoa=`, `squad=`, `kind=`) nunca ampliam a visão; erro nunca vaza chave ou nome fora da visão. Teste com USUARIO, SQUAD_LEADER, COORDENADOR e GERENTE.
4. **Auditoria de consulta:** a rota grava `access_log`, inclusive quando nega; falha ao gravar = 503 sem dados.
5. **Regras de negócio:** concluída = categoria done **e** `resolution`; fuso America/Manaus; dias úteis por padrão (corridos opcionais); relógio e calendário injetados; "no prazo" e "no prazo ajustado" lado a lado quando houver prazo.
6. **Dado ruim não derruba o card:** issue inconsistente vira aviso com código e gravidade.
7. **Tela:** filtros na URL, KPIs clicáveis que abrem a gaveta (drill), foco e `Esc` na gaveta, CSV com neutralização de fórmula, pt-BR e inglês completos, três temas com WCAG AA, símbolos (▲ ● ✓ ⏸) junto da cor, pessoas em ordem alfabética.
8. **Testes:** TDD em cada camada; o backend usa SQLite real em arquivo temporário (`testContextComBanco`) e `MockEngine` para o Jira.

Execute a implementação pelo workflow `/executar-plano.md` e revise com `/revisar-tarefa.md`.
