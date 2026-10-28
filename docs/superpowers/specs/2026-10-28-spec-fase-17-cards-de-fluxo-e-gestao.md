# Spec da Fase 17: Contrato do cartão e cards de fluxo e gestão

Data: 28/10/2026
Plano: [`docs/superpowers/plans/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md`](../plans/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md) (13 tarefas, dois PRs: **17a** antes do Plano 5a e **17b** depois do 14b)
Origem das ideias: [`docs/superpowers/ideias-de-cards-a-partir-da-referencia.md`](../ideias-de-cards-a-partir-da-referencia.md) (27 ideias, fichas I-01 a I-27)
Visual: [`docs/design/cartoes-gramatica-e-wireframes.md`](../../design/cartoes-gramatica-e-wireframes.md)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) · Plano visual: [`docs/design/plano-visual-frontend.md`](../../design/plano-visual-frontend.md) (seções 3, 5 e 6)

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano > spec mestra. Esta spec **vence os planos 5, 6, 7 e 10 nos pontos da seção 6** (eles não foram reescritos de propósito: não há como reabrir planos prontos sem risco).

## 1. Intenção e porquê

O gestor pediu duas coisas: (a) que **todo cartão, atual e novo, diga o que mostra**, esteja **classificado de forma organizada** e seja **clicável até as tasks que o geram**; (b) que as ideias da referência do Jira Data Center que **ainda não temos** e servem ao **gerenciamento das atividades do time** virem plano.

Achado que muda a ordem: hoje só existem duas telas (Tempo em status e Sincronização) e o cartão (`KpiCard`) é uma função local de `TempoEmStatus.tsx`; a gaveta (`DrillDrawer`) também é da tela. Os Planos 5, 6 e 7 vão criar **dezenas de cartões** (o inventário `docs/analise/relatorio-cards-app-atual.md` lista os códigos). Se cada um nascer sem descrição, sem classe e com drill próprio, a regra "todo número é clicável e abre o mesmo tamanho" vira disciplina de revisão, não garantia. Por isso:

- **17a (antes do 5a):** um **contrato único de cartão** (registro no backend + componente no frontend) e um **teste de contrato** que falha se algum cartão registrado tiver número diferente do total do seu drill ou texto sem tradução. Cartões atuais migram para ele.
- **17b (depois do 14b):** os **cards novos** (seção 3), escritos já no contrato.

Por que 17b só depois do 14b: o ciclo, as colunas do fluxo e o changelog real só são conhecidos na primeira conexão; cards de fluxo calibrados em fixtures têm alta chance de refazer.

## 2. Triagem das 27 ideias

Critérios: **viável** (dado existe local ou pede só leitura já planejada; sem escrita no Jira; sem dado pessoal novo); **não temos** (não está coberto pelos planos, seção 1 do documento de ideias); **gestão de atividades** (ajuda a decidir sobre o trabalho do time: fluxo, prazo, carga, bloqueio, qualidade da tarefa).

