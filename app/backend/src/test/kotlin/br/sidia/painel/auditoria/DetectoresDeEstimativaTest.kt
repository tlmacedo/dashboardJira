package br.sidia.painel.auditoria

import br.sidia.painel.cards.AvisoDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sinal ESTIMATIVA_REDUZIDA (cenário AUD-1: TG-10 nasce em Open, responsável inicial `ana`, reporter `pl.alfa`). O limite padrão é
 * 20% (`estimativaReducaoMinima`) e só vale DEPOIS da primeira entrada em andamento: antes disso é planejamento.
 */
class DetectoresDeEstimativaTest {
    private val c = CenarioAud1()

    private val inicio = c.ev("status", "Open", "In Progress", "2026-10-05T12:00:00Z", "ana")

    private fun detectar(vararg eventos: br.sidia.painel.sync.ChangeEventRecord) =
        c.detectar(DetectoresDeEstimativa.todos, eventos.toList())

    private fun reduz(de: String?, para: String?, at: String, autor: String? = "ana") =
        c.ev("timeoriginalestimate", de, para, at, autor)

    @Test
    fun reducaoDepoisDeIniciarGera() {
        val s = detectar(inicio, reduz("36000", "21600", "2026-10-07T12:00:00Z")).single()
        assertEquals(CodigoSinal.ESTIMATIVA_REDUZIDA, s.codigo)
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals("10.0", s.de)
        assertEquals("6.0", s.para)
        assertEquals("timeoriginalestimate", s.campo)
        assertEquals("ana", s.autor)
        assertEquals(MotivoAutorizacao.PROPRIO_RESPONSAVEL, s.motivo)
        assertEquals(false, s.autorizado)
        assertEquals(2, s.gravidade)
    }

    @Test
    fun reducaoAntesDeIniciarNaoGera() {
        // Replanejamento legítimo: a task ainda está em Open.
        assertTrue(detectar(reduz("36000", "21600", "2026-10-03T12:00:00Z"), inicio).isEmpty())
        // Sem nenhuma entrada em andamento também não.
        assertTrue(detectar(reduz("36000", "21600", "2026-10-07T12:00:00Z")).isEmpty())
    }

    @Test
    fun reducaoPequenaNaoGera() {
        assertTrue(detectar(inicio, reduz("36000", "32400", "2026-10-07T12:00:00Z")).isEmpty(), "10% fica abaixo de 20%")
    }

    @Test
    fun exatamenteNoLimiteGera() {
        // 36000 -> 28800 = 20% exato: o limite é inclusivo.
        assertEquals(1, detectar(inicio, reduz("36000", "28800", "2026-10-07T12:00:00Z")).size)
        assertTrue(detectar(inicio, reduz("36000", "28801", "2026-10-07T12:00:00Z")).isEmpty())
    }

    @Test
    fun reducaoPeloReporterViraAlerta() {
        val s = detectar(inicio, reduz("36000", "21600", "2026-10-07T12:00:00Z", "pl.alfa")).single()
        assertEquals(Resultado.ALERTA, s.resultado)
        assertEquals(MotivoAutorizacao.REPORTER, s.motivo)
        assertEquals(true, s.autorizado)
        // Coordenação e líder do squad do responsável também são autorizados.
        for (autor in listOf("coord.um", "lider.alfa2")) {
            assertEquals(Resultado.ALERTA, detectar(inicio, reduz("36000", "21600", "2026-10-12T12:00:00Z", autor)).single().resultado, autor)
        }
    }

    @Test
    fun autorDesconhecidoOuDeFora() {
        assertEquals(Resultado.AUDITORIA, detectar(inicio, reduz("36000", "21600", "2026-10-07T12:00:00Z", null)).single().resultado)
        assertEquals(Resultado.AUDITORIA, detectar(inicio, reduz("36000", "21600", "2026-10-07T12:00:00Z", "carla")).single().resultado)
    }

    @Test
    fun aumentoNaoGera() {
        // Aumento é da Integridade (ESTIMATIVA_AUMENTADA), não da Auditoria.
        assertTrue(detectar(inicio, reduz("21600", "36000", "2026-10-07T12:00:00Z")).isEmpty())
        assertTrue(detectar(inicio, reduz("36000", "36000", "2026-10-07T12:00:00Z")).isEmpty())
    }

    @Test
    fun semValorNaoGera() {
        assertTrue(detectar(inicio, reduz(null, null, "2026-10-07T12:00:00Z")).isEmpty())
        assertTrue(detectar(inicio, reduz("36000", null, "2026-10-07T12:00:00Z")).isEmpty(), "remover a estimativa não é redução")
        assertTrue(detectar(inicio, reduz(null, "100", "2026-10-07T12:00:00Z")).isEmpty(), "primeira estimativa")
        assertTrue(detectar(inicio, reduz("0", "0", "2026-10-07T12:00:00Z")).isEmpty(), "zero não divide")
        assertTrue(detectar(inicio, reduz("abc", "10", "2026-10-07T12:00:00Z")).isEmpty())
    }

    @Test
    fun soTimeOriginalEstimateConta() {
        assertTrue(detectar(inicio, c.ev("timeestimate", "36000", "3600", "2026-10-07T12:00:00Z", "ana")).isEmpty(), "o restante cai normalmente")
    }

    @Test
    fun limiteVemDasRegras() {
        val rigido = c.ctx.copy(regras = c.regras.copy(estimativaReducaoMinima = 0.05))
        val t = c.timeline(listOf(inicio, reduz("36000", "34200", "2026-10-07T12:00:00Z"))) // 5% exato
        assertEquals(1, DetectoresDeEstimativa.todos.flatMap { it.detectar(t, rigido) }.size)
        val desligado = c.ctx.copy(regras = c.regras.copy(sinais = c.regras.sinais + (CodigoSinal.ESTIMATIVA_REDUZIDA to RegraSinal(2, ativo = false))))
        assertTrue(DetectoresDeEstimativa.todos.flatMap { it.detectar(t, desligado) }.isEmpty())
    }

    @Test
    fun iniciadaDesdeACriacaoContaDesdeACriacao() {
        // Sem transições e já em andamento: o trabalho começou na criação.
        val andamento = CenarioAud1().timeline(listOf(reduz("36000", "21600", "2026-10-07T12:00:00Z")))
        val emAndamento = andamento.issue.copy(status = "In Progress")
        val t = AuditTimeline(emAndamento, c.statusMap)
        assertEquals(1, DetectoresDeEstimativa.todos.flatMap { it.detectar(t, c.ctx) }.size)
    }

    @Test
    fun historicoIncompletoContaEventosSemValor() {
        val t = c.timeline(
            listOf(
                inicio, reduz(null, null, "2026-10-06T12:00:00Z"), reduz(null, null, "2026-10-07T12:00:00Z"),
                reduz("36000", "21600", "2026-10-08T12:00:00Z"), c.ev("status", "In Progress", "Done", "2026-10-09T12:00:00Z", "ana"),
            ),
        )
        assertEquals(2, DetectoresDeEstimativa.eventosSemValor(t))
        assertEquals("HISTORICO_DE_ESTIMATIVA_INCOMPLETO", AvisoDto.HISTORICO_DE_ESTIMATIVA_INCOMPLETO)
    }
}
