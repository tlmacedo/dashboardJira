package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.camposDeTeste
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.demo.DadosDeDemonstracao
import br.sidia.painel.demo.lerManifestoDemo
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLDecoder
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Link "Abrir no Jira" (I-18): montado só no backend, só com a flag ligada e a instância configurada, com no máximo
 * 100 chaves e sem credencial. Nenhum cliente Jira é criado e nenhuma requisição sai: é só texto de URL.
 */
class LinkJiraTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            banco.apagar()
        }

        // Host reservado para exemplos (nunca resolve): nada de endereço real da empresa.
        private const val BASE = "https://jira.exemplo.invalid"
    }

    private val campos = camposDeTeste()

    private fun config(url: String = BASE) = JiraInstanceSettings(url, JiraAuthKind.BEARER, null, "token-ficticio-abc123")

    private fun ligado(url: String = BASE) = Settings(jiraLiveEnabled = true, jiraInstances = mapOf("samsung" to config(url)))

    private fun chaves(n: Int) = (1..n).map { "TG-$it" }

    private fun jqlDoLink(link: String): String =
        URLDecoder.decode(link.substringAfter("jql="), Charsets.UTF_8)

    // ---- Montador ----

    @Test
    fun semLinkComFlagDesligada() {
        val desligado = Settings(jiraLiveEnabled = false, jiraInstances = mapOf("samsung" to config()))
        assertNull(LinkJira.paraChaves(desligado, campos, "samsung", chaves(3)))
        // Ligada, o mesmo pedido produz o link: a flag é mesmo o que decide.
        assertNotNull(LinkJira.paraChaves(ligado(), campos, "samsung", chaves(3)))
    }

    @Test
    fun semLinkSemInstanciaConfiguradaOuSemChaves() {
        assertNull(LinkJira.paraChaves(Settings(jiraLiveEnabled = true), campos, "samsung", chaves(3)))
        assertNull(LinkJira.paraChaves(ligado(), campos, "sidia", chaves(3)))
        assertNull(LinkJira.paraChaves(ligado(), campos, "samsung", emptyList()))
        assertNull(LinkJira.paraChaves(ligado(), campos, null, chaves(3)))
    }

    @Test
    fun linkSemCredenciais() {
        val link = LinkJira.paraChaves(ligado(), campos, "samsung", listOf("TG-107", "TG-2"))
        assertNotNull(link)
        assertTrue(link.startsWith("https://jira.exemplo.invalid/issues/?jql="), link)
        assertFalse("@" in link || "token" in link.lowercase() || "abc123" in link, link)
        assertEquals("key in (\"TG-107\", \"TG-2\")", jqlDoLink(link))
        // O caminho do contexto do Jira (instalação em subcaminho) é mantido; barra final não duplica.
        assertTrue(LinkJira.paraChaves(ligado("https://jira.exemplo.invalid/jira/"), campos, "samsung", chaves(1))!!
            .startsWith("https://jira.exemplo.invalid/jira/issues/?jql="))
        // Base com credencial, sem https, com consulta ou fragmento, ou ilegível: nenhum link (nunca vaza a credencial).
        for (ruim in listOf(
            "https://usuario:senha@jira.exemplo.invalid", "https://usuario@jira.exemplo.invalid", "http://jira.exemplo.invalid",
            "https://jira.exemplo.invalid?token=abc123", "https://jira.exemplo.invalid#x", "jira.exemplo.invalid", "ftp://jira.exemplo.invalid", "",
        )) {
            assertNull(LinkJira.paraChaves(ligado(ruim), campos, "samsung", chaves(2)), "base '$ruim'")
        }
    }

    @Test
    fun limiteDe100Chaves() {
        assertNotNull(LinkJira.paraChaves(ligado(), campos, "samsung", chaves(100)))
        // Acima do limite não há link (a tela avisa e oferece o CSV).
        assertNull(LinkJira.paraChaves(ligado(), campos, "samsung", chaves(101)))
        // Chave repetida conta uma vez.
        assertNotNull(LinkJira.paraChaves(ligado(), campos, "samsung", chaves(100) + chaves(50)))
    }

    @Test
    fun chaveSuspeitaNaoGeraLinkNemEscapa() {
        for (suspeita in listOf("TG-1\" OR project = X", "tg-1", "TG-1; DROP", "TG-")) {
            assertNull(LinkJira.paraChaves(ligado(), campos, "samsung", listOf("TG-1", suspeita)), suspeita)
        }
    }

    // ---- Pela rota ----

    private val zona = ZoneId.of("America/Manaus")
    private val manifesto = lerManifestoDemo("demo")
    private val relogio: Clock = Clock.fixed(manifesto.ancora.plusDays(1).atTime(12, 0).atZone(zona).toInstant(), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)

    private val diretorio = RoleDirectory(
        listOf(Pessoa("gerente.exemplo", "gerente.exemplo", listOf(AtribuicaoPapel(Role.GERENTE, Vigencia(null, null), null)))),
    )

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private fun contexto(settings: Settings): AppContext {
        val ctx = testContextComBanco(settings, banco, diretorio, relogio)
        val ds = requireNotNull(ctx.dataSource)
        DadosDeDemonstracao(ds, SqliteIssueStore(ds), ctx.clock, ctx.settings.zone).carregar()
        return ctx
    }

    private fun drill(ctx: AppContext, caminho: String): Pair<HttpStatusCode, String> {
        var resultado: Pair<HttpStatusCode, String>? = null
        testApplication {
            application { module(ctx.settings, ctx, autenticador) }
            val r = client.get(caminho) { header("X-Teste-Usuario", "gerente.exemplo") }
            resultado = r.status to r.bodyAsText()
        }
        return requireNotNull(resultado)
    }

    private fun linhasDoAccessLog(ctx: AppContext): List<Pair<String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2).orEmpty()) }
            }
        }

    @Test
    fun rotaSemLinkComFlagDesligada() {
        val ctx = contexto(Settings(jiraLiveEnabled = false, dashboardDevMode = false, jiraInstances = mapOf("samsung" to config())))
        ctx.use {
            val (status, corpo) = drill(ctx, "/api/cartoes/TS-01/tasks?segmento=analisadas")
            assertEquals(HttpStatusCode.OK, status, corpo)
            val dto = Json.decodeFromString<DrillGenericoDto>(corpo)
            assertTrue(dto.total > 0)
            assertNull(dto.linkJira)
        }
    }

    @Test
    fun rotaComLinkQuandoLigadaEAccessLogSemChaves() {
        val ctx = contexto(Settings(jiraLiveEnabled = true, dashboardDevMode = false, jiraInstances = mapOf("samsung" to config())))
        ctx.use {
            val (status, corpo) = drill(ctx, "/api/cartoes/TS-01/tasks?segmento=analisadas")
            assertEquals(HttpStatusCode.OK, status, corpo)
            val dto = Json.decodeFromString<DrillGenericoDto>(corpo)
            assertTrue(dto.total in 1..100, "a demonstração cabe no limite: ${dto.total}")
            val link = assertNotNull(dto.linkJira)
            assertTrue(link.startsWith("https://jira.exemplo.invalid/issues/?jql="), link)
            // O link tem exatamente as chaves da gaveta.
            val doLink = Regex("\"([A-Z]+-\\d+)\"").findAll(jqlDoLink(link)).map { it.groupValues[1] }.toSet()
            assertEquals(dto.linhas.map { it.key }.toSet(), doLink)
            // access_log: rota e quantidade, nunca as chaves nem o endereço do Jira.
            val linha = linhasDoAccessLog(ctx).single()
            assertEquals("/api/cartoes/TS-01/tasks", linha.first)
            assertTrue("\"total\":\"${dto.total}\"" in linha.second, linha.second)
            for (chave in dto.linhas.map { it.key }) assertFalse(chave in linha.second, "access_log guardou $chave")
            assertFalse("jira.exemplo" in linha.second || "jql" in linha.second.lowercase(), linha.second)
        }
    }

    @Test
    fun rotaSemLinkAcimaDoLimite() {
        // Pelo montador: 101 chaves nunca viram link (a rota usa o mesmo montador).
        assertNull(LinkJira.paraChaves(ligado(), campos, "samsung", chaves(101)))
    }
}
