package br.sidia.painel.implantacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 10: a reversão com banco restaurado volta os dados de antes da migração do pacote novo. SQLite real em
 * arquivo temporário; a "migração do pacote novo" é uma `V9999__ensaio.sql` numa pasta temporária (nunca existe no repositório).
 */
class ReversaoComBackupTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    private fun abrir(arquivo: Path): DataSource = Database.connect(Database.urlDeArquivo(arquivo), poolSize = 1)

    private fun fechar(ds: DataSource) = (ds as AutoCloseable).close()

    /** Flyway do pacote atual MAIS uma pasta com a migração do "pacote novo". */
    private fun flywayComMigracaoNova(ds: DataSource, pasta: Path): Flyway =
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "filesystem:$pasta").cleanDisabled(true).load()

    private fun pastaComMigracaoDeEnsaio(): Path {
        val pasta = Files.createTempDirectory("painel-migracao-")
        Files.writeString(pasta.resolve("V9999__ensaio.sql"), "CREATE TABLE ensaio_futuro (id INTEGER PRIMARY KEY, nota TEXT);\n")
        return pasta
    }

    private fun existeTabela(ds: DataSource, nome: String): Boolean =
        ds.connection.use { c ->
            c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").use { st ->
                st.setString(1, nome)
                st.executeQuery().use { it.next() }
            }
        }

    private fun versoesAplicadas(ds: DataSource): List<String> =
        ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    private fun contagem(ds: DataSource, tabela: String): Long =
        ds.connection.use { c -> c.createStatement().executeQuery("SELECT count(*) FROM $tabela").use { it.next(); it.getLong(1) } }

    /** Banco migrado com um dado fictício, o backup `VACUUM INTO` dele e o banco depois da migração nova. */
    private class Cenario(val vivo: Path, val backup: Path, val pasta: Path)

    private fun prepararCenario(): Cenario {
        val vivo = banco.novoArquivo()
        val backup = vivo.resolveSibling(vivo.fileName.toString() + ".pre-2026.11.2")
        val pasta = pastaComMigracaoDeEnsaio()
        val ds = abrir(vivo)
        try {
            Database.migrate(ds)
            ds.connection.use { c ->
                c.createStatement().use { st ->
                    st.executeUpdate("INSERT INTO access_log (user_id, route, filters, at) VALUES ('ana.silva', '/api/ensaio', 'ANTES', 1000)")
                }
            }
            // backup antes de atualizar (o mesmo mecanismo do Plano 12)
            ds.connection.use { c -> c.createStatement().use { it.execute("VACUUM INTO '${backup.toString().replace("'", "''")}'") } }
            // o pacote novo traz uma migração sem volta e o painel grava mais dados
            flywayComMigracaoNova(ds, pasta).migrate()
            ds.connection.use { c ->
                c.createStatement().use {
                    it.executeUpdate("INSERT INTO ensaio_futuro (nota) VALUES ('depois')")
                    it.executeUpdate("INSERT INTO access_log (user_id, route, filters, at) VALUES ('ana.silva', '/api/ensaio', 'DEPOIS', 2000)")
                }
            }
        } finally {
            fechar(ds)
        }
        return Cenario(vivo, backup, pasta)
    }

    /** Reversão com banco: guarda o banco migrado ao lado e põe o backup no lugar (serviço parado; arquivos -wal e -shm saem juntos). */
    private fun restaurar(c: Cenario) {
        val guardado = c.vivo.resolveSibling(c.vivo.fileName.toString() + ".migrado-teste")
        for (sufixo in listOf("", "-wal", "-shm")) {
            val origem = c.vivo.resolveSibling(c.vivo.fileName.toString() + sufixo)
            if (Files.exists(origem)) Files.move(origem, c.vivo.resolveSibling(guardado.fileName.toString() + sufixo), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.copy(c.backup, c.vivo)
    }

    @Test
    fun restauracaoVoltaOsDadosAnterioresAMigracao() {
        val c = prepararCenario()
        restaurar(c)
        val ds = abrir(c.vivo)
        try {
            assertEquals(1L, contagem(ds, "access_log"), "só o dado de antes do backup")
            val filtros = ds.connection.use { con -> con.createStatement().executeQuery("SELECT filters FROM access_log").use { it.next(); it.getString(1) } }
            assertEquals("ANTES", filtros)
        } finally {
            fechar(ds)
        }
    }

    @Test
    fun historicoNaoTemAMigracaoDeEnsaio() {
        val c = prepararCenario()
        restaurar(c)
        val ds = abrir(c.vivo)
        try {
            assertFalse("9999" in versoesAplicadas(ds), "a migração de ensaio não pode constar depois de restaurar")
            assertTrue(versoesAplicadas(ds).isNotEmpty())
        } finally {
            fechar(ds)
        }
    }

    @Test
    fun tabelaFuturaNaoExisteDepoisDeRestaurar() {
        val c = prepararCenario()
        // antes de restaurar, o banco vivo tem a tabela da migração nova
        val vivo = abrir(c.vivo)
        try {
            assertTrue(existeTabela(vivo, "ensaio_futuro"))
        } finally {
            fechar(vivo)
        }
        restaurar(c)
        val ds = abrir(c.vivo)
        try {
            assertFalse(existeTabela(ds, "ensaio_futuro"))
        } finally {
            fechar(ds)
        }
    }

    @Test
    fun bancoRestauradoSobeComOPacoteAtual() {
        val c = prepararCenario()
        restaurar(c)
        val ds = abrir(c.vivo)
        try {
            Database.migrate(ds) // o pacote atual sobre o banco restaurado: sem erro
            assertEquals(0, Database.migracoesPendentes(ds))
            assertEquals(1L, contagem(ds, "access_log"))
        } finally {
            fechar(ds)
        }
    }

    @Test
    fun bancoGuardadoAoLadoPreservaOQueFoiGravadoDepoisDoBackup() {
        val c = prepararCenario()
        restaurar(c)
        val guardado = c.vivo.resolveSibling(c.vivo.fileName.toString() + ".migrado-teste")
        assertTrue(Files.isRegularFile(guardado), "o banco migrado fica guardado, nada é apagado")
        val ds = abrir(guardado)
        try {
            assertEquals(2L, contagem(ds, "access_log"))
            assertEquals(1L, contagem(ds, "ensaio_futuro"))
        } finally {
            fechar(ds)
        }
    }

    /**
     * Comportamento real do Flyway (registrado em docs/implantacao/reversao.md): o pacote ANTERIOR sobre um esquema que já tem
     * uma migração que ele não conhece. Este teste observa e fixa o que a versão do Flyway do projeto faz hoje; se mudar, o
     * guia precisa mudar junto.
     */
    @Test
    fun pacoteAnteriorSobreEsquemaNovoComportamentoDoFlyway() {
        val c = prepararCenario()
        val ds = abrir(c.vivo)
        try {
            val resultado = runCatching { Database.migrate(ds) }
            // O Flyway ignora, por padrão, migrações "futuras" já aplicadas e desconhecidas localmente: o pacote anterior sobe
            // sem erro, mas só funciona se a migração for compatível com o código antigo. Na dúvida, restaurar o backup.
            assertTrue(resultado.isSuccess, "esperado: o Flyway aceita (ignora a migração futura); veio ${resultado.exceptionOrNull()}")
            assertTrue(existeTabela(ds, "ensaio_futuro"), "a tabela da migração nova continua lá")
        } finally {
            fechar(ds)
        }
    }
}
