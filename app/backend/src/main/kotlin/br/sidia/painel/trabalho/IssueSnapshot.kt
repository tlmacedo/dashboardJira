package br.sidia.painel.trabalho

import br.sidia.painel.auditoria.AuditIssue
import br.sidia.painel.auditoria.Vencimentos
import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.FiltroDeFicha
import java.time.Instant
import java.time.LocalDate

/**
 * Modelo de leitura único das issues para os cards de trabalho e prazos (e, depois, pessoas e integridade): o estado
 * atual da issue, a linha do tempo de status/responsável e todos os eventos do changelog. [parentKey] serve só à
 * hierarquia/Epic; o par Task × Review vem do vínculo "Blocks" (`issue_link`), nunca daqui.
 */
data class IssueSnapshot(
    val instance: String,
    val key: String,
    val summary: String,
    val issueType: String,
    val priority: String?,
    val status: String,
    val resolution: String?,
    val created: Instant,
    val updated: Instant?,
    val resolved: Instant?,
    val duedate: LocalDate?,
    val assignee: String?,
    val reporter: String?,
    val labels: List<String>,
    val parentKey: String?,
    val epicKey: String?,
    val storyPoints: Double?,
    val estimateS: Long?,
    val remainingS: Long?,
    val spentS: Long?,
    val osVersion: String?,
    val flagged: Boolean,
    val components: List<String>,
    val timeline: IssueTimeline,
    val events: List<ChangeEventRecord>,
    /** Campos do dicionário com `filtro: true` (nome -> valor; Plano 10); vazio quando a issue não tem nenhum. */
    val camposDicionario: Map<String, String> = emptyMap(),
)

/** Adaptador para `NoPrazo` e `TaskReview` (Plano 4): mesmos campos, mesmos eventos. */
fun IssueSnapshot.toAuditIssue(): AuditIssue = AuditIssue(
    instance = instance, key = key, summary = summary, issueType = issueType, parentKey = parentKey, reporter = reporter,
    assignee = assignee, duedate = duedate, created = created, resolved = resolved, resolution = resolution,
    status = status, labels = labels, events = events,
)

/**
 * O vencimento ORIGINAL: o `from` do primeiro evento de `duedate` com valor legível; se o primeiro evento é a
 * primeira definição (de nada para uma data), vale a data definida. Sem eventos legíveis, o vencimento atual.
 */
fun IssueSnapshot.vencimentoOriginal(): LocalDate? {
    for (e in events) {
        if (!e.field.equals("duedate", ignoreCase = true)) continue
        Vencimentos.ler(e.from)?.let { return it }
        if (e.from.isNullOrBlank()) Vencimentos.ler(e.to)?.let { return it }
    }
    return duedate
}

/** Quais issues entram numa leitura: abertas (`resolved` nulo), resolvidas (em `[de, ate)`) ou todas. */
enum class Situacao { ABERTAS, RESOLVIDAS, TODAS }

/**
 * Consulta de leitura. [filtro] é o que a ficha do card manda (tipos e status excluídos). [resolvidasDe] e
 * [resolvidasAte] valem com [Situacao.RESOLVIDAS] (`resolved >= de` e `resolved < ate`). [vencimentoDe] e
 * [vencimentoAte] limitam o `duedate` (inclusivos nas duas pontas) e combinam com qualquer situação.
 */
data class ConsultaDeTrabalho(
    val instance: String,
    val filtro: FiltroDeFicha?,
    val situacao: Situacao,
    val resolvidasDe: Instant? = null,
    val resolvidasAte: Instant? = null,
    val osVersion: String? = null,
    val vencimentoDe: LocalDate? = null,
    val vencimentoAte: LocalDate? = null,
)
