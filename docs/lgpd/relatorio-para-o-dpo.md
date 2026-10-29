# Relatório para o encarregado de dados (DPO): Painel de Squads

Documento do Plano 11. Reúne o que o encarregado de dados (DPO) precisa para decidir se as telas de **Auditoria** e **Integridade** podem mostrar dados reais. Está em linguagem direta; os termos técnicos são explicados na primeira vez. Este relatório **não afirma nenhuma base legal**: ela é decisão do DPO, e as candidatas aparecem como perguntas (seções 5 e 11). Nenhum nome, endereço ou e-mail reais estão aqui.

Hoje as duas telas ficam **fechadas** em produção por uma trava no próprio painel, até o aceite do DPO (seção 12). Sem o aceite, o painel mostra apenas as demais telas.

## 1. O que é o painel

O Painel de Squads é uma página interna do time TG-Basic que organiza os dados das tasks do Jira (prazos, carga de trabalho, ausências, fluxo de entrega) para a gestão do time. **Nesta etapa ele é somente leitura do Jira: lê dados, nunca escreve no Jira, e não cria ranking de pessoas.** O painel só passa a falar com o Jira quando o usuário responsável liga, de propósito, a chave de acesso ao Jira (`JIRA_LIVE_ENABLED`); até lá usa só dados fictícios de demonstração.

Se o Plano 16 ("escrita no Jira pelo painel") for executado um dia, **este relatório, o inventário e o aceite do DPO precisam ser revistos antes** de ligar a escrita, porque o painel deixaria de ser somente leitura.

## 2. Titulares

As pessoas cujos dados o painel trata são os membros do time TG-Basic (quem tem tasks), os líderes de squad, a coordenação e o gerente. A identificação é pelo nome de usuário do Jira (username); o painel não guarda nome de exibição, e-mail, foto nem comentários.

## 3. Dados tratados

Resumo: por task, o responsável, o Reporter, as datas, o status e o histórico de alterações de campos permitidos (status, resolução, vencimento, labels, responsável e estimativas em segundos); por pessoa, as horas registradas (com a data em que foram registradas), as ausências planejadas (sem o motivo), os papéis no painel e os acessos dela ao próprio painel. Texto livre de issue, comentários e descrições **não** são guardados como dado de pessoa.

A lista completa, coluna por coluna, com finalidade, quem vê e retenção, está em [`inventario-de-dados.md`](inventario-de-dados.md). Um teste automático compara essa lista com o banco de dados real: coluna nova sem linha no inventário derruba o teste.

## 4. Finalidades por módulo

- **Tempo em status e cards de trabalho:** medir o fluxo e acompanhar prazos, riscos, entregas e metas das tasks. Todos os papéis, cada um dentro da sua visão.
- **Carga e ausências:** planejar a capacidade do time. O usuário vê só o próprio; o líder, o squad; coordenação e gerente, todos.
- **Auditoria (`AUDITORIA`):** conferir quem alterou prazo, responsável e labels de atraso, e dois sinais novos (estimativa reduzida depois do início do trabalho; horas registradas com atraso). Só coordenação e gerente. **Indício, não prova**: cada sinal pede conferência humana.
- **Integridade (`INTEGRIDADE`):** padrões de fechamento de tasks por pessoa. Só o gerente. Mais sensível, porque **pontua e compara pessoas**: cada sinal vale pontos (pela gravidade); para cada pessoa o painel calcula os pontos, as tasks concluídas, a taxa (pontos por max(concluídas, mínimo de concluídas do YAML), para que quem concluiu pouco não tenha taxa inflada), o número de sinais graves e o múltiplo da taxa dela em relação à taxa mediana do time, e classifica em um nível de indício (ALTO ou MÉDIO). Só quem está nesses níveis aparece na lista, em ordem alfabética, sem posição. Também indício, não prova.
- **Distribuição e rebalanceamento:** o painel sugere quem poderia assumir uma task (em modo sombra, isto é, só mostra) e registra a decisão humana. Coordenação e gerente.
- **Registro de acesso:** cada consulta ao painel é registrada (quem, qual tela, quando) para auditoria de uso. O gerente consulta o registro inteiro; cada pessoa vê só os próprios acessos recentes, na tela Meus dados.

## 5. Bases legais candidatas, como perguntas

O painel **não decide** a base legal. Para o DPO avaliar, as candidatas conhecidas são perguntas, não afirmações:

- O tratamento por Auditoria e Integridade poderia se apoiar no **legítimo interesse** do empregador (gestão do trabalho e dos prazos), com teste de balanceamento documentado?
- Poderia se apoiar na **execução do contrato de trabalho**?
- Há outra base que o DPO considere mais adequada?
- O tratamento dos demais módulos (fluxo, carga, ausências) segue a mesma base ou outra?

A resposta é decisão do DPO e fica na seção 11 (perguntas 1 e 2). Enquanto isso, a coluna "Base legal" do inventário diz "a definir pelo DPO".

## 6. Medidas técnicas e organizacionais

