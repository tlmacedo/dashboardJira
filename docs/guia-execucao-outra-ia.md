# Guia de execução para outra IA (ClineSR e similares)

Objetivo: quem continuar a execução dos planos (a partir da Fase 0 e do Plano 9; o Plano 4A já está na `main`) deve ter **o mesmo entendimento de projeto e as mesmas regras** de quem os escreveu (o Claude Code). Este guia substitui o que o Claude tem de ferramentas próprias (subagentes, skills de terceiros, revisores) e que o Cline não tem do mesmo jeito. O porte dessas ferramentas (workflows, skills do projeto e o hook `PreToolUse` do Cline) está em `docs/ferramentas-claude-para-o-cline/`. Nada aqui contradiz o `CLAUDE.md`; havendo conflito, vale o `CLAUDE.md`, depois este guia, depois o plano.

**Workflows do Cline** (`.clinerules/workflows/`, digite `/nome.md`): `/continuar.md` (início de sessão), `/executar-plano.md` (seção 4 deste guia), `/revisar-tarefa.md` (seção 5), `/fim-de-branch.md` (seções 4, 6 e 8), `/perguntar-ao-usuario.md` (seção 7) e `/escrever-plano.md`.

**Executor:** o **ClineSR** (variante Samsung do Cline, com o modelo Gauss 3.7) num **PC com Windows 11**. Comandos no PowerShell (equivalentes na seção 3); prepare o PC e rode o teste do hook (`.clinerules\hooks\test-PreToolUse.ps1`) seguindo `docs/ferramentas-claude-para-o-cline/README.md` e `hooks.md` antes da primeira tarefa. Com o Gauss 3.7, trabalhe em tarefas pequenas, releia o plano a cada tarefa e releia as regras no original em vez de resumi-las.

**Dúvida ou falta de direção SEMPRE vira pergunta ao usuário** com 2 a 4 opções: a recomendada primeiro, marcada "(Recomendada)", e cada opção com o porquê e a consequência. Nunca siga no palpite nem pergunte em prosa.

## 1. Ordem de leitura (obrigatória antes de escrever código)

0. `docs/superpowers/fluxo-de-entrega.md` (revisão, validação, PR, merge) e `docs/superpowers/plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md` (SQLite, sem Docker); num PC novo, `docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md`.
1. `.clinerules/00-regras-do-projeto.md` (proibições e decisões).
2. `CLAUDE.md` e `PRODUCT.md` (regras sempre válidas e produto).
3. `HANDOFF.md` (estado atual e próximos passos).
4. A spec do projeto `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` e a spec da fase que vai executar (`docs/superpowers/specs/2026-10-25-spec-fase-NN-*.md`).
5. O índice `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` e **o plano da vez**.
6. Para qualquer tarefa que mexa em segurança, leia antes os arquivos de `app/backend/src/main/kotlin/br/sidia/painel/security/` e o plano 3 (`2026-10-24-plano-03-tempo-em-status.md`), que é o exemplo completo de uma fatia (API + domínio + banco + tela).

## 2. O que já existe (não refaça)

