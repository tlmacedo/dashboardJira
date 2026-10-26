package br.sidia.painel.jira

import br.sidia.painel.AppContext
import br.sidia.painel.TestPaths
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jql.JqlQuery
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.Proxy
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Cache e fechamento da fábrica, e ordem de fechamento do AppContext. Só MockEngine, hosts `.invalid`. */
class JiraClientFactoryCacheTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val corpoOk = """{"startAt":0,"maxResults":100,"total":1,"issues":[{"key":"TG-1"}]}"""

    private fun settings(live: Boolean = true) = Settings(
        jiraLiveEnabled = live,
        jiraInstances = mapOf(
            "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid/", JiraAuthKind.BEARER, null, "tk-um-1234"),
            "sidia" to JiraInstanceSettings("https://jira2.exemplo.invalid/", JiraAuthKind.BEARER, null, "tk-dois-123"),
        ),
    )

    private val retry = RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { })

    /** Fábrica com provedor que guarda os HttpClient criados, para os testes verem o fechamento. */
    private class Registro {
        val criados = mutableListOf<HttpClient>()
        var fechamentos = 0
    }

    private fun fabrica(live: Boolean = true, reg: Registro = Registro(), engine: MockEngine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }) =
        JiraClientFactory(
            settings(live),
            { HttpClient(engine).also { reg.criados += it } },
            retry,
            aoFecharCliente = { reg.fechamentos++ },
        )

    private fun aberto(c: HttpClient) = c.coroutineContext[Job]!!.isActive

    @Test
    fun createDevolveAMesmaInstancia() {
        val f = fabrica()
        val a = f.create("samsung")
        assertSame(a, f.create("samsung"))
        assertNotSame(a, f.create("sidia"))
        assertSame(f.create("sidia"), f.create("sidia"))
    }

    @Test
    fun createComLiveDesligadoTambemEhUmSoPorId() {
        var criacoesDoHttpReal = 0
        val f = JiraClientFactory(Settings(jiraLiveEnabled = false), { criacoesDoHttpReal++; error("não deve criar") })
        assertSame(f.create("samsung"), f.create("samsung"))
        assertNotSame(f.create("samsung"), f.create("sidia"))
        assertEquals(0, criacoesDoHttpReal)
    }

    @Test
    fun createEhThreadSafe() {
        val reg = Registro()
        val f = fabrica(reg = reg)
        val achados = java.util.concurrent.ConcurrentHashMap.newKeySet<JiraClient>()
        val threads = (1..16).map { Thread { achados += f.create("samsung") } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, achados.size)
        assertEquals(1, reg.criados.size)
    }

    @Test
    fun closeFechaOsHttpClients() {
        val reg = Registro()
        val f = fabrica(reg = reg)
        f.create("samsung")
        f.create("sidia")
        assertEquals(2, reg.criados.size)
        assertTrue(reg.criados.all { aberto(it) })
        f.close()
        assertTrue(reg.criados.none { aberto(it) }, "algum HttpClient continuou aberto")
        assertEquals(2, reg.fechamentos)
    }

    @Test
    fun closeEhIdempotente() {
        val reg = Registro()
        val f = fabrica(reg = reg)
        f.create("samsung")
        f.close()
        f.close()
        assertEquals(1, reg.fechamentos)
        assertFailsWith<IllegalStateException> { f.create("samsung") }
    }

    @Test
    fun closeSemClientesNaoFazNada() {
        fabrica().close()
    }

    @Test
    fun closeComLiveDesligadoFechaOMotorDesligado() {
        val reg = Registro()
        val f = JiraClientFactory(settings(live = false), { error("não deve criar") }, retry, aoFecharCliente = { reg.fechamentos++ })
        f.create("samsung")
        f.close()
        assertEquals(1, reg.fechamentos)
    }

    @Test
    fun closeFechaOsDemaisEPropagaOPrimeiroErro() {
        val reg = Registro()
        // A falha é simulada no gancho de fechamento, chamado logo após fechar cada cliente.
        val f = JiraClientFactory(
            settings(),
            { HttpClient(MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }).also { reg.criados += it } },
            retry,
            aoFecharCliente = { reg.fechamentos++; error("falha ao fechar #${reg.fechamentos}") },
        )
        f.create("samsung")
        f.create("sidia")
        val e = assertFailsWith<IllegalStateException> { f.close() }
        assertEquals("falha ao fechar #1", e.message)
        assertEquals(1, e.suppressed.size)
        // Os dois foram fechados apesar da falha no primeiro.
        assertEquals(2, reg.fechamentos)
        assertTrue(reg.criados.none { aberto(it) })
        // Idempotente mesmo depois de uma falha.
        f.close()
        assertEquals(2, reg.fechamentos)
    }

    private fun fakePool(ordem: MutableList<String>): DataSource =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(DataSource::class.java, AutoCloseable::class.java),
        ) { _, m, _ ->
            if (m.name == "close") ordem += "pool"
            null
        } as DataSource

    private fun contexto(jira: JiraClientFactory, pool: DataSource?) = AppContext(
        settings = settings(),
        calendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings().zone),
        roles = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
        dataSource = pool,
        jira = jira,
        statusMap = br.sidia.painel.domain.StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml")),
        campos = br.sidia.painel.jql.FieldDictionary(emptyList()),
        fichas = emptyMap(),
    )

    @Test
    fun appContextCloseFechaClientesAntesDoPool() {
        val ordem = mutableListOf<String>()
        val f = JiraClientFactory(
            settings(),
            { HttpClient(MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }) },
            retry,
            aoFecharCliente = { ordem += "jira" },
        )
        f.create("samsung")
        contexto(f, fakePool(ordem)).close()
        assertEquals(listOf("jira", "pool"), ordem)
    }

    @Test
    fun appContextCloseFechaOPoolMesmoSeOJiraFalhar() {
        val ordem = mutableListOf<String>()
        val f = JiraClientFactory(
            settings(),
            { HttpClient(MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }) },
            retry,
            aoFecharCliente = { ordem += "jira"; error("falha ao fechar") },
        )
        f.create("samsung")
        assertFailsWith<IllegalStateException> { contexto(f, fakePool(ordem)).close() }
        assertEquals(listOf("jira", "pool"), ordem)
    }

    @Test
    fun searchAllComJqlQueryUsaFieldsEExpand() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val c = fabrica(engine = engine).create("samsung")
        val q = JqlQuery("samsung", "project = TG", listOf("summary", "status"), expandChangelog = true)
        val issues = mutableListOf<kotlinx.serialization.json.JsonObject>()
        c.searchAll(q).collect { issues += it }
        assertEquals(1, issues.size)
        val corpo = Json.parseToJsonElement(String(engine.requestHistory.single().body.toByteArray())).jsonObject
        assertEquals("project = TG", corpo["jql"]!!.jsonPrimitive.content)
        assertEquals(listOf("summary", "status"), corpo["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("changelog"), corpo["expand"]!!.jsonArray.map { it.jsonPrimitive.content })

        // Sem changelog: o corpo não leva `expand`.
        val engine2 = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        fabrica(engine = engine2).create("samsung").search(JqlQuery("samsung", "x", listOf("key"), false))
        val corpo2 = Json.parseToJsonElement(String(engine2.requestHistory.single().body.toByteArray())).jsonObject
        assertFalse("expand" in corpo2)
    }

    @Test
    fun countComJqlQueryPedeSoUmResultado() = runBlocking {
        val engine = MockEngine { respond("""{"startAt":0,"maxResults":1,"total":42,"issues":[]}""", HttpStatusCode.OK, json) }
        val c = fabrica(engine = engine).create("samsung")
        assertEquals(42, c.count(JqlQuery("samsung", "project = TG", listOf("summary"), false)))
        val corpo = Json.parseToJsonElement(String(engine.requestHistory.single().body.toByteArray())).jsonObject
        assertEquals("1", corpo["maxResults"]!!.jsonPrimitive.content)
    }

    @Test
    fun instanciaDaQueryDiferenteDoClienteFalhaSemEcoarOJql() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val c = fabrica(engine = engine).create("samsung")
        val q = JqlQuery("sidia", "assignee = segredo.da.silva", listOf("key"), false)
        val e1 = assertFailsWith<IllegalArgumentException> { c.search(q) }
        val e2 = assertFailsWith<IllegalArgumentException> { c.searchAll(q) }
        val e3 = assertFailsWith<IllegalArgumentException> { c.count(q) }
        for (e in listOf(e1, e2, e3)) assertFalse("segredo.da.silva" in e.message.orEmpty())
        assertEquals(0, engine.requestHistory.size)
    }
}
