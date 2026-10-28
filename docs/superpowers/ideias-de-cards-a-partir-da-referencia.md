# Ideias de cards, telas, ações e melhorias a partir da referência do Jira

Data: 28/10/2026. Estado: **proposta; nada foi decidido nem implementado.** Origem: leitura dos quatro documentos de `docs/jira/referencia/` (JQL, filtros e gadgets, REST do Data Center, gestão e análise), cruzada com `PRODUCT.md`, `docs/analise/relatorio-cards-app-atual.md`, o índice dos planos, as specs das Fases 4 a 16, `docs/jira/matriz-compatibilidade.md` e o código atual do backend (`JiraClient`, `Syncer`, `ChangelogParser`, `JqlBuilder`, esquema `V1__base.sql`).

Nenhuma consulta foi feita ao Jira. **Nenhum JQL ou endpoint citado aqui foi testado em servidor real** (ver `docs/jira/referencia/00-README.md`). Onde uma ideia depende do servidor, a ficha diz o quê.

**Resultado: 27 ideias** em 6 temas, com 5 de prioridade Alta. O top 10 está na seção 10 e as descartadas na seção 11.

## 0. Regras que valem para todas as fichas

- **Somente leitura.** Nenhuma ideia escreve no Jira. Escrita é exclusivamente o Plano 16 (futuro, só com decisão do usuário). Ações propostas aqui só leem, filtram, exportam ou abrem um link.
- **Flag `JIRA_LIVE_ENABLED`:** única trava; só o usuário a muda; com ela desligada, ninguém acessa o Jira por caminho nenhum (inclusive uma pessoa clicando num link do painel). Toda chamada nova passa por `LiveGuard`, conta no `OrcamentoDeRequisicoes` e é testada com `MockEngine` e host `.invalid`.
- **Sem ranking de pessoas;** tabelas e listas de pessoas em ordem alfabética; nada de "maior/menor" nem de posição.
- **Visibilidade por papel:** USUARIO vê só o próprio; SQUAD_LEADER o próprio e o seu squad; COORDENADOR e GERENTE todos. Todo agregado é calculado só sobre o que o papel vê. Tasks sem responsável só para coordenação e gerente.
- **Dados pessoais:** nada de nome de exibição, e-mail ou texto livre do Jira; fixtures com nomes fictícios (`ana.silva`); dado novo sobre pessoa entra no inventário e na trava do DPO (Plano 11).
- **Fuso America/Manaus** para todo "dia"; **dias úteis** por padrão, `unit=corrido` opcional; semana de segunda a domingo.
- **Todo número é clicável** e abre as tasks que o compõem, com o mesmo tamanho (drill); a cor nunca é o único sinal.
- **Esforço:** P = uma tarefa de plano (código, teste e i18n); M = duas a quatro tarefas; G = um bloco novo. **"Fase 17 candidata"** é um nome provisório para um futuro plano de cards novos (o 16 está reservado à escrita); não existe plano 17 hoje.

## 1. Já coberto pelos planos (não duplicar)

| Sugestão da referência | Já está em |
|---|---|
| Lista "Atrasadas" (gadget Filter Results) | Plano 5a (Abertas, Metas e prazos), Plano 6a (Vencimentos da semana) |
| Pie Chart, Issue Statistics, totais por status/responsável | AB-05, AB-06, VG-01 a VG-08 (Planos 5 e 7b) |
| Time Since Chart (issues paradas por `updated`) | NG-01 e NG-05 (Plano 5a) |
| Cumulative Flow Diagram | GA-01 "Fluxo cumulativo de 60 dias", reconstruído do changelog (Plano 6a, Tarefa 7). A ideia nova é só I-07 |
| Velocity | Throughput: VG-10, PV-04 e Monte Carlo (Plano 5) |
| Epic Report e Epic Burndown | EP-01 Épicos, previsão só com amostra própria (Plano 5b) |
| Assigned to Me / Favorite Filters | Meu painel (Plano 7b); filtros na URL (Plano 10) |
| Days Remaining in Sprint | Calendário do time, Vencimentos da semana, Plano de 4 a 8 semanas (Plano 6a) |
| User Workload, capacidade do Advanced Roadmaps | Carga por pessoa, Capacidade e ausências, YAML de ausências (Plano 6) |
| Activity Stream | Linha do tempo da Auditoria e gaveta da task (Planos 4 e 10) |
| Reabertas, devolvidas, canceladas (padrões 14, 15) | NE-01 a NE-10 (Plano 5b) |
| Sem estimativa, sem vencimento, sem responsável, concluída sem resolution, em andamento sem tempo (padrões 16 a 20) | Qualidade dos dados, 7 verificações mais `DONE_SEM_RESOLUTION` e `RESOLUTION_SEM_DONE` (Plano 7b) |
| Mudanças de responsável, vencimento e labels (padrões 25 a 29) | 11 sinais da Auditoria (Plano 4); atraso justificado por label (Plano 4) |
| Review ligada à task por link (padrão 21) | `issue_link` e `ParesTaskReview` (Plano 4A, Tarefa 12), Divergência (Plano 4), Checklist × Review (Plano 7a) |
| Sincronização incremental com sobreposição, reconciliação, paginação pelo que veio, `Retry-After`, orçamento (03, seção 5) | Plano 4A |
| Worklog embutido em `fields.worklog` e endpoint por issue só se truncado | Plano 6b, Tarefa 10 |
| Validade do PAT | Plano 12 (informada pelo operador; consultar a API é não objetivo) |
| Conferência de campos e status em respostas salvas | Plano 14a, Tarefa 2 |
| Roteiro de validação por ficha contra totais do Jira | Plano 14b, Tarefas 8 e 9 |
| Tabela pessoa x status (mediana de dias) | TS-06 (Plano 3); mapa pessoa x semana de capacidade: CA-05 (Plano 6a) |

## 2. Resumo das 27 ideias

| ID | Ideia | Tipo | Esforço | Prioridade | Onde cabe |
|---|---|---|---|---|---|
| I-01 | Entrada x saída x iniciadas por semana | card | M | Alta | Fase 17 candidata |
| I-02 | Gráfico de controle do ciclo | card | M | Média | Plano 5a (Fechadas) |
| I-03 | Tendência do tempo de resolução | card | P | Baixa | Plano 5a (Fechadas) |
| I-04 | Idade do backlog ao longo do tempo | card | M | Baixa | Fase 17 candidata |
| I-05 | Envelhecimento por coluna (Aging WIP) | card | M | Média | Fase 17 candidata |
| I-06 | Sem movimento real (além de `updated`) | melhoria | P | Média | Plano 5a (Negligenciadas) |
| I-07 | Retrato diário do fluxo | melhoria técnica | M | Baixa | Fase 17 candidata (ponto de parada) |
| I-08 | Bloqueios entre tasks | card | M | Média | Fase 17 candidata |
| I-09 | Burnup/burndown da janela e escopo inserido | card | M | Média | Fase 17 candidata (ponto de parada) |
| I-10 | Tabela cruzada configurável | aba | M | Média | Fase 17 candidata |
| I-11 | Mapa de calor de vencimentos por tipo | card | P | Baixa | Plano 6a (Calendário) |
| I-12 | Labels fora do vocabulário de atraso | card (QD) | P | Alta | Plano 7b (Qualidade dos dados) |
| I-13 | Diretório mínimo de usuários | melhoria técnica | M | Alta | Plano 14a (T6) e Plano 7b |
| I-14 | Pai concluído com sub-tarefas abertas | verificação (QD) | P | Baixa | Plano 7b |
| I-15 | Alteração em lote como contexto | melhoria | P | Média | Plano 4 (Tarefas 4 a 9) |
| I-16 | Conta de automação conhecida | melhoria | P | Baixa | Plano 4 (Tarefa 2) |
| I-17 | Reporter e prioridade com valor | melhoria técnica | M | Baixa | Plano 11 (Tarefa 3; ponto de parada) |
| I-18 | Abrir a seleção no Jira (link com JQL) | ação (leitura) | P | Alta | Planos 10 e 4 |
| I-19 | Corrigir o risco do changelog e do `expand` | melhoria técnica | M | Alta | Plano 14a (T6) e 4A |
| I-20 | Ficha de capacidades do servidor | melhoria técnica | M | Média | Plano 14a (T4, T5) |
| I-21 | Vocabulário do fluxo a partir do Jira | melhoria técnica | M | Média | Plano 14a (T2) e Plano 12 |
| I-22 | Catálogo de JQL de controle | melhoria técnica | P | Média | Plano 14b (T8, T9) |
| I-23 | Sentinela de contagem | melhoria técnica | P | Baixa | Plano 12 |
| I-24 | Carga inicial em duas passadas | melhoria técnica | M | Média | Evolução do 4A |
| I-25 | Ritmo adaptativo pelos cabeçalhos de taxa | melhoria técnica | P | Baixa | Evolução do 4A |
| I-26 | Busca rápida por chave | ação (leitura) | P | Baixa | Plano 10 |
| I-27 | Filtros rápidos nomeados em YAML | ação (leitura) | P | Baixa | Plano 10 |

