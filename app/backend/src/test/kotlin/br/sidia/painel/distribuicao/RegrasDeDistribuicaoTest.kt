package br.sidia.painel.distribuicao

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `distribuicao.yaml`: valores do modelo e leitura estrita (chave desconhecida, limite violado, erro que não cita o valor lido). */
class RegrasDeDistribuicaoTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("distribuicao", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val modelo: String = Files.readString(TestPaths.configDir.resolve("distribuicao.exemplo.yaml"))

    @Test
    fun modeloTemOsValoresDoPlano() {
        val r = RegrasDeDistribuicao.fromYaml(TestPaths.configDir.resolve("distribuicao.exemplo.yaml"))
        assertEquals(1, r.versao)
        assertEquals(0.5, r.pesoWip)
        assertEquals(1.0, r.penalidadeWipCheio)
        assertEquals(0.6, r.pesoCompetenciaFaltante)
        assertEquals(0.15, r.bonusEpicoOuComponente)
        assertEquals(0.85, r.produtividadeMin)
        assertEquals(1.15, r.produtividadeMax)
        assertEquals(0.5, r.capacidadeMinimaH)
        assertEquals(1.10, r.gatilhoRebalanceamento)
        assertEquals(10, r.maxMovimentos)
        assertEquals(20.0, r.histereseRebalanceamentoPp)
        assertEquals(RegrasDeDistribuicao.PADRAO, r, "o PADRÃO dos testes é o modelo versionado")
        assertEquals("distribuicao v1", r.rotulo)
    }

    @Test
    fun regrasEstritas() {
        fun troca(de: String, para: String) = modelo.replace(de, para).also { assertTrue(it != modelo, "o modelo não tem '$de'") }
        val ruins = listOf(
            troca("versao: 1", "versao: 2"),
            troca("pesoWip: 0.5", "pesoWip: -0.1"),
            troca("pesoWip: 0.5", "pesoWip: 11.0"),
            troca("penalidadeWipCheio: 1.0", "penalidadeWipCheio: -1.0"),
            troca("pesoCompetenciaFaltante: 0.6", "pesoCompetenciaFaltante: 99.0"),
            troca("bonusEpicoOuComponente: 0.15", "bonusEpicoOuComponente: -0.15"),
            troca("produtividadeMin: 0.85", "produtividadeMin: 0.0"),
            troca("produtividadeMin: 0.85", "produtividadeMin: 1.2"),
            troca("produtividadeMax: 1.15", "produtividadeMax: 0.9"),
            troca("produtividadeMax: 1.15", "produtividadeMax: 4.0"),
            troca("capacidadeMinimaH: 0.5", "capacidadeMinimaH: -1.0"),
            troca("gatilhoRebalanceamento: 1.10", "gatilhoRebalanceamento: 1.0"),
            troca("gatilhoRebalanceamento: 1.10", "gatilhoRebalanceamento: 50.0"),
            troca("maxMovimentos: 10", "maxMovimentos: 0"),
            troca("maxMovimentos: 10", "maxMovimentos: 101"),
            troca("histereseRebalanceamentoPp: 20", "histereseRebalanceamentoPp: -1"),
            troca("histereseRebalanceamentoPp: 20", "histereseRebalanceamentoPp: 101"),
            modelo + "\nchaveNova: 1\n",
            modelo.lines().filter { !it.startsWith("maxMovimentos") }.joinToString("\n"),
            "versao: 1\npesoWip: texto\n",
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { RegrasDeDistribuicao.fromYaml(arquivo(r)) }
    }

    @Test
    fun erroCitaOArquivoENuncaOValorLido() {
        val caminho = arquivo(modelo.replace("maxMovimentos: 10", "maxMovimentos: SEGREDO123"))
        val e = assertFailsWith<IllegalArgumentException> { RegrasDeDistribuicao.fromYaml(caminho) }
        assertTrue(caminho.toString() in e.message.orEmpty(), e.message)
        assertFalse("SEGREDO123" in e.message.orEmpty(), e.message)
        val desconhecida = arquivo(modelo + "\nchaveSecretaXyz: 1\n")
        val e2 = assertFailsWith<IllegalArgumentException> { RegrasDeDistribuicao.fromYaml(desconhecida) }
        assertFalse("chaveSecretaXyz" in e2.message.orEmpty(), e2.message)
        assertEquals(null, e2.cause, "a causa do parser (que traz o valor) não é encadeada")
    }
}
