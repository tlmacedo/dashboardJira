package br.sidia.painel.domain

import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeInStatusCalculatorTest {
    private val mapa = StatusMap(
        mapOf(
            "A Fazer" to StatusCategory.TODO,
            "Em andamento" to StatusCategory.ACTIVE,
            "Em revisão" to StatusCategory.WAIT,
            "Bloqueado" to StatusCategory.BLOCKED,
            "Concluído" to StatusCategory.DONE,
        ),
    )
    private val cal = BusinessCalendar(ZoneId.of("America/Manaus"), fixedHolidays = setOf(MonthDay.of(10, 12)))
    private val calc = TimeInStatusCalculator(cal, mapa)
    private val agora = Instant.parse("2026-10-23T12:00:00Z")
    private val tol = 1e-6

    private fun i(s: String) = Instant.parse(s)

    /** Cenário TG-1 da Tarefa 1. */
    private fun tg1() = IssueTimeline(
        key = "TG-1", summary = "Cenário", issueType = "Task",
        created = i("2026-10-05T12:00:00Z"),
        initialStatus = "A Fazer", initialAssignee = "ana",
        transitions = listOf(
            Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", "ana"),
            Transition(i("2026-10-09T12:00:00Z"), "Em andamento", "Em revisão", "bruno"),
            Transition(i("2026-10-12T12:00:00Z"), "Em revisão", "Concluído", "bruno"),
        ),
        assigneeChanges = listOf(AssigneeChange(i("2026-10-08T12:00:00Z"), "bruno")),
        resolved = i("2026-10-12T12:00:00Z"), resolution = "Done",
        currentStatus = "Concluído", currentAssignee = "bruno",
    )

    private fun aberta(key: String, desde: String, status: String = "Em revisão") = IssueTimeline(
        key = key, summary = "Aberta $key", issueType = "Task",
        created = i("2026-10-01T12:00:00Z"),
        initialStatus = "A Fazer", initialAssignee = "ana",
        transitions = listOf(Transition(i(desde), "A Fazer", status, "ana")),
        assigneeChanges = emptyList(), resolved = null, resolution = null,
        currentStatus = status, currentAssignee = "ana",
    )

    private fun StatusStat?.med() = assertNotNull(this).median

    @Test
    fun tempoPorStatusEmDiasUteis() {
        val r = calc.compute(listOf(tg1()), emptyList(), agora)
        val por = r.perStatus.associateBy { it.status }
        assertEquals(1.0, por["A Fazer"].med(), tol)
        assertEquals(3.0, por["Em andamento"].med(), tol)
        assertEquals(0.666667, por["Em revisão"].med(), tol)
        assertEquals(setOf("A Fazer", "Em andamento", "Em revisão"), por.keys)
        assertEquals(TimeUnitMode.UTIL, r.unit)
        assertEquals(listOf("TG-1"), r.analyzed)
    }

    @Test
    fun tempoPorStatusEmDiasCorridos() {
        val r = calc.compute(listOf(tg1()), emptyList(), agora, unit = TimeUnitMode.CORRIDO)
        val por = r.perStatus.associateBy { it.status }
        assertEquals(1.0, por["A Fazer"].med(), tol)
        assertEquals(3.0, por["Em andamento"].med(), tol)
        assertEquals(3.0, por["Em revisão"].med(), tol)
    }

    @Test
    fun eficienciaDeFluxo() {
        val r = calc.compute(listOf(tg1()), emptyList(), agora)
        assertEquals(0.642857, assertNotNull(r.flowEfficiency), tol)
    }

    @Test
    fun posseDivideOTempo() {
        val r = calc.compute(listOf(tg1()), emptyList(), agora)
        fun v(p: String, s: String) = assertNotNull(r.perPersonStatus.firstOrNull { it.person == p && it.status == s }).median
        assertEquals(2.0, v("ana", "Em andamento"), tol)
        assertEquals(1.0, v("bruno", "Em andamento"), tol)
        assertEquals(0.666667, v("bruno", "Em revisão"), tol)
        assertEquals(1.0, v("ana", "A Fazer"), tol)
        // ordem determinística: pessoa e depois status, alfabéticos
        assertEquals(
            r.perPersonStatus.sortedWith(compareBy({ it.person }, { it.status })),
            r.perPersonStatus,
        )
    }

    @Test
    fun reentradaSoma() {
        val t = tg1().copy(
            transitions = listOf(
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", "ana"),
                Transition(i("2026-10-07T12:00:00Z"), "Em andamento", "A Fazer", "ana"),
                Transition(i("2026-10-09T12:00:00Z"), "A Fazer", "Concluído", "ana"),
            ),
            assigneeChanges = emptyList(), resolved = i("2026-10-09T12:00:00Z"),
        )
        // A Fazer: 5->6 (1 d) + 7->9 (2 d) = 3 d corridos
        val r = calc.compute(listOf(t), emptyList(), agora, unit = TimeUnitMode.CORRIDO)
        assertEquals(3.0, r.perStatus.first { it.status == "A Fazer" }.median, tol)
        assertEquals(1, r.perStatus.first { it.status == "A Fazer" }.n)
    }

    @Test
    fun outlierExcluidoSoQuandoPedido() {
        val longa = tg1().copy(
            key = "TG-9", created = i("2026-09-02T12:00:00Z"),
            transitions = listOf(Transition(i("2026-10-12T12:00:00Z"), "A Fazer", "Concluído", "ana")),
            assigneeChanges = emptyList(),
        )
        val padrao = calc.compute(listOf(tg1(), longa), emptyList(), agora)
        assertEquals(setOf("TG-1", "TG-9"), padrao.analyzed.toSet())
        val filtrado = calc.compute(listOf(tg1(), longa), emptyList(), agora, excludeOutlierDays = 30)
        assertEquals(listOf("TG-1"), filtrado.analyzed)
    }

    @Test
    fun paradaAlemDoNormal() {
        val parada = aberta("TG-20", "2026-10-14T12:00:00Z")
        val recente = aberta("TG-21", "2026-10-22T00:00:00Z") // 1,5 dia corrido antes do now
        val r = calc.compute(listOf(tg1()), listOf(parada, recente), agora, unit = TimeUnitMode.CORRIDO)
        // corridos: mediana de Em revisão é 3.0, limite max(2.0, 4.5) = 4.5; 9 d > 4.5, 1,5 d não.
        assertEquals(listOf("TG-20"), r.stuck.map { it.key })

        val u = calc.compute(listOf(tg1()), listOf(parada, recente), agora)
        val s = u.stuck.single()
        assertEquals("TG-20", s.key)
        assertEquals(7.0, s.daysInStatus, tol)
        assertEquals(0.666667, s.median, tol)
        assertEquals("Em revisão", s.status)
        assertEquals("ana", s.assignee)
    }

    @Test
    fun paradaSemMedianaUsaSoOMinimo() {
        val a = aberta("TG-30", "2026-10-14T12:00:00Z", status = "Bloqueado")
        val b = aberta("TG-31", "2026-10-22T12:00:00Z", status = "Bloqueado")
        val r = calc.compute(emptyList(), listOf(a, b), agora)
        assertEquals(listOf("TG-30"), r.stuck.map { it.key })
    }

    @Test
    fun abertaSemTransicaoNaoQuebra() {
        val t = aberta("TG-40", "2026-10-02T12:00:00Z").copy(
            transitions = emptyList(), currentStatus = "A Fazer",
        )
        val r = calc.compute(emptyList(), listOf(t), agora)
        assertEquals(listOf("TG-40"), r.stuck.map { it.key })
        assertEquals("A Fazer", r.stuck.single().status)
    }

    @Test
    fun semConcluidasDevolveNulosSemErro() {
        val r = calc.compute(emptyList(), emptyList(), agora)
        assertNull(r.flowEfficiency)
        assertTrue(r.analyzed.isEmpty())
        assertTrue(r.perStatus.isEmpty())
        assertTrue(r.perPersonStatus.isEmpty())
        assertTrue(r.stuck.isEmpty())
    }

    @Test
    fun statusSemMapaPropagaExcecao() {
        val t = tg1().copy(
            transitions = listOf(
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Revisando", "ana"),
                Transition(i("2026-10-12T12:00:00Z"), "Revisando", "Concluído", "ana"),
            ),
            assigneeChanges = emptyList(),
        )
        val e = assertFailsWith<UnmappedStatusException> { calc.compute(listOf(t), emptyList(), agora) }
        assertEquals("Revisando", e.status)
    }

    @Test
    fun categoriaDoneNaoEntraNasSomas() {
        // Transição para Concluído antes de resolved: o intervalo DONE com duração é ignorado.
        val t = tg1().copy(
            transitions = listOf(
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Concluído", "ana"),
            ),
            assigneeChanges = emptyList(), resolved = i("2026-10-09T12:00:00Z"),
        )
        val r = calc.compute(listOf(t), emptyList(), agora, unit = TimeUnitMode.CORRIDO)
        assertTrue(r.perStatus.none { it.category == StatusCategory.DONE })
        assertTrue(r.perPersonStatus.none { it.status == "Concluído" })
        // só A Fazer (TODO, 1 d) conta: 0 ativo / 1 total
        assertEquals(0.0, assertNotNull(r.flowEfficiency), tol)
    }

    @Test
    fun transicoesForaDeOrdemSaoOrdenadas() {
        val t = tg1().copy(transitions = tg1().transitions.reversed())
        val r = calc.compute(listOf(t), emptyList(), agora)
        assertEquals(0.642857, assertNotNull(r.flowEfficiency), tol)
    }
}
