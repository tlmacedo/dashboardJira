# Treinamento: Usuário

Material para quem usa o painel para ver o próprio trabalho. Roteiro da sessão (agenda, pré-requisitos e fechamento): [`roteiro-da-sessao.md`](roteiro-da-sessao.md). Guia completo: [`guia-do-painel.md`](../manual/guia-do-painel.md).

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`). Toda a sessão usa os **dados de demonstração** fictícios; os números não são os do time. O mesmo roteiro serve depois que o usuário ligar a flag e a primeira sincronização rodar.

## Objetivo da sessão

Duração: 30 a 45 min. Ao fim, a pessoa sabe abrir o **Meu painel**, achar o que está apertado, abrir o número até as tasks que o compõem e ler "No prazo" e "No prazo ajustado" sem confundir as duas. Também sabe o que o painel **não** faz: não mostra a task de outras pessoas, não faz ranking e não altera nada no Jira.

## Antes de começar

- Acesso ao painel e papel de **Usuário** em `papeis.yaml`. Na demonstração (modo de desenvolvimento, sem login, faixa vermelha no topo), escolha **Usuário** em **Papel (simulação)**, no menu. **Atenção:** na demonstração o seletor só mostra o **menu** daquele papel. No servidor, o modo de desenvolvimento enxerga todos os dados (como gerente), então o escopo de dados por papel ("só o que é seu") **só vale em produção, com login**.
- Em produção, sua visão é só a sua. O menu mostra as telas de trabalho, prazos e as suas próprias carga e agenda; telas de liderança (Capacidade, Distribuição) e de gestão (Auditoria, Qualidade dos dados) não aparecem para você, e isso é esperado.
- Na demonstração, o **Meu painel** aparece zerado (os indicadores MP-01, MP-03, MP-05 e MP-06): o usuário de desenvolvimento não tem tasks. Isso é esperado e não é erro.
- Idioma e tema ficam no menu (pt-BR e inglês; Claro, Escuro e Sidia).

## Roteiro por tela

### Tela `meu` — Meu painel

**Pergunta.** O que tenho para fazer, e o que está apertado?

**Onde clicar.** Comece pelos indicadores no alto (MP-01 Minhas abertas, MP-03 Em risco, MP-06 No prazo e no prazo ajustado). Clique em qualquer número: abre a gaveta com as suas tasks. A chave de uma task abre os detalhes dela.

**Como ler o número.** MP-02 (Minha ocupação em 4 semanas) é a sua fila em horas dividida pela sua capacidade, já descontadas ausências e feriados: verde até 85%, laranja até 110%, vermelho acima, sempre com símbolo e texto. MP-05 (Minha fila) mostra a ordem em que a agenda executaria as suas tasks e o término previsto.

**Armadilha comum.** Ler a ocupação como nota. É um indicador de planejamento, não uma avaliação. E MP-04 (Minhas ausências futuras) mostra só os períodos cadastrados: o painel não guarda nem mostra o motivo.

### Tela `abertas` — Abertas

**Pergunta.** O que está em aberto agora, há quanto tempo e o que vence?

**Onde clicar.** Os filtros ficam no topo da tela; AB-05 (Abertas por status), AB-07 (Abertas por idade) e AB-08 (Abertas por vencimento) abrem as tasks de cada faixa. AB-09 (Tabela de abertas) é ordenável por qualquer coluna e exportável em CSV.

**Como ler o número.** AB-04 (Acima do P85 do tipo) separa as tasks em andamento mais velhas do que 85% das concluídas do mesmo tipo: são candidatas a atenção, não culpadas. A idade usa a unidade escolhida: **Dias úteis** (padrão) ou **Dias corridos**.

**Armadilha comum.** Comparar idade de tipos diferentes: cada tipo tem o próprio P85. E, com menos de 5 tasks no histórico, o número vem com aviso de amostra pequena.

### Tela `negligenciadas` — Negligenciadas

**Pergunta.** O que está parado, sem dono, vencido ou sem movimento de verdade?

**Onde clicar.** Cada cartão (NG-01 a NG-06) é um motivo: sem atualização, sem responsável, vencidas, bloqueadas, paradas no status e "a fazer" há mais de 30 dias. Clique no número para ver as tasks.

**Como ler o número.** NG-01 (Sem atualização) olha tasks em andamento sem atualização há mais de 7 dias corridos. Um comentário também atualiza a task, por isso a linha mostra o último movimento real.

**Armadilha comum.** Somar os cartões: a mesma task pode estar em mais de um (por exemplo, vencida e sem atualização).

### Tela `vencimentos` — Vencimentos da semana

**Pergunta.** O que vence nos próximos dias e quem corre o risco de atrasar?

**Onde clicar.** VE-01 (Vencem nos próximos dias) e VE-02 (Risco de atrasar): clique no número para abrir as tasks.

**Como ler o número.** O término previsto vem da capacidade do responsável (fila, ausências e feriados). Folga = dias úteis entre o término previsto e o vencimento. "Risco de atrasar" é folga negativa ou sem término previsto.

**Armadilha comum.** Tasks que já venceram não entram aqui: elas estão em Negligenciadas (NG-03). E "sem término previsto" não quer dizer atrasada, quer dizer que não cabe no horizonte ou não tem responsável.

### Tela `metas` — Metas e prazos

**Pergunta.** As entregas saíram até o vencimento? E pela data original combinada?

**Onde clicar.** MT-01 mostra, lado a lado, **No prazo** e **No prazo ajustado**; MT-02 mostra o prazo pela data original. MT-14 (Prazo por pessoa) lista as pessoas da sua visão em ordem alfabética.

**Como ler o número.** "No prazo" conta as concluídas que saíram até o vencimento (dia da resolução no fuso de Manaus). "No prazo ajustado" conta também as que têm um label que justifica o atraso. A diferença entre as duas é o atraso justificado. Só entram tasks concluídas (categoria concluído e resolução preenchida) com vencimento no período.

**Armadilha comum.** Achar que o "ajustado" é um jeito de esconder atraso. Os dois números ficam sempre lado a lado, e a Auditoria (para a coordenação e o gerente) olha as justificativas suspeitas. Indício, não prova.

### Tela `carga` — Carga por pessoa

**Pergunta.** Quanto trabalho cada pessoa tem na fila e quanto cabe na capacidade das próximas 4 semanas?

**Onde clicar.** CP-03 (Carga por pessoa) mostra a sua linha; clique nas abertas ou vencidas para abrir as tasks.

**Como ler o número.** Ocupação = demanda ÷ capacidade. Quando a capacidade da semana é zero (ausência, feriado), aparece **sem capacidade**, nunca uma porcentagem.

**Armadilha comum.** Procurar posição ou nota: pessoas ficam em ordem alfabética e o painel não faz ranking.

### Tela `calendario` — Calendário do time

**Pergunta.** Quem está ausente, quando há feriado e o que vence em cada dia?

**Onde clicar.** CL-01: cada dia abre as tasks que vencem nele.

**Como ler o número.** A capacidade do time no dia é a líquida dividida pela bruta das pessoas que você vê; dia não útil fica vazio. "Dia crítico" é um dia útil com vencimentos e capacidade abaixo do limite.

**Armadilha comum.** Procurar o motivo de uma ausência: o painel não o guarda nem o mostra.

### Tela `posse` — Posse da task

**Pergunta.** Quem ficou com cada task e por quanto tempo, e quantas vezes o responsável mudou?

**Onde clicar.** PS-01 (Posse por task) e PS-02 (Posse por pessoa) abrem as tasks.

**Como ler o número.** O tempo de cada trecho com o mesmo responsável é contado em dias úteis (ou corridos) até a resolução ou até agora.

**Armadilha comum.** Em produção, quem está fora da sua visão aparece nesta tela como "outra pessoa": é a regra de visão por papel, não um erro. Na demonstração você vê todos, porque o escopo por papel só vale com login.

## Outras telas que o seu papel vê

`epicos`, `riscos`, `previsao`, `fluxoDaSemana`, `agingWip`, `bloqueios`, `fechadas`, `negativas`, `cruzamentos`, `tempo`, `esforco`, `checklistReview`, `competencias`, `revisoes` e `cargaTipo`. Cada uma está descrita, com os seus cartões, no [guia do painel](../manual/guia-do-painel.md).

## Exercício com dados de demonstração

Com o papel **Usuário** escolhido (só o menu muda) e os dados de demonstração carregados. Os exercícios usam telas que mostram dados na demonstração, não o Meu painel:

1. Em **Meu painel**, observe os indicadores zerados e explique por quê (o usuário de desenvolvimento não tem tasks). Em **Abertas**, clique no número de AB-01: o total da gaveta é o mesmo do cartão?
2. Em **Abertas**, troque a unidade de **Dias úteis** para **Dias corridos**. Qual número mudou? Por quê?
3. Em **Metas e prazos**, anote "No prazo" e "No prazo ajustado" do período. A diferença entre eles é o quê?
4. Em **Carga por pessoa**, procure uma pessoa **sem capacidade** em alguma semana (se a demonstração tiver uma) e explique por que não aparece uma porcentagem.
5. Em **Metas e prazos**, tente achar uma posição ou uma nota para as pessoas. O que o painel mostra no lugar?

Gabarito: (1) o usuário de desenvolvimento não tem tasks; e sim, cartão e gaveta têm o mesmo total; (2) a idade das tasks, que em **Dias úteis** deixa de contar fins de semana e feriados; (3) o atraso justificado por label; (4) a capacidade da semana é zero; (5) uma tabela em ordem alfabética, sem ranking.

## Para saber mais

- Perguntas frequentes: [`perguntas-frequentes.md`](../manual/perguntas-frequentes.md) (inglês: [`faq.md`](../manual/faq.md)).
- Guia do painel: [`guia-do-painel.md`](../manual/guia-do-painel.md) (inglês: [`panel-guide.md`](../manual/panel-guide.md)).
