# Fase 10: Complementos de telas e filtros — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Completar o que todas as telas compartilham e que os Planos 5 a 7 deixaram parcial: filtros e agrupamentos pelos campos do dicionário (Checklist Type, SKU Type, Model Name, Model Category, TG Request Type, Carrier Code), opções de filtro vindas do backend, gaveta universal da task em todas as telas (com link para o Jira montado da configuração), detalhe em nova aba, paginação virtual de tabelas grandes, estados de tela padronizados, mensagens do backend por código com i18n pt/en, componente de gráfico com paleta categórica acessível e os ajustes que a auditoria apontou nos Planos 5 e 7.

**Arquitetura (Architecture):** Os campos do dicionário passam a ser extraídos na gravação para uma coluna `issue.campos_dicionario` (`TEXT` com JSON: nome do campo → valor; filtros por `json_extract`/`json_each`), com preenchimento retroativo a partir de `issue.raw` (só banco local) e chegam ao `IssueSnapshot` como `camposDicionario`. `FiltrosDeCard` ganha filtros por campo, aplicados em memória pelo `CardDeLeitura` antes de qualquer cálculo, o que mantém "card = tamanho do detalhe". Um endpoint de opções (`/api/filtros/opcoes`) devolve só o que está no escopo visível do principal. A gaveta universal é uma rota única (`/api/tarefa/{key}`) que reaproveita os domínios existentes (tempo por status, posse, fatores de risco, histórico da auditoria). No frontend, o kit comum (`CardPage`, `DrillDrawer`) ganha `EstadoDaTela`, `TabelaVirtual`, `TarefaDrawer` e `Grafico*`, de modo que cada tela herda tudo sem código próprio.

**Stack (Tech Stack):** Kotlin 2.2.20 + Ktor 3.3.1, kaml, SQLite embutido + Flyway (testes com SQLite real em arquivo temporário); React 19 + TypeScript (Vite 8, Vitest 5, Testing Library, TanStack Query 5). **Nenhuma dependência npm nova**: gráficos em SVG próprio e virtualização própria.

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-10-complementos-de-telas-e-filtros.md`. **Spec mestra:** seções 5.1, 6.1 ("dicionário como filtros e agrupamentos"), 10. **Plano visual:** `docs/design/plano-visual-frontend.md` seções 3 (paleta), 5.5 a 5.8 (tabela, detalhamento, gaveta, estados), 6.4 (gaveta com link para o Jira) e 7 (paginação virtual). **Auditoria:** `docs/superpowers/cobertura-do-projeto.md` (A4, A5, M1, M2, M3, M4, M5, M7 código, B4; inconsistências E5, E12, E13).

**Quando executar:** depois do Plano 7 mesclado e **antes** dos Planos 11 e 8 (o 8 faz a revisão visual e AA final sobre estas telas).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, conferir na `main` que os Planos 4A, 9, 4, 5, 6 e 7 foram mesclados e que estes nomes existem. Se algum nome mudou, atualizar só os nomes deste plano e registrar `Ruling:` no progresso.

| Nome | Origem |
|---|---|
| `IssueSnapshot`, `WorkStore`, `SqliteWorkStore`, `ConsultaDeTrabalho`, `Situacao` | `trabalho/` (Plano 5, T2) |
| `FiltrosDeCard`, `FiltroInvalidoException`, `EntradaDeCard`, `CardDeLeitura<D>`, `rotaDeCard`, `Escopo` | `cards/` (Plano 5, T4) |
| `SqliteIssueStore.upsert(issue, events, instance, osVersionCustomId)`, `OsVersionExtrator`, `SanitizadorRaw` | `sync/` (Planos 3 e 5) |
| `FieldDictionary`, `jira_campos.exemplo.yaml`, ficha `sincronizacao.card.yaml` (`campos: [OS Version, SKU Type, Checklist Type]`) | Planos 2 e 4A |
| `DadosDeDemonstracao`, `ManifestoDemo`, `InstanciaDemo(arquivo, prefixo, osVersionCustomId)` | `demo/` (Plano 4A, T10) |
| `TimeInStatusCalculator`, `PosseCalculator.posse`, `RiscoCalculator.avaliar`, `FatorDeRisco` | Planos 3, 6 (T11) e 5 (T11) |
| `AuditStore`, `HistoricoDto`, `LinhaHistoricoDto` (`GET /api/auditoria/historico/{key}`) | Plano 4 (T6, T7) |
| `FechadasDto`, `NegativasDto`, `CentralDto`, `ItemCentral` | Planos 5 (T6, T10) e 7 (T15) |
| `AvisoDto(codigo, gravidade, mensagem, params)` | Plano 4A, T8 |
| `ErroDto(codigo, params)`, `enviarJson`, `ErroDaApi`, `EVENTO_SESSAO_EXPIRADA` | Plano 9 (T4, T5) |
| `CardPage`, `KpiCard`, `useCard`, `Filtros.tsx`, `shared/DrillDrawer.tsx`, `shared/csv.ts`, `nav.ts` | frontend (Planos 1, 3, 4, 5) |
| Componentes de gráfico criados nos Planos 5 a 7 (se houver) | telas dos Planos 5 a 7 |
| Último número de migração na `main` (confirme com `ls`; hoje só existe a V1) | `resources/db/migration/` |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- **A única trava de acesso ao Jira é a flag `JIRA_LIVE_ENABLED`** (regra 1 do `CLAUDE.md`, 25/10). Este plano não acessa o Jira: tudo roda com fixtures, `MockEngine` e dados de demonstração. O **link para o Jira** só é montado quando a flag é `true` e a instância está configurada; com `false`, a resposta traz `linkJira = null` e a tela não mostra o botão (abrir o link seria um acesso ao Jira por uma pessoa).
- O link nunca é escrito no código: sai de `JiraInstanceSettings.baseUrl` (variável `JIRA_<INSTANCIA>_URL`, lida pelo `Settings`), só com esquema `https`, host, porta e caminho base, sem credenciais nem query (`Settings.mascararUrl` não serve: aqui a URL tem de ser limpa, não mascarada).
- **Visibilidade:** a gaveta, as opções de filtro e os filtros por campo só devolvem o que está no escopo visível do principal (`Visibility`/`Escopo`). Issue fora da visão → 404 (nunca 403, para não confirmar que a chave existe). Pessoa fora da visão aparece como "outra pessoa" (`null`), nunca pelo nome. O histórico campo a campo na gaveta só aparece para `COORDENADOR` e `GERENTE` (mesma regra da Auditoria).
- **"Card = tamanho do detalhe":** todo filtro novo é aplicado antes do cálculo, igual no card e no drill; os testes `drillTemOMesmoTamanhoDoCard` existentes continuam verdes com filtro por campo.
- Sem ranking de pessoas: listas de pessoas (opções de filtro inclusive) em ordem alfabética.
- Interface: cor nunca é o único sinal (`✓` `●` `▲` com texto e, nos gráficos, padrão e forma); pt-BR e inglês completos; três temas; foco e `Esc` em gaveta; CSV com neutralização de fórmula (`shared/csv.ts`).
- Toda rota nova grava `access_log` (inclusive 400, 403 e 404) por `executarAuditado`; falha ao gravar → 503.
- **Migração:** uma só, `V{n}__campos_do_dicionario.sql`, com `n` = próxima migração livre (confirme com `ls app/backend/src/main/resources/db/migration`; hoje só existe a V1, então a V2). O esquema novo entra numa migração nova, nunca editando a V1 nem outra já mesclada.
- Nenhuma variável nova começa com `JIRA_`. Nenhuma dependência npm nova. Sem Docker: banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`); a suíte só exige JDK 21.
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(filtros): ...`, `feat(ui): ...`, `fix(cards): ...`).

## Foco da revisão (Review Focus)

1. **Opções de filtro vazando pessoas ou valores de fora da visão** (USUARIO pedindo `/api/filtros/opcoes`): só o próprio nome nas pessoas e só valores de campo das issues visíveis → `opcoesSoDoEscopoVisivel` (T2).
2. **Gaveta pedida por chave de outro squad** (Squad Leader trocando a URL): 404 sem corpo útil, e `access_log` gravado → `tarefaForaDaVisao404` (T5).
3. **Link para o Jira com a flag `false`** ou com URL configurada com usuário e senha (`https://u:p@host`): nada de link no primeiro caso; link sem credenciais no segundo → `semLinkComFlagDesligada` e `linkSemCredenciais` (T5).
4. **Drill de 30.000 linhas** (escala do Plano 8): a tabela renderiza só as linhas visíveis e continua navegável por teclado → `renderizaSoAsLinhasVisiveis` e `tecladoRolaATabela` (T7).
5. **Código de aviso ou de erro sem tradução** (backend cria um código novo e esquece o i18n): o build falha → `todoCodigoTemTraducaoPtEEn` (T9).
6. **Valor de campo com caixa ou espaço diferente** (`MR` × `mr ` vindo do Jira): o filtro casa sem diferenciar maiúsculas e sem espaços nas pontas, e as opções não duplicam → `filtroDeCampoSemDiferenciarMaiusculas` (T3).

