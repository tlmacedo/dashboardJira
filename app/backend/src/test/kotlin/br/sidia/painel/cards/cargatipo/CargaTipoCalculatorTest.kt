package br.sidia.painel.cards.cargatipo

import br.sidia.painel.cards.esforco.EsforcoTask
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Carga por tipo e OS: abertas (contagem e horas) e fechadas (contagem e esforço) por tipo × OS Version; sem OS é coluna própria. */
class CargaTipoCalculatorTest {
    private val calc = CargaTipoCalculator(Cap1.motor)

    private fun fechada(key: String, tipo: String, os: String?): IssueSnapshot =
        Cap1.snapshot(key, tipo, "ana", "Closed", Trb1.t("10-01T12"), null, null, null, null, os = os).copy(resolution = "Done", resolved = Trb1.t("10-20T12"))

    private fun indice(key: String, valor: Double) = key to EsforcoTask(key, 0.0, 0, 0.0, valor, false)

    private val fechadas = listOf(
        fechada("TG-401", "Checklist", "Android 15"),
        fechada("TG-402", "Checklist Review", null),
        fechada("TG-403", "Checklist", "Android 15"),
    )
    private val esforcos = mapOf(indice("TG-401", 14.5), indice("TG-402", 3.0), indice("TG-403", 5.0))

    private fun rodar(abertas: List<IssueSnapshot> = Cap1.abertas(), fechadas: List<IssueSnapshot> = this.fechadas) = calc.calcular(abertas, fechadas, esforcos)

    @Test
    fun cargaPorTipoEOs() {
        val d = rodar().dto
        assertEquals(listOf("Checklist", "Checklist Review"), d.tipos)
        // "sem OS" é a versão vazia e vem por último.
        assertEquals(listOf("Android 15", ""), d.versoes)
        val c = d.tipos.indexOf("Checklist")
        val a15 = d.versoes.indexOf("Android 15")
        assertEquals(2, d.abertasN[c][a15])
        assertEquals(16.8, d.abertasH[c][a15], 1e-9)
        assertEquals(2, d.fechadasN[c][a15])
        assertEquals(19.5, d.esforco[c][a15], 1e-9)
    }

    @Test
    fun semOsViraLinhaPropria() {
        val d = rodar().dto
        val c = d.tipos.indexOf("Checklist")
        val semOs = d.versoes.indexOf("")
        // TG-203 (4 h) e TG-205 (vencida, 6 h assumidas) não têm OS.
        assertEquals(2, d.abertasN[c][semOs])
        assertEquals(10.0, d.abertasH[c][semOs], 1e-9)
        val r = d.tipos.indexOf("Checklist Review")
        assertEquals(1, d.fechadasN[r][semOs])
        assertEquals(3.0, d.esforco[r][semOs], 1e-9)
    }

    @Test
    fun matrizesTemOMesmoFormatoETotaisFecham() {
        val d = rodar().dto
        for (m in listOf(d.abertasN, d.fechadasN)) {
            assertEquals(d.tipos.size, m.size)
            assertTrue(m.all { it.size == d.versoes.size })
        }
        assertEquals(d.tipos.size, d.abertasH.size)
        assertEquals(d.tipos.size, d.esforco.size)
        assertEquals(Cap1.abertas().size, d.abertasN.sumOf { it.sum() })
        assertEquals(fechadas.size, d.fechadasN.sumOf { it.sum() })
    }

    @Test
    fun segmentosAbremAsMesmasTasksDoNumero() {
        val a = rodar()
        assertEquals(listOf("TG-201", "TG-202"), a.segmentos.getValue("abertas:tipo:Checklist:os:Android 15").map { it.key })
        assertEquals(listOf("TG-203", "TG-205"), a.segmentos.getValue("abertas:tipo:Checklist:os:").map { it.key })
        assertEquals(listOf("TG-401", "TG-403"), a.segmentos.getValue("fechadas:tipo:Checklist:os:Android 15").map { it.key })
        assertEquals(listOf("TG-402"), a.segmentos.getValue("fechadas:tipo:Checklist Review:os:").map { it.key })
    }

    @Test
    fun osComMaiusculaDiferenteEEspacosAgrupaJunto() {
        val a = Cap1.snapshot("TG-1", "Checklist", "ana", "Open", Trb1.t("10-01T12"), null, 3600, null, null, os = "Android 15")
        val b = Cap1.snapshot("TG-2", "Checklist", "ana", "Open", Trb1.t("10-01T12"), null, 3600, null, null, os = "  android 15 ")
        val d = rodar(abertas = listOf(a, b), fechadas = emptyList()).dto
        assertEquals(1, d.versoes.size)
        assertEquals(2, d.abertasN.single().single())
    }

    @Test
    fun semNadaDevolveListasVazias() {
        val d = rodar(abertas = emptyList(), fechadas = emptyList()).dto
        assertEquals(emptyList(), d.tipos)
        assertEquals(emptyList(), d.versoes)
        assertEquals(emptyList(), d.abertasN)
    }
}