## 3. Fluxo e gargalos

### I-01 Entrada x saída x iniciadas por semana
- **Tipo:** card (Fluxo).
- **Por quê:** mostra se o backlog do squad cresce ou encolhe semana a semana (entra mais do que sai?) e explica visualmente o ritmo que o Monte Carlo da Previsão assume. Hoje existe só "entregas por semana", sem a entrada.
- **Fonte:** `02`, gadget "Created vs Resolved Chart" (ideia: "entrada x saída semanal por squad"); `01`, padrão 40 "Entrada e saída da semana"; `04` seção 8 "Definições" (throughput).
- **Dado e consulta necessários:** tudo local. Entrada = `issue.created`; saída = concluídas (categoria done **e** `resolution`; resolução negativa vira "canceladas", em série à parte); iniciadas = 1ª entrada em status ativo (`Fluxo.inicioAtivo`, a partir do changelog de status). Saldo acumulado = backlog. Conferência no servidor, por amostra: `project = TG AND created >= "2026-10-19 00:00" AND created < "2026-10-26 00:00"` e o equivalente com `resolved`. **Exige changelog** só para "iniciadas"; sem worklog.
- **Regras do projeto que se aplicam:** squad pelo responsável no instante do evento (como o Plano 5 faz com pessoas); sem série lado a lado por pessoa; USUARIO só vê as próprias (pouca amostra: aviso `AMOSTRA_PEQUENA`); sem responsável só para coordenação; semana de segunda a domingo de Manaus; símbolo `▲` com texto "backlog crescendo" quando a entrada supera a saída por 3 semanas seguidas; cada ponto abre o drill.
- **Onde cabe:** Fase 17 candidata. Reaproveita a série de chegada que o Plano 5b (Tarefa 12, Previsão) já calcula para o Monte Carlo; reforça PV-04 e VG-10 sem duplicar.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** reaberta limpa `resolved` (usar o último evento de `resolution` do changelog e testar); issue removida ou movida (`removida_em`) sai da série; o fuso do servidor desloca contagens perto da meia-noite (matriz, linha do fuso).
- **Prioridade:** Alta. O dado já existe, o ganho de leitura é imediato e explica a previsão.

### I-02 Gráfico de controle do ciclo (Control Chart)
- **Tipo:** card (seção em Fechadas).
- **Por quê:** cada task concluída vira um ponto (data de conclusão x dias de ciclo), com média móvel e faixas P50/P85. Revela se o processo está estável, quais itens fogem da curva e se uma mudança de fluxo funcionou. A dispersão atual (FE-08) só dá percentis.
- **Fonte:** `02` seção 6 "Control Chart" (média móvel de 20% das issues, mínimo 5, ímpar); `04` seção 8 "Control Chart nativo" e "Definições".
- **Dado e consulta necessários:** concluídas positivas do período com `resolved` e ciclo = 1ª entrada em status ativo até a resolução, em dias úteis (opção corridos), **por tipo** (Checklist e Review não se misturam, Plano 5). Média móvel calculada por nós com a regra do Jira (20% das issues, ímpar, mínimo 5), para permitir conferir contra o relatório do quadro. **Exige changelog.** Conferência: relatório Control Chart do quadro, só por amostra manual.
- **Regras do projeto que se aplicam:** percentis por tipo; outliers acima de 30 dias com opção de excluir (YAML, regra do bscbi-web); USUARIO só vê os próprios pontos; sem ranking; data de conclusão no dia de Manaus; cada ponto abre o drill.
- **Onde cabe:** Plano 5a, Fechadas (estende FE-08 "Dispersão do ciclo"); se o 5a já tiver sido executado, Fase 17.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** a definição de "ciclo" (1ª entrada ativa x última saída de In Progress; reaberturas) muda a forma do gráfico, e o Jira inclui retrabalho; calibrar no Plano 14b. Depende de changelog completo (I-19).
- **Prioridade:** Média. Bom diagnóstico, mas FE-08 já cobre o essencial.

### I-03 Tendência do tempo de resolução (Resolution Time)
- **Tipo:** card (seção "Tendência" em Fechadas).
- **Por quê:** P50/P85 do período não dizem se o lead time está melhorando; uma série mensal por tipo responde "estamos ficando mais rápidos?".
- **Fonte:** `02`, gadget "Resolution Time" (ideia: lead time por mês); `04` seção 8.
- **Dado e consulta necessários:** lead = `resolved - created`; ciclo como em I-02; últimos 12 meses, P50 e P85 por mês e por tipo, com o n de cada mês. Lead não exige changelog; ciclo exige. Conferência: `resolved >= "2026-09-01 00:00" AND resolved < "2026-10-01 00:00"` só para o n.
- **Regras do projeto que se aplicam:** dias úteis por padrão; mês com menos de `AMOSTRA_MINIMA` itens mostra o número com aviso; por tipo e por squad, nunca por pessoa; fuso de Manaus no mês.
- **Onde cabe:** Plano 5a, Fechadas (FE-03 e FE-04).
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** meses com poucas entregas e outliers (o bscbi-web excluía acima de 30 dias); calibração no 14b.
- **Prioridade:** Baixa. Acrescenta a série, não dado novo.

### I-04 Idade do backlog ao longo do tempo (Average Age)
- **Tipo:** card (Fluxo).
- **Por quê:** o backlog "envelhecendo" (idade média e P85 das abertas semana a semana) é sinal de trabalho esquecido que a foto de hoje (AB-07) não mostra.
- **Fonte:** `02`, gadget "Average Age Chart"; `01`, padrão 32 "Backlog envelhecido".
- **Dado e consulta necessários:** para cada fim de semana `d`: abertas = `created <= d` e não resolvida em `d` (reabertas pelo evento de `resolution` do changelog); idade em dias úteis; média, mediana e P85 por tipo/squad. Exige changelog para reabertas (sem ele, aproximação por `resolved`). O Jira não devolve estado passado, só o atual: `resolution = Unresolved AND created <= "2026-09-28 00:00"` reproduz apenas o presente.
- **Regras do projeto que se aplicam:** resolução negativa conta como resolvida; sem ranking; USUARIO só as próprias; Manaus; issue resolvida há mais de 24 meses é apagada pela retenção, então limitar a série a 12 meses.
- **Onde cabe:** Fase 17 candidata; reforça AB-02, AB-03 e AB-07 (Plano 5a).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** reconstrução depende de changelog completo e da retenção (ver I-07).
- **Prioridade:** Baixa. I-01 já mostra se o backlog cresce; este mostra o envelhecimento, útil mas secundário.

### I-05 Envelhecimento por coluna (Aging WIP)
- **Tipo:** card (Fluxo).
- **Por quê:** para cada etapa do fluxo, mostra as tasks em andamento e há quanto tempo estão naquela etapa, com as faixas P50/P85 históricas da própria etapa. O gestor vê de uma vez onde há itens fora do normal antes de virarem atraso.
- **Fonte:** `02` seção 4 "Colunas, mapeamento de status e limites de WIP" ("Days in column"); `04` seção 8 "Definições" (aging = agora menos a entrada no status atual).
- **Dado e consulta necessários:** abertas em andamento com o tempo desde a última entrada no status atual (changelog de status), em dias úteis; P50/P85 por status das concluídas (TS-04, Plano 3); colunas na ordem de `status_map.ordem` (Plano 5). **Exige changelog.**
- **Regras do projeto que se aplicam:** pontos sem nome de pessoa; drill por ponto e por coluna; USUARIO só as próprias; sem ranking; `▲` com o texto "acima do normal" acima do P85; só issue em andamento (espera e bloqueio entram como colunas próprias).
- **Onde cabe:** Fase 17 candidata; reforça NG-05 "Paradas no status" e AB-04 (Plano 5a), que hoje listam só quem passou do limite.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** ordem real do fluxo e status de espera (vêm do `status_map` real); amostra por status.
- **Prioridade:** Média.

