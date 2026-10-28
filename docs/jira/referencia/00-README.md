# Referência local do Jira Data Center: índice e guia de leitura

Data: 28/10/2026. Pasta: `docs/jira/referencia/`. Esta página não traz conteúdo novo do Jira: ela diz **o que há em cada um dos quatro documentos**, **como lê-los** e **o que ainda precisa ser confirmado quando o usuário liberar a primeira conexão**. As propostas de cards que saíram desta referência estão em `docs/superpowers/ideias-de-cards-a-partir-da-referencia.md`.

## Avisos que valem para os quatro documentos

1. **Nenhum exemplo foi testado em servidor real.** Todo JQL, endpoint, corpo de requisição e resposta foi escrito a partir de documentação pública (pesquisa na web, só leitura) e de leitura do nosso código. Nenhuma instância de Jira foi acessada. Trate cada exemplo como hipótese até a primeira conexão confirmá-lo. A confirmação só acontece com a flag `JIRA_LIVE_ENABLED` ligada **pelo usuário** (única trava; nenhuma IA a liga), uma chamada por vez, na ordem de `docs/jira/checklist-primeira-conexao.md`. Depois de confirmar, atualize `docs/jira/matriz-compatibilidade.md` (troque "não validado" por "confirmado em aaaa-mm-dd").
2. **Data Center (DC) x Cloud.** O alvo é o **Jira Software Data Center** (a empresa não usa Cloud). A documentação pública mistura os dois, e várias diferenças quebram código:
   - identidade: DC usa `name` (username) e `key` (`JIRAUSER...`); Cloud usa `accountId`;
   - changelog: DC só tem `expand=changelog`; o endpoint paginado `issue/{key}/changelog` é do Cloud (ver a primeira lacuna abaixo);
   - busca: DC pagina com `startAt`/`total`; Cloud novo usa `nextPageToken` e `/search/jql`;
   - `parent` cobre épico -> filhos só no Cloud; no DC use `"Epic Link"`;
   - autenticação: PAT Bearer só no DC (8.14+); Cloud usa e-mail + API token.
   Itens que só existem no Cloud aparecem marcados `[Cloud]` ou "Cloud" nas observações e **não devem ser usados** no painel.
3. **Versões.** Os textos vêm da documentação do DC 11.2/11.3 (JQL, filtros, administração), 10.2/10.4 (relatórios do Jira Software) e 9.12 (REST). Entre 9.x e 11.x o conceito muda pouco, mas isso não foi conferido página a página. A versão real do servidor só se sabe pela primeira conexão.
4. **Paráfrase e nomes fictícios.** Usuários `ana.silva`, `bruno.lima`, `carla.souza`, grupo `squad-alfa`, projeto `TG`, hosts `.invalid`. Nada aqui é dado real. Mantenha assim ao copiar trechos para outros arquivos.

## Os quatro documentos

| Arquivo | O que cobre | Entradas (aprox.) | Use quando |
|---|---|---|---|
| `01-jql-basico-ao-avancado.md` | JQL: estrutura, 32 entradas de campos, operadores, palavras-chave, funções, datas, aspas, ordenação, histórico (`WAS`/`CHANGED`), busca de texto, e a seção 11 com **40 padrões prontos** de gestão (abertas, atrasadas, paradas, reabertas, auditoria, sincronização incremental). | ~117 (77 de referência + 40 padrões) | Escrever ou revisar um JQL; saber que campo aceita que operador; montar consulta de conferência. |
| `02-filtros-buscas-boards-dashboards.md` | Busca rápida/básica/avançada, filtros salvos e compartilhamento, exportação e alteração em lote, quadros (filtro, quick filters, raias, colunas, WIP), dashboards e gadgets, relatórios do Jira Software (burndown, CFD, controle...), Advanced Roadmaps, assinaturas. Cada entrada termina com "Ideia para o nosso painel". | ~44 | Entender o que o Jira já oferece (gadgets e relatórios) e de onde vêm as ideias de cards. |
| `03-rest-api-data-center.md` | REST do DC: autenticação (Basic, PAT), plataforma (`/rest/api/2`: `search`, `issue`, `field`, `status`, `user`, `filter`...), Agile (`/rest/agile/1.0`), webhooks, erros, limite de taxa, CAPTCHA, boas práticas de carga, e a seção 6 "Como o nosso painel usa" (tabela do que o código chama hoje) e 7 "O que ainda não usamos". | ~35 entradas + tabela de uso (11 linhas) + 11 itens de oportunidade | Escrever ou depurar o cliente HTTP, a sincronização, o tratamento de 401/403/404/429. |
| `04-gestao-administracao-e-analise.md` | Workflows e categorias de status, resolução, esquemas e permissões, campos personalizados, links entre issues, time tracking e worklog, changelog e auditoria, índice e desempenho, exportação, medições de fluxo (lead, cycle, vazão, WIP, aging), ausências e capacidade, automação, webhooks, usuários inativos. Termina com **17 perguntas que só o servidor real responde**. | ~39 + 17 perguntas | Decidir regra de negócio (o que é "concluída", quem pode alterar) e entender o que o servidor pode estar fazendo por trás dos dados. |

