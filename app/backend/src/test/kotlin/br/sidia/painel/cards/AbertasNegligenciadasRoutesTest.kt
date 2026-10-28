package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.abertas.AbertasDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.negligenciadas.NegligenciadasDto
import br.sidia.painel.http.ErroDto
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Rotas dos cards Abertas e Negligenciadas: visão por papel, drill do mesmo tamanho, avisos e auditoria. */
class AbertasNegligenciadasRoutesTest {
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

    private fun abertas(r: RespTrb) = Json.decodeFromString<AbertasDto>(r.corpo)
    private fun negligenciadas(r: RespTrb) = Json.decodeFromString<NegligenciadasDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)

    @Test
    fun drillTemOMesmoTamanhoDoCardAbertas() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "carla")) {
            val d = abertas(get(u, "/api/cards/abertas?unit=corrido"))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?unit=corrido&segmento=${java.net.URLEncoder.encode(segmento, "UTF-8")}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size)
            }
            confere("AB-01", "abertas", d.abertas)
            confere("AB-01", "emAndamento", d.emAndamento)
            confere("AB-04", "acimaDoP85", d.acimaDoP85)
            confere("AB-09", "abertas", d.abertas)
            for (c in d.porStatus) confere("AB-05", "status:${c.chave}", c.n)
            for (c in d.porPrioridade) confere("AB-06", "prioridade:${c.chave}", c.n)
            for (c in d.porIdade) confere("AB-07", "idade:${c.chave}", c.n)
            for (c in d.porVencimento) confere("AB-08", "vence:${c.chave}", c.n)
            for (f in d.historicoPorTipo) confere("AB-02", "historico:${f.tipo}", f.n)
        }
    }

    @Test
    fun drillTemOMesmoTamanhoDoCardNegligenciadas() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val d = negligenciadas(get(u, "/api/cards/negligenciadas"))
            val esperado = mapOf(
                "NG-01" to ("semAtualizacao" to d.semAtualizacao), "NG-02" to ("semResponsavel" to d.semResponsavel),
                "NG-03" to ("vencidas" to d.vencidas), "NG-04" to ("bloqueadas" to d.bloqueadas),
                "NG-05" to ("paradas" to d.paradasNoStatus), "NG-06" to ("aFazer" to d.aFazerAntigas),
            )
            for ((cartao, par) in esperado) {
                val g = gaveta(get(u, "/api/cartoes/$cartao/tasks?segmento=${par.first}"))
                assertEquals(par.second, g.total, "$u $cartao")
                assertEquals(par.second, g.linhas.size)
                assertEquals(d.listas.getValue(par.first).map { it.key }, g.linhas.map { it.key }.take(d.listas.getValue(par.first).size))
            }
        }
    }

    @Test
    fun usuarioVeSoAsProprias() = rodar { get ->
        val ana = abertas(get("ana", "/api/cards/abertas"))
        assertEquals(1, ana.abertas)
        assertEquals(listOf("TG-101"), ana.linhas.map { it.key })
        assertEquals(1, abertas(get("carla", "/api/cards/abertas")).abertas)
        assertEquals(2, abertas(get("lider.alfa", "/api/cards/abertas")).abertas)
        assertEquals(4, abertas(get("gerente.exemplo", "/api/cards/abertas")).abertas)
        // Negligenciadas: a ana só vê a TG-101 (sem atualização, vencida, parada).
        val n = negligenciadas(get("ana", "/api/cards/negligenciadas"))
        assertEquals(listOf(1, 0, 1, 0, 1, 0), listOf(n.semAtualizacao, n.semResponsavel, n.vencidas, n.bloqueadas, n.paradasNoStatus, n.aFazerAntigas))
    }

    @Test
    fun historicoDoUsuarioSoComAsProprias() = rodar { get ->
        val ana = abertas(get("ana", "/api/cards/abertas"))
        val checklist = ana.historicoPorTipo.single { it.tipo == "Checklist" }
        // TG-111 e TG-113 (ciclos úteis 4 e 8): nada de TG-112, do bruno.
        assertEquals(2, checklist.n)
        assertEquals(6.0, checklist.p50, 1e-9)
        assertEquals(7.4, checklist.p85, 1e-9)
        val aviso = ana.avisos.single { it.codigo == AvisoDto.AMOSTRA_PEQUENA && it.params["tipo"] == "Checklist" }
        assertEquals("2", aviso.params["n"])
        assertTrue(ana.historicoPorTipo.none { it.tipo == "Checklist Review" }, "a Review é da carla")
        val gerente = abertas(get("gerente.exemplo", "/api/cards/abertas"))
        assertEquals(3, gerente.historicoPorTipo.single { it.tipo == "Checklist" }.n)
        assertEquals(1, gerente.historicoPorTipo.single { it.tipo == "Checklist Review" }.n)
    }

    @Test
    fun semResponsavelSoCoordenacao() = rodar { get ->
        assertEquals(1, negligenciadas(get("coord.um", "/api/cards/negligenciadas")).semResponsavel)
        assertEquals(1, negligenciadas(get("gerente.exemplo", "/api/cards/negligenciadas")).semResponsavel)
        for (u in listOf("lider.alfa", "ana", "bruno", "carla")) {
            assertEquals(0, negligenciadas(get(u, "/api/cards/negligenciadas")).semResponsavel, u)
        }
        assertFalse("TG-104" in abertas(get("lider.alfa", "/api/cards/abertas")).linhas.map { it.key })
        assertTrue("TG-104" in abertas(get("coord.um", "/api/cards/abertas")).linhas.map { it.key })
    }

    @Test
    fun statusNaoMapeado200() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica", prioridade = "High")) { get ->
        for (caminho in listOf("/api/cards/abertas", "/api/cards/negligenciadas")) {
            val r = get("gerente.exemplo", caminho)
            assertEquals(HttpStatusCode.OK, r.status, caminho)
            val avisos = if ("abertas" in caminho) abertas(r).avisos else negligenciadas(r).avisos
            val aviso = avisos.single()
            assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, aviso.codigo)
            assertEquals(AvisoDto.BLOQUEANTE, aviso.gravidade)
            assertEquals("Revisao Exotica", aviso.params["status"])
        }
        assertEquals(0, abertas(get("gerente.exemplo", "/api/cards/abertas")).abertas)
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "/api/cartoes/AB-01/tasks?segmento=abertas").status)
    }

    @Test
    fun baseSemCamposDeTrabalhoMostraAvisoNaoZero() = rodar(Trb1.issues(comPrioridade = false)) { get ->
        val d = abertas(get("gerente.exemplo", "/api/cards/abertas"))
        assertEquals(emptyList(), d.porPrioridade)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS })
        assertEquals(4, d.abertas)
    }

    @Test
    fun filtrosDeTipoPrioridadeEUnidadeAlteramOCard() = rodar { get ->
        assertEquals(1, abertas(get("gerente.exemplo", "/api/cards/abertas?tipo=Checklist%20Review")).abertas)
        assertEquals(2, abertas(get("gerente.exemplo", "/api/cards/abertas?prioridade=High")).abertas)
        assertEquals(1, abertas(get("gerente.exemplo", "/api/cards/abertas?pessoa=carla")).abertas)
        assertEquals(2, abertas(get("gerente.exemplo", "/api/cards/abertas?squad=Alfa")).abertas)
        val corrido = abertas(get("gerente.exemplo", "/api/cards/abertas?unit=corrido"))
        assertEquals("corrido", corrido.unit)
        assertEquals(29.0, corrido.linhas.first { it.key == "TG-101" }.idade, 1e-9)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/abertas?unit=semana").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/abertas?pessoa=bruno").status)
    }

    @Test
    fun filtroSemMovimentoRealNaoSubstituiASemAtualizacao() = rodar { get ->
        val normal = negligenciadas(get("gerente.exemplo", "/api/cards/negligenciadas"))
        val real = negligenciadas(get("gerente.exemplo", "/api/cards/negligenciadas?movimento=semReal"))
        assertFalse(normal.semMovimentoReal)
        assertTrue(real.semMovimentoReal)
        // Sem movimento real há mais de 7 dias: TG-101 (status em 01/10), TG-102 (nunca mexeu), TG-103 (On Hold em 26/10: 4 dias, fora).
        assertEquals(listOf("TG-101"), real.listas.getValue("semAtualizacao").map { it.key })
        assertEquals(listOf("TG-102"), real.listas.getValue("aFazer").map { it.key })
        assertEquals(0, real.bloqueadas, "TG-103 mexeu há 4 dias")
        assertEquals(1, normal.bloqueadas)
        // O drill respeita o mesmo filtro.
        assertEquals(0, gaveta(get("gerente.exemplo", "/api/cartoes/NG-04/tasks?segmento=bloqueadas&movimento=semReal")).total)
        assertEquals(1, gaveta(get("gerente.exemplo", "/api/cartoes/NG-04/tasks?segmento=bloqueadas")).total)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/negligenciadas?movimento=qualquer").status)
        // Cada linha traz o último movimento real.
        val linha = normal.listas.getValue("semAtualizacao").single()
        assertEquals("status", linha.ultimoMovimento.campo)
        assertEquals("2026-10-01", linha.ultimoMovimento.dia)
        assertEquals(29.0, linha.ultimoMovimento.dias, 1e-9)
        assertTrue(linha.semMovimentoReal)
    }

    @Test
    fun segmentoDeOutroCartaoE400() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cartoes/AB-05/tasks?segmento=prioridade:High")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("SEGMENTO_INVALIDO", Json.decodeFromString<ErroDto>(r.corpo).codigo)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/NG-01/tasks?segmento=vencidas").status)
        // Segmento do prefixo certo, mas que não existe neste cálculo (status sem nenhuma task): 400, nunca lista vazia.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/AB-05/tasks?segmento=status:Inexistente%20Aqui").status)
    }

    @Test
    fun consultaFicaNoAccessLogSemChaveDeIssue() = rodar { get ->
        get("ana", "/api/cards/abertas?tipo=Checklist")
        get("ana", "/api/cartoes/AB-01/tasks?segmento=abertas")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(2, linhas.size)
        assertNotNull(linhas.firstOrNull { it.first == "/api/cards/abertas" })
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it.first + it.second) })
        assertTrue(linhas.any { "\"total\":\"1\"" in it.second }, linhas.toString())
    }
}
