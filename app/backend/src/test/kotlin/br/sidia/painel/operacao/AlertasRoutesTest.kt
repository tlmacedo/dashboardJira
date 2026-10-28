package br.sidia.painel.operacao

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertasRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private val zona = ZoneId.of("America/Manaus")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-30T16:00:00Z"), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)

    private fun pessoa(id: String, papel: Role, squad: String? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, null), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("ana.silva", Role.USUARIO, "Alfa"),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
        ),
    )

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
        ctx = null
    }

    private fun contexto(live: Boolean = false, jira: JiraClientFactory? = null): AppContext {
        val settings = Settings(
            jiraLiveEnabled = live,
            dashboardDevMode = false,
            jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123")),
        )
        return (if (jira != null) testContextComBanco(settings, banco, diretorio, relogio, jira) else testContextComBanco(settings, banco, diretorio, relogio))
            .also { ctx = it }
    }

    private fun linhasDoAccessLog(c: AppContext): List<Pair<String, String>> =
        requireNotNull(c.dataSource).connection.use { con ->
            con.createStatement().executeQuery("SELECT user_id, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2).orEmpty()) }
            }
        }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private fun rodar(c: AppContext, bloco: suspend (suspend (String?) -> Resp) -> Unit) = testApplication {
        application { module(c.settings, c, autenticador) }
        val http: HttpClient = client
        bloco { usuario ->
            val r = http.get("/api/operacao/alertas") { if (usuario != null) header("X-Teste-Usuario", usuario) }
            Resp(r.status, r.bodyAsText())
        }
    }

    @Test
    fun soGerente() {
        val c = contexto()
        rodar(c) { get ->
            val ok = get("gerente.exemplo")
            assertEquals(HttpStatusCode.OK, ok.status, ok.corpo)
            assertEquals("""{"alertas":[]}""", ok.corpo.replace(" ", ""))
            for (u in listOf("coord.um", "lider.alfa", "ana.silva")) {
                val r = get(u)
                assertEquals(HttpStatusCode.Forbidden, r.status, u)
                assertEquals("", r.corpo, u)
            }
            assertEquals(HttpStatusCode.Unauthorized, get(null).status)
        }
        // Toda chamada autenticada, inclusive as negadas, vai para o access_log com o resultado.
        val log = linhasDoAccessLog(c)
        assertEquals(listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana.silva"), log.map { it.first })
        assertEquals(listOf("200", "403", "403", "403"), log.map { Regex("\"resultado\":\"(\\d+)\"").find(it.second)?.groupValues?.get(1) })
    }

    @Test
    fun semDadosPessoais() {
        val c = contexto()
        rodar(c) { get ->
            // Depois da subida (que só acontece na primeira requisição): ela limpa as suspensões (reiniciar equivale a religar).
            get(null)
            SqliteSyncStateStore(requireNotNull(c.dataSource)).suspender("samsung", "HTTP_401", Instant.parse("2026-10-30T15:00:00Z"))
            val r = get("gerente.exemplo")
            assertEquals(HttpStatusCode.OK, r.status)
            val alertas = Json.parseToJsonElement(r.corpo).jsonObject.getValue("alertas").jsonArray
            val a = alertas.single { it.jsonObject["codigo"].toString() == "\"SYNC_SUSPENSA\"" }.jsonObject
            assertEquals(setOf("codigo", "gravidade", "instancia", "desde"), a.keys)
            assertEquals("\"CRITICO\"", a.getValue("gravidade").toString())
            assertEquals("\"samsung\"", a.getValue("instancia").toString())
            for (proibido in listOf("gerente.exemplo", "token-de-teste", "jira.exemplo", "HTTP_401")) {
                assertFalse(proibido in r.corpo, "o corpo não pode conter '$proibido'")
            }
        }
    }

    @Test
    fun gerenteVeOMotivoDeNaoProntoQueOPublicoNaoVe() {
        val c = contexto()
        val arquivo = br.sidia.painel.db.Database.arquivoDe(requireNotNull(c.dataSource))!!
        val url = br.sidia.painel.db.Database.urlDeArquivo(arquivo) + "?busy_timeout=1500&open_mode=1"
        val prontidao = Prontidao(arquivo, url, { 0 }, { false }, 1, relogio)
        testApplication {
            application { module(c.settings, c, autenticador, prontidao = prontidao) }
            val publico = client.get("/api/health/pronto")
            assertEquals(HttpStatusCode.ServiceUnavailable, publico.status)
            assertEquals("""{"pronto":false}""", publico.bodyAsText())
            val gerente = client.get("/api/operacao/alertas") { header("X-Teste-Usuario", "gerente.exemplo") }
            val corpo = Json.parseToJsonElement(gerente.bodyAsText()).jsonObject
            assertEquals("\"TRAVA_PERDIDA\"", corpo.getValue("naoPronto").toString())
        }
    }

    @Test
    fun semRegistroSemResposta() {
        val c = contexto()
        // O registro de acesso fica indisponível: sem registro não há resposta com dados.
        requireNotNull(c.dataSource).connection.use { it.createStatement().execute("ALTER TABLE access_log RENAME TO access_log_fora") }
        rodar(c) { get ->
            val r = get("gerente.exemplo")
            assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
            assertFalse("alertas" in r.corpo)
        }
    }

    @Test
    fun avaliacaoNaoFazRequisicao() {
        val requisicoes = AtomicInteger(0)
        val clientes = AtomicInteger(0)
        val base = Settings(
            jiraLiveEnabled = true, dashboardDevMode = false,
            jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123")),
        )
        val fabrica = JiraClientFactory(base, {
            clientes.incrementAndGet()
            HttpClient(MockEngine { requisicoes.incrementAndGet(); respond("{}", HttpStatusCode.OK) })
        })
        val c = contexto(live = true, jira = fabrica)
        rodar(c) { get -> assertEquals(HttpStatusCode.OK, get("gerente.exemplo").status) }
        val avaliador = requireNotNull(montarAvaliadorDeAlertas(c, Metricas()))
        avaliador.avaliarEPublicar()
        assertEquals(0, requisicoes.get())
        assertEquals(0, clientes.get(), "nem cliente do Jira chega a ser criado")
    }

    @Test
    fun codigoDoOperacaoNaoReferenciaClienteDoJira() {
        val pasta = TestPaths.mainSrcDir.resolve("kotlin/br/sidia/painel/operacao")
        val proibidos = listOf("JiraClient", "HttpClient", "LiveGuard", "createJiraHttpClient")
        java.nio.file.Files.list(pasta).use { arquivos ->
            arquivos.filter { it.fileName.toString().endsWith(".kt") }.forEach { arquivo ->
                val texto = java.nio.file.Files.readString(arquivo)
                for (nome in proibidos) {
                    assertTrue(nome !in texto, "${arquivo.fileName} não pode referenciar $nome (a operação nunca fala com o Jira)")
                }
            }
        }
    }
}
