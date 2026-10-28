package br.sidia.painel.cards.metas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.domain.FaixaDePrazo
import br.sidia.painel.labelsDeAtrasoDeTeste
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import br.sidia.painel.trabalho.candidatasNoPrazo
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Metas e prazos sobre o cenário TRB-1 (vencimentos de 01/10 a 30/10). */
class MetasCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val regras = RegrasDeMetas.PADRAO
    private val calc = MetasCalculator(Trb1.calendario, Trb1.statusMap, labelsDeAtrasoDeTeste(), regras, Trb1.papeis)
    private val de = LocalDate.parse("2026-10-01")
    private val ate = LocalDate.parse("2026-10-30")
    private val todos = setOf("ana", "bruno", "carla")

    @BeforeTest
    fun preparar() {
        ds = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4)
        Database.migrate(ds)
        Trb1.carregar(ds)
    }

    @AfterTest
    fun encerrar() {
        (ds as AutoCloseable).close()
    }

    private fun candidatas(): List<IssueSnapshot> = SqliteWorkStore(ds).candidatasNoPrazo(Trb1.INSTANCIA, null, de, ate)

    private fun resolvidas(): List<IssueSnapshot> = SqliteWorkStore(ds).snapshots(
        ConsultaDeTrabalho(
            Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, de.atStartOfDay(Trb1.zona).toInstant(),
            ate.plusDays(1).atStartOfDay(Trb1.zona).toInstant(),
        ),
    )

    private fun analisar(g: Granularidade = Granularidade.SEMANA, visiveis: Set<String> = todos) =
        calc.analisar(candidatas(), resolvidas(), de, ate, g, visiveis)

    private fun dto(g: Granularidade = Granularidade.SEMANA) = analisar(g).dto

    @Test
    fun metasDoCenario() {
        val d = dto()
        assertEquals(4, d.universo)
        assertEquals(3, d.noPrazo)
        assertEquals(4, d.noPrazoAjustado)
        assertEquals(1, d.antecipadas)
        assertEquals(1, d.emD1D2)
        assertEquals(2, d.noDia)
        assertEquals(1, d.atrasadas)
        assertEquals(0.75, d.taxaNoPrazo!!, 1e-9)
        assertEquals(1.0, d.taxaAjustada!!, 1e-9)
        assertEquals(0.25, d.taxaAntecipadas!!, 1e-9)
        assertEquals("metas v1, labels v1", d.versaoRegras)
        assertEquals(0.90, d.metaNoPrazo)
        assertEquals(0.35, d.metaAntecipadas)
    }

    @Test
    fun dataOriginal() {
        val d = dto()
        // TG-111 vencia 08/10 até o evento de 07/10: pela data original saiu 1 dia depois.
        assertEquals(2, d.noPrazoOriginal)
        assertEquals(0.5, d.taxaOriginal!!, 1e-9)
        assertTrue(d.alertaDataOriginal, "75% pela data atual contra 50% pela original: 25 pp >= 5 pp")
        // Sem remarcação, os dois lados são iguais e não há alerta.
        val semRemarcacao = calc.analisar(candidatas().filter { it.key != "TG-111" }, resolvidas(), de, ate, Granularidade.SEMANA, todos).dto
        assertEquals(semRemarcacao.noPrazo, semRemarcacao.noPrazoOriginal)
        assertFalse(semRemarcacao.alertaDataOriginal)
    }

    @Test
    fun excluiEpicECancelada() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-151", "Epic", "ana", "10-01T12", "Resolved", resolution = "Done", resolvida = "10-18T12", vence = "2026-10-20",
                    eventos = Trb1.trilha("TG-151", "Open", "10-02T12" to "In Progress", "10-18T12" to "Resolved"),
                ),
                Trb1.issue(
                    "TG-150", "Task", "ana", "10-01T12", "Cancelled", resolution = "Won't Do", resolvida = "10-19T12", vence = "2026-10-20",
                    eventos = Trb1.trilha("TG-150", "Open", "10-19T12" to "Cancelled"),
                ),
            ),
        )
        val d = dto()
        assertEquals(4, d.universo)
        assertEquals(0, d.semVencimento)
    }

    @Test
    fun semVencimentoTemDrillProprio() {
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-118", "Checklist", "bruno", "10-01T12", "Closed", resolution = "Done", resolvida = "10-21T12",
                    eventos = Trb1.trilha("TG-118", "Open", "10-02T12" to "In Progress", "10-21T12" to "Closed"),
                ),
            ),
        )
        val a = analisar()
        assertEquals(1, a.dto.semVencimento)
        assertEquals(listOf("TG-118"), a.segmentos.getValue("semVencimento").map { it.key })
        // Fora do universo: as taxas não mudam.
        assertEquals(4, a.dto.universo)
    }

    @Test
    fun estadoDaLinha() {
        assertEquals("ok", MetasCalculator.estadoDaTaxa(0.90, regras))
        assertEquals("ok", MetasCalculator.estadoDaTaxa(1.0, regras))
        assertEquals("atencao", MetasCalculator.estadoDaTaxa(0.85, regras))
        assertEquals("atencao", MetasCalculator.estadoDaTaxa(0.80, regras))
        assertEquals("problema", MetasCalculator.estadoDaTaxa(0.75, regras))
        assertEquals("problema", MetasCalculator.estadoDaTaxa(0.0, regras))
        // Na tabela do cenário: tipo Checklist 2 de 3 no prazo = 0,67 -> problema; Review 1 de 1 = 1,0 -> ok.
        val porTipo = dto().porTipo.associateBy { it.chave }
        assertEquals("problema", porTipo.getValue("Checklist").estado)
        assertEquals(3, porTipo.getValue("Checklist").universo)
        assertEquals(2, porTipo.getValue("Checklist").noPrazo)
        assertEquals(3, porTipo.getValue("Checklist").noPrazoAjustado)
        assertEquals("ok", porTipo.getValue("Checklist Review").estado)
    }

    @Test
    fun tabelaPorPessoaAlfabetica() {
        val d = dto()
        assertEquals(listOf("ana", "bruno", "carla"), d.porPessoa.map { it.chave })
        assertEquals(listOf(2, 1, 1), d.porPessoa.map { it.universo })
        // ana tem 2 (uma no prazo e uma ajustada); a ordem NÃO segue a taxa (bruno e carla têm 100%).
        assertEquals(listOf(1, 1, 1), d.porPessoa.map { it.noPrazo })
        assertEquals(listOf(2, 1, 1), d.porPessoa.map { it.noPrazoAjustado })
        assertEquals(listOf("Alfa", "Beta"), d.porSquad.map { it.chave })
        assertEquals(listOf(3, 1), d.porSquad.map { it.universo })
        assertEquals(listOf("(sem)"), d.porPrioridade.map { it.chave }, "as concluídas do cenário não têm prioridade")
    }

    @Test
    fun soPessoasVisiveisNaTabelaDePessoas() {
        val d = analisar(visiveis = setOf("ana")).dto
        assertEquals(listOf("ana"), d.porPessoa.map { it.chave })
    }

    @Test
    fun pessoaESquadPelaPosseNoDiaDaResolucao() {
        // TG-175 passou da ana (Alfa) para a carla (Beta) antes de resolver: conta para a carla e para o squad Beta.
        Trb1.carregar(
            ds,
            listOf(
                Trb1.issue(
                    "TG-175", "Checklist", "carla", "10-01T12", "Closed", resolution = "Done", resolvida = "10-20T12", vence = "2026-10-21",
                    eventos = Trb1.trilha("TG-175", "Open", "10-02T12" to "In Progress", "10-20T12" to "Closed") +
                        Trb1.ev("TG-175", "assignee", "ana", "carla", "10-10T12"),
                ),
            ),
        )
        val d = dto()
        assertEquals(2, d.porPessoa.first { it.chave == "carla" }.universo)
        assertEquals(2, d.porSquad.first { it.chave == "Beta" }.universo)
    }

    @Test
    fun distribuicaoPorFaixaSomaOUniverso() {
        val d = dto()
        assertEquals(FaixaDePrazo.entries.map { it.name }, d.distribuicao.map { it.chave })
        assertEquals(d.universo, d.distribuicao.sumOf { it.n })
        assertEquals(mapOf("D3_OU_ANTES" to 0, "D2" to 1, "D1" to 0, "D" to 2, "ATRASO_1_3" to 0, "ATRASO_MAIS_3" to 1), d.distribuicao.associate { it.chave to it.n })
    }

    @Test
    fun seriePorGranularidadePeloVencimento() {
        // Vencimentos: 09/10 (qui), 15/10 (qui), 16/10 (sex) x2.
        assertEquals(listOf("2026-10-05" to 1, "2026-10-12" to 3), dto(Granularidade.SEMANA).porPeriodo.map { it.chave to it.universo })
        assertEquals(listOf("2026-10-09" to 1, "2026-10-15" to 1, "2026-10-16" to 2), dto(Granularidade.DIA).porPeriodo.map { it.chave to it.universo })
        assertEquals(listOf("2026-10" to 4), dto(Granularidade.MES).porPeriodo.map { it.chave to it.universo })
        val semana = dto().porPeriodo
        assertEquals(1, semana[0].noPrazo)
        assertEquals(2, semana[1].noPrazo)
        assertEquals(0.6666, semana[1].taxaNoPrazo!!, 1e-3)
    }

    @Test
    fun segmentosTemOMesmoTamanhoDasContagens() {
        val a = analisar()
        val d = a.dto
        assertEquals(d.universo, a.segmentos.getValue("universo").size)
        assertEquals(d.noPrazo, a.segmentos.getValue("noPrazo").size)
        assertEquals(d.noPrazoAjustado, a.segmentos.getValue("noPrazoAjustado").size)
        assertEquals(d.noPrazoOriginal, a.segmentos.getValue("noPrazoOriginal").size)
        assertEquals(d.antecipadas, a.segmentos.getValue("antecipadas").size)
        assertEquals(d.emD1D2, a.segmentos.getValue("emD1D2").size)
        assertEquals(d.noDia, a.segmentos.getValue("noDia").size)
        assertEquals(d.atrasadas, a.segmentos.getValue("atrasadas").size)
        for (c in d.distribuicao) assertEquals(c.n, a.segmentos.getValue("faixa:${c.chave}").size)
        for (p in d.porPeriodo) {
            assertEquals(p.universo, a.segmentos.getValue("periodo:${p.chave}").size)
            assertEquals(p.noPrazo, a.segmentos.getValue("periodo:${p.chave}:noPrazo").size)
        }
        for (l in d.porTipo) assertEquals(l.universo, a.segmentos.getValue("tipo:${l.chave}").size)
        for (l in d.porPessoa) assertEquals(l.universo, a.segmentos.getValue("pessoa:${l.chave}").size)
        for (l in d.porSquad) assertEquals(l.universo, a.segmentos.getValue("squad:${l.chave}").size)
    }

    @Test
    fun universoVazioDaTaxasNulas() {
        val d = calc.calcular(emptyList(), emptyList(), de, ate, Granularidade.SEMANA, todos)
        assertEquals(0, d.universo)
        assertNull(d.taxaNoPrazo)
        assertNull(d.taxaOriginal)
        assertFalse(d.alertaDataOriginal)
        assertEquals(emptyList(), d.porPeriodo)
    }
}
