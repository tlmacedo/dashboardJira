package br.sidia.painel.sync

import br.sidia.painel.domain.IssueTimeline
import kotlinx.serialization.json.JsonObject
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** Persistência das issues e do changelog. */
interface IssueStore {
    /** Grava a issue (atualiza se já existir) e os eventos novos; repetir a chamada não duplica nada. */
    fun upsert(issue: JsonObject, events: List<ChangeEventRecord>, instance: String)

    /**
     * Linhas do tempo da instância. `openOnly=true` devolve só as não resolvidas (os limites são ignorados).
     * Caso contrário, com [closedFrom] e/ou [closedTo], só as resolvidas em `[closedFrom, closedTo)`;
     * sem nenhum limite, todas.
     */
    fun timelines(instance: String, closedFrom: Instant?, closedTo: Instant?, openOnly: Boolean): List<IssueTimeline>
}

/** Implementação sobre PostgreSQL (JDBC). Todas as consultas são parametrizadas. */
class PostgresIssueStore(private val ds: DataSource) : IssueStore {

    override fun upsert(issue: JsonObject, events: List<ChangeEventRecord>, instance: String) {
        val c = CamposDaIssue.de(issue)
        // O changelog fica nas linhas de change_event; o raw guarda só a issue, sem dados pessoais.
        val raw = SanitizadorRaw.sanitizar(JsonObject(issue.filterKeys { it != "changelog" })).toString()
        ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                val issueId = gravarIssue(con, c, instance, raw)
                gravarEventos(con, issueId, events)
                con.commit()
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
        }
    }

    private fun gravarIssue(con: Connection, c: CamposDaIssue, instance: String, raw: String): Long {
        val sql = """
            INSERT INTO issue (key, instance, project, issue_type, status, resolution, assignee_ref, reporter_ref,
                               created, updated, resolved, duedate, labels, parent_key, estimate_s, spent_s, raw)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (instance, key) DO UPDATE SET
                project = EXCLUDED.project, issue_type = EXCLUDED.issue_type,
                status = EXCLUDED.status, resolution = EXCLUDED.resolution, assignee_ref = EXCLUDED.assignee_ref,
                reporter_ref = EXCLUDED.reporter_ref, created = EXCLUDED.created, updated = EXCLUDED.updated,
                resolved = EXCLUDED.resolved, duedate = EXCLUDED.duedate, labels = EXCLUDED.labels,
                parent_key = EXCLUDED.parent_key, estimate_s = EXCLUDED.estimate_s, spent_s = EXCLUDED.spent_s,
                raw = EXCLUDED.raw
            RETURNING id
        """.trimIndent()
        con.prepareStatement(sql).use { ps ->
            ps.setString(1, c.key)
            ps.setString(2, instance)
            ps.setString(3, c.project)
            ps.setString(4, c.issueType)
            ps.setString(5, c.status)
            ps.setString(6, c.resolution)
            ps.setString(7, c.assignee)
            ps.setString(8, c.reporter)
            ps.setTimestamp(9, c.created?.let(Timestamp::from))
            ps.setTimestamp(10, c.updated?.let(Timestamp::from))
            ps.setTimestamp(11, c.resolved?.let(Timestamp::from))
            ps.setDate(12, c.duedate?.let { Date.valueOf(LocalDate.parse(it.take(10))) })
            ps.setArray(13, con.createArrayOf("text", c.labels.toTypedArray()))
            ps.setString(14, c.parentKey)
            if (c.estimateS != null) ps.setLong(15, c.estimateS) else ps.setNull(15, java.sql.Types.BIGINT)
            if (c.spentS != null) ps.setLong(16, c.spentS) else ps.setNull(16, java.sql.Types.BIGINT)
            ps.setString(17, raw)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "upsert da issue não devolveu o id" }
                return rs.getLong(1)
            }
        }
    }

    private fun gravarEventos(con: Connection, issueId: Long, events: List<ChangeEventRecord>) {
        if (events.isEmpty()) return
        val sql = """
            INSERT INTO change_event (changelog_id, issue_id, field, from_value, to_value, at, author_ref)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (issue_id, changelog_id) DO NOTHING
        """.trimIndent()
        con.prepareStatement(sql).use { ps ->
            for (e in events) {
                ps.setString(1, e.changelogId)
                ps.setLong(2, issueId)
                ps.setString(3, e.field)
                ps.setString(4, e.from)
                ps.setString(5, e.to)
                ps.setTimestamp(6, Timestamp.from(e.at))
                ps.setString(7, e.authorRef)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private class LinhaIssue(
        val id: Long, val key: String, val summary: String, val issueType: String, val status: String,
        val resolution: String?, val assignee: String?, val created: Instant, val resolved: Instant?,
        val labels: List<String>,
    )

    override fun timelines(instance: String, closedFrom: Instant?, closedTo: Instant?, openOnly: Boolean): List<IssueTimeline> {
        val filtros = StringBuilder("instance = ? AND created IS NOT NULL")
        val params = mutableListOf<Any>(instance)
        if (openOnly) {
            filtros.append(" AND resolved IS NULL")
        } else {
            if (closedFrom != null) {
                filtros.append(" AND resolved >= ?")
                params += Timestamp.from(closedFrom)
            }
            if (closedTo != null) {
                filtros.append(" AND resolved < ?")
                params += Timestamp.from(closedTo)
            }
        }
        val sql = "SELECT id, key, raw->'fields'->>'summary', issue_type, status, resolution, assignee_ref, " +
            "created, resolved, labels FROM issue WHERE $filtros ORDER BY key"
        ds.connection.use { con ->
            val linhas = con.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, p ->
                    if (p is Timestamp) ps.setTimestamp(i + 1, p) else ps.setString(i + 1, p as String)
                }
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            @Suppress("UNCHECKED_CAST")
                            val labels = (rs.getArray(10)?.array as? Array<String>)?.toList().orEmpty()
                            add(
                                LinhaIssue(
                                    rs.getLong(1), rs.getString(2), rs.getString(3).orEmpty(), rs.getString(4).orEmpty(),
                                    rs.getString(5).orEmpty(), rs.getString(6), rs.getString(7),
                                    rs.getTimestamp(8).toInstant(), rs.getTimestamp(9)?.toInstant(), labels,
                                ),
                            )
                        }
                    }
                }
            }
            if (linhas.isEmpty()) return emptyList()
            val eventos = eventosDe(con, linhas)
            return linhas.map { l ->
                ChangelogParser.montarTimeline(
                    key = l.key, summary = l.summary, issueType = l.issueType, created = l.created,
                    status = l.status, assignee = l.assignee, resolved = l.resolved, resolution = l.resolution,
                    labels = l.labels, events = eventos[l.id].orEmpty(),
                )
            }
        }
    }

    private fun eventosDe(con: Connection, linhas: List<LinhaIssue>): Map<Long, List<ChangeEventRecord>> {
        val chaves = linhas.associate { it.id to it.key }
        val sql = "SELECT issue_id, changelog_id, field, from_value, to_value, at, author_ref " +
            "FROM change_event WHERE issue_id = ANY (?) ORDER BY at, id"
        con.prepareStatement(sql).use { ps ->
            ps.setArray(1, con.createArrayOf("bigint", chaves.keys.toTypedArray()))
            ps.executeQuery().use { rs ->
                val saida = HashMap<Long, MutableList<ChangeEventRecord>>()
                while (rs.next()) {
                    val id = rs.getLong(1)
                    saida.getOrPut(id) { mutableListOf() } += ChangeEventRecord(
                        changelogId = rs.getString(2), issueKey = chaves.getValue(id), field = rs.getString(3),
                        from = rs.getString(4), to = rs.getString(5), at = rs.getTimestamp(6).toInstant(),
                        authorRef = rs.getString(7),
                    )
                }
                return saida
            }
        }
    }
}
