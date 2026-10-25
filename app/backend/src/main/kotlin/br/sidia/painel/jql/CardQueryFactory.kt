package br.sidia.painel.jql

import java.time.LocalDate

/** Período fechado [de, ate]; `de <= ate`. */
data class Periodo(val de: LocalDate, val ate: LocalDate) {
    init {
        require(!de.isAfter(ate)) { "periodo invertido: de=$de é depois de ate=$ate" }
    }
}

/**
 * Monta a consulta de um card a partir da ficha e dos parâmetros. Todo JQL sai do [JqlBuilder];
 * nenhum valor de `params` é concatenado em texto.
 */
class CardQueryFactory(private val dict: FieldDictionary) {
    private companion object {
        /** Máximo de itens numa lista de parâmetro (squad, os, tipo). */
        const val MAX_ITENS = 200
    }

    fun build(spec: CardSpec, params: Map<String, Any>): JqlQuery {
        val declarados = spec.parametros.associateBy { it.name }
        params.keys.forEach { require(it in declarados) { "parâmetro desconhecido: '$it'" } }
        spec.parametros.forEach {
            require(!it.required || params.containsKey(it.name)) { "parâmetro obrigatório ausente: '${it.name}'" }
        }
        fun valorDe(tipo: String): Pair<CardParam, Any>? =
            spec.parametros.firstOrNull { it.type == tipo }?.let { p -> params[p.name]?.let { p to it } }

        val b = JqlBuilder(dict, spec.instancia)
        spec.filtrosFixos.forEach { (campo, valores) -> b.inList(campo, valores) }

        valorDe("squad")?.let { (p, v) -> b.users(CAMPO_DO_PARAMETRO.getValue("squad"), listaDeTexto(p, v)) }
        valorDe("pessoa")?.let { (p, v) ->
            require(v is String) { "parâmetro '${p.name}' deve ser um username (texto)" }
            b.users(CAMPO_DO_PARAMETRO.getValue("pessoa"), listOf(v))
        }
        valorDe("tipo")?.let { (p, v) -> b.inList(CAMPO_DO_PARAMETRO.getValue("tipo"), listaDeTexto(p, v)) }

        spec.filtrosExcluidos.forEach { (campo, valores) -> b.notInList(campo, valores) }

        valorDe("periodo")?.let { (p, v) ->
            val periodo = periodoDe(p, v)
            val campo = CAMPO_DO_PARAMETRO.getValue("periodo")
            b.dateAtLeast(campo, periodo.de.toString()).dateAtMost(campo, periodo.ate.toString())
        }
        valorDe("os")?.let { (p, v) -> b.inList(CAMPO_DO_PARAMETRO.getValue("os"), listaDeTexto(p, v)) }

        val campoDicionario = dict.forInstance(spec.instancia)
        val minimos = listOf(
            "summary", "issuetype", "status", "assignee", "reporter", "created", "updated",
            "resolutiondate", "duedate", "labels", "parent", "project",
        )
        val extras = spec.campos.map { c -> campoDicionario.get(c).let { it.customId ?: it.name } }
        val fields = (minimos + extras).distinctBy { it.lowercase() }
        return JqlQuery(spec.instancia, b.build(), fields, spec.expandChangelog)
    }

    private fun listaDeTexto(p: CardParam, v: Any): List<String> {
        require(v is List<*> && v.all { it is String }) { "parâmetro '${p.name}' deve ser uma lista de textos" }
        require(v.size <= MAX_ITENS) { "parâmetro '${p.name}' com ${v.size} itens; o limite é $MAX_ITENS" }
        return v.map { it as String }
    }

    private fun periodoDe(p: CardParam, v: Any): Periodo = when (v) {
        is Periodo -> v
        is Pair<*, *> -> Periodo(data(p, v.first), data(p, v.second))
        is Map<*, *> -> {
            require(v.containsKey("de") && v.containsKey("ate")) { "parâmetro '${p.name}' exige 'de' e 'ate'" }
            require(v.keys.all { it == "de" || it == "ate" }) { "parâmetro '${p.name}' aceita só 'de' e 'ate'" }
            Periodo(data(p, v["de"]), data(p, v["ate"]))
        }
        else -> throw IllegalArgumentException("parâmetro '${p.name}' deve ter 'de' e 'ate'")
    }

    private fun data(p: CardParam, v: Any?): LocalDate = when (v) {
        is LocalDate -> v
        is String -> try {
            LocalDate.parse(JqlValues.isoDate(v))
        } catch (e: InvalidJqlValueException) {
            throw IllegalArgumentException("parâmetro '${p.name}': data inválida '${v.take(20)}'", e)
        }
        else -> throw IllegalArgumentException("parâmetro '${p.name}': data deve ser LocalDate ou aaaa-mm-dd")
    }
}
