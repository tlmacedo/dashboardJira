package br.sidia.painel.sync

import br.sidia.painel.domain.AssigneeChange
import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.domain.Transition
import br.sidia.painel.security.normalizarUsername
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException

/**
 * Um item do changelog gravado em `change_event`. [changelogId] é único e estável
 * (chave da issue + id do histórico + posição do item), o que torna a gravação idempotente.
 * Para `assignee`, [from] e [to] são usernames normalizados (ou nome de exibição normalizado, se só houver texto).
 */
data class ChangeEventRecord(
    val changelogId: String,
    val issueKey: String,
    val field: String,
    val from: String?,
    val to: String?,
    val at: Instant,
    val authorRef: String?,
)

/** Converte o JSON do Jira DC (issue + históricos do changelog) em linha do tempo e em eventos. */
object ChangelogParser {
    private val OFFSET_SEM_DOIS_PONTOS: DateTimeFormatter = DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .appendOffset("+HHmm", "Z")
        .toFormatter()

    /** Aceita `2026-10-05T08:00:00.000-0400` (padrão do DC), `-04:00` e `Z`. Falha com [IllegalArgumentException]. */
    fun parseInstant(texto: String): Instant {
        val t = texto.trim()
        try {
            return OffsetDateTime.parse(t, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            // tenta o formato do DC, sem dois-pontos no offset
        }
        try {
            return OffsetDateTime.parse(t, OFFSET_SEM_DOIS_PONTOS).toInstant()
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Data do Jira em formato desconhecido")
        }
    }

    /** Linha do tempo da issue a partir do JSON dela e dos históricos (embutidos ou completos). */
    fun toTimeline(issue: JsonObject, changelog: List<JsonObject>): IssueTimeline {
        val c = CamposDaIssue.de(issue)
        return montarTimeline(
            key = c.key, summary = c.summary, issueType = c.issueType,
            created = requireNotNull(c.created) { "Issue ${c.key} sem data de criação" },
            status = c.status, assignee = c.assignee, resolved = c.resolved, resolution = c.resolution,
            labels = c.labels, events = toEvents(issue, changelog),
        )
    }

    /** Um evento por item de cada histórico, em ordem cronológica. */
    fun toEvents(issue: JsonObject, changelog: List<JsonObject>): List<ChangeEventRecord> {
        val key = requireNotNull(issue.texto("key")) { "Issue sem chave" }
        val eventos = mutableListOf<ChangeEventRecord>()
        for (h in changelog) {
            val historicoId = h.texto("id") ?: continue
            val quando = parseInstant(h.texto("created") ?: continue)
            val autor = (h["author"] as? JsonObject)?.texto("name")?.takeIf { it.isNotBlank() }?.let(::normalizarUsername)
            val itens = h["items"] as? JsonArray ?: continue
            itens.forEachIndexed { i, el ->
                val item = el as? JsonObject ?: return@forEachIndexed
                val campo = item.texto("field") ?: return@forEachIndexed
                val ehResponsavel = campo.equals("assignee", ignoreCase = true)
                val de = if (ehResponsavel) usuario(item, "from", "fromString") else item.texto("fromString")
                val para = if (ehResponsavel) usuario(item, "to", "toString") else item.texto("toString")
                eventos += ChangeEventRecord("$key:$historicoId:$i", key, campo, de, para, quando, autor)
            }
        }
        return eventos.sortedBy { it.at }
    }

    /** Responsável: usa o username (`from`/`to`) quando presente; senão o texto (nome de exibição). */
    private fun usuario(item: JsonObject, chave: String, chaveTexto: String): String? =
        (item.texto(chave)?.takeIf { it.isNotBlank() } ?: item.texto(chaveTexto)?.takeIf { it.isNotBlank() })
            ?.let(::normalizarUsername)

    /**
     * Monta a linha do tempo a partir do estado atual da issue e dos eventos. O status/responsável
     * inicial é o `from` do primeiro evento do campo; sem eventos, o valor atual.
     */
    internal fun montarTimeline(
        key: String,
        summary: String,
        issueType: String,
        created: Instant,
        status: String,
        assignee: String?,
        resolved: Instant?,
        resolution: String?,
        labels: List<String>,
        events: List<ChangeEventRecord>,
    ): IssueTimeline {
        val ordenados = events.sortedBy { it.at }
        val statusEv = ordenados.filter { it.field.equals("status", ignoreCase = true) && it.to != null }
        val respEv = ordenados.filter { it.field.equals("assignee", ignoreCase = true) }
        return IssueTimeline(
            key = key,
            summary = summary,
            issueType = issueType,
            created = created,
            initialStatus = statusEv.firstOrNull()?.from ?: status,
            initialAssignee = if (respEv.isEmpty()) assignee else respEv.first().from,
            transitions = statusEv.map { Transition(it.at, it.from, it.to!!, it.authorRef) },
            assigneeChanges = respEv.map { AssigneeChange(it.at, it.to) },
            resolved = resolved,
            resolution = resolution,
            currentStatus = status,
            currentAssignee = assignee,
            labels = labels,
        )
    }
}

/** Campos da issue do DC já extraídos (usernames normalizados). */
internal class CamposDaIssue(
    val key: String,
    val project: String,
    val summary: String,
    val issueType: String,
    val status: String,
    val resolution: String?,
    val assignee: String?,
    val reporter: String?,
    val created: Instant?,
    val updated: Instant?,
    val resolved: Instant?,
    val duedate: String?,
    val labels: List<String>,
    val parentKey: String?,
    val estimateS: Long?,
    val spentS: Long?,
) {
    companion object {
        fun de(issue: JsonObject): CamposDaIssue {
            val key = requireNotNull(issue.texto("key")) { "Issue sem chave" }
            val f = issue["fields"] as? JsonObject ?: JsonObject(emptyMap())
            fun data(nome: String) = f.texto(nome)?.let(ChangelogParser::parseInstant)
            fun usuario(nome: String) = (f[nome] as? JsonObject)?.texto("name")?.takeIf { it.isNotBlank() }?.let(::normalizarUsername)
            return CamposDaIssue(
                key = key,
                project = (f["project"] as? JsonObject)?.texto("key") ?: key.substringBeforeLast('-'),
                summary = f.texto("summary").orEmpty(),
                issueType = (f["issuetype"] as? JsonObject)?.texto("name").orEmpty(),
                status = (f["status"] as? JsonObject)?.texto("name").orEmpty(),
                resolution = (f["resolution"] as? JsonObject)?.texto("name"),
                assignee = usuario("assignee"),
                reporter = usuario("reporter"),
                created = data("created"),
                updated = data("updated"),
                resolved = data("resolutiondate"),
                duedate = f.texto("duedate"),
                labels = (f["labels"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty(),
                parentKey = (f["parent"] as? JsonObject)?.texto("key"),
                estimateS = (f["timeoriginalestimate"] as? JsonPrimitive)?.longOrNull,
                spentS = (f["timespent"] as? JsonPrimitive)?.longOrNull,
            )
        }
    }
}

/** Texto do campo, ou null se ausente, nulo ou não textual. */
internal fun JsonObject.texto(nome: String): String? {
    val e: JsonElement = this[nome] ?: return null
    if (e is JsonNull) return null
    return (e as? JsonPrimitive)?.content
}
