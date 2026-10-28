# Gramática do cartão e wireframes dos cards novos

Data: 28/10/2026. Complementa `docs/design/plano-visual-frontend.md` (seções 3 temas, 5 padrões de tela, 6 wireframes) **sem substituí-lo**: tokens, temas, símbolos e regras de conteúdo são os de lá. Spec que usa este documento: `docs/superpowers/specs/2026-10-28-spec-fase-17-cards-de-fluxo-e-gestao.md`.

> **Nota de método.** O pedido era usar a skill "impeccable" para a parte visual. Ela não está instalada neste ambiente (busca nas skills do usuário e no repositório: nenhum resultado), então **nada aqui foi feito por ela**. Este documento segue o plano visual do projeto, e o Plano 17 (Tarefa 7) traz critérios objetivos de revisão visual que qualquer executor consegue conferir; se a skill existir na máquina de quem executar, ela entra como segunda opinião depois desses critérios, nunca no lugar deles.

## 1. Princípios (o que o cartão tem de ser)

1. **Uma pergunta por cartão.** O título responde "o quê"; a natureza responde "que tipo de pergunta".
2. **Explica a si mesmo.** O ⓘ diz o que mostra e como calcula, sem sair da tela.
3. **Todo número leva às tasks.** Mesmo tamanho no cartão e na gaveta; se não for possível (percentual), abre o numerador e avisa.
4. **Estado nunca só por cor:** `✓ ● ▲ ■` mais texto.
5. **Pessoas só em ordem alfabética,** nunca em posição.
6. **Densidade por papel de tela:** telas de trabalho (Abertas, Auditoria) densas; telas de leitura (Central, Meu painel) com mais respiro; o mesmo cartão serve às duas, mudando só a variante.

## 2. Anatomia do cartão

```
┌────────────────────────────────────────────────────┐
│ Abertas                      [ESTOQUE]         (ⓘ) │  1 título · 2 natureza · 3 ajuda
│                                                    │
│  412            ▲ 18 vencidas                      │  4 valor · 5 estado (símbolo + texto)
│  abertas        +12 vs semana passada              │  6 variação (símbolo + texto)
│                                                    │
│  ▂▃▅▆▇▆▅ (opcional, com tabela equivalente)        │  7 minigráfico
│ ──────────────────────────────────────────────────  │
│ n = 412 · atualizado há 8 min · ● dados incompletos│  8 procedência · 9 aviso
│                                        Ver tasks → │  10 ação principal
└────────────────────────────────────────────────────┘
```

| # | Parte | Regra |
|---|---|---|
| 1 | Título | Frase curta, igual ao nome no Mapa de cartões. |
| 2 | Natureza | Chip fixo: Estoque, Fluxo, Prazo, Pessoas, Qualidade (texto + forma, cor só reforça). |
| 3 | Ajuda (ⓘ) | Botão com `aria-expanded`; abre um balão **não modal**: "O que mostra", "Como é calculado", "Fonte", "Visível para". Fecha com `Esc` e devolve o foco ao botão. |
| 4 | Valor | Número grande, formatado pelo idioma, com unidade ("d", "h", "%") e legenda. |
| 5 | Estado | Símbolo + texto ("▲ 18 vencidas"). Quando não há meta, `■` neutro. |
| 6 | Variação | Contra o período anterior de mesmo tamanho; "sem base" quando faltar histórico. |
| 7 | Minigráfico | Opcional. Sempre com a tabela equivalente acessível; nunca o único meio de ler o dado. |
| 8 | Procedência | `n`, momento da última sincronização e, quando houver, aviso `AMOSTRA_PEQUENA`. |
| 9 | Aviso | Dado incompleto leva à Qualidade dos dados (link). |
| 10 | Ação | "Ver tasks →" abre a gaveta de tasks. |

**Área clicável.** O cartão é um `<article>`. A ação principal é **um botão** cuja área é ampliada por CSS (`::after` cobrindo o cartão); o ⓘ fica acima dessa camada (`z-index`). Assim existe um só elemento interativo por finalidade e não há botão dentro de botão. Foco visível de 2 px no token de ação, contraste ≥ 3:1.

**Variantes:** `kpi` (valor grande), `serie` (gráfico no corpo), `tabela` (corpo é tabela com linhas clicáveis), `lista` (até 5 itens com "ver todas").

**Estados do cartão:** carregando (esqueleto com altura fixa, sem salto de layout), vazio ("Nenhuma task neste recorte" + o que ajustar), erro (causa + "tentar de novo"), sem permissão (explica o papel), incompleto (aviso com link). Reaproveita `EstadoDaTela` do Plano 10; até lá, o 17a entrega a versão mínima.

## 3. Classificação (grupo e natureza)

Dois eixos, não confundir:

- **Grupo = onde mora** (o menu: Trabalho em curso, Resultados, Prazos, Pessoas e planejamento, Ação, Auditoria, Governança).
- **Natureza = a pergunta que responde:**

