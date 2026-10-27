# Fluxo de entrega: da tarefa ao merge (procedimento único para o ClineSR e qualquer IA)

**Decisão do usuário (27/10/2026):** ao fim de cada plano (ou de cada bloco que o índice marca como PR separado, como 5a e 5b), a própria IA executora revisa, valida, publica, abre a PR, **mescla na `main`** e atualiza o clone local. O usuário não precisa mandar o merge, **desde que todas as condições da seção 5 estejam satisfeitas**. Se qualquer uma falhar, a IA **não mescla** e pergunta (seção 8). Esta decisão substitui a antiga regra "mesclar só quando o usuário mandar" (`CLAUDE.md` regra 7 e `.clinerules` proibição 4 foram atualizados). Ela **não** autoriza: ligar a flag do Jira, editar `app/.env`, mudar configuração do repositório, usar `--admin`/`--force`, nem escrever direto na `main`.

**Precedência:** `CLAUDE.md` > `.clinerules/` > `docs/guia-execucao-outra-ia.md` > `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` > este fluxo > plano. Em conflito, vale o de cima.

**Unidades.** *Tarefa* = `### Tarefa N` do plano: gera **um commit** e passa por revisão própria (seção 2). *Plano/bloco* = o conjunto de tarefas que o índice define como **um PR**: gera revisão final, validação, PR e merge (seções 3 a 6). Nunca acumule dois planos numa branch, nem comece o plano seguinte antes de a seção 6 estar concluída.

Os comandos têm duas formas: **Windows 11 PowerShell** (executor principal, PowerShell 5.1 não tem `&&`: use `;` e teste `$LASTEXITCODE`) e **sh**. Cada comando é independente do diretório: ao começar, `cd` para a raiz do repositório.

---

## 1. Antes de começar um plano (branch nova a partir da `main` atualizada)

```powershell
git status                                  # tem que estar limpo
git checkout main
git pull --ff-only origin main              # se falhar: pare e pergunte (histórico divergiu)
git branch -r --no-merged origin/main       # branches remotas com código fora da main
gh pr list --state open                     # PRs abertas
git checkout -b feat/<nome-do-plano>
```
```sh
git status && git checkout main && git pull --ff-only origin main
git branch -r --no-merged origin/main; gh pr list --state open
git checkout -b feat/<nome-do-plano>
```

- Havendo trabalho fora da `main` ou PR aberta que você não criou: **pare e pergunte** (seção 8).
- Primeiro commit da branch (pequeno, só documentação): marque no índice (`docs/superpowers/plans/2026-10-24-indice-dos-planos.md`) o plano anterior como `Executado (PR #N)` e este como `Em execução`; crie o arquivo `docs/superpowers/progresso/<arquivo-do-plano>.md` (primeira linha `# Progresso — plano: <caminho do plano>`).
- Rode a suíte uma vez para saber o ponto de partida e anote os números no arquivo de progresso: `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm install; pnpm vitest run`. Se a `main` já falha, **pare**: a falha não é sua, mas você não pode empilhar trabalho sobre uma base vermelha sem perguntar.

## 2. Ciclo de cada tarefa (revisão por tarefa)

1. Releia **o texto da tarefa**, suas Interfaces (Consome/Produz) e o Foco da revisão do plano. Confira nomes no código real; divergindo do plano, siga o código e registre `Ruling:` no progresso.
2. TDD: escreva os testes da tarefa e **veja-os falhar pelo motivo certo**. Teste que passa de primeira é suspeito (faça a mutação: quebre a linha, veja falhar, desfaça).
3. Implemente o mínimo, com as assinaturas exatas do plano. Rode os testes da tarefa e a suíte do módulo.
4. **Autorrevisão** pelo checklist do guia (`docs/guia-execucao-outra-ia.md`, seção 5) e **revisão independente**: abra uma *nova tarefa* do Cline (contexto limpo) com o texto de `docs/ferramentas-claude-para-o-cline/processo-completo.md` seção 4 (ou `/revisar-tarefa.md`). Somente leitura; devolve achados Critical/Important/Minor.
5. Corrija Critical e Important em **commit novo** (não reescreva o anterior); no máximo 3 rodadas por tarefa; na 4ª, pare e pergunte. Minor vai para "Minor adiados" do progresso, com arquivo e cenário.
6. Commit: `git add <só os arquivos da tarefa>` (nunca `git add -A`, nunca `-f`); mensagem em pt-BR no padrão Conventional Commits, **por arquivo** para não esbarrar nos hooks: crie `$env:TEMP\msg.txt` (sh: `/tmp/msg.txt`) e rode `git commit -F $env:TEMP\msg.txt`.
7. Atualize o progresso: `Tarefa N: concluída (commit <hash>; testes X totais, 0 falhas; revisão: aprovada)`.

