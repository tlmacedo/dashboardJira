package br.sidia.painel.cards.fluxo

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.previsao.PrevisaoDto
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

/** Rota do Fluxo da semana sobre o TRB-1: visão por papel, número da barra igual ao drill e coerência com a Previsão. */
class FluxoDaSemanaRoutesTest {
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

    private fun fluxo(r: RespTrb) = Json.decodeFromString<FluxoDaSemanaDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")

    @Test
    fun numerosDoTrb1() = rodar { get ->
        val d = fluxo(get("gerente.exemplo", "/api/cards/fluxo-da-semana"))
        assertEquals(12, d.semanas.size)
        assertEquals(listOf(10, 7, 4, 1, 5), listOf(d.entrou, d.comecou, d.saiu, d.canceladas, d.saldoDoBacklog))
        val porSemana = d.semanas.associateBy { it.semana }
        // entrou, começou, saiu, canceladas: a tabela do cenário, semana a semana.
        assertEquals(listOf(1, 0, 0, 0), porSemana.getValue("2026-09-14").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertEquals(listOf(4, 1, 0, 0), porSemana.getValue("2026-09-28").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertEquals(listOf(3, 4, 1, 0), porSemana.getValue("2026-10-05").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertEquals(listOf(0, 1, 2, 0), porSemana.getValue("2026-10-12").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertEquals(listOf(1, 1, 1, 1), porSemana.getValue("2026-10-19").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertEquals(listOf(1, 0, 0, 0), porSemana.getValue("2026-10-26").let { listOf(it.entrou, it.comecou, it.saiu, it.canceladas) })
        assertTrue(porSemana.getValue("2026-10-26").emCurso)
        // TG-116 (fechada sem resolução) não é saída nem cancelada; a semana de 19/10 empata entrada e saída: sem sinal.
        assertEquals(0, d.semanasCrescendo)
        assertFalse(d.backlogCrescendo)
        assertEquals(10, d.tasksNaJanela)
        assertTrue(d.avisos.isEmpty(), d.avisos.toString())
    }

    @Test
    fun numeroDaBarraIgualAoDrill() = rodar { get ->
        for (u in usuarios) {
            val d = fluxo(get(u, "/api/cards/fluxo-da-semana"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            for (s in d.semanas) {
                confere("FX-01", "entrada:${s.semana}", s.entrou)
                confere("FX-01", "inicio:${s.semana}", s.comecou)
                confere("FX-01", "saida:${s.semana}", s.saiu)
                confere("FX-01", "cancelada:${s.semana}", s.canceladas)
            }
            confere("FX-02", "entradas", d.entrou)
            confere("FX-02", "iniciadas", d.comecou)
            confere("FX-02", "saidas", d.saiu)
            confere("FX-02", "canceladas", d.canceladas)
            // Cada cartão só abre os seus segmentos.
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/FX-01/tasks?segmento=entradas").status)
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/FX-02/tasks?segmento=entrada:2026-10-05").status)
        }
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val d = fluxo(get("ana", "/api/cards/fluxo-da-semana"))
        // A ana criou TG-101, TG-111, TG-113 e TG-116; entregou TG-111 e TG-113: só 4 tasks na janela.
        assertEquals(listOf(4, 2), listOf(d.entrou, d.saiu))
        assertEquals(4, d.tasksNaJanela)
        val aviso = d.avisos.single { it.codigo == AvisosDoFluxo.AMOSTRA_PEQUENA_NA_JANELA }
        assertEquals(listOf("4", "5"), listOf(aviso.params["n"], aviso.params["minimo"]))
        val g = get("ana", "/api/cartoes/FX-02/tasks?segmento=entradas")
        assertTrue("TG-102" !in g.corpo && "TG-112" !in g.corpo && "bruno" !in g.corpo && "carla" !in g.corpo, g.corpo)
        // Squad leader: o squad dele (ana e bruno); a TG-103 e a TG-114 são da carla.
        val lider = fluxo(get("lider.alfa", "/api/cards/fluxo-da-semana"))
        assertEquals(7, lider.entrou) // TG-101, 102, 111, 112, 113, 115, 116
        // Pedir a série de outra pessoa não amplia a visão.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/fluxo-da-semana?pessoa=bruno").status)
    }

    @Test
    fun semSerieLadoALadoPorPessoa() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/fluxo-da-semana")
        // A resposta é só agregado: sem nome de pessoa, sem chave de task, sem campo de responsável.
        for (p in listOf("ana", "bruno", "carla", "lider.alfa")) assertFalse("\"$p\"" in r.corpo, "$p na resposta")
        assertFalse("TG-" in r.corpo, "chave de task na resposta")
        assertFalse(Regex("\"(pessoa|responsavel|assignee)").containsMatchIn(r.corpo), r.corpo)
        // Filtrar por pessoa devolve a série DELA (um recorte único), nunca várias séries lado a lado.
        val dela = fluxo(get("gerente.exemplo", "/api/cards/fluxo-da-semana?pessoa=bruno"))
        assertEquals(12, dela.semanas.size)
        assertEquals(listOf(3, 1), listOf(dela.entrou, dela.saiu)) // TG-102, 112, 115 entraram; TG-112 saiu
    }

    @Test
    fun mesmaSerieQueAPrevisao() = rodar { get ->
        for (u in listOf("gerente.exemplo", "lider.alfa", "ana")) {
            val f = fluxo(get(u, "/api/cards/fluxo-da-semana"))
            val p = Json.decodeFromString<PrevisaoDto>(get(u, "/api/cards/previsao").corpo)
            val entradas = p.chegadaSemanal.associate { it.semana to it.n }
            val saidas = p.vazaoSemanal.associate { it.semana to it.n }
            // As semanas FECHADAS em comum têm exatamente a mesma entrada e a mesma saída (função única SerieSemanal).
            val fechadas = f.semanas.filterNot { it.emCurso }
            assertTrue(fechadas.isNotEmpty())
            for (s in fechadas) {
                assertEquals(entradas.getValue(s.semana), s.entrou, "$u entrada ${s.semana}")
                assertEquals(saidas.getValue(s.semana), s.saiu, "$u saída ${s.semana}")
            }
        }
    }

    @Test
    fun accessLogNaoGravaChaveDeIssue() = rodar { get ->
        get("gerente.exemplo", "/api/cards/fluxo-da-semana")
        get("gerente.exemplo", "/api/cartoes/FX-01/tasks?segmento=${enc("saida:2026-10-12")}")
        get("gerente.exemplo", "/api/cartoes/FX-02/tasks?segmento=ruim")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(3, linhas.size, linhas.toString())
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, linhas.toString())
        assertTrue("saida:2026-10-12" in linhas[1], linhas[1])
    }

    @Test
    fun filtrosInvalidosPapelEStatusNaoMapeado() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/fluxo-da-semana?unit=hora").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/fluxo-da-semana").status)
        val r = get("gerente.exemplo", "/api/cards/fluxo-da-semana")
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = fluxo(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, fluxo(r).entrou)
    }
}
