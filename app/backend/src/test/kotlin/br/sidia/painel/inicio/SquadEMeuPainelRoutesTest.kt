package br.sidia.painel.inicio

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.contarAccessLog
import br.sidia.painel.distribuicao.Di1
import br.sidia.painel.trabalho.RespTrb
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Visão do squad e Meu painel pelo HTTP no cenário DI-1 (sobre o CAP-1): sempre o próprio usuário, squad do líder e números dos cards. */
class SquadEMeuPainelRoutesTest {
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

    private fun rodar(bloco: suspend (Di1.Cliente) -> Unit) = Di1.rodar(Di1.novoContexto(banco).also { ctx = it }, bloco)

    private fun meu(r: RespTrb) = Json.decodeFromString<MeuPainelDto>(r.corpo)
    private fun squad(r: RespTrb) = Json.decodeFromString<SquadDto>(r.corpo)
    private fun campo(r: RespTrb, nome: String) = Json.parseToJsonElement(r.corpo).jsonObject.getValue(nome).jsonPrimitive.int

    @Test
    fun meuPainelDaAna() = rodar { cli ->
        val r = cli.get("ana", "/api/meu-painel")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = meu(r)
        assertEquals("ana", d.pessoa)
        assertEquals(2, d.minhasAbertas)
        assertEquals(2, d.vencemEm7d)
        assertEquals(0.25, assertNotNull(d.ocupacao4Sem).fracao ?: -1.0, 1e-6)
        assertEquals(listOf("2026-11-04" to "2026-11-05"), d.ausenciasFuturas.map { it.de to it.ate })
        // A fila segue a agenda: TG-201 já iniciada termina primeiro; a TG-202 passa do vencimento.
        assertEquals(listOf("TG-201", "TG-202"), d.fila.map { it.key })
        assertEquals(listOf("2026-11-03", "2026-11-06"), d.fila.map { it.termino })
        assertEquals(listOf(false, true), d.fila.map { it.atrasa })
        assertFalse(d.fila.any { it.resumo.isBlank() })
        // O esforço do mês só aparece com task concluída por ela no mês; sem ela é nulo (nunca zero enganoso).
        assertNull(d.esforcoDoMes)
    }

    @Test
    fun meuPainelIgnoraParametroPessoaDoUsuario() = rodar { cli ->
        val r = cli.get("ana", "/api/meu-painel?pessoa=bruno")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        assertEquals("ana", meu(r).pessoa)
        assertEquals(meu(cli.get("ana", "/api/meu-painel")), meu(r))
        // O mesmo vale para a gaveta dos cartões do Meu painel: o parâmetro não leva ao painel de outra pessoa.
        val a = cli.get("ana", "/api/cartoes/MP-01/tasks?segmento=abertas&pessoa=bruno")
        assertEquals(HttpStatusCode.OK, a.status, a.corpo)
        assertEquals(2, campo(a, "total"))
        assertTrue("TG-203" !in a.corpo, "task do bruno não pode aparecer na gaveta da ana")
        for (u in listOf("coord.um", "gerente.exemplo")) {
            assertEquals("$u", meu(cli.get(u, "/api/meu-painel?pessoa=ana")).pessoa)
        }
        assertEquals(HttpStatusCode.Unauthorized, cli.get(null, "/api/meu-painel").status)
    }

    @Test
    fun squadLeaderVePessoaDoSquad() = rodar { cli ->
        val bruno = cli.get("lider.alfa", "/api/meu-painel?pessoa=bruno")
        assertEquals(HttpStatusCode.OK, bruno.status, bruno.corpo)
        assertEquals("bruno", meu(bruno).pessoa)
        assertEquals(HttpStatusCode.Forbidden, cli.get("lider.alfa", "/api/meu-painel?pessoa=carla").status)
        assertEquals(HttpStatusCode.BadRequest, cli.get("lider.alfa", "/api/meu-painel?pessoa=${"x".repeat(80)}").status)
        // Na gaveta também: pessoa do próprio squad abre, de outro squad é 403.
        val g = cli.get("lider.alfa", "/api/cartoes/MP-01/tasks?segmento=abertas&pessoa=bruno")
        assertEquals(HttpStatusCode.OK, g.status, g.corpo)
        assertEquals(HttpStatusCode.Forbidden, cli.get("lider.alfa", "/api/cartoes/MP-01/tasks?segmento=abertas&pessoa=carla").status)
    }

