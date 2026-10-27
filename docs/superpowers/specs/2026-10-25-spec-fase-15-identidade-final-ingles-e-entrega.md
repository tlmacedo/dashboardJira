# Spec da Fase 15: Identidade visual final, inglês e entrega ao time

Data: 25/10/2026
Plano: [`docs/superpowers/plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`](../plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md) (11 tarefas, um PR)
Spec mestra: [`2026-10-24-painel-squads-kotlin-react-design.md`](2026-10-24-painel-squads-kotlin-react-design.md) (seções 2, 4.5, 9 "Fonte tipográfica", 10, 11)
Visual: [`docs/design/plano-visual-frontend.md`](../../design/plano-visual-frontend.md) (seções 2, 2.1, 3, 8, 9)
Depende de: Fases 8, 9 e 14 (aceite do gestor) mescladas na `main`; ativos de marca fornecidos pelo usuário (opcionais: há caminho alternativo)
Lacunas de origem: [`cobertura-do-projeto.md`](../cobertura-do-projeto.md), M7 (guia), M9, M10 e B1

> **Precedência:** `CLAUDE.md` > `docs/guia-execucao-outra-ia.md` > esta spec > o plano.

## 1. Intenção e porquê

Ao fim da Fase 14 os números estão validados, mas o painel ainda usa o nome em texto no lugar do logo, a pilha de fontes do sistema, dois azuis de marca (`#1329A0` na spec e `#0023A4` nos tokens), e o tema pisca ao abrir. O guia existe só em pt-BR, não há perguntas frequentes nem treinamento, e ninguém foi nomeado para manter os YAML de ausências, feriados e papéis. Sem isso, o time não sabe ler "no prazo × ajustado", "indício, não prova" ou "sem capacidade", e o painel depende de quem o escreveu.

Esta fase fecha a identidade visual com os ativos oficiais, quando o usuário os fornecer, e com um caminho alternativo honesto quando não. Ela registra o sistema visual em `DESIGN.md`, entrega o guia e as perguntas frequentes nas duas línguas, treina cada papel, define o dono da manutenção e termina com um checklist de entrega com evidências.

## 2. Objetivos, não objetivos e antiobjetivos

### Objetivos

- Logo saneado no menu e no favicon; fonte Mark com `font-display: swap` e alternativa de sistema.
- Azul de marca único `#1329A0`; moldura de quatro cores como faixa decorativa no tema Sidia; tema aplicado antes da primeira pintura.
- `DESIGN.md` verificado contra `tokens.css`, valor a valor.
- Guia do painel e perguntas frequentes em pt-BR e inglês, com os mesmos termos das telas.
- Material de treinamento por papel, capturas só de demonstração, sessões registradas sem nomes.
- Dono da manutenção (por papel) e checklist de entrega com responsável e evidência por item.

### Não objetivos

- Cards novos ou mudança de regra de cálculo.
- Redesenho das telas (só tokens, marca e tipografia).
- Mensagens do backend por código em inglês (Fase 10).
- Manual de marca próprio do painel.

### Antiobjetivos (falha mesmo que "funcione")

- Logo ou fonte inventados, baixados de terceiros ou servidos de fora do domínio.
- SVG com script, `on*`, `<foreignObject>` ou referência externa.
- Fonte versionada contra a licença, ou em repositório que não seja privado.
- Laranja ou verde da marca como texto sobre fundo claro; cor como único sinal.
- `DESIGN.md` que diverge do código.
- Guia em inglês com seção faltando ou termo diferente da tela.
- Captura com dados reais; Integridade em material de outro papel; nome de pessoa em registro.

## 3. Restrições

### 3.1 Regras transversais do projeto (valem sempre)

**Acesso ao Jira.** A única trava é `JIRA_LIVE_ENABLED` (regra 1 do `CLAUDE.md`); só o usuário muda. Esta fase não acessa o Jira; capturas com `DASHBOARD_DEMO_DATA=true`.

**Visibilidade por papel.** O material de cada papel mostra só as telas que o `nav.ts` dá a ele; Integridade só no material do gerente.

**Sem ranking de pessoas.** O `DESIGN.md` e o FAQ explicam por quê.

**Interface.** Temas Claro, Escuro e Sidia; pt-BR e inglês; WCAG AA (texto ≥ 4,5:1; gráficos e bordas ≥ 3:1); foco de 2 px; `prefers-reduced-motion`; `✓` `●` `▲` sempre com texto.

**Dados pessoais.** Registros de treinamento e de entrega só com papéis, contagens e datas.

**Ambiente.** Sem Docker (testes do backend só com JDK 21); comentários e commits em pt-BR.

### 3.2 Restrições específicas desta fase

- CSP do Plano 9 (`font-src 'self'`, `script-src 'self'`, `img-src 'self' data:`) não pode ser afrouxada.
- Bundle principal continua < 300 KB gzip (fontes fora do bundle JS).
- Sem dependência npm nova.

## 4. Decisões já tomadas

| # | Decisão | Origem |
|---|---|---|
| U1 | Identidade segue a Sidia; ativos oficiais ainda não fornecidos; não inventar logo | [usuário] plano visual 1 e 2 |
| U2 | Fonte: sistema até a Sidia fornecer a fonte e o logo limpo | [usuário/design] spec mestra 9 |
| U3 | Tema Sidia: menu e cabeçalho azul `#1329A0`; moldura de 4 cores como faixa fina | [design aceito] spec mestra 4.5; plano visual 2.1 |
| U4 | pt-BR e inglês completos | [usuário] spec mestra 2 e 10 |
| D1 | Azul de marca único `#1329A0`; reabrir só com manual divergente | [decorre de U3] |
| D2 | Caminho alternativo sem ativos: nome em texto, pilha de sistema | [decorre de U1 e U2] |
| D3 | Logo como `<img>`, nunca SVG inline | [valor padrão 25/10, reversível] |
| D4 | Script de tema inicial em arquivo próprio (compatível com a CSP) | [valor padrão 25/10, reversível] |
| D5 | Capturas só de demonstração, com manifesto | [valor padrão 25/10, reversível] |
| D6 | Registros sem nomes de pessoas | [valor padrão 25/10, reversível] |

