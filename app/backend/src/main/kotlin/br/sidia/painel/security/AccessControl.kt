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
internal val rotasPublicas =setOf(listOf("api", "health"), listOf("api", "meta"))

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

/** Segmento aceito na casca estática: começa com letra ou dígito (nada oculto) e só usa caracteres simples. */
private val segmentoEstatico = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

/** Máximo de segmentos de uma URL da casca estática (as telas da SPA têm no máximo 3 ou 4). */
private const val MAX_SEGMENTOS_ESTATICOS = 8

/**
 * Decide se o caminho é PÚBLICO. Lista de permissão: tudo o que não for reconhecido exige login
 * (negar por padrão em todo o servidor, não só na API).
 *
 * É público somente se:
 * 1. normaliza exatamente para um item de [publicas] (hoje `/api/health` e `/api/meta`); ou
 * 2. é a raiz `/`; ou
 * 3. é a casca estática da SPA (HTML, JS e CSS sem dados): o primeiro segmento NÃO é `api` nem `auth`
 *    (sem diferenciar maiúsculas), todo segmento casa [segmentoEstatico] (nada oculto, vazio, com `%`, `\`, NUL
 *    ou `..`), há no máximo [MAX_SEGMENTOS_ESTATICOS] segmentos e a URI começa com `/` e não com `//`.
 *
 * Todos os dados ficam atrás de `/api/`, que continua protegida; assim a SPA e a futura tela de login
 * carregam mesmo em produção com autenticador que nega. Variantes camufladas da API
 * (`/%61pi/x`, `//api/x`, `/API/x`, `/api/x/../health`) e tudo sob `/auth` exigem principal; os Planos 8 e 12
 * acrescentam suas rotas públicas (`/auth/login`, `/auth/callback`) em [rotasPublicas].
 */
internal fun ehPublica(uri: String, publicas: Set<List<String>> = rotasPublicas): Boolean {
    // URI absoluta (`http://h/api/x`) ou relativa: o roteador pode normalizá-la para um caminho
    // diferente do que se analisa aqui, então nunca é pública. Também cobre `//h/api/x`.
    if (!uri.startsWith("/") || uri.startsWith("//")) return false
    val segmentos = segmentosNormalizados(uri) ?: return false
    if (segmentos in publicas) return true
    if (segmentos.isEmpty()) return true // "/" é a página inicial da SPA
    if (segmentos.size > MAX_SEGMENTOS_ESTATICOS) return false
    val primeiro = segmentos.first()
    if (primeiro.equals("api", ignoreCase = true) || primeiro.equals("auth", ignoreCase = true)) return false
    // Os segmentos já vêm decodificados: um "%2F" decodificado vira "/" e não casa a expressão.
    return segmentos.all { segmentoEstatico.matches(it) }
}

/** Contrário de [ehPublica]: o caminho exige principal autenticado. */
internal fun exigeAutenticacao(uri: String): Boolean = !ehPublica(uri)


/**
 * Principal da chamada. Fora de rota protegida ela lança: isso é erro de programação,
 * não condição de runtime (toda rota não pública já passa pela autenticação).
 */
val ApplicationCall.principal: Principal
    get() = attributes.getOrNull(chavePrincipal)
        ?: throw IllegalStateException("Chamada sem principal autenticado")

/** Principal da chamada se já foi resolvido; `null` em rota pública ou antes da autenticação. Nunca lança. */
internal fun ApplicationCall.principalOuNulo(): Principal? = attributes.getOrNull(chavePrincipal)

/**
 * Resolve o principal uma vez por chamada para toda rota sob `/api/` (exceto health e meta).
 * Sem principal, responde 401 e interrompe o processamento.
 */
fun Application.installAccessControl(
    settings: Settings,
    authenticator: Authenticator,
    // Gancho de auditoria: chamado quando um principal autenticado é negado por não ter papel vigente.
    aoNegarSemPapel: suspend (ApplicationCall, Principal) -> Unit = { _, _ -> },
) {
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
        } else if (principal.roles.isEmpty()) {
            // Autenticado porém sem papel vigente (id desconhecido, papel encerrado): nega por padrão.
            // O gancho nunca pode impedir a negação (quem o fornece trata as próprias falhas).
            try {
                aoNegarSemPapel(call, principal)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                // negação segue mesmo assim
            }
            call.respond(HttpStatusCode.Forbidden)
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
