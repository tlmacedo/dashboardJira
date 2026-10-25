package br.sidia.painel.time

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessCalendarTest {
    private val manaus = ZoneId.of("America/Manaus")
    private val cal = BusinessCalendar(manaus, fixedHolidays = setOf(MonthDay.of(10, 12)))
    private val exemplo = TestPaths.configDir.resolve("calendario.exemplo.yaml")

    /** Pasta temporária por teste; o JUnit a apaga ao final. */
    @TempDir
    lateinit var pastaTemp: Path

    /** Grava um YAML temporário com o conteúdo informado e devolve o caminho. */
    private fun yamlTemp(conteudo: String): Path =
        Files.createTempFile(pastaTemp, "calendario", ".yaml").also { Files.writeString(it, conteudo) }

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
        val comFeriadoNoSabado = BusinessCalendar(manaus, movableHolidays = setOf(LocalDate.of(2026, 10, 10)))
        val semFeriado = BusinessCalendar(manaus)
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
        val c = BusinessCalendar.fromYaml(exemplo, manaus)
        assertFalse(c.isBusinessDay(LocalDate.of(2026, 10, 12)))
        assertFalse(c.isBusinessDay(LocalDate.of(2026, 10, 24)))
        assertTrue(c.isBusinessDay(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun feriadoFixoValeEmQualquerAno() {
        for (ano in listOf(2027, 2030, 2040)) assertTrue(cal.isHoliday(LocalDate.of(ano, 10, 12)), "ano $ano")
        // 12/10/2028 cai numa quinta e deixa de ser dia útil
        val quinta = LocalDate.of(2028, 10, 12)
        assertEquals(java.time.DayOfWeek.THURSDAY, quinta.dayOfWeek)
        assertTrue(cal.isHoliday(quinta))
        assertFalse(cal.isBusinessDay(quinta))
    }

    @Test
    fun feriadoMovelSoValeNaDataCompleta() {
        val c = BusinessCalendar(manaus, movableHolidays = setOf(LocalDate.of(2026, 2, 16)))
        assertTrue(c.isHoliday(LocalDate.of(2026, 2, 16)))
        assertFalse(c.isHoliday(LocalDate.of(2027, 2, 16)))
    }

    @Test
    fun movelDe2030() {
        val c = BusinessCalendar.fromYaml(exemplo, manaus)
        assertTrue(c.isHoliday(LocalDate.of(2030, 3, 4)))
        assertTrue(c.isHoliday(LocalDate.of(2030, 4, 19)))
    }

    @Test
    fun mesDiaInvalidoFalha() {
        for (invalido in listOf("13-40", "2-30")) {
            val arq = yamlTemp("feriados_fixos:\n  - {mes_dia: \"$invalido\", nome: X}\n")
            val e = assertFailsWith<IllegalArgumentException> { BusinessCalendar.fromYaml(arq, manaus) }
            assertTrue(e.message!!.contains(arq.toString()), "deve citar o caminho: ${e.message}")
            assertTrue(e.message!!.contains(invalido), "deve citar o item: ${e.message}")
        }
    }

    @Test
    fun duplicataFalha() {
        val fixos = yamlTemp("feriados_fixos:\n  - {mes_dia: \"10-12\", nome: A}\n  - {mes_dia: \"10-12\", nome: B}\n")
        val e1 = assertFailsWith<IllegalArgumentException> { BusinessCalendar.fromYaml(fixos, manaus) }
        assertTrue(e1.message!!.contains("10-12"))
        val moveis = yamlTemp("feriados_moveis:\n  - {data: 2026-02-16, nome: A}\n  - {data: 2026-02-16, nome: B}\n")
        val e2 = assertFailsWith<IllegalArgumentException> { BusinessCalendar.fromYaml(moveis, manaus) }
        assertTrue(e2.message!!.contains("2026-02-16"))
    }

    @Test
    fun vinteENoveFevereiroSoEmAnoBissexto() {
        val c = BusinessCalendar.fromYaml(yamlTemp("feriados_fixos:\n  - {mes_dia: \"02-29\", nome: Bissexto}\n"), manaus)
        assertTrue(c.isHoliday(LocalDate.of(2028, 2, 29)))
        assertFalse(c.isHoliday(LocalDate.of(2027, 2, 28)))
        assertFalse(c.isHoliday(LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun exemploCarregaTodosOsFeriadosEsperados() {
        val c = BusinessCalendar.fromYaml(exemplo, manaus)
        assertEquals(12, c.fixedHolidays.size)
        assertEquals(20, c.movableHolidays.size)
        val carnaval = LocalDate.of(2028, 2, 29)
        assertEquals(java.time.DayOfWeek.TUESDAY, carnaval.dayOfWeek)
        assertTrue(c.isHoliday(carnaval))
    }
}
