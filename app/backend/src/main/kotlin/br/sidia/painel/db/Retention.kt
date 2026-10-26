package br.sidia.painel.db

import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

/**
 * Retenção de histórico. Apaga o que for anterior ao limite (hoje menos N meses, 00:00 no fuso do calendário):
 * - `change_event` por `at` e `worklog` por `started`;
 * - `issue` só se resolvida E sem alteração desde o limite (`resolved < limite` e `updated < limite`); os
 *   eventos e worklogs da issue saem em cascata. Issue aberta (sem `resolved`) nunca é apagada;
 * - `access_log` por `at`, `sync_run` por `started_at` e `jira_user_key` por `visto_em` (última vez vista).
 * Nunca toca em `person` nem em `sync_state`.
 */
class Retention(private val ds: DataSource, private val calendar: BusinessCalendar) {

    companion object {
        /** Instante-limite da retenção: `today - months` às 00:00 no [zone]. Mais antigo que isso é expurgado. */
        fun limite(today: LocalDate, months: Int, zone: ZoneId): Instant =
            today.minusMonths(months.toLong()).atStartOfDay(zone).toInstant()

        /** Ordem importa: eventos e worklogs antes das issues; as demais tabelas são independentes. */
        private val PURGAS = listOf(
            "DELETE FROM change_event WHERE at < ?",
            "DELETE FROM worklog WHERE started < ?",
            "DELETE FROM issue WHERE resolved < ? AND updated < ?",
            "DELETE FROM access_log WHERE at < ?",
            "DELETE FROM sync_run WHERE started_at < ?",
            "DELETE FROM jira_user_key WHERE visto_em < ?",
        )
    }

    /**
     * Remove registros anteriores a `today.minusMonths(months)` às 00:00 no fuso do calendário
     * (o que está exatamente na data-limite permanece), numa só transação. Devolve a soma das linhas
     * apagadas diretamente (as removidas em cascata junto com a issue não entram na conta).
     */
    fun purge(today: LocalDate, months: Int): Int {
        require(months >= 1) { "months deve ser >= 1 (recebido $months)" }
        val limite = limite(today, months, calendar.zone).atZone(calendar.zone).toOffsetDateTime()
        return ds.connection.use { c ->
            c.autoCommit = false
            try {
                var total = 0
                for (sql in PURGAS) {
                    c.prepareStatement(sql).use { st ->
                        for (i in 1..sql.count { it == '?' }) st.setObject(i, limite)
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
