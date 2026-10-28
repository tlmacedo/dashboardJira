package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.metas.MetasDto
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

/** Rota do card Metas e prazos: universo pelo vencimento, drill do mesmo tamanho e visão por papel. */
class MetasRoutesTest {
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

    private fun metas(r: RespTrb) = Json.decodeFromString<MetasDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val comSemVencimento get() = Trb1.issues() + Trb1.issue(
        "TG-118", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-21T12",
        eventos = Trb1.trilha("TG-118", "Open", "10-02T12" to "In Progress", "10-21T12" to "Closed"),
    )

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar(comSemVencimento) { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = metas(get(u, "/api/cards/metas?$PERIODO"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?$PERIODO&segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            confere("MT-01", "universo", d.universo)
            confere("MT-01", "noPrazo", d.noPrazo)
            confere("MT-01", "noPrazoAjustado", d.noPrazoAjustado)
            confere("MT-02", "noPrazoOriginal", d.noPrazoOriginal)
            confere("MT-03", "antecipadas", d.antecipadas)
            confere("MT-04", "emD1D2", d.emD1D2)
            confere("MT-05", "noDia", d.noDia)
            confere("MT-06", "atrasadas", d.atrasadas)
            confere("MT-07", "semVencimento", d.semVencimento)
            for (p in d.porPeriodo) {
                confere("MT-08", "periodo:${p.chave}", p.universo)
                confere("MT-09", "periodo:${p.chave}:noPrazo", p.noPrazo)
            }
            for (c in d.distribuicao) confere("MT-10", "faixa:${c.chave}", c.n)
            for (l in d.porTipo) confere("MT-11", "tipo:${l.chave}", l.universo)
            for (l in d.porPrioridade) confere("MT-12", "prioridade:${l.chave}", l.universo)
            for (l in d.porSquad) confere("MT-13", "squad:${l.chave}", l.universo)
            for (l in d.porPessoa) confere("MT-14", "pessoa:${l.chave}", l.universo)
        }
        val g = metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO"))
        assertEquals(listOf(4, 3, 4, 1), listOf(g.universo, g.noPrazo, g.noPrazoAjustado, g.semVencimento))
        assertEquals(listOf("TG-111", "TG-112"), gaveta(get("gerente.exemplo", "/api/cartoes/MT-05/tasks?$PERIODO&segmento=noDia")).linhas.map { it.key })
        assertEquals(listOf("TG-113"), gaveta(get("gerente.exemplo", "/api/cartoes/MT-06/tasks?$PERIODO&segmento=atrasadas")).linhas.map { it.key })
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val ana = metas(get("ana", "/api/cards/metas?$PERIODO"))
        assertEquals(2, ana.universo)
        assertEquals(1, ana.noPrazo)
        assertEquals(2, ana.noPrazoAjustado)
        assertEquals(listOf("ana"), ana.porPessoa.map { it.chave })
        assertEquals(listOf("Alfa"), ana.porSquad.map { it.chave })
        val lider = metas(get("lider.alfa", "/api/cards/metas?$PERIODO"))
        assertEquals(listOf("ana", "bruno"), lider.porPessoa.map { it.chave })
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/metas?$PERIODO&pessoa=carla").status)
        val g = get("ana", "/api/cartoes/MT-14/tasks?$PERIODO&segmento=pessoa:ana")
        assertFalse("bruno" in g.corpo || "carla" in g.corpo)
    }

    @Test
    fun granularidadeInvalida400() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=ano").status)
        assertEquals("dia", metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=dia")).granularidade)
        assertEquals("mes", metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=MES")).granularidade)
        assertEquals("semana", metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO")).granularidade, "o padrão é por semana")
        assertEquals(listOf("2026-10" to 4), metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO&granularidade=mes")).porPeriodo.map { it.chave to it.universo })
    }

    @Test
    fun versaoDasRegrasNaResposta() = rodar { get ->
        assertEquals("metas v1, labels v1", metas(get("gerente.exemplo", "/api/cards/metas?$PERIODO")).versaoRegras)
    }

    @Test
    fun periodoPadraoEPeriodoPeloVencimento() = rodar(
        Trb1.issues() + Trb1.issue(
            "TG-152", "Task", "ana", "09-20T12", "Resolved", resolution = "Done", resolvida = "09-28T12", vence = "2026-10-05",
            eventos = Trb1.trilha("TG-152", "Open", "09-21T12" to "In Progress", "09-28T12" to "Resolved"),
        ),
    ) { get ->
        // 90 dias de Manaus até 30/10 pegam todos os vencimentos do cenário (a TG-152 resolvida em setembro vence em 05/10).
        val padrao = metas(get("gerente.exemplo", "/api/cards/metas"))
        assertEquals(5, padrao.universo)
        // Só o dia 05/10: a TG-152, resolvida em setembro, entra pelo vencimento.
        assertEquals(1, metas(get("gerente.exemplo", "/api/cards/metas?de=2026-10-05&ate=2026-10-05")).universo)
        assertEquals(0, metas(get("gerente.exemplo", "/api/cards/metas?de=2026-10-06&ate=2026-10-08")).universo)
        assertTrue(gaveta(get("gerente.exemplo", "/api/cartoes/MT-01/tasks?$PERIODO&segmento=universo")).linhas.any { it.key == "TG-152" })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/metas?de=2024-01-01&ate=2026-10-30").status)
    }

    @Test
    fun statusNaoMapeado200() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica", resolvida = "10-21T12", vence = "2026-10-22")) { get ->
        val r = get("gerente.exemplo", "/api/cards/metas?$PERIODO")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, metas(r).avisos.single().codigo)
        assertEquals(0, metas(r).universo)
    }
}
