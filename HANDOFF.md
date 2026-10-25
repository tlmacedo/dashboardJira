# Retomada: Painel de Squads (atualizado em 25/10/2026)

Para continuar: abrir a sessão com `claude --advisor opus --subagents haiku` na pasta do projeto e digitar **`/continuar`**.

## Onde paramos

Branches empilhadas (nada foi mesclado em `main` nem publicado): `docs/planejamento-novo-app` → `feat/fundacao` (Plano 1) → **`feat/dicionario-jql` (Plano 2, branch atual)**. **Os Planos 1 e 2 estão executados e revisados.**

| Entrega | Estado |
|---|---|
| Spec (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`) | Aprovada pelo usuário |
| Planos 1 a 3 e índice (`docs/superpowers/plans/2026-10-24-*.md`) | Escritos; usuário aprovou e escolheu execução **subagent-driven** |
| **Plano 1: Fundação (10 tarefas + ajustes 3b e 4b)** | **Executado**: backend Ktor (flags, calendário de Manaus, papéis com vigência, controle de acesso, banco PostgreSQL + retenção de 24 meses, cliente Jira com trava `LiveGuard`, paginação), frontend React (3 temas, pt/en, menu por papel), SPA servida pelo Ktor, Dockerfile e compose. Backend: **91 testes, 0 falhas** (execução real, com PostgreSQL via Testcontainers); frontend: 16 testes, build ok. Revisão por tarefa, revisão final da branch (opus) e re-revisão da correção final, todas aprovadas |
| **Plano 2: Dicionário de campos e construtor de JQL (5 tarefas + onda final)** | **Executado**: `FieldDictionary` por instância (`forInstance`), `JqlValues` (validadores que REJEITAM, nunca escapam), `JqlBuilder` (única forma de gerar JQL; usernames entre aspas; fim do período exclusivo `resolved < dia seguinte`), ficha de card em YAML com ensaio na carga, `jira_campos.exemplo.yaml`, `docs/jira/matriz-compatibilidade.md` e `docs/jira/checklist-primeira-conexao.md`. Backend: **185 testes, 0 falhas**. Revisão por tarefa, revisão final (opus), onda única de correção e re-revisão, todas aprovadas |
| Plano 3 | **Não iniciado** (ganhou uma **Tarefa 0** de inicialização da aplicação) |
| Ledgers e relatórios por tarefa | `.superpowers/sdd/2026-10-24-plano-01-fundacao/` e `.../2026-10-24-plano-02-dicionario-e-jql/` (git-ignorados; contêm todos os rulings, achados Minor e relatórios; NÃO apagar enquanto houver Minor pendente) |

Commits principais: Plano 1 `87a12db` até `8202fcb`, `d386f03` (planos) e `5b6f9e7` (HANDOFF/decisões); Plano 2 `b734dea` até `9fceb8b` (onda final em `0a6023f`).

## Próximos passos, em ordem

1. ~~Integração da branch~~: decidido em 25/10, seguir empilhando (próxima branch a partir de `feat/fundacao`) e abrir o PR só quando os Planos 2 e 3 estiverem prontos. Não mesclar nem publicar sem o usuário decidir.
2. ~~Gerente em `isReporterAuthorized`~~: decidido em 25/10, o gerente é autorizado como o coordenador (spec, CLAUDE.md e PRODUCT.md atualizados).
3. ~~Plano 2~~: executado e revisado (25/10).
4. Antes do **Plano 3** (a branch seguinte nasce de `feat/dicionario-jql`), detalhar e executar a **Tarefa 0 de inicialização** (ver o topo do Plano 3): carregar calendário e papéis, abrir o banco e migrar, `DATABASE_USER/PASSWORD` no `Settings`, fábrica única do cliente Jira que valida `https`/token e liga o `LiveGuard` ao `Settings`, correções do cliente (total ausente = erro etc.), `logback.xml`.
5. Depois, Plano 3 (card Tempo em status) e detalhar os planos 4 a 8 só ao iniciar cada fase.

## Regras que não podem ser esquecidas

- **Nenhuma consulta ao Jira da empresa sem autorização expressa do usuário, por pesquisa.** Hoje nenhuma está autorizada (hook em `.claude/hooks/guard.sh`, flag `JIRA_LIVE_ENABLED=false`). Vale para o MCP da Atlassian e para qualquer comando de rede.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- **Docker sempre ativo** (`sh .claude/hooks/ensure-docker.sh`); os testes de banco usam PostgreSQL real via Testcontainers; nunca trocar por H2 ou mock.
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.
- **Dica do hook:** o `guard.sh` barra comandos Bash que citam variáveis de conexão do Jira, URLs jira/atlassian ou juntam `curl` com a palavra Jira (inclusive o caminho `.../dashboardJira`). É um falso positivo conhecido. **Nunca reescreva um comando bloqueado para contornar:** crie e edite arquivos com Write/Edit e rode comandos curtos e separados.

## Decisões principais (resumo; detalhe na spec e na memória do projeto)

- Stack: Ktor + PostgreSQL + React/Vite. O app Python está em `legacy/` até a Fase 8 (só referência).
- Fuso America/Manaus; dias úteis por padrão; concluída = categoria "done" + `resolution`.
- Calendário: feriados fixos só com mês e dia (valem todo ano); móveis com data completa de 2026 a 2030 (estender depois de 2030).
- Papéis YAML com vigência por datas e ids por cargo (`ge-001`, `co-001`, `sl-001`, `us-001`); retenção de 24 meses configurável.
- Atraso justificado: duas métricas ("no prazo" e "ajustado"). Auditoria: só Reporter (PL), coordenadores ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso (sinais na seção 7 da spec, inclui `LATE_SENT`/`LATE_REVIEW` com posse ≥ 2 dias antes do vencimento).
- Segurança do acesso: em `/api` nega por padrão (caminho normalizado; `.`/`..`, URIs absolutas e `//` exigem login); só `/api/health` e `/api/meta` são públicos; a casca estática (fora de `/api`) é pública e não tem dados.
- Ausências e feriados em YAML, somente leitura.

## Pendências abertas

- **Rulings do Plano 2 a conferir** (tomados sem parar a execução; reversíveis): (1) usernames saem entre aspas duplas no JQL (`assignee in ("x")`), para fechar palavras reservadas; confirmar na 1ª conexão autorizada que o Jira DC aceita; (2) fim do período exclusivo no dia seguinte (`resolved < "aaaa-mm-dd"` com `ate + 1`) para não perder o último dia; o comportamento de data sem hora do Jira também está por validar; (3) `InvalidJqlValueException` herda de `IllegalArgumentException` (toda entrada inválida vira 400 numa rota futura); (4) `issuetype` e `status` têm `valores` iniciais no dicionário (ampliar depois de `/status`).
- **Minor do Plano 2 para o início do Plano 3:** comentário velho na ficha `app/config/cards/tempo-em-status.card.yaml` (diz `resolved <= ate`; agora é `< dia seguinte`); `JqlBuilder.valor()` devolve username sem aspas em `inList`/`notInList`/`equalsTo` de campo USER; testes de tipo de campo para `os` e `tipo`; `fields` por id (`customfield_N`, normalizar `cf[N]`); JQL tipado em `SearchRequest` (só `JqlQuery` chega ao cliente); `orderBy` repetido; `PreparacaoDocsTest` por seção; nomes de pessoas reais ainda no texto histórico de `docs/superpowers/plans/2026-10-24-plano-02-dicionario-e-jql.md` (trocar por fictícios).

- **Minor adiados** (lista completa no ledger e no relatório da revisão final): endurecer `exigeAutenticacao` invertendo a regra (aceitar como público só `/` seguido de caractere diferente de `/` e `\`) e testes de `/\h/api/x` e `/api%2fx`; validar `https` e token no cliente; retry em falha de rede; `total` ausente como erro; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de borda `--line`, piscada do tema, `#1329A0` do protótipo × `#0023A4` do plano); CSP e X-Frame-Options; imagens por digest; imagem Docker com `DASHBOARD_DEV_MODE=true` por padrão.
- `RetentionTest` falhou uma vez com "Could not connect to Ryuk" (Testcontainers) e passou na repetição: possível intermitência.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo aceita chamadas): código legado que sai na Fase 8; não corrigido.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- O endpoint `GET /issue/{key}/changelog` do Jira DC não foi verificado (plano B: `expand=changelog` paginado); entra no checklist da 1ª conexão autorizada.
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
