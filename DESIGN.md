# DESIGN.md: o sistema visual do Painel de Squads

Este documento registra o sistema visual **como está no código** (Plano 15, Tarefa 5). Ele não propõe nada novo: onde o código e o plano visual (`docs/design/plano-visual-frontend.md`) divergem, vale o código, e a diferença está dita aqui. A tabela de tokens é conferida contra `app/frontend/src/theme/tokens.css` por um teste (`DesignMdTest.designMdBateComTokens`); se você mudar um token, o teste falha até este arquivo ser atualizado.

Produto e público: [PRODUCT.md](PRODUCT.md). Gramática do cartão e wireframes: [docs/design/cartoes-gramatica-e-wireframes.md](docs/design/cartoes-gramatica-e-wireframes.md). Ativos de marca (logo, fonte): [docs/design/ativos-de-marca.md](docs/design/ativos-de-marca.md).

## 1. Princípios

1. **Operate, não decorar.** O painel é uma ferramenta de trabalho de gestão: cada tela responde a uma pergunta, e cada número leva às tasks que o compõem.
2. **Densa nas telas de trabalho, editorial nas de leitura.** Telas de trabalho (Abertas, Auditoria, Tempo em status) usam a variante densa; telas de leitura (Central de atenção, Meu painel, Meu squad) têm mais respiro. O mesmo cartão serve às duas, mudando só a variante.
3. **A cor nunca é o único sinal.** Estado leva símbolo e texto; série de gráfico leva padrão de preenchimento e forma de marcador.
4. **Sem ranking de pessoas.** Tabelas de pessoas ficam em ordem alfabética, sem posição, nota ou cor de "melhor/pior". Indicadores de pessoa são capacidade e carga, nunca desempenho.
5. **Indício, não prova.** Sinais de Auditoria e de Integridade são redigidos e desenhados como motivo de conversa e conferência, nunca como conclusão.
6. **Três temas, dois idiomas.** Claro, Escuro e Sidia; pt-BR e inglês com as mesmas chaves (`app/frontend/src/i18n/pt.json` e `en.json`).

## 2. Temas

O tema é escolhido por `data-theme` na raiz do documento, com os valores `light`, `dark` e `sidia` (chave `painel.tema`, gravada pelo `ThemeProvider`). Claro é o `:root`; **Escuro** (`dark`) e **Sidia** só redefinem o que muda. Sem escolha salva, vale `prefers-color-scheme`; o tema Sidia só entra por escolha. O arquivo `public/tema-inicial.js` aplica o tema antes da primeira pintura (sem piscada) e é carregado como arquivo próprio, por causa da CSP (`script-src 'self'`).

- **Claro** (`light`): fundo `#f7f7f7`, superfície branca, azul de marca `#1329a0` como ação primária.
- **Escuro** (`dark`): fundo `#101521`, superfície `#171e2e`; a ação primária clareia para `#8fa8ff` para manter o contraste.
- **Sidia** (`sidia`): superfícies claras com tom azulado, menu em azul de marca `#1329a0` e, no topo, a **moldura**: uma faixa de 4 px com as quatro cores do logo (`#3e4095`, `#00a85a`, `#f58634`, `#1329a0`). A moldura é decorativa (`aria-hidden`), existe só neste tema e nunca indica estado.

## 3. Tokens

Valor efetivo em cada tema: o do bloco do tema, ou o de `:root` quando o tema não o redefine. Todo componente usa só estes tokens.

