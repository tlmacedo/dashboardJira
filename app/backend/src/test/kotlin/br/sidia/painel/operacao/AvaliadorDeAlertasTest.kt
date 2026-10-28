package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.sync.INSTANCIA_RETENCAO
import br.sidia.painel.sync.RunResumo
import br.sidia.painel.sync.RunStatus
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.sync.SyncKind
import br.sidia.painel.time.BusinessCalendar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvaliadorDeAlertasTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val AGORA = Instant.parse("2026-10-30T12:00:00Z")
    }

    private class Cenario(
        val settings: Settings,
        val relogio: RelogioDeTeste,
        val ds: DataSource,
        val estado: SqliteSyncStateStore,
        val backups: SqliteBackupRunStore,
        val metricas: Metricas,
        val saidas: MutableList<Triple<Alerta, Boolean, Instant>>,
        val avaliador: AvaliadorDeAlertas,
        val disco: LongArray,
    )

    private val aAbrir = mutableListOf<DataSource>()
    private val saidaOriginal: PrintStream = System.out

    @AfterEach
    fun fechar() {
        aAbrir.forEach { (it as AutoCloseable).close() }
        aAbrir.clear()
        System.setOut(saidaOriginal)
    }

    private fun cenario(
        vararg env: Pair<String, String>,
        agora: Instant = AGORA,
        // Pouco tempo no ar: as regras de "nunca rodou" ainda não disparam; os testes que as exercitam passam outro valor.
        noArHa: Duration = Duration.ofMinutes(10),
        calendario: BusinessCalendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), ZoneId.of("America/Manaus")),
    ): Cenario {
        val settings = Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "true") + env)
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        aAbrir.add(ds)
        val relogio = RelogioDeTeste(agora)
        val estado = SqliteSyncStateStore(ds)
        val backups = SqliteBackupRunStore(ds)
        val metricas = Metricas()
        val saidas = mutableListOf<Triple<Alerta, Boolean, Instant>>()
        val disco = longArrayOf(10L * 1024 * 1024 * 1024)
        val avaliador = AvaliadorDeAlertas(
            estado, backups, metricas, settings, calendario, { disco[0] },
            listOf(object : SaidaDeAlertas {
                override fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant) {
                    saidas.add(Triple(alerta, ativo, quando))
                }
            }),
            relogio, noArDesde = agora.minus(noArHa),
        )
        return Cenario(settings, relogio, ds, estado, backups, metricas, saidas, avaliador, disco)
    }

    private val producao = arrayOf("DASHBOARD_DEV_MODE" to "false")
    /** Jira ligado em produção (modo dev desligado), para não acender o alerta de modo dev com Jira ligado. */
    private val live = arrayOf("JIRA_LIVE_ENABLED" to "true", "DASHBOARD_DEV_MODE" to "false")

    /** Grava uma execução já concluída. */
    private fun Cenario.execucao(
        instancia: String, tipo: SyncKind, status: RunStatus, fimHa: Duration, codigo: String? = null,
    ) {
        val fim = relogio.agora.minus(fimHa)
        val id = estado.iniciar(instancia, tipo, fim.minusSeconds(5))
        estado.concluir(id, status, RunResumo(), codigo, null, fim)
    }

    private fun Cenario.backup(tipo: TipoDeBackup, status: StatusDeBackup, fimHa: Duration, codigo: String? = null) {
        val fim = relogio.agora.minus(fimHa)
        backups.registrar(
            RegistroDeBackup(
                tipo, status, fim.minusSeconds(30), fim, if (status == StatusDeBackup.OK) "painel-2026-10-30-020000.db" else null,
                if (status == StatusDeBackup.OK) 1000 else null, null, codigo,
            ),
        )
    }

    private fun Cenario.codigos(): List<CodigoAlerta> = avaliador.avaliar().map { it.codigo }

    @Test
    fun semNadaAvaliadoNaoHaAlerta() {
        assertEquals(emptyList(), cenario().avaliador.avaliar())
    }

    @Test
    fun suspensaoViraAlerta() {
        val c = cenario()
        c.estado.suspender("samsung", "HTTP_401", AGORA)
        val a = c.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.SYNC_SUSPENSA, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
        assertEquals("samsung", a.instancia)
    }

    @Test
    fun syncAtrasadaDepoisDe3Intervalos() {
        val c = cenario(*live, "SYNC_INTERVALO_MIN" to "10")
        c.execucao("samsung", SyncKind.INCREMENTAL, RunStatus.OK, Duration.ofMinutes(29))
        assertEquals(emptyList(), c.codigos(), "29 min = menos de 3 intervalos")
        val atrasada = cenario(*live, "SYNC_INTERVALO_MIN" to "10")
        atrasada.execucao("samsung", SyncKind.INCREMENTAL, RunStatus.OK, Duration.ofMinutes(31))
        assertEquals(listOf(CodigoAlerta.SYNC_ATRASADA), atrasada.codigos())
        assertEquals(GravidadeAlerta.ATENCAO, atrasada.avaliador.avaliar().single().gravidade)
    }

    @Test
    fun syncNuncaRodouSoAlertaDepoisDeUmaHoraNoAr() {
        assertEquals(listOf(CodigoAlerta.SYNC_ATRASADA), cenario(*live, noArHa = Duration.ofMinutes(61)).codigos())
        assertEquals(emptyList(), cenario(*live, noArHa = Duration.ofMinutes(59)).codigos())
    }

    @Test
    fun syncAtrasadaSoComAFlagLigada() {
        val c = cenario("SYNC_INTERVALO_MIN" to "10")
        c.execucao("samsung", SyncKind.INCREMENTAL, RunStatus.OK, Duration.ofHours(5))
        assertEquals(emptyList(), c.codigos())
    }

    @Test
    fun reconciliacaoSuspeita() {
        val c = cenario()
        c.execucao("samsung", SyncKind.RECONCILIACAO, RunStatus.FALHA, Duration.ofHours(1), "RECONCILIACAO_SUSPEITA")
        val a = c.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.RECONCILIACAO_SUSPEITA, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
    }

    @Test
    fun reconciliacaoSuspeitaSomeQuandoAProximaDaCerto() {
        val c = cenario()
        c.execucao("samsung", SyncKind.RECONCILIACAO, RunStatus.FALHA, Duration.ofHours(30), "RECONCILIACAO_SUSPEITA")
        c.execucao("samsung", SyncKind.RECONCILIACAO, RunStatus.OK, Duration.ofHours(2))
        assertEquals(emptyList(), c.codigos())
    }

    @Test
    fun reconciliacaoAtrasada26h() {
        val atrasada = cenario(*live)
        atrasada.execucao("samsung", SyncKind.INCREMENTAL, RunStatus.OK, Duration.ofMinutes(1))
        atrasada.execucao("samsung", SyncKind.RECONCILIACAO, RunStatus.OK, Duration.ofHours(27))
        assertEquals(listOf(CodigoAlerta.RECONCILIACAO_ATRASADA), atrasada.codigos())
        val emDia = cenario(*live)
        emDia.execucao("samsung", SyncKind.INCREMENTAL, RunStatus.OK, Duration.ofMinutes(1))
        emDia.execucao("samsung", SyncKind.RECONCILIACAO, RunStatus.OK, Duration.ofHours(25))
        assertEquals(emptyList(), emDia.codigos())
    }

    @Test
    fun retencaoFalhou() {
        val falha = cenario()
        falha.execucao(INSTANCIA_RETENCAO, SyncKind.RETENCAO, RunStatus.FALHA, Duration.ofHours(1), "ERRO_INTERNO")
        assertEquals(listOf(CodigoAlerta.RETENCAO_FALHOU), falha.codigos())
        val velha = cenario()
        velha.execucao(INSTANCIA_RETENCAO, SyncKind.RETENCAO, RunStatus.OK, Duration.ofHours(27))
        assertEquals(listOf(CodigoAlerta.RETENCAO_FALHOU), velha.codigos())
        val ok = cenario()
        ok.execucao(INSTANCIA_RETENCAO, SyncKind.RETENCAO, RunStatus.OK, Duration.ofHours(1))
        assertEquals(emptyList(), ok.codigos())
    }

    @Test
    fun accessLogFalhando() {
        val c = cenario()
        c.avaliador.avaliarEPublicar() // primeira amostra: contador em 0
        assertEquals(emptyList(), c.codigos())
        c.metricas.falhaDeAccessLog()
        c.relogio.avancar(Duration.ofMinutes(5))
        val a = c.avaliador.avaliarEPublicar().single()
        assertEquals(CodigoAlerta.ACCESS_LOG_FALHANDO, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
        // Sem novas falhas, sai da janela de 60 min e o alerta se resolve.
        c.relogio.avancar(Duration.ofMinutes(30))
        c.avaliador.avaliarEPublicar()
        assertEquals(listOf(CodigoAlerta.ACCESS_LOG_FALHANDO), c.codigos(), "ainda dentro da hora")
        c.relogio.avancar(Duration.ofMinutes(40))
        c.avaliador.avaliarEPublicar()
        c.relogio.avancar(Duration.ofMinutes(5))
        assertEquals(emptyList(), c.avaliador.avaliarEPublicar())
    }

    @Test
    fun patExpirandoEm14Dias() {
        // hoje = 2026-10-30 em Manaus
        assertEquals(listOf(CodigoAlerta.PAT_EXPIRANDO), cenario(*live, "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-11-13").codigos())
        assertEquals(emptyList(), cenario(*live, "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-11-14").codigos())
        val hojeEhOUltimoDia = cenario(*live, "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-10-30").avaliador.avaliar().single()
        assertEquals(CodigoAlerta.PAT_EXPIRANDO, hojeEhOUltimoDia.codigo)
        assertEquals("samsung", hojeEhOUltimoDia.instancia)
    }

    @Test
    fun patExpirado() {
        val a = cenario(*live, "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-10-29").avaliador.avaliar().single()
        assertEquals(CodigoAlerta.PAT_EXPIRADO, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
    }

    @Test
    fun patSemFlagNaoAlerta() {
        assertEquals(emptyList(), cenario("SYNC_PAT_VALIDADE_SAMSUNG" to "2020-01-01").codigos())
    }

    @Test
    fun calendarioSemProximoAno() {
        val exemplo = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), ZoneId.of("America/Manaus"))
        // o exemplo vai até 2030
        assertEquals(
            listOf(CodigoAlerta.CALENDARIO_SEM_PROXIMO_ANO),
            cenario(agora = Instant.parse("2030-11-01T12:00:00Z"), calendario = exemplo).codigos(),
        )
        assertEquals(emptyList(), cenario(agora = Instant.parse("2030-10-31T12:00:00Z"), calendario = exemplo).codigos())
        assertEquals(emptyList(), cenario(agora = Instant.parse("2026-11-01T12:00:00Z"), calendario = exemplo).codigos(), "2027 está no exemplo")
    }

    @Test
    fun discoBaixo() {
        val c = cenario("PAINEL_DISCO_MIN_MB" to "500")
        c.disco[0] = 400L * 1024 * 1024
        val a = c.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.DISCO_BAIXO, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
        c.disco[0] = 600L * 1024 * 1024
        assertEquals(emptyList(), c.codigos())
    }

    @Test
    fun bancoSuspeito() {
        val c = cenario()
        c.backup(TipoDeBackup.BACKUP, StatusDeBackup.FALHA, Duration.ofHours(1), "INTEGRIDADE_FALHOU")
        val a = c.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.BANCO_SUSPEITO, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
        // Outro motivo de falha (disco, pasta) não indica banco suspeito.
        val outro = cenario()
        outro.backup(TipoDeBackup.BACKUP, StatusDeBackup.FALHA, Duration.ofHours(1), "DISCO_INSUFICIENTE")
        assertEquals(emptyList(), outro.codigos())
    }

    @Test
    fun backupAtrasadoSoEmProducao() {
        val atrasado = cenario(*producao)
        atrasado.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(27))
        atrasado.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK, Duration.ofHours(27))
        val a = atrasado.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.BACKUP_ATRASADO, a.codigo)
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
        val emDia = cenario(*producao)
        emDia.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(25))
        emDia.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK, Duration.ofHours(25))
        assertEquals(emptyList(), emDia.codigos())
        val dev = cenario()
        dev.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofDays(30))
        assertEquals(emptyList(), dev.codigos(), "em desenvolvimento não há backup obrigatório")
    }

    @Test
    fun backupNuncaFeitoSoAlertaDepoisDe26hNoAr() {
        assertTrue(CodigoAlerta.BACKUP_ATRASADO in cenario(*producao, noArHa = Duration.ofHours(27)).codigos())
        assertFalse(CodigoAlerta.BACKUP_ATRASADO in cenario(*producao, noArHa = Duration.ofHours(25)).codigos())
    }

    @Test
    fun restauracaoNaoTestada3Dias() {
        val velha = cenario(*producao)
        velha.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(2))
        velha.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK, Duration.ofHours(73))
        val a = velha.avaliador.avaliar().single()
        assertEquals(CodigoAlerta.RESTAURACAO_NAO_TESTADA, a.codigo)
        assertEquals(GravidadeAlerta.ATENCAO, a.gravidade)
        val recente = cenario(*producao)
        recente.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(2))
        recente.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK, Duration.ofHours(71))
        assertEquals(emptyList(), recente.codigos())
    }

    @Test
    fun restauracaoFalhouEhCritica() {
        val c = cenario(*producao, noArHa = Duration.ofDays(5))
        c.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(2))
        c.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.OK, Duration.ofDays(10))
        c.backup(TipoDeBackup.RESTAURACAO, StatusDeBackup.FALHA, Duration.ofHours(1), "INTEGRIDADE_FALHOU")
        // (a retenção também nunca rodou neste cenário de 5 dias; só interessa o alerta da restauração)
        val a = c.avaliador.avaliar().single { it.codigo == CodigoAlerta.RESTAURACAO_NAO_TESTADA }
        assertEquals(GravidadeAlerta.CRITICO, a.gravidade)
    }

    @Test
    fun modoDevComJiraLigado() {
        val a = cenario("JIRA_LIVE_ENABLED" to "true").avaliador.avaliar()
        assertTrue(CodigoAlerta.MODO_DEV_COM_JIRA_LIGADO in a.map { it.codigo })
        assertEquals(GravidadeAlerta.CRITICO, a.first { it.codigo == CodigoAlerta.MODO_DEV_COM_JIRA_LIGADO }.gravidade)
        assertFalse(CodigoAlerta.MODO_DEV_COM_JIRA_LIGADO in cenario(*producao, *live).codigos())
    }

    @Test
    fun criticoVemAntesDeAtencaoDepoOCodigo() {
        val c = cenario(*live, "SYNC_PAT_VALIDADE_SAMSUNG" to "2026-11-05")
        c.estado.suspender("samsung", "HTTP_401", AGORA)
        c.disco[0] = 1
        c.execucao(INSTANCIA_RETENCAO, SyncKind.RETENCAO, RunStatus.FALHA, Duration.ofHours(1), "ERRO_INTERNO")
        val ordem = c.avaliador.avaliar().map { it.gravidade to it.codigo }
        assertEquals(
            listOf(
                GravidadeAlerta.CRITICO to CodigoAlerta.SYNC_SUSPENSA,
                GravidadeAlerta.CRITICO to CodigoAlerta.DISCO_BAIXO,
                GravidadeAlerta.ATENCAO to CodigoAlerta.RETENCAO_FALHOU,
                GravidadeAlerta.ATENCAO to CodigoAlerta.PAT_EXPIRANDO,
            ),
            ordem,
        )
    }

    @Test
    fun suspensaoViraAlertaUmaVez() {
        val saida = ByteArrayOutputStream()
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(PrintStream(saida, true, Charsets.UTF_8))
        val c = cenario()
        c.estado.suspender("samsung", "HTTP_401", AGORA)
        repeat(3) { c.avaliador.avaliarEPublicar() }
        assertEquals(1, c.saidas.size)
        assertTrue(c.saidas.single().second)
        assertEquals(CodigoAlerta.SYNC_SUSPENSA, c.saidas.single().first.codigo)
        assertEquals(1, Regex("ALERTA SYNC_SUSPENSA ATIVO").findAll(saida.toString(Charsets.UTF_8)).count())

        c.estado.limparSuspensoes()
        repeat(2) { c.avaliador.avaliarEPublicar() }
        assertEquals(2, c.saidas.size)
        assertFalse(c.saidas.last().second)
        assertTrue("ALERTA SYNC_SUSPENSA RESOLVIDO" in saida.toString(Charsets.UTF_8))
    }

    @Test
    fun desdeFicaNoInstanteEmQueAtivou() {
        val c = cenario()
        c.estado.suspender("samsung", "HTTP_401", AGORA)
        c.avaliador.avaliarEPublicar()
        c.relogio.avancar(Duration.ofHours(1))
        assertEquals(AGORA, c.avaliador.avaliar().single().desde)
    }

    @Test
    fun metricaDeAlertaAcompanha() {
        val c = cenario()
        c.estado.suspender("samsung", "HTTP_401", AGORA)
        c.avaliador.avaliarEPublicar()
        fun valor() = c.metricas.texto().lines().first { it.startsWith("painel_alerta_ativo") && "SYNC_SUSPENSA" in it }.substringAfterLast(' ').toDouble()
        assertEquals(1.0, valor())
        c.estado.limparSuspensoes()
        c.avaliador.avaliarEPublicar()
        assertEquals(0.0, valor())
        assertEquals(CodigoAlerta.entries.size, c.metricas.texto().lines().count { it.startsWith("painel_alerta_ativo") })
    }

    @Test
    fun publicaDiscoEUltimoBackupNasMetricas() {
        val c = cenario()
        c.backup(TipoDeBackup.BACKUP, StatusDeBackup.OK, Duration.ofHours(2))
        c.avaliador.avaliarEPublicar()
        val texto = c.metricas.texto()
        assertTrue(texto.lines().any { it.startsWith("painel_disco_livre_bytes") && "volume=\"banco\"" in it })
        assertTrue(texto.lines().any { it.startsWith("painel_backup_ultimo_ok_timestamp_segundos") && !it.endsWith(" 0.0") })
    }

    @Test
    fun saidaQueFalhaNaoImpedeAsOutras() {
        val chamadas = mutableListOf<String>()
        val settings = Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "true"))
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        aAbrir.add(ds)
        val estado = SqliteSyncStateStore(ds)
        estado.suspender("samsung", "HTTP_401", AGORA)
        val avaliador = AvaliadorDeAlertas(
            estado, SqliteBackupRunStore(ds), Metricas(), settings,
            BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), ZoneId.of("America/Manaus")),
            { 10L * 1024 * 1024 * 1024 },
            listOf(
                object : SaidaDeAlertas {
                    override fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant) = error("saída quebrada")
                },
                object : SaidaDeAlertas {
                    override fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant) {
                        chamadas.add(alerta.codigo.name)
                    }
                },
            ),
            RelogioDeTeste(AGORA),
        )
        avaliador.avaliarEPublicar()
        assertEquals(listOf("SYNC_SUSPENSA"), chamadas)
    }
}
