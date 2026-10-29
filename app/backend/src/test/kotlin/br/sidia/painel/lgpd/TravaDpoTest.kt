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
    fun migracaoQueZeraACursorNaoAbreATravaSobreDadoReal() {
        // A V7 zera a marca d'água; sem sync_run (a retenção os apagou), a linha de sync_state da instância ainda prova a sincronização.
        val ds = bancoNovo()
        ds.connection.use { c ->
            c.createStatement().use {
                it.executeUpdate("INSERT INTO sync_state(instance, watermark, ultima_reconciliacao) VALUES ('samsung', NULL, 2000)")
            }
        }
        assertFalse(TravaDpo(dev, ds).liberada)
        // Só a linha de uma instância já basta (suspensão, marcador de falhas...), mesmo sem reconciliação nem marca d'água.
        val outro = bancoNovo()
        outro.connection.use { c -> c.createStatement().use { it.executeUpdate("INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('sidia', 3)") } }
        assertFalse(TravaDpo(dev, outro).liberada)
    }

    @Test
    fun demonstracaoComRetencaoContinuaAberta() {
        // A retenção grava sync_run e, no reinício, o marcador de falhas da instância "-": nada disso é sincronização real.
        val ds = bancoNovo()
        execucao(ds, "RETENCAO")
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('-', 1)") } }
        assertTrue(TravaDpo(dev, ds).liberada)
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

    private val prod = mapOf("DASHBOARD_DEV_MODE" to "false") + br.sidia.painel.ENV_OIDC_DE_TESTE
    private fun comAceite(ref: String = "DPO-2026-11") = prod + mapOf("DASHBOARD_DPO_ACEITE" to "true", "DASHBOARD_DPO_ACEITE_REF" to ref)

    @Test
    fun padraoDoAmbienteVazioEhLiberadaSoPorqueEhDevOffline() {
        val s = Settings.fromEnv(emptyMap())
        assertFalse(s.lgpd.aceiteDpo)
        assertTrue(TravaDpo(s, bancoNovo()).liberada)
    }

    @Test
    fun aceiteLiberaEmProducaoMesmoComSincronizacaoReal() {
        val ds = bancoNovo()
        comSyncReal(ds)
        val trava = TravaDpo(Settings.fromEnv(comAceite()), ds)
        assertTrue(trava.liberada)
        assertNull(trava.motivo())
        for (m in ModuloSensivel.entries) trava.exigir(m)
    }

    @Test
    fun semAceiteProducaoFecha() {
        assertFalse(TravaDpo(Settings.fromEnv(prod), bancoNovo()).liberada)
        // aceite explicitamente falso e vazio também
        assertFalse(TravaDpo(Settings.fromEnv(prod + ("DASHBOARD_DPO_ACEITE" to "false")), bancoNovo()).liberada)
        assertFalse(TravaDpo(Settings.fromEnv(prod + ("DASHBOARD_DPO_ACEITE" to "  ")), bancoNovo()).liberada)
    }

    @Test
    fun valorInvalidoFalhaNaSubida() {
        val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(prod + ("DASHBOARD_DPO_ACEITE" to "sim")) }
        assertTrue("DASHBOARD_DPO_ACEITE" in e.message.orEmpty())
    }

    @Test
    fun aceiteSemReferenciaFalha() {
        for (env in listOf(prod + ("DASHBOARD_DPO_ACEITE" to "true"), comAceite("  "))) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(env) }
            assertEquals("DASHBOARD_DPO_ACEITE_REF obrigatória com DASHBOARD_DPO_ACEITE=true", e.message)
        }
    }

    @Test
    fun referenciaInvalidaFalhaCitandoSoONome() {
        for (ref in listOf("a b", "x".repeat(81), "DPO;rm", "ção")) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(comAceite(ref)) }
            assertTrue("DASHBOARD_DPO_ACEITE_REF" in e.message.orEmpty())
            assertFalse(ref in e.message.orEmpty(), "o valor não aparece na mensagem")
        }
        // 80 caracteres e os símbolos permitidos passam
        assertEquals("a".repeat(80), Settings.fromEnv(comAceite("a".repeat(80))).lgpd.aceiteRef)
        assertEquals("DPO-2026/11_a.b", Settings.fromEnv(comAceite("DPO-2026/11_a.b")).lgpd.aceiteRef)
    }

    @Test
    fun referenciaSemAceiteEhIgnoradaEAceiteNaoVazaNoToString() {
        val s = Settings.fromEnv(prod + ("DASHBOARD_DPO_ACEITE_REF" to "DPO-1"))
        assertFalse(s.lgpd.aceiteDpo)
        // o objeto montado à mão com aceite e sem referência válida nem existe
        assertFailsWith<IllegalArgumentException> { LgpdSettings(aceiteDpo = true, aceiteRef = null) }
        assertFailsWith<IllegalArgumentException> { LgpdSettings(aceiteDpo = true, aceiteRef = "a b") }
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
