# Regras do projeto Painel de Squads (para o Cline e qualquer IA)

Este arquivo é lido automaticamente pelo Cline. A fonte da verdade do projeto é o `CLAUDE.md` na raiz (escrito para outra IA, o Claude Code, mas as regras valem para todos). O guia completo de execução está em `docs/guia-execucao-outra-ia.md`: **leia-o inteiro antes de qualquer ação**, depois o `HANDOFF.md` e o plano a executar.

## Proibições absolutas (nenhum hook protege você; vigie a si mesmo)

1. **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`.** Com `false` (padrão), **nunca acesse um servidor Jira**, por nenhum caminho: nem pelo app, nem por `curl`/`wget`/`httpie`, navegador, script, MCP da Atlassian ou qualquer ferramenta. Com `true`, o acesso está liberado (app, sincronização periódica e ferramentas), seguindo `docs/jira/checklist-primeira-conexao.md` na primeira conexão. **Só o usuário muda a flag** (ele pretende liberar o ClineSR a partir de 26/10); nunca a ligue por conta própria. Testes automáticos usam sempre fixtures e `MockEngine`, mesmo com a flag `true`. Se um comando seu citar uma URL ou host do Jira da empresa com a flag `false`, pare.
2. **Nunca edite nem commite** `.env`, `config/papeis.yaml`, `config/equipes.yaml`, `config/ausencias.yaml`, `config/squads.yaml`, nem o arquivo `Filtros_Status_tool.txt` (tem usernames reais). Use sempre os modelos `.exemplo`/`.demo`.
3. **Dados pessoais:** em fixtures, testes, docs e mensagens de commit use só nomes fictícios (`ana.silva`, `bruno`, domínio `.invalid`). Antes de qualquer `git push`, varra o que vai ser publicado em busca de usernames reais, e-mails, hosts internos, tokens e segredos. Em dúvida, pare e pergunte.
4. **Nunca mescle PR** nem apague branch com código fora da `main` sem o usuário mandar.
5. **Nunca troque o PostgreSQL real por H2 ou mock** nos testes de banco; use Testcontainers (Docker precisa estar ativo: `docker info`; se parado, abra o Docker Desktop e espere).
6. **Nunca contorne uma trava** (hook, teste, regra) reescrevendo o comando para passar. Relate ao usuário.

## Como conversar e decidir

- Converse em **português do Brasil**. Comentários de código em pt-BR. Código pode ser em inglês ou pt-BR (siga o arquivo vizinho).
- **Toda dúvida, pendência ou aprovação** vai ao usuário como **pergunta com 2 a 4 opções**, a recomendada primeiro e marcada "(Recomendada)", cada opção com o porquê e a consequência. Nunca como lista solta ou pergunta em prosa.
- Onde o plano deixa uma escolha em aberto, decida a mais segura e reversível, registre como "Ruling" no ledger do plano e siga; só pare para: ação irreversível, algo de segurança, efeito fora do repositório (merge, push em branch compartilhada, publicação) ou plano quebrado.

## Fluxo de trabalho (resumo; detalhes no guia)

- Uma branch por plano, a partir de `main` atualizada: `feat/<nome-do-plano>`.
- Tarefa a tarefa, na ordem do plano, com **TDD** (teste que falha primeiro, depois o código, depois o teste passando), um commit por tarefa, mensagem em pt-BR.
- Ao fim de cada tarefa: rode a suíte afetada; ao fim do plano: suíte completa (`cd app/backend && ./gradlew test`; `cd app/frontend && pnpm vitest run && pnpm build`), revisão do conjunto (peça ao usuário a revisão ou use o checklist do guia), push, PR, volta à `main` e `pull`.
- Atualize o `HANDOFF.md` e registre o progresso ao fim de cada tarefa (ver guia).
