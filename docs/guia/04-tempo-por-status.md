# Capítulo 04: tempo por status

Este capítulo trata do único indicador do dashboard que o Jira não entrega sozinho: quanto tempo cada tarefa passa em cada status. Ele compara seis alternativas, traz uma árvore de decisão para escolher uma e descreve a configuração de cada módulo. O capítulo não cria filtros novos. Onde precisar delimitar o escopo, ele usa o filtro `EQP | Base | Equipe`, criado no capítulo 01.

A matriz do capítulo 00 já classifica "Tempo por status" como não nativo. O capítulo 06 cita a árvore e as configurações daqui para decidir o que entra no layout.

## 1. Por que não é nativo

O Jira registra cada transição de status no histórico da tarefa (quem mudou, de qual status para qual e quando). Mas ele não transforma esse histórico em um relatório de tempo em cada status. No núcleo do Jira, sem apps, não existe gadget que mostre "esta tarefa ficou 3 dias em Em andamento e 2 dias em Em revisão", nem média ou total por status para uma fila de tarefas.

Quem quer esse indicador precisa de uma destas saídas: um app que calcule a partir do histórico, um script (inclusive via REST), uma ferramenta de relatórios ou campos que acumulem o tempo a cada transição. O gadget Average Time in Status do Jira Charting acrescenta uma média por status, mas é de app (ver seção 2). A seção 2 compara as alternativas.

## 2. Comparação das alternativas

| Alternativa | Como funciona | Prós | Contras | Custo | Marca |
|---|---|---|---|---|---|
| App de Time in Status | Calcula o tempo em cada status a partir do histórico de transições, inclusive de tarefas antigas (retroativo). | Menor esforço de implantação. Entrega média e total por status. | Exige licença. Os relatórios e dashboards são do próprio app, com visual diferente do restante do dashboard. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: Time in Status]** |
| eazyBI | Mede o tempo em cada status como medida do relatório e permite média e total. | Também entrega cards numéricos, horas por pessoa e filtros de página, que atendem aos capítulos 02 e 03. | A importação de dados é feita em lotes e tem defasagem. Há curva de aprendizado. | Licença paga no Marketplace, por faixa de usuários; consultar o preço atual para o seu número de usuários. | **[Exige app: eazyBI]** |
| ScriptRunner | Um campo com script calcula o tempo a partir do histórico da tarefa. | Totalmente sob medida: você define exatamente a regra de cálculo. | Exige manter código. Pode pesar no desempenho em instâncias grandes, porque o cálculo percorre o histórico. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: ScriptRunner]** |
| Campos personalizados alimentados por Automation | A cada transição, uma regra grava a data e hora de entrada no novo status e acumula o tempo no campo do status que acabou de terminar. | Não exige app extra além do próprio Automation (ver aviso abaixo). | Não há histórico retroativo: só vale a partir da implantação. Exige manter as regras e os campos. | Sem custo de app adicional se o Automation já estiver disponível. Se não estiver, licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Confirmar na sua versão]** |
| REST + script | O script `scripts/tempo_em_status.py` lê o changelog das tarefas pelo REST e calcula o tempo em cada status localmente (retroativo). Detalhes no capítulo 07. | Retroativo e sem app. Não exige licença. | Exige quem execute e mantenha o script. O resultado fica fora do dashboard (arquivo ou planilha) e precisa ser atualizado por você. | Sem custo de licença. | **[Confirmar na sua versão]** |
| Gadget Average Time in Status (Jira Charting) | Gadget do app Jira Charting que mostra a média de tempo por status. | Gadget pronto para dashboard. | Exige o campo personalizado "Time in Status" e reindexação completa (a reindexação em segundo plano não vale). Entrega média, não total por tarefa. Um KB antigo da Atlassian diz que nunca foi compatível com o Data Center. | Licença paga no Marketplace; consultar o preço atual para o seu número de usuários. | **[Exige app: Jira Charting]** **[Confirmar na sua versão]** |

Compatibilidade declarada no Marketplace (declarado pelo fornecedor, sem teste independente; confirme na sua versão):

- Time in Status (Timepiece, app 1211756): Data Center 8.0.0 a 11.3.12.
- Status Time Reports 2.3.x (app 1221826): Data Center 8.0.0 a 10.7.4.
- Cycle Time Gadgets (app 1235431): Data Center 8.20.0 a 11.3.12, com poucas instalações.
- Time in Status Reports for Jira (app 1226187): a versão mais nova só declara Data Center 11.x.
- Jira Charting (app 288): v4.0.1 para Data Center 9.6.0 a 9.17.5 e v5.0.0 para Data Center 10.0.0 a 10.7.4.

Horizonte do Data Center, segundo a Atlassian: 30/03/2026 foi o fim das vendas a novos clientes; 30/03/2028 é a data em que clientes existentes deixam de comprar produtos, apps e expansões; 28/03/2029 é o fim de vida, com assinaturas e apps somente leitura. Planeje a saída do Data Center ao escolher a alternativa: o que depende de app ou de regras de Automation precisará ser refeito ou migrado. A eazyBI declara compromisso com o Data Center em post do fornecedor, mas a Atlassian informa que a venda a novos clientes Data Center terminou em 30/03/2026; confirme com o fornecedor a disponibilidade para o seu caso.

