# Fase 7: Checklist × Review, Integridade, Distribuição e Rebalanceamento, Governança e telas de início — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Portar as telas restantes do app atual com as decisões da spec: Checklist × Review (pares, alertas, aprendizados), Integridade (12 sinais, só gerente e sempre auditada), Distribuição e Rebalanceamento (modo sombra, decisões gravadas só no painel), Governança (Qualidade dos dados, registro de acesso, decisões, configuração em leitura) e as telas de início por papel (Central de atenção, Visão do squad e Meu painel), que agregam os cards das Fases 4 a 6.

**Arquitetura (Architecture):** Tudo é calculado na leitura sobre o `IssueSnapshot` (Plano 5), com detectores e calculadores puros e regras em YAML versionado. Checklist × Review parte dos pares de `TaskReview.pares(issues, vinculos)` do Plano 4 (pelo adaptador `toAuditIssue`), em que os `vinculos` (`ParDeChaves`) vêm já resolvidos pelo link "Blocks / is blocked by" (`ParesLidos` do Plano 4, a partir de `issue_link`, 4A Tarefa 12; nunca pelo `parentKey`), e filtra as tasks pelos tipos de checklist, para que Divergência e Checklist × Review usem o mesmo pareamento. A Integridade segue o desenho da Auditoria (detectores `fun interface`, sinais com gravidade, rota restrita e `access_log`). Distribuição e Rebalanceamento são motores puros sobre `Demanda`/`Capacidade` (Plano 6); só as **decisões** (aprovar, trocar, rejeitar) são gravadas numa tabela local, nunca no Jira. As telas de início compõem os calculadores existentes, e cada item leva ao card de destino com o mesmo número.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kaml (YAML estrito), SQLite embutido + Flyway (testes com SQLite real em arquivo temporário); React + TypeScript (Vite, Vitest, Testing Library, TanStack Query).

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 2 "Antiobjetivos", 4.2 "Central de atenção" e "Heurísticas", 4.3, 4.6 "Subtask Review", 7.2 (12 sinais de integridade), 7.3, 8 item 7, 10). Inventário: `docs/analise/relatorio-cards-app-atual.md` (seções 4.1, 4.2, 4.12, 4.13, 4.17, 4.18; achados 2, 11, 12). Visual: `docs/design/plano-visual-frontend.md` (seções 4, 6.1, 6.5).

Dividido em dois blocos, cada um um PR que deixa a `main` verde: **7a** (Tarefas 1 a 8: Checklist × Review e Integridade) e **7b** (Tarefas 9 a 18: Distribuição, Rebalanceamento, Governança e telas de início).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere estes nomes na `main` e, se mudaram, atualiza só os nomes deste plano e registra no `progress.md`.

| Nome | Origem |
|---|---|
| `IssueSnapshot`, `WorkStore`, `toAuditIssue`, `Fluxo`, `CardDeLeitura`, `rotaDeCard`, `FiltrosDeCard`, `escopoDe`, kit de tela | Plano 5, Tarefas 2 a 4 |
| `MetasCalculator`, `NoPrazo.classificar`, `RiscoCalculator`, `NegligenciadasCalculator`, `AbertasCalculator`, `PrevisaoCalculator`, `MonteCarlo` | Plano 5 |
| `MetasCalculator.calcular(candidatasNoPrazo, resolvidasNoPeriodo, de, ate, g, visiveis)` e `WorkStore.candidatasNoPrazo` (regra única de "no prazo"; a Central lê as candidatas por vencimento, nunca as resolvidas no período) | Plano 5, T7 |
| `Equipes`, `Ausencias`, `RegrasDePlanejamento`, `HorasDaIssue`, `Capacidade`, `Demanda`, `Ocupacao`, `Jain`, `Agenda`, `EsforcoCalculator`, `PosseCalculator` | Plano 6 |
| `TaskReview.pares(issues, vinculos)/resumo`, `ParTaskReview`, `AuditStore.paresTaskReview` / `ParesLidos`, `AuditTimeline`, `AuditCalculator`, `Sinal`, `Detector`, `RegrasDeAuditoria.tiposReview/statusCancelamento`, `LabelsDeAtraso` | Plano 4 |
| `ParDeChaves`, `ParesTaskReview.resolver`, `VinculosConfig`, `IssueLinkStore.ativos` (pares Task × Review pelo link `Blocks`, nas duas direções) | Plano 4A, Tarefa 12 |
| `executarAuditado` (`cards/RotaAuditada.kt`), `requireRole`, `AccessLog`, `AvisoDto`, `DrillRow`, `ORDEM_NATURAL_DE_CHAVES` | Planos 3 e 4 |
| `SyncStatusDto`/`GET /api/sync/status` (tela Sincronização) | Plano 4A, Tarefa 8 |
| `StatusMap.ordem/ehResolucaoNegativa` | Plano 5, Tarefa 1 |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- Nenhuma chamada ao Jira; fixtures e builders em memória. `JIRA_LIVE_ENABLED=false`. **Escrita no Jira continua desligada:** Distribuição e Rebalanceamento só sugerem; as decisões ficam numa tabela do painel.
- **Integridade só para GERENTE** (COORDENADOR, SQUAD_LEADER e USUARIO → 403, também na Central) e **toda consulta é registrada** (200, 400, 403, 404); falha ao registrar → 503 sem dados. Aviso fixo "Indício, não prova" e "Validar a base legal com o DPO antes do uso real" em toda resposta e tela de Integridade.
- **Sem ranking de pessoas:** tabelas por pessoa em ordem alfabética; nenhuma lista ordenada por pontos, score, carga ou atraso; a Distribuição mostra a sugestão e os fatores de cada candidato em ordem alfabética.
- Heurísticas (limiares da Integridade, pesos da distribuição, amostras do Checklist × Review) em YAML versionado; a versão sai em toda resposta.
- Central de atenção: "Sem atualização" e "Paradas no status" são **dois itens separados**; cada item tem o mesmo número do card de destino e abre a mesma lista.
- Todo número clicável abre as tasks que o compõem, com o mesmo tamanho; status sem mapeamento → 200 com aviso; agregados só do visível.
- Fuso America/Manaus; dias úteis por padrão. Cor nunca é o único sinal; pt-BR e inglês; três temas.
- Migração na próxima livre (hoje só existe a V1, então a V2; nunca editar a V1). Sem Docker: banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`); a suíte só exige JDK 21. Commits Conventional Commits pt-BR (`feat(checklist): ...`, `feat(integridade): ...`, `feat(distribuicao): ...`, `feat(governanca): ...`, `feat(inicio): ...`); comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **COORDENADOR chegando à Integridade por outro caminho** (Central, drill, CSV, URL direta): 403 em todos, e a Central não mostra o item "pessoas com indício alto" → `integridadeSoGerenteEmTodasAsRotas` (T7) e `centralDoCoordenadorSemItemDeIntegridade` (T15).
2. **Fechamento às 22h–24h de Manaus** do dia do vencimento (madrugada do dia seguinte em UTC) e **lote de 6 fechamentos** cruzando a meia-noite → `fechadaNoLimitePeloFusoDeManaus` e `loteCruzandoMeiaNoite` (T4).
3. **Decisão de distribuição repetida ou concorrente** (dois cliques, duas abas): grava uma vez; a segunda resposta é 409 → `decisaoDuplicada409` (T11).
4. **Squad Leader decidindo sobre issue de outro squad** ou trocando para pessoa fora do visível → 403, e a tentativa fica no `access_log` → `squadLeaderSoNoProprioSquad` (T11).
5. **Item da Central que diverge do card de destino** (regras diferentes): um teste compara cada número da Central com o card → `itensBatemComAsTelasDeDestino` (T15).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  checklist/RegrasChecklistReview.kt, ParesChecklistReview.kt, EstatisticasChecklistReview.kt
  cards/checklist/{ChecklistReviewDto.kt,CardChecklistReview.kt}
  integridade/RegrasDeIntegridade.kt, SinaisDeIntegridade.kt, DetectoresDeTempo.kt, DetectoresDePrazoEFluxo.kt, NiveisPorPessoa.kt
  integridade/IntegridadeRoutes.kt, IntegridadeDto.kt
  distribuicao/RegrasDeDistribuicao.kt, Distribuidor.kt, Rebalanceador.kt, DecisaoStore.kt, DistribuicaoRoutes.kt, DistribuicaoDto.kt
  governanca/QualidadeDosDados.kt, RegistroDeAcessoRoutes.kt, ConfiguracaoRoutes.kt, GovernancaDto.kt
  inicio/CentralCalculator.kt, InicioRoutes.kt, InicioDto.kt
app/backend/src/main/resources/db/migration/V{n}__decisao_distribuicao.sql
app/config/checklist_review.exemplo.yaml, integridade.exemplo.yaml, distribuicao.exemplo.yaml, app/config/cards/*.card.yaml
app/frontend/src/screens/{ChecklistReview,Integridade,Distribuicao,Rebalanceamento,Qualidade,RegistroDeAcesso,Decisoes,Configuracao,Central,Squad,MeuPainel}/
docs/analise/diferencas-esperadas-checklist-integridade-governanca.md
```