Tarefa não é "terminada" sem: testes verdes **com números mostrados**, revisão sem Critical/Important, commit feito e progresso atualizado.

## 3. Fechamento do plano: onda final e revisão final

1. Releia os "Minor adiados" e os compromissos do progresso. Faça **uma onda única** de correções, em commits pequenos por tema, por prioridade: **A** dados pessoais e segurança; **B** comportamento; **C** testes fracos e estilo. Não amplie o escopo.
2. Suíte completa **sem cache**, das duas camadas, mostrando os números:
```powershell
cd app\backend; .\gradlew.bat test --rerun-tasks
cd ..\frontend; pnpm install --frozen-lockfile; pnpm vitest run; if ($LASTEXITCODE -eq 0) { pnpm build }
```
```sh
(cd app/backend && ./gradlew test --rerun-tasks) && (cd app/frontend && pnpm install --frozen-lockfile && pnpm vitest run && pnpm build)
```
   Depois que o Plano 9 estiver na `main`, rode também os testes dos hooks (`sh .claude/hooks/test-guard.sh`; `.clinerules\hooks\test-PreToolUse.ps1`).
3. **Revisão final do conjunto** em contexto limpo (texto em `processo-completo.md`, seção 6) sobre `git diff main..HEAD`. Critical/Important → corrija em commits novos e peça **re-revisão focada** nas correções. O veredicto precisa dizer explicitamente "PRONTA PARA PUSH/PR/MERGE".
4. Atualize `HANDOFF.md` (estado, próximo passo, pendências) e o arquivo de progresso (estado final, números finais, Minor restantes, rulings). Commit só desses arquivos.

## 4. Varredura antes do push (obrigatória, sem exceção)

```powershell
git log main..HEAD --format='%ae | %s'
git diff main..HEAD --stat
git diff main..HEAD | Select-String -Pattern '@[a-z0-9-]+\.(com|br|net|org)|token|password|senha|secret|bearer' -CaseSensitive:$false
git status --short
gh repo view tlmacedo/dashboardJira --json visibility
```
```sh
git log main..HEAD --format='%ae | %s'; git diff main..HEAD --stat
git diff main..HEAD | grep -n -i -E '@[a-z0-9-]+\.(com|br|net|org)|token|password|senha|secret|bearer'
git status --short; gh repo view tlmacedo/dashboardJira --json visibility
```

