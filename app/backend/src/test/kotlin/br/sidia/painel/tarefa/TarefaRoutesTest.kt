package br.sidia.painel.tarefa

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.http.ErroDto
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gaveta universal da task (`GET /api/tarefa/{key}`, Plano 10, T5): visão por papel, 404 fora dela e link só com a flag. */
class TarefaRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    /** O cenário TRB-1 mais TG-501: hoje de `carla`, mas `ana` já foi a responsável (visível a ela pela posse). */
    private fun issues() = Trb1.issues() + Trb1.issue(
        "TG-501", "Checklist", "carla", "10-01T12", "In Progress", prioridade = "Medium",
        campos = mapOf("SKU Type" to "MR", "Model Name" to "MODELO-A"),
        eventos = Trb1.trilha("TG-501", "Open", "10-02T12" to "In Progress") + Trb1.ev("TG-501", "assignee", "ana", "carla", "10-10T12"),
    )

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues()).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun tarefa(r: RespTrb) = Json.decodeFromString<TarefaDto>(r.corpo)

    @Test
    fun tarefaVisivelDevolveTudo() = rodar { get ->
        val r = get("gerente.exemplo", "/api/tarefa/TG-101")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val t = tarefa(r)
        assertEquals("TG-101", t.key)
        assertEquals("Checklist", t.tipo)
        assertEquals("In Progress", t.status)
        assertEquals("ana", t.responsavel)
        assertEquals("High", t.prioridade)
        assertEquals("2026-10-28", t.vencimento)
        // Tempo por status em ordem de primeira entrada, em dias úteis.
        assertEquals(listOf("Open", "In Progress"), t.tempoPorStatus.map { it.status })
        assertTrue(t.tempoPorStatus.all { it.dias >= 0.0 })
        // Posse: ana ficou com a task o tempo todo.
        assertEquals(listOf("ana"), t.posse.map { it.pessoa })
        assertEquals(1.0, t.posse.single().fracao, 1e-9)
        // Vencida e aberta: o fator de risco aparece pelo nome.
        assertTrue("VENCIDA" in t.fatoresDeRisco, t.fatoresDeRisco.toString())
        // Histórico campo a campo para a coordenação e o gerente.
        assertNotNull(t.historico)
        assertTrue(t.historico.any { it.campo == "status" })
        // Flag desligada: sem link.
        assertNull(t.linkJira)
    }

    @Test
    fun resolvidaNaoTemFatoresDeRisco() = rodar { get ->
        val t = tarefa(get("gerente.exemplo", "/api/tarefa/TG-111"))
        assertEquals(emptyList(), t.fatoresDeRisco)
        assertEquals("Done", t.resolucao)
        assertNotNull(t.resolvida)
    }

    @Test
    fun camposDoDicionarioChegam() = rodar { get ->
        val t = tarefa(get("gerente.exemplo", "/api/tarefa/TG-501"))
        assertEquals(mapOf("SKU Type" to "MR", "Model Name" to "MODELO-A"), t.campos)
    }

    @Test
    fun tarefaForaDaVisao404() = rodar { get ->
        // TG-103 é de carla e ana nunca a teve: 404 sem corpo útil (não confirma que a chave existe).
        val r = get("ana", "/api/tarefa/TG-103")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals("", r.corpo)
        // Chave que não existe responde igual.
        val inexistente = get("ana", "/api/tarefa/TG-9999")
        assertEquals(HttpStatusCode.NotFound, inexistente.status)
        assertEquals("", inexistente.corpo)
        // A consulta ficou no access_log, sem a chave.
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(2, linhas.size)
        assertTrue(linhas.all { it.startsWith("/api/tarefa/{key}") && "404" in it }, linhas.toString())
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, linhas.toString())
    }

    @Test
    fun historicoSoParaCoordenacaoEGerente() = rodar { get ->
        assertNull(tarefa(get("lider.alfa", "/api/tarefa/TG-101")).historico)
        assertNull(tarefa(get("ana", "/api/tarefa/TG-101")).historico)
        assertNotNull(tarefa(get("coord.um", "/api/tarefa/TG-101")).historico)
        // O campo some do JSON de quem não vê (nada de "historico":[] que pareça vazio).
        assertFalse("\"historico\":[" in get("ana", "/api/tarefa/TG-101").corpo)
    }

    @Test
    fun exDonoNaoVeMaisATask() = rodar { get ->
        // Ruling (I-2): a gaveta segue só o responsável ATUAL. ana já teve TG-501, mas hoje é de carla: 404 sem corpo.
        val r = get("ana", "/api/tarefa/TG-501")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals("", r.corpo)
        // O gerente vê a task e o responsável atual.
        assertEquals("carla", tarefa(get("gerente.exemplo", "/api/tarefa/TG-501")).responsavel)
    }

    @Test
    fun chaveCodificadaComBarraNaoFalsificaARotaDoLog() = rodar { get ->
        val r = get("gerente.exemplo", "/api/tarefa/..%2Fx%2FTG-1")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        val rotas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route FROM access_log").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertEquals(listOf("/api/tarefa/{key}"), rotas)
    }

    @Test
    fun chaveInvalida400ComCodigo() = rodar { get ->
        val r = get("gerente.exemplo", "/api/tarefa/TG-1%3Bdrop")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("CHAVE_INVALIDA", Json.decodeFromString<ErroDto>(r.corpo).codigo)
    }

    @Test
    fun nenhumHostNoCodigo() {
        // O link sai da configuração (JIRA_<INSTANCIA>_URL): o código da gaveta não escreve host nem endereço do Jira.
        val pasta = java.nio.file.Path.of("src/main/kotlin/br/sidia/painel/tarefa")
        java.nio.file.Files.list(pasta).use { arquivos ->
            for (arq in arquivos.toList()) {
                val texto = java.nio.file.Files.readString(arq)
                assertFalse("https://" in texto || "http://" in texto, "${arq.fileName} escreve um endereço")
                assertFalse(Regex("jira\\.[a-z]").containsMatchIn(texto), "${arq.fileName} cita um host do Jira")
            }
        }
    }

    @Test
    fun semLinkComFlagDesligada() = rodar { get ->
        assertNull(tarefa(get("gerente.exemplo", "/api/tarefa/TG-101")).linkJira)
    }

    @Test
    fun rotaNaoFazRequisicaoELinkSaiDaConfiguracao() {
        val contador = AtomicInteger()
        val settings = Settings(
            dashboardDevMode = false, jiraLiveEnabled = true,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid/base/", JiraAuthKind.BEARER, null, "token-ficticio-abc123"),
            ),
        )
        val fabrica = JiraClientFactory(settings, { HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) }) })
        val c = testContextComBanco(
            settings, banco, Trb1.papeis, Trb1.relogio, jira = fabrica, statusMap = Trb1.statusMap, calendar = Trb1.calendario,
        ).also { ctx = it }
        Trb1.carregar(requireNotNull(c.dataSource), issues())
        rodarTrb1(c) { get ->
            val t = tarefa(get("gerente.exemplo", "/api/tarefa/TG-101"))
            assertEquals("https://jira.exemplo.invalid/base/browse/TG-101", t.linkJira)
        }
        assertEquals(0, contador.get(), "montar o link não chama o Jira")
    }
}
