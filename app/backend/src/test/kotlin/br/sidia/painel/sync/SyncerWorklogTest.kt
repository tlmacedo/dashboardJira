package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.JiraInstanceConfig
import br.sidia.painel.jira.JiraLiveDisabledException
import br.sidia.painel.jira.LiveGuard
import br.sidia.painel.jira.OrcamentoDeRequisicoes
import br.sidia.painel.jira.OrcamentoEsgotadoException
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.jql.AtualizadoDesde
import br.sidia.painel.jql.CardQueryFactory
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Sincronização do worklog (Plano 6, Tarefa 10): SQLite real em arquivo e Jira simulado por MockEngine em host `.invalid`.
 * Nenhuma requisição real; o comentário do worklog nunca chega ao banco.
 */
class SyncerWorklogTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() = banco.apagar()

        private fun fixture(nome: String) =
            checkNotNull(SyncerWorklogTest::class.java.getResource("/fixtures/dc/$nome")) { nome }.readText()

        const val SEGREDO = "COMENTARIO-SECRETO"
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val consulta = JqlQuery("samsung", "project = TG", listOf("summary", "status", "worklog"), true)
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)

    private lateinit var ctx: AppContext
    private lateinit var store: SqliteIssueStore
    private lateinit var worklogs: SqliteWorklogStore

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), banco)
        store = SqliteIssueStore(requireNotNull(ctx.dataSource))
        worklogs = SqliteWorklogStore(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() = ctx.close()

    /** Jira simulado: a busca devolve [busca]; o endpoint de worklog de TG-301 devolve a lista completa (ou [paginas]). */
    private fun jira(busca: String = fixture("search-com-worklog.json"), paginas: List<String>? = null): MockEngine = MockEngine { req ->
        val caminho = req.url.encodedPath
        when {
            caminho.endsWith("/rest/api/2/search") -> respond(busca, HttpStatusCode.OK, json)
            caminho.endsWith("/rest/api/2/issue/TG-301/worklog") -> {
                val corpo = if (paginas == null) fixture("worklog-tg-301-completo.json")
                else paginas[(req.url.parameters["startAt"] ?: "0").toInt().let { if (it == 0) 0 else 1 }]
                respond(corpo, HttpStatusCode.OK, json)
            }
            else -> respond("""{"errorMessages":["nao esperado"]}""", HttpStatusCode.NotFound, json)
        }
    }

    private fun cliente(engine: MockEngine) = JiraClient(
        JiraInstanceConfig("samsung", "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null),
        HttpClient(engine), LiveGuard(true), RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    private fun syncer(engine: MockEngine, clock: Clock = relogio) = Syncer(cliente(engine), store, "samsung", clock = clock)

    private fun consultar(sql: String): List<List<String?>> = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
        }
    }

    private fun contarWorklog(): Int = consultar("SELECT count(*) FROM worklog").single().single()!!.toInt()

    private fun porIssue(): Map<String, List<WorklogRecord>> =
        worklogs.porIssue("samsung", setOf("TG-301", "TG-302", "TG-303", "TG-304"))

    @Test
    fun worklogSemComentario() = runBlocking {
        syncer(jira()).run(consulta)
        assertTrue(contarWorklog() > 0)
        // Nenhuma coluna de nenhuma tabela guarda o texto do comentário.
        for (tabela in listOf("worklog", "issue", "change_event", "jira_user_key")) {
            val linhas = consultar("SELECT * FROM $tabela").flatten().filterNotNull()
            assertTrue(linhas.none { SEGREDO in it }, "o comentário apareceu em $tabela")
        }
        // As colunas do worklog são só as permitidas (sem coluna de texto livre).
        val colunas = consultar("SELECT name FROM pragma_table_info('worklog')").flatten().toSet()
        assertEquals(setOf("id", "issue_id", "author_ref", "started", "seconds", "instance", "jira_id", "criado_em"), colunas)
    }

    @Test
    fun rawNaoGuardaWorklog() = runBlocking {
        syncer(jira()).run(consulta)
        val raws = consultar("SELECT raw FROM issue").map { it.single()!! }
        assertEquals(4, raws.size)
        assertTrue(raws.none { "\"worklog\"" in it || "timeSpentSeconds" in it }, "o raw guarda o worklog")
        // O timespent (campo da issue) continua no raw: só o worklog sai.
        assertTrue(raws.any { "\"timespent\"" in it })
    }

    @Test
    fun autorJirauserTraduzido() = runBlocking {
        syncer(jira()).run(consulta)
        val tg302 = porIssue().getValue("TG-302")
        // 70010: autor "JIRAUSER10500" -> username pelo objeto do responsável; 70011: nome normalizado.
        assertEquals(listOf("70010" to "ana.silva", "70011" to "bruno.costa"), tg302.map { it.jiraId to it.autor })
        assertEquals(3600L, tg302[0].seconds)
        assertEquals(Instant.parse("2026-10-05T13:00:00Z"), tg302[0].started)
        assertTrue(consultar("SELECT author_ref FROM worklog").flatten().none { it!!.startsWith("JIRAUSER", ignoreCase = true) })
    }

    @Test
    fun worklogIdempotente() = runBlocking {
        val s = syncer(jira())
        s.run(consulta)
        val antes = porIssue()
        val total = contarWorklog()
        s.run(consulta)
        assertEquals(total, contarWorklog())
        assertEquals(antes, porIssue())
        assertEquals(5, total) // 3 de TG-301 (busca truncada + endpoint) e 2 de TG-302
    }

    @Test
    fun worklogTruncadoBuscaCompleto() = runBlocking {
        val engine = jira()
        syncer(engine).run(consulta)
        val chamadas = engine.requestHistory.map { it.url.encodedPath }.filter { it.endsWith("/worklog") }
        // Só a issue truncada (total 3 > 1 recebido) pede o endpoint; as completas e a sem campo, não.
        assertEquals(listOf("/rest/api/2/issue/TG-301/worklog"), chamadas)
        assertEquals(listOf("70001", "70002", "70003"), porIssue().getValue("TG-301").map { it.jiraId })
        assertEquals(listOf("ana.silva", "ana.silva", "bruno.costa"), porIssue().getValue("TG-301").map { it.autor })
    }

    @Test
    fun worklogTruncadoEmDuasPaginas() = runBlocking {
        val pagina1 = """{"startAt":0,"maxResults":2,"total":3,"worklogs":[${worklogJson("70001", "2026-10-02T09:00:00.000-0400", 7200)},${worklogJson("70002", "2026-10-05T09:00:00.000-0400", 10800)}]}"""
        val pagina2 = """{"startAt":2,"maxResults":2,"total":3,"worklogs":[${worklogJson("70003", "2026-10-06T09:00:00.000-0400", 3600)}]}"""
        val engine = jira(paginas = listOf(pagina1, pagina2))
        syncer(engine).run(consulta)
        val urls = engine.requestHistory.filter { it.url.encodedPath.endsWith("/TG-301/worklog") }
        assertEquals(listOf("0", "2"), urls.map { it.url.parameters["startAt"] })
        assertEquals(3, porIssue().getValue("TG-301").size)
    }

    private fun worklogJson(id: String, started: String, segundos: Int) =
        """{"id":"$id","author":{"name":"Ana.Silva"},"started":"$started","timeSpentSeconds":$segundos,"comment":"$SEGREDO"}"""

    @Test
    fun worklogRemovidoNoJiraSaiNaRessincronizacao() = runBlocking {
        syncer(jira()).run(consulta)
        assertEquals(2, porIssue().getValue("TG-302").size)
        // Na segunda busca o worklog 70011 sumiu do Jira (e TG-301 ganhou um novo, já completo na busca).
        val sem70011 = fixture("search-com-worklog.json")
            .replace(Regex(""",\s*\{\s*"self": "https://jira.exemplo.invalid/rest/api/2/issue/10302/worklog/70011".*?"timeSpentSeconds": 7200\s*\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace("\"total\": 2,", "\"total\": 1,")
        assertTrue("70011" !in sem70011, "o ajuste da fixture não removeu o 70011")
        syncer(jira(busca = sem70011)).run(consulta)
        assertEquals(listOf("70010"), porIssue().getValue("TG-302").map { it.jiraId })
    }

    @Test
    fun campoAusenteNaoMexeEListaVaziaLimpa() = runBlocking {
        syncer(jira()).run(consulta)
        // Gravados à mão para TG-303 (a busca não traz o campo) e TG-304 (a busca traz o worklog vazio).
        requireNotNull(ctx.dataSource).connection.use { c ->
            for ((chave, jiraId) in listOf("TG-303" to "90001", "TG-304" to "90002")) {
                c.prepareStatement(
                    "INSERT INTO worklog (issue_id, author_ref, started, seconds, instance, jira_id) " +
                        "VALUES ((SELECT id FROM issue WHERE key = ?), 'bruno.costa', 1760000000000, 600, 'samsung', ?)",
                ).use { ps ->
                    ps.setString(1, chave)
                    ps.setString(2, jiraId)
                    ps.executeUpdate()
                }
            }
        }
        syncer(jira()).run(consulta)
        val depois = porIssue()
        assertEquals(listOf("90001"), depois.getValue("TG-303").map { it.jiraId }, "campo ausente não pode apagar")
        assertEquals(null, depois["TG-304"], "worklog vazio na busca limpa os registros da issue")
    }

    @Test
    fun endpointDeWorklogAusenteNaoDerrubaOCicloNemApagaOGravado() = runBlocking {
        syncer(jira()).run(consulta)
        assertEquals(3, porIssue().getValue("TG-301").size)
        // Servidor sem o endpoint (404): o ciclo segue e a lista parcial da busca (1 de 3) NÃO substitui a gravada.
        val semEndpoint = MockEngine { req ->
            if (req.url.encodedPath.endsWith("/rest/api/2/search")) respond(fixture("search-com-worklog.json"), HttpStatusCode.OK, json)
            else respond("""{"errorMessages":["nao existe"]}""", HttpStatusCode.NotFound, json)
        }
        val r = syncer(semEndpoint).run(consulta)
        assertEquals(4, r.issues)
        assertEquals(3, porIssue().getValue("TG-301").size)
    }

    @Test
    fun worklogAnteriorAoCorteDeRetencaoNaoEntra() = runBlocking {
        // Relógio em 2029: o corte de 24 meses é 2027-01-01 e todo worklog de 2026 já estaria expurgado.
        syncer(jira(), Clock.fixed(Instant.parse("2029-01-01T12:00:00Z"), ZoneOffset.UTC)).run(consulta)
        assertEquals(0, contarWorklog())
    }

    @Test
    fun liveDesligadoNaoBuscaWorklog() = runBlocking {
        var motorAlcancado = 0
        val fabrica = JiraClientFactory(
            Settings(jiraLiveEnabled = false), { error("HttpClient real não deve ser criado") },
            RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }), { motorAlcancado++ },
        )
        assertFailsWith<JiraLiveDisabledException> { fabrica.create("samsung").worklogs("TG-301") }
        assertFailsWith<JiraLiveDisabledException> { Syncer(fabrica.create("samsung"), store, "samsung").run(consulta) }
        assertEquals(0, motorAlcancado)
        assertEquals(0, contarWorklog())
        fabrica.close()
    }

    @Test
    fun orcamentoContaRequisicaoDeWorklog() = runBlocking {
        // A busca gasta 1 e o worklog truncado de TG-301 gasta mais 1: com orçamento 1 o segundo esbarra no teto.
        assertFailsWith<OrcamentoEsgotadoException> {
            withContext(OrcamentoDeRequisicoes(1)) { syncer(jira()).run(consulta) }
        }
        val engine = jira()
        withContext(OrcamentoDeRequisicoes(2)) { syncer(engine).run(consulta) }
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun fichaDeSincronizacaoPedeWorklogETimespent() {
        val query = CardQueryFactory(ctx.campos).build(requireNotNull(ctx.fichas["sincronizacao"]), mapOf("desde" to AtualizadoDesdeDeTeste.valor))
        assertTrue("worklog" in query.fields, query.fields.toString())
        assertTrue("timespent" in query.fields, query.fields.toString())
    }
}

/** O parâmetro `desde` da ficha de sincronização, para montar a consulta sem o serviço. */
private object AtualizadoDesdeDeTeste {
    val valor = AtualizadoDesde(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC)
}