---

## Arquivos (mapa)

```
app/backend/src/main/resources/db/migration/V{n}__campos_do_dicionario.sql
app/backend/src/main/kotlin/br/sidia/painel/
  sync/CamposDoDicionario.kt, sync/IssueStore.kt, sync/PreenchimentoDeCampos.kt
  trabalho/IssueSnapshot.kt, trabalho/WorkStore.kt
  cards/FiltrosDeCard.kt, cards/CardDeLeitura.kt, cards/OpcoesDeFiltroRoutes.kt, cards/OpcoesDeFiltroDto.kt
  tarefa/{TarefaRoutes.kt, TarefaDto.kt, LinkJira.kt}
  http/CodigosDeMensagem.kt
  cards/fechadas/, cards/negativas/, inicio/ (ajustes da T10)
  demo/DadosDeDemonstracao.kt, resources/demo/{manifesto.yaml,samsung.json}
app/config/jira_campos.exemplo.yaml, app/config/cards/{sincronizacao,central,integridade,auditoria}.card.yaml
app/frontend/src/shared/{EstadoDaTela.tsx, TabelaVirtual.tsx, TarefaDrawer.tsx, Filtros.tsx, CardPage.tsx, DrillDrawer.tsx}
app/frontend/src/shared/graficos/{paleta.ts, Barras.tsx, Linhas.tsx, Dispersao.tsx, Legenda.tsx, padroes.tsx}
app/frontend/src/screens/Detalhe/{Detalhe.tsx, Detalhe.test.tsx}
app/frontend/src/i18n/{pt,en}.json
docs/analise/diferencas-esperadas-complementos.md
```

---

### Tarefa 1: Campos do dicionário na base e no `IssueSnapshot`

**Arquivos:** Criar `resources/db/migration/V{n}__campos_do_dicionario.sql`, `sync/CamposDoDicionario.kt`, `sync/PreenchimentoDeCampos.kt`; Modificar `sync/IssueStore.kt` (`upsert` grava a coluna), `trabalho/IssueSnapshot.kt`, `trabalho/WorkStore.kt`, `AppContext.kt` (chama o preenchimento na subida), `app/config/jira_campos.exemplo.yaml` (chave `filtro: true` nos seis campos), `app/config/cards/sincronizacao.card.yaml` (acrescenta `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code` a `campos`), `demo/DadosDeDemonstracao.kt`, `resources/demo/manifesto.yaml` e `samsung.json` (customIds e valores fictícios dos seis campos), `docs/jira/checklist-primeira-conexao.md` (conferir os quatro campos novos da ficha); Teste `sync/CamposDoDicionarioTest.kt`, `sync/PreenchimentoDeCamposTest.kt`, `trabalho/WorkStoreTest.kt`, `jql/CardQueryFactoryTest.kt`, `demo/FixturesDeDemoTest.kt`.

