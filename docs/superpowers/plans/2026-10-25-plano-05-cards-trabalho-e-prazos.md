# Fase 5: Cards de trabalho e prazos — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Recriar, a partir dos dados já sincronizados no PostgreSQL, os cards de trabalho em curso e de resultados do app atual (Abertas, Negligenciadas, Fechadas, Metas e prazos, Negativas, Riscos, Previsão e Épicos), com "no prazo" e "no prazo ajustado", percentis por tipo de issue, eventos pela data do changelog e Monte Carlo com taxa de chegada.

**Arquitetura (Architecture):** Um modelo de leitura único (`IssueSnapshot`: campos atuais + linha do tempo + eventos) sai de um `WorkStore` sobre `issue` e `change_event`, e é reaproveitado nas Fases 6 e 7. Métricas transversais puras (`Fluxo`) e um calculador puro por card produzem DTOs; uma rota genérica (`CardDeLeitura` + `rotaDeCard`) aplica filtros, `Visibility`, aviso de status sem mapeamento e `access_log`, e serve card e detalhamento com o mesmo cálculo. As telas usam um kit comum (`CardPage`, `KpiCard`, `DrillDrawer`).

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kaml (YAML estrito), PostgreSQL + Flyway + Testcontainers 2.0.3; React + TypeScript (Vite, Vitest, Testing Library, TanStack Query).

**Spec da fase (ler primeiro):** `docs/superpowers/specs/2026-10-25-spec-fase-05-cards-trabalho-e-prazos.md`. **Spec mestra:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 4.2, 4.3, 5.2, 6.1, 8 item 5, 10). Inventário e fórmulas: `docs/analise/relatorio-cards-app-atual.md` (seções 3, 4.3 a 4.11, 6 e 7.1). Visual: `docs/design/plano-visual-frontend.md` (seções 5 e 7).

Dividido em dois blocos, cada um um PR que deixa a `main` verde: **5a** (Tarefas 1 a 9: base de leitura, Abertas, Negligenciadas, Fechadas, Metas) e **5b** (Tarefas 10 a 14: Negativas, Riscos, Previsão, Épicos).

## Estado herdado (confirmar na execução)

Este plano consome nomes dos Planos 3, 4A e 4. **Antes da Tarefa 1**, o executor confere cada nome abaixo no código da `main`; se o 4A ou o 4 o renomearam, atualiza este plano (só os nomes) e registra no `progress.md`.

| Nome | Origem |
|---|---|
| `IssueTimeline`, `TimelineBuilder.intervals`, `StatusInterval`, `firstEntered`, `lastLeft`, `ORDEM_NATURAL_DE_CHAVES` | `domain/IssueTimeline.kt`, `domain/TimelineBuilder.kt` (Plano 3) |
| `StatusMap`, `StatusCategory`, `UnmappedStatusException` | `domain/StatusMap.kt` (Plano 3) |
| `Percentiles.of/median`, `TimeUnitMode { UTIL, CORRIDO }` | `domain/Percentiles.kt`, `domain/TimeInStatusCalculator.kt` (Plano 3) |
| `BusinessCalendar.businessDays/calendarDays/localDate/isBusinessDay` | `time/BusinessCalendar.kt` (Plano 1) |
| `ChangeEventRecord`, `ChangelogParser`, `PostgresIssueStore.upsert`, `OsVersionExtrator` | `sync/` (Plano 3) |
| `AvisoDto` (com `params: Map<String, String>` desde o Plano 4A, Tarefa 8), `DrillRow` | `cards/TempoEmStatusDto.kt` (Plano 3) |
| `DadosDeDemonstracao`, `DASHBOARD_DEMO_DATA` (dados fictícios para conferir as telas sem Jira) | `demo/` (Plano 4A, Tarefa 10) |
| `Visibility.visibleUserIds`, `RoleDirectory.membersOf/squadOf/squadsLedBy`, `Principal`, `Role` | `security/` (Plano 1) |
| `AccessLog.record`, `testContextComBanco` | `db/AccessLog.kt`, testes (Plano 3) |
| `executarAuditado` | `cards/RotaAuditada.kt` (Plano 4, Tarefa 7) |
| `LabelsDeAtraso.justifica`, `NoPrazo.calcular`, `AuditIssue`, `ctx.labelsDeAtraso` | `domain/LabelsDeAtraso.kt`, `domain/NoPrazo.kt`, `auditoria/AuditModel.kt` (Plano 4) |
| `FiltroDeFicha.de(spec)`, `ctx.fichas`, ficha `sincronizacao.card.yaml`, coluna `issue.removida_em` | Plano 4A (Tarefas 3, 6 e 8) |
| `shared/csv.ts` (`linhaFiltrosCsv`), `Shell`, `t()` | frontend (Planos 3 e 4) |

## Restrições globais (Global Constraints)

- Nenhuma chamada ao Jira; só fixtures, builders em memória e `MockEngine`. `JIRA_LIVE_ENABLED=false`. Hook `BLOQUEADO` = parar e relatar. Variáveis novas nunca começam com `JIRA_`.
- Fuso America/Manaus para todo "dia"; **dias úteis por padrão** (`unit=corrido` alterna e fica na URL); semana = segunda a domingo de Manaus.
- **Concluída** = categoria `DONE` **e** `resolution` preenchida **e** `resolved` preenchido; resolução negativa (lista em `status_map`) = cancelada; divergência vai para aviso `DONE_SEM_RESOLUTION` (Qualidade dos dados, Fase 7).
- **Atraso justificado:** "no prazo" e "no prazo ajustado" lado a lado, com a mesma regra de `NoPrazo` (Plano 4); labels de `labels_atraso.yaml`.
- Status fora do mapa → resposta 200 com `AvisoDto(STATUS_SEM_MAPEAMENTO, bloqueante)` e números nulos, nunca 500.
- **Agregados seguem a visibilidade:** todo número (inclusive P50/P85 históricos e throughput) é calculado só sobre as issues que o papel vê; para `USUARIO`, isso significa **só as tasks da própria pessoa** (Decisão 2). Faixa histórica com menos de `AMOSTRA_MINIMA = 5` itens continua aparecendo, com o aviso informativo `AMOSTRA_PEQUENA` (`params`: `tipo`, `n`, `minimo`). Issue sem responsável ou com responsável fora do diretório: só coordenação e gerente (Decisão 1).
- **Todo número clicável abre as tasks que o compõem, com o mesmo tamanho.**
- **Sem ranking de pessoas:** tabelas por pessoa em ordem alfabética, sem "maior/menor"; contagem por pessoa atribuída pela posse no instante do evento.
- Heurísticas (pesos do risco, parâmetros da previsão) em YAML versionado; a versão sai em toda resposta (`versaoRegras`). Monte Carlo com semente injetável; em produção a semente é derivada do dia de Manaus e dos filtros (card, detalhamento e recarga no mesmo dia mostram o mesmo número).
- JQL só por `CardQueryFactory`; cada card tem ficha `app/config/cards/<id>.card.yaml` com teste de snapshot do JQL. A leitura no banco aplica `FiltroDeFicha.de(ficha)` e `removida_em IS NULL`.
- Cor nunca é o único sinal (`✓` `●` `▲` com texto); pt-BR e inglês; três temas.
- Migração: próximo número livre na execução; nunca editar migração mesclada. Docker ativo (`sh .claude/hooks/ensure-docker.sh`); banco real por Testcontainers.
- Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(cards): ...`).

## Foco da revisão (Review Focus)

1. **Resolução às 23:30 de Manaus** (dia seguinte em UTC) cai no mesmo dia em Fechadas, Metas e Auditoria → `resolucaoNaFronteiraDeManaus` (T3) e `mesmoNoPrazoEmTresTelas` (T9).
2. **USUARIO pedindo card agregado** (P85 histórico, throughput, Monte Carlo) não recebe número calculado com tasks de outros → `agregadoSoComOVisivel` (T4) e `previsaoDoUsuarioSoComAsProprias` (T12).
3. **Base sincronizada antes da Tarefa 1** (prioridade e estimativas nulas em todas as issues): o card não mente "0 sem estimativa"; mostra aviso → `camposDeTrabalhoIndisponiveisViraAviso` (T5).
4. **Evento antigo em issue atualizada agora** (reaberta em setembro, `updated` em outubro): não conta em outubro → `reabertaForaDoPeriodoNaoConta` (T10).
5. **Backlog que nunca termina** (chegada ≥ vazão): a previsão diz "mais de 104 semanas", não um número inventado nem exceção → `chegadaIgualAVazaoNaoConverge` (T12).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  domain/StatusMap.kt                                    # + ordem do fluxo e resoluções negativas
  sync/CamposDeTrabalho.kt, sync/IssueStore.kt           # extração de prioridade, estimativas, pontos, épico, flag
  trabalho/IssueSnapshot.kt, WorkStore.kt                # modelo de leitura e leitura no PostgreSQL
  trabalho/Fluxo.kt, MonteCarlo.kt                       # métricas transversais
  cards/FiltrosDeCard.kt, EscopoVisivel.kt, CardDeLeitura.kt   # rota genérica de card
  cards/abertas/, negligenciadas/, fechadas/, metas/, negativas/, riscos/, previsao/, epicos/   # calculador + DTO + card
  riscos/RegrasDeRisco.kt, previsao/RegrasDePrevisao.kt
app/backend/src/main/resources/db/migration/V{n}__campos_de_trabalho.sql
app/config/cards/{abertas,negligenciadas,fechadas,metas,negativas,riscos,previsao,epicos}.card.yaml
app/config/risco.exemplo.yaml, app/config/previsao.exemplo.yaml, app/config/jira_campos.exemplo.yaml, app/config/status_map.exemplo.yaml
app/frontend/src/shared/{CardPage.tsx,KpiCard.tsx,DrillDrawer.tsx,useCard.ts,Filtros.tsx}
app/frontend/src/screens/{Abertas,Negligenciadas,Fechadas,Metas,Negativas,Riscos,Previsao,Epicos}/
docs/analise/diferencas-esperadas-trabalho-e-prazos.md
```