### I-06 Sem movimento real (além de `updated`)
- **Tipo:** melhoria em Negligenciadas.
- **Por quê:** `updated` muda com comentário, worklog ou edição de texto, então uma task parada pode parecer "atualizada". Separar "sem atualização no Jira" de "sem movimento de status, responsável ou vencimento" evita falso negativo.
- **Fonte:** `04` seção 8 "Métricas por JQL e changelog" (`updated` é indicador fraco de "parada"); `01` padrões 7 e 8.
- **Dado e consulta necessários:** último `change_event` dos campos de movimento (status, assignee, resolution, duedate, labels) por issue, comparado a `issue.updated`. Conferência no servidor: `statusCategory = "In Progress" AND updated <= "-7d"` (o que NG-01 já espelha); para movimento real só o changelog (a composição `NOT (status CHANGED AFTER "-7d")` do padrão 8 não está validada). **Exige changelog.**
- **Regras do projeto que se aplicam:** limite em YAML (mesmo de NG-01); dias corridos ou úteis conforme o Plano 5; lista, não ranking; USUARIO só as próprias.
- **Onde cabe:** Plano 5a, Negligenciadas: coluna "último movimento" e filtro, sem substituir NG-01.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** depende de changelog completo (I-19); comentário pode indicar vida legítima, por isso é coluna extra e não troca.
- **Prioridade:** Média. Barato e corrige uma fragilidade já apontada na própria referência.

### I-07 Retrato diário do fluxo (cumulative flow)
- **Tipo:** melhoria técnica que alimenta o GA-01.
- **Por quê:** o fluxo cumulativo (GA-01) é reconstruído do changelog a cada consulta. Um retrato diário gravado (contagem por squad x tipo x categoria de status) não depende de changelog completo, resiste à purga de 24 meses e deixa a série barata.
- **Fonte:** `02` seção 6 "Cumulative Flow Diagram" ("precisa snapshot diário no SQLite"); `04` seção 8 "CFD" (para reproduzir, reconstruir do changelog; relatos de inexatidão no Jira).
- **Dado e consulta necessários:** tabela nova (`retrato_fluxo`: instância, dia, squad, tipo, categoria, quantidade) gerada ao fim do dia de Manaus a partir do próprio SQLite; **sem pessoa**; migração na próxima numeração livre. Nenhuma chamada ao Jira, sem changelog.
- **Regras do projeto que se aplicam:** agregados sem pessoa; retenção como `RETENTION_MONTHS`; entra no inventário de dados (o teste `todaColunaEstaNoInventario` do Plano 11 falha se for esquecido); Manaus; o GA-01 continua reconstruindo o passado sem retrato.
- **Onde cabe:** Fase 17 candidata. Toca a decisão D1 do Plano 6 ("sem tabela de retrato semanal", reversível, dita para a Tendência de ocupação).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** só vale daqui para frente e duplica o que é reconstruível. **Ponto de parada: perguntar ao usuário** (seção 9, PP2).
- **Prioridade:** Baixa. Fazer só se a validação do 14b mostrar o GA-01 lento, impreciso (changelog truncado) ou cortado pela retenção.

## 4. Prazos e metas

### I-08 Bloqueios entre tasks (dependências)
- **Tipo:** card (estende Negligenciadas) e verificação na Qualidade dos dados.
- **Por quê:** hoje o vínculo `Blocks` só liga a Review à sua task; os demais ficam gravados em `issue_link` e ignorados (o teste `blocksEntreDuasTasksIgnorado` do 4A é só sobre pareamento). Uma lista de tasks bloqueadas por outra ainda aberta, com o vencimento da bloqueadora, mostra o impedimento real e avisa quando a bloqueadora vence depois da bloqueada.
- **Fonte:** `02` seção 8 ("Alerta de dependência"); `04` seção 3 "Tipos de link e direções" e "Consultar links no JQL"; `01` padrões 21 e 22.
- **Dado e consulta necessários:** `issue_link` (só chaves e tipo, `removida_em IS NULL`) cruzada com `issue` (status, vencimento, responsável) quando a outra ponta está na base; `from_key` é quem bloqueia (outward "blocks"). Idade do bloqueio aproximada por `visto_em` (o evento de campo `Link` no changelog só grava o nome do campo). Conferência: `issueLinkType = "Blocks" AND statusCategory != Done` e `issue in linkedIssues(TG-107, "is blocked by")` (o nome do tipo e a direção reais são pendência da matriz). **Sem changelog nem worklog.**
- **Regras do projeto que se aplicam:** excluir os pares Task x Review (já na Divergência); se a bloqueadora é de pessoa fora da visão, mostrar "task de outro squad" sem chave nem nome; sem ranking de bloqueadores; ponta fora da base vira "verificar no Jira" (link da gaveta, Plano 10); QD: status da categoria `blocked` ou flag sem nenhum vínculo `Blocks` aberto = "bloqueada sem causa registrada".
- **Onde cabe:** Fase 17 candidata (estende NG-04 "Bloqueadas", Plano 5a; a verificação na QD do Plano 7b).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** tipo e direção do link; se mexer no link altera `updated` (matriz); status e vencimento da ponta fora da ficha não existem na base.
- **Prioridade:** Média.

### I-09 Burnup/burndown da janela e escopo inserido depois do corte
- **Tipo:** card (Prazos).
- **Por quê:** para uma janela de entrega (um mês, uma versão de SO), responde se o restante está no ritmo e se o escopo cresceu depois do início. É o equivalente do Burndown, do Burnup e do Sprint Report ("inseridas depois"), sem depender de sprint.
- **Fonte:** `02` seção 6 (Burndown, Burnup, Sprint Report, Epic Burndown) e gadget "Days Remaining in Sprint"; `04` seção 8.
- **Dado e consulta necessários:** janela escolhida por filtro (vencimento em `[início, fim]` ou `OS Version`, campo do dicionário, Plano 10). Escopo(d) = tasks da janela com `created <= d`; concluídas(d) = resolvidas até `d`; restante = escopo - concluídas; ideal linear em dias úteis com feriados e ausências do YAML; "inseridas depois" = `created > início`. Sem changelog (reaberta é aproximação). Conferência: `duedate >= "2026-11-01" AND duedate <= "2026-11-30"` e `created > "2026-11-01 00:00"`.
- **Regras do projeto que se aplicam:** contagem de issues por padrão (pontos opcionais); dias úteis; ausências reduzem o ideal só no nível do squad; USUARIO só as suas; Manaus.
- **Onde cabe:** Fase 17 candidata.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** o que é "janela" no TG-Basic (OS Version? vencimento?) e se sprint existe (referência `04`, pergunta 12). **Ponto de parada: perguntar ao usuário** (seção 9, PP4) antes de implementar.
- **Prioridade:** Média.

## 5. Carga e capacidade

### I-10 Tabela cruzada configurável (estatística bidimensional)
- **Tipo:** aba "Cruzamentos".
- **Por quê:** responde perguntas ad hoc ("abertas por tipo x categoria de status", "por OS Version x faixa de idade") sem pedir tela nova, com os mesmos filtros e o mesmo drill. No Jira é o gadget mais usado para ver carga.
- **Fonte:** `02` seção 5 "Two Dimensional Filter Statistics" (e "Issue Statistics", "Pie Chart").
- **Dado e consulta necessários:** SQL local sobre `issue` e `campos_dicionario` (Plano 10). Eixos de uma lista fechada: squad, categoria de status, status, tipo, prioridade, OS Version, Checklist Type, SKU Type, faixa de idade, semana de vencimento, com/sem label de atraso. Medida: contagem (padrão) ou idade P50. O eixo "pessoa" só como linhas em ordem alfabética. Sem changelog. Conferência: `project = TG AND statusCategory != Done` com `maxResults=1` por célula, em amostra.
- **Regras do projeto que se aplicam:** agregados só do visível; USUARIO só a própria linha; pessoa fora da visão = "outra pessoa"; nunca ordenar por contagem quando o eixo é pessoa; sem total por pessoa em destaque; célula com número e padrão além da cor; cada célula abre o drill.
- **Onde cabe:** Fase 17 candidata; generaliza o agrupamento por campo do Plano 10 (Tarefa 3) e o TS-06 (Plano 3).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** nada do servidor; o risco é de produto (virar ranking disfarçado), por isso a lista fechada de eixos.
- **Prioridade:** Média.

