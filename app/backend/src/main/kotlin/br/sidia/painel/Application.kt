package br.sidia.painel

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResposta(val ok: Boolean)

fun main() {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}

/** Módulo Ktor do painel: JSON e rota de saúde. */
fun Application.module(settings: Settings = Settings.fromEnv()) {
    install(ContentNegotiation) { json() }
    routing {
        get("/api/health") { call.respond(HealthResposta(ok = true)) }
    }
}
