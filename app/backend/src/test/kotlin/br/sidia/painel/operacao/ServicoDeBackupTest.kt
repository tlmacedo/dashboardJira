package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.sql.DriverManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ServicoDeBackupTest {
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

    private class Cenario(val ds: DataSource, val arquivo: Path, val dir: Path, val runs: SqliteBackupRunStore, val relogio: RelogioDeTeste)

    private fun cenario(agora: String = "2026-10-30T06:00:00Z"): Cenario {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo))
        Database.migrate(ds)
        pools.add(ds)
        ds.connection.use { c ->
            val ps = c.prepareStatement("INSERT INTO issue(key, instance, project) VALUES (?, 'samsung', 'TG')")
            for (i in 1..200) {
                ps.setString(1, "TG-$i")
                ps.executeUpdate()
            }
        }
        val dir = Files.createTempDirectory("painel-backup-").also { temporarias.add(it) }
        return Cenario(ds, arquivo, dir, SqliteBackupRunStore(ds), RelogioDeTeste(Instant.parse(agora)))
    }

    private fun servico(
        c: Cenario,
        retencao: Int = 14,
        dir: Path = c.dir,
        livre: (Path) -> Long = { Long.MAX_VALUE },
        aposCopiar: (Path) -> Unit = {},
        metricas: Metricas? = null,
    ) = ServicoDeBackup(c.ds, c.arquivo, BackupSettings(dir, retencaoCopias = retencao), c.runs, metricas, c.relogio, MANAUS, livre, aposCopiar)

    private fun contarIssues(arquivo: Path): Int =
        DriverManager.getConnection("jdbc:sqlite:${arquivo.toAbsolutePath()}?open_mode=1").use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM issue").use { it.next(); it.getInt(1) }
        }

    private fun nomes(dir: Path) = dir.listDirectoryEntries().map { it.fileName.toString() }.filterNot { it.endsWith(".sha256") }.sorted()

    @Test
    fun copiaLegivelEIgualAoOriginal() {
        val c = cenario()
        val r = assertIs<ResultadoDoBackup.Ok>(servico(c).executar())
        // 06:00Z = 02:00 em Manaus: o nome usa o fuso do painel
        assertEquals("painel-2026-10-30-020000.db", r.arquivo.fileName.toString())
        assertEquals(200, contarIssues(r.arquivo))
        assertTrue(r.tamanhoBytes > 0)
        assertEquals(64, r.sha256.length)
        val registro = assertNotNull(c.runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK))
        assertEquals("painel-2026-10-30-020000.db", registro.arquivo)
        assertEquals(r.tamanhoBytes, registro.tamanhoBytes)
        assertEquals(r.sha256, registro.sha256)
        assertEquals(listOf("painel-2026-10-30-020000.db"), nomes(c.dir))
    }

    @Test
    fun copiaConsistenteComEscritaEmParalelo() {
        val c = cenario()
        val parar = AtomicBoolean(false)
        val escritor = thread {
            var i = 1000
            while (!parar.get()) {
                runCatching {
                    c.ds.connection.use { con ->
                        con.prepareStatement("INSERT INTO issue(key, instance, project) VALUES (?, 'samsung', 'TG')").use {
                            it.setString(1, "TG-${i++}")
                            it.executeUpdate()
                        }
                    }
                }
            }
        }
        try {
            val resultados = (1..3).map {
                c.relogio.avancar(java.time.Duration.ofSeconds(1))
                servico(c).executar()
            }
            resultados.forEach { assertIs<ResultadoDoBackup.Ok>(it) }
            // cada cópia passou na conferência (integridade) e tem pelo menos as 200 linhas iniciais
            resultados.map { (it as ResultadoDoBackup.Ok).arquivo }.forEach { assertTrue(contarIssues(it) >= 200) }
        } finally {
            parar.set(true)
            escritor.join()
        }
    }

    @Test
    fun permissao600() {
        val c = cenario()
        val r = assertIs<ResultadoDoBackup.Ok>(servico(c).executar())
        assumeTrue(Files.getFileStore(r.arquivo).supportsFileAttributeView("posix"), "sem POSIX neste sistema")
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(r.arquivo))
    }

    @Test
    fun parcialNasceComPermissaoRestrita() {
        val c = cenario()
        var permissoes: Set<java.nio.file.attribute.PosixFilePermission>? = null
        servico(c, aposCopiar = { p ->
            permissoes = runCatching { Files.getPosixFilePermissions(p) }.getOrNull()
        }).executar()
        assumeTrue(permissoes != null, "sem POSIX neste sistema")
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissoes, "o .parcial já nasce 600, não só a cópia final")
    }

    @Test
    fun sha256FicaAoLadoDaCopiaEAcompanhaARetencao() {
        val c = cenario()
        val r = assertIs<ResultadoDoBackup.Ok>(servico(c).executar())
        val lado = r.arquivo.resolveSibling(r.arquivo.fileName.toString() + ".sha256")
        assertEquals("${r.sha256}  ${r.arquivo.fileName}\n", Files.readString(lado))
        // retenção 1: ao sair a cópia antiga, sai o .sha256 dela
        c.relogio.avancar(java.time.Duration.ofDays(1))
        val segunda = assertIs<ResultadoDoBackup.Ok>(servico(c, retencao = 1).executar())
        assertFalse(Files.exists(r.arquivo) || Files.exists(lado))
        assertTrue(Files.exists(segunda.arquivo.resolveSibling(segunda.arquivo.fileName.toString() + ".sha256")))
    }

    @Test
    fun parcialNaoSobra() {
        val c = cenario()
        val r = servico(c, aposCopiar = { Files.writeString(it, "lixo") }).executar()
        assertIs<ResultadoDoBackup.Falha>(r)
        assertTrue(nomes(c.dir).none { it.endsWith(".parcial") }, nomes(c.dir).toString())
    }

    @Test
    fun copiaCorrompidaNaoViraOk() {
        val c = cenario()
        val r = servico(c, aposCopiar = { parcial ->
            // trunca a cópia ao meio, como um disco que enche ou uma queda no meio da escrita
            FileChannel.open(parcial, StandardOpenOption.WRITE).use { it.truncate(Files.size(parcial) / 2) }
        }).executar()
        assertEquals(ResultadoDoBackup.Falha("INTEGRIDADE_FALHOU"), r)
        assertEquals(emptyList(), nomes(c.dir), "nenhum .db novo e nenhum .parcial")
        assertEquals(null, c.runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK))
        assertEquals("INTEGRIDADE_FALHOU", c.runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.FALHA)?.codigoErro)
    }

    @Test
    fun copiaComBytesAlteradosTambemNaoViraOk() {
        val c = cenario()
        val r = servico(c, aposCopiar = { parcial ->
            val bytes = Files.readAllBytes(parcial)
            for (i in 100 until bytes.size step 64) bytes[i] = (bytes[i] + 1).toByte() // corrompe a página
            Files.write(parcial, bytes)
        }).executar()
        assertEquals(ResultadoDoBackup.Falha("INTEGRIDADE_FALHOU"), r)
        assertEquals(emptyList(), nomes(c.dir))
    }

    @Test
    fun pastaSemEscritaFalhaComCodigo() {
        val c = cenario()
        val arquivoComum = Files.createTempFile("painel-backup-arquivo-", ".txt").also { temporarias.add(it) }
        val r = servico(c, dir = arquivoComum.resolve("dentro")).executar()
        assertEquals(ResultadoDoBackup.Falha("PASTA_SEM_ESCRITA"), r)
        assertEquals("PASTA_SEM_ESCRITA", c.runs.ultima(TipoDeBackup.BACKUP)?.codigoErro)
    }

    @Test
    fun discoInsuficienteFalhaComCodigo() {
        val c = cenario()
        val r = servico(c, livre = { 1024 }).executar()
        assertEquals(ResultadoDoBackup.Falha("DISCO_INSUFICIENTE"), r)
        assertEquals(emptyList(), nomes(c.dir))
    }

    @Test
    fun retencaoSoApagaNomesDeBackup() {
        val c = cenario()
        for (dia in 1..20) Files.writeString(c.dir.resolve("painel-2026-09-%02d-020000.db".format(dia)), "x")
        Files.writeString(c.dir.resolve("leia-me.txt"), "nota")
        Files.writeString(c.dir.resolve("painel-qualquer.db"), "x")
        assertIs<ResultadoDoBackup.Ok>(servico(c, retencao = 14).executar())
        val restantes = nomes(c.dir)
        val copias = restantes.filter { NOME_DE_BACKUP.matches(it) }
        assertEquals(14, copias.size)
        assertTrue("painel-2026-10-30-020000.db" in copias, "a cópia nova é a mais recente")
        assertTrue("painel-2026-09-20-020000.db" in copias && "painel-2026-09-08-020000.db" in copias)
        assertFalse("painel-2026-09-07-020000.db" in copias)
        assertTrue("leia-me.txt" in restantes && "painel-qualquer.db" in restantes, "arquivos que não são cópias ficam")
    }

    @Test
    fun retencaoPreservaUltimoOk() {
        val c = cenario()
        val antigo = "painel-2026-10-01-020000.db"
        val recente = "painel-2026-10-02-020000.db"
        Files.writeString(c.dir.resolve(antigo), "x")
        Files.writeString(c.dir.resolve(recente), "x")
        val t = Instant.parse("2026-10-01T06:00:00Z")
        c.runs.registrar(RegistroDeBackup(TipoDeBackup.BACKUP, StatusDeBackup.OK, t, t, antigo, 1, "a".repeat(64), null))
        c.runs.registrar(RegistroDeBackup(TipoDeBackup.BACKUP, StatusDeBackup.FALHA, t.plusSeconds(86400), t.plusSeconds(86400), recente, null, null, "INTEGRIDADE_FALHOU"))
        // o backup de agora falha: com retenção 1 o mais recente por nome fica e o último OK conhecido também
        servico(c, retencao = 1, aposCopiar = { Files.writeString(it, "lixo") }).executar()
        assertEquals(listOf(antigo, recente), nomes(c.dir))
    }

    @Test
    fun saidaSemCaminhoNemSegredo() {
        val saida = ByteArrayOutputStream()
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(PrintStream(saida, true, Charsets.UTF_8))
        val c = cenario()
        servico(c).executar()
        servico(c, livre = { 1 }).executar()
        val log = saida.toString(Charsets.UTF_8)
        assertTrue("Backup OK" in log && "painel-2026-10-30-020000.db" in log)
        assertFalse(c.dir.toString() in log, "o caminho da pasta de backup não pode aparecer no log")
        val registro = c.runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)!!
        assertFalse('/' in registro.arquivo.orEmpty() || '\\' in registro.arquivo.orEmpty())
    }

    @Test
    fun metricasDoBackup() {
        val c = cenario()
        val metricas = Metricas()
        servico(c, metricas = metricas).executar()
        servico(c, metricas = metricas, livre = { 1 }).executar()
        val texto = metricas.texto()
        assertTrue(texto.lines().any { it.startsWith("painel_backup_execucoes_total") && "status=\"OK\"" in it && it.endsWith(" 1.0") })
        assertTrue(texto.lines().any { it.startsWith("painel_backup_execucoes_total") && "status=\"FALHA\"" in it && it.endsWith(" 1.0") })
    }
}
