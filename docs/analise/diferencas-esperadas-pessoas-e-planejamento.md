# Diferenças esperadas: pessoas, planejamento e ausências (Fase 6)

Este documento lista o que **muda de propósito** entre o app Python anterior (`legacy/backend/jiradash/metrics/capacity.py`, `core.py`) e o painel novo nos cards de gestão de pessoas, capacidade e ausências. Quem validar com dados reais deve ler daqui o que **não** é bug. Fonte do inventário: `docs/analise/relatorio-cards-app-atual.md` (seções 4.14 a 4.16, achados 8 a 11). Spec: `docs/superpowers/specs/2026-10-25-spec-fase-06-pessoas-planejamento-ausencias.md`.

O bloco 6b (worklog, posse, esforço, cobertura, simulador, competências, revisões pendentes e carga por tipo/OS) tem a sua seção no fim deste documento ("Bloco 6b").

## Seção 6a: capacidade, carga e planejamento

### O que não muda (conferido no código do legado)

| Assunto | Regra | Teste |
|---|---|---|
| Capacidade do dia | horas semanais ÷ 5 × foco × alocação × (1 − fração de ausência); fim de semana e feriado valem 0 (`day_capacity_h`) | `CompatLegadoCapacidadeTest.capacidadeDoDiaComoOLegado` |
| Horas mínimas | estimativa menos registrado, no mínimo 0,5 h (`issue_hours`) | `horasMinimasComoOLegado` |
| Índice de Jain | (Σx)² ÷ (n × Σx²), 1,0 = distribuição igual (`jain_index`); aqui com teto por valor (3,0) | `jainComoOLegado` |

### Diferenças esperadas, por inventário

| Id | Tipo | O que mudou e por quê |
|---|---|---|
| CP-01 | diferença esperada | **Jain** do conjunto visível, com cada ocupação limitada ao teto (`tetoJain`, 3,0) e **sem valor com menos de 2 pessoas com capacidade**. O usuário comum nunca recebe o índice (revelaria os colegas). |
| CP-02 | diferença esperada | **Sem ranking de pessoas**: "maior e menor ocupação" foi trocado por duas **contagens** (pessoas acima da capacidade, ocupação maior que 110%, e pessoas com folga, menor que 50%). O cartão abre as tasks na fila das pessoas contadas (o número do cartão é o total da gaveta). |
| CP-03 | diferença esperada | Tabela **em ordem alfabética, sem coluna de posição**. "Capacidade zero" aparece como o texto **sem capacidade** (achado 8: o legado mostrava 990%), com as horas planejadas ao lado. Task vencida **não infla a semana atual**: soma em **atraso acumulado (h)** (achado 9). Horas por pessoa (`horasRegistradas30d`) ficam em traço até o worklog do bloco 6b. |
| CA-01 | diferença esperada | Contagem de alertas de carga e de ausência, só para a liderança (usuário comum recebe 403). |
| CA-02, CA-03, CA-09 | diferença esperada | **Atraso previsto** pela capacidade líquida, dia útil a dia útil (a `schedule` do legado distribuía por **semana inteira** e estimava o dia dentro da semana). A causa é **ausência** quando o atraso some no cenário sem ausência. A task **já vencida não é atraso previsto**: ela está em vencidas da Carga. |
| CA-04 | diferença esperada | Jain do conjunto visível (mesma regra do CP-01). |
| CA-05 | diferença esperada | Mapa de **8 semanas** por pessoa em ordem alfabética; semana sem capacidade por **ausência** mostra **aus.** (texto, com ▓), nunca porcentagem. A semana atual conta **de hoje até domingo** (o tempo que já passou não é livre). |
| CA-06 | diferença esperada | Alertas: carga da semana acima do limite alto, e semana com 2 ou mais dias de ausência **e** trabalho agendado, com as **horas a replanejar** (as que a agenda sem ausência teria feito nesses dias). |
| CA-07 | diferença esperada | **Ausências lidas de `ausencias.yaml`, somente leitura, sem motivo** (a chave `motivo` é recusada na carga e nunca aparece na resposta). Nenhuma rota grava ausência; o antigo card de "aprovação" vira o Simulador (bloco 6b). |
| CA-08 | diferença esperada | Feriados do horizonte com o **nome** do `calendario.yaml`. |
| GA-01 | diferença esperada | Fluxo cumulativo de **60 dias** reconstruído do changelog (status do fim de cada dia de Manaus), na ordem do fluxo do `status_map`; o status concluído fica de fora; sem tasks por trás (série). |
| GA-02 | diferença esperada | **Gargalo** só em etapa ativa ou de espera: acima do limite de WIP (`planejamento.yaml`) **ou** faixa que cresceu pelo menos max(3, 30%) em 15 dias. O **motivo** aparece em texto. |
| GA-03, GA-04 | diferença esperada | Pessoas acima da capacidade e com folga como **contagens** (sem ranking); a gaveta abre as tasks na fila delas. |

