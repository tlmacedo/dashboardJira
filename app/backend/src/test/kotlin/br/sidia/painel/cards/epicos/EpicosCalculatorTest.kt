package br.sidia.painel.cards.epicos

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.previsao.RegrasDePrevisao
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import org.junit.jupiter.api.AfterAll
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Épicos: progresso, exclusão de review, previsão só com amostra própria e aviso sem o campo de épico. */
class EpicosCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val calc = EpicosCalculator(Trb1.calendario, Trb1.statusMap, RegrasDePrevisao.PADRAO, setOf("Checklist Review"))

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun todas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.TODAS))
    private fun analise() = calc.analisar(todas(), Trb1.agora, 7L)

    private fun feita(chave: String, epico: String, resolvida: String, tipo: String = "Checklist") = Trb1.issue(
        chave, tipo, "ana", "09-01T12", "Closed", resolution = "Done", resolvida = resolvida, epico = epico,
        eventos = Trb1.trilha(chave, "Open", "${resolvida.substring(0, 5)}T00" to "In Progress", resolvida to "Closed"),
    )

    private fun aberta(chave: String, epico: String, criada: String = "09-10T12", vence: String? = null, pontos: Double? = null) =
        Trb1.issue(chave, "Checklist", "ana", criada, "In Progress", epico = epico, vence = vence, pontos = pontos)

    @Test
    fun progressoEExclusaoDeReview() {
        // Épico com 6 tasks (4 concluídas, 2 abertas) e 2 Reviews (fora dos itens).
        val itens = listOf("10-05T12", "10-06T12", "10-07T12", "10-08T12").mapIndexed { i, r -> feita("TG-91$i", "TG-900", r) } +
            listOf(aberta("TG-920", "TG-900"), aberta("TG-921", "TG-900")) +
            listOf(feita("TG-930", "TG-900", "10-09T12", "Checklist Review"), feita("TG-931", "TG-900", "10-09T12", "Checklist Review"))
        Trb1.carregar(ds, itens)
        val l = analise().dto.linhas.single()
        assertEquals("TG-900", l.epico)
        assertEquals(6, l.itens)
        assertEquals(4, l.concluidos)
        assertEquals(0.666667, l.progresso!!, 1e-9)
    }

    @Test
    fun canceladaNaoEItem() {
        val cancelada = Trb1.issue("TG-940", "Checklist", "ana", "09-01T12", "Closed", resolution = "Won't Do", resolvida = "10-01T12", epico = "TG-901")
        Trb1.carregar(ds, listOf(cancelada, aberta("TG-941", "TG-901")))
        val l = analise().dto.linhas.single()
        assertEquals(1, l.itens)
        assertEquals(0, l.concluidos)
    }

    @Test
    fun semAmostraPropriaTraco() {
        // 4 concluídas no histórico (menos que o mínimo de 5): sem previsão, nunca a vazão do time.
        Trb1.carregar(ds, (0..3).map { feita("TG-95$it", "TG-902", "10-0${5 + it}T12") } + aberta("TG-960", "TG-902"))
        assertNull(analise().dto.linhas.single().previsaoSemanasP85)
    }

    @Test
    fun comAmostraPropria() {
        // 8 entregas do épico, 1 por semana nas 8 semanas fechadas (31/08 a 19/10), 3 abertas, sem chegada: 3 semanas.
        val quartas = listOf("09-02", "09-09", "09-16", "09-23", "09-30", "10-07", "10-14", "10-21")
        Trb1.carregar(ds, quartas.mapIndexed { i, d -> feita("TG-97$i", "TG-903", "${d}T12") } + (0..2).map { aberta("TG-98$it", "TG-903") })
        val l = analise().dto.linhas.single()
        assertEquals(3, l.previsaoSemanasP85)
        assertEquals(8, l.concluidos)
        assertEquals(11, l.itens)
    }

    @Test
    fun vencidosMaisAntigoEPontos() {
        Trb1.carregar(
            ds,
            listOf(
                aberta("TG-990", "TG-904", criada = "09-10T12", vence = "2026-10-20", pontos = 3.0),
                aberta("TG-991", "TG-904", criada = "09-20T12", vence = "2026-11-20", pontos = 5.0),
                aberta("TG-992", "TG-904", criada = "10-01T12"),
            ),
        )
        val l = analise().dto.linhas.single()
        assertEquals(1, l.vencidos) // só a TG-990 (hoje é 30/10)
        assertEquals("2026-09-10", l.maisAntigo)
        assertEquals(8.0, l.pontos)
        // Sem nenhuma task com pontos, o total é nulo (traço), nunca 0.
        Trb1.carregar(ds, listOf(aberta("TG-993", "TG-905")))
        assertNull(analise().dto.linhas.first { it.epico == "TG-905" }.pontos)
    }

    @Test
    fun linhasEmOrdemDeChaveEIdOpacoDaChave() {
        Trb1.carregar(ds, listOf(aberta("TG-994", "TG-910"), aberta("TG-995", "TG-902"), aberta("TG-996", "TG-910")))
        val a = analise()
        assertEquals(listOf("TG-902", "TG-910"), a.dto.linhas.map { it.epico })
        // O id do segmento não carrega a chave (a chave nunca vai para o access_log).
        assertTrue(a.dto.linhas.all { "TG-" !in it.id && it.id.length >= 8 }, a.dto.linhas.toString())
        assertNotEquals(a.dto.linhas[0].id, a.dto.linhas[1].id)
        val id = a.dto.linhas[1].id
        assertEquals(listOf("TG-994", "TG-996"), a.segmentos.getValue("epico:$id:itens").map { it.key })
    }

    @Test
    fun semCampoEpicoAviso() {
        Trb1.carregar(ds) // o TRB-1 não tem épico
        val d = analise().dto
        assertTrue(d.linhas.isEmpty())
        assertTrue(d.avisos.any { it.codigo == AvisoDto.EPICO_INDISPONIVEL && it.gravidade == AvisoDto.INFORMATIVO })
    }

    @Test
    fun segmentosDeConcluidosEVencidos() {
        Trb1.carregar(ds, listOf(feita("TG-997", "TG-906", "10-05T12"), aberta("TG-998", "TG-906", vence = "2026-10-01")))
        val a = analise()
        val id = a.dto.linhas.single().id
        assertEquals(listOf("TG-997"), a.segmentos.getValue("epico:$id:concluidos").map { it.key })
        assertEquals(listOf("TG-998"), a.segmentos.getValue("epico:$id:vencidos").map { it.key })
        assertEquals(2, a.segmentos.getValue("epico:$id:itens").size)
    }
}
