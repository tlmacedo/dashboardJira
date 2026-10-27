# Ferramentas do Claude Code levadas para o Cline

Para quem vai continuar o Painel de Squads com o **ClineSR** (variante Samsung do Cline, com o modelo **Gauss 3.7**) num **PC com Windows 11**, a partir de um clone do GitHub. Sobre o ClineSR, só se sabe o que o usuário informou; o resto deste documento foi conferido no Cline público. Aqui está o que o Claude Code usava (regras, skills, hooks, comandos, subagentes, memória e modelos) e o equivalente no Cline: porte feito, como instalar e o que não tem equivalente.

**Antes de tudo:** leia `.clinerules/00-regras-do-projeto.md` e `docs/guia-execucao-outra-ia.md`. Prepare o PC (seção "Windows 11" abaixo), rode o teste do hook e depois `/continuar.md`. **Toda dúvida vira pergunta ao usuário** com opções (`/perguntar-ao-usuario.md`).

## Índice

| Documento | Conteúdo |
|---|---|
| [processo-completo.md](processo-completo.md) | **Leia primeiro.** O ciclo completo que o Claude executou (implementar, revisar com contexto limpo, corrigir, onda final, revisão final, PR, voltar à `main` com `pull`), com os textos prontos do implementador e do revisor |
| [regras.md](regras.md) | Todas as regras do projeto e as globais do usuário que valem aqui, deduplicadas, com a origem de cada uma e os conflitos em aberto |
| [skills.md](skills.md) | Cada skill, comando e agente do Claude: origem (repositório público), licença, resumo, uso neste projeto e porte para o Cline |
| [hooks.md](hooks.md) | O que cada hook do Claude faz, os hooks `PreToolUse` do Cline (bash para macOS/Linux e `PreToolUse.ps1` para Windows), como instalar e testar, e as proibições que não dependem de hook |
| `scripts/instalar-skills-cline.ps1` | Instala ou atualiza as skills de terceiros (superpowers, mattpocock-skills, impeccable) no Windows; ver [skills.md](skills.md) |

## Workflows disponíveis (`.clinerules/workflows/`, digite `/nome.md` no chat)

| Workflow | Quando usar |
|---|---|
| `/continuar.md` | Início de toda sessão: lê o `HANDOFF.md`, confere git, PRs e ambiente, diz onde estamos e o próximo passo |
| `/executar-plano.md` | Executar um plano tarefa a tarefa (TDD, revisão, commit, progresso) |
| `/revisar-tarefa.md` | Revisar uma tarefa ou o plano inteiro (checklist de 12 itens, mutação mental, lentes extras) |
| `/fim-de-branch.md` | Fechar a branch: verificação, varredura de dados pessoais, push, PR, volta à `main`; nunca mesclar sem o usuário mandar |
| `/perguntar-ao-usuario.md` | Toda dúvida ou falta de direção: pergunta com opções, a recomendada primeiro, cada uma com porquê e consequência |
| `/escrever-plano.md` | Escrever um plano novo no padrão dos planos existentes |

Skills próprias do projeto (`.cline/skills/`, carregadas pela descrição ou por `/nome`): `convencoes-painel-squads` e `novo-card-painel`.

## Windows 11: preparar o PC do ClineSR

### Caminhos

