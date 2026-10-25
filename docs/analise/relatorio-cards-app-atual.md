# Relatório dos cards do app atual (Python/FastAPI)

Objetivo: inventariar cada tela e card do app atual, o que cada um espera como entrada e quais cálculos faz. É a base para **validar, recriar (Kotlin + React), melhorar e criar cards novos** voltados a gestão de pessoas, planejamento de atividades e ausências.

- Data: 2026-10-24. Nenhuma consulta foi feita ao Jira.
- Fonte principal: `app/docs/manual/MANUAL_DO_USUARIO.md` (que afirma ter conferido as fórmulas no código), cruzada com amostras do código (`metrics/risk.py`, `metrics/capacity.py`, `analytics.py`, `jira/sync.py`, `jira/client.py`).
- **Verificado por mim no código:** pesos do risco (30/15/25/20/15, estouro de horas > 125%), níveis do risco (≥ 60 alto, ≥ 30 médio), capacidade por dia = horas semanais ÷ 5 × foco (0,7) × alocação, horas mínimas por issue (0,5 h), campos buscados no Jira.
- **Não reverificado linha a linha:** os demais cálculos abaixo vêm do manual. Marcados como "validar" na seção 6.

---

## 1. Mapa das telas (23)

| Grupo | Telas | Prefixo dos cards |
|---|---|---|
| Visão | Visão geral, Meu painel | VG, MP |
| Trabalho em curso | Abertas, Épicos, Negligenciadas, Riscos, Previsão | AB, EP, NG, RI, PV |
| Resultados | Fechadas, Negativas, Tempo em status | FE, NE, TS |
| Prazos e qualidade | Metas e prazos, Checklist × Review, Integridade | MT, CR, IN |
| Pessoas e capacidade | Carga por pessoa, Capacidade e ausências, Gargalos e fluxo | CP, CA, GA |
| Ação | Distribuição, Rebalanceamento | DI, RB |
| Governança | Qualidade dos dados, Auditoria, Sincronização | QD, AU, SI |

## 2. Entradas comuns

### 2.1 Do Jira (campos buscados hoje)
`summary, issuetype, priority, status, resolution, assignee, reporter, created, updated, resolutiondate, duedate, timeoriginalestimate, timeestimate, timespent, labels, components, parent, project`, mais campos descobertos por nome: Story Points, Sprint, Flagged, Epic Link.
Também: **changelog** (status, assignee, duedate, labels, estimativa, prioridade, resolution) e **worklogs**.

**Não buscados hoje e necessários para o contexto TG-Basic:** `OS Version`, `SKU Type`, `Checklist Type`, `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code`, `cf[12416]`, `cf[11412]`.

### 2.2 Que NÃO vêm do Jira (config em YAML)
Squads e gerentes, membros, horas semanais, alocação e fator de foco, **férias, licenças e feriados**, competências (`skill:xxx`), limites de WIP, mapa de categorias de status (`todo|active|wait|blocked|done`), resoluções negativas, metas (`on_time_pct`, `early_pct`), tipos de checklist/review.

### 2.3 Filtros globais
Squad, Projeto, Pessoa, Tipo, Prioridade (combinados por E); De/Até só em 6 telas (Fechadas, Negativas, Tempo em status, Metas, Checklist × Review, Integridade), aplicado à **data de resolução**; granularidade (dia/semana/mês) só em Metas. Cache de 60 s.

## 3. Cálculos transversais (usados por vários cards)

