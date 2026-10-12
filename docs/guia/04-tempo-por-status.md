# Capítulo 04: tempo por status

Este capítulo trata do único indicador do dashboard que o Jira não entrega sozinho: quanto tempo cada tarefa passa em cada status. Ele compara as quatro alternativas, traz uma árvore de decisão para escolher uma e descreve a configuração de cada módulo. O capítulo não cria filtros novos. Onde precisar delimitar o escopo, ele usa o filtro `EQP | Base | Equipe`, criado no capítulo 01.

A matriz do capítulo 00 já classifica "Tempo por status" como não nativo. O capítulo 06 cita a árvore e as configurações daqui para decidir o que entra no layout.

## 1. Por que não é nativo

O Jira registra cada transição de status no histórico da tarefa (quem mudou, de qual status para qual e quando). Mas ele não transforma esse histórico em um relatório de tempo em cada status. Não existe gadget nativo que mostre "esta tarefa ficou 3 dias em Em andamento e 2 dias em Em revisão", nem média ou total por status para uma fila de tarefas.

Quem quer esse indicador precisa de uma destas saídas: um app que calcule a partir do histórico, um script, uma ferramenta de relatórios ou campos que acumulem o tempo a cada transição. A seção 2 compara as quatro.

## 2. Comparação das alternativas

| Alternativa | Como funciona | Prós | Contras | Custo | Marca |
|---|---|---|---|---|---|
| App de Time in Status | Calcula o tempo em cada status a partir do histórico de transições, inclusive de tarefas antigas (retroativo). | Menor esforço de implantação. Entrega média e total por status. | Exige licença. Os relatórios e dashboards são do próprio app, com visual diferente do restante do dashboard. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: Time in Status]** |
| eazyBI | Mede o tempo em cada status como medida do relatório e permite média e total. | Também entrega cards numéricos, horas por pessoa e filtros de página, que atendem aos capítulos 02 e 03. | A importação de dados é feita em lotes e tem defasagem. Há curva de aprendizado. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: eazyBI]** |
| ScriptRunner | Um campo com script calcula o tempo a partir do histórico da tarefa. | Totalmente sob medida: você define exatamente a regra de cálculo. | Exige manter código. Pode pesar no desempenho em instâncias grandes, porque o cálculo percorre o histórico. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: ScriptRunner]** |
| Campos personalizados alimentados por Automation | A cada transição, uma regra grava a data e hora de entrada no novo status e acumula o tempo no campo do status que acabou de terminar. | Não exige app extra além do próprio Automation (ver aviso abaixo). | Não há histórico retroativo: só vale a partir da implantação. Exige manter as regras e os campos. | Sem custo de app adicional se o Automation já estiver disponível. Se não estiver, licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Confirmar na sua versão]** |

Aviso sobre o Automation: dependendo da versão do Jira Data Center, o Automation pode ser um recurso incluído ou um app instalado à parte. **[Confirmar na sua versão]** Use a checagem do capítulo 00 (menu Automação numa tarefa e lista em Administração > Gerenciar apps) para saber qual é o seu caso. Se for um app, vale a mesma regra de custo das outras linhas.

## 3. Árvore de decisão

Percorra as perguntas na ordem e pare na primeira que se aplicar.

1. Você tem um app de Time in Status instalado? Use-o e siga a seção 4.
2. Se não, você tem o eazyBI instalado? Use-o e siga a seção 5.
3. Se não, você consegue instalar um dos dois? Recomende o app de Time in Status: é o de menor esforço e entrega média e total por status. Depois de instalado, siga a seção 4.
4. Se não, use campos personalizados alimentados por Automation e siga a seção 6. Aceite as duas limitações: manutenção maior e nenhum histórico retroativo.

O ScriptRunner não aparece como primeira escolha. Ele só entra se você já o tem, tem quem mantenha o código e precisa de uma regra de cálculo que os outros não oferecem. Nesse caso, siga a documentação do app instalado. **[Confirmar na documentação do app instalado]**

