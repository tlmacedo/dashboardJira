package br.sidia.painel.http

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.contarAccessLog
import br.sidia.painel.comCsrf
import br.sidia.painel.module
import br.sidia.painel.testContext
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.delete as rotaDelete
import io.ktor.server.routing.get as rotaGet
import io.ktor.server.routing.patch as rotaPatch
import io.ktor.server.routing.post
import io.ktor.server.routing.put as rotaPut
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtecaoCsrfTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @org.junit.jupiter.api.AfterAll
        fun apagar() = banco.apagar()

        /** Rotas mutantes reais do módulo hoje. O Plano 7 acrescenta POST /api/distribuicao/decisao; o 8, POST /auth/logout. */
        val MUTANTES_ESPERADAS = emptySet<Pair<HttpMethod, String>>()
    }

    private val contador = AtomicInteger(0)

    @AfterTest
    fun zerar() = contador.set(0)

    private fun Application.rotasDeTeste() {
        routing {
            post("/api/teste-csrf") {
                contador.incrementAndGet()
                call.respondText("feito")
            }
            rotaPut("/api/teste-csrf") {
                contador.incrementAndGet()
                call.respondText("feito")
            }
            rotaPatch("/api/teste-csrf") {
                contador.incrementAndGet()
                call.respondText("feito")
            }
            rotaDelete("/api/teste-csrf") {
                contador.incrementAndGet()
                call.respondText("feito")
            }
            rotaGet("/api/teste-csrf") { call.respondText("lido") }
        }
    }

    private fun comApp(env: Map<String, String> = emptyMap(), bloco: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            val s = Settings.fromEnv(env)
            application {
                module(s, testContext(s))
                rotasDeTeste()
            }
            bloco()
        }
    }

    private suspend fun assertNegado(r: HttpResponse, codigo: String) {
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(r.bodyAsText().contains("\"codigo\":\"$codigo\""), "corpo sem o código $codigo")
    }

    @Test
    fun postSemCabecalhoEh403SemExecutar() = comApp {
        val r = client.post("/api/teste-csrf")
        assertNegado(r, "CSRF_AUSENTE")
        assertEquals(0, contador.get())
    }

    @Test
    fun postComCabecalhoPassa() = comApp {
        val r = client.post("/api/teste-csrf") { comCsrf() }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(1, contador.get())
    }

    @Test
    fun cabecalhoComValorErradoEhAusente() = comApp {
        val r = client.post("/api/teste-csrf") { header(CABECALHO_CSRF, "0") }
        assertNegado(r, "CSRF_AUSENTE")
        assertEquals(0, contador.get())
    }

    @Test
    fun origemDeOutroSiteEh403() = comApp {
        val r = client.post("/api/teste-csrf") { comCsrf(origem = "https://evil.example") }
        assertNegado(r, "ORIGEM_INVALIDA")
        assertEquals(0, contador.get())
    }

    @Test
    fun originNullEh403() = comApp {
        val r = client.post("/api/teste-csrf") { comCsrf(origem = "null") }
        assertNegado(r, "ORIGEM_INVALIDA")
        assertEquals(0, contador.get())
    }

    @Test
    fun secFetchSiteCrossSiteEh403() = comApp {
        val r = client.post("/api/teste-csrf") {
            comCsrf()
            header("Sec-Fetch-Site", "cross-site")
        }
        assertNegado(r, "ORIGEM_INVALIDA")
        assertEquals(0, contador.get())
    }

    @Test
    fun secFetchSiteSameSiteEh403() = comApp {
        val r = client.post("/api/teste-csrf") {
            comCsrf()
            header("Sec-Fetch-Site", "same-site")
        }
        assertNegado(r, "ORIGEM_INVALIDA")
    }

    @Test
    fun secFetchSiteSameOriginENoneSegue() = comApp {
        for (valor in listOf("same-origin", "none")) {
            val r = client.post("/api/teste-csrf") {
                comCsrf()
                header("Sec-Fetch-Site", valor)
            }
            assertEquals(HttpStatusCode.OK, r.status, valor)
        }
    }

    @Test
    fun semOriginESemSecFetchPassa() = comApp {
        val r = client.post("/api/teste-csrf") { header(CABECALHO_CSRF, "1") }
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun origemPublicaConfiguradaVale() = comApp(mapOf("DASHBOARD_ORIGEM_PUBLICA" to "https://painel.exemplo.invalid")) {
        val certa = client.post("/api/teste-csrf") {
            comCsrf(origem = "https://painel.exemplo.invalid")
            header(HttpHeaders.Host, "interno:8080")
        }
        assertEquals(HttpStatusCode.OK, certa.status)
        val daPropriaHost = client.post("/api/teste-csrf") { comCsrf(origem = "http://localhost") }
        assertNegado(daPropriaHost, "ORIGEM_INVALIDA")
    }

    @Test
    fun getNaoExigeCabecalho() = comApp {
        assertEquals(HttpStatusCode.OK, client.get("/api/teste-csrf").status)
    }

    @Test
    fun putPatchEDeleteTambemExigem() = comApp {
        assertNegado(client.put("/api/teste-csrf"), "CSRF_AUSENTE")
        assertNegado(client.patch("/api/teste-csrf"), "CSRF_AUSENTE")
        assertNegado(client.delete("/api/teste-csrf"), "CSRF_AUSENTE")
        assertEquals(0, contador.get())
        assertEquals(HttpStatusCode.OK, client.put("/api/teste-csrf") { comCsrf() }.status)
        assertEquals(HttpStatusCode.OK, client.patch("/api/teste-csrf") { comCsrf() }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/api/teste-csrf") { comCsrf() }.status)
        assertEquals(3, contador.get())
    }

    @Test
    fun negacaoGravaAccessLog() {
        testApplication {
            val s = Settings.fromEnv(emptyMap())
            val ctx = testContextComBanco(s, banco)
            application {
                module(s, ctx)
                rotasDeTeste()
            }
            client.post("/api/teste-csrf") { header(HttpHeaders.ContentType, "application/json") }
            assertEquals(1, ctx.contarAccessLog())
            ctx.dataSource!!.connection.use { c ->
                c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("dev", rs.getString("user_id"))
                    assertEquals("/api/teste-csrf", rs.getString("route"))
                    val filtros = rs.getString("filters")
                    assertTrue(filtros.contains("motivo") && filtros.contains("CSRF_AUSENTE"), filtros)
                }
            }
            ctx.close()
        }
    }

    @Test
    fun negacaoNaoGravaChaveDeIssueNaRota() {
        testApplication {
            val s = Settings.fromEnv(emptyMap())
            val ctx = testContextComBanco(s, banco)
            application { module(s, ctx) }
            // POST sem cabeçalho CSRF no histórico da auditoria: o caminho leva a chave de issue.
            client.post("/api/auditoria/historico/TG-1") { header(HttpHeaders.ContentType, "application/json") }
            ctx.dataSource!!.connection.use { c ->
                c.createStatement().executeQuery("SELECT route FROM access_log").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("/api/auditoria/historico/{key}", rs.getString("route"))
                    assertTrue(!rs.next())
                }
            }
            ctx.close()
        }
    }

    @Test
    fun negacaoSemPrincipalGravaAnonymous() {
        testApplication {
            val s = Settings.fromEnv(emptyMap())
            val ctx = testContextComBanco(s, banco)
            application {
                module(s, ctx)
                rotasDeTeste()
            }
            // /api/meta é pública: o controle de acesso não resolve principal nela.
            client.post("/api/meta")
            ctx.dataSource!!.connection.use { c ->
                c.createStatement().executeQuery("SELECT user_id, filters FROM access_log").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("anonymous", rs.getString("user_id"))
                }
            }
            ctx.close()
        }
    }

    @Test
    fun falhaDaAuditoriaNaoImpedeOs403() {
        testApplication {
            val s = Settings.fromEnv(emptyMap())
            val ctx = testContextComBanco(s, banco)
            application {
                module(s, ctx)
                rotasDeTeste()
            }
            // Pool fechado: a gravação da auditoria falha, mas a negação segue.
            (ctx.dataSource as AutoCloseable).close()
            val r = client.post("/api/teste-csrf")
            assertNegado(r, "CSRF_AUSENTE")
            assertEquals(0, contador.get())
        }
    }

    // ---- varredura da árvore de rotas ----

    private fun metodoMutante(m: HttpMethod) = m != HttpMethod.Get && m != HttpMethod.Head && m != HttpMethod.Options

    /** Percorre a árvore de rotas e devolve (método, caminho com marcadores) das folhas com método mutante. */
    private fun mutantesDe(app: Application): Set<Pair<HttpMethod, String>> {
        val achadas = mutableSetOf<Pair<HttpMethod, String>>()
        fun visitar(rota: RoutingNode) {
            val sel = rota.selector
            if (sel is io.ktor.server.routing.HttpMethodRouteSelector && metodoMutante(sel.method)) {
                // O seletor de método é filho do nó do caminho; o toString() do pai é o caminho acumulado.
                achadas += sel.method to (rota.parent?.toString() ?: "/")
            }
            rota.children.forEach(::visitar)
        }
        visitar(app.plugin(RoutingRoot))
        return achadas
    }

    /** Troca marcadores de parâmetro (`{id}`, `{...}`) por um segmento qualquer. */
    private fun urlConcreta(caminho: String) = caminho.replace(Regex("\\{[^}]*\\}"), "x")

    @Test
    fun todoMetodoMutanteExigeCsrf() {
        var encontradas: Set<Pair<HttpMethod, String>> = emptySet()
        testApplication {
            val s = Settings.fromEnv(emptyMap())
            application {
                module(s, testContext(s))
                rotasDeTeste()
                encontradas = mutantesDe(this)
            }
            // Força a subida do app e a coleta.
            client.get("/api/health")

            // Sem a rota de teste, o percurso precisa achar só as reais (hoje nenhuma): a lista é versionada.
            val reais = encontradas.filterNot { it.second == "/api/teste-csrf" }.toSet()
            assertEquals(MUTANTES_ESPERADAS, reais, "Rota mutante nova: atualize MUTANTES_ESPERADAS e use comCsrf() nos testes dela")

            // O percurso não pode ser vazio: a rota de teste tem de ser achada, nos quatro métodos mutantes.
            val deTeste = encontradas.filter { it.second == "/api/teste-csrf" }.map { it.first }.toSet()
            assertEquals(setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete), deTeste)

            for ((metodo, caminho) in encontradas) {
                val r = client.request(urlConcreta(caminho)) { method = metodo }
                assertNegado(r, "CSRF_AUSENTE")
            }
            assertEquals(0, contador.get())
        }
    }
}
