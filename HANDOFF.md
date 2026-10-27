# Retomada: Painel de Squads (atualizado em 27/10/2026)

## COMECE AQUI (ClineSR, PC da empresa, clone do GitHub)

1. **Clone numa pasta SEM "jira" no nome** (o hook de proteção bloqueia a palavra em comandos enquanto a flag do Jira não estiver ligada) e use o Windows 11 com PowerShell. Instale/valide JDK 21, Node 22 e pnpm; **não há Docker nem banco a instalar** (a empresa os bloqueia; o banco é um arquivo SQLite).
2. Leia, nesta ordem: `.clinerules/00-regras-do-projeto.md` → `docs/guia-execucao-outra-ia.md` → `docs/superpowers/fluxo-de-entrega.md` (**o procedimento de revisão, validação, commit, push, PR, merge e volta à `main`**) → `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` → `docs/ferramentas-claude-para-o-cline/README.md` e `processo-completo.md` → `CLAUDE.md` → este arquivo → o índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`.
3. **Execute a Fase 0** (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): proxy, certificado da empresa, espelhos, testes, subida e teste do hook. Só depois o Plano 9, e assim por diante na ordem do índice.
4. **Jira:** a flag `JIRA_LIVE_ENABLED` é a única trava e **vale somente dentro de `app/.env`** (variável de terminal ou do sistema é ignorada; linha ausente = desligado). `false` = proibido para todos; `true` = liberado. **Só o usuário muda a flag, à mão**, e avisa o ClineSR. O primeiro uso real segue `docs/jira/checklist-primeira-conexao.md`, o runbook `docs/jira/runbook-sincronizacao.md` e o Plano 14a. Para diagnosticar conexão e rede (o usuário roda): `docs/jira/diagnostico-conexao.ps1`.
5. **Dúvida ou falta de direção = pergunta ao usuário** com opções (recomendada primeiro, cada opção com porquê e consequência). Workflows do Cline em `.clinerules/workflows/`.
6. Configs reais (`app/.env`, `papeis.yaml`, `status_map.yaml`, `jira_campos.yaml`, `calendario.yaml`, `vinculos.yaml`) **não** estão no GitHub: o usuário as cria a partir dos `.exemplo` (Fase 0, Tarefa 7); a IA nunca as lê, edita nem commita.
7. **Merge:** por decisão do usuário (27/10/2026), a IA mescla a PR de cada plano/bloco sozinha **quando as 7 condições da seção 5 do fluxo de entrega forem verdadeiras**; senão pergunta. Instalar as skills de terceiros: `scripts/instalar-skills-cline.ps1`.

(Quando o Claude Code for usado: abrir com `claude --advisor opus --subagents haiku` e digitar **`/continuar`**.)

## Onde paramos (27/10/2026)

- **Na `main`:** Planos 1, 2, 3A, 3, **3B (saída do Docker/PostgreSQL, PRs #18 e #19)**, **4A** (sincronização periódica, PR #14) e as **Tarefas 1 a 3 do Plano 4** (Auditoria, PR #15). Backend: 745+ testes, 0 falhas, com SQLite real. Frontend: 48 testes.
- **Nenhum acesso ao Jira foi feito** em todo o projeto. O primeiro ciclo real é do usuário (Plano 14a).
- **Plano 4 (Auditoria)** continua pela **Tarefa 4**: estado e passo a passo em `docs/superpowers/progresso/2026-10-25-plano-04-auditoria.md`. Os planos 5 a 16 e suas specs estão escritos e ajustados à nova plataforma (SQLite, sem Docker); os Planos 8, 9, 12 e 13 foram reescritos.
- O repositório `tlmacedo/dashboardJira` é PRIVADO (o histórico tem usernames reais: nunca torná-lo público sem limpar o histórico).
- Alerta do GitGuardian em `SettingsTest.kt` (3 "Generic Password"): falso positivo (valores fictícios de teste de mascaramento). O usuário pode marcá-lo como tal no painel do GitGuardian.

## Próximos passos, em ordem

1. **Fase 0** (PC da empresa) → **Plano 9** (CI e endurecimento; cria o `.github/` que ainda não existe) → **Plano 14a** (primeira conexão real, quando o usuário ligar a flag) → **Plano 4, Tarefas 4 a 9** → 5a → 5b → 6a → 6b → 7a → 7b → 10 → 11 → 14b → 8 → 12 → 13 → 15. O **Plano 16** (escrita no Jira pelo painel) é futuro e só com decisão do usuário. Justificativa e dependências: índice dos planos.
2. Cada plano/bloco = uma branch `feat/<nome>` a partir da `main` atualizada = um PR, mesclado ao fim conforme `docs/superpowers/fluxo-de-entrega.md`; só então o próximo começa.
3. Migrações: hoje só existe a `V1`; a próxima é a `V2` (confirmar com `ls app/backend/src/main/resources/db/migration`); nunca editar migração já mesclada.

## Regras que não podem ser esquecidas

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (decisão do usuário em 25/10): `false` = bloqueado para todos (app, agendador, MCP, rede); `true` = liberado. Só o usuário muda a flag; hook em `.claude/hooks/guard.sh`.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- **Sem Docker nem PostgreSQL** (bloqueados na empresa): banco SQLite em arquivo (`DATABASE_PATH`, padrão `data/painel.db`), testes com SQLite real em arquivo temporário; JDK 21 e Node/pnpm; subida por `app/iniciar.sh` ou `app/iniciar.ps1`. Migração única `V1__base.sql`. O compose e os gatilhos de Docker já foram removidos (PR #19).
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`. `Filtros_Status_tool.txt` (usernames reais) está no `.gitignore`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.
- **Dica do hook:** com `JIRA_LIVE_ENABLED` diferente de `true` no ambiente do Claude Code, o `guard.sh` barra comandos Bash que citam variáveis de conexão do Jira, URLs jira/atlassian ou juntam `curl` com a palavra Jira (inclusive o caminho `.../dashboardJira`). Falso positivo conhecido e documentado em `test-guard.sh`. Com a flag `true` essas regras não valem; continua barrado só exibir token ou senha no terminal. **Como o usuário liga a flag do hook:** (a) exportar a variável no terminal antes de abrir o Claude Code, ou (b) `env` em `.claude/settings.local.json` (ignorado pelo git); nunca no `.claude/settings.json` versionado. Vale depois de reabrir a sessão. **Atenção:** nas duas formas a flag entra no ambiente do próprio Claude Code e é herdada por todo comando que ele roda: `docker compose up` a repassa ao painel (`${JIRA_LIVE_ENABLED:-false}`) e `./gradlew` a recebe (a suíte de testes a fixa em `false`). Na prática, ligar a flag do hook liga a do app em tudo o que o Claude Code lançar. Escrita em `.claude/settings*.json`, em overrides do compose e em **todo** `*compose*.y*ml` (inclusive o `app/docker-compose.yml` comum) é barrada para a IA (só o usuário edita esses arquivos; mudança no compose vira pergunta ao usuário): o caminho é normalizado antes de casar (barras duplas, `.`/`..`, barra invertida, maiúsculas, ponto/espaço final, `:` e nomes curtos 8.3) e comando que cita esses arquivos é negado por padrão (só leitura conhecida passa). Gravar texto que liga a flag em qualquer arquivo também é barrado: na documentação, escreva "a flag em true" em vez da forma de atribuição. Regras completas em `docs/ferramentas-claude-para-o-cline/hooks.md`. **Limites honestos que não serão fechados:** a trava é contra acidente, não contra má-fé; com a flag `true`, formas indiretas de exibir segredo (`bash -c`, `env` sem pipe, `set -x`, `cat .env` etc.) não são todas cobertas; o WebFetch do Claude não passa pelo hook; com a flag `false`, atribuição calculada da flag (`=$'true'`, `${X:-true}`) não é coberta. Não verificado: se o hook enxerga a variável definida em `settings.local.json` (item b); se não enxergar, valha só a forma (a). **Nunca reescreva um comando bloqueado para contornar:** crie e edite arquivos com Write/Edit (nunca heredoc nem python no Bash) e rode comandos curtos e separados.

