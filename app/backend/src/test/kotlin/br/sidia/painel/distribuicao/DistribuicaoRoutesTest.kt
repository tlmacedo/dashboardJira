package br.sidia.painel.distribuicao

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.contarAccessLog
import br.sidia.painel.http.ErroDto
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.net.URLEncoder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Distribuição, Rebalanceamento e decisões pelo HTTP no cenário DI-1: visão por papel, decisão uma vez só e auditoria sem o motivo. */
class DistribuicaoRoutesTest {
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

    private fun rodar(bloco: suspend (Di1.Cliente) -> Unit) {
        val c = Di1.novoContexto(banco).also { ctx = it }
        Di1.rodar(c, bloco)
    }

    private fun dist(r: br.sidia.painel.trabalho.RespTrb) = Json.decodeFromString<DistribuicaoDto>(r.corpo)
    private fun reb(r: br.sidia.painel.trabalho.RespTrb) = Json.decodeFromString<RebalanceamentoDto>(r.corpo)
    private fun erro(r: br.sidia.painel.trabalho.RespTrb) = Json.decodeFromString<ErroDto>(r.corpo).codigo
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val D = TipoDeSugestao.DISTRIBUICAO
    private val R = TipoDeSugestao.REBALANCEAMENTO

    private fun linhas(sql: String): List<List<String?>> = requireNotNull(ctx!!.dataSource).connection.use { c ->
        c.createStatement().executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
        }
    }

    @Test
    fun sugestoesDoGerenteEFatoresEmOrdemAlfabetica() = rodar { c ->
        val r = c.get("gerente.exemplo", "/api/distribuicao")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = dist(r)
        assertTrue(d.modoSombra)
        assertEquals("distribuicao v1", d.versaoRegras)
        assertEquals(listOf("TG-301", "TG-302", "TG-303"), d.sugestoes.map { it.issueKey })
        assertEquals(3, d.aguardando)
        assertEquals(3, d.sugestoesPendentes)
        val porChave = d.sugestoes.associateBy { it.issueKey }
        assertEquals("ana", porChave.getValue("TG-301").sugerido)
        assertEquals("carla", porChave.getValue("TG-302").sugerido)
        assertEquals("carla", porChave.getValue("TG-303").sugerido)
        assertTrue(porChave.getValue("TG-302").comGerente)
        assertFalse(porChave.getValue("TG-301").comGerente)
        for (s in d.sugestoes) assertEquals(listOf("ana", "bruno", "carla"), s.fatores.map { it.pessoa }, "alfabética, nunca por score")
        assertEquals(listOf("ana", "bruno", "carla"), d.candidatos)
        assertTrue(d.jainAntes != null && d.jainDepois != null)
        // Sem posição, nota nem ranking na resposta.
        for (proibida in listOf("posicao", "ranking", "rank", "melhor", "pior")) assertFalse("\"$proibida" in r.corpo.lowercase(), proibida)
    }

    @Test
    fun usuarioComum403EDemaisRotasTambem() = rodar { c ->
        for (u in listOf("ana", "carla")) {
            assertEquals(HttpStatusCode.Forbidden, c.get(u, "/api/distribuicao").status, u)
            assertEquals(HttpStatusCode.Forbidden, c.get(u, "/api/rebalanceamento").status, u)
            assertEquals(HttpStatusCode.Forbidden, c.post(u, "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "APROVADA", "carla")).status, u)
            assertEquals(HttpStatusCode.Forbidden, c.get(u, "/api/governanca/decisoes").status, u)
        }
        assertEquals(HttpStatusCode.Unauthorized, c.get(null, "/api/distribuicao").status)
        assertEquals(0, linhas("SELECT 1 FROM decisao_distribuicao").size)
    }

    @Test
    fun registraUmaVezEDecisaoDuplicada409() = rodar { c ->
        val corpo = Di1.corpo(D, "TG-303", "APROVADA", sugerido = "carla")
        val primeira = c.post("gerente.exemplo", "/api/distribuicao/decisao", corpo)
        assertEquals(HttpStatusCode.OK, primeira.status, primeira.corpo)
        val segunda = c.post("gerente.exemplo", "/api/distribuicao/decisao", corpo)
        assertEquals(HttpStatusCode.Conflict, segunda.status)
        assertEquals("JA_DECIDIDA", erro(segunda))
        // Duas abas, dois líderes: a decisão do outro também é recusada, e a primeira é a que vale.
        val outro = c.post("coord.um", "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "REJEITADA", sugerido = "carla"))
        assertEquals(HttpStatusCode.Conflict, outro.status)
        val gravadas = linhas("SELECT issue_key, decisao, escolhido, decidido_por, versao_regras FROM decisao_distribuicao")
        assertEquals(listOf(listOf("TG-303", "APROVADA", "carla", "gerente.exemplo", "distribuicao v1")), gravadas)
        // A tela passa a mostrar a decisão, e a sugestão já decidida sai das pendentes.
        val d = dist(c.get("gerente.exemplo", "/api/distribuicao"))
        assertEquals("APROVADA", d.sugestoes.first { it.issueKey == "TG-303" }.decisao)
        assertEquals(2, d.sugestoesPendentes)
        assertEquals(3, d.aguardando, "a task continua aguardando dono no Jira: o painel não a mexeu")
    }

    @Test
    fun trocadaExigeMotivoEEscolhidoVisivel() = rodar { c ->
        suspend fun trocaParaASugerida(motivo: String?, escolhido: String? = "ana") =
            c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "TROCADA", sugerido = "ana", escolhido = escolhido, motivo = motivo))
        // sugerido da TG-301 é a ana: trocar para outra pessoa.
        suspend fun trocaPara(motivo: String?, escolhido: String? = "bruno") =
            c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "TROCADA", sugerido = "ana", escolhido = escolhido, motivo = motivo))
        assertEquals("MOTIVO_OBRIGATORIO", erro(trocaPara(null)), "sem motivo")
        assertEquals(HttpStatusCode.BadRequest, trocaPara("   \t ‮  ").status, "só espaço e formatação")
        val longo = trocaPara("x".repeat(501))
        assertEquals(HttpStatusCode.BadRequest, longo.status)
        assertEquals("MOTIVO_LONGO", erro(longo))
        // Sem o escolhido, ou o mesmo da sugestão, não é troca.
        assertEquals(HttpStatusCode.BadRequest, trocaPara("competência", escolhido = null).status)
        assertEquals(HttpStatusCode.BadRequest, trocaParaASugerida("mesma pessoa").status)
        // Pessoa que não existe na equipe: fora da visão, 403, sem dizer qual.
        assertEquals(HttpStatusCode.Forbidden, trocaPara("quem?", escolhido = "ninguem.aqui").status)
        assertEquals(0, linhas("SELECT 1 FROM decisao_distribuicao").size, "nada foi gravado nas recusas")
        // 500 caracteres exatos vale, e o motivo sai saneado (controle e espaços repetidos).
        val ok = trocaPara("a".repeat(497) + "\n\n b")
        assertEquals(HttpStatusCode.OK, ok.status, ok.corpo)
        assertEquals("bruno", Json.decodeFromString<DecisaoRegistradaDto>(ok.corpo).escolhido)
        val motivo = linhas("SELECT motivo FROM decisao_distribuicao").single().single()!!
        assertEquals("a".repeat(497) + " b", motivo)
    }

    @Test
    fun sugestaoMudouOuInexistente() = rodar { c ->
        // Sugestão velha na tela (a ana, e não a carla): o servidor recusa com 409 e outro código.
        val velha = c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "APROVADA", sugerido = "ana"))
        assertEquals(HttpStatusCode.Conflict, velha.status)
        assertEquals("SUGESTAO_MUDOU", erro(velha))
        assertEquals(HttpStatusCode.NotFound, c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-999", "REJEITADA")).status)
        // Rejeitar vale sem motivo e sem escolhido.
        assertEquals(HttpStatusCode.OK, c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "REJEITADA", sugerido = "carla")).status)
        assertNull(linhas("SELECT escolhido FROM decisao_distribuicao").single().single())
    }

    @Test
    fun corpoMalFormadoOuEnormeEh4xxSemEco() = rodar { c ->
        suspend fun envia(corpo: String) = c.post("gerente.exemplo", "/api/distribuicao/decisao", corpo)
        for (ruim in listOf("", "{", "[]", "{\"tipo\":\"DISTRIBUICAO\"}", Di1.corpo("ESCREVER_NO_JIRA", "TG-303", "APROVADA"),
            Di1.corpo(D, "TG-303", "APAGAR"), Di1.corpo(D, "../../etc", "REJEITADA"), Di1.corpo(D, "TG-303", "REJEITADA").dropLast(1) + ",\"extra\":1}")) {
            val r = envia(ruim)
            assertEquals(HttpStatusCode.BadRequest, r.status, ruim)
            assertFalse("ESCREVER_NO_JIRA" in r.corpo || "etc" in r.corpo, "o valor recebido nunca volta no corpo")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, envia(Di1.corpo(D, "TG-303", "REJEITADA", motivo = "x".repeat(9_000))).status)
        assertEquals(0, linhas("SELECT 1 FROM decisao_distribuicao").size)
    }

    @Test
    fun semCsrfNaoGrava() = rodar { c ->
        val r = c.postSemCsrf("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "REJEITADA", sugerido = "carla"))
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals(0, linhas("SELECT 1 FROM decisao_distribuicao").size)
    }

    @Test
    fun squadLeaderSoNoProprioSquad() = rodar { c ->
        // O líder do Alfa não tem a carla (Beta) entre os candidatos nem as tasks do gerente entre as pendentes.
        val d = dist(c.get("lider.alfa", "/api/distribuicao"))
        assertEquals(listOf("ana", "bruno"), d.candidatos)
        assertEquals(listOf("TG-301", "TG-303"), d.sugestoes.map { it.issueKey })
        for (s in d.sugestoes) assertTrue(s.fatores.all { it.pessoa in setOf("ana", "bruno") }, "carla fora dos fatores")
        assertFalse("carla" in c.get("lider.alfa", "/api/distribuicao").corpo, "a carla (outro squad) não aparece")
        val antes = ctx!!.contarAccessLog()
        // Trocar para a carla: 403 e nada gravado; a tentativa fica no access_log.
        val r = c.post("lider.alfa", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "TROCADA", sugerido = "ana", escolhido = "carla", motivo = "quero a carla"))
        assertEquals(HttpStatusCode.Forbidden, r.status)
        // Task fora da visão dele (a TG-302 está com o gerente): 403 também, igual a uma task que não existe.
        val fora = c.post("lider.alfa", "/api/distribuicao/decisao", Di1.corpo(D, "TG-302", "REJEITADA", sugerido = "carla"))
        assertEquals(HttpStatusCode.Forbidden, fora.status)
        assertEquals(HttpStatusCode.Forbidden, c.post("lider.alfa", "/api/distribuicao/decisao", Di1.corpo(D, "TG-999", "REJEITADA")).status)
        assertEquals(0, linhas("SELECT 1 FROM decisao_distribuicao").size)
        assertEquals(antes + 3, ctx!!.contarAccessLog(), "cada tentativa negada fica registrada")
        val ultimas = linhas("SELECT route, filters FROM access_log ORDER BY id DESC LIMIT 3")
        assertTrue(ultimas.all { it[0] == "/api/distribuicao/decisao" && "\"resultado\":\"403\"" in it[1]!! }, ultimas.toString())
        // Dentro do squad ele decide normalmente.
        val ok = c.post("lider.alfa", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "TROCADA", sugerido = "ana", escolhido = "bruno", motivo = "ana está em outra frente"))
        assertEquals(HttpStatusCode.OK, ok.status, ok.corpo)
    }

    @Test
    fun postGravaAccessLogSemOCorpoDoMotivo() = rodar { c ->
        val segredo = "motivo-secreto-xyz"
        c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "TROCADA", sugerido = "ana", escolhido = "bruno", motivo = segredo))
        val log = linhas("SELECT user_id, route, filters FROM access_log WHERE route = '/api/distribuicao/decisao'").single()
        assertEquals("gerente.exemplo", log[0])
        val filtros = log[2]!!
        assertTrue("\"tipo\":\"DISTRIBUICAO\"" in filtros && "\"decisao\":\"TROCADA\"" in filtros && "\"resultado\":\"200\"" in filtros, filtros)
        for (proibido in listOf(segredo, "TG-301", "bruno", "ana", "carla")) assertFalse(proibido in filtros, "'$proibido' vazou para o access_log: $filtros")
        // O motivo está só na tabela de decisões.
        assertEquals(segredo, linhas("SELECT motivo FROM decisao_distribuicao").single().single())
    }

    @Test
    fun decisoesListadasPorData() = rodar { c ->
        c.post("gerente.exemplo", "/api/distribuicao/decisao", Di1.corpo(D, "TG-303", "APROVADA", sugerido = "carla"))
        c.post("coord.um", "/api/distribuicao/decisao", Di1.corpo(D, "TG-301", "REJEITADA", sugerido = "ana", motivo = "esperar a sprint"))
        for (u in listOf("coord.um", "gerente.exemplo")) {
            val r = c.get(u, "/api/governanca/decisoes?de=2026-10-01&ate=2026-10-30")
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            val d = Json.decodeFromString<DecisoesDto>(r.corpo)
            assertEquals(listOf("TG-301", "TG-303"), d.decisoes.map { it.issueKey }, "da mais recente para a mais antiga")
            assertEquals("esperar a sprint", d.decisoes.first().motivo)
            assertEquals("coord.um", d.decisoes.first().decididoPor)
            assertFalse(d.truncado)
        }
        // Fora do período: vazio. O líder de squad e o usuário não entram no registro.
        assertEquals(0, Json.decodeFromString<DecisoesDto>(c.get("gerente.exemplo", "/api/governanca/decisoes?de=2026-09-01&ate=2026-09-30").corpo).decisoes.size)
        assertEquals(HttpStatusCode.Forbidden, c.get("lider.alfa", "/api/governanca/decisoes").status)
        // Sem agregação nem ordem por pessoa na resposta.
        val corpo = c.get("gerente.exemplo", "/api/governanca/decisoes?de=2026-10-01&ate=2026-10-30").corpo.lowercase()
        for (proibida in listOf("total", "porpessoa", "ranking", "contagem")) assertFalse("\"$proibida" in corpo, proibida)
        // Datas inválidas ou extremas: 400, nunca 500; o período máximo é de 366 dias.
        for (ruim in listOf("de=lixo", "de=%2B999999999-12-31", "ate=1969-12-31", "de=2026-10-30&ate=2026-10-01", "de=2024-01-01&ate=2026-10-30")) {
            assertEquals(HttpStatusCode.BadRequest, c.get("gerente.exemplo", "/api/governanca/decisoes?$ruim").status, ruim)
        }
    }

    @Test
    fun rebalanceamentoDoGerenteEDoLiderDoSquad() = rodar { c ->
        val g = reb(c.get("gerente.exemplo", "/api/rebalanceamento").also { assertEquals(HttpStatusCode.OK, it.status, it.corpo) })
        val m = g.movimentos.single()
        assertEquals("TG-311", m.issueKey)
        assertEquals("bruno", m.de)
        assertEquals("carla", m.para)
        assertTrue(m.ocupacaoOrigemAntes > g.gatilho && m.ocupacaoDestinoDepois < m.ocupacaoOrigemDepois - 0.2 + 1e-9)
        assertEquals(1.10, g.gatilho)
        assertEquals(1, g.movimentosPendentes)
        // O líder do Alfa só vê o squad dele: o destino é a ana.
        val l = reb(c.get("lider.alfa", "/api/rebalanceamento"))
        assertEquals("ana", l.movimentos.single().para)
        assertFalse("carla" in c.get("lider.alfa", "/api/rebalanceamento").corpo)
        // Decidir: aprovar o movimento; a segunda vez é 409 e a tela passa a mostrar a decisão.
        val corpo = Di1.corpo(R, "TG-311", "APROVADA", sugerido = "carla")
        assertEquals(HttpStatusCode.OK, c.post("gerente.exemplo", "/api/distribuicao/decisao", corpo).status)
        assertEquals(HttpStatusCode.Conflict, c.post("gerente.exemplo", "/api/distribuicao/decisao", corpo).status)
        val depois = reb(c.get("gerente.exemplo", "/api/rebalanceamento"))
        assertEquals("APROVADA", depois.movimentos.single().decisao)
        assertEquals(0, depois.movimentosPendentes)
        // Trocar o destino para a própria origem não é troca.
        assertEquals(HttpStatusCode.BadRequest, c.post("coord.um", "/api/distribuicao/decisao", Di1.corpo(R, "TG-311", "TROCADA", "carla", "bruno", "x")).status)
        // O líder do Alfa não decide para a carla.
        assertEquals(HttpStatusCode.Forbidden, c.post("lider.alfa", "/api/distribuicao/decisao", Di1.corpo(R, "TG-311", "TROCADA", "ana", "carla", "x")).status)
        // O mesmo cartão, o mesmo número: RB-01 e RB-02 abrem o que o painel mostra.
        val gaveta = Json.decodeFromString<DrillGenericoDto>(c.get("gerente.exemplo", "/api/cartoes/RB-01/tasks?segmento=movimentos").corpo)
        assertEquals(1, gaveta.total)
        assertEquals(0, Json.decodeFromString<DrillGenericoDto>(c.get("gerente.exemplo", "/api/cartoes/RB-02/tasks?segmento=semDecisao").corpo).total)
    }

    @Test
    fun numeroDosCartoesIgualAoDrillParaCadaPapel() = rodar { c ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa")) {
            val d = dist(c.get(u, "/api/distribuicao"))
            val r = reb(c.get(u, "/api/rebalanceamento"))
            suspend fun total(cartao: String, seg: String) =
                Json.decodeFromString<DrillGenericoDto>(c.get(u, "/api/cartoes/$cartao/tasks?segmento=${enc(seg)}").also { assertEquals(HttpStatusCode.OK, it.status, "$u $cartao") }.corpo).total
            assertEquals(d.aguardando, total("DI-01", "pendentes"), u)
            assertEquals(d.sugestoesPendentes, total("DI-02", "semDecisao"), u)
            assertEquals(r.movimentos.size, total("RB-01", "movimentos"), u)
            assertEquals(r.movimentosPendentes, total("RB-02", "semDecisao"), u)
        }
        // Cartão de lista não tem gaveta; o usuário comum não vê estes cartões.
        assertEquals(HttpStatusCode.NotFound, c.get("gerente.exemplo", "/api/cartoes/DI-04/tasks?segmento=x").status)
        assertEquals(HttpStatusCode.Forbidden, c.get("ana", "/api/cartoes/DI-01/tasks?segmento=pendentes").status)
        assertEquals(HttpStatusCode.BadRequest, c.get("gerente.exemplo", "/api/cartoes/DI-01/tasks?segmento=semDecisao").status)
    }

    @Test
    fun todaConsultaERegistradaInclusiveANegada() = rodar { c ->
        val antes = ctx!!.contarAccessLog()
        c.get("gerente.exemplo", "/api/distribuicao")
        c.get("ana", "/api/distribuicao")
        c.get("gerente.exemplo", "/api/governanca/decisoes?de=lixo")
        c.get("gerente.exemplo", "/api/rebalanceamento")
        assertEquals(antes + 4, ctx!!.contarAccessLog())
        val rotas = linhas("SELECT route, filters FROM access_log ORDER BY id").takeLast(4)
        assertEquals(listOf("/api/distribuicao", "/api/distribuicao", "/api/governanca/decisoes", "/api/rebalanceamento"), rotas.map { it[0] })
        assertTrue("\"resultado\":\"403\"" in rotas[1][1]!! && "\"resultado\":\"400\"" in rotas[2][1]!!)
    }
}
