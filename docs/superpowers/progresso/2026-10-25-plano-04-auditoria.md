# Progresso — plano: docs/superpowers/plans/2026-10-25-plano-04-auditoria.md

Tarefas 1 a 3 já estão na `main` (PR #15, mesclada com squash em 26/10). Continue numa **branch nova a partir da `main`** (ex.: `feat/auditoria-2`); a antiga `feat/auditoria` foi apagada. **Plano 4 NÃO está terminado**: este arquivo diz exatamente de onde continuar. Quem continuar deve ler antes `docs/guia-execucao-outra-ia.md` e `docs/superpowers/progresso/2026-10-25-plano-04a-sincronizacao.md` (limites aceitos, regras dos hooks, o que o 4A deixou).

> **Nota (27/10/2026):** o projeto saiu do Docker/PostgreSQL (SQLite embutido; ver `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`). Na retomada, siga também `docs/superpowers/fluxo-de-entrega.md`; a Fase 0 (`2026-10-27-plano-00-pc-da-empresa.md`) vem antes. Menções a Testcontainers, compose e migrações V4+ neste arquivo são históricas.

## Estado por tarefa

| Tarefa | Estado | Commit | Observações |
|---|---|---|---|
| 1 Labels de atraso e regras da auditoria em YAML | Concluída, revisão aprovada | `bfdaea1` | `labels_atraso.yaml` e `auditoria.yaml` reais passam a ser obrigatórios em produção (modelos `.exemplo` só em dev) |
| 2 Linha do tempo de auditoria e quem pode alterar | Concluída, revisão aprovada | `f01edb5` | `AuditModel`, `AuditTimeline`, `Autorizacao`; `duedate` no `ChangelogParser` |
| 3 Sinais de vencimento, responsável, passa-bola e cancelamento | Concluída. A revisão reprovou 1 Important (DoS na contagem de dias úteis com datas absurdas), corrigido na rodada seguinte | `0e34a05` + `35d7326` | A contagem em `UTIL` para ao passar de `pertoDoVencimentoDias + 1`; anos fora de 2000..2100 são ilegíveis; sem re-revisão separada (o teste de tempo passaria também no código antigo: pega só regressão grosseira) |
| 4 Sinais de labels de atraso | Concluída (branch `feat/auditoria-tarefas-4-a-9`, retomada de 28/10) | `55e4446` | `DetectoresDeLabels`; um sinal por label (a label adicionada vai em `Sinal.para`); `ContextoDeteccao.diaAntes` (corrido ou útil) |
| 5 Task × Review, no prazo e calculador | Concluída | `5fb74f9` | `TaskReview`, `NoPrazo` (+ `detalhar`), `AuditCalculator`; modificador "em lote (N)" (I-15, spec 17 seção 6) |
| 6 `AuditStore` sobre SQLite | Concluída | `64dba1c` | `SqliteAuditStore`; pares pelo `issue_link` (4A, T12) com as duas pontas ativas; sem migração |
| 7 API de auditoria, histórico e card Task × Review | Concluída (parte backend; cartões no `CatalogoDeCartoes`) | `c146954` | `GET /api/auditoria/sinais`, `GET /api/auditoria/historico/{key}`, `GET /api/cards/task-review`; cartões `AU-01`, `AU-02`, `AU-03` e `DV-01` |
| 8 Telas Auditoria e Divergência (React) | Concluída; captura de verificação (Passo 5) NÃO feita | ver `git log` | Usa `Cartao` e `GavetaDeTasks` do 17a; `shared/csv.ts` já existia desde o 17a |
| 9 | Em andamento nesta branch (ver a linha abaixo quando concluída) | | |

### Rulings da retomada de 28/10/2026 (Tarefas 4 a 9; sem consultar o usuário, opção recomendada)

- Ruling: alteração em lote (I-15) — chave `emLoteMinimo` em `auditoria.yaml` (2 a 100, padrão 5, opcional: arquivo real antigo continua valendo) e campo `Sinal.emLote: Int?`; conta issues DISTINTAS com evento da mesma pessoa, no mesmo campo e no mesmo minuto (UTC), nas issues carregadas; é só contexto (não cria nem apaga sinal, não entra na gravidade); não há contagem nem lista por pessoa no DTO. Custo se errado: remover a chave e o campo (a tela só mostra o "em lote (N)").
- Ruling: `Sinal.para` das labels — a label adicionada (um sinal por label), em vez do texto completo do `to`; `de` fica nulo. Permite distinguir os sinais do mesmo evento e ligar o sinal à label do "no prazo ajustado". Custo se errado: trocar em `DetectoresDeLabels`.
- Ruling: `NoPrazo.detalhar` além de `calcular` — devolve as issues por trás de cada número (a gaveta de tasks do cartão do contrato abre dali); `calcular` continua com a assinatura do plano.
- Ruling: gavetas de tasks pelo contrato do 17a — em vez de `GET /api/drill/auditoria` e `GET /api/drill/task-review` (plano), os cartões `AU-01` (segmentos `todos`, `resultado:auditoria`, `resultado:alerta`), `AU-02` (`sinal:<CODIGO>`), `AU-03` (`concluidas`, `noPrazo`, `noPrazoAjustado`, `justificadasSobAuditoria`) e `DV-01` (`pares`, `divergentes`) se registram no `catalogoPadrao()` e a gaveta usa `GET /api/cartoes/{id}/tasks?segmento=...` (spec 17, seção 6: a regra nova vence os planos 5 a 7 e 10; vale também aqui por ordem do usuário). O teste `drillTemOMesmoTamanhoDoCard` fica em `AuditoriaRoutesTest` e o `ContratoDeCartoesTest` já percorre os quatro cartões. Custo se errado: criar as rotas `/api/drill/*` sobre as mesmas funções (`linhasDeSinais`, `linhasDePares`).
- Ruling: checagem de papel dentro de `executarAuditado` (e não `requireRole`) — o `requireRole` nega antes do handler e a negação não chegaria ao `access_log`, mas o plano exige a negação registrada (Review Focus 5). Mesmo critério: coordenador ou gerente (`papeisVigentes`; o principal `dev` do modo desenvolvimento é GERENTE sem ramo especial além do que o painel já tem). Custo se errado: trocar por `requireRole` e perder o registro do 403.
- Ruling: `cards/RotaAuditada.kt` não extrai nada do `TempoEmStatusRoutes.kt` — o 17a já extraiu `executar` (internal, usada pelo catálogo e pelo drill genérico). `executarAuditado(auditoria, ctx) { principal -> ... }` é um invólucro `internal` sobre ela; `TempoEmStatusRoutesTest` segue verde sem mudança. Os leitores de parâmetro (`texto`, `data`, `usernameValido`) passaram de `private` a `internal` para a auditoria reaproveitá-los.
- Ruling: link direto para o Jira (`urlTask`, `urlReview`) — `LinkJira.paraIssue` só monta o link com a flag ligada, instância configurada e base https sem credencial, consulta nem fragmento (base com credencial = sem link, mais estrito que "remontar sem userinfo" do plano). Com a flag desligada o painel não aponta para o Jira (regra 1 do CLAUDE.md vale também para quem clica). Custo se errado: remover o teste da flag em `paraIssue`.
- Ruling: instância fixa — a auditoria lê a instância `samsung` (a mesma constante do Tempo em status, `INSTANCIA_AUDITORIA`); a `sidia` entra quando houver ficha própria. Custo: parâmetro de instância nas rotas.
- Ruling: "no prazo" respeita só o filtro `squad` (squad do responsável no dia da resolução), nunca `pessoa`: a taxa de uma pessoa sozinha seria um indicador por pessoa. `pessoa` filtra os sinais (responsável no instante ou autor).
- Ruling: `TaskReviewDto` ganhou `divergencias: List<TaskReviewLinhaDto>` (só os pares divergentes, com os links) para a tela Divergência listar com link por linha; a gaveta de tasks do cartão continua sendo o drill genérico. Cada sinal é uma linha da gaveta (a mesma chave pode repetir: a gaveta do frontend passa a chavear por posição).
- Ruling: Tarefa 8, `shared/csv.ts` — o plano manda mover `TempoEmStatus/csv.ts` para `shared/csv.ts`; o 17a já fez isso, então nada foi movido. Os testes de CSV da tela (`csvNeutralizaFormulas`) ficam em `Auditoria.test.tsx` e os do utilitário em `shared/csv.test.ts`.
- Ruling: Tarefa 8, Passo 5 (captura em Chrome headless, 1440 e 390 px, três temas) — não executado: o ambiente desta execução não tem navegador. A tela só usa tokens dos temas e componentes do kit (`Cartao`, `GavetaDeTasks`, classes de `tempo.css` e `tabela.css`); a conferência visual fica para a revisão final do usuário, com `PAINEL_MOCK=1 pnpm dev` (o mock tem Auditoria, histórico e Task × Review). Custo se errado: uma rodada de captura e ajuste de `auditoria.css`.
- Ruling: Tarefa 8, filtro de pessoa — `<select>` com as pessoas que já apareceram nos sinais (responsável e autor), em ordem alfabética, sem contagem; a pessoa selecionada sempre aparece. Os KPIs reutilizam o mesmo cartão do catálogo (`AU-01` duas vezes, `AU-03` três vezes) com títulos próprios, e a lista de sinais é o cartão `AU-01` em variante lista.
- Ruling: `GavetaDeTasks` passou a chavear as linhas por chave + posição (uma issue pode ter dois sinais); teste `mostraAsDuasLinhasDaMesmaIssueSemAvisoDeChaveDoReact`.
- Ruling: `ResultadoAuditoria` ganhou `noPrazoDetalhe` (campo extra além do plano) pelo mesmo motivo.

Testes no último ponto verde conhecido: backend 745+, 0 falhas (SQLite real, desde a saída do Docker; era 744 com PostgreSQL). Confirme com `cd app/backend && ./gradlew test` antes de seguir.

**Minor da revisão da Tarefa 3:** `JIRAUSER*`/conta de serviço saem `NAO_AUTORIZADO` (o brief fixa isso; decidir se a Tarefa 4 ou a tela rotula como autor desconhecido); empate de instante usa `<` estrito e a ordem do empate depende da entrada; texto ilegível de `VENCIMENTO_REMOVIDO` sem limite de tamanho; em `UTIL`, sábado com vencimento na sexta anterior dá distância 0 e conta como "perto"; `from=" "` contra `to=null` conta como troca; autorização avaliada duas vezes por sinal (duplicação trivial); faltam testes de vigência, de empate, de feriado em `UTIL` e de `to` ilegível.

## Como continuar (passo a passo)

1. `git checkout main && git pull && git checkout -b feat/auditoria-2`; confira `git log --oneline -5` e rode a suíte.
2. Leia a próxima tarefa no plano (`### Tarefa N`) e a spec `docs/superpowers/specs/2026-10-25-spec-fase-04-auditoria.md`.
3. Siga o fluxo do guia: testes primeiro (veja falharem), implementar, rodar, autorrevisão pelo checklist, um commit por tarefa, atualizar ESTE arquivo.
4. Ao terminar as 9 tarefas: revisão final do conjunto, varredura de dados pessoais, PR e `main` com `pull`. Mesclar pelo fluxo de entrega (`docs/superpowers/fluxo-de-entrega.md`).

## Minor adiados do Plano 4 até aqui (para a onda final)

- **Tarefa 1:** README e comentário do compose não listam `labels_atraso.yaml` e `auditoria.yaml` como obrigatórios em produção (o compose só o usuário edita, por causa do hook: entregar o trecho pronto e perguntar); `statusCancelamento` é guardado como veio, mas o YAML promete "sem diferenciar maiúsculas" (quem consome normaliza); faltam testes para item em branco em `statusCancelamento`, `*` sozinho e `A**` em `justificativas`, `unidade` ausente, `posseAntesDoVencimentoDias` negativo, e "sem eco" para `labels_atraso.yaml`.
- **Tarefa 2 (revisão aprovada; Minor):** autor `JIRAUSER*` não resolvido ou conta de serviço cai em `NAO_AUTORIZADO`, e o detector (Tarefa 3) deve rotular esse caso como `AUTOR_DESCONHECIDO` (o autor não passa pela tradução de chaves no parser); coordenador ou gerente que é o próprio responsável sai como `COORDENACAO` (segue o brief, mas o `CLAUDE.md` diz que o responsável não conta: decidir e fixar com teste); eventos no mesmo instante são ignorados na consulta "em `at`" (o detector deve avaliar cada evento com `responsavelEm(e.at)`; falta teste de empate); texto de `fromString`/`toString` no `duedate` sem limite de tamanho; faltam testes de coordenador que deixou de sê-lo na data e de `duedate` apagado da timeline.
- **Tarefa 2 (decisões do implementador):** `duedate` apagado no changelog vira "sem vencimento"; eventos no mesmo instante são ignorados na consulta "em `at`"; status sem mapa conta como não fechado em vez de lançar; `posseDesde` devolve a criação da issue se nunca houve troca.
- Em produção, depender de `vinculos.yaml` real (Plano 4A, Tarefa 12) para o card Task × Review; sem vínculos o card fica vazio com o aviso `VINCULOS_INDISPONIVEIS`.
- A Auditoria só tem dados reais depois da sincronização (Plano 4A) e do aceite do DPO antes do uso com dados reais (Plano 11).

## Regras que valem aqui (resumo)

- A flag de acesso ao Jira é a única trava; só o usuário a liga, à mão. Nenhum teste toca o Jira.
- Hooks: a IA não edita compose, settings nem arquivo de ambiente; texto com a flag ligada em forma de atribuição é barrado; mensagens de commit via arquivo (`git commit -F`).
- Visibilidade da Auditoria: coordenador e gerente (e o usuário `dev` em modo desenvolvimento). Sem ranking de pessoas.
