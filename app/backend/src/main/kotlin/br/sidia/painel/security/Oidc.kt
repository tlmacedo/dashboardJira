package br.sidia.painel.security

import br.sidia.painel.db.AccessLog
import br.sidia.painel.http.ErroDto
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val log = LoggerFactory.getLogger("br.sidia.painel.security.Oidc")

/** Cookie da sessão do painel (assinado por [SessaoAssinada]): `HttpOnly`, `Secure`, `SameSite=Lax`. */
const val COOKIE_DE_SESSAO = "painel_sessao"

/** Cookie de vida curta que liga o `/auth/login` ao `/auth/callback` (guarda `state`, `nonce` e o verificador PKCE). */
const val COOKIE_DE_LOGIN = "painel_login"

/** O provedor não respondeu como esperado (rede, status, formato ou descoberta incoerente). Só um código, nunca o corpo. */
class FalhaNoProvedor(val codigo: String, val recusouOCodigo: Boolean = false) : Exception(codigo)

/** Endereços da descoberta (`/.well-known/openid-configuration`), já conferidos (`issuer` igual e `https`). */
internal class Descoberta(val autorizacao: String, val token: String, val jwks: String)

/**
 * Conversa com o provedor OIDC pela interface do cliente HTTP injetado: descoberta, JWKS e troca do código. Guarda a
 * descoberta e o JWKS em memória; uma chave desconhecida (`kid` novo) refaz a busca do JWKS, no máximo uma vez por minuto
 * (rotação de chaves do provedor sem permitir que token forjado force requisição a cada chamada).
 */
