# Guia do Painel de Squads

Versão em inglês: [`panel-guide.md`](panel-guide.md). Perguntas frequentes: [`perguntas-frequentes.md`](perguntas-frequentes.md). Treinamento por papel: `docs/treinamento/`.

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`): o painel não fala com nenhum servidor Jira e os números que se veem vêm dos **dados de demonstração fictícios** (`DASHBOARD_DEMO_DATA=true`, só com `DASHBOARD_DEV_MODE=true`). Tudo o que este guia diz sobre números reais vale depois que o usuário ligar a flag e a primeira sincronização rodar. Auditoria e Integridade (e os cartões VG-13 e VG-14 da Central) estão atrás da Trava do DPO, fechada por padrão até o aceite do encarregado de dados.

Este guia segue o catálogo de cartões do backend e o menu do painel: cada item do menu tem uma seção, na mesma ordem do menu, e cada cartão citado existe no catálogo com o mesmo nome e os mesmos papéis. Para quem instala o painel, veja [`docs/implantacao.md`](../implantacao.md).

## Antes de começar

**Papéis e o que cada um vê.** O papel vem de `papeis.yaml` e define o menu e os dados. O **Usuário** vê só o que é seu; o **Squad Leader** vê o próprio e o seu squad; **Coordenador** e **Gerente** veem todos. Registro de acesso, Sincronização, Configuração e Integridade são só do Gerente; Qualidade dos dados, Decisões de distribuição, Mapa de cartões, Auditoria e Divergência são da coordenação e do gerente. Em desenvolvimento (`DASHBOARD_DEV_MODE=true`) não há login, o servidor age como gerente e todos os dados aparecem, uma faixa vermelha avisa e o seletor "Papel (simulação)" só mostra o menu daquele papel: **o escopo de dados por papel só vale em produção, com login**. Em produção o acesso é por login único (SSO/OIDC).

**Filtros.** As telas de trabalho e prazos têm período, squad, pessoa, tipo e prioridade (cada tela mostra os que fazem sentido). Os filtros ficam na URL: copie o endereço para compartilhar o mesmo recorte.

**Unidade de tempo.** O padrão é **Dias úteis**; há a opção **Dias corridos**. O fuso é America/Manaus.

**Gaveta de tasks.** Todo número de um cartão com "Abre tasks: sim" abre a lista de tasks que o compõe, com o mesmo total. Dentro da gaveta há exportação em CSV e, se o acesso ao Jira estiver ligado, o link "Abrir no Jira". A chave de cada linha abre a gaveta da task; "Abrir em nova aba" leva o mesmo recorte para a página de detalhe.

**Estados de tela.** Carregando, vazio (com "Limpar filtros"), erro (com "Tentar de novo"), sem permissão (a mensagem diz quais papéis veem a tela), sessão expirada e filtro inválido. Dados incompletos aparecem como aviso, com caminho para a Qualidade dos dados.

**Símbolos.** `✓` ok, `●` atenção, `▲` problema, `■` neutro. Eles sempre vêm com texto: a cor nunca é o único sinal. Os temas são Claro, Escuro e Sidia; os idiomas, pt-BR e inglês.

**Pessoas.** As tabelas de pessoas ficam em ordem alfabética. O painel não tem ranking nem nota de pessoa.

## Como ler os números

- **No prazo e No prazo ajustado.** Duas métricas lado a lado. Só entram tasks concluídas (categoria concluído, resolução preenchida e que não seja cancelamento), fora de Epic, com vencimento no período. "No prazo" conta as que saíram até o vencimento (dia da resolução no fuso de Manaus). "No prazo ajustado" conta também as que têm um label que justifica o atraso (lista em `labels_atraso.yaml`).
- **Indício, não prova.** Sinais de Auditoria e de Integridade mostram o que merece conferência humana. Eles nunca são uma conclusão sobre uma pessoa.
- **sem capacidade.** Quando a capacidade de uma pessoa na semana é zero (férias, feriado, ausência), a ocupação não vira porcentagem: aparece "sem capacidade".
- **Dias úteis e Dias corridos.** O tempo conta dias úteis por padrão (fins de semana e feriados do calendário ficam de fora); o filtro permite dias corridos.
- **Amostra pequena.** Com menos de 5 tasks, percentis (P50 e P85) aparecem com aviso de amostra pequena.

## Trava do DPO

A Trava do DPO impede que a Auditoria e a Integridade sirvam dados reais antes do aceite do encarregado de dados (DPO). Hoje (código na `main`):

- **O que passa pela trava:** a Auditoria (cartões AU-01 a AU-03, DV-01 e o VG-14 da Central) e a Integridade (cartões IN-01 a IN-08 e o VG-13 da Central). Com a trava fechada as telas mostram "Esta tela fica desligada até o encarregado de dados (DPO) aprovar o uso", nenhum dado é consultado e o servidor responde com o código `AGUARDANDO_ACEITE_DPO`. O histórico campo a campo da gaveta de Auditoria só abre com a trava aberta.
- **Quando abre:** só com o aceite por configuração (`DASHBOARD_DPO_ACEITE=true` com a referência do parecer, ligado só pelo usuário) ou na demonstração: modo de desenvolvimento, Jira desligado e banco sem dado sincronizado de verdade. Ela é **fechada por padrão**; em produção sem aceite está fechada.
- **Quando fecha de novo:** qualquer estado de sincronização de uma instância do Jira no banco fecha a porta da demonstração, mesmo que a flag seja desligada depois; só o aceite a mantém aberta.
- **Aviso à coordenação:** quando algum item fica de fora por causa da trava, a Central mostra "Alguns itens ficam fora desta lista até o encarregado de dados (DPO) aprovar o uso das telas sensíveis que você tem permissão de ver" (código `ITENS_AGUARDANDO_ACEITE_DPO`), sem nomear o módulo.

## Meus dados e proteção de dados

Todos os papéis têm a tela **Meus dados** (`meusDados`, rota `/api/titular/meus-dados`): ela mostra só o registro da própria pessoa, com quantidades de tasks, alterações e registros de horas, ausências planejadas (sem o motivo), papéis, as categorias tratadas com finalidade e prazo, e os acessos dela nos últimos 90 dias (no máximo 200 acessos na lista), além da contagem de decisões de distribuição em que ela aparece. Na demonstração o usuário de desenvolvimento ("dev") aparece com as contagens zeradas. O botão "Baixar meus dados (JSON)" gera o mesmo conteúdo no navegador. A tela não aceita pedir os dados de outra pessoa e não mostra o conteúdo de sinais de Auditoria ou de Integridade sobre a pessoa. Quem saiu do time e não tem papel vigente recebe "sem permissão" e exerce o direito pelo canal do DPO. Para corrigir um dado, corrija no Jira (o painel reflete na sincronização seguinte); para pedir eliminação, o caminho é o canal do DPO. Veja [`docs/lgpd/atendimento-ao-titular.md`](../lgpd/atendimento-ao-titular.md).

**Retenção por categoria.** O padrão é 24 meses para todas as categorias (decisão provisória, a confirmar com o DPO), cada uma com a sua variável, de 1 a 120 meses: `RETENTION_HISTORICO_MONTHS` (issue resolvida antiga, alterações, horas registradas), `RETENTION_ACCESS_LOG_MONTHS` (registro de acesso), `RETENTION_DECISOES_MONTHS` (decisões de distribuição) e `RETENTION_EXECUCOES_MONTHS` (execuções da sincronização e das cópias de segurança). Reter o histórico por pouco tempo também encurta a janela da carga inicial da sincronização. O código ainda aceita as variáveis antigas `RETENTION_MONTHS` (6 a 600) e `ACCESS_LOG_RETENTION_MONTHS` (mínimo 12). A precedência de cada categoria é a variável própria, depois (só registro de acesso e decisões) `ACCESS_LOG_RETENTION_MONTHS`, depois `RETENTION_MONTHS`; na herança vale um piso de 12 meses para a auditoria (registro de acesso e decisões), e só a variável própria aceita menos.

## Início

### `central` — Central de atenção

**Pergunta que responde.** O que pede atenção agora, e onde está?

**Quem vê.** Coordenador, Gerente.

É o início da coordenação e do gerente. Reúne os cartões VG, cada um com o seu número e a gaveta de tasks. O cartão VG-13 (indícios de integridade) só aparece ao gerente, e o VG-14 (itens da Auditoria) à coordenação e ao gerente, ambos só com a Trava do DPO aberta (ver "Trava do DPO"). Se algum item ficar de fora por causa da trava, a coordenação vê um aviso, sem nome de módulo.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| VG-01 | Abertas | Quantas tasks estão abertas (não concluídas) na visão de quem consulta. Abre as mesmas tasks do card Abertas. | Coordenador, Gerente | sim |
| VG-02 | Concluídas nos últimos 30 dias | Quantas tasks foram concluídas nos últimos 30 dias, comparadas com os 30 dias anteriores. Abre as mesmas tasks do card Fechadas. | Coordenador, Gerente | sim |
| VG-03 | Ciclo das concluídas (30 dias) | Mediana (P50) e P85 do ciclo, do início em andamento à conclusão, das tasks concluídas nos últimos 30 dias. Abre as tasks que formam a faixa. | Coordenador, Gerente | sim |
| VG-04 | No prazo e no prazo ajustado | Das tasks com vencimento nos últimos 90 dias, quantas foram concluídas no prazo e quantas no prazo ajustado (com atraso justificado). As duas métricas ficam lado a lado. | Coordenador, Gerente | sim |
| VG-05 | Vencidas | Tasks abertas cujo vencimento já passou. Abre as mesmas tasks do card Negligenciadas. | Coordenador, Gerente | sim |
| VG-08 | Risco alto e médio | Quantas tasks abertas estão em risco alto e em risco médio pelo score explicável. Abre as mesmas tasks do card Riscos. | Coordenador, Gerente | sim |
| VG-09 | Central de atenção | Os indicadores que pedem atenção da coordenação e do gerente: vencidas, risco, capacidade, atrasos previstos, sem atualização, paradas, sem responsável, bloqueadas, metas, checklist e review, divergência, alterações sem autorização e estimativa. Cada indicador abre as tasks. | Coordenador, Gerente | sim |
| VG-10 | Entregas por semana | As tasks concluídas em cada uma das últimas 12 semanas. Cada coluna abre as tasks entregues na semana. | Coordenador, Gerente | sim |
| VG-11 | Quando o backlog termina | O tamanho do backlog e a data em que ele deve terminar pelo ritmo recente, em três níveis de confiança (P50, P85 e P95). Abre as tasks do backlog. | Coordenador, Gerente | sim |
| VG-12 | Ocupação por squad | A ocupação média e o índice de Jain de cada squad nas próximas 4 semanas. É um indicador do conjunto, sem nota de pessoa. | Coordenador, Gerente | não |
| VG-13 | Pessoas com indício alto | As evidências de integridade das pessoas com indício alto. É um indício para conferir, nunca uma prova. Só o gerente vê, e só com a trava do DPO aberta. | Gerente | sim |
| VG-14 | Itens da Auditoria na Central | As alterações sem autorização e as divergências Task × Review que a Central destaca. São indícios para conferir, nunca provas. Só aparecem com a trava do DPO aberta. | Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/central.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `squad` — Meu squad

**Pergunta que responde.** Como está o meu squad esta semana?

**Quem vê.** Squad Leader.

Início do Squad Leader; o item do menu é só do Squad Leader e a tela aceita `?squad=` na URL para escolher o squad. Mostra a carga de cada pessoa em ordem alfabética e sem nota, o que vence em 7 dias com a folga real, as ausências (sem motivo) e o risco. Cada número abre as tasks do cartão de origem (Carga, Vencimentos, Capacidade e Riscos) com o mesmo filtro de squad. Esta tela não tem cartões próprios no catálogo.

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.


### `meu` — Meu painel

**Pergunta que responde.** O que tenho para fazer, e o que está apertado?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

Início do usuário. Em produção, mostra só o que é seu; o líder do squad pode abrir o painel de uma pessoa do seu squad. Na demonstração o usuário de desenvolvimento não tem tasks, então os indicadores MP-01, MP-03, MP-05 e MP-06 aparecem zerados.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| MP-01 | Minhas abertas | Quantas tasks abertas estão com você e quantas delas vencem nos próximos 7 dias. Cada número abre as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MP-02 | Minha ocupação em 4 semanas | Quanto da sua capacidade das próximas 4 semanas a sua fila ocupa, já descontadas ausências e feriados. | Usuário, Squad Leader, Coordenador, Gerente | não |
| MP-03 | Em risco | Quantas das suas tasks abertas estão em risco médio ou alto pelo score explicável. Abre essas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MP-04 | Minhas ausências futuras | Os períodos de ausência cadastrados para você a partir de hoje. O painel não guarda nem mostra o motivo. | Usuário, Squad Leader, Coordenador, Gerente | não |
| MP-05 | Minha fila | Suas tasks abertas na ordem em que a agenda as executaria, com o término previsto pela sua capacidade e o aviso de quando passam do vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MP-06 | No prazo e no prazo ajustado | Das suas tasks com vencimento nos últimos 90 dias, quantas foram concluídas no prazo e quantas no prazo ajustado (com atraso justificado), lado a lado. Cada número abre as tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `meusDados` — Meus dados

**Pergunta que responde.** O que o painel guarda sobre mim, para que serve e por quanto tempo?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

Tela do titular (LGPD), para todos os papéis: mostra só o registro da própria pessoa e os acessos dela dos últimos 90 dias, e permite baixar o mesmo conteúdo em JSON. Não aceita pedir os dados de outra pessoa. Quem saiu do time e não tem papel vigente recebe "sem permissão" e procura o canal do DPO. Na demonstração o usuário de desenvolvimento ("dev") aparece com as contagens zeradas, como no Meu painel. A lista de acessos mostra no máximo 200 e a contagem de decisões de distribuição em que a pessoa aparece também entra no resumo. Detalhes na seção "Meus dados e proteção de dados".

**De onde vêm os números.** Esta tela não tem ficha nem cartões: lê o registro da própria pessoa pela rota `/api/titular/meus-dados`.


## Trabalho em curso

### `abertas` — Abertas

**Pergunta que responde.** O que está em aberto agora, há quanto tempo e o que vence?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| AB-01 | Abertas | Quantas tasks estão abertas agora e, destas, quantas estão em andamento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-02 | Idade histórica por tipo (P50 e P85) | Quanto tempo as tasks de cada tipo levaram, do início em andamento à conclusão, nas últimas 16 semanas: a referência para dizer se uma aberta está velha. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-04 | Acima do P85 do tipo | Tasks em andamento mais velhas do que 85% das concluídas do mesmo tipo: candidatas a atenção. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-05 | Abertas por status | Quantas tasks abertas há em cada status, na ordem do fluxo. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-06 | Abertas por prioridade | Quantas tasks abertas há em cada prioridade do Jira. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-07 | Abertas por idade | Quantas tasks abertas há em cada faixa de idade: até 7, 8 a 14, 15 a 30 e mais de 30 dias. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-08 | Abertas por vencimento | Quantas tasks abertas já venceram, vencem em até 7 dias, vencem depois ou não têm vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-09 | Tabela de abertas | Todas as tasks abertas da sua visão, com status, prioridade, idade, vencimento e horas restantes; ordenável e exportável. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AB-10 | Abertas por campo | Quantas tasks abertas há para cada valor de Checklist Type, SKU Type, Model Name, Model Category, TG Request Type e Carrier Code; cada grupo abre as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/abertas.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `epicos` — Épicos

**Pergunta que responde.** Como andam os épicos: quanto já foi entregue, o que está vencido e quando termina?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| EP-01 | Épicos | Por épico: tasks, concluídas, progresso, a mais antiga em aberto, vencidas, pontos e previsão em semanas. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/epicos.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `negligenciadas` — Negligenciadas

**Pergunta que responde.** O que está parado, sem dono, vencido ou sem movimento de verdade?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| NG-01 | Sem atualização | Tasks em andamento que o Jira não atualiza há mais de 7 dias. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NG-02 | Sem responsável | Tasks abertas que ninguém assumiu. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NG-03 | Vencidas | Tasks abertas cujo vencimento já passou. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NG-04 | Bloqueadas | Tasks abertas bloqueadas ou com impedimento marcado. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NG-05 | Paradas no status | Tasks em andamento há tempo demais no mesmo status. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NG-06 | A fazer há mais de 30 dias | Tasks que continuam na fila há mais de 30 dias. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/negligenciadas.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `riscos` — Riscos

**Pergunta que responde.** Quais tasks abertas correm mais risco de atrasar ou travar, e por quê?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| RI-01 | Risco alto e médio | Quantas tasks abertas estão em risco alto e em risco médio. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| RI-02 | Tasks em risco | A lista das tasks em risco alto ou médio, com o fator principal e os pontos de cada fator. O nível baixo fica no detalhamento para conferência. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| RI-03 | Sinais de risco por pessoa | Quantas tasks em risco e quantas abertas cada pessoa da sua visão tem, em proporção. Sem ranking: ordem alfabética. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/riscos.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `previsao` — Previsão

**Pergunta que responde.** Quando o backlog termina e quanto cabe nas próximas semanas, pelo ritmo real de entrega e de chegada?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| PV-01 | Backlog | Quantas tasks abertas ainda faltam entregar no seu escopo. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| PV-02 | Termina em | Em quantas semanas o backlog termina, em P50, P85 e P95, com a data da semana alvo. O cartão abre as tasks do backlog (o numerador da previsão). | Usuário, Squad Leader, Coordenador, Gerente | sim |
| PV-03 | Confiança da previsão | Quanto histórico sustenta a previsão: baixa, média ou alta, pelas semanas com dado. O cartão abre as entregas da janela. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| PV-04 | Vazão e chegada por semana | Quantas tasks foram entregues e quantas foram criadas em cada semana fechada (segunda a domingo de Manaus). | Usuário, Squad Leader, Coordenador, Gerente | sim |
| PV-05 | Quantos itens em 2, 4 e 8 semanas | Quantas tasks devem sair em 2, 4 e 8 semanas, em P50, P85 e P95 conservadores. É uma projeção, sem tasks por trás. | Usuário, Squad Leader, Coordenador, Gerente | não |
| PV-06 | Previsão por task | Para cada task em andamento, quanto falta pelo histórico de ciclos do mesmo tipo que passaram da idade dela. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/previsao.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `fluxoDaSemana` — Fluxo da semana

**Pergunta que responde.** O backlog está crescendo ou encolhendo? Quantas tasks entram, começam e saem a cada semana.

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| FX-01 | Fluxo da semana | Por semana, quantas tasks entraram, começaram e saíram, com as canceladas à parte. Cada barra abre as tasks daquela semana e série. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FX-02 | Saldo do backlog | Os totais das últimas 12 semanas (entrou, começou, saiu e canceladas) e o sinal de backlog crescendo. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/fluxo-da-semana.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `agingWip` — Envelhecimento por etapa

**Pergunta que responde.** Onde há tasks paradas na etapa há mais tempo do que o normal, antes de virarem atraso?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| AW-01 | Envelhecimento por etapa | Para cada etapa do fluxo, as tasks em andamento como pontos com a idade na etapa, contra a faixa normal (P50 e P85) da própria etapa. Cada etapa e cada contagem de "acima do normal" abre as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| AW-02 | Acima do normal | Quantas tasks estão em andamento e quantas delas estão há mais tempo na etapa do que o P85 histórico dessa etapa. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/aging-wip.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `bloqueios` — Bloqueios entre tasks

**Pergunta que responde.** Quais tasks estão travadas por outra task ainda aberta, e o prazo de uma cabe no da outra?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| BQ-01 | Bloqueadas por outra task | Tasks abertas que dependem de outra task ainda aberta, com a bloqueadora e o vencimento dela. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| BQ-02 | Bloqueadoras | As tasks abertas que travam outras e que estão ao seu alcance. As de pessoas que você não vê entram à parte, sem chave nem nome. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| BQ-03 | Prazo em conflito | Tasks bloqueadas por uma task que vence DEPOIS delas: o prazo de uma não cabe no da outra. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| BQ-04 | Bloqueadas sem causa registrada | Tasks em status bloqueado ou com a flag de impedimento, mas sem nenhuma bloqueadora aberta no Jira: o motivo do bloqueio não está registrado. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| BQ-05 | Verificar no Jira | Tasks cujo vínculo de bloqueio aponta para uma task que não está na base: não dá para saber daqui se a bloqueadora ainda está aberta. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/bloqueios.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Resultados

### `fechadas` — Fechadas

**Pergunta que responde.** O que foi entregue no período, em quanto tempo e dentro do prazo?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

Esta tela também traz o Controle do ciclo (cartões CT-01 e CT-02), que tem a sua própria ficha em `app/config/cards/controle-ciclo.card.yaml`.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| FE-01 | Concluídas | Quantas tasks foram concluídas no período. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-02 | Pontos entregues | Soma dos pontos das tasks concluídas no período; abre as concluídas que têm pontos. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-03 | Ciclo (P50 e P85) | Quanto tempo as tasks concluídas levaram desde o início em andamento até a resolução. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-04 | Lead time (P50 e P85) | Quanto tempo as tasks concluídas levaram desde a criação até a resolução. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-05 | No prazo e no prazo ajustado | Das tasks concluídas com vencimento no período, quantas saíram até o vencimento e quantas saíram até o vencimento contando também as com label que justifica o atraso. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-06 | Canceladas e sem resolução | Tasks fechadas com resolução de cancelamento no período e tasks em status concluído sem resolução. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-07 | Entregas por semana | Quantas tasks foram concluídas em cada semana (segunda a domingo, em Manaus). | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-08 | Dispersão do ciclo | Cada task concluída como um ponto (dia da resolução e dias de ciclo), com P50, P85 e P95. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-09 | Ciclo por tipo | P50 e P85 do ciclo de cada tipo de task; tipos não se misturam. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-10 | Entregas por projeto | Quantas concluídas há por projeto (prefixo da chave). | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-11 | Entregas por pessoa | Quantas tasks cada pessoa finalizou e em quantas participou, com pontos, ciclo e horas estimadas e registradas. Sem ranking: ordem alfabética. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-12 | Tabela de concluídas | Todas as tasks concluídas do período com ciclo, lead, vencimento, situação do prazo e horas; ordenável e exportável. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| FE-13 | Concluídas por campo | Quantas tasks concluídas do período há para cada valor de Checklist Type, SKU Type, Model Name, Model Category, TG Request Type e Carrier Code; cada grupo abre as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CT-01 | Controle do ciclo | Cada task concluída como um ponto (dia da conclusão x dias de ciclo), por tipo, com a média móvel e as faixas P50 e P85. Mostra se o processo está estável e quais tasks fogem da curva. Cada ponto abre as tasks do dia e do tipo. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CT-02 | Tendência mensal do ciclo | P50 e P85 do ciclo mês a mês, por tipo, para ver se o processo está ficando mais rápido. Cada mês abre as concluídas daquele mês e tipo. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/fechadas.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `negativas` — Negativas

**Pergunta que responde.** O que saiu do trilho no período: reaberto, devolvido, cancelado ou entregue com atraso?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| NE-01 | Reabertas | Tasks que voltaram de concluído para não concluído no período. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-02 | Devolvidas | Tasks que voltaram a uma etapa anterior do fluxo no período. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-03 | Canceladas | Tasks fechadas no período com resolução de cancelamento (Won't Do, Duplicate...). | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-04 | Concluídas com atraso | Tasks concluídas no período depois do vencimento, e quantas delas não têm label que justifique o atraso. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-05 | Abertas vencidas | Tasks ainda abertas cujo vencimento já passou. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-06 | Estouro de estimativa | Tasks concluídas no período em que o tempo gasto passou da estimativa original em mais de 25%. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-07 | Muitas trocas de responsável | Tasks que mudaram de responsável 3 vezes ou mais no período. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-08 | Taxas de reabertura, devolução e atraso | Que fração das concluídas do período foi reaberta, devolvida, entregue com atraso e entregue com atraso sem justificativa. Acima de 20% a tela marca ▲. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-09 | Onde ficaram mais tempo | Das tasks reabertas, devolvidas, canceladas e concluídas com atraso, quantas ficaram mais tempo em cada status; cada grupo abre as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| NE-10 | Negativas por responsável | Reabertas, devolvidas e entregues com atraso de cada pessoa da sua visão. Sem ranking: ordem alfabética. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/negativas.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `cruzamentos` — Cruzamentos

**Pergunta que responde.** Como as tasks abertas se distribuem entre dois eixos à sua escolha, sem pedir uma tela nova?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CZ-01 | Cruzamentos | Tabela cruzada das tasks abertas por dois eixos à sua escolha (tipo, status, squad, faixa de idade...), com a contagem ou a idade P50 em cada célula. Cada célula e cada total abrem as suas tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/cruzamentos.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `tempo` — Tempo em status

**Pergunta que responde.** Quanto tempo as tasks passam em cada status e quais abertas estão paradas além do normal.

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| TS-01 | Tasks analisadas | Quantas tasks concluídas no período entram nos cálculos desta tela. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-02 | Eficiência de fluxo | Que parte do tempo das tasks analisadas foi gasta em status ativos. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-03 | Paradas além do normal | Tasks abertas paradas no status atual além do normal para esse status. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-04 | Tempo por status, mediana / P85 | Mediana e P85 do tempo que as tasks concluídas passaram em cada status. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-05 | Onde o tempo é gasto | O peso relativo de cada status no tempo das tasks, para achar onde elas esperam. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-06 | Mediana por pessoa × status (dias) | Mediana de dias de cada pessoa em cada status, em ordem alfabética e sem ranking. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| TS-07 | Abertas paradas além do normal | Lista das tasks abertas paradas além do normal. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/tempo-em-status.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `esforco` — Esforço por task

**Pergunta que responde.** Quanto esforço cada task concluída custou, e como ele se divide entre as pessoas pela posse?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| EF-01 | Esforço por task fechada | O esforço de cada task concluída no período, em três componentes lado a lado (peso do tipo, testes e horas) e o índice composto. O cartão abre as tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| EF-02 | Esforço por pessoa | O esforço das tasks concluídas dividido entre as pessoas da sua visão pela posse (a fração do tempo com a task), e não pelo autor do worklog. Sem ranking. O cartão abre as tasks da pessoa. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/esforco.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Prazos

### `metas` — Metas e prazos

**Pergunta que responde.** As entregas saíram até o vencimento? E pela data original combinada?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

Aqui estão as duas métricas lado a lado: "No prazo" e "No prazo ajustado" (cartão MT-01). Veja "Como ler os números".

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| MT-01 | No prazo e no prazo ajustado | Das tasks concluídas com vencimento no período, quantas saíram até o vencimento (no prazo) e quantas contando também as com label que justifica o atraso (no prazo ajustado), lado a lado. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-02 | No prazo pela data original | Quantas tasks saíram até o PRIMEIRO vencimento combinado, antes de prazos serem remarcados. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-03 | Antecipadas | Tasks concluídas pelo menos 1 dia antes do vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-04 | Em D-1 ou D-2 | Tasks concluídas 1 ou 2 dias antes do vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-05 | No dia | Tasks concluídas exatamente no dia do vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-06 | Atrasadas | Tasks concluídas depois do vencimento. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-07 | Sem vencimento | Tasks concluídas no período que não têm vencimento e por isso ficam fora das taxas de prazo. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-08 | Entregas por período | Quantas tasks do universo vencem em cada período (dia, semana ou mês), separadas em no dia, antecipadas e atrasadas. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-09 | % no prazo por período | A taxa de no prazo em cada período, comparada com a meta. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-10 | Distribuição do dia da resolução | Quantas tasks saíram em cada faixa em relação ao vencimento: 3 ou mais dias antes, 2 dias antes, 1 dia antes, no dia, 1 a 3 dias depois e mais de 3 dias depois. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-11 | Prazo por tipo | Universo, no prazo e no prazo ajustado de cada tipo de task, com o estado frente à meta. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-12 | Prazo por prioridade | Universo, no prazo e no prazo ajustado de cada prioridade, com o estado frente à meta. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-13 | Prazo por squad | Universo, no prazo e no prazo ajustado de cada squad, com o estado frente à meta. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| MT-14 | Prazo por pessoa | Universo, no prazo e no prazo ajustado de cada pessoa da sua visão, com o estado frente à meta. Sem ranking: ordem alfabética. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/metas.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `vencimentos` — Vencimentos da semana

**Pergunta que responde.** O que vence nos próximos dias e quem corre o risco de atrasar?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| VE-01 | Vencem nos próximos dias | Quantas tasks abertas vencem nos próximos 7 ou 14 dias, com o término previsto e a folga real de cada uma. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| VE-02 | Risco de atrasar | Quantas das que vencem nos próximos dias devem terminar depois do vencimento ou não têm previsão de término. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/vencimentos.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `checklistReview` — Checklist × Review

**Pergunta que responde.** A review de cada checklist foi feita a tempo, por outra pessoa e com duração plausível?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CR-01 | Pares Checklist × Review | Os checklists que têm uma review ligada a eles pelo vínculo de bloqueio, um par por linha. O cartão abre os checklists dos pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-02 | Checklists sem review | Checklists que não têm nenhuma review ligada pelo vínculo de bloqueio. O cartão abre esses checklists. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-03 | Ciclo da review (mediana) | Quanto a review leva da primeira entrada em andamento até a resolução, na mediana. O cartão abre os pares com review concluída. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-04 | Review no ciclo do checklist | Que fração do ciclo do checklist a review ocupa, na mediana. O cartão abre os pares com review concluída. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-05 | Review criada depois do checklist | Quantas horas depois do checklist a review é criada, na mediana. O cartão abre os pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-06 | Review vence depois do checklist | Pares em que o vencimento da review é posterior ao do checklist: o prazo da review não cabe no do pai. O cartão abre esses pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-07 | Mesma pessoa nos dois | Pares em que o responsável do checklist e o da review é a mesma pessoa: não há revisão independente. O cartão abre esses pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-08 | Pares com alerta | Pares com ao menos um dos seis alertas (review vence depois, pai fechado antes, mesma pessoa, review curta com pai reaberto, review vencida, review atrasada). O cartão abre os pares e cada alerta abre os seus. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-10 | Aprendizados | Frases que o painel só escreve quando a amostra permite: duração da review × reabertura do pai, labels com mais reabertura e padrões dos pares. | Usuário, Squad Leader, Coordenador, Gerente | não |
| CR-11 | Duração da review × reabertura | Para cada faixa de horas ativas da review, quantos pares há e em quantos o checklist foi reaberto. Cada faixa abre os seus pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-12 | Atrasos em cadeia | Pares em que a review terminou depois do vencimento do checklist, empurrando o fechamento do pai. É uma contagem de pares, sem nomes. O cartão abre esses pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-13 | Labels e resultado | Para cada label, em quantos pares ela aparece, em quantos o checklist foi reaberto e o lift contra a média. Cada label abre os seus pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CR-14 | Diferenças de labels | Labels que existem só no checklist ou só na review, com o número de pares. Cada linha abre os pares. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/checklist-review.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Pessoas e planejamento

### `carga` — Carga por pessoa

**Pergunta que responde.** Quanto trabalho cada pessoa tem na fila e quanto cabe na capacidade das próximas 4 semanas?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CP-01 | Equilíbrio da carga (índice de Jain) | Quão parecida é a ocupação das pessoas da sua visão: 1,0 = todas iguais. É um índice do conjunto, nunca uma nota de pessoa. | Usuário, Squad Leader, Coordenador, Gerente | não |
| CP-02 | Acima da capacidade e com folga | Quantas pessoas estão acima da capacidade e quantas têm folga. O cartão abre as tasks na fila dessas pessoas. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CP-03 | Carga por pessoa | Para cada pessoa, em ordem alfabética: abertas, em andamento, fila em horas, capacidade de 4 semanas, ocupação, ausência, vencidas, acima do P85 e risco alto. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/carga.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `calendario` — Calendário do time

**Pergunta que responde.** Quem está ausente, quando há feriado e o que vence em cada dia?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CL-01 | Calendário do time | Feriados, ausências e vencimentos de cada dia, com a capacidade do time no dia. Cada dia abre as tasks que vencem nele. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/calendario.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `capacidade` — Capacidade

**Pergunta que responde.** Onde a capacidade não dá conta do trabalho e quais atrasos vêm das ausências?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CA-01 | Alertas de carga e ausência | Quantos alertas de semana com carga acima do limite ou com ausência e trabalho agendado. | Squad Leader, Coordenador, Gerente | não |
| CA-02 | Atrasos previstos | Quantas tasks devem terminar depois do vencimento, pela capacidade da pessoa. | Squad Leader, Coordenador, Gerente | sim |
| CA-03 | Atrasos por causa de ausência | Quantos dos atrasos previstos deixariam de existir se a pessoa não tivesse a ausência. | Squad Leader, Coordenador, Gerente | sim |
| CA-04 | Equilíbrio da capacidade (Jain) | Quão parecida é a ocupação das pessoas da sua visão: 1,0 = todas iguais. É um índice do conjunto, nunca uma nota de pessoa. | Squad Leader, Coordenador, Gerente | não |
| CA-05 | Mapa de capacidade | A ocupação de cada pessoa em 8 semanas; "aus." quando a ausência tira toda a capacidade da semana. | Squad Leader, Coordenador, Gerente | não |
| CA-06 | Alertas por pessoa | Cada alerta de carga alta ou de ausência com trabalho agendado, com as horas a replanejar. | Squad Leader, Coordenador, Gerente | não |
| CA-07 | Ausências planejadas | Quem está ausente nas próximas 8 semanas. O painel não guarda nem mostra o motivo. | Squad Leader, Coordenador, Gerente | não |
| CA-08 | Feriados no horizonte | Os feriados das próximas 8 semanas, que valem capacidade zero. | Squad Leader, Coordenador, Gerente | não |
| CA-09 | Lista de atrasos previstos | Cada task que deve atrasar, com vencimento, término previsto e a causa (ausência ou carga). | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/capacidade.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `plano` — Plano 4–8 semanas

**Pergunta que responde.** Quantas horas cada pessoa tem planejadas para as próximas semanas, contra a capacidade?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| PL-01 | Plano de 4 a 8 semanas | Horas planejadas contra a capacidade de cada pessoa em cada semana. Cada célula abre as tasks que ocupam a semana. | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/plano.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `cobertura` — Cobertura por ausência

**Pergunta que responde.** Que trabalho fica sem dono nas próximas ausências e quem do squad pode cobrir?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CB-01 | Ausências com trabalho a cobrir | Ausências próximas de pessoas da sua visão e o trabalho que fica sem cobertura: tasks afetadas, horas a replanejar, competências pedidas e quem pode cobrir. O cartão abre as tasks afetadas. | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/cobertura.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `simulador` — Simulador de ausência

**Pergunta que responde.** O que atrasaria se uma pessoa se ausentasse num período? Simulação só de leitura: nada é gravado.

**Quem vê.** Squad Leader, Coordenador, Gerente.

Simulação só de leitura: nada é gravado.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| SI-01 | Simulador de ausência | O que atrasaria se uma pessoa se ausentasse num período: as tasks que passam a atrasar e as horas a replanejar, com a ocupação antes e depois. Nada é gravado. O cartão abre as tasks que passam a atrasar. | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/simulador.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `competencias` — Competências

**Pergunta que responde.** Quem tem cada competência e quais dependem de uma pessoa só ou de ninguém?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CM-01 | Matriz de competências | Quem tem cada competência na sua visão e quais competências dependem de uma pessoa só. | Usuário, Squad Leader, Coordenador, Gerente | não |
| CM-02 | Competências pedidas sem ninguém | Tasks abertas que pedem uma competência (label skill:x) que ninguém da sua visão tem. O cartão abre as tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CM-03 | Demanda por competência | Tasks abertas e horas por competência pedida. O cartão abre as tasks da competência. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/competencias.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `revisoes` — Revisões pendentes

**Pergunta que responde.** Quantas reviews cada revisor tem pendentes, quantas estão vencidas e quantas passaram do prazo de revisão?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| RV-01 | Revisões pendentes por revisor | As reviews abertas de cada revisor da sua visão, quantas estão vencidas e quantas passaram do prazo de revisão. O cartão abre as tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| RV-02 | Reviews sem revisor | Reviews abertas sem responsável (só coordenação e gerente). O cartão abre as tasks. | Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/revisoes.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `posse` — Posse da task

**Pergunta que responde.** Quem ficou com cada task e por quanto tempo, e quantas vezes o responsável mudou?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| PS-01 | Posse por task | As tasks do período (resolvidas nele, mais as abertas) com o tempo que cada pessoa ficou com elas e quantas vezes o responsável mudou. O cartão abre as tasks. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| PS-02 | Posse por pessoa | Em quantas tasks cada pessoa da sua visão teve posse e por quanto tempo. Sem ranking: pessoas em ordem alfabética. O cartão abre as tasks da pessoa. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/posse.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `cargaTipo` — Carga por tipo/OS

**Pergunta que responde.** Quanto trabalho aberto e fechado há por tipo de task e versão do SO?

**Quem vê.** Usuário, Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CG-01 | Abertas por tipo e OS | Tasks abertas e horas de fila por tipo de task e versão do SO. O cartão abre as tasks da célula. | Usuário, Squad Leader, Coordenador, Gerente | sim |
| CG-02 | Fechadas por tipo e OS | Tasks concluídas no período e o esforço delas por tipo de task e versão do SO. O cartão abre as tasks da célula. | Usuário, Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/carga-tipo.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `tendencia` — Tendência de ocupação

**Pergunta que responde.** A sobrecarga de cada pessoa é recorrente ou foi pontual nas últimas 12 semanas?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| TD-01 | Tendência de ocupação | A ocupação de cada pessoa nas últimas 12 semanas e se a sobrecarga é recorrente ou pontual. | Squad Leader, Coordenador, Gerente | não |

**De onde vêm os números.** Ficha `app/config/cards/tendencia.card.yaml` (fórmula, exemplo e aceite).


### `gargalos` — Gargalos

**Pergunta que responde.** Onde o trabalho se acumula e quem está acima da capacidade?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| GA-01 | Fluxo cumulativo de 60 dias | Quantas tasks estavam em cada status no fim de cada um dos últimos 60 dias, na ordem do fluxo. | Squad Leader, Coordenador, Gerente | não |
| GA-02 | Estado das etapas | Quantas tasks há em cada etapa hoje e onde há gargalo. O cartão abre as tasks da etapa. | Squad Leader, Coordenador, Gerente | sim |
| GA-03 | Pessoas acima da capacidade | Quantas pessoas estão acima da capacidade nas próximas 4 semanas. O cartão abre as tasks na fila delas. | Squad Leader, Coordenador, Gerente | sim |
| GA-04 | Pessoas com folga | Quantas pessoas têm folga nas próximas 4 semanas. O cartão abre as tasks na fila delas. | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/gargalos.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Ação

### `distribuicao` — Distribuição

**Pergunta que responde.** Quem deveria pegar cada task que está esperando dono?

**Quem vê.** Squad Leader, Coordenador, Gerente.

As sugestões são de leitura: a decisão é humana. Quem pode alterar responsável, vencimento e labels de atraso é definido pela regra de auditoria (ver as perguntas frequentes).

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| DI-01 | Tasks aguardando dono | As tasks "a fazer" que estão sem responsável ou com o gerente e esperam alguém para executá-las. O cartão abre essas tasks. | Squad Leader, Coordenador, Gerente | sim |
| DI-02 | Sugestões sem decisão | As pendentes para as quais o painel sugere uma pessoa e que ninguém aprovou, trocou ou rejeitou hoje. O cartão abre essas tasks. | Squad Leader, Coordenador, Gerente | sim |
| DI-03 | Equilíbrio antes e depois | O índice de Jain da ocupação das pessoas elegíveis antes e depois de aplicar todas as sugestões (1,0 = todos iguais). É um índice do conjunto, nunca uma nota de pessoa. | Squad Leader, Coordenador, Gerente | não |
| DI-04 | Por que cada pessoa | Os fatores do score de cada candidato para uma task, em ordem alfabética e sem posição: carga, WIP, competências que faltam e experiência no épico. | Squad Leader, Coordenador, Gerente | não |

**De onde vêm os números.** Ficha `app/config/cards/distribuicao.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `rebalanceamento` — Rebalanceamento

