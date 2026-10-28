package br.sidia.painel.pessoas

import br.sidia.painel.domain.Transition
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Agenda: previsão de término pela capacidade líquida, com e sem a ausência, sobre o CAP-1. */
class AgendaTest {
    private val agenda = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap)
    private val hoje = Cap1.hoje
    private val abertas = Cap1.abertas()
    private fun dia(s: String) = LocalDate.parse(s)

    private fun fila(
        key: String, prioridade: String?, vence: String?, criada: String, status: String = "Open", horas: Double = 1.0, resp: String = "ana",
    ) = Cap1.snapshot(
        key, "Checklist", resp, status, Trb1.t(criada), vence?.let(LocalDate::parse), (horas * 3600).toLong(), null, null, prioridade,
        transicoes = if (status == "Open") emptyList() else listOf(Transition(Trb1.t("10-22T12"), "Open", status, null)),
    )

    @Test
    fun filaNaOrdem() {
        val itens = listOf(
            fila("TG-260", "Weird", "2026-11-02", "10-01T12"),
            fila("TG-259", null, null, "10-01T12"),
            fila("TG-258", "Medium", "2026-11-06", "10-25T12"),
            fila("TG-257", "Medium", "2026-11-06", "10-24T12"),
            fila("TG-256", "Medium", "2026-11-05", "10-28T12"),
            fila("TG-255", "High", "2026-11-20", "10-26T12"),
            fila("TG-254", "Low", "2026-11-01", "10-01T12"),
            fila("TG-253", "Medium", null, "10-01T12", status = "In Progress"),
            fila("TG-252", "Highest", "2026-11-30", "10-26T12"),
            fila("TG-251", "Medium", "2026-11-06", "10-24T12"),
        )
        val ordem = agenda.agendar("ana", itens, hoje).map { it.key }
        // Iniciada primeiro; depois prioridade (Highest > High > Medium > Low > desconhecida/nula);
        // na mesma prioridade, vencimento (sem vencimento no fim); depois criação; por fim a chave.
        assertEquals(
            listOf("TG-253", "TG-252", "TG-255", "TG-256", "TG-251", "TG-257", "TG-258", "TG-254", "TG-260", "TG-259"),
            ordem,
        )
    }

    @Test
    fun terminoPelaCapacidade() {
        val ag = agenda.agendar("ana", abertas, hoje).associateBy { it.key }
        assertEquals(setOf("TG-201", "TG-202"), ag.keys)
        // TG-201: sexta 30/10 (5,6 h) e terça 03/11 (5,6 h); 02/11 é feriado.
        assertEquals(dia("2026-11-03"), ag.getValue("TG-201").termino)
        assertEquals(mapOf(dia("2026-10-30") to 5.6, dia("2026-11-03") to 5.6), ag.getValue("TG-201").horasPorDia.mapValues { Math.round(it.value * 10) / 10.0 })
        assertFalse(ag.getValue("TG-201").atrasa, "vence 04/11")
        // TG-202: 04 e 05/11 `ana` está ausente; só termina na sexta 06/11, depois do vencimento (05/11).
        assertEquals(dia("2026-11-06"), ag.getValue("TG-202").termino)
        assertEquals(dia("2026-11-05"), ag.getValue("TG-202").vencimento)
        assertTrue(ag.getValue("TG-202").atrasa)
        assertEquals(listOf(dia("2026-11-06")), ag.getValue("TG-202").horasPorDia.keys.toList())
    }

    @Test
    fun semAusenciaNaoAtrasa() {
        val par = agenda.comparar("ana", abertas, hoje, Cap1.capacidadeSemAusencia).first { it.first.key == "TG-202" }
        assertEquals(dia("2026-11-06"), par.first.termino)
        assertTrue(par.first.atrasa)
        assertEquals(dia("2026-11-04"), par.second.termino)
        assertFalse(par.second.atrasa)
        // O par segue a mesma ordem da fila.
        assertEquals(listOf("TG-201", "TG-202"), agenda.comparar("ana", abertas, hoje, Cap1.capacidadeSemAusencia).map { it.first.key })
    }

    @Test
    fun vencidaEntraNaFilaEContinuaAtrasando() {
        val c = agenda.agendar("carla", abertas, hoje).single()
        assertEquals("TG-205", c.key)
        // 6 h assumidas: 5,6 h na sexta 30/10 e o resto na segunda 09/11 (carla está ausente de 02 a 06/11).
        assertEquals(dia("2026-11-09"), c.termino)
        assertTrue(c.atrasa)
    }

    @Test
    fun naoCabeNoHorizonte() {
        // Sete dias a partir de hoje: 30/10 a 05/11. A TG-201 cabe (termina 03/11); a TG-202 só terminaria em 06/11.
        val curta = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap, horizonteDias = 7)
        val ag = curta.agendar("ana", abertas, hoje).associateBy { it.key }
        assertEquals(dia("2026-11-03"), ag.getValue("TG-201").termino)
        assertNull(ag.getValue("TG-202").termino)
        assertTrue(ag.getValue("TG-202").atrasa, "não cabe antes do vencimento (05/11, dentro do horizonte)")
        // Vencimento além do horizonte: não dá para afirmar que atrasa.
        val longe = Agenda(HorasDaIssue(Cap1.regras), Cap1.capacidade, Cap1.statusMap, horizonteDias = 6)
        val ag6 = longe.agendar("ana", abertas, hoje).associateBy { it.key }
        assertNull(ag6.getValue("TG-202").termino)
        assertFalse(ag6.getValue("TG-202").atrasa)
    }

    @Test
    fun pessoaSemCapacidadeNaoQuebra() {
        val ag = agenda.agendar("fantasma", listOf(fila("TG-270", "High", "2026-11-04", "10-25T12", resp = "fantasma")), hoje).single()
        assertNull(ag.termino)
        assertTrue(ag.atrasa)
        assertTrue(ag.horasPorDia.isEmpty())
        // Sem vencimento não há atraso a declarar.
        val semVenc = agenda.agendar("fantasma", listOf(fila("TG-271", "High", null, "10-25T12", resp = "fantasma")), hoje).single()
        assertNull(semVenc.termino)
        assertFalse(semVenc.atrasa)
    }

    @Test
    fun soAgendaAsDaPessoaEAsNaoConcluidas() {
        assertEquals(listOf("TG-203"), agenda.agendar("bruno", abertas, hoje).map { it.key })
        val concluida = fila("TG-272", "High", "2026-11-04", "10-25T12", status = "Closed", resp = "bruno")
        assertEquals(listOf("TG-203"), agenda.agendar("bruno", abertas + concluida, hoje).map { it.key })
    }

    @Test
    fun comoPrevisaoPorChave() {
        val p = agenda.comoPrevisao(abertas + fila("TG-273", "High", null, "10-25T12", resp = "ana").let { it.copy(assignee = null) }, hoje)
        assertEquals(dia("2026-11-03"), p.previsao("TG-201"))
        assertEquals(dia("2026-11-06"), p.previsao("TG-202"))
        assertEquals(dia("2026-11-09"), p.previsao("TG-205"))
        assertNull(p.previsao("TG-273"), "sem responsável não há capacidade a consumir")
        assertNull(p.previsao("TG-999"))
    }
}
