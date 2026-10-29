# Treinamento: Gerente

Material para o gerente do TG-Basic. É o **único** material que trata da Integridade e do Registro de acesso. Roteiro da sessão (agenda, pré-requisitos e fechamento): [`roteiro-da-sessao.md`](roteiro-da-sessao.md). Para as telas que o gerente divide com a coordenação (Metas e prazos, Fechadas, Divergência, Decisões), veja o [material da coordenação](coordenador.md). Guia completo: [`guia-do-painel.md`](../manual/guia-do-painel.md).

> **Estado de hoje (29/10/2026).** O acesso ao Jira está desligado (`JIRA_LIVE_ENABLED=false`). A sessão usa os **dados de demonstração** fictícios; os números não são os do time. A **Integridade** e a **Auditoria** estão atrás da Trava do DPO: fechada por padrão até o aceite do encarregado de dados (configuração `DASHBOARD_DPO_ACEITE` com a referência do parecer, ligada só pelo usuário), e aberta apenas na demonstração.

## Objetivo da sessão

Duração: 30 a 45 min. Ao fim, o gerente sabe começar pela **Central de atenção**, usar a **Auditoria** e a **Integridade** com a regra "Indício, não prova" e com a consulta sempre auditada, ler o **Registro de acesso**, conferir o estado da **Sincronização** e da **Configuração** e usar o **Mapa de cartões** para explicar qualquer número. Também sabe o que o painel **não** faz: não ranqueia pessoas, não conclui nada sobre uma pessoa e não altera nada no Jira.

## Antes de começar

- Acesso ao painel e papel de **Gerente** em `papeis.yaml`. Na demonstração (modo de desenvolvimento, sem login, faixa vermelha no topo), escolha **Gerente** em **Papel (simulação)**, no menu. Na demonstração o seletor só muda o **menu**; o servidor em modo de desenvolvimento age como gerente e mostra todos os dados.
- O gerente vê todos os squads e as telas de governança que a coordenação não vê: Integridade, Registro de acesso, Sincronização e Configuração.
- **Regra de consulta auditada.** Integridade e Auditoria tratam de dados de pessoas. Toda consulta é registrada pelo painel (inclusive as negadas e a consulta ao próprio registro), e o uso real só vem depois do aceite do DPO. Consulte para decidir o que conversar e conferir, nunca para punir pelo número.

## Roteiro por tela

### Tela `central` — Central de atenção

**Pergunta.** O que pede atenção agora, e onde está?

**Onde clicar.** O cartão VG-09 reúne os indicadores de atenção (vencidas, risco, capacidade, atrasos previstos, sem atualização, paradas, sem responsável, bloqueadas); cada número abre a gaveta com o mesmo total do card de destino. VG-13 (Pessoas com indício alto) só aparece para o gerente e leva à Integridade.

**Como ler o número.** VG-11 (Quando o backlog termina) é previsão em três níveis de confiança e VG-12 (Ocupação por squad) é indicador do conjunto, sem nota de pessoa.

**Armadilha comum.** Tratar VG-13 como lista de suspeitos. É um indício para conferir, nunca uma prova, e só aparece com a Trava do DPO aberta.

### Tela `auditoria` — Alterações de data, dono e label

**Pergunta.** Houve alterações de vencimento, responsável e labels de atraso por quem talvez não pudesse?

**Onde clicar.** AU-01 (Sinais para conferir), AU-02 (Sinais por tipo) e AU-03 (No prazo e No prazo ajustado). A task abre o histórico campo a campo.

**Como ler o número.** Cada alteração é comparada com quem podia fazê-la na data: o Reporter da task, a coordenação, o gerente ou o Squad Leader do responsável, com os papéis vigentes naquele dia. Os limites e as gravidades vêm de `auditoria.yaml`.

**Armadilha comum.** Confundir sinal com culpa. **Indício, não prova.** Esta tela também passa pela Trava do DPO (cartões AU-01 a AU-03, DV-01 e VG-14); o histórico campo a campo só abre com a trava aberta. Há dois sinais além dos de data, dono e label: `ESTIMATIVA_REDUZIDA` e `WORKLOG_RETROATIVO`.

### Tela `integridade` — Integridade

**Pergunta.** Há sinais de que prazo ou fluxo foram ajustados de forma suspeita?

**Onde clicar.** IN-02 (Pessoas com indício alto ou médio), IN-05 (Evidências por sinal), IN-07 (Tabela de evidências) e IN-08 (Catálogo de sinais, que explica o que cada sinal procura e o limite configurado). IN-01 e IN-03 abrem as tasks.

**Como ler o número.** São 12 sinais, cada um com gravidade. IN-02 lista as pessoas em ordem alfabética, sem ranking, com pontos, taxa (pontos por concluída), o múltiplo da taxa em relação à mediana do time e o nível de indício (alto ou médio): o nível é motivo de conversa, não nota. O catálogo de sinais (IN-08) inclui `ESTIMATIVA_AUMENTADA`. IN-06 mostra quantos pontos percentuais o "no prazo" atual está acima do "no prazo pela data original".

**Armadilha comum.** Usar o nível como avaliação de desempenho. **Indício, não prova**: cada sinal é um motivo para conversar e conferir a task no Jira.

**A Trava do DPO.** Sem o aceite do DPO, a tela mostra "Esta tela fica desligada até o encarregado de dados (DPO) aprovar o uso" e nenhum dado é consultado; a resposta do servidor é o código `AGUARDANDO_ACEITE_DPO`. Ela só abre com o aceite do DPO (`DASHBOARD_DPO_ACEITE`) ou na demonstração: desenvolvimento, Jira desligado e banco sem dado sincronizado de verdade. Qualquer estado de sincronização de uma instância do Jira no banco fecha a porta da demonstração. Sem aceite, em produção está fechada.

