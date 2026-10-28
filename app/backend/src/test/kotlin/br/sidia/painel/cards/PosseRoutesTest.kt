package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.posse.PosseDto
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Posse da task pelo HTTP: tempo proporcional, pessoa fora da visão como "outra pessoa" (nulo), sem ranking e drill do mesmo tamanho. */
class PosseRoutesTest {
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

    /**
     * TG-401 (fechada em 12/10): `ana` de 05 a 08/10 e `bruno` de 08 a 12/10 (o TG-1 do Plano 3). TG-402 (aberta): da `ana`
     * desde sempre. TG-403 (aberta): da `carla` (outro squad).
     */
    private fun issues() = listOf(
        Trb1.issue(
            "TG-401", "Checklist", "bruno", "10-05T12", "Closed", resolution = "Done", resolvida = "10-12T12",
            eventos = Trb1.trilha("TG-401", "Open", "10-06T12" to "In Progress", "10-09T12" to "Submitted", "10-12T12" to "Closed") +
                Trb1.ev("TG-401", "assignee", "ana", "bruno", "10-08T12"),
        ),
        Trb1.issue("TG-402", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-402", "Open", "10-21T12" to "In Progress")),
        Trb1.issue("TG-403", "Checklist", "carla", "10-10T12", "Open"),
    )

    private fun rodar(bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, issues()).also { ctx = it }
        rodarTrb1(c, bloco = bloco)
    }

    private fun posse(r: RespTrb) = Json.decodeFromString<PosseDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun posseDoTG1PeloHttp() = rodar { get ->
        val d = posse(get("gerente.exemplo", "/api/cards/posse"))
        val t = d.tasks.first { it.key == "TG-401" }
        assertEquals(1, t.transferencias)
        assertEquals(listOf("ana", "bruno"), t.posse.map { it.pessoa })
        assertEquals(3.0, t.posse[0].dias, 1e-6)
        assertEquals(0.642857, t.posse[0].fracao, 1e-6)
        assertEquals(0.357143, t.posse[1].fracao, 1e-6)
        assertEquals("util", d.unit)
        // O gerente vê as três pessoas, em ordem alfabética (nunca por tempo de posse).
        assertEquals(listOf("ana", "bruno", "carla"), d.pessoas.map { it.pessoa })
        val ana = d.pessoas.first { it.pessoa == "ana" }
        assertEquals(2, ana.tasks)
        assertEquals(11.0, ana.dias, 1e-6)
        assertEquals(0, ana.recebidas)
        assertEquals(1, ana.repassadas)
        val bruno = d.pessoas.first { it.pessoa == "bruno" }
        assertEquals(1, bruno.recebidas)
        assertEquals(0, bruno.repassadas)
        assertEquals(listOf("TG-401", "TG-402", "TG-403"), d.tasks.map { it.key })
    }

    @Test
    fun pessoaInvisivelViraNula() = rodar { get ->
        val r = get("ana", "/api/cards/posse")
        val d = posse(r)
        val t = d.tasks.first { it.key == "TG-401" }
        // O bruno aparece como "outra pessoa" (nulo) com a fração dele, nunca pelo nome.
        assertEquals(listOf("ana", null), t.posse.map { it.pessoa })
        assertEquals(0.357143, t.posse[1].fracao, 1e-6)
        assertEquals(listOf("ana"), d.pessoas.map { it.pessoa })
        assertTrue("bruno" !in r.corpo && "carla" !in r.corpo && "TG-403" !in r.corpo, r.corpo)
    }

    @Test
    fun squadLeaderVeOSquadEOutroSquadVemNulo() = rodar { get ->
        val d = posse(get("lider.alfa", "/api/cards/posse"))
        assertEquals(listOf("ana", "bruno"), d.pessoas.map { it.pessoa })
        assertTrue(d.tasks.none { it.key == "TG-403" })
        assertNotNull(d.tasks.first { it.key == "TG-401" }.posse.firstOrNull { it.pessoa == "bruno" })
    }

    @Test
    fun parametrosNaoAmpliamAVisao() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/posse?pessoa=bruno").status)
        val soBruno = posse(get("gerente.exemplo", "/api/cards/posse?pessoa=bruno"))
        assertEquals(listOf("bruno"), soBruno.pessoas.map { it.pessoa })
        // Com o filtro, a ana (fora do filtro) vira "outra pessoa".
        assertEquals(listOf(null, "bruno"), soBruno.tasks.single { it.key == "TG-401" }.posse.map { it.pessoa }.sortedBy { it ?: "" })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/posse?squad=Inexistente").status)
    }

    @Test
    fun periodoPelaResolucaoMaisAsAbertas() = rodar { get ->
        // A TG-401 foi resolvida em 12/10: sai se o período começa em 20/10; as abertas continuam.
        val d = posse(get("gerente.exemplo", "/api/cards/posse?de=2026-10-20"))
        assertEquals(listOf("TG-402", "TG-403"), d.tasks.map { it.key })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/posse?de=2026-10-20&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/posse?de=+999999999-12-31").status)
    }

    @Test
    fun posseEmDiasCorridos() = rodar { get ->
        val d = posse(get("gerente.exemplo", "/api/cards/posse?unit=corrido"))
        assertEquals("corrido", d.unit)
        assertEquals(4.0, d.tasks.first { it.key == "TG-401" }.posse.first { it.pessoa == "bruno" }.dias, 1e-6)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/posse?unit=semana").status)
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
            val d = posse(get(u, "/api/cards/posse"))
            confere(u, "PS-01", "tasks", d.tasks.size)
            confere(u, "PS-01", "transferidas", d.tasks.count { it.transferencias > 0 })
            for (p in d.pessoas) confere(u, "PS-02", "pessoa:${p.pessoa}", p.tasks)
        }
        // A gaveta de `ana` não abre a pessoa de outro; o responsável de cada linha só aparece se estiver na visão.
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cartoes/PS-02/tasks?segmento=${enc("pessoa:bruno")}").status)
        val g = gaveta(get("ana", "/api/cartoes/PS-02/tasks?segmento=${enc("pessoa:ana")}"))
        assertEquals(listOf("TG-401", "TG-402"), g.linhas.map { it.key })
        assertNull(g.linhas.first { it.key == "TG-401" }.responsavel, "o responsável atual (bruno) está fora da visão da ana")
    }

    @Test
    fun consultaEhAuditadaSemChaveDeIssueNaRota() = rodar { get ->
        val antes = requireNotNull(ctx).contarAccessLog()
        get("gerente.exemplo", "/api/cards/posse")
        get("ana", "/api/cards/posse?pessoa=bruno")
        get("ana", "/api/cartoes/PS-02/tasks?segmento=${enc("pessoa:ana")}")
        assertEquals(antes + 3, requireNotNull(ctx).contarAccessLog())
        val rotas = requireNotNull(ctx?.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route || ' ' || coalesce(filters, '') FROM access_log").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertTrue(rotas.none { Regex("TG-\\d+").containsMatchIn(it) }, rotas.toString())
    }
}
