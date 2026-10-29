package br.sidia.painel.security

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.operacao.RelogioDeTeste
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Plano 13, Tarefa 2: o login de ponta a ponta com o formato de username de cada perfil de provedor (Keycloak, Entra ID,
 * ADFS). Emissor falso (MockEngine, host `.invalid`), banco SQLite real em arquivo temporário, nenhuma rede.
 * Os papéis vêm sempre do diretório (`papeis.exemplo.yaml`: `dev.alfa1` é USUARIO, `gerente.exemplo` é GERENTE).
 */
class UsernamePorProvedorOidcTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    private val relogio = RelogioDeTeste(Instant.parse("2026-10-28T15:00:00Z"))
    private val emissor = EmissorOidcFalso()
    private val agoraSeg get() = relogio.instant().epochSecond

    private fun configuracao(claim: String, estrategia: EstrategiaDeUsername) = OidcSettings(
        issuer = emissor.issuer,
        clientId = emissor.clientId,
        clientSecret = null,
        redirectUri = "https://painel.exemplo.invalid/auth/callback",
        claimUsername = claim,
        estrategiaUsername = estrategia,
        dominiosPermitidos = setOf("empresa.invalid", "empresa"),
        sessao = SegredosDaSessao.de(Base64.getEncoder().encodeToString(ByteArray(48) { (it * 5 + 1).toByte() }), null),
    )

    private class Cenario(val ctx: br.sidia.painel.AppContext, val cliente: HttpClient)

    private fun comApp(claim: String, estrategia: EstrategiaDeUsername, bloco: suspend ApplicationTestBuilder.(Cenario) -> Unit) {
        val cfg = configuracao(claim, estrategia)
        val settings = Settings(dashboardDevMode = false, oidc = cfg)
        val ctx = testContextComBanco(settings, banco, clock = relogio)
        val autenticador = OidcAuthenticator(cfg, ctx.roles, relogio, emissor.cliente(), null)
        testApplication {
            application {
                module(settings, ctx, autenticador)
                routing { get("/api/eu") { call.respondText(call.principal.userId + ":" + call.principal.roles.map { it.name }.sorted().joinToString(",")) } }
            }
            bloco(Cenario(ctx, createClient { followRedirects = false }))
        }
    }

    /** Login completo; [valoresDaClaim] vira o `claim` do token (a claim padrão `preferred_username` é a do próprio token). */
    private suspend fun entrar(c: Cenario, claim: String, valor: Any?): HttpResponse {
        val r = c.cliente.get("/auth/login")
        val loc = Url(r.headers[HttpHeaders.Location]!!)
        val cookie = r.setCookie().first { it.name == COOKIE_DE_LOGIN }.value
        emissor.desafioEsperado = loc.parameters["code_challenge"]
        val base = emissor.claimsValidas(loc.parameters["nonce"]!!, "sem-uso", agoraSeg) - "preferred_username"
        emissor.idTokenDaTroca = emissor.assinar(base + (claim to valor))
        return c.cliente.get("/auth/callback?code=codigo-1&state=${loc.parameters["state"]}") {
            header(HttpHeaders.Cookie, "$COOKIE_DE_LOGIN=$cookie")
        }
    }

    private fun sessaoDe(r: HttpResponse): String? = r.setCookie().firstOrNull { it.name == COOKIE_DE_SESSAO }?.value

    private suspend fun comoEu(c: Cenario, sessao: String): String =
        c.cliente.get("/api/eu") { header(HttpHeaders.Cookie, "$COOKIE_DE_SESSAO=$sessao") }.bodyAsText()

    private fun ultimoMotivo(c: Cenario): String =
        requireNotNull(c.ctx.dataSource).connection.use { con ->
            con.createStatement().executeQuery("SELECT filters FROM access_log ORDER BY id DESC LIMIT 1").use { rs ->
                check(rs.next()); rs.getString(1)
            }
        }

    @Test
    fun keycloakPreferredUsername() = comApp("preferred_username", EstrategiaDeUsername.CLAIM_DIRETO) { c ->
        val r = entrar(c, "preferred_username", "Dev.Alfa1")
        assertEquals(HttpStatusCode.Found, r.status)
        assertEquals("dev.alfa1:USUARIO", comoEu(c, assertNotNull(sessaoDe(r))))
    }

    @Test
    fun entraUpnParteLocal() = comApp("preferred_username", EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL) { c ->
        val r = entrar(c, "preferred_username", "Gerente.Exemplo@Empresa.INVALID")
        assertEquals(HttpStatusCode.Found, r.status)
        assertEquals("gerente.exemplo:GERENTE", comoEu(c, assertNotNull(sessaoDe(r))))
    }

    @Test
    fun adfsContaWindows() = comApp("winaccountname", EstrategiaDeUsername.CONTA_WINDOWS) { c ->
        val r = entrar(c, "winaccountname", "EMPRESA\\Dev.Alfa1")
        assertEquals(HttpStatusCode.Found, r.status)
        assertEquals("dev.alfa1:USUARIO", comoEu(c, assertNotNull(sessaoDe(r))))
    }

    @Test
    fun estrategiaNaoCasaComOutraPessoa() = comApp("preferred_username", EstrategiaDeUsername.CLAIM_DIRETO) { c ->
        // O UPN de outro domínio, com claim_direto, NUNCA vira "dev.alfa1": a sessão não abre.
        val r = entrar(c, "preferred_username", "dev.alfa1@outro.invalid")
        assertNull(sessaoDe(r))
        assertContains(listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden), r.status)
    }

    @Test
    fun convidadoDeOutroDominioNaoRecebeOsPapeisDoGerente() = comApp("preferred_username", EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL) { c ->
        val r = entrar(c, "preferred_username", "gerente.exemplo@gmail.invalid")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
        assertContains(ultimoMotivo(c), "USERNAME_ILEGIVEL")
        assertFalse("gmail" in r.bodyAsText())
    }

    @Test
    fun contaDeOutroDominioWindowsNaoRecebeOsPapeisDoGerente() = comApp("winaccountname", EstrategiaDeUsername.CONTA_WINDOWS) { c ->
        val r = entrar(c, "winaccountname", "OUTRODOMINIO\\gerente.exemplo")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
        assertContains(ultimoMotivo(c), "USERNAME_ILEGIVEL")
    }

    @Test
    fun idReservadoNaoViraPapel() = comApp("preferred_username", EstrategiaDeUsername.CLAIM_DIRETO) { c ->
        val r = entrar(c, "preferred_username", "Admin")
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertNull(sessaoDe(r))
        assertContains(ultimoMotivo(c), "SEM_PAPEL_VIGENTE")
    }

    @Test
    fun doisArrobasIlegivel401() = comApp("preferred_username", EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL) { c ->
        val r = entrar(c, "preferred_username", "dev.alfa1@a@empresa.invalid")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
        assertContains(ultimoMotivo(c), "USERNAME_ILEGIVEL")
    }

    @Test
    fun duasBarrasIlegivel401() = comApp("winaccountname", EstrategiaDeUsername.CONTA_WINDOWS) { c ->
        val r = entrar(c, "winaccountname", "EMPRESA\\SUB\\dev.alfa1")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
        assertContains(ultimoMotivo(c), "USERNAME_ILEGIVEL")
    }

    @Test
    fun claimAusente401() = comApp("winaccountname", EstrategiaDeUsername.CONTA_WINDOWS) { c ->
        // O token traz só preferred_username; a claim configurada (winaccountname) não existe.
        val r = entrar(c, "preferred_username", "dev.alfa1")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertNull(sessaoDe(r))
    }

    @Test
    fun claimDePapelIgnoradaComAsTresEstrategias() {
        val casos = listOf(
            Triple("preferred_username", EstrategiaDeUsername.CLAIM_DIRETO, "dev.alfa1"),
            Triple("preferred_username", EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL, "dev.alfa1@empresa.invalid"),
            Triple("winaccountname", EstrategiaDeUsername.CONTA_WINDOWS, "EMPRESA\\dev.alfa1"),
        )
        for ((claim, estrategia, valor) in casos) {
            comApp(claim, estrategia) { c ->
                val i = c.cliente.get("/auth/login")
                val loc = Url(i.headers[HttpHeaders.Location]!!)
                val cookie = i.setCookie().first { it.name == COOKIE_DE_LOGIN }.value
                emissor.desafioEsperado = loc.parameters["code_challenge"]
                val base = emissor.claimsValidas(loc.parameters["nonce"]!!, "x", agoraSeg) - "preferred_username"
                emissor.idTokenDaTroca = emissor.assinar(
                    base + (claim to valor) + mapOf("roles" to listOf("GERENTE"), "groups" to listOf("GERENTE"), "role" to "GERENTE"),
                )
                val r = c.cliente.get("/auth/callback?code=codigo-1&state=${loc.parameters["state"]}") {
                    header(HttpHeaders.Cookie, "$COOKIE_DE_LOGIN=$cookie")
                }
                assertEquals("dev.alfa1:USUARIO", comoEu(c, assertNotNull(sessaoDe(r))), estrategia.name)
            }
        }
    }
}
