package br.sidia.painel.cards.plano

import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plano de 4 a 8 semanas sobre o CAP-1. */
class PlanoCalculatorTest {
    private val calc = PlanoCalculator(Cap1.motor)
    private val pessoas = listOf("ana", "bruno", "carla")

    @Test
    fun planoQuatroSemanas() {
        val a = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, 4)
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"), a.dto.semanas)
        val ana = a.dto.linhas.first { it.pessoa == "ana" }.celulas
        assertEquals(8.4, ana[0].planejadaH, 1e-9)
        assertEquals(5.6, ana[0].capacidadeH, 1e-9)
        assertEquals(1.5, ana[0].ocupacao.fracao!!, 1e-9)
        assertEquals("problema", ana[0].ocupacao.estado)
        assertEquals(2, ana[0].itens)
        assertEquals(0.0, ana[0].semDataH)
        assertEquals(0, ana[2].itens)
        assertEquals(pessoas, a.dto.linhas.map { it.pessoa })
    }

    @Test
    fun oitoSemanas() {
        val a = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, 8)
        assertEquals(8, a.dto.semanas.size)
        assertTrue(a.dto.linhas.all { it.celulas.size == 8 })
    }

    @Test
    fun semanaSemCapacidadeMostraAsHorasPlanejadas() {
        val tres = Cap1.snapshot("TG-420", "Checklist", "carla", "Open", Trb1.t("10-22T12"), LocalDate.of(2026, 11, 4), 6 * 3600L, null, null)
        val c = calc.calcular(pessoas, listOf(tres), Cap1.hoje, 4).dto.linhas.first { it.pessoa == "carla" }.celulas[1]
        assertTrue(c.ocupacao.semCapacidade)
        assertEquals(3.0, c.planejadaH, 1e-9)
        assertEquals(0.0, c.capacidadeH)
    }

    @Test
    fun horasSemVencimentoAparecemAParte() {
        val semVenc = Cap1.snapshot("TG-421", "Checklist", "bruno", "Open", Trb1.t("10-22T12"), null, 8 * 3600L, null, null)
        val c = calc.calcular(pessoas, Cap1.abertas() + semVenc, Cap1.hoje, 4).dto.linhas.first { it.pessoa == "bruno" }.celulas
        assertEquals(2.0, c[0].semDataH, 1e-9)
        assertEquals(4.0, c[0].planejadaH, 1e-9)
        assertEquals(2, c[0].itens)
        assertEquals(1, c[3].itens)
    }

    @Test
    fun segmentosDeCelulaTemOMesmoTamanhoDoNumero() {
        val a = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, 4)
        for (l in a.dto.linhas) for (c in l.celulas) {
            assertEquals(c.itens, a.segmentos.getValue("celula:${l.pessoa}:${c.semana}").size, "${l.pessoa} ${c.semana}")
        }
        assertEquals(listOf("TG-201", "TG-202"), a.segmentos.getValue("celula:ana:2026-10-26").map { it.key })
    }

    @Test
    fun vencidaNaoOcupaSemana() {
        val a = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, 4)
        assertTrue(a.dto.linhas.first { it.pessoa == "carla" }.celulas.all { it.itens == 0 && it.planejadaH == 0.0 })
    }
}
