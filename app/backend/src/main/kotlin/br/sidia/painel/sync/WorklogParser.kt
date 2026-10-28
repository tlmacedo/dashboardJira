package br.sidia.painel.sync

import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Um registro de horas do Jira, SEM comentário: só o id do Jira, o autor (username normalizado; chave interna sem
 * tradução fica em maiúsculas), o início e os segundos.
 */
data class WorklogRecord(val jiraId: String, val autor: String?, val started: Instant, val seconds: Long)

/**
 * Lê o worklog do Jira Data Center (`fields.worklog.worklogs` ou o endpoint `issue/{key}/worklog`). Só `id`, `author`,
 * `started` e `timeSpentSeconds` são lidos: `comment`, `updateAuthor`, `self`, `created` e `updated` NUNCA são tocados, então
 * nenhum texto livre nem dado de exibição de pessoa chega ao banco.
 */
object WorklogParser {
    private val log = LoggerFactory.getLogger(WorklogParser::class.java)
    private const val TAMANHO_MAXIMO_DO_ID = 64

    /**
     * Um registro por `id` (o primeiro vence), em ordem de início. Item malformado (sem id, início ilegível, segundos ausentes
     * ou negativos) é ignorado sem exceção; o log leva só a contagem. [chaves] traduz `JIRAUSER*` em username.
     */
    fun toRecords(worklogs: List<JsonObject>, chaves: Map<String, String>): List<WorklogRecord> {
        val porId = LinkedHashMap<String, WorklogRecord>()
        var ignorados = 0
        for (w in worklogs) {
            val r = converter(w, chaves)
            if (r == null) ignorados++ else porId.putIfAbsent(r.jiraId, r)
        }
        if (ignorados > 0) log.warn("Registros de worklog malformados ignorados: {}", ignorados)
        return porId.values.sortedWith(compareBy({ it.started }, { it.jiraId }))
    }

    private fun converter(w: JsonObject, chaves: Map<String, String>): WorklogRecord? {
        val id = w.texto("id")?.trim()?.takeIf { it.isNotEmpty() && it.length <= TAMANHO_MAXIMO_DO_ID } ?: return null
        val inicio = w.texto("started")?.let {
            try {
                ChangelogParser.parseInstant(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return null
        val segundos = (w["timeSpentSeconds"] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 } ?: return null
        return WorklogRecord(id, autor(w, chaves), inicio, segundos)
    }

    /** `name` normalizado; chave interna (`JIRAUSER*`, no nome ou só na `key`) traduzida por [chaves] ou, sem tradução, em maiúsculas. */
    private fun autor(w: JsonObject, chaves: Map<String, String>): String? {
        val usuario = w["author"] as? JsonObject ?: return null
        val bruto = usuario.texto("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: usuario.texto("key")?.trim()?.takeIf { ChavesDeUsuario.ehChaveInterna(it) }
            ?: return null
        return if (ChavesDeUsuario.ehChaveInterna(bruto)) chaves[bruto.uppercase()] ?: bruto.uppercase() else normalizarUsername(bruto)
    }
}
