package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.negativas.NegativasDto
import br.sidia.painel.trabalho.Neg1
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
import kotlin.test.assertTrue

/** Rota do card Negativas sobre o cenário NEG-1: visão por papel, drill do mesmo tamanho, avisos e filtros. */
class NegativasRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Neg1.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun negativas(r: RespTrb) = Json.decodeFromString<NegativasDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = negativas(get(u, "/api/cards/negativas?$PERIODO"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?$PERIODO&segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("NE-01", "reabertas", d.reabertas)
            confere("NE-02", "devolvidas", d.devolvidas)
            confere("NE-03", "canceladas", d.canceladas)
            confere("NE-04", "concluidasComAtraso", d.concluidasComAtraso)
            confere("NE-04", "atrasadasSemJustificativa", d.atrasadasSemJustificativa)
            confere("NE-05", "abertasVencidas", d.abertasVencidas)
            confere("NE-06", "estouroDeEstimativa", d.estouroDeEstimativa)
            confere("NE-07", "muitasTrocas", d.muitasTrocas)
            confere("NE-08", "concluidas", d.concluidas)
            confere("NE-08", "taxa:reabertura", d.reabertas)
            confere("NE-08", "taxa:devolucao", d.devolvidas)
            confere("NE-08", "taxa:atraso", d.concluidasComAtraso)
            confere("NE-08", "taxa:atrasoSemJustificativa", d.atrasadasSemJustificativa)
            for (p in d.porResponsavel) {
                confere("NE-10", "pessoa:${p.pessoa}:reabertas", p.reabertas)
                confere("NE-10", "pessoa:${p.pessoa}:devolvidas", p.devolvidas)
                confere("NE-10", "pessoa:${p.pessoa}:atrasadas", p.atrasadas)
            }
        }
        val g = negativas(get("gerente.exemplo", "/api/cards/negativas?$PERIODO"))
        assertEquals(listOf(1, 1, 1, 2, 1, 1, 1, 1, 4), listOf(g.reabertas, g.devolvidas, g.canceladas, g.concluidasComAtraso, g.atrasadasSemJustificativa, g.abertasVencidas, g.estouroDeEstimativa, g.muitasTrocas, g.concluidas))
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        // A ana é dona de TG-133 (aberta), TG-134 (3 trocas, fechada) e TG-137 (atrasada, justificada).
        val d = negativas(get("ana", "/api/cards/negativas?$PERIODO"))
        assertEquals(listOf("ana"), d.porResponsavel.map { it.pessoa })
        assertEquals(2, d.concluidas)
        assertEquals(1, d.concluidasComAtraso)
        assertEquals(0, d.atrasadasSemJustificativa)
        assertEquals(1, d.muitasTrocas)
        assertEquals(0, d.canceladas)
        // As tasks dos colegas nunca aparecem na gaveta dela.
        val g = get("ana", "/api/cartoes/NE-04/tasks?$PERIODO&segmento=concluidasComAtraso")
        assertTrue("TG-136" !in g.corpo && "carla" !in g.corpo, g.corpo)
    }

    @Test
    fun squadLeaderVeSoOSeuSquad() = rodar { get ->
        val d = negativas(get("lider.alfa", "/api/cards/negativas?$PERIODO"))
        // Squad Alfa = ana e bruno: TG-131, TG-133, TG-134, TG-135, TG-137 (a carla é do Beta).
        assertEquals(listOf("ana"), d.porResponsavel.map { it.pessoa })
        assertEquals(1, d.reabertas)
        assertEquals(0, d.devolvidas)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/negativas?$PERIODO&pessoa=carla").status)
    }

    @Test
    fun periodoDeSetembroContaAReaberturaAntiga() = rodar { get ->
        val d = negativas(get("gerente.exemplo", "/api/cards/negativas?de=2026-09-01&ate=2026-09-30"))
        assertEquals(1, d.reabertas) // TG-133, reaberta em 20/09
    }

    @Test
    fun statusNaoMapeado200() = rodar(Neg1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", "/api/cards/negativas?$PERIODO")
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = negativas(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, negativas(r).concluidas)
    }

    @Test
    fun filtrosInvalidosEPapelSemPermissao() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/negativas?unit=hora").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/negativas?de=2026-10-30&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/negativas?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/negativas").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/NE-01/tasks?$PERIODO&segmento=ruim").status)
    }
}
