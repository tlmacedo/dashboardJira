package br.sidia.painel.db

import br.sidia.painel.time.BusinessCalendar
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Testes contra PostgreSQL real (Testcontainers); nunca H2 nem mock. */
class RetentionTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }
    }

    private val zona = ZoneId.of("America/Manaus")
    private val calendario = BusinessCalendar(zona)
    private lateinit var ds: DataSource

    @BeforeTest
    fun preparar() {
        ds = Database.connect(pg.jdbcUrl, pg.username, pg.password)
        // banco limpo a cada teste: recria o schema e migra de novo
        ds.connection.use { it.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as? AutoCloseable)?.close()
    }

    private fun exec(sql: String) = ds.connection.use { it.createStatement().execute(sql) }

    private fun contar(tabela: String): Int = ds.connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM $tabela").use { it.next(); it.getInt(1) }
    }

    private fun issue(id: Int) =
        exec("INSERT INTO issue(id, key, instance, project) VALUES ($id, 'TG-$id', 'main', 'TG')")

    private fun evento(id: Int, issueId: Int, instante: String) =
        exec("INSERT INTO change_event(id, changelog_id, issue_id, field, at) VALUES ($id, 'c$id', $issueId, 'status', '$instante')")

    @Test
    fun migracaoCriaTabelas() {
        val nomes = ds.connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public'",
            ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toSet() }
        }
        val esperadas = setOf("person", "issue", "change_event", "worklog", "sync_state", "access_log")
        assertEquals(true, nomes.containsAll(esperadas), "tabelas encontradas: $nomes")
    }

    @Test
    fun retencaoRemoveSoOQueExcedeOLimite() {
        issue(1)
        // limite = 2024-10-24 00:00 em Manaus (-04:00): exatamente nele permanece, um instante antes sai
        evento(1, 1, "2024-10-24 00:00:00-04")
        evento(2, 1, "2024-10-23 23:59:59-04")
        val removidas = Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 24)
        assertEquals(1, removidas)
        assertEquals(1, contar("change_event"))
    }

    @Test
    fun retencaoApagaWorklogAntigoPorStarted() {
        issue(1)
        exec("INSERT INTO worklog(id, issue_id, started, seconds) VALUES (1, 1, '2024-10-24 00:00:00-04', 60)")
        exec("INSERT INTO worklog(id, issue_id, started, seconds) VALUES (2, 1, '2024-10-23 12:00:00-04', 60)")
        evento(1, 1, "2024-10-01 00:00:00-04")
        assertEquals(2, Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 24))
        assertEquals(1, contar("worklog"))
        assertEquals(0, contar("change_event"))
    }

    @Test
    fun retencaoNaoMexeEmIssues() {
        issue(1)
        issue(2)
        exec("INSERT INTO person(id, jira_ref, name, active) VALUES (1, 'u1', 'Ana', true)")
        exec("INSERT INTO sync_state(instance, watermark) VALUES ('main', '2020-01-01 00:00:00+00')")
        exec("INSERT INTO access_log(id, user_id, route, filters, at) VALUES (1, 'u1', '/x', '', '2020-01-01 00:00:00+00')")
        evento(1, 1, "2020-01-01 00:00:00+00")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 24)
        assertEquals(2, contar("issue"))
        assertEquals(1, contar("person"))
        assertEquals(1, contar("sync_state"))
        assertEquals(1, contar("access_log"))
    }

    @Test
    fun mesesInvalidosLancamExcecao() {
        assertFailsWith<IllegalArgumentException> { Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 0) }
    }
}
