package br.sidia.painel.auditoria

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sinais de vencimento alterado e removido (cenário AUD-1, vencimento 2026-10-15). */
class DetectoresDeVencimentoTest {
    private val c = CenarioAud1()

    private fun detectar(vararg eventos: br.sidia.painel.sync.ChangeEventRecord) =
        c.detectar(DetectoresDeVencimento.todos, eventos.toList())

    @Test
    fun alteradoPeloResponsavelGeraAuditoria() {
        val s = detectar(c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "ana")).single()
        assertEquals(CodigoSinal.VENCIMENTO_ALTERADO_NAO_AUTORIZADO, s.codigo)
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(MotivoAutorizacao.PROPRIO_RESPONSAVEL, s.motivo)
        assertEquals(false, s.autorizado)
        assertEquals("2026-10-15", s.de)
        assertEquals("2026-10-20", s.para)
        assertEquals(3, s.gravidade)
        assertEquals("TG-10", s.issueKey)
        assertEquals("duedate", s.campo)
        assertEquals("ana", s.autor)
        assertEquals("ana", s.responsavel)
    }

    @Test
    fun alteradoPeloLiderVigenteNaoGera() {
        assertTrue(detectar(c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "lider.alfa2")).isEmpty())
    }

    @Test
    fun alteradoPorCarlaGera() {
        val s = detectar(c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "carla")).single()
        assertEquals(MotivoAutorizacao.NAO_AUTORIZADO, s.motivo)
    }

    @Test
    fun definidoPelaPrimeiraVezNaoGera() {
        assertTrue(detectar(c.ev("duedate", null, "2026-10-15", "2026-10-02T12:00:00Z", "ana")).isEmpty())
    }

    @Test
    fun valorIlegivelNaoGeraAlteracao() {
        assertTrue(detectar(c.ev("duedate", "15/10", "2026-10-20", "2026-10-10T12:00:00Z", "ana")).isEmpty())
    }

    @Test
    fun alteracaoParaOMesmoValorNaoGera() {
        assertTrue(detectar(c.ev("duedate", "2026-10-15", "2026-10-15 00:00:00.0", "2026-10-10T12:00:00Z", "ana")).isEmpty())
    }

    @Test
    fun removidoGeraMesmoPorCoordenador() {
        val s = detectar(c.ev("duedate", "2026-10-15", null, "2026-10-10T12:00:00Z", "coord.um")).single()
        assertEquals(CodigoSinal.VENCIMENTO_REMOVIDO, s.codigo)
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(true, s.autorizado)
        assertEquals(MotivoAutorizacao.COORDENACAO, s.motivo)
        assertEquals("2026-10-15", s.de)
        assertNull(s.para)
        assertEquals(3, s.gravidade)
    }

    @Test
    fun autorJirauserNaoResolvidoAparece() {
        val s = detectar(c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "JIRAUSER99")).single()
        assertEquals("JIRAUSER99", s.autor)
        assertEquals(MotivoAutorizacao.NAO_AUTORIZADO, s.motivo)
    }

    @Test
    fun autorAusenteAparece() {
        val s = detectar(c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", null)).single()
        assertNull(s.autor)
        assertEquals(MotivoAutorizacao.AUTOR_DESCONHECIDO, s.motivo)
    }

    @Test
    fun regraInativaNaoGera() {
        val inativas = c.regras.copy(sinais = c.regras.sinais.mapValues { it.value.copy(ativo = false) })
        val eventos = listOf(
            c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "ana"),
            c.ev("duedate", "2026-10-20", null, "2026-10-11T12:00:00Z", "ana"),
        )
        assertTrue(c.detectar(DetectoresDeVencimento.todos, eventos, contexto = c.comRegras(inativas)).isEmpty())
    }

    @Test
    fun usaOResponsavelNoInstante() {
        // ana vira bruno em 05/10; em 10/10 o vencimento é mexido por bruno: é o próprio responsável
        val s = detectar(
            c.ev("assignee", "ana", "bruno", "2026-10-05T12:00:00Z"),
            c.ev("duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "bruno"),
        ).single()
        assertEquals("bruno", s.responsavel)
        assertEquals(MotivoAutorizacao.PROPRIO_RESPONSAVEL, s.motivo)
    }
}
