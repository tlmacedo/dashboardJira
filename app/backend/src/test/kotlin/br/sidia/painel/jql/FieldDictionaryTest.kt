package br.sidia.painel.jql

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldDictionaryTest {
    private val exemplo = TestPaths.configDir.resolve("jira_campos.exemplo.yaml")

    private fun campo(nome: String, instancia: String = "samsung") =
        FieldDef(nome, FieldType.TEXT, instancia, "teste")

    @Test
    fun carregaOExemplo() {
        val dic = FieldDictionary.fromYaml(exemplo)
        val os = dic.get("OS Version")
        assertEquals(FieldType.SELECT, os.type)
        assertEquals(4, os.knownValues.size)
        assertEquals(os.knownValues, dic.allowedValues("OS Version"))
        assertEquals(3, dic.allowedValues("SKU Type").size)
        assertTrue(dic.has("cf[12416]"))
        assertTrue(dic.get("cf[11412]").purpose.contains("a confirmar"))
        assertNull(dic.get("OS Version").customId)
    }

    @Test
    fun campoDesconhecidoFalha() {
        val dic = FieldDictionary(listOf(campo("A")))
        val e = assertFailsWith<UnknownFieldException> { dic.get("Inexistente") }
        assertEquals("Inexistente", e.name)
        assertFalse(dic.has("Inexistente"))
    }

    @Test
    fun nomeDuplicadoFalha() {
        val e = assertFailsWith<IllegalArgumentException> {
            FieldDictionary(listOf(campo("Dup"), campo("Dup")))
        }
        assertTrue(e.message!!.contains("Dup"))
    }

    @Test
    fun mesmoNomeEmInstanciasDiferentes() {
        val dic = FieldDictionary(listOf(campo("Comum", "samsung"), campo("Comum", "outra")))
        assertTrue(dic.has("Comum"))
    }

    @Test
    fun yamlComChaveDesconhecidaFalhaCitandoArquivo() {
        val tmp = Files.createTempFile("campos", ".yaml")
        try {
            Files.writeString(
                tmp,
                "campos:\n  - nome: A\n    tipo: TEXT\n    instancia: x\n    finalidade: f\n    inventada: 1\n",
            )
            val e = assertFailsWith<Exception> { FieldDictionary.fromYaml(tmp) }
            assertTrue(e.message!!.contains(tmp.toString()))
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    @Test
    fun tipoInvalidoCitaArquivoECampo() {
        val tmp = Files.createTempFile("campos", ".yaml")
        try {
            Files.writeString(
                tmp,
                "campos:\n  - nome: Estranho\n    tipo: XPTO\n    instancia: x\n    finalidade: f\n",
            )
            val e = assertFailsWith<IllegalArgumentException> { FieldDictionary.fromYaml(tmp) }
            assertTrue(e.message!!.contains(tmp.toString()))
            assertTrue(e.message!!.contains("Estranho"))
            assertTrue(e.message!!.contains("XPTO"))
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
