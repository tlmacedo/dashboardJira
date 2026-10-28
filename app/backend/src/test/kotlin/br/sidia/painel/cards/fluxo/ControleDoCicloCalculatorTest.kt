package br.sidia.painel.cards.fluxo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Controle do ciclo e tendência (I-02 e I-03): média móvel, tipos separados, outliers, meses e o dia de Manaus. */
class ControleDoCicloCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val de = LocalDate.parse("2026-08-01")
    private val ate = LocalDate.parse("2026-11-30")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    /** Concluída de [tipo] que entrou em andamento em [inicio] e foi resolvida em [fim] (formato `MM-ddTHH`, UTC). */
    private fun fechada(chave: String, inicio: String, fim: String, tipo: String = "Checklist", resp: String = "ana") = Trb1.issue(
        chave, tipo, resp, "07-20T12", "Closed", resolution = "Done", resolvida = fim,
        eventos = Trb1.trilha(chave, "Open", inicio to "In Progress", fim to "Closed"),
    )

    private fun analisar(semOutliers: Boolean = false, unit: TimeUnitMode = TimeUnitMode.CORRIDO): ControleDoCicloAnalise {
        val resolvidas = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS))
        return ControleDoCicloCalculator(Trb1.calendario, Trb1.statusMap).analisar(resolvidas, de, ate, unit, semOutliers)
    }

    private fun serie(a: ControleDoCicloAnalise, tipo: String) = a.dto.series.single { it.tipo == tipo }

    /** Cinco concluídas de Checklist com ciclos 1, 2, 3, 4 e 10 dias corridos, resolvidas em dias seguidos. */
    private fun cinco() = listOf(
        fechada("TG-601", "10-05T12", "10-06T12"), fechada("TG-602", "10-05T12", "10-07T12"), fechada("TG-603", "10-05T12", "10-08T12"),
        fechada("TG-604", "10-05T12", "10-09T12"), fechada("TG-605", "10-02T12", "10-12T12"),
    )

    @Test
    fun mediaMovelUsaVintePorCentoImparMinimoCinco() {
        // 20% dos pontos, arredondado para cima, sempre ímpar e nunca menos que 5.
        val esperado = mapOf(5 to 5, 10 to 5, 25 to 5, 26 to 7, 30 to 7, 100 to 21, 101 to 21, 102 to 21, 110 to 23)
        for ((n, janela) in esperado) assertEquals(janela, ControleDoCicloCalculator.janelaDaMediaMovel(n), "n = $n")
        // A conta sobre os pontos: janela 5 (2 de cada lado), cortada nas pontas.
        Trb1.carregar(ds, cinco())
        val s = serie(analisar(), "Checklist")
        assertEquals(5, s.janela)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 10.0), s.pontos.map { it.ciclo })
        // Centrada: os ciclos 1,2,3 / 1..4 / 1..10 / 2,3,4,10 / 3,4,10 dão 2, 2,5, 4, 4,75 e 17/3.
        val esperadas = listOf(2.0, 2.5, 4.0, 4.75, 17.0 / 3)
        s.pontos.forEachIndexed { i, p -> assertEquals(esperadas[i], assertNotNull(p.mediaMovel), 1e-9, "ponto $i") }
    }

    @Test
    fun semMediaMovelComMenosDeCincoPontos() {
        Trb1.carregar(ds, cinco().take(4))
        val s = serie(analisar(), "Checklist")
        assertNull(s.janela)
        assertTrue(s.pontos.all { it.mediaMovel == null })
        assertEquals(4, s.n)
        assertTrue(s.amostraPequena)
    }

    @Test
    fun cicloPorTipoSemMisturarChecklistEReview() {
        val review = listOf(
            fechada("TG-611", "10-05T12", "10-25T12", tipo = "Checklist Review", resp = "carla"),
            fechada("TG-612", "10-05T12", "10-26T12", tipo = "Checklist Review", resp = "carla"),
        )
        Trb1.carregar(ds, cinco() + review)
        val a = analisar()
        assertEquals(listOf("Checklist", "Checklist Review"), a.dto.series.map { it.tipo }, "um por tipo, em ordem alfabética")
        assertEquals(listOf(5, 2), a.dto.series.map { it.n })
        // A mediana do Checklist (3) não é arrastada pelos ciclos longos da Review (20 e 21).
        assertEquals(3.0, serie(a, "Checklist").p50)
        assertEquals(20.5, serie(a, "Checklist Review").p50)
        assertEquals(listOf("TG-601", "TG-602", "TG-603", "TG-604", "TG-605"), a.segmentos.getValue("tipo:Checklist").map { it.key })
        assertEquals(listOf("TG-611", "TG-612"), a.segmentos.getValue("tipo:Checklist Review").map { it.key })
    }

    @Test
    fun outliersAcimaDe30DiasExcluiveisPorParametro() {
        // TG-621 levou 45 dias corridos: é outlier (acima de 30). Por padrão aparece marcada; com semOutliers sai da conta.
        Trb1.carregar(ds, cinco() + fechada("TG-621", "08-20T12", "10-04T12"))
        val com = analisar()
        val pontoLongo = serie(com, "Checklist").pontos.single { it.ciclo == 45.0 }
        assertTrue(pontoLongo.outlier)
        assertEquals(6, serie(com, "Checklist").n)
        assertEquals(0, com.dto.outliersExcluidos)
        assertFalse(com.dto.semOutliers)
        val sem = analisar(semOutliers = true)
        assertEquals(5, serie(sem, "Checklist").n)
        assertTrue(serie(sem, "Checklist").pontos.none { it.ciclo == 45.0 })
        assertEquals(1, sem.dto.outliersExcluidos)
        assertTrue(sem.dto.semOutliers)
        assertEquals(30, sem.dto.limiteOutlierDias)
        assertTrue("TG-621" !in sem.segmentos.getValue("tipo:Checklist").map { it.key })
    }

    @Test
    fun outlierValeEmDiasCorridosMesmoNaUnidadeUtil() {
        // 36 dias corridos (08/09 a 14/10) são cerca de 24 úteis: ainda é outlier porque a regra é em dias corridos.
        Trb1.carregar(ds, cinco() + fechada("TG-622", "09-08T12", "10-14T12"))
        val sem = analisar(semOutliers = true, unit = TimeUnitMode.UTIL)
        assertEquals(1, sem.dto.outliersExcluidos)
        assertEquals("util", sem.dto.unit)
    }

    @Test
    fun mesComPoucosItensMostraAviso() {
        // Outubro tem 5 (sem aviso); setembro tem só 2 (aviso do mês). Agosto não tem nenhuma.
        val setembro = listOf(fechada("TG-631", "09-01T12", "09-03T12"), fechada("TG-632", "09-01T12", "09-04T12"))
        Trb1.carregar(ds, cinco() + setembro)
        val s = serie(analisar(), "Checklist")
        val porMes = s.meses.associateBy { it.mes }
        assertEquals(listOf("2026-08", "2026-09", "2026-10", "2026-11"), s.meses.map { it.mes })
        assertEquals(listOf(0, 2, 5, 0), s.meses.map { it.n })
        assertTrue(porMes.getValue("2026-09").amostraPequena)
        // Ciclos de setembro: 2 e 3 dias, então P50 = 2,5 e P85 = 2,85.
        assertEquals(2.5, assertNotNull(porMes.getValue("2026-09").p50), 1e-9)
        assertEquals(2.85, assertNotNull(porMes.getValue("2026-09").p85), 1e-9)
        assertFalse(porMes.getValue("2026-10").amostraPequena)
        assertNull(porMes.getValue("2026-08").p50)
        assertFalse(porMes.getValue("2026-08").amostraPequena, "mês sem task não é amostra pequena: é vazio")
        // O aviso do tipo continua valendo só quando o tipo todo tem menos de 5 tasks.
        assertTrue(analisar().dto.avisos.none { it.codigo == AvisoDto.AMOSTRA_PEQUENA })
    }

    @Test
    fun tipoComMenosDeCincoTasksTemAvisoDeAmostra() {
        Trb1.carregar(ds, cinco().take(3))
        val avisos = analisar().dto.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(listOf("Checklist"), avisos.map { it.params["tipo"] })
        assertEquals("3", avisos.single().params["n"])
    }

    @Test
    fun dataDeConclusaoNoDiaDeManaus() {
        // 12/10 03:00Z é domingo 11/10 23:00 em Manaus; 01/11 03:00Z é sábado 31/10 23:00: o mês fica em outubro.
        val domingo = fechada("TG-641", "10-05T12", "10-12T03")
        val viradaDoMes = fechada("TG-642", "10-25T12", "11-01T03")
        Trb1.carregar(ds, listOf(domingo, viradaDoMes))
        val a = analisar()
        val s = serie(a, "Checklist")
        assertEquals(listOf("2026-10-11", "2026-10-31"), s.pontos.map { it.dia })
        assertEquals(listOf("TG-641"), a.segmentos.getValue("dia:2026-10-11:Checklist").map { it.key })
        assertEquals(listOf("TG-642"), a.segmentos.getValue("dia:2026-10-31:Checklist").map { it.key })
        assertEquals(2, s.meses.single { it.mes == "2026-10" }.n)
        assertEquals(0, s.meses.single { it.mes == "2026-11" }.n)
        assertEquals(listOf("TG-641", "TG-642"), a.segmentos.getValue("mes:2026-10:Checklist").map { it.key })
    }

    @Test
    fun pontoNaoTrazPessoa() {
        Trb1.carregar(ds, cinco())
        val json = Json.encodeToString(analisar().dto)
        assertFalse("\"ana\"" in json)
        assertFalse(Regex("\"(pessoa|responsavel|assignee|key)\"").containsMatchIn(json), json)
        assertFalse("TG-60" in json, "chave de task na resposta")
    }

    @Test
    fun canceladaEFechadaSemResolucaoNaoEntram() {
        val cancelada = Trb1.issue(
            "TG-651", "Checklist", "ana", "07-20T12", "Closed", resolution = "Won't Do", resolvida = "10-10T12",
            eventos = Trb1.trilha("TG-651", "Open", "10-05T12" to "In Progress", "10-10T12" to "Closed"),
        )
        val semResolucao = Trb1.issue(
            "TG-652", "Checklist", "ana", "07-20T12", "Closed", resolvida = "10-10T12",
            eventos = Trb1.trilha("TG-652", "Open", "10-05T12" to "In Progress", "10-10T12" to "Closed"),
        )
        Trb1.carregar(ds, cinco() + cancelada + semResolucao)
        val a = analisar()
        assertEquals(5, serie(a, "Checklist").n)
        assertEquals(5, a.dto.concluidas)
    }
}
