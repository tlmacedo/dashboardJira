package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.module
import br.sidia.painel.sync.montarAgendador
import br.sidia.painel.testContextComBanco
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fluxo completo com SQLite real: origem sintética, backup, restauração e conferência (nada de mock de banco). */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class BackupRestauracaoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val MANAUS = ZoneId.of("America/Manaus")
        private val AGORA = Instant.parse("2026-10-30T12:00:00Z")
    }

    private val temporarias = mutableListOf<Path>()
    private val pools = mutableListOf<DataSource>()
    private val fechaveis = mutableListOf<AutoCloseable>()

    @AfterEach
    fun limpar() {
        fechaveis.forEach { it.close() }
        fechaveis.clear()
        pools.forEach { (it as AutoCloseable).close() }
        pools.clear()
        temporarias.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun nova(): Path = Files.createTempDirectory("painel-fluxo-").also { temporarias.add(it) }

    /** A origem do cenário "restauracaoIgualAoOriginal": 500 issues, 5 eventos cada, 12 pessoas, 800 worklogs. */
    private fun origem(): Pair<Path, DataSource> {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo))
        Database.migrate(ds)
        pools.add(ds)
        PopularBanco.popular(ds, issues = 500, eventosPorIssue = 5, worklogs = 800, acessos = 120)
        return arquivo to ds
    }

    private fun backup(arquivo: Path, ds: DataSource, copias: Path): Path =
        assertIs<ResultadoDoBackup.Ok>(
            ServicoDeBackup(ds, arquivo, BackupSettings(copias), SqliteBackupRunStore(ds), null, RelogioDeTeste(AGORA), MANAUS).executar(),
        ).arquivo

    @Test
    fun restauracaoIgualAoOriginal() {
        val (arquivo, ds) = origem()
        val copia = backup(arquivo, ds, nova())
        val r = VerificadorDeRestauracao(SqliteBackupRunStore(ds), null, RelogioDeTeste(AGORA)).verificar(copia)
        assertTrue(r.ok, r.codigoErro)
        // backup_run fica de fora: a linha do backup é gravada DEPOIS da cópia (a cópia não a contém), por desenho.
        val daOrigem = PopularBanco.contagens(ds) - "backup_run"
        assertEquals(daOrigem, r.tabelas - "backup_run", "contagem por tabela igual à da origem")
        assertEquals(500L, r.tabelas["issue"])
        assertEquals(2500L, r.tabelas["change_event"])
        assertEquals(800L, r.tabelas["worklog"])
        assertEquals(120L, r.tabelas["access_log"])
    }

    @Test
    fun migracoesSemPendencia() {
        val (arquivo, ds) = origem()
        val copia = backup(arquivo, ds, nova())
        val restaurado = nova().resolve("painel.db")
        Files.copy(copia, restaurado)
        val dsRestaurado = Database.connect(Database.urlDeArquivo(restaurado), poolSize = 1).also { pools.add(it) }
        assertEquals(0, Database.migracoesPendentes(dsRestaurado))
    }

    private fun restaurarEmArquivo(copia: Path): Path {
        val restaurado = nova().resolve("painel.db")
        Files.copy(copia, restaurado)
        return restaurado
    }

    @Test
    fun painelSobeNoRestaurado() = testApplication {
        val (arquivo, ds) = origem()
        val restaurado = restaurarEmArquivo(backup(arquivo, ds, nova()))
        val s = Settings.fromEnv(emptyMap())
        val ctx = testContextComBanco(s, banco, arquivoExistente = restaurado).also { fechaveis.add(it) }
        application { module(s, ctx) }
        val r = client.get("/api/health/pronto")
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals("""{"pronto":true}""", r.bodyAsText())
    }

    @Test
    fun numerosIguaisNoCard() {
        val (arquivo, ds) = origem()
        val restaurado = restaurarEmArquivo(backup(arquivo, ds, nova()))
        val relogio = java.time.Clock.fixed(AGORA, MANAUS)
        val s = Settings.fromEnv(emptyMap())
        // O contexto de teste abre o arquivo indicado (a origem ou o restaurado) e o card é pedido como GERENTE (modo dev).
        fun corpoDoCard(arq: Path): String {
            var corpo = ""
            testApplication {
                val ctx = testContextComBanco(s, banco, clock = relogio, arquivoExistente = arq).also { fechaveis.add(it) }
                application { module(s, ctx) }
                val r = client.get("/api/cards/abertas")
                assertEquals(HttpStatusCode.OK, r.status)
                corpo = r.bodyAsText()
            }
            return corpo
        }
        val daOrigem = corpoDoCard(arquivo)
        val doRestaurado = corpoDoCard(restaurado)
        val abertas = Regex("\"abertas\":(\\d+)").find(doRestaurado)?.groupValues?.get(1)?.toInt()
        assertTrue((abertas ?: 0) > 0, "o cenário precisa ter tasks abertas: ${doRestaurado.take(200)}")
        assertEquals(daOrigem, doRestaurado, "o card devolve os mesmos números na origem e no restaurado")
    }

    @Test
    fun verificacaoAposBackupRegistraRestauracaoOk() = runBlocking {
        val copias = nova()
        val s = Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "true", "BACKUP_DIR" to copias.toString(), "BACKUP_HORA" to "02:00"))
        val ctx = testContextComBanco(s, banco).also { fechaveis.add(it) }
        PopularBanco.popular(requireNotNull(ctx.dataSource), issues = 50, eventosPorIssue = 2, worklogs = 20, acessos = 10)
        val relogio = RelogioDeTeste(Instant.parse("2026-10-30T06:00:00Z"))
        val agendador = requireNotNull(montarAgendador(ctx, relogio, metricas = null))
        agendador.ciclo()
        val runs = SqliteBackupRunStore(requireNotNull(ctx.dataSource))
        val backup = runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)!!
        val restauracao = runs.ultima(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK)!!
        assertEquals(backup.arquivo, restauracao.arquivo, "a restauração testada é a da cópia que acabou de sair")
        assertTrue(restauracao.inicio >= backup.inicio)
        relogio.avancar(Duration.ofHours(1))
    }
}
