# Capítulo 00: pré-requisitos e checagem de versão

Este capítulo reúne o que você precisa saber sobre o seu ambiente antes de montar o dashboard. O guia foi escrito com placeholders (como `[CHAVE_DO_PROJETO]` e `grupo-da-equipe`) e com um núcleo que usa apenas recursos nativos do Jira Data Center. Cada lacuna do núcleo vira um módulo opcional, que só vale a pena se o app correspondente estiver disponível.

Ao final, você terá três coisas: a lista de dados do ambiente preenchida, a matriz "nativo × app" para decidir o que implantar e a checagem de versão feita.

## 1. Dados do ambiente a coletar

Preencha a tabela antes de seguir para o capítulo 01. Os capítulos seguintes citam estes dados e trazem alternativas quando um deles estiver em falta.

| Dado | Onde encontrar | Por que importa |
|---|---|---|
| Versão exata do Jira Data Center | Administração > Informações do sistema, ou rodapé de qualquer página | Define quais gadgets e recursos de Automation existem. Muitos pontos deste guia levam a marca **[Confirmar na sua versão]**. |
| Apps instalados (ScriptRunner, eazyBI, Time in Status, JMWE, Automation) | Administração > Gerenciar apps | Define quais módulos opcionais você consegue implantar sem comprar nada. |
| Permissão para instalar apps | Decisão interna (administração do Jira, TI, compras) | Sem permissão, o guia segue só com o núcleo nativo. Custo de app: licença paga no Marketplace; consultar o preço atual para o seu número de usuários. |
| Chaves dos projetos | Lista de projetos, coluna "Chave" | Substituem `[CHAVE_DO_PROJETO]` nos filtros e delimitam a regra de Automation. |
| Usernames ou grupo da equipe | Administração > Gerenciamento de usuários (grupos) | Alimentam o filtro-base. Se o grupo não existir ou estiver vazio, o filtro dá erro ou não retorna nada. Confira o grupo antes e, se preciso, use uma lista de usernames. No Data Center a identificação é por username ou user key. |
| Lista de status do fluxo, indicando quais são "concluído" e quais são "em andamento" | Administração > Tarefas > Status, e o fluxo de trabalho de cada projeto | O guia usa categorias de status. Conferir a categoria de cada status evita contar tarefa concluída como aberta. |
| Tipos de tarefa em uso e se as subtarefas seguem o fluxo do pai | Administração > Tarefas > Tipos de tarefa, e os esquemas de fluxo de trabalho | Separa tarefas padrão de subtarefas nos cards e nas regras. Subtarefas com fluxo próprio mudam o que significa "concluída". |
| Campos personalizados relevantes | Administração > Tarefas > Campos personalizados | Necessários se você optar por campos com Automation (tempo por status, marca de divergência). |
| Time Tracking ativo e estimativa preenchida | Administração > Tarefas > Controle de tempo, e uma amostra de tarefas | Sem Time Tracking ativo não há horas registradas. Sem estimativa preenchida, o filtro de "sem estimativa" retorna quase tudo. |
| Volume aproximado de issues | Navegador de Tarefas sem filtro, total de resultados | Influencia o desempenho dos filtros e a escolha de agendamentos fora do horário de pico. |

## 2. Matriz nativo × app

A tabela resume o que o núcleo nativo entrega e o que exige app. A coluna "Capítulo" aponta onde cada item é tratado.

| Funcionalidade | Nativo? | O que exige | Capítulo |
|---|---|---|---|
| Filtros e JQL de data | Sim | Nada além do próprio Jira. | 01 |
| Gadgets de resumo (totais, pizza, responsável × status, Criadas vs Resolvidas, mapa de calor, lista) | Sim, **[Confirmar na sua versão]** | Conferir se cada gadget existe e como se chama na sua versão. | 02 |
| Percentuais fora do gráfico de pizza | Não | **[Exige app: eazyBI]** | 02 |
| Cards numéricos reais | Não | **[Exige app: eazyBI]** | 02, 03 |
| Sem atualização, sem estimativa, sem vencimento, sem responsável | Sim | Nada além do próprio Jira. | 03 |
| Parada no mesmo status | Parcialmente nativo, a validar na amostra | Variante com `status CHANGED` precisa de teste. A alternativa segura é o filtro de sem atualização. | 03 |
| Tempo por status | Não | **[Exige app: Time in Status ou eazyBI]**, ou campos personalizados alimentados por Automation (mais manutenção, sem histórico retroativo). | 04 |
| Horas por pessoa | Parcialmente: gadget Timesheet, **[Confirmar na sua versão]** | Se o Timesheet não existir na sua versão, ele pode ser um app do Marketplace **[Exige app: Timesheet — confirmar no Marketplace e na compatibilidade com Data Center]**, ou use o eazyBI **[Exige app: eazyBI]**. Estimado × registrado por pessoa e por período exige **[Exige app: eazyBI]**. | 03 |
| Divergência de vencimento entre pai e subtarefa | Não | Automation, ScriptRunner ou eazyBI. **[Exige app: ScriptRunner]** ou **[Exige app: eazyBI]** nos dois últimos casos. | 05 |
| Botão de sincronização | Não | Automation com gatilho manual **[Confirmar na sua versão]**, ou **[Exige app: ScriptRunner]** ou **[Exige app: JMWE]**. | 05 |
| Cards de horas separados por tipo (tarefas e subtarefas) | Parcialmente | Um Timesheet por filtro, **[Confirmar na sua versão]**, ou **[Exige app: eazyBI]** para os cards completos. | 03 |

Duas limitações nativas valem ser lembradas desde já: dashboards nativos não têm filtros globais interativos e não executam ações.

## 3. Checagem de versão em 5 minutos

1. Anote a versão exata do Jira (Administração > Informações do sistema, ou rodapé).
2. Abra um dashboard e clique em Adicionar gadget. Confira se existem os gadgets citados no capítulo 02. Anote os que faltarem ou tiverem outro nome.
3. Abra uma tarefa e confira se o menu Automação aparece. Se aparecer, abra as regras e veja se há gatilho manual disponível.
4. Abra Administração > Gerenciar apps e anote os apps instalados e suas versões.
5. Confira em Administração > Tarefas > Controle de tempo se o Time Tracking está ativo.

Guarde as anotações: elas decidem quais módulos opcionais fazem sentido para você.

## 4. Como ler as marcas

Duas marcas aparecem ao longo do guia. Nenhum gadget, app ou recurso é afirmado como existente sem uma delas quando depende de versão ou instalação.

- **[Confirmar na sua versão]**: o recurso pode variar conforme a versão do Jira Data Center, a configuração ou o que está habilitado na sua instância. Antes de depender dele, confira na sua instância (use a checagem da seção 3) e adapte o passo se o nome ou o comportamento forem diferentes.
- **[Exige app: X]**: o recurso só existe com o app X instalado e licenciado. A licença é paga no Marketplace; consultar o preço atual para o seu número de usuários. Sem o app, use a alternativa nativa que o capítulo indicar, ou pule o módulo.

## 5. O que levar para a validação final

Separe, desde já, uma amostra de 10 tarefas reais, sendo 5 tarefas e 5 subtarefas. A amostra deve incluir:

- uma tarefa ou subtarefa sem vencimento;
- uma tarefa ou subtarefa atrasada (vencimento anterior a hoje e ainda não concluída);
- uma tarefa ou subtarefa com horas registradas.

Anote as chaves, os vencimentos, as estimativas e as horas registradas de cada uma. No capítulo 06 você compara esses valores com o que o dashboard mostra.