**Pergunta que responde.** O que dá para mover de quem está acima da capacidade?

**Quem vê.** Squad Leader, Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| RB-01 | Movimentos sugeridos | As tasks ainda não iniciadas que o painel sugere tirar de quem passou do gatilho de ocupação, cada uma com o destino sugerido. O cartão abre essas tasks. | Squad Leader, Coordenador, Gerente | sim |
| RB-02 | Movimentos sem decisão | Os movimentos sugeridos que ninguém aprovou, trocou ou rejeitou hoje. O cartão abre essas tasks. | Squad Leader, Coordenador, Gerente | sim |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Auditoria

### `auditoria` — Alterações de data, dono e label

**Pergunta que responde.** Alterações de vencimento, responsável e labels de atraso feitas por quem talvez não pudesse, para conferir. Só coordenação e gerente.

**Quem vê.** Coordenador, Gerente.

Mostra alterações de vencimento, responsável e labels de atraso para conferir. Indício, não prova: a tela aponta o que merece conversa, nunca uma conclusão sobre pessoas. Fica atrás da Trava do DPO: com a trava fechada a tela fica desligada e nada é consultado, e o histórico campo a campo só abre com ela aberta (ver "Trava do DPO"). Além dos sinais de data, dono e label, há dois sinais: estimativa reduzida depois do início (`ESTIMATIVA_REDUZIDA`) e horas registradas com atraso (`WORKLOG_RETROATIVO`). Esses sinais dependem da recarga do Jira: até a nova carga (que só acontece com a flag ligada) aparecem avisos como `HISTORICO_DE_ESTIMATIVA_INCOMPLETO` e `SINAL_SEM_DADO`.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| AU-01 | Sinais para conferir | Quantas alterações de vencimento, responsável e labels de atraso merecem conferência no período. É indício, não prova. | Coordenador, Gerente | sim |
| AU-02 | Sinais por tipo | A contagem de sinais de cada tipo (vencimento, troca de responsável, passa-bola, labels de atraso, cancelamento), na ordem do catálogo. Não há contagem por pessoa. | Coordenador, Gerente | sim |
| AU-03 | No prazo e no prazo ajustado | Das tasks concluídas com vencimento no período, quantas saíram até o vencimento (no prazo) e quantas contando também as que têm label que justifica o atraso (no prazo ajustado). | Coordenador, Gerente | sim |

