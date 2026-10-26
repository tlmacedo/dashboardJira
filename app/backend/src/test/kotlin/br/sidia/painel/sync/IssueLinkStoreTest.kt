package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.config.Settings
import br.sidia.painel.testContextComBanco
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gravação dos vínculos junto com a issue, contra PostgreSQL real (Testcontainers). */
class IssueLinkStoreTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    private val t0 = Instant.parse("2026-10-30T12:00:00Z")
    private val t1 = Instant.parse("2026-10-30T13:00:00Z")
    private lateinit var ctx: AppContext
    private lateinit var store: PostgresIssueStore
    private lateinit var links: PostgresIssueLinkStore

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), pg)
        val ds = requireNotNull(ctx.dataSource)
        store = PostgresIssueStore(ds)
        links = PostgresIssueLinkStore(ds)
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    private fun issue(chave: String, vararg itens: String, semCampo: Boolean = false): JsonObject {
        val campo = if (semCampo) "" else """, "issuelinks": [${itens.joinToString(",")}]"""
        val n = chave.substringAfter('-')
        return Json.parseToJsonElement(
            """{"id":"1$n","key":"$chave","fields":{"summary":"s$n","project":{"key":"TG"},"issuetype":{"name":"Task"},
            "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"2026-10-02T09:00:00.000-0400","labels":[]$campo}}""",
        ).jsonObject
    }

    private fun blocks(ponta: String, chave: String) =
        """{"type":{"name":"Blocks","inward":"is blocked by","outward":"blocks"},"$ponta":{"key":"$chave"}}"""

    private fun gravar(i: JsonObject, instancia: String = "samsung", em: Instant = t0) {
        val chave = (i["key"] as kotlinx.serialization.json.JsonPrimitive).content
        store.upsert(i, emptyList(), instancia, null, IssueLinks.ler(chave, i), em)
    }

    private fun contar(where: String = "true"): Int = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM issue_link WHERE $where").use { it.next(); it.getInt(1) }
    }

    private fun removidaEm(from: String, to: String, instancia: String = "samsung"): Instant? =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.prepareStatement("SELECT removida_em FROM issue_link WHERE instance = ? AND from_key = ? AND to_key = ?").use { ps ->
                ps.setString(1, instancia)
                ps.setString(2, from)
                ps.setString(3, to)
                ps.executeQuery().use { rs ->
                    assertTrue(rs.next(), "vínculo $from -> $to não encontrado")
                    rs.getTimestamp(1)?.toInstant()
                }
            }
        }

    @Test
    fun linkVistoPelasDuasPontasEhUmaLinha() {
        gravar(issue("TG-1", blocks("inwardIssue", "TG-2")))
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")))
        assertEquals(1, contar())
        assertEquals(listOf(VinculoRecord("TG-2", "TG-1", "Blocks", DirecaoVinculo.OUTWARD)), links.ativos("samsung", "Blocks"))
    }

    @Test
    fun vinculosIdempotentes() {
        val i = issue("TG-1", blocks("inwardIssue", "TG-2"))
        gravar(i)
        gravar(i, em = t1)
        assertEquals(1, contar())
        assertEquals(0, contar("removida_em IS NOT NULL"))
    }

    @Test
    fun linkRemovidoGanhaRemovidaEm() {
        gravar(issue("TG-1", blocks("inwardIssue", "TG-2")))
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")))
        // A Review volta sem o link: ele sumiu no Jira.
        gravar(issue("TG-2"), em = t1)
        assertEquals(t1, removidaEm("TG-2", "TG-1"))
        assertEquals(emptyList(), links.ativos("samsung", "Blocks"))
        assertEquals(emptyList(), links.daIssue("samsung", "TG-1"))
    }

    @Test
    fun linkQueVoltaLimpaRemocao() {
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")))
        gravar(issue("TG-2"), em = t1)
        assertNotNull(removidaEm("TG-2", "TG-1"))
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")), em = Instant.parse("2026-10-30T14:00:00Z"))
        assertNull(removidaEm("TG-2", "TG-1"))
        assertEquals(1, links.ativos("samsung", "blocks").size)
    }

    @Test
    fun campoAusenteNaoApagaVinculos() {
        gravar(issue("TG-1", blocks("inwardIssue", "TG-2")))
        gravar(issue("TG-1", semCampo = true), em = t1)
        assertEquals(1, links.ativos("samsung", "Blocks").size)
        assertNull(removidaEm("TG-2", "TG-1"))
    }

    @Test
    fun daIssueTrazOsDasDuasPontas() {
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")))
        gravar(issue("TG-3", blocks("inwardIssue", "TG-1")))
        assertEquals(2, links.daIssue("samsung", "TG-1").size)
        assertEquals(1, links.daIssue("samsung", "TG-2").size)
    }

    @Test
    fun vinculosPorInstanciaIsolados() {
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")), "samsung")
        gravar(issue("TG-2", blocks("outwardIssue", "TG-1")), "sidia")
        assertEquals(2, contar())
        assertEquals(1, links.ativos("samsung", "Blocks").size)
        assertEquals(1, links.ativos("sidia", "Blocks").size)
        // Remover na samsung não toca a sidia.
        gravar(issue("TG-2"), "samsung", t1)
        assertEquals(emptyList(), links.ativos("samsung", "Blocks"))
        assertEquals(1, links.ativos("sidia", "Blocks").size)
        assertNull(removidaEm("TG-2", "TG-1", "sidia"))
    }

    @Test
    fun rawDaIssueNaoGuardaOsVinculos() {
        val i = issue("TG-1", """{"type":{"name":"Blocks"},"inwardIssue":{"key":"TG-2","fields":{"summary":"segredo da outra ponta"}}}""")
        gravar(i)
        val raw = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT raw::text FROM issue WHERE key = 'TG-1'").use { it.next(); it.getString(1) }
        }
        assertTrue("segredo" !in raw && "issuelinks" !in raw, raw)
    }
}
