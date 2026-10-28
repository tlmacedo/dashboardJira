package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Changelog no Data Center: o endpoint paginado `issue/{key}/changelog` pode não existir (é do Cloud);
 * o cliente cai para `issue/{key}?expand=changelog`. Somente MockEngine, nenhuma rede real.
 */
class JiraClientChangelogDcTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun cliente(engine: MockEngine) = JiraClient(
        JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null),
        HttpClient(engine), LiveGuard(true), RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    private val corpo404 = """{"errorMessages":["nao existe"]}"""

    private fun issueComHistories(n: Int) =
        """{"key":"TG-1","changelog":{"startAt":0,"maxResults":100,"total":$n,"histories":[""" +
            (1..n).joinToString(",") { """{"id":"$it"}""" } + "]}}"

    /** Jira de Data Center: o endpoint paginado responde [statusPaginado]; o GET da issue traz `changelog.histories`. */
    private fun jiraDc(statusPaginado: HttpStatusCode = HttpStatusCode.NotFound, n: Int = 3) = MockEngine { req ->
        if (req.url.encodedPath.endsWith("/changelog")) respond(corpo404, statusPaginado, json)
        else respond(issueComHistories(n), HttpStatusCode.OK, json)
    }

    @Test
    fun changelogPaginadoAusenteCaiParaExpand() = runBlocking {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.BadRequest, HttpStatusCode.MethodNotAllowed)) {
            val engine = jiraDc(status)
            val historicos = cliente(engine).fullChangelog("TG-1")
            assertEquals(3, historicos.size, "status $status")
            assertEquals(2, engine.requestHistory.size)
            val fallback = engine.requestHistory.last()
            assertEquals("/rest/api/2/issue/TG-1", fallback.url.encodedPath)
            assertEquals("changelog", fallback.url.parameters["expand"])
            assertEquals("key", fallback.url.parameters["fields"])
        }
    }

    @Test
    fun outrosErrosDoPaginadoNaoCaemParaOFallback() = runBlocking {
        val engine = jiraDc(HttpStatusCode.Forbidden)
        val e = assertFailsWith<JiraException> { cliente(engine).fullChangelog("TG-1") }
        assertEquals(403, e.status)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun changelogPaginadoLembraQueNaoExiste() = runBlocking {
        val engine = jiraDc()
        val c = cliente(engine)
        c.fullChangelog("TG-1")
        c.fullChangelog("TG-2")
        c.fullChangelog("TG-3")
        // 1 tentativa no paginado (404) + 3 chamadas ao GET da issue; as demais não repetem o 404.
        assertEquals(1, engine.requestHistory.count { it.url.encodedPath.endsWith("/changelog") })
        assertEquals(3, engine.requestHistory.count { !it.url.encodedPath.endsWith("/changelog") })
    }

    @Test
    fun aceitaFormatoValuesEHistories() = runBlocking {
        // Paginado no formato Cloud (`values`) e também com `histories` no topo.
        val comValues = MockEngine { respond("""{"total":2,"values":[{"id":"1"},{"id":"2"}]}""", HttpStatusCode.OK, json) }
        assertEquals(2, cliente(comValues).fullChangelog("TG-1").size)
        val comHistories = MockEngine { respond("""{"total":2,"histories":[{"id":"1"},{"id":"2"}]}""", HttpStatusCode.OK, json) }
        assertEquals(2, cliente(comHistories).fullChangelog("TG-1").size)
        // Fallback: `changelog.values` também é lido.
        val fallbackValues = MockEngine { req ->
            if (req.url.encodedPath.endsWith("/changelog")) respond(corpo404, HttpStatusCode.NotFound, json)
            else respond("""{"key":"TG-1","changelog":{"total":1,"values":[{"id":"9"}]}}""", HttpStatusCode.OK, json)
        }
        assertEquals(1, cliente(fallbackValues).fullChangelog("TG-1").size)
        // Fallback com changelog ausente na resposta: lista vazia, sem falha.
        val semChangelog = MockEngine { req ->
            if (req.url.encodedPath.endsWith("/changelog")) respond(corpo404, HttpStatusCode.NotFound, json)
            else respond("""{"key":"TG-1"}""", HttpStatusCode.OK, json)
        }
        assertEquals(0, cliente(semChangelog).fullChangelog("TG-1").size)
    }

    @Test
    fun orcamentoDeRequisicoesAindaVale() = runBlocking {
        // O fallback gasta 2 requisições na primeira issue (404 + expand) e 1 nas seguintes.
        val engine = jiraDc()
        val c = cliente(engine)
        val orcamento = OrcamentoDeRequisicoes(3)
        withContext(orcamento) {
            c.fullChangelog("TG-1")
            c.fullChangelog("TG-2")
            assertFailsWith<OrcamentoEsgotadoException> { c.fullChangelog("TG-3") }
        }
        assertEquals(3, orcamento.usadas)
        assertEquals(3, engine.requestHistory.size)
    }
}
