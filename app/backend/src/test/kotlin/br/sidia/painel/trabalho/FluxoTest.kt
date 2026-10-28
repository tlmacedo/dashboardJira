package br.sidia.painel.trabalho

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.UnmappedStatusException
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Métricas transversais sobre o cenário TRB-1 (SQLite real em arquivo temporário). */
class FluxoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private lateinit var work: SqliteWorkStore
    private val map = Trb1.statusMap
    private val cal = Trb1.calendario
    private val agora = Trb1.agora

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        work = SqliteWorkStore(ds)
        Trb1.carregar(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun todas(): Map<String, IssueSnapshot> =
        work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).associateBy { it.key }

    private fun s(chave: String) = todas().getValue(chave)

    @Test
    fun concluidaExigeResolution() {
        assertTrue(Fluxo.concluidaPositiva(s("TG-111"), map))
        val sem = s("TG-116")
        assertFalse(Fluxo.concluidaPositiva(sem, map))
        assertTrue(Fluxo.divergenteDoneResolution(sem, map))
        assertFalse(Fluxo.divergenteDoneResolution(s("TG-111"), map))
        assertFalse(Fluxo.concluidaPositiva(s("TG-101"), map), "aberta não é concluída")
    }

    @Test
    fun canceladaPelaResolucaoNegativa() {
        assertTrue(Fluxo.cancelada(s("TG-115"), map))
        assertFalse(Fluxo.concluidaPositiva(s("TG-115"), map), "cancelada não conta como entrega")
        assertFalse(Fluxo.cancelada(s("TG-111"), map))
        assertFalse(Fluxo.cancelada(s("TG-116"), map))
    }

    @Test
    fun doneSemResolvedNemResolution() {
        val i = Trb1.issue(
            "TG-190", "Checklist", "ana", "10-03T12", "Closed",
            eventos = Trb1.trilha("TG-190", "Open", "10-05T12" to "In Progress", "10-12T12" to "Closed"),
        )
        Trb1.carregar(ds, listOf(i))
        val v = s("TG-190")
        assertNull(v.resolved)
        assertEquals(Trb1.t("10-12T12"), Fluxo.entrouEmDone(v, map))
        assertTrue(Fluxo.divergenteDoneResolution(v, map))
        assertFalse(Fluxo.concluidaPositiva(v, map))
        assertFalse(Fluxo.emAndamento(v, map), "categoria DONE não está em andamento")
        // A concluída de verdade também tem a data da última entrada em DONE.
        assertEquals(Trb1.t("10-09T12"), Fluxo.entrouEmDone(s("TG-111"), map))
        assertNull(Fluxo.entrouEmDone(s("TG-101"), map))
    }

    @Test
    fun cicloEmUteisECorridos() {
        assertEquals(6.0, Fluxo.cicloDias(s("TG-112"), map, cal, TimeUnitMode.UTIL)!!, 1e-9)
        assertEquals(9.0, Fluxo.cicloDias(s("TG-112"), map, cal, TimeUnitMode.CORRIDO)!!, 1e-9)
        assertEquals(4.0, Fluxo.cicloDias(s("TG-111"), map, cal, TimeUnitMode.UTIL)!!, 1e-9)
        assertEquals(8.0, Fluxo.cicloDias(s("TG-113"), map, cal, TimeUnitMode.UTIL)!!, 1e-9)
        assertEquals(1.0, Fluxo.cicloDias(s("TG-114"), map, cal, TimeUnitMode.UTIL)!!, 1e-9)
        assertEquals(13.0, Fluxo.cicloDias(s("TG-113"), map, cal, TimeUnitMode.CORRIDO)!!, 1e-9)
        // Sem resolução ou sem início ativo não há ciclo.
        assertNull(Fluxo.cicloDias(s("TG-101"), map, cal, TimeUnitMode.UTIL))
        assertNull(Fluxo.cicloDias(s("TG-115"), map, cal, TimeUnitMode.UTIL))
    }

    @Test
    fun leadCorridos() {
        assertEquals(8.0, Fluxo.leadDias(s("TG-111"), cal, TimeUnitMode.CORRIDO)!!, 1e-9)
        assertNull(Fluxo.leadDias(s("TG-101"), cal, TimeUnitMode.CORRIDO))
    }

    @Test
    fun idadeDesdeInicioAtivoOuCriacao() {
        assertEquals(29.0, Fluxo.idadeDias(s("TG-101"), map, cal, TimeUnitMode.CORRIDO, agora), 1e-9)
        assertEquals(20.0, Fluxo.idadeDias(s("TG-101"), map, cal, TimeUnitMode.UTIL, agora), 1e-9)
        assertEquals(45.0, Fluxo.idadeDias(s("TG-102"), map, cal, TimeUnitMode.CORRIDO, agora), 1e-9)
        assertEquals(Trb1.t("10-01T12"), Fluxo.inicioAtivo(s("TG-101"), map))
        assertNull(Fluxo.inicioAtivo(s("TG-102"), map))
    }

    @Test
    fun diasNoStatusAtual() {
        assertEquals(4.0, Fluxo.diasNoStatusAtual(s("TG-103"), cal, TimeUnitMode.UTIL, agora), 1e-9)
        assertEquals(4.0, Fluxo.diasNoStatusAtual(s("TG-103"), cal, TimeUnitMode.CORRIDO, agora), 1e-9)
        // Sem transição: conta desde a criação (TG-102 nunca saiu de Open).
        assertEquals(45.0, Fluxo.diasNoStatusAtual(s("TG-102"), cal, TimeUnitMode.CORRIDO, agora), 1e-9)
    }

    @Test
    fun resolucaoNaFronteiraDeManaus() {
        // 18/10 23:30 em Manaus (domingo) = 19/10 03:30Z: ainda é a semana que começou em 12/10.
        val noDomingo = Instant.parse("2026-10-19T03:30:00Z")
        assertEquals(LocalDate.parse("2026-10-12"), Fluxo.semanaDe(noDomingo, Trb1.zona))
        assertEquals(LocalDate.parse("2026-10-19"), Fluxo.semanaDe(noDomingo.plusSeconds(3600 * 4 + 1), Trb1.zona))
    }

    @Test
    fun throughputComZeros() {
        val concluidas = listOf("TG-111", "TG-112", "TG-113", "TG-114").map(::s)
        val serie = Fluxo.throughputSemanal(concluidas, LocalDate.parse("2026-10-26"), 4, Trb1.zona)
        assertEquals(
            listOf(
                LocalDate.parse("2026-10-05") to 1, LocalDate.parse("2026-10-12") to 2,
                LocalDate.parse("2026-10-19") to 1, LocalDate.parse("2026-10-26") to 0,
            ),
            serie,
        )
        val chegadas = Fluxo.chegadasSemanais(todas().values.toList(), LocalDate.parse("2026-10-26"), 2, Trb1.zona)
        // Criadas de 19 a 25/10: TG-103 (20/10); de 26/10 em diante: TG-104 (29/10).
        assertEquals(listOf(LocalDate.parse("2026-10-19") to 1, LocalDate.parse("2026-10-26") to 1), chegadas)
    }

    @Test
    fun faixasPorTipo() {
        val f = Fluxo.faixasPorTipo(mapOf("Checklist" to listOf(4.0, 6.0, 8.0), "Checklist Review" to listOf(1.0)))
        assertEquals(6.0, f.getValue("Checklist").p50, 1e-9)
        assertEquals(7.4, f.getValue("Checklist").p85, 1e-9)
        assertEquals(3, f.getValue("Checklist").n)
        assertEquals(Faixa(1.0, 1.0, 1), f.getValue("Checklist Review"))
        // Tipo sem valores não aparece.
        assertFalse("Vazio" in Fluxo.faixasPorTipo(mapOf("Vazio" to emptyList())))
    }

    @Test
    fun amostraPequenaGeraAviso() {
        val f = Fluxo.faixasPorTipo(mapOf("Checklist Review" to listOf(1.0), "Checklist" to listOf(4.0, 6.0, 8.0)))
        val avisos = Fluxo.avisosDeAmostra(f)
        assertEquals(2, avisos.size)
        assertEquals(AvisoDto.AMOSTRA_PEQUENA, avisos[0].codigo)
        assertEquals(AvisoDto.INFORMATIVO, avisos[0].gravidade)
        assertEquals(mapOf("tipo" to "Checklist", "n" to "3", "minimo" to "5"), avisos[0].params)
        assertEquals(mapOf("tipo" to "Checklist Review", "n" to "1", "minimo" to "5"), avisos[1].params)
        val cinco = Fluxo.faixasPorTipo(mapOf("Checklist" to List(5) { it.toDouble() }))
        assertTrue(Fluxo.avisosDeAmostra(cinco).isEmpty(), "n == 5 não avisa")
        assertEquals(5, Fluxo.AMOSTRA_MINIMA)
    }

    @Test
    fun flaggedContaComoEmAndamento() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue("TG-191", "Checklist", "ana", "10-20T12", "Open", prioridade = "High"),
                Trb1.issue("TG-192", "Checklist", "ana", "10-20T12", "Open", prioridade = "High", flagged = true),
            ),
        )
        assertFalse(Fluxo.emAndamento(s("TG-191"), map))
        assertTrue(s("TG-192").flagged)
        assertTrue(Fluxo.emAndamento(s("TG-192"), map))
        assertTrue(Fluxo.emAndamento(s("TG-101"), map))
        assertTrue(Fluxo.emAndamento(s("TG-103"), map), "bloqueada conta como em andamento")
        assertFalse(Fluxo.emAndamento(s("TG-102"), map))
    }

    @Test
    fun statusSemMapaPropaga() {
        val mapaPobre = StatusMap(mapOf("Open" to StatusCategory.TODO))
        assertNotNull(s("TG-102"))
        assertFailsWith<UnmappedStatusException> { Fluxo.concluidaPositiva(s("TG-111"), mapaPobre) }
        assertFailsWith<UnmappedStatusException> { Fluxo.emAndamento(s("TG-101"), mapaPobre) }
    }
}