**Interfaces:**
- Migração: `ALTER TABLE issue ADD COLUMN campos_dicionario TEXT` (JSON em texto; anulável: `NULL` = ainda não extraído; `'{}'` = extraído e vazio). Sem índice GIN (não existe no SQLite): os filtros usam `json_extract(campos_dicionario, '$."Model Name"') = ?` e, para valores repetíveis e opções de filtro, `json_each(campos_dicionario)`; o orçamento de desempenho (Plano 8, T6) mede e, só se estourar, uma migração própria cria índice de expressão sobre `json_extract`.
- Produz:
  ```kotlin
  data class CampoDeFiltro(val nome: String, val slug: String, val customId: String?)   // slug: "checklist-type", "sku-type", "model-name", "model-category", "tg-request-type", "carrier-code"
  object CamposDoDicionario {
      fun deFiltro(dic: FieldDictionary, instancia: String): List<CampoDeFiltro>      // campos com filtro: true, na ordem do YAML
      fun extrair(issue: JsonObject, campos: List<CampoDeFiltro>): Map<String, String> // nome -> valor; SELECT lê "value"/"name"; TEXT lê string; lista vira valores unidos por "; "; trim; vazio fica fora
  }
  class PreenchimentoDeCampos(private val ds: DataSource) {
      fun preencher(instancia: String, campos: List<CampoDeFiltro>, lote: Int = 500): Int   // só linhas com campos_dicionario IS NULL, a partir de issue.raw; nunca fala com o Jira
  }
  // IssueSnapshot ganha: val camposDicionario: Map<String, String> = emptyMap()
  ```
  `jira_campos.exemplo.yaml`: `filtro: true` em `Checklist Type`, `SKU Type`, `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code` (chave nova, opcional, padrão `false`; `FieldDictionary` estrito aceita a chave). O `OS Version` continua com a coluna própria (`os_version`) e o filtro `os` do Plano 5.
  Linhas gravadas antes da ficha nova não têm os quatro campos acrescentados no `raw`: o preenchimento grava só o que existe; o aviso informativo `CAMPOS_DO_DICIONARIO_INCOMPLETOS` (`params["n"]`) sai nos cards enquanto houver issue com algum dos seis campos ausente **e** `updated` anterior à data da migração. Uma reconciliação completa do Plano 4A (com a flag ligada pelo usuário) completa as linhas.
  Dados de demonstração: `InstanciaDemo` ganha `camposCustomIds: Map<String, String> = emptyMap()` (ids fictícios `customfield_90001`..`90006` só no manifesto); as fixtures `samsung.json` ganham valores fictícios (`Checklist Type`: `Full`, `Regression`; `SKU Type`: `MR`, `1st Carrier (Global)`; `Model Category`: `Phone`, `Tablet`; `TG Request Type`: `New Model`, `Maintenance`; `Model Name` e `Carrier Code` fictícios como `MODELO-A`, `OP01`).

- [ ] **Passo 1: Testes que falham:**
  - `extraiSelectTextoELista` (`{"customfield_90001":{"value":"Full"}}` → `Checklist Type=Full`; texto `" OP01 "` → `OP01`; lista de dois SELECT → `"A; B"`; ausente ou `null` → fora do mapa).
  - `campoSemCustomIdFicaFora` (sem exceção).
  - `upsertGravaCamposDoDicionario` (SQLite em arquivo temporário) e `upsertSemCamposGravaObjetoVazio`.
  - `preencheRetroativamenteDoRaw` (linha com `campos_dicionario IS NULL` e `raw` com `customfield_90002` → preenchida; segunda chamada → 0 linhas tocadas).
  - `preenchimentoNaoFazRequisicao` (contexto com `MockEngine` contador → 0).
  - `snapshotTrazCamposDoDicionario` (`WorkStoreTest`).
  - `fichaDeSincronizacaoPedeOsSeisCampos` (`CardQueryFactoryTest`).
  - `fixturesDeDemoTemOsSeisCampos` (`FixturesDeDemoTest`; sem e-mail nem host real).
  - `avisoCamposIncompletosComLinhasAntigas`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*CamposDoDicionarioTest*' --tests '*PreenchimentoDeCamposTest*' --tests '*WorkStoreTest*' --tests '*CardQueryFactoryTest*' --tests '*FixturesDeDemoTest*'` → FALHA.
- [ ] **Passo 3: Implementar** (SQL parametrizado; o JSON é gravado como texto com `emJson()` de `db/SqlSupport.kt`, sem `?::jsonb`).
- [ ] **Passo 4: Rodar** → PASSA e suíte completa (conferir os XML).
- [ ] **Passo 5: Commit** `feat(filtros): campos do dicionário na base, com preenchimento a partir do raw`.

---

### Tarefa 2: Opções de filtro pelo escopo visível

**Arquivos:** Criar `cards/OpcoesDeFiltroRoutes.kt`, `cards/OpcoesDeFiltroDto.kt`; Modificar `Application.kt`; Teste `cards/OpcoesDeFiltroRoutesTest.kt`.

**Interfaces:**
- `GET /api/filtros/opcoes` (todos os papéis; `executarAuditado`):
  ```kotlin
  @Serializable data class OpcoesDeFiltroDto(val squads: List<String>, val pessoas: List<String>, val tipos: List<String>,
      val prioridades: List<String>, val os: List<String>, val campos: List<CampoOpcoesDto>)
  @Serializable data class CampoOpcoesDto(val nome: String, val slug: String, val valores: List<String>)
  ```
  - `squads`: os que o principal enxerga hoje (`RoleDirectory`), alfabéticos; USUARIO recebe só o próprio squad.
  - `pessoas`: `Visibility.visibleUserIds` do principal, alfabéticas (USUARIO: só ele mesmo).
  - `tipos`, `prioridades`, `os` e `campos[].valores`: valores distintos das issues **visíveis** dos últimos 180 dias de Manaus (abertas ou resolvidas no período), sem diferenciar maiúsculas (mantém a primeira grafia em ordem alfabética), sem vazios; no máximo 200 por lista (excedente → aviso `OPCOES_TRUNCADAS`).
  - Sem dado: listas vazias, 200.

- [ ] **Passo 1: Testes que falham** (cenário TRB-1 do Plano 5 com os campos novos):
  - `opcoesSoDoEscopoVisivel` (`ana` USUARIO → `pessoas == ["ana"]`, `squads == ["alfa"]`, valores só das issues de `ana`; `lider.alfa` → pessoas do Alfa; `gerente.exemplo` → todas).
  - `pessoasEmOrdemAlfabetica`; `valoresSemDuplicarCaixa` (`MR` e `mr ` → só `MR`).
  - `todaConsultaGravaAccessLog`; `semDadosListasVazias`; `limiteDe200ComAviso`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*OpcoesDeFiltroRoutesTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA.
- [ ] **Passo 5: Commit** `feat(filtros): opções de filtro pelo escopo visível do principal`.

---

### Tarefa 3: Filtros e agrupamentos por campo do dicionário em todos os cards

**Arquivos:** Modificar `cards/FiltrosDeCard.kt`, `cards/CardDeLeitura.kt`, `cards/abertas/AbertasDto.kt` e calculador, `cards/fechadas/FechadasDto.kt` e calculador; Teste `cards/FiltrosDeCardTest.kt`, `cards/CardDeLeituraTest.kt`, `cards/abertas/AbertasCalculatorTest.kt`, `cards/fechadas/FechadasCalculatorTest.kt`.

