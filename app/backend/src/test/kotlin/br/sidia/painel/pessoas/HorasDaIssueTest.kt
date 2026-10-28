package br.sidia.painel.pessoas

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Horas restantes de uma issue: Jira, estimativa menos gasto, horas por tipo e padrão (as duas últimas são assumidas). */
class HorasDaIssueTest {
    private val horas = HorasDaIssue(RegrasDePlanejamento.PADRAO)
    private val criada = Instant.parse("2026-10-20T12:00:00Z")

    private fun issue(tipo: String = "Checklist", restanteS: Long? = null, estimativaS: Long? = null, gastoS: Long? = null) =
        Cap1.snapshot("TG-900", tipo, "ana", "Open", criada, null, restanteS, estimativaS, gastoS)

    @Test
    fun restanteDoJira() {
        assertEquals(Horas(2.0, false), horas.de(issue(restanteS = 7200)))
    }

    @Test
    fun restanteDoJiraTemPrioridadeSobreAEstimativa() {
        assertEquals(Horas(2.0, false), horas.de(issue(restanteS = 7200, estimativaS = 36000, gastoS = 0)))
    }

    @Test
    fun estimativaMenosGastoComMinimo() {
        // 10 h de estimativa e 9,8 h gastas: sobram 0,2 h, mas o mínimo é 0,5 h.
        assertEquals(Horas(0.5, false), horas.de(issue(estimativaS = 36000, gastoS = 35280)))
        // Estouro: gastou mais que a estimativa, continua no mínimo.
        assertEquals(Horas(0.5, false), horas.de(issue(estimativaS = 36000, gastoS = 50000)))
    }

    @Test
    fun estimativaMenosGasto() {
        assertEquals(Horas(6.0, false), horas.de(issue(estimativaS = 36000, gastoS = 14400)))
        // Sem registro de horas, vale a estimativa inteira.
        assertEquals(Horas(4.0, false), horas.de(issue(estimativaS = 14400)))
    }

    @Test
    fun restanteZeroNaoInformaENaoViraZeroHoras() {
        // O Jira zera o restante ao estourar a estimativa; a task aberta ainda tem trabalho: segue a estimativa menos o gasto.
        assertEquals(Horas(0.5, false), horas.de(issue(restanteS = 0, estimativaS = 36000, gastoS = 40000)))
        assertEquals(Horas(6.0, true), horas.de(issue(restanteS = 0)))
    }

    @Test
    fun porTipoAssumidas() {
        assertEquals(Horas(6.0, true), horas.de(issue(tipo = "Checklist")))
        assertEquals(Horas(2.0, true), horas.de(issue(tipo = "Checklist Review")))
        // O nome do tipo não diferencia maiúsculas.
        assertEquals(Horas(2.0, true), horas.de(issue(tipo = "checklist review")))
    }

    @Test
    fun tipoDesconhecidoUsaPadrao() {
        val outras = HorasDaIssue(RegrasDePlanejamento.PADRAO.copy(horasPadrao = 5.0))
        assertEquals(Horas(5.0, true), outras.de(issue(tipo = "Feature check")))
    }

    @Test
    fun estimativaZeradaNaoConta() {
        assertEquals(Horas(6.0, true), horas.de(issue(estimativaS = 0)))
    }
}