### Cards novos do bloco 6a

| Card | Observação |
|---|---|
| **Calendário do time** (`CL-01`) | Semana ou mês (28 dias) a partir de uma data: feriados com nome, ausências (▓ inteira, ▒ meio dia), vencimentos por dia (◆n) e **dia crítico** (▲: dia útil com vencimentos e capacidade do time abaixo de 60% da bruta). A capacidade do time usa só as pessoas que você vê: o usuário comum recebe a própria linha e a capacidade só dele. |
| **Plano de 4 a 8 semanas** (`PL-01`) | Horas planejadas contra a capacidade, por pessoa e semana (`semanas=4..8`, fora disso é 400). A parte das tasks **sem vencimento** aparece à parte (espalhadas por regra: 2 semanas se iniciadas, 4 se não). |
| **Vencimentos da semana** (`VE-01`, `VE-02`) | O que vence em 7 ou 14 dias com a **folga real** em dias úteis entre o término previsto pela capacidade e o vencimento. Folga negativa, ou sem término previsto, é "risco de atrasar" (▲). Não calcula "no prazo" nem "no prazo ajustado" (isso é da tela Metas e prazos). |
| **Tendência de ocupação** (`TD-01`) | 12 semanas por pessoa, reconstruídas do changelog (responsável e vencimento de cada segunda-feira) **com as horas de hoje** (aviso `HORAS_ATUAIS_NO_PASSADO`: não há retrato semanal gravado). Padrão recorrente (3 ou mais das últimas 4 semanas acima do limite alto), pontual (1 ou 2) ou normal. |

### Regras de horas e capacidade que mudam (achados 8 a 11)

| Achado | Legado | Painel novo |
|---|---|---|
| 8 | Capacidade zero → ocupação 990% | **Sem capacidade**, em texto, com a demanda ao lado; nunca divide por zero |
| 9 | Task vencida entrava na semana atual | **Atraso acumulado (h)**, fora das semanas |
| 10 | 6 h fixas, ou pontos × 4 h, para a task sem estimativa | **Horas padrão por tipo** em `planejamento.yaml` (Checklist 6 h, Checklist Review 2 h...), marcadas como **assumidas** (a confiança cai e a tela avisa); pontos não entram |
| 11 | "Maior e menor ocupação" e tabelas ordenadas por carga | **Contagens** e ordem alfabética (sem ranking) |
| — | Semana atual inteira | Semana atual **de hoje até domingo** |
| — | Previsão por semana inteira (`schedule`) | Previsão **dia a dia** pela capacidade líquida (fila: iniciadas, prioridade, vencimento, criação); alimenta o fator "previsão depois do vencimento" do Riscos e as colunas **pela capacidade** e **Atrasa?** do PV-06 |
| — | Restante igual a zero valia 0 h | Restante igual a zero **não informa** (o Jira o zera ao estourar a estimativa): cai para estimativa menos registrado ou para as horas por tipo |
| — | Agregados de todo o time | Agregados (Jain, capacidade do time) **só sobre o que o papel vê**; usuário comum não recebe agregado do squad |

