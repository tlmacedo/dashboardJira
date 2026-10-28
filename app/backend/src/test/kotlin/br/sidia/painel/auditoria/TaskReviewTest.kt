package br.sidia.painel.auditoria

import br.sidia.painel.sync.ParDeChaves
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pares Task × Review pelos vínculos já resolvidos (4A), divergência de vencimento e sinal no evento que diverge. */
class TaskReviewTest {
    private val m = CenarioMulti()

    private fun quatroPares() = listOf(
        m.issue("TG-1", duedate = "2026-10-15"), m.issue("TG-101", duedate = "2026-10-15", tipo = "Checklist Review"),
        m.issue("TG-2", duedate = "2026-10-16"), m.issue("TG-102", duedate = "2026-10-16", tipo = "Checklist Review"),
        m.issue("TG-3", duedate = "2026-10-17"), m.issue("TG-103", duedate = "2026-10-17", tipo = "Checklist Review"),
        m.issue("TG-4", duedate = "2026-10-18"), m.issue("TG-104", duedate = "2026-10-20", tipo = "Checklist Review"),
    )

    // Dois pares em que a Review bloqueia a task e dois em que a task bloqueia a Review: o 4A já normalizou a direção.
    private val vinculos = listOf(
        ParDeChaves("TG-1", "TG-101"), ParDeChaves("TG-2", "TG-102"), ParDeChaves("TG-3", "TG-103"), ParDeChaves("TG-4", "TG-104"),
    )

    @Test
    fun quatroParesUmDivergente() {
        val pares = TaskReview.pares(quatroPares(), vinculos)
        assertEquals(4, pares.size)
        assertEquals(listOf("TG-4"), pares.filter { it.divergente }.map { it.task.key })
        assertEquals(ResumoTaskReview(4, 1, 25.0), TaskReview.resumo(pares))
    }

    @Test
    fun reviewSemTaskLigadaIgnorada() {
        val issues = quatroPares() + m.issue("TG-105", tipo = "Checklist Review")
        // Review sem vínculo, e vínculo para uma task que não está na lista (ausente ou removida): nenhum par novo.
        val pares = TaskReview.pares(issues, vinculos + ParDeChaves("TG-99", "TG-105"))
        assertEquals(4, pares.size)
        assertTrue(pares.none { it.review.key == "TG-105" })
    }

    @Test
    fun parentKeyNaoFormaPar() {
        val task = m.issue("TG-1")
        val review = m.issue("TG-101", tipo = "Checklist Review").copy(parentKey = "TG-1")
        assertTrue(TaskReview.pares(listOf(task, review), emptyList()).isEmpty())
    }

    @Test
    fun ambosSemVencimentoNaoDiverge() {
        val par = ParTaskReview(m.issue("TG-1", duedate = null), m.issue("TG-101", duedate = null))
        assertFalse(par.divergente)
    }

    @Test
    fun umNuloEOutroNaoDiverge() {
        assertTrue(ParTaskReview(m.issue("TG-1", duedate = "2026-10-15"), m.issue("TG-101", duedate = null)).divergente)
        assertTrue(ParTaskReview(m.issue("TG-1", duedate = null), m.issue("TG-101", duedate = "2026-10-15")).divergente)
    }

    @Test
    fun semParesPercentualNulo() {
        assertEquals(ResumoTaskReview(0, 0, null), TaskReview.resumo(emptyList()))
        assertNull(TaskReview.resumo(emptyList()).percentual)
    }

    @Test
    fun sinalNoEventoQueDiverge() {
        // A Review muda 15/10 -> 18/10 e as datas passam a diferir; depois a task também vai para 18/10 (voltam a ser iguais).
        val task = m.issue(
            "TG-1", duedate = "2026-10-18",
            eventos = listOf(m.ev("TG-1", "duedate", "2026-10-15", "2026-10-18", "2026-10-12T12:00:00Z", "pl.alfa")),
        )
        val review = m.issue(
            "TG-101", duedate = "2026-10-18", tipo = "Checklist Review",
            eventos = listOf(m.ev("TG-101", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z", "carla")),
        )
        val sinais = TaskReview.sinais(ParTaskReview(task, review), m.c.ctx)
        val s = sinais.single()
        assertEquals(CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE, s.codigo)
        assertEquals(Resultado.ALERTA, s.resultado)
        assertEquals("TG-101", s.issueKey)
        assertEquals(m.i("2026-10-10T12:00:00Z"), s.at)
        assertEquals("carla", s.autor)
        assertEquals(1, s.gravidade)
    }

    @Test
    fun divergenciaQueContinuaNaoGeraSinalNovo() {
        // Já divergentes; um segundo evento que mantém a divergência não é "o evento que gera" a divergência.
        val task = m.issue("TG-1", duedate = "2026-10-15")
        val review = m.issue(
            "TG-101", duedate = "2026-10-20", tipo = "Checklist Review",
            eventos = listOf(
                m.ev("TG-101", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z", "carla"),
                m.ev("TG-101", "duedate", "2026-10-18", "2026-10-20", "2026-10-11T12:00:00Z", "carla"),
            ),
        )
        val sinais = TaskReview.sinais(ParTaskReview(task, review), m.c.ctx)
        assertEquals(listOf(m.i("2026-10-10T12:00:00Z")), sinais.map { it.at })
    }

    @Test
    fun regraInativaNaoGera() {
        val regras = m.c.regras.copy(
            sinais = m.c.regras.sinais + (CodigoSinal.VENCIMENTO_TASK_REVIEW_DIVERGENTE to RegraSinal(1, ativo = false)),
        )
        val review = m.issue(
            "TG-101", duedate = "2026-10-18", tipo = "Checklist Review",
            eventos = listOf(m.ev("TG-101", "duedate", "2026-10-15", "2026-10-18", "2026-10-10T12:00:00Z", "carla")),
        )
        assertTrue(TaskReview.sinais(ParTaskReview(m.issue("TG-1"), review), m.c.comRegras(regras)).isEmpty())
    }
}
