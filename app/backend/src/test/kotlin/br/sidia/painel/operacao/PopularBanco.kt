package br.sidia.painel.operacao

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/**
 * Dados SINTÉTICOS para os testes de backup e restauração (nada vem do Jira nem de pessoas reais). O gerador do Plano 8
 * (`GeradorSintetico`) ainda não existe na `main`: este inserir direto cobre o que o teste precisa (issues, eventos,
 * worklogs, registro de acesso e execuções de sincronização), de forma determinística.
 */
object PopularBanco {
    private val TIPOS = listOf("Checklist", "Checklist TG", "Checklist Review")
    private val STATUS = listOf("To Do", "In Progress", "In Review", "Done")
    private val BASE: Instant = Instant.parse("2026-09-01T12:00:00Z")

    fun popular(ds: DataSource, issues: Int = 500, eventosPorIssue: Int = 5, worklogs: Int = 800, acessos: Int = 120) {
        ds.connection.use { c ->
            c.autoCommit = false
            c.prepareStatement(
                "INSERT INTO issue(key, instance, project, issue_type, status, resolution, assignee_ref, created, updated, resolved, duedate, labels) " +
                    "VALUES (?, 'samsung', 'TG', ?, ?, ?, ?, ?, ?, ?, ?, '[]')",
            ).use { ps ->
                for (i in 1..issues) {
                    val status = STATUS[i % STATUS.size]
                    val criada = BASE.plusSeconds(i * 3600L)
                    ps.setString(1, "TG-$i")
                    ps.setString(2, TIPOS[i % TIPOS.size])
                    ps.setString(3, status)
                    ps.setString(4, if (status == "Done") "Done" else null)
                    ps.setString(5, "dev.alfa${1 + i % 3}")
                    ps.setTimestamp(6, Timestamp.from(criada))
                    ps.setTimestamp(7, Timestamp.from(criada.plusSeconds(7200)))
                    if (status == "Done") ps.setTimestamp(8, Timestamp.from(criada.plusSeconds(7200))) else ps.setNull(8, java.sql.Types.INTEGER)
                    ps.setString(9, "2026-11-%02d".format(1 + i % 28))
                    ps.executeUpdate()
                }
            }
            c.prepareStatement("INSERT INTO change_event(changelog_id, issue_id, field, from_value, to_value, at, author_ref) VALUES (?, ?, 'status', ?, ?, ?, 'dev.alfa1')").use { ps ->
                for (i in 1..issues) {
                    for (e in 1..eventosPorIssue) {
                        ps.setString(1, "c$e")
                        ps.setInt(2, i)
                        ps.setString(3, STATUS[(e - 1) % STATUS.size])
                        ps.setString(4, STATUS[e % STATUS.size])
                        ps.setTimestamp(5, Timestamp.from(BASE.plusSeconds(i * 3600L + e * 600L)))
                        ps.executeUpdate()
                    }
                }
            }
            c.prepareStatement("INSERT INTO worklog(issue_id, author_ref, started, seconds) VALUES (?, 'dev.alfa2', ?, ?)").use { ps ->
                for (w in 1..worklogs) {
                    ps.setInt(1, 1 + w % issues)
                    ps.setTimestamp(2, Timestamp.from(BASE.plusSeconds(w * 900L)))
                    ps.setInt(3, 1800 + w % 7 * 600)
                    ps.executeUpdate()
                }
            }
            c.prepareStatement("INSERT INTO access_log(user_id, route, filters, at) VALUES (?, ?, '{}', ?)").use { ps ->
                for (a in 1..acessos) {
                    ps.setString(1, if (a % 2 == 0) "ana.silva" else "gerente.exemplo")
                    ps.setString(2, "/api/cards/abertas")
                    ps.setTimestamp(3, Timestamp.from(BASE.plusSeconds(a * 60L)))
                    ps.executeUpdate()
                }
            }
            c.prepareStatement("INSERT INTO sync_run(instance, kind, started_at, finished_at, status) VALUES ('samsung', 'INCREMENTAL', ?, ?, 'OK')").use { ps ->
                for (s in 1..30) {
                    ps.setTimestamp(1, Timestamp.from(BASE.plusSeconds(s * 600L)))
                    ps.setTimestamp(2, Timestamp.from(BASE.plusSeconds(s * 600L + 20)))
                    ps.executeUpdate()
                }
            }
            c.commit()
        }
    }

    /** Linhas por tabela do usuário (nome → linhas), para comparar origem e restauração. */
    fun contagens(ds: DataSource): Map<String, Long> = ds.connection.use { c ->
        val tabelas = c.createStatement().executeQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
        tabelas.associateWith { t -> c.createStatement().executeQuery("SELECT count(*) FROM \"$t\"").use { it.next(); it.getLong(1) } }
    }
}
