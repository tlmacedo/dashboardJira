# Retomada: Painel de Squads (atualizado em 25/10/2026)

Para continuar: abrir a sessão com `claude --advisor opus --subagents haiku` na pasta do projeto e digitar **`/continuar`**.

## Onde paramos

Branch de trabalho: `feat/fundacao` (criada a partir de `docs/planejamento-novo-app`). **O Plano 1 (Fundação) está executado e revisado.** Nada foi mesclado em `main` ainda.

| Entrega | Estado |
|---|---|
| Spec (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`) | Aprovada pelo usuário |
| Planos 1 a 3 e índice (`docs/superpowers/plans/2026-10-24-*.md`) | Escritos; usuário aprovou e escolheu execução **subagent-driven** |
| **Plano 1: Fundação (10 tarefas + ajustes 3b e 4b)** | **Executado**: backend Ktor (flags, calendário de Manaus, papéis com vigência, controle de acesso, banco PostgreSQL + retenção de 24 meses, cliente Jira com trava `LiveGuard`, paginação), frontend React (3 temas, pt/en, menu por papel), SPA servida pelo Ktor, Dockerfile e compose. Backend: **91 testes, 0 falhas** (execução real, com PostgreSQL via Testcontainers); frontend: 16 testes, build ok. Revisão por tarefa, revisão final da branch (opus) e re-revisão da correção final, todas aprovadas |
| Planos 2 e 3 | **Não iniciados** (o Plano 3 ganhou uma **Tarefa 0** de inicialização da aplicação) |
| Ledger e relatórios por tarefa | `.superpowers/sdd/2026-10-24-plano-01-fundacao/` (git-ignorado; contém todos os rulings, achados Minor e relatórios) |

Commits principais da branch `feat/fundacao`: `87a12db` (move o app Python para `legacy/`) até `8202fcb` (correção final) e `d386f03` (planos atualizados).

## Próximos passos, em ordem

1. **Decidir como integrar a branch `feat/fundacao`** (mesclar em `main`, abrir PR, ou seguir empilhando). Não mesclar nem publicar sem o usuário decidir.
2. **Decisão pendente do usuário:** `isReporterAuthorized` hoje autoriza também o GERENTE (spec e CLAUDE.md citam só coordenadores). Confirmar.
3. Executar o **Plano 2** (dicionário de campos e construtor de JQL) pelo mesmo fluxo subagent-driven (`superpowers:subagent-driven-development`). O plano já descreve `JqlValues.safeText`.
4. Antes do **Plano 3**, detalhar e executar a **Tarefa 0 de inicialização** (ver o topo do Plano 3): carregar calendário e papéis, abrir o banco e migrar, `DATABASE_USER/PASSWORD` no `Settings`, fábrica única do cliente Jira que valida `https`/token e liga o `LiveGuard` ao `Settings`, correções do cliente (total ausente = erro etc.), `logback.xml`.
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

- **Minor adiados** (lista completa no ledger e no relatório da revisão final): endurecer `exigeAutenticacao` invertendo a regra (aceitar como público só `/` seguido de caractere diferente de `/` e `\`) e testes de `/\h/api/x` e `/api%2fx`; validar `https` e token no cliente; retry em falha de rede; `total` ausente como erro; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de borda `--line`, piscada do tema, `#1329A0` do protótipo × `#0023A4` do plano); CSP e X-Frame-Options; imagens por digest; imagem Docker com `DASHBOARD_DEV_MODE=true` por padrão.
- `RetentionTest` falhou uma vez com "Could not connect to Ryuk" (Testcontainers) e passou na repetição: possível intermitência.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo aceita chamadas): código legado que sai na Fase 8; não corrigido.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- O endpoint `GET /issue/{key}/changelog` do Jira DC não foi verificado (plano B: `expand=changelog` paginado); entra no checklist da 1ª conexão autorizada.
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
