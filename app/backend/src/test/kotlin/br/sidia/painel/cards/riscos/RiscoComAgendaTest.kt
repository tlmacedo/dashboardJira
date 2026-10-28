package br.sidia.painel.cards.riscos

import br.sidia.painel.pessoas.Agenda
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.HorasDaIssue
import br.sidia.painel.riscos.FatorDeRisco
import br.sidia.painel.riscos.RegrasDeRisco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** O fator "previsão depois do vencimento" do Riscos passa a vir da Agenda (término pela capacidade), no CAP-1. */
class RiscoComAgendaTest {
    private val abertas = Cap1.abertas()
    private val agenda = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap)

    private fun fatores(calc: RiscoCalculator, key: String) =
        calc.avaliar(abertas, emptyList(), Cap1.agora).first { it.key == key }.fatores.toMap()

    @Test
    fun previsaoAposVencimentoNoRisco() {
        val calc = RiscoCalculator(Cap1.calendario, Cap1.statusMap, RegrasDeRisco.PADRAO, agenda.comoPrevisao(abertas, Cap1.hoje))
        // TG-202 vence 05/11 e a ana só termina em 06/11 (ausente em 04 e 05/11): ganha 20 pontos.
        assertEquals(20, fatores(calc, "TG-202")[FatorDeRisco.PREVISAO_APOS_VENCIMENTO])
        // TG-201 termina em 03/11, antes do vencimento (04/11): sem o fator.
        assertTrue(FatorDeRisco.PREVISAO_APOS_VENCIMENTO !in fatores(calc, "TG-201"))
        // Vencida vale VENCIDA, não a previsão (fatores exclusivos).
        val f205 = fatores(calc, "TG-205")
        assertNotNull(f205[FatorDeRisco.VENCIDA])
        assertTrue(FatorDeRisco.PREVISAO_APOS_VENCIMENTO !in f205)
    }

    @Test
    fun semAgendaNaoHaPrevisao() {
        val calc = RiscoCalculator(Cap1.calendario, Cap1.statusMap, RegrasDeRisco.PADRAO)
        assertTrue(FatorDeRisco.PREVISAO_APOS_VENCIMENTO !in fatores(calc, "TG-202"))
    }

    @Test
    fun semAusenciaAPrevisaoNaoAtrasa() {
        val semAusencia = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidadeSemAusencia, Cap1.statusMap)
        val calc = RiscoCalculator(Cap1.calendario, Cap1.statusMap, RegrasDeRisco.PADRAO, semAusencia.comoPrevisao(abertas, Cap1.hoje))
        assertTrue(FatorDeRisco.PREVISAO_APOS_VENCIMENTO !in fatores(calc, "TG-202"))
    }
}
