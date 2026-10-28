package br.sidia.painel.security

import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Validação do `id_token` contra um JWKS de teste, com relógio injetado. Chaves RSA geradas na hora; nada de rede. */
class IdTokenTest {
    private val emissor = EmissorOidcFalso()
    private val agoraInstante = Instant.parse("2026-10-28T15:00:00Z")
    private val agora = agoraInstante.epochSecond
    private val relogio = Clock.fixed(agoraInstante, ZoneOffset.UTC)
    private val nonce = "nonce-do-login-1"
    private val validador = ValidadorDeIdToken(emissor.issuer, emissor.clientId, "preferred_username", relogio)
    private val chaves get() = ChavesPublicas.deJson(emissor.jwks())

    private fun validas(extras: Map<String, Any?> = emptyMap()) = emissor.claimsValidas(nonce, "ana.silva", agora, extras)

    private fun motivo(token: String, chaves: ChavesPublicas = this.chaves, nonceEsperado: String = nonce): MotivoDeRecusa =
        assertFailsWith<RecusaDeToken> { validador.validar(token, chaves, nonceEsperado) }.motivo

    @Test
    fun tokenValidoEhAceito() {
        val ok = validador.validar(emissor.assinar(validas()), chaves, nonce)
        assertEquals("ana.silva", ok.usuario)
        assertEquals("sub-ana.silva", ok.sub)
    }

    @Test
    fun claimDoUsernameEhConfiguravel() {
        val v = ValidadorDeIdToken(emissor.issuer, emissor.clientId, "uid", relogio)
        val ok = v.validar(emissor.assinar(validas(mapOf("uid" to "bruno"))), chaves, nonce)
        assertEquals("bruno", ok.usuario)
        // a claim padrão não vale quando outra foi configurada
        assertEquals(
            MotivoDeRecusa.USUARIO_AUSENTE,
            assertFailsWith<RecusaDeToken> { v.validar(emissor.assinar(validas()), chaves, nonce) }.motivo,
        )
    }

    @Test
    fun tokenExpiradoEhRecusado() {
        assertEquals(MotivoDeRecusa.EXPIRADO, motivo(emissor.assinar(validas(mapOf("exp" to agora - 120)))))
        // exatamente no limite da tolerância (60 s) já venceu; dentro dela ainda vale
        assertEquals(MotivoDeRecusa.EXPIRADO, motivo(emissor.assinar(validas(mapOf("exp" to agora - 60)))))
        assertNotNull(validador.validar(emissor.assinar(validas(mapOf("exp" to agora - 30))), chaves, nonce))
    }

    @Test
    fun semExpOuExpTextoNaoEhAceito() {
        assertEquals(MotivoDeRecusa.EXPIRADO, motivo(emissor.assinar(validas(mapOf("exp" to null)))))
        assertEquals(MotivoDeRecusa.EXPIRADO, motivo(emissor.assinar(validas(mapOf("exp" to "99999999999")))))
    }

    @Test
    fun tokenAindaNaoValidoEhRecusado() {
        assertEquals(MotivoDeRecusa.AINDA_NAO_VALIDO, motivo(emissor.assinar(validas(mapOf("nbf" to agora + 600)))))
        assertEquals(MotivoDeRecusa.AINDA_NAO_VALIDO, motivo(emissor.assinar(validas(mapOf("iat" to agora + 600)))))
    }

    @Test
    fun audienciaErradaEhRecusada() {
        assertEquals(MotivoDeRecusa.AUDIENCIA_ERRADA, motivo(emissor.assinar(validas(mapOf("aud" to "outro-cliente")))))
        assertEquals(MotivoDeRecusa.AUDIENCIA_ERRADA, motivo(emissor.assinar(validas(mapOf("aud" to null)))))
        assertEquals(MotivoDeRecusa.AUDIENCIA_ERRADA, motivo(emissor.assinar(validas(mapOf("aud" to listOf("a", "b"))))))
    }

    @Test
    fun audienciaComVariosExigeAzpIgualAoCliente() {
        val varias = listOf(emissor.clientId, "api-da-empresa")
        assertEquals(MotivoDeRecusa.AUDIENCIA_ERRADA, motivo(emissor.assinar(validas(mapOf("aud" to varias)))))
        assertEquals(MotivoDeRecusa.AUDIENCIA_ERRADA, motivo(emissor.assinar(validas(mapOf("aud" to varias, "azp" to "outro")))))
        assertNotNull(validador.validar(emissor.assinar(validas(mapOf("aud" to varias, "azp" to emissor.clientId))), chaves, nonce))
        assertNotNull(validador.validar(emissor.assinar(validas(mapOf("aud" to listOf(emissor.clientId)))), chaves, nonce))
    }

    @Test
    fun emissorErradoEhRecusado() {
        assertEquals(MotivoDeRecusa.EMISSOR_ERRADO, motivo(emissor.assinar(validas(mapOf("iss" to "https://outro.exemplo.invalid")))))
        // sem a barra final igual à configurada não vale (comparação exata)
        assertEquals(MotivoDeRecusa.EMISSOR_ERRADO, motivo(emissor.assinar(validas(mapOf("iss" to emissor.issuer + "/")))))
    }

    @Test
    fun nonceErradoOuAusenteEhRecusado() {
        assertEquals(MotivoDeRecusa.NONCE_ERRADO, motivo(emissor.assinar(validas()), nonceEsperado = "outro-nonce"))
        assertEquals(MotivoDeRecusa.NONCE_ERRADO, motivo(emissor.assinar(validas(mapOf("nonce" to null)))))
    }