**Interfaces:**
- `FiltrosDeCard` ganha `val campos: Map<String, Set<String>> = emptyMap()` (nome do campo → valores aceitos). Parâmetro de URL `campo.<slug>=<valor>` (repetível; ex.: `campo.sku-type=MR&campo.sku-type=1st%20Carrier%20(Global)`). Slug desconhecido → 400 `ErroDto("FILTRO_INVALIDO", {"campo": "<slug>"})`; valor com controle ou mais de 120 caracteres → 400 `FILTRO_INVALIDO`.
- `CardDeLeitura`: antes de chamar `calcular`/`detalhar`, aplica `fun IssueSnapshot.casaCampos(campos: Map<String, Set<String>>): Boolean` (cada campo pedido precisa casar um dos valores, sem diferenciar maiúsculas e sem espaços nas pontas; issue sem o campo não casa). A mesma lista filtrada vai para card e drill.
- Agrupamento (spec 6.1): `AbertasDto` e `FechadasDto` ganham `val porCampo: List<AgrupamentoDto> = emptyList()` com `@Serializable data class AgrupamentoDto(val campo: String, val slug: String, val grupos: List<ContagemGrupoDto>)` e `@Serializable data class ContagemGrupoDto(val valor: String?, val n: Int)` (`valor = null` = "sem valor"), um por campo com `filtro: true`, grupos em ordem alfabética com "sem valor" por último. Drill `kind=campo:<slug>:<valor>` (e `campo:<slug>:-` para sem valor) em Abertas e Fechadas.

- [ ] **Passo 1: Testes que falham:**
  - `lerFiltroDeCampoRepetivel`; `slugDesconhecido400ComCodigo`; `valorComControle400`.
  - `filtroDeCampoSemDiferenciarMaiusculas` (`campo.sku-type=mr` casa `MR`).
  - `issueSemOCampoNaoCasa`.
  - `drillTemOMesmoTamanhoDoCardComFiltroDeCampo` (Abertas e Fechadas com `campo.checklist-type=Full`).
  - `agrupamentoPorCampoComSemValorPorUltimo` e `drillDoGrupoIgualAoGrupo` (`campo:sku-type:MR` == `n` do grupo).
  - Os testes de rota dos Planos 5 a 7 continuam verdes sem o parâmetro novo.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FiltrosDeCardTest*' --tests '*CardDeLeituraTest*' --tests '*AbertasCalculatorTest*' --tests '*FechadasCalculatorTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA e suíte completa.
- [ ] **Passo 5: Commit** `feat(filtros): filtros e agrupamentos por Checklist Type, SKU Type e demais campos do dicionário`.

---

### Tarefa 4: Filtros na tela com opções do backend

**Arquivos:** Modificar `app/frontend/src/shared/Filtros.tsx`, `app/frontend/src/shared/CardPage.tsx`, `app/frontend/src/api/client.ts` (`fetchOpcoesDeFiltro`), `i18n/{pt,en}.json`; Teste `shared/Filtros.test.tsx`.

**Interfaces:**
- `export function Filtros(props: { disponiveis: Array<'periodo'|'squad'|'pessoa'|'tipo'|'prioridade'|'os'|'campos'|'unit'> })`: lê as opções com `useQuery(['filtros-opcoes'], fetchOpcoesDeFiltro)`; cada filtro vira um seletor (`<select>` para um valor, lista de caixas para repetíveis) com rótulo; nada de texto livre para pessoa, squad, tipo, OS ou campos (texto livre só fica em período, que é data). Os valores vão para a URL (`campo.<slug>`), como os demais filtros. Chips mostram os filtros ativos com botão "remover" acessível.
- Valor na URL que não está nas opções (link antigo) continua aplicado e aparece no chip com o aviso "valor fora das opções atuais" (o backend decide).

- [ ] **Passo 1: Testes que falham:**
  - `pessoaViraSeletorComAsOpcoesDoBackend` (sem `<input type="text">` para pessoa).
  - `campoDoDicionarioVaiParaAUrl` (`campo.sku-type=MR`).
  - `chipRemoveFiltro` (teclado: `Tab` até o chip, `Enter` remove).
  - `valorForaDasOpcoesContinuaNoChip`.
  - `paridadeDeChavesPtEn` (já existe; rodar).
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shared src/i18n` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build`.
- [ ] **Passo 5: Commit** `feat(ui): filtros com opções do backend e campos do dicionário`.

---

### Tarefa 5: Gaveta universal da task (API)

**Arquivos:** Criar `tarefa/TarefaRoutes.kt`, `tarefa/TarefaDto.kt`, `tarefa/LinkJira.kt`; Modificar `Application.kt`; Teste `tarefa/TarefaRoutesTest.kt`, `tarefa/LinkJiraTest.kt`.

**Interfaces:**
- `GET /api/tarefa/{key}?instancia=samsung` (todos os papéis; `executarAuditado`; chave validada por `JqlValues.issueKey` → 400 `ErroDto("CHAVE_INVALIDA")`; inexistente **ou fora da visão** → 404 sem corpo):
  ```kotlin
  @Serializable data class TarefaDto(val key: String, val resumo: String, val tipo: String, val status: String, val categoria: String,
      val resolucao: String?, val responsavel: String?, val prioridade: String?, val criada: String, val atualizada: String?,
      val resolvida: String?, val vencimento: String?, val vencimentoOriginal: String?, val labels: List<String>,
      val campos: Map<String, String>, val tempoPorStatus: List<TempoStatusDto>, val posse: List<FracaoPosseDto>,
      val fatoresDeRisco: List<String>, val historico: List<LinhaHistoricoDto>?, val linkJira: String?, val avisos: List<AvisoDto>)
  @Serializable data class TempoStatusDto(val status: String, val dias: Double)
  object LinkJira { fun montar(settings: Settings, instancia: String, key: String): String? }
  ```
  - `tempoPorStatus`: da linha do tempo, na unidade padrão (dias úteis), status em ordem de primeira entrada.
  - `posse`: `PosseCalculator.posse` (pessoa fora da visão = `null`).
  - `fatoresDeRisco`: nomes dos `FatorDeRisco` que o `RiscoCalculator` atribui hoje (só para abertas; resolvidas → lista vazia).
  - `historico`: só para `COORDENADOR` e `GERENTE` (as mesmas linhas de `/api/auditoria/historico/{key}`, com `valorGuardado`); demais papéis → `null` (campo ausente na tela, sem mensagem de erro). O Plano 11 acrescenta a trava do DPO a este campo.
  - `responsavel` fora da visão → `null`.
  - `linkJira = LinkJira.montar(...)`: `null` se `settings.jiraLiveEnabled == false`, se a instância não estiver em `settings.jiraInstances`, se a URL não for `https`; senão `<esquema>://<host>[:porta]<caminho base sem barra final>/browse/<key>` (sem `user:senha@`, sem query, sem fragmento; a `key` já validada).

