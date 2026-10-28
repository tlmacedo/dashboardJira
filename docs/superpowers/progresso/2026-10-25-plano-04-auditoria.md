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
| 6 `AuditStore` sobre SQLite | Concluída | ver `git log` | `SqliteAuditStore`; pares pelo `issue_link` (4A, T12) com as duas pontas ativas; sem migração |
| 7 a 9 | Em andamento nesta branch (ver as linhas abaixo quando concluídas) | | |

### Rulings da retomada de 28/10/2026 (Tarefas 4 a 9; sem consultar o usuário, opção recomendada)

- Ruling: alteração em lote (I-15) — chave `emLoteMinimo` em `auditoria.yaml` (2 a 100, padrão 5, opcional: arquivo real antigo continua valendo) e campo `Sinal.emLote: Int?`; conta issues DISTINTAS com evento da mesma pessoa, no mesmo campo e no mesmo minuto (UTC), nas issues carregadas; é só contexto (não cria nem apaga sinal, não entra na gravidade); não há contagem nem lista por pessoa no DTO. Custo se errado: remover a chave e o campo (a tela só mostra o "em lote (N)").
- Ruling: `Sinal.para` das labels — a label adicionada (um sinal por label), em vez do texto completo do `to`; `de` fica nulo. Permite distinguir os sinais do mesmo evento e ligar o sinal à label do "no prazo ajustado". Custo se errado: trocar em `DetectoresDeLabels`.
- Ruling: `NoPrazo.detalhar` além de `calcular` — devolve as issues por trás de cada número (a gaveta de tasks do cartão do contrato abre dali); `calcular` continua com a assinatura do plano.
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
