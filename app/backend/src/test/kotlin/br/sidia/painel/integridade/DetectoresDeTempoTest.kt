package br.sidia.painel.integridade

import br.sidia.painel.integridade.Int1.fechada
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste.ev
import br.sidia.painel.trabalho.SnapDeTeste.snap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sinais de tempo da Integridade no cenário INT-1: limites exatos, pessoa do sinal = autor do evento e fuso de Manaus. */
class DetectoresDeTempoTest {
    private fun detectar(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade = Int1.regras): List<EvidenciaIntegridade> =
        DetectoresDeTempo.todos.flatMap { it.detectar(issues, regras, Int1.ctx) }

    private fun de(sinal: SinalIntegridade, issues: List<IssueSnapshot>, regras: RegrasDeIntegridade = Int1.regras) =
        detectar(issues, regras).filter { it.sinal == sinal }

    // ---- TROCA_ANTES_DE_FECHAR ----

    private fun comTroca(key: String, trocaEm: String, fimEm: String = "2026-10-14T12:00:00Z", autorFim: String? = "bruno", de: String? = "ana", para: String = "bruno") =
        fechada(key, resp = para, fim = fimEm, autorFim = autorFim, extras = listOf(ev(key, "assignee", de, para, trocaEm, "bruno")))

    @Test
    fun trocaAntesDeFechar() {
        // 1 h 59 min antes do fechamento: sinal; 2 h 01 min: não. Exatamente 2 h é o limite inclusivo.
        val dentro = comTroca("TG-501", "2026-10-14T10:01:00Z")
        val limite = comTroca("TG-502", "2026-10-14T10:00:00Z")
        val fora = comTroca("TG-503", "2026-10-14T09:59:00Z")
        val evidencias = de(SinalIntegridade.TROCA_ANTES_DE_FECHAR, listOf(dentro, limite, fora))
        assertEquals(listOf("TG-501", "TG-502"), evidencias.map { it.issueKey }.sorted())
        val e = evidencias.first { it.issueKey == "TG-501" }
        assertEquals("bruno", e.pessoa, "a pessoa é o autor do evento da troca")
        assertEquals(3, e.gravidade)
        assertEquals(i("2026-10-14T10:01:00Z"), e.at, "o instante é o da troca")
        assertEquals("bruno", e.detalhe["responsavel"])
        assertEquals("119", e.detalhe["minutosAntesDeFechar"])
    }

    @Test
    fun trocaSemApropriacaoNaoGeraSinal() {
        // Quem fechou não é o novo responsável: é uma passagem de bastão normal.
        assertEquals(emptyList(), de(SinalIntegridade.TROCA_ANTES_DE_FECHAR, listOf(comTroca("TG-504", "2026-10-14T11:00:00Z", autorFim = "carla"))))
        // Primeira atribuição (sem responsável antes) não é troca.
        assertEquals(emptyList(), de(SinalIntegridade.TROCA_ANTES_DE_FECHAR, listOf(comTroca("TG-505", "2026-10-14T11:00:00Z", de = null))))
        // Fechamento de autor desconhecido não permite afirmar quem ficou com o crédito.
        assertEquals(emptyList(), de(SinalIntegridade.TROCA_ANTES_DE_FECHAR, listOf(comTroca("TG-506", "2026-10-14T11:00:00Z", autorFim = null))))
    }

    private fun i(s: String) = Int1.i(s)

    // ---- CRIADA_E_FECHADA_RAPIDO ----

