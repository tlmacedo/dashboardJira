package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.JiraException
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Carga inicial e incremental contra PostgreSQL real (Testcontainers) e Jira simulado por MockEngine.
 * "Hoje" é 2026-10-30 em Manaus; nenhuma rede real.
 */
class SyncServiceTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC)

    private val corpos = CopyOnWriteArrayList<JsonObject>()
    private val motoresCriados = CopyOnWriteArrayList<String>()
    private var issues: List<String> = emptyList()
    private var porPagina = 100
    private var aoReceber: suspend MockRequestHandleScope.(JsonObject) -> HttpResponseData = { corpo -> paginar(corpo) }

    private var ctx: AppContext? = null
    private lateinit var estado: PostgresSyncStateStore

    @AfterTest
    fun encerrar() {
        ctx?.close()
        ctx = null
    }

    private fun obj(texto: String): JsonObject = Json.parseToJsonElement(texto).jsonObject

    private fun MockRequestHandleScope.paginar(corpo: JsonObject): HttpResponseData {
        val inicio = corpo.getValue("startAt").jsonPrimitive.content.toInt()
        val pagina = issues.drop(inicio).take(porPagina)
        return respond(
            """{"startAt":$inicio,"maxResults":$porPagina,"total":${issues.size},"issues":[${pagina.joinToString(",")}]}""",
            HttpStatusCode.OK,
            json,
        )
    }

    private fun issue(n: Int, updated: String) =
        """{"id":"${900 + n}","key":"TG-$n","fields":{"summary":"s$n","project":{"key":"TG"},"issuetype":{"name":"Task"},
        "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"$updated","labels":[]},
        "changelog":{"total":1,"histories":[{"id":"${5000 + n}","created":"2026-10-20T09:00:00.000-0400",
        "author":{"name":"ana"},"items":[{"field":"status","from":"1","to":"3","fromString":"To Do","toString":"In Progress"}]}]}}"""

    /** `updated` em Manaus (-04:00) a partir de um horário UTC `HH:mm:ss` do dia 30/10. */
    private fun em(hhmmssUtc: String): String {
        val manaus = Instant.parse("2026-10-30T${hhmmssUtc}Z").atOffset(ZoneOffset.ofHours(-4))
        return manaus.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx"))
    }

    private fun settingsLive(sync: SyncSettings = SyncSettings(), live: Boolean = true) = Settings(
        jiraLiveEnabled = live,
        jiraInstances = mapOf(
            "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
        ),
        retentionMonths = 24,
        sync = sync,
    )

    private fun montar(
        sync: SyncSettings = SyncSettings(),
        live: Boolean = true,
        retry: RetryPolicy = RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    ): SyncService {
        val settings = settingsLive(sync, live)
        val fabrica = JiraClientFactory(
            settings,
            {
                val motor = MockEngine { req ->
                    corpos += obj(String(req.body.toByteArray()))
                    aoReceber(this, corpos.last())
                }
                motoresCriados += motor::class.java.simpleName
                HttpClient(motor)
            },
            retry,
        )
        val c = testContextComBanco(settings, pg, clock = relogio, jira = fabrica)
        ctx = c
        estado = PostgresSyncStateStore(requireNotNull(c.dataSource))
        return SyncService(c, PostgresIssueStore(requireNotNull(c.dataSource)), estado, SyncGate(live, settings.sync), relogio)
    }

    private fun contar(tabela: String): Int = requireNotNull(requireNotNull(ctx).dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM $tabela").use { it.next(); it.getInt(1) }
    }

    private fun jql(i: Int) = corpos[i].getValue("jql").jsonPrimitive.content

    private fun executada(r: ResultadoExecucao) = assertIs<ResultadoExecucao.Executada>(r)

    @Test
    fun cargaInicialUsaLimiteDeRetencao() = runBlocking {
        val s = montar()
        issues = emptyList()
        executada(s.incremental("samsung"))
        assertTrue(jql(0).contains("""updated >= "2024-10-30 00:00""""), jql(0))
        assertEquals(Instant.parse("2024-10-30T04:00:00Z"), s.limiteDeRetencao())
    }

    @Test
    fun incrementalUsaCursorMenosSobreposicao() = runBlocking {
        val s = montar()
        estado.avancarCursor("samsung", Instant.parse("2026-10-30T12:05:00Z"))
        executada(s.incremental("samsung"))
        assertTrue(jql(0).contains("""updated >= "2026-10-30 08:00""""), jql(0))
    }

    @Test
    fun cursorAntigoDemaisFicaNoLimiteDeRetencao() = runBlocking {
        val s = montar()
        estado.avancarCursor("samsung", Instant.parse("2024-01-01T00:00:00Z"))
        executada(s.incremental("samsung"))
        assertTrue(jql(0).contains("""updated >= "2024-10-30 00:00""""), jql(0))
    }

    @Test
    fun cursorAvancaParaOMaiorUpdatedGravado() = runBlocking {
        val s = montar()
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")))
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(2, r.resumo.issues)
        assertEquals(2, r.resumo.eventos)
        assertEquals(1, r.resumo.requisicoes)
        assertNull(r.codigoErro)
        assertEquals(Instant.parse("2026-10-30T12:20:00Z"), estado.cursor("samsung"))
        assertEquals(RunStatus.OK, estado.ultimas("samsung").single().status)
    }

    @Test
    fun semIssuesNaoMexeNoCursor() = runBlocking {
        val s = montar()
        val antes = Instant.parse("2026-10-30T12:05:00Z")
        estado.avancarCursor("samsung", antes)
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.issues)
        assertEquals(antes, estado.cursor("samsung"))
    }

    @Test
    fun falhaNoMeioNaoAvancaCursorAlemDoCommit() = runBlocking {
        val s = montar()
        // A 3ª tem `updated` VÁLIDO (a maior de todas), mas o changelog malformado: não pode avançar o cursor.
        val terceira = issue(3, em("12:30:00")).replace("2026-10-20T09:00:00.000-0400", "nao-eh-data")
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")), terceira)
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("DADO_MALFORMADO", r.codigoErro)
        assertEquals(2, r.resumo.issues)
        assertEquals(2, r.resumo.eventos)
        assertEquals(Instant.parse("2026-10-30T12:20:00Z"), estado.cursor("samsung"))
        assertEquals(2, contar("issue"))
        // reexecução com a fixture corrigida: sem eventos duplicados, cursor na 3ª
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")), issue(3, em("12:30:00")))
        val r2 = executada(s.incremental("samsung"))
        assertEquals(RunStatus.OK, r2.status)
        assertEquals(3, contar("issue"))
        assertEquals(3, contar("change_event"))
        assertEquals(Instant.parse("2026-10-30T12:30:00Z"), estado.cursor("samsung"))
    }

    @Test
    fun cursorComSobreposicaoNaoPerdeMesmoMinuto() = runBlocking {
        val s = montar()
        issues = listOf(issue(1, em("12:05:10")))
        executada(s.incremental("samsung"))
        assertEquals(Instant.parse("2026-10-30T12:05:10Z"), estado.cursor("samsung"))
        // B só aparece na execução seguinte, no mesmo minuto de A
        issues = listOf(issue(1, em("12:05:10")), issue(2, em("12:05:40")))
        executada(s.incremental("samsung"))
        assertTrue(jql(1).contains("""updated >= "2026-10-30 08:00""""), jql(1))
        assertEquals(2, contar("issue"))
        assertEquals(Instant.parse("2026-10-30T12:05:40Z"), estado.cursor("samsung"))
    }

    @Test
    fun flagLigadaExecutaSemContador() = runBlocking {
        val s = montar()
        repeat(5) { assertIs<ResultadoExecucao.Executada>(s.incremental("samsung")) }
        assertEquals(5, contar("sync_run"))
    }

    @Test
    fun orcamentoEsgotadoViraFalhaComCodigo() = runBlocking {
        val s = montar(SyncSettings(maxRequisicoes = 1))
        porPagina = 2
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")), issue(3, em("12:30:00")), issue(4, em("12:40:00")))
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("ORCAMENTO_REQUISICOES", r.codigoErro)
        assertEquals(Instant.parse("2026-10-30T12:20:00Z"), estado.cursor("samsung"))
        assertEquals(1, r.resumo.requisicoes)
    }

    private fun suspendeSemNovaTentativa(status: Int, codigo: String, retry: RetryPolicy, requisicoesEsperadas: Int) = runBlocking {
        val s = montar(retry = retry)
        aoReceber = { respond("""{"errorMessages":["x"]}""", HttpStatusCode.fromValue(status), json) }
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals(codigo, r.codigoErro)
        assertEquals(requisicoesEsperadas, corpos.size)
        assertEquals(codigo, estado.suspensa("samsung"))
        // suspensa: nenhuma tentativa nova e nenhuma linha nova
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.SUSPENSA_POR_ERRO), s.incremental("samsung"))
        assertEquals(requisicoesEsperadas, corpos.size)
        assertEquals(1, contar("sync_run"))
        // o reinício do app (limparSuspensoes) libera de novo
        estado.limparSuspensoes()
        aoReceber = { paginar(it) }
        assertEquals(RunStatus.OK, executada(s.incremental("samsung")).status)
    }

    @Test
    fun erro401SuspendeSemNovaTentativa() =
        suspendeSemNovaTentativa(401, "HTTP_401", RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }), 1)

    @Test
    fun erro403SuspendeSemNovaTentativa() =
        suspendeSemNovaTentativa(403, "HTTP_403", RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }), 1)

    @Test
    fun erro429AposRetryEsgotadoSuspende() =
        suspendeSemNovaTentativa(429, "HTTP_429", RetryPolicy(maxRetries = 1, backoff = { 1.seconds }, delay = { }), 2)

    @Test
    fun tresFalhasSeguidasSuspendem() = runBlocking {
        val s = montar()
        aoReceber = { respond("""{"errorMessages":["x"]}""", HttpStatusCode.ServiceUnavailable, json) }
        repeat(3) {
            val r = executada(s.incremental("samsung"))
            assertEquals("HTTP_5XX", r.codigoErro)
        }
        assertEquals("FALHAS_SEGUIDAS", estado.suspensa("samsung"))
        val antes = corpos.size
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.SUSPENSA_POR_ERRO), s.incremental("samsung"))
        assertEquals(antes, corpos.size)
    }

    @Test
    fun duasFalhasNaoSuspendem() = runBlocking {
        val s = montar()
        aoReceber = { respond("""{"errorMessages":["x"]}""", HttpStatusCode.ServiceUnavailable, json) }
        repeat(2) { executada(s.incremental("samsung")) }
        assertNull(estado.suspensa("samsung"))
    }

    @Test
    fun falhaDeRedeViraRede() = runBlocking {
        val s = montar()
        aoReceber = { throw java.io.IOException("sem rede") }
        assertEquals("REDE", executada(s.incremental("samsung")).codigoErro)
    }

    @Test
    fun duasChamadasConcorrentesNaoExecutamJuntas() = runBlocking {
        val s = montar()
        val r = estado.comTrava("samsung") { s.incremental("samsung") }
        assertEquals(ResultadoExecucao.Ocupada, r)
        assertEquals(0, corpos.size)
        assertEquals(0, contar("sync_run"))
    }

    @Test
    fun canceladaRegistraCancelada() = runBlocking {
        val s = montar()
        porPagina = 1
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")))
        val naSegunda = CompletableDeferred<Unit>()
        aoReceber = { corpo ->
            if (corpo.getValue("startAt").jsonPrimitive.content.toInt() >= 1) {
                naSegunda.complete(Unit)
                delay(60_000)
            }
            paginar(corpo)
        }
        val job = launch(Dispatchers.Default) {
            try {
                s.incremental("samsung")
            } catch (e: CancellationException) {
                throw e
            }
        }
        naSegunda.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        val reg = estado.ultimas("samsung").single()
        assertEquals(RunStatus.CANCELADA, reg.status)
        assertEquals("CANCELADA", reg.codigoErro)
        assertEquals(Instant.parse("2026-10-30T12:10:00Z"), estado.cursor("samsung"))
        assertNull(estado.suspensa("samsung"))
        // a trava foi solta: nova execução roda
        aoReceber = { paginar(it) }
        assertIs<ResultadoExecucao.Executada>(s.incremental("samsung"))
    }

    @Test
    fun registroNaoGuardaTextoDeErro() = runBlocking {
        val s = montar()
        aoReceber = { throw JiraException(500, "segredo-xyz", "segredo-xyz") }
        val r = executada(s.incremental("samsung"))
        assertEquals("HTTP_5XX", r.codigoErro)
        val linha = requireNotNull(requireNotNull(ctx).dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT row_to_json(r)::text FROM sync_run r").use { it.next(); it.getString(1) }
        }
        assertFalse(linha.contains("segredo-xyz"), linha)
        assertEquals(500, estado.ultimas("samsung").single().httpStatus)
    }

    @Test
    fun tempoMaximoViraFalha() = runBlocking {
        val s = montar(SyncSettings(tempoMaximo = 1.seconds))
        aoReceber = { corpo ->
            delay(2_000)
            paginar(corpo)
        }
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("TEMPO_ESGOTADO", r.codigoErro)
    }

    @Test
    fun tetoDeItensViraFalhaComCodigo() = runBlocking {
        val s = montar(SyncSettings(maxItens = 1))
        issues = listOf(issue(1, em("12:10:00")), issue(2, em("12:20:00")))
        assertEquals("TETO_ITENS", executada(s.incremental("samsung")).codigoErro)
    }

    @Test
    fun instanciaForaDaListaNaoExecuta() = runBlocking {
        val s = montar(SyncSettings(instancias = listOf("samsung")))
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.INSTANCIA_NAO_CONFIGURADA), s.incremental("sidia"))
        assertEquals(0, corpos.size)
        assertEquals(0, contar("sync_run"))
    }

    @Test
    fun provedorRealNuncaEhChamado() = runBlocking {
        val s = montar()
        s.incremental("samsung")
        assertTrue(motoresCriados.isNotEmpty())
        assertTrue(motoresCriados.all { it == "MockEngine" }, motoresCriados.toString())
        assertEquals(Instant.parse("2024-10-30T04:00:00Z"), s.limiteDeRetencao())
        val unico = AtomicInteger(motoresCriados.size)
        assertEquals(1, unico.get())
    }

    private fun servicoCom(store: SyncStateStore, live: Boolean): SyncService {
        val c = requireNotNull(ctx)
        return SyncService(c, PostgresIssueStore(requireNotNull(c.dataSource)), store, SyncGate(live, c.settings.sync), relogio)
    }

    @Test
    fun flagDesligadaNegaSemTocarNoBanco() = runBlocking {
        montar(live = false)
        val proibido = java.lang.reflect.Proxy.newProxyInstance(
            SyncStateStore::class.java.classLoader, arrayOf(SyncStateStore::class.java),
        ) { _, m, _ -> throw AssertionError("tocou no banco: ${m.name}") } as SyncStateStore
        val s = servicoCom(proibido, live = false)
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG), s.incremental("samsung"))
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG), s.reconciliar("samsung"))
        assertEquals(0, corpos.size)
    }

    /** Suspende a instância depois da prévia do gate e antes da trava (outra réplica suspendeu enquanto esperávamos). */
    private class SuspendeAntesDaTrava(private val real: SyncStateStore) : SyncStateStore by real {
        override suspend fun <T> comTrava(instance: String, bloco: suspend () -> T): T? {
            real.suspender(instance, "HTTP_429", Instant.parse("2026-10-30T14:59:00Z"))
            return real.comTrava(instance, bloco)
        }
    }

    @Test
    fun suspensaoEhReleitaDentroDaTrava() = runBlocking {
        montar()
        val s = servicoCom(SuspendeAntesDaTrava(estado), live = true)
        issues = listOf(issue(1, em("12:10:00")))
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.SUSPENSA_POR_ERRO), s.incremental("samsung"))
        assertEquals(ResultadoExecucao.Negada(MotivoNegacao.SUSPENSA_POR_ERRO), s.reconciliar("samsung"))
        assertEquals(0, corpos.size)
        assertEquals(0, contar("sync_run"))
    }

    /** Faz `concluir` devolver false, como se outro caminho já tivesse fechado a execução. */
    private class ConcluirFalso(private val real: SyncStateStore) : SyncStateStore by real {
        override fun concluir(
            runId: Long, status: RunStatus, resumo: RunResumo, codigoErro: String?, httpStatus: Int?, at: Instant,
        ): Boolean = false
    }

    @Test
    fun concluirFalsoNaoContaSucessoNemSuspende() = runBlocking {
        montar()
        val s = servicoCom(ConcluirFalso(estado), live = true)
        issues = listOf(issue(1, em("12:10:00")))
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("INTERROMPIDA", r.codigoErro)
        // Mesmo com falhas, nada é suspenso quando o desfecho foi descartado.
        aoReceber = { throw JiraException(401, "x", "x") }
        repeat(3) { assertEquals("INTERROMPIDA", executada(s.incremental("samsung")).codigoErro) }
        assertNull(estado.suspensa("samsung"))
    }

    @Test
    fun falhasSemCodigoProprioRegistramSoAClasseDaExcecao() = runBlocking {
        val s = montar()
        val acumulado = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        val logger = org.slf4j.LoggerFactory.getLogger(SyncService::class.java) as ch.qos.logback.classic.Logger
        logger.addAppender(acumulado)
        try {
            issues = listOf(issue(1, em("12:10:00")).replace("2026-10-20T09:00:00.000-0400", "segredo-xyz"))
            val r = executada(s.incremental("samsung"))
            assertEquals("DADO_MALFORMADO", r.codigoErro)
            val mensagens = acumulado.list.map { it.formattedMessage }
            assertTrue(mensagens.any { it.contains("DADO_MALFORMADO") && it.contains("DadoMalformadoException") }, mensagens.toString())
            assertTrue(mensagens.none { it.contains("segredo-xyz") }, mensagens.toString())
        } finally {
            logger.detachAppender(acumulado)
        }
    }

    @Test
    fun resumoParcialDaFalhaPreservaIncompletasEUsuariosNaoResolvidos() = runBlocking {
        val s = montar()
        // 1ª: sem summary (incompleta) e com troca de responsável por chaves internas sem tradução; 2ª: changelog malformado.
        val primeira = """{"id":"901","key":"TG-1","fields":{"project":{"key":"TG"},"issuetype":{"name":"Task"},
            "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"${em("12:10:00")}","labels":[]},
            "changelog":{"total":1,"histories":[{"id":"1","created":"2026-10-20T09:00:00.000-0400","author":{"name":"ana"},
            "items":[{"field":"assignee","from":"JIRAUSER9","to":"JIRAUSER8","fromString":"A","toString":"B"}]}]}}"""
        val segunda = issue(2, em("12:20:00")).replace("2026-10-20T09:00:00.000-0400", "nao-eh-data")
        issues = listOf(primeira, segunda)
        val r = executada(s.incremental("samsung"))
        assertEquals("DADO_MALFORMADO", r.codigoErro)
        assertEquals(1, r.resumo.issues)
        assertEquals(1, r.resumo.incompletas)
        assertEquals(2, r.resumo.usuariosNaoResolvidos)
        val gravado = estado.ultimas("samsung").single().resumo
        assertEquals(1, gravado.incompletas)
        assertEquals(2, gravado.usuariosNaoResolvidos)
    }

    @Test
    fun sobreposicaoConfiguravelEntraNoCorte() = runBlocking {
        val s = montar(SyncSettings(sobreposicao = 65.minutes))
        estado.avancarCursor("samsung", Instant.parse("2026-10-30T12:05:00Z"))
        executada(s.incremental("samsung"))
        assertTrue(jql(0).contains("""updated >= "2026-10-30 07:00""""), jql(0))
    }
}
