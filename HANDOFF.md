# Retomada: Painel de Squads (atualizado em 28/10/2026)

## COMECE AQUI (ClineSR, PC da empresa, clone do GitHub)

1. Clone o projeto (qualquer nome de pasta: desde 28/10 o hook **não bloqueia mais a palavra "jira"**, só URLs/hosts de Jira) e use o Windows 11 com PowerShell. Instale/valide JDK 21, Node 22 e pnpm; **não há Docker nem banco a instalar** (a empresa os bloqueia; o banco é um arquivo SQLite).
2. Leia, nesta ordem: `.clinerules/00-regras-do-projeto.md` → `docs/guia-execucao-outra-ia.md` → `docs/superpowers/fluxo-de-entrega.md` (**o procedimento de revisão, validação, commit, push, PR, merge e volta à `main`**) → `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` → `docs/ferramentas-claude-para-o-cline/README.md` e `processo-completo.md` → `CLAUDE.md` → este arquivo → o índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md`.
3. **Execute a Fase 0** (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): proxy, certificado da empresa, espelhos, testes, subida e teste do hook. O Plano 9 (CI) já está na `main`; depois da Fase 0 siga a ordem do índice (Plano 14a quando o usuário ligar a flag).
4. **Jira:** a flag `JIRA_LIVE_ENABLED` é a única trava e **vale somente dentro de `app/.env`** (variável de terminal ou do sistema é ignorada; linha ausente = desligado). `false` = proibido para todos; `true` = liberado. **Só o usuário muda a flag, à mão**, e avisa o ClineSR. O primeiro uso real segue `docs/jira/checklist-primeira-conexao.md`, o runbook `docs/jira/runbook-sincronizacao.md` e o Plano 14a. Para diagnosticar conexão e rede (o usuário roda): `docs/jira/diagnostico-conexao.ps1`.
5. **Dúvida ou falta de direção = pergunta ao usuário** com opções (recomendada primeiro, cada opção com porquê e consequência). Workflows do Cline em `.clinerules/workflows/`.
6. Configs reais (`app/.env`, `papeis.yaml`, `status_map.yaml`, `jira_campos.yaml`, `calendario.yaml`, `vinculos.yaml`) **não** estão no GitHub: o usuário as cria a partir dos `.exemplo` (Fase 0, Tarefa 7); a IA nunca as lê, edita nem commita.
7. **Merge:** por decisão do usuário (27/10/2026), a IA mescla a PR de cada plano/bloco sozinha **quando as 7 condições da seção 5 do fluxo de entrega forem verdadeiras**; senão pergunta. Instalar as skills de terceiros: `scripts/instalar-skills-cline.ps1`.

(Quando o Claude Code for usado: abrir com `claude --advisor opus --subagents haiku` e digitar **`/continuar`**.)

## Se o ClineSR mostrar "aborted"

Quase sempre é o clone ou a tarefa antigos mandando usar contêiner/banco de servidor/WSL (bloqueados pela TI). Siga a seção "Se aparecer aborted" de `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`: `git pull --ff-only origin main`, tarefa nova no Cline, conferir as regras globais do Cline, terminal em PowerShell (nunca `bash`/`sh`/`wsl` no Windows).

## Onde paramos (28/10/2026)

- **Na `main`:** Planos 1, 2, 3A, 3, **3B (banco SQLite embutido, PRs #18 e #19)**, **4A** (sincronização periódica, PR #14), as **Tarefas 1 a 3 do Plano 4** (Auditoria, PR #15), o **Plano 9 completo (CI, cabeçalhos de segurança, CSRF, sessão expirada, pacote em modo produção; PR #25)**, a **Tarefa 0 do Plano 14 e a nova regra dos hooks (PR #26)**, a **correção preventiva do changelog do Data Center (PR #28)** e a **referência local do Jira Data Center + ideias de cards (PR #27)**. Backend: 850+ testes, 0 falhas; frontend: 74. **CI no GitHub ativo** (jobs `backend`, `frontend`, `guarda`, `varredura`, `guarda-windows`).
- **Nenhum acesso ao Jira foi feito** em todo o projeto. O primeiro ciclo real é do usuário (Plano 14a).
- **Plano 4 (Auditoria), Tarefas 4 a 9 executadas** na branch `feat/auditoria-tarefas-4-a-9` (PR pendente): sinais de labels, Task × Review, "no prazo" e "no prazo ajustado", calculador, leitura no SQLite, API (`/api/auditoria/sinais`, `/api/auditoria/historico/{key}`, `/api/cards/task-review`), telas Auditoria e Divergência, cartões `AU-01` a `AU-03` e `DV-01` no catálogo (contrato do 17a), modificador "em lote (N)" (I-15) e `docs/analise/diferencas-esperadas-auditoria.md`. Estado, rulings e o que ficou por fazer (captura visual, dados de demonstração para cada sinal) em `docs/superpowers/progresso/2026-10-25-plano-04-auditoria.md`. **Pendência aberta: confirmar a base legal com o DPO antes de usar a auditoria com dados reais; até lá só fixtures e demonstração.** Os planos 5 a 16 e suas specs estão escritos e ajustados à nova plataforma (SQLite, sem Docker); os Planos 8, 9, 12 e 13 foram reescritos.
- **Plano 5, bloco 5a (Tarefas 1 a 9) executado** na branch `feat/cards-trabalho-5a` (PR pendente; estado e rulings em `docs/superpowers/progresso/2026-10-25-plano-05-cards-trabalho-e-prazos.md`): migração `V2` (campos de trabalho), `WorkStore`/`IssueSnapshot`/`Fluxo`, kit `CardPage`/`Filtros`/`useCard`, telas Abertas, Negligenciadas (com "último movimento real" e filtro "sem movimento real", I-06), Fechadas e Metas, regra única de "no prazo" nas três telas e `docs/analise/diferencas-esperadas-trabalho-e-prazos.md`. Cartões `AB-01/02/04 a 09`, `NG-01 a 06`, `FE-01 a 12` e `MT-01 a 14` no catálogo. Backend 1125 testes e frontend 193, 0 falhas. Pendências: `metas.yaml` real a criar na implantação (copiar de `config/metas.exemplo.yaml`); captura visual das telas não feita (sem navegador); `TempoEmStatusRoutes` ainda não usa `FiltrosDeCard`. Também corrigido o Minor do Plano 4 (negação CSRF gravava a rota crua).
- **Plano 5, bloco 5b (Tarefas 10 a 14) executado** na branch `feat/cards-trabalho-5b` (PR pendente; estado e rulings na mesma seção "Bloco 5b" do arquivo de progresso do Plano 5): Negativas (eventos pela data do changelog; `NE-01` a `NE-08` e `NE-10`), Riscos (pesos em `risco.yaml`; `RI-01` a `RI-03`), Previsão (Monte Carlo com taxa de chegada, parâmetros em `previsao.yaml`; `PV-01` a `PV-06`) e Épicos (só com amostra própria; `EP-01`), mais as quatro telas e a seção 5b de `docs/analise/diferencas-esperadas-trabalho-e-prazos.md`. A função única `SerieSemanal` (`trabalho/SerieSemanal.kt`: entradas, saídas e canceladas por semana de Manaus) é a que o cartão "Fluxo da semana" do 17b deve reutilizar. Backend 1220 testes e frontend 231, 0 falhas. Pendências: criar `risco.yaml` e `previsao.yaml` a partir dos modelos na implantação (além do `metas.yaml`); NE-09 ("maior tempo" por status) fica no Plano 10; "pela capacidade" e "Atrasa?" do PV-06 na Fase 6; captura visual das telas não feita (sem navegador). O 6a é o próximo.
- **Plano 17a (contrato do cartão) executado** na branch `feat/contrato-do-cartao` (PR pendente; estado e rulings em `docs/superpowers/progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md`): catálogo de cartões, drill genérico `GET /api/cartoes/{id}/tasks`, teste de contrato, componentes `Cartao` e `GavetaDeTasks`, Tempo em status e Sincronização migrados e tela Mapa de cartões. **Todo cartão novo dos próximos planos se registra no `catalogoPadrao()` e usa esses componentes** (regras abaixo).
- **Plano 17b (cards de fluxo e gestão) executado** na branch `feat/cards-de-fluxo-e-gestao` (PR pendente, por ordem do usuário antes do 14b; rulings e pendências em `docs/superpowers/progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md`): Fluxo da semana (`FX-01/02`), Envelhecimento por etapa (`AW-01/02`), Bloqueios entre tasks (`BQ-01` a `BQ-05`), Controle do ciclo e tendência (`CT-01/02`, seção de Fechadas) e a aba Cruzamentos (`CZ-01`). Sem migração. A demonstração ganhou dois bloqueios entre tasks abertas. Pendências: aceite do gestor card a card, capturas visuais (sem navegador), calibração com dados reais no 14b; a verificação `BLOQUEADA_SEM_CAUSA` (`BloqueiosAnalise.semCausa`) e os eixos de campos do dicionário do Cruzamentos (`CruzamentosCalculator.DICIONARIO_PENDENTE`) esperam os Planos 7b e 10.
- O repositório `tlmacedo/dashboardJira` é **PÚBLICO desde 26/10** (decisão do usuário). O histórico antigo tem 3 usernames reais (aceito e documentado); nada novo com dado real pode entrar, então a varredura antes do push é ainda mais importante.
- Alerta do GitGuardian em `SettingsTest.kt` (3 "Generic Password"): falso positivo (valores fictícios de teste de mascaramento). O usuário pode marcá-lo como tal no painel do GitGuardian.

## Ações do usuário pendentes (28/10)

1. **Proteção da `main`** no GitHub exigindo os checks `backend`, `frontend`, `guarda` e `varredura` (passo a passo em `docs/operacao/ci.md`, seção 5). `guarda-windows` entra como recomendado (leva ~18 min).
2. Criar o segredo `VARREDURA_NOMES` (usernames reais do time para a varredura de CI) e conferir "Require approval for all outside collaborators" (`docs/operacao/ci.md`).
3. Marcar como falso positivo no painel do GitGuardian os alertas "Generic Password" em `SettingsTest.kt` e `guard.sh` (valores fictícios / linha de código).
4. Ler `docs/superpowers/relatorio-2026-10-28.md` (relatório da noite) e decidir sobre as ideias de `docs/superpowers/ideias-de-cards-a-partir-da-referencia.md` (pontos de parada PP1 a PP4).

## O que o usuário precisa ter pronto (a IA não consegue sozinha)

- **Fase 0:** acesso para instalar JDK 21, Node 22 e pnpm; `gh auth login` feito; se houver proxy/inspeção TLS, endereço do proxy/espelho e o arquivo `.pem` da CA da empresa.
- **Clone:** `git clone https://github.com/tlmacedo/dashboardJira.git painel` (ação do usuário; a pasta `painel` não tem "jira" no nome, e o hook barra comandos da IA que citem URL com "jira" enquanto a flag estiver desligada).
- **Plano 9:** proteção da `main` no GitHub (exigir os 4 checks), se o plano da conta permitir.
- **Plano 14a:** URL e token do Jira (em `app/.env`), a flag ligada à mão, `papeis.yaml` com ao menos um GERENTE, `status_map.yaml` e `jira_campos.yaml` reais.
- **Plano 13:** provedor OIDC, máquina de produção, proxy/TLS e cofre/arquivo de segredos.
- **Branches conhecidas** (ignorar no `git branch -r --no-merged`): nenhuma pendente em 27/10/2026; se aparecer uma branch `ccr-*` ou `claude/*` antiga, confirme que está mesclada e peça ao usuário para apagá-la.

