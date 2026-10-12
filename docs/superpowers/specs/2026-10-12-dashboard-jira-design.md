# Design: dashboard de monitoramento de tarefas no Jira Data Center

Data: 12/10/2026
Status: aguardando revisão do usuário

## 1. Objetivo e contexto

Entregar um guia de implantação (JQL, gadgets, regra de automação, layout, permissões e validação) para um dashboard de monitoramento de tarefas de uma equipe no Jira Data Center, com uso operacional diário e análise gerencial.

O dashboard cobre:

- resumo da equipe;
- indicadores negligenciados;
- divergência de vencimento entre tarefa pai e subtarefas;
- sincronização de vencimentos;
- cards de tempo dedicado sem contagem em duplicidade.

### Restrições

- Data Center: identificação por username ou user key, nunca accountId.
- Dashboards nativos não têm filtros globais interativos e não executam ações.
- Não inventar gadgets ou apps. Tudo que depender de versão ou de app deve ser sinalizado como "confirmar".
- Datas em dd/mm/aaaa nos textos e nomes. No JQL literal usa-se `aaaa-mm-dd`, e os exemplos trazem as duas formas.
- Textos em português do Brasil.

### Dados do ambiente

Os dados abaixo ainda **não foram informados** pelo usuário. O guia será entregue na modalidade "com placeholders e árvore de decisão":

- versão exata do Jira Data Center;
- apps instalados (ScriptRunner, eazyBI, Time in Status, JMWE, Automation) e permissão para instalar;
- chaves dos projetos e lista de usernames ou grupo da equipe;
- status do fluxo, tipos de tarefa e campos personalizados;
- volume aproximado de issues.

### Critérios de sucesso

- Os números do dashboard conferem com uma amostra real.
- O botão de sincronização foi testado em homologação antes de produção.
- Cada trecho que depende de versão ou app diz isso explicitamente.

## 2. Abordagem escolhida

**Núcleo nativo + módulos opcionais por app.**

- O núcleo funciona apenas com recursos nativos do Data Center.
- Cada lacuna vira um módulo independente (Time in Status, ScriptRunner, eazyBI), implantado conforme os apps disponíveis.
- Alternativas descartadas:
  - três perfis completos, por duplicação e custo de manutenção;
  - árvore de decisão única, por ser difícil de seguir na prática.

## 3. Estrutura do guia

1. Pré-requisitos e checagem de versão, com tabela "nativo × exige app".
2. Fundação de filtros.
3. Núcleo nativo: resumo da equipe, negligenciados, cards de horas.
4. Módulos opcionais: Time in Status, ScriptRunner, eazyBI.
5. Divergência de datas e botão de sincronização.
6. Layout, compartilhamento e permissões.
7. Checklist de validação, riscos, desempenho, manutenção e melhorias futuras.

## 4. Fundação de filtros

- **Filtro-base da equipe:** `assignee in membersOf("grupo-da-equipe")`. Sem grupo, usa lista de usernames. A mudança de membros é feita em um único ponto.
- **Filtros derivados** começam com `filter = "EQP | Base"`.
- **Padrão de nome:** `EQP | <seção> | <métrica>`. A descrição de cada filtro traz finalidade e critério de data (criação, vencimento, resolução, atualização ou worklog).
- **Recortes de período** (semana atual, mês atual, últimos 30 dias, trimestre) são filtros separados, com `startOfWeek()`, `startOfMonth()` e `-30d`. Período livre fica a cargo do Navegador de Tarefas ou do eazyBI.
- **Opções para segmentação interativa**, cada uma com prós, contras e custo:
  - filtros salvos separados por recorte;
  - JQL com funções de data;
  - eazyBI com filtros de página;
  - outras soluções de Marketplace, somente se confirmadas como compatíveis com Data Center.
- **Filtros sem responsável** usam uma base alternativa por projeto ou componente, pois o base filtra por assignee.
- Filtros e dashboard são compartilhados com o mesmo grupo.

## 5. Núcleo nativo: resumo da equipe

- **Estado por categoria de status:** `statusCategory` com `Done`, `"In Progress"` e `"To Do"`, independente da lista de status.
- **Tipos:** `issuetype in standardIssueTypes()` e `issuetype in subTaskIssueTypes()`.
- **Atrasadas:** `resolution = Unresolved AND duedate < startOfDay()`.
- **Sem responsável:** `assignee is EMPTY`.

