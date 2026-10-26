package br.sidia.painel.sync

import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.module
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Relógio mutável dos testes de agendamento: o "tempo" só anda quando o teste manda. */
class RelogioMutavel(inicio: Instant, private val zona: ZoneId = ZoneOffset.UTC) : Clock() {
    @Volatile
    var agora: Instant = inicio

    fun avancar(d: Duration) {
        agora = agora.plusMillis(d.inWholeMilliseconds)
    }

    override fun getZone(): ZoneId = zona
    override fun withZone(zone: ZoneId): Clock = RelogioMutavel(agora, zone).also { espelho -> espelho.agora = agora }
    override fun instant(): Instant = agora
}

/** Agenda pura e agendador contra PostgreSQL real; relógio e `dormir` injetados, sem espera de relógio real. */
class AgendadorTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }

        private val MANAUS = ZoneId.of("America/Manaus")
    }

    private fun registro(inicio: String) = RegistroDeExecucao(
        id = 1,
        kind = SyncKind.INCREMENTAL,
        status = RunStatus.OK,
        startedAt = Instant.parse(inicio),
        finishedAt = null,
        resumo = RunResumo(),
        codigoErro = null,
        httpStatus = null,
    )

    private fun settings(live: Boolean) = Settings(
        jiraLiveEnabled = live,
        jiraInstances = mapOf(
            "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
        ),
    )

    private fun estadoNovo(): Pair<br.sidia.painel.AppContext, PostgresSyncStateStore> {
        val ctx = testContextComBanco(settings(false), pg)
        return ctx to PostgresSyncStateStore(requireNotNull(ctx.dataSource))
    }

    private fun tarefa(kind: SyncKind = SyncKind.INCREMENTAL, executar: suspend () -> Unit) =
        TarefaAgendada(kind, if (kind == SyncKind.RETENCAO) null else "samsung", { _, _ -> true }, executar)

    @Test
    fun incrementalDevidaACadaIntervalo() {
        val ultima = registro("2026-10-30T12:00:00Z")
        assertFalse(Agenda.incrementalDevida(Instant.parse("2026-10-30T12:09:59Z"), ultima, 10.minutes))
        assertTrue(Agenda.incrementalDevida(Instant.parse("2026-10-30T12:10:00Z"), ultima, 10.minutes))
        assertTrue(Agenda.incrementalDevida(Instant.parse("2026-10-30T12:00:00Z"), null, 10.minutes))
    }

    @Test
    fun diariaUmaVezPorDiaDeManausDepoisDaHora() {
        val hora = LocalTime.of(3, 0)
        fun devida(agora: String, ultima: RegistroDeExecucao?) =
            Agenda.diariaDevida(Instant.parse(agora), ultima, hora, MANAUS)
        // 03:00 em Manaus = 07:00Z
        assertFalse(devida("2026-10-30T06:59:00Z", null))
        assertTrue(devida("2026-10-30T07:00:00Z", null))
        val executada = registro("2026-10-30T07:01:00Z")
        assertFalse(devida("2026-10-30T12:00:00Z", executada))
        // 03:30Z de 31/10 ainda é 23:30 de 30/10 em Manaus
        assertFalse(devida("2026-10-31T03:30:00Z", executada))
        assertFalse(devida("2026-10-31T06:59:00Z", executada))
        assertTrue(devida("2026-10-31T07:00:00Z", executada))
    }

    @Test
    fun cicloNaoSobrepoe() = runBlocking {
        val (ctx, estado) = estadoNovo()
        ctx.use {
            val execucoes = AtomicInteger(0)
            val presa = CompletableDeferred<Unit>()
            val iniciou = CompletableDeferred<Unit>()
            val ag = Agendador(listOf(tarefa { execucoes.incrementAndGet(); iniciou.complete(Unit); presa.await() }), estado, Clock.systemUTC())
            val primeiro = launch(Dispatchers.Default) { ag.ciclo() }
            withTimeout(10.seconds) { iniciou.await() }
            ag.ciclo() // concorrente: retorna sem executar
            assertEquals(1, execucoes.get())
            presa.complete(Unit)
            primeiro.join()
            assertEquals(1, execucoes.get())
        }
    }

    @Test
    fun umSoJob() = runBlocking {
        val (ctx, estado) = estadoNovo()
        ctx.use {
            val escopo = CoroutineScope(Dispatchers.Default)
            val ag = Agendador(listOf(tarefa { }), estado, Clock.systemUTC(), dormir = { awaitCancellation() })
            ag.iniciar(escopo)
            assertFailsWith<IllegalStateException> { ag.iniciar(escopo) }
            ag.parar()
        }
    }

    @Test
    fun pararCancelaEAguarda() = runBlocking {
        val (ctx, estado) = estadoNovo()
        ctx.use {
            val escopo = CoroutineScope(Dispatchers.Default)
            val emExecucao = CompletableDeferred<Unit>()
            val cancelada = CompletableDeferred<Boolean>()
            val ag = Agendador(
                listOf(
                    tarefa {
                        try {
                            emExecucao.complete(Unit)
                            awaitCancellation()
                        } finally {
                            cancelada.complete(true)
                        }
                    },
                ),
                estado,
                Clock.systemUTC(),
            )
            val job = ag.iniciar(escopo)
            withTimeout(10.seconds) { emExecucao.await() }
            ag.parar()
            // parar() só devolve depois de o job terminar de verdade
            assertTrue(job.isCompleted)
            assertTrue(job.isCancelled)
            assertTrue(cancelada.isCompleted)
        }
    }

    @Test
    fun falhaDeUmaTarefaNaoParaOAgendador() = runBlocking {
        val (ctx, estado) = estadoNovo()
        ctx.use {
            val executadas = AtomicInteger(0)
            val ag = Agendador(
                listOf(
                    tarefa { throw IllegalStateException("segredo-que-nao-deve-vazar") },
                    tarefa { throw java.sql.SQLException("banco fora") },
                    tarefa(SyncKind.RECONCILIACAO) { executadas.incrementAndGet() },
                ),
                estado,
                Clock.systemUTC(),
            )
            ag.ciclo()
            assertEquals(1, executadas.get())
            ag.ciclo()
            assertEquals(2, executadas.get())
        }
    }

    @Test
    fun falhaLogaSoAClasseENaoMensagemENemDerrubaComError() = runBlocking {
        val (ctx, estado) = estadoNovo()
        val acumulado = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        val logger = org.slf4j.LoggerFactory.getLogger(Agendador::class.java) as ch.qos.logback.classic.Logger
        logger.addAppender(acumulado)
        try {
            ctx.use {
                val executadas = AtomicInteger(0)
                val ag = Agendador(
                    listOf(
                        tarefa { throw IllegalStateException("segredo-que-nao-deve-vazar") },
                        // Throwable que não é Exception (ex.: erro de linkagem) também não derruba o laço.
                        tarefa { throw NoSuchMethodError("segredo-de-error") },
                        tarefa(SyncKind.RECONCILIACAO) { executadas.incrementAndGet() },
                    ),
                    estado,
                    Clock.systemUTC(),
                )
                ag.ciclo()
                assertEquals(1, executadas.get())
            }
            val mensagens = acumulado.list.map { it.formattedMessage }
            assertTrue(mensagens.any { it.contains("IllegalStateException") }, mensagens.toString())
            assertTrue(mensagens.any { it.contains("NoSuchMethodError") }, mensagens.toString())
            assertTrue(mensagens.none { it.contains("segredo") }, mensagens.toString())
        } finally {
            logger.detachAppender(acumulado)
        }
    }

    @Test
    fun relogioInjetavel() = runBlocking {
        val (ctx, estado) = estadoNovo()
        ctx.use {
            val relogio = RelogioMutavel(Instant.parse("2026-10-30T12:00:00Z"))
            val execucoes = AtomicInteger(0)
            val voltas = AtomicInteger(0)
            val fim = CompletableDeferred<Unit>()
            val tarefa = TarefaAgendada(
                kind = SyncKind.INCREMENTAL,
                instance = "samsung",
                devida = { agora, ultima -> Agenda.incrementalDevida(agora, ultima, 10.minutes) },
                executar = {
                    execucoes.incrementAndGet()
                    val id = estado.iniciar("samsung", SyncKind.INCREMENTAL, relogio.instant())
                    estado.concluir(id, RunStatus.OK, RunResumo(), null, null, relogio.instant())
                },
            )
            val ag = Agendador(
                listOf(tarefa),
                estado,
                relogio,
                passo = 10.minutes,
                dormir = { d ->
                    relogio.avancar(d)
                    if (voltas.incrementAndGet() >= 3) {
                        fim.complete(Unit)
                        awaitCancellation()
                    }
                },
            )
            ag.iniciar(CoroutineScope(Dispatchers.Default))
            withTimeout(30.seconds) { fim.await() }
            ag.parar()
            assertEquals(3, execucoes.get())
        }
    }

    @Test
    fun gateFechadoSoAgendaRetencao() {
        testContextComBanco(settings(false), pg).use { ctx ->
            assertEquals(listOf(SyncKind.RETENCAO), montarAgendador(ctx)?.tipos)
        }
        testContextComBanco(settings(true), pg).use { ctx ->
            assertEquals(
                listOf(SyncKind.INCREMENTAL, SyncKind.RECONCILIACAO, SyncKind.RETENCAO),
                montarAgendador(ctx)?.tipos,
            )
        }
    }

    @Test
    fun instanciaSemCredencialViraCodigoDeErroENaoDerruba() = runBlocking {
        // SYNC_INSTANCIAS aceita "sidia", mas só "samsung" tem credencial: o agendador registra o código e segue.
        val base = Settings(
            jiraLiveEnabled = true,
            jiraInstances = settings(true).jiraInstances,
            sync = SyncSettings(instancias = listOf("sidia")),
        )
        testContextComBanco(base, pg).use { ctx ->
            val ag = requireNotNull(montarAgendador(ctx))
            ag.ciclo() // não lança
            val estado = PostgresSyncStateStore(requireNotNull(ctx.dataSource))
            val ultimas = estado.ultimas("sidia")
            assertTrue(ultimas.isNotEmpty())
            assertTrue(ultimas.all { it.status == RunStatus.FALHA && it.codigoErro == "INSTANCIA_SEM_CREDENCIAL" })
        }
    }

    @Test
    fun reinicioLimpaSuspensao() = testApplication {
        val s = settings(false)
        val ctx = testContextComBanco(s, pg)
        val estado = PostgresSyncStateStore(requireNotNull(ctx.dataSource))
        estado.suspender("samsung", "HTTP_401", Instant.parse("2026-10-30T12:00:00Z"))
        assertEquals("HTTP_401", estado.suspensa("samsung"))
        application { module(s, ctx) }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        assertNull(estado.suspensa("samsung"))
        ctx.close()
    }

    @Test
    fun inicioFechaOrfasAnterioresAoProcesso() = testApplication {
        val s = settings(false)
        val ctx = testContextComBanco(s, pg)
        val estado = PostgresSyncStateStore(requireNotNull(ctx.dataSource))
        val id = estado.iniciar("samsung", SyncKind.INCREMENTAL, Instant.parse("2020-01-01T00:00:00Z"))
        application { module(s, ctx) }
        client.get("/api/health")
        val registro = estado.ultimas("samsung").single { it.id == id }
        assertEquals(RunStatus.FALHA, registro.status)
        assertEquals("INTERROMPIDA", registro.codigoErro)
        ctx.close()
    }

    @Test
    fun moduloSemAgendadorNaoIniciaNada() = testApplication {
        val s = settings(false)
        application { module(s, br.sidia.painel.testContext(s)) }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
    }

    @Test
    fun moduloIniciaEParaOAgendador() {
        val s = settings(false)
        val ctx = testContextComBanco(s, pg)
        val estado = PostgresSyncStateStore(requireNotNull(ctx.dataSource))
        val rodou = CompletableDeferred<Unit>()
        val ag = Agendador(
            listOf(tarefa { rodou.complete(Unit) }),
            estado,
            Clock.systemUTC(),
            dormir = { awaitCancellation() },
        )
        testApplication {
            application { module(s, ctx, agendador = ag) }
            client.get("/api/health")
            runBlocking { withTimeout(10.seconds) { rodou.await() } }
            assertTrue(ag.emExecucao)
        }
        assertFalse(ag.emExecucao)
        // ApplicationStopping → parar(): reiniciar o mesmo agendador é recusado porque o job já existiu
        assertFailsWith<IllegalStateException> { ag.iniciar(CoroutineScope(Dispatchers.Default)) }
        ctx.close()
    }
}
