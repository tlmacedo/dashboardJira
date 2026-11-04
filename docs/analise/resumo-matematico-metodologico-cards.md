# Resumo matemático, metodológico e procedural dos cards

**Para quem é:** outra IA (ex.: ClineSR) que vai recriar ou adaptar os cards a outro conjunto de campos, filtros e buscas do Jira, sem perder a estrutura, a finalidade e a correção dos cálculos.
**O que é:** para cada card, (1) a decisão gerencial que ele sustenta, (2) as entradas, (3) a matemática, (4) o procedimento passo a passo, (5) o que adaptar e o que é invariante, (6) o que ele permite prever, auditar e localizar.
**Como foi feito:** as fórmulas foram **conferidas linha a linha** no código legado (`legacy/backend/jiradash/metrics/*.py`, `analytics.py`, `reports.py`, `distribution/engine.py`) e no Kotlin já escrito (`TimeInStatusCalculator`, `BusinessCalendar`, `Percentiles`, `auditoria/*`). Nenhuma consulta ao Jira foi feita (a flag de acesso ao Jira está desligada). O que vem só do manual/relatório e não foi reconferido está marcado **[manual]**. Divergências entre o relatório anterior e o código estão na seção 9.
**Precedência:** onde uma spec de fase (`docs/superpowers/specs/`) decidir diferente, a spec vence; este documento descreve a **lógica de referência**.

Documentos irmãos: inventário de cards em [relatorio-cards-app-atual.md](relatorio-cards-app-atual.md); diferenças legado × Kotlin do Tempo em status em [diferencas-esperadas-tempo-em-status.md](diferencas-esperadas-tempo-em-status.md); contrato com o Jira em [../jira/matriz-compatibilidade.md](../jira/matriz-compatibilidade.md).

---

## 0. Como ler (notação)

- `t` = instante; `d` = data civil no fuso **America/Manaus**; `hoje` = data civil de agora.
- `P_k(X)` = percentil k de X por **interpolação linear**: ordene X, posição `k/100 × (n−1)`, interpole entre vizinhos. Vazio → `nulo` (nunca 0).
- `[a ≤ x]` = indicador (1 se verdadeiro, 0 se falso). `n` = tamanho da amostra.
- **Categoria de status** `cat(s) ∈ {todo, active, wait, blocked, done}` vem do mapa de status (config). `INICIADA = {active, wait, blocked}`.
- Dias: **útil** (seg–sex, exceto feriados do calendário) é o padrão do app novo; **corrido** é opção. O legado só tinha corrido.
- Nada de ranking de pessoas: toda tabela de pessoa em ordem alfabética.

---

## 1. Modelo canônico de dados (o que toda adaptação precisa fornecer)

Os cards **não conhecem o Jira**: leem este modelo. Adaptar a outro projeto/campos é só preencher este modelo; se o modelo estiver correto, os cálculos continuam válidos.

### 1.1 Entidade `Issue`

| Campo canônico | Tipo | Campo Jira (DC) | Obrigatório | Observação de adaptação |
|---|---|---|---|---|
| `key` | texto | `key` | sim | Ordenação “natural” (TG-9 < TG-10) |
| `resumo` | texto | `summary` | sim | |
| `projeto` | texto | `project` | sim | |
| `tipo` | texto | `issuetype` | sim | Define papel: task, review, épico (ver 1.4) |
| `prioridade` | texto | `priority` | cards de risco/fila | Mapear nomes para ordem 0–4 (`PRIO`) |
| `status` | texto | `status` | sim | |
| `categoria` | enum | derivada de `status` via **mapa de status** | sim | Mapa completo obrigatório (status sem mapa = erro explícito, não categoria nula) |
| `resolucao` | texto? | `resolution` | sim | |
| `responsavel` | username? | `assignee` | sim | `id` da pessoa = username Jira |
| `reporter` | username? | `reporter` | auditoria | |
| `criada` | instante | `created` | sim | |
| `atualizada` | instante | `updated` | negligenciadas, sync | |
| `resolvida` | instante? | `resolutiondate` | fechadas/metas | |
| `vence` | data? | `duedate` | prazo | Data sem hora. Formato pode variar (`yyyy-MM-dd`, `dd/MMM/yy`) |
| `est_orig_s` | seg? | `timeoriginalestimate` | carga/risco | **Não está nos campos mínimos do sync atual; incluir** |
| `est_rest_s` | seg? | `timeestimate` | carga | idem |
| `gasto_s` | seg | `timespent` | carga/risco | idem |
| `pontos` | num? | campo “Story Points” (por nome) | fallback de horas | Pode não existir no TG-Basic |
| `labels` | lista | `labels` | atraso justificado, competências | |
| `componentes` | lista | `components` | afinidade na distribuição | |
| `epico` | chave? | “Epic Link” (por nome) ou `parent` | épicos | **Epic fora do sync** (decisão 25/10): ver 1.4 |
| `sinalizada` | bool | “Flagged” (por nome) | bloqueio | |
| `pai` | chave? | `parent` | legado: par Checklist×Review | **No app novo o par vem de `issuelinks`** |
| `links` | lista | `issuelinks` | par Task↔Review | tipo `Blocks`, direção nas duas pontas |
| campos TG | texto | OS Version, SKU Type, Checklist Type, Model Name/Category, TG Request Type, Carrier Code | filtros/agrupamentos | `customId` descoberto na 1ª conexão; pedir por id quando conhecido |

### 1.2 Eventos (changelog) — **a fonte de quase todo o fluxo**

Lista ordenada por `t` de itens `{campo, de, para, autor}`. Campos lidos:

| Campo do changelog | Uso |
|---|---|
| `status` | Transições: tempo em status, ciclo, idade, reabertura, devolução, CFD, integridade |
| `assignee` | Intervalos de responsável: posse do tempo, trocas, auditoria, passa-bola |
| `duedate` | Prorrogações, vencimento original, auditoria |
| `labels` | Label tardia/removida, atraso justificado, auditoria |
| `timeoriginalestimate` | Estimativa aumentada (integridade) |
| `resolution`, `priority`, “Flagged”, “Link” | Qualidade, bloqueio, vínculos |

Armadilhas já mapeadas (não validadas no servidor): `from/to` do assignee pode vir como chave interna (`JIRAUSER*`) em vez de username; changelog em `expand=changelog` pode truncar; autor pode ser nulo.

### 1.3 Entidades de configuração (não vêm do Jira)

`Pessoa{id, nome, papel(es), squad, vigência}`, `Ausência{pessoa, ini, fim, fração_do_dia}`, `Feriado{data}`, `PessoaCap{horas_semanais=40, foco=0,7, alocação=1,0}`, mapa de status, ordem do fluxo (`workflow_order`), limite de WIP por status, resoluções negativas, `labels_atraso` (justificativas e tardias), metas (`on_time_pct=90`, `early_pct=35`), tipos de review/checklist, competências (`skill:xxx`).

### 1.4 Papéis dos tipos de issue (decisão de adaptação nº 1)

