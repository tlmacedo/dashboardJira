package br.sidia.painel.cards.capacidade

import br.sidia.painel.pessoas.Ausencia
import br.sidia.painel.pessoas.Ausencias
import br.sidia.painel.pessoas.Cap1
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Capacidade e ausências sobre o CAP-1: mapa de 8 semanas, alertas e atrasos previstos com e sem a ausência. */
class CapacidadeCardCalculatorTest {
    private val calc = CapacidadeCardCalculator(Cap1.motor)
    private val pessoas = listOf("ana", "bruno", "carla")
    private val d = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, "planejamento v1").dto

    @Test
    fun mapaMostraAusente() {
        val carla = d.mapa.first { it.pessoa == "carla" }
        val s = carla.semanas.first { it.semana == "2026-11-02" }
        assertTrue(s.ocupacao.semCapacidade)
        assertNull(s.ocupacao.fracao)
        assertTrue(s.ausente, "a semana inteira de ausência aparece como aus.")
        assertEquals(8, carla.semanas.size)
        // Uma semana com capacidade não é ausente; semana com capacidade zero só por feriado/fim de semana também não.
        assertTrue(carla.semanas.filter { it.semana != "2026-11-02" }.none { it.ausente })
    }

    @Test
    fun semanaDeFeriadoSemAusenciaNaoEhAusente() {
        val ana = d.mapa.first { it.pessoa == "ana" }.semanas.first { it.semana == "2026-11-02" }
        assertTrue(!ana.ausente && !ana.ocupacao.semCapacidade, "ana tem 11,2 h na semana de 02/11")
    }

    @Test
    fun alertaAusenciaComTrabalho() {
        val a = d.alertas.single { it.pessoa == "ana" && it.tipo == "AUSENCIA_COM_TRABALHO" }
        assertEquals("2026-11-02", a.semana)
        // No cenário sem ausência a TG-202 seria feita na quarta 04/11 (5,6 h): é o que precisa de novo plano.
        assertEquals(5.6, a.horasAReplanejar!!, 1e-9)
    }

    @Test
    fun alertaDeCargaAlta() {
        // A semana atual da ana só tem a sexta (5,6 h) para 8,4 h de demanda: 150%.
        val a = d.alertas.single { it.pessoa == "ana" && it.tipo == "CARGA_ALTA" }
        assertEquals("2026-10-26", a.semana)
        assertNull(a.horasAReplanejar)
    }

    @Test
    fun meioDiaDeAusenciaNaoDisparaOAlertaDeAusencia() {
        // O bruno tem só 0,5 dia de ausência (10/11): abaixo de 2 dias.
        assertTrue(d.alertas.none { it.pessoa == "bruno" && it.tipo == "AUSENCIA_COM_TRABALHO" })
    }

    @Test
    fun atrasoPorAusencia() {
        assertEquals(1, d.atrasosPrevistos)
        assertEquals(1, d.atrasosPorAusencia)
        val a = d.listaAtrasos.single()
        assertEquals("TG-202", a.key)
        assertEquals("ana", a.pessoa)
        assertEquals("2026-11-05", a.vence)
        assertEquals("2026-11-06", a.termino)
        assertEquals("AUSENCIA", a.causa)
    }

    @Test
    fun atrasoPorCargaQuandoSemAusenciaTambemAtrasa() {
        // 80 h para a ana vencendo em 04/11: atrasa com ou sem a ausência (causa CARGA).
        val enorme = Cap1.snapshot("TG-410", "Checklist", "ana", "Open", Trb1.t("10-22T12"), LocalDate.of(2026, 11, 4), 80 * 3600L, null, null, "Highest")
        val a = calc.calcular(pessoas, Cap1.abertas() + enorme, Cap1.hoje, "v").dto.listaAtrasos.first { it.key == "TG-410" }
        assertEquals("CARGA", a.causa)
    }

    @Test
    fun vencidaNaoEhAtrasoPrevisto() {
        assertTrue(d.listaAtrasos.none { it.key == "TG-205" }, "a TG-205 já venceu: aparece em vencidas da Carga")
    }

    @Test
    fun ausenciasSemMotivoEmOrdemAlfabetica() {
        assertEquals(listOf("ana", "bruno", "carla"), d.ausencias.map { it.pessoa })
        assertEquals(Triple("2026-11-04", "2026-11-05", 1.0), d.ausencias.first().let { Triple(it.de, it.ate, it.fracao) })
        assertEquals(0.5, d.ausencias.first { it.pessoa == "bruno" }.fracao)
    }

    @Test
    fun soAusenciasDasPessoasDaVisao() {
        val r = calc.calcular(listOf("ana"), Cap1.abertas(), Cap1.hoje, "v").dto
        assertEquals(listOf("ana"), r.ausencias.map { it.pessoa })
        assertEquals(listOf("ana"), r.mapa.map { it.pessoa })
    }

    @Test
    fun feriadosDoHorizonteComNome() {
        assertEquals(listOf("2026-11-02" to "Finados", "2026-11-20" to "Consciência Negra"), d.feriados.map { it.dia to it.nome })
    }

    @Test
    fun jainDoConjuntoVisivel() {
        assertTrue(d.jain != null)
        assertNull(calc.calcular(listOf("ana"), Cap1.abertas(), Cap1.hoje, "v").dto.jain)
    }

    @Test
    fun ausenciaHipoteticaNaoAlteraOMotor() {
        // O simulador (bloco 6b) usa `Ausencias.mais`: aqui só garantimos que o cálculo usa o motor que recebe.
        val outro = MotorDePessoas(
            Cap1.equipes, Ausencias(listOf(Ausencia("ana", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6), 1.0))),
            Cap1.calendario, Cap1.regras, Cap1.statusMap,
        )
        val r = CapacidadeCardCalculator(outro).calcular(pessoas, Cap1.abertas(), Cap1.hoje, "v").dto
        assertEquals(2, r.atrasosPrevistos, "TG-201 também atrasa se a ana faltar de 03 a 06/11")
        assertEquals(1, d.atrasosPrevistos)
    }

    @Test
    fun segmentosTemOMesmoTamanhoDoNumero() {
        val a = calc.calcular(pessoas, Cap1.abertas(), Cap1.hoje, "v")
        assertEquals(a.dto.atrasosPrevistos, a.segmentos.getValue("atrasosPrevistos").size)
        assertEquals(a.dto.atrasosPorAusencia, a.segmentos.getValue("atrasosPorAusencia").size)
    }
}