| Token | Claro | Escuro | Sidia | Uso |
|---|---|---|---|---|
| `--bg` | `#f7f7f7` | `#101521` | `#f4f6ff` | Fundo da página. |
| `--surface` | `#ffffff` | `#171e2e` | `#ffffff` | Superfície de cartão, tabela, gaveta e campo. |
| `--text` | `#181818` | `#eef1f8` | `#10163a` | Texto principal. |
| `--text2` | `#595959` | `#b4bccf` | `#4a5073` | Texto secundário (legenda, procedência). |
| `--line` | `#dadde6` | `#2a3449` | `#d6dbf2` | Divisória decorativa de cartão e tabela; nunca borda de controle. |
| `--line-control` | `#7e8399` | `#6c7897` | `#7a81a6` | Borda de controle (campo, botão, segmentado): 3:1 ou mais sobre superfície, fundo e soft. |
| `--soft` | `#eef0f6` | `#1e2740` | `#e9edff` | Fundo suave (cabeçalho de tabela, chip, linha selecionada). |
| `--primary` | `#1329a0` | `#8fa8ff` | `#1329a0` | Ação primária e o azul de marca. |
| `--on-primary` | `#ffffff` | `#0b1030` | `#ffffff` | Texto sobre a cor primária. |
| `--accent` | `#772feb` | `#b9a0ff` | `#772feb` | Violeta de apoio; reservado, nenhum componente o usa hoje. |
| `--ok` | `#0b7a3b` | `#77ff94` | `#0b7a3b` | Estado ok (sempre com ✓ e texto). |
| `--warn` | `#9a5b00` | `#ffb74d` | `#9a5b00` | Estado de atenção (sempre com ● e texto). |
| `--bad` | `#b3261e` | `#ff8a80` | `#b3261e` | Estado de problema (sempre com ▲ e texto). |
| `--menu` | `#ffffff` | `#0d121c` | `#1329a0` | Fundo do menu lateral. |
| `--menu-text` | `#181818` | `#eef1f8` | `#ffffff` | Texto do menu. |
| `--menu-text2` | `#595959` | `#b4bccf` | `#c9d4ff` | Texto secundário do menu. |
| `--menu-line` | `#dadde6` | `#222b40` | `#3a4db8` | Divisória do menu (decorativa). |
| `--menu-line-control` | `#7e8399` | `#6c7897` | `#97a6ff` | Contorno dos botões do menu (segmentados e ☰): 3:1 ou mais sobre o menu e o item ativo. |
| `--menu-active` | `#e8ecff` | `#1e2740` | `#2438b0` | Fundo do item ativo do menu. |
| `--menu-marca` | `var(--primary)` | `var(--primary)` | `#77ff94` | Cor do nome do painel no menu. |
| `--s1` | `#1329a0` | `#8fa8ff` | `#1329a0` | Série de apoio herdada dos protótipos; reservada, nenhum componente a usa hoje (os gráficos usam --cat-*). |
| `--s2` | `#772feb` | `#b9a0ff` | `#772feb` | Idem (reservada). |
| `--s3` | `#b85a0b` | `#f58634` | `#b85a0b` | Idem (reservada). |
| `--s4` | `#0b7a3b` | `#77ff94` | `#0b7a3b` | Idem (reservada). |
| `--cat-1` | `#0072b2` | `#4da3db` | `#0072b2` | Paleta categórica dos gráficos, série 1. |
| `--cat-2` | `#b07800` | `#e69f00` | `#b07800` | Série 2. |
| `--cat-3` | `#00855f` | `#2fc79a` | `#00855f` | Série 3. |
| `--cat-4` | `#b5528c` | `#e58fbf` | `#b5528c` | Série 4. |
| `--cat-5` | `#2a7fb8` | `#56b4e9` | `#2a7fb8` | Série 5. |
| `--cat-6` | `#c25400` | `#f07a2b` | `#c25400` | Série 6. |
| `--focus` | `#3333ff` | `#8fa8ff` | `#3333ff` | Contorno de foco (2 px). |
| `--frame` | `none` | `none` | `linear-gradient(90deg, #3e4095 0 25%, #00a85a 25% 50%, #f58634 50% 75%, #1329a0 75% 100%)` | Moldura de quatro cores do tema Sidia; decorativa. |
| `--frame-altura` | `0px` | `0px` | `4px` | Altura da moldura (0 fora do tema Sidia). |
| `--fonte-texto` | `system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif` | `system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif` | `system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif` | Pilha de fontes do painel (fonte do sistema). |

Observação sobre o menu do tema Sidia: o texto do menu usa `#ffffff` e `#c9d4ff` sobre `#1329a0`; no foco, o contorno do menu muda para `#77ff94` para contrastar com o azul.

