# Retomada: Painel de Squads (atualizado em 25/10/2026, fim do Plano 3A)

Para continuar: abrir a sessão com `claude --advisor opus --subagents haiku` na pasta do projeto e digitar **`/continuar`**.

## Onde paramos

As PRs #5, #6, #7 e #8 (planejamento, Planos 1, 2 e 3A) foram **mescladas na `main` em 25/10** (`5afb93f`), a pedido do usuário, depois do CI. O repositório `tlmacedo/dashboardJira` está PRIVADO (o histórico tem usernames reais). As branches locais mescladas foram apagadas; as remotas correspondentes ainda existem no GitHub (apagar só se o usuário quiser). **Em andamento: `feat/tempo-em-status` (Plano 3), só local, reposicionada sobre a `main`; Tarefas 0A e 0B feitas.** Os Planos 1, 2 e 3A estão executados e revisados.

**Regra de fim de branch (usuário, 25/10):** revisão, commit, push, PR, voltar a `main` e `pull`, sempre. Antes do push, checar dados pessoais no histórico a publicar (usernames reais, nomes, segredos, informação interna) e a visibilidade do repositório.

| Entrega | Estado |
|---|---|
| Spec (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`) | Aprovada pelo usuário |
| **Plano 1: Fundação** | Executado: Ktor, calendário de Manaus (feriados fixos mês/dia e móveis 2026–2030), papéis com vigência, controle de acesso (nega por padrão), banco PostgreSQL + retenção, cliente Jira com trava `LiveGuard`, frontend React (3 temas, pt/en), Dockerfile e compose |
| **Plano 2: Dicionário de campos e construtor de JQL** | Executado: `FieldDictionary` por instância, `JqlValues` (rejeita, nunca escapa), `JqlBuilder` (única forma de gerar JQL; usernames entre aspas; `resolved < dia seguinte`), ficha de card em YAML, `docs/jira/` (matriz e checklist da 1ª conexão) |
| **Plano 3A: Inicialização da aplicação** (`docs/superpowers/plans/2026-10-25-plano-03a-inicializacao.md`) | **Executado**: `Settings` (banco, instâncias do Jira, "vazio = padrão", `toString` sem segredos, `DATABASE_POOL_SIZE`), fábrica única do cliente Jira (https, token, sem credencial na URL; timeouts; sem vazamento por exceção; cancelamento propagado; `LiveGuard` final), `AppContext` (carrega calendário e papéis, abre o banco e migra; produção falha na subida sem papéis, calendário, gerente vigente ou senha; dev cai nos modelos), papéis só do diretório, **identidade: o `id` da pessoa é o username do Jira (`assignee`)**, Dockerfile com `config/`, hook com mais cobertura. Backend: **290 testes, 0 falhas** (execução real, PostgreSQL via Testcontainers). Revisão por tarefa, revisão final (opus), onda única e re-revisão, todas aprovadas |
| Plano 3: Tempo em status | **Em execução** na branch `feat/tempo-em-status`. Tarefa 0A (cliente Jira com cache, fechamento, busca por `JqlQuery`, teto de itens) aprovada. Tarefa 0B (`access_log`, normalização de username, contexto de teste com banco) implementada, 322 testes, 0 falhas; **revisão da 0B ainda pendente**. Próximas: Tarefas 1 a 7 |
| Ledgers e relatórios por tarefa | `.superpowers/sdd/2026-10-24-plano-01-fundacao/`, `.../2026-10-24-plano-02-dicionario-e-jql/`, `.../2026-10-25-plano-03a-inicializacao/` (git-ignorados; contêm rulings, achados Minor e relatórios; NÃO apagar enquanto houver Minor pendente) |

Commits do Plano 3A: `9cd22aa` (plano) até `8e84a31` (ajustes de identidade). Plano 2: `b734dea` até `9fceb8b`. Plano 1: `87a12db` até `8202fcb`.

## Próximos passos, em ordem

1. **Plano 3** (card Tempo em status, `docs/superpowers/plans/2026-10-24-plano-03-tempo-em-status.md`), branch `feat/tempo-em-status`: revisar a Tarefa 0B, depois executar as Tarefas 1 a 7 pelo fluxo subagent-driven (ledger em `.superpowers/sdd/2026-10-24-plano-03-tempo-em-status/`). Minor adiados de 0A (M1 a M5) e 0B (`record()` lança `IllegalArgumentException` para usuário vazio, além de `AccessLogException`) entram numa onda final. Ao terminar: revisão, commit, push, PR (checar dados pessoais), `main` e `pull`.
2. Detalhar os planos 4 a 8 só ao iniciar cada fase (índice em `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`).
3. Integração (PR) só quando o usuário decidir; decidido em 25/10: seguir empilhando e abrir o PR depois dos Planos 2 e 3.

## Regras que não podem ser esquecidas

- **Nenhuma consulta ao Jira da empresa sem autorização expressa do usuário, por pesquisa.** Hoje nenhuma está autorizada (hook em `.claude/hooks/guard.sh`, flag `JIRA_LIVE_ENABLED=false`). Vale para o MCP da Atlassian e para qualquer comando de rede.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- **Docker sempre ativo** (`sh .claude/hooks/ensure-docker.sh`); testes de banco com PostgreSQL real via Testcontainers; nunca trocar por H2 ou mock.
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`. `Filtros_Status_tool.txt` (usernames reais) está no `.gitignore`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.
- **Dica do hook:** o `guard.sh` barra comandos Bash que citam variáveis de conexão do Jira, URLs jira/atlassian ou juntam `curl` com a palavra Jira (inclusive o caminho `.../dashboardJira`). Falso positivo conhecido e documentado em `test-guard.sh`. **Nunca reescreva um comando bloqueado para contornar:** crie e edite arquivos com Write/Edit (nunca heredoc nem python no Bash) e rode comandos curtos e separados.

## Decisões principais (resumo; detalhe na spec e na memória do projeto)

- Stack: Ktor + PostgreSQL + React/Vite. O app Python está em `legacy/` até a Fase 8 (só referência).
- Fuso America/Manaus; dias úteis por padrão; concluída = categoria "done" + `resolution`.
- **Identidade e papéis:** em `papeis.yaml`, `id` = identificador único da PESSOA = username do Jira = `assignee` (minúsculas, `[a-z0-9._-]`, 1 a 64; reservados `dev`, `system`, `admin`, `root`, `anonymous`). O cargo é o `papel`: USUARIO vê só o próprio; SQUAD_LEADER o próprio e os devs do seu squad (exige `squad`); COORDENADOR e GERENTE veem todos. Issues de quem não está no arquivo só aparecem para coordenação e gerente. Vigência por datas; retenção de 24 meses configurável.
- Atraso justificado: duas métricas ("no prazo" e "ajustado"). Auditoria: só Reporter (PL), coordenadores e gerente, ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso (spec seção 7).
- Segurança do acesso: em `/api` nega por padrão (caminho normalizado; `.`/`..`, URIs absolutas e `//` exigem login; principal sem papel = 403); só `/api/health` e `/api/meta` são públicos; a casca estática é pública e não tem dados.
- Ausências e feriados em YAML, somente leitura.

## Riscos para o Plano 3 (da revisão final do 3A)

- `testContext` não tem banco; o teste `consultaEhRegistrada` (`access_log`) precisa de contexto com PostgreSQL.
- `Visibility` e `access_log` ainda não são aplicados em nenhuma rota (o Plano 3 constrói isso).
- O `Syncer` deve receber o cliente por `context.jira.create(...)`; a fábrica precisa guardar um cliente por instância e fechá-lo em `AppContext.close()`.
- Teto de 10.000 itens por busca contra "dezenas de milhares de issues" na reconciliação diária.
- `SearchRequest(jql: String)` sem tipo: só `JqlQuery` deveria chegar ao cliente.
- Normalizar com `lowercase()` o username do reporter e do login OIDC na fronteira (o diretório compara exato e em minúsculas).

## Pendências abertas

- **Rulings reversíveis a conferir na 1ª conexão autorizada:** usernames entre aspas no JQL; `resolved < dia seguinte` (data sem hora no Jira); `issuetype` e `status` com valores iniciais no dicionário; teto de páginas assume que o Jira honra o `pageSize`; `GET /issue/{key}/changelog` não verificado (plano B `expand=changelog`); conferir cada `id` de `papeis.yaml` no Jira, inclusive a caixa.
- **Minor adiados** (ledgers dos três planos): `.dockerignore` por lista de bloqueio (preferir lista de permissão); parâmetro `settingsIgnorado` de `module`; limites da varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado); inverter a regra de `exigeAutenticacao`; teste do `delay` do retry com cancelamento; máscara do token escapado em JSON/URL; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de `--line`, piscada do tema, `#1329A0` × `#0023A4`); CSP e X-Frame-Options; imagens Docker por digest; imagem com `DASHBOARD_DEV_MODE=true` por padrão.
- `RetentionTest` falhou uma vez com "Could not connect to Ryuk" (Testcontainers) e passou na repetição: possível intermitência.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo): código legado que sai na Fase 8.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