- **Skills do Claude no Cline (teste):** inventário em `SKILLS_INSTALADAS_CLAUDE.md`; 45 cópias em `skills-para-cline/` e `scripts/copiar-skills-para-cline.ps1`. Rode o script no PC da empresa e registre em `docs/ferramentas-claude-para-o-cline/skills.md` se o Cline reconheceu (não bloqueia nenhum plano).

## Próximos passos, em ordem

1. **Fase 0** (PC da empresa; o Plano 9, a Tarefa 0 do Plano 14 e o **17a** já estão feitos) → **antes da flag** (Claude Code): Plano 4 (Tarefas 4 a 9, já executadas; PR pendente) → 5a → 5b → 6a → 6b → 7a → 7b → 10 → 11 → 17b → 8 → 12 → 13 → 15 (parte de código) → **o usuário liga a flag em `app/.env`** → **depois da flag** (ClineSR): Plano 14a → 14b e as pendências com efeito fora do repositório. O mapa completo, com o que fica de cada lado, está em `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`. O **Plano 16** (escrita no Jira pelo painel) é futuro e só com decisão do usuário. Justificativa e dependências: índice dos planos.
2. Cada plano/bloco = uma branch `feat/<nome>` a partir da `main` atualizada = um PR, mesclado ao fim conforme `docs/superpowers/fluxo-de-entrega.md`; só então o próximo começa.
3. Migrações: hoje só existe a `V1`; a próxima é a `V2` (confirmar com `ls app/backend/src/main/resources/db/migration`); nunca editar migração já mesclada.

