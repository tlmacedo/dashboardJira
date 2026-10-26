# Índice dos planos do Painel de Squads

Spec: `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`. Cada plano gera software testável sozinho. Os planos 1 a 3 (com o 3A) estão executados e mesclados; os planos 4A e 4 estão detalhados e aguardam aprovação; os planos 5 a 8 são resumos e **são detalhados ao iniciar a fase**, porque dependem das interfaces entregues antes (detalhar agora seria adivinhar nomes).

| Fase | Plano | Estado |
|---|---|---|
| 1 | `2026-10-24-plano-01-fundacao.md` | Executado (PR #6) |
| 2 | `2026-10-24-plano-02-dicionario-e-jql.md` | Executado (PR #7) |
| 3A | `2026-10-25-plano-03a-inicializacao.md` | Executado (PR #8) |
| 3 | `2026-10-24-plano-03-tempo-em-status.md` | Executado (PR #9) |
| 4A | `2026-10-25-plano-04a-sincronizacao.md` | Escrito, aguardando aprovação |
| 4 | `2026-10-25-plano-04-auditoria.md` | Escrito, aguardando aprovação |
| 5 | Cards de trabalho e prazos | A detalhar |
| 6 | Pessoas, planejamento e ausências | A detalhar |
| 7 | Checklist × Review, Integridade, Distribuição, Governança | A detalhar |
| 8 | Preparação final, acessibilidade e remoção do `legacy/` | A detalhar |

## Fase 4A — Sincronização periódica (depende das Fases 1 a 3)
Detalhada em `2026-10-25-plano-04a-sincronizacao.md` (9 tarefas): modo de sincronização desligado por padrão e autorização por execução, cursor e registro de execuções, ficha de sincronização e JQL com hora, `Syncer` em fluxo com tradução de chaves `JIRAUSER*`, carga inicial e incremental com retomada, reconciliação diária com remoção lógica, agendador único no ciclo do Ktor e retenção agendada (`issue` e `access_log`), status para o gerente e runbook da 1ª sincronização. Independente da Fase 4 (próximo número de migração livre na execução).

## Fase 4 — Auditoria (depende das Fases 1 a 3; da 4A só pelos dados reais)
Detalhada em `2026-10-25-plano-04-auditoria.md` (9 tarefas): labels de atraso e regras em YAML, linha do tempo de auditoria e autorização pela vigência na data, sinais de vencimento, responsável, passa-bola, cancelamento e labels, Task × Review, "no prazo" e "no prazo ajustado", leitura no PostgreSQL, API restrita a coordenação e gerente com drill e gaveta, telas Auditoria e Divergência, e documento de diferenças. Estimativa e worklog ficam fora (sem dado); Integridade fica na Fase 7.

## Fase 5 — Cards de trabalho e prazos
Abertas, Metas e prazos (com "no prazo" e "no prazo ajustado" por labels em YAML), Fechadas, Negativas (eventos pela data do changelog), Riscos, Previsão (Monte Carlo com taxa de chegada), percentis por tipo de issue. Cada card: ficha YAML, JQL gerado, teste de aceite com exemplo numérico e detalhamento com o mesmo tamanho do card. Base: IDs do `docs/analise/relatorio-cards-app-atual.md`.

## Fase 6 — Pessoas, planejamento e ausências
Carga, Capacidade ("sem capacidade", atraso acumulado, horas por tipo), Calendário do time, Cobertura por ausência, Plano de 4 a 8 semanas, Vencimentos da semana com folga real, Posse da task, Esforço por task fechada (tipo + labels `TEST-*` + worklog + índice composto em YAML), Simulador de ausência, Matriz de competências, Revisões pendentes por revisor, Carga por tipo/OS, Tendência de ocupação. Ausências e feriados vêm de YAML.

## Fase 7 — Checklist × Review, Integridade, Distribuição e Rebalanceamento, Governança
Portar as telas restantes do app atual com as decisões da spec; Integridade só para gerente e sempre auditada; distribuição em modo sombra.

## Fase 8 — Fechamento
Documentos finais (matriz de compatibilidade e checklist da 1ª conexão atualizados), revisão de acessibilidade (AA nos pares de cor, teclado, celular), revisão visual dos três temas, e remoção do `legacy/` quando a paridade for confirmada pelo usuário.
