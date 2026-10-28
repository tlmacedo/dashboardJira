package br.sidia.painel.cards.simulador

import br.sidia.painel.pessoas.Cap1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Simulador de ausência sobre o CAP-1: compara a agenda com e sem a ausência hipotética, sem tocar no que é real. */
class SimuladorCalculatorTest {
    private val calc = SimuladorCalculator(Cap1.motor)

    private fun dia(s: String) = LocalDate.parse(s)

    private fun simular(pessoa: String, de: String, ate: String, fracao: Double = 1.0) =
        calc.simular(pessoa, Cap1.abertas(), Cap1.hoje, dia(de), dia(ate), fracao)

    @Test
    fun simuladorCausaAtraso() {
        // O bruno (alocação 0,5) ausente de 03 a 06/11: a TG-203 (4 h, vence 05/11) só terminaria depois de 10/11.
        val a = simular("bruno", "2026-11-03", "2026-11-06")
        assertEquals(listOf("TG-203"), a.dto.atrasosCausados)
        assertEquals(listOf("TG-203"), a.atrasos.map { it.key })
        // As 1,2 h que a TG-203 consumiria em 03/11 precisam de novo dono.
        assertEquals(1.2, a.dto.horasAReplanejar, 1e-9)
        assertEquals("bruno", a.dto.pessoa)
        assertEquals(1.0, a.dto.fracao)
    }

    @Test
    fun simuladorSemEfeito() {
        // A ana já terminou tudo até 06/11: ausência em 16/11 não atrasa nada nem tem hora agendada.
        val a = simular("ana", "2026-11-16", "2026-11-18")
        assertEquals(emptyList(), a.dto.atrasosCausados)
        assertEquals(0.0, a.dto.horasAReplanejar, 1e-9)
        assertEquals(emptyList(), a.atrasos)
    }

    @Test
    fun meiaAusenciaNaoAtrasaOQueCabe() {
        // Com 0,5 a capacidade do bruno cai para 1,4 h por dia: a TG-203 ainda cabe em 03/11 (1,2 h), não atrasa e nada sobra a replanejar.
        val a = simular("bruno", "2026-11-03", "2026-11-06", 0.5)
        assertEquals(emptyList(), a.dto.atrasosCausados)
        assertEquals(0.0, a.dto.horasAReplanejar, 1e-9)
        // Já com a ana (5,6 h por dia) no dia 03/11, meia ausência (2,8 h de capacidade) deixa 2,8 h do que estava agendado sem lugar.
        val ana = simular("ana", "2026-11-03", "2026-11-03", 0.5)
        assertEquals(2.8, ana.dto.horasAReplanejar, 1e-9)
    }

    @Test
    fun jaAtrasadaPorOutroMotivoNaoContaComoCausada() {
        // A TG-202 da ana já atrasa por causa da ausência real (04 a 05/11); uma ausência extra não a "causa" de novo.
        val a = simular("ana", "2026-11-09", "2026-11-13")
        assertTrue("TG-202" !in a.dto.atrasosCausados)
    }

    @Test
    fun comparaOcupacaoDasSemanasAntesEDepois() {
        val a = simular("bruno", "2026-11-03", "2026-11-06")
        val s = a.dto.semanas.first { it.semana == "2026-11-02" }
        // Antes: 2,0 h de demanda para 11,2 h de capacidade. Depois: capacidade zero ("sem capacidade", nunca porcentagem).
        assertEquals(2.0 / 11.2, s.antes.fracao!!, 1e-9)
        assertEquals(false, s.antes.semCapacidade)
        assertEquals(true, s.depois.semCapacidade)
        assertEquals(null, s.depois.fracao)
        assertEquals("problema", s.depois.estado)
        // As semanas começam na atual (26/10) e vão até a semana do fim da ausência.
        assertEquals(listOf("2026-10-26", "2026-11-02"), a.dto.semanas.map { it.semana })
    }

    @Test
    fun naoAlteraOQueEhReal() {
        val antes = Cap1.ausencias.daPessoa("bruno")
        simular("bruno", "2026-11-03", "2026-11-06")
        assertEquals(antes, Cap1.ausencias.daPessoa("bruno"))
        assertEquals(1, Cap1.ausencias.daPessoa("bruno").size)
        // A capacidade real continua a mesma depois de simular.
        assertEquals(2.8, Cap1.capacidade.dia("bruno", dia("2026-11-03")), 1e-9)
    }
}
