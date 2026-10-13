# Capítulo 05: divergência de datas e botão de sincronização

Este capítulo resolve duas necessidades ligadas ao vencimento das subtarefas: enxergar quais subtarefas têm vencimento diferente do da tarefa-pai e, quando fizer sentido, alinhar essas datas com um botão. O filtro criado aqui alimenta o item 5 do layout do capítulo 06, e a regra do botão é citada por ele.

Ao final, você terá uma regra agendada que sinaliza as divergências, um filtro que as lista, uma regra manual de sincronização e um roteiro para testá-la em homologação antes de liberar em produção.

Pré-requisito: o filtro `EQP | Base | Equipe` do capítulo 01 já existe e retorna as issues da equipe.

Aviso geral sobre o Automation: dependendo da versão do Jira Data Center, o Automation pode ser um recurso incluído ou um app instalado à parte. **[Confirmar na sua versão]** Use a checagem do capítulo 00. Se for um app, o custo é o de uma licença paga no Marketplace; consultar o preço atual para o seu número de usuários.

## 1. Detectar a divergência de vencimento

O JQL nativo não compara o vencimento da subtarefa com o do pai. Não existe uma condição do tipo "`duedate` da subtarefa diferente do `duedate` do pai". Por isso a detecção usa uma regra do Automation que faz a comparação e deixa uma marca (o rótulo `venc-divergente`) na subtarefa. O filtro então busca por essa marca.

### 1.1. Camada 1 (núcleo): regra agendada de sinalização

Em Automação (menu da tarefa ou administração do projeto, conforme a versão), crie a regra abaixo. **[Confirmar na sua versão]**

1. **Nome:** `Sinalizar vencimento divergente`.
2. **Gatilho:** agendado, por exemplo a cada hora e fora do horário de pico. Configure a busca JQL do gatilho como `project in ([CHAVE_DO_PROJETO]) AND issuetype in subTaskIssueTypes() AND statusCategory != Done` e a ação para cada issue encontrada. **[Confirmar na sua versão]** (a existência do gatilho agendado com busca JQL e o número máximo de issues por execução variam).
3. **Condição 1:** o vencimento do pai não é vazio. Use a comparação avançada: `{{issue.parent.duedate}}` não é igual a vazio. **[Confirmar na sua versão]** (a existência da condição de comparação avançada e a sintaxe do smart value variam).
4. **Condição 2 (se/senão):** comparação avançada com `{{issue.duedate}}` diferente de `{{issue.parent.duedate}}`. **[Confirmar na sua versão]**
5. **Se divergir:** ação Editar issue, adicionando o rótulo `venc-divergente`.
6. **Senão:** ação Editar issue, removendo o rótulo `venc-divergente`.

Limites desta camada:

- O dado reflete a última execução da regra. Se alguém mudar uma data agora, o rótulo só muda na próxima execução.
- Editar o rótulo gera histórico e atualiza o campo `updated` da subtarefa. Isso afeta o filtro de issues sem atualização do capítulo 03: uma subtarefa que recebe ou perde o rótulo deixa de parecer parada. Considere esse efeito ao interpretar o indicador.
- A sintaxe dos smart values e o limite de issues por execução do agendamento dependem da versão. **[Confirmar na sua versão]** Teste a regra com poucas subtarefas antes de liberar para o projeto inteiro.

### 1.2. Filtro de divergência

Este é o único filtro novo do capítulo. Crie e compartilhe como no capítulo 01, com o mesmo dono dos demais.

### EQP | Datas | Subtarefas com vencimento divergente

**Nome:** EQP | Datas | Subtarefas com vencimento divergente
**Descrição:** Subtarefas abertas sinalizadas pela regra como divergentes do vencimento do pai
**Critério de data:** vencimento

```jql
filter = "EQP | Base | Equipe" AND labels = venc-divergente AND statusCategory != Done
```

Lembre que o resultado vale até a última execução da regra de sinalização.

## 2. Módulos opcionais para a divergência

### 2.1. ScriptRunner **[Exige app: ScriptRunner]**

Um campo com script permite exibir em tempo real o "Vencimento do pai" e a "Diferença em dias", inclusive em colunas de listas. **[Exige app: ScriptRunner]** **[Confirmar na documentação do app instalado]**

O app também oferece as funções `subtasksOf()` e `parentsOf()`. Exemplos de uso: **[Exige app: ScriptRunner]**

