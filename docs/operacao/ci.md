# CI do Painel de Squads

O workflow `.github/workflows/ci.yml` roda em **todo pull request** e em todo push na `main`. Usa só JDK 21 (Temurin) e Node 22 com pnpm 9.12.3: o banco dos testes é um arquivo SQLite temporário, então não há serviço, contêiner nem imagem. Nenhum job acessa o Jira (a flag fica desligada e o workflow não declara segredo do Jira).

## 1. O que cada job prova

Os nomes dos jobs são os nomes dos checks que a proteção da `main` exige. Não renomeie, não filtre por caminho e não crie um quinto job sem atualizar a proteção (o teste `CiWorkflowTest` barra os três casos).

| Check | O que prova |
|---|---|
| `backend` | `./gradlew test` completo (SQLite real em arquivo temporário, MockEngine no lugar do Jira). |
| `frontend` | `pnpm lint`, `pnpm vitest run` e `pnpm build` do React/TypeScript. |
| `guarda` | Os testes dos hooks que protegem o acesso ao Jira: `.claude/hooks/test-guard.sh` (Claude Code) e `.clinerules/hooks/test-PreToolUse.sh` (Cline). |
| `varredura` | Os testes do script de varredura e a varredura do que o PR acrescenta (linhas adicionadas e mensagens de commit): arquivo proibido, e-mail, segredo, host interno e nome proibido. |

## 2. Rodar o mesmo localmente

Todos os comandos partem da raiz do repositório.

| O quê | PowerShell (Windows 11) | sh |
|---|---|---|
| Backend | `Push-Location app\backend; .\gradlew.bat test; Pop-Location` | `cd app/backend && ./gradlew test` |
| Frontend | `Push-Location app\frontend; pnpm lint; pnpm vitest run; pnpm build; Pop-Location` | `cd app/frontend && pnpm lint && pnpm vitest run && pnpm build` |
| Hook do Claude Code | `sh .claude/hooks/test-guard.sh` | `sh .claude/hooks/test-guard.sh` |
| Hook do Cline | `sh .clinerules/hooks/test-PreToolUse.sh` | `sh .clinerules/hooks/test-PreToolUse.sh` |
| Testes da varredura | `sh scripts/ci/test-varredura.sh` | `sh scripts/ci/test-varredura.sh` |
| Varredura do que a branch acrescenta | `git fetch origin main; sh scripts/ci/varrer-dados-pessoais.sh --base origin/main` | `git fetch origin main && sh scripts/ci/varrer-dados-pessoais.sh --base origin/main` |
| Varredura de toda a árvore versionada | `sh scripts/ci/varrer-dados-pessoais.sh --arvore` | `sh scripts/ci/varrer-dados-pessoais.sh --arvore` |

No Windows, o `sh`, o `grep` e o `jq` vêm do Git for Windows (`$env:ProgramFiles\Git\usr\bin`). Se o PowerShell não achar `sh`, use `& "$env:ProgramFiles\Git\bin\sh.exe" <script>`. O `jq` é necessário para os testes de hook (`winget install jqlang.jq`, ou o do Git Bash).

## 3. Nomes proibidos (opcional)

A varredura procura, como palavra inteira e sem diferenciar maiúsculas, cada nome listado:

- **No CI:** segredo do repositório `VARREDURA_NOMES`, um nome por linha. O usuário cria em *Settings* → *Secrets and variables* → *Actions* → *New repository secret*. Nunca no repositório.
- **Na máquina:** arquivo `scripts/ci/nomes-proibidos.local.txt`, um nome por linha. Ele é ignorado pelo git; nunca o versione.

Sem nenhuma das duas fontes, a categoria `NOME_PROIBIDO` fica inativa (as demais continuam valendo).

## 4. Como ler um check vermelho sem expor dados

A varredura nunca imprime o que encontrou. Cada achado sai como `ACHADO <categoria> <arquivo>:<linha>` (mensagem de commit: `commit:<hash curto>`; arquivo proibido: linha `0`). Abra o arquivo na linha indicada, corrija ou remova o dado e faça novo commit. Categorias:

| Categoria | Significa |
|---|---|
| `ARQUIVO_PROIBIDO` | Arquivo que nunca vai ao git: arquivo de variáveis real, configs com dados pessoais, catálogo de filtros, `settings.local.json` e arquivos de banco local. Os modelos `.exemplo`, `.demo` e `.env.example` são permitidos. |
| `EMAIL` | Endereço de e-mail de domínio que não está em `scripts/ci/padroes-permitidos.txt`. Use o domínio fictício `exemplo.invalid`. |
| `SEGREDO` | Token, senha, chave privada ou prefixo de token conhecido. Placeholders (`<...>`, `${...}`) e valores marcados como de teste não contam. |
| `HOST_INTERNO` | URL cujo host não é `.invalid`, `.example`, `localhost`, `127.0.0.1` nem público da lista permitida. |
| `NOME_PROIBIDO` | Nome da lista de nomes proibidos. |

`scripts/ci/padroes-permitidos.txt` aceita só hosts públicos e domínios fictícios. **Host da empresa ou dado pessoal nunca entra nessa lista:** corrija o arquivo, ou pergunte ao usuário.

## 5. Proteção da `main`: ação do USUÁRIO no GitHub

A IA nunca altera a configuração do repositório. Para os quatro checks serem exigidos antes do merge:

1. **Pré-requisito:** o workflow já rodou ao menos uma vez (o primeiro PR com este workflow). Sem isso o GitHub não oferece os nomes `backend`, `frontend`, `guarda` e `varredura` na busca de checks.
2. No GitHub: repositório → *Settings* → *Rules* → *Rulesets* → *New ruleset* → *New branch ruleset*. Nome: `main protegida`. *Enforcement status*: `Active`.
3. *Target branches* → *Add target* → *Include default branch*.
4. Regras: marque *Restrict deletions*; *Block force pushes*; *Require a pull request before merging* (com *Required approvals* em `0`, pois quem abre e quem mescla é a mesma pessoa; suba para `1` se houver outro revisor); *Require status checks to pass* → *Add checks* → escolha `backend`, `frontend`, `guarda` e `varredura`; marque *Require branches to be up to date before merging* (o fluxo é sequencial, então custa pouco e impede mesclar um PR testado contra uma `main` antiga).
5. *Bypass list*: deixe vazia (o usuário também fica sujeito à regra; numa emergência, desative o ruleset por alguns minutos e reative). Salve com *Create*.
6. Se o GitHub avisar que a regra não está disponível para repositório privado nesse plano da conta, a proteção automática não existe: a IA só mescla depois de conferir `gh pr checks <número>` com os quatro checks verdes (condição 5 do fluxo de entrega, por disciplina e não por trava técnica) e o fato fica registrado no `HANDOFF.md`. Nunca torne o repositório público para contornar sem decisão do usuário.
7. Conferir (somente leitura): `gh api repos/{owner}/{repo}/rules/branches/main` lista as regras ativas (mesmo comando no PowerShell e no sh; o `gh` preenche `{owner}` e `{repo}` dentro do clone). Aceite: abra um PR de teste e veja os quatro checks marcados como *Required* e o botão de mesclar bloqueado enquanto estiverem pendentes ou vermelhos.

## 6. Custo e limites

Runner `ubuntu-latest` hospedado pelo GitHub: cada PR consome alguns minutos da cota da conta. Todo job tem `timeout-minutes`, e a `concurrency` cancela a execução que ficou velha quando chega um push novo na mesma branch. Para trocar o runner (por exemplo, acrescentar `windows-latest` ao backend), edite `runs-on` e atualize a proteção da `main`: o nome do check muda.
