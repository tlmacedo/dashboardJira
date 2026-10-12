# Capítulo 02: resumo da equipe

Este capítulo liga cada métrica do resumo da equipe a um gadget nativo do Jira Data Center e a um filtro salvo do capítulo 01. Os filtros são citados pelo nome exato; nenhum filtro novo é criado aqui.

Ao final, você terá a tabela métrica → gadget → filtro → configuração, saberá o que os gadgets nativos não fazem e terá validado cada total contra o Navegador de Tarefas.

Pré-requisito: os filtros do capítulo 01 criados e compartilhados com o grupo que vai ver o dashboard.

## 1. Tabela métrica → gadget → filtro → configuração

Os nomes dos gadgets estão em português como aparecem em instalações no idioma pt-BR. O nome exato depende do idioma e da versão da instalação. **[Confirmar na sua versão]**

| Métrica | Gadget **[Confirmar na sua versão]** | Filtro | Configuração |
|---|---|---|---|
| Total de tarefas | Resultados do Filtro ou Estatísticas da Tarefa | `EQP \| Resumo \| Tarefas` | No Resultados do Filtro, o total aparece junto da lista (por exemplo, "x–y de N"); a posição varia por versão. **[Confirmar na sua versão]** O Estatísticas da Tarefa mostra uma contagem por valor da estatística escolhida (por exemplo, Status), não um total único; para obter o total, some as linhas ou use o Resultados do Filtro |
| Total de subtarefas | Resultados do Filtro ou Estatísticas da Tarefa | `EQP \| Resumo \| Subtarefas` | Mesma configuração da linha anterior |
| Atrasadas | Resultados do Filtro | `EQP \| Resumo \| Atrasadas` | Colunas: Chave, Resumo, Responsável, Vencimento |
| Sem responsável | Resultados do Filtro | `EQP \| Resumo \| Sem responsável` | Colunas sugeridas: Chave, Resumo, Status, Criada |
| Distribuição por status | Gráfico de Pizza | `EQP \| Base \| Equipe` | Estatística: Status |
| Distribuição por tipo | Gráfico de Pizza | `EQP \| Base \| Equipe` | Estatística: Tipo de tarefa |
| Distribuição por responsável | Gráfico de Pizza | `EQP \| Base \| Equipe` | Estatística: Responsável |
| Responsável × status | Estatísticas de Filtro Bidimensional | `EQP \| Base \| Equipe` | Eixo X: Status; eixo Y: Responsável |
| Concluídas, em andamento e a fazer | Opção A: Gráfico de Pizza. Opção B: três gadgets Estatísticas da Tarefa | Opção A: `EQP \| Base \| Equipe`. Opção B: `EQP \| Resumo \| Concluídas`, `EQP \| Resumo \| Em andamento` e `EQP \| Resumo \| A fazer` | Opção A: estatística por categoria de status, se a sua versão oferecer. Opção B: um gadget por filtro **[Confirmar na sua versão]** |
| Tendência | Criadas vs Resolvidas | `EQP \| Base \| Equipe` | Período: 30 dias; agrupamento diário ou semanal |
| Concentração | Mapa de Calor | `EQP \| Base \| Equipe` | Estatística: Responsável |

Notas sobre a tabela:

- **Tarefas e subtarefas ficam separadas.** O filtro `EQP | Resumo | Tarefas` exclui subtarefas, e o `EQP | Resumo | Subtarefas` traz só elas. Para o total geral, some os dois valores ou use o gadget direto sobre `EQP | Base | Equipe`.
- **Atrasadas.** O vencimento de hoje não conta como atrasado, porque o filtro compara com o início do dia. Uma tarefa que vence hoje só aparece amanhã, se continuar aberta.
- **Sem responsável.** O filtro parte de `EQP | Base | Equipe sem responsável`, que não depende de membros da equipe, e por isso a contagem inclui issues sem dono dos projetos escolhidos.
- **Concluídas, em andamento e a fazer.** Se o gadget de pizza da sua versão não agrupar por categoria de status, use a opção B. A opção B é a mais previsível, ao custo de três gadgets.
- **Tendência.** O Criadas vs Resolvidas usa o filtro-base e não os filtros de período, porque o próprio gadget já aplica o recorte de 30 dias.