internal class ProvedorOidc(
    private val cfg: OidcSettings,
    private val http: HttpClient,
    private val relogio: Clock,
    private val validadeDaDescoberta: Duration = Duration.ofHours(1),
    private val intervaloMinimoDoJwks: Duration = Duration.ofMinutes(1),
) {
    private val trava = Mutex()
    private var descoberta: Descoberta? = null
    private var descobertaEm: Instant = Instant.MIN
    private var falha: String? = null
    private var falhaEm: Instant = Instant.MIN
    private var jwks: ChavesPublicas? = null
    private var jwksEm: Instant = Instant.MIN

    private suspend fun lerJson(url: String, codigo: String): JsonObject {
        val resposta = try {
            http.get(url) { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FalhaNoProvedor(codigo)
        }
        if (!resposta.status.isSuccess()) throw FalhaNoProvedor(codigo)
        return corpoJson(resposta, codigo)
    }

    private suspend fun corpoJson(resposta: HttpResponse, codigo: String): JsonObject =
        try {
            val bytes = resposta.bodyAsChannel().readRemaining(LIMITE_DO_CORPO + 1).readByteArray()
            if (bytes.size > LIMITE_DO_CORPO) throw FalhaNoProvedor(codigo)
            Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: FalhaNoProvedor) {
            throw e
        } catch (e: Exception) {
            throw FalhaNoProvedor(codigo)
        }

    private fun JsonObject.url(nome: String, codigo: String): String {
        val valor = (this[nome] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (valor == null || !valor.startsWith("https://")) throw FalhaNoProvedor(codigo)
        // O valor nunca vai para a mensagem: só o código.
        if (!OidcSettings.endpointPermitido(valor, cfg.hostsPermitidos)) throw FalhaNoProvedor("DESCOBERTA_ENDPOINT_NAO_PERMITIDO")
        return valor
    }

    suspend fun descoberta(): Descoberta = trava.withLock {
        val agora = relogio.instant()
        descoberta?.takeIf { agora.isBefore(descobertaEm.plus(validadeDaDescoberta)) }?.let { return it }
        // Cache negativo: provedor fora do ar não é consultado a cada /auth/login (cada tentativa espera o tempo-limite).
        falha?.takeIf { agora.isBefore(falhaEm.plus(VALIDADE_DA_FALHA)) }?.let { throw FalhaNoProvedor(it) }
        try {
            val corpo = lerJson(cfg.issuer.trimEnd('/') + "/.well-known/openid-configuration", "DESCOBERTA_FALHOU")
            // O emissor declarado tem de ser o configurado: protege contra um documento de descoberta de outro emissor.
            if ((corpo["issuer"] as? JsonPrimitive)?.takeIf { it.isString }?.content != cfg.issuer) {
                throw FalhaNoProvedor("DESCOBERTA_EMISSOR_DIFERENTE")
            }
            val nova = Descoberta(
                corpo.url("authorization_endpoint", "DESCOBERTA_INVALIDA"),
                corpo.url("token_endpoint", "DESCOBERTA_INVALIDA"),
                corpo.url("jwks_uri", "DESCOBERTA_INVALIDA"),
            )
            descoberta = nova
            descobertaEm = agora
            falha = null
            nova
        } catch (e: FalhaNoProvedor) {
            falha = e.codigo
            falhaEm = agora
            throw e
        }
    }

    /** As chaves para validar um token cujo cabeçalho traz [kid]; refaz a busca se a chave não está no que se guardou. */
    suspend fun chaves(kid: String?): ChavesPublicas {
        val d = descoberta()
        return trava.withLock {
            val agora = relogio.instant()
            val atual = jwks
            val vencido = atual == null || !agora.isBefore(jwksEm.plus(validadeDaDescoberta))
            val faltaAChave = atual != null && atual.buscar(kid) == null && !agora.isBefore(jwksEm.plus(intervaloMinimoDoJwks))
            if (!vencido && !faltaAChave) return@withLock atual!!
            val novas = ChavesPublicas.deJson(
                lerJson(d.jwks, "JWKS_FALHOU").toString(),
            )
            if (novas.chaves.isEmpty()) throw FalhaNoProvedor("JWKS_SEM_CHAVES")
            jwks = novas
            jwksEm = agora
            novas
        }
    }

    suspend fun urlDeAutorizacao(state: String, nonce: String, desafio: String): String {
        val d = descoberta()
        return URLBuilder(d.autorizacao).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", cfg.clientId)
            parameters.append("redirect_uri", cfg.redirectUri)
            parameters.append("scope", cfg.escopos.joinToString(" "))
            parameters.append("state", state)
            parameters.append("nonce", nonce)
            parameters.append("code_challenge", desafio)
            parameters.append("code_challenge_method", "S256")
        }.buildString()
    }

    /** Troca o `code` (com o verificador PKCE) e devolve o `id_token`. */
    suspend fun trocarCodigo(code: String, verificador: String): String {
        val d = descoberta()
        val resposta = try {
            http.submitForm(
                url = d.token,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", cfg.redirectUri)
                    append("client_id", cfg.clientId)
                    append("code_verifier", verificador)
                    cfg.clientSecret?.let { append("client_secret", it) }
                },
            ) { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FalhaNoProvedor("TOKEN_FALHOU")
        }
        if (resposta.status == HttpStatusCode.BadRequest || resposta.status == HttpStatusCode.Unauthorized) {
            throw FalhaNoProvedor("CODIGO_RECUSADO", recusouOCodigo = true)
        }
        if (!resposta.status.isSuccess()) throw FalhaNoProvedor("TOKEN_FALHOU")
        val corpo = corpoJson(resposta, "TOKEN_FALHOU")
        return (corpo["id_token"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
            ?: throw FalhaNoProvedor("TOKEN_SEM_ID_TOKEN")
    }

    private companion object {
        const val LIMITE_DO_CORPO = 256 * 1024L
        val VALIDADE_DA_FALHA: Duration = Duration.ofSeconds(30)
    }
}

/** Estado de um login em andamento, guardado no cookie [COOKIE_DE_LOGIN] (assinado) e conferido no `/auth/callback`. */
internal class EstadoDeLogin(val state: String, val nonce: String, val verificador: String, val volta: String, val expiraEm: Instant)

/** Assina e lê o [COOKIE_DE_LOGIN]: `v1.<corpo em base64url>.<HMAC-SHA-256>`, vence em minutos e nunca vale com outro segredo. */
internal class CookieDeLogin(private val segredo: ByteArray, private val relogio: Clock) {
    fun emitir(estado: EstadoDeLogin): String {
        val corpo = listOf(estado.state, estado.nonce, estado.verificador, estado.volta, estado.expiraEm.epochSecond.toString())
            .joinToString("\n")
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(corpo.toByteArray(Charsets.UTF_8))
        return "v1.$b64.${assinar(b64)}"
    }

    fun ler(valor: String?): EstadoDeLogin? {
        val partes = valor?.split('.') ?: return null
        if (partes.size != 3 || partes[0] != "v1") return null
        val esperado = assinar(partes[1]).toByteArray(Charsets.US_ASCII)
        if (!MessageDigest.isEqual(partes[2].toByteArray(Charsets.US_ASCII), esperado)) return null
        val campos = try {
            String(Base64.getUrlDecoder().decode(partes[1]), Charsets.UTF_8).split('\n')
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (campos.size != 5) return null
        val fim = campos[4].toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() } ?: return null
        if (!relogio.instant().isBefore(fim)) return null
        return EstadoDeLogin(campos[0], campos[1], campos[2], campos[3], fim)
    }

    private fun assinar(corpo: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(segredo, "HmacSHA256")) }
                .doFinal(corpo.toByteArray(Charsets.US_ASCII)),
        )
}

/** Cada `state` só vale uma vez neste processo: repetir o `callback` (o mesmo `nonce`) é recusado. */
internal class ConsumoUnico(private val relogio: Clock, private val limite: Int = 10_000) {
    // Em ordem de inserção: o primeiro é o mais antigo.
    private val consumidos = LinkedHashMap<String, Instant>()

    /**
     * `true` na primeira vez; `false` se o `state` já foi consumido. Com o registro cheio, descarta primeiro os vencidos e,
     * se ainda estiver cheio, o MAIS ANTIGO: recusar quem chega deixaria um atacante que enche o registro com logins
     * anônimos bloquear os logins legítimos por 10 minutos. O custo (um `state` muito antigo e ainda vigente poder ser
     * reapresentado) exige antes um cookie de login assinado, o `code` do provedor e o `nonce` do token.
     */
    @Synchronized
    fun consumir(chave: String, validoAte: Instant): Boolean {
        if (consumidos.containsKey(chave)) return false
        if (consumidos.size >= limite) {
            val agora = relogio.instant()
            consumidos.entries.removeIf { !agora.isBefore(it.value) }
        }
        while (consumidos.size >= limite) {
            val mais = consumidos.entries.iterator()
            mais.next()
            mais.remove()
        }
        consumidos[chave] = validoAte
        return true
    }

    @Synchronized
    internal fun tamanho(): Int = consumidos.size
}

/** Resultado de concluir um login. */
internal sealed interface ResultadoDeLogin {
    class Ok(val usuario: String, val cookieDeSessao: String, val expiraEm: Instant, val volta: String) : ResultadoDeLogin
    class Recusado(val codigo: String, val status: HttpStatusCode) : ResultadoDeLogin
}

/**
 * SSO/OIDC genérico: o login por *authorization code* com PKCE, `state` e `nonce`, e a leitura da sessão a cada chamada.
 *
 * Os PAPÉIS nunca vêm do token: ele só diz QUEM é a pessoa (a claim [OidcSettings.claimUsername]); `principalFor` consulta o
 * `RoleDirectory` na data de hoje, a cada chamada, então encerrar a vigência de um papel vale na hora, mesmo com a sessão
 * aberta. Sem sessão válida o resultado é `null` (401); com sessão mas sem papel vigente, o controle de acesso responde 403.
 *
 * @param inicioDaRotacao quando a rotação do `SESSION_SECRET` começou (persistido por [InicioDaRotacao]); nulo = agora.
 */
class OidcAuthenticator(
    private val cfg: OidcSettings,
    private val roles: RoleDirectory,
    private val relogio: Clock,
    http: HttpClient,
    inicioDaRotacao: Instant? = null,
) : Authenticator {
    internal val provedor = ProvedorOidc(cfg, http, relogio)
    private val validador = ValidadorDeIdToken(cfg.issuer, cfg.clientId, cfg.claimUsername, relogio)
    private val cookieDeLogin = CookieDeLogin(cfg.sessao.atual, relogio)
    private val consumidos = ConsumoUnico(relogio)
    private val aleatorio = SecureRandom()

    internal val sessao = SessaoAssinada(
        cfg.sessao, cfg.duracaoDaSessao, inicioDaRotacao = inicioDaRotacao, agora = { relogio.instant() },
    )

    override suspend fun authenticate(call: ApplicationCall): Principal? {
        val leitura = leitura(call) ?: return null
        // Rotação do segredo: a sessão lida com o segredo anterior é regravada com o atual, sem estender o prazo.
        sessao.regravarSeNecessario(leitura)?.let { novo ->
            val restante = Duration.between(relogio.instant(), leitura.expiraEm)
            call.response.cookies.append(cookieDeSessao(novo, restante))
        }
        return principalFor(leitura.userId, roles, LocalDate.now(relogio))
    }

    /** O usuário da sessão, se o cookie é válido (para auditar o logout). */
    internal fun usuarioDaSessao(call: ApplicationCall): String? = leitura(call)?.userId

    private fun leitura(call: ApplicationCall): LeituraDeSessao? =
        call.request.cookies[COOKIE_DE_SESSAO, CookieEncoding.RAW]?.let { sessao.ler(it) }

    private fun aleatorio(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { aleatorio.nextBytes(it) })

    /** Começa o login: devolve a URL do provedor e o valor do cookie de login. */
    internal suspend fun iniciar(volta: String?): Pair<String, String> {
        val state = aleatorio()
        val nonce = aleatorio()
        val verificador = aleatorio() // 43 caracteres, dentro de 43..128 do RFC 7636
        val desafio = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray(Charsets.US_ASCII)))
        val url = provedor.urlDeAutorizacao(state, nonce, desafio)
        val estado = EstadoDeLogin(state, nonce, verificador, caminhoDeVoltaSeguro(volta), relogio.instant().plus(VALIDADE_DO_LOGIN))
        return url to cookieDeLogin.emitir(estado)
    }

    /** Conclui o login com o que o provedor devolveu na URL do callback e o cookie de login do navegador. */
    internal suspend fun concluir(code: String?, state: String?, valorDoCookie: String?): ResultadoDeLogin {
        val estado = cookieDeLogin.ler(valorDoCookie) ?: return recusa("LOGIN_EXPIRADO_OU_INVALIDO")
        if (state == null || !MessageDigest.isEqual(state.toByteArray(Charsets.UTF_8), estado.state.toByteArray(Charsets.UTF_8))) {
            return recusa("STATE_INVALIDO")
        }
        if (code.isNullOrEmpty()) return recusa("CODE_AUSENTE")
        // Uso único: repetir o callback (mesmo state, mesmo nonce) é recusado, ainda que o provedor aceitasse o código.
        if (!consumidos.consumir(estado.state, estado.expiraEm)) return recusa("LOGIN_REUTILIZADO")
        val idToken = try {
            provedor.trocarCodigo(code, estado.verificador)
        } catch (e: FalhaNoProvedor) {
            return if (e.recusouOCodigo) recusa("CODIGO_RECUSADO") else recusa(e.codigo, HttpStatusCode.BadGateway)
        }
        val valido = try {
            validador.validar(idToken, provedor.chaves(ValidadorDeIdToken.kidDoCabecalho(idToken)), estado.nonce)
        } catch (e: RecusaDeToken) {
            return recusa("TOKEN_" + e.motivo.name)
        } catch (e: FalhaNoProvedor) {
            return recusa(e.codigo, HttpStatusCode.BadGateway)
        }
        val usuario = try {
            normalizarUsername(valido.usuario)
        } catch (e: IllegalArgumentException) {
            return recusa("USUARIO_INVALIDO")
        }
        if (!USERNAME_VALIDO.matches(usuario)) return recusa("USUARIO_INVALIDO")
        // Os papéis vêm do diretório de hoje; sem papel vigente não se abre sessão (e o API responderia 403 de qualquer jeito).
        if (principalFor(usuario, roles, LocalDate.now(relogio)).roles.isEmpty()) {
            return ResultadoDeLogin.Recusado("SEM_PAPEL_VIGENTE", HttpStatusCode.Forbidden)
        }
        val cookie = sessao.emitir(usuario)
        return ResultadoDeLogin.Ok(usuario, cookie, relogio.instant().plus(cfg.duracaoDaSessao), estado.volta)
    }

    private fun recusa(codigo: String, status: HttpStatusCode = HttpStatusCode.Unauthorized) =
        ResultadoDeLogin.Recusado(codigo, status)

    /** Cookie da sessão com os atributos de segurança; [duracao] é quanto falta até a sessão vencer. */
    internal fun cookieDeSessao(valor: String, duracao: Duration) = cookieSeguro(COOKIE_DE_SESSAO, valor, duracao.seconds)

    internal fun cookieDeLogin(valor: String) = cookieSeguro(COOKIE_DE_LOGIN, valor, VALIDADE_DO_LOGIN.seconds)

    internal companion object {
        val VALIDADE_DO_LOGIN: Duration = Duration.ofMinutes(10)
        private val USERNAME_VALIDO = Regex("^[a-z0-9._-]{1,64}$")

        /**
         * `HttpOnly`, `Secure` (o painel escuta atrás do proxy da empresa, que termina o TLS) e `SameSite=Lax`: o cookie
         * acompanha a volta do provedor (navegação de topo, GET) e não vai em requisição de outro site.
         */
        fun cookieSeguro(nome: String, valor: String, segundos: Long) = Cookie(
            name = nome,
            value = valor,
            encoding = CookieEncoding.RAW,
            maxAge = segundos.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            path = "/",
            secure = true,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )

        fun cookieApagado(nome: String) = Cookie(
            name = nome, value = "", encoding = CookieEncoding.RAW, maxAge = 0, expires = GMTDate.START,
            path = "/", secure = true, httpOnly = true, extensions = mapOf("SameSite" to "Lax"),
        )

        /** Só caminho interno do próprio site (começa com `/`, não com `//`, sem `\` nem controle, fora de `/auth`); senão `/`. */
        fun caminhoDeVoltaSeguro(volta: String?): String {
            if (volta == null || !volta.startsWith("/") || volta.startsWith("//")) return "/"
            if (volta.any { it == '\\' || it.isISOControl() } || volta.length > 512) return "/"
            if (volta == "/auth" || volta.startsWith("/auth/") || volta.startsWith("/auth?")) return "/"
            return volta
        }
    }
}

