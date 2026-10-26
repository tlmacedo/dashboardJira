# Índice dos planos do Painel de Squads

Spec mestra: `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`. Cada plano gera software testável sozinho. Os planos 1 a 3 (com o 3A) estão executados e mesclados; os planos 4A a 8 estão detalhados e aguardam execução, com as decisões em aberto resolvidas em 25/10 (usuário ou valor padrão recomendado, reversível). Como dependem das interfaces das fases anteriores, cada plano abre com uma seção "Estado herdado (confirmar na execução)" em que o executor confere os nomes na `main` antes da Tarefa 1.

## Como começar (ClineSR ou outra IA)

1. **Leia primeiro `docs/guia-execucao-outra-ia.md`**, inteiro. Ele dá a ordem de leitura (`.clinerules/00-regras-do-projeto.md`, `CLAUDE.md`, `PRODUCT.md`, `HANDOFF.md`, spec mestra), o fluxo por tarefa (TDD, autorrevisão, um commit por tarefa), o arquivo de progresso em `docs/superpowers/progresso/` e quando parar e perguntar.
2. Para a fase da vez, leia **a spec da fase** (coluna "Spec da fase" abaixo) e depois **o plano**. A spec diz o porquê, as regras que se aplicam, as decisões já tomadas, os critérios de aceitação e o glossário do que a fase consome; o plano diz o como, tarefa a tarefa.
3. **Precedência em caso de conflito:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > spec da fase > plano > spec mestra de 24/10 (que ainda traz a regra antiga de acesso ao Jira, substituída em 25/10).
4. **Acesso ao Jira:** a única trava é a flag `JIRA_LIVE_ENABLED` (`false` = proibido para todos; `true` = liberado). Só o usuário muda a flag. Testes automáticos nunca acessam o Jira real.

**Ordem de execução** (cada item é um PR a partir da `main` atualizada; nada é mesclado sem o usuário mandar; o próximo só começa depois que o anterior estiver na `main`):

4A Sincronização → 4 Auditoria → 5a Cards de trabalho → 5b Negativas, Riscos, Previsão e Épicos → 6a Capacidade e planejamento → 6b Worklog, posse, esforço e cobertura → 7a Checklist × Review e Integridade → 7b Distribuição, Governança e início → 8 Preparação final (a remoção do `legacy/` só depois do "sim" do usuário ao documento de equivalência; sem ele, vira PR próprio depois).

4A e 4 são independentes entre si (a 4 depende da 4A só pelos dados reais); a ordem acima é a do guia e permite que a Tarefa 6 do Plano 4 já acrescente `removida_em IS NULL` quando a 4A estiver na `main`.

