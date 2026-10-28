package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Extração dos campos de trabalho (prioridade, estimativas, pontos, épico, flag, componentes) do JSON da issue. */
class CamposDeTrabalhoTest {
    private fun issue(campos: String): JsonObject =
        Json.parseToJsonElement("""{"key":"TG-1","fields":$campos}""").jsonObject

    private val ids = mapOf("Story Points" to "customfield_10002", "Epic Link" to "cf[10003]", "Flagged" to "customfield_10004")

    @Test
    fun extraiPrioridadeEEstimativas() {
        val c = CamposDeTrabalhoExtrator.extrair(issue("""{"priority":{"name":"High"},"timeestimate":7200}"""), ids)
        assertEquals("High", c.priority)
        assertEquals(7200L, c.remainingS)
        assertEquals(emptyList(), c.components)
    }

    @Test
    fun flagPresenteViraTrue() {
        val com = CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10004":[{"value":"Impediment"}]}"""), ids)
        assertTrue(com.flagged)
        assertFalse(CamposDeTrabalhoExtrator.extrair(issue("""{}"""), ids).flagged)
        assertFalse(CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10004":[]}"""), ids).flagged)
        assertFalse(CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10004":null}"""), ids).flagged)
    }

    @Test
    fun campoCustomSemIdFicaNulo() {
        val corpo = issue("""{"customfield_10002":5.0,"customfield_10003":"TG-9","customfield_10004":[{"value":"x"}]}""")
        val c = CamposDeTrabalhoExtrator.extrair(corpo, mapOf("Story Points" to null))
        assertNull(c.storyPoints)
        assertNull(c.epicKey)
        assertFalse(c.flagged)
        // Sem nenhum id no mapa o resultado é o mesmo: nada de exceção.
        assertNull(CamposDeTrabalhoExtrator.extrair(corpo, emptyMap()).storyPoints)
    }

    @Test
    fun pontosNaoNumericosFicamNulos() {
        for (bruto in listOf("\"muitos\"", "\"3\"", "true", "[3]", "{\"v\":3}", "null")) {
            val c = CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10002":$bruto}"""), ids)
            assertNull(c.storyPoints, "pontos '$bruto' deveria ser nulo")
        }
        assertEquals(3.0, CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10002":3}"""), ids).storyPoints)
        assertEquals(2.5, CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10002":2.5}"""), ids).storyPoints)
    }

    @Test
    fun epicEComponentesSaemDosCampos() {
        val c = CamposDeTrabalhoExtrator.extrair(
            issue("""{"customfield_10003":"TG-900","components":[{"name":"Camera"},{"name":"Audio"},{"id":"7"}]}"""),
            ids,
        )
        assertEquals("TG-900", c.epicKey)
        assertEquals(listOf("Camera", "Audio"), c.components)
        // Valor que não parece chave de issue não vira épico.
        assertNull(CamposDeTrabalhoExtrator.extrair(issue("""{"customfield_10003":"qualquer coisa"}"""), ids).epicKey)
    }
}
