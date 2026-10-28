package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.contarAccessLog
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Datas extremas ou malformadas nos cards de pessoas respondem 400 (nunca 500) e a consulta é auditada. */
class DatasExtremasPessoasTest {
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

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = testContextComBanco(
            Settings(dashboardDevMode = false), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        ).also { ctx = it }
        Trb1.carregar(requireNotNull(c.dataSource), Cap1.issuesParaBanco())
        rodarTrb1(c, bloco = bloco)
    }

    private val ruins = listOf("%2B999999999-12-31", "-999999999-01-01", "9999-12-31", "1800-01-01", "2026-13-40", "ontem", "2026-11-0%00")
    private val cards = listOf("carga", "capacidade", "calendario", "plano", "vencimentos", "gargalos", "tendencia")

    @Test
    fun inicioExtremoNoCalendarioEh400() = rodar { get ->
        for (d in ruins) {
            val r = get("gerente.exemplo", "/api/cards/calendario?inicio=$d")
            assertEquals(HttpStatusCode.BadRequest, r.status, d)
            assertTrue("999999999" !in r.corpo && "ontem" !in r.corpo, "o valor não volta na resposta")
        }
    }

    @Test
    fun todosOsCardsDePessoasRecusamDataRuimSem500() = rodar { get ->
        for (card in cards) for (param in listOf("inicio", "de", "ate")) for (d in ruins) {
            val r = get("gerente.exemplo", "/api/cards/$card?$param=$d")
            val esperado = if (param == "inicio" && card != "calendario") HttpStatusCode.OK else HttpStatusCode.BadRequest
            assertEquals(esperado, r.status, "$card $param=$d")
        }
    }

    @Test
    fun erroDeDataEhAuditado() = rodar { get ->
        val antes = requireNotNull(ctx).contarAccessLog()
        get("gerente.exemplo", "/api/cards/calendario?inicio=%2B999999999-12-31")
        get("gerente.exemplo", "/api/cards/plano?de=ontem")
        assertEquals(antes + 2, requireNotNull(ctx).contarAccessLog())
    }
}
