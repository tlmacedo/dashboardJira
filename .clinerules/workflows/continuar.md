# /continuar.md: retomar o Painel de Squads de onde a última sessão parou

Porte do comando `/continuar` do Claude Code (`.claude/commands/continuar.md`). Não escreva código antes de terminar os passos 1 a 5.

1. Leia, nesta ordem: `.clinerules/00-regras-do-projeto.md`, `HANDOFF.md`, `CLAUDE.md` e `docs/guia-execucao-outra-ia.md` (se ainda não leu nesta tarefa).
2. Confira o estado real do repositório com comandos curtos e separados:
   - `git status`
   - `git log --oneline -5`
   - `git fetch`
   - `git branch -r --no-merged origin/main`
   - `gh pr list` (se o `gh` existir; senão, diga que não deu para ver as PRs)
3. Confira o ambiente (não há Docker nem banco a iniciar): `java -version` deve mostrar 21 e `git status` deve estar limpo; Windows PowerShell: use `;` no lugar de `&&` e não use `sh`/`bash`. Diga se o hook do Cline está ligado ("Enable Hooks" e `.clinerules/hooks/PreToolUse.ps1` no Windows); se não estiver, avise que as proibições dependem só de você.
4. Leia o arquivo de progresso do plano em andamento, se existir (`docs/superpowers/progresso/*.md`), e diga qual foi a última tarefa concluída.
5. Responda em poucas linhas, em pt-BR:
   - onde estamos (branch, último commit, plano e tarefa);
   - diferenças entre o `HANDOFF.md` e o estado real;
   - código fora da `main` (branches ou PRs não mescladas).
6. Se houver código fora da `main`, ou se o próximo passo do `HANDOFF.md` depender de decisão do usuário, **pare e pergunte** seguindo `/perguntar-ao-usuario.md`. Exemplo de opções: "Concluir a branch X primeiro (Recomendada)", "Pedir ao usuário para mesclar a PR", "Começar o próximo plano em outra branch".
7. Sem pendências: siga a lista "Próximos passos" do `HANDOFF.md` a partir do primeiro item não feito. Se for executar um plano, use `/executar-plano.md`; se for escrever um, `/escrever-plano.md`.
8. Ao fim da sessão: atualize o `HANDOFF.md` (estado, próximos passos, pendências), faça commit só dos arquivos tocados e siga `/fim-de-branch.md` se a branch acabou. Nunca deixe commit só local sem registrar o motivo no `HANDOFF.md`.

Lembrete fixo: com `JIRA_LIVE_ENABLED` diferente de `true`, nenhum acesso ao Jira por nenhum caminho; toda aprovação é pergunta com opções, a recomendada primeiro.
