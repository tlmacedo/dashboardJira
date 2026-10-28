package br.sidia.painel.http

import br.sidia.painel.AppContext
import br.sidia.painel.cards.rotaParaRegistro
import br.sidia.painel.db.AccessLog
import br.sidia.painel.security.principalOuNulo
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Cabeçalho que só o código do painel manda (um formulário de outro site não consegue pôr cabeçalho próprio). */
const val CABECALHO_CSRF = "X-Painel-CSRF"

/** Códigos de erro desta proteção (texto na tela vem do código). */
const val CSRF_AUSENTE = "CSRF_AUSENTE"
const val ORIGEM_INVALIDA = "ORIGEM_INVALIDA"

private const val ID_ANONIMO = "anonymous"

private val log = LoggerFactory.getLogger("br.sidia.painel.http.ProtecaoCsrf")

/** [origemPublica]: `DASHBOARD_ORIGEM_PUBLICA` já normalizada; nula = compara com o `Host` da requisição. */
data class ConfigCsrf(val origemPublica: String?)

/** Tudo que altera estado: qualquer método que não seja GET, HEAD nem OPTIONS. */
fun metodoMutante(m: HttpMethod): Boolean =
    m != HttpMethod.Get && m != HttpMethod.Head && m != HttpMethod.Options

/**
 * Proteção CSRF para todo método mutante, em qualquer caminho (a API, a autenticação e os que vierem):
 * 1. sem `X-Painel-CSRF: 1` responde 403 `CSRF_AUSENTE`;
 * 2. `Sec-Fetch-Site` presente e diferente de `same-origin` e `none` responde 403 `ORIGEM_INVALIDA`;
 * 3. `Origin` presente (inclusive o texto `null`) e diferente da origem esperada responde 403 `ORIGEM_INVALIDA`;
 * 4. senão segue para o handler.
 *
 * O handler NUNCA roda quando negado. Sem CORS: o painel não envia `Access-Control-Allow-*`, então nenhum site externo
 * consegue mandar o cabeçalho próprio. Instalar DEPOIS do controle de acesso e do `ContentNegotiation`.
 */
fun protecaoCsrf(cfg: ConfigCsrf, aoNegar: suspend (ApplicationCall, codigo: String) -> Unit): ApplicationPlugin<Unit> =
    createApplicationPlugin("ProtecaoCsrf") {
        application.intercept(ApplicationCallPipeline.Plugins) {
            if (!metodoMutante(call.request.local.method)) return@intercept
            val codigo = motivoDaNegacao(call, cfg) ?: return@intercept
            try {
                aoNegar(call, codigo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A auditoria nunca impede a negação. Só o tipo da exceção vai para o log.
                log.error("Falha ao registrar a negação CSRF ({})", e.javaClass.simpleName)
            }
            call.respond(HttpStatusCode.Forbidden, ErroDto(codigo))
            finish()
        }
    }

/** `null` = a chamada pode seguir. */
private fun motivoDaNegacao(call: ApplicationCall, cfg: ConfigCsrf): String? {
    val cab = call.request.headers
    if (cab[CABECALHO_CSRF]?.trim() != "1") return CSRF_AUSENTE
    val secFetch = cab["Sec-Fetch-Site"]?.trim()?.lowercase()
    if (secFetch != null && secFetch != "same-origin" && secFetch != "none") return ORIGEM_INVALIDA
    val origem = cab[HttpHeaders.Origin]?.trim()
    if (origem != null && normalizarOrigem(origem) != origemEsperada(call, cfg)) return ORIGEM_INVALIDA
    return null
}

private fun origemEsperada(call: ApplicationCall, cfg: ConfigCsrf): String {
    cfg.origemPublica?.let { return normalizarOrigem(it) }
    val host = call.request.headers[HttpHeaders.Host]?.trim()?.takeIf { it.isNotEmpty() }
        ?: call.request.local.let { "${it.serverHost}:${it.serverPort}" }
    return normalizarOrigem("${call.request.local.scheme}://$host")
}

/** Minúsculas, sem barra final e sem a porta padrão do esquema (`:80` em http, `:443` em https). */
private fun normalizarOrigem(bruta: String): String {
    val o = bruta.trim().lowercase().trimEnd('/')
    return when {
        o.startsWith("http://") && o.endsWith(":80") -> o.removeSuffix(":80")
        o.startsWith("https://") && o.endsWith(":443") -> o.removeSuffix(":443")
        else -> o
    }
}

/**
 * Grava a negação em `access_log` (rota sem query string; filtro `motivo`; nunca o corpo). Sem principal resolvido o
 * usuário é `anonymous`. Falha ao gravar é registrada (só o tipo da exceção) e não impede o 403.
 */
suspend fun registrarNegacaoCsrf(auditoria: AccessLog, ctx: AppContext, call: ApplicationCall, codigo: String) {
    try {
        withContext(Dispatchers.IO) {
            auditoria.record(
                call.principalOuNulo()?.userId?.takeIf { it.isNotBlank() } ?: ID_ANONIMO,
                rotaParaRegistro(call.request.path()),
                mapOf("motivo" to codigo),
                ctx.clock.instant(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.error("Falha ao registrar a negação CSRF no access_log ({})", e.javaClass.simpleName)
    }
}
