package br.sidia.painel.cards.simulador

import br.sidia.painel.cards.paraDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.Ausencia
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** O DTO e as tasks que passam a atrasar (o que a gaveta abre). */
class SimuladorAnalise(val dto: SimuladorDto, val atrasos: List<IssueSnapshot>)

/**
 * Simulador de ausência: o que atrasaria se [pessoa] se ausentasse de [de] a [ate]. Compara a agenda pela capacidade (e a
 * ocupação das semanas) com a ausência real e com a real MAIS a hipotética (`Ausencias.mais`, que devolve uma cópia): o que é
 * real nunca muda e nada é gravado. Domínio puro.
 */
class SimuladorCalculator(private val motor: MotorDePessoas) {
    private val map = motor.statusMap

    fun simular(pessoa: String, abertas: List<IssueSnapshot>, hoje: LocalDate, de: LocalDate, ate: LocalDate, fracao: Double): SimuladorAnalise {
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val hipotetico = MotorDePessoas(
            motor.equipes, motor.ausencias.mais(Ausencia(pessoa, de, ate, fracao)), motor.calendario, motor.regras, motor.statusMap,
        )

        val atuais = motor.agenda.agendar(pessoa, naoConcluidas, hoje)
        val comAusencia = hipotetico.agenda.agendar(pessoa, naoConcluidas, hoje).associateBy { it.key }
        // Atraso CAUSADO: a task não atrasava e passa a atrasar; a que já atrasava por outro motivo não conta.
        val causadas = atuais.filter { !it.atrasa && comAusencia.getValue(it.key).atrasa }.map { it.key }.toSet()
        val tasks = naoConcluidas.filter { it.key in causadas }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

        // Horas que o dia já tinha agendado e que não cabem na capacidade com a ausência hipotética (ausência total: todas).
        val horas = generateSequence(maxOf(de, hoje)) { it.plusDays(1) }.takeWhile { !it.isAfter(ate) }.sumOf { d ->
            maxOf(0.0, atuais.sumOf { it.horasPorDia[d] ?: 0.0 } - hipotetico.capacidade.dia(pessoa, d))
        }

        val semanas = (ChronoUnit.WEEKS.between(segundaDe(hoje), segundaDe(ate)).toInt() + 1).coerceAtLeast(1)
        val antes = motor.demanda.carga(pessoa, naoConcluidas, hoje, semanas).semanas
        val depois = hipotetico.demanda.carga(pessoa, naoConcluidas, hoje, semanas).semanas
        val comparacao = antes.zip(depois).map { (a, d) ->
            ComparacaoSemanaDto(a.semana.toString(), a.ocupacao.paraDto(motor.regras, a.demandaH), d.ocupacao.paraDto(motor.regras, d.demandaH))
        }
        val dto = SimuladorDto(
            pessoa = pessoa, de = de.toString(), ate = ate.toString(), fracao = fracao,
            atrasosCausados = tasks.map { it.key }, horasAReplanejar = horas, semanas = comparacao, avisos = emptyList(),
        )
        return SimuladorAnalise(dto, tasks)
    }

    private fun segundaDe(dia: LocalDate): LocalDate = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
