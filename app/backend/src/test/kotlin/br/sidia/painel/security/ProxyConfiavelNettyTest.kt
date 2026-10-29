package br.sidia.painel.security

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Plano 13, I3 (revisão do PR #48): prova com o Netty de verdade e requisição crua por socket (127.0.0.1, sem rede externa) que
 * DISTINGUE a leitura só da primeira linha de cabeçalho (a forjada pelo cliente) da leitura de todas as linhas. O host de teste
 * do Ktor junta linhas repetidas e por isso não pegava a diferença.
 */
class ProxyConfiavelNettyTest {
    private fun cru(porta: Int, linhas: List<String>): String {
        Socket("127.0.0.1", porta).use { s ->
            val req = (listOf("GET /eco HTTP/1.1", "Host: painel.exemplo.invalid") + linhas + listOf("Connection: close", "", "")).joinToString("\r\n")
            s.getOutputStream().write(req.toByteArray())
            s.getOutputStream().flush()
            return s.getInputStream().readBytes().decodeToString().substringAfter("\r\n\r\n")
        }
    }

    private fun campo(corpo: String, nome: String) = corpo.substringAfter("$nome=").substringBefore(";")

    private fun comServidor(bloco: (Int) -> Unit) {
        val porta = ServerSocket(0).use { it.localPort }
        val servidor = embeddedServer(Netty, port = porta, host = "127.0.0.1") {
            instalarProxyConfiavel(listOf(FaixaCidr.parse("127.0.0.1/32")))
            routing {
                get("/eco") {
                    call.respondText(
                        "PRIMEIRA=${call.request.headers["X-Forwarded-For"]};IP=${call.request.origin.remoteAddress};" +
                            "HOST=${call.request.origin.serverHost};ESQ=${call.request.origin.scheme}",
                    )
                }
            }
        }.start(wait = false)
        try {
            bloco(porta)
        } finally {
            servidor.stop(100, 1000)
        }
    }

    @Test
    fun duasLinhasDeXffValeAUltimaQueOProxyAcrescentou() = comServidor { porta ->
        val r = cru(porta, listOf("X-Forwarded-For: 198.51.100.7", "X-Forwarded-For: 203.0.113.50"))
        // `headers[...]` (o código antigo) devolve só a primeira linha: a forjada pelo cliente
        assertEquals("198.51.100.7", campo(r, "PRIMEIRA"), "a sonda precisa reproduzir o caso que distingue o código antigo")
        // o código novo junta todas as linhas e lê da direita
        assertEquals("203.0.113.50", campo(r, "IP"))
    }

    @Test
    fun hostEProtoDivergentesEmLinhasSeparadasSaoIgnoradosNoNettyReal() = comServidor { porta ->
        val r = cru(
            porta,
            listOf(
                "X-Forwarded-Host: atacante.invalid", "X-Forwarded-Host: painel.publico.invalid",
                "X-Forwarded-Proto: http", "X-Forwarded-Proto: https",
            ),
        )
        assertNotEquals("atacante.invalid", campo(r, "HOST"))
        assertNotEquals("painel.publico.invalid", campo(r, "HOST"))
        assertEquals("http", campo(r, "ESQ"))
    }

    @Test
    fun hostEProtoIguaisValemNoNettyReal() = comServidor { porta ->
        val r = cru(porta, listOf("X-Forwarded-Host: painel.publico.invalid", "X-Forwarded-Proto: https"))
        assertEquals("painel.publico.invalid", campo(r, "HOST"))
        assertEquals("https", campo(r, "ESQ"))
    }
}
