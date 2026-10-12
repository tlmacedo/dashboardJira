# Capítulo 01: filtros salvos

Este capítulo monta a base de todo o dashboard: os filtros salvos. Cada gadget dos próximos capítulos aponta para um destes filtros, sempre pelo nome exato. Por isso os nomes do catálogo (seção 7) fazem parte da interface do guia e não devem ser alterados.

Ao final, você terá o filtro-base apontando para a sua equipe, os 16 filtros do catálogo criados e compartilhados, e saberá como recortar por time, tipo, responsável e período.

## 1. Como o filtro-base funciona

Existe um único filtro-base, chamado `EQP | Base | Equipe`. Ele define quais issues pertencem à equipe: as dos projetos escolhidos, atribuídas aos membros da equipe. Todos os outros filtros começam com `filter = "EQP | Base | Equipe"` e acrescentam uma condição (tipo, categoria de status, data).

A vantagem é a manutenção: quando alguém entra ou sai da equipe, ou quando um projeto é acrescentado, você altera o filtro-base em um único lugar e todos os derivados, e os gadgets que usam esses derivados, passam a refletir a mudança.

## 2. Dois modos do filtro-base

**Modo com grupo (recomendado).** A equipe é um grupo do Jira. A entrada e a saída de pessoas é feita na administração de grupos, sem editar o filtro.

```jql
project in ([CHAVE_DO_PROJETO]) AND assignee in membersOf("grupo-da-equipe")
```

**Modo com lista de usernames.** Use quando o grupo não existe ou não pode ser criado. No Data Center a identificação é por username (ou user key), nunca por accountId. Troque `usuario1`, `usuario2` e `usuario3` pelos usernames reais.

```jql
project in ([CHAVE_DO_PROJETO]) AND assignee in (usuario1, usuario2, usuario3)
```

Para usar o segundo modo, edite a consulta do filtro `EQP | Base | Equipe` e mantenha o nome. Os derivados continuam funcionando sem alteração.

## 3. Cuidados

- **Confira o grupo antes.** Se `grupo-da-equipe` não existir, o Jira acusa erro na consulta. Se existir mas estiver vazio, o filtro funciona e não retorna nada, o que parece um dashboard zerado. Verifique em Administração > Gerenciamento de usuários (grupos) e teste o filtro no Navegador de Tarefas antes de criar os derivados.
- **Um único dono para os filtros.** Os filtros são referenciados por nome. Se dois filtros com o mesmo nome pertencem a donos diferentes, a referência pode ficar ambígua. Mantenha todos os filtros do catálogo com o mesmo dono (de preferência uma conta de serviço ou a pessoa responsável pelo dashboard) e evite criar cópias com o mesmo nome.
- **Alternativa por ID.** Se a referência por nome causar problema, use o ID numérico do filtro, que aparece na URL ao abrir o filtro. Em vez do nome, a consulta usa `filter = 12345` (com o seu número). O custo é a legibilidade: o ID não diz o que o filtro faz, então anote a correspondência.
- **Sem ORDER BY no filtro-base.** Deixe a ordenação para os filtros que alimentam listas. Um `ORDER BY` herdado do base pode conflitar com a ordenação dos derivados.

## 4. Segmentação por time, tipo, responsável e período

É preciso ser honesto sobre um limite: os dashboards nativos do Jira Data Center não têm filtros interativos globais. Não existe uma caixa no topo do dashboard em que o leitor escolha "este time" ou "este período" e todos os gadgets reajam. **[Confirmar na sua versão]**

Há opções para contornar o limite, cada uma com seu preço:

| Opção | Prós | Contras | Custo |
|---|---|---|---|
| Filtros salvos separados por recorte | Simples e nativo | Muitos filtros e gadgets duplicados | Zero |
| JQL com funções de data (`startOfWeek()`, `startOfMonth()`, `-30d`) | Sempre atualiza sozinho | Recortes fixos | Zero |
| Navegador de Tarefas para período livre | Qualquer data | Manual e não alimenta o dashboard | Zero |
| eazyBI com filtros de página | Filtros interativos de período, time e tipo | Dados importados em lotes e configuração própria. **[Exige app: eazyBI]** | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários |
| Outras soluções do Marketplace | Podem oferecer filtros interativos | Só recomendar após confirmar a compatibilidade com Data Center na listagem do app. Nenhuma é nomeada neste guia | Consultar a listagem do app |

O catálogo da seção 7 usa as duas primeiras linhas da tabela: filtros separados por recorte e funções de data. Para um time, tipo ou responsável diferente, duplique o filtro-base com outro grupo ou outra lista de usernames e crie os derivados que precisar, seguindo o padrão de nomes.

## 5. Formato de data

Nos textos e nos nomes, as datas aparecem em dd/mm/aaaa. Dentro do JQL, uma data literal usa sempre `aaaa-mm-dd`. Exemplo: o dia 15/10/2026 vira `"2026-10-15"` no JQL. Sempre que possível, prefira as funções (`startOfMonth()`, `-30d`), que dispensam datas literais.

## 6. Como criar e compartilhar um filtro

