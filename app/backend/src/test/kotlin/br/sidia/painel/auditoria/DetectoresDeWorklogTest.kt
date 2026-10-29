package br.sidia.painel.auditoria

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sinal WORKLOG_RETROATIVO (cenário AUD-1, issue TG-10 nascida em 01/10/2026): horas registradas 2 dias úteis de Manaus ou mais
 * depois do início declarado, ou depois da resolução. Sempre auditoria; um sinal por worklog.
 */
class DetectoresDeWorklogTest {
    private val c = CenarioAud1()
    private val zona = ZoneId.of("America/Manaus")

    private fun w(id: String, started: String, criado: String?, autor: String? = "ana", s: Long = 3600) =
        WorklogRecord(id, autor, Instant.parse(started), s, criado?.let(Instant::parse))

    private fun timeline(vararg ws: WorklogRecord, resolved: String? = null) = AuditTimeline(
        c.timeline(emptyList()).issue.copy(worklogs = ws.toList(), resolved = resolved?.let(Instant::parse)),
        c.statusMap,
    )

    private fun detectar(t: AuditTimeline, ctx: ContextoDeteccao = c.ctx) = DetectoresDeWorklog.todos.flatMap { it.detectar(t, ctx) }

    @Test
    fun registradoDoisDiasUteisDepoisGera() {
        // Início quinta 15/10 (09:00 em Manaus), registro segunda 19/10: sexta e segunda = 2 dias úteis.
        val s = detectar(timeline(w("9", "2026-10-15T13:00:00Z", "2026-10-19T15:00:00Z"))).single()
        assertEquals(CodigoSinal.WORKLOG_RETROATIVO, s.codigo)
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals("worklog", s.campo)
        assertEquals("2026-10-15", s.de)
        assertEquals("2026-10-19", s.para)
        assertEquals(Instant.parse("2026-10-19T15:00:00Z"), s.at)
        assertEquals("ana", s.autor)
        assertEquals("worklog:9", s.changelogId)
        assertEquals("TG-10", s.issueKey)
        assertEquals(2, s.gravidade)
        assertEquals(MotivoAutorizacao.PROPRIO_RESPONSAVEL, s.motivo)
        assertEquals(false, s.autorizado)
    }

    @Test
    fun umDiaUtilNaoGera() {
        assertTrue(detectar(timeline(w("1", "2026-10-15T13:00:00Z", "2026-10-16T15:00:00Z"))).isEmpty())
        assertTrue(detectar(timeline(w("2", "2026-10-15T13:00:00Z", "2026-10-15T20:00:00Z"))).isEmpty(), "no mesmo dia")
    }

    @Test
    fun fimDeSemanaNaoConta() {
        // Sexta 16/10 -> segunda 19/10: o sábado e o domingo não contam, só a segunda (1 dia útil).
        assertTrue(detectar(timeline(w("1", "2026-10-16T13:00:00Z", "2026-10-19T15:00:00Z"))).isEmpty())
        // Sexta -> terça: segunda e terça = 2.
        assertEquals(1, detectar(timeline(w("2", "2026-10-16T13:00:00Z", "2026-10-20T15:00:00Z"))).size)
    }

    @Test
    fun feriadoNaoConta() {
        val comFeriado = c.ctx.copy(calendario = BusinessCalendar(zona, movableHolidays = setOf(LocalDate.parse("2026-10-12"))))
        // Sexta 09/10 -> terça 13/10: com a segunda 12/10 feriado só a terça conta (1); sem feriado seriam 2.
        val registro = w("1", "2026-10-09T13:00:00Z", "2026-10-13T15:00:00Z")
        assertTrue(detectar(timeline(registro), comFeriado).isEmpty())
        assertEquals(1, detectar(timeline(registro)).size)
    }

