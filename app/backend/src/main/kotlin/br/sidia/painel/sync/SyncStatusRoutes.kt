package br.sidia.painel.sync

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
import org.slf4j.LoggerFactory

/** Quantas execuções recentes de cada instância a tela mostra. */
private const val EXECUCOES_POR_INSTANCIA = 10

private val log = LoggerFactory.getLogger("br.sidia.painel.sync.SyncStatusRoutes")

/** Negação interna: vira só o status, sem corpo. */
private class Negado(val status: HttpStatusCode) : RuntimeException(status.toString(), null, false, false)

/**
 * `GET /api/sync/status`: estado do sincronizador, SÓ para o GERENTE (inclusive em desenvolvimento, que já é
 * GERENTE). O papel é checado aqui dentro, e não por `requireRole`, porque toda chamada, inclusive a negada
 * (403), precisa ir para o `access_log`; sem registro não há resposta com dados (503).
 * O corpo nunca leva URL, token, usuário de serviço, JQL, mensagem de exceção nem username de pessoa.
 */
fun Route.rotasSyncStatus(ctx: AppContext, estado: SyncStateStore, auditoria: AccessLog) {
    get("/api/sync/status") {
        val principal = call.principal
        var status = HttpStatusCode.OK
        var corpo: SyncStatusDto? = null
        try {
            corpo = withContext(Dispatchers.IO) {
                if (Role.GERENTE !in principal.roles) throw Negado(HttpStatusCode.Forbidden)
                montarStatus(ctx, estado)
            }
        } catch (e: Negado) {
            status = e.status
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Só a classe da exceção vai para o log do servidor: a mensagem pode carregar dados.
            log.error("Falha ao montar o status do sincronizador ({})", e.javaClass.simpleName)
            status = HttpStatusCode.InternalServerError
        }
        if (!registrar(ctx, auditoria, call, principal.userId, status)) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        if (corpo != null) call.respond(corpo) else call.respond(status)
    }
}

/** Grava a consulta (com o resultado) e devolve se deu certo. */
private suspend fun registrar(
    ctx: AppContext,
    auditoria: AccessLog,
    call: ApplicationCall,
    userId: String,
    status: HttpStatusCode,
): Boolean =
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

private fun montarStatus(ctx: AppContext, estado: SyncStateStore): SyncStatusDto {
    val live = ctx.settings.jiraLiveEnabled
    val gate = SyncGate(live, ctx.settings.sync)
    val instancias = ctx.settings.sync.instancias.map { nome ->
        val suspensa = estado.suspensa(nome)
        val decisao = gate.avaliar(SyncKind.INCREMENTAL, nome, suspensa != null)
        InstanciaStatusDto(
            instancia = nome,
            gate = when (decisao) {
                GateDecision.Liberada -> InstanciaStatusDto.LIBERADA
                is GateDecision.Negada -> decisao.motivo.name
            },
            cursor = estado.cursor(nome)?.toString(),
            ultimaReconciliacao = estado.ultimaReconciliacao(nome)?.toString(),
            suspensa = suspensa,
            execucoes = estado.ultimas(nome, EXECUCOES_POR_INSTANCIA).map { it.paraDto() },
        )
    }
    return SyncStatusDto(
        modo = if (gate.modo == SyncMode.PERIODICO) SyncStatusDto.PERIODICO else SyncStatusDto.DESLIGADO_PELA_FLAG,
        liveEnabled = live,
        instancias = instancias,
        retencao = estado.ultimaPorTipo(null)[SyncKind.RETENCAO]?.paraDto(),
    )
}

/** Só campos seguros: o `http_status` e qualquer texto livre ficam de fora. */
private fun RegistroDeExecucao.paraDto() = ExecucaoDto(
    tipo = kind.name,
    status = status.name,
    inicio = startedAt.toString(),
    fim = finishedAt?.toString(),
    issues = resumo.issues,
    eventos = resumo.eventos,
    requisicoes = resumo.requisicoes,
    incompletas = resumo.incompletas,
    usuariosNaoResolvidos = resumo.usuariosNaoResolvidos,
    removidas = resumo.removidas,
    codigoErro = codigoErro,
)
