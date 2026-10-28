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
 *   eventos e worklogs da issue saem em cascata. Também sai a issue removida no Jira (`removida_em` preenchido), nunca
 *   resolvida, com remoção e última alteração antes do limite. Issue aberta ativa ou recente nunca é apagada;
 * - `issue_link` (vínculos entre issues): apaga os que têm uma ponta purgada nesta execução e os removidos no Jira
 *   antes do limite (`removida_em < limite`);
 * - `access_log` por `at`, com limite próprio e mais longo (`accessLogMonths`: a auditoria não encurta com a
 *   retenção geral), `sync_run` por `started_at` e `jira_user_key` por `visto_em` (última vez vista).
 * Nunca toca em `person` nem em `sync_state`.
 */
class Retention(private val ds: DataSource, private val calendar: BusinessCalendar) {

    companion object {
        /** Instante-limite da retenção: `today - months` às 00:00 no [zone]. Mais antigo que isso é expurgado. */
        fun limite(today: LocalDate, months: Int, zone: ZoneId): Instant =
            today.minusMonths(months.toLong()).atStartOfDay(zone).toInstant()

        /**
         * Ordem importa: eventos e worklogs antes das issues; os vínculos também (precisam das issues ainda gravadas
         * para saber quais serão purgadas, pois `issue_link` não tem FK nem cascata); as demais tabelas são independentes.
         */
        /**
         * Issue purgável (alias `i`, 4 parâmetros): resolvida e parada antes do limite; OU removida no Jira
         * (`removida_em` preenchido), nunca resolvida, com a remoção e a última alteração antes do limite.
         * Issue aberta ativa ou recente (sem `removida_em`) nunca entra.
         */
        private const val ISSUE_PURGAVEL =
            "((i.resolved < ? AND i.updated < ?) OR " +
                "(i.removida_em IS NOT NULL AND i.resolved IS NULL AND i.removida_em < ? AND i.updated < ?))"

        /** Cada item: SQL e se usa o limite do `access_log` (os demais usam o limite geral). */
        private class Purga(val sql: String, val doAccessLog: Boolean = false) {
            /** Nome da tabela do `DELETE FROM <tabela>`: é o rótulo da métrica de remoções (nunca dado). */
            val categoria: String get() = sql.substringAfter("DELETE FROM ").substringBefore(' ')
        }

        private val PURGAS = listOf(
            Purga("DELETE FROM change_event WHERE at < ?"),
            Purga("DELETE FROM worklog WHERE started < ?"),
            // Vínculo com uma ponta que esta execução vai purgar (mesma instância) ou removido no Jira antes do limite.
            Purga(
                "DELETE FROM issue_link AS l WHERE l.removida_em < ? OR EXISTS (SELECT 1 FROM issue i WHERE i.instance = l.instance " +
                    "AND i.key IN (l.from_key, l.to_key) AND $ISSUE_PURGAVEL)",
            ),
            Purga("DELETE FROM issue AS i WHERE $ISSUE_PURGAVEL"),
            Purga("DELETE FROM access_log WHERE at < ?", doAccessLog = true),
            Purga("DELETE FROM sync_run WHERE started_at < ?"),
            Purga("DELETE FROM jira_user_key WHERE visto_em < ?"),
            Purga("DELETE FROM backup_run WHERE started_at < ?"),
        )
    }

    /**
     * Remove registros anteriores a `today.minusMonths(months)` às 00:00 no fuso do calendário
     * (o que está exatamente na data-limite permanece), numa só transação. Devolve a soma das linhas
     * apagadas diretamente (as removidas em cascata junto com a issue não entram na conta).
     */
    fun purge(
        today: LocalDate,
        months: Int,
        accessLogMonths: Int = months,
        /** Chamado depois do commit, uma vez por tabela, com o nome da tabela e as linhas removidas (para métricas). */
        aoRemover: (categoria: String, removidas: Int) -> Unit = { _, _ -> },
    ): Int {
        require(months >= 1) { "months deve ser >= 1 (recebido $months)" }
        require(accessLogMonths >= 1) { "accessLogMonths deve ser >= 1 (recebido $accessLogMonths)" }
        val limite = limite(today, months, calendar.zone).atZone(calendar.zone).toOffsetDateTime()
        val limiteAccessLog = limite(today, accessLogMonths, calendar.zone).atZone(calendar.zone).toOffsetDateTime()
        return ds.connection.use { c ->
            c.autoCommit = false
            try {
                var total = 0
                val porTabela = ArrayList<Pair<String, Int>>()
                for (purga in PURGAS) {
                    val sql = purga.sql
                    val corte = if (purga.doAccessLog) limiteAccessLog else limite
                    c.prepareStatement(sql).use { st ->
                        for (i in 1..sql.count { it == '?' }) st.setTimestamp(i, java.sql.Timestamp.from(corte.toInstant()))
                        val n = st.executeUpdate()
                        total += n
                        porTabela += purga.categoria to n
                    }
                }
                c.commit()
                // A observação nunca muda o desfecho da retenção (já confirmada).
                porTabela.forEach { (categoria, n) -> runCatching { aoRemover(categoria, n) } }
                total
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }
    }
}
