package br.sidia.painel.sync

import br.sidia.painel.db.emJson
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Leitura do worklog gravado. Sempre por instância; issues removidas logicamente não contam. */
interface WorklogStore {
    /** Os registros de cada issue de [keys] (chave -> registros por início); issue sem registro não aparece. */
    fun porIssue(instance: String, keys: Set<String>): Map<String, List<WorklogRecord>>

    /** Horas registradas por autor com `started` em `[de, ate)`. Registro sem autor não entra. */
    fun horasPorAutor(instance: String, de: Instant, ate: Instant): Map<String, Double>
}

/** Implementação sobre SQLite (JDBC), tudo parametrizado; listas entram como JSON em `json_each(?)`. */
class SqliteWorklogStore(private val ds: DataSource) : WorklogStore {

    override fun porIssue(instance: String, keys: Set<String>): Map<String, List<WorklogRecord>> {
        if (keys.isEmpty()) return emptyMap()
        val sql = """
            SELECT i.key, COALESCE(w.jira_id, CAST(w.id AS TEXT)), w.author_ref, w.started, w.seconds, w.criado_em
            FROM worklog w JOIN issue i ON i.id = w.issue_id
            WHERE i.instance = ? AND i.removida_em IS NULL AND i.key IN (SELECT value FROM json_each(?))
            ORDER BY i.key, w.started, w.jira_id
        """.trimIndent()
        return ds.connection.use { con ->
            con.prepareStatement(sql).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, keys.emJson())
                ps.executeQuery().use { rs ->
                    val saida = LinkedHashMap<String, MutableList<WorklogRecord>>()
                    while (rs.next()) {
                        saida.getOrPut(rs.getString(1)) { mutableListOf() } +=
                            WorklogRecord(rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getLong(5), rs.getTimestamp(6)?.toInstant())
                    }
                    saida
                }
            }
        }
    }

    override fun horasPorAutor(instance: String, de: Instant, ate: Instant): Map<String, Double> {
        val sql = """
            SELECT w.author_ref, SUM(w.seconds)
            FROM worklog w JOIN issue i ON i.id = w.issue_id
            WHERE i.instance = ? AND i.removida_em IS NULL AND w.author_ref IS NOT NULL AND w.started >= ? AND w.started < ?
            GROUP BY w.author_ref
            ORDER BY w.author_ref
        """.trimIndent()
        return ds.connection.use { con ->
            con.prepareStatement(sql).use { ps ->
                ps.setString(1, instance)
                ps.setTimestamp(2, Timestamp.from(de))
                ps.setTimestamp(3, Timestamp.from(ate))
                ps.executeQuery().use { rs ->
                    val saida = LinkedHashMap<String, Double>()
                    while (rs.next()) saida[rs.getString(1)] = rs.getLong(2) / SEGUNDOS_POR_HORA
                    saida
                }
            }
        }
    }

    /**
     * Troca os registros da issue na conexão e na transação do chamador (sem commit aqui): apaga os da issue e grava os
     * novos. O id do Jira é único por instância, então um worklog movido de outra issue passa para esta (atualiza no lugar).
     */
    internal fun substituir(con: Connection, instance: String, issueId: Long, registros: List<WorklogRecord>) {
        con.prepareStatement("DELETE FROM worklog WHERE issue_id = ?").use { ps ->
            ps.setLong(1, issueId)
            ps.executeUpdate()
        }
        if (registros.isEmpty()) return
        val sql = """
            INSERT INTO worklog (issue_id, author_ref, started, seconds, instance, jira_id, criado_em) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instance, jira_id) DO UPDATE SET
                issue_id = excluded.issue_id, author_ref = excluded.author_ref, started = excluded.started, seconds = excluded.seconds, criado_em = excluded.criado_em
        """.trimIndent()
        con.prepareStatement(sql).use { ps ->
            for (r in registros) {
                ps.setLong(1, issueId)
                ps.setString(2, r.autor)
                ps.setTimestamp(3, Timestamp.from(r.started))
                ps.setLong(4, r.seconds)
                ps.setString(5, instance)
                ps.setString(6, r.jiraId)
                if (r.criado != null) ps.setTimestamp(7, Timestamp.from(r.criado)) else ps.setNull(7, java.sql.Types.INTEGER)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private companion object {
        const val SEGUNDOS_POR_HORA = 3600.0
    }
}