### I-11 Mapa de calor de vencimentos por tipo de trabalho
- **Tipo:** card (visão alternativa do Calendário).
- **Por quê:** mostra, nas próximas 8 semanas, onde os vencimentos se concentram por tipo de trabalho, OS ou Checklist Type. O calendário mostra o dia; o mapa mostra o tipo. Ajuda a antecipar picos antes de virarem atraso.
- **Fonte:** `02` seção 5 "Heat Map" (a ideia ali é pessoa x semana, que já existe no CA-05; aqui o eixo é outro); `01` padrões 5 e 6.
- **Dado e consulta necessários:** abertas com `duedate` nas próximas 8 semanas; linhas = campo do dicionário/tipo/squad; colunas = semanas, anotadas com feriados e ausências do YAML. Sem changelog.
- **Regras do projeto que se aplicam:** não usa pessoa; número na célula e padrão tracejado para a faixa mais alta (a cor não é o único sinal); dias úteis; Manaus; drill por célula.
- **Onde cabe:** Plano 6a, Calendário do time (Tarefa 6), como visão alternativa; ou Fase 17.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** sobreposição com o Calendário e com o Plano de 4 a 8 semanas; o valor só aparece se houver concentração real.
- **Prioridade:** Baixa.

## 6. Qualidade dos dados

### I-12 Labels fora do vocabulário de atraso
- **Tipo:** verificação na Qualidade dos dados.
- **Por quê:** o atraso justificado depende de uma lista de labels em YAML, que a matriz chama de ativo de domínio mais valioso. Uma label nova (ou escrita diferente) que ninguém classificou faz um atraso real parecer injustificado, ou o contrário. A lista de "labels sem classificação" mantém o YAML vivo.
- **Fonte:** `01`, campo `labels` (sem `CHANGED`: histórico só pelo changelog) e padrões 28 e 29; `03`, `jql/autocompletedata/suggestions`; `matriz-compatibilidade.md`, linha "Taxonomia de labels de atraso".
- **Dado e consulta necessários:** local: `issue.labels` (JSON) das últimas N semanas, agrupadas por frequência, menos as de `labels_atraso.yaml` (justificativas e tardias, com curinga no fim) e de uma lista nova `labels_ignoradas`. Opcional no servidor: `GET /rest/api/2/jql/autocompletedata/suggestions?fieldName=labels&fieldValue=ONHOLD` para conferir se uma label do YAML ainda existe. Sem changelog nem worklog.
- **Regras do projeto que se aplicam:** label não é pessoa; só coordenação e gerente (define política de atraso); agregado por label, sem ranking de pessoas; uma "candidata" nunca entra no cálculo sozinha, o gestor edita o YAML (somente leitura no app).
- **Onde cabe:** Plano 7b, Qualidade dos dados: nova verificação informativa `LABEL_SEM_CLASSIFICACAO`.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** o vocabulário real de labels só se vê na 1ª conexão; labels livres geram ruído.
- **Prioridade:** Alta. A regra de atraso justificado é central e o vocabulário muda com o tempo; o custo é baixo.

### I-13 Diretório mínimo de usuários do Jira (chaves pendentes e responsável inativo)
- **Tipo:** melhoria técnica e verificação na Qualidade dos dados.
- **Por quê:** um autor ou responsável que aparece só como chave interna (`JIRAUSER...`) fica "não resolvido" e vira "não autorizado" na auditoria, ou até para a primeira sincronização (o 14a trata `usuariosNaoResolvidos` maior que 0 como motivo de parada). E uma task atribuída a quem foi desativado no Jira fica parada sem alerta.
- **Fonte:** `03`, entrada "user, user/search, user/assignable/search" e seção 7 item 9; `04` seção 10 "Usuários inativos e licenças".
- **Dado e consulta necessários:** `GET /rest/api/2/user?key=JIRAUSER10001` (resolve o `name` quando a chave não veio de nenhuma issue recebida) e `GET /rest/api/2/user?username=ana.silva` (campo `active`), uma vez por dia por usuário distinto, em cache (dezenas de chamadas). Guardar só `username` e `active`; tabela nova ou colunas em `jira_user_key` (migração). Hoje `ChavesDeUsuario` só aprende o mapa das issues recebidas. Sem changelog.
- **Regras do projeto que se aplicam:** dado pessoal mínimo (sem nome de exibição, e-mail ou avatar) e entrada no inventário do Plano 11; só coordenação e gerente veem "responsável inativo"; inativo não é ausência (ausências seguem no YAML); sem ranking; as chamadas contam no orçamento.
- **Onde cabe:** resolução de chaves pendentes na Tarefa 6 do Plano 14a (regras refutadas viram correção), código em `ChavesDeUsuario`/`Syncer` (4A); verificação `RESPONSAVEL_INATIVO` no Plano 7b.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** se `user?key=` existe e devolve usuários inativos no DC da empresa; permissão para consultar usuários; usuário removido.
- **Prioridade:** Alta. Destrava a auditoria (autor desconhecido) e evita parar a primeira carga.

### I-14 Pai concluído com sub-tarefas abertas (e o inverso)
- **Tipo:** verificação na Qualidade dos dados.
- **Por quê:** sinal clássico de "fechou sem terminar": task concluída com sub-tarefas ainda abertas, ou todas as sub-tarefas prontas e a pai parada.
- **Fonte:** `01`, padrão 38 e campo `parent`; `04` seção 2 "Tipos de issue e sub-tarefas" (`fields.subtasks`).
- **Dado e consulta necessários:** o `fields.subtasks` do JSON da própria pai traz chave e status das filhas, então basta acrescentar `subtasks` aos campos mínimos da ficha, sem sincronizar as sub-tarefas. Conferência: `parent = TG-107 AND statusCategory != Done`. Sem changelog.
- **Regras do projeto que se aplicam:** só faz sentido se o time usa sub-tarefas (`04`, pergunta 16, que também decide se contam na carga); visibilidade pelo responsável da pai; sem ranking.
- **Onde cabe:** Plano 7b, Qualidade dos dados.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** `Sub-task` não está na ficha de sincronização; o status das filhas no campo pode vir resumido.
- **Prioridade:** Baixa.

## 7. Auditoria

### I-15 Alteração em lote como contexto
- **Tipo:** melhoria dos sinais da Auditoria (modificador informativo).
- **Por quê:** trocar vencimento, responsável ou label de muitas tasks de uma vez é o caso de maior impacto da auditoria, e hoje cada alteração aparece isolada. Marcar "parte de um lote de N tasks" muda a leitura do coordenador.
- **Fonte:** `02` seção 3 "Alteração em lote (Bulk change)" ("troca em lote ... é exatamente o caso que a auditoria deve detectar").
- **Dado e consulta necessários:** local: `change_event` agrupado por (autor, campo, mesmo minuto) com pelo menos N issues distintas (N em `auditoria.yaml`, padrão sugerido 5). Cada operação em lote deve gerar entradas de changelog com o mesmo instante (premissa a confirmar). Conferência apenas para responsável: `assignee CHANGED BY ana.silva DURING ("2026-10-01","2026-10-31")`. Sem worklog.
- **Regras do projeto que se aplicam:** não é sinal de culpa: aparece como "em lote (N)" nos sinais existentes; indício, não prova; só coordenação e gerente; sem contagem nem ranking por pessoa (lista por data); janela no dia de Manaus.
- **Onde cabe:** Plano 4 (Tarefas 4 a 9 ainda não executadas): modificador no detector; ou Fase 17.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** se o instante é idêntico em um lote ou varia em segundos; automação também gera lotes (ver I-16).
- **Prioridade:** Média.

