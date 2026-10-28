package br.sidia.painel.domain

import br.sidia.painel.auditoria.CenarioMulti
import br.sidia.painel.auditoria.CodigoSinal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "No prazo" (resolvida até o vencimento, dia de Manaus) e "no prazo ajustado" (também com label que justifica). */
class NoPrazoTest {
    private val m = CenarioMulti()
    private val zona = ZoneId.of("America/Manaus")
    private val de = LocalDate.parse("2026-10-01")
    private val ate = LocalDate.parse("2026-10-31")

    private fun feita(key: String, resolved: String, duedate: String = "2026-10-15", labels: List<String> = emptyList(), resolution: String? = "Done") =
        m.issue(key, status = "Done", resolution = resolution, resolved = resolved, duedate = duedate, labels = labels)

    private val cenario = listOf(
        feita("TG-21", "2026-10-14T12:00:00Z"),
        // 15/10 23:30 em Manaus (16/10 03:30Z): ainda é o dia do vencimento.
        feita("TG-22", "2026-10-16T03:30:00Z"),
        feita("TG-23", "2026-10-17T12:00:00Z", labels = listOf("LATE_SENT")),
        feita("TG-24", "2026-10-17T12:00:00Z"),
        // status concluído sem resolution: fica fora (divergência vai para Qualidade dos dados).
        feita("TG-25", "2026-10-17T12:00:00Z", resolution = null),
    )

    private fun calcular(issues: List<br.sidia.painel.auditoria.AuditIssue> = cenario, sinais: List<br.sidia.painel.auditoria.Sinal> = emptyList()) =
        NoPrazo.calcular(issues, m.c.ctx.labels, m.c.statusMap, zona, de, ate, sinais)

    @Test
    fun noPrazoEAjustado() {
        val r = calcular()
        assertEquals(NoPrazoResultado(concluidas = 4, noPrazo = 2, noPrazoAjustado = 3, justificadasSobAuditoria = 0), r)
        assertEquals(0.5, r.taxa)
        assertEquals(0.75, r.taxaAjustada)
    }

    @Test
    fun justificadaSobAuditoriaContaNoAjustado() {
        val sinal = m.sinal(CodigoSinal.LABEL_APOS_VENCIMENTO, "TG-23", para = "LATE_SENT")
        val r = calcular(sinais = listOf(sinal))
        assertEquals(1, r.justificadasSobAuditoria)
        // continua contando no ajustado
        assertEquals(3, r.noPrazoAjustado)
        // sinal de outra label ou de outro código não marca a task
        assertEquals(0, calcular(sinais = listOf(m.sinal(CodigoSinal.LABEL_APOS_VENCIMENTO, "TG-23", para = "OUTRA"))).justificadasSobAuditoria)
        assertEquals(0, calcular(sinais = listOf(m.sinal(CodigoSinal.PASSA_BOLA, "TG-23", para = "LATE_SENT"))).justificadasSobAuditoria)
        assertEquals(0, calcular(sinais = listOf(m.sinal(CodigoSinal.LABEL_APOS_VENCIMENTO, "TG-24", para = "LATE_SENT"))).justificadasSobAuditoria)
    }

    @Test
    fun semConcluidasTaxaNula() {
        val r = calcular(issues = emptyList())
        assertEquals(0, r.concluidas)
        assertNull(r.taxa)
        assertNull(r.taxaAjustada)
    }

    @Test
    fun soEntramVencimentosDoPeriodo() {
        val fora = listOf(
            feita("TG-31", "2026-10-10T12:00:00Z", duedate = "2026-09-30"),
            feita("TG-32", "2026-10-10T12:00:00Z", duedate = "2026-11-01"),
            feita("TG-33", "2026-10-10T12:00:00Z", duedate = "2026-10-31"), // limite inclusivo
            m.issue("TG-34", status = "Done", resolution = "Done", resolved = "2026-10-10T12:00:00Z", duedate = null),
        )
        assertEquals(1, calcular(issues = fora).concluidas)
    }

    @Test
    fun statusSemMapaOuAbertaFicaFora() {
        val issues = listOf(
            feita("TG-41", "2026-10-10T12:00:00Z").copy(status = "Estado Novo"),
            m.issue("TG-42", status = "In Progress", duedate = "2026-10-15"),
            m.issue("TG-43", status = "Done", resolution = "Done", resolved = null, duedate = "2026-10-15"),
        )
        assertEquals(0, calcular(issues = issues).concluidas)
    }

    @Test
    fun labelsQueNaoJustificamNaoAjustam() {
        val r = calcular(issues = listOf(feita("TG-51", "2026-10-17T12:00:00Z", labels = listOf("login"))))
        assertEquals(1, r.concluidas)
        assertEquals(0, r.noPrazoAjustado)
    }
}