| Fase | Plano | Spec da fase | Estado |
|---|---|---|---|
| 1 | `2026-10-24-plano-01-fundacao.md` | — (spec mestra) | Executado (PR #6) |
| 2 | `2026-10-24-plano-02-dicionario-e-jql.md` | — (spec mestra) | Executado (PR #7) |
| 3A | `2026-10-25-plano-03a-inicializacao.md` | — (spec mestra) | Executado (PR #8) |
| 3 | `2026-10-24-plano-03-tempo-em-status.md` | — (spec mestra) | Executado (PR #9) |
| 4A (11 tarefas) | `2026-10-25-plano-04a-sincronizacao.md` | `../specs/2026-10-25-spec-fase-04a-sincronizacao.md` | Escrito, aguardando execução |
| 4 | `2026-10-25-plano-04-auditoria.md` | `../specs/2026-10-25-spec-fase-04-auditoria.md` | Escrito, aguardando execução |
| 5 | `2026-10-25-plano-05-cards-trabalho-e-prazos.md` | `../specs/2026-10-25-spec-fase-05-cards-trabalho-e-prazos.md` | Escrito, aguardando execução |
| 6 | `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` | `../specs/2026-10-25-spec-fase-06-pessoas-planejamento-ausencias.md` | Escrito, aguardando execução |
| 7 | `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` | `../specs/2026-10-25-spec-fase-07-checklist-integridade-distribuicao-governanca.md` | Escrito, aguardando execução |
| 8 | `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` | `../specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md` | Escrito, aguardando execução |

## Fase 4A — Sincronização periódica (depende das Fases 1 a 3)
Detalhada em `2026-10-25-plano-04a-sincronizacao.md` (9 tarefas): sincronização periódica com a flag `JIRA_LIVE_ENABLED` como única trava (sem autorização por execução), cursor e registro de execuções, ficha de sincronização e JQL com hora, `Syncer` em fluxo com tradução de chaves `JIRAUSER*`, carga inicial e incremental com retomada, reconciliação diária com remoção lógica, agendador único no ciclo do Ktor e retenção agendada (`issue` e `access_log`), status para o gerente e runbook da 1ª sincronização. Independente da Fase 4 (próximo número de migração livre na execução).

## Fase 4 — Auditoria (depende das Fases 1 a 3; da 4A só pelos dados reais)
Detalhada em `2026-10-25-plano-04-auditoria.md` (9 tarefas): labels de atraso e regras em YAML, linha do tempo de auditoria e autorização pela vigência na data, sinais de vencimento, responsável, passa-bola, cancelamento e labels, Task × Review, "no prazo" e "no prazo ajustado", leitura no PostgreSQL, API restrita a coordenação e gerente com drill e gaveta, telas Auditoria e Divergência, e documento de diferenças. Estimativa e worklog ficam fora (sem dado); Integridade fica na Fase 7.

## Fase 5 — Cards de trabalho e prazos (depende das Fases 3, 4A e 4)
Detalhada em `2026-10-25-plano-05-cards-trabalho-e-prazos.md` (14 tarefas, em dois blocos que são PRs separados). **5a (9 tarefas):** campos de trabalho (prioridade, estimativas, pontos, épico, flag), ordem do fluxo e resoluções negativas; modelo de leitura `IssueSnapshot`/`WorkStore`; métricas transversais; filtros, escopo visível e rota genérica de card; Abertas, Negligenciadas ("Sem atualização" e "Paradas" separadas), Fechadas e Metas e prazos (no prazo, ajustado e pela data original); telas; consistência entre telas e diferenças. **5b (5 tarefas):** Negativas (eventos pela data do changelog), Riscos (pesos em YAML), Previsão (Monte Carlo com taxa de chegada) e Épicos (só com amostra própria), telas e diferenças.

## Fase 6 — Pessoas, planejamento e ausências (depende da Fase 5)
Detalhada em `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` (16 tarefas, em dois blocos). **6a (9 tarefas):** equipes, ausências (sem motivo) e regras de planejamento em YAML somente leitura; horas por issue e capacidade; demanda, ocupação com "sem capacidade", atraso acumulado e Jain; agenda pela capacidade (ligada a Riscos e Previsão); Carga, Capacidade e ausências, Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana, Gargalos e Tendência; telas e diferenças. **6b (7 tarefas):** sincronização do worklog sem comentário; Posse da task; Esforço por task fechada (índice composto em YAML); Cobertura por ausência e Simulador; Competências, Revisões pendentes e Carga por tipo/OS; telas; diferenças e garantia de somente leitura.

## Fase 7 — Checklist × Review, Integridade, Distribuição, Governança e telas de início (depende das Fases 4 a 6)
Detalhada em `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` (18 tarefas, em dois blocos). **7a (8 tarefas):** pares e alertas do Checklist × Review, estatísticas e lift com amostra mínima, card e tela; os 12 sinais da Integridade, nível por pessoa e impacto nas metas, API só do gerente e sempre auditada; diferenças. **7b (10 tarefas):** distribuição e rebalanceamento em modo sombra, decisões gravadas só no painel (sem escrita no Jira), telas; Qualidade dos dados (com done × resolution), registro de acesso e configuração em leitura; Central de atenção, Visão do squad e Meu painel; diferenças.

## Fase 8 — Preparação final, acessibilidade e remoção do `legacy/` (depende das Fases 4A a 7)
Detalhada em `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` (10 tarefas): inventário de rotas com papel e registro; SSO/OIDC genérico testado com emissor falso; equivalência por card com o app atual; WCAG AA (contraste dos três temas, teclado, rótulos); revisão visual; desempenho na escala da spec; contêiner de produção e guia de implantação; checklist da 1ª conexão e matriz finais; remoção do `legacy/` (tag `legacy-final`, só com aprovação do usuário); guia do painel e documentação final.
