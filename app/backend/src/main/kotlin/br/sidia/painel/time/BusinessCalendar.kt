package br.sidia.painel.time

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendário de trabalho: converte instantes para o dia civil do fuso e conta dias úteis
 * (segunda a sexta, exceto feriados). Domínio puro, sem rede.
 */
class BusinessCalendar(val zone: ZoneId, holidays: Set<LocalDate>) {
    private val holidays: Set<LocalDate> = holidays.toSet()

    /** Dia civil do instante no fuso do calendário. */
    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Dia útil: segunda a sexta que não seja feriado. */
    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && date !in holidays

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
        /** Lê `feriados: [{data: 2026-10-12, nome: ...}]` de um YAML. */
        fun fromYaml(path: Path, zone: ZoneId): BusinessCalendar {
            val arquivo = Yaml.default.decodeFromString(ArquivoCalendario.serializer(), Files.readString(path))
            return BusinessCalendar(zone, arquivo.feriados.map { LocalDate.parse(it.data) }.toSet())
        }
    }
}

@Serializable
private data class ArquivoCalendario(val feriados: List<FeriadoYaml> = emptyList())

@Serializable
private data class FeriadoYaml(val data: String, val nome: String = "")
