package br.sidia.painel.security

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Segredos que assinam o cookie de sessão: o [atual] (`SESSION_SECRET`) e, durante uma rotação, o [anterior]
 * (`SESSION_SECRET_ANTERIOR`), aceito SÓ para ler. Cada um precisa ter pelo menos 32 bytes (em base64) e o anterior não
 * pode ser igual ao atual. As mensagens de erro citam só os NOMES das variáveis, nunca o valor; o `toString` não mostra segredo.
 */
class SegredosDaSessao private constructor(
    val atual: ByteArray,
    val anterior: ByteArray?,
    /** Os textos originais, para a máscara de log registrá-los. */
    internal val textos: List<String>,
) {
    override fun toString(): String = "SegredosDaSessao(atual=***, anterior=${if (anterior != null) "***" else "nenhum"})"

    companion object {
        const val MINIMO_DE_BYTES = 32

        private fun decodificar(nome: String, texto: String): ByteArray {
            val bytes = try {
                Base64.getDecoder().decode(texto.trim())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$nome inválido (esperado base64)")
            }
            require(bytes.size >= MINIMO_DE_BYTES) { "$nome inválido (mínimo de $MINIMO_DE_BYTES bytes em base64)" }
            return bytes
        }

        fun de(atual: String, anterior: String?): SegredosDaSessao {
            val bytesAtual = decodificar("SESSION_SECRET", atual)
            val bytesAnterior = anterior?.let { decodificar("SESSION_SECRET_ANTERIOR", it) }
            require(bytesAnterior == null || !bytesAnterior.contentEquals(bytesAtual)) {
                "SESSION_SECRET_ANTERIOR não pode ser igual a SESSION_SECRET"
            }
            return SegredosDaSessao(bytesAtual, bytesAnterior, listOfNotNull(atual.trim(), anterior?.trim()))
        }

        /** Sem `SESSION_SECRET` (e sem o anterior) não há segredos: o modo desenvolvimento não usa cookie assinado. */
        fun fromEnv(env: Map<String, String>): SegredosDaSessao? {
            val atual = env["SESSION_SECRET"]?.trim()?.takeIf { it.isNotEmpty() }
            val anterior = env["SESSION_SECRET_ANTERIOR"]?.trim()?.takeIf { it.isNotEmpty() }
            if (atual == null) {
                require(anterior == null) { "SESSION_SECRET_ANTERIOR exige SESSION_SECRET" }
                return null
            }
            return de(atual, anterior)
        }
    }
}

/** O que se leu de um cookie válido. [usouSegredoAnterior]: foi preciso o segredo antigo para validar a assinatura. */
class LeituraDeSessao(val userId: String, val expiraEm: Instant, val usouSegredoAnterior: Boolean)

/**
 * Cookie de sessão assinado (HMAC-SHA-256): `v1.<usuário em base64url>.<fim em segundos>.<assinatura>`. É SEMPRE emitido
 * com o segredo atual. Na leitura vale o atual e, se a assinatura não bater, o anterior (rotação sem derrubar quem está
 * logado); uma sessão lida com o anterior deve ser regravada com o atual na mesma resposta ([regravarSeNecessario]), sem
 * estender o prazo. Cookie adulterado, mal formado ou vencido não lê. A assinatura é comparada em tempo constante.
 *
 * Este é o mínimo para a rotação do Plano 12; a ligação ao login (OIDC) e as regras do cookie (flags, `SameSite`) são do
 * Plano 8, que deve usar esta classe em vez de assinar por conta própria.
 */
class SessaoAssinada(
    private val segredos: SegredosDaSessao,
    private val duracao: Duration,
    /** Por quanto tempo, a partir de [inicioDaRotacao], o segredo anterior ainda vale (padrão: a duração da sessão). */
    private val janela: Duration = duracao,
    /** Quando a rotação começou (a subida com `SESSION_SECRET_ANTERIOR`); nulo = o instante da criação desta instância. */
    inicioDaRotacao: Instant? = null,
    private val agora: () -> Instant,
) {
    private val inicioDaJanela: Instant = inicioDaRotacao ?: agora()

    fun emitir(userId: String): String = assinar(userId, agora().plus(duracao).epochSecond, segredos.atual)

    fun ler(cookie: String): LeituraDeSessao? {
        val partes = cookie.split('.')
        if (partes.size != 4 || partes[0] != VERSAO) return null
        val corpo = partes.take(3).joinToString(".")
        val assinatura = try {
            Base64.getUrlDecoder().decode(partes[3])
        } catch (e: IllegalArgumentException) {
            return null
        }
        val comAtual = MessageDigest.isEqual(assinatura, hmac(corpo, segredos.atual))
        val comAnterior = !comAtual && segredos.anterior != null && MessageDigest.isEqual(assinatura, hmac(corpo, segredos.anterior))
        if (!comAtual && !comAnterior) return null
        val agoraMesmo = agora()
        // O segredo anterior só vale na janela da rotação: depois dela, mesmo com a variável ainda no arquivo, nada o aceita.
        if (comAnterior && !agoraMesmo.isBefore(inicioDaJanela.plus(janela))) return null
        // Fim fora do intervalo representável (assinado por quem tem o segredo) é cookie inválido, não exceção.
        val fim = partes[2].toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() } ?: return null
        if (!agoraMesmo.isBefore(fim)) return null
        // Nenhum cookie vale além da duração da sessão: quem tem o segredo não forja um cookie de longa duração.
        val limite = runCatching { agoraMesmo.plus(duracao) }.getOrNull() ?: return null
        if (fim.isAfter(limite)) return null
        val usuario = try {
            String(Base64.getUrlDecoder().decode(partes[1]), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return LeituraDeSessao(usuario, fim, usouSegredoAnterior = comAnterior)
    }

    /** O cookie novo (assinado com o segredo atual, mesmo fim de sessão) se a leitura precisou do anterior; senão `null`. */
    fun regravarSeNecessario(leitura: LeituraDeSessao): String? =
        if (leitura.usouSegredoAnterior) assinar(leitura.userId, leitura.expiraEm.epochSecond, segredos.atual) else null

    internal fun assinar(userId: String, fimEmSegundos: Long, segredo: ByteArray): String {
        val usuario = Base64.getUrlEncoder().withoutPadding().encodeToString(userId.toByteArray(Charsets.UTF_8))
        val corpo = "$VERSAO.$usuario.$fimEmSegundos"
        return corpo + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(corpo, segredo))
    }

    private fun hmac(corpo: String, segredo: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec(segredo, "HmacSHA256")) }.doFinal(corpo.toByteArray(Charsets.UTF_8))

    private companion object {
        const val VERSAO = "v1"
    }
}