### I-16 Conta de automação conhecida
- **Tipo:** melhoria da linha do tempo da Auditoria.
- **Por quê:** mudanças feitas por regra de automação ou post function aparecem no changelog em nome de um usuário. Sem distinguir, o coordenador investiga ruído (por exemplo, `resolution` limpa ao reabrir) ou lê um sinal sem contexto.
- **Fonte:** `04` seção 1 "Condições, validadores e post functions", seção 10 "Automação nativa", perguntas 10 e 15.
- **Dado e consulta necessários:** lista em YAML versionado `contas_automaticas` (só contas técnicas); a linha do tempo e a gaveta marcam "automática" a alteração cujo autor está na lista. Local, sem Jira. A 1ª conexão mostra quem aparece como autor (Passo 4 do checklist).
- **Regras do projeto que se aplicam:** **não** muda a decisão de autorização (autor de automação nunca vira autorizado, antiobjetivo do Plano 4); só contas técnicas, nenhuma pessoa; sem ranking.
- **Onde cabe:** Plano 4, Tarefa 2 (`Autorizacao`/`AuditTimeline`); ou Fase 17.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** quais contas existem; conta compartilhada usada por humanos.
- **Prioridade:** Baixa. Reduz ruído, não muda decisão; vale quando a 1ª conexão mostrar volume de automação.

### I-17 Reporter e prioridade com valor no histórico
- **Tipo:** melhoria técnica (dados do changelog).
- **Por quê:** a autorização do PL usa o reporter **atual**, e a troca de reporter só gera o aviso `REPORTER_ALTERADO`; se o reporter mudou antes da alteração auditada, a decisão pode estar errada. A prioridade (escalada ou rebaixamento) hoje só registra "houve mudança".
- **Fonte:** `01` seção 9 e operadores (`reporter` e `priority` aceitam `WAS`/`CHANGED`), padrões 31 e 34; código: `ChangelogParser.CAMPOS_COM_VALOR` (hoje `status`, `resolution`, `duedate`, `labels`, `OS Version`; `assignee` à parte).
- **Dado e consulta necessários:** incluir `reporter` e `priority` em `CAMPOS_COM_VALOR` (valores curtos e categóricos; reporter como username normalizado). **Exige changelog;** o histórico antigo só reaparece com recarga (zerar o cursor, como a opção 1 do Plano 11). Conferência: `reporter CHANGED DURING ("2026-10-01","2026-10-31")` e `priority CHANGED AFTER "-7d"`.
- **Regras do projeto que se aplicam:** reporter é dado pessoal: inventário e trava do DPO (Plano 11); autorização por `reporterEm(instante)`, análogo a `responsavelEm`, com vigência de papéis na data de Manaus; sem ranking.
- **Onde cabe:** Plano 11, Tarefa 3 (mesma recarga do changelog); o Plano 4 recebe `reporterEm`.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** formato do reporter no changelog (chave interna?); custo da recarga. **Ponto de parada: perguntar ao usuário** (seção 9, PP3).
- **Prioridade:** Baixa. Reporter muda raramente; vale quando o Plano 11 já for recarregar o changelog (custo marginal).

### I-18 Abrir a seleção no Jira (link com JQL por chaves), em vez de escrever
- **Tipo:** ação (somente leitura).
- **Por quê:** encurta o ciclo "ver o erro no painel, corrigir no Jira" sem o painel escrever. O botão abre o Navegador de issues do Jira com as chaves selecionadas; quem tem permissão faz a alteração em lote ali, com a própria identidade (a autoria fica correta, sem conta de serviço), até o Plano 16 decidir se o painel escreverá.
- **Fonte:** `02` seção 3 "Alteração em lote (Bulk change)" (limite de 1.000 issues, permissões) e "Exportar resultados"; `01`, `issuekey IN (...)` (até 3000 operandos no GET).
- **Dado e consulta necessários:** nenhuma chamada ao Jira. A URL é montada com a `baseUrl` da configuração (o `linkJira` do Plano 10: https, sem credenciais, só com a flag ligada): `<baseUrl>/issues/?jql=issuekey%20in%20(TG-107,TG-212)`. O formato `/issues/?jql=` não está na referência: confirmar na 1ª conexão. Chaves vêm do drill ou da seleção na Divergência, na Auditoria (coordenação) ou em qualquer card; no máximo 100 por link, com aviso acima disso (alternativa: o CSV que já existe).
- **Regras do projeto que se aplicam:** com a flag desligada o botão não aparece; só chaves visíveis ao principal; `access_log` guarda a rota e a quantidade, nunca as chaves nem o texto; nova aba com `noopener`; não é escrita e não depende do Plano 16.
- **Onde cabe:** Plano 10 (Tarefas 5 e 6: gaveta e drill) e Plano 4 (Divergência, Tarefa 8).
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** formato da URL do Navegador de issues; tamanho máximo da URL; se o usuário tem a permissão de Bulk change.
- **Prioridade:** Alta. Pequeno e resolve o caso de uso que motivou o Plano 16, sem a política de escrita.

## 8. Integração, técnico e uso

### I-19 Corrigir o risco do changelog e do `expand` (obrigatória)
- **Tipo:** melhoria técnica (correção preventiva).
- **Por quê:** a sincronização depende de um endpoint que a referência do DC 9.12 não lista (o `issue/{key}/changelog` paginado é do Cloud) e de um `expand` no corpo do `POST /search` que o resumo 9.12 não confirma. Se qualquer um falhar, a carga inicial para com `HTTP_404` ou, pior, chama o endpoint em todas as issues. Hoje isso só seria descoberto na primeira conexão, com a flag ligada.
- **Fonte:** `03`, entradas "issue/{key}/changelog" (ALERTA), "search (GET e POST)" e seção 6 "Como o nosso painel usa" (linha do `fullChangelog`), seção 7 item 8; `04`, "Aba History e changelog da issue"; `matriz-compatibilidade.md`, riscos abertos ("GET /issue/{key}/changelog", plano B).
- **Dado e consulta necessários:** estratégia em degraus, escolhida por sonda e lembrada na execução (sem laço de tentativas): (1) `POST /rest/api/2/search` com `expand` (atual); (2) fallback documentado `GET /rest/api/2/search?jql=...&expand=changelog&fields=...&validateQuery=true` (`expand` é parâmetro documentado; o JQL do incremental e os lotes de 100 chaves da reconciliação cabem na URL); (3) `GET /rest/api/2/issue/{key}?expand=changelog&fields=key,updated` por issue; (4) o endpoint paginado só se a sonda o encontrar. **Exige changelog** (é o próprio assunto). Fatos do código que a correção precisa tratar:
  - `Syncer.truncado()` devolve "truncado" também quando a issue **não traz** o objeto `changelog`: se o servidor ignorar `expand`, toda issue cai no `fullChangelog`. Criar o código de erro `CHANGELOG_NAO_VEIO` (nenhuma issue da página trouxe `changelog`) em vez de N chamadas inúteis.
  - `JiraClient.fullChangelog` lê `values` (formato do Cloud); o embutido e o `issue/{key}?expand=changelog` trazem `changelog.histories`. O fallback precisa do outro leitor.
  - `Syncer.incompleta()` hoje só olha `created` e `summary`: issue cujo changelog continua truncado deve virar `incompleta` (aviso `HISTORICO_TRUNCADO` na Qualidade dos dados).
  - Testes sugeridos: `postSemChangelogNaRespostaParaComCodigoClaro`, `fallbackGetComExpandTrazChangelog`, `fullChangelogEm404CaiParaIssueComExpand`, `formatoHistoriesDaIssueELidoComoDoSearch`, `historicoTruncadoMarcaIncompleta`, `orcamentoContaAsChamadasDoFallback`, `nenhumaRequisicaoComFlagDesligada`.
- **Regras do projeto que se aplicam:** `LiveGuard` antes de cada tentativa; orçamento de requisições conta o fallback (por issue custa 1 chamada, contra o teto de 2.000 por execução); nunca repetir 404 em laço (404 também significa issue invisível); sem texto de erro em `sync_run` (só códigos); nada novo sobre pessoas.
- **Onde cabe:** código no 4A (já mesclado) como PR de correção pequeno; a confirmação e o registro na Tarefa 6 do Plano 14a e na linha da matriz. Dá para fazer tudo offline, com `MockEngine`.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** se o `POST` aceita `expand`; se `issue/{key}?expand=changelog` volta completo ou cortado em 100; a versão exata do DC; o custo em requisições no fallback (1 por issue).
- **Prioridade:** Alta. Sem isso a primeira carga pode parar ou gastar o orçamento à toa; é correção prévia e barata de fazer offline.