    @Test
    fun criadaEFechadaRapido() {
        val rapida = fechada("TG-511", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:14:00Z", autorFim = "bruno")
        val limite = fechada("TG-512", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:15:00Z", autorFim = "bruno")
        val evidencias = de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(rapida, limite))
        assertEquals(listOf("TG-511"), evidencias.map { it.issueKey }, "14 min dispara; 15 min não")
        val e = evidencias.single()
        assertEquals("bruno", e.pessoa)
        assertEquals(3, e.gravidade)
        assertEquals("14", e.detalhe["minutos"])
        assertEquals(i("2026-10-14T10:14:00Z"), e.at)
    }

    @Test
    fun semAutorDoEventoUsaOResponsavelNoInstante() {
        val s = fechada("TG-513", resp = "ana", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:05:00Z", autorFim = null)
        assertEquals("ana", de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(s)).single().pessoa)
    }

    @Test
    fun canceladaNaoGeraSinalDeFechamento() {
        val cancelada = fechada("TG-514", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:05:00Z", resolution = "Won't Do")
        assertEquals(emptyList(), detectar(listOf(cancelada)))
    }

    // ---- REABRE_E_FECHA_RAPIDO ----

    private fun reaberta(key: String, fechou: String, reabriu: String, fechouDeNovo: String) = fechada(
        key, criada = "2026-10-01T12:00:00Z", inicio = "2026-10-02T12:00:00Z", fim = fechouDeNovo, autorFim = "bruno",
        extras = listOf(
            ev(key, "status", "In Progress", "Closed", fechou, "ana"),
            ev(key, "status", "Closed", "In Progress", reabriu, "carla"),
        ),
    )

    @Test
    fun reabreEFechaRapido() {
        // Fechada 09:00, reaberta 10:00 e fechada de novo às 11:59 (menos de 2 h): sinal; às 12:00 já não.
        val dentro = reaberta("TG-521", "2026-10-14T09:00:00Z", "2026-10-14T10:00:00Z", "2026-10-14T11:59:00Z")
        val fora = reaberta("TG-522", "2026-10-14T09:00:00Z", "2026-10-14T10:00:00Z", "2026-10-14T12:00:00Z")
        val evidencias = de(SinalIntegridade.REABRE_E_FECHA_RAPIDO, listOf(dentro, fora))
        assertEquals(listOf("TG-521"), evidencias.map { it.issueKey })
        val e = evidencias.single()
        assertEquals("bruno", e.pessoa, "a pessoa é o autor do segundo fechamento")
        assertEquals(i("2026-10-14T11:59:00Z"), e.at)
        assertEquals(3, e.gravidade)
    }

    @Test
    fun reaberturaTardiaNaoEhRenovacaoDeData() {
        // Reaberta 3 h depois de fechada (e refechada em 1 h): é uma devolução normal, não renovação da data de resolução.
        val tardia = reaberta("TG-523", "2026-10-14T07:00:00Z", "2026-10-14T10:00:00Z", "2026-10-14T11:00:00Z")
        assertEquals(emptyList(), de(SinalIntegridade.REABRE_E_FECHA_RAPIDO, listOf(tardia)))
    }

    // ---- FECHAMENTO_EM_LOTE ----

    private fun lote(prefixo: String, autor: String, inicio: String, n: Int, passoMin: Long): List<IssueSnapshot> {
        val t0 = i(inicio)
        return (1..n).map { k ->
            fechada("$prefixo-$k", resp = "ana", criada = "2026-10-01T12:00:00Z", fim = t0.plusSeconds((k - 1) * passoMin * 60).toString(), autorFim = autor)
        }
    }

    @Test
    fun fechamentoEmLote() {
        // bruno fecha 6 issues entre 14:00 e 14:09: 6 evidências; com 5, nenhuma.
        val seis = lote("TG-6", "bruno", "2026-10-14T14:00:00Z", 6, 1).take(5) + fechada("TG-69", fim = "2026-10-14T14:09:00Z", autorFim = "bruno")
        val ev6 = de(SinalIntegridade.FECHAMENTO_EM_LOTE, seis)
        assertEquals(6, ev6.size)
        assertTrue(ev6.all { it.pessoa == "bruno" && it.gravidade == 3 && it.detalhe["fechadas"] == "6" })
        assertEquals(emptyList(), de(SinalIntegridade.FECHAMENTO_EM_LOTE, lote("TG-7", "bruno", "2026-10-14T14:00:00Z", 5, 1)))
    }

    @Test
    fun loteNaoMisturaPessoasNemJanelasLongas() {
        // 3 do bruno e 3 da carla na mesma janela: nenhum dos dois chega a 6.
        val misto = lote("TG-8", "bruno", "2026-10-14T14:00:00Z", 3, 1) + lote("TG-9", "carla", "2026-10-14T14:00:30Z", 3, 1)
        assertEquals(emptyList(), de(SinalIntegridade.FECHAMENTO_EM_LOTE, misto))
        // 6 fechamentos do bruno espaçados de 3 em 3 min (15 min no total): a janela de 10 min não junta seis.
        assertEquals(emptyList(), de(SinalIntegridade.FECHAMENTO_EM_LOTE, lote("TG-10", "bruno", "2026-10-14T14:00:00Z", 6, 3)))
    }

    @Test
    fun loteCruzandoMeiaNoite() {
        // 23:55 a 00:04 de Manaus (03:55Z a 04:04Z): é um lote só, mesmo atravessando o dia.
        val t = i("2026-10-15T03:55:00Z")
        val issues = listOf(0L, 2L, 4L, 6L, 8L, 9L).mapIndexed { k, min ->
            fechada("TG-11-$k", fim = t.plusSeconds(min * 60).toString(), autorFim = "bruno")
        }
        val evidencias = de(SinalIntegridade.FECHAMENTO_EM_LOTE, issues)
        assertEquals(6, evidencias.size)
        // Os fechamentos caem em dois dias de Manaus (14/10 e 15/10).
        assertEquals(setOf(java.time.LocalDate.of(2026, 10, 14), java.time.LocalDate.of(2026, 10, 15)), evidencias.map { it.at.atZone(Int1.ctx.zone).toLocalDate() }.toSet())
    }

    // ---- FECHADA_POR_OUTRO ----

    @Test
    fun fechadaPorOutro() {
        val porOutro = fechada("TG-531", resp = "ana", autorFim = "carla")
        val peloDono = fechada("TG-532", resp = "ana", autorFim = "ana")
        val semAutor = fechada("TG-533", resp = "ana", autorFim = null)
        val semResponsavel = fechada("TG-534", resp = null, autorFim = "carla")
        val evidencias = de(SinalIntegridade.FECHADA_POR_OUTRO, listOf(porOutro, peloDono, semAutor, semResponsavel))
        assertEquals(listOf("TG-531"), evidencias.map { it.issueKey })
        val e = evidencias.single()
        assertEquals("carla", e.pessoa, "a pessoa é o autor da transição")
        assertEquals("ana", e.detalhe["responsavel"], "o responsável sempre aparece no detalhe")
        assertEquals(1, e.gravidade)
    }

    @Test
    fun quemJaFoiResponsavelDaTaskNaoContaComoOutro() {
        // carla recebeu a task, devolveu para ana e depois a fechou: já foi responsável, como no legado (M1).
        val s = fechada(
            "TG-535", resp = "ana", autorFim = "carla",
            extras = listOf(
                ev("TG-535", "assignee", "ana", "carla", "2026-10-05T12:00:00Z", "ana"),
                ev("TG-535", "assignee", "carla", "ana", "2026-10-06T12:00:00Z", "carla"),
            ),
        )
        assertEquals(emptyList(), de(SinalIntegridade.FECHADA_POR_OUTRO, listOf(s)))
    }

    // ---- FECHADA_NO_LIMITE ----

    @Test
    fun fechadaNoLimitePeloFusoDeManaus() {
        // Vencimento 15/10: 2026-10-16T02:30Z é 22:30 de 15/10 em Manaus (sinal); 01:59Z é 21:59 (não); 02:00Z é 22:00 (limite inclusivo).
        val tarde = fechada("TG-541", vence = "2026-10-15", fim = "2026-10-16T02:30:00Z", autorFim = "ana")
        val cedo = fechada("TG-542", vence = "2026-10-15", fim = "2026-10-16T01:59:00Z", autorFim = "ana")
        val limite = fechada("TG-543", vence = "2026-10-15", fim = "2026-10-16T02:00:00Z", autorFim = "ana")
        // Em UTC o fechamento cai em 16/10, mas em Manaus ainda é 15/10; um dia depois do vencimento não conta.
        val diaSeguinte = fechada("TG-544", vence = "2026-10-15", fim = "2026-10-17T02:30:00Z", autorFim = "ana")
        val evidencias = de(SinalIntegridade.FECHADA_NO_LIMITE, listOf(tarde, cedo, limite, diaSeguinte))
        assertEquals(listOf("TG-541", "TG-543"), evidencias.map { it.issueKey }.sorted())
        assertEquals("22", evidencias.first { it.issueKey == "TG-541" }.detalhe["hora"])
        assertEquals(1, evidencias.first().gravidade)
    }

    @Test
    fun fechadaNoLimiteUsaOVencimentoDaEpocaDoFechamento() {
        // O vencimento foi prorrogado DEPOIS do fechamento: vale o de 15/10 que estava vigente quando fechou.
        val prorrogadaDepois = fechada(
            "TG-545", vence = "2026-10-20", fim = "2026-10-16T02:30:00Z", autorFim = "ana",
            extras = listOf(ev("TG-545", "duedate", "2026-10-15", "2026-10-20", "2026-10-17T12:00:00Z", "pl.alfa")),
        )
        assertEquals(1, de(SinalIntegridade.FECHADA_NO_LIMITE, listOf(prorrogadaDepois)).size)
    }

    // ---- Regras ----

    @Test
    fun sinalInativoNaoGera() {
        val rapida = fechada("TG-551", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:05:00Z", autorFim = "bruno")
        assertEquals(1, de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(rapida)).size)
        assertEquals(emptyList(), de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(rapida), Int1.semOsSinais(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO)))
    }

    @Test
    fun gravidadeEOsLimitesVemDasRegras() {
        val rapida = fechada("TG-552", criada = "2026-10-14T10:00:00Z", inicio = null, fim = "2026-10-14T10:20:00Z", autorFim = "bruno")
        assertEquals(emptyList(), de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(rapida)))
        val regras = Int1.regras.copy(
            criadaEFechadaMinutos = 30, gravidades = Int1.regras.gravidades + (SinalIntegridade.CRIADA_E_FECHADA_RAPIDO to 1),
        )
        val e = de(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, listOf(rapida), regras).single()
        assertEquals(1, e.gravidade)
    }

    @Test
    fun taskAbertaNaoGeraSinalDeFechamento() {
        val aberta = snap("TG-553", "Checklist", "ana", "2026-10-14T10:00:00Z", "In Progress")
        assertEquals(emptyList(), detectar(listOf(aberta)))
    }
}
