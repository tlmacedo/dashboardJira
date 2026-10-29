package br.sidia.painel.governanca

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.contarAccessLog
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

/** Qualidade dos dados pelo HTTP no cenário QD-1: papéis, números iguais à gaveta, status fora do mapa e auditoria sem o label. */
class QualidadeRoutesTest {
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

    private fun rodar(issues: List<Trb1.IssueDeTeste> = Qd1.issues(), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private val rota = "/api/cards/qualidade"
    private fun dto(r: RespTrb) = Json.decodeFromString<QualidadeDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun QualidadeDto.linha(v: VerificacaoQD) = linhas.first { it.verificacao == v.name }

    @Test
    fun valoresDoCenario() = rodar { get ->
        val r = get("gerente.exemplo", rota)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = dto(r)
        assertEquals(VerificacaoQD.entries.map { it.name }, d.linhas.map { it.verificacao }, "todas as verificações, na ordem do enum")
        fun confere(v: VerificacaoQD, n: Int, universo: Int, estado: String) {
            val l = d.linha(v)
            assertEquals(listOf(n, universo, estado), listOf(l.n, l.universo, l.estado), v.name)
        }
        // Abertas do cenário: TG-101, 102, 103, 104 e 105 (a 106 tem resolução e entra nas recentes).
        confere(VerificacaoQD.SEM_ESTIMATIVA, 2, 5, "problema")
        confere(VerificacaoQD.SEM_RESPONSAVEL, 1, 5, "atencao")
        confere(VerificacaoQD.SEM_VENCIMENTO, 3, 5, "problema")
        confere(VerificacaoQD.EM_ANDAMENTO_SEM_HORAS, 1, 2, "problema")
        confere(VerificacaoQD.SEM_SQUAD, 1, 4, "atencao")
        confere(VerificacaoQD.PRIORIDADE_PADRAO, 3, 5, "problema")
        assertEquals("indisponivel", d.linha(VerificacaoQD.SEM_EPICO).estado, "nenhuma issue tem Epic Link")
        // Conclusão divergente (TG-116 e TG-106).
        confere(VerificacaoQD.DONE_SEM_RESOLUTION, 1, 6, "atencao")
        confere(VerificacaoQD.RESOLUTION_SEM_DONE, 1, 6, "atencao")
        confere(VerificacaoQD.STATUS_SEM_MAPEAMENTO, 0, 12, "ok")
        assertEquals(listOf("Epic Link", "Flagged", "OS Version"), d.linha(VerificacaoQD.CAMPO_INDISPONIVEL).nomes)
        val label = d.linha(VerificacaoQD.LABEL_SEM_CLASSIFICACAO)
        assertEquals(listOf("MYSTERY"), label.nomes)
        assertEquals("neutro", label.estado)
        assertEquals(listOf(LabelSemClasseDto("MYSTERY", 1)), d.labels)
        confere(VerificacaoQD.BLOQUEADA_SEM_CAUSA, 1, 1, "problema")
        assertEquals("qualidade v1", d.versaoRegras)
    }

    @Test
    fun soCoordenacaoEGerente() = rodar { get ->
        assertEquals(HttpStatusCode.OK, get("coord.um", rota).status)
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", rota).status)
        for (u in listOf("lider.alfa", "ana", "carla")) assertEquals(HttpStatusCode.Forbidden, get(u, rota).status, u)
        assertEquals(HttpStatusCode.Unauthorized, get(null, rota).status)
        // Os cartões seguem o mesmo papel: quem não vê a tela não abre a gaveta.
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cartoes/QD-01/tasks?segmento=SEM_ESTIMATIVA").status)
        assertEquals(HttpStatusCode.OK, get("coord.um", "/api/cartoes/QD-01/tasks?segmento=SEM_ESTIMATIVA").status)
    }

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um")) {
            val d = dto(get(u, rota))
            suspend fun confere(cartao: String, segmento: String, esperado: Int) {
                val g = get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(segmento)}")
                assertEquals(HttpStatusCode.OK, g.status, "$u $cartao $segmento ${g.corpo}")
                assertEquals(esperado, gaveta(g).total, "$u $cartao $segmento")
                assertEquals(esperado, gaveta(g).linhas.size, "$u $cartao $segmento")
            }
            for (v in listOf("SEM_ESTIMATIVA", "SEM_RESPONSAVEL", "SEM_VENCIMENTO", "SEM_EPICO", "EM_ANDAMENTO_SEM_HORAS", "SEM_SQUAD", "PRIORIDADE_PADRAO")) {
                confere("QD-01", v, d.linhas.first { it.verificacao == v }.n)
            }
            confere("QD-02", "DONE_SEM_RESOLUTION", d.linha(VerificacaoQD.DONE_SEM_RESOLUTION).n)
            confere("QD-02", "RESOLUTION_SEM_DONE", d.linha(VerificacaoQD.RESOLUTION_SEM_DONE).n)
            confere("QD-03", "STATUS_SEM_MAPEAMENTO", d.linha(VerificacaoQD.STATUS_SEM_MAPEAMENTO).n)
            confere("QD-03", "LABEL_SEM_CLASSIFICACAO", d.linha(VerificacaoQD.LABEL_SEM_CLASSIFICACAO).n)
            confere("QD-03", "label:MYSTERY", 1)
            confere("QD-04", "BLOQUEADA_SEM_CAUSA", d.linha(VerificacaoQD.BLOQUEADA_SEM_CAUSA).n)
        }
        // A gaveta de bloqueadas sem causa abre as mesmas tasks do Bloqueios (BQ-04).
        val qd = gaveta(get("gerente.exemplo", "/api/cartoes/QD-04/tasks?segmento=BLOQUEADA_SEM_CAUSA")).linhas.map { it.key }
        val bq = gaveta(get("gerente.exemplo", "/api/cartoes/BQ-04/tasks?segmento=semCausa")).linhas.map { it.key }
        assertEquals(bq, qd)
        assertEquals(listOf("TG-103"), qd)
        // Cada cartão só abre os seus números; a lista de campos não tem gaveta.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/QD-02/tasks?segmento=SEM_ESTIMATIVA").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/QD-03/tasks?segmento=${enc("label:inexistente")}").status)
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/QD-05/tasks?segmento=CAMPO_INDISPONIVEL").status)
    }

    @Test
    fun statusForaDoMapaNaoDerrubaATela() = rodar(Qd1.comStatusDesconhecido()) { get ->
        val r = get("gerente.exemplo", rota)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = dto(r)
        val l = d.linha(VerificacaoQD.STATUS_SEM_MAPEAMENTO)
        assertEquals(1, l.n)
        assertEquals(listOf("Weird"), l.nomes)
        assertEquals("problema", l.estado)
        assertTrue(d.avisos.any { it.codigo == "ISSUES_IGNORADAS_STATUS" && it.gravidade == "informativo" }, d.avisos.toString())
        assertFalse(d.avisos.any { it.gravidade == "bloqueante" })
        // As demais contas seguem sem a TG-107, e as bloqueadas sem causa (que dependem do Bloqueios) ficam indisponíveis.
        assertEquals(5, d.linha(VerificacaoQD.SEM_RESPONSAVEL).universo)
        assertEquals("indisponivel", d.linha(VerificacaoQD.BLOQUEADA_SEM_CAUSA).estado)
        val g = get("gerente.exemplo", "/api/cartoes/QD-03/tasks?segmento=STATUS_SEM_MAPEAMENTO")
        assertEquals(listOf("TG-107"), gaveta(g).linhas.map { it.key })
    }

    @Test
    fun filtrosNaoAmpliamAVisao() = rodar { get ->
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?squad=Inexistente").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "$rota?de=lixo").status)
        // Filtro de squad reduz o universo (os do Alfa são ana e bruno).
        val alfa = dto(get("gerente.exemplo", "$rota?squad=Alfa"))
        assertTrue(alfa.linha(VerificacaoQD.SEM_RESPONSAVEL).universo < dto(get("gerente.exemplo", rota)).linha(VerificacaoQD.SEM_RESPONSAVEL).universo)
    }

    @Test
    fun auditoriaNaoGuardaOLabelNemChaveDeIssue() = rodar { get ->
        val antes = ctx!!.contarAccessLog()
        get("gerente.exemplo", "/api/cartoes/QD-03/tasks?segmento=${enc("label:MYSTERY")}")
        get("lider.alfa", rota)
        get("coord.um", rota)
        assertEquals(antes + 3, ctx!!.contarAccessLog(), "inclusive a negada")
        val linhas = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2)) }
            }
        }.takeLast(3)
        assertEquals(listOf("/api/cartoes/QD-03/tasks", rota, rota), linhas.map { it.first })
        assertTrue("\"segmento\":\"label:*\"" in linhas[0].second, linhas[0].second)
        assertFalse(linhas.any { "MYSTERY" in it.second || "TG-" in it.second }, linhas.toString())
        assertTrue("\"resultado\":\"403\"" in linhas[1].second)
    }
}
