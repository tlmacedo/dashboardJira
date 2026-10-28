package br.sidia.painel.trabalho

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A série semanal única (entradas por criação, saídas por conclusão positiva, canceladas à parte) sobre o TRB-1. */
class SerieSemanalTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource

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

    private fun todas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS))
    private val ultima = LocalDate.parse("2026-10-26")

    @Test
    fun saidasPorConclusaoPositivaECanceladasAParte() {
        val s = SerieSemanal.calcular(todas(), Trb1.statusMap, ultima, 4, Trb1.zona)
        assertEquals(listOf("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"), s.map { it.semana.toString() })
        assertEquals(listOf(1, 2, 1, 0), s.map { it.saidas.size })
        assertEquals(listOf(listOf("TG-111"), listOf("TG-112", "TG-114"), listOf("TG-113"), emptyList()), s.map { w -> w.saidas.map { it.key }.sorted() })
        // TG-115 (Won't Do, 22/10) é cancelada, não entrega; TG-116 (Closed sem resolução) não é nenhuma das duas.
        assertEquals(listOf(0, 0, 1, 0), s.map { it.canceladas.size })
        assertEquals("TG-115", s[2].canceladas.single().key)
        assertTrue(s.flatMap { it.saidas + it.canceladas }.none { it.key == "TG-116" })
    }

    @Test
    fun entradasPorCriacao() {
        val s = SerieSemanal.calcular(todas(), Trb1.statusMap, ultima, 8, Trb1.zona)
        val entradas = s.associate { it.semana.toString() to it.entradas.map { i -> i.key }.sorted() }
        assertEquals(listOf("TG-102"), entradas.getValue("2026-09-14"))
        assertEquals(listOf("TG-101", "TG-111", "TG-112", "TG-116"), entradas.getValue("2026-09-28"))
        assertEquals(listOf("TG-113", "TG-114", "TG-115"), entradas.getValue("2026-10-05"))
        assertEquals(emptyList(), entradas.getValue("2026-10-12"))
        assertEquals(listOf("TG-103"), entradas.getValue("2026-10-19"))
        assertEquals(listOf("TG-104"), entradas.getValue("2026-10-26"))
    }

    @Test
    fun mesmaSerieQueOFluxoPorContagem() {
        // A Previsão e o 17b leem a MESMA conta: as contagens batem com as funções do Fluxo (regressão da extração).
        val issues = todas()
        val s = SerieSemanal.calcular(issues, Trb1.statusMap, ultima, 4, Trb1.zona)
        val positivas = issues.filter { Fluxo.concluidaPositiva(it, Trb1.statusMap) }
        assertEquals(Fluxo.throughputSemanal(positivas, ultima, 4, Trb1.zona), s.map { it.semana to it.saidas.size })
        assertEquals(Fluxo.chegadasSemanais(issues, ultima, 4, Trb1.zona), s.map { it.semana to it.entradas.size })
    }

    @Test
    fun semanaDeSegundaADomingoDeManaus() {
        // 19/10 03:30Z = domingo 18/10 23:30 em Manaus: ainda é a semana de 12/10.
        val domingoNaNoite = Trb1.issue("TG-180", "Checklist", "ana", "10-19T03", "Closed", resolution = "Done", resolvida = "10-19T03")
        Trb1.carregar(ds, listOf(domingoNaNoite))
        val s = SerieSemanal.calcular(todas(), Trb1.statusMap, ultima, 4, Trb1.zona)
        assertTrue(s[1].saidas.any { it.key == "TG-180" } && s[1].entradas.any { it.key == "TG-180" }, "deveria cair na semana de 12/10")
        assertTrue(s[2].saidas.none { it.key == "TG-180" })
    }

    @Test
    fun ultimaSemanaFechada() {
        assertEquals(LocalDate.parse("2026-10-19"), SerieSemanal.ultimaSemanaFechada(LocalDate.parse("2026-10-30"))) // sexta
        assertEquals(LocalDate.parse("2026-10-19"), SerieSemanal.ultimaSemanaFechada(LocalDate.parse("2026-10-26"))) // segunda
        assertEquals(LocalDate.parse("2026-10-19"), SerieSemanal.ultimaSemanaFechada(LocalDate.parse("2026-11-01"))) // domingo
    }

    @Test
    fun issueRepetidaContaUmaVez() {
        val issues = todas()
        val duplicado = SerieSemanal.calcular(issues + issues, Trb1.statusMap, ultima, 4, Trb1.zona)
        assertEquals(listOf(1, 2, 1, 0), duplicado.map { it.saidas.size })
    }
}
