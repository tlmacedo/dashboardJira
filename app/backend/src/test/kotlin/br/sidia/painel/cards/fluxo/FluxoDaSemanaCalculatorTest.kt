package br.sidia.painel.cards.fluxo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fluxo da semana (I-01) sobre cenários pequenos e fictícios. Relógio do TRB-1: sexta 30/10/2026, então as 12 semanas
 * vão de 10/08 a 26/10 (a última é a semana em curso) e a última FECHADA é a de 19/10.
 */
class FluxoDaSemanaCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val hoje: LocalDate = LocalDate.parse("2026-10-30")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun todas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS))
    private fun analisar() = FluxoDaSemanaCalculator(Trb1.calendario, Trb1.statusMap).analisar(todas(), hoje)
    private fun semana(a: FluxoDaSemanaAnalise, segunda: String) = a.dto.semanas.single { it.semana == segunda }
    private fun chaves(a: FluxoDaSemanaAnalise, segmento: String) = a.segmentos.getValue(segmento).map { it.key }

    private fun entra(chave: String, criada: String, resp: String = "ana") = Trb1.issue(chave, "Checklist", resp, criada, "Open")

    private fun sai(chave: String, criada: String, resolvida: String, resp: String = "ana") = Trb1.issue(
        chave, "Checklist", resp, criada, "Closed", resolution = "Done", resolvida = resolvida,
        eventos = Trb1.trilha(chave, "Open", resolvida to "Closed"),
    )

    @Test
    fun dozeSemanasDeSegundaADomingoComAEmCursoMarcada() {
        Trb1.carregar(ds, listOf(entra("TG-501", "10-05T12")))
        val a = analisar()
        assertEquals(12, a.dto.semanas.size)
        assertEquals("2026-08-10", a.dto.semanas.first().semana)
        assertEquals("2026-10-26", a.dto.semanas.last().semana)
        assertEquals(listOf(true), a.dto.semanas.map { it.emCurso }.filter { it })
        assertTrue(a.dto.semanas.last().emCurso)
        assertEquals(12, a.dto.semanasNaJanela)
    }

    @Test
    fun semanaDeSegundaADomingoEmManaus() {
        // 12/10 03:00Z = domingo 11/10 23:00 em Manaus (semana de 05/10); 04:00Z = segunda 00:00 (semana de 12/10).
        Trb1.carregar(
            ds,
            listOf(entra("TG-503", "10-12T03"), entra("TG-504", "10-12T04"), sai("TG-509", "10-01T12", "10-12T03"), sai("TG-510", "10-01T12", "10-12T04")),
        )
        val a = analisar()
        assertEquals(listOf("TG-503"), chaves(a, "entrada:2026-10-05").filter { it == "TG-503" })
        assertEquals(listOf("TG-504"), chaves(a, "entrada:2026-10-12").filter { it == "TG-504" })
        assertEquals(listOf("TG-509"), chaves(a, "saida:2026-10-05"))
        assertEquals(listOf("TG-510"), chaves(a, "saida:2026-10-12"))
        assertEquals(listOf(1, 1), listOf(semana(a, "2026-10-05").entrou, semana(a, "2026-10-12").entrou))
        assertEquals(listOf(1, 1), listOf(semana(a, "2026-10-05").saiu, semana(a, "2026-10-12").saiu))
    }

    @Test
    fun reabertaNaoContaComoNovaEntrada() {
        // Criada em 30/09, fechada em 07/10 e reaberta em 14/10: a reabertura não é uma entrada da semana de 12/10.
        val reaberta = Trb1.issue(
            "TG-505", "Checklist", "ana", "09-30T12", "Open",
            eventos = Trb1.trilha("TG-505", "Open", "10-07T12" to "Closed", "10-14T12" to "Open"),
        )
        Trb1.carregar(ds, listOf(reaberta))
        val a = analisar()
        assertEquals(listOf("TG-505"), chaves(a, "entrada:2026-09-28"))
        assertEquals(0, semana(a, "2026-10-12").entrou)
        assertEquals(1, a.dto.entrou)
    }

    @Test
    fun issueRemovidaSaiDaSerie() {
        Trb1.carregar(ds, listOf(entra("TG-507", "10-06T12"), entra("TG-508", "10-06T12")))
        assertEquals(2, analisar().dto.entrou)
        ds.connection.use { c ->
            c.prepareStatement("UPDATE issue SET removida_em = ? WHERE key = ?").use { ps ->
                ps.setTimestamp(1, Timestamp.from(Instant.parse("2026-10-20T12:00:00Z")))
                ps.setString(2, "TG-507")
                ps.executeUpdate()
            }
        }
        val a = analisar()
        assertEquals(1, a.dto.entrou)
        assertEquals(listOf("TG-508"), chaves(a, "entradas"))
    }

    @Test
    fun canceladaVaiParaSerieAparte() {
        val cancelada = Trb1.issue(
            "TG-506", "Checklist", "ana", "10-06T12", "Closed", resolution = "Won't Do", resolvida = "10-20T12",
            eventos = Trb1.trilha("TG-506", "Open", "10-20T12" to "Closed"),
        )
        Trb1.carregar(ds, listOf(cancelada, sai("TG-511", "10-06T12", "10-21T12")))
        val a = analisar()
        val s = semana(a, "2026-10-19")
        assertEquals(1, s.canceladas)
        assertEquals(1, s.saiu, "só a entrega conta como saída")
        assertEquals(listOf("TG-506"), chaves(a, "cancelada:2026-10-19"))
        assertEquals(listOf("TG-511"), chaves(a, "saida:2026-10-19"))
        // O saldo desconta a saída e a cancelada: 2 entradas - 1 saída - 1 cancelada.
        assertEquals(0, a.dto.saldoDoBacklog)
    }

    @Test
    fun iniciadaUsaPrimeiraEntradaEmStatusAtivo() {
        // Entra em andamento em 14/10, volta a Open e entra de novo em 21/10: a iniciada é a de 12/10.
        val idaEVolta = Trb1.issue(
            "TG-512", "Checklist", "ana", "10-01T12", "In Progress",
            eventos = Trb1.trilha("TG-512", "Open", "10-14T12" to "In Progress", "10-15T12" to "Open", "10-21T12" to "In Progress"),
        )
        // Nasce em andamento (status inicial ativo): inicia na criação, 06/10.
        val nasceAtiva = Trb1.issue("TG-513", "Checklist", "bruno", "10-06T12", "In Progress")
        // Nunca andou: não é iniciada.
        Trb1.carregar(ds, listOf(idaEVolta, nasceAtiva, entra("TG-514", "10-07T12")))
        val a = analisar()
        assertEquals(listOf("TG-512"), chaves(a, "inicio:2026-10-12"))
        assertEquals(emptyList(), chaves(a, "inicio:2026-10-19"))
        assertEquals(listOf("TG-513"), chaves(a, "inicio:2026-10-05"))
        assertEquals(2, a.dto.comecou)
        assertEquals(listOf("TG-512", "TG-513"), chaves(a, "iniciadas"))
    }

    private fun cenarioDeEntradaESaida(porSemana: Map<String, Pair<Int, Int>>): List<Trb1.IssueDeTeste> {
        var n = 600
        return porSemana.flatMap { (segunda, par) ->
            (1..par.first).map { entra("TG-${n++}", "${segunda}T12") } +
                (1..par.second).map { sai("TG-${n++}", "07-01T12", "${segunda}T13") }
        }
    }

    @Test
    fun sinalBacklogCrescendoAposTresSemanas() {
        // Entrada > saída em 05/10, 12/10 e 19/10 (as 3 últimas fechadas); 28/09 não conta. A semana em curso fica de fora.
        Trb1.carregar(
            ds,
            cenarioDeEntradaESaida(
                mapOf("09-28" to (1 to 2), "10-05" to (3 to 1), "10-12" to (2 to 0), "10-19" to (4 to 2), "10-26" to (0 to 5)),
            ),
        )
        val a = analisar()
        assertEquals(3, a.dto.semanasCrescendo)
        assertTrue(a.dto.backlogCrescendo)
    }

    @Test
    fun duasSemanasOuIgualdadeNaoAcendemOSinal() {
        Trb1.carregar(ds, cenarioDeEntradaESaida(mapOf("10-05" to (1 to 3), "10-12" to (2 to 0), "10-19" to (4 to 2))))
        val a = analisar()
        assertEquals(2, a.dto.semanasCrescendo)
        assertFalse(a.dto.backlogCrescendo)
        // Entrada igual à saída quebra a sequência (a conta é "supera", estritamente).
        val b = analisar2(mapOf("10-05" to (3 to 1), "10-12" to (2 to 2), "10-19" to (4 to 2)))
        assertEquals(1, b.dto.semanasCrescendo)
        assertFalse(b.dto.backlogCrescendo)
    }

    private fun analisar2(porSemana: Map<String, Pair<Int, Int>>): FluxoDaSemanaAnalise {
        ds.connection.use { it.createStatement().execute("DELETE FROM issue") }
        Trb1.carregar(ds, cenarioDeEntradaESaida(porSemana))
        return analisar()
    }

    @Test
    fun amostraPequenaNaJanelaGeraAvisoInformativo() {
        Trb1.carregar(ds, listOf(entra("TG-515", "10-06T12"), entra("TG-516", "10-07T12")))
        val a = analisar()
        assertEquals(2, a.dto.tasksNaJanela)
        val aviso = a.dto.avisos.single { it.codigo == AvisosDoFluxo.AMOSTRA_PEQUENA_NA_JANELA }
        assertEquals("informativo", aviso.gravidade)
        assertEquals("2", aviso.params["n"])
        assertEquals("5", aviso.params["minimo"])
        // Com amostra suficiente o aviso some.
        Trb1.carregar(ds, (1..5).map { entra("TG-52$it", "10-08T12") })
        assertTrue(analisar().dto.avisos.none { it.codigo == AvisosDoFluxo.AMOSTRA_PEQUENA_NA_JANELA })
    }
}
