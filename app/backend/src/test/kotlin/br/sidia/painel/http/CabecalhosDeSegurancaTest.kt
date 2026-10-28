package br.sidia.painel.http

import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.testContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CabecalhosDeSegurancaTest {
    private val cspEsperada = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
        "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

    private val semLogin = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? = null
    }

    private fun dist(): Path {
        val dir = Files.createTempDirectory("painel-cab").resolve("dist").createDirectories()
        dir.resolve("index.html").writeText("<!doctype html><html><body>painel-teste</body></html>")
        dir.resolve("assets").createDirectories().resolve("app.js").writeText("console.log('ok')")
        return dir
    }

    /** Sobe o app com o `dist` de teste. `dev = false` usa um autenticador que nega tudo (produção sem login). */
    private fun comApp(dev: Boolean, bloco: suspend ApplicationTestBuilder.() -> Unit) {
        val dir = dist()
        try {
            testApplication {
                val s = Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to dir.toString())).copy(dashboardDevMode = dev)
                application {
                    if (dev) module(s, testContext(s)) else module(s, testContext(s), semLogin)
                }
                bloco()
            }
        } finally {
            dir.parent.toFile().deleteRecursively()
        }
    }

    private val obrigatorios = mapOf(
        "Content-Security-Policy" to cspEsperada,
        "X-Frame-Options" to "DENY",
        "X-Content-Type-Options" to "nosniff",
        "Referrer-Policy" to "no-referrer",
        "Permissions-Policy" to "camera=(), microphone=(), geolocation=(), payment=()",
        "Cross-Origin-Opener-Policy" to "same-origin",
    )

    private fun confereCabecalhos(r: HttpResponse, onde: String) {
        obrigatorios.forEach { (nome, valor) -> assertEquals(valor, r.headers[nome], "$nome em $onde") }
    }

    private suspend fun HttpClient.pega(caminho: String) = get(caminho)

    @Test
    fun todaRespostaTemOsCabecalhos() = comApp(dev = true) {
        listOf("/api/health", "/api/meta", "/", "/assets/app.js", "/api/inexistente", "/squads/x", "/nao-existe.png").forEach {
            confereCabecalhos(client.pega(it), it)
        }
    }

    @Test
    fun respostas401TambemTemOsCabecalhos() = comApp(dev = false) {
        val r = client.pega("/api/cards/qualquer")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        confereCabecalhos(r, "401")
    }

    @Test
    fun respostas404ComCorpoTambemTemOsCabecalhos() = comApp(dev = true) {
        val r = client.pega("/api/inexistente")
        assertEquals(HttpStatusCode.NotFound, r.status)
        confereCabecalhos(r, "404")
    }

    @Test
    fun cabecalhosAparecemUmaVez() = comApp(dev = true) {
        listOf("/", "/api/meta", "/assets/app.js", "/squads/x").forEach { caminho ->
            val r = client.pega(caminho)
            (obrigatorios.keys + HttpHeaders.CacheControl).forEach { nome ->
                val valores = r.headers.getAll(nome) ?: emptyList()
                assertTrue(valores.size <= 1, "$nome repetido em $caminho: $valores")
            }
            assertEquals(1, r.headers.getAll("X-Content-Type-Options")?.size, caminho)
        }
    }

    @Test
    fun cspSemUnsafeInline() = comApp(dev = true) {
        val csp = assertNotNull(client.pega("/").headers["Content-Security-Policy"])
        assertFalse(csp.contains("unsafe-"), csp)
        assertTrue(csp.contains("script-src 'self'"))
    }

    @Test
    fun cspContinuaComFontSelf() = comApp(dev = true) {
        val csp = assertNotNull(client.pega("/").headers["Content-Security-Policy"])
        // Fontes (inclusive a da marca, se vier) só do próprio domínio: nada de host externo na CSP.
        assertTrue(csp.contains("font-src 'self';"), csp)
        assertFalse(Regex("https?:|//").containsMatchIn(csp), "a CSP não pode citar domínio externo: $csp")
        assertEquals(cspEsperada, csp)
    }

    @Test
    fun frameAncestorsNone()= comApp(dev = true) {
        assertTrue(assertNotNull(client.pega("/").headers["Content-Security-Policy"]).contains("frame-ancestors 'none'"))
        assertEquals("DENY", client.pega("/").headers["X-Frame-Options"])
    }

    @Test
    fun hstsSoForaDoDevMode() {
        comApp(dev = true) { assertNull(client.pega("/api/meta").headers["Strict-Transport-Security"]) }
        comApp(dev = false) {
            assertEquals("max-age=31536000", client.pega("/api/meta").headers["Strict-Transport-Security"])
            assertEquals("max-age=31536000", client.pega("/").headers["Strict-Transport-Security"])
        }
    }

    @Test
    fun noStoreSoNaApi() = comApp(dev = true) {
        assertEquals("no-store", client.pega("/api/meta").headers[HttpHeaders.CacheControl])
        assertEquals("no-store", client.pega("/api/inexistente").headers[HttpHeaders.CacheControl])
        // O servidor estático define o próprio Cache-Control; o plugin não o sobrescreve nem acrescenta no-store.
        assertEquals("public, max-age=31536000, immutable", client.pega("/assets/app.js").headers[HttpHeaders.CacheControl])
        assertEquals("no-cache", client.pega("/").headers[HttpHeaders.CacheControl])
        // Fora da API e sem Cache-Control próprio (404 de arquivo), nada de no-store.
        assertNull(client.pega("/nao-existe.png").headers[HttpHeaders.CacheControl])
    }
}
