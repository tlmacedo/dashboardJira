package br.sidia.painel.db

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.AppContext
import br.sidia.painel.config.Settings
import br.sidia.painel.contarAccessLog
import br.sidia.painel.limparAccessLog
import br.sidia.painel.testContextComBanco
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Testes do registro de consultas contra PostgreSQL real (Testcontainers). */
class AccessLogTest {
    companion object {
        private val pg = BancoEmArquivo()

        /** Para o contêiner explicitamente, sem depender do Ryuk. */
        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
        }
    }

    private lateinit var ctx: AppContext
    private lateinit var log: AccessLog
    private val agora = Instant.parse("2026-10-25T12:00:00Z")

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), pg)
        ctx.limparAccessLog()
        log = AccessLog(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private data class Linha(val userId: String, val route: String, val filters: String?, val at: Instant)

    private fun linhas(): List<Linha> = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT user_id, route, filters, at FROM access_log ORDER BY id").use { rs ->
            generateSequence {
                if (rs.next()) Linha(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()) else null
            }.toList()
        }
    }

    private fun filtros(l: Linha): JsonObject = Json.parseToJsonElement(assertNotNull(l.filters)).jsonObject

    @Test
    fun gravaUmaLinhaPorConsulta() {
        log.record("Ana.Silva", "/api/cards/tempo-em-status", mapOf("squad" to "Alfa"), agora)
        assertEquals(1, ctx.contarAccessLog())
        val l = linhas().single()
        assertEquals("ana.silva", l.userId)
        assertEquals("/api/cards/tempo-em-status", l.route)
        assertEquals("Alfa", filtros(l)["squad"]?.jsonPrimitive?.content)
        assertEquals(agora, l.at)
        log.record("ana.silva", "/api/x", emptyMap(), agora)
        assertEquals(2, ctx.contarAccessLog())
    }

    @Test
    fun filtrosSaneadosETruncados() {
        val longo = "a".repeat(500)
        log.record("ana", "/r", mapOf("busca" to "x\ny\u0000z", "longo" to longo), agora)
        val f = filtros(linhas().single())
        assertEquals("x?y?z", f["busca"]?.jsonPrimitive?.content)
        assertEquals(200, f["longo"]?.jsonPrimitive?.content?.length)
    }

    @Test
    fun naoGravaSegredos() {
        log.record(
            "ana", "/r",
            mapOf(
                "token" to "abc", "Password" to "p", "SENHA" to "s", "secret" to "x", "Authorization" to "Bearer z",
                "cookie" to "c", "key" to "k", "squad" to "Alfa",
            ),
            agora,
        )
        val l = linhas().single()
        val f = filtros(l)
        assertEquals(setOf("squad"), f.keys)
        for (segredo in listOf("abc", "Bearer z")) assertFalse(segredo in assertNotNull(l.filters))
    }

    @Test
    fun naoGravaSegredosComVariacoesDeNome() {
        val segredos = listOf(
            "access_token", "api_key", "API-KEY", "apiKey", "client_secret", " token", "jwt", "id_token",
            "X-Session-Id", "Set-Cookie", "passwd", "user_password", "Bearer", "credentials", "SAMLAssertion",
        )
        log.record("ana", "/r", segredos.associateWith { "valor-secreto" } + mapOf("squad" to "Alfa", "kind" to "paradas"), agora)
        val l = linhas().single()
        assertEquals(setOf("squad", "kind"), filtros(l).keys)
        assertFalse("valor-secreto" in assertNotNull(l.filters))
    }

    @Test
    fun usuarioTemTetoESaneamento() {
        log.record("a".repeat(500), "/r", emptyMap(), agora)
        log.record("ana\u0000\nx", "/r", emptyMap(), agora)
        val ls = linhas()
        assertEquals(64, ls[0].userId.length)
        assertEquals("ana??x", ls[1].userId)
    }

    @Test
    fun routeSemQueryStringETruncada() {
        log.record("ana", "/api/x?token=segredo&a=1", emptyMap(), agora)
        log.record("ana", "/" + "r".repeat(500), emptyMap(), agora)
        val ls = linhas()
        assertEquals("/api/x", ls[0].route)
        assertEquals(200, ls[1].route.length)
    }

    @Test
    fun noMaximoCinquentaFiltros() {
        log.record("ana", "/r", (1..80).associate { "f$it" to "v" }, agora)
        assertEquals(50, filtros(linhas().single()).size)
    }

    @Test
    fun valorComAspasEBarrasViraJsonValido() {
        log.record("ana", "/r", mapOf("q" to "a\"b\\c"), agora)
        assertEquals("a\"b\\c", filtros(linhas().single())["q"]?.jsonPrimitive?.content)
    }

    @Test
    fun usuarioVazioFalhaSemGravar() {
        assertFailsWith<IllegalArgumentException> { log.record("  ", "/r", emptyMap(), agora) }
        assertEquals(0, ctx.contarAccessLog())
    }

    @Test
    fun falhaDeGravacaoLancaExcecaoEspecificaSemDados() {
        ctx.close() // pool fechado: a gravação falha
        val e = assertFailsWith<AccessLogException> { log.record("ana.silva", "/r", mapOf("squad" to "Alfa"), agora) }
        val texto = e.message.orEmpty() + e.stackTraceToString().lines().first()
        assertFalse("ana.silva" in texto || "Alfa" in texto, texto)
        assertNull(e.cause, "a causa original (SQLException) pode trazer dados")
        // reabre o contexto para o @AfterTest poder fechar sem erro
        ctx = testContextComBanco(Settings(), pg)
    }
}
