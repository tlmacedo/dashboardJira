package br.sidia.painel.cards.previsao

import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.pessoas.Agenda
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.HorasDaIssue
import br.sidia.painel.previsao.RegrasDePrevisao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PV-06 ganha "pela capacidade" e "Atrasa?" a partir da Agenda (Fase 6), no CAP-1. */
class PrevisaoComCapacidadeTest {
    // PV-06 só lista task EM ANDAMENTO: a TG-202 do plano passa a In Progress para aparecer ao lado da TG-201.
    private val abertas = Cap1.linhas.map { if (it.key == "TG-202") it.copy(status = "In Progress", iniciada = "10-27T12") else it }
        .map(Cap1::snap)
    private val agenda = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap)
    private val calc = PrevisaoCalculator(Cap1.calendario, Cap1.statusMap, RegrasDePrevisao.PADRAO)

    @Test
    fun previsaoPorIssuePelaCapacidade() {
        val agendamentos = agenda.agendarTodas(abertas, Cap1.hoje)
        val porIssue = calc.analisar(abertas, emptyList(), Cap1.agora, 1L, TimeUnitMode.UTIL, agendamentos).dto.porIssue.associateBy { it.key }
        assertEquals(setOf("TG-201", "TG-202"), porIssue.keys)
        assertEquals("2026-11-03", porIssue.getValue("TG-201").pelaCapacidade)
        assertEquals(false, porIssue.getValue("TG-201").atrasa)
        assertEquals("2026-11-06", porIssue.getValue("TG-202").pelaCapacidade)
        assertEquals(true, porIssue.getValue("TG-202").atrasa)
    }

    @Test
    fun semAgendaAsColunasFicamVazias() {
        val porIssue = calc.analisar(abertas, emptyList(), Cap1.agora, 1L, TimeUnitMode.UTIL).dto.porIssue
        assertEquals(2, porIssue.size)
        for (p in porIssue) {
            assertNull(p.pelaCapacidade)
            assertNull(p.atrasa)
        }
    }

    @Test
    fun semVencimentoNaoDizSeAtrasa() {
        val semVenc = Cap1.linhas.filter { it.key == "TG-201" }.map { it.copy(vence = null) }.map(Cap1::snap)
        val ag = agenda.agendarTodas(semVenc, Cap1.hoje)
        val p = calc.analisar(semVenc, emptyList(), Cap1.agora, 1L, TimeUnitMode.UTIL, ag).dto.porIssue.single()
        assertEquals("2026-11-03", p.pelaCapacidade)
        assertNull(p.atrasa)
    }
}
