package br.sidia.painel.sync

import br.sidia.painel.db.Database
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes contra PostgreSQL real (Testcontainers); nunca H2 nem mock. */
class SyncStateStoreTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }
    }

    private lateinit var ds: DataSource
    private lateinit var store: PostgresSyncStateStore

    private fun t(s: String) = Instant.parse(s)

    private fun limpar() =
        ds.connection.use { it.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }

    @BeforeTest
    fun preparar() {
        ds = Database.connect(pg.jdbcUrl, pg.username, pg.password)
        limpar()
        Database.migrate(ds)
        store = PostgresSyncStateStore(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as? AutoCloseable)?.close()
    }

    private fun <T> consulta(sql: String, leitor: (java.sql.ResultSet) -> T): T =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); leitor(it) } }

    @Test
    fun migracaoAplicaEmBancoPopulado() {
        limpar()
        // banco só com V1..V3 e dados
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("3").load().migrate()
        ds.connection.use {
            it.createStatement().execute(
                "INSERT INTO issue(id, key, instance, project) VALUES (1, 'TG-1', 'main', 'TG');" +
                    "INSERT INTO change_event(id, changelog_id, issue_id, field, at) VALUES (1, 'c1', 1, 'status', '2026-10-01 00:00:00+00')",
            )
        }
        Database.migrate(ds)
        assertEquals(1, consulta("SELECT count(*) FROM issue") { it.getInt(1) })
        assertEquals(1, consulta("SELECT count(*) FROM change_event") { it.getInt(1) })
        assertNull(consulta("SELECT jira_id FROM issue") { it.getObject(1) })
        assertNull(consulta("SELECT removida_em FROM issue") { it.getObject(1) })
    }

    @Test
    fun cursorAusenteEhNulo() {
        assertNull(store.cursor("samsung"))
    }

    @Test
    fun cursorAvancaENuncaRecua() {
        store.avancarCursor("samsung", t("2026-10-30T12:20:00Z"))
        store.avancarCursor("samsung", t("2026-10-30T12:10:00Z"))
        assertEquals(t("2026-10-30T12:20:00Z"), store.cursor("samsung"))
        store.avancarCursor("samsung", t("2026-10-30T12:30:00Z"))
        assertEquals(t("2026-10-30T12:30:00Z"), store.cursor("samsung"))
    }

    @Test
    fun execucaoGuardaSoCodigos() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(id, RunStatus.FALHA, RunResumo(requisicoes = 3), "HTTP_503", 503, t("2026-10-30T12:01:00Z"))
        val r = store.ultimas("samsung").single()
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("HTTP_503", r.codigoErro)
        assertEquals(503, r.httpStatus)
        assertEquals(3, r.resumo.requisicoes)
        assertEquals(t("2026-10-30T12:01:00Z"), r.finishedAt)
        // a única coluna de texto livre candidata é codigo_erro (mais as de identificação/enumeração)
        val textos = ds.connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_name='sync_run' AND data_type='text'",
            ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toSet() }
        }
        assertEquals(setOf("instance", "kind", "status", "codigo_erro"), textos)
    }

    @Test
    fun falhasSeguidasZeraAposOk() {
        fun rodar(status: RunStatus, n: Int) {
            val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
            store.concluir(id, status, RunResumo(), if (status == RunStatus.FALHA) "TIMEOUT" else null, null, t("2026-10-30T12:00:30Z"))
            assertEquals(n, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        }
        rodar(RunStatus.FALHA, 1)
        rodar(RunStatus.FALHA, 2)
        rodar(RunStatus.OK, 0)
        rodar(RunStatus.FALHA, 1)
        assertEquals(0, store.falhasSeguidas("samsung", SyncKind.RECONCILIACAO))
    }

    @Test
    fun suspensaoPersisteAteLimpar() {
        assertNull(store.suspensa("samsung"))
        store.suspender("samsung", "HTTP_401", t("2026-10-30T12:00:00Z"))
        assertEquals("HTTP_401", store.suspensa("samsung"))
        // outra réplica (outro objeto, outra conexão) enxerga a mesma suspensão
        assertEquals("HTTP_401", PostgresSyncStateStore(ds).suspensa("samsung"))
        assertEquals(1, store.limparSuspensoes())
        assertNull(store.suspensa("samsung"))
        assertEquals(0, store.limparSuspensoes())
    }

    @Test
    fun travaExclusivaEntreDuasConexoes() = runBlocking {
        val dentro = CompletableDeferred<Unit>()
        val liberar = CompletableDeferred<Unit>()
        val primeira = async {
            store.comTrava("samsung") {
                dentro.complete(Unit)
                liberar.await()
                "primeira"
            }
        }
        dentro.await()
        assertNull(store.comTrava("samsung") { "segunda" })
        // outra instância não é afetada
        assertEquals("outra", store.comTrava("main") { "outra" })
        liberar.complete(Unit)
        assertEquals("primeira", primeira.await())
        assertEquals("terceira", store.comTrava("samsung") { "terceira" })
    }

    @Test
    fun orfasViramFalhaInterrompida() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        val ok = store.iniciar("samsung", SyncKind.RECONCILIACAO, t("2026-10-30T12:00:00Z"))
        store.concluir(ok, RunStatus.OK, RunResumo(), null, null, t("2026-10-30T12:00:10Z"))
        assertEquals(1, store.fecharOrfas(t("2026-10-30T13:00:00Z")))
        val r = store.ultimas("samsung").single { it.id == id }
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("INTERROMPIDA", r.codigoErro)
        assertEquals(t("2026-10-30T13:00:00Z"), r.finishedAt)
        assertEquals(0, store.fecharOrfas(t("2026-10-30T13:00:00Z")))
    }

    @Test
    fun chavesDeUsuarioUpsert() {
        store.gravarChavesDeUsuario("samsung", mapOf("JIRAUSER10" to "ana"), t("2026-10-30T12:00:00Z"))
        store.gravarChavesDeUsuario("samsung", mapOf("JIRAUSER10" to "bruno", "JIRAUSER11" to "carla"), t("2026-10-30T13:00:00Z"))
        assertEquals(mapOf("JIRAUSER10" to "bruno", "JIRAUSER11" to "carla"), store.chavesDeUsuario("samsung"))
        assertEquals(emptyMap(), store.chavesDeUsuario("main"))
        val visto = consulta("SELECT visto_em FROM jira_user_key WHERE user_key='JIRAUSER10'") {
            it.getObject(1, java.time.OffsetDateTime::class.java).toInstant()
        }
        assertEquals(t("2026-10-30T13:00:00Z"), visto)
    }

    @Test
    fun reconciliacaoEUltimaPorTipo() {
        assertNull(store.ultimaReconciliacao("samsung"))
        store.marcarReconciliacao("samsung", t("2026-10-30T03:00:00Z"))
        assertEquals(t("2026-10-30T03:00:00Z"), store.ultimaReconciliacao("samsung"))
        // não apaga o cursor nem recria linha
        store.avancarCursor("samsung", t("2026-10-30T12:00:00Z"))
        assertEquals(t("2026-10-30T03:00:00Z"), store.ultimaReconciliacao("samsung"))

        val a = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(a, RunStatus.OK, RunResumo(issues = 2), null, null, t("2026-10-30T12:00:05Z"))
        val b = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:10:00Z"))
        val r = store.iniciar("-", SyncKind.RETENCAO, t("2026-10-30T04:00:00Z"))
        val porTipo = store.ultimaPorTipo("samsung")
        assertEquals(setOf(SyncKind.INCREMENTAL), porTipo.keys)
        assertEquals(b, porTipo.getValue(SyncKind.INCREMENTAL).id)
        // instância nula = retenção, gravada como '-'
        val ret = store.ultimaPorTipo(null)
        assertEquals(r, assertNotNull(ret[SyncKind.RETENCAO]).id)
        assertTrue(store.ultimas("samsung").none { it.kind == SyncKind.RETENCAO })
    }
}
