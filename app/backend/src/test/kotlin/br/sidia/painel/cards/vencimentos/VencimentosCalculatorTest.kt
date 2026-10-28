package br.sidia.painel.cards.vencimentos

import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vencimentos da semana com folga real (dias úteis entre o término previsto e o vencimento), sobre o CAP-1. */
class VencimentosCalculatorTest {
    private val calc = VencimentosCalculator(Cap1.motor)
    private fun dia(s: String) = LocalDate.parse(s)

    @Test
    fun vencimentosSeteDias() {
        val a = calc.calcular(Cap1.abertas(), Cap1.hoje, 7)
        assertEquals(7, a.dto.dias)
        assertEquals(listOf("TG-201", "TG-202", "TG-203"), a.dto.linhas.map { it.key })
        val l = a.dto.linhas.associateBy { it.key }
        assertEquals(Triple("2026-11-03", 1.0, "ok"), l.getValue("TG-201").let { Triple(it.termino, it.folgaDiasUteis, it.estado) })
        assertEquals(Triple("2026-11-06", -1.0, "problema"), l.getValue("TG-202").let { Triple(it.termino, it.folgaDiasUteis, it.estado) })
        assertEquals(Triple("2026-11-03", 2.0, "ok"), l.getValue("TG-203").let { Triple(it.termino, it.folgaDiasUteis, it.estado) })
        assertEquals(1, a.dto.riscoDeAtrasar)
        assertEquals(3, a.dto.vencem)
        assertEquals("ana", l.getValue("TG-202").pessoa)
    }

    @Test
    fun vencidaFicaDeFora() {
        assertTrue(calc.calcular(Cap1.abertas(), Cap1.hoje, 14).dto.linhas.none { it.key == "TG-205" })
    }

    @Test
    fun catorzeDiasPegaMaisUmaSemana() {
        val longe = Cap1.snapshot("TG-430", "Checklist", "bruno", "Open", Trb1.t("10-22T12"), dia("2026-11-12"), 3600L, null, null)
        assertEquals(3, calc.calcular(Cap1.abertas() + longe, Cap1.hoje, 7).dto.linhas.size)
        assertEquals(4, calc.calcular(Cap1.abertas() + longe, Cap1.hoje, 14).dto.linhas.size)
    }

    @Test
    fun semResponsavelOuForaDoHorizonteEhRisco() {
        val sem = Cap1.snapshot("TG-431", "Checklist", null, "Open", Trb1.t("10-22T12"), dia("2026-11-04"), 3600L, null, null)
        val l = calc.calcular(Cap1.abertas() + sem, Cap1.hoje, 7).dto.linhas.first { it.key == "TG-431" }
        assertNull(l.termino)
        assertNull(l.folgaDiasUteis)
        assertEquals("problema", l.estado)
        assertNull(l.pessoa)
    }

    @Test
    fun folgaConsideraFimDeSemanaEFeriado() {
        // Término na sexta 30/10 e vencimento na terça 03/11: sábado, domingo e o feriado de 02/11 não contam; só a terça.
        assertEquals(1.0, calc.folgaDiasUteis(dia("2026-10-30"), dia("2026-11-03")))
        assertEquals(0.0, calc.folgaDiasUteis(dia("2026-11-03"), dia("2026-11-03")))
        assertEquals(-1.0, calc.folgaDiasUteis(dia("2026-11-03"), dia("2026-10-30")))
        // Vencimento num feriado (02/11): de 03 a 06/11 são 4 dias úteis de atraso.
        assertEquals(-4.0, calc.folgaDiasUteis(dia("2026-11-06"), dia("2026-11-02")))
    }

    @Test
    fun folgaZeroEhAtencao() {
        // Duas tasks de 5,6 h da ana (um dia de capacidade cada) com vencimento em 03/11: a primeira da fila termina hoje
        // (sexta 30/10; folga de 1 dia útil, a terça 03/11) e a segunda só na terça 03/11 (folga zero).
        val a = Cap1.snapshot("TG-440", "Checklist", "ana", "Open", Trb1.t("10-22T12"), dia("2026-11-03"), (5.6 * 3600).toLong(), null, null, "Highest")
        val sozinha = calc.calcular(listOf(a), Cap1.hoje, 7).dto.linhas.single()
        assertEquals(1.0, sozinha.folgaDiasUteis)
        val duas = calc.calcular(listOf(a, a.copy(key = "TG-441", priority = "High")), Cap1.hoje, 7).dto.linhas.associateBy { it.key }
        assertEquals(0.0, duas.getValue("TG-441").folgaDiasUteis)
        assertEquals("atencao", duas.getValue("TG-441").estado)
    }

    @Test
    fun segmentosTemOMesmoTamanhoDoNumero() {
        val a = calc.calcular(Cap1.abertas(), Cap1.hoje, 7)
        assertEquals(a.dto.vencem, a.segmentos.getValue("vencem").size)
        assertEquals(a.dto.riscoDeAtrasar, a.segmentos.getValue("riscoDeAtrasar").size)
        assertEquals(listOf("TG-202"), a.segmentos.getValue("riscoDeAtrasar").map { it.key })
    }
}
