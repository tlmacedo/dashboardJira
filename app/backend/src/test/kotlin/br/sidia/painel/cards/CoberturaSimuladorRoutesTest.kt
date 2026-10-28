package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.cobertura.CoberturaDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.cards.simulador.SimuladorDto
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
import kotlin.test.assertTrue

/** Cobertura e Simulador pelo HTTP (CAP-1): só liderança, visão por papel, parâmetros validados (400), nada gravado, drill do mesmo tamanho. */
class CoberturaSimuladorRoutesTest {
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

    private fun cobertura(r: RespTrb) = Json.decodeFromString<CoberturaDto>(r.corpo)
    private fun simulador(r: RespTrb) = Json.decodeFromString<SimuladorDto>(r.corpo)
    private fun gaveta(r: RespTrb) = Json.decodeFromString<DrillGenericoDto>(r.corpo)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private val bruno = "/api/cards/simulador?pessoa=bruno&de=2026-11-03&ate=2026-11-06"

    // ---- Cobertura ----

    @Test
    fun coberturaDaAnaPeloHttp() = rodar { get ->
        val d = cobertura(get("gerente.exemplo", "/api/cards/cobertura"))
        assertEquals(listOf("ana", "bruno", "carla"), d.ausencias.map { it.pessoa })
        val ana = d.ausencias.first { it.pessoa == "ana" }
        assertEquals(listOf("TG-202"), ana.issues)
        assertEquals(5.6, ana.horasAReplanejar, 1e-9)
        assertEquals(listOf("android"), ana.competencias)
        assertEquals(listOf("bruno"), ana.podemCobrir.map { it.pessoa })
        assertEquals(9.2, ana.podemCobrir.single().folgaH, 1e-9)
        assertTrue(ana.podemCobrir.single().temTodasAsCompetencias)
        assertEquals(30, d.dias)
    }

