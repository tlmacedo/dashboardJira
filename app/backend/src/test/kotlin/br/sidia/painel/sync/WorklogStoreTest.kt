package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.db.Retention
import br.sidia.painel.time.BusinessCalendar
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gravação e leitura do worklog no SQLite real: substituição, isolamento por instância, remoção lógica e retenção. */
class WorklogStoreTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private val zona = ZoneId.of("America/Manaus")

    private fun novoBanco(): DataSource =
        Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 2).also { Database.migrate(it) }

    private fun issue(chave: String): JsonObject = buildJsonObject {
        put("key", chave)
        put("id", chave.substringAfter('-'))
        putJsonObject("fields") {
            put("summary", "Task fictícia $chave")
            putJsonObject("project") { put("key", "TG") }
            putJsonObject("issuetype") { put("name", "Task") }
            putJsonObject("status") { put("name", "Open") }
            put("created", "2026-10-01T09:00:00.000-0400")
            put("updated", "2026-10-02T09:00:00.000-0400")
        }
    }

    private fun reg(id: String, autor: String?, inicio: String, s: Long) = WorklogRecord(id, autor, Instant.parse(inicio), s)

    private fun contar(ds: DataSource, sql: String = "SELECT count(*) FROM worklog"): Int =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getInt(1) } }

    @Test
    fun gravaSubstituiELeSemDuplicar() {
        val ds = novoBanco()
        val issues = SqliteIssueStore(ds)
        val worklogs = SqliteWorklogStore(ds)
        val a = reg("1", "ana", "2026-10-05T13:00:00Z", 3600)
        val b = reg("2", "bruno", "2026-10-06T13:00:00Z", 1800)
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = listOf(a, b))
        assertEquals(mapOf("TG-1" to listOf(a, b)), worklogs.porIssue("samsung", setOf("TG-1", "TG-2")))
        // Substitui: o 2 sumiu, o 3 entrou, o 1 mudou de duração.
        val a2 = a.copy(seconds = 7200)
        val c = reg("3", "ana", "2026-10-07T13:00:00Z", 60)
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = listOf(a2, c))
        assertEquals(listOf(a2, c), worklogs.porIssue("samsung", setOf("TG-1")).getValue("TG-1"))
        assertEquals(2, contar(ds))
        // Nulo não mexe; lista vazia limpa.
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = null)
        assertEquals(2, contar(ds))
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = emptyList())
        assertEquals(0, contar(ds))
    }

    @Test
    fun worklogQueMudouDeIssueNaoViolaOIndiceUnico() {
        val ds = novoBanco()
        val issues = SqliteIssueStore(ds)
        val w = reg("9", "ana", "2026-10-05T13:00:00Z", 60)
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = listOf(w))
        // O mesmo id do Jira aparece agora na TG-2 (movido) ANTES de a TG-1 ser ressincronizada.
        issues.upsert(issue("TG-2"), emptyList(), "samsung", worklogs = listOf(w))
        val porIssue = SqliteWorklogStore(ds).porIssue("samsung", setOf("TG-1", "TG-2"))
        assertEquals(setOf("TG-2"), porIssue.keys)
        assertEquals(1, contar(ds))
    }

    @Test
    fun mesmaChaveEmOutraInstanciaNaoMistura() {
        val ds = novoBanco()
        val issues = SqliteIssueStore(ds)
        val worklogs = SqliteWorklogStore(ds)
        issues.upsert(issue("TG-1"), emptyList(), "samsung", worklogs = listOf(reg("1", "ana", "2026-10-05T13:00:00Z", 3600)))
        issues.upsert(issue("TG-1"), emptyList(), "outra", worklogs = listOf(reg("1", "carla", "2026-10-05T13:00:00Z", 60)))
        assertEquals(2, contar(ds))
        assertEquals("ana", worklogs.porIssue("samsung", setOf("TG-1")).getValue("TG-1").single().autor)
        assertEquals("carla", worklogs.porIssue("outra", setOf("TG-1")).getValue("TG-1").single().autor)
    }

    @Test
    fun horasPorAutorNaJanelaSemRemovidasNemDesconhecidos() {
        val ds = novoBanco()
        val issues = SqliteIssueStore(ds)
        val worklogs = SqliteWorklogStore(ds)
        issues.upsert(
            issue("TG-1"), emptyList(), "samsung",
            worklogs = listOf(
                reg("1", "ana", "2026-10-05T13:00:00Z", 3600), reg("2", "ana", "2026-10-10T13:00:00Z", 7200),
                reg("3", "bruno", "2026-10-10T13:00:00Z", 1800), reg("4", null, "2026-10-10T13:00:00Z", 999),
                reg("5", "ana", "2026-09-01T13:00:00Z", 36000),
            ),
        )
        issues.upsert(issue("TG-2"), emptyList(), "samsung", worklogs = listOf(reg("6", "ana", "2026-10-11T13:00:00Z", 3600)))
        val de = Instant.parse("2026-10-01T00:00:00Z")
        val ate = Instant.parse("2026-10-30T00:00:00Z")
        assertEquals(mapOf("ana" to 4.0, "bruno" to 0.5), worklogs.horasPorAutor("samsung", de, ate))
        // O limite superior é exclusivo.
        assertEquals(mapOf("ana" to 1.0), worklogs.horasPorAutor("samsung", de, Instant.parse("2026-10-10T13:00:00Z")))
        // Issue removida logicamente deixa de contar.
        ds.connection.use { c -> c.createStatement().executeUpdate("UPDATE issue SET removida_em = 1760000000000 WHERE key = 'TG-2'") }
        assertEquals(mapOf("ana" to 3.0, "bruno" to 0.5), worklogs.horasPorAutor("samsung", de, ate))
        assertTrue(worklogs.horasPorAutor("outra", de, ate).isEmpty())
    }

    @Test
    fun retencaoApagaWorklogAntigoComInstancia() {
        val ds = novoBanco()
        val issues = SqliteIssueStore(ds)
        issues.upsert(
            issue("TG-1"), emptyList(), "samsung",
            worklogs = listOf(reg("1", "ana", "2024-10-23T12:00:00Z", 60), reg("2", "ana", "2024-10-25T12:00:00Z", 60)),
        )
        // Hoje 2026-10-24, 24 meses: o limite é 2024-10-24 00:00 de Manaus.
        Retention(ds, BusinessCalendar(zona)).purge(LocalDate.of(2026, 10, 24), 24)
        val restantes = SqliteWorklogStore(ds).porIssue("samsung", setOf("TG-1")).getValue("TG-1")
        assertEquals(listOf("2"), restantes.map { it.jiraId })
    }
}
