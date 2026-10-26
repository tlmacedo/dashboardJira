package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coleta das chaves internas (JIRAUSER*) do JSON recebido; sem rede nem banco. */
class ChavesDeUsuarioTest {

    private fun obj(texto: String): JsonObject = Json.parseToJsonElement(texto).jsonObject

    private fun historicos(texto: String) = Json.parseToJsonElement(texto).jsonArray.map { it.jsonObject }

    @Test
    fun reconheceChaveInternaSemDiferenciarMaiusculas() {
        assertTrue(ChavesDeUsuario.ehChaveInterna("JIRAUSER10500"))
        assertTrue(ChavesDeUsuario.ehChaveInterna("jirauser7"))
        assertFalse(ChavesDeUsuario.ehChaveInterna("ana.silva"))
        assertFalse(ChavesDeUsuario.ehChaveInterna("JIRAUSER"))
        assertFalse(ChavesDeUsuario.ehChaveInterna("JIRAUSER12x"))
        assertFalse(ChavesDeUsuario.ehChaveInterna("xJIRAUSER12"))
        assertFalse(ChavesDeUsuario.ehChaveInterna(""))
    }

    @Test
    fun coletaDeAssigneeReporterCreatorEAutores() {
        val issue = obj(
            """{"key":"TG-1","fields":{
                "assignee":{"name":"Ana.Silva","key":"JIRAUSER1"},
                "reporter":{"name":" Carla ","key":"jirauser2"},
                "creator":{"name":"Dino","key":"JIRAUSER3"}}}""",
        )
        val h = historicos(
            """[{"id":"1","author":{"name":"Eva","key":"JIRAUSER4"},"items":[]},
                {"id":"2","author":{"name":"Eva","key":"JIRAUSER4"},"items":[]}]""",
        )
        assertEquals(
            mapOf("JIRAUSER1" to "ana.silva", "JIRAUSER2" to "carla", "JIRAUSER3" to "dino", "JIRAUSER4" to "eva"),
            ChavesDeUsuario.coletar(issue, h),
        )
    }

    @Test
    fun ignoraChaveQueNaoEInternaObjetoIncompletoENomeVazio() {
        val issue = obj(
            """{"key":"TG-1","fields":{
                "assignee":{"name":"ana.silva","key":"ana.silva"},
                "reporter":{"key":"JIRAUSER2"},
                "creator":{"name":"  ","key":"JIRAUSER3"}}}""",
        )
        assertEquals(emptyMap(), ChavesDeUsuario.coletar(issue, emptyList()))
    }

    @Test
    fun issueSemFieldsNaoQuebra() {
        assertEquals(emptyMap(), ChavesDeUsuario.coletar(obj("""{"key":"TG-1"}"""), emptyList()))
    }
}
