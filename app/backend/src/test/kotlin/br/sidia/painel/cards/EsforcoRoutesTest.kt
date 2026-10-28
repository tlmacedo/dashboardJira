package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.esforco.EsforcoDto
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Esforço por task fechada pelo HTTP: três componentes e índice, posse no lugar do autor do worklog, versão e visão por papel. */
class EsforcoRoutesTest {
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

    private val tg401 = Trb1.issue(
        "TG-401", "Checklist", "bruno", "10-05T12", "Closed", resolution = "Done", resolvida = "10-12T12",
        labels = listOf("TEST-A", "test-b", "TEST-C", "login"),
        eventos = Trb1.trilha("TG-401", "Open", "10-06T12" to "In Progress", "10-09T12" to "Submitted", "10-12T12" to "Closed") +
            Trb1.ev("TG-401", "assignee", "ana", "bruno", "10-08T12"),
    )

    /**
     * TG-401 (o TG-1 do Plano 3): `ana` e depois `bruno`, worklogs de 6 h + 4 h. TG-405: review da `carla`, sem worklog e com
     * 2 h de tempo gasto. TG-406 cancelada (fora). TG-402 aberta (fora).
     */
    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val issues = listOf(
            tg401,
            Trb1.issue("TG-402", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-402", "Open", "10-21T12" to "In Progress")),
            Trb1.issue(
                "TG-405", "Checklist Review", "carla", "10-10T12", "Closed", resolution = "Done", resolvida = "10-27T12", gastoS = 7200,
                eventos = Trb1.trilha("TG-405", "Open", "10-12T12" to "In Progress", "10-27T12" to "Closed"),
            ),
            Trb1.issue(
                "TG-406", "Checklist", "bruno", "10-10T12", "Closed", resolution = "Won't Do", resolvida = "10-22T12",
                eventos = Trb1.trilha("TG-406", "Open", "10-22T12" to "Closed"),
            ),
        )
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        SqliteIssueStore(requireNotNull(c.dataSource)).upsert(
            tg401.json, tg401.eventos, Trb1.INSTANCIA, Trb1.CUSTOM_OS_VERSION, null, Trb1.agora, Trb1.ids,
            worklogs = listOf(
                WorklogRecord("1", "ana", Instant.parse("2026-10-06T13:00:00Z"), 6 * 3600L),
                WorklogRecord("2", "bruno", Instant.parse("2026-10-09T13:00:00Z"), 4 * 3600L),
            ),
        )
        rodarTrb1(c, bloco = bloco)
    }

    private fun esforco(r: RespTrb) = Json.decodeFromString<EsforcoDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun indiceDaTaskPeloHttp() = rodar { get ->
        val d = esforco(get("gerente.exemplo", "/api/cards/esforco"))
        assertEquals(listOf("TG-401", "TG-405"), d.tasks.map { it.key })
        val t = d.tasks.first { it.key == "TG-401" }
        assertEquals(3.0, t.pesoTipo, 1e-9)
        assertEquals(3, t.testes)
        assertEquals(10.0, t.horas, 1e-9)
        assertEquals(14.5, t.indice, 1e-9)
        assertEquals(false, t.horasDoCampo)
        assertEquals("2026-10-12", t.resolvida)
        assertEquals("Checklist", t.tipo)
    }

    @Test
    fun porPessoaPelaPosse() = rodar { get ->
        val d = esforco(get("gerente.exemplo", "/api/cards/esforco"))
        assertEquals(listOf("ana", "bruno", "carla"), d.pessoas.map { it.pessoa })
        assertEquals(9.321429, d.pessoas.first { it.pessoa == "ana" }.indice, 1e-6)
        assertEquals(5.178571, d.pessoas.first { it.pessoa == "bruno" }.indice, 1e-6)
        assertEquals(3.0, d.pessoas.first { it.pessoa == "carla" }.indice, 1e-9)
        // Quem registrou o worklog não importa: a bruno registrou 4 h e a parte dele segue a posse (0,357143 de 10 h).
        assertEquals(3.571429, d.pessoas.first { it.pessoa == "bruno" }.horas, 1e-6)
    }

