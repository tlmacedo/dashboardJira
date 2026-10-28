package br.sidia.painel.pessoas

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Capacidade líquida por dia, período e semana, sobre o CAP-1. */
class CapacidadeTest {
    private val cap = Cap1.capacidade
    private fun dia(s: String) = LocalDate.parse(s)

    @Test
    fun capacidadeDoDia() {
        assertEquals(5.6, cap.dia("ana", dia("2026-11-03")), 1e-9)
        assertEquals(0.0, cap.dia("ana", dia("2026-11-02")), "feriado")
        assertEquals(0.0, cap.dia("ana", dia("2026-10-31")), "sábado")
        assertEquals(0.0, cap.dia("ana", dia("2026-11-01")), "domingo")
        assertEquals(0.0, cap.dia("ana", dia("2026-11-04")), "ausência inteira")
        assertEquals(1.4, cap.dia("bruno", dia("2026-11-10")), 1e-9)
    }

    @Test
    fun brutaIgnoraAAusenciaMasNaoOFeriado() {
        assertEquals(5.6, cap.brutaDia("ana", dia("2026-11-04")), 1e-9)
        assertEquals(2.8, cap.brutaDia("bruno", dia("2026-11-10")), 1e-9)
        assertEquals(0.0, cap.brutaDia("ana", dia("2026-11-02")))
        assertEquals(0.0, cap.brutaDia("ana", dia("2026-11-07")))
    }

    @Test
    fun focoPadraoQuandoNulo() {
        // `carla` não tem foco no cadastro: vale o focoPadrao do planejamento (0,7).
        assertEquals(5.6, cap.dia("carla", dia("2026-11-09")), 1e-9)
        val com = Capacidade(Cap1.equipes, Cap1.ausencias, Cap1.calendario, RegrasDePlanejamento.PADRAO.copy(focoPadrao = 0.5))
        assertEquals(4.0, com.dia("carla", dia("2026-11-09")), 1e-9)
        // Com foco no cadastro, o padrão não conta.
        assertEquals(5.6, com.dia("ana", dia("2026-11-03")), 1e-9)
    }

    @Test
    fun periodoEhInclusivo() {
        // 03/11 (5,6) e 06/11 (5,6): 04 e 05 estão ausentes.
        assertEquals(11.2, cap.periodo("ana", dia("2026-11-03"), dia("2026-11-06")), 1e-9)
        assertEquals(5.6, cap.periodo("ana", dia("2026-11-03"), dia("2026-11-03")), 1e-9)
        assertEquals(0.0, cap.periodo("ana", dia("2026-11-06"), dia("2026-11-03")), "intervalo vazio")
    }

    @Test
    fun semanaAtualDeHojeAteDomingo() {
        // Hoje é sexta 30/10: a semana atual só tem a sexta (5,6 h); segunda a quinta já passaram.
        val s = cap.semanas("ana", dia("2026-10-30"), 4)
        assertEquals(dia("2026-10-26"), s.first().first)
        assertEquals(5.6, s.first().second, 1e-9)
        assertEquals(4, s.size)
    }

    @Test
    fun semanaComFeriadoEAusencia() {
        val s = cap.semanas("ana", dia("2026-10-30"), 4).toMap()
        assertEquals(11.2, s.getValue(dia("2026-11-02")), 1e-9)
        assertEquals(28.0, s.getValue(dia("2026-11-09")), 1e-9)
        assertEquals(22.4, s.getValue(dia("2026-11-16")), 1e-9)
    }

    @Test
    fun semanaSemCapacidade() {
        assertEquals(0.0, cap.semanas("carla", dia("2026-10-30"), 4).toMap().getValue(dia("2026-11-02")))
    }

    @Test
    fun hojeNoSabadoAteDomingoNaoTemCapacidade() {
        assertEquals(0.0, cap.semanas("ana", dia("2026-10-31"), 2).first().second)
    }

    @Test
    fun semCadastroNaEquipeEhZero() {
        assertEquals(0.0, cap.dia("fantasma", dia("2026-11-03")))
        assertEquals(0.0, cap.brutaDia("fantasma", dia("2026-11-03")))
        assertEquals(0.0, cap.periodo("fantasma", dia("2026-11-03"), dia("2026-11-09")))
        assertEquals(listOf(0.0, 0.0), cap.semanas("fantasma", dia("2026-10-30"), 2).map { it.second })
    }

    @Test
    fun semAusenciaACapacidadeVoltaAoNormal() {
        assertEquals(5.6, Cap1.capacidadeSemAusencia.dia("ana", dia("2026-11-04")), 1e-9)
        assertEquals(5.6, Cap1.capacidadeSemAusencia.dia("carla", dia("2026-11-03")), 1e-9)
    }
}
