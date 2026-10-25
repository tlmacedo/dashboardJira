package br.sidia.painel

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import br.sidia.painel.config.Settings
import br.sidia.painel.security.Role
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppContextTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }
        private const val SENHA = "segredo-super-123"
    }

    @TempDir
    lateinit var pasta: Path

    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(AppContext::class.java) as Logger

    @BeforeTest
    fun anexarLog() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun soltarLog() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun avisos() = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private val calendarioTeste = """
        feriados_fixos:
          - mes_dia: "01-01"
            nome: Confraternizacao
    """.trimIndent()

    private val papeisTeste = """
        pessoas:
          - id: ge-001
            nome: Gerente Ficticia
            papeis:
              - papel: GERENTE
    """.trimIndent()

    private fun escreverArquivosReais() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(pasta.resolve("papeis.yaml"), papeisTeste)
    }

    private fun producao(senha: String = SENHA, url: String = pg.jdbcUrl) = Settings(
        dashboardDevMode = false,
        configDir = pasta,
        databaseUrl = url,
        databaseUser = pg.username,
        databasePassword = senha,
    )

    private val urlFechada = "jdbc:postgresql://127.0.0.1:1/painel"

    @Test
    fun producaoSemPapeisFalha() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("papeis.yaml").toString() in e.message.orEmpty(), e.message)
    }

    @Test
    fun producaoSemCalendarioFalha() {
        Files.writeString(pasta.resolve("papeis.yaml"), papeisTeste)
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("calendario.yaml").toString() in e.message.orEmpty(), e.message)
    }

    @Test
    fun devSemPapeisUsaExemploComAviso() {
        // pasta com os modelos de exemplo copiados, sem os arquivos reais
        Files.copy(TestPaths.configDir.resolve("papeis.exemplo.yaml"), pasta.resolve("papeis.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("calendario.exemplo.yaml"), pasta.resolve("calendario.exemplo.yaml"))
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databaseUrl = pg.jdbcUrl,
            databaseUser = pg.username,
            databasePassword = pg.password,
        )
        AppContext.load(s).use { ctx ->
            assertTrue(Role.GERENTE in ctx.roles.rolesOf("ge-001", java.time.LocalDate.parse("2026-10-25")))
        }
        val av = avisos()
        assertTrue(av.any { "papeis.exemplo.yaml" in it }, av.toString())
        assertTrue(av.any { "calendario.exemplo.yaml" in it }, av.toString())
    }

    @Test
    fun producaoComSenhaVaziaFalha() {
        escreverArquivosReais()
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao(senha = "")) }
        assertEquals("DATABASE_PASSWORD obrigatório em produção", e.message)
    }

    @Test
    fun devComSenhaVaziaApenasAvisa() {
        escreverArquivosReais()
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databaseUrl = urlFechada,
            databasePassword = "",
        )
        // sobe até tentar o banco (inalcançável): a falha é de conexão, nunca a da senha obrigatória
        val e = assertFailsWith<IllegalStateException> { AppContext.load(s) }
        assertFalse("DATABASE_PASSWORD obrigatório" in e.message.orEmpty(), e.message)
        assertTrue(avisos().any { "DATABASE_PASSWORD" in it }, avisos().toString())
    }

    @Test
    fun bancoIndisponivelFalhaSemSenhaNaMensagem() {
        escreverArquivosReais()
        val url = "jdbc:postgresql://usuario:$SENHA@127.0.0.1:1/painel?password=$SENHA"
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao(url = url)) }
        var t: Throwable? = e
        while (t != null) {
            assertFalse(SENHA in t.message.orEmpty(), "senha vazou na mensagem: ${t.javaClass}")
            t = t.cause
        }
        assertFalse(SENHA in avisos().joinToString(), "senha vazou no log")
        assertFalse(SENHA in appender.list.joinToString { it.formattedMessage + it.throwableProxy?.message })
    }

    @Test
    fun carregaComBancoRealETestcontainers() {
        escreverArquivosReais()
        val ctx = AppContext.load(producao(senha = pg.password))
        val ds = assertNotNull(ctx.dataSource) as HikariDataSource
        val tabelas = ds.connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toSet() }
        }
        assertTrue(tabelas.containsAll(setOf("person", "issue", "change_event")), tabelas.toString())
        assertFalse(ds.isClosed)
        ctx.close()
        assertTrue(ds.isClosed)
    }

    @Test
    fun moduloSobeComContextoDeTeste() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
        assertNull(testContext(s).dataSource)
    }
}
