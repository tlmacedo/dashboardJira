# Índice dos planos do Painel de Squads

Spec mestra: `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`. Cada plano gera software testável sozinho. **Estado em 27/10/2026:** executados e mesclados os planos 1, 2, 3A, 3, **3B (saída do Docker/PostgreSQL)**, **4A** e as Tarefas 1 a 3 do Plano 4; a **Fase 0 (PC da empresa)** e os planos 9, 14 a 16, 4 (Tarefas 4 a 9), 5 a 8 e 10 a 13 estão escritos e aguardam execução. **Todos os planos valem sobre SQLite embutido, sem Docker** (ver o adendo abaixo); os 8, 9, 12 e 13 foram reescritos para isso.

## Como começar (ClineSR ou outra IA)

0. **Leia antes de tudo, nesta ordem:** `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` (a plataforma mudou: SQLite, sem Docker; vence qualquer trecho antigo de plano) e `docs/superpowers/fluxo-de-entrega.md` (revisão, validação, commit, push, PR, **merge** e volta à `main`, ao fim de cada plano/bloco). Num PC novo da empresa, execute primeiro a **Fase 0**: `2026-10-27-plano-00-pc-da-empresa.md`.
1. **Leia primeiro `docs/guia-execucao-outra-ia.md`**, inteiro. Ele dá a ordem de leitura (`.clinerules/00-regras-do-projeto.md`, `CLAUDE.md`, `PRODUCT.md`, `HANDOFF.md`, spec mestra), o fluxo por tarefa (TDD, autorrevisão, um commit por tarefa), o arquivo de progresso em `docs/superpowers/progresso/` e quando parar e perguntar.
2. Para a fase da vez, leia **a spec da fase** (coluna "Spec da fase" abaixo) e depois **o plano**. A spec diz o porquê, as regras que se aplicam, as decisões já tomadas, os critérios de aceitação e o glossário do que a fase consome; o plano diz o como, tarefa a tarefa.
3. **Precedência em caso de conflito:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > spec da fase > plano > spec mestra de 24/10 (que ainda traz a regra antiga de acesso ao Jira, substituída em 25/10).
4. **Acesso ao Jira:** a única trava é a flag `JIRA_LIVE_ENABLED` (`false` = proibido para todos; `true` = liberado). Só o usuário muda a flag. Testes automáticos nunca acessam o Jira real.

> **Desde 28/10/2026 a divisão entre o que vem ANTES e DEPOIS da mudança da flag do Jira está em `2026-10-28-roteiro-antes-e-depois-da-flag.md`**: o Claude Code implementa tudo que não precisa do Jira real (inclusive o 17b, antes do 14b, por ordem do usuário) e o ClineSR executa o 14a/14b e o que depende de decisões e efeitos fora do repositório. Ele vence a ordem abaixo onde houver diferença.

**Ordem de execução** (cada item é um PR a partir da `main` atualizada; a IA mescla ao fim de cada plano/bloco pelo `docs/superpowers/fluxo-de-entrega.md` (7 condições; o Plano 16 só com ordem expressa do usuário); o próximo só começa depois que o anterior estiver na `main`):

**Fase 0 PC da empresa** → 9 CI e endurecimento → **17a Contrato do cartão (antes de qualquer card novo)** → 14a Primeira conexão (quando o usuário ligar a flag em `app/.env`) → 4 Auditoria (Tarefas 4 a 9) → 5a Cards de trabalho → 5b Negativas, Riscos, Previsão e Épicos → 6a Capacidade e planejamento → 6b Worklog, posse, esforço e cobertura → 7a Checklist × Review e Integridade → 7b Distribuição, Governança e início → 10 Complementos de telas e filtros → 11 Auditoria completa e LGPD → 14b Validação e calibração dos cards (depois do 7) → **17b Cards de fluxo e gestão (PP5)** → 8 Preparação final (a remoção do `legacy/` só depois do "sim" do usuário ao documento de equivalência; sem ele, vira PR próprio depois) → 12 Operação em produção → 13 Implantação real → 15 Identidade final, inglês e entrega. O **Plano 16** (escrita no Jira pelo painel) é futuro: só com decisão do usuário.

