package br.sidia.painel.sync

import br.sidia.painel.jql.FieldDictionary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Um campo do dicionário que vira filtro e agrupamento nos cards (Plano 10). [slug] é o que vai na URL
 * (`campo.<slug>=<valor>`); [customId] fica nulo até a primeira conexão descobrir o id do campo.
 */
data class CampoDeFiltro(val nome: String, val slug: String, val customId: String?)

/** Extração dos campos de filtro do dicionário (Checklist Type, SKU Type, Model Name etc.) do JSON da issue. */
object CamposDoDicionario {
    /** `Checklist Type` -> `checklist-type`: minúsculas, qualquer sequência fora de letra e dígito vira um hífen. */
    fun slugDe(nome: String): String =
        nome.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Os campos com `filtro: true` da [instancia], na ordem do YAML. Campo sem customId também entra (id nulo). */
    fun deFiltro(dic: FieldDictionary, instancia: String): List<CampoDeFiltro> =
        dic.forInstance(instancia).todos().filter { it.filtro }.map { CampoDeFiltro(it.name, slugDe(it.name), it.customId) }

    /**
     * Nome do campo -> valor, só dos campos presentes e não vazios. SELECT (`{value}` ou `{name}`) e TEXTO (string)
     * são lidos; uma lista vira os valores unidos por "; ". Espaços nas pontas saem e os internos se colapsam (a mesma
     * normalização do OS Version). Formato inesperado (número, objeto sem `value`, nulo) é ignorado em silêncio.
     */
    fun extrair(issue: JsonObject, campos: List<CampoDeFiltro>): Map<String, String> {
        val f = issue["fields"] as? JsonObject ?: return emptyMap()
        val saida = LinkedHashMap<String, String>()
        for (c in campos) {
            val chave = OsVersionExtrator.chaveJson(c.customId) ?: continue
            val valor = texto(f[chave]) ?: continue
            saida[c.nome] = valor
        }
        return saida
    }

    /** Os valores de um campo repetível (lista unida por "; "); um valor simples devolve ele mesmo. */
    fun partesDe(valor: String): List<String> = valor.split("; ").map { it.trim() }.filter { it.isNotEmpty() }

    /** Texto da coluna `issue.campos_dicionario` (objeto JSON nome -> valor). */
    fun paraJson(valores: Map<String, String>): String =
        JsonObject(valores.mapValues { JsonPrimitive(it.value) }).toString()

    /** Lê a coluna; nula, vazia ou malformada dá mapa vazio (a leitura nunca quebra por causa dela). */
    fun deJson(texto: String?): Map<String, String> {
        if (texto.isNullOrBlank()) return emptyMap()
        val objeto = try {
            kotlinx.serialization.json.Json.parseToJsonElement(texto) as? JsonObject
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        } ?: return emptyMap()
        return objeto.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }.toMap()
    }

    private fun texto(e: JsonElement?): String? = when (e) {
        null, is JsonNull -> null
        is JsonPrimitive -> e.takeIf { it.isString }?.content
        is JsonObject -> ((e["value"] ?: e["name"]) as? JsonPrimitive)?.takeIf { it.isString }?.content
        is JsonArray -> e.mapNotNull { texto(it) }.map(::normalizarOsVersion).filter { it.isNotEmpty() }
            .joinToString("; ").ifEmpty { null }
    }?.let(::normalizarOsVersion)?.ifEmpty { null }
}