    @Test
    fun semWorklogUsaTimespentComAviso() = rodar { get ->
        val d = esforco(get("gerente.exemplo", "/api/cards/esforco"))
        val t = d.tasks.first { it.key == "TG-405" }
        assertEquals(2.0, t.horas, 1e-9)
        assertTrue(t.horasDoCampo)
        assertEquals(3.0, t.indice, 1e-9) // peso 1 (review) + 0 testes + 2 h
        val aviso = d.avisos.single { it.codigo == "HORAS_DO_CAMPO_TIMESPENT" }
        assertEquals(AvisoDto.INFORMATIVO, aviso.gravidade)
        assertEquals("1", aviso.params["n"])
    }

    @Test
    fun versaoNaResposta() = rodar { get ->
        assertEquals("esforço v1", esforco(get("gerente.exemplo", "/api/cards/esforco")).versaoRegras)
    }

    @Test
    fun periodoPorSemanaEPorMes() = rodar { get ->
        val semana = esforco(get("gerente.exemplo", "/api/cards/esforco"))
        assertEquals("semana", semana.granularidade)
        assertEquals(listOf("2026-10-12", "2026-10-26"), semana.periodos.map { it.inicio })
        val mes = esforco(get("gerente.exemplo", "/api/cards/esforco?granularidade=mes"))
        assertEquals("mes", mes.granularidade)
        assertEquals(listOf("2026-10-01"), mes.periodos.map { it.inicio })
        assertEquals(2, mes.periodos.single().tasks)
        assertEquals(17.5, mes.periodos.single().indice, 1e-9)
        assertEquals(12.0, mes.periodos.single().horas, 1e-9)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/esforco?granularidade=ano").status)
    }

    @Test
    fun usuarioVeSoOProprio() = rodar { get ->
        val r = get("ana", "/api/cards/esforco")
        val d = esforco(r)
        assertEquals(listOf("ana"), d.pessoas.map { it.pessoa })
        assertEquals(listOf("TG-401"), d.tasks.map { it.key })
        assertFalse("bruno" in r.corpo || "carla" in r.corpo || "TG-405" in r.corpo, r.corpo)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/esforco?pessoa=bruno").status)
        // O líder do squad Alfa vê ana e bruno (e a TG-401); a carla é de outro squad.
        val lider = esforco(get("lider.alfa", "/api/cards/esforco"))
        assertEquals(listOf("ana", "bruno"), lider.pessoas.map { it.pessoa })
        assertEquals(listOf("TG-401"), lider.tasks.map { it.key })
    }

    @Test
    fun filtrosEPeriodo() = rodar { get ->
        assertEquals(listOf("TG-405"), esforco(get("gerente.exemplo", "/api/cards/esforco?de=2026-10-20")).tasks.map { it.key })
        assertEquals(listOf("TG-405"), esforco(get("gerente.exemplo", "/api/cards/esforco?tipo=${enc("Checklist Review")}")).tasks.map { it.key })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/esforco?de=2026-10-20&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/esforco?ate=+999999999-12-31").status)
    }

    @Test
    fun pessoasAlfabeticasESemRanking() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/esforco")
        assertEquals(esforco(r).pessoas.map { it.pessoa }.sorted(), esforco(r).pessoas.map { it.pessoa })
        for (proibida in listOf("posicao", "ranking", "rank", "maior", "menor", "pior", "melhor")) {
            assertFalse("\"$proibida" in r.corpo.lowercase(), proibida)
        }
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        suspend fun confere(u: String, cartao: String, segmento: String, esperado: Int) {
            val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
            assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
            assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
            assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val d = esforco(get(u, "/api/cards/esforco"))
            confere(u, "EF-01", "tasks", d.tasks.size)
            for (p in d.pessoas) confere(u, "EF-02", "pessoa:${p.pessoa}", p.tasks)
        }
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cartoes/EF-02/tasks?segmento=${enc("pessoa:bruno")}").status)
        val g = gaveta(get("gerente.exemplo", "/api/cartoes/EF-01/tasks?segmento=tasks"))
        assertTrue("14.5" in g.linhas.first { it.key == "TG-401" }.detalhe, g.linhas.first { it.key == "TG-401" }.detalhe)
    }
}
