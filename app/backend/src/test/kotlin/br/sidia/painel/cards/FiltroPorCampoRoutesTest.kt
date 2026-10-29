package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.abertas.AbertasDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.fechadas.FechadasDto
import br.sidia.painel.http.ErroDto
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Filtro e agrupamento por campo do dicionário em Abertas e Fechadas (Plano 10, T3): card == drill. */
class FiltroPorCampoRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private const val PERIODO = "de=2026-10-01&ate=2026-10-30"
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun issues() = listOf(
        Trb1.issue("TG-401", "Checklist", "ana", "10-01T12", "Open", campos = mapOf("SKU Type" to "MR", "Checklist Type" to "Full")),
        Trb1.issue("TG-402", "Checklist", "bruno", "10-01T12", "Open", campos = mapOf("SKU Type" to "mr ", "Checklist Type" to "Regression")),
        Trb1.issue(
            "TG-403", "Checklist", "carla", "10-01T12", "Open",
            campos = mapOf("SKU Type" to "1st Carrier (Global)", "Checklist Type" to "Full"),
        ),
        Trb1.issue("TG-404", "Checklist", "ana", "10-01T12", "Open"),
        Trb1.issue(
            "TG-405", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-20T12",
            eventos = Trb1.trilha("TG-405", "Open", "10-05T12" to "In Progress", "10-20T12" to "Closed"),
            campos = mapOf("SKU Type" to "MR", "Checklist Type" to "Full"),
        ),
        Trb1.issue(
            "TG-406", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-21T12",
            eventos = Trb1.trilha("TG-406", "Open", "10-05T12" to "In Progress", "10-21T12" to "Closed"),
            campos = mapOf("SKU Type" to "1st Carrier (Global)"),
        ),
    )

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues()).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun abertas(r: RespTrb) = Json.decodeFromString<AbertasDto>(r.corpo)
    private fun fechadas(r: RespTrb) = Json.decodeFromString<FechadasDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun filtroDeCampoSemDiferenciarMaiusculas() = rodar { get ->
        // "mr" casa "MR" (ana) e "mr " (bruno, com espaço gravado no Jira).
        val d = abertas(get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=mr"))
        assertEquals(2, d.abertas)
        // Dois valores repetidos na URL = união.
        val dois = abertas(get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=mr&campo.sku-type=1st%20carrier%20(global)"))
        assertEquals(3, dois.abertas)
        // Campos diferentes = interseção.
        val inter = abertas(get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=mr&campo.checklist-type=full"))
        assertEquals(1, inter.abertas)
    }

    @Test
    fun issueSemOCampoNaoCasa() = rodar { get ->
        // TG-404 não tem SKU Type: fica fora de qualquer filtro por esse campo.
        val todas = abertas(get("gerente.exemplo", "/api/cards/abertas"))
        assertEquals(4, todas.abertas)
        val filtradas = abertas(get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=MR&campo.sku-type=1st%20Carrier%20(Global)"))
        assertEquals(3, filtradas.abertas)
    }

    @Test
    fun slugDesconhecido400ComCodigo() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/abertas?campo.x=1")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        val erro = Json.decodeFromString<ErroDto>(r.corpo)
        assertEquals("FILTRO_INVALIDO", erro.codigo)
        assertEquals("x", erro.params["campo"])
        // O slug esquisito não volta no corpo.
        val estranho = get("gerente.exemplo", "/api/cards/abertas?campo.%3Cscript%3E=1")
        assertEquals(HttpStatusCode.BadRequest, estranho.status)
        assertTrue("script" !in estranho.corpo)
    }

    @Test
    fun valorInvalido400() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=${"a".repeat(121)}").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/abertas?campo.sku-type=a%00b").status)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCardComFiltroDeCampo() = rodar { get ->
        val filtro = "campo.checklist-type=Full"
        for (u in listOf("gerente.exemplo", "lider.alfa", "ana")) {
            val a = abertas(get(u, "/api/cards/abertas?$filtro"))
            val ga = get(u, "/api/cartoes/AB-01/tasks?$filtro&segmento=abertas")
            assertEquals(HttpStatusCode.OK, ga.status, ga.corpo)
            assertEquals(a.abertas, gaveta(ga).total, u)
            assertEquals(a.abertas, gaveta(ga).linhas.size, u)
            val f = fechadas(get(u, "/api/cards/fechadas?$PERIODO&$filtro"))
            val gf = gaveta(get(u, "/api/cartoes/FE-01/tasks?$PERIODO&$filtro&segmento=concluidas"))
            assertEquals(f.concluidas, gf.total, u)
        }
        // O filtro realmente reduz: Full = TG-401 e TG-403 abertas (gerente); só TG-405 concluída.
        assertEquals(2, abertas(get("gerente.exemplo", "/api/cards/abertas?$filtro")).abertas)
        assertEquals(1, fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO&$filtro")).concluidas)
    }

    @Test
    fun agrupamentoPorCampoComSemValorPorUltimo() = rodar { get ->
        val d = abertas(get("gerente.exemplo", "/api/cards/abertas"))
        val sku = assertNotNull(d.porCampo.firstOrNull { it.slug == "sku-type" })
        assertEquals("SKU Type", sku.campo)
        assertEquals(listOf("1st Carrier (Global)", "MR", null), sku.grupos.map { it.valor })
        assertEquals(listOf(1, 2, 1), sku.grupos.map { it.n })
        assertNull(sku.grupos.last().valor)
        // Um agrupamento por campo com filtro: true.
        assertEquals(6, d.porCampo.size)
        val f = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO"))
        assertEquals(listOf("1st Carrier (Global)", "MR"), f.porCampo.first { it.slug == "sku-type" }.grupos.map { it.valor })
    }

    @Test
    fun drillDoGrupoIgualAoGrupo() = rodar { get ->
        for (u in listOf("gerente.exemplo", "lider.alfa", "ana")) {
            val d = abertas(get(u, "/api/cards/abertas"))
            for (agr in d.porCampo) for (g in agr.grupos) {
                val seg = "campo:${agr.slug}:${g.valor ?: "-"}"
                val r = get(u, "/api/cartoes/AB-10/tasks?segmento=${enc(seg)}")
                assertEquals(HttpStatusCode.OK, r.status, "$u $seg ${r.corpo}")
                assertEquals(g.n, gaveta(r).total, "$u $seg")
            }
            val f = fechadas(get(u, "/api/cards/fechadas?$PERIODO"))
            for (agr in f.porCampo) for (g in agr.grupos) {
                val seg = "campo:${agr.slug}:${g.valor ?: "-"}"
                val r = get(u, "/api/cartoes/FE-13/tasks?$PERIODO&segmento=${enc(seg)}")
                assertEquals(g.n, gaveta(r).total, "$u $seg")
            }
        }
    }

    @Test
    fun parametroDeCampoNaoAmpliaAVisao() = rodar { get ->
        // ana (USUARIO) pede o grupo MR: só as dela (TG-401), mesmo que bruno tenha "mr ".
        val r = get("ana", "/api/cartoes/AB-10/tasks?segmento=${enc("campo:sku-type:MR")}")
        assertEquals(1, gaveta(r).total)
        assertEquals("TG-401", gaveta(r).linhas.single().key)
    }
}
