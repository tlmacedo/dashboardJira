# Progresso: Plano 15 (identidade final, inglês e entrega)

Plano: `docs/superpowers/plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`
Branch: `feat/identidade-visual` (Tarefas 1 a 4). Executor: Claude Code (subagente implementador), sem o usuário presente. As Tarefas 5 a 11 ficam para depois que as telas estiverem prontas.

## Checklist

- [x] Tarefa 1: ativos de marca (registro; caminho alternativo)
- [x] Tarefa 2: componente `Marca` e ativos saneados (frontend 350 testes, 0 falhas; build ok; CSP ok). Saneamento: o `favicon.svg` próprio já não tinha script, `on*`, `foreignObject` nem referência externa; só ganhou `<title>`. Os logos PNG existentes ficaram, isolados em `shell/marca-ativos.ts` (ponto único de troca). Testes de arquivo ficam em `src/a11y` (projeto TypeScript "node", fora do app).
- [x] Tarefa 3: fonte com alternativa de sistema (frontend 356 testes, 0 falhas; +6 em `a11y/fonte.test.ts`; backend `CabecalhosDeSegurancaTest` +1 `cspContinuaComFontSelf`, verde; build ok). `--font` renomeado para `--fonte-texto` (único token de família). Sem `.woff2`, `fontes.css` só documenta como ligar a Mark. Observação: `cspContinuaComFontSelf` já nasce verde (a CSP do Plano 9 existe); ele é uma trava contra afrouxamento futuro, não um teste vermelho.
- [x] Tarefa 4: tokens finais, moldura do tema Sidia e tema sem piscada (frontend 373 testes em 41 arquivos, 0 falhas; +17 em `a11y/tema.test.ts` e `a11y/contraste.test.ts`; build e `verificar-csp` ok; backend `./gradlew test --rerun-tasks` verde, 1497 testes, 0 falhas; varredura de dados pessoais OK)
  - Azul de marca já era único (`#1329a0`, nenhum `#0023a4` em `src/`); o teste `azulDeMarcaUnico` trava isso.
  - Novo token `--line-control` (Claro `#7e8399`, Escuro `#6c7897`, Sidia `#7a81a6`): 3,27:1 a 4,14:1 sobre `surface`, `bg` e `soft` nos três temas. Aplicado em `.btn` (shell), campos de texto e data, `.seg--claro` e `.celula` (Tempo em status) e `.gaveta-link` (gaveta compartilhada). Achado do 17a resolvido: `--line` (1,34 a 1,37:1) ficou só como divisória decorativa e sai no script como `info`, sem reprovar.
  - `scripts/contraste-dos-tokens.mjs` estendido: resolve `var()`, cobre o menu nos três temas, tem pares informativos; sai 0 e agora é rodado dentro de `a11y/contraste.test.ts`.
  - Moldura: 4 px com as quatro cores (`#3e4095`, `#00a85a`, `#f58634`, `#1329a0`), só no tema Sidia, `aria-hidden`.
  - Tema sem piscada: `public/tema-inicial.js` (arquivo próprio, sem defer, antes do CSS e do bundle; mesma chave `painel.tema` do `ThemeProvider`; sem escolha segue `prefers-color-scheme`; Sidia só por escolha). `prefers-reduced-motion` já existia no CSS global e ganhou teste.
  - Passo 5 do plano (capturas de tela em 1440 e 390 px, três temas) NÃO feito: sem navegador no ambiente de execução. Fica para quem tiver o painel aberto.
- [ ] Tarefas 5 a 11: fora deste bloco

## Minor adiados

- RESOLVIDO no Plano 8, T4 (branch `feat/rotas-e-acessibilidade-plano-8`): contorno dos botões segmentados do menu (`--menu-line`, 1,33 a 1,58:1). Agora há o token `--menu-line-control` (3,2 a 5,0:1 nos três temas), usado em `.seg` e `.burger`, com pares no script de contraste e teste em `src/a11y/contraste.test.ts`.
- RESOLVIDO no Plano 8, T4: demais `border: var(--line)` em telas de cartões revisados um a um; só ficaram containers, selos de texto e divisórias (lista versionada em `contraste.test.ts`, que reprova seletor novo de controle com `--line`).
- CORRIGIDO: `app/frontend/scripts/orcamento-bundle.mjs` EXISTE na `main` (Plano 8, T6; `pnpm build && node scripts/orcamento-bundle.mjs`, limite padrão 300 KB gzip). A nota anterior ("não existe na `main`") estava desatualizada.

## Rulings

- Ruling (T1): ponto de parada dos ativos resolvido pelo caminho alternativo, sem perguntar. Motivo: execução autônoma e o `CLAUDE.md` afirma que não há arquivo limpo do logo nem manual. Como reverter: seguir `docs/design/ativos-de-marca.md` (trocar `marca-ativos.ts`, `fontes.css` e os tokens).
- Ruling (estado herdado): o plano cita `src/a11y/`, `claro|escuro|sidia` e `--fonte-texto`; na `main` o tema usa os valores `light|dark|sidia` (chave `painel.tema` do `ThemeProvider`), a fonte era `--font`, a pasta `a11y` não existia, o menu já tinha o logo em PNG (`public/`) e a moldura já existia (5 px, três cores). Usei os nomes da `main` onde são o contrato existente e criei `src/a11y/` para os testes novos.
