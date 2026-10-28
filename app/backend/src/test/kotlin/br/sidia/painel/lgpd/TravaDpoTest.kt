package br.sidia.painel.lgpd

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A porta do DPO é decidida pela configuração E pelo banco, e fecha por padrão (fail-closed): só desenvolvimento offline sobre
 * banco SEM dado sincronizado de verdade a abre sem aceite. SQLite real em arquivo; nenhuma rede.
 */
class TravaDpoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private fun bancoNovo(): DataSource = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 2).also { Database.migrate(it) }

    private fun comSyncReal(ds: DataSource) = ds.connection.use { c ->
        c.createStatement().use {
            it.executeUpdate("INSERT INTO sync_run(instance, kind, started_at, finished_at, status) VALUES ('samsung', 'INCREMENTAL', 1, 2, 'OK')")
        }
    }

    private val dev = Settings(dashboardDevMode = true)

    @Test
    fun desenvolvimentoOfflineComBancoDeDemonstracaoEstaLiberada() {
        val trava = TravaDpo(dev, bancoNovo())
        assertTrue(trava.liberada)
        assertNull(trava.motivo())
        trava.exigir(ModuloSensivel.INTEGRIDADE)
        trava.exigir(ModuloSensivel.AUDITORIA)
    }

    @Test
    fun desenvolvimentoComBancoQueJaSincronizouDeVerdadeFicaFechada() {
        // A flag do Jira foi desligada depois de uma sincronização real: o banco tem dado real e a porta NÃO abre.
        val ds = bancoNovo()
        comSyncReal(ds)
        val trava = TravaDpo(dev, ds)
        assertFalse(trava.liberada)
        assertEquals("AGUARDANDO_ACEITE_DPO", trava.motivo())
        assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(ModuloSensivel.INTEGRIDADE) }
    }

    private fun execucao(ds: DataSource, kind: String) = ds.connection.use { c ->
        c.createStatement().use {
            it.executeUpdate("INSERT INTO sync_run(instance, kind, started_at, finished_at, status) VALUES ('-', '$kind', 1, 2, 'OK')")
        }
    }

    @Test
    fun retencaoNaoContaComoSincronizacaoReal() {
        // A retenção roda sempre (inclusive com o Jira desligado) e grava sync_run: banco de demonstração segue ABERTO.
        val ds = bancoNovo()
        execucao(ds, "RETENCAO")
        assertTrue(TravaDpo(dev, ds).liberada)
    }

    @Test
    fun incrementalOuReconciliacaoFecham() {
        for (kind in listOf("INCREMENTAL", "RECONCILIACAO")) {
            val ds = bancoNovo()
            execucao(ds, "RETENCAO")
            execucao(ds, kind)
            assertFalse(TravaDpo(dev, ds).liberada, kind)
        }
    }

    @Test
    fun semBancoFecha() {
        // Sem como provar que o banco é de demonstração, a porta fica fechada (fail-closed).
        assertFalse(TravaDpo(dev, null).liberada)
        assertFailsWith<BloqueadoPeloDpoException> { TravaDpo(dev, null).exigir(ModuloSensivel.INTEGRIDADE) }
    }

    @Test
    fun marcaDeSincronizacaoSemExecucaoRegistradaTambemFecha() {
        // A retenção pode apagar o histórico de execuções; a marca d'água da sincronização continua dizendo que houve dado real.
        val ds = bancoNovo()
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("INSERT INTO sync_state(instance, watermark) VALUES ('samsung', 5)") } }
        assertFalse(TravaDpo(dev, ds).liberada)
    }

    @Test
    fun producaoSemAceiteFechaOsDoisModulos() {
        val trava = TravaDpo(Settings(dashboardDevMode = false), bancoNovo())
        assertFalse(trava.liberada)
        assertEquals("AGUARDANDO_ACEITE_DPO", trava.motivo())
        for (m in ModuloSensivel.entries) {
            val e = assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(m) }
            assertEquals(m, e.modulo)
            assertEquals("AGUARDANDO_ACEITE_DPO", e.message, "a mensagem é só o código estável")
        }
    }

    @Test
    fun desenvolvimentoComJiraLigadoTambemFecha() {
        // Dados reais sem login e sem aceite: a porta continua fechada, mesmo com o banco vazio.
        val trava = TravaDpo(dev.copy(jiraLiveEnabled = true), bancoNovo())
        assertFalse(trava.liberada)
        assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(ModuloSensivel.INTEGRIDADE) }
    }

    @Test
    fun erroAoConsultarOBancoFecha() {
        // Banco sem as tabelas (ou inacessível): não dá para provar que é demonstração, então fecha.
        val semEsquema = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 1)
        val trava = TravaDpo(dev, semEsquema)
        assertFalse(trava.liberada)
        assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(ModuloSensivel.INTEGRIDADE) }
    }
}
