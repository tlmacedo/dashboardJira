package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sinais de labels de atraso (cenário AUD-1, vencimento 15/10). */
class DetectoresDeLabelsTest {
    private val c = CenarioAud1()

    private fun detectar(
        vararg eventos: ChangeEventRecord,
        assignee: String? = "ana",
        duedate: LocalDate? = LocalDate.parse("2026-10-15"),
    ) = c.detectar(DetectoresDeLabels.todos, eventos.toList(), assignee, duedate)

    private fun de(codigo: CodigoSinal, sinais: List<Sinal>) = sinais.filter { it.codigo == codigo }

    /** Eventos que fecham a task em 14/10 às 12:00Z (status Done e resolution), como no changelog do Jira. */
    private fun fechamento() = arrayOf(
        c.ev("status", "Open", "Done", "2026-10-14T12:00:00Z", "ana"),
        c.ev("resolution", null, "Done", "2026-10-14T12:00:00Z", "ana"),
    )

    @Test
    fun labelAposFechamentoPorNaoAutorizado() {
        val s = detectar(*fechamento(), c.ev("labels", "login", "login DUEDATE_NEGOTIATED", "2026-10-14T15:00:00Z", "carla"))
        assertEquals(listOf(CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO), s.map { it.codigo })
        val unico = s.single()
        assertEquals(Resultado.AUDITORIA, unico.resultado)
        assertEquals("DUEDATE_NEGOTIATED", unico.para)
        assertEquals("carla", unico.autor)
        assertEquals(MotivoAutorizacao.NAO_AUTORIZADO, unico.motivo)
        assertEquals(2, unico.gravidade)
    }

    @Test
    fun labelPeloResponsavelAposFechamentoEhAuditoria() {
        val s = detectar(*fechamento(), c.ev("labels", null, "LATE_SENT", "2026-10-14T15:00:00Z", "ana"))
        val p = de(CodigoSinal.LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO, s).single()
        assertEquals(Resultado.AUDITORIA, p.resultado)
        assertEquals(MotivoAutorizacao.PROPRIO_RESPONSAVEL, p.motivo)
        // o responsável não vira também "não autorizado" (um só código de fechamento por evento)
        assertTrue(de(CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO, s).isEmpty())
    }

    @Test
    fun responsavelAutorizadoViraAlerta() {
        // O responsável é o próprio Reporter (autorizado): a label depois de fechada vira alerta, não auditoria.
        val s = detectar(
            c.ev("status", "Open", "Done", "2026-10-14T12:00:00Z", "pl.alfa"),
            c.ev("resolution", null, "Done", "2026-10-14T12:00:00Z", "pl.alfa"),
            c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-14T15:00:00Z", "pl.alfa"),
            assignee = "pl.alfa",
        )
        val p = de(CodigoSinal.LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO, s).single()
        assertEquals(Resultado.ALERTA, p.resultado)
        assertTrue(p.autorizado)
        assertEquals(MotivoAutorizacao.REPORTER, p.motivo)
    }

    @Test
    fun coordenadorAutorizadoNaoGeraSinalDeFechamento() {
        val s = detectar(*fechamento(), c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-14T15:00:00Z", "coord.um"))
        assertTrue(s.isEmpty(), s.toString())
    }

    @Test
    fun labelAposVencimentoFronteira() {
        // 16/10 00:00 em Manaus = 04:00Z: já é o dia seguinte ao vencimento; 03:59Z ainda é 15/10.
        val gera = detectar(c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-16T04:00:00Z", "pl.alfa"))
        val s = de(CodigoSinal.LABEL_APOS_VENCIMENTO, gera).single()
        assertEquals(Resultado.AUDITORIA, s.resultado)
        assertEquals(3, s.gravidade)
        val nao = detectar(c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-16T03:59:00Z", "pl.alfa"))
        assertTrue(de(CodigoSinal.LABEL_APOS_VENCIMENTO, nao).isEmpty())
    }

    @Test
    fun labelAposVencimentoValeParaQualquerAutor() {
        val s = detectar(c.ev("labels", null, "LATE_REVIEW", "2026-10-17T15:00:00Z", "coord.um"))
        assertEquals(1, de(CodigoSinal.LABEL_APOS_VENCIMENTO, s).size)
    }

    @Test
    fun tardiaComPosseAntecipada() {
        val s = detectar(
            c.ev("assignee", "bruno", "ana", "2026-10-12T12:00:00Z", "pl.alfa"),
            c.ev("labels", null, "LATE_SENT", "2026-10-15T20:00:00Z", "pl.alfa"),
        )
        val p = de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, s).single()
        assertEquals(Resultado.AUDITORIA, p.resultado)
        assertEquals("ana", p.responsavel)
    }

    @Test
    fun posseExatamenteDoisDias() {
        // Assumiu em 13/10 00:00 de Manaus (04:00Z) = vencimento menos 2 dias: ainda conta.
        val s = detectar(
            c.ev("assignee", "bruno", "ana", "2026-10-13T04:00:00Z", "pl.alfa"),
            c.ev("labels", null, "LATE_SENT", "2026-10-15T20:00:00Z", "pl.alfa"),
        )
        assertEquals(1, de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, s).size)
    }

    @Test
    fun posseRecente() {
        val s = detectar(
            c.ev("assignee", "bruno", "ana", "2026-10-14T12:00:00Z", "pl.alfa"),
            c.ev("labels", null, "LATE_SENT", "2026-10-15T20:00:00Z", "pl.alfa"),
        )
        assertTrue(de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, s).isEmpty())
    }