- [ ] **Passo 1: Testes que falham:**
  - `tarefaVisivelDevolveTudo` (TG-101 como `gerente.exemplo`: tempo por status, posse, fatores, histórico não nulo).
  - `tarefaForaDaVisao404` (`ana` pedindo a de `carla` → 404; `access_log` gravado).
  - `historicoSoParaCoordenacaoEGerente` (`lider.alfa` → `historico == null`; `coord.um` → lista).
  - `responsavelForaDaVisaoViraNulo`.
  - `chaveInvalida400ComCodigo` (`TG-1;drop`).
  - `LinkJiraTest`: `semLinkComFlagDesligada`; `semLinkSemInstancia`; `linkSemCredenciais` (`https://u:p@jira.exemplo.invalid/base/?x=1` → `https://jira.exemplo.invalid/base/browse/TG-1`); `semLinkComHttp`; `nenhumHostNoCodigo` (o arquivo `LinkJira.kt` não contém `jira.` nem `https://` literal além do esquema).
  - `rotaNaoFazRequisicao` (contexto com flag `true` e `MockEngine` contador → 0 requisições: montar o link não chama o Jira).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TarefaRoutesTest*' --tests '*LinkJiraTest*'` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA e suíte completa.
- [ ] **Passo 5: Commit** `feat(tarefa): gaveta universal da task com link para o Jira montado da configuração`.

---

### Tarefa 6: Gaveta e detalhe em nova aba em todas as telas

**Arquivos:** Criar `app/frontend/src/shared/TarefaDrawer.tsx`, `app/frontend/src/screens/Detalhe/{Detalhe.tsx,Detalhe.test.tsx}`; Modificar `shared/DrillDrawer.tsx` (chave de cada linha abre a `TarefaDrawer`; botão "Abrir em nova aba"), `App.tsx` (rota `/detalhe`), `api/client.ts` (`fetchTarefa`), `screens/Auditoria/` (o `HistoricoDrawer` passa a reaproveitar a `TarefaDrawer` com o histórico aberto; importar `DrillDrawer` de `shared/`, inconsistência E12), i18n; Teste `shared/TarefaDrawer.test.tsx`.

**Interfaces:**
- `export function TarefaDrawer(props: { chave: string; instancia?: string; aoFechar: () => void })`: seções "Situação" (status com símbolo, datas, vencimento e vencimento original), "Tempo por status" (tabela), "Posse" (frações, "outra pessoa" para `null`), "Fatores de risco", "Campos" (dicionário), "Histórico" (só se `historico != null`, com "valor não guardado (privacidade)") e o botão "Abrir no Jira" (só se `linkJira != null`; `target="_blank"`, `rel="noopener noreferrer"`). `Esc` fecha e devolve o foco à linha de origem; `aria-modal`; título com a chave.
- `/detalhe?card=<id>&kind=<kind>&<filtros>`: página que mostra o mesmo drill em tela cheia (mesma chamada `/api/drill/{id}`), com CSV; "Abrir em nova aba" no `DrillDrawer` usa `window.open(url, '_blank', 'noopener')` com a URL montada da tela atual.

- [ ] **Passo 1: Testes que falham:**
  - `chaveDoDrillAbreGaveta` (em Abertas, Fechadas e Tempo em status: clicar na chave abre a gaveta com o título).
  - `escFechaEDevolveFoco`.
  - `historicoSoQuandoVem` (`historico: null` → seção ausente).
  - `botaoJiraSoComLink` e `botaoJiraAbreEmNovaAbaSemOpener` (`rel` contém `noopener` e `noreferrer`).
  - `outraPessoaNaPosse`.
  - `detalheEmNovaAbaMesmaListaDoDrill` (mesmo número de linhas e mesmo CSV).
  - `auditoriaUsaDrillDoShared` (nenhum import de `screens/TempoEmStatus/DrillDrawer`).
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/shared src/screens` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build && node scripts/verificar-csp.mjs`.
- [ ] **Passo 5: Commit** `feat(ui): gaveta universal da task e detalhe em nova aba em todas as telas`.

---

### Tarefa 7: Paginação virtual de tabelas grandes

**Arquivos:** Criar `app/frontend/src/shared/TabelaVirtual.tsx`, `shared/TabelaVirtual.test.tsx`; Modificar `shared/DrillDrawer.tsx`, `screens/Detalhe/Detalhe.tsx` e as tabelas de lista das telas (Abertas, Fechadas, Auditoria, Integridade, Registro de acesso) para usar o componente.

**Interfaces:**
- `export function TabelaVirtual<T>(props: { colunas: Coluna<T>[]; linhas: T[]; alturaLinha?: number /* 36 */; altura?: number /* 480 */; limiarVirtual?: number /* 200 */; rotulo: string; ordenacao?: Ordenacao })`:
  - abaixo do limiar, renderiza todas as linhas (comportamento atual);
  - acima, renderiza só as linhas da janela visível mais 10 de folga acima e abaixo, com espaçadores de altura (`<tr aria-hidden>`), cabeçalho fixo (`<th scope="col">`);
  - `role="grid"` com `aria-rowcount` = total e `aria-rowindex` em cada linha renderizada;
  - teclado: `↑`/`↓` movem a linha ativa, `PageUp`/`PageDown` movem uma janela, `Home`/`End` vão ao início e fim, rolando o contêiner; `Enter` na linha ativa abre a gaveta (quando a tabela tiver `aoAbrir`);
  - ordenação por coluna continua no cliente (sobre a lista inteira) e não muda a contagem.
- O CSV sempre exporta a lista inteira, nunca só a janela.

