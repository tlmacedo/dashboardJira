package br.sidia.painel.trabalho

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.jql.CardSpecs
import br.sidia.painel.camposDeTeste
import br.sidia.painel.TestPaths
import br.sidia.painel.domain.firstEntered
import br.sidia.painel.sync.FiltroDeFicha
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Leitura do modelo único de issues no SQLite real em arquivo temporário (cenário TRB-1). */
class WorkStoreTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private lateinit var work: SqliteWorkStore

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        work = SqliteWorkStore(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun chaves(l: List<IssueSnapshot>) = l.map { it.key }.sorted()

    private val outubro = t("10-01T00") to Instant.parse("2026-11-01T00:00:00Z")
    private fun t(s: String) = Trb1.t(s)

    @Test
    fun abertasEResolvidas() {
        Trb1.carregar(ds)
        val abertas = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
        assertEquals(listOf("TG-101", "TG-102", "TG-103", "TG-104"), chaves(abertas))
        val resolvidas = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, outubro.first, outubro.second))
        assertEquals(listOf("TG-111", "TG-112", "TG-113", "TG-114", "TG-115", "TG-116"), chaves(resolvidas))
        assertEquals(10, work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).size)
        // Campos de trabalho e linha do tempo chegam no modelo.
        val tg101 = abertas.first { it.key == "TG-101" }
        assertEquals("High", tg101.priority)
        assertEquals("In Progress", tg101.status)
        assertEquals(t("10-01T12"), tg101.timeline.firstEntered("In Progress"))
        assertEquals(LocalDate.parse("2026-10-28"), tg101.duedate)
        assertEquals(3.0, resolvidas.first { it.key == "TG-111" }.storyPoints)
    }

    @Test
    fun fronteiraDoPeriodo() {
        Trb1.carregar(ds)
        val resolvidoEm = t("10-09T12") // TG-111
        fun consulta(de: Instant, ate: Instant) =
            chaves(work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, de, ate)))
        assertTrue("TG-111" in consulta(resolvidoEm, resolvidoEm.plusSeconds(1)), "resolved == de entra")
        assertFalse("TG-111" in consulta(resolvidoEm.minusSeconds(3600), resolvidoEm), "resolved == ate fica fora")
    }

    @Test
    fun filtroDeFichaAplicado() {
        Trb1.carregar(ds)
        val soChecklist = FiltroDeFicha(setOf("Checklist"), emptySet())
        val abertas = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, soChecklist, Situacao.ABERTAS))
        assertEquals(listOf("TG-101", "TG-103", "TG-104"), chaves(abertas), "TG-102 é Checklist Review")
        val semFechadas = FiltroDeFicha(emptySet(), setOf("Closed"))
        assertEquals(
            listOf("TG-101", "TG-102", "TG-103", "TG-104"),
            chaves(work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, semFechadas, Situacao.TODAS))),
        )
        // A ficha de verdade (tempo-em-status) também é lida pelo FiltroDeFicha.de.
        val ficha = CardSpecs.fromYaml(TestPaths.configDir.resolve("cards/tempo-em-status.card.yaml"), camposDeTeste())
        assertNotNull(FiltroDeFicha.de(ficha))
    }

    @Test
    fun removidaNaoAparece() {
        val store = Trb1.carregar(ds)
        val idsRemovidos = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT id FROM issue WHERE key = 'TG-101'").use { rs -> rs.next(); setOf(rs.getLong(1)) }
        }
        assertEquals(1, store.marcarRemovidas(Trb1.INSTANCIA, idsRemovidos, Trb1.agora))
        val abertas = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
        assertEquals(listOf("TG-102", "TG-103", "TG-104"), chaves(abertas))
        val comEvento = work.comEventoEntre(Trb1.INSTANCIA, null, setOf("status"), t("10-01T00"), t("10-02T00"))
        assertTrue(comEvento.none { it.key == "TG-101" })
    }

    @Test
    fun eventosDeOutrasDatasVemJuntos() {
        Trb1.carregar(ds)
        // Só o evento de vencimento de TG-111 cai em 07/10; a issue vem com TODOS os seus eventos.
        val achadas = work.comEventoEntre(Trb1.INSTANCIA, null, setOf("duedate"), t("10-07T00"), t("10-08T00"))
        assertEquals(listOf("TG-111"), chaves(achadas))
        val campos = achadas.single().events.map { it.field }
        assertEquals(listOf("status", "duedate", "status"), campos, "ordenados por instante: 10-05, 10-07, 10-09")
        // Campo em maiúsculas no pedido ainda casa, e fora do período não devolve nada.
        assertEquals(listOf("TG-111"), chaves(work.comEventoEntre(Trb1.INSTANCIA, null, setOf("DueDate"), t("10-07T00"), t("10-08T00"))))
        assertTrue(work.comEventoEntre(Trb1.INSTANCIA, null, setOf("duedate"), t("10-08T00"), t("10-09T00")).isEmpty())
    }

    @Test
    fun vencimentoOriginalPeloPrimeiroEvento() {
        Trb1.carregar(ds)
        val todas = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).associateBy { it.key }
        assertEquals(LocalDate.parse("2026-10-08"), todas.getValue("TG-111").vencimentoOriginal())
        assertEquals(LocalDate.parse("2026-10-09"), todas.getValue("TG-111").duedate)
        // Sem eventos de vencimento: vale o atual; sem vencimento: nulo.
        assertEquals(LocalDate.parse("2026-10-15"), todas.getValue("TG-112").vencimentoOriginal())
        assertNull(todas.getValue("TG-102").vencimentoOriginal())
    }

    @Test
    fun vencimentoDefinidoDepoisTemPorOriginalOPrimeiroValor() {
        val i = Trb1.issue(
            "TG-170", "Checklist", "ana", "10-01T12", "Open", vence = "2026-10-20",
            eventos = listOf(
                Trb1.ev("TG-170", "duedate", null, "2026-10-15", "10-02T12"),
                Trb1.ev("TG-170", "duedate", "2026-10-15", "2026-10-20", "10-10T12"),
            ),
        )
        Trb1.carregar(ds, listOf(i))
        val s = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).single()
        assertEquals(LocalDate.parse("2026-10-15"), s.vencimentoOriginal())
    }

    @Test
    fun porVencimento() {
        Trb1.carregar(ds)
        val removida = Trb1.issue("TG-175", "Checklist", "ana", "10-01T12", "Open", vence = "2026-10-15")
        val store = Trb1.carregar(ds, listOf(removida))
        val idRemovida = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT id FROM issue WHERE key = 'TG-175'").use { rs -> rs.next(); rs.getLong(1) }
        }
        store.marcarRemovidas(Trb1.INSTANCIA, setOf(idRemovida), Trb1.agora)
        fun chaves(de: String, ate: String) =
            chaves(work.candidatasNoPrazo(Trb1.INSTANCIA, null, LocalDate.parse(de), LocalDate.parse(ate)))
        // Inclusivo nas duas pontas, qualquer situação (abertas e resolvidas), e a removida logicamente nunca aparece.
        assertEquals(listOf("TG-112", "TG-113", "TG-114"), chaves("2026-10-15", "2026-10-16").filter { it != "TG-175" })
        assertEquals(listOf("TG-112"), chaves("2026-10-15", "2026-10-15"))
        assertEquals(listOf("TG-101"), chaves("2026-10-28", "2026-10-28"))
        assertEquals(listOf("TG-103"), chaves("2026-11-01", "2026-11-30"))
        assertTrue(chaves("2026-12-01", "2026-12-31").isEmpty())
        // Uma resolvida ANTES do período que vence dentro dele entra (a leitura é pelo vencimento).
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-152", "Task", "ana", "09-20T12", "Resolved", resolution = "Done", resolvida = "09-28T12", vence = "2026-10-05",
                    eventos = Trb1.trilha("TG-152", "Open", "09-21T12" to "In Progress", "09-28T12" to "Resolved"),
                ),
            ),
        )
        assertEquals(listOf("TG-152"), chaves("2026-10-05", "2026-10-05"))
    }

    @Test
    fun adaptadorParaAuditIssue() {
        Trb1.carregar(ds)
        val s = work.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS)).first { it.key == "TG-113" }
        val a = s.toAuditIssue()
        assertEquals(s.key, a.key)
        assertEquals(s.duedate, a.duedate)
        assertEquals(s.labels, a.labels)
        assertEquals(s.events, a.events)
        assertEquals(s.assignee, a.assignee)
        assertEquals(s.resolved, a.resolved)
        assertEquals(s.resolution, a.resolution)
        assertEquals(s.status, a.status)
        assertEquals(Trb1.INSTANCIA, a.instance)
        assertEquals(listOf("LATE_SENT"), a.labels)
    }

    @Test
    fun instanciaSeparada() {
        Trb1.carregar(ds)
        Trb1.carregar(ds, listOf(Trb1.issue("TG-101", "Checklist", "carla", "10-01T12", "Open")), instancia = "sidia")
        val samsung = work.snapshots(ConsultaDeTrabalho("samsung", null, Situacao.ABERTAS)).first { it.key == "TG-101" }
        assertEquals("ana", samsung.assignee)
        val sidia = work.snapshots(ConsultaDeTrabalho("sidia", null, Situacao.TODAS))
        assertEquals(listOf("TG-101"), chaves(sidia))
        assertEquals("carla", sidia.single().assignee)
        assertFalse(work.temCamposDeTrabalho("outra"))
    }

    @Test
    fun semPrioridadeEmNenhumaIssue() {
        Trb1.carregar(ds, Trb1.issues(comPrioridade = false))
        assertFalse(work.temCamposDeTrabalho(Trb1.INSTANCIA))
        Trb1.carregar(ds)
        assertTrue(work.temCamposDeTrabalho(Trb1.INSTANCIA))
    }

    @Test
    fun osVersionFiltraSemDiferenciarMaiusculas() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue("TG-180", "Checklist", "ana", "10-01T12", "Open", osVersion = "Android 17.0 (OneUI_9.0)"),
                Trb1.issue("TG-181", "Checklist", "ana", "10-01T12", "Open", osVersion = "Android 16.0 (16)"),
                Trb1.issue("TG-182", "Checklist", "ana", "10-01T12", "Open"),
            ),
        )
        val c = ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS, osVersion = "android 17.0 (oneui_9.0)")
        assertEquals(listOf("TG-180"), chaves(work.snapshots(c)))
        assertEquals("Android 17.0 (OneUI_9.0)", work.snapshots(c).single().osVersion)
    }
}
