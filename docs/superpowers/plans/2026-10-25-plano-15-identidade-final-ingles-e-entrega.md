# Fase 15: Identidade visual final, inglês e entrega ao time — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Para o ClineSR, isso equivale à seção 4 de `docs/guia-execucao-outra-ia.md`.

**Objetivo (Goal):** Fechar o projeto do ponto de vista de quem usa: logo e fonte oficiais da Sidia (quando o usuário fornecer os arquivos), tokens finais com o azul de marca único, a moldura de quatro cores no tema Sidia e sem piscada de tema, `DESIGN.md` derivado do que foi construído, guia do painel e perguntas frequentes em pt-BR e inglês, material de treinamento por papel, sessões de treinamento registradas, dono da manutenção definido e um checklist de entrega ao time.

**Arquitetura (Architecture):** Nenhum card novo e nenhuma regra de cálculo nova. A fase acrescenta:
- ativos de marca saneados e servidos do próprio domínio (CSP `font-src 'self'` do Plano 9), com caminho alternativo se os arquivos não vierem;
- testes estáticos que amarram documentos ao código: `DESIGN.md` × `tokens.css`, guia em inglês × guia em pt-BR × `nav.ts`, treinamento × papéis do menu, checklist de entrega × evidências;
- registros de entrega sem nomes de pessoas.

**Stack (Tech Stack):** React + TypeScript (Vite, Vitest, Testing Library), CSS com tokens; Kotlin 2 + Ktor 3.3.1 só nos testes de documentos (mesmo padrão de `GuiaDoPainelDocTest`).

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-15-identidade-final-ingles-e-entrega.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 2, 4.5, 9 "Fonte tipográfica", 10, 11). Visual: `docs/design/plano-visual-frontend.md` (seções 2, 2.1, 3, 8 e 9). Lacunas de origem: `docs/superpowers/cobertura-do-projeto.md`, M7 (guia), M9, M10 e B1.

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere na `main` que as Fases 8, 9 e 14 (ao menos o aceite da T13 do 14b) foram mescladas e que os nomes abaixo existem. Se algum mudou, use o da `main`, atualize só os nomes deste plano e registre `Ruling:` no progresso.