| Métrica | Gadget nativo | Observação |
|---|---|---|
| Totais | Resultados do Filtro ou Estatísticas da Tarefa | Não existe card numérico nativo. |
| Distribuição por tipo, status ou responsável | Gráfico de Pizza | Mostra número e percentual. |
| Responsável × status | Estatísticas de Filtro Bidimensional | Números absolutos com totais, sem percentual. |
| Tendência | Criadas vs Resolvidas | |
| Concentração | Mapa de Calor | |
| Lista operacional | Resultados do Filtro | Colunas de vencimento e responsável. |

Limites: percentuais só no gráfico de pizza, e cards numéricos reais só com eazyBI. A existência e o nome de cada gadget na versão do usuário devem ser confirmados.

## 6. Indicadores negligenciados e horas

### Indicadores

| Indicador | Como |
|---|---|
| Sem atualização | `statusCategory != Done AND updated <= -14d` (prazo parametrizável) |
| Parada no mesmo status | Variante com `status CHANGED`, a validar na amostra. A alternativa segura é o filtro de sem atualização. |
| Sem estimativa | `originalEstimate is EMPTY` (tarefas abertas) |
| Sem vencimento | `duedate is EMPTY` (tarefas abertas) |
| Sem responsável | `assignee is EMPTY` (tarefas abertas) |
| Tempo em cada status | Não é nativo. Ver abaixo. |

### Tempo por status

Alternativas comparadas: app de Time in Status, eazyBI, ScriptRunner e campos alimentados por Automation. Recomendação por árvore de decisão:

1. Com app de Time in Status instalado, é a melhor opção.
2. Sem ele, mas com eazyBI, usa-se o eazyBI.
3. Sem nenhum dos dois, usam-se campos personalizados com Automation. A manutenção é maior e não há histórico retroativo.

### Horas por pessoa

- `worklogAuthor` e `worklogDate` apenas filtram tarefas, não somam horas.
- O gadget Timesheet soma horas por pessoa e por dia, dentro de período e filtro. A existência e as opções na versão do usuário devem ser confirmadas.
- O Relatório de Time Tracking compara estimado e registrado por projeto, versão ou sprint, não por período livre.
- Estimado × registrado por pessoa e por período exige eazyBI.

### Cards de tempo dedicado

- `timespent` é o tempo da própria issue. `aggregatetimespent` (Σ Time Spent) é o da issue mais suas subtarefas.
- **Card de tarefas:** Time Spent das tarefas padrão.
- **Card de subtarefas:** Time Spent das subtarefas.
- **Card combinado:** soma dos dois cards acima, ou Σ Time Spent apenas das tarefas padrão. Nunca somar o Σ do pai com o tempo das subtarefas.
- Limites nativos: Resultados do Filtro não totaliza horas, e o Timesheet respeita o filtro de issues. O responsável da issue não é necessariamente quem registrou as horas. Totais separados por tipo exigem um Timesheet por filtro, e o comportamento com subtarefas deve ser testado.
- O eazyBI faz os cards completos, com estimado × realizado.

## 7. Divergência de datas e botão de sincronização

### Detecção

O JQL nativo não compara o vencimento da subtarefa com o do pai.

1. **Núcleo:** regra agendada do Automation compara `{{issue.duedate}}` com `{{issue.parent.duedate}}` e marca a subtarefa com o rótulo `venc-divergente`, ou com um campo, removendo a marca quando alinhada. O filtro usado é `labels = venc-divergente AND statusCategory != Done`. A defasagem é a do agendamento.
2. **Módulo ScriptRunner:** campo com script exibe o vencimento do pai e a diferença em dias, com possibilidade de busca. As funções `subtasksOf()` e `parentsOf()` listam subtarefas ou pais. Que alguma delas compare datas é ponto a testar na instância do usuário. Exige licença e manutenção de scripts, e há impacto de desempenho.
3. **Módulo eazyBI:** tabela com pai, subtarefa, responsável, status e as duas datas, com a diferença calculada. A importação é em lotes.

