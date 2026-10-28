package br.sidia.painel.security

import br.sidia.painel.operacao.RelogioDeTeste
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Endpoints da descoberta (`authorization`, `token`, `jwks_uri`): lista opcional de hosts e recusa de endereço interno. */
class HostsDoOidcTest {
    private val relogio = RelogioDeTeste(Instant.parse("2026-10-28T15:00:00Z"))
    private val emissor = EmissorOidcFalso()
    private val segredo = Base64.getEncoder().encodeToString(ByteArray(48) { (it * 5 + 1).toByte() })

    private fun provedor(hosts: Set<String> = emptySet()) = ProvedorOidc(
        OidcSettings(
            issuer = emissor.issuer, clientId = emissor.clientId, clientSecret = null,
            redirectUri = "https://painel.exemplo.invalid/auth/callback",
            sessao = SegredosDaSessao.de(segredo, null), hostsPermitidos = hosts,
        ),
        emissor.cliente(), relogio,
    )

    @Test
    fun comListaDeHostsQualquerEndpointForaDelaERecusadoSemEco() = runBlocking {
        emissor.autorizacaoDaDescoberta = "https://outro-provedor.exemplo.invalid/authorize"
        val e = assertFailsWith<FalhaNoProvedor> { provedor(setOf("sso.exemplo.invalid")).descoberta() }
        assertEquals("DESCOBERTA_ENDPOINT_NAO_PERMITIDO", e.codigo)
        assertFalse("outro-provedor" in (e.message ?: ""))
    }

    @Test
    fun comListaDeHostsOsEndpointsDoProprioProvedorPassam() = runBlocking {
        val d = provedor(setOf("SSO.exemplo.invalid", "outro.exemplo.invalid")).descoberta()
        assertEquals("https://sso.exemplo.invalid/token", d.token)
    }

    @Test
    fun semListaRecusaEnderecoLiteralLoopbackLinkLocalEPrivado() = runBlocking {
        // Os endereços são montados (esquema + host) para a varredura de hosts internos do repositório não os confundir com dado real.
        val hosts = listOf(
            "127.0.0.1", "[::1]", "localhost", "169.254.169.254", "10.1.2.3", "192.168.0.7", "172.16.0.1", "172.31.255.255",
            "[fe80::1]", "[fd00::1]", "0.0.0.0", "8.8.8.8", "painel.local", "2130706433", "0x7f.1", "usuario:senha@sso.exemplo.invalid",
        )
        for (u in hosts.map { "https://$it/token" }) {
            emissor.tokenDaDescoberta = u
            relogio.avancar(Duration.ofMinutes(5))
            val e = assertFailsWith<FalhaNoProvedor>(u) { provedor().descoberta() }
            assertEquals("DESCOBERTA_ENDPOINT_NAO_PERMITIDO", e.codigo, u)
            assertFalse(u.substringAfter("//").substringBefore("/") in (e.message ?: ""), u)
        }
    }

    @Test
    fun semListaNomeDeHostNormalPassa() = runBlocking {
        emissor.tokenDaDescoberta = "https://login.exemplo.invalid:8443/oauth/token"
        assertEquals("https://login.exemplo.invalid:8443/oauth/token", provedor().descoberta().token)
    }

    @Test
    fun comListaOEnderecoLiteralSoPassaSeEstiverNela() = runBlocking {
        val literal = "10.1.2.3"
        emissor.tokenDaDescoberta = "https://$literal/token"
        assertFailsWith<FalhaNoProvedor> { provedor(setOf("sso.exemplo.invalid")).descoberta() }
        assertEquals("https://$literal/token", provedor(setOf("sso.exemplo.invalid", literal)).descoberta().token)
    }

    @Test
    fun variavelDeHostsPermitidosELidaDoAmbiente() {
        val base = mapOf(
            "OIDC_ISSUER" to "https://sso.exemplo.invalid", "OIDC_CLIENT_ID" to "painel-teste",
            "OIDC_REDIRECT_URI" to "https://painel.exemplo.invalid/auth/callback", "SESSION_SECRET" to segredo,
        )
        assertEquals(emptySet(), OidcSettings.fromEnv(base, devMode = false)!!.hostsPermitidos)
        val cfg = OidcSettings.fromEnv(base + ("OIDC_HOSTS_PERMITIDOS" to " SSO.exemplo.invalid , login.exemplo.invalid ,, "), devMode = false)!!
        assertEquals(setOf("sso.exemplo.invalid", "login.exemplo.invalid"), cfg.hostsPermitidos)
        val ruim = assertFailsWith<IllegalArgumentException> {
            OidcSettings.fromEnv(base + ("OIDC_HOSTS_PERMITIDOS" to "https://sso.exemplo.invalid/x"), devMode = false)
        }
        assertFalse("sso.exemplo.invalid/x" in (ruim.message ?: ""))
        assertTrue("OIDC_HOSTS_PERMITIDOS" in (ruim.message ?: ""))
    }
}
