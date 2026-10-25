# Dicionário de campos e construtor de JQL (Fase 2) — Plano de implementação

> **Para executores:** USE superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Os passos usam caixas `- [ ]`.

**Objetivo:** Classificar os campos do Jira DC do TG-Basic num dicionário YAML e gerar a busca de cada card a partir de uma ficha YAML, por um construtor de JQL com validação, sem JQL cru vindo da tela ou da API.

**Arquitetura:** Três camadas puras (sem rede): `FieldDictionary` (o que existe), `JqlBuilder` com validadores (como escrever com segurança) e `CardSpec` + `CardQueryFactory` (o que cada card busca). Testes comparam o JQL gerado com o texto esperado. Os documentos de preparação (matriz de compatibilidade e checklist da 1ª conexão) fecham a fase.

**Stack:** Kotlin, kotlinx.serialization, `kaml` (ou o leitor YAML usado na Fase 1), JUnit 5/kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-24-painel-squads-kotlin-react-design.md` (seções 5.1, 5.2, 5.5). Depende do Plano 01 (pacote `br.sidia.painel`, `Settings`, `BusinessCalendar`).

## Restrições globais

- Nenhuma chamada ao Jira. Ids `customfield_NNNNN` são **descobertos por nome** só na 1ª conexão autorizada; antes disso o dicionário guarda nomes.
- O arquivo de filtros do TG-Basic (`Filtros_Status_tool.txt`, fora do repositório) é **vocabulário**, nunca usado como JQL pronto.
- JQL só é gerado pelo `JqlBuilder`; nenhum valor de tela entra por concatenação de string.
- Usernames: regex `[a-z0-9._-]+`. Datas: `aaaa-mm-dd`. Estados e tipos só os do dicionário.
- Comentários em pt-BR.

## Foco da revisão

- Valor com aspas, parêntese e `OR` (`x") OR project is not EMPTY OR ("`) é rejeitado, não escapado de forma ambígua.
- `startOfDay(-100)` com número negativo muito grande ou zero (limite do parâmetro).
- Campo com espaço no nome (`"OS Version"`) e campo por id (`cf[12416]`) saem com a sintaxe certa.
- Lista vazia em `in (...)` não gera JQL inválido: falha com mensagem clara.
- Ficha com campo que não existe no dicionário falha na carga, não na hora da busca.

---

## Arquivos (mapa)

```
app/backend/src/main/kotlin/br/sidia/painel/jql/
  FieldDictionary.kt     # modelo e carga de jira_campos.yaml
  JqlValues.kt           # validadores: IsoDate, Username, Literal, RelativeDays
  JqlBuilder.kt          # DSL de cláusulas + ORDER BY
  CardSpec.kt            # ficha do card (YAML) e carga
  CardQueryFactory.kt    # CardSpec + parâmetros -> JqlQuery
