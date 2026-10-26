package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Issue como a auditoria a enxerga: estado atual mais todos os eventos do changelog.
 * [parentKey] serve só à hierarquia/Epic; o par Task × Review vem de `issue_link` (vínculo "Blocks").
 */
data class AuditIssue(
    val instance: String,
    val key: String,
    val summary: String,
    val issueType: String,
    val parentKey: String?,
    val reporter: String?,
    val assignee: String?,
    val duedate: LocalDate?,
    val created: Instant,
    val resolved: Instant?,
    val resolution: String?,
    val status: String,
    val labels: List<String>,
    val events: List<ChangeEventRecord>,
)

/** Leitor do vencimento nos dois formatos que o Jira DC entrega. */
object Vencimentos {
    private val FORMATO = Regex("""^(\d{4}-\d{2}-\d{2})(?: 00:00:00(?:\.0+)?)?$""")

    /** `2026-10-15` e `2026-10-15 00:00:00.0` viram a data; qualquer outro texto (ou vazio) vira nulo. */
    fun ler(valor: String?): LocalDate? {
        val texto = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val data = FORMATO.matchEntire(texto)?.groupValues?.get(1) ?: return null
        return try {
            LocalDate.parse(data)
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }
}

/** Diferença entre dois conjuntos de labels. */
data class MudancaDeLabels(val adicionadas: Set<String>, val removidas: Set<String>)
