package br.sidia.painel.inicio

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import br.sidia.painel.cards.previsao.QuandoDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Central sem banco: estados dos itens, itens separados, itens que o papel não vê e status fora do mapa. */
class CentralCalculatorTest {
    private class Fonte(
        val vg09: Map<String, Int> = emptyMap(),
        val vg13: Map<String, Int> = emptyMap(),
        val universoEstimativa: Int? = 20,
        val avisos: List<AvisoDto> = emptyList(),
        val kpis: Map<String, Map<String, Int>> = emptyMap(),
        val squadsDaFonte: List<CartaoSquadDto> = emptyList(),
        val cicloDaFonte: FaixaDaCentral? = null,
    ) : FontesDaCentral {
        override suspend fun valoresDe(cartao: String): Map<String, Int> = when (cartao) {
            "VG-09" -> vg09
            "VG-13" -> vg13
            else -> kpis[cartao].orEmpty()
        }
        override suspend fun universoDeEstimativa() = universoEstimativa
        override suspend fun ausentesEm14Dias() = 2
        override suspend fun previsao() = PrevisaoDaCentral(30, QuandoDto("2026-12-07", "2026-12-21", null, 6, 8, null), "media")
        override suspend fun squads() = squadsDaFonte
        override suspend fun ciclo30d() = cicloDaFonte
        override suspend fun avisos() = avisos
        override val metaNoPrazo = 0.8
        override val faixaAtencaoPp = 10.0
    }

    private fun central(f: Fonte): CentralDto = runBlocking { CentralCalculator(f).calcular() }
    private fun CentralDto.item(nome: String) = itens.firstOrNull { it.item == nome }

    @Test
    fun centralTemConcluidas30dComVariacao() {
        val c = central(Fonte(kpis = mapOf("VG-02" to mapOf("atual" to 12, "anterior" to 8))))
        val k = c.kpis.first { it.item == "CONCLUIDAS_30D" }
        assertEquals(listOf(12, "VG-02", "atual", "neutro"), listOf(k.n, k.cartao, k.segmento, k.estado))
        assertEquals(12, c.concluidas30d?.atual)
        assertEquals(8, c.concluidas30d?.anterior)
        assertEquals(50.0, c.concluidas30d?.variacao)
        // Queda: variação negativa.
        assertEquals(-25.0, central(Fonte(kpis = mapOf("VG-02" to mapOf("atual" to 6, "anterior" to 8)))).concluidas30d?.variacao)
        // Sem base (nada nos 30 dias anteriores): sem variação, nunca divisão por zero.
        assertNull(central(Fonte(kpis = mapOf("VG-02" to mapOf("atual" to 6, "anterior" to 0)))).concluidas30d?.variacao)
        // Papel que não vê o cartão: o indicador some do DTO.
        assertNull(central(Fonte()).concluidas30d)
        assertTrue(central(Fonte()).kpis.none { it.item == "CONCLUIDAS_30D" })
    }

    @Test
    fun centralTemCiclo30d() {
        val c = central(Fonte(kpis = mapOf("VG-03" to mapOf("ciclo" to 9)), cicloDaFonte = FaixaDaCentral(2.0, 5.5, 9)))
        val k = c.kpis.first { it.item == "CICLO_30D" }
        assertEquals(listOf(9, "VG-03", "ciclo"), listOf(k.n, k.cartao, k.segmento))
        assertEquals(FaixaDaCentral(2.0, 5.5, 9), c.ciclo30d)
        assertNull(central(Fonte(kpis = mapOf("VG-03" to mapOf("ciclo" to 0)))).ciclo30d, "sem concluídas não há faixa")
    }

    @Test
    fun concluidasECicloFicamIndisponiveisComStatusForaDoMapa() {
        val bloqueio = AvisoDto(AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "x")
        val c = central(Fonte(kpis = mapOf("VG-02" to mapOf("atual" to 3, "anterior" to 1), "VG-03" to mapOf("ciclo" to 3)), cicloDaFonte = FaixaDaCentral(1.0, 2.0, 3), avisos = listOf(bloqueio)))
        assertNull(c.kpis.first { it.item == "CONCLUIDAS_30D" }.n)
        assertNull(c.concluidas30d?.atual)
        assertNull(c.ciclo30d)
    }

    @Test
    fun semAtualizacaoEParadasSaoDoisItensQueNaoSeSomam() {
        val c = central(Fonte(vg09 = mapOf("SEM_ATUALIZACAO" to 3, "PARADAS_NO_STATUS" to 2)))
        assertEquals(3, c.item("SEM_ATUALIZACAO")?.n)
        assertEquals(2, c.item("PARADAS_NO_STATUS")?.n)
        assertEquals(1, c.itens.count { it.item == "SEM_ATUALIZACAO" })
        assertEquals("VG-09", c.item("PARADAS_NO_STATUS")?.cartao)
        assertEquals("PARADAS_NO_STATUS", c.item("PARADAS_NO_STATUS")?.segmento)
    }