Aviso sobre o Automation: o Automation é embutido no Jira Software Data Center desde a versão 9.0, então em 9.12 e 10.x não é preciso instalar app à parte. **[Confirmar na sua versão]** Use a checagem do capítulo 00 (menu Automação numa tarefa) para confirmar que o recurso aparece na sua instância. Limites documentados para o Data Center 11.2 (**[Confirmar na sua versão]**): a busca JQL de uma regra trata no máximo 1000 issues, e cada regra tem limite de 5000 execuções por hora e de 3600 segundos de processamento por dia.

## 3. Árvore de decisão

Percorra as perguntas na ordem e pare na primeira que se aplicar.

1. Você tem um app de Time in Status instalado? Use-o e siga a seção 4.
2. Se não, você tem o eazyBI instalado? Use-o e siga a seção 5.
3. Se não, você consegue instalar um dos dois? Recomende o app de Time in Status: é o de menor esforço e entrega média e total por status. Depois de instalado, siga a seção 4.
4. Se não, você precisa do histórico de tarefas antigas e tem quem execute um script? Use REST + script (`scripts/tempo_em_status.py`, capítulo 07): é retroativo e não exige app.
5. Se o requisito for só a média por status num gadget e você aceitar o campo "Time in Status" e a reindexação completa, o gadget Average Time in Status do Jira Charting é uma opção (exige app). **[Confirmar na sua versão]**
6. Se nenhuma das anteriores servir, use campos personalizados alimentados por Automation e siga a seção 6. Aceite as duas limitações: manutenção maior e nenhum histórico retroativo.

No cenário sem app, o REST vem antes do Automation justamente por ser retroativo.

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

Esta alternativa não usa app além do Automation (embutido no Jira Software Data Center desde a 9.0) e não tem histórico: ela só mede o que acontecer depois de ativada. Tarefas que já estavam em andamento começam a contar a partir da primeira transição seguinte.

### 6.1. Criar os campos

Em Administração > Tarefas > Campos personalizados, crie: **[Confirmar na sua versão]**

1. Um campo de data/hora chamado "Entrada no status atual".
2. Um campo numérico para cada status relevante, chamado "Horas em <status>" (por exemplo, "Horas em Em andamento" e "Horas em Em revisão"). Escolha só os status que importam para a análise; cada campo extra é mais uma regra para manter.

Associe os campos às telas dos projetos da equipe e confira se estão no esquema de campos, senão a regra não consegue gravar. Veja também a lista de campos do capítulo 00.

### 6.2. Criar as regras

Em Automação (menu da tarefa ou administração do projeto, conforme a versão), crie duas regras delimitadas aos projetos `[CHAVE_DO_PROJETO]`. **[Confirmar na sua versão]**

**Regra A: "Issue criada" (inicialização).** Sem ela, o tempo no primeiro status se perde, porque a regra de transição só dispara na primeira mudança de status e "Entrada no status atual" ficaria vazio em tarefas novas. Issues criadas antes da implantação também não terão histórico.

1. **Gatilho:** criação de issue (em inglês, "Issue created"). **[Confirmar na sua versão]**
2. **Ação:** editar a issue gravando `{{now}}` em "Entrada no status atual" e `0` em cada campo "Horas em <status>". A inicialização com 0 evita o problema do campo numérico vazio descrito na regra B. **[Confirmar na sua versão]**

**Regra B: mudança do campo Status (acumulação).**

1. **Gatilho:** valor do campo alterado (em inglês, "Field value changed"), no campo Status. **[Confirmar na sua versão]** Assim a regra vale para qualquer transição e expõe `{{fieldChange}}`.
2. **Smart values do gatilho:** use o status de origem `{{fieldChange.fromString}}` e o de destino `{{fieldChange.toString}}`. Os smart values `{{fromStatus}}` e `{{toStatus}}` não foram encontrados na documentação do Data Center; não dependa deles. Limitação: `{{fieldChange}}` só traz o primeiro valor alterado. **[Confirmar na sua versão]** Use `{{fieldChange.fromString}}` nas condições abaixo, porque é o status que acabou de terminar e cujo campo recebe o tempo.
3. **Acumular o tempo do status que saiu:** para cada status monitorado, crie uma condição "`{{fieldChange.fromString}}` é igual a <status>" e, dentro dela, uma ação "Editar issue" no campo "Horas em <status>". O novo valor é o valor atual do campo somado ao tempo decorrido desde "Entrada no status atual". Esse tempo vem de uma conta de datas com smart values entre `{{now}}` e o valor do campo "Entrada no status atual", convertida para horas. A documentação do Data Center traz `.diff(...)` com `.hours` e `.minutes` (por exemplo, `{{issue.<campo>.diff(now).hours}}`) e `{{#=}}` para a soma. **[Confirmar na sua versão]** O formato do resultado varia entre versões; monte a expressão, teste-a (6.3) e ajuste.
4. **Campo vazio conta como 0:** um campo numérico vazio quebra a soma. Trate vazio como 0, seja inicializando os campos na regra A, seja usando na expressão um valor padrão ou condicional para o caso vazio. A sintaxe para isso varia entre versões. **[Confirmar na sua versão]** Tarefas anteriores à implantação têm os campos vazios, então esse tratamento também é necessário na regra B.
5. **Tarefa sem entrada anterior:** se "Entrada no status atual" estiver vazio, a conta do passo 3 falha ou dá um valor sem sentido. Acrescente uma condição "o campo não está vazio" antes de acumular, e grave `{{now}}` de qualquer forma no passo seguinte.
6. **Gravar a nova entrada:** depois de acumular, edite "Entrada no status atual" com `{{now}}`.

