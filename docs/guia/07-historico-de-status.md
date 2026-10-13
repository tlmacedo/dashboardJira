# Capítulo 07: histórico de status

Este capítulo trata de tudo o que depende do histórico de mudanças das tarefas: quais issues passaram por um status, quais foram reabertas, e de onde tirar tempo em status, tempo de ciclo e passagens. Ele traz seis filtros de histórico (JQL nativo), uma tabela que liga cada card à sua fonte, receitas de REST, um esboço de SQL e a descrição do script `scripts/tempo_em_status.py`. Todos os filtros partem do filtro `EQP | Base | Equipe`, criado no capítulo 01. O capítulo 04 compara as alternativas de tempo por status; este aqui detalha o caminho retroativo sem app e os filtros que não dependem de app.

Convenção de confiança, usada na tabela da seção 3: **confirmado** (lido em documentação oficial), **parcial** (parte confirmada, parte inferência) e **não confirmado** (não encontrado ou não testado). A fonte dos fatos é o relatório `reports/Histórico de status e tempos Jira.md` (seções 2 a 5).

## 1. O que o JQL de histórico faz e não faz

O JQL de histórico funciona para seis campos: Assignee, Fix Version, Priority, Reporter, Resolution e Status. Os operadores são `WAS`, `WAS IN`, `WAS NOT`, `WAS NOT IN` e `CHANGED`. Os predicados são `AFTER`, `BEFORE`, `BY`, `DURING` e `ON`; `FROM` e `TO` só valem junto com `CHANGED`. As páginas de campos e de operadores da documentação se contradizem sobre Resolution como campo de histórico: teste na instância. **[Confirmar na sua versão]**

O que ele entrega: a lista de issues que tiveram a transição. O que ele não entrega: a duração em cada status nem a contagem de passagens. Esses números exigem o changelog (REST ou banco), um app ou campos acumulados por Automation (seção 3).

Pontos de atenção:

- As páginas oficiais lidas descrevem o Data Center 10.7 e 11.3, não o 9.12. Trate toda a sintaxe desta seção como a das páginas lidas e valide na sua instância. **[Confirmar na sua versão]**
- Datas ficam entre aspas, em `aaaa-mm-dd` ou relativas (por exemplo `"-7d"`). O uso de aspas em datas relativas dentro dos predicados de `WAS`/`CHANGED` é inferência a partir da regra de datas dos campos; nenhum exemplo oficial lido o mostra. **[Confirmar na sua versão]**
- `WAS IN` e `WAS NOT IN` aceitam até 3000 operandos. Acima disso, a requisição GET falha com HTTP 400; use POST em `/rest/api/2/search`.
- Use sempre o nome do status, nunca o ID: as páginas de operadores e de campos divergem sobre como `WAS` trata o ID. **[Confirmar na sua versão]**

