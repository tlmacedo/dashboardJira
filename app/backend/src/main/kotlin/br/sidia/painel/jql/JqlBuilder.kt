package br.sidia.painel.jql

/**
 * Construtor de JQL: a ÚNICA forma de gerar texto JQL no projeto.
 *
 * Só compõe blocos já validados ([FieldDictionary] e [JqlValues]); nunca concatena texto de entrada.
 * Operadores permitidos: `=`, `!=`, `in`, `not in`, `>=`, `<=`, `<` e `ORDER BY`. Não há busca de texto (`~`).
 * Os campos vêm só da instância informada, para não haver ambiguidade entre instâncias.
 */
class JqlBuilder(dict: FieldDictionary, instance: String) {
    private val dict: FieldDictionary = dict.forInstance(instance)
    private val clausulas = mutableListOf<String>()
    private val ordenacao = mutableListOf<String>()
    private val camposOrdenados = mutableSetOf<String>()

    /** `campo in ("a", "b")`. */
    fun inList(field: String, values: List<String>): JqlBuilder = lista(field, values, "in")

    /** `campo not in ("a", "b")`. */
    fun notInList(field: String, values: List<String>): JqlBuilder = lista(field, values, "not in")

    /** `campo = "valor"`. */
    fun equalsTo(field: String, value: String): JqlBuilder {
        val def = campoDeValores(field, "equalsTo")
        clausulas += "${JqlValues.quotedField(def.name)} = ${valor(def, value)}"
        return this
    }

    /**
     * `assignee in ("a", "b")`: usernames validados e SEMPRE entre aspas duplas, o que neutraliza
     * palavras reservadas do JQL (`empty`, `null`, `or`, `order`...). Só em campo do tipo USER.
     */
    fun users(field: String, usernames: List<String>): JqlBuilder {
        val def = dict.get(field)
        exigirTipo(def, "users", FieldType.USER)
        exigirNaoVazia(def, usernames)
        val itens = usernames.joinToString(", ") { "\"${JqlValues.username(it)}\"" }
        clausulas += "${JqlValues.quotedField(def.name)} in ($itens)"
        return this
    }

    /** `campo >= "aaaa-mm-dd"`. Só em campo do tipo DATE. */
    fun dateAtLeast(field: String, isoDate: String): JqlBuilder = data(field, isoDate, ">=", "dateAtLeast")

    /** `campo <= "aaaa-mm-dd"`. Só em campo do tipo DATE. */
    fun dateAtMost(field: String, isoDate: String): JqlBuilder = data(field, isoDate, "<=", "dateAtMost")

    /** `campo < "aaaa-mm-dd"` (exclusivo). Só em campo do tipo DATE. */
    fun dateBefore(field: String, isoDate: String): JqlBuilder = data(field, isoDate, "<", "dateBefore")

    /** `campo >= startOfDay(-N)`. Só em campo do tipo DATE. */
    fun relativeDaysAtLeast(field: String, days: Int): JqlBuilder {
        val def = dict.get(field)
        exigirTipo(def, "relativeDaysAtLeast", FieldType.DATE)
        clausulas += "${JqlValues.quotedField(def.name)} >= ${JqlValues.relativeDays(days)}"
        return this
    }

    /** `labels not in ("a", "b")`. Exige o campo `labels` (tipo LABELS) no dicionário. */
    fun labelsNotIn(values: List<String>): JqlBuilder {
        val def = dict.get("labels")
        exigirTipo(def, "labelsNotIn", FieldType.LABELS)
        exigirNaoVazia(def, values)
        val itens = values.joinToString(", ") { valor(def, it) }
        clausulas += "${JqlValues.quotedField(def.name)} not in ($itens)"
        return this
    }

    /** Acrescenta um critério de ordenação; só campos do dicionário. Vai no fim, em `ORDER BY`. */
    fun orderBy(field: String, asc: Boolean = true): JqlBuilder {
        val def = dict.get(field)
        require(camposOrdenados.add(def.name.lowercase())) { "orderBy repetido para o campo '${def.name}'" }
        ordenacao +="${JqlValues.quotedField(def.name)} ${if (asc) "ASC" else "DESC"}"
        return this
    }

    /** Cláusulas ligadas por ` AND `, seguidas de ` ORDER BY ...` se houver. Sem cláusula de filtro, lança. */
    fun build(): String {
        check(clausulas.isNotEmpty()) { "JQL sem nenhuma cláusula de filtro" }
        val base = clausulas.joinToString(" AND ")
        return if (ordenacao.isEmpty()) base else "$base ORDER BY ${ordenacao.joinToString(", ")}"
    }

    // --- internos ---

    private fun lista(field: String, values: List<String>, operador: String): JqlBuilder {
        val def = campoDeValores(field, if (operador == "in") "inList" else "notInList")
        exigirNaoVazia(def, values)
        val itens = values.joinToString(", ") { valor(def, it) }
        clausulas += "${JqlValues.quotedField(def.name)} $operador ($itens)"
        return this
    }

    private fun data(field: String, isoDate: String, operador: String, metodo: String): JqlBuilder {
        val def = dict.get(field)
        exigirTipo(def, metodo, FieldType.DATE)
        clausulas += "${JqlValues.quotedField(def.name)} $operador \"${JqlValues.isoDate(isoDate)}\""
        return this
    }

    /** Campo que aceita comparação de valores textuais: SELECT, TEXT, USER e LABELS (não DATE/NUMBER). */
    private fun campoDeValores(field: String, metodo: String): FieldDef {
        val def = dict.get(field)
        exigirTipo(def, metodo, FieldType.SELECT, FieldType.TEXT, FieldType.USER, FieldType.LABELS)
        return def
    }

    private fun exigirTipo(def: FieldDef, metodo: String, vararg aceitos: FieldType) {
        require(def.type in aceitos) {
            "$metodo não se aplica ao campo '${def.name}' do tipo ${def.type} (aceita: ${aceitos.joinToString()})"
        }
    }

    private fun exigirNaoVazia(def: FieldDef, values: List<String>) {
        require(values.isNotEmpty()) { "lista vazia para ${def.name}" }
    }

    /**
     * Valor já validado e entre aspas duplas: username (validado) para USER, o que neutraliza palavras
     * reservadas do JQL; literal fechado se há lista de conhecidos; senão texto seguro.
     */
    private fun valor(def: FieldDef, value: String): String = when {
        def.type == FieldType.USER -> "\"${JqlValues.username(value)}\""
        def.knownValues.isNotEmpty() -> JqlValues.literal(value, def.knownValues)
        else -> JqlValues.safeText(value)
    }
}
