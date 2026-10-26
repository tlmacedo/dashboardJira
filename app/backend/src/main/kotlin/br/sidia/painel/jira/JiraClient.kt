package br.sidia.painel.jira

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
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
import br.sidia.painel.jql.JqlQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
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

/**
 * Expressão que casa o [segredo] cru ou com QUALQUER caractere escrito de forma escapada:
 * JSON (`\/`, `\"`, `\\`, `\u00XX` em minúsculas e maiúsculas), URL (`%XX` em ambas as caixas) e
 * HTML (`&#43;`, `&#x2b;`, `&#x2B;`). A escolha é por caractere, então formas misturadas também casam.
 */
internal fun padraoEscapado(segredo: String): Regex {
    val sb = StringBuilder()
    for (c in segredo) {
        val alternativas = linkedSetOf(Regex.escape(c.toString()))
        val codigo = c.code
        val hexMin = "%04x".format(codigo)
        val hexMai = "%04X".format(codigo)
        alternativas += Regex.escape("\\u$hexMin")
        alternativas += Regex.escape("\\u$hexMai")
        when (c) {
            '/' -> alternativas += Regex.escape("\\/")
            '"' -> alternativas += Regex.escape("\\\"")
            '\\' -> alternativas += Regex.escape("\\\\")
        }
        // Entidades HTML nomeadas dos caracteres que o HTML escapa (token que contém & < > " ').
        when (c) {
            '&' -> alternativas += Regex.escape("&amp;")
            '<' -> alternativas += Regex.escape("&lt;")
            '>' -> alternativas += Regex.escape("&gt;")
            '"' -> alternativas += Regex.escape("&quot;")
            '\'' -> alternativas += Regex.escape("&apos;")
        }
        if (codigo < 0x100) {
            alternativas += Regex.escape("%" + "%02x".format(codigo))
            alternativas += Regex.escape("%" + "%02X".format(codigo))
        }
        alternativas += Regex.escape("&#$codigo;")
        alternativas += Regex.escape("&#x" + "%x".format(codigo) + ";")
        alternativas += Regex.escape("&#x" + "%X".format(codigo) + ";")
        alternativas += Regex.escape("&#X" + "%x".format(codigo) + ";")
        alternativas += Regex.escape("&#X" + "%X".format(codigo) + ";")
        sb.append("(?:").append(alternativas.joinToString("|")).append(')')
    }
    return Regex(sb.toString())
}

/**
 * Cria o cliente HTTP real (CIO). Nunca é usado nos testes, que injetam o MockEngine.
 * `internal`: só a [JiraClientFactory] a usa.
 */
internal fun createJiraHttpClient(): HttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    // Nunca seguir redirecionamentos: a credencial não pode ir a outro host.
    followRedirects = false
}

/**
 * Cliente de leitura do Jira Data Center. Toda requisição passa antes por `LiveGuard.check()`.
 *
 * O construtor é `internal`: em produção só a [JiraClientFactory] cria clientes. O `internal` só protege
 * de outros módulos; dentro deste, o teste `SomenteAFabricaCriaClientesJira` varre `src/main` e garante isso.
 */
