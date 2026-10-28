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

    // ---- Regra única do universo (Plano 5, Tarefa 7): a mesma para Auditoria, Fechadas e Metas ----

    private val mapaComNegativas = StatusMap(
        mapOf("Done" to StatusCategory.DONE, "In Progress" to StatusCategory.ACTIVE),
        resolucoesNegativas = setOf("Won't Do", "Duplicate"),
    )

    private fun noUniverso(i: br.sidia.painel.auditoria.AuditIssue, de: LocalDate = this.de, ate: LocalDate = this.ate) =
        NoPrazo.noUniverso(i, mapaComNegativas, zona, de, ate)

    @Test
    fun canceladaComVencimentoForaDoUniverso() {
        val cancelada = feita("TG-61", "2026-10-10T12:00:00Z", resolution = "Won't Do")
        assertEquals(false, noUniverso(cancelada))
        assertEquals(false, noUniverso(feita("TG-62", "2026-10-10T12:00:00Z", resolution = "duplicate")), "sem diferenciar maiúsculas")
        assertEquals(true, noUniverso(feita("TG-63", "2026-10-10T12:00:00Z", resolution = "Done")))
        // E o cálculo também não a conta (com o mapa que conhece a resolução negativa).
        val r = NoPrazo.calcular(listOf(cancelada, feita("TG-64", "2026-10-10T12:00:00Z")), m.c.ctx.labels, mapaComNegativas, zona, de, ate)
        assertEquals(1, r.concluidas)
    }

    @Test
    fun epicForaDoUniverso() {
        val epic = m.issue("TG-65", status = "Done", resolution = "Done", resolved = "2026-10-10T12:00:00Z", duedate = "2026-10-15", tipo = "Epic")
        assertEquals(false, noUniverso(epic))
        assertEquals(false, noUniverso(epic.copy(issueType = "epic")), "sem diferenciar maiúsculas")
        assertEquals(true, noUniverso(epic.copy(issueType = "Checklist")))
    }

    @Test
    fun resolvidaAntesDoPeriodoComVencimentoDentroEntra() {
        // Resolvida em 28/09 e vencendo em 05/10: o período é pelo VENCIMENTO, então entra em outubro.
        val antes = feita("TG-66", "2026-09-28T12:00:00Z", duedate = "2026-10-05")
        assertEquals(true, noUniverso(antes))
        val r = NoPrazo.calcular(listOf(antes), m.c.ctx.labels, mapaComNegativas, zona, de, ate)
        assertEquals(1, r.noPrazo)
        // Vencimento fora do período continua fora, qualquer que seja a data de resolução.
        assertEquals(false, noUniverso(feita("TG-67", "2026-10-10T12:00:00Z", duedate = "2026-11-02")))
    }

    @Test
    fun universoExigeCategoriaResolucaoEDataDeResolucao() {
        assertEquals(false, noUniverso(m.issue("TG-68", status = "In Progress", resolution = "Done", resolved = "2026-10-10T12:00:00Z")))
        assertEquals(false, noUniverso(m.issue("TG-69", status = "Done", resolution = null, resolved = "2026-10-10T12:00:00Z")))
        assertEquals(false, noUniverso(m.issue("TG-70", status = "Done", resolution = "Done", resolved = null)))
        assertEquals(false, noUniverso(m.issue("TG-71", status = "Desconhecido", resolution = "Done", resolved = "2026-10-10T12:00:00Z")))
        assertEquals(false, noUniverso(m.issue("TG-72", status = "Done", resolution = "Done", resolved = "2026-10-10T12:00:00Z", duedate = null)))
    }

    @Test
    fun classificarFaixas() {
        val regras = m.c.ctx.labels
        fun faixa(delta: Long) = NoPrazo.classificar(LocalDate.parse("2026-10-15").plusDays(delta), LocalDate.parse("2026-10-15"), emptyList(), regras)
        assertEquals(FaixaDePrazo.D3_OU_ANTES, faixa(-3).faixa)
        assertEquals(FaixaDePrazo.D3_OU_ANTES, faixa(-9).faixa)
        assertEquals(FaixaDePrazo.D2, faixa(-2).faixa)
        assertEquals(FaixaDePrazo.D1, faixa(-1).faixa)
        assertEquals(FaixaDePrazo.D, faixa(0).faixa)
        assertEquals(FaixaDePrazo.ATRASO_1_3, faixa(1).faixa)
        assertEquals(FaixaDePrazo.ATRASO_1_3, faixa(3).faixa)
        assertEquals(FaixaDePrazo.ATRASO_MAIS_3, faixa(4).faixa)
        assertEquals(-3, faixa(-3).delta)
        assertEquals(true, faixa(0).noPrazo)
        assertEquals(false, faixa(1).noPrazo)
        // Atrasada com label que justifica: fora do prazo, mas dentro do ajustado.
        val comLabel = NoPrazo.classificar(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-15"), listOf("late_sent"), regras)
        assertEquals(false, comLabel.noPrazo)
        assertEquals(true, comLabel.ajustado)
        assertEquals(false, NoPrazo.classificar(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-15"), listOf("login"), regras).ajustado)
    }

    @Test
    fun labelsQueNaoJustificamNaoAjustam() {
        val r = calcular(issues = listOf(feita("TG-51", "2026-10-17T12:00:00Z", labels = listOf("login"))))
        assertEquals(1, r.concluidas)
        assertEquals(0, r.noPrazoAjustado)
    }
}
