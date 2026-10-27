package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes contra SQLite real em arquivo temporário; nunca H2 nem mock. */
class SyncStateStoreTest {
    companion object {
        private val pg = BancoEmArquivo()
    }

    private lateinit var ds: DataSource
    private lateinit var store: SqliteSyncStateStore

    private fun t(s: String) = Instant.parse(s)

    @BeforeTest
    fun preparar() {
        ds = Database.connect(pg.jdbcUrl)
        Database.migrate(ds)
        store = SqliteSyncStateStore(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as? AutoCloseable)?.close()
        pg.limpar()
    }

    private fun <T> consulta(sql: String, leitor: (java.sql.ResultSet) -> T): T =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); leitor(it) } }

    @Test
    fun migracaoEhIdempotenteEPreservaDados() {
        ds.connection.use {
            it.createStatement().execute("INSERT INTO issue(id, key, instance, project) VALUES (1, 'TG-1', 'main', 'TG')")
        }
        Database.migrate(ds)
        assertEquals(1, consulta("SELECT count(*) FROM issue") { it.getInt(1) })
        assertNull(consulta("SELECT jira_id FROM issue") { it.getObject(1) })
        assertNull(consulta("SELECT removida_em FROM issue") { it.getObject(1) })
    }

    @Test
    fun cursorAusenteEhNulo() {
        assertNull(store.cursor("samsung"))
    }

    @Test
    fun cursorAvancaENuncaRecua() {
        store.avancarCursor("samsung", t("2026-10-30T12:20:00Z"))
        store.avancarCursor("samsung", t("2026-10-30T12:10:00Z"))
        assertEquals(t("2026-10-30T12:20:00Z"), store.cursor("samsung"))
        store.avancarCursor("samsung", t("2026-10-30T12:30:00Z"))
        assertEquals(t("2026-10-30T12:30:00Z"), store.cursor("samsung"))
    }

    @Test
    fun execucaoGuardaSoCodigos() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(id, RunStatus.FALHA, RunResumo(requisicoes = 3), "HTTP_503", 503, t("2026-10-30T12:01:00Z"))
        val r = store.ultimas("samsung").single()
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("HTTP_503", r.codigoErro)
        assertEquals(503, r.httpStatus)
        assertEquals(3, r.resumo.requisicoes)
        assertEquals(t("2026-10-30T12:01:00Z"), r.finishedAt)
        // a única coluna de texto livre candidata é codigo_erro (mais as de identificação/enumeração)
        val textos = ds.connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT name FROM pragma_table_info('sync_run') WHERE upper(type)='TEXT'",
            ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toSet() }
        }
        assertEquals(setOf("instance", "kind", "status", "codigo_erro"), textos)
    }

    @Test
    fun falhasSeguidasZeraAposOk() {
        fun rodar(status: RunStatus, n: Int) {
            val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
            store.concluir(id, status, RunResumo(), if (status == RunStatus.FALHA) "TIMEOUT" else null, null, t("2026-10-30T12:00:30Z"))
            assertEquals(n, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        }
        rodar(RunStatus.FALHA, 1)
        rodar(RunStatus.FALHA, 2)
        rodar(RunStatus.OK, 0)
        rodar(RunStatus.FALHA, 1)
        assertEquals(0, store.falhasSeguidas("samsung", SyncKind.RECONCILIACAO))
    }

    @Test
    fun codigosDeLimiteNaoContamComoFalhaSeguida() {
        fun falhar(codigo: String) {
            val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
            store.concluir(id, RunStatus.FALHA, RunResumo(), codigo, null, t("2026-10-30T12:00:30Z"))
        }
        listOf("TETO_ITENS", "ORCAMENTO_REQUISICOES", "TEMPO_ESGOTADO", "TETO_ITENS", "ORCAMENTO_REQUISICOES").forEach(::falhar)
        assertEquals(0, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        // Falhas reais intercaladas com limites: só as reais contam.
        falhar("HTTP_5XX")
        falhar("TETO_ITENS")
        falhar("REDE")
        assertEquals(2, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
    }

    @Test
    fun suspensaoPersisteAteLimpar() {
        assertNull(store.suspensa("samsung"))
        store.suspender("samsung", "HTTP_401", t("2026-10-30T12:00:00Z"))
        assertEquals("HTTP_401", store.suspensa("samsung"))
        // outra réplica (outro objeto, outra conexão) enxerga a mesma suspensão
        assertEquals("HTTP_401", SqliteSyncStateStore(ds).suspensa("samsung"))
        assertEquals(1, store.limparSuspensoes())
        assertNull(store.suspensa("samsung"))
        assertEquals(0, store.limparSuspensoes())
    }

    @Test
    fun travaExclusivaEntreDuasConexoes() = runBlocking {
        val dentro = CompletableDeferred<Unit>()
        val liberar = CompletableDeferred<Unit>()
        val primeira = async {
            store.comTrava("samsung") {
                dentro.complete(Unit)
                liberar.await()
                "primeira"
            }
        }
        dentro.await()
        assertNull(store.comTrava("samsung") { "segunda" })
        // outra instância não é afetada
        assertEquals("outra", store.comTrava("main") { "outra" })
        liberar.complete(Unit)
        assertEquals("primeira", primeira.await())
        assertEquals("terceira", store.comTrava("samsung") { "terceira" })
    }

    @Test
    fun orfasViramFalhaInterrompida() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        val ok = store.iniciar("samsung", SyncKind.RECONCILIACAO, t("2026-10-30T12:00:00Z"))
        store.concluir(ok, RunStatus.OK, RunResumo(), null, null, t("2026-10-30T12:00:10Z"))
        val inicio = t("2026-10-30T13:00:00Z")
        // iniciada no/depois do início do processo atual: não é órfã
        val nova = store.iniciar("samsung", SyncKind.INCREMENTAL, inicio)
        assertEquals(1, store.fecharOrfas(inicio, inicio))
        val r = store.ultimas("samsung").single { it.id == id }
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("INTERROMPIDA", r.codigoErro)
        assertEquals(inicio, r.finishedAt)
        assertEquals(RunStatus.EM_ANDAMENTO, store.ultimas("samsung").single { it.id == nova }.status)
        assertEquals(0, store.fecharOrfas(inicio, inicio))
    }

    @Test
    fun execucaoIniciadaDepoisDoInicioDoProcesso_naoEhFechadaComoOrfa() = runBlocking {
        val dentro = CompletableDeferred<Long>()
        val liberar = CompletableDeferred<Unit>()
        val replicaA = SqliteSyncStateStore(ds)
        val a = async {
            replicaA.comTrava("samsung") {
                val id = replicaA.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
                dentro.complete(id)
                liberar.await()
                replicaA.concluir(id, RunStatus.OK, RunResumo(issues = 1), null, null, t("2026-10-30T12:05:00Z"))
            }
        }
        val idA = dentro.await()
        // a limpeza de órfãs só atinge execuções iniciadas antes do início deste processo
        val replicaB = SqliteSyncStateStore(ds)
        assertEquals(0, replicaB.fecharOrfas(t("2026-10-30T11:59:00Z"), t("2026-10-30T12:02:00Z")))
        assertEquals(RunStatus.EM_ANDAMENTO, store.ultimas("samsung").single { it.id == idA }.status)
        liberar.complete(Unit)
        assertEquals(true, a.await())
        val r = store.ultimas("samsung").single { it.id == idA }
        assertEquals(RunStatus.OK, r.status)
        assertNull(r.codigoErro)
    }

    @Test
    fun concluirNaoSobrescreveExecucaoJaInterrompida() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        assertEquals(1, store.fecharOrfas(t("2026-10-30T13:00:00Z"), t("2026-10-30T13:00:00Z")))
        assertEquals(false, store.concluir(id, RunStatus.OK, RunResumo(issues = 9), null, null, t("2026-10-30T13:05:00Z")))
        val r = store.ultimas("samsung").single()
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("INTERROMPIDA", r.codigoErro)
        assertEquals(0, r.resumo.issues)
    }

    private fun falha(instance: String = "samsung", kind: SyncKind = SyncKind.INCREMENTAL, codigo: String = "HTTP_503"): Long {
        val id = store.iniciar(instance, kind, t("2026-10-30T12:00:00Z"))
        store.concluir(id, RunStatus.FALHA, RunResumo(), codigo, null, t("2026-10-30T12:00:30Z"))
        return id
    }

    @Test
    fun reinicioZeraContagemDeFalhas() {
        repeat(3) { falha() }
        assertEquals(3, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        store.suspender("samsung", "FALHAS_SEGUIDAS", t("2026-10-30T12:10:00Z"))
        assertEquals(1, store.limparSuspensoes())
        assertEquals(0, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        falha()
        assertEquals(1, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        assertNull(store.suspensa("samsung"))
    }

    @Test
    fun reinicioZeraContagemMesmoSemSuspensao() {
        falha()
        falha()
        store.limparSuspensoes()
        assertEquals(0, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
    }

    @Test
    fun interrompidaCanceladaEEmAndamentoNaoContamNemZeram() {
        falha()
        falha(codigo = "INTERROMPIDA")
        val canc = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(canc, RunStatus.CANCELADA, RunResumo(), null, null, t("2026-10-30T12:00:10Z"))
        store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        assertEquals(1, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        falha()
        assertEquals(2, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
    }

    @Test
    fun falhasIsoladasPorTipoEInstancia() {
        falha()
        falha(kind = SyncKind.RECONCILIACAO)
        falha(instance = "main")
        val ok = store.iniciar("main", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(ok, RunStatus.OK, RunResumo(), null, null, t("2026-10-30T12:00:10Z"))
        // OK de outro tipo/instância não zera
        assertEquals(1, store.falhasSeguidas("samsung", SyncKind.INCREMENTAL))
        assertEquals(1, store.falhasSeguidas("samsung", SyncKind.RECONCILIACAO))
        assertEquals(0, store.falhasSeguidas("main", SyncKind.INCREMENTAL))
        assertNull(store.cursor("main"))
        store.avancarCursor("samsung", t("2026-10-30T12:20:00Z"))
        assertNull(store.cursor("main"))
        store.suspender("samsung", "HTTP_401", t("2026-10-30T12:00:00Z"))
        assertNull(store.suspensa("main"))
    }

    @Test
    fun codigoLivreEhRejeitado() {
        val id = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        assertFailsWith<IllegalArgumentException> {
            store.concluir(id, RunStatus.FALHA, RunResumo(), "erro https://jira/x?token=abc", null, t("2026-10-30T12:01:00Z"))
        }
        assertFailsWith<IllegalArgumentException> { store.suspender("samsung", "http 401", t("2026-10-30T12:01:00Z")) }
        assertEquals(RunStatus.EM_ANDAMENTO, store.ultimas("samsung").single().status)
    }

    @Test
    fun travaLiberadaAposExcecaoEAposCancelamento() = runBlocking {
        assertFailsWith<IllegalStateException> { store.comTrava("samsung") { error("falha do bloco") } }
        assertEquals("a", store.comTrava("samsung") { "a" })

        val dentro = CompletableDeferred<Unit>()
        val job = async { store.comTrava("samsung") { dentro.complete(Unit); awaitCancellation() } }
        dentro.await()
        assertNull(store.comTrava("samsung") { "x" })
        job.cancelAndJoin()
        assertEquals("b", store.comTrava("samsung") { "b" })
    }

    @Test
    fun chavesDeUsuarioUpsert() {
        store.gravarChavesDeUsuario("samsung", mapOf("JIRAUSER10" to "ana"), t("2026-10-30T12:00:00Z"))
        store.gravarChavesDeUsuario("samsung", mapOf("JIRAUSER10" to "bruno", "JIRAUSER11" to "carla"), t("2026-10-30T13:00:00Z"))
        assertEquals(mapOf("JIRAUSER10" to "bruno", "JIRAUSER11" to "carla"), store.chavesDeUsuario("samsung"))
        assertEquals(emptyMap(), store.chavesDeUsuario("main"))
        val visto = consulta("SELECT visto_em FROM jira_user_key WHERE user_key='JIRAUSER10'") {
            it.getTimestamp(1).toInstant()
        }
        assertEquals(t("2026-10-30T13:00:00Z"), visto)
    }

    @Test
    fun reconciliacaoEUltimaPorTipo() {
        assertNull(store.ultimaReconciliacao("samsung"))
        store.marcarReconciliacao("samsung", t("2026-10-30T03:00:00Z"))
        assertEquals(t("2026-10-30T03:00:00Z"), store.ultimaReconciliacao("samsung"))
        // não apaga o cursor nem recria linha
        store.avancarCursor("samsung", t("2026-10-30T12:00:00Z"))
        assertEquals(t("2026-10-30T03:00:00Z"), store.ultimaReconciliacao("samsung"))

        val a = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:00:00Z"))
        store.concluir(a, RunStatus.OK, RunResumo(issues = 2), null, null, t("2026-10-30T12:00:05Z"))
        val b = store.iniciar("samsung", SyncKind.INCREMENTAL, t("2026-10-30T12:10:00Z"))
        val r = store.iniciar("-", SyncKind.RETENCAO, t("2026-10-30T04:00:00Z"))
        val porTipo = store.ultimaPorTipo("samsung")
        assertEquals(setOf(SyncKind.INCREMENTAL), porTipo.keys)
        assertEquals(b, porTipo.getValue(SyncKind.INCREMENTAL).id)
        // instância nula = retenção, gravada como '-'
        val ret = store.ultimaPorTipo(null)
        assertEquals(r, assertNotNull(ret[SyncKind.RETENCAO]).id)
        assertTrue(store.ultimas("samsung").none { it.kind == SyncKind.RETENCAO })
    }
}