    @Test
    fun coberturaSoLiderancaEVisaoDoLider() = rodar { get ->
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/cobertura").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/cobertura").status)
        val lider = cobertura(get("lider.alfa", "/api/cards/cobertura"))
        // O líder do Alfa não vê a ausência da carla.
        assertEquals(listOf("ana", "bruno"), lider.ausencias.map { it.pessoa })
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cartoes/CB-01/tasks?segmento=${enc("ausencia:ana:2026-11-04")}").status)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/cobertura?pessoa=carla").status)
    }

    @Test
    fun horizonteForaDoIntervalo400() = rodar { get ->
        for (dias in listOf("0", "91", "-1", "abc", "1.5", "99999999999")) {
            assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/cobertura?dias=$dias").status, dias)
        }
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "/api/cards/cobertura?dias=90").status)
        assertEquals(listOf("carla"), cobertura(get("gerente.exemplo", "/api/cards/cobertura?dias=4")).ausencias.map { it.pessoa })
    }

    @Test
    fun ausenciasSemMotivoNaResposta() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/cobertura")
        assertFalse("motivo" in r.corpo.lowercase(), r.corpo)
    }

    @Test
    fun drillDaCoberturaTemOMesmoTamanhoDoCard() = rodar { get ->
        for (u in listOf("gerente.exemplo", "coord.um", "lider.alfa")) {
            val d = cobertura(get(u, "/api/cards/cobertura"))
            for (a in d.ausencias) {
                val g = gaveta(get(u, "/api/cartoes/CB-01/tasks?segmento=${enc("ausencia:${a.pessoa}:${a.de}")}"))
                assertEquals(a.issues.size, g.total, "$u ${a.pessoa}")
                assertEquals(a.issues, g.linhas.map { it.key }, "$u ${a.pessoa}")
            }
        }
        assertEquals(HttpStatusCode.BadRequest, get("lider.alfa", "/api/cartoes/CB-01/tasks?segmento=${enc("ausencia:carla:2026-11-02")}").status)
    }

    // ---- Simulador ----

    @Test
    fun simuladorCausaAtrasoPeloHttp() = rodar { get ->
        val r = get("gerente.exemplo", bruno)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = simulador(r)
        assertEquals(listOf("TG-203"), d.atrasosCausados)
        assertEquals(1.2, d.horasAReplanejar, 1e-9)
        assertEquals(listOf("2026-10-26", "2026-11-02"), d.semanas.map { it.semana })
        assertTrue(d.semanas.last().depois.semCapacidade)
        // A liderança do squad do bruno também simula.
        assertEquals(listOf("TG-203"), simulador(get("lider.alfa", bruno)).atrasosCausados)
    }

    @Test
    fun simuladorSemEfeito() = rodar { get ->
        val d = simulador(get("gerente.exemplo", "/api/cards/simulador?pessoa=ana&de=2026-11-16&ate=2026-11-18&fracao=0.5"))
        assertEquals(emptyList(), d.atrasosCausados)
        assertEquals(0.0, d.horasAReplanejar, 1e-9)
        assertEquals(0.5, d.fracao)
    }

    @Test
    fun simuladorSemParametrosDevolveFormularioVazioComAviso() = rodar { get ->
        val r = get("gerente.exemplo", "/api/cards/simulador")
        assertEquals(HttpStatusCode.OK, r.status)
        val d = simulador(r)
        assertEquals(emptyList(), d.atrasosCausados)
        assertTrue(d.avisos.any { it.codigo == "SIMULACAO_SEM_PARAMETROS" }, r.corpo)
    }

    @Test
    fun simuladorPeriodoLongo400() = rodar { get ->
        // 60 dias passa; 61 dias é 400.
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "/api/cards/simulador?pessoa=bruno&de=2026-11-01&ate=2026-12-30").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/simulador?pessoa=bruno&de=2026-11-01&ate=2026-12-31").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/simulador?pessoa=bruno&de=2026-01-01&ate=2027-12-31").status)
    }

    @Test
    fun simuladorParametrosRuinsSao400SemEco() = rodar { get ->
        val ruins = listOf(
            "pessoa=bruno", // sem de/ate
            "pessoa=bruno&de=2026-11-03", // sem ate
            "pessoa=bruno&de=2026-11-06&ate=2026-11-03", // invertido
            "pessoa=bruno&de=2026-11-03&ate=2026-11-06&fracao=0.3",
            "pessoa=bruno&de=2026-11-03&ate=2026-11-06&fracao=abc",
            "pessoa=bruno&de=2026-11-03&ate=2026-11-06&fracao=2",
            "pessoa=bruno&de=2026-10-01&ate=2026-10-20", // tudo no passado
            "pessoa=bruno&de=2027-06-01&ate=2027-06-10", // além do horizonte da agenda
            "pessoa=bruno&de=nao-e-data&ate=2026-11-06",
            "pessoa=bruno&de=%2B999999999-12-31&ate=2026-11-06",
            "pessoa=zzz.desconhecido&de=2026-11-03&ate=2026-11-06", // fora da equipe cadastrada
        )
        for (q in ruins) {
            val r = get("gerente.exemplo", "/api/cards/simulador?$q")
            assertEquals(HttpStatusCode.BadRequest, r.status, q)
            assertFalse("zzz.desconhecido" in r.corpo || "nao-e-data" in r.corpo, q)
        }
    }

    @Test
    fun simuladorPessoaForaDoVisivel403() = rodar { get ->
        // O líder do Alfa simulando a carla (Beta); e o usuário comum nem chega ao simulador.
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/simulador?pessoa=carla&de=2026-11-03&ate=2026-11-06").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", bruno).status)
        // Nem simulando a si mesma: o simulador é da liderança, não do USUARIO.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/simulador?pessoa=ana&de=2026-11-03&ate=2026-11-06").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/simulador").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cartoes/SI-01/tasks?segmento=atrasosCausados").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, bruno).status)
    }

    @Test
    fun simuladorNaoGrava() = rodar { get ->
        val c = requireNotNull(ctx)
        val ausenciasAntes = Cap1.ausencias.toList()
        fun fotografia(): Map<String, List<List<String?>>> = c.dataSource!!.connection.use { con ->
            val tabelas = con.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'flyway%' AND name <> 'access_log' ORDER BY name",
            ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            tabelas.associateWith { t ->
                con.createStatement().executeQuery("SELECT * FROM $t").use { rs ->
                    buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
                }
            }
        }
        val antes = fotografia()
        repeat(3) { get("gerente.exemplo", bruno) }
        get("gerente.exemplo", "/api/cards/cobertura")
        assertEquals(antes, fotografia(), "alguma tabela mudou depois de simular")
        assertEquals(ausenciasAntes, Cap1.ausencias.toList())
        assertEquals(ausenciasAntes, c.ausencias.toList())
    }

    @Test
    fun drillDoSimuladorTemOMesmoTamanhoDoCard() = rodar { get ->
        val d = simulador(get("gerente.exemplo", bruno))
        val g = gaveta(get("gerente.exemplo", "/api/cartoes/SI-01/tasks?segmento=atrasosCausados&pessoa=bruno&de=2026-11-03&ate=2026-11-06"))
        assertEquals(d.atrasosCausados.size, g.total)
        assertEquals(d.atrasosCausados, g.linhas.map { it.key })
        // Sem parâmetros do formulário, o número é zero e a gaveta também.
        assertEquals(0, gaveta(get("gerente.exemplo", "/api/cartoes/SI-01/tasks?segmento=atrasosCausados")).total)
    }

    @Test
    fun consultaEhAuditadaInclusiveNegada() = rodar { get ->
        val antes = requireNotNull(ctx).contarAccessLog()
        get("gerente.exemplo", "/api/cards/cobertura")
        get("gerente.exemplo", bruno)
        get("ana", bruno)
        get("gerente.exemplo", "/api/cards/simulador?pessoa=bruno&de=ruim&ate=2026-11-06")
        assertEquals(antes + 4, requireNotNull(ctx).contarAccessLog())
    }
}