**Por que a 9 e a 14a vêm antes do restante da Auditoria:** a 9 cria o CI (hoje não existe `.github/`), que passa a ser condição do merge automático; a 14a calibra campos, status e vínculos com o Jira real logo cedo, antes de seis planos de cards que dependem desses nomes. Argumento contra: a 14a depende de o usuário ligar a flag e de a rede da empresa estar liberada; se isso atrasar, pule para o Plano 4 (que roda com fixtures) e volte à 14a quando puder, registrando o `Ruling`.

A 14a (bloco de sincronização) começa logo depois do 9, quando o usuário ligar a flag; a validação dos cards (14b) vem depois do 7. A **Tarefa 0 do Plano 14** (hooks lendo a flag do `app/.env`) é pré-requisito do 14a.

**O Plano 4 depende do 4A (executar depois do 4A):** usa `JqlValues.issueKey` (4A, T3), `AvisoDto.params` (4A, T8), `issue.removida_em` (4A, T2 e T6), os dados de demonstração (4A, T10) e o teste `cadaPassoDoChecklistConfirmaAFlag` (4A, T11); sem o 4A na `main`, o Plano 4 não compila.

**Nota (25/10):** 4A tem 12 tarefas; Review ligada à task pelo link "Blocks / is blocked by" (4A, Tarefa 12, executada por último; o Plano 4 roda depois dela), não pelo `parent`; Plano 16 = escrita no Jira (futuro).

