package br.sidia.painel.security

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.comCsrf
import br.sidia.painel.config.Settings
import br.sidia.painel.limparAccessLog
import br.sidia.painel.module
import br.sidia.painel.operacao.RelogioDeTeste
import br.sidia.painel.testContextComBanco
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.setCookie
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Login SSO/OIDC de ponta a ponta contra um emissor FALSO (MockEngine, host `.invalid`, chaves RSA geradas no teste).
 * Banco SQLite real em arquivo temporário. Nenhuma requisição sai da máquina.
 */
class OidcTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    private val b64 = Base64.getEncoder()
    private fun segredo(semente: Int): String = b64.encodeToString(ByteArray(48) { (it * 5 + semente).toByte() })

    private val inicio = Instant.parse("2026-10-28T15:00:00Z")
    private val relogio = RelogioDeTeste(inicio)
    private val emissor = EmissorOidcFalso()
    private val agoraSeg get() = relogio.instant().epochSecond

    private fun configuracao(
        atual: String = segredo(1),
        anterior: String? = null,
        duracao: Duration = Duration.ofHours(8),
    ) = OidcSettings(
        issuer = emissor.issuer,
        clientId = emissor.clientId,
        clientSecret = "segredo-do-cliente-ficticio-1",
        redirectUri = "https://painel.exemplo.invalid/auth/callback",
        sessao = SegredosDaSessao.de(atual, anterior),
        duracaoDaSessao = duracao,
    )

    private class Cenario(val ctx: AppContext, val autenticador: OidcAuthenticator, val cliente: HttpClient)

    /** Sobe o painel em produção com o autenticador OIDC e rotas de teste; [bloco] roda dentro do `testApplication`. */
    private fun comApp(
        cfg: OidcSettings = configuracao(),
        papeis: RoleDirectory? = null,
        inicioDaRotacao: Instant? = null,
        bloco: suspend ApplicationTestBuilder.(Cenario) -> Unit,
    ) {
        val settings = Settings(dashboardDevMode = false, oidc = cfg)
        val ctx = papeis?.let { testContextComBanco(settings, banco, roles = it, clock = relogio) }
            ?: testContextComBanco(settings, banco, clock = relogio)
        val autenticador = OidcAuthenticator(cfg, ctx.roles, relogio, emissor.cliente(), inicioDaRotacao)
        testApplication {
            application {
                module(settings, ctx, autenticador)
                routing {
                    get("/api/eu") { call.respondText(call.principal.userId + ":" + call.principal.roles.map { it.name }.sorted().joinToString(",")) }
                    requireRole(Role.GERENTE) { get("/api/so-gerente") { call.respondText("ok") } }
                }
            }
            val cliente = createClient { followRedirects = false }
            bloco(Cenario(ctx, autenticador, cliente))
        }
    }

    private class Inicio(val state: String, val nonce: String, val desafio: String, val cookie: String, val localizacao: Url)

    private suspend fun iniciar(c: Cenario, volta: String? = null): Inicio {
        val r = c.cliente.get("/auth/login" + (volta?.let { "?volta=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""))
        assertEquals(HttpStatusCode.Found, r.status)
        val loc = Url(r.headers[HttpHeaders.Location]!!)
        val cookie = r.setCookie().first { it.name == COOKIE_DE_LOGIN }.value
        return Inicio(loc.parameters["state"]!!, loc.parameters["nonce"]!!, loc.parameters["code_challenge"]!!, cookie, loc)
    }

    private suspend fun voltar(c: Cenario, i: Inicio, code: String? = "codigo-de-autorizacao-1", state: String? = i.state, cookie: String? = i.cookie): HttpResponse =
        c.cliente.get("/auth/callback?" + listOfNotNull(code?.let { "code=$it" }, state?.let { "state=$it" }).joinToString("&")) {
            cookie?.let { header(HttpHeaders.Cookie, "$COOKIE_DE_LOGIN=$it") }
        }

    /** Faz o login inteiro e devolve a resposta do callback. [claims] ajusta as claims do token (ou o troca por completo). */
    private suspend fun entrar(
        c: Cenario,
        usuario: String = "dev.alfa1",
        extras: Map<String, Any?> = emptyMap(),
        volta: String? = null,
        idToken: ((Inicio) -> String)? = null,
    ): HttpResponse {
        val i = iniciar(c, volta)
        emissor.desafioEsperado = i.desafio
        emissor.idTokenDaTroca = idToken?.invoke(i) ?: emissor.assinar(emissor.claimsValidas(i.nonce, usuario, agoraSeg, extras))
        return voltar(c, i)
    }

    private fun sessaoDe(r: HttpResponse): String? = r.setCookie().firstOrNull { it.name == COOKIE_DE_SESSAO }?.value

    private suspend fun comoEu(c: Cenario, sessao: String?): HttpResponse = c.cliente.get("/api/eu") {
        sessao?.let { header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$it") }
    }

    private fun linhasDeAcesso(ctx: AppContext): List<Triple<String, String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3))) }
            }
        }

    // ---------------------------------------------------------------- caminho feliz

    @Test
    fun tokenValidoViraPrincipalDoDiretorio() = comApp { c ->
        val r = entrar(c, usuario = "Dev.Alfa1")
        assertEquals(HttpStatusCode.Found, r.status)
        assertEquals("/", r.headers[HttpHeaders.Location])
        val sessao = assertNotNull(sessaoDe(r))
        val eu = comoEu(c, sessao)
        assertEquals(HttpStatusCode.OK, eu.status)
        assertEquals("dev.alfa1:USUARIO", eu.bodyAsText())
        // sem o cookie, 401
        assertEquals(HttpStatusCode.Unauthorized, comoEu(c, null).status)
    }

    @Test
    fun gerenteDoDiretorioVirarGerente() = comApp { c ->
        val sessao = assertNotNull(sessaoDe(entrar(c, usuario = "gerente.exemplo")))
        assertEquals("gerente.exemplo:GERENTE", comoEu(c, sessao).bodyAsText())
        assertEquals(HttpStatusCode.OK, c.cliente.get("/api/so-gerente") { header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$sessao") }.status)
    }

    @Test
    fun claimDePapelIgnorada() = comApp { c ->
        // O token afirma ser GERENTE, mas o diretório diz USUARIO: vale o diretório.
        val r = entrar(c, usuario = "dev.alfa1", extras = mapOf("roles" to listOf("GERENTE"), "groups" to listOf("GERENTE"), "role" to "GERENTE"))
        val sessao = assertNotNull(sessaoDe(r))
        assertEquals("dev.alfa1:USUARIO", comoEu(c, sessao).bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, c.cliente.get("/api/so-gerente") { header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$sessao") }.status)
    }

    @Test
    fun urlDeAutorizacaoTraParametrosDoFluxoComPkce() = comApp { c ->
        val i = iniciar(c)
        val p = i.localizacao.parameters
        assertEquals("sso.exemplo.invalid", i.localizacao.host)
        assertEquals("/authorize", i.localizacao.encodedPath)
        assertEquals("code", p["response_type"])
        assertEquals(emissor.clientId, p["client_id"])
        assertEquals("https://painel.exemplo.invalid/auth/callback", p["redirect_uri"])
        assertEquals("openid profile", p["scope"])
        assertEquals("S256", p["code_challenge_method"])
        assertTrue(i.state.length >= 32 && i.nonce.length >= 32 && i.desafio.length >= 43)
        assertTrue(i.state != i.nonce)
        // cada login gera valores novos
        val j = iniciar(c)
        assertTrue(i.state != j.state && i.nonce != j.nonce && i.desafio != j.desafio && i.cookie != j.cookie)
    }

    @Test
    fun trocaDoCodigoUsaPkceECredenciaisDoCliente() = comApp { c ->
        val r = entrar(c)
        assertEquals(HttpStatusCode.Found, r.status) // o emissor falso só aceita se o verificador bate com o desafio
        val troca = emissor.ultimaTroca
        assertEquals("authorization_code", troca["grant_type"])
        assertEquals("codigo-de-autorizacao-1", troca["code"])
        assertEquals("https://painel.exemplo.invalid/auth/callback", troca["redirect_uri"])
        assertEquals(emissor.clientId, troca["client_id"])
        assertEquals("segredo-do-cliente-ficticio-1", troca["client_secret"])
        assertTrue((troca["code_verifier"] ?: "").length in 43..128)
    }

    @Test
    fun verificadorPkceErradoEhRecusadoPeloProvedor() = comApp { c ->
        val i = iniciar(c)
        emissor.desafioEsperado = "desafio-de-outro-login"
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(i.nonce, "dev.alfa1", agoraSeg))
        val r = voltar(c, i)
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
    }

    @Test
    fun voltaSoParaCaminhoInternoDoPainel() = comApp { c ->
        assertEquals("/abertas?squad=Alfa", entrar(c, volta = "/abertas?squad=Alfa").headers[HttpHeaders.Location])
        for (ruim in listOf("//evil.invalid/x", "https://evil.invalid", "/\\evil.invalid", "/auth/login", "javascript:alert(1)", "abertas")) {
            assertEquals("/", entrar(c, volta = ruim).headers[HttpHeaders.Location], ruim)
        }
    }

    // ---------------------------------------------------------------- recusas

    private suspend fun assertRecusa(c: Cenario, status: HttpStatusCode, r: HttpResponse, codigo: String) {
        assertEquals(status, r.status, codigo)
        assertNull(sessaoDe(r), "recusa não abre sessão")
        assertContains(linhasDeAcesso(c.ctx).last().third, codigo)
        assertFalse(r.bodyAsText().contains(codigo), "o corpo da resposta não detalha o motivo")
    }

    @Test
    fun assinaturaInvalida401() = comApp { c ->
        val intrusa = emissor.novaChave()
        val r = entrar(c, idToken = { emissor.assinar(emissor.claimsValidas(it.nonce, "gerente.exemplo", agoraSeg), privada = intrusa.private) })
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_ASSINATURA_INVALIDA")
    }

    @Test
    fun expirado401() = comApp { c ->
        val r = entrar(c, extras = mapOf("exp" to agoraSeg - 300))
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_EXPIRADO")
    }

    @Test
    fun issuerErrado401() = comApp { c ->
        val r = entrar(c, extras = mapOf("iss" to "https://outro.exemplo.invalid"))
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_EMISSOR_ERRADO")
    }

    @Test
    fun audienciaErrada401() = comApp { c ->
        val r = entrar(c, extras = mapOf("aud" to "outro-cliente"))
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_AUDIENCIA_ERRADA")
    }

    @Test
    fun algoritmoNone401() = comApp { c ->
        val r = entrar(c, idToken = { emissor.assinar(emissor.claimsValidas(it.nonce, "gerente.exemplo", agoraSeg), alg = "none", assinar = false) + "x" })
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_ALGORITMO_NAO_ACEITO")
    }

    @Test
    fun kidDesconhecido401() = comApp { c ->
        val r = entrar(c, idToken = { emissor.assinar(emissor.claimsValidas(it.nonce, "gerente.exemplo", agoraSeg), kid = "chave-inexistente") })
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_KID_DESCONHECIDO")
    }

    @Test
    fun nonceDoTokenDiferenteDoLogin401() = comApp { c ->
        val r = entrar(c, idToken = { emissor.assinar(emissor.claimsValidas("nonce-de-outro-login", "dev.alfa1", agoraSeg)) })
        assertRecusa(c, HttpStatusCode.Unauthorized, r, "TOKEN_NONCE_ERRADO")
    }

    @Test
    fun stateOuCookieDeLoginErrado401() = comApp { c ->
        val a = iniciar(c)
        val b = iniciar(c)
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(a.nonce, "dev.alfa1", agoraSeg))
        // state de outro login
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, state = b.state), "STATE_INVALIDO")
        // state ausente
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, state = null), "STATE_INVALIDO")
        // sem o cookie de login (outro navegador)
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, cookie = null), "LOGIN_EXPIRADO_OU_INVALIDO")
        // cookie adulterado
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, cookie = a.cookie.dropLast(2) + "xx"), "LOGIN_EXPIRADO_OU_INVALIDO")
        // cookie de login assinado com outro segredo
        val forjado = CookieDeLogin(segredo(9).toByteArray(), relogio)
            .emitir(EstadoDeLogin("s", "n", "v".repeat(43), "/", relogio.instant().plusSeconds(60)))
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, cookie = forjado), "LOGIN_EXPIRADO_OU_INVALIDO")
        // sem o code
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, a, code = null), "CODE_AUSENTE")
    }

    @Test
    fun loginVenceEm10Minutos() = comApp { c ->
        val i = iniciar(c)
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(i.nonce, "dev.alfa1", agoraSeg))
        relogio.avancar(Duration.ofMinutes(11))
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, i), "LOGIN_EXPIRADO_OU_INVALIDO")
    }

    @Test
    fun nonceRepetidoRepetirOCallbackEhRecusado() = comApp { c ->
        val i = iniciar(c)
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(i.nonce, "dev.alfa1", agoraSeg))
        val primeiro = voltar(c, i)
        assertEquals(HttpStatusCode.Found, primeiro.status)
        assertNotNull(sessaoDe(primeiro))
        // quem copiou a URL do callback e o cookie de login e os repete não entra
        val repetido = voltar(c, i)
        assertRecusa(c, HttpStatusCode.Unauthorized, repetido, "LOGIN_REUTILIZADO")
    }

    @Test
    fun provedorNegouOuCodigoRecusado401() = comApp { c ->
        val negou = c.cliente.get("/auth/callback?error=access_denied")
        assertRecusa(c, HttpStatusCode.Unauthorized, negou, "PROVEDOR_NEGOU")
        val i = iniciar(c)
        emissor.idTokenDaTroca = null // a troca responde invalid_grant
        assertRecusa(c, HttpStatusCode.Unauthorized, voltar(c, i), "CODIGO_RECUSADO")
    }

    @Test
    fun usuarioSemPapelVigente403() = comApp { c ->
        val r = entrar(c, usuario = "sem.papel.nenhum")
        assertRecusa(c, HttpStatusCode.Forbidden, r, "SEM_PAPEL_VIGENTE")
    }

    @Test
    fun usernameInvalidoDoTokenEhRecusado() = comApp { c ->
        for (ruim in listOf("ana@exemplo.invalid", "a b", "../x", "x".repeat(65))) {
            assertRecusa(c, HttpStatusCode.Unauthorized, entrar(c, usuario = ruim), "USUARIO_INVALIDO")
        }
    }

    @Test
    fun papelQueEncerraComASessaoAbertaVira403() {
        val yaml = Files.createTempFile("papeis-teste", ".yaml")
        try {
            // O papel de temp.vigente acaba hoje (28/10): a leitura é viva, a sessão aberta perde o acesso no dia seguinte.
            Files.writeString(
                yaml,
                "pessoas:\n  - id: temp.vigente\n    nome: Temporaria Ficticia\n    papeis:\n      - papel: USUARIO\n        squad: Alfa\n        ate: 2026-10-28\n  - id: gerente.exemplo\n    nome: G\n    papeis:\n      - papel: GERENTE\n",
            )
            comApp(configuracao(duracao = Duration.ofHours(24)), papeis = RoleDirectory.fromYaml(yaml)) { c ->
                val sessao = assertNotNull(sessaoDe(entrar(c, usuario = "temp.vigente")))
                assertEquals("temp.vigente:USUARIO", comoEu(c, sessao).bodyAsText())
                relogio.avancar(Duration.ofHours(14)) // já é 29/10 em Manaus; a sessão (24 h) continua válida
                assertEquals(HttpStatusCode.Forbidden, comoEu(c, sessao).status)
            }
        } finally {
            Files.deleteIfExists(yaml)
        }
    }

    // ---------------------------------------------------------------- sessão, cookies e logout

    @Test
    fun cookieComFlagsSeguras() = comApp { c ->
        val login = c.cliente.get("/auth/login")
        val doLogin = login.headers.getAll(HttpHeaders.SetCookie)!!.single { it.startsWith("$COOKIE_DE_LOGIN=") }
        for (flag in listOf("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=600")) assertContains(doLogin, flag)
        assertFalse(doLogin.contains("Domain", ignoreCase = true))

        val r = entrar(c)
        val doSessao = r.headers.getAll(HttpHeaders.SetCookie)!!.single { it.startsWith("$COOKIE_DE_SESSAO=") }
        for (flag in listOf("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=28800")) assertContains(doSessao, flag)
        assertFalse(doSessao.contains("Domain", ignoreCase = true))
        // o valor da sessão não carrega o token nem o código: é só o cookie assinado
        assertTrue(doSessao.startsWith("$COOKIE_DE_SESSAO=v1."))
        // e o cookie de login é apagado na volta
        assertTrue(r.headers.getAll(HttpHeaders.SetCookie)!!.any { it.startsWith("$COOKIE_DE_LOGIN=;") || (it.startsWith("$COOKIE_DE_LOGIN=") && it.contains("Max-Age=0")) })
    }

    @Test
    fun sessaoVenceNaDuracaoConfigurada() = comApp { c ->
        val sessao = assertNotNull(sessaoDe(entrar(c)))
        relogio.avancar(Duration.ofHours(8).minusSeconds(5))
        assertEquals(HttpStatusCode.OK, comoEu(c, sessao).status)
        relogio.avancar(Duration.ofSeconds(10))
        assertEquals(HttpStatusCode.Unauthorized, comoEu(c, sessao).status)
    }

    @Test
    fun cookieAdulteradoOuDeOutroSegredoEhRecusado() = comApp { c ->
        val sessao = assertNotNull(sessaoDe(entrar(c)))
        assertEquals(HttpStatusCode.Unauthorized, comoEu(c, sessao.dropLast(2) + "xx").status)
        val outro = SessaoAssinada(SegredosDaSessao.de(segredo(7), null), Duration.ofHours(8)) { relogio.instant() }.emitir("gerente.exemplo")
        assertEquals(HttpStatusCode.Unauthorized, comoEu(c, outro).status)
    }

    @Test
    fun logoutLimpaSessao() = comApp { c ->
        val sessao = assertNotNull(sessaoDe(entrar(c)))
        val certo = c.cliente.post("/auth/logout") {
            comCsrf()
            header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$sessao")
        }
        assertEquals(HttpStatusCode.NoContent, certo.status)
        val cookie = certo.headers.getAll(HttpHeaders.SetCookie)!!.single { it.startsWith("$COOKIE_DE_SESSAO=") }
        assertContains(cookie, "Max-Age=0")
        assertTrue(cookie.startsWith("$COOKIE_DE_SESSAO=;"), cookie)
        val linha = linhasDeAcesso(c.ctx).last()
        assertEquals("dev.alfa1", linha.first)
        assertEquals("/auth/logout", linha.second)
    }

    @Test
    fun logoutExigeOCabecalhoAntiCsrf() = comApp { c ->
        val sessao = assertNotNull(sessaoDe(entrar(c)))
        val r = c.cliente.post("/auth/logout") { header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$sessao") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }

    @Test
    fun logoutFuncionaTambemComSessaoVencida() = comApp { c ->
        val r = c.cliente.post("/auth/logout") {
            comCsrf()
            header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=lixo")
        }
        assertEquals(HttpStatusCode.NoContent, r.status)
        assertEquals("anonymous", linhasDeAcesso(c.ctx).last().first)
    }

    // ---------------------------------------------------------------- rotação do segredo e do JWKS

    @Test
    fun rotacaoDoSegredoRegravaCookieSemEstenderOPrazo() {
        val antigo = segredo(1)
        val novo = segredo(2)
        // 1) sessão emitida quando só o segredo antigo existia
        var cookieAntigo: String? = null
        comApp(configuracao(atual = antigo)) { c -> cookieAntigo = sessaoDe(entrar(c)) }
        val velho = assertNotNull(cookieAntigo)
        relogio.avancar(Duration.ofHours(2))
        // 2) depois da rotação (atual = novo, anterior = antigo) a mesma sessão segue valendo e é regravada com o atual
        comApp(configuracao(atual = novo, anterior = antigo), inicioDaRotacao = relogio.instant()) { c ->
            val r = comoEu(c, velho)
            assertEquals(HttpStatusCode.OK, r.status)
            val regravado = r.setCookie().single { it.name == COOKIE_DE_SESSAO }
            // faltam 6 h dos 8 h originais: a regravação não estende
            assertEquals(6 * 3600, regravado.maxAge)
            assertEquals("dev.alfa1:USUARIO", comoEu(c, regravado.value).bodyAsText())
            assertTrue(comoEu(c, regravado.value).setCookie().none { it.name == COOKIE_DE_SESSAO }, "já está no segredo atual")
        }
        // 3) sem o anterior, a sessão velha cai; a regravada continua
        comApp(configuracao(atual = novo)) { c -> assertEquals(HttpStatusCode.Unauthorized, comoEu(c, velho).status) }
    }

    @Test
    fun chaveNovaDoProvedorEhBuscadaDeNovoMasComLimite() = comApp { c ->
        assertEquals(HttpStatusCode.Found, entrar(c).status)
        val buscasIniciais = emissor.requisicoes.count { it.endsWith("/jwks") }
        assertEquals(1, buscasIniciais)
        // o provedor troca de chave
        emissor.chave = emissor.novaChave()
        emissor.kid = "chave-2"
        emissor.publicadas.clear()
        emissor.publicadas["chave-2"] = emissor.chave.public as java.security.interfaces.RSAPublicKey
        // dentro do intervalo mínimo (1 min), a chave nova ainda não é buscada: recusa e não martela o provedor
        assertRecusa(c, HttpStatusCode.Unauthorized, entrar(c), "TOKEN_KID_DESCONHECIDO")
        assertEquals(1, emissor.requisicoes.count { it.endsWith("/jwks") })
        relogio.avancar(Duration.ofMinutes(2))
        assertEquals(HttpStatusCode.Found, entrar(c).status)
        assertEquals(2, emissor.requisicoes.count { it.endsWith("/jwks") })
    }

    @Test
    fun provedorForaDoArRetorna502SemSessao() = comApp { c ->
        emissor.statusDoJwks = HttpStatusCode.InternalServerError
        val r = entrar(c)
        assertRecusa(c, HttpStatusCode.BadGateway, r, "JWKS_FALHOU")
    }

    @Test
    fun descobertaDeOutroEmissorNaoRedireciona() = comApp { c ->
        emissor.emissorDaDescoberta = "https://outro.exemplo.invalid"
        val r = c.cliente.get("/auth/login")
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertNull(r.headers[HttpHeaders.Location])
        assertTrue(r.setCookie().none { it.name == COOKIE_DE_LOGIN })
    }

    // ---------------------------------------------------------------- auditoria, log e rede

    @Test
    fun loginELogoutGravamAccessLogSemToken() = comApp { c ->
        c.ctx.limparAccessLog()
        val i = iniciar(c)
        emissor.idTokenDaTroca = emissor.assinar(emissor.claimsValidas(i.nonce, "dev.alfa1", agoraSeg))
        val token = emissor.idTokenDaTroca!!
        val sessao = assertNotNull(sessaoDe(voltar(c, i)))
        val linhas = linhasDeAcesso(c.ctx)
        assertEquals(listOf("anonymous" to "/auth/login", "dev.alfa1" to "/auth/callback"), linhas.map { it.first to it.second })
        val tudo = linhas.joinToString("|")
        for (segredoDoTeste in listOf(token, sessao, i.state, i.nonce, i.cookie, "codigo-de-autorizacao-1")) {
            assertFalse(tudo.contains(segredoDoTeste))
        }
        assertContains(linhas.last().third, "ok")
    }

    @Test
    fun logNaoGuardaToken() {
        val raiz = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val acumulado = ListAppender<ILoggingEvent>().apply { start() }
        raiz.addAppender(acumulado)
        try {
            var segredos = emptyList<String>()
            comApp { c ->
                // um login que dá certo, um recusado por assinatura e um recusado por nonce
                val i = iniciar(c)
                val token = emissor.assinar(emissor.claimsValidas(i.nonce, "dev.alfa1", agoraSeg))
                emissor.idTokenDaTroca = token
                val ok = voltar(c, i)
                val ruim = iniciar(c)
                val tokenRuim = emissor.assinar(emissor.claimsValidas("nonce-errado", "dev.alfa1", agoraSeg))
                emissor.idTokenDaTroca = tokenRuim
                voltar(c, ruim)
                segredos = listOf(token, tokenRuim, assertNotNull(sessaoDe(ok)), i.state, i.nonce, i.cookie, ruim.state, ruim.nonce, "codigo-de-autorizacao-1", "segredo-do-cliente-ficticio-1")
            }
            val texto = acumulado.list.joinToString("\n") { it.formattedMessage + " " + (it.throwableProxy?.message ?: "") }
            for (s in segredos) assertFalse(texto.contains(s), "o log não pode conter um segredo do fluxo")
            assertContains(texto, "codigo=TOKEN_NONCE_ERRADO") // mas registra o MOTIVO
        } finally {
            raiz.detachAppender(acumulado)
        }
    }

    @Test
    fun nenhumaRequisicaoParaHostReal() = comApp { c ->
        entrar(c)
        entrar(c, extras = mapOf("aud" to "x"))
        assertTrue(emissor.requisicoes.isNotEmpty())
        assertTrue(emissor.requisicoes.all { it.startsWith("sso.exemplo.invalid/") }, emissor.requisicoes.toString())
    }

    // ---------------------------------------------------------------- rotas públicas e modo desenvolvimento

    @Test
    fun rotasPublicasIncluemProntidaoELogin() {
        for (r in listOf("/api/health", "/api/health/pronto", "/api/meta", "/auth/login", "/auth/callback", "/auth/logout")) {
            assertTrue(r in ROTAS_PUBLICAS, r)
        }
        assertFalse("/api/me" in ROTAS_PUBLICAS)
        assertTrue(exigeAutenticacao("/auth/qualquer"))
        assertTrue(exigeAutenticacao("/auth/login/outra"))
        assertFalse(exigeAutenticacao("/auth/login"))
    }

    @Test
    fun devModeContinuaSemLogin() = testApplication {
        val settings = Settings(dashboardDevMode = true)
        val ctx = testContextComBanco(settings, banco, clock = relogio)
        application {
            module(settings, ctx)
            routing { get("/api/eu") { call.respondText(call.principal.userId) } }
        }
        val c = createClient { followRedirects = false }
        assertEquals("dev", c.get("/api/eu").bodyAsText())
        // em desenvolvimento não há rotas de login nem cookie
        val login = c.get("/auth/login")
        assertTrue(login.status != HttpStatusCode.Found)
        assertTrue(login.setCookie().isEmpty())
    }

    @Test
    fun producaoSemOidcNaoMontaOAutenticadorPadrao() = testApplication {
        val settings = Settings(dashboardDevMode = false, oidc = null)
        val ctx = testContextComBanco(settings, banco, clock = relogio)
        val erro = runCatching { application { module(settings, ctx) }; client.get("/api/health") }.exceptionOrNull()
        assertNotNull(erro, "produção sem OIDC não pode subir")
        assertContains(generateSequence(erro) { it.cause }.joinToString { it.message ?: "" }, "OIDC_ISSUER")
    }
}
