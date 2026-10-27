# Fase 6: Pessoas, planejamento e ausências — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Entregar os cards de gestão de pessoas, capacidade e ausências (Carga, Capacidade e ausências, Calendário do time, Plano de 4 a 8 semanas, Vencimentos da semana com folga real, Gargalos e fluxo, Tendência de ocupação, Posse da task, Esforço por task fechada, Cobertura por ausência, Simulador de ausência, Matriz de competências, Revisões pendentes por revisor e Carga por tipo/OS), com ausências e feriados lidos de YAML somente leitura.

**Arquitetura (Architecture):** Três arquivos YAML (equipes, ausências e regras de planejamento) alimentam um motor puro de capacidade: `HorasDaIssue` → `Capacidade` → `Demanda`/`Ocupacao` → `Agenda` (previsão pela capacidade, com e sem ausência). Os cards são `CardDeLeitura` da Fase 5 sobre o `IssueSnapshot` do `WorkStore`; a `Agenda` passa a implementar `PrevisaoDeTermino` (Riscos e Previsão da Fase 5). A sincronização ganha o worklog (sem comentário), que alimenta Esforço e horas registradas.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kaml (YAML estrito), SQLite embutido + Flyway (testes com SQLite real em arquivo temporário), Ktor `MockEngine`; React + TypeScript (Vite, Vitest, Testing Library, TanStack Query).

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 4.2 "Capacidade", "Horas sem estimativa", "Heurísticas", "Esforço por task fechada"; 4.3; 4.4; 5.4; 6.2; 6.3; 8 item 6; 9). Inventário: `docs/analise/relatorio-cards-app-atual.md` (seções 2.2, 3, 4.14 a 4.16, 6 achados 8 a 11, 7.2). Visual: `docs/design/plano-visual-frontend.md` (seções 6.3 e 6.5).

Dividido em dois blocos, cada um um PR que deixa a `main` verde: **6a** (Tarefas 1 a 9: configuração, motor de capacidade, Carga, Capacidade, Calendário, Plano, Vencimentos, Gargalos, Tendência) e **6b** (Tarefas 10 a 16: worklog, Posse, Esforço, Cobertura, Simulador, Competências, Revisões pendentes, Carga por tipo/OS).

## Estado herdado (confirmar na execução)

Antes da Tarefa 1, o executor confere estes nomes na `main` e, se mudaram, atualiza só os nomes deste plano e registra no `progress.md`.

| Nome | Origem |
|---|---|
| `IssueSnapshot`, `WorkStore`, `ConsultaDeTrabalho`, `Situacao`, `toAuditIssue` | Plano 5, Tarefa 2 |
| `Fluxo` (`emAndamento`, `inicioAtivo`, `semanaDe`, `idadeDias`, `faixasPorTipo`) | Plano 5, Tarefa 3 |
| `FiltrosDeCard`, `Escopo`, `escopoDe`, `CardDeLeitura`, `rotaDeCard`, `EntradaDeCard`, `CardPage`, `KpiCard`, `DrillDrawer`, `useCard` | Plano 5, Tarefa 4 |
| `RiscoCalculator`, `PrevisaoDeTermino`, `PrevisaoCalculator` (coluna PV-06) | Plano 5, Tarefas 11 e 12 |
| `StatusMap.ordem` | Plano 5, Tarefa 1 |
| `TimelineBuilder.intervals` (posse por responsável), `TimeInStatusCalculator` (cenário TG-1) | Plano 3 |
| `AuditTimeline.responsavelEm/vencimentoEm`, `RegrasDeAuditoria.tiposReview`, `ctx.regrasAuditoria` | Plano 4, Tarefas 1 e 2 |
| `Syncer.run`, `ChavesDeUsuario.coletar`, `OrcamentoDeRequisicoes`, `SyncGate`, `JiraClientFactory` (construtor `internal` com `MockEngine`), `Retention` (`worklog` por `started`) | Planos 3 e 4A |
| `BusinessCalendar`, `RoleDirectory` (`membersOf`, `squadOf`, `rolesOf`), `Visibility`, `AccessLog`, `testContextComBanco` | Planos 1 e 3 |

## Restrições globais (Global Constraints)

- **Plataforma (adendo de 27/10, `plans/2026-10-27-adendo-plataforma-sqlite-sem-docker.md`):** SQLite embutido, sem Docker e sem servidor de banco; o adendo vence qualquer menção antiga a PostgreSQL, Docker ou Testcontainers. SQL novo segue a tradução da seção 2 do adendo (instantes em `INTEGER` ms UTC, datas em `TEXT` ISO, listas em JSON com `json_each`, helpers de `db/SqlSupport.kt`). Comandos de terminal: Windows 11 PowerShell (principal), por exemplo `cd app\backend; .\gradlew.bat test` e `cd app\frontend; pnpm test`; em `sh`, `cd app/backend && ./gradlew test`. Flags do Jira e de escrita só valem no `app/.env`.
- Nenhuma chamada ao Jira; fixtures e `MockEngine` (`https://jira.exemplo.invalid`). `JIRA_LIVE_ENABLED=false`; nenhuma variável nova começa com `JIRA_`; arquivos que citem variáveis do Jira são escritos com Write/Edit, nunca heredoc. Hook `BLOQUEADO` = parar e relatar.
- **Ausências e feriados:** YAML versionado, **somente leitura**; nenhuma rota grava ausência; a ausência **não tem motivo** (chave `motivo` é recusada na carga). Calendário único do time (`calendario.yaml`).
- Configs reais (`equipes.yaml`, `ausencias.yaml`, `papeis.yaml`) fora do git e da imagem; versionar só `*.exemplo.yaml`; fallback para `.exemplo` só em modo desenvolvimento; em produção o arquivo real é obrigatório (mensagem com o caminho).
- **Capacidade:** horas semanais ÷ 5 × foco × alocação × (1 − fração de ausência); fim de semana e feriado = 0. Capacidade zero aparece como **"sem capacidade"**, nunca 990%; issue vencida sai da semana atual e vira **"atraso acumulado (h)"**.
- **Horas sem estimativa:** restante do Jira → estimativa − registrado (mín. 0,5 h) → horas padrão **por tipo** (YAML) → horas padrão; quando assumidas, a resposta marca `horasAssumidas` e a confiança cai.
- **Posse:** cada pessoa responde pelo tempo em que ficou com a task; métricas por pessoa usam a fração de posse.
- **Sem ranking de pessoas:** toda tabela por pessoa em ordem alfabética; nada de "maior/menor ocupação" nem ordenação por carga; agregados (Jain, ocupação do squad) só sobre o conjunto visível e `null` com menos de 2 pessoas.
- Heurísticas (foco, horas por tipo, limiares, pesos do esforço) em YAML versionado; a versão sai em toda resposta (`versaoRegras`).
- Todo número clicável abre as tasks que o compõem, com o mesmo tamanho; status sem mapeamento → 200 com aviso; toda consulta grava `access_log`.
- Fuso America/Manaus; dias úteis por padrão; semana de segunda a domingo; a semana atual vai de hoje até domingo.
- Cor nunca é o único sinal; pt-BR e inglês; três temas. Migração no próximo número livre. Sem Docker: banco SQLite real em arquivo temporário (`testContextComBanco`/`BancoEmArquivo`); a suíte só exige JDK 21.
- Commits Conventional Commits pt-BR (`feat(pessoas): ...`, `feat(sync): ...`); comentários em pt-BR.

## Foco da revisão (Review Focus)