**De onde vêm os números.** Ficha `app/config/cards/auditoria.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `divergencia` — Divergência Task × Review

**Pergunta que responde.** Tasks e Reviews ligadas pelo link Blocks cujos vencimentos são diferentes. Só mostra e leva ao Jira; não corrige nada.

**Quem vê.** Coordenador, Gerente.

Só mostra e leva ao Jira; não corrige nada.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| DV-01 | Divergência Task × Review | Pares de task e Review com vencimentos diferentes. | Coordenador, Gerente | sim |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `integridade` — Integridade

**Pergunta que responde.** Há sinais de que prazo ou fluxo foram ajustados de forma suspeita? Cada sinal é um motivo para conversar e conferir no Jira.

**Quem vê.** Gerente.

Só o gerente vê. Fica atrás da Trava do DPO: sem o aceite do DPO a tela não consulta dado algum (mostra "Esta tela fica desligada até o encarregado de dados (DPO) aprovar o uso"). Cada sinal é indício, não prova. Toda consulta é registrada no Registro de acesso. Por pessoa, a tela calcula pontos, taxa (pontos por concluída), o múltiplo da taxa em relação à mediana do time e um nível de indício (alto ou médio), em ordem alfabética e sem ranking; o catálogo de sinais (IN-08) inclui `ESTIMATIVA_AUMENTADA`, que também depende da recarga do Jira (até lá aparece o aviso `SINAL_SEM_DADO`). Sem o aceite do DPO, só funciona com dados de demonstração.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| IN-01 | Evidências de integridade | Indícios de possível manipulação de prazo ou de fluxo encontrados no período, um por sinal e task. Indício, não prova. O cartão abre as tasks. | Gerente | sim |
| IN-02 | Pessoas com indício alto ou médio | Quantas pessoas estão em cada nível de indício e as evidências delas; o cartão abre as evidências dessas pessoas. Em ordem alfabética, sem ranking. Nível é motivo de conversa, não nota. | Gerente | sim |
| IN-03 | Tasks afetadas | Quantas tasks têm ao menos uma evidência no recorte. O cartão abre essas tasks. | Gerente | sim |
| IN-04 | Mediana da taxa do time | A taxa mediana de pontos por task concluída no time; é a base do múltiplo de cada pessoa. Não há tasks por trás. | Gerente | não |
| IN-05 | Evidências por sinal | Quantas evidências cada um dos 12 sinais teve no recorte, na ordem do catálogo. Cada sinal abre as suas tasks. | Gerente | sim |
| IN-06 | Impacto nas metas | Quantos pontos percentuais o "no prazo" atual está acima do "no prazo pela data original" do mesmo período; indício de que prorrogações melhoram a meta. Não há tasks por trás. | Gerente | não |
| IN-07 | Tabela de evidências | Cada evidência com a task, o sinal, a gravidade, a pessoa do sinal e o dia. O cartão abre as tasks. | Gerente | sim |
| IN-08 | Catálogo de sinais | O que cada sinal procura, a gravidade e o limite configurado, sem nenhum dado de pessoas. Não há tasks por trás. | Gerente | não |

**De onde vêm os números.** Ficha `app/config/cards/integridade.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


