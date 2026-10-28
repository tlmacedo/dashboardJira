package br.sidia.painel.inicio

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.contarAccessLog
import br.sidia.painel.distribuicao.Di1
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Central de atenção pelo HTTP no cenário DI-1 (sobre o CAP-1): números iguais aos cards de destino e visão por papel. */
class CentralRoutesTest {
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

    /** O mesmo cenário DI-1, com o modo de desenvolvimento ligado (a trava do DPO fica aberta) ou não. */
    private fun contexto(dev: Boolean): AppContext {
        val c = testContextComBanco(
            Settings(dashboardDevMode = dev), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        )
        Trb1.carregar(requireNotNull(c.dataSource), Di1.issues())
        return c.also { ctx = it }
    }

    private fun rodar(dev: Boolean = false, bloco: suspend (Di1.Cliente) -> Unit) = Di1.rodar(contexto(dev), bloco)

    private val rota = "/api/central"
    private fun dto(r: RespTrb) = Json.decodeFromString<CentralDto>(r.corpo)
    private fun CentralDto.item(nome: String) = itens.firstOrNull { it.item == nome }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun inteiro(r: RespTrb, campo: String) = Json.parseToJsonElement(r.corpo).jsonObject.getValue(campo).jsonPrimitive.int

    @Test
    fun itensBatemComAsTelasDeDestino() = rodar { cli ->
        for (u in listOf("gerente.exemplo", "coord.um")) {
            val r = cli.get(u, rota)
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            val c = dto(r)
            // Cada número da Central é o total da gaveta do cartão da Central (que abre o cartão de destino).
            var comTasks = 0
            for (i in c.kpis + c.itens) {
                val cartao = i.cartao ?: continue
                val g = cli.get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(requireNotNull(i.segmento))}")
                assertEquals(HttpStatusCode.OK, g.status, "$u ${i.item}")
                assertEquals(i.n, inteiro(g, "total"), "$u ${i.item}")
                comTasks++
            }
            assertTrue(comTasks >= 10, "poucos indicadores conferidos: $comTasks")
            // E é o número que o próprio card de destino mostra.
            val neg = cli.get(u, "/api/cards/negligenciadas")
            assertEquals(inteiro(neg, "vencidas"), c.item("VENCIDAS")?.n, u)
            assertEquals(inteiro(neg, "semAtualizacao"), c.item("SEM_ATUALIZACAO")?.n, u)
            assertEquals(inteiro(neg, "paradasNoStatus"), c.item("PARADAS_NO_STATUS")?.n, u)
            assertEquals(inteiro(neg, "semResponsavel"), c.item("SEM_RESPONSAVEL")?.n, u)
            assertEquals(inteiro(neg, "bloqueadas"), c.item("BLOQUEADAS")?.n, u)
            assertEquals(inteiro(cli.get(u, "/api/cards/riscos"), "alto"), c.item("RISCO_ALTO")?.n, u)
            assertEquals(inteiro(cli.get(u, "/api/cards/capacidade"), "atrasosPrevistos"), c.item("ATRASOS_PREVISTOS")?.n, u)
            val carga = cli.get(u, "/api/cards/carga")
            assertEquals(inteiro(carga, "tasksAcimaDaCapacidade"), c.item("ACIMA_DA_CAPACIDADE")?.n, u)
            assertEquals(inteiro(carga, "tasksComFolga"), c.item("COM_FOLGA")?.n, u)
            assertEquals(inteiro(cli.get(u, "/api/cards/metas"), "noPrazo"), c.kpis.first { it.item == "NO_PRAZO" }.n, u)
        }
    }

    @Test
    fun cenarioTemNumerosPositivos() = rodar { cli ->
        val c = dto(cli.get("gerente.exemplo", rota))
        // Sem estes pares positivos "número == gaveta" seria 0 == 0.
        assertTrue((c.item("ACIMA_DA_CAPACIDADE")?.n ?: 0) > 0, c.itens.toString())
        assertTrue((c.item("SEM_RESPONSAVEL")?.n ?: 0) > 0, c.itens.toString())
        assertTrue((c.kpis.first { it.item == "ABERTAS" }.n ?: 0) > 0)
        assertEquals(3, c.item("AUSENCIAS_14D")?.n, "carla, ana e bruno (10/11) têm ausência até 13/11, os 14 dias de 30/10")
    }

    @Test
    fun semAtualizacaoEParadasSeparadas() = rodar { cli ->
        val c = dto(cli.get("gerente.exemplo", rota))
        val a = assertNotNull(c.item("SEM_ATUALIZACAO"))
        val p = assertNotNull(c.item("PARADAS_NO_STATUS"))
        assertEquals(listOf("negligenciadas", "negligenciadas"), listOf(a.rota, p.rota))
        assertEquals(listOf("SEM_ATUALIZACAO", "PARADAS_NO_STATUS"), listOf(a.segmento, p.segmento))
        // Há um só item de cada; as gavetas abrem listas diferentes (a das paradas pode ter tasks que a outra não tem).
        assertEquals(1, c.itens.count { it.item == "SEM_ATUALIZACAO" })
        assertEquals(1, c.itens.count { it.item == "PARADAS_NO_STATUS" })
    }

    @Test
    fun centralDoCoordenadorSemItemDeIntegridade() = rodar(dev = true) { cli ->
        val r = cli.get("coord.um", rota)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        assertFalse("INDICIO" in r.corpo, "nenhum rastro do item de integridade para o coordenador")
        assertFalse("VG-13" in r.corpo)
        // A gaveta do VG-13 também não existe para ele.
        assertEquals(HttpStatusCode.Forbidden, cli.get("coord.um", "/api/cartoes/VG-13/tasks?segmento=INDICIO_ALTO").status)
    }

    @Test
    fun centralDoGerenteComIndicioAltoSoComATravaAberta() {
        rodar(dev = true) { cli ->
            val antes = ctx!!.contarAccessLog()
            val c = dto(cli.get("gerente.exemplo", rota))
            val item = assertNotNull(c.item("INDICIO_ALTO"), c.itens.toString())
            assertEquals(listOf("VG-13", "INDICIO_ALTO", "integridade"), listOf(item.cartao, item.segmento, item.rota))
            assertEquals(antes + 1, ctx!!.contarAccessLog(), "a consulta fica registrada")
            val g = cli.get("gerente.exemplo", "/api/cartoes/VG-13/tasks?segmento=INDICIO_ALTO")
            assertEquals(HttpStatusCode.OK, g.status, g.corpo)
            assertEquals(item.n, inteiro(g, "total"))
        }
        encerrar()
        // Fora do modo de desenvolvimento a trava do DPO fica fechada: o item não existe nem para o gerente, e a gaveta é negada.
        rodar(dev = false) { cli ->
            val r = cli.get("gerente.exemplo", rota)
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            assertFalse("INDICIO" in r.corpo)
            assertEquals(HttpStatusCode.Forbidden, cli.get("gerente.exemplo", "/api/cartoes/VG-13/tasks?segmento=INDICIO_ALTO").status)
        }
    }

    @Test
    fun usuarioESquadLeader403() = rodar { cli ->
        for (u in listOf("ana", "carla", "lider.alfa")) assertEquals(HttpStatusCode.Forbidden, cli.get(u, rota).status, u)
        assertEquals(HttpStatusCode.Unauthorized, cli.get(null, rota).status)
        // Os cartões da Central seguem o mesmo papel: quem não vê a tela não abre a gaveta.
        assertEquals(HttpStatusCode.Forbidden, cli.get("lider.alfa", "/api/cartoes/VG-09/tasks?segmento=VENCIDAS").status)
    }

    @Test
    fun cartaoPorSquadAlfabeticoSemNotaDePessoa() = rodar { cli ->
        val r = cli.get("gerente.exemplo", rota)
        val c = dto(r)
        assertEquals(listOf("Alfa", "Beta"), c.squads.map { it.squad })
        assertEquals(listOf(2, 1), c.squads.map { it.pessoas })
        // Só o conjunto: nenhum nome de pessoa no cartão de squad.
        val squads = Json.parseToJsonElement(r.corpo).jsonObject.getValue("squads").toString()
        assertFalse(listOf("ana", "bruno", "carla").any { "\"$it\"" in squads }, squads)
        assertTrue(c.squads.first().jain != null && c.squads.last().jain == null, "o Jain pede ao menos duas pessoas: ${c.squads}")
    }

    @Test
    fun consultaGravaAccessLogSemOCorpo() = rodar { cli ->
        val antes = ctx!!.contarAccessLog()
        cli.get("gerente.exemplo", rota)
        cli.get("ana", rota)
        assertEquals(antes + 2, ctx!!.contarAccessLog(), "inclusive a negada")
    }
}
