package br.sidia.painel.governanca

import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste
import br.sidia.painel.trabalho.Trb1
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** As regras puras da Qualidade dos dados, sobre issues em memória (o cenário completo pelo HTTP está em `QualidadeRoutesTest`). */
class QualidadeDosDadosTest {
    private val hoje = LocalDate.of(2026, 10, 30)
    private val map = Trb1.statusMap
    private val roles = Trb1.papeis
    private val labels = LabelsDeAtraso(1, listOf("ONHOLDBY-*", "LATE_SENT"), setOf("LATE_SENT"), listOf("skill:*"))
    private val todosOsCampos = mapOf("Story Points" to true, "Epic Link" to true, "Flagged" to true, "OS Version" to true)

    private fun aberta(chave: String, resp: String? = "ana", status: String = "Open", estimateS: Long? = 3600L, vence: String? = "2026-11-10", prioridade: String? = "High", labels: List<String> = emptyList()) =
        SnapDeTeste.snap(chave, resp = resp, status = status, estimateS = estimateS, vence = vence, prioridade = prioridade, labels = labels).copy(epicKey = "EP-1")

    private fun verificar(
        abertas: List<IssueSnapshot>, recentes: List<IssueSnapshot> = emptyList(), campos: Map<String, Boolean> = todosOsCampos,
        bloqueadas: List<IssueSnapshot>? = emptyList(),
    ) = QualidadeDosDados.verificar(abertas, recentes, map, roles, hoje, "Medium", campos, labels, bloqueadas).associateBy { it.verificacao }

    @Test
    fun semResponsavel() {
        val abertas = listOf(aberta("TG-1"), aberta("TG-2"), aberta("TG-3"), aberta("TG-4", resp = null))
        val l = verificar(abertas).getValue(VerificacaoQD.SEM_RESPONSAVEL)
        assertEquals(1, l.n)
        assertEquals(4, l.universo)
        assertEquals(0.25, l.fracao)
        assertEquals("atencao", l.estado)
    }

    @Test
    fun faixasDeEstado() {
        fun estadoComSemResponsavel(n: Int): String {
            val abertas = (1..100).map { aberta("TG-$it", resp = if (it <= n) null else "ana") }
            return verificar(abertas).getValue(VerificacaoQD.SEM_RESPONSAVEL).estado
        }
        assertEquals("ok", estadoComSemResponsavel(0))
        assertEquals("ok", estadoComSemResponsavel(10), "10% ainda é ok")
        assertEquals("atencao", estadoComSemResponsavel(11))
        assertEquals("atencao", estadoComSemResponsavel(30), "30% ainda é atenção")
        assertEquals("problema", estadoComSemResponsavel(31))
        assertEquals("ok", QualidadeDosDados.estadoDaFracao(null), "sem universo não há problema a apontar")
    }

    @Test
    fun asSeteVerificacoesDasAbertas() {
        val abertas = listOf(
            aberta("TG-1", estimateS = null), // sem estimativa
            aberta("TG-2", vence = null), // sem vencimento
            aberta("TG-3", prioridade = "Medium"), // prioridade padrão (sem diferenciar maiúsculas)
            aberta("TG-4", resp = "fulano.fora"), // responsável fora do papeis.yaml
            aberta("TG-5", status = "In Progress"), // em andamento sem horas
            aberta("TG-6", status = "In Progress").copy(spentS = 7200L), // em andamento com horas: ok
            aberta("TG-7").copy(epicKey = null), // sem épico
            aberta("TG-8", prioridade = "medium"),
        )
        val v = verificar(abertas)
        assertEquals(1, v.getValue(VerificacaoQD.SEM_ESTIMATIVA).n)
        assertEquals(1, v.getValue(VerificacaoQD.SEM_VENCIMENTO).n)
        assertEquals(2, v.getValue(VerificacaoQD.PRIORIDADE_PADRAO).n)
        assertEquals(1, v.getValue(VerificacaoQD.SEM_SQUAD).n)
        assertEquals(1, v.getValue(VerificacaoQD.SEM_EPICO).n)
        val andamento = v.getValue(VerificacaoQD.EM_ANDAMENTO_SEM_HORAS)
        assertEquals(1, andamento.n)
        assertEquals(2, andamento.universo, "o universo é só quem está em andamento")
        assertEquals(8, v.getValue(VerificacaoQD.SEM_RESPONSAVEL).universo)
    }

    @Test
    fun doneSemResolutionEResolutionSemDone() {
        val recentes = listOf(
            SnapDeTeste.snap("TG-10", status = "Closed", resolution = "Done", resolvida = "2026-10-12T12:00:00Z"),
            SnapDeTeste.snap("TG-11", status = "Closed", resolution = null, resolvida = "2026-10-12T12:00:00Z"), // DONE sem resolution
            SnapDeTeste.snap("TG-12", status = "In Progress", resolution = "Done", resolvida = "2026-10-20T12:00:00Z"), // resolution sem DONE
            SnapDeTeste.snap("TG-13", status = "Closed", resolution = "  ", resolvida = "2026-10-13T12:00:00Z"), // resolution em branco
        )
        val v = verificar(emptyList(), recentes)
        val d = v.getValue(VerificacaoQD.DONE_SEM_RESOLUTION)
        assertEquals(2, d.n)
        assertEquals(3, d.universo, "o universo é quem está em status concluído")
        val r = v.getValue(VerificacaoQD.RESOLUTION_SEM_DONE)
        assertEquals(1, r.n)
        assertEquals(2, r.universo, "universo = quem tem resolution preenchida (em branco não conta)")
    }