Leia o resultado inteiro. Condições para seguir: nenhum e-mail real, username real do time, host interno, token, chave ou senha no diff nem nas mensagens (as ocorrências de "token"/"password" em **testes que verificam mascaramento** usam valores fictícios, como `abc123`/`sg1`: confirme valor a valor); `git status` sem `.env`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`, `calendario.yaml`, `status_map.yaml`, `jira_campos.yaml`, `vinculos.yaml`, `Filtros_Status_tool.txt` nem arquivos `*.db`; visibilidade `PRIVATE`. Qualquer dúvida: **pare e pergunte**. Nunca publique "para corrigir depois".

## 5. Push, PR e condições de merge

```powershell
git push -u origin feat/<nome-do-plano>
# corpo da PR em arquivo fora do repositório
@"
## O que traz
...
## Segurança
...
## Verificação
Backend: X testes, 0 falhas. Frontend: Y testes, 0 falhas. Build: ok.
## Não testado / pendências
...
"@ | Set-Content -Encoding utf8 $env:TEMP\pr.md
gh pr create --base main --head feat/<nome-do-plano> --title "<tipo>(<escopo>): <resumo>" --body-file $env:TEMP\pr.md
gh pr checks --watch                      # espera o CI (existe depois do Plano 9)
```
```sh
git push -u origin feat/<nome-do-plano>
gh pr create --base main --head feat/<nome-do-plano> --title "<tipo>(<escopo>): <resumo>" --body-file /tmp/pr.md
gh pr checks --watch
```

**Só mescle se TODAS forem verdadeiras** (registre cada uma no progresso):
1. Todas as tarefas do plano/bloco concluídas e revisadas; nenhuma "não iniciada".
2. Revisão final: "PRONTA PARA PUSH/PR/MERGE", sem Critical/Important abertos (nem em re-revisão).
3. Suíte completa sem cache verde nas duas camadas, números no corpo da PR. Em PR de documentação pura, a suíte ainda precisa estar verde.
4. Varredura da seção 4 limpa e repositório `PRIVATE`.
5. CI verde **no último commit** (`gh pr checks`), quando o Plano 9 já estiver na `main`. Antes dele não há CI: vale a suíte local. CI vermelho: ache a causa; **uma** nova tentativa só se o log mostrar falha de infraestrutura antes de qualquer teste; nunca desative nem pule teste, nunca empurre commit vazio para "chutar" o CI.
6. A PR não está em rascunho, não tem conflito (`gh pr view --json mergeable,mergeStateStatus`) e nenhum comentário de revisão humana pendente.
7. Nada nela liga a flag do Jira, altera `app/.env`, configuração do GitHub ou os hooks de proteção sem ter sido pedido pelo usuário.

Conflito com a `main`: `git fetch origin; git merge origin/main` (merge, não rebase), resolva, regenere lockfile com a ferramenta, rode a suíte, refaça as seções 4 e 5. Se os dois lados mudaram a mesma lógica de modo que escolher um perde comportamento: pergunte.

## 6. Merge e retorno à `main`

```powershell
gh pr merge <número> --squash --delete-branch --subject "<mesmo título da PR>"
git checkout main
git pull --ff-only origin main
git log --oneline -3                          # o commit do merge tem que aparecer
git branch -d feat/<nome-do-plano>            # -d (não -D): recusa se houver código fora da main
git status                                    # limpo
```
```sh
gh pr merge <número> --squash --delete-branch --subject "<mesmo título da PR>"
git checkout main && git pull --ff-only origin main && git log --oneline -3
git branch -d feat/<nome-do-plano> && git status
```

Depois: rode a suíte **na `main`** (backend e frontend) para provar que a `main` mesclada compila e passa; anote os números no progresso do **próximo** plano. Se a `main` ficou vermelha: pare, avise o usuário com o log e não comece o próximo plano.

- Se o `gh pr merge` for recusado (proteção de branch, aprovação exigida, checks obrigatórios): **não use `--admin` nem desative a proteção**. Pare e pergunte (seção 8).
- O estado "Executado (PR #N)" no índice e no `HANDOFF.md` entra no **primeiro commit da branch do próximo plano** (seção 1), nunca como commit direto na `main`. Nunca faça `git push` para a `main`.
- Sem `gh` instalado ou autenticado (`gh auth status` falha): faça o push da branch, **pare** e entregue ao usuário o link de comparação e o texto da PR; peça que ele abra e mescle (ou que autentique o `gh`).
- Em seguida, e só então, comece o próximo plano (seção 1).

## 7. Fim de sessão sem plano concluído

Nunca termine com commit só local. Se o plano está incompleto: push da branch, PR em **rascunho** (`gh pr create --draft`) com o estado exato, progresso e `HANDOFF.md` atualizados, e **sem merge**. A sessão seguinte retoma por `/continuar.md`.

## 8. Quando parar e perguntar (pergunta com 2 a 4 opções, a recomendada primeiro)

Pare para: qualquer acesso ao Jira ou necessidade de ligar/desligar a flag (a resposta padrão é "não": só o usuário muda a flag, à mão, em `app/.env`); condição de merge da seção 5 não atendida; 4ª rodada de correção; CI vermelho que você não consegue explicar; conflito de lógica; `main` vermelha; `gh` recusando o merge; qualquer dado pessoal duvidoso; ação irreversível ou fora do repositório (apagar branch com código fora da `main`, mudar configuração do GitHub, publicar release); decisão de produto; plano quebrado em que todo caminho é palpite. Decisões já tomadas pelo usuário estão no `CLAUDE.md`, no `HANDOFF.md` e nos `Ruling:` dos progressos: não pergunte de novo.

## 9. Resumo em uma tela

```
branch nova da main → [ tarefa: TDD → testes → autorrevisão → revisão independente → commit → progresso ] × N
→ onda final → suíte sem cache → revisão final do conjunto → HANDOFF/progresso
→ varredura de dados pessoais → push → PR → CI verde → condições da seção 5 → merge (squash)
→ checkout main → pull --ff-only → suíte na main → próximo plano
```
