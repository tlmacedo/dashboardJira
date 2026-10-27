# Skills instaladas no Claude (inventário de 27/10/2026)

Inventário das skills que o Claude Code (esta conta do Claude e o ambiente em nuvem do projeto) tem instaladas, com a origem, a licença e se uma cópia foi preparada para o Cline/ClineSR em [`skills-para-cline/`](skills-para-cline/README.md). Gerado a partir dos arquivos `SKILL.md` encontrados em `~/.claude/plugins/synced` e `~/.claude/skills/synced` no ambiente em nuvem, mais as skills embutidas e as do projeto. **Não é a lista do PC da empresa**: lá o Claude Code pode ter outro conjunto.

Legenda da coluna **Cline**: **sim** = copiada para `skills-para-cline/`; **não** = não copiada, com o motivo. Licença com `*` = o plugin traz arquivo `LICENSE` mas o tipo não foi conferido linha a linha.

**Resumo:** 116 skills de plugins/contas encontradas em disco; 45 copiadas para o Cline; 71 não copiadas (licença proprietária ou não declarada, dependência de ferramentas do Claude, ou irrelevante ao projeto).

## 1. Skills de plugins e da conta (em disco)

### adb-mcp (licença: MIT; 6 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `build-and-test` | não: só Android/adb; irrelevante para este projeto | Mapping a multi-module Gradle project before guessing a task name, building a variant, running JVM vs on-device tests, and turning a JaCoCo coverage report into the spec… |
| `crash-triage` | não: só Android/adb; irrelevante para este projeto | Using logcat to surface the real "Caused by:" root cause hidden behind a generic UI error, plus app lifecycle tools for reproducing failures. Use when an app crashes, sh… |
| `driving` | não: só Android/adb; irrelevante para este projeto | The core observe→locate→act→re-observe loop for driving an Android UI with adb-mcp — true-pixel coordinates, and gotchas like overlays eating taps, keyboards covering bu… |
| `getting-started` | não: só Android/adb; irrelevante para este projeto | How to list AVDs, boot an emulator, target a device with the serial argument, and run a first interaction with adb-mcp. Use when starting a new Android automation task, … |
| `pin-and-lock` | não: só Android/adb; irrelevante para este projeto | Entering digits on native (non-IME) PIN pads, and setting/clearing a secure lock screen required by AndroidKeyStore / Keystore-backed crypto flows. Use when a task needs… |
| `rn-expo` | não: só Android/adb; irrelevante para este projeto | The RN/Expo dev-build recipe for adb-mcp — adb_reverse before launching (or the dev client silently runs its embedded bundle), app_state to confirm it is on Metro, on th… |

