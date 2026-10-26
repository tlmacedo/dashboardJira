# Fase 4: Auditoria de vencimento, responsável e labels de atraso — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo (Goal):** Detectar, a partir do changelog sincronizado (`change_event`), as alterações de responsável, vencimento e labels de atraso feitas por quem não podia fazê-las (spec seção 7), e mostrá-las em telas restritas, com gaveta de histórico campo a campo, o card Task × Review e as métricas "no prazo" e "no prazo ajustado".

**Arquitetura (Architecture):** Um domínio puro reconstrói o estado de cada issue em qualquer instante (`AuditTimeline`) e decide quem estava autorizado na data civil de Manaus de cada alteração (`Autorizacao`, sobre o `RoleDirectory` com vigência). Detectores puros transformam a linha do tempo em `Sinal`; o `AuditCalculator` junta sinais, Task × Review e no prazo. Os achados são **calculados na leitura** (sem tabela nova): o `PostgresAuditStore` lê `issue` e `change_event`. As rotas `/api/auditoria/*` e `/api/cards/task-review` exigem coordenador ou gerente, gravam toda consulta em `access_log` e alimentam duas telas React densas.

**Stack (Tech Stack):** Kotlin 2 + Ktor 3.3.1, kaml (YAML estrito), PostgreSQL + Testcontainers 2.0.3; React + TypeScript (Vite, Vitest, Testing Library, TanStack Query).

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 4.2 "Atraso justificado", 4.3, 7.1, 7.2, 7.3, 9 e 10). Depende só de interfaces do Plano 3, já na `main`: `ChangeEventRecord`, `ChangelogParser`, `IssueStore`/`PostgresIssueStore` (só como modelo de SQL), `StatusMap`, `BusinessCalendar`, `RoleDirectory`, `Visibility`, `AccessLog`, `AvisoDto`, `requireRole`, `testContextComBanco`, `ORDEM_NATURAL_DE_CHAVES`. Do Plano 4A depende **só pelos dados reais**; executa-se 100% offline com fixtures.

## Dados disponíveis e o que fica de fora

O Plano 3 grava em `change_event` o valor de/para **só** de `status`, `assignee` (username normalizado), `resolution`, `duedate`, `labels` e `OS Version`. Os demais campos (`reporter`, `description`, `summary`, estimativas...) só registram o **nome do campo** e o autor. Consequências, declaradas na tela e no documento de diferenças:
- **Reporter (PL):** a autorização usa o reporter **atual** (`issue.reporter_ref`). Se houver evento de `reporter`, o sinal recebe o aviso `REPORTER_ALTERADO` (a autorização pode estar avaliada contra o PL errado).
- **Fora desta fase:** "Estimativa reduzida" e "Worklog retroativo" (sem valor de estimativa no changelog e sem worklog sincronizado); os 12 sinais de **Integridade** (Fase 7, só gerente); histórico de reporter; conteúdo de descrição e título.
- `labels` no DC vêm como texto separado por espaço em `fromString`/`toString`; `duedate` pode vir `aaaa-mm-dd` (`from`) ou `aaaa-mm-dd 00:00:00.0` (`fromString`): o leitor aceita os dois, sem exigir nova sincronização.

## Restrições globais (Global Constraints)

- Nenhuma chamada ao Jira; só fixtures e builders em memória. `JIRA_LIVE_ENABLED=false`.
- **Indício, não prova:** aviso fixo em toda tela e em toda resposta (`aviso = "INDICIO_NAO_PROVA"`). **LGPD:** confirmar a base legal com o DPO antes de usar com dados reais; até lá, só fixtures e modo demonstração.
- **Quem pode alterar** (spec 7.1): Reporter (PL), coordenadores e gerente, ou Squad Leader do squad do responsável, **com os papéis vigentes na data civil de Manaus da alteração**.
- Telas e rotas de auditoria: só `COORDENADOR` e `GERENTE` (spec 7.3; o menu já tem `auditoria` e `divergencia` para esses papéis). Com `DASHBOARD_DEV_MODE=true`, o usuário local de desenvolvimento (principal `dev`, que já é `GERENTE`) também vê, **sem ramo especial no código**: vale a mesma checagem de papel. `USUARIO` e `SQUAD_LEADER` não veem sinais de auditoria em nenhuma rota nem tela. Integridade continua só do gerente (Fase 7).
- **Sinais calculados na consulta:** sem tabela de achados, sem migração para sinais e sem triagem.
- Toda consulta (200, 400, 403, 404) grava `access_log`; falha ao gravar → 503 sem dados (mesma regra do Plano 3).
- Sem ranking de pessoas: listas por data e chave; nenhuma contagem agregada por pessoa; filtros de pessoa em ordem alfabética.
- Cor nunca é o único sinal: `✓` autorizado, `▲` auditoria/não autorizado, `●` alerta, sempre com texto. pt-BR e inglês.
- Fuso America/Manaus para todo "dia"; limites de dias em YAML versionado; a versão das regras sai em toda resposta.
- **Sem migração** neste plano. Não referenciar colunas do Plano 4A (`removida_em`, `jira_id`): se o 4A já estiver na `main`, a Tarefa 6 acrescenta `removida_em IS NULL` e o teste `auditoriaIgnoraIssueRemovida`; senão a Tarefa 6 do 4A o faz.
- Configs reais fora do git; versionar só `*.exemplo.yaml`. Comentários em pt-BR; commits Conventional Commits pt-BR (`feat(auditoria): ...`).

## Foco da revisão (Review Focus)

