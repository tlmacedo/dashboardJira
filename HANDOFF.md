# Retomada: Painel de Squads (atualizado em 29/10/2026)

> **Aviso ao usuário (regra 8 do `CLAUDE.md`):** a regra manda avisar antes de usar o modelo Opus, e só em revisão final de código sensível a segurança. Nos PRs #43, #45, #48 e #49 a revisão final foi feita em Opus com você ausente, e este aviso é depois do fato. Para objetar: diga na conversa que quer outra revisão (padrão ou sua) dos pontos de segurança desses PRs, e/ou edite a regra 8 do `CLAUDE.md` (só você altera o `CLAUDE.md`); nenhuma IA muda a regra.

## COMECE AQUI (ClineSR, PC da empresa, clone do GitHub)

1. Clone o projeto (qualquer nome de pasta: o hook **não bloqueia a palavra "jira"**, só URLs e hosts de Jira) e use o Windows 11 com PowerShell. Instale e valide JDK 21, Node 22 e pnpm; **não há Docker nem banco a instalar** (a empresa os bloqueia; o banco é um arquivo SQLite).
2. Leia, nesta ordem: `.clinerules/00-regras-do-projeto.md` → `docs/guia-execucao-outra-ia.md` → `docs/superpowers/fluxo-de-entrega.md` (revisão, validação, commit, push, PR, merge e volta à `main`) → `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` → `docs/ferramentas-claude-para-o-cline/README.md` e `processo-completo.md` → `CLAUDE.md` → este arquivo → `docs/superpowers/relatorio-final-antes-da-flag.md` → o índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` → o roteiro `docs/superpowers/plans/2026-10-28-roteiro-antes-e-depois-da-flag.md`.
3. **Execute a Fase 0** (`docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`): proxy, certificado da empresa, espelhos, testes, subida e teste do hook. Depois dela o próximo passo é o **Plano 14a**, **quando o usuário ligar a flag** (ver "Primeira conexão" abaixo).
3b. **Antes da primeira subida e de qualquer pedido da flag:** `docs/primeiros-passos-clinesr.md` (checklist de ajustes no PC e as perguntas que você faz ao usuário).
4. **Dúvida ou falta de direção = pergunta ao usuário** com opções (recomendada primeiro, cada opção com porquê e consequência). Workflows do Cline em `.clinerules/workflows/`; skills de terceiros: `scripts/instalar-skills-cline.ps1`.
5. Configs reais (`app/.env`, `papeis.yaml`, `status_map.yaml`, `jira_campos.yaml`, `calendario.yaml`, `vinculos.yaml`, `equipes.yaml`, `ausencias.yaml` e os demais) **não** estão no GitHub: o usuário as cria a partir dos `.exemplo` (Fase 0, Tarefa 7); a IA nunca as lê, edita nem commita.
6. **Merge:** por decisão do usuário (27/10/2026), a IA mescla a PR de cada plano ou bloco sozinha **quando as 7 condições da seção 5 do fluxo de entrega forem verdadeiras**; senão pergunta. Nunca `--admin`, nunca push direto na `main`.

(Quando o Claude Code for usado: abrir com `claude --advisor opus --subagents haiku` e digitar **`/continuar`**.)

Se o ClineSR mostrar "aborted": quase sempre é clone ou tarefa antigos mandando usar contêiner, banco de servidor ou WSL (bloqueados pela TI). Siga a seção "Se aparecer aborted" do adendo de plataforma (`git pull --ff-only origin main`, tarefa nova no Cline, terminal em PowerShell).

## Estado em 29/10/2026

- **O lado "antes da flag" está pronto e na `main`** (roteiro A1 a A16): PRs #31 a #50. O que foi feito, os problemas que as revisões acharam, as decisões tomadas sozinho (com a reversão), o que ficou de fora e a lista de ações do usuário estão em `docs/superpowers/relatorio-final-antes-da-flag.md`. Cada plano tem o seu arquivo de progresso em `docs/superpowers/progresso/` com os `Ruling:` (decisão, motivo, como reverter), as pendências e os Minor adiados: **é lá que está o detalhe; este arquivo não o repete.**
- **Nenhum acesso ao Jira foi feito em todo o projeto.** A flag está desligada em todo lugar. O primeiro ciclo real é do usuário (Plano 14a).
- **O que existe:** backend Kotlin/Ktor (`app/backend`) e frontend React/TypeScript (`app/frontend`) sobre SQLite embutido, migrações **V1 a V7** (a próxima livre é a V8; nunca editar migração mesclada); sincronização periódica com cursor, reconciliação e retenção; catálogo de cartões com contrato (número do cartão = total do drill) e a tela Mapa de cartões; telas de trabalho e prazos, negativas, riscos, previsão, épicos, pessoas, capacidade, planejamento, checklist × review, integridade, distribuição e rebalanceamento (**só em modo sombra: nada é escrito no Jira**), governança, Central de atenção, Meu squad, Meu painel e Meus dados; auditoria completa; SSO/OIDC testado com emissor falso; operação (logs JSON, prontidão, métricas em porta interna, alertas, backup com restauração testada); pacote de produção com versão e SHA-256, atualizar e reverter ensaiados; guias do painel, FAQ e treinamento por papel; CI no GitHub (`backend`, `frontend`, `guarda`, `guarda-windows`, `varredura`).
- **Plano 4 (Auditoria):** Tarefas 1 a 3 (PR #15) e Tarefas 4 a 9 (PR #32) na `main`. **Pendência: confirmar a base legal com o DPO antes de usar a auditoria com dados reais**; Auditoria e Integridade ficam atrás da **trava do DPO** (`DASHBOARD_DPO_ACEITE` com a referência do parecer; fechada por padrão). Só o usuário liga o aceite, depois do parecer, e anota a data aqui.
- **Fora do alcance do "antes"** (seção 3 do roteiro): Fase 0, Plano 14a e 14b, implantação no SO da empresa (Plano 13: T1, T7, T8, T11 a T13), ativos de marca e sessões de treinamento (Plano 15: T1, T9, T10, T11), Plano 16 (escrita no Jira, só por ordem expressa).
- O repositório `tlmacedo/dashboardJira` é **PÚBLICO desde 26/10**. O histórico antigo tem 3 usernames reais (aceito e documentado); nada novo com dado real pode entrar. Rode `sh scripts/ci/varrer-dados-pessoais.sh --base origin/main` antes de todo push.

## Como subir

`app/iniciar.sh` (`cd app && sh iniciar.sh`; PowerShell: `app/iniciar.ps1`, com `.\iniciar.ps1`); painel em `http://127.0.0.1:8080`, em modo desenvolvimento (sem login, faixa vermelha). Dados fictícios: `DASHBOARD_DEMO_DATA=true` (só com a flag desligada). Para apagar os dados locais, pare o painel e apague `app/data/painel.db`. Testes: `cd app/backend && ./gradlew test` e `cd app/frontend && pnpm test` (nunca os dois ao mesmo tempo: a máquina fica sob carga). O Gradle do backend já traz `-Xmx3g` em `app/backend/gradle.properties` (sem isso a compilação dos testes estoura a memória); não o reduza. Detalhe e todas as variáveis: `app/README.md`; produção: `docs/implantacao.md`.