### Parâmetros em YAML (a calibrar com dados reais)

`planejamento.yaml` guarda foco padrão, horas por tipo, mínimo de semanas, limiares de ocupação (atenção 85%, alta 110%, folga 50%), teto do Jain, limites de WIP, dia crítico e a versão (`planejamento v1`, em toda resposta). Todos são **ponto de partida**: calibrar depois da primeira conexão real. `equipes.yaml` e `ausencias.yaml` têm dados pessoais e ficam **fora do git** (só os modelos `.exemplo`); a base legal com o DPO deve ser confirmada antes de usar com dados reais.

## Bloco 6b: posse, esforço, cobertura, simulador, competências, revisões e carga por tipo

Todos os cartões abaixo são **somente leitura**: nenhuma rota de escrita sob `/api/cards`, `/api/cartoes`, `/api/drill` ou `/api/pessoas`, e nenhum código grava `equipes`, `ausencias` ou `papeis` (garantido por `SomenteLeituraTest`). Pessoas sempre em **ordem alfabética**, sem ranking.

| Tema | Legado ou expectativa | Painel novo |
|---|---|---|
| **Posse** (`PS-01`, `PS-02`) | Toda a task ia para o responsável final | **Posse proporcional**: a task é dividida entre quem foi responsável, na proporção do tempo com cada um (changelog). Quem está fora da visão do papel vira "outra pessoa"; task sem responsável soma fração nula. Drill: tasks, transferidas e `pessoa:<id>` |
| **Esforço** (`EF-01`, `EF-02`) | Não existia | **Índice proposto**, em `esforco.yaml` (modelo `.exemplo`), com versão das regras em toda resposta. A **calibração está pendente** (pesos por tipo, testes ponderados e fator de revisão são ponto de partida). Granularidade semana ou mês; as horas vêm do worklog e, sem ele, do campo `timespent` (aviso `HORAS_DO_CAMPO_TIMESPENT`) |
| **Cobertura** (`CB-01`) | "Aprovação de ausências" | A aprovação **virou o simulador** e a cobertura: para cada ausência próxima, tasks sem dono, horas a replanejar, competências pedidas e **candidatos sem ordem por folga** (alfabética, com a folga ao lado: ordenar por folga seria ranking de disponibilidade). Só liderança |
| **Simulador** (`SI-01`) | Aprovar ou negar ausência | Formulário de **leitura** (GET): "se esta pessoa faltar de X a Y, quais atrasos surgem". Valida período de até 60 dias, fração 0,5 ou 1, pessoa existente; não grava nada e a pessoa não simula a si mesma (403). Sem parâmetros: 200 com aviso `SIMULACAO_SEM_PARAMETROS`. Só liderança |
| **Competências** (`CM-01`, `CM-02`, `CM-03`) | Planilha manual | Matriz pessoa x competência vinda de `equipes.yaml` (sem novo YAML); marca **pessoa única** e **ninguém** em triângulo e texto; drill `semNinguem` e `skill:<x>` |
| **Revisões pendentes** (`RV-01`, `RV-02`) | Contagem solta de reviews | Por revisor: pendentes, vencidas e fora do prazo de revisão (dias úteis). "Sem revisor" só para coordenação e gerente |
| **Carga por tipo e OS** (`CG-01`, `CG-02`) | Soma única | Abertas e fechadas por tipo e versão da OS; sem OS aparece como "sem OS" |
| **Worklog** | Não sincronizado | Sincronizado por instância (migração V4): id, autor, início e segundos, **sem comentário** (dado pessoal). Endpoint ausente não apaga o que já existe. Alimenta as horas registradas em 30 dias da carga e o esforço |

Pendências sem dono: "estimativa reduzida" (queda de estimativa depois do início) e "worklog retroativo" (registro lançado muito depois do dia trabalhado) são sinais citados no relatório que nenhum cartão cobre ainda.