## 4. Tipografia

- **Família:** `--fonte-texto`, a pilha do sistema. A fonte Mark da Sidia **não foi fornecida** (nem a licença), então nada é baixado de terceiros e a CSP mantém `font-src 'self'`. Para ligar a Mark no futuro, siga o comentário de `app/frontend/src/theme/fontes.css`.
- **Escala em uso** (px, como o CSS atual): corpo 14 com entrelinha 1,45; texto de apoio e tabelas 12 a 13 (12,5 é o mais comum); rótulos e chips 11 a 11,5; títulos de seção 15 a 16; títulos de tela 20 a 22; números grandes de KPI até 30.
- **Pesos:** 400 para texto, 600 para títulos e rótulos (o mais usado), 650 e 700 em destaques pontuais.
- **Números:** sempre formatados pelo idioma (`shared/format.ts`) e renderizados como texto.

## 5. Espaçamento, forma e movimento

- **Espaçamento:** passos de 4, 6, 8 e 10 px em `gap` e `padding` (4 e 8 são os mais usados).
- **Raios:** 6 px (botões e campos), 8 a 10 px (cartões e gaveta), 999 px (chips).
- **Foco:** `:focus-visible` com contorno de 2 px no token `--focus`, deslocado 2 px.
- **Movimento:** quase nenhum (há transições de opacidade de 0,15 s e a entrada da gaveta); sob `prefers-reduced-motion: reduce` elas são desligadas (regras em `shell/shell.css`, `shared/cartao.css` e `shared/gaveta.css`).
- **Largura:** abaixo de 820 px o menu vira um botão e a lista abre sob demanda; a gaveta (640 px) e algumas telas (560 a 720 px) têm regras próprias para o celular.

## 6. Componentes do kit

Os componentes ficam em `app/frontend/src/shared` e `app/frontend/src/shell`. Tudo que vem da API entra como **texto**, nunca como HTML.

| Componente | Arquivo | O que faz |
|---|---|---|
| Cartão | `shared/Cartao.tsx` | Gramática do cartão: título, natureza (chip com glifo e texto), ajuda ⓘ, valor, estado (símbolo + texto), variação, procedência (`n`, "atualizado há") e aviso. Variantes `kpi`, `serie`, `tabela` e `lista`; situações carregando, vazio, erro e sem permissão, com altura mínima fixa para não saltar o layout. O cartão inteiro é a área clicável da ação "Ver tasks". |
| Ajuda do cartão | `shared/AjudaDoCartao.tsx` | Botão ⓘ com `aria-expanded`; balão não modal com "O que mostra", "Como é calculado", "Fonte" e "Visível para". `Esc` fecha e devolve o foco. |
| Gaveta de tasks | `shared/GavetaDeTasks.tsx` | Lista de tasks de um número do cartão, com o mesmo total. Foco no botão Fechar ao abrir, `Tab` preso, `Esc` fecha e devolve o foco; o resto da página fica inerte. Exporta CSV; o link "Abrir no Jira" vem pronto do backend e só existe com o acesso ao Jira ligado. |
| Tabela virtual | `shared/TabelaVirtual.tsx` | Tabela ordenável por qualquer coluna (`aria-sort`) que vira virtual acima de 200 linhas (`role="grid"`, `aria-rowcount`). Teclado: setas, PageUp, PageDown, Home, End e Enter. Sem coluna de posição. |
| Estado da tela | `shared/EstadoDaTela.tsx` | Carregando (esqueleto), vazio (com "Limpar filtros"), erro (com "Tentar de novo"), filtro inválido, sem permissão, sessão e incompleto. Erro com dado anterior na tela não derruba a tela. |
| Página de cartões | `shared/CardPage.tsx` | Moldura das telas de trabalho e prazos: título, pergunta, filtros, estados, avisos e exportação CSV. |
| Filtros | `shared/Filtros.tsx` | Barra de filtros (período, squad, pessoa, tipo, prioridade, campos do dicionário) guardada na URL. |
| Marca | `shell/Marca.tsx` | Logo como arquivo `<img>` (nunca SVG inline) seguido do nome em texto; se a imagem faltar ou falhar, só o nome. |
| Gráficos | `shared/graficos/Barras.tsx`, `shared/graficos/Linhas.tsx`, `shared/graficos/Dispersao.tsx`, `shared/graficos/Legenda.tsx`, `shared/graficos/TabelaEquivalente.tsx` | SVG próprio, sem biblioteca. Toda série tem cor, padrão e forma; legenda sempre visível; os mesmos dados saem numa tabela equivalente ("Ver dados em tabela"). |