| Nome ou capacidade | Origem |
|---|---|
| `app/frontend/src/theme/tokens.css` (temas `claro`, `escuro`, `sidia`), `ThemeProvider`, `Shell`, `nav.ts` (ids e papéis de cada item) | Plano 1; Plano 8, T4 e T5 |
| `a11y/contraste.ts`, `contrasteDosTokensNosTresTemas`, `laranjaEVerdeDaMarcaNuncaComoTexto`, `paridadeDeChavesPtEn` | Plano 8, T4; Plano 5, T4 |
| `scripts/orcamento-bundle.mjs` (bundle principal < 300 KB gzip) | Plano 8, T6 |
| `docs/manual/guia-do-painel.md`, `GuiaDoPainelDocTest` (`guiaCitaCadaItemDoMenu`, `guiaCitaCadaFicha`) | Plano 8, T10 |
| `CabecalhosDeSeguranca` com CSP `font-src 'self'; img-src 'self' data:` e `CabecalhosDeSegurancaTest` | Plano 9, T3 |
| `DASHBOARD_DEMO_DATA` (dados fictícios para capturas) | Plano 4A, T10 |
| `docs/validacao/aceite-do-gestor.md` | Plano 14, T13 |
| Runbooks de operação e de incidentes; rotação de segredos | Plano 12 (confirmar caminhos) |
| `docs/implantacao.md` e `docs/implantacao/` | Planos 8, T7 e 13 |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- **Jira:** a única trava é `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`); este plano não acessa o Jira. Capturas de tela e material de treinamento usam **só dados de demonstração** (`DASHBOARD_DEMO_DATA=true`, `DASHBOARD_DEV_MODE=true`, flag do Jira `false`).
- **Nenhum ativo inventado.** Sem logo oficial, o menu mostra o nome "Painel de Squads" em texto (plano visual, seção 2). Sem a fonte Mark e a licença, vale a pilha de sistema (`system-ui`, `Segoe UI`, `Roboto`, `Helvetica Neue`). Nada é baixado de terceiros.
- **Licença da fonte e visibilidade do repositório:** a fonte só entra no repositório se a licença permitir **e** o repositório for privado; caso contrário, segue o caminho alternativo do ponto de parada da Tarefa 1.
- **Azul de marca decidido: `#1329A0`** (plano visual 2.1 e spec mestra 4.5, cor medida no logo). Só se reabre se um manual de marca entregue disser outra coisa.
- WCAG AA nos três temas (texto ≥ 4,5:1; gráficos e bordas ≥ 3:1); cor nunca é o único sinal; foco visível de 2 px; `prefers-reduced-motion`.
- **Sem nomes de pessoas** em material de treinamento, registros de sessão, checklist de entrega e documentos de manutenção: papéis ("gerente", "dono da manutenção"), contagens e datas. Material da Integridade só no material do gerente.
- pt-BR e inglês com as mesmas seções e chaves.
- Commits em Conventional Commits pt-BR (`feat(ui): ...`, `docs(manual): ...`, `docs(entrega): ...`). Comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **SVG do logo com script, manipulador `on*`, `<foreignObject>` ou referência externa** (vetor de XSS servido do próprio domínio) → `logoSemScriptNemReferenciaExterna` (T2).
2. **Fonte carregada de fora do domínio ou quebrando a CSP** → `fonteServidaDoProprioDominio`, `cspContinuaComFontSelf` (T3).
3. **Token do `DESIGN.md` diferente do `tokens.css`** (documento que mente) → `designMdBateComTokens` (T5).
4. **Guia em inglês com seção faltando ou termo traduzido diferente da tela** → `guiaEnMesmasSecoes`, `termosDoGuiaIguaisAosDaTela` (T6).
5. **Material de treinamento mostrando tela de outro papel ou Integridade fora do gerente** → `treinamentoSoComTelasDoPapel`, `integridadeSoNoMaterialDoGerente` (T8).
6. **Nome de pessoa em registro de treinamento ou de entrega** → `registroSemNomes` (T9, T10).

---

## Arquivos (mapa)

```
app/frontend/src/assets/marca/{logo-sidia.svg, favicon.svg}          # só com o arquivo do usuário (T2)
app/frontend/public/fonts/                                            # só se a licença permitir (T3)
app/frontend/src/theme/{tokens.css, fontes.css}, app/frontend/index.html
app/frontend/src/shell/{Marca.tsx, Marca.test.tsx, Shell.tsx}
app/frontend/src/a11y/{marca.test.ts, fonte.test.ts, tema.test.ts}
DESIGN.md
docs/manual/{guia-do-painel.md, panel-guide.md, perguntas-frequentes.md, faq.md}
docs/treinamento/{usuario.md, squad-leader.md, coordenador.md, gerente.md, roteiro-da-sessao.md, registro.md, img/}
docs/entrega/{checklist-de-entrega.md, manutencao.md}
app/backend/src/test/kotlin/br/sidia/painel/docs/{DesignMdTest.kt, GuiaEnDocTest.kt, TreinamentoDocTest.kt, EntregaDocTest.kt}
CLAUDE.md, PRODUCT.md, README.md, HANDOFF.md, docs/superpowers/plans/2026-10-24-indice-dos-planos.md
```

---

### Tarefa 1: Ativos de marca (ponto de parada)

