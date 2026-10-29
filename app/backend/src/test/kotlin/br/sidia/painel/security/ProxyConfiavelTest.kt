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
    fun prefixosForaDoLimiteDeOcteto() {
        val a = FaixaCidr.parse("10.0.0.128/25")
        assertTrue(a.contem("10.0.0.200") && a.contem("10.0.0.128") && a.contem("10.0.0.255"))
        assertFalse(a.contem("10.0.0.127") || a.contem("10.0.0.100"))
        val b = FaixaCidr.parse("172.16.0.0/12")
        assertTrue(b.contem("172.31.255.255") && b.contem("172.16.0.1"))
        assertFalse(b.contem("172.32.0.0") || b.contem("172.15.255.255"))
        val c = FaixaCidr.parse("128.0.0.0/1")
        assertTrue(c.contem("200.1.1.1"))
        assertFalse(c.contem("100.1.1.1"))
        val d = FaixaCidr.parse("2001:db8:8000::/33")
        assertTrue(d.contem("2001:db8:ffff::1"))
        assertFalse(d.contem("2001:db8:7fff::1"))
        // IPv4 mapeado em CIDR IPv6 vira 4 bytes no JDK: um prefixo de IPv6 não faz sentido e é recusado
        assertFailsWith<IllegalArgumentException> { FaixaCidr.parse("::ffff:10.0.0.0/104") }
    }

    @Test
    fun entradaQueComecaComPontoNaoEhLiteralENuncaConsultaNome() {
        assertFailsWith<IllegalArgumentException> { FaixaCidr.parse(".:1") }
        assertFailsWith<IllegalArgumentException> { FaixaCidr.parse("..::") }
        assertFalse(FaixaCidr.parse("0.0.0.0/0").contem(".:1"))
        // XFF com esse lixo à direita nunca vira IP
        val o = resolver("10.0.0.5", mapOf("X-Forwarded-For" to "203.0.113.9, .:1"), faixas("10.0.0.0/24"))
        assertEquals("10.0.0.5", o.ip)
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
        // valores divergentes na lista: ninguém é crido (o da esquerda pode ser do cliente)
        val p = resolver("10.0.0.5", mapOf("X-Forwarded-Proto" to "https, http", "X-Forwarded-Host" to "a.invalid, b.invalid"), faixas("10.0.0.0/24"))
        assertFalse(p.https)
        assertEquals("painel.exemplo.invalid", p.host)
        // valores iguais valem
        val q = resolver("10.0.0.5", mapOf("X-Forwarded-Proto" to "https, HTTPS", "X-Forwarded-Host" to "a.invalid, A.invalid"), faixas("10.0.0.0/24"))
        assertTrue(q.https)
        assertEquals("a.invalid", q.host)
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

    /** Como [eco], mas com cada cabeçalho em LINHAS separadas (proxies como o HAProxy acrescentam uma linha nova) e a porta. */
    private fun ecoComLinhas(conexao: String, confiaveis: List<FaixaCidr>, cabecalhos: List<Pair<String, String>>): String {
        var corpo = ""
        testApplication {
            application {
                instalarProxyConfiavel(confiaveis) { conexao }
                routing {
                    get("/eco") { call.respondText("${call.request.origin.scheme}|${call.request.origin.serverHost}|${call.request.origin.remoteAddress}|${call.request.origin.serverPort}") }
                }
            }
            corpo = client.get("/eco") { cabecalhos.forEach { (k, v) -> header(k, v) } }.bodyAsText()
        }
        return corpo
    }

    @Test
    fun xffEmLinhasSeparadasOClienteForjaAPrimeiraEOProxyAcrescentaAUltima() {
        val r = ecoComLinhas(
            "10.0.0.5", faixas("10.0.0.0/24"),
            listOf("X-Forwarded-For" to "198.51.100.7", "X-Forwarded-For" to "203.0.113.50"),
        )
        assertEquals("203.0.113.50", r.split("|")[2], "vale a linha do proxy, não a do cliente")
    }

    @Test
    fun xffEmLinhasSeparadasComCadeiaConfiavel() {
        val r = ecoComLinhas(
            "10.0.0.5", faixas("10.0.0.0/24"),
            listOf("X-Forwarded-For" to "198.51.100.7", "X-Forwarded-For" to "203.0.113.9", "X-Forwarded-For" to "10.0.0.7"),
        )
        assertEquals("203.0.113.9", r.split("|")[2])
    }

    @Test
    fun hostEProtoDivergentesEmLinhasSeparadasSaoIgnorados() {
        // o cliente mandou X-Forwarded-Host/Proto e o proxy (que também os define) acrescentou os seus: divergem, ninguém é crido
        val r = ecoComLinhas(
            "10.0.0.5", faixas("10.0.0.0/24"),
            listOf(
                "X-Forwarded-Host" to "atacante.invalid", "X-Forwarded-Host" to "painel.publico.invalid",
                "X-Forwarded-Proto" to "http", "X-Forwarded-Proto" to "https",
            ),
        )
        val partes = r.split("|")
        assertEquals("http", partes[0])
        assertTrue(partes[1] != "atacante.invalid" && partes[1] != "painel.publico.invalid", r)
    }

    @Test
    fun hostEProtoIguaisEmVariasLinhasValem() {
        val r = ecoComLinhas(
            "10.0.0.5", faixas("10.0.0.0/24"),
            listOf("X-Forwarded-Host" to "painel.publico.invalid", "X-Forwarded-Host" to "painel.publico.invalid", "X-Forwarded-Proto" to "https"),
        )
        assertEquals("https|painel.publico.invalid", r.substringBeforeLast("|").substringBeforeLast("|"))
    }

    @Test
    fun hostIpv6ComPortaNaoEhEstragado() {
        val r = ecoComLinhas("10.0.0.5", faixas("10.0.0.0/24"), listOf("X-Forwarded-Host" to "[2001:db8::1]:8443", "X-Forwarded-Proto" to "https"))
        val p = r.split("|")
        assertEquals("2001:db8::1", p[1].removeSurrounding("[", "]"))
        assertEquals("8443", p[3])
        // sem porta
        val s = ecoComLinhas("10.0.0.5", faixas("10.0.0.0/24"), listOf("X-Forwarded-Host" to "[2001:db8::1]"))
        assertEquals("2001:db8::1", s.split("|")[1].removeSurrounding("[", "]"))
    }

    @Test
    fun conexaoNaoConfiavelNaoMudaNadaNemOHost() {
        val normal = ecoComLinhas("203.0.113.9", faixas("10.0.0.0/24"), emptyList())
        val forjado = ecoComLinhas(
            "203.0.113.9", faixas("10.0.0.0/24"),
            listOf("X-Forwarded-Host" to "[::1]:9", "X-Forwarded-Proto" to "https", "X-Forwarded-For" to "198.51.100.7"),
        )
        assertEquals(normal.split("|")[0], forjado.split("|")[0])
        assertEquals(normal.split("|")[1], forjado.split("|")[1])
        assertEquals(normal.split("|")[3], forjado.split("|")[3])
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