## Primeira conexão (quando o usuário ligar a flag)

1. **Antes de ligar a flag, ainda desligada:** o `app/.env` deve ter as credenciais da instância e os **tetos pequenos** `SYNC_MAX_ITENS=50` e `SYNC_MAX_REQUISICOES=5` (os padrões do código são 50000 e 2000: sem os tetos a primeira carga sai sem limite). Eles só sobem depois da primeira carga conferida (runbook, Etapa 1).
2. Só então o **usuário** liga a flag à mão no `app/.env` e avisa. Nenhuma IA a liga. Ver "A regra da flag".
3. Siga `docs/jira/checklist-primeira-conexao.md` (cinco passos mais os passos extras: ids de `papeis.yaml`, vocabulário de prioridade e criticidade, ordem do fluxo e resoluções negativas, formato do worklog, formato das estimativas no changelog, Reporter como PL), o runbook `docs/jira/runbook-sincronizacao.md` e o Plano 14a. Para diagnosticar conexão e rede (o usuário roda): `docs/jira/diagnostico-conexao.ps1`.
4. Na primeira subida com a flag ligada o painel **apaga sozinho só as linhas fictícias** da demonstração (antes de qualquer tentativa de sincronização) e mostra "Aguardando a primeira carga do Jira"; banco com issues e sem tentativa de sincronização é recusado (`BANCO_DESCONHECIDO_SEM_SINCRONIZACAO`).
5. **A migração V7 zerou o cursor da sincronização**: a primeira carga é uma recarga completa (changelog e worklog dos últimos 24 meses), em vários ciclos se for preciso. Até lá os sinais de estimativa e de worklog retroativo aparecem sem dado.
6. Os pontos onde a suposição offline mais provavelmente falha (R1 a R22) estão na seção 4 do roteiro; as regras reversíveis a conferir (usernames entre aspas no JQL, `resolved < dia seguinte`, `key in (...)` com chave apagada, tipo e direção do vínculo Blocks, fuso do `updated`) estão em `docs/jira/matriz-compatibilidade.md`.

## A regra da flag

