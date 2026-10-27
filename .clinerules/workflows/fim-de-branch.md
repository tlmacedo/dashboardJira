# /fim-de-branch.md: fechar um plano (revisão final, validação, push, PR, merge e volta à main)

O procedimento completo, com os comandos para Windows 11 PowerShell e sh, as condições de merge e os pontos de parada, está em **`docs/superpowers/fluxo-de-entrega.md`**. Leia-o inteiro e siga as seções 3 a 6, nesta ordem:

1. **Onda final e revisão final do conjunto** (seção 3): corrigir Minor por prioridade, suíte completa **sem cache** das duas camadas (mostrando os números), revisão independente em contexto limpo (`/revisar-tarefa.md`), HANDOFF e progresso atualizados.
2. **Varredura antes do push** (seção 4): e-mails, usernames reais, hosts internos, tokens, senhas; `git status` sem arquivos de configuração real nem `*.db`; repositório `PRIVATE`. Qualquer dúvida: pare e pergunte.
3. **Push, PR e CI** (seção 5): `git push -u origin <branch>`, `gh pr create --body-file ...`, `gh pr checks --watch`.
4. **Merge** (seção 5, lista de 7 condições): a IA **mescla por conta própria** (`gh pr merge --squash --delete-branch`) **somente se todas as condições forem verdadeiras**, por decisão do usuário em 27/10/2026. Faltando qualquer uma, não mescla: pergunta (`/perguntar-ao-usuario.md`). Nunca `--admin`, nunca push direto na `main`.
5. **Volta à main** (seção 6): `git checkout main`, `git pull --ff-only origin main`, suíte na `main`, `git branch -d <branch>`; só então começar o próximo plano.

Nunca termine a sessão com commit só local nem branch sem PR: PR em rascunho com o estado exato e progresso/HANDOFF atualizados (seção 7).
