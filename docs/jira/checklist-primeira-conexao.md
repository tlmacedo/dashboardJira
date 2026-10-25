# Checklist da primeira conexão autorizada ao Jira

Lista **mínima e ordenada** das chamadas a pedir ao usuário. Este documento só descreve; **ninguém faz nenhuma chamada** até o usuário autorizar.

Regra de ouro do projeto: nenhuma consulta ao Jira da empresa sem **autorização expressa** do usuário, **por pesquisa**. Vale para o MCP da Atlassian, para `curl` e para o cliente HTTP do app. Cada passo abaixo é uma pesquisa separada: pedir, esperar o "sim", executar só aquela, registrar o resultado e voltar a pedir para a próxima. Uma autorização não vale para o passo seguinte. Fatos e riscos de cada item: [matriz-compatibilidade.md](matriz-compatibilidade.md).

## Antes de pedir a primeira autorização

- [ ] **`JIRA_LIVE_ENABLED` só deve ser ligado pelo usuário.** O padrão é `false` (e `DASHBOARD_DEV_MODE=true`). Quem liga é o usuário, no ambiente dele; este checklist nunca liga a flag.
- [ ] **O token fica em variável de ambiente.** Nunca em arquivo versionado, nunca em `.env` commitado, nunca colado em documento, relatório, log ou mensagem de commit. Se o token aparecer em algum arquivo do repositório, parar e avisar.
- [ ] **Escolher a instância:** Samsung Data Center (`Bearer PAT`) ou Sidia (`Basic`, usuário e token). Uma instância por vez; cada uma repete os passos que precisar.
- [ ] **Pare no primeiro erro 401, 403, 429, qualquer 5xx ou CAPTCHA.** Não repetir, não tentar outra credencial, não contornar. Insistir pode bloquear a conta. Relatar ao usuário o código e a mensagem e esperar instrução.
- [ ] Confirmar a lista de pesquisas que o usuário aceita rodar nesta sessão (os cinco passos abaixo) e a ordem. Qualquer chamada fora da lista é uma pesquisa nova e exige nova autorização.
- [ ] Limites desta primeira rodada: uma issue no passo 4, `maxResults=1` no passo 3, sem escrita, sem busca ampla.

## Passo 1: `GET /rest/api/2/myself` (credencial e fuso)

**Peça autorização expressa antes.** Consulta: `GET {base}/rest/api/2/myself`, uma vez, na instância escolhida.

- **O que conferir:**
  - A credencial vale (resposta 200) e o usuário é o esperado (nome e nome de exibição).
  - `timeZone` do usuário. O JQL interpreta datas nesse fuso; o painel usa America/Manaus. Registrar a diferença.
  - Idioma (`locale`), para saber como o Jira formata datas.
- **Quando parar:** 401, 403 ou CAPTCHA (ver a regra acima); usuário diferente do esperado; `timeZone` diferente de America/Manaus (decidir a conversão com o usuário antes de qualquer filtro de data).

## Passo 2: `GET /rest/api/2/field` (ids dos campos do dicionário)

**Peça autorização expressa antes.** Consulta: `GET {base}/rest/api/2/field`, uma vez (a lista de todos os campos).

- **O que conferir:** casar cada campo de `app/config/jira_campos.exemplo.yaml` pelo nome com o `id` real (`customfield_NNNNN`), o `schema.type` e o nome exato. Procurar, em especial:
  - `OS Version`, `SKU Type`, `Checklist Type`, `Model Name`;
  - `cf[12416]` (`customfield_12416`) e `cf[11412]` (`customfield_11412`): registrar nome e tipo, pois nenhum dos dois consta do bscbi-web;
  - nomes duplicados, que tornariam o JQL por nome ambíguo.
  - Preencher `customId` só na cópia local do dicionário, nunca no modelo `.exemplo` com dado interno.
- **Quando parar:** campo do dicionário ausente ou duplicado; o tipo real contradiz o `tipo` do YAML (por exemplo, TEXT versus SELECT); 401, 403, 429, 5xx ou CAPTCHA.

