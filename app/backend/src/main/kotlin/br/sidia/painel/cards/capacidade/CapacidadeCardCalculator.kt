package br.sidia.painel.cards.capacidade

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.cards.carga.CargaCalculator
import br.sidia.painel.cards.paraDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.Jain
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** O DTO e, por segmento, as tasks por trás dos números (a gaveta abre exatamente estas), com o término previsto de cada uma. */
class CapacidadeAnalise(val dto: CapacidadeDto, val segmentos: Map<String, List<IssueSnapshot>>, val terminos: Map<String, String?>)

/**
 * Capacidade e ausências: o mapa de [SEMANAS_DO_MAPA] semanas, os alertas (carga acima do limite; semana com 2 ou mais dias
 * de ausência e trabalho agendado) e os atrasos previstos pela capacidade, comparando com o cenário SEM a ausência para dizer
 * a causa. Domínio puro. Não ordena pessoas por carga: mapa e ausências saem em ordem alfabética.
 */
class CapacidadeCardCalculator(private val motor: MotorDePessoas) {
    companion object {
        const val SEMANAS_DO_MAPA = 8
        private const val DIAS_DE_AUSENCIA_NO_ALERTA = 2.0
    }

    private val regras = motor.regras

    fun calcular(pessoas: List<String>, abertas: List<IssueSnapshot>, hoje: LocalDate, versaoRegras: String): CapacidadeAnalise {
        val naoConcluidas = abertas.filter { motor.statusMap.category(it.status) != StatusCategory.DONE }
        val segundaDeHoje = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val fimDoMapa = segundaDeHoje.plusWeeks(SEMANAS_DO_MAPA.toLong()).minusDays(1)

        val mapa = mutableListOf<LinhaMapaDto>()
        val alertas = mutableListOf<AlertaCapacidadeDto>()
        val atrasos = mutableListOf<Triple<AtrasoPrevistoDto, IssueSnapshot, Boolean>>()
        val ocupacoes = mutableListOf<Ocupacao>()
        var algumaHoraAssumida = false
        val porChave = naoConcluidas.associateBy { it.key }

        for (p in pessoas) {
            val carga = motor.demanda.carga(p, naoConcluidas, hoje, SEMANAS_DO_MAPA)
            algumaHoraAssumida = algumaHoraAssumida || carga.horasAssumidas
            ocupacoes += motor.demanda.carga(p, naoConcluidas, hoje, CargaCalculator.SEMANAS).ocupacaoHorizonte
            val semAusencia = motor.capacidadeSemAusencia.semanas(p, hoje, SEMANAS_DO_MAPA).toMap()

            mapa += LinhaMapaDto(
                p,
                carga.semanas.map { s ->
                    val ausente = s.capacidadeH <= 0.0 && (semAusencia[s.semana] ?: 0.0) > 0.0
                    CelulaMapaDto(s.semana.toString(), s.ocupacao.paraDto(regras, s.demandaH), ausente)
                },
            )
            for (s in carga.semanas) {
                val o = s.ocupacao
                if (o is Ocupacao.Valor && o.fracao > regras.ocupacaoAlta) {
                    alertas += AlertaCapacidadeDto(p, "CARGA_ALTA", s.semana.toString(), null)
                }
            }

            val pares = motor.agenda.comparar(p, naoConcluidas, hoje, motor.capacidadeSemAusencia)
            // Alerta de ausência com trabalho: semana com 2 ou mais dias de ausência E horas agendadas nesses dias no cenário sem ausência.
            for (i in 0 until SEMANAS_DO_MAPA) {
                val segunda = segundaDeHoje.plusWeeks(i.toLong())
                val dias = generateSequence(maxOf(segunda, hoje)) { it.plusDays(1) }.takeWhile { !it.isAfter(segunda.plusDays(6)) }
                    .filter { motor.calendario.isBusinessDay(it) && motor.ausencias.fracaoEm(p, it) > 0.0 }.toList()
                if (dias.sumOf { motor.ausencias.fracaoEm(p, it) } < DIAS_DE_AUSENCIA_NO_ALERTA) continue
                val horas = pares.sumOf { (_, sem) -> dias.sumOf { d -> (sem.horasPorDia[d] ?: 0.0) * motor.ausencias.fracaoEm(p, d) } }
                if (horas > 0.0) alertas += AlertaCapacidadeDto(p, "AUSENCIA_COM_TRABALHO", segunda.toString(), horas)
            }

            for ((atual, sem) in pares) {
                val vence = atual.vencimento ?: continue
                // Só atraso PREVISTO: a task já vencida está em "vencidas" da Carga, não é previsão.
                if (!atual.atrasa || vence.isBefore(hoje)) continue
                val s = porChave.getValue(atual.key)
                val porAusencia = !sem.atrasa
                atrasos += Triple(
                    AtrasoPrevistoDto(atual.key, p, vence.toString(), atual.termino?.toString(), if (porAusencia) "AUSENCIA" else "CARGA"),
                    s, porAusencia,
                )
            }
        }

        val listaAtrasos = atrasos.sortedWith(
            compareBy<Triple<AtrasoPrevistoDto, IssueSnapshot, Boolean>> { it.first.vence }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.first.key },
        ).map { it.first }
        fun ordena(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val segmentos = mapOf(
            "atrasosPrevistos" to ordena(atrasos.map { it.second }),
            "atrasosPorAusencia" to ordena(atrasos.filter { it.third }.map { it.second }),
        )
        val ausencias = motor.ausencias.entre(hoje, fimDoMapa).filter { it.pessoa in pessoas }
            .sortedWith(compareBy({ it.pessoa }, { it.de })).map { AusenciaDto(it.pessoa, it.de.toString(), it.ate.toString(), it.fracao) }
        val feriados = generateSequence(hoje) { it.plusDays(1) }.takeWhile { !it.isAfter(fimDoMapa) }
            .filter { motor.calendario.isHoliday(it) }.map { FeriadoDto(it.toString(), motor.calendario.holidayName(it)) }.toList()

        val dto = CapacidadeDto(
            versaoRegras = versaoRegras,
            alertas = alertas.sortedWith(compareBy({ it.pessoa }, { it.semana }, { it.tipo })),
            atrasosPrevistos = atrasos.size, atrasosPorAusencia = atrasos.count { it.third },
            jain = Jain.indice(ocupacoes, regras.tetoJain), mapa = mapa, ausencias = ausencias, feriados = feriados,
            listaAtrasos = listaAtrasos, avisos = avisosDePessoas(motor, naoConcluidas, algumaHoraAssumida),
        )
        return CapacidadeAnalise(dto, segmentos, atrasos.associate { it.first.key to it.first.termino })
    }
}
