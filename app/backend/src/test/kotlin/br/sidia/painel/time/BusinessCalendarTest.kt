package br.sidia.painel.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessCalendarTest {
    private val manaus = ZoneId.of("America/Manaus")
    private val feriado = LocalDate.of(2026, 10, 12)
    private val cal = BusinessCalendar(manaus, setOf(feriado))

    @Test
    fun viradaDeDia() {
        assertEquals(LocalDate.of(2026, 10, 30), cal.localDate(Instant.parse("2026-10-31T03:59:00Z")))
        assertEquals(LocalDate.of(2026, 10, 31), cal.localDate(Instant.parse("2026-10-31T04:00:00Z")))
    }

    @Test
    fun diaUtil() {
        assertTrue(cal.isBusinessDay(LocalDate.of(2026, 10, 9)))
        assertFalse(cal.isBusinessDay(LocalDate.of(2026, 10, 10)))
        assertFalse(cal.isBusinessDay(LocalDate.of(2026, 10, 11)))
        assertFalse(cal.isBusinessDay(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun businessDaysSegundaAteTerca() {
        val r = cal.businessDays(Instant.parse("2026-10-05T12:00:00Z"), Instant.parse("2026-10-06T12:00:00Z"))
        assertEquals(1.0, r, 1e-6)
    }

    @Test
    fun businessDaysComFeriadoNaSegunda() {
        val de = Instant.parse("2026-10-09T12:00:00Z")
        val ate = Instant.parse("2026-10-12T12:00:00Z")
        assertEquals(0.666667, cal.businessDays(de, ate), 1e-6)
        assertEquals(3.0, cal.calendarDays(de, ate), 1e-6)
    }

    @Test
    fun feriadoNoFimDeSemanaNaoDuplica() {
        val de = Instant.parse("2026-10-09T12:00:00Z")
        val ate = Instant.parse("2026-10-12T12:00:00Z")
        val comFeriadoNoSabado = BusinessCalendar(manaus, setOf(LocalDate.of(2026, 10, 10)))
        val semFeriado = BusinessCalendar(manaus, emptySet())
        assertEquals(semFeriado.businessDays(de, ate), comFeriadoNoSabado.businessDays(de, ate), 1e-9)
    }

    @Test
    fun intervaloInvertidoFalha() {
        assertFailsWith<IllegalArgumentException> {
            cal.businessDays(Instant.parse("2026-10-06T12:00:00Z"), Instant.parse("2026-10-05T12:00:00Z"))
        }
    }

    @Test
    fun carregaYamlDeExemplo() {
        val c = BusinessCalendar.fromYaml(java.nio.file.Path.of("../config/calendario.exemplo.yaml"), manaus)
        assertFalse(c.isBusinessDay(LocalDate.of(2026, 10, 12)))
        assertFalse(c.isBusinessDay(LocalDate.of(2026, 10, 24)))
        assertTrue(c.isBusinessDay(LocalDate.of(2026, 10, 13)))
    }
}
