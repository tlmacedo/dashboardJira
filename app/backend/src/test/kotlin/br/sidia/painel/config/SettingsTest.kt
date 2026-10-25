package br.sidia.painel.config

import br.sidia.painel.jira.JiraAuthKind
import java.nio.file.Path
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsTest {
    @Test
    fun padroesSaoSeguros() {
        val s = Settings.fromEnv(emptyMap())
        assertFalse(s.jiraLiveEnabled)
        assertTrue(s.dashboardDevMode)
        assertEquals(24, s.retentionMonths)
        assertEquals(ZoneId.of("America/Manaus"), s.zone)
    }

    @Test
    fun leVariaveis() {
        val s = Settings.fromEnv(
            mapOf(
                "JIRA_LIVE_ENABLED" to "true",
                "DASHBOARD_DEV_MODE" to "false",
                "RETENTION_MONTHS" to "12",
            ),
        )
        assertTrue(s.jiraLiveEnabled)
        assertFalse(s.dashboardDevMode)
        assertEquals(12, s.retentionMonths)
    }

    @Test
    fun diretorioEstaticoTemPadraoELeVariavel() {
        assertEquals(Path.of("frontend/dist"), Settings.fromEnv(emptyMap()).staticDir)
        assertEquals(
            Path.of("/app/static"),
            Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to " /app/static ")).staticDir,
        )
    }

    // Revisado: variável presente mas vazia (comum no docker-compose) agora significa "usar o padrão".
    @Test
    fun diretorioEstaticoVazioUsaPadrao() {
        assertEquals(Path.of("frontend/dist"), Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "  ")).staticDir)
    }

    @Test
    fun vazioUsaPadraoParaTodos() {
        val s = Settings.fromEnv(
            mapOf(
                "JIRA_LIVE_ENABLED" to "",
                "DASHBOARD_DEV_MODE" to " ",
                "RETENTION_MONTHS" to "  ",
                "PAINEL_TZ" to "",
                "PAINEL_CONFIG_DIR" to "",
                "DATABASE_URL" to "",
                "DATABASE_USER" to "",
                "DATABASE_PASSWORD" to "",
                "PAINEL_STATIC_DIR" to "",
            ),
        )
        assertEquals(Settings(), s)
    }

    @Test
    fun lePainelBanco() {
        val s = Settings.fromEnv(mapOf("DATABASE_USER" to "painel", "DATABASE_PASSWORD" to "segredo"))
        assertEquals("painel", s.databaseUser)
        assertEquals("segredo", s.databasePassword)
    }

    @Test
    fun toStringNaoVazaSegredos() {
        val s = Settings.fromEnv(
            mapOf(
                "DATABASE_URL" to "jdbc:postgresql://usuarioX:senhaUrlY@h/db",
                "DATABASE_PASSWORD" to "senhaBancoZ",
                "JIRA_SAMSUNG_URL" to "https://x.exemplo.com",
                "JIRA_SAMSUNG_TOKEN" to "tokenSecretoW",
            ),
        )
        val texto = s.toString()
        listOf("senhaBancoZ", "tokenSecretoW", "usuarioX", "senhaUrlY").forEach {
            assertFalse(texto.contains(it), "vazou $it")
        }
        assertTrue(texto.contains("jdbc:postgresql://***@h/db"))
        assertFalse(s.jiraInstances.getValue("samsung").toString().contains("tokenSecretoW"))
    }

    @Test
    fun toStringMascaraSenhaNaQuery() {
        val a = Settings(databaseUrl = "jdbc:postgresql://h/db?user=u&password=abc123&ssl=true").toString()
        val b = Settings(databaseUrl = "jdbc:postgresql://h/db?password=abc123").toString()
        assertFalse(a.contains("abc123"))
        assertFalse(b.contains("abc123"))
        assertTrue(a.contains("password=***&ssl=true"))
        assertTrue(b.contains("?password=***"))
    }

    @Test
    fun instanciaSamsungCompleta() {
        val s = Settings.fromEnv(
            mapOf("JIRA_SAMSUNG_URL" to "https://s.exemplo.com", "JIRA_SAMSUNG_TOKEN" to "t"),
        )
        assertEquals(setOf("samsung"), s.jiraInstances.keys)
        val i = s.jiraInstances.getValue("samsung")
        assertEquals("https://s.exemplo.com", i.baseUrl)
        assertEquals(JiraAuthKind.BEARER, i.auth)
        assertEquals(null, i.username)
        assertEquals("t", i.token)
    }

    @Test
    fun instanciaSidiaCompleta() {
        val s = Settings.fromEnv(
            mapOf("JIRA_SIDIA_URL" to "https://d.exemplo.com", "JIRA_SIDIA_USER" to "u", "JIRA_SIDIA_TOKEN" to "t"),
        )
        val i = s.jiraInstances.getValue("sidia")
        assertEquals(JiraAuthKind.BASIC, i.auth)
        assertEquals("u", i.username)
        assertEquals("t", i.token)
    }

    @Test
    fun instanciaIncompletaFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("JIRA_SIDIA_URL" to "https://valorSecretoQ.exemplo.com", "JIRA_SIDIA_USER" to " "))
        }
        val msg = e.message!!
        assertTrue(msg.contains("JIRA_SIDIA_USER"))
        assertTrue(msg.contains("JIRA_SIDIA_TOKEN"))
        assertFalse(msg.contains("valorSecretoQ"))
    }

    @Test
    fun semInstanciasMapaVazio() {
        assertTrue(Settings.fromEnv(emptyMap()).jiraInstances.isEmpty())
        assertTrue(Settings.fromEnv(mapOf("JIRA_SAMSUNG_URL" to "")).jiraInstances.isEmpty())
    }

    @Test
    fun valorInvalidoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("RETENTION_MONTHS" to "abc"))
        }
        assertTrue(e.message!!.contains("RETENTION_MONTHS"))
    }
}
