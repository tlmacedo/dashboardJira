package br.sidia.painel.jira

import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Testes da fábrica única do cliente Jira: somente MockEngine, hosts `.invalid`, nenhuma rede real. */
class JiraClientFactoryTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val corpoOk = """{"startAt":0,"maxResults":100,"total":1,"issues":[{"key":"TG-1"}]}"""
    private val token = "token-secreto-123"
    private val urlBoa = "https://jira.exemplo.invalid/"

    private fun settings(
        live: Boolean = true,
        id: String = "samsung",
        url: String = urlBoa,
        auth: JiraAuthKind = JiraAuthKind.BEARER,
        usuario: String? = null,
        tokenInstancia: String = token,
    ) = Settings(
        jiraLiveEnabled = live,
        jiraInstances = mapOf(id to JiraInstanceSettings(url, auth, usuario, tokenInstancia)),
    )

    private fun fabrica(s: Settings, engine: MockEngine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }) =
        JiraClientFactory(s, { HttpClient(engine) }, RetryPolicy(maxRetries = 1, backoff = { 1.seconds }, delay = { }))

    private fun falhaDeConfiguracao(s: Settings, id: String = "samsung"): IllegalStateException =
        assertFailsWith { fabrica(s).create(id) }

    @Test
    fun liveDesligadoNaoExigeCredencialENaoEnvia() = runBlocking {
        var criacoesDoHttpReal = 0
        // Sem nenhuma instância configurada e com o provedor de HttpClient proibido.
        val f = JiraClientFactory(Settings(jiraLiveEnabled = false), { criacoesDoHttpReal++; error("não deve criar HttpClient") })
        val c = f.create("samsung")
        assertFailsWith<JiraLiveDisabledException> { c.search(SearchRequest("x", listOf("summary"))) }
        assertFailsWith<JiraLiveDisabledException> { c.searchAll(SearchRequest("x", listOf("summary"))).toList() }
        assertFailsWith<JiraLiveDisabledException> { c.count("x") }
        assertFailsWith<JiraLiveDisabledException> { c.fullChangelog("TG-1") }
        assertEquals(0, criacoesDoHttpReal)
    }

    @Test
    fun liveDesligadoNaoUsaOHttpDoProvedorMesmoComInstanciaCompleta() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        var criacoes = 0
        val f = JiraClientFactory(settings(live = false), { criacoes++; HttpClient(engine) })
        assertFailsWith<JiraLiveDisabledException> { f.create("samsung").count("x") }
        assertEquals(0, criacoes)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun liveLigadoCriaClienteQueEnviaComAutenticacao() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val c = fabrica(settings(), engine).create("samsung")
        assertEquals(1, c.count("x"))
        val req = engine.requestHistory.single()
        assertEquals("jira.exemplo.invalid", req.url.host)
        assertEquals("Bearer $token", req.headers[HttpHeaders.Authorization])
    }

    @Test
    fun liveLigadoComBasicEnviaBasic() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val s = settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = "maria")
        fabrica(s, engine).create("sidia").count("x")
        assertTrue(engine.requestHistory.single().headers[HttpHeaders.Authorization]!!.startsWith("Basic "))
    }

    @Test
    fun liveLigadoExigeHttps() {
        for (url in listOf("http://jira.exemplo.invalid", "ftp://jira.exemplo.invalid", "jira.exemplo.invalid", "//jira.exemplo.invalid")) {
            val e = falhaDeConfiguracao(settings(url = url))
            assertTrue("samsung" in e.message!!, "id ausente para $url")
        }
    }

    @Test
    fun httpSoEmLocalhost() {
        for (url in listOf("http://localhost:8080", "http://127.0.0.1:8080/jira", "http://[::1]:8080", "HTTP://LOCALHOST")) {
            fabrica(settings(url = url)).create("samsung")
        }
        for (url in listOf("http://localhost.exemplo.invalid", "http://127.0.0.2", "http://10.0.0.1", "http://exemplo.invalid")) {
            falhaDeConfiguracao(settings(url = url))
        }
    }

    @Test
    fun tokenVazioFalha() {
        falhaDeConfiguracao(settings(tokenInstancia = ""))
        falhaDeConfiguracao(settings(tokenInstancia = "   "))
    }

    @Test
    fun basicSemUsuarioFalha() {
        falhaDeConfiguracao(settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = null), "sidia")
        falhaDeConfiguracao(settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = ""), "sidia")
        falhaDeConfiguracao(settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = "  "), "sidia")
    }

    @Test
    fun usernameComDoisPontosFalha() {
        falhaDeConfiguracao(settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = "maria:x"), "sidia")
    }

    @Test
    fun urlComCredencialFalha() {
        val e = falhaDeConfiguracao(settings(url = "https://usuario:senha-url@jira.exemplo.invalid/"))
        assertFalse("senha-url" in e.message!!)
        falhaDeConfiguracao(settings(url = "https://usuario@jira.exemplo.invalid/"))
    }

    @Test
    fun urlComFragmentoFalha() {
        falhaDeConfiguracao(settings(url = "https://jira.exemplo.invalid/#frag"))
    }

    @Test
    fun urlComEspacoOuInvalidaFalha() {
        falhaDeConfiguracao(settings(url = "https://jira.exemplo.invalid/ x"))
        falhaDeConfiguracao(settings(url = " https://jira.exemplo.invalid/"))
        falhaDeConfiguracao(settings(url = "https://"))
        falhaDeConfiguracao(settings(url = ""))
    }

    @Test
    fun instanciaDesconhecidaFalha() {
        val e = assertFailsWith<IllegalArgumentException> { fabrica(settings()).create("outra") }
        assertTrue("outra" in e.message!!)
        // Também com o live desligado.
        assertFailsWith<IllegalArgumentException> { fabrica(settings(live = false)).create("outra") }
    }

    @Test
    fun instanciaConhecidaMasNaoConfiguradaFalhaComLiveLigado() {
        val e = assertFailsWith<IllegalStateException> { fabrica(Settings(jiraLiveEnabled = true)).create("sidia") }
        assertTrue("sidia" in e.message!!)
    }

    @Test
    fun mensagensNaoVazamTokenNemUrl() {
        val casos = listOf(
            settings(url = "http://jira.exemplo.invalid/"),
            settings(url = "https://usuario:senha-url@jira.exemplo.invalid/"),
            settings(url = "https://jira.exemplo.invalid/#frag"),
            settings(url = "https://jira.exemplo.invalid/ x"),
            settings(tokenInstancia = " "),
            settings(id = "sidia", auth = JiraAuthKind.BASIC, usuario = "a:b"),
        )
        for (s in casos) {
            val id = s.jiraInstances.keys.single()
            val e = falhaDeConfiguracao(s, id)
            val msg = e.message!!
            assertTrue(id in msg)
            assertFalse("jira.exemplo.invalid" in msg, "vazou a URL: $msg")
            assertFalse(token in msg, "vazou o token: $msg")
            assertFalse("senha-url" in msg, "vazou a senha da URL: $msg")
        }
    }
}
