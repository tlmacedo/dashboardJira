package br.sidia.painel.trabalho

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.TreeMap

/** P50 e P85 de uma amostra de [n] itens. */
data class Faixa(val p50: Double, val p85: Double, val n: Int)

/** O último movimento REAL de uma issue: o campo que mudou (`criacao` se nunca mudou nada) e quando. */
data class MovimentoReal(val campo: String, val em: Instant)

/**
 * Métricas transversais dos cards de trabalho e prazos: uma regra só para "concluída", "ciclo", "idade" e "semana"
 * em todas as telas. Funções puras (relógio e calendário entram por parâmetro). Status fora do mapa propaga
 * `UnmappedStatusException`, que a rota genérica transforma em aviso bloqueante.
 */
object Fluxo {
    /** Menos itens que isto numa faixa histórica gera o aviso informativo `AMOSTRA_PEQUENA`. */
    const val AMOSTRA_MINIMA = 5

    private val EM_CURSO = setOf(StatusCategory.ACTIVE, StatusCategory.WAIT, StatusCategory.BLOCKED)

    private fun ehDone(s: IssueSnapshot, map: StatusMap) = map.category(s.status) == StatusCategory.DONE

    /** Categoria DONE, com `resolution` e `resolved`, e a resolução não é de cancelamento. */
    fun concluidaPositiva(s: IssueSnapshot, map: StatusMap): Boolean =
        ehDone(s, map) && !s.resolution.isNullOrBlank() && s.resolved != null && !map.ehResolucaoNegativa(s.resolution)

    /** Categoria DONE e resolução de cancelamento (Won't Do, Duplicate...). */
    fun cancelada(s: IssueSnapshot, map: StatusMap): Boolean =
        ehDone(s, map) && !s.resolution.isNullOrBlank() && map.ehResolucaoNegativa(s.resolution)

    /** Status concluído sem `resolution`: não é entrega nem cancelamento; vai para o aviso `DONE_SEM_RESOLUTION`. */
    fun divergenteDoneResolution(s: IssueSnapshot, map: StatusMap): Boolean =
        ehDone(s, map) && s.resolution.isNullOrBlank()

    /** Última transição para um status de categoria DONE; base do período de "DONE sem resolution". */
    fun entrouEmDone(s: IssueSnapshot, map: StatusMap): Instant? =
        s.timeline.transitions.filter { map.category(it.to) == StatusCategory.DONE }.maxOfOrNull { it.at }

    /** Ativa, em espera ou bloqueada (ou com a flag de impedimento), e ainda não concluída. */
    fun emAndamento(s: IssueSnapshot, map: StatusMap): Boolean {
        val cat = map.category(s.status)
        return cat != StatusCategory.DONE && (cat in EM_CURSO || s.flagged)
    }

    /** Primeira entrada em status ativo, de espera ou bloqueado; criação se já nasceu assim; nulo se nunca andou. */
    fun inicioAtivo(s: IssueSnapshot, map: StatusMap): Instant? {
        if (map.category(s.timeline.initialStatus) in EM_CURSO) return s.created
        return s.timeline.transitions.filter { map.category(it.to) in EM_CURSO }.minOfOrNull { it.at }
    }

    private fun dias(de: Instant, ate: Instant, cal: BusinessCalendar, unit: TimeUnitMode): Double {
        val fim = if (ate.isBefore(de)) de else ate
        return if (unit == TimeUnitMode.UTIL) cal.businessDays(de, fim) else cal.calendarDays(de, fim)
    }

    /** Do início ativo à resolução; nulo sem uma das duas pontas (ou com a resolução antes do início). */
    fun cicloDias(s: IssueSnapshot, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode): Double? {
        val inicio = inicioAtivo(s, map) ?: return null
        val fim = s.resolved ?: return null
        if (fim.isBefore(inicio)) return null
        return dias(inicio, fim, cal, unit)
    }

    /** Da criação à resolução; nulo sem resolução. */
    fun leadDias(s: IssueSnapshot, cal: BusinessCalendar, unit: TimeUnitMode): Double? {
        val fim = s.resolved ?: return null
        if (fim.isBefore(s.created)) return null
        return dias(s.created, fim, cal, unit)
    }

