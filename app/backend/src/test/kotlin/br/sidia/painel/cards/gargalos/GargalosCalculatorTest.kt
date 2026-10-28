package br.sidia.painel.cards.gargalos

import br.sidia.painel.domain.Transition
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gargalos e fluxo: o fluxo cumulativo reconstruído do changelog e o estado das etapas, sobre o CAP-1 e fixtures de fluxo. */
class GargalosCalculatorTest {
    private val pessoas = listOf("ana", "bruno", "carla")
    private val calc = GargalosCalculator(Cap1.motor)

    /** Task criada em 09/20 que entrou em In Progress em [desde] e segue lá. */
    private fun andando(key: String, desde: String, resp: String = "ana"): IssueSnapshot =
        Cap1.snapshot(
            key, "Checklist", resp, "In Progress", Trb1.t("09-20T12"), null, 3600L, null, null,
            transicoes = listOf(Transition(Trb1.t(desde), "Open", "In Progress", null)),
        )

    private fun rodar(issues: List<IssueSnapshot>, motor: MotorDePessoas = Cap1.motor) =
        GargalosCalculator(motor).calcular(pessoas, issues, issues.filter { it.resolved == null }, Cap1.hoje, Cap1.agora)

    private fun motorComRegras(regras: br.sidia.painel.pessoas.RegrasDePlanejamento) =
        MotorDePessoas(Cap1.equipes, Cap1.ausencias, Cap1.calendario, regras, Cap1.statusMap)

    private fun etapa(a: GargalosAnalise, status: String) = a.dto.etapas.first { it.status == status }

    @Test
    fun faixaCresceuViraGargalo() {
        // Cinco em In Progress desde 10/01 e quatro desde 10/20: eram 5 há 15 dias e são 9 hoje (limite 12).
        val issues = (1..5).map { andando("TG-60$it", "10-01T12") } + (6..9).map { andando("TG-60$it", "10-20T12") }
        val e = etapa(rodar(issues), "In Progress")
        assertEquals(9, e.wip)
        assertEquals(4, e.crescimento15d)
        assertTrue(e.gargalo)
        assertEquals("FAIXA_CRESCEU", e.motivo)
    }

    @Test
    fun crescimentoPequenoNaoEhGargalo() {
        // De 10 para 12 (limite 12, não ultrapassado): cresceu 2, menos que max(3, 30% de 10).
        val issues = (1..10).map { andando("TG-61$it", "10-01T12") } + (11..12).map { andando("TG-61$it", "10-20T12") }
        val e = etapa(rodar(issues), "In Progress")
        assertEquals(12, e.wip)
        assertEquals(2, e.crescimento15d)
        assertTrue(!e.gargalo)
        assertNull(e.motivo)
    }

    @Test
    fun crescimentoAbaixoDoMinimoDeTresNaoEhGargalo() {
        // De 2 para 4: cresceu 2, que é 100%, mas menos que o piso de 3 tasks.
        val issues = (1..2).map { andando("TG-64$it", "10-01T12") } + (3..4).map { andando("TG-64$it", "10-20T12") }
        val e = etapa(rodar(issues), "In Progress")
        assertEquals(2, e.crescimento15d)
        assertTrue(!e.gargalo)
        // Já de 2 para 5 (3 a mais) é gargalo.
        val mais = issues + andando("TG-645", "10-21T12")
        assertTrue(etapa(rodar(mais), "In Progress").gargalo)
    }

    @Test
    fun wipAcimaDoLimite() {
        val issues = (1..13).map { andando("TG-62$it", "10-01T12") }
        val e = etapa(rodar(issues), "In Progress")
        assertEquals(13, e.wip)
        assertEquals(12, e.limite)
        assertTrue(e.gargalo)
        assertEquals("WIP_ACIMA_DO_LIMITE", e.motivo)
    }

    @Test
    fun etapaTodoNuncaEhGargalo() {
        val regras = Cap1.regras.copy(wipPorStatus = mapOf("Open" to 1, "In Progress" to 12))
        val abertas = (1..4).map { Cap1.snapshot("TG-63$it", "Checklist", "ana", "Open", Trb1.t("10-25T12"), null, 3600L, null, null) }
        val e = etapa(rodar(abertas, motorComRegras(regras)), "Open")
        assertEquals(4, e.wip)
        assertEquals("TODO", e.categoria)
        assertTrue(!e.gargalo)
    }

