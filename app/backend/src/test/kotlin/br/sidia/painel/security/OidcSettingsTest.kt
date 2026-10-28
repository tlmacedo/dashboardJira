package br.sidia.painel.security

import br.sidia.painel.config.Settings
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Variáveis `OIDC_*` e `SESSION_SECRET`: produção falha fechada; as mensagens citam só o NOME da variável. */
class OidcSettingsTest {
    private val segredoDaSessao = Base64.getEncoder().encodeToString(ByteArray(48) { (it + 11).toByte() })
    private val segredoDoCliente = "cliente-segredo-ficticio-77"

    /** Produção completa e válida. */
    private val completo = mapOf(
        "DASHBOARD_DEV_MODE" to "false",
        "OIDC_ISSUER" to "https://sso.exemplo.invalid",
        "OIDC_CLIENT_ID" to "painel-teste",
        "OIDC_CLIENT_SECRET" to segredoDoCliente,
        "OIDC_REDIRECT_URI" to "https://painel.exemplo.invalid/auth/callback",
        "SESSION_SECRET" to segredoDaSessao,
    )

    private fun erro(env: Map<String, String>): String =
        assertFailsWith<IllegalArgumentException> { Settings.fromEnv(env) }.message ?: ""

    @Test
    fun producaoSemOidcNaoSobe() {
        val msg = erro(mapOf("DASHBOARD_DEV_MODE" to "false"))
        assertTrue("OIDC_ISSUER" in msg, msg)
        assertTrue("obrigatório em produção" in msg, msg)
    }

    @Test
    fun faltandoUmaVariavelACitadaEMaisNenhuma() {
        for (nome in listOf("OIDC_ISSUER", "OIDC_CLIENT_ID", "OIDC_REDIRECT_URI", "SESSION_SECRET")) {
            val msg = erro(completo - nome)
            assertTrue(msg.startsWith("$nome "), "$nome -> $msg")
            assertFalse(segredoDaSessao in msg || segredoDoCliente in msg)
        }
    }

    @Test
    fun variavelEmBrancoEhErroNuncaAusenteSilencioso() {
        for (nome in listOf("OIDC_ISSUER", "OIDC_CLIENT_ID", "OIDC_REDIRECT_URI", "SESSION_SECRET")) {
            for (branco in listOf("", "   ", "\t")) {
                val msg = erro(completo + (nome to branco))
                assertTrue(msg.startsWith("$nome "), "$nome='$branco' -> $msg")
            }
        }
    }

    @Test
    fun segredoDaSessaoCurtoOuInvalidoNaoSobe() {
        val curto = Base64.getEncoder().encodeToString(ByteArray(16))
        assertTrue(erro(completo + ("SESSION_SECRET" to curto)).contains("SESSION_SECRET"))
        assertTrue(erro(completo + ("SESSION_SECRET" to "isto não é base64!")).contains("SESSION_SECRET"))
        // a mensagem nunca ecoa o valor
        assertFalse(erro(completo + ("SESSION_SECRET" to curto)).contains(curto))
    }

    @Test
    fun enderecosExigemHttpsSemCredencialConsultaNemCaminhoErrado() {
        for (ruim in listOf("http://sso.exemplo.invalid", "sso.exemplo.invalid", "https://u:p@sso.exemplo.invalid", "https://sso.exemplo.invalid?x=1", "https://sso.exemplo.invalid#f", "ftp://x.invalid")) {
            val msg = erro(completo + ("OIDC_ISSUER" to ruim))
            assertTrue(msg.startsWith("OIDC_ISSUER "), "$ruim -> $msg")
            assertFalse("u:p" in msg)
        }
        for (ruim in listOf("http://painel.exemplo.invalid/auth/callback", "https://painel.exemplo.invalid/outro", "https://painel.exemplo.invalid/auth/callback?x=1", "https://painel.exemplo.invalid/auth/callback/")) {
            assertTrue(erro(completo + ("OIDC_REDIRECT_URI" to ruim)).startsWith("OIDC_REDIRECT_URI "), ruim)
        }
    }

    @Test
    fun claimDoUsernameTemPadraoEAceitaOutra() {
        assertEquals("preferred_username", Settings.fromEnv(completo).oidc?.claimUsername)
        assertEquals("uid", Settings.fromEnv(completo + ("OIDC_CLAIM_USERNAME" to " uid ")).oidc?.claimUsername)
        assertTrue(erro(completo + ("OIDC_CLAIM_USERNAME" to "com espaço")).startsWith("OIDC_CLAIM_USERNAME "))
    }

    @Test
    fun producaoCompletaLeTudo() {
        val oidc = assertNotNull(Settings.fromEnv(completo).oidc)
        assertEquals("https://sso.exemplo.invalid", oidc.issuer)
        assertEquals("painel-teste", oidc.clientId)
        assertEquals(segredoDoCliente, oidc.clientSecret)
        assertEquals("https://painel.exemplo.invalid/auth/callback", oidc.redirectUri)
        assertEquals(listOf("openid", "profile"), oidc.escopos)
        // o segredo do cliente é opcional (cliente público com PKCE)
        assertNull(Settings.fromEnv(completo - "OIDC_CLIENT_SECRET").oidc?.clientSecret)
    }

    @Test
    fun desenvolvimentoSemOidcNaoTemLogin() {
        assertNull(Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "true")).oidc)
        assertNull(Settings.fromEnv(emptyMap()).oidc)
        // desenvolvimento com OIDC pela metade continua sendo erro (configuração incoerente)
        assertTrue(erro(mapOf("DASHBOARD_DEV_MODE" to "true", "OIDC_ISSUER" to "https://sso.exemplo.invalid")).startsWith("OIDC_CLIENT_ID "))
    }

    @Test
    fun settingsToStringSemSegredos() {
        val s = Settings.fromEnv(completo)
        val texto = s.toString()
        assertFalse(segredoDaSessao in texto)
        assertFalse(segredoDoCliente in texto)
        assertTrue("oidc=configurado" in texto, texto)
        assertTrue("oidc=ausente" in Settings.fromEnv(emptyMap()).toString())
        val oidc = s.oidc.toString()
        assertFalse(segredoDaSessao in oidc || segredoDoCliente in oidc, oidc)
        // e os dois segredos vão para a máscara de log
        assertTrue(segredoDoCliente in s.valoresSecretos())
        assertTrue(segredoDaSessao in s.valoresSecretos())
    }
}