---

# Bloco 7a — Checklist × Review e Integridade

**Cenário CR-1** (relógio `2026-10-30T12:00:00Z`; papéis do TRB-1; tipos pai `Checklist`, review `Checklist Review`):

| Par | Pai (resp., vence, resolvido) | Review (resp., vence, situação) | Alertas esperados |
|---|---|---|---|
| P1 | TG-401 (`ana`, 10-10, 10-09T12Z) | TG-402 (`bruno`, 10-10, resolvida 10-08T12Z, ativa 8 h) | nenhum |
| P2 | TG-403 (`ana`, 10-15, 10-14T12Z) | TG-404 (`ana`, 10-17, resolvida 10-16T12Z) | `MESMA_PESSOA`, `REVIEW_VENCE_DEPOIS`, `PAI_FECHADO_ANTES` |
| P3 | TG-405 (`bruno`, 10-20, reaberto 10-21T12Z) | TG-406 (`carla`, 10-20, resolvida, ativa 0,25 h) | `REVIEW_CURTA_COM_PAI_REABERTO` |
| P4 | TG-407 (`carla`, 10-28, aberto) | TG-408 (`bruno`, 10-25, aberta) | `REVIEW_VENCIDA` |
| — | TG-409 (`ana`, sem review) | — | conta em "Checklists sem review" |

### Tarefa 1: Pares Checklist × Review e alertas por par

**Arquivos:** Criar `checklist/RegrasChecklistReview.kt`, `checklist/ParesChecklistReview.kt`, `app/config/checklist_review.exemplo.yaml`; Modificar `AppContext.kt` (`regrasChecklistReview`); Teste `checklist/RegrasChecklistReviewTest.kt`, `checklist/ParesChecklistReviewTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class RegrasChecklistReview(val versao: Int, val tiposPai: Set<String>, val amostraMinima: Int, val limiteVenceDepois: Double,
      val limiteMesmaPessoa: Double, val limiteParesComAlerta: Double, val reviewCurtaHoras: Double, val faixasDuracaoHoras: List<Double>) {
      companion object { fun fromYaml(path: Path): RegrasChecklistReview }
  }
  enum class AlertaDoPar { REVIEW_VENCE_DEPOIS, PAI_FECHADO_ANTES, MESMA_PESSOA, REVIEW_CURTA_COM_PAI_REABERTO, REVIEW_VENCIDA, REVIEW_ATRASADA }
  data class ParCR(val pai: IssueSnapshot, val review: IssueSnapshot, val alertas: Set<AlertaDoPar>, val horasAtivasReview: Double?, val paiReaberto: Boolean)
  class ParesChecklistReview(regras: RegrasChecklistReview, tiposReview: Set<String>, map: StatusMap, cal: BusinessCalendar) {
      fun pares(issues: List<IssueSnapshot>, vinculos: List<ParDeChaves>, agora: Instant): List<ParCR>   // vinculos = ParesLidos.vinculos (Plano 4), já resolvidos pelo link Blocks
      fun semReview(issues: List<IssueSnapshot>): List<IssueSnapshot>
  }
  ```
  `checklist_review.exemplo.yaml`: `versao: 1`, `tiposPai: [Checklist, "Checklist TG"]`, `amostraMinima: 5`, `limiteVenceDepois: 0.10`, `limiteMesmaPessoa: 0.10`, `limiteParesComAlerta: 0.30`, `reviewCurtaHoras: 0.5`, `faixasDuracaoHoras: [1, 4, 12]`. Os tipos de review vêm de `ctx.regrasAuditoria.tiposReview` (fonte única). Pares = `TaskReview.pares(issues.map { it.toAuditIssue() }, vinculos)` (Plano 4; `vinculos` = `ParesLidos.vinculos`, obtidos por `AuditStore.paresTaskReview`, pelo link "Blocks / is blocked by" do 4A, Tarefa 12, nas duas direções), mantendo só os cuja task (o "pai" do par, o lado Checklist) tem tipo em `tiposPai`; o campo `parent`/`parentKey` não define o par. `semReview` = tasks em `tiposPai` sem nenhum vínculo resolvido. `ParesLidos.ambiguas` e `semVinculos` viram os avisos `VINCULO_AMBIGUO` e `SEM_VINCULOS` da resposta. Horas ativas da review = soma dos intervalos em ACTIVE (horas corridas). `REVIEW_ATRASADA` = review resolvida depois do próprio vencimento.
- [ ] **Passo 1: Testes que falham** (CR-1): `quatroParesESemReview` (4 pares; `semReview=[TG-409]`); `alertasDoCenario` (P1 nenhum; P2 os três; P3 `REVIEW_CURTA_COM_PAI_REABERTO`; P4 `REVIEW_VENCIDA`); `reviewSemPaiNaListaIgnorada`; `paiDeTipoForaDaListaIgnorado`; `parPeloLinkBlocksNasDuasDirecoes` (vínculos `ParDeChaves` em memória, com a Review bloqueando a task e a task bloqueando a Review, dão o mesmo `ParCR`); `parentKeyNaoDefinePar` (review com `parentKey` apontando para outra task, sem vínculo, não gera par); `tiposReviewDaAuditoria` (trocar `tiposReview` no YAML da auditoria muda os pares); `regrasEstritas`.
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*RegrasChecklistReviewTest*' --tests '*ParesChecklistReviewTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(checklist): pares Checklist × Review e alertas por par`.

---

### Tarefa 2: Estatísticas, aprendizados e labels do Checklist × Review

**Arquivos:** Criar `checklist/EstatisticasChecklistReview.kt`; Teste `checklist/EstatisticasChecklistReviewTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class LiftDeLabel(val label: String, val n: Int, val taxaReabertura: Double, val lift: Double?, val nBaixo: Boolean)
  data class FaixaDuracao(val faixa: String, val n: Int, val taxaReabertura: Double?)       // "<1h", "1-4h", "4-12h", ">=12h"
  data class ResumoCR(val pares: Int, val semReview: Int, val cicloReviewP50: Double?, val razaoReviewPaiP50: Double?,
      val criadaAposPaiHorasP50: Double?, val venceDepoisPct: Double?, val mesmaPessoaPct: Double?, val comAlertaPct: Double?,
      val duracaoXReabertura: List<FaixaDuracao>, val labels: List<LiftDeLabel>, val diferencasDeLabels: List<Pair<String, Int>>,
      val atrasosEmCadeia: Int, val aprendizados: List<String>)            // aprendizados = códigos de frase + parâmetros
  object EstatisticasChecklistReview { fun resumir(pares: List<ParCR>, semReview: Int, regras: RegrasChecklistReview, cal: BusinessCalendar, map: StatusMap): ResumoCR }
  ```
  Percentuais sobre o número de pares. Lift = taxa de reabertura dos pais com a label ÷ taxa média; `nBaixo` quando `n < amostraMinima` (o lift sai, mas marcado). **CR-12 "Quem atrasa quem" vira `atrasosEmCadeia`**: pares em que a review atrasada adiou o fechamento do pai (contagem e drill), sem pares de pessoas. Aprendizados só para faixas e labels com `n ≥ amostraMinima` (códigos traduzidos no frontend, ex.: `REVIEW_CURTA_REABRE_MAIS`).
- [ ] **Passo 1: Testes que falham** (CR-1): `percentuaisDoCenario` (`venceDepoisPct=0.25` ●, `mesmaPessoaPct=0.25` ●, `comAlertaPct=0.75` ▲); `cicloReviewP50` (ciclos das reviews concluídas `[1.0, 2.0, 3.0]` → `2.0`); `liftComAmostraPequena` (label em 2 pais → `nBaixo=true`); `lift` (10 pais, label em 5 com 2 reabertos, média 0,2 → `lift=2.0`, `nBaixo=false`); `duracaoXReabertura` (review de 0,25 h com pai reaberto conta em `<1h`); `aprendizadoSoComAmostra`; `atrasosEmCadeiaSemPessoas` (P2 conta 1; o resultado não tem campo de pessoa).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*EstatisticasChecklistReviewTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(checklist): estatísticas, lift de labels e aprendizados com amostra mínima`.

