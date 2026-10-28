package br.sidia.painel.cards.fluxo

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rota dos Bloqueios entre tasks sobre o cenário BQ-1: visão por papel, número igual ao drill e nada escondido que vaze. */
class BloqueiosRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Bq1.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = Bq1.novoContexto(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun bloqueios(r: RespTrb) = Json.decodeFromString<BloqueiosDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")
    private val card = "/api/cards/bloqueios"

    private fun numeros(d: BloqueiosDto) = listOf(d.bloqueadas, d.bloqueadoras, d.bloqueadorasForaDaVisao, d.prazoEmConflito, d.verificarNoJira, d.semCausa)

    @Test
    fun numerosPorPapel() = rodar { get ->
        assertEquals(listOf(3, 3, 0, 1, 1, 2), numeros(bloqueios(get("gerente.exemplo", card))))
        assertEquals(listOf(3, 3, 0, 1, 1, 2), numeros(bloqueios(get("coord.um", card))))
        // O líder do Alfa vê ana e bruno: a TG-802 (bruno) está ao alcance; a TG-812 (carla, Beta) não.
        assertEquals(listOf(2, 1, 1, 1, 1, 2), numeros(bloqueios(get("lider.alfa", card))))
        // A ana só vê as próprias: as duas bloqueadoras (TG-802 e TG-812) são de outras pessoas.
        assertEquals(listOf(2, 0, 2, 1, 1, 1), numeros(bloqueios(get("ana", card))))
        // A carla bloqueia-se a si mesma: a TG-804 é dela.
        assertEquals(listOf(1, 1, 0, 0, 0, 0), numeros(bloqueios(get("carla", card))))
    }

    @Test
    fun numeroIgualAoDrill() = rodar { get ->
        for (u in usuarios) {
            val d = bloqueios(get(u, card))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("BQ-01", "bloqueadas", d.bloqueadas)
            confere("BQ-02", "bloqueadoras", d.bloqueadoras)
            confere("BQ-03", "prazoEmConflito", d.prazoEmConflito)
            confere("BQ-04", "semCausa", d.semCausa)
            confere("BQ-05", "verificarNoJira", d.verificarNoJira)
            // Cada cartão só abre o seu número.
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/BQ-01/tasks?segmento=semCausa").status)
        }
    }

    @Test
    fun bloqueadoraForaDaVisaoNaoVazaNemNoDrill() = rodar { get ->
        val r = get("ana", card)
        for (escondido in listOf("TG-802", "TG-812", "bruno", "carla")) assertFalse(escondido in r.corpo, "$escondido na resposta")
        val linha = bloqueios(r).linhas.single { it.bloqueada == "TG-801" }
        assertEquals(EstadoDaBloqueadora.OUTRO_SQUAD, linha.bloqueadora.estado)
        assertEquals(null, linha.bloqueadora.key)
        for (seg in listOf("bloqueadas", "prazoEmConflito")) {
            val g = get("ana", "/api/cartoes/${if (seg == "bloqueadas") "BQ-01" else "BQ-03"}/tasks?segmento=$seg")
            for (escondido in listOf("TG-802", "TG-812", "bruno", "carla")) assertFalse(escondido in g.corpo, "$escondido no drill $seg")
        }
        val detalhe = gaveta(get("ana", "/api/cartoes/BQ-01/tasks?segmento=bloqueadas")).linhas.single { it.key == "TG-801" }.detalhe
        assertTrue("task de outro squad" in detalhe && "vence depois" in detalhe, detalhe)
        // O líder, que vê o bruno, enxerga a TG-802 pelo nome da chave (e só ela).
        val lider = get("lider.alfa", card)
        assertTrue("TG-802" in lider.corpo && "TG-812" !in lider.corpo, lider.corpo)
        assertEquals(listOf("TG-802"), gaveta(get("lider.alfa", "/api/cartoes/BQ-02/tasks?segmento=bloqueadoras")).linhas.map { it.key })
    }

    @Test
    fun linhaDaPontaForaDaBaseAparecePorChaveEAvisa() = rodar { get ->
        val d = bloqueios(get("gerente.exemplo", card))
        val linha = d.linhas.single { it.bloqueada == "TG-809" }
        assertEquals(EstadoDaBloqueadora.FORA_DA_BASE, linha.bloqueadora.estado)
        assertEquals("TG-9999", linha.bloqueadora.key)
        assertEquals(AvisosDoFluxo.BLOQUEIO_FORA_DA_BASE, d.avisos.single().codigo)
        val g = gaveta(get("gerente.exemplo", "/api/cartoes/BQ-05/tasks?segmento=verificarNoJira"))
        assertTrue("fora da base: verificar no Jira" in g.linhas.single().detalhe, g.linhas.single().detalhe)
    }

    @Test
    fun semCausaListaStatusBloqueadoEFlagSemVinculoAberto() = rodar { get ->
        val g = gaveta(get("gerente.exemplo", "/api/cartoes/BQ-04/tasks?segmento=semCausa"))
        assertEquals(listOf("TG-805", "TG-810"), g.linhas.map { it.key })
        assertTrue("status bloqueado" in g.linhas[0].detalhe && "flag de impedimento" in g.linhas[1].detalhe)
        // Um usuário só vê a sua: o bruno vê a TG-810; a ana, a TG-805.
        assertEquals(listOf("TG-810"), gaveta(get("bruno", "/api/cartoes/BQ-04/tasks?segmento=semCausa")).linhas.map { it.key })
        assertEquals(listOf("TG-805"), gaveta(get("ana", "/api/cartoes/BQ-04/tasks?segmento=semCausa")).linhas.map { it.key })
    }

    @Test
    fun accessLogNaoGravaChaveDeIssue() = rodar { get ->
        get("gerente.exemplo", card)
        get("gerente.exemplo", "/api/cartoes/BQ-02/tasks?segmento=bloqueadoras")
        get("gerente.exemplo", "/api/cartoes/BQ-01/tasks?segmento=ruim")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(3, linhas.size, linhas.toString())
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, linhas.toString())
    }

    @Test
    fun semVinculosNaBaseAvisaEFiltrosEPapel() = rodar(listOf(Trb1.issue("TG-901", "Checklist", "ana", "10-20T12", "Open"))) { get ->
        val d = bloqueios(get("gerente.exemplo", card))
        assertEquals(0, d.bloqueadas)
        assertEquals(AvisosDoFluxo.SEM_VINCULOS_NA_BASE, d.avisos.single().codigo)
        assertEquals("Blocks", d.avisos.single().params["tipo"])
        assertEquals(HttpStatusCode.Forbidden, get("ana", "$card?pessoa=bruno").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card?unit=hora").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, card).status)
    }

    @Test
    fun statusNaoMapeadoVira200ComAvisoBloqueante() = rodar(Bq1.issues() + Trb1.issue("TG-899", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", card)
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = bloqueios(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, bloqueios(r).bloqueadas)
    }
}