1. **Semana inteira de ausência com trabalho agendado:** capacidade 0 → "sem capacidade" e horas a replanejar, nunca divisão por zero nem 990% → `semanaSemCapacidade` (T3) e `mapaMostraAusente` (T5).
2. **USUARIO pedindo Carga ou Calendário:** recebe só a própria linha, sem Jain nem capacidade do squad que revelem colegas → `usuarioNaoVeAgregadoDoSquad` (T5) e `calendarioDoUsuarioSoAPropriaLinha` (T6).
3. **Ausência de quem saiu do papéis.yaml** ou com datas invertidas: a carga falha citando o id/linha, sem derrubar com stack trace genérico → `ausenciaDePessoaDesconhecidaFalha` (T1).
4. **Worklog com comentário e autor `JIRAUSER*`:** o comentário nunca chega ao banco nem ao `raw`; o autor é traduzido ou fica como chave, sem exceção → `worklogSemComentario` e `autorJirauserTraduzido` (T10).
5. **Simulador chamado com período longo ou de pessoa fora do visível:** 400/403 e nada é gravado → `simuladorNaoGrava` e `simuladorPessoaForaDoVisivel403` (T13).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  pessoas/Equipes.kt, Ausencias.kt, RegrasDePlanejamento.kt     # YAML somente leitura
  pessoas/HorasDaIssue.kt, Capacidade.kt, Demanda.kt, Agenda.kt  # motor puro
  cards/carga/, capacidade/, calendario/, plano/, vencimentos/, gargalos/, tendencia/   # bloco 6a
  sync/WorklogParser.kt, sync/WorklogStore.kt                    # worklog
  cards/posse/, esforco/, cobertura/, simulador/, competencias/, revisoes/, cargatipo/  # bloco 6b
  pessoas/RegrasDeEsforco.kt
app/backend/src/main/resources/db/migration/V{n}__worklog_por_instancia.sql
app/config/equipes.exemplo.yaml, ausencias.exemplo.yaml, planejamento.exemplo.yaml, esforco.exemplo.yaml
app/config/cards/{carga,capacidade,calendario,plano,vencimentos,gargalos,tendencia,posse,esforco,cobertura,simulador,competencias,revisoes,carga-tipo}.card.yaml
app/frontend/src/screens/{Carga,Capacidade,Calendario,Plano,Vencimentos,Gargalos,Tendencia,Posse,Esforco,Cobertura,Simulador,Competencias,Revisoes,CargaTipo}/
docs/analise/diferencas-esperadas-pessoas-e-planejamento.md
```

**Cenário CAP-1** (relógio `2026-10-30T12:00:00Z`, sexta 30/10; calendário de teste com feriados 02/11/2026 e 20/11/2026; papéis do TRB-1 do Plano 5: `ana` e `bruno` no Alfa, `carla` no Beta). Equipes: `ana` 40 h/sem, foco 0,7, alocação 1,0, competências `[android, rede]`; `bruno` 40 h, alocação 0,5, `[android]`; `carla` 40 h, alocação 1,0, `[rede, ui]`. Ausências: `carla` 02/11 a 06/11 (1,0); `bruno` 10/11 (0,5); `ana` 04/11 a 05/11 (1,0). Capacidade por dia útil: `ana` 5,6 h, `bruno` 2,8 h, `carla` 5,6 h. Issues abertas (todas Checklist): `ana` tem TG-201 (In Progress, restante 11,2 h, vence 04/11, `skill:android`, OS "Android 15") e TG-202 (Open, restante 5,6 h, vence 05/11, `skill:android`, OS "Android 15"); `bruno` tem TG-203 (Open, prioridade High, 4 h, vence 05/11, sem OS); `carla` tem TG-205 (vencida em 28/10, sem estimativa). Horizonte de 4 semanas: semanas de 26/10, 02/11, 09/11 e 16/11.

---

# Bloco 6a — Capacidade, carga e planejamento

### Tarefa 1: Equipes, ausências e regras de planejamento em YAML

**Arquivos:** Criar `pessoas/Equipes.kt`, `pessoas/Ausencias.kt`, `pessoas/RegrasDePlanejamento.kt`, `app/config/equipes.exemplo.yaml`, `app/config/ausencias.exemplo.yaml`, `app/config/planejamento.exemplo.yaml` (todos fictícios, comentados em pt-BR como o `papeis.exemplo.yaml`); Modificar `AppContext.kt` (`equipes`, `ausencias`, `regrasPlanejamento`), `TestContext.kt`, `TestContextComBanco.kt`, `app/README.md` (como copiar os modelos); Teste `pessoas/EquipesTest.kt`, `pessoas/AusenciasTest.kt`, `pessoas/RegrasDePlanejamentoTest.kt`, `AppContextTest.kt`, `pessoas/ArquivosReaisForaDoGitTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class MembroEquipe(val id: String, val horasSemanais: Double, val foco: Double?, val alocacao: Double, val competencias: Set<String>)
  class Equipes(membros: List<MembroEquipe>) {
      fun de(id: String): MembroEquipe?; val todos: List<MembroEquipe>
      companion object { fun fromYaml(path: Path, roles: RoleDirectory): Equipes }
  }
  data class Ausencia(val pessoa: String, val de: LocalDate, val ate: LocalDate, val fracao: Double)   // sem motivo
  class Ausencias(lista: List<Ausencia>) {
      fun fracaoEm(pessoa: String, dia: LocalDate): Double          // 0.0 sem ausência
      fun daPessoa(pessoa: String): List<Ausencia>
      fun entre(de: LocalDate, ate: LocalDate): List<Ausencia>      // que tocam o intervalo
      fun mais(hipotetica: Ausencia): Ausencias                     // para o simulador; não grava nada
      companion object { fun fromYaml(path: Path, roles: RoleDirectory): Ausencias }
  }
  data class RegrasDePlanejamento(val versao: Int, val focoPadrao: Double, val horasMinimas: Double, val horasPorTipo: Map<String, Double>,
      val horasPadrao: Double, val semanasMinimasDemanda: Int, val semanasSemVencimentoIniciada: Int, val semanasSemVencimentoNaoIniciada: Int,
      val ocupacaoAtencao: Double, val ocupacaoAlta: Double, val folga: Double, val tetoJain: Double, val wipPorStatus: Map<String, Int>,
      val wipPorPessoa: Int, val slaRevisaoDias: Int, val diaCriticoCapacidade: Double, val horizonteCoberturaDias: Int) {
      companion object { fun fromYaml(path: Path): RegrasDePlanejamento }
  }
  ```
  `planejamento.exemplo.yaml`: `versao: 1`, `focoPadrao: 0.7`, `horasMinimas: 0.5`, `horasPorTipo: {Checklist: 6.0, "Checklist TG": 6.0, "Checklist Review": 2.0}`, `horasPadrao: 6.0`, `semanasMinimasDemanda: 2`, `semanasSemVencimentoIniciada: 2`, `semanasSemVencimentoNaoIniciada: 4`, `ocupacaoAtencao: 0.85`, `ocupacaoAlta: 1.10`, `folga: 0.50`, `tetoJain: 3.0`, `wipPorStatus: {"In Progress": 12, "Submitted": 5}`, `wipPorPessoa: 3`, `slaRevisaoDias: 2`, `diaCriticoCapacidade: 0.6`, `horizonteCoberturaDias: 30`. Todos estritos (chave desconhecida falha citando arquivo e chave).
- [ ] **Passo 1: Testes que falham:** `carregaEquipesDoExemplo`; `membroForaDoPapeisFalha` (cita o id); `limitesDeEquipe` (`horasSemanais` 0..60, `foco` (0,1], `alocacao` [0,1], competência fora de `[a-z0-9-]`); `ausenciaSemMotivo` (chave `motivo` → falha com "ausências não guardam motivo"); `ausenciaDePessoaDesconhecidaFalha`; `datasInvertidasFalham`; `sobreposicaoDaMesmaPessoaFalha`; `fracaoEm` (CAP-1: `bruno` 10/11 → `0.5`; `ana` 04/11 → `1.0`; `ana` 03/11 → `0.0`); `maisNaoAlteraOOriginal`; `regrasEstritasELimites` (`ocupacaoAtencao < ocupacaoAlta`, `folga < ocupacaoAtencao`); `appContextCarregaComFallbackDeDev`; `producaoSemArquivoRealFalha`; `arquivosReaisNoGitignore` (o `.gitignore` cita `config/equipes.yaml` e `config/ausencias.yaml`).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*EquipesTest*' --tests '*AusenciasTest*' --tests '*RegrasDePlanejamentoTest*' --tests '*AppContextTest*' --tests '*ArquivosReaisForaDoGitTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(pessoas): equipes, ausências e regras de planejamento em YAML somente leitura`.