    @Test
    fun statusSemMapeamentoComNomeSemDerrubarAsOutras() {
        val abertas = listOf(aberta("TG-1"), aberta("TG-2", status = "Weird"), aberta("TG-3", status = "Weird"), aberta("TG-4", status = "Estranho"))
        val v = verificar(abertas)
        val l = v.getValue(VerificacaoQD.STATUS_SEM_MAPEAMENTO)
        assertEquals(3, l.n)
        assertEquals(listOf("Estranho", "Weird"), l.nomes)
        assertEquals("problema", l.estado)
        // As outras contas ignoram as issues de status desconhecido (universo 1), sem exceção.
        assertEquals(1, v.getValue(VerificacaoQD.SEM_RESPONSAVEL).universo)
        assertEquals("ok", verificar(listOf(aberta("TG-1"))).getValue(VerificacaoQD.STATUS_SEM_MAPEAMENTO).estado)
    }

    @Test
    fun campoIndisponivelComONomeEEpicoIndisponivel() {
        val v = verificar(listOf(aberta("TG-1")), campos = todosOsCampos + mapOf("Epic Link" to false, "Flagged" to false))
        val c = v.getValue(VerificacaoQD.CAMPO_INDISPONIVEL)
        assertEquals(listOf("Epic Link", "Flagged"), c.nomes)
        assertEquals(2, c.n)
        assertEquals(4, c.universo)
        assertEquals("atencao", c.estado)
        // Sem o campo de épico, "sem épico" não vira 100% de problema: fica indisponível.
        val e = v.getValue(VerificacaoQD.SEM_EPICO)
        assertEquals("indisponivel", e.estado)
        assertNull(e.fracao)
        assertEquals("ok", verificar(listOf(aberta("TG-1"))).getValue(VerificacaoQD.CAMPO_INDISPONIVEL).estado)
    }

    @Test
    fun labelSemClassificacaoNaoDiferenciaMaiusculasEEhSoInformativa() {
        val abertas = listOf(
            aberta("TG-1", labels = listOf("MYSTERY", "ONHOLDBY-PL", "skill:android")),
            aberta("TG-2", labels = listOf("mystery", "Outra", " ")),
            aberta("TG-3", labels = listOf("late_sent")),
            aberta("TG-4"),
        )
        val a = QualidadeDosDados.analisar(abertas, emptyList(), map, roles, hoje, "Medium", todosOsCampos, labels, emptyList())
        val l = a.linhas.first { it.verificacao == VerificacaoQD.LABEL_SEM_CLASSIFICACAO }
        assertEquals(listOf("MYSTERY", "Outra"), l.nomes, "grafia da primeira ocorrência, em ordem alfabética")
        assertEquals(2, l.n)
        assertEquals(3, l.universo, "o universo é quem tem algum label")
        assertEquals("neutro", l.estado, "informativa: nunca vira problema")
        assertEquals(mapOf("MYSTERY" to 2, "Outra" to 1), a.tasksPorLabel.mapValues { it.value.size })
        // Sem nenhum label desconhecido, ok; sem o vocabulário carregado, indisponível.
        assertEquals("ok", verificar(listOf(aberta("TG-1", labels = listOf("skill:rede")))).getValue(VerificacaoQD.LABEL_SEM_CLASSIFICACAO).estado)
        val sem = QualidadeDosDados.verificar(abertas, emptyList(), map, roles, hoje, "Medium", todosOsCampos, null, emptyList())
        assertEquals("indisponivel", sem.first { it.verificacao == VerificacaoQD.LABEL_SEM_CLASSIFICACAO }.estado)
    }

    @Test
    fun bloqueadasSemCausaVemDoBloqueios() {
        val bloqueada = aberta("TG-1", status = "On Hold")
        val v = verificar(listOf(bloqueada, aberta("TG-2", status = "On Hold"), aberta("TG-3")), bloqueadas = listOf(bloqueada))
        val l = v.getValue(VerificacaoQD.BLOQUEADA_SEM_CAUSA)
        assertEquals(1, l.n)
        assertEquals(2, l.universo, "o universo são as bloqueadas por status ou flag")
        assertEquals("problema", l.estado)
        // Sem a conta do Bloqueios (ela depende de status mapeado), a linha fica indisponível.
        assertEquals("indisponivel", verificar(listOf(bloqueada), bloqueadas = null).getValue(VerificacaoQD.BLOQUEADA_SEM_CAUSA).estado)
        assertTrue(verificar(listOf(aberta("TG-3"))).getValue(VerificacaoQD.BLOQUEADA_SEM_CAUSA).n == 0)
    }
}
