# Capítulo 03: negligenciados e horas

Este capítulo acrescenta três blocos ao dashboard: os indicadores de trabalho negligenciado, as horas registradas por pessoa e os cards de tempo dedicado. Ele define 8 filtros novos (5 de negligenciados e 3 de horas), que se somam aos 16 do capítulo 01 e usam como base os filtros `EQP | Base | Equipe` e `EQP | Base | Equipe sem responsável`, criados lá. Crie os dois filtros-base antes de criar os filtros deste capítulo.

Os nomes dos filtros fazem parte da interface do guia: os capítulos 04 e 06 os citam pelo nome exato. Não os altere.

## 1. Negligenciados

Negligenciado, neste guia, é trabalho aberto que ninguém está olhando: sem atualização recente, sem estimativa, sem vencimento, sem responsável ou parado no mesmo status. Cada indicador é um filtro salvo, e cada filtro vira um gadget de contagem ou lista no capítulo 02. Como todos medem o estado atual da fila, a maioria não tem critério de data.

Para criar cada filtro, siga o passo a passo da seção 6 do capítulo 01 (Navegador de Tarefas, pesquisa Avançada, "Salvar como" com o nome exato, compartilhamento).

### EQP | Negligenciados | Sem atualização

**Nome:** EQP | Negligenciados | Sem atualização
**Descrição:** Abertas sem atualização há 14 dias ou mais (ajuste o prazo conforme a equipe)
**Critério de data:** atualização

```jql
filter = "EQP | Base | Equipe" AND statusCategory != Done AND updated <= -14d
```

Nota: para outro prazo, troque `-14d` (por exemplo `-7d` ou `-30d`). Qualquer edição ou comentário na issue conta como atualização e tira a tarefa deste filtro.

### EQP | Negligenciados | Sem estimativa

**Nome:** EQP | Negligenciados | Sem estimativa
**Descrição:** Abertas sem estimativa original
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND statusCategory != Done AND originalEstimate is EMPTY
```

### EQP | Negligenciados | Sem vencimento

**Nome:** EQP | Negligenciados | Sem vencimento
**Descrição:** Abertas sem data de vencimento
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND statusCategory != Done AND duedate is EMPTY
```

### EQP | Negligenciados | Sem responsável

**Nome:** EQP | Negligenciados | Sem responsável
**Descrição:** Abertas sem responsável
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe sem responsável" AND statusCategory != Done
```

Nota: este filtro parte do filtro-base alternativo, porque o `EQP | Base | Equipe` exige responsável membro da equipe e jamais retornaria issues sem responsável.

### EQP | Negligenciados | Parada no mesmo status

**Nome:** EQP | Negligenciados | Parada no mesmo status
**Descrição:** **A validar na amostra.** Abertas, criadas há pelo menos 14 dias, sem mudança de status nos últimos 14 dias
**Critério de data:** atualização

```jql
filter = "EQP | Base | Equipe" AND statusCategory != Done AND created <= -14d AND NOT (status CHANGED AFTER "-14d")
```

Aviso: a negação do operador `CHANGED` pode não ser aceita ou pode se comportar de forma diferente conforme a versão, e a ressalva vale para os dois `-14d` do filtro (o de `created` e o de `CHANGED`). **[Confirmar na sua versão]** Pendência: a criação da issue conta como mudança de status? Não confirmado. Teste o filtro numa amostra em que você conheça o histórico: uma tarefa que mudou de status ontem não deve aparecer, uma parada há mais de 14 dias deve aparecer, e uma tarefa criada há 3 dias não deve aparecer. Se a sua versão rejeitar a negação com `CHANGED`, use `EQP | Negligenciados | Sem atualização` como substituto seguro, e registre o resultado do teste. Anote também se a criação da issue contou como mudança de status.

A diferença entre os dois filtros: "sem atualização" considera qualquer edição ou comentário, então uma tarefa comentada ontem sai da lista mesmo sem ter andado. "Parada no status" considera só a mudança de status, então ela continua na lista até que o status mude de fato. Por isso "parada no status" é o indicador mais exigente.

## 2. Horas por pessoa

Os filtros desta seção alimentam o gadget de horas e os cards de tempo dedicado (seção 3).

### EQP | Horas | Com worklog no mês

**Nome:** EQP | Horas | Com worklog no mês
**Descrição:** Issues em que alguém da equipe registrou horas no mês atual (filtra tarefas, não soma horas)
**Critério de data:** worklog

```jql
project in ([CHAVE_DO_PROJETO]) AND worklogAuthor in membersOf("grupo-da-equipe") AND worklogDate >= startOfMonth()
```

Notas:

- `worklogAuthor` e `worklogDate` filtram tarefas; não somam horas. O resultado é a lista de issues com registro no período, não o total de horas de ninguém.
- O uso de `membersOf` com `worklogAuthor` depende da versão. **[Confirmar na sua versão]** Se for rejeitado, troque pela lista de usernames: `worklogAuthor in (usuario1, usuario2, usuario3)`.
- Este filtro não parte do `EQP | Base | Equipe` de propósito: aquele filtra por responsável da issue, e quem registrou horas pode não ser o responsável.
- O fuso do usuário que consulta, comparado ao do servidor, pode deslocar registros feitos perto da virada do dia ou do mês em `worklogDate`. Confira com um registro feito no último dia do mês.

### EQP | Horas | Tarefas com horas

**Nome:** EQP | Horas | Tarefas com horas
**Descrição:** Tarefas padrão com tempo registrado
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND issuetype in standardIssueTypes() AND timespent > 0
```

### EQP | Horas | Subtarefas com horas

