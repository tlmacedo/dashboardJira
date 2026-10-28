package br.sidia.painel.cards.cobertura

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.Ausencia
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** O DTO e, por segmento (`ausencia:<pessoa>:<de>`), as tasks afetadas por trás de cada número. */
class CoberturaAnalise(val dto: CoberturaDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Cobertura por ausência: para cada ausência das pessoas da visão que toca `[hoje, hoje + dias]`, o trabalho que fica sem dono
 * e quem do mesmo squad tem folga para cobrir. Domínio puro sobre o motor de capacidade; nada é gravado.
 *
 * Task afetada = aberta da pessoa que a [br.sidia.painel.pessoas.Agenda] SEM a ausência colocaria em algum dia dela, mais as que
 * vencem durante a ausência e não terminariam antes dela. Horas a replanejar = horas agendadas nos dias da ausência (cenário sem
 * ausência) que NÃO cabem na capacidade real do dia (ausência total: todas as horas; meia ausência: só o que passa da metade).
 */
class CoberturaCalculator(private val motor: MotorDePessoas, private val roles: RoleDirectory) {
    private val map = motor.statusMap

    fun calcular(visiveis: Set<String>, abertas: List<IssueSnapshot>, hoje: LocalDate, dias: Int): CoberturaAnalise {
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val porChave = naoConcluidas.associateBy { it.key }
        val ausencias = motor.ausencias.entre(hoje, hoje.plusDays(dias.toLong())).filter { it.pessoa in visiveis }
            .sortedWith(compareBy({ it.pessoa }, { it.de }))

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val linhas = ausencias.map { aus ->
            val pares = motor.agenda.comparar(aus.pessoa, naoConcluidas, hoje, motor.capacidadeSemAusencia)
            val diasDaAusencia = generateSequence(maxOf(aus.de, hoje)) { it.plusDays(1) }.takeWhile { !it.isAfter(aus.ate) }.toList()
            val afetadas = pares.filter { (_, sem) ->
                val caiNosDias = sem.horasPorDia.keys.any { aus.cobre(it) }
                val venceNaAusencia = sem.vencimento?.let { aus.cobre(it) } == true && (sem.termino == null || !sem.termino.isBefore(aus.de))
                caiNosDias || venceNaAusencia
            }.map { porChave.getValue(it.first.key) }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
            segmentos["ausencia:${aus.pessoa}:${aus.de}"] = afetadas

            val horas = diasDaAusencia.sumOf { d ->
                val agendadas = pares.sumOf { (_, sem) -> sem.horasPorDia[d] ?: 0.0 }
                maxOf(0.0, agendadas - motor.capacidade.dia(aus.pessoa, d))
            }
            val competencias = afetadas.flatMap { s -> s.labels }.map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.startsWith(PREFIXO_SKILL) && it.length > PREFIXO_SKILL.length }.map { it.removePrefix(PREFIXO_SKILL) }.distinct().sorted()
            CoberturaAusenciaDto(
                pessoa = aus.pessoa, de = aus.de.toString(), ate = aus.ate.toString(), issues = afetadas.map { it.key },
                horasAReplanejar = horas, competencias = competencias,
                podemCobrir = if (afetadas.isEmpty()) emptyList() else candidatos(aus, visiveis, naoConcluidas, hoje, competencias.toSet()),
            )
        }
        return CoberturaAnalise(CoberturaDto(linhas, emptyList(), dias), segmentos)
    }

    /**
     * Pessoas VISÍVEIS do mesmo squad (na data de início da ausência) com folga: soma de (capacidade − demanda) nas semanas da
     * ausência, maior que zero. Sempre em ORDEM ALFABÉTICA; a folga vai ao lado, mas nunca define a ordem.
     */
    private fun candidatos(
        aus: Ausencia, visiveis: Set<String>, abertas: List<IssueSnapshot>, hoje: LocalDate, pedidas: Set<String>,
    ): List<CandidatoDto> {
        val squad = roles.squadOf(aus.pessoa, aus.de) ?: return emptyList()
        val primeira = segundaDe(maxOf(aus.de, hoje))
        val ultima = segundaDe(aus.ate)
        val semanas = ChronoUnit.WEEKS.between(segundaDe(hoje), ultima).toInt() + 1
        return visiveis.filter { it != aus.pessoa && motor.equipes.de(it) != null && roles.squadOf(it, aus.de) == squad }.sorted().mapNotNull { c ->
            val folga = motor.demanda.carga(c, abertas, hoje, semanas).semanas
                .filter { !it.semana.isBefore(primeira) && !it.semana.isAfter(ultima) }.sumOf { it.capacidadeH - it.demandaH }
            if (folga <= FOLGA_MINIMA) return@mapNotNull null
            val tem = motor.equipes.de(c)!!.competencias
            CandidatoDto(c, arredondar(folga), tem.containsAll(pedidas))
        }
    }

    private fun segundaDe(dia: LocalDate): LocalDate = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun arredondar(horas: Double): Double = Math.round(horas * CENTESIMOS) / CENTESIMOS

    private companion object {
        const val PREFIXO_SKILL = "skill:"
        const val FOLGA_MINIMA = 1e-9
        const val CENTESIMOS = 100.0
    }
}