    @Test
    fun fluxoCumulativoReconstruido() {
        // Entrou em In Progress em 10/15 e passou para Submitted em 10/20: no dia 19 está em In Progress; no dia 20, em Submitted.
        val s = Cap1.snapshot(
            "TG-640", "Checklist", "ana", "Submitted", Trb1.t("10-10T12"), null, 3600L, null, null,
            transicoes = listOf(Transition(Trb1.t("10-15T12"), "Open", "In Progress", null), Transition(Trb1.t("10-20T12"), "In Progress", "Submitted", null)),
        )
        val d = rodar(listOf(s)).dto
        fun valor(status: String, dia: String) = d.faixas.first { it.status == status }.contagens[d.dias.indexOf(dia)]
        assertEquals(1, valor("In Progress", "2026-10-19"))
        assertEquals(0, valor("Submitted", "2026-10-19"))
        assertEquals(0, valor("In Progress", "2026-10-20"))
        assertEquals(1, valor("Submitted", "2026-10-20"))
        assertEquals(1, valor("Open", "2026-10-11"), "criada em 10/10 e ainda em Open no dia 11")
        assertEquals(0, valor("Open", "2026-10-09"), "ainda não existia")
        assertEquals(60, d.dias.size)
        assertEquals("2026-10-30", d.dias.last())
    }

    @Test
    fun concluidaSaiDaFaixaNoDiaQueFecha() {
        val fechada = Cap1.snapshot(
            "TG-641", "Checklist", "ana", "Closed", Trb1.t("10-01T12"), null, null, null, null,
            transicoes = listOf(Transition(Trb1.t("10-05T12"), "Open", "In Progress", null), Transition(Trb1.t("10-10T12"), "In Progress", "Closed", null)),
        ).copy(resolved = Trb1.t("10-10T12"), resolution = "Done")
        val d = rodar(listOf(fechada)).dto
        fun valor(dia: String) = d.faixas.first { it.status == "In Progress" }.contagens[d.dias.indexOf(dia)]
        assertEquals(1, valor("2026-10-09"))
        assertEquals(0, valor("2026-10-10"))
        assertTrue(d.faixas.none { it.status == "Closed" }, "o status concluído fica de fora")
    }

    @Test
    fun faixasNaOrdemDoFluxo() {
        val s = Cap1.snapshot(
            "TG-642", "Checklist", "ana", "On Hold", Trb1.t("10-01T12"), null, 3600L, null, null,
            transicoes = listOf(Transition(Trb1.t("10-05T12"), "Open", "In Progress", null), Transition(Trb1.t("10-10T12"), "In Progress", "On Hold", null)),
        )
        val d = rodar(listOf(s)).dto
        // Open, In Progress (da ordem do fluxo); On Hold, fora da ordem, entra depois.
        assertEquals(listOf("Open", "In Progress", "On Hold"), d.faixas.map { it.status })
        assertEquals("BLOCKED", d.etapas.last().categoria)
        assertTrue(!d.etapas.last().gargalo, "etapa bloqueada não é gargalo")
    }

    @Test
    fun statusForaDoMapaPropagaOAvisoBloqueante() {
        val estranha = Cap1.snapshot("TG-643", "Checklist", "ana", "Revisao Exotica", Trb1.t("10-10T12"), null, 3600L, null, null)
        assertFailsWith<UnmappedStatusException> { rodar(listOf(estranha)) }
    }

    @Test
    fun pessoasAcimaDaCapacidadeEComFolga() {
        val a = rodar(Cap1.abertas())
        assertEquals(0, a.dto.acimaDaCapacidade)
        assertEquals(3, a.dto.comFolga)
        val pesada = Cap1.snapshot("TG-644", "Checklist", "bruno", "Open", Trb1.t("10-22T12"), LocalDate.of(2026, 11, 4), 40 * 3600L, null, null)
        val b = rodar(Cap1.abertas() + pesada)
        assertEquals(1, b.dto.acimaDaCapacidade)
        assertEquals(2, b.dto.tasksAcimaDaCapacidade)
        assertEquals(b.dto.tasksAcimaDaCapacidade, b.segmentos.getValue("acimaDaCapacidade").size)
        assertEquals(b.dto.tasksComFolga, b.segmentos.getValue("comFolga").size)
    }

    @Test
    fun segmentoDaEtapaTemOMesmoTamanhoDoWip() {
        val a = rodar(Cap1.abertas())
        for (e in a.dto.etapas) assertEquals(e.wip, a.segmentos.getValue("status:${e.status}").size, e.status)
        assertEquals(1, etapa(a, "In Progress").wip)
        assertEquals(3, etapa(a, "Open").wip)
    }
}
