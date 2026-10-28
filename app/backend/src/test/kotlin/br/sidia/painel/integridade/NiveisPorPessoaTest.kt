package br.sidia.painel.integridade

import br.sidia.painel.cards.metas.MetasDto
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nível por pessoa (alto, médio, normal) e impacto nas metas, no cenário INT-1: taxa, mediana com piso, múltiplo e os três critérios do nível alto. */
class NiveisPorPessoaTest {
    private val regras = Int1.regras

    /** Evidências de uma pessoa: [n] de cada gravidade (não importa o sinal). */
    private fun evidencias(pessoa: String?, g3: Int = 0, g2: Int = 0, g1: Int = 0, prefixo: String = "TG-${pessoa ?: "x"}"): List<EvidenciaIntegridade> {
        var k = 0
        fun uma(g: Int) = EvidenciaIntegridade(
            SinalIntegridade.entries.first { regras.gravidade(it) == g }, g, "$prefixo-${++k}", pessoa, Int1.agora, emptyMap(),
        )
        return List(g3) { uma(3) } + List(g2) { uma(2) } + List(g1) { uma(1) }
    }

    private fun metas(taxaNoPrazo: Double?, taxaOriginal: Double?) = MetasDto(
        versaoRegras = "metas v1", universo = 0, noPrazo = 0, noPrazoAjustado = 0, noPrazoOriginal = 0, antecipadas = 0, emD1D2 = 0, noDia = 0,
        atrasadas = 0, semVencimento = 0, taxaNoPrazo = taxaNoPrazo, taxaAjustada = null, taxaOriginal = taxaOriginal, taxaAntecipadas = null,
        alertaDataOriginal = false, metaNoPrazo = 0.9, metaAntecipadas = 0.35, granularidade = "semana", porPeriodo = emptyList(),
        distribuicao = emptyList(), porTipo = emptyList(), porPrioridade = emptyList(), porSquad = emptyList(), porPessoa = emptyList(), avisos = emptyList(),
    )

    private val concluidas = mapOf("ana" to 10, "bruno" to 8, "carla" to 12, "lider.alfa" to 10)

    /** `ana` 12 pontos (4 graves), `bruno` 6, `carla` 1, `lider.alfa` 0. */
    private val cenario = evidencias("ana", g3 = 4) + evidencias("bruno", g2 = 3) + evidencias("carla", g1 = 1)

    @Test
    fun niveisDoCenario() {
        val r = NiveisPorPessoa.resumir(cenario, concluidas, regras, null)
        val porPessoa = r.pessoas.associateBy { it.pessoa }
        assertEquals(1.2, porPessoa.getValue("ana").taxa, 1e-9)
        assertEquals(0.75, porPessoa.getValue("bruno").taxa, 1e-9)
        assertEquals(1.0 / 12, porPessoa.getValue("carla").taxa, 1e-6)
        assertEquals(0.0, porPessoa.getValue("lider.alfa").taxa, 1e-9)
        // Mediana das quatro taxas: (0,083333 + 0,75) / 2.
        assertEquals(0.416667, r.medianaTaxa, 1e-6)
        assertEquals(2.88, porPessoa.getValue("ana").multiplo, 1e-6)
        assertEquals(NivelIntegridade.MEDIO, porPessoa.getValue("ana").nivel)
        assertEquals(1.8, porPessoa.getValue("bruno").multiplo, 1e-6)
        assertEquals(NivelIntegridade.NORMAL, porPessoa.getValue("bruno").nivel)
        assertEquals(NivelIntegridade.NORMAL, porPessoa.getValue("carla").nivel)
        assertEquals(NivelIntegridade.NORMAL, porPessoa.getValue("lider.alfa").nivel)
        assertEquals(12, porPessoa.getValue("ana").pontos)
        assertEquals(4, porPessoa.getValue("ana").graves)
        assertEquals(10, porPessoa.getValue("ana").concluidas)
    }

    @Test
    fun altoComOsTres() {
        // ana com 15 pontos e 4 graves: taxa 1,5, múltiplo 3,6, pontos >= 12 e graves >= 3.
        val ana = evidencias("ana", g3 = 4, g2 = 1, g1 = 1)
        val r = NiveisPorPessoa.resumir(ana + evidencias("bruno", g2 = 3) + evidencias("carla", g1 = 1), concluidas, regras, null)
        val a = r.pessoas.first { it.pessoa == "ana" }
        assertEquals(15, a.pontos)
        assertEquals(3.6, a.multiplo, 1e-6)
        assertEquals(NivelIntegridade.ALTO, a.nivel)
    }

    @Test
    fun altoExigeOsGraves() {
        // 15 pontos e múltiplo 3,6, mas só 2 graves: fica em MEDIO.
        val ana = evidencias("ana", g3 = 2, g2 = 3, g1 = 3)
        val r = NiveisPorPessoa.resumir(ana + evidencias("bruno", g2 = 3) + evidencias("carla", g1 = 1), concluidas, regras, null)
        val a = r.pessoas.first { it.pessoa == "ana" }
        assertEquals(15, a.pontos)
        assertEquals(3.6, a.multiplo, 1e-6)
        assertEquals(2, a.graves)
        assertEquals(NivelIntegridade.MEDIO, a.nivel)
    }

