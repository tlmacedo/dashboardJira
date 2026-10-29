package br.sidia.painel.sync

import br.sidia.painel.camposDeTeste
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Extração dos seis campos do dicionário (Checklist Type, SKU Type etc.) do JSON da issue (Plano 10, T1). */
class CamposDoDicionarioTest {
    private fun issue(campos: String): JsonObject =
        Json.parseToJsonElement("""{"key":"TG-1","fields":$campos}""").jsonObject

    private val campos = listOf(
        CampoDeFiltro("Checklist Type", "checklist-type", "customfield_90001"),
        CampoDeFiltro("SKU Type", "sku-type", "customfield_90002"),
        CampoDeFiltro("Model Name", "model-name", "cf[90003]"),
        CampoDeFiltro("Carrier Code", "carrier-code", "customfield_90006"),
    )

    @Test
    fun extraiSelectTextoELista() {
        val corpo = issue(
            """{"customfield_90001":{"value":"Full"},"customfield_90002":[{"value":"MR"},{"name":"1st Carrier (Global)"}],
               "customfield_90003":"  MODELO-A ","customfield_90006":" OP01 "}""",
        )
        val r = CamposDoDicionario.extrair(corpo, campos)
        assertEquals("Full", r["Checklist Type"])
        assertEquals("MR; 1st Carrier (Global)", r["SKU Type"])
        assertEquals("MODELO-A", r["Model Name"])
        assertEquals("OP01", r["Carrier Code"])
    }

    @Test
    fun ausenteNuloOuVazioFicaForaDoMapa() {
        val corpo = issue("""{"customfield_90001":null,"customfield_90002":{"value":"  "},"customfield_90003":[]}""")
        assertEquals(emptyMap(), CamposDoDicionario.extrair(corpo, campos))
    }

    @Test
    fun campoSemCustomIdFicaForaSemExcecao() {
        val semId = listOf(CampoDeFiltro("Model Category", "model-category", null))
        assertEquals(emptyMap(), CamposDoDicionario.extrair(issue("""{"customfield_90004":{"value":"Phone"}}"""), semId))
        // Formato de valor inesperado (número, objeto sem value) também é ignorado em silêncio.
        val estranho = issue("""{"customfield_90001":42,"customfield_90002":{"id":"7"}}""")
        assertEquals(emptyMap(), CamposDoDicionario.extrair(estranho, campos))
    }

    @Test
    fun slugEmMinusculasComHifen() {
        assertEquals("checklist-type", CamposDoDicionario.slugDe("Checklist Type"))
        assertEquals("tg-request-type", CamposDoDicionario.slugDe("TG Request Type"))
    }

    @Test
    fun dicionarioDeExemploTemOsSeisCamposDeFiltroNaOrdemDoYaml() {
        val lista = CamposDoDicionario.deFiltro(camposDeTeste(), "samsung")
        assertEquals(
            listOf("Checklist Type", "SKU Type", "Model Name", "Model Category", "TG Request Type", "Carrier Code").sorted(),
            lista.map { it.nome }.sorted(),
        )
        assertEquals(
            listOf("checklist-type", "sku-type", "model-name", "model-category", "tg-request-type", "carrier-code").sorted(),
            lista.map { it.slug }.sorted(),
        )
        // OS Version mantém a coluna própria: não é campo de filtro do dicionário.
        assertTrue(lista.none { it.nome == "OS Version" })
        assertEquals(emptyList(), CamposDoDicionario.deFiltro(camposDeTeste(), "instancia-inexistente"))
    }
}
