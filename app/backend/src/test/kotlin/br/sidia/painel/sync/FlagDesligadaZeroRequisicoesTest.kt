package br.sidia.painel.sync

import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.JiraLiveDisabledException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Prova de que, com a flag `false`, nenhum caminho da sincronização fala com o Jira. */
class FlagDesligadaZeroRequisicoesTest {
    companion object {
        private val pg = org.testcontainers.postgresql.PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    @Test
    fun cargaInicialEIncrementalComFlagFalse() = runBlocking {
        val contador = AtomicInteger(0)
        val provedor = AtomicInteger(0)
        val settings = Settings(
            jiraLiveEnabled = false,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, {
            provedor.incrementAndGet()
            HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        val ctx = br.sidia.painel.testContextComBanco(settings, pg, jira = fabrica)
        ctx.use {
            val ds = requireNotNull(ctx.dataSource)
            val estado = PostgresSyncStateStore(ds)
            val servico = SyncService(ctx, PostgresIssueStore(ds), estado, SyncGate(false, settings.sync), ctx.clock)
            val negada = ResultadoExecucao.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG)
            // carga inicial (sem cursor)
            assertEquals(negada, servico.incremental("samsung"))
            // incremental (com cursor)
            val cursor = java.time.Instant.parse("2026-10-30T12:05:00Z")
            estado.avancarCursor("samsung", cursor)
            assertEquals(negada, servico.incremental("samsung"))
            assertEquals(0, contador.get())
            assertEquals(0, provedor.get())
            assertEquals(cursor, estado.cursor("samsung"))
            val linhas = ds.connection.use { c ->
                c.createStatement().executeQuery("SELECT count(*) FROM sync_run").use { it.next(); it.getInt(1) }
            }
            assertEquals(0, linhas)
        }
    }

    @Test
    fun reconciliacaoComFlagFalse() = runBlocking {
        val contador = AtomicInteger(0)
        val provedor = AtomicInteger(0)
        val settings = Settings(
            jiraLiveEnabled = false,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, {
            provedor.incrementAndGet()
            HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        val ctx = br.sidia.painel.testContextComBanco(settings, pg, jira = fabrica)
        ctx.use {
            val ds = requireNotNull(ctx.dataSource)
            val estado = PostgresSyncStateStore(ds)
            val servico = SyncService(ctx, PostgresIssueStore(ds), estado, SyncGate(false, settings.sync), ctx.clock)
            assertEquals(ResultadoExecucao.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG), servico.reconciliar("samsung"))
            assertEquals(0, contador.get())
            assertEquals(0, provedor.get())
            val (runs, removidas) = ds.connection.use { c ->
                val r = c.createStatement().executeQuery("SELECT count(*) FROM sync_run").use { it.next(); it.getInt(1) }
                val m = c.createStatement().executeQuery("SELECT count(*) FROM issue WHERE removida_em IS NOT NULL")
                    .use { it.next(); it.getInt(1) }
                r to m
            }
            assertEquals(0, runs)
            assertEquals(0, removidas)
            assertEquals(null, estado.ultimaReconciliacao("samsung"))
        }
    }

    @Test
    fun agendadorComFlagFalseUmDiaSimulado() = runBlocking {
        val contador = AtomicInteger(0)
        val provedor = AtomicInteger(0)
        val settings = Settings(
            jiraLiveEnabled = false,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, {
            provedor.incrementAndGet()
            HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        val ctx = br.sidia.painel.testContextComBanco(settings, pg, jira = fabrica)
        ctx.use {
            val ds = requireNotNull(ctx.dataSource)
            // 02:00 em Manaus; 24 h em passos de 1 min cobrem 03:00 (reconciliação) e 04:00 (retenção)
            val relogio = RelogioMutavel(java.time.Instant.parse("2026-10-30T06:00:00Z"))
            val passos = AtomicInteger(0)
            val fim = kotlinx.coroutines.CompletableDeferred<Unit>()
            val agendador = requireNotNull(
                montarAgendador(ctx, relogio, dormir = { d ->
                    relogio.avancar(d)
                    if (passos.incrementAndGet() >= 24 * 60) {
                        fim.complete(Unit)
                        kotlinx.coroutines.awaitCancellation()
                    }
                }),
            )
            agendador.iniciar(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
            kotlinx.coroutines.withTimeout(120_000) { fim.await() }
            agendador.parar()
            assertEquals(listOf(SyncKind.RETENCAO), agendador.tipos)
            assertEquals(0, contador.get())
            assertEquals(0, provedor.get())
            val tipos = ds.connection.use { c ->
                c.createStatement().executeQuery("SELECT kind FROM sync_run").use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
            assertEquals(listOf("RETENCAO"), tipos)
        }
    }

    @Test
    fun nenhumaVariavelSyncLigaASincronizacao() {
        val env = mapOf(
            "SYNC_INSTANCIAS" to "samsung,sidia",
            "SYNC_INTERVALO_MIN" to "15",
            "SYNC_HORA_RECONCILIACAO" to "02:30",
            "SYNC_HORA_RETENCAO" to "05:00",
            "SYNC_MAX_ITENS" to "1000",
            "SYNC_MAX_REQUISICOES" to "100",
            "SYNC_SOBREPOSICAO_MIN" to "10",
            "SYNC_FUSO_SERVIDOR" to "America/Sao_Paulo",
            "SYNC_TEMPO_MAX_MIN" to "30",
            "SYNC_MODE" to "periodico",
            "SYNC_AUTORIZACAO_REF" to "x",
        )
        val s = Settings.fromEnv(env)
        assertEquals(false, s.jiraLiveEnabled)
        val gate = SyncGate(s.jiraLiveEnabled, s.sync)
        assertEquals(SyncMode.DESLIGADO, gate.modo)
        assertEquals(false, gate.podeAgendar())
        for (kind in listOf(SyncKind.INCREMENTAL, SyncKind.RECONCILIACAO)) {
            for (inst in listOf("samsung", "sidia")) {
                assertEquals(
                    GateDecision.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG),
                    gate.avaliar(kind, inst, false),
                )
            }
        }
    }

    @Test
    fun fabricaComFlagFalseNaoFazRequisicao() {
        val contador = AtomicInteger(0)
        val provedorChamado = AtomicInteger(0)
        val settings = Settings(
            jiraLiveEnabled = false,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, {
            provedorChamado.incrementAndGet()
            HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        fabrica.use {
            val cliente = it.create("samsung")
            assertFailsWith<JiraLiveDisabledException> { runBlocking { cliente.count("project = X") } }
        }
        assertEquals(0, contador.get())
        assertEquals(0, provedorChamado.get())
    }
}
