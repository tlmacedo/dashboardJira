package br.sidia.painel.cards.previsao

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.previsao.RegrasDePrevisao
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.MonteCarlo
import br.sidia.painel.trabalho.SerieSemanal
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Previsão (Monte Carlo com taxa de chegada) sobre o TRB-1 e sobre um cenário de vazão constante. */
class PrevisaoCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private const val SEMENTE = 20261030L
    }

    private lateinit var ds: DataSource
    private val hoje: LocalDate = LocalDate.parse("2026-10-30")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun abertas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
    private fun resolvidas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS))

    private fun analisar(regras: RegrasDePrevisao = RegrasDePrevisao.PADRAO, abertas: List<IssueSnapshot> = abertas()) =
        PrevisaoCalculator(Trb1.calendario, Trb1.statusMap, regras).analisar(abertas, resolvidas(), Trb1.agora, SEMENTE, TimeUnitMode.UTIL)

    /** 8 semanas fechadas (31/08 a 19/10) com 5 entregas por semana, mais um backlog aberto de [backlog] tasks. */
    private fun cenarioDeVazaoConstante(backlog: Int): List<Trb1.IssueDeTeste> {
        val quartas = listOf("09-02", "09-09", "09-16", "09-23", "09-30", "10-07", "10-14", "10-21")
        val segundas = listOf("08-31", "09-07", "09-14", "09-21", "09-28", "10-05", "10-12", "10-19")
        val feitas = quartas.indices.flatMap { w ->
            (1..5).map { n ->
                val chave = "TG-%d".format(300 + w * 5 + n)
                Trb1.issue(
                    chave, "Checklist", "ana", "${segundas[w]}T12", "Closed", resolution = "Done", resolvida = "${quartas[w]}T12",
                    eventos = Trb1.trilha(chave, "Open", "${quartas[w]}T00" to "In Progress", "${quartas[w]}T12" to "Closed"),
                )
            }
        }
        val abertas = (1..backlog).map { Trb1.issue("TG-%d".format(400 + it), "Checklist", "ana", "10-21T12", "Open") }
        return feitas + abertas
    }

    @Test
    fun backlogESeriesDoCenario() {
        Trb1.carregar(ds)
        val a = analisar().dto
        assertEquals(4, a.backlog)
        assertEquals(16, a.vazaoSemanal.size)
        assertEquals("2026-07-06", a.vazaoSemanal.first().semana)
        assertEquals("2026-10-19", a.vazaoSemanal.last().semana) // a semana em curso (26/10) não entra
        assertEquals(listOf(0, 1, 2, 1), a.vazaoSemanal.takeLast(4).map { it.n })
        assertEquals(listOf(4, 3, 0, 1), a.chegadaSemanal.takeLast(4).map { it.n })
        assertEquals(4, a.entregasNaJanela)
        assertEquals("previsao v1", a.versaoRegras)
        assertEquals("util", a.unit)
        assertEquals("corrido", PrevisaoCalculator(Trb1.calendario, Trb1.statusMap, RegrasDePrevisao.PADRAO)
            .analisar(abertas(), resolvidas(), Trb1.agora, SEMENTE, TimeUnitMode.CORRIDO).dto.unit)
        assertEquals(16, a.semanasHistorico)
        assertEquals(104, a.limiteSemanas)
    }

    @Test
    fun segmentosSaoAsTasksPorTrasDosNumeros() {
        Trb1.carregar(ds)
        val s = analisar().segmentos
        assertEquals(listOf("TG-101", "TG-102", "TG-103", "TG-104"), s.getValue("backlog").map { it.key })
        assertEquals(listOf("TG-111", "TG-112", "TG-113", "TG-114"), s.getValue("vazao").map { it.key })
        assertEquals(listOf("TG-112", "TG-114"), s.getValue("vazao:2026-10-12").map { it.key })
        assertEquals(listOf("TG-113", "TG-114", "TG-115"), s.getValue("chegada:2026-10-05").map { it.key })
        assertEquals(emptyList(), s.getValue("vazao:2026-09-28"))
        assertEquals(listOf("TG-101", "TG-103"), s.getValue("porIssue").map { it.key })
    }

    @Test
    fun mesmaSerieEMesmoMonteCarloQueAntes() {
        // Regressão da extração da SerieSemanal: as séries do card são as do Fluxo e o resultado é o do Monte Carlo direto.
        Trb1.carregar(ds)
        val a = analisar()
        val todas = abertas() + resolvidas()
        val ultima = SerieSemanal.ultimaSemanaFechada(hoje)
        val positivas = todas.filter { Fluxo.concluidaPositiva(it, Trb1.statusMap) }
        val vazao = Fluxo.throughputSemanal(positivas, ultima, 16, Trb1.zona)
        val chegada = Fluxo.chegadasSemanais(todas, ultima, 16, Trb1.zona)
        assertEquals(vazao.map { it.second }, a.dto.vazaoSemanal.map { it.n })
        assertEquals(chegada.map { it.second }, a.dto.chegadaSemanal.map { it.n })
        // O sorteio usa só as semanas com dado (a partir da primeira atividade), sem o zero anterior à base.
        val comDado = a.dto.semanasComDado
        val esperado = MonteCarlo(Random(SEMENTE)).quando(vazao.map { it.second }.takeLast(comDado), chegada.map { it.second }.takeLast(comDado), 4, 10_000, 104)
        assertEquals(esperado.p50, a.dto.termina.semanasP50)
        assertEquals(esperado.p85, a.dto.termina.semanasP85)
        assertEquals(esperado.p95, a.dto.termina.semanasP95)
    }

    @Test
    fun confiancaPelaAmostra() {
        Trb1.carregar(ds)
        // A base do TRB-1 tem atividade desde a semana de 14/09: 6 semanas com dado, abaixo de 8.
        val a = analisar().dto
        assertEquals(6, a.semanasComDado)
        assertEquals("baixa", a.confianca)
        Trb1.carregar(ds, cenarioDeVazaoConstante(0))
        val b = analisar().dto
        assertEquals(8, b.semanasComDado)
        assertEquals("media", b.confianca)
    }

    @Test
    fun chegadaMaiorQueAVazaoNaoConvergeEDizMaisQueOLimite() {
        Trb1.carregar(ds)
        val t = analisar().dto.termina // entram 9 e saem 4 no período: o backlog nunca termina
        assertEquals(QuandoDto(null, null, null, null, null, null), t)
    }

    @Test
    fun semChegadaCorrespondeAoAppAnterior() {
        Trb1.carregar(ds, cenarioDeVazaoConstante(20))
        // Vazão constante de 5 por semana e backlog de 20: 4 semanas, sem a taxa de chegada (como o legado).
        val a = analisar(RegrasDePrevisao.PADRAO.copy(comChegada = false)).dto
        assertEquals(20, a.backlog)
        assertEquals(false, a.comChegada)
        assertEquals(listOf(4, 4, 4), listOf(a.termina.semanasP50, a.termina.semanasP85, a.termina.semanasP95))
        // Semana alvo = 4ª semana inteira depois da semana em curso (26/10): segunda 23/11.
        assertEquals("2026-11-23", a.termina.p50)
        assertEquals(listOf(10, 20, 40), a.quantos.map { it.p50 })
        assertEquals(listOf(2, 4, 8), a.quantos.map { it.semanas })
        assertEquals(a.quantos.map { it.p50 }, a.quantos.map { it.p95 }) // vazão constante: todos os percentis iguais
    }

    @Test
    fun comChegadaConstanteIgualAVazaoNaoConverge() {
        // Cada entrega do cenário foi criada na mesma semana: entram 5 e saem 5 por semana.
        Trb1.carregar(ds, cenarioDeVazaoConstante(20))
        val a = analisar().dto
        assertTrue(a.comChegada)
        assertNull(a.termina.semanasP50)
        assertNull(a.termina.p95)
    }

    @Test
    fun semVazaoAvisaESemDatas() {
        Trb1.carregar(ds, listOf(Trb1.issue("TG-500", "Checklist", "ana", "10-05T12", "Open")))
        val a = analisar().dto
        assertEquals(1, a.backlog)
        assertEquals(QuandoDto(null, null, null, null, null, null), a.termina)
        assertTrue(a.avisos.any { it.codigo == AvisoDto.SEM_VAZAO_HISTORICA && it.gravidade == AvisoDto.INFORMATIVO })
    }

    @Test
    fun backlogVazioTerminaEmZeroSemanas() {
        Trb1.carregar(ds, cenarioDeVazaoConstante(0))
        val t = analisar().dto.termina
        assertEquals(0, t.semanasP50)
        assertNull(t.p50) // sem backlog não há semana alvo
    }

    @Test
    fun restanteDadaIdadePorTipoDoCenario() {
        Trb1.carregar(ds)
        val porIssue = analisar().dto.porIssue.associateBy { it.key }
        // Só tasks em andamento (TG-102 e TG-104 estão em Open); amostra pequena (menos de 5 ciclos mais longos): traço.
        assertEquals(setOf("TG-101", "TG-103"), porIssue.keys)
        assertNull(porIssue.getValue("TG-101").restanteP50)
        assertEquals(0, porIssue.getValue("TG-101").n) // nenhum ciclo do histórico passa de 20 dias úteis
        assertEquals(1, porIssue.getValue("TG-103").n) // só o ciclo de 8 dias úteis passa de 7
        assertEquals(20.0, porIssue.getValue("TG-101").idade, 1e-9)
    }

    @Test
    fun restanteDadaIdadeComAmostraSuficiente() {
        val longas = (1..6).map { n ->
            Trb1.issue(
                "TG-60$n", "Checklist", "bruno", "08-20T12", "Closed", resolution = "Done", resolvida = "10-14T12",
                eventos = Trb1.trilha("TG-60$n", "Open", "09-01T12" to "In Progress", "10-14T12" to "Closed"),
            )
        }
        Trb1.carregar(ds, Trb1.issues() + longas)
        val t101 = analisar().dto.porIssue.first { it.key == "TG-101" }
        assertEquals(6, t101.n)
        assertNotNull(t101.restanteP50)
        assertNotNull(t101.restanteP85)
        assertTrue(t101.restanteP50!! > 0 && t101.restanteP85!! >= t101.restanteP50!!)
    }

    @Test
    fun amostraPequenaPorTipoAvisa() {
        Trb1.carregar(ds)
        val avisos = analisar().dto.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(listOf("Checklist", "Checklist Review"), avisos.map { it.params.getValue("tipo") })
    }
}