    @Test
    fun registradoDepoisDaResolucaoGera() {
        // Resolvida 14/10 às 12:00Z; início antes da resolução e registro no dia seguinte (1 dia útil só): gera pela regra 2.
        val antes = w("1", "2026-10-14T10:00:00Z", "2026-10-15T15:00:00Z")
        assertEquals(1, detectar(timeline(antes, resolved = "2026-10-14T12:00:00Z")).size)
        // Início DEPOIS da resolução não é retroativo por esta regra (e 1 dia útil não basta).
        val depois = w("2", "2026-10-14T13:00:00Z", "2026-10-15T15:00:00Z")
        assertTrue(detectar(timeline(depois, resolved = "2026-10-14T12:00:00Z")).isEmpty())
        // Registro antes da resolução também não.
        val naoResolvida = w("3", "2026-10-14T10:00:00Z", "2026-10-14T11:00:00Z")
        assertTrue(detectar(timeline(naoResolvida, resolved = "2026-10-14T12:00:00Z")).isEmpty())
        // Sem resolução, a regra 2 não existe.
        assertTrue(detectar(timeline(antes)).isEmpty())
    }

    @Test
    fun fronteiraDeManaus() {
        // 03:30Z de 20/10 ainda é 19/10 em Manaus (23:30): de sexta 16/10 só a segunda conta (1 dia útil) e não gera.
        val naFronteira = w("1", "2026-10-16T13:00:00Z", "2026-10-20T03:30:00Z")
        assertTrue(detectar(timeline(naFronteira)).isEmpty())
        // 04:30Z já é 20/10 00:30 em Manaus: segunda e terça = 2 dias úteis, e o dia gravado é o de Manaus.
        val s = detectar(timeline(w("2", "2026-10-16T13:00:00Z", "2026-10-20T04:30:00Z"))).single()
        assertEquals("2026-10-20", s.para)
        // O início também é o dia de Manaus: 02:00Z de 16/10 é 15/10 22:00 lá.
        assertEquals("2026-10-15", detectar(timeline(w("3", "2026-10-16T02:00:00Z", "2026-10-19T15:00:00Z"))).single().de)
    }

    @Test
    fun semCriacaoNaoGeraEAvisa() {
        val t = timeline(w("1", "2026-10-01T13:00:00Z", null), w("2", "2026-10-02T13:00:00Z", null), w("3", "2026-10-15T13:00:00Z", "2026-10-19T15:00:00Z"))
        assertEquals(1, detectar(t).size)
        assertEquals(2, DetectoresDeWorklog.semCriacao(t))
        assertEquals("HISTORICO_DE_WORKLOG_INCOMPLETO", AvisoDto.HISTORICO_DE_WORKLOG_INCOMPLETO)
    }

    @Test
    fun umSinalPorWorklogEQualquerAutor() {
        val t = timeline(
            w("1", "2026-10-15T13:00:00Z", "2026-10-19T15:00:00Z", "carla"),
            w("2", "2026-10-15T14:00:00Z", "2026-10-19T16:00:00Z", null),
            w("3", "2026-10-15T14:00:00Z", "2026-10-19T16:30:00Z", "coord.um"),
        )
        val s = detectar(t)
        assertEquals(listOf("worklog:1", "worklog:2", "worklog:3"), s.map { it.changelogId })
        assertTrue(s.all { it.resultado == Resultado.AUDITORIA }, "sempre auditoria, mesmo de quem podia alterar")
        assertEquals(MotivoAutorizacao.AUTOR_DESCONHECIDO, s[1].motivo)
        assertNull(s[1].autor)
        assertEquals(MotivoAutorizacao.COORDENACAO, s[2].motivo)
    }

    @Test
    fun limiteVemDasRegrasEOSinalPodeSerDesligado() {
        val umDia = timeline(w("1", "2026-10-15T13:00:00Z", "2026-10-16T15:00:00Z"))
        val estrito = c.ctx.copy(regras = c.regras.copy(worklogRetroativoDiasUteis = 1))
        assertEquals(1, detectar(umDia, estrito).size)
        val desligado = c.ctx.copy(regras = c.regras.copy(sinais = c.regras.sinais + (CodigoSinal.WORKLOG_RETROATIVO to RegraSinal(2, ativo = false))))
        assertTrue(detectar(timeline(w("1", "2026-10-15T13:00:00Z", "2026-10-19T15:00:00Z")), desligado).isEmpty())
    }
}