---

### Tarefa 2: Horas por issue e capacidade líquida

**Arquivos:** Criar `pessoas/HorasDaIssue.kt`, `pessoas/Capacidade.kt`; Teste `pessoas/HorasDaIssueTest.kt`, `pessoas/CapacidadeTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class Horas(val valor: Double, val assumidas: Boolean)
  class HorasDaIssue(private val regras: RegrasDePlanejamento) { fun de(s: IssueSnapshot): Horas }
  class Capacidade(private val equipes: Equipes, private val ausencias: Ausencias, private val cal: BusinessCalendar, private val regras: RegrasDePlanejamento) {
      fun dia(pessoa: String, dia: LocalDate): Double               // 0 sem cadastro em equipes
      fun brutaDia(pessoa: String, dia: LocalDate): Double          // sem descontar ausência (para "% da capacidade do squad")
      fun periodo(pessoa: String, de: LocalDate, ate: LocalDate): Double   // inclusivo
      fun semanas(pessoa: String, hoje: LocalDate, n: Int): List<Pair<LocalDate, Double>>   // semana atual de hoje até domingo
  }
  ```
  Ordem das horas: `remainingS` → `estimateS − spentS` com mínimo `horasMinimas` → `horasPorTipo[issueType]` (assumidas) → `horasPadrao` (assumidas).
- [ ] **Passo 1: Testes que falham:** `restanteDoJira` (`remainingS=7200` → `Horas(2.0, false)`); `estimativaMenosGastoComMinimo` (10 h e 9,8 h → `0.5`); `estimativaMenosGasto` (10 h e 4 h → `6.0`); `porTipoAssumidas` (Checklist sem estimativa → `Horas(6.0, true)`; Checklist Review → `2.0`); `tipoDesconhecidoUsaPadrao`; CAP-1: `capacidadeDoDia` (`ana` 03/11 → `5.6`; 02/11 feriado → `0.0`; 31/10 sábado → `0.0`; 04/11 ausente → `0.0`; `bruno` 10/11 → `1.4`); `focoPadraoQuandoNulo`; `semanaAtualDeHojeAteDomingo` (`ana` semana de 26/10 → `5.6`); `semanaComFeriadoEAusencia` (`ana` semana de 02/11 → `11.2`; semana de 09/11 → `28.0`; semana de 16/11 → `22.4`); `semanaSemCapacidade` (`carla` semana de 02/11 → `0.0`); `semCadastroNaEquipeEhZero`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*HorasDaIssueTest*' --tests '*CapacidadeTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): horas por issue e capacidade líquida`.

---

### Tarefa 3: Demanda semanal, ocupação, atraso acumulado e índice de Jain

**Arquivos:** Criar `pessoas/Demanda.kt`; Teste `pessoas/DemandaTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  sealed interface Ocupacao { data class Valor(val fracao: Double) : Ocupacao; data object SemCapacidade : Ocupacao }
  data class SemanaDeCarga(val semana: LocalDate, val demandaH: Double, val capacidadeH: Double, val ocupacao: Ocupacao)
  data class CargaDaPessoa(val pessoa: String, val semanas: List<SemanaDeCarga>, val atrasoAcumuladoH: Double, val horasAssumidas: Boolean,
      val ocupacaoHorizonte: Ocupacao)
  class Demanda(private val horas: HorasDaIssue, private val cap: Capacidade, private val map: StatusMap, private val regras: RegrasDePlanejamento) {
      fun carga(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate, semanas: Int): CargaDaPessoa
  }
  object Jain { fun indice(ocupacoes: List<Ocupacao>, teto: Double): Double? }   // ignora SemCapacidade; null com < 2 valores
  ```
  Demanda: horas da issue espalhadas por igual da semana atual até a semana do vencimento, no mínimo `semanasMinimasDemanda` semanas; sem vencimento: 2 semanas se iniciada (`Fluxo.inicioAtivo`), 4 se não; **vencida (`duedate < hoje`) não entra nas semanas: soma em `atrasoAcumuladoH`**. Ocupação = demanda ÷ capacidade; capacidade 0 → `SemCapacidade` (com a demanda mostrada).
- [ ] **Passo 1: Testes que falham** (CAP-1, 4 semanas):
  - `espalhaAteOVencimento`: TG-201 (11,2 h, vence 04/11) → `5.6` nas semanas de 26/10 e 02/11; TG-202 (5,6 h, vence 05/11) → `2.8` em cada uma.
  - `ocupacaoDaSemana`: `ana` semana 26/10 → demanda `8.4`, capacidade `5.6`, `Valor(1.5)`; semana 02/11 → `Valor(0.75)`.
  - `ocupacaoDoHorizonte`: `16.8 / 67.2 = Valor(0.25)`.
  - `vencidaViraAtrasoAcumulado`: TG-205 (`carla`) → `atrasoAcumuladoH = 6.0`, `horasAssumidas = true`, fora das semanas.
  - `semanaSemCapacidade`: demanda de 3 h para `carla` na semana de 02/11 → `SemCapacidade` com `demandaH = 3.0`.
  - `semVencimentoIniciadaDuasSemanas` e `naoIniciadaQuatroSemanas`.
  - `jain`: `[Valor(0.5), Valor(1.0)]` → `0.9`; com `SemCapacidade` no meio → igual; um só valor → `null`; `Valor(5.0)` limitado a `3.0`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DemandaTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): demanda, ocupação sem sentinela, atraso acumulado e Jain`.

---

### Tarefa 4: Agenda (previsão pela capacidade) e ligação com Riscos e Previsão

**Arquivos:** Criar `pessoas/Agenda.kt`; Modificar `cards/riscos/CardRiscos.kt` e `cards/previsao/PrevisaoCalculator.kt` (Plano 5: passam a receber a `Agenda`; PV-06 ganha "pela capacidade" e "Atrasa?"); Teste `pessoas/AgendaTest.kt`, `cards/riscos/RiscoCalculatorTest.kt` e `cards/previsao/PrevisaoCalculatorTest.kt` (acrescentar).

**Interfaces:**
- Produz:
  ```kotlin
  data class Agendamento(val key: String, val pessoa: String, val termino: LocalDate?, val vencimento: LocalDate?, val atrasa: Boolean,
      val horasPorDia: Map<LocalDate, Double>)        // termino null = não cabe no horizonte
  class Agenda(private val horas: HorasDaIssue, private val cap: Capacidade, private val map: StatusMap, private val horizonteDias: Int = 120) {
      fun agendar(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate): List<Agendamento>
      fun comparar(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate, sem: Capacidade): List<Pair<Agendamento, Agendamento>>   // (atual, sem a ausência)
      fun comoPrevisao(abertas: List<IssueSnapshot>, hoje: LocalDate): PrevisaoDeTermino
  }
  ```
  Fila da pessoa: primeiro as já iniciadas, depois prioridade (ordem do dicionário `priority`; desconhecida no fim), vencimento, criação, chave natural. Cada dia útil consome a capacidade líquida do dia, começando hoje. Vencidas entram na fila (o atraso continua visível na previsão).
