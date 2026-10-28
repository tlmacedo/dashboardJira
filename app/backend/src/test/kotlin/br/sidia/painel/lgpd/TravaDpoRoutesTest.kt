package br.sidia.painel.lgpd

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.http.ErroDto
import br.sidia.painel.integridade.Int2
import br.sidia.painel.module
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.principalFor
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A trava do DPO pelo HTTP: produção SEM o aceite fecha Auditoria e Integridade com 403 `AGUARDANDO_ACEITE_DPO` (depois do
 * papel, sempre registrada, sem nenhum dado no corpo); COM o aceite tudo volta a responder. SQLite real; nenhuma rede.
 */
class TravaDpoRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        val SEM_ACEITE = Settings(dashboardDevMode = false)
        val COM_ACEITE = Settings(dashboardDevMode = false, lgpd = LgpdSettings(aceiteDpo = true, aceiteRef = "DPO-TESTE-1"))

        private const val GERENTE = "gerente.exemplo"
        private const val COORD = "coord.um"

        /** Todas as rotas de dados que dependem da trava da Auditoria. */
        val ROTAS_AUDITORIA = listOf(
            "/api/auditoria/sinais", "/api/auditoria/historico/TG-101", "/api/cards/task-review",
            "/api/cartoes/AU-01/tasks?segmento=todos", "/api/cartoes/AU-02/tasks?segmento=sinal:PASSA_BOLA",
            "/api/cartoes/AU-03/tasks?segmento=concluidas", "/api/cartoes/DV-01/tasks?segmento=pares",
        )
        val ROTAS_INTEGRIDADE = listOf(
            "/api/integridade", "/api/integridade/catalogo", "/api/cartoes/IN-01/tasks?segmento=todas",
        )
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun rodar(settings: Settings, bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, Int2.issues(), settings).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun accessLog(): List<Pair<String, String>> = requireNotNull(ctx!!.dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
            buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2).orEmpty()) }
        }
    }

    private fun erro(r: RespTrb) = Json.decodeFromString<ErroDto>(r.corpo)

    @Test
    fun producaoSemAceiteBloqueiaAuditoria() = rodar(SEM_ACEITE) { get ->
        for (u in listOf(GERENTE, COORD)) {
            for (rota in ROTAS_AUDITORIA) {
                val r = get(u, rota)
                assertEquals(HttpStatusCode.Forbidden, r.status, "$u $rota")
                assertEquals(ErroDto("AGUARDANDO_ACEITE_DPO", mapOf("modulo" to "AUDITORIA")), erro(r), "$u $rota")
                // Só o código e o módulo: nenhum outro campo, nenhum dado.
                assertEquals(setOf("codigo", "params"), Json.parseToJsonElement(r.corpo).jsonObject.keys, rota)
            }
        }
        val log = accessLog()
        assertEquals(2 * ROTAS_AUDITORIA.size, log.size, "cada tentativa fica no access_log")
        assertTrue(log.all { "\"resultado\":\"403\"" in it.second }, log.toString())
        assertTrue(log.all { "AGUARDANDO_ACEITE_DPO" in it.second }, "o registro leva o motivo: $log")
    }

    @Test
    fun producaoSemAceiteBloqueiaIntegridade() = rodar(SEM_ACEITE) { get ->
        for (rota in ROTAS_INTEGRIDADE) {
            val r = get(GERENTE, rota)
            assertEquals(HttpStatusCode.Forbidden, r.status, rota)
            assertEquals(ErroDto("AGUARDANDO_ACEITE_DPO", mapOf("modulo" to "INTEGRIDADE")), erro(r), rota)
            assertEquals(setOf("codigo", "params"), Json.parseToJsonElement(r.corpo).jsonObject.keys, rota)
        }
        assertEquals(ROTAS_INTEGRIDADE.size, accessLog().size)
        assertTrue(accessLog().all { "\"resultado\":\"403\"" in it.second && "AGUARDANDO_ACEITE_DPO" in it.second })
    }

    @Test
    fun usuarioContinua403SemCodigoDoDpo() = rodar(SEM_ACEITE) { get ->
        for (u in listOf("ana", "lider.alfa")) {
            for (rota in ROTAS_AUDITORIA + ROTAS_INTEGRIDADE) {
                val r = get(u, rota)
                assertEquals(HttpStatusCode.Forbidden, r.status, "$u $rota")
                assertEquals("", r.corpo, "$u $rota: nem o código do DPO (não fica sabendo do módulo)")
            }
        }
        // Coordenador não é gerente: a Integridade continua sem código especial para ele.
        for (rota in ROTAS_INTEGRIDADE) assertEquals("", get(COORD, rota).corpo, rota)
    }

    @Test
    fun comAceiteTudoComoAntes() = rodar(COM_ACEITE) { get ->
        for (rota in ROTAS_AUDITORIA + ROTAS_INTEGRIDADE) {
            val r = get(GERENTE, rota)
            // O histórico de uma issue que não existe responde 404 (e não 403): a trava abriu.
            val esperado = if (rota.contains("historico")) HttpStatusCode.NotFound else HttpStatusCode.OK
            assertEquals(esperado, r.status, "$rota ${r.corpo.take(80)}")
        }
    }

    @Test
    fun auditoriaGuardaSoQuemConsultouNuncaOAlvoNemOTotal() = rodar(COM_ACEITE) { get ->
        val rotas = listOf(
            "/api/cartoes/AU-01/tasks?segmento=todos&pessoa=bruno&squad=Alfa",
            "/api/cartoes/AU-02/tasks?segmento=sinal:PASSA_BOLA&pessoa=bruno",
            "/api/cartoes/DV-01/tasks?segmento=pares&squad=Alfa",
            "/api/auditoria/sinais?pessoa=bruno&squad=Alfa&sinal=PASSA_BOLA",
            "/api/cards/task-review?pessoa=bruno&squad=Alfa",
            "/api/auditoria/historico/TG-101",
        )
        for (rota in rotas) get(GERENTE, rota)
        val log = accessLog()
        assertEquals(rotas.size, log.size)
        for ((rota, filtros) in log) {
            for (proibido in listOf("bruno", "Alfa", "PASSA_BOLA", "total", "TG-101")) {
                assertTrue(proibido !in filtros && proibido !in rota, "$rota guarda '$proibido': $filtros")
            }
        }
    }

    @Test
    fun devComJiraLigadoSemAceiteTambemBloqueia() = rodar(Settings(dashboardDevMode = true, jiraLiveEnabled = true)) { get ->
        for (rota in ROTAS_AUDITORIA + ROTAS_INTEGRIDADE) {
            assertEquals(HttpStatusCode.Forbidden, get(GERENTE, rota).status, rota)
            assertEquals("AGUARDANDO_ACEITE_DPO", erro(get(GERENTE, rota)).codigo, rota)
        }
    }

    @Test
    fun devOfflineContinuaLiberado() = rodar(Settings(dashboardDevMode = true)) { get ->
        for (rota in ROTAS_AUDITORIA.filterNot { it.contains("historico") } + ROTAS_INTEGRIDADE) {
            assertEquals(HttpStatusCode.OK, get(GERENTE, rota).status, rota)
        }
    }

    @Test
    fun meDtoListaModulosBloqueados() {
        rodar(SEM_ACEITE) { get ->
            val me = Json.parseToJsonElement(get(GERENTE, "/api/me").corpo).jsonObject
            assertEquals(listOf("AUDITORIA", "INTEGRIDADE"), me.getValue("modulosBloqueados").jsonArray.map { it.jsonPrimitive.content })
        }
        encerrar()
        rodar(COM_ACEITE) { get ->
            val me = Json.parseToJsonElement(get(GERENTE, "/api/me").corpo).jsonObject
            assertEquals(emptyList(), me["modulosBloqueados"]?.jsonArray?.toList().orEmpty())
        }
    }

    /** Toda rota GET sob um prefixo sensível, achada na árvore de rotas: uma rota nova esquecida da trava falha aqui. */
    @Test
    fun todaRotaSensivelPassaPelaTrava() {
        val c = novoContextoTrb1(banco, Int2.issues(), SEM_ACEITE).also { ctx = it }
        val hoje = LocalDate.now(c.clock.withZone(c.settings.zone))
        val autenticador = object : Authenticator {
            override suspend fun authenticate(call: ApplicationCall): Principal? =
                call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, Trb1.papeis, hoje) }
        }
        val prefixos = listOf(
            "/api/auditoria", "/api/integridade", "/api/drill/auditoria", "/api/drill/integridade", "/api/cards/task-review",
            "/api/drill/task-review",
        )
        testApplication {
            application { module(c.settings, c, autenticador) }
            startApplication()
            val raiz: RoutingNode = application.routing { }
            val todas = mutableListOf<String>()
            fun andar(r: RoutingNode) {
                todas += r.toString()
                r.children.forEach(::andar)
            }
            andar(raiz)
            val achadas = todas.filter { "method:GET" in it }.map { it.substringBefore("/(method") }
                .filter { rota -> prefixos.any { rota.startsWith(it) } }.distinct()
            assertTrue(achadas.size >= 5, "o teste enxerga as rotas sensíveis: $achadas")
            for (rota in achadas) {
                val caminho = rota.replace(Regex("\\{[^}]+}"), "TG-101")
                val r = client.get(caminho) { header("X-Teste-Usuario", GERENTE) }
                assertEquals(HttpStatusCode.Forbidden, r.status, "$rota sem a trava do DPO")
                assertEquals("AGUARDANDO_ACEITE_DPO", Json.decodeFromString<ErroDto>(r.bodyAsText()).codigo, rota)
            }
        }
    }
}
