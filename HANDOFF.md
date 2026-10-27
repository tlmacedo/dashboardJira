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

## O que o usuário precisa ter pronto (a IA não consegue sozinha)

- **Fase 0:** acesso para instalar JDK 21, Node 22 e pnpm; `gh auth login` feito; se houver proxy/inspeção TLS, endereço do proxy/espelho e o arquivo `.pem` da CA da empresa.
- **Clone:** `git clone https://github.com/tlmacedo/dashboardJira.git painel` (ação do usuário; a pasta `painel` não tem "jira" no nome, e o hook barra comandos da IA que citem URL com "jira" enquanto a flag estiver desligada).
- **Plano 9:** proteção da `main` no GitHub (exigir os 4 checks), se o plano da conta permitir.
- **Plano 14a:** URL e token do Jira (em `app/.env`), a flag ligada à mão, `papeis.yaml` com ao menos um GERENTE, `status_map.yaml` e `jira_campos.yaml` reais.
- **Plano 13:** provedor OIDC, máquina de produção, proxy/TLS e cofre/arquivo de segredos.
- **Branches conhecidas** (ignorar no `git branch -r --no-merged`): nenhuma pendente em 27/10/2026; se aparecer uma branch `ccr-*` ou `claude/*` antiga, confirme que está mesclada e peça ao usuário para apagá-la.

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
- **Hooks e a flag (atenção):** o painel lê a flag só de `app/.env`, mas hoje os hooks de proteção ainda leem a variável da flag do ambiente do processo da IA (Claude Code ou VS Code/Cline). Enquanto a Tarefa 0 do Plano 14 não for feita, o usuário precisa, ao ligar a flag no `app/.env`, definir também a variável de usuário do Windows com o mesmo nome e valor e reiniciar o VS Code; senão os hooks continuam barrando comandos que citem o Jira. Os hooks também barram a palavra "jira" em caminhos (falso positivo conhecido): clone numa pasta sem "jira" no nome. Com a flag desligada, continua barrado exibir token.

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
- **Minor adiados** (ledgers dos três planos): parâmetro `settingsIgnorado` de `module`; limites da varredura da fábrica (`::JiraClient`, alias, `typealias`, `copy` aninhado); inverter a regra de `exigeAutenticacao`; teste do `delay` do retry com cancelamento; máscara do token escapado em JSON/URL; teste unitário de `resolverDentro` com symlink; acessibilidade da casca (contraste de `--line`, piscada do tema, `#1329A0` × `#0023A4`); CSP e X-Frame-Options; imagens Docker por digest; imagem com `DASHBOARD_DEV_MODE=true` por padrão.
- Histórico: a intermitência "Could not connect to Ryuk" (Testcontainers) deixou de existir com a saída do Docker.
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo): código legado que sai na Fase 8.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
