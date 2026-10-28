package br.sidia.painel.trabalho

import br.sidia.painel.domain.StatusMap
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Uma semana (segunda a domingo de Manaus) do fluxo: o que ENTROU (por `created`), o que SAIU como entrega (conclusão
 * positiva, por `resolved`) e as CANCELADAS à parte (resolução negativa, por `resolved`). As listas são as tasks por
 * trás de cada número (a gaveta abre exatamente estas).
 */
class SemanaDeFluxo(
    val semana: LocalDate,
    val entradas: List<IssueSnapshot>,
    val saidas: List<IssueSnapshot>,
    val canceladas: List<IssueSnapshot>,
)

/**
 * A função ÚNICA das séries semanais do painel: a Previsão (vazão e chegada do Monte Carlo) e o futuro cartão "Fluxo da
 * semana" (Plano 17b) leem as mesmas semanas, então as telas nunca divergem. Mais antiga primeiro, semanas vazias com
 * listas vazias, [calcular] inclui a `ultimaSemana`. Status fora do mapa propaga `UnmappedStatusException`.
 */
object SerieSemanal {
    /** Segunda-feira da última semana FECHADA antes da semana de [hoje] (a semana em curso não entra nas séries). */
    fun ultimaSemanaFechada(hoje: LocalDate): LocalDate =
        hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    fun calcular(
        issues: List<IssueSnapshot>, map: StatusMap, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId,
    ): List<SemanaDeFluxo> {
        require(semanas > 0) { "semanas deve ser positivo" }
        val unicas = issues.distinctBy { it.key }
        val entradas = unicas.groupBy { Fluxo.semanaDe(it.created, zone) }
        val saidas = unicas.filter { Fluxo.concluidaPositiva(it, map) }.groupBy { Fluxo.semanaDe(requireNotNull(it.resolved), zone) }
        val canceladas = unicas.filter { Fluxo.cancelada(it, map) && it.resolved != null }
            .groupBy { Fluxo.semanaDe(requireNotNull(it.resolved), zone) }
        return (semanas - 1 downTo 0).map { k ->
            val semana = ultimaSemana.minusWeeks(k.toLong())
            SemanaDeFluxo(semana, entradas[semana].orEmpty(), saidas[semana].orEmpty(), canceladas[semana].orEmpty())
        }
    }

    /** Conta [instantes] por semana (a mesma janela de [calcular]); base de `Fluxo.throughputSemanal` e `chegadasSemanais`. */
    internal fun contar(instantes: List<Instant>, ultimaSemana: LocalDate, semanas: Int, zone: ZoneId): List<Pair<LocalDate, Int>> {
        require(semanas > 0) { "semanas deve ser positivo" }
        val porSemana = instantes.groupingBy { Fluxo.semanaDe(it, zone) }.eachCount()
        return (semanas - 1 downTo 0).map { k ->
            val semana = ultimaSemana.minusWeeks(k.toLong())
            semana to (porSemana[semana] ?: 0)
        }
    }
}
