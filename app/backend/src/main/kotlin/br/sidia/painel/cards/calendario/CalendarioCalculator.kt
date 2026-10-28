package br.sidia.painel.cards.calendario

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate

/** O DTO e as tasks por trás do vencimento de cada dia (`dia:<aaaa-mm-dd>`). */
class CalendarioAnalise(val dto: CalendarioDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Calendário único do time: feriados, ausências e vencimentos por dia, com a capacidade do time (das pessoas visíveis) e o
 * dia crítico. Domínio puro. Pessoas em ordem alfabética (a ordem de [pessoas]); nenhuma ordenação por carga.
 */
class CalendarioCalculator(private val motor: MotorDePessoas) {
    companion object {
        const val DIAS_DO_MES = 28
        const val DIAS_DA_SEMANA = 7
    }

    private val cal = motor.calendario

    /** [pessoas]: as da visão que constam na equipe. [abertas]: as abertas da visão (todas as pessoas, com e sem cadastro). */
    fun calcular(pessoas: List<String>, abertas: List<IssueSnapshot>, inicio: LocalDate, modo: ModoCalendario): CalendarioAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
        val quantos = if (modo == ModoCalendario.SEMANA) DIAS_DA_SEMANA else DIAS_DO_MES
        val datas = (0 until quantos).map { inicio.plusDays(it.toLong()) }
        val vencemNoDia = naoConcluidas.filter { it.duedate != null }.groupBy { requireNotNull(it.duedate) }

        val dias = datas.map { d ->
            val util = cal.isBusinessDay(d)
            val bruta = pessoas.sumOf { motor.capacidade.brutaDia(it, d) }
            val liquida = pessoas.sumOf { motor.capacidade.dia(it, d) }
            val capacidade = if (util && bruta > 0.0) liquida / bruta else null
            val vencimentos = vencemNoDia[d].orEmpty().size
            DiaDto(
                dia = d.toString(), feriado = if (cal.isHoliday(d)) cal.holidayName(d).orEmpty() else null, util = util,
                capacidadeSquad = capacidade, vencimentos = vencimentos,
                critico = util && vencimentos > 0 && capacidade != null && capacidade < motor.regras.diaCriticoCapacidade,
            )
        }
        val linhas = pessoas.map { p ->
            val dela = naoConcluidas.filter { it.assignee == p && it.duedate != null }.groupBy { requireNotNull(it.duedate) }
            LinhaCalendarioDto(
                p,
                datas.map { d -> CelulaCalendarioDto(d.toString(), motor.ausencias.fracaoEm(p, d), dela[d].orEmpty().size) },
            )
        }
        val segmentos = datas.associate { d ->
            "dia:$d" to vencemNoDia[d].orEmpty().sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        }
        return CalendarioAnalise(CalendarioDto(inicio.toString(), dias, linhas, avisosDePessoas(motor, naoConcluidas, false)), segmentos)
    }
}
