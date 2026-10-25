# Início rápido

Objetivo: do `git clone` ao painel funcionando com o **seu** Jira. Tempo estimado: 15 a 30 minutos.

## 1. Ver funcionando agora (dados fictícios)

```bash
git clone <URL-DO-REPOSITORIO> dashboardJira && cd dashboardJira/app
./start.sh demo          # Windows: start.bat demo
```
Abra http://localhost:8000. Há um aviso fixo "dados fictícios". Requisito: Python 3.11 ou superior.

## 2. Ligar ao seu Jira (3 informações + 1 arquivo)

| O que | Onde informar | Exemplo |
|---|---|---|
| **URL do servidor** | `.env` → `JIRA_BASE_URL` | `https://suaempresa.atlassian.net` ou `https://jira.suaempresa.com.br` |
| **Tipo** | `.env` → `JIRA_FLAVOR` | `cloud` ou `dc` (Data Center/Server) |
| **Login** | `.env` → `JIRA_EMAIL` (só Cloud) | `conta-de-servico@suaempresa.com` |
| **Senha = token** | `.env` → `JIRA_TOKEN` | Cloud: crie em https://id.atlassian.com/manage-profile/security/api-tokens · Data Center: Perfil → Personal Access Tokens |
| **Equipes e pessoas** | `config/equipes.yaml` | squads, gerente, membros, projetos |

Use uma **conta de serviço** com permissão de *Browse Projects* e *View Worklogs* nos projetos. Não precisa de permissão de escrita.

Caminho mais curto, com assistente:
```bash
cd backend
python -m jiradash init                      # pergunta URL, e-mail e token, grava o .env e testa a conexão
python -m jiradash discover                  # lista projetos, status, tipos e usuários e gera config/equipes.gerado.yaml
```
Depois edite o rascunho (veja a seção 3) e salve como `config/equipes.yaml`:
```bash
python -m jiradash check                     # confere login, campos e status sem mapeamento
python -m jiradash sync --full               # primeira carga com histórico (pode levar minutos)
cd .. && ./start.sh                          # sobe o painel em http://localhost:8000
```

## 3. Informar equipes e pessoas (`config/equipes.yaml`)

Parta de `config/equipes.exemplo.yaml` (ou do rascunho gerado). O essencial:

```yaml
jql_scope: "project in (ABC, DEF)"            # o que sincronizar
status_map:                                   # nomes EXATOS dos status do seu fluxo -> categoria
  "A Fazer": todo
  "Em Andamento": active
  "Em Revisão": wait
  "Bloqueado": blocked
  "Concluído": done
workflow_order: ["A Fazer", "Em Andamento", "Em Revisão", "Concluído"]
checklist_types: ["Checklist"]                # tipo da tarefa pai
review_types: ["Review"]                      # tipo da tarefa filha de revisão
goals: {on_time_pct: 90, early_pct: 35}       # metas de prazo (%)
squads:
  - key: meu-squad
    name: Meu Squad
    manager: {ref: "ref-do-gerente", name: "Nome do Gerente"}
    projects: [ABC]                           # um squad pode ter vários projetos; um projeto pode ser dividido por components
    members:
      - {ref: "ref-da-pessoa", name: "Nome da Pessoa", skills: [backend], weekly_hours: 40}
absences:                                     # férias, licenças (sem motivo detalhado)
  - {person: "ref-da-pessoa", start: 2026-12-01, end: 2026-12-15, kind: ferias}
```
- `ref` da pessoa: **Cloud = accountId**, **Data Center = username**. O `discover` já lista os valores.
- Vários squads, vários gerentes, pessoa em mais de um squad (`allocation: 0.5`) são suportados.
- Cada squad define o próprio `wip_limit_per_person`. Feriados nacionais já vêm embutidos; acrescente os locais em `holidays`.

## 4. Conferir se os números estão certos

1. Abra **Abertas** e compare o total com um filtro do Jira equivalente.
2. Abra uma issue em **Fechadas** e confira o histórico de status no Jira.
3. Veja **Qualidade dos dados**: muitas issues sem estimativa ou sem vencimento enfraquecem previsões e metas.
4. Só depois pense em ligar a escrita (`JIRADASH_ALLOW_WRITE=1`). Antes, use a **Distribuição** em modo sombra e compare com as decisões do gerente.

## 5. Problemas comuns

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `401`/`403` no `check` | token errado, e-mail errado (Cloud) ou sem permissão | refaça o token; no Cloud use o e-mail da conta dona do token |
| `ATENÇÃO: status sem mapeamento` | status novo não está em `status_map` | adicione-o com a categoria certa |
| Telas vazias | `jql_scope` não cobre os projetos ou sync não rodou | `python -m jiradash sync --full` e confira o JQL |
| Pessoa aparece como "Sem responsável" | `ref` no YAML diferente do accountId/username | use o valor do `discover` |
| Tempos estranhos em Data Center | nome de exibição no changelog de responsável | confira `ref` e nomes; abra o caso |
| Porta 8000 ocupada | outro serviço | `python -m uvicorn jiradash.main:app --port 8010` |

Manual completo das telas: [`docs/manual/MANUAL_DO_USUARIO.md`](docs/manual/MANUAL_DO_USUARIO.md). Procedimento para uma IA fazer isto sozinha: [`INTEGRACAO_PARA_IA.md`](INTEGRACAO_PARA_IA.md).