**Arquivos:** Criar `docs/design/ativos-de-marca.md` (o que foi recebido, data, licença em uma linha, decisão); Teste `docs/DesignMdTest.kt` (`ativosDeMarcaRegistrados`).

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** (três perguntas separadas, opções na ordem, a primeira "(Recomendada)"):
  1. **Logo.**
     - "Fornecer o logo limpo em SVG (Recomendada)": consequência: a T2 saneia e usa no menu e no favicon.
     - "Fornecer só PNG transparente (≥ 512 px)": consequência: usado como imagem, sem recolorir por tema.
     - "Ainda não há logo oficial": consequência: o menu continua com o nome em texto; a T2 só cria o componente `Marca` com o caminho alternativo.
  2. **Fonte Mark e licença.**
     - "Fornecer os arquivos `.woff2` e a licença, que permite uso web e cópia em repositório privado (Recomendada)": consequência: a T3 versiona os arquivos em `public/fonts/`.
     - "Fornecer os arquivos, mas a licença não permite cópia no repositório": consequência: os arquivos ficam fora do git e entram na imagem no build a partir de um diretório local ignorado (`app/frontend/fontes-licenciadas/`), documentado na T3; o CI usa a pilha de sistema.
     - "Não usar a Mark": consequência: pilha de sistema permanente (decisão registrada).
  3. **Manual de marca.**
     - "Não há manual: manter o azul `#1329A0` e as cores do plano visual (Recomendada)".
     - "Há manual: anexar; a T4 confere cada cor contra ele e reabre a decisão do azul só se ele divergir".
- [ ] **Passo 2: Registrar** as respostas, as datas e o caminho dos arquivos recebidos (fora do git até a T2/T3 decidir). Sem resposta, seguir com as Tarefas 4 a 11 pelo caminho alternativo e registrar a pendência no `HANDOFF.md`.
- [ ] **Passo 3: Rodar** `./gradlew test --tests '*DesignMdTest*'` (`ativosDeMarcaRegistrados`: o documento existe e cita as três decisões) → PASSA. **Passo 4: Commit** `docs(design): registro dos ativos de marca recebidos`.

---

### Tarefa 2: Logo saneado e componente `Marca`

**Arquivos:** Criar `app/frontend/src/assets/marca/logo-sidia.svg` e `favicon.svg` (só com o arquivo do usuário), `shell/Marca.tsx`, `shell/Marca.test.tsx`, `a11y/marca.test.ts`; Modificar `shell/Shell.tsx`, `index.html` (favicon), `i18n/{pt,en}.json`.

**Interfaces:**
- `export function Marca(props: { compacta?: boolean }): JSX.Element` → com logo: `<img src=... alt="Sidia">` seguido do nome do painel em texto; sem logo: só o texto "Painel de Squads" / "Squads Dashboard". O SVG entra como **arquivo** (`<img>`), nunca inline com `dangerouslySetInnerHTML`.
- Saneamento manual do SVG recebido (registrar no progresso o que foi removido): sem `<script>`, sem atributos `on*`, sem `<foreignObject>`, sem `href`/`xlink:href` externos, sem `<style>` com `@import`; `viewBox` presente; `<title>Sidia</title>`.
- No tema Sidia (menu azul), usar a variante clara do logo se o usuário a fornecer; senão, o logo sobre um fundo branco arredondado (contraste ≥ 3:1 com o menu).

