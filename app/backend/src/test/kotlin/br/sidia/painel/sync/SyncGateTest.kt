package br.sidia.painel.sync

import br.sidia.painel.config.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncGateTest {
    @Test
    fun padraoEhDesligado() {
        val s = Settings.fromEnv(emptyMap())
        assertFalse(s.jiraLiveEnabled)
        val gate = SyncGate(s.jiraLiveEnabled, s.sync)
        assertEquals(SyncMode.DESLIGADO, gate.modo)
        assertFalse(gate.podeAgendar())
    }

    @Test
    fun flagLigadaEhPeriodico() {
        val gate = SyncGate(true, SyncSettings())
        assertEquals(SyncMode.PERIODICO, gate.modo)
        assertTrue(gate.podeAgendar())
        assertEquals(GateDecision.Liberada, gate.avaliar(SyncKind.INCREMENTAL, "samsung", false))
    }

    @Test
    fun flagDesligadaNegaTodoTipo() {
        val gate = SyncGate(false, SyncSettings(instancias = listOf("samsung", "sidia")))
        for (kind in listOf(SyncKind.INCREMENTAL, SyncKind.RECONCILIACAO)) {
            for (inst in listOf("samsung", "sidia", "outra")) {
                assertEquals(
                    GateDecision.Negada(MotivoNegacao.DESLIGADO_PELA_FLAG),
                    gate.avaliar(kind, inst, false),
                )
            }
        }
    }

    @Test
    fun instanciaForaDaListaNega() {
        val s = Settings.fromEnv(mapOf("SYNC_INSTANCIAS" to "samsung"))
        val gate = SyncGate(true, s.sync)
        assertEquals(
            GateDecision.Negada(MotivoNegacao.INSTANCIA_NAO_CONFIGURADA),
            gate.avaliar(SyncKind.INCREMENTAL, "sidia", false),
        )
    }

    @Test
    fun suspensaNega() {
        val gate = SyncGate(true, SyncSettings())
        assertEquals(
            GateDecision.Negada(MotivoNegacao.SUSPENSA_POR_ERRO),
            gate.avaliar(SyncKind.RECONCILIACAO, "samsung", true),
        )
    }

    @Test
    fun retencaoNaoPassaPeloGate() {
        for (live in listOf(true, false)) {
            assertFailsWith<IllegalArgumentException> {
                SyncGate(live, SyncSettings()).avaliar(SyncKind.RETENCAO, "samsung", false)
            }
        }
    }
}
