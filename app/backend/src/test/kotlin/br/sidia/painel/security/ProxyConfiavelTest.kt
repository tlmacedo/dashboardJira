package br.sidia.painel.security

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.operacao.RelogioDeTeste
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headers
import io.ktor.http.setCookie
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 3: `X-Forwarded-*` só vale quando a CONEXÃO vem do proxy da empresa (faixa configurada), e o
 * `X-Forwarded-For` é lido da direita para a esquerda. Nenhuma rede: o ponto de injeção `origemDaConexao` troca o IP real.
 */
class ProxyConfiavelTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    private fun faixas(vararg texto: String) = texto.map { FaixaCidr.parse(it) }

    private fun resolver(
        conexao: String,
        cabecalhos: Map<String, String>,
        confiaveis: List<FaixaCidr>,
        host: String = "painel.exemplo.invalid",
    ) = resolverOrigem(conexao, { nome -> cabecalhos.entries.firstOrNull { it.key.equals(nome, ignoreCase = true) }?.value }, confiaveis, host)

    // ---------------------------------------------------------------- FaixaCidr

    @Test
    fun faixaIpv4EIpv6() {
        val v4 = FaixaCidr.parse("10.0.0.0/24")
        assertTrue(v4.contem("10.0.0.5") && v4.contem("10.0.0.255"))
        assertFalse(v4.contem("10.0.1.5") || v4.contem("203.0.113.9") || v4.contem("::1"))
        val unico = FaixaCidr.parse("127.0.0.1/32")
        assertTrue(unico.contem("127.0.0.1"))
        assertFalse(unico.contem("127.0.0.2"))
        val v6 = FaixaCidr.parse("::1/128")
        assertTrue(v6.contem("::1") && v6.contem("0:0:0:0:0:0:0:1"))
        assertFalse(v6.contem("::2") || v6.contem("127.0.0.1"))
        assertTrue(FaixaCidr.parse("2001:db8::/32").contem("2001:db8:1::5"))
        assertFalse(FaixaCidr.parse("2001:db8::/32").contem("2001:db9::5"))
        // sem prefixo = um endereço só; prefixo 0 = tudo da família
        assertTrue(FaixaCidr.parse("192.0.2.7").contem("192.0.2.7"))
        assertFalse(FaixaCidr.parse("192.0.2.7").contem("192.0.2.8"))
        assertTrue(FaixaCidr.parse("0.0.0.0/0").contem("203.0.113.9"))
    }

    @Test
    fun ipQueNaoEhLiteralNaoCasaComNada() {
        val tudo = FaixaCidr.parse("0.0.0.0/0")
        for (ruim in listOf("", "localhost", "abc", "1.2.3", "1.2.3.4.5", "999.1.1.1", "10.0.0.5, 10.0.0.6", " ")) {
            assertFalse(tudo.contem(ruim), ruim)
        }
    }

    @Test
    fun cidrInvalidoNaoSobe() {
        for (ruim in listOf("", "abc", "10.0.0.0/33", "10.0.0.0/-1", "::/129", "10.0.0.0/", "10.0.0.0/24/1", "10.0.0/24", "host.invalid/24", "10.0.0.0/x")) {
            val e = assertFailsWith<IllegalArgumentException>(ruim) { FaixaCidr.parse(ruim) }
            if (ruim.length > 2) assertFalse(ruim in (e.message ?: ""), "a mensagem não ecoa o valor: $ruim")
        }
    }

    // ---------------------------------------------------------------- resolverOrigem

    @Test
    fun forwardedDeOrigemNaoConfiavelIgnorado() {
        val o = resolver(
            "203.0.113.9",
            mapOf("X-Forwarded-Proto" to "https", "X-Forwarded-Host" to "atacante.invalid", "X-Forwarded-For" to "198.51.100.7"),
            faixas("10.0.0.0/24"),
        )
        assertEquals(OrigemDaChamada(ip = "203.0.113.9", https = false, host = "painel.exemplo.invalid"), o)
    }

    @Test
    fun forwardedDoProxyAceito() {
        val o = resolver(
            "10.0.0.5",
            mapOf("X-Forwarded-Proto" to "https", "X-Forwarded-For" to "203.0.113.9", "X-Forwarded-Host" to "painel.publico.invalid"),
            faixas("10.0.0.0/24"),
        )
        assertEquals(OrigemDaChamada(ip = "203.0.113.9", https = true, host = "painel.publico.invalid"), o)
    }

    @Test
    fun proxyLocalLoopback() {
        for (conexao in listOf("127.0.0.1", "::1")) {
            val o = resolver(conexao, mapOf("X-Forwarded-Proto" to "https", "X-Forwarded-For" to "203.0.113.9"), faixas("127.0.0.1/32", "::1/128"))
            assertTrue(o.https, conexao)
            assertEquals("203.0.113.9", o.ip, conexao)
        }
    }

    @Test
    fun xffForjadoPeloClienteNaoVence() {
        // O cliente mandou "198.51.100.7"; o proxy acrescentou à direita o endereço real "203.0.113.50".
        val o = resolver("10.0.0.5", mapOf("X-Forwarded-For" to "198.51.100.7, 203.0.113.50"), faixas("10.0.0.0/24"))
        assertEquals("203.0.113.50", o.ip)
    }

    @Test
    fun cadeiaDeProxiesConfiaveis() {
        val o = resolver("10.0.0.5", mapOf("X-Forwarded-For" to "203.0.113.9, 10.0.0.7"), faixas("10.0.0.0/24"))
        assertEquals("203.0.113.9", o.ip)
    }

    @Test
    fun xffTodoConfiavelUsaOMaisAEsquerda() {
        val o = resolver("10.0.0.5", mapOf("X-Forwarded-For" to "10.0.0.9, 10.0.0.7"), faixas("10.0.0.0/24"))
        assertEquals("10.0.0.9", o.ip)
    }

    @Test
    fun xffComLixoNaoViraIp() {
        val o = resolver("10.0.0.5", mapOf("X-Forwarded-For" to "203.0.113.9, nao-eh-ip"), faixas("10.0.0.0/24"))
        assertEquals("10.0.0.5", o.ip, "entrada ilegível: fica o IP da conexão, nunca o lixo")
        assertEquals("10.0.0.5", resolver("10.0.0.5", mapOf("X-Forwarded-For" to ""), faixas("10.0.0.0/24")).ip)
    }

    @Test
    fun protoEHostIlegiveisSaoIgnorados() {
        val o = resolver(
            "10.0.0.5",
            mapOf("X-Forwarded-Proto" to "gopher", "X-Forwarded-Host" to "a b/c"),
            faixas("10.0.0.0/24"),
        )
        assertEquals(OrigemDaChamada("10.0.0.5", https = false, host = "painel.exemplo.invalid"), o)
        // lista separada por vírgula: vale o primeiro (o mais próximo do cliente)
        val p = resolver("10.0.0.5", mapOf("X-Forwarded-Proto" to "https, http", "X-Forwarded-Host" to "a.invalid, b.invalid"), faixas("10.0.0.0/24"))
        assertTrue(p.https)
        assertEquals("a.invalid", p.host)
    }

    @Test
    fun faixaVaziaNaoConfiaEmNinguem() {
        val o = resolver("127.0.0.1", mapOf("X-Forwarded-Proto" to "https", "X-Forwarded-For" to "203.0.113.9"), emptyList())
        assertEquals(OrigemDaChamada("127.0.0.1", https = false, host = "painel.exemplo.invalid"), o)
    }

    // ---------------------------------------------------------------- plugin

    private fun eco(conexao: String, confiaveis: List<FaixaCidr>, cabecalhos: Map<String, String>): String {
        var corpo = ""
        testApplication {
            application {
                instalarProxyConfiavel(confiaveis) { conexao }
                routing { get("/eco") { call.respondText("${call.request.origin.scheme}|${call.request.origin.serverHost}|${call.request.origin.remoteAddress}") } }
            }
            corpo = client.get("/eco") { cabecalhos.forEach { (k, v) -> header(k, v) } }.bodyAsText()
        }
        return corpo
    }

    @Test
    fun pluginAjustaEsquemaHostEIpSoDoProxy() {
        val cab = mapOf("X-Forwarded-Proto" to "https", "X-Forwarded-Host" to "painel.publico.invalid", "X-Forwarded-For" to "203.0.113.9")
        assertEquals("https|painel.publico.invalid|203.0.113.9", eco("10.0.0.5", faixas("10.0.0.0/24"), cab))
        val sem = eco("203.0.113.9", faixas("10.0.0.0/24"), cab).split("|")
        assertEquals("http", sem[0])
        assertFalse(sem[1] == "painel.publico.invalid")
    }

    // ---------------------------------------------------------------- login atrás do proxy

    private val relogio = RelogioDeTeste(Instant.parse("2026-10-28T15:00:00Z"))
    private val emissor = EmissorOidcFalso()
    private val redirect = "https://painel.exemplo.invalid/auth/callback"

    private fun comLogin(bloco: suspend io.ktor.server.testing.ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) {
        val cfg = OidcSettings(
            issuer = emissor.issuer, clientId = emissor.clientId, clientSecret = null, redirectUri = redirect,
            sessao = SegredosDaSessao.de(Base64.getEncoder().encodeToString(ByteArray(48) { (it * 3 + 1).toByte() }), null),
        )
        val settings = Settings(dashboardDevMode = false, oidc = cfg)
        val ctx = testContextComBanco(settings, banco, clock = relogio)
        val autenticador = OidcAuthenticator(cfg, ctx.roles, relogio, emissor.cliente(), null)
        testApplication {
            application {
                instalarProxyConfiavel(faixas("10.0.0.0/24")) { "10.0.0.5" }
                module(settings, ctx, autenticador)
            }
            bloco(createClient { followRedirects = false })
        }
    }

    @Test
    fun redirectUriNuncaVemDeCabecalho() = comLogin { cliente ->
        val r = cliente.get("/auth/login") {
            header("X-Forwarded-Host", "atacante.invalid")
            header("X-Forwarded-Proto", "https")
            header(HttpHeaders.Host, "atacante.invalid")
        }
        assertEquals(HttpStatusCode.Found, r.status)
        val loc = r.headers[HttpHeaders.Location]!!
        assertTrue(loc.startsWith("${emissor.issuer}/authorize"), loc)
        assertEquals(redirect, Url(loc).parameters["redirect_uri"])
        assertFalse("atacante" in loc)
    }

    @Test
    fun cookieSecureAtrasDoProxy() = comLogin { cliente ->
        val login = cliente.get("/auth/login") { header("X-Forwarded-Proto", "https") }
        val loc = Url(login.headers[HttpHeaders.Location]!!)
        val cookieLogin = login.setCookie().first { it.name == COOKIE_DE_LOGIN }
        emissor.desafioEsperado = loc.parameters["code_challenge"]
        val agora = relogio.instant().epochSecond
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(loc.parameters["nonce"]!!, "dev.alfa1", agora))
        val cb = cliente.get("/auth/callback?code=c1&state=${loc.parameters["state"]}") {
            header(HttpHeaders.Cookie, "$COOKIE_DE_LOGIN=${cookieLogin.value}")
            header("X-Forwarded-Proto", "https")
        }
        assertEquals(HttpStatusCode.Found, cb.status)
        val sessao = assertNotNull(cb.setCookie().firstOrNull { it.name == COOKIE_DE_SESSAO })
        assertTrue(sessao.secure && sessao.httpOnly)
        assertEquals("Lax", sessao.extensions["SameSite"])
    }

    // ---------------------------------------------------------------- Settings

    @Test
    fun settingsLeAListaDeCidr() {
        assertEquals(emptyList(), Settings.fromEnv(emptyMap()).proxyConfiavel.map { it.toString() })
        val s = Settings.fromEnv(mapOf("DASHBOARD_PROXY_CONFIAVEL" to " 127.0.0.1/32 , ::1/128,"))
        assertEquals(2, s.proxyConfiavel.size)
        assertTrue(s.proxyConfiavel[0].contem("127.0.0.1") && s.proxyConfiavel[1].contem("::1"))
    }

    @Test
    fun settingsCidrInvalidoNaoSobeCitandoSoONomeDaVariavel() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("DASHBOARD_PROXY_CONFIAVEL" to "10.0.0.0/99,valor-secreto")) }
        assertTrue("DASHBOARD_PROXY_CONFIAVEL" in e.message!!)
        assertFalse("valor-secreto" in e.message!!)
    }

    @Test
    fun devModeNaoExigeProxy() {
        // Sem a variável, o modo de desenvolvimento sobe normalmente (a obrigatoriedade em produção é da GuardasDeProducao).
        assertEquals(emptyList(), Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "true")).proxyConfiavel.map { it.toString() })
    }
}