    @Test
    fun semEstimativaAcimaDeVintePorCentoEProblema() {
        fun estado(n: Int) = central(Fonte(vg09 = mapOf("SEM_ESTIMATIVA" to n), universoEstimativa = 20)).item("SEM_ESTIMATIVA")?.estado
        assertEquals("problema", estado(5), "25%")
        assertEquals("atencao", estado(3), "15%")
        assertEquals("atencao", estado(4), "20% exato ainda é atenção")
        assertEquals("ok", estado(2), "10% exato")
        assertEquals("ok", estado(0))
        assertEquals("5/20", central(Fonte(vg09 = mapOf("SEM_ESTIMATIVA" to 5))).item("SEM_ESTIMATIVA")?.valor)
    }

    @Test
    fun itemQueOPapelNaoVeNaoExisteNoDto() {
        // O coordenador não recebe valores do VG-13: o item some e nem o nome aparece no DTO.
        val semIndicio = central(Fonte(vg09 = mapOf("VENCIDAS" to 1)))
        assertNull(semIndicio.item("INDICIO_ALTO"))
        assertFalse(kotlinx.serialization.json.Json.encodeToString(CentralDto.serializer(), semIndicio).contains("INDICIO"))
        val comIndicio = central(Fonte(vg09 = mapOf("VENCIDAS" to 1), vg13 = mapOf("INDICIO_ALTO" to 2)))
        val item = assertNotNull(comIndicio.item("INDICIO_ALTO"))
        assertEquals(listOf("VG-13", "INDICIO_ALTO", "problema", "integridade"), listOf(item.cartao, item.segmento, item.estado, item.rota))
    }

    @Test
    fun estadosPorGravidade() {
        val c = central(
            Fonte(
                vg09 = mapOf(
                    "VENCIDAS" to 4, "BLOQUEADAS" to 0, "SEM_RESPONSAVEL" to 2, "COM_FOLGA" to 3, "META_DE_PRAZO" to 7,
                ),
            ),
        )
        assertEquals("problema", c.item("VENCIDAS")?.estado)
        assertEquals("ok", c.item("BLOQUEADAS")?.estado, "zero é ok")
        assertEquals("atencao", c.item("SEM_RESPONSAVEL")?.estado)
        assertEquals("neutro", c.item("COM_FOLGA")?.estado, "folga só informa")
        // Ausências não têm tasks: sem cartão nem segmento.
        val ausencias = assertNotNull(c.item("AUSENCIAS_14D"))
        assertEquals(listOf(2, null, null, "neutro"), listOf(ausencias.n, ausencias.cartao, ausencias.segmento, ausencias.estado))
    }

    @Test
    fun kpisDePrazoLadoALadoContraAMeta() {
        val c = central(Fonte(kpis = mapOf("VG-04" to mapOf("noPrazo" to 15, "noPrazoAjustado" to 18, "universo" to 20), "VG-01" to mapOf("abertas" to 9))))
        val porNome = c.kpis.associateBy { it.item }
        assertEquals(listOf("ABERTAS", "NO_PRAZO", "NO_PRAZO_AJUSTADO"), c.kpis.map { it.item })
        assertEquals("15/20", porNome.getValue("NO_PRAZO").valor)
        assertEquals("atencao", porNome.getValue("NO_PRAZO").estado, "75% com meta de 80% e faixa de 10 pp")
        assertEquals("ok", porNome.getValue("NO_PRAZO_AJUSTADO").estado, "90%")
        assertEquals("VG-04", porNome.getValue("NO_PRAZO").cartao)
    }

    @Test
    fun statusForaDoMapaTornaOsNumerosIndisponiveis() {
        val bloqueante = AvisoDto(AvisoDto.STATUS_SEM_MAPEAMENTO, AvisoDto.BLOQUEANTE, "status sem mapeamento: X")
        val c = central(Fonte(vg09 = mapOf("VENCIDAS" to 0, "SEM_ESTIMATIVA" to 0), avisos = listOf(bloqueante), kpis = mapOf("VG-01" to mapOf("abertas" to 0))))
        assertTrue(c.itens.filter { it.cartao != null }.all { it.n == null && it.estado == "indisponivel" && it.valor == "—" }, c.itens.toString())
        assertNull(c.kpis.single().n)
        assertNull(c.backlogTermina, "sem Monte Carlo com o status fora do mapa")
        assertEquals(listOf(bloqueante), c.avisos)
    }

    @Test
    fun squadsEmOrdemAlfabeticaSemNotaDePessoa() {
        val ok = OcupacaoDto(0.8, false, "ok")
        val c = central(
            Fonte(squadsDaFonte = listOf(CartaoSquadDto("Gama", ok, 0.9, 3), CartaoSquadDto("Alfa", ok, null, 2), CartaoSquadDto("Beta", ok, 0.7, 4))),
        )
        assertEquals(listOf("Alfa", "Beta", "Gama"), c.squads.map { it.squad })
    }

    @Test
    fun entregasPorSemanaEmOrdemDeData() {
        val c = central(Fonte(kpis = mapOf("VG-10" to mapOf("semana:2026-10-19" to 4, "semana:2026-10-05" to 1, "semana:2026-10-12" to 2))))
        assertEquals(listOf("2026-10-05", "2026-10-12", "2026-10-19"), c.entregasPorSemana.map { it.semana })
        assertEquals(listOf(1, 2, 4), c.entregasPorSemana.map { it.n })
        assertEquals("2026-12-07", c.backlogTermina?.p50)
        assertEquals(30, c.backlog)
    }
}