Cada medida abaixo cita o teste automático que a prova (classe e método).

- **Trava do DPO, fechada por padrão:** em produção, sem o aceite, Auditoria e Integridade respondem "aguardando aceite do DPO" e não entregam dado algum: `TravaDpoRoutesTest.producaoSemAceiteBloqueiaAuditoria`, `TravaDpoRoutesTest.producaoSemAceiteBloqueiaIntegridade`. Aceite sem a referência do parecer derruba a subida: `TravaDpoTest.aceiteSemReferenciaFalha`. Rota sensível nova esquecida da trava faz o teste falhar: `TravaDpoRoutesTest.todaRotaSensivelPassaPelaTrava`.
- **Papéis e visibilidade, negar por padrão:** quem não tem o papel recebe 403 sem sequer saber que o módulo existe: `TravaDpoRoutesTest.usuarioContinua403SemCodigoDoDpo`, `IntegridadeRoutesTest.integridadeSoGerenteEmTodasAsRotas`.
- **Registro de toda consulta, sem o alvo:** o registro de acesso guarda quem consultou o módulo, nunca a pessoa, o sinal ou a quantidade consultados: `TravaDpoRoutesTest.auditoriaGuardaSoQuemConsultouNuncaOAlvoNemOTotal`. O motivo digitado numa decisão de distribuição nunca vai para o registro: `DistribuicaoRoutesTest.postGravaAccessLogSemOCorpoDoMotivo`.
- **Sanitização do JSON da issue:** o painel remove nome de exibição, e-mail, foto, identificador de conta e fuso antes de guardar: `SanitizadorRawTest.usuarioPerdeDadosPessoaisENormalizaName`.
- **Histórico só com valores permitidos:** campos de texto livre (descrição, título, comentário) só registram que houve mudança, sem o texto: `ChangelogParserTest.textoLivreContinuaSemValor`. As estimativas entram só em segundos. O comentário do worklog nunca é lido: `WorklogParserTest.worklogSemComentario`.
- **Inventário ligado ao banco:** `InventarioDeDadosTest.todaColunaEstaNoInventario`.
- **Retenção por categoria:** `RetentionTest.cadaCategoriaComSeuPrazo`.
- **Direito de acesso do titular sem escolher outra pessoa:** `TitularRoutesTest.titularNaoEscolheOutraPessoa`, sem o conteúdo dos sinais: `TitularRoutesTest.semConteudoDeSinais`.
- **Proteções do navegador e do servidor:** cabeçalhos de segurança em toda resposta (`CabecalhosDeSegurancaTest.todaRespostaTemOsCabecalhos`) e proteção contra requisição forjada (`ProtecaoCsrfTest.postSemCabecalhoEh403SemExecutar`).
- **Pacote sem segredos:** o token do Jira fica só no ambiente do servidor, nunca no banco, no repositório ou nos logs (mascarado).
- **Organizacionais:** o aceite do DPO só é ligado pelo usuário responsável; nenhuma automação o liga. Os arquivos com dados de pessoas (papéis, equipes, ausências) ficam fora do repositório.

## 7. Riscos aos titulares e mitigação

- **Sinal lido como conclusão.** Mitigação: toda tela e resposta de Auditoria e Integridade levam o aviso fixo "indício, não prova"; a linguagem nunca afirma má-fé.
- **Acesso indevido a dado sensível.** Mitigação: acesso restrito por papel, trava do DPO fechada por padrão e registro de toda consulta.
- **Pontuação e comparação por pessoa (Integridade).** Existe: pontos, taxa, evidências e múltiplo em relação ao time, e um nível de indício por pessoa. Mitigação: só o gerente vê, a trava do DPO fica fechada até o aceite, o aviso "indício, não prova" acompanha tudo, a lista é alfabética e não há posição nem ranking. O DPO decide o aceite sabendo disso (pergunta 2).
- **Decisão automatizada sobre pessoas.** Não existe: as sugestões de distribuição ficam em modo sombra (só mostram) e a decisão é sempre humana, com registro.
- **Dado guardado por mais tempo que o necessário.** Mitigação: retenção por categoria (seção 8) e expurgo diário.
- **Dado antigo ou incompleto** (carga do Jira ainda não refeita): os sinais avisam quando faltam dados e não geram achado sem eles.

## 8. Retenção proposta

Prazo proposto: **24 meses** para todas as categorias (decisão provisória do usuário em 25/10, a confirmar com o DPO). Cada categoria tem a sua variável, de 1 a 120 meses:

| Categoria | Variável | Proposta |
|---|---|---|
| Histórico (issue resolvida antiga, alterações, horas registradas, mapa de chaves de usuário) | `RETENTION_HISTORICO_MONTHS` | 24 meses |
| Registro de acesso | `RETENTION_ACCESS_LOG_MONTHS` | 24 meses |
| Decisões de distribuição | `RETENTION_DECISOES_MONTHS` | 24 meses |
| Execuções da sincronização e das cópias de segurança | `RETENTION_EXECUCOES_MONTHS` | 24 meses |

