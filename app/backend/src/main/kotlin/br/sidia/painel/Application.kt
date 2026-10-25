package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.DevAuthenticator
import br.sidia.painel.security.OidcAuthenticator
import br.sidia.painel.security.installAccessControl
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import java.time.Clock
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files

@Serializable
data class HealthResposta(val ok: Boolean)

@Serializable
data class MetaResposta(val devMode: Boolean, val liveEnabled: Boolean, val zone: String, val banner: String?)

private val log = LoggerFactory.getLogger("br.sidia.painel")

fun main() {
    val settings = Settings.fromEnv()
    // Falhas de configuração, papéis ou banco acontecem aqui, na subida, e não na primeira requisição.
    val context = AppContext.load(settings)
    Runtime.getRuntime().addShutdownHook(Thread { context.close() })
    embeddedServer(Netty, port = 8080) {
        module(settings, context)
        monitor.subscribe(ApplicationStopped) { context.close() }
    }.start(wait = true)
}

/** Módulo Ktor do painel: JSON, controle de acesso, rota de saúde e metadados do ambiente. */
fun Application.module(
    settings: Settings,
    context: AppContext,
    authenticator: Authenticator = if (settings.dashboardDevMode) {
        DevAuthenticator(context.roles, Clock.system(settings.zone))
    } else {
        OidcAuthenticator()
    },
) {
    install(ContentNegotiation) { json() }
    installAccessControl(settings, authenticator)
    if (settings.dashboardDevMode && settings.jiraLiveEnabled) {
        log.warn("ATENÇÃO: modo de desenvolvimento (sem login) com Jira real ligado: dados reais ficam expostos sem autenticação.")
    }
    val banner = when {
        !settings.dashboardDevMode -> null
        settings.jiraLiveEnabled -> "MODO DESENVOLVIMENTO: dados reais do Jira sem login"
        else -> "MODO DESENVOLVIMENTO"
    }
    if (!Files.isDirectory(settings.staticDir)) {
        log.warn("Diretório do frontend não encontrado: ${settings.staticDir} (defina PAINEL_STATIC_DIR); a SPA não será servida.")
    }
    routing {
        // Qualquer rota de API inexistente (em qualquer método) devolve 404 vazio, nunca o HTML da SPA.
        // As rotas explícitas abaixo têm precedência por serem mais específicas.
        route("/api/{...}") { handle { call.respond(HttpStatusCode.NotFound) } }
        route("/api") { handle { call.respond(HttpStatusCode.NotFound) } }
        // Build do frontend: arquivos reais, e index.html só para rotas de página (ver StaticSpa.kt).
        spaEstatica(settings.staticDir)
        get("/api/health") { call.respond(HealthResposta(ok = true)) }
        get("/api/meta") {
            call.respond(
                MetaResposta(
                    devMode = settings.dashboardDevMode,
                    liveEnabled = settings.jiraLiveEnabled,
                    zone = settings.zone.id,
                    banner = banner,
                ),
            )
        }
    }
}
