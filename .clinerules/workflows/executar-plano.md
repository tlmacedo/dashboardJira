# /executar-plano.md: executar um plano, tarefa a tarefa

Porte do fluxo que o Claude Code fazia com as skills `superpowers:subagent-driven-development` e `superpowers:executing-plans`, adaptado ao guia (`docs/guia-execucao-outra-ia.md`, seção 4). Sem subagentes: você é o executor e o revisor, nesta ordem.

## Avisos para o Windows (ClineSR)

- O diretório do projeto não deve ter "jira" no nome (o hook `PreToolUse` bloqueia comandos com essa palavra): veja `docs/ferramentas-claude-para-o-cline/hooks.md`.
- O Gauss deve reler a tarefa inteira a cada passo (não trabalhe de memória): releia a tarefa, as Interfaces e o Foco da revisão antes de cada etapa.

## Antes do plano

1. Pergunte (ou confirme no `HANDOFF.md`) qual plano executar. Se não estiver claro, use `/perguntar-ao-usuario.md`.
2. Leia a spec da fase, o índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` e **o plano inteiro**, inclusive "Restrições globais", "Foco da revisão" e "Decisões".
3. Prepare a branch com comandos separados:
   - `git checkout main`
   - `git pull`
   - `git branch -r --no-merged origin/main` e `gh pr list`: havendo trabalho fora da `main`, pare e pergunte.
   - `git checkout -b feat/<nome-do-plano>`
4. macOS/Linux: `docker info` (se parado, `sh .claude/hooks/ensure-docker.sh`). Windows PowerShell: `docker info`; se parado, abra o Docker Desktop com `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"` e espere `docker info` responder (`do { Start-Sleep 5; docker info *> $null } until ($LASTEXITCODE -eq 0)`); não use `sh`/`bash`.
5. Crie `docs/superpowers/progresso/<arquivo-do-plano>.md` com a primeira linha `# Progresso — plano: <caminho do plano>`.

## Para cada tarefa (na ordem do plano)

1. Leia a tarefa inteira, as Interfaces (Consome/Produz) e o Foco da revisão.
2. **Vermelho:** escreva os testes nomeados na tarefa e rode-os. Eles precisam falhar **pelo motivo certo** (não só por erro de compilação). Se passarem de primeira, desconfie: o teste prova algo?
3. **Verde:** implemente o mínimo, com as assinaturas **exatas** do plano.
4. Rode os testes da tarefa e depois a suíte do módulo:
   - backend: macOS/Linux `cd app/backend && ./gradlew test`; Windows PowerShell `cd app\backend; .\gradlew.bat test`
   - frontend: macOS/Linux `cd app/frontend && pnpm vitest run`; Windows PowerShell `cd app\frontend; pnpm vitest run`
   - (no PowerShell 5.1 use `;` no lugar de `&&` e confira `$LASTEXITCODE` após cada comando)
5. **Revisão:** rode `/revisar-tarefa.md` sobre o diff da tarefa. Corrija tudo o que for Critical ou Important antes de commitar.
6. **Commit:** `git add` só dos arquivos da tarefa (nunca `-A` nem `-f`), mensagem Conventional Commits em pt-BR (`feat(...)`, `fix(...)`, `test(...)`, `docs(...)`), corpo curto.
7. **Progresso:** acrescente `Tarefa N: concluída (commit <hash>; testes X/0 falhas)`. Toda escolha que o plano deixou aberta vira `Ruling: <o que> — <por quê> — <custo se errado>`.

## Quando o plano deixa uma escolha em aberto

Escolha a opção mais segura e reversível, registre como Ruling e siga. Pare e pergunte (`/perguntar-ao-usuario.md`) só para: ação irreversível; segurança que o plano não resolve; efeito fora do repositório (merge, push em branch compartilhada, publicação, reiniciar o Docker); decisão de produto; plano tão quebrado que todo caminho é palpite; qualquer necessidade de acessar o Jira (resposta padrão: não).

## Quando algo falha

Não tente remendos em série. Reproduza, leia o erro inteiro, levante uma hipótese, teste só ela e só então corrija (é o espírito de `superpowers:systematic-debugging`). Depois de três tentativas sem progresso, pare e pergunte.

## Ao fim do plano

1. Suíte completa das duas camadas (macOS/Linux: `./gradlew test`; `pnpm vitest run && pnpm build`. Windows PowerShell: `.\gradlew.bat test`; `pnpm vitest run; if ($LASTEXITCODE -eq 0) { pnpm build }`). Mostre os números (testes e falhas); nada de "deve passar" sem rodar.
2. `/revisar-tarefa.md` sobre `git diff main..HEAD` inteiro.
3. Atualize `HANDOFF.md` e o índice dos planos.
4. `/fim-de-branch.md`.
