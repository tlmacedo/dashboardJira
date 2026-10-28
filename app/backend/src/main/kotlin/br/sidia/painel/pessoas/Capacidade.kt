package br.sidia.painel.pessoas

import br.sidia.painel.time.BusinessCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Capacidade líquida de cada pessoa: horas semanais ÷ 5 × foco × alocação × (1 − fração de ausência) em dia útil;
 * fim de semana e feriado valem zero; quem não está na equipe tem capacidade zero (nunca um padrão inventado).
 */
class Capacidade(
    private val equipes: Equipes,
    private val ausencias: Ausencias,
    private val cal: BusinessCalendar,
    private val regras: RegrasDePlanejamento,
) {
    /** Capacidade do dia, já descontada a ausência. */
    fun dia(pessoa: String, dia: LocalDate): Double = brutaDia(pessoa, dia) * (1.0 - ausencias.fracaoEm(pessoa, dia))

    /** Capacidade do dia SEM descontar a ausência (base de "% da capacidade do time"); feriado e fim de semana continuam zero. */
    fun brutaDia(pessoa: String, dia: LocalDate): Double {
        val m = equipes.de(pessoa) ?: return 0.0
        if (!cal.isBusinessDay(dia)) return 0.0
        return m.horasSemanais / DIAS_UTEIS_POR_SEMANA * (m.foco ?: regras.focoPadrao) * m.alocacao
    }

    /** Soma da capacidade de [de] a [ate], ambos inclusivos. */
    fun periodo(pessoa: String, de: LocalDate, ate: LocalDate): Double {
        var total = 0.0
        var d = de
        while (!d.isAfter(ate)) {
            total += dia(pessoa, d)
            d = d.plusDays(1)
        }
        return total
    }

    /**
     * As [n] semanas a partir da de [hoje] (chave = segunda-feira). A semana ATUAL conta de hoje até domingo: o que já
     * passou não é tempo livre. As demais são inteiras (segunda a domingo).
     */
    fun semanas(pessoa: String, hoje: LocalDate, n: Int): List<Pair<LocalDate, Double>> {
        val segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until n).map { i ->
            val inicio = segunda.plusWeeks(i.toLong())
            val de = if (i == 0) hoje else inicio
            inicio to periodo(pessoa, de, inicio.plusDays(6))
        }
    }

    private companion object {
        const val DIAS_UTEIS_POR_SEMANA = 5.0
    }
}