1. **Data civil de Manaus na fronteira** (alteração às 23:30 de Manaus = dia seguinte em UTC) decide vigência de papel, janela "perto do vencimento" e "depois do vencimento" → `dataCivilDeManaus` (T2) e `labelAposVencimentoFronteira` (T4).
2. **Autor ausente** (automação, usuário apagado, chave `JIRAUSER*` não resolvida) nunca vira "autorizado" nem derruba a tela → `autorDesconhecidoNaoEhAutorizado` (T2) e `autorJirauserNaoResolvidoAparece` (T3).
3. **Valor de `duedate` em formato inesperado** já gravado na base: o sinal não some em silêncio nem dá 500 → `vencimentoIlegivelViraAviso` (T5).
4. **Papel encerrado na véspera** da alteração (Squad Leader que saiu do squad): não autoriza → `vigenciaTerminouOntem` (T2).
5. **USUARIO ou SQUAD_LEADER chamando a API direto** (sem passar pelo menu): 403 em todas as rotas, e a negação fica registrada → `soCoordenacaoEGerente` (T7).

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/
  domain/LabelsDeAtraso.kt, domain/NoPrazo.kt           # reutilizáveis na Fase 5
  auditoria/RegrasDeAuditoria.kt                         # YAML de limites, gravidades e versão
  auditoria/AuditModel.kt, AuditTimeline.kt, Autorizacao.kt
  auditoria/Sinais.kt, DetectoresDeVencimento.kt, DetectoresDeResponsavel.kt, DetectoresDeLabels.kt
  auditoria/TaskReview.kt, AuditCalculator.kt, AuditStore.kt
  auditoria/AuditoriaRoutes.kt, AuditoriaDto.kt
  cards/RotaAuditada.kt                                   # wrapper de rota auditada extraído do Plano 3
app/config/labels_atraso.exemplo.yaml, app/config/auditoria.exemplo.yaml
app/frontend/src/shared/csv.ts
app/frontend/src/screens/Auditoria/{Auditoria.tsx,api.ts,HistoricoDrawer.tsx,auditoria.css,Auditoria.test.tsx}
app/frontend/src/screens/Divergencia/{Divergencia.tsx,api.ts,Divergencia.test.tsx}
docs/analise/diferencas-esperadas-auditoria.md
```

**Cenário AUD-1** (usado nas Tarefas 2 a 5; papéis de teste em memória, como no Plano 3): `pl.alfa` (Reporter, USUARIO do squad Alfa), `ana` e `bruno` USUARIO do Alfa, `carla` USUARIO do Beta, `lider.alfa` SQUAD_LEADER do Alfa **até 2026-10-09**, `lider.alfa2` SQUAD_LEADER do Alfa desde 2026-10-10, `coord.um` COORDENADOR, `gerente.exemplo` GERENTE. Issue **TG-10**: criada `2026-10-01T12:00Z`, responsável inicial `ana`, vencimento `2026-10-15` (quinta), status inicial "Open".

---

### Tarefa 1: Labels de atraso e regras da auditoria em YAML

**Arquivos:** Criar `domain/LabelsDeAtraso.kt`, `auditoria/RegrasDeAuditoria.kt`, `app/config/labels_atraso.exemplo.yaml`, `app/config/auditoria.exemplo.yaml`; Modificar `AppContext.kt` (`labelsDeAtraso`, `regrasAuditoria`; fallback `.exemplo` só em dev), `TestContext.kt`, `TestContextComBanco.kt`; Teste `domain/LabelsDeAtrasoTest.kt`, `auditoria/RegrasDeAuditoriaTest.kt`, `AppContextTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  class LabelsDeAtraso(val versao: Int, justificativas: List<String>, tardias: Set<String>) {
      fun justifica(label: String): Boolean        // exato ou prefixo com "*" final; sem diferenciar maiúsculas
      fun ehTardia(label: String): Boolean          // LATE_SENT, LATE_REVIEW
      companion object { fun fromYaml(path: Path): LabelsDeAtraso }
  }
  enum class UnidadeDias { CORRIDO, UTIL }
  data class RegraSinal(val gravidade: Int, val ativo: Boolean = true)        // gravidade 1..3
  data class RegrasDeAuditoria(val versao: Int, val pertoDoVencimentoDias: Int, val posseAntesDoVencimentoDias: Int,
      val passaBolaTrocas: Int, val unidade: UnidadeDias, val statusCancelamento: Set<String>,
      val tiposReview: Set<String>, val sinais: Map<CodigoSinal, RegraSinal>) {
      companion object { fun fromYaml(path: Path): RegrasDeAuditoria }
  }
  ```
  (`CodigoSinal` é definido na Tarefa 3; nesta tarefa crie já o `enum` em `auditoria/Sinais.kt` com os 11 códigos listados lá.)
  `labels_atraso.exemplo.yaml`: `versao: 1`, `justificativas: ["ONHOLDBY-*", "LATE_SENT", "LATE_REVIEW", "DUEDATE_NEGOTIATED", "BI-CONSIDERCANCELLED"]`, `tardias: ["LATE_SENT", "LATE_REVIEW"]`. `auditoria.exemplo.yaml`: `versao: 1`, `pertoDoVencimentoDias: 2`, `posseAntesDoVencimentoDias: 2`, `passaBolaTrocas: 3`, `unidade: corrido`, `statusCancelamento: [Cancelled, Canceled, Dropped]`, `tiposReview: ["Checklist Review"]`, e `sinais:` com gravidade de cada código (3 para os de vencimento, responsável não autorizado, passa-bola e label após vencimento; 2 para os demais; 1 para `VENCIMENTO_TASK_REVIEW_DIVERGENTE`). Ambos estritos (chave desconhecida falha).

- [ ] **Passo 1: Testes que falham:**
  - `curingaSoNoFim`: `ONHOLDBY-*` casa `ONHOLDBY-PL` e `onholdby-x`, não casa `XONHOLDBY-PL`; padrões `*X` e `A*B` falham na carga.
  - `semDiferenciarMaiusculas` (`late_sent` justifica e é tardia).
  - `tardiaPrecisaSerJustificativa` (tardia fora de `justificativas` → `IllegalArgumentException`).
  - `arquivoEstrito`; `limitesValidos` (`pertoDoVencimentoDias` 0..30, `passaBolaTrocas` 2..10, gravidade 1..3; fora → falha citando a chave).
  - `todoCodigoTemRegra` (código ausente em `sinais` → falha citando o código; código desconhecido → falha).
  - `appContextCarregaComFallbackDeDev`; `producaoSemArquivoRealFalha` (mensagem com o caminho esperado).
- [ ] **Passo 2: Rodar** `cd app/backend && ./gradlew test --tests '*LabelsDeAtrasoTest*' --tests '*RegrasDeAuditoriaTest*' --tests '*AppContextTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa (conferir os XML). **Passo 5: Commit** `feat(auditoria): labels de atraso e regras da auditoria em YAML versionado`.

---

### Tarefa 2: Linha do tempo de auditoria e quem pode alterar

