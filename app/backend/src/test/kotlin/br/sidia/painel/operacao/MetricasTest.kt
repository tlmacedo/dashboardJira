package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.db.AccessLog
import br.sidia.painel.db.Database
import br.sidia.painel.db.ObservadorDeAccessLog
import br.sidia.painel.db.Retention
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.sync.RunResumo
import br.sidia.painel.sync.RunStatus
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.sync.SyncKind
import br.sidia.painel.testContext
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.TestPaths
import io.ktor.client.request.get
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricasTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    @AfterEach
    fun limparObservador() {
        ObservadorDeAccessLog.aoFalhar = {}
    }

    private val ana = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall) = Principal("ana.silva", setOf(Role.GERENTE))
    }

    /** Linhas de amostra do texto Prometheus que começam com [nome] e contêm todos os [pares] de rótulo. */
    private fun linhas(texto: String, nome: String, vararg pares: String): List<String> =
        texto.lines().filter { l -> l.startsWith(nome) && pares.all { it in l } }

    private fun valor(linha: String): Double = linha.substringAfterLast(' ').toDouble()

    @Test
    fun rotuloDeRotaEhModelo() = testApplication {
        val metricas = Metricas()
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s), metricas = metricas) }
        routing { get("/api/tarefa/{key}") { call.respondText("ok") } }
        client.get("/api/tarefa/TG-1")
        val texto = metricas.texto()
        val l = linhas(texto, "painel_http_requisicoes_total", "rota=\"/api/tarefa/{key}\"", "metodo=\"GET\"", "status=\"200\"")
        assertEquals(1, l.size, texto.lines().filter { it.startsWith("painel_http") }.joinToString("\n"))
        assertEquals(1.0, valor(l.single()))
        assertTrue(linhas(texto, "painel_http_duracao_segundos_count", "rota=\"/api/tarefa/{key}\"").isNotEmpty())
        assertFalse("TG-1" in texto)
    }

    @Test
    fun semDadoPessoalNosRotulos() = testApplication {
        val metricas = Metricas().registrarBindersDaJvm()
        val s = Settings.fromEnv(emptyMap())
        val ctx = br.sidia.painel.testContextComBanco(s, banco)
        application { module(s, ctx, authenticator = ana, metricas = metricas) }
        routing { get("/api/tarefa/{key}") { call.respondText("ok") } }
        client.get("/api/tarefa/TG-1?pessoa=ana.silva&instancia=samsung")
        client.get("/api/health")
        val texto = metricas.texto()
        for (proibido in listOf("ana.silva", "TG-1", "https://", "samsung", Database.arquivoDe(requireNotNull(ctx.dataSource))!!.toString())) {
            assertFalse(proibido in texto, "o texto das métricas não pode conter '$proibido'")
        }
        ctx.close()
    }

    @Test
    fun syncIncrementaContador() {
        val metricas = Metricas()
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).use {
            val estado = EstadoComMetricas(SqliteSyncStateStore(ds), metricas)
            val agora = Instant.parse("2026-10-30T12:00:00Z")
            val id = estado.iniciar("samsung", SyncKind.INCREMENTAL, agora)
            assertTrue(estado.concluir(id, RunStatus.OK, RunResumo(), null, null, agora))
            val retencao = estado.iniciar("-", SyncKind.RETENCAO, agora)
            estado.concluir(retencao, RunStatus.FALHA, RunResumo(), "ERRO_INTERNO", null, agora)
            val texto = metricas.texto()
            val ok = linhas(texto, "painel_sync_execucoes_total", "instancia=\"samsung\"", "tipo=\"INCREMENTAL\"", "status=\"OK\"")
            assertEquals(1.0, valor(ok.single()))
            val local = linhas(texto, "painel_sync_execucoes_total", "instancia=\"local\"", "tipo=\"RETENCAO\"", "status=\"FALHA\"")
            assertEquals(1.0, valor(local.single()))
        }
    }

    @Test
    fun conclusaoRepetidaNaoContaDuasVezes() {
        val metricas = Metricas()
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).use {
            val estado = EstadoComMetricas(SqliteSyncStateStore(ds), metricas)
            val agora = Instant.parse("2026-10-30T12:00:00Z")
            val id = estado.iniciar("samsung", SyncKind.INCREMENTAL, agora)
            estado.concluir(id, RunStatus.OK, RunResumo(), null, null, agora)
            estado.concluir(id, RunStatus.FALHA, RunResumo(), "X", null, agora)
            assertEquals(1, linhas(metricas.texto(), "painel_sync_execucoes_total").size)
        }
    }

    @Test
    fun gaugesDaSincronizacaoLeemOEstado() {
        val metricas = Metricas()
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).use {
            val estado = SqliteSyncStateStore(ds)
            val agora = Instant.parse("2026-10-30T12:00:00Z")
            estado.suspender("samsung", "HTTP_401", agora)
            estado.avancarCursor("samsung", agora.minusSeconds(600))
            val id = estado.iniciar("samsung", SyncKind.INCREMENTAL, agora.minusSeconds(100))
            estado.concluir(id, RunStatus.OK, RunResumo(), null, null, agora.minusSeconds(90))
            metricas.vincularSincronizacao(estado, listOf("samsung", "nao-existe"), Clock.fixed(agora, ZoneId.of("UTC")))
            val texto = metricas.texto()
            assertEquals(1.0, valor(linhas(texto, "painel_sync_suspensa", "instancia=\"samsung\"").single()))
            assertEquals(600.0, valor(linhas(texto, "painel_sync_cursor_idade_segundos", "instancia=\"samsung\"").single()))
            assertEquals(
                agora.minusSeconds(90).epochSecond.toDouble(),
                valor(linhas(texto, "painel_sync_ultima_ok_timestamp_segundos", "instancia=\"samsung\"", "tipo=\"INCREMENTAL\"").single()),
            )
            assertTrue(linhas(texto, "painel_sync_suspensa", "nao-existe").isEmpty(), "instância desconhecida não vira rótulo")
        }
    }

    @Test
    fun falhaDeAccessLogConta() {
        val metricas = Metricas()
        ObservadorDeAccessLog.aoFalhar = { metricas.falhaDeAccessLog() }
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).close() // pool fechado: a gravação falha
        assertFailsWith<br.sidia.painel.db.AccessLogException> {
            AccessLog(ds).record("ana.silva", "/api/x", emptyMap(), Instant.parse("2026-10-30T12:00:00Z"))
        }
        assertEquals(1.0, valor(linhas(metricas.texto(), "painel_access_log_falhas_total").single()))
    }

    @Test
    fun retencaoInformaCategoriasPorTabelaERegistraMetrica() {
        val metricas = Metricas()
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).use {
            val calendario = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), ZoneId.of("America/Manaus"))
            val categorias = mutableListOf<String>()
            Retention(ds, calendario).purge(LocalDate.of(2026, 10, 30), 24, 24) { categoria, n ->
                categorias += categoria
                metricas.retencaoRemoveu(categoria, n)
            }
            assertEquals(
                listOf("change_event", "worklog", "issue_link", "issue", "access_log", "sync_run", "jira_user_key"),
                categorias.take(7),
            )
            metricas.retencaoRemoveu("change_event", 3)
            assertEquals(3.0, valor(linhas(metricas.texto(), "painel_retencao_removidas_total", "categoria=\"change_event\"").single()))
        }
    }

    @Test
    fun alertaAtivoAcompanha() {
        val metricas = Metricas()
        metricas.alertaAtivo("SYNC_SUSPENSA", true)
        assertEquals(1.0, valor(linhas(metricas.texto(), "painel_alerta_ativo", "codigo=\"SYNC_SUSPENSA\"").single()))
        metricas.alertaAtivo("SYNC_SUSPENSA", false)
        assertEquals(0.0, valor(linhas(metricas.texto(), "painel_alerta_ativo", "codigo=\"SYNC_SUSPENSA\"").single()))
    }

    @Test
    fun backupContaEGuardaUltimoOk() {
        val metricas = Metricas()
        val quando = Instant.parse("2026-10-30T06:00:00Z")
        metricas.backup(StatusDeBackup.OK, 4096, quando)
        metricas.backup(StatusDeBackup.FALHA, null, quando.plusSeconds(10))
        val texto = metricas.texto()
        assertEquals(1.0, valor(linhas(texto, "painel_backup_execucoes_total", "status=\"OK\"").single()))
        assertEquals(1.0, valor(linhas(texto, "painel_backup_execucoes_total", "status=\"FALHA\"").single()))
        assertEquals(quando.epochSecond.toDouble(), valor(linhas(texto, "painel_backup_ultimo_ok_timestamp_segundos").single()))
        assertEquals(4096.0, valor(linhas(texto, "painel_backup_tamanho_bytes").single()))
    }

    @Test
    fun discoELivreSemCaminho() {
        val metricas = Metricas()
        metricas.discoLivre("banco", 123_456_789)
        val l = linhas(metricas.texto(), "painel_disco_livre_bytes", "volume=\"banco\"")
        assertEquals(123_456_789.0, valor(l.single()))
        assertFalse('/' in l.single().substringBefore(' '), "o rótulo não pode carregar caminho")
        assertFailsWith<IllegalArgumentException> { metricas.discoLivre("/var/dados", 1) }
    }

    @Test
    fun bindersDaJvmPresentes() {
        val texto = Metricas().registrarBindersDaJvm().texto()
        assertTrue(texto.lines().any { it.startsWith("jvm_memory_used_bytes") })
        assertTrue(texto.lines().any { it.startsWith("jvm_threads_live_threads") })
    }

    @Test
    fun instanciaForaDaListaViraDesconhecida() {
        val metricas = Metricas()
        metricas.execucaoDeSync("qualquer.coisa", SyncKind.INCREMENTAL, RunStatus.OK)
        val texto = metricas.texto()
        assertTrue(linhas(texto, "painel_sync_execucoes_total", "instancia=\"desconhecida\"").isNotEmpty())
        assertFalse("qualquer.coisa" in texto)
    }
}
