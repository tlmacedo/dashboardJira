package br.sidia.painel.config

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

    @Test
    fun diretorioEstaticoVazioFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("PAINEL_STATIC_DIR" to "  "))
        }
        assertTrue(e.message!!.contains("PAINEL_STATIC_DIR"))
    }

    @Test
    fun valorInvalidoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("RETENTION_MONTHS" to "abc"))
        }
        assertTrue(e.message!!.contains("RETENTION_MONTHS"))
    }
}