## Governança

### `mapaCartoes` — Mapa de cartões

**Pergunta que responde.** Todos os cartões do painel que o seu papel vê: o que mostram, como são calculados e de onde vem o dado.

**Quem vê.** Coordenador, Gerente.

É gerado do catálogo de cartões do backend (a mesma lista que este guia segue): para cada cartão, o que mostra, como calcula, a fonte do dado e quem vê. Se este guia e o Mapa divergirem, vale o Mapa.

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.


### `qualidade` — Qualidade dos dados

**Pergunta que responde.** O que está faltando ou desencontrado nos dados que chegam do Jira?

**Quem vê.** Coordenador, Gerente.

Reúne o que falta ou está desencontrado nos dados. Os cartões com aviso de dado incompleto levam até aqui.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| QD-01 | Abertas com dado faltando | Quantas tasks abertas estão sem estimativa, responsável, vencimento ou épico, em andamento sem horas, com responsável fora do papeis.yaml ou ainda com a prioridade padrão. Cada linha abre as tasks. | Coordenador, Gerente | sim |
| QD-02 | Conclusão divergente | As tasks concluídas ou resolvidas nos últimos 90 dias em que o status concluído e a resolution não concordam. Cada linha abre as tasks. | Coordenador, Gerente | sim |
| QD-03 | Vocabulário do Jira | Status que não estão no status_map.yaml e labels recentes que não estão em labels_atraso.yaml nem na lista de ignoradas. O gestor corrige nos YAML; o painel só lê. | Coordenador, Gerente | sim |
| QD-04 | Bloqueadas sem causa | As tasks em status bloqueado ou com a flag de impedimento que não têm nenhum vínculo aberto que explique. O cartão abre essas tasks. | Coordenador, Gerente | sim |
| QD-05 | Campos sem valor | Os campos do Jira ainda a confirmar (Story Points, Epic Link, Flagged, OS Version) em que nenhuma issue sincronizada tem valor. É uma lista de nomes, sem tasks. | Coordenador, Gerente | não |

