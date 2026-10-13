# Capítulo 06: layout, permissões e validação

Este capítulo junta o que foi montado nos capítulos 01 a 05: define a ordem dos gadgets no dashboard, as permissões para que todos enxerguem os números, o checklist para validar os totais, as regras de desempenho e manutenção e as melhorias futuras. Nenhum filtro novo é criado aqui; todos são citados pelo nome exato do catálogo do capítulo 01.

Ao final, você terá o dashboard montado em 3 colunas, compartilhado com o grupo certo, com os números conferidos à mão e com um plano de manutenção.

Pré-requisito: capítulos 00 a 05 concluídos, a amostra de 10 tarefas do capítulo 00 separada e o dashboard criado.

## 1. Layout em 3 colunas

O dashboard usa o layout de 3 colunas. Os nomes e a disponibilidade dos gadgets dependem da versão e das apps instaladas **[Confirmar na sua versão]**. A tabela segue a ordem de cima para baixo, da faixa 1 à faixa 5. As faixas 1 a 4 seguem a ordem do roteiro do projeto, e a faixa 5 isola a lista de divergências de vencimento, citada no capítulo 05. Preencha as três colunas da esquerda para a direita em cada faixa.

| Posição | Gadget **[Confirmar na sua versão]** | Filtro | Capítulo |
|---|---|---|---|
| 1. Totais, coluna 1 | Total de tarefas (Resultados do Filtro ou Estatísticas da Tarefa) | `EQP \| Resumo \| Tarefas` | 02 |
| 1. Totais, coluna 2 | Total de subtarefas (Resultados do Filtro ou Estatísticas da Tarefa) | `EQP \| Resumo \| Subtarefas` | 02 |
| 1. Totais, coluna 3 | Atrasadas (Resultados do Filtro) | `EQP \| Resumo \| Atrasadas` | 02 |
| 1. Totais, coluna 1 | Sem responsável (Resultados do Filtro) | `EQP \| Resumo \| Sem responsável` | 02 |
| 1. Totais, coluna 2 | Card de horas: tempo em tarefas (coluna Time Spent) | `EQP \| Horas \| Tarefas com horas` | 03 |
| 1. Totais, coluna 3 | Card de horas: tempo em subtarefas (coluna Time Spent) | `EQP \| Horas \| Subtarefas com horas` | 03 |
| 2. Equipe, coluna 1 | Pizza de status (Gráfico de Pizza, estatística Status) | `EQP \| Base \| Equipe` | 02 |
| 2. Equipe, coluna 2 | Pizza de tipo (Gráfico de Pizza, estatística Tipo de tarefa) | `EQP \| Base \| Equipe` | 02 |
| 2. Equipe, coluna 3 | Responsável × status (Estatísticas de Filtro Bidimensional) | `EQP \| Base \| Equipe` | 02 |
| 2. Equipe, coluna 1 | Criadas vs Resolvidas (período de 30 dias) | `EQP \| Base \| Equipe` | 02 |
| 3. Negligenciados, coluna 1 | Sem atualização (Resultados do Filtro) | `EQP \| Negligenciados \| Sem atualização` | 03 |
| 3. Negligenciados, coluna 2 | Sem estimativa (Resultados do Filtro) | `EQP \| Negligenciados \| Sem estimativa` | 03 |
| 3. Negligenciados, coluna 3 | Sem vencimento (Resultados do Filtro) | `EQP \| Negligenciados \| Sem vencimento` | 03 |
| 3. Negligenciados, coluna 1 | Sem responsável (Resultados do Filtro) | `EQP \| Negligenciados \| Sem responsável` | 03 |
| 4. Datas e horas, coluna 2 | Timesheet **[Confirmar na sua versão]** | `EQP \| Horas \| Com worklog no mês` | 03 |
| 4. Datas e horas, coluna 3 | Módulo de tempo por status, se instalado. **[Exige app: Time in Status]** ou **[Exige app: eazyBI]**, conforme a escolha do capítulo 04; com campos de Automation, uma lista com as colunas dos campos | Depende da alternativa escolhida no capítulo 04 | 04 |
| 5. Divergência, coluna 1 | Subtarefas com vencimento divergente (Resultados do Filtro) | `EQP \| Datas \| Subtarefas com vencimento divergente` | 05 |

