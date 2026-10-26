package br.sidia.painel.jql

import java.time.LocalDate

/**
 * Período fechado [de, ate]; `de <= ate`.
 *
 * `ate` é INCLUSIVO: a tela e o usuário falam em "até 24/10" querendo dizer o dia 24 inteiro.
 * No Jira, `campo <= "2026-10-24"` compara com 2026-10-24 00:00 e deixaria de fora tudo o que
 * foi resolvido durante o dia 24. Por isso a fábrica gera `campo < "2026-10-25"` (dia seguinte,
 * exclusivo). O último dia do calendário aceito (9999-12-31) é recusado, pois não tem dia seguinte válido.
 */
data class Periodo(val de: LocalDate, val ate: LocalDate) {
    init {
        require(!de.isAfter(ate)) { "periodo invertido: de=$de é depois de ate=$ate" }
        require(ate.isBefore(LocalDate.of(9999, 12, 31))) {
            "periodo: 'ate' não pode ser o último dia do calendário (9999-12-31)"
        }
    }

    /** Primeiro dia fora do período (limite exclusivo usado no JQL). */
    val limiteExclusivo: LocalDate get() = ate.plusDays(1)
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

    /**
     * Mesma consulta de [build] (mesmo JQL), mas pedindo só `key` e `updated` e sem o changelog:
     * serve para a sincronização descobrir o que mudou sem baixar o conteúdo todo.
     */
    fun buildLeve(spec: CardSpec, params: Map<String, Any>): JqlQuery =
        JqlQuery(spec.instancia, montarJql(spec, params), listOf("key", "updated"), expandChangelog = false)

    fun build(spec: CardSpec, params: Map<String, Any>): JqlQuery {
        val jql = montarJql(spec, params)
        val campoDicionario = dict.forInstance(spec.instancia)
        val minimos = listOf(
            "summary", "issuetype", "status", "assignee", "reporter", "created", "updated",
            "resolutiondate", "duedate", "labels", "parent", "project",
            // Campo de sistema (não é do dicionário): os vínculos Blocks ligam a Review à sua task.
            "issuelinks",
        )
        // Campo com id técnico sai como customfield_NNNN (cf[NNNN] é normalizado); sem id, sai pelo nome.
        val extras = spec.campos.map { c -> campoDicionario.get(c).let { idParaFields(it.customId) ?: it.name } }
        val fields = (minimos + extras).distinctBy { it.lowercase() }
        return JqlQuery(spec.instancia, jql, fields, spec.expandChangelog)
    }

    /** Valida os parâmetros e monta o JQL, sempre pelo [JqlBuilder]. */
    private fun montarJql(spec: CardSpec, params: Map<String, Any>): String {
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
            b.dateAtLeast(campo, periodo.de.toString()).dateBefore(campo, periodo.limiteExclusivo.toString())
        }
        valorDe("os")?.let { (p, v) -> b.inList(CAMPO_DO_PARAMETRO.getValue("os"), listaDeTexto(p, v)) }

        valorDe("atualizadoDesde")?.let { (p, v) ->
            require(v is AtualizadoDesde) { "parâmetro '${p.name}' deve ser um AtualizadoDesde (instante e fuso do servidor)" }
            b.dateTimeAtLeast(CAMPO_DO_PARAMETRO.getValue("atualizadoDesde"), v.at, v.fusoDoServidor)
        }
        valorDe("chaves")?.let { (p, v) -> b.keysIn(listaDeTexto(p, v)) }

        if (spec.ordenarPorAtualizacao) {
            CAMPOS_DA_ORDENACAO.forEach { b.orderBy(it, asc = true) }
        }
        return b.build()
    }

    /** `cf[12416]` vira `customfield_12416`; `customfield_NNNN` fica como está; nulo continua nulo. */
    private fun idParaFields(customId: String?): String? = when {
        customId == null -> null
        customId.startsWith("cf[") && customId.endsWith("]") -> "customfield_" + customId.substring(3, customId.length - 1)
        else -> customId
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
            throw IllegalArgumentException("parâmetro '${p.name}': data inválida '${amostra(v)}'", e)
        }
        else -> throw IllegalArgumentException("parâmetro '${p.name}': data deve ser LocalDate ou aaaa-mm-dd")
    }
}
