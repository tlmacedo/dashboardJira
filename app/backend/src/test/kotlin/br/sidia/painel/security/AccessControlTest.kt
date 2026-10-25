package br.sidia.painel.security

import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.testContext
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccessControlTest {
    @Test
    fun uriQueNaoComecaComBarraExigeAutenticacao() {
        // URI absoluta ou relativa: o primeiro segmento não pode virar "público" por normalização.
        listOf("http://h/api/x", "https://h/api/health", "//h/api/x", "api/x", "")
            .forEach { assertTrue(exigeAutenticacao(it), "deveria exigir: '$it'") }
        listOf("/", "/assets/x.js", "/api/health", "/api/meta").forEach {
            assertFalse(exigeAutenticacao(it), it)
        }
    }

    private fun autenticadorFixo(principal: Principal?) = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? = principal
    }

    private fun status(
        settings: Settings,
        autenticador: Authenticator,
        caminho: String = "/api/ping-protegido",
    ): HttpStatusCode {
        var resultado = HttpStatusCode.InternalServerError
        testApplication {
            application {
                module(settings, testContext(settings), autenticador)
                routing {
                    get("/api/ping-protegido") { call.respondText("pong") }
                    requireRole(Role.GERENTE) {
                        get("/api/so-gerente") { call.respondText("ok ${call.principal.userId}") }
                    }
                }
            }
            resultado = client.get(caminho).status
        }
        return resultado
    }

    @Test
    fun devModeNaoExigeLogin() {
        val s = Settings(dashboardDevMode = true)
        assertEquals(HttpStatusCode.OK, status(s, DevAuthenticator()))
    }

    @Test
    fun producaoSemTokenRetorna401() {
        val s = Settings(dashboardDevMode = false)
        assertEquals(HttpStatusCode.Unauthorized, status(s, OidcAuthenticator()))
        assertEquals(HttpStatusCode.OK, status(s, OidcAuthenticator(), "/api/health"))
        assertEquals(HttpStatusCode.OK, status(s, OidcAuthenticator(), "/api/meta"))
    }

    @Test
    fun requireRoleNega403() {
        val s = Settings(dashboardDevMode = false)
        val usuario = autenticadorFixo(Principal("ana", setOf(Role.USUARIO)))
        assertEquals(HttpStatusCode.Forbidden, status(s, usuario, "/api/so-gerente"))
    }

    @Test
    fun requireRoleLibera() {
        val s = Settings(dashboardDevMode = false)
        val gerente = autenticadorFixo(Principal("gil", setOf(Role.GERENTE)))
        assertEquals(HttpStatusCode.OK, status(s, gerente, "/api/so-gerente"))
    }

    private val producao = Settings(dashboardDevMode = false)

    @Test
    fun caminhosCamufladosNaoContornamAutenticacao() {
        listOf("/%61pi/ping-protegido", "/API/ping-protegido", "/api//ping-protegido").forEach {
            assertEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), it), it)
        }
    }

    @Test
    fun caminhoCodificadoQueViraHealthEPublico() {
        // /api/%68ealth normaliza para /api/health, que é público por definição.
        assertEquals(HttpStatusCode.OK, status(producao, OidcAuthenticator(), "/api/%68ealth"))
    }

    @Test
    fun publicoSomenteHealthEMetaExatos() {
        // Barra final: o caminho é público (não exige login); o roteador pode responder 404, nunca 401.
        assertNotEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), "/api/health/"))
        assertEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), "/api/meta/x"))
        assertEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), "/api/healthXYZ"))
        // Mudança: fora de /api é a casca estática (sem dados) e deixou de exigir login; ver SPA abaixo.
        assertNotEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), "/fora-da-api"))
    }

    /** Executa [caminho] em produção (dashboardDevMode=false) com um `dist` de teste. */
    private fun statusComDist(autenticador: Authenticator, caminho: String): HttpStatusCode {
        val pai = java.nio.file.Files.createTempDirectory("painel-ac")
        val dist = pai.resolve("dist").also { java.nio.file.Files.createDirectories(it.resolve("assets")) }
        dist.resolve("index.html").toFile().writeText("<html>spa</html>")
        dist.resolve("assets/app.js").toFile().writeText("x")
        var resultado = HttpStatusCode.InternalServerError
        try {
            testApplication {
                val s = Settings(dashboardDevMode = false, staticDir = dist)
                application { module(s, testContext(s), autenticador) }
                resultado = client.get(caminho).status
            }
        } finally {
            pai.toFile().deleteRecursively()
        }
        return resultado
    }

    @Test
    fun cascaEstaticaEPublicaEmProducao() {
        listOf("/", "/assets/app.js", "/rota-de-pagina", "/squads/x", "/api/health", "/api/meta").forEach {
            assertEquals(HttpStatusCode.OK, statusComDist(OidcAuthenticator(), it), it)
        }
        // Arquivo estático ausente: 404 (nunca 401, a casca é pública).
        assertEquals(HttpStatusCode.NotFound, statusComDist(OidcAuthenticator(), "/favicon.ico"))
    }

    @Test
    fun apiContinuaNegadaPorPadraoEmProducao() {
        listOf(
            // "//api/x" só é testável na regra pura (cliente de teste lê "//api" como host).
            "/api/qualquer", "/api/x/../health", "/api/%2e%2e/meta", "/%61pi/x", "/api//x", "/API/x",
            "/api/health;x", "/api/meta%00", "/api",
        ).forEach {
            assertEquals(HttpStatusCode.Unauthorized, statusComDist(OidcAuthenticator(), it), it)
        }
    }

    @Test
    fun apiInexistenteAutenticadaDa404ENaoHtml() {
        val gerente = autenticadorFixo(Principal("gil", setOf(Role.GERENTE)))
        assertEquals(HttpStatusCode.NotFound, statusComDist(gerente, "/api/inexistente"))
        assertEquals(HttpStatusCode.NotFound, statusComDist(gerente, "/API/inexistente"))
        assertEquals(HttpStatusCode.NotFound, statusComDist(gerente, "/%61pi/inexistente"))
    }

    @Test
    fun regraDeCaminhoPublicoForaDaApi() {
        listOf("/", "/assets/x.js", "/squads/x", "/favicon.ico").forEach { assertFalse(exigeAutenticacao(it), it) }
        listOf("/%61pi/x", "//api/x", "/API/x", "/api/x", "/api/x/../health", "/api/%2e%2e/meta", "/x/../y", "/%zz")
            .forEach { assertTrue(exigeAutenticacao(it), it) }
    }

    @Test
    fun devModeComRequireRoleGerenteLibera() {
        assertEquals(HttpStatusCode.OK, status(Settings(dashboardDevMode = true), DevAuthenticator(), "/api/so-gerente"))
    }

    @Test
    fun devAuthenticatorForaDoDevModeLanca() {
        assertFailsWith<IllegalArgumentException> {
            testApplication { application { module(producao, testContext(producao), DevAuthenticator()) }; client.get("/api/health") }
        }
    }

    @Test
    fun negacaoDeRequireRoleNaoExecutaHandler() {
        var executou = false
        var codigo = HttpStatusCode.OK
        testApplication {
            application {
                module(producao, testContext(producao), autenticadorFixo(Principal("ana", setOf(Role.USUARIO))))
                routing { requireRole(Role.GERENTE) { get("/api/efeito") { executou = true; call.respondText("x") } } }
            }
            codigo = client.get("/api/efeito").status
        }
        assertEquals(HttpStatusCode.Forbidden, codigo)
        assertFalse(executou)
    }

    @Test
    fun principalAusenteEmRequireRoleRetorna401SemExecutar() {
        var executou = false
        var codigo = HttpStatusCode.OK
        testApplication {
            application {
                module(producao, testContext(producao), OidcAuthenticator())
                routing { requireRole(Role.GERENTE) { get("/api/efeito") { executou = true; call.respondText("x") } } }
            }
            codigo = client.get("/api/efeito").status
        }
        assertEquals(HttpStatusCode.Unauthorized, codigo)
        assertFalse(executou)
    }

    @Test
    fun gerenteExecutaHandler() {
        var executou = false
        testApplication {
            application {
                module(producao, testContext(producao), autenticadorFixo(Principal("gil", setOf(Role.GERENTE))))
                routing { requireRole(Role.GERENTE) { get("/api/efeito") { executou = true; call.respondText("x") } } }
            }
            client.get("/api/efeito")
        }
        assertTrue(executou)
    }

    @Test
    fun normalizacaoDoCaminhoCobreFormasCamufladas() {
        assertEquals(listOf("api", "ping"), segmentosNormalizados("//api/ping"))
        assertEquals(listOf("api", ".", "x", "..", "ping"), segmentosNormalizados("/%61pi/./x/../ping?q=1"))
        assertEquals(listOf("api", "health"), segmentosNormalizados("/api/%68ealth/"))
        assertEquals(null, segmentosNormalizados("/api/%zz"))
    }

    @Test
    fun segmentosDeNavegacaoEMalformadosNuncaSaoPublicos() {
        listOf(
            "/api/x/../health", "/api/x/%2e%2e/meta", "/api/%2e/health", "/api/meta/..%2fsecreto",
            "/api/health;x", "/api/health%2f", "/api/meta%00", "/api/%zz", "/api/health/..", "/api/./meta",
        ).forEach { assertTrue(exigeAutenticacao(it), it) }
        assertFalse(exigeAutenticacao("/api/health"))
        assertFalse(exigeAutenticacao("/api/meta?x=1"))
    }

    @Test
    fun navegacaoViaClienteRetorna401() {
        listOf("/api/x/../health", "/api/x/%2e%2e/meta", "/api/%2e/health").forEach {
            assertEquals(HttpStatusCode.Unauthorized, status(producao, OidcAuthenticator(), it), it)
        }
    }
}