**Arquivos:** Criar `auditoria/AuditModel.kt`, `auditoria/AuditTimeline.kt`, `auditoria/Autorizacao.kt`; Modificar `sync/ChangelogParser.kt` (`duedate`: guarda `from`/`to` quando são `aaaa-mm-dd`, senão `fromString`/`toString`); Teste `auditoria/AuditTimelineTest.kt`, `auditoria/AutorizacaoTest.kt`, `sync/ChangelogParserTest.kt`.

**Interfaces:**
- Consome: `ChangeEventRecord`, `StatusMap`, `RoleDirectory` (`rolesOf`, `squadOf`, `squadsLedBy`, `isReporterAuthorized`).
- Produz:
  ```kotlin
  data class AuditIssue(val instance: String, val key: String, val summary: String, val issueType: String, val parentKey: String?,
      val reporter: String?, val assignee: String?, val duedate: LocalDate?, val created: Instant, val resolved: Instant?,
      val resolution: String?, val status: String, val labels: List<String>, val events: List<ChangeEventRecord>)
  object Vencimentos { fun ler(valor: String?): LocalDate? }   // "2026-10-15" e "2026-10-15 00:00:00.0" → 2026-10-15; outro → null
  data class MudancaDeLabels(val adicionadas: Set<String>, val removidas: Set<String>)
  class AuditTimeline(val issue: AuditIssue, private val statusMap: StatusMap) {
      fun responsavelEm(at: Instant): String?          // estado imediatamente ANTES dos eventos do instante `at`
      fun vencimentoEm(at: Instant): LocalDate?
      fun fechadaEm(at: Instant): Boolean              // categoria DONE ou resolution preenchida, antes de `at`
      fun posseDesde(at: Instant): Instant?            // início da posse contínua de responsavelEm(at)
      fun jaTeve(pessoa: String, antes: Instant): Boolean
      val trocasDeResponsavel: List<ChangeEventRecord>
      val trocasDeVencimento: List<ChangeEventRecord>
      val mudancasDeLabels: List<Pair<ChangeEventRecord, MudancaDeLabels>>
      val transicoes: List<ChangeEventRecord>
      val reporterAlterado: Boolean                    // existe evento de `reporter` (só o nome do campo)
      val vencimentosIlegiveis: Int
  }
  enum class MotivoAutorizacao { REPORTER, COORDENACAO, LIDER_DO_SQUAD, PROPRIO_RESPONSAVEL, NAO_AUTORIZADO, AUTOR_DESCONHECIDO }
  data class Avaliacao(val autorizado: Boolean, val motivo: MotivoAutorizacao)
  class Autorizacao(private val roles: RoleDirectory, private val zone: ZoneId) {
      fun avaliar(autor: String?, reporter: String?, responsavelDeReferencia: String?, at: Instant): Avaliacao
  }
  ```
  Estado inicial de cada campo = `from` do primeiro evento do campo; sem eventos, o valor atual da issue. Ordem do `avaliar`, na data civil de Manaus de `at`: autor nulo/vazio → `(false, AUTOR_DESCONHECIDO)`; autor == reporter → `REPORTER`; COORDENADOR ou GERENTE na data → `COORDENACAO`; autor == responsável de referência → `(false, PROPRIO_RESPONSAVEL)` (mesmo se for Squad Leader do próprio squad; ver Decisões); Squad Leader do squad do responsável na data → `LIDER_DO_SQUAD`; senão `NAO_AUTORIZADO`. Para o autor diferente do responsável, o resultado coincide com `RoleDirectory.isReporterAuthorized`.