## Como ler o padrão "Finalidade -> comando"

Todas as entradas seguem a mesma ideia: **uma linha de Finalidade (para que serve), o comando ou exemplo, as Observações (pegadinhas e limites) e a Fonte (URL pública)**. Os rótulos variam um pouco por documento:

| Documento | Sequência de cada entrada |
|---|---|
| 01 | **Finalidade** -> **Exemplo** (bloco `jql`) -> **Observações** (começam por `[doc]` ou `[composto]`) -> **Fonte** |
| 02 | **Finalidade** -> **Como fazer / comando** (menu do Jira ou JQL) -> **Observações** -> **Fonte** -> **Ideia para o nosso painel** |
| 03 | **Finalidade** -> **Requisição** (bloco `http`) -> **Corpo / resposta (campos que importam)** -> **Observações** -> **Fonte** |
| 04 | **Finalidade** -> **Como fazer / comando** -> **Observações** (começam por `confirmado`, `secundário` ou `conhecimento geral`) -> **Fonte** -> **Uso possível no nosso painel** |

Dica de leitura: vá direto à linha **Observações**. É ali que estão o que quebra (por exemplo, `!=` não traz campo vazio; `due` é só data; `labels` não aceita `CHANGED`).

## Legenda de confiança

| Marca | Onde aparece | Significado | Confiança |
|---|---|---|---|
| `[doc]` | 01 | O exemplo ou a regra vem da documentação oficial lida (às vezes com chave ou nome trocado por um fictício). | Alta quanto à sintaxe; ainda sem teste no nosso servidor. |
| `[composto]` | 01 (toda a seção 11, salvo indicação) | Consulta montada pelo autor aplicando as regras da documentação. A regra é certa; a combinação não foi vista funcionando. | Média. |
| `confirmado` | 04 | Lido na página oficial citada. | Alta. |
| `secundário` | 04 | Visto em resultado de busca, KB ou comunidade, não na página oficial. | Baixa até validar. |
| `conhecimento geral` | 04 | Prática conhecida do produto, não relida nesta pesquisa. | Baixa até validar. |
| `[não confirmado]` | 02, 03 | O texto diz que a leitura parcial não permitiu confirmar (por exemplo, `GET /priority` simples, `filter/my`, `jql/parse`). | Baixa; confirmar antes de usar. |
| `[Cloud]` / "Cloud" | 01, 02, 03 | Existe só no Cloud ou se comporta diferente lá. | Não usar no DC. |
| **ALERTA** | 03 | Risco concreto para o nosso código (ex.: `issue/{key}/changelog`). | Tratar como bloqueio até confirmar. |
| "Ideia para o nosso painel" / "Uso possível" | 02, 04 | Ligação da entrada com um card ou tela. É sugestão, não decisão. | Passa pelo plano antes de virar tarefa. |

## Atalhos: onde procurar

- **"Quais atrasadas, paradas, reabertas?"** -> 01 seção 11 (padrões 3, 7, 8, 14) e 04 seção 8.
- **"Quem trocou responsável/vencimento/label?"** -> 01 seção 9 e padrões 25 a 28; 04 seção 5. Atenção: `duedate` e `labels` **não têm** `CHANGED`/`WAS`; só o changelog responde.
- **"O que é concluída?"** -> 01 campos `statusCategory` e `resolution`; 04 seção 1.
- **"Como paginar sem perder issue?"** -> 03 entrada `search` e seção 5 "Boas práticas de carga".
- **"Por que deu 404/403/429?"** -> 03 seção 5.
- **"Que gadget ou relatório do Jira tem equivalente no painel?"** -> 02 seções 5 e 6.
- **"O que o servidor real pode me desmentir?"** -> 04 "Perguntas que só o servidor real responde" e a lista abaixo.

