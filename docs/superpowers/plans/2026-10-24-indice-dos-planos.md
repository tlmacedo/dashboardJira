# Índice dos planos do Painel de Squads

Spec mestra: `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`. Cada plano gera software testável sozinho. Os planos 1 a 3 (com o 3A) estão executados e mesclados; os planos 4A a 15 estão detalhados e aguardam execução, com as decisões em aberto resolvidas em 25/10 (usuário ou valor padrão recomendado, reversível). Como dependem das interfaces das fases anteriores, cada plano abre com uma seção "Estado herdado (confirmar na execução)" em que o executor confere os nomes na `main` antes da Tarefa 1.

## Como começar (ClineSR ou outra IA)

1. **Leia primeiro `docs/guia-execucao-outra-ia.md`**, inteiro. Ele dá a ordem de leitura (`.clinerules/00-regras-do-projeto.md`, `CLAUDE.md`, `PRODUCT.md`, `HANDOFF.md`, spec mestra), o fluxo por tarefa (TDD, autorrevisão, um commit por tarefa), o arquivo de progresso em `docs/superpowers/progresso/` e quando parar e perguntar.
2. Para a fase da vez, leia **a spec da fase** (coluna "Spec da fase" abaixo) e depois **o plano**. A spec diz o porquê, as regras que se aplicam, as decisões já tomadas, os critérios de aceitação e o glossário do que a fase consome; o plano diz o como, tarefa a tarefa.
3. **Precedência em caso de conflito:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > spec da fase > plano > spec mestra de 24/10 (que ainda traz a regra antiga de acesso ao Jira, substituída em 25/10).
4. **Acesso ao Jira:** a única trava é a flag `JIRA_LIVE_ENABLED` (`false` = proibido para todos; `true` = liberado). Só o usuário muda a flag. Testes automáticos nunca acessam o Jira real.

**Ordem de execução** (cada item é um PR a partir da `main` atualizada; nada é mesclado sem o usuário mandar; o próximo só começa depois que o anterior estiver na `main`):

4A Sincronização → 9 CI e endurecimento → 4 Auditoria → 5a Cards de trabalho → 5b Negativas, Riscos, Previsão e Épicos → 6a Capacidade e planejamento → 6b Worklog, posse, esforço e cobertura → 7a Checklist × Review e Integridade → 7b Distribuição, Governança e início → 10 Complementos de telas e filtros → 11 Auditoria completa e LGPD → 8 Preparação final (a remoção do `legacy/` só depois do "sim" do usuário ao documento de equivalência; sem ele, vira PR próprio depois) → 12 Operação em produção → 13 Implantação real → 14 Primeira conexão, validação e calibração → 15 Identidade final, inglês e entrega.

Por quê: a 14 já pode começar em parte (bloco 14a, sincronização) logo depois do 4A, quando o usuário ligar a flag, em PR próprio e sem migração; a validação dos cards (bloco 14b) vem depois do 7, e a posição 14 da lista é a do fechamento.

**O Plano 4 depende do 4A (executar depois do 4A):** usa `JqlValues.issueKey` (4A, T3), `AvisoDto.params` (4A, T8), `issue.removida_em` (4A, T2 e T6), os dados de demonstração (4A, T10) e o teste `cadaPassoDoChecklistConfirmaAFlag` (4A, T11); sem o 4A na `main`, o Plano 4 não compila.

**Nota (25/10):** 4A tem 12 tarefas; Review ligada à task pelo link "Blocks / is blocked by" (4A, Tarefa 12, executada por último; o Plano 4 roda depois dela), não pelo `parent`; Plano 16 = escrita no Jira (futuro).

