package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SanitizadorRawTest {
    private fun sanitizar(json: String): JsonObject =
        SanitizadorRaw.sanitizar(Json.parseToJsonElement(json)).jsonObject

    @Test
    fun projetoNaoEhUsuarioEPreservaKeyENome() {
        val r = sanitizar(
            """{"fields":{"project":{"self":"https://j/p","id":"1","key":"TG","name":"Time Basic",
            |"avatarUrls":{"48x48":"https://j/a.png"}}}}""".trimMargin(),
        )
        val p = r["fields"]!!.jsonObject["project"]!!.jsonObject
        assertEquals("TG", p["key"]?.jsonPrimitive?.content)
        // O nome do projeto não é normalizado como username (continua com a caixa original).
        assertEquals("Time Basic", p["name"]?.jsonPrimitive?.content)
        assertFalse("avatarUrls" in p || "self" in p)
    }

    @Test
    fun usuarioPerdeDadosPessoaisENormalizaName() {
        val r = sanitizar(
            """{"fields":{"assignee":{"self":"x","name":"Ana.Silva","key":"JIRAUSER123","displayName":"Ana Silva",
            |"emailAddress":"ana@exemplo.invalid","accountId":"abc","timeZone":"America/Manaus",
            |"avatarUrls":{"16x16":"u"},"active":true}}}""".trimMargin(),
        )
        val a = r["fields"]!!.jsonObject["assignee"]!!.jsonObject
        assertEquals("ana.silva", a["name"]?.jsonPrimitive?.content)
        assertEquals(setOf("name", "active"), a.keys)
    }

    @Test
    fun camposDePessoaConhecidosSaoUsuarioMesmoSoComNameEKey() {
        val r = sanitizar("""{"fields":{"reporter":{"name":"BRUNO","key":"JIRAUSER9"}},"changelog":{"histories":[{"author":{"name":"Carla","key":"K"}}]}}""")
        val rep = r["fields"]!!.jsonObject["reporter"]!!.jsonObject
        assertEquals(setOf("name"), rep.keys)
        assertEquals("bruno", rep["name"]?.jsonPrimitive?.content)
        val autor = r["changelog"]!!.jsonObject["histories"].toString()
        assertTrue("\"carla\"" in autor && "\"K\"" !in autor, autor)
    }

    @Test
    fun objetoComEmailOuAccountIdEhUsuarioEmQualquerCampo() {
        val r = sanitizar("""{"fields":{"customfield_1":{"name":"Dev.UM","key":"JU1","emailAddress":"d@x.invalid"},"customfield_2":{"name":"Dev.DOIS","accountId":"z"}}}""")
        val f = r["fields"]!!.jsonObject
        assertEquals("dev.um", f["customfield_1"]!!.jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals(setOf("name"), f["customfield_1"]!!.jsonObject.keys)
        assertEquals(setOf("name"), f["customfield_2"]!!.jsonObject.keys)
    }

    @Test
    fun objetoComNameEKeyForaDeCampoDePessoaNaoMudaDeCaixa() {
        // Sem marca de usuário (status, resolução, tipo): nada é normalizado nem removido.
        val r = sanitizar("""{"fields":{"status":{"name":"Em Andamento","id":"3"},"issuetype":{"name":"Task"},"x":{"key":"TG","name":"Abc"}}}""")
        val f = r["fields"]!!.jsonObject
        assertEquals("Em Andamento", f["status"]!!.jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("TG", f["x"]!!.jsonObject["key"]?.jsonPrimitive?.content)
        assertEquals("Abc", f["x"]!!.jsonObject["name"]?.jsonPrimitive?.content)
    }
}