---

### Tarefa 3: Checklist × Review (API e tela)

**Arquivos:** Criar `cards/checklist/{ChecklistReviewDto.kt,CardChecklistReview.kt}`, `app/config/cards/checklist-review.card.yaml`, `app/frontend/src/screens/ChecklistReview/{ChecklistReview.tsx,api.ts,ChecklistReview.test.tsx}`; Modificar `Application.kt`, `App.tsx` (rota `checklistReview`), `i18n/pt.json`, `i18n/en.json`; Teste `cards/ChecklistReviewRoutesTest.kt`.

**Interfaces:**
- Produz `@Serializable data class ChecklistReviewDto(val versaoRegras: String, val resumo: ResumoCRDto, val pares: List<ParCRDto>, val avisos: List<AvisoDto>)` com `ParCRDto(pai, review, responsavelPai, responsavelReview, vencePai, venceReview, alertas: List<String>)` e drill `kind = pares | semReview | alerta:<CODIGO> | comAlerta | label:<nome> | faixa:<faixa> | cadeia`. A rota lê os vínculos por `AuditStore.paresTaskReview` (`ParesLidos`) e repassa os avisos `VINCULO_AMBIGUO`/`SEM_VINCULOS`. Filtros aplicados ao **pai** (relatório 4.12); visibilidade pelo responsável do pai **ou** da review. Tela densa: KPIs (pares, sem review, com alerta ▲ acima de 30%), tabela de pares com comparação lado a lado na gaveta, labels com "n baixo" em texto, aprendizados.
- [ ] **Passo 1: Testes que falham:** `drillTemOMesmoTamanhoDoCard` (`pares=4`, `semReview=1`, `alerta:MESMA_PESSOA=1`, `comAlerta=3`); `filtroAplicadoAoPai`; `usuarioVeParDoQualParticipa` (`carla` vê P3 e P4); `versaoNaResposta`; frontend `alertaComSimboloETexto`, `nBaixoEmTexto`, `gavetaLadoALado`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ChecklistReviewRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens/ChecklistReview` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e `pnpm build`. **Passo 5: Commit** `feat(checklist): card e tela Checklist × Review`.

---

**Cenário INT-1** (relógio `2026-10-30T12:00:00Z`; papéis do TRB-1; vencimento das issues em 15/10 salvo indicação). Os 12 sinais do relatório (seção 4.13), um caso por sinal, com os limites de cada regra (ver Tarefas 4 e 5).

### Tarefa 4: Regras e sinais de tempo da Integridade

**Arquivos:** Criar `integridade/RegrasDeIntegridade.kt`, `integridade/SinaisDeIntegridade.kt`, `integridade/DetectoresDeTempo.kt`, `app/config/integridade.exemplo.yaml`; Modificar `AppContext.kt` (`regrasIntegridade`); Teste `integridade/RegrasDeIntegridadeTest.kt`, `integridade/DetectoresDeTempoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class SinalIntegridade { RESGATE_POR_PRORROGACAO, TROCA_ANTES_DE_FECHAR, CRIADA_E_FECHADA_RAPIDO, REABRE_E_FECHA_RAPIDO,
      FECHAMENTO_EM_LOTE, VENCIMENTO_PRORROGADO, VENCIMENTO_TARDIO, PULOU_O_FLUXO, ESTIMATIVA_AUMENTADA,
      LABEL_CRITICIDADE_REMOVIDA, FECHADA_POR_OUTRO, FECHADA_NO_LIMITE }
  data class RegrasDeIntegridade(val versao: Int, val gravidades: Map<SinalIntegridade, Int>, val ativos: Set<SinalIntegridade>,
      val trocaAntesDeFecharHoras: Double, val criadaEFechadaMinutos: Int, val reabreEFechaHoras: Double, val loteQuantidade: Int,
      val loteMinutos: Int, val fracaoVidaTardia: Double, val estimativaAumento: Double, val labelsCriticidade: List<String>,
      val labelRemovidaDias: Int, val limiteInicioHora: Int, val pontosAlto: Int, val multiploAlto: Double, val gravesAlto: Int,
      val pontosMedio: Int, val multiploMedio: Double, val concluidasMinimas: Int, val pisoMediana: Double) {
      companion object { fun fromYaml(path: Path): RegrasDeIntegridade }
  }
  data class EvidenciaIntegridade(val sinal: SinalIntegridade, val gravidade: Int, val issueKey: String, val pessoa: String?, val at: Instant, val detalhe: Map<String, String>)
  fun interface DetectorIntegridade { fun detectar(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade, ctx: ContextoIntegridade): List<EvidenciaIntegridade> }
  data class ContextoIntegridade(val map: StatusMap, val zone: ZoneId, val cal: BusinessCalendar)
  object DetectoresDeTempo { val todos: List<DetectorIntegridade> }   // TROCA_ANTES_DE_FECHAR, CRIADA_E_FECHADA_RAPIDO, REABRE_E_FECHA_RAPIDO, FECHAMENTO_EM_LOTE, FECHADA_POR_OUTRO, FECHADA_NO_LIMITE
  ```
  `integridade.exemplo.yaml`: `versao: 1`; gravidades 3 para os cinco primeiros sinais do enum, 2 para os cinco seguintes, 1 para `FECHADA_POR_OUTRO` e `FECHADA_NO_LIMITE`; `ativos` = todos menos `ESTIMATIVA_AUMENTADA`; `trocaAntesDeFecharHoras: 2`, `criadaEFechadaMinutos: 15`, `reabreEFechaHoras: 2`, `loteQuantidade: 6`, `loteMinutos: 10`, `fracaoVidaTardia: 0.7`, `estimativaAumento: 0.3`, `labelsCriticidade: ["CRITICAL", "BLOCKER"]` (vocabulário a confirmar), `labelRemovidaDias: 2`, `limiteInicioHora: 22`, `pontosAlto: 12`, `multiploAlto: 3.0`, `gravesAlto: 3`, `pontosMedio: 6`, `multiploMedio: 2.0`, `concluidasMinimas: 8`, `pisoMediana: 0.05`. **Pessoa do sinal** = autor do evento que o dispara (lote, troca, fechamento); sem autor, o responsável no instante.
- [ ] **Passo 1: Testes que falham** (INT-1): `trocaAntesDeFechar` (troca 1 h 59 min antes do fechamento → sinal; 2 h 01 min → não); `criadaEFechadaRapido` (14 min → sinal; 15 min → não); `reabreEFechaRapido` (reaberta 10:00, fechada 11:59 → sinal); `fechamentoEmLote` (`bruno` fecha 6 issues entre 14:00 e 14:09 → 6 evidências; 5 → nenhuma); `loteCruzandoMeiaNoite` (6 fechamentos de 23:55 a 00:04 de Manaus → sinal); `fechadaPorOutro` (autor da transição para DONE ≠ responsável no instante); `fechadaNoLimitePeloFusoDeManaus` (vencimento 15/10, fechada `2026-10-16T02:30Z` = 15/10 22:30 Manaus → sinal; `2026-10-16T01:59Z` = 21:59 → não); `regrasEstritas` (gravidade fora de 1..3, sinal sem gravidade, chave desconhecida); `sinalInativoNaoGera`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegrasDeIntegridadeTest*' --tests '*DetectoresDeTempoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(integridade): regras em YAML e sinais de tempo`.

