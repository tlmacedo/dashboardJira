package br.sidia.painel.operacao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRunStoreTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private fun comBanco(bloco: (DataSource, SqliteBackupRunStore) -> Unit) {
        val ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()))
        Database.migrate(ds)
        (ds as AutoCloseable).use { bloco(ds, SqliteBackupRunStore(ds)) }
    }

    private val t0 = Instant.parse("2026-10-30T06:00:00Z")

    private fun registro(
        tipo: TipoDeBackup = TipoDeBackup.BACKUP, status: StatusDeBackup = StatusDeBackup.OK, inicio: Instant = t0,
        arquivo: String? = "painel-2026-10-30-020000.db", codigo: String? = null,
    ) = RegistroDeBackup(tipo, status, inicio, inicio.plusSeconds(12), arquivo, 4096, "a".repeat(64), codigo)

    @Test
    fun registraELeUltimaOk() = comBanco { _, store ->
        assertNull(store.ultima(TipoDeBackup.BACKUP))
        store.registrar(registro(inicio = t0))
        store.registrar(registro(status = StatusDeBackup.FALHA, inicio = t0.plusSeconds(3600), arquivo = null, codigo = "COPIA_FALHOU"))
        store.registrar(registro(tipo = TipoDeBackup.RESTAURACAO, inicio = t0.plusSeconds(100)))
        val ok = store.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)!!
        assertEquals(t0, ok.inicio)
        assertEquals(t0.plusSeconds(12), ok.fim)
        assertEquals(4096L, ok.tamanhoBytes)
        assertEquals("a".repeat(64), ok.sha256)
        // a mais recente de qualquer status é a falha
        assertEquals("COPIA_FALHOU", store.ultima(TipoDeBackup.BACKUP)!!.codigoErro)
        assertEquals(TipoDeBackup.RESTAURACAO, store.ultima(TipoDeBackup.RESTAURACAO)!!.tipo)
    }

    @Test
    fun porArquivoAcha() = comBanco { _, store ->
        store.registrar(registro(arquivo = "painel-2026-10-29-020000.db", inicio = t0.minusSeconds(86400)))
        store.registrar(registro(arquivo = "painel-2026-10-30-020000.db"))
        assertEquals(t0, store.porArquivo("painel-2026-10-30-020000.db")!!.inicio)
        assertNull(store.porArquivo("painel-inexistente.db"))
    }

    @Test
    fun soOLimiteDeNomeSemCaminho() = comBanco { _, store ->
        assertFailsWith<IllegalArgumentException> { store.registrar(registro(arquivo = "/var/backups/painel-2026-10-30-020000.db")) }
        assertFailsWith<IllegalArgumentException> { store.registrar(registro(arquivo = "C:\\dados\\painel.db")) }
        assertFailsWith<IllegalArgumentException> { store.registrar(registro(codigo = "texto livre com espaço")) }
    }

    @Test
    fun migracaoCriaTabelaEIndice() = comBanco { ds, _ ->
        ds.connection.use { c ->
            val objetos = c.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE name IN ('backup_run','idx_backup_run_tipo_started')").use {
                generateSequence { if (it.next()) it.getString(1) else null }.toSet()
            }
            assertEquals(setOf("backup_run", "idx_backup_run_tipo_started"), objetos)
            // o CHECK recusa tipo e status fora da lista
            val recusou = runCatching {
                c.createStatement().execute("INSERT INTO backup_run(tipo, started_at, status) VALUES ('OUTRO', 1, 'OK')")
            }.isFailure
            assertTrue(recusou)
        }
    }
}
