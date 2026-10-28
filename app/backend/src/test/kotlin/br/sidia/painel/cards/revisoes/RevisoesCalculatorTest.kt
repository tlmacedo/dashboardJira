package br.sidia.painel.cards.revisoes

import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Revisões pendentes por revisor: abertas dos tipos de review pelo responsável, vencidas e fora do prazo de revisão (SLA em dias úteis). */
class RevisoesCalculatorTest {
    private val calc = RevisoesCalculator(Cap1.motor, setOf("Checklist Review"))

    private fun review(key: String, resp: String?, criada: String, vence: String?, tipo: String = "Checklist Review", status: String = "Open") =
        Cap1.snapshot(key, tipo, resp, status, Trb1.t(criada), vence?.let(LocalDate::parse), null, null, null)

    /**
     * Hoje é 30/10 (08:00 em Manaus). TG-305 do `bruno` foi criada em 26/10 (4 dias úteis) e venceu em 29/10; TG-306 dele é
     * de ontem. A TG-307 da `ana` tem 2 dias úteis (o prazo de 2 NÃO é ultrapassado). TG-308 não tem revisor.
     */
    private val tasks: List<IssueSnapshot> = listOf(
        review("TG-305", "bruno", "10-26T12", "2026-10-29"),
        review("TG-306", "bruno", "10-29T12", "2026-11-05"),
        review("TG-307", "ana", "10-28T12", null),
        review("TG-308", null, "10-27T12", "2026-10-28"),
        review("TG-309", "bruno", "10-20T12", "2026-10-21", tipo = "Checklist"), // não é review
        review("TG-310", "carla", "10-26T12", "2026-10-29"),
        review("TG-311", "bruno", "10-20T12", "2026-10-21", status = "Closed"), // concluída
    )

    private fun rodar(visiveis: Set<String> = setOf("ana", "bruno", "carla"), veSemRevisor: Boolean = true) =
        calc.calcular(visiveis, tasks, Cap1.hoje, Cap1.agora, veSemRevisor)

    @Test
    fun revisoesDoBruno() {
        val bruno = rodar().dto.linhas.first { it.revisor == "bruno" }
        assertEquals(2, bruno.pendentes)
        assertEquals(1, bruno.vencidas)
        assertEquals(1, bruno.foraDoSla) // TG-305: 4 dias úteis > 2
        assertEquals(2.5, bruno.idadeP50!!, 1e-9) // mediana de 4,0 e 1,0
    }

    @Test
    fun prazoDeRevisaoNaoEhUltrapassadoNoLimite() {
        val ana = rodar().dto.linhas.first { it.revisor == "ana" }
        assertEquals(1, ana.pendentes)
        assertEquals(0, ana.foraDoSla)
        assertEquals(0, ana.vencidas)
        assertEquals(2.0, ana.idadeP50!!, 1e-9)
    }

    @Test
    fun soReviewAbertaContaEPessoasEmOrdemAlfabetica() {
        val d = rodar().dto
        assertEquals(listOf("ana", "bruno", "carla"), d.linhas.map { it.revisor })
        // A task comum (TG-309) e a review fechada (TG-311) do bruno não entram.
        assertEquals(2, d.linhas.first { it.revisor == "bruno" }.pendentes)
    }

    @Test
    fun semRevisorSoCoordenacao() {
        assertEquals(1, rodar(veSemRevisor = true).dto.semRevisor)
        assertNull(rodar(veSemRevisor = false).dto.semRevisor)
        assertEquals(listOf("TG-308"), rodar(veSemRevisor = true).segmentos.getValue("semRevisor").map { it.key })
        assertNull(rodar(veSemRevisor = false).segmentos["semRevisor"])
    }

    @Test
    fun soRevisoresDaVisao() {
        val d = rodar(visiveis = setOf("ana")).dto
        assertEquals(listOf("ana"), d.linhas.map { it.revisor })
        assertTrue("bruno" !in d.toString() && "carla" !in d.toString())
    }

    @Test
    fun segmentosAbremAsMesmasTasksDoNumero() {
        val a = rodar()
        assertEquals(listOf("TG-305", "TG-306"), a.segmentos.getValue("revisor:bruno:pendentes").map { it.key })
        assertEquals(listOf("TG-305"), a.segmentos.getValue("revisor:bruno:vencidas").map { it.key })
        assertEquals(listOf("TG-305"), a.segmentos.getValue("revisor:bruno:foraDoSla").map { it.key })
        for (l in a.dto.linhas) {
            assertEquals(l.pendentes, a.segmentos.getValue("revisor:${l.revisor}:pendentes").size)
            assertEquals(l.vencidas, a.segmentos.getValue("revisor:${l.revisor}:vencidas").size)
            assertEquals(l.foraDoSla, a.segmentos.getValue("revisor:${l.revisor}:foraDoSla").size)
        }
    }

    @Test
    fun tipoDeReviewSemDiferenciarMaiusculas() {
        val c = RevisoesCalculator(Cap1.motor, setOf("  CHECKLIST review "))
        assertEquals(2, c.calcular(setOf("bruno"), tasks, Cap1.hoje, Cap1.agora, false).dto.linhas.single().pendentes)
    }
}
