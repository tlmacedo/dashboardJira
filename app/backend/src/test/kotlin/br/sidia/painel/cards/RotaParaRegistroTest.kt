package br.sidia.painel.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O rótulo da rota no `access_log` é sempre o MODELO da rota casada (provado ponta a ponta em `TarefaRoutesTest`). Aqui fica o
 * reforço do caminho: `rotaSensivel` usa o caminho NORMALIZADO e não pode ser enganado por `\`, `%5C`, `%2F` ou `;param`.
 */
class RotaParaRegistroTest {
    @Test
    fun rotaSensivelComBarraInvertidaContinuaSensivel() {
        for (v in listOf("/api/cartoes/IN-01\\..\\..\\x/tasks", "/api/cartoes/IN-01%5C..%5Cx/tasks", "/api/integridade\\x", "/api/%69ntegridade", "/api/integridade;x=1")) {
            assertTrue(rotaSensivel(v), v)
        }
        assertFalse(rotaSensivel("/api/cartoes/AB-01/tasks"))
    }

    @Test
    fun barraInvertidaECodificadaFicamNoMesmoSegmento() {
        for (v in listOf("..\\x\\TG-1", "..%5Cx%5CTG-1", "..%2Fx%2FTG-1", "..%252Fx%252FTG-1", "%2E%2E\\x\\TG-1")) {
            assertEquals(3, segmentosNormalizados("/api/tarefa/$v").size, v)
        }
    }

    @Test
    fun caminhoComumSegueNormalizado() {
        assertEquals("/api/integridade", normalizarCaminho("/api/%69ntegridade?x=1"))
    }
}
