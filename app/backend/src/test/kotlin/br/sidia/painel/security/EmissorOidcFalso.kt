package br.sidia.painel.security

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Emissor OIDC FALSO para os testes: roda dentro do teste (MockEngine, nenhuma rede) no host `.invalid`. Responde a
 * descoberta, o JWKS e a troca do código, com chaves RSA geradas em memória. Registra cada requisição (`host + caminho`)
 * para provar que nada saiu para outro host.
 */
class EmissorOidcFalso(
    val issuer: String = "https://sso.exemplo.invalid",
    val clientId: String = "painel-teste",
) {
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun novaChave(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    var chave: KeyPair = novaChave()
    var kid: String = "chave-1"

    /** Chaves publicadas no JWKS (kid -> chave pública). Começa com a chave de assinatura. */
    val publicadas = linkedMapOf<String, RSAPublicKey>(kid to (chave.public as RSAPublicKey))

    /** `host + caminho` de cada requisição recebida. */
    val requisicoes = CopyOnWriteArrayList<String>()

    /** Parâmetros recebidos na troca do código (o último). */
    @Volatile var ultimaTroca: Map<String, String> = emptyMap()

    /** O `id_token` que a troca do código devolve; nulo = a troca responde `invalid_grant` (400). */
    @Volatile var idTokenDaTroca: String? = null

    /** Se definido, a troca confere o PKCE contra este `code_challenge` (S256). */
    @Volatile var desafioEsperado: String? = null

    @Volatile var statusDoJwks: HttpStatusCode = HttpStatusCode.OK
    @Volatile var emissorDaDescoberta: String = issuer
    @Volatile var statusDaDescoberta: HttpStatusCode = HttpStatusCode.OK

    /** Endereços que a descoberta anuncia (padrão: os do próprio emissor). */
    @Volatile var autorizacaoDaDescoberta: String = "$issuer/authorize"
    @Volatile var tokenDaDescoberta: String = "$issuer/token"
    @Volatile var jwksDaDescoberta: String = "$issuer/jwks"

    private fun MockRequestHandleScope.json(corpo: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(corpo, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    fun jwks(): String {
        val chaves = publicadas.map { (k, pub) ->
            JsonObject(
                mapOf(
                    "kty" to JsonPrimitive("RSA"), "kid" to JsonPrimitive(k), "use" to JsonPrimitive("sig"), "alg" to JsonPrimitive("RS256"),
                    "n" to JsonPrimitive(b64.encodeToString(pub.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())),
                    "e" to JsonPrimitive(b64.encodeToString(pub.publicExponent.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())),
                ),
            )
        }
        return JsonObject(mapOf("keys" to JsonArray(chaves))).toString()
    }

    val motor = MockEngine { req ->
        requisicoes += req.url.host + req.url.encodedPath
        when (req.url.encodedPath) {
            "/.well-known/openid-configuration" -> if (statusDaDescoberta != HttpStatusCode.OK) respond("", statusDaDescoberta) else json(
                """{"issuer":"$emissorDaDescoberta","authorization_endpoint":"$autorizacaoDaDescoberta","token_endpoint":"$tokenDaDescoberta","jwks_uri":"$jwksDaDescoberta"}""",
            )
            "/jwks" -> if (statusDoJwks == HttpStatusCode.OK) json(jwks()) else respond("", statusDoJwks)
            "/token" -> {
                val params = String(req.body.toByteArray(), Charsets.UTF_8).parseUrlEncodedParameters()
                ultimaTroca = params.entries().associate { it.key to it.value.first() }
                val desafio = desafioEsperado
                val calculado = params["code_verifier"]?.let {
                    b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.US_ASCII)))
                }
                val jwt = idTokenDaTroca
                if (jwt == null || (desafio != null && desafio != calculado)) {
                    json("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
                } else {
                    json("""{"token_type":"Bearer","access_token":"opaco-de-teste","id_token":"$jwt"}""")
                }
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    fun cliente() = HttpClient(motor)

    /** Monta e assina um JWT. [cabecalho] e [claims] entram como estão (valores `null` viram JSON null). */
    fun assinar(
        claims: Map<String, Any?>,
        alg: String = "RS256",
        kid: String? = this.kid,
        privada: PrivateKey = chave.private,
        assinar: Boolean = true,
    ): String {
        val cab = mutableMapOf<String, Any?>("alg" to alg, "typ" to "JWT")
        if (kid != null) cab["kid"] = kid
        val h = b64.encodeToString(paraJson(cab).toByteArray())
        val p = b64.encodeToString(paraJson(claims).toByteArray())
        val assinatura = if (!assinar) "" else b64.encodeToString(
            Signature.getInstance("SHA256withRSA").run { initSign(privada); update("$h.$p".toByteArray()); sign() },
        )
        return "$h.$p.$assinatura"
    }

    /** Claims de um token válido agora (instante em segundos). */
    fun claimsValidas(nonce: String, usuario: String, agora: Long, extras: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "iss" to issuer, "aud" to clientId, "sub" to "sub-$usuario", "exp" to agora + 600, "iat" to agora,
            "nonce" to nonce, "preferred_username" to usuario,
        ) + extras

    private fun paraJson(m: Map<String, Any?>): String = JsonObject(m.mapValues { (_, v) -> elemento(v) }).toString()

    private fun elemento(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is List<*> -> JsonArray(v.map { elemento(it) })
        else -> error("tipo não suportado no teste")
    }

    companion object {
        fun leitor(texto: String) = Json.parseToJsonElement(texto)
    }
}