- [ ] **Passo 1: Testes que falham** (CAP-1): `filaNaOrdem` (iniciada TG-201 antes de TG-202; entre não iniciadas, prioridade, vencimento, criação); `terminoPelaCapacidade`: TG-201 → `2026-11-03` (30/10 5,6 h + 03/11 5,6 h), TG-202 → `2026-11-06`, `atrasa=true` (vence 05/11); `semAusenciaNaoAtrasa` (`comparar` com capacidade sem ausências: TG-202 → `2026-11-04`, `atrasa=false`); `naoCabeNoHorizonte` (`termino == null`); `pessoaSemCapacidadeNaoQuebra`; `previsaoAposVencimentoNoRisco` (TG-202 recebe `PREVISAO_APOS_VENCIMENTO 20`); `previsaoPorIssuePelaCapacidade` (PV-06 de TG-202 → `pelaCapacidade="2026-11-06"`, `atrasa=true`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AgendaTest*' --tests '*RiscoCalculatorTest*' --tests '*PrevisaoCalculatorTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): agenda pela capacidade e previsão ligada a Riscos e Previsão`.

---

### Tarefa 5: Carga por pessoa e Capacidade e ausências (API)

**Arquivos:** Criar `cards/carga/{CargaCalculator.kt,CargaDto.kt,CardCarga.kt}`, `cards/capacidade/{CapacidadeCardCalculator.kt,CapacidadeDto.kt,CardCapacidade.kt}`, `app/config/cards/carga.card.yaml`, `capacidade.card.yaml`; Modificar `Application.kt`; Teste `cards/carga/CargaCalculatorTest.kt`, `cards/capacidade/CapacidadeCardCalculatorTest.kt`, `cards/CargaCapacidadeRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class OcupacaoDto(val fracao: Double?, val semCapacidade: Boolean, val estado: String)   // ok | atencao | problema
  @Serializable data class CargaDto(val versaoRegras: String, val jain: Double?, val acimaDaCapacidade: Int, val comFolga: Int,
      val linhas: List<LinhaCargaDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaCargaDto(val pessoa: String, val abertas: Int, val wip: Int, val filaH: Double, val capacidade4SemH: Double,
      val ocupacao: OcupacaoDto, val ausenciaDias: Double, val horasRegistradas30d: Double?, val acimaDoP85: Int, val vencidas: Int,
      val riscoAlto: Int, val atrasoAcumuladoH: Double, val horasAssumidas: Boolean)   // ordem alfabética
  @Serializable data class CapacidadeDto(val versaoRegras: String, val alertas: List<AlertaCapacidadeDto>, val atrasosPrevistos: Int,
      val atrasosPorAusencia: Int, val jain: Double?, val mapa: List<LinhaMapaDto>, val ausencias: List<AusenciaDto>,
      val feriados: List<FeriadoDto>, val listaAtrasos: List<AtrasoPrevistoDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaMapaDto(val pessoa: String, val semanas: List<CelulaMapaDto>)
  @Serializable data class CelulaMapaDto(val semana: String, val ocupacao: OcupacaoDto, val ausente: Boolean)   // "aus." quando SemCapacidade por ausência
  @Serializable data class AlertaCapacidadeDto(val pessoa: String, val tipo: String /* CARGA_ALTA | AUSENCIA_COM_TRABALHO */, val semana: String, val horasAReplanejar: Double?)
  @Serializable data class AusenciaDto(val pessoa: String, val de: String, val ate: String, val fracao: Double)   // sem motivo
  @Serializable data class AtrasoPrevistoDto(val key: String, val pessoa: String, val vence: String, val termino: String?, val causa: String /* AUSENCIA | CARGA */)
  ```
  Carga (CP-01..03): `acimaDaCapacidade` (> `ocupacaoAlta`) e `comFolga` (< `folga`) substituem "maior/menor ocupação"; `horasRegistradas30d` fica `null` até a Tarefa 10. Capacidade (CA-01..09): mapa de 8 semanas; alerta `AUSENCIA_COM_TRABALHO` quando a semana tem ≥ 2 dias de ausência e trabalho agendado (horas a replanejar = horas agendadas nesses dias no cenário sem ausência); causa `AUSENCIA` quando o atraso some no cenário sem ausência. Visibilidade: linhas só de pessoas visíveis; `jain` e contagens só sobre o visível; Carga para todos os papéis (USUARIO vê a própria linha), Capacidade só liderança.
- [ ] **Passo 1: Testes que falham** (CAP-1): `linhaDaAna` (`abertas=2`, `filaH=16.8`, `capacidade4SemH=67.2`, ocupação `0.25` `ok`, `ausenciaDias=2.0`, `atrasoAcumuladoH=0.0`); `linhaDaCarla` (`atrasoAcumuladoH=6.0`, `horasAssumidas=true`); `linhasAlfabeticasSemPosicao`; `contagensNoLugarDeExtremos`; `usuarioNaoVeAgregadoDoSquad` (`ana`: 1 linha, `jain == null`); `mapaMostraAusente` (`carla` semana 02/11 → `semCapacidade=true`, `ausente=true`); `alertaAusenciaComTrabalho` (`ana` semana 02/11, `horasAReplanejar=5.6`); `atrasoPorAusencia` (TG-202, causa `AUSENCIA`); `ausenciasSemMotivoNaResposta` (o JSON não tem chave `motivo`); `capacidadeSoLideranca` (USUARIO → 403); `drillTemOMesmoTamanhoDoCard` (`acimaDaCapacidade`, `atrasosPrevistos`, `pessoa:ana:fila`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CargaCalculatorTest*' --tests '*CapacidadeCardCalculatorTest*' --tests '*CargaCapacidadeRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): cards Carga por pessoa e Capacidade e ausências`.

---

### Tarefa 6: Calendário do time, Plano de 4 a 8 semanas e Vencimentos da semana (API)

**Arquivos:** Criar `cards/calendario/{CalendarioCalculator.kt,CalendarioDto.kt,CardCalendario.kt}`, `cards/plano/{PlanoCalculator.kt,PlanoDto.kt,CardPlano.kt}`, `cards/vencimentos/{VencimentosCalculator.kt,VencimentosDto.kt,CardVencimentos.kt}` e as fichas; Teste `cards/calendario/CalendarioCalculatorTest.kt`, `cards/plano/PlanoCalculatorTest.kt`, `cards/vencimentos/VencimentosCalculatorTest.kt`, `cards/CalendarioPlanoVencimentosRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class ModoCalendario { SEMANA, MES }
  @Serializable data class CalendarioDto(val inicio: String, val dias: List<DiaDto>, val linhas: List<LinhaCalendarioDto>, val avisos: List<AvisoDto>)
  @Serializable data class DiaDto(val dia: String, val feriado: String?, val util: Boolean, val capacidadeSquad: Double?, val vencimentos: Int, val critico: Boolean)
  @Serializable data class LinhaCalendarioDto(val pessoa: String, val celulas: List<CelulaCalendarioDto>)   // alfabética
  @Serializable data class CelulaCalendarioDto(val dia: String, val ausencia: Double, val vencimentos: Int)
  @Serializable data class PlanoDto(val semanas: List<String>, val linhas: List<LinhaPlanoDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaPlanoDto(val pessoa: String, val celulas: List<CelulaPlanoDto>)
  @Serializable data class CelulaPlanoDto(val semana: String, val planejadaH: Double, val capacidadeH: Double, val ocupacao: OcupacaoDto, val itens: Int, val semDataH: Double)
  @Serializable data class VencimentosDto(val dias: Int, val linhas: List<VencimentoDto>, val riscoDeAtrasar: Int, val avisos: List<AvisoDto>)
  @Serializable data class VencimentoDto(val key: String, val resumo: String, val pessoa: String?, val vence: String, val termino: String?, val folgaDiasUteis: Double?, val estado: String)
  ```
  Calendário: `inicio=aaaa-mm-dd` (padrão: segunda da semana atual), `modo=semana|mes`; `capacidadeSquad` = soma da capacidade líquida ÷ soma da bruta das pessoas visíveis no dia (`null` em dia não útil); `critico` = dia útil com vencimentos e `capacidadeSquad < diaCriticoCapacidade`; ausência sem motivo. Plano: `semanas=4..8` (padrão 4; fora → 400), planejada = demanda da Tarefa 3, `semDataH` = parte vinda de issues sem vencimento. Vencimentos: `dias=7|14`, folga real = dias úteis entre o término previsto pela `Agenda` e o vencimento (negativa → `problema` ▲ "risco de atrasar"; `termino == null` → `problema`).
- [ ] **Passo 1: Testes que falham** (CAP-1): `calendarioSemana` (semana de 02/11: 02/11 `feriado="Finados"` e `util=false`; `carla` `ausencia=1.0` de 02 a 06; `ana` 04 e 05 `1.0`; `ana` 04/11 `vencimentos=1`); `capacidadeDoSquadNoDia` (04/11 com `ana` e `carla` ausentes e `bruno` presente, visíveis os três → `2.8 / 14.0 = 0.2`, `critico=true`); `calendarioDoUsuarioSoAPropriaLinha` (`ana`: 1 linha; `capacidadeSquad` só dela); `planoQuatroSemanas` (`ana` semana 26/10 → `planejadaH=8.4`, `capacidadeH=5.6`); `semanasForaDoIntervalo400` (3 e 9); `vencimentosSeteDias` (TG-201 folga `1.0` útil (`termino` 03/11, vence 04/11), TG-202 folga `−1.0` → `riscoDeAtrasar` conta, TG-203 `ok`); `drillTemOMesmoTamanhoDoCard` (`dia:2026-11-04`, `riscoDeAtrasar`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CalendarioCalculatorTest*' --tests '*PlanoCalculatorTest*' --tests '*VencimentosCalculatorTest*' --tests '*CalendarioPlanoVencimentosRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Calendário do time, Plano de 4 a 8 semanas e Vencimentos da semana`.

---

### Tarefa 7: Gargalos e fluxo e Tendência de ocupação (API)

**Arquivos:** Criar `cards/gargalos/{GargalosCalculator.kt,GargalosDto.kt,CardGargalos.kt}`, `cards/tendencia/{TendenciaCalculator.kt,TendenciaDto.kt,CardTendencia.kt}` e as fichas; Teste `cards/gargalos/GargalosCalculatorTest.kt`, `cards/tendencia/TendenciaCalculatorTest.kt`, `cards/GargalosTendenciaRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class GargalosDto(val dias: List<String>, val faixas: List<FaixaFluxoDto>, val etapas: List<EtapaDto>,
      val acimaDaCapacidade: Int, val comFolga: Int, val avisos: List<AvisoDto>)
  @Serializable data class FaixaFluxoDto(val status: String, val contagens: List<Int>)          // fluxo cumulativo, 60 dias
  @Serializable data class EtapaDto(val status: String, val categoria: String, val wip: Int, val limite: Int?, val crescimento15d: Int, val gargalo: Boolean, val motivo: String?)  // WIP_ACIMA_DO_LIMITE | FAIXA_CRESCEU
  @Serializable data class TendenciaDto(val semanas: List<String>, val linhas: List<LinhaTendenciaDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaTendenciaDto(val pessoa: String, val ocupacoes: List<OcupacaoDto>, val padrao: String /* RECORRENTE | PONTUAL | NORMAL */)
  ```
  Fluxo cumulativo: para cada dia de Manaus dos últimos 60, quantas issues estavam em cada status no fim do dia (reconstruído da linha do tempo), na ordem do fluxo. Gargalo (GA-02): só categorias ACTIVE e WAIT; `wip > wipPorStatus[status]` **ou** crescimento da faixa em 15 dias `≥ max(3, 30% do valor de 15 dias atrás)`. Tendência: 12 semanas; em cada segunda passada reconstrói abertas, responsável e vencimento daquele instante (`toAuditIssue` + `AuditTimeline.responsavelEm/vencimentoEm`) e aplica a `Demanda` com as **horas atuais** (aviso `HORAS_ATUAIS_NO_PASSADO`); `RECORRENTE` = ≥ 3 das últimas 4 semanas acima de `ocupacaoAlta`; `PONTUAL` = 1 ou 2.
- [ ] **Passo 1: Testes que falham:** `faixaCresceuViraGargalo` (In Progress de 5 para 9 em 15 dias → gargalo `FAIXA_CRESCEU`; de 10 para 12 → não); `wipAcimaDoLimite` (13 com limite 12 → `WIP_ACIMA_DO_LIMITE`); `etapaTodoNuncaEhGargalo`; `fluxoCumulativoReconstruido` (issue que passou para Submitted em 20/10 conta em In Progress em 19/10 e em Submitted em 20/10); `tendenciaUsaResponsavelDaEpoca` (task de `bruno` até 12/10 e de `ana` depois entra na carga de `bruno` na semana de 05/10); `padraoRecorrente` (`[1.2, 1.3, 0.9, 1.15]` → `RECORRENTE`; `[1.2, 0.5, 0.6, 0.7]` → `PONTUAL`); `avisoDeHorasAtuais`; `tendenciaSoLideranca` (USUARIO → 403); `pessoasAlfabeticas`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*GargalosCalculatorTest*' --tests '*TendenciaCalculatorTest*' --tests '*GargalosTendenciaRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Gargalos e fluxo e Tendência de ocupação`.

---

### Tarefa 8: Telas do bloco 6a

**Arquivos:** Criar `app/frontend/src/screens/{Carga,Capacidade,Calendario,Plano,Vencimentos,Gargalos,Tendencia}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx`, `i18n/pt.json`, `i18n/en.json`, mock de desenvolvimento.

**Interfaces:** Consome os DTOs das Tarefas 5 a 7 e o kit do Plano 5. Layout **denso**; Calendário conforme o wireframe 6.3 (▓ ausência, ◆n vencimentos, ▲ dia crítico, legenda em texto; clicar numa célula abre a gaveta com os vencimentos do dia); ocupação sempre com número e símbolo; "sem capacidade" como texto, nunca porcentagem.

- [ ] **Passo 1: Testes que falham:** `cargaSemCapacidadeEmTexto`; `cargaSemColunaDePosicao`; `cargaUsuarioUmaLinhaSemJain`; `mapaAusenteMostraAus`; `calendarioLegendaComSimbolos`; `calendarioCelulaAbreGaveta` (linhas == `vencimentos`); `planoAlternaQuatroAOitoSemanas` (`semanas=8` na URL); `vencimentosFolgaNegativaComTriangulo`; `gargaloComMotivoEmTexto`; `tendenciaPadraoRecorrenteEmTexto`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Carga src/screens/Capacidade src/screens/Calendario src/screens/Plano src/screens/Vencimentos src/screens/Gargalos src/screens/Tendencia` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira e `pnpm build`. **Passo 5: Captura de verificação** (uma rodada, 1440 e 390 px, três temas). **Passo 6: Commit** `feat(pessoas): telas de carga, capacidade, calendário, plano, vencimentos, gargalos e tendência`.

---

### Tarefa 9: Diferenças esperadas (bloco 6a)

**Arquivos:** Criar `docs/analise/diferencas-esperadas-pessoas-e-planejamento.md` (seção 6a), `pessoas/CompatLegadoCapacidadeTest.kt`, `docs/DiferencasPessoasDocTest.kt`.

- [ ] **Passo 1: Testes que falham:** `capacidadeDoDiaComoOLegado` (40 h, foco 0,7, alocação 1,0 → `5.6`, igual a `legacy/backend/jiradash/metrics/capacity.py::day_capacity_h`); `horasMinimasComoOLegado` (`0.5`); `jainComoOLegado` (mesma fórmula e teto 3,0); `documentoCitaOsIds` (CP-01..CP-03, CA-01..CA-09, GA-01..GA-04 e os cards novos Calendário, Plano, Vencimentos, Tendência).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompatLegadoCapacidadeTest*' --tests '*DiferencasPessoasDocTest*'` → FALHA. **Passo 3: Escrever** a seção 6a: "sem capacidade" no lugar de 990% (achado 8), vencidas viram atraso acumulado (achado 9), horas por tipo no lugar de 6 h e pontos × 4 h (achado 10), semana atual de hoje a domingo, CP-02 trocado por contagens (sem ranking), agregados só do visível, tendência com horas atuais. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(pessoas): diferenças esperadas do bloco 6a`.

---

# Bloco 6b — Worklog, posse, esforço, cobertura e competências

### Tarefa 10: Sincronização do worklog (sem comentário)

**Arquivos:** Criar `sync/WorklogParser.kt`, `sync/WorklogStore.kt`, `resources/db/migration/V{n}__worklog_por_instancia.sql` (`{n}` = próxima migração livre, hoje V2; nunca editar a V1; instantes em `INTEGER` ms, datas em `TEXT` ISO), `src/test/resources/fixtures/dc/search-com-worklog.json`, `fixtures/dc/worklog-tg-301-completo.json`; Modificar `jira/JiraClient.kt` (`worklogs`), `sync/Syncer.kt`, `sync/IssueStore.kt` (grava worklogs na mesma transação; `raw` sem o campo `worklog`), `app/config/cards/sincronizacao.card.yaml` (campos `worklog` e `timespent`), `cards/carga/CargaCalculator.kt` (`horasRegistradas30d`), `docs/jira/checklist-primeira-conexao.md` e `docs/jira/matriz-compatibilidade.md` (endpoint de worklog "não validado"); Teste `sync/WorklogParserTest.kt`, `sync/SyncerTest.kt`, `sync/WorklogStoreTest.kt`, `jira/JiraClientTest.kt`.

**Interfaces:**
- Migração: `worklog` ganha `instance text`, `jira_id text`, índice único `(instance, jira_id)`; linhas antigas sem `jira_id` ficam (nenhuma existe hoje).
- Produz:
  ```kotlin
  suspend fun JiraClient.worklogs(issueKey: String): List<JsonObject>      // GET /rest/api/2/issue/{key}/worklog, paginado (startAt/maxResults/total); LiveGuard e orçamento em toda tentativa
  data class WorklogRecord(val jiraId: String, val autor: String?, val started: Instant, val seconds: Long)   // sem comentário
  object WorklogParser { fun toRecords(worklogs: List<JsonObject>, chaves: Map<String, String>): List<WorklogRecord> }
  interface WorklogStore {
      fun porIssue(instance: String, keys: Set<String>): Map<String, List<WorklogRecord>>
      fun horasPorAutor(instance: String, de: Instant, ate: Instant): Map<String, Double>
  }
  class SqliteWorklogStore(private val ds: DataSource) : WorklogStore
  ```
  O `Syncer` lê `fields.worklog` da busca; se `total > worklogs.size`, chama `worklogs(key)`. Ao gravar a issue, os worklogs dela são **substituídos** (o que sumiu no Jira sai). Autor por `name` normalizado; `JIRAUSER*` traduzido por `ChavesDeUsuario`. `comment`, `updateAuthor` e `self` nunca são lidos.
- [ ] **Passo 1: Testes que falham:** `worklogSemComentario` (fixture com `comment` → nenhuma coluna nem `raw` contém o texto); `rawNaoGuardaWorklog`; `autorJirauserTraduzido`; `worklogIdempotente` (duas sincronizações → mesma contagem); `worklogTruncadoBuscaCompleto` (o `MockEngine` recebe `/issue/TG-301/worklog`, 2 páginas); `worklogRemovidoNoJiraSaiNaRessincronizacao`; `liveDesligadoNaoBuscaWorklog` (fábrica com live `false`: `JiraLiveDisabledException` e 0 requisições); `orcamentoContaRequisicaoDeWorklog`; `retencaoApagaWorklogAntigo` (já coberto pelo `Retention`; acrescentar caso com `instance`); `horasRegistradas30dNaCarga` (`ana` 10 h nos últimos 30 dias → `10.0`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*WorklogParserTest*' --tests '*SyncerTest*' --tests '*WorklogStoreTest*' --tests '*JiraClientTest*' --tests '*CargaCalculatorTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa (incluindo `SomenteAFabricaCriaClientesJira` e `sh .claude/hooks/test-guard.sh`). **Passo 5: Commit** `feat(sync): worklog por instância, sem comentário e idempotente`.

---

### Tarefa 11: Posse da task (domínio e API)

**Arquivos:** Criar `cards/posse/{PosseCalculator.kt,PosseDto.kt,CardPosse.kt}`, `app/config/cards/posse.card.yaml`; Teste `cards/posse/PosseCalculatorTest.kt`, `cards/PosseRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class PosseDePessoa(val pessoa: String?, val dias: Double, val fracao: Double, val trechos: Int)
  object PosseCalculator {
      fun posse(s: IssueSnapshot, ate: Instant, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode): List<PosseDePessoa>   // alfabética; null = sem responsável
  }
  @Serializable data class PosseDto(val tasks: List<PosseTaskDto>, val pessoas: List<PossePessoaDto>, val unit: String, val avisos: List<AvisoDto>)
  @Serializable data class PosseTaskDto(val key: String, val resumo: String, val transferencias: Int, val posse: List<FracaoPosseDto>)
  @Serializable data class FracaoPosseDto(val pessoa: String?, val dias: Double, val fracao: Double)
  @Serializable data class PossePessoaDto(val pessoa: String, val tasks: Int, val dias: Double, val recebidas: Int, val repassadas: Int)   // alfabética
  ```
  Tempo de posse até a resolução (ou agora, se aberta), fora dos status `DONE`, na unidade pedida; usa `TimelineBuilder.intervals`. Período padrão 90 dias pela resolução, mais as abertas. Pessoa fora do visível aparece como `null` ("outra pessoa") na fração, nunca pelo nome.
- [ ] **Passo 1: Testes que falham:** `posseDoTG1` (cenário TG-1 do Plano 3, úteis: `ana` `3.0` e fração `0.642857`, `bruno` `1.666667` e `0.357143`, 1 transferência); `semResponsavelVemNulo`; `voltaAQuemJaTeveSomaTrechos` (`trechos=2`); `posseEmCorridos`; `pessoaInvisivelViraNula` (`ana` consultando vê `bruno` como `null`); `pessoasAlfabeticas`; `drillTemOMesmoTamanhoDoCard` (`pessoa:ana` == `tasks` dela).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PosseCalculatorTest*' --tests '*PosseRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Posse da task por pessoa`.

---

### Tarefa 12: Esforço por task fechada (domínio e API)

**Arquivos:** Criar `pessoas/RegrasDeEsforco.kt`, `cards/esforco/{EsforcoCalculator.kt,EsforcoDto.kt,CardEsforco.kt}`, `app/config/esforco.exemplo.yaml`, `app/config/cards/esforco.card.yaml`; Modificar `AppContext.kt` (`regrasEsforco`); Teste `pessoas/RegrasDeEsforcoTest.kt`, `cards/esforco/EsforcoCalculatorTest.kt`, `cards/EsforcoRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class RegrasDeEsforco(val versao: Int, val pesoPorTipo: Map<String, Double>, val pesoPadrao: Double, val prefixoTeste: String,
      val pesoTeste: Double, val fatorHora: Double) { companion object { fun fromYaml(path: Path): RegrasDeEsforco } }
  data class EsforcoTask(val key: String, val pesoTipo: Double, val testes: Int, val horas: Double, val indice: Double, val horasDoCampo: Boolean)
  class EsforcoCalculator(private val regras: RegrasDeEsforco) {
      fun daTask(s: IssueSnapshot, worklogs: List<WorklogRecord>): EsforcoTask
      fun porPessoa(tasks: List<Pair<EsforcoTask, List<PosseDePessoa>>>): Map<String, EsforcoTask>   // componentes × fração de posse
  }
  @Serializable data class EsforcoDto(val versaoRegras: String, val tasks: List<EsforcoTaskDto>, val pessoas: List<EsforcoPessoaDto>,
      val periodos: List<EsforcoPeriodoDto>, val avisos: List<AvisoDto>)   // pessoas alfabéticas; períodos por semana ou mês
  ```
  `esforco.exemplo.yaml`: `versao: 1`, `pesoPorTipo: {Checklist: 3.0, "Checklist TG": 3.0, "Checklist Review": 1.0, "Feature check": 2.0}`, `pesoPadrao: 1.0`, `prefixoTeste: "TEST-"`, `pesoTeste: 0.5`, `fatorHora: 1.0`. Índice = `pesoTipo + testes × pesoTeste + horas × fatorHora` (proposta inicial da spec 9; calibração depois). Testes = labels distintas que começam com o prefixo, sem diferenciar maiúsculas. Horas = soma do worklog; sem worklog e com `spentS > 0` → `spentS` com `horasDoCampo=true` e aviso `HORAS_DO_CAMPO_TIMESPENT`. Só concluídas positivas no período (padrão 30 dias, `granularidade=semana|mes`).
- [ ] **Passo 1: Testes que falham:** `indiceDaTask` (TG-1 Checklist com `TEST-A`, `test-b`, `TEST-C`, `login` e worklogs de 6 h + 4 h → `pesoTipo=3.0`, `testes=3`, `horas=10.0`, `indice=14.5`); `porPessoaPelaPosse` (`ana` `9.321429`, `bruno` `5.178571`, tolerância 1e-6); `semWorklogUsaTimespentComAviso`; `tipoSemPesoUsaPadrao`; `labelRepetidaContaUmaVez`; `regrasEstritas`; `versaoNaResposta` (`"esforço v1"`); `periodoPorMes`; `pessoasAlfabeticas`; `usuarioVeSoOProprio`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*RegrasDeEsforcoTest*' --tests '*EsforcoCalculatorTest*' --tests '*EsforcoRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Esforço por task fechada com índice composto em YAML`.

---

### Tarefa 13: Cobertura por ausência e Simulador de ausência (API)

**Arquivos:** Criar `cards/cobertura/{CoberturaCalculator.kt,CoberturaDto.kt,CardCobertura.kt}`, `cards/simulador/{SimuladorCalculator.kt,SimuladorDto.kt,CardSimulador.kt}` e as fichas; Teste `cards/cobertura/CoberturaCalculatorTest.kt`, `cards/simulador/SimuladorCalculatorTest.kt`, `cards/CoberturaSimuladorRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class CoberturaDto(val ausencias: List<CoberturaAusenciaDto>, val avisos: List<AvisoDto>)
  @Serializable data class CoberturaAusenciaDto(val pessoa: String, val de: String, val ate: String, val issues: List<String>,
      val horasAReplanejar: Double, val competencias: List<String>, val podemCobrir: List<CandidatoDto>)
  @Serializable data class CandidatoDto(val pessoa: String, val folgaH: Double, val temTodasAsCompetencias: Boolean)   // alfabética
  @Serializable data class SimuladorDto(val pessoa: String, val de: String, val ate: String, val fracao: Double, val atrasosCausados: List<String>,
      val horasAReplanejar: Double, val semanas: List<ComparacaoSemanaDto>, val avisos: List<AvisoDto>)
  @Serializable data class ComparacaoSemanaDto(val semana: String, val antes: OcupacaoDto, val depois: OcupacaoDto)
  ```
  Cobertura: ausências de pessoas visíveis que tocam `[hoje, hoje + horizonteCoberturaDias]` (`dias=1..90`); issues afetadas = abertas da pessoa que a `Agenda` sem ausência agendaria nos dias da ausência, mais as que vencem na ausência e não terminariam antes dela; horas a replanejar = horas agendadas nos dias da ausência no cenário sem ausência; competências = labels `skill:<x>` das issues afetadas; candidatos = pessoas visíveis **do mesmo squad na data** com folga (capacidade − demanda) > 0 nas semanas da ausência, em **ordem alfabética**, com a folga em horas e se têm todas as competências. Simulador: `GET /api/cards/simulador?pessoa=&de=&ate=&fracao=0.5|1.0`, no máximo 60 dias, compara a `Agenda` com `Ausencias.mais(hipotetica)`; **nada é gravado**. Ambos só para liderança; pessoa fora do visível → 403.
- [ ] **Passo 1: Testes que falham** (CAP-1): `coberturaDaAna` (ausência 04/11–05/11: `issues=[TG-202]`, `horasAReplanejar=5.6`, `competencias=[android]`, `podemCobrir=[bruno(folga 9.2, true)]` (capacidade 11,2 h − demanda 2,0 h na semana de 02/11); TG-201 fica fora porque termina em 03/11; `carla` fora por ser de outro squad); `candidatosAlfabeticosSemOrdenarPorFolga`; `horizonteForaDoIntervalo400`; `simuladorCausaAtraso` (`bruno` 03/11–06/11, 1,0 → `atrasosCausados=[TG-203]`); `simuladorSemEfeito` (ausência sem trabalho agendado → lista vazia); `simuladorNaoGrava` (contagem das tabelas e conteúdo do `ausencias.yaml` iguais antes e depois); `simuladorPeriodoLongo400` (61 dias); `simuladorPessoaForaDoVisivel403` (`lider.alfa` simulando `carla`); `usuarioSemAcesso403`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CoberturaCalculatorTest*' --tests '*SimuladorCalculatorTest*' --tests '*CoberturaSimuladorRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Cobertura por ausência e Simulador de ausência`.

---

### Tarefa 14: Matriz de competências, Revisões pendentes por revisor e Carga por tipo/OS (API)

**Arquivos:** Criar `cards/competencias/{CompetenciasCalculator.kt,CompetenciasDto.kt,CardCompetencias.kt}`, `cards/revisoes/{RevisoesCalculator.kt,RevisoesDto.kt,CardRevisoes.kt}`, `cards/cargatipo/{CargaTipoCalculator.kt,CargaTipoDto.kt,CardCargaTipo.kt}` e as fichas; Teste `cards/competencias/CompetenciasCalculatorTest.kt`, `cards/revisoes/RevisoesCalculatorTest.kt`, `cards/cargatipo/CargaTipoCalculatorTest.kt`, `cards/CompetenciasRevisoesCargaTipoRoutesTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  @Serializable data class CompetenciasDto(val competencias: List<String>, val pessoas: List<String>, val celulas: List<List<Boolean>>,
      val pessoaUnica: List<String>, val semNinguem: List<String>, val demandaH: Map<String, Double>, val avisos: List<AvisoDto>)
  @Serializable data class RevisoesDto(val linhas: List<RevisorDto>, val semRevisor: Int?, val avisos: List<AvisoDto>)
  @Serializable data class RevisorDto(val revisor: String, val pendentes: Int, val vencidas: Int, val foraDoSla: Int, val idadeP50: Double?)   // alfabética
  @Serializable data class CargaTipoDto(val tipos: List<String>, val versoes: List<String>, val abertasH: List<List<Double>>, val abertasN: List<List<Int>>,
      val fechadasN: List<List<Int>>, val esforco: List<List<Double>>, val avisos: List<AvisoDto>)
  ```
  Competências: da `Equipes` (pessoas visíveis, alfabéticas); `pessoaUnica` = competência com exatamente 1 pessoa visível; `semNinguem` = `skill:<x>` pedida por issue aberta sem ninguém que a tenha; `demandaH` = horas abertas por competência. Revisões: abertas dos tipos `ctx.regrasAuditoria.tiposReview`, agrupadas pelo responsável; `foraDoSla` = idade em dias úteis desde a criação > `slaRevisaoDias`; `semRevisor` só para coordenação e gerente (`null` para os demais). Carga por tipo/OS: abertas (horas da Tarefa 2 e contagem) e fechadas no período (contagem e índice de esforço) por tipo × `OS Version` (`null` → "sem OS").
- [ ] **Passo 1: Testes que falham:** `matrizDoCenario` (`android`: `ana`, `bruno`; `rede`: `ana`, `carla`; `ui`: `carla` → `pessoaUnica=[ui]`); `skillSemNinguem` (TG-204 com `skill:ios` → `semNinguem=[ios]`); `competenciaSoDoVisivel` (`lider.alfa` não vê `carla`: `rede` passa a ter só `ana`); `revisoesDoBruno` (cenário próprio: review TG-305 de `bruno`, criada `2026-10-26T12:00Z`, vence 29/10 → `pendentes=1`, `vencidas=1`, `foraDoSla=1` (4 dias úteis > 2)); `semRevisorSoCoordenacao`; `cargaPorTipoEOs` (Checklist × "Android 15" com 2 abertas e `16.8` h); `semOsViraLinhaPropria` (TG-203 em "sem OS"); `drillTemOMesmoTamanhoDoCard` (`revisor:bruno:foraDoSla`, `tipo:Checklist:os:Android 15`).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CompetenciasCalculatorTest*' --tests '*RevisoesCalculatorTest*' --tests '*CargaTipoCalculatorTest*' --tests '*CompetenciasRevisoesCargaTipoRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(pessoas): Matriz de competências, Revisões pendentes e Carga por tipo e OS`.

---

### Tarefa 15: Telas do bloco 6b

**Arquivos:** Criar `app/frontend/src/screens/{Posse,Esforco,Cobertura,Simulador,Competencias,Revisoes,CargaTipo}/{<Tela>.tsx,api.ts,<Tela>.test.tsx}`; Modificar `App.tsx`, `i18n/pt.json`, `i18n/en.json`, mock de desenvolvimento.

**Interfaces:** Consome os DTOs das Tarefas 10 a 14 e o kit do Plano 5. Esforço mostra os três componentes e o índice lado a lado ("Tipo 3,0 · Testes TEST-* 3 · Horas 10 h · Índice 14,5", wireframe 6.5) com a versão das regras; Simulador é um formulário de leitura (pessoa, de, até, fração) que só muda a URL e nunca envia POST; Cobertura lista candidatos em ordem alfabética com a folga ao lado.

- [ ] **Passo 1: Testes que falham:** `posseMostraOutraPessoaSemNome`; `esforcoTresComponentesEIndice`; `esforcoAvisoHorasDoCampo`; `coberturaCandidatosAlfabeticos`; `simuladorSoUsaGet` (nenhum `fetch` com método diferente de GET); `simuladorMostraAntesEDepois`; `competenciaPessoaUnicaComTriangulo`; `revisoesForaDoSlaComTexto`; `cargaTipoSemOsEmTexto`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Posse src/screens/Esforco src/screens/Cobertura src/screens/Simulador src/screens/Competencias src/screens/Revisoes src/screens/CargaTipo` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA, suíte inteira e `pnpm build`. **Passo 5: Captura de verificação** (uma rodada, 1440 e 390 px, três temas). **Passo 6: Commit** `feat(pessoas): telas de posse, esforço, cobertura, simulador, competências, revisões e carga por tipo`.

---

### Tarefa 16: Diferenças esperadas (bloco 6b) e somente leitura

**Arquivos:** Modificar `docs/analise/diferencas-esperadas-pessoas-e-planejamento.md` (seção 6b), `docs/DiferencasPessoasDocTest.kt`, `HANDOFF.md`; Criar `pessoas/SomenteLeituraTest.kt`.

- [ ] **Passo 1: Testes que falham:** `nenhumaRotaEscreveAusenciasOuEquipes` (inventário das rotas do `module`: nenhuma `post`/`put`/`patch`/`delete` sob `/api/cards`, `/api/drill`, `/api/pessoas`); `nenhumCodigoGravaOsYamlDePessoas` (varredura de `src/main`: nenhum `Files.write*` com `equipes`, `ausencias` ou `papeis`); `documentoCitaOsCardsNovos` (Posse, Esforço, Cobertura, Simulador, Competências, Revisões pendentes, Carga por tipo/OS).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*SomenteLeituraTest*' --tests '*DiferencasPessoasDocTest*'` → FALHA. **Passo 3: Escrever** a seção 6b: posse proporcional no lugar de "tudo para o responsável final", esforço novo (índice proposto, calibração pendente), "aprovação de ausências" virou simulador (spec 4.4), candidatos à cobertura sem ordem por folga, worklog sincronizado sem comentário. **Passo 4: Rodar** → PASSA e suítes completas. **Passo 5: Commit** `docs(pessoas): diferenças esperadas do bloco 6b e garantia de somente leitura`.

---

## Autoavaliação

- **Cobertura da spec:** 4.2 (capacidade zero, atraso acumulado, horas por tipo, heurísticas versionadas, esforço com três componentes e índice: T2, T3, T12), 4.4 (ausências em YAML somente leitura e simulador: T1, T13, T16), 6.2 (os 11 cards novos: Calendário, Cobertura, Plano, Vencimentos, Posse, Simulador, Competências, Revisões, Carga por tipo/OS, Tendência e Esforço: T6, T7, T11 a T14), 6.3 (posse: T11, T12), 5.4 (worklog guardado com retenção: T10). IDs do relatório: CP, CA, GA (6a). "Pessoas sem capacidade para novos itens" (relatório 7.2) é a coluna de folga da Cobertura e o contador `comFolga` da Carga.
- **Consistência de tipos:** `Horas`/`Capacidade`/`Demanda`/`Agenda` (T2 a T4) servem todos os cards; `Agenda.comoPrevisao` implementa `PrevisaoDeTermino` do Plano 5; `PosseDePessoa` (T11) é usado por Esforço (T12); `WorklogRecord`/`WorklogStore` (T10) por Carga e Esforço; `OcupacaoDto` é comum a Carga, Capacidade, Plano, Tendência e Simulador.
- **Pendências deliberadas:** endpoint e paginação do worklog no DC "não validados" até a 1ª conexão real (depois que o usuário ligar `JIRA_LIVE_ENABLED=true`, seguindo `docs/jira/checklist-primeira-conexao.md`); pesos do esforço e foco calibrados depois com dados reais. As horas do Esforço por pessoa seguem a posse (spec 6.3), não o autor do worklog.

## Decisões (25/10, valor padrão recomendado, reversível)

O usuário delegou estas escolhas em 25/10; vale a opção que o plano recomendava. Cada uma pode ser revertida depois por decisão do usuário, sem refazer o restante da fase. A spec da fase é `docs/superpowers/specs/2026-10-25-spec-fase-06-pessoas-planejamento-ausencias.md`.

1. **Tendência de ocupação: reconstruir as semanas passadas a partir do changelog, com as horas atuais.** Sem tabela nova nem job semanal; funciona desde já; a resposta traz o aviso `HORAS_ATUAIS_NO_PASSADO` e a tela o mostra (Tarefa 7, `avisoDeHorasAtuais`). Alternativa descartada por ora: retrato semanal gravado no banco (exigiria migração, job e só teria histórico a partir da implantação).
2. **Candidatos na Cobertura por ausência: só pessoas visíveis do mesmo squad na data, em ordem alfabética, com a folga ao lado.** Cumpre "sem ranking de pessoas"; a escolha fica com o líder (Tarefa 13, `candidatosAlfabeticosSemOrdenarPorFolga`; Tarefa 15, `coberturaCandidatosAlfabeticos`). Alternativa descartada: ordenar por folga (seria um ranking de disponibilidade entre colegas).
3. **Semana atual na capacidade: de hoje até domingo.** Não conta como livre o tempo que já passou (Tarefa 2, `semanaAtualDeHojeAteDomingo`; Tarefa 9 registra a diferença com o app atual). Alternativa descartada: a semana inteira de segunda a domingo como no app atual.