**De onde vêm os números.** Ficha `app/config/cards/qualidade.card.yaml` (fórmula, exemplo e aceite).

**Detalhamento.** Onde a coluna "Abre tasks" diz sim, clique no número para abrir a gaveta de tasks; ela tem o mesmo total do cartão.


### `decisoes` — Decisões de distribuição

**Pergunta que responde.** O que foi decidido sobre as sugestões de distribuição e de rebalanceamento?

**Quem vê.** Coordenador, Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| DC-01 | Decisões registradas | As decisões de aprovar, trocar ou rejeitar sugestões, da mais recente à mais antiga, com quem decidiu e o motivo. Não há contagem nem ordem por pessoa. | Coordenador, Gerente | não |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.


### `registroAcesso` — Registro de acesso

**Pergunta que responde.** Quem consultou o quê no painel, e quando?

**Quem vê.** Gerente.

Só o gerente vê. Lista quem consultou o quê no painel e quando; é o registro que torna auditáveis as telas sensíveis.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| RA-01 | Registro de acesso | Quem consultou qual rota do painel e quando, com o resultado (200, 400, 403) e só os nomes dos filtros usados. Não mostra chave de task, segmento nem valor de filtro. | Gerente | não |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.


### `sincronizacao` — Sincronização

**Pergunta que responde.** Estado da sincronização com o Jira. Somente leitura.

