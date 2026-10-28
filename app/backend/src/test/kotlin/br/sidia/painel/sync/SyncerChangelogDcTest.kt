package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jira.JiraInstanceConfig
import br.sidia.painel.jira.LiveGuard
import br.sidia.painel.jira.OrcamentoDeRequisicoes
import br.sidia.painel.jira.OrcamentoEsgotadoException
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
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/** Changelog truncado versus ausente na sincronização (Data Center). SQLite real; Jira simulado por MockEngine. */
class SyncerChangelogDcTest {
    companion object {
        private val pg = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
        }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val consulta = JqlQuery("samsung", "project = TG", listOf("summary", "status"), true)

    private lateinit var ctx: AppContext
    private lateinit var store: SqliteIssueStore

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), pg)
        store = SqliteIssueStore(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private fun historia(id: Int) =
        """{"id":"$id","created":"2026-10-02T10:00:00.000-0400","author":{"name":"ana"},""" +
            """"items":[{"field":"status","from":"1","to":"3","fromString":"To Do","toString":"In Progress"}]}"""

    /** Issue de busca; [changelog] é o JSON completo do objeto `changelog` (nulo = a chave não vem). */
    private fun issue(chave: String, changelog: String?) =
        """{"key":"$chave","fields":{"summary":"S $chave","project":{"key":"TG"},"issuetype":{"name":"Task"},""" +
            """"status":{"name":"In Progress"},"assignee":{"name":"ana"},"created":"2026-10-01T09:00:00.000-0400",""" +
            """"updated":"2026-10-05T08:00:00.000-0400","labels":[]}""" +
            (changelog?.let { ""","changelog":$it""" } ?: "") + "}"

    private fun busca(vararg issues: String) =
        """{"startAt":0,"maxResults":1000,"total":${issues.size},"issues":[${issues.joinToString(",")}]}"""

    private fun changelogEmbutido(total: Int?, vararg ids: Int) =
        """{"startAt":0,"maxResults":100""" + (total?.let { ""","total":$it""" } ?: "") +
            ""","histories":[${ids.joinToString(",") { historia(it) }}]}"""

    /**
     * Jira simulado. O endpoint paginado só responde para [paginado] (chave -> corpo); fora dele, 404 (Data Center).
     * O GET `issue/{key}` devolve o changelog de [porExpand] (chave -> histórias); chave ausente do mapa dá 404.
     */
    private fun jira(
        corpoBusca: String,
        paginado: Map<String, String> = emptyMap(),
        porExpand: Map<String, List<Int>> = emptyMap(),
    ) = MockEngine { req ->
        val caminho = req.url.encodedPath
        when {
            caminho.endsWith("/rest/api/2/search") -> respond(corpoBusca, HttpStatusCode.OK, json)
            caminho.endsWith("/changelog") -> {
                val corpo = paginado.entries.firstOrNull { caminho.endsWith("/issue/${it.key}/changelog") }?.value
                if (corpo != null) respond(corpo, HttpStatusCode.OK, json)
                else respond("""{"errorMessages":["nao existe"]}""", HttpStatusCode.NotFound, json)
            }
            else -> {
                val chave = caminho.substringAfterLast('/')
                val ids = porExpand[chave]
                if (ids != null) {
                    respond("""{"key":"$chave","changelog":${changelogEmbutido(ids.size, *ids.toIntArray())}}""", HttpStatusCode.OK, json)
                } else {
                    respond("""{"errorMessages":["nao existe"]}""", HttpStatusCode.NotFound, json)
                }
            }
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
    fun truncadoSoQuandoTotalMaiorQueEmbutido() = runBlocking {
        val engine = jira(
            busca(
                issue("TG-1", changelogEmbutido(1, 1)), // total = embutido: completo
                issue("TG-2", changelogEmbutido(3, 2)), // total > embutido: truncado
                issue("TG-3", changelogEmbutido(null, 3)), // sem total: não é truncado
            ),
            paginado = mapOf("TG-2" to """{"total":3,"values":[${historia(21)},${historia(22)},${historia(23)}]}"""),
        )
        val r = Syncer(cliente(engine), store, "samsung").run(consulta)
        assertEquals(3, r.issues)
        assertEquals(1 + 3 + 1, r.events)
        val extras = engine.requestHistory.map { it.url.encodedPath }.filter { !it.endsWith("/search") }
        assertEquals(listOf("/rest/api/2/issue/TG-2/changelog"), extras)
    }

    @Test
    fun issueSemChangelogNaoDerrubaOCiclo() = runBlocking {
        // O servidor ignorou o `expand`: nenhuma issue traz a chave `changelog`. TG-9 nem existe na rota de expand (404).
        val engine = jira(
            busca(issue("TG-1", null), issue("TG-9", null), issue("TG-2", null)),
            porExpand = mapOf("TG-1" to listOf(1, 2), "TG-2" to listOf(3)),
        )
        val r = Syncer(cliente(engine), store, "samsung").run(consulta)
        assertEquals(3, r.issues)
        assertEquals(3, r.events)
        assertEquals(3, contar("issue"))
        // Nunca tenta o endpoint paginado para issue sem changelog: vai direto à rota de expand.
        val extras = engine.requestHistory.filter { !it.url.encodedPath.endsWith("/search") }
        assertEquals(listOf("/rest/api/2/issue/TG-1", "/rest/api/2/issue/TG-9", "/rest/api/2/issue/TG-2"), extras.map { it.url.encodedPath })
        assertEquals(true, extras.all { it.url.parameters["expand"] == "changelog" })
    }

    @Test
    fun orcamentoDeRequisicoesAindaVale() = runBlocking {
        val engine = jira(
            busca(issue("TG-1", null), issue("TG-2", null), issue("TG-3", null)),
            porExpand = mapOf("TG-1" to listOf(1), "TG-2" to listOf(2), "TG-3" to listOf(3)),
        )
        // Busca + 2 issues cabem em 3; a terceira esbarra no teto, que não pode ser engolido como "changelog ausente".
        val orcamento = OrcamentoDeRequisicoes(3)
        assertFailsWith<OrcamentoEsgotadoException> {
            withContext(orcamento) { Syncer(cliente(engine), store, "samsung").run(consulta) }
        }
        assertEquals(3, orcamento.usadas)
        assertEquals(3, engine.requestHistory.size)
    }
}