- [ ] **Passo 1: Testes que falham** (cenário AUD-1):
  - `responsavelEVencimentoNoInstante`: eventos `2026-10-05T12:00Z` assignee `ana→bruno` e `2026-10-08T12:00Z` duedate `2026-10-15→2026-10-20`: `responsavelEm(2026-10-05T12:00Z) == "ana"`, `responsavelEm(2026-10-05T12:00:01Z) == "bruno"`; `vencimentoEm(2026-10-08T11:59Z) == 2026-10-15`, depois `2026-10-20`.
  - `semEventosUsaEstadoAtual`.
  - `vencimentoNosDoisFormatos`: `"2026-10-15"` e `"2026-10-15 00:00:00.0"` → `2026-10-15`; `"15/10/2026"` → `null` e `vencimentosIlegiveis == 1`.
  - `labelsAdicionadasERemovidas`: de `"login acesso"` para `"acesso LATE_SENT"` → `adicionadas={LATE_SENT}`, `removidas={login}`.
  - `fechadaEmPelaCategoriaOuResolution`; `posseDesdeContinua` (`bruno` desde `10-05T12:00Z`; volta a `ana` em `10-12T12:00Z` → `posseDesde(10-13) == 10-12T12:00Z`); `jaTeve`.
  - `reporterAlteradoSoPeloNomeDoCampo`.
  - Autorização: `reporterAutorizado`; `coordenadorAutorizado`; `liderDoSquadDoResponsavelAutorizado` (`lider.alfa2`, responsável `ana`, `2026-10-10T12:00Z`); `liderDeOutroSquadNaoAutorizado`; `proprioResponsavelNaoAutorizadoMesmoSendoLider`; `autorDesconhecidoNaoEhAutorizado` (`null` e `""`).
  - `vigenciaTerminouOntem`: `lider.alfa` em `2026-10-10T12:00Z` → `NAO_AUTORIZADO`.
  - `dataCivilDeManaus`: `lider.alfa` em `2026-10-10T03:30Z` (09/10 23:30 em Manaus) → `LIDER_DO_SQUAD`.
  - `coerenteComIsReporterAuthorized`: 6 combinações com autor ≠ responsável dão o mesmo `autorizado`.
  - `ChangelogParserTest.duedateGuardaFromIsoQuandoPresente`: item `{field:"duedate", from:"2026-10-15", fromString:"2026-10-15 00:00:00.0", to:null, toString:null}` → `from == "2026-10-15"`, `to == null`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AuditTimelineTest*' --tests '*AutorizacaoTest*' --tests '*ChangelogParserTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(auditoria): linha do tempo de auditoria e autorização pela vigência na data`.

---

### Tarefa 3: Sinais de vencimento, responsável e cancelamento

**Arquivos:** Modificar `auditoria/Sinais.kt` (o `enum` já existe desde a T1; acrescenta `Resultado`, `Sinal`, `ContextoDeteccao` e `Detector`); Criar `auditoria/DetectoresDeVencimento.kt`, `auditoria/DetectoresDeResponsavel.kt`; Teste `auditoria/DetectoresDeVencimentoTest.kt`, `auditoria/DetectoresDeResponsavelTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  enum class CodigoSinal { VENCIMENTO_ALTERADO_NAO_AUTORIZADO, VENCIMENTO_REMOVIDO, VENCIMENTO_TASK_REVIEW_DIVERGENTE,
      RESPONSAVEL_TROCADO_NAO_AUTORIZADO, PASSA_BOLA, TROCA_PERTO_DO_VENCIMENTO,
      LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO, LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO,
      LABEL_TARDIA_COM_POSSE_ANTECIPADA, LABEL_APOS_VENCIMENTO, CANCELAMENTO_SUSPEITO }
  enum class Resultado { AUDITORIA, ALERTA }
  data class Sinal(val codigo: CodigoSinal, val resultado: Resultado, val gravidade: Int, val issueKey: String,
      val campo: String, val de: String?, val para: String?, val at: Instant, val autor: String?,
      val autorizado: Boolean, val motivo: MotivoAutorizacao, val responsavel: String?, val changelogId: String)
  data class ContextoDeteccao(val regras: RegrasDeAuditoria, val labels: LabelsDeAtraso, val autorizacao: Autorizacao,
      val calendario: BusinessCalendar, val statusMap: StatusMap) {
      fun distanciaDias(dia: LocalDate, vencimento: LocalDate): Int   // assinada; CORRIDO = ChronoUnit.DAYS, UTIL = dias úteis do calendário
      fun perto(dia: LocalDate, vencimento: LocalDate): Boolean       // |distância| <= pertoDoVencimentoDias
  }
  fun interface Detector { fun detectar(t: AuditTimeline, ctx: ContextoDeteccao): List<Sinal> }
  object DetectoresDeVencimento { val todos: List<Detector> }   // alterado, removido
  object DetectoresDeResponsavel { val todos: List<Detector> }  // trocado, passa-bola, troca perto, cancelamento
  ```
  Regras: **alterado** = `from` e `to` legíveis e diferentes, autor não autorizado (responsável de referência = responsável no instante) → AUDITORIA; definir pela 1ª vez (`from` nulo) não conta. **Removido** = `from` não nulo e `to` nulo, **qualquer autor** → AUDITORIA. **Responsável trocado** por não autorizado (referência = `from ?: to`) → AUDITORIA. **Passa-bola**: trocas cujo dia está `perto` do vencimento vigente no instante; ≥ `passaBolaTrocas` trocas, **ou** uma troca cujo `to` já teve a task → UM sinal por issue, na troca que completa a condição, qualquer autor. **Troca perto do vencimento** por não autorizado → AUDITORIA por evento. **Cancelamento suspeito**: transição para status de `statusCancelamento` com dia > vencimento vigente, **ou** por não autorizado → UM sinal por evento. Gravidade e `ativo` vêm das regras; sem vencimento, as regras de "perto" e "depois" não disparam.

- [ ] **Passo 1: Testes que falham** (AUD-1; vencimento `2026-10-15`, janela `13/10` a `17/10` em Manaus):
  - `alteradoPeloResponsavelGeraAuditoria`: `ana` muda `2026-10-15→2026-10-20` em `2026-10-10T12:00Z` → 1 sinal AUDITORIA, `motivo=PROPRIO_RESPONSAVEL`, `de="2026-10-15"`, `para="2026-10-20"`, gravidade 3.
  - `alteradoPeloLiderVigenteNaoGera` (`lider.alfa2`); `alteradoPorCarlaGera`; `definidoPelaPrimeiraVezNaoGera`.
  - `removidoGeraMesmoPorCoordenador`.
  - `trocaPorUsuarioComumGera` (`bruno` passa de `ana` para `bruno`); `trocaPeloReporterNaoGera`; `trocaDeTaskSemResponsavelUsaSquadDoNovo` (`lider.alfa2` atribui a `ana` uma task sem responsável → sem sinal).
  - `autorJirauserNaoResolvidoAparece`: autor `JIRAUSER99` → sinal com `autor="JIRAUSER99"`, `motivo=NAO_AUTORIZADO`, sem exceção.
  - `passaBolaTresTrocasNaJanela` (trocas em 13/10, 14/10 e 16/10 → 1 sinal em 16/10); `passaBolaVoltaAQuemJaTeve` (`ana→bruno` 13/10, `bruno→ana` 14/10 → 1 sinal em 14/10); `duasTrocasForaDaJanelaNaoGeram` (01/10 e 02/10); `passaBolaSemVencimentoNaoGera`.
  - `limiteDaJanela`: troca em `2026-10-13T04:00Z` (13/10 00:00 Manaus) conta; em `2026-10-13T03:59:59Z` (12/10) não.
  - `trocaPertoPorUsuarioGera` (no dia 15/10 por `carla`); `trocaPertoPeloLiderNaoGera`.
  - `diasUteisQuandoConfigurado`: vencimento `2026-10-19` (segunda), troca na sexta `2026-10-16`: `CORRIDO` → distância 3, fora; `UTIL` → 1, dentro.
  - `canceladaDepoisDoVencimento` (`coord.um` cancela em 16/10 → sinal, `autorizado=true`); `canceladaPorUsuario` (`ana` em 10/10 → sinal); `canceladaPeloReporterAteOVencimento` (em 15/10 → 0).
  - `regraInativaNaoGera` (`ativo: false` no YAML de teste).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DetectoresDe*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(auditoria): sinais de vencimento, responsável, passa-bola e cancelamento`.

---

### Tarefa 4: Sinais de labels de atraso

**Arquivos:** Criar `auditoria/DetectoresDeLabels.kt`; Teste `auditoria/DetectoresDeLabelsTest.kt`.

**Interfaces:**
- Consome: `AuditTimeline.mudancasDeLabels`, `fechadaEm`, `responsavelEm`, `posseDesde`, `vencimentoEm` (T2); `LabelsDeAtraso` (T1); `Detector`, `ContextoDeteccao` (T3).
- Produz `object DetectoresDeLabels { val todos: List<Detector> }`. Para cada label **adicionada** que `justifica` (removidas não geram nada):
  1. Se `fechadaEm(at)`: autor == responsável no instante → `LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO`, AUDITORIA se não autorizado e **ALERTA** se autorizado (responsável que é o Reporter ou coordenação); senão, se não autorizado → `LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO` AUDITORIA.
  2. Dia (Manaus) > vencimento vigente → `LABEL_APOS_VENCIMENTO` AUDITORIA, qualquer autor.
  3. `ehTardia` e `posseDesde(at)` do responsável no instante ≤ início (00:00 Manaus) do dia `vencimento − posseAntesDoVencimentoDias` (na unidade das regras) → `LABEL_TARDIA_COM_POSSE_ANTECIPADA` AUDITORIA.
  Um evento pode gerar mais de um código; nunca dois sinais do mesmo código para o mesmo `changelogId` e label.

