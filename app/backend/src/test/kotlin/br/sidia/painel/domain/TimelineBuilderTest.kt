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

    private fun aberta(
        criada: String, transicoes: List<Transition> = emptyList(), trocas: List<AssigneeChange> = emptyList(),
        resolvida: String? = null,
    ) = IssueTimeline(
        key = "TG-9", summary = "s", issueType = "Task", created = i(criada),
        initialStatus = "A Fazer", initialAssignee = "ana",
        transitions = transicoes, assigneeChanges = trocas,
        resolved = resolvida?.let(::i), resolution = resolvida?.let { "Done" },
        currentStatus = if (resolvida != null) "Concluído" else "A Fazer", currentAssignee = "ana",
    )

    @Test
    fun ordenaTransicoesDesordenadasNaEntrada() {
        val t = aberta(
            "2026-10-05T12:00:00Z",
            transicoes = listOf(
                Transition(i("2026-10-09T12:00:00Z"), "Em andamento", "Em revisão", null),
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", null),
            ),
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa)
        assertEquals(listOf("A Fazer", "Em andamento", "Em revisão"), r.map { it.status })
    }

    @Test
    fun duasTransicoesNoMesmoInstanteSeguemAOrdemOriginal() {
        val mesmo = i("2026-10-06T12:00:00Z")
        val a = aberta(
            "2026-10-05T12:00:00Z",
            transicoes = listOf(Transition(mesmo, "A Fazer", "Em andamento", null), Transition(mesmo, "Em andamento", "Em revisão", null)),
        )
        // O último da lista vence: a issue fica em "Em revisão" e o "Em andamento" tem duração zero (some).
        assertEquals(listOf("A Fazer", "Em revisão"), TimelineBuilder.intervals(a, i("2026-10-10T12:00:00Z"), mapa).map { it.status })
        // Invertendo a ordem da lista, o estado final muda: a ordem do empate é de fato respeitada.
        val b = aberta(
            "2026-10-05T12:00:00Z",
            transicoes = listOf(Transition(mesmo, "Em andamento", "Em revisão", null), Transition(mesmo, "A Fazer", "Em andamento", null)),
        )
        assertEquals(listOf("A Fazer", "Em andamento"), TimelineBuilder.intervals(b, i("2026-10-10T12:00:00Z"), mapa).map { it.status })
    }

    @Test
    fun eventosDepoisDeResolvedNaoGeramIntervalo() {
        val t = aberta(
            "2026-10-05T12:00:00Z",
            transicoes = listOf(
                Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", null),
                Transition(i("2026-10-12T12:00:00Z"), "Em andamento", "Em revisão", null),
            ),
            trocas = listOf(AssigneeChange(i("2026-10-13T12:00:00Z"), "bruno")),
            resolvida = "2026-10-08T12:00:00Z",
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-20T12:00:00Z"), mapa)
        assertEquals(listOf("A Fazer", "Em andamento"), r.map { it.status })
        assertEquals(i("2026-10-08T12:00:00Z"), r.last().end)
        assertEquals("ana", r.last().assignee)
    }

    @Test
    fun eventosAntesDeCreatedColapsamNoInicio() {
        // Transição anterior à criação: o intervalo inicial tem duração zero e some; nada começa antes de created.
        val t = aberta(
            "2026-10-05T12:00:00Z",
            transicoes = listOf(Transition(i("2026-10-01T12:00:00Z"), "A Fazer", "Em andamento", null)),
        )
        val r = TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa)
        assertEquals(listOf("Em andamento"), r.map { it.status })
        assertEquals(i("2026-10-05T12:00:00Z"), r.single().start)
    }

    @Test
    fun agoraAntesDeCreatedNaoGeraIntervalos() {
        val t = aberta("2026-10-05T12:00:00Z", transicoes = listOf(Transition(i("2026-10-06T12:00:00Z"), "A Fazer", "Em andamento", null)))
        assertEquals(emptyList(), TimelineBuilder.intervals(t, i("2026-10-01T12:00:00Z"), mapa))
    }

    @Test
    fun statusInicialForaDoMapaLanca() {
        val t = aberta("2026-10-05T12:00:00Z").copy(initialStatus = "Inexistente")
        assertFailsWith<UnmappedStatusException> { TimelineBuilder.intervals(t, i("2026-10-10T12:00:00Z"), mapa) }
    }

    @Test
    fun empateDeStatusEResponsavelNaoGeraIntervaloVazio() {
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