    @Test
    fun altoExigeOMultiplo() {
        // Muitos pontos e graves, mas todo o time tem taxa parecida: o múltiplo não passa de 3.
        val todos = listOf("ana", "bruno", "carla", "lider.alfa").flatMap { evidencias(it, g3 = 4) }
        val r = NiveisPorPessoa.resumir(todos, mapOf("ana" to 10, "bruno" to 10, "carla" to 10, "lider.alfa" to 10), regras, null)
        assertTrue(r.pessoas.all { it.nivel == NivelIntegridade.NORMAL }, r.pessoas.toString())
    }

    @Test
    fun concluidasMinimas() {
        // Duas concluídas contam como oito: a taxa de quem fechou pouco não dispara.
        val r = NiveisPorPessoa.resumir(evidencias("ana", g3 = 2), mapOf("ana" to 2), regras, null)
        val a = r.pessoas.single()
        assertEquals(6.0 / 8, a.taxa, 1e-9)
        assertEquals(2, a.concluidas, "o número real de concluídas é mostrado; só a conta usa o mínimo")
    }

    @Test
    fun pisoDaMediana() {
        // Ninguém com sinal: mediana 0 sobe para o piso 0,05.
        val r = NiveisPorPessoa.resumir(emptyList(), concluidas, regras, null)
        assertEquals(0.05, r.medianaTaxa, 1e-12)
        assertEquals(0, r.evidencias)
        assertTrue(r.pessoas.all { it.nivel == NivelIntegridade.NORMAL && it.pontos == 0 })
    }

    @Test
    fun pessoaSemConcluidasTemTaxaMasNaoEntraNaMediana() {
        // `pl.alfa` só tem sinais (não fechou nada): taxa = pontos / 8, mas a mediana é só de quem tem concluídas.
        val r = NiveisPorPessoa.resumir(cenario + evidencias("pl.alfa", g2 = 2), concluidas, regras, null)
        assertEquals(0.416667, r.medianaTaxa, 1e-6)
        val pl = r.pessoas.first { it.pessoa == "pl.alfa" }
        assertEquals(0, pl.concluidas)
        assertEquals(4.0 / 8, pl.taxa, 1e-9)
    }

    @Test
    fun pessoasAlfabeticas() {
        val r = NiveisPorPessoa.resumir(
            evidencias("zoe", g3 = 1) + evidencias("ana", g3 = 5) + evidencias("mario", g2 = 2), mapOf("zoe" to 3, "ana" to 3, "mario" to 3, "bia" to 3), regras, null,
        )
        assertEquals(listOf("ana", "bia", "mario", "zoe"), r.pessoas.map { it.pessoa })
        // Nenhuma posição, ordenação por pontos ou campo de "melhor/pior" no resultado.
        val campos = NivelPessoa::class.memberProperties.map { it.name.lowercase() }
        assertTrue(campos.none { "posicao" in it || "rank" in it || "melhor" in it || "pior" in it }, campos.toString())
    }

    @Test
    fun impactoNasMetas() {
        assertEquals(25.0, NiveisPorPessoa.resumir(cenario, concluidas, regras, metas(0.75, 0.5)).impactoMetasPp)
        assertEquals(0.0, NiveisPorPessoa.resumir(cenario, concluidas, regras, metas(0.5, 0.5)).impactoMetasPp)
        // Sem Metas, ou sem uma das taxas (sem vencimentos no período), não há o que dizer.
        assertNull(NiveisPorPessoa.resumir(cenario, concluidas, regras, null).impactoMetasPp)
        assertNull(NiveisPorPessoa.resumir(cenario, concluidas, regras, metas(null, 0.5)).impactoMetasPp)
        assertNull(NiveisPorPessoa.resumir(cenario, concluidas, regras, metas(0.75, null)).impactoMetasPp)
    }

    @Test
    fun totaisPorSinalEIssuesAfetadas() {
        val duas = listOf(
            EvidenciaIntegridade(SinalIntegridade.FECHAMENTO_EM_LOTE, 3, "TG-1", "ana", Int1.agora, emptyMap()),
            EvidenciaIntegridade(SinalIntegridade.CRIADA_E_FECHADA_RAPIDO, 3, "TG-1", "ana", Int1.agora, emptyMap()),
            EvidenciaIntegridade(SinalIntegridade.FECHADA_POR_OUTRO, 1, "TG-2", null, Int1.agora, emptyMap()),
        )
        val r = NiveisPorPessoa.resumir(duas, concluidas, regras, null)
        assertEquals(3, r.evidencias)
        assertEquals(2, r.issuesAfetadas)
        assertEquals(SinalIntegridade.entries, r.porSinal.keys.toList(), "todos os sinais, na ordem do catálogo")
        assertEquals(1, r.porSinal.getValue(SinalIntegridade.FECHAMENTO_EM_LOTE))
        assertEquals(0, r.porSinal.getValue(SinalIntegridade.PULOU_O_FLUXO))
        // Evidência sem pessoa conta nos totais e fica fora dos níveis.
        assertTrue(r.pessoas.none { it.pontos == 1 && it.pessoa == "null" })
        assertEquals(6, r.pessoas.first { it.pessoa == "ana" }.pontos)
    }
}
