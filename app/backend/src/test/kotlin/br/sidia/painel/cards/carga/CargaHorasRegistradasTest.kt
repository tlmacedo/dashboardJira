package br.sidia.painel.cards.carga

import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.riscos.RegrasDeRisco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** As horas registradas nos últimos 30 dias na linha da Carga (Plano 6, Tarefa 10): vêm do worklog, nunca do estimado. */
class CargaHorasRegistradasTest {
    private val calc = CargaCalculator(Cap1.motor, RegrasDeRisco.PADRAO)

    private fun linhas(horas: Map<String, Double>?) =
        calc.calcular(listOf("ana", "bruno", "carla"), Cap1.abertas(), emptyList(), Cap1.hoje, Cap1.agora, true, "planejamento v1", horas)
            .dto.linhas.associateBy { it.pessoa }

    @Test
    fun horasRegistradas30dNaCarga() {
        val l = linhas(mapOf("ana" to 10.0, "bruno" to 2.5))
        assertEquals(10.0, l.getValue("ana").horasRegistradas30d)
        assertEquals(2.5, l.getValue("bruno").horasRegistradas30d)
        // Quem não registrou nada na janela, com worklog sincronizado, tem zero hora (e não "indisponível").
        assertEquals(0.0, l.getValue("carla").horasRegistradas30d)
    }

    @Test
    fun semWorklogSincronizadoFicaIndisponivel() {
        assertNull(linhas(null).getValue("ana").horasRegistradas30d)
        // Mapa vazio = ninguém registrou hora alguma: o worklog não foi sincronizado, e "0 h" enganaria.
        assertNull(linhas(emptyMap()).getValue("ana").horasRegistradas30d)
    }
}