## 7. Símbolos de estado

`✓` ok, `●` atenção, `▲` problema, `■` neutro. O símbolo vem **sempre com texto** ("▲ 18 vencidas"). A natureza do cartão tem glifo próprio junto do nome: Estoque `▤`, Fluxo `⇢`, Prazo `◷`, Pessoas `◉`, Qualidade `◈`.

## 8. Gráficos

A paleta categórica (`--cat-1` a `--cat-6`, Okabe-Ito ajustada) tem pelo menos 3:1 sobre a superfície nos três temas. Em `shared/graficos/paleta.ts`, a série de índice `i` usa o item `i % 6` de três listas, e a sétima repete a primeira nas três dimensões:

- **cor:** `--cat-1` a `--cat-6`;
- **padrão de preenchimento:** `solido`, `listras`, `pontos`, `xadrez`, `diagonal`, `ondas`;
- **forma do marcador:** `circulo`, `quadrado`, `triangulo`, `losango`, `cruz`, `estrela`;
- **traço da linha:** contínuo na primeira série e tracejados distintos nas demais.

Ninguém precisa distinguir cores para ler um gráfico: a legenda mostra padrão, forma e nome, e a tabela equivalente traz os números.

## 9. Acessibilidade

- **WCAG AA nos três temas:** texto com contraste de pelo menos 4,5:1; gráficos, bordas de controle e ícones de pelo menos 3:1. O script `app/frontend/scripts/contraste-dos-tokens.mjs`, rodado por `a11y/contraste.test.ts`, confere os pares de tokens; `--line` (divisória decorativa) sai só como informação.
- **Laranja e verde da marca nunca como texto** sobre fundo claro (`#f58634` e `#00a85a` ficam na moldura e em detalhes decorativos).
- **Foco visível** de 2 px em todo controle; ordem de tabulação seguindo a leitura; gaveta e diálogos prendem o foco e devolvem-no ao fechar.
- **`prefers-reduced-motion`** desliga transições e animações.
- **Teclado:** tudo que se faz com o mouse se faz com o teclado, inclusive a tabela virtual.
- **Leitor de tela:** tabelas com cabeçalhos, esqueleto de carregamento anunciado, gráficos com tabela equivalente.

## 10. Idioma

pt-BR e inglês completos, com as mesmas chaves nos dois arquivos de tradução (teste de paridade). Os termos dos guias, das perguntas frequentes e do treinamento são os das telas ("No prazo" e "No prazo ajustado", "Indício, não prova", "sem capacidade", "Dias úteis" e "Dias corridos" em pt-BR; "On time" e "Adjusted on time", "Evidence, not proof", "no capacity", "Business days" e "Calendar days" em inglês). Datas e números seguem o idioma e o fuso America/Manaus.

## 11. O que não fazer

- **Ranking de pessoas**, ordem por desempenho, nota ou cor de "melhor/pior" em tabela de pessoas.
- **Cor como único sinal** de estado, série de gráfico ou diferença entre itens.
- **Laranja e verde da marca como texto** sobre fundo claro.
- **Borda de controle com `--line`:** use `--line-control`.
- **Tema só no JavaScript do bundle:** o tema inicial vem do arquivo `tema-inicial.js`, antes do CSS.
- **SVG do logo inline**, fonte ou imagem de fora do domínio, ou script inline (a CSP não deixa).
- **Mostrar como conclusão** o que é indício (Auditoria, Integridade).
- **Usar dado real em captura ou material de treinamento:** só dados de demonstração.
