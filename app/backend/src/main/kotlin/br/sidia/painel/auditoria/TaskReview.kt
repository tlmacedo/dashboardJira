package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.ParDeChaves
import java.time.Instant
import java.time.LocalDate

/** Um par Task × Review. [divergente]: vencimentos diferentes; um nulo e o outro não também diverge; ambos nulos, não. */
data class ParTaskReview(val task: AuditIssue, val review: AuditIssue) {
    val divergente: Boolean get() = task.duedate != review.duedate
}

/** [percentual] em pontos percentuais (25.0 = 25%); nulo quando não há pares. */
data class ResumoTaskReview(val pares: Int, val divergentes: Int, val percentual: Double?)

/**
 * O que a leitura do banco entrega para Task × Review: as issues das duas pontas de cada par (com todos os eventos),
 * os pares já resolvidos pelo 4A, quantos candidatos foram descartados no desempate ([ambiguas]) e se a base não tem
 * nenhum vínculo do tipo configurado ([semVinculos]).
 */
data class ParesLidos(val issues: List<AuditIssue>, val vinculos: List<ParDeChaves>, val ambiguas: Int, val semVinculos: Boolean)

/** Task × Review pelo link "Blocks" (nunca pelo `parent`): pares, divergência de vencimento e o sinal no evento que diverge. */
object TaskReview {

    /**
     * Pares a partir dos vínculos JÁ resolvidos pelo 4A (`ParesTaskReview.resolver`). Vínculo cuja task ou Review não
     * está em [issues] (ausente ou removida) é ignorado; o `parentKey` nunca é lido.
     */
    fun pares(issues: List<AuditIssue>, vinculos: List<ParDeChaves>): List<ParTaskReview> {
        val porChave = issues.associateBy { it.key }
        return vinculos.mapNotNull { v ->
            val task = porChave[v.task] ?: return@mapNotNull null
            val review = porChave[v.review] ?: return@mapNotNull null
            ParTaskReview(task, review)
        }
    }

    fun resumo(pares: List<ParTaskReview>): ResumoTaskReview {
        val divergentes = pares.count { it.divergente }
        return ResumoTaskReview(pares.size, divergentes, if (pares.isEmpty()) null else divergentes * 100.0 / pares.size)
    }

    /**
     * `VENCIMENTO_TASK_REVIEW_DIVERGENTE` (alerta) no evento de `duedate` que deixa as duas datas diferentes: percorre
     * os eventos das duas issues em ordem de tempo e emite quando as datas eram iguais e deixam de ser. Evento que
     * mantém uma divergência que já existia não gera sinal novo; se as datas voltam a ser iguais, o próximo desvio gera.
     */
    fun sinais(par: ParTaskReview, ctx: ContextoDeteccao): List<Sinal> {
        val timelineTask = AuditTimeline(par.task, ctx.statusMap)
        val timelineReview = AuditTimeline(par.review, ctx.statusMap)
        // Estado antes do primeiro evento de cada uma (o `from` do primeiro evento, ou o valor atual).
        var task: LocalDate? = timelineTask.vencimentoEm(Instant.MIN)
        var review: LocalDate? = timelineReview.vencimentoEm(Instant.MIN)
        val eventos: List<Pair<AuditTimeline, ChangeEventRecord>> =
            (timelineTask.trocasDeVencimento.map { timelineTask to it } + timelineReview.trocasDeVencimento.map { timelineReview to it })
                .sortedBy { it.second.at }
        val sinais = mutableListOf<Sinal>()
        for ((t, e) in eventos) {
            val eraIgual = task == review
            val novo = Vencimentos.ler(e.to)
            if (t === timelineTask) task = novo else review = novo
            if (eraIgual && task != review) {
                ctx.sinal(CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE, t, e, t.responsavelEm(e.at), Resultado.ALERTA)?.let(sinais::add)
            }
        }
        return sinais
    }
}