### I-20 Ficha de capacidades do servidor
- **Tipo:** melhoria técnica e bloco na tela de Sincronização do gerente.
- **Por quê:** versão, permissões e limites do servidor decidem como o cliente deve se comportar. O checklist chama `myself`, `field`, `search` e uma issue; não chama `serverInfo`, `mypermissions` nem `application-properties` (verificado no checklist e no runbook).
- **Fonte:** `03`, entradas `serverInfo`, `myself`, "Outros de leitura úteis" e seção 7 itens 1 e 4; "Limite de taxa do DC".
- **Dado e consulta necessários:** `GET /rest/api/2/serverInfo?doHealthCheck=false` (`versionNumbers`, `deploymentType`, `serverTime`), `GET /rest/api/2/myself` (`timeZone`), `GET /rest/api/2/mypermissions?projectKey=<projeto>` (confere `BROWSE_PROJECTS` e avisa se a credencial pode editar issues, já que o ideal é conta somente leitura), `GET /rest/api/2/application-properties?key=jira.search.views.default.max` (muitas chaves exigem administrador: falha vira "desconhecido"). Guardar um JSON pequeno em memória e no `/api/sync/status`: versão, fuso do usuário, teto de página visto, se há cabeçalhos de taxa, se pode editar. Sem host, usuário nem token. Sem changelog.
- **Regras do projeto que se aplicam:** só o GERENTE vê; nada pessoal; chamadas no orçamento; só com a flag ligada pelo usuário.
- **Onde cabe:** Plano 14a (Tarefas 4 e 5, passos extras do checklist) e a tela de status do 4A; registrar na matriz.
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** `application-properties` pode negar; semântica exata de `mypermissions` no DC da empresa.
- **Prioridade:** Média. Informa as demais correções (I-19, I-24, I-25) em vez de adivinhar.

### I-21 Vocabulário do fluxo a partir do Jira (status_map assistido e deriva)
- **Tipo:** melhoria técnica (ferramenta local e conferência recorrente).
- **Por quê:** o `status_map` e a ordem do fluxo são decisão do gestor, mas hoje se montam à mão; e um status ou resolução novo no workflow só aparece quando a primeira issue com ele chega e vira `STATUS_SEM_MAPEAMENTO`.
- **Fonte:** `03`, entradas `status`, `statuscategory`, `resolution`, `priority`, `project/{key}/statuses`, `board/{id}/configuration`, seção 7 item 3; `04` seção 1 "Categorias de status" e "Resolução"; `02` seção 4 "Colunas, mapeamento de status".
- **Dado e consulta necessários:** `GET /rest/api/2/statuscategory`, `GET /rest/api/2/project/<chave>/statuses`, `GET /rest/api/2/resolution`, `GET /rest/api/2/priority` e, opcionalmente, `GET /rest/agile/1.0/board?projectKeyOrId=<chave>` mais `board/{id}/configuration` (colunas -> status, como sugestão de `ordem`). Saída: arquivo local ignorado pelo git (mesma convenção do casador de campos, Plano 14 Tarefa 2; nunca um `.exemplo`). Conferência recorrente: 4 a 5 chamadas por dia, em cache. Sem changelog.
- **Regras do projeto que se aplicam:** nada pessoal; "concluída" = categoria done e `resolution`; a ordem do fluxo é só proposta (quem decide é o gestor); `Filtros_Status_tool.txt` continua sendo o vocabulário versionado.
- **Onde cabe:** Plano 14a (Tarefa 2: da conferência sobre respostas salvas para leitura viva) e Plano 12 (novo código de alerta, por exemplo `VOCABULARIO_DERIVOU`).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** quadro inexistente, sem permissão ou Kanban sem as colunas esperadas; o filtro do quadro pode diferir da nossa ficha; `status/page` e `priority/page` são experimentais.
- **Prioridade:** Média.

### I-22 Catálogo de JQL de controle (statusCategory, CHANGED DURING, WAS IN, datas)
- **Tipo:** melhoria técnica (validação) e política de datas.
- **Por quê:** a validação do Plano 14b precisa de uma fonte independente do próprio painel. Os operadores de categoria e de histórico do JQL contam, no servidor, o que calculamos localmente; juntos formam um conjunto pequeno de contagens de controle (`maxResults=1`, lendo `total`).
- **Fonte:** `01` campo `statusCategory`, operadores `WAS`, `WAS IN`, `CHANGED`, predicados `DURING`/`AFTER`/`BY`, seção 6 (datas), padrões 3, 9, 14, 19, 23 a 26 e 32; `03` entrada `search`; `matriz` (totais baratos).
- **Dado e consulta necessários:** contagens de controle, uma por card:

  | Card (plano) | JQL de controle | Observação |
  |---|---|---|
  | Abertas (AB-01, Plano 5a) | `project = TG AND statusCategory != Done` | `statusCategory` não existe hoje no dicionário nem no `JqlBuilder` |
  | Concluídas no período (FE-01) | `statusCategory = Done AND resolution IS NOT EMPTY AND resolved >= "2026-10-01 00:00" AND resolved < "2026-11-01 00:00"` | datas absolutas |
  | Concluída sem resolution (QD) | `statusCategory = Done AND resolution IS EMPTY` e o inverso | padrão 19 |
  | Reabertas (NE-01) | `status CHANGED FROM <status done> AFTER "2026-10-01"` | alternativa: `status WAS IN (<done>) AND statusCategory != Done` |
  | Trocas de responsável | `assignee CHANGED DURING ("2026-10-01","2026-10-31")` | conta **issues**, não eventos |
  | Passaram por etapa (TS, CR) | `status WAS IN ("Checklist Review") DURING ("2026-10-01","2026-10-15")` | diz se passou, não por quanto tempo |
  | Prioridade (I-17) | `priority CHANGED AFTER "2026-10-21"` | |
  | Vencimento e labels | sem `CHANGED`/`WAS` | só o changelog responde; validar por amostra na aba History do Jira |

  **Política de datas:** sempre absolutas (`aaaa-MM-dd HH:mm`, no fuso do usuário do token, como o `JqlBuilder.dateTimeAtLeast` já faz). Evitar `startOfWeek()`/`endOfWeek()` (dependem da localidade), `"-1d"` (meia-noite do servidor, diferente de `"-24h"`) e `currentUser()` (seria sempre a conta de serviço). Funções `startOf*`/`endOf*` só em conferência manual, depois de confirmar o primeiro dia da semana do Jira. Exige changelog só do lado do painel; do lado do Jira, nada além de `search`.
- **Regras do projeto que se aplicam:** só contagens e datas entram no registro versionado (agregados, sem chave nem usuário reais); fuso de Manaus; a divergência de "concluída" segue a regra categoria + `resolution`.
- **Onde cabe:** Plano 14b (Tarefas 8 e 9, roteiro de validação com fonte `JIRA_TOTAL`); a extensão do dicionário com `statusCategory` fica como tarefa pequena do mesmo plano.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** desempenho de `WAS`/`CHANGED` e o bug JRASERVER-78497; `statusCategory` e `WAS IN` em campos do nosso workflow; se `NOT (status CHANGED ...)` funciona.
- **Prioridade:** Média. Reforça o 14b com consultas já escritas e uma regra de datas que evita erros de fuso.

### I-23 Sentinela de contagem
- **Tipo:** melhoria técnica (alerta operacional).
- **Por quê:** a reconciliação diária (4A) detecta issue removida ou movida só uma vez por dia. Uma contagem barata a cada hora compara o total de abertas no Jira com o local e acusa perda silenciosa (índice atrasado, issue movida, `status_map` errado).
- **Fonte:** `03` seção 5 "Boas práticas de carga" (itens 3 e 4) e `matriz` (totais com `maxResults=1`); `04` seção 6 "Índice e reindexação" (defasagem da busca).
- **Dado e consulta necessários:** `POST /search` com `{"jql":"<universo da ficha> AND statusCategory != Done","maxResults":1,"fields":["key"]}` e leitura de `total`, contra a contagem local de abertas por categoria (mesma definição nos dois lados). As abertas nunca são apagadas pela retenção (4A, decisão 5), então a comparação é exata, salvo a janela de sobreposição de 5 minutos (reavaliar após o próximo incremental). Segundo par: `statusCategory = Done AND resolution IS EMPTY` contra `DONE_SEM_RESOLUTION`. Sem changelog.
- **Regras do projeto que se aplicam:** 2 a 3 chamadas por hora, no orçamento; alerta no Plano 12 (por exemplo `CONTAGEM_DIVERGE`, sem dado pessoal); só com a flag ligada; `maxResults=0` não está confirmado (usar 1).
- **Onde cabe:** Plano 12 (alertas), reaproveitando `JiraClient.count`.
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** diferença legítima entre `statusCategory` do Jira e a categoria do nosso `status_map`; defasagem do índice.
- **Prioridade:** Baixa. A reconciliação diária já cobre; vale como prova contínua barata.