| Fase | Plano | Spec da fase | Estado |
|---|---|---|---|
| 1 | `2026-10-24-plano-01-fundacao.md` | — (spec mestra) | Executado (PR #6) |
| 2 | `2026-10-24-plano-02-dicionario-e-jql.md` | — (spec mestra) | Executado (PR #7) |
| 3A | `2026-10-25-plano-03a-inicializacao.md` | — (spec mestra) | Executado (PR #8) |
| 3 | `2026-10-24-plano-03-tempo-em-status.md` | — (spec mestra) | Executado (PR #9) |
| 4A (12 tarefas) | `2026-10-25-plano-04a-sincronizacao.md` | `../specs/2026-10-25-spec-fase-04a-sincronizacao.md` | Escrito, aguardando execução |
| 4 | `2026-10-25-plano-04-auditoria.md` | `../specs/2026-10-25-spec-fase-04-auditoria.md` | Escrito, aguardando execução |
| 5 | `2026-10-25-plano-05-cards-trabalho-e-prazos.md` | `../specs/2026-10-25-spec-fase-05-cards-trabalho-e-prazos.md` | Escrito, aguardando execução |
| 6 | `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` | `../specs/2026-10-25-spec-fase-06-pessoas-planejamento-ausencias.md` | Escrito, aguardando execução |
| 7 | `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` | `../specs/2026-10-25-spec-fase-07-checklist-integridade-distribuicao-governanca.md` | Escrito, aguardando execução |
| 8 | `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` | `../specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md` | Escrito, aguardando execução |
| 9 (11 tarefas) | `2026-10-25-plano-09-ci-e-endurecimento.md` | `../specs/2026-10-25-spec-fase-09-ci-e-endurecimento.md` | Escrito, aguardando execução |
| 10 (12 tarefas) | `2026-10-25-plano-10-complementos-de-telas-e-filtros.md` | `../specs/2026-10-25-spec-fase-10-complementos-de-telas-e-filtros.md` | Escrito, aguardando execução |
| 11 (12 tarefas) | `2026-10-25-plano-11-auditoria-completa-e-lgpd.md` | `../specs/2026-10-25-spec-fase-11-auditoria-completa-e-lgpd.md` | Escrito, aguardando execução |
| 12 (12 tarefas) | `2026-10-25-plano-12-operacao-em-producao.md` | `../specs/2026-10-25-spec-fase-12-operacao-em-producao.md` | Escrito, aguardando execução |
| 13 (13 tarefas) | `2026-10-25-plano-13-implantacao-real.md` | `../specs/2026-10-25-spec-fase-13-implantacao-real.md` | Escrito, aguardando execução |
| 14 (14 tarefas: 14a e 14b) | `2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md` | `../specs/2026-10-25-spec-fase-14-primeira-conexao-validacao-e-calibracao.md` | Escrito, aguardando execução |
| 15 (11 tarefas) | `2026-10-25-plano-15-identidade-final-ingles-e-entrega.md` | `../specs/2026-10-25-spec-fase-15-identidade-final-ingles-e-entrega.md` | Escrito, aguardando execução |

## Fase 4A — Sincronização periódica (depende das Fases 1 a 3)
Detalhada em `2026-10-25-plano-04a-sincronizacao.md` (12 tarefas): sincronização periódica com a flag `JIRA_LIVE_ENABLED` como única trava (sem autorização por execução), cursor e registro de execuções, ficha de sincronização e JQL com hora, `Syncer` em fluxo com tradução de chaves `JIRAUSER*`, carga inicial e incremental com retomada, reconciliação diária com remoção lógica, agendador único no ciclo do Ktor e retenção agendada (`issue` e `access_log`), status para o gerente e runbook da 1ª sincronização, dados de demonstração e hook alinhado à flag, e vínculos entre issues ("Blocks / is blocked by", tabela `issue_link`, Tarefa 12) que definem o par Task × Review. A Fase 4 depende desta (próximo número de migração livre na execução).

## Fase 4 — Auditoria (depende das Fases 1 a 3 e da 4A: executar depois do 4A)
Detalhada em `2026-10-25-plano-04-auditoria.md` (9 tarefas): labels de atraso e regras em YAML, linha do tempo de auditoria e autorização pela vigência na data, sinais de vencimento, responsável, passa-bola, cancelamento e labels, Task × Review, "no prazo" e "no prazo ajustado", leitura no PostgreSQL, API restrita a coordenação e gerente com drill e gaveta, telas Auditoria e Divergência, e documento de diferenças. Estimativa e worklog ficam fora (sem dado); Integridade fica na Fase 7.

## Fase 5 — Cards de trabalho e prazos (depende das Fases 3, 4A e 4)
Detalhada em `2026-10-25-plano-05-cards-trabalho-e-prazos.md` (14 tarefas, em dois blocos que são PRs separados). **5a (9 tarefas):** campos de trabalho (prioridade, estimativas, pontos, épico, flag), ordem do fluxo e resoluções negativas; modelo de leitura `IssueSnapshot`/`WorkStore`; métricas transversais; filtros, escopo visível e rota genérica de card; Abertas, Negligenciadas ("Sem atualização" e "Paradas" separadas), Fechadas e Metas e prazos (no prazo, ajustado e pela data original); telas; consistência entre telas e diferenças. **5b (5 tarefas):** Negativas (eventos pela data do changelog), Riscos (pesos em YAML), Previsão (Monte Carlo com taxa de chegada) e Épicos (só com amostra própria), telas e diferenças.

## Fase 6 — Pessoas, planejamento e ausências (depende da Fase 5)
Detalhada em `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` (16 tarefas, em dois blocos). **6a (9 tarefas):** equipes, ausências (sem motivo) e regras de planejamento em YAML somente leitura; horas por issue e capacidade; demanda, ocupação com "sem capacidade", atraso acumulado e Jain; agenda pela capacidade (ligada a Riscos e Previsão); Carga, Capacidade e ausências, Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana, Gargalos e Tendência; telas e diferenças. **6b (7 tarefas):** sincronização do worklog sem comentário; Posse da task; Esforço por task fechada (índice composto em YAML); Cobertura por ausência e Simulador; Competências, Revisões pendentes e Carga por tipo/OS; telas; diferenças e garantia de somente leitura.

## Fase 7 — Checklist × Review, Integridade, Distribuição, Governança e telas de início (depende das Fases 4 a 6)
Detalhada em `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` (18 tarefas, em dois blocos). **7a (8 tarefas):** pares e alertas do Checklist × Review, estatísticas e lift com amostra mínima, card e tela; os 12 sinais da Integridade, nível por pessoa e impacto nas metas, API só do gerente e sempre auditada; diferenças. **7b (10 tarefas):** distribuição e rebalanceamento em modo sombra, decisões gravadas só no painel (sem escrita no Jira), telas; Qualidade dos dados (com done × resolution), registro de acesso e configuração em leitura; Central de atenção, Visão do squad e Meu painel; diferenças.

## Fase 8 — Preparação final, acessibilidade e remoção do `legacy/` (depende das Fases 4A a 7)
Detalhada em `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` (10 tarefas): inventário de rotas com papel e registro; SSO/OIDC genérico testado com emissor falso; equivalência por card com o app atual; WCAG AA (contraste dos três temas, teclado, rótulos); revisão visual; desempenho na escala da spec; contêiner de produção e guia de implantação; checklist da 1ª conexão e matriz finais; remoção do `legacy/` (tag `legacy-final`, só com aprovação do usuário); guia do painel e documentação final.

## Fase 9 — CI e endurecimento (depende da 4A; antes do 4)
Detalhada em `2026-10-25-plano-09-ci-e-endurecimento.md`: workflow de CI com backend em Testcontainers, frontend, `test-guard.sh` e varredura de dados pessoais; CSP e cabeçalhos de segurança; CSRF; sessão expirada na tela; imagem com modo dev desligado por padrão, digests e `.dockerignore` por lista de permissão; Minor técnicos adiados.

## Fase 10 — Complementos de telas e filtros (depende das Fases 5 a 7)
Detalhada em `2026-10-25-plano-10-complementos-de-telas-e-filtros.md`: filtros pelo dicionário além de OS, gaveta universal da task, paginação virtual, estados de tela e mensagens do backend por código.

## Fase 11 — Auditoria completa e LGPD (depende das Fases 4, 6 e 7)
Detalhada em `2026-10-25-plano-11-auditoria-completa-e-lgpd.md` (12 tarefas): sinais de estimativa reduzida e worklog retroativo, trava de Auditoria e Integridade até o aceite do DPO, inventário de dados tratados e material para o DPO.

## Fase 12 — Operação em produção (depende das Fases 8 e 9)
Detalhada em `2026-10-25-plano-12-operacao-em-producao.md` (12 tarefas): logs estruturados, métricas, readiness e alertas; validador de configuração; backup agendado com teste de restauração; runbook diário e de incidentes; rotação de segredos.

## Fase 13 — Implantação real (depende das Fases 8, 9, 11 e 12)
Detalhada em `2026-10-25-plano-13-implantacao-real.md` (13 tarefas): decisões com a infraestrutura (provedor OIDC, VM ou k8s, registro, cofre, TLS: pontos de parada); username pelo perfil do provedor; proxy confiável; guardas de produção; versão e rótulos OCI; publicação por tag com digest; manifestos da VM **ou** do k8s; segredos; atualização e reversão ensaiadas; homologação e fumaça; produção com a flag do Jira desligada.

## Fase 14 — Primeira conexão, validação e calibração (14a depois da 4A; 14b depois da 7)
Detalhada em `2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md` (14 tarefas, dois PRs). **14a (6 tarefas):** o usuário liga a flag (a IA pergunta e espera); roteiro; casador de campos e conferência de status sobre respostas salvas fora do repositório; registro de divergências só com agregados; checklist passo a passo; primeiro ciclo de sincronização; correção com teste de cada regra refutada. **14b (8 tarefas):** recarga completa sem rede; roteiro de validação por ficha; validação dos cards Samsung; cards da Sidia (CAT, ponto de parada); calibração por retroteste e aplicação com aprovação; aceite do gestor card a card; fechamento.

## Fase 15 — Identidade final, inglês e entrega (depende das Fases 8, 9 e 14)
Detalhada em `2026-10-25-plano-15-identidade-final-ingles-e-entrega.md` (11 tarefas): ativos de marca (ponto de parada, com caminho alternativo); logo saneado; fonte com alternativa de sistema; azul `#1329A0` único, moldura do tema Sidia e tema sem piscada; `DESIGN.md` verificado contra os tokens; guia e FAQ em inglês; treinamento por papel; sessões registradas; dono da manutenção e checklist de entrega; fechamento.

## Migrações
O 4A ocupa V4, V5 e V6. Os demais planos usam `V{n}` e **confirmam a próxima numeração livre na execução** (nunca editar migração mesclada); a tabela é a sequência esperada, na ordem de execução acima.

| Migração | Plano | Observação |
|---|---|---|
| V4 | 4A, Tarefa 2 | `V4__sincronizacao.sql` |
| V5 | 4A, correção da Tarefa 2 | `V5__marcador_de_falhas.sql` |
| V6 | 4A, Tarefa 12 | `V6__vinculos_entre_issues.sql` |
| V7 | 5a | campos de trabalho |
| V8 | 6b | worklog por instância |
| V9 | 7b | decisão de distribuição |
| V10 | 10 | campos do dicionário |
| V11 | 11 | condicional (ponto de parada da Tarefa 3, ou remoção de `person`) |
| V12 | 8 | condicional (índices de leitura, só se a Tarefa 6 pedir) |
| V12 ou V13 | 12 | execução de backup: V12 se a do 8 não existir, V13 se existir |
| próximo livre | 16 | escrita no Jira (futuro) |

Sem migração: 9, 4, 13, 14 e 15. Se a V11 não for criada, o Plano 8 usa V11; blocos em branches paralelas renumeram a migração do PR que mesclar depois, antes do merge.
