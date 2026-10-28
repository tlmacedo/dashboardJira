package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.module
import br.sidia.painel.testContext
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProntidaoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private class RelogioMutavel(var agora: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = agora
    }

    private val relogio = RelogioMutavel(Instant.parse("2026-10-30T12:00:00Z"))

    private fun sonda(arquivo: Path) = Database.urlDeArquivo(arquivo) + "?busy_timeout=1500&open_mode=1"

    /** Banco novo e migrado; devolve o arquivo e o pool (fechável). */
    private fun bancoNovo(): Pair<Path, javax.sql.DataSource> {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo), poolSize = 4)
        Database.migrate(ds)
        return arquivo to ds
    }

    private fun prontidao(
        arquivo: Path,
        pendentes: () -> Int = { 0 },
        trava: () -> Boolean = { true },
        discoMinBytes: Long = 1,
    ) = Prontidao(arquivo, sonda(arquivo), pendentes, trava, discoMinBytes, relogio)

    @Test
    fun prontoComBancoMigracoesDiscoETrava() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            val r = prontidao(arquivo).avaliar()
            assertEquals(ProntidaoDto(pronto = true), r)
        }
    }

    @Test
    fun prontoDetectaBancoIndisponivelEmAte3s() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            for (s in listOf("", "-wal", "-shm")) Files.deleteIfExists(Path.of(arquivo.toString() + s))
            val t0 = System.nanoTime()
            val r = prontidao(arquivo).avaliar()
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertEquals(ProntidaoDto(false, "BANCO_INDISPONIVEL"), r)
            assertTrue(ms < 3000, "demorou ${ms}ms")
        }
    }

    @Test
    fun bancoTrocadoPorPastaEIndisponivel() = runBlocking {
        val arquivo = banco.novoArquivo()
        Files.createDirectory(arquivo)
        assertEquals("BANCO_INDISPONIVEL", prontidao(arquivo).avaliar().codigo)
    }

    @Test
    fun prontoNaoEsperaOPoolEsgotado() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            val seguradas = List(4) { ds.connection }
            try {
                val t0 = System.nanoTime()
                val r = prontidao(arquivo).avaliar()
                val ms = (System.nanoTime() - t0) / 1_000_000
                assertEquals(ProntidaoDto(pronto = true), r)
                assertTrue(ms < 3000, "demorou ${ms}ms")
            } finally {
                seguradas.forEach { it.close() }
            }
        }
    }

    @Test
    fun migracaoPendente503() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            assertEquals("MIGRACAO_PENDENTE", prontidao(arquivo, pendentes = { 1 }).avaliar().codigo)
        }
    }

    @Test
    fun discoBaixo503() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            assertEquals("DISCO_BAIXO", prontidao(arquivo, discoMinBytes = Long.MAX_VALUE).avaliar().codigo)
        }
    }

    @Test
    fun travaPerdida503() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            assertEquals("TRAVA_PERDIDA", prontidao(arquivo, trava = { false }).avaliar().codigo)
        }
    }

    @Test
    fun resultadoGuardadoPor5s() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            var chamadas = 0
            val p = prontidao(arquivo, pendentes = { chamadas++; 0 })
            p.avaliar()
            relogio.agora = relogio.agora.plusSeconds(4)
            p.avaliar()
            assertEquals(1, chamadas, "dentro de 5 s não reconsulta")
            relogio.agora = relogio.agora.plus(Duration.ofSeconds(2))
            p.avaliar()
            assertEquals(2, chamadas, "depois de 5 s reconsulta")
        }
    }

    @Test
    fun verificacaoDeDiscoLivreEPositivoEAceitaPastaInexistente() {
        val pasta = Files.createTempDirectory("painel-disco-")
        try {
            assertTrue(VerificacaoDeDisco.livreBytes(pasta) > 0)
            assertTrue(VerificacaoDeDisco.livreBytes(pasta.resolve("nao/existe/ainda")) > 0)
        } finally {
            Files.deleteIfExists(pasta)
        }
    }

    // ---- rota HTTP ----

    @Test
    fun respostaSemDetalhesEProntoEhPublico() = testApplication {
        // Produção (sem modo dev): a rota continua pública, sem principal.
        val s = Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "false"))
        val ctx = testContextComBanco(s, banco)
        application { module(s, ctx, authenticator = object : br.sidia.painel.security.Authenticator {
            override suspend fun authenticate(call: io.ktor.server.application.ApplicationCall) = null
        }) }
        val r = client.get("/api/health/pronto")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"pronto":true}""", r.bodyAsText())
        ctx.close()
    }

    @Test
    fun semBancoResponde503ComCodigoSemDetalhes() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        val r = client.get("/api/health/pronto")
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertEquals("""{"pronto":false,"codigo":"BANCO_INDISPONIVEL"}""", r.bodyAsText())
    }

    @Test
    fun healthContinua200ComBancoIndisponivel() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        val ctx = testContextComBanco(s, banco)
        application { module(s, ctx) }
        val arquivo = Database.arquivoDe(requireNotNull(ctx.dataSource))!!
        // O banco some com o processo vivo: a prontidão cai, a vivacidade não.
        for (suf in listOf("", "-wal", "-shm")) Files.deleteIfExists(Path.of(arquivo.toString() + suf))
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        val pronto = client.get("/api/health/pronto")
        assertEquals(HttpStatusCode.ServiceUnavailable, pronto.status)
        assertEquals("""{"pronto":false,"codigo":"BANCO_INDISPONIVEL"}""", pronto.bodyAsText())
        assertFalse(arquivo.toString() in pronto.bodyAsText())
        ctx.close()
    }

    @Test
    fun codigoDeNaoProntoSoNoCorpo503() = runBlocking {
        val (arquivo, ds) = bancoNovo()
        (ds as AutoCloseable).use {
            assertNull(prontidao(arquivo).avaliar().codigo)
        }
    }
}
