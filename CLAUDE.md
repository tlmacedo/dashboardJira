# Painel de Squads (dashboardJira)

Dashboard de gestão do time TG-Basic sobre tasks do Jira Data Center: fluxo, prazos, carga, ausências e auditoria de alterações. Produto e público: [PRODUCT.md](PRODUCT.md).

## Regras que valem sempre

1. **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (decisão do usuário em 25/10, substitui a regra antiga de autorização expressa por pesquisa). **`false` (o padrão e o estado atual): é proibido acessar qualquer servidor Jira, por qualquer caminho**: cliente do app, agendador de sincronização, MCP da Atlassian, `curl`, navegador ou script, de qualquer IA ou pessoa; o trabalho se apoia em pesquisa na web (documentação pública do Jira), nos arquivos de filtro (`Filtros_Status_tool.txt`, só vocabulário), no projeto irmão bscbi-web e em fixtures. **`true`: o acesso fica liberado** (app, sincronização periódica e ferramentas), seguindo `docs/jira/checklist-primeira-conexao.md` na primeira conexão. **Só o usuário muda a flag** (previsto: o ClineSR, a pedido do usuário, a partir de 26/10); nenhuma IA liga a flag por conta própria. Trava técnica: `LiveGuard` no código (0 requisições com `false`, provado por teste) e `.claude/hooks/guard.sh` (Claude Code).
2. Trabalhar offline: fixtures, mocks e documentos. Flags de ambiente: `JIRA_LIVE_ENABLED=false` e `DASHBOARD_DEV_MODE=true` por padrão.
3. Nunca editar nem commitar `.env` ou configs com dados pessoais reais (`config/equipes.yaml`, `ausencias.yaml`, `papeis.yaml`). Usar os modelos `.exemplo`/`.demo`.
4. Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
5. Idioma: conversar em pt-BR; comentários de código em pt-BR.
6. **Sem Docker e sem servidor de banco.** A empresa bloqueia Docker e PostgreSQL. O banco é um arquivo SQLite embutido (`app/data/painel.db`, criado na primeira subida) e os testes usam SQLite real em arquivo temporário. Ambiente: JDK 21 e Node 22 com pnpm. Nunca reintroduzir contêiner, servidor de banco, H2 ou mock de banco. O arquivo de composição legado (`app/docker-compose.yml`) e a referência a ele nas settings do Claude Code só o usuário pode remover (os hooks barram a IA).

7. **Ao finalizar CADA branch, sempre, nesta ordem:** revisão (por tarefa e final), commit, `push`, PR; depois voltar para `main` e fazer `pull`, deixando tudo atualizado (o GitHub não pode ficar defasado). **Antes de qualquer `push`:** conferir que o histórico a publicar não tem dados pessoais (usernames reais, nomes), segredos nem informação interna da empresa, **lembrando que o repositório é PÚBLICO (decisão do usuário em 26/10) e que o histórico antigo tem 3 usernames reais do time (aceito e documentado), então nada novo com dado real entra: nem username, e-mail, host interno, token ou configuração real, em código, teste, fixture, documento ou mensagem de commit**; havendo dúvida, parar e perguntar. Mesclar PR em `main` só quando o usuário mandar.
8. **Subagentes e custo:** despachar pelos tipos de `.claude/agents/` (`implementador`, `revisor-de-tarefa`, `redator-de-planos`: sonnet; `tarefa-mecanica`: haiku) e **não** passar `model` no despacho, para valer o modelo do agente. O `general-purpose` (embutido, sem arquivo para editar) fica só para pesquisa avulsa. `opus` só em revisão final de código sensível a segurança (hooks, controle de acesso), dita ao usuário antes.

## Decisões de produto (resumo)

- **Stack:** backend Kotlin (Ktor) + SQLite embutido (arquivo); frontend React + TypeScript (Vite). O app Python em `legacy/` é referência e será substituído.
- **Fuso:** America/Manaus. **Tempo:** dias úteis por padrão, com opção de dias corridos.
- **Concluída:** categoria de status "done" **e** `resolution`; divergência vai para Qualidade dos dados.
- **Atraso justificado:** duas métricas lado a lado ("no prazo" e "no prazo ajustado"); labels em YAML.
- **Sem ranking de pessoas.** Tabelas de pessoas em ordem alfabética. Integridade só para gerente e sempre auditada.
- **Acesso:** `DASHBOARD_DEV_MODE=true` sem login e com todos os dados (faixa vermelha). Em produção: SSO/OIDC; usuário vê só o próprio; Squad Leader vê o próprio e o seu squad; coordenador e gerente veem todos.
- **Identidade:** o `id` da pessoa em `papeis.yaml` é o username do Jira, igual ao `assignee` das issues (não é código de cargo; o papel define a visão). Ids reservados: `dev`, `system`, `admin`, `root`, `anonymous`.
- **Auditoria:** só o Reporter (PL), coordenadores e gerente, ou o Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso. Os demais casos geram alerta ou auditoria (regras e sinais no relatório e na memória do projeto).
- **Ausências e feriados:** arquivo YAML versionado, somente leitura no app.

## Onde está cada coisa

| Assunto | Local |
|---|---|
| Inventário dos cards e cálculos do app atual | `docs/analise/relatorio-cards-app-atual.md` |
| Plano visual, temas e wireframes | `docs/design/plano-visual-frontend.md` |
| Protótipos visuais descartáveis (A densa, B editorial) | `docs/design/prototipo/` |
| Direção visual escolhida | Híbrida: densa nas telas de trabalho, editorial nas de leitura |
| Pesquisa e guias originais | `docs/guia/`, `reports/`, `research_notes/` |
| Specs e planos | `docs/superpowers/` (índice dos planos: `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`; specs por fase: `docs/superpowers/specs/2026-10-25-spec-fase-*.md`) |
| Guia para outra IA (ClineSR) | `docs/guia-execucao-outra-ia.md`, `.clinerules/`, `docs/ferramentas-claude-para-o-cline/` |
| Backend Kotlin/Ktor (pacote `br.sidia.painel`) | `app/backend/` |
| Frontend React + TypeScript (Vite) | `app/frontend/` |
| Modelo de variáveis e scripts de subida | `app/.env.example`, `app/iniciar.sh`, `app/iniciar.ps1` |
| App Python anterior (só referência) | `legacy/` |

## Estrutura e comandos

- `app/backend` (Gradle, Kotlin 2 + Ktor 3), `app/frontend` (pnpm + Vite), `legacy/` (app Python de referência, não evoluir).
- Subir tudo local: `cd app && sh iniciar.sh` (PowerShell: `.\iniciar.ps1`); painel em `http://127.0.0.1:8080`. Para apagar os dados locais, pare o painel e apague `app/data/painel.db`. Detalhes em `app/README.md`.
- Testes: `cd app/backend && ./gradlew test` e `cd app/frontend && pnpm test`.

## Interface

Temas Claro, Escuro e Sidia; pt-BR e inglês; WCAG AA; a cor nunca é o único sinal (▲ ● ✓ junto de texto). Identidade Sidia levantada do site público e do logo enviado; não há manual de marca nem arquivo limpo do logo.