## 4. Configuração: app de Time in Status

Os passos abaixo são genéricos de propósito. Nomes de telas, menus e opções variam entre apps e versões. **[Confirmar na documentação do app instalado]**

1. Instale o app em Administração > Gerenciar apps e confirme que a licença está ativa. **[Exige app: Time in Status]**
2. Defina o escopo do cálculo. Use o filtro salvo `EQP | Base | Equipe` como fonte de tarefas, para que o relatório cubra só a equipe. **[Confirmar na documentação do app instalado]** (alguns apps aceitam filtro salvo, outros pedem JQL colado ou projeto).
3. Escolha a medida: tempo em cada status, com média e total por status. Se o app oferecer, escolha também o intervalo de datas do relatório. **[Confirmar na documentação do app instalado]**
4. Se o app oferecer um gadget para dashboard, adicione-o ao dashboard e aponte para o mesmo escopo. **[Confirmar na documentação do app instalado]** Se não oferecer, deixe o relatório numa página do próprio app e coloque no dashboard um gadget de texto com o link para ele.
5. Valide com a amostra de 10 tarefas do capítulo 00: escolha uma tarefa cujo histórico você conheça e compare o tempo mostrado com o que você vê na aba Histórico da tarefa.

## 5. Configuração: eazyBI

Os nomes exatos de telas e medidas dependem da versão do app. **[Confirmar na documentação do app instalado]**

1. Instale o app e crie a conta de dados do eazyBI, apontando para esta instância do Jira. **[Exige app: eazyBI]**
2. Importe os projetos da equipe. Na importação, selecione os projetos pelas chaves `[CHAVE_DO_PROJETO]` do capítulo 00.
3. Importe também o campo de vencimento e as horas (estimado e registrado) como dimensões ou medidas, para reaproveitar a mesma conta de dados nos cards do capítulo 03. **[Confirmar na documentação do app instalado]**
4. Crie um relatório com Status nas linhas e as medidas de tempo em status nas colunas (por exemplo, média e total de tempo em cada status). **[Confirmar na documentação do app instalado]**
5. Adicione filtros de página para período, equipe e tipo de tarefa. Assim quem vê o dashboard recorta o relatório sem editar nada.
6. Inclua o relatório no dashboard pelo gadget do eazyBI. **[Confirmar na documentação do app instalado]**
7. Agende a importação fora do horário de pico. O eazyBI importa em lotes, então o relatório mostra o estado da última importação, não o do momento. Avise os usuários da defasagem, por exemplo com um texto no próprio dashboard.

## 6. Configuração: campos personalizados com Automation

Esta alternativa não usa app além do Automation e não tem histórico: ela só mede o que acontecer depois de ativada. Tarefas que já estavam em andamento começam a contar a partir da primeira transição seguinte.

### 6.1. Criar os campos

Em Administração > Tarefas > Campos personalizados, crie: **[Confirmar na sua versão]**

1. Um campo de data/hora chamado "Entrada no status atual".
2. Um campo numérico para cada status relevante, chamado "Horas em <status>" (por exemplo, "Horas em Em andamento" e "Horas em Em revisão"). Escolha só os status que importam para a análise; cada campo extra é mais uma regra para manter.

Associe os campos às telas dos projetos da equipe e confira se estão no esquema de campos, senão a regra não consegue gravar. Veja também a lista de campos do capítulo 00.

### 6.2. Criar a regra

Em Automação (menu da tarefa ou administração do projeto, conforme a versão), crie uma regra delimitada aos projetos `[CHAVE_DO_PROJETO]`. **[Confirmar na sua versão]**

