# Retomada: Painel de Squads (pausa em 24/10/2026)

Para continuar: abrir a sessão com `claude --advisor opus --subagents haiku` na pasta do projeto e digitar **`/continuar`**.

## Onde paramos

Branch `docs/planejamento-novo-app`. Estamos na etapa **planejamento**; nenhum código do produto foi escrito.

| Entrega | Estado |
|---|---|
| Relatório dos cards do app atual (`docs/analise/relatorio-cards-app-atual.md`) | Pronto, commitado |
| Plano visual, temas e dois protótipos (`docs/design/`) | Pronto, commitado. Direção escolhida: **híbrida** (densa nas telas de trabalho, editorial nas de leitura) |
| `PRODUCT.md`, `CLAUDE.md`, travas do Jira e de segredos (`.claude/`) | Prontos e commitados; hooks testados (20 casos) |
| Design / spec (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`) | **Aprovado pelo usuário**, commitado |
| Planos 1 a 3 e índice (`docs/superpowers/plans/2026-10-24-*.md`) | Commitados (`e312ab4`); autoavaliação feita em 25/10 (ajuste no Plano 2: `safeText`); **aguardam revisão do usuário** |
| Execução | Método escolhido: subagent-driven. Plano 1 prestes a iniciar (ver lista de tarefas do projeto) |

Commits na branch: `c3fe40c` (planejamento), `67d4208` (trava reforçada), `b815aa7` (spec).

## Próximos passos, em ordem

1. ~~Commitar planos e HANDOFF~~ (feito).
2. ~~Autoavaliação dos planos 1 a 3~~ (feita em 25/10).
3. ~~Revisão dos planos e método de execução~~: usuário aprovou em 25/10 e escolheu **subagent-driven**, começando pelo Plano 1.
4. Executar o **Plano 1 (Fundação)** tarefa por tarefa (a Tarefa 1 move o app Python para `legacy/` com `git mv`).
5. Depois, Planos 2 e 3. Detalhar os planos 4 a 8 só ao iniciar cada fase.

## Regras que não podem ser esquecidas

- **Nenhuma consulta ao Jira da empresa sem autorização expressa do usuário, por pesquisa.** Hoje nenhuma está autorizada (hook em `.claude/hooks/guard.sh`, flag `JIRA_LIVE_ENABLED=false`). Vale para o MCP da Atlassian e para qualquer comando de rede.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais de equipes, ausências e papéis (usar `.exemplo`/`.demo`).
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.

## Decisões principais (resumo; detalhe na spec e na memória do projeto)

- Stack: Ktor + PostgreSQL + React/Vite. O app Python vai para `legacy/` na Fase 1 e sai na Fase 8.
- Fuso America/Manaus; dias úteis por padrão; concluída = categoria "done" + `resolution`.
- Atraso justificado: duas métricas ("no prazo" e "ajustado"). Papéis YAML com vigência por datas. Retenção de 24 meses configurável.
- Auditoria: só Reporter (PL), coordenadores ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso; sinais na seção 7 da spec (inclui `LATE_SENT`/`LATE_REVIEW` com posse ≥ 2 dias antes do vencimento).
- Ausências e feriados em YAML, somente leitura.

## Pendências abertas (não bloqueiam o próximo passo)

- `AGENTS.md` e `.agents/` (cópias do CLAUDE.md e do /continuar geradas por outra ferramenta, com caminhos trocados por `Codex`) foram apagados em 25/10 a pedido do usuário; não tinham informação única.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte de baixa resolução e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação.
- Validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
