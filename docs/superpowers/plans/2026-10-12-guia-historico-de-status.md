# Guia: histórico de status, tempos e correções da pesquisa — plano de implementação

> **Para agentes de execução:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendada) ou superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam checkboxes (`- [ ]`).

**Objetivo:** acrescentar ao guia existente (`docs/guia/`) um capítulo de filtros e fontes de dados para histórico de status, um script que calcula tempo em status a partir do changelog do REST, e aplicar as 16 edições de correção apontadas pelas duas rodadas de pesquisa.

**Arquitetura:** o capítulo 07 traz seis filtros JQL de histórico (confirmados na documentação oficial) e a tabela card → fonte; `scripts/tempo_em_status.py` é o caminho retroativo sem app (REST + cálculo local), testado com fixtures. As correções são editadas capítulo a capítulo (Tasks 3 a 6), localizando o texto pelo conteúdo, não pelo número de linha (as linhas deslocam).

**Tecnologias:** Markdown, Python 3 (biblioteca padrão, `unittest`), JQL do Jira Data Center.

**Spec:** `reports/Histórico de status e tempos Jira.md` (seções 2 a 6) e `reports/Filtros JQL para cards Jira.md`. Notas de apoio: `research_notes/Histórico de status e tempos Jira/` e `research_notes/Filtros JQL para cards Jira/`. O desenho base continua em `docs/superpowers/specs/2026-10-12-dashboard-jira-design.md`.

## Restrições globais

