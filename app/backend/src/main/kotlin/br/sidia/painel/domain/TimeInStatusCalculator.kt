package br.sidia.painel.domain

import br.sidia.painel.time.BusinessCalendar
import kotlinx.serialization.Serializable
import java.time.Instant

/** Unidade do tempo: dias úteis (padrão) ou dias corridos. */
enum class TimeUnitMode { UTIL, CORRIDO }

@Serializable
data class StatusStat(val status: String, val category: StatusCategory, val median: Double, val p85: Double, val n: Int)

@Serializable
data class PersonStatus(val person: String, val status: String, val median: Double, val n: Int)

@Serializable
data class StuckIssue(
    val key: String, val summary: String, val assignee: String?, val status: String,
    val daysInStatus: Double, val median: Double,
)

data class TimeInStatusResult(
    val analyzed: List<String>,
    val flowEfficiency: Double?,
    val perStatus: List<StatusStat>,
    val perPersonStatus: List<PersonStatus>,
    val stuck: List<StuckIssue>,
    val unit: TimeUnitMode,
    /** Issues descartadas por datas inconsistentes (ver [TimeInStatusCalculator.datasInconsistentes]). */
    val ignoredInconsistent: Int = 0,
)

/**
 * Tempo em status das tasks concluídas (mediana e p85 por status e por pessoa), eficiência de fluxo
 * e tasks abertas paradas além do normal. A categoria DONE é ignorada em todas as somas.
 * Status sem mapeamento propaga [UnmappedStatusException]. Domínio puro, sem rede.
 */
class TimeInStatusCalculator(private val calendar: BusinessCalendar, private val map: StatusMap) {

    fun compute(
        closed: List<IssueTimeline>,
        open: List<IssueTimeline>,
        now: Instant,
        unit: TimeUnitMode = TimeUnitMode.UTIL,
        excludeOutlierDays: Int? = null,
        stuckFactor: Double = 1.5,
        stuckMinDays: Double = 2.0,
    ): TimeInStatusResult {
        // Dado inconsistente não derruba o cálculo: a issue é ignorada e contada no resultado.
        val ruins = (closed + open).count { datasInconsistentes(it, now) }
        val fechadasOk = closed.filterNot { datasInconsistentes(it, now) }
        val abertasOk = open.filterNot { datasInconsistentes(it, now) }
        val analisadas = fechadasOk.filter { concluida(it) && !outlier(it, excludeOutlierDays) }

        // Por task: tempo total por status e por (pessoa, status), sem a categoria DONE.
        val porStatus = HashMap<String, MutableList<Double>>()
        val porPessoa = HashMap<Pair<String, String>, MutableList<Double>>()
        var ativo = 0.0
        var total = 0.0
        for (t in analisadas) {
            val somaStatus = HashMap<String, Double>()
            val somaPessoa = HashMap<Pair<String, String>, Double>()
            for (iv in intervalos(t, now)) {
                if (iv.category == StatusCategory.DONE) continue
                val d = duracao(iv, unit)
                somaStatus.merge(iv.status, d, Double::plus)
                iv.assignee?.let { somaPessoa.merge(it to iv.status, d, Double::plus) }
                total += d
                if (iv.category == StatusCategory.ACTIVE) ativo += d
            }
            somaStatus.forEach { (s, v) -> porStatus.getOrPut(s) { mutableListOf() }.add(v) }
            somaPessoa.forEach { (k, v) -> porPessoa.getOrPut(k) { mutableListOf() }.add(v) }
        }

        // Ordem estável de perStatus: categoria (ordem do enum: TODO, ACTIVE, WAIT, BLOCKED), depois nome.
        val perStatus = porStatus.map { (status, valores) ->
            StatusStat(
                status, map.category(status),
                Percentiles.median(valores)!!, Percentiles.of(valores, 0.85)!!, valores.size,
            )
        }.sortedWith(compareBy({ it.category.ordinal }, { it.status }))

        // perPersonStatus em ordem alfabética de pessoa e depois de status.
        val perPersonStatus = porPessoa.map { (k, valores) ->
            PersonStatus(k.first, k.second, Percentiles.median(valores)!!, valores.size)
        }.sortedWith(compareBy({ it.person }, { it.status }))

        val medianaPorStatus = perStatus.associate { it.status to it.median }
        val paradas = abertasOk.mapNotNull { parada(it, now, unit, medianaPorStatus, stuckFactor, stuckMinDays) }
            .sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

        return TimeInStatusResult(
            analyzed = analisadas.map { it.key },
            flowEfficiency = if (total > 0.0) ativo / total else null,
            perStatus = perStatus,
            perPersonStatus = perPersonStatus,
            stuck = paradas,
            unit = unit,
            ignoredInconsistent = ruins,
        )
    }

    /** Resolução antes da criação, ou criação no futuro em relação a [now]: o tempo não tem como ser medido. */
    fun datasInconsistentes(t: IssueTimeline, now: Instant): Boolean =
        (t.resolved?.let { it < t.created } ?: false) || t.created > now

    /** Concluída = categoria DONE no status atual e resolution preenchida (e data de resolução). */
    private fun concluida(t: IssueTimeline): Boolean =
        map.category(t.currentStatus) == StatusCategory.DONE &&
            !t.resolution.isNullOrBlank() && t.resolved != null

    /** Ciclo (criação até resolução) em dias corridos maior que N. */
    private fun outlier(t: IssueTimeline, limite: Int?): Boolean =
        limite != null && calendar.calendarDays(t.created, t.resolved!!) > limite

    /** Não assume transições ordenadas: ordena antes de chamar o builder. */
    internal fun intervalos(t: IssueTimeline, now: Instant): List<StatusInterval> =
        TimelineBuilder.intervals(
            t.copy(
                transitions = t.transitions.sortedBy { it.at },
                assigneeChanges = t.assigneeChanges.sortedBy { it.at },
            ),
            now, map,
        )

    internal fun duracao(iv: StatusInterval, unit: TimeUnitMode): Double = when (unit) {
        TimeUnitMode.UTIL -> calendar.businessDays(iv.start, iv.end)
        TimeUnitMode.CORRIDO -> calendar.calendarDays(iv.start, iv.end)
    }

    /** Aberta parada: tempo no status atual (reentrada não soma, conta a permanência corrente). */
    private fun parada(
        t: IssueTimeline, now: Instant, unit: TimeUnitMode,
        medianas: Map<String, Double>, fator: Double, minimo: Double,
    ): StuckIssue? {
        val ivs = intervalos(t, now)
        val ultimo = ivs.lastOrNull() ?: return null
        if (ultimo.category == StatusCategory.DONE) return null
        // Junta os cortes por responsável da permanência corrente (intervalos contíguos no mesmo status).
        var inicio = ultimo.start
        for (iv in ivs.dropLast(1).asReversed()) {
            if (iv.status == ultimo.status && iv.end == inicio) inicio = iv.start else break
        }
        val dias = duracao(StatusInterval(ultimo.status, ultimo.category, inicio, ultimo.end, ultimo.assignee), unit)
        val mediana = medianas[ultimo.status]
        val limite = if (mediana == null) minimo else maxOf(minimo, fator * mediana)
        if (dias <= limite) return null
        return StuckIssue(t.key, t.summary, ultimo.assignee, ultimo.status, dias, mediana ?: 0.0)
    }
}
