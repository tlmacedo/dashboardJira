package br.sidia.painel.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PercentilesTest {
    private val umADez = (1..10).map { it.toDouble() }

    @Test
    fun p50DeUmADez() {
        assertEquals(5.5, Percentiles.of(umADez, 0.5)!!, 1e-9)
        assertEquals(5.5, Percentiles.median(umADez)!!, 1e-9)
    }

    @Test
    fun p85DeUmADez() {
        assertEquals(8.65, Percentiles.of(umADez, 0.85)!!, 1e-9)
    }

    @Test
    fun umSoValor() {
        for (p in listOf(0.0, 0.3, 0.5, 0.85, 1.0)) {
            assertEquals(7.0, Percentiles.of(listOf(7.0), p)!!, 1e-9)
        }
    }

    @Test
    fun listaVazia() {
        assertNull(Percentiles.of(emptyList(), 0.5))
        assertNull(Percentiles.median(emptyList()))
    }

    @Test
    fun pForaDoIntervaloFalha() {
        assertFailsWith<IllegalArgumentException> { Percentiles.of(umADez, -0.1) }
        assertFailsWith<IllegalArgumentException> { Percentiles.of(umADez, 1.1) }
        assertFailsWith<IllegalArgumentException> { Percentiles.of(umADez, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Percentiles.of(emptyList(), 2.0) }
    }

    @Test
    fun ordemDeEntradaNaoImporta() {
        val embaralhado = listOf(9.0, 2.0, 10.0, 1.0, 5.0, 7.0, 3.0, 8.0, 6.0, 4.0)
        assertEquals(8.65, Percentiles.of(embaralhado, 0.85)!!, 1e-9)
        assertEquals(5.5, Percentiles.median(embaralhado)!!, 1e-9)
    }
}
