package br.sidia.painel.checklist

import br.sidia.painel.sync.DirecaoVinculo
import br.sidia.painel.sync.ParDeChaves
import br.sidia.painel.sync.ParesTaskReview
import br.sidia.painel.sync.VinculoRecord
import br.sidia.painel.sync.VinculosConfig
import br.sidia.painel.trabalho.SnapDeTeste.snap
import br.sidia.painel.trabalho.Trb1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pares Checklist × Review pelo link "Blocks" (já resolvido pelo 4A) e os alertas de cada par, no cenário CR-1. */
class ParesChecklistReviewTest {
    private val tiposReview = setOf("Checklist Review")

    private fun motor(regras: RegrasChecklistReview = RegrasChecklistReview.PADRAO, revisao: Set<String> = tiposReview) =
        ParesChecklistReview(regras, revisao, Trb1.statusMap, Trb1.calendario)

    @Test
    fun quatroParesESemReview() {
        val m = motor()
        val pares = m.pares(Cr1.issues, Cr1.vinculos, Cr1.agora)
        assertEquals(listOf("TG-401", "TG-403", "TG-405", "TG-407"), pares.map { it.pai.key })
        assertEquals(listOf("TG-402", "TG-404", "TG-406", "TG-408"), pares.map { it.review.key })
        assertEquals(listOf("TG-409"), m.semReview(Cr1.issues, Cr1.vinculos).map { it.key })
    }

    @Test
    fun alertasDoCenario() {
        val pares = motor().pares(Cr1.issues, Cr1.vinculos, Cr1.agora).associateBy { it.pai.key }
        assertEquals(emptySet(), pares.getValue("TG-401").alertas)
        assertEquals(
            setOf(AlertaDoPar.MESMA_PESSOA, AlertaDoPar.REVIEW_VENCE_DEPOIS, AlertaDoPar.PAI_FECHADO_ANTES),
            pares.getValue("TG-403").alertas,
        )
        assertEquals(setOf(AlertaDoPar.REVIEW_CURTA_COM_PAI_REABERTO), pares.getValue("TG-405").alertas)
        assertEquals(setOf(AlertaDoPar.REVIEW_VENCIDA), pares.getValue("TG-407").alertas)
    }

    @Test
    fun horasAtivasEReaberturaDoPai() {
        val pares = motor().pares(Cr1.issues, Cr1.vinculos, Cr1.agora).associateBy { it.pai.key }
        assertEquals(8.0, pares.getValue("TG-401").horasAtivasReview)
        assertEquals(24.0, pares.getValue("TG-403").horasAtivasReview)
        assertEquals(0.25, pares.getValue("TG-405").horasAtivasReview)
        assertEquals(listOf(false, false, true, false), listOf("TG-401", "TG-403", "TG-405", "TG-407").map { pares.getValue(it).paiReaberto })
    }

    @Test
    fun reviewAtrasadaQuandoResolvidaDepoisDoPropriVencimento() {
        // Review resolvida em 21/10 (Manaus) com vencimento em 20/10.
        val pai = snap("TG-501", "Checklist", "ana", "2026-10-01T12:00:00Z", "Open", vence = "2026-10-30")
        val review = snap(
            "TG-502", "Checklist Review", "bruno", "2026-10-02T12:00:00Z", "Closed", resolution = "Done",
            resolvida = "2026-10-21T15:00:00Z", vence = "2026-10-20",
        )
        val par = motor().pares(listOf(pai, review), listOf(ParDeChaves("TG-501", "TG-502")), Cr1.agora).single()
        assertTrue(AlertaDoPar.REVIEW_ATRASADA in par.alertas)
        // Resolvida às 02:00Z de 21/10 = 22:00 de 20/10 em Manaus: ainda no dia do vencimento, portanto não atrasa.
        val noLimite = review.copy(resolved = SnapDeTeste_i("2026-10-21T02:00:00Z"))
        val par2 = motor().pares(listOf(pai, noLimite), listOf(ParDeChaves("TG-501", "TG-502")), Cr1.agora).single()
        assertTrue(AlertaDoPar.REVIEW_ATRASADA !in par2.alertas, "o dia é o de Manaus")
    }

    @Test
    fun reviewSemPaiNaListaIgnorada() {
        // O vínculo aponta para um checklist que não veio na lista de issues: o par é ignorado, sem erro.
        val soReview = Cr1.issues.filter { it.key != "TG-401" }
        val pares = motor().pares(soReview, Cr1.vinculos, Cr1.agora)
        assertEquals(listOf("TG-403", "TG-405", "TG-407"), pares.map { it.pai.key })
    }

