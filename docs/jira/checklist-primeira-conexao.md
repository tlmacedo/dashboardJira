# Checklist da primeira conexão ao Jira

Lista **mínima e ordenada** das chamadas da primeira conexão. É um procedimento cuidadoso recomendado; a flag `JIRA_LIVE_ENABLED` é a única trava: com `false` nenhum passo pode ser executado (nem pelo app, nem pelo MCP da Atlassian, nem por `curl`), e só o usuário liga a flag. Com `true`, cada passo abaixo é uma chamada separada: rodar, registrar o resultado e só então seguir para a próxima. Fatos e riscos de cada item: [matriz-compatibilidade.md](matriz-compatibilidade.md).

## Antes do primeiro passo

- [ ] **`JIRA_LIVE_ENABLED` só deve ser ligado pelo usuário.** O padrão é `false` (e `DASHBOARD_DEV_MODE=true`). Quem liga é o usuário, no ambiente dele; este checklist nunca liga a flag.
- [ ] **O token fica em variável de ambiente.** Nunca em arquivo versionado, nunca em `.env` commitado, nunca colado em documento, relatório, log ou mensagem de commit. Se o token aparecer em algum arquivo do repositório, parar e avisar.
- [ ] **Escolher a instância:** Samsung Data Center (`Bearer PAT`) ou Sidia (`Basic`, usuário e token). Uma instância por vez; cada uma repete os passos que precisar.
- [ ] **Pare no primeiro erro 401, 403, 429, qualquer 5xx ou CAPTCHA.** Não repetir, não tentar outra credencial, não contornar. Insistir pode bloquear a conta. Relatar ao usuário o código e a mensagem e esperar instrução.
- [ ] Combinar a lista de chamadas desta sessão (os cinco passos abaixo) e a ordem. Qualquer chamada fora da lista é um passo novo: confirmar a flag e registrar antes de rodar.
- [ ] Limites desta primeira rodada: uma issue no passo 4, `maxResults=1` no passo 3, sem escrita, sem busca ampla.
- [ ] **Os testes automáticos do projeto nunca acessam o Jira real.** Os testes automáticos usam só fixtures e `MockEngine`, mesmo com a flag ligada. A prova técnica fica nos testes `SomenteAFabricaCriaClientesJira` (só a fábrica cria cliente do Jira) e `FlagDesligadaZeroRequisicoesTest` (com a flag desligada, zero requisição). Nenhuma verificação deste checklist é feita por teste: cada passo é uma chamada manual, pela conta do usuário.

## Passo 1: `GET /rest/api/2/myself` (credencial e fuso)

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** Consulta: `GET {base}/rest/api/2/myself`, uma vez, na instância escolhida.

- **O que conferir:**
  - A credencial vale (resposta 200) e o usuário é o esperado (nome e nome de exibição).
  - `timeZone` do usuário. O JQL interpreta datas nesse fuso; o painel usa America/Manaus. Registrar a diferença.
  - Idioma (`locale`), para saber como o Jira formata datas.
- **Quando parar:** 401, 403 ou CAPTCHA (ver a regra acima); usuário diferente do esperado; `timeZone` diferente de America/Manaus (decidir a conversão com o usuário antes de qualquer filtro de data).

## Passo 2: `GET /rest/api/2/field` (ids dos campos do dicionário)

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** Consulta: `GET {base}/rest/api/2/field`, uma vez (a lista de todos os campos).

