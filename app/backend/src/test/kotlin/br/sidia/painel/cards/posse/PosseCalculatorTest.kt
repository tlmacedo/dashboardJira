package br.sidia.painel.cards.posse

import br.sidia.painel.domain.AssigneeChange
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.Transition
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Posse da task sobre o cenário TG-1 do Plano 3 (adaptado aos status do TRB-1): cada pessoa responde pelo tempo que ficou com ela. */
class PosseCalculatorTest {
    private val tol = 1e-6
    private val mapa = Trb1.statusMap
    private val cal = Trb1.calendario

    /**
     * TG-1: criada em 05/10 com `ana`; em andamento em 06/10; passa a `bruno` em 08/10; em revisão em 09/10; concluída em
     * 12/10 (feriado). Em dias úteis: ana 3,0 (A fazer 1 + andamento 2); bruno 1,0 + 0,666667.
     */
    private fun tg1(
        mudancas: List<AssigneeChange> = listOf(AssigneeChange(Trb1.t("10-08T12"), "bruno")), inicial: String? = "ana",
        atual: String? = "bruno", resolvida: Boolean = true,
    ): IssueSnapshot {
        val base = Cap1.snapshot("TG-1", "Checklist", atual, if (resolvida) "Closed" else "Submitted", Trb1.t("10-05T12"), null, null, null, null)
        val transicoes = buildList {
            add(Transition(Trb1.t("10-06T12"), "Open", "In Progress", null))
            add(Transition(Trb1.t("10-09T12"), "In Progress", "Submitted", null))
            if (resolvida) add(Transition(Trb1.t("10-12T12"), "Submitted", "Closed", null))
        }
        return base.copy(
            resolution = if (resolvida) "Done" else null, resolved = if (resolvida) Trb1.t("10-12T12") else null,
            timeline = base.timeline.copy(
                initialAssignee = inicial, transitions = transicoes, assigneeChanges = mudancas,
                resolved = if (resolvida) Trb1.t("10-12T12") else null, resolution = if (resolvida) "Done" else null,
                currentAssignee = atual, currentStatus = if (resolvida) "Closed" else "Submitted",
            ),
        )
    }

    private fun posse(s: IssueSnapshot, unit: TimeUnitMode = TimeUnitMode.UTIL) = PosseCalculator.posse(s, Trb1.agora, mapa, cal, unit)

    @Test
    fun posseDoTG1() {
        val p = posse(tg1())
        assertEquals(listOf("ana", "bruno"), p.map { it.pessoa })
        assertEquals(3.0, p[0].dias, tol)
        assertEquals(0.642857, p[0].fracao, tol)
        assertEquals(1.666667, p[1].dias, tol)
        assertEquals(0.357143, p[1].fracao, tol)
        assertEquals(1.0, p.sumOf { it.fracao }, tol)
        assertEquals(1, PosseCalculator.detalhe(tg1(), Trb1.agora, mapa, cal, TimeUnitMode.UTIL).transferencias)
    }

    @Test
    fun semResponsavelVemNulo() {
        // Sem responsável no início: os 3 primeiros dias úteis são de ninguém ("null"), depois de `bruno`.
        val p = posse(tg1(inicial = null))
        assertEquals(listOf("bruno", null), p.map { it.pessoa })
        val semDono = p.single { it.pessoa == null }
        assertEquals(3.0, semDono.dias, tol)
        // A posse "de ninguém" também conta como passagem de responsável (de ninguém para bruno).
        assertEquals(1, PosseCalculator.detalhe(tg1(inicial = null), Trb1.agora, mapa, cal, TimeUnitMode.UTIL).transferencias)
    }

    @Test
    fun voltaAQuemJaTeveSomaTrechos() {
        // ana -> bruno (08/10) -> ana (09/10): a ana tem dois trechos; são duas passagens.
        val s = tg1(
            mudancas = listOf(AssigneeChange(Trb1.t("10-08T12"), "bruno"), AssigneeChange(Trb1.t("10-09T12"), "ana")), atual = "ana",
        )
        val p = posse(s).associateBy { it.pessoa }
        assertEquals(2, p.getValue("ana").trechos)
        assertEquals(1, p.getValue("bruno").trechos)
        // Soma dos dois trechos da ana: 3,0 + 0,666667 (09 a 12/10 com o feriado).
        assertEquals(3.666667, p.getValue("ana").dias, tol)
        val d = PosseCalculator.detalhe(s, Trb1.agora, mapa, cal, TimeUnitMode.UTIL)
        assertEquals(2, d.transferencias)
        assertEquals(mapOf("bruno" to 1, "ana" to 1), d.recebidas)
        assertEquals(mapOf("ana" to 1, "bruno" to 1), d.repassadas)
    }

    @Test
    fun trechosNaoQuebramPorMudancaDeStatus() {
        // A ana atravessa Open e In Progress (dois intervalos de status) e continua sendo UM trecho.
        assertEquals(1, posse(tg1()).single { it.pessoa == "ana" }.trechos)
    }

    @Test
    fun posseEmCorridos() {
        val p = posse(tg1(), TimeUnitMode.CORRIDO)
        assertEquals(3.0, p[0].dias, tol)
        assertEquals(4.0, p[1].dias, tol)
        assertEquals(3.0 / 7.0, p[0].fracao, tol)
    }

    @Test
    fun abertaContaAteAgora() {
        // Aberta (em revisão desde 09/10) com `bruno`: o tempo corre até "agora" (30/10), dias úteis.
        val p = posse(tg1(resolvida = false))
        assertTrue(p.single { it.pessoa == "bruno" }.dias > 10.0)
    }

    @Test
    fun statusConcluidoNaoContaComoPosse() {
        // Um intervalo em status DONE (reaberta depois) não entra na posse de ninguém.
        val s = tg1(resolvida = false)
        val reaberta = s.copy(
            timeline = s.timeline.copy(
                transitions = listOf(
                    Transition(Trb1.t("10-06T12"), "Open", "Closed", null),
                    Transition(Trb1.t("10-14T12"), "Closed", "Submitted", null),
                ),
                assigneeChanges = emptyList(), initialAssignee = "ana", currentAssignee = "ana",
            ),
        )
        val p = posse(reaberta, TimeUnitMode.CORRIDO)
        // Open de 05 a 06/10 (1 dia) + Submitted de 14/10 a 30/10 (16 dias); os 8 dias em Closed ficam fora.
        assertEquals(17.0, p.single().dias, tol)
        assertNull(p.firstOrNull { it.pessoa == null })
    }

    @Test
    fun semTempoNenhumNaoDividePorZero() {
        // Issue criada agora: nenhum intervalo com duração; lista vazia (nunca fração NaN).
        val s = Cap1.snapshot("TG-9", "Checklist", "ana", "Open", Trb1.agora, null, null, null, null)
        assertTrue(posse(s).isEmpty())
    }

    @Test
    fun pessoasEmOrdemAlfabeticaComNuloNoFim() {
        val s = tg1(
            mudancas = listOf(AssigneeChange(Trb1.t("10-07T12"), "bruno"), AssigneeChange(Trb1.t("10-08T12"), null), AssigneeChange(Trb1.t("10-09T12"), "ana")),
            atual = "ana",
        )
        assertEquals(listOf("ana", "bruno", null), posse(s).map { it.pessoa })
    }
}