1. Abra o Navegador de Tarefas (Filtros > Pesquisar tarefas) e mude para o modo de pesquisa Avançada, que aceita JQL. **[Confirmar na sua versão]**
2. Cole o JQL do filtro.
3. Execute a pesquisa e confira se o resultado faz sentido (não está vazio nem vem com erro).
4. Use "Salvar como" e digite o nome exato do catálogo.
5. Em Filtros > Gerenciar filtros, abra o filtro e compartilhe com o grupo da equipe (ou com os projetos envolvidos), para que quem enxerga o dashboard enxergue também os dados dos gadgets.
6. Crie primeiro o `EQP | Base | Equipe`, depois os derivados: um derivado só funciona se o filtro que ele referencia já existir.

## 7. Catálogo de filtros

Cada entrada traz nome, descrição, critério de data e o JQL pronto para colar. Troque `[CHAVE_DO_PROJETO]` e `grupo-da-equipe` pelos dados do seu ambiente (capítulo 00).

### EQP | Base | Equipe

**Nome:** EQP | Base | Equipe
**Descrição:** Base de todos os filtros: issues dos projetos da equipe atribuídas a seus membros
**Critério de data:** nenhum (retrato do momento)

```jql
project in ([CHAVE_DO_PROJETO]) AND assignee in membersOf("grupo-da-equipe")
```

### EQP | Base | Equipe sem responsável

**Nome:** EQP | Base | Equipe sem responsável
**Descrição:** Base alternativa para issues sem responsável nos projetos da equipe
**Critério de data:** nenhum (retrato do momento)

```jql
project in ([CHAVE_DO_PROJETO]) AND assignee is EMPTY
```

### EQP | Resumo | Tarefas

**Nome:** EQP | Resumo | Tarefas
**Descrição:** Tarefas padrão (sem subtarefas)
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND issuetype in standardIssueTypes()
```

### EQP | Resumo | Subtarefas

**Nome:** EQP | Resumo | Subtarefas
**Descrição:** Somente subtarefas
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND issuetype in subTaskIssueTypes()
```

### EQP | Resumo | Concluídas

**Nome:** EQP | Resumo | Concluídas
**Descrição:** Issues em categoria Concluído
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND statusCategory = Done
```

### EQP | Resumo | Em andamento

**Nome:** EQP | Resumo | Em andamento
**Descrição:** Issues em categoria Em andamento
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND statusCategory = "In Progress"
```

### EQP | Resumo | A fazer

**Nome:** EQP | Resumo | A fazer
**Descrição:** Issues em categoria A fazer
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe" AND statusCategory = "To Do"
```

### EQP | Resumo | Atrasadas

**Nome:** EQP | Resumo | Atrasadas
**Descrição:** Abertas com vencimento anterior a hoje
**Critério de data:** vencimento

```jql
filter = "EQP | Base | Equipe" AND resolution = Unresolved AND duedate < startOfDay()
```

### EQP | Resumo | Sem responsável

**Nome:** EQP | Resumo | Sem responsável
**Descrição:** Abertas sem responsável
**Critério de data:** nenhum (retrato do momento)

```jql
filter = "EQP | Base | Equipe sem responsável" AND resolution = Unresolved
```

### EQP | Período | Criadas no mês

**Nome:** EQP | Período | Criadas no mês
**Descrição:** Criadas desde o primeiro dia do mês atual
**Critério de data:** criação

```jql
filter = "EQP | Base | Equipe" AND created >= startOfMonth()
```

### EQP | Período | Resolvidas no mês

**Nome:** EQP | Período | Resolvidas no mês
**Descrição:** Resolvidas desde o primeiro dia do mês atual
**Critério de data:** resolução

```jql
filter = "EQP | Base | Equipe" AND resolved >= startOfMonth()
```

### EQP | Período | Vencendo na semana

**Nome:** EQP | Período | Vencendo na semana
**Descrição:** Vencimento entre segunda e domingo da semana atual
**Critério de data:** vencimento

```jql
filter = "EQP | Base | Equipe" AND duedate >= startOfWeek() AND duedate <= endOfWeek()
```

Nota: os limites da semana dependem do primeiro dia da semana configurado no Jira e do locale do usuário, então a semana pode ir de domingo a sábado em vez de segunda a domingo. **[Confirmar na sua versão]** Para conferir, use uma tarefa com vencimento no domingo e veja se ela entra na semana atual ou na seguinte.

### EQP | Período | Criadas em 30 dias

**Nome:** EQP | Período | Criadas em 30 dias
**Descrição:** Criadas nos últimos 30 dias
**Critério de data:** criação

```jql
filter = "EQP | Base | Equipe" AND created >= -30d
```

### EQP | Período | Resolvidas em 30 dias

**Nome:** EQP | Período | Resolvidas em 30 dias
**Descrição:** Resolvidas nos últimos 30 dias
**Critério de data:** resolução

```jql
filter = "EQP | Base | Equipe" AND resolved >= -30d
```

### EQP | Período | Atualizadas em 30 dias

**Nome:** EQP | Período | Atualizadas em 30 dias
**Descrição:** Atualizadas nos últimos 30 dias
**Critério de data:** atualização

```jql
filter = "EQP | Base | Equipe" AND updated >= -30d
```

### EQP | Período | Criadas em 3 meses

**Nome:** EQP | Período | Criadas em 3 meses
**Descrição:** Criadas no mês atual e nos 2 anteriores (o JQL não tem início de trimestre)
**Critério de data:** criação

```jql
filter = "EQP | Base | Equipe" AND created >= startOfMonth(-2)
```