## Decisões principais (resumo; detalhe na spec e na memória do projeto)

- Stack: Ktor + SQLite + React/Vite. O app Python está em `legacy/` até a Fase 8 (só referência).
- Fuso America/Manaus; dias úteis por padrão; concluída = categoria "done" + `resolution`.
- **Identidade e papéis:** em `papeis.yaml`, `id` = identificador único da PESSOA = username do Jira = `assignee` (minúsculas, `[a-z0-9._-]`, 1 a 64; reservados `dev`, `system`, `admin`, `root`, `anonymous`). O cargo é o `papel`: USUARIO vê só o próprio; SQUAD_LEADER o próprio e os devs do seu squad (exige `squad`); COORDENADOR e GERENTE veem todos. Issues de quem não está no arquivo só aparecem para coordenação e gerente. Vigência por datas; retenção de 24 meses configurável.
- Atraso justificado: duas métricas ("no prazo" e "ajustado"). Auditoria: só Reporter (PL), coordenadores e gerente, ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso (spec seção 7).
- Segurança do acesso: em `/api` nega por padrão (caminho normalizado; `.`/`..`, URIs absolutas e `//` exigem login; principal sem papel = 403); só `/api/health` e `/api/meta` são públicos; a casca estática é pública e não tem dados.
- Ausências e feriados em YAML, somente leitura.

## Riscos para o Plano 3 (da revisão final do 3A)

- `testContext` não tem banco; o teste `consultaEhRegistrada` (`access_log`) precisa de contexto com banco (`testContextComBanco`, SQLite em arquivo).
- `Visibility` e `access_log` ainda não são aplicados em nenhuma rota (o Plano 3 constrói isso).
- O `Syncer` deve receber o cliente por `context.jira.create(...)`; a fábrica precisa guardar um cliente por instância e fechá-lo em `AppContext.close()`.
- Teto de 10.000 itens por busca contra "dezenas de milhares de issues" na reconciliação diária.
- `SearchRequest(jql: String)` sem tipo: só `JqlQuery` deveria chegar ao cliente.
- Normalizar com `lowercase()` o username do reporter e do login OIDC na fronteira (o diretório compara exato e em minúsculas).

## Pendências abertas

- **Rulings reversíveis a conferir na 1ª conexão real (flag ligada pelo usuário):** usernames entre aspas no JQL; `resolved < dia seguinte` (data sem hora no Jira); `issuetype` e `status` com valores iniciais no dicionário; teto de páginas assume que o Jira honra o `pageSize`; `GET /issue/{key}/changelog` não verificado (plano B `expand=changelog`); conferir cada `id` de `papeis.yaml` no Jira, inclusive a caixa.
- **Minor adiados** (ledgers dos três planos): `.dockerignore` por lista de bloqueio (preferir lista de permissão); parâmetro `settingsIgnorado` de `module`; limites da varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado); inverter a regra de `exigeAutenticacao`; teste do `delay` do retry com cancelamento; máscara do token escapado em JSON/URL; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de `--line`, piscada do tema, `#1329A0` × `#0023A4`); CSP e X-Frame-Options; imagens Docker por digest; imagem com `DASHBOARD_DEV_MODE=true` por padrão.
- Histórico: a intermitência "Could not connect to Ryuk" (Testcontainers) deixou de existir com a saída do Docker.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo): código legado que sai na Fase 8.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
