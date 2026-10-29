package br.sidia.painel.cards.negativas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.labelsDeAtrasoDeTeste
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Neg1
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Eventos pela data do changelog, atraso e estouro sobre o cenário NEG-1 (período 01/10 a 30/10). */
class NegativasCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val de: Instant = LocalDate.parse("2026-10-01").atStartOfDay(Trb1.zona).toInstant()
    private val ate: Instant = LocalDate.parse("2026-10-31").atStartOfDay(Trb1.zona).toInstant()
    private val todos = setOf("ana", "bruno", "carla")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        Trb1.carregar(ds, Neg1.issues())
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun calculadora(map: StatusMap = Trb1.statusMap) = NegativasCalculator(Trb1.calendario, map, labelsDeAtrasoDeTeste())

    private fun todas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS))
    private fun resolvidas() = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, de, ate))
    private fun abertas() = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))

    private fun analise(map: StatusMap = Trb1.statusMap, visiveis: Set<String> = todos) =
        calculadora(map).analisar(todas(), resolvidas(), abertas(), de, ate, visiveis, Trb1.agora)

    @Test
    fun maiorTempoPorStatusNasNegativas() {
        val a = analise()
        val uniao = (
            a.segmentos.getValue("reabertas") + a.segmentos.getValue("devolvidas") + a.segmentos.getValue("canceladas") +
                a.segmentos.getValue("concluidasComAtraso")
            ).map { it.key }.toSet()
        val grupos = a.dto.maiorTempoPorStatus
        assertTrue(uniao.isNotEmpty())
        // Cada task das quatro listas cai em exatamente um status (aquele onde ficou mais tempo), em ordem alfabética.
        assertEquals(uniao.size, grupos.sumOf { it.n })
        assertEquals(grupos.map { it.valor }.sortedBy { it!!.lowercase() }, grupos.map { it.valor })
        for (g in grupos) {
            val seg = a.segmentos.getValue("maiorTempo:${g.valor}")
            assertEquals(g.n, seg.size, g.valor)
            // A linha da gaveta diz qual é o status e quantos dias.
            assertTrue(seg.all { a.motivos.getValue("maiorTempo:${g.valor}").getValue(it.key).startsWith("Maior tempo em ${g.valor}:") })
        }
        // Os segmentos são só estes; a lista vazia (sem negativas) não inventa grupo.
        assertEquals(grupos.size, a.segmentos.keys.count { it.startsWith("maiorTempo:") })
    }

    @Test
    fun contagens() {
        val d = analise().dto
        assertEquals(1, d.reabertas)
        assertEquals(1, d.devolvidas)
        assertEquals(1, d.canceladas)
        assertEquals(2, d.concluidasComAtraso)
        assertEquals(1, d.atrasadasSemJustificativa)
        assertEquals(1, d.abertasVencidas)
        assertEquals(1, d.estouroDeEstimativa)
        assertEquals(1, d.muitasTrocas)
        assertEquals(4, d.concluidas)
    }

    @Test
    fun segmentosSaoAsTasksPorTrasDeCadaNumero() {
        val a = analise()
        assertEquals(listOf("TG-131"), a.segmentos.getValue("reabertas").map { it.key })
        assertEquals(listOf("TG-132"), a.segmentos.getValue("devolvidas").map { it.key })
        assertEquals(listOf("TG-135"), a.segmentos.getValue("canceladas").map { it.key })
        assertEquals(listOf("TG-136", "TG-137"), a.segmentos.getValue("concluidasComAtraso").map { it.key })
        assertEquals(listOf("TG-136"), a.segmentos.getValue("atrasadasSemJustificativa").map { it.key })
        assertEquals(listOf("TG-132"), a.segmentos.getValue("abertasVencidas").map { it.key })
        assertEquals(listOf("TG-136"), a.segmentos.getValue("estouroDeEstimativa").map { it.key })
        assertEquals(listOf("TG-134"), a.segmentos.getValue("muitasTrocas").map { it.key })
        assertEquals(listOf("TG-131", "TG-134", "TG-136", "TG-137"), a.segmentos.getValue("concluidas").map { it.key })
        // O motivo de cada linha cita o evento ou o prazo (a gaveta mostra).
        assertTrue("Closed" in a.motivos.getValue("reabertas").getValue("TG-131"), a.motivos.toString())
    }

    @Test
    fun taxas() {
        val d = analise().dto
        assertEquals(0.25, d.taxaReabertura!!, 1e-9)
        assertEquals(0.25, d.taxaDevolucao!!, 1e-9)
        assertEquals(0.5, d.taxaAtraso!!, 1e-9)
        assertEquals(0.25, d.taxaAtrasoSemJustificativa!!, 1e-9)
        assertEquals(0.20, d.limiteDaTaxa, 1e-9)
    }

    @Test
    fun semConcluidasTaxasNulas() {
        val d = calculadora().calcular(emptyList(), emptyList(), emptyList(), de, ate, todos, Trb1.agora)
        assertNull(d.taxaReabertura)
        assertNull(d.taxaAtraso)
        assertEquals(0, d.concluidas)
    }

    @Test
    fun reabertaForaDoPeriodoNaoConta() {
        // A TG-133 foi reaberta em 20/09 e foi atualizada em 25/10: o evento antigo não conta em outubro.
        val a = analise()
        assertTrue(a.segmentos.getValue("reabertas").none { it.key == "TG-133" })
        // Mudando o período para setembro, ela passa a contar (a data do evento manda, não o `updated`).
        val setembroDe = LocalDate.parse("2026-09-01").atStartOfDay(Trb1.zona).toInstant()
        val setembroAte = LocalDate.parse("2026-10-01").atStartOfDay(Trb1.zona).toInstant()
        val setembro = calculadora().analisar(todas(), emptyList(), abertas(), setembroDe, setembroAte, todos, Trb1.agora)
        assertTrue(setembro.segmentos.getValue("reabertas").any { it.key == "TG-133" })
    }

    @Test
    fun devolvidaNaoContaAReabertura() {
        // A reabertura da TG-131 (Closed para In Progress) volta na ordem do fluxo, mas é "reaberta", não "devolvida".
        assertTrue(analise().segmentos.getValue("devolvidas").none { it.key == "TG-131" })
    }

    @Test
    fun semOrdemDevolvidasZeroComAviso() {
        val semOrdem = StatusMap(
            mapOf(
                "Open" to StatusCategory.TODO, "In Progress" to StatusCategory.ACTIVE, "Submitted" to StatusCategory.WAIT,
                "On Hold" to StatusCategory.BLOCKED, "Resolved" to StatusCategory.DONE, "Closed" to StatusCategory.DONE,
                "Cancelled" to StatusCategory.DONE,
            ),
            resolucoesNegativas = setOf("Won't Do", "Duplicate"),
        )
        val d = analise(semOrdem).dto
        assertEquals(0, d.devolvidas)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.ORDEM_DO_FLUXO_AUSENTE && it.gravidade == AvisoDto.INFORMATIVO })
        // O resto continua calculado.
        assertEquals(1, d.reabertas)
    }

    @Test
    fun porResponsavelNoInstanteDoEvento() {
        val porPessoa = analise().dto.porResponsavel.associateBy { it.pessoa }
        // A TG-131 passou a bruno em 22/10, DEPOIS da reabertura de 21/10: a reabertura é da ana.
        assertEquals(1, porPessoa.getValue("ana").reabertas)
        assertNull(porPessoa["bruno"])
        // A devolução da TG-132 e o atraso da TG-136 são da carla; o atraso da TG-137 é da ana.
        assertEquals(NegativaPessoaDto("carla", reabertas = 0, devolvidas = 1, atrasadas = 1), porPessoa.getValue("carla"))
        assertEquals(NegativaPessoaDto("ana", reabertas = 1, devolvidas = 0, atrasadas = 1), porPessoa.getValue("ana"))
    }

    @Test
    fun porResponsavelAlfabeticoESoOsVisiveis() {
        assertEquals(listOf("ana", "carla"), analise().dto.porResponsavel.map { it.pessoa })
        // Quem não está na visão não aparece na tabela, mesmo com eventos.
        assertEquals(listOf("carla"), analise(visiveis = setOf("carla", "bruno")).dto.porResponsavel.map { it.pessoa })
    }

    @Test
    fun estouroDeEstimativaExigeEstimativaPositiva() {
        // Sem estimativa não há como estourar; gasto igual a 125% da estimativa não é estouro (só acima).
        val d = analise().dto
        assertEquals(1, d.estouroDeEstimativa)
        val a = calculadora().analisar(todas(), resolvidas().map { it.copy(estimateS = 11_200L, spentS = 14_000L) }, abertas(), de, ate, todos, Trb1.agora)
        assertEquals(0, a.dto.estouroDeEstimativa) // 14000 = 1,25 x 11200: não passa
    }
}