---

### Tarefa 5: Sinais de prazo, fluxo, estimativa e criticidade

**Arquivos:** Criar `integridade/DetectoresDePrazoEFluxo.kt`; Teste `integridade/DetectoresDePrazoEFluxoTest.kt`.

**Interfaces:**
- Produz `object DetectoresDePrazoEFluxo { val todos: List<DetectorIntegridade> }` com `RESGATE_POR_PRORROGACAO` (vencimento estendido **depois** de vencido e a task fechada até o novo vencimento), `VENCIMENTO_PRORROGADO` (qualquer extensão; não soma com o resgate no mesmo evento), `VENCIMENTO_TARDIO` (1º vencimento definido depois de 70% da vida criação → resolução), `PULOU_O_FLUXO` (chegou a `DONE` sem nunca passar por categoria ACTIVE; sem `ordem` configurada usa só a categoria), `ESTIMATIVA_AUMENTADA` (+30% em evento de `timeoriginalestimate`; o valor só existe se uma **decisão futura do usuário** ampliar a lista de campos com valor em `change_event` (hoje só `status`, `assignee`, `resolution`, `duedate`, `labels` e `OS Version`; a decisão 7 do Plano 4 deixou estimativa e worklog fora); sem eventos com valor → aviso `SINAL_SEM_DADO` e nenhuma evidência), `LABEL_CRITICIDADE_REMOVIDA` (label de `labelsCriticidade` removida a ≤ 2 dias do fechamento).
- [ ] **Passo 1: Testes que falham** (INT-1): `resgatePorProrrogacao` (vencida em 15/10, prorrogada em 17/10 para 20/10, fechada 19/10 → `RESGATE_POR_PRORROGACAO` e não `VENCIMENTO_PRORROGADO`); `prorrogadoAntesDeVencer` (prorrogada em 10/10 → só `VENCIMENTO_PRORROGADO`); `antecipacaoNaoConta`; `vencimentoTardio` (vida de 10 dias, vencimento definido no dia 8 → sinal; no dia 6 → não); `pulouOFluxo` (Open → Closed); `estimativaSemDadoViraAviso`; `estimativaComDado` (evento `timeoriginalestimate` 36000 → 50400 → sinal, quando ativo no YAML de teste); `criticidadeRemovidaPertoDoFim` (removida 1 dia antes → sinal; 3 dias → não); `labelsSemDiferenciarMaiusculas`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DetectoresDePrazoEFluxoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(integridade): sinais de prazo, fluxo, estimativa e criticidade`.

---

### Tarefa 6: Nível por pessoa e impacto nas metas

**Arquivos:** Criar `integridade/NiveisPorPessoa.kt`; Teste `integridade/NiveisPorPessoaTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class NivelIntegridade { ALTO, MEDIO, NORMAL }
  data class NivelPessoa(val pessoa: String, val pontos: Int, val concluidas: Int, val taxa: Double, val multiplo: Double, val graves: Int, val nivel: NivelIntegridade)
  data class ResumoIntegridade(val evidencias: Int, val issuesAfetadas: Int, val medianaTaxa: Double, val porSinal: Map<SinalIntegridade, Int>,
      val pessoas: List<NivelPessoa>, val impactoMetasPp: Double?)       // pessoas em ordem alfabética
  object NiveisPorPessoa { fun resumir(evidencias: List<EvidenciaIntegridade>, concluidasPorPessoa: Map<String, Int>, regras: RegrasDeIntegridade, metas: MetasDto?): ResumoIntegridade }
  ```
  Taxa = pontos ÷ `max(concluídas, 8)`; mediana das taxas das pessoas com concluídas (piso `0,05`); múltiplo = taxa ÷ mediana. **Alto:** pontos ≥ 12, múltiplo ≥ 3 e graves (gravidade 3) ≥ 3. **Médio:** pontos ≥ 6 e múltiplo ≥ 2. `impactoMetasPp` = (`taxaNoPrazo − taxaOriginal`) × 100 do `MetasDto` (IN-06).
- [ ] **Passo 1: Testes que falham:** `niveisDoCenario` (`ana` 12 pontos, 10 concluídas, 4 graves → taxa `1.2`; `bruno` 6 pontos, 8 concluídas → `0.75`; `carla` 1 ponto, 12 concluídas → `0.083333`; `lider.alfa` 0 ponto, 10 concluídas → `0.0`; mediana `0.416667` → `ana` múltiplo `2.88`, `MEDIO`; `bruno` `1.8`, `NORMAL`); `altoComOsTres` (`ana` com 15 pontos e 4 graves → múltiplo `3.6`, `ALTO`); `altoExigeOsGraves` (15 pontos, múltiplo `3.6`, 2 graves → `MEDIO`); `concluidasMinimas` (2 concluídas usa 8); `pisoDaMediana` (todas 0 → mediana `0.05`); `pessoasAlfabeticas`; `impactoNasMetas` (`0.75` e `0.5` → `25.0`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*NiveisPorPessoaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(integridade): nível por pessoa e impacto nas metas`.

---

### Tarefa 7: Integridade (API só do gerente, sempre auditada, e tela)

**Arquivos:** Criar `integridade/IntegridadeRoutes.kt`, `integridade/IntegridadeDto.kt`, `app/frontend/src/screens/Integridade/{Integridade.tsx,api.ts,Integridade.test.tsx}`; Modificar `Application.kt`, `App.tsx`, `i18n/pt.json`, `i18n/en.json`; Teste `integridade/IntegridadeRoutesTest.kt`.

**Interfaces:**
- Rotas (todas em `requireRole(GERENTE)` com `executarAuditado`):
  ```
  GET /api/integridade?de=&ate=&squad=&pessoa=&sinal=     (padrão 90 dias; máximo 180)
  GET /api/drill/integridade?kind=todas|sinal:<SINAL>|pessoa:<id>|nivel:<ALTO|MEDIO>
  GET /api/integridade/catalogo                           (IN-08: sinais, gravidade, regra e limite, sem dados de pessoas)
  ```
  `IntegridadeDto(aviso: "INDICIO_NAO_PROVA", avisoDpo: "VALIDAR_BASE_LEGAL_COM_DPO", versaoRegras, resumo, evidencias: List<EvidenciaDto>, avisos)`. Tela densa com as duas faixas fixas (`role="note"`), KPIs IN-01..IN-04, evidências por sinal (ordem do catálogo), pessoas alta/média em ordem alfabética, impacto nas metas, tabela de evidências e catálogo; CSV com a linha de filtros.
