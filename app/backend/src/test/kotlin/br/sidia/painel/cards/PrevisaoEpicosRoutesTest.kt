package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.epicos.EpicosDto
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rotas de Previsão e de Épicos: visão por papel, drill do mesmo tamanho, semente do dia e versão das regras. */
class PrevisaoEpicosRoutesTest {
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

    private fun previsao(r: RespTrb) = Json.decodeFromString<PrevisaoDto>(r.corpo)
    private fun epicos(r: RespTrb) = Json.decodeFromString<EpicosDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun drillBacklogMesmoTamanho() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = previsao(get(u, "/api/cards/previsao"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("PV-01", "backlog", d.backlog)
            confere("PV-02", "backlog", d.backlog)
            confere("PV-03", "vazao", d.entregasNaJanela)
            for (s in d.vazaoSemanal) confere("PV-04", "vazao:${s.semana}", s.n)
            for (s in d.chegadaSemanal) confere("PV-04", "chegada:${s.semana}", s.n)
            confere("PV-06", "porIssue", d.porIssue.size)
        }
        // Os números de referência do TRB-1.
        val g = previsao(get("gerente.exemplo", "/api/cards/previsao"))
        assertEquals(listOf(4, 4, 2), listOf(g.backlog, g.entregasNaJanela, g.porIssue.size))
        // O PV-05 é projeção, sem tasks por trás: não tem gaveta.
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/PV-05/tasks?segmento=x").status)
    }

    @Test
    fun previsaoDoUsuarioSoComAsProprias() = rodar { get ->
        val d = previsao(get("ana", "/api/cards/previsao"))
        assertEquals(1, d.backlog) // só a TG-101 dela
        assertEquals(2, d.entregasNaJanela) // TG-111 e TG-113
        // A série dela: entregas nas semanas de 05/10 e 19/10; nada de colegas.
        assertEquals(listOf(1, 0, 1), d.vazaoSemanal.takeLast(3).map { it.n })
        val g = get("ana", "/api/cartoes/PV-03/tasks?segmento=vazao")
        assertTrue("TG-112" !in g.corpo && "TG-114" !in g.corpo && "bruno" !in g.corpo && "carla" !in g.corpo, g.corpo)
        // Squad leader: o squad dele (ana e bruno); a TG-114 é da carla.
        assertEquals(3, previsao(get("lider.alfa", "/api/cards/previsao")).entregasNaJanela)
    }

    @Test
    fun versaoDasRegrasNaResposta() = rodar { get ->
        val d = previsao(get("gerente.exemplo", "/api/cards/previsao"))
        assertEquals("previsao v1", d.versaoRegras)
        assertEquals(16, d.semanasHistorico)
        assertEquals(104, d.limiteSemanas)
        assertEquals(listOf(2, 4, 8), d.quantos.map { it.semanas })
    }

    @Test
    fun mesmoNumeroNoMesmoDiaComOsMesmosFiltros() = rodar { get ->
        val a = previsao(get("gerente.exemplo", "/api/cards/previsao?tipo=Checklist&tipo=Checklist%20Review"))
        val b = previsao(get("gerente.exemplo", "/api/cards/previsao?tipo=Checklist%20Review&tipo=Checklist"))
        assertEquals(a.termina, b.termina)
        assertEquals(a.quantos, b.quantos)
        assertEquals(a.termina, previsao(get("gerente.exemplo", "/api/cards/previsao?tipo=Checklist&tipo=Checklist%20Review")).termina)
    }

    @Test
    fun statusNaoMapeado200() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", "/api/cards/previsao")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, previsao(r).avisos.single().codigo)
        assertEquals(AvisoDto.BLOQUEANTE, previsao(r).avisos.single().gravidade)
        assertEquals(0, previsao(r).backlog)
    }

    @Test
    fun filtrosInvalidosEPapelSemPermissao() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/previsao?unit=hora").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/previsao?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/previsao").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/PV-01/tasks?segmento=ruim").status)
    }

    private fun comEpico(): List<Trb1.IssueDeTeste> = listOf(
        Trb1.issue("TG-701", "Checklist", "ana", "10-01T12", "In Progress", epico = "TG-900", vence = "2026-10-20"),
        Trb1.issue("TG-702", "Checklist", "bruno", "10-01T12", "Open", epico = "TG-900"),
        Trb1.issue(
            "TG-703", "Checklist", "carla", "10-01T12", "Closed", resolution = "Done", resolvida = "10-10T12", epico = "TG-900",
            eventos = Trb1.trilha("TG-703", "Open", "10-02T12" to "In Progress", "10-10T12" to "Closed"),
        ),
        Trb1.issue("TG-704", "Checklist Review", "ana", "10-01T12", "Open", epico = "TG-900"),
    )

    @Test
    fun epicosDrillMesmoTamanhoEVisao() = rodar(comEpico()) { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = epicos(get(u, "/api/cards/epicos"))
            for (l in d.linhas) {
                for ((seg, n) in listOf("itens" to l.itens, "concluidos" to l.concluidos, "vencidos" to l.vencidos)) {
                    val g = get(u, "/api/cartoes/EP-01/tasks?segmento=${enc("epico:${l.id}:$seg")}")
                    assertEquals(HttpStatusCode.OK, g.status, "$u $seg ${g.corpo}")
                    assertEquals(n, gaveta(g).total, "$u $seg")
                }
            }
        }
        val g = epicos(get("gerente.exemplo", "/api/cards/epicos")).linhas.single()
        assertEquals(listOf("TG-900", 3, 1, 1), listOf(g.epico, g.itens, g.concluidos, g.vencidos)) // o Review fica fora
        assertNull(g.previsaoSemanasP85) // amostra própria pequena
        // O squad leader do Alfa só conta as dos dois do squad (a TG-703 é da carla).
        assertEquals(2, epicos(get("lider.alfa", "/api/cards/epicos")).linhas.single().itens)
        // O usuário vê só o épico das próprias tasks.
        assertEquals(1, epicos(get("ana", "/api/cards/epicos")).linhas.single().itens)
        // Tipo e prioridade são ignorados (como o app anterior).
        assertEquals(3, epicos(get("gerente.exemplo", "/api/cards/epicos?tipo=Task")).linhas.single().itens)
    }

    @Test
    fun segmentoDoEpicoNaoGuardaAChaveNoRegistro() = rodar(comEpico()) { get ->
        val id = epicos(get("gerente.exemplo", "/api/cards/epicos")).linhas.single().id
        get("gerente.exemplo", "/api/cartoes/EP-01/tasks?segmento=${enc("epico:$id:itens")}")
        val c = ctx!!
        val registros = requireNotNull(c.dataSource).connection.use { con ->
            con.createStatement().executeQuery("SELECT route, filters FROM access_log").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertTrue(registros.isNotEmpty() && registros.none { "TG-900" in it }, registros.toString())
    }

    @Test
    fun semCampoEpicoAvisa() = rodar { get ->
        val d = epicos(get("gerente.exemplo", "/api/cards/epicos"))
        assertTrue(d.linhas.isEmpty())
        assertTrue(d.avisos.any { it.codigo == AvisoDto.EPICO_INDISPONIVEL })
    }
}