| Cálculo | Definição |
|---|---|
| Aberta / concluída | Pela **categoria do status** no `status_map`, não pelo campo `resolution` |
| Resolução positiva | `resolution` fora da lista de negativas (Cancelado, Duplicado, Não será feito) |
| Ciclo (cycle time) | 1ª entrada em status ativo/espera/bloqueado → resolução, em **dias corridos** |
| Lead time | criação → resolução, dias corridos |
| Idade | agora − 1ª entrada em status ativo (ou criação, se nunca iniciou) |
| Percentis | P50, P85, P95 sobre as concluídas do período (histórico de 16 semanas em Abertas/Previsão) |
| Throughput | concluídas com resolução positiva por semana (segunda a domingo) |
| Horas por issue | horas restantes do Jira; senão estimativa − registrado (mín. 0,5 h); senão pontos × 4 h; senão 6 h |
| Capacidade líquida/dia | horas semanais ÷ 5 × foco (0,7) × alocação × (1 − fração de ausência); fim de semana e feriado = 0 |
| Demanda semanal | horas da issue espalhadas da semana atual até a semana do vencimento (mín. 2 semanas); sem vencimento: 2 sem. se iniciada, 4 se não |
| Ocupação (4 sem.) | demanda ÷ capacidade líquida; capacidade 0 com demanda = **990%** (sentinela) |
| Índice de Jain | (Σx)² ÷ (n·Σx²), x = ocupação limitada a 3,0 |
| Monte Carlo | 10.000 sorteios com reposição do throughput semanal (12 ou 16 semanas), sem entrada de itens novos |
| Previsão pela capacidade | fila da pessoa preenchida com horas líquidas; ordem: já iniciadas, prioridade, vencimento, criação |
| Risco (0–100) | idade > P85 +30 (ou > P50 +10); parada no status +15; vencida +25 (ou previsão após o vencimento +20, ou vence em ≤ 3 d +8); bloqueada +15; reaberta +10; ≥ 3 trocas de responsável +10; horas > 125% do estimado +15; sem atualização > 7 d +10; sem estimativa +5. Alto ≥ 60, médio 30–59 |
| Dia civil | fuso `JIRADASH_TZ` (padrão America/Sao_Paulo) em "no prazo", D-1/D-2 |

## 4. Catálogo de cards por tela

Legenda da coluna **Espera**: entradas do card. **Cálculo**: o que faz. **V** = precisa validar na recriação.

### 4.1 Visão geral (VG)
| ID | Card | Espera | Cálculo |
|---|---|---|---|
| VG-01 | Abertas | issues + status_map | contagem de não concluídas; "em andamento" = ativa/espera/bloqueada |
| VG-02 | Concluídas (30 d) | resolução, janela 30 d + 30 d anteriores | contagem com resolução positiva; variação % vs. 30 d anteriores |
| VG-03 | Ciclo P50 (P85 no rodapé) | histórico de status | mediana e P85 do ciclo em 30 d; seta invertida (menor é melhor) |
| VG-04 | No prazo (30 d) | duedate, resolutiondate | resolvidas ≤ vencimento ÷ concluídas com vencimento; verde ≥ 80% |
| VG-05 | Vencidas | duedate | abertas com `due < hoje` |
| VG-06 | Negligenciadas | updated | em andamento sem atualização > 7 d |
| VG-07 | Bloqueadas | categoria, flag | categoria bloqueada ou flag ligada |
| VG-08 | Risco alto (médio no rodapé) | todo o risco | score ≥ 60 (30–59) |
| VG-09 | Central de atenção (13 itens) | resultados de outras telas | vencidas; risco alto; pessoas > 110%; atrasos previstos; negligenciadas + paradas; sem responsável; bloqueadas; ausências em 14 d; pessoas com folga (< 50%); meta de prazo (90 d); pares checklist × review com alerta; pessoas com indício alto; sem estimativa (> 20%) |
| VG-10 | Entregas por semana | throughput | colunas de 12 semanas |
| VG-11 | Quando o backlog termina | throughput 12 sem. | Monte Carlo (P50/P85/P95); confiança "baixa" com < 8 semanas |
| VG-12 | Cartão por squad | ocupação, Jain | ocupação média (demanda 4 sem. ÷ capacidade); verde ≤ 85%, laranja ≤ 110%, vermelho acima |

### 4.2 Meu painel (MP) — exige Pessoa
MP-01 Minhas abertas (contagem) · MP-02 Ocupação 4 semanas · MP-03 Em risco (score ≥ 30) · MP-04 Ausências futuras (fim ≥ hoje) · MP-05 Minha fila (abertas por idade, com previsão e risco).

### 4.3 Abertas (AB)
AB-01 Abertas · AB-02 Idade P50 histórica e AB-03 P85 (ciclo das concluídas em 16 semanas do squad/projeto, sem filtro de pessoa/tipo/prioridade) · AB-04 Acima do P85 (em andamento com idade > P85) · AB-05 Por status (ordem do fluxo, cor por categoria) · AB-06 Por prioridade · AB-07 Idade (0–7, 8–14, 15–30, > 30 d) · AB-08 Vencimento (vencidas, vence em 7 d, depois, sem) · AB-09 Tabela de issues abertas (ordenável, CSV).

### 4.4 Épicos (EP)
EP-01 Tabela por épico (ignora pessoa/tipo/prioridade). Itens (exclui Review), concluídos, progresso = concluídos ÷ itens, mais antigo, vencidos, pontos, **previsão (sem.) = abertos ÷ (throughput semanal médio × participação do épico), máx. 52** (grosseira).