## Lacunas e coisas a confirmar na primeira conexão

Lista consolidada dos quatro documentos, da mais arriscada para a menos. Cada item diz onde se confirma (`checklist` = `docs/jira/checklist-primeira-conexao.md`; `matriz` = `docs/jira/matriz-compatibilidade.md`). Nada daqui foi confirmado.

**Sincronização (risco alto)**

1. **O endpoint paginado de changelog `GET /rest/api/2/issue/{key}/changelog` NÃO consta na referência do DC 9.12.** Fontes de comunidade o tratam como exclusivo do Cloud (03, entrada `issue/{key}/changelog`; 04, "Aba History"). O nosso `JiraClient.fullChangelog` o usa quando o changelog embutido vem truncado, então **o risco é um 404 que interrompe a sincronização** com `HTTP_404`. Agravante verificado no código: `Syncer.truncado()` devolve "truncado" também quando a issue **não traz** o objeto `changelog`; se o servidor ignorar o `expand` (item 2), **toda issue** cairia no endpoint. Além disso, `fullChangelog` lê a lista `values` (formato do Cloud), enquanto o changelog embutido vem em `changelog.histories`: trocar de endpoint exige outro leitor. **Alternativa documentada no DC 9.12:** `GET /rest/api/2/issue/{key}?expand=changelog&fields=key,updated` (e `GET /search?...&expand=changelog`, onde `expand` é parâmetro documentado). Confirme também se o changelog embutido vem **completo ou cortado** (há relato de corte em 100 itens). Onde: `checklist`, Passo 4 (testar o endpoint depois do `expand`), linha "GET /issue/{key}/changelog" da `matriz`.
2. **`expand` no corpo do `POST /rest/api/2/search`.** O resumo do 9.12 lista `jql`, `startAt`, `maxResults`, `fields`, `validateQuery`, **sem `expand`**; o nosso cliente envia `"expand":["changelog"]`. Confirme vendo se `changelog` volta na resposta (se não voltar, ver o item 1). O `GET` aceita `expand` documentado, mas a URL tem limite de tamanho. Onde: `checklist`, Passos 3 e 4.
3. **Teto real de `maxResults`.** O servidor não dá erro ao pedir mais que o teto (`jira.search.views.default.max`, `jira.search.views.max.limit`): devolve menos. Valor comum citado: 1000. Relato de bug no DC (JSWSERVER-15503) de que os endpoints Agile ignoram `maxResults` acima de 50, e de uma janela de resultados (`jira.search.platform.max.result.window`, padrão 10.000) a partir do 11.3.12. Avance sempre pelo que veio. Onde: `checklist`, Passo 3.
4. **Limite de taxa (rate limiting).** Opcional, ligado pelo administrador, por usuário e **por nó**; resposta 429 com `Retry-After` e cabeçalhos `X-RateLimit-*`. Não se sabe se está ligado nem com que valores. O cliente atual lê só `Retry-After`.
5. **Fuso e resolução de minuto.** O JQL de datas interpreta no fuso do usuário da credencial e com resolução de **minuto**; `startOfWeek()` depende da localidade; `"-1d"` começa à meia-noite do servidor e `"-24h"` conta 24 horas. Confirme `timeZone` em `GET /myself` e `serverTime` em `GET /serverInfo` (este último o checklist ainda não chama). O painel usa America/Manaus.

**Identidade e histórico**

