package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.capacidade.CapacidadeDto
import br.sidia.painel.cards.carga.CargaDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.config.Settings
import br.sidia.painel.contarAccessLog
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rotas de Carga e Capacidade sobre o CAP-1: visão por papel, sem ranking, sem motivo e drill do mesmo tamanho. */
class CargaCapacidadeRoutesTest {
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

    private fun carga(r: RespTrb) = Json.decodeFromString<CargaDto>(r.corpo)
    private fun capacidade(r: RespTrb) = Json.decodeFromString<CapacidadeDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun linhasAlfabeticasSemPosicao() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/carga")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(listOf("ana", "bruno", "carla"), carga(r).linhas.map { it.pessoa })
        // Nenhuma chave de posição, ranking ou extremo na resposta.
        for (proibida in listOf("posicao", "ranking", "rank", "maior", "menor", "pior", "melhor")) {
            assertTrue("\"$proibida" !in r.corpo.lowercase(), proibida)
        }
        assertEquals("planejamento v1", carga(r).versaoRegras)
    }

    @Test
    fun linhaDaAnaEDaCarlaPeloHttp() = rodar { get ->
        val d = carga(get("gerente.exemplo", "/api/cards/carga"))
        val ana = d.linhas.first { it.pessoa == "ana" }
        assertEquals(2, ana.abertas)
        assertEquals(16.8, ana.filaH, 1e-9)
        assertEquals(67.2, ana.capacidade4SemH, 1e-9)
        assertEquals(0.25, ana.ocupacao.fracao!!, 1e-9)
        val carla = d.linhas.first { it.pessoa == "carla" }
        assertEquals(6.0, carla.atrasoAcumuladoH, 1e-9)
        assertEquals(true, carla.horasAssumidas)
    }

    @Test
    fun contagensNoLugarDeExtremos() = rodar { get ->
        val d = carga(get("gerente.exemplo", "/api/cards/carga"))
        assertEquals(0, d.acimaDaCapacidade)
        assertEquals(3, d.comFolga)
    }

    @Test
    fun usuarioNaoVeAgregadoDoSquad() = rodar { get ->
        val d = carga(get("ana", "/api/cards/carga"))
        assertEquals(listOf("ana"), d.linhas.map { it.pessoa })
        assertNull(d.jain, "um usuário não recebe índice do conjunto")
        // Nada dos colegas: nem o nome, nem as tasks, nem as horas.
        val corpo = get("ana", "/api/cards/carga").corpo
        assertTrue("bruno" !in corpo && "carla" !in corpo && "TG-203" !in corpo && "TG-205" !in corpo, corpo)
        // O squad leader vê o próprio squad (ana e bruno), com índice.
        val lider = carga(get("lider.alfa", "/api/cards/carga"))
        assertEquals(listOf("ana", "bruno"), lider.linhas.map { it.pessoa })
        assertNotNull(lider.jain)
    }

    @Test
    fun parametroDePessoaNaoAmpliaAVisao() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/carga?pessoa=bruno").status)
        assertEquals(listOf("bruno"), carga(get("gerente.exemplo", "/api/cards/carga?pessoa=bruno")).linhas.map { it.pessoa })
        assertEquals(listOf("carla"), carga(get("gerente.exemplo", "/api/cards/carga?squad=Beta")).linhas.map { it.pessoa })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/carga?squad=Inexistente").status)
    }

    @Test
    fun mapaMostraAusente() = rodar { get ->
        val d = capacidade(get("gerente.exemplo", "/api/cards/capacidade"))
        val carla = d.mapa.first { it.pessoa == "carla" }.semanas.first { it.semana == "2026-11-02" }
        assertTrue(carla.ocupacao.semCapacidade)
        assertTrue(carla.ausente)
        assertEquals(listOf("ana", "bruno", "carla"), d.mapa.map { it.pessoa })
    }

    @Test
    fun alertaAusenciaComTrabalhoEAtrasoPorAusencia() = rodar { get ->
        val d = capacidade(get("gerente.exemplo", "/api/cards/capacidade"))
        assertEquals(5.6, d.alertas.single { it.pessoa == "ana" && it.tipo == "AUSENCIA_COM_TRABALHO" }.horasAReplanejar!!, 1e-9)
        assertEquals("AUSENCIA", d.listaAtrasos.single { it.key == "TG-202" }.causa)
        assertEquals(1, d.atrasosPorAusencia)
    }

    @Test
    fun ausenciasSemMotivoNaResposta() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/capacidade")
        assertTrue("motivo" !in r.corpo.lowercase(), r.corpo)
        assertEquals(3, capacidade(r).ausencias.size)
    }

    @Test
    fun capacidadeSoLideranca() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/capacidade").status)
        assertEquals(HttpStatusCode.OK, get("lider.alfa", "/api/cards/capacidade").status)
        assertEquals(HttpStatusCode.OK, get("coord.um", "/api/cards/capacidade").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/capacidade").status)
        // O líder só enxerga o próprio squad no mapa e nas ausências.
        val lider = capacidade(get("lider.alfa", "/api/cards/capacidade"))
        assertEquals(listOf("ana", "bruno"), lider.mapa.map { it.pessoa })
        assertTrue(lider.ausencias.none { it.pessoa == "carla" })
        // O drill de um cartão da liderança também é 403 para o usuário.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cartoes/CA-02/tasks?segmento=atrasosPrevistos").status)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        suspend fun confere(u: String, cartao: String, segmento: String, esperado: Int) {
            val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
            assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
            assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
            assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val c = carga(get(u, "/api/cards/carga"))
            confere(u, "CP-02", "acimaDaCapacidade", c.tasksAcimaDaCapacidade)
            confere(u, "CP-02", "comFolga", c.tasksComFolga)
            for (l in c.linhas) {
                confere(u, "CP-03", "pessoa:${l.pessoa}:fila", l.abertas)
                confere(u, "CP-03", "pessoa:${l.pessoa}:wip", l.wip)
                confere(u, "CP-03", "pessoa:${l.pessoa}:vencidas", l.vencidas)
                confere(u, "CP-03", "pessoa:${l.pessoa}:acimaDoP85", l.acimaDoP85)
                confere(u, "CP-03", "pessoa:${l.pessoa}:riscoAlto", l.riscoAlto)
            }
        }
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa")) {
            val c = capacidade(get(u, "/api/cards/capacidade"))
            confere(u, "CA-02", "atrasosPrevistos", c.atrasosPrevistos)
            confere(u, "CA-03", "atrasosPorAusencia", c.atrasosPorAusencia)
            confere(u, "CA-09", "atrasosPrevistos", c.listaAtrasos.size)
        }
        // A gaveta da ana não revela colegas; o segmento de outra pessoa não existe na visão dela.
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cartoes/CP-03/tasks?segmento=${enc("pessoa:bruno:fila")}").status)
        val fila = gaveta(get("gerente.exemplo", "/api/cartoes/CP-03/tasks?segmento=${enc("pessoa:ana:fila")}"))
        assertEquals(listOf("TG-201", "TG-202"), fila.linhas.map { it.key })
        assertTrue("Restam 11.2 h" in fila.linhas.first().detalhe, fila.linhas.first().detalhe)
    }

    @Test
    fun consultaEhAuditadaInclusiveNegada() = rodar { get ->
        val antes = requireNotNull(ctx).contarAccessLog()
        get("gerente.exemplo", "/api/cards/carga")
        get("ana", "/api/cards/capacidade")
        assertEquals(antes + 2, requireNotNull(ctx).contarAccessLog())
    }

    @Test
    fun statusNaoMapeado200ComAvisoBloqueante() {
        val c = testContextComBanco(
            Settings(dashboardDevMode = false), banco, Cap1.papeis, Cap1.relogio, statusMap = Cap1.statusMap, calendar = Cap1.calendario,
            equipes = Cap1.equipes, ausencias = Cap1.ausencias, regrasPlanejamento = Cap1.regras,
        ).also { ctx = it }
        Trb1.carregar(requireNotNull(c.dataSource), Cap1.issuesParaBanco() + Trb1.issue("TG-499", "Checklist", "ana", "10-20T12", "Revisao Exotica"))
        rodarTrb1(c) { get ->
            for (card in listOf("carga", "capacidade")) {
                val r = get("gerente.exemplo", "/api/cards/$card")
                assertEquals(HttpStatusCode.OK, r.status, card)
                assertTrue(AvisoDto.STATUS_SEM_MAPEAMENTO in r.corpo && AvisoDto.BLOQUEANTE in r.corpo, card)
            }
        }
    }
}
