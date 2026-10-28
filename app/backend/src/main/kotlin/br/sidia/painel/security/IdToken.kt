package br.sidia.painel.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Por que um `id_token` foi recusado. É só um CÓDIGO (vai para o registro de acesso e para o log): nunca carrega o token,
 * nem trecho dele, nem o valor de uma claim.
 */
enum class MotivoDeRecusa {
    MAL_FORMADO,
    ALGORITMO_NAO_ACEITO,
    KID_DESCONHECIDO,
    ASSINATURA_INVALIDA,
    EMISSOR_ERRADO,
    AUDIENCIA_ERRADA,
    EXPIRADO,
    AINDA_NAO_VALIDO,
    NONCE_ERRADO,
    SEM_SUBJECT,
    USUARIO_AUSENTE,
}

/** Recusa de um `id_token`; a mensagem é só o código. */
class RecusaDeToken(val motivo: MotivoDeRecusa) : Exception(motivo.name)

/** `id_token` aceito: o `sub` do provedor e o valor bruto da claim configurada como username. */
class IdTokenValido(val sub: String, val usuario: String) {
    override fun toString() = "IdTokenValido(***)"
}

/** Uma chave pública RSA do JWKS; [kid] pode faltar quando o provedor publica uma chave só. */
class ChaveDoJwks(val kid: String?, val chave: RSAPublicKey)

/**
 * Chaves públicas do provedor (JWKS). Só entra chave RSA de assinatura (`use` ausente ou `sig`, `alg` ausente ou `RS256`)
 * com módulo de pelo menos 2048 bits; qualquer outra entrada, ou entrada mal formada, é ignorada (nunca derruba a leitura).
 */
class ChavesPublicas(val chaves: List<ChaveDoJwks>) {
    /**
     * A chave do [kid]. Sem `kid` no token, só vale se o provedor publica UMA chave só (e ela também sem `kid` ou com um):
     * com várias chaves, adivinhar qual usar é recusar.
     */
    fun buscar(kid: String?): RSAPublicKey? {
        if (kid != null) return chaves.firstOrNull { it.kid == kid }?.chave
        return chaves.singleOrNull()?.chave
    }

    companion object {
        private const val MINIMO_DE_BITS = 2048
        private val BASE64URL = Base64.getUrlDecoder()

        fun deJson(texto: String): ChavesPublicas {
            val raiz = try {
                Json.parseToJsonElement(texto).jsonObject
            } catch (e: Exception) {
                return ChavesPublicas(emptyList())
            }
            val lista = (raiz["keys"] as? JsonArray) ?: return ChavesPublicas(emptyList())
            return ChavesPublicas(lista.mapNotNull { runCatching { chaveDe(it) }.getOrNull() })
        }

        private fun texto(o: JsonObject, nome: String): String? = (o[nome] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun chaveDe(e: JsonElement): ChaveDoJwks? {
            val o = e as? JsonObject ?: return null
            if (texto(o, "kty") != "RSA") return null
            val uso = texto(o, "use")
            if (uso != null && uso != "sig") return null
            val alg = texto(o, "alg")
            if (alg != null && alg != "RS256") return null
            val n = BigInteger(1, BASE64URL.decode(texto(o, "n") ?: return null))
            val exp = BigInteger(1, BASE64URL.decode(texto(o, "e") ?: return null))
            if (n.bitLength() < MINIMO_DE_BITS) return null
            val chave = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, exp)) as RSAPublicKey
            return ChaveDoJwks(texto(o, "kid"), chave)
        }
    }
}

/**
 * Valida um `id_token` do OIDC contra as chaves do provedor. Aceita SÓ `RS256`: `none`, HMAC (o ataque de trocar a chave
 * pública por segredo) e qualquer outro algoritmo são recusados antes de olhar a assinatura. Confere, nesta ordem:
 * assinatura, `iss` (igual ao configurado), `aud` (contém o `client_id`; com várias, `azp` igual ao `client_id`), `exp`,
 * `nbf`/`iat` (com [tolerancia] de relógio), `nonce` (igual ao do login), `sub` e a claim do username.
 * O relógio é injetado. Nenhuma mensagem de erro carrega o token.
 */