6. **Formato de `from`/`to` do `assignee` no changelog** (username ou chave interna `JIRAUSER...`), `fromString`/`toString` de `duedate` e de `labels` (separador), e **quem é o autor de mudanças feitas por automação ou post function** (aparece como o usuário que executou). Decide a regra de auditoria. Onde: `checklist`, Passo 4.
7. **Quais campos aceitam `WAS`/`CHANGED` e a que custo.** Confirmados na doc: `assignee`, `reporter`, `fixVersion`, `priority`, `resolution`, `status` (e `creator` com `WAS`). **Não** aceitam: `duedate`, `labels`, `summary`, `sprint`, campos personalizados. Consulta de histórico sem predicado pode ser lenta; há bug conhecido de resultado errado com histórico corrompido (JRASERVER-78497). O padrão 8 (`NOT (status CHANGED AFTER ...)`) é composição nossa, não vista na documentação.
8. **Permissões e visibilidade do usuário de serviço:** ver issues, ver histórico e worklog, e se há segurança por nível de issue (`level`) escondendo tasks; usuários **inativos** ainda como responsáveis. `GET /mypermissions` e `GET /user` ajudam; nenhum dos dois é chamado hoje.

**Campos e vocabulário**

9. **Ids dos campos personalizados** (`customfield_NNNNN`): `OS Version`, `SKU Type`, `Checklist Type`, Story Points, Sprint, `Epic Link`/Parent Link, campo de Review, `cf[12416]`, `cf[11412]`. O id muda por instância; confirme também se o `fields` do `POST /search` aceita o id no lugar do nome. Onde: `checklist`, Passo 2 (`GET /field`).
10. **Status, categorias e resolução reais:** nomes por workflow, a `statusCategory` de cada um, status de espera/bloqueio, existência de "done" sem `resolution` (e o inverso), nomes de prioridade. Onde: `GET /status`, `GET /resolution` (ainda não chamados) e `Filtros_Status_tool.txt`.
11. **Links entre issues:** nome do tipo (`Blocks`?), textos `inward`/`outward`, em que direção a Review aparece, e **se criar ou remover um link altera o `updated` das duas pontas** (se não alterar, o incremental não percebe). Onde: `checklist`, Passo 4, e a linha `fields.issuelinks` da `matriz`.
12. **O que o time realmente usa:** sprints, `fixVersion`, sub-tarefas (contam na carga?), estimativa e worklog, issues de ausência. Isso decide quais gadgets e relatórios do Jira Software valem como cards (02, seções 5 e 6) e se há dependência de apps de terceiros (ScriptRunner, Tempo, ActivityTimeline).

**Endpoints e versões não confirmados**

13. **Não confirmados na leitura parcial do 9.12:** paginação de `issue/{key}/worklog`, `worklog/updated`, `GET /priority` simples, `project/{key}/versions`, `version/{id}`, `filter/my`, `jql/parse` (ausente no DC), `maxResults=0` (use 1). `status/page`, `priority/page`, `resolution/page`, `component/page` são **experimentais**.
14. **Versão exata do DC e referências 10.x/11.x.** A referência REST 10.3.0 deu 404 e o portal novo só entregou o menu; relatórios do Jira Software foram lidos na 10.2/10.4. Chame `GET /serverInfo` (`versionNumbers`) e registre na `matriz`.

**Operação e limites da própria pesquisa**

15. **Índice e defasagem.** A busca JQL usa o índice; a leitura de uma issue por chave vem do banco. Reindexação em segundo plano pode atrasar dados na busca (e no incremental).
16. **Webhooks, automação e Audit log.** Webhook exige conta administradora (a do painel é só leitura) e a página não descreve segredo/HMAC nem reentrega; automação limita o JQL agendado a 1000 issues por padrão; o Audit log cobre configuração, não campos de issue.
17. **Páginas lidas só em parte ou ausentes:** campos de JQL (lidos ~100 mil de ~317 mil caracteres: `watcher`, `voter`, `worklogDate`, `updated`, `timespent`, `workratio` ficaram parciais), gadget Control Chart (não consta no catálogo consultado), watchers e notificações no DC 11.x (sem página oficial), formato Excel de exportação, PAT: criação documentada, **listagem/expiração por API não consta** nesta referência.

## Documentos relacionados

- `docs/jira/matriz-compatibilidade.md`: o que o bscbi-web validou x o que o painel assume; onde registrar a confirmação.
- `docs/jira/checklist-primeira-conexao.md`: ordem e limites da primeira conexão.
- `docs/jira/runbook-sincronizacao.md`: operação da sincronização.
- `docs/superpowers/ideias-de-cards-a-partir-da-referencia.md`: propostas de cards, telas, ações e melhorias técnicas que esta referência sugere.
