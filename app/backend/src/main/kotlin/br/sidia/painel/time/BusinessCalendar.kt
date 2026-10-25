package br.sidia.painel.time

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeParseException
import java.time.ZoneId

/**
 * Calendário de trabalho: converte instantes para o dia civil do fuso e conta dias úteis
 * (segunda a sexta, exceto feriados). Domínio puro, sem rede.
 *
 * Feriados de data fixa (nacionais, estaduais, municipais) são guardados só com mês e dia e valem
 * em qualquer ano; 29/02 conta apenas em ano bissexto. Feriados de data móvel (Carnaval, Sexta-feira
 * Santa, Corpus Christi) são guardados com a data completa e valem só naquele dia.
 */
class BusinessCalendar(
    val zone: ZoneId,
    fixedHolidays: Set<MonthDay> = emptySet(),
    movableHolidays: Set<LocalDate> = emptySet(),
) {
    val fixedHolidays: Set<MonthDay> = fixedHolidays.toSet()
    val movableHolidays: Set<LocalDate> = movableHolidays.toSet()

    /** Feriado: data móvel exata ou mês/dia fixo (MonthDay.from(29/02) só existe em ano bissexto). */
    fun isHoliday(date: LocalDate): Boolean =
        date in movableHolidays || MonthDay.from(date) in fixedHolidays

    /** Dia civil do instante no fuso do calendário. */
    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Dia útil: segunda a sexta que não seja feriado. */
    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !isHoliday(date)

    /**
     * Soma, para cada dia útil local coberto pelo intervalo, a fração do dia (24h) dentro dele.
     * Fim de semana e feriado valem zero, então feriado em fim de semana não desconta duas vezes.
     */
    fun businessDays(from: Instant, to: Instant): Double {
        require(!from.isAfter(to)) { "Intervalo invertido: $from > $to" }
        val inicio = from.atZone(zone)
        val fim = to.atZone(zone)
        var total = 0.0
        var dia = inicio.toLocalDate()
        val ultimo = fim.toLocalDate()
        while (!dia.isAfter(ultimo)) {
            if (isBusinessDay(dia)) {
                val iniDia = dia.atStartOfDay(zone).toInstant()
                val fimDia = dia.plusDays(1).atStartOfDay(zone).toInstant()
                val a = maxOf(iniDia, from)
                val b = minOf(fimDia, to)
                if (b.isAfter(a)) {
                    // fração real do dia, robusta a dias que não tenham exatamente 24h
                    total += Duration.between(a, b).toMillis().toDouble() /
                        Duration.between(iniDia, fimDia).toMillis().toDouble()
                }
            }
            dia = dia.plusDays(1)
        }
        return total
    }

    /** Dias corridos: duração dividida por 24h. */
    fun calendarDays(from: Instant, to: Instant): Double {
        require(!from.isAfter(to)) { "Intervalo invertido: $from > $to" }
        return Duration.between(from, to).toMillis().toDouble() / Duration.ofHours(24).toMillis().toDouble()
    }

    companion object {
        /**
         * Lê `feriados_fixos: [{mes_dia: "MM-DD", nome: ...}]` e `feriados_moveis: [{data: AAAA-MM-DD, nome: ...}]`.
         * Formato inválido ou item duplicado lança [IllegalArgumentException] citando o arquivo e o item.
         */
        fun fromYaml(path: Path, zone: ZoneId): BusinessCalendar {
            val arquivo = Yaml.default.decodeFromString(ArquivoCalendario.serializer(), Files.readString(path))
            val fixos = mutableSetOf<MonthDay>()
            for (f in arquivo.feriadosFixos) {
                val md = try {
                    MonthDay.parse("--${f.mesDia}")
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("$path: mes_dia inválido '${f.mesDia}' (esperado MM-DD) em '${f.nome}'", e)
                }
                require(fixos.add(md)) { "$path: feriado fixo duplicado '${f.mesDia}' ('${f.nome}')" }
            }
            val moveis = mutableSetOf<LocalDate>()
            for (m in arquivo.feriadosMoveis) {
                val d = try {
                    LocalDate.parse(m.data)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("$path: data inválida '${m.data}' (esperado AAAA-MM-DD) em '${m.nome}'", e)
                }
                require(moveis.add(d)) { "$path: feriado móvel duplicado '${m.data}' ('${m.nome}')" }
            }
            return BusinessCalendar(zone, fixos, moveis)
        }
    }
}

@Serializable
private data class ArquivoCalendario(
    @SerialName("feriados_fixos") val feriadosFixos: List<FeriadoFixoYaml> = emptyList(),
    @SerialName("feriados_moveis") val feriadosMoveis: List<FeriadoMovelYaml> = emptyList(),
)

@Serializable
private data class FeriadoFixoYaml(@SerialName("mes_dia") val mesDia: String, val nome: String = "")

@Serializable
private data class FeriadoMovelYaml(val data: String, val nome: String = "")
