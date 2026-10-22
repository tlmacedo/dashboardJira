# Procedimento de integração para uma IA (ou pessoa técnica)

Leia este arquivo inteiro e execute os passos em ordem. Objetivo: deixar o painel rodando e lendo o Jira do usuário, com equipes e pessoas configuradas, **sem ligar escrita no Jira**.

## 0. Contrato

- Repositório: raiz contém `requirements.txt`; aplicação em `app/`. Backend: `app/backend/jiradash` (FastAPI). Front estático: `app/frontend`. Configuração: `app/.env` (segredos) e `app/config/equipes.yaml` (equipes).
- Python 3.11+. Comandos do CLI rodam em `app/backend` com `PYTHONPATH=.` (ou usando `app/start.sh`).
- **Regras de segurança (obrigatórias):** nunca imprima, comite ou envie o token; nunca versione `.env` nem `config/equipes.yaml`; nunca defina `JIRADASH_ALLOW_WRITE=1` sem pedido explícito do usuário; não execute `sync` contra um Jira que o usuário não indicou.

## 1. Entradas que você precisa do usuário (peça se faltarem)

| Chave | Descrição | Como obter |
|---|---|---|
| `JIRA_FLAVOR` | `cloud` se a URL termina em `.atlassian.net`; senão `dc` | observar a URL |
| `JIRA_BASE_URL` | URL base, sem `/` final | ex.: `https://empresa.atlassian.net` |
| `JIRA_EMAIL` | só Cloud | e-mail da conta dona do token |
| `JIRA_TOKEN` | Cloud: API token; DC: Personal Access Token | Cloud: https://id.atlassian.com/manage-profile/security/api-tokens |
| Projetos | chaves dos projetos (ex.: `ABC,DEF`) | pergunte; ou use todos no `discover` |
| Equipes | para cada squad: nome, gerente, membros, projetos | pergunte; o `discover` lista usuários |
| Tipos de issue | nome do tipo "pai/checklist" e da "review" filha | o `discover` lista os tipos existentes |

## 2. Procedimento

```bash
# 2.1 ambiente
cd app && python3 -m venv .venv && source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r backend/requirements.txt
# opcional (PostgreSQL): pip install -r backend/requirements-postgres.txt
cd backend && export PYTHONPATH="$PWD"

# 2.2 credenciais  (não interativo; o token vem de variável de ambiente já definida pelo usuário)
python -m jiradash init --flavor "$JIRA_FLAVOR" --url "$JIRA_BASE_URL" --email "$JIRA_EMAIL" --token "$JIRA_TOKEN"
#   esperado: "Conexão OK. Conectado como: <nome>"   (se FALHA: ver seção 4)

# 2.3 descoberta -> rascunho de equipes
python -m jiradash discover [--projects ABC,DEF]
#   gera app/config/equipes.gerado.yaml

# 2.4 equipes: copie o rascunho para app/config/equipes.yaml e ajuste (seção 3)

# 2.5 validação e carga
python -m jiradash check            # esperado: equipes carregadas, conectado, campos descobertos, sem "NÃO MAPEADO"
python -m jiradash sync --full      # esperado: JSON com "issues": N > 0

# 2.6 subir
python -m uvicorn jiradash.main:app --host 0.0.0.0 --port 8000
curl -s localhost:8000/api/health   # esperado: {"ok":true,"mode":"jira","write_enabled":false}
```

Verificações de aceitação (todas devem passar):
1. `curl -s localhost:8000/api/sync/status` mostra `mode":"jira"` e `ultima_sincronizacao` preenchida.
2. `curl -s localhost:8000/api/open | python -c "import sys,json;print(json.load(sys.stdin)['total'])"` retorna um número coerente com uma contagem feita no Jira para o mesmo JQL.
3. `curl -s localhost:8000/api/quality` não mostra 100% em nenhum item.
4. `python -m pytest -q` (em `app/backend`) passa.

## 3. Como editar `config/equipes.yaml`

Esquema (chaves de topo): `jql_scope` (string JQL), `status_map` (nome do status → `todo|active|wait|blocked|done`), `workflow_order` (lista ordenada de status), `negative_resolutions` (lista de resoluções que contam como cancelamento), `status_wip_limits` (status → limite), `default_issue_hours` (número), `checklist_types`, `review_types`, `goals` (`on_time_pct`, `early_pct`), `squads[]`, `absences[]`, `holidays[]`.

Regras de preenchimento:
- `status_map`: use categoria do Jira como padrão (`new`→`todo`, `indeterminate`→`active`, `done`→`done`); marque como `wait` status de espera/revisão e `blocked` os de bloqueio. **Todo status que aparece no Jira precisa estar mapeado**; o `check` lista os que faltam.
- `workflow_order`: do primeiro ao último status do fluxo normal (a volta para uma etapa anterior conta como "devolução").
- `squads[].manager.ref` e `members[].ref`: **Cloud = accountId; DC = username**. Sem referência correta a pessoa aparece como "Sem responsável".
- Um projeto pode pertencer a um squad (`projects`); se vários squads dividem um projeto, use `components` no squad.
- `absences[]`: `{person: ref, start: AAAA-MM-DD, end: AAAA-MM-DD, kind: ferias|licenca|folga, partial: 1.0}`.
- Não invente pessoas, status ou datas. Se o usuário não informou, deixe vazio e avise.

## 4. Diagnóstico por mensagem

| Mensagem/sintoma | Causa | Ação |
|---|---|---|
| `JiraError ... 401` | credencial inválida | Cloud: confira e-mail+token; DC: PAT válido (expira) |
| `... 403` | sem permissão no projeto | conceder *Browse Projects* e *View Worklogs* à conta |
| `... 404` em `/rest/api/3/search/jql` | instância não é Cloud | usar `JIRA_FLAVOR=dc` |
| `... 404` em `/rest/api/2/search` no Cloud | flavor errado | usar `cloud` |
| `... 400 ... jql` | `jql_scope` inválido/ilimitado | ajustar; JQL com só `ORDER BY` é rejeitado no Cloud |
| `... 429` repetido | limite de requisições | aguardar; reduzir `JIRADASH_SYNC_MIN` não ajuda: aumente-o |
| `ATENÇÃO: status sem mapeamento` | status faltando | adicionar em `status_map` |
| `FileNotFoundError ... equipes.yaml` | passo 2.4 pendente | criar o arquivo |
| Telas vazias | sync não rodou ou `squads[].projects` não bate com as chaves dos projetos | rodar sync; conferir chaves |
| Pessoas "Sem responsável" | `ref` incorreto | usar valores do `discover` |

## 5. Arquivos que você pode alterar sem risco

`app/.env`, `app/config/equipes.yaml`. Qualquer outra alteração (código) exige pedido do usuário e testes (`python -m pytest -q`).

## 6. Entrega ao usuário

Informe: URL do painel, modo (`jira`), número de issues sincronizadas, squads carregados, status mapeados, o que ficou pendente (ex.: gerente sem `ref`, ausências não informadas) e lembre que a escrita no Jira está **desligada**.
