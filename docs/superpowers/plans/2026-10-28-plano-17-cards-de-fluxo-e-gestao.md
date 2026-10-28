# Fase 17: Contrato do cartão e cards de fluxo e gestão — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`. Leia antes: `docs/superpowers/fluxo-de-entrega.md` e o adendo de plataforma (SQLite, sem Docker).

**Objetivo (Goal):** (17a) criar o **contrato único do cartão**: registro no backend, componente `Cartao` e `GavetaDeTasks` compartilhados, teste de contrato (número do cartão = total do drill, textos em pt/en) e a tela **Mapa de cartões**, migrando os cartões atuais; (17b) entregar os cards novos **Fluxo da semana**, **Envelhecimento por etapa**, **Bloqueios entre tasks**, **Controle do ciclo e tendência** e a aba **Cruzamentos**, já no contrato.

**Arquitetura (Architecture):** `CartaoMeta` (identidade, grupo, natureza, chaves de texto, fontes, papéis) e a interface `CartaoRegistrado` (`meta`, `valores(entrada)`, `drill(entrada, segmento)`) vivem em `cards/contrato/`. `CatalogoDeCartoes` reúne os registrados; `GET /api/cartoes/catalogo` devolve só o que o papel vê. O drill genérico `GET /api/cartoes/{id}/tasks` chama `CartaoRegistrado.drill`; os cálculos continuam nos domínios existentes (nada é recalculado em dois lugares). No frontend, `shared/Cartao.tsx` (anatomia da seção 2 de `docs/design/cartoes-gramatica-e-wireframes.md`), `shared/GavetaDeTasks.tsx` (o atual `DrillDrawer` de Tempo em status, generalizado), `useCatalogo()` e a tela `MapaDeCartoes`. **Sem migração, sem dependência npm nova, sem chamada nova ao Jira.**

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1, SQLite embutido (testes com SQLite real em arquivo temporário), React 19 + TypeScript (Vite, Vitest, Testing Library, TanStack Query). Gráficos em SVG próprio.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-28-spec-fase-17-cards-de-fluxo-e-gestao.md`. **Visual:** `docs/design/cartoes-gramatica-e-wireframes.md` e `docs/design/plano-visual-frontend.md`. **Ideias (fichas):** `docs/superpowers/ideias-de-cards-a-partir-da-referencia.md`.

**Quando executar:** **17a (Tarefas 1 a 7) logo depois da Fase 0 e antes do Plano 5a** (cada PR a partir da `main` atualizada). **17b (Tarefas 8 a 13) depois do 14b** (ponto de parada PP5 da spec). Branches `feat/contrato-do-cartao` e `feat/cards-de-fluxo-e-gestao`.

## Estado herdado (confirmar na execução)

| Nome | Origem |
|---|---|
| `TempoEmStatusRoutes.kt`, `TempoEmStatusDto.kt`, rota de drill com `kind` | `cards/` (Plano 3) |
| `TempoEmStatus.tsx` (função local `Kpi`), `DrillDrawer.tsx`, `csv.ts`, `format.ts`, `api.ts` | `app/frontend/src/screens/TempoEmStatus/` (Plano 3) |
| `Sincronizacao.tsx` | `app/frontend/src/screens/Sincronizacao/` (Plano 4A) |
| `nav.ts`, `Shell`, `Menu`, `RoleContext` | frontend (Plano 1) |
| `i18n/pt.json`, `i18n/en.json`, `LangProvider` | frontend (Plano 1) |
| `AccessLog`, `executarAuditado`, `Visibility`, `Papel` | backend (Planos 1 e 4) |
| **Só para 17b:** `IssueSnapshot`, `WorkStore`, `FiltrosDeCard`, `CardDeLeitura`, `rotaDeCard`, `SerieSemanal` (Previsão), `issue_link`, `status_map`, `TimeInStatusCalculator` | Planos 3, 4A, 5a e 5b |

Se algum nome mudou, atualizar só os nomes deste plano e registrar `Ruling:` no arquivo de progresso `docs/superpowers/progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md` (criar na Tarefa 1).

## Restrições globais (Global Constraints)

- **Plataforma:** SQLite embutido, sem Docker nem servidor de banco; comandos em PowerShell no Windows 11 (`cd app\backend; .\gradlew.bat test`, `cd app\frontend; pnpm test`) ou `sh` equivalente. Nunca `bash`/`wsl` no PC da empresa.
- **Jira:** a única trava é a flag em `app/.env`, que só o usuário muda. Este plano **não acessa o Jira**; tudo roda com fixtures e dados de demonstração. A ação "Abrir no Jira" só aparece com a flag ligada e a instância configurada.
- **Sem ranking de pessoas;** listas em ordem alfabética; pessoa fora da visão = "outra pessoa". Sem dado pessoal novo. Fuso America/Manaus; dias úteis por padrão.
- **Cor nunca é o único sinal;** pt-BR e inglês completos; três temas; WCAG AA.
- **"Card = tamanho do detalhe"** vale para todo cartão registrado e é verificado por teste.
- **Sem migração.** Se a execução achar que precisa de uma, parar e perguntar (viola a premissa da spec).
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(cartoes): ...`, `test(cartoes): ...`); **um commit por tarefa**; nenhum dado real, segredo nem informação interna em fixtures (usar `ana.silva`, `TG-107` e similares fictícios).

