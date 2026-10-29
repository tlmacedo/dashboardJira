# Regras consolidadas (projeto + globais do usuário)

Todas as regras que o Claude Code segue neste projeto, sem repetição, com a origem de cada uma. Ordem de precedência (do `docs/guia-execucao-outra-ia.md`): **`CLAUDE.md` > guia > plano**. As regras globais do usuário (arquivo pessoal do Claude Code no PC dele, fora do repositório) entram aqui só no que vale para este projeto; quando conflitam com o projeto, vale o projeto.

Siglas da coluna "Onde": **C** = `CLAUDE.md` · **R** = `.clinerules/00-regras-do-projeto.md` · **G** = `docs/guia-execucao-outra-ia.md` · **H** = `HANDOFF.md` · **P4A** = `docs/superpowers/plans/2026-10-25-plano-04a-sincronizacao.md` · **GL** = regras globais do usuário · **K** = `.claude/hooks/guard.sh` (trava técnica) · **W** = workflows em `.clinerules/workflows/`.

## 1. Jira e a flag `JIRA_LIVE_ENABLED`

| # | Regra | Onde |
|---|---|---|
| 1.1 | Com a flag `false` (padrão) ou ausente: **nenhum** acesso a servidor Jira por nenhum caminho (app, agendador, `curl`, navegador, script, MCP da Atlassian), de nenhuma IA ou pessoa | C, R, H, P4A, K |
| 1.2 | Só o usuário muda a flag. Nenhuma IA a liga, nem dentro de um comando | C, R, P4A, K |
| 1.3 | Testes automáticos usam sempre fixtures e `MockEngine`, mesmo com a flag `true`; nunca o motor CIO | R, P4A |
| 1.4 | A primeira conexão real segue `docs/jira/checklist-primeira-conexao.md` | R, G |
| 1.5 | Variáveis novas começam com `SYNC_` ou `DASHBOARD_`, nunca `JIRA_` | P4A |
| 1.6 | Arquivos que citam variáveis do Jira são escritos com a ferramenta de edição, nunca com heredoc ou script no terminal | H, P4A |
| 1.7 | Pesquisa na web só na documentação pública do Jira; o projeto irmão e `Filtros_Status_tool.txt` (só vocabulário) são referência | C |
| 1.8 | No Cline, com a flag diferente de `true`, navegador e `web_fetch` não abrem nenhuma URL com `jira` ou `atlassian` (inclusive a documentação pública; decisão do usuário em 25/10). Nesses períodos a pesquisa na documentação pública é feita pelo usuário, colando trechos, ou pela busca genérica (`web_search`), abrindo só resultados sem `jira`/`atlassian` no endereço. Com `true`, nada disso é bloqueado | Usuário (25/10), hooks do Cline |


## 2. Segredos, configs reais e dados pessoais

