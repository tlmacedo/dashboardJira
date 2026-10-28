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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** Testes de paginação, contagem e changelog completo: somente MockEngine, nenhuma rede real. */
class JiraClientPagingTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun cfg() = JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null)

    private fun semEspera() = RetryPolicy(maxRetries = 2, backoff = { 1.seconds }, delay = { })

    private fun cliente(engine: MockEngine, live: Boolean = true) =
        JiraClient(cfg(), HttpClient(engine), LiveGuard(live), semEspera())

    private fun corpoDe(req: HttpRequestData) =
        Json.parseToJsonElement(String(runBlocking { req.body.toByteArray() })).jsonObject

    /** Simula um Jira com `total` issues: devolve a fatia pedida por startAt/maxResults. */
    private fun jiraComIssues(total: Int) = MockEngine { req ->
        val corpo = corpoDe(req)
        val inicio = corpo["startAt"]!!.jsonPrimitive.content.toInt()
        val max = corpo["maxResults"]!!.jsonPrimitive.content.toInt()
        val fim = minOf(total, inicio + max)
        val issues = (inicio until fim).joinToString(",") { """{"key":"TG-${it + 1}"}""" }
        respond("""{"startAt":$inicio,"maxResults":$max,"total":$total,"issues":[$issues]}""", HttpStatusCode.OK, json)
    }

    @Test
    fun searchAllPercorreTodasAsPaginas() = runBlocking {
        val engine = jiraComIssues(2500)
        val issues = cliente(engine).searchAll(req("project = TG", listOf("summary")), pageSize = 1000).toList()

        assertEquals(2500, issues.size)
        assertEquals("TG-1", issues.first()["key"]!!.jsonPrimitive.content)
        assertEquals("TG-2500", issues.last()["key"]!!.jsonPrimitive.content)
        assertEquals(listOf(0, 1000, 2000), engine.requestHistory.map { corpoDe(it)["startAt"]!!.jsonPrimitive.content.toInt() })
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun searchAllParaEmPaginaVazia() = runBlocking {
        // O total promete 50, mas a segunda página vem vazia: não pode haver laço infinito.
        var chamadas = 0
        val engine = MockEngine {
            chamadas++
            val issues = if (chamadas == 1) """{"key":"TG-1"},{"key":"TG-2"}""" else ""
            respond("""{"startAt":0,"maxResults":2,"total":50,"issues":[$issues]}""", HttpStatusCode.OK, json)
        }
        val issues = cliente(engine).searchAll(req("x", listOf("summary")), pageSize = 2).toList()
        assertEquals(2, issues.size)
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun pageSizeAcimaDeCincoMilFalha() {
        val engine = jiraComIssues(10)
        assertFailsWith<IllegalArgumentException> {
            cliente(engine).searchAll(req("x", listOf("summary")), pageSize = 5001)
        }
        assertFailsWith<IllegalArgumentException> {
            cliente(engine).searchAll(req("x", listOf("summary")), pageSize = 0)
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun countUsaMaxResultsUm() = runBlocking {
        val engine = jiraComIssues(37)
        assertEquals(37, cliente(engine).count("project = TG"))
        val corpo = corpoDe(engine.requestHistory.single())
        assertEquals(1, corpo["maxResults"]!!.jsonPrimitive.content.toInt())
        assertEquals("project = TG", corpo["jql"]!!.jsonPrimitive.content)
    }

    /** Simula o endpoint de changelog com `total` históricos, 100 por página. */
    private fun jiraComChangelog(total: Int) = MockEngine { req ->
        val inicio = req.url.parameters["startAt"]!!.toInt()
        val max = req.url.parameters["maxResults"]!!.toInt()
        val fim = minOf(total, inicio + max)
        val valores = (inicio until fim).joinToString(",") { """{"id":"${it + 1}"}""" }
        respond("""{"startAt":$inicio,"maxResults":$max,"total":$total,"values":[$valores]}""", HttpStatusCode.OK, json)
    }

    @Test
    fun changelogTruncadoBuscaCompleto() = runBlocking {
        val engine = jiraComChangelog(120)
        val historicos = cliente(engine).fullChangelog("TG-42")

        assertEquals(120, historicos.size)
        assertEquals(2, engine.requestHistory.size)
        val primeira = engine.requestHistory.first()
        assertEquals(HttpMethod.Get, primeira.method)
        assertEquals("/rest/api/2/issue/TG-42/changelog", primeira.url.encodedPath)
        assertEquals("Bearer abc", primeira.headers[HttpHeaders.Authorization])
        assertEquals(listOf("0", "100"), engine.requestHistory.map { it.url.parameters["startAt"] })
    }

    @Test
    fun changelogParaEmPaginaVazia() = runBlocking {
        var chamadas = 0
        val engine = MockEngine {
            chamadas++
            val valores = if (chamadas == 1) """{"id":"1"}""" else ""
            respond("""{"startAt":0,"maxResults":100,"total":500,"values":[$valores]}""", HttpStatusCode.OK, json)
        }
        assertEquals(1, cliente(engine).fullChangelog("TG-1").size)
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun chaveDeIssueInvalidaFalhaSemRequisicao() = runBlocking {
        val engine = jiraComChangelog(1)
        val c = cliente(engine)
        for (chave in listOf("", "tg-1", "TG-", "TG-1/../x", "TG-1?x=1", "TG-1 ", "TG-1\n", "../TG-1", "TG-1#a", "1TG-1")) {
            assertFailsWith<IllegalArgumentException>("chave: $chave") { c.fullChangelog(chave) }
        }
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun erroNoChangelogEMascarado() = runBlocking {
        val engine = MockEngine { respond("""{"errorMessages":["token abc invalido"]}""", HttpStatusCode.Forbidden, json) }
        val e = assertFailsWith<JiraException> { cliente(engine).fullChangelog("TG-1") }
        assertEquals(403, e.status)
        assertEquals(false, "abc" in e.message!!)
    }

    @Test
    fun guardBloqueiaAsTresFuncoesSemRequisicao() = runBlocking {
        val engine = jiraComIssues(10)
        val c = cliente(engine, live = false)
        assertFailsWith<JiraLiveDisabledException> { c.searchAll(req("x", listOf("summary"))).toList() }
        assertFailsWith<JiraLiveDisabledException> { c.count("x") }
        assertFailsWith<JiraLiveDisabledException> { c.fullChangelog("TG-1") }
        assertEquals(0, engine.requestHistory.size)
    }
}
