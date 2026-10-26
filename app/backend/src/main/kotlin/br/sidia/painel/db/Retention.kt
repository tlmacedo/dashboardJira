package br.sidia.painel.db

import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

/**
 * Retenção de histórico: apaga só `change_event` (por `at`) e `worklog` (por `started`) anteriores
 * à data-limite. Nunca toca em issue, person, sync_state nem access_log.
 */
class Retention(private val ds: DataSource, private val calendar: BusinessCalendar) {

    companion object {
        /** Instante-limite da retenção: `today - months` às 00:00 no [zone]. Mais antigo que isso é expurgado. */
        fun limite(today: LocalDate, months: Int, zone: ZoneId): Instant =
            today.minusMonths(months.toLong()).atStartOfDay(zone).toInstant()
    }

    /**
     * Remove registros anteriores a `today.minusMonths(months)` às 00:00 no fuso do calendário
     * (o que está exatamente na data-limite permanece). Devolve a soma das linhas removidas.
     */
    fun purge(today: LocalDate, months: Int): Int {
        require(months >= 1) { "months deve ser >= 1 (recebido $months)" }
        val limite = limite(today, months, calendar.zone).atZone(calendar.zone).toOffsetDateTime()
        return ds.connection.use { c ->
            c.autoCommit = false
            try {
                var total = 0
                for (sql in listOf("DELETE FROM change_event WHERE at < ?", "DELETE FROM worklog WHERE started < ?")) {
                    c.prepareStatement(sql).use { st ->
                        st.setObject(1, limite)
                        total += st.executeUpdate()
                    }
                }
                c.commit()
                total
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }
    }
}