## 5. O que fica para quem implementa

- Formato exato das tabelas do `DESIGN.md`, desde que o teste consiga comparar token, tema e valor.
- Redação do guia, do FAQ e do treinamento, desde que passem nos testes e usem os termos de `en.json`/`pt.json`.
- Quais variações da fonte usar (no máximo três), conforme os arquivos recebidos.
- Escolhas em aberto: `Ruling:` em `docs/superpowers/progresso/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`.

## 6. Critérios de aceitação verificáveis

1. **Ativos (T1).** `docs/design/ativos-de-marca.md` registra as três respostas com data (`ativosDeMarcaRegistrados`).
2. **Logo (T2).** `pnpm vitest run src/shell src/a11y` passa, incluindo `logoSemScriptNemReferenciaExterna` e `marcaSemLogoMostraTexto`.
3. **Fonte (T3).** `fonteServidaDoProprioDominio`, `pilhaDeFallbackMantida` e `cspContinuaComFontSelf` passam; `orcamento-bundle.mjs` passa.
4. **Tokens (T4).** `azulDeMarcaUnico`, `molduraSoNoTemaSidia`, `temaInicialAntesDoCss` e `contrasteDosTokensNosTresTemas` passam.
5. **`DESIGN.md` (T5).** `designMdBateComTokens` passa.
6. **Inglês (T6, T7).** `guiaEnMesmasSecoes`, `termosDoGuiaIguaisAosDaTela`, `faqTemAsPerguntasMinimas` e `faqMesmaOrdemNasDuasLinguas` passam.
7. **Treinamento (T8, T9).** `treinamentoSoComTelasDoPapel`, `integridadeSoNoMaterialDoGerente`, `capturasSoDeDemonstracao` e `registroSemNomes` passam; o registro tem data, papel e contagem de cada sessão.
8. **Entrega (T10).** `checklistDeEntregaCompleto` e `evidenciasExistem` passam; o papel do dono da manutenção está registrado com data.
9. **Fim (T11).** Suítes completas, `pnpm build`, `test-guard.sh` e `claudeMdSemCaminhoInexistente` verdes; nenhuma fonte de licença restrita versionada; PR sem dado pessoal; nada mesclado sem o usuário mandar.

## 7. Riscos, pendências e pontos de parada

**Pontos de parada (perguntar ao usuário, com opções; a recomendada primeiro):**
- **Logo** (T1): SVG limpo (Recomendada); PNG transparente; ainda não há.
- **Fonte Mark e licença** (T1): arquivos com licença que permite repositório privado (Recomendada); arquivos sem permissão de repositório (ficam fora do git e entram no build local); não usar a Mark.
- **Manual de marca** (T1): não há, manter `#1329A0` (Recomendada); há manual, conferir as cores.
- **Treinamento** (T9): uma sessão por papel, gravada (Recomendada); sessão única com blocos; só material assíncrono. Datas e contagem de participantes vêm do usuário.
- **Dono da manutenção** (T10): coordenador do TG-Basic com o gerente como substituto (Recomendada); Squad Leader rotativo; equipe de sistemas internos.

**Riscos:**
- Logo recebido com elementos inseguros ou de baixa resolução. Mitigação: saneamento e teste; sem arquivo bom, caminho alternativo.
- Licença da fonte incompatível com o repositório. Mitigação: opção de ficar fora do git.
- Termos traduzidos divergindo da tela. Mitigação: `termosDoGuiaIguaisAosDaTela`.
- Visibilidade do repositório ainda em discussão (cobertura A8). Mitigação: nenhum nome nos registros; fonte licenciada só com repositório privado.

## 8. Glossário do que esta fase consome

| Nome | Arquivo | Origem |
|---|---|---|
| `tokens.css`, `ThemeProvider`, `Shell`, `nav.ts` | `app/frontend/src/` | [main] Plano 1; [Plano 8] T4, T5 |
| `contraste.ts`, `contrasteDosTokensNosTresTemas`, `paridadeDeChavesPtEn` | `app/frontend/src/a11y/` | [Plano 8] T4; [Plano 5] T4 |
| `orcamento-bundle.mjs` | `app/frontend/scripts/` | [Plano 8] T6 |
| `guia-do-painel.md`, `GuiaDoPainelDocTest` | `docs/manual/`, `src/test/.../docs/` | [Plano 8] T10 |
| `CabecalhosDeSeguranca`, `CabecalhosDeSegurancaTest` | `app/backend/.../http/` | [Plano 9] T3 |
| `DASHBOARD_DEMO_DATA` | `config/Settings.kt` | [Plano 4A] T10 |
| `aceite-do-gestor.md` | `docs/validacao/` | [Plano 14] T13 |
| Runbooks de operação, incidentes e rotação | `docs/` | [Plano 12] (confirmar caminhos) |

## 9. Referência ao plano

Execute [`2026-10-25-plano-15-identidade-final-ingles-e-entrega.md`](../plans/2026-10-25-plano-15-identidade-final-ingles-e-entrega.md) tarefa a tarefa, com TDD, conforme `docs/guia-execucao-outra-ia.md` (seções 4 a 8). Sem os ativos de marca, as Tarefas 2 e 3 seguem pelo caminho alternativo e as demais rodam normalmente.