| Natureza | Pergunta | Exemplos (códigos do inventário) |
|---|---|---|
| Estoque | O que há agora? | Abertas (AB), Bloqueios entre tasks, Riscos (RI) |
| Fluxo | Como o trabalho anda? | Tempo em status (TS), Fluxo da semana, Aging WIP, Controle do ciclo |
| Prazo | O que vence ou atrasou? | Metas e prazos (MT), Vencimentos da semana |
| Pessoas | Quanto cabe a quem? | Carga (CP), Capacidade (CA) |
| Qualidade | O dado ou o processo está suspeito? | Qualidade dos dados, Auditoria, Divergência |

No **Mapa de cartões** (Governança) a lista tem filtro por grupo e por natureza, busca por título e uma linha por cartão: título, natureza, "o que mostra", "como calcula", fonte do dado, tela onde vive, quem vê. É gerado do registro.

## 4. Gaveta de tasks (o que abre ao clicar)

```
┌ Gaveta ─────────────────────────────────────────────────── [Fechar ✕] ┐
│ Vencidas · 18 tasks            Squad: Alfa · Período: 90 d             │
│ O que é isto: tasks abertas com vencimento anterior a hoje.        (ⓘ) │
│ [Exportar CSV]  [Abrir no Jira ↗]  (só com o acesso ao Jira ligado)    │
├────────┬──────────┬─────────────┬────────────┬──────────┬──────────────┤
│ Chave  │ Tipo     │ Status      │ Vence      │ Resp.    │ Detalhe      │
│ TG-107 │ Checklist│ Em revisão  │ 21/10/2026 │ ana.silva│ 5 d úteis    │
│ TG-212 │ Review   │ Bloqueado   │ 23/10/2026 │ outra pes│ bloqueada por│
│ …      │          │             │            │          │ TG-190       │
├────────┴──────────┴─────────────┴────────────┴──────────┴──────────────┤
│ 18 de 18 · linhas clicáveis abrem a task (gaveta da task, Plano 10)    │
└────────────────────────────────────────────────────────────────────────┘
```

- Cabeçalho repete **filtros ativos** e a **definição do número** (o mesmo texto do ⓘ).
- Coluna **Detalhe** explica por que a task entrou (ex.: "5 dias úteis de atraso", "bloqueada por TG-190 que vence depois").
- **Abrir no Jira** (I-18): só quando o usuário tiver ligado a flag de acesso ao Jira no `app/.env` e a instância estiver configurada; no máximo 100 chaves por link, com aviso acima disso (alternativa: CSV); nova aba com `noopener`; `access_log` guarda rota e quantidade, nunca as chaves.
- Teclado: foco vai para "Fechar" ao abrir, `Tab` preso na gaveta, `Esc` fecha e devolve o foco; resto da página inerte. (É o comportamento que o `DrillDrawer` de Tempo em status já tem e passa a ser compartilhado.)
- Pessoas em ordem alfabética; pessoa fora da visão aparece como "outra pessoa".

## 5. Wireframes dos cards novos

### 5.1 Fluxo da semana: entrada x saída x iniciadas (I-01)

```
┌ Fluxo da semana                    [FLUXO]                          (ⓘ) ┐
│ Últimas 12 semanas · Squad ▾ · Tipo ▾              [Úteis|Corridos]     │
│                                                                        │
│  entrou ▮   começou ▯   saiu ▨        ▲ backlog crescendo 3 semanas    │
│   14│        ▮                                                         │
│   10│     ▮  ▮ ▯  ▨                                                    │
│    6│  ▮▯▨ ▮▯▨ ▮▯▨ ▮▯▨ ...  (barras agrupadas; cada uma é clicável)     │
│     └──────────────────────────────────────                            │
│      s-11 ...                                    s-0                   │
│ Saldo do backlog: +9 nas 12 semanas (linha tracejada)                  │
│ n = 143 tasks · 12 semanas de segunda a domingo (Manaus) · Ver tabela  │
└────────────────────────────────────────────────────────────────────────┘
```
Barras diferenciadas por **preenchimento e padrão** (cheio, vazado, hachurado), não só cor. Tabela equivalente atrás de "Ver tabela". Clique na barra abre a gaveta com a semana e a série.

### 5.2 Envelhecimento por etapa: Aging WIP (I-05)

```
┌ Envelhecimento por etapa            [FLUXO]                         (ⓘ) ┐
│              P50 ──┤ P85 (faixa histórica da etapa)                     │
│ Em andamento  ●  ●   ●────[░░░░░░░░]──────────────  ▲ 1 acima do normal │
│ Em revisão    ● ●●     [░░░░░░░░░░░░]────  ●                            │
│ Bloqueado     ●        [░░░]──────                                      │
│ eixo: dias úteis na etapa  0   2   4   6   8   10                       │
│ Ponto = uma task (clique abre a task) · ▲ = acima do P85 da etapa       │
│ Etapa com menos de 5 itens no histórico: sem faixa (aviso de amostra)   │
└────────────────────────────────────────────────────────────────────────┘
```
Sem nome de pessoa nos pontos. Ponto acima do P85 tem forma diferente (triângulo) e rótulo textual no resumo à direita.

