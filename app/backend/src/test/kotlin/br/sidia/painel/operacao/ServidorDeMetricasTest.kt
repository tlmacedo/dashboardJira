package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.testContext
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServidorDeMetricasTest {
    private fun portaLivre(): Int = ServerSocket(0).use { it.localPort }

    private val http = HttpClient.newHttpClient()

    private fun get(porta: Int, caminho: String): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$porta$caminho")).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun metricasNaPortaInternaENaoNaPublica() {
        val metricas = Metricas().registrarBindersDaJvm()
        val publica = portaLivre()
        val interna = portaLivre()
        // Com a casca estática presente, um caminho desconhecido cai na página inicial (200): é o que o /metrics público
        // NÃO pode receber. O teste prova as duas coisas.
        val casca = java.nio.file.Files.createTempDirectory("painel-casca-")
        java.nio.file.Files.writeString(casca.resolve("index.html"), "<html></html>")
        val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to casca.toString()))
        val ctx = testContext(s)
        val servidorPublico = embeddedServer(Netty, port = publica, host = "127.0.0.1") {
            module(s, ctx, metricas = metricas)
        }.start(wait = false)
        val fechar = assertNotNull(iniciarServidorDeMetricas(metricas, "127.0.0.1", interna))
        try {
            val r = get(interna, "/metrics")
            assertEquals(200, r.statusCode())
            assertEquals("text/plain; version=0.0.4; charset=utf-8", r.headers().firstValue("Content-Type").orElse(""))
            assertTrue("jvm_memory_used_bytes" in r.body())

            // a porta pública nunca serve /metrics (nem a página inicial no lugar dele)
            assertEquals(200, get(publica, "/uma-pagina-qualquer").statusCode(), "premissa: a casca responde a caminhos desconhecidos")
            assertEquals(404, get(publica, "/metrics").statusCode())
            // e a porta interna só serve /metrics
            assertEquals(404, get(interna, "/api/health").statusCode())
            assertEquals(404, get(interna, "/").statusCode())
            // a requisição pública acima já aparece nas métricas, pelo modelo
            assertTrue(get(interna, "/metrics").body().lines().any { it.startsWith("painel_http_requisicoes_total") })
        } finally {
            fechar.close()
            servidorPublico.stop(100, 500)
        }
    }

    @Test
    fun portaZeroDesliga() {
        assertNull(iniciarServidorDeMetricas(Metricas(), "127.0.0.1", 0))
        assertEquals(0, Settings.fromEnv(mapOf("PAINEL_METRICAS_PORTA" to "0")).operacao.metricasPorta)
    }

    @Test
    fun portaIgualA8080OuInvalidaFalha() {
        for (valor in listOf("8080", "80", "70000", "abc")) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("PAINEL_METRICAS_PORTA" to valor)) }
            assertTrue("PAINEL_METRICAS_PORTA" in e.message.orEmpty())
        }
    }

    @Test
    fun padroesEHostInvalido() {
        val o = Settings.fromEnv(emptyMap()).operacao
        assertEquals(9464, o.metricasPorta)
        assertEquals("127.0.0.1", o.metricasHost)
        assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("PAINEL_METRICAS_HOST" to "host com espaço")) }
    }

    @Test
    fun portaOcupadaNaoDerrubaOPainel() {
        val ocupada = ServerSocket(0)
        ocupada.use {
            assertNull(iniciarServidorDeMetricas(Metricas(), "127.0.0.1", it.localPort))
        }
    }
}
