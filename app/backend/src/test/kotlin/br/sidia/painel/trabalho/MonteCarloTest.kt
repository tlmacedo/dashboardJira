package br.sidia.painel.trabalho

import br.sidia.painel.cards.FiltrosDeCard
import br.sidia.painel.domain.TimeUnitMode
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Monte Carlo com taxa de chegada, semente injetável e previsão condicional pela idade. */
class MonteCarloTest {
    private fun mc(semente: Long = 42) = MonteCarlo(Random(semente))

    @Test
    fun vazaoConstanteSemChegada() {
        // Como o app anterior (monte_carlo_when) para vazão constante: 20 itens a 5 por semana = 4 semanas.
        assertEquals(Quando(4, 4, 4), mc().quando(List(8) { 5 }, null, 20, 10_000, 104))
    }

    @Test
    fun comChegadaEstendeOPrazo() {
        // Entram 1 por semana e saem 5: o saldo cai 4 por semana, 20 -> 0 em 5 semanas.
        assertEquals(Quando(5, 5, 5), mc().quando(List(8) { 5 }, List(8) { 1 }, 20, 10_000, 104))
    }

    @Test
    fun chegadaIgualAVazaoNaoConverge() {
        // Entra o que sai: o backlog nunca termina; a resposta é "mais que o limite", sem número inventado nem exceção.
        assertEquals(Quando(null, null, null), mc().quando(listOf(5), listOf(5), 20, 10_000, 104))
        assertEquals(Quando(null, null, null), mc().quando(listOf(5), listOf(7), 20, 1_000, 104))
    }

    @Test
    fun backlogVazioTerminaEmZeroSemanas() {
        assertEquals(Quando(0, 0, 0), mc().quando(listOf(5), null, 0, 100, 104))
    }

    @Test
    fun semVazaoNenhumaNaoConverge() {
        assertEquals(Quando(null, null, null), mc().quando(listOf(0, 0, 0), null, 3, 100, 104))
        assertEquals(Quando(null, null, null), mc().quando(emptyList(), null, 3, 100, 104))
    }

    @Test
    fun percentisOrdenados() {
        val q = mc(42).quando(listOf(2, 8), null, 20, 10_000, 104)
        val (p50, p85, p95) = Triple(q.p50!!, q.p85!!, q.p95!!)
        assertTrue(p50 <= p85 && p85 <= p95, "$q")
        assertTrue(p50 in 2..10 && p95 in 2..10, "$q")
        // 20 itens com semanas de 2 ou 8: pelo menos 3 semanas (8 + 8 + 8) e no máximo 10 (2 x 10).
        assertTrue(p50 >= 3, "$q")
    }

    @Test
    fun mesmaSementeMesmoResultado() {
        val a = mc(7).quando(listOf(1, 3, 6, 0), listOf(1, 0, 2), 25, 5_000, 104)
        val b = mc(7).quando(listOf(1, 3, 6, 0), listOf(1, 0, 2), 25, 5_000, 104)
        assertEquals(a, b)
        assertEquals(mc(7).quantos(listOf(1, 3, 6, 0), 4, 5_000), mc(7).quantos(listOf(1, 3, 6, 0), 4, 5_000))
        // Outra semente pode dar outro resultado (a semente é mesmo usada).
        val outras = (1L..20L).map { mc(it).quando(listOf(1, 9), null, 30, 50, 104) }.toSet()
        assertTrue(outras.size > 1, "a semente não tem efeito: $outras")
    }

    @Test
    fun sementeDoDiaEstavel() {
        val f = FiltrosDeCard(
            squad = "Alfa", pessoa = null, tipos = listOf("Checklist", "Checklist Review"), prioridades = emptyList(),
            os = null, de = null, ate = null, unit = TimeUnitMode.UTIL,
        )
        val dia = LocalDate.parse("2026-10-30")
        assertEquals(sementeDoDia(dia, f), sementeDoDia(dia, f))
        // A ordem dos tipos não muda a semente (mesmos filtros).
        assertEquals(sementeDoDia(dia, f), sementeDoDia(dia, f.copy(tipos = f.tipos.reversed())))
        assertNotEquals(sementeDoDia(dia, f), sementeDoDia(dia.plusDays(1), f))
        assertNotEquals(sementeDoDia(dia, f), sementeDoDia(dia, f.copy(squad = "Beta")))
        assertNotEquals(sementeDoDia(dia, f), sementeDoDia(dia, f.copy(unit = TimeUnitMode.CORRIDO)))
    }

    @Test
    fun quantosConservador() {
        // 4 semanas a 5 por semana: sempre 20, em qualquer percentil.
        assertEquals(Triple(20, 20, 20), mc().quantos(listOf(5), 4, 5_000))
        // Com variação, P85 e P95 são conservadores (menores ou iguais ao P50).
        val (p50, p85, p95) = mc().quantos(listOf(0, 2, 10), 4, 10_000)
        assertTrue(p95 <= p85 && p85 <= p50, "$p50 $p85 $p95")
    }

    @Test
    fun quantosSemHistoricoZera() {
        assertEquals(Triple(0, 0, 0), mc().quantos(emptyList(), 4, 1_000))
    }

    @Test
    fun restanteDadaIdade() {
        // Como remaining_given_age: dos ciclos maiores que a idade, o que faltou (P50, P85), mínimo de 5.
        assertEquals(5.0 to 7.8, mc().restanteDadaIdade(listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0), 3.0))
        assertNull(mc().restanteDadaIdade(listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0), 5.0)) // só 4 mais longos
        assertNull(mc().restanteDadaIdade(emptyList(), 1.0))
        // O mínimo é configurável.
        assertEquals(1.0 to 1.0, mc().restanteDadaIdade(listOf(2.0), 1.0, minimo = 1))
    }
}