Nativamente só é possível listar subtarefas ordenadas por pai, sem o vencimento do pai ao lado.

### Onde fica o botão

| Opção | Avaliação |
|---|---|
| (a) Automation com gatilho manual | **Recomendada.** O botão fica na própria tarefa. Confirmar na versão do usuário se o Automation vem incluído e se o gatilho manual existe. |
| (b) Webhook de entrada acionado por link | Alternativa. O link dispara GET, o webhook costuma esperar POST, e o segredo vai na URL. |
| (c) ScriptRunner (fragmento) ou JMWE (transição) | Viável se não houver Automation. Exige app. |
| (d) Gadget personalizado | Não recomendado. Custo alto e frágil. |

### Regra recomendada

1. Gatilho manual, em tarefas padrão, restrito a um grupo.
2. Condição: vencimento do pai não vazio. Se vazio, a regra para e comenta no pai.
3. Ramificação para subtarefas.
4. Condições no ramo: `statusCategory != Done` e vencimento diferente do pai.
5. Ação: editar o vencimento para `{{triggerIssue.duedate}}`.
6. Comentário na subtarefa com a data antiga, a nova e `{{initiator.displayName}}`.
7. Permissões de quem executa: Editar Tarefas, Agendar Tarefas e Adicionar Comentários no projeto das subtarefas.
8. Riscos: sobrescrever datas legítimas, mitigado ao ignorar subtarefas concluídas e ao registrar comentário. O guia traz roteiro de teste em homologação.

## 8. Layout, compartilhamento e permissões

### Layout (3 colunas)

1. Faixa de totais e cards de horas.
2. Visão da equipe (pizzas, responsável × status, Criadas vs Resolvidas).
3. Negligenciados.
4. Datas e horas (divergências, Timesheet, módulo de tempo por status).
5. Lista de tarefas com vencimento divergente. O botão em si fica na tarefa.

Máximo de 12 a 15 gadgets por dashboard.

### Compartilhamento e permissões

- Dashboard e filtros têm compartilhamento independente. Quem vê o dashboard sem acesso a um filtro vê o gadget com erro ou vazio.
- Compartilhar dashboard e filtros com o mesmo grupo.
- Dono do dashboard e dos filtros deve ser uma conta de serviço ou de administrador. Se o dono sair, o administrador transfere a propriedade.
- A conta que executa a automação precisa das permissões da seção 7.

## 9. Validação, desempenho, manutenção e evolução

### Checklist de validação (amostra real)

- A contagem de cada gadget bate com a mesma consulta no Navegador de Tarefas.
- Tipos somam o total, e status somam o total.
- Amostra de cerca de 10 tarefas confere à mão: horas registradas, estimativa e Σ Time Spent do pai.
- Teste do card combinado, sem contagem dupla.
- Fronteiras de data: vencimento hoje não é atrasada, e o fuso não desloca o `worklogDate`.
- Teste com usuário sem acesso aos filtros.
- Teste do botão em homologação: pai sem data, subtarefa concluída e subtarefa já alinhada.

### Desempenho e manutenção

- Evitar encadeamento profundo de filtros e `status CHANGED` ou funções de app pesadas em filtros usados por vários gadgets.
- Agendar a regra de divergência e a importação do eazyBI fora do horário de pico, e delimitar a regra por projeto.
- Pontos de manutenção: membros do grupo, dono de filtros e dashboard, mudanças de fluxo de trabalho e conta da automação.

### Melhorias futuras

- Alertas de tarefas atrasadas por e-mail via Automation.
- Assinatura de filtro por e-mail, que envia a lista de tarefas do filtro e **não o dashboard inteiro**.
- Relatório periódico em PDF do dashboard apenas com app compatível confirmado.

## 10. Pontos em aberto (dependem do ambiente do usuário)

- Versão do Jira Data Center e disponibilidade de cada gadget, do Automation incluído e do gatilho manual.
- Apps instalados e permissão para instalar.
- Se `subtasksOf()` e `parentsOf()` do ScriptRunner permitem comparar datas entre pai e filho.
- Comportamento do Timesheet com subtarefas e da combinação `status CHANGED`.
- Dados da seção 1 (projetos, usernames, fluxo, campos, volume).
