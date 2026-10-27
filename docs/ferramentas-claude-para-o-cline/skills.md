# Skills, comandos e agentes do Claude Code: origem e porte para o Cline (ClineSR no Windows 11)

Inventário das ferramentas que o Claude Code tinha neste projeto e o que fazer com cada uma no Cline. Os resumos são em palavras próprias; o texto original das skills de terceiros **não** foi copiado para o repositório (o script de instalação baixa cada uma do repositório de origem, respeitando a licença).

**Decisão do usuário (25/10):** instalar **todas** as skills de terceiros (superpowers, mattpocock-skills e impeccable) no PC do ClineSR. O executor é o ClineSR (variante Samsung do Cline, com o modelo Gauss 3.7) no Windows 11; se o ClineSR lê skills como o Cline público é **não confirmado** (confira na aba Skills depois de instalar).

Legenda: **[instalável como skill]** o Cline lê o `SKILL.md` como está (com ressalvas) · **[portado como workflow]** reescrito em `.clinerules/workflows/` · **[equivalente nativo do Cline]** · **[sem equivalente]**.

## Precedência: quem vence quando as instruções conflitam

**Regra do projeto (comportamento):** `CLAUDE.md` > `.clinerules/` do projeto (começando por `00-regras-do-projeto.md`) > workflows do projeto (`.clinerules/workflows/`) e skills do projeto (`.cline/skills/`) > skills de terceiros. **Em conflito, vale o projeto.** Uma skill de terceiro nunca autoriza: acessar o Jira com a flag diferente de `true`, mesclar PR, apagar branch, publicar algo, abrir issue, escrever em `CLAUDE.md`/`AGENTS.md`/`.clinerules/`, baixar ou executar programas, ou pular a pergunta com opções ao usuário.

