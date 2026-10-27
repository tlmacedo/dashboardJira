# Regras do projeto Painel de Squads (para o Cline e qualquer IA)

Este arquivo é lido automaticamente pelo Cline. **O executor é o ClineSR (com o modelo Gauss 3.7) num PC com Windows 11:** os comandos do terminal são PowerShell; os equivalentes dos comandos dos documentos (`.\gradlew.bat test`, `;` no lugar de `&&`, `%USERPROFILE%` no lugar de `~`) estão em `docs/ferramentas-claude-para-o-cline/README.md`, seção "Windows 11". Trabalhe em tarefas pequenas, releia o plano a cada tarefa e releia estas regras no original em vez de resumi-las de memória. A fonte da verdade do projeto é o `CLAUDE.md` na raiz (escrito para outra IA, o Claude Code, mas as regras valem para todos). O guia completo de execução está em `docs/guia-execucao-outra-ia.md`: **leia-o inteiro antes de qualquer ação**, depois o `HANDOFF.md` e o plano a executar.

## Ferramentas do Claude portadas para o Cline

Tudo o que o Claude Code usava aqui (regras, skills, hooks, comandos, memória, modelos) e o equivalente no Cline está em `docs/ferramentas-claude-para-o-cline/` (comece pelo `README.md`; regras consolidadas em `regras.md`). Workflows deste projeto (digite `/nome.md` no chat):

- `/continuar.md`: início de toda sessão (HANDOFF, git, PRs, ambiente, próximo passo).
- `/executar-plano.md`: executar um plano tarefa a tarefa.
- `/revisar-tarefa.md`: checklist de revisão com mutação mental.
- `/fim-de-branch.md`: revisão final, varredura de dados pessoais, push, PR, merge e volta à `main` (`docs/superpowers/fluxo-de-entrega.md`).
- `/perguntar-ao-usuario.md`: como levar dúvidas ao usuário.
- `/escrever-plano.md`: escrever um plano no padrão do projeto.

Skills do projeto em `.cline/skills/`: `convencoes-painel-squads` e `novo-card-painel`.

## Proibições absolutas (vigie a si mesmo; o hook é só uma rede de proteção)

No Windows o hook é `.clinerules/hooks/PreToolUse.ps1` (no macOS/Linux, `.clinerules/hooks/PreToolUse`, que reaproveita o `.claude/hooks/guard.sh`). Ele só protege se "Enable Hooks" estiver ligado no Cline, e **antes de usá-lo rode o teste** `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .clinerules\hooks\test-PreToolUse.ps1` (ver `docs/ferramentas-claude-para-o-cline/hooks.md`). Com ou sem ele, as regras abaixo valem.

1. **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`.** Com `false` (padrão), **nunca acesse um servidor Jira**, por nenhum caminho: nem pelo app, nem por `curl`/`wget`/`httpie`, navegador, script, MCP da Atlassian ou qualquer ferramenta. Com `true`, o acesso está liberado (app, sincronização periódica e ferramentas), seguindo `docs/jira/checklist-primeira-conexao.md` na primeira conexão. **Só o usuário muda a flag** (ele pretende liberar o ClineSR a partir de 26/10); nunca a ligue por conta própria. Testes automáticos usam sempre fixtures e `MockEngine`, mesmo com a flag `true`. Se um comando seu citar uma URL ou host do Jira da empresa com a flag `false`, pare. Com a flag diferente de `true`, o navegador e o `web_fetch` não abrem **nenhuma** URL com `jira` ou `atlassian`, nem a documentação pública (decisão do usuário em 25/10): peça ao usuário um trecho ou use a busca genérica (`web_search`).
2. **Nunca edite, leia nem commite** `.env`, `config/papeis.yaml`, `config/equipes.yaml`, `config/ausencias.yaml`, `config/squads.yaml`. **Nunca edite nem commite** o arquivo `Filtros_Status_tool.txt` (tem usernames reais; lê-lo como vocabulário é permitido). Use sempre os modelos `.exemplo`/`.demo`.
3. **Dados pessoais:** em fixtures, testes, docs e mensagens de commit use só nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`). Antes de qualquer `git push`, varra o que vai ser publicado em busca de usernames reais, e-mails, hosts internos, tokens e segredos. Em dúvida, pare e pergunte.
4. **Mesclagem de PR:** a IA só mescla a PR de um plano/bloco quando **todas** as condições da seção 5 de `docs/superpowers/fluxo-de-entrega.md` forem verdadeiras (decisão do usuário em 27/10/2026); fora isso, nunca mescla. Nunca use `--admin`, `--force` nem push direto na `main`. Nunca apague branch com código fora da `main`.
5. **Proibido neste projeto (a TI da empresa bloqueia): contêineres, servidor de banco e WSL.** Não instale, não inicie e não tente usar nenhum deles. Também não use `bash`, `sh` nem `wsl` no terminal do Windows: no Windows 11 o `bash.exe` abre o WSL e o comando é abortado. Use sempre PowerShell. O banco é SQLite em arquivo e os testes usam SQLite real em arquivo temporário (`testContextComBanco`). Nunca troque por H2 nem por mock de banco. Tradução do SQL e regras de teste: `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`.
6. **Nunca contorne uma trava** (hook, teste, regra) reescrevendo o comando para passar. Relate ao usuário.

## Como conversar e decidir

- Converse em **português do Brasil**. Comentários de código em pt-BR. Código pode ser em inglês ou pt-BR (siga o arquivo vizinho).
- Precedência: `CLAUDE.md` > estas regras > workflows e skills do projeto > skills de terceiros (superpowers, mattpocock-skills, impeccable). Em conflito, vale o projeto.
- **Toda dúvida, pendência, falta de direção ou aprovação SEMPRE** vai ao usuário como **pergunta com 2 a 4 opções**, a recomendada primeiro e marcada "(Recomendada)", cada opção com o porquê e a consequência. Nunca como lista solta ou pergunta em prosa. Use `ask_followup_question` com `options`, como em `/perguntar-ao-usuario.md`.
- Onde o plano deixa uma escolha em aberto, decida a mais segura e reversível, registre como "Ruling" no ledger do plano e siga; só pare para: ação irreversível, algo de segurança, efeito fora do repositório (merge, push em branch compartilhada, publicação) ou plano quebrado.

## Fluxo de trabalho (resumo; detalhes no guia)

- Uma branch por plano, a partir de `main` atualizada: `feat/<nome-do-plano>`.
- Tarefa a tarefa, na ordem do plano, com **TDD** (teste que falha primeiro, depois o código, depois o teste passando), um commit por tarefa, mensagem em pt-BR.
- Ao fim de cada tarefa: rode a suíte afetada; ao fim do plano: suíte completa (`cd app/backend && ./gradlew test`; `cd app/frontend && pnpm vitest run && pnpm build`), revisão final do conjunto em contexto limpo, varredura de dados pessoais, push, PR, CI verde, merge (condições da seção 5 do fluxo de entrega), volta à `main` e `pull --ff-only`. Procedimento único: `docs/superpowers/fluxo-de-entrega.md`.
- Atualize o `HANDOFF.md` e registre o progresso ao fim de cada tarefa (ver guia).