- [ ] **Passo 1: Testes que falham** (AUD-1, vencimento 15/10, `resolved 2026-10-14T12:00Z` quando dito "fechada"):
  - `labelAposFechamentoPorNaoAutorizado`: fechada; `carla` adiciona `DUEDATE_NEGOTIATED` em `2026-10-14T15:00Z` → só `LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO`.
  - `labelPeloResponsavelAposFechamentoEhAuditoria` (`ana`, responsável) e `responsavelAutorizadoViraAlerta` (responsável e autor `pl.alfa`, o Reporter → ALERTA).
  - `labelAposVencimentoFronteira`: em `2026-10-16T04:00Z` (16/10 00:00 Manaus) → gera; em `2026-10-16T03:59Z` (15/10 23:59) → não.
  - `tardiaComPosseAntecipada`: `ana` responsável desde `2026-10-12T12:00Z`, adiciona `LATE_SENT` em `2026-10-15T20:00Z` (aberta) → gera; `posseExatamenteDoisDias` (assumiu `2026-10-13T04:00Z`) → gera; `posseRecente` (assumiu `2026-10-14T12:00Z`) → não.
  - `curingaOnHoldBy` (`ONHOLDBY-PL` em 16/10 → `LABEL_APOS_VENCIMENTO`).
  - `labelQueNaoJustificaIgnorada` (`login`); `labelRemovidaNaoGera`; `semVencimentoSoRegrasDeFechamento`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DetectoresDeLabelsTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(auditoria): sinais de labels de atraso depois do fechamento, do vencimento e com posse antecipada`.

---

### Tarefa 5: Task × Review, "no prazo" × "no prazo ajustado" e `AuditCalculator`

**Arquivos:** Criar `auditoria/TaskReview.kt`, `domain/NoPrazo.kt`, `auditoria/AuditCalculator.kt`; Teste `auditoria/TaskReviewTest.kt`, `domain/NoPrazoTest.kt`, `auditoria/AuditCalculatorTest.kt`.

**Interfaces:**
- Produz:
  ```kotlin
  data class ParTaskReview(val task: AuditIssue, val review: AuditIssue) { val divergente: Boolean }  // vencimentos diferentes; um nulo e outro não = divergente; ambos nulos = não
  data class ResumoTaskReview(val pares: Int, val divergentes: Int, val percentual: Double?)        // null sem pares; 25.0 = 25%
  object TaskReview {
      fun pares(issues: List<AuditIssue>, tiposReview: Set<String>): List<ParTaskReview>   // review = tipo em tiposReview com parentKey presente na lista
      fun resumo(pares: List<ParTaskReview>): ResumoTaskReview
      fun sinais(par: ParTaskReview, ctx: ContextoDeteccao): List<Sinal>                // VENCIMENTO_TASK_REVIEW_DIVERGENTE (ALERTA) no evento de duedate que deixa as datas diferentes
  }
  data class NoPrazoResultado(val concluidas: Int, val noPrazo: Int, val noPrazoAjustado: Int, val justificadasSobAuditoria: Int) {
      val taxa: Double?; val taxaAjustada: Double?                                         // fração 0..1; null sem concluídas
  }
  object NoPrazo { fun calcular(issues: List<AuditIssue>, labels: LabelsDeAtraso, statusMap: StatusMap, zone: ZoneId,
                                de: LocalDate, ate: LocalDate, sinais: List<Sinal> = emptyList()): NoPrazoResultado }
  data class ResultadoAuditoria(val versaoRegras: String, val sinais: List<Sinal>, val porCodigo: Map<CodigoSinal, Int>,
      val taskReview: ResumoTaskReview, val pares: List<ParTaskReview>, val noPrazo: NoPrazoResultado, val avisos: List<AvisoDto>)
  class AuditCalculator(private val ctx: ContextoDeteccao,
      private val detectores: List<Detector> = DetectoresDeVencimento.todos + DetectoresDeResponsavel.todos + DetectoresDeLabels.todos) {
      fun calcular(comEventos: List<AuditIssue>, concluidas: List<AuditIssue>, paresBase: List<AuditIssue>,
                   de: Instant, ate: Instant, diaDe: LocalDate, diaAte: LocalDate): ResultadoAuditoria
  }
  ```
  `NoPrazo`: universo = concluídas (categoria `DONE` **e** `resolution`) com vencimento em `[de, ate]`; no prazo = dia de Manaus de `resolved` ≤ vencimento; ajustado = no prazo **ou** alguma label atual que `justifica`; `justificadasSobAuditoria` = atrasadas ajustadas cuja label tem sinal `LABEL_*` (continuam contando no ajustado; ver Decisões). `AuditCalculator`: só sinais com `at` em `[de, ate)`; ordem por `at` decrescente, depois `ORDEM_NATURAL_DE_CHAVES`, depois o código (nunca por pessoa); `versaoRegras = "regras v{n}, labels v{m}"`; avisos informativos com `params`: `REPORTER_ALTERADO` (`n`), `VENCIMENTO_ILEGIVEL` (`n`), `ISSUES_IGNORADAS_STATUS` (`status`, `n`: issue com status fora do mapa é ignorada, sem derrubar a tela).

- [ ] **Passo 1: Testes que falham:**
  - `quatroParesUmDivergente` → `ResumoTaskReview(4, 1, 25.0)`; `reviewSemPaiNaListaIgnorada`; `ambosSemVencimentoNaoDiverge`; `semParesPercentualNulo`.
  - `sinalNoEventoQueDiverge`: task 15/10; review muda `15/10→18/10` em `2026-10-10T12:00Z` → 1 ALERTA; a task muda depois para `18/10` → nenhum sinal novo (as datas voltam a ser iguais).
  - `noPrazoEAjustado` (período 01/10 a 31/10): TG-21 resolvida 14/10, vence 15/10 (no prazo); TG-22 resolvida `2026-10-16T03:30Z` (15/10 23:30 Manaus), vence 15/10 (no prazo); TG-23 resolvida 17/10 com `LATE_SENT`, vence 15/10 (só ajustada); TG-24 resolvida 17/10 sem label (atrasada); TG-25 `DONE` sem `resolution` (fora) → `concluidas=4`, `noPrazo=2`, `noPrazoAjustado=3`, `taxa=0.5`, `taxaAjustada=0.75`.
  - `justificadaSobAuditoriaContaNoAjustado` (TG-23 com sinal `LABEL_APOS_VENCIMENTO` → `justificadasSobAuditoria=1`, `noPrazoAjustado` continua 3).
  - `periodoFiltraPelaDataDoEvento`: evento em `2026-10-31T03:30Z` (30/10 Manaus) entra com `ate=2026-10-31T04:00Z`; em `2026-10-31T04:00Z` não.
  - `ordemSemRankingDePessoas`; `versaoDasRegrasNoResultado` (`"regras v1, labels v1"`).
  - `reporterAlteradoViraAviso`; `vencimentoIlegivelViraAviso` (evento `duedate` com `"15/10/2026"` → aviso `VENCIMENTO_ILEGIVEL n=1`, sem exceção); `statusSemMapaIgnoraIssueEAvisa`.
  - `todoCodigoDetectadoNoCenarioCompleto`: um cenário com uma issue por caso detecta os 11 `CodigoSinal` (critério de sucesso da spec 10).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*TaskReviewTest*' --tests '*NoPrazoTest*' --tests '*AuditCalculatorTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(auditoria): Task × Review, no prazo e ajustado, e calculador da auditoria`.