**Cenário TRB-1** (Tarefas 3 a 9 e 11; relógio `2026-10-30T12:00:00Z` = sexta 30/10, 08:00 em Manaus; calendário de teste com feriados 12/10/2026 e 02/11/2026; papéis de teste em memória como no Plano 3: `ana` e `bruno` USUARIO do Alfa, `carla` USUARIO do Beta, `lider.alfa` SQUAD_LEADER do Alfa, `coord.um`, `gerente.exemplo`). Mapa de status de teste: `Open` todo, `In Progress` active, `Submitted` wait, `On Hold` blocked, `Resolved`/`Closed`/`Cancelled` done; `ordem: [Open, In Progress, Submitted, Resolved, Closed]`; `resolucoesNegativas: ["Won't Do", "Duplicate"]`.

| Issue | Tipo | Resp. | Criada | 1ª entrada ativa | Resolvida (resolution) | Vence | Outros |
|---|---|---|---|---|---|---|---|
| TG-111 | Checklist | ana | 10-01T12Z | 10-05T12Z | 10-09T12Z (Done) | 10-09 (era 10-08 até evento de 10-07) | 3 pontos |
| TG-112 | Checklist | bruno | 10-02T12Z | 10-06T12Z | 10-15T12Z (Done) | 10-15 | 5 pontos |
| TG-113 | Checklist | ana | 10-05T12Z | 10-07T12Z | 10-20T12Z (Done) | 10-16 | `LATE_SENT` |
| TG-114 | Checklist Review | carla | 10-08T12Z | 10-13T12Z | 10-14T12Z (Done) | 10-16 | |
| TG-115 | Checklist | bruno | 10-10T12Z | — | status `Closed`, 10-22T12Z (Won't Do) | — | |
| TG-116 | Checklist | ana | 10-03T12Z | 10-05T12Z | status `Closed`, `resolved` 10-12T12Z, resolution nula | — | |
| TG-101 | Checklist | ana | 09-28T12Z | 10-01T12Z (In Progress até hoje) | aberta | 10-28 | High, sem estimativa, `updated` 10-20T12Z |
| TG-102 | Checklist Review | bruno | 09-15T12Z | — (Open) | aberta | — | Medium, estimativa 4 h, `updated` 10-29 |
| TG-103 | Checklist | carla | 10-20T12Z | 10-21T12Z; On Hold desde 10-26T12Z | aberta | 11-02 | High, estimativa 8 h, gasto 12 h, `updated` 10-26T12Z |
| TG-104 | Checklist | — | 10-29T12Z | — (Open) | aberta | — | Medium |

As concluídas vão direto de In Progress para Closed. Ciclo em dias úteis: TG-111 `4`, TG-112 `6`, TG-113 `8`, TG-114 `1` (corridos `4`, `9`, `13`, `1`).

---

# Bloco 5a — Base de leitura, Abertas, Negligenciadas, Fechadas e Metas

### Tarefa 1: Campos de trabalho, ordem do fluxo e resoluções negativas

**Arquivos:** Criar `sync/CamposDeTrabalho.kt`, `resources/db/migration/V{n}__campos_de_trabalho.sql`; Modificar `domain/StatusMap.kt`, `sync/IssueStore.kt` (`upsert` grava as colunas novas), `app/config/jira_campos.exemplo.yaml`, `app/config/cards/sincronizacao.card.yaml` (4A), `app/config/status_map.exemplo.yaml`, `docs/jira/checklist-primeira-conexao.md` (conferir os nomes dos campos novos); Teste `sync/CamposDeTrabalhoTest.kt`, `domain/StatusMapTest.kt`, `sync/SyncerTest.kt` (acrescentar), `jql/CardQueryFactoryTest.kt`.

**Interfaces:**
- Migração: `issue` ganha `priority text`, `remaining_s bigint`, `story_points numeric`, `epic_key text`, `flagged boolean NOT NULL DEFAULT false`, `components text[] NOT NULL DEFAULT '{}'`. Conferir que `estimate_s` vem de `timeoriginalestimate` e `spent_s` de `timespent`; se não, corrigir no `upsert` (sem nova coluna).
- Dicionário (instância `samsung`): `priority` SELECT, `timeoriginalestimate`, `timeestimate`, `timespent` NUMBER, `components` SELECT, e com `finalidade: "a confirmar"` `Story Points` NUMBER, `Epic Link` TEXT, `Flagged` SELECT. A ficha de sincronização passa a pedi-los.
- Produz:
  ```kotlin
  data class CamposDeTrabalho(val priority: String?, val remainingS: Long?, val storyPoints: Double?, val epicKey: String?,
      val flagged: Boolean, val components: List<String>)
  object CamposDeTrabalhoExtrator { fun extrair(issue: JsonObject, ids: Map<String, String?>): CamposDeTrabalho }   // ids: nome do dicionário -> customId
  class StatusMap(byName: Map<String, StatusCategory>, val ordem: List<String> = emptyList(), resolucoesNegativas: Set<String> = emptySet()) {
      fun indiceNaOrdem(status: String): Int?            // null fora da ordem
      fun ehResolucaoNegativa(resolution: String): Boolean   // sem diferenciar maiúsculas
  }
  ```
  YAML: `status_map:` como hoje, mais `ordem:` e `resolucoesNegativas:` opcionais; status da `ordem` fora do mapa → falha citando o status; chave desconhecida continua falhando.
- [ ] **Passo 1: Testes que falham:** `extraiPrioridadeEEstimativas` (`{"priority":{"name":"High"},"timeestimate":7200}` → `priority="High"`, `remainingS=7200`); `flagPresenteViraTrue` (`Flagged` com `[{"value":"Impediment"}]` → `true`; ausente → `false`); `campoCustomSemIdFicaNulo` (id desconhecido → `storyPoints == null`, sem exceção); `pontosNaoNumericosFicamNulos`; `ordemEResolucoesNegativasCarregam`; `ordemComStatusForaDoMapaFalha`; `resolucaoNegativaSemDiferenciarMaiusculas` (`won't do`); `arquivoAntigoSemOrdemContinuaValido`; `SyncerTest.gravaCamposDeTrabalho` (fixture com `priority` e `timeestimate` grava `priority` e `remaining_s`); `fichaDeSincronizacaoPedeCamposNovos` (os `fields` do `JqlQuery` contêm `priority`, `timeestimate`, `timespent`, `timeoriginalestimate`).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*CamposDeTrabalhoTest*' --tests '*StatusMapTest*' --tests '*SyncerTest*' --tests '*CardQueryFactoryTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa (conferir os XML). **Passo 5: Commit** `feat(cards): campos de trabalho, ordem do fluxo e resoluções negativas`.

---

### Tarefa 2: Modelo de leitura `IssueSnapshot` e `WorkStore`

**Arquivos:** Criar `trabalho/IssueSnapshot.kt`, `trabalho/WorkStore.kt`; Teste `trabalho/WorkStoreTest.kt` (Testcontainers; dados gravados com `PostgresIssueStore.upsert` a partir de JSON de fixture do TRB-1).

**Interfaces:**
- Consome: `IssueTimeline`, `ChangelogParser`, `ChangeEventRecord`, `FiltroDeFicha`, `AuditIssue`.
- Produz:
  ```kotlin
  data class IssueSnapshot(val instance: String, val key: String, val summary: String, val issueType: String, val priority: String?,
      val status: String, val resolution: String?, val created: Instant, val updated: Instant?, val resolved: Instant?,
      val duedate: LocalDate?, val assignee: String?, val reporter: String?, val labels: List<String>, val parentKey: String?,
      val epicKey: String?, val storyPoints: Double?, val estimateS: Long?, val remainingS: Long?, val spentS: Long?,
      val osVersion: String?, val flagged: Boolean, val components: List<String>,
      val timeline: IssueTimeline, val events: List<ChangeEventRecord>)
  fun IssueSnapshot.toAuditIssue(): AuditIssue                    // adaptador para NoPrazo e TaskReview do Plano 4
  fun IssueSnapshot.vencimentoOriginal(): LocalDate?              // `from` do 1º evento de duedate legível; sem eventos, duedate atual
  enum class Situacao { ABERTAS, RESOLVIDAS, TODAS }
  data class ConsultaDeTrabalho(val instance: String, val filtro: FiltroDeFicha?, val situacao: Situacao,
      val resolvidasDe: Instant? = null, val resolvidasAte: Instant? = null, val osVersion: String? = null)
  interface WorkStore {
      fun snapshots(c: ConsultaDeTrabalho): List<IssueSnapshot>
      fun comEventoEntre(instance: String, filtro: FiltroDeFicha?, campos: Set<String>, de: Instant, ate: Instant): List<IssueSnapshot>  // com TODOS os eventos
      fun temCamposDeTrabalho(instance: String): Boolean           // alguma issue com priority não nula
  }
  class PostgresWorkStore(private val ds: DataSource) : WorkStore
  ```
  `ABERTAS` = `resolved IS NULL`; `RESOLVIDAS` em `[resolvidasDe, resolvidasAte)`; sempre `removida_em IS NULL`; eventos em lote por `issue_id = ANY(?)` ordenados por `(at, id)`; tudo parametrizado.
- [ ] **Passo 1: Testes que falham:** `abertasEResolvidas` (TRB-1: 4 abertas; resolvidas de 10-01 a 10-31 → 6); `fronteiraDoPeriodo` (`resolved == de` entra, `== ate` fica fora); `filtroDeFichaAplicado` (tipo fora da ficha não vem); `removidaNaoAparece`; `eventosDeOutrasDatasVemJuntos`; `vencimentoOriginalPeloPrimeiroEvento` (TG-111 → `2026-10-08`); `adaptadorParaAuditIssue` (mesmos `key`, `duedate`, `labels`, `events`); `instanciaSeparada`; `semPrioridadeEmNenhumaIssue` (`temCamposDeTrabalho == false`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*WorkStoreTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): modelo de leitura das issues e leitura no PostgreSQL`.

---

### Tarefa 3: Métricas transversais (`Fluxo`)

**Arquivos:** Criar `trabalho/Fluxo.kt`; Teste `trabalho/FluxoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class Faixa(val p50: Double, val p85: Double, val n: Int)
  object Fluxo {
      fun concluidaPositiva(s: IssueSnapshot, map: StatusMap): Boolean      // DONE + resolution + resolved + resolution não negativa
      fun cancelada(s: IssueSnapshot, map: StatusMap): Boolean              // DONE + resolution negativa
      fun divergenteDoneResolution(s: IssueSnapshot, map: StatusMap): Boolean
      fun entrouEmDone(s: IssueSnapshot, map: StatusMap): Instant?          // última transição para categoria DONE; base do período de "DONE sem resolution"
      fun emAndamento(s: IssueSnapshot, map: StatusMap): Boolean            // ACTIVE, WAIT ou BLOCKED, ou flagged
      fun inicioAtivo(s: IssueSnapshot, map: StatusMap): Instant?           // 1ª entrada em ACTIVE/WAIT/BLOCKED
      fun cicloDias(s: IssueSnapshot, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode): Double?
      fun leadDias(s: IssueSnapshot, cal: BusinessCalendar, unit: TimeUnitMode): Double?
      fun idadeDias(s: IssueSnapshot, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode, agora: Instant): Double   // desde inicioAtivo ou created
      fun diasNoStatusAtual(s: IssueSnapshot, cal: BusinessCalendar, unit: TimeUnitMode, agora: Instant): Double
      fun semanaDe(at: Instant, zone: ZoneId): LocalDate                    // segunda-feira de Manaus
      fun throughputSemanal(concluidas: List<IssueSnapshot>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>>
      fun chegadasSemanais(issues: List<IssueSnapshot>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>>
      fun faixasPorTipo(valores: Map<String, List<Double>>): Map<String, Faixa>
      const val AMOSTRA_MINIMA = 5
      fun avisosDeAmostra(faixas: Map<String, Faixa>, minimo: Int = AMOSTRA_MINIMA): List<AvisoDto>   // AMOSTRA_PEQUENA por tipo com n < minimo, ordem por tipo
  }
  ```
  Séries semanais: mais antiga primeiro, semanas sem itens com `0`, `ultimaSemana` inclusive. `avisosDeAmostra` é usado por todo card que mostra faixa histórica (Abertas, Fechadas, Riscos, Previsão por issue); o aviso é informativo e não esconde o número.
- [ ] **Passo 1: Testes que falham** (TRB-1):
  - `concluidaExigeResolution` (TG-116 → falso e `divergenteDoneResolution`); `canceladaPelaResolucaoNegativa` (TG-115).
  - `doneSemResolvedNemResolution`: variante de TG-116 em `Closed` com `resolved` nulo → `entrouEmDone` = instante da última transição para `Closed`, `divergenteDoneResolution`, e não conta como aberta (categoria `DONE`).
  - `cicloEmUteisECorridos`: TG-112 → `6.0` úteis, `9.0` corridos; `leadCorridos` TG-111 → `8.0`.
  - `idadeDesdeInicioAtivoOuCriacao`: TG-101 → `29.0` corridos e `20.0` úteis; TG-102 → `45.0` corridos.
  - `diasNoStatusAtual`: TG-103 → `4.0` úteis.
  - `resolucaoNaFronteiraDeManaus`: `resolved = 2026-10-19T03:30Z` (18/10 23:30 Manaus, domingo) cai na semana de 12/10.
  - `throughputComZeros`: concluídas TG-111..114 com `ultimaSemana = 2026-10-26`, 4 semanas → `[(10-05,1), (10-12,2), (10-19,1), (10-26,0)]`.
  - `faixasPorTipo`: Checklist `[4,6,8]` → `Faixa(6.0, 7.4, 3)`; Review `[1]` → `Faixa(1.0, 1.0, 1)`.
  - `amostraPequenaGeraAviso`: as duas faixas acima → avisos `AMOSTRA_PEQUENA` com `params` `{tipo: "Checklist", n: "3", minimo: "5"}` e `{tipo: "Checklist Review", n: "1", minimo: "5"}`; faixa com `n = 5` → nenhum aviso.
  - `flaggedContaComoEmAndamento`; `statusSemMapaPropaga`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FluxoTest*'` → FALHA. **Passo 3: Implementar** com `BusinessCalendar` e `Percentiles`. **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): métricas transversais de fluxo`.

---

### Tarefa 4: Filtros comuns, escopo visível, rota genérica de card e kit de tela

**Arquivos:** Criar `cards/FiltrosDeCard.kt`, `cards/EscopoVisivel.kt`, `cards/CardDeLeitura.kt`; Modificar `cards/TempoEmStatusRoutes.kt` (usa `FiltrosDeCard.ler` e `escopoDe`, sem mudar respostas), `Application.kt`; Criar `app/frontend/src/shared/{CardPage.tsx,KpiCard.tsx,useCard.ts,Filtros.tsx}`, mover `screens/TempoEmStatus/DrillDrawer.tsx` para `shared/DrillDrawer.tsx`; Teste `cards/FiltrosDeCardTest.kt`, `cards/CardDeLeituraTest.kt`, `shared/CardPage.test.tsx`, `i18n/paridade.test.ts` (criar se não existir). `TempoEmStatusRoutesTest`, `AuditoriaRoutesTest` e os testes da tela Tempo em status seguem verdes.

**Interfaces:**
- Produz:
  ```kotlin
  data class FiltrosDeCard(val squad: String?, val pessoa: String?, val tipos: List<String>, val prioridades: List<String>,
      val os: String?, val de: LocalDate?, val ate: LocalDate?, val unit: TimeUnitMode) {
      companion object { fun ler(params: Parameters, hoje: LocalDate, periodoPadraoDias: Int?, maxDias: Int = 366): FiltrosDeCard }
  }
  class FiltroInvalidoException(val parametro: String) : IllegalArgumentException("filtro inválido: $parametro")
  data class Escopo(val visiveis: Set<String>, val veSemResponsavel: Boolean)
  fun escopoDe(p: Principal, ctx: AppContext, hoje: LocalDate, f: FiltrosDeCard, todos: Set<String>): Escopo   // pessoa fora do visível -> AcessoNegadoException (403)
  fun Escopo.permiteAtual(s: IssueSnapshot): Boolean                  // responsável atual
  fun Escopo.permitePosse(s: IssueSnapshot, map: StatusMap, agora: Instant): Boolean   // algum trecho de posse (regra do Plano 3)
  data class EntradaDeCard(val principal: Principal, val filtros: FiltrosDeCard, val escopo: Escopo, val agora: Instant, val hoje: LocalDate, val ctx: AppContext, val store: WorkStore)
  interface CardDeLeitura<D : Any> {
      val id: String                                  // = id da ficha
      val kinds: Set<String>                          // prefixos aceitos no drill ("vencidas", "status:" ...)
      val periodoPadraoDias: Int?                     // null = card sem período
      fun calcular(e: EntradaDeCard): D
      fun detalhar(e: EntradaDeCard, kind: String): List<DrillRow>
      fun vazio(avisos: List<AvisoDto>): D            // resposta quando o status não está mapeado
  }
  inline fun <reified D : Any> Route.rotaDeCard(ctx: AppContext, store: WorkStore, auditoria: AccessLog, card: CardDeLeitura<D>,
      papeis: Set<Role> = Role.entries.toSet())        // GET /api/cards/{id} e /api/drill/{id}; usa executarAuditado
  ```
  Parâmetros: `squad`, `pessoa`, `tipo` (repetível), `prioridade` (repetível), `os`, `de`, `ate`, `unit=util|corrido`. Squad pelo `RoleDirectory.membersOf` na data; inexistente → 400. `kind` fora de `kinds` → 400. `UnmappedStatusException` → 200 com `vazio(...)`.
- Frontend: `export function CardPage(props: {titulo, pergunta, filtros, csv, children})`, `export function KpiCard(props: {rotulo, valor: string, estado?: 'ok'|'atencao'|'problema', onAbrir?})` (símbolo `✓`/`●`/`▲` + texto; `role="button"` quando clicável), `useCard<T>(id: string, params: URLSearchParams)`, `DrillDrawer` (move; `Esc` fecha e devolve o foco).
- [ ] **Passo 1: Testes que falham:** `lerFiltrosValidos` (`tipo=Checklist&tipo=Checklist Review` → 2 tipos); `datasInvalidasDao400` (`de=2026-13-01`, `de > ate`, período > `maxDias`); `unitPadraoUtil`; `squadInexistente400`; `pessoaForaDoVisivel403`; `semResponsavelSoParaCoordenacao` (TG-104: `coord.um` vê, `lider.alfa` não); `agregadoSoComOVisivel` (card de teste que conta as abertas do TRB-1: `ana` → 1 (TG-101), `lider.alfa` → 2 (TG-101, TG-102), `gerente.exemplo` → 4); `kindDesconhecido400`; `statusSemMapaDa200ComAviso`; `consultaGravaAccessLog` (200, 400, 403); `TempoEmStatusRoutesTest` inteiro verde; frontend `kpiMostraSimboloETexto`, `kpiClicavelAbreGaveta`, `filtrosVaoParaAUrl`, `escFechaAGavetaEDevolveFoco`, `paridadeDeChavesPtEn`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FiltrosDeCardTest*' --tests '*CardDeLeituraTest*' --tests '*TempoEmStatusRoutesTest*'` e `cd app/frontend && pnpm vitest run src/shared src/i18n src/screens/TempoEmStatus` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `refactor(cards): filtros, escopo visível, rota genérica de card e kit de tela`.

---

### Tarefa 5: Abertas e Negligenciadas (domínio e API)

**Arquivos:** Criar `cards/abertas/{AbertasCalculator.kt,AbertasDto.kt,CardAbertas.kt}`, `cards/negligenciadas/{NegligenciadasCalculator.kt,NegligenciadasDto.kt,CardNegligenciadas.kt}`, `app/config/cards/abertas.card.yaml`, `negligenciadas.card.yaml`; Modificar `Application.kt`; Teste `cards/abertas/AbertasCalculatorTest.kt`, `cards/negligenciadas/NegligenciadasCalculatorTest.kt`, `cards/AbertasNegligenciadasRoutesTest.kt`, `jql/CardQueryFactoryTest.kt`.

**Interfaces:**
- Consome: `Fluxo`, `IssueSnapshot`, `CardDeLeitura`, `rotaDeCard`.
- Produz:
  ```kotlin
  @Serializable data class AbertasDto(val abertas: Int, val emAndamento: Int, val acimaDoP85: Int,
      val historicoPorTipo: List<FaixaTipoDto>, val porStatus: List<ContagemDto>, val porPrioridade: List<ContagemDto>,
      val porIdade: List<ContagemDto>, val porVencimento: List<ContagemDto>, val linhas: List<LinhaAbertaDto>, val unit: String, val avisos: List<AvisoDto>)
  @Serializable data class FaixaTipoDto(val tipo: String, val p50: Double, val p85: Double, val n: Int)
  @Serializable data class ContagemDto(val chave: String, val n: Int)
  @Serializable data class LinhaAbertaDto(val key: String, val resumo: String, val tipo: String, val status: String, val prioridade: String?,
      val responsavel: String?, val idade: Double, val vence: String?, val horasRestantes: Double?)
  @Serializable data class NegligenciadasDto(val semAtualizacao: Int, val semResponsavel: Int, val vencidas: Int, val bloqueadas: Int,
      val paradasNoStatus: Int, val aFazerAntigas: Int, val avisos: List<AvisoDto>)
  class AbertasCalculator(cal: BusinessCalendar, map: StatusMap) { fun calcular(abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode): AbertasDto }
  class NegligenciadasCalculator(cal: BusinessCalendar, map: StatusMap, diasSemAtualizacao: Int = 7, diasAFazer: Int = 30, minParadaDias: Double = 3.0, fatorParada: Double = 2.0) {
      fun calcular(abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode): Map<String, List<IssueSnapshot>>   // chave = kind
  }
  ```
  Abertas = `Situacao.ABERTAS` com categoria diferente de `DONE` (issue em `DONE` com `resolved` nulo não é aberta: vai para o aviso `DONE_SEM_RESOLUTION`). Histórico = concluídas positivas das últimas 16 semanas no escopo; **AB-04 compara a idade com o P85 do próprio tipo** (sem histórico do tipo → não entra e aviso `SEM_HISTORICO_DO_TIPO`). Idade em faixas `0–7`, `8–14`, `15–30`, `>30` na unidade pedida; vencimento em `vencidas`, `7d`, `depois`, `sem`. Status na ordem do fluxo (fora da ordem no fim, por nome). Negligenciadas: kinds `semAtualizacao` (em andamento com `updated` há > 7 dias corridos), `semResponsavel`, `vencidas`, `bloqueadas`, `paradas` (**só em andamento**, como no app atual: tempo no status atual > `max(3, 2 × mediana do status entre as concluídas de 16 semanas)`, só `3` sem mediana), `aFazer` (categoria TODO há > 30 dias corridos). **"Sem atualização" e "Paradas" são listas separadas** (a mesma issue pode estar nas duas). Drill: `kind` = chave de cada contagem (`status:<nome>`, `prioridade:<nome>`, `idade:<faixa>`, `vence:<faixa>`, `acimaDoP85`).
- [ ] **Passo 1: Testes que falham** (TRB-1, `unit=CORRIDO` nas faixas de idade):
  - `contagensDeAbertas`: `abertas=4`, `emAndamento=2`, `porIdade = [0–7:1, 8–14:1, 15–30:1, >30:1]`, `porVencimento = [vencidas:1, 7d:1, depois:0, sem:2]`, `porPrioridade = [High:2, Medium:2]`, `porStatus = [Open:2, In Progress:1, On Hold:1]` (só status com itens, na ordem do fluxo; `On Hold` está fora da `ordem` e vai para o fim).
  - `acimaDoP85UsaOProprioTipo` (úteis): TG-101 (idade `20.0` > P85 Checklist `7.4`) entra; TG-103 (idade `7.0`) não → `acimaDoP85 = 1`.
  - `negligenciadasSeparadas`: `semAtualizacao=[TG-101]`, `semResponsavel=[TG-104]`, `vencidas=[TG-101]`, `bloqueadas=[TG-103]`, `paradas=[TG-101, TG-103]` (limite In Progress `max(3, 2×5.0)=10` úteis; On Hold sem mediana → `3`), `aFazer=[TG-102]`.
  - `camposDeTrabalhoIndisponiveisViraAviso`: `temCamposDeTrabalho == false` → aviso `CAMPOS_DE_TRABALHO_INDISPONIVEIS` e `porPrioridade` vazio.
  - Rotas: `drillTemOMesmoTamanhoDoCard` (cada contagem == linhas do drill); `usuarioVeSoAsProprias` (`ana`: `abertas=1`); `historicoDoUsuarioSoComAsProprias` (`ana`: `historicoPorTipo` de Checklist só com TG-111 e TG-113, ciclos úteis `[4, 8]` → `p50 = 6.0`, `p85 = 7.4`, `n = 2`, e aviso `AMOSTRA_PEQUENA` com `n = "2"`; para `gerente.exemplo`, `n = 3`); `semResponsavelSoCoordenacao`; `statusNaoMapeado200`; `jqlSnapshotAbertas` (JQL exato da ficha: `project in ("TG-Basic") AND issuetype in ("Checklist", "Checklist TG", "Checklist Review") AND status not in ("Cancelled", "Canceled", "Dropped")`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AbertasCalculatorTest*' --tests '*NegligenciadasCalculatorTest*' --tests '*AbertasNegligenciadasRoutesTest*' --tests '*CardQueryFactoryTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Abertas e Negligenciadas`.

---

### Tarefa 6: Fechadas (domínio e API)

**Arquivos:** Criar `cards/fechadas/{FechadasCalculator.kt,FechadasDto.kt,CardFechadas.kt}`, `app/config/cards/fechadas.card.yaml`; Teste `cards/fechadas/FechadasCalculatorTest.kt`, `cards/FechadasRoutesTest.kt`.

**Interfaces:**
- Consome: `Fluxo`, `NoPrazo` (via `toAuditIssue`), `LabelsDeAtraso`.
- Produz:
  ```kotlin
  @Serializable data class FechadasDto(val concluidas: Int, val pontos: Double?, val ciclo: FaixaDto?, val lead: FaixaDto?,
      val noPrazo: Int, val noPrazoAjustado: Int, val comVencimento: Int, val taxaNoPrazo: Double?, val taxaAjustada: Double?,
      val canceladas: Int, val porSemana: List<SemanaDto>, val dispersao: DispersaoDto?, val porTipo: List<FaixaTipoDto>,
      val porProjeto: List<ContagemDto>, val porPessoa: List<EntregaPessoaDto>, val semResolution: Int, val unit: String, val avisos: List<AvisoDto>)
  @Serializable data class FaixaDto(val p50: Double, val p85: Double, val n: Int)
  @Serializable data class DispersaoDto(val p50: Double, val p85: Double, val p95: Double, val pontos: List<PontoCicloDto>)
  @Serializable data class SemanaDto(val semana: String, val n: Int)
  @Serializable data class EntregaPessoaDto(val pessoa: String, val finalizou: Int, val participou: Int, val pontos: Double?, val cicloP50: Double?,
      val horasEstimadas: Double?, val horasGastas: Double?)       // ordem alfabética
  class FechadasCalculator(cal: BusinessCalendar, map: StatusMap, labels: LabelsDeAtraso) {
      fun calcular(resolvidas: List<IssueSnapshot>, de: LocalDate, ate: LocalDate, agora: Instant, unit: TimeUnitMode, visiveis: Set<String>): FechadasDto
  }
  ```
  Período padrão 90 dias pela data de resolução (Manaus). "No prazo" delega a `NoPrazo` (mesma regra do Plano 4). `porPessoa`: `finalizou` = responsável na resolução; `participou` = teve posse; horas estimadas × gastas por `estimate_s`/`spent_s` da issue (worklog só na Fase 6); só pessoas visíveis, ordem alfabética, sem "melhor/pior". `semResolution` = issues em `DONE` sem resolution cuja `entrouEmDone` cai no período (lidas por `comEventoEntre(..., campos = {status})`, valem com `resolved` nulo ou não; aviso `DONE_SEM_RESOLUTION` informativo, vai para Qualidade dos dados). A ficha `fechadas` **não** exclui status de cancelamento (as canceladas contam em FE-06); o mesmo vale para `negativas`.
- [ ] **Passo 1: Testes que falham** (TRB-1, período 01/10 a 30/10):
  - `contagensDasFechadas`: `concluidas=4`, `canceladas=1`, `semResolution=1`, `pontos=8.0`.
  - `cicloELeadEmUteis`: `ciclo = FaixaDto(5.0, 7.1, 4)`; em `CORRIDO` `FaixaDto(6.5, 11.2, 4)`; `lead` corridos `FaixaDto(10.5, 14.1, 4)`.
  - `noPrazoEAjustado`: `comVencimento=4`, `noPrazo=3`, `noPrazoAjustado=4`, `taxaNoPrazo=0.75`, `taxaAjustada=1.0`.
  - `porSemana`: `[10-05:1, 10-12:2, 10-19:1, 10-26:0]`.
  - `percentisPorTipo`: Checklist `(6.0, 7.4, 3)`, Checklist Review `(1.0, 1.0, 1)`.
  - `entregasPorPessoaAlfabeticas`: `ana(finalizou 2)`, `bruno(1)`, `carla(1)`, nessa ordem.
  - Rotas: `drillTemOMesmoTamanhoDoCard` (`concluidas`, `canceladas`, `noPrazo`, `tipo:Checklist`, `semana:2026-10-12`); `squadLeaderNaoVePessoaDeOutroSquad` (`lider.alfa` não recebe linha de `carla`); `periodoPadraoNoventaDias`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FechadasCalculatorTest*' --tests '*FechadasRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Fechadas com no prazo e ajustado e percentis por tipo`.

---

### Tarefa 7: Metas e prazos (domínio e API)

**Arquivos:** Modificar `domain/NoPrazo.kt` (extrai a classificação por issue; `NoPrazoTest` segue verde); Criar `cards/metas/{MetasCalculator.kt,MetasDto.kt,CardMetas.kt,RegrasDeMetas.kt}`, `app/config/cards/metas.card.yaml`, `app/config/metas.exemplo.yaml`; Teste `cards/metas/MetasCalculatorTest.kt`, `cards/MetasRoutesTest.kt`, `domain/NoPrazoTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class FaixaDePrazo { D3_OU_ANTES, D2, D1, D, ATRASO_1_3, ATRASO_MAIS_3 }
  data class ClassePrazo(val delta: Int, val faixa: FaixaDePrazo, val noPrazo: Boolean, val ajustado: Boolean)
  fun NoPrazo.classificar(resolvidaEm: LocalDate, vencimento: LocalDate, labels: List<String>, regras: LabelsDeAtraso): ClassePrazo
  data class RegrasDeMetas(val versao: Int, val metaNoPrazo: Double, val metaAntecipadas: Double, val alertaDataOriginalPp: Double,
      val faixaAtencaoPp: Double) { companion object { fun fromYaml(path: Path): RegrasDeMetas } }   // 0.90, 0.35, 5.0, 10.0
  enum class Granularidade { DIA, SEMANA, MES }
  @Serializable data class MetasDto(val versaoRegras: String, val universo: Int, val noPrazo: Int, val noPrazoAjustado: Int,
      val noPrazoOriginal: Int, val antecipadas: Int, val emD1D2: Int, val noDia: Int, val atrasadas: Int, val semVencimento: Int,
      val taxaNoPrazo: Double?, val taxaAjustada: Double?, val taxaOriginal: Double?, val taxaAntecipadas: Double?,
      val alertaDataOriginal: Boolean, val porPeriodo: List<PeriodoMetaDto>, val distribuicao: List<ContagemDto>,
      val porTipo: List<LinhaMetaDto>, val porPrioridade: List<LinhaMetaDto>, val porSquad: List<LinhaMetaDto>,
      val porPessoa: List<LinhaMetaDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaMetaDto(val chave: String, val universo: Int, val noPrazo: Int, val noPrazoAjustado: Int, val estado: String)  // "ok" (✓ ≥ meta), "atencao" (● até 10 pp abaixo), "problema" (▲)
  class MetasCalculator(cal: BusinessCalendar, map: StatusMap, labels: LabelsDeAtraso, regras: RegrasDeMetas, roles: RoleDirectory) {
      fun calcular(concluidas: List<IssueSnapshot>, de: LocalDate, ate: LocalDate, g: Granularidade, visiveis: Set<String>): MetasDto
  }
  ```
  Universo: concluídas positivas com **vencimento** no período (padrão 90 dias), exclui tipo `Epic`. Δ = dia de Manaus da resolução − vencimento. Faixas: `Δ ≤ −3`, `−2`, `−1`, `0`, `1..3`, `> 3`. "No prazo" = `Δ ≤ 0`; ajustado = no prazo ou label que `justifica`; data original = `vencimentoOriginal()`; `alertaDataOriginal` quando `taxaNoPrazo − taxaOriginal ≥ 5 pp`. Tabelas por tipo/prioridade/squad/pessoa: squad pelo `RoleDirectory.squadOf` do responsável na data da resolução; pessoas e squads em ordem alfabética, nunca por taxa. `semVencimento` = concluídas positivas resolvidas no período sem vencimento (drill próprio). Parâmetro `granularidade=dia|semana|mes` (padrão `semana`).
- [ ] **Passo 1: Testes que falham** (TRB-1, período 01/10 a 30/10):
  - `classificarFaixas`: Δ `−3` → `D3_OU_ANTES`; `0` → `D`; `+4` → `ATRASO_MAIS_3`; `NoPrazoTest` inteiro verde.
  - `metasDoCenario`: `universo=4`, `noPrazo=3`, `noPrazoAjustado=4`, `antecipadas=1`, `emD1D2=1`, `noDia=2`, `atrasadas=1`, `taxaNoPrazo=0.75`, `taxaAjustada=1.0`, `taxaAntecipadas=0.25`.
  - `dataOriginal`: TG-111 vencia 08/10 → `noPrazoOriginal=2`, `taxaOriginal=0.5`, `alertaDataOriginal=true`.
  - `excluiEpic`; `semVencimentoTemDrillProprio`.
  - `estadoDaLinha`: taxa 0.90 com meta 0.90 → `ok`; 0.85 → `atencao`; 0.75 → `problema`.
  - `tabelaPorPessoaAlfabetica` (`ana`, `bruno`, `carla`).
  - Rotas: `drillTemOMesmoTamanhoDoCard` (`noPrazo`, `ajustado`, `faixa:D`, `atrasadas`, `semVencimento`); `granularidadeInvalida400`; `versaoDasRegrasNaResposta` (`"metas v1, labels v1"`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*MetasCalculatorTest*' --tests '*MetasRoutesTest*' --tests '*NoPrazoTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Metas e prazos com data original e atraso justificado`.

---

### Tarefa 8: Telas Abertas, Negligenciadas, Fechadas e Metas

**Arquivos:** Criar `app/frontend/src/screens/{Abertas,Negligenciadas,Fechadas,Metas}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx` (rotas `abertas`, `negligenciadas`, `fechadas`, `metas`), `i18n/pt.json`, `i18n/en.json`, mock de desenvolvimento do Vite (dados fictícios só no servidor de dev).

**Interfaces:** Consome os DTOs das Tarefas 5 a 7 e o kit da Tarefa 4. Produz `export function Abertas()`, `Negligenciadas()`, `Fechadas()`, `Metas()` e os tipos espelhando os DTOs. Layout **denso** (plano visual, seção 5): KPIs, no máximo dois gráficos e uma tabela principal (ordenável, CSV com a linha de filtros). Metas: "No prazo" e "No prazo ajustado" lado a lado, pílulas `✓`/`●`/`▲` com texto, aviso "data original" quando `alertaDataOriginal`. Negligenciadas: seis listas, com "Sem atualização" e "Paradas no status" separadas.

- [ ] **Passo 1: Testes que falham:** `abertasMostraKpisEFaixas` (4, 2, 1); `negligenciadasDuasListasSeparadas` (TG-101 aparece em "Sem atualização" e em "Paradas"); `fechadasCicloEmUteisEAlternaCorridos` (`unit=corrido` vai para a URL); `metasNoPrazoEAjustadoLadoALado` (`75%` e `100%`); `metasPilulaComSimboloETexto`; `kpiAbreGavetaComMesmoTamanho`; `avisoBloqueanteMostraTraco` ("—"); `semPermissao403MostraMensagem`; `tabelaPorPessoaAlfabeticaSemColunaDePosicao`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Abertas src/screens/Negligenciadas src/screens/Fechadas src/screens/Metas` → FALHA. **Passo 3: Implementar** com TanStack Query e o kit. **Passo 4: Rodar** → PASSA, suíte inteira e `pnpm build`.
- [ ] **Passo 5: Captura de verificação:** uma rodada (Chrome headless, 1440 e 390 px, Claro/Escuro/Sidia, modo demo); corrigir tudo de uma vez; no máximo mais uma rodada. **Passo 6: Commit** `feat(cards): telas Abertas, Negligenciadas, Fechadas e Metas`.

---

### Tarefa 9: Consistência entre telas e diferenças esperadas (bloco 5a)

**Arquivos:** Criar `docs/analise/diferencas-esperadas-trabalho-e-prazos.md` (seção 5a), `cards/ConsistenciaEntreTelasTest.kt`, `cards/CompatLegadoTrabalhoTest.kt`, `docs/DiferencasTrabalhoDocTest.kt` (em `src/test/kotlin/br/sidia/painel/docs/`).

**Interfaces:** Consome os calculadores das Tarefas 5 a 7 e `NoPrazo`/`AuditCalculator` do Plano 4.

- [ ] **Passo 1: Testes que falham:**
  - `mesmoNoPrazoEmTresTelas`: no TRB-1 mais a issue `TG-120` (resolvida `2026-10-16T03:30Z`, vence 15/10), `FechadasDto.noPrazo == MetasDto.noPrazo == NoPrazoResultado.noPrazo` (Auditoria) `== 4`, e as três contam `TG-120` no dia 15/10.
  - `mesmaSemanaEmFechadasETempoEmStatus` (TG-112 na semana de 12/10 nas duas).
  - `CompatLegadoTrabalhoTest` (fórmula do app atual conferida no código de `legacy/backend/jiradash/analytics.py` antes de aceitar): `cicloCorridoComoOLegado` (P50 `6.5` em `CORRIDO`, 1ª entrada em ativo/espera/bloqueado); `faixasDeIdadeComoOLegado` (`0–7/8–14/15–30/>30` em corridos); `metasDeltaComoOLegado` (mesmas faixas D-3..> 3 d).
  - `documentoCitaOsIds`: o documento cita AB-01..AB-09, NG-01..NG-06, FE-01..FE-12, MT-01..MT-14.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*ConsistenciaEntreTelasTest*' --tests '*CompatLegadoTrabalhoTest*' --tests '*DiferencasTrabalhoDocTest*'` → FALHA. **Passo 3: Escrever** o documento: por ID, "igual", "diferença esperada" (com efeito e exemplo do TRB-1) ou "novo": fuso Manaus × UTC em VG/FE (achado 1), dias úteis × corridos (achado 4), concluída por categoria + `resolution` (achado 5), P85 por tipo em AB-04, histórico só do visível, atraso justificado em FE/MT (relatório seção 5), "Sem atualização" e "Paradas" separados (achado 2), horas por pessoa sem worklog até a Fase 6. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(cards): diferenças esperadas e consistência entre telas do bloco 5a`.

---

# Bloco 5b — Negativas, Riscos, Previsão e Épicos

### Tarefa 10: Negativas (domínio e API), eventos pela data do changelog

**Arquivos:** Criar `cards/negativas/{NegativasCalculator.kt,NegativasDto.kt,CardNegativas.kt}`, `app/config/cards/negativas.card.yaml`; Teste `cards/negativas/NegativasCalculatorTest.kt`, `cards/NegativasRoutesTest.kt`.

**Interfaces:**
- Consome: `WorkStore.comEventoEntre`, `Fluxo`, `NoPrazo.classificar`, `StatusMap.indiceNaOrdem`.
- Produz:
  ```kotlin
  @Serializable data class NegativasDto(val reabertas: Int, val devolvidas: Int, val canceladas: Int, val concluidasComAtraso: Int,
      val atrasadasSemJustificativa: Int, val abertasVencidas: Int, val estouroDeEstimativa: Int, val muitasTrocas: Int,
      val concluidas: Int, val taxaReabertura: Double?, val taxaDevolucao: Double?, val taxaAtraso: Double?, val taxaAtrasoSemJustificativa: Double?,
      val porResponsavel: List<NegativaPessoaDto>, val avisos: List<AvisoDto>)
  @Serializable data class NegativaPessoaDto(val pessoa: String, val reabertas: Int, val devolvidas: Int, val atrasadas: Int)   // alfabética
  class NegativasCalculator(cal: BusinessCalendar, map: StatusMap, labels: LabelsDeAtraso, estouro: Double = 1.25, trocas: Int = 3) {
      fun calcular(comEventos: List<IssueSnapshot>, resolvidas: List<IssueSnapshot>, abertas: List<IssueSnapshot>, de: Instant, ate: Instant, visiveis: Set<String>): NegativasDto
  }
  ```
  **Reaberta** = transição de status `DONE` para não `DONE` com `at` em `[de, ate)`. **Devolvida** = transição para status com índice menor na `ordem` (sem ordem configurada → 0 e aviso `ORDEM_DO_FLUXO_AUSENTE`). Canceladas, concluídas com atraso e estouro (`spent_s > 1,25 × estimate_s`) pelas resolvidas no período; muitas trocas = ≥ 3 eventos de `assignee` no período. Taxa = contagem ÷ concluídas positivas do período; `▲` acima de 20%. `porResponsavel` atribui cada evento ao responsável no instante (`AuditTimeline.responsavelEm` via `toAuditIssue`), só visíveis, ordem alfabética.
- [ ] **Passo 1: Testes que falham** (cenário **NEG-1**, período 01/10 a 30/10: TG-131 Closed em 10-08, volta a In Progress em `2026-10-21T12:00Z`, fecha em 10-23 (Done); TG-132 Submitted → In Progress em 10-22, aberta, vence 10-25; TG-133 reaberta em `2026-09-20`, `updated` 10-25; TG-134 trocas `ana→bruno` 10-05, `bruno→carla` 10-06, `carla→ana` 10-07, Done em 10-09; TG-135 status `Closed` com Won't Do em 10-12; TG-136 Done em 10-20, vence 10-16, estimativa 10 h, gasto 14 h; TG-137 Done em 10-21, vence 10-19, `LATE_SENT`):
  - `contagens`: `reabertas=1`, `devolvidas=1`, `canceladas=1`, `concluidasComAtraso=2`, `atrasadasSemJustificativa=1`, `abertasVencidas=1`, `estouroDeEstimativa=1`, `muitasTrocas=1`, `concluidas=4`.
  - `taxas`: `taxaReabertura=0.25`, `taxaDevolucao=0.25`, `taxaAtraso=0.5`, `taxaAtrasoSemJustificativa=0.25`.
  - `reabertaForaDoPeriodoNaoConta` (TG-133).
  - `semOrdemDevolvidasZeroComAviso`; `porResponsavelNoInstanteDoEvento` (reabertura de TG-131 vai para o responsável de 10-21).
  - Rotas: `drillTemOMesmoTamanhoDoCard` (cada contagem); `usuarioVeSoAsProprias`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*NegativasCalculatorTest*' --tests '*NegativasRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Negativas com eventos pela data do changelog`.

---

### Tarefa 11: Riscos (regras em YAML, domínio e API)

**Arquivos:** Criar `riscos/RegrasDeRisco.kt`, `cards/riscos/{RiscoCalculator.kt,RiscosDto.kt,CardRiscos.kt}`, `app/config/risco.exemplo.yaml`, `app/config/cards/riscos.card.yaml`; Modificar `AppContext.kt` (`regrasDeRisco`; fallback `.exemplo` só em dev); Teste `riscos/RegrasDeRiscoTest.kt`, `cards/riscos/RiscoCalculatorTest.kt`, `cards/RiscosRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class FatorDeRisco { IDADE_ACIMA_P85, IDADE_ACIMA_P50, PARADA, VENCIDA, PREVISAO_APOS_VENCIMENTO, VENCE_EM_BREVE,
      BLOQUEADA, REABERTA, MUITAS_TROCAS, ESTOURO_DE_HORAS, SEM_ATUALIZACAO, SEM_ESTIMATIVA }
  data class RegrasDeRisco(val versao: Int, val pesos: Map<FatorDeRisco, Int>, val alto: Int, val medio: Int, val venceEmDias: Int,
      val estouro: Double, val trocas: Int, val diasSemAtualizacao: Int) { companion object { fun fromYaml(path: Path): RegrasDeRisco } }
  data class Risco(val key: String, val pontos: Int, val nivel: String /* alto|medio|baixo */, val fatores: List<Pair<FatorDeRisco, Int>>)
  fun interface PrevisaoDeTermino { fun previsao(key: String): LocalDate? }       // Fase 6 entrega a implementação; aqui sempre null
  class RiscoCalculator(cal: BusinessCalendar, map: StatusMap, regras: RegrasDeRisco, previsao: PrevisaoDeTermino = PrevisaoDeTermino { null }) {
      fun avaliar(abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant): List<Risco>
  }
  @Serializable data class RiscosDto(val versaoRegras: String, val alto: Int, val medio: Int, val linhas: List<LinhaRiscoDto>,
      val sinaisPorPessoa: List<SinaisPessoaDto>, val avisos: List<AvisoDto>)
  @Serializable data class SinaisPessoaDto(val pessoa: String, val issuesEmRisco: Int, val abertas: Int)   // alfabética; proporção, não ranking
  ```
  `risco.exemplo.yaml`: `versao: 1`; pesos `IDADE_ACIMA_P85 30`, `IDADE_ACIMA_P50 10` (exclusivos), `PARADA 15`, `VENCIDA 25`, `PREVISAO_APOS_VENCIMENTO 20`, `VENCE_EM_BREVE 8` (os três de vencimento exclusivos, nessa precedência), `BLOQUEADA 15`, `REABERTA 10`, `MUITAS_TROCAS 10`, `ESTOURO_DE_HORAS 15`, `SEM_ATUALIZACAO 10`, `SEM_ESTIMATIVA 5`; `alto: 60`, `medio: 30`, `venceEmDias: 3` (corridos), `estouro: 1.25`, `trocas: 3`, `diasSemAtualizacao: 7`. Idade comparada com P50/P85 **do próprio tipo**, em dias úteis. Parada = mesma regra de Negligenciadas (só em andamento). Lista do card: só `pontos ≥ medio` (drill `nivel:baixo` existe para conferir). `sinaisPorPessoa` mostra issues em risco **e** abertas da pessoa (proporção), em ordem alfabética.
- [ ] **Passo 1: Testes que falham** (TRB-1):
  - `riscoDoCenario`: TG-101 → `85`, alto (`IDADE_ACIMA_P85 30 + PARADA 15 + VENCIDA 25 + SEM_ATUALIZACAO 10 + SEM_ESTIMATIVA 5`); TG-103 → `63`, alto (`IDADE_ACIMA_P50 10 + PARADA 15 + BLOQUEADA 15 + VENCE_EM_BREVE 8 + ESTOURO_DE_HORAS 15`); TG-102 → `30`, médio (`IDADE_ACIMA_P85 30`; em `Open` não conta como parada); TG-104 → `5`, baixo.
  - `fatoresExclusivos` (vencida não soma "vence em breve"); `previsaoAposVencimentoSoComPrevisao` (stub que devolve 05/11 para TG-103 com vencimento 02/11 → `PREVISAO_APOS_VENCIMENTO 20` no lugar de `VENCE_EM_BREVE`).
  - `regrasEstritas` (chave desconhecida, fator sem peso, `medio ≥ alto` → falha); `producaoSemArquivoRealFalha`.
  - Rotas: `contagens` (`alto=2`, `medio=1`); `drillNivel` (`nivel:alto` → 2 linhas); `versaoDasRegrasNaResposta`; `sinaisPorPessoaAlfabeticos`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegrasDeRiscoTest*' --tests '*RiscoCalculatorTest*' --tests '*RiscosRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Riscos com pesos versionados em YAML`.

---

### Tarefa 12: Previsão e Épicos (Monte Carlo com taxa de chegada)

**Arquivos:** Criar `trabalho/MonteCarlo.kt`, `previsao/RegrasDePrevisao.kt`, `cards/previsao/{PrevisaoCalculator.kt,PrevisaoDto.kt,CardPrevisao.kt}`, `cards/epicos/{EpicosCalculator.kt,EpicosDto.kt,CardEpicos.kt}`, `app/config/previsao.exemplo.yaml`, `app/config/cards/previsao.card.yaml`, `epicos.card.yaml`; Teste `trabalho/MonteCarloTest.kt`, `cards/previsao/PrevisaoCalculatorTest.kt`, `cards/epicos/EpicosCalculatorTest.kt`, `cards/PrevisaoEpicosRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class Quando(val p50: Int?, val p85: Int?, val p95: Int?)          // semanas; null = mais que o limite
  class MonteCarlo(private val random: Random) {
      fun quando(vazao: List<Int>, chegadas: List<Int>?, backlog: Int, sorteios: Int, limiteSemanas: Int): Quando
      fun quantos(vazao: List<Int>, semanas: Int, sorteios: Int): Triple<Int, Int, Int>   // P50, P85 conservador (15º percentil), P95 conservador (5º)
      fun restanteDadaIdade(ciclos: List<Double>, idade: Double, minimo: Int = 5): Pair<Double, Double>?   // P50, P85 de (c − idade) com c > idade
  }
  fun sementeDoDia(dia: LocalDate, filtros: FiltrosDeCard): Long
  data class RegrasDePrevisao(val versao: Int, val semanasHistorico: Int, val sorteios: Int, val limiteSemanas: Int,
      val minSemanasConfianca: Int, val comChegada: Boolean, val minItensEpico: Int) { companion object { fun fromYaml(path: Path): RegrasDePrevisao } }
  @Serializable data class PrevisaoDto(val versaoRegras: String, val backlog: Int, val termina: QuandoDto, val confianca: String,
      val vazaoSemanal: List<SemanaDto>, val chegadaSemanal: List<SemanaDto>, val quantos: List<QuantosDto>,
      val porIssue: List<PrevisaoIssueDto>, val avisos: List<AvisoDto>)
  @Serializable data class QuandoDto(val p50: String?, val p85: String?, val p95: String?, val semanasP50: Int?, val semanasP85: Int?, val semanasP95: Int?)   // datas = segunda da semana alvo
  @Serializable data class EpicosDto(val linhas: List<LinhaEpicoDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaEpicoDto(val epico: String, val itens: Int, val concluidos: Int, val progresso: Double?, val maisAntigo: String?,
      val vencidos: Int, val pontos: Double?, val previsaoSemanasP85: Int?)   // null = "—" (sem amostra própria)
  ```
  `previsao.exemplo.yaml`: `versao: 1`, `semanasHistorico: 16`, `sorteios: 10000`, `limiteSemanas: 104`, `minSemanasConfianca: 8`, `comChegada: true`, `minItensEpico: 5`. Por sorteio e semana: `restante += chegada sorteada − vazão sorteada` até `≤ 0`; sem convergir até o limite conta como `limite + 1` e o percentil acima do limite sai `null` ("mais de 104 semanas"). Confiança: `baixa` com < 8 semanas com dado, `media` < 16, `alta` com 16. Séries de vazão e chegada do **escopo visível** (`Fluxo.throughputSemanal`/`chegadasSemanais`, semanas fechadas antes da atual). Por issue (PV-06): `restanteDadaIdade` dos ciclos do mesmo tipo; colunas "pela capacidade" e "Atrasa?" chegam na Fase 6. Épicos: itens = issues com `epicKey` (exclui tipos de review de `regrasAuditoria.tiposReview`); previsão do épico só com a vazão do próprio épico e ao menos `minItensEpico` concluídos no histórico; sem campo `Epic Link` em nenhuma issue → aviso `EPICO_INDISPONIVEL`.
- [ ] **Passo 1: Testes que falham:**
  - `vazaoConstanteSemChegada`: vazão `[5]×8`, backlog 20, `chegadas=null` → `Quando(4, 4, 4)`.
  - `comChegadaEstendeOPrazo`: vazão `[5]×8`, chegadas `[1]×8` → `Quando(5, 5, 5)`.
  - `chegadaIgualAVazaoNaoConverge`: vazão `[5]`, chegadas `[5]` → `Quando(null, null, null)`.
  - `percentisOrdenados` (vazão `[2, 8]`, semente 42, backlog 20 → `p50 ≤ p85 ≤ p95`, todos entre 2 e 10).
  - `mesmaSementeMesmoResultado`; `sementeDoDiaEstavel` (mesmos filtros e dia → mesma semente; outro dia → outra).
  - `quantosConservador` (vazão `[5]`, 4 semanas → `(20, 20, 20)`).
  - `restanteDadaIdade`: ciclos `[2,4,6,8,10,12]`, idade 3 → `(5.0, 7.8)`; idade 5 → `null` (4 < 5).
  - `confiancaPelaAmostra` (7 semanas → `baixa`; 8 → `media`; 16 → `alta`).
  - `previsaoDoUsuarioSoComAsProprias` (rota: `ana` → `backlog` só das dela, série só das dela).
  - Épicos: `progressoEExclusaoDeReview` (épico com 6 itens não review, 4 concluídos → `progresso = 0.666667`); `semAmostraPropriaTraco` (4 concluídos → `previsaoSemanasP85 == null`); `comAmostraPropria` (8 concluídos, 1 por semana em 8 semanas, 3 abertos, sem chegada → `3`); `semCampoEpicoAviso`.
  - Rotas: `drillBacklogMesmoTamanho`; `versaoDasRegrasNaResposta`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*MonteCarloTest*' --tests '*PrevisaoCalculatorTest*' --tests '*EpicosCalculatorTest*' --tests '*PrevisaoEpicosRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(cards): Previsão com taxa de chegada e Épicos com amostra própria`.

---

### Tarefa 13: Telas Negativas, Riscos, Previsão e Épicos

**Arquivos:** Criar `app/frontend/src/screens/{Negativas,Riscos,Previsao,Epicos}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx`, `i18n/pt.json`, `i18n/en.json`, mock de desenvolvimento.

**Interfaces:** Consome os DTOs das Tarefas 10 a 12 e o kit da Tarefa 4. Riscos: tabela com fator principal e gaveta com os pontos por fator; Previsão: P50/P85/P95 em datas e semanas, "mais de 104 semanas" quando `null`, confiança com texto, séries de vazão e chegada; Épicos: "—" quando sem amostra própria, com legenda.

- [ ] **Passo 1: Testes que falham:** `negativasTaxaAcimaDe20ComTriangulo` (`▲ 25%`); `negativasAtrasadasEAtrasadasSemJustificativa`; `riscosGavetaMostraPontosPorFator` (TG-101: 5 fatores, total 85); `previsaoMaisDeLimite`; `previsaoConfiancaComTexto`; `epicoSemAmostraMostraTraco`; `sinaisPorPessoaAlfabeticosSemPosicao`; `drillMesmoTamanho`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Negativas src/screens/Riscos src/screens/Previsao src/screens/Epicos` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira e `pnpm build`. **Passo 5: Captura de verificação** (uma rodada, 1440 e 390 px, três temas). **Passo 6: Commit** `feat(cards): telas Negativas, Riscos, Previsão e Épicos`.

---

### Tarefa 14: Diferenças esperadas (bloco 5b)

**Arquivos:** Modificar `docs/analise/diferencas-esperadas-trabalho-e-prazos.md` (seção 5b), `cards/CompatLegadoTrabalhoTest.kt`, `docs/DiferencasTrabalhoDocTest.kt`, `HANDOFF.md`.

- [ ] **Passo 1: Testes que falham:** `CompatLegadoTrabalhoTest.monteCarloSemChegadaComoOLegado` (`comChegada=false` reproduz `legacy/backend/jiradash/metrics/forecast.py::monte_carlo_when` para vazão constante: `4` semanas); `riscoComPesosDoLegado` (mesmos pesos e limiares de `metrics/risk.py`, já conferidos no relatório); `restanteDadaIdadeComoOLegado` (`remaining_given_age`, mínimo 5); `documentoCitaOsIds` passa a exigir NE-01..NE-10, RI-01..RI-03, PV-01..PV-06, EP-01.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompatLegadoTrabalhoTest*' --tests '*DiferencasTrabalhoDocTest*'` → FALHA. **Passo 3: Escrever** a seção 5b: eventos pela data do changelog (achado 3, exemplo TG-133), taxa de chegada (achado 6), previsão por épico só com amostra própria (achado 7), P50/P85 do próprio tipo no risco, lista de risco com drill `nivel:baixo` (achado 13), sinais por pessoa com proporção (achado 13), "pela capacidade" pendente da Fase 6. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(cards): diferenças esperadas do bloco 5b`.

---

## Autoavaliação

- **Cobertura da spec e do relatório:** 6.1 (atraso justificado em Metas, Negativas e Fechadas: T6, T7, T10; dicionário como filtro por OS: T4; fuso único e dias úteis: T3; percentis por tipo: T3, T5, T6; previsão com chegada: T12), 4.2 (concluída, eventos pelo changelog, Central com itens separados preparada em Negligenciadas: T5), 5.2 (ficha e JQL por card), 10 (número = lista do drill, mesmo dia em todas as telas: T9; visibilidade por papel em todas as rotas). IDs: AB, NG, FE, MT (5a); NE, RI, PV, EP (5b). Meu painel (MP), Visão geral (VG) e a Central ficam na Fase 7; "pela capacidade" e "Atrasa?" (PV-06) na Fase 6.
- **Consistência de tipos:** `IssueSnapshot` (T2) alimenta `Fluxo` (T3), todos os calculadores (T5 a T12) e, nas Fases 6 e 7, Carga, Esforço, Checklist × Review; `CardDeLeitura`/`rotaDeCard` (T4) servem todas as rotas; `NoPrazo.classificar` (T7) é usado por Fechadas, Metas e Negativas; `PrevisaoDeTermino` (T11) é implementada pela `Agenda` da Fase 6.
- **Pendências deliberadas:** `Story Points`, `Epic Link` e `Flagged` são "a confirmar" na 1ª conexão; sem eles os cards mostram aviso, não zero. A taxa de chegada fica ligada por padrão (decisão da spec 4.2); `comChegada: false` no YAML reproduz o app atual.

## Decisões (25/10, valor padrão recomendado, reversível)

1. **Issues sem responsável** (NG-02 e as abertas sem dono): só coordenador e gerente veem, como no Tempo em status. Mantém a regra única de visibilidade; o Squad Leader fica sem ver a fila sem dono até a Distribuição da Fase 7. Implementado em `Escopo.veSemResponsavel` (T4) e provado por `semResponsavelSoParaCoordenacao` (T4) e `semResponsavelSoCoordenacao` (T5). Para reverter (Squad Leader vendo as sem dono dos tipos do seu squad) seria preciso uma regra nova de squad por tipo/projeto em YAML.
2. **P50/P85 do histórico para o papel USUARIO:** calculado só com as tasks da própria pessoa (não expõe dado de colegas), com o aviso `AMOSTRA_PEQUENA` quando a faixa tem menos de `AMOSTRA_MINIMA = 5` itens (o mesmo mínimo de `restanteDadaIdade`). Implementado em `Fluxo.avisosDeAmostra` (T3) e no escopo visível (T4); provado por `amostraPequenaGeraAviso` (T3), `historicoDoUsuarioSoComAsProprias` (T5) e `previsaoDoUsuarioSoComAsProprias` (T12). Para reverter (squad inteiro, mais estável, mas revela um agregado de colegas) bastaria trocar o escopo do histórico do `USUARIO`.