- Nos comandos do Windows use barra invertida (`app\backend`, `.clinerules\hooks\test-PreToolUse.ps1`); os documentos do projeto escrevem caminhos com `/`, que valem igual.
- `~` nos documentos = `%USERPROFILE%` no Windows (por exemplo `C:\Users\<você>`); no PowerShell, `$env:USERPROFILE`. Exemplo: `~/.cline/skills/` = `%USERPROFILE%\.cline\skills\`.
- **Clone o projeto numa pasta sem "jira" no nome** (por exemplo `C:\dev\painel-squads`): o hook bloqueia URLs e comandos de rede que citam "jira" e bloquearia até arquivos locais da pasta.

### Programas

| Programa | Para quê | Observação |
|---|---|---|
| Git for Windows | clone, commits, `git` no terminal; traz o Git Bash | Antes de clonar, nada a configurar: o `.gitattributes` do projeto fixa LF nos `.sh`, no hook sem extensão e nas regras/workflows/skills (`.md` lidos pelas IAs) e CRLF nos `.ps1`. Se um `.sh` aparecer com CRLF, rode `git add --renormalize .` só depois de perguntar ao usuário |
| JDK 21 (por exemplo Eclipse Temurin 21) | backend Kotlin (`jvmToolchain(21)` no `build.gradle.kts`) | `JAVA_HOME` apontando para o JDK |
| Node.js 22 e pnpm | frontend | `corepack enable` e depois `pnpm --version`, ou instalar o pnpm à parte |
| JDK 21 e Node 22 com pnpm | Backend (Gradle) e frontend (Vite) | Sem Docker nem banco a instalar: o banco é um arquivo SQLite |
| PowerShell 7 (opcional, recomendado) | o Cline usa o `pwsh` para o hook quando existe | Sem ele, o Cline usa o Windows PowerShell 5.1, que já vem no Windows |
| VS Code + ClineSR | executor | Ligar **Enable Hooks** (Settings → Features) |

### Comandos equivalentes (PowerShell)

| Tarefa | macOS/Linux (documentos do projeto) | Windows 11 (PowerShell) |
|---|---|---|
| Ambiente ok? | `java -version` | `java -version` (Temurin/OpenJDK 21) |
| Testes do backend | `cd app/backend && ./gradlew test` | `cd app\backend; .\gradlew.bat test` |
| Testes do frontend | `cd app/frontend && pnpm vitest run && pnpm build` | `cd app\frontend; pnpm vitest run; if ($?) { pnpm build }` |
| Subir tudo local | `cd app && sh iniciar.sh` | `cd app; .\iniciar.ps1` (variáveis em `app/.env`; a flag do Jira só vale ali) |
| Teste do hook | `sh .clinerules/hooks/test-PreToolUse.sh` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1` e o mesmo com `pwsh` |
| Instalar skills de terceiros | (ver [skills.md](skills.md)) | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\instalar-skills-cline.ps1` |
| Flag do Jira | variável de ambiente | Só o usuário define (variáveis de ambiente do usuário no Windows) e reabre o VS Code; a IA nunca usa `setx`/`$env:` para ela |

No PowerShell 5.1 não existe `&&`: use `;` e confira `$?` (ou `$LASTEXITCODE` para programas) antes do passo seguinte. O `gradlew.bat` e o `pnpm` devolvem código diferente de 0 quando um teste falha.

### Modelo Gauss 3.7: trabalhar dentro dos limites

O limite de contexto e de raciocínio do Gauss 3.7 não é conhecido aqui; trate-o como menor que o do Claude. Por isso:

- **Tarefas pequenas:** uma tarefa do plano por vez, um commit por tarefa; se a tarefa for grande, pergunte ao usuário se pode dividi-la.
- **Releia o plano a cada tarefa** (a seção da tarefa e o ledger de progresso), em vez de confiar no que leu antes.
- **Não resuma as regras de memória:** quando precisar delas, releia `.clinerules/00-regras-do-projeto.md` e o trecho do `CLAUDE.md`; nunca reescreva as proibições com palavras suas.
- **Use os workflows** (`/continuar.md`, `/executar-plano.md`, `/revisar-tarefa.md`, `/fim-de-branch.md`, `/perguntar-ao-usuario.md`): eles carregam o passo a passo para você.
- Com o contexto cheio, use `/newtask` (leva o essencial para uma tarefa nova) depois de atualizar o `HANDOFF.md` e o progresso; carregue skills de terceiros só quando acrescentarem algo.
- Na dúvida, pare e pergunte ao usuário com opções; nunca adivinhe.

## Claude Code → Cline

| Recurso | Claude Code (neste projeto) | Cline | Situação |
|---|---|---|---|
| Regras sempre ativas | `CLAUDE.md` (projeto) + regras globais do usuário (fora do repositório) | `.clinerules/*.md` na raiz (lidos automaticamente; o Cline também lê `AGENTS.md`, `.cursorrules`, `.windsurfrules`). `CLAUDE.md` não aparece na lista de compatibilidade da documentação, por isso `.clinerules/00` manda lê-lo | Porte feito: `.clinerules/00-regras-do-projeto.md` + [regras.md](regras.md) |
| Regras condicionais | não usadas | frontmatter `paths:` com globs | Não usado |
| Comando `/continuar` | `.claude/commands/continuar.md` | Workflow `.clinerules/workflows/continuar.md` (`/continuar.md`) | Porte feito |
| Skills de fluxo (superpowers etc.) | plugins em `~/.claude/plugins/` e `~/.claude/skills/` | Skills em `.cline/skills/` ou `%USERPROFILE%\.cline\skills\` (mesmo formato `SKILL.md`); o Cline **não** lê `~/.claude/` | Fluxos do projeto portados como workflows; terceiros instalados todos pelo script, com precedência do projeto ([skills.md](skills.md)) |
| Skills próprias | recomendadas e não criadas (`HANDOFF.md`) | `.cline/skills/convencoes-painel-squads`, `.cline/skills/novo-card-painel` | Criadas |
| Hook trava Jira/arquivos | `.claude/hooks/guard.sh` via `PreToolUse` | macOS/Linux: `.clinerules/hooks/PreToolUse` (adaptador que chama o `guard.sh`); Windows: `.clinerules/hooks/PreToolUse.ps1` (regras reescritas em PowerShell); precisa ligar "Enable Hooks" | Porte feito; o `.ps1` **não foi executado no Windows** (só PowerShell 7 no macOS) ([hooks.md](hooks.md)) |
| Hook Docker (obsoleto) | `ensure-docker.sh` virou no-op | Nenhum passo | Nada a fazer |
| Subagentes (Haiku) e revisores | `CLAUDE_CODE_SUBAGENT_MODEL=haiku`; agentes do `pr-review-toolkit` | Documentação: subagentes só na CLI/Desktop; na extensão VS Code, não confirmado | Você executa e revisa em sequência (`/revisar-tarefa.md`) |
| Advisor (Opus) | `advisorModel: opus`, modelo principal `sonnet` | "Use different models for Plan and Act" (modelo forte no Plan) | Equivalente parcial |
| Modos | não há modo de planejar separado | Plan (lê e discute, não edita) e Act (executa); `/deep-planning` | Use Plan para ler o plano e Act para executar |
| Memória entre sessões | memória automática do Claude (fora do repositório) + plugin claude-mem | Nenhuma nativa; "Memory Bank" é só metodologia | `HANDOFF.md` + `docs/superpowers/progresso/*.md` + `/continuar.md` |
| Perguntas com opções | ferramenta de perguntas do Claude | `ask_followup_question` com `options` | `/perguntar-ao-usuario.md` |
| Permissões | hooks bloqueiam; permissões por ferramenta | Auto-approve por categoria (ler, editar, comandos seguros, todos os comandos, navegador, MCP) e YOLO; **sem lista de comandos permitidos/proibidos** | Recomendado: auto-aprovar só leitura; **nunca YOLO** neste projeto |
| MCP | Atlassian (proibido), context7 e outros | Servidores MCP configuráveis | **Não** configurar o MCP da Atlassian; context7 só se o usuário autorizar |
| Retomada de contexto | `/continuar` | `/continuar.md`; `/newtask` e `/smol` para contexto longo | — |

## Fontes consultadas (25/10/2026)

- Documentação: https://docs.cline.bot/customization/cline-rules.md, https://docs.cline.bot/customization/skills.md, https://docs.cline.bot/customization/hooks.md, https://docs.cline.bot/customization/plugins.md, https://docs.cline.bot/sdk/plugins.md, https://docs.cline.bot/core-workflows/using-commands.md, https://docs.cline.bot/core-workflows/plan-and-act.md, https://docs.cline.bot/features/auto-approve.md, https://docs.cline.bot/features/subagents.md, https://docs.cline.bot/best-practices/memory-bank.md. As páginas não mostram número de versão.
- Código: github.com/cline/cline, branch `main` (extensão VS Code `apps/vscode/package.json` versão 4.1.25; releases recentes `cli-v3.0.73` e `sdk/v0.0.95` de 17/10/2026): `apps/vscode/src/core/storage/disk.ts` (pastas `.clinerules/workflows`, `.clinerules/hooks`, `.clinerules/skills`, `.cline/skills`, `.claude/skills`), `skill-directories.ts`, `core/hooks/utils.ts` e `templates.ts`, `core/context/instructions/user-instructions/cline-rules.ts` (exclui `workflows`, `hooks` e `skills` das regras), `shared/tools.ts` (nomes das ferramentas), `sdk/examples/hooks/`.
- Workflows: a página de workflows da documentação atual não foi encontrada (404); pasta e invocação (`.clinerules/workflows/`, `/nome.md`) confirmadas no código e no espelho da documentação antiga (cline-efdc8260.mintlify.app/features/slash-commands/workflows). Global: `~/Documents/Cline/Workflows`.

## Não confirmado

- Se o ClineSR tem hooks, skills e workflows como o Cline público, e em que versão (só se sabe que é uma variante Samsung do Cline, com o Gauss 3.7, no Windows 11).
- O `PreToolUse.ps1` e o `test-PreToolUse.ps1` **não foram executados no Windows** nem no Windows PowerShell 5.1: rodaram só com o PowerShell 7.5 no macOS (176 casos, 0 falhas). O ClineSR roda o teste primeiro (ver [hooks.md](hooks.md)).
- O `scripts/instalar-skills-cline.ps1` rodou só com o PowerShell 7.5 no macOS (43 skills); no Windows, não.
- Subagentes na extensão VS Code (a documentação diz que não; o código tem `use_subagents`).
- Se skills precisam de chave experimental na versão do ClineSR.
- Se o programa auxiliar do impeccable funciona no ClineSR (ele baixa um binário na primeira execução).
- Uso do hook com a CLI do Cline (`.cline/hooks/PreToolUse.sh`): não testado.

## Decisões do usuário (25/10)

- Executor: ClineSR no Windows 11, com o Gauss 3.7.
- `web_fetch` e navegador: **bloquear qualquer URL com "jira" ou "atlassian"** enquanto a flag não for `true`, aceitando que isso bloqueia também a documentação pública do Jira; nesses períodos, a pesquisa é feita pelo usuário (colando trechos) ou pela busca genérica (`web_search`). Com a flag `true`, nada disso é bloqueado.
- Skills de terceiros: **instalar todas** (superpowers, mattpocock-skills, impeccable) no PC do ClineSR, com a precedência do projeto ([skills.md](skills.md)).