Observações sobre a tabela:

- O módulo de tempo por status é opcional. Se o app tiver painel próprio, ele fica fora do dashboard nativo ou entra como gadget, conforme o app. **[Confirmar na documentação do app instalado]**
- A faixa 5 pode ficar na coluna que estiver mais vazia; o que importa é a ordem de leitura.
- Os dois "Sem responsável" usam filtros diferentes (`EQP | Resumo | Sem responsável` e `EQP | Negligenciados | Sem responsável`). Se os dois mostrarem o mesmo número na sua instância, mantenha só um.

### Quantos gadgets usar

Recomendação: no máximo 12 a 15 gadgets no dashboard. Cada gadget executa uma consulta JQL ao abrir a página, e cada pessoa que abre o dashboard repete todas elas. Com muitos gadgets, o dashboard demora a carregar e pesa no servidor, principalmente em instância grande.

A tabela acima tem 16 gadgets sem o módulo opcional, ou seja, passa do recomendado. Para voltar à faixa de 12 a 15, corte primeiro os repetidos: o segundo "Sem responsável" (mantendo só um dos dois), e depois o total de subtarefas, se o seu grupo não o usa. Se instalar o módulo de tempo por status, corte mais um gadget para compensar.

## 2. Onde fica o botão

O botão de sincronização de vencimento fica na própria tarefa, não no dashboard. O dashboard mostra apenas a lista de subtarefas com vencimento divergente (faixa 5 da tabela). Quem quer corrigir abre a tarefa a partir da lista e aciona o botão ali. A regra, as opções de implementação e as permissões estão no capítulo 05.

## 3. Compartilhamento e permissões

Filtros e dashboard têm compartilhamento independente. Ter acesso ao dashboard não dá acesso aos filtros usados pelos gadgets.

- **Compartilhe com o mesmo grupo.** O dashboard e todos os filtros do catálogo devem ser compartilhados com o mesmo grupo (por exemplo, `grupo-da-equipe`). As opções de compartilhamento (grupo, projeto, função, todos os usuários logados) variam por versão e configuração. **[Confirmar na sua versão]**
- **O que o leitor vê sem acesso ao filtro.** Quem vê o dashboard mas não tem acesso a um filtro vê o gadget vazio ou com mensagem de erro. Se um colega relatar um gadget vazio, a primeira checagem é o compartilhamento do filtro.
- **Dono da conta.** O dono do dashboard e dos filtros deve ser uma conta de serviço ou de administrador, e não uma pessoa da equipe. Assim, uma saída ou uma desativação de usuário não derruba os gadgets.
- **Se o dono sair.** O administrador transfere a propriedade do dashboard e dos filtros para a conta de serviço. Os menus e os passos da transferência variam conforme a versão. **[Confirmar na sua versão]** Depois da transferência, confira o compartilhamento e abra o dashboard com um usuário comum.
- **Nome duplicado.** Filtros com o mesmo nome e donos diferentes causam ambiguidade nas referências por nome. Mantenha um único dono e, se precisar, referencie o filtro por ID (`filter = 12345`).
- **Conta da automação.** A conta que executa a regra do botão e a regra agendada precisa das permissões descritas na seção 7 do capítulo 05 (Procurar Projetos, Editar Tarefas, Agendar Tarefas e Adicionar Comentários no projeto das subtarefas).

## 4. Checklist de validação dos números

Use a amostra de 10 tarefas do capítulo 00 e um usuário comum do grupo. Marque cada item só depois de conferir.