- [ ] **Passo 1: Testes que falham:** `logoSemScriptNemReferenciaExterna` (lê o SVG como texto: nenhum dos padrões proibidos), `logoTemViewBoxETitle`, `marcaComLogoTemAltEnome`, `marcaSemLogoMostraTexto` (com o módulo do logo ausente por mock), `faviconDeclarado` (`index.html`), `paridadeDeChavesPtEn` (continua).
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shell src/a11y` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira, `pnpm build` e `node scripts/orcamento-bundle.mjs`. **Passo 5: Commit** `feat(ui): logo da Sidia saneado no menu e no favicon`.

Sem logo (T1, terceira opção): criar só `Marca.tsx` com o texto, os testes `marcaSemLogoMostraTexto` e `paridadeDeChavesPtEn`, e registrar `Ruling:`.

---

### Tarefa 3: Fonte da marca com alternativa de sistema

**Arquivos:** Criar `theme/fontes.css`, `a11y/fonte.test.ts`; Modificar `theme/tokens.css` (`--fonte-texto`), `main.tsx` (importa `fontes.css`), e `.gitignore` (só no caminho "licença não permite cópia"); Teste backend `http/CabecalhosDeSegurancaTest.kt` (caso `cspContinuaComFontSelf`).

**Interfaces:**
- `@font-face { font-family: "Mark"; src: url("/fonts/mark-regular.woff2") format("woff2"); font-display: swap; }` e as variações recebidas (no máximo regular, médio e negrito); `--fonte-texto: "Mark", system-ui, "Segoe UI", Roboto, "Helvetica Neue", sans-serif`.
- Sem os arquivos: `fontes.css` não declara `@font-face` e `--fonte-texto` começa em `system-ui`.

- [ ] **Passo 1: Testes que falham:** `fonteServidaDoProprioDominio` (nenhum `url(` com `http`), `fontDisplaySwap`, `pilhaDeFallbackMantida` (sempre termina em `sans-serif` e contém `system-ui`), `semFonteSemFontFace` (caminho alternativo), `cspContinuaComFontSelf` (backend: o cabeçalho tem `font-src 'self'` e não ganhou domínio externo).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/a11y` e `./gradlew test --tests '*CabecalhosDeSegurancaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, `pnpm build`, `node scripts/orcamento-bundle.mjs` (fontes ficam fora do bundle JS). **Passo 5: Commit** `feat(ui): fonte da marca com alternativa de sistema`.

---

### Tarefa 4: Tokens finais, moldura do tema Sidia e tema sem piscada

**Arquivos:** Modificar `theme/tokens.css`, `index.html` (script inline mínimo que aplica `data-theme` antes da primeira pintura, lendo a mesma chave de armazenamento do `ThemeProvider`), `shell/Shell.tsx` (faixa da moldura), `theme/ThemeProvider.tsx` (se a chave mudar); Teste `a11y/tema.test.ts`, `a11y/contraste.test.ts` (continua).

**Interfaces:**
- Azul de marca único `#1329A0` em ação primária (Claro e Sidia) e menu do tema Sidia; nenhum `#0023A4` restante.
- Faixa fina (4 px) com as quatro cores da moldura do logo (`#3E4095`, `#00A85A`, `#F58634`, `#1329A0`) só no topo do tema Sidia, decorativa (`aria-hidden="true"`), nunca como sinal de estado.
- Script de tema: se a chave salva for `claro|escuro|sidia`, aplica; senão segue `prefers-color-scheme`. Com a CSP do Plano 9 (`script-src 'self'`), o script fica em arquivo próprio (`/tema-inicial.js`) carregado no `<head>` sem `defer`, nunca inline; registrar `Ruling:` se o Plano 9 tiver outra solução.
- Se o usuário entregou um manual (T1), conferir cada cor do plano visual contra ele e ajustar só o que divergir.

- [ ] **Passo 1: Testes que falham:** `azulDeMarcaUnico` (`#0023A4` não aparece em `src/`), `molduraSoNoTemaSidia`, `molduraDecorativa` (`aria-hidden`), `temaInicialAntesDoCss` (em `index.html`, o `<script src="/tema-inicial.js">` vem antes do CSS e sem `defer`), `temaInicialRespeitaEscolhaSalva`, `contrasteDosTokensNosTresTemas` e `laranjaEVerdeDaMarcaNuncaComoTexto` (continuam).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/a11y` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira, `pnpm build`. **Passo 5: Captura:** uma rodada (1440 e 390 px, três temas, modo demo) só das telas Central, Meu painel e Tempo em status; no máximo mais uma. **Passo 6: Commit** `feat(ui): tokens finais, moldura do tema Sidia e tema sem piscada`.

---

### Tarefa 5: `DESIGN.md` derivado do que foi construído

**Arquivos:** Criar `DESIGN.md` (raiz); Teste `docs/DesignMdTest.kt` (casos novos).

**Interfaces:** O documento segue o fluxo do Impeccable (plano visual, seção 9) e registra o sistema **como está no código**: princípios (Operate; densa nas telas de trabalho, editorial nas de leitura), tabela de tokens por tema (nome do token, valor em cada tema, uso), tipografia (pilha e escalas), espaçamentos, componentes do kit (`CardPage`, `KpiCard`, `DrillDrawer`, `Filtros`, `Marca`), símbolos de estado (`✓` `●` `▲` `■` com texto), gráficos (paleta categórica, padrão e forma além da cor), regras de acessibilidade e de idioma, e o que não fazer (ranking de pessoas, cor como único sinal, laranja e verde como texto sobre branco).

- [ ] **Passo 1: Testes que falham:** `designMdBateComTokens` (lê `tokens.css`, extrai `--token: valor` de cada `[data-theme=...]` e exige a mesma tabela no `DESIGN.md`, valor a valor; a falha cita token, tema e os dois valores), `designMdCitaOsTresTemas`, `designMdCitaCadaComponenteDoKit` (os arquivos existem em `src/shared` ou `src/shell`), `designMdCitaRegrasDeAcessibilidade` (4,5:1, 3:1, foco de 2 px, `prefers-reduced-motion`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DesignMdTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `docs(design): DESIGN.md do sistema visual construído`.

---

### Tarefa 6: Guia do painel em inglês

**Arquivos:** Criar `docs/manual/panel-guide.md`; Modificar `docs/manual/guia-do-painel.md` (link para a versão em inglês e vice-versa); Teste `docs/GuiaEnDocTest.kt`.

**Interfaces:** Mesma estrutura do guia em pt-BR (Plano 8, T10): uma seção por item do `nav.ts`, com a pergunta que a tela responde, quem vê, de onde vêm os números (ficha, fórmula, aceite), avisos possíveis e como abrir o detalhamento. Os termos são **os mesmos da tela em inglês** (`i18n/en.json`): por exemplo, o rótulo de "no prazo ajustado" no guia é exatamente o valor da chave correspondente.

- [ ] **Passo 1: Testes que falham:** `guiaEnMesmasSecoes` (mesmos ids do `nav.ts`, na mesma ordem, nos dois guias), `guiaEnCitaCadaFicha`, `termosDoGuiaIguaisAosDaTela` (uma lista fixa de chaves de `en.json` — no prazo, no prazo ajustado, indício não prova, sem capacidade, dias úteis, dias corridos — aparece no guia com o texto exato), `guiasSeReferenciam`, `guiaCitaCadaItemDoMenu` (Plano 8, continua).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*GuiaEnDocTest*' --tests '*GuiaDoPainelDocTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `docs(manual): guia do painel em inglês`.

---

### Tarefa 7: Perguntas frequentes em pt-BR e inglês

**Arquivos:** Criar `docs/manual/perguntas-frequentes.md` e `docs/manual/faq.md`; Teste `docs/GuiaEnDocTest.kt` (casos novos).

**Interfaces:** Perguntas mínimas, nesta ordem, nas duas línguas: por que "no prazo" e "no prazo ajustado" são diferentes; o que é "indício, não prova"; o que significa "sem capacidade"; por que dias úteis; por que não vejo a task de outra pessoa (visão por papel); por que um número mudou de ontem para hoje (sincronização a cada 10 min, reconciliação diária); por que não há ranking de pessoas; o que fazer quando um número parece errado (como reportar; o registro de divergências do Plano 14); quem pode alterar responsável, vencimento e labels (spec 7.1).

- [ ] **Passo 1: Testes que falham:** `faqTemAsPerguntasMinimas` (pt e en), `faqMesmaOrdemNasDuasLinguas`, `faqSemNomesNemHosts`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*GuiaEnDocTest*'` → FALHA. **Passo 3: Escrever.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `docs(manual): perguntas frequentes em pt-BR e inglês`.

---

### Tarefa 8: Material de treinamento por papel

**Arquivos:** Criar `docs/treinamento/{usuario.md, squad-leader.md, coordenador.md, gerente.md, roteiro-da-sessao.md}`, `docs/treinamento/img/` (capturas), `docs/treinamento/img/manifesto.yaml`; Teste `docs/TreinamentoDocTest.kt`.

**Interfaces:**
- Um material por papel, só com as telas que o `nav.ts` mostra para aquele papel: objetivo da sessão (30 a 45 min), roteiro de demonstração por tela (pergunta, onde clicar, como ler o número, armadilhas comuns), exercício curto com dados de demonstração e link para o FAQ.
- `gerente.md` é o único que cita Integridade e Registro de acesso, com o aviso "indício, não prova" e a regra de consulta auditada.
- Capturas só em modo demonstração; o `manifesto.yaml` registra, por arquivo, a tela, o tema, o idioma, a data e `demo: true`.
- `roteiro-da-sessao.md`: agenda, pré-requisitos (acesso ao painel, papel em `papeis.yaml`), perguntas para fechar a sessão.

- [ ] **Passo 1: Testes que falham:** `umMaterialPorPapel`, `treinamentoSoComTelasDoPapel` (cada id de tela citado num material está em `nav.ts` para aquele papel), `integridadeSoNoMaterialDoGerente`, `capturasSoDeDemonstracao` (toda imagem de `img/` está no manifesto com `demo: true`), `materialSemNomesNemHosts`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TreinamentoDocTest*'` → FALHA. **Passo 3: Gerar as capturas** (Chrome headless, modo demo, 1440 px, tema Claro, pt-BR) e escrever. **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `docs(treinamento): material por papel com dados de demonstração`.

---

### Tarefa 9: Sessões de treinamento (ponto de parada)

**Arquivos:** Criar `docs/treinamento/registro.md`; Teste `docs/TreinamentoDocTest.kt` (`registroSemNomes`, `registroComDataPapelEContagem`).

- [ ] **Passo 1: Ponto de parada: perguntar ao usuário** sobre o formato: "Uma sessão por papel, ao vivo, gravada para quem faltar (Recomendada)"; "Uma sessão única para todos, com blocos por papel"; "Só material assíncrono (guia, FAQ e material por papel), sem sessão". E sobre **datas e participantes**: o usuário informa as datas e quantas pessoas por papel; nenhum nome entra no repositório.
- [ ] **Passo 2: Registrar** cada sessão: data, papel, número de participantes, dúvidas que viraram pergunta nova no FAQ (T7 é atualizada se houver).
- [ ] **Passo 3: Rodar** `./gradlew test --tests '*TreinamentoDocTest*'` → PASSA. **Passo 4: Commit** `docs(treinamento): registro das sessões`.

---

### Tarefa 10: Dono da manutenção e checklist de entrega

**Arquivos:** Criar `docs/entrega/manutencao.md`, `docs/entrega/checklist-de-entrega.md`; Teste `docs/EntregaDocTest.kt`.

**Interfaces:**
- `manutencao.md`: papel do dono da manutenção (sem nome), o que ele faz por semana (ausências e feriados nos YAML, entrada e saída de pessoas em `papeis.yaml`, conferir alertas da sincronização), por mês (rotação de segredos, revisão das divergências abertas), por ano (calendário de feriados do ano seguinte; renovação do PAT), onde estão os runbooks (Planos 4A, 12 e 13) e como pedir mudança (PR com revisão).
- `checklist-de-entrega.md`: uma linha por item, com **papel responsável** e **evidência** (link para documento, teste ou registro): `main` verde no CI; produção no ar com fumaça `FUMACA_OK`; flag do Jira no estado decidido; aceite do DPO (ou pendência com data); backup e restauração ensaiados; runbooks diário, de incidentes e de reversão; validação e aceite do gestor por card; calibração registrada; guia, FAQ e treinamento (pt e en); identidade visual final (ou pendência registrada); dono da manutenção definido; acessos de administração entregues; nenhuma branch fora da `main`.

- [ ] **Passo 1: Testes que falham:** `checklistDeEntregaCompleto` (cada item acima existe com responsável e evidência), `evidenciasExistem` (todo link relativo aponta para arquivo existente), `manutencaoCitaCadaRunbook`, `registroSemNomes` (nos dois documentos).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EntregaDocTest*'` → FALHA. **Passo 3: Escrever.**
- [ ] **Passo 4: Ponto de parada: perguntar ao usuário** quem é o dono da manutenção (por papel): "O coordenador do TG-Basic, com o gerente como substituto (Recomendada): quem mais usa o painel e conhece os squads"; "Um Squad Leader rotativo por trimestre"; "Uma equipe de sistemas internos da empresa (exige repasse técnico; acrescentar sessão de repasse na T9)". Registrar o papel escolhido e a data.
- [ ] **Passo 5: Rodar** → PASSA. **Passo 6: Commit** `docs(entrega): dono da manutenção e checklist de entrega`.

---

### Tarefa 11: Fechamento do projeto

**Arquivos:** Modificar `CLAUDE.md` (tabela "Onde está cada coisa": `DESIGN.md`, `docs/manual/`, `docs/treinamento/`, `docs/entrega/`; seção "Interface" com o estado da identidade), `PRODUCT.md` (identidade e capacidades), `README.md`, `HANDOFF.md`, `docs/design/plano-visual-frontend.md` (seção 8: pendências resolvidas ou registradas), `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (estado final das fases).

- [ ] **Passo 1: Rodar** `claudeMdSemCaminhoInexistente` (Plano 8) e as suítes completas (backend e frontend), `pnpm build`, `node scripts/orcamento-bundle.mjs`, `sh .claude/hooks/test-guard.sh` → PASSA.
- [ ] **Passo 2: Varredura** de dados pessoais, hosts e segredos em `git diff main..HEAD`; conferir que nenhuma fonte com licença restrita foi versionada.
- [ ] **Passo 3: Commit** `docs: fechamento do projeto e entrega ao time`; PR para a `main`. Não mesclar sem o usuário mandar.

---

## Autoavaliação

**Cobertura:**

| Lacuna ou requisito | Tarefas |
|---|---|
| M9: logo, manual, fonte Mark, azul único, piscada do tema | T1 a T4 |
| B1: `DESIGN.md` do Impeccable | T5 |
| M7 (guia): guia em inglês | T6, T7 |
| M10: treinamento, FAQ, dono da manutenção | T7 a T10 |
| Spec mestra 4.5: moldura de 4 cores como faixa fina no tema Sidia | T4 |
| Spec mestra 10: pt-BR e inglês, três temas, AA | T2 a T7 |

**Consistência de tipos:** `Marca` (T2) entra no `Shell`; `--fonte-texto` (T3) é o único token de família; `DESIGN.md` (T5) é verificado contra o `tokens.css` final (T4); os guias (T6) e o treinamento (T8) leem o mesmo `nav.ts`.

**Pendências deliberadas:** ativos que o usuário não fornecer (registrados no `HANDOFF.md`, com o caminho alternativo ativo).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Azul de marca `#1329A0`** (plano visual 2.1, spec mestra 4.5); reabre só com manual divergente (T4).
2. **Caminho alternativo sem ativos:** nome em texto e pilha de sistema; nada inventado nem baixado de terceiros (T1 a T3).
3. **Logo como arquivo `<img>`, nunca SVG inline** (T2).
4. **Script de tema inicial em arquivo próprio**, compatível com `script-src 'self'` (T4).
5. **Capturas só com dados de demonstração** e manifesto (T8).
6. **Registros sem nomes de pessoas** (T9, T10).

Decisões que **dependem do usuário** (pontos de parada): logo, fonte e licença, manual (T1); formato, datas e participantes do treinamento (T9); dono da manutenção (T10).
