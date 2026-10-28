package br.sidia.painel.jira

import br.sidia.painel.jql.JqlQuery
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

/** A busca só aceita JQL tipado ([JqlQuery]); nenhum teste aqui faz requisição (reflexão e construtores). */
class JiraClientBuscaTipadaTest {
    private val cfg = JiraInstanceConfig("a", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null)

    /** Cliente cujo motor registra toda requisição que chega a ele. */
    private fun clienteQueContaRequisicoes(contador: IntArray) = JiraClient(
        cfg,
        HttpClient(
            MockEngine {
                contador[0]++
                respond(
                    """{"startAt":0,"maxResults":100,"total":0,"issues":[]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
        LiveGuard(true),
        RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    @Test
    fun buscaDeOutraInstanciaPorSearchRequestEhRecusada() = runBlocking {
        val contador = IntArray(1)
        val cliente = clienteQueContaRequisicoes(contador)
        val outra = req(jql = "segredo.da.silva", instancia = "b")

        val busca = assertFailsWith<IllegalArgumentException> { cliente.search(outra) }
        val todas = assertFailsWith<IllegalArgumentException> { cliente.searchAll(outra).toList() }
        for (e in listOf(busca, todas)) {
            val texto = e.message.orEmpty()
            assertTrue("'a'" in texto && "'b'" in texto, "a mensagem deve citar os dois ids: $texto")
            assertFalse("segredo.da.silva" in texto, "a mensagem não pode citar o JQL")
        }
        assertEquals(0, contador[0], "nenhuma requisição pode sair para a instância errada")
        // A instância certa continua funcionando pelo mesmo caminho.
        assertEquals(0, cliente.search(req()).total)
        assertEquals(1, contador[0])
    }

    @Test
    fun buscaSoAceitaJqlQuery() {
        val construtores = SearchRequest::class.java.constructors
        assertTrue(construtores.isNotEmpty(), "SearchRequest sem construtor público")
        for (construtor in construtores) {
            // Nenhum construtor público recebe o JQL como texto avulso.
            val temTextoDeJql = construtor.parameters.any { it.type == String::class.java }
            assertEquals(false, temTextoDeJql, "construtor público de SearchRequest aceita String: ${construtor.parameters.map { it.name }}")
        }
        assertTrue(
            construtores.any { c -> c.parameterTypes.any { it == JqlQuery::class.java } },
            "SearchRequest precisa receber um JqlQuery",
        )
        assertTrue(SearchRequest::class.java.declaredFields.none { it.name == "jql" }, "SearchRequest não guarda o JQL como campo próprio")
    }
}