- **Task/Checklist** (unidade de entrega), **Review** (`Checklist Review`), **Épico** (agregador).
- No TG-Basic: tipos espelhados = Checklist, Checklist TG, Checklist Review, Feature check, TG Feature check, Task. **Epic não é espelhado** → o card **Épicos (EP-01)** e a verificação “sem épico” só funcionam se o épico vier por outro caminho (Epic Link/`parent`) ou forem removidos. Decidir antes de recriar.
- O par Task↔Review no app novo é por **link `Blocks`** (nas duas direções), não por `parent`. A configuração mora em `vinculos.yaml` (`tipoDeLink`, `tiposReview`, `tiposTask`).

---

## 2. Primitivas matemáticas (definidas uma vez, usadas por todos os cards)

Cada card abaixo cita estas pelo código `Pn`. **Alterar uma primitiva muda todos os cards que a usam**; por isso são o primeiro alvo de teste.

**P1 – Concluída / aberta.** `concluída ⇔ cat(status)=done ∧ resolucao≠∅ ∧ resolvida≠∅`. `aberta ⇔ cat(status)≠done`. Concluída por categoria **sem** resolution = divergência → vai para Qualidade dos dados e **fica fora das médias** (Kotlin). O legado usava só a categoria.
*Resolução positiva:* `resolucao ∉ NEGATIVAS` (Cancelado, Duplicado, Won't Do). Entrega = concluída com resolução positiva. Cancelamento por **status** (Cancelled/Canceled/Dropped) é tratado no filtro de exclusão da ficha.

**P2 – Percentil.** Interpolação linear (igual a `numpy.percentile`): `pos = p·(n−1)`, `v = x[⌊pos⌋] + (x[⌈pos⌉]−x[⌊pos⌋])·frac(pos)`. Mediana = P50.

**P3 – Duração entre instantes** `dur(t1,t2,modo)`:
- `corrido`: `(t2−t1)/24h`.
- `útil`: `Σ_{d dia útil local em [t1,t2]} fração do dia coberta` (fração = ms cobertos ÷ ms do dia). Fim de semana e feriado valem 0; feriado em fim de semana não desconta duas vezes. Intervalo invertido é erro.

**P4 – Linha do tempo de status.** Dado `criada`, transições ordenadas `(t_i, de_i, para_i)` e `fim = resolvida` (se concluída) ou `agora`: intervalos `[criada,t_1]` em `de_1`; `[t_i,t_{i+1}]` em `para_i`; `[t_n, fim]` em `para_n`. Sem transições: um intervalo no status atual. Cada intervalo é partido nos pontos de **troca de responsável**, herdando o responsável vigente (posse do tempo). Tempo por status = soma dos intervalos do status (**reentradas somam**).

**P5 – Início do trabalho.** `início = t da 1ª transição cujo destino ∈ INICIADA`. (Legado.) *Variante bscbi-web:* 1ª entrada em “In Progress” e **última** saída; decidir uma e documentar.

**P6 – Ciclo, lead, idade.**
`ciclo = dur(início, resolvida)` · `lead = dur(criada, resolvida)` · `idade(aberta) = dur(início ou criada, agora)`.
O legado mede em dias **corridos**; o novo permite útil. Mantenha a mesma unidade ao comparar P50/P85 históricos com idades atuais.

**P7 – Tempo no status atual.** `parada_dias = dur(t_última_transição ou criada, agora)` (permanência **corrente**; reentrada anterior não soma).

**P8 – Reabertura e devolução.**
`reabertura = #{transições com cat(de)=done ∧ cat(para)≠done}`.
`devolução = #{transições entre status da workflow_order com índice(para) < índice(de) ∧ cat(de)≠done}`.
`trocas_resp = #{mudanças de assignee com de≠∅ ∧ para≠∅ ∧ de≠para}`.

**P9 – Throughput semanal.** `T_w = #{entregas (P1 positiva) com resolvida na semana w}`, semana seg–dom. Janela: 12 semanas (Visão/Fechadas), 16 (Previsão/Abertas). *Atenção:* o legado agrupa pela data **UTC** de `resolvida` (ver seção 9, item 2).

**P10 – Horas de uma issue aberta.**
`h = est_rest_s/3600` se existir; senão `max(0,5; (est_orig_s − gasto_s)/3600)` se `est_orig_s`; senão `pontos × 4`; senão `6`. (Constantes configuráveis: 4 h/ponto, 6 h padrão, mínimo 0,5 h.) **É a premissa mais frágil do planejamento** (seção 9).

**P11 – Capacidade líquida.**
`cap_dia(p,d) = [d útil] · (horas_semanais/5) · foco · alocação · (1 − aus(p,d))`, `aus(p,d)=max fração das ausências que cobrem d`.
`cap_semana = Σ_{d da semana, d ≥ hoje} cap_dia`. (A semana corrente só conta de hoje em diante.)
Exemplo: 40 h, foco 0,7, alocação 1 → 5,6 h/dia → 28 h/semana; meio dia de ausência no dia = 2,8 h.

**P12 – Fila e agenda por capacidade.** Fila da pessoa ordenada por `(não iniciada?, prioridade, vence ou +∞, criada)` (iniciadas primeiro). `schedule` preenche as horas semanais na ordem, consumindo `h` de cada issue; a data prevista cai proporcionalmente dentro da semana em que a issue termina (`offset = min(4, ⌈4·usado/total⌉)`, avança para dia útil). Fora do horizonte → `nulo` (“além do horizonte”).

**P13 – Demanda semanal.** Para cada issue aberta da pessoa: `última = clamp((vence − seg_semana_0).dias ÷ 7, 1, nº_semanas−1)`; sem `vence`: `1` se iniciada, `3` se não. `demanda[w] += h/(última+1)` para `w=0..última` (**espalhamento uniforme**, mínimo 2 semanas). Vencida (diferença negativa) cai no mínimo: 2 semanas.
Exemplo: h=12, vence em 2 semanas → `última=2` → 4 h nas semanas 0,1,2.

**P14 – Ocupação.** `ocup_4s = Σ_{w<4} demanda[w] ÷ Σ_{w<4} cap_semana[w]` se `cap > 0,5 h`; senão `9,9` se há demanda, `0` se não (**sentinela 990%**: tratar como “sem capacidade”, nunca como número). Faixas: ≤ 0,85 verde; ≤ 1,10 laranja; > 1,10 vermelho.

**P15 – Equidade (Jain).** Para as pessoas com `cap_4s > 0,5`: `x_i = min(ocup_i, 3)`; `J = (Σx)² ÷ (n·Σx²)`; `1` = perfeitamente igual; nulo se `Σx²=0`.
Exemplo: x=[0,5; 1,0; 1,5] → 9 ÷ (3·3,5) = **0,857**.

**P16 – Monte Carlo “quando termina”.** Entradas: série `H` de throughput semanal (≥1 semana) e `R` = itens abertos. 10.000 simulações; em cada uma sorteia com reposição uma semana de `H` por semana de calendário até `Σ ≥ R` (teto 104 semanas). Saída: P50/P85/P95 das semanas. Confiança “baixa” se `|H| < 8`. `R ≤ 0` → 0 semanas; `ΣH=0` → “sem dados”. Semente fixa (42) → determinístico e testável. **Premissas:** ritmo recente persiste; **nenhum item novo entra**.
**“Quantos até N semanas”:** soma de N sorteios; reporta **percentis conservadores**: “P85” = 15º percentil dos totais, “P95” = 5º.

**P17 – Previsão condicional por idade.** Dos ciclos históricos `C`, os `L = {c ∈ C : c > idade}`; se `|L| < 5` → sem previsão; senão `restante = c − idade`, reporta P50/P85 (data = hoje + restante).

**P18 – Risco (0–100, explicável).** Soma de fatores, teto 100. Cada fator guarda o motivo textual.

| Fator | Pontos | Condição |
|---|---|---|
| Idade | +30 | idade > P85 do ciclo histórico (16 sem.); senão +10 se > P50 |
| Parada | +15 | `parada_dias > max(2; 2×mediana do status)` |
| Prazo | +25 | vencida; senão +20 se previsão-por-capacidade > vence; senão +8 se vence em ≤ 3 d |
| Bloqueio | +15 | cat=blocked ∨ sinalizada |
| Reabertura | +10 | `reabertura ≥ 1` |
| Trocas | +10 | `trocas_resp ≥ 3` |
| Estouro | +15 | `gasto > 1,25 × est_orig` |
| Sem atualização | +10 | `agora − atualizada > 7 d` |
| Sem estimativa | +5 | sem `est_orig` e sem `pontos` |

Níveis: `alto ≥ 60`, `médio 30–59`, `baixo < 30`. Exemplo: idade>P85 (30) + vencida (25) + sem atualização (10) = **65 → alto**. Pesos são **heurísticos**: expor como configuráveis e calibrar com histórico.

**P19 – Prazo (Δ).** `Δ = d(resolvida local) − vence` em dias de calendário. “No prazo” ⇔ `Δ ≤ 0`. Baldes: `Δ≤−3` “D-3 ou antes”; `−2` D-2; `−1` D-1; `0` “No dia”; `1..3` “Atrasada 1–3 d”; `>3` “Atrasada > 3 d”. **Vencimento original** = `de` do 1º evento de `duedate` (ou o 1º `para`, ou o `vence` atual se não houve evento).
**Atraso justificado:** label ∈ `labels_atraso.justificativas` (prefixo `*` só no fim, sem diferenciar caixa). Duas métricas lado a lado: “no prazo” e “no prazo ajustado” (atraso com justificativa conta como no prazo).

**P20 – Score de distribuição (menor vence).**
`cap_ef = max(0,1; cap_4s × clamp(share; 0,85; 1,15))`
`score = 1,0·(carga + h_nova)/cap_ef + 0,5·(wip/limite) + 1,0·[wip ≥ limite] + 0,6·#competências_faltantes − 0,15·[já trabalhou no épico/componente]`
`share = (1−s)·1 + s·(entregas_12sem_da_pessoa ÷ média_do_squad)`, `s = min(1, entregas_totais/40)` (poucos dados puxam para neutro). Elegível ⇔ `cap_4s > 0,5 h`. Desempate: quem recebeu há mais tempo, depois id. Guloso: itens do **maior para o menor**, recalculando a carga após cada atribuição.
Exemplo: carga 20 h + nova 4 h, cap 40 h, share 1, wip 1, limite 3 → 0,60 + 0,167 = **0,767**.

**P21 – Rebalanceamento.** Gatilho `ocup > 1,10` (4 semanas). Só issues **não iniciadas**; destino = melhor score elegível **sem competência faltante**; só move se `ocup_origem − ocup_destino_após ≥ 0,20` (histerese); até 10 movimentos; para quando ninguém passa de 1,10 ou nenhum movimento é válido.

---

## 3. Fichas dos cards

Formato de cada ficha: **Decisão** (para que serve) · **Universo/busca** · **Matemática** · **Procedimento** · **Adaptar** · **Detecta** (prever/auditar/localizar). Cards em ordem das telas do relatório. IDs entre parênteses (VG-01…) referem-se ao relatório.

Convenção de **busca de referência** (JQL do universo, sempre gerada pelo construtor, com aspas duplas nos valores, nunca texto livre; nada abaixo foi validado no servidor da empresa):
- Base: `project = "<P>" AND issuetype in ("<t1>","<t2>",…)`.
- Abertas: `… AND statusCategory != Done`.
- Fechadas no período `[de, até]` (até inclusivo): `… AND resolved >= "<de>" AND resolved < "<até+1>"` (+ `resolution` preenchida por conferência local).
- Squad/pessoa: `assignee in ("u1","u2")`. OS Version: `"OS Version" in ("…")`.
- Incremental: `updated >= "yyyy-MM-dd HH:mm" ORDER BY updated ASC, key ASC` + `expand=changelog` (fuso do **servidor** Jira).
- Contagem barata: `maxResults=1` e ler `total` (conferência, nunca como valor de card sem validar).

### 3.1 Visão geral (VG)

**Decisão:** onde olhar primeiro hoje; o time está entregando no ritmo e dentro do prazo?
**Universo:** abertas + entregas dos últimos 60 dias (30 atuais + 30 anteriores).

| Card | Matemática | Procedimento / adaptação |
|---|---|---|
| VG-01 Abertas | `#aberta` (P1); “em andamento” = cat ∈ INICIADA | Depende do mapa de status completo |
| VG-02 Concluídas 30 d | `N30 = #entregas em [hoje−29, hoje]`; variação `(N30−N60)/N60·100` (nula se `N60=0`) | Janela por data **local** (ver seção 9.2) |
| VG-03 Ciclo P50 (P85) | P2 sobre ciclos (P6) das entregas dos 30 d; seta invertida (menor é melhor) | Unidade (útil/corrido) deve ser a mesma do histórico |
| VG-04 No prazo 30 d | `#[Δ≤0] ÷ #com vence`, entre entregas dos 30 d. Verde ≥ 80% | Adicionar “ajustado” por label (P19) |
| VG-05 Vencidas | `#aberta ∧ vence < hoje` | |
| VG-06 Negligenciadas | `#aberta ∧ iniciada ∧ (agora−atualizada) > 7 d` | Limite configurável |
| VG-07 Bloqueadas | `#aberta ∧ (cat=blocked ∨ sinalizada)` | Exige campo “Flagged” ou status de bloqueio mapeado |
| VG-08 Risco alto (médio) | contagem por nível de P18 | Depende de ciclo histórico do squad |
| VG-09 Central de atenção (13 itens) | Agrega outras telas (vencidas; risco alto; pessoas > 110%; atrasos previstos; negligenciadas; sem responsável; bloqueadas; ausências em 14 d; pessoas < 50%; meta de prazo; pares com alerta; indício alto; sem estimativa > 20%) | Cada item aponta a tela de origem; “negligenciadas” une *sem atualização* ∪ *paradas no status* **sem dupla contagem** (por chave) |
| VG-10 Entregas por semana | P9, 12 semanas | |
| VG-11 Quando o backlog termina | P16 com `R = #abertas` e `H` = 12 semanas | Rotular premissas; confiança baixa com < 8 semanas |
| VG-12 Cartão por squad | `ocup_média = mean(ocup_4s)`, `J` (P15), abertas, concluídas 30 d, vencidas, risco alto, série de throughput | Faixas P14 |

**Detecta:** deterioração de prazo (VG-04↓ com VG-05↑), acúmulo (VG-01↑ com VG-02↓), sobrecarga (VG-12), perda de ritmo (VG-10).

### 3.2 Meu painel (MP) — exige Pessoa

MP-01 abertas da pessoa; MP-02 `ocup_4s` (P14); MP-03 `#risco ≥ 30`; MP-04 ausências com `fim ≥ hoje`; MP-05 fila ordenada por idade com previsão (P12) e risco (P18).
**Adaptar:** `pessoa` = username Jira; visibilidade por papel (o usuário vê só o próprio). **Detecta:** o que a própria pessoa precisa reordenar.

### 3.3 Abertas (AB)

**Decisão:** o que está envelhecendo e onde o fluxo acumula.
**Universo:** abertas do squad/projeto. **Referência histórica** (AB-02/03): ciclo das entregas das últimas **16 semanas** do squad/projeto, **sem** filtro de pessoa/tipo/prioridade (evita referência instável).

- AB-01 `#abertas`. AB-02/03: `P50` e `P85` do ciclo histórico. AB-04: `#iniciada ∧ idade > P85`.
- AB-05 por status (ordem do fluxo, cor por categoria). AB-06 por prioridade (ordem `PRIO`).
- AB-07 idade em baldes: `0–7`, `8–14`, `15–30`, `>30` dias.
- AB-08 vencimento: `vencidas`, `vence ≤ 7 d`, `depois`, `sem vencimento`.
- AB-09 tabela (ordenável, CSV): chave, status, responsável, idade, no status (dias), estimado/gasto, previsão por capacidade, risco e fatores.

**Adaptar:** `workflow_order` e mapa de status; prioridades do projeto. **Detecta:** itens acima do P85 (candidatos a atraso), acúmulo por etapa, abertas sem vencimento.

### 3.4 Épicos (EP) — ver decisão 1.4

Por épico: `itens` (exclui Review), `concluidos` (P1 positiva), `progresso = concluídos/itens`, `mais_antigo = max idade das abertas`, `vencidos`, `pontos`, `previsão_semanas = min(52, abertos ÷ (throughput_médio_semanal × max(participação; 0,02)))`, `participação = itens_do_épico/itens_totais`.
**Aviso de método:** estimativa **grosseira** (rateia o ritmo do time pela participação do épico). Só vale com ≥ 8 semanas de throughput. **Adaptar:** fonte do épico (Epic Link/`parent`); com Epic fora do sync, remover ou reabastecer.

### 3.5 Negligenciadas (NG)

**Decisão:** o que ninguém está olhando. Listas até 100.
NG-01 sem atualização: `iniciada ∧ idle > 7 d` · NG-02 sem responsável · NG-03 vencidas · NG-04 bloqueadas · NG-05 **paradas no status**: `iniciada ∧ parada_dias > max(3; 2 × mediana_do_status_16sem)` · NG-06 “a fazer” há `> 30 d` (cat=todo, idade desde `criada`).
**Mediana do status** = P50 do tempo total em cada status entre as entregas das últimas 16 semanas (P4), em dias.
**Adaptar:** limites 7/3/2×/30; se o fluxo for curto (checklists), reduzir. **Detecta:** trabalho esquecido; **atenção:** três limiares diferentes de “parada” coexistem (seção 9.3).

### 3.6 Riscos (RI)

RI-01 contagens alto/médio; RI-02 tabela `score ≥ 30` até 200 (fator principal, gaveta com pontos); RI-03 sinais por pessoa (contagem de issues em risco e de fatores *idade*, *parada*, *estouro*; alfabético).
**Matemática:** P18. **Uso:** conversa e apoio, **não** avaliação de pessoa. **Adaptar:** pesos/limiares configuráveis; calibrar contra atrasos reais (taxa de acerto do nível “alto”). **Detecta:** onde agir antes do vencimento.

### 3.7 Previsão (PV)

PV-01 backlog = `#abertas`. PV-02 “termina em” P50/P85/P95 (P16, 16 semanas); data = `hoje + semanas`. PV-03 confiança. PV-04 série semanal. PV-05 “quantos em 2/4/8 semanas” (percentis conservadores de P16). PV-06 por issue iniciada: P17 (`restante`, `n`), previsão por capacidade (P12), e **“Atrasa?” = vence ∧ (previsão_capacidade > vence ∨ data_P85 > vence)**.
**Prevê:** término do backlog e risco individual de atraso. **Limites:** não modela chegada de itens novos; `n<5` desliga a previsão por issue. **Adaptar:** escolher a janela de throughput por estabilidade do squad; opcionalmente incluir taxa de chegada `λ` (melhoria B).

### 3.8 Fechadas (FE)

**Universo:** `resolved` em `[de,até]` (padrão últimos 30 d); entregas = P1 positiva.
FE-01 `#entregas`; FE-02 `Σ pontos`; FE-03 ciclo P50/P85/P95/média (P6); FE-04 lead P50/P85; FE-05 no prazo = `#[Δ≤0]/#com vence`; FE-06 canceladas/descartadas = fechadas − entregas; FE-07 throughput; FE-08 dispersão (dispersão ciclo × dia); FE-09/10 por tipo e projeto; FE-11 por pessoa; FE-12 tabela.
**FE-11 por pessoa:** `finalizou` (era o responsável final), `participou` (teve tempo ativo — P4 restrito a cat=active, cortado por intervalo de responsável), `pontos`, ciclo P50/P85 só das que finalizou, `tempo_ativo_h`, `estimado_h`, `gasto_h`. **Alfabético, sem ranking.**
**Razão real÷estimado** `= gasto_s / est_orig_s` (nula sem estimativa ou sem horas).
**Detecta:** previsibilidade (FE-08 largo = processo instável), estimativa sistematicamente curta, entrega concentrada em poucas pessoas (participou × finalizou).

### 3.9 Negativas (NE)

**Janela padrão:** 90 dias. Conjuntos:
- Reabertas: `reabertura ≥ 1 ∧ atualizada ≥ início` · Devolvidas: `devolução ≥ 1 ∧ atualizada ≥ início` · Muitas trocas: `trocas_resp ≥ 3 ∧ atualizada ≥ início`.
- Canceladas/descartadas: fechadas no período com resolução negativa · Concluídas com atraso: entrega com `Δ>0` · Abertas vencidas · Estouro: `gasto/est_orig > 1,25` (abertas, ou fechadas no período).
- **Taxas** (denominador `max(1, #entregas do período)`): `reabertura%`, `devolução%`, `atraso%`; vermelho > 20%.
**Atenção:** reabertas/devolvidas/trocas usam `atualizada ≥ início` como aproximação do período; evento antigo de issue atualizada depois **sai** da janela (viés; seção 9.4). **Melhoria:** filtrar pela **data do evento** do changelog.
**Detecta:** retrabalho (reabertura/devolução), qualidade de entrega, instabilidade de responsável.

### 3.10 Tempo em status (TS) — implementado em Kotlin

**Decisão:** onde o tempo é gasto e onde o fluxo para.
**Universo:** projeto TG-Basic, tipos Checklist/Checklist TG/Checklist Review, exclui status Cancelled/Canceled/Dropped; analisadas = concluídas (P1) com `resolvida` no período (padrão 90 d); filtros squad (assignee in), OS Version opcional.
**Matemática:**
- Por task, `T_s = Σ dur(intervalos no status s)` (P3, P4), **excluindo cat=done**. Por status: `mediana_s = P50{T_s}`, `P85_s`, `n`.
- `eficiência de fluxo = Σ_{cat=active} T ÷ Σ_{todos não-done} T`; nula sem dados. (Exemplo TG-1: corrido 3/7 = 0,4286; útil 3,0/4,667 = 0,6429.)
- Mapa pessoa × status: mediana de `T` por (pessoa, status), com **posse do tempo repartida por responsável vigente** (legado atribuía tudo ao último).
- **Parada além do normal** (abertas): `parada_dias > max(2; 1,5 × mediana_s)` (P7).
- Descartadas por dado inconsistente: `resolvida < criada` ou `criada > agora` (contadas em `ignoredInconsistent`).
- Opcional: `excludeOutlierDays` (ciclo corrido > N dias sai; bscbi-web usa 30).
**Adaptar:** mapa de status **completo** (status fora do mapa = erro); calendário de feriados; lista de status ativos. **Detecta:** etapas de espera longas, pessoa×etapa com tempo anômalo, issues paradas; separa fim de semana de espera real (modo útil).

### 3.11 Metas e prazos (MT)

**Decisão:** o time cumpre os prazos e com qual folga; quem puxa a média para baixo.
**Universo:** entregas (P1 positiva) com `vence` no período (padrão 90 d), **exclui Epic**. `Δ` por P19 (fuso local). `tot = #com vence`.
- MT-01 `no_prazo% = 100·#[Δ≤0]/tot` (meta 90). MT-02 igual, mas com o **vencimento original** (laranja se ≥ 5 pp menor que MT-01: sinal de prorrogação “maquiando” prazo).
- MT-03 antecipadas `= #[Δ≤−1]/tot` (meta 35). MT-04 `(D-1+D-2)/tot`. MT-05 `D/tot`. MT-06 atrasadas `= (1–3 d + >3 d)/tot`. MT-07 sem vencimento = entregas − tot.
- MT-08/09 série por período (dia/semana/mês) e `% no prazo = (D-3…D)/total`; MT-10 distribuição nos 6 baldes.
- MT-11…14 por tipo, prioridade, squad, pessoa: pílula `✓ ≥ meta`, `● até 10 pp abaixo`, `▲ abaixo`; **queda_data_original = MT-01 − MT-02** por pessoa.
**Adaptar:** (a) atraso justificado (P19) — **a maior lacuna do legado**; (b) unidade: o “D” do prazo é em dias de calendário; avaliar tolerância de dia inteiro e dias úteis (um prazo numa sexta cumprido na segunda); (c) metas em config.
**Detecta:** entrega no “último minuto”, dependência de prorrogação (MT-02 ≪ MT-01), tipos/squads sistematicamente atrasados.

### 3.12 Checklist × Review (CR)

**Decisão:** a revisão está funcionando como controle independente ou virou formalidade?
**Pareamento (legado):** `parent` do review = checklist. **App novo:** link `Blocks` nas duas direções (`vinculos.yaml`). Checklists sem review = órfãos. Filtros aplicam ao **checklist**.
**Por par** `(P=checklist, R=review)`:
- `atraso_criação_review_h = (R.criada − P.criada)` em horas.
- `ciclo_review`, `ciclo_checklist` (P6); `review_pct_do_ciclo = 100·ciclo_R/ciclo_P`.
- `review_ativa_h = Σ tempo em cat=active da review` (P4).
- `dif_vencimento = R.vence − P.vence` (dias); `review_antes_do_pai = P.resolvida − R.resolvida` (dias).
- Reaberturas/devoluções do checklist (P8); labels comuns / só no checklist / só na review (exclui label técnica “review”).
- **Alertas do par:** review vence depois do checklist (`dif>0`) · checklist fechado antes da review (`P.resolvida ∧ (¬R.resolvida ∨ R.resolvida > P.resolvida)`) · mesma pessoa nos dois · review ativa `< 0,5 h` com pai reaberto · review vencida (aberta e `vence<hoje`) · review atrasada (fechada depois do `vence`).
**Cards:** CR-01 pares; CR-02 órfãos; CR-03 ciclo review P50; CR-04 `review_pct_do_ciclo` mediana; CR-05 criação da review após o pai (P50 h); CR-06 `% dif>0` (laranja > 10%); CR-07 `% mesma pessoa` (laranja > 10%); CR-08 `% pares com alerta` (vermelho > 30%); CR-09 tabela/comparação lado a lado; CR-10 aprendizados (frases só com amostra mínima, `n≥5`); CR-11 reabertura/devolução/atraso por **faixa de duração ativa da review** (`<1 h`, `1–4`, `4–12`, `≥12`); CR-12 quem atrasa quem (contingência: `P(review atrasada | checklist atrasado)` vs `P(review no prazo | checklist atrasado)`); CR-13 labels × resultado com **lift** `= (reabertura% com a label) ÷ (reabertura% média)`, “n baixo” se `n<5`; CR-14 diferenças de labels.
**Adaptar:** tipos de review/checklist e o tipo de vínculo; sem independência (mesma pessoa) não há controle. **Detecta:** revisão pro-forma (curta + pai reaberto), ordem de fechamento invertida, prazos da review incompatíveis com o do pai, labels de risco.

### 3.13 Integridade (IN) — sensível (LGPD), só gerente, todo acesso auditado

**Decisão:** há indício de manipulação de datas, estimativas ou fechamentos? **São indícios para revisão humana, não prova de má-fé nem nota de desempenho.**
**Sinais (gravidade):** 3 — *prazo resgatado* (pela data original estaria atrasada; prorrogação a fez parecer no prazo: `resolvida_local > antigo ∧ ≤ novo`), *apropriação* (troca de responsável ≤ 2 h antes de concluir e o novo responsável é quem concluiu), *fechamento instantâneo* (`resolvida−criada < 15 min`), *reabre e fecha* (reaberta e refechada < 2 h, e a reabertura < 2 h após o fechamento anterior), *fechamento em lote* (≥ 6 conclusões do mesmo autor em janela de 10 min). 2 — *prazo prorrogado* (novo>antigo, no dia ≥ antigo−2 d), *vencimento definido tarde* (definido a > 70% do ciclo), *pulou o fluxo* (nunca iniciou e sem horas), *estimativa aumentada* (≥ +30% depois de iniciar), *label de criticidade removida* (urgente/sla/regressão, atrasada ou < 3 d do fim). 1 — *fechada por outro* (autor ≠ responsável, sem troca para ele e não gerente), *último minuto* (entre 22 h e 24 h do dia do vencimento).
**Nível por pessoa:** `pontos = Σ gravidade`; `taxa = pontos ÷ max(concluídas, 8)`; `mediana = P50(taxas)`; `base = max(mediana; 0,05)`; `razão = taxa/base`. **Alto:** `pontos ≥ 12 ∧ razão ≥ 3 ∧ ≥ 3 sinais graves (gravidade 3)`. **Médio:** `pontos ≥ 6 ∧ razão ≥ 2`. Exemplo: 12 pts, 5 concluídas → taxa 1,5; mediana 0,4 → razão 3,75.
**Cards:** IN-01 evidências; IN-02 pessoas alto/médio; IN-03 issues afetadas; IN-04 mediana da taxa; IN-05 evidências por sinal; IN-06 **impacto nas metas** `= no_prazo% − no_prazo_original%` (pp); IN-07 tabela de evidências (até 600, com autor, instante e detalhe); IN-08 catálogo.
**Adaptar:** limiares e gravidades em config (calibrar); fuso (“último minuto” depende dele); papéis autorizados. **Relação com Auditoria (AU):** AU (Kotlin) detecta o **ato** (quem pode mudar responsável/vencimento/label); IN agrega **padrões** por pessoa.

### 3.14 Auditoria de alterações (AU) — implementado em Kotlin

**Decisão:** quem alterou responsável, vencimento ou labels sem poder? **Regra:** podem alterar o *Reporter (PL)*, *Coordenador/Gerente* e o *Squad Leader do squad do responsável na data do evento*. O próprio responsável não se autoriza.
**Sinais (gravidade padrão):**
- `VENCIMENTO_ALTERADO_NAO_AUTORIZADO` (3): `de`≠`para`, ambos legíveis, autor não autorizado. Definir pela primeira vez (`de` nulo) **nunca** gera sinal.
- `VENCIMENTO_REMOVIDO` (3): `de` preenchido e `para` vazio, **qualquer autor**.
- `VENCIMENTO_TASK_REVIEW_DIVERGENTE` (1): vencimentos do par divergem.
- `RESPONSAVEL_TROCADO_NAO_AUTORIZADO` (3): troca real por autor não autorizado.
- `PASSA_BOLA` (3): ≥ `passaBolaTrocas` (3) trocas “perto do vencimento” (|distância| ≤ 2 d), ou devolver a quem já teve; um sinal por issue.
- `TROCA_PERTO_DO_VENCIMENTO` (2): troca não autorizada perto do vencimento.
- `LABEL_*` (2–3): label após fechamento (não autorizado / pelo responsável), label tardia com posse antecipada, label após vencimento.
- `CANCELAMENTO_SUSPEITO` (2): transição para Cancelled/Canceled/Dropped depois do vencimento ou por não autorizado.
**Matemática:** distância em dias corridos ou úteis (`unidade`), papéis avaliados na **data civil local do evento** (vigência `de/ate`). **Resultado:** `AUDITORIA` (indício) ou `ALERTA` (informativo).
**Adaptar:** `auditoria.yaml` (limiares, gravidades, status de cancelamento), `papeis.yaml`, `labels_atraso.yaml`. **Dependência crítica:** `from/to` do assignee e a chave de usuário do changelog (**não validado**: se vier `JIRAUSER*`, resolver antes de comparar).

### 3.15 Carga por pessoa (CP)

**Decisão:** quem está sobrecarregado ou ocioso (para redistribuir, não para ranquear).
**Por pessoa (alfabético):** abertas; WIP (`#cat ∈ INICIADA`); fila (h) `= Σ h` (P10); `cap_4s`; `carga_4s = Σ_{w<4} demanda` (P13); `ocup_4s` (P14); `ausência_4s (d)`; horas registradas 30 d (`Σ worklog.seconds` com `started ≥ agora−30 d`, **exige worklogs**); itens acima do P85; vencidas; risco alto.
CP-01 Jain (P15); CP-02 maior/menor ocupação.
**Adaptar:** horas semanais/foco/alocação por pessoa (YAML); incluir `worklog` na sincronização. **Detecta:** concentração de carga, itens críticos por pessoa.

### 3.16 Capacidade e ausências (CA)

**Decisão:** o plano das próximas semanas cabe na capacidade, descontadas ausências e feriados?
- CA-05 mapa 8 semanas: `ocup_w = demanda[w] ÷ cap_w` (se `cap_w>0,5`; senão 9,9/0), “aus.” quando a semana não tem capacidade.
- CA-06 alertas: **carga** (`ocup_4s > 1,10`: “demanda X h para Y h”) e **ausência** (`dias_ausente ≥ 2 ∧ carga_w > 0,5`: “~ dias × h/dia a replanejar”, `h/dia = horas_semanais/5 × foco`).
- CA-02/09 **atrasos previstos**: para abertas com `vence ≥ hoje`: `real = previsão pela capacidade com ausências`; `atrasa ⇔ real = ∅ ∨ real > vence`.
- CA-03 **por ausência**: recalcula o plano **sem ausências**; `por_ausência ⇔ sem_aus ≤ vence` (a ausência é a causa); senão a causa é carga.
- CA-07 ausências (sem motivo detalhado; dias úteis = dias do intervalo que são úteis); CA-08 feriados no horizonte; CA-04 Jain.
**Adaptar:** ausências e feriados em YAML; ordem da fila (P12); horizonte (`semanas+4`). **Prevê:** quais entregas vão estourar o prazo e se a causa é ausência ou carga — a base do planejamento de férias e cobertura.

### 3.17 Gargalos e fluxo (GA)

- GA-01 **Fluxo cumulativo (CFD)**, 60 dias: para cada dia `d`, `N_s(d) = #issues cujo status ao fim de d (último evento ≤ 23:59:59 de d) é s` (reconstruído do changelog).
- GA-02 por etapa aberta `s`: `itens` (hoje), `uso_limite = itens/limite_WIP`, `variação_15d = N_s(hoje) − N_s(hoje−15)`, mediana histórica. **Gargalo ⇔ `uso_limite > 1` ∨ (`variação_15d ≥ max(3; 0,3·max(N_s(hoje−15);1))` ∧ cat ∈ {wait, active})**.
- GA-03 pessoas com `ocup_4s > 1,10`; GA-04 folga: `0 < cap_4s ∧ ocup_4s < 0,5`.
**Adaptar:** limites de WIP por status (config; sem limite só vale o critério de crescimento); janela 60/15 d. **Detecta:** onde a fila cresce (espera de review, de teste, de aprovação), antes de virar atraso.

### 3.18 Distribuição (DI) e Rebalanceamento (RB)

**Decisão:** quem deve receber o trabalho novo ou ser aliviado, com justificativa auditável.
**DI — pendentes:** abertas com `cat=todo` e responsável vazio ou igual ao gerente do squad. Cada sugestão traz **ranking completo e fatores** (carga projetada, WIP, competência faltante, afinidade). Procedimento: P20 guloso → **equidade antes → depois** (Jain sobre `carga/cap`) → gerente **aprova / troca (motivo obrigatório) / rejeita**; tudo auditado. **Modo sombra:** nada é escrito no Jira por padrão (Fase 16 prevê escrita).
**RB:** P21; mostra Jain antes/depois e o motivo textual (“X projetado em N%; Y fica em M% após o movimento”).
**Adaptar:** competências (`skill:xxx` em labels ou YAML), limite de WIP por squad, constantes de P20 (todas calibráveis). Produtividade entra **só** como peso de capacidade limitado a 0,85–1,15 (não pune quem entrega mais). **Detecta:** desequilíbrio estrutural e dependência de competência única (ninguém elegível).

### 3.19 Governança

- **QD – Qualidade dos dados** (sobre abertas; verde ≤ 10%, laranja ≤ 30%): sem estimativa (`sem est_orig ∧ sem pontos`); sem responsável; sem vencimento; sem épico; em andamento sem horas (`gasto=0 ∧ criada há > 3 d`); sem squad; prioridade padrão (Medium/Média). `% = 100·#falha/#abertas`. **É o termômetro de confiança de todos os outros cards**, em especial P10, P12, P14. Inclui também divergências de P1 (concluída sem resolution) e labels desconhecidos.
- **AU-01/02** decisões de distribuição e registro de auditoria. **SI-01/02** estado da sincronização (cursor, contagens, `usuariosNaoResolvidos`) e como ligar ao Jira.
- **Sincronização** (não é card de tela): espelha o projeto por `updated ASC, key ASC` com sobreposição de minutos; reconciliação diária por `key in (…)` em lotes de 100; cancelada **entra** (a auditoria precisa).

---

## 4. Cards novos propostos (matemática mínima de referência)

Itens do relatório (seção 7.2) com a regra para implementá-los sem inventar números:

| Card | Matemática de referência |
|---|---|
| Calendário do time | União de `Ausência`, `Feriado` e `vence` por dia; contagem de pessoas disponíveis `= #{p : cap_dia(p,d)>0}` |
| Cobertura por ausência | Para cada ausência futura de `p`: issues abertas de `p` com `vence` ou início dentro do intervalo; horas `Σ h`; candidatos = P20 restrito a competência + folga |
| Plano 4–8 semanas | `demanda[w]` (P13) × `cap_w` (P11) por pessoa |
| Vencimentos da semana | `{vence ∈ [hoje, hoje+7/14]}` com `atrasa` (PV-06) |
| Pessoas sem capacidade para novos itens | `folga = cap_4s − carga_4s`; elegível se `folga > h_típica` |
| Cobertura de competências | Matriz competência × pessoa; risco se `#pessoas(c)=1` |
| Revisões pendentes por revisor | Reviews abertas agrupadas por responsável; SLA `= agora − criada` vs P85 do `ciclo_review` |
| Tendência de ocupação | Série semanal de `ocup_4s` por pessoa (histórico persistido); recorrente se `>1,10` em ≥ 3 de 6 semanas |
| Saúde do squad | Índice composto sem ranking de pessoas: média ponderada normalizada de VG-04, VG-05/abertas, eficiência de fluxo e (1−|ocup−1|) |

---

## 5. Matriz card × campos Jira necessários

`S` = obrigatório, `o` = opcional/melhora, `—` = não usa. Colunas: **St** status+changelog · **As** assignee+changelog · **Dd** duedate+changelog · **Rs** resolution/resolutiondate · **Es** estimativas (orig/rest/gasto) · **Lb** labels · **Pr** priority · **Wl** worklog · **Ln** issuelinks/parent · **Ep** épico/componentes · **Fl** Flagged · **Tg** campos TG (OS/SKU/Checklist Type).

| Card | St | As | Dd | Rs | Es | Lb | Pr | Wl | Ln | Ep | Fl | Tg |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| VG | S | S | S | S | o | o | — | — | — | — | o | o |
| MP | S | S | S | — | S | — | o | — | — | — | — | — |
| AB | S | S | S | — | o | — | S | — | — | — | — | o |
| EP | S | — | S | S | o | — | — | — | — | S | — | — |
| NG | S | S | S | — | — | — | — | — | — | — | o | — |
| RI | S | S | S | — | S | — | o | — | — | — | o | — |
| PV | S | S | S | S | S | — | S | — | — | — | — | — |
| FE | S | S | S | S | o | — | — | — | — | — | — | o |
| NE | S | S | S | S | S | — | — | — | — | — | — | — |
| TS | S | S | — | S | — | — | — | — | — | — | — | o |
| MT | o | S | S | S | — | S | S | — | — | — | — | o |
| CR | S | S | S | S | o | S | — | — | S | — | — | o |
| IN | S | S | S | S | S | S | — | — | — | — | — | — |
| AU | o | S | S | o | — | S | — | — | o | — | — | — |
| CP/CA | S | S | S | — | S | — | S | S | — | — | — | — |
| GA | S | — | — | — | — | — | — | — | — | — | — | — |
| DI/RB | S | S | o | — | S | S | S | — | — | S | — | — |
| QD | S | S | S | — | S | — | S | — | — | S | — | — |

**Lacunas do sync atual para recriar tudo:** hoje só são pedidos os campos mínimos (summary, issuetype, status, assignee, reporter, created, updated, resolutiondate, duedate, labels, parent, project, issuelinks) + OS Version, SKU Type, Checklist Type. **Faltam** `priority`, `resolution`, `timeoriginalestimate`, `timeestimate`, `timespent`, `components`, `Flagged`, Story Points/Epic Link (se existirem) e **worklogs**. Cada inclusão exige entrada no dicionário `jira_campos.yaml` (e a descoberta do `customId` na 1ª conexão).

---

## 6. Procedimento de adaptação (para a outra IA)

1. **Preencher o modelo canônico (seção 1)** campo a campo; marcar o que o Jira de destino não tem e decidir o *fallback* (ex.: sem `timeestimate` → P10 cai nos próximos níveis; sem `Flagged` → bloqueio só por status).
2. **Mapa de status completo** (todos os status do workflow → `todo|active|wait|blocked|done`) e `workflow_order`. Sem isso TS/GA/ciclo falham ou mentem.
3. **Definir papéis de tipo** (task/review/épico) e o **vínculo** Task↔Review.
4. **Calendário** (feriados do local) e **fuso** único (America/Manaus) para dia de resolução, “no prazo”, D-1/D-2, “último minuto”.
5. **Config de pessoas**: horas semanais, foco, alocação, competências, ausências.
6. **Ficha por card** (YAML): `campos`, `filtrosFixos`, `filtrosExcluidos`, `parametros`, `formula`, `exemplo`, `aceite`, `expandChangelog`. Todo campo citado precisa existir no dicionário da instância.
7. **Testes de aceite por card** (seção 7). Rodar a suíte sem cache. A validação com o Jira real só acontece depois que o **usuário** liberar o acesso (a flag de acesso é só dele), na ordem de `docs/jira/checklist-primeira-conexao.md`.

### 6.1 Invariantes (não mudar sem decisão do gerente)

Percentil por interpolação linear; concluída = categoria **e** resolution; reentradas somam; Monte Carlo com reposição e semente fixa; Jain sobre ocupação limitada a 3; sem ranking de pessoas (tabelas alfabéticas); integridade só para gerente e auditada; sugestão ≠ escrita no Jira; capacidade líquida desconta ausência e feriado; “nulo” ≠ 0.

### 6.2 Adaptável (com registro da decisão)

Pesos e limiares (risco, integridade, distribuição, foco 0,7, 4 h/ponto, 6 h padrão); janelas (7/30/60/90 d, 12/16 semanas); unidade útil/corrido; lista de status ativos; tipos; labels de atraso; metas (90%/35%).

---

## 7. Casos de aceite com números (oráculo para a recriação)

| Card | Entrada | Esperado |
|---|---|---|
| TS (modo corrido) | A Fazer 1 d, Em andamento 3 d, Em revisão 3 d (concluída em 12/10) | eficiência `3/7 = 0,428571` |
| TS (útil, feriado 12/10) | idem | Em revisão `0,666667`; eficiência `3,0/4,666667 = 0,642857` |
| TS posse | Em andamento 3 d, `ana` até 08/10, `bruno` depois | `ana 2,0`, `bruno 1,0` |
| Percentil | [1; 3] | mediana `2,0` |
| Capacidade | 40 h, foco 0,7, alocação 1, meio dia ausente | 5,6 h/dia; dia ausente 2,8 h |
| Demanda | h=12, vence em 2 semanas | 4 + 4 + 4 h nas semanas 0–2 |
| Ocupação | cap_4s=0, carga>0 | `9,9` (exibir “sem capacidade”) |
| Jain | ocup [0,5; 1,0; 1,5] | `0,857` |
| Risco | idade>P85, vencida, sem atualização > 7 d | `30+25+10 = 65 → alto` |
| Prazo | vence 10/10, resolvida 09/10 | `Δ=−1` → “D-1”, antecipada, no prazo |
| Prazo | vence 10/10, resolvida 13/10 | `Δ=+3` → “Atrasada 1–3 d” |
| Integridade | 12 pts, 5 concluídas, mediana 0,4 | taxa `1,5`, razão `3,75`; “alto” só se ≥ 3 sinais graves |
| Distribuição | carga 20 h, nova 4 h, cap 40 h, share 1, wip 1/3 | score `0,767` |
| Monte Carlo | H vazio, R>0 | “sem dados”; R=0 → 0 semanas; `|H|<8` → confiança baixa |
| Monte Carlo (cons.) | N semanas | “P85” = 15º percentil dos totais |

---

## 8. O que o conjunto permite manter, prever, auditar e localizar

**Manter (rotina gerencial):** Visão geral e Central de atenção (diário); Abertas/Negligenciadas/Riscos (triagem); Carga e Capacidade (semanal); Metas e Fechadas (fechamento de ciclo); Qualidade dos dados (higiene contínua).

**Prever:**
- *Término do backlog* (P16) e *entregas em N semanas* — probabilístico, sem chegada de itens novos.
- *Atraso por issue* (P17 + P12) — duas visões independentes; se ambas dizem “atrasa”, a confiança é maior.
- *Sobrecarga* (P14) e *impacto de ausência* (CA-03) 4–8 semanas à frente.
- *Efeito de redistribuição* (Jain antes/depois).

**Auditar:** AU (ato) e IN (padrão), MT-02/IN-06 (prazo original × atual), CR (independência da revisão), QD (qualidade do dado que alimenta tudo), registro de decisões de distribuição.

**Localizar gargalos e falhas:**

| Sintoma | Card que localiza | Causa provável |
|---|---|---|
| Etapa cresce em 15 d | GA-02 | espera de revisão/aprovação, limite de WIP estourado |
| Tempo parado no status | TS-03/07, NG-05 | dependência externa, fila de review |
| Pessoa × etapa lenta | TS-06 | falta de competência, handoff |
| Atrasos concentrados em quem/qual tipo | MT-11…14 | estimativa curta, prioridade mal definida |
| Retrabalho | NE-01/02/08, CR-11 | revisão curta, requisitos instáveis |
| Atraso “por ausência” × “por carga” | CA-03 | falta de cobertura × excesso de demanda |
| Previsão ruim | QD sem estimativa > 20% | P10 usando padrão de 6 h |
| Indício de manipulação | AU, IN | prorrogação tardia, apropriação, fechamento em lote |

---

## 9. Divergências e fragilidades **verificadas no código** (corrigem ou refinam o relatório)

1. **Sentinela 990%** = `9,9` no código (`utilization_rows`): confirmado. Usar campo explícito “sem capacidade”.
2. **Fuso:** `on_time` e as metas usam data **local** (`JIRADASH_TZ`), mas os filtros de período e o agrupamento semanal usam `resolved.date()` em **UTC** (`closed_recs`, `weekly_throughput`). Resultado: uma entrega às 22h locais pode cair no dia seguinte na janela/semana mas “no prazo” pelo dia local. O Kotlin deve usar **sempre** o fuso local.
3. **Três limiares de “parada no status”:** risco `max(2; 2×mediana)`, negligenciadas `max(3; 2×mediana)`, tempo em status `max(2; 1,5×mediana)`. Unificar ou documentar a diferença intencional; hoje a mesma issue pode estar “parada” em um card e não em outro.
4. **Negativas:** reabertas/devolvidas/trocas filtram por `atualizada ≥ início`, não pela data do evento (perde eventos antigos de issues atualizadas depois).
5. **Demanda de vencidas:** o relatório diz que concentram horas na semana atual; o código as espalha em **2 semanas** (mínimo `última=1`). Ainda inflam a ocupação, porém menos.
6. **Previsão por capacidade** ordena por `(iniciada, prioridade, vence, criada)`; a demanda semanal (P13) **não** usa essa ordem nem respeita capacidade, então `carga_4s` (por vencimento) e a data prevista (por ordem de fila) podem discordar. Manter as duas, mas rotular.
7. **Central de atenção:** a fusão *sem atualização ∪ paradas* por chave existe no código (sem dupla contagem); o manual descreve diferente. Vale o código.
8. **Estouro de estimativa** `gasto/est_orig > 1,25` compara o gasto **total** da vida da issue, inclusive de issues abertas ainda em curso (pode sinalizar cedo).
9. **Pareamento Checklist×Review** por `parent` no legado; **por link Blocks no novo**. Qualquer teste do legado precisa ser adaptado.
10. **Horas padrão (4 h/ponto, 6 h, mín. 0,5 h)**, pesos de risco, limiares de integridade e constantes de P20 não têm calibração empírica; sem base real, tratar P10–P14 como **ordem de grandeza**, não como promessa.
11. **Monte Carlo** semente fixa torna o resultado reproduzível, mas oculta a variabilidade entre execuções; é uma escolha de teste, não de estatística.
12. **Ciclo/lead em corridos** inflam por fim de semana; o novo usa útil — comparar com histórico do legado exige a mesma unidade.

---

## 10. Pendências e o que **não** foi verificado

- Comportamento real do Jira DC da empresa (todos os itens de `matriz-compatibilidade.md` seguem **não validados**).
- Cálculos do frontend (formatação, cores por limiar), a tela Meu painel e partes do manual marcadas **[manual]**.
- As specs de fase 05–07 e 10 podem ter decidido de modo diferente para Abertas, Metas, Pessoas/Planejamento, Checklist×Review, Integridade e Distribuição; **ler a spec da fase antes de implementar o card** e registrar divergências aqui.
- Decisões em aberto para o usuário: destino do card **Épicos** (Epic fora do sync); definição única de “parada”; unidade padrão (útil) para Metas; calibração dos pesos.
