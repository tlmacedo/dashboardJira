package br.sidia.painel.auditoria

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.sync.ParDeChaves
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import br.sidia.painel.vinculosDeTeste
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Leitura das issues e do changelog para a auditoria, contra SQLite real em arquivo temporário (dados fictícios). */
class AuditStoreTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            banco.apagar()
        }
    }

    private lateinit var ctx: AppContext
    private lateinit var gravador: SqliteIssueStore
    private lateinit var auditoria: SqliteAuditStore
    private var seq = 0

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), banco)
        val ds = requireNotNull(ctx.dataSource)
        gravador = SqliteIssueStore(ds)
        auditoria = SqliteAuditStore(ds)
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private fun i(s: String) = Instant.parse(s)

    private fun json(
        chave: String, tipo: String = "Task", status: String = "In Progress", assignee: String? = "ana", reporter: String? = "pl.alfa",
        duedate: String? = "2026-10-15", labels: List<String> = emptyList(), resolution: String? = null, resolvida: String? = null,
        paiChave: String? = null, links: List<String> = emptyList(),
    ): JsonObject {
        val n = chave.substringAfter('-')
        fun u(nome: String, v: String?) = if (v == null) "" else """"$nome":{"name":"$v","displayName":"Nome Fictício"},"""
        val pai = if (paiChave == null) "" else """"parent":{"key":"$paiChave"},"""
        val res = if (resolution == null) "" else """"resolution":{"name":"$resolution"},"resolutiondate":"${resolvida ?: "2026-10-10T09:00:00.000-0400"}","""
        val due = if (duedate == null) "" else """"duedate":"$duedate","""
        val rotulos = labels.joinToString(",") { "\"$it\"" }
        return Json.parseToJsonElement(
            """{"id":"2$n","key":"$chave","fields":{"summary":"Resumo $chave","project":{"key":"TG"},"issuetype":{"name":"$tipo"},
            "status":{"name":"$status"},${u("assignee", assignee)}${u("reporter", reporter)}$pai$res$due
            "created":"2026-10-01T09:00:00.000-0400","updated":"2026-10-02T09:00:00.000-0400","labels":[$rotulos],
            "issuelinks":[${links.joinToString(",")}]}}""",
        ).jsonObject
    }

    private fun ev(chave: String, campo: String, de: String?, para: String?, at: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("$chave:${++seq}:0", chave, campo, de, para, i(at), autor)

    private fun gravar(j: JsonObject, eventos: List<ChangeEventRecord> = emptyList(), instancia: String = "samsung") {
        val chave = (j["key"] as kotlinx.serialization.json.JsonPrimitive).content
        gravador.upsert(j, eventos, instancia, null, IssueLinks.ler(chave, j), i("2026-10-30T12:00:00Z"))
    }

    private fun blocks(ponta: String, chave: String) =
        """{"type":{"name":"Blocks","inward":"is blocked by","outward":"blocks"},"$ponta":{"key":"$chave"}}"""

    private val de = i("2026-10-01T00:00:00Z")
    private val ate = i("2026-11-01T00:00:00Z")

    // ---- comEventos ----

    @Test
    fun devolveIssuesComEventoAuditadoNoPeriodo() {
        gravar(json("TG-1"), listOf(ev("TG-1", "duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z")))
        // só evento de campo fora da lista de campos auditados
        gravar(json("TG-2"), listOf(ev("TG-2", "description", null, null, "2026-10-10T12:00:00Z")))
        gravar(json("TG-3"))
        assertEquals(listOf("TG-1"), auditoria.comEventos("samsung", de, ate).map { it.key })
    }

    @Test
    fun campoSemDiferenciarMaiusculas() {
        gravar(json("TG-1"), listOf(ev("TG-1", "DueDate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z")))
        assertEquals(listOf("TG-1"), auditoria.comEventos("samsung", de, ate).map { it.key })
    }

    @Test
    fun trazTodosOsEventosDaIssue() {
        gravar(
            json("TG-1"),
            listOf(
                ev("TG-1", "assignee", "bruno", "ana", "2026-10-01T12:00:00Z"),
                ev("TG-1", "description", null, null, "2026-10-05T12:00:00Z"),
                ev("TG-1", "duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z"),
            ),
        )
        val issues = auditoria.comEventos("samsung", i("2026-10-10T00:00:00Z"), i("2026-10-11T00:00:00Z"))
        val eventos = issues.single().events
        // o de 01/10 vem junto para reconstruir o estado; ordenados por instante
        assertEquals(listOf("assignee", "description", "duedate"), eventos.map { it.field })
        assertEquals(eventos.sortedBy { it.at }, eventos)
    }

    @Test
    fun fronteiraDoPeriodo() {
        gravar(json("TG-1"), listOf(ev("TG-1", "duedate", "2026-10-15", "2026-10-16", "2026-10-10T00:00:00Z")))
        gravar(json("TG-2"), listOf(ev("TG-2", "duedate", "2026-10-15", "2026-10-16", "2026-10-11T00:00:00Z")))
        // [de, ate): `at == de` entra, `at == ate` fica fora
        assertEquals(listOf("TG-1"), auditoria.comEventos("samsung", i("2026-10-10T00:00:00Z"), i("2026-10-11T00:00:00Z")).map { it.key })
    }

    @Test
    fun leCamposDaIssueSemDadoPessoal() {
        gravar(
            json("TG-1", labels = listOf("LATE_SENT", "login"), paiChave = "TG-900", resolution = "Done", status = "Done"),
            listOf(ev("TG-1", "labels", null, "LATE_SENT", "2026-10-10T12:00:00Z")),
        )
        val x = auditoria.comEventos("samsung", de, ate).single()
        assertEquals("Resumo TG-1", x.summary)
        assertEquals("Task", x.issueType)
        assertEquals("TG-900", x.parentKey)
        assertEquals("pl.alfa", x.reporter)
        assertEquals("ana", x.assignee)
        assertEquals(LocalDate.parse("2026-10-15"), x.duedate)
        assertEquals(listOf("LATE_SENT", "login"), x.labels)
        assertEquals("Done", x.resolution)
        assertEquals("Done", x.status)
        assertEquals(i("2026-10-01T13:00:00Z"), x.created)
        assertEquals("samsung", x.instance)
    }

    @Test
    fun instanciaSeparada() {
        gravar(json("TG-1"), listOf(ev("TG-1", "duedate", "2026-10-15", "2026-10-16", "2026-10-10T12:00:00Z")), "sidia")
        assertTrue(auditoria.comEventos("samsung", de, ate).isEmpty())
        assertEquals(listOf("TG-1"), auditoria.comEventos("sidia", de, ate).map { it.key })
        assertNull(auditoria.issue("samsung", "TG-1"))
        assertEquals("TG-1", auditoria.issue("sidia", "TG-1")?.key)
    }

    @Test
    fun auditoriaIgnoraIssueRemovida() {
        gravar(json("TG-1"), listOf(ev("TG-1", "duedate", "2026-10-15", "2026-10-16", "2026-10-10T12:00:00Z")))
        marcarRemovida("TG-1")
        assertTrue(auditoria.comEventos("samsung", de, ate).isEmpty())
        assertNull(auditoria.issue("samsung", "TG-1"))
        assertTrue(auditoria.concluidasComVencimento("samsung", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")).isEmpty())
    }

    private fun marcarRemovida(chave: String) {
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.prepareStatement("UPDATE issue SET removida_em = ? WHERE key = ?").use { ps ->
                ps.setLong(1, 1_700_000_000_000)
                ps.setString(2, chave)
                ps.executeUpdate()
            }
        }
    }

    // ---- concluidasComVencimento ----

    @Test
    fun concluidasComVencimentoNoPeriodo() {
        gravar(json("TG-1", status = "Done", resolution = "Done", duedate = "2026-10-31"))
        gravar(json("TG-2", status = "Done", resolution = "Done", duedate = "2026-11-01"))
        gravar(json("TG-3", status = "Done", resolution = "Done", duedate = "2026-09-30"))
        // aberta e sem vencimento ficam de fora
        gravar(json("TG-4"))
        gravar(json("TG-5", status = "Done", resolution = "Done", duedate = null))
        val r = auditoria.concluidasComVencimento("samsung", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals(listOf("TG-1"), r.map { it.key })
        assertEquals(LocalDate.parse("2026-10-31"), r.single().duedate)
        assertTrue(r.single().resolved != null)
    }

    // ---- paresTaskReview (vínculo Blocks do 4A, nunca o parent) ----

    @Test
    fun paresPeloVinculoBlocks() {
        // Par 1: a Review bloqueia a task. Par 2: a task bloqueia a Review.
        gravar(json("TG-1"))
        gravar(json("TG-101", tipo = "Checklist Review", links = listOf(blocks("outwardIssue", "TG-1"))))
        gravar(json("TG-2", links = listOf(blocks("outwardIssue", "TG-102"))))
        gravar(json("TG-102", tipo = "Checklist Review", links = listOf(blocks("inwardIssue", "TG-2"))), listOf(ev("TG-102", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z")))
        // Uma Review com `parent` da task e sem vínculo: fora.
        gravar(json("TG-3"))
        gravar(json("TG-103", tipo = "Checklist Review", paiChave = "TG-3"))
        val lidos = auditoria.paresTaskReview("samsung", vinculosDeTeste())
        assertFalse(lidos.semVinculos)
        assertEquals(0, lidos.ambiguas)
        assertEquals(listOf(ParDeChaves("TG-1", "TG-101"), ParDeChaves("TG-2", "TG-102")), lidos.vinculos)
        assertEquals(setOf("TG-1", "TG-101", "TG-2", "TG-102"), lidos.issues.map { it.key }.toSet())
        // as issues dos pares vêm com todos os eventos (para o sinal de divergência)
        assertEquals(1, lidos.issues.single { it.key == "TG-102" }.events.size)
    }

    @Test
    fun pontaRemovidaNaoFormaPar() {
        gravar(json("TG-1"))
        gravar(json("TG-101", tipo = "Checklist Review", links = listOf(blocks("outwardIssue", "TG-1"))))
        marcarRemovida("TG-1")
        val lidos = auditoria.paresTaskReview("samsung", vinculosDeTeste())
        assertTrue(lidos.vinculos.isEmpty())
        assertTrue(lidos.issues.isEmpty())
        // o vínculo ativo existe (não é "sem vínculos"): só a ponta saiu
        assertFalse(lidos.semVinculos)
    }

    @Test
    fun semVinculosSinalizaIndisponivel() {
        gravar(json("TG-1"))
        gravar(json("TG-101", tipo = "Checklist Review", paiChave = "TG-1"))
        val lidos = auditoria.paresTaskReview("samsung", vinculosDeTeste())
        assertTrue(lidos.semVinculos)
        assertTrue(lidos.vinculos.isEmpty() && lidos.issues.isEmpty())
    }

    @Test
    fun vinculoAmbiguoContaDescartados() {
        // Uma Review bloqueando duas tasks: fica a de menor chave; o descarte é contado.
        gravar(json("TG-1"))
        gravar(json("TG-2"))
        gravar(json("TG-101", tipo = "Checklist Review", links = listOf(blocks("outwardIssue", "TG-1"), blocks("outwardIssue", "TG-2"))))
        val lidos = auditoria.paresTaskReview("samsung", vinculosDeTeste())
        assertEquals(listOf(ParDeChaves("TG-1", "TG-101")), lidos.vinculos)
        assertEquals(1, lidos.ambiguas)
    }

    @Test
    fun paresEmInstanciaSeparada() {
        gravar(json("TG-1"), instancia = "sidia")
        gravar(json("TG-101", tipo = "Checklist Review", links = listOf(blocks("outwardIssue", "TG-1"))), instancia = "sidia")
        val samsung = auditoria.paresTaskReview("samsung", vinculosDeTeste())
        assertTrue(samsung.semVinculos && samsung.vinculos.isEmpty())
        assertEquals(1, auditoria.paresTaskReview("sidia", vinculosDeTeste()).vinculos.size)
    }

    // ---- issue ----

    @Test
    fun issueInexistenteEhNula() {
        assertNull(auditoria.issue("samsung", "TG-999"))
    }

    @Test
    fun issueTrazOsEventos() {
        gravar(json("TG-1"), listOf(ev("TG-1", "assignee", "bruno", "ana", "2026-10-02T12:00:00Z")))
        val x = auditoria.issue("samsung", "TG-1")!!
        assertEquals(1, x.events.size)
        assertEquals("bruno", x.events.single().from)
    }

    @Test
    fun summaryDoRaw() {
        gravar(json("TG-1"))
        assertEquals("Resumo TG-1", auditoria.issue("samsung", "TG-1")?.summary)
    }
}