### 4.5 Negligenciadas (NG)
NG-01 Sem atualização (> 7 d) · NG-02 Sem responsável · NG-03 Vencidas · NG-04 Bloqueadas · NG-05 **Paradas no status** (tempo desde a última transição > max(3 d, 2 × mediana do status em 16 sem.)) · NG-06 A fazer há > 30 d. Listas até 100.

### 4.6 Riscos (RI)
RI-01 Risco alto/médio (contagens) · RI-02 Tabela em risco (até 200, fator principal, gaveta com pontos) · RI-03 Sinais por pessoa (contagem de fatores, alfabética). Pesos na seção 3 (**heurísticos**).

### 4.7 Previsão (PV)
PV-01 Backlog · PV-02 Termina em P50/P85/P95 (Monte Carlo, 16 sem.) · PV-03 Confiança · PV-04 Throughput semanal · PV-05 Quantos itens em 2/4/8 semanas (percentis conservadores: P85 = 15º percentil dos totais) · PV-06 Previsão por issue (P50/P85 do restante dos itens históricos com ciclo > idade, mín. 5; "pela capacidade"; "Atrasa?").

### 4.8 Fechadas (FE)
FE-01 Concluídas · FE-02 Pontos entregues · FE-03 Ciclo P50/P85 · FE-04 Lead time P50/P85 · FE-05 No prazo · FE-06 Canceladas · FE-07 Entregas por semana · FE-08 Dispersão do ciclo (P50/P85/P95) · FE-09 Por tipo · FE-10 Por projeto · FE-11 Entregas por pessoa (finalizou × participou, pontos, ciclo, tempo ativo, estimado × registrado) · FE-12 Tabela de concluídas (Real÷estim., prazo, CSV).

### 4.9 Negativas (NE)
NE-01 Reabertas (concluído → não concluído) · NE-02 Devolvidas (volta a etapa anterior do fluxo) · NE-03 Canceladas · NE-04 Concluídas com atraso · NE-05 Abertas vencidas · NE-06 Estouro de estimativa (> 125%) · NE-07 Muitas trocas de responsável (≥ 3) · NE-08 Taxas de reabertura/devolução/atraso (vermelho > 20%) · NE-09 Listas por categoria (até 150, "maior tempo" por status) · NE-10 Por responsável.

### 4.10 Tempo em status (TS) — **fatia vertical escolhida**
TS-01 Issues analisadas · TS-02 Eficiência de fluxo = tempo em status ativos ÷ tempo total em não concluídos · TS-03 Paradas além do normal (> 1,5 × mediana, mín. 2 d) · TS-04 Tempo por status (mediana/P85, soma reentradas) · TS-05 Onde o tempo é gasto · TS-06 Mapa pessoa × status (mediana; ≤ 3 d, 3–6 d, > 6 d) · TS-07 Lista de paradas (até 50).

### 4.11 Metas e prazos (MT)
Universo: concluídas com resolução positiva e vencimento no período (padrão 90 d), exclui tipo Epic. Δ = dia da resolução − vencimento (fuso local).
Buckets: D-3 ou antes (Δ ≤ −3), D-2, D-1, D (0), atrasada 1–3 d, atrasada > 3 d. "No prazo" = Δ ≤ 0.
MT-01 No prazo (meta 90%) · MT-02 No prazo pela data original (1º vencimento do changelog; laranja se ≥ 5 pp menor) · MT-03 Antecipadas (Δ ≤ −1; meta 35%) · MT-04 Em D-1 ou D-2 · MT-05 No dia (D) · MT-06 Atrasadas · MT-07 Sem vencimento · MT-08 Entregas por período (empilhado) · MT-09 % no prazo por período vs. meta · MT-10 Distribuição · MT-11 a MT-14 Tabelas por tipo, prioridade, squad, pessoa (pílulas: ✓ ≥ meta, ● até 10 pp abaixo, ▲ abaixo).