| ID | Ideia | Viável | Já temos | Gestão de atividades | Destino |
|---|---|---|---|---|---|
| I-01 | Entrada x saída x iniciadas por semana | sim | não (só entregas) | **sim** | **17b T8 (card novo)** |
| I-02 | Gráfico de controle do ciclo | sim | parcial (FE-08 percentis) | sim | **17b T11 (seção em Fechadas)** |
| I-03 | Tendência do tempo de resolução | sim | não (série) | sim | **17b T11** (junto de I-02) |
| I-04 | Idade do backlog no tempo | sim | não | pouco (I-01 já mostra) | Adiada: reavaliar após I-01 |
| I-05 | Envelhecimento por coluna (Aging WIP) | sim | parcial (NG-05 só lista os acima do limite) | **sim** | **17b T9 (card novo)** |
| I-06 | Último movimento real | sim | parcial (NG-01 usa `updated`) | sim | **Estende o Plano 5a (Negligenciadas)**, ver seção 6 |
| I-07 | Retrato diário do fluxo | sim | reconstruível | não é card | Fora (PP2: decidir após o 14b) |
| I-08 | Bloqueios entre tasks | sim | não (só Task x Review) | **sim** | **17b T10 (card novo)** |
| I-09 | Burnup/burndown da janela | depende | não | sim | Fora até o PP4 (o que é "janela") |
| I-10 | Tabela cruzada configurável | sim | parcial (TS-06) | sim | **17b T12 (aba nova "Cruzamentos")** |
| I-11 | Mapa de calor de vencimentos por tipo | sim | parcial (CA-05, Calendário) | pouco | Adiada |
| I-12 | Labels fora do vocabulário de atraso | sim | não | **sim** (mantém o atraso justificado vivo) | **Estende o Plano 7b (Qualidade dos dados)** |
| I-13 | Diretório mínimo de usuários | sim | não | indireto | Técnica: Plano 14a (T6); fora desta fase |
| I-14 | Pai concluído com sub-tarefas abertas | condicional | não | sim, se o time usa sub-tarefas | Estende o 7b **só se** a 1ª conexão mostrar sub-tarefas |
| I-15 | Alteração em lote como contexto | sim | não | sim (auditoria) | **Estende o Plano 4 (Tarefas 4 a 9)** |
| I-16 | Conta de automação conhecida | sim | não | pouco | Adiada |
| I-17 | Reporter e prioridade no histórico | sim | não | pouco | Fora (PP3) |
| I-18 | Abrir a seleção no Jira (link com JQL) | sim | não | **sim** (fecha o ciclo "vi no painel, corrijo no Jira") | **17a T4 (ação na gaveta)** |
| I-19 | Corrigir risco do changelog | feita | **sim (PR #28)** | técnica | Concluída |
| I-20 | Ficha de capacidades do servidor | sim | não | técnica | Plano 14a (T4, T5) |
| I-21 | Vocabulário do fluxo a partir do Jira | sim | não | técnica | Plano 14a (T2) |
| I-22 | Catálogo de JQL de controle | sim | não | técnica | Plano 14b (T8, T9) |
| I-23 | Sentinela de contagem | sim | parcial (reconciliação diária) | técnica | Plano 12 |
| I-24 | Carga inicial em duas passadas | sim | não | técnica | Evolução do 4A, PR próprio |
| I-25 | Ritmo adaptativo por cabeçalhos | sim | não | técnica | Evolução do 4A |
| I-26 | Busca rápida por chave | sim | não | sim (atalho) | **Estende o Plano 10 (T6)** |
| I-27 | Filtros rápidos nomeados em YAML | sim | não | sim | **Estende o Plano 10 (T4)** |

**Resultado (27 ideias):** 3 cards novos e 1 seção nova em Fechadas (I-01, I-05, I-08; I-02 e I-03), 1 aba nova (I-10) e 1 ação nova (I-18) dentro desta fase = 7 ideias; 6 extensões pequenas em planos que já existem (I-06, I-12, I-14 condicional, I-15, I-26, I-27); 8 técnicas que não são card (I-13, I-19 já feita, I-20 a I-25); 6 adiadas ou dependentes de decisão (I-04, I-07, I-09, I-11, I-16, I-17).

**Argumento contra esta seleção (registrado de propósito):** os quatro cards de 17b desenham o fluxo (como o trabalho anda), e o gestor também precisa do **prazo** (o que vai atrasar), que o Plano 5a/6a já cobrem. Se o orçamento de execução for curto, 17b inteiro pode esperar: só o 17a é indispensável, porque corrige um problema estrutural antes de ele se multiplicar.

## 3. Cards novos de 17b

Todos: somente leitura; sem pessoa em série; dias úteis por padrão (opção corridos); fuso America/Manaus; cartão no contrato do 17a; clicáveis até as tasks; `n` e aviso de amostra pequena (`AMOSTRA_PEQUENA`); visão pelo papel.

### 3.1 Fluxo da semana: entrada x saída x iniciadas (I-01) — natureza FLUXO
- **O que mostra:** por semana (segunda a domingo, últimas 12), quantas tasks **entraram** (`created`), **começaram** (1ª entrada em status ativo) e **saíram** (concluídas positivas; canceladas à parte). Saldo do backlog acumulado e um sinal `▲` "backlog crescendo" quando a entrada supera a saída por 3 semanas seguidas.
- **Dado:** `issue` + eventos de status (changelog). Nenhuma chamada nova ao Jira. Reaproveita a série de chegada do Monte Carlo (Plano 5b, Tarefa 12) sem duplicar o cálculo (função única `SerieSemanal`).
- **Clique:** cada barra/ponto abre as tasks daquela semana e série; o total da legenda é igual ao drill.
- **Aceite:** reaberta não conta como nova entrada; issue removida (`removida_em`) sai da série; semana de 7 dias de Manaus (teste de borda 23:59 de domingo); USUARIO vê só as próprias com aviso de amostra pequena.

### 3.2 Envelhecimento por etapa: Aging WIP (I-05) — natureza FLUXO
- **O que mostra:** para cada etapa ativa do fluxo (ordem do `status_map`), as tasks em andamento como pontos, com a **idade na etapa** (agora menos a última entrada no status atual) e faixas P50/P85 históricas **da própria etapa**. Ponto acima do P85 recebe `▲` e o texto "acima do normal".
- **Dado:** changelog de status das abertas; P50/P85 por status do Plano 3 (`TimeInStatusCalculator`). Espera e bloqueio são colunas próprias.
- **Clique:** ponto abre a task (gaveta do Plano 10); coluna abre a lista da etapa; o valor "acima do normal" abre as tasks acima do P85.
- **Aceite:** etapa sem amostra mínima mostra o número com aviso e **sem faixa**; sem nome de pessoa no ponto (a pessoa só aparece na gaveta, pela visão do papel); reentrada na etapa reinicia a idade.

### 3.3 Bloqueios entre tasks (I-08) — natureza ESTOQUE
- **O que mostra:** tasks **bloqueadas por outra ainda aberta**, com a task bloqueadora, o vencimento dela e um sinal quando a bloqueadora vence **depois** da bloqueada. Contagens: bloqueadas, bloqueadoras distintas, bloqueios com prazo conflitante. Segunda lista (Qualidade dos dados): "bloqueada sem causa registrada" (status ou flag de bloqueio sem vínculo `Blocks` aberto).
- **Dado:** `issue_link` já gravado (4A, T12), `removida_em IS NULL`, cruzado com `issue`. Exclui os pares Task x Review (já tratados em Divergência). Ponta fora da base aparece como "verificar no Jira" (link da gaveta, só com a flag ligada).
- **Clique:** número abre as tasks bloqueadas; coluna "Detalhe" diz qual a bloqueadora e o vencimento.
- **Aceite:** bloqueadora de pessoa fora da visão aparece como "task de outro squad" sem chave nem nome; sem ranking de bloqueadores; tipo e direção do link vêm da matriz (pendência da 1ª conexão, registrar `Ruling`).

### 3.4 Controle do ciclo e tendência (I-02 e I-03) — natureza FLUXO
- **O que mostra:** em Fechadas, cada task concluída vira um ponto (data de conclusão x dias de ciclo), com média móvel (regra do Jira: 20% das issues, número ímpar, mínimo 5) e faixas P50/P85; abaixo, a **série mensal** de P50/P85 por tipo (12 meses).
- **Dado:** concluídas positivas com ciclo; por tipo (Checklist e Review não se misturam).
- **Clique:** ponto abre a task; mês abre as concluídas daquele mês e tipo.
- **Aceite:** mês com menos de `AMOSTRA_MINIMA` mostra o número com aviso; opção de excluir outliers (> 30 dias) em YAML; nada por pessoa.

### 3.5 Cruzamentos (I-10) — aba nova em "Resultados"
- **O que mostra:** tabela cruzada configurável sobre uma **lista fechada** de eixos (squad, categoria de status, status, tipo, prioridade, OS Version, Checklist Type, SKU Type, faixa de idade, semana de vencimento, com/sem label de atraso); medida: contagem (padrão) ou idade P50. O eixo "pessoa" só como linhas, em ordem alfabética.
- **Clique:** cada célula abre as tasks; o estado dos eixos vai na URL (link compartilhável).
- **Aceite:** nunca ordenar por contagem quando o eixo é pessoa; sem total por pessoa em destaque; célula com número e padrão, não só cor; USUARIO só a própria linha; pessoa fora da visão = "outra pessoa".

## 4. Contrato do cartão (17a)

Todo cartão do painel, atual e futuro, **se registra** e cumpre:

1. **Identidade e classe:** `id` estável (ex.: `TS-03`), **grupo** (o do menu: Trabalho em curso, Resultados, Prazos, Pessoas e planejamento, Ação, Auditoria, Governança) e **natureza** (a pergunta que responde): `ESTOQUE` (o que há agora), `FLUXO` (como o trabalho anda), `PRAZO` (o que vence ou atrasou), `PESSOAS` (carga e capacidade), `QUALIDADE` (dado ou processo suspeito).
2. **Texto em três níveis**, sempre em arquivos de tradução pt/en: **título**, **o que mostra** (uma frase, no ⓘ) e **como é calculado** (fórmula curta com unidade, universo e exclusões).
3. **Estado:** símbolo + texto (`✓` ok, `●` atenção, `▲` problema, `■` neutro); nunca só cor.
4. **Procedência:** `n` da amostra, "atualizado há X" (último ciclo de sincronização) e aviso quando o dado é incompleto (link para Qualidade dos dados).
5. **Clicável:** o cartão inteiro abre a **gaveta de tasks** do número; cada elemento de gráfico (barra, ponto, célula) abre o seu recorte; teclado e leitor de tela equivalentes. Cartão sem tasks por trás (ex.: um percentual puro) abre as tasks do **numerador** e diz isso no texto.
6. **Invariante testada:** `número do cartão == total do drill`, com os mesmos filtros, para **todo** cartão registrado (teste de contrato que percorre o registro).
7. **Visão:** o registro diz quais papéis veem o cartão; o catálogo devolvido pela API já vem filtrado.

Tela **Mapa de cartões** (Governança): lista todos os cartões que o papel vê, agrupados por grupo e natureza, com o que mostra, como calcula, de onde vem o dado e em que tela vive. É gerada do registro, então nunca fica desatualizada.

## 5. Regras que valem para todas as tarefas

- **Somente leitura no Jira; a flag `JIRA_LIVE_ENABLED` segue como única trava.** O 17a e o 17b não fazem chamada nova ao Jira (tudo sai do SQLite). A ação I-18 monta um link e só aparece com a flag ligada.
- **Sem ranking de pessoas;** listas de pessoas em ordem alfabética. **Sem dado pessoal novo.**
- **Fuso America/Manaus; dias úteis por padrão.** Cor nunca é o único sinal. pt-BR e inglês completos. Três temas. WCAG AA.
- **Sem migração** em 17a e 17b (se a execução descobrir necessidade, parar e perguntar: viola a premissa desta spec).
- Plataforma: SQLite embutido, sem Docker; comandos em PowerShell (principal) ou `sh`. Comentários em pt-BR; commits Conventional Commits pt-BR.

## 6. Alterações em planos existentes (valem sem reescrevê-los)

| Plano | Mudança |
|---|---|
| 5a, Tarefa 4 (`CardDeLeitura`, `rotaDeCard`) | Todo cartão criado **se registra** no `CatalogoDeCartoes` (17a). O teste `drillTemOMesmoTamanhoDoCard` passa a ser o teste de contrato do 17a. |
| 5a, Negligenciadas | Acrescentar a coluna "último movimento" (status, responsável, vencimento, labels, resolução; I-06) e o filtro "sem movimento real". Não substitui NG-01. |
| 5, 6 e 7 (todas as telas) | Cartões usam o componente `Cartao` do 17a em vez de criar o seu; textos "o que mostra" e "como calcula" entram junto do título. |
| 4, Tarefas 4 a 9 | Modificador informativo "em lote (N)" nos sinais (I-15): mesma pessoa, mesmo campo, mesmo minuto, a partir de N issues (N em `auditoria.yaml`, padrão 5). Indício, não prova; só coordenação e gerente. |
| 7b, Qualidade dos dados | Nova verificação informativa `LABEL_SEM_CLASSIFICACAO` (I-12); `PAI_CONCLUIDO_COM_FILHAS_ABERTAS` (I-14) **somente se** a 1ª conexão confirmar o uso de sub-tarefas. |
| 10, Tarefas 4 e 6 | Filtros rápidos nomeados em `filtros_rapidos.yaml` (I-27) e busca por chave na gaveta universal (I-26; chave fora da visão responde 404). A gaveta do Plano 10 **reutiliza** a `GavetaDeTasks` do 17a em vez de criar outra. |

## 7. Pontos de parada: perguntar ao usuário

**PP5: perguntar ao usuário — quando executar o 17b.**
1. **Depois do 14b, com dados reais (Recomendada).** Por quê: o ciclo e o fluxo reais calibram os cards. Consequência: os cards novos chegam mais tarde.
2. **Logo depois do 7 (antes do 14b).** Por quê: entrega mais cedo. Consequência: calibração com fixtures; provável retrabalho no 14b.

**PP6: perguntar ao usuário — a janela do burnup (I-09) e o retrato diário (I-07).** Continuam nos pontos de parada PP4 e PP2 do documento de ideias; esta fase não os implementa.

## 8. Critérios de aceitação

- 17a: `CatalogoDeCartoes` com todos os cartões atuais (Tempo em status e Sincronização), teste de contrato verde, `Cartao` e `GavetaDeTasks` compartilhados, tela Mapa de cartões, três temas e AA conferidos.
- 17b: os cinco itens da seção 3 no contrato, cada um com drill igual ao número, fixtures cobrindo as bordas da seção, e diferenças contra o documento de ideias registradas.
- Em ambos: `./gradlew test` e `pnpm test` verdes sem cache, varredura limpa, CI verde, fluxo de entrega do `docs/superpowers/fluxo-de-entrega.md`.