## Regras do contrato do cartão (spec do Plano 17, seção 6; valem para os Planos 4, 5, 6, 7 e 10 sem reescrevê-los)

- **Planos 5, 6 e 7 (todas as telas):** cada cartão se registra no `CatalogoDeCartoes` (`cards/contrato/CatalogoPadrao.kt`), usa o componente `Cartao` (`shared/Cartao.tsx`) e a `GavetaDeTasks`; os textos "título", "o que mostra" e "como calcula" entram junto, em pt e en. O teste `drillTemOMesmoTamanhoDoCard` do 5a passa a ser o `ContratoDeCartoesTest`, e `todaRotaDeCardEstaNoRegistro` acusa rota `/api/cards/**` sem cartão registrado.
- **Plano 5a, Negligenciadas:** acrescentar a coluna "último movimento" (status, responsável, vencimento, labels, resolução; I-06) e o filtro "sem movimento real"; não substitui NG-01.
- **Plano 4, Tarefas 4 a 9:** modificador informativo "em lote (N)" nos sinais (I-15): mesma pessoa, mesmo campo, mesmo minuto, a partir de N issues (N em `auditoria.yaml`, padrão 5); indício, não prova; só coordenação e gerente.
- **Plano 7b, Qualidade dos dados:** verificação informativa `LABEL_SEM_CLASSIFICACAO` (I-12); `PAI_CONCLUIDO_COM_FILHAS_ABERTAS` (I-14) só se a 1ª conexão confirmar o uso de sub-tarefas.
- **Plano 10, Tarefas 4 e 6:** filtros rápidos nomeados em `filtros_rapidos.yaml` (I-27) e busca por chave na gaveta universal (I-26; chave fora da visão responde 404). A gaveta do Plano 10 **reutiliza** a `GavetaDeTasks` e estende o `LinkJira` (`cards/contrato/LinkJira.kt`) em vez de criar outros.