- **O que conferir:** casar cada campo de `app/config/jira_campos.exemplo.yaml` pelo nome com o `id` real (`customfield_NNNNN`), o `schema.type` e o nome exato. Procurar, em especial:
  - `OS Version` e os demais campos do TG-Basic cuja finalidade ou tipo ainda está "a confirmar" no dicionário: `SKU Type`, `Checklist Type`, `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code` e `Reporter` (o `Reporter` é o PL: ver o passo extra do Reporter);
  - os campos de trabalho dos cards (Plano 5): `Story Points`, `Epic Link` e `Flagged` (nomes e tipos "a confirmar"; sem o id certo o painel grava pontos, épico e flag como ausentes e os cards avisam, não mostram zero) e os nativos `priority`, `timeoriginalestimate`, `timeestimate`, `timespent` e `components`. **Atenção (migração `V2` não é retroativa):** se o banco já tiver issues gravadas antes do bloco 5a, elas ficam com esses campos nulos até serem atualizadas, o aviso de campos indisponíveis some com UMA issue com prioridade e AB-06/MT-12 mostram "(sem)" inflado; faça a recarga completa (Plano 14b, Tarefa 7). Em banco novo a carga inicial não tem o problema;
  - em `status_map.yaml`, conferir `ordem` (sequência do fluxo, base da "devolvida") e `resolucoesNegativas` (por exemplo `Won't Do`, `Duplicate`) contra os status e as resoluções reais do Jira;
  - `cf[12416]` (`customfield_12416`) e `cf[11412]` (`customfield_11412`): registrar nome e tipo, pois nenhum dos dois consta do bscbi-web;
  - nomes duplicados, que tornariam o JQL por nome ambíguo.
  - Preencher `customId` só na cópia local do dicionário, nunca no modelo `.exemplo` com dado interno.
- **Quando parar:** campo do dicionário ausente ou duplicado; o tipo real contradiz o `tipo` do YAML (por exemplo, TEXT versus SELECT); 401, 403, 429, 5xx ou CAPTCHA.

## Passo 3: `POST /rest/api/2/search` com `maxResults=1`, uma vez por ficha de card

**Confirme que a flag de acesso ao Jira está ligada e rode o passo**, **para cada ficha**: mostrar e registrar o JQL gerado (o construtor produz o texto, sem rede) e a ficha a que pertence antes de enviar. Consulta: `POST {base}/rest/api/2/search` com corpo `{"jql": "...", "maxResults": 1, "fields": ["key"]}`. O objetivo é só a contagem de referência (`total`).

- **O que conferir:**
  - O `total` bate com a contagem da mesma busca na tela do Jira (o usuário confere) e com a esperada pela ficha.
  - O JQL é aceito sem erro: campo por nome, aspas, username com `.` entre aspas e o fim do período (`resolved < dia seguinte`).
  - Sondar `=` versus `~` em campo TEXT, ordenação (`ORDER BY labels`) e filtro salvo **não** faz parte deste passo: é uma ficha ou chamada nova, com o JQL mostrado e registrado antes.
  - Fichas com `total` acima de 5.000: anotar; a paginação é uma chamada nova.
  - Contagens por tipo de issue do TG-Basic, para validar a lista de tipos.
- **Quando parar:** contagem diferente da esperada (não ajustar o JQL em silêncio: relatar); erro 400 de JQL (relatar o JQL e a mensagem); 401, 403, 429 ou CAPTCHA; qualquer resposta 5xx (não repetir); qualquer resposta com mais de uma issue (o corpo deve vir com `maxResults=1`).

## Passo 4: UMA issue com `expand=changelog`

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** O usuário indica **uma** chave de issue (de preferência com troca de responsável e de vencimento no histórico). Consulta 4a: `GET {base}/rest/api/2/issue/{chave}?expand=changelog`, ou a busca equivalente `POST /search` com `expand: ["changelog"]` e `maxResults=1`. Consulta 4b, **só se a 4a não trouxer o changelog completo**: `GET {base}/rest/api/2/issue/{chave}/changelog` na mesma issue.

- **O que conferir:**
  - Formato de `from`/`to` (e `fromString`/`toString`) nos itens com `field = assignee`: é usuário, chave ou nome de exibição? (É o `from/to` do assignee que decide a regra de auditoria de responsável.)
  - Itens de `duedate` e `labels`, base da regra de auditoria:
    - `duedate`: comparar `from` com `fromString` (e `to` com `toString`). O painel aceita `aaaa-mm-dd` e `aaaa-mm-dd 00:00:00.0`; qualquer outro formato vira o aviso `VENCIMENTO_ILEGIVEL` na tela de Auditoria. Registrar o formato visto.
    - `labels`: o separador dos labels em `fromString`/`toString`. O painel assume texto separado por espaço; se for vírgula ou outro, registrar e ajustar o leitor com teste.
    - `reporter`: confirmar que a troca de reporter aparece como item `reporter`. O painel guarda só o nome do campo, sem valor (a autorização usa o reporter atual e a tela avisa com `REPORTER_ALTERADO`); não esperar o valor antigo.
  - Changelog truncado: comparar `changelog.total` com `maxResults` e o número de `histories`. Se o histórico vier cortado, o plano B é `expand=changelog` paginado.
  - 4b: se o endpoint `/changelog` existe e pagina. Não verificado; não depender dele antes de testar.
  - Vínculos (a issue com changelog já traz `fields.issuelinks`): conferir em `fields.issuelinks` o nome exato do tipo (`type.name`, esperado `Blocks`), os textos `inward`/`outward` (esperados `is blocked by`/`blocks`) e em que direção a Review aparece em relação à task; se criar ou remover um link altera o `updated` das **duas** issues (se não alterar, o incremental não percebe a mudança: registrar e tratar como divergência na matriz); se o nome diferir, ajustar o `vinculos.yaml` real; registrar só o tipo e a direção na matriz, sem chaves nem resumo de issues reais.
