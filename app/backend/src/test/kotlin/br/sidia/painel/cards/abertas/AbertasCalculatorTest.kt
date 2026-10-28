package br.sidia.painel.cards.abertas

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.db.Database
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
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
import kotlin.test.assertTrue

/** Contagens, faixas e P85 por tipo do card Abertas sobre o cenário TRB-1. */
class AbertasCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private lateinit var ds: DataSource
    private val calc = AbertasCalculator(Trb1.calendario, Trb1.statusMap)

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

    private fun abertas(): List<IssueSnapshot> =
        SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))

    private fun historico(visiveis: Set<String>? = null): List<IssueSnapshot> =
        SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.RESOLVIDAS, Trb1.t("07-01T00")))
            .filter { visiveis == null || it.assignee in visiveis }

    private fun dto(unit: TimeUnitMode) = calc.calcular(abertas(), historico(), Trb1.agora, unit)

    private fun List<ContagemDto>.mapa() = associate { it.chave to it.n }

    @Test
    fun contagensDeAbertas() {
        val d = dto(TimeUnitMode.CORRIDO)
        assertEquals(4, d.abertas)
        assertEquals(2, d.emAndamento)
        assertEquals(listOf("0-7" to 1, "8-14" to 1, "15-30" to 1, ">30" to 1), d.porIdade.map { it.chave to it.n })
        assertEquals(listOf("vencidas" to 1, "7d" to 1, "depois" to 0, "sem" to 2), d.porVencimento.map { it.chave to it.n })
        assertEquals(listOf("High" to 2, "Medium" to 2), d.porPrioridade.map { it.chave to it.n })
        // Só status com itens, na ordem do fluxo; On Hold está fora da `ordem` e vai para o fim.
        assertEquals(listOf("Open" to 2, "In Progress" to 1, "On Hold" to 1), d.porStatus.map { it.chave to it.n })
        assertEquals("corrido", d.unit)
        assertEquals(4, d.linhas.size)
        assertEquals(listOf("TG-101", "TG-102", "TG-103", "TG-104"), d.linhas.map { it.key })
        val tg103 = d.linhas.first { it.key == "TG-103" }
        assertEquals("carla", tg103.responsavel)
        assertEquals("2026-11-02", tg103.vence)
        assertEquals(9.0, tg103.idade, 1e-9)
    }

    @Test
    fun acimaDoP85UsaOProprioTipo() {
        val d = dto(TimeUnitMode.UTIL)
        // Checklist: ciclos úteis [4, 6, 8] -> P85 7,4. TG-101 (idade 20) entra; TG-103 (idade 7) não.
        val checklist = d.historicoPorTipo.first { it.tipo == "Checklist" }
        assertEquals(6.0, checklist.p50, 1e-9)
        assertEquals(7.4, checklist.p85, 1e-9)
        assertEquals(3, checklist.n)
        assertEquals(1, d.acimaDoP85)
        val analise = calc.analisar(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        assertEquals(listOf("TG-101"), analise.segmentos.getValue("acimaDoP85").map { it.key })
        assertEquals(20.0, analise.idades.getValue("TG-101"), 1e-9)
        assertEquals(7.0, analise.idades.getValue("TG-103"), 1e-9)
    }

    @Test
    fun faixaDeOutroTipoNaoContaParaAIdade() {
        // TG-102 é Checklist Review e está em Open: não está em andamento, então nunca entra no P85, mesmo muito velha.
        val analise = calc.analisar(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        assertTrue("TG-102" !in analise.segmentos.getValue("acimaDoP85").map { it.key })
        assertTrue("TG-102" !in analise.segmentos.getValue("emAndamento").map { it.key })
    }

    @Test
    fun semHistoricoDoTipoGeraAvisoENaoContaNoP85() {
        val sem = calc.analisar(abertas(), emptyList(), Trb1.agora, TimeUnitMode.UTIL)
        assertEquals(0, sem.dto.acimaDoP85)
        val aviso = sem.dto.avisos.single { it.codigo == AvisoDto.SEM_HISTORICO_DO_TIPO }
        assertEquals("2", aviso.params["n"], "TG-101 e TG-103 estão em andamento e são de tipo sem histórico")
        assertEquals(emptyList(), sem.dto.historicoPorTipo)
    }

    @Test
    fun amostraPequenaDoHistoricoAvisaSemEsconderONumero() {
        val d = dto(TimeUnitMode.UTIL)
        val avisos = d.avisos.filter { it.codigo == AvisoDto.AMOSTRA_PEQUENA }
        assertEquals(listOf("Checklist", "Checklist Review"), avisos.map { it.params["tipo"] })
        assertEquals("3", avisos[0].params["n"])
        assertEquals(1.0, d.historicoPorTipo.first { it.tipo == "Checklist Review" }.p50, 1e-9)
    }

    @Test
    fun camposDeTrabalhoIndisponiveisViraAviso() {
        val d = calc.calcular(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL, temCamposDeTrabalho = false)
        assertEquals(emptyList(), d.porPrioridade)
        assertTrue(d.avisos.any { it.codigo == AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS })
        assertEquals(4, d.abertas, "o resto do card continua")
        // Com os campos disponíveis, não há aviso.
        assertTrue(dto(TimeUnitMode.UTIL).avisos.none { it.codigo == AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS })
    }

    @Test
    fun doneSemResolvedNaoEhAbertaMasViraAviso() {
        val i = Trb1.issue(
            "TG-190", "Checklist", "ana", "10-03T12", "Closed",
            eventos = Trb1.trilha("TG-190", "Open", "10-05T12" to "In Progress", "10-12T12" to "Closed"),
        )
        Trb1.carregar(ds, listOf(i))
        val d = dto(TimeUnitMode.UTIL)
        assertEquals(4, d.abertas)
        assertTrue("TG-190" !in d.linhas.map { it.key })
        assertEquals("1", d.avisos.single { it.codigo == AvisoDto.DONE_SEM_RESOLUTION }.params["n"])
    }

    @Test
    fun segmentosTemOMesmoTamanhoDasContagens() {
        val a = calc.analisar(abertas(), historico(), Trb1.agora, TimeUnitMode.UTIL)
        val d = a.dto
        assertEquals(d.abertas, a.segmentos.getValue("abertas").size)
        assertEquals(d.emAndamento, a.segmentos.getValue("emAndamento").size)
        for (c in d.porStatus) assertEquals(c.n, a.segmentos.getValue("status:${c.chave}").size)
        for (c in d.porPrioridade) assertEquals(c.n, a.segmentos.getValue("prioridade:${c.chave}").size)
        for (c in d.porIdade) assertEquals(c.n, a.segmentos.getValue("idade:${c.chave}").size)
        for (c in d.porVencimento) assertEquals(c.n, a.segmentos.getValue("vence:${c.chave}").size)
        for (f in d.historicoPorTipo) assertEquals(f.n, a.segmentos.getValue("historico:${f.tipo}").size)
    }

    @Test
    fun historicoSoComOsVisiveis() {
        val soAna = calc.calcular(abertas(), historico(setOf("ana")), Trb1.agora, TimeUnitMode.UTIL)
        val checklist = soAna.historicoPorTipo.single()
        assertEquals("Checklist", checklist.tipo)
        assertEquals(2, checklist.n)
        assertEquals(6.0, checklist.p50, 1e-9)
        assertEquals(7.4, checklist.p85, 1e-9)
        assertTrue(Fluxo.AMOSTRA_MINIMA > checklist.n)
    }
}