Planos 1, 2, 3A, 3, 3B (saída do Docker), 4A e as Tarefas 1 a 3 do Plano 4 estão **mesclados na `main`** (PRs #5 a #19). Há backend Ktor (`app/backend`, pacote `br.sidia.painel`), frontend React (`app/frontend`), SQLite em arquivo com Flyway (migração única V1; a próxima é a V2), sem Docker. Itens-chave:

| Peça | Onde | Regra a respeitar |
|---|---|---|
| Trava do Jira | `jira/LiveGuard.kt`, `jira/JiraClientFactory.kt` | Só a fábrica cria clientes; com `JIRA_LIVE_ENABLED=false` há 0 requisições (há teste que varre `src/main`) |
| JQL | `jql/JqlBuilder.kt`, `JqlValues.kt`, `CardQueryFactory.kt` | JQL só pelo `JqlBuilder`; valores suspeitos são **rejeitados, nunca escapados**; usernames entre aspas |
| Papéis e visão | `security/{Role,RoleDirectory,Visibility,AccessControl,Authenticator,Usernames}.kt` | Nega por padrão em `/api`; papéis só do `RoleDirectory`; `id` da pessoa = username do Jira em minúsculas |
| Auditoria de consulta | `db/AccessLog.kt` | Toda rota de dados grava `access_log` (inclusive as negadas); falha ao gravar = 503 sem dados |
| Calendário | `time/BusinessCalendar.kt` | Fuso America/Manaus, dias úteis por padrão, corridos opcionais |
| Domínio do Tempo em status | `domain/*` | Concluída = categoria done **e** `resolution`; DONE não entra nas somas |
| Sincronização (simulada) | `sync/{ChangelogParser,IssueStore,Syncer,SanitizadorRaw}.kt` | `issue` única por `(instance, key)`; `change_event` só guarda valor dos campos permitidos; `raw` sem dados pessoais |
| API de exemplo | `cards/TempoEmStatus*` | Copie este padrão para cards novos |
| Tela de exemplo | `app/frontend/src/screens/TempoEmStatus/` | Copie o padrão (filtros na URL, KPIs clicáveis, gaveta, CSV seguro) |

## 3. Ambiente e comandos

- Backend: `cd app/backend && ./gradlew test` (Kotlin 2.2, Ktor 3.3, SQLite; só JDK 21, **sem Docker**).
- Frontend: `cd app/frontend && pnpm install && pnpm vitest run && pnpm build` (pnpm; dependências em versões fixas).
- Subir tudo: `app/iniciar.ps1` (Windows) ou `app/iniciar.sh`; ver `app/README.md`. Variáveis em `app/.env` (copiado de `app/.env.example`); **a flag do Jira vale só ali**.
- Flags: `JIRA_LIVE_ENABLED=false` e `DASHBOARD_DEV_MODE=true` por padrão. `DASHBOARD_DEV_MODE=true` = sem login, principal `dev` com papel GERENTE, faixa vermelha na tela.
- Antes de qualquer plano, no PC da empresa, execute a Fase 0: `docs/superpowers/plans/2026-10-27-plano-00-pc-da-empresa.md` (proxy, certificado da empresa, espelhos, testes, subida).
- **Windows 11 (PowerShell):** backend `cd app\backend; .\gradlew.bat test`; frontend `cd app\frontend; pnpm install --frozen-lockfile; pnpm vitest run; if ($LASTEXITCODE -eq 0) { pnpm build }`. No PowerShell 5.1 não há `&&`: use `;` e confira `$LASTEXITCODE`. `~` = `%USERPROFILE%`. Lista completa em `docs/ferramentas-claude-para-o-cline/README.md`.

## 4. Como executar um plano (substitui as skills/subagentes do Claude)

Para cada plano, em ordem:

1. `git checkout main && git pull`; `git checkout -b feat/<nome-do-plano>`. Confira com `git branch -r --no-merged origin/main` e `gh pr list` se há trabalho fora da `main`; havendo, pergunte ao usuário.
2. Crie o arquivo de progresso `docs/superpowers/progresso/<arquivo-do-plano>.md` (versionado, ao contrário do ledger local do Claude). Primeira linha: `# Progresso — plano: <caminho do plano>`. A cada tarefa concluída, acrescente `Tarefa N: concluída (commit <hash>; testes X/0 falhas)` e, em linhas `Ruling:`, toda decisão tomada onde o plano deixou escolha: `Ruling: <o que> — <por quê> — <custo se errado>`.
3. **Para cada tarefa, nesta ordem:**
   1. Leia a tarefa inteira, suas Interfaces (Consome/Produz) e o Review Focus do plano.
   2. Escreva os testes nomeados na tarefa **e veja-os falhar pelo motivo certo** (não por erro de compilação apenas). Se um teste passar de primeira, desconfie dele: ele prova algo?
   3. Implemente o mínimo para passar, com as assinaturas **exatas** do plano.
   4. Rode os testes da tarefa e depois a suíte do módulo.
   5. **Autorrevisão obrigatória** (checklist da seção 5). Corrija antes de commitar.
   6. Um commit por tarefa, mensagem em pt-BR no padrão Conventional Commits (`feat(...)`, `fix(...)`, `docs(...)`, `test(...)`), corpo curto. `git add` só dos arquivos da tarefa (nunca `git add -A`: o usuário mantém `legacy/.env.example` modificado e fora de commit).
   7. Atualize o arquivo de progresso.
4. Ao terminar todas as tarefas: siga **`docs/superpowers/fluxo-de-entrega.md`** (seções 3 a 6): onda final, suíte completa sem cache, revisão final do conjunto em contexto limpo, varredura de dados pessoais (seção 6 deste guia), HANDOFF e progresso, push, PR, CI verde, merge (squash) quando as 7 condições forem verdadeiras, `git checkout main`, `git pull --ff-only origin main` e suíte na `main`.
5. **Merge:** por decisão do usuário (27/10/2026), a IA mescla a PR do plano/bloco **por conta própria somente quando todas as condições da seção 5 do fluxo de entrega forem verdadeiras**; faltando qualquer uma, pergunta (pergunta com opções). Não empilhe branches: o próximo plano parte da `main` já atualizada e mesclada.

## 5. Checklist de revisão (use em cada tarefa e no conjunto)

Estes foram os defeitos que as revisões do Claude mais encontraram; procure-os de propósito:

1. **Teste vazio:** asserção que passa mesmo com o código quebrado. Faça a **mutação mental** (ou real: quebre a linha, veja o teste falhar, desfaça) para cada teste importante.
2. **Acesso a dados sem `Visibility`:** toda consulta que devolve issues, pessoas ou tempos passa pelo filtro do papel; parâmetros como `pessoa=`, `squad=`, `kind=` nunca ampliam a visão; maiúsculas, espaços e caracteres estranhos não furam o filtro; erro nunca vaza chave de issue ou nome fora da visão.
3. **Dados pessoais em banco/log/fixture:** `displayName`, e-mail, avatar, `accountId`, textos livres do Jira não são gravados. Só o que o cálculo precisa.
4. **Isolamento entre instâncias:** sempre `(instance, key)`; nunca confie só na chave da issue.
5. **Segredos:** token, senha e URLs com credencial não aparecem em `toString`, log, mensagem de erro, resposta HTTP ou relatório. Erros de requisição saem sem corpo.
6. **SQL só parametrizado** (nunca concatenar valor); JQL só pelo `JqlBuilder`.
7. **Datas e fuso:** `Instant` no banco; conversões em America/Manaus; relógio e calendário **injetados** (nada de `Instant.now()` solto em regra de negócio); fronteiras de dia (`>=` / `<`).
8. **Dado ruim não derruba o card:** issue inconsistente é ignorada com aviso informativo (código e gravidade), nunca zera ou dá 500.
9. **Sem ranking de pessoas:** tabelas de pessoas em ordem alfabética, sem "melhor/pior" nem ordenação por desempenho.
10. **Interface:** a cor nunca é o único sinal (use ▲ ● ✓ ⏸ com texto), WCAG AA nos 3 temas (Claro, Escuro, Sidia), pt-BR e inglês completos (nenhuma chave faltando), foco e `Esc` em gavetas, CSV com neutralização de fórmula (`=`, `+`, `-`, `@`, tab, CR, mesmo após espaços/aspas).
11. **Fail-closed em produção:** configuração obrigatória ausente derruba a subida; só o modo dev cai nos modelos `.exemplo`.
12. **Comentários em pt-BR, estilo igual ao arquivo vizinho, sem código morto.**

## 6. Antes de qualquer push

- `git log main..HEAD --format='%ae %s'` e `git diff main..HEAD` varridos por: e-mails reais, usernames reais do time (compare com os nomes que existem no `Filtros_Status_tool.txt` local, **sem copiar esses nomes para lugar nenhum**), hosts internos da empresa, tokens, chaves, senhas.
- Confirme que o repositório está **PRIVADO** (`gh repo view tlmacedo/dashboardJira --json visibility`). O histórico já contém usernames reais; nunca torne-o público sem limpar o histórico.
- `git status` não pode listar `.env`, `papeis.yaml`, `equipes.yaml`, `ausencias.yaml`, `squads.yaml` nem `Filtros_Status_tool.txt`.

## 7. Parar e perguntar ao usuário (em forma de pergunta com opções)

Ação irreversível ou destrutiva; qualquer coisa de segurança que o plano não resolva; efeito fora do repositório (push em branch compartilhada, publicação, mudar configuração do GitHub, merge fora das condições do fluxo de entrega); decisão de produto que muda o que é construído; plano tão quebrado que todo caminho é palpite; qualquer necessidade de acessar o Jira (**a resposta padrão é "não"**: a flag `JIRA_LIVE_ENABLED` só o usuário liga, e a primeira conexão real segue `docs/jira/checklist-primeira-conexao.md`).

## 8. Fim de sessão

Atualize o `HANDOFF.md` (estado, próximo passo, pendências), faça push da branch e abra a PR (em rascunho se o plano estiver incompleto; fluxo de entrega, seção 7). Não deixe commits só locais.

## 9. Ordem dos planos e dependências

A ordem de execução e as dependências estão no índice (`docs/superpowers/plans/2026-10-24-indice-dos-planos.md`), que é a fonte única: ele lista a Fase 0 (PC da empresa) e as fases 9 a 16 na ordem de execução (a 14a vem logo após a 9, quando o usuário ligar a flag em `app/.env`). Cada plano é executável offline com fixtures; dados reais só depois que o usuário ligar `JIRA_LIVE_ENABLED` e seguir o checklist da primeira conexão.
