package br.sidia.painel.demo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.AppContext
import br.sidia.painel.TestPaths
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.module
import br.sidia.painel.sync.DirecaoVinculo
import br.sidia.painel.sync.ParDeChaves
import br.sidia.painel.sync.ParesTaskReview
import br.sidia.painel.sync.SqliteIssueLinkStore
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.VinculoRecord
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.io.path.extension
import kotlin.io.path.name

/** Dados de demonstração contra SQLite real em arquivo temporário. Nenhuma rede, nenhum Jira. */
class DadosDeDemonstracaoTest {
    companion object {
        private val pg = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
        }
    }

    private val zona = ZoneId.of("America/Manaus")
    private val manifesto = lerManifestoDemo("demo")
    private val ancora: LocalDate = manifesto.ancora

    private fun relogioEm(dia: LocalDate, hora: Int = 12): Clock =
        Clock.fixed(dia.atTime(hora, 0).atZone(zona).toInstant(), zona)

    private val settingsDemo = Settings(dashboardDevMode = true, dashboardDemoData = true)

    private fun contexto(clock: Clock, settings: Settings = settingsDemo, jira: JiraClientFactory = JiraClientFactory(settings)) =
        testContextComBanco(settings, pg, clock = clock, jira = jira)

    private fun carregador(ctx: AppContext) =
        DadosDeDemonstracao(requireNotNull(ctx.dataSource), SqliteIssueStore(ctx.dataSource!!), ctx.clock, ctx.settings.zone)

    private fun contar(ctx: AppContext, sql: String, vararg params: Any): Int =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.prepareStatement(sql).use { ps ->
                params.forEachIndexed { n, p -> if (p is Timestamp) ps.setTimestamp(n + 1, p) else ps.setString(n + 1, p.toString()) }
                ps.executeQuery().use { it.next(); it.getInt(1) }
            }
        }

    private fun issuesDaFixture(instancia: String): List<JsonObject> =
        lerIssuesDemo("demo", manifesto.instancias.getValue(instancia).arquivo)

    private fun eventosDaFixture(i: JsonObject): Int =
        ((i["changelog"] as? JsonObject)?.get("histories") as? JsonArray).orEmpty()
            .sumOf { (it.jsonObject["items"] as JsonArray).size }

    @Test
    fun carregaAsDuasInstancias() {
        contexto(relogioEm(ancora.plusDays(1))).use { ctx ->
            val resumo = carregador(ctx).carregar()
            assertEquals(setOf("samsung", "sidia"), resumo.map { it.instancia }.toSet())
            for (r in resumo) {
                val fixture = issuesDaFixture(r.instancia)
                assertEquals(fixture.size, r.issues)
                assertEquals(fixture.sumOf(::eventosDaFixture), r.eventos)
                assertEquals(fixture.size, contar(ctx, "SELECT count(*) FROM issue WHERE instance = ?", r.instancia))
                assertEquals(
                    r.eventos,
                    contar(ctx, "SELECT count(*) FROM change_event e JOIN issue i ON i.id = e.issue_id WHERE i.instance = ?", r.instancia),
                )
            }
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue WHERE key LIKE 'DEMOCAT-%' AND instance <> 'sidia'"))
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue WHERE key LIKE 'DEMO-%' AND instance <> 'samsung'"))
            assertEquals(5, contar(ctx, "SELECT count(*) FROM issue WHERE key LIKE 'DEMOCAT-%' AND instance = 'sidia'"))
            // O jira_id é gravado pelo caminho real (a Tarefa 4).
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue WHERE jira_id IS NULL"))
            assertTrue(carregador(ctx).existemDados())
        }
    }

    @Test
    fun recarregarEhIdempotente() {
        contexto(relogioEm(ancora.plusDays(1))).use { ctx ->
            val d = carregador(ctx)
            val primeiro = d.carregar()
            val issues = contar(ctx, "SELECT count(*) FROM issue")
            val eventos = contar(ctx, "SELECT count(*) FROM change_event")
            val vinculos = contar(ctx, "SELECT count(*) FROM issue_link")
            assertTrue(vinculos > 0)
            val segundo = d.carregar()
            assertEquals(primeiro, segundo)
            assertEquals(issues, contar(ctx, "SELECT count(*) FROM issue"))
            assertEquals(eventos, contar(ctx, "SELECT count(*) FROM change_event"))
            assertEquals(vinculos, contar(ctx, "SELECT count(*) FROM issue_link"))
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue_link WHERE removida_em IS NOT NULL"))
        }
    }

    @Test
    fun carregaVinculosDaDemo() {
        contexto(relogioEm(ancora.plusDays(1))).use { ctx ->
            carregador(ctx).carregar()
            val ds = requireNotNull(ctx.dataSource)
            val ativos = SqliteIssueLinkStore(ds).ativos("samsung", "Blocks")
            // Dois pares, um em cada direção (visto pelas duas pontas, uma linha por link).
            assertEquals(
                setOf(
                    VinculoRecord("DEMO-7", "DEMO-35", "Blocks", DirecaoVinculo.OUTWARD),
                    VinculoRecord("DEMO-36", "DEMO-12", "Blocks", DirecaoVinculo.OUTWARD),
                ),
                ativos.toSet(),
            )
            assertEquals(2, ativos.size)
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue_link WHERE instance <> 'samsung'"))
            val tipoDe = ds.connection.use { c ->
                c.createStatement().executeQuery("SELECT key, issue_type FROM issue WHERE instance = 'samsung' AND removida_em IS NULL").use { rs ->
                    buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2).orEmpty()) }
                }
            }
            val r = ParesTaskReview.resolver(ativos, tipoDe, ctx.vinculos)
            assertEquals(setOf(ParDeChaves("DEMO-35", "DEMO-7"), ParDeChaves("DEMO-36", "DEMO-12")), r.pares.toSet())
            assertEquals(2, r.pares.size)
            assertEquals(0, r.ambiguas)
            assertEquals(0, r.ignorados)
        }
    }

    @Test
    fun datasDeslocadasEmSemanasInteiras() {
        assertEquals(7, DadosDeDemonstracao.deslocamentoDias(ancora, ancora.plusDays(10)))
        assertEquals(14, DadosDeDemonstracao.deslocamentoDias(ancora, ancora.plusDays(14)))
        assertEquals(-7, DadosDeDemonstracao.deslocamentoDias(ancora, ancora.minusDays(5)))
        assertEquals(0, DadosDeDemonstracao.deslocamentoDias(ancora, ancora))
        assertEquals(0, DadosDeDemonstracao.deslocamentoDias(ancora, ancora.plusDays(6)))
        val original = issuesDaFixture("samsung").first { it["key"]!!.jsonPrimitive.content == "DEMO-1" }
        val criadoOriginal = Instant.parse(
            original["fields"]!!.jsonObject["created"]!!.jsonPrimitive.content
                .let { br.sidia.painel.sync.ChangelogParser.parseInstant(it).toString() },
        )
        for ((hoje, esperado) in listOf(ancora.plusDays(10) to 7L, ancora.minusDays(5) to -7L, ancora to 0L)) {
            contexto(relogioEm(hoje)).use { ctx ->
                carregador(ctx).carregar()
                val criado = requireNotNull(ctx.dataSource).connection.use { c ->
                    c.createStatement().executeQuery("SELECT created FROM issue WHERE key = 'DEMO-1' AND instance = 'samsung'").use {
                        it.next(); it.getTimestamp(1).toInstant()
                    }
                }
                val deslocado = criadoOriginal.atZone(zona).plusDays(esperado).toInstant()
                assertEquals(deslocado, criado)
                assertEquals(criadoOriginal.atZone(zona).dayOfWeek, criado.atZone(zona).dayOfWeek)
            }
        }
    }

    @Test
    fun nenhumInstanteNoFuturo() {
        val relogios = listOf(
            relogioEm(ancora.minusDays(5)), relogioEm(ancora, 0), relogioEm(ancora.plusDays(3)), relogioEm(ancora.plusDays(40), 0),
        )
        for (relogio in relogios) {
            contexto(relogio).use { ctx ->
                carregador(ctx).carregar()
                val agora = Timestamp.from(relogio.instant())
                assertEquals(
                    0,
                    contar(ctx, "SELECT count(*) FROM issue WHERE created > ? OR updated > ? OR resolved > ?", agora, agora, agora),
                    "instante no futuro com o relógio em ${relogio.instant()}",
                )
                assertEquals(0, contar(ctx, "SELECT count(*) FROM change_event WHERE at > ?", agora))
            }
        }
    }

    @Test
    fun demoNaoFazRequisicao() {
        val requisicoes = AtomicInteger(0)
        val fabricasCriadas = AtomicInteger(0)
        val settings = Settings(
            dashboardDevMode = true,
            dashboardDemoData = true,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, {
            fabricasCriadas.incrementAndGet()
            HttpClient(MockEngine { requisicoes.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        contexto(relogioEm(ancora.plusDays(1)), settings, fabrica).use { ctx ->
            prepararDemonstracao(ctx)
            // Rodar de novo (apaga e regrava) também não toca a rede.
            carregador(ctx).carregar()
            assertTrue(contar(ctx, "SELECT count(*) FROM issue") > 0)
            assertEquals(0, requisicoes.get())
            assertEquals(0, fabricasCriadas.get())
        }
        // O código de demo/ não conhece o cliente do Jira, o cliente HTTP nem qualquer API de rede.
        val pasta = TestPaths.mainSrcDir.resolve("kotlin/br/sidia/painel/demo")
        val fontes = Files.walk(pasta).use { s -> s.filter { it.extension == "kt" }.toList() }
        assertTrue(fontes.isNotEmpty())
        val proibidos = listOf(
            "JiraClient", "JiraClientFactory", "HttpClient", "io.ktor.client", "java.net", "javax.net", "HttpURLConnection",
            "Socket", "URL(", "URI(", "ctx.jira", ".jira.", "LiveGuard", "OrcamentoDeRequisicoes",
        )
        for (f in fontes) {
            val texto = Files.readString(f)
            for (proibido in proibidos) {
                assertFalse(proibido in texto, "${f.name} cita $proibido")
            }
        }
    }

    private fun issueReal(chave: String): JsonObject = buildJsonObject {
        put("id", "777001")
        put("key", chave)
        putJsonObject("fields") {
            put("summary", "Issue real simulada")
            putJsonObject("project") { put("key", "TG") }
            putJsonObject("issuetype") { put("name", "Checklist") }
            putJsonObject("status") { put("name", "To Do") }
            put("created", "2026-09-01T09:00:00.000-0400")
        }
    }

    @Test
    fun demoRecusaBancoComDadosReais() {
        contexto(relogioEm(ancora.plusDays(1))).use { ctx ->
            val store = SqliteIssueStore(requireNotNull(ctx.dataSource))
            store.upsert(issueReal("TG-1"), emptyList(), "samsung")
            // Uma linha de demonstração antiga também NÃO pode ser apagada quando a recusa acontece.
            store.upsert(issueReal("DEMO-99").let { JsonObject(it + ("id" to kotlinx.serialization.json.JsonPrimitive("777002"))) }, emptyList(), "samsung")
            val e = assertFailsWith<DemoRecusadaException> { carregador(ctx).carregar() }
            assertEquals("BANCO_COM_DADOS_REAIS", e.codigo)
            assertEquals(2, contar(ctx, "SELECT count(*) FROM issue"))
            assertEquals(1, contar(ctx, "SELECT count(*) FROM issue WHERE key = 'DEMO-99'"))
            // A subida também cai (não só o carregador).
            assertFailsWith<DemoRecusadaException> { prepararDemonstracao(ctx) }
            assertEquals(2, contar(ctx, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun demoRecusaIssueRealEmOutraInstancia() {
        contexto(relogioEm(ancora.plusDays(1))).use { ctx ->
            val store = SqliteIssueStore(requireNotNull(ctx.dataSource))
            // Instância fora do manifesto, com chave real: a carga também recusa.
            store.upsert(issueReal("TG-5"), emptyList(), "outra")
            val e = assertFailsWith<DemoRecusadaException> { carregador(ctx).carregar() }
            assertEquals("BANCO_COM_DADOS_REAIS", e.codigo)
            assertEquals(1, contar(ctx, "SELECT count(*) FROM issue"))
            // Chave de demonstração na instância errada (DEMO- em sidia) também não é demonstração.
            store.upsert(issueReal("DEMO-7").let { JsonObject(it + ("id" to kotlinx.serialization.json.JsonPrimitive("777003"))) }, emptyList(), "sidia")
            assertFailsWith<DemoRecusadaException> { carregador(ctx).carregar() }
            assertEquals(2, contar(ctx, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun producaoComDadosDeDemoResidualNaoSobe() {
        contexto(relogioEm(ancora.plusDays(1))).use { demo ->
            carregador(demo).carregar()
            val total = contar(demo, "SELECT count(*) FROM issue")
            fun comSettings(s: Settings) = AppContext(
                s, demo.calendar, demo.roles, demo.dataSource, demo.jira, demo.statusMap, demo.campos, demo.fichas, demo.vinculos, demo.labelsDeAtraso, demo.regrasAuditoria, demo.clock,
            )
            val e = assertFailsWith<DemoRecusadaException> {
                prepararDemonstracao(comSettings(Settings(dashboardDevMode = false, dashboardDemoData = false)))
            }
            assertEquals("DEMO_RESIDUAL", e.codigo)
            assertEquals(total, contar(demo, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun producaoSemDadosDeDemoSobeNormalmente() {
        contexto(relogioEm(ancora.plusDays(1)), Settings()).use { ctx ->
            fun comSettings(s: Settings) = AppContext(
                s, ctx.calendar, ctx.roles, ctx.dataSource, ctx.jira, ctx.statusMap, ctx.campos, ctx.fichas, ctx.vinculos, ctx.labelsDeAtraso, ctx.regrasAuditoria, ctx.clock,
            )
            prepararDemonstracao(comSettings(Settings(dashboardDevMode = false, dashboardDemoData = false)))
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun flagLigadaComDadosDeDemoDerrubaSubida() {
        contexto(relogioEm(ancora.plusDays(1))).use { demo ->
            carregador(demo).carregar()
            val total = contar(demo, "SELECT count(*) FROM issue")
            fun comSettings(s: Settings) = AppContext(
                s, demo.calendar, demo.roles, demo.dataSource, demo.jira, demo.statusMap, demo.campos, demo.fichas, demo.vinculos, demo.labelsDeAtraso, demo.regrasAuditoria, demo.clock,
            )
            // Flag ligada e demonstração desligada, mas o banco tem dados fictícios: não sobe, e nada é apagado.
            val e = assertFailsWith<DemoRecusadaException> {
                prepararDemonstracao(comSettings(Settings(jiraLiveEnabled = true, dashboardDemoData = false)))
            }
            assertEquals("BANCO_COM_DADOS_DE_DEMONSTRACAO", e.codigo)
            assertEquals(total, contar(demo, "SELECT count(*) FROM issue"))
            // Defesa em profundidade: mesmo com um Settings montado à mão, a carga recusa a combinação insegura.
            assertFailsWith<DemoRecusadaException> {
                prepararDemonstracao(comSettings(Settings(jiraLiveEnabled = true, dashboardDemoData = true)))
            }
            assertFailsWith<DemoRecusadaException> {
                prepararDemonstracao(comSettings(Settings(dashboardDevMode = false, dashboardDemoData = true)))
            }
            // Tudo desligado: não faz nada e não apaga nada.
            prepararDemonstracao(comSettings(Settings()))
            assertEquals(total, contar(demo, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun semDemoELimpoNaoCarregaNada() {
        contexto(relogioEm(ancora.plusDays(1)), Settings()).use { ctx ->
            prepararDemonstracao(ctx)
            assertEquals(0, contar(ctx, "SELECT count(*) FROM issue"))
            assertFalse(carregador(ctx).existemDados())
        }
    }

    @Test
    fun tempoEmStatusMostraNumerosComDemo() {
        // Sexta-feira da âncora, meio-dia em Manaus: deslocamento 0.
        contexto(relogioEm(ancora)).use { ctx ->
            prepararDemonstracao(ctx)
            testApplication {
                application { module(ctx.settings, ctx) }
                val card = Json.parseToJsonElement(client.get("/api/cards/tempo-em-status").bodyAsText()).jsonObject
                assertTrue(card["analisadas"]!!.jsonPrimitive.content.toInt() > 0, card.toString())
                assertTrue(card["paradas"]!!.jsonPrimitive.content.toInt() > 0, card.toString())
                val bloqueantes = card["avisos"]!!.jsonArray.filter { it.jsonObject["gravidade"]!!.jsonPrimitive.content == "bloqueante" }
                assertEquals(emptyList(), bloqueantes)
                val analisadas = Json.parseToJsonElement(client.get("/api/cartoes/TS-01/tasks?segmento=analisadas").bodyAsText())
                    .jsonObject["linhas"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content }
                assertTrue("DEMO-1" in analisadas, analisadas.toString())
                // O Epic e a Cancelled ficam fora; a Closed sem resolução e as chaves da outra instância também.
                val epico = issuesDaFixture("samsung").first { it["fields"]!!.jsonObject["issuetype"]!!.jsonObject["name"]!!.jsonPrimitive.content == "Epic" }
                val cancelada = issuesDaFixture("samsung").first { it["fields"]!!.jsonObject["status"]!!.jsonObject["name"]!!.jsonPrimitive.content == "Cancelled" }
                assertFalse(epico["key"]!!.jsonPrimitive.content in analisadas)
                assertFalse(cancelada["key"]!!.jsonPrimitive.content in analisadas)
                assertTrue(analisadas.none { it.startsWith("DEMOCAT-") })
            }
        }
    }

    @Test
    fun existemDadosEhFalsoComBancoVazio() {
        contexto(relogioEm(ancora)).use { ctx ->
            assertFalse(carregador(ctx).existemDados())
        }
    }
}
