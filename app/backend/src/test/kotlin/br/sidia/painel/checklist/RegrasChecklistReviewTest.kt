package br.sidia.painel.checklist

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `checklist_review.yaml`: valores do modelo e leitura estrita (chave desconhecida, limite violado, erro que não cita o valor lido). */
class RegrasChecklistReviewTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("checklist_review", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val modelo: String = Files.readString(TestPaths.configDir.resolve("checklist_review.exemplo.yaml"))

    @Test
    fun modeloTemOsValoresDoPlano() {
        val r = RegrasChecklistReview.fromYaml(TestPaths.configDir.resolve("checklist_review.exemplo.yaml"))
        assertEquals(1, r.versao)
        assertEquals(setOf("Checklist", "Checklist TG"), r.tiposPai)
        assertEquals(5, r.amostraMinima)
        assertEquals(0.10, r.limiteVenceDepois)
        assertEquals(0.10, r.limiteMesmaPessoa)
        assertEquals(0.30, r.limiteParesComAlerta)
        assertEquals(0.5, r.reviewCurtaHoras)
        assertEquals(listOf(1.0, 4.0, 12.0), r.faixasDuracaoHoras)
        assertEquals(RegrasChecklistReview.PADRAO, r, "o PADRÃO dos testes é o modelo versionado")
        assertEquals("checklist-review v1", r.rotulo)
    }

    @Test
    fun regrasEstritas() {
        fun troca(de: String, para: String) = modelo.replace(de, para).also { assertTrue(it != modelo, "o modelo não tem '$de'") }
        val ruins = listOf(
            troca("versao: 1", "versao: 2"),
            troca("tiposPai: [Checklist, \"Checklist TG\"]", "tiposPai: []"),
            troca("tiposPai: [Checklist, \"Checklist TG\"]", "tiposPai: [\"  \"]"),
            troca("amostraMinima: 5", "amostraMinima: 0"),
            troca("amostraMinima: 5", "amostraMinima: 1001"),
            troca("limiteVenceDepois: 0.10", "limiteVenceDepois: 0.0"),
            troca("limiteVenceDepois: 0.10", "limiteVenceDepois: 1.5"),
            troca("limiteMesmaPessoa: 0.10", "limiteMesmaPessoa: -0.1"),
            troca("limiteParesComAlerta: 0.30", "limiteParesComAlerta: 2.0"),
            troca("reviewCurtaHoras: 0.5", "reviewCurtaHoras: 0.0"),
            troca("reviewCurtaHoras: 0.5", "reviewCurtaHoras: 5000.0"),
            troca("faixasDuracaoHoras: [1, 4, 12]", "faixasDuracaoHoras: []"),
            troca("faixasDuracaoHoras: [1, 4, 12]", "faixasDuracaoHoras: [4, 1, 12]"),
            troca("faixasDuracaoHoras: [1, 4, 12]", "faixasDuracaoHoras: [1, 1, 12]"),
            troca("faixasDuracaoHoras: [1, 4, 12]", "faixasDuracaoHoras: [0, 4]"),
            troca("faixasDuracaoHoras: [1, 4, 12]", "faixasDuracaoHoras: [1, 2, 3, 4, 5, 6, 7]"),
            modelo + "\nchaveNova: 1\n",
            modelo.lines().filter { !it.startsWith("amostraMinima") }.joinToString("\n"),
            "versao: 1\ntiposPai: texto\n",
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { RegrasChecklistReview.fromYaml(arquivo(r)) }
    }

    @Test
    fun erroCitaOArquivoENuncaOValorLido() {
        val caminho = arquivo(modelo.replace("amostraMinima: 5", "amostraMinima: SEGREDO123"))
        val e = assertFailsWith<IllegalArgumentException> { RegrasChecklistReview.fromYaml(caminho) }
        assertTrue(caminho.toString() in e.message.orEmpty(), e.message)
        assertFalse("SEGREDO123" in e.message.orEmpty(), e.message)
        val desconhecida = arquivo(modelo + "\nchaveSecretaXyz: 1\n")
        val e2 = assertFailsWith<IllegalArgumentException> { RegrasChecklistReview.fromYaml(desconhecida) }
        assertFalse("chaveSecretaXyz" in e2.message.orEmpty(), e2.message)
        assertEquals(null, e2.cause, "a causa do parser (que traz o valor) não é encadeada")
    }

    @Test
    fun tipoDoPaiNaoDiferenciaMaiusculas() {
        val r = RegrasChecklistReview.PADRAO
        assertTrue(r.ehPai("checklist"))
        assertTrue(r.ehPai(" CHECKLIST TG "))
        assertFalse(r.ehPai("Task"))
        assertFalse(r.ehPai("Checklist Review"))
    }
}
