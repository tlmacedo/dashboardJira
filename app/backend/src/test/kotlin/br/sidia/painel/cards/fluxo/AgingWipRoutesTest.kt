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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rota do Envelhecimento por etapa sobre o TRB-1, mais três concluídas da ana (TG-121 a TG-123) que passaram 2, 3 e 7 dias
 * em In Progress, para a etapa ter amostra mínima. Dias corridos, para as contas fecharem sem feriado.
 */
class AgingWipRoutesTest {
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

    private fun concluida(chave: String, dias: Int): Trb1.IssueDeTeste {
        val entra = "09-02T12"
        val sai = "09-%02dT12".format(2 + dias)
        return Trb1.issue(
            chave, "Checklist", "ana", "09-01T12", "Closed", resolution = "Done", resolvida = sai,
            eventos = Trb1.trilha(chave, "Open", entra to "In Progress", sai to "Closed"),
        )
    }

    private fun comHistorico() = Trb1.issues() + listOf(concluida("TG-121", 2), concluida("TG-122", 3), concluida("TG-123", 7))

    private fun rodar(issues: List<Trb1.IssueDeTeste> = comHistorico(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun aging(r: RespTrb) = Json.decodeFromString<AgingWipDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")
    private val card = "/api/cards/aging-wip?unit=corrido"

    @Test
    fun numerosDoTrb1() = rodar { get ->
        val d = aging(get("gerente.exemplo", card))
        // Ordem do fluxo (Open, In Progress, Submitted, Resolved, Closed) e On Hold, que não consta na ordem, por último.
        assertEquals(listOf("In Progress", "Submitted", "On Hold"), d.etapas.map { it.status })
        assertEquals(listOf("ACTIVE", "WAIT", "BLOCKED"), d.etapas.map { it.categoria })
        val ip = d.etapas.first()
        assertEquals(listOf("TG-101"), ip.pontos.map { it.key })
        assertEquals(29.0, ip.pontos.single().idade) // desde 01/10 12:00 até 30/10 12:00
        // As 7 concluídas que passaram por In Progress: 1, 2, 3, 4, 7, 9 e 13 dias corridos (TG-116 e TG-115 não são entrega).
        val faixa = assertNotNull(ip.faixa)
        assertEquals(4.0, faixa.p50, 1e-9)
        assertEquals(9.4, faixa.p85, 1e-9)
        assertEquals(7, faixa.n)
        assertTrue(ip.pontos.single().acima)
        // Bloqueada desde 26/10 12:00 = 4,0 dias; sem histórico em On Hold, então sem faixa e fora do "acima do normal".
        val bloqueada = d.etapas.last()
        assertEquals(listOf(1, 0, 0), listOf(bloqueada.total, bloqueada.acima, bloqueada.historicoN))
        assertNull(bloqueada.faixa)
        assertEquals(listOf(2, 1), listOf(d.emAndamento, d.acimaDoNormal))
        assertEquals("corrido", d.unit)
        val aviso = d.avisos.single { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(mapOf("tipo" to "On Hold", "n" to "0", "minimo" to "5"), aviso.params)
    }

    @Test
    fun numeroAcimaDoNormalIgualAoDrill() = rodar { get ->
        for (u in usuarios) {
            val d = aging(get(u, card))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}&unit=corrido")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            for (e in d.etapas) {
                confere("AW-01", "etapa:${e.status}", e.total)
                confere("AW-01", "acima:${e.status}", e.acima)
                assertEquals(e.total, e.pontos.size, "$u ${e.status}: um ponto por task")
            }
            confere("AW-02", "emAndamento", d.emAndamento)
            confere("AW-02", "acimaDoNormal", d.acimaDoNormal)
            assertEquals(d.etapas.sumOf { it.acima }, d.acimaDoNormal, u)
            // Cada cartão só abre os seus segmentos.
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/AW-01/tasks?segmento=acimaDoNormal").status)
            assertEquals(HttpStatusCode.BadRequest, get(u, "/api/cartoes/AW-02/tasks?segmento=${enc("etapa:In Progress")}").status)
        }
    }

    @Test
    fun gavetaDizPorQueFoiParaAcimaDoNormal() = rodar { get ->
        val g = gaveta(get("gerente.exemplo", "/api/cartoes/AW-02/tasks?segmento=acimaDoNormal&unit=corrido"))
        assertEquals(listOf("TG-101"), g.linhas.map { it.key })
        assertTrue("acima do normal" in g.linhas.single().detalhe && "29,0".replace(',', '.') in g.linhas.single().detalhe, g.linhas.single().detalhe)
        val semFaixa = gaveta(get("gerente.exemplo", "/api/cartoes/AW-01/tasks?segmento=${enc("etapa:On Hold")}&unit=corrido"))
        assertTrue("sem faixa" in semFaixa.linhas.single().detalhe, semFaixa.linhas.single().detalhe)
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val ana = aging(get("ana", card))
        // A TG-103 (On Hold) é da carla: a etapa nem aparece para a ana (não consta na ordem do fluxo e ela não tem task lá).
        assertEquals(listOf("In Progress", "Submitted"), ana.etapas.map { it.status })
        assertEquals(listOf(1, 0), ana.etapas.map { it.total })
        assertEquals(listOf("TG-101"), ana.etapas.first().pontos.map { it.key })
        // O histórico dela (TG-111, TG-113, TG-121 a TG-123) dá 5 concluídas: justo a amostra mínima.
        assertEquals(5, assertNotNull(ana.etapas.first().faixa).n)
        val carla = aging(get("carla", card))
        assertEquals(listOf(0, 0, 1), carla.etapas.map { it.total })
        val bruno = aging(get("bruno", card))
        assertEquals(0, bruno.emAndamento)
        // Pedir a etapa de outra pessoa não amplia a visão.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/aging-wip?pessoa=carla").status)
        val g = get("ana", "/api/cartoes/AW-02/tasks?segmento=emAndamento")
        assertTrue("TG-103" !in g.corpo && "carla" !in g.corpo, g.corpo)
    }

    @Test
    fun pontoNaoTrazNomeDePessoa() = rodar { get ->
        val r = get("gerente.exemplo", card)
        for (p in listOf("ana", "bruno", "carla", "lider.alfa")) assertFalse("\"$p\"" in r.corpo, "$p na resposta")
        assertFalse(Regex("\"(pessoa|responsavel|assignee)").containsMatchIn(r.corpo), r.corpo)
    }

    @Test
    fun unidadePadraoEhDiasUteis() = rodar { get ->
        val util = aging(get("gerente.exemplo", "/api/cards/aging-wip"))
        val corrido = aging(get("gerente.exemplo", card))
        assertEquals("util", util.unit)
        assertTrue(util.etapas.first().pontos.single().idade < corrido.etapas.first().pontos.single().idade)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/aging-wip?unit=hora").status)
    }

    @Test
    fun statusNaoMapeadoEPapel() = rodar(comHistorico() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", card)
        assertEquals(HttpStatusCode.OK, r.status)
        val aviso = aging(r).avisos.single()
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
        assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
        assertEquals(0, aging(r).emAndamento)
        assertEquals(HttpStatusCode.Unauthorized, get(null, card).status)
    }
}