### 4.12 Checklist × Review (CR)
Pares pai (Checklist) / filha (Review) pelo campo `parent`; filtros aplicam ao checklist.
CR-01 Pares · CR-02 Checklists sem review · CR-03 Ciclo da review P50 · CR-04 Review no ciclo do checklist (mediana da razão) · CR-05 Review criada após o pai (P50 em horas) · CR-06 Review vence depois do pai (laranja > 10%) · CR-07 Mesma pessoa nos dois (laranja > 10%) · CR-08 Pares com alerta (vermelho > 30%) · CR-09 Tabela de pares e comparação lado a lado · CR-10 Aprendizados (frases só com amostra mínima) · CR-11 Duração ativa da review × reabertura (< 1 h, 1–4, 4–12, ≥ 12) · CR-12 Quem atrasa quem · CR-13 Labels e resultado (**lift** = reabertura com a label ÷ média; "n baixo" se n < 5) · CR-14 Diferenças de labels.
Alertas por par: review vence depois, pai fechado antes da review, mesma pessoa, review < 0,5 h ativa com pai reaberto, review vencida, review atrasada.

### 4.13 Integridade (IN) — sensível (LGPD)
12 sinais com gravidade 1–3 (resgate por prorrogação 3; troca de responsável ≤ 2 h antes de fechar 3; criada e fechada em < 15 min 3; reabre e fecha < 2 h 3; fechamento em lote ≥ 6 em 10 min 3; vencimento prorrogado 2; vencimento definido tardiamente (> 70% da vida) 2; pulou o fluxo 2; estimativa +30% 2; label de criticidade removida perto do fim 2; fechada por quem não é o responsável 1; fechada entre 22h e 24h do vencimento 1).
Nível por pessoa: pontos, taxa = pontos ÷ max(concluídas, 8), × mediana (piso 0,05). Alto: ≥ 12 pontos, ≥ 3× e ≥ 3 sinais graves. Médio: ≥ 6 pontos e ≥ 2×.
IN-01 Evidências · IN-02 Pessoas alta/média · IN-03 Issues afetadas · IN-04 Mediana da taxa · IN-05 Evidências por sinal · IN-06 Impacto nas metas (no prazo − data original) · IN-07 Tabela de evidências · IN-08 Catálogo. Cada consulta é auditada.

### 4.14 Carga por pessoa (CP)
CP-01 Jain · CP-02 Maior/menor ocupação · CP-03 Tabela (alfabética): abertas, WIP, fila (h), capacidade 4 sem., ocupação, ausência (d), horas registradas 30 d, > P85, vencidas, risco alto.

### 4.15 Capacidade e ausências (CA)
CA-01 Alertas de carga/ausência · CA-02 Atrasos previstos pela capacidade · CA-03 Por causa de ausência (compara previsão com e sem ausência) · CA-04 Jain · CA-05 Mapa de capacidade (8 semanas, % por pessoa e semana; "aus." quando a semana não tem capacidade) · CA-06 Alertas (carga > 110%; semana com ≥ 2 dias de ausência e trabalho agendado, com ~h a replanejar) · CA-07 Ausências planejadas (sem motivo) · CA-08 Feriados no horizonte · CA-09 Lista de atrasos previstos (causa ausência ou carga).

### 4.16 Gargalos e fluxo (GA)
GA-01 Fluxo cumulativo de 60 dias (estado reconstruído do changelog) · GA-02 Estado das etapas: **gargalo** = acima do limite de WIP, ou banda que cresceu ≥ max(3, 30%) em 15 dias, em etapa ativa ou de espera · GA-03 Pessoas acima da capacidade · GA-04 Folga (< 50%).

### 4.17 Distribuição (DI) e Rebalanceamento (RB)
DI: pendentes = "a fazer" sem responsável ou com o gerente. Elegível: capacidade de 4 sem. > 0,5 h.
`score = carga projetada ÷ capacidade + 0,5 × (WIP ÷ limite) + 1,0 se WIP ≥ limite + 0,6 × competências faltantes − 0,15 se já trabalhou no épico/componente`. Carga projetada usa peso de produtividade limitado a 0,85–1,15. Menor score vence; empate: quem recebeu há mais tempo; itens processados do maior para o menor.
DI-01 Aguardando · DI-02 Sugestões pendentes · DI-03 Gerar sugestões (Jain antes → depois) · DI-04 Ranking e fatores · DI-05 Decisão: aprovar, trocar (motivo obrigatório), rejeitar.
RB: gatilho ocupação > 110%; só issues não iniciadas; até 10 movimentos; histerese de 20 pp; destino exclui quem não tem a competência. RB-01 Calcular · RB-02 Movimentos pendentes · RB-03 Decisão.
Escrita no Jira desligada (modo sombra) por padrão.

