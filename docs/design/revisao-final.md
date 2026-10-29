# Revisão visual final — três temas e responsividade (Plano 8, T5 e T4)

Estado em 29/10/2026. A revisão foi feita **por inspeção de código** (CSS, tokens, componentes), por testes jsdom e pelo script de contraste, porque o ambiente do agente não tem navegador. A **rodada com captura de tela fica pendente** e é a única parte que falta da T5 (seção 4).

Provas executáveis: `app/frontend/src/a11y/` (`contraste.test.ts`, `teclado.test.tsx`, `rotulos.test.tsx`, `temasEResponsividade.test.ts`) e `node scripts/contraste-dos-tokens.mjs`.

## 1. Achados e correções

| # | Achado | Onde | Correção | Prova |
|---|---|---|---|---|
| 1 | Contorno dos botões segmentados do menu (papel, tema, idioma) e do botão ☰ usava `--menu-line` (divisória decorativa): 1,33:1 (Escuro), 1,36:1 (Claro), 1,58:1 (Sidia). Componente de interface exige 3:1 (WCAG 1.4.11). | `shell.css` (`.seg`, `.burger`) | Token novo `--menu-line-control` nos três temas: 3,75/3,20 (Claro), 4,26/3,36 (Escuro), 4,97/4,06 (Sidia) sobre o menu e sobre o item ativo. `--menu-line` ficou só como divisória. | `menuControleTem3para1NosTresTemas`, `controlesDoMenuUsamABordaDeControleDoMenu`, pares novos no script |
| 2 | Botão "Sair" no rodapé do menu: `.btn` tem fundo `--surface` e o texto herdava `--menu-text2` (`#c9d4ff` no Sidia), ou seja, quase branco sobre branco (cerca de 1,5:1). O script de contraste não pegava porque o par não estava na lista. | `shell.css` (`.foot .btn`) | `color: var(--text)` | `botaoDoRodapeDoMenuTemTextoDaSuperficieEmVezDoTextoDoMenu` |
| 3 | Botão × do chip de filtro com 22 x 22 px (WCAG 2.2, 2.5.8, mínimo 24 x 24 px). | `tempo.css` (`.chip button`) | 24 x 24 px | `alvoDeToqueDeAoMenos24px` |
| 4 | O ciclo de Tab das gavetas e janelas só incluía `button`, `[href]` e `[tabindex="0"]`: campo de texto, lista e área de texto dentro de uma gaveta ficavam fora do ciclo (Shift+Tab no primeiro item não ia para o último). | `shared/useModalAcessivel.ts` | Seletor inclui `input`, `select`, `textarea` e `summary` (não desabilitados) | `tabFicaPresoInclusiveEmCamposDeFormulario` |
| 5 | Bordas `var(--line)` (divisória de cerca de 1,3:1) em controles: revisados todos os usos (19 contornos completos e 15 divisórias de um lado só). Nenhum controle usa `--line`; só containers (cartão, barra de filtros, balão de ajuda, aviso, diálogo), selos de texto (natureza, lote), esqueleto, `fieldset` e separadores de um lado. | vários `.css` | Nenhuma mudança de estilo; a lista revisada ficou versionada para que seletor novo com `--line` reprove | `bordaDecorativaSoEmContainersRevisados`, `controleNuncaUsaABordaDecorativa` |

Itens verificados sem defeito: foco visível global de 2 px (`:focus-visible`, `--focus` com 6,4 a 8,0:1 sobre a página) e exceção do cartão inteiro clicável (o anel de 2 px passa para o `::after`); `prefers-reduced-motion` desliga transição e animação em tudo; todas as 387 tags `<th>` do código têm `scope`; `lang` do documento acompanha o idioma; botões, links e campos da tela Abertas têm nome acessível; estados ✓ ● ▲ sempre com texto; tabela virtual é `grid` com nome, `aria-rowcount`, `aria-sort`, `aria-activedescendant` e rolagem por teclado; `main` recebe o atalho "Pular para o conteúdo" como primeiro foco; as cinco gavetas e janelas modais têm foco em "Fechar", Esc fecha e devolve o foco, e o resto da página fica `inert`.

## 2. Tokens dos três temas

