package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.fechadas.FechadasDto
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

/** Rota do card Fechadas: período, visão por papel, drill do mesmo tamanho e avisos. */
class FechadasRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Trb1.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun fechadas(r: RespTrb) = Json.decodeFromString<FechadasDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = fechadas(get(u, "/api/cards/fechadas?$PERIODO"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?$PERIODO&segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("FE-01", "concluidas", d.concluidas)
            confere("FE-12", "concluidas", d.concluidas)
            confere("FE-02", "comPontos", d.comPontos)
            confere("FE-03", "ciclo", d.ciclo?.n ?: 0)
            confere("FE-04", "lead", d.lead?.n ?: 0)
            confere("FE-05", "noPrazo", d.noPrazo)
            confere("FE-05", "noPrazoAjustado", d.noPrazoAjustado)
            confere("FE-05", "comVencimento", d.comVencimento)
            confere("FE-06", "canceladas", d.canceladas)
            confere("FE-06", "semResolution", d.semResolution)
            confere("FE-08", "dispersao", d.dispersao?.pontos?.size ?: 0)
            for (s in d.porSemana) confere("FE-07", "semana:${s.semana}", s.n)
            for (t in d.porTipo) confere("FE-09", "tipo:${t.tipo}", t.n)
            for (p in d.porProjeto) confere("FE-10", "projeto:${p.chave}", p.n)
            for (p in d.porPessoa) {
                confere("FE-11", "pessoa:${p.pessoa}:finalizou", p.finalizou)
                confere("FE-11", "pessoa:${p.pessoa}:participou", p.participou)
            }
        }
        // Os números de referência do cenário.
        val g = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO"))
        assertEquals(listOf(4, 1, 1), listOf(g.concluidas, g.canceladas, g.semResolution))
        assertEquals(1, gaveta(get("gerente.exemplo", "/api/cartoes/FE-07/tasks?$PERIODO&segmento=semana:2026-10-05")).total)
        assertEquals(2, gaveta(get("gerente.exemplo", "/api/cartoes/FE-07/tasks?$PERIODO&segmento=semana:2026-10-12")).total)
    }

    @Test
    fun squadLeaderNaoVePessoaDeOutroSquad() = rodar { get ->
        val d = fechadas(get("lider.alfa", "/api/cards/fechadas?$PERIODO"))
        assertEquals(listOf("ana", "bruno"), d.porPessoa.map { it.pessoa })
        assertFalse(d.linhas.any { it.key == "TG-114" }, "a TG-114 é da carla, do squad Beta")
        assertEquals(3, d.concluidas)
        val g = get("lider.alfa", "/api/cartoes/FE-01/tasks?$PERIODO&segmento=concluidas")
        assertFalse(g.corpo.contains("carla"))
        assertFalse(g.corpo.contains("TG-114"))
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/fechadas?$PERIODO&pessoa=carla").status)
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val d = fechadas(get("ana", "/api/cards/fechadas?$PERIODO"))
        assertEquals(2, d.concluidas)
        assertEquals(listOf("ana"), d.porPessoa.map { it.pessoa })
        assertEquals(2, d.comVencimento)
        // O percentil dela é só dela (n = 2, com aviso de amostra pequena).
        assertEquals(2, d.porTipo.single().n)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.AMOSTRA_PEQUENA && it.params["n"] == "2" })
    }

    @Test
    fun periodoPadraoNoventaDias() = rodar(
        Trb1.issues() + listOf(
            Trb1.issue("TG-160", "Checklist", "ana", "07-20T12", "Closed", resolution = "Done", resolvida = "08-01T12",
                eventos = Trb1.trilha("TG-160", "Open", "07-25T12" to "In Progress", "08-01T12" to "Closed")),
            Trb1.issue("TG-161", "Checklist", "ana", "07-20T12", "Closed", resolution = "Done", resolvida = "08-03T12",
                eventos = Trb1.trilha("TG-161", "Open", "07-25T12" to "In Progress", "08-03T12" to "Closed")),
        ),
    ) { get ->
        // 90 dias de Manaus terminando em 30/10: de 02/08 a 30/10. A TG-160 (01/08) fica de fora; a TG-161 (03/08) entra.
        val padrao = fechadas(get("gerente.exemplo", "/api/cards/fechadas"))
        assertEquals(5, padrao.concluidas)
        assertTrue(padrao.linhas.any { it.key == "TG-161" } && padrao.linhas.none { it.key == "TG-160" })
        assertEquals(6, fechadas(get("gerente.exemplo", "/api/cards/fechadas?de=2026-08-01&ate=2026-10-30")).concluidas)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/fechadas?de=2024-01-01&ate=2026-10-30").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/fechadas?de=2026-10-30&ate=2026-10-01").status)
    }

    @Test
    fun resolucaoNaFronteiraDeManausCaiNoMesmoDia() = rodar(
        Trb1.issues() + Trb1.issue(
            "TG-120", "Checklist", "ana", "10-10T12", "Closed", resolution = "Done", resolvida = "10-16T03", vence = "2026-10-15",
            eventos = Trb1.trilha("TG-120", "Open", "10-11T12" to "In Progress", "10-16T03" to "Closed"),
        ),
    ) { get ->
        // 16/10 03:00Z = 15/10 23:00 em Manaus: no dia do vencimento, portanto no prazo.
        val d = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO"))
        assertEquals("2026-10-15", d.linhas.first { it.key == "TG-120" }.resolvida)
        assertEquals("noPrazo", d.linhas.first { it.key == "TG-120" }.prazo)
        assertEquals(4, d.noPrazo)
    }

    @Test
    fun statusNaoMapeado200() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica", resolvida = "10-21T12")) { get ->
        val r = get("gerente.exemplo", "/api/cards/fechadas?$PERIODO")
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = fechadas(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, fechadas(r).concluidas)
    }

    @Test
    fun statusNaoMapeadoEmCandidataPorVencimentoViraAvisoBloqueante() = rodar(
        Trb1.issues() + Trb1.issue("TG-198", "Checklist", "ana", "09-20T12", "Revisao Exotica", resolvida = "09-28T12", vence = "2026-10-05"),
    ) { get ->
        val r = get("gerente.exemplo", "/api/cards/fechadas?$PERIODO")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, fechadas(r).avisos.single().codigo)
        assertEquals(AvisoDto.BLOQUEANTE, fechadas(r).avisos.single().gravidade)
    }

    @Test
    fun filtrosInvalidosEPapelSemPermissao()= rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/fechadas?unit=hora").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/fechadas?squad=Nenhum").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/fechadas?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/fechadas").status)
        val corrido = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO&unit=corrido"))
        assertEquals(6.5, corrido.ciclo!!.p50, 1e-9)
    }

    private fun tg801() = Trb1.issue(
        "TG-801", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-09T12", vence = "2026-10-09",
        estimativaS = 4 * 3600L, gastoS = 6 * 3600L, eventos = Trb1.trilha("TG-801", "Open", "10-05T12" to "In Progress", "10-09T12" to "Closed"),
    )

    @Test
    fun tempoAtivoPorPessoa() = rodar(listOf(tg801())) { get ->
        val d = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO"))
        // ana ficou em status ativo de segunda 05/10 a sexta 09/10: 4 dias úteis.
        assertEquals(4.0, d.porPessoa.single { it.pessoa == "ana" }.tempoAtivoDias!!, 1e-9)
        // Em dias corridos o mesmo trecho também são 4 dias (segunda a sexta, sem fim de semana no meio).
        val corridos = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO&unit=corrido"))
        assertEquals(4.0, corridos.porPessoa.single { it.pessoa == "ana" }.tempoAtivoDias!!, 1e-9)
    }

    @Test
    fun tempoAtivoSoContaOTrechoDaPessoa() = rodar(
        listOf(
            Trb1.issue(
                "TG-802", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-09T12",
                eventos = Trb1.trilha("TG-802", "Open", "10-05T12" to "In Progress", "10-09T12" to "Closed") +
                    Trb1.ev("TG-802", "assignee", "ana", "bruno", "10-07T12"),
            ),
        ),
    ) { get ->
        val d = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO"))
        // ana teve a task de 05 a 07 (2 dias úteis ativos) e bruno de 07 a 09 (2).
        assertEquals(2.0, d.porPessoa.single { it.pessoa == "ana" }.tempoAtivoDias!!, 1e-9)
        assertEquals(2.0, d.porPessoa.single { it.pessoa == "bruno" }.tempoAtivoDias!!, 1e-9)
    }

    @Test
    fun tabelaDeConcluidasComRealSobreEstimado() = rodar(listOf(tg801(), Trb1.issue(
        "TG-803", "Checklist", "ana", "10-01T12", "Closed", resolution = "Done", resolvida = "10-12T12",
        gastoS = 3600L, eventos = Trb1.trilha("TG-803", "Open", "10-02T12" to "In Progress", "10-12T12" to "Closed"),
    ))) { get ->
        val linhas = fechadas(get("gerente.exemplo", "/api/cards/fechadas?$PERIODO")).linhas.associateBy { it.key }
        assertEquals(1.5, linhas.getValue("TG-801").realSobreEstimado!!, 1e-9)
        // Sem estimativa não há razão (nunca divisão por zero nem "infinito").
        assertEquals(null, linhas.getValue("TG-803").realSobreEstimado)
    }
}