**Nome:** EQP | Horas | Subtarefas com horas
**Descrição:** Subtarefas com tempo registrado
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND issuetype in subtaskIssueTypes() AND timespent > 0
```

### Como obter horas por pessoa: comparação das alternativas

| Alternativa | O que entrega | Limite |
|---|---|---|
| JQL com `worklogAuthor` e `worklogDate` | Lista das issues com registro de horas por pessoa e período | Não soma horas; só diz quais tarefas têm registro |
| Relatório de Time Tracking | Estimado × registrado, por versão (projeto ou sprint: não confirmado) | Não aceita período livre; o escopo disponível depende da versão. **[Confirmar na sua versão]** |
| App de Timesheet (Tempo, Simple Reports ou Decadis sumUp) | Soma de horas por pessoa e por dia. **[Exige app: Timesheet — Tempo, Simple Reports ou Decadis sumUp; confirmar compatibilidade com Data Center no Marketplace]** | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. As opções do gadget dependem do app escolhido |
| eazyBI | Estimado × registrado por pessoa e período. **[Exige app: eazyBI]** | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. O eazyBI declara compromisso com o Data Center em post do fornecedor, mas o ciclo de vida do Data Center limita a compra (ver o capítulo 00) |

**Recomendação:** use um app de Timesheet ou o eazyBI para horas por pessoa e período; sem app, use a REST de worklog. O Timesheet é fornecido por apps **[Exige app: Timesheet — Tempo, Simple Reports ou Decadis sumUp; confirmar compatibilidade com Data Center no Marketplace]**; o eazyBI serve quando for preciso comparar com o estimado **[Exige app: eazyBI]**. Opcionalmente, o gadget Gráfico de Pizza de carga de trabalho (Workload Pie Chart) mostra a estimativa restante por responsável, mas não soma Time Spent, e exige o Jira Charting App. **[Exige app: Jira Charting]** **[Confirmar na sua versão]**

### Configuração do Timesheet

**[Exige app: Timesheet]** **[Confirmar na sua versão]** (nomes das opções e posição dos totais variam conforme o app escolhido).

1. Com o app de Timesheet instalado, adicione o gadget Timesheet ao dashboard.
2. Como filtro, escolha `EQP | Horas | Com worklog no mês`.
3. Defina o período como o mês atual.
4. Agrupe por usuário.
5. Se a sua versão oferecer a opção de somar as horas das subtarefas, decida se ela deve ficar ligada e anote a escolha.
6. **Teste com subtarefas.** Escolha uma tarefa-pai com subtarefas que tenham horas registradas, compare o total exibido com a soma manual e anote o resultado: se as horas das subtarefas aparecem uma vez, duas vezes ou nenhuma.

## 3. Cards de tempo dedicado

Os cards respondem "quanto tempo a equipe dedicou" a tarefas e a subtarefas. Dois campos entram em jogo:

- `timespent` (Time Spent): o tempo registrado na própria issue.
- `aggregatetimespent` (Σ Time Spent): o tempo da issue somado ao das suas subtarefas. Os campos Σ não são pesquisáveis em JQL, segundo fonte secundária (JIRAAUTOSERVER-1231); **não confirmado** na página oficial de campos. Por isso nenhum filtro deste capítulo usa `aggregatetimespent`.

| Card | Campo | Filtro | Exibição |
|---|---|---|---|
| Tempo em tarefas | Time Spent | `EQP \| Horas \| Tarefas com horas` | App de Timesheet, um por filtro (ver a configuração do Timesheet neste capítulo) **[Exige app: Timesheet]** **[Confirmar na sua versão]** |
| Tempo em subtarefas | Time Spent | `EQP \| Horas \| Subtarefas com horas` | App de Timesheet, um por filtro (ver a configuração do Timesheet neste capítulo) **[Exige app: Timesheet]** **[Confirmar na sua versão]** |
| Conjunto | Soma dos dois cards acima | Os dois filtros acima | Soma dos dois valores dos Timesheets acima. Alternativa: Σ Time Spent só das tarefas padrão |
| Estimado × realizado | Estimativa original e tempo registrado | Definido no eazyBI | **[Exige app: eazyBI]** (licença paga no Marketplace; consultar o preço atual para o seu número de usuários) |

> **Regra de ouro: nunca some o Σ Time Spent do pai com o Time Spent das subtarefas.** O Σ do pai já contém as horas das subtarefas, então a soma conta tudo duas vezes.

### Exemplo numérico

Uma tarefa-pai com 2h registradas nela mesma e duas subtarefas, de 3h e 1h:

| Item | Valor |
|---|---|
| Time Spent do pai | 2h |
| Σ Time Spent do pai | 6h (2h + 3h + 1h) |
| Card "Tempo em tarefas" | 2h |
| Card "Tempo em subtarefas" | 4h (3h + 1h) |
| Card "Conjunto" (2h + 4h) | 6h |
| Soma errada (Σ do pai + Time Spent das subtarefas) | 10h |
| Soma errada (Σ do pai + Σ de cada subtarefa + Time Spent do pai) | 12h |

O resultado correto do conjunto é 6h, e **não** 12h. Se um número do dashboard passar do total real, procure primeiro uma soma de Σ Time Spent com Time Spent.

### Limites do núcleo e do app

- O gadget Resultados do Filtro mostra a coluna de tempo, mas não totaliza. **[Confirmar na sua versão]**
- O app de Timesheet respeita o filtro escolhido, e o responsável da issue pode diferir de quem registrou as horas. Por isso o filtro `EQP | Horas | Com worklog no mês` usa `worklogAuthor`, e não `assignee`.
- Para totais por tipo (tarefas e subtarefas), use um Timesheet (do app) por filtro, cada um apontando para o seu filtro da tabela acima.
