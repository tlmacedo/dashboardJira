package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.JiraInstanceConfig
import br.sidia.painel.jira.JiraLiveDisabledException
import br.sidia.painel.jira.LiveGuard
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.jql.JqlQuery
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/** Sincronização contra PostgreSQL real (Testcontainers) e Jira simulado por MockEngine; nenhuma rede real. */
class SyncerTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }

        private fun fixture(nome: String) =
            checkNotNull(SyncerTest::class.java.getResource("/fixtures/dc/$nome")) { nome }.readText()
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val consulta = JqlQuery("samsung", "project = TG", listOf("summary", "status"), true)
    private val eventosEsperados = 10 // TG-1: 3, TG-2: 3, TG-3: 1, TG-4 (completo): 3

    private lateinit var ctx: AppContext
    private lateinit var store: PostgresIssueStore

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), pg)
        store = PostgresIssueStore(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    /** Jira simulado: busca devolve a fixture; changelog de TG-4 devolve o histórico completo. */
    private fun jira(): MockEngine = MockEngine { req ->
        val caminho = req.url.encodedPath
        when {
            caminho.endsWith("/rest/api/2/search") -> respond(fixture("search-tg-basic-changelog.json"), HttpStatusCode.OK, json)
            caminho.endsWith("/rest/api/2/issue/TG-4/changelog") -> respond(fixture("changelog-tg-4-completo.json"), HttpStatusCode.OK, json)
            else -> respond("""{"errorMessages":["nao esperado"]}""", HttpStatusCode.NotFound, json)
        }
    }

    private fun cliente(engine: MockEngine) = JiraClient(
        JiraInstanceConfig("samsung", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null),
        HttpClient(engine), LiveGuard(true), RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    private fun contar(tabela: String): Int = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM $tabela").use { it.next(); it.getInt(1) }
    }

    @Test
    fun syncerEhIdempotente() = runBlocking {
        val syncer = Syncer(cliente(jira()), store, "samsung")
        val r1 = syncer.run(consulta)
        val issues = contar("issue")
        val eventos = contar("change_event")
        val r2 = syncer.run(consulta)
        assertEquals(SyncSummary(4, eventosEsperados), r1)
        assertEquals(r1, r2)
        assertEquals(4, issues)
        assertEquals(eventosEsperados, eventos)
        assertEquals(issues, contar("issue"))
        assertEquals(eventos, contar("change_event"))
    }

    @Test
    fun changelogTruncadoBuscaOCompleto() = runBlocking {
        val engine = jira()
        Syncer(cliente(engine), store, "samsung").run(consulta)
        val chamadasChangelog = engine.requestHistory.filter { it.url.encodedPath.endsWith("/issue/TG-4/changelog") }
        assertEquals(1, chamadasChangelog.size)
        assertEquals(0, engine.requestHistory.count { it.url.encodedPath.endsWith("/issue/TG-1/changelog") })
        // o evento que só existe no histórico completo (id 10006) foi gravado
        val extra = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM change_event WHERE changelog_id = 'TG-4:10006:0'")
                .use { it.next(); it.getInt(1) }
        }
        assertEquals(1, extra)
    }

    @Test
    fun liveDesligadoNaoFazRequisicao() = runBlocking {
        var motorAlcancado = 0
        val fabrica = JiraClientFactory(Settings(jiraLiveEnabled = false), { error("HttpClient real não deve ser criado") },
            RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }), { motorAlcancado++ })
        assertFailsWith<JiraLiveDisabledException> { Syncer(fabrica.create("samsung"), store, "samsung").run(consulta) }
        assertEquals(0, motorAlcancado)
        assertEquals(0, contar("issue"))
        fabrica.close()
    }

    @Test
    fun syncerUsaUsernameNormalizado() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        val tg1 = store.timelines("samsung", null, null, false).first { it.key == "TG-1" }
        assertEquals("ana.silva", tg1.currentAssignee)
        assertEquals("ana.silva", tg1.assigneeChanges.single().to)
        assertEquals("ana.silva", tg1.transitions.first().authorRef)
        val gravado = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT assignee_ref, reporter_ref FROM issue WHERE key = 'TG-1'")
                .use { it.next(); it.getString(1) to it.getString(2) }
        }
        assertEquals("ana.silva" to "carla.souza", gravado)
        val evento = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT to_value, author_ref FROM change_event WHERE changelog_id = 'TG-1:10001:1'")
                .use { it.next(); it.getString(1) to it.getString(2) }
        }
        assertEquals("ana.silva" to "ana.silva", evento)
    }

    @Test
    fun timelinesFiltraAbertasEFechadas() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        assertEquals(listOf("TG-3", "TG-4"), store.timelines("samsung", null, null, true).map { it.key })
        val fechadas = store.timelines("samsung", Instant.parse("2026-10-06T00:00:00Z"), Instant.parse("2026-10-07T00:00:00Z"), false)
        assertEquals(listOf("TG-2"), fechadas.map { it.key })
        assertEquals(emptyList(), store.timelines("sidia", null, null, false))
        val tg4 = store.timelines("samsung", null, null, true).first { it.key == "TG-4" }
        assertEquals("To Do", tg4.initialStatus)
        assertEquals("Migrar cadastro de equipes", tg4.summary)
        assertNotNull(tg4.transitions.firstOrNull())
    }
}
