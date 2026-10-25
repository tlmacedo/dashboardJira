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
        assertEquals(
            listOf("Android 16.0 (16)", "Android 16.0 (OneUI_8.5)", "Android 17.0 (OneUI_9.0)", "Android 17.0 (OneUI_9.5)"),
            dic.allowedValues("OS Version"),
        )
        assertEquals(listOf("1st carrier (Latin)", "1st Carrier (Global)", "MR"), dic.allowedValues("SKU Type"))
        assertTrue(dic.has("cf[12416]"))
        assertTrue(dic.get("cf[11412]").purpose.contains("a confirmar"))
        assertNull(dic.get("OS Version").customId)
        listOf("resolution", "summary", "updated", "parent", "resolutiondate").forEach { assertTrue(dic.has(it), it) }
    }

    @Test
    fun campoDesconhecidoFalha() {
        val dic = FieldDictionary(listOf(campo("A")))
        val e = assertFailsWith<UnknownFieldException> { dic.get("Inexistente") }
        assertEquals("Inexistente", e.name)
        assertEquals("Campo fora do dicionário: Inexistente", e.message)
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
    fun nomeDuplicadoIgnoraMaiusculasNaMesmaInstancia() {
        assertFailsWith<IllegalArgumentException> {
            FieldDictionary(listOf(campo("Dup"), campo("DUP")))
        }
    }

    @Test
    fun mesmoNomeEmInstanciasDiferentes() {
        val sam = FieldDef("Comum", FieldType.SELECT, "samsung", "do samsung", listOf("A", "B"))
        val sid = FieldDef("Comum", FieldType.TEXT, "sidia", "da sidia")
        val dic = FieldDictionary(listOf(sam, sid))
        assertEquals(sam, dic.forInstance("samsung").get("Comum"))
        assertEquals(sid, dic.forInstance("sidia").get("Comum"))
        assertEquals(listOf("A", "B"), dic.forInstance("samsung").allowedValues("Comum"))
        val e1 = assertFailsWith<IllegalStateException> { dic.get("Comum") }
        assertTrue(e1.message!!.contains("Comum") && e1.message!!.contains("samsung") && e1.message!!.contains("sidia"))
        assertFailsWith<IllegalStateException> { dic.has("Comum") }
        assertFailsWith<IllegalStateException> { dic.allowedValues("Comum") }
    }

    @Test
    fun instanciaSemCamposDevolveDicionarioVazio() {
        val dic = FieldDictionary(listOf(campo("A"))).forInstance("nenhuma")
        assertFalse(dic.has("A"))
    }

    @Test
    fun buscaIgnoraMaiusculas() {
        val dic = FieldDictionary.fromYaml(exemplo)
        assertEquals("Reporter", dic.get("reporter").name)
        assertTrue(dic.has("OS VERSION"))
        assertEquals(4, dic.allowedValues("os version").size)
    }

    @Test
    fun campoSemListaDeValoresTemAllowedValuesVazio() {
        assertEquals(emptyList(), FieldDictionary.fromYaml(exemplo).allowedValues("Model Name"))
    }

    @Test
    fun fieldDefValida() {
        fun def(
            name: String = "N",
            instance: String = "i",
            purpose: String = "p",
            values: List<String> = emptyList(),
            customId: String? = null,
        ) = FieldDef(name, FieldType.SELECT, instance, purpose, values, customId)

        assertFailsWith<IllegalArgumentException> { def(name = " ") }
        assertFailsWith<IllegalArgumentException> { def(instance = "") }
        val e = assertFailsWith<IllegalArgumentException> { def(name = "Campo X", purpose = " ") }
        assertTrue(e.message!!.contains("Campo X"))
        assertFailsWith<IllegalArgumentException> { def(customId = "customfield_abc") }
        assertFailsWith<IllegalArgumentException> { def(customId = "xcf[1]") }
        assertFailsWith<IllegalArgumentException> { def(values = listOf("A", " ")) }
        assertFailsWith<IllegalArgumentException> { def(values = listOf("A", "A")) }
        assertEquals("customfield_12345", def(customId = "customfield_12345").customId)
        assertEquals("cf[12416]", def(customId = "cf[12416]").customId)
    }

    @Test
    fun knownValuesEhCopiado() {
        val origem = mutableListOf("A", "B")
        val def = FieldDef("N", FieldType.SELECT, "i", "p", origem)
        origem.add("C")
        assertEquals(listOf("A", "B"), def.knownValues)
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

    @Test
    fun campoInvalidoNoYamlCitaArquivo() {
        val tmp = Files.createTempFile("campos", ".yaml")
        try {
            Files.writeString(
                tmp,
                "campos:\n  - nome: Ruim\n    tipo: TEXT\n    instancia: x\n    finalidade: f\n    customId: abc\n",
            )
            val e = assertFailsWith<IllegalArgumentException> { FieldDictionary.fromYaml(tmp) }
            assertTrue(e.message!!.contains(tmp.toString()))
            assertTrue(e.message!!.contains("Ruim"))
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
