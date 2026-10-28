package br.sidia.painel.cards.riscos

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.riscos.FatorDeRisco
import br.sidia.painel.riscos.FatorDeRisco.BLOQUEADA
import br.sidia.painel.riscos.FatorDeRisco.ESTOURO_DE_HORAS
import br.sidia.painel.riscos.FatorDeRisco.IDADE_ACIMA_P50
import br.sidia.painel.riscos.FatorDeRisco.IDADE_ACIMA_P85
import br.sidia.painel.riscos.FatorDeRisco.PARADA
import br.sidia.painel.riscos.FatorDeRisco.PREVISAO_APOS_VENCIMENTO
import br.sidia.painel.riscos.FatorDeRisco.REABERTA
import br.sidia.painel.riscos.FatorDeRisco.SEM_ATUALIZACAO
import br.sidia.painel.riscos.FatorDeRisco.SEM_ESTIMATIVA
import br.sidia.painel.riscos.FatorDeRisco.VENCE_EM_BREVE
import br.sidia.painel.riscos.FatorDeRisco.VENCIDA
import br.sidia.painel.riscos.RegrasDeRisco
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pontos de risco por fator sobre o cenário TRB-1 (hoje = sexta 30/10; histórico = as quatro concluídas positivas). */
class RiscoCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val regras = RegrasDeRisco.PADRAO

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        Trb1.carregar(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun abertas(): List<IssueSnapshot> =
        SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
    private fun historico(): List<IssueSnapshot> =
        SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS))

    private fun avaliar(abertas: List<IssueSnapshot> = abertas(), previsao: PrevisaoDeTermino = PrevisaoDeTermino { null }) =
        RiscoCalculator(Trb1.calendario, Trb1.statusMap, regras, previsao).avaliar(abertas, historico(), Trb1.agora).associateBy { it.key }

    @Test
    fun riscoDoCenario() {
        val r = avaliar()
        assertEquals(4, r.size)
        val t101 = r.getValue("TG-101")
        assertEquals(85, t101.pontos)
        assertEquals("alto", t101.nivel)
        assertEquals(
            setOf(IDADE_ACIMA_P85 to 30, PARADA to 15, VENCIDA to 25, SEM_ATUALIZACAO to 10, SEM_ESTIMATIVA to 5), t101.fatores.toSet(),
        )
        // Do maior para o menor ponto.
        assertEquals(t101.fatores.map { it.second }.sortedDescending(), t101.fatores.map { it.second })
        val t103 = r.getValue("TG-103")
        assertEquals(63, t103.pontos)
        assertEquals("alto", t103.nivel)
        assertEquals(
            setOf(IDADE_ACIMA_P50 to 10, PARADA to 15, BLOQUEADA to 15, VENCE_EM_BREVE to 8, ESTOURO_DE_HORAS to 15), t103.fatores.toSet(),
        )
        // Em Open não conta como parada; a idade (45 corridos) passa do P85 do tipo Review.
        val t102 = r.getValue("TG-102")
        assertEquals(30, t102.pontos)
        assertEquals("medio", t102.nivel)
        assertEquals(listOf(IDADE_ACIMA_P85 to 30), t102.fatores)
        val t104 = r.getValue("TG-104")
        assertEquals(5, t104.pontos)
        assertEquals("baixo", t104.nivel)
        assertEquals(listOf(SEM_ESTIMATIVA to 5), t104.fatores)
    }

    @Test
    fun fatoresExclusivos() {
        val r = avaliar()
        // Vencida (TG-101) não soma "vence em breve" nem "previsão depois do vencimento".
        val f101 = r.getValue("TG-101").fatores.map { it.first }
        assertTrue(VENCE_EM_BREVE !in f101 && PREVISAO_APOS_VENCIMENTO !in f101)
        // Idade: P85 e P50 nunca juntos.
        for (risco in r.values) {
            val f = risco.fatores.map { it.first }
            assertTrue(!(IDADE_ACIMA_P85 in f && IDADE_ACIMA_P50 in f), risco.key)
        }
        // Mesmo com previsão depois do vencimento, a vencida fica só com VENCIDA.
        val comPrevisao = avaliar { LocalDate.parse("2026-11-20") }
        val f = comPrevisao.getValue("TG-101").fatores.map { it.first }
        assertTrue(VENCIDA in f && PREVISAO_APOS_VENCIMENTO !in f)
    }

    @Test
    fun previsaoAposVencimentoSoComPrevisao() {
        // Sem previsão (a única implementação por ora): nunca aparece.
        assertTrue(avaliar().values.none { r -> r.fatores.any { it.first == PREVISAO_APOS_VENCIMENTO } })
        // Com uma previsão para 05/11 e vencimento 02/11, entra no lugar de "vence em breve".
        val r = avaliar { key -> if (key == "TG-103") LocalDate.parse("2026-11-05") else null }
        val t103 = r.getValue("TG-103")
        assertEquals(setOf(IDADE_ACIMA_P50 to 10, PARADA to 15, BLOQUEADA to 15, PREVISAO_APOS_VENCIMENTO to 20, ESTOURO_DE_HORAS to 15), t103.fatores.toSet())
        assertEquals(75, t103.pontos)
        // Previsão no próprio vencimento ou antes não pune.
        val naData = avaliar { key -> if (key == "TG-103") LocalDate.parse("2026-11-02") else null }
        assertTrue(PREVISAO_APOS_VENCIMENTO !in naData.getValue("TG-103").fatores.map { it.first })
    }

    @Test
    fun semHistoricoDoTipoNaoPontuaIdade() {
        val r = RiscoCalculator(Trb1.calendario, Trb1.statusMap, regras).avaliar(abertas(), emptyList(), Trb1.agora).associateBy { it.key }
        for (risco in r.values) {
            assertTrue(risco.fatores.none { it.first == IDADE_ACIMA_P85 || it.first == IDADE_ACIMA_P50 }, risco.key)
        }
        // Sem mediana por status, a parada usa o mínimo de 3 dias úteis (TG-101 está em In Progress há mais).
        assertTrue(PARADA in r.getValue("TG-101").fatores.map { it.first })
    }

    @Test
    fun semCamposDeTrabalhoNaoPunePorFaltaDeEstimativa() {
        val r = RiscoCalculator(Trb1.calendario, Trb1.statusMap, regras).avaliar(abertas(), historico(), Trb1.agora, camposDeTrabalho = false)
            .associateBy { it.key }
        assertTrue(r.values.none { x -> x.fatores.any { it.first == SEM_ESTIMATIVA } })
        assertEquals(listOf(), r.getValue("TG-104").fatores)
    }

    @Test
    fun reabertaMuitasTrocasEPontuacaoMaximaDe100() {
        val extra = Trb1.issue(
            "TG-170", "Checklist", "ana", "09-01T12", "In Progress", vence = "2026-10-20", atualizada = "10-01T12",
            flagged = true,
            eventos = Trb1.trilha("TG-170", "Open", "09-02T12" to "In Progress", "09-10T12" to "Closed", "09-12T12" to "In Progress") +
                Trb1.ev("TG-170", "assignee", "ana", "bruno", "09-03T12") + Trb1.ev("TG-170", "assignee", "bruno", "carla", "09-04T12") +
                Trb1.ev("TG-170", "assignee", "carla", "ana", "09-05T12"),
        )
        Trb1.carregar(ds, listOf(extra))
        val t = avaliar().getValue("TG-170")
        val f = t.fatores.map { it.first }
        assertTrue(REABERTA in f && FatorDeRisco.MUITAS_TROCAS in f && BLOQUEADA in f && VENCIDA in f, f.toString())
        // Soma bruta: 30 + 15 + 25 + 15 + 10 + 10 + 10 + 5 = 120; o total é limitado a 100.
        assertEquals(100, t.pontos)
        assertEquals("alto", t.nivel)
        assertNull(avaliar().values.firstOrNull { it.pontos > 100 })
    }
}
