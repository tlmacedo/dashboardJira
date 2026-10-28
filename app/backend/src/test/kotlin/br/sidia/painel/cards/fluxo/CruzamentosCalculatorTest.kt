package br.sidia.painel.cards.fluxo

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.db.Database
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.labelsDeAtrasoDeTeste
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import br.sidia.painel.trabalho.Trb1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cruzamentos (I-10) sobre o TRB-1 (4 abertas) e extras: eixos da lista fechada, ordem de cada eixo, totais e medida. */
class CruzamentosCalculatorTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private val hoje: LocalDate = LocalDate.parse("2026-10-30")
        private val labels: LabelsDeAtraso = labelsDeAtrasoDeTeste()
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

    private fun analisar(
        linhas: Eixo, colunas: Eixo, medida: Medida = Medida.CONTAGEM, unit: TimeUnitMode = TimeUnitMode.CORRIDO,
    ): CruzamentosAnalise {
        val abertas = SqliteWorkStore(ds).snapshots(ConsultaDeTrabalho(Trb1.INSTANCIA, null, Situacao.ABERTAS))
        val calc = CruzamentosCalculator(Trb1.calendario, Trb1.statusMap, labels) { Trb1.papeis.squadOf(it, hoje) }
        return calc.analisar(abertas, Trb1.agora, unit, linhas, colunas, medida)
    }

    private fun aberta(
        chave: String, resp: String?, status: String = "Open", tipo: String = "Checklist", prioridade: String? = null, vence: String? = null,
        labels: List<String> = emptyList(), inicio: String? = null, os: String? = null,
    ) = Trb1.issue(
        chave, tipo, resp, "10-20T12", status, vence = vence, labels = labels, prioridade = prioridade, osVersion = os,
        eventos = if (inicio == null) emptyList() else Trb1.trilha(chave, "Open", inicio to status),
    )

    private fun CruzamentosAnalise.celula(linha: String, coluna: String): CelulaDto =
        dto.celulas[dto.linhas.indexOf(linha)][dto.colunas.indexOf(coluna)]

    @Test
    fun contagemDeTipoPorCategoriaComTotais() {
        Trb1.carregar(ds)
        val a = analisar(Eixo.TIPO, Eixo.CATEGORIA)
        // TG-101 ativa, TG-103 bloqueada, TG-104 a fazer (Checklist); TG-102 a fazer (Review).
        assertEquals(listOf("Checklist", "Checklist Review"), a.dto.linhas)
        assertEquals(listOf("TODO", "ACTIVE", "BLOCKED"), a.dto.colunas)
        assertEquals(listOf(listOf(1, 1, 1), listOf(1, 0, 0)), a.dto.celulas.map { l -> l.map { it.n } })
        assertEquals(listOf(3, 1), a.dto.totaisDasLinhas)
        assertEquals(listOf(2, 1, 1), a.dto.totaisDasColunas)
        assertEquals(4, a.dto.total)
        assertEquals(listOf("squad", "categoria", "status", "tipo", "prioridade", "os", "idade", "vencimento", "atraso", "pessoa"), a.dto.eixosDeLinha)
        assertEquals(a.dto.eixosDeLinha - "pessoa", a.dto.eixosDeColuna)
        assertEquals("contagem", a.dto.medida)
        assertTrue(a.dto.celulas.flatten().all { it.valor == null && !it.amostraPequena }, "contagem não leva valor nem aviso por célula")
    }

    @Test
    fun pessoaSoComoLinhaEmOrdemAlfabeticaNuncaPorContagem() {
        // A carla passa a ter 4 tasks e as demais, 1: por contagem ela viria primeiro; a ordem é alfabética e (sem) por último.
        Trb1.carregar(ds, Trb1.issues() + listOf(aberta("TG-201", "carla"), aberta("TG-202", "carla"), aberta("TG-203", "carla")))
        val a = analisar(Eixo.PESSOA, Eixo.CATEGORIA)
        assertEquals(listOf("ana", "bruno", "carla", CruzamentosCalculator.SEM), a.dto.linhas)
        assertEquals(listOf(1, 1, 4, 1), a.dto.celulas.map { l -> l.sumOf { it.n } }, "a carla tem as 3 novas e a TG-103")
    }

    @Test
    fun semTotalPorPessoaEmDestaque() {
        Trb1.carregar(ds)
        val porPessoa = analisar(Eixo.PESSOA, Eixo.CATEGORIA)
        assertNull(porPessoa.dto.totaisDasLinhas)
        assertTrue(porPessoa.segmentos.keys.none { it.startsWith("l:") }, "nem segmento de total por pessoa")
        // O total geral e o das colunas continuam.
        assertEquals(4, porPessoa.dto.total)
        assertNotNull(analisar(Eixo.TIPO, Eixo.CATEGORIA).dto.totaisDasLinhas)
        assertTrue(analisar(Eixo.TIPO, Eixo.CATEGORIA).segmentos.keys.any { it.startsWith("l:") })
    }

    @Test
    fun pessoaNaoPodeSerColuna() {
        assertTrue(Eixo.PESSOA.soLinha)
        assertTrue(Eixo.entries.filter { it != Eixo.PESSOA }.none { it.soLinha })
        assertNull(Eixo.deId("checklistType"), "campo do dicionário não é eixo")
        assertEquals(Eixo.TIPO, Eixo.deId("tipo"))
        assertEquals(listOf("checklistType", "skuType"), CruzamentosCalculator.DICIONARIO_PENDENTE)
    }

    @Test
    fun statusNaOrdemDoFluxoEDepoisOsDemais() {
        Trb1.carregar(ds)
        // A ordem é Open, In Progress, Submitted...; On Hold não consta e vem depois.
        assertEquals(listOf("Open", "In Progress", "On Hold"), analisar(Eixo.STATUS, Eixo.TIPO).dto.linhas)
    }

    @Test
    fun prioridadeEmOrdemDePrioridadeComSemPorUltimo() {
        Trb1.carregar(ds, Trb1.issues() + listOf(aberta("TG-210", "ana", prioridade = "Low"), aberta("TG-211", "ana", prioridade = "Highest"), aberta("TG-212", "ana")))
        assertEquals(
            listOf("Highest", "High", "Medium", "Low", CruzamentosCalculator.SEM),
            analisar(Eixo.PRIORIDADE, Eixo.CATEGORIA).dto.linhas,
        )
    }

    @Test
    fun faixaDeIdadeEmOrdemNaturalEDependeDaUnidade() {
        Trb1.carregar(ds, Trb1.issues() + listOf(aberta("TG-220", "ana", "In Progress", inicio = "10-29T12")))
        val a = analisar(Eixo.IDADE, Eixo.CATEGORIA)
        // TG-220 tem 1 dia e TG-104 (nunca andou, criada em 29/10) também; TG-103 tem 9 (desde 21/10); TG-101 tem 29 (desde
        // 01/10); TG-102 nunca andou e foi criada em 15/09, então tem 45 (idade desde a criação).
        assertEquals(listOf("0-7", "8-14", "15-30", ">30"), a.dto.linhas)
        assertEquals(listOf(2, 1, 1, 1), a.dto.totaisDasLinhas)
        // Em dias úteis a idade encolhe: os 29 dias corridos da TG-101 (desde 01/10) caem para menos de 29.
        val uteis = analisar(Eixo.IDADE, Eixo.CATEGORIA, unit = TimeUnitMode.UTIL)
        assertEquals("util", uteis.dto.unit)
        assertTrue(uteis.segmentos.getValue("total").size == 5)
    }

    @Test
    fun semanaDeVencimentoCronologicaComSemPorUltimo() {
        Trb1.carregar(ds)
        // TG-101 vence 28/10 (semana de 26/10) e TG-103 vence 02/11 (semana de 02/11); as outras duas não têm vencimento.
        val a = analisar(Eixo.VENCIMENTO, Eixo.CATEGORIA)
        assertEquals(listOf("2026-10-26", "2026-11-02", CruzamentosCalculator.SEM), a.dto.linhas)
        assertEquals(listOf(1, 1, 2), a.dto.totaisDasLinhas)
    }

    @Test
    fun atrasoComESemLabel() {
        Trb1.carregar(ds, Trb1.issues() + listOf(aberta("TG-230", "ana", labels = listOf("LATE_SENT")), aberta("TG-231", "ana", labels = listOf("OUTRO"))))
        val a = analisar(Eixo.ATRASO, Eixo.CATEGORIA)
        assertEquals(listOf("com", "sem"), a.dto.linhas)
        assertEquals(listOf(1, 5), a.dto.totaisDasLinhas)
        assertEquals(listOf("TG-230"), a.segmentos.getValue("l:1").map { it.key })
    }

    @Test
    fun squadPeloDiretorioDePapeis() {
        Trb1.carregar(ds)
        val a = analisar(Eixo.SQUAD, Eixo.CATEGORIA)
        // ana (Alfa), bruno (Alfa), carla (Beta) e a TG-104, sem responsável.
        assertEquals(listOf("Alfa", "Beta", CruzamentosCalculator.SEM), a.dto.linhas)
        assertEquals(listOf(2, 1, 1), a.dto.totaisDasLinhas)
    }

    @Test
    fun osSemValorViraSem() {
        Trb1.carregar(ds, Trb1.issues() + listOf(aberta("TG-240", "ana", os = "Android 17.0 (OneUI_9.0)")))
        val a = analisar(Eixo.OS, Eixo.CATEGORIA)
        assertEquals(2, a.dto.linhas.size)
        assertEquals(CruzamentosCalculator.SEM, a.dto.linhas.last())
        assertEquals(listOf(1, 4), a.dto.totaisDasLinhas, "a TG-240 tem OS; as 4 do TRB-1 não")
    }

    @Test
    fun medidaIdadeP50ComAmostraMinima() {
        // Cinco tasks ativas com 1, 2, 3, 4 e 5 dias na célula (Checklist, ativa) mais a TG-101 (29 dias): P50 = 3,5 e n = 6.
        val ativas = listOf("10-29T12", "10-28T12", "10-27T12", "10-26T12", "10-25T12").mapIndexed { i, inicio ->
            aberta("TG-25$i", "ana", "In Progress", inicio = inicio)
        }
        Trb1.carregar(ds, Trb1.issues() + ativas)
        val a = analisar(Eixo.TIPO, Eixo.CATEGORIA, Medida.IDADE_P50)
        val ativa = a.celula("Checklist", "ACTIVE")
        assertEquals(6, ativa.n)
        assertEquals(3.5, assertNotNull(ativa.valor), 1e-9)
        assertFalse(ativa.amostraPequena, "seis tasks: amostra suficiente")
        // A bloqueada (TG-103) está sozinha: idade 9 dias (desde 21/10) e aviso de amostra pequena.
        val bloqueada = a.celula("Checklist", "BLOCKED")
        assertEquals(1, bloqueada.n)
        assertEquals(9.0, assertNotNull(bloqueada.valor), 1e-9)
        assertTrue(bloqueada.amostraPequena)
        // Célula vazia: sem valor e sem aviso (vazio não é amostra pequena).
        val vazia = a.celula("Checklist Review", "ACTIVE")
        assertEquals(0, vazia.n)
        assertNull(vazia.valor)
        assertFalse(vazia.amostraPequena)
        assertEquals("idadeP50", a.dto.medida)
    }

    @Test
    fun segmentosPosicionaisBatemComAsCelulas() {
        Trb1.carregar(ds)
        val a = analisar(Eixo.TIPO, Eixo.CATEGORIA)
        for ((i, linha) in a.dto.celulas.withIndex()) {
            for ((j, celula) in linha.withIndex()) assertEquals(celula.n, a.segmentos.getValue("c:${i + 1}:${j + 1}").size, "c:${i + 1}:${j + 1}")
            assertEquals(a.dto.totaisDasLinhas!![i], a.segmentos.getValue("l:${i + 1}").size)
        }
        for (j in a.dto.colunas.indices) assertEquals(a.dto.totaisDasColunas[j], a.segmentos.getValue("k:${j + 1}").size)
        assertEquals(a.dto.total, a.segmentos.getValue("total").size)
        assertEquals(listOf("TG-101"), a.segmentos.getValue("c:1:2").map { it.key })
    }

    @Test
    fun respostaNaoTrazChaveDeIssueNemPessoaForaDoEixoPessoa() {
        Trb1.carregar(ds)
        val json = Json.encodeToString(analisar(Eixo.TIPO, Eixo.STATUS).dto)
        assertFalse("TG-" in json)
        for (p in listOf("ana", "bruno", "carla")) assertFalse("\"$p\"" in json, "$p na resposta")
    }
}
