package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `GET /api/filtros/opcoes` (Plano 10, T2): opções só do escopo visível de quem pergunta. */
class OpcoesDeFiltroRoutesTest {
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

    private fun issuesComCampos() = listOf(
        Trb1.issue(
            "TG-201", "Checklist", "ana", "10-01T12", "Open", prioridade = "High", osVersion = "Android 17.0 (OneUI_9.0)",
            campos = mapOf("SKU Type" to "MR", "Model Name" to "MODELO-A", "Checklist Type" to "Full"),
        ),
        Trb1.issue(
            "TG-202", "Checklist Review", "bruno", "10-01T12", "Open", prioridade = "Low",
            campos = mapOf("SKU Type" to "mr ", "Model Name" to "MODELO-B"),
        ),
        Trb1.issue(
            "TG-203", "Task", "carla", "10-01T12", "Open", prioridade = "Medium", osVersion = "Android 16.0 (16)",
            campos = mapOf("SKU Type" to "1st Carrier (Global)", "Model Name" to "SO-DA-CARLA"),
        ),
        // Resolvida há mais de 180 dias de Manaus: fora das opções.
        Trb1.issue(
            "TG-204", "Epic", "ana", "01-01T12", "Closed", resolution = "Done", resolvida = "01-05T12",
            campos = mapOf("SKU Type" to "ANTIGO"),
        ),
    )

    private fun rodar(
        issues: List<Trb1.IssueDeTeste> = issuesComCampos(),
        bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit,
    ) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun opcoes(r: RespTrb) = Json.decodeFromString<OpcoesDeFiltroDto>(r.corpo)

    private fun OpcoesDeFiltroDto.valores(slug: String) = campos.first { it.slug == slug }.valores

    @Test
    fun opcoesSoDoEscopoVisivel() = rodar { get ->
        val ana = opcoes(get("ana", "/api/filtros/opcoes"))
        assertEquals(listOf("ana"), ana.pessoas)
        assertEquals(listOf("Alfa"), ana.squads)
        assertEquals(listOf("TG"), ana.prefixosDeChave)
        assertEquals(listOf("MR"), ana.valores("sku-type"))
        assertEquals(listOf("MODELO-A"), ana.valores("model-name"))
        assertEquals(listOf("Checklist"), ana.tipos)
        assertEquals(listOf("High"), ana.prioridades)
        assertEquals(listOf("Android 17.0 (OneUI_9.0)"), ana.os)

        val lider = opcoes(get("lider.alfa", "/api/filtros/opcoes"))
        assertTrue("ana" in lider.pessoas && "bruno" in lider.pessoas && "carla" !in lider.pessoas, lider.pessoas.toString())
        assertEquals(listOf("Alfa"), lider.squads)
        assertEquals(listOf("MR"), lider.valores("sku-type"))
        assertEquals(listOf("Checklist", "Checklist Review"), lider.tipos)

        val gerente = opcoes(get("gerente.exemplo", "/api/filtros/opcoes"))
        assertTrue(setOf("ana", "bruno", "carla").all { it in gerente.pessoas })
        assertEquals(listOf("Alfa", "Beta"), gerente.squads)
        assertEquals(listOf("1st Carrier (Global)", "MR"), gerente.valores("sku-type"))
    }

    @Test
    fun pessoasEmOrdemAlfabetica() = rodar { get ->
        val g = opcoes(get("coord.um", "/api/filtros/opcoes"))
        assertEquals(g.pessoas.sortedBy { it.lowercase() }, g.pessoas)
        assertEquals(g.squads.sorted(), g.squads)
        assertEquals(g.tipos.sortedBy { it.lowercase() }, g.tipos)
    }

    @Test
    fun valoresSemDuplicarCaixa() = rodar { get ->
        // "MR" (ana) e "mr " (bruno): uma só opção, com a primeira grafia em ordem alfabética.
        val l = opcoes(get("lider.alfa", "/api/filtros/opcoes"))
        assertEquals(listOf("MR"), l.valores("sku-type"))
    }

    @Test
    fun todaConsultaGravaAccessLog() = rodar { get ->
        get("ana", "/api/filtros/opcoes")
        val rotas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route FROM access_log").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertTrue("/api/filtros/opcoes" in rotas, rotas.toString())
    }

    @Test
    fun semDadosListasVazias() = rodar(emptyList()) { get ->
        val o = opcoes(get("gerente.exemplo", "/api/filtros/opcoes"))
        assertEquals(emptyList(), o.tipos)
        assertEquals(emptyList(), o.prioridades)
        assertEquals(emptyList(), o.os)
        assertTrue(o.campos.all { it.valores.isEmpty() })
        assertEquals(emptyList(), o.avisos)
    }

    @Test
    fun limiteDe200ComAviso() {
        val muitas = (1..230).map { n ->
            Trb1.issue("TG-${300 + n}", "Checklist", "ana", "10-01T12", "Open", campos = mapOf("Model Name" to "MODELO-%03d".format(n)))
        }
        rodar(muitas) { get ->
            val o = opcoes(get("ana", "/api/filtros/opcoes"))
            assertEquals(200, o.valores("model-name").size)
            assertTrue(o.avisos.any { it.codigo == "OPCOES_TRUNCADAS" })
        }
    }

    @Test
    fun semLoginRecebe401() = rodar { get ->
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/filtros/opcoes").status)
    }
}
