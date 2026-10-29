package br.sidia.painel.domain

import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Compatibilidade com o app Python anterior (legacy/backend/jiradash/analytics.py::time_in_status, na tag legacy-final).
 * Fórmula do legado, conferida no código: tempo corrido por status (status_durations, soma
 * reentradas, da criação até a resolução), categoria "done" fora das somas, eficiência de fluxo =
 * soma dos status "active" ÷ soma de todos os status não concluídos, percentil por interpolação linear.
 * O legado devolve a eficiência em percentual (round(100 * ativo / total, 1)); aqui comparamos a fração.
 */
class CompatLegadoTest {
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

    /** Cenário TG-1 da Tarefa 1 (mesmo do teste do calculador). */
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

    @Test
    fun modoCorridoReproduzAFormulaDoAppAtual() {
        val r = calc.compute(listOf(tg1()), emptyList(), agora, unit = TimeUnitMode.CORRIDO)
        val por = r.perStatus.associateBy { it.status }

        // Legado: A Fazer 05->06 = 1 d; Em andamento 06->09 = 3 d; Em revisão 09->12 = 3 d.
        assertEquals(1.0, assertNotNull(por["A Fazer"]).median, tol)
        assertEquals(3.0, assertNotNull(por["Em andamento"]).median, tol)
        assertEquals(3.0, assertNotNull(por["Em revisão"]).median, tol)
        // Concluído (done) fica fora das somas, como no legado.
        assertEquals(setOf("A Fazer", "Em andamento", "Em revisão"), por.keys)

        // Legado: ativo (Em andamento, 3 d) ÷ total não concluído (1 + 3 + 3 = 7 d) = 3/7 (42,9 %).
        assertEquals(3.0 / 7.0, assertNotNull(r.flowEfficiency), tol)
        assertEquals(42.9, Math.round(1000.0 * r.flowEfficiency!!) / 10.0, 1e-9)
    }
}
