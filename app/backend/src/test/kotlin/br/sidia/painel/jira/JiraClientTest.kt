package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Testes do cliente Jira: usam somente MockEngine, nenhuma rede real. */
class JiraClientTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val corpoOk = """{"startAt":0,"maxResults":100,"total":1,"issues":[{"key":"TG-1"}]}"""

    private fun cfg(auth: JiraAuthKind = JiraAuthKind.BEARER, token: String = "abc", user: String? = null) =
        JiraInstanceConfig("a", "https://jira.exemplo.invalid/", auth, token, user)

    private fun semEspera() = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { })

    @Test
    fun guardBloqueiaSemLive() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(false), semEspera())
        assertFailsWith<JiraLiveDisabledException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun enviaPostComCorpoEBearer() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        val page = client.search(SearchRequest("project = TG", listOf("summary", "status"), expandChangelog = true, startAt = 5, maxResults = 50))

        assertEquals(1, page.total)
        assertEquals("TG-1", page.issues.single()["key"]!!.jsonPrimitive.content)
        val req: HttpRequestData = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/rest/api/2/search", req.url.encodedPath)
        assertEquals("jira.exemplo.invalid", req.url.host)
        assertEquals("Bearer abc", req.headers[HttpHeaders.Authorization])
        val corpo = Json.parseToJsonElement(String(req.body.toByteArray())).jsonObject
        assertEquals("project = TG", corpo["jql"]!!.jsonPrimitive.content)
        assertEquals(50, corpo["maxResults"]!!.jsonPrimitive.content.toInt())
        assertEquals(5, corpo["startAt"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf("summary", "status"), corpo["fields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("changelog"), corpo["expand"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun semChangelogNaoEnviaExpand() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        client.search(SearchRequest("x", listOf("summary")))
        val corpo = Json.parseToJsonElement(String(engine.requestHistory.single().body.toByteArray())).jsonObject
        assertFalse("expand" in corpo)
    }

    @Test
    fun usaBasicParaSidia() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        val client = JiraClient(cfg(JiraAuthKind.BASIC, "segredo", "usuario"), HttpClient(engine), LiveGuard(true), semEspera())
        client.search(SearchRequest("x", listOf("summary")))
        val esperado = "Basic " + Base64.getEncoder().encodeToString("usuario:segredo".toByteArray())
        assertEquals(esperado, engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun fieldsVazioFalha() = runBlocking {
        val engine = MockEngine { respond(corpoOk, HttpStatusCode.OK, json) }
        // guard desligado: a validação de fields deve vir antes dele
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(false), semEspera())
        assertFailsWith<IllegalArgumentException> { client.search(SearchRequest(jql = "x", fields = emptyList())) }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun erroPreservaStatus() = runBlocking {
        val engine = MockEngine { respond("""{"errorMessages":["JQL inválido"]}""", HttpStatusCode.BadRequest, json) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        val e = assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(400, e.status)
        assertContains(e.message!!, "JQL inválido")
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun detalhesDoErroSaoResumidosSemQuebraDeLinha() = runBlocking {
        val longo = "linha1\nlinha2\r\n" + "x".repeat(1000)
        val engine = MockEngine { respond(longo, HttpStatusCode.InternalServerError, json) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        val e = assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(500, e.status)
        val d = e.details!!
        assertEquals(true, d.length <= 300)
        assertFalse(d.contains('\n') || d.contains('\r'))
    }

    @Test
    fun retryEm429RespeitaRetryAfter() = runBlocking {
        var n = 0
        val esperas = mutableListOf<Duration>()
        val engine = MockEngine {
            n++
            if (n <= 2) respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "7"))
            else respond(corpoOk, HttpStatusCode.OK, json)
        }
        val retry = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { esperas += it })
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), retry)
        val page = client.search(SearchRequest("x", listOf("summary")))
        assertEquals(1, page.total)
        assertEquals(3, engine.requestHistory.size)
        assertEquals(listOf(7.seconds, 7.seconds), esperas)
    }

    @Test
    fun retryUsaBackoffSemRetryAfterEEsgotaTentativas() = runBlocking {
        val esperas = mutableListOf<Duration>()
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val retry = RetryPolicy(maxRetries = 2, backoff = { (it + 1).seconds }, delay = { esperas += it })
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), retry)
        val e = assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(503, e.status)
        assertEquals(3, engine.requestHistory.size)
        assertEquals(listOf(1.seconds, 2.seconds), esperas)
    }

    @Test
    fun naoRepeteErroDeCliente() = runBlocking {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun retryAfterGigantePassaAoTetoDe120s() = runBlocking {
        var n = 0
        val esperas = mutableListOf<Duration>()
        val engine = MockEngine {
            n++
            if (n == 1) respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "99999999999"))
            else respond(corpoOk, HttpStatusCode.OK, json)
        }
        val retry = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { esperas += it })
        JiraClient(cfg(), HttpClient(engine), LiveGuard(true), retry).search(SearchRequest("x", listOf("summary")))
        assertEquals(listOf(120.seconds), esperas)
    }

    @Test
    fun retryAfterNegativoViraZeroETextoInvalidoUsaBackoff() = runBlocking {
        var n = 0
        val esperas = mutableListOf<Duration>()
        val engine = MockEngine {
            n++
            when (n) {
                1 -> respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "-5"))
                2 -> respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "amanha"))
                else -> respond(corpoOk, HttpStatusCode.OK, json)
            }
        }
        val retry = RetryPolicy(maxRetries = 5, backoff = { 9.seconds }, delay = { esperas += it })
        JiraClient(cfg(), HttpClient(engine), LiveGuard(true), retry).search(SearchRequest("x", listOf("summary")))
        assertEquals(listOf(0.seconds, 9.seconds), esperas)
    }

    @Test
    fun redirecionamentoNaoEhSeguidoNemRepetido() = runBlocking {
        val engine = MockEngine {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://outro.exemplo.invalid/x"))
        }
        val client = JiraClient(cfg(), HttpClient(engine), LiveGuard(true), semEspera())
        val e = assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        assertEquals(302, e.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals("jira.exemplo.invalid", engine.requestHistory.single().url.host)
    }

    @Test
    fun corpoDoErroNaoVazaTokenNemBase64DoBasic() = runBlocking {
        val basic = Base64.getEncoder().encodeToString("usuario:segredo-super".toByteArray())
        val engine = MockEngine {
            respond("falha: token=segredo-super auth=Basic $basic fim", HttpStatusCode.BadRequest, json)
        }
        val client = JiraClient(cfg(JiraAuthKind.BASIC, "segredo-super", "usuario"), HttpClient(engine), LiveGuard(true), semEspera())
        val e = assertFailsWith<JiraException> { client.search(SearchRequest("x", listOf("summary"))) }
        for (texto in listOf(e.details!!, e.message!!)) {
            assertFalse(texto.contains("segredo-super"))
            assertFalse(texto.contains(basic))
        }
        assertContains(e.details!!, "***")
    }

    @Test
    fun tokenNuncaApareceNoToString() {
        val c = cfg(JiraAuthKind.BASIC, "segredo-super", "usuario")
        assertFalse(c.toString().contains("segredo-super"))
    }
}