### I-24 Carga inicial em duas passadas (abertas primeiro)
- **Tipo:** melhoria técnica da sincronização.
- **Por quê:** a ficha de sincronização ordena por `updated ASC`, então durante a carga inicial (e a recarga do Plano 11 ou 14b) o que o gestor mais quer ver, as tasks abertas e recentes, chega por último. Como as abertas nunca são apagadas pela retenção, vale carregá-las antes das resolvidas.
- **Fonte:** `03` seção 5 "Boas práticas de carga"; `04` seção 6 "Boas práticas de consultas pesadas" (changelog só do que mudou); `01` campo `statusCategory`.
- **Dado e consulta necessários:** passada A: universo da ficha com `statusCategory != Done` (`updated ASC, key ASC`); passada B: resolvidas dentro do corte de retenção. Cada passada com marca d'água própria (coluna nova em `sync_state` ou linha por tipo de execução: migração). Mostrar na tela de status a completude do histórico (percentual por passada) e o aviso `HISTORICO_PARCIAL` nos cards que dependem de resolvidas (Fechadas, Metas). Exige changelog (volume é justamente o problema). Depende do `statusCategory` no `JqlBuilder` (I-22).
- **Regras do projeto que se aplicam:** cursor nunca recua nem passa da última issue gravada (antiobjetivo do 4A); orçamento por execução; suspensão após 401/403/429; sem texto de erro; a flag.
- **Onde cabe:** evolução do Plano 4A (PR próprio); interage com a recarga do Plano 11 (opção 1) e do Plano 14b (Tarefa 7).
- **Esforço:** M.
- **Risco / o que só o servidor real confirma:** o número real de abertas; o comportamento do cursor com duas passadas.
- **Prioridade:** Média. Só pesa na primeira carga e nas recargas, mas é nelas que o gestor decide se confia no painel.

### I-25 Ritmo adaptativo pelos cabeçalhos de limite de taxa
- **Tipo:** melhoria técnica do cliente.
- **Por quê:** o cliente só reage ao 429 e ao `Retry-After`. A estratégia de menor impacto recomendada pela Atlassian é ler `X-RateLimit-Remaining` e `X-RateLimit-FillRate` e espaçar as requisições antes de esgotar as fichas.
- **Fonte:** `03` seção 5 "Limite de taxa do DC (rate limiting)", seção 1 "Cabeçalhos úteis" e seção 7 item 5.
- **Dado e consulta necessários:** cabeçalhos `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Interval-Seconds`, `X-RateLimit-FillRate` e `Retry-After` de toda resposta; sem cabeçalhos (limite desligado) nada muda. Nenhuma consulta nova. Sem changelog.
- **Regras do projeto que se aplicam:** concorrência baixa e sequencial; o orçamento continua valendo; o limite vale por nó; testes com `MockEngine` e cabeçalhos simulados.
- **Onde cabe:** evolução do `JiraClient`/`RetryPolicy` do 4A; registrar o valor visto na ficha de capacidades (I-20).
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** se o limite está ligado e com que valores; se o proxy da empresa remove os cabeçalhos.
- **Prioridade:** Baixa.

### I-26 Busca rápida por chave
- **Tipo:** ação (somente leitura).
- **Por quê:** digitar `TG-107` (ou só `107`) e abrir a gaveta da task, em qualquer tela. É o atalho mais usado do Jira e concretiza o princípio "todo número leva às tasks".
- **Fonte:** `02` seção 1 "Busca rápida (quick search)".
- **Dado e consulta necessários:** só `issue.key` local, por prefixo e por chave exata; abre `/api/tarefa/{key}` (Plano 10). Sem busca de texto: o esquema não guarda `summary` em coluna e o resumo pode conter dado pessoal. Sem changelog.
- **Regras do projeto que se aplicam:** task fora da visão responde 404 (não confirma que a chave existe, decisão D5 do Plano 10); consulta auditada em `access_log`; sem ranking; sem autocompletar chaves que o principal não vê.
- **Onde cabe:** Plano 10 (Tarefa 6, gaveta universal).
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** nenhum do servidor.
- **Prioridade:** Baixa.

### I-27 Filtros rápidos nomeados em YAML
- **Tipo:** ação (somente leitura).
- **Por quê:** botões acima dos cards que aplicam uma combinação de filtros com um clique ("só atrasadas", "sem responsável", "com label de atraso", "Checklist Type X"), como os quick filters do quadro do Jira.
- **Fonte:** `02` seção 4 "Quick filters com JQL" (padrões "Only My Issues", "Recently Updated").
- **Dado e consulta necessários:** `filtros_rapidos.yaml` versionado (somente leitura) mapeando nome -> filtros já existentes do Plano 10 (squad, tipo, campo do dicionário, situação); não é JQL do Jira. Sem changelog.
- **Regras do projeto que se aplicam:** não nomear pessoas (nada de "tasks da ana"; "minhas" é o Meu painel); respeitam a visão do papel; o filtro vai na URL como os demais.
- **Onde cabe:** Plano 10 (Tarefa 4, filtros).
- **Esforço:** P.
- **Risco / o que só o servidor real confirma:** nenhum do servidor.
- **Prioridade:** Baixa.

## 9. Pontos de parada: perguntar ao usuário

Estas decisões são do usuário. Cada uma vira uma pergunta com opções (a recomendada primeiro) quando o assunto for retomado.

**Ponto de parada PP1: perguntar ao usuário — o que fazer com estas ideias e quando.**
1. **Fazer agora, offline, um PR pequeno com I-19 (e a parte técnica de I-13 e I-20), antes da Fase 14a; os cards novos viram uma Fase 17 depois da 14b (Recomendada).** Por quê: a primeira conexão é o maior risco do projeto, e os dados reais calibram a prioridade dos cards. Consequência: os cards novos esperam; o risco do changelog some antes de ligar a flag.
2. **Estender agora os planos 5a, 7b e 10 (ainda não executados) com as ideias Alta e Média de cada um.** Por quê: estender um plano não executado custa menos que reabrir um executado. Consequência: esses planos crescem e a entrega dos cards atuais atrasa.
3. **Só arquivar este documento por enquanto.** Por quê: foco total nas fases em andamento. Consequência: o risco do changelog fica para a Tarefa 6 do Plano 14a descobrir com a flag ligada.

**Ponto de parada PP2: perguntar ao usuário — gravar um retrato diário do fluxo (I-07).**
1. **Não gravar; manter a reconstrução do changelog e reavaliar na validação do 14b (Recomendada).** Por quê: evita tabela nova e preserva a decisão D1 do Plano 6. Consequência: o GA-01 pode ficar lento ou cortado pela retenção; decide-se com dados reais.
2. **Gravar o retrato agregado (squad, tipo, categoria), sem pessoa.** Por quê: série barata e independente do changelog. Consequência: migração nova e linha no inventário do DPO; só vale daqui para frente.
3. **Gravar também por pessoa (para a Tendência de ocupação com as horas da época).** Por quê: acabaria com o aviso `HORAS_ATUAIS_NO_PASSADO`. Consequência: dado pessoal diário, retenção e parecer do DPO; é a opção mais sensível.

**Ponto de parada PP3: perguntar ao usuário — guardar o valor de reporter e prioridade no histórico (I-17).**
1. **Decidir junto com o ponto de parada 1 do Plano 11, para fazer uma única recarga do changelog (Recomendada).** Por quê: o custo marginal é mínimo se a recarga já vai acontecer. Consequência: amplia dados pessoais tratados (reporter); exige aceite do DPO.
2. **Ampliar só o reporter.** Por quê: corrige a autorização do PL sem tocar em prioridade. Consequência: a escalada de prioridade continua invisível.
3. **Não ampliar; manter o aviso `REPORTER_ALTERADO`.** Por quê: mínimo de dados. Consequência: a autorização do PL pode errar quando o reporter foi trocado antes da alteração.