- **Regra:** não copiar dados pessoais das respostas para o repositório (observar e registrar só o formato).
- **Quando parar:** changelog truncado sem como paginar; 401, 403, 429, 5xx ou CAPTCHA; 4b falhou (404): manter o plano B e não insistir.

## Passo 5: `GET /rest/api/2/status` (mapa de status)

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** Consulta: `GET {base}/rest/api/2/status`, uma vez.

- **O que conferir:**
  - Cada status e a sua `statusCategory` (a task é concluída quando a categoria é `done` **e** há `resolution`).
  - Casar com o mapa de status do app (ativo, espera, bloqueado, concluído) e listar status desconhecidos.
  - Status cujo nome muda entre projetos do TG-Basic.
- **Quando parar:** status sem categoria ou com categoria que contradiz o mapa; 401, 403, 429, 5xx ou CAPTCHA.

## Passo extra (fora dos cinco): conferir os ids de `papeis.yaml`

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** O `id` de cada pessoa em `papeis.yaml` é o username dela no Jira, o mesmo valor do campo `assignee` das issues. Consulta: `GET {base}/rest/api/2/user?username={id}`, uma por `id` (o usuário pode preferir conferir a lista na própria tela do Jira).

- **O que conferir:** cada `id` existe no Jira (resposta 200) e o `name` devolvido é igual ao `id` escrito, **inclusive na caixa** (o painel compara exato e em minúsculas: se o Jira devolver maiúsculas, o `id` em minúsculas não casa com o `assignee`). Se divergir, relatar ao usuário; não adaptar o `id` nem o arquivo em silêncio. Quem usa username diferente na instância Sidia é o caso do campo futuro `jira_sidia` (ainda não lido pelo app).
- **Quando parar:** `id` inexistente (404; relatar, sem corrigir o arquivo em silêncio); 401, 403, 429, 5xx ou CAPTCHA. Não copiar nomes nem e-mails da resposta para o repositório.

## Passo extra (fora dos cinco): vocabulário de prioridade e de criticidade

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** Consulta: `GET {base}/rest/api/2/priority`, uma vez (a lista das prioridades do Jira, com nome e id).

- **O que conferir:**
  - Os nomes de `priority` que o painel espera (cards Abertas, Riscos e Metas: `High`, `Medium`, `Low` nos modelos `.exemplo`) existem com essa grafia e essa caixa; anotar os que faltam e os que sobram.
  - `prioridadePadrao`: qual prioridade o painel assume para a task sem prioridade (hoje "sem prioridade"); confirmar com o usuário que é isso mesmo, e registrar o valor definitivo quando o Plano 7 (Checklist e Integridade) trouxer a chave.
  - `labelsCriticidade`: quais labels do TG-Basic indicam criticidade (por exemplo as de impedimento), comparando com os labels de uma amostra de issues (passo 4); os labels reais ficam só no `labels_atraso.yaml` e no YAML de criticidade real, nunca no modelo `.exemplo`.
- **Quando parar:** nome de prioridade diferente do esperado em mais de um item (decidir com o usuário se muda o YAML ou o mapa, sem ajuste silencioso); 401, 403, 429, 5xx ou CAPTCHA.

## Passo extra (fora dos cinco): ordem do fluxo e resoluções negativas

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** Consulta: `GET {base}/rest/api/2/resolution`, uma vez, junto da lista de status do passo 5.