- Todas as da fase anterior continuam valendo (ver `docs/superpowers/plans/2026-10-12-guia-dashboard-jira.md`, "Restrições globais"): pt-BR, comentários de código em pt-BR, sem `accountId` em blocos jql, datas dd/mm/aaaa em texto e `aaaa-mm-dd` em jql, nome de filtro `EQP | <seção> | <métrica>`, cada filtro com **Nome:**, **Descrição:**, **Critério de data:** e bloco jql, nada de recurso/preço inventado, marcas **[Confirmar na sua versão]** / **[Exige app: X]**, marcadores proibidos TBD/TODO/XXX/FIXME.
- Nada afirmado como fato para 9.12 ou 10.x que as fontes só confirmam para 10.7/11.3: a pesquisa avisa que as páginas oficiais lidas descrevem 10.7 ou 11.3 e que só a referência REST foi lida em 9.12.0. Sempre que a sintaxe vier dessas páginas, manter **[Confirmar na sua versão]**.
- Ciclo de vida do Data Center (Atlassian, https://www.atlassian.com/licensing/data-center-end-of-life): 30/03/2026 fim de vendas a novos clientes; 30/03/2028 clientes existentes não compram mais produtos, apps nem expansões; 28/03/2029 fim de vida (assinaturas e apps somente leitura). Compromisso da eazyBI e da ScriptRunner: posts dos fornecedores, dizer isso.
- Smart values de Automation documentados no DC: `{{changelog.status}}`, `{{fieldChange.from}}`, `{{fieldChange.to}}`, `{{fieldChange.fromString}}`, `{{fieldChange.toString}}` (só o primeiro valor alterado), `{{now}}`, `.diff(...)` com `.hours`/`.minutes`, `{{#=}}`. `{{fromStatus}}`, `{{toStatus}}` e `{{transition.*}}` NÃO foram encontrados na documentação DC: marcar como não confirmados.
- Eventuais textos com a palavra "nativo" sobre Timesheet são proibidos: o Timesheet é app (Tempo, Simple Reports, Decadis sumUp).
- Código Python: apenas biblioteca padrão; comentários e mensagens em pt-BR; nomes de função em pt-BR sem acento.

## Foco de revisão

1. Issue sem nenhuma transição de status: o tempo no status único vai da criação até a resolução (ou até agora se não resolvida).
2. Issue reaberta: o último intervalo termina na `resolutiondate` se existir; o tempo em "Done" entre fechar e reabrir é contado, o tempo em "Done" depois do último fechamento não é inflado até hoje.
3. Resposta 429 do Jira com `retry-after`: o script espera e tenta de novo, com limite de tentativas.
4. Datas do Jira com `+0000` sem dois-pontos e com ou sem milissegundos.
5. O filtro "parada no mesmo status" continua com o aviso de teste: nenhuma edição pode transformá-lo em "confirmado".

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `docs/guia/07-historico-de-status.md` (novo) | Filtros de histórico, tabela card → fonte, receitas REST/SQL, uso do script |
| `scripts/tempo_em_status.py` (novo) | Cálculo de tempo em status, passagens, ciclo e reaberturas a partir do changelog |
| `scripts/tests/test_tempo_em_status.py` (novo) | Testes do script com fixtures no formato do REST do Jira |
| `scripts/check_guia.py` | Passa a listar o capítulo 07 em `CAPITULOS` |
| `docs/guia/00..06`, `README.md` | Correções (Tasks 3 a 6) |

---

### Task 1: Capítulo 07 e registro no verificador

**Arquivos:**
- Criar: `docs/guia/07-historico-de-status.md`
- Modificar: `scripts/check_guia.py` (acrescentar `"07-historico-de-status.md"` ao final de `CAPITULOS`)
- Modificar: `scripts/tests/test_check_guia.py` (acrescentar um teste que exige que `CAPITULOS` contenha o capítulo 07)

**Interfaces:**
- Consome: filtro `EQP | Base | Equipe` (capítulo 01).
- Produz: seis filtros (nomes exatos abaixo) e a tabela de cards, citados pelo capítulo 06 e pelo README (Task 6); a seção "Script de tempo em status" que a Task 2 completa (o script já deve ser citado pelo caminho `scripts/tempo_em_status.py`).

- [ ] **Passo 1: teste que falha.** Em `scripts/tests/test_check_guia.py` acrescentar à classe `TestVerificarTexto`:

```python
    def test_capitulo_07_registrado(self):
        from check_guia import CAPITULOS
        self.assertIn("07-historico-de-status.md", CAPITULOS)
```

Executar `python3 scripts/tests/test_check_guia.py`. Esperado: FALHA (`AssertionError`).

- [ ] **Passo 2: registrar o capítulo** em `CAPITULOS` (`scripts/check_guia.py`) e rodar os testes. Esperado: OK. Rodar `python3 scripts/check_guia.py` (sem `--completo`): sem erros.

- [ ] **Passo 3: escrever o capítulo 07** em português, com estas seções:

1. **O que o JQL de histórico faz e não faz.** Funciona para 6 campos (Assignee, Fix Version, Priority, Reporter, Resolution, Status) com `WAS`, `WAS IN`, `WAS NOT`, `WAS NOT IN` e `CHANGED`; predicados `AFTER`, `BEFORE`, `BY`, `DURING`, `ON`; `FROM` e `TO` só com `CHANGED`. Só diz quais issues tiveram a transição. Não calcula duração nem conta passagens. As páginas oficiais lidas descrevem 10.7 e 11.3; **[Confirmar na sua versão]**. Datas entre aspas, `aaaa-mm-dd` ou relativas (`"-7d"`). `WAS IN`/`WAS NOT IN` aceitam até 3000 operandos (acima disso a requisição GET falha com 400; use POST). Fonte: página oficial "Advanced searching - operators reference" (https://confluence.atlassian.com/spaces/JIRASOFTWARESERVER/pages/939938745/Advanced+searching+-+operators+reference).
2. **Filtros de histórico** (entrada padrão, `### nome`, `**Nome:**`, `**Descrição:**`, `**Critério de data:**`, bloco jql). Em todos, `"[STATUS_EM_ANDAMENTO]"`, `"[STATUS_CONCLUIDO]"` e `"[STATUS_REABERTO]"` são placeholders dos nomes de status do seu fluxo (entre aspas); explicar isso antes do catálogo. Dados (nome | descrição | critério | JQL):

| Nome | Descrição | Critério | JQL |
|---|---|---|---|
| EQP \| Histórico \| Entraram em andamento no mês | Issues da equipe que passaram para o status de andamento desde o primeiro dia do mês | mudança de status | `filter = "EQP \| Base \| Equipe" AND status CHANGED TO "[STATUS_EM_ANDAMENTO]" AFTER startOfMonth()` |
| EQP \| Histórico \| Concluídas no mês por transição | Issues que passaram para o status de concluído desde o primeiro dia do mês | mudança de status | `filter = "EQP \| Base \| Equipe" AND status CHANGED TO "[STATUS_CONCLUIDO]" AFTER startOfMonth()` |
| EQP \| Histórico \| Reabertas | Issues que saíram de concluído para reaberto ao menos uma vez (não diz quantas vezes) | mudança de status | `filter = "EQP \| Base \| Equipe" AND status CHANGED FROM "[STATUS_CONCLUIDO]" TO "[STATUS_REABERTO]"` |
| EQP \| Histórico \| Mudaram de status em 7 dias | Issues com qualquer mudança de status nos últimos 7 dias | mudança de status | `filter = "EQP \| Base \| Equipe" AND status CHANGED AFTER "-7d"` |
| EQP \| Histórico \| Nunca estiveram em andamento | Abertas que nunca tiveram o status de andamento | mudança de status | `filter = "EQP \| Base \| Equipe" AND statusCategory != Done AND status WAS NOT IN ("[STATUS_EM_ANDAMENTO]")` |
| EQP \| Histórico \| Status alterado por usuário no mês | Issues cujo status foi alterado por um usuário específico desde o primeiro dia do mês (troque `[USUARIO]` pelo username) | mudança de status | `filter = "EQP \| Base \| Equipe" AND status CHANGED BY [USUARIO] AFTER startOfMonth()` |

   Dentro dos blocos jql os nomes usam `|` simples. Notas obrigatórias após os blocos: (a) se uma issue nunca mudou de status, o valor inicial conta como histórico para `WAS`? **Não confirmado**: testar com uma issue recém-criada; (b) `status CHANGED` devolve a issue, não os itens da mudança: para ver quem/quando use o REST (seção 4); (c) a combinação `NOT (status CHANGED ...)` não está na documentação; o filtro "Parada no mesmo status" do capítulo 03 continua marcado a validar.
3. **Tabela card → fonte.** Colunas "Card", "Fonte", "Consulta", "Confirmação". Linhas: tempo em cada status (app, eazyBI, REST+script, SQL, Automation); tempo de ciclo (gadget Resolution Time para criação→resolução; entre dois status: app/REST/SQL, com o filtro de apoio `status CHANGED FROM "[STATUS_EM_ANDAMENTO]" TO "[STATUS_CONCLUIDO]" DURING ("2026-01-01","2026-01-31")`); passagens por status (Jira Charting "Average Number of Times in Status" é média por status; eazyBI; REST contando itens do changelog); reaberturas (filtro "Reabertas" + REST para contar); histórico com autor e data (REST `changelog.histories[]`); issues que passaram por um status no período (filtros da seção 2). Confirmação: confirmado / parcial / não confirmado, como no relatório `reports/Histórico de status e tempos Jira.md` seção 2.
4. **REST: receitas.**
   - Autenticação: PAT (Jira 8.14+) com `Authorization: Bearer <token>`, conta de serviço com leitura nos projetos.
   - Uma issue: `GET /rest/api/2/issue/{key}?expand=changelog`; estrutura `changelog{startAt,maxResults,total,histories[]}`; cada history tem `id`, `author`, `created`, `items[]`; cada item tem `field`, `fieldtype`, `from`, `fromString`, `to`, `toString`. Exemplo JSON mínimo de uma transição de status.
   - Em lote: `GET /rest/api/2/search?jql=<jql>&expand=changelog&fields=status,created,resolutiondate&startAt=0&maxResults=100`; teto de `maxResults` dado por `jira.search.views.default.max`; paginar por `startAt`.
   - `/rest/api/2/issue/{key}/changelog` NÃO consta na referência 9.12.0 (pedido JRASERVER-59998 aberto); o `expand=changelog` pode vir truncado em históricos longos: **[Confirmar na sua versão]**.
   - Referência REST 10.x: páginas deram 404 na pesquisa de 12/10/2026; **não confirmado** para 10.x.
   - Worklog: `GET /rest/api/2/issue/{key}/worklog`, `GET /rest/api/2/worklog/updated?since=<ms>`, `POST /rest/api/2/worklog/list` (corpo `{"ids":[...]}`, máx. 1000), `GET /rest/api/2/worklog/deleted` (referência 9.12.0).
   - Rate limiting: HTTP 429 com `retry-after`; concorrência de 1 a 2 requisições.
5. **Script de tempo em status** (`scripts/tempo_em_status.py`): o que calcula (tempo por status, passagens por status, tempo de ciclo entre dois status, reaberturas), como rodar (`python3 scripts/tempo_em_status.py --url https://jira.exemplo.com --jql 'filter = 12345' --token-env JIRA_PAT --saida tempos.csv`, ver Task 2 para as opções exatas), e que o cálculo foi testado só com fixtures no formato do REST (não contra um Jira real): validar contra duas ou três issues conferindo a aba Histórico antes de confiar nos números.
6. **SQL (esboço de autoria nossa, PostgreSQL)** com `LEAD()` sobre `changegroup`/`changeitem` onde `field = 'status'`, marcado: a Atlassian recomenda a API em vez de consulta direta, banco só em réplica de leitura, não suportado oficialmente; fonte: https://developer.atlassian.com/server/jira/platform/database-change-history/.
7. **Desempenho e limites**: `jira.search.views.default.max` e `jira.search.views.max.limit`; impacto de WAS/CHANGED em dashboards **não documentado**: medir; rate limiting só vale para REST externo; sem guia oficial de desempenho para instâncias grandes.
8. **Que caminho escolher** (3 cenários: já tem app; pode instalar; não pode instalar), incluindo o horizonte do Data Center (datas das restrições globais) e a recomendação de que a camada REST pode ser reaproveitada na migração para Cloud (APIs diferentes).

- [ ] **Passo 4: verificar.** `python3 scripts/tests/test_check_guia.py` (OK) e `python3 scripts/check_guia.py` (esperado: 9 capítulos com os 8 anteriores mais o 07 presentes quando existem; 0 erros). Os 6 filtros novos devem passar nas regras de nome, critério e referência.

---

### Task 2: Script de tempo em status (TDD)

**Arquivos:**
- Criar: `scripts/tempo_em_status.py`
- Teste: `scripts/tests/test_tempo_em_status.py`

**Interfaces:**
- Produz: `analisar_data(texto: str) -> datetime`, `transicoes_de_status(issue: dict) -> list[dict]` (chaves `quando`, `autor`, `de`, `para`), `intervalos(issue: dict, agora: datetime) -> list[dict]` (chaves `status`, `inicio`, `fim`, `autor_entrada`), `tempo_por_status(issue, agora) -> dict[str, timedelta]`, `passagens_por_status(issue) -> dict[str, int]`, `tempo_de_ciclo(issue, status_inicio, status_fim) -> timedelta | None`, `reaberturas(issue, de_status, para_status) -> int`, `buscar_issues(url, token, jql, abrir=urllib.request.urlopen, dormir=time.sleep, tamanho_pagina=100, max_tentativas=5)` (gerador de issues), `linhas_csv(issues, agora, status_inicio, status_fim) -> list[list[str]]`, `main(argv) -> int`.

- [ ] **Passo 1: escrever os testes que falham** em `scripts/tests/test_tempo_em_status.py`. Fixture (formato do REST do Jira):

```python
ISSUE = {
    "key": "ABC-1",
    "fields": {
        "created": "2026-01-05T09:00:00.000+0000",
        "resolutiondate": "2026-01-08T11:00:00.000+0000",
        "status": {"name": "Done"},
    },
    "changelog": {"histories": [
        {"author": {"name": "ana"}, "created": "2026-01-05T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Open", "toString": "In Progress"}]},
        {"author": {"name": "bia"}, "created": "2026-01-06T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "In Progress", "toString": "Done"}]},
        {"author": {"name": "ana"}, "created": "2026-01-07T10:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Done", "toString": "Reopened"}]},
        {"author": {"name": "ana"}, "created": "2026-01-07T11:00:00.000+0000",
         "items": [{"field": "status", "fromString": "Reopened", "toString": "In Progress"}]},
        {"author": {"name": "bia"}, "created": "2026-01-08T11:00:00.000+0000",
         "items": [{"field": "priority", "fromString": "Low", "toString": "High"},
                   {"field": "status", "fromString": "In Progress", "toString": "Done"}]},
    ]},
}
```

Testes obrigatórios (valores esperados exatos; `AGORA = analisar_data("2026-02-01T00:00:00.000+0000")`):
  - `analisar_data` aceita `+0000` com e sem milissegundos (`2026-01-05T09:00:00.000+0000` e `2026-01-05T09:00:00+0000`) e devolve datetime com fuso.
  - `transicoes_de_status(ISSUE)` devolve 5 transições em ordem cronológica, ignorando o item `priority`; a primeira tem `de="Open"`, `para="In Progress"`, `autor="ana"`; mesmo com `histories` fora de ordem na entrada o resultado é cronológico (embaralhar a lista no teste).
  - `tempo_por_status(ISSUE, AGORA)`: `Open` = 1h; `In Progress` = 24h + 24h = 48h (05/01 10:00 a 06/01 10:00 e 07/01 11:00 a 08/01 11:00); `Done` = 24h (06/01 10:00 a 07/01 10:00) + 0 (último intervalo termina na `resolutiondate`, que é o instante da última entrada em Done); `Reopened` = 1h. O total de todos os intervalos = 1 + 48 + 24 + 1 = 74h.
  - `passagens_por_status(ISSUE)`: `{"Open": 1, "In Progress": 2, "Done": 2, "Reopened": 1}` (o status inicial conta como 1 passagem).
  - `tempo_de_ciclo(issue, status_inicio, status_fim)` mede da primeira entrada em `status_inicio` até a primeira entrada em `status_fim` que ocorra DEPOIS dela. `tempo_de_ciclo(ISSUE, "In Progress", "Done")` = 24h (05/01 10:00 a 06/01 10:00); `tempo_de_ciclo(ISSUE, "Done", "In Progress")` = 25h (primeira entrada em Done é 06/01 10:00; a primeira entrada em In Progress depois dela é 07/01 11:00); `tempo_de_ciclo(ISSUE, "Done", "Inexistente")` = `None`.
  - `reaberturas(ISSUE, "Done", "Reopened")` = 1.
  - Issue sem transições (`changelog.histories` vazio), `fields.status.name = "Open"`, sem `resolutiondate`: `tempo_por_status` = `{"Open": AGORA - created}`; `passagens_por_status` = `{"Open": 1}`.
  - Issue sem transições com `resolutiondate`: o intervalo único termina na `resolutiondate`.
  - `buscar_issues` com `abrir` falso que devolve duas páginas (primeira com `startAt=0`, `total=3`, 2 issues; segunda com `startAt=2`, 1 issue) produz 3 issues em ordem e faz 2 chamadas; a URL contém `expand=changelog`, `startAt=` e `maxResults=`; o cabeçalho `Authorization` é `Bearer <token>`.
  - `buscar_issues` com resposta 429 (levantar `urllib.error.HTTPError` com cabeçalho `retry-after: 3`) na primeira chamada: chama `dormir(3)` e tenta de novo; após `max_tentativas` respostas 429 seguidas levanta `RuntimeError` com mensagem em pt-BR.
  - `linhas_csv` devolve cabeçalho + uma linha por issue com as colunas `chave`, `tempo_<status>_horas` para cada status encontrado (ordem alfabética), `passagens_<status>`, `ciclo_horas`, `reaberturas`; horas com uma casa decimal.

Executar `python3 scripts/tests/test_tempo_em_status.py`. Esperado: FALHA (`ModuleNotFoundError`).

- [ ] **Passo 2: implementar** `scripts/tempo_em_status.py` até os testes passarem. Regras: biblioteca padrão apenas; `analisar_data` usa `datetime.strptime` com `%Y-%m-%dT%H:%M:%S.%f%z` e, se falhar, sem `.%f`; intervalos: o status inicial é o `de` da primeira transição (ou `fields.status.name` se não houver transições) começando em `fields.created`; cada transição fecha o intervalo anterior e abre outro; o último intervalo termina na `resolutiondate` se existir, senão em `agora`; `main` aceita `--url`, `--jql`, `--token-env` (nome da variável de ambiente com o PAT), `--saida` (arquivo CSV; padrão saída padrão), `--status-inicio` e `--status-fim` (padrões `In Progress` e `Done`) e devolve 0; comentários e mensagens em pt-BR; docstring do módulo explica que o cálculo é nosso e foi validado só com fixtures.

- [ ] **Passo 3: rodar** `python3 scripts/tests/test_tempo_em_status.py` (OK, saída limpa) e `python3 scripts/tests/test_check_guia.py` (OK).

---

### Task 3: Correções do capítulo 04

**Arquivos:**
- Modificar: `docs/guia/04-tempo-por-status.md`

**Interfaces:** consome as restrições globais; localizar cada trecho pelo conteúdo.

- [ ] **Passo 1: aplicar as edições** (do relatório `reports/Histórico de status e tempos Jira.md`, seção 6, itens 1, 2, 3, 4 (partes do 04), 6 (partes do 04), 11 e 12 (parte do 04)):
  1. Onde as regras de Automation usam `{{fromStatus.name}}`/`{{toStatus.name}}`: trocar pelo gatilho "Field value changed" no campo Status com `{{fieldChange.fromString}}` e `{{fieldChange.toString}}`, e marcar `{{fromStatus}}`/`{{toStatus}}` como **não encontrados na documentação do DC**; acrescentar a limitação de que `{{fieldChange}}` só traz o primeiro valor alterado.
  2. Na nota de valores obsoletos e na aritmética de datas: citar `.diff(...)` com `.hours`/`.minutes`, `{{#=}}` e a ação "Re-fetch issue data" (Automation 7.0.0+, DC 8.0.0+; **[Confirmar na sua versão]**); suavizar "refletem a issue como estava no início" para "podem refletir".
  3. Incluir, na comparação e na árvore de decisão, duas alternativas: **REST + script** (`scripts/tempo_em_status.py`, retroativo, sem app; referir o capítulo 07) e o **gadget Average Time in Status do Jira Charting** (exige campo personalizado "Time in Status" e reindexação completa; app 288: v4.0.1 DC 9.6.0 a 9.17.5 e v5.0.0 DC 10.0.0 a 10.7.4, "declarado pelo fornecedor"; um KB antigo diz que nunca foi compatível com DC: **[Confirmar na sua versão]**). Na árvore, para o cenário sem app, o REST vem antes do Automation (retroativo).
  4. Faixas de compatibilidade declaradas (sempre com "declarado pelo fornecedor, sem teste independente"): Time in Status (Timepiece, app 1211756) DC 8.0.0 a 11.3.12; Status Time Reports 2.3.x (app 1221826) DC 8.0.0 a 10.7.4; Cycle Time Gadgets (app 1235431) DC 8.20.0 a 11.3.12 (poucas instalações); Time in Status Reports for Jira (app 1226187): a versão mais nova só declara DC 11.x.
  5. Acrescentar o horizonte do Data Center (datas das restrições globais) e a recomendação de planejar a saída; qualificar a eazyBI: licença por faixa de usuários via Marketplace, compromisso com o DC declarado em post do fornecedor, e a venda a novos clientes DC terminou em 30/03/2026 segundo a Atlassian.
  6. Qualificar "não existe gadget nativo de tempo por status": vale para o núcleo sem apps; o Jira Charting acrescenta Average Time in Status e Average Number of Times in Status.
  7. Automation: embutido no Jira Software DC desde a 9.0 (remover a ressalva "ou app instalado à parte" para 9.12/10.x); acrescentar o limite de 1000 issues por busca JQL e os limites de 5000 execuções por hora e 3600 s de processamento por dia por regra (documentados para DC 11.2; **[Confirmar na sua versão]**).
- [ ] **Passo 2: verificar.** `python3 scripts/check_guia.py` (0 erros). Conferir por busca que `{{fromStatus` só aparece em frases que o declaram não confirmado.

---

### Task 4: Correções dos capítulos 00, 01 e 02

**Arquivos:**
- Modificar: `docs/guia/00-pre-requisitos.md`, `docs/guia/01-filtros-salvos.md`, `docs/guia/02-resumo-da-equipe.md`

- [ ] **Passo 1: aplicar** (relatório seção 6, itens 5 (cap.00), 6 (cap.00 e 01), 7 (cap.01), 8, 10, 12 (cap.00), 15):
  1. Cap. 00 (matriz e checagem de versão): reclassificar o Timesheet como app (Tempo, Simple Reports, Decadis sumUp), **[Exige app: Timesheet]**; Workload Pie Chart exige o Jira Charting App; gadgets "Average Time in Status" e "Average Number of Times in Status" também; checagem de versão: se o Automation vem embutido (desde 9.0); remover ressalvas obsoletas.
  2. Cap. 00 e 01: qualificar a eazyBI (ciclo de vida do DC e licença por faixa de usuários, como na Task 3 item 5).
  3. Cap. 01: `subTaskIssueTypes()` → `subtaskIssueTypes()` (grafia oficial) em todos os JQL (o verificador não pega isso; use busca por `subTaskIssueTypes` em todo o arquivo); `startOfMonth(-2)` → `startOfMonth("-2M")` (filtro "Criadas em 3 meses" e sua descrição; `startOfQuarter` não existe, manter); fundação de filtros: recomendar `filter = <ID>` como padrão (a documentação recomenda ID) e manter o nome só como alternativa legível; corrigir a justificativa de ORDER BY (um ORDER BY digitado na busca substitui o do filtro salvo; manter o filtro-base sem ORDER BY por clareza).
  4. Cap. 00: Automation incluído desde a 9.0; limite de 1000 issues por busca JQL.
  5. Cap. 02: retirar ou qualificar com **[Confirmar na sua versão]** as afirmações sem fonte oficial: total "x–y de N", agrupamento por statusCategory na pizza, percentuais só na pizza (a documentação não lista os tipos de estatística nem confirma percentual); a lista oficial de gadgets nativos (43) inclui Filter Results, Pie Chart, Issue Statistics, Two Dimensional Filter Statistics, Heat Map, Created vs Resolved, Average Age, Resolution Time, Time Since e Recently Created.
- [ ] **Passo 2: verificar.** `python3 scripts/check_guia.py` (0 erros); `grep -rn "subTaskIssueTypes" docs/guia` não pode devolver nada; `grep -rn "startOfMonth(-2)" docs/guia` não pode devolver nada.

---

### Task 5: Correções dos capítulos 03 e 05

**Arquivos:**
- Modificar: `docs/guia/03-negligenciados-e-horas.md`, `docs/guia/05-datas-e-botao-de-sincronizacao.md`

- [ ] **Passo 1: aplicar** (relatório seção 6, itens 5 (cap.03), 6 (cap.03), 7 (cap.03 e 05), 9, 12 (cap.05), 13, 14):
  1. Cap. 03: Timesheet reclassificado como app em toda a seção de horas, nos cards e na tabela de alternativas (**[Exige app: Timesheet — Tempo, Simple Reports ou Decadis sumUp; confirmar compatibilidade DC no Marketplace]**); Workload Pie Chart exige o Jira Charting App; Time Tracking Report é por versão (projeto ou sprint: não confirmado); a recomendação passa a ser "app de Timesheet ou eazyBI para horas por pessoa e período; sem app, REST de worklog".
  2. Cap. 03: `subTaskIssueTypes()` → `subtaskIssueTypes()`; no filtro "Parada no mesmo status" usar `"-14d"` com aspas dentro de `CHANGED` (`NOT (status CHANGED AFTER "-14d")`), manter `created <= -14d` e o aviso de teste, e acrescentar a pendência "a criação conta como mudança de status? não confirmado"; a ressalva do `-14d` agora vale para os dois `-14d` do filtro.
  3. Cap. 03: qualificar eazyBI (ciclo de vida, licença por faixa de usuários); campos Σ (`aggregatetimespent`) não pesquisáveis (fonte secundária JIRAAUTOSERVER-1231; **não confirmado** na página oficial de campos).
  4. Cap. 05: `subtaskIssueTypes()`; Automation incluído desde a 9.0 (remover ressalva de app para 9.12/10.x); rebaixar para "testar em homologação" a condição "Advanced compare", `{{initiator.displayName}}`, a grafia `duedate` versus `dueDate` nos smart values e a permissão "Agendar Tarefas" (um KB do DC cita Editar Tarefas); webhook de entrada: só POST está documentado (GET e segredo não confirmados).
- [ ] **Passo 2: verificar.** `python3 scripts/check_guia.py` (0 erros); `grep -rn "subTaskIssueTypes" docs/guia` nada; o filtro "Parada no mesmo status" continua com UM `**Critério de data:**`.

---

### Task 6: Capítulo 06, README e ligação com o capítulo 07

**Arquivos:**
- Modificar: `docs/guia/06-layout-permissoes-e-validacao.md`, `docs/guia/README.md`

- [ ] **Passo 1: aplicar** (relatório seção 6, itens 4 (06), 5 (06 e README), 10 (06), 14 (06), 16):
  1. Cap. 06: linhas do layout e textos que tratam o Timesheet como nativo passam a "Timesheet de app **[Exige app: Timesheet]**"; a contagem de gadgets do layout continua 15 sem o módulo (não altere a contagem).
  2. Cap. 06 manutenção: "Troque o `-14d`" → "Troque **todos** os `-14d`" (o filtro "Parada no mesmo status" tem dois); ajustar a justificativa do ORDER BY e recomendar referência por ID; a frase "evite `status CHANGED`" passa a dizer que o impacto de `WAS`/`CHANGED` em dashboards não está documentado e deve ser medido.
  3. Cap. 06: acrescentar o horizonte do DC (datas das restrições globais) em "Melhorias futuras" ou "Manutenção", e a recomendação de planejar a saída.
  4. Cap. 06: nova seção "Testes de histórico de status" (checklist `- [ ]`) com: issue recém-criada contra `status WAS` (valor inicial conta?); `NOT (status CHANGED AFTER "-14d")`; `expand=changelog` truncado em histórico longo (issue com mais de 100 mudanças); `/issue/{key}/changelog` ausente em 9.12; referência REST 10.x; resultado de `scripts/tempo_em_status.py` contra duas ou três issues conferidas na aba Histórico; fuso de `worklogDate`; `worklogAuthor` e `worklogDate` casam no mesmo worklog?; gravar o mesmo valor atualiza `updated`?; Advanced compare e `{{initiator}}`.
  5. Cap. 06: o layout ganha uma observação: os filtros do capítulo 07 alimentam gadgets Filter Results opcionais (faixa 4 ou como substituição), sem passar de 15 gadgets (indicar quais gadgets cortar).
  6. README: incluir o capítulo 07 na ordem de execução (módulo opcional, depende de REST/app), o script `scripts/tempo_em_status.py` e as pastas `reports/` e `research_notes/`; Timesheet deixa de ser listado como núcleo nativo; saída esperada do verificador completo passa a "9 capítulo(s) verificado(s), 0 erro(s)".
- [ ] **Passo 2: verificação completa.** `python3 scripts/tests/test_check_guia.py` (OK), `python3 scripts/tests/test_tempo_em_status.py` (OK) e `python3 scripts/check_guia.py --completo` (esperado: `9 capítulo(s) verificado(s), 0 erro(s)`). Conferir que a tabela do layout do capítulo 06 ainda tem 15 gadgets sem o módulo.
