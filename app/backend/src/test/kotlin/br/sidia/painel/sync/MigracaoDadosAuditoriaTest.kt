package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A migração `V7__dados_para_auditoria_completa` (Plano 11, T3 opção A) e a gravação dos dados novos: criação do worklog, valor
 * das estimativas e regravação só dos eventos de estimativa ainda sem valor. SQLite real em arquivo; nenhuma rede.
 */
class MigracaoDadosAuditoriaTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private fun ate(versao: String?): DataSource {
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 2)
        val f = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
        if (versao != null) f.target(versao)
        f.load().migrate()
        return ds
    }

    private fun <T> consulta(ds: DataSource, sql: String, leitura: (java.sql.ResultSet) -> T): T =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use(leitura) }

    private fun exec(ds: DataSource, sql: String) = ds.connection.use { c -> c.createStatement().use { it.executeUpdate(sql) } }

    @Test
    fun cursorZeradoPelaMigracao() {
        val ds = ate("6")
        exec(ds, "INSERT INTO sync_state(instance, watermark, ultima_reconciliacao) VALUES ('samsung', 1000, 2000), ('sidia', 3000, NULL)")
        exec(ds, "INSERT INTO issue(key, instance, project, created) VALUES ('TG-1', 'samsung', 'TG', 1)")
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
        // Cursor nulo nas duas instâncias; as linhas e o resto do estado ficam.
        assertEquals(listOf<Long?>(null, null), consulta(ds, "SELECT watermark FROM sync_state ORDER BY instance") { rs ->
            buildList { while (rs.next()) add(rs.getObject(1) as Long?) }
        })
        assertEquals(2000L, consulta(ds, "SELECT ultima_reconciliacao FROM sync_state WHERE instance = 'samsung'") { it.next(); it.getLong(1) })
        assertEquals(1, consulta(ds, "SELECT count(*) FROM issue") { it.next(); it.getInt(1) })
    }

    @Test
    fun migracaoAcrescentaCriadoEmSemPerderWorklog() {
        val ds = ate("6")
        exec(ds, "INSERT INTO issue(id, key, instance, project, created) VALUES (1, 'TG-1', 'samsung', 'TG', 1)")
        exec(ds, "INSERT INTO worklog(issue_id, author_ref, started, seconds, instance, jira_id) VALUES (1, 'ana', 5, 60, 'samsung', '9')")
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
        assertNull(consulta(ds, "SELECT criado_em FROM worklog") { it.next(); it.getObject(1) }, "registro antigo fica sem a data")
        assertEquals(60L, consulta(ds, "SELECT seconds FROM worklog") { it.next(); it.getLong(1) })
    }

    @Test
    fun tabelaPersonRemovidaQuandoSemUso() {
        val ds = ate("6")
        assertTrue(consulta(ds, "SELECT count(*) FROM sqlite_master WHERE name = 'person'") { it.next(); it.getInt(1) } == 1)
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
        assertEquals(0, consulta(ds, "SELECT count(*) FROM sqlite_master WHERE name = 'person'") { it.next(); it.getInt(1) })
        // Migrar um banco novo direto até a última versão também não deixa a tabela.
        assertEquals(0, consulta(ate(null), "SELECT count(*) FROM sqlite_master WHERE name = 'person'") { it.next(); it.getInt(1) })
    }

    // ---- Gravação dos eventos de estimativa ----

    private fun issue(chave: String) = buildJsonObject {
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

    private fun ev(id: String, campo: String, de: String?, para: String?) =
        ChangeEventRecord("TG-1:$id:0", "TG-1", campo, de, para, Instant.parse("2026-10-03T10:00:00Z"), "ana")

    private fun valores(ds: DataSource): Map<String, Pair<String?, String?>> =
        consulta(ds, "SELECT changelog_id, from_value, to_value FROM change_event ORDER BY changelog_id") { rs ->
            buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2) to rs.getString(3)) }
        }

    @Test
    fun regravaSoEstimativaSemValor() {
        val ds = ate(null)
        val store = SqliteIssueStore(ds)
        // Carga antiga: estimativa sem valor, status com valor, e uma descrição sem valor.
        store.upsert(
            issue("TG-1"),
            listOf(ev("1", "timeoriginalestimate", null, null), ev("2", "status", "Open", "In Progress"), ev("3", "description", null, null)),
            "samsung", null,
        )
        // Nova carga: a estimativa agora vem com valor; o status "mudou" no payload (não pode mudar no banco); a descrição ganha valor (não pode).
        store.upsert(
            issue("TG-1"),
            listOf(ev("1", "timeoriginalestimate", "36000", "28800"), ev("2", "status", "Outro", "Outro2"), ev("3", "description", "x", "y")),
            "samsung", null,
        )
        val v = valores(ds)
        assertEquals("36000" to "28800", v["TG-1:1:0"], "a estimativa sem valor é preenchida")
        assertEquals("Open" to "In Progress", v["TG-1:2:0"], "evento de status já gravado não muda")
        assertEquals(null to null, v["TG-1:3:0"], "campo fora da lista de estimativas nunca é regravado")
        // Idempotente: repetir a mesma carga não muda nada; um valor já gravado não é sobrescrito por outro.
        store.upsert(issue("TG-1"), listOf(ev("1", "timeoriginalestimate", "36000", "28800")), "samsung", null)
        store.upsert(issue("TG-1"), listOf(ev("1", "timeoriginalestimate", "1", "2")), "samsung", null)
        assertEquals("36000" to "28800", valores(ds)["TG-1:1:0"])
        assertEquals(3, valores(ds).size)
    }

    @Test
    fun regravaTimeEstimateSemValorEIgnoraNulosNovos() {
        val ds = ate(null)
        val store = SqliteIssueStore(ds)
        store.upsert(issue("TG-1"), listOf(ev("1", "timeestimate", null, null)), "samsung", null)
        // Um payload que continua sem valor (formato inesperado) mantém nulo, sem erro.
        store.upsert(issue("TG-1"), listOf(ev("1", "timeestimate", null, null)), "samsung", null)
        assertEquals(null to null, valores(ds)["TG-1:1:0"])
        store.upsert(issue("TG-1"), listOf(ev("1", "timeestimate", "7200", "3600")), "samsung", null)
        assertEquals("7200" to "3600", valores(ds)["TG-1:1:0"])
    }
}