class ValidadorDeIdToken(
    private val emissor: String,
    private val clientId: String,
    private val claimUsername: String,
    private val relogio: Clock,
    private val tolerancia: Duration = Duration.ofSeconds(60),
) {
    fun validar(token: String, chaves: ChavesPublicas, nonceEsperado: String): IdTokenValido {
        // O cabeçalho é lido e o algoritmo conferido ANTES de decodificar o resto: `none` e afins nunca passam adiante.
        val cabecalho = cabecalhoDe(token)
        if (cabecalho.texto("alg") != "RS256") throw RecusaDeToken(MotivoDeRecusa.ALGORITMO_NAO_ACEITO)
        // `crit` lista extensões que o validador DEVE entender; não entendemos nenhuma.
        if ("crit" in cabecalho) throw RecusaDeToken(MotivoDeRecusa.ALGORITMO_NAO_ACEITO)
        val (_, corpo, assinatura, assinado) = partir(token)
        val chave = chaves.buscar(cabecalho.texto("kid")) ?: throw RecusaDeToken(MotivoDeRecusa.KID_DESCONHECIDO)
        val confere = try {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(chave)
                update(assinado.toByteArray(Charsets.US_ASCII))
                verify(assinatura)
            }
        } catch (e: Exception) {
            false
        }
        if (!confere) throw RecusaDeToken(MotivoDeRecusa.ASSINATURA_INVALIDA)

        if (corpo.texto("iss") != emissor) throw RecusaDeToken(MotivoDeRecusa.EMISSOR_ERRADO)
        conferirAudiencia(corpo)
        val agora = relogio.instant()
        val exp = corpo.instante("exp") ?: throw RecusaDeToken(MotivoDeRecusa.EXPIRADO)
        if (!agora.isBefore(exp.plus(tolerancia))) throw RecusaDeToken(MotivoDeRecusa.EXPIRADO)
        if (corpo.instante("nbf")?.let { agora.isBefore(it.minus(tolerancia)) } == true) {
            throw RecusaDeToken(MotivoDeRecusa.AINDA_NAO_VALIDO)
        }
        if (corpo.instante("iat")?.let { agora.isBefore(it.minus(tolerancia)) } == true) {
            throw RecusaDeToken(MotivoDeRecusa.AINDA_NAO_VALIDO)
        }
        val nonce = corpo.texto("nonce")
        if (nonce == null || !MessageDigest.isEqual(nonce.toByteArray(Charsets.UTF_8), nonceEsperado.toByteArray(Charsets.UTF_8))) {
            throw RecusaDeToken(MotivoDeRecusa.NONCE_ERRADO)
        }
        val sub = corpo.texto("sub")?.takeIf { it.isNotBlank() } ?: throw RecusaDeToken(MotivoDeRecusa.SEM_SUBJECT)
        val usuario = corpo.texto(claimUsername)?.takeIf { it.isNotBlank() } ?: throw RecusaDeToken(MotivoDeRecusa.USUARIO_AUSENTE)
        return IdTokenValido(sub, usuario)
    }

    private fun conferirAudiencia(corpo: JsonObject) {
        val aud = when (val a = corpo["aud"]) {
            is JsonPrimitive -> if (a.isString) listOf(a.content) else emptyList()
            is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            else -> emptyList()
        }
        if (clientId !in aud) throw RecusaDeToken(MotivoDeRecusa.AUDIENCIA_ERRADA)
        if (aud.size > 1 && corpo.texto("azp") != clientId) throw RecusaDeToken(MotivoDeRecusa.AUDIENCIA_ERRADA)
    }

    private class Partes(val cabecalho: JsonObject, val corpo: JsonObject, val assinatura: ByteArray, val assinado: String) {
        operator fun component1() = cabecalho
        operator fun component2() = corpo
        operator fun component3() = assinatura
        operator fun component4() = assinado
    }

    companion object {
        /** Tamanho máximo aceito para o token inteiro (um `id_token` normal tem 1 a 3 KB). */
        const val MAXIMO_DE_CARACTERES = 16 * 1024

        private val BASE64URL = Base64.getUrlDecoder()

        /** O `kid` do cabeçalho, ou `null` (token mal formado ou sem `kid`). Não valida nada. */
        fun kidDoCabecalho(token: String): String? =
            try {
                cabecalhoDe(token).texto("kid")
            } catch (e: RecusaDeToken) {
                null
            }

        /** Só o cabeçalho (primeiro segmento), com as mesmas regras de forma de [partir]. */
        private fun cabecalhoDe(token: String): JsonObject {
            if (token.length > MAXIMO_DE_CARACTERES) throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            val partes = token.split('.')
            if (partes.size != 3 || partes[0].isEmpty()) throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            return try {
                Json.parseToJsonElement(String(BASE64URL.decode(partes[0]), Charsets.UTF_8)).jsonObject
            } catch (e: Exception) {
                throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            }
        }

        private fun partir(token: String): Partes {
            if (token.length > MAXIMO_DE_CARACTERES) throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            val partes = token.split('.')
            if (partes.size != 3 || partes.any { it.isEmpty() }) throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            return try {
                Partes(
                    Json.parseToJsonElement(String(BASE64URL.decode(partes[0]), Charsets.UTF_8)).jsonObject,
                    Json.parseToJsonElement(String(BASE64URL.decode(partes[1]), Charsets.UTF_8)).jsonObject,
                    BASE64URL.decode(partes[2]),
                    partes[0] + "." + partes[1],
                )
            } catch (e: Exception) {
                throw RecusaDeToken(MotivoDeRecusa.MAL_FORMADO)
            }
        }

        private fun JsonObject.texto(nome: String): String? = (this[nome] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.instante(nome: String): Instant? {
            val p = this[nome] as? JsonPrimitive ?: return null
            if (p.isString) return null
            val segundos = p.longOrNull ?: p.contentOrNull?.toDoubleOrNull()?.toLong() ?: return null
            return runCatching { Instant.ofEpochSecond(segundos) }.getOrNull()
        }
    }
}