/**
 * Rotas do login: `GET /auth/login` (leva ao provedor), `GET /auth/callback` (a volta do provedor) e `POST /auth/logout`.
 * Todas gravam `access_log` com o resultado e SEM token, código nem `state`; se o registro falhar, nada é entregue (503).
 * `/auth/login`, `/auth/callback` e `/auth/logout` são públicas (ver `rotasPublicas`): quem não tem sessão precisa delas.
 * O logout é público de propósito, para que mesmo uma sessão de segredo antigo ou sem papel consiga ser limpa; o POST
 * continua atrás da proteção CSRF.
 */
fun Route.rotasDeLogin(autenticador: OidcAuthenticator, auditoria: AccessLog?, relogio: Clock) {
    suspend fun auditar(usuario: String, rota: String, resultado: String): Boolean =
        try {
            auditoria?.let { a ->
                withContext(Dispatchers.IO) { a.record(usuario, rota, mapOf("resultado" to resultado), relogio.instant()) }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Falha ao registrar o acesso de login ({})", e.javaClass.simpleName)
            false
        }

    suspend fun ApplicationCall.semArmazenar() {
        response.header(HttpHeaders.CacheControl, "no-store")
    }

    get("/auth/login") {
        call.semArmazenar()
        val (url, cookie) = try {
            autenticador.iniciar(call.request.queryParameters["volta"])
        } catch (e: FalhaNoProvedor) {
            log.warn("Login não iniciado codigo={}", e.codigo)
            auditar(ID_ANONIMO, "/auth/login", e.codigo)
            call.respond(HttpStatusCode.BadGateway, ErroDto("PROVEDOR_INDISPONIVEL"))
            return@get
        }
        if (!auditar(ID_ANONIMO, "/auth/login", "redirecionado")) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        call.response.cookies.append(autenticador.cookieDeLogin(cookie))
        call.respondRedirect(url)
    }

    get("/auth/callback") {
        call.semArmazenar()
        val q = call.request.queryParameters
        val resultado = if (q["error"] != null) {
            ResultadoDeLogin.Recusado("PROVEDOR_NEGOU", HttpStatusCode.Unauthorized)
        } else {
            autenticador.concluir(q["code"], q["state"], call.request.cookies[COOKIE_DE_LOGIN, CookieEncoding.RAW])
        }
        // O cookie de login serve uma vez só, qualquer que seja o resultado.
        call.response.cookies.append(OidcAuthenticator.cookieApagado(COOKIE_DE_LOGIN))
        when (resultado) {
            is ResultadoDeLogin.Recusado -> {
                log.warn("Login recusado codigo={}", resultado.codigo)
                auditar(ID_ANONIMO, "/auth/callback", resultado.codigo)
                call.respond(resultado.status, ErroDto("LOGIN_FALHOU"))
            }
            is ResultadoDeLogin.Ok -> {
                if (!auditar(resultado.usuario, "/auth/callback", "ok")) {
                    call.respond(HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                call.response.cookies.append(
                    autenticador.cookieDeSessao(resultado.cookieDeSessao, Duration.between(relogio.instant(), resultado.expiraEm)),
                )
                call.respondRedirect(resultado.volta)
            }
        }
    }

    post("/auth/logout") {
        call.semArmazenar()
        val usuario = autenticador.usuarioDaSessao(call) ?: ID_ANONIMO
        if (!auditar(usuario, "/auth/logout", "ok")) {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@post
        }
        call.response.cookies.append(OidcAuthenticator.cookieApagado(COOKIE_DE_SESSAO))
        call.respond(HttpStatusCode.NoContent)
    }
}

private const val ID_ANONIMO = "anonymous"