    @Test
    fun paiDeTipoForaDaListaIgnorado() {
        val tarefa = snap("TG-601", "Task", "ana", "2026-10-01T12:00:00Z", "Open", vence = "2026-10-20")
        val review = snap("TG-602", "Checklist Review", "bruno", "2026-10-02T12:00:00Z", "Open", vence = "2026-10-21")
        val m = motor()
        assertEquals(emptyList(), m.pares(listOf(tarefa, review), listOf(ParDeChaves("TG-601", "TG-602")), Cr1.agora))
        // Tarefa de tipo fora da lista de pais também não é "checklist sem review".
        assertEquals(emptyList(), m.semReview(listOf(tarefa), emptyList()))
    }

    @Test
    fun parPeloLinkBlocksNasDuasDirecoes() {
        val cfg = VinculosConfig(versao = 1, tipoDeLink = "Blocks", tiposReview = tiposReview)
        val tipoDe = mapOf("TG-401" to "Checklist", "TG-402" to "Checklist Review")
        // A Review bloqueia o checklist (is blocked by) ou o checklist bloqueia a Review: o mesmo par resolvido.
        val reviewBloqueia = listOf(VinculoRecord("TG-402", "TG-401", "Blocks", DirecaoVinculo.OUTWARD))
        val taskBloqueia = listOf(VinculoRecord("TG-401", "TG-402", "Blocks", DirecaoVinculo.OUTWARD))
        val a = ParesTaskReview.resolver(reviewBloqueia, tipoDe, cfg).pares
        val b = ParesTaskReview.resolver(taskBloqueia, tipoDe, cfg).pares
        assertEquals(listOf(ParDeChaves("TG-401", "TG-402")), a)
        val m = motor()
        val issues = listOf(Cr1.p1Pai, Cr1.p1Review)
        assertEquals(m.pares(issues, a, Cr1.agora), m.pares(issues, b, Cr1.agora))
        assertEquals(1, m.pares(issues, a, Cr1.agora).size)
    }

    @Test
    fun parentKeyNaoDefinePar() {
        // A Review aponta o `parentKey` para um checklist, mas não há vínculo: nenhum par e o checklist fica sem review.
        val pai = snap("TG-701", "Checklist", "ana", "2026-10-01T12:00:00Z", "Open", vence = "2026-10-20")
        val review = snap("TG-702", "Checklist Review", "bruno", "2026-10-02T12:00:00Z", "Open", vence = "2026-10-21").copy(parentKey = "TG-701")
        val m = motor()
        assertEquals(emptyList(), m.pares(listOf(pai, review), emptyList(), Cr1.agora))
        assertEquals(listOf("TG-701"), m.semReview(listOf(pai, review), emptyList()).map { it.key })
    }

    @Test
    fun tiposReviewDaAuditoria() {
        // Os tipos de review vêm da auditoria (fonte única): trocar a lista muda os pares, sem mexer neste YAML.
        assertEquals(4, motor(revisao = setOf("Checklist Review")).pares(Cr1.issues, Cr1.vinculos, Cr1.agora).size)
        assertEquals(0, motor(revisao = setOf("Outro tipo")).pares(Cr1.issues, Cr1.vinculos, Cr1.agora).size)
        assertEquals(4, motor(revisao = setOf("checklist review")).pares(Cr1.issues, Cr1.vinculos, Cr1.agora).size, "sem diferenciar maiúsculas")
    }

    @Test
    fun paresESemReviewUsamAMesmaListaDeTiposReview() {
        // Com uma lista de review que não cobre o tipo do vínculo, o checklist não pode sumir das DUAS listas (M8):
        // sem par com review válida, ele é "sem review".
        val outra = motor(revisao = setOf("Outro tipo"))
        assertEquals(0, outra.pares(Cr1.issues, Cr1.vinculos, Cr1.agora).size)
        val semReview = outra.semReview(Cr1.issues, Cr1.vinculos).map { it.key }
        assertEquals(listOf("TG-401", "TG-403", "TG-405", "TG-407", "TG-409"), semReview.sorted())
        // Com a lista certa, cada checklist está em exatamente uma das duas listas.
        val m = motor()
        val comPar = m.pares(Cr1.issues, Cr1.vinculos, Cr1.agora).map { it.pai.key }
        val sem = m.semReview(Cr1.issues, Cr1.vinculos).map { it.key }
        assertEquals(emptySet(), comPar.toSet() intersect sem.toSet())
    }

    @Test
    fun tiposPaiVemDoYamlDeChecklistReview() {
        val soTg = RegrasChecklistReview.PADRAO.copy(tiposPai = setOf("Checklist TG"))
        assertEquals(0, motor(soTg).pares(Cr1.issues, Cr1.vinculos, Cr1.agora).size)
        assertNotNull(motor().pares(Cr1.issues, Cr1.vinculos, Cr1.agora).firstOrNull())
    }

    private fun SnapDeTeste_i(s: String) = br.sidia.painel.trabalho.SnapDeTeste.i(s)
}
