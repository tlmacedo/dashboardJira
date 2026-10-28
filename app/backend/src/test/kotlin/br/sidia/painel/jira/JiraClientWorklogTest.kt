package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** `JiraClient.worklogs`: GET `issue/{key}/worklog` paginado, com guard e retry de todo cliente. Somente MockEngine. */
class JiraClientWorklogTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun cliente(engine: MockEngine, live: Boolean = true) = JiraClient(
        JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null),
        HttpClient(engine), LiveGuard(live), RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    /** Endpoint com [total] registros, [porPagina] por resposta. */
    private fun jiraComWorklogs(total: Int, porPagina: Int) = MockEngine { req ->
        val inicio = req.url.parameters["startAt"]!!.toInt()
        val fim = minOf(total, inicio + porPagina)
        val itens = (inicio until fim).joinToString(",") { """{"id":"${it + 1}","timeSpentSeconds":60}""" }
        respond("""{"startAt":$inicio,"maxResults":$porPagina,"total":$total,"worklogs":[$itens]}""", HttpStatusCode.OK, json)
    }

    @Test
    fun paginaOWorklogDaIssue() = runBlocking {
        val engine = jiraComWorklogs(total = 5, porPagina = 2)
        val itens = cliente(engine).worklogs("TG-42")
        assertEquals(listOf("1", "2", "3", "4", "5"), itens.map { (it["id"] as kotlinx.serialization.json.JsonPrimitive).content })
        val primeira = engine.requestHistory.first()
        assertEquals(HttpMethod.Get, primeira.method)
        assertEquals("/rest/api/2/issue/TG-42/worklog", primeira.url.encodedPath)
        assertEquals("Bearer abc", primeira.headers[HttpHeaders.Authorization])
        assertEquals(listOf("0", "2", "4"), engine.requestHistory.map { it.url.parameters["startAt"] })
    }

    @Test
    fun paraEmPaginaVaziaESemTotalFalha() = runBlocking {
        var chamadas = 0
        val engine = MockEngine {
            chamadas++
            val itens = if (chamadas == 1) """{"id":"1"}""" else ""
            respond("""{"startAt":0,"maxResults":1,"total":500,"worklogs":[$itens]}""", HttpStatusCode.OK, json)
        }
        assertEquals(1, cliente(engine).worklogs("TG-1").size)
        assertEquals(2, engine.requestHistory.size)
        val semTotal = MockEngine { respond("""{"worklogs":[{"id":"1"}]}""", HttpStatusCode.OK, json) }
        assertFailsWith<JiraException> { cliente(semTotal).worklogs("TG-1") }
    }

    @Test
    fun chaveInvalidaFalhaSemRequisicao() = runBlocking {
        val engine = jiraComWorklogs(1, 1)
        for (chave in listOf("", "tg-1", "TG-1/../x", "TG-1?x=1", "../TG-1")) {
            assertFailsWith<IllegalArgumentException>("chave: $chave") { cliente(engine).worklogs(chave) }
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun flagDesligadaNaoFazRequisicao() = runBlocking {
        val engine = jiraComWorklogs(1, 1)
        assertFailsWith<JiraLiveDisabledException> { cliente(engine, live = false).worklogs("TG-1") }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun erroDoServidorNaoVazaTokenNemCorpo() = runBlocking {
        val engine = MockEngine { respond("""{"errorMessages":["negado abc"]}""", HttpStatusCode.Forbidden, json) }
        val e = assertFailsWith<JiraException> { cliente(engine).worklogs("TG-1") }
        assertEquals(403, e.status)
        assertEquals(false, "abc" in (e.message ?: ""))
    }
}
