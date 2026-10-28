package br.sidia.painel.cards.esforco

import br.sidia.painel.cards.posse.PosseDePessoa
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.RegrasDeEsforco
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Trb1
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Esforço da task: três componentes (peso do tipo, testes TEST-*, horas) e o índice composto; por pessoa, pela posse. */
class EsforcoCalculatorTest {
    private val tol = 1e-6
    private val calc = EsforcoCalculator(RegrasDeEsforco.PADRAO)

    /** TG-1 do Plano 6: Checklist com 3 testes (`TEST-A`, `test-b`, `TEST-C`), uma label comum e worklogs de 6 h + 4 h. */
    private fun tg1(tipo: String = "Checklist", labels: List<String> = listOf("TEST-A", "test-b", "TEST-C", "login"), gastoS: Long? = null): IssueSnapshot =
        Cap1.snapshot("TG-1", tipo, "ana", "Closed", Trb1.t("10-05T12"), null, null, null, gastoS, labels = labels)

    private fun w(id: String, horas: Double) = WorklogRecord(id, "ana", Instant.parse("2026-10-06T13:00:00Z"), (horas * 3600).toLong())

    private val seisMaisQuatro = listOf(w("1", 6.0), w("2", 4.0))

    @Test
    fun indiceDaTask() {
        val e = calc.daTask(tg1(), seisMaisQuatro)
        assertEquals("TG-1", e.key)
        assertEquals(3.0, e.pesoTipo, tol)
        assertEquals(3, e.testes)
        assertEquals(10.0, e.horas, tol)
        assertEquals(14.5, e.indice, tol)
        assertFalse(e.horasDoCampo)
    }

    @Test
    fun porPessoaPelaPosse() {
        val tarefa = calc.daTask(tg1(), seisMaisQuatro)
        val posse = listOf(PosseDePessoa("ana", 3.0, 0.642857142857, 1), PosseDePessoa("bruno", 1.666667, 0.357142857143, 1))
        val p = calc.porPessoa(listOf(tarefa to posse))
        assertEquals(listOf("ana", "bruno"), p.keys.toList())
        assertEquals(9.321429, p.getValue("ana").indice, tol)
        assertEquals(5.178571, p.getValue("bruno").indice, tol)
        // Cada componente também segue a fração (as horas do worklog são da task, não de quem registrou).
        assertEquals(6.428571, p.getValue("ana").horas, tol)
        assertEquals(3 * 0.642857142857, p.getValue("ana").testesPonderados, tol)
        assertEquals("ana", p.getValue("ana").key)
    }

    @Test
    fun porPessoaSomaTasksEIgnoraAPosseNula() {
        val t1 = calc.daTask(tg1(), seisMaisQuatro)
        val t2 = calc.daTask(tg1(labels = emptyList()).copy(key = "TG-2"), listOf(w("3", 2.0))) // 3 + 0 + 2 = 5
        val p = calc.porPessoa(
            listOf(
                t1 to listOf(PosseDePessoa("ana", 1.0, 0.5, 1), PosseDePessoa(null, 1.0, 0.5, 1)),
                t2 to listOf(PosseDePessoa("ana", 2.0, 1.0, 1)),
            ),
        )
        // A parte "de ninguém" (ou de outra pessoa) não vira linha de pessoa.
        assertEquals(setOf("ana"), p.keys)
        assertEquals(14.5 * 0.5 + 5.0, p.getValue("ana").indice, tol)
    }

    @Test
    fun semWorklogUsaTimespentComAviso() {
        val e = calc.daTask(tg1(gastoS = 7200), emptyList())
        assertEquals(2.0, e.horas, tol)
        assertTrue(e.horasDoCampo)
        assertEquals(3.0 + 1.5 + 2.0, e.indice, tol)
        // Com worklog, o campo é ignorado (não soma duas vezes).
        val com = calc.daTask(tg1(gastoS = 7200), listOf(w("1", 1.0)))
        assertEquals(1.0, com.horas, tol)
        assertFalse(com.horasDoCampo)
        // Sem worklog e sem tempo gasto: zero hora, sem marcar o campo.
        val nada = calc.daTask(tg1(), emptyList())
        assertEquals(0.0, nada.horas, tol)
        assertFalse(nada.horasDoCampo)
    }

    @Test
    fun tipoSemPesoUsaPadrao() {
        assertEquals(1.0, calc.daTask(tg1(tipo = "Tipo Novo"), emptyList()).pesoTipo, tol)
        // O tipo casa sem diferenciar maiúsculas nem espaços nas pontas.
        assertEquals(1.0, calc.daTask(tg1(tipo = "  checklist review "), emptyList()).pesoTipo, tol)
        assertEquals(2.0, calc.daTask(tg1(tipo = "FEATURE CHECK"), emptyList()).pesoTipo, tol)
    }

    @Test
    fun labelRepetidaContaUmaVez() {
        val e = calc.daTask(tg1(labels = listOf("TEST-A", "test-a", "Test-A", "TEST-B", "test-")), emptyList())
        // `TEST-A` em três grafias é um teste; a label que é só o prefixo não é teste.
        assertEquals(2, e.testes)
        assertEquals(0, calc.daTask(tg1(labels = listOf("testing", "attest-x")), emptyList()).testes)
    }

    @Test
    fun regrasPersonalizadasMudamOIndice() {
        val regras = RegrasDeEsforco(1, mapOf("Checklist" to 2.0), 1.0, "QA-", 1.0, 0.5)
        val e = EsforcoCalculator(regras).daTask(tg1(labels = listOf("qa-x", "TEST-A")), seisMaisQuatro)
        assertEquals(1, e.testes)
        assertEquals(2.0 + 1.0 + 10.0 * 0.5, e.indice, tol)
    }
}