## Foco da revisão (Review Focus)

1. **Registro incompleto:** um cartão existe na tela mas não no registro (some do Mapa e foge do teste de contrato) → `todaRotaDeCardEstaNoRegistro` (T1).
2. **Número diferente do drill** por filtro aplicado de um lado só → `numeroDoCartaoIgualAoTotalDoDrill` (T2).
3. **Vazamento pelo catálogo ou drill:** USUARIO vendo cartão de outro papel, ou drill com pessoa fora da visão → `catalogoSoComOQueOPapelVe`, `drillForaDaVisaoEmOutraPessoa` (T1, T2).
4. **Botão dentro de botão / ⓘ inalcançável pelo teclado** → `cartaoTemUmaAcaoPrincipalEIconeDeAjudaAlcancavel` (T3).
5. **Texto sem tradução** (título, "o que mostra", "como calcula") → `todoCartaoTemTextoPtEEn` (T1).
6. **Retrofit quebrando Tempo em status** → todos os testes atuais da tela passam sem alterar asserções de comportamento (T5).
7. **Cards novos (17b):** semana errada na borda de domingo, reaberta contada como entrada, bloqueadora de outro squad com chave/nome visível, etapa sem amostra mostrando faixa → testes nomeados em cada tarefa.

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/cards/contrato/
  CartaoMeta.kt, Natureza.kt, GrupoDeCartao.kt, CartaoRegistrado.kt
  CatalogoDeCartoes.kt, CatalogoRoutes.kt, CatalogoDto.kt, DrillGenericoRoutes.kt
app/backend/src/test/kotlin/br/sidia/painel/cards/contrato/
  CatalogoDeCartoesTest.kt, ContratoDeCartoesTest.kt
app/frontend/src/shared/{Cartao.tsx, cartao.css, AjudaDoCartao.tsx, GavetaDeTasks.tsx, useCatalogo.ts}
app/frontend/src/screens/MapaDeCartoes/{MapaDeCartoes.tsx, MapaDeCartoes.test.tsx}
app/frontend/src/i18n/{pt.json,en.json}      (chaves cartao.*, catalogo.*, e uma por cartão)
docs/superpowers/progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md
17b: cards/fluxo/{FluxoDaSemana*,AgingWip*,Bloqueios*,ControleDoCiclo*,Cruzamentos*}.kt
     screens/{FluxoDaSemana,AgingWip,Bloqueios,Cruzamentos}/ e seção em Fechadas
