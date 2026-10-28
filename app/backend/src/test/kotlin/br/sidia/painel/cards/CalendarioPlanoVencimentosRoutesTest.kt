package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.calendario.CalendarioDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.plano.PlanoDto
import br.sidia.painel.cards.vencimentos.VencimentosDto
import br.sidia.painel.config.Settings
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.testContextComBanco
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
import kotlin.test.assertTrue

/** Rotas de Calendário, Plano e Vencimentos sobre o CAP-1: visão por papel, validação de parâmetros e drill do mesmo tamanho. */
class CalendarioPlanoVencimentosRoutesTest {
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

    private fun calendario(r: RespTrb) = Json.decodeFromString<CalendarioDto>(r.corpo)
    private fun plano(r: RespTrb) = Json.decodeFromString<PlanoDto>(r.corpo)
    private fun vencimentos(r: RespTrb) = Json.decodeFromString<VencimentosDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun calendarioSemana() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/calendario?inicio=2026-11-02")
        assertEquals(HttpStatusCode.OK, r.status)
        val d = calendario(r)
        assertEquals("Finados", d.dias.first { it.dia == "2026-11-02" }.feriado)
        assertEquals(false, d.dias.first { it.dia == "2026-11-02" }.util)
        assertEquals(1.0, d.linhas.first { it.pessoa == "carla" }.celulas.first { it.dia == "2026-11-02" }.ausencia)
        assertEquals(1, d.linhas.first { it.pessoa == "ana" }.celulas.first { it.dia == "2026-11-04" }.vencimentos)
        assertEquals(0.2, d.dias.first { it.dia == "2026-11-04" }.capacidadeSquad!!, 1e-9)
        assertEquals(listOf("ana", "bruno", "carla"), d.linhas.map { it.pessoa })
        assertTrue("motivo" !in r.corpo.lowercase())
    }

    @Test
    fun calendarioPadraoComecaNaSegundaDaSemanaAtual() = rodar { get ->
        assertEquals("2026-10-26", calendario(get("gerente.exemplo", "/api/cards/calendario")).inicio)
        assertEquals(28, calendario(get("gerente.exemplo", "/api/cards/calendario?modo=mes")).dias.size)
    }

    @Test
    fun calendarioParametrosRuinsSao400() = rodar { get ->
        for (ruim in listOf("inicio=ontem", "inicio=2026-13-40", "modo=ano", "modo=")) {
            val r = get("gerente.exemplo", "/api/cards/calendario?$ruim")
            // `modo=` vazio equivale a ausente (padrão semana); os demais são 400.
            assertEquals(if (ruim == "modo=") HttpStatusCode.OK else HttpStatusCode.BadRequest, r.status, ruim)
            assertTrue("ontem" !in r.corpo, "o valor recebido não volta na resposta")
        }
    }

    @Test
    fun calendarioDoUsuarioSoAPropriaLinha() = rodar { get ->
        val r = get("ana", "/api/cards/calendario?inicio=2026-11-02")
        val d = calendario(r)
        assertEquals(listOf("ana"), d.linhas.map { it.pessoa })
        // A capacidade do squad no dia é só a dela: 03/11 = 100% e 04/11 (ausente) = 0%.
        assertEquals(1.0, d.dias.first { it.dia == "2026-11-03" }.capacidadeSquad!!, 1e-9)
        assertEquals(0.0, d.dias.first { it.dia == "2026-11-04" }.capacidadeSquad!!, 1e-9)
        assertTrue("bruno" !in r.corpo && "carla" !in r.corpo && "TG-203" !in r.corpo, r.corpo)
        // O vencimento do dia 05/11 conta só a TG-202 (dela), não a TG-203 do colega.
        assertEquals(1, d.dias.first { it.dia == "2026-11-05" }.vencimentos)
    }

    @Test
    fun planoQuatroSemanas() = rodar { get ->
        val p = plano(get("gerente.exemplo", "/api/cards/plano"))
        assertEquals(4, p.semanas.size)
        val ana = p.linhas.first { it.pessoa == "ana" }.celulas.first()
        assertEquals(8.4, ana.planejadaH, 1e-9)
        assertEquals(5.6, ana.capacidadeH, 1e-9)
    }

    @Test
    fun planoAlternaDe4A8Semanas() = rodar { get ->
        assertEquals(8, plano(get("gerente.exemplo", "/api/cards/plano?semanas=8")).semanas.size)
        assertEquals(5, plano(get("lider.alfa", "/api/cards/plano?semanas=5")).semanas.size)
    }

    @Test
    fun semanasForaDoIntervalo400() = rodar { get ->
        for (s in listOf("3", "9", "0", "-4", "abc", "4.5")) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/plano?semanas=$s").status, s)
        }
    }

    @Test
    fun planoSoLideranca() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/plano").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cartoes/PL-01/tasks?segmento=${enc("celula:ana:2026-10-26")}").status)
        assertEquals(listOf("ana", "bruno"), plano(get("lider.alfa", "/api/cards/plano")).linhas.map { it.pessoa })
    }

    @Test
    fun vencimentosSeteDias() = rodar { get ->
        val v = vencimentos(get("gerente.exemplo", "/api/cards/vencimentos"))
        val l = v.linhas.associateBy { it.key }
        assertEquals(1.0, l.getValue("TG-201").folgaDiasUteis)
        assertEquals(-1.0, l.getValue("TG-202").folgaDiasUteis)
        assertEquals("ok", l.getValue("TG-203").estado)
        assertEquals(1, v.riscoDeAtrasar)
        assertEquals(7, v.dias)
    }

    @Test
    fun diasInvalidos400() = rodar { get ->
        assertEquals(14, vencimentos(get("gerente.exemplo", "/api/cards/vencimentos?dias=14")).dias)
        for (d in listOf("0", "8", "30", "x")) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/vencimentos?dias=$d").status, d)
        }
    }

    @Test
    fun vencimentosDoUsuarioSoAsDele() = rodar { get ->
        val r = get("ana", "/api/cards/vencimentos")
        assertEquals(listOf("TG-201", "TG-202"), vencimentos(r).linhas.map { it.key })
        assertTrue("TG-203" !in r.corpo && "bruno" !in r.corpo, r.corpo)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        suspend fun confere(u: String, cartao: String, segmento: String, esperado: Int, extra: String = "") {
            val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}$extra")
            assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
            assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
            assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val c = calendario(get(u, "/api/cards/calendario?inicio=2026-11-02"))
            for (d in c.dias) confere(u, "CL-01", "dia:${d.dia}", d.vencimentos, "&inicio=2026-11-02")
            val v = vencimentos(get(u, "/api/cards/vencimentos"))
            confere(u, "VE-01", "vencem", v.vencem)
            confere(u, "VE-02", "riscoDeAtrasar", v.riscoDeAtrasar)
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa")) {
            val p = plano(get(u, "/api/cards/plano?semanas=6"))
            for (l in p.linhas) for (c in l.celulas) confere(u, "PL-01", "celula:${l.pessoa}:${c.semana}", c.itens, "&semanas=6")
        }
        // Dia fora da janela do calendário não existe.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CL-01/tasks?segmento=${enc("dia:2027-01-01")}&inicio=2026-11-02").status)
        val dia = gaveta(get("gerente.exemplo", "/api/cartoes/CL-01/tasks?segmento=${enc("dia:2026-11-04")}&inicio=2026-11-02"))
        assertEquals(listOf("TG-201"), dia.linhas.map { it.key })
    }
}