---

### Tarefa 6: `AuditStore` sobre PostgreSQL

**Arquivos:** Criar `auditoria/AuditStore.kt`; Teste `auditoria/AuditStoreTest.kt` (Testcontainers; dados gravados com `PostgresIssueStore.upsert` a partir de JSON de fixture, como no `SyncerTest`).

**Interfaces:**
- Produz (interface nova; `IssueStore` não muda):
  ```kotlin
  val CAMPOS_AUDITADOS: Set<String> = setOf("assignee", "duedate", "labels", "status", "resolution", "reporter")
  interface AuditStore {
      fun comEventos(instance: String, de: Instant, ate: Instant): List<AuditIssue>    // issues com evento de CAMPOS_AUDITADOS em [de, ate), com TODOS os seus eventos
      fun concluidasComVencimento(instance: String, de: LocalDate, ate: LocalDate): List<AuditIssue>
      fun paresTaskReview(instance: String, tiposReview: Set<String>): List<AuditIssue> // reviews com parent_key e as tasks pai
      fun issue(instance: String, key: String): AuditIssue?
  }
  class PostgresAuditStore(private val ds: DataSource) : AuditStore
  ```
  Campo comparado sem diferenciar maiúsculas (`lower(field)`); `summary` de `raw->'fields'->>'summary'`; eventos em lote por `issue_id = ANY(?)` ordenados por `(at, id)`; tudo parametrizado.

- [ ] **Passo 1: Testes que falham:**
  - `devolveIssuesComEventoAuditadoNoPeriodo` (evento de `duedate` em 10/10 entra; issue só com evento de `description` não entra).
  - `trazTodosOsEventosDaIssue` (evento de 01/10, fora do período, vem junto para reconstruir o estado).
  - `fronteiraDoPeriodo` (`at == de` entra, `at == ate` fica fora).
  - `paresPeloParentKey`; `summaryDoRaw`; `instanciaSeparada` (TG-1 da `sidia` não aparece na `samsung`).
  - `concluidasComVencimentoNoPeriodo` (vencimento 31/10 entra com `ate=31/10`).
  - `issueInexistenteEhNula`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AuditStoreTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `feat(auditoria): leitura das issues e do changelog para a auditoria`.

---

### Tarefa 7: API de auditoria, gaveta de histórico e card Task × Review

**Arquivos:** Criar `cards/RotaAuditada.kt` (extrai o `executar` privado de `TempoEmStatusRoutes.kt`, sem mudar comportamento), `auditoria/AuditoriaRoutes.kt`, `auditoria/AuditoriaDto.kt`; Modificar `cards/TempoEmStatusRoutes.kt` (usa `RotaAuditada`), `Application.kt`; Teste `auditoria/AuditoriaRoutesTest.kt` (os testes de `TempoEmStatusRoutesTest` seguem verdes).

**Interfaces:**
- Produz `suspend inline fun <reified T : Any> ApplicationCall.executarAuditado(auditoria: AccessLog, ctx: AppContext, bloco: suspend (Principal) -> T)` (registra antes de responder, inclusive 400/403/404; falha do registro → 503).
- Rotas (todas em `requireRole(COORDENADOR, GERENTE)`; `Visibility` aplicada):
  ```
  GET /api/auditoria/sinais?de=&ate=&squad=&pessoa=&sinal=&resultado=auditoria|alerta   (padrão: últimos 30 dias de Manaus; ate inclusivo; no máximo 180 dias)
  GET /api/drill/auditoria?kind=todos|sinal:<CODIGO>|resultado:<auditoria|alerta>&(mesmos filtros)
  GET /api/auditoria/historico/{key}
  GET /api/cards/task-review?squad=&pessoa=
  GET /api/drill/task-review?kind=pares|divergentes&(mesmos filtros)
  ```
  ```kotlin
  @Serializable data class AuditoriaDto(val aviso: String /* "INDICIO_NAO_PROVA" */, val versaoRegras: String, val total: Int,
      val porSinal: List<ContagemSinalDto>, val sinais: List<SinalDto>, val noPrazo: NoPrazoDto, val avisos: List<AvisoDto>)
  @Serializable data class ContagemSinalDto(val codigo: String, val resultado: String, val gravidade: Int, val n: Int)
  @Serializable data class SinalDto(val codigo: String, val resultado: String, val gravidade: Int, val key: String, val resumo: String,
      val campo: String, val de: String?, val para: String?, val at: String, val dia: String, val autor: String?,
      val autorizado: Boolean, val motivo: String, val responsavel: String?)
  @Serializable data class NoPrazoDto(val concluidas: Int, val noPrazo: Int, val noPrazoAjustado: Int, val justificadasSobAuditoria: Int,
      val taxa: Double?, val taxaAjustada: Double?)
  @Serializable data class HistoricoDto(val aviso: String, val key: String, val resumo: String, val reporter: String?, val linhas: List<LinhaHistoricoDto>, val avisos: List<AvisoDto>)
  @Serializable data class LinhaHistoricoDto(val at: String, val campo: String, val de: String?, val para: String?, val autor: String?,
      val autorizado: Boolean?, val motivo: String?, val valorGuardado: Boolean)
  @Serializable data class TaskReviewDto(val pares: Int, val divergentes: Int, val percentual: Double?, val avisos: List<AvisoDto>)
  @Serializable data class TaskReviewLinhaDto(val task: String, val review: String, val vencimentoTask: String?, val vencimentoReview: String?,
      val responsavelTask: String?, val responsavelReview: String?, val divergente: Boolean)
  ```
  `pessoa` casa o responsável no instante **ou** o autor; `squad` = squad do responsável na data do evento (`RoleDirectory.squadOf`); squad inexistente, data inválida, `de > ate` ou período > 180 dias → 400. `porSinal` na ordem do enum; nenhuma agregação por pessoa. No histórico, campo fora da lista de permissão sai com `de`/`para` nulos e `valorGuardado=false`; chave fora do padrão `JqlValues.issueKey` → 400; inexistente → 404.