- [ ] **Passo 1: Testes que falham:** `integridadeSoGerenteEmTodasAsRotas` (COORDENADOR, SQUAD_LEADER e USUARIO → 403 nas 3 rotas; GERENTE → 200); `todaConsultaGravaAccessLog` (200, 400, 403, 404); `falhaAoRegistrar503SemDados`; `avisosFixos`; `drillTemOMesmoTamanhoDoCard` (`sinal:FECHAMENTO_EM_LOTE == porSinal`, `nivel:MEDIO == pessoas MEDIO`); `periodoMaximo180`; `catalogoSemPessoas`; `devModeGerenteVe`; frontend `faixasFixasPtEn`, `pessoasAlfabeticasSemColunaDePontosOrdenavel`, `semPermissaoMostraMensagemDePapel`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*IntegridadeRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens/Integridade` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e `pnpm build`. **Passo 5: Captura de verificação** (uma rodada das telas Checklist × Review e Integridade, 1440 e 390 px, três temas). **Passo 6: Commit** `feat(integridade): API restrita ao gerente, sempre auditada, e tela`.

---

### Tarefa 8: Diferenças esperadas (bloco 7a)

**Arquivos:** Criar `docs/analise/diferencas-esperadas-checklist-integridade-governanca.md` (seção 7a), `checklist/CompatLegadoChecklistTest.kt`, `docs/DiferencasChecklistIntegridadeDocTest.kt`.

- [ ] **Passo 1: Testes que falham:** `alertasDoParComoOLegado` (as seis regras de alerta do relatório 4.12 conferidas em `legacy/backend/jiradash/analytics.py` antes de aceitar); `niveisComoOLegado` (mesmos limiares 12 pontos, 3×, 3 graves; 6 pontos, 2×; `max(concluídas, 8)`; piso 0,05); `documentoCitaOsIds` (CR-01..CR-14, IN-01..IN-08 e os 12 nomes de `SinalIntegridade`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompatLegadoChecklistTest*' --tests '*DiferencasChecklistIntegridadeDocTest*'` → FALHA. **Passo 3: Escrever** a seção 7a: review por tipo `Checklist Review` (e não `summary ~ Review` do bscbi-web), CR-12 reformulado sem pares de pessoas, "n baixo" mantido, texto de ajuda da Integridade igual ao código (achado 12), "fechada no limite" no fuso de Manaus, estimativa +30% sem dado até uma decisão futura do usuário ampliar os campos com valor no changelog, pessoa do sinal = autor do evento. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(integridade): diferenças esperadas do bloco 7a`.

---

# Bloco 7b — Distribuição, Rebalanceamento, Governança e telas de início

### Tarefa 9: Distribuição em modo sombra (motor)

**Arquivos:** Criar `distribuicao/RegrasDeDistribuicao.kt`, `distribuicao/Distribuidor.kt`, `app/config/distribuicao.exemplo.yaml`; Modificar `AppContext.kt` (`regrasDistribuicao`); Teste `distribuicao/RegrasDeDistribuicaoTest.kt`, `distribuicao/DistribuidorTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class RegrasDeDistribuicao(val versao: Int, val pesoWip: Double, val penalidadeWipCheio: Double, val pesoCompetenciaFaltante: Double,
      val bonusEpicoOuComponente: Double, val produtividadeMin: Double, val produtividadeMax: Double, val capacidadeMinimaH: Double,
      val gatilhoRebalanceamento: Double, val maxMovimentos: Int, val histereseRebalanceamentoPp: Double) {
      companion object { fun fromYaml(path: Path): RegrasDeDistribuicao }
  }
  data class Candidato(val pessoa: String, val cargaProjetadaH: Double, val capacidadeH: Double, val wip: Int, val limiteWip: Int,
      val competenciasFaltantes: Int, val jaTrabalhouNoEpicoOuComponente: Boolean, val ultimaAtribuicao: Instant?, val produtividade: Double)
  data class Fatores(val pessoa: String, val carga: Double, val wip: Double, val wipCheio: Double, val competencias: Double, val bonus: Double, val score: Double)
  data class Sugestao(val issueKey: String, val horas: Double, val sugerido: String?, val fatores: List<Fatores>)   // fatores em ordem alfabética
  data class ResultadoDistribuicao(val versaoRegras: String, val sugestoes: List<Sugestao>, val jainAntes: Double?, val jainDepois: Double?)
  class Distribuidor(private val regras: RegrasDeDistribuicao) {
      fun pendentes(abertas: List<IssueSnapshot>, map: StatusMap, roles: RoleDirectory, hoje: LocalDate): List<IssueSnapshot>   // TODO sem responsável ou com um GERENTE
      fun distribuir(itens: List<Pair<IssueSnapshot, Double>>, candidatos: List<Candidato>, competenciasDoItem: (IssueSnapshot) -> Set<String>): ResultadoDistribuicao
  }
  ```
  `distribuicao.exemplo.yaml`: `versao: 1`, `pesoWip: 0.5`, `penalidadeWipCheio: 1.0`, `pesoCompetenciaFaltante: 0.6`, `bonusEpicoOuComponente: 0.15`, `produtividadeMin: 0.85`, `produtividadeMax: 1.15`, `capacidadeMinimaH: 0.5`, `gatilhoRebalanceamento: 1.10`, `maxMovimentos: 10`, `histereseRebalanceamentoPp: 20`. Score = `cargaProjetada × produtividade ÷ capacidade + pesoWip × wip ÷ limite + penalidade se wip ≥ limite + pesoCompetencia × faltantes − bônus`. Elegível: capacidade de 4 semanas > `capacidadeMinimaH`. Menor score vence; empate: quem recebeu há mais tempo, depois ordem alfabética; itens do maior para o menor (horas); cada atribuição atualiza carga e WIP do escolhido antes do próximo item. Produtividade = gasto ÷ estimado das concluídas da pessoa em 16 semanas, limitada a 0,85–1,15 (sem dado: 1,0).
- [ ] **Passo 1: Testes que falham:** `scoreDosCandidatos` (`ana` (16,8 h; 67,2 h; WIP 1/3; 0; não) → `0.416667`; `bruno` (8,0; 32,0; 3/3; 0; sim) → `1.6`; `carla` (0; 44,8; 0/3; 1 faltante; não) → `0.6`; sugerido `ana`); `fatoresEmOrdemAlfabetica` (nunca por score); `empatePorUltimaAtribuicao`; `atualizaCargaEntreItens` (dois itens de 6 h: o segundo vê `ana` com 22,8 h); `inelegivelSemCapacidade` (capacidade 0,4 h → fora); `produtividadeLimitada` (1,5 → 1,15); `jainAntesEDepois`; `pendentesSemResponsavelOuComGerente`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegrasDeDistribuicaoTest*' --tests '*DistribuidorTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(distribuicao): motor de distribuição em modo sombra`.

---

### Tarefa 10: Rebalanceamento (motor)

**Arquivos:** Criar `distribuicao/Rebalanceador.kt`; Teste `distribuicao/RebalanceadorTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class Movimento(val issueKey: String, val de: String, val para: String, val horas: Double, val ocupacaoOrigemAntes: Double,
      val ocupacaoOrigemDepois: Double, val ocupacaoDestinoDepois: Double)
  class Rebalanceador(private val regras: RegrasDeDistribuicao) {
      fun calcular(cargas: Map<String, CargaDaPessoa>, naoIniciadas: Map<String, List<Pair<IssueSnapshot, Double>>>,
                   competencias: Map<String, Set<String>>, competenciasDoItem: (IssueSnapshot) -> Set<String>): List<Movimento>
  }
  ```
  Gatilho: ocupação do horizonte > `gatilhoRebalanceamento`; só issues **não iniciadas**; no máximo `maxMovimentos`; destino precisa ter todas as competências do item; movimento só se a ocupação do destino depois ficar ≥ 20 pp abaixo da ocupação da origem depois (histerese); pessoa `SemCapacidade` nunca é destino.