- [ ] Cada contagem de gadget bate com o Navegador de Tarefas para o mesmo filtro (modo Avançado, `filter = "<nome do filtro>"`, conforme a validação do capítulo 02).
- [ ] Os tipos somam o total e os status somam o total (pizzas de tipo e de status contra `EQP | Base | Equipe`).
- [ ] A amostra de 10 tarefas, separada no capítulo 00, foi conferida à mão: horas registradas, estimativa e Σ Time Spent do pai, comparados com o que o dashboard e as listas mostram.
- [ ] O card combinado não conta em duplicidade: o exemplo numérico do capítulo 03 (pai com 2h, subtarefas de 3h e 1h) dá 6h no conjunto, e não 10h nem 12h.
- [ ] Fronteira de data: uma tarefa com vencimento hoje não conta como atrasada (a comparação usa o início do dia). Confira o fuso do usuário em relação ao do servidor nos filtros com `worklogDate`, usando um registro de horas feito perto da meia-noite. **[Confirmar na sua versão]**
- [ ] Um usuário do grupo, sem acesso especial, abre o dashboard e vê o resultado esperado em todos os gadgets (sem gadget vazio ou com erro).
- [ ] O botão foi testado em homologação, conforme o roteiro de 7 casos do capítulo 05, seção 9.
- [ ] O filtro `EQP | Negligenciados | Parada no mesmo status` e o uso de `membersOf` com `worklogAuthor` (`EQP | Horas | Com worklog no mês`) foram testados, e o resultado foi anotado (aceitou ou rejeitou a consulta; o que retornou numa amostra conhecida).

Se um item falhar, siga a ordem de diagnóstico do capítulo 02, seção 4: filtro correto no gadget, compartilhamento, cache e permissões de quem criou contra quem vê. Antes de qualquer teste com `membersOf`, confira que o grupo existe e tem membros; se não, use a alternativa com lista de usernames do capítulo 01.

## 5. Desempenho em instâncias grandes

- Evite encadeamento profundo de filtros (filtro que chama filtro que chama filtro). Cada nível aumenta o custo da consulta.
- Evite `status CHANGED` e funções de app pesadas em filtros usados por muitos gadgets. Use-os só onde precisar, como em `EQP | Negligenciados | Parada no mesmo status`.
- Agende a regra de divergência (capítulo 05) e a importação do eazyBI (capítulo 04) fora do horário de pico.
- Delimite as consultas por projeto, com a chave em `[CHAVE_DO_PROJETO]`, em vez de pesquisar a instância inteira.
- Nos gadgets Resultados do Filtro, limite colunas e linhas (por exemplo, 10 linhas e só as colunas necessárias).
- Mantenha o número de gadgets na faixa recomendada da seção 1.

## 6. Manutenção

| Ponto | Quando muda | O que fazer |
|---|---|---|
| Membros do grupo `grupo-da-equipe` | Entrada, saída ou mudança de pessoas da equipe | Atualize o grupo (ou a lista de usernames, se for a alternativa) e confira o resultado de `EQP \| Base \| Equipe` |
| Dono dos filtros e do dashboard | O dono sai, muda de função ou a conta é desativada | O administrador transfere a propriedade para a conta de serviço e confere o compartilhamento. **[Confirmar na sua versão]** |
| Fluxo de trabalho | Status novos, renomeados ou removidos | Revise os filtros que dependem de categoria de status e as pizzas de status; no tempo por status, atualize os campos e as regras do capítulo 04 e refaça o teste |
| Conta da automação | Senha, desativação ou mudança de permissões da conta | Confirme as permissões da seção 7 do capítulo 05 e rode o roteiro de testes de novo |
| Prazo de 14 dias da inatividade | A equipe decide outro prazo | Troque o `-14d` nos filtros `EQP \| Negligenciados \| Sem atualização` e `EQP \| Negligenciados \| Parada no mesmo status`, e revalide |

## 7. Melhorias futuras

- **Alertas de tarefas atrasadas por e-mail via Automation.** Uma regra agendada sobre `EQP | Resumo | Atrasadas` que avise o responsável ou a equipe. Depende de o Automation estar disponível e de como ele envia e-mails na sua versão. **[Confirmar na sua versão]**
- **Assinatura de filtro por e-mail.** O Jira pode enviar por e-mail o resultado de um filtro. A assinatura envia a lista de tarefas do filtro, e **não** o dashboard inteiro. Disponibilidade e opções de agenda variam por versão. **[Confirmar na sua versão]**
- **Relatório periódico em PDF do dashboard.** Não é nativo. Só use um app cuja compatibilidade com Data Center esteja confirmada na listagem do Marketplace. **[Exige app: app de exportação com compatibilidade com Data Center confirmada no Marketplace]** Custo: licença paga no Marketplace; consultar o preço atual para o seu número de usuários.
