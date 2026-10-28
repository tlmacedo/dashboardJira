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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
        private val pg = BancoEmArquivo()

        /** Apaga a pasta temporária dos bancos de teste. */
        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
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
        Files.copy(TestPaths.configDir.resolve("labels_atraso.exemplo.yaml"), pasta.resolve("labels_atraso.yaml"))
        Files.copy(TestPaths.configDir.resolve("auditoria.exemplo.yaml"), pasta.resolve("auditoria.yaml"))
        Files.copy(TestPaths.configDir.resolve("metas.exemplo.yaml"), pasta.resolve("metas.yaml"))
        Files.copy(TestPaths.configDir.resolve("risco.exemplo.yaml"), pasta.resolve("risco.yaml"))
        Files.copy(TestPaths.configDir.resolve("previsao.exemplo.yaml"), pasta.resolve("previsao.yaml"))
        Files.copy(TestPaths.configDir.resolve("planejamento.exemplo.yaml"), pasta.resolve("planejamento.yaml"))
        // Equipes e ausências reais (fictícias aqui) só podem citar ids que existam no papeis.yaml do teste.
        Files.writeString(pasta.resolve("equipes.yaml"), "membros:\n  - id: gerente.exemplo\n    horasSemanais: 40\n    alocacao: 1.0\n")
        Files.writeString(pasta.resolve("ausencias.yaml"), "ausencias: []\n")
        copiarFichas()
    }

    @Test
    fun appContextCarregaComFallbackDeDev() {
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos", "labels_atraso", "auditoria", "metas", "risco", "previsao", "equipes", "ausencias", "planejamento").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true, configDir = pasta,
            databasePath = pg.arquivo,
        )
        AppContext.load(s).use { ctx ->
            assertTrue(ctx.labelsDeAtraso.justifica("ONHOLDBY-PL"))
            // Equipes, ausências e regras de planejamento caem nos modelos, com aviso, só em desenvolvimento.
            assertTrue(ctx.equipes.de("dev.alfa1") != null)
            assertTrue(ctx.ausencias.daPessoa("dev.alfa1").isNotEmpty())
            assertEquals(1, ctx.regrasPlanejamento.versao)
            assertEquals(3, ctx.regrasAuditoria.passaBolaTrocas)
            // tiposReview tem uma fonte só: o vinculos.yaml
            assertEquals(ctx.vinculos.tiposReview, ctx.regrasAuditoria.tiposReview)
        }
        assertTrue(avisos().any { "labels_atraso.exemplo.yaml" in it }, avisos().toString())
        assertTrue(avisos().any { "auditoria.exemplo.yaml" in it }, avisos().toString())
        // Com os arquivos reais, os modelos são ignorados.
        Files.writeString(pasta.resolve("labels_atraso.yaml"), "versao: 1\njustificativas: [\"XYZ\"]\ntardias: []\n")
        AppContext.load(s).use { ctx -> assertTrue(ctx.labelsDeAtraso.justifica("xyz")) }
    }

    @Test
    fun producaoSemArquivoRealFalha() {
        for (nome in listOf("labels_atraso", "auditoria", "metas", "risco", "previsao", "equipes", "ausencias", "planejamento")) {
            pasta.toFile().listFiles()?.forEach { it.deleteRecursively() }
            escreverArquivosReais()
            // o modelo existe, mas em produção só o arquivo real vale
            Files.copy(TestPaths.configDir.resolve("$nome.exemplo.yaml"), pasta.resolve("$nome.exemplo.yaml"), StandardCopyOption.REPLACE_EXISTING)
            Files.delete(pasta.resolve("$nome.yaml"))
            val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
            assertTrue(pasta.resolve("$nome.yaml").toString() in e.message.orEmpty(), e.message)
        }
    }

    @Test
    fun auditoriaInvalidaFalhaSemVazarConteudo() {
        escreverArquivosReais()
        Files.writeString(pasta.resolve("auditoria.yaml"), "versao: 1\nsegredo-xyz: 1\n")
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("auditoria.yaml").toString() in e.message.orEmpty(), e.message)
        assertFalse("segredo-xyz" in e.stackTraceToString())
    }

    @Test
    fun metasComFallbackDeDevEInvalidasFalhamSemVazarConteudo() {
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos", "labels_atraso", "auditoria", "metas", "risco", "previsao", "equipes", "ausencias", "planejamento").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val dev = Settings(dashboardDevMode = true, configDir = pasta, databasePath = pg.arquivo)
        AppContext.load(dev).use { ctx ->
            assertEquals(0.90, ctx.regrasDeMetas.metaNoPrazo)
            assertEquals(1, ctx.regrasDeMetas.versao)
        }
        assertTrue(avisos().any { "metas.exemplo.yaml" in it }, avisos().toString())
        pasta.toFile().listFiles()?.forEach { it.deleteRecursively() }
        escreverArquivosReais()
        Files.writeString(pasta.resolve("metas.yaml"), "versao: 1\nmetaNoPrazo: 0.9\nsegredo-xyz: 1\n")
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue(pasta.resolve("metas.yaml").toString() in e.message.orEmpty(), e.message)
        assertFalse("segredo-xyz" in e.stackTraceToString())
    }

    @Test
    fun appContextCarregaVinculosComFallbackDeDev() {
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos", "labels_atraso", "auditoria", "metas", "risco", "previsao", "equipes", "ausencias", "planejamento").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true, configDir = pasta,
            databasePath = pg.arquivo,
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
        listOf("papeis", "calendario", "status_map", "jira_campos", "vinculos", "labels_atraso", "auditoria", "metas", "risco", "previsao", "equipes", "ausencias", "planejamento").forEach {
            Files.copy(TestPaths.configDir.resolve("$it.exemplo.yaml"), pasta.resolve("$it.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databasePath = pg.arquivo,
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
        val s = producao().copy(
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
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
        assertTrue("quebrada.card.yaml" in e.message.orEmpty(), e.message)
        assertFalse("segredo-xyz" in e.stackTraceToString())
    }

    private fun escreverMapaDeStatus() {
        Files.copy(TestPaths.configDir.resolve("status_map.exemplo.yaml"), pasta.resolve("status_map.yaml"))
    }

    private fun producao() = Settings(
        dashboardDevMode = false,
        configDir = pasta,
        databasePath = pg.arquivo,
    )

    /** Caminho que não pode ser aberto (o "diretório pai" é um arquivo comum): a subida para ao chegar no banco. */
    private val caminhoInvalido: Path
        get() {
            val arquivoComum = pasta.resolve("nao-e-pasta.txt")
            if (!Files.exists(arquivoComum)) Files.writeString(arquivoComum, "x")
            return arquivoComum.resolve("painel.db")
        }

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
        Files.copy(TestPaths.configDir.resolve("labels_atraso.exemplo.yaml"), pasta.resolve("labels_atraso.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("auditoria.exemplo.yaml"), pasta.resolve("auditoria.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("metas.exemplo.yaml"), pasta.resolve("metas.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("risco.exemplo.yaml"), pasta.resolve("risco.exemplo.yaml"))
        Files.copy(TestPaths.configDir.resolve("previsao.exemplo.yaml"), pasta.resolve("previsao.exemplo.yaml"))
        for (nome in listOf("equipes", "ausencias", "planejamento")) {
            Files.copy(TestPaths.configDir.resolve("$nome.exemplo.yaml"), pasta.resolve("$nome.exemplo.yaml"))
        }
        copiarFichas()
        val s = Settings(
            dashboardDevMode = true,
            configDir = pasta,
            databasePath = pg.arquivo,
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
    fun bancoQueNaoPodeSerAbertoFalhaSemVazarCaminhoNemCausa() {
        escreverArquivosReais()
        val e = assertFailsWith<IllegalStateException> { AppContext.load(producao().copy(databasePath = caminhoInvalido)) }
        assertTrue(e.message.orEmpty().startsWith("Falha ao abrir o banco"), e.message)
        assertNull(e.cause)
    }

    @Test
    fun doisPaineisNoMesmoBancoNaoSobemJuntos() {
        escreverArquivosReais()
        AppContext.load(producao()).use {
            val e = assertFailsWith<IllegalStateException> { AppContext.load(producao()) }
            assertEquals("Já existe outro painel usando este banco. Feche-o antes de abrir este.", e.message)
        }
        // Fechado o primeiro, o banco volta a poder ser aberto.
        AppContext.load(producao()).use { assertNotNull(it.dataSource) }
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
        val s = Settings(dashboardDevMode = true, configDir = pasta, databasePath = caminhoInvalido)
        // chega até o banco (inalcançável): a falha é de conexão, não de gerente
        val e = assertFailsWith<IllegalStateException> { AppContext.load(s) }
        assertFalse("GERENTE" in e.message.orEmpty(), e.message)
        assertTrue(avisos().any { "GERENTE" in it }, avisos().toString())
    }

    @Test
    fun carregaComBancoSqliteReal() {
        escreverArquivosReais()
        val ctx = AppContext.load(producao())
        val ds = assertNotNull(ctx.dataSource) as HikariDataSource
        val tabelas = ds.connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
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
        AppContext.load(producao().copy(databasePoolSize = 3)).use { ctx ->
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
            databasePath = caminhoInvalido,
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
        val s = producao().copy(
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
    fun closeLogaSoAClasseDaFalhaDeAoFecharEContinua() {
        val acumulado = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(AppContext::class.java) as Logger
        logger.addAppender(acumulado)
        try {
            val ctx = testContext(Settings())
            var segunda = false
            ctx.aoFechar { throw IllegalStateException("segredo-no-fechamento") }
            ctx.aoFechar { segunda = true }
            ctx.close()
            assertTrue(segunda, "a falha de uma ação não impede as outras")
            val mensagens = acumulado.list.map { it.formattedMessage }
            assertTrue(mensagens.any { it.contains("IllegalStateException") }, mensagens.toString())
            assertTrue(mensagens.none { it.contains("segredo") }, mensagens.toString())
        } finally {
            logger.detachAppender(acumulado)
        }
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