- [ ] **Passo 1: Testes que falham** (jsdom: a altura do contêiner vem da prop; rolagem simulada com `fireEvent.scroll` alterando `scrollTop`):
  - `abaixoDoLimiarRenderizaTudo` (150 linhas → 150 `<tr>` de dados).
  - `renderizaSoAsLinhasVisiveis` (30.000 linhas, altura 480, linha 36 → no máximo 14 + 20 linhas de dados no DOM; `aria-rowcount=30001` contando o cabeçalho).
  - `rolarMostraAJanelaCerta` (`scrollTop = 36 × 1000` → linha 1001 presente).
  - `tecladoRolaATabela` (`End` → última linha presente e ativa; `Home` → primeira).
  - `ordenacaoNaoMudaContagem`; `csvExportaTudo` (30.000 linhas no CSV).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/shared/TabelaVirtual` → FALHA.
- [ ] **Passo 3: Implementar** e trocar as tabelas de lista.
- [ ] **Passo 4: Rodar** a suíte do frontend → PASSA; `pnpm build`.
- [ ] **Passo 5: Commit** `feat(ui): paginação virtual nas tabelas grandes`.

---

### Tarefa 8: Estados de tela padronizados

**Arquivos:** Criar `app/frontend/src/shared/EstadoDaTela.tsx`, `shared/EstadoDaTela.test.tsx`, `app/frontend/src/screens/estados.test.tsx`; Modificar `shared/CardPage.tsx`, `shared/useCard.ts`, i18n.

**Interfaces:**
- `export type Estado = { tipo: 'carregando' } | { tipo: 'vazio' } | { tipo: 'erro'; tentarDeNovo: () => void } | { tipo: 'sem-permissao' } | { tipo: 'sessao' } | { tipo: 'incompleto'; avisos: AvisoDto[] } | { tipo: 'ok' }`
- `export function estadoDe(q: UseQueryResult<unknown>, vazio: (d: unknown) => boolean): Estado`: `isPending` → carregando; erro `ErroDaApi` 403 → sem-permissão; 401 → sessão (o diálogo do Plano 9 já aparece; a tela mostra só "Entre de novo para ver os dados"); outro erro → erro; dados sem linhas → vazio; avisos de gravidade informativa → `ok` com faixa de avisos; aviso bloqueante → incompleto.
- `export function EstadoDaTela(props: { estado: Estado; papel: string[] })`:
  - **carregando:** esqueleto com `aria-busy="true"` e texto para leitor de tela "Carregando…";
  - **vazio:** "Nenhum item para os filtros escolhidos." e botão "Limpar filtros";
  - **erro:** "Não foi possível carregar os dados." e botão "Tentar de novo" (chama `tentarDeNovo`); nunca mostra mensagem técnica;
  - **sem-permissão:** "Esta tela é para <papéis permitidos>." (lista vinda do `nav.ts`);
  - **incompleto:** texto do aviso por código (T9) e, só para `GERENTE`, link "Ver em Qualidade dos dados" (`/qualidade`).
  `CardPage` usa `EstadoDaTela` sempre; telas deixam de ter tratamento próprio.

- [ ] **Passo 1: Testes que falham:**
  - `EstadoDaTela`: um teste por estado (pt e en), `erroTemTentarDeNovoQueRefaz`, `linkDeQualidadeSoParaGerente`, `erroNuncaMostraTextoTecnico` (resposta 500 com corpo `"java.lang.NullPointerException"` → texto não aparece).
  - `estados.test.tsx`: para cada item de `nav.ts` com tela, renderiza a tela com respostas simuladas de 403, 500, lista vazia e aviso bloqueante → o estado certo aparece (teste dirigido por tabela, um caso por item do menu).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/shared src/screens/estados` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suíte do frontend; `pnpm build`.
- [ ] **Passo 5: Commit** `feat(ui): estados de tela padronizados (carregando, vazio, erro, sem permissão, sessão, incompleto)`.

---

### Tarefa 9: Mensagens do backend por código, com i18n pt e en

**Arquivos:** Criar `http/CodigosDeMensagem.kt`, `http/CodigosDeMensagemTest.kt`; Modificar todo `respond(HttpStatusCode.BadRequest, <texto>)` e `FiltroInvalidoException` (passam a responder `ErroDto`), as telas que leem `AvisoDto.mensagem` (passam a ler `codigo` + `params`), `i18n/{pt,en}.json` (chaves `avisos.<CODIGO>` e `erros.<CODIGO>`), `app/frontend/src/i18n/index.ts` (`tAviso(aviso)`, `tErro(erro)` com interpolação de `params`).

**Interfaces:**
- Produz:
  ```kotlin
  object CodigosDeMensagem {
      val avisos: Set<String>     // todo código de AvisoDto que o backend pode emitir (Planos 3 a 10)
      val erros: Set<String>      // todo código de ErroDto: FILTRO_INVALIDO, CHAVE_INVALIDA, PERIODO_INVALIDO, PERIODO_LONGO_DEMAIS, CSRF_AUSENTE, ORIGEM_INVALIDA, ...
  }
  ```
  Toda resposta 400 passa a ser `ErroDto(codigo, params)`; nenhum texto em pt-BR no corpo de erro. `AvisoDto.mensagem` continua no DTO (compatibilidade e log), mas a tela nunca o mostra: usa `codigo` e `params`.
  - Chave i18n: `avisos.<CODIGO>` e `erros.<CODIGO>`, com `{param}` interpolado; código sem chave → a tela mostra `avisos.DESCONHECIDO` ("Aviso do servidor (código <codigo>)") e nunca a mensagem pt-BR.
  - Datas e números na tela formatados por `Intl` com o idioma atual (`pt-BR` → `30/10/2026`, `1.234,5`; `en` → `10/30/2026`, `1,234.5`).

- [ ] **Passo 1: Testes que falham:**
  - `todoCodigoTemTraducaoPtEEn` (backend, lê `app/frontend/src/i18n/pt.json` e `en.json` por `painel.repoDir`: cada código de `avisos` e `erros` tem chave nas duas).
  - `codigosConhecidosCobremOsEmitidos` (varre `src/main` por `AvisoDto("` e `ErroDto("` e confere que cada literal está em `CodigosDeMensagem`).
  - `respostas400SaoErroDto` (amostra: Tempo em status com `de=2026-13-01`, Abertas com `campo.x=1`, Auditoria com período de 181 dias → corpo `{"codigo":...}` sem texto pt-BR).
  - Frontend: `avisoPorCodigoComParametro` (`STATUS_SEM_MAPEAMENTO` com `params.status` em pt e en), `codigoDesconhecidoNaoMostraMensagemPt`, `datasENumerosPorIdioma`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CodigosDeMensagemTest*'` e as rotas afetadas; `pnpm vitest run src/i18n src/shared` → FALHA.
- [ ] **Passo 3: Implementar.**
- [ ] **Passo 4: Rodar** → PASSA; suítes completas.
- [ ] **Passo 5: Commit** `feat(i18n): mensagens do backend por código com tradução pt e en`.

