package br.sidia.painel.checklist

import br.sidia.painel.auditoria.TaskReview
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.sync.ParDeChaves
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.toAuditIssue
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Alertas de um par Checklist × Review (relatório 4.12): cada um é um indício de revisão fraca, não uma prova. */
enum class AlertaDoPar {
    /** A review vence depois do checklist pai (os dois com vencimento). */
    REVIEW_VENCE_DEPOIS,

    /** O pai foi resolvido antes de a review terminar (ou a review ainda está aberta). */
    PAI_FECHADO_ANTES,

    /** A mesma pessoa é responsável pelo pai e pela review: não há revisão independente. */
    MESMA_PESSOA,

    /** Review concluída com poucas horas ativas e o pai chegou a ser reaberto. */
    REVIEW_CURTA_COM_PAI_REABERTO,

    /** Review aberta com o vencimento já passado. */
    REVIEW_VENCIDA,

    /** Review resolvida depois do próprio vencimento. */
    REVIEW_ATRASADA,
}

/**
 * Um par: o [pai] (o lado Checklist) e a [review] ligada a ele pelo link "Blocks" (nunca pelo `parent`).
 * [horasAtivasReview] soma as horas corridas da review em status de categoria ativa; [paiReaberto] diz se o pai já saiu
 * de um status concluído para outro.
 */
data class ParCR(
    val pai: IssueSnapshot,
    val review: IssueSnapshot,
    val alertas: Set<AlertaDoPar>,
    val horasAtivasReview: Double?,
    val paiReaberto: Boolean,
)

/**
 * Pares Checklist × Review. Usa o MESMO pareamento da Divergência Task × Review (`TaskReview.pares` sobre os vínculos já
 * resolvidos pelo 4A, nas duas direções) e fica só com os pares cujo pai tem tipo em [RegrasChecklistReview.tiposPai] e cuja
 * review tem tipo em [tiposReview] (a lista da auditoria, fonte única). Status fora do mapa propaga `UnmappedStatusException`.
 */
class ParesChecklistReview(
    private val regras: RegrasChecklistReview,
    tiposReview: Set<String>,
    private val map: StatusMap,
    private val cal: BusinessCalendar,
) {
    private val reviews: Set<String> = tiposReview.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    private fun ehReview(tipo: String) = tipo.trim().lowercase(Locale.ROOT) in reviews

    fun pares(issues: List<IssueSnapshot>, vinculos: List<ParDeChaves>, agora: Instant): List<ParCR> {
        val porChave = issues.associateBy { it.key }
        return TaskReview.pares(issues.map { it.toAuditIssue() }, vinculos).mapNotNull { p ->
            val pai = porChave[p.task.key] ?: return@mapNotNull null
            val review = porChave[p.review.key] ?: return@mapNotNull null
            if (!regras.ehPai(pai.issueType) || !ehReview(review.issueType)) return@mapNotNull null
            montar(pai, review, agora)
        }
    }

    /** Checklists (tipos de pai) sem nenhum vínculo resolvido com uma Review: o "Checklists sem review". */
    fun semReview(issues: List<IssueSnapshot>, vinculos: List<ParDeChaves>): List<IssueSnapshot> {
        val comVinculo = vinculos.map { it.task }.toSet()
        return issues.filter { regras.ehPai(it.issueType) && it.key !in comVinculo }
    }

    private fun montar(pai: IssueSnapshot, review: IssueSnapshot, agora: Instant): ParCR {
        val horas = horasAtivas(review, agora)
        val reaberto = reaberto(pai)
        val alertas = buildSet {
            val vencePai = pai.duedate
            val venceReview = review.duedate
            if (vencePai != null && venceReview != null && venceReview.isAfter(vencePai)) add(AlertaDoPar.REVIEW_VENCE_DEPOIS)
            val paiResolvido = pai.resolved
            if (paiResolvido != null && (review.resolved == null || review.resolved.isAfter(paiResolvido))) add(AlertaDoPar.PAI_FECHADO_ANTES)
            if (pai.assignee != null && pai.assignee == review.assignee) add(AlertaDoPar.MESMA_PESSOA)
            if (review.resolved != null && horas < regras.reviewCurtaHoras && reaberto) add(AlertaDoPar.REVIEW_CURTA_COM_PAI_REABERTO)
            if (review.resolved == null && venceReview != null && venceReview.isBefore(cal.localDate(agora))) add(AlertaDoPar.REVIEW_VENCIDA)
            if (review.resolved != null && venceReview != null && cal.localDate(review.resolved).isAfter(venceReview)) add(AlertaDoPar.REVIEW_ATRASADA)
        }
        return ParCR(pai, review, alertas, horas, reaberto)
    }

    /** Horas corridas da review em status de categoria ativa (as de espera e bloqueio não contam). */
    private fun horasAtivas(review: IssueSnapshot, agora: Instant): Double =
        TimelineBuilder.intervals(review.timeline, agora, map)
            .filter { it.category == StatusCategory.ACTIVE }
            .sumOf { Duration.between(it.start, it.end).toMillis() } / MILIS_POR_HORA

    /** O pai saiu de um status concluído para outro em algum momento. */
    private fun reaberto(pai: IssueSnapshot): Boolean =
        pai.timeline.transitions.any { t -> t.from != null && map.category(t.from) == StatusCategory.DONE && map.category(t.to) != StatusCategory.DONE }

    private companion object {
        const val MILIS_POR_HORA = 3_600_000.0
    }
}
