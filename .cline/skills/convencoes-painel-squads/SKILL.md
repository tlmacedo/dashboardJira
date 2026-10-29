---
name: convencoes-painel-squads
description: Use em QUALQUER tarefa neste repositório (Painel de Squads, Kotlin/Ktor + React) antes de escrever código, testes, commits ou docs. Resume as proibições (Jira, dados pessoais, configs reais, plataforma), as convenções de código e de commit, e aponta para os workflows /continuar.md, /executar-plano.md, /revisar-tarefa.md, /fim-de-branch.md, /perguntar-ao-usuario.md e /escrever-plano.md.
---

# Convenções do Painel de Squads

Skill própria do projeto (não é de terceiros). Resume o que está em `CLAUDE.md`, `.clinerules/00-regras-do-projeto.md` e `docs/guia-execucao-outra-ia.md`. Havendo conflito, vale o `CLAUDE.md`, depois o guia, depois o plano. Lista completa e deduplicada: `docs/ferramentas-claude-para-o-cline/regras.md`.

## Antes de agir

1. Leia `HANDOFF.md` e o plano da vez. Para retomar, use o workflow `/continuar.md`.
2. Rode `git fetch` e veja se há branch ou PR fora da `main` (`git branch -r --no-merged origin/main`, `gh pr list`). Havendo, pergunte ao usuário (workflow `/perguntar-ao-usuario.md`).

## Proibições (valem com ou sem o hook ligado)

- **Jira:** com `JIRA_LIVE_ENABLED` diferente de `true`, nenhum acesso a servidor Jira por nenhum caminho (app, `curl`, navegador, script, MCP). Só o usuário muda a flag; nunca a ligue, nem dentro de um comando. Testes usam sempre fixtures e `MockEngine`. A regra vigente é a regra 1 do `CLAUDE.md`; não há exigência extra de autorização por pesquisa.
- **Arquivos reais:** nunca edite nem commite `.env`, `config/{papeis,equipes,ausencias,squads}.yaml` nem `Filtros_Status_tool.txt`. Use os modelos `.exemplo`/`.demo`.
- **Dados pessoais:** só nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`) em fixtures, testes, docs e commits.
- **Banco:** SQLite em arquivo; testes com SQLite real em arquivo temporário (`testContextComBanco`). Nunca H2 nem mock. Próxima migração livre (hoje a V2).
- **Git:** nunca `git add -A` nem `git add -f`; só mescle PR pelo fluxo de entrega (`docs/superpowers/fluxo-de-entrega.md`, seção 5: 7 condições; Plano 16 só com ordem expressa do usuário) e nunca apague branch com código fora da `main`.
- **Travas:** um bloqueio do hook nunca é contornado reescrevendo o comando. Pare e relate.

## Código

- Backend: pacote `br.sidia.painel` em `app/backend`. JQL só por `JqlBuilder`/`CardQueryFactory` (valores suspeitos são rejeitados, nunca escapados). SQL só parametrizado. Toda rota de dados passa por `Visibility` e grava `access_log`. Relógio e calendário injetados; fuso America/Manaus; dias úteis por padrão.
- Frontend: `app/frontend` (pnpm + Vite). Três temas (Claro, Escuro, Sidia), pt-BR e inglês completos, WCAG AA, a cor nunca é o único sinal, CSV com neutralização de fórmula.
- Sem ranking de pessoas: tabelas de pessoas em ordem alfabética.
- Comentários em pt-BR, no estilo do arquivo vizinho.

## Testes e commits

- TDD: teste que falha pelo motivo certo, código mínimo, teste passando. Mutação mental em cada teste importante.
- Comandos: `cd app/backend && ./gradlew test`; `cd app/frontend && pnpm vitest run && pnpm build`.
- Um commit por tarefa, Conventional Commits em pt-BR (`feat(sync): ...`), `git add` só dos arquivos da tarefa.

## Dúvida

Toda dúvida, pendência ou falta de direção vira pergunta ao usuário com 2 a 4 opções: a recomendada primeiro, marcada "(Recomendada)", e cada opção com o porquê e a consequência. Veja `/perguntar-ao-usuario.md`.