    @Test
    fun squadDoLider() = rodar { cli ->
        val r = cli.get("lider.alfa", "/api/squad")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = squad(r)
        assertEquals("Alfa", d.squad)
        // Pessoas do squad em ordem alfabética, sem a carla (Beta).
        assertEquals(listOf("ana", "bruno"), d.carga.map { it.pessoa })
        assertEquals(HttpStatusCode.Forbidden, cli.get("lider.alfa", "/api/squad?squad=Beta").status)
        assertEquals(HttpStatusCode.OK, cli.get("lider.alfa", "/api/squad?squad=Alfa").status)
        assertEquals(HttpStatusCode.Forbidden, cli.get("ana", "/api/squad").status)
        assertEquals(HttpStatusCode.Unauthorized, cli.get(null, "/api/squad").status)
        // Coordenação e gerente veem qualquer squad; squad que não existe é 400.
        assertEquals("Beta", squad(cli.get("coord.um", "/api/squad?squad=Beta")).squad)
        assertEquals("Alfa", squad(cli.get("gerente.exemplo", "/api/squad")).squad)
        assertEquals(HttpStatusCode.BadRequest, cli.get("coord.um", "/api/squad?squad=Inexistente").status)
    }

    @Test
    fun numerosIguaisAosCards() = rodar { cli ->
        val ana = meu(cli.get("ana", "/api/meu-painel"))
        // O "vencem em 7 dias" é o do card Vencimentos com dias=7 para a mesma pessoa, e a gaveta tem o mesmo tamanho.
        val venc = cli.get("gerente.exemplo", "/api/cards/vencimentos?pessoa=ana&dias=7")
        assertEquals(campo(venc, "vencem"), ana.vencemEm7d)
        val g = cli.get("ana", "/api/cartoes/MP-01/tasks?segmento=vencemEm7d")
        assertEquals(ana.vencemEm7d, campo(g, "total"))
        assertEquals(ana.minhasAbertas, campo(cli.get("ana", "/api/cartoes/MP-05/tasks?segmento=fila"), "total"))
        assertEquals(ana.fila.size, ana.minhasAbertas)
        // Em risco: o mesmo número do card Riscos (sinais por pessoa) e da gaveta do MP-03.
        val risco = cli.get("ana", "/api/cartoes/MP-03/tasks?segmento=emRisco")
        if (risco.status == HttpStatusCode.OK) assertEquals(ana.emRisco, campo(risco, "total"))
        // Squad: os números são os dos cards com o filtro de squad.
        val s = squad(cli.get("lider.alfa", "/api/squad"))
        val riscos = cli.get("gerente.exemplo", "/api/cards/riscos?squad=Alfa")
        assertEquals(campo(riscos, "alto"), s.riscoAlto)
        assertEquals(campo(riscos, "medio"), s.riscoMedio)
        val v = cli.get("gerente.exemplo", "/api/cards/vencimentos?squad=Alfa&dias=7")
        assertEquals(campo(v, "vencem"), s.vencimentos.vencem)
        assertEquals(s.vencimentos.vencem, campo(cli.get("lider.alfa", "/api/cartoes/VE-01/tasks?segmento=vencem&squad=Alfa&dias=7"), "total"))
        val carga = Json.parseToJsonElement(cli.get("gerente.exemplo", "/api/cards/carga?squad=Alfa").corpo).jsonObject.getValue("linhas")
        assertEquals(s.carga.size, (carga as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun ausenciasDoSquadSemMotivoNemOutroSquad() = rodar { cli ->
        val r = cli.get("lider.alfa", "/api/squad")
        val d = squad(r)
        assertTrue(d.ausencias.isNotEmpty() && d.ausencias.all { it.pessoa in setOf("ana", "bruno") }, d.ausencias.toString())
        assertFalse("motivo" in r.corpo.lowercase())
    }

    @Test
    fun consultasSaoRegistradas() = rodar { cli ->
        val antes = ctx!!.contarAccessLog()
        cli.get("ana", "/api/meu-painel")
        cli.get("ana", "/api/squad")
        cli.get("lider.alfa", "/api/squad")
        assertEquals(antes + 3, ctx!!.contarAccessLog(), "inclusive a negada")
    }
}