### anthropic-skills (licença: proprietária (Anthropic); 15 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `built-in-browser` | não: depende do navegador embutido do Claude | Read this skill before the first step that uses the built-in browser, the browser pane inside the Claude desktop app (also called the in-app browser, the browser pane, C… |
| `chrome-browser` | não: depende da extensão do Chrome do Claude | Read this skill before the first step that uses Claude in Chrome, the browser extension whose tools are named mcp__claude-in-chrome__* (also called Chrome, the browser e… |
| `computer-use` | não: controle de computador do Claude; não se aplica ao Cline | Read this skill before the first step of any request to do something in an app on the person's own computer (Notes, Finder, System Settings, any desktop app), to look at… |
| `deep-research` | não: depende de subagentes do Claude | Use this skill when the user's prompt requires (1) researching a topic across multiple sources, comparing options or alternatives, analyzing trends or history, understan… |
| `docs` | não: depende do conector Claude Docs | docs (editable docs people share and comment on; the default for any document, named as a doc or not: a document, report, proposal, resume, cover letter, letter, contrac… |
| `docx` | não: licença proprietária proíbe copiar/redistribuir | Use this skill whenever the user wants to create, read, edit, or manipulate Word documents (.docx) or Word templates (.dotx). Triggers include: any mention of Microsoft … |
| `google-workspace` | não: depende dos conectores Google | Read this before the first Google Drive, Docs, Sheets or Slides connector call whenever the task creates or changes a Google file. Use this skill whenever the user wants… |
| `import-memory` | não: memória do Claude | Import a memory export from another AI assistant into Claude's memory — conversationally, additively, and with the content treated as data. |
| `mcp-builder` | sim | Guide for creating high-quality MCP (Model Context Protocol) servers that enable LLMs to interact with external services through well-designed tools. Use when building M… |
| `morning` | não: rotina do Claude Cowork | Render the user's morning brief as a styled HTML artifact, or set it up as a recurring weekday task. Use only when the user explicitly asks to run, see, or set up their … |
| `pdf` | não: licença proprietária proíbe copiar/redistribuir | Use this skill whenever the user wants to do anything with PDF files. This includes reading or extracting text/tables from PDFs, combining or merging multiple PDFs into … |
| `pptx` | não: licença proprietária proíbe copiar/redistribuir | Use this skill any time a .pptx or .potx file is involved in any way — as input, output, or both. This includes: creating slide decks, pitch decks, or presentations as P… |
| `skill-creator` | sim | Create new skills, modify and improve existing skills, and measure skill performance. Use when users want to create a skill from scratch, edit, or optimize an existing s… |
| `theme-factory` | sim | Toolkit for styling artifacts with a theme. These artifacts can be slides, docs, reportings, HTML landing pages, etc. There are 10 pre-set themes with colors/fonts that … |
| `xlsx` | não: licença proprietária proíbe copiar/redistribuir | Use this skill any time a spreadsheet file is the primary input or output. This means any task where the user wants to: open, read, edit, or fix an existing .xlsx, .xlsm… |

### cartographer (licença: MIT; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `cartographer` | sim | Map, audit, and review large multi-segment codebases (hundreds of files, multiple integrations like Supabase, Telegram, Vercel, AI APIs, file systems, queues, etc.) by b… |

### claude-android-qa-plugin (licença: Apache-2.0; 2 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `android-emulator-qa` | não: só Android; irrelevante para este projeto | Drive Android emulator QA with adb, UI tree inspection, screenshots, and logcat evidence |
| `android-emulator-qa-guidance` | não: só Android; irrelevante para este projeto | This skill should be used when validating Android feature flows in an emulator, reproducing Android UI bugs, driving adb input, inspecting uiautomator XML, capturing scr… |

### claude-code-setup (licença: Apache-2.0*; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `claude-automation-recommender` | não: específica do Claude Code | Analyze a codebase and recommend Claude Code automations (hooks, subagents, skills, plugins, MCP servers). Use when user asks for automation recommendations, wants to op… |

### claude-md-management (licença: Apache-2.0*; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `claude-md-improver` | não: específica do Claude Code (CLAUDE.md) | Audit and improve CLAUDE.md files in repositories. Use when user asks to check, audit, update, improve, or fix CLAUDE.md files. Scans for all CLAUDE.md files, evaluates … |

### cowork-plugin-management (licença: Apache-2.0*; 2 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `cowork-plugin-customizer` | não: específica do Claude Cowork | Customize a Claude Code plugin for a specific organization's tools and workflows. Use when: customize plugin, set up plugin, configure plugin, tailor plugin, adjust plug… |
| `create-cowork-plugin` | não: específica do Claude Cowork | Guide users through creating a new plugin from scratch in a cowork session. Use when users want to create a plugin, build a plugin, make a new plugin, develop a plugin, … |

### design (licença: não declarada; 7 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `accessibility-review` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Run a WCAG 2.1 AA accessibility audit on a design or page. Trigger with "audit accessibility", "check a11y", "is this accessible?", or when reviewing a design for color … |
| `design-critique` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Get structured design feedback on usability, hierarchy, and consistency. Trigger with "review this design", "critique this mockup", "what do you think of this screen?", … |
| `design-handoff` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Generate developer handoff specs from a design. Use when a design is ready for engineering and needs a spec sheet covering layout, design tokens, component props, intera… |
| `design-system` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Audit, document, or extend your design system. Use when checking for naming inconsistencies or hardcoded values across components, writing documentation for a component'… |
| `research-synthesis` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Synthesize user research into themes, insights, and recommendations. Use when you have interview transcripts, survey results, usability test notes, support tickets, or N… |
| `user-research` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Plan, conduct, and synthesize user research. Trigger with "user research plan", "interview guide", "usability test", "survey design", "research questions", or when the u… |
| `ux-copy` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Write or review UX copy — microcopy, error messages, empty states, CTAs. Trigger with "write copy for", "what should this button say?", "review this error message", or w… |

### engineering (licença: não declarada; 10 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `architecture` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Create or evaluate an architecture decision record (ADR). Use when choosing between technologies (e.g., Kafka vs SQS), documenting a design decision with trade-offs and … |
| `code-review` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Review code changes for security, performance, and correctness. Trigger with a PR URL or diff, "review this before I merge", "is this code safe?", or when checking a cha… |
| `debug` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Structured debugging session — reproduce, isolate, diagnose, and fix. Trigger with an error message or stack trace, "this works in staging but not prod", "something brok… |
| `deploy-checklist` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Pre-deployment verification checklist. Use when about to ship a release, deploying a change with database migrations or feature flags, verifying CI status and approvals … |
| `documentation` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Write and maintain technical documentation. Trigger with "write docs for", "document this", "create a README", "write a runbook", "onboarding guide", or when the user ne… |
| `incident-response` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Run an incident response workflow — triage, communicate, and write postmortem. Trigger with "we have an incident", "production is down", an alert that needs severity ass… |
| `standup` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Generate a standup update from recent activity. Use when preparing for daily standup, summarizing yesterday's commits and PRs and ticket moves, formatting work into yest… |
| `system-design` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Design systems, services, and architectures. Trigger with "design a system for", "how should we architect", "system design for", "what's the right architecture for", or … |
| `tech-debt` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Identify, categorize, and prioritize technical debt. Trigger with "tech debt", "technical debt audit", "what should we refactor", "code health", or when the user asks ab… |
| `testing-strategy` | não: plugin da Anthropic sem arquivo de licença: não redistribuir | Design test strategies and test plans. Trigger with "how should we test", "test strategy for", "write tests for", "test plan", "what tests do we need", or when the user … |

### figma (licença: proprietária (Figma); 18 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `figma-code-connect` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Creates and maintains Figma Code Connect template files that map Figma components to code snippets. Use when the user mentions Code Connect, Figma component mapping, des… |
| `figma-code-workflows` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Import or upload local HTML or project files into Figma, choose editable Design frames, runnable Code Layers or standalone Code (also known as Make), and repair imported… |
| `figma-create-new-file` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Always use this skill when the user wants to create a new Figma Design, FigJam, or Slides file. You MUST invoke this skill BEFORE every `create_new_file` tool call. |
| `figma-design-to-code` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | **MANDATORY prerequisite** — you MUST invoke this skill BEFORE calling the `get_design_context` Figma MCP tool. You MUST trigger this skill whenever the user wants to im… |
| `figma-generate-design` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Use this skill alongside figma-use when the task involves translating an application page, view, or multi-section layout into Figma. Triggers: 'write to Figma', 'create … |
| `figma-generate-diagram` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | MANDATORY prerequisite — load this skill BEFORE every `generate_diagram` tool call. NEVER call `generate_diagram` directly without loading this skill first. Trigger when… |
| `figma-generate-library` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Build or update a professional-grade design system in Figma from a codebase. Use when the user wants to create variables/tokens, build component libraries, create indivi… |
| `figma-generative-plugins` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | **MANDATORY prerequisite** — load this skill before calling `create_generative_plugin` or `update_generative_plugin`. Use when the user asks to create, author, change, f… |
| `figma-implement-motion` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Translates Figma motion and animations into production-ready application code. Use when implementing animation/motion from a Figma design — user mentions "implement this… |
| `figma-shaders` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | **MANDATORY prerequisite** — load this skill before calling `create_shader` or `update_shader`. Use when the user asks to create, author, change, fix, or iterate on a sh… |
| `figma-spec` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | This skill is required to create, read, edit, review, or comment on Figma specs. You must read it when working on a PRD, brief, RFC or any kind of document in either a s… |
| `figma-swiftui` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | SwiftUI ↔ Figma translation. Use whenever the user mentions Swift, SwiftUI, iOS, iPhone, or iPad — in EITHER direction — translating a Figma design into SwiftUI (design … |
| `figma-use` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | **MANDATORY prerequisite** — you MUST invoke this skill BEFORE every `use_figma` tool call. NEVER call `use_figma` directly without loading this skill first. Skipping it… |
| `figma-use-figjam` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | This skill helps agents use Figma's use_figma MCP tool in the FigJam context. Can be used alongside figma-use which has foundational context for using the use_figma tool. |
| `figma-use-motion` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Motion / animation context for the `use_figma` MCP tool — animating Figma nodes via manual keyframes, animation styles, easing, and timeline duration. Load alongside fig… |
| `figma-use-slides` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | This skill helps agents use Figma's use_figma MCP tool in the Slides context. Can be used alongside figma-use which has foundational context for using the use_figma tool. |
| `generate-project-plan` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | Generate a FigJam project plan board from a PRD plus codebase context. Interactive flow: research → propose sections → per-section deep research → per-section content + … |
| `video-interaction-mapper` | não: plugin do Figma: depende do MCP do Figma; não redistribuir | This skill should be used when the user asks to analyze a UI screen recording and map interaction states into Figma. Trigger for requests such as "put video frames in Fi… |

### headroom (licença: MIT; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `headroom` | sim | Use when the user wants Claude to work with a document, log or data file too large to read whole, such as long specs, big logs or large JSON or CSV files. |

### kotlin-lsp (licença: MIT; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `kotlin-lsp` | não: depende do kotlin-language-server e do sistema de plugins do Claude Code | Real-time Kotlin code intelligence via kotlin-language-server. Gives Claude instant error detection, go-to-definition, find-references, and type info for .kt and .kts fi… |

### mattpocock-skills (licença: MIT; 38 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `ask-matt` | sim | Ask which skill or flow fits your situation. A router over the skills in this repo. |
| `claude-handoff` | não: skill "em andamento" do autor (instável) | Hand the current conversation off to a fresh background agent that picks up the work immediately. |
| `code-review` | sim | Review the changes since a fixed point (commit, branch, tag, or merge-base) along two axes: Standards (does the code follow this repo's documented coding standards?) and… |
| `codebase-design` | sim | Shared vocabulary for designing deep modules. Use when the user wants to design or improve a module's interface, find deepening opportunities, decide where a seam goes, … |
| `diagnosing-bugs` | sim | Diagnosis loop for hard bugs and performance regressions. Use when the user says "diagnose"/"debug this", or reports something broken/throwing/failing/slow. |
| `domain-modeling` | sim | Build and sharpen a project's domain model. Use when discussing codebase terminology, writing or editing a CONTEXT.md, or recording or editing an ADR. |
| `git-guardrails-claude-code` | sim | Set up Claude Code hooks to block dangerous git commands (push, reset --hard, clean, branch -D, etc.) before they execute. Use when user wants to prevent destructive git… |
| `grill-me` | sim | A relentless interview to sharpen a plan or design. |
| `grill-with-docs` | sim | A relentless interview to sharpen a plan or design, which also creates docs (ADR's and glossary) as we go. |
| `grilling` | sim | Grill the user relentlessly about a plan, decision, or idea. Use when the user wants to stress-test their thinking, or uses any 'grill' trigger phrases. |
| `handoff` | sim | Compact the current conversation into a handoff document for another agent to pick up. |
| `implement` | sim | Implement a piece of work based on a spec or set of tickets. |
| `implement-spec` | não: skill "em andamento" do autor (instável) | Implement a specification in code. |
| `improve-codebase-architecture` | sim | Scan a codebase for deepening opportunities, present them as a visual HTML report, then grill through whichever one you pick. |
| `loop-me` | não: skill "em andamento" do autor (instável) | Grill me about specs for the workflows I want to build, within this workspace. |
| `migrate-to-shoehorn` | sim | Migrate test files from `as` type assertions to @total-typescript/shoehorn. Use when user mentions shoehorn, wants to replace `as` in tests, or needs partial test data. |
| `pr` | não: skill "em andamento" do autor (instável) | Use when writing a PR body. |
| `prototype` | sim | Build a throwaway prototype to answer a design question. Use when the user wants to sanity-check whether a state model or logic feels right, or explore what a UI should … |
| `research` | sim | Investigate a question against high-trust primary sources and capture the findings as a Markdown file in the repo. Use when the user wants a topic researched, docs or AP… |
| `resolving-merge-conflicts` | sim | Use when you need to resolve an in-progress git merge/rebase conflict. |
| `retro` | não: skill "em andamento" do autor (instável) | Conduct a retrospective on a coding session. |
| `scaffold-exercises` | sim | Create exercise directory structures with sections, problems, solutions, and explainers that pass linting. Use when user wants to scaffold exercises, create exercise stu… |
| `setup-matt-pocock-skills` | sim | Configure this repo for the engineering skills: set up its issue tracker, triage label vocabulary, and domain doc layout. Run once before first use of the other engineer… |
| `setup-pre-commit` | sim | Set up Husky pre-commit hooks with lint-staged (Prettier), type checking, and tests in the current repo. Use when user wants to add pre-commit hooks, set up Husky, confi… |
| `setup-ts-deep-modules` | não: skill "em andamento" do autor (instável) | Wire dependency-cruiser into a TypeScript repo so each package is a deep module, with implementation hidden in subfolders and reachable only through its entry-point file… |
| `tdd` | sim | Test-driven development. Use when the user wants to build features or fix bugs test-first, mentions "red-green-refactor", or wants integration tests. |
| `teach` | sim | Teach the user a new skill or concept, within this workspace. |
| `to-questionnaire` | sim | Turn a decision you can't fully answer into a questionnaire for someone else to fill in. |
| `to-spec` | sim | Turn the current conversation into a spec and publish it to the project issue tracker: no interview, just synthesis of what you've already discussed. |
| `to-tickets` | sim | Break a plan, spec, or the current conversation into a set of tracer-bullet tickets, each declaring its blocking edges, published to the configured tracker (edges as tex… |
| `triage` | sim | Move issues and external PRs through a state machine of triage roles, categorise, verify, grill if needed, and write agent-ready briefs. |
| `wait-what` | sim | Stop. That last message did not land: re-pitch it. |
| `wayfinder` | sim | Plan a huge chunk of work (more than one agent session can hold) as a shared map of decision tickets on your issue tracker, and resolve them one at a time until the way … |
| `wizard` | sim | Generate an interactive bash wizard that walks a human through steps only they can perform. Use when provisioning infrastructure, setting up credentials or CI secrets, w… |
| `writing-beats` | não: skill "em andamento" do autor (instável) | Writing, exploit; assemble raw material into a journey of beats, grounding each term before a beat leans on it. |
| `writing-for-agents` | sim | Writing documents for agents. Use when creating or editing skills, or modifying AGENTS.md or CLAUDE.md. |
| `writing-fragments` | não: skill "em andamento" do autor (instável) | Writing, explore: mine raw fragments, no structure yet. |
| `writing-shape` | não: skill "em andamento" do autor (instável) | Writing, exploit: shape raw material into an article, paragraph by paragraph. |

### omniout (licença: MIT; 1 skill)

| Skill | Cline | O que faz |
|---|---|---|
| `omniout` | sim | Use when building or fixing an app that calls LLM APIs and needs retries, timeouts, rate-limit handling or fallback to another model or provider. |

### product-management (licença: Apache-2.0; 8 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `competitive-brief` | sim | Create a competitive analysis brief for one or more competitors or a feature area. Use when informing product strategy or feature prioritization, building sales battle c… |
| `metrics-review` | sim | Review and analyze product metrics with trend analysis and actionable insights. Use when running a weekly, monthly, or quarterly metrics review, investigating a sudden s… |
| `product-brainstorming` | sim | Brainstorm product ideas, explore problem spaces, and challenge assumptions as a thinking partner. Use when exploring a new opportunity, generating solutions to a produc… |
| `roadmap-update` | sim | Update, create, or reprioritize your product roadmap. Use when adding a new initiative and deciding what moves to make room, shifting priorities after new information co… |
| `sprint-planning` | sim | Plan a sprint — scope work, estimate capacity, set goals, and draft a sprint plan. Use when kicking off a new sprint, sizing a backlog against team availability (account… |
| `stakeholder-update` | sim | Generate a stakeholder update tailored to audience and cadence. Use when writing a weekly or monthly status for leadership, announcing a launch, escalating a risk or blo… |
| `synthesize-research` | sim | Synthesize user research from interviews, surveys, and feedback into structured insights. Use when you have a pile of interview notes, survey responses, or support ticke… |
| `write-spec` | sim | Write a feature spec or PRD from a problem statement or feature idea. Use when turning a vague idea or user request into a structured document, scoping a feature with go… |

### productivity (licença: Apache-2.0; 4 skills)

| Skill | Cline | O que faz |
|---|---|---|
| `memory-management` | sim | Two-tier memory system that makes Claude a true workplace collaborator. Decodes shorthand, acronyms, nicknames, and internal language so Claude understands requests like… |
| `start` | não: depende do painel/TASKS.md do plugin no Claude Cowork | Initialize the productivity system and open the dashboard. Use when setting up the plugin for the first time, bootstrapping working memory from your existing task list, … |
| `task-management` | sim | Simple task management using a shared TASKS.md file. Reference this when the user asks about their tasks, wants to add/complete tasks, or needs help tracking commitments. |
| `update` | não: depende do painel/TASKS.md do plugin no Claude Cowork | Sync tasks and refresh memory from your current activity. Use when pulling new assignments from your project tracker into TASKS.md, triaging stale or overdue tasks, fill… |

## 2. Plugins sem `SKILL.md` próprio (comandos e agentes)

Estes plugins aparecem no Claude Code como comandos `/…` ou agentes, não como skills; não têm equivalente direto de pasta no Cline: `commit-commands` (commit, push e PR; `/commit`, `/commit-push-pr`, `/clean_gone`), `pr-review-toolkit` (revisores `code-reviewer`, `code-simplifier`, `comment-analyzer`, `pr-test-analyzer`, `silent-failure-hunter`, `type-design-analyzer` e `/review-pr`) e `security-guidance` (alerta de segurança ao editar arquivos). No Cline, o papel deles é coberto pelos workflows do projeto em `.clinerules/workflows/` (`/revisar-tarefa.md`, `/fim-de-branch.md`).

## 3. Skills embutidas do Claude Code (não são arquivos instalados)

Fazem parte do próprio Claude Code e não podem ser copiadas: `update-config`, `keybindings-help`, `code-review`, `simplify`, `fewer-permission-prompts`, `loop`, `claude-api`, `workflow-authoring`, `run`, `plugin-authoring`, `init`, `security-review`, `artifact-design`, `artifact-diagramming`, `artifact-capabilities`, `dataviz`. Para as de revisão, o projeto usa o equivalente `/revisar-tarefa.md`.

## 4. Skills e comandos do próprio projeto

| Item | Onde | Cline |
|---|---|---|
| `/continuar` (retoma o projeto) | `.claude/commands/continuar.md` | equivalente: `.clinerules/workflows/continuar.md` |
| `convencoes-painel-squads` | `.cline/skills/convencoes-painel-squads/` | já é skill do Cline (versionada) |
| `novo-card-painel` | `.cline/skills/novo-card-painel/` | já é skill do Cline (versionada) |
| Subagentes `implementador`, `revisor-de-tarefa`, `redator-de-planos`, `tarefa-mecanica` | `.claude/agents/` | equivalente: contextos separados descritos em `docs/ferramentas-claude-para-o-cline/processo-completo.md` |

Outras skills de terceiros que o projeto já manda instalar no Cline direto da fonte (`scripts/instalar-skills-cline.ps1`): `superpowers` (obra), `mattpocock-skills` e `impeccable` (pbakaus). Elas **não** estão no disco deste ambiente em nuvem, por isso não constam acima; as de `mattpocock-skills` aparecem na seção 1 porque o plugin também está instalado aqui.

## 5. Como usar no Cline

Veja [`skills-para-cline/README.md`](skills-para-cline/README.md): como copiar para a pasta de skills do Cline, como ativar o recurso, como confirmar que foi reconhecida e a lista das limitações esperadas (muitas skills citam ferramentas do Claude, como `Skill`, `Agent` e `AskUserQuestion`, que o Cline não tem).
