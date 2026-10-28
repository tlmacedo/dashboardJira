package br.sidia.painel.cards.vencimentos

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.Agendamento
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate

/** O DTO e as tasks por segmento (`vencem` e `riscoDeAtrasar`), com a linha de cada uma. */
class VencimentosAnalise(val dto: VencimentosDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Vencimentos dos próximos [dias] dias (de hoje, inclusive, até hoje + dias) com folga real. A previsão de término vem da
 * `Agenda` da pessoa (fila inteira, com ausências e feriados); a folga conta os dias úteis entre o término e o vencimento.
 * A task já vencida não entra (está em "vencidas" da Carga). Ordem: vencimento e chave; nunca por pessoa nem por folga.
 */
class VencimentosCalculator(private val motor: MotorDePessoas) {
    fun calcular(abertas: List<IssueSnapshot>, hoje: LocalDate, dias: Int): VencimentosAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
        val agendamentos: Map<String, Agendamento> = motor.agenda.agendarTodas(naoConcluidas, hoje)
        val ate = hoje.plusDays(dias.toLong())
        val doPeriodo = naoConcluidas.filter { s -> s.duedate?.let { !it.isBefore(hoje) && !it.isAfter(ate) } == true }
            .sortedWith(compareBy<IssueSnapshot> { it.duedate }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

        val linhas = doPeriodo.map { s ->
            val vence = requireNotNull(s.duedate)
            val termino = agendamentos[s.key]?.termino
            val folga = termino?.let { folgaDiasUteis(it, vence) }
            val estado = when {
                folga == null || folga < 0.0 -> "problema"
                folga == 0.0 -> "atencao"
                else -> "ok"
            }
            VencimentoDto(s.key, s.summary, s.assignee, vence.toString(), termino?.toString(), folga, estado)
        }
        val chaveDeRisco = linhas.filter { it.estado == "problema" }.map { it.key }.toSet()
        val segmentos = mapOf(
            "vencem" to doPeriodo,
            "riscoDeAtrasar" to doPeriodo.filter { it.key in chaveDeRisco },
        )
        val dto = VencimentosDto(dias, linhas.size, linhas, chaveDeRisco.size, avisosDePessoas(motor, naoConcluidas, false))
        return VencimentosAnalise(dto, segmentos)
    }

    /**
     * Dias úteis entre o término e o vencimento: com o término antes do vencimento, os dias úteis depois do término até o
     * vencimento (inclusive); com o término depois, o negativo dos dias úteis depois do vencimento até o término.
     */
    fun folgaDiasUteis(termino: LocalDate, vence: LocalDate): Double {
        fun uteisEntre(de: LocalDate, ate: LocalDate): Int {
            var n = 0
            var d = de.plusDays(1)
            while (!d.isAfter(ate)) {
                if (motor.calendario.isBusinessDay(d)) n++
                d = d.plusDays(1)
            }
            return n
        }
        return if (termino.isAfter(vence)) -uteisEntre(vence, termino).toDouble() else uteisEntre(termino, vence).toDouble()
    }
}
