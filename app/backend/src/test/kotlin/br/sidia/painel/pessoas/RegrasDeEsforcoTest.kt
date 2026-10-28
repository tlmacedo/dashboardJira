package br.sidia.painel.pessoas

import br.sidia.painel.TestPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `esforco.yaml`: valores do modelo e leitura estrita (chave desconhecida, limite violado, erro que cita o arquivo e não o valor). */
class RegrasDeEsforcoTest {
    private fun arquivo(conteudo: String): Path = Files.createTempFile("esforco", ".yaml").also {
        Files.writeString(it, conteudo)
        it.toFile().deleteOnExit()
    }

    private val modelo: String = Files.readString(TestPaths.configDir.resolve("esforco.exemplo.yaml"))

    @Test
    fun modeloTemOsValoresDoPlano() {
        val r = RegrasDeEsforco.fromYaml(TestPaths.configDir.resolve("esforco.exemplo.yaml"))
        assertEquals(1, r.versao)
        assertEquals(mapOf("Checklist" to 3.0, "Checklist TG" to 3.0, "Checklist Review" to 1.0, "Feature check" to 2.0), r.pesoPorTipo)
        assertEquals(1.0, r.pesoPadrao)
        assertEquals("TEST-", r.prefixoTeste)
        assertEquals(0.5, r.pesoTeste)
        assertEquals(1.0, r.fatorHora)
        assertEquals(RegrasDeEsforco.PADRAO, r)
        assertEquals("esforço v1", r.rotulo)
    }

    @Test
    fun regrasEstritas() {
        fun troca(de: String, para: String) = modelo.replace(de, para).also { assertTrue(it != modelo, "o modelo não tem '$de'") }
        val ruins = listOf(
            troca("versao: 1", "versao: 2"),
            troca("Checklist: 3.0", "Checklist: 0.0"),
            troca("Checklist: 3.0", "Checklist: -1.0"),
            troca("pesoPadrao: 1.0", "pesoPadrao: 0.0"),
            troca("prefixoTeste: \"TEST-\"", "prefixoTeste: \"\""),
            troca("prefixoTeste: \"TEST-\"", "prefixoTeste: \"   \""),
            troca("pesoTeste: 0.5", "pesoTeste: -0.1"),
            troca("fatorHora: 1.0", "fatorHora: -1.0"),
            troca("fatorHora: 1.0", "fatorHora: 5000.0"),
            modelo + "\nchaveNova: 1\n",
            modelo.lines().filter { !it.startsWith("fatorHora") }.joinToString("\n"),
            "versao: 1\npesoPorTipo: texto\n",
        )
        for (r in ruins) assertFailsWith<IllegalArgumentException>(r) { RegrasDeEsforco.fromYaml(arquivo(r)) }
    }

    @Test
    fun erroCitaOArquivoENuncaOValorLido() {
        val caminho = arquivo(modelo.replace("fatorHora: 1.0", "fatorHora: SEGREDO123"))
        val e = assertFailsWith<IllegalArgumentException> { RegrasDeEsforco.fromYaml(caminho) }
        assertTrue(caminho.toString() in e.message.orEmpty(), e.message)
        assertFalse("SEGREDO123" in e.message.orEmpty(), e.message)
        val desconhecida = arquivo(modelo + "\nchaveSecretaXyz: 1\n")
        val e2 = assertFailsWith<IllegalArgumentException> { RegrasDeEsforco.fromYaml(desconhecida) }
        assertFalse("chaveSecretaXyz" in e2.message.orEmpty(), e2.message)
    }
}
