package br.sidia.painel

import br.sidia.painel.config.Settings
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaTest {
    private val html = "<!doctype html><html><body>painel-teste</body></html>"

    /** Cria um `dist` de teste com index.html e um asset. */
    private fun dist(): Path {
        val dir = Files.createTempDirectory("painel-dist")
        dir.resolve("index.html").writeText(html)
        dir.resolve("assets").createDirectories().resolve("app.js").writeText("console.log('ok')")
        return dir
    }

    private fun comDist(bloco: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) {
        val dir = dist()
        try {
            testApplication {
                application { module(Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to dir.toString()))) }
                bloco()
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun servidorEntregaIndexEFallback() = comDist {
        assertEquals(html, client.get("/").bodyAsText())
        assertEquals(html, client.get("/rota-qualquer").bodyAsText())
        assertEquals(html, client.get("/squads/alpha/pessoas").bodyAsText())
    }

    @Test
    fun arquivoExistenteEhEntregueSemFallback() = comDist {
        assertEquals("console.log('ok')", client.get("/assets/app.js").bodyAsText())
    }

    @Test
    fun apiInexistenteDevolve404SemHtml() = comDist {
        val resposta = client.get("/api/inexistente")
        assertEquals(HttpStatusCode.NotFound, resposta.status)
        assertFalse(resposta.bodyAsText().contains("painel-teste"))
        assertEquals(HttpStatusCode.NotFound, client.get("/api/inexistente/aninhada").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/inexistente").status)
    }

    @Test
    fun rotasDaApiContinuamFuncionando() = comDist {
        assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        assertTrue(client.get("/api/meta").bodyAsText().contains("\"liveEnabled\":false"))
    }

    @Test
    fun semDistNaoQuebra() {
        testApplication {
            application { module(Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "/nao/existe/dist"))) }
            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
            assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        }
    }
}