Sem variável própria, vale `RETENTION_MONTHS`. O valor final de cada categoria é decisão do DPO (pergunta 3).

## 9. Direitos do titular

Cada pessoa vê os **próprios** dados na tela **Meus dados** (e baixa em um arquivo): quantidades, papéis, categorias tratadas, retenção e os próprios acessos dos últimos 90 dias. Correção e eliminação se pedem pelo canal do DPO; os dados vêm do Jira e são corrigidos lá. O passo a passo está em [`atendimento-ao-titular.md`](atendimento-ao-titular.md).

## 10. Incidentes

O procedimento operacional está em [`../operacao/runbook-incidentes.md`](../operacao/runbook-incidentes.md). O prazo de comunicação à ANPD e aos titulares segue a regulamentação vigente da ANPD, **a confirmar com o DPO**; este documento não fixa número de dias.

## 11. Perguntas ao DPO

Cada pergunta traz as opções (a recomendada primeiro) e o que muda no painel.

### Pergunta 1. Qual é a base legal da Auditoria?
- Legítimo interesse, com teste de balanceamento (Recomendada). Consequência: a Auditoria pode abrir assim que o aceite for registrado, com o teste de balanceamento anexado ao parecer.
- Execução do contrato de trabalho. Consequência: igual, sem teste de balanceamento, e com a comunicação à equipe citando o contrato.
- Outra base indicada pelo DPO. Consequência: o painel não muda; só o texto dos documentos é ajustado.

### Pergunta 2. Qual é a base legal da Integridade, que pontua cada pessoa e a compara com o time?
- Legítimo interesse, com teste de balanceamento próprio, mais rigoroso (Recomendada). Consequência: a Integridade só abre com o parecer específico, já que avalia padrões por pessoa.
- Execução do contrato de trabalho. Consequência: igual, sem teste de balanceamento.
- Outra base, ou manter a Integridade desligada. Consequência: a Integridade permanece fechada pela trava e o resto do painel segue normal.

### Pergunta 3. Qual é o prazo de retenção de cada categoria?
- 24 meses para todas (Recomendada). Consequência: nada muda, é o padrão atual.
- 12 meses para todas. Consequência: o operador define `RETENTION_MONTHS=12` (ou as variáveis por categoria) e o expurgo diário apaga o que passar disso.
- Prazos diferentes por categoria. Consequência: o operador define as quatro variáveis `RETENTION_*_MONTHS`.

### Pergunta 4. O titular pode ver o conteúdo dos sinais sobre ele?
- Não, só as categorias tratadas (Recomendada). Consequência: nada muda, é o comportamento atual da tela Meus dados.
- Sim, na tela Meus dados. Consequência: muda o painel: a tela e o arquivo passam a listar os sinais da própria pessoa.
- Sim, somente mediante pedido ao DPO. Consequência: nada muda no painel; o DPO pede o levantamento ao operador.

### Pergunta 5. Como a equipe será avisada?
- Texto de `comunicacao-a-equipe.md` por e-mail interno, antes do uso real (Recomendada). Consequência: o aceite só é ligado depois do envio.
- Reunião com o texto como apoio. Consequência: igual, com ata da reunião anexada ao parecer.
- Outra forma indicada pelo DPO. Consequência: o texto é ajustado ao canal escolhido.

### Pergunta 6. Os módulos podem ser liberados separadamente (Auditoria antes da Integridade)?
- Não, liberar os dois juntos (Recomendada). Consequência: nada muda; hoje a trava libera os dois com o mesmo aceite.
- Sim, Auditoria primeiro. Consequência: pequena mudança no painel: duas variáveis de aceite em vez de uma.
- Sim, qualquer ordem. Consequência: a mesma mudança pequena, com referência de parecer por módulo.

### Pergunta 7. Quem saiu do time (sem papel vigente no diretório) exerce o direito de acesso pela tela?
- Não, pelo canal do DPO, que pede o levantamento ao operador (Recomendada). Consequência: nada muda no painel; quem não tem papel vigente recebe 403 na tela Meus dados.
- Sim, a tela passa a aceitar quem consta no diretório mesmo sem papel vigente. Consequência: pequena mudança no painel e revisão do controle de acesso.

## 12. Como registrar o aceite

Depois do parecer, o **usuário responsável** (nunca uma automação) define no ambiente de produção, no arquivo de ambiente do servidor:

- `DASHBOARD_DPO_ACEITE=true`
- `DASHBOARD_DPO_ACEITE_REF=<referência do parecer>` (de 1 a 80 caracteres: letras, números, ponto, barra, hífen e sublinhado; por exemplo `DPO-2026-11`)

Sem a referência, o painel não sobe. A data do aceite e a referência são anotadas no `HANDOFF.md`. A tela de Configuração (só o gerente) mostra a referência do aceite quando ele está ligado.

## 13. Relatório de impacto (RIPD)

Se o DPO pedir um Relatório de Impacto à Proteção de Dados, este documento e o [inventário](inventario-de-dados.md) são o ponto de partida.
