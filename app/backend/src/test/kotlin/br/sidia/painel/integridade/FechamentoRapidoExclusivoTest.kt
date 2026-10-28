package br.sidia.painel.integridade

import br.sidia.painel.integridade.Int1.fechada
import br.sidia.painel.trabalho.IssueSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Um fechamento rápido gera UM sinal só, como no legado (`if life_min < 15 ... elif not started`): "criada e fechada em minutos" é o
 * mais específico e "pulou o fluxo" só vale para a task que não foi fechada em minutos. Sem isso, Open -> Done em 5 min somava 5 pontos.
 */
class FechamentoRapidoExclusivoTest {
    private fun todos(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade = Int1.regras): List<EvidenciaIntegridade> =
        (DetectoresDeTempo.todos + DetectoresDePrazoEFluxo.todos).flatMap { it.detectar(issues, regras, Int1.ctx) }

    private fun rapida(key: String, criada: String, fim: String, autor: String = "bruno") =
        fechada(key, resp = autor, inicio = null, criada = criada, fim = fim, autorFim = autor)

    @Test
    fun abertaEFechadaEmCincoMinutosGeraSoUmSinal() {
        val s = rapida("TG-641", "2026-10-14T12:00:00Z", "2026-10-14T12:05:00Z")
        val sinais = todos(listOf(s)).filter { it.issueKey == "TG-641" }.map { it.sinal }
        assertEquals(listOf(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO), sinais)
    }

    @Test
    fun taskLentaQuePulouOFluxoSegueComOSinalDoFluxo() {
        val s = rapida("TG-642", "2026-10-01T12:00:00Z", "2026-10-14T12:00:00Z")
        assertEquals(listOf(SinalIntegridade.PULOU_O_FLUXO), todos(listOf(s)).map { it.sinal })
    }

    @Test
    fun loteDeSeisRapidasSomaSeisPontosPorTaskEMantemOLote() {
        val seis = (0..5).map { rapida("TG-65$it", "2026-10-14T13:5${it}:00Z", "2026-10-14T14:0$it:00Z") }
        val porTask = todos(seis).groupBy { it.issueKey }
        assertEquals(6, porTask.size)
        for ((_, evs) in porTask) {
            assertEquals(setOf(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, SinalIntegridade.FECHAMENTO_EM_LOTE), evs.map { it.sinal }.toSet())
            assertEquals(6, evs.sumOf { it.gravidade }, "3 da criada e fechada + 3 do lote; sem o sinal do fluxo")
        }
    }

    @Test
    fun comOSinalRapidoDesligadoOFluxoVoltaAValer() {
        val regras = Int1.regras.copy(ativos = Int1.regras.ativos - SinalIntegridade.CRIADA_E_FECHADA_RAPIDO)
        val s = rapida("TG-643", "2026-10-14T12:00:00Z", "2026-10-14T12:05:00Z")
        assertEquals(listOf(SinalIntegridade.PULOU_O_FLUXO), todos(listOf(s), regras).map { it.sinal })
    }
}
