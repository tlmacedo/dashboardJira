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
    fun retencaoNaoMexeEmIssuesSemDatas() {
        // issue sem resolved/updated (nulos) nunca é apagada; person e sync_state também ficam
        issue(1)
        issue(2)
        exec("INSERT INTO person(id, jira_ref, name, active) VALUES (1, 'u1', 'Ana', true)")
        exec("INSERT INTO sync_state(instance, watermark) VALUES ('main', '2020-01-01 00:00:00+00')")
        evento(1, 1, "2020-01-01 00:00:00+00")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 24)
        assertEquals(2, contar("issue"))
        assertEquals(1, contar("person"))
        assertEquals(1, contar("sync_state"))
    }

    private fun issueComDatas(id: Int, resolved: String?, updated: String?) {
        val r = resolved?.let { "'$it'" } ?: "NULL"
        val u = updated?.let { "'$it'" } ?: "NULL"
        exec("INSERT INTO issue(id, key, instance, project, resolved, updated) VALUES ($id, 'TG-$id', 'main', 'TG', $r, $u)")
    }

    @Test
    fun retencaoApagaIssueResolvidaAntigaEAccessLog() {
        // hoje 2026-10-30 em Manaus, 24 meses: limite 2024-10-30 00:00 (04:00Z)
        issueComDatas(1, "2024-09-01 12:00:00+00", "2024-09-02 12:00:00+00") // resolvida e parada: apaga
        issueComDatas(2, null, "2024-09-02 12:00:00+00") // aberta antiga: fica
        issueComDatas(3, "2024-09-01 12:00:00+00", "2025-01-10 12:00:00+00") // resolvida antiga mas mexida: fica
        evento(1, 1, "2024-09-01 12:00:00+00") // evento antigo da issue 1 (sai pelo limite, não pela cascata)
        evento(2, 1, "2026-01-01 12:00:00+00") // evento recente da issue 1: sai em cascata
        exec("INSERT INTO access_log(id, user_id, route, filters, at) VALUES (1, 'u', '/x', '', '2024-10-29 12:00:00+00')")
        exec("INSERT INTO access_log(id, user_id, route, filters, at) VALUES (2, 'u', '/x', '', '2024-10-30 04:00:00+00')")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24)
        val chaves = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT key FROM issue ORDER BY key").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        assertEquals(listOf("TG-2", "TG-3"), chaves)
        assertEquals(0, contar("change_event"))
        val ids = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT id FROM access_log").use { rs ->
                generateSequence { if (rs.next()) rs.getInt(1) else null }.toList()
            }
        }
        assertEquals(listOf(2), ids)
    }

    @Test
    fun retencaoApagaVinculosDaIssuePurgada() {
        issueComDatas(1, "2024-09-01 12:00:00+00", "2024-09-02 12:00:00+00") // purgada
        issueComDatas(2, null, "2026-10-01 12:00:00+00") // fica
        fun vinculo(instancia: String, de: String, para: String, removida: String? = null) = exec(
            "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em, removida_em) VALUES " +
                "('$instancia', '$de', '$para', 'Blocks', 'OUTWARD', now(), ${removida?.let { "'$it'" } ?: "NULL"})",
        )
        vinculo("main", "TG-1", "TG-2") // ponta purgada como origem
        vinculo("main", "TG-3", "TG-1") // ponta purgada como destino
        vinculo("main", "TG-2", "TG-3") // fica: nenhuma ponta purgada
        vinculo("main", "TG-2", "TG-4", "2024-09-01 12:00:00+00") // removida antes do limite: apaga
        vinculo("main", "TG-2", "TG-5", "2026-10-01 12:00:00+00") // removida há pouco: fica
        vinculo("outra", "TG-1", "TG-2") // outra instância sem a issue TG-1: fica
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24)
        val restantes = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT instance || ':' || from_key || '>' || to_key FROM issue_link ORDER BY 1").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        assertEquals(listOf("main:TG-2>TG-3", "main:TG-2>TG-5", "outra:TG-1>TG-2"), restantes)
    }

    @Test
    fun accessLogUsaOSeuProprioLimite() {
        // hoje 2026-10-30: com 6 meses o limite é 2026-04-30; com 24 meses do access_log é 2024-10-30
        exec("INSERT INTO access_log(id, user_id, route, filters, at) VALUES (1, 'u', '/x', '', '2025-06-01 12:00:00+00')")
        exec("INSERT INTO access_log(id, user_id, route, filters, at) VALUES (2, 'u', '/x', '', '2024-09-01 12:00:00+00')")
        issue(1)
        evento(1, 1, "2025-06-01 12:00:00+00")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 6, accessLogMonths = 24)
        assertEquals(1, contar("access_log"))
        assertEquals(0, contar("change_event"))
    }

    @Test
    fun issueRemovidaSemResolvedEParadaEApagadaComCascata() {
        // removida no Jira, nunca resolvida, parada há mais que o limite (2024-10-30): apaga
        exec("INSERT INTO issue(id, key, instance, project, updated, removida_em) VALUES (1, 'TG-1', 'main', 'TG', '2024-01-01 12:00:00+00', '2024-02-01 12:00:00+00')")
        // removida mas mexida recentemente: fica
        exec("INSERT INTO issue(id, key, instance, project, updated, removida_em) VALUES (2, 'TG-2', 'main', 'TG', '2026-09-01 12:00:00+00', '2026-09-02 12:00:00+00')")
        // aberta antiga SEM removida_em: nunca apagada
        exec("INSERT INTO issue(id, key, instance, project, updated) VALUES (3, 'TG-3', 'main', 'TG', '2024-01-01 12:00:00+00')")
        // removida há pouco, porém parada: fica até a remoção também passar do limite
        exec("INSERT INTO issue(id, key, instance, project, updated, removida_em) VALUES (4, 'TG-4', 'main', 'TG', '2024-01-01 12:00:00+00', '2026-10-01 12:00:00+00')")
        evento(1, 1, "2026-01-01 12:00:00+00")
        exec(
            "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em) VALUES " +
                "('main', 'TG-1', 'TG-3', 'Blocks', 'OUTWARD', now())",
        )
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24)
        val chaves = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT key FROM issue ORDER BY key").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        assertEquals(listOf("TG-2", "TG-3", "TG-4"), chaves)
        assertEquals(0, contar("change_event"))
        assertEquals(0, contar("issue_link"))
    }

    @Test
    fun retencaoApagaSyncRunAntigo() {
        exec("INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('samsung', 'INCREMENTAL', '2024-10-29 12:00:00+00', 'OK')")
        exec("INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('samsung', 'INCREMENTAL', '2024-10-30 04:00:00+00', 'OK')")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24)
        assertEquals(1, contar("sync_run"))
    }

    @Test
    fun retencaoApagaChaveDeUsuarioNaoVistaHaMuito() {
        exec("INSERT INTO jira_user_key(instance, user_key, username, visto_em) VALUES ('samsung', 'JIRAUSER1', 'ana', '2024-10-29 12:00:00+00')")
        exec("INSERT INTO jira_user_key(instance, user_key, username, visto_em) VALUES ('samsung', 'JIRAUSER2', 'bia', '2025-06-01 12:00:00+00')")
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24)
        assertEquals(1, contar("jira_user_key"))
    }

    @Test
    fun mesesInvalidosLancamExcecao() {
        assertFailsWith<IllegalArgumentException> { Retention(ds, calendario).purge(LocalDate.of(2026, 10, 24), 0) }
    }
}
