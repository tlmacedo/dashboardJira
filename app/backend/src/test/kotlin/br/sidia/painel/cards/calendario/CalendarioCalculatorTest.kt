package br.sidia.painel.cards.calendario

import br.sidia.painel.pessoas.Cap1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Calendário do time sobre o CAP-1 (semana de 02/11: feriado, ausências e vencimentos). */
class CalendarioCalculatorTest {
    private val calc = CalendarioCalculator(Cap1.motor)
    private val pessoas = listOf("ana", "bruno", "carla")
    private val inicio = LocalDate.of(2026, 11, 2)
    private val semana = calc.calcular(pessoas, Cap1.abertas(), inicio, ModoCalendario.SEMANA)
    private fun dia(d: String) = semana.dto.dias.first { it.dia == d }

    @Test
    fun calendarioSemana() {
        val d = semana.dto
        assertEquals("2026-11-02", d.inicio)
        assertEquals(7, d.dias.size)
        assertEquals("Finados", dia("2026-11-02").feriado)
        assertFalse(dia("2026-11-02").util)
        assertNull(dia("2026-11-03").feriado)
        assertFalse(dia("2026-11-07").util, "sábado")
        val carla = d.linhas.first { it.pessoa == "carla" }.celulas
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0), carla.map { it.ausencia })
        val ana = d.linhas.first { it.pessoa == "ana" }.celulas
        assertEquals(listOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0), ana.map { it.ausencia })
        assertEquals(1, ana.first { it.dia == "2026-11-04" }.vencimentos)
        assertEquals(1, ana.first { it.dia == "2026-11-05" }.vencimentos)
        assertEquals(1, d.linhas.first { it.pessoa == "bruno" }.celulas.first { it.dia == "2026-11-05" }.vencimentos)
    }

    @Test
    fun capacidadeDoSquadNoDia() {
        // 04/11: ana e carla ausentes, bruno presente: 2,8 ÷ 14,0 = 0,2, e o dia tem vencimento, então é crítico.
        assertEquals(0.2, dia("2026-11-04").capacidadeSquad!!, 1e-9)
        assertTrue(dia("2026-11-04").critico)
        assertEquals(1, dia("2026-11-04").vencimentos)
        // 05/11 também (2 vencimentos); 03/11 e 06/11 estão a 60% (sem vencimento): não são críticos.
        assertTrue(dia("2026-11-05").critico)
        assertEquals(2, dia("2026-11-05").vencimentos)
        assertEquals(0.6, dia("2026-11-03").capacidadeSquad!!, 1e-9)
        assertFalse(dia("2026-11-03").critico)
        // Dia não útil não tem capacidade (nulo, nunca zero ou porcentagem).
        assertNull(dia("2026-11-02").capacidadeSquad)
        assertNull(dia("2026-11-07").capacidadeSquad)
        assertFalse(dia("2026-11-02").critico)
    }

    @Test
    fun capacidadeSoDasPessoasDaVisao() {
        val soAna = calc.calcular(listOf("ana"), Cap1.abertas(), inicio, ModoCalendario.SEMANA).dto
        assertEquals(1, soAna.linhas.size)
        // Só a ana: 03/11 está a 100% e 04/11 a 0%; nada da capacidade dos colegas aparece.
        assertEquals(1.0, soAna.dias.first { it.dia == "2026-11-03" }.capacidadeSquad!!, 1e-9)
        assertEquals(0.0, soAna.dias.first { it.dia == "2026-11-04" }.capacidadeSquad!!, 1e-9)
    }

    @Test
    fun mesTem28Dias() {
        val m = calc.calcular(pessoas, Cap1.abertas(), inicio, ModoCalendario.MES).dto
        assertEquals(28, m.dias.size)
        assertEquals("Consciência Negra", m.dias.first { it.dia == "2026-11-20" }.feriado)
        assertEquals(28, m.linhas.first().celulas.size)
    }

    @Test
    fun segmentosPorDiaTemOMesmoTamanhoDoNumero() {
        for (d in semana.dto.dias) assertEquals(d.vencimentos, semana.segmentos.getValue("dia:${d.dia}").size, d.dia)
        assertEquals(listOf("TG-202", "TG-203"), semana.segmentos.getValue("dia:2026-11-05").map { it.key })
    }

    @Test
    fun pessoasNaOrdemRecebidaESemMotivo() {
        assertEquals(pessoas, semana.dto.linhas.map { it.pessoa })
        assertTrue("motivo" !in semana.dto.toString().lowercase())
    }

    @Test
    fun feriadoSemNomeAindaEhFeriado() {
        val semNome = br.sidia.painel.time.BusinessCalendar(Cap1.zona, setOf(java.time.MonthDay.of(11, 2)))
        val motor = br.sidia.painel.pessoas.MotorDePessoas(Cap1.equipes, Cap1.ausencias, semNome, Cap1.regras, Cap1.statusMap)
        val d = CalendarioCalculator(motor).calcular(pessoas, Cap1.abertas(), inicio, ModoCalendario.SEMANA).dto.dias.first()
        assertEquals("", d.feriado)
        assertFalse(d.util)
    }
}
