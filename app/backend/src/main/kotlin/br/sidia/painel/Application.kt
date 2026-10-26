package br.sidia.painel

import br.sidia.painel.cards.registrarNegacaoSemPapel
import br.sidia.painel.cards.rotasTempoEmStatus
import br.sidia.painel.config.Settings
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.DevAuthenticator
import br.sidia.painel.security.OidcAuthenticator
import br.sidia.painel.security.installAccessControl
import br.sidia.painel.sync.PostgresIssueStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
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
import java.time.Clock

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
        module(context.settings, context)
        monitor.subscribe(ApplicationStopped) { context.close() }
    }.start(wait = true)
}

/** Em desenvolvimento, sem login (relógio na zona do painel); em produção, o SSO/OIDC. */
private fun autenticadorPadrao(settings: Settings): Authenticator =
    if (settings.dashboardDevMode) DevAuthenticator(clock = Clock.system(settings.zone)) else OidcAuthenticator()

/**
 * Módulo Ktor do painel: JSON, controle de acesso, rota de saúde e metadados do ambiente.
 *
 * A configuração efetiva é SEMPRE `context.settings`. O primeiro parâmetro existe só por
 * compatibilidade de assinatura (posicional) e é ignorado, para que os dois nunca divirjam em silêncio.
 */
fun Application.module(
    @Suppress("UNUSED_PARAMETER") settingsIgnorado: Settings,
    context: AppContext,
    authenticator: Authenticator = autenticadorPadrao(context.settings),
) {
    val settings = context.settings
    install(ContentNegotiation) { json() }
    // Sem banco (só em testes) não há auditoria nem rotas de dados.
    val auditoria = context.dataSource?.let { AccessLog(it) }
    installAccessControl(settings, authenticator, aoNegarSemPapel = { call, principal ->
        auditoria?.let { registrarNegacaoSemPapel(it, context, call, principal) }
    })
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
        context.dataSource?.let { ds ->
            rotasTempoEmStatus(context, PostgresIssueStore(ds), AccessLog(ds))
        }
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