---

### Tarefa 10: Ajustes apontados pela auditoria nos Planos 5 e 7

**PONTO DE PARADA (antes do Passo 3): VG-02 e VG-03 na Central** (spec, ponto de parada 1). Sem resposta, seguir com a opção recomendada.

**Arquivos:** Modificar `cards/fechadas/` (FE-11, FE-12), `cards/negativas/` (NE-09), `domain/NoPrazo.kt` (só se o teste de universo falhar), `inicio/CentralCalculator.kt` e `inicio/InicioDto.kt` (VG-02, VG-03), as telas correspondentes; Criar `app/config/cards/{central,integridade,auditoria}.card.yaml` (B4) se não existirem; Teste `cards/fechadas/FechadasCalculatorTest.kt`, `cards/negativas/NegativasCalculatorTest.kt`, `cards/ConsistenciaEntreTelasTest.kt` (do Plano 5, T9), `inicio/CentralCalculatorTest.kt`, `docs/FichasDoMenuTest.kt`.

**Interfaces:**
- **FE-11 (tempo ativo por pessoa):** `FechadasDto` ganha, em cada linha de "entregas por pessoa", `val tempoAtivoDias: Double?` = soma dos intervalos em categoria ACTIVE em que a pessoa era a responsável, nas concluídas do período, em dias úteis. Pessoas em ordem alfabética.
- **FE-12 (tabela de concluídas):** `FechadasDto` ganha `val linhas: List<LinhaConcluidaDto>` com `@Serializable data class LinhaConcluidaDto(val key: String, val resumo: String, val tipo: String, val responsavel: String?, val resolvida: String, val vencimento: String?, val noPrazo: Boolean?, val noPrazoAjustado: Boolean?, val estimadoH: Double?, val gastoH: Double?, val realSobreEstimado: Double?)`; `realSobreEstimado = gasto ÷ estimado` (estimado 0 ou nulo → `null`); ordem por `resolvida` decrescente e depois `ORDEM_NATURAL_DE_CHAVES`; CSV na tela.
- **NE-09 ("maior tempo" por status):** cada lista de Negativas (reabertas, devolvidas, canceladas, concluídas com atraso) ganha, por issue, `val maiorTempo: TempoStatusDto?` = status onde a issue ficou mais tempo (dias úteis), e o DTO ganha `val maiorTempoPorStatus: List<ContagemGrupoDto>` (quantas issues têm cada status como o de maior tempo, alfabético).
- **E5 (universo de "no prazo"):** acrescentar ao `ConsistenciaEntreTelasTest` o caso `TG-121` cancelada (`Won't Do`) **com vencimento** e um `Epic` concluído com vencimento: Fechadas, Metas e Auditoria continuam com o mesmo `noPrazo`, e nenhuma das três conta `TG-121` nem o Epic. Se falhar, `NoPrazo.calcular` passa a excluir resoluções negativas (`StatusMap.resolucoesNegativas`) e o tipo `Epic`.
- **VG-02 e VG-03 (Central):** opção recomendada: `CentralDto` ganha `val concluidas30d: KpiComVariacaoDto` (`atual`, `anterior` = 30 dias anteriores, `variacao` em pontos percentuais ou `null` sem base) e `val ciclo30d: FaixaDto?` (P50 e P85 em dias úteis das concluídas positivas em 30 dias), ambos com drill para Fechadas com o período correspondente. Se o usuário escolher "remover", o documento de equivalência do Plano 8 marca VG-02 e VG-03 como `removido` com o motivo, e nada muda na Central.
- **B4 (ficha por item do menu):** toda entrada de `nav.ts` que é tela de dados tem `app/config/cards/<id>.card.yaml` (Central, Integridade e Auditoria são as que faltam; Central e Integridade não têm JQL próprio: a ficha declara `fontes: [<ids dos cards de origem>]`, a fórmula e o aceite, sem consulta).

- [ ] **Passo 1: Testes que falham:**
  - `tempoAtivoPorPessoa` (TRB-1: `ana` em TG-111 de 10-05 a 10-09 → 4 dias úteis).
  - `tabelaDeConcluidasComRealSobreEstimado` (TG-111: estimado 4 h, gasto 6 h → `1.5`; estimado nulo → `null`).
  - `maiorTempoPorStatusNasNegativas`.
  - `canceladaComVencimentoForaDoNoPrazoEmTresTelas` (E5).
  - `centralTemConcluidas30dComVariacao` e `centralTemCiclo30d`, com `drillTemOMesmoTamanhoDoCard` para os dois (só se a opção recomendada for a escolhida).
  - `cadaItemDoMenuTemFicha` (`FichasDoMenuTest`: lê os ids de `nav.ts` e confere o arquivo).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FechadasCalculatorTest*' --tests '*NegativasCalculatorTest*' --tests '*ConsistenciaEntreTelasTest*' --tests '*CentralCalculatorTest*' --tests '*FichasDoMenuTest*'` → FALHA.
- [ ] **Passo 3: Implementar** conforme a resposta do ponto de parada; telas com as colunas e KPIs novos.
- [ ] **Passo 4: Rodar** → PASSA; suítes completas.
- [ ] **Passo 5: Commit** `fix(cards): tempo ativo, tabela de concluídas, maior tempo por status, no prazo sem canceladas e fichas do menu`.

---

### Tarefa 11: Componente de gráfico e paleta categórica acessível

**Arquivos:** Criar `app/frontend/src/shared/graficos/{paleta.ts, padroes.tsx, Barras.tsx, Linhas.tsx, Dispersao.tsx, Legenda.tsx, graficos.test.tsx}`; Modificar `theme/tokens.css` (tokens `--cat-1`..`--cat-6` nos três temas) e as telas que hoje desenham gráficos (Fechadas FE-07/FE-08, Metas MT-08/MT-09, Previsão PV-04, Gargalos GA-01, Central VG-10, Tempo em status TS-05) para usar os componentes.