- [ ] **Passo 1: Testes que falham:** `moveDaSobrecarregada` (origem 150% com item de 10 h não iniciado, destino 40% com a competência → 1 movimento); `naoMoveIniciada`; `respeitaCompetencia`; `histerese` (destino terminaria a 15 pp da origem → nenhum movimento); `maximoDeMovimentos` (20 candidatos → 10); `semCapacidadeNuncaEhDestino`; `abaixoDoGatilhoNaoMove` (110% exatos → nada).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RebalanceadorTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(distribuicao): motor de rebalanceamento com histerese`.

---

### Tarefa 11: Decisões gravadas no painel e API de Distribuição, Rebalanceamento e Decisões

**Arquivos:** Criar `resources/db/migration/V{n}__decisao_distribuicao.sql`, `distribuicao/DecisaoStore.kt`, `distribuicao/DistribuicaoRoutes.kt`, `distribuicao/DistribuicaoDto.kt`; Modificar `Application.kt`; Teste `distribuicao/DecisaoStoreTest.kt`, `distribuicao/DistribuicaoRoutesTest.kt`, `jira/SemEscritaNoJiraTest.kt`.

**Interfaces:**
- Migração (arquivo `V{n}__decisao_distribuicao.sql`, `{n}` = próxima migração livre, hoje V2; nunca editar a V1): `decisao_distribuicao(id INTEGER PRIMARY KEY AUTOINCREMENT, instance TEXT NOT NULL, tipo TEXT NOT NULL CHECK (tipo IN ('DISTRIBUICAO','REBALANCEAMENTO')), issue_key TEXT NOT NULL, sugerido TEXT, escolhido TEXT, decisao TEXT NOT NULL CHECK (decisao IN ('APROVADA','TROCADA','REJEITADA')), motivo TEXT, decidido_por TEXT NOT NULL, decidido_em INTEGER NOT NULL /* ms UTC */, versao_regras TEXT NOT NULL, chave_sugestao TEXT NOT NULL UNIQUE)`; `chave_sugestao` = hash de (instance, tipo, issue_key, sugerido, versão, dia de Manaus).
- Produz:
  ```kotlin
  enum class Decisao { APROVADA, TROCADA, REJEITADA }
  data class RegistroDecisao(val tipo: String, val issueKey: String, val sugerido: String?, val escolhido: String?, val decisao: Decisao,
      val motivo: String?, val decididoPor: String, val decididoEm: Instant, val versaoRegras: String)
  interface DecisaoStore { fun registrar(instance: String, r: RegistroDecisao, chaveSugestao: String): Boolean   // false = já decidida
      fun listar(instance: String, de: Instant, ate: Instant, issueKeys: Set<String>?): List<RegistroDecisao> }
  class SqliteDecisaoStore(private val ds: DataSource) : DecisaoStore
  ```
  Rotas (liderança; Squad Leader só issues e pessoas do próprio squad):
  ```
  GET  /api/distribuicao              (DI-01..DI-04: aguardando, sugestões pendentes, Jain antes → depois, fatores alfabéticos)
  GET  /api/rebalanceamento           (RB-01, RB-02)
  POST /api/distribuicao/decisao      {tipo, issueKey, sugerido, escolhido, decisao, motivo}   (DI-05, RB-03)
  GET  /api/governanca/decisoes?de=&ate=   (AU-01; coordenação e gerente)
  ```
  `TROCADA` exige `motivo` (1 a 500 caracteres, saneado) e `escolhido` visível; `REJEITADA` aceita motivo opcional; decisão repetida (mesma `chave_sugestao`) → 409. Nada vai ao Jira.
- [ ] **Passo 1: Testes que falham:** `registraUmaVez` e `decisaoDuplicada409`; `trocadaExigeMotivo` (sem motivo → 400; 501 caracteres → 400); `squadLeaderSoNoProprioSquad` (issue de outro squad ou `escolhido=carla` → 403 registrado no `access_log`); `usuarioComum403`; `postGravaAccessLogSemOCorpoDoMotivo` (o `filters` guarda só `tipo` e `decisao`); `decisoesListadasPorData` (AU-01, sem agregação por pessoa); `SemEscritaNoJiraTest.nenhumCaminhoEscreveNoJira` (métodos públicos do `JiraClient` = conjunto permitido de leitura; nenhum `HttpMethod.Put`, `Patch` ou `Delete` no pacote `jira`; `HttpMethod.Post` só para `/search`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DecisaoStoreTest*' --tests '*DistribuicaoRoutesTest*' --tests '*SemEscritaNoJiraTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(distribuicao): decisões gravadas no painel e API de distribuição e rebalanceamento`.

---

### Tarefa 12: Telas Distribuição, Rebalanceamento e Decisões

**Arquivos:** Criar `app/frontend/src/screens/{Distribuicao,Rebalanceamento,Decisoes}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx`, `nav.ts` (item `decisoes` em `governanca` para COORDENADOR e GERENTE), `i18n/pt.json`, `i18n/en.json`.

**Interfaces:** Consome a Tarefa 11. Faixa fixa "Modo sombra: nada é alterado no Jira". Cada sugestão mostra o sugerido e uma tabela de fatores dos candidatos em ordem alfabética (sem coluna de posição); botões Aprovar, Trocar (abre campo de motivo obrigatório e lista de pessoas em ordem alfabética), Rejeitar; 409 mostra "já decidida".

- [ ] **Passo 1: Testes que falham:** `faixaModoSombra`; `fatoresAlfabeticosSemPosicao`; `trocarExigeMotivo` (botão desabilitado sem texto); `decisaoDuplicadaMostraMensagem`; `rebalanceamentoMostraAntesEDepois`; `decisoesListaPorData`; `menuDecisoesSoCoordenacaoEGerente`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Distribuicao src/screens/Rebalanceamento src/screens/Decisoes src/nav` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e `pnpm build`. **Passo 5: Commit** `feat(distribuicao): telas de distribuição, rebalanceamento e decisões`.

---

### Tarefa 13: Qualidade dos dados (API e tela)

**Arquivos:** Criar `governanca/QualidadeDosDados.kt`, `governanca/GovernancaDto.kt`, `app/frontend/src/screens/Qualidade/{Qualidade.tsx,api.ts,Qualidade.test.tsx}`; Modificar `pessoas/RegrasDePlanejamento.kt` e `app/config/planejamento.exemplo.yaml` (chave `prioridadePadrao`), `Application.kt`, `App.tsx`, i18n; Teste `governanca/QualidadeDosDadosTest.kt`, `governanca/QualidadeRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class VerificacaoQD { SEM_ESTIMATIVA, SEM_RESPONSAVEL, SEM_VENCIMENTO, SEM_EPICO, EM_ANDAMENTO_SEM_HORAS, SEM_SQUAD, PRIORIDADE_PADRAO,
      DONE_SEM_RESOLUTION, RESOLUTION_SEM_DONE, STATUS_SEM_MAPEAMENTO, CAMPO_INDISPONIVEL }
  data class LinhaQD(val verificacao: VerificacaoQD, val n: Int, val universo: Int, val fracao: Double?, val estado: String)   // ok ≤ 10%, atencao ≤ 30%, problema acima
  object QualidadeDosDados { fun verificar(abertas: List<IssueSnapshot>, recentes: List<IssueSnapshot>, map: StatusMap, roles: RoleDirectory,
      hoje: LocalDate, prioridadePadrao: String, camposDisponiveis: Map<String, Boolean>): List<LinhaQD> }
  ```
  As 7 verificações do app atual sobre as abertas; as novas `DONE_SEM_RESOLUTION` e `RESOLUTION_SEM_DONE` (spec 4.2) sobre as resolvidas ou em `DONE` nos últimos 90 dias; `STATUS_SEM_MAPEAMENTO` com os nomes; `CAMPO_INDISPONIVEL` para campos "a confirmar" sem valor em nenhuma issue (Story Points, Epic Link, Flagged, OS Version). `prioridadePadrao` vem da chave nova `prioridadePadrao: "Medium"` do `planejamento.yaml` (vocabulário a confirmar). Rota `GET /api/governanca/qualidade` e drill (`kind = <VERIFICACAO>`), só GERENTE.
- [ ] **Passo 1: Testes que falham** (TRB-1 do Plano 5): `semResponsavel` (TG-104 → `1` de `4`, `0.25` `atencao`); `doneSemResolution` (TG-116 → `1`); `statusSemMapeamentoComNome`; `campoIndisponivel` (nenhum `Epic Link` → linha `CAMPO_INDISPONIVEL` com o nome); `semSquad` (assignee fora do `papeis.yaml` na data); `faixasDeEstado` (10% → `ok`; 30% → `atencao`; 31% → `problema`); `soGerente` (COORDENADOR → 403); `drillTemOMesmoTamanhoDoCard`; frontend `estadoComSimboloETexto`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*QualidadeDosDadosTest*' --tests '*QualidadeRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens/Qualidade` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(governanca): Qualidade dos dados com divergência done × resolution`.

---

### Tarefa 14: Registro de acesso e Configuração em leitura (API e telas)

**Arquivos:** Criar `governanca/RegistroDeAcessoRoutes.kt`, `governanca/ConfiguracaoRoutes.kt`, `app/frontend/src/screens/{RegistroDeAcesso,Configuracao}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `db/AccessLog.kt` (`listar`), `Application.kt`, `App.tsx`, `nav.ts` (item `registroAcesso` em `governanca` só GERENTE), i18n; Teste `governanca/RegistroDeAcessoRoutesTest.kt`, `governanca/ConfiguracaoRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class LinhaAcesso(val userId: String, val route: String, val filters: String?, val at: Instant)
  fun AccessLog.listar(de: Instant, ate: Instant, usuario: String?, rotaPrefixo: String?, limite: Int, depoisDe: Long?): List<Pair<Long, LinhaAcesso>>
  @Serializable data class ConfiguracaoDto(val devMode: Boolean, val liveEnabled: Boolean, val modoSync: String, val arquivos: List<ArquivoConfigDto>,
      val pessoas: Int, val squads: Int)
  @Serializable data class ArquivoConfigDto(val nome: String, val versao: Int?, val sha256: String, val origem: String /* real | exemplo */)
  ```
  `GET /api/governanca/acessos?de=&ate=&usuario=&rota=&depoisDe=` (AU-02; só GERENTE; período ≤ 90 dias; página de 200; a própria consulta é registrada). `GET /api/governanca/configuracao` (só GERENTE): nome, versão, hash e origem de cada YAML carregado (`status_map`, `calendario`, `labels_atraso`, `auditoria`, `risco`, `previsao`, `metas`, `planejamento`, `esforco`, `checklist_review`, `integridade`, `distribuicao`, `jira_campos`, fichas), contagens de pessoas e squads, **sem nomes, usernames, hosts ou tokens**.
