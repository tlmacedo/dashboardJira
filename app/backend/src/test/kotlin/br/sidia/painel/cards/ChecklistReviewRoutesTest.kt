package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.checklist.ChecklistReviewDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.checklist.Cr1Banco
import br.sidia.painel.contarAccessLog
import br.sidia.painel.http.ErroDto
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checklist × Review pelo HTTP no cenário CR-1: visão por papel, filtros no pai, número igual ao drill e versão das regras. */
class ChecklistReviewRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Cr1Banco.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = Cr1Banco.contexto(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun card(r: RespTrb) = Json.decodeFromString<ChecklistReviewDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val rota = "/api/cards/checklist-review"
    private val todosOsPapeis = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        val d = card(get("gerente.exemplo", rota))
        assertEquals(4, d.resumo.pares)
        assertEquals(1, d.resumo.semReview)
        assertEquals(1, d.resumo.porAlerta.getValue("MESMA_PESSOA"))
        assertEquals(3, d.resumo.comAlerta)
        assertEquals(1, d.resumo.atrasosEmCadeia)
        assertEquals(listOf("TG-401", "TG-403", "TG-405", "TG-407"), d.pares.map { it.pai })
        for (u in todosOsPapeis) {
            val dto = card(get(u, rota))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("CR-01", "pares", dto.resumo.pares)
            confere("CR-02", "semReview", dto.resumo.semReview)
            confere("CR-03", "concluidas", dto.resumo.concluidas)
            confere("CR-04", "concluidas", dto.resumo.concluidas)
            confere("CR-05", "pares", dto.resumo.pares)
            confere("CR-06", "alerta:REVIEW_VENCE_DEPOIS", dto.resumo.porAlerta.getValue("REVIEW_VENCE_DEPOIS"))
            confere("CR-07", "alerta:MESMA_PESSOA", dto.resumo.porAlerta.getValue("MESMA_PESSOA"))
            confere("CR-08", "comAlerta", dto.resumo.comAlerta)
            for ((alerta, n) in dto.resumo.porAlerta) confere("CR-08", "alerta:$alerta", n)
            confere("CR-12", "cadeia", dto.resumo.atrasosEmCadeia)
            for (f in dto.resumo.duracaoXReabertura) confere("CR-11", "faixa:${f.faixa}", f.n)
            for (l in dto.resumo.labels) confere("CR-13", "label:${l.label}", l.n)
            for (x in dto.resumo.diferencasDeLabels) confere("CR-14", "dif:${x.codigo}", x.pares)
        }
        // Cada cartão só abre os seus números; o de aprendizados (texto) não tem gaveta.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CR-06/tasks?segmento=pares").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CR-13/tasks?segmento=${enc("label:inexistente")}").status)
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/CR-10/tasks?segmento=x").status)
    }

    @Test
    fun filtroAplicadoAoPai() = rodar { get ->
        // O filtro vale para o checklist: `pessoa=bruno` fica com o P3 (pai do bruno), não com o P1 (só a review é dele).
        assertEquals(listOf("TG-405"), card(get("gerente.exemplo", "$rota?pessoa=bruno")).pares.map { it.pai })
        assertEquals(listOf("TG-407"), card(get("gerente.exemplo", "$rota?squad=Beta")).pares.map { it.pai })
        assertEquals(listOf("TG-401", "TG-403", "TG-405", "TG-407"), card(get("gerente.exemplo", "$rota?tipo=Checklist")).pares.map { it.pai })
        assertEquals(0, card(get("gerente.exemplo", "$rota?tipo=${enc("Checklist TG")}")).resumo.pares)
        // A lista de checklists sem review também segue o filtro do pai.
        assertEquals(0, card(get("gerente.exemplo", "$rota?pessoa=bruno")).resumo.semReview)
        assertEquals(1, card(get("gerente.exemplo", "$rota?pessoa=ana")).resumo.semReview)
        // O período vale pela resolução do pai (o aberto entra sempre): o TG-401 foi resolvido em 09/10.
        assertEquals(listOf("TG-403", "TG-405", "TG-407"), card(get("gerente.exemplo", "$rota?de=2026-10-12&ate=2026-10-30")).pares.map { it.pai })
    }

    @Test
    fun usuarioVeParDoQualParticipa() = rodar { get ->
        // carla: review do P3 (TG-406) e pai do P4 (TG-407).
        val carla = card(get("carla", rota))
        assertEquals(listOf("TG-405", "TG-407"), carla.pares.map { it.pai })
        assertEquals(0, carla.resumo.semReview)
        // ana: pai dos P1, P2 e do TG-409 sem review; a review do P1 é do bruno e vem sem o nome dele.
        val ana = card(get("ana", rota))
        assertEquals(listOf("TG-401", "TG-403"), ana.pares.map { it.pai })
        assertEquals(1, ana.resumo.semReview)
        assertNull(ana.pares.first { it.pai == "TG-401" }.responsavelReview)
        assertEquals("ana", ana.pares.first { it.pai == "TG-401" }.responsavelPai)
        // bruno: review dos P1 e P4 e pai do P3.
        assertEquals(listOf("TG-401", "TG-405", "TG-407"), card(get("bruno", rota)).pares.map { it.pai })
        // O líder do Alfa vê os pares de ana e bruno (P1, P2, P3 e P4 pela review do bruno).
        assertEquals(listOf("TG-401", "TG-403", "TG-405", "TG-407"), card(get("lider.alfa", rota)).pares.map { it.pai })
        // Os agregados são só do visível: a carla não conta os pares da ana.
        assertEquals(2, carla.resumo.pares)
        val corpo = get("carla", rota).corpo
        assertTrue("TG-401" !in corpo && "TG-403" !in corpo && "TG-409" !in corpo, "par de outra pessoa vazou")
    }

    @Test
    fun parametrosNaoAmpliamAVisao() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "$rota?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Forbidden, get("carla", "$rota?pessoa=ana").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?squad=Inexistente").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?de=lixo").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?de=+999999999-12-31").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, rota).status)
    }

    @Test
    fun versaoNaResposta() = rodar { get ->
        assertEquals("checklist-review v1", card(get("gerente.exemplo", rota)).versaoRegras)
    }

    @Test
    fun consultaEhRegistradaInclusiveNegadaESemChaveDeIssue() = rodar { get ->
        val antes = ctx!!.contarAccessLog()
        get("gerente.exemplo", rota)
        get("ana", "$rota?pessoa=bruno")
        get("gerente.exemplo", "$rota?de=lixo")
        get("gerente.exemplo", "/api/cartoes/CR-01/tasks?segmento=pares")
        assertEquals(antes + 4, ctx!!.contarAccessLog())
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2)) }
            }
        }
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, "chave de issue no access_log: $linhas")
    }

    @Test
    fun avisosDeVinculoEDeAmostra() = rodar { get ->
        val d = card(get("gerente.exemplo", rota))
        // Quatro pares < 5 (amostra mínima): aviso informativo, números mantidos.
        val amostra = d.avisos.single { it.codigo == "PARES_EM_AMOSTRA_PEQUENA" }
        assertEquals(AvisoDto.INFORMATIVO, amostra.gravidade)
        assertEquals("4", amostra.params["n"])
        assertEquals("5", amostra.params["minimo"])
        assertEquals(emptyList(), d.resumo.aprendizados)
    }

    @Test
    fun semVinculosNaBaseAvisaEFicaVazio() = rodar(issues = listOf(Trb1.issue("TG-901", "Checklist", "ana", "10-01T12", "Open"))) { get ->
        val d = card(get("gerente.exemplo", rota))
        assertEquals(0, d.resumo.pares)
        assertEquals(1, d.resumo.semReview)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.VINCULOS_INDISPONIVEIS }, d.avisos.toString())
    }

    @Test
    fun statusSemMapeamentoDaAvisoBloqueanteEZeraOsNumeros() = rodar(
        issues = Cr1Banco.issues() + Trb1.issue("TG-902", "Checklist", "ana", "10-01T12", "Status Inventado"),
    ) { get ->
        val r = get("gerente.exemplo", rota)
        assertEquals(HttpStatusCode.OK, r.status)
        val d = card(r)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.STATUS_SEM_MAPEAMENTO && it.gravidade == AvisoDto.BLOQUEANTE }, d.avisos.toString())
        assertEquals(0, d.resumo.pares)
        assertEquals(0, d.pares.size)
    }

    @Test
    fun erroDeSegmentoNaoTrazOValorRecebido() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cartoes/CR-13/tasks?segmento=${enc("label:valor-estranho-123")}")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("SEGMENTO_INVALIDO", Json.decodeFromString<ErroDto>(r.corpo).codigo)
        assertTrue("valor-estranho-123" !in r.corpo)
    }

}
