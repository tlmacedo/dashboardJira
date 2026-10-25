package br.sidia.painel.security

import br.sidia.painel.config.Settings
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
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

/** Únicas rotas públicas, já normalizadas. Todo o resto exige principal (negar por padrão). */
private val rotasPublicas = setOf(listOf("api", "health"), listOf("api", "meta"))

/**
 * Normaliza o caminho como o roteador do Ktor: divide por `/`, descarta segmentos vazios e
 * decodifica cada segmento. NÃO resolve `.` nem `..` (o roteador também não resolve): eles
 * ficam na lista e fazem [exigeAutenticacao] negar o acesso público. Devolve `null` se a
 * decodificação falhar.
 */
internal fun segmentosNormalizados(uri: String): List<String>? {
    val caminho = uri.substringBefore('?').substringBefore('#')
    val saida = ArrayList<String>()
    for (bruto in caminho.split('/')) {
        if (bruto.isEmpty()) continue
        val segmento = try {
            bruto.decodeURLPart()
        } catch (e: Exception) {
            return null
        }
        saida.add(segmento)
    }
    return saida
}

/**
 * Decide se o caminho exige autenticação. Negar por padrão DENTRO da API.
 *
 * É público somente se:
 * 1. normaliza exatamente para `/api/health` ou `/api/meta`; ou
 * 2. é a casca estática da SPA (HTML, JS e CSS sem dados): o primeiro segmento decodificado NÃO é
 *    `api` (sem diferenciar maiúsculas), nenhum segmento é `.` ou `..` e a decodificação não falhou.
 *
 * Todos os dados ficam atrás de `/api/` (qualquer subcaminho), que continua protegida; assim a SPA e a futura tela de
 * login carregam mesmo em produção com autenticador que nega. Variantes camufladas da API
 * (`/%61pi/x`, `//api/x`, `/API/x`, `/api/x/../health`) continuam exigindo principal.
 */
internal fun exigeAutenticacao(uri: String): Boolean {
    val segmentos = segmentosNormalizados(uri) ?: return true
    if (segmentos.any { it == "." || it == ".." }) return true
    if (segmentos in rotasPublicas) return false
    // Casca estática: fora da API e sem NUL em nenhum segmento.
    val primeiro = segmentos.firstOrNull() ?: return false // "/" é a página inicial da SPA
    return primeiro.equals("api", ignoreCase = true) || segmentos.any { '\u0000' in it }
}

/**
 * Principal da chamada. Fora de rota protegida ela lança: isso é erro de programação,
 * não condição de runtime (toda rota não pública já passa pela autenticação).
 */
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
        try {
            proceed()
        } catch (e: AcessoNegado) {
            call.respond(e.status)
        }
    }
    intercept(ApplicationCallPipeline.Plugins) {
        if (!exigeAutenticacao(call.request.local.uri)) return@intercept
        val principal = authenticator.authenticate(call)
        if (principal == null) {
            call.respond(HttpStatusCode.Unauthorized)
            finish()
        } else {
            call.attributes.put(chavePrincipal, principal)
        }
    }
}

/** Negação de acesso: capturada em [installAccessControl] e convertida em 401/403. */
private class AcessoNegado(val status: HttpStatusCode) : RuntimeException(status.toString())

private class RoleGuardConfig {
    var papeis: Set<Role> = emptySet()
}

private val RoleGuard = createRouteScopedPlugin("RoleGuard", ::RoleGuardConfig) {
    val permitidos = pluginConfig.papeis
    onCall { call ->
        val principal = call.attributes.getOrNull(chavePrincipal)
        // A exceção interrompe o pipeline: o handler da rota nunca roda em caso de negação.
        when {
            principal == null -> throw AcessoNegado(HttpStatusCode.Unauthorized)
            principal.roles.none { it in permitidos } -> throw AcessoNegado(HttpStatusCode.Forbidden)
        }
    }
}

/** Seletor transparente: agrupa rotas sem alterar o caminho. */
private object GrupoPapel : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(requireRole)"
}

/**
 * Restringe as rotas de [build] a quem tem ao menos um dos [roles]; sem principal responde 401
 * e com papel insuficiente 403, sem executar o handler.
 *
 * NÃO há hierarquia de papéis: GERENTE não satisfaz `requireRole(COORDENADOR)`. Quando ambos
 * valem, a rota deve listar `COORDENADOR, GERENTE`.
 */
fun Route.requireRole(vararg roles: Role, build: Route.() -> Unit) {
    require(roles.isNotEmpty()) { "requireRole exige ao menos um papel" }
    val filho = createChild(GrupoPapel)
    filho.install(RoleGuard) { papeis = roles.toSet() }
    filho.build()
}