- [ ] **Passo 1: Testes que falham:** `acessosSoGerente`; `consultaAoRegistroTambemERegistrada`; `paginacaoPorDepoisDe`; `periodoMaximo90`; `configuracaoSemDadosPessoais` (o JSON não contém nenhum id do `papeis` de teste, nem `https://`, nem `token`); `origemExemploEmDev`; `hashMudaQuandoOArquivoMuda`; frontend `registroFiltraPorUsuario`, `configuracaoMostraVersoes`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegistroDeAcessoRoutesTest*' --tests '*ConfiguracaoRoutesTest*'` e `cd app/frontend && pnpm vitest run src/screens/RegistroDeAcesso src/screens/Configuracao` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(governanca): registro de acesso e configuração em leitura`.

---

### Tarefa 15: Central de atenção (API)

**Arquivos:** Criar `inicio/CentralCalculator.kt`, `inicio/InicioDto.kt`, `inicio/InicioRoutes.kt`; Modificar `Application.kt`; Teste `inicio/CentralCalculatorTest.kt`, `inicio/CentralRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class ItemCentral { VENCIDAS, RISCO_ALTO, ACIMA_DA_CAPACIDADE, ATRASOS_PREVISTOS, SEM_ATUALIZACAO, PARADAS_NO_STATUS, SEM_RESPONSAVEL,
      BLOQUEADAS, AUSENCIAS_14D, COM_FOLGA, META_DE_PRAZO, PARES_CR_COM_ALERTA, TASK_REVIEW_DIVERGENTE, ALTERACOES_SEM_AUTORIZACAO,
      INDICIO_ALTO, SEM_ESTIMATIVA }
  @Serializable data class ItemCentralDto(val item: String, val valor: String, val n: Int?, val estado: String, val rota: String, val drill: String)
  @Serializable data class CentralDto(val kpis: List<ItemCentralDto>, val itens: List<ItemCentralDto>, val entregasPorSemana: List<SemanaDto>,
      val backlogTermina: QuandoDto, val squads: List<CartaoSquadDto>, val avisos: List<AvisoDto>)
  @Serializable data class CartaoSquadDto(val squad: String, val ocupacaoMedia: OcupacaoDto, val jain: Double?)   // VG-12; ordem alfabética
  class CentralCalculator(fontes: FontesDaCentral) { fun calcular(e: EntradaDeCard): CentralDto }
  interface FontesDaCentral   // um método por card de origem; produção = calculadores das Fases 4 a 7
  ```
  `GET /api/central` (COORDENADOR e GERENTE). KPIs: abertas, vencidas, risco alto, no prazo e no prazo ajustado (90 dias). Itens VG-09 com `SEM_ATUALIZACAO` e `PARADAS_NO_STATUS` **separados**; `INDICIO_ALTO` só para GERENTE (para o coordenador o item não existe no DTO); `drill` = rota e `kind` do card de destino. VG-10 entregas por semana (12), VG-11 Monte Carlo do backlog, VG-12 cartão por squad (ocupação média e Jain do squad; verde ≤ 85%, laranja ≤ 110%, vermelho acima).
- [ ] **Passo 1: Testes que falham:** `itensBatemComAsTelasDeDestino` (com TRB-1 e CAP-1: cada `n` == contagem do card de destino: `VENCIDAS`/Negligenciadas, `RISCO_ALTO`/Riscos, `ACIMA_DA_CAPACIDADE`/Carga, `SEM_ATUALIZACAO` e `PARADAS_NO_STATUS`/Negligenciadas, `TASK_REVIEW_DIVERGENTE`/Divergência...); `semAtualizacaoEParadasSeparadas` (TG-101 conta nas duas, sem somar num item só); `centralDoCoordenadorSemItemDeIntegridade`; `centralDoGerenteComIndicioAlto` (e a consulta grava `access_log`); `usuarioESquadLeader403`; `cartaoPorSquadAlfabetico`; `semEstimativaAcimaDe20Porcento` (estado `problema` com 25%).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CentralCalculatorTest*' --tests '*CentralRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(inicio): Central de atenção com itens iguais aos cards de destino`.

---

### Tarefa 16: Visão do squad e Meu painel (API)

**Arquivos:** Modificar `inicio/InicioRoutes.kt`, `inicio/InicioDto.kt`; Teste `inicio/SquadEMeuPainelRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class SquadDto(val squad: String, val carga: List<LinhaCargaDto>, val vencimentos: VencimentosDto, val ausencias: List<AusenciaDto>,
      val riscoAlto: Int, val riscoMedio: Int, val avisos: List<AvisoDto>)
  @Serializable data class MeuPainelDto(val minhasAbertas: Int, val vencemEm7d: Int, val ocupacao4Sem: OcupacaoDto, val ausenciasFuturas: List<AusenciaDto>,
      val fila: List<ItemFilaDto>, val esforcoDoMes: EsforcoResumoDto?, val avisos: List<AvisoDto>)
  @Serializable data class ItemFilaDto(val key: String, val resumo: String, val vence: String?, val termino: String?, val atrasa: Boolean, val nivelRisco: String)
  @Serializable data class EsforcoResumoDto(val pesoTipo: Double, val testes: Int, val horas: Double, val indice: Double, val versaoRegras: String)
  ```
  `GET /api/squad?squad=` (SQUAD_LEADER: o squad que lidera na data; COORDENADOR e GERENTE: qualquer squad; outro squad → 403). `GET /api/meu-painel` (todos os papéis; **sempre** o próprio usuário, sem parâmetro `pessoa`; Squad Leader escolhe uma pessoa do squad com `pessoa=` visível). MP-01 minhas abertas, MP-02 ocupação 4 semanas, MP-03 em risco (≥ médio), MP-04 ausências futuras (fim ≥ hoje, sem motivo), MP-05 fila ordenada pela `Agenda` com término e risco, mais "vencem em 7 dias" e o esforço do mês (wireframe 6.5).
- [ ] **Passo 1: Testes que falham:** `meuPainelDaAna` (CAP-1: `minhasAbertas=2`, `vencemEm7d=2`, ocupação `0.25`, ausências `[04/11–05/11]`, fila `[TG-201 (03/11), TG-202 (06/11, atrasa)]`); `meuPainelIgnoraParametroPessoaDoUsuario` (`ana` com `pessoa=bruno` → os dados continuam dela); `squadLeaderVePessoaDoSquad` (`lider.alfa` com `pessoa=bruno` → 200; `pessoa=carla` → 403); `squadDoLider` (`lider.alfa` sem parâmetro → Alfa; `squad=Beta` → 403); `numerosIguaisAosCards` (`vencemEm7d` == Vencimentos com `dias=7` para a mesma pessoa).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SquadEMeuPainelRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(inicio): Visão do squad e Meu painel`.

