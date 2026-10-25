package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Robustez do cliente Jira: total ausente, teto de páginas, falhas de rede e corpo que não é JSON. Só MockEngine. */
class JiraClientRobustezTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val token = "token-secreto-123"

    private fun cfg() = JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, token, null)

    private fun retry(max: Int = 3) = RetryPolicy(maxRetries = max, backoff = { 1.seconds }, delay = { })

    private fun cliente(engine: MockEngine, guard: LiveGuard = LiveGuard(true), r: RetryPolicy = retry()) =
        JiraClient(cfg(), HttpClient(engine), guard, r)

    private fun corpoDe(req: HttpRequestData) =
        Json.parseToJsonElement(String(runBlocking { req.body.toByteArray() })).jsonObject

    private val req = SearchRequest("x", listOf("summary"))

    @Test
    fun totalAusenteViraErro() = runBlocking {
        val engine = MockEngine { respond("""{"startAt":0,"issues":[{"key":"TG-1"}],"values":[{"id":"1"}]}""", HttpStatusCode.OK, json) }
        val c = cliente(engine)
        assertFailsWith<JiraException> { c.search(req) }
        assertFailsWith<JiraException> { c.searchAll(req).toList() }
        assertFailsWith<JiraException> { c.count("x") }
        assertFailsWith<JiraException> { c.fullChangelog("TG-1") }
        Unit
    }

    @Test
    fun totalNaoNumericoViraErro() = runBlocking {
        val engine = MockEngine { respond("""{"total":"muitos","issues":[]}""", HttpStatusCode.OK, json) }
        assertFailsWith<JiraException> { cliente(engine).search(req) }
        Unit
    }

    @Test
    fun tetoDePaginas() = runBlocking {
        // Servidor sempre devolve página cheia e um total sempre maior que o já percorrido.
        val engine = MockEngine { r ->
            val corpo = corpoDe(r)
            val inicio = corpo["startAt"]!!.jsonPrimitive.content.toInt()
            val max = corpo["maxResults"]!!.jsonPrimitive.content.toInt()
            val issues = (0 until max).joinToString(",") { """{"key":"TG-${inicio + it + 1}"}""" }
            respond("""{"startAt":$inicio,"maxResults":$max,"total":${inicio + max + 1},"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        assertFailsWith<JiraException> { cliente(engine).searchAll(req, pageSize = 1000).toList() }
        // 10_000 / 1000 = 10 páginas no máximo.
        assertEquals(10, engine.requestHistory.size)
    }

    @Test
    fun tetoDePaginasTemMinimoDeUm() = runBlocking {
        val engine = MockEngine { r ->
            val inicio = corpoDe(r)["startAt"]!!.jsonPrimitive.content.toInt()
            val issues = (0 until 5000).joinToString(",") { """{"key":"TG-${inicio + it + 1}"}""" }
            respond("""{"startAt":$inicio,"maxResults":5000,"total":${inicio + 5001},"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        assertFailsWith<JiraException> { cliente(engine).searchAll(req, pageSize = 5000).toList() }
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun totalExatoNoTetoNaoEhErro() = runBlocking {
        val engine = MockEngine { r ->
            val corpo = corpoDe(r)
            val inicio = corpo["startAt"]!!.jsonPrimitive.content.toInt()
            val max = corpo["maxResults"]!!.jsonPrimitive.content.toInt()
            val fim = minOf(10_000, inicio + max)
            val issues = (inicio until fim).joinToString(",") { """{"key":"TG-${it + 1}"}""" }
            respond("""{"startAt":$inicio,"maxResults":$max,"total":10000,"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        assertEquals(10_000, cliente(engine).searchAll(req, pageSize = 1000).toList().size)
    }

    @Test
    fun fieldsVazioFalhaNaChamadaDeSearchAll() {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, json) }
        // Falha ao chamar searchAll, sem precisar coletar o Flow.
        assertFailsWith<IllegalArgumentException> {
            cliente(engine, LiveGuard(false)).searchAll(SearchRequest("x", emptyList()))
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun falhaDeRedeEhRepetidaEViraJiraException() = runBlocking {
        var n = 0
        val engine = MockEngine {
            n++
            if (n <= 3) throw IOException("conexão caiu para https://jira.exemplo.invalid com $token")
            respond("""{"total":1,"issues":[{"key":"TG-1"}]}""", HttpStatusCode.OK, json)
        }
        assertEquals(1, cliente(engine).search(req).total)
        assertEquals(4, n)
    }

    @Test
    fun falhaDeRedeEsgotadaViraJiraExceptionStatusZeroSemSegredos() = runBlocking {
        var n = 0
        val engine = MockEngine { n++; throw IOException("falha em https://jira.exemplo.invalid/rest com $token") }
        val e = assertFailsWith<JiraException> { cliente(engine).search(req) }
        assertEquals(0, e.status)
        assertEquals(4, n)
        for (texto in listOfNotNull(e.message, e.details)) {
            assertFalse(token in texto)
            assertFalse("jira.exemplo.invalid" in texto)
        }
    }

    @Test
    fun timeoutDoKtorTambemEhRepetido() = runBlocking {
        var n = 0
        val engine = MockEngine { r ->
            n++
            if (n == 1) throw HttpRequestTimeoutException(r.url.toString(), 1000) else respond("""{"total":2,"issues":[]}""", HttpStatusCode.OK, json)
        }
        assertEquals(2, cliente(engine).search(req).total)
        assertEquals(2, n)
    }

    @Test
    fun respostaNaoJsonViraJiraException() = runBlocking {
        val html = "<html><body>Faça login. token=$token\n" + "x".repeat(2000) + "</body></html>"
        val engine = MockEngine { respond(html, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }
        val e = assertFailsWith<JiraException> { cliente(engine).search(req) }
        assertEquals(200, e.status)
        assertTrue(e.details!!.length <= 300)
        assertFalse(e.details!!.contains('\n'))
        assertFalse(token in e.message!! || token in e.details!!)
        Unit
    }

    @Test
    fun respostaJsonQueNaoEhObjetoViraJiraException() = runBlocking {
        val engine = MockEngine { respond("[1,2,3]", HttpStatusCode.OK, json) }
        assertFailsWith<JiraException> { cliente(engine).search(req) }
        val vazio = MockEngine { respond("", HttpStatusCode.OK, json) }
        val e = runCatching { cliente(vazio).count("x") }.exceptionOrNull()
        assertTrue(e is JiraException && e !is SerializationException)
        Unit
    }

    /** Guard de teste que pode ser desligado no meio do retry. */
    private class GuardMutavel : LiveGuard(true) {
        var ligado = true
        override fun check() {
            if (!ligado) throw JiraLiveDisabledException("desligado no meio do retry")
            super.check()
        }
    }

    @Test
    fun guardDesligadoNoMeioDoRetryNaoEnviaMais() = runBlocking {
        val guard = GuardMutavel()
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val r = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { guard.ligado = false })
        assertFailsWith<JiraLiveDisabledException> { cliente(engine, guard, r).search(req) }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun guardDesligadoNoMeioDeRetryDeRedeNaoEnviaMais() = runBlocking {
        val guard = GuardMutavel()
        var n = 0
        val engine = MockEngine { n++; throw IOException("caiu") }
        val r = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { guard.ligado = false })
        assertFailsWith<JiraLiveDisabledException> { cliente(engine, guard, r).search(req) }
        assertEquals(1, n)
    }
}
