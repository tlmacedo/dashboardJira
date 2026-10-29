package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O leitor do worklog do Data Center: só id, autor, início e segundos; nunca comentário. Nenhuma rede. */
class WorklogParserTest {
    private fun lista(json: String): List<JsonObject> = (Json.parseToJsonElement(json) as JsonArray).map { it.jsonObject }

    private fun w(
        id: String? = "70001", autor: String? = """{"name":"Ana.Silva"}""", started: String? = "2026-10-05T09:00:00.000-0400",
        segundos: String? = "3600", extra: String = "",
    ): String = buildString {
        append("{")
        val partes = mutableListOf<String>()
        id?.let { partes += """"id":"$it"""" }
        autor?.let { partes += """"author":$it""" }
        started?.let { partes += """"started":"$it"""" }
        segundos?.let { partes += """"timeSpentSeconds":$it""" }
        if (extra.isNotEmpty()) partes += extra
        append(partes.joinToString(","))
        append("}")
    }

    @Test
    fun lePrimeiroRegistroCompleto() {
        val r = WorklogParser.toRecords(lista("[${w()}]"), emptyMap()).single()
        assertEquals(WorklogRecord("70001", "ana.silva", Instant.parse("2026-10-05T13:00:00Z"), 3600), r)
    }

    @Test
    fun worklogSemComentario() {
        // O texto do comentário, o updateAuthor e o self existem no JSON e NÃO podem aparecer em nada que o leitor devolve.
        val json = w(extra = """"comment":"COMENTARIO-SECRETO","updateAuthor":{"name":"outra.pessoa","displayName":"Nome Completo"},"self":"https://jira.exemplo.invalid/x"""")
        val registros = WorklogParser.toRecords(lista("[$json]"), emptyMap())
        val texto = registros.toString()
        assertFalse("COMENTARIO-SECRETO" in texto || "outra.pessoa" in texto || "Nome Completo" in texto || "exemplo.invalid" in texto, texto)
        // O registro só tem os cinco campos permitidos (a data de criação entrou no Plano 11; comentário nunca).
        assertEquals(setOf("jiraId", "autor", "started", "seconds", "criado"), WorklogRecord::class.java.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun worklogGuardaCriacao() {
        val r = WorklogParser.toRecords(lista("[${w(extra = """"created":"2026-10-20T15:00:00.000-0400"""")}]"), emptyMap()).single()
        assertEquals(Instant.parse("2026-10-20T19:00:00Z"), r.criado)
        assertEquals(Instant.parse("2026-10-05T13:00:00Z"), r.started)
    }

    @Test
    fun worklogSemCriacaoOuIlegivelFicaNuloSemPerderAsHoras() {
        assertNull(WorklogParser.toRecords(lista("[${w()}]"), emptyMap()).single().criado)
        val ruim = WorklogParser.toRecords(lista("[${w(extra = """"created":"ontem à tarde"""")}]"), emptyMap()).single()
        assertNull(ruim.criado)
        assertEquals(3600, ruim.seconds)
    }

    @Test
    fun autorJirauserTraduzido() {
        val chaves = mapOf("JIRAUSER10500" to "ana.silva")
        val porNome = WorklogParser.toRecords(lista("[${w(autor = """{"name":"JIRAUSER10500","key":"JIRAUSER10500"}""")}]"), chaves).single()
        assertEquals("ana.silva", porNome.autor)
        // Sem nome, a chave interna do usuário também é traduzida.
        val porChave = WorklogParser.toRecords(lista("[${w(autor = """{"key":"jirauser10500"}""")}]"), chaves).single()
        assertEquals("ana.silva", porChave.autor)
        // Sem tradução a chave fica como chave, em maiúsculas (nunca casa com um username), sem exceção.
        val semTraducao = WorklogParser.toRecords(lista("[${w(autor = """{"name":"JIRAUSER99"}""")}]"), chaves).single()
        assertEquals("JIRAUSER99", semTraducao.autor)
    }

    @Test
    fun autorAusenteFicaNulo() {
        assertNull(WorklogParser.toRecords(lista("[${w(autor = null)}]"), emptyMap()).single().autor)
        assertNull(WorklogParser.toRecords(lista("""[${w(autor = """{"name":"  "}""")}]"""), emptyMap()).single().autor)
    }

    @Test
    fun registroMalformadoEhIgnoradoSemExcecao() {
        val lixo = listOf(
            w(id = null), w(started = null), w(started = "ontem"), w(segundos = null), w(segundos = "-5"), w(segundos = "\"muito\""),
            w(id = "  "),
        )
        val bom = w(id = "70002")
        val registros = WorklogParser.toRecords(lista("[${(lixo + bom).joinToString(",")}]"), emptyMap())
        assertEquals(listOf("70002"), registros.map { it.jiraId })
    }

    @Test
    fun idRepetidoFicaUmaVez() {
        val registros = WorklogParser.toRecords(lista("[${w(id = "7")},${w(id = "7", segundos = "60")}]"), emptyMap())
        assertEquals(1, registros.size)
    }

    @Test
    fun listaVaziaEOrdemPorInicio() {
        assertTrue(WorklogParser.toRecords(emptyList(), emptyMap()).isEmpty())
        val registros = WorklogParser.toRecords(
            lista("[${w(id = "2", started = "2026-10-07T09:00:00.000-0400")},${w(id = "1", started = "2026-10-05T09:00:00.000-0400")}]"),
            emptyMap(),
        )
        assertEquals(listOf("1", "2"), registros.map { it.jiraId })
    }
}
