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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
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
        assertFailsWith<JiraException> { cliente(engine).searchAll(req, pageSize = 1000, maxItens = 10_000).toList() }
        // O total prometido passa de 10_000 ao pedir a 10ª página (9000 + 1000 + 1): 10 requisições.
        assertEquals(10, engine.requestHistory.size)
    }

    @Test
    fun tetoDePaginasTemMinimoDeUm() = runBlocking {
        val engine = MockEngine { r ->
            val inicio = corpoDe(r)["startAt"]!!.jsonPrimitive.content.toInt()
            val issues = (0 until 5000).joinToString(",") { """{"key":"TG-${inicio + it + 1}"}""" }
            respond("""{"startAt":$inicio,"maxResults":5000,"total":${inicio + 5001},"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        assertFailsWith<JiraException> { cliente(engine).searchAll(req, pageSize = 5000, maxItens = 10_000).toList() }
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
        assertEquals(10_000, cliente(engine).searchAll(req, pageSize = 1000, maxItens = 10_000).toList().size)
    }

    private fun jiraComTotal(total: Int) = MockEngine { r ->
        val corpo = corpoDe(r)
        val inicio = corpo["startAt"]!!.jsonPrimitive.content.toInt()
        val max = corpo["maxResults"]!!.jsonPrimitive.content.toInt()
        val fim = minOf(total, inicio + max)
        val issues = (inicio until fim).joinToString(",") { """{"key":"TG-${it + 1}"}""" }
        respond("""{"startAt":$inicio,"maxResults":$max,"total":$total,"issues":[$issues]}""", HttpStatusCode.OK, json)
    }

    @Test
    fun maxItensEstouraComJiraException() = runBlocking {
        // Padrão (50.000): total de 60.000 estoura, antes de emitir qualquer item.
        val engine = jiraComTotal(60_000)
        val emitidos = mutableListOf<JsonObject>()
        val e = assertFailsWith<JiraException> { cliente(engine).searchAll(req).collect { emitidos += it } }
        assertTrue("50000" in e.message!! && "60000" in e.message!!, e.message)
        assertFalse("x" == e.message, "mensagem não deve ecoar o JQL")
        assertEquals(0, emitidos.size)
        assertEquals(1, engine.requestHistory.size)

        // Com maxItens maior, passa em páginas.
        val engine2 = jiraComTotal(60_000)
        assertEquals(60_000, cliente(engine2).searchAll(req, pageSize = 5000, maxItens = 100_000).toList().size)
        assertEquals(12, engine2.requestHistory.size)
    }

    @Test
    fun maxItensNaoEcoaOJql() = runBlocking {
        val q = SearchRequest("assignee = segredo.da.silva", listOf("key"))
        val e = assertFailsWith<JiraException> { cliente(jiraComTotal(500)).searchAll(q, maxItens = 100).toList() }
        assertFalse("segredo.da.silva" in tudoDe(e))
        assertTrue("100" in e.message!! && "500" in e.message!!)
    }

    @Test
    fun servidorQueMenteSobreOTotalAindaEhLimitadoPorMaxItens() = runBlocking {
        // Total sempre pequeno, mas páginas cheias para sempre: o acumulado estoura o limite.
        val engine = MockEngine { r ->
            val inicio = corpoDe(r)["startAt"]!!.jsonPrimitive.content.toInt()
            val issues = (0 until 10).joinToString(",") { """{"key":"TG-${inicio + it + 1}"}""" }
            respond("""{"startAt":$inicio,"maxResults":10,"total":1000000,"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        assertFailsWith<JiraException> { cliente(engine).searchAll(req, pageSize = 10, maxItens = 50).toList() }
        assertTrue(engine.requestHistory.size <= 6)
    }

    @Test
    fun maxItensInvalidoFalha() {
        val engine = jiraComTotal(10)
        for (invalido in listOf(0, -1, 200_001)) {
            assertFailsWith<IllegalArgumentException>("maxItens=$invalido") {
                cliente(engine).searchAll(req, maxItens = invalido)
            }.also { assertTrue("maxItens" in it.message!!) }
        }
        // Nos limites é aceito (e nada é enviado até coletar o Flow).
        cliente(engine).searchAll(req, maxItens = 1)
        cliente(engine).searchAll(req, maxItens = 200_000)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun tokenEscapadoEmJsonUrlEHtmlEhMascarado() = runBlocking {
        val tk = "ab+cd/ef=gh"
        val config = JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, tk, null)
        val formas = listOf(
            tk,
            "ab+cd\\/ef=gh",
            "ab\\u002Bcd\\u002fef\\u003dgh",
            "ab%2Bcd%2Fef%3Dgh",
            "ab%2bcd%2fef%3dgh",
            "ab&#43;cd&#47;ef&#61;gh",
            "ab&#x2b;cd&#x2f;ef&#x3d;gh",
            "ab&#x2B;cd&#x2F;ef&#x3D;gh",
        )
        for (forma in formas) {
            // Em texto livre (details/mensagem de resumo)...
            val livre = MockEngine { respond("falha com [$forma] no meio", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "text/plain")) }
            val e1 = assertFailsWith<JiraException> { JiraClient(config, HttpClient(livre), LiveGuard(true), retry()).search(req) }
            // ...e dentro de JSON com errorMessages.
            val json2 = MockEngine { respond("""{"errorMessages":["token $forma invalido"]}""", HttpStatusCode.Forbidden, json) }
            val e2 = assertFailsWith<JiraException> { JiraClient(config, HttpClient(json2), LiveGuard(true), retry()).search(req) }
            // ...e em resposta 200 que não é JSON.
            val html = MockEngine { respond("<html>$forma</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }
            val e3 = assertFailsWith<JiraException> { JiraClient(config, HttpClient(html), LiveGuard(true), retry()).search(req) }
            for (e in listOf(e1, e2, e3)) {
                val texto = e.message.orEmpty() + "\n" + e.details.orEmpty()
                assertTrue("***" in texto, "sem máscara para: $forma -> $texto")
                assertFalse(forma in texto, "vazou a forma $forma -> $texto")
                // Nenhum resto do escape do token (ex.: "cd\/ef", "%2Fef", "&#47;ef") pode sobrar.
                assertFalse(Regex("(?i)cd(\\\\/|/|\\\\u002f|%2f|&#47;|&#x2f;)ef").containsMatchIn(texto), "vazou -> $texto")
            }
        }
    }

    @Test
    fun base64DoBasicComBarraEMaisEscapadoEhMascarado() = runBlocking {
        // Usuário e token escolhidos para o base64 conter '+' e '/'.
        val usuario = "u"
        val tk = "???>>>~~~"
        val b64 = java.util.Base64.getEncoder().encodeToString("$usuario:$tk".toByteArray())
        assertTrue('+' in b64 || '/' in b64, "base64 de teste sem + nem /: $b64")
        val config = JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BASIC, tk, usuario)
        val escapado = b64.replace("/", "\\/").replace("+", "\\u002b")
        val engine = MockEngine { respond("""{"errorMessages":["eco $escapado e $b64"]}""", HttpStatusCode.Forbidden, json) }
        val e = assertFailsWith<JiraException> { JiraClient(config, HttpClient(engine), LiveGuard(true), retry()).search(req) }
        val texto = e.message.orEmpty() + e.details.orEmpty()
        assertFalse(b64 in texto || escapado in texto, texto)
        assertTrue("***" in texto)
    }

    @Test
    fun delayDoRetryRespeitaCancelamento() = runBlocking {
        var requisicoes = 0
        val engine = MockEngine { requisicoes++; respond("", HttpStatusCode.ServiceUnavailable) }
        val noDelay = kotlinx.coroutines.CompletableDeferred<Unit>()
        // O delay é suspenso de verdade (cancelável): nunca termina sozinho dentro do teste.
        val r = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { noDelay.complete(Unit); delay(60_000) })
        val trabalho = launch(Dispatchers.Default) { cliente(engine, r = r).search(req) }
        withTimeout(5_000) { noDelay.await() }
        trabalho.cancel()
        withTimeout(5_000) { trabalho.join() }
        assertTrue(trabalho.isCancelled)
        assertEquals(1, requisicoes, "não pode haver nova requisição depois do cancelamento")
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

    @Test
    fun guardDesligadoNoMeioDoRetryNaoEnviaMais() = runBlocking {
        var ligado = true
        val guard = LiveGuard { ligado }
        val engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }
        val r = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { ligado = false })
        assertFailsWith<JiraLiveDisabledException> { cliente(engine, guard, r).search(req) }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun guardDesligadoNoMeioDeRetryDeRedeNaoEnviaMais() = runBlocking {
        var ligado = true
        val guard = LiveGuard { ligado }
        var n = 0
        val engine = MockEngine { n++; throw IOException("caiu") }
        val r = RetryPolicy(maxRetries = 5, backoff = { 1.seconds }, delay = { ligado = false })
        assertFailsWith<JiraLiveDisabledException> { cliente(engine, guard, r).search(req) }
        assertEquals(1, n)
    }

    /** Texto de tudo o que a exceção expõe: mensagem, detalhes, causas, toString e stack trace impresso. */
    private fun tudoDe(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val partes = mutableListOf(e.toString(), e.message.orEmpty(), sw.toString())
        if (e is JiraException) partes += e.details.orEmpty()
        return partes.joinToString("\n")
    }

    @Test
    fun excecaoInesperadaNaoVazaTokenNemUrl() = runBlocking {
        val urlComToken = "https://alvo.invalid/x?token=$token"
        val lancadas = listOf<() -> Throwable>(
            { IllegalArgumentException("Authorization: Bearer $token") },
            { RuntimeException(urlComToken) },
        )
        for (nova in lancadas) {
            var n = 0
            val engine = MockEngine { n++; throw nova() }
            val e = assertFailsWith<JiraException> { cliente(engine).search(req) }
            assertEquals(0, e.status)
            assertEquals(null, e.cause)
            assertEquals(null, e.details)
            assertEquals(1, n, "falha inesperada não deve ser repetida")
            val texto = tudoDe(e)
            assertFalse(token in texto, "vazou o token")
            assertFalse("alvo.invalid" in texto, "vazou a URL")
            assertTrue("Falha inesperada" in e.message!!)
        }
    }

    @Test
    fun cancellationExceptionNaoEhRepetidaNemConvertida() = runBlocking {
        var n = 0
        val engine = MockEngine { n++; throw CancellationException("cancelado") }
        assertFailsWith<CancellationException> { cliente(engine).search(req) }
        assertEquals(1, n)
    }

    @Test
    fun timeoutExternoPropagaTimeoutCancellationException() = runBlocking {
        val engine = MockEngine { delay(10_000); respond("{}", HttpStatusCode.OK, json) }
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) { cliente(engine).search(req) }
        }
        Unit
    }

    @Test
    fun changelogComTotalNegativoViraErro() = runBlocking {
        val engine = MockEngine { respond("""{"total":-1,"values":[{"id":"1"}]}""", HttpStatusCode.OK, json) }
        assertFailsWith<JiraException> { cliente(engine).fullChangelog("TG-1") }
        Unit
    }

    @Test
    fun changelogComTetoDePaginas() = runBlocking {
        // Sempre páginas cheias (100) e total gigante: 10_000 / 100 = 100 páginas no máximo.
        var n = 0
        val engine = MockEngine { r ->
            n++
            val inicio = r.url.parameters["startAt"]!!.toInt()
            val valores = (0 until 100).joinToString(",") { """{"id":"${inicio + it}"}""" }
            respond("""{"total":1000000,"values":[$valores]}""", HttpStatusCode.OK, json)
        }
        assertFailsWith<JiraException> { cliente(engine).fullChangelog("TG-1") }
        assertEquals(100, n)
    }
}
