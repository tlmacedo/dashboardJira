package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.AppContext
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
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
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/sync/status` contra SQLite real em arquivo temporário: só para o gerente, sempre auditada e sem segredos.
 * Nenhuma rede, nenhum Jira.
 */
class SyncStatusRoutesTest {
    companion object {
        private val pg = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
        }

        private const val TOKEN = "token-de-teste-123"
        private const val HOST = "jira.exemplo.invalid"
        private const val USUARIO_SERVICO = "svc-sync-secreto"
    }

    private val zona = ZoneId.of("America/Manaus")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-25T16:00:00Z"), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)

    private fun pessoa(id: String, papel: Role, squad: String? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, null), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("ana", Role.USUARIO, "Alfa"),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
        ),
    )

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private fun settings(live: Boolean) = Settings(
        jiraLiveEnabled = live,
        dashboardDevMode = false,
        jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://$HOST", JiraAuthKind.BASIC, USUARIO_SERVICO, TOKEN)),
    )

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
        ctx = null
    }

    private fun contexto(live: Boolean): AppContext =
        testContextComBanco(settings(live), pg, diretorio, relogio).also { ctx = it }

    private fun estado(c: AppContext) = SqliteSyncStateStore(requireNotNull(c.dataSource))

    private fun linhasDoAccessLog(c: AppContext): List<Triple<String, String, String>> =
        requireNotNull(c.dataSource).connection.use { con ->
            con.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private fun rodar(c: AppContext, bloco: suspend (suspend (String?, String) -> Resp) -> Unit) = testApplication {
        application { module(c.settings, c, autenticador) }
        val http: HttpClient = client
        bloco { usuario, caminho ->
            val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
            Resp(r.status, r.bodyAsText())
        }
    }

    @Test
    fun statusSoParaGerente() {
        val c = contexto(live = false)
        rodar(c) { get ->
            val ok = get("gerente.exemplo", "/api/sync/status")
            assertEquals(HttpStatusCode.OK, ok.status, ok.corpo)
            for (u in listOf("coord.um", "lider.alfa", "ana")) {
                val r = get(u, "/api/sync/status")
                assertEquals(HttpStatusCode.Forbidden, r.status, u)
                assertEquals("", r.corpo, u)
            }
            assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/sync/status").status)
        }
        // Toda chamada autenticada grava access_log, inclusive as negadas, com o resultado.
        val log = linhasDoAccessLog(c)
        assertEquals(listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana"), log.map { it.first })
        assertTrue(log.all { it.second == "/api/sync/status" })
        assertEquals(listOf("200", "403", "403", "403"), log.map { Regex("\"resultado\":\"(\\d+)\"").find(it.third)?.groupValues?.get(1) })
    }

    @Test
    fun statusNaoVazaSegredos() {
        val c = contexto(live = true)
        val e = estado(c)
        val id = e.iniciar("samsung", SyncKind.INCREMENTAL, Instant.parse("2026-10-25T15:00:00Z"))
        e.concluir(id, RunStatus.FALHA, RunResumo(requisicoes = 3), "HTTP_401", 401, Instant.parse("2026-10-25T15:01:00Z"))
        rodar(c) { get ->
            // A subida do app (na 1ª requisição) limpa as suspensões (reiniciar equivale a religar): suspende depois dela.
            get(null, "/api/health")
            e.suspender("samsung", "HTTP_401", Instant.parse("2026-10-25T15:01:00Z"))
            val r = get("gerente.exemplo", "/api/sync/status")
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            for (proibido in listOf(TOKEN, HOST, USUARIO_SERVICO, "https://", "gerente.exemplo", "jql", "Basic ")) {
                assertFalse(proibido in r.corpo, "o corpo não pode conter '$proibido': ${r.corpo}")
            }
            // Os códigos (e só eles) aparecem.
            assertTrue("HTTP_401" in r.corpo, r.corpo)
            val dto = Json.decodeFromString<SyncStatusDto>(r.corpo)
            val inst = dto.instancias.single()
            assertEquals("HTTP_401", inst.suspensa)
            assertEquals("SUSPENSA_POR_ERRO", inst.gate)
            assertEquals("HTTP_401", inst.execucoes.single().codigoErro)
            assertEquals("FALHA", inst.execucoes.single().status)
            assertEquals(3, inst.execucoes.single().requisicoes)
        }
    }

    @Test
    fun statusMostraDesligadoPelaFlag() {
        val desligado = contexto(live = false)
        rodar(desligado) { get ->
            val dto = Json.decodeFromString<SyncStatusDto>(get("gerente.exemplo", "/api/sync/status").corpo)
            assertEquals("DESLIGADO_PELA_FLAG", dto.modo)
            assertFalse(dto.liveEnabled)
            assertTrue(dto.instancias.isNotEmpty())
            assertTrue(dto.instancias.all { it.gate == "DESLIGADO_PELA_FLAG" })
            assertNull(dto.retencao)
        }
        desligado.close()
        val ligado = contexto(live = true)
        rodar(ligado) { get ->
            val dto = Json.decodeFromString<SyncStatusDto>(get("gerente.exemplo", "/api/sync/status").corpo)
            assertEquals("PERIODICO", dto.modo)
            assertTrue(dto.liveEnabled)
            assertTrue(dto.instancias.all { it.gate == "LIBERADA" })
        }
    }

    @Test
    fun statusTrazRetencaoCursorEReconciliacao() {
        val c = contexto(live = true)
        val e = estado(c)
        e.avancarCursor("samsung", Instant.parse("2026-10-25T14:00:00Z"))
        e.marcarReconciliacao("samsung", Instant.parse("2026-10-25T07:00:00Z"))
        val r = e.iniciar(INSTANCIA_RETENCAO, SyncKind.RETENCAO, Instant.parse("2026-10-25T08:00:00Z"))
        e.concluir(r, RunStatus.OK, RunResumo(removidas = 7), null, null, Instant.parse("2026-10-25T08:00:05Z"))
        rodar(c) { get ->
            val dto = Json.decodeFromString<SyncStatusDto>(get("gerente.exemplo", "/api/sync/status").corpo)
            val inst = dto.instancias.single()
            assertEquals("2026-10-25T14:00:00Z", inst.cursor)
            assertEquals("2026-10-25T07:00:00Z", inst.ultimaReconciliacao)
            assertNull(inst.suspensa)
            val ret = assertNotNull(dto.retencao)
            assertEquals("RETENCAO", ret.tipo)
            assertEquals("OK", ret.status)
            assertEquals(7, ret.removidas)
        }
    }

    @Test
    fun metaNaoMudou() {
        val c = contexto(live = false)
        rodar(c) { get ->
            val r = get(null, "/api/meta")
            assertEquals(HttpStatusCode.OK, r.status)
            assertEquals("""{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null}""", r.corpo)
        }
    }
}
