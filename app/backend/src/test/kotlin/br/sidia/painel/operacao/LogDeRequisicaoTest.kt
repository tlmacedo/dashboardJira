package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.testContext
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogDeRequisicaoTest {
    private val saidaOriginal: PrintStream = System.out
    private val capturada = ByteArrayOutputStream()

    @BeforeEach
    fun capturar() {
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(PrintStream(capturada, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restaurar() = System.setOut(saidaOriginal)

    private fun linhasDeRequisicao(): List<String> =
        capturada.toString(Charsets.UTF_8).lines().filter { "\"metodo=" in it || "metodo=" in it }

    private val ana = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall) = Principal("ana.silva", setOf(Role.GERENTE))
    }

    @Test
    fun linhaPorRequisicaoComModeloDaRota() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        routing { get("/api/tarefa/{key}") { call.respondText("ok") } }
        val r = client.get("/api/tarefa/TG-1?instancia=samsung")
        assertEquals(HttpStatusCode.OK, r.status)
        val linhas = linhasDeRequisicao()
        assertEquals(1, linhas.size, capturada.toString(Charsets.UTF_8))
        val linha = linhas.single()
        assertTrue("rota=/api/tarefa/{key}" in linha, linha)
        assertTrue("metodo=GET" in linha && "status=200" in linha && "duracaoMs=" in linha, linha)
        assertFalse("TG-1" in linha, linha)
        assertFalse("instancia" in linha || "samsung" in linha, linha)
    }

    @Test
    fun semUsuarioNoLog() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s), authenticator = ana) }
        routing { get("/api/tarefa/{key}") { call.respondText("ok") } }
        client.get("/api/tarefa/TG-1")
        assertTrue(linhasDeRequisicao().isNotEmpty())
        assertFalse("ana.silva" in capturada.toString(Charsets.UTF_8))
    }

    @Test
    fun requestIdDoProxyAceitoSoValido() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        routing { get("/api/tarefa/{key}") { call.respondText("ok") } }

        val invalido = client.get("/api/tarefa/TG-1") { header("X-Request-Id", "../../x") }
        val gerado = assertNotNull(invalido.headers["X-Request-Id"])
        assertTrue(Regex("^[A-Za-z0-9-]{8,64}$").matches(gerado), gerado)
        assertTrue(gerado != "../../x")

        val valido = client.get("/api/tarefa/TG-1") { header("X-Request-Id", "abc-12345678") }
        assertEquals("abc-12345678", valido.headers["X-Request-Id"])
        assertTrue(capturada.toString(Charsets.UTF_8).contains("\"requisicao\":\"abc-12345678\""))
    }

    @Test
    fun healthNaoPoluiInfo() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        client.get("/api/health")
        client.get("/")
        assertTrue(linhasDeRequisicao().isEmpty(), capturada.toString(Charsets.UTF_8))
    }
}
