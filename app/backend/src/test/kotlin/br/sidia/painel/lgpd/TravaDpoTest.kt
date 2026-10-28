package br.sidia.painel.lgpd

import br.sidia.painel.config.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A porta do DPO é decidida só pela configuração e fecha por padrão (fail-closed): só desenvolvimento offline a abre sem aceite. */
class TravaDpoTest {
    @Test
    fun desenvolvimentoOfflineEstaLiberada() {
        val trava = TravaDpo(Settings(dashboardDevMode = true))
        assertTrue(trava.liberada)
        assertNull(trava.motivo())
        trava.exigir(ModuloSensivel.INTEGRIDADE)
        trava.exigir(ModuloSensivel.AUDITORIA)
    }

    @Test
    fun producaoSemAceiteFechaOsDoisModulos() {
        val trava = TravaDpo(Settings(dashboardDevMode = false))
        assertFalse(trava.liberada)
        assertEquals("AGUARDANDO_ACEITE_DPO", trava.motivo())
        for (m in ModuloSensivel.entries) {
            val e = assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(m) }
            assertEquals(m, e.modulo)
            assertEquals("AGUARDANDO_ACEITE_DPO", e.message, "a mensagem é só o código estável")
        }
    }

    @Test
    fun desenvolvimentoComJiraLigadoTambemFecha() {
        // Dados reais sem login e sem aceite: a porta continua fechada.
        val trava = TravaDpo(Settings(dashboardDevMode = true).copy(jiraLiveEnabled = true))
        assertFalse(trava.liberada)
        assertFailsWith<BloqueadoPeloDpoException> { trava.exigir(ModuloSensivel.INTEGRIDADE) }
    }
}
