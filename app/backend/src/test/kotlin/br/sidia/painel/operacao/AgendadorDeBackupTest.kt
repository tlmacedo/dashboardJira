package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.sync.Agendador
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.sync.TarefaDeOperacao
import br.sidia.painel.sync.montarAgendador
import br.sidia.painel.testContextComBanco
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class AgendadorDeBackupTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private val temporarias = mutableListOf<Path>()
    private val abertos = mutableListOf<AutoCloseable>()

    @AfterEach
    fun limpar() {
        abertos.forEach { it.close() }
        abertos.clear()
        temporarias.forEach { runCatching { it.deleteRecursively() } }
    }

    @Test
    fun operacaoDevidaRodaEUmaFalhaNaoParaOutras() = runBlocking {
        val ctx = testContextComBanco(Settings(), banco).also { abertos.add(it) }
        val relogio = RelogioDeTeste(Instant.parse("2026-10-30T12:00:00Z"))
        val rodou = mutableListOf<String>()
        val agendador = Agendador(
            emptyList(), SqliteSyncStateStore(requireNotNull(ctx.dataSource)), relogio,
            operacoes = listOf(
                TarefaDeOperacao("quebra", { true }) { error("falhou de propósito") },
                TarefaDeOperacao("naoDevida", { false }) { rodou.add("naoDevida") },
                TarefaDeOperacao("segue", { true }) { rodou.add("segue") },
            ),
        )
        agendador.ciclo()
        assertEquals(listOf("segue"), rodou)
    }

    private fun settingsComBackup(vararg extras: Pair<String, String>, dir: Path?): Settings =
        Settings.fromEnv(
            buildMap {
                put("DASHBOARD_DEV_MODE", "true")
                if (dir != null) put("BACKUP_DIR", dir.toString())
                putAll(extras)
            },
        )

    @Test
    fun backupDiarioNaHoraConfigurada() = runBlocking {
        val dir = Files.createTempDirectory("painel-agenda-backup-").also { temporarias.add(it) }
        val ctx = testContextComBanco(settingsComBackup("BACKUP_HORA" to "02:00", dir = dir), banco).also { abertos.add(it) }
        // 05:00Z = 01:00 em Manaus (antes da hora)
        val relogio = RelogioDeTeste(Instant.parse("2026-10-30T05:00:00Z"))
        val agendador = requireNotNull(montarAgendador(ctx, relogio, metricas = null))
        agendador.ciclo()
        assertEquals(emptyList(), dir.listDirectoryEntries(), "antes de 02:00 não faz backup")

        relogio.avancar(Duration.ofHours(1)) // 02:00 em Manaus
        agendador.ciclo()
        val copias = dir.listDirectoryEntries().map { it.fileName.toString() }
        assertEquals(listOf("painel-2026-10-30-020000.db"), copias)

        relogio.avancar(Duration.ofHours(3)) // 05:00 do mesmo dia: já feito, não repete
        agendador.ciclo()
        assertEquals(1, dir.listDirectoryEntries().size)

        relogio.avancar(Duration.ofHours(24)) // dia seguinte, depois da hora
        agendador.ciclo()
        assertEquals(2, dir.listDirectoryEntries().size)
    }

    @Test
    fun falhaDeHojeNaoTentaDeNovoNoMesmoDia() = runBlocking {
        val arquivoComum = Files.createTempFile("painel-agenda-arquivo-", ".txt").also { temporarias.add(it) }
        // a "pasta" de backup é um arquivo comum: toda tentativa falha com PASTA_SEM_ESCRITA
        val ctx = testContextComBanco(settingsComBackup(dir = arquivoComum.resolve("dentro")), banco).also { abertos.add(it) }
        val relogio = RelogioDeTeste(Instant.parse("2026-10-30T07:00:00Z"))
        val agendador = requireNotNull(montarAgendador(ctx, relogio, metricas = null))
        agendador.ciclo()
        agendador.ciclo()
        val runs = SqliteBackupRunStore(requireNotNull(ctx.dataSource))
        assertEquals("PASTA_SEM_ESCRITA", runs.ultima(TipoDeBackup.BACKUP)?.codigoErro)
        val total = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM backup_run WHERE tipo = 'BACKUP'").use { it.next(); it.getInt(1) }
        }
        assertEquals(1, total, "uma tentativa por dia")
    }

    @Test
    fun semBackupDirNaoEntraNoAgendador() = runBlocking {
        val ctx = testContextComBanco(settingsComBackup(dir = null), banco).also { abertos.add(it) }
        val relogio = RelogioDeTeste(Instant.parse("2026-10-30T07:00:00Z"))
        val agendador = requireNotNull(montarAgendador(ctx, relogio, metricas = null))
        agendador.ciclo()
        assertNull(SqliteBackupRunStore(requireNotNull(ctx.dataSource)).ultima(TipoDeBackup.BACKUP))
        assertTrue(true)
    }
}
