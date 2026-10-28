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
import kotlin.test.assertTrue

/** Rota dos Cruzamentos sobre o TRB-1 (4 abertas): eixos da lista fechada, visão por papel e célula igual ao drill. */
class CruzamentosRoutesTest {
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

    private fun cruz(r: RespTrb) = Json.decodeFromString<CruzamentosDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val usuarios = listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana", "bruno", "carla")
    private val card = "/api/cards/cruzamentos"

    @Test
    fun padraoETipoPorCategoria() = rodar { get ->
        val d = cruz(get("gerente.exemplo", card))
        assertEquals(listOf("tipo", "categoria", "contagem"), listOf(d.linhasEixo, d.colunasEixo, d.medida))
        assertEquals(listOf("Checklist", "Checklist Review"), d.linhas)
        assertEquals(listOf("TODO", "ACTIVE", "BLOCKED"), d.colunas)
        assertEquals(listOf(listOf(1, 1, 1), listOf(1, 0, 0)), d.celulas.map { l -> l.map { it.n } })
        assertEquals(4, d.total)
        assertTrue(d.eixosDeColuna.none { it == "pessoa" } && "pessoa" in d.eixosDeLinha)
    }

    @Test
    fun soAceitaEixosDaListaFechada() = rodar { get ->
        for (eixo in Eixo.entries) {
            assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "$card?linhas=${eixo.id}&colunas=${if (eixo == Eixo.TIPO) "status" else "tipo"}").status, eixo.id)
        }
        for (ruim in listOf("linhas=xyz", "colunas=xyz", "linhas=assignee", "linhas=", "medida=media", "linhas=tipo&colunas=tipo", "colunas=pessoa")) {
            val r = get("gerente.exemplo", "$card?$ruim")
            if (ruim == "linhas=") assertEquals(HttpStatusCode.OK, r.status, "valor vazio volta ao padrão")
            else assertEquals(HttpStatusCode.BadRequest, r.status, ruim)
            assertFalse("xyz" in r.corpo, "o valor recebido nunca volta no corpo")
        }
        // Todas as medidas da lista.
        for (m in Medida.entries) assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "$card?medida=${m.id}").status)
    }

    @Test
    fun eixoDeCampoDoDicionarioDependeDoPlano10() = rodar { get ->
        for (campo in CruzamentosCalculator.DICIONARIO_PENDENTE) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card?linhas=$campo").status, campo)
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card?colunas=$campo").status, campo)
        }
        val d = cruz(get("gerente.exemplo", card))
        assertTrue(CruzamentosCalculator.DICIONARIO_PENDENTE.none { it in d.eixosDeLinha || it in d.eixosDeColuna })
    }

    @Test
    fun pessoaSoComoLinhaEmOrdemAlfabeticaSemTotalEmDestaque() = rodar(Trb1.issues() + (1..3).map { Trb1.issue("TG-30$it", "Checklist", "carla", "10-20T12", "Open") }) { get ->
        val d = cruz(get("gerente.exemplo", "$card?linhas=pessoa&colunas=status"))
        assertEquals(listOf("ana", "bruno", "carla", "(sem)"), d.linhas, "alfabética, mesmo com a carla tendo mais tasks; (sem) por último")
        assertEquals(null, d.totaisDasLinhas)
        // O segmento do total de uma linha de pessoa não existe.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CZ-01/tasks?segmento=${enc("l:1")}&linhas=pessoa&colunas=status").status)
    }

    @Test
    fun usuarioVeSoAPropriaLinha() = rodar { get ->
        val ana = get("ana", "$card?linhas=pessoa&colunas=status")
        assertEquals(listOf("ana"), cruz(ana).linhas)
        for (outro in listOf("bruno", "carla")) assertFalse("\"$outro\"" in ana.corpo, "$outro na resposta")
        assertEquals(listOf("carla"), cruz(get("carla", "$card?linhas=pessoa&colunas=status")).linhas)
        // O líder do Alfa vê ana e bruno, em ordem alfabética.
        assertEquals(listOf("ana", "bruno"), cruz(get("lider.alfa", "$card?linhas=pessoa&colunas=status")).linhas)
        // Pedir a linha de outra pessoa não amplia a visão.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "$card?pessoa=bruno").status)
        // Sem responsável só para coordenação e gerente: o (sem) não aparece para o líder.
        assertTrue("(sem)" !in cruz(get("lider.alfa", "$card?linhas=pessoa&colunas=status")).linhas)
        assertTrue("(sem)" in cruz(get("coord.um", "$card?linhas=pessoa&colunas=status")).linhas)
    }

    @Test
    fun celulaIgualAoDrill() = rodar { get ->
        val consultas = listOf("", "?linhas=pessoa&colunas=status", "?linhas=squad&colunas=prioridade&medida=idadeP50", "?linhas=status&colunas=idade&unit=corrido")
        for (u in usuarios) for (q in consultas) {
            val d = cruz(get(u, "$card$q"))
            val parametros = q.removePrefix("?").let { if (it.isEmpty()) "" else "&$it" }
            suspend fun confere(segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/CZ-01/tasks?segmento=${enc(segmento)}$parametros")
                assertEquals(HttpStatusCode.OK, g.status, "$u $q $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $q $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $q $segmento")
            }
            confere("total", d.total)
            for ((i, linha) in d.celulas.withIndex()) {
                for ((j, c) in linha.withIndex()) confere("c:${i + 1}:${j + 1}", c.n)
                d.totaisDasLinhas?.let { confere("l:${i + 1}", it[i]) }
            }
            for (j in d.colunas.indices) confere("k:${j + 1}", d.totaisDasColunas[j])
        }
    }

    @Test
    fun segmentoPosicionalForaDoIntervaloEh400() = rodar { get ->
        for (seg in listOf("c:9:9", "c:0:1", "c:a:b", "l:9", "k:9", "pessoa:ana", "")) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CZ-01/tasks?segmento=${enc(seg)}").status, "segmento '$seg'")
        }
    }

    @Test
    fun medidaIdadeP50ComAvisoDeAmostraPequenaPorCelula() = rodar { get ->
        val d = cruz(get("gerente.exemplo", "$card?medida=idadeP50&unit=corrido"))
        // A TG-101 (ativa, desde 01/10) tem 29 dias; sozinha na célula: valor 29,0 e aviso de amostra pequena.
        val ativa = d.celulas[d.linhas.indexOf("Checklist")][d.colunas.indexOf("ACTIVE")]
        assertEquals(1, ativa.n)
        assertEquals(29.0, ativa.valor!!, 1e-9)
        assertTrue(ativa.amostraPequena)
        assertEquals("idadeP50", d.medida)
        assertEquals("corrido", d.unit)
    }

    @Test
    fun accessLogGravaOsEixosMasNaoValoresNemChaves() = rodar { get ->
        get("gerente.exemplo", "$card?linhas=pessoa&colunas=status")
        get("gerente.exemplo", "/api/cartoes/CZ-01/tasks?segmento=${enc("c:1:1")}&linhas=pessoa&colunas=status")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(2, linhas.size, linhas.toString())
        assertTrue("\"linhas\":\"pessoa\"" in linhas[0] && "\"colunas\":\"status\"" in linhas[0], linhas[0])
        assertTrue("\"segmento\":\"c:1:1\"" in linhas[1], linhas[1])
        for (p in listOf("ana", "bruno", "carla")) assertTrue(linhas.none { "\"$p\"" in it || "=$p" in it }, "$p no access_log")
        assertTrue(linhas.none { Regex("TG-\\d+").containsMatchIn(it) }, linhas.toString())
    }

    @Test
    fun accessLogNaoGravaEixoEMedidaInvalidos() = rodar { get ->
        val r = get("gerente.exemplo", "$card?linhas=ana&colunas=TG-101&medida=xyz")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) + " " + rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(1, linhas.size, linhas.toString())
        for (cru in listOf("ana", "TG-101", "xyz")) assertTrue(cru !in linhas[0], "$cru cru no access_log: ${linhas[0]}")
        assertTrue("\"linhas\":\"(invalido)\"" in linhas[0], linhas[0])
    }

    @Test
    fun statusNaoMapeadoPapelEUnidade() = rodar(Trb1.issues() + Trb1.issue("TG-199", "Checklist", "ana", "10-20T12", "Revisao Exotica")) { get ->
        val r = get("gerente.exemplo", card)
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(AvisoDto.STATUS_SEM_MAPEAMENTO, cruz(r).avisos.single().codigo)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$card?unit=hora").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, card).status)
    }
}
