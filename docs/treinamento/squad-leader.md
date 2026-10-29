# Treinamento: Squad Leader

Material para quem lidera um squad e acompanha a semana da equipe. Roteiro da sessão (agenda, pré-requisitos e fechamento): [`roteiro-da-sessao.md`](roteiro-da-sessao.md). O que vale para todos (Meu painel, Abertas, Metas e prazos) está no [material do usuário](usuario.md); aqui estão as telas de liderança. Guia completo: [`guia-do-painel.md`](../manual/guia-do-painel.md).

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`). A sessão usa os **dados de demonstração** fictícios; os números não são os do time. O mesmo roteiro serve depois que o usuário ligar a flag e a primeira sincronização rodar.

## Objetivo da sessão

Duração: 30 a 45 min. Ao fim, o Squad Leader sabe ler a semana do seu squad em **Meu squad**, ver o que vence e quem corre risco de atrasar, enxergar capacidade e gargalos, testar o efeito de uma ausência no simulador e usar as sugestões de distribuição e de rebalanceamento sem confundir sugestão com decisão. Também sabe o que o painel **não** faz: não ranqueia pessoas, não altera nada no Jira e não dá ao Squad Leader a tela de Auditoria.

## Antes de começar

- Acesso ao painel e papel de **Squad Leader** em `papeis.yaml`, com o squad vigente. Na demonstração (modo de desenvolvimento, sem login, faixa vermelha no topo), escolha **Squad Leader** em **Papel (simulação)**, no menu. **Atenção:** na demonstração o seletor só mostra o **menu** daquele papel. No servidor, o modo de desenvolvimento enxerga todos os dados (como gerente), então o escopo de dados por papel **só vale em produção, com login**.
- Em produção, sua visão é a do seu squad (o próprio e a equipe). Na tela Posse, quem está fora dela aparece como "outra pessoa".
- **Meu squad** é um item só do Squad Leader e depende do squad vigente do usuário logado; na demonstração, abra-o pelo endereço com `?squad=` e o nome de um squad da demonstração, ou use Carga por pessoa e Capacidade, que mostram o time todo.
- Coordenação e gerente veem todos os squads; por isso alguns números que eles citam não batem com os seus.

## Roteiro por tela

### Tela `squad` — Meu squad

**Pergunta.** Como está o meu squad esta semana?

**Onde clicar.** Os indicadores do alto (vencem em 7 dias, risco de atrasar, risco alto e médio) e a tabela de carga por pessoa. Cada número abre as tasks do cartão de origem com o mesmo filtro de squad.

**Como ler o número.** A carga por pessoa vem em ordem alfabética e sem nota; a ocupação é a das próximas 4 semanas. Verde até 85%, laranja até 110%, vermelho acima, sempre com símbolo e texto. As ausências aparecem sem motivo.

**Armadilha comum.** Ler a ocupação como avaliação de desempenho. É um indicador de planejamento do conjunto.

### Tela `vencimentos` — Vencimentos da semana

**Pergunta.** O que vence nos próximos dias e quem corre o risco de atrasar?

**Onde clicar.** VE-01 (Vencem nos próximos dias) e VE-02 (Risco de atrasar) abrem as tasks.

**Como ler o número.** O término previsto sai da capacidade do responsável, com ausências e feriados; folga = dias úteis entre o término e o vencimento. Risco de atrasar é folga negativa ou ausência de término previsto.

**Armadilha comum.** Tasks já vencidas ficam fora desta tela (estão em Negligenciadas, NG-03).

### Tela `carga` — Carga por pessoa

**Pergunta.** Quanto trabalho cada pessoa tem na fila e quanto cabe na capacidade das próximas 4 semanas?

**Onde clicar.** CP-02 (Acima da capacidade e com folga) abre as tasks na fila dessas pessoas; CP-03 (Carga por pessoa) traz abertas, fila em horas, ocupação, ausência, vencidas, acima do P85 e risco alto.

**Como ler o número.** Fila = horas restantes do Jira (senão estimativa menos registrado; senão horas padrão por tipo, que são assumidas). Capacidade zero aparece como **sem capacidade**. CP-01 é um índice do conjunto (1,0 = todos iguais), nunca uma nota de pessoa.

**Armadilha comum.** Esquecer que as horas padrão por tipo são suposição: onde o Jira não tem estimativa, a ocupação é aproximada.

### Tela `capacidade` — Capacidade

**Pergunta.** Onde a capacidade não dá conta do trabalho e quais atrasos vêm das ausências?

**Onde clicar.** CA-05 (Mapa de capacidade, 8 semanas), CA-02 (Atrasos previstos), CA-03 (Atrasos por causa de ausência) e CA-09 (Lista de atrasos previstos, com a causa de cada um).

**Como ler o número.** CA-05 mostra "aus." quando a ausência tira toda a capacidade da semana, e **sem capacidade** nunca vira porcentagem. CA-03 conta quantos atrasos previstos deixariam de existir sem a ausência.

**Armadilha comum.** Tratar o atraso previsto como atraso real. É uma previsão pela capacidade, útil para replanejar a tempo.

### Tela `gargalos` — Gargalos

**Pergunta.** Onde o trabalho se acumula e quem está acima da capacidade?

**Onde clicar.** GA-02 (Estado das etapas) abre as tasks da etapa; GA-03 e GA-04 abrem as tasks na fila de quem está acima da capacidade e de quem tem folga.

**Como ler o número.** Gargalo é uma etapa ativa ou de espera com mais tasks do que o limite de WIP do planejamento, ou cuja faixa cresceu em 15 dias pelo menos o maior valor entre 3 tasks e 30%. A etapa "a fazer" nunca é gargalo.

**Armadilha comum.** Procurar o "culpado" do gargalo. A tela mostra onde o trabalho acumula, não de quem é a culpa.

### Tela `simulador` — Simulador de ausência

**Pergunta.** O que atrasaria se uma pessoa se ausentasse num período?

**Onde clicar.** Preencha a pessoa, o período e a fração do dia (meio dia ou dia inteiro, até 60 dias) e rode a simulação.

**Como ler o número.** O simulador compara a agenda com e sem a ausência hipotética. "Atraso causado" é a task que não atrasava e passa a atrasar; as horas a replanejar aparecem com a ocupação antes e depois.

**Armadilha comum.** Esperar que a simulação registre a ausência. É só leitura: nada é gravado e nenhuma ausência é cadastrada (ausências vêm do YAML versionado).

### Tela `distribuicao` — Distribuição

**Pergunta.** Quem deveria pegar cada task que está esperando dono?

**Onde clicar.** DI-01 (Tasks aguardando dono) e DI-02 (Sugestões sem decisão) abrem as tasks. Em cada task há as ações **Aprovar**, **Trocar** e **Rejeitar**; a troca exige um motivo, e a tela avisa se você não puder decidir sobre aquela task ou pessoa. DI-04 mostra por que cada pessoa foi sugerida.

**Como ler o número.** O score combina carga projetada, WIP, competências que faltam e experiência no épico; o menor vence. Os fatores aparecem em ordem alfabética, sem posição. DI-03 mostra o equilíbrio antes e depois de aplicar as sugestões.

**Armadilha comum.** Achar que aprovar muda o responsável no Jira. Não muda: o painel está em **modo sombra**, só sugere e guarda a decisão. A troca de responsável acontece no Jira, por quem pode (Reporter, coordenação, gerente ou o Squad Leader do responsável).

### Tela `rebalanceamento` — Rebalanceamento

**Pergunta.** O que dá para mover de quem está acima da capacidade?

**Onde clicar.** RB-01 (Movimentos sugeridos) e RB-02 (Movimentos sem decisão) abrem as tasks.

**Como ler o número.** Só entram tasks ainda não iniciadas, tiradas de quem passou do gatilho de ocupação em 4 semanas. O destino precisa ter a competência e ficar pelo menos 20 pontos percentuais abaixo da origem depois do movimento, em até 10 movimentos.

**Armadilha comum.** Mover task já iniciada: o painel nunca sugere isso.

### Tela `meusDados` — Meus dados

**Pergunta.** O que o painel guarda sobre mim, para que serve e por quanto tempo?

**Onde clicar.** Abra **Meus dados** no menu (todos os papéis têm a tela). "Baixar meus dados (JSON)" gera, no navegador, um arquivo com o mesmo conteúdo da tela.

**Como ler o número.** A tela mostra só o seu registro: quantidades de tasks, alterações e registros de horas, ausências planejadas (sem o motivo), os seus papéis, as categorias tratadas com finalidade e prazo de retenção (24 meses por padrão, a confirmar com o DPO) e os seus acessos dos últimos 90 dias (a lista mostra no máximo 200), além da contagem de decisões de distribuição em que você aparece. Na demonstração o usuário de desenvolvimento ("dev") aparece com as contagens zeradas, como no Meu painel.

**Armadilha comum.** Esperar ver os dados de outra pessoa (a tela não aceita pedir) ou o conteúdo de sinais de Auditoria sobre você (quem quiser esse levantamento pede ao DPO). Para corrigir um dado, ajuste a task no Jira; para pedir eliminação, use o canal do DPO.

## Outras telas que o seu papel vê

`plano`, `cobertura`, `tendencia` (liderança) e todas as do material do usuário. Cada uma está descrita, com os seus cartões, no [guia do painel](../manual/guia-do-painel.md).

## Exercício com dados de demonstração

Com o papel **Squad Leader** escolhido (só o menu muda) e os dados de demonstração carregados:

1. Em **Carga por pessoa**, ache a pessoa com maior ocupação nas 4 semanas e clique no número das abertas dela. Quantas tasks vencidas há na gaveta? (Na demonstração a tela mostra o time todo.)
2. Em **Capacidade**, ache uma semana **sem capacidade** e diga a causa (ausência ou feriado).
3. Em **Simulador de ausência**, simule 5 dias úteis de ausência de uma pessoa do squad. Quais tasks passam a atrasar?
4. Em **Distribuição**, abra uma sugestão e leia DI-04. Qual fator pesou mais? Rejeite a sugestão com um motivo e confirme que a tela diz que nada mudou no Jira.
5. Em **Rebalanceamento**, confira por que só aparecem tasks não iniciadas.

Gabarito: (1) o total da gaveta é o das abertas na tabela; (2) a ausência ou o feriado cadastrado zera a capacidade; (3) as que o simulador marca como "atraso causado"; (4) o fator depende da task; o modo sombra só guarda a decisão; (5) a regra do rebalanceamento só move o que ainda não começou.

## Para saber mais

- Perguntas frequentes: [`perguntas-frequentes.md`](../manual/perguntas-frequentes.md) (inglês: [`faq.md`](../manual/faq.md)).
- Guia do painel: [`guia-do-painel.md`](../manual/guia-do-painel.md) (inglês: [`panel-guide.md`](../manual/panel-guide.md)).