```jql
issueFunction in subtasksOf("project in ([CHAVE_DO_PROJETO]) AND duedate is not EMPTY")
```

```jql
issueFunction in parentsOf("labels = venc-divergente")
```

Atenção: essas funções listam subtarefas ou pais a partir de uma busca. Não há garantia de que comparem datas entre pai e filho. Teste na instância antes de contar com isso. **[Confirmar na documentação do app instalado]**

Limites: licença paga no Marketplace; consultar o preço atual para o seu número de usuários. Os scripts precisam de manutenção, e há impacto de desempenho em instâncias grandes.

### 2.2. eazyBI **[Exige app: eazyBI]**

Monte uma tabela com pai, subtarefa, responsável, status, as duas datas de vencimento e a diferença em dias. **[Exige app: eazyBI]** **[Confirmar na documentação do app instalado]** Limite: a importação é feita em lotes, então a tabela mostra o estado da última importação. Custo: licença paga no Marketplace; consultar o preço atual para o seu número de usuários.

## 3. Exibição nativa no dashboard

Adicione o gadget Resultados do Filtro apontando para `EQP | Datas | Subtarefas com vencimento divergente`, com as colunas Chave, Pai, Responsável, Status e Vencimento. **[Confirmar na sua versão]** (nomes de gadget e de colunas). Limite: o vencimento do pai não aparece ao lado. Para ver a diferença, abra a tarefa-pai ou use um dos módulos da seção 2.

## 4. Onde colocar o botão de sincronização

O botão aplica à subtarefa o vencimento da tarefa-pai. Há quatro caminhos:

| Opção | Viável? | Prós | Contras | Quando usar |
|---|---|---|---|---|
| (a) Automation com gatilho manual | Sim, se a sua versão tiver o gatilho manual. **[Confirmar na sua versão]** (se o Automation vem incluído e se o gatilho manual existe) | **Recomendada.** O botão fica na própria tarefa, sem código | Depende do Automation e do gatilho manual na sua versão | Quando a checagem do capítulo 00 confirmar os dois |
| (b) Webhook de entrada acionado por link | Parcialmente. **[Confirmar na sua versão]** | Não exige botão na tela da tarefa | Um link dispara GET, e o webhook costuma esperar POST; o segredo vai na URL, o que o expõe em históricos e logs | Só se não houver o gatilho manual e houver quem trate o risco do segredo |
| (c) ScriptRunner ou JMWE | Sim, com o app. **[Exige app: ScriptRunner]** **[Exige app: JMWE]** | Resolve sem o Automation (fragmento de tela no ScriptRunner, transição no JMWE) | Exige app e manutenção. Licença paga no Marketplace; consultar o preço atual para o seu número de usuários | Se não houver Automation utilizável e o app já existir |
| (d) Gadget personalizado | Possível, mas não recomendado | Interface sob medida | Custo alto de desenvolvimento e manutenção, e fragilidade a cada atualização | Não recomendado |

O resto do capítulo detalha a opção (a).

## 5. Regra completa da opção (a)

Em Automação, crie uma regra com estes passos. Os nomes de menus e opções variam entre versões. **[Confirmar na sua versão]**

1. **Nome:** `Sincronizar vencimento das subtarefas com o do pai`.
2. **Gatilho: Manual.** Em "quem pode executar", escolha o grupo `[grupo-que-pode-sincronizar]`. Rótulo exibido na tarefa: "Sincronizar vencimento das subtarefas". **[Confirmar na sua versão]** (existência do gatilho manual, restrição por grupo e rótulo exibido).
3. **Condição:** `{{issue.duedate}}` não vazio, em tarefas padrão. **[Confirmar na sua versão]** Se estiver vazio, execute a ação Adicionar comentário na tarefa com o texto "Sincronização cancelada: a tarefa não tem data de vencimento." e encerre a regra.
4. **Ramificação:** Para subtarefas (branch). **[Confirmar na sua versão]** (o nome do ramo e o limite de issues por ramo).
5. **Condição dentro do ramo:** `statusCategory` da subtarefa diferente de `Done`.
6. **Condição dentro do ramo:** comparação avançada, `{{issue.duedate}}` diferente de `{{triggerIssue.duedate}}`. **[Confirmar na sua versão]** Se forem iguais, nada é feito, nem comentário.
7. **Ações, nesta ordem:**
   1. Adicionar comentário na subtarefa com o texto: "Vencimento alterado de {{issue.duedate}} para {{triggerIssue.duedate}} por {{initiator.displayName}} (sincronização com a tarefa {{triggerIssue.key}})."
   2. Editar issue: Data de vencimento = `{{triggerIssue.duedate}}`.

   O comentário vem antes da edição para registrar a data antiga. Em algumas versões, os smart values refletem a issue como ela estava quando a regra começou, mesmo depois da edição; por isso o teste 1 da seção 8 confere se a data antiga aparece no comentário. **[Confirmar na sua versão]**