- [ ] **Passo 1: Testes que falham** (contexto com banco; papéis do cenário AUD-1; relógio fixo `2026-10-30T15:00Z`):
  - `soCoordenacaoEGerente`: USUARIO e SQUAD_LEADER → 403 nas 5 rotas; COORDENADOR e GERENTE → 200.
  - `todaConsultaGravaAccessLog` (200, 400, 403 e 404; rota sem query string; filtros saneados).
  - `avisoFixoIndicioNaoProva` (sinais e histórico).
  - `drillTemOMesmoTamanhoDoCard`: `sinal:PASSA_BOLA` == `porSinal[PASSA_BOLA].n`; `todos` == `total`; `divergentes` == `TaskReviewDto.divergentes`.
  - `periodoPadraoTrintaDiasEMaximo180` (181 dias → 400; `de > ate` → 400; `de=2026-13-01` → 400, nunca 500).
  - `filtroPessoaPorResponsavelOuAutor`; `squadPelaDataDoEvento`.
  - `historicoCampoACampoSemTextoLivre` (evento `description` → `valorGuardado=false`, `de`/`para` nulos; `TG-1;drop` → 400; `TG-999` → 404).
  - `ordenadoPorDataSemRanking` (sinais por `at` decrescente; o DTO não tem lista por pessoa).
  - `cardTaskReviewQuatroParesVinteECincoPorcento` (`percentual == 25.0`).
  - `semDadosRespondeVazio` (200 com listas vazias, `taxa == null`).
  - `devModeSemLogin` (com `DASHBOARD_DEV_MODE=true`, o principal `dev`, GERENTE, vê as 5 rotas com 200; com o dev mode ligado, um principal USUARIO ou SQUAD_LEADER injetado continua com 403).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*AuditoriaRoutesTest*' --tests '*TempoEmStatusRoutesTest*'` → FALHA. **Passo 3: Implementar.** **Passo 4: Rodar** → PASSA e suíte completa. **Passo 5: Commit** `feat(auditoria): API de auditoria com drill, gaveta de histórico e card Task × Review`.

---

### Tarefa 8: Telas Auditoria e Divergência Task × Review (React)

**Arquivos:** Criar `app/frontend/src/shared/csv.ts` (move `TempoEmStatus/csv.ts`; a tela Tempo em status passa a importar daqui, com os testes), `app/frontend/src/screens/Auditoria/{Auditoria.tsx,api.ts,HistoricoDrawer.tsx,auditoria.css,Auditoria.test.tsx}`, `app/frontend/src/screens/Divergencia/{Divergencia.tsx,api.ts,Divergencia.test.tsx}`; Modificar `App.tsx` (rotas `auditoria` e `divergencia`), `i18n/pt.json` e `i18n/en.json` (códigos de sinal, motivos, resultados, avisos), o mock de desenvolvimento do Vite (dados fictícios só no servidor de dev, fora do bundle).

**Interfaces:**
- Consome: rotas e DTOs da Tarefa 7; `t()`, `Shell`, `DrillDrawer`, `linhaFiltrosCsv`, `format.ts` (Plano 3).
- Produz: `export function Auditoria(): JSX.Element`, `export function Divergencia(): JSX.Element`, `fetchSinais(params)`, `fetchHistorico(key)`, `fetchTaskReview(params)`; tipos espelhando os DTOs.
  Layout **denso**: faixa fixa `role="note"` "Indício, não prova: a tela mostra alterações para conferir, não conclusões sobre pessoas." (en: "Evidence, not proof: ..."); filtros (período, squad, pessoa em ordem alfabética, sinal, resultado) na URL; KPIs: sinais de auditoria (`▲`), alertas (`●`), "No prazo" e "No prazo ajustado" lado a lado, justificadas sob auditoria; tabela de contagem por sinal (ordem fixa do catálogo, gravidade em texto "Gravidade 3"); lista de sinais (data, issue, campo, de → para, autor, `✓ autorizado` ou `▲ não autorizado` com o motivo); clicar abre `HistoricoDrawer` campo a campo ("valor não guardado (privacidade)" quando `valorGuardado=false`); CSV da lista e da gaveta com a linha de filtros. Divergência: KPI "% de pares divergentes", lista clicável (`kind=divergentes`).

- [ ] **Passo 1: Testes que falham** (Vitest + Testing Library, `fetch` simulado):
  - `mostraAvisoFixo` (pt e en).
  - `simbolosAlemDaCor` (autorizado `✓` + texto; não autorizado `▲` + texto; alerta `●`).
  - `kpisNoPrazoEAjustadoLadoALado` (`50%` e `75%`).
  - `clicarNoSinalAbreHistorico` (chama `/api/auditoria/historico/TG-10`; linhas == DTO; `valorGuardado=false` mostra o texto de privacidade).
  - `drillMesmoTamanho` (contador `PASSA_BOLA` = 2 → gaveta com 2 linhas).
  - `csvNeutralizaFormulas` (autor `=HYPERLINK("x")` e ` =1+1` saem neutralizados; teste movido para `shared/csv.test.ts`).
  - `semPermissao403MostraMensagemDePapel`; `semRankingDePessoas` (nenhuma coluna de contagem por pessoa; opções do filtro de pessoa em ordem alfabética).
  - `divergenciaMostraPercentual` (`25%`) e `divergenciaAbreLista`.