| # | Regra | Onde |
|---|---|---|
| 2.1 | Nunca editar nem commitar `.env`, `config/papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`; usar `.exemplo`/`.demo`. No Cline, também não **ler** esses arquivos (o conteúdo iria para o modelo; os hooks do Cline bloqueiam) | C, R, H, K |
| 2.2 | `Filtros_Status_tool.txt` tem usernames reais: nunca editar, commitar nem copiar nomes dele | R, G |
| 2.3 | Fixtures, testes, docs e commits só com nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`) | R |
| 2.4 | Antes de qualquer push: varrer `git log main..HEAD` e `git diff main..HEAD` por e-mails, usernames reais, hosts internos, tokens e senhas; conferir a visibilidade (o repositório é **público** por decisão do usuário em 26/10: nada novo com dado real entra); `git status` sem arquivos protegidos | G, H, W (`fim-de-branch.md`) |
| 2.5 | Nunca `git add -A` nem `git add -f` | G, K |
| 2.6 | Token, senha e URL com credencial nunca em `toString`, log, erro, resposta HTTP, relatório nem no terminal (mesmo com a flag `true`) | G, P4A (Decisão 10) |
| 2.7 | Banco, log e `raw` sem dados pessoais (`displayName`, e-mail, avatar, `accountId`, texto livre) | G |
| 2.8 | A flag do Jira vale só no `app/.env` (terminal e sistema são ignorados); o `.env` real nunca vai para arquivo versionado | C, G |
| 2.9 | Arquivos que o projeto declara só locais (`.superpowers/`, segredos) não são commitados | GL, `.gitignore` |

## 3. Banco e plataforma

| # | Regra | Onde |
|---|---|---|
| 3.1 | **Proibido neste projeto (a TI da empresa bloqueia): contêineres, servidor de banco e WSL.** Não instale, não inicie e não tente usar nenhum deles. Também não use `bash`, `sh` nem `wsl` no terminal do Windows: no Windows 11 o `bash.exe` abre o WSL e o comando é abortado. Use sempre PowerShell. O banco é SQLite em arquivo (`DATABASE_PATH`); JDK 21 e Node/pnpm | C, G |
| 3.2 | Nunca trocar o SQLite real por H2 ou mock nos testes | C, R |
| 3.3 | Um painel por arquivo de banco (trava `<banco>.lock`); apagar `app/data/painel.db` só com o painel parado | G |
| 3.4 | Migração Flyway nova no próximo número livre; nunca editar migração já mesclada | P4A |

## 4. Idioma e comunicação

| # | Regra | Onde |
|---|---|---|
| 4.1 | Conversar em pt-BR; comentários de código em pt-BR; código em inglês ou pt-BR (seguir o arquivo vizinho) | C, R, GL |
| 4.2 | **Toda dúvida, pendência, falta de direção ou aprovação vira pergunta com 2 a 4 opções**: a recomendada primeiro, marcada "(Recomendada)", e cada opção com o porquê e a consequência (custo, risco). Uma pergunta por assunto. Nunca texto solto nem lista para resolver sozinho | C, R, GL, W (`perguntar-ao-usuario.md`) |
| 4.3 | Escolha pequena e reversível que o plano deixou aberta: decidir a mais segura, registrar como Ruling e seguir | R, G |
| 4.4 | Parar e perguntar: ação irreversível; segurança não resolvida; efeito fora do repositório; decisão de produto; plano quebrado; qualquer acesso ao Jira | R, G |
| 4.5 | Nunca contornar uma trava (hook, teste, regra) reescrevendo o comando; relatar | R, H |

## 5. Fluxo de trabalho, branch, PR e merge

| # | Regra | Onde |
|---|---|---|
| 5.1 | Ao iniciar a sessão: `git fetch`, listar branches e PRs fora da `main`; havendo, perguntar (mesclar, concluir primeiro, descartar com confirmação) | GL, G, W (`continuar.md`) |
| 5.2 | Uma branch por plano a partir da `main` atualizada: `feat/<nome-do-plano>` | R, G |
| 5.3 | Tarefa a tarefa, na ordem do plano, um commit por tarefa, Conventional Commits em pt-BR | R, G, GL |
| 5.4 | Arquivo de progresso versionado `docs/superpowers/progresso/<plano>.md` com tarefas concluídas e Rulings | G |
| 5.5 | Ao fim do plano: suíte completa, revisão do conjunto, varredura de dados pessoais, `HANDOFF.md` e índice atualizados, push, PR, volta à `main` e `pull` | R, G, H, W (`fim-de-branch.md`) |
| 5.6 | **Mesclar PR só pelo fluxo de entrega (7 condições, `docs/superpowers/fluxo-de-entrega.md`); Plano 16 só com ordem expressa do usuário; nunca apagar branch com código fora da `main`** | R, G, H |
| 5.7 | Branches empilhadas mescladas da base para o topo, cada PR refeita contra a `main` | GL |
| 5.8 | Trabalho em partes pequenas e mescláveis; funcionalidade incompleta atrás de flag | GL |
| 5.9 | Fim de sessão: `HANDOFF.md` atualizado; nada de commit só local nem branch sem PR (ou registrar o motivo e a próxima ação) | GL, G, H |
| 5.10 | Problema Critical ou Important na revisão, ou teste falhando: não seguir para PR/merge; corrigir | GL |

**Conflito resolvido a favor do projeto:** a regra global manda mesclar "assim que revisão e testes estiverem verdes e houver permissão" e fala em `master`, Play Store e aparelho (vale para outros projetos do usuário). Aqui a branch principal é `main` e **o merge só acontece quando o usuário mandar** (5.6).

## 6. TDD, testes e revisão

| # | Regra | Onde |
|---|---|---|
| 6.1 | TDD: teste nomeado que falha pelo motivo certo, código mínimo com as assinaturas exatas do plano, teste passando | R, G, W |
| 6.2 | Rodar a suíte afetada a cada tarefa; suíte completa ao fim (`./gradlew test`; `pnpm vitest run && pnpm build`) | R, G, C |
| 6.3 | Autorrevisão obrigatória com o checklist de 12 itens e mutação mental | G, W (`revisar-tarefa.md`) |
| 6.4 | Revisão proporcional ao risco: PR pequena, revisão simples; PR grande ou que mexe em banco, cálculo ou muitos arquivos, revisão ampla (no Cline: `revisar-tarefa.md` com as lentes extras) | GL |
| 6.5 | Evidência antes de afirmação: só dizer "passa" depois de rodar e ler a saída | GL (verificação), W |

## 7. Regras de produto e código (resumo; detalhe no `CLAUDE.md` e no guia)

| # | Regra | Onde |
|---|---|---|
| 7.1 | Stack Kotlin/Ktor + SQLite + React/Vite; o app Python antigo foi removido (tag `legacy-final`) | C |
| 7.2 | Fuso America/Manaus; dias úteis por padrão; relógio e calendário injetados | C, G |
| 7.3 | Concluída = categoria done **e** `resolution`; divergência vai para Qualidade dos dados | C |
| 7.4 | "No prazo" e "no prazo ajustado" lado a lado | C |
| 7.5 | Sem ranking de pessoas; tabelas em ordem alfabética | C, G |
| 7.6 | JQL só por `JqlBuilder`/`CardQueryFactory`; valores suspeitos rejeitados, nunca escapados; SQL só parametrizado | G, P4A |
| 7.7 | Nega por padrão em `/api`; toda rota de dados passa por `Visibility` e grava `access_log` | G |
| 7.8 | `(instance, key)` sempre; dado ruim vira aviso, não 500 | G |
| 7.9 | Interface: 3 temas, pt-BR e inglês, WCAG AA, cor nunca é o único sinal, CSV com neutralização de fórmula | C, G |
| 7.10 | Fail-closed em produção; só o modo dev cai nos `.exemplo` | G |
| 7.11 | `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões | C, H |

## 8. Modelos (só Claude Code)

A regra global "advisor Opus e subagentes Haiku" é do Claude Code e não tem equivalente direto. No Cline, o mais próximo é ligar "Use different models for Plan and Act" (modelo mais forte no Plan, mais barato no Act). Ver `README.md`.

No ClineSR o modelo é o **Gauss 3.7**, com limites de contexto e raciocínio não conhecidos aqui: tarefas pequenas, releitura do plano a cada tarefa, regras relidas no original (nunca resumidas de memória) e uso dos workflows. Detalhes na seção "Modelo Gauss 3.7" do `README.md`.

## 9. Executor, sistema e skills de terceiros

- O executor é o **ClineSR no Windows 11**: comandos em PowerShell (equivalentes no `README.md`), hook `.clinerules/hooks/PreToolUse.ps1` (testar antes, ver `hooks.md`).
- Precedência quando as instruções conflitam: `CLAUDE.md` > `.clinerules/` do projeto > workflows e skills do projeto > skills de terceiros. Em conflito, vale o projeto (ver `skills.md`).
- Toda dúvida, pendência ou falta de direção vira pergunta ao usuário com opções (`/perguntar-ao-usuario.md`).