- Claro (`:root`), Escuro (`[data-theme='dark']`) e Sidia (`[data-theme='sidia']`) passam todos os pares do script (texto 4,5:1; foco, borda de controle, marca do menu e séries 3:1): `pares abaixo do mínimo: 0`.
- O Escuro redefine todos os tokens de fundo, texto, estado, menu, foco e as seis cores categóricas (e declara `color-scheme: dark`, para campos nativos). O Sidia redefine bordas, fundo, texto e menu, e herda do Claro superfície e estado (já aprovados).
- Toda `var(--x)` usada no CSS tem definição; só duas cores literais ficam fora dos tokens (foco do menu Sidia `#77ff94`, 8,9:1 sobre o menu, e a faixa vermelha do modo desenvolvimento `#b3261e` com texto branco, 6,5:1), cada uma com contraste testado.
- Cor da marca (`#F58634`, `#13DC14`) nunca é cor de texto (teste `laranjaEVerdeDaMarcaNuncaComoTexto`).

## 3. Responsividade (1440 e 390 px), por inspeção do CSS

- 1440 px: a casca é `240px minmax(0, 1fr)`; as telas densas limitam a largura (`.tempo` em 1280 px); nenhuma regra `max-width` abaixo de 1440 esconde conteúdo.
- 390 px (358 px úteis com 16 px de margem): abaixo de 820 px a casca vira uma coluna e o menu vira o botão ☰ (`aria-expanded`, `aria-controls`); `main` fica com 16 px; os KPIs de Tempo em status viram uma coluna abaixo de 640 px; as linhas de barras reorganizam em áreas abaixo de 980 px; os gráficos SVG têm `min-width` de 480 a 520 px **dentro de contêiner com `overflow-x: auto`**; tabelas rolam dentro do cartão (`.tabela-rolagem`); a gaveta ocupa `min(920px, 100%)`; nenhuma largura fixa acima de 358 px fora de `@media`.
- O `viewport` não bloqueia zoom (nada de `user-scalable=no` nem `maximum-scale`).
- Limite desta inspeção: ela não vê quebra de linha real, sobreposição nem corte de texto em inglês. Isso só a captura mostra.

## 4. PENDENTE: rodada com captura de tela (quem tiver o painel aberto)

Não foi feita por falta de navegador no ambiente do agente. Procedimento (plano 8, T5, passos 1 a 4), **com banco descartável** e sem tocar nos dados locais:

```
cd app
DASHBOARD_DEMO_DATA=true DATABASE_PATH=data/revisao.db sh iniciar.sh      # PowerShell: $env:DASHBOARD_DEMO_DATA='true'; $env:DATABASE_PATH='data/revisao.db'; .\iniciar.ps1
```

A demonstração só sobe com `DASHBOARD_DEV_MODE=true` e `JIRA_LIVE_ENABLED=false` em `app/.env`; nenhuma requisição ao Jira é feita. Ao fim, pare o painel e apague `app/data/revisao.db` (e `-wal`, `-shm`).

Capturar, com Chrome em modo headless ou as ferramentas do navegador, **todas as telas do `nav.ts`** em 1440 e 390 px, nos temas Claro, Escuro e Sidia (seletor do menu; preferência `painel.tema`), e em pt-BR e inglês só na Central, no Meu painel e na Auditoria. Conferir contra `docs/design/plano-visual-frontend.md`:

- [ ] gramática única da seção 5 (cabeçalho, filtros em chips, KPIs, no máximo dois gráficos e uma tabela);
- [ ] densa nas telas de trabalho e editorial nas de leitura;
- [ ] faixa vermelha do modo desenvolvimento visível nos três temas;
- [ ] textos sem corte em inglês (menu, KPIs, chips, cabeçalhos de tabela);
- [ ] tabelas com rolagem dentro do cartão a 390 px, sem rolagem horizontal da página;
- [ ] botões segmentados do menu com contorno visível nos três temas (correção 1) e "Sair" legível no Sidia (correção 2);
- [ ] foco por teclado visível em cada tela (anel de 2 px), inclusive sobre o menu azul do Sidia;
- [ ] gaveta de tasks a 390 px (topo em coluna, tabela rolando por dentro).

Registrar aqui os achados da captura e as correções (no máximo uma rodada de confirmação, como diz o plano), e só então marcar a T5 como concluída.

## 5. Minor adiados

- A checagem automática de "gráfico com `min-width` dentro de rolagem" é por arquivo CSS (não por seletor): um arquivo com um `overflow-x: auto` qualquer a satisfaz. A captura cobre o resto.
- Campos nativos sem estilo próprio (`select`, `input[type=number]`, `textarea` da decisão) dependem do contorno padrão do navegador, que com `color-scheme` fica legível nos três temas; confirmar na captura e, se preciso, dar a eles `--line-control`.