**Interfaces:**
- `paleta.ts`: `export const CATEGORIAS = ['--cat-1', ..., '--cat-6'] as const`; `export const PADROES = ['solido', 'listras', 'pontos', 'xadrez', 'diagonal', 'ondas'] as const`; `export const FORMAS = ['circulo', 'quadrado', 'triangulo', 'losango', 'cruz', 'estrela'] as const`. Série `i` usa cor `CATEGORIAS[i % 6]`, padrão `PADROES[i % 6]` e forma `FORMAS[i % 6]`.
- Cores (valor padrão 25/10, reversível; distinguíveis em deuteranopia, protanopia e tritanopia, sequência de Okabe-Ito): `#0072B2`, `#E69F00`, `#009E73`, `#CC79A7`, `#56B4E9`, `#D55E00`. No tema Escuro, as mesmas com luminância ajustada só se o contraste com a superfície ficar abaixo de 3:1.
- Componentes SVG (`role="img"` com `aria-label` que resume o gráfico) com **tabela equivalente** acessível (`<details>` "Ver dados em tabela"); legenda sempre visível com padrão e forma; nenhum gráfico depende só da cor. Sem dependência npm: SVG escrito à mão, escalas lineares simples.
- Se as telas dos Planos 5 a 7 já usarem uma biblioteca de gráficos, **manter a biblioteca** e aplicar só a paleta, os padrões, as formas e a tabela equivalente (registrar `Ruling:`); não reescrever gráfico que já atende.

- [ ] **Passo 1: Testes que falham:**
  - `seisCoresComContraste3NaSuperficie` (usa `contraste()` do Plano 8 se já existir; senão a mesma fórmula da WCAG num helper local; ≥ 3:1 contra `--surface` nos três temas).
  - `serieTemCorPadraoEForma` (7 séries → a 7ª repete a 1ª nas três dimensões).
  - `graficoTemTabelaEquivalente` e `ariaLabelResumeOGrafico`.
  - `legendaSempreVisivel`.
  - `nenhumGraficoSoPorCor` (para cada tela listada: cada série desenhada tem `data-padrao` e `data-forma`).
- [ ] **Passo 2: Rodar** `pnpm vitest run src/shared/graficos src/screens` → FALHA.
- [ ] **Passo 3: Implementar** e trocar os gráficos das telas.
- [ ] **Passo 4: Rodar** → PASSA; `pnpm build && node scripts/verificar-csp.mjs` (sem estilo inline em HTML).
- [ ] **Passo 5: Commit** `feat(ui): gráficos com paleta categórica acessível, padrão e forma`.

---

### Tarefa 12: Diferenças esperadas e fechamento

**Arquivos:** Criar `docs/analise/diferencas-esperadas-complementos.md`, `docs/DiferencasComplementosDocTest.kt`; Modificar `HANDOFF.md`.

**Interfaces:** o documento lista, por item da auditoria (A4, A5, M1, M2, M3, M4, M5, M7, B4, E5, E12, E13), o que mudou, a prova (classe e método de teste) e a diferença em relação ao app atual (por exemplo: o app atual não tem filtro por SKU Type; a gaveta é nova; o link para o Jira só aparece com a flag ligada).

- [ ] **Passo 1: Teste que falha:** `documentoCitaCadaItemEProva` (cada código de item aparece; cada `Classe.metodo` citado existe em `src/test`, como no `EquivalenciaDocTest` do Plano 8).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DiferencasComplementosDocTest*'` → FALHA.
- [ ] **Passo 3: Escrever** o documento e atualizar o `HANDOFF.md` (estado do Plano 10, resposta do ponto de parada com data, pendência "reconciliação completa para preencher os quatro campos novos nas linhas antigas" quando a flag for ligada).
- [ ] **Passo 4: Rodar** suítes completas (backend, frontend, `pnpm build`, `verificar-csp.mjs`, `test-guard.sh`, varredura do Plano 9) → PASSA. Uma rodada de captura (1440 e 390 px, tema Claro) de Abertas com filtro de campo, da gaveta e de uma tabela virtual, para o progresso.
- [ ] **Passo 5: Commit** `docs: diferenças esperadas dos complementos de telas e filtros`.

---

## Autoavaliação

**Cobertura (pedido e auditoria):**

| Item | Tarefa |
|---|---|
| A5 / spec 6.1: filtros e agrupamentos por Checklist Type, SKU Type e demais campos | T1, T3, T4 |
| A4: opções de filtro (squads, pessoas, tipos, OS) pelo escopo visível | T2, T4 |
| M1 / plano visual 5.7 e 6.4: gaveta universal com link para o Jira da configuração | T5, T6 |
| Plano visual 5.6: detalhe em nova aba | T6 |
| M5 / plano visual 7: paginação virtual | T7 |
| M5 / plano visual 5.8: estados de tela (carregando, vazio, erro, 403, sessão, incompleto) | T8 |
| M7 (código): mensagens 400 e avisos por código, datas e números por idioma | T9 |
| M2: componente de gráfico e paleta categórica acessível | T11 |
| M3, M4, B4, E5 (ajustes nos Planos 5 e 7) | T10 |
| E12 (import do `DrillDrawer`), E13 (parser de filtros duplicado: a Auditoria passa a usar `FiltrosDeCard` na T3, se ainda tiver parser próprio) | T6, T3 |

**Consistência de tipos:** `camposDicionario` (T1) é o que `casaCampos` (T3), `OpcoesDeFiltroDto.campos` (T2) e `TarefaDto.campos` (T5) leem; `CampoDeFiltro.slug` (T1) é o parâmetro `campo.<slug>` (T3, T4); `ErroDto` vem do Plano 9; `TempoStatusDto` (T5) é reaproveitado no NE-09 (T10); `ContagemGrupoDto` (T3) é reaproveitado no NE-09 (T10).

**Fora deste plano:** trava do DPO no histórico da gaveta (Plano 11); guia do painel em inglês e identidade visual final (Plano 15); cards da instância Sidia/CAT (Plano 14, depois de decisão).

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Coluna `campos_dicionario` (JSON em `TEXT`)** em vez de uma coluna por campo: um só formato para os seis campos e para os que vierem, sem migração por campo.
2. **Preenchimento retroativo a partir do `raw`**, só banco local; as linhas antigas sem os quatro campos novos se completam na próxima reconciliação com a flag ligada pelo usuário.
3. **Link para o Jira só com a flag `true`** e montado de `JIRA_<INSTANCIA>_URL`, sem credenciais: com a flag `false`, abrir o link seria acesso ao Jira.
4. **Issue fora da visão → 404** na gaveta (não confirma a existência da chave).
5. **Virtualização e gráficos sem dependência nova**; se os Planos 5 a 7 já adotaram uma biblioteca de gráficos, ela fica.
6. **Paleta Okabe-Ito** de 6 cores, sempre com padrão e forma.
7. **VG-02 e VG-03 voltam à Central** como KPIs com drill, salvo resposta contrária do usuário (ponto de parada).
