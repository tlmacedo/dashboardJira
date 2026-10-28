package br.sidia.painel

import br.sidia.painel.config.Settings
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpaTest {
    private val html = "<!doctype html><html><body>painel-teste</body></html>"
    private val segredo = "SEGREDO-FORA-DO-DIST"

    /**
     * Cria uma pasta pai com um arquivo "secreto" fora do `dist` e, dentro dela, o `dist` de teste
     * com index.html e um asset. Devolve o `dist`.
     */
    private fun dist(): Path {
        val pai = Files.createTempDirectory("painel-pai")
        pai.resolve("secreto").writeText(segredo)
        pai.resolve("secreto.txt").writeText(segredo)
        val dir = pai.resolve("dist").createDirectories()
        dir.resolve("index.html").writeText(html)
        val assets = dir.resolve("assets").createDirectories()
        assets.resolve("app.js").writeText("console.log('ok')")
        // Arquivos e pasta ocultos dentro do dist nunca são servidos.
        dir.resolve(".oculto").writeText("OCULTO-DA-SPA")
        assets.resolve(".oculto").writeText("OCULTO-DA-SPA")
        dir.resolve(".pasta").createDirectories().resolve("arquivo").writeText("OCULTO-DA-SPA")
        return dir
    }

    private fun comDist(bloco: suspend ApplicationTestBuilder.() -> Unit) {
        val dir = dist()
        try {
            testApplication {
                val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to dir.toString()))
                application { module(s, testContext(s)) }
                bloco()
            }
        } finally {
            dir.parent.toFile().deleteRecursively()
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
        // Método mutante: com o cabeçalho anti-CSRF chega ao roteador (404); sem ele a proteção nega antes (403 JSON, nunca HTML).
        assertEquals(HttpStatusCode.NotFound, client.post("/api/inexistente") { comCsrf() }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/inexistente").status)
        // Variantes camufladas da API também nunca recebem o HTML.
        // ("//api/x" não é testável pelo cliente de teste, que o lê como host; coberto em AccessControlTest.)
        listOf("/API/inexistente", "/%61pi/inexistente", "/api//inexistente").forEach {
            val r = client.get(it)
            assertFalse(r.bodyAsText().contains("painel-teste"), it)
            assertNotEquals(HttpStatusCode.OK, r.status, it)
        }
    }

    @Test
    fun fallbackSoParaPaginasSemExtensao() = comDist {
        assertEquals(HttpStatusCode.NotFound, client.get("/assets/sumido.js").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/qualquer.png").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/favicon.ico").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/squads/x.map").status)
        val pagina = client.get("/squads/x")
        assertEquals(HttpStatusCode.OK, pagina.status)
        assertEquals(html, pagina.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/inexistente").status)
    }

    @Test
    fun cabecalhosDeCacheENosniff() = comDist {
        val index = client.get("/")
        assertEquals("nosniff", index.headers["X-Content-Type-Options"])
        assertEquals("no-cache", index.headers[HttpHeaders.CacheControl])
        val fallback = client.get("/squads/x")
        assertEquals("no-cache", fallback.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", fallback.headers["X-Content-Type-Options"])
        val asset = client.get("/assets/app.js")
        assertEquals("public, max-age=31536000, immutable", asset.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", asset.headers["X-Content-Type-Options"])
        // A API não ganha cache de arquivo estático: leva no-store (cabeçalhos de segurança).
        assertEquals("no-store", client.get("/api/health").headers[HttpHeaders.CacheControl])
    }

    @Test
    fun travessiaDeCaminhoNuncaSaiDoDist() = comDist {
        listOf(
            "/..%2f..%2fetc/passwd",
            "/%2e%2e/%2e%2e/etc/passwd",
            "/assets/..%2f..%2f..%2fsecreto",
            "/..%2fsecreto.txt",
            "/%2e%2e/secreto.txt",
            "/assets/%2e%2e/%2e%2e/secreto.txt",
            "/../secreto.txt",
            "//etc/passwd",
            "/..\\x",
            "/..%5csecreto.txt",
            "/assets/..%5c..%5csecreto.txt",
            "/%00",
            "/secreto%00.js",
        ).forEach { caminho ->
            val corpo = try {
                client.get(caminho).bodyAsText()
            } catch (e: Exception) {
                // O cliente de teste pode recusar a URL malformada: também não vaza nada.
                ""
            }
            assertFalse(corpo.contains(segredo), caminho)
            assertFalse(corpo.contains("root:"), caminho)
        }
    }

    @Test
    fun rotasDaApiContinuamFuncionando() = comDist {
        assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        assertTrue(client.get("/api/meta").bodyAsText().contains("\"liveEnabled\":false"))
    }

    @Test
    fun arquivoOcultoDaSpa404() = comDist {
        assertEquals(HttpStatusCode.NotFound, client.get("/.oculto").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/assets/.oculto").status)
        // Pasta oculta não cai no fallback da página inicial: também é 404.
        assertEquals(HttpStatusCode.NotFound, client.get("/.pasta/arquivo").status)
        listOf("/.oculto", "/assets/.oculto", "/.pasta/arquivo").forEach {
            assertFalse(client.get(it).bodyAsText().contains("OCULTO-DA-SPA"), it)
        }
    }

    @Test
    fun headEmAssetResponde200SemCorpo() = comDist {
        val get = client.get("/assets/app.js")
        val head = client.head("/assets/app.js")
        assertEquals(HttpStatusCode.OK, head.status)
        assertEquals("", head.bodyAsText())
        assertEquals(get.headers[HttpHeaders.CacheControl], head.headers[HttpHeaders.CacheControl])
        assertEquals(get.headers[HttpHeaders.ContentType], head.headers[HttpHeaders.ContentType])
        assertEquals(get.headers[HttpHeaders.ContentLength], head.headers[HttpHeaders.ContentLength])
        assertEquals("nosniff", head.headers["X-Content-Type-Options"])
        // Página da SPA e arquivo inexistente.
        assertEquals(HttpStatusCode.OK, client.head("/squads/x").status)
        assertEquals(HttpStatusCode.NotFound, client.head("/assets/sumido.js").status)
        assertEquals(HttpStatusCode.NotFound, client.head("/.oculto").status)
    }

    @Test
    fun moduleSemSettingsCompila() {
        testApplication {
            val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "/nao/existe/dist"))
            val ctx = testContext(s)
            application { module(context = ctx) }
            assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        }
    }

    @Test
    fun moduleComSettingsPosicionalCompila() {
        testApplication {
            val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "/nao/existe/dist"))
            application { module(s, testContext(s)) }
            assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        }
    }

    @Test
    fun semDistNaoQuebra() {
        testApplication {
            val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "/nao/existe/dist"))
            application { module(s, testContext(s)) }
            assertEquals(HttpStatusCode.NotFound, client.get("/").status)
            assertEquals("""{"ok":true}""", client.get("/api/health").bodyAsText())
        }
    }
}