### 4.18 Governança
QD (7 verificações em abertas; verde ≤ 10%, laranja ≤ 30%): sem estimativa, sem responsável, sem vencimento, sem épico, em andamento sem horas, sem squad, prioridade padrão. AU-01 Decisões de distribuição, AU-02 Registro de auditoria. SI-01 Estado da sincronização, SI-02 Como ligar ao Jira.

## 5. O que o bscbi-web já valida e este app ainda não cobre

| Regra do bscbi-web (em produção) | Situação no app atual |
|---|---|
| Atraso **justificado** por lista de labels (`ONHOLDBY-*`, `LATE_SENT`, `DUEDATE_NEGOTIATED`, `BI-CONSIDERCANCELLED`…) conta como no prazo | Ausente. MT/NE/FE tratam todo atraso como atraso |
| Comparar apenas a **data** (zera hora) para atraso; tolerância de dia inteiro | Parcial (dia civil local; sem a tolerância de +23 h) |
| Review × Complete por `summary ~ Review` (`isReviewer`) | App usa `issuetype` Review; no TG-Basic existe `Checklist Review` e `Checklist Type` (Complete) |
| Exclusão padrão `resolution != Canceled AND status != Cancelled` | Parcial (resoluções negativas configuráveis) |
| `startedInProgressAt` = 1ª entrada em In Progress, `leftInProgressAt` = **última** saída | App usa 1ª entrada em qualquer status ativo/espera/bloqueado |
| Outliers > 30 dias (720 h) excluídos nos tempos CAT | Ausente |
| Taxonomia por campo: Model Category, SKU Type (MR/1st carrier), OS Version, Checklist Type, Carrier | Ausente (campos não buscados) |
| Totais baratos por `maxResults=1` e ler `total` | Ausente (conferência de contagem) |
| Tipos do TG-Basic: Checklist, Checklist TG, Checklist Review, Feature check, Propagation Issue/List, Task | Hoje só "Checklist" e "Review" |

## 6. Divergências e pontos a validar (achados da leitura)

1. **Datas em UTC × fuso local:** "no prazo" usa fuso local em Metas, mas UTC em Visão geral e Fechadas (manual, FAQ). Um mesmo item pode cair em dias diferentes. Padronizar em fuso local.
2. **Central de atenção:** soma "sem atualização" com "paradas no status" e pode contar a mesma issue duas vezes; o manual dá explicações conflitantes (3.1 vs. observação 1).
3. **Negativas:** reabertas/devolvidas/trocas filtram o período pela data da **última atualização**, aproximação que perde eventos antigos já atualizados depois.
4. **Ciclo e lead em dias corridos:** fins de semana e feriados inflam o ciclo; nenhuma métrica usa dias úteis.
5. **"Concluída" por categoria de status, não por `resolution`:** pode divergir do Jira; depende de `status_map` completo (status não mapeado cai para a categoria do Jira e só avisa).
6. **Monte Carlo** (VG-11, PV): assume o ritmo recente e que nenhum item novo entra; confiança "baixa" apenas por tamanho da amostra.
7. **Previsão de épicos** (EP-01): participação do épico × throughput médio é uma aproximação grosseira.
8. **Ocupação 990%:** sentinela confunde (parece valor real). Usar campo explícito "sem capacidade".
9. **Demanda semanal:** issues vencidas concentram horas na semana atual, inflando ocupação; sem vencimento assume 2 ou 4 semanas.
10. **Horas padrão (6 h; pontos × 4 h):** convenções sem base. Impacta ocupação, previsão e distribuição.
11. **Heurísticas sem calibração:** pesos do risco, limiares da Integridade (12 pontos, 3×), pesos do score de distribuição (1,0/0,5/1,0/0,6/0,15) e fator de foco 0,7. Marcar como configuráveis e calibrar com dados reais.
12. **Integridade:** texto de ajuda da tela diverge do código (código exige também ≥ 12 pontos). Sinal "fechada entre 22h e 24h" depende do fuso.
13. **Riscos:** a lista oculta score < 30; sinais por pessoa contam fatores de issues em risco (não proporcionais ao volume).
14. **Tabela Metas "Por tipo":** mistura Checklist e Review; sem o conceito de atraso justificado (seção 5).
15. **Cliente Jira nunca testado em servidor real** (README): pontos a verificar primeiro no Cloud (bulkfetch) e DC (campo from/to de assignee, changelog truncado).

## 7. Melhorias e cards novos propostos

