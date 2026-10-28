package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.Role
import br.sidia.painel.security.principal
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class AlertaDto(val codigo: String, val gravidade: String, val instancia: String? = null, val desde: String)

@Serializable
data class AlertasDto(val alertas: List<AlertaDto>)

private val log = LoggerFactory.getLogger("br.sidia.painel.operacao.AlertasRoutes")

/** Negação interna: vira só o status, sem corpo. */
private class Negado(val status: HttpStatusCode) : RuntimeException(status.toString(), null, false, false)

/**
 * `GET /api/operacao/alertas`: os alertas operacionais de agora, SÓ para o GERENTE. Mesmo padrão de `/api/sync/status`: o
 * papel é conferido aqui dentro, toda chamada (inclusive a negada) vai para o `access_log` e, sem registro, responde 503
 * sem dados. O corpo só leva código, gravidade, instância e início: nenhum username, chave, caminho ou texto livre.
 */
fun Route.rotasDeAlertas(ctx: AppContext, avaliador: AvaliadorDeAlertas, auditoria: AccessLog) {
    get("/api/operacao/alertas") {
        val principal = call.principal
        var status = HttpStatusCode.OK
        var corpo: AlertasDto? = null
        try {
            corpo = withContext(Dispatchers.IO) {
                if (Role.GERENTE !in principal.roles) throw Negado(HttpStatusCode.Forbidden)
                AlertasDto(avaliador.avaliar().map { AlertaDto(it.codigo.name, it.gravidade.name, it.instancia, it.desde.toString()) })
            }
        } catch (e: Negado) {
            status = e.status
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Falha ao montar os alertas ({})", e.javaClass.simpleName)
            status = HttpStatusCode.InternalServerError
        }
        if (!registrar(ctx, auditoria, call, principal.userId, status)) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        if (corpo != null) call.respond(corpo) else call.respond(status)
    }
}

private suspend fun registrar(ctx: AppContext, auditoria: AccessLog, call: ApplicationCall, userId: String, status: HttpStatusCode): Boolean =
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(userId, call.request.path(), mapOf("resultado" to status.value.toString()), ctx.clock.instant())
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao gravar no access_log ({})", e.javaClass.simpleName)
        false
    }
