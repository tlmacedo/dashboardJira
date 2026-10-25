package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Política de novas tentativas. O `delay` é injetável para os testes não dormirem de verdade.
 * O backoff recebe o número da tentativa já feita (0 = após a primeira falha).
 */
class RetryPolicy(
    val maxRetries: Int = 5,
    val backoff: (Int) -> Duration = { tentativa -> minOf(30.seconds, 1.seconds * (1 shl tentativa.coerceIn(0, 5))) },
    val delay: suspend (Duration) -> Unit = { kotlinx.coroutines.delay(it) },
)

/** Cria o cliente HTTP real (CIO + JSON). Nunca é usado nos testes, que injetam o MockEngine. */
fun createJiraHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json() }
    // Nunca seguir redirecionamentos: a credencial não pode ir a outro host.
    followRedirects = false
}

/**
 * Cliente de leitura do Jira Data Center. Toda requisição passa antes por `LiveGuard.check()`.
 */
class JiraClient(
    private val config: JiraInstanceConfig,
    http: HttpClient,
    private val guard: LiveGuard,
    private val retry: RetryPolicy = RetryPolicy(),
) {
    // Mesmo que o cliente injetado siga redirecionamentos, aqui eles ficam desligados.
    private val http: HttpClient = http.config { followRedirects = false }
    private val baseUrl = config.baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    private val authorization: String = when (config.auth) {
        JiraAuthKind.BEARER -> "Bearer ${config.token}"
        JiraAuthKind.BASIC -> {
            val usuario = requireNotNull(config.username) { "BASIC exige username" }
            "Basic " + Base64.getEncoder().encodeToString("$usuario:${config.token}".toByteArray())
        }
    }

    suspend fun search(req: SearchRequest): SearchPage {
        // Validação antes do guard: pedido inválido nunca chega nem a consultar a trava.
        require(req.fields.isNotEmpty()) { "fields não pode ser vazio" }
        val corpo = buildJsonObject {
            put("jql", req.jql)
            put("maxResults", req.maxResults)
            put("startAt", req.startAt)
            put("fields", JsonArray(req.fields.map { JsonPrimitive(it) }))
            if (req.expandChangelog) put("expand", JsonArray(listOf(JsonPrimitive("changelog"))))
        }.toString()

        val raiz = requisitarJson(HttpMethod.Post, "$baseUrl/rest/api/2/search", corpo)
        return SearchPage(
            total = raiz["total"]?.jsonPrimitive?.int ?: 0,
            startAt = raiz["startAt"]?.jsonPrimitive?.int ?: 0,
            issues = raiz["issues"]?.jsonArray?.map { it.jsonObject } ?: emptyList(),
        )
    }

    /**
     * Percorre todas as páginas de uma busca (requisições sequenciais por `startAt`) e emite cada issue.
     * `pageSize` vai de 1 a 5000; fora disso falha na hora, antes de qualquer requisição.
     * Para ao atingir o `total` ou ao receber uma página vazia (evita laço infinito).
     */
    fun searchAll(req: SearchRequest, pageSize: Int = 1000): Flow<JsonObject> {
        require(pageSize in 1..PAGE_SIZE_MAX) { "pageSize deve ficar entre 1 e $PAGE_SIZE_MAX" }
        return flow {
            var inicio = req.startAt
            while (true) {
                val pagina = search(req.copy(startAt = inicio, maxResults = pageSize))
                pagina.issues.forEach { emit(it) }
                if (pagina.issues.isEmpty()) break
                inicio += pagina.issues.size
                if (inicio >= pagina.total) break
            }
        }
    }

    /** Conta as issues de um JQL sem baixá-las: pede só 1 resultado e lê o `total`. */
    suspend fun count(jql: String): Int =
        search(SearchRequest(jql = jql, fields = listOf("key"), maxResults = 1)).total

    /**
     * Histórico completo de uma issue via GET `/rest/api/2/issue/{key}/changelog`, paginado.
     * A chave vem de dado externo: é validada e o segmento do caminho é codificado.
     */
    suspend fun fullChangelog(issueKey: String): List<JsonObject> {
        require(CHAVE_ISSUE.matches(issueKey)) { "Chave de issue inválida" }
        val segmento = issueKey.encodeURLPathPart()
        val historicos = mutableListOf<JsonObject>()
        var inicio = 0
        while (true) {
            val url = "$baseUrl/rest/api/2/issue/$segmento/changelog?startAt=$inicio&maxResults=$CHANGELOG_PAGINA"
            val raiz = requisitarJson(HttpMethod.Get, url, null)
            val valores = raiz["values"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            historicos += valores
            if (valores.isEmpty()) break
            inicio += valores.size
            val total = raiz["total"]?.jsonPrimitive?.int ?: break
            if (inicio >= total) break
        }
        return historicos
    }

    /** Único caminho HTTP do cliente: guard + retry antes de cada requisição, erro mascarado, corpo em JSON. */
    private suspend fun requisitarJson(metodo: HttpMethod, url: String, corpo: String?): JsonObject {
        val resposta = executarComRetry { enviar(metodo, url, corpo) }
        val texto = resposta.bodyAsText()
        if (!resposta.status.isSuccess2xx()) throw erro(resposta.status.value, texto)
        return json.parseToJsonElement(texto).jsonObject
    }

    private fun HttpStatusCode.isSuccess2xx() = value in 200..299

    private suspend fun enviar(metodo: HttpMethod, url: String, corpo: String?): HttpResponse =
        try {
            withTimeout(TIMEOUT) {
                http.request(url) {
                    method = metodo
                    header(HttpHeaders.Authorization, authorization)
                    accept(ContentType.Application.Json)
                    if (corpo != null) setBody(TextContent(corpo, ContentType.Application.Json))
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw JiraException(0, "Tempo esgotado (${TIMEOUT.inWholeSeconds}s) ao consultar o Jira", null)
        }

    /** Repete em 429/502/503/504; o guard é consultado antes de cada tentativa. */
    private suspend fun executarComRetry(chamada: suspend () -> HttpResponse): HttpResponse {
        var tentativa = 0
        while (true) {
            guard.check()
            val resposta = chamada()
            val repetivel = resposta.status.value in STATUS_REPETIVEIS
            if (!repetivel || tentativa >= retry.maxRetries) return resposta
            val espera = resposta.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
                ?.coerceIn(0, RETRY_AFTER_MAX_SEGUNDOS)?.seconds
                ?: retry.backoff(tentativa)
            retry.delay(espera)
            tentativa++
        }
    }

    /** Monta a exceção com o status e um resumo do corpo (sem quebras de linha, até 300 caracteres). */
    private fun erro(status: Int, corpo: String): JiraException {
        val limpo = mascarar(corpo)
        val resumo = resumir(limpo)
        val mensagens = extrairMensagens(limpo)
        val prefixo = if (status in 300..399) {
            "Jira respondeu $status (redirecionamento não seguido, verifique a URL base)"
        } else {
            "Jira respondeu $status"
        }
        val msg = prefixo + (mensagens ?: resumo)?.let { ": $it" }.orEmpty()
        return JiraException(status, msg, resumo)
    }

    private fun extrairMensagens(corpo: String): String? = try {
        val o = json.parseToJsonElement(corpo).jsonObject
        val lista = (o["errorMessages"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty() +
            ((o["errors"] as? JsonObject)?.values?.map { it.jsonPrimitive.content }.orEmpty())
        lista.takeIf { it.isNotEmpty() }?.let { resumir(it.joinToString("; ")) }
    } catch (e: Exception) {
        null
    }

    /** Troca o token e o base64 do Basic por `***` antes de qualquer texto sair do cliente. */
    private fun mascarar(texto: String): String {
        var r = texto
        if (config.auth == JiraAuthKind.BASIC) r = r.replace(authorization.removePrefix("Basic "), "***")
        if (config.token.isNotEmpty()) r = r.replace(config.token, "***")
        return r
    }

    private fun resumir(texto: String): String? =
        texto.replace(Regex("\\s+"), " ").trim().take(300).ifEmpty { null }

    private companion object {
        val TIMEOUT = 30.seconds
        const val PAGE_SIZE_MAX = 5000
        const val CHANGELOG_PAGINA = 100
        val CHAVE_ISSUE = Regex("[A-Z][A-Z0-9_]*-\\d+")
        // Teto para Retry-After: um valor absurdo do servidor não pode travar o job.
        const val RETRY_AFTER_MAX_SEGUNDOS = 120L
        val STATUS_REPETIVEIS = setOf(429, 502, 503, 504)
    }
}