Foco pedido: gerenciar pessoas, planejar atividades e ausências. Prioridade A = recomendo no primeiro ciclo; B = segundo; C = depois.

### 7.1 Ajustes nos cards existentes
| Prio | Melhoria |
|---|---|
| A | Atraso **justificado** por labels (config) em MT, NE, FE e Meu painel; mostrar "atrasadas" e "atrasadas sem justificativa" |
| A | Dicionário de campos do TG-Basic (OS Version, SKU Type, Checklist Type, Model Name…) como filtros e agrupamentos (por versão de OS, por tipo de checklist) |
| A | Fuso local único; opção de **dias úteis** (calendário de feriados) para ciclo e prazo |
| A | Capacidade zero explícita em vez de 990%; mostrar desvio "estimado vs. real" por pessoa em horas |
| B | Tempo em status: separar primeira e última entrada em In Progress (regra do bscbi-web); excluir outliers > 30 d com opção |
| B | Percentis por tipo de issue (checklist, review, propagation) em vez de misturar |
| B | Previsão considerando entrada de itens novos (taxa de chegada) |

### 7.2 Cards novos (pessoas, planejamento, ausências)
| Prio | Novo card | O que responde | Dados |
|---|---|---|---|
| A | **Calendário do time** (semana/mês) | Quem está ausente quando; feriados; entregas com vencimento | ausências + feriados + duedate |
| A | **Cobertura por ausência** | Para cada ausência futura: issues da pessoa a reatribuir, horas a replanejar e quem pode cobrir (competência + folga) | ausências, fila, competências |
| A | **Plano das próximas 4–8 semanas por pessoa** | Carga planejada × capacidade por semana, incluindo as vencendo e as sem data | fila, capacidade |
| A | **Vencimentos da semana por pessoa** | O que vence em 7/14 dias e quem tem risco de atrasar (previsão pela capacidade) | duedate, previsão |
| A | **Pessoas sem capacidade para novos itens** (folga real, descontando ausência) | A quem dá para distribuir agora | capacidade, WIP |
| B | **Solicitação e aprovação de ausências** com impacto estimado (atrasos causados se aprovar) | Decisão informada | ausências, previsão |
| B | **Cobertura de competências** (matriz competência × pessoa, risco de pessoa única) | Dependência de uma só pessoa | competências do YAML/labels |
| B | **Carga por tipo de trabalho e versão de OS** (checklist, review, propagation, feature check) | Onde o esforço do TG-Basic se concentra | issuetype, OS Version, Checklist Type |
| B | **Revisões pendentes por revisor** (fila de Checklist Review) | Gargalo de revisão e SLA | par checklist × review |
| B | **Tendência de ocupação** (série por semana/pessoa, últimas 12) | Sobrecarga recorrente × pontual | histórico de carga |
| C | **1:1 / apoio** (resumo por pessoa sem ranking: fila, riscos, ausências, estourou estimativa) | Conversa de apoio com contexto | MP + RI |
| C | **Planejamento de férias** (janelas com menor impacto no time) | Sugerir períodos | capacidade, vencimentos |
| C | **Meta × realizado por mês** (metas de prazo, entregas, antecipação) com projeção | Acompanhar metas | MT |
| C | **Saúde do squad** (índice composto de fluxo; sem ranking de pessoas) | Visão rápida | VG |

Mantém a decisão de privacidade já tomada: sem ranking individual; tabelas de pessoas em ordem alfabética; ausências sem motivo detalhado.

## 8. Plano de validação e recriação

1. **Congelar a especificação:** transformar cada ID acima em ficha (entrada, fórmula, critérios de aceite, exemplo numérico). Usar as fichas como testes de aceite no Kotlin.
2. **Casos de referência:** reaproveitar os dados do modo demonstração e a suíte de ~600 testes como oráculo (mesmas entradas, mesmos números) antes de aposentar o Python.
3. **Ordem de recriação:** (a) cálculos transversais da seção 3; (b) fatia vertical **Tempo em status (TS)**; (c) Abertas, Metas, Fechadas; (d) Capacidade e Carga; (e) Previsão, Riscos, Negativas; (f) Checklist × Review, Integridade; (g) Distribuição e Rebalanceamento; (h) Governança.
4. **Decisões a tomar por card** (levar para o spec): manter / ajustar / remover / novo; fórmula validada com o gestor; fonte dos dados (Jira ou YAML).
5. **Validação com Jira real** só depois da sua autorização expressa, por pesquisa, seguindo o checklist da 1ª conexão.