app/config/jira_campos.exemplo.yaml
app/config/cards/tempo-em-status.card.yaml   # primeira ficha (Fase 3 a usa)
app/backend/src/test/kotlin/br/sidia/painel/jql/*Test.kt
docs/jira/matriz-compatibilidade.md
docs/jira/checklist-primeira-conexao.md
```

---

### Tarefa 1: Dicionário de campos

**Arquivos:**
- Criar: `jql/FieldDictionary.kt`, `app/config/jira_campos.exemplo.yaml`
- Teste: `jql/FieldDictionaryTest.kt`

**Interfaces:**
- Produz:
  ```kotlin
  enum class FieldType { SELECT, TEXT, DATE, USER, LABELS, NUMBER }
  data class FieldDef(val name: String, val type: FieldType, val instance: String, val purpose: String,
                      val knownValues: List<String> = emptyList(), val customId: String? = null)
  class FieldDictionary(fields: List<FieldDef>) {
      fun get(name: String): FieldDef            // lança UnknownFieldException(name)
      fun has(name: String): Boolean
      fun allowedValues(name: String): List<String>
      companion object { fun fromYaml(path: Path): FieldDictionary }
  }
  class UnknownFieldException(val name: String) : RuntimeException("Campo fora do dicionário: $name")
  ```
  O YAML tem `campos: [{nome, tipo, instancia, finalidade, valores?, customId?}]`. O exemplo traz: `OS Version`, `SKU Type`, `Checklist Type`, `Model Name`, `Model Category`, `TG Request Type`, `Carrier Code`, `Reporter`, `assignee`, `labels`, `status`, `issuetype`, `project`, `created`, `resolved`, `duedate`, mais `cf[12416]` e `cf[11412]` marcados `"a confirmar"` na finalidade. Valores conhecidos de `OS Version` e `SKU Type` vêm do vocabulário dos filtros do TG-Basic ("Android 16.0 (16)", "Android 16.0 (OneUI_8.5)", "Android 17.0 (OneUI_9.0)", "Android 17.0 (OneUI_9.5)"; "1st carrier (Latin)", "1st Carrier (Global)", "MR").

- [ ] **Passo 1: Testes que falham:**
  - `carregaOExemplo`: `FieldDictionary.fromYaml(exemplo)` tem `OS Version` do tipo `SELECT` com 4 valores conhecidos.
  - `campoDesconhecidoFalha`: `get("Inexistente")` lança `UnknownFieldException`.
  - `nomeDuplicadoFalha`: dois campos com o mesmo `nome` e `instancia` → `IllegalArgumentException`.
  - `mesmoNomeEmInstanciasDiferentes` é permitido.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*FieldDictionaryTest*'` → FALHA.
- [ ] **Passo 3: Implementar** o modelo e a carga.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Dicionário de campos do Jira em YAML"`.

---

### Tarefa 2: Validadores de valores JQL

**Arquivos:**
- Criar: `jql/JqlValues.kt`
- Teste: `jql/JqlValuesTest.kt`

**Interfaces:**
- Produz:
  ```kotlin
  class InvalidJqlValueException(val kind: String, val value: String) : RuntimeException("Valor inválido para $kind: $value")
  object JqlValues {
      fun isoDate(value: String): String                    // aaaa-mm-dd existente no calendário
      fun username(value: String): String                   // [a-z0-9._-]+, máx. 64
      fun literal(value: String, allowed: Collection<String>): String   // só se estiver na lista; devolve entre aspas duplas
      fun relativeDays(n: Int): String                      // "startOfDay(-N)"; 1 <= n <= 3650
      fun quotedField(name: String): String                 // "OS Version" entre aspas; cf[12345] sem aspas
  }
  ```

- [ ] **Passo 1: Testes que falham:**
  - `dataValida` → `"2026-10-24"`; `dataImpossivel`: `"2026-13-45"` lança; `dataComTexto`: `"ontem"` lança.
  - `usernameValido`: `"thiago.macedo"`, `"pedro.almeida-e"` passam; `usernameComEspaco`, `usernameComAspas` (`x" OR "`) e `usernameVazio` lançam.
  - `literalForaDaListaLanca`; `literalDentroDaListaVoltaComAspas`: `literal("Android 16.0 (16)", allowed)` → `"\"Android 16.0 (16)\""`.
  - `relativeDays(100)` → `startOfDay(-100)`; `relativeDays(0)` e `relativeDays(4000)` lançam.
  - `campoComEspacoVaiEntreAspas`: `quotedField("OS Version")` → `"OS Version"`; `quotedField("cf[12416]")` → `cf[12416]`; `quotedField("a\"b")` lança.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JqlValuesTest*'` → FALHA.
- [ ] **Passo 3: Implementar** com regex fechadas e `LocalDate.parse` para a data. Rejeitar sempre; **não** escapar valores livres.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Validadores de valores JQL"`.

---

### Tarefa 3: `JqlBuilder`

**Arquivos:**
- Criar: `jql/JqlBuilder.kt`
- Teste: `jql/JqlBuilderTest.kt`

**Interfaces:**
- Consome: `FieldDictionary`, `JqlValues`.
- Produz:
  ```kotlin
  class JqlBuilder(private val dict: FieldDictionary, private val instance: String) {
      fun inList(field: String, values: List<String>): JqlBuilder          // field in (...)
      fun notInList(field: String, values: List<String>): JqlBuilder       // field not in (...)
      fun equalsTo(field: String, value: String): JqlBuilder
      fun users(field: String, usernames: List<String>): JqlBuilder        // assignee in (a, b) usa username puro
      fun dateAtLeast(field: String, isoDate: String): JqlBuilder          // field >= "aaaa-mm-dd"
      fun dateAtMost(field: String, isoDate: String): JqlBuilder
      fun relativeDaysAtLeast(field: String, days: Int): JqlBuilder        // field >= startOfDay(-N)
      fun labelsNotIn(values: List<String>): JqlBuilder
      fun orderBy(field: String, asc: Boolean = true): JqlBuilder
      fun build(): String                                                  // cláusulas ligadas por " AND "
  }
  ```
  `inList`/`notInList` com lista vazia lançam `IllegalArgumentException("lista vazia para <campo>")`. Valores de campos `SELECT` com `knownValues` só aceitam o que está na lista; campos sem lista (`project`, `status`, `issuetype`, `Model Name`) aceitam texto livre **somente** via `JqlValues.safeText(value)`, que rejeita `"`, `\`, quebra de linha e caracteres de controle e limita a 120 caracteres (os valores saem sempre entre aspas duplas, então aspas e barra são o único jeito de escapar do literal; parênteses como em `Android 17.0 (OneUI_9.0)` são válidos). Acrescentar a `JqlValues` (Tarefa 2) a função `safeText` e o teste `safeTextRejeitaAspasEBarra` (`x") OR project is not EMPTY OR ("` lança; `Android 17.0 (OneUI_9.0)` passa).

- [ ] **Passo 1: Testes que falham** (strings esperadas exatas):
  - `jqlDoSquadAbertas`: `JqlBuilder(dict,"samsung").inList("project",listOf("TG-Basic")).users("assignee",listOf("thiago.macedo","wendell.marques")).notInList("status",listOf("Resolved","Closed","Cancelled")).relativeDaysAtLeast("created",100).build()` → `project in ("TG-Basic") AND assignee in ("thiago.macedo", "wendell.marques") AND status not in ("Resolved", "Closed", "Cancelled") AND created >= startOfDay(-100)` (usernames sempre entre aspas; nos testes reais os nomes são fictícios, `dev.alfa`, `dev.beta`).
  - `campoComEspaco`: `inList("OS Version", listOf("Android 17.0 (OneUI_9.0)"))` → `"OS Version" in ("Android 17.0 (OneUI_9.0)")`.
  - `ordenacao`: `.orderBy("resolved").orderBy("duedate", asc = false).build()` termina com ` ORDER BY resolved ASC, duedate DESC`.
  - `tentativaDeInjecaoFalha`: `users("assignee", listOf("x\") OR project is not EMPTY OR (\""))` lança `InvalidJqlValueException`.
  - `listaVaziaFalha`; `campoForaDoDicionarioFalha` (`UnknownFieldException`).
  - `valorForaDosConhecidosFalha`: `inList("OS Version", listOf("Android 99"))` lança.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*JqlBuilderTest*'` → FALHA.
- [ ] **Passo 3: Implementar** acumulando cláusulas já validadas; a única forma de gerar texto é este builder.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Construtor de JQL com validação"`.

---

### Tarefa 4: Ficha de card (`CardSpec`) e `CardQueryFactory`

**Arquivos:**
- Criar: `jql/CardSpec.kt`, `jql/CardQueryFactory.kt`, `app/config/cards/tempo-em-status.card.yaml`
- Teste: `jql/CardQueryFactoryTest.kt`

**Interfaces:**
- Consome: `FieldDictionary`, `JqlBuilder`.
- Produz:
  ```kotlin
  data class CardParam(val name: String, val type: String, val required: Boolean = true)       // type: squad|periodo|os|pessoa|tipo
  data class CardSpec(val id: String, val titulo: String, val instancia: String, val campos: List<String>,
                      val filtrosFixos: Map<String, List<String>>, val parametros: List<CardParam>,
                      val formula: String, val exemplo: String, val aceite: List<String>, val expandChangelog: Boolean)
  data class JqlQuery(val instance: String, val jql: String, val fields: List<String>, val expandChangelog: Boolean)
  object CardSpecs { fun fromYaml(path: Path, dict: FieldDictionary): CardSpec }       // falha se `campos` ou chaves de `filtrosFixos` não estão no dicionário
  class CardQueryFactory(private val dict: FieldDictionary) {
      fun build(spec: CardSpec, params: Map<String, Any>): JqlQuery
  }
  ```
  Ficha do Tempo em status: instância `samsung`, `project in ("TG-Basic")`, tipos `Checklist`, `Checklist TG`, `Checklist Review`; `status not in (Cancelled, Canceled, Dropped)`; `resolved >= <início>` e `resolved < <dia seguinte ao fim>` (o fim do período é inclusivo, então o limite no JQL é exclusivo); parâmetros `squad` (lista de usernames vinda do papéis YAML), `periodo` (`de`, `ate` ISO), `os` (opcional). `fields` mínimos: `summary, issuetype, status, assignee, reporter, created, updated, resolutiondate, duedate, labels, parent, project` + os do dicionário; `expandChangelog=true`.

- [ ] **Passo 1: Testes que falham:**
  - `fichaDoTempoEmStatusCarrega`: a ficha carrega e `parametros` tem `squad`, `periodo`.
  - `fichaComCampoForaDoDicionarioFalha` (YAML de teste com `campos: [Inventado]`) → `UnknownFieldException` na carga.
  - `jqlSnapshotTempoEmStatus`: com `squad=[thiago.macedo,wendell.marques]`, `periodo=(2026-07-26, 2026-10-24)`, `os=null` → `project in ("TG-Basic") AND issuetype in ("Checklist", "Checklist TG", "Checklist Review") AND assignee in ("thiago.macedo", "wendell.marques") AND status not in ("Cancelled", "Canceled", "Dropped") AND resolved >= "2026-07-26" AND resolved < "2026-10-25"` (fim 2026-10-24 inclusivo vira `< 2026-10-25`).
  - `osOpcionalEntraQuandoInformado`: com `os=["Android 17.0 (OneUI_9.0)"]` o JQL ganha `AND "OS Version" in ("Android 17.0 (OneUI_9.0)")`.
  - `parametroObrigatorioAusenteFalha`: sem `periodo` → `IllegalArgumentException` citando `periodo`.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*CardQueryFactoryTest*'` → FALHA.
- [ ] **Passo 3: Implementar** a carga e a fábrica usando só o `JqlBuilder`.
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Ficha de card e fábrica de consultas"`.

---

### Tarefa 5: Documentos de preparação para a 1ª conexão

**Arquivos:**
- Criar: `docs/jira/matriz-compatibilidade.md`, `docs/jira/checklist-primeira-conexao.md`
- Teste: `app/backend/src/test/kotlin/br/sidia/painel/jql/PreparacaoDocsTest.kt`

**Conteúdo exigido:**
- **Matriz de compatibilidade:** tabela com colunas *Item*, *Validado no bscbi-web*, *Neste app*, *Como confirmar*. Linhas mínimas: `POST /rest/api/2/search`; Bearer PAT (DC) e Basic (Sidia); `expand=changelog`; totais com `maxResults=1`; filtros salvos do Jira; campos por nome no JQL; limite de 5.000 por página; paginação de `mps/all`; `dateCompare` do ScriptRunner (**não** usado, regra calculada em código); taxonomia de labels de atraso justificado; tipos de issue do TG-Basic; ids `cf[12416]`/`cf[11412]`.
- **Checklist da 1ª conexão autorizada:** lista **mínima** e ordenada de chamadas a pedir ao usuário, cada uma com *o que conferir* e *quando parar*: (1) `GET /rest/api/2/myself` (credencial e fuso); (2) `GET /rest/api/2/field` (ids dos campos do dicionário); (3) `POST /search` com `maxResults=1` para cada ficha (contagem de referência); (4) 1 issue com `expand=changelog` (formato do `from/to` do assignee, changelog truncado); (5) `GET /rest/api/2/status` (mapa de status). Cada passo diz "**peça autorização expressa antes**".

- [ ] **Passo 1: Teste que falha.** `PreparacaoDocsTest.checklistETemTodasAsChamadas`: lê `docs/jira/checklist-primeira-conexao.md` e exige as cinco chamadas e a frase `autorização expressa`; `matrizTemAsLinhasMinimas` exige as 11 linhas acima pelo texto do item.
- [ ] **Passo 2: Rodar** `./gradlew test --tests '*PreparacaoDocsTest*'` → FALHA.
- [ ] **Passo 3: Escrever os dois documentos** com o conteúdo exigido (fatos do `docs/analise` e do documento do bscbi-web em `Requirements-TG/bscbi-web/docs/jira-integration-reverse-engineering.md`, só leitura).
- [ ] **Passo 4: Rodar** → PASSA. **Passo 5: Commit** `git commit -m "Matriz de compatibilidade e checklist da 1ª conexão"`.

---

## Autoavaliação

- **Cobertura da spec:** 5.1 (T1), 5.2 (T2–T4), 5.5 (T1 e T5). Cliente Jira e sincronização ficam nos Planos 01 e 03.
- **Consistência de tipos:** `FieldDictionary` (T1) → `JqlBuilder` (T3) → `CardSpecs`/`CardQueryFactory` (T4); `JqlQuery` é o contrato com a sincronização da Fase 3 (`JiraClient.search(SearchRequest(jql, fields, expandChangelog))`).
- **Pendências deliberadas:** descoberta de ids por nome (`/field`) e verificação de campos reais só na 1ª conexão autorizada.
