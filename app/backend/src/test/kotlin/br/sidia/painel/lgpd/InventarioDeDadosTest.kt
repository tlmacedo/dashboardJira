package br.sidia.painel.lgpd

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O inventário dos dados tratados (`docs/lgpd/inventario-de-dados.md`) é verificado contra o esquema REAL do banco migrado até a
 * última versão (SQLite em arquivo temporário): coluna nova sem linha no documento, ou linha de coluna que sumiu, derruba o teste.
 */
class InventarioDeDadosTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val VARIAVEIS_DE_RETENCAO = setOf(
            "RETENTION_HISTORICO_MONTHS", "RETENTION_ACCESS_LOG_MONTHS", "RETENTION_DECISOES_MONTHS", "RETENTION_EXECUCOES_MONTHS",
        )
    }

    private val raiz: Path = TestPaths.configDir.toAbsolutePath().normalize().parent.parent
    private val documento: Path = raiz.resolve("docs/lgpd/inventario-de-dados.md")

    private fun texto(): String {
        assertTrue(Files.isRegularFile(documento), "Documento ausente: $documento")
        return Files.readString(documento)
    }

    private class Linha(val coluna: String, val dado: String, val pessoal: String, val finalidade: String, val origem: String, val quemVe: String, val retencao: String, val baseLegal: String)

    /** Linhas da tabela principal: a primeira célula é `` `tabela.coluna` `` (qualquer outra tabela do documento é ignorada). */
    private fun linhas(): List<Linha> = texto().lines()
        .filter { it.startsWith("| `") }
        .map { l -> l.trim().trim('|').split('|').map { it.trim() } }
        .filter { it.size == 8 && Regex("`[a-z0-9_]+\\.[a-z0-9_]+`").matches(it[0]) }
        .map { Linha(it[0].trim('`'), it[1], it[2], it[3], it[4], it[5], it[6], it[7]) }

    private fun colunasDoBanco(): Set<String> {
        val ds: DataSource = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 1)
        Database.migrate(ds)
        return ds.connection.use { c ->
            val tabelas = c.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'flyway_schema_history' ORDER BY name",
            ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            tabelas.flatMap { t ->
                c.createStatement().executeQuery("SELECT name FROM pragma_table_info('$t')").use { rs ->
                    buildList { while (rs.next()) add("$t.${rs.getString(1)}") }
                }
            }.toSet()
        }.also { (ds as? AutoCloseable)?.close() }
    }

    @Test
    fun todaColunaEstaNoInventario() {
        val inventariadas = linhas().map { it.coluna }.toSet()
        val faltam = colunasDoBanco() - inventariadas
        assertTrue(faltam.isEmpty(), "colunas do banco sem linha em docs/lgpd/inventario-de-dados.md: ${faltam.sorted()}")
    }

    @Test
    fun nenhumaColunaInventariadaSumiu() {
        val noBanco = colunasDoBanco()
        val sobram = linhas().map { it.coluna }.filter { it !in noBanco }
        assertTrue(sobram.isEmpty(), "linhas do inventário sem coluna no banco: $sobram")
        val repetidas = linhas().groupBy { it.coluna }.filterValues { it.size > 1 }.keys
        assertTrue(repetidas.isEmpty(), "colunas repetidas no inventário: $repetidas")
    }

    @Test
    fun colunaPessoalTemRetencaoEQuemVe() {
        val pessoais = linhas().filter { it.pessoal == "sim" || it.pessoal == "indireto" }
        assertTrue(pessoais.size >= 5, "o teste enxerga as colunas pessoais: ${pessoais.map { it.coluna }}")
        for (l in pessoais) {
            assertTrue(l.quemVe.isNotBlank() && l.quemVe != "—" && l.quemVe != "-", "${l.coluna}: sem 'Quem vê'")
            val valida = VARIAVEIS_DE_RETENCAO.any { it in l.retencao } || l.retencao.contains("enquanto a issue existir", ignoreCase = true)
            assertTrue(valida, "${l.coluna}: retenção '${l.retencao}' não cita uma variável de retenção nem 'enquanto a issue existir'")
            assertTrue(l.finalidade.isNotBlank(), "${l.coluna}: sem finalidade")
        }
        // Os valores permitidos de "Pessoal?" são só estes três.
        assertTrue(linhas().all { it.pessoal in setOf("sim", "indireto", "não") }, linhas().map { it.pessoal }.toSet().toString())
        assertTrue(linhas().all { it.origem in setOf("Jira", "painel", "configuração") }, linhas().map { it.origem }.toSet().toString())
    }

    @Test
    fun baseLegalNaoAfirmada() {
        val linhas = linhas()
        assertTrue(linhas.isNotEmpty())
        for (l in linhas) assertEquals("a definir pelo DPO", l.baseLegal, "${l.coluna}: a base legal é do DPO")
        assertTrue(!texto().contains("a base legal é", ignoreCase = true), "o inventário não afirma base legal")
    }

    @Test
    fun descreveRawEFiltrosEOQueNaoTrata() {
        val t = texto()
        // issue.raw e access_log.filters têm o saneamento descrito.
        assertTrue(t.contains("SanitizadorRaw"), "issue.raw sem a descrição do SanitizadorRaw")
        assertTrue(Regex("access_log\\.filters.*(sanea|valor)", RegexOption.IGNORE_CASE).containsMatchIn(t), "access_log.filters sem a descrição do saneamento")
        for (item in listOf("papeis.yaml", "equipes.yaml", "ausencias.yaml", "squads.yaml", "comentário", "descrição", "e-mail", "avatar", "nome de exibição", "token")) {
            assertTrue(t.contains(item, ignoreCase = true), "inventário sem '$item'")
        }
    }

    @Test
    fun semDadosReais() {
        val t = texto()
        assertTrue(!Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.(?!invalid)[A-Za-z]{2,}").containsMatchIn(t), "e-mail real no inventário")
        assertTrue(!Regex("https?://(?![^\\s)]*\\.invalid)").containsMatchIn(t), "endereço https fora de .invalid")
    }
}
