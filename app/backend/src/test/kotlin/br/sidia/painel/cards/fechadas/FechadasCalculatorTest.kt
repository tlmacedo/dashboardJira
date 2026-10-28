package br.sidia.painel.cards.fechadas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.labelsDeAtrasoDeTeste
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Concluídas, ciclo, "no prazo" e entregas por pessoa sobre o cenário TRB-1 (período 01/10 a 30/10). */
class FechadasCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val calc = FechadasCalculator(Trb1.calendario, Trb1.statusMap, labelsDeAtrasoDeTeste())
    private val de = LocalDate.parse("2026-10-01")
    private val ate = LocalDate.parse("2026-10-30")
    private val todos = setOf("ana", "bruno", "carla")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        Trb1.carregar(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun resolvidas(): List<IssueSnapshot> {
        val inicio = de.atStartOfDay(Trb1.zona).toInstant()
        val fim = ate.plusDays(1).atStartOfDay(Trb1.zona).toInstant()
        return SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, inicio, fim))
    }

    private fun dto(unit: TimeUnitMode = TimeUnitMode.UTIL, visiveis: Set<String> = todos) =
        calc.calcular(resolvidas(), de, ate, Trb1.agora, unit, visiveis)

    @Test
    fun contagensDasFechadas() {
        val d = dto()
        assertEquals(4, d.concluidas)
        assertEquals(1, d.canceladas)
        assertEquals(1, d.semResolution)
        assertEquals(8.0, d.pontos)
        assertEquals(2, d.comPontos)
        assertEquals("util", d.unit)
        assertEquals(listOf("TG-111", "TG-112", "TG-113", "TG-114"), d.linhas.map { it.key })
        assertTrue(d.avisos.any { it.codigo == AvisoDto.DONE_SEM_RESOLUTION && it.params["n"] == "1" })
    }

    @Test
    fun semPontosOTotalFicaNuloENaoZero() {
        val d = calc.calcular(resolvidas().map { it }.filter { it.storyPoints == null }, de, ate, Trb1.agora, TimeUnitMode.UTIL, todos)
        assertNull(d.pontos)
        assertEquals(0, d.comPontos)
    }

    @Test
    fun cicloELeadEmUteisECorridos() {
        val util = dto(TimeUnitMode.UTIL)
        assertEquals(5.0, util.ciclo!!.p50, 1e-9)
        assertEquals(7.1, util.ciclo!!.p85, 1e-9)
        assertEquals(4, util.ciclo!!.n)
        val corrido = dto(TimeUnitMode.CORRIDO)
        assertEquals(6.5, corrido.ciclo!!.p50, 1e-9)
        assertEquals(11.2, corrido.ciclo!!.p85, 1e-9)
        assertEquals(10.5, corrido.lead!!.p50, 1e-9)
        assertEquals(14.1, corrido.lead!!.p85, 1e-9)
        assertEquals(4, corrido.lead!!.n)
        assertEquals("corrido", corrido.unit)
    }

    @Test
    fun noPrazoEAjustado() {
        val d = dto()
        assertEquals(4, d.comVencimento)
        assertEquals(3, d.noPrazo)
        assertEquals(4, d.noPrazoAjustado)
        assertEquals(0.75, d.taxaNoPrazo!!, 1e-9)
        assertEquals(1.0, d.taxaAjustada!!, 1e-9)
        assertEquals(listOf("noPrazo", "noPrazo", "ajustado", "noPrazo"), d.linhas.map { it.prazo })
    }

    @Test
    fun porSemana() {
        val semanas = dto().porSemana
        // A semana parcial de 28/09 (1º a 4/10) não tem entrega e é cortada; a de 26/10 é inteira do período e fica, com zero.
        assertEquals(listOf("2026-10-05" to 1, "2026-10-12" to 2, "2026-10-19" to 1, "2026-10-26" to 0), semanas.map { it.semana to it.n })
    }

    @Test
    fun semanaParcialComEntregaAparece() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-171", "Checklist", "ana", "09-29T12", "Closed", resolution = "Done", resolvida = "10-02T12",
                    eventos = Trb1.trilha("TG-171", "Open", "09-30T12" to "In Progress", "10-02T12" to "Closed"),
                ),
            ),
        )
        val semanas = dto().porSemana
        assertEquals(listOf("2026-09-28", "2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26"), semanas.map { it.semana })
        assertEquals(1, semanas.first().n)
    }

    @Test
    fun percentisPorTipo() {
        val porTipo = dto().porTipo.associateBy { it.tipo }
        assertEquals(setOf("Checklist", "Checklist Review"), porTipo.keys)
        assertEquals(6.0, porTipo.getValue("Checklist").p50, 1e-9)
        assertEquals(7.4, porTipo.getValue("Checklist").p85, 1e-9)
        assertEquals(3, porTipo.getValue("Checklist").n)
        assertEquals(1.0, porTipo.getValue("Checklist Review").p50, 1e-9)
        assertEquals(1, porTipo.getValue("Checklist Review").n)
        // Amostra pequena: o número aparece e há um aviso por tipo.
        val avisos = dto().avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(listOf("Checklist", "Checklist Review"), avisos.map { it.params["tipo"] })
    }

    @Test
    fun entregasPorPessoaAlfabeticas() {
        val p = dto().porPessoa
        assertEquals(listOf("ana", "bruno", "carla"), p.map { it.pessoa })
        assertEquals(listOf(2, 1, 1), p.map { it.finalizou })
        assertEquals(listOf(2, 1, 1), p.map { it.participou })
        assertEquals(3.0, p[0].pontos)
        assertEquals(5.0, p[1].pontos)
        assertNull(p[2].pontos)
        assertEquals(6.0, p[0].cicloP50!!, 1e-9)
        assertNull(p[0].horasEstimadas, "sem estimativa nas concluídas do cenário: vazio, não zero")
    }

    @Test
    fun soPessoasVisiveisAparecem() {
        val d = dto(visiveis = setOf("ana"))
        assertEquals(listOf("ana"), d.porPessoa.map { it.pessoa })
        // O cálculo recebe a lista já filtrada pelo escopo; quem chama decide quais tasks entram.
        val soDaAna = calc.calcular(resolvidas().filter { it.assignee == "ana" }, de, ate, Trb1.agora, TimeUnitMode.UTIL, setOf("ana"))
        assertEquals(2, soDaAna.concluidas)
        assertEquals(listOf("ana"), soDaAna.porPessoa.map { it.pessoa })
    }

    @Test
    fun participouConsideraATrocaDeResponsavel() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-172", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-20T12",
                    eventos = Trb1.trilha("TG-172", "Open", "10-02T12" to "In Progress", "10-20T12" to "Closed") +
                        Trb1.ev("TG-172", "assignee", "ana", "bruno", "10-10T12"),
                ),
            ),
        )
        val p = dto().porPessoa.associateBy { it.pessoa }
        assertEquals(1 + 1, p.getValue("bruno").finalizou)
        assertEquals(3, p.getValue("ana").participou, "ana teve a TG-172 até 10/10")
        assertEquals(2, p.getValue("ana").finalizou)
    }

    @Test
    fun segmentosTemOMesmoTamanhoDasContagens() {
        val a = calc.analisar(resolvidas(), emptyList(), de, ate, Trb1.agora, TimeUnitMode.UTIL, todos)
        val d = a.dto
        assertEquals(d.concluidas, a.segmentos.getValue("concluidas").size)
        assertEquals(d.canceladas, a.segmentos.getValue("canceladas").size)
        assertEquals(d.semResolution, a.segmentos.getValue("semResolution").size)
        assertEquals(d.noPrazo, a.segmentos.getValue("noPrazo").size)
        assertEquals(d.noPrazoAjustado, a.segmentos.getValue("noPrazoAjustado").size)
        assertEquals(d.comVencimento, a.segmentos.getValue("comVencimento").size)
        assertEquals(d.ciclo!!.n, a.segmentos.getValue("ciclo").size)
        assertEquals(d.lead!!.n, a.segmentos.getValue("lead").size)
        for (s in d.porSemana) assertEquals(s.n, a.segmentos.getValue("semana:${s.semana}").size)
        for (t in d.porTipo) assertEquals(t.n, a.segmentos.getValue("tipo:${t.tipo}").size)
        for (p in d.porProjeto) assertEquals(p.n, a.segmentos.getValue("projeto:${p.chave}").size)
        for (p in d.porPessoa) {
            assertEquals(p.finalizou, a.segmentos.getValue("pessoa:${p.pessoa}:finalizou").size)
            assertEquals(p.participou, a.segmentos.getValue("pessoa:${p.pessoa}:participou").size)
        }
        assertEquals(listOf("TG" to 4), d.porProjeto.map { it.chave to it.n })
    }

    @Test
    fun dispersaoTemPontosEPercentis() {
        val disp = dto().dispersao!!
        assertEquals(4, disp.pontos.size)
        assertEquals(5.0, disp.p50, 1e-9)
        assertEquals(7.1, disp.p85, 1e-9)
        assertTrue(disp.p95 > disp.p85)
        assertEquals("2026-10-09", disp.pontos.first { it.key == "TG-111" }.dia)
    }
}
