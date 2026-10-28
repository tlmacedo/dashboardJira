package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.riscos.RiscosDto
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

/** Rota do card Riscos sobre o TRB-1: contagens, drill do mesmo tamanho, visão por papel, versão das regras. */
class RiscosRoutesTest {
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

    private fun riscos(r: RespTrb) = Json.decodeFromString<RiscosDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun contagens() = rodar { get ->
        val d = riscos(get("gerente.exemplo", "/api/cards/riscos"))
        assertEquals(listOf(2, 1, 1, 3), listOf(d.alto, d.medio, d.baixo, d.emRisco))
        // A lista só leva alto e médio, do maior para o menor ponto; cada linha tem os pontos por fator.
        assertEquals(listOf("TG-101", "TG-103", "TG-102"), d.linhas.map { it.key })
        val l101 = d.linhas.first()
        assertEquals(85, l101.pontos)
        assertEquals("IDADE_ACIMA_P85", l101.fatorPrincipal)
        assertEquals(85, l101.fatores.sumOf { it.pontos })
        assertEquals(5, l101.fatores.size)
    }

    @Test
    fun drillNivel() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = riscos(get(u, "/api/cards/riscos"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("RI-01", "nivel:alto", d.alto)
            confere("RI-01", "nivel:medio", d.medio)
            confere("RI-02", "emRisco", d.emRisco)
            confere("RI-02", "nivel:baixo", d.baixo)
            for (p in d.sinaisPorPessoa) {
                confere("RI-03", "pessoa:${p.pessoa}:emRisco", p.issuesEmRisco)
                confere("RI-03", "pessoa:${p.pessoa}:abertas", p.abertas)
            }
        }
        val alto = gaveta(get("gerente.exemplo", "/api/cartoes/RI-01/tasks?segmento=nivel:alto"))
        assertEquals(listOf("TG-101", "TG-103"), alto.linhas.map { it.key })
        assertTrue("85 pontos (alto)" in alto.linhas.first().detalhe, alto.linhas.first().detalhe)
    }

    @Test
    fun versaoDasRegrasNaResposta() = rodar { get ->
        val d = riscos(get("gerente.exemplo", "/api/cards/riscos"))
        assertEquals("risco v1", d.versaoRegras)
        assertEquals(60, d.limiteAlto)
        assertEquals(30, d.limiteMedio)
    }

    @Test
    fun sinaisPorPessoaAlfabeticosSemRanking() = rodar { get ->
        val d = riscos(get("gerente.exemplo", "/api/cards/riscos"))
        assertEquals(listOf("ana", "bruno", "carla"), d.sinaisPorPessoa.map { it.pessoa })
        // Proporção: em risco sobre abertas de cada pessoa; sem responsável (TG-104) não entra.
        assertTrue(d.sinaisPorPessoa.all { it.issuesEmRisco == 1 && it.abertas == 1 })
        // O squad leader só vê o próprio squad.
        assertEquals(listOf("ana", "bruno"), riscos(get("lider.alfa", "/api/cards/riscos")).sinaisPorPessoa.map { it.pessoa })
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val d = riscos(get("ana", "/api/cards/riscos"))
        assertEquals(listOf("TG-101"), d.linhas.map { it.key })
        assertEquals(listOf("ana"), d.sinaisPorPessoa.map { it.pessoa })
        // O histórico dela é só dela (n = 2 por tipo): o aviso de amostra pequena aparece.
        assertTrue(d.avisos.any { it.codigo == AvisoDto.AMOSTRA_PEQUENA })
        // A TG-102 e a TG-103 (dos colegas) não vazam na gaveta nem no corpo.
        val g = get("ana", "/api/cartoes/RI-02/tasks?segmento=emRisco")
        assertTrue("TG-102" !in g.corpo && "TG-103" !in g.corpo && "bruno" !in g.corpo, g.corpo)
    }

    @Test
    fun semCamposDeTrabalhoAvisaESemEstimativaNaoPune() = rodar(Trb1.issues(comPrioridade = false)) { get ->
        val d = riscos(get("gerente.exemplo", "/api/cards/riscos"))
        assertTrue(d.avisos.any { it.codigo == AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS })
        // Sem o fator "sem estimativa" a TG-101 perde 5 pontos.
        assertEquals(80, d.linhas.first { it.key == "TG-101" }.pontos)
    }

    @Test
    fun statusNaoMapeado200() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", "/api/cards/riscos")
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = riscos(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, riscos(r).alto)
    }

    @Test
    fun filtrosInvalidosEPapelSemPermissao() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/riscos?unit=hora").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/riscos?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/riscos").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/RI-01/tasks?segmento=ruim").status)
    }
}
