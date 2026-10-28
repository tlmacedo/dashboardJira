package br.sidia.painel.cards.tendencia

import br.sidia.painel.cards.AvisosDePessoas
import br.sidia.painel.cards.OcupacaoDto
import br.sidia.painel.domain.Transition
import br.sidia.painel.pessoas.Ausencia
import br.sidia.painel.pessoas.Ausencias
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao.SemCapacidade
import br.sidia.painel.pessoas.Ocupacao.Valor
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tendência de ocupação reconstruída do changelog, com as horas de hoje. */
class TendenciaCalculatorTest {
    private val calc = TendenciaCalculator(Cap1.motor)
    private val pessoas = listOf("ana", "bruno", "carla")

    /** TG-650: era do bruno até 12/10 e é da ana desde então; 6 h, vence 06/11. */
    private val trocada: IssueSnapshot = Cap1.snapshot("TG-650", "Checklist", "ana", "Open", Trb1.t("09-20T12"), LocalDate.of(2026, 11, 6), 6 * 3600L, null, null)
        .copy(events = listOf(Trb1.ev("TG-650", "assignee", "bruno", "ana", "10-12T12")))

    private fun TendenciaDto.ocupacao(pessoa: String, segunda: String): OcupacaoDto =
        linhas.first { it.pessoa == pessoa }.ocupacoes[semanas.indexOf(segunda)]

    @Test
    fun tendenciaUsaResponsavelDaEpoca() {
        val d = calc.calcular(pessoas, listOf(trocada), Cap1.hoje)
        assertEquals(12, d.semanas.size)
        assertEquals("2026-10-26", d.semanas.last())
        assertEquals("2026-08-10", d.semanas.first())
        // Na semana de 05/10 a task ainda era do bruno: entra na carga dele e não na da ana.
        assertTrue((d.ocupacao("bruno", "2026-10-05").fracao ?: 0.0) > 0.0)
        assertEquals(0.0, d.ocupacao("ana", "2026-10-05").fracao!!, 1e-9)
        // Na semana de 19/10 é da ana.
        assertEquals(0.0, d.ocupacao("bruno", "2026-10-19").fracao!!, 1e-9)
        assertTrue((d.ocupacao("ana", "2026-10-19").fracao ?: 0.0) > 0.0)
    }

    @Test
    fun taskAindaNaoCriadaOuJaFechadaNaoEntra() {
        val base = Cap1.snapshot(
            "TG-651", "Checklist", "bruno", "Closed", Trb1.t("09-20T12"), LocalDate.of(2026, 10, 30), 6 * 3600L, null, null,
            transicoes = listOf(Transition(Trb1.t("09-21T12"), "Open", "In Progress", null), Transition(Trb1.t("10-08T12"), "In Progress", "Closed", null)),
        )
        val fechada = base.copy(resolved = Trb1.t("10-08T12"), resolution = "Done", timeline = base.timeline.copy(resolved = Trb1.t("10-08T12"), resolution = "Done"))
        val d = calc.calcular(pessoas, listOf(fechada), Cap1.hoje)
        // Aberta (e ocupando a semana) em 05/10; fechada em 08/10, então na semana de 12/10 já não conta; antes de 09/20 não existia.
        assertTrue((d.ocupacao("bruno", "2026-10-05").fracao ?: 0.0) > 0.0)
        assertEquals(0.0, d.ocupacao("bruno", "2026-10-12").fracao!!, 1e-9)
        assertEquals(0.0, d.ocupacao("bruno", "2026-08-10").fracao!!, 1e-9, "ainda não existia")
    }

    @Test
    fun padraoRecorrente() {
        // Acima do limite alto (1,10) em 3 das últimas 4 semanas: recorrente.
        assertEquals("RECORRENTE", calc.padrao(listOf(Valor(1.2), Valor(1.3), Valor(0.9), Valor(1.15))))
        // Só 1 ou 2 das 4: pontual.
        assertEquals("PONTUAL", calc.padrao(listOf(Valor(1.2), Valor(0.5), Valor(0.6), Valor(0.7))))
        assertEquals("PONTUAL", calc.padrao(listOf(Valor(1.2), Valor(1.2), Valor(0.6), Valor(0.7))))
        // 1,10 não passa de 1,10.
        assertEquals("NORMAL", calc.padrao(listOf(Valor(0.9), Valor(1.1), Valor(0.6), Valor(0.7))))
        // Só as 4 últimas semanas contam; "sem capacidade" nunca é sobrecarga.
        assertEquals("NORMAL", calc.padrao(listOf(Valor(1.5), Valor(1.5), Valor(1.5), Valor(0.5), Valor(0.5), Valor(0.5), Valor(0.5))))
        assertEquals("PONTUAL", calc.padrao(listOf(SemCapacidade, SemCapacidade, Valor(1.2), SemCapacidade)))
        assertEquals("NORMAL", calc.padrao(emptyList()))
    }

    @Test
    fun avisoDeHorasAtuais() {
        val avisos = calc.calcular(pessoas, emptyList(), Cap1.hoje).avisos
        assertTrue(avisos.any { it.codigo == AvisosDePessoas.HORAS_ATUAIS_NO_PASSADO })
    }

    @Test
    fun pessoasNaOrdemRecebida() {
        assertEquals(pessoas, calc.calcular(pessoas, listOf(trocada), Cap1.hoje).linhas.map { it.pessoa })
        assertEquals(listOf("carla"), calc.calcular(listOf("carla"), listOf(trocada), Cap1.hoje).linhas.map { it.pessoa })
    }

    @Test
    fun semanaSemCapacidadeNaoViraPorcentagem() {
        // A carla ficou ausente a semana inteira de 12/10 (segunda a sexta) com uma task de 6 h no nome dela.
        val ausencias = Ausencias(listOf(Ausencia("carla", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), 1.0)))
        val motor = MotorDePessoas(Cap1.equipes, ausencias, Cap1.calendario, Cap1.regras, Cap1.statusMap)
        val dela = Cap1.snapshot("TG-652", "Checklist", "carla", "Open", Trb1.t("09-20T12"), LocalDate.of(2026, 11, 6), 6 * 3600L, null, null)
        val s = TendenciaCalculator(motor).calcular(pessoas, listOf(dela), Cap1.hoje).ocupacao("carla", "2026-10-12")
        assertTrue(s.semCapacidade)
        assertEquals(null, s.fracao)
        assertEquals("problema", s.estado)
    }
}
