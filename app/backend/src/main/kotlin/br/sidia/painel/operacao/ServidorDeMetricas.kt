package br.sidia.painel.operacao

import br.sidia.painel.sync.RunStatus
import br.sidia.painel.sync.SyncKind
import br.sidia.painel.sync.SyncStateStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.util.AttributeKey
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("br.sidia.painel.operacao.Metricas")
private val INICIO_DA_MEDICAO = AttributeKey<Long>("painel.inicioDaMedicaoHttp")

/**
 * Aplicação da PORTA INTERNA de métricas: só `GET /metrics`; qualquer outro caminho ou método responde 404. É um servidor
 * separado do painel (outra porta, outro módulo), então `/metrics` não existe na porta pública. Sem autenticação: quem
 * alcança a porta interna (host `127.0.0.1` por padrão) é o coletor; abri-la para a rede é decisão do Plano 13 e
 * exige firewall só para o coletor.
 */
fun Application.servidorDeMetricas(metricas: Metricas) {
    routing {
        get("/metrics") {
            call.respondText(metricas.texto(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
        }
    }
}

/**
 * Sobe o servidor de métricas em [host]:[porta] (a porta 0 o desliga) e devolve quem o fecha. Se a porta estiver em uso
 * ou o host for inválido, avisa (só a classe da exceção) e o painel segue sem métricas: a observação nunca derruba a produção.
 */
fun iniciarServidorDeMetricas(metricas: Metricas, host: String, porta: Int): AutoCloseable? {
    if (porta == 0) return null
    return try {
        val servidor = embeddedServer(Netty, port = porta, host = host) { servidorDeMetricas(metricas) }.start(wait = false)
        log.info("Métricas na porta interna {}", porta)
        AutoCloseable { servidor.stop(200, 1000) }
    } catch (e: Exception) {
        log.warn("Servidor de métricas não subiu ({}); o painel segue sem ele.", e.javaClass.simpleName)
        null
    }
}

/**
 * Plugin da porta pública: conta cada requisição (`painel_http_requisicoes_total`) e mede a duração, com a rota pelo
 * MODELO capturado em [instalarLogDeRequisicao] (por isso este plugin vem depois dele). Também fecha o `/metrics`
 * público: a casca estática poderia responder 200 com a página inicial para um caminho desconhecido.
 */
fun Application.instalarMetricasHttp(metricas: Metricas) {
    routing { route("/metrics") { handle { call.respond(HttpStatusCode.NotFound) } } }
    install(
        createApplicationPlugin("PainelMetricasHttp") {
            onCall { call -> call.attributes.put(INICIO_DA_MEDICAO, System.nanoTime()) }
            on(ResponseSent) { call ->
                val inicio = call.attributes.getOrNull(INICIO_DA_MEDICAO) ?: return@on
                metricas.requisicaoHttp(
                    modeloDaRota(call), call.request.httpMethod.value, call.response.status()?.value ?: 0,
                    (System.nanoTime() - inicio) / 1_000_000_000.0,
                )
            }
        },
    )
}

/**
 * Decorador do estado da sincronização que conta cada execução concluída em `painel_sync_execucoes_total`. Só
 * observa: toda a lógica continua no [SyncStateStore] real.
 */
class EstadoComMetricas(private val real: SyncStateStore, private val metricas: Metricas) : SyncStateStore by real {
    private val abertas = ConcurrentHashMap<Long, Pair<String, SyncKind>>()

    override fun iniciar(instance: String, kind: SyncKind, at: java.time.Instant): Long =
        real.iniciar(instance, kind, at).also { abertas[it] = instance to kind }

    override fun concluir(
        runId: Long,
        status: RunStatus,
        resumo: br.sidia.painel.sync.RunResumo,
        codigoErro: String?,
        httpStatus: Int?,
        at: java.time.Instant,
    ): Boolean {
        val concluiu = real.concluir(runId, status, resumo, codigoErro, httpStatus, at)
        val origem = abertas.remove(runId)
        if (concluiu && origem != null) metricas.execucaoDeSync(origem.first, origem.second, status)
        return concluiu
    }
}
