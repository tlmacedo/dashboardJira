# Painel de Squads (dashboardJira)

Dashboard de gestão do time TG-Basic sobre tasks do Jira Data Center: fluxo, prazos, carga, ausências e auditoria de alterações. Produto e público: [PRODUCT.md](PRODUCT.md).

## Regras que valem sempre

1. **Nenhuma consulta ao Jira da empresa sem autorização expressa do usuário, por pesquisa.** Hoje nenhuma está autorizada. Vale também para o MCP da Atlassian e para `curl`. Trava técnica: `.claude/hooks/guard.sh` (bloqueia MCP Atlassian e comandos de rede para o Jira). A regra é temporária; só o usuário a remove.
2. Trabalhar offline: fixtures, mocks e documentos. Flags de ambiente: `JIRA_LIVE_ENABLED=false` e `DASHBOARD_DEV_MODE=true` por padrão.
3. Nunca editar nem commitar `.env` ou configs com dados pessoais reais (`config/equipes.yaml`, `ausencias.yaml`, `papeis.yaml`). Usar os modelos `.exemplo`/`.demo`.
4. Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
5. Idioma: conversar em pt-BR; comentários de código em pt-BR.
6. **Docker sempre ativo.** O projeto depende de contêineres (PostgreSQL e testes com Testcontainers). Antes de testes, build ou qualquer trabalho que use banco, confira `docker info`; se estiver parado, **inicie em segundo plano** com `sh .claude/hooks/ensure-docker.sh` (abre o Docker Desktop, espera o daemon e sobe o `postgres` do `app/docker-compose.yml`). Hooks já fazem isso ao abrir a sessão e antes de `gradlew`/`docker`. Nunca trocar o banco real por H2 ou mock porque o Docker estava desligado: inicie-o ou relate.

## Decisões de produto (resumo)

- **Stack:** backend Kotlin (Ktor) + PostgreSQL; frontend React + TypeScript (Vite). O app Python em `legacy/` é referência e será substituído.
- **Fuso:** America/Manaus. **Tempo:** dias úteis por padrão, com opção de dias corridos.
- **Concluída:** categoria de status "done" **e** `resolution`; divergência vai para Qualidade dos dados.
- **Atraso justificado:** duas métricas lado a lado ("no prazo" e "no prazo ajustado"); labels em YAML.
- **Sem ranking de pessoas.** Tabelas de pessoas em ordem alfabética. Integridade só para gerente e sempre auditada.
- **Acesso:** `DASHBOARD_DEV_MODE=true` sem login e com todos os dados (faixa vermelha). Em produção: SSO/OIDC; usuário vê só o próprio; Squad Leader vê o próprio e o seu squad; coordenador e gerente veem todos.
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
| Specs e planos | `docs/superpowers/` |
| Backend Kotlin/Ktor (pacote `br.sidia.painel`) | `app/backend/` |
| Frontend React + TypeScript (Vite) | `app/frontend/` |
| Docker, compose e modelo de variáveis | `app/Dockerfile`, `app/docker-compose.yml`, `app/.env.example` |
| App Python anterior (só referência) | `legacy/` |

## Estrutura e comandos

- `app/backend` (Gradle, Kotlin 2 + Ktor 3), `app/frontend` (pnpm + Vite), `legacy/` (app Python de referência, não evoluir).
- Subir tudo local: `cd app && POSTGRES_PASSWORD=<valor descartável> docker compose up --build -d` (painel em `http://127.0.0.1:8080`, só localhost; `docker compose down -v` ao terminar). A senha do banco é obrigatória, sem padrão, e nunca vai para arquivo versionado. Detalhes em `app/README.md`.
- Testes: `cd app/backend && ./gradlew test` e `cd app/frontend && pnpm test`.

## Interface

Temas Claro, Escuro e Sidia; pt-BR e inglês; WCAG AA; a cor nunca é o único sinal (▲ ● ✓ junto de texto). Identidade Sidia levantada do site público e do logo enviado; não há manual de marca nem arquivo limpo do logo.