### 5.3 Bloqueios entre tasks (I-08)

```
┌ Bloqueios entre tasks               [ESTOQUE]                       (ⓘ) ┐
│ ┌Bloqueadas──┐ ┌Bloqueadoras┐ ┌Prazo em conflito┐                       │
│ │    12      │ │     7      │ │     ▲ 3         │   ← cada um abre a    │
│ └ver tasks───┘ └ver tasks───┘ └ver tasks────────┘     gaveta           │
│ Bloqueada   Bloqueadora   Vence (bloq.)  Vence (bloqueadora)            │
│ TG-212      TG-190        23/10/2026     27/10/2026   ▲ conflito        │
│ TG-230      outro squad   28/10/2026     —            ● sem vencimento  │
│ Ordem: por vencimento da bloqueada (nunca por pessoa)                   │
└────────────────────────────────────────────────────────────────────────┘
```
Bloqueadora de pessoa fora da visão: "task de outro squad", sem chave nem nome.

### 5.4 Controle do ciclo e tendência (I-02, I-03) — seção em Fechadas

```
┌ Controle do ciclo                   [FLUXO]    Tipo ▾  □ sem outliers  ┐
│ dias│ ●        ●                                                       │
│  12 │      ●        ─ ─ P85                                            │
│   6 │  ● ●   ● ●●  ●  ━━ média móvel                                    │
│   0 └────────────────────── data de conclusão                          │
│ Tendência mensal (P50 / P85 por tipo, 12 meses)  mês com n<5: aviso    │
└────────────────────────────────────────────────────────────────────────┘
```

### 5.5 Cruzamentos (aba em Resultados)

```
┌ Cruzamentos                        Linhas [Tipo ▾]  Colunas [Status ▾]  ┐
│ Medida (•) Contagem ( ) Idade P50          Filtros: Squad ▾ OS ▾        │
│            A fazer  Em andamento  Em revisão  Bloqueado   Total         │
│ Checklist     8        ▒ 14          6          ▲ 3        31           │
│ Review        2          5         ▒ 9            1        17           │
│ Task          …                                                         │
│ (número + padrão na faixa mais alta; cada célula abre a gaveta)        │
│ Eixo "pessoa": linhas em ordem alfabética, sem total em destaque        │
└────────────────────────────────────────────────────────────────────────┘
```
Só eixos de uma lista fechada; a URL guarda linhas, colunas, medida e filtros.

### 5.6 Mapa de cartões (Governança)

```
┌ Mapa de cartões     [Grupo ▾] [Natureza ▾] [Buscar título…]   [CSV] ┐
│ Grupo: Resultados                                                   │
│ ▸ Tempo em status — TS-02 Eficiência de fluxo       [FLUXO]         │
│     O que mostra: parte do tempo em status ativos…                  │
│     Como calcula: tempo ativo ÷ tempo total (dias úteis), abertas   │
│     Fonte: changelog de status · Tela: Tempo em status · Vê: todos  │
└─────────────────────────────────────────────────────────────────────┘
```

## 6. Responsividade e movimento

- ≥ 1280 px: grade de 3 a 4 cartões por linha nas telas de leitura; cartões de série ocupam 2 colunas.
- 768 a 1279 px: 2 colunas. < 768 px: 1 coluna; tabelas rolam na horizontal dentro do cartão, nunca a página.
- Movimento só em hover/foco e abertura de gaveta (≤ 150 ms), desligado com `prefers-reduced-motion`.
- O CSV carrega os filtros ativos e a definição do número (já é regra do Plano 10).

## 7. Critérios de revisão visual (objetivos)

1. Cada cartão mostra os elementos aplicáveis da seção 2; nenhum número sem unidade.
2. Contraste ≥ 4,5:1 (texto) e ≥ 3:1 (gráfico e borda de componente) nos três temas, calculado a partir dos tokens.
3. Nenhum estado depende só de cor; teste com simulação de daltonismo nas capturas.
4. Navegação só por teclado: Tab chega ao ⓘ, à ação principal e a cada elemento clicável de gráfico, na ordem visual; `Esc` fecha balão e gaveta.
5. Leitor de tela: o cartão anuncia título, valor, estado e "abre as tasks"; gráfico tem tabela equivalente.
6. Sem salto de layout ao carregar; sem rolagem horizontal da página a 360 px.
7. Texto do ⓘ presente em pt e en (teste de tradução), sem frase com mais de 2 linhas no balão.
