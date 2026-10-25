package br.sidia.painel.domain

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TimelineBuilderTest {
    private val mapa = StatusMap(
        mapOf(
            "A Fazer" to StatusCategory.TODO,
            "Em andamento" to StatusCategory.ACTIVE,
            "Em revisão" to StatusCategory.WAIT,
            "Bloqueado" to StatusCategory.BLOCKED,
            "Concluído" to StatusCategory.DONE,
        ),
    )

    @TempDir
    lateinit var pastaTemp: Path

    private fun i(s: String) = Instant.parse(s)

    /** Cenário TG-1 do plano. */
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
    fun cortaPorStatusEPorResponsavel() {
        val r = TimelineBuilder.intervals(tg1(), i("2026-10-20T12:00:00Z"), mapa)
        // O brief cita 5, mas lista 4 e diz que "Concluído" não gera intervalo: são 4.
        assertEquals(
            listOf(
                StatusInterval("A Fazer", StatusCategory.TODO, i("2026-10-05T12:00:00Z"), i("2026-10-06T12:00:00Z"), "ana"),
                StatusInterval("Em andamento", StatusCategory.ACTIVE, i("2026-10-06T12:00:00Z"), i("2026-10-08T12:00:00Z"), "ana"),
                StatusInterval("Em andamento", StatusCategory.ACTIVE, i("2026-10-08T12:00:00Z"), i("2026-10-09T12:00:00Z"), "bruno"),
                StatusInterval("Em revisão", StatusCategory.WAIT, i("2026-10-09T12:00:00Z"), i("2026-10-12T12:00:00Z"), "bruno"),
            ),
            r,
        )
    }

    @Test
    fun reentradaNoMesmoStatusSomaDepois() {
        val t = tg1().copy(
            transitions = listOf(
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", "ana"),
                Transition(i("2026-10-07T12:00:00Z"), "Em andamento", "A Fazer", "ana"),
            ),
            assigneeChanges = emptyList(), resolved = null, resolution = null,
            currentStatus = "A Fazer", currentAssignee = "ana",
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa)
        assertEquals(listOf("A Fazer", "Em andamento", "A Fazer"), r.map { it.status })
        assertEquals(i("2026-10-10T12:00:00Z"), r.last().end)
    }

    @Test
    fun semTransicaoFicaTodoNoMesmoStatus() {
        val t = tg1().copy(
            transitions = emptyList(), assigneeChanges = emptyList(), resolved = null, resolution = null,
            currentStatus = "A Fazer", currentAssignee = "ana",
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa)
        assertEquals(
            listOf(StatusInterval("A Fazer", StatusCategory.TODO, i("2026-10-05T12:00:00Z"), i("2026-10-10T12:00:00Z"), "ana")),
            r,
        )
    }

    @Test
    fun empateDeTimestampAplicaStatusAntes() {
        val t = tg1().copy(
            transitions = listOf(Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", "ana")),
            assigneeChanges = listOf(AssigneeChange(i("2026-10-06T12:00:00Z"), "bruno")),
            resolved = null, resolution = null, currentStatus = "Em andamento", currentAssignee = "bruno",
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa)
        assertEquals(2, r.size)
        assertEquals(StatusInterval("A Fazer", StatusCategory.TODO, i("2026-10-05T12:00:00Z"), i("2026-10-06T12:00:00Z"), "ana"), r[0])
        assertEquals(StatusInterval("Em andamento", StatusCategory.ACTIVE, i("2026-10-06T12:00:00Z"), i("2026-10-10T12:00:00Z"), "bruno"), r[1])
    }

    @Test
    fun primeiraEntradaEUltimaSaida() {
        val t = tg1()
        assertEquals(i("2026-10-06T12:00:00Z"), t.firstEntered("Em andamento"))
        assertEquals(i("2026-10-09T12:00:00Z"), t.lastLeft("Em andamento"))
        assertNull(t.firstEntered("Bloqueado"))
        assertNull(t.lastLeft("Bloqueado"))
    }

    @Test
    fun statusForaDoMapaLanca() {
        val t = tg1().copy(
            transitions = listOf(Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Revisando", "ana")),
            assigneeChanges = emptyList(), resolved = null, resolution = null, currentStatus = "Revisando",
        )
        val e = assertFailsWith<UnmappedStatusException> { TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa) }
        assertEquals("Revisando", e.status)
    }

    @Test
    fun modeloDeExemploCarregaDoYaml() {
        val m = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml"))
        assertEquals(StatusCategory.TODO, m.category("A Fazer"))
        assertEquals(StatusCategory.DONE, m.category("Concluído"))
    }

    @Test
    fun yamlComCategoriaInvalidaFalhaCitandoArquivo() {
        val arq = pastaTemp.resolve("sm.yaml")
        Files.writeString(arq, "status_map:\n  \"A Fazer\": inexistente\n")
        val e = assertFailsWith<IllegalArgumentException> { StatusMap.fromYaml(arq) }
        assert(e.message!!.contains("sm.yaml"))
    }
}
