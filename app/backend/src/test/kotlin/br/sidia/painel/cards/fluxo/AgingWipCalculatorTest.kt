package br.sidia.painel.cards.fluxo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Envelhecimento por etapa (Aging WIP, I-05). Relógio do TRB-1 (sexta 30/10/2026 12:00Z) e dias corridos, para as contas
 * fecharem sem feriado: cada etapa tem o tempo histórico das concluídas e as tasks de hoje com a idade desde a última entrada.
 */
class AgingWipCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    /**
     * Histórico: [dias].size concluídas que ficaram `d` dias em In Progress e 1 dia em Submitted (e 1 dia em Open antes).
     * Com 1..6 o P50 de In Progress é 3,5 e o P85 é 5,25; em Submitted tudo é 1,0.
     */
    private fun historico(dias: List<Int>): List<Trb1.IssueDeTeste> = dias.mapIndexed { i, d ->
        val chave = "TG-%d".format(701 + i)
        val entra = "09-02T12"
        val sai = "09-%02dT12".format(2 + d)
        val fecha = "09-%02dT12".format(3 + d)
        Trb1.issue(
            chave, "Checklist", "ana", "09-01T12", "Closed", resolution = "Done", resolvida = fecha,
            eventos = Trb1.trilha(chave, "Open", entra to "In Progress", sai to "Submitted", fecha to "Closed"),
        )
    }

    private fun emAndamento(): List<Trb1.IssueDeTeste> = listOf(
        // In Progress desde 26/10 12:00 = 4,0 dias: abaixo do P85 (5,25).
        Trb1.issue("TG-711", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-711", "Open", "10-26T12" to "In Progress")),
        // In Progress desde 24/10 12:00 = 6,0 dias: acima do P85.
        Trb1.issue("TG-712", "Checklist", "bruno", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-712", "Open", "10-24T12" to "In Progress")),
        // Entrou, voltou a Open e reentrou em 28/10 12:00: a idade recomeça (2,0 dias), não são 29.
        Trb1.issue(
            "TG-713", "Checklist", "ana", "09-28T12", "In Progress",
            eventos = Trb1.trilha("TG-713", "Open", "10-01T12" to "In Progress", "10-10T12" to "Open", "10-28T12" to "In Progress"),
        ),
        // Em espera desde 28/10 12:00 = 2,0 dias; o histórico de Submitted é 1,0 (acima do normal).
        Trb1.issue("TG-714", "Checklist Review", "carla", "10-20T12", "Submitted", eventos = Trb1.trilha("TG-714", "Open", "10-28T12" to "Submitted")),
        // Bloqueada: etapa própria, sem histórico.
        Trb1.issue("TG-715", "Checklist", "ana", "10-20T12", "On Hold", eventos = Trb1.trilha("TG-715", "Open", "10-27T12" to "On Hold")),
        // A fazer: não é "em andamento".
        Trb1.issue("TG-716", "Checklist", "bruno", "10-20T12", "Open"),
    )

    private fun analisar(): AgingWipAnalise {
        val store = SqliteWorkStore(ds)
        val abertas = store.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
        val resolvidas = store.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS))
        return AgingWipCalculator(Trb1.calendario, Trb1.statusMap).analisar(abertas, resolvidas, Trb1.agora, TimeUnitMode.CORRIDO)
    }

    private fun etapa(a: AgingWipAnalise, status: String) = a.dto.etapas.single { it.status == status }
    private fun chaves(a: AgingWipAnalise, segmento: String) = a.segmentos.getValue(segmento).map { it.key }

    @Test
    fun idadeNaEtapaContaDesdeAUltimaEntrada() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val a = analisar()
        val ip = etapa(a, "In Progress")
        assertEquals(listOf("TG-712", "TG-711", "TG-713"), ip.pontos.map { it.key }, "mais velho primeiro")
        assertEquals(listOf(6.0, 4.0, 2.0), ip.pontos.map { it.idade })
    }

    @Test
    fun taskComImpedimentoEmAFazerNaoViraEtapaAFazer() {
        val impedida = Trb1.issue("TG-717", "Checklist", "ana", "10-20T12", "Open", flagged = true)
        Trb1.carregar(ds, emAndamento() + impedida)
        val a = analisar()
        assertTrue(a.dto.etapas.none { it.status == "Open" }, "A fazer nunca é etapa: ${a.dto.etapas.map { it.status }}")
        assertEquals(5, a.dto.emAndamento)
        assertTrue("TG-717" !in chaves(a, "emAndamento"))
    }

    @Test
    fun reentradaReiniciaAIdade() {
        Trb1.carregar(ds, emAndamento())
        val ponto = etapa(analisar(), "In Progress").pontos.single { it.key == "TG-713" }
        assertEquals(2.0, ponto.idade, "reentrou em 28/10; a primeira entrada, em 01/10, não vale")
    }

    @Test
    fun faixaDaEtapaVemDoTempoEmStatusDasConcluidas() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val a = analisar()
        val ip = etapa(a, "In Progress")
        val faixa = assertNotNull(ip.faixa)
        assertEquals(listOf(3.5, 5.25, 6), listOf(faixa.p50, faixa.p85, faixa.n))
        assertEquals(6, ip.historicoN)
        val espera = assertNotNull(etapa(a, "Submitted").faixa)
        assertEquals(listOf(1.0, 1.0, 6), listOf(espera.p50, espera.p85, espera.n))
    }

    @Test
    fun acimaDoP85TemSinalETexto() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val a = analisar()
        assertEquals(listOf(true, false, false), etapa(a, "In Progress").pontos.map { it.acima })
        assertEquals(listOf("TG-712"), chaves(a, "acima:In Progress"))
        assertEquals(listOf("TG-714"), chaves(a, "acima:Submitted"))
        assertEquals(listOf("TG-712", "TG-714"), chaves(a, "acimaDoNormal"))
        assertEquals(2, a.dto.acimaDoNormal)
        assertEquals(1, etapa(a, "In Progress").acima)
        // O P85 da etapa fica à mão para o texto da gaveta ("acima do normal").
        assertEquals(5.25, a.p85PorEtapa.getValue("In Progress"))
    }

    @Test
    fun etapaSemAmostraMinimaSemFaixa() {
        // Só 4 concluídas: abaixo do mínimo de 5, então nenhuma faixa e ninguém fica "acima do normal", por mais velho que seja.
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4)) + emAndamento())
        val a = analisar()
        val ip = etapa(a, "In Progress")
        assertNull(ip.faixa)
        assertEquals(4, ip.historicoN)
        assertEquals(0, ip.acima)
        assertEquals(0, a.dto.acimaDoNormal)
        assertTrue(ip.pontos.none { it.acima })
        // Aviso informativo por etapa que tem tasks e não tem amostra (tipo = o status, n = o histórico).
        val avisos = a.dto.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(setOf("In Progress", "Submitted", "On Hold"), avisos.map { it.params.getValue("tipo") }.toSet())
        assertEquals("4", avisos.single { it.params["tipo"] == "In Progress" }.params["n"])
        assertTrue(avisos.all { it.gravidade == AvisoDto.INFORMATIVO })
    }

    @Test
    fun etapaComAmostraNaoGeraAviso() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val tipos = analisar().dto.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }.map { it.params["tipo"] }
        assertEquals(listOf("On Hold"), tipos, "só a etapa bloqueada não tem histórico")
    }

    @Test
    fun pontoNaoTrazNomeDePessoa() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val json = Json.encodeToString(analisar().dto)
        for (p in listOf("ana", "bruno", "carla")) assertFalse("\"$p\"" in json, "$p na resposta")
        assertFalse(Regex("\"(pessoa|responsavel|assignee)").containsMatchIn(json), json)
    }

    @Test
    fun esperaEBloqueioSaoColunasProprias() {
        Trb1.carregar(ds, historico(listOf(1, 2, 3, 4, 5, 6)) + emAndamento())
        val a = analisar()
        assertEquals(listOf("ACTIVE", "WAIT", "BLOCKED"), a.dto.etapas.map { it.categoria })
        assertEquals(listOf(3, 1, 1), a.dto.etapas.map { it.total })
        // A fazer (Open) e concluídas não são etapas de andamento.
        assertTrue(a.dto.etapas.none { it.status == "Open" || it.status == "Closed" })
        assertEquals(5, a.dto.emAndamento)
        assertEquals(5, chaves(a, "emAndamento").size)
    }

    @Test
    fun ordemDasEtapasVemDoStatusMap() {
        // A ordem do TRB-1 é Open, In Progress, Submitted, Resolved, Closed; On Hold não consta e vem depois. Etapa da ordem
        // sem task agora continua na tela (a coluna existe no fluxo).
        Trb1.carregar(ds, listOf(Trb1.issue("TG-720", "Checklist", "ana", "10-20T12", "On Hold", eventos = Trb1.trilha("TG-720", "Open", "10-27T12" to "On Hold"))))
        val a = analisar()
        assertEquals(listOf("In Progress", "Submitted", "On Hold"), a.dto.etapas.map { it.status })
        assertEquals(listOf(0, 0, 1), a.dto.etapas.map { it.total })
    }

    @Test
    fun unidadeEmDiasUteisDescontaFimDeSemanaEFeriado() {
        // In Progress desde sexta 23/10 12:00Z até sexta 30/10 12:00Z: 7 dias corridos, 5 úteis (sem sáb/dom).
        Trb1.carregar(ds, listOf(Trb1.issue("TG-730", "Checklist", "ana", "10-20T12", "In Progress", eventos = Trb1.trilha("TG-730", "Open", "10-23T12" to "In Progress"))))
        val store = SqliteWorkStore(ds)
        val abertas = store.snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
        val calc = AgingWipCalculator(Trb1.calendario, Trb1.statusMap)
        val corridos = calc.analisar(abertas, emptyList(), Trb1.agora, TimeUnitMode.CORRIDO).dto.etapas.single { it.total > 0 }.pontos.single().idade
        val uteis = calc.analisar(abertas, emptyList(), Trb1.agora, TimeUnitMode.UTIL).dto.etapas.single { it.total > 0 }.pontos.single().idade
        assertEquals(7.0, corridos)
        assertTrue(uteis < corridos, "úteis $uteis < corridos $corridos")
        assertEquals("util", calc.analisar(abertas, emptyList(), Trb1.agora, TimeUnitMode.UTIL).dto.unit)
    }
}