## Regras que não podem ser esquecidas

- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (decisão do usuário em 25/10): `false` = bloqueado para todos (app, agendador, MCP, rede); `true` = liberado. Só o usuário muda a flag; hook em `.claude/hooks/guard.sh`.
- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)".
- **Contêineres, servidor de banco e WSL são proibidos** (bloqueados pela TI): banco SQLite em arquivo (`DATABASE_PATH`, padrão `data/painel.db`), testes com SQLite real em arquivo temporário; JDK 21 e Node/pnpm; subida por `app/iniciar.sh` ou `app/iniciar.ps1`. Migração única `V1__base.sql`. O compose e os gatilhos de Docker já foram removidos (PR #19).
- Conversar em pt-BR; comentários de código em pt-BR.
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml`); usar `.exemplo`/`.demo`. `Filtros_Status_tool.txt` (usernames reais) está no `.gitignore`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões.
- **Hooks e a flag (desde 28/10, PR #26):** os hooks (`.claude/hooks/guard.sh`, `.clinerules/hooks/PreToolUse` e `PreToolUse.ps1`) leem a flag **só da linha da flag em `<raiz>/app/.env`** (ambiente do processo ignorado; ausente/ilegível = desligada), igual ao painel. Com a flag desligada bloqueiam apenas **URL/host de Jira** (rótulo do host com `jira`; `jira`+`sidia` na mesma URL; `*.atlassian.net` com `sidia`); documentação em `atlassian.com` e a palavra "jira" solta passam. Continuam barrados: MCP da Atlassian, variáveis `JIRA_*` de conexão, ligar a flag por texto/arquivo, exibir token, ler/escrever `.env`. Risco residual aceito: host montado em tempo de execução, IP direto, redirects. O `.ps1` é provado no CI do Windows (job `guarda-windows`, PowerShell 5.1 e 7).

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
- Aviso automático de segurança sobre `legacy/backend/.../routes.py` (webhook sem segredo): código legado que sai na Fase 8.
- `legacy/.env.example` está modificado pelo usuário (fuso Manaus) e fora de commit.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Provedor SSO/OIDC real e infraestrutura de implantação; validação com o DPO (Integridade, auditoria, retenção).
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir.
- Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, skills `novo-card` e `convencoes-projeto`, MCP Playwright e context7 (precisa autorização no `/mcp`).
