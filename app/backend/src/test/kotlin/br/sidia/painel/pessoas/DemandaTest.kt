package br.sidia.painel.pessoas

import br.sidia.painel.pessoas.Ocupacao.SemCapacidade
import br.sidia.painel.pessoas.Ocupacao.Valor
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Demanda semanal, ocupação sem sentinela, atraso acumulado e índice de Jain, sobre o CAP-1. */
class DemandaTest {
    private val demanda = Demanda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap, Cap1.regras)
    private val hoje = Cap1.hoje
    private fun dia(s: String) = LocalDate.parse(s)
    private val abertas = Cap1.abertas()

    private fun semana(c: CargaDaPessoa, segunda: String) = c.semanas.first { it.semana == dia(segunda) }

    private fun extra(key: String, resp: String, vence: String?, horas: Double, status: String = "Open", tipo: String = "Checklist") =
        Cap1.snapshot(
            key, tipo, resp, status, Trb1.t("10-21T12"), vence?.let(LocalDate::parse), (horas * 3600).toLong(), null, null,
            transicoes = if (status == "Open") emptyList() else listOf(br.sidia.painel.domain.Transition(Trb1.t("10-22T12"), "Open", status, null)),
        )

    @Test
    fun espalhaAteOVencimento() {
        // TG-201 (11,2 h, vence 04/11) e TG-202 (5,6 h, vence 05/11): a semana do vencimento é a de 02/11; o mínimo é 2 semanas.
        val so201 = demanda.carga("ana", abertas.filter { it.key == "TG-201" }, hoje, 4)
        assertEquals(5.6, semana(so201, "2026-10-26").demandaH, 1e-9)
        assertEquals(5.6, semana(so201, "2026-11-02").demandaH, 1e-9)
        assertEquals(0.0, semana(so201, "2026-11-09").demandaH, 1e-9)
        val so202 = demanda.carga("ana", abertas.filter { it.key == "TG-202" }, hoje, 4)
        assertEquals(2.8, semana(so202, "2026-10-26").demandaH, 1e-9)
        assertEquals(2.8, semana(so202, "2026-11-02").demandaH, 1e-9)
    }

    @Test
    fun ocupacaoDaSemana() {
        val c = demanda.carga("ana", abertas, hoje, 4)
        val s1 = semana(c, "2026-10-26")
        assertEquals(8.4, s1.demandaH, 1e-9)
        assertEquals(5.6, s1.capacidadeH, 1e-9)
        assertEquals(1.5, (s1.ocupacao as Valor).fracao, 1e-9)
        assertEquals(0.75, ((semana(c, "2026-11-02")).ocupacao as Valor).fracao, 1e-9)
        assertEquals(4, c.semanas.size)
    }

    @Test
    fun ocupacaoDoHorizonte() {
        val c = demanda.carga("ana", abertas, hoje, 4)
        assertEquals(0.25, (c.ocupacaoHorizonte as Valor).fracao, 1e-9)
        assertFalse(c.horasAssumidas, "as horas de ana vêm do restante do Jira")
        assertEquals(0.0, c.atrasoAcumuladoH)
    }

    @Test
    fun vencidaViraAtrasoAcumulado() {
        val c = demanda.carga("carla", abertas, hoje, 4)
        assertEquals(6.0, c.atrasoAcumuladoH, 1e-9)
        assertTrue(c.horasAssumidas)
        // A vencida não entra em nenhuma semana.
        assertTrue(c.semanas.all { it.demandaH == 0.0 })
    }

    @Test
    fun soContaAsIssuesDaPessoaEAsNaoConcluidas() {
        val c = demanda.carga("bruno", abertas, hoje, 4)
        // TG-203 (4 h, vence 05/11): 2 h por semana nas duas primeiras semanas; as de ana e carla não entram.
        assertEquals(2.0, semana(c, "2026-10-26").demandaH, 1e-9)
        assertEquals(0.0, c.atrasoAcumuladoH)
        val concluida = extra("TG-300", "bruno", "2026-11-04", 8.0, status = "Closed")
        val c2 = demanda.carga("bruno", abertas + concluida, hoje, 4)
        assertEquals(2.0, semana(c2, "2026-10-26").demandaH, 1e-9)
    }

    @Test
    fun semanaSemCapacidade() {
        // 6 h para `carla` vencendo em 04/11: 3 h na semana de 02/11, em que ela está ausente a semana inteira.
        val c = demanda.carga("carla", listOf(extra("TG-310", "carla", "2026-11-04", 6.0)), hoje, 4)
        val s = semana(c, "2026-11-02")
        assertEquals(SemCapacidade, s.ocupacao)
        assertEquals(3.0, s.demandaH, 1e-9)
        assertEquals(0.0, s.capacidadeH)
        // E a semana do horizonte inteiro tem capacidade (as outras semanas), então o horizonte é um valor.
        assertTrue(c.ocupacaoHorizonte is Valor)
    }

    @Test
    fun horizonteSemNenhumaCapacidadeEhSemCapacidade() {
        val c = demanda.carga("fantasma", listOf(extra("TG-311", "fantasma", "2026-11-04", 6.0)), hoje, 4)
        assertEquals(SemCapacidade, c.ocupacaoHorizonte)
        assertTrue(c.semanas.all { it.ocupacao == SemCapacidade })
    }

    @Test
    fun semVencimentoIniciadaDuasSemanas() {
        val c = demanda.carga("bruno", listOf(extra("TG-320", "bruno", null, 8.0, status = "In Progress")), hoje, 4)
        assertEquals(listOf(4.0, 4.0, 0.0, 0.0), c.semanas.map { it.demandaH })
    }

    @Test
    fun naoIniciadaQuatroSemanas() {
        val c = demanda.carga("bruno", listOf(extra("TG-321", "bruno", null, 8.0)), hoje, 4)
        assertEquals(listOf(2.0, 2.0, 2.0, 2.0), c.semanas.map { it.demandaH })
    }

    @Test
    fun vencimentoLongeEspalhaAteLaEOQueSobraDoHorizonteFicaFora() {
        // Vence em 04/12 (semana de 30/11, índice 5): 6 h em 6 semanas (1 h por semana); o horizonte de 4 semanas vê 4 delas.
        val c = demanda.carga("bruno", listOf(extra("TG-322", "bruno", "2026-12-04", 6.0)), hoje, 4)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), c.semanas.map { it.demandaH })
    }

    @Test
    fun vencimentoHojeOuNaSemanaAtualUsaOMinimoDeSemanas() {
        val c = demanda.carga("bruno", listOf(extra("TG-323", "bruno", "2026-10-30", 4.0)), hoje, 4)
        assertEquals(listOf(2.0, 2.0, 0.0, 0.0), c.semanas.map { it.demandaH })
        assertEquals(0.0, c.atrasoAcumuladoH, "vence hoje: ainda não está vencida")
    }

    @Test
    fun itensEHorasSemDataPorSemana() {
        val semVenc = extra("TG-330", "bruno", null, 8.0)
        val c = demanda.carga("bruno", abertas.filter { it.key == "TG-203" } + semVenc, hoje, 4)
        // TG-203 (4 h, 05/11) ocupa as 2 primeiras semanas; a sem vencimento (8 h, não iniciada) ocupa as 4.
        assertEquals(listOf(2, 2, 1, 1), c.semanas.map { it.itens })
        assertEquals(listOf(2.0, 2.0, 2.0, 2.0), c.semanas.map { it.semDataH })
        assertEquals(listOf(4.0, 4.0, 2.0, 2.0), c.semanas.map { it.demandaH })
    }

    @Test
    fun semanasQueOcupaEhARegraUnica() {
        val d = demanda
        assertEquals(2, d.semanasQueOcupa(abertas.first { it.key == "TG-201" }, hoje))
        assertNull(d.semanasQueOcupa(abertas.first { it.key == "TG-205" }, hoje), "vencida não ocupa semana")
        assertEquals(4, d.semanasQueOcupa(extra("TG-331", "bruno", null, 1.0), hoje))
        assertEquals(2, d.semanasQueOcupa(extra("TG-332", "bruno", null, 1.0, status = "In Progress"), hoje))
    }

    @Test
    fun jain() {
        assertEquals(0.9, assertNotNull(Jain.indice(listOf(Valor(0.5), Valor(1.0)), 3.0)), 1e-9)
        // SemCapacidade é ignorada: o índice não muda.
        assertEquals(0.9, assertNotNull(Jain.indice(listOf(Valor(0.5), SemCapacidade, Valor(1.0)), 3.0)), 1e-9)
        // Um só valor (ou nenhum) não tem índice.
        assertNull(Jain.indice(listOf(Valor(0.5)), 3.0))
        assertNull(Jain.indice(listOf(Valor(0.5), SemCapacidade), 3.0))
        assertNull(Jain.indice(emptyList(), 3.0))
        // 5,0 é limitado ao teto 3,0: (3 + 1)² / (2 × (9 + 1)) = 0,8.
        assertEquals(0.8, assertNotNull(Jain.indice(listOf(Valor(5.0), Valor(1.0)), 3.0)), 1e-9)
        // Todos zerados: igualdade perfeita.
        assertEquals(1.0, assertNotNull(Jain.indice(listOf(Valor(0.0), Valor(0.0)), 3.0)), 1e-9)
    }
}