### Tela `registroAcesso` — Registro de acesso

**Pergunta.** Quem consultou o quê no painel, e quando?

**Onde clicar.** RA-01: do mais recente ao mais antigo, até 90 dias, em páginas de 200.

**Como ler o número.** Cada linha traz quem consultou, qual rota, quando, o resultado (200, 400, 403) e só os nomes dos filtros usados. Não mostra chave de task, segmento nem valor de filtro. Sua própria consulta a esta tela também fica registrada.

**Armadilha comum.** Usar o registro para vigiar o trabalho das pessoas. Ele existe para provar que as telas sensíveis são consultadas de forma auditável.

### Tela `sincronizacao` — Sincronização

**Pergunta.** Em que estado está a sincronização com o Jira?

**Onde clicar.** SY-01 (Modo do sincronizador), SY-02 (Últimas execuções por instância) e SY-03 (Retenção).

**Como ler o número.** Hoje a tela mostra "Desligado pela flag": nada fala com o Jira. A sincronização periódica (a cada 10 minutos, por padrão) e a reconciliação diária só rodam depois que **o usuário** ligar a flag. Se o sincronizador for suspenso por erro, é preciso reiniciar o painel.

**Armadilha comum.** Esperar números atualizados com a flag desligada. Com ela desligada, só existem os dados de demonstração.

### Tela `configuracao` — Configuração

**Pergunta.** Com que regras e em que modo o painel está rodando?

**Onde clicar.** CF-01: o modo do ambiente, o modo da sincronização e, de cada arquivo de regras, versão, hash e se vale o arquivo real ou o modelo.

**Como ler o número.** Nunca mostra segredo, URL, caminho, nomes de pessoas nem o conteúdo de papéis, equipes e ausências; desses só as contagens.

**Armadilha comum.** Esperar editar aqui. É só leitura: as regras mudam nos arquivos YAML, por quem mantém o painel.

### Tela `qualidade` — Qualidade dos dados

**Pergunta.** O que está faltando ou desencontrado nos dados que chegam do Jira?

**Onde clicar.** QD-01 a QD-05; o aviso de dado incompleto dos outros cartões leva até aqui.

**Como ler o número.** QD-02 lista tasks em que o status concluído e a resolução não concordam. QD-03 mostra status que não estão no `status_map.yaml` e labels recentes fora de `labels_atraso.yaml`.

**Armadilha comum.** Corrigir no painel: ele só lê.

### Tela `mapaCartoes` — Mapa de cartões

**Pergunta.** Quais cartões existem, o que mostram e como são calculados?

**Onde clicar.** Filtros por grupo e por natureza, busca por título.

**Como ler o número.** Para cada cartão: o que mostra, como é calculado, a fonte do dado, a tela e quem vê. É gerado do catálogo do backend.

**Armadilha comum.** Procurar dados: o Mapa descreve, não mede.

### Tela `meusDados` — Meus dados

**Pergunta.** O que o painel guarda sobre mim, para que serve e por quanto tempo?

**Onde clicar.** Abra **Meus dados** no menu (todos os papéis têm a tela). "Baixar meus dados (JSON)" gera, no navegador, um arquivo com o mesmo conteúdo da tela.

**Como ler o número.** A tela mostra só o seu registro: quantidades de tasks, alterações e registros de horas, ausências planejadas (sem o motivo), os seus papéis, as categorias tratadas com finalidade e prazo de retenção (24 meses por padrão, a confirmar com o DPO) e os seus acessos dos últimos 90 dias.

**Armadilha comum.** Esperar ver os dados de outra pessoa (a tela não aceita pedir) ou o conteúdo de sinais de Auditoria sobre você (quem quiser esse levantamento pede ao DPO). Para corrigir um dado, ajuste a task no Jira; para pedir eliminação, use o canal do DPO.

## Outras telas que o seu papel vê

`decisoes`, `divergencia`, `metas`, `fechadas` e todas as de liderança e de usuário. Cada uma está descrita, com os seus cartões, no [guia do painel](../manual/guia-do-painel.md).

## Exercício com dados de demonstração

Com o papel **Gerente** escolhido e os dados de demonstração carregados:

1. Na **Integridade**, abra IN-08 e escolha um sinal: o que ele procura e qual o limite?
2. Em IN-02, confira que as pessoas aparecem em ordem alfabética e explique por que não há posição.
3. Abra o **Registro de acesso** e leia as consultas mais recentes. O que o registro mostra do filtro usado?
4. Em **Sincronização**, leia o modo. Por que não há execuções?
5. Em **Configuração**, ache um arquivo de regras e diga se vale o real ou o modelo.

Gabarito: (1) conforme o catálogo de sinais, sem dados de pessoas; (2) o nível é motivo de conversa, não nota; (3) a rota, o resultado e só os nomes dos filtros, nunca os valores; (4) a flag do Jira está desligada; (5) o painel mostra qual arquivo vale.

## Para saber mais

- Perguntas frequentes: [`perguntas-frequentes.md`](../manual/perguntas-frequentes.md) (inglês: [`faq.md`](../manual/faq.md)).
- Guia do painel: [`guia-do-painel.md`](../manual/guia-do-painel.md) (inglês: [`panel-guide.md`](../manual/panel-guide.md)).