1. **Gatilho:** transição de issue (nas versões em inglês, "Issue transitioned"). **[Confirmar na sua versão]** Deixe os campos "de status" e "para status" vazios, para a regra valer para qualquer transição.
2. **Ação, acumular o tempo do status que saiu:** para cada status monitorado, crie uma condição "o status de origem é <status>" e, dentro dela, uma ação "Editar issue" no campo "Horas em <status>". O novo valor é o valor atual do campo somado ao tempo decorrido desde "Entrada no status atual". Esse tempo vem de uma conta de datas com smart values entre `{{now}}` e o valor do campo "Entrada no status atual", convertida para horas. **[Confirmar na sua versão]** Os nomes das funções de data e o formato do resultado variam entre versões; monte a expressão, teste-a (6.3) e ajuste.
3. **Ação, gravar a nova entrada:** depois do passo anterior, edite o campo "Entrada no status atual" com o valor `{{now}}`.
4. **Primeira transição de uma tarefa antiga:** se "Entrada no status atual" estiver vazio, a conta do passo 2 falha ou dá um valor sem sentido. Acrescente uma condição "o campo não está vazio" antes de acumular, e grave `{{now}}` de qualquer forma.

A ordem importa: acumule primeiro (usando a entrada anterior) e só então sobrescreva a entrada com `{{now}}`. Se inverter, o tempo calculado será zero.

### 6.3. Testar antes de ativar para todos

1. Ative a regra em um projeto de teste ou numa tarefa de teste.
2. Mova a tarefa por dois ou três status, esperando alguns minutos entre as transições.
3. Confira os campos "Horas em <status>" contra o tempo real entre as transições. A aritmética de datas em smart values deve ser testada na sua versão. **[Confirmar na sua versão]** Cuidado com arredondamento: tempos curtos podem resultar em zero se o resultado for convertido para horas inteiras.
4. Só então ative para os projetos da equipe.

### 6.4. Exibir no dashboard

Os campos numéricos aparecem em listas e podem ser usados em filtros e gadgets de lista, de acordo com as opções da sua versão. **[Confirmar na sua versão]** Média e total por status não saem prontos nos gadgets nativos. Se precisar deles, é o ponto em que esta alternativa chega ao limite, e o eazyBI (seção 5) passa a valer o custo.

## 7. Limites e manutenção

**App de Time in Status.** O cálculo é do app, e a qualidade depende da versão instalada e de como ele trata fusos, horário comercial e status que se repetem (tarefa que volta para o mesmo status). Confirme essas regras na documentação. O dashboard do app é separado dos gadgets nativos, então o visual e os filtros não são os mesmos do restante da página. A licença precisa ser renovada e acompanhar o número de usuários; consultar o preço atual para o seu número de usuários. **[Confirmar na documentação do app instalado]**

**eazyBI.** Os dados refletem a última importação, e a importação em lotes consome recursos do servidor, por isso o agendamento fora do horário de pico. Mudanças no fluxo (novos status, status renomeados) exigem reimportar e, às vezes, ajustar o relatório. Há curva de aprendizado para montar medidas e filtros de página; reserve tempo para quem vai manter os relatórios.

**ScriptRunner.** O script é código seu: alguém precisa mantê-lo, revisá-lo a cada atualização do Jira ou do app e corrigi-lo quando o fluxo mudar. Em instâncias grandes, um campo com script que percorre o histórico de cada tarefa pode pesar em listas e pesquisas. Meça o desempenho antes de liberar. **[Confirmar na documentação do app instalado]**

**Campos com Automation.** Não existe histórico retroativo: o indicador só vale a partir da data de ativação, e tarefas anteriores têm campos vazios ou parciais. Cada status novo no fluxo exige um campo e uma condição novos na regra. Se a regra falhar ou for desativada por engano, o acumulado fica errado sem aviso, e corrigir exige editar os valores. Por isso, registre a data de implantação no dashboard, acompanhe o histórico de execução da regra **[Confirmar na sua versão]** e refaça o teste da seção 6.3 depois de qualquer mudança no fluxo de trabalho.
