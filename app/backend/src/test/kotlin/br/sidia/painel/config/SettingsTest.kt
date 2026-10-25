package br.sidia.painel.config

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
    fun valorInvalidoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            Settings.fromEnv(mapOf("RETENTION_MONTHS" to "abc"))
        }
        assertTrue(e.message!!.contains("RETENTION_MONTHS"))
    }
}
