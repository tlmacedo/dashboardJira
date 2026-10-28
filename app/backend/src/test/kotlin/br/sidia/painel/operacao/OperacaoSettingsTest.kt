package br.sidia.painel.operacao

import br.sidia.painel.config.Settings
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperacaoSettingsTest {
    @Test
    fun padroesSemNenhumaVariavel() {
        val o = Settings.fromEnv(emptyMap()).operacao
        assertNull(o.logFormato)
        assertNull(o.logDir)
        assertEquals(500, o.discoMinMb)
    }

    @Test
    fun lePastaEFormatoDeLog() {
        val o = Settings.fromEnv(mapOf("PAINEL_LOG_FORMATO" to " TEXTO ", "PAINEL_LOG_DIR" to "logs")).operacao
        assertEquals("TEXTO", o.logFormato)
        assertEquals(Path.of("logs"), o.logDir)
    }

    @Test
    fun discoMinForaDaFaixaFalhaCitandoSoONome() {
        for (valor in listOf("49", "1000001", "abc")) {
            val e = assertFailsWith<IllegalArgumentException> { Settings.fromEnv(mapOf("PAINEL_DISCO_MIN_MB" to valor)) }
            assertTrue("PAINEL_DISCO_MIN_MB" in e.message.orEmpty())
            assertFalse(valor in e.message.orEmpty().replace("1000000", "").replace("50", ""), e.message)
        }
        assertEquals(50, Settings.fromEnv(mapOf("PAINEL_DISCO_MIN_MB" to "50")).operacao.discoMinMb)
    }
}
