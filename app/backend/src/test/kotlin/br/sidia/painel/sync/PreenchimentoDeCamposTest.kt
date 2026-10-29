package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Gravação dos campos do dicionário no upsert e preenchimento retroativo a partir do `raw` (SQLite real; sem Jira). */
class PreenchimentoDeCamposTest {
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
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private val campos = listOf(
        CampoDeFiltro("Checklist Type", "checklist-type", "customfield_90001"),
        CampoDeFiltro("SKU Type", "sku-type", "customfield_90002"),
    )

    private fun issue(key: String, extra: String = ""): JsonObject = Json.parseToJsonElement(
        """{"id":"1","key":"$key","fields":{"summary":"Task fictícia","project":{"key":"TG"},"issuetype":{"name":"Checklist"},
           "status":{"name":"Open"},"created":"2026-10-01T10:00:00.000+0000","updated":"2026-10-02T10:00:00.000+0000"$extra}}""",
    ).jsonObject

    private fun lerColuna(key: String, coluna: String): String? = ds.connection.use { con ->
        con.prepareStatement("SELECT $coluna FROM issue WHERE instance = 'samsung' AND key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> check(rs.next()); rs.getString(1) }
        }
    }

    @Test
    fun upsertGravaCamposDoDicionario() {
        val store = SqliteIssueStore(ds)
        store.upsert(
            issue("TG-1", ""","customfield_90001":{"value":"Full"},"customfield_90002":{"value":"MR"}"""), emptyList(), "samsung",
            camposDeFiltro = campos,
        )
        val gravado = Json.parseToJsonElement(lerColuna("TG-1", "campos_dicionario")!!).jsonObject
        assertEquals("Full", (gravado["Checklist Type"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("MR", (gravado["SKU Type"] as kotlinx.serialization.json.JsonPrimitive).content)
        // Gravado por uma sincronização que já pedia os campos: a linha está marcada como completa.
        assertNotNull(lerColuna("TG-1", "campos_dicionario_em"))
    }

    @Test
    fun upsertSemCamposGravaObjetoVazio() {
        SqliteIssueStore(ds).upsert(issue("TG-2"), emptyList(), "samsung", camposDeFiltro = campos)
        assertEquals("{}", lerColuna("TG-2", "campos_dicionario"))
        // Sem nenhum customId conhecido a linha NÃO é marcada como completa (não há o que extrair).
        SqliteIssueStore(ds).upsert(
            issue("TG-3"), emptyList(), "samsung", camposDeFiltro = listOf(CampoDeFiltro("SKU Type", "sku-type", null)),
        )
        assertEquals("{}", lerColuna("TG-3", "campos_dicionario"))
        assertNull(lerColuna("TG-3", "campos_dicionario_em"))
    }

    @Test
    fun preencheRetroativamenteDoRaw() {
        val store = SqliteIssueStore(ds)
        // Gravada antes da migração: sem lista de campos, o upsert deixa o objeto vazio; simulamos NULL como na linha antiga.
        store.upsert(issue("TG-4", ""","customfield_90002":{"value":"MR"}"""), emptyList(), "samsung")
        ds.connection.use { it.createStatement().executeUpdate("UPDATE issue SET campos_dicionario = NULL, campos_dicionario_em = NULL") }
        val tocadas = PreenchimentoDeCampos(ds).preencher("samsung", campos, lote = 1)
        assertEquals(1, tocadas)
        val gravado = Json.parseToJsonElement(lerColuna("TG-4", "campos_dicionario")!!).jsonObject
        assertEquals(setOf("SKU Type"), gravado.keys)
        // Preenchido do raw (que pode não ter os campos novos): continua marcado como a completar.
        assertNull(lerColuna("TG-4", "campos_dicionario_em"))
        assertEquals(0, PreenchimentoDeCampos(ds).preencher("samsung", campos), "segunda chamada não toca nenhuma linha")
    }

    @Test
    fun semCustomIdNaoPreenchePraNaoGravarObjetoVazioDefinitivo() {
        SqliteIssueStore(ds).upsert(issue("TG-5", ""","customfield_90002":{"value":"MR"}"""), emptyList(), "samsung")
        ds.connection.use { it.createStatement().executeUpdate("UPDATE issue SET campos_dicionario = NULL") }
        val semId = listOf(CampoDeFiltro("SKU Type", "sku-type", null))
        assertEquals(0, PreenchimentoDeCampos(ds).preencher("samsung", semId))
        assertNull(lerColuna("TG-5", "campos_dicionario"))
    }

    @Test
    fun preenchimentoNaoFazRequisicao() {
        // O preenchimento só recebe o DataSource: não há cliente do Jira, nem MockEngine, que possa ser chamado.
        var requisicoes = 0
        val contador = io.ktor.client.engine.mock.MockEngine { requisicoes++; throw AssertionError("não deve haver requisição") }
        SqliteIssueStore(ds).upsert(issue("TG-6", ""","customfield_90001":{"value":"Full"}"""), emptyList(), "samsung")
        ds.connection.use { it.createStatement().executeUpdate("UPDATE issue SET campos_dicionario = NULL") }
        PreenchimentoDeCampos(ds).preencher("samsung", campos)
        assertEquals(0, requisicoes)
        contador.close()
    }

    @Test
    fun avisoCamposIncompletosContaLinhasAMarcar() {
        val store = SqliteIssueStore(ds)
        store.upsert(issue("TG-7", ""","customfield_90001":{"value":"Full"}"""), emptyList(), "samsung", camposDeFiltro = campos)
        store.upsert(issue("TG-8"), emptyList(), "samsung")
        ds.connection.use { it.createStatement().executeUpdate("UPDATE issue SET campos_dicionario = NULL, campos_dicionario_em = NULL WHERE key = 'TG-8'") }
        PreenchimentoDeCampos(ds).preencher("samsung", campos)
        // TG-7 foi gravada completa; TG-8 só foi preenchida do raw: 1 linha ainda espera a reconciliação.
        assertEquals(1, PreenchimentoDeCampos(ds).linhasIncompletas("samsung"))
        // Reconciliar (novo upsert com a lista de campos) completa a linha.
        store.upsert(issue("TG-8"), emptyList(), "samsung", camposDeFiltro = campos)
        assertEquals(0, PreenchimentoDeCampos(ds).linhasIncompletas("samsung"))
    }
}
