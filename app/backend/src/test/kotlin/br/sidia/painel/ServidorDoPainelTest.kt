package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Plano 13, Tarefa 4: o servidor escuta só na interface configurada e o Flyway nunca limpa o banco. */
class ServidorDoPainelTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    @Test
    fun servidorEscutaSoNoLoopback() = runBlocking {
        val porta = ServerSocket(0).use { it.localPort }
        val settings = Settings(dashboardDevMode = true, host = "127.0.0.1", porta = porta)
        val servidor = servidorDoPainel(testContextComBanco(settings, banco), agendador = null, metricas = null)
        servidor.start(wait = false)
        try {
            val conectores = servidor.engine.resolvedConnectors()
            assertEquals(listOf("127.0.0.1"), conectores.map { it.host })
            assertEquals(listOf(porta), conectores.map { it.port })
        } finally {
            servidor.stop(100, 1000)
        }
    }

    @Test
    fun flywayCleanDesligado() {
        val arquivo = banco.novoArquivo()
        val ds = Database.connect(Database.urlDeArquivo(arquivo), poolSize = 1)
        try {
            val flyway = Database.flyway(ds)
            assertTrue(flyway.configuration.isCleanDisabled)
            Database.migrate(ds)
            assertFailsWith<FlywayException> { flyway.clean() }
        } finally {
            (ds as AutoCloseable).close()
        }
    }
}
