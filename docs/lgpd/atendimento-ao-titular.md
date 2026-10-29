# Atendimento ao titular

Como o Painel de Squads atende os direitos de quem tem dados tratados nele (LGPD). Complementa o [relatório para o DPO](relatorio-para-o-dpo.md). Os prazos abaixo são **sugestões internas, a confirmar com o DPO**.

## 1. Consultar os próprios dados

Qualquer pessoa, em qualquer papel, abre a tela **Meus dados** no menu. Ela mostra, só da própria pessoa, o que o painel guarda (quantidades de tasks, alterações e registros de horas, ausências planejadas, papéis), as categorias tratadas com finalidade e prazo, e os acessos dos últimos 90 dias. O botão "Baixar meus dados (JSON)" gera um arquivo com o mesmo conteúdo, no próprio navegador. A tela não aceita pedir os dados de outra pessoa.

Nesta versão a tela **não mostra o conteúdo** de sinais de Auditoria ou de Integridade sobre a pessoa; a pergunta 4 do relatório pergunta ao DPO se isso deve mudar. Quem quiser esse levantamento pede ao DPO.

## 2. Pedir correção

Os dados vêm do Jira e são corrigidos lá: a pessoa pede ao PL ou ao líder de squad para ajustar a task, o responsável ou o prazo no Jira. O painel passa a refletir a correção na sincronização seguinte. Para corrigir algo que o painel guarda por conta própria (por exemplo, um papel no diretório de papéis), o pedido vai ao canal do DPO, que aciona quem edita o arquivo de configuração.

Prazo sugerido: resposta em até 15 dias corridos.

## 3. Pedir eliminação

O pedido vai pelo **canal do DPO**, que avalia o que pode ser eliminado respeitando a retenção legal. Se for aprovada, o operador do painel apaga os dados da pessoa pelo procedimento do runbook diário, e a retenção automática (seção 8 do relatório) apaga o restante no prazo. Eliminar dado de uma pessoa não apaga o dado no Jira, que é o sistema de origem, e **a próxima sincronização traz de volta** do Jira o que foi eliminado do painel (enquanto o dado existir lá e estiver dentro da retenção). Eliminação definitiva depende de tratar o dado no Jira, ou de o DPO decidir uma exclusão que o painel respeite (hoje não existe); o pedido deve ser registrado com essa ressalva.

Quem saiu do time e não tem mais papel no diretório não abre a tela Meus dados (recebe "sem permissão"): o pedido dessa pessoa também vai pelo canal do DPO (pergunta 7 do relatório).

Prazo sugerido: resposta em até 15 dias corridos; execução em até 30 dias.

## 4. Registro dos pedidos

Cada pedido, a data, a decisão do DPO e a execução ficam registrados **fora do painel**, no canal do DPO. O painel não guarda esses pedidos. A consulta à tela Meus dados já fica registrada no registro de acesso, como qualquer outra.

## 5. Incidentes

Se um dado for exposto, vale o [runbook de incidentes](../operacao/runbook-incidentes.md); a comunicação segue o prazo da regulamentação vigente da ANPD, a confirmar com o DPO.