8. **Opcional, após o ramo:** comentário na tarefa com o resumo da sincronização.

## 6. Smart values usados

| Smart value | Para que serve na regra |
|---|---|
| `{{triggerIssue.duedate}}` | Vencimento da tarefa em que o botão foi acionado (a tarefa-pai), usado dentro do ramo |
| `{{issue.duedate}}` | Vencimento da issue atual: a tarefa fora do ramo, a subtarefa dentro dele |
| `{{initiator.displayName}}` | Nome de quem acionou o botão |
| `{{triggerIssue.key}}` | Chave da tarefa que originou a sincronização |
| `{{issue.parent.duedate}}` | Vencimento do pai, usado na regra agendada da seção 1 |

Todos estes smart values e a forma como se comportam dentro de um ramo dependem da versão. **[Confirmar na sua versão]** O formato de data que aparece nos comentários também depende da versão; se vier fora do esperado, formate com os sufixos de data suportados, como `.jiraDate` ou `.shortDate`, por exemplo `{{triggerIssue.duedate.jiraDate}}`. **[Confirmar na sua versão]**

## 7. Permissões

- O ator da regra precisa, no projeto das subtarefas, de Procurar Projetos, Editar Tarefas, Agendar Tarefas (necessária para alterar o vencimento) e Adicionar Comentários.
- A regra roda como o ator configurado. Uma conta de serviço é recomendada, para que as alterações não dependam de uma pessoa específica. Com isso, o histórico mostra a conta de serviço como autora da edição, e o nome de quem acionou aparece pelo comentário (`{{initiator.displayName}}`).
- Quem aciona o botão precisa estar no grupo `[grupo-que-pode-sincronizar]`.
- Quem administra a regra precisa de permissão de administração do projeto ou do Jira. **[Confirmar na sua versão]**

## 8. Riscos e proteções

| Risco | Proteção na regra |
|---|---|
| Sobrescrever datas legítimas | Botão manual restrito a um grupo; comentário com a data antiga; subtarefas concluídas são ignoradas |
| Pai sem vencimento | A regra encerra sem alterar nada e comenta na tarefa |
| Subtarefa já alinhada | Sem alteração e sem comentário |
| Muitas subtarefas | **[Confirmar na sua versão]** o limite de issues por ramo e por execução; teste com um pai que tenha muitas subtarefas |
| Execução acidental | Restrição por grupo e registro no histórico da regra |

## 9. Roteiro de teste em homologação

Execute os casos abaixo em um projeto de homologação, nunca direto em produção.

| Caso | Preparação | Resultado esperado |
|---|---|---|
| 1. Alinhamento básico | Pai com vencimento 20/10/2026 e 3 subtarefas abertas com datas diferentes | As 3 subtarefas ficam em 20/10/2026, cada uma com comentário. Confira que o comentário traz a data antiga (se trouxer a nova nos dois campos, os smart values refletem o estado inicial da regra de outra forma; ajuste a regra). **[Confirmar na sua versão]** |
| 2. Subtarefa concluída | Subtarefa concluída com data diferente | Não é alterada |
| 3. Já alinhada | Subtarefa já com 20/10/2026 | Não é alterada e não recebe comentário |
| 4. Pai sem vencimento | Pai sem data de vencimento | Nenhuma subtarefa é alterada e a tarefa recebe o comentário de cancelamento |
| 5. Subtarefa sem vencimento | Subtarefa aberta sem data | Recebe a data do pai |
| 6. Usuário fora do grupo | Usuário que não está em `[grupo-que-pode-sincronizar]` | O botão não aparece ou a execução é negada |
| 7. Repetir a execução | Executar de novo logo após o caso 1 | Nenhuma alteração nova |

Depois dos testes, confira o histórico das tarefas e o histórico de execução da regra, e só então copie a regra para produção. Se a regra agendada da seção 1 estiver ativa, aguarde sua próxima execução e confirme que as subtarefas sincronizadas perderam o rótulo `venc-divergente`.
