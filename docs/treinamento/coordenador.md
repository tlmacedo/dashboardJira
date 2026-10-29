# Treinamento: Coordenação

Material para a coordenação do TG-Basic, que acompanha todos os squads. Roteiro da sessão (agenda, pré-requisitos e fechamento): [`roteiro-da-sessao.md`](roteiro-da-sessao.md). Telas que a coordenação divide com todos (Meu painel, Abertas, Negligenciadas, Vencimentos, Carga) estão no [material do usuário](usuario.md) e as de liderança (Capacidade, Gargalos, Distribuição, Rebalanceamento) no [material do Squad Leader](squad-leader.md); aqui estão as telas que são da coordenação. Guia completo: [`guia-do-painel.md`](../manual/guia-do-painel.md).

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`). A sessão usa os **dados de demonstração** fictícios; os números não são os do time. O mesmo roteiro serve depois que o usuário ligar a flag e a primeira sincronização rodar.

## Objetivo da sessão

Duração: 30 a 45 min. Ao fim, a coordenação sabe começar pela **Central de atenção**, ler o ritmo de entregas e o prazo ("No prazo" e "No prazo ajustado"), usar a **Auditoria** com a regra "Indício, não prova", conferir a **Divergência Task × Review**, cuidar da **Qualidade dos dados** e achar no **Mapa de cartões** de onde vem cada número. Também sabe o que o painel **não** faz: não ranqueia pessoas e não altera nada no Jira.

## Antes de começar

- Acesso ao painel e papel de **Coordenador** em `papeis.yaml`. Na demonstração (modo de desenvolvimento, sem login, faixa vermelha no topo), escolha **Coordenador** em **Papel (simulação)**, no menu. Na demonstração o seletor só muda o **menu**; o servidor mostra todos os dados, e o escopo por papel só vale em produção, com login.
- A coordenação vê todos os squads. Cada squad se escolhe pelos filtros.
- Regra de ouro das telas de conferência: o painel mostra o que merece conversa, nunca uma conclusão sobre uma pessoa.

## Roteiro por tela

### Tela `central` — Central de atenção

**Pergunta.** O que pede atenção agora, e onde está?

**Onde clicar.** O cartão VG-09 (Central de atenção) reúne os indicadores que pedem atenção: vencidas, risco, capacidade, atrasos previstos, sem atualização, paradas, sem responsável e bloqueadas. Cada número abre a gaveta com o mesmo total do card de destino. VG-10 (Entregas por semana) abre as tasks entregues em cada semana. VG-14 (Itens da Auditoria na Central) destaca alterações sem autorização e divergências Task × Review, só com a Trava do DPO aberta; quando algum item fica de fora por causa da trava, a Central mostra o aviso "Alguns itens ficam fora desta lista até o encarregado de dados (DPO) aprovar o uso das telas sensíveis que você tem permissão de ver", sem dizer qual módulo.

**Como ler o número.** VG-11 (Quando o backlog termina) é uma previsão por simulação sobre as semanas fechadas, em três níveis de confiança (P50, P85 e P95); com poucas semanas de dado a confiança é baixa. VG-12 (Ocupação por squad) é um indicador do conjunto: verde até 85%, laranja até 110%, vermelho acima.

**Armadilha comum.** Somar os indicadores do VG-09: a mesma task pode estar em mais de um (sem atualização e paradas no status, por exemplo).

### Tela `metas` — Metas e prazos

**Pergunta.** As entregas saíram até o vencimento? E pela data original combinada?

**Onde clicar.** MT-01 (**No prazo** e **No prazo ajustado**, lado a lado), MT-02 (pela data original) e as quebras por tipo (MT-11), prioridade (MT-12), squad (MT-13) e pessoa (MT-14, em ordem alfabética).

**Como ler o número.** O universo são as concluídas (categoria concluído e resolução preenchida), fora de Epic, com vencimento no período. "No prazo" usa o dia da resolução em Manaus. "No prazo ajustado" soma as que têm label de atraso justificado. A tela avisa quando a taxa pela data original fica 5 pontos percentuais ou mais abaixo da taxa pela data atual: sinal de prazos remarcados.

**Armadilha comum.** Comparar squads só pela taxa. Veja o universo (n): taxa alta com poucas tasks é amostra pequena.

### Tela `fechadas` — Fechadas

**Pergunta.** O que foi entregue no período, em quanto tempo e dentro do prazo?

**Onde clicar.** FE-05 repete o prazo (No prazo e No prazo ajustado). Os cartões de ciclo (FE-03, FE-04) e o Controle do ciclo (CT-01, CT-02) mostram P50 e P85.

**Como ler o número.** Ciclo vai da primeira entrada em andamento, espera ou bloqueio até a resolução, em **Dias úteis** (ou **Dias corridos**). Checklist e Review não se misturam.

**Armadilha comum.** Ler a média como se fosse típica. O P85 diz o prazo que 85% das entregas cumpriram.

### Tela `auditoria` — Alterações de data, dono e label

**Pergunta.** Houve alterações de vencimento, responsável e labels de atraso por quem talvez não pudesse?

**Onde clicar.** AU-01 (Sinais para conferir) abre as tasks; AU-02 (Sinais por tipo) separa por tipo de sinal; AU-03 repete No prazo e No prazo ajustado. Abrir uma task mostra o histórico campo a campo.

**Como ler o número.** Cada alteração é comparada com quem podia fazê-la na data da alteração: o Reporter da task (o PL), a coordenação, o gerente ou o Squad Leader do responsável, com os papéis vigentes naquele dia. Quem não podia gera um sinal.

**Armadilha comum.** Tratar o sinal como prova. **Indício, não prova**: pode haver razão legítima. Converse e confira a task antes de qualquer conclusão. Não há contagem nem ordem por pessoa. Esta tela fica atrás da **Trava do DPO**: fechada por padrão, ela mostra "Esta tela fica desligada até o encarregado de dados (DPO) aprovar o uso" e nada é consultado (o servidor responde `AGUARDANDO_ACEITE_DPO`). Ela só abre com o aceite do DPO, ligado pelo usuário, ou na demonstração. O histórico campo a campo da gaveta também só abre com a trava aberta. Além dos sinais de data, dono e label, há `ESTIMATIVA_REDUZIDA` (estimativa reduzida depois do início) e `WORKLOG_RETROATIVO` (horas registradas com atraso).

### Tela `divergencia` — Divergência Task × Review

**Pergunta.** Quais Tasks e Reviews ligadas têm vencimentos diferentes?

**Onde clicar.** DV-01: a lista de pares divergentes, com o percentual de divergência.

**Como ler o número.** Um par é uma Task e uma Review ligadas pelo link Blocks / is blocked by. Divergente é ter vencimentos diferentes, ou só um deles preenchido.

**Armadilha comum.** Esperar que a tela corrija. Ela só mostra e leva ao Jira; a correção é feita lá.

### Tela `qualidade` — Qualidade dos dados

**Pergunta.** O que está faltando ou desencontrado nos dados que chegam do Jira?

**Onde clicar.** QD-01 (abertas com dado faltando), QD-02 (conclusão divergente), QD-03 (vocabulário do Jira), QD-04 (bloqueadas sem causa), QD-05 (campos sem valor).

**Como ler o número.** QD-01 usa verde até 10%, laranja até 30%, vermelho acima. QD-02 lista tasks em que o status concluído e a resolução não concordam: uma task só vale como concluída com as duas coisas, e a divergência vem para esta tela.

**Armadilha comum.** Corrigir no painel. O painel só lê: status e labels novos entram nos arquivos de regras (`status_map.yaml`, `labels_atraso.yaml`) e o resto se corrige no Jira.

### Tela `decisoes` — Decisões de distribuição

**Pergunta.** O que foi decidido sobre as sugestões de distribuição e de rebalanceamento?

**Onde clicar.** DC-01: a tabela de decisões, da mais recente à mais antiga, com quem decidiu e o motivo.

**Como ler o número.** Lê só a tabela de decisões do próprio painel; nada vem do Jira nem vai para ele. Não há contagem nem ordem por pessoa.

**Armadilha comum.** Ler decisão como mudança de responsável: o painel está em modo sombra.

### Tela `mapaCartoes` — Mapa de cartões

**Pergunta.** Quais cartões existem, o que mostram e como são calculados?

**Onde clicar.** Filtre por grupo e por natureza, busque por título e leia, em cada linha, "O que mostra", "Como é calculado", a fonte do dado, a tela e quem vê.

**Como ler o número.** O Mapa é gerado do catálogo de cartões do backend: é a fonte da verdade quando um texto de guia divergir.

**Armadilha comum.** Procurar aqui os números: o Mapa descreve cartões, não traz dados.

### Tela `meusDados` — Meus dados

**Pergunta.** O que o painel guarda sobre mim, para que serve e por quanto tempo?

**Onde clicar.** Abra **Meus dados** no menu (todos os papéis têm a tela). "Baixar meus dados (JSON)" gera, no navegador, um arquivo com o mesmo conteúdo da tela.

**Como ler o número.** A tela mostra só o seu registro: quantidades de tasks, alterações e registros de horas, ausências planejadas (sem o motivo), os seus papéis, as categorias tratadas com finalidade e prazo de retenção (24 meses por padrão, a confirmar com o DPO) e os seus acessos dos últimos 90 dias.

**Armadilha comum.** Esperar ver os dados de outra pessoa (a tela não aceita pedir) ou o conteúdo de sinais de Auditoria sobre você (quem quiser esse levantamento pede ao DPO). Para corrigir um dado, ajuste a task no Jira; para pedir eliminação, use o canal do DPO.

## Outras telas que o seu papel vê

`riscos`, `previsao`, `fluxoDaSemana`, `agingWip`, `bloqueios`, `negativas`, `cruzamentos`, `tempo`, `esforco`, `vencimentos`, `checklistReview`, `calendario`, `capacidade`, `plano`, `cobertura`, `simulador`, `tendencia`, `gargalos`, `distribuicao` e `rebalanceamento`. Cada uma está descrita, com os seus cartões, no [guia do painel](../manual/guia-do-painel.md).

## Exercício com dados de demonstração

Com o papel **Coordenador** escolhido e os dados de demonstração carregados:

1. Na **Central de atenção**, clique no número de vencidas e confira que o total da gaveta é o do cartão.
2. Em **Metas e prazos**, anote **No prazo** e **No prazo ajustado** de um squad e diga o que a diferença representa.
3. Na **Auditoria**, abra um sinal e leia o histórico campo a campo. Quem alterou, quando e por que o sinal foi gerado?
4. Em **Divergência Task × Review**, ache um par divergente e diga qual vencimento está diferente.
5. Em **Mapa de cartões**, ache o cartão "No prazo e no prazo ajustado" e leia "Como é calculado".

Gabarito: (1) igual; (2) o atraso justificado por label; (3) o autor não estava entre os autorizados na data, e isso é indício, não prova; (4) o da Task ou o da Review, ou só um preenchido; (5) a regra é a mesma da Auditoria, de Fechadas e de Metas.

## Para saber mais

- Perguntas frequentes: [`perguntas-frequentes.md`](../manual/perguntas-frequentes.md) (inglês: [`faq.md`](../manual/faq.md)).
- Guia do painel: [`guia-do-painel.md`](../manual/guia-do-painel.md) (inglês: [`panel-guide.md`](../manual/panel-guide.md)).