## 2. Limites dos gadgets nativos

- **Percentuais só na pizza.** O Gráfico de Pizza mostra a fatia em percentual. Os demais gadgets exibem apenas contagens.
- **Percentual nos demais gadgets exige planilha ou eazyBI.** Para calcular, por exemplo, a taxa de atrasadas sobre o total, exporte os números para uma planilha ou use o eazyBI. **[Exige app: eazyBI]** Custo do eazyBI: licença paga no Marketplace; consultar o preço atual para o seu número de usuários.
- **Não há card numérico nativo.** Não existe um gadget que mostre um número grande isolado, como um indicador. Os gadgets Resultados do Filtro e Estatísticas da Tarefa mostram o total, mas em formato de lista ou tabela. Quando você precisar de um card real, veja o módulo com eazyBI no capítulo 04.
- **Sem filtros interativos globais.** Como explicado no capítulo 01, o leitor não escolhe o time ou o período no topo do dashboard. Cada recorte é um filtro salvo.

## 3. Como adicionar cada tipo de gadget

O caminho é o mesmo para todos os gadgets deste capítulo:

1. Abra o dashboard e entre no modo de edição (Ferramentas > Editar painel, ou o botão equivalente da sua versão). **[Confirmar na sua versão]**
2. Clique em Adicionar gadget.
3. Procure o gadget pelo nome da tabela da seção 1 e clique em Adicionar.
4. No gadget, em vez de digitar JQL, selecione o filtro salvo pelo nome (comece a digitar `EQP` para localizar).
5. Configure as opções da coluna "Configuração" da tabela.
6. Clique em Salvar. Confira se o gadget carregou números e não uma mensagem de erro.

Nota: o nome do gadget em português depende do idioma da instalação. Se não encontrar um nome, pesquise pelo equivalente em inglês (por exemplo, Filter Results, Pie Chart, Created vs Resolved Chart, Heat Map, Two Dimensional Filter Statistics).

Pontos específicos de cada tipo:

- **Resultados do Filtro:** defina o número de linhas e as colunas. Mantenha poucas linhas (por exemplo, 10), para a lista não dominar o painel.
- **Estatísticas da Tarefa:** escolha o filtro e a estatística; o gadget mostra a contagem por valor.
- **Gráfico de Pizza:** escolha o filtro e a estatística. Esta é a única visão com percentuais.
- **Estatísticas de Filtro Bidimensional:** defina o eixo X (Status) e o eixo Y (Responsável); a célula é a contagem do cruzamento.
- **Criadas vs Resolvidas:** informe o período em dias, o agrupamento (diário ou semanal) e se quer o acumulado.
- **Mapa de Calor:** escolha o filtro e a estatística (Responsável).

## 4. Validação do capítulo

Para cada gadget do capítulo, confira o total contra a fonte:

1. Abra o Navegador de Tarefas e mude para o modo Avançado.
2. Execute o mesmo filtro salvo do gadget: abra-o pelo menu Filtros, ou execute `filter = "<nome do filtro>"` na pesquisa avançada, com o nome exato do catálogo. **[Confirmar na sua versão]**
3. Compare o total de resultados do Navegador com o número do gadget.
4. Se os números divergirem, verifique nesta ordem: o gadget aponta para o filtro certo; o filtro foi compartilhado com o seu usuário; o cache do gadget (atualize a página); a diferença de permissões entre quem criou e quem vê.
5. Para os gadgets de distribuição, a soma das fatias deve igualar o total do Navegador para o mesmo filtro.

Só siga para o próximo capítulo quando todos os totais baterem.
