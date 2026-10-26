package br.sidia.painel.auditoria

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.sync.ChangeEventRecord
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Linha do tempo de auditoria (cenário AUD-1, issue TG-10), sem banco nem rede. */
class AuditTimelineTest {
    private val statusMap = StatusMap(
        mapOf("Open" to StatusCategory.TODO, "In Progress" to StatusCategory.ACTIVE, "Done" to StatusCategory.DONE),
    )
    private fun i(s: String) = Instant.parse(s)
    private fun d(s: String) = LocalDate.parse(s)
    private var seq = 0

    private fun ev(campo: String, de: String?, para: String?, at: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("TG-10:${++seq}:0", "TG-10", campo, de, para, i(at), autor)

    private fun issue(
        eventos: List<ChangeEventRecord> = emptyList(),
        assignee: String? = "ana",
        duedate: LocalDate? = d("2026-10-15"),
        status: String = "Open",
        resolution: String? = null,
        resolved: Instant? = null,
        labels: List<String> = emptyList(),
    ) = AuditIssue(
        "dc", "TG-10", "Tarefa", "Task", null, "pl.alfa", assignee, duedate,
        i("2026-10-01T12:00:00Z"), resolved, resolution, status, labels, eventos,
    )

    private fun tl(i: AuditIssue) = AuditTimeline(i, statusMap)

    @Test
    fun responsavelEVencimentoNoInstante() {
        val t = tl(
            issue(
                listOf(
                    ev("assignee", "ana", "bruno", "2026-10-05T12:00:00Z"),
                    ev("duedate", "2026-10-15", "2026-10-20", "2026-10-08T12:00:00Z"),
                ),
                assignee = "bruno", duedate = d("2026-10-20"),
            ),
        )
        assertEquals("ana", t.responsavelEm(i("2026-10-05T12:00:00Z")))
        assertEquals("bruno", t.responsavelEm(i("2026-10-05T12:00:01Z")))
        assertEquals(d("2026-10-15"), t.vencimentoEm(i("2026-10-08T11:59:00Z")))
        assertEquals(d("2026-10-20"), t.vencimentoEm(i("2026-10-08T12:00:01Z")))
        assertEquals(d("2026-10-15"), t.vencimentoEm(i("2026-10-08T12:00:00Z")))
    }

    @Test
    fun semEventosUsaEstadoAtual() {
        val t = tl(issue(assignee = "ana", duedate = d("2026-10-15")))
        assertEquals("ana", t.responsavelEm(i("2026-10-03T00:00:00Z")))
        assertEquals(d("2026-10-15"), t.vencimentoEm(i("2026-10-03T00:00:00Z")))
        assertTrue(t.jaTeve("ana", i("2026-10-03T00:00:00Z")))
        assertFalse(t.fechadaEm(i("2026-10-03T00:00:00Z")))
        assertTrue(t.trocasDeResponsavel.isEmpty())
        assertEquals(0, t.vencimentosIlegiveis)
    }

    @Test
    fun responsavelRemovidoViraNulo() {
        val t = tl(issue(listOf(ev("assignee", "ana", null, "2026-10-05T12:00:00Z")), assignee = null))
        assertNull(t.responsavelEm(i("2026-10-06T00:00:00Z")))
        assertNull(t.posseDesde(i("2026-10-06T00:00:00Z")))
    }

    @Test
    fun vencimentoNosDoisFormatos() {
        assertEquals(d("2026-10-15"), Vencimentos.ler("2026-10-15"))
        assertEquals(d("2026-10-15"), Vencimentos.ler("2026-10-15 00:00:00.0"))
        assertNull(Vencimentos.ler("15/10/2026"))
        assertNull(Vencimentos.ler(null))
        assertNull(Vencimentos.ler("  "))

        val t = tl(issue(listOf(ev("duedate", "15/10/2026", "2026-10-20", "2026-10-08T12:00:00Z")), duedate = d("2026-10-20")))
        assertNull(t.vencimentoEm(i("2026-10-08T11:00:00Z")))
        assertEquals(d("2026-10-20"), t.vencimentoEm(i("2026-10-09T00:00:00Z")))
        assertEquals(1, t.vencimentosIlegiveis)
    }

    @Test
    fun vencimentoEmFormatoComHoraNoEvento() {
        val t = tl(issue(listOf(ev("duedate", "2026-10-15 00:00:00.0", null, "2026-10-08T12:00:00Z")), duedate = null))
        assertEquals(d("2026-10-15"), t.vencimentoEm(i("2026-10-08T11:00:00Z")))
        assertNull(t.vencimentoEm(i("2026-10-09T00:00:00Z")))
        assertEquals(0, t.vencimentosIlegiveis)
    }

    @Test
    fun labelsAdicionadasERemovidas() {
        val e = ev("labels", "login acesso", "acesso LATE_SENT", "2026-10-09T12:00:00Z")
        val t = tl(issue(listOf(e), labels = listOf("acesso", "LATE_SENT")))
        val (evento, mudanca) = t.mudancasDeLabels.single()
        assertEquals(e, evento)
        assertEquals(setOf("LATE_SENT"), mudanca.adicionadas)
        assertEquals(setOf("login"), mudanca.removidas)
    }

    @Test
    fun labelsDeTextoVazioOuNulo() {
        val t = tl(issue(listOf(ev("labels", "", "a", "2026-10-09T12:00:00Z"), ev("labels", "a", null, "2026-10-10T12:00:00Z"))))
        assertEquals(setOf("a"), t.mudancasDeLabels[0].second.adicionadas)
        assertTrue(t.mudancasDeLabels[0].second.removidas.isEmpty())
        assertEquals(setOf("a"), t.mudancasDeLabels[1].second.removidas)
    }

    @Test
    fun fechadaEmPelaCategoriaOuResolution() {
        val porStatus = tl(
            issue(listOf(ev("status", "Open", "Done", "2026-10-06T12:00:00Z")), status = "Done"),
        )
        assertFalse(porStatus.fechadaEm(i("2026-10-06T12:00:00Z")))
        assertTrue(porStatus.fechadaEm(i("2026-10-06T12:00:01Z")))

        val porResolution = tl(
            issue(listOf(ev("resolution", null, "Done", "2026-10-07T12:00:00Z")), resolution = "Done"),
        )
        assertFalse(porResolution.fechadaEm(i("2026-10-07T11:00:00Z")))
        assertTrue(porResolution.fechadaEm(i("2026-10-08T00:00:00Z")))

        // reaberta: volta a não estar fechada
        val reaberta = tl(
            issue(
                listOf(
                    ev("status", "Open", "Done", "2026-10-06T12:00:00Z"),
                    ev("status", "Done", "In Progress", "2026-10-07T12:00:00Z"),
                ),
                status = "In Progress",
            ),
        )
        assertTrue(reaberta.fechadaEm(i("2026-10-07T00:00:00Z")))
        assertFalse(reaberta.fechadaEm(i("2026-10-08T00:00:00Z")))
    }

    @Test
    fun statusSemMapeamentoNaoFechaENaoQuebra() {
        val t = tl(issue(status = "Estranho"))
        assertFalse(t.fechadaEm(i("2026-10-08T00:00:00Z")))
    }

    @Test
    fun posseDesdeContinua() {
        val t = tl(
            issue(
                listOf(
                    ev("assignee", "ana", "bruno", "2026-10-05T12:00:00Z"),
                    ev("assignee", "bruno", "ana", "2026-10-12T12:00:00Z"),
                ),
                assignee = "ana",
            ),
        )
        assertEquals(i("2026-10-05T12:00:00Z"), t.posseDesde(i("2026-10-10T00:00:00Z")))
        assertEquals(i("2026-10-12T12:00:00Z"), t.posseDesde(i("2026-10-13T00:00:00Z")))
        // antes da primeira troca, a posse vem desde a criação
        assertEquals(i("2026-10-01T12:00:00Z"), t.posseDesde(i("2026-10-02T00:00:00Z")))
    }

    @Test
    fun jaTeve() {
        val t = tl(
            issue(
                listOf(
                    ev("assignee", "ana", "bruno", "2026-10-05T12:00:00Z"),
                    ev("assignee", "bruno", "carla", "2026-10-08T12:00:00Z"),
                ),
                assignee = "carla",
            ),
        )
        assertTrue(t.jaTeve("ana", i("2026-10-09T00:00:00Z")))
        assertTrue(t.jaTeve("bruno", i("2026-10-09T00:00:00Z")))
        assertFalse(t.jaTeve("bruno", i("2026-10-05T12:00:00Z")))
        assertFalse(t.jaTeve("carla", i("2026-10-08T12:00:00Z")))
        assertTrue(t.jaTeve("carla", i("2026-10-08T12:00:01Z")))
        assertFalse(t.jaTeve("zoe", i("2026-10-30T00:00:00Z")))
    }

    @Test
    fun listasPorTipoDeEventoEmOrdemCronologica() {
        val a2 = ev("assignee", "bruno", "ana", "2026-10-12T12:00:00Z")
        val a1 = ev("assignee", "ana", "bruno", "2026-10-05T12:00:00Z")
        val s = ev("status", "Open", "In Progress", "2026-10-06T12:00:00Z")
        val dd = ev("duedate", "2026-10-15", "2026-10-20", "2026-10-08T12:00:00Z")
        val t = tl(issue(listOf(a2, a1, s, dd)))
        assertEquals(listOf(a1, a2), t.trocasDeResponsavel)
        assertEquals(listOf(s), t.transicoes)
        assertEquals(listOf(dd), t.trocasDeVencimento)
    }

    @Test
    fun reporterAlteradoSoPeloNomeDoCampo() {
        assertFalse(tl(issue()).reporterAlterado)
        assertTrue(tl(issue(listOf(ev("reporter", null, null, "2026-10-05T12:00:00Z")))).reporterAlterado)
    }
}
