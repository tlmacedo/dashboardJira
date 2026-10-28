package br.sidia.painel.cards.negligenciadas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** As seis listas de Negligenciadas e o último movimento real, sobre o cenário TRB-1. */
class NegligenciadasCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val calc = NegligenciadasCalculator(Trb1.calendario, Trb1.statusMap)

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
        SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, Trb1.t("07-01T00")))

    private fun chaves(m: Map<String, List<IssueSnapshot>>, kind: String) = m.getValue(kind).map { it.key }

    @Test
    fun negligenciadasSeparadas() {
        val r = calc.calcular(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        assertEquals(KindsDeNegligenciada.TODOS, r.keys.toList())
        assertEquals(listOf("TG-101"), chaves(r, "semAtualizacao"))
        assertEquals(listOf("TG-104"), chaves(r, "semResponsavel"))
        assertEquals(listOf("TG-101"), chaves(r, "vencidas"))
        assertEquals(listOf("TG-103"), chaves(r, "bloqueadas"))
        // Limite de In Progress: max(3, 2 x 5,0) = 10 úteis; On Hold sem mediana: 3 úteis.
        assertEquals(listOf("TG-101", "TG-103"), chaves(r, "paradas"))
        assertEquals(listOf("TG-102"), chaves(r, "aFazer"))
        // "Sem atualização" e "Paradas" são listas separadas: a mesma task está nas duas.
        assertTrue("TG-101" in chaves(r, "semAtualizacao") && "TG-101" in chaves(r, "paradas"))
    }

    @Test
    fun paradaSoConsideraEmAndamento() {
        // TG-102 está há 45 dias em Open (muito mais que 3): como não está em andamento, não é "parada".
        val r = calc.calcular(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        assertTrue("TG-102" !in chaves(r, "paradas"))
        assertTrue("TG-102" !in chaves(r, "semAtualizacao"))
    }

    @Test
    fun limitesVemDoConstrutor() {
        val frouxo = NegligenciadasCalculator(Trb1.calendario, Trb1.statusMap, diasSemAtualizacao = 30, diasAFazer = 60, minParadaDias = 100.0)
        val r = frouxo.calcular(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        assertEquals(emptyList(), chaves(r, "semAtualizacao"))
        assertEquals(emptyList(), chaves(r, "aFazer"))
        assertEquals(emptyList(), chaves(r, "paradas"))
    }

    @Test
    fun semHistoricoSoOMinimoDeTresDias() {
        val r = calc.calcular(abertas(), emptyList(), Trb1.agora, TimeUnitMode.UTIL)
        // Sem mediana, ambos passam do mínimo de 3 úteis.
        assertEquals(listOf("TG-101", "TG-103"), chaves(r, "paradas"))
    }

    @Test
    fun ultimoMovimentoRealIgnoraOUpdatedDoJira() {
        val todas = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).associateBy { it.key }
        // TG-102 tem `updated` de 29/10, mas nenhum evento: o último movimento real é a criação.
        val tg102 = Fluxo.ultimoMovimentoReal(todas.getValue("TG-102"))
        assertEquals("criacao", tg102.campo)
        assertEquals(Trb1.t("09-15T12"), tg102.em)
        // TG-101: última mudança foi o status (01/10), embora o updated seja de 20/10.
        assertEquals("status" to Trb1.t("10-01T12"), Fluxo.ultimoMovimentoReal(todas.getValue("TG-101")).let { it.campo to it.em })
        // TG-111: o vencimento mudou em 07/10 e o status em 09/10: vale o mais recente.
        assertEquals("status" to Trb1.t("10-09T12"), Fluxo.ultimoMovimentoReal(todas.getValue("TG-111")).let { it.campo to it.em })
    }

    @Test
    fun movimentoSoContaOsCamposDeMovimento() {
        val i = Trb1.issue(
            "TG-195", "Checklist", "ana", "10-01T12", "Open",
            eventos = listOf(Trb1.ev("TG-195", "status", "Open", "In Progress", "10-02T12"), Trb1.ev("TG-195", "description", null, null, "10-28T12")),
        )
        Trb1.carregar(ds, listOf(i))
        val s = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).first { it.key == "TG-195" }
        assertEquals("status", Fluxo.ultimoMovimentoReal(s).campo)
        assertEquals(Trb1.t("10-02T12"), Fluxo.ultimoMovimentoReal(s).em)
    }
}