    @Test
    fun algoritmoNoneEhRecusadoMesmoSemAssinar() {
        val semAssinatura = emissor.assinar(validas(), alg = "none", assinar = false)
        // o algoritmo é conferido antes da assinatura: com o terceiro segmento vazio, ou com lixo, o motivo é o mesmo
        assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo(semAssinatura))
        val comLixo = emissor.assinar(validas(), alg = "none", assinar = false) + "AAAA"
        assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo(comLixo))
        assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo(emissor.assinar(validas(), alg = "None")))
    }

    @Test
    fun hmacComChavePublicaComoSegredoEhRecusado() {
        // ataque clássico: assinar com HS256 usando a chave pública (que é pública) como segredo
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val publica = (emissor.chave.public as RSAPublicKey).encoded
        val cab = b64.encodeToString("""{"alg":"HS256","kid":"${emissor.kid}"}""".toByteArray())
        val corpo = b64.encodeToString(
            """{"iss":"${emissor.issuer}","aud":"${emissor.clientId}","sub":"x","exp":${agora + 600},"nonce":"$nonce","preferred_username":"gerente.exemplo"}""".toByteArray(),
        )
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(publica, "HmacSHA256")) }
        val token = "$cab.$corpo." + b64.encodeToString(mac.doFinal("$cab.$corpo".toByteArray()))
        assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo(token))
        for (alg in listOf("HS256", "RS384", "ES256", "PS256", "")) {
            assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo(emissor.assinar(validas(), alg = alg)), alg)
        }
    }

    @Test
    fun cabecalhoComCritEhRecusado() {
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val cab = b64.encodeToString("""{"alg":"RS256","kid":"${emissor.kid}","crit":["exp"],"exp":1}""".toByteArray())
        val partes = emissor.assinar(validas()).split('.')
        assertEquals(MotivoDeRecusa.ALGORITMO_NAO_ACEITO, motivo("$cab.${partes[1]}.${partes[2]}"))
    }

    @Test
    fun kidDesconhecidoEhRecusado() {
        assertEquals(MotivoDeRecusa.KID_DESCONHECIDO, motivo(emissor.assinar(validas(), kid = "nao-publicada")))
    }

    @Test
    fun semKidSoValeComUmaChaveSo() {
        val semKid = emissor.assinar(validas(), kid = null)
        assertNotNull(validador.validar(semKid, chaves, nonce))
        emissor.publicadas["chave-2"] = emissor.novaChave().public as RSAPublicKey
        assertEquals(MotivoDeRecusa.KID_DESCONHECIDO, motivo(semKid))
    }

    @Test
    fun assinaturaDeOutraChaveOuAdulteradaEhRecusada() {
        val intrusa = emissor.novaChave()
        // kid certo, mas assinado por quem não tem a chave do provedor
        assertEquals(MotivoDeRecusa.ASSINATURA_INVALIDA, motivo(emissor.assinar(validas(), privada = intrusa.private)))
        // trocar o corpo depois de assinado
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val partes = emissor.assinar(validas()).split('.')
        val forjado = b64.encodeToString(
            String(Base64.getUrlDecoder().decode(partes[1])).replace("ana.silva", "gerente.exemplo").toByteArray(),
        )
        assertEquals(MotivoDeRecusa.ASSINATURA_INVALIDA, motivo("${partes[0]}.$forjado.${partes[2]}"))
    }

    @Test
    fun tokenMalFormadoEhRecusado() {
        for (ruim in listOf("", "a.b", "a.b.c.d", "...", "###.###.###", "x".repeat(ValidadorDeIdToken.MAXIMO_DE_CARACTERES + 1))) {
            assertEquals(MotivoDeRecusa.MAL_FORMADO, motivo(ruim), ruim.take(20))
        }
    }

    @Test
    fun semSubjectOuSemUsuarioEhRecusado() {
        assertEquals(MotivoDeRecusa.SEM_SUBJECT, motivo(emissor.assinar(validas(mapOf("sub" to "")))))
        assertEquals(MotivoDeRecusa.USUARIO_AUSENTE, motivo(emissor.assinar(validas(mapOf("preferred_username" to " ")))))
        assertEquals(MotivoDeRecusa.USUARIO_AUSENTE, motivo(emissor.assinar(validas(mapOf("preferred_username" to 42)))))
    }

    @Test
    fun kidDoCabecalhoNaoQuebraComTokenRuim() {
        assertEquals(emissor.kid, ValidadorDeIdToken.kidDoCabecalho(emissor.assinar(validas())))
        assertNull(ValidadorDeIdToken.kidDoCabecalho("lixo"))
    }

    @Test
    fun jwksIgnoraChaveFracaEntradaRuimEUsoDeCifra() {
        val fraca = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public as RSAPublicKey
        emissor.publicadas["fraca"] = fraca
        val comRuins = ChavesPublicas.deJson(
            emissor.jwks().replace("""{"keys":[""", """{"keys":[{"kty":"EC","kid":"ec"},{"kty":"RSA","kid":"sem-n"},42,{"kty":"RSA","kid":"enc","use":"enc","n":"AQAB","e":"AQAB"},"""),
        )
        assertNull(comRuins.buscar("fraca"))
        assertNull(comRuins.buscar("ec"))
        assertNull(comRuins.buscar("enc"))
        assertNotNull(comRuins.buscar(emissor.kid))
        assertEquals(0, ChavesPublicas.deJson("não é json").chaves.size)
        assertEquals(0, ChavesPublicas.deJson("""{"keys":"x"}""").chaves.size)
    }
}
