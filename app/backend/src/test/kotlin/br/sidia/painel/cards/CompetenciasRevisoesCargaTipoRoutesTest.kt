package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.cargatipo.CargaTipoDto
import br.sidia.painel.cards.competencias.CompetenciasDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.revisoes.RevisoesDto
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Competências, Revisões pendentes e Carga por tipo/OS pelo HTTP (CAP-1 mais: TG-204 do `bruno` com `skill:ios`; TG-305, review
 * do `bruno` criada em 26/10 e vencida em 29/10; TG-401 e TG-402 concluídas): visão por papel, sem ranking e drill do mesmo tamanho.
 */
class CompetenciasRevisoesCargaTipoRoutesTest {
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
        val extras = listOf(
            Trb1.issue("TG-204", "Checklist", "bruno", "10-23T12", "Open", vence = "2026-11-09", labels = listOf("skill:ios")),
            Trb1.issue("TG-305", "Checklist Review", "bruno", "10-26T12", "Open", vence = "2026-10-29"),
            Trb1.issue(
                "TG-401", "Checklist", "ana", "10-05T12", "Closed", resolution = "Done", resolvida = "10-20T12", osVersion = "Android 15",
                labels = listOf("TEST-A"), gastoS = 7200, eventos = Trb1.trilha("TG-401", "Open", "10-06T12" to "In Progress", "10-20T12" to "Closed"),
            ),
            Trb1.issue(
                "TG-402", "Checklist Review", "bruno", "10-10T12", "Closed", resolution = "Done", resolvida = "10-22T12",
                eventos = Trb1.trilha("TG-402", "Open", "10-12T12" to "In Progress", "10-22T12" to "Closed"),
            ),
        )
        Trb1.carregar(requireNotNull(c.dataSource), Cap1.issuesParaBanco() + extras)
        rodarTrb1(c, bloco = bloco)
    }

    private fun competencias(r: RespTrb) = Json.decodeFromString<CompetenciasDto>(r.corpo)
    private fun revisoes(r: RespTrb) = Json.decodeFromString<RevisoesDto>(r.corpo)
    private fun cargaTipo(r: RespTrb) = Json.decodeFromString<CargaTipoDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---- Competências ----

    @Test
    fun matrizDoCenarioPeloHttp() = rodar { get ->
        val d = competencias(get("gerente.exemplo", "/api/cards/competencias"))
        assertEquals(listOf("android", "ios", "rede", "ui"), d.competencias)
        assertEquals(listOf("ana", "bruno", "carla"), d.pessoas)
        assertEquals(listOf("ui"), d.pessoaUnica)
        assertEquals(listOf("ios"), d.semNinguem)
        assertEquals(6.0, d.demandaH.getValue("ios"), 1e-9)
        assertEquals(16.8, d.demandaH.getValue("android"), 1e-9)
    }

    @Test
    fun competenciaSoDoVisivel() = rodar { get ->
        val lider = competencias(get("lider.alfa", "/api/cards/competencias"))
        assertEquals(listOf("ana", "bruno"), lider.pessoas)
        // Sem a carla, `rede` é só da ana e `ui` nem aparece.
        assertEquals(listOf("android", "ios", "rede"), lider.competencias)
        assertEquals(listOf("rede"), lider.pessoaUnica)
        val r = get("ana", "/api/cards/competencias")
        val ana = competencias(r)
        assertEquals(listOf("ana"), ana.pessoas)
        assertEquals(emptyList(), ana.pessoaUnica)
        assertEquals(emptyList(), ana.semNinguem)
        assertFalse("bruno" in r.corpo || "carla" in r.corpo || "ios" in r.corpo, r.corpo)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/competencias?pessoa=bruno").status)
    }

    @Test
    fun drillDeCompetenciasTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val d = competencias(get(u, "/api/cards/competencias"))
            val g = gaveta(get(u, "/api/cartoes/CM-02/tasks?segmento=semNinguem"))
            assertEquals(g.total, g.linhas.size, u)
            assertEquals(d.semNinguem.isEmpty(), g.total == 0, "$u: sem competência órfã não há task órfã")
            for (c in d.demandaH.keys) {
                val x = gaveta(get(u, "/api/cartoes/CM-03/tasks?segmento=${enc("skill:$c")}"))
                assertEquals(x.total, x.linhas.size, "$u $c")
                assertTrue(x.total > 0, "$u $c")
            }
        }
        assertEquals(listOf("TG-204"), gaveta(get("gerente.exemplo", "/api/cartoes/CM-02/tasks?segmento=semNinguem")).linhas.map { it.key })
        assertEquals(listOf("TG-201", "TG-202"), gaveta(get("gerente.exemplo", "/api/cartoes/CM-03/tasks?segmento=${enc("skill:android")}")).linhas.map { it.key })
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cartoes/CM-03/tasks?segmento=${enc("skill:ios")}").status)
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/cartoes/CM-01/tasks?segmento=x").status)
    }

    // ---- Revisões ----

    @Test
    fun revisoesDoBrunoPeloHttp() = rodar { get ->
        val d = revisoes(get("gerente.exemplo", "/api/cards/revisoes"))
        val bruno = d.linhas.single()
        assertEquals("bruno", bruno.revisor)
        assertEquals(1, bruno.pendentes)
        assertEquals(1, bruno.vencidas)
        assertEquals(1, bruno.foraDoSla)
        assertEquals(4.0, bruno.idadeP50!!, 1e-9)
    }

    @Test
    fun semRevisorSoCoordenacao() = rodar { get ->
        assertEquals(0, revisoes(get("gerente.exemplo", "/api/cards/revisoes")).semRevisor)
        assertEquals(0, revisoes(get("coord.um", "/api/cards/revisoes")).semRevisor)
        assertNull(revisoes(get("lider.alfa", "/api/cards/revisoes")).semRevisor)
        assertNull(revisoes(get("ana", "/api/cards/revisoes")).semRevisor)
        // Com filtro de squad nem a coordenação recebe a contagem (a review sem dono não pertence a squad).
        assertNull(revisoes(get("gerente.exemplo", "/api/cards/revisoes?squad=Alfa")).semRevisor)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cartoes/RV-02/tasks?segmento=semRevisor").status)
    }

    @Test
    fun usuarioVeSoAsPropriasRevisoes() = rodar { get ->
        val r = get("ana", "/api/cards/revisoes")
        assertEquals(emptyList(), revisoes(r).linhas)
        assertFalse("bruno" in r.corpo, r.corpo)
        val bruno = revisoes(get("bruno", "/api/cards/revisoes"))
        assertEquals(listOf("bruno"), bruno.linhas.map { it.revisor })
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/revisoes?pessoa=bruno").status)
    }

    @Test
    fun drillDeRevisoesTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "bruno")) {
            val d = revisoes(get(u, "/api/cards/revisoes"))
            for (l in d.linhas) {
                assertEquals(l.pendentes, gaveta(get(u, "/api/cartoes/RV-01/tasks?segmento=${enc("revisor:${l.revisor}:pendentes")}")).total, u)
                assertEquals(l.vencidas, gaveta(get(u, "/api/cartoes/RV-01/tasks?segmento=${enc("revisor:${l.revisor}:vencidas")}")).total, u)
                assertEquals(l.foraDoSla, gaveta(get(u, "/api/cartoes/RV-01/tasks?segmento=${enc("revisor:${l.revisor}:foraDoSla")}")).total, u)
            }
        }
        assertEquals(HttpStatusCode.BadRequest, get("ana", "/api/cartoes/RV-01/tasks?segmento=${enc("revisor:bruno:pendentes")}").status)
        assertEquals(0, gaveta(get("gerente.exemplo", "/api/cartoes/RV-02/tasks?segmento=semRevisor")).total)
    }

    // ---- Carga por tipo e OS ----

    @Test
    fun cargaPorTipoEOsPeloHttp() = rodar { get ->
        val d = cargaTipo(get("gerente.exemplo", "/api/cards/carga-tipo"))
        assertEquals(listOf("Checklist", "Checklist Review"), d.tipos)
        assertEquals(listOf("Android 15", ""), d.versoes)
        val c = d.tipos.indexOf("Checklist")
        assertEquals(2, d.abertasN[c][0])
        assertEquals(16.8, d.abertasH[c][0], 1e-9)
        // Sem OS: TG-203 (4 h), TG-204 (6 h assumidas) e TG-205 (6 h assumidas).
        assertEquals(3, d.abertasN[c][1])
        assertEquals(16.0, d.abertasH[c][1], 1e-9)
        // Fechadas: TG-401 (Checklist, Android 15) = 3 (tipo) + 1 teste x 0,5 + 2 h do tempo gasto = 5,5; TG-402 (review, sem OS) = 1.
        assertEquals(1, d.fechadasN[c][0])
        assertEquals(5.5, d.esforco[c][0], 1e-9)
        val r = d.tipos.indexOf("Checklist Review")
        assertEquals(1, d.fechadasN[r][1])
        assertEquals(1.0, d.esforco[r][1], 1e-9)
        assertEquals(1, d.abertasN[r][1]) // TG-305
    }

    @Test
    fun cargaPorTipoSoDaVisao() = rodar { get ->
        val r = get("ana", "/api/cards/carga-tipo")
        val d = cargaTipo(r)
        assertEquals(listOf("Checklist"), d.tipos)
        assertEquals(2, d.abertasN.single().sum())
        assertFalse("Review" in r.corpo, r.corpo)
    }

    @Test
    fun filtrosDeTipoEOsEPeriodo() = rodar { get ->
        assertEquals(listOf("Checklist Review"), cargaTipo(get("gerente.exemplo", "/api/cards/carga-tipo?tipo=${enc("Checklist Review")}")).tipos)
        assertEquals(listOf("Android 15"), cargaTipo(get("gerente.exemplo", "/api/cards/carga-tipo?os=${enc("Android 15")}")).versoes)
        val semFechadas = cargaTipo(get("gerente.exemplo", "/api/cards/carga-tipo?de=2026-10-25"))
        assertEquals(0, semFechadas.fechadasN.sumOf { it.sum() })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/carga-tipo?de=2026-10-25&ate=2026-10-01").status)
    }

    @Test
    fun drillDeCargaTipoTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa", "ana")) {
            val d = cargaTipo(get(u, "/api/cards/carga-tipo"))
            for ((i, t) in d.tipos.withIndex()) for ((j, v) in d.versoes.withIndex()) {
                val a = get(u, "/api/cartoes/CG-01/tasks?segmento=${enc("abertas:tipo:$t:os:$v")}")
                if (d.abertasN[i][j] == 0) assertEquals(HttpStatusCode.BadRequest, a.status, "$u $t/$v") else assertEquals(d.abertasN[i][j], gaveta(a).total, "$u $t/$v")
                val f = get(u, "/api/cartoes/CG-02/tasks?segmento=${enc("fechadas:tipo:$t:os:$v")}")
                if (d.fechadasN[i][j] == 0) assertEquals(HttpStatusCode.BadRequest, f.status, "$u $t/$v") else assertEquals(d.fechadasN[i][j], gaveta(f).total, "$u $t/$v")
            }
        }
        assertEquals(
            listOf("TG-203", "TG-204", "TG-205"),
            gaveta(get("gerente.exemplo", "/api/cartoes/CG-01/tasks?segmento=${enc("abertas:tipo:Checklist:os:")}")).linhas.map { it.key },
        )
        // Um segmento do CG-02 não abre no CG-01.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cartoes/CG-01/tasks?segmento=${enc("fechadas:tipo:Checklist:os:Android 15")}").status)
    }

    @Test
    fun consultasSaoAuditadasESemRanking() = rodar { get ->
        val antes = requireNotNull(ctx).contarAccessLog()
        val corpos = listOf("competencias", "revisoes", "carga-tipo").map { get("gerente.exemplo", "/api/cards/$it") }
        assertEquals(antes + 3, requireNotNull(ctx).contarAccessLog())
        for (r in corpos) for (proibida in listOf("posicao", "ranking", "rank", "maior", "menor", "pior", "melhor")) {
            assertFalse("\"$proibida" in r.corpo.lowercase(), proibida)
        }
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/revisoes").status)
    }
}