## Passo 3: `POST /rest/api/2/search` com `maxResults=1`, uma vez por ficha de card

**Peça autorização expressa antes**, **para cada ficha**: mostrar ao usuário o JQL gerado (o construtor produz o texto, sem rede) e a ficha a que pertence, e só então enviar. Consulta: `POST {base}/rest/api/2/search` com corpo `{"jql": "...", "maxResults": 1, "fields": ["key"]}`. O objetivo é só a contagem de referência (`total`).

- **O que conferir:**
  - O `total` bate com a contagem da mesma busca na tela do Jira (o usuário confere) e com a esperada pela ficha.
  - O JQL é aceito sem erro: campo por nome, aspas, username com `.` entre aspas e o fim do período (`resolved < dia seguinte`).
  - Sondar `=` versus `~` em campo TEXT, ordenação (`ORDER BY labels`) e filtro salvo **não** faz parte deste passo: exige ficha ou pesquisa nova, com autorização própria.
  - Fichas com `total` acima de 5.000: anotar; a paginação exige outro pedido de autorização.
  - Contagens por tipo de issue do TG-Basic, para validar a lista de tipos.
- **Quando parar:** contagem diferente da esperada (não ajustar o JQL em silêncio: relatar); erro 400 de JQL (relatar o JQL e a mensagem); 401, 403, 429 ou CAPTCHA; qualquer resposta 5xx (não repetir); qualquer resposta com mais de uma issue (o corpo deve vir com `maxResults=1`).

## Passo 4: UMA issue com `expand=changelog`

**Peça autorização expressa antes.** O usuário indica **uma** chave de issue (de preferência com troca de responsável e de vencimento no histórico). Consulta 4a: `GET {base}/rest/api/2/issue/{chave}?expand=changelog`, ou a busca equivalente `POST /search` com `expand: ["changelog"]` e `maxResults=1`. Consulta 4b, **só se o usuário autorizar separadamente**: `GET {base}/rest/api/2/issue/{chave}/changelog` na mesma issue.

- **O que conferir:**
  - Formato de `from`/`to` (e `fromString`/`toString`) nos itens com `field = assignee`: é usuário, chave ou nome de exibição?
  - Itens de `duedate` e `labels`, base da regra de auditoria.
  - Changelog truncado: comparar `changelog.total` com `maxResults` e o número de `histories`. Se o histórico vier cortado, o plano B é `expand=changelog` paginado.
  - 4b: se o endpoint `/changelog` existe e pagina. Não verificado; não depender dele antes de testar.
- **Regra:** não copiar dados pessoais das respostas para o repositório (observar e registrar só o formato).
- **Quando parar:** changelog truncado sem como paginar; 401, 403, 429, 5xx ou CAPTCHA; 4b falhou (404): manter o plano B e não insistir.

## Passo 5: `GET /rest/api/2/status` (mapa de status)

**Peça autorização expressa antes.** Consulta: `GET {base}/rest/api/2/status`, uma vez.

- **O que conferir:**
  - Cada status e a sua `statusCategory` (a task é concluída quando a categoria é `done` **e** há `resolution`).
  - Casar com o mapa de status do app (ativo, espera, bloqueado, concluído) e listar status desconhecidos.
  - Status cujo nome muda entre projetos do TG-Basic.
- **Quando parar:** status sem categoria ou com categoria que contradiz o mapa; 401, 403, 429, 5xx ou CAPTCHA.

## Depois dos cinco passos

1. Registrar o resultado de cada passo (data, instância, o que foi visto) na matriz, trocando **não validado** por **confirmado em aaaa-mm-dd**.
2. Não copiar para o repositório dados pessoais, tokens nem respostas brutas do Jira.
3. Desligar `JIRA_LIVE_ENABLED` (o usuário) e encerrar a sessão com o token fora do ambiente.
4. Qualquer chamada nova (outra ficha, outra issue, paginação, leitura de filtro salvo) volta ao início: **peça autorização expressa antes**.
