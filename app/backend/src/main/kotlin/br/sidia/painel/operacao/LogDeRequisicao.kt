package br.sidia.painel.operacao

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.util.UUID

private val INICIO = AttributeKey<Long>("painel.inicioDaRequisicao")
private val ROTA = AttributeKey<String>("painel.modeloDaRota")

/** Id de requisição aceito do proxy: só letras, dígitos e hífen, de 8 a 64 caracteres (senão gera um UUID). */
internal val ID_DE_REQUISICAO_VALIDO = Regex("^[A-Za-z0-9-]{8,64}$")

/**
 * Id de requisição (`X-Request-Id`, devolvido na resposta e no MDC como `requisicao`) e UMA linha de log por requisição
 * da API: `metodo`, `rota` (o MODELO da rota, como `/api/tarefa/{key}`; nunca o caminho real), `status` e `duracaoMs`.
 * Sem query string, usuário, cabeçalhos nem corpo. A saúde (`/api/health*`) e a casca estática não geram linha, para
 * não poluir o INFO com a sonda do monitor e com os arquivos do frontend.
 */
fun Application.instalarLogDeRequisicao() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { ID_DE_REQUISICAO_VALIDO.matches(it) }
        replyToHeader(HttpHeaders.XRequestId)
    }
    intercept(ApplicationCallPipeline.Setup) { call.attributes.put(INICIO, System.nanoTime()) }
    // A chamada que o CallLogging recebe não é a do roteador: guarda o modelo da rota casada quando o roteador a resolve.
    monitor.subscribe(RoutingRoot.RoutingCallStarted) { call -> call.attributes.put(ROTA, call.route.toString()) }
    // Exceção não tratada: sem isto o Ktor loga "Unhandled: GET - <caminho real>" (com a chave da issue) e a pilha/mensagem
    // crua. Aqui só o MODELO da rota e a CLASSE da exceção saem; a resposta é 500 sem corpo (400/404 para erros de requisição).
    intercept(ApplicationCallPipeline.Plugins) {
        try {
            proceed()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            val status = when (e) {
                is io.ktor.server.plugins.NotFoundException -> io.ktor.http.HttpStatusCode.NotFound
                is io.ktor.server.plugins.BadRequestException -> io.ktor.http.HttpStatusCode.BadRequest
                else -> io.ktor.http.HttpStatusCode.InternalServerError
            }
            LoggerFactory.getLogger("br.sidia.painel.requisicao")
                .error("Falha não tratada rota={} classe={} status={}", modeloDaRota(call), e.javaClass.simpleName, status.value)
            call.respond(status)
        }
    }
    install(CallLogging) {
        level = Level.INFO
        logger = LoggerFactory.getLogger("br.sidia.painel.requisicao")
        callIdMdc(EncoderJsonSeguro.CAMPO_REQUISICAO)
        filter { call -> deveRegistrar(call.request.path()) }
        format { call ->
            val inicio = call.attributes.getOrNull(INICIO)
            val duracao = if (inicio == null) 0L else (System.nanoTime() - inicio) / 1_000_000
            "metodo=${call.request.httpMethod.value} rota=${modeloDaRota(call)} " +
                "status=${call.response.status()?.value ?: 0} duracaoMs=$duracao"
        }
    }
}

private fun deveRegistrar(caminho: String): Boolean =
    caminho.startsWith("/api/") && !caminho.startsWith("/api/health")

/**
 * Modelo da rota casada (`/api/tarefa/{key}`), sem seletores de método nem de papel. Sem rota casada: `desconhecida`.
 * Nunca usa o caminho real: ele pode levar chave de issue.
 */
internal fun modeloDaRota(call: ApplicationCall): String {
    val rota = call.attributes.getOrNull(ROTA) ?: return "desconhecida"
    val modelo = rota.split('/').filter { it.isNotEmpty() && !it.startsWith("(") }.joinToString("/", prefix = "/")
    return if (modelo == "/") "desconhecida" else modelo
}
