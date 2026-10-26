# Retomada: Painel de Squads (atualizado em 26/10/2026)

## COMECE AQUI (ClineSR, outro PC, clone do GitHub)

1. Leia, nesta ordem: `.clinerules/00-regras-do-projeto.md` → `docs/guia-execucao-outra-ia.md` → `docs/ferramentas-claude-para-o-cline/README.md` → `CLAUDE.md` → este arquivo → o índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`.
2. **Estado do código:** Planos 1, 2, 3A, 3 e **4A (sincronização periódica, 12 tarefas, PR #14)** estão na `main`; veja `docs/superpowers/progresso/2026-10-25-plano-04a-sincronizacao.md` (estado final, Minor adiados, rulings, as 8 coisas do ClineSR e as pendências da 1ª conexão). O **Plano 4 (Auditoria, 9 tarefas) está PARCIAL na branch `feat/auditoria`** (PR aberta como rascunho, não mesclada): Tarefas 1 e 2 concluídas e revisadas; **continue pela Tarefa 3** conforme `docs/superpowers/progresso/2026-10-25-plano-04-auditoria.md` (estado por tarefa e passo a passo). Os planos 5 a 16 e suas specs (`docs/superpowers/specs/2026-10-25-spec-fase-*.md`) estão escritos e aguardam execução, na ordem do índice.
3. **Jira:** a flag `JIRA_LIVE_ENABLED` é a única trava (`false` = proibido para todos; `true` = liberado). Só o usuário a muda. O primeiro uso real segue `docs/jira/checklist-primeira-conexao.md` e o Plano 14a.
4. **Dúvida ou falta de direção = pergunta ao usuário** com opções (recomendada primeiro, cada opção com porquê e consequência). Workflows do Cline em `.clinerules/workflows/`.
5. **Clone o projeto numa pasta SEM "jira" no nome** (o hook de proteção bloqueia a palavra), no Windows 11 use os comandos PowerShell dos workflows, e rode primeiro o teste do hook (`docs/ferramentas-claude-para-o-cline/hooks.md`). Instalar as skills de terceiros: `scripts/instalar-skills-cline.ps1`.
6. Configs reais (`.env`, `papeis.yaml`, `status_map.yaml`, `jira_campos.yaml`) **não** estão no GitHub: crie a partir dos `.exemplo`; nunca as commite.

(Quando o Claude Code for usado: abrir com `claude --advisor opus --subagents haiku` e digitar **`/continuar`**.)

## Onde paramos

As PRs #5, #6, #7 e #8 (planejamento, Planos 1, 2 e 3A) foram **mescladas na `main` em 25/10** (`5afb93f`), a pedido do usuário, depois do CI. O repositório `tlmacedo/dashboardJira` está PRIVADO (o histórico tem usernames reais). As branches locais mescladas foram apagadas; as remotas correspondentes ainda existem no GitHub (apagar só se o usuário quiser). **Plano 3 (`feat/tempo-em-status`) executado e revisado (tarefas 0A a 7, onda final de Minor e revisão final); branch publicada e PR aberta em 25/10, merge só quando o usuário mandar.** Os Planos 1, 2 e 3A estão executados e revisados.

**Regra de fim de branch (usuário, 25/10):** revisão, commit, push, PR, voltar a `main` e `pull`, sempre. Antes do push, checar dados pessoais no histórico a publicar (usernames reais, nomes, segredos, informação interna) e a visibilidade do repositório.

| Entrega | Estado |
|---|---|
| Spec (`docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md`) | Aprovada pelo usuário |
| **Plano 1: Fundação** | Executado: Ktor, calendário de Manaus (feriados fixos mês/dia e móveis 2026–2030), papéis com vigência, controle de acesso (nega por padrão), banco PostgreSQL + retenção, cliente Jira com trava `LiveGuard`, frontend React (3 temas, pt/en), Dockerfile e compose |
| **Plano 2: Dicionário de campos e construtor de JQL** | Executado: `FieldDictionary` por instância, `JqlValues` (rejeita, nunca escapa), `JqlBuilder` (única forma de gerar JQL; usernames entre aspas; `resolved < dia seguinte`), ficha de card em YAML, `docs/jira/` (matriz e checklist da 1ª conexão) |
| **Plano 3A: Inicialização da aplicação** (`docs/superpowers/plans/2026-10-25-plano-03a-inicializacao.md`) | **Executado**: `Settings` (banco, instâncias do Jira, "vazio = padrão", `toString` sem segredos, `DATABASE_POOL_SIZE`), fábrica única do cliente Jira (https, token, sem credencial na URL; timeouts; sem vazamento por exceção; cancelamento propagado; `LiveGuard` final), `AppContext` (carrega calendário e papéis, abre o banco e migra; produção falha na subida sem papéis, calendário, gerente vigente ou senha; dev cai nos modelos), papéis só do diretório, **identidade: o `id` da pessoa é o username do Jira (`assignee`)**, Dockerfile com `config/`, hook com mais cobertura. Backend: **290 testes, 0 falhas** (execução real, PostgreSQL via Testcontainers). Revisão por tarefa, revisão final (opus), onda única e re-revisão, todas aprovadas |
| Plano 3: Tempo em status | **Executado** na branch `feat/tempo-em-status` (PR aberta): cliente Jira com cache, `access_log`, linha do tempo por status/responsável, percentis, `TimeInStatusCalculator`, changelog/Syncer/IssueStore (simulados, idempotentes, unicidade por instância, `change_event` só com campos permitidos), API `/api/me`, `/api/cards/tempo-em-status` e `/api/drill/...` com visibilidade por papel, filtro OS Version, tela React (avisos com código e gravidade, CSV sem injeção de fórmula), documento de diferenças com o app atual. Backend 428 testes, frontend 36, 0 falhas. Revisões por tarefa, onda final e revisão final (opus). Ledger: `.superpowers/sdd/2026-10-24-plano-03-tempo-em-status/` |
| Ledgers e relatórios por tarefa | `.superpowers/sdd/2026-10-24-plano-01-fundacao/`, `.../2026-10-24-plano-02-dicionario-e-jql/`, `.../2026-10-25-plano-03a-inicializacao/` (git-ignorados; contêm rulings, achados Minor e relatórios; NÃO apagar enquanto houver Minor pendente) |

Commits do Plano 3A: `9cd22aa` (plano) até `8e84a31` (ajustes de identidade). Plano 2: `b734dea` até `9fceb8b`. Plano 1: `87a12db` até `8202fcb`.

## Plano 4A (sincronização periódica), estado em 26/10 (branch `feat/sincronizacao`)

- **Plano 4A pronto**: as 12 tarefas, a onda final de Minor, a revisão final (opus) e a rodada de correção dela (I1 a I4 e M1 a M4) estão na branch. Falta só o fim de fluxo (varredura de dados pessoais, push, PR contra a `origin/main`, merge). Detalhes, commits, testes, Minor adiados e rulings: `docs/superpowers/progresso/2026-10-25-plano-04a-sincronizacao.md`.
- **Próximo: Plano 9 e depois Plano 4**, conforme o índice dos planos.
- **Nenhum acesso ao Jira foi feito.** O primeiro ciclo real é do **usuário**: ele liga a flag de acesso ao Jira **à mão** e segue o runbook (Etapa 1 com `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5`). O ClineSR (ou qualquer IA) **pede a ele**: os hooks barram a IA de editar o arquivo de ambiente, o compose e as settings, e de gravar texto que ligue a flag. **Decisão provisória de 26/10 (madrugada), a confirmar com o usuário** (a alternativa é uma exceção explícita nos hooks).
- Travas dos hooks são de acidente, não de má-fé (ruling de 26/10); três opções de endurecimento ficam para decisão futura do usuário (proteger os diretórios de hooks, resolver symlinks, bloquear qualquer escrita que cite a flag com valor diferente de desligado): ver o arquivo de progresso.
- Pendências do primeiro ciclo: formato de `from/to` do assignee (`JIRAUSER*`), fuso do `updated >=`, `key in (...)` com chave apagada (HTTP 400 e fallback por chave), tipo e direção do link `Blocks` (linhas na matriz de compatibilidade). `SYNC_INSTANCIAS` só aceita `samsung` por enquanto (`sidia` exige ficha própria, Plano 14b).

## Próximos passos, em ordem

1. **Plano 4A pronto; próximo: Plano 9 e depois Plano 4.** Fim de fluxo do 4A: varredura de dados pessoais, push, PR contra a `origin/main`, merge (a sincronização periódica, a retenção e o `status_map.yaml` fora do `.gitignore`, antes pendências sem dono, foram entregues no 4A).
2. **Ordem dos planos:** conforme o índice em `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`; detalhar só ao iniciar.
3. Regra de integração: cada branch termina com revisão, push, PR e volta à `main` com `pull`; mesclar só quando o usuário mandar.

## Regras que não podem ser esquecidas

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (decisão do usuário em 25/10): `false` = bloqueado para todos (app, agendador, MCP, rede); `true` = liberado. Só o usuário muda a flag; hook em `.claude/hooks/guard.sh`.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- **Docker sempre ativo** (`sh .claude/hooks/ensure-docker.sh`); testes de banco com PostgreSQL real via Testcontainers; nunca trocar por H2 ou mock.
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`. `Filtros_Status_tool.txt` (usernames reais) está no `.gitignore`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.
- **Dica do hook:** com `JIRA_LIVE_ENABLED` diferente de `true` no ambiente do Claude Code, o `guard.sh` barra comandos Bash que citam variáveis de conexão do Jira, URLs jira/atlassian ou juntam `curl` com a palavra Jira (inclusive o caminho `.../dashboardJira`). Falso positivo conhecido e documentado em `test-guard.sh`. Com a flag `true` essas regras não valem; continua barrado só exibir token ou senha no terminal. **Como o usuário liga a flag do hook:** (a) exportar a variável no terminal antes de abrir o Claude Code, ou (b) `env` em `.claude/settings.local.json` (ignorado pelo git); nunca no `.claude/settings.json` versionado. Vale depois de reabrir a sessão. **Atenção:** nas duas formas a flag entra no ambiente do próprio Claude Code e é herdada por todo comando que ele roda: `docker compose up` a repassa ao painel (`${JIRA_LIVE_ENABLED:-false}`) e `./gradlew` a recebe (a suíte de testes a fixa em `false`). Na prática, ligar a flag do hook liga a do app em tudo o que o Claude Code lançar. Escrita em `.claude/settings*.json`, em overrides do compose e em **todo** `*compose*.y*ml` (inclusive o `app/docker-compose.yml` comum) é barrada para a IA (só o usuário edita esses arquivos; mudança no compose vira pergunta ao usuário): o caminho é normalizado antes de casar (barras duplas, `.`/`..`, barra invertida, maiúsculas, ponto/espaço final, `:` e nomes curtos 8.3) e comando que cita esses arquivos é negado por padrão (só leitura conhecida passa). Gravar texto que liga a flag em qualquer arquivo também é barrado: na documentação, escreva "a flag em true" em vez da forma de atribuição. Regras completas em `docs/ferramentas-claude-para-o-cline/hooks.md`. **Limites honestos que não serão fechados:** a trava é contra acidente, não contra má-fé; com a flag `true`, formas indiretas de exibir segredo (`bash -c`, `env` sem pipe, `set -x`, `cat .env` etc.) não são todas cobertas; o WebFetch do Claude não passa pelo hook; com a flag `false`, atribuição calculada da flag (`=$'true'`, `${X:-true}`) não é coberta. Não verificado: se o hook enxerga a variável definida em `settings.local.json` (item b); se não enxergar, valha só a forma (a). **Nunca reescreva um comando bloqueado para contornar:** crie e edite arquivos com Write/Edit (nunca heredoc nem python no Bash) e rode comandos curtos e separados.

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

- **Rulings reversíveis a conferir na 1ª conexão real (flag ligada pelo usuário):** usernames entre aspas no JQL; `resolved < dia seguinte` (data sem hora no Jira); `issuetype` e `status` com valores iniciais no dicionário; teto de páginas assume que o Jira honra o `pageSize`; `GET /issue/{key}/changelog` não verificado (plano B `expand=changelog`); conferir cada `id` de `papeis.yaml` no Jira, inclusive a caixa.
- **Minor adiados** (ledgers dos três planos): `.dockerignore` por lista de bloqueio (preferir lista de permissão); parâmetro `settingsIgnorado` de `module`; limites da varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado); inverter a regra de `exigeAutenticacao`; teste do `delay` do retry com cancelamento; máscara do token escapado em JSON/URL; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de `--line`, piscada do tema, `#1329A0` × `#0023A4`); CSP e X-Frame-Options; imagens Docker por digest; imagem com `DASHBOARD_DEV_MODE=true` por padrão.
- `RetentionTest` falhou uma vez com "Could not connect to Ryuk" (Testcontainers) e passou na repetição: possível intermitência.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo): código legado que sai na Fase 8.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
