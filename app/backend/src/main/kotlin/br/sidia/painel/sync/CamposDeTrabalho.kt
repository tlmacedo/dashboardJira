package br.sidia.painel.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Campos de trabalho de uma issue (Plano 5): prioridade, estimativa restante (segundos), pontos, épico, flag de
 * impedimento e componentes. Nenhum é dado pessoal. Campo ausente ou ilegível vira nulo (flag `false`, componentes
 * vazios): o card avisa que o dado não existe, nunca mostra zero.
 */
data class CamposDeTrabalho(
    val priority: String?,
    val remainingS: Long?,
    val storyPoints: Double?,
    val epicKey: String?,
    val flagged: Boolean,
    val components: List<String>,
)

object CamposDeTrabalhoExtrator {
    /** Nomes do dicionário (`jira_campos`) dos campos que dependem de id customizado. */
    const val STORY_POINTS = "Story Points"
    const val EPIC_LINK = "Epic Link"
    const val FLAGGED = "Flagged"

    private val CHAVE_DE_ISSUE = Regex("[A-Za-z][A-Za-z0-9_]*-\\d+")

    /**
     * [ids]: nome do dicionário -> customId (`customfield_N` ou `cf[N]`); id ausente ou nulo deixa o campo nulo.
     * Nunca lança por causa do formato do JSON do Jira.
     */
    fun extrair(issue: JsonObject, ids: Map<String, String?>): CamposDeTrabalho {
        val f = issue["fields"] as? JsonObject ?: JsonObject(emptyMap())

        fun custom(nome: String): JsonElement? = OsVersionExtrator.chaveJson(ids[nome])?.let { f[it] }

        val prioridade = ((f["priority"] as? JsonObject)?.get("name") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
        val restante = (f["timeestimate"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 }
        // Pontos só valem como NÚMERO do JSON: texto, booleano, lista e objeto ficam nulos.
        val pontos = (custom(STORY_POINTS) as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }
            ?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
        val epico = (custom(EPIC_LINK) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.takeIf { CHAVE_DE_ISSUE.matches(it) }
        // Flagged do Jira é uma lista de opções; qualquer item presente = impedimento ligado.
        val flag = (custom(FLAGGED) as? JsonArray)?.isNotEmpty() == true
        val componentes = (f["components"] as? JsonArray).orEmpty().mapNotNull { c ->
            ((c as? JsonObject)?.get("name") as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
        }
        return CamposDeTrabalho(prioridade, restante, pontos, epico, flag, componentes)
    }
}