---

### Tarefa 17: Telas Central de atenção, Visão do squad e Meu painel

**Arquivos:** Criar `app/frontend/src/screens/{Central,Squad,MeuPainel}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx` (rotas `central`, `squad`, `meu`; início por papel de `inicioDoPapel`), i18n, mock de desenvolvimento.

**Interfaces:** Consome as Tarefas 15 e 16. Layout **editorial** (direção híbrida, plano visual 6.1 e 6.5): KPIs no topo, lista de indicadores em colunas (cada linha é link para a tela e abre o drill), gráficos por último; Meu painel com a fila "da mais urgente" e o esforço do mês.

- [ ] **Passo 1: Testes que falham:** `centralItensSaoLinks` (cada item navega para `rota` com o `drill`); `centralSemAtualizacaoEParadasEmLinhasSeparadas`; `centralCoordenadorSemItemIntegridade`; `inicioPorPapel` (GERENTE → `/central`, SQUAD_LEADER → `/squad`, USUARIO → `/meu`); `meuPainelFilaComSimbolos` (`▲ atrasa`); `meuPainelEsforcoTresComponentes`; `squadVencimentosDaSemana`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Central src/screens/Squad src/screens/MeuPainel` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira e `pnpm build`. **Passo 5: Captura de verificação** (uma rodada das telas do bloco 7b, 1440 e 390 px, três temas). **Passo 6: Commit** `feat(inicio): telas Central de atenção, Visão do squad e Meu painel`.

---

### Tarefa 18: Diferenças esperadas (bloco 7b)

**Arquivos:** Modificar `docs/analise/diferencas-esperadas-checklist-integridade-governanca.md` (seção 7b), `docs/DiferencasChecklistIntegridadeDocTest.kt`, `HANDOFF.md`; Criar `distribuicao/CompatLegadoDistribuicaoTest.kt`.

- [ ] **Passo 1: Testes que falham:** `scoreComoOLegado` (pesos 1,0/0,5/1,0/0,6/0,15 e produtividade 0,85–1,15 de `legacy/backend/jiradash/distribution/engine.py`, conferidos antes de aceitar); `rebalanceamentoComoOLegado` (gatilho 110%, até 10 movimentos, histerese 20 pp); `documentoCitaOsIds` passa a exigir VG-01..VG-12, MP-01..MP-05, DI-01..DI-05, RB-01..RB-03, QD, AU-01, AU-02, SI-01, SI-02.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompatLegadoDistribuicaoTest*' --tests '*DiferencasChecklistIntegridadeDocTest*'` → FALHA. **Passo 3: Escrever** a seção 7b: DI-04 sem ranking (fatores alfabéticos), decisões gravadas só no painel, Central com "Sem atualização" e "Paradas" separados (achado 2), item de Integridade só para o gerente, QD com `DONE_SEM_RESOLUTION`/`RESOLUTION_SEM_DONE` e campos indisponíveis, Configuração em leitura nova, SI-01/SI-02 já entregues pelo Plano 4A. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(governanca): diferenças esperadas do bloco 7b`.

---

## Autoavaliação

- **Cobertura da spec e do relatório:** CR (T1 a T3), IN com os 12 sinais, gravidade 1 a 3, só gerente e auditada (T4 a T7; spec 7.2 e 7.3), DI e RB em modo sombra (T9 a T12; spec 2 "Não objetivos"), QD com a divergência done × resolution (T13; spec 4.2), AU-01 e AU-02 (T11, T14), SI (Plano 4A), Central com itens separados (T15; spec 4.2), início por papel (T15 a T17; plano visual seção 4). Heurísticas versionadas (spec 4.2 e 9): T1, T4, T9.
- **Consistência de tipos:** `ParCR` (T1) alimenta `EstatisticasChecklistReview` (T2) e o card (T3) e o item `PARES_CR_COM_ALERTA` (T15); `EvidenciaIntegridade` (T4, T5) alimenta `NiveisPorPessoa` (T6) e a rota (T7); `Candidato`/`Sugestao` (T9) e `Movimento` (T10) viram DTOs na T11; `CentralCalculator` usa os calculadores das Fases 4 a 7 por `FontesDaCentral`.
- **Pendências deliberadas:** `ESTIMATIVA_AUMENTADA` inativo até uma decisão futura do usuário ampliar os campos com valor em `change_event` (a decisão 7 do Plano 4 deixou estimativa e worklog fora); vocabulário de `labelsCriticidade` a confirmar na 1ª conexão real (depois que o usuário ligar `JIRA_LIVE_ENABLED=true`); base legal com o DPO (obrigatória pela spec 3) antes do uso real da Integridade e do Registro de acesso: até lá, só fixtures e demo.

## Decisões (25/10, valor padrão recomendado, reversível)

O usuário delegou estas escolhas em 25/10; vale a opção que o plano recomendava. Cada uma pode ser revertida depois por decisão do usuário. A spec da fase é `docs/superpowers/specs/2026-10-25-spec-fase-07-checklist-integridade-distribuicao-governanca.md`.

1. **Distribuição (DI-04) sem ranking: o sugerido e os fatores de todos os candidatos em ordem alfabética, sem coluna de posição.** Cumpre "sem ranking de pessoas" e mantém a explicação da sugestão (Tarefa 9, `fatoresEmOrdemAlfabetica`; Tarefa 12, `fatoresAlfabeticosSemPosicao`). Alternativa descartada: lista ordenada por score como no app atual.
2. **"Quem atrasa quem" (CR-12) vira "atrasos em cadeia":** contagem de pares em que a review atrasada adiou o pai, com drill, sem nomes nem pares de pessoas (Tarefa 2, `atrasosEmCadeiaSemPessoas`). Alternativa descartada: matriz pessoa × pessoa.
3. **A "pessoa" de um sinal de Integridade é o autor do evento que o dispara** (lote, troca, fechamento); sem autor, o responsável no instante. O responsável aparece no detalhe (Tarefa 4). Alternativa descartada: sempre o responsável da task.
4. **Decisões de Distribuição e Rebalanceamento gravadas numa tabela local do painel** (`decisao_distribuicao`), auditada e **sem escrita no Jira** (Tarefa 11, `SemEscritaNoJiraTest`). Dá o rastreio do AU-01. Alternativa descartada: não gravar nada.