    @Test
    fun labelTardiaSoEmLabelsTardias() {
        // DUEDATE_NEGOTIATED justifica mas não é "tardia": não gera o sinal de posse antecipada.
        val s = detectar(c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-15T20:00:00Z", "pl.alfa"))
        assertTrue(de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, s).isEmpty())
    }

    @Test
    fun posseAntecipadaEmDiasUteisVoltaDiasUteis() {
        // Vencimento segunda 19/10; 2 dias úteis antes = quinta 15/10 (sexta é 1, quinta é 2).
        val regras = c.regras.copy(unidade = UnidadeDias.UTIL)
        val ctx = c.comRegras(regras)
        val venc = LocalDate.parse("2026-10-19")
        fun comPosseDesde(instante: String) = c.detectar(
            DetectoresDeLabels.todos,
            listOf(
                c.ev("assignee", "bruno", "ana", instante, "pl.alfa"),
                c.ev("labels", null, "LATE_SENT", "2026-10-19T12:00:00Z", "pl.alfa"),
            ),
            duedate = venc, contexto = ctx,
        )
        // quinta 15/10 00:00 Manaus (04:00Z): conta; quinta 15/10 12:00Z: não.
        assertEquals(1, de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, comPosseDesde("2026-10-15T04:00:00Z")).size)
        assertTrue(de(CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA, comPosseDesde("2026-10-15T12:00:00Z")).isEmpty())
    }

    @Test
    fun curingaOnHoldBy() {
        val s = detectar(c.ev("labels", null, "ONHOLDBY-PL", "2026-10-16T15:00:00Z", "pl.alfa"))
        assertEquals(1, de(CodigoSinal.LABEL_APOS_VENCIMENTO, s).size)
    }

    @Test
    fun labelQueNaoJustificaIgnorada() {
        val s = detectar(
            *fechamento(),
            c.ev("labels", null, "login", "2026-10-16T15:00:00Z", "carla"),
        )
        assertTrue(s.isEmpty(), s.toString())
    }

    @Test
    fun labelRemovidaNaoGera() {
        val s = detectar(
            *fechamento(),
            c.ev("labels", "LATE_SENT login", "login", "2026-10-16T15:00:00Z", "carla"),
        )
        assertTrue(s.isEmpty(), s.toString())
    }

    @Test
    fun semVencimentoSoRegrasDeFechamento() {
        val aberta = detectar(c.ev("labels", null, "LATE_SENT", "2026-10-30T15:00:00Z", "carla"), duedate = null)
        assertTrue(aberta.isEmpty(), aberta.toString())
        val fechada = detectar(*fechamento(), c.ev("labels", null, "LATE_SENT", "2026-10-30T15:00:00Z", "carla"), duedate = null)
        assertEquals(listOf(CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO), fechada.map { it.codigo })
    }

    @Test
    fun umEventoPodeGerarMaisDeUmCodigoMasNuncaDoisIguais() {
        // Fechada, depois do vencimento, por não autorizado, com label tardia de posse antiga: 3 códigos distintos.
        val s = detectar(
            c.ev("assignee", "bruno", "ana", "2026-10-02T12:00:00Z", "pl.alfa"),
            c.ev("status", "Open", "Done", "2026-10-14T12:00:00Z", "ana"),
            c.ev("resolution", null, "Done", "2026-10-14T12:00:00Z", "ana"),
            c.ev("labels", null, "LATE_SENT LATE_REVIEW", "2026-10-17T15:00:00Z", "carla"),
        )
        // duas labels no mesmo evento: cada uma gera os seus códigos, sem repetir o mesmo código para a mesma label
        val chaves = s.map { it.codigo to it.para }
        assertEquals(chaves.distinct(), chaves, s.toString())
        assertEquals(
            setOf(
                CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO,
                CodigoSinal.LABEL_APOS_VENCIMENTO,
                CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA,
            ),
            s.map { it.codigo }.toSet(),
        )
        assertEquals(6, s.size)
    }

    @Test
    fun regraInativaNaoGera() {
        val regras = c.regras.copy(
            sinais = c.regras.sinais + (CodigoSinal.LABEL_APOS_VENCIMENTO to RegraSinal(3, ativo = false)),
        )
        val s = c.detectar(
            DetectoresDeLabels.todos,
            listOf(c.ev("labels", null, "DUEDATE_NEGOTIATED", "2026-10-16T15:00:00Z", "pl.alfa")),
            contexto = c.comRegras(regras),
        )
        assertTrue(s.isEmpty())
    }
}
