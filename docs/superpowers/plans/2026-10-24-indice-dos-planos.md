# Índice dos planos do Painel de Squads

Spec: `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`. Cada plano gera software testável sozinho. Os planos 1 a 3 estão detalhados; os planos 4 a 8 são resumos e **são detalhados ao iniciar a fase**, porque dependem das interfaces entregues antes (detalhar agora seria adivinhar nomes).

| Fase | Plano | Estado |
|---|---|---|
| 1 | `2026-10-24-plano-01-fundacao.md` | Escrito, aguardando revisão do usuário |
| 2 | `2026-10-24-plano-02-dicionario-e-jql.md` | Escrito, aguardando revisão do usuário |
| 3 | `2026-10-24-plano-03-tempo-em-status.md` | Escrito, aguardando revisão do usuário |
| 4 | Auditoria | A detalhar |
| 5 | Cards de trabalho e prazos | A detalhar |
| 6 | Pessoas, planejamento e ausências | A detalhar |
| 7 | Checklist × Review, Integridade, Distribuição, Governança | A detalhar |
| 8 | Preparação final, acessibilidade e remoção do `legacy/` | A detalhar |

## Fase 4 — Auditoria (depende das Fases 1 a 3)
**Objetivo:** detectar os sinais da seção 7 da spec e mostrar a gaveta de histórico.
**Tarefas previstas:** (1) modelo de `AuditSignal` e catálogo de sinais com gravidade e limites em YAML versionado; (2) detectores de data (removido, alterado por não autorizado, Task × Review diferentes) usando `RoleDirectory.isReporterAuthorized` na data da alteração; (3) detectores de dono (passa-bola, troca perto do vencimento); (4) detectores de label (pós-fechamento, pós-vencimento, `LATE_SENT`/`LATE_REVIEW` com posse ≥ 2 dias antes do vencimento — limite configurável); (5) cancelamento suspeito, estimativa reduzida e worklog retroativo; (6) API `/api/auditoria` com filtro por papel (coordenador e gerente) e registro de consulta; (7) card Task × Review (% de divergência e lista clicável); (8) telas Auditoria e gaveta do histórico campo a campo.
**Casos de limite obrigatórios:** 2 dias exatos, fuso de Manaus, autorizado × não autorizado, vigência de papel na data da alteração.

## Fase 5 — Cards de trabalho e prazos
Abertas, Metas e prazos (com "no prazo" e "no prazo ajustado" por labels em YAML), Fechadas, Negativas (eventos pela data do changelog), Riscos, Previsão (Monte Carlo com taxa de chegada), percentis por tipo de issue. Cada card: ficha YAML, JQL gerado, teste de aceite com exemplo numérico e detalhamento com o mesmo tamanho do card. Base: IDs do `docs/analise/relatorio-cards-app-atual.md`.

## Fase 6 — Pessoas, planejamento e ausências
Carga, Capacidade ("sem capacidade", atraso acumulado, horas por tipo), Calendário do time, Cobertura por ausência, Plano de 4 a 8 semanas, Vencimentos da semana com folga real, Posse da task, Esforço por task fechada (tipo + labels `TEST-*` + worklog + índice composto em YAML), Simulador de ausência, Matriz de competências, Revisões pendentes por revisor, Carga por tipo/OS, Tendência de ocupação. Ausências e feriados vêm de YAML.

## Fase 7 — Checklist × Review, Integridade, Distribuição e Rebalanceamento, Governança
Portar as telas restantes do app atual com as decisões da spec; Integridade só para gerente e sempre auditada; distribuição em modo sombra.

## Fase 8 — Fechamento
Documentos finais (matriz de compatibilidade e checklist da 1ª conexão atualizados), revisão de acessibilidade (AA nos pares de cor, teclado, celular), revisão visual dos três temas, e remoção do `legacy/` quando a paridade for confirmada pelo usuário.