**Regra do Cline (mecânica, só para nomes iguais):** se duas skills têm o **mesmo nome**, o Cline usa a de nível mais alto: remota (configuração corporativa) > global (`%USERPROFILE%\.cline\skills\`, `%USERPROFILE%\.agents\skills\`) > projeto (código: `apps/vscode/src/core/context/instructions/user-instructions/skills.ts`). Isso não muda a regra de comportamento acima; só decide qual arquivo é carregado. Nenhuma skill de terceiro tem o nome das skills do projeto (`convencoes-painel-squads`, `novo-card-painel`); se um dia tiver, renomeie a do terceiro na instalação.

## Como o Cline lê skills (verificado em 25/10/2026)

- Formato: uma pasta por skill com `SKILL.md`; frontmatter com `name` **igual ao nome da pasta** (senão a skill é ignorada) e `description`. Ativação automática pela descrição (ferramenta `use_skill`) ou manual digitando `/nome-da-skill`. Fonte: https://docs.cline.bot/customization/skills.md e o código acima.
- O Cline procura skills **só um nível abaixo** de cada diretório (`<diretório>\<skill>\SKILL.md`). Por isso não basta clonar um repositório inteiro para dentro de `skills\`: é preciso copiar cada pasta de skill (o script faz isso).
- Diretórios lidos (`apps/vscode/src/core/storage/skill-directories.ts`):
  - projeto: `.clinerules\skills\`, `.cline\skills\`, `.claude\skills\`, `.agents\skills\`;
  - global: `%USERPROFILE%\.cline\skills\` e `%USERPROFILE%\.agents\skills\` (no código, `~` = `os.homedir()`, que no Windows é `%USERPROFILE%`, por exemplo `C:\Users\<você>`).
- O Cline **não** lê `~/.claude/skills/` nem os plugins do Claude: as skills de terceiros precisam ser instaladas no PC do ClineSR.
- Chave por skill na aba Skills (habilitar/desabilitar). Se a versão do ClineSR exige ligar um recurso experimental: **não confirmado**.

## Instalar as skills de terceiros no Windows 11

### Pelo script (recomendado)

Pré-requisito: Git for Windows no `PATH`. Na raiz do projeto, num terminal PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\instalar-skills-cline.ps1
```

- O que faz: para cada repositório público, `git clone --depth 1 --filter=blob:none --sparse` (só as pastas de skills) em `%USERPROFILE%\.cline\skill-repos\<nome>`, ou `git pull --ff-only` se já existir; depois copia cada pasta de skill para `%USERPROFILE%\.cline\skills\<skill>`. **Não executa nada dos repositórios** (nem `npm`/`npx`, nem instaladores, nem scripts das skills).
- Idempotente: rodar de novo atualiza. Cada skill instalada recebe o arquivo `.origem-instalar-skills-cline` (repositório e commit); uma pasta de mesmo nome sem esse arquivo (instalada à mão) não é tocada.
- Opções: `-Destino .cline\skills` instala só neste projeto (**não versione**: são de terceiros); `-Cache <pasta>`; `-Somente superpowers,impeccable`.
- Resultado esperado (conferido em 25/10/2026, rodando o script com PowerShell 7.5 no macOS): **43 skills** (15 superpowers, 27 mattpocock, 1 impeccable), nenhuma com `name` diferente da pasta. **No Windows: não executado.**
- Depois: reabra o VS Code e confira a aba Skills do Cline/ClineSR.

### Passo a passo manual (se o script não puder ser usado)

| Repositório | Origem pública (licença) | Pasta a copiar | Destino |
|---|---|---|---|
| superpowers | `https://github.com/obra/superpowers` (MIT) | cada subpasta de `skills\` (15) | `%USERPROFILE%\.cline\skills\<subpasta>` |
| mattpocock-skills | `https://github.com/mattpocock/skills` (MIT) | as 27 pastas listadas em `.claude-plugin\plugin.json` (campo `skills`), em `skills\engineering\` e `skills\productivity\`; ficam de fora `deprecated`, `in-progress` e `misc`, que não fazem parte do plugin que o Claude usava | `%USERPROFILE%\.cline\skills\<pasta>` |
| impeccable | `https://github.com/pbakaus/impeccable` (Apache-2.0) | `.agents\skills\impeccable` (versão já montada para agentes, com `scripts\` e `reference\`) | `%USERPROFILE%\.cline\skills\impeccable` |

```powershell
git clone --depth 1 https://github.com/obra/superpowers.git $env:USERPROFILE\.cline\skill-repos\superpowers
Copy-Item -Recurse $env:USERPROFILE\.cline\skill-repos\superpowers\skills\* $env:USERPROFILE\.cline\skills\
```

(o mesmo para os outros dois, copiando só as pastas da tabela). O instalador oficial do impeccable (`npx impeccable install`) **não** cita o Cline entre os provedores (README do repositório, 25/10/2026) e executa código; não use sem o usuário pedir.

## Conflitos conhecidos: ferramentas do Claude citadas pelas skills

As skills de terceiros foram escritas para o Claude Code (e outros agentes) e citam ferramentas que o Cline não tem com o mesmo nome. Traduza assim:

| A skill cita (Claude Code) | No Cline/ClineSR | Como agir neste projeto |
|---|---|---|
| `Skill` (carregar outra skill) | `use_skill` ou digitar `/nome-da-skill` | Carregue só quando a tarefa pedir; não carregue skills "por garantia" (gasta contexto) |
| `TodoWrite` (lista de tarefas) | sem ferramenta própria; lista de passos no plano ou no arquivo de progresso | Use `docs/superpowers/progresso/<plano>.md` (regra 5.4) |
| `Task` / `Agent` / subagentes / "dispatch" | `new_task` (nova tarefa com contexto destilado) ou `use_subagents` (no código da extensão; disponibilidade na extensão **não confirmada**) | Faça você mesmo, em sequência: executor e depois revisor (`/executar-plano.md`, `/revisar-tarefa.md`) |
| `AskUserQuestion` | `ask_followup_question` com `options` | Sempre no formato de `/perguntar-ao-usuario.md` (2 a 4 opções, a recomendada primeiro) |
| `Bash` / `Read` / `Write` / `Edit` | `execute_command` / `read_file` / `write_to_file` / `replace_in_file` | No Windows, os comandos são PowerShell (ver `README.md`) |
| `WebFetch` / `WebSearch` | `web_fetch` / `web_search` | Com a flag diferente de `true`, nada com `jira`/`atlassian` no endereço (hook bloqueia) |
| Worktrees (`using-git-worktrees`) | o Cline trabalha na pasta aberta no VS Code | Uma branch por plano basta; worktree só se o usuário pedir |
| Modelos (`opus`, `haiku`, `sonnet`) | o modelo é o configurado no ClineSR (Gauss 3.7) | Ignore escolhas de modelo da skill |

### Conflitos específicos (o projeto vence)

| Skill | Conflito | O que fazer |
|---|---|---|
| superpowers `using-superpowers` | Manda carregar skills antes de qualquer resposta, inclusive perguntas | Não siga isso à risca: com o Gauss 3.7, carregar várias skills consome o contexto. Siga os workflows do projeto e carregue uma skill só quando ela acrescentar algo |
| superpowers `subagent-driven-development`, `dispatching-parallel-agents` | Dependem de subagentes | Use `/executar-plano.md` (sequencial) |
| superpowers `brainstorming`, `writing-plans` | Têm o próprio formato e local de spec/plano | Use `/escrever-plano.md` e o padrão de `docs/superpowers/` |
| superpowers `finishing-a-development-branch` | Oferece mesclar ou descartar a branch | Use `/fim-de-branch.md`: **mescle só pelo fluxo de entrega (7 condições) e nunca apague branch com código fora da `main`** |
| superpowers `using-git-worktrees` | Cria worktrees | Só com o usuário pedindo |
| mattpocock `setup-matt-pocock-skills` | Escreve uma seção em `AGENTS.md`/`CLAUDE.md`, `docs/agents/` e configura rastreador de issues | **Não rode** sem o usuário pedir: o `CLAUDE.md` é a fonte da verdade do projeto |
| mattpocock `to-tickets`, `triage`, `to-spec`, `pr` | Abrem issues ou PRs (`gh`) | Efeito fora do repositório: só com pergunta ao usuário |
| mattpocock `handoff` | Gera documento de passagem próprio | O projeto usa o `HANDOFF.md` e `/continuar.md` |
| mattpocock `grill-me`, `grilling` | Interrogatório longo | Forma das perguntas: `/perguntar-ao-usuario.md`; intensidade só a pedido |
| impeccable | Manda rodar `scripts\impeccable.cmd` (no Windows), que **baixa e executa um binário** na primeira vez (em `%USERPROFILE%\.impeccable\bin\`) e tem telemetria (desligável com `DO_NOT_TRACK=1` ou `IMPECCABLE_NO_TELEMETRY=1`, segundo o README); lê e pode escrever `PRODUCT.md`/`DESIGN.md` | Baixar e executar programa é efeito fora do repositório: **pergunte ao usuário antes** da primeira execução. O `PRODUCT.md` do projeto não é reescrito sem pedido. Se o programa funciona no ClineSR: **não confirmado** |

## Skills próprias do projeto (versionadas)

| Skill | Pasta | Para que serve |
|---|---|---|
| `convencoes-painel-squads` | `.cline/skills/convencoes-painel-squads/` | Proibições e convenções do projeto em uma página; aponta para os workflows |
| `novo-card-painel` | `.cline/skills/novo-card-painel/` | Padrão para criar um card novo copiando o Tempo em status |

Ficam em `.cline/skills/` (e não em `.claude/skills/`) para não mudar o comportamento do Claude Code no mesmo repositório.

## Comando do projeto

| Claude Code | Porte | Marca |
|---|---|---|
| `.claude/commands/continuar.md` (`/continuar`) | `.clinerules/workflows/continuar.md` (`/continuar.md`) | [portado como workflow] |

## superpowers (Jesse Vincent)

Origem: https://github.com/obra/superpowers · Licença: MIT. Uso aqui: os planos dizem "USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans" no cabeçalho; no Cline, leia isso como `/executar-plano.md`.

| Skill | Para que serve (resumo próprio) | Porte no projeto |
|---|---|---|
| brainstorming | Explorar a ideia com o usuário antes de criar | Seção 1 de `escrever-plano.md` |
| writing-plans | Spec → plano com tarefas pequenas e testes nomeados | `escrever-plano.md` |
| subagent-driven-development | Um subagente por tarefa, revisão entre tarefas | `executar-plano.md` (sequencial) |
| executing-plans | Executar o plano na própria sessão, com pontos de verificação | `executar-plano.md` |
| test-driven-development | Vermelho, verde, refatorar | `executar-plano.md` + `revisar-tarefa.md` |
| verification-before-completion | Só afirmar "passa" depois de rodar e ler a saída | `revisar-tarefa.md`; `fim-de-branch.md` |
| requesting-code-review / receiving-code-review | Pedir e avaliar revisão | `revisar-tarefa.md` |
| finishing-a-development-branch | Fechar a branch | `fim-de-branch.md` (sem mesclar) |
| systematic-debugging | Causa raiz antes de corrigir | "Quando algo falha" em `executar-plano.md` |
| using-git-worktrees, dispatching-parallel-agents, writing-skills, using-superpowers, diagnosing-superpowers | Worktrees, paralelismo, escrever skills, uso das skills, diagnóstico do próprio pacote | Sem porte; ver conflitos acima |

## mattpocock-skills (Matt Pocock)

Origem: https://github.com/mattpocock/skills · Licença: MIT · 27 skills no plugin (`.claude-plugin/plugin.json`). Uso aqui: sem registro nos documentos do projeto. Mais úteis aqui: `tdd`, `diagnosing-bugs`, `code-review`, `codebase-design`, `domain-modeling`, `grilling`. As demais (`ask-matt`, `grill-with-docs`, `triage`, `improve-codebase-architecture`, `setup-matt-pocock-skills`, `to-spec`, `to-tickets`, `wayfinder`, `implement`, `implement-spec`, `prototype`, `research`, `pr`, `retro`, `wizard`, `grill-me`, `handoff`, `teach`, `to-questionnaire`, `wait-what`, `writing-for-agents`) ficam instaladas, mas só valem quando não conflitam com o projeto.

## impeccable (Paul Bakaus)

Origem: https://github.com/pbakaus/impeccable · Licença: Apache-2.0 · versão no repositório em 25/10/2026: 4.7.1. Para que serve: design e revisão de interfaces (hierarquia, tipografia, cor, acessibilidade, temas). Uso aqui: plano visual e protótipos (`docs/design/`). Ver o conflito na tabela acima antes de rodar o programa auxiliar.

## Plugins oficiais da Anthropic e outros (sem instalação no Cline)

| Plugin / item | Porte |
|---|---|
| code-review, pr-review-toolkit (revisores) | `revisar-tarefa.md` (lentes extras) |
| commit-commands (`commit`, `commit-push-pr`, `clean_gone`) | `fim-de-branch.md` |
| claude-code-setup | Recomendações viraram as skills do projeto |
| feature-dev, security-guidance, claude-md-management, skill-creator, kotlin-lsp | Sem equivalente |
| context7 | Servidor MCP; configurar no Cline só se o usuário autorizar |
| atlassian (MCP) | **Não instalar**: proibido enquanto a flag não for `true` |
| claude-mem (memória entre sessões) | Sem equivalente; continuidade por `HANDOFF.md`, `docs/superpowers/progresso/` e `/continuar.md`. Não adote o "Memory Bank" do Cline sem perguntar |

## Agentes e subagentes

| Claude Code | Cline |
|---|---|
| Subagentes Haiku para tarefas mecânicas | A documentação diz que subagentes existem só na CLI e no Desktop (https://docs.cline.bot/features/subagents.md); o código da extensão tem `use_subagents`. No ClineSR: **não confirmado**. Os workflows assumem que você faz tudo em sequência |
| Revisores (`pr-review-toolkit:*`, revisão final com Opus) | `revisar-tarefa.md`; para uma segunda opinião, pergunte ao usuário (pergunta com opções) |
