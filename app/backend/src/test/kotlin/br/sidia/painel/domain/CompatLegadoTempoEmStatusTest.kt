package br.sidia.painel.domain

import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compatibilidade do Tempo em status com o app Python (`legacy/backend/jiradash/analytics.py::time_in_status`, tag `legacy-final`, e
 * `metrics/core.py::status_durations`/`pct`), SÓ em `unit = CORRIDO` (o padrão do painel novo são dias úteis; a
 * diferença está em `docs/analise/diferencas-esperadas-tempo-em-status.md`).
 *
 * Os valores esperados são os que o legado calcula, congelados aqui à mão a partir do código dele (P85 por interpolação
 * linear do numpy, reentrada somada por status, parada quando `d > max(2.0, 1.5 * mediana)` com mediana 1 para status
 * sem histórico). O código Python está preservado na tag `legacy-final`; o teste não depende mais da pasta `legacy/`.
 */
class CompatLegadoTempoEmStatusTest {
    private val mapa = StatusMap(
        mapOf(
            "A Fazer" to StatusCategory.TODO,
            "Em andamento" to StatusCategory.ACTIVE,
            "Em revisão" to StatusCategory.WAIT,
            "Bloqueado" to StatusCategory.BLOCKED,
            "Concluído" to StatusCategory.DONE,
        ),
    )
    private val calc = TimeInStatusCalculator(BusinessCalendar(ZoneId.of("America/Manaus"), fixedHolidays = emptySet()), mapa)
    private val agora = Instant.parse("2026-10-23T12:00:00Z")
    private val tol = 1e-9

    private fun i(s: String) = Instant.parse(s)

    private fun fechada(key: String, vararg passos: Pair<String, String>): IssueTimeline {
        // passos: (instante, novo status), começando em "A Fazer" na criação (2026-09-01T12:00Z).
        var de = "A Fazer"
        val trs = passos.map { (quando, para) -> Transition(i(quando), de, para, "ana").also { de = para } }
        return IssueTimeline(
            key = key, summary = key, issueType = "Task", created = i("2026-09-01T12:00:00Z"),
            initialStatus = "A Fazer", initialAssignee = "ana", transitions = trs, assigneeChanges = emptyList(),
            resolved = trs.last().at, resolution = "Done", currentStatus = "Concluído", currentAssignee = "ana",
        )
    }

    private fun aberta(key: String, status: String, desde: String) = IssueTimeline(
        key = key, summary = key, issueType = "Task", created = i("2026-09-01T12:00:00Z"),
        initialStatus = "A Fazer", initialAssignee = "ana",
        transitions = listOf(Transition(i(desde), "A Fazer", status, "ana")),
        assigneeChanges = emptyList(), resolved = null, resolution = null, currentStatus = status, currentAssignee = "ana",
    )

    // A: Em andamento 2 d. B: Em andamento 2 d, Em revisão 1 d, volta a Em andamento por 4 d (reentrada: soma 6 d).
    private val a = fechada("TG-A", "2026-09-02T12:00:00Z" to "Em andamento", "2026-09-04T12:00:00Z" to "Concluído")
    private val b = fechada(
        "TG-B", "2026-09-02T12:00:00Z" to "Em andamento", "2026-09-04T12:00:00Z" to "Em revisão",
        "2026-09-05T12:00:00Z" to "Em andamento", "2026-09-09T12:00:00Z" to "Concluído",
    )

    @Test
    fun p85ERentradaSomadaComoOLegado() {
        // Trechos do legado (tag `legacy-final`): analytics.py `m.pct(agg[s], 85)` e `agg[s].append(sec / m.DAY)`;
        // metrics/core.py `out[cur] = out.get(cur, 0.0) +` e `np.percentile(`.
        val r = calc.compute(listOf(a, b), emptyList(), agora, unit = TimeUnitMode.CORRIDO)
        val andamento = assertNotNull(r.perStatus.firstOrNull { it.status == "Em andamento" })
        // Legado: valores por issue [2, 6] (a reentrada da B soma 2 + 4); mediana 4,0; P85 = 2 + 0,85 × (6 − 2) = 5,4; n = 2.
        assertEquals(2, andamento.n)
        assertEquals(4.0, andamento.median, tol)
        assertEquals(5.4, andamento.p85, tol)
        // Em revisão: um valor só (1 d).
        assertEquals(1.0, r.perStatus.first { it.status == "Em revisão" }.median, tol)
        // Concluído (done) fica fora das somas.
        assertTrue(r.perStatus.none { it.status == "Concluído" })
    }

    @Test
    fun paradaMaxDeDoisDiasEUmVirgulaCincoVezesAMedianaComoOLegado() {
        // Trecho do legado (analytics.py): `d > max(2.0, 1.5 * smed.get(r.status, 1))`.
        // Mediana de "Em andamento" = 4,0 → limite 6,0 (estrito: exatamente 6,0 não é parada). "Bloqueado" não tem
        // histórico: o legado usa mediana 1 → limite max(2, 1,5) = 2,0.
        val abertas = listOf(
            aberta("TG-X", "Em andamento", "2026-10-16T12:00:00Z"), // 7,00 d → parada
            aberta("TG-Y", "Em andamento", "2026-10-17T18:00:00Z"), // 5,75 d → não
            aberta("TG-Z", "Em andamento", "2026-10-17T12:00:00Z"), // 6,00 d → não (estrito)
            aberta("TG-W", "Bloqueado", "2026-10-21T00:00:00Z"), // 2,50 d → parada
            aberta("TG-V", "Bloqueado", "2026-10-21T14:00:00Z"), // 1,92 d → não
        )
        val r = calc.compute(listOf(a, b), abertas, agora, unit = TimeUnitMode.CORRIDO)
        assertEquals(setOf("TG-X", "TG-W"), r.stuck.map { it.key }.toSet())
        assertEquals(7.0, r.stuck.first { it.key == "TG-X" }.daysInStatus, tol)
        assertEquals(4.0, r.stuck.first { it.key == "TG-X" }.median, tol)
        assertEquals(2.5, r.stuck.first { it.key == "TG-W" }.daysInStatus, tol)
    }
}
