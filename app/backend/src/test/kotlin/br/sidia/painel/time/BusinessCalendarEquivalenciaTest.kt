package br.sidia.painel.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `businessDays` foi acelerado (os dias inteiros do meio do intervalo valem exatamente 1,0 e não precisam de conversão de
 * fuso). Este teste compara, BIT A BIT, com a implementação anterior (copiada abaixo como referência) em milhares de
 * intervalos sorteados, em fusos com e sem horário de verão e com feriados fixos e móveis.
 */
class BusinessCalendarEquivalenciaTest {
    /** A implementação anterior, dia a dia, com conversão de fuso em todo dia útil. */
    private fun referencia(cal: BusinessCalendar, from: Instant, to: Instant): Double {
        require(!from.isAfter(to))
        val zone = cal.zone
        val inicio = from.atZone(zone)
        val fim = to.atZone(zone)
        var total = 0.0
        var dia = inicio.toLocalDate()
        val ultimo = fim.toLocalDate()
        while (!dia.isAfter(ultimo)) {
            if (cal.isBusinessDay(dia)) {
                val iniDia = dia.atStartOfDay(zone).toInstant()
                val fimDia = dia.plusDays(1).atStartOfDay(zone).toInstant()
                val a = maxOf(iniDia, from)
                val b = minOf(fimDia, to)
                if (b.isAfter(a)) {
                    total += Duration.between(a, b).toMillis().toDouble() / Duration.between(iniDia, fimDia).toMillis().toDouble()
                }
            }
            dia = dia.plusDays(1)
        }
        return total
    }

    private fun calendario(zona: String) = BusinessCalendar(
        ZoneId.of(zona),
        fixedHolidays = setOf(MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(9, 7), MonthDay.of(12, 25), MonthDay.of(2, 29)),
        movableHolidays = setOf(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 6, 4), LocalDate.of(2026, 3, 8)),
    )

    @Test
    fun mesmoResultadoQueAImplementacaoAnteriorEmIntervalosSorteados() {
        val rnd = Random(2026_10_28)
        val base = Instant.parse("2025-01-01T00:00:00Z").epochSecond
        for (zona in listOf("America/Manaus", "America/New_York", "Europe/Lisbon", "Australia/Lord_Howe", "UTC")) {
            val cal = calendario(zona)
            repeat(3_000) {
                val a = base + rnd.nextInt(3 * 365 * 86_400)
                // intervalos de poucos minutos a dois anos, inclusive nulos
                val duracao = when (rnd.nextInt(4)) {
                    0 -> rnd.nextInt(3 * 3_600).toLong()
                    1 -> rnd.nextInt(5 * 86_400).toLong()
                    2 -> rnd.nextInt(120 * 86_400).toLong()
                    else -> rnd.nextInt(730 * 86_400).toLong()
                }
                val de = Instant.ofEpochSecond(a)
                val ate = Instant.ofEpochSecond(a + duracao)
                assertEquals(referencia(cal, de, ate), cal.businessDays(de, ate), "$zona $de -> $ate")
            }
        }
    }

    @Test
    fun mesmoResultadoNasFronteirasDeDiaEDeHorarioDeVerao() {
        val cal = calendario("America/New_York")
        val zone = cal.zone
        // meia-noite exata, 1 ms antes e depois, e as viradas do horário de verão (dias de 23 e de 25 horas)
        val dias = listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))
        val instantes = dias.flatMap { d ->
            val meiaNoite = d.atStartOfDay(zone).toInstant()
            listOf(meiaNoite.minusMillis(1), meiaNoite, meiaNoite.plusMillis(1), meiaNoite.plusSeconds(12 * 3600))
        }.sorted()
        for (i in instantes.indices) for (j in i until instantes.size) {
            assertEquals(referencia(cal, instantes[i], instantes[j]), cal.businessDays(instantes[i], instantes[j]), "${instantes[i]} -> ${instantes[j]}")
        }
    }

    @Test
    fun diaInteiroDeSemanaVale1EFimDeSemanaEFeriadoValem0() {
        val cal = calendario("America/Manaus")
        val zone = cal.zone
        fun inicio(d: LocalDate) = d.atStartOfDay(zone).toInstant()
        assertEquals(1.0, cal.businessDays(inicio(LocalDate.of(2026, 10, 27)), inicio(LocalDate.of(2026, 10, 28))))
        assertEquals(0.0, cal.businessDays(inicio(LocalDate.of(2026, 10, 24)), inicio(LocalDate.of(2026, 10, 25)))) // sábado
        assertEquals(0.0, cal.businessDays(inicio(LocalDate.of(2026, 9, 7)), inicio(LocalDate.of(2026, 9, 8)))) // feriado (segunda)
        // uma semana inteira de segunda a segunda tem 5 dias úteis
        assertEquals(5.0, cal.businessDays(inicio(LocalDate.of(2026, 10, 19)), inicio(LocalDate.of(2026, 10, 26))))
        assertTrue(cal.businessDays(Instant.parse("2026-10-27T12:00:00Z"), Instant.parse("2026-10-27T12:00:00Z")) == 0.0)
    }

    @Test
    fun intervaloInvertidoContinuaFalhando() {
        val cal = calendario("America/Manaus")
        val erro = runCatching { cal.businessDays(Instant.parse("2026-10-28T00:00:00Z"), Instant.parse("2026-10-27T00:00:00Z")) }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException)
    }
}