    /** Do início ativo (ou da criação) até [agora]. */
    fun idadeDias(s: IssueSnapshot, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode, agora: Instant): Double =
        dias(inicioAtivo(s, map) ?: s.created, agora, cal, unit)

    /** Desde a última entrada no status atual (ou da criação, se nunca mudou de status) até [agora]. */
    fun diasNoStatusAtual(s: IssueSnapshot, cal: BusinessCalendar, unit: TimeUnitMode, agora: Instant): Double {
        val desde = s.timeline.transitions.filter { it.to == s.status }.maxOfOrNull { it.at } ?: s.created
        return dias(desde, agora, cal, unit)
    }

    /** Campos cuja mudança é movimento real da task (ao contrário de `updated`, que um comentário também move). */
    val CAMPOS_DE_MOVIMENTO = setOf("status", "assignee", "duedate", "labels", "resolution")

    /**
     * Último movimento real (ideia I-06): a mudança mais recente de status, responsável, vencimento, labels ou
     * resolução no changelog; sem nenhuma, a criação. NÃO é o `updated` do Jira.
     */
    fun ultimoMovimentoReal(s: IssueSnapshot): MovimentoReal {
        val e = s.events.filter { it.field.lowercase(java.util.Locale.ROOT) in CAMPOS_DE_MOVIMENTO }.maxByOrNull { it.at }
        return if (e == null) MovimentoReal("criacao", s.created) else MovimentoReal(e.field.lowercase(java.util.Locale.ROOT), e.at)
    }

    /** Quem era o responsável em [at]: a última troca até o instante, ou o responsável inicial (nulo = sem responsável). */
    fun responsavelEm(s: IssueSnapshot, at: Instant): String? {
        val ultima = s.timeline.assigneeChanges.sortedBy { it.at }.lastOrNull { !it.at.isAfter(at) }
        return if (ultima == null) s.timeline.initialAssignee else ultima.to
    }

    /** Segunda-feira da semana de [at] no fuso do painel (semana = segunda a domingo). */
    fun semanaDe(at: Instant, zone: ZoneId): LocalDate =
        at.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun serie(instantes: List<Instant>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>> {
        require(semanas > 0) { "semanas deve ser positivo" }
        val porSemana = instantes.groupingBy { semanaDe(it, zone) }.eachCount()
        return (semanas - 1 downTo 0).map { k ->
            val semana = ultimaSemana.minusWeeks(k.toLong())
            semana to (porSemana[semana] ?: 0)
        }
    }

    /** Concluídas por semana (pela `resolved`), da mais antiga à [ultimaSemana] inclusive; semanas vazias com 0. */
    fun throughputSemanal(concluidas: List<IssueSnapshot>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>> =
        serie(concluidas.mapNotNull { it.resolved }, ultimaSemana, semanas, zone)

    /** Chegadas (pela `created`) por semana, no mesmo formato de [throughputSemanal]. */
    fun chegadasSemanais(issues: List<IssueSnapshot>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>> =
        serie(issues.map { it.created }, ultimaSemana, semanas, zone)

    /** P50 e P85 por tipo, em ordem de tipo; tipo sem valores não aparece. */
    fun faixasPorTipo(valores: Map<String, List<Double>>): Map<String, Faixa> {
        val saida = TreeMap<String, Faixa>()
        for ((tipo, v) in valores) {
            val p50 = Percentiles.of(v, 0.5) ?: continue
            val p85 = Percentiles.of(v, 0.85) ?: continue
            saida[tipo] = Faixa(p50, p85, v.size)
        }
        return saida
    }

    /** Um `AMOSTRA_PEQUENA` por tipo com menos de [minimo] itens, em ordem de tipo. Informativo: não esconde o número. */
    fun avisosDeAmostra(faixas: Map<String, Faixa>, minimo: Int = AMOSTRA_MINIMA): List<AvisoDto> =
        faixas.toSortedMap().filterValues { it.n < minimo }.map { (tipo, f) ->
            AvisoDto(
                AvisoDto.AMOSTRA_PEQUENA, AvisoDto.INFORMATIVO, "amostra pequena para $tipo",
                mapOf("tipo" to tipo, "n" to f.n.toString(), "minimo" to minimo.toString()),
            )
        }
}