| Fase | Plano | Spec da fase | Estado |
|---|---|---|---|
| 0 | `2026-10-27-plano-00-pc-da-empresa.md` | `../specs/2026-10-27-spec-fase-00-pc-da-empresa.md` | Escrito, aguardando execução (primeira a executar no PC da empresa) |
| 1 | `2026-10-24-plano-01-fundacao.md` | — (spec mestra) | Executado (PR #6) |
| 2 | `2026-10-24-plano-02-dicionario-e-jql.md` | — (spec mestra) | Executado (PR #7) |
| 3A | `2026-10-25-plano-03a-inicializacao.md` | — (spec mestra) | Executado (PR #8) |
| 3 | `2026-10-24-plano-03-tempo-em-status.md` | — (spec mestra) | Executado (PR #9) |
| 3B | — (adendo) | `../specs/2026-10-27-spec-fase-3b-saida-do-docker-sqlite.md` | Executado (PRs #18 e #19): SQLite, sem Docker |
| 4A (12 tarefas) | `2026-10-25-plano-04a-sincronizacao.md` | `../specs/2026-10-25-spec-fase-04a-sincronizacao.md` | Executado (PR #14) |
| 4 | `2026-10-25-plano-04-auditoria.md` | `../specs/2026-10-25-spec-fase-04-auditoria.md` | Tarefas 1 a 3 na `main` (PR #15); Tarefas 4 a 9 executadas na branch `feat/auditoria-tarefas-4-a-9` (PR pendente) |
| 5 | `2026-10-25-plano-05-cards-trabalho-e-prazos.md` | `../specs/2026-10-25-spec-fase-05-cards-trabalho-e-prazos.md` | Bloco 5a (Tarefas 1 a 9) executado (PR #33, mesclado); bloco 5b (Tarefas 10 a 14) executado na branch `feat/cards-trabalho-5b` (PR pendente) |
| 6 | `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` | `../specs/2026-10-25-spec-fase-06-pessoas-planejamento-ausencias.md` | Escrito, aguardando execução |
| 7 | `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` | `../specs/2026-10-25-spec-fase-07-checklist-integridade-distribuicao-governanca.md` | Escrito, aguardando execução |
| 8 | `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` | `../specs/2026-10-25-spec-fase-08-preparacao-final-e-remocao-do-legacy.md` | Escrito, aguardando execução |
| 9 (12 tarefas) | `2026-10-25-plano-09-ci-e-endurecimento.md` | `../specs/2026-10-25-spec-fase-09-ci-e-endurecimento.md` | Executado (PR #25; CI ativo no GitHub) |
| 10 (12 tarefas) | `2026-10-25-plano-10-complementos-de-telas-e-filtros.md` | `../specs/2026-10-25-spec-fase-10-complementos-de-telas-e-filtros.md` | Escrito, aguardando execução |
| 11 (12 tarefas) | `2026-10-25-plano-11-auditoria-completa-e-lgpd.md` | `../specs/2026-10-25-spec-fase-11-auditoria-completa-e-lgpd.md` | Escrito, aguardando execução |
| 12 (12 tarefas) | `2026-10-25-plano-12-operacao-em-producao.md` | `../specs/2026-10-25-spec-fase-12-operacao-em-producao.md` | Escrito, aguardando execução |
| 13 (13 tarefas) | `2026-10-25-plano-13-implantacao-real.md` | `../specs/2026-10-25-spec-fase-13-implantacao-real.md` | Escrito, aguardando execução |
| 14 (15 tarefas: Tarefa 0 dos hooks, 14a e 14b) | `2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md` | `../specs/2026-10-25-spec-fase-14-primeira-conexao-validacao-e-calibracao.md` | Tarefa 0 executada (PR #26); 14a e 14b aguardando execução (depois da flag) |
| 17 (13 tarefas: 17a antes do 5a, 17b depois do 14b) | `2026-10-28-plano-17-cards-de-fluxo-e-gestao.md` | `../specs/2026-10-28-spec-fase-17-cards-de-fluxo-e-gestao.md` | **17a executado** (PR #31; progresso em `../progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md`); 17b aguardando (por ordem do usuário em 28/10 roda antes do 14b; ver o roteiro) |
| 15 (11 tarefas) | `2026-10-25-plano-15-identidade-final-ingles-e-entrega.md` | `../specs/2026-10-25-spec-fase-15-identidade-final-ingles-e-entrega.md` | Escrito, aguardando execução |

## Fase 4A — Sincronização periódica (depende das Fases 1 a 3)
Detalhada em `2026-10-25-plano-04a-sincronizacao.md` (12 tarefas): sincronização periódica com a flag `JIRA_LIVE_ENABLED` como única trava (sem autorização por execução), cursor e registro de execuções, ficha de sincronização e JQL com hora, `Syncer` em fluxo com tradução de chaves `JIRAUSER*`, carga inicial e incremental com retomada, reconciliação diária com remoção lógica, agendador único no ciclo do Ktor e retenção agendada (`issue` e `access_log`), status para o gerente e runbook da 1ª sincronização, dados de demonstração e hook alinhado à flag, e vínculos entre issues ("Blocks / is blocked by", tabela `issue_link`, Tarefa 12) que definem o par Task × Review. A Fase 4 depende desta (próximo número de migração livre na execução).

## Fase 4 — Auditoria (depende das Fases 1 a 3 e da 4A: executar depois do 4A)
Detalhada em `2026-10-25-plano-04-auditoria.md` (9 tarefas): labels de atraso e regras em YAML, linha do tempo de auditoria e autorização pela vigência na data, sinais de vencimento, responsável, passa-bola, cancelamento e labels, Task × Review, "no prazo" e "no prazo ajustado", leitura no SQLite, API restrita a coordenação e gerente com drill e gaveta, telas Auditoria e Divergência, e documento de diferenças. Estimativa e worklog ficam fora (sem dado); Integridade fica na Fase 7.

## Fase 5 — Cards de trabalho e prazos (depende das Fases 3, 4A e 4)
Detalhada em `2026-10-25-plano-05-cards-trabalho-e-prazos.md` (14 tarefas, em dois blocos que são PRs separados). **5a (9 tarefas):** campos de trabalho (prioridade, estimativas, pontos, épico, flag), ordem do fluxo e resoluções negativas; modelo de leitura `IssueSnapshot`/`WorkStore`; métricas transversais; filtros, escopo visível e rota genérica de card; Abertas, Negligenciadas ("Sem atualização" e "Paradas" separadas), Fechadas e Metas e prazos (no prazo, ajustado e pela data original); telas; consistência entre telas e diferenças. **5b (5 tarefas):** Negativas (eventos pela data do changelog), Riscos (pesos em YAML), Previsão (Monte Carlo com taxa de chegada) e Épicos (só com amostra própria), telas e diferenças.

## Fase 6 — Pessoas, planejamento e ausências (depende da Fase 5)
Detalhada em `2026-10-25-plano-06-pessoas-planejamento-ausencias.md` (16 tarefas, em dois blocos). **6a (9 tarefas):** equipes, ausências (sem motivo) e regras de planejamento em YAML somente leitura; horas por issue e capacidade; demanda, ocupação com "sem capacidade", atraso acumulado e Jain; agenda pela capacidade (ligada a Riscos e Previsão); Carga, Capacidade e ausências, Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana, Gargalos e Tendência; telas e diferenças. **6b (7 tarefas):** sincronização do worklog sem comentário; Posse da task; Esforço por task fechada (índice composto em YAML); Cobertura por ausência e Simulador; Competências, Revisões pendentes e Carga por tipo/OS; telas; diferenças e garantia de somente leitura.

## Fase 7 — Checklist × Review, Integridade, Distribuição, Governança e telas de início (depende das Fases 4 a 6)
Detalhada em `2026-10-25-plano-07-checklist-integridade-distribuicao-governanca.md` (18 tarefas, em dois blocos). **7a (8 tarefas):** pares e alertas do Checklist × Review, estatísticas e lift com amostra mínima, card e tela; os 12 sinais da Integridade, nível por pessoa e impacto nas metas, API só do gerente e sempre auditada; diferenças. **7b (10 tarefas):** distribuição e rebalanceamento em modo sombra, decisões gravadas só no painel (sem escrita no Jira), telas; Qualidade dos dados (com done × resolution), registro de acesso e configuração em leitura; Central de atenção, Visão do squad e Meu painel; diferenças.

## Fase 8 — Preparação final, acessibilidade e remoção do `legacy/` (depende das Fases 4A a 7)
Detalhada em `2026-10-25-plano-08-preparacao-final-e-remocao-do-legacy.md` (10 tarefas): inventário de rotas com papel e registro; SSO/OIDC genérico testado com emissor falso; equivalência por card com o app atual; WCAG AA (contraste dos três temas, teclado, rótulos); revisão visual; desempenho na escala da spec sobre SQLite (leitura concorrente e escrita durante leitura); pacote de produção (`installDist` + build do frontend) e guia de implantação sem contêiner; checklist da 1ª conexão e matriz finais; remoção do `legacy/` (tag `legacy-final`, só com aprovação do usuário); guia do painel e documentação final.

## Fase 9 — CI e endurecimento (depende da 4A; antes do 4)
Detalhada em `2026-10-25-plano-09-ci-e-endurecimento.md`: (12 tarefas): workflow de CI do GitHub Actions só com JDK 21 e Node 22/pnpm (jobs `backend`, `frontend`, `guarda`, `varredura`; sem Docker nem serviços), que passa a ser exigido antes do merge (proteção da `main` é ação do usuário); CSP e cabeçalhos de segurança; CSRF; sessão expirada na tela; pacote `installDist` que sobe em modo produção por padrão e modelo de variáveis que nunca liga o modo dev; barreira por teste contra o retorno de Docker/PostgreSQL/Testcontainers; Minor técnicos adiados.

## Fase 10 — Complementos de telas e filtros (depende das Fases 5 a 7)
Detalhada em `2026-10-25-plano-10-complementos-de-telas-e-filtros.md`: filtros pelo dicionário além de OS, gaveta universal da task, paginação virtual, estados de tela e mensagens do backend por código.

## Fase 11 — Auditoria completa e LGPD (depende das Fases 4, 6 e 7)
Detalhada em `2026-10-25-plano-11-auditoria-completa-e-lgpd.md` (12 tarefas): sinais de estimativa reduzida e worklog retroativo, trava de Auditoria e Integridade até o aceite do DPO, inventário de dados tratados e material para o DPO.

## Fase 12 — Operação em produção (depende das Fases 8 e 9)
Detalhada em `2026-10-25-plano-12-operacao-em-producao.md` (12 tarefas): logs estruturados, métricas, readiness (arquivo do banco, migrações, disco, trava) e alertas em arquivo/log/tela; validador de configuração; backup agendado do SQLite (`VACUUM INTO`, integridade, SHA-256, retenção) com restauração testada automaticamente; runbooks diário e de incidentes (Windows e sh); rotação de segredos.

## Fase 13 — Implantação real (depende das Fases 8, 9, 11 e 12)
Detalhada em `2026-10-25-plano-13-implantacao-real.md` (13 tarefas): decisões com a infraestrutura (provedor OIDC, máquina e gerenciador do serviço, proxy e TLS, segredos, entrega do pacote: pontos de parada); username pelo perfil do provedor; proxy confiável; escuta restrita a `127.0.0.1` e guardas de produção; versão do pacote em `/api/meta`; zip com SHA-256 publicado pelo CI; serviço do SO (Windows: NSSM ou Agendador de Tarefas; Linux: systemd); segredos; atualização e reversão ensaiadas; homologação e fumaça; produção com a flag do Jira desligada.

## Fase 14 — Primeira conexão, validação e calibração (14a depois da 4A; 14b depois da 7)
Detalhada em `2026-10-25-plano-14-primeira-conexao-validacao-e-calibracao.md` (14 tarefas, dois PRs). **14a (6 tarefas):** o usuário liga a flag (a IA pergunta e espera); roteiro; casador de campos e conferência de status sobre respostas salvas fora do repositório; registro de divergências só com agregados; checklist passo a passo; primeiro ciclo de sincronização; correção com teste de cada regra refutada. **14b (8 tarefas):** recarga completa sem rede; roteiro de validação por ficha; validação dos cards Samsung; cards da Sidia (CAT, ponto de parada); calibração por retroteste e aplicação com aprovação; aceite do gestor card a card; fechamento.

## Fase 15 — Identidade final, inglês e entrega (depende das Fases 8, 9 e 14)
Detalhada em `2026-10-25-plano-15-identidade-final-ingles-e-entrega.md` (11 tarefas): ativos de marca (ponto de parada, com caminho alternativo); logo saneado; fonte com alternativa de sistema; azul `#1329A0` único, moldura do tema Sidia e tema sem piscada; `DESIGN.md` verificado contra os tokens; guia e FAQ em inglês; treinamento por papel; sessões registradas; dono da manutenção e checklist de entrega; fechamento.

## Migrações
Desde 26/10 (saída do Docker/PostgreSQL) existe **uma só migração, a `V1__base.sql`**, com o esquema final (as antigas V4 a V6 do 4A estão incorporadas nela). **A próxima é a `V2`.** Os rótulos "V7" a "V13" que ainda aparecem nos planos são apenas nomes de ordem: na execução, confirme o próximo número livre com `ls app/backend/src/main/resources/db/migration` e nunca edite migração já mesclada. A tabela é a sequência esperada, na ordem de execução acima.

| Ordem | Plano | Observação |
|---|---|---|
| 1ª | 5a | campos de trabalho (provável `V2`) |
| 2ª | 6b | worklog por instância |
| 3ª | 7b | decisão de distribuição |
| 4ª | 10 | campos do dicionário |
| 5ª | 11 | condicional (ponto de parada da Tarefa 3, ou remoção de `person`) |
| 6ª | 8 | condicional (índices de leitura, só se a Tarefa 6 pedir) |
| 7ª | 12 | execução de backup |
| 8ª | 16 | escrita no Jira (futuro) |

Sem migração: 0, 9, 4 (Tarefas 4 a 9), 13, 14 e 15. Blocos em branches paralelas renumeram a migração do PR que mesclar depois, antes do merge. Em SQLite não existe `ALTER COLUMN`: mudança de tipo ou restrição recria a tabela dentro da migração (adendo, seção 2).

## Fase 17 — Contrato do cartão e cards de fluxo e gestão (17a antes do 5a; 17b depois do 14b)
Detalhada em `2026-10-28-plano-17-cards-de-fluxo-e-gestao.md` (13 tarefas, dois PRs). **17a (7 tarefas):** registro de cartões (identidade, grupo, natureza, textos "o que mostra" e "como calcula"), drill genérico e teste de contrato (número do cartão = total do drill), componente `Cartao`, gaveta de tasks compartilhada com "Abrir no Jira" (somente leitura), migração de Tempo em status e Sincronização e tela Mapa de cartões. **17b (6 tarefas):** Fluxo da semana, Envelhecimento por etapa, Bloqueios entre tasks, Controle do ciclo e tendência, aba Cruzamentos. A spec (seção 6) acrescenta regras aos Planos 4, 5a, 5 a 7, 7b e 10 sem reescrevê-los. Visual: `docs/design/cartoes-gramatica-e-wireframes.md`.

## Candidatas (não numeradas): ideias a partir da referência do Jira Data Center
`docs/superpowers/ideias-de-cards-a-partir-da-referencia.md` reúne 27 ideias de novos cards, abas e ações (somente leitura), priorizadas, com fonte na referência local `docs/jira/referencia/`. Quatro delas pedem decisão do usuário antes de virar plano (pontos de parada PP1 a PP4 do documento). A correção preventiva do changelog (I-19) já foi feita (PR #28).