**Quem vê.** Gerente.

Com a flag do Jira desligada (o estado de hoje) a tela mostra "Desligado pela flag" e nada fala com o Jira. A sincronização periódica e a reconciliação diária só rodam depois que o usuário ligar a flag.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| SY-01 | Modo do sincronizador | Se o painel está sincronizando com o Jira ou está desligado pela flag. | Gerente | não |
| SY-02 | Últimas execuções | As últimas execuções do sincronizador em cada instância, com contagens e resultado. | Gerente | não |
| SY-03 | Retenção | A última limpeza local de dados antigos. | Gerente | não |

**De onde vêm os números.** Ficha `app/config/cards/sincronizacao.card.yaml` (fórmula, exemplo e aceite).


### `configuracao` — Configuração

**Pergunta que responde.** Com que regras e em que modo o painel está rodando?

**Quem vê.** Gerente.

**Cartões.**

| Id | Cartão | O que mostra | Quem vê | Abre tasks |
|---|---|---|---|---|
| CF-01 | Configuração | O modo do ambiente, o modo da sincronização e, de cada arquivo de regras, a versão, o hash e se vale o arquivo real ou o modelo. Só leitura. | Gerente | não |

**De onde vêm os números.** Esta tela não tem ficha própria; os números vêm dos cartões citados acima e do Mapa de cartões.