class JiraClient internal constructor(
    private val config: JiraInstanceConfig,
    http: HttpClient,
    private val guard: LiveGuard,
    private val retry: RetryPolicy = RetryPolicy(),
) {
    // Cliente original (dono do motor) e derivado sem redirecionamentos: o derivado NÃO gerencia o motor,
    // por isso o fechamento precisa fechar os dois.
    private val httpOrigem: HttpClient = http

    // Mesmo que o cliente injetado siga redirecionamentos, aqui eles ficam desligados.
    private val http: HttpClient = http.config { followRedirects = false }

    /**
     * Fecha os recursos HTTP deste cliente. `internal`: só a [JiraClientFactory] o chama.
     * Fecha o derivado e depois o original; se o primeiro falhar, o segundo ainda é fechado
     * e o primeiro erro é relançado (o segundo vai como suprimido).
     */
    internal fun close() {
        var erro: Throwable? = null
        for (cliente in listOf(http, httpOrigem)) {
            try {
                cliente.close()
            } catch (e: Throwable) {
                if (erro == null) erro = e else erro.addSuppressed(e)
            }
        }
        erro?.let { throw it }
    }
    private val baseUrl = config.baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    private val authorization: String = when (config.auth) {
        JiraAuthKind.BEARER -> "Bearer ${config.token}"
        JiraAuthKind.BASIC -> {
            val usuario = requireNotNull(config.username) { "BASIC exige username" }
            "Basic " + Base64.getEncoder().encodeToString("$usuario:${config.token}".toByteArray())
        }
    }

    /** Busca uma página (até 100 itens) a partir de uma [JqlQuery]; a instância da consulta deve ser a do cliente. */
    suspend fun search(query: JqlQuery): SearchPage = search(paraRequisicao(query))

    /**
     * Percorre todas as páginas de uma [JqlQuery] e emite cada issue. `pageSize` vai de 1 a 5000 e
     * `maxItens` de 1 a 200.000; fora disso falha na hora, antes de qualquer requisição.
     */
    fun searchAll(query: JqlQuery, pageSize: Int = 1000, maxItens: Int = MAX_ITENS_PADRAO): Flow<JsonObject> =
        searchAll(paraRequisicao(query), pageSize, maxItens)

    /** Conta as issues de uma [JqlQuery] sem baixá-las. */
    suspend fun count(query: JqlQuery): Int = count(paraRequisicao(query).jql)

    /** Exige a mesma instância do cliente; a mensagem cita só os ids (nunca o JQL). */
    private fun paraRequisicao(query: JqlQuery): SearchRequest {
        require(query.instance == config.id) {
            "A consulta é da instância '${query.instance}', mas o cliente é da instância '${config.id}'"
        }
        return SearchRequest(jql = query.jql, fields = query.fields, expandChangelog = query.expandChangelog)
    }

    internal suspend fun search(req: SearchRequest): SearchPage {
        // Validação antes do guard: pedido inválido nunca chega nem a consultar a trava.
        require(req.fields.isNotEmpty()) { "fields não pode ser vazio" }
        val corpo = buildJsonObject {
            put("jql", req.jql)
            put("maxResults", req.maxResults)
            put("startAt", req.startAt)
            put("fields", JsonArray(req.fields.map { JsonPrimitive(it) }))
            if (req.expandChangelog) put("expand", JsonArray(listOf(JsonPrimitive("changelog"))))
        }.toString()

        val resposta = requisitarJson(HttpMethod.Post, "$baseUrl/rest/api/2/search", corpo)
        val raiz = resposta.raiz
        return SearchPage(
            total = exigirTotal(resposta),
            startAt = (raiz["startAt"] as? JsonPrimitive)?.intOrNull ?: 0,
            issues = (raiz["issues"] as? JsonArray)?.map { it as? JsonObject ?: throw respostaInesperada(resposta.status) }
                ?: emptyList(),
        )
    }

    /** O `total` é obrigatório: sem ele não dá para saber se a lista está completa (nunca assume 0). */
    private fun exigirTotal(resposta: RespostaJson): Int =
        (resposta.raiz["total"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
            ?: throw JiraException(resposta.status, "Resposta do Jira sem o campo 'total' numérico e não negativo", null)

    private fun respostaInesperada(status: Int) =
        JiraException(status, "Resposta do Jira com formato inesperado", null)

    /**
     * Percorre todas as páginas de uma busca (requisições sequenciais por `startAt`) e emite cada issue.
     * `pageSize` vai de 1 a 5000 e `maxItens` de 1 a 200.000; fora disso falha na hora, antes de qualquer requisição.
     * Para ao atingir o `total` ou ao receber uma página vazia (evita laço infinito).
     * Teto de segurança: se o `total` informado pelo Jira (ou a soma dos itens recebidos) passar de `maxItens`,
     * lança [JiraException] antes de emitir a página excedente; a mensagem cita só o limite e o total, nunca o JQL.
     * `fields` vazio falha já nesta chamada.
     */
    internal fun searchAll(req: SearchRequest, pageSize: Int = 1000, maxItens: Int = MAX_ITENS_PADRAO): Flow<JsonObject> {
        require(pageSize in 1..PAGE_SIZE_MAX) { "pageSize deve ficar entre 1 e $PAGE_SIZE_MAX" }
        require(maxItens in 1..MAX_ITENS_LIMITE) { "maxItens deve ficar entre 1 e $MAX_ITENS_LIMITE" }
        require(req.fields.isNotEmpty()) { "fields não pode ser vazio" }
        return flow {
            var inicio = req.startAt
            var emitidos = 0
            while (true) {
                val pagina = search(req.copy(startAt = inicio, maxResults = pageSize))
                if (pagina.total > maxItens || emitidos + pagina.issues.size > maxItens) {
                    throw TetoDeItensExcedidoException(maxItens, pagina.total)
                }
                pagina.issues.forEach { emit(it) }
                emitidos += pagina.issues.size
                if (pagina.issues.isEmpty()) break
                inicio += pagina.issues.size
                if (inicio >= pagina.total) break
            }
        }
    }

    /** Conta as issues de um JQL sem baixá-las: pede só 1 resultado e lê o `total`. */
    internal suspend fun count(jql: String): Int =
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
        var paginas = 0
        // Mesmo teto do searchAll: MAX_ITENS / tamanho da página, no mínimo 1.
        val maxPaginas = maxOf(1, (MAX_ITENS + CHANGELOG_PAGINA - 1) / CHANGELOG_PAGINA)
        while (true) {
            val url = "$baseUrl/rest/api/2/issue/$segmento/changelog?startAt=$inicio&maxResults=$CHANGELOG_PAGINA"
            val resposta = requisitarJson(HttpMethod.Get, url, null)
            val total = exigirTotal(resposta)
            val valores = (resposta.raiz["values"] as? JsonArray)
                ?.map { it as? JsonObject ?: throw respostaInesperada(resposta.status) }.orEmpty()
            historicos += valores
            if (valores.isEmpty()) break
            inicio += valores.size
            paginas++
            if (inicio >= total) break
            if (paginas >= maxPaginas) {
                throw JiraException(0, "Changelog excedeu o teto de $maxPaginas páginas", null)
            }
        }
        return historicos
    }

    /** Único caminho HTTP do cliente: guard + retry antes de cada requisição, erro mascarado, corpo em JSON. */
    private suspend fun requisitarJson(metodo: HttpMethod, url: String, corpo: String?): RespostaJson {
        val resposta = executarComRetry { enviar(metodo, url, corpo) }
        val status = resposta.status.value
        val texto = try {
            resposta.bodyAsText()
        } catch (e: Exception) {
            repassarOuEmbrulhar(e)
            throw JiraException(0, "Falha de rede ao ler a resposta do Jira (${e::class.simpleName})", null)
        }
        if (!resposta.status.isSuccess2xx()) throw erro(status, texto)
        val raiz = try {
            json.parseToJsonElement(texto).jsonObject
        } catch (e: IllegalArgumentException) {
            // SerializationException é IllegalArgumentException; .jsonObject também falha assim (ex.: página de login).
            val resumo = resumir(mascarar(texto))
            throw JiraException(
                status,
                "Jira respondeu $status com conteúdo que não é JSON válido" + resumo?.let { ": $it" }.orEmpty(),
                resumo,
            )
        }
        return RespostaJson(status, raiz)
    }

    private class RespostaJson(val status: Int, val raiz: JsonObject)

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
            // Se o cancelamento veio de um escopo externo (inclusive withTimeout do chamador), propaga.
            currentCoroutineContext().ensureActive()
            // Só o timeout PRÓPRIO chega aqui: tratado como falha de rede (mensagem sem URL nem token).
            throw IOException("Tempo esgotado (${TIMEOUT.inWholeSeconds}s) ao consultar o Jira")
        }

    /**
     * Cancelamento do chamador e exceções do próprio cliente passam intactos. Falha de rede retorna
     * normalmente (o retry decide). Qualquer outra exceção vira `JiraException(0)` SEM causa e SEM a
     * mensagem original: o Ktor pode incluir o cabeçalho Authorization ou a URL completa nela.
     */
    private suspend fun repassarOuEmbrulhar(e: Exception) {
        if (e is CancellationException) throw e
        currentCoroutineContext().ensureActive()
        if (e is JiraException || e is JiraLiveDisabledException) throw e
        if (ehFalhaDeRede(e)) return
        throw JiraException(0, "Falha inesperada (${e::class.simpleName ?: "desconhecido"})", null)
    }

    private fun ehFalhaDeRede(e: Throwable) =
        e is IOException || e is HttpRequestTimeoutException ||
            e is ConnectTimeoutException || e is SocketTimeoutException

    /**
     * Repete em 429/502/503/504 e em falhas de rede (IOException, timeouts); o guard é consultado antes
     * de CADA tentativa. Esgotadas as tentativas de rede, vira `JiraException(status = 0)` sem URL nem token.
     */
    private suspend fun executarComRetry(chamada: suspend () -> HttpResponse): HttpResponse {
        var tentativa = 0
        while (true) {
            guard.check()
            // Orçamento do trecho (se houver): conta cada tentativa, inclusive as do retry.
            currentCoroutineContext()[OrcamentoDeRequisicoes]?.consumir()
            val resposta = try {
                chamada()
            } catch (e: Exception) {
                repassarOuEmbrulhar(e)
                if (tentativa >= retry.maxRetries) {
                    // Só o tipo da falha: a mensagem original pode conter a URL ou o token.
                    throw JiraException(
                        0,
                        "Falha de rede ao consultar o Jira (${e::class.simpleName}) após ${tentativa + 1} tentativas",
                        null,
                    )
                }
                retry.delay(retry.backoff(tentativa))
                tentativa++
                continue
            }
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
        // Mascara de novo a mensagem: ela vem de texto já decodificado do JSON (defesa em profundidade).
        val msg = mascarar(prefixo + (mensagens ?: resumo)?.let { ": $it" }.orEmpty())
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

    /**
     * Padrões do segredo (base64 do Basic e token), em texto cru e nas formas escapadas por JSON, URL e HTML.
     * Calculados uma vez. O Basic vem primeiro: o base64 é mais longo e não contém o token cru.
     */
    private val padroesSecretos: List<Regex> = buildList {
        if (config.auth == JiraAuthKind.BASIC) add(padraoEscapado(authorization.removePrefix("Basic ")))
        if (config.token.isNotEmpty()) add(padraoEscapado(config.token))
    }

    /** Troca o token e o base64 do Basic (inclusive escapados) por `***` antes de qualquer texto sair do cliente. */
    private fun mascarar(texto: String): String {
        var r = texto
        for (p in padroesSecretos) r = p.replace(r, "***")
        return r
    }

    private fun resumir(texto: String): String? =
        texto.replace(Regex("\\s+"), " ").trim().take(300).ifEmpty { null }

    private companion object {
        val TIMEOUT = 30.seconds
        const val PAGE_SIZE_MAX = 5000
        // Limite padrão e máximo de itens de um searchAll (parâmetro `maxItens`).
        const val MAX_ITENS_PADRAO = 50_000
        const val MAX_ITENS_LIMITE = 200_000
        // Teto do changelog de uma issue (vira o número máximo de páginas de 100).
        const val MAX_ITENS = 10_000
        const val CHANGELOG_PAGINA = 100
        val CHAVE_ISSUE = Regex("[A-Z][A-Z0-9_]*-\\d+")
        // Teto para Retry-After: um valor absurdo do servidor não pode travar o job.
        const val RETRY_AFTER_MAX_SEGUNDOS = 120L
        val STATUS_REPETIVEIS = setOf(429, 502, 503, 504)
    }
}
