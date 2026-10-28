# Progresso: Plano 15 (identidade final, inglês e entrega)

Plano: `docs/superpowers/plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`
Branch: `feat/identidade-visual` (Tarefas 1 a 4). Executor: Claude Code (subagente implementador), sem o usuário presente. As Tarefas 5 a 11 ficam para depois que as telas estiverem prontas.

## Checklist

- [x] Tarefa 1: ativos de marca (registro; caminho alternativo)
- [x] Tarefa 2: componente `Marca` e ativos saneados (frontend 350 testes, 0 falhas; build ok; CSP ok). Saneamento: o `favicon.svg` próprio já não tinha script, `on*`, `foreignObject` nem referência externa; só ganhou `<title>`. Os logos PNG existentes ficaram, isolados em `shell/marca-ativos.ts` (ponto único de troca). Testes de arquivo ficam em `src/a11y` (projeto TypeScript "node", fora do app).
- [ ] Tarefa 3: fonte com alternativa de sistema
- [ ] Tarefa 4: tokens finais, moldura do tema Sidia e tema sem piscada
- [ ] Tarefas 5 a 11: fora deste bloco

## Rulings

- Ruling (T1): ponto de parada dos ativos resolvido pelo caminho alternativo, sem perguntar. Motivo: execução autônoma e o `CLAUDE.md` afirma que não há arquivo limpo do logo nem manual. Como reverter: seguir `docs/design/ativos-de-marca.md` (trocar `marca-ativos.ts`, `fontes.css` e os tokens).
- Ruling (estado herdado): o plano cita `src/a11y/`, `claro|escuro|sidia` e `--fonte-texto`; na `main` o tema usa os valores `light|dark|sidia` (chave `painel.tema` do `ThemeProvider`), a fonte era `--font`, a pasta `a11y` não existia, o menu já tinha o logo em PNG (`public/`) e a moldura já existia (5 px, três cores). Usei os nomes da `main` onde são o contrato existente e criei `src/a11y/` para os testes novos.