- **O que conferir:**
  - `ordem` do `status_map.yaml`: a sequência real do workflow (por exemplo `To Do`, `In Progress`, `In Review`, `Done`). Ela define a "devolvida" (volta a um status de índice menor); status fora da `ordem` simplesmente não contam como devolução.
  - `resolucoesNegativas` (por exemplo `Won't Do`, `Duplicate`): cada nome existe na lista de resoluções do Jira, com a mesma grafia; resolução de cancelamento que não está na lista conta como entrega (distorce Fechadas e Negativas).
  - Resoluções que o Jira devolve e o YAML não conhece: listar para o usuário decidir se são entrega ou cancelamento.
- **Quando parar:** status da `ordem` que não existe no Jira; resolução de cancelamento não listada; 401, 403, 429, 5xx ou CAPTCHA.

## Passo extra (fora dos cinco): formato do worklog

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** O usuário indica **uma** chave de issue com horas registradas por mais de uma pessoa. Consulta: `GET {base}/rest/api/2/issue/{chave}/worklog`, uma vez.

- **O que conferir:**
  - `author`: o `name` é o username (o mesmo do `assignee`) ou a chave interna `JIRAUSER*` do Data Center? O painel traduz a chave pelo mesmo mapa do changelog; registrar qual veio.
  - `started`: o formato (`aaaa-mm-ddTHH:mm:ss.SSS+0000`) e o fuso; a data do worklog decide o dia em que a hora conta, no fuso de Manaus.
  - `timeSpentSeconds` presente e coerente com `timeSpent` (por exemplo `1h` = 3600).
  - Paginação: `total` contra `maxResults`.
  - O `comment` do worklog **nunca** é lido nem gravado pelo painel (texto livre, pode ter dado pessoal): confirmar apenas que o campo existe, sem copiar nenhum conteúdo.
- **Quando parar:** `author` fora do username e sem como traduzir; `timeSpentSeconds` ausente; 401, 403, 429, 5xx ou CAPTCHA.

## Passo extra (fora dos cinco): o Reporter é o PL

**Confirme que a flag de acesso ao Jira está ligada e rode o passo.** A regra de auditoria (spec 11) trata o `Reporter` da task como o PL: só ele, os coordenadores, o gerente ou o Squad Leader do responsável podem trocar responsável, vencimento e labels de atraso. Consulta: `GET {base}/rest/api/2/issue/{chave}?fields=reporter`, para três issues indicadas pelo usuário, de squads diferentes.

- **O que conferir:**
  - Com o usuário, em cada uma das três: quem aparece em `fields.reporter.name` é de fato o PL daquela task? Se a task nasce por automação ou por um usuário de serviço, o `Reporter` não é o PL e a regra precisa de outra fonte.
  - O `name` do reporter é o mesmo username usado nos `id` de `papeis.yaml` (inclusive a caixa).
  - Quando o `Reporter` muda (item `reporter` no changelog do passo 4), o painel só guarda o nome do campo: confirmar que isso basta para o aviso `REPORTER_ALTERADO`.
- **Quando parar:** `Reporter` que não é o PL em mais de uma das três (decidir com o usuário a fonte do PL antes de usar a regra de autorização); username diferente do `id`; 401, 403, 429, 5xx ou CAPTCHA. Não copiar nomes das respostas para o repositório.

## Depois dos cinco passos

1. Registrar o resultado de cada passo (data, instância, o que foi visto) na matriz, trocando **não validado** por **confirmado em aaaa-mm-dd**.
2. Não copiar para o repositório dados pessoais, tokens nem respostas brutas do Jira.
3. A sincronização periódica segue o [runbook da sincronização](runbook-sincronizacao.md) com a flag ligada (primeiro ciclo pequeno, limites padrão, reconciliação, como desligar e ler o status). A flag `JIRA_LIVE_ENABLED` continua sendo a única trava e só o usuário a muda.
4. Voltar ao modo offline: o usuário desliga a flag (`false`) e encerra a sessão com o token fora do ambiente.
5. Qualquer chamada nova (outra ficha, outra issue, paginação, leitura de filtro salvo) volta ao início: **confirme que a flag de acesso ao Jira está ligada**, mostre e registre o que será enviado e rode só aquela chamada.
