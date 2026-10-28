package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.gargalos.GargalosDto
import br.sidia.painel.cards.tendencia.TendenciaDto
import br.sidia.painel.config.Settings
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rotas de Gargalos e Tendência sobre o CAP-1: só liderança, visão do papel, drill do mesmo tamanho e aviso das horas atuais. */
class GargalosTendenciaRoutesTest {
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

    private fun rodar(
        issues: List<Trb1.IssueDeTeste> = Cap1.issuesParaBanco(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit,
    ) {
        val c = testContextComBanco(
            Settings(dashboardDevMode = false), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        ).also { ctx = it }
        Trb1.carregar(requireNotNull(c.dataSource), issues)
        rodarTrb1(c, bloco = bloco)
    }

    private fun gargalos(r: RespTrb) = Json.decodeFromString<GargalosDto>(r.corpo)
    private fun tendencia(r: RespTrb) = Json.decodeFromString<TendenciaDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun gargalosDoCap1() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/gargalos")
        assertEquals(HttpStatusCode.OK, r.status)
        val d = gargalos(r)
        assertEquals(60, d.dias.size)
        assertEquals("2026-10-30", d.dias.last())
        assertEquals(listOf("Open", "In Progress"), d.etapas.map { it.status })
        assertEquals(3, d.etapas.first { it.status == "Open" }.wip)
        assertEquals(1, d.etapas.first { it.status == "In Progress" }.wip)
        assertTrue(d.etapas.none { it.gargalo })
        assertEquals(0, d.acimaDaCapacidade)
        assertEquals(3, d.comFolga)
        assertTrue(d.faixas.all { it.contagens.size == 60 })
    }

    @Test
    fun gargalosESoLideranca() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/gargalos").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cartoes/GA-02/tasks?segmento=${enc("status:Open")}").status)
        assertEquals(HttpStatusCode.OK, get("lider.alfa", "/api/cards/gargalos").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/gargalos").status)
        // O líder só enxerga as tasks do próprio squad: 2 em aberto (Open) e 1 em andamento, sem a da carla.
        val lider = gargalos(get("lider.alfa", "/api/cards/gargalos"))
        assertEquals(2, lider.etapas.first { it.status == "Open" }.wip)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        suspend fun confere(u: String, cartao: String, segmento: String, esperado: Int) {
            val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
            assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
            assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
            assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa")) {
            val d = gargalos(get(u, "/api/cards/gargalos"))
            for (e in d.etapas) confere(u, "GA-02", "status:${e.status}", e.wip)
            confere(u, "GA-03", "acimaDaCapacidade", d.tasksAcimaDaCapacidade)
            confere(u, "GA-04", "comFolga", d.tasksComFolga)
        }
        // Segmento de outro cartão é 400.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/GA-03/tasks?segmento=comFolga").status)
    }

    @Test
    fun tendenciaSoLideranca() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/tendencia").status)
        assertEquals(HttpStatusCode.OK, get("coord.um", "/api/cards/tendencia").status)
    }

    @Test
    fun pessoasAlfabeticasEAvisoDeHorasAtuais() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/tendencia")
        val d = tendencia(r)
        assertEquals(listOf("ana", "bruno", "carla"), d.linhas.map { it.pessoa })
        assertEquals(12, d.semanas.size)
        assertTrue(d.linhas.all { it.ocupacoes.size == 12 && it.padrao in setOf("RECORRENTE", "PONTUAL", "NORMAL") })
        assertTrue(d.avisos.any { it.codigo == AvisosDePessoas.HORAS_ATUAIS_NO_PASSADO })
        val lider = tendencia(get("lider.alfa", "/api/cards/tendencia"))
        assertEquals(listOf("ana", "bruno"), lider.linhas.map { it.pessoa })
        assertTrue("carla" !in get("lider.alfa", "/api/cards/tendencia").corpo)
    }

    @Test
    fun statusNaoMapeado200ComAvisoBloqueante() = rodar(Cap1.issuesParaBanco() + Trb1.issue("TG-498", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        for (card in listOf("gargalos", "tendencia")) {
            val r = get("gerente.exemplo", "/api/cards/$card")
            assertEquals(HttpStatusCode.OK, r.status, card)
            assertTrue(AvisoDto.STATUS_SEM_MAPEAMENTO in r.corpo && AvisoDto.BLOQUEANTE in r.corpo, card)
        }
    }
}