- [ ] **Passo 2: Rodar** `cd app/frontend && pnpm vitest run src/screens/Auditoria src/screens/Divergencia src/shared` → FALHA. **Passo 3: Implementar** com TanStack Query. **Passo 4: Rodar** → PASSA, a suíte inteira do frontend e `pnpm build` sem erros.
- [ ] **Passo 5: Captura de verificação:** uma rodada (Chrome headless, 1440 e 390 px, temas Claro, Escuro e Sidia, modo demo); corrigir tudo de uma vez; no máximo mais uma rodada.
- [ ] **Passo 6: Commit** `feat(auditoria): telas de auditoria e de divergência Task × Review`.

---

### Tarefa 9: Diferenças com o app atual e preparação da 1ª conexão

**Arquivos:** Criar `docs/analise/diferencas-esperadas-auditoria.md`; Modificar `docs/jira/checklist-primeira-conexao.md` (Passo 4: conferir `from` × `fromString` de `duedate`, separador das labels em `toString`, e que `reporter` só traz o nome do campo no painel), `docs/jira/matriz-compatibilidade.md` (linhas de `duedate` e `labels` no changelog), `HANDOFF.md`; Teste `auditoria/DiferencasAuditoriaDocTest.kt`.

**Conteúdo:** o app atual não tem os sinais da seção 7.2 (só a Integridade, Fase 7, com "vencimento prorrogado" e "troca de responsável ≤ 2 h antes de fechar", e o AU-02 é registro de acesso, relatório seção 4.13/QD); tabela com cada `CodigoSinal`, a regra, o limite configurável e o equivalente no app atual (ou "novo"); o que fica de fora (estimativa, worklog, histórico de reporter, Integridade); diferenças de fuso (Manaus × UTC), de "concluída" (categoria + `resolution`) e de "no prazo ajustado" (ausente no app atual, relatório seção 5).

- [ ] **Passo 1: Teste que falha:** `documentoCitaTodosOsCodigos` (o documento contém o nome de cada valor de `CodigoSinal`); `PreparacaoDocsTest` continua passando (o `PreparacaoDocsTest` do checklist, que a Tarefa 11 do Plano 4A renomeia para `cadaPassoDoChecklistConfirmaAFlag`; sem host real nem token).
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*DiferencasAuditoriaDocTest*' --tests '*PreparacaoDocsTest*'` → FALHA. **Passo 3: Escrever** os documentos e o HANDOFF (pendência aberta: confirmar a base legal com o DPO antes de usar com dados reais; até lá só fixtures e demo). **Passo 4: Rodar** → PASSA e suítes completas (backend e frontend). **Passo 5: Commit** `docs(auditoria): diferenças esperadas com o app atual e itens da 1ª conexão`.

---

## Autoavaliação

- **Cobertura da spec:** 7.1 (quem pode alterar, vigência por data: T2), 7.2 (11 dos 12 itens da tabela: T3, T4, T5; "estimativa reduzida ou worklog retroativo" declarado fora por falta de dado), 7.3 (aviso fixo, consulta auditada, coordenação e gerente: T7 e T8), 4.2 (duas métricas lado a lado, labels em YAML: T1 e T5), 9 (gravidade e "perto do vencimento" configuráveis: T1), 10 (todo sinal detectado em fixture com fronteiras de 2 dias, fuso e autorizado × não autorizado: T3 a T5). Integridade (12 sinais) fica na Fase 7.
- **Consistência de tipos:** `AuditIssue` (T2) é produzido pelo `PostgresAuditStore` (T6) e consumido por `AuditTimeline`, detectores (T3, T4), `TaskReview`/`NoPrazo`/`AuditCalculator` (T5) e rotas (T7); `CodigoSinal` nasce completo na T1 (as regras do YAML o referenciam) e `Sinal` na T3; `ResultadoAuditoria` (T5) vira `AuditoriaDto` (T7), espelhado no frontend (T8).
- **Pendências deliberadas:** formato real de `duedate`, `labels` e do `from/to` do assignee só na 1ª conexão autorizada (checklist, Passo 4); o leitor aceita os dois formatos conhecidos.

## Decisões

1. **Decisão do usuário (25/10): visibilidade.** Telas e rotas de auditoria para coordenador e gerente; com `DASHBOARD_DEV_MODE=true`, também o usuário local de desenvolvimento (principal `dev`, já GERENTE). Usuário comum e Squad Leader não veem sinais de auditoria. Integridade continua só do gerente (Restrições globais; T7 `soCoordenacaoEGerente` e `devModeSemLogin`).
2. **Decisão do usuário (25/10): sinais calculados na consulta.** Sem tabela nova de achados, sem migração para sinais e sem triagem (Arquitetura; T6, T7).
3. **Decisão do usuário (25/10): auto-alteração do Squad Leader.** Quando o Squad Leader é o responsável da task e altera o próprio vencimento ou a própria posse, conta como não autorizada (`PROPRIO_RESPONSAVEL`; T2 `proprioResponsavelNaoAutorizadoMesmoSendoLider`).
4. **Decisão do usuário (25/10): dias corridos.** "Perto do vencimento" e "2 dias de posse" em dias corridos (`unidade: corrido` no YAML; T1, T3, T4); a configuração continua aceitando dias úteis.
5. **Decisão do usuário (25/10): label sob auditoria no "no prazo ajustado".** Continua contando, com o contador "justificadas sob auditoria" ao lado (T5 `justificadaSobAuditoriaContaNoAjustado`, T8).
6. **Decisão do usuário (25/10): primeira definição do vencimento.** Vazio → data não conta como alteração (T3 `definidoPelaPrimeiraVezNaoGera`).
7. **Decisão do usuário (25/10): estimativa e worklog fora.** Sinais de estimativa reduzida e worklog retroativo ficam fora desta fase.
8. **Decisão do usuário (25/10): LGPD.** Confirmar a base legal com o DPO antes de usar com dados reais; até lá, só fixtures e modo demonstração (Restrições globais; registrado no HANDOFF na T9).
