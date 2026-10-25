package br.sidia.painel.security

import br.sidia.painel.config.Settings
import br.sidia.painel.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessControlTest {
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
                module(settings, autenticador)
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
}