```

---

# 17a — Contrato do cartão (antes do Plano 5a)

## Tarefa 1: Registro de cartões e catálogo por papel

**Arquivos:** `cards/contrato/*`, `CatalogoDeCartoesTest.kt`, rota no `module`, `i18n`.

- [ ] **Passo 1 (arquivo de progresso).** Criar `docs/superpowers/progresso/2026-10-28-plano-17-cards-de-fluxo-e-gestao.md` com o checklist das tarefas. Confirmar a tabela "Estado herdado".
- [ ] **Passo 2 (testes primeiro).** Em `CatalogoDeCartoesTest`: `idsSaoUnicos`, `todoCartaoTemTextoPtEEn` (lê `pt.json` e `en.json` por chave `meta.tituloChave`, `oQueMostraChave`, `comoCalculaChave`), `todaNaturezaETodoGrupoValidos`, `catalogoSoComOQueOPapelVe` (USUARIO não vê cartão de Auditoria; GERENTE vê todos), `catalogoSemTextoLivreDoJira`, `rotaDoCatalogoGravaAccessLog` (200 e 403).
- [ ] **Passo 3 (implementar).** `enum Natureza { ESTOQUE, FLUXO, PRAZO, PESSOAS, QUALIDADE }`; `enum GrupoDeCartao` (os 7 grupos do menu); `data class CartaoMeta(id, grupo, natureza, tela, tituloChave, oQueMostraChave, comoCalculaChave, fontes: List<FonteDoDado>, papeis: Set<Papel>, temDrill: Boolean, exigeChangelog: Boolean, exigeWorklog: Boolean)`; `interface CartaoRegistrado { val meta: CartaoMeta; suspend fun valores(e: EntradaDeCartao): Map<String, Int>; suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> }`; `CatalogoDeCartoes(cartoes)` com `visiveisPara(papel)`; `GET /api/cartoes/catalogo` por `executarAuditado`.
- [ ] **Passo 4.** Registrar os cartões **TS-01 a TS-07** (Tempo em status) no catálogo, apontando para os cálculos atuais (sem copiar lógica). Rodar `.\gradlew.bat test --rerun-tasks`.
- [ ] **Passo 5 (commit).** `feat(cartoes): registro de cartões e catálogo por papel`.

## Tarefa 2: Drill genérico e teste de contrato

**Arquivos:** `DrillGenericoRoutes.kt`, `ContratoDeCartoesTest.kt`, adaptação de `TempoEmStatusRoutes.kt`.

- [ ] **Passo 1 (testes primeiro).** `ContratoDeCartoesTest` percorre **todo** `CartaoRegistrado` do catálogo com a fixture padrão e, para cada par `(segmento, valor)` de `valores()`, exige `drill(segmento).size == valor`: `numeroDoCartaoIgualAoTotalDoDrill`. Mais: `drillComFiltroTemOMesmoTamanhoDoCartao`, `drillForaDaVisaoEmOutraPessoa` (a linha não traz nome nem chave de quem está fora da visão), `drillOrdenaPessoasAlfabeticamente`, `segmentoInexistenteDevolve400ComCodigo`, `rotaGravaAccessLogInclusiveNos400`.
- [ ] **Passo 2 (implementar).** `GET /api/cartoes/{id}/tasks?segmento=...&<filtros>`; resposta `{ titulo, definicaoChave, total, colunas, linhas, linkJira }` (`linkJira` sempre `null` neste plano; o Plano 10 preenche). A rota de drill de Tempo em status passa a delegar ao genérico, **mantendo a rota antiga** até o frontend migrar (remover na Tarefa 5).
- [ ] **Passo 3.** `todaRotaDeCardEstaNoRegistro`: teste que lista as rotas `/api/cards/**` e falha se alguma não corresponde a um cartão registrado (o teste fica como porta de entrada do Plano 5a).
- [ ] **Passo 4 (commit).** `feat(cartoes): drill genérico e teste de contrato`.

## Tarefa 3: Componente `Cartao` e ajuda (ⓘ)

**Arquivos:** `shared/Cartao.tsx`, `AjudaDoCartao.tsx`, `cartao.css`, `useCatalogo.ts`, testes, `i18n`.

- [ ] **Passo 1 (testes primeiro).** `Cartao.test.tsx`: `mostraTituloNaturezaValorEstadoEProcedencia`, `estadoTemSimboloETexto`, `cartaoTemUmaAcaoPrincipalEIconeDeAjudaAlcancavel` (Tab na ordem ⓘ → ação; não há `button` dentro de `button`), `ajudaAbreComEnterEFechaComEsc` (foco volta ao ⓘ), `ajudaMostraOQueMostraEComoCalcula`, `semDrillNaoMostraVerTasks`, `estadosCarregandoVazioErroSemPermissaoIncompleto`, `valorVindoDaApiEhTexto` (nenhum `dangerouslySetInnerHTML`), `semSaltoDeLayoutNoEsqueleto` (altura mínima fixa).
- [ ] **Passo 2 (implementar).** `Cartao` com as variantes `kpi | serie | tabela | lista` e a área clicável por `::after`; `AjudaDoCartao` como balão não modal com `aria-expanded`; `useCatalogo()` (TanStack Query, `staleTime` longo) que entrega `meta` por `id`; tokens do `theme/tokens.css`, sem cor nova.
- [ ] **Passo 3.** Chaves `cartao.*` em pt e en. Rodar `pnpm test` e `pnpm build`.
- [ ] **Passo 4 (commit).** `feat(ui): componente Cartao com ajuda, estados e área clicável`.

## Tarefa 4: Gaveta de tasks compartilhada e ação "Abrir no Jira"

**Arquivos:** `shared/GavetaDeTasks.tsx`, `shared/csv.ts` (movido), testes.

- [ ] **Passo 1 (testes primeiro).** Mover os testes de `DrillDrawer` de Tempo em status para `GavetaDeTasks.test.tsx` **sem enfraquecê-los**: foco no Fechar, Tab preso, Esc fecha e devolve o foco, resto da página inerte, CSV com neutralização de fórmula e linha de filtros. Novos: `mostraDefinicaoDoNumeroEFiltrosAtivos`, `colunaDetalheExplicaPorQueEntrou`, `pessoasEmOrdemAlfabetica`, `semLinkJiraNaoMostraAbrirNoJira` e `comLinkJiraAbreEmNovaAbaComNoopener` (o link vem pronto da API; o front só o usa), `acimaDe100ChavesAvisaEOferecePeloCsv`.
- [ ] **Passo 2 (implementar).** `GavetaDeTasks({ cartaoId, segmento, filtros, aoFechar })` consome `GET /api/cartoes/{id}/tasks`. O front **não monta URL do Jira**; usa `linkJira` da resposta. O botão não existe se `linkJira == null`.
- [ ] **Passo 3 (backend).** O link por chaves (I-18) é montado no backend a partir da `baseUrl` configurada, só com a flag ligada e a instância configurada, no máximo 100 chaves, https sem credenciais; `access_log` guarda rota e quantidade, nunca as chaves. Testes: `semLinkComFlagDesligada`, `linkSemCredenciais`, `limiteDe100Chaves`, `accessLogSemChaves`. (O Plano 10 reaproveita este montador para a gaveta da task; se o Plano 10 já tiver sido executado, reutilizar `LinkJira`.)
- [ ] **Passo 4 (commit).** `feat(ui): gaveta de tasks compartilhada e abrir no Jira`.

## Tarefa 5: Migrar Tempo em status e Sincronização para o contrato

**Arquivos:** `TempoEmStatus.tsx`, `Sincronizacao.tsx`, `tempo.css`, testes existentes.

- [ ] **Passo 1.** Substituir a função local `Kpi` e o `DrillDrawer` local por `Cartao` e `GavetaDeTasks`. Os KPIs de Sincronização que não têm tasks por trás usam `Cartao` sem drill.
- [ ] **Passo 2 (verificação).** Os testes atuais de `TempoEmStatus.test.tsx` e `Sincronizacao.test.tsx` passam **sem alterar as asserções de comportamento** (só seletores de markup, se mudarem). Registrar no progresso qualquer asserção alterada e por quê.
- [ ] **Passo 3.** Remover a rota de drill antiga de Tempo em status e o `DrillDrawer` local.
- [ ] **Passo 4 (commit).** `refactor(ui): Tempo em status e Sincronização usam o contrato do cartão`.

## Tarefa 6: Tela Mapa de cartões

**Arquivos:** `screens/MapaDeCartoes/*`, `nav.ts`, `i18n`.

- [ ] **Passo 1 (testes primeiro).** `agrupaPorGrupoEFiltraPorNatureza`, `buscaPorTitulo`, `mostraOQueMostraComoCalculaEFonte`, `soOQueOPapelVe`, `exportaCsvDoMapa`, `itemDoMenuSoParaGerenteECoordenador`, `semTextoSemTraducao`.
- [ ] **Passo 2 (implementar).** Tela em Governança conforme a seção 5.6 do documento visual; sem chamada além de `/api/cartoes/catalogo`. Item no `nav.ts` (testes de `nav.test.ts` atualizados).
- [ ] **Passo 3 (commit).** `feat(ui): tela Mapa de cartões`.

## Tarefa 7: Revisão visual, acessibilidade e fechamento do 17a

- [ ] **Passo 1.** Conferir os critérios da seção 7 de `docs/design/cartoes-gramatica-e-wireframes.md` nos três temas (claro, escuro, Sidia) e em 360, 768 e 1280 px: contraste calculado dos tokens, foco visível, teclado, leitor de tela (rótulos), `prefers-reduced-motion`. Registrar capturas e valores no progresso (sem dados reais; usar a demonstração).
- [ ] **Passo 2.** Regra para os próximos planos: atualizar `docs/superpowers/plans/2026-10-24-indice-dos-planos.md` (linha "17a executado") e confirmar que a seção 6 da spec está refletida no `HANDOFF.md`.
- [ ] **Passo 3 (revisão e entrega).** Revisão final independente (sem Critical/Important), suíte sem cache (`.\gradlew.bat test --rerun-tasks` e `pnpm test`), varredura limpa, commit, push, PR, CI verde e merge pelas 7 condições do `docs/superpowers/fluxo-de-entrega.md`; voltar à `main` e `git pull`.

---

# 17b — Cards novos (depois do 14b; ponto de parada PP5)

Cada tarefa abaixo: (a) registrar o cartão no catálogo com textos pt/en, (b) cálculo no domínio, (c) `valores()` e `drill()`, (d) tela ou seção com `Cartao`, (e) teste de contrato verde, (f) fixture com as bordas. Nenhuma tarefa acessa o Jira.

## Tarefa 8: Fluxo da semana (I-01)

- [ ] **Testes primeiro:** `semanaDeSegundaADomingoEmManaus` (evento às 23:59 de domingo cai na semana certa), `reabertaNaoContaComoNovaEntrada`, `issueRemovidaSaiDaSerie`, `canceladaVaiParaSerieAparte`, `iniciadaUsaPrimeiraEntradaEmStatusAtivo`, `sinalBacklogCrescendoAposTresSemanas`, `usuarioVeSoAsProprias` com `AMOSTRA_PEQUENA`, `semSerieLadoALadoPorPessoa`, `numeroDaBarraIgualAoDrill`.
- [ ] **Implementar:** `FluxoDaSemanaCard` usando a função única `SerieSemanal` (a mesma da Previsão; se ainda não houver, extraí-la do Plano 5b sem mudar o resultado do Monte Carlo, com teste de regressão); tela e gráfico de barras agrupadas por padrão de preenchimento (wireframe 5.1), tabela equivalente.
- [ ] **Commit:** `feat(cards): fluxo da semana (entrada, início e saída)`.

## Tarefa 9: Envelhecimento por etapa (I-05)

- [ ] **Testes primeiro:** `idadeNaEtapaContaDesdeAUltimaEntrada`, `reentradaReiniciaAIdade`, `etapaSemAmostraMinimaSemFaixa`, `acimaDoP85TemSinalETexto`, `pontoNaoTrazNomeDePessoa`, `esperaEBloqueioSaoColunasProprias`, `ordemDasEtapasVemDoStatusMap`, `numeroAcimaDoNormalIgualAoDrill`.
- [ ] **Implementar:** `AgingWipCard` reaproveitando `TimeInStatusCalculator` (P50/P85 por status); gráfico de pontos por etapa (wireframe 5.2).
- [ ] **Commit:** `feat(cards): envelhecimento por etapa (Aging WIP)`.

## Tarefa 10: Bloqueios entre tasks (I-08)

- [ ] **Testes primeiro:** `excluiOsParesTaskReview`, `soVinculosNaoRemovidos`, `bloqueadoraConcluidaNaoBloqueia`, `prazoEmConflitoQuandoBloqueadoraVenceDepois`, `bloqueadoraForaDaVisaoApareceComoOutroSquadSemChaveNemNome`, `pontaForaDaBaseViraVerificarNoJira`, `semRankingDeBloqueadoras`, `bloqueadaSemCausaRegistradaNaQualidadeDosDados`, `tipoEDirecaoVemDaMatriz`, `numeroIgualAoDrill`.
- [ ] **Implementar:** `BloqueiosCard` sobre `issue_link` cruzada com `issue`; a verificação `BLOQUEADA_SEM_CAUSA` entra como aviso informativo na Qualidade dos dados (se o Plano 7b ainda não existir, deixar o ponto de extensão e registrar `Ruling`). Wireframe 5.3.
- [ ] **Commit:** `feat(cards): bloqueios entre tasks`.

## Tarefa 11: Controle do ciclo e tendência (I-02 e I-03)

- [ ] **Testes primeiro:** `mediaMovelUsaVintePorCentoImparMinimoCinco`, `cicloPorTipoSemMisturarChecklistEReview`, `outliersAcimaDe30DiasExcluiveisPorYaml`, `mesComPoucosItensMostraAviso`, `dataDeConclusaoNoDiaDeManaus`, `pontoNaoTrazPessoa`, `numeroDoMesIgualAoDrill`.
- [ ] **Implementar:** seção nova na tela Fechadas (wireframe 5.4), reutilizando o cálculo de ciclo de FE-08.
- [ ] **Commit:** `feat(cards): controle do ciclo e tendência do tempo de resolução`.

## Tarefa 12: Aba Cruzamentos (I-10)

- [ ] **Testes primeiro:** `soAceitaEixosDaListaFechada`, `pessoaSoComoLinhaEmOrdemAlfabetica`, `semTotalPorPessoaEmDestaque`, `usuarioVeSoAPropriaLinha`, `estadoDosEixosNaUrl`, `celulaIgualAoDrill`, `medidaIdadeP50ComAmostraMinima`, `eixoDeCampoDoDicionarioDependeDoPlano10` (se o Plano 10 não foi executado, os eixos de campo ficam ocultos).
- [ ] **Implementar:** consulta em memória sobre o `IssueSnapshot` (mesmos filtros dos demais cards); aba em Resultados (wireframe 5.5).
- [ ] **Commit:** `feat(cards): aba Cruzamentos`.

## Tarefa 13: Fechamento do 17b

- [ ] **Passo 1.** Rodar o teste de contrato completo (agora com os cartões novos) e conferir o Mapa de cartões: todos os cartões novos aparecem com textos pt/en.
- [ ] **Passo 2.** Revisão visual pelos critérios da seção 7 do documento visual, nos três temas.
- [ ] **Passo 3.** Aceite do gestor card a card (o mesmo aceite que o Plano 14b prevê para os cards existentes), registrado no progresso; divergências contra as fichas do documento de ideias anotadas em `docs/superpowers/ideias-de-cards-a-partir-da-referencia.md` (seção "Executado").
- [ ] **Passo 4 (revisão e entrega).** Revisão final independente, suíte sem cache, varredura, push, PR, CI verde e merge pelas 7 condições do fluxo de entrega; voltar à `main` e `git pull`.

## Autorrevisão deste plano (feita ao escrever)

- Cobertura da spec: seção 3 (cinco itens) → T8 a T12; seção 4 (contrato) → T1 a T3; ação I-18 → T4; Mapa → T6; seção 6 (mudanças em outros planos) vive na spec, com ponto de aplicação em T2 (passo 3) e T7 (passo 2).
- Risco assumido: o 17a muda `TempoEmStatus` já testado. Mitigação: T5 exige os testes atuais intactos.
- Risco aberto: os nomes do Plano 5 (`CardDeLeitura`, `rotaDeCard`) ainda não existem; o 17a define a interface `CartaoRegistrado` antes e o 5a deve se adaptar a ela (regra da spec, seção 6). Se o 5a for escrito de outro jeito na execução, registrar `Ruling` e ajustar o adaptador, não o contrato.