- `JIRA_LIVE_ENABLED` é a **única** trava de acesso ao Jira e **vale somente dentro de `app/.env`** (variável de terminal ou do sistema é ignorada; linha ausente = desligada). Desligada: proibido para todos (app, agendador, MCP, rede). Ligada: liberado. **Só o usuário muda a flag, à mão.** Os scripts do repositório respeitam a trava: `scripts/tempo_em_status.py` lê só a linha da flag em `app/.env` e recusa (código 2) antes de qualquer rede; `ScriptsNaoAcessamJiraSemTravaTest` falha se surgir script com cliente de rede que cite o Jira sem a checagem (correção da auditoria final, PR #52, **feita**).
- **Sem exceção fora da trava:** o app Python antigo (`client.py` e `teams.py` acessavam o Jira sem a flag) foi removido em 29/10/2026 (Plano 8, T9); o código só existe no branch (ou tag) `legacy-final`. Não restaure nem rode esse código contra um Jira real.
- Os hooks (`.claude/hooks/guard.sh`, `.clinerules/hooks/PreToolUse` e `PreToolUse.ps1`) leem a flag só dessa linha e, com ela desligada, bloqueiam URL e host de Jira (não a palavra solta); `atlassian.com` é liberado. Continuam barrados: MCP da Atlassian, variáveis `JIRA_*` de conexão, ligar a flag por texto ou arquivo, exibir token, ler ou escrever `.env`. Risco residual aceito: host montado em tempo de execução, IP direto, redirects. O `.ps1` do hook é provado no CI do Windows; **os `.ps1` do pacote e da implantação nunca foram executados** (D14 do roteiro).
- Documentos não escrevem a linha da flag com valor verdadeiro (o hook barra): escreva "ligada" ou "valor verdadeiro".

## Ações do usuário pendentes

A lista completa e em ordem está na seção (f) do relatório final. Em resumo: proteção da `main` com os quatro checks obrigatórios (`backend`, `frontend`, `guarda`, `varredura`; `guarda-windows` recomendado, `docs/operacao/ci.md`, seção 5); segredo `VARREDURA_NOMES`; marcar como falso positivo os alertas do GitGuardian em `SettingsTest.kt` e `guard.sh`; ativos de marca; parecer do DPO e depois o aceite; confirmar as decisões da seção (c) do relatório (a T3 do Plano 11 e a V7 primeiro); limpar os worktrees locais antigos em `.claude/worktrees/`; fornecer os YAML reais; Fase 0; por último, a flag.

## Confirmações do usuário (preencha a resposta e a data)

Decisões que as IAs tomaram sozinhas pela regra "(Recomendada)" e que mudam dado guardado, quem vê o quê ou o que sobe. Texto, motivo e reversão de cada uma: `docs/superpowers/relatorio-final-antes-da-flag.md`, seção (c). Resposta esperada: "confirmo" ou "reverter" (e o que fazer). Enquanto estiver "pendente", vale o que o código faz hoje.

| # | Decisão | Resposta | Data |
|---|---|---|---|
| 1 | Plano 11, T3 opção A e migração V7 (guarda estimativas e criação do worklog; zera o cursor; remove `person`) | pendente | |
| 2 | Qualidade dos dados aberta à coordenação | pendente | |
| 3 | Gaveta da task só pelo responsável atual | pendente | |
| 4 | `OIDC_DOMINIOS_PERMITIDOS` obrigatória nas estratégias `parte_local_do_email` e `conta_windows` | pendente | |
| 5 | Aceite do DPO fechado até o parecer | pendente | |
| 6 | Remoção do `legacy/` (o "sim" ao documento de equivalência) | sim, remover ("Delete o /legacy"); feita, código no branch (ou tag) `legacy-final` | 29/10/2026 |
| 7 | "Quem consultou quem" fora do `access_log` | pendente | |
| 8 | Squad Leader sem task sem responsável | pendente | |
| 9 | Distribuição e Rebalanceamento só em modo sombra | pendente | |
| 10 | Expurgo automático dos dados fictícios e recusas de banco | pendente | |

## Regras do contrato do cartão (spec do Plano 17, seção 6)

- Todo cartão novo se registra no `CatalogoDeCartoes` (`cards/contrato/CatalogoPadrao.kt`, por `catalogoPadrao()`), usa o componente `Cartao` (`shared/Cartao.tsx`) e a `GavetaDeTasks`; os textos "título", "o que mostra" e "como calcula" entram junto, em pt e en. O drill é o genérico `GET /api/cartoes/{id}/tasks?segmento=`; o `ContratoDeCartoesTest` prova número = total do drill, e `todaRotaDeCardEstaNoRegistro` acusa rota `/api/cards/**` sem cartão.
- Rota nova em `/api` entra no `InventarioDeRotasTest` (papel, sensibilidade e registro de acesso) e, se for módulo novo, em `MODULOS_DA_API`.
- Nunca chave de issue, pessoa ou texto livre do Jira no `access_log`: o rótulo da rota é sempre o modelo casado (`modeloDaRota`) e os módulos sensíveis gravam só quem consultou.
- Sem ranking de pessoas: listas de pessoas em ordem alfabética, "no prazo" e "no prazo ajustado" lado a lado.

## Regras que não podem ser esquecidas

- Toda aprovação do usuário vem como **pergunta com opções**, a recomendada primeiro e marcada "(Recomendada)". Conversar em pt-BR; comentários de código em pt-BR.
- **Contêineres, servidor de banco e WSL são proibidos** (bloqueados pela TI): SQLite em arquivo (`DATABASE_PATH`), testes com SQLite real em arquivo temporário, `PlataformaSemDockerTest` barra o retorno. O compose e os gatilhos de Docker foram removidos (PR #19).
- Não editar nem commitar `.env` nem as configs reais (`papeis.yaml`, `equipes.yaml`, `ausencias.yaml`); usar `.exemplo`/`.demo`. `Filtros_Status_tool.txt` (usernames reais) está no `.gitignore`.
- `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` são os padrões do desenvolvimento; o pacote instalado sobe em produção.
- **Identidade e papéis:** em `papeis.yaml`, `id` = username do Jira = `assignee` (minúsculas, `[a-z0-9._-]`, 1 a 64; reservados `dev`, `system`, `admin`, `root`, `anonymous`). USUARIO vê só o próprio; SQUAD_LEADER o próprio e o squad (exige `squad`); COORDENADOR e GERENTE veem todos. Issues de quem não está no arquivo só aparecem para coordenação e gerente.
- **Acesso à API:** em `/api` nega por padrão; só são públicas as rotas de `ROTAS_PUBLICAS` (`security/AccessControl.kt`): `/api/health`, `/api/health/pronto`, `/api/meta`, `/auth/login`, `/auth/callback` e `/auth/logout` (as de `/auth` só existem em produção, com OIDC). Principal sem papel vigente = 403. Rota nova entra no `InventarioDeRotasTest`.
- Auditoria: só Reporter (PL), coordenadores e gerente, ou Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso (spec, seção 7). Concluída = categoria "done" e `resolution`; fuso America/Manaus; dias úteis por padrão.

## Pendências abertas

- **Auditoria final da `main` (PR #52, feita):** trava da flag no ponto de rede de `scripts/tempo_em_status.py`, provas de root do `como_servico` rodando no CI por `sudo -n` (falham, não pulam, com `CI=true`), mensagens de configuração sem eco do valor lido, orçamento do bundle no CI e `diagnostico-conexao.ps1` com caixa exata. Detalhe e Minor não corrigidos (M-1 `access_log` com texto cru de filtro, M-2 modo dev com escuta aberta, M-5 cadeia de suprimentos, M-7 `/api/meta`, M-8 lint): `docs/superpowers/progresso/2026-10-29-auditoria-final.md`. **Lacunas da varredura de scripts:** rede montada por concatenação de texto, `eval`/`__import__`, binários não listados, ordem dentro de ramos condicionais; `tests/` e `test*` ficam fora; `DASHBOARD_DPO_ACEITE_REF` e `OIDC_HOSTS_PERMITIDOS`/`OIDC_DOMINIOS_PERMITIDOS` sem caso próprio no teste de mensagens.

- **Rulings reversíveis a conferir na 1ª conexão** e **Minor adiados** de cada plano: nos arquivos de `docs/superpowers/progresso/` (seções "Minor adiados" e "Depois da flag / com o usuário"). Não os copie para cá.
- `legacy-final` foi criado como **branch** em 29/10/2026 porque o push de tag é barrado nesta sessão; não apague o branch. Para converter em tag: `git tag legacy-final origin/legacy-final && git push origin legacy-final` e só depois apague o branch (nomes iguais geram ambiguidade). Para ler o código antigo: `git fetch origin legacy-final` e `git show origin/legacy-final:legacy/<arquivo>`.
- O aviso automático de segurança sobre o `routes.py` do app Python antigo (webhook sem segredo) some com a remoção; o código antigo fica só no branch (ou tag) `legacy-final`.
- Logo limpo (SVG ou PNG transparente) e manual de marca da Sidia: hoje há só um recorte e as cores públicas do site.
- Skills do Claude no Cline (teste, não bloqueia nenhum plano): inventário em `SKILLS_INSTALADAS_CLAUDE.md`, cópias em `skills-para-cline/`; rode `scripts/copiar-skills-para-cline.ps1` no PC da empresa e registre em `docs/ferramentas-claude-para-o-cline/skills.md` se o Cline as reconheceu.
- Versão nova do Impeccable disponível (v4.7.0; instalada v4.3.1); só atualizar se o usuário pedir. Recomendações de automação ainda não criadas: subagentes `jql-validator` e `auditoria-fixtures`, MCP Playwright e context7 (precisa autorização no `/mcp`).
