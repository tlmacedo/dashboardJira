package br.sidia.painel

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.security.Role
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

        /** Para o contêiner explicitamente, sem depender do Ryuk. */
        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    @TempDir
    lateinit var pasta: Path

    private val appender = ListAppender<ILoggingEvent>()
    // Logger RAIZ: captura também o que as bibliotecas (Hikari, driver) registram.
    private val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

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
          - id: gerente.exemplo
            nome: Gerente Ficticia
            papeis:
              - papel: GERENTE
    """.trimIndent()

    private fun escreverArquivosReais() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(pasta.resolve("papeis.yaml"), papeisTeste)
        escreverMapaDeStatus()
        Files.copy(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"), pasta.resolve("jira_campos.yaml"))
        Files.copy(TestPaths.configDir.resolve("vinculos.exemplo.yaml"), pasta.resolve("vinculos.yaml"))
        copiarFichas()
    }

    @Test
    fun appContextCarregaVinculosComFallbackDeDev() {
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true, configDir = pasta,
            databaseUrl = pg.jdbcUrl, databaseUser = pg.username, databasePassword = pg.password,
        )
        AppContext.load(s).use { ctx ->
            assertEquals("Blocks", ctx.vinculos.tipoDeLink)
            assertTrue(ctx.vinculos.ehReview("Checklist Review"))
        }
        assertTrue(avisos().any { "vinculos.exemplo.yaml" in it }, avisos().toString())
        // Com o arquivo real, o modelo é ignorado.
        Files.writeString(pasta.resolve("vinculos.yaml"), "versao: 1\ntipoDeLink: Outro\ntiposReview: [\"Revisao\"]\n")
        AppContext.load(s).use { ctx -> assertEquals("Outro", ctx.vinculos.tipoDeLink) }
    }

    @Test
    fun producaoSemVinculosRealFalhaCitandoOCaminho() {
        escreverArquivosReais()
        Files.delete(pasta.resolve("vinculos.yaml"))
        Files.copy(TestPaths.configDir.resolve("vinculos.exemplo.yaml"), pasta.resolve("vinculos.exemplo.yaml"))
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("vinculos.yaml").toString() in e.message.orEmpty(), e.message)
    }

    @Test
    fun vinculosInvalidoFalhaSemVazarConteudo() {
        escreverArquivosReais()
        Files.writeString(pasta.resolve("vinculos.yaml"), "versao: 1\ntiposReview: []\n")
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("vinculos.yaml").toString() in e.message.orEmpty(), e.message)
    }

    /** Copia as fichas versionadas (arquivos `.card.yaml` da pasta cards) para a pasta de configuração do teste. */
    private fun copiarFichas() {
        val destino = Files.createDirectories(pasta.resolve("cards"))
        Files.list(TestPaths.configDir.resolve("cards")).use { fichas ->
            fichas.forEach { Files.copy(it, destino.resolve(it.fileName.toString())) }
        }
    }

    @Test
    fun appContextCarregaDicionarioEFichas() {
        // dev sem arquivos reais: modelos de exemplo; as fichas versionadas vêm de cards/
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databaseUrl = pg.jdbcUrl,
            databaseUser = pg.username,
            databasePassword = pg.password,
        )
        AppContext.load(s).use { ctx ->
            assertTrue(ctx.fichas.keys.containsAll(listOf("tempo-em-status", "sincronizacao")), ctx.fichas.keys.toString())
            assertEquals("samsung", ctx.fichas.getValue("sincronizacao").instancia)
            assertTrue(ctx.campos.forInstance("samsung").has("key"))
        }
        assertTrue(avisos().any { "jira_campos.exemplo.yaml" in it }, avisos().toString())
    }

    @Test
    fun producaoComFlagLigadaExigeDicionarioReal() {
        escreverArquivosReais()
        Files.delete(pasta.resolve("jira_campos.yaml"))
        // o modelo existe, mas com o Jira ligado em produção só o arquivo real vale
        Files.copy(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"), pasta.resolve("jira_campos.exemplo.yaml"))
        val s = producao(senha = pg.password).copy(
            jiraLiveEnabled = true,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid/", JiraAuthKind.BEARER, null, "tk-12345"),
            ),
        )
        val e = assertFailsWith<IllegalStateException> { AppContext.load(s) }
        assertTrue(pasta.resolve("jira_campos.yaml").toString() in e.message.orEmpty(), e.message)
    }

    @Test
    fun fichaInvalidaFalhaNaSubidaSemVazarConteudo() {
        escreverArquivosReais()
        Files.writeString(pasta.resolve("cards/quebrada.card.yaml"), "id: q\nchaveInventada: segredo-xyz\n")
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao(senha = pg.password)) }
        assertTrue("quebrada.card.yaml" in e.message.orEmpty(), e.message)
        assertFalse("segredo-xyz" in e.stackTraceToString())
    }

    private fun escreverMapaDeStatus() {
        Files.copy(TestPaths.configDir.resolve("status_map.exemplo.yaml"), pasta.resolve("status_map.yaml"))
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
        Files.copy(TestPaths.configDir.resolve("status_map.exemplo.yaml"), pasta.resolve("status_map.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"), pasta.resolve("jira_campos.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("vinculos.exemplo.yaml"), pasta.resolve("vinculos.exemplo.yaml"))
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databaseUrl = pg.jdbcUrl,
            databaseUser = pg.username,
            databasePassword = pg.password,
        )
        AppContext.load(s).use { ctx ->
            assertTrue(Role.GERENTE in ctx.roles.rolesOf("gerente.exemplo", java.time.LocalDate.parse("2026-10-25")))
        }
        val av = avisos()
        assertTrue(av.any { "papeis.exemplo.yaml" in it }, av.toString())
        assertTrue(av.any { "calendario.exemplo.yaml" in it }, av.toString())
        assertTrue(av.any { "status_map.exemplo.yaml" in it }, av.toString())
    }

    @Test
    fun producaoSemMapaDeStatusFalhaSemInventarPadrao() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(pasta.resolve("papeis.yaml"), papeisTeste)
        // O modelo existe, mas em produção só o arquivo real vale.
        Files.copy(TestPaths.configDir.resolve("status_map.exemplo.yaml"), pasta.resolve("status_map.exemplo.yaml"))
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("status_map.yaml").toString() in e.message.orEmpty(), e.message)
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
        // URL jdbc válida, porta fechada de localhost, senha no parâmetro e como senha do usuário.
        val url = "jdbc:postgresql://127.0.0.1:1/painel?user=painel&password=$SENHA"
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao(url = url)) }
        var t: Throwable? = e
        while (t != null) {
            assertFalse(SENHA in t.message.orEmpty(), "senha vazou na mensagem: ${t.javaClass}")
            assertFalse(SENHA in t.stackTraceToString(), "senha vazou no stack trace: ${t.javaClass}")
            t = t.cause
        }
        // Nenhuma linha capturada (mensagem, stack trace impresso, causa), de qualquer logger.
        appender.list.forEach {
            val texto = it.formattedMessage + "\n" + (it.throwableProxy?.let(ThrowableProxyUtil::asString) ?: "")
            assertFalse(SENHA in texto, "senha vazou no log de ${it.loggerName}: ${it.formattedMessage}")
        }
    }

    @Test
    fun producaoSemGerenteVigenteFalha() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(
            pasta.resolve("papeis.yaml"),
            """
            pessoas:
              - id: coord.um
                nome: Coordenador Ficticio
                papeis:
                  - papel: COORDENADOR
              - id: gerente.exemplo
                nome: Gerente Encerrada
                papeis:
                  - papel: GERENTE
                    ate: 2026-01-31
            """.trimIndent(),
        )
        val relogio = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneId.of("America/Manaus"))
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao(), relogio) }
        assertTrue(pasta.resolve("papeis.yaml").toString() in e.message.orEmpty(), e.message)
        assertFalse(SENHA in e.message.orEmpty())
    }

    @Test
    fun devSemGerenteApenasAvisa() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(
            pasta.resolve("papeis.yaml"),
            """
            pessoas:
              - id: coord.um
                nome: Coordenador Ficticio
                papeis:
                  - papel: COORDENADOR
            """.trimIndent(),
        )
        val s = Settings(dashboardDevMode = true, configDir = pasta, databaseUrl = urlFechada)
        // chega até o banco (inalcançável): a falha é de conexão, não de gerente
        val e = assertFailsWith<IllegalStateException> { AppContext.load(s) }
        assertFalse("GERENTE" in e.message.orEmpty(), e.message)
        assertTrue(avisos().any { "GERENTE" in it }, avisos().toString())
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
    fun poolUsaOTamanhoConfigurado() {
        escreverArquivosReais()
        AppContext.load(producao(senha = pg.password).copy(databasePoolSize = 3)).use { ctx ->
            assertEquals(3, (assertNotNull(ctx.dataSource) as HikariDataSource).maximumPoolSize)
        }
    }

    @Test
    fun yamlDeCalendarioInvalidoCitaCaminhoESemConteudo() {
        Files.writeString(pasta.resolve("papeis.yaml"), papeisTeste)
        Files.writeString(pasta.resolve("calendario.yaml"), "feriados_fixos: [Fulano Secreto: {{{")
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(e.message.orEmpty().startsWith("arquivo ${pasta.resolve("calendario.yaml")} inválido: "), e.message)
        assertNull(e.cause)
        assertFalse("Fulano" in e.message.orEmpty() || "Fulano" in e.stackTraceToString())
    }

    @Test
    fun yamlDePapeisInvalidoCitaCaminhoESemConteudo() {
        Files.writeString(pasta.resolve("calendario.yaml"), calendarioTeste)
        Files.writeString(
            pasta.resolve("papeis.yaml"),
            """
            pessoas:
              - id: gerente.exemplo
                nome: Fulano Secreto
                papeis:
                  - papel: PAPEL_INEXISTENTE
            """.trimIndent(),
        )
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertEquals("arquivo ${pasta.resolve("papeis.yaml")} inválido: IllegalArgumentException", e.message)
        assertNull(e.cause)
        assertFalse("Fulano" in e.stackTraceToString())
    }

    @Test
    fun jiraLigadoComInstanciaInvalidaFalhaNaSubidaSemVazarSegredo() {
        escreverArquivosReais()
        val token = "token-secreto-xyz"
        val s = Settings(
            jiraLiveEnabled = true,
            dashboardDevMode = true,
            configDir = pasta,
            databaseUrl = urlFechada,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("http://jira.exemplo.invalid/", JiraAuthKind.BEARER, null, token),
            ),
        )
        val e = assertFailsWith<IllegalStateException> { AppContext.load(s) }
        assertEquals("Instância Jira 'samsung' inválida: regra 'https-obrigatorio'", e.message)
        assertFalse(token in e.stackTraceToString())
    }

    @Test
    fun jiraLigadoComInstanciasValidasSobe() {
        escreverArquivosReais()
        val s = producao(senha = pg.password).copy(
            jiraLiveEnabled = true,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid/", JiraAuthKind.BEARER, null, "tk-12345"),
            ),
        )
        AppContext.load(s).use { assertNotNull(it.dataSource) }
    }

    @Test
    fun moduloSobeComContextoDeTeste() = testApplication {
        val s = Settings.fromEnv(emptyMap())
        application { module(s, testContext(s)) }
        val saude = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, saude.status)
        assertEquals("""{"ok":true}""", saude.bodyAsText())
        val meta = client.get("/api/meta")
        assertEquals(HttpStatusCode.OK, meta.status)
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO"}""",
            meta.bodyAsText(),
        )
    }

    @Test
    fun moduloUsaSempreAConfiguracaoDoContexto() = testApplication {
        val divergente = Settings(dashboardDevMode = false)
        val doContexto = Settings(dashboardDevMode = true, zone = ZoneId.of("America/Sao_Paulo"))
        application { module(divergente, testContext(doContexto)) }
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Sao_Paulo","banner":"MODO DESENVOLVIMENTO"}""",
            client.get("/api/meta").bodyAsText(),
        )
    }
}
