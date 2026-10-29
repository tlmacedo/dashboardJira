package br.sidia.painel.db

import br.sidia.painel.config.RetencaoSettings
import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

/**
 * Retenção de histórico, com um prazo por categoria ([RetencaoSettings]). Cada categoria apaga o que for anterior ao limite dela
 * (hoje menos N meses, 00:00 no fuso do calendário):
 * - HISTÓRICO: `change_event` por `at` e `worklog` por `started`; `issue` só se resolvida E sem alteração desde o limite
 *   (`resolved < limite` e `updated < limite`; os eventos e worklogs da issue saem em cascata); também sai a issue removida no Jira
 *   (`removida_em` preenchido), nunca resolvida, com remoção e última alteração antes do limite. Issue aberta ativa ou recente nunca
 *   é apagada. `issue_link` (vínculos): os que têm uma ponta purgada nesta execução e os removidos no Jira antes do limite.
 *   `jira_user_key` por `visto_em` (última vez vista);
 * - ACCESS_LOG: `access_log` por `at`;
 * - DECISÕES: `decisao_distribuicao` por `decidido_em`;
 * - EXECUÇÕES: `sync_run` por `started_at` e `backup_run` por `started_at`.
 * Nunca toca em `sync_state`. O expurgo só APAGA: não grava `sync_run` (quem registra a execução da retenção é o agendador, e a
 * trava do DPO ignora esse tipo), então rodar a retenção nunca fecha a trava do DPO na demonstração.
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

        private enum class Categoria { HISTORICO, ACCESS_LOG, DECISOES, EXECUCOES }

        /** Cada item: SQL e a categoria de prazo que ele usa. */
        private class Purga(val sql: String, val prazo: Categoria = Categoria.HISTORICO) {
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
            Purga("DELETE FROM access_log WHERE at < ?", Categoria.ACCESS_LOG),
            // O registro de decisões de Distribuição é auditoria como o access_log (quem decidiu e por quê), com prazo próprio.
            Purga("DELETE FROM decisao_distribuicao WHERE decidido_em < ?", Categoria.DECISOES),
            Purga("DELETE FROM sync_run WHERE started_at < ?", Categoria.EXECUCOES),
            Purga("DELETE FROM jira_user_key WHERE visto_em < ?"),
            Purga("DELETE FROM backup_run WHERE started_at < ?", Categoria.EXECUCOES),
        )
    }

    /**
     * Atalho com dois prazos (histórico e execuções em [months]; access_log e decisões em [accessLogMonths]): o mesmo que o
     * `purge` por categoria com [RetencaoSettings] derivado deles. Fica para os testes e chamadas simples.
     */
    fun purge(
        today: LocalDate,
        months: Int,
        accessLogMonths: Int = months,
        aoRemover: (categoria: String, removidas: Int) -> Unit = { _, _ -> },
    ): Int {
        require(months >= 1) { "months deve ser >= 1 (recebido $months)" }
        require(accessLogMonths >= 1) { "accessLogMonths deve ser >= 1 (recebido $accessLogMonths)" }
        return purge(today, RetencaoSettings(months, accessLogMonths, accessLogMonths, months), aoRemover)
    }

    /**
     * Remove registros anteriores ao limite de cada categoria (o que está exatamente na data-limite permanece), numa só
     * transação. Devolve a soma das linhas apagadas diretamente (as removidas em cascata junto com a issue não entram na conta).
     */
    fun purge(
        today: LocalDate,
        retencao: RetencaoSettings,
        /** Chamado depois do commit, uma vez por tabela, com o nome da tabela e as linhas removidas (para métricas). */
        aoRemover: (categoria: String, removidas: Int) -> Unit = { _, _ -> },
    ): Int {
        val limites = mapOf(
            Categoria.HISTORICO to limite(today, retencao.historicoMeses, calendar.zone),
            Categoria.ACCESS_LOG to limite(today, retencao.accessLogMeses, calendar.zone),
            Categoria.DECISOES to limite(today, retencao.decisoesMeses, calendar.zone),
            Categoria.EXECUCOES to limite(today, retencao.execucoesMeses, calendar.zone),
        ).mapValues { it.value.atZone(calendar.zone).toOffsetDateTime() }
        return ds.connection.use { c ->
            c.autoCommit = false
            try {
                var total = 0
                val porTabela = ArrayList<Pair<String, Int>>()
                for (purga in PURGAS) {
                    val sql = purga.sql
                    val corte = limites.getValue(purga.prazo)
                    c.prepareStatement(sql).use { st ->
                        for (i in 1..sql.count { it == '?' }) st.setTimestamp(i, java.sql.Timestamp.from(corte.toInstant()))
                        val n = st.executeUpdate()
                        total += n
                        porTabela += purga.categoria to n
                    }
                }
                c.commit()
                // O que saiu do banco não pode continuar nas leituras em cache.
                CacheDeLeituras.invalidar()
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