A ordem importa: leia a entrada antiga antes de sobrescrevê-la. Acumule primeiro e só então grave `{{now}}`. Se inverter, o tempo calculado será zero.

Nota sobre valores obsoletos: em algumas versões, os smart values podem refletir a issue como ela estava no início da regra, e não o resultado das ações anteriores. **[Confirmar na sua versão]** Isso ajuda a ler a entrada antiga com segurança, mas pode fazer o valor atual de "Horas em <status>" ficar desatualizado entre uma ação e outra. A ação "Re-fetch issue data" (Automation 7.0.0+, Data Center 8.0.0+) recarrega a issue entre as ações; **[Confirmar na sua versão]**. Confirme no teste da seção 6.3 se ela é necessária ou, se preferir, calcule e grave tudo numa única ação de edição.

### 6.3. Testar antes de ativar para todos

1. Ative a regra em um projeto de teste ou numa tarefa de teste.
2. Crie uma tarefa nova (para exercitar a regra A) e mova-a por dois ou três status, esperando alguns minutos entre as transições.
3. Confira os campos "Horas em <status>" contra o tempo real entre as transições. A aritmética de datas em smart values deve ser testada na sua versão. **[Confirmar na sua versão]** Confirme também se o valor lido entre as ações está atualizado e se `{{fieldChange.fromString}}` traz o status de origem esperado (nota de valores obsoletos em 6.2). Cuidado com arredondamento: tempos curtos podem resultar em zero se o resultado for convertido para horas inteiras.
4. Só então ative para os projetos da equipe.

### 6.4. Exibir no dashboard

Os campos numéricos aparecem em listas e podem ser usados em filtros e gadgets de lista, de acordo com as opções da sua versão. **[Confirmar na sua versão]** Média e total por status não saem prontos nos gadgets nativos. Se precisar deles, é o ponto em que esta alternativa chega ao limite, e o eazyBI (seção 5) passa a valer o custo.

## 7. Limites e manutenção

**App de Time in Status.** O cálculo é do app, e a qualidade depende da versão instalada e de como ele trata fusos, horário comercial e status que se repetem (tarefa que volta para o mesmo status). Confirme essas regras na documentação. O dashboard do app é separado dos gadgets nativos, então o visual e os filtros não são os mesmos do restante da página. A licença precisa ser renovada e acompanhar o número de usuários; consultar o preço atual para o seu número de usuários. **[Confirmar na documentação do app instalado]**

**eazyBI.** O compromisso com o Data Center é declarado em post do fornecedor; a Atlassian informa que a venda a novos clientes Data Center terminou em 30/03/2026. Os dados refletem a última importação, e a importação em lotes consome recursos do servidor, por isso o agendamento fora do horário de pico. Mudanças no fluxo (novos status, status renomeados) exigem reimportar e, às vezes, ajustar o relatório. Há curva de aprendizado para montar medidas e filtros de página; reserve tempo para quem vai manter os relatórios.

**REST + script.** O script do capítulo 07 depende do REST da sua versão, que deve ser confirmado antes de usar; o resultado fica fora do dashboard e precisa de quem o execute de tempos em tempos. **[Confirmar na sua versão]**

**Jira Charting (Average Time in Status).** Depende do app, do campo "Time in Status" e de reindexação completa; a compatibilidade é declarada pelo fornecedor, sem teste independente, e há KB antigo em sentido contrário. **[Confirmar na sua versão]**

**ScriptRunner.** O script é código seu: alguém precisa mantê-lo, revisá-lo a cada atualização do Jira ou do app e corrigi-lo quando o fluxo mudar. Em instâncias grandes, um campo com script que percorre o histórico de cada tarefa pode pesar em listas e pesquisas. Meça o desempenho antes de liberar. **[Confirmar na documentação do app instalado]**

**Campos com Automation.** Não existe histórico retroativo: o indicador só vale a partir da data de ativação, e tarefas anteriores têm campos vazios ou parciais. Cada status novo no fluxo exige um campo e uma condição novos na regra. Respeite também os limites de execução do Automation (seção 2). Se a regra falhar ou for desativada por engano, o acumulado fica errado sem aviso, e corrigir exige editar os valores. Por isso, registre a data de implantação no dashboard, acompanhe o histórico de execução da regra **[Confirmar na sua versão]** e refaça o teste da seção 6.3 depois de qualquer mudança no fluxo de trabalho.
