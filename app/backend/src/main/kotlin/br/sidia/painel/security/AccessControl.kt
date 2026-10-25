package br.sidia.painel.security

import br.sidia.painel.config.Settings
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey

private val chavePrincipal = AttributeKey<Principal>("painel.principal")

/** Rotas de `/api/` abertas, sem autenticação. */
private val rotasPublicas = setOf("/api/health", "/api/meta")

private fun exigeAutenticacao(caminho: String): Boolean =
    caminho.startsWith("/api/") && caminho.trimEnd('/') !in rotasPublicas

/** Principal da chamada; lança se a chamada não foi autenticada. */
val ApplicationCall.principal: Principal
    get() = attributes.getOrNull(chavePrincipal)
        ?: throw IllegalStateException("Chamada sem principal autenticado")

/**
 * Resolve o principal uma vez por chamada para toda rota sob `/api/` (exceto health e meta).
 * Sem principal, responde 401 e interrompe o processamento.
 */
fun Application.installAccessControl(settings: Settings, authenticator: Authenticator) {
    // Em produção nunca se aceita o autenticador de desenvolvimento.
    require(settings.dashboardDevMode || authenticator !is DevAuthenticator) {
        "DevAuthenticator não pode ser usado fora do modo de desenvolvimento"
    }
    intercept(ApplicationCallPipeline.Plugins) {
        if (!exigeAutenticacao(call.request.local.uri.substringBefore('?'))) return@intercept
        val principal = authenticator.authenticate(call)
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            finish()
        } else {
            call.attributes.put(chavePrincipal, principal)
        }
    }
}

private class RoleGuardConfig {
    var papeis: Set<Role> = emptySet()
}

private val RoleGuard = createRouteScopedPlugin("RoleGuard", ::RoleGuardConfig) {
    val permitidos = pluginConfig.papeis
    onCall { call ->
        val principal = call.attributes.getOrNull(chavePrincipal)
        when {
            principal == null -> call.respond(HttpStatusCode.Unauthorized)
            principal.roles.none { it in permitidos } -> call.respond(HttpStatusCode.Forbidden)
        }
    }
}

/** Seletor transparente: agrupa rotas sem alterar o caminho. */
private object GrupoPapel : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(requireRole)"
}

/** Restringe as rotas de [build] a quem tem ao menos um dos [roles]; caso contrário responde 403. */
fun Route.requireRole(vararg roles: Role, build: Route.() -> Unit) {
    require(roles.isNotEmpty()) { "requireRole exige ao menos um papel" }
    val filho = createChild(GrupoPapel)
    filho.install(RoleGuard) { papeis = roles.toSet() }
    filho.build()
}