Fonte: página oficial "Advanced searching - operators reference" (https://confluence.atlassian.com/spaces/JIRASOFTWARESERVER/pages/939938745/Advanced+searching+-+operators+reference).

## 2. Filtros de histórico

Em todos os filtros abaixo, `"[STATUS_EM_ANDAMENTO]"`, `"[STATUS_CONCLUIDO]"` e `"[STATUS_REABERTO]"` são placeholders: troque cada um pelo nome exato do status do seu fluxo, mantendo as aspas (por exemplo, `"Em andamento"`). Se o seu fluxo tem mais de um status de andamento ou de conclusão, ajuste o JQL com `IN`. O último filtro tem ainda o placeholder `[USUARIO]`, que vai sem aspas e leva o username (no Data Center, nunca accountId).

Crie os filtros como no capítulo 01: o `EQP | Base | Equipe` deve existir antes.

### EQP | Histórico | Entraram em andamento no mês

**Nome:** EQP | Histórico | Entraram em andamento no mês
**Descrição:** Issues da equipe que passaram para o status de andamento desde o primeiro dia do mês
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND status CHANGED TO "[STATUS_EM_ANDAMENTO]" AFTER startOfMonth()
```

### EQP | Histórico | Concluídas no mês por transição

**Nome:** EQP | Histórico | Concluídas no mês por transição
**Descrição:** Issues que passaram para o status de concluído desde o primeiro dia do mês
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND status CHANGED TO "[STATUS_CONCLUIDO]" AFTER startOfMonth()
```

### EQP | Histórico | Reabertas

**Nome:** EQP | Histórico | Reabertas
**Descrição:** Issues que saíram de concluído para reaberto ao menos uma vez (não diz quantas vezes)
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND status CHANGED FROM "[STATUS_CONCLUIDO]" TO "[STATUS_REABERTO]"
```

### EQP | Histórico | Mudaram de status em 7 dias

**Nome:** EQP | Histórico | Mudaram de status em 7 dias
**Descrição:** Issues com qualquer mudança de status nos últimos 7 dias
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND status CHANGED AFTER "-7d"
```

### EQP | Histórico | Nunca estiveram em andamento

**Nome:** EQP | Histórico | Nunca estiveram em andamento
**Descrição:** Abertas que nunca tiveram o status de andamento
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND statusCategory != Done AND status WAS NOT IN ("[STATUS_EM_ANDAMENTO]")
```

### EQP | Histórico | Status alterado por usuário no mês

**Nome:** EQP | Histórico | Status alterado por usuário no mês
**Descrição:** Issues cujo status foi alterado por um usuário específico desde o primeiro dia do mês (troque [USUARIO] pelo username)
**Critério de data:** mudança de status

```jql
filter = "EQP | Base | Equipe" AND status CHANGED BY [USUARIO] AFTER startOfMonth()
```

### Notas sobre os filtros

- **Issue que nunca mudou de status.** O valor inicial conta como histórico para `WAS`? **Não confirmado.** Teste com uma issue recém-criada, nunca transitada, antes de confiar nos filtros "Nunca estiveram em andamento" e similares.
- **O filtro devolve a issue, não a mudança.** `status CHANGED` lista as issues que tiveram a transição, sem os itens da mudança. Para ver quem mudou e quando, use o REST (seção 4).
- **Combinação com `NOT`.** A forma `NOT (status CHANGED ...)` não está na documentação lida. Por isso o filtro "Parada no mesmo status" do capítulo 03 continua marcado como a validar; nada neste capítulo muda isso. **[Confirmar na sua versão]**
- **Desempenho.** O custo de `WAS`/`CHANGED` em filtros usados por muitos gadgets não está documentado; veja a seção 7.

## 3. Tabela card → fonte

| Card | Fonte | Consulta | Confirmação |
|---|---|---|---|
| Tempo em cada status (média, total, por fase) | App de tempo em status (Time in Status, Status Time Reports, Cycle Time Gadgets). **[Exige app: Time in Status]** | Configuração do app; escopo por filtro salvo ou JQL, conforme o app | Parcial: compatibilidade declarada nas listagens do Marketplace; o cálculo (fuso, horário útil, status repetido) é do app e deve ser testado. Licença paga no Marketplace; consultar o preço atual para o seu número de usuários |
| Tempo em cada status | eazyBI. **[Exige app: eazyBI]** | Medidas "Days in transition status" e "Average days in transition status", com a opção "Import issue change history" | Confirmado para o produto na documentação da eazyBI; importação em lotes, com defasagem. Licença paga no Marketplace; consultar o preço atual para o seu número de usuários |
| Tempo em cada status (média), com gadget | Jira Charting: Average Time in Status. **[Exige app: Jira Charting]** | Gadget; exige campo personalizado do tipo "Time in Status" e reindexação completa | Parcial: as fontes divergem sobre a compatibilidade com Data Center e sobre as telas do campo. **[Confirmar na sua versão]** |
| Tempo em cada status, sem app | REST + script | `GET /rest/api/2/issue/{key}?expand=changelog`, ou em lote pelo `/search` (seção 4); cálculo com `scripts/tempo_em_status.py` (seção 5) | Parcial: endpoint e esquema confirmados na referência REST 9.12.0; o cálculo é inferência nossa. Retroativo |
| Tempo em cada status, no banco | SQL em réplica de leitura | Esboço da seção 6 | Parcial: tabelas e colunas documentadas; a Atlassian recomenda a API. Autoria nossa |
| Tempo em cada status, por campos | Automation | Duas regras: uma inicializa na criação, outra acumula na mudança de status (capítulo 04) | Parcial: gatilhos e `{{fieldChange.*}}` documentados; sem histórico retroativo |
| Tempo de ciclo (criação até resolução) | Gadget nativo Resolution Time | Filtro com `resolution is not EMPTY` ou `statusCategory = Done` | Confirmado para a descrição do gadget; mede criação até resolução, não "primeiro status ativo até concluído". Opções de período e unidade: não confirmado |
| Tempo de ciclo (entre dois status) | App, REST, SQL ou eazyBI | Filtro de apoio: `status CHANGED FROM "[STATUS_EM_ANDAMENTO]" TO "[STATUS_CONCLUIDO]" DURING ("2026-01-01","2026-01-31")`; a duração vem do cálculo sobre o changelog | Confirmado para a sintaxe do JQL; o JQL só diz quais issues cumpriram a transição, não a duração |
| Passagens por status | Jira Charting: Average Number of Times in Status. **[Exige app: Jira Charting]** | Gadget do app (mesmo campo "Time in Status" e reindexação completa) | Parcial (mesmas ressalvas do Jira Charting acima); é média por status, não contagem por issue |
| Passagens por status | eazyBI. **[Exige app: eazyBI]** | Dimensões "Transition Status" e "Transition" | Confirmado na documentação da eazyBI |
| Passagens por status, sem app | REST + contagem | Contar os itens do changelog com `field == "status"` e `toString` igual ao status desejado; some 1 pelo status inicial, como faz a coluna `passagens_*` do script | Confirmado para o esquema; a contagem é inferência. Nenhuma fonte oficial documenta contagem de transições via JQL |
| Reaberturas | Filtro "Reabertas" (seção 2) + REST para contar | Filtro para listar; REST para contar quantas vezes | Confirmado para o formato `CHANGED FROM/TO`; o filtro não diz quantas vezes |
| Histórico com autor e data | REST | `changelog.histories[]`: `author`, `created`, `items[]` | Confirmado na referência REST 9.12.0 |
| Issues que passaram por um status no período | Filtros da seção 2 | `status CHANGED TO "[STATUS_EM_ANDAMENTO]" AFTER startOfMonth()` ou `status WAS "[STATUS_EM_ANDAMENTO]" DURING ("2026-01-01","2026-01-31")` | Confirmado para a sintaxe; a semântica de `DURING` em `WAS` não foi lida em detalhe e deve ser testada. **[Confirmar na sua versão]** |

## 4. REST: receitas

**Autenticação.** Use um token de acesso pessoal (PAT, disponível desde o Jira 8.14) no cabeçalho `Authorization: Bearer <token>`, de uma conta de serviço com permissão de leitura nos projetos da equipe. Nunca escreva o token no código nem em arquivo versionado; leia-o de uma variável de ambiente.

**Uma issue.** `GET /rest/api/2/issue/{key}?expand=changelog`. A resposta traz `changelog{startAt,maxResults,total,histories[]}`. Cada elemento de `histories` tem `id`, `author`, `created` e `items[]`; cada item tem `field`, `fieldtype`, `from`, `fromString`, `to` e `toString`. Exemplo mínimo de uma transição de status:

```json
{
  "changelog": {
    "startAt": 0,
    "maxResults": 1,
    "total": 1,
    "histories": [
      {
        "id": "10001",
        "author": {"name": "usuario1", "displayName": "Usuário Um"},
        "created": "2026-01-10T09:30:00.000+0000",
        "items": [
          {
            "field": "status",
            "fieldtype": "jira",
            "from": "1",
            "fromString": "Aberto",
            "to": "3",
            "toString": "Em andamento"
          }
        ]
      }
    ]
  }
}
```

Para o cálculo de tempo em status, filtre no cliente os itens com `field == "status"`: não há filtro de changelog no servidor (pedido JRASERVER-71168, aberto).

**Em lote.** `GET /rest/api/2/search?jql=<jql>&expand=changelog&fields=status,created,resolutiondate&startAt=0&maxResults=100`. O teto de `maxResults` é ditado pela propriedade `jira.search.views.default.max`. Pagine aumentando `startAt` até cobrir o `total`; use `ORDER BY key` na JQL para a paginação ser estável. Para listas grandes de operandos ou JQL longo, use `POST /rest/api/2/search`.

**Limites conhecidos do changelog.**

- `GET /rest/api/2/issue/{key}/changelog` NÃO consta na referência 9.12.0 (o pedido JRASERVER-59998 segue aberto). Não conte com esse endpoint.
- O `expand=changelog` pode vir truncado em históricos longos; o tamanho de página dele no Data Center não está documentado. **[Confirmar na sua versão]**
- A referência REST 10.x: as páginas retornaram 404 na pesquisa de 12/10/2026. Tudo de REST em 10.x é **não confirmado**; a continuidade em relação à 9.12.0 é presumida.

**Worklog** (referência 9.12.0; em 10.x, não confirmado):

- `GET /rest/api/2/issue/{key}/worklog`
- `GET /rest/api/2/worklog/updated?since=<ms>` (data em milissegundos desde a época)
- `POST /rest/api/2/worklog/list` com corpo `{"ids":[...]}` (máximo de 1000 ids)
- `GET /rest/api/2/worklog/deleted`

**Rate limiting.** Quando o limite estoura, o Jira responde HTTP 429 com o cabeçalho `retry-after`. Use concorrência de 1 a 2 requisições, espere o tempo indicado e tente de novo. A introdução do recurso por versão não foi confirmada. **[Confirmar na sua versão]**

## 5. Script de tempo em status

O script `scripts/tempo_em_status.py` é o caminho retroativo sem app: lê o changelog pelo REST (seção 4) e calcula localmente. Ele usa só a biblioteca padrão do Python 3. Calcula:

- o tempo em cada status, por issue;
- as passagens por status (quantas vezes a issue entrou em cada um);
- o tempo de ciclo entre dois status à escolha;
- as reaberturas.

Como rodar:

```bash
export JIRA_PAT="<seu token>"
python3 scripts/tempo_em_status.py --url https://jira.exemplo.com --jql 'filter = 12345 ORDER BY key' --token-env JIRA_PAT --saida tempos.csv \
  --status-inicio "<seu status de início>" --status-fim "<seu status de conclusão>"
```

As opções são `--url`, `--jql`, `--token-env`, `--saida`, `--status-inicio` e `--status-fim`. As duas últimas definem o par de status do tempo de ciclo; os detalhes de cada opção e do formato do CSV ficam na ajuda do próprio script (`--help`). Exemplo num fluxo em português: `--status-inicio "Em andamento" --status-fim "Concluído"`. Os padrões do script são `In Progress` e `Done` (fluxo em inglês); num fluxo em português, sem as duas opções `ciclo_horas` sai vazio e `reaberturas` sai 0. A coluna `reaberturas` conta TODA saída de `--status-fim` para outro status (Done para Closed também conta), enquanto o filtro "Reabertas" exige sair de concluído PARA reaberto. Se o changelog lido tiver menos mudanças que `changelog.total`, o script avisa em stderr com a chave da issue. O último intervalo de uma issue reaberta cujo `resolutiondate` não foi limpo é truncado em 0 (o intervalo não fica negativo).

Validação: o cálculo foi testado apenas com fixtures no formato do REST do Jira; **não foi validado contra um Jira real**. Antes de confiar nos números, escolha duas ou três issues de histórico conhecido e confira o resultado do script com a aba Histórico de cada uma. Confira em especial uma issue que nunca mudou de status e uma que foi reaberta.

## 6. SQL (esboço de autoria nossa, PostgreSQL)

Este esboço é nosso, não vem da Atlassian, e não foi executado contra um banco real. A Atlassian recomenda usar a API em vez de consulta direta ao banco, e não há declaração atual de suporte oficial à leitura direta. Use apenas em réplica de leitura, com usuário somente leitura e aprovação do administrador. O esquema não é contrato de API e a consulta direta contorna a segurança de projetos.

```sql
WITH st AS (
  SELECT g.issueid, g.created, ci.oldstring, ci.newstring
  FROM changegroup g
  JOIN changeitem ci ON ci.groupid = g.id
  WHERE ci.field = 'status'
)
SELECT issueid,
       newstring AS status,
       created AS entrou_em,
       LEAD(created) OVER (PARTITION BY issueid ORDER BY created) AS saiu_em
FROM st;
```

Limites do esboço: falta o status inicial (use a data de criação da issue, `jiraissue.created`) e a última linha, em que `saiu_em` nulo significa "até agora" ou até a resolução. Fonte do esquema: https://developer.atlassian.com/server/jira/platform/database-change-history/.

## 7. Desempenho e limites

- `jira.search.views.default.max` (limite "soft", padrão 1000 segundo um KB da Atlassian) e `jira.search.views.max.limit` (limite "hard"; excedê-lo gera erro 403) controlam quantas issues uma busca devolve. Não eleve o primeiro sem motivo.
- O impacto de `WAS`/`CHANGED` no desempenho de filtros usados por muitos gadgets não está documentado. **Meça** na sua instância: abra o dashboard com e sem os filtros de histórico e compare os tempos.
- Não existe guia oficial de desempenho para `WAS`/`CHANGED` nem para `expand=changelog` em lote em instâncias grandes. O que há de geral: restringir por projeto e evitar `filter IN` e funções com muitos IDs.
- O rate limiting só vale para chamadas REST externas, como as do script; o dashboard em si não é afetado por ele.
- Para o script, prefira horários de baixo uso e carga incremental pelo campo `updated`.

## 8. Que caminho escolher

**Já tem app de tempo em status.** Use o app para média e total por status, com o filtro `EQP | Base | Equipe` como escopo se o app aceitar filtro salvo. Use os filtros da seção 2 para "passou pelo status" e "reabertas", que não dependem do app, e o REST só para a lista de mudanças com autor e data. Confira uma issue de histórico conhecido contra o app.

**Não tem, mas pode instalar.** Veja a comparação do capítulo 04. Considere o calendário do Data Center (Atlassian): 30/03/2026 foi o fim de vendas a novos clientes; 30/03/2028 é quando os clientes existentes deixam de comprar produtos, apps e expansões; 28/03/2029 é o fim de vida, com assinaturas e apps somente leitura. Um dashboard novo tem, portanto, horizonte de uso até 03/2029 (https://www.atlassian.com/licensing/data-center-end-of-life). A eazyBI e a ScriptRunner declararam, em posts dos próprios fornecedores, compromisso com o Data Center até o fim de vida; para os demais apps não há compromisso confirmado. Licença paga no Marketplace; consultar o preço atual para o seu número de usuários.

**Não pode instalar.** Os filtros da seção 2 cobrem as contagens (quem passou, quem foi reaberta). Para tempo em status, o caminho retroativo é o REST com o script da seção 5; o Automation com campos personalizados (capítulo 04) mede só a partir da ativação. O SQL da seção 6 é a terceira via, com as restrições citadas.

**Migração para o Cloud.** Se a migração para o Jira Cloud entrar no horizonte, a camada REST (a coleta do changelog e o cálculo local) pode ser reaproveitada, mas as APIs do Cloud são diferentes das do Data Center: será preciso adaptar os endpoints, a autenticação e a paginação. **[Confirmar na sua versão]**
