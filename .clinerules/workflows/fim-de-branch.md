# /fim-de-branch.md: fechar um plano (revisão final, validação, push, PR, merge e volta à main)

O procedimento completo, com os comandos para Windows 11 PowerShell e sh, as condições de merge e os pontos de parada, está em **`docs/superpowers/fluxo-de-entrega.md`**. Leia-o inteiro e siga as seções 3 a 6, nesta ordem:

1. **Onda final e revisão final do conjunto** (seção 3): corrigir Minor por prioridade, suíte completa **sem cache** das duas camadas (mostrando os números), revisão independente em contexto limpo (`/revisar-tarefa.md`), HANDOFF e progresso atualizados.
2. **Varredura antes do push** (seção 4): e-mails, usernames reais, hosts internos, tokens, senhas; `git status` sem arquivos de configuração real nem `*.db`; repositório `PRIVATE`. Qualquer dúvida: pare e pergunte.
3. **Push, PR e CI** (seção 5): `git push -u origin <branch>`, `gh pr create --body-file ...`, `gh pr checks --watch`.
4. **Merge** (seção 5, lista de 7 condições): a IA **mescla por conta própria** (`gh pr merge --squash --delete-branch`) **somente se todas as condições forem verdadeiras**, por decisão do usuário em 27/10/2026. Faltando qualquer uma, não mescla: pergunta (`/perguntar-ao-usuario.md`). Nunca `--admin`, nunca push direto na `main`.
5. **Volta à main** (seção 6): `git checkout main`, `git pull --ff-only origin main`, suíte na `main`; a branch local já foi apagada pelo merge (se sobrar e a PR estiver `MERGED`, `git branch -D`, nunca `-d` após squash); só então começar o próximo plano.

- `git status`: nada pendente além do que o usuário mantém fora de commit (`legacy/.env.example`).
- Suíte completa. macOS/Linux: `cd app/backend && ./gradlew test`; `cd app/frontend && pnpm vitest run && pnpm build`. Windows PowerShell: `cd app\backend; .\gradlew.bat test`; `cd app\frontend; pnpm vitest run; if ($LASTEXITCODE -eq 0) { pnpm build }`. Anote os números. Se algo falhar, pare: volte a `/executar-plano.md`.
- `/revisar-tarefa.md` sobre `git diff main..HEAD`, sem Critical nem Important em aberto.

## 2. Varredura de dados pessoais (antes de qualquer push)

Rode e leia:
- `git log main..HEAD --format='%ae %s'`
- `git diff main..HEAD`

Procure: e-mails reais, usernames reais do time (compare de cabeça com os do `Filtros_Status_tool.txt` local, **sem copiar esses nomes para lugar nenhum**), hosts internos, tokens, chaves, senhas. Fixtures só com nomes fictícios (`ana.silva`, `bruno`, `.invalid`).

Confira também:
- `git status` não lista `.env`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml` nem `Filtros_Status_tool.txt`.
- O repositório é **público** (decisão do usuário em 26/10): `gh repo view --json visibility`. O histórico antigo tem 3 usernames reais (aceito e documentado), então a varredura acima é ainda mais importante: nada novo com dado real entra. Se a visibilidade mudar sem o usuário ter dito, pare e pergunte.

Qualquer achado: pare e pergunte (`/perguntar-ao-usuario.md`). Nunca publique "para corrigir depois".

## 3. Push e PR

1. Atualize `HANDOFF.md` (estado, próximos passos, pendências) e o índice dos planos; commit só desses arquivos.
2. `git push -u origin <branch>`
3. `gh pr create --base main` com título em Conventional Commits e descrição em pt-BR: o que traz, segurança, verificação (números dos testes), pendências. Sem `gh`: avise que o push foi feito e dê o link de comparação.

## 4. Merge: só com ordem do usuário

Pergunte, seguindo `/perguntar-ao-usuario.md`, por exemplo:
- "Esperar a sua revisão da PR (Recomendada)": você revisa com calma; a branch fica aberta até lá.
- "Mesclar agora com squash": a `main` recebe tudo já; só faz sentido se você já revisou.
- "Começar o próximo plano em outra branch a partir da `main`": adianta trabalho, mas o próximo plano não verá este código até o merge.

Mescle só quando o usuário mandar. Branches empilhadas: da base para o topo, refazendo cada PR contra a `main`.

## 5. Volta à main

- `git checkout main`
- `git pull`
- Depois do merge feito **pelo usuário ou por ordem dele**: confirme que compila e que os testes passam e apague a branch local. Nunca apague branch com código fora da `main` sem confirmação explícita.

Não termine a sessão com commit só local nem branch sem PR: faça push e abra a PR, ou registre no `HANDOFF.md` o motivo e a próxima ação.
