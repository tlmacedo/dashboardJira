package br.sidia.painel.cards.fluxo

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rota do Controle do ciclo sobre o TRB-1, em dias corridos: Checklist com ciclos 4 (TG-111), 9 (TG-112) e 13 (TG-113) e
 * Checklist Review com 1 (TG-114). TG-115 (cancelada) e TG-116 (fechada sem resolução) não são conclusão positiva.
 */
class ControleDoCicloRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Trb1.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun controle(r: RespTrb) = Json.decodeFromString<ControleDoCicloDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")
    private val card = "/api/cards/controle-ciclo?unit=corrido"

    @Test
    fun numerosDoTrb1() = rodar { get ->
        val d = controle(get("gerente.exemplo", card))
        assertEquals(listOf("Checklist", "Checklist Review"), d.series.map { it.tipo })
        val checklist = d.series.first()
        assertEquals(listOf(4.0, 9.0, 13.0), checklist.pontos.map { it.ciclo })
        assertEquals(listOf("2026-10-09", "2026-10-15", "2026-10-20"), checklist.pontos.map { it.dia })
        assertEquals(9.0, checklist.p50)
        assertEquals(11.8, checklist.p85!!, 1e-9)
        assertEquals(3, checklist.n)
        assertTrue(checklist.amostraPequena, "só 3 concluídas")
        assertEquals(null, checklist.janela)
        assertEquals(listOf(1.0), d.series.last().pontos.map { it.ciclo })
        assertEquals(4, d.concluidas)
        // 12 meses: de novembro de 2025 a outubro de 2026 (período padrão de 365 dias que termina em 30/10).
        assertEquals("2025-11", checklist.meses.first().mes)
        assertEquals("2026-10", checklist.meses.last().mes)
        assertEquals(12, checklist.meses.size)
        assertEquals(listOf(3), checklist.meses.map { it.n }.filter { it > 0 })
        assertEquals(setOf("Checklist", "Checklist Review"), d.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }.map { it.params.getValue("tipo") }.toSet())
        assertEquals("corrido", d.unit)
        assertEquals(30, d.limiteOutlierDias)
    }

    @Test
    fun numeroDoMesIgualAoDrill() = rodar { get ->
        for (u in usuarios) {
            val d = controle(get(u, card))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}&unit=corrido")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            for (s in d.series) {
                confere("CT-01", "tipo:${s.tipo}", s.n)
                for ((dia, pontos) in s.pontos.groupBy { it.dia }) confere("CT-01", "dia:$dia:${s.tipo}", pontos.size)
                for (m in s.meses) confere("CT-02", "mes:${m.mes}:${s.tipo}", m.n)
            }
            // Cada cartão só abre os seus segmentos.
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/CT-01/tasks?segmento=${enc("mes:2026-10:Checklist")}").status)
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/CT-02/tasks?segmento=${enc("tipo:Checklist")}").status)
        }
    }

    @Test
    fun usuarioVeSoOsProprios() = rodar { get ->
        // Ana: TG-111 (4) e TG-113 (13). Bruno: TG-112 (9). Carla: a Review TG-114.
        assertEquals(listOf(4.0, 13.0), controle(get("ana", card)).series.single().pontos.map { it.ciclo })
        assertEquals(listOf(9.0), controle(get("bruno", card)).series.single().pontos.map { it.ciclo })
        assertEquals("Checklist Review", controle(get("carla", card)).series.single().tipo)
        assertEquals(3, controle(get("lider.alfa", card)).series.single().n)
        val g = get("ana", "/api/cartoes/CT-01/tasks?segmento=${enc("tipo:Checklist")}&unit=corrido")
        assertTrue("TG-112" !in g.corpo && "bruno" !in g.corpo, g.corpo)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "$card&pessoa=bruno").status)
    }

    @Test
    fun respostaNaoTrazPessoaNemChave() = rodar { get ->
        val r = get("gerente.exemplo", card)
        for (p in listOf("ana", "bruno", "carla")) assertFalse("\"$p\"" in r.corpo, "$p na resposta")
        assertFalse("TG-" in r.corpo, "chave de task na resposta")
    }

    @Test
    fun semOutliersEhParametroValidado() = rodar(Trb1.issues() + longa()) { get ->
        val com = controle(get("gerente.exemplo", card))
        assertEquals(5, com.concluidas)
        assertTrue(com.series.first().pontos.any { it.outlier && it.ciclo == 45.0 })
        val sem = controle(get("gerente.exemplo", "$card&semOutliers=true"))
        assertEquals(listOf(4, 1), listOf(sem.concluidas, sem.outliersExcluidos))
        assertTrue(sem.series.first().pontos.none { it.outlier })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card&semOutliers=talvez").status)
        // O drill respeita o mesmo parâmetro: a task de 45 dias some do tipo.
        val tipo = gaveta(get("gerente.exemplo", "/api/cartoes/CT-01/tasks?segmento=${enc("tipo:Checklist")}&unit=corrido&semOutliers=true"))
        assertEquals(3, tipo.total)
    }

    private fun longa() = listOf(
        Trb1.issue(
            "TG-171", "Checklist", "ana", "08-01T12", "Closed", resolution = "Done", resolvida = "10-04T12",
            eventos = Trb1.trilha("TG-171", "Open", "08-20T12" to "In Progress", "10-04T12" to "Closed"),
        ),
    )

    @Test
    fun periodoEFiltrosEPapel() = rodar { get ->
        // Período curto: só a TG-113 (resolvida em 20/10) cai em 16 a 25/10.
        val curto = controle(get("gerente.exemplo", "$card&de=2026-10-16&ate=2026-10-25"))
        assertEquals(listOf("Checklist"), curto.series.map { it.tipo })
        assertEquals(listOf(13.0), curto.series.single().pontos.map { it.ciclo })
        assertEquals(1, curto.concluidas)
        assertEquals(listOf("2026-10"), curto.series.single().meses.map { it.mes })
        assertEquals(listOf("2026-10-16", "2026-10-25"), listOf(curto.de, curto.ate))
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card&de=2026-10-16&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/controle-ciclo?unit=hora").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, card).status)
    }

    @Test
    fun accessLogNaoGravaChaveDeIssue() = rodar { get ->
        get("gerente.exemplo", card)
        get("gerente.exemplo", "/api/cartoes/CT-01/tasks?segmento=${enc("dia:2026-10-09:Checklist")}&unit=corrido")
        get("gerente.exemplo", "/api/cartoes/CT-02/tasks?segmento=ruim")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(3, linhas.size, linhas.toString())
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, linhas.toString())
    }
}
