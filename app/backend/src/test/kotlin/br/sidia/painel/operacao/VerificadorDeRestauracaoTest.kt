package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class VerificadorDeRestauracaoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val MANAUS = ZoneId.of("America/Manaus")
    }

    private val temporarias = mutableListOf<Path>()
    private val pools = mutableListOf<DataSource>()
    private val saidaOriginal: PrintStream = System.out

    @AfterEach
    fun limpar() {
        System.setOut(saidaOriginal)
        pools.forEach { (it as AutoCloseable).close() }
        pools.clear()
        temporarias.forEach { runCatching { it.deleteRecursively() } }
    }

    private class Origem(val ds: DataSource, val arquivo: Path, val copias: Path, val runs: SqliteBackupRunStore, val relogio: RelogioDeTeste)

    private fun origem(issues: Int = 100): Origem {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo))
        Database.migrate(ds)
        pools.add(ds)
        PopularBanco.popular(ds, issues = issues, eventosPorIssue = 3, worklogs = 150, acessos = 40)
        val copias = Files.createTempDirectory("painel-copias-").also { temporarias.add(it) }
        return Origem(ds, arquivo, copias, SqliteBackupRunStore(ds), RelogioDeTeste(Instant.parse("2026-10-30T06:00:00Z")))
    }

    private fun Origem.fazerBackup(): Path {
        val s = ServicoDeBackup(ds, arquivo, BackupSettings(copias), runs, null, relogio, MANAUS)
        return assertIs<ResultadoDoBackup.Ok>(s.executar()).arquivo
    }

    private fun Origem.verificador(tmp: () -> Path = { Files.createTempDirectory("painel-restauracao-") }) =
        VerificadorDeRestauracao(runs, null, relogio, tmp)

    @Test
    fun restauraEConfereUmaCopiaBoa() {
        val o = origem()
        val copia = o.fazerBackup()
        val r = o.verificador().verificar(copia)
        assertTrue(r.ok, r.codigoErro)
        // backup_run fica de fora: a linha do backup é gravada DEPOIS da cópia (a cópia não a contém), por desenho.
        assertEquals(PopularBanco.contagens(o.ds) - "backup_run", r.tabelas - "backup_run")
        assertEquals(0L, r.tabelas["backup_run"])
        assertEquals(100L, r.tabelas["issue"])
        assertEquals(300L, r.tabelas["change_event"])
        // fica registrado, com o NOME da cópia
        val registro = o.runs.ultima(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK)!!
        assertEquals(copia.fileName.toString(), registro.arquivo)
    }

    @Test
    fun copiaAlteradaDetectada() {
        val o = origem()
        val copia = o.fazerBackup()
        val bytes = Files.readAllBytes(copia)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte() // um byte mudado depois do registro
        Files.write(copia, bytes)
        val r = o.verificador().verificar(copia)
        assertEquals(ResultadoDaVerificacao(false, emptyMap(), "COPIA_ALTERADA"), r)
        assertEquals("COPIA_ALTERADA", o.runs.ultima(TipoDeBackup.RESTAURACAO, StatusDeBackup.FALHA)?.codigoErro)
    }

    @Test
    fun copiaTruncadaFalha() {
        val o = origem()
        val copia = o.fazerBackup()
        // outra cópia, SEM registro (não há SHA-256 para comparar), cortada ao meio
        val truncada = o.copias.resolve("painel-2026-10-31-020000.db")
        Files.copy(copia, truncada)
        FileChannel.open(truncada, StandardOpenOption.WRITE).use { it.truncate(Files.size(truncada) / 2) }
        val r = o.verificador().verificar(truncada)
        assertFalse(r.ok)
        assertEquals("INTEGRIDADE_FALHOU", r.codigoErro)
    }

    @Test
    fun migracaoPendenteFalha() {
        val o = origem()
        // um banco de uma versão antiga do esquema: só a primeira migração aplicada
        val antigo = o.copias.resolve("painel-2026-10-01-020000.db")
        val dsAntigo = Database.connect(Database.urlDeArquivo(antigo), poolSize = 1)
        Flyway.configure().dataSource(dsAntigo).locations("classpath:db/migration").target("1").load().migrate()
        (dsAntigo as AutoCloseable).close()
        for (sufixo in listOf("-wal", "-shm")) Files.deleteIfExists(Path.of(antigo.toString() + sufixo))
        val r = o.verificador().verificar(antigo)
        assertEquals("MIGRACAO_PENDENTE", r.codigoErro)
    }

    @Test
    fun temporarioApagadoMesmoNaFalha() {
        val o = origem()
        val copia = o.fazerBackup()
        val truncada = o.copias.resolve("painel-2026-10-31-020000.db")
        Files.copy(copia, truncada)
        FileChannel.open(truncada, StandardOpenOption.WRITE).use { it.truncate(Files.size(truncada) / 2) }
        val criados = mutableListOf<Path>()
        val falha = o.verificador { Files.createTempDirectory("painel-restauracao-").also { criados.add(it) } }.verificar(truncada)
        assertFalse(falha.ok)
        val ok = o.verificador { Files.createTempDirectory("painel-restauracao-").also { criados.add(it) } }.verificar(copia)
        assertTrue(ok.ok)
        assertEquals(2, criados.size)
        assertTrue(criados.none { Files.exists(it) }, "nenhum diretório temporário pode sobrar (nem -wal nem -shm)")
    }

    @Test
    fun bancoVivoIntactoEmEscrita() {
        val o = origem()
        val copia = o.fazerBackup()
        o.ds.connection.use { it.createStatement().execute("PRAGMA wal_checkpoint(TRUNCATE)") }
        val wal = Path.of(o.arquivo.toString() + "-wal")
        val antes = Triple(Files.size(o.arquivo), if (Files.exists(wal)) Files.size(wal) else 0L, Files.getLastModifiedTime(o.arquivo))
        // Sem registrar o resultado (o registro é uma escrita deliberada em backup_run): a verificação em si não escreve.
        VerificadorDeRestauracao(SemRegistroDeBackup, null, o.relogio).verificar(copia)
        val depois = Triple(Files.size(o.arquivo), if (Files.exists(wal)) Files.size(wal) else 0L, Files.getLastModifiedTime(o.arquivo))
        assertEquals(antes, depois, "a verificação não toca o banco vivo")
        // e a cópia no disco continua igual ao registrado (ela também não foi tocada)
        assertEquals(o.runs.porArquivo(copia.fileName.toString())?.tamanhoBytes, Files.size(copia))
    }

    @Test
    fun saidaSemDadosPessoais() {
        val saida = ByteArrayOutputStream()
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(PrintStream(saida, true, Charsets.UTF_8))
        val o = origem() // access_log tem linhas com 'ana.silva'
        val copia = o.fazerBackup()
        val r = o.verificador().verificar(copia)
        assertTrue(r.ok)
        val tudo = r.toString() + saida.toString(Charsets.UTF_8) + o.runs.ultima(TipoDeBackup.RESTAURACAO).toString()
        assertFalse("ana.silva" in tudo)
        assertFalse("gerente.exemplo" in tudo)
        assertFalse(o.copias.toString() in saida.toString(Charsets.UTF_8), "o caminho não vai para o log")
    }

    @Test
    fun arquivoInexistenteViraErroInternoSemQuebrar() {
        val o = origem()
        val r = o.verificador().verificar(o.copias.resolve("painel-2026-01-01-000000.db"))
        assertEquals("ERRO_INTERNO", r.codigoErro)
    }
}