**Ponto de parada PP4: perguntar ao usuário — o que é a "janela" do burnup/burndown (I-09).**
1. **A versão de SO (campo `OS Version`) (Recomendada).** Por quê: é a unidade de entrega do TG-Basic e já está no dicionário. Consequência: tasks sem `OS Version` ficam fora do card.
2. **Um intervalo de vencimento escolhido na tela.** Por quê: funciona sem campo especial. Consequência: o escopo muda toda vez que alguém ajusta um vencimento.
3. **Uma sprint do Jira.** Por quê: só se o time usa sprint. Consequência: exige a API Agile e um quadro Scrum; a 1ª conexão precisa confirmar que existe.

## 10. Proposta de ordem (top 10)

A ordem segue o risco e as dependências do índice dos planos: o que protege a primeira conexão vem antes dos cards.

| # | Ideia | Quando | Por quê nesta posição |
|---|---|---|---|
| 1 | I-19 Corrigir o risco do changelog e do `expand` | Antes da 14a | Impede a carga inicial de parar com 404 ou gastar o orçamento; é feita offline. |
| 2 | I-13 Diretório mínimo de usuários | Na 14a (Tarefa 6) | Sem ele, `usuariosNaoResolvidos` maior que 0 trava a primeira carga e a auditoria erra autor. |
| 3 | I-20 Ficha de capacidades do servidor | Na 14a (Tarefas 4 e 5) | Dá a versão, o fuso e as permissões que decidem as demais correções. |
| 4 | I-22 Catálogo de JQL de controle | Na 14b (Tarefas 8 e 9) | Entrega a fonte independente da validação com consultas e regra de datas prontas. |
| 5 | I-12 Labels fora do vocabulário de atraso | Plano 7b | Mantém viva a regra de atraso justificado, que é central; custo baixo. |
| 6 | I-18 Abrir a seleção no Jira | Plano 10 | Resolve o ciclo de correção sem escrita e sem a política do Plano 16. |
| 7 | I-01 Entrada x saída x iniciadas | Fase 17 | Dado já existe; explica o backlog e a previsão. |
| 8 | I-15 Alteração em lote como contexto | Plano 4 (Tarefas 4 a 9) | Qualifica o caso de maior impacto da auditoria com custo pequeno. |
| 9 | I-08 Bloqueios entre tasks | Fase 17 | Aproveita `issue_link` já gravado e mostra impedimentos reais. |
| 10 | I-05 Envelhecimento por coluna | Fase 17 | Antecipa atrasos por etapa; reforça NG-05 e AB-04. |

Ficam logo atrás: I-24 (carga em duas passadas), I-02 (gráfico de controle), I-10 (tabela cruzada), I-06 (sem movimento real) e I-09 (burnup, depende de PP4).

## 11. Descartadas e por quê

| Ideia da referência | Fonte | Por que não vale a pena |
|---|---|---|
| Webhooks (`jira:issue_updated`) no lugar do polling | `03` seção 4; `04` seção 10 | Exige conta administradora (a do painel é somente leitura), receptor alcançável e autenticidade própria (a página não descreve segredo nem reentrega). Reabrir só se a infraestrutura oferecer tudo isso. |
| Alteração em lote, edição, transição, comentário, anexos, criar/mover issues, sprint | `02` seção 3; `03` (POST/PUT/DELETE) | Exige escrita no Jira. Escrita é só o Plano 16. A ideia I-18 cobre o caso de uso sem escrever. |
| Advanced Roadmaps (Plans) como fonte de capacidade | `02` seção 7; `04` seção 9 | Capacidade por equipe, sem ausência nem feriado por pessoa; o app tem o seu cálculo em YAML (Plano 6). Aviso de fim de suporte do DC no texto. |
| Dias sem worklog como sinal de ausência | `04` seção 4 "Worklogs" | Vigilância indireta de pessoas, contra "sem ranking" e a LGPD; ausência é YAML, sem motivo. |
| Velocity, Sprint Health, Release Burndown, Version Report, gadget Road Map | `02` seções 5 e 6 | Dependem de quadro Scrum, estimativa e `fixVersion` não confirmados; a vazão (VG-10, PV-04) já cobre. Reabrir se a 1ª conexão mostrar uso de sprint ou versão. |
| Plugins: ScriptRunner (`issueFunction`, `dateCompare`), ActivityTimeline, Tempo, consulta em planilha | `matriz`; `04` seções 7 e 9 | Dependência de terceiros que a empresa pode não ter; o prazo é calculado em código. |
| Itens só do Cloud: `issue/{key}/changelog` paginado como caminho principal, `search/jql` com `nextPageToken`, `accountId`, `parent` para épico, `jql/parse` | `03`; `01` | Não existem no DC. O changelog paginado entra só como degrau opcional em I-19. |
| Audit log do Jira (`/rest/auditing/1.0/events`) | `04` seção 5 | Exige administrador e cobre configuração (workflow, permissões), não campos de issue; o changelog já atende. |
| Funções por usuário logado: `currentUser()`, `watchedIssues()`, `votedIssues()`, `lastLogin()`, `issueHistory()`, `lastViewed` | `01` seção 5 | Avaliam a conta de serviço; inúteis na sincronização. |
| Watchers, votos e notificações | `02` seção 8; `04` seção 10 | Sem página 11.x oficial, dado pessoal sem valor claro para a gestão. |
| Assinatura de filtro por e-mail e RSS | `02` seção 8 | O painel não envia e-mail (alertas em arquivo, log e tela, Plano 12); só 200 resultados por envio; dados saem do controle de papéis. |
| Dashboards e gadgets via API | `03` entrada `dashboard` | Não expõe os dados dos gadgets; baixa utilidade. |
| Filtros salvos do Jira (`filter = <id>`, `filter/{id}`) como universo das fichas | `02` seção 2; `03` entrada `filter` | Faz o painel depender de filtros que terceiros editam e de permissões; a ficha própria define o universo. Reabrir só como conferência pontual na 14b. |
| Grupos do Jira (`membersOf`, `group/member`) como fonte de squads | `01` seção 5; `04` seção 2 | A decisão do projeto é YAML versionado; nome de grupo e permissão de leitura são incertos. |
| Issues de ausência no Jira como fonte de ausências | `04` seção 9 | Decisão U1 (ausências em YAML, somente leitura). Reabrir se o servidor real mostrar que o time registra ausência em issues. |
| Raias (swimlanes) e quadro Kanban no painel | `02` seção 4 | Duplicaria o Jira; o painel é de gestão, não de execução. I-05 e I-10 cobrem a análise. |
| Descobrir a validade do PAT consultando o Jira | `03` seção 1 | Já é não objetivo do Plano 12, e a referência não traz o endpoint de listagem. |
| Exportação CSV/Excel do Jira como fonte de dados do painel | `02` seção 3; `04` seção 7 | Útil só para gerar fixtures sem acesso ao vivo; não é card nem ação do painel. |
| Colunas Min/Max e "Hide completed" do quadro como regra | `02` seção 4 | Apenas sinalizam no Jira; o limite de WIP já é YAML (GA-02, Plano 6a). |

## 12. Executado (Plano 17b, 28/10/2026)

I-01 (Fluxo da semana), I-05 (Envelhecimento por etapa), I-08 (Bloqueios entre tasks), I-02 e I-03 (Controle do ciclo e tendência) e I-10 (Cruzamentos) foram implementados no Plano 17b, antes do 14b por ordem do usuário. Diferenças contra as fichas (detalhe nos `Ruling:` do progresso):

- **I-01:** a janela inclui a semana em curso (marcada), e o sinal "backlog crescendo" olha só as semanas fechadas; o aviso de amostra tem código próprio (`AMOSTRA_PEQUENA_NA_JANELA`); a linha tracejada do saldo acumulado ficou na tabela.
- **I-05:** a faixa vem do tempo total por status das concluídas (`TimeInStatusCalculator`); o clique no ponto (abrir a task) espera a gaveta do Plano 10.
- **I-08:** bloqueadora de pessoa fora da visão entra nos números mas sem chave nem nome; ponta fora da base vira "verificar no Jira" e não conta como bloqueio; `BLOQUEADA_SEM_CAUSA` é o cartão BQ-04 e o ponto de extensão do Plano 7b; tipo e direção do vínculo seguem a configuração e continuam pendentes de confirmação na 1ª conexão.
- **I-02 e I-03:** média móvel centrada e cortada nas pontas; os outliers (mais de 30 dias corridos) saem por parâmetro, não por YAML; sem `de`, a série cobre 12 meses inteiros.
- **I-10:** sem os eixos de campos do dicionário (Plano 10); universo = abertas; segmentos do drill posicionais.

Aceite do gestor card a card: pendente.
