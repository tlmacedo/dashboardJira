package br.sidia.painel.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testes do parser do changelog do DC, sem rede nem banco. */
class ChangelogParserTest {

    private fun obj(texto: String): JsonObject = Json.parseToJsonElement(texto).jsonObject

    private fun issue(assignee: String = "ana.silva") = obj(
        """{"key":"TG-9","fields":{"summary":"x","issuetype":{"name":"Task"},"status":{"name":"In Progress"},
            "assignee":{"name":"$assignee"},"created":"2026-10-01T09:00:00.000-0400","labels":[]}}""",
    )

    private fun historicos(texto: String) = Json.parseToJsonElement(texto).jsonArray.map { it.jsonObject }

    @Test
    fun statusViraTransicao() {
        val h = historicos(
            """[{"id":"1","created":"2026-10-02T10:00:00.000-0400","author":{"name":"Ana.Silva"},
                "items":[{"field":"status","from":"1","to":"3","fromString":"To Do","toString":"In Progress"}]}]""",
        )
        val t = ChangelogParser.toTimeline(issue(), h)
        val tr = t.transitions.single()
        assertEquals("To Do", tr.from)
        assertEquals("In Progress", tr.to)
        assertEquals(Instant.parse("2026-10-02T14:00:00Z"), tr.at)
        assertEquals("ana.silva", tr.authorRef)
        assertEquals("To Do", t.initialStatus)
    }

    @Test
    fun assigneeComFromToEUsaUsername() {
        val h = historicos(
            """[{"id":"2","created":"2026-10-02T10:00:00.000-0400","author":{"name":"x"},
                "items":[{"field":"assignee","from":"ana.silva","to":"bruno","fromString":"Ana Silva","toString":"Bruno Lima"}]}]""",
        )
        val t = ChangelogParser.toTimeline(issue(), h)
        assertEquals("bruno", t.assigneeChanges.single().to)
        assertEquals("ana.silva", t.initialAssignee)
        val e = ChangelogParser.toEvents(issue(), h).single()
        assertEquals("ana.silva", e.from)
        assertEquals("bruno", e.to)
    }

    @Test
    fun assigneeSoComStringsUsaNomeDeExibicao() {
        val h = historicos(
            """[{"id":"3","created":"2026-10-02T10:00:00.000-0400","author":{"name":"x"},
                "items":[{"field":"assignee","from":null,"to":null,"fromString":"Ana Silva","toString":"Bruno Lima"}]}]""",
        )
        val t = ChangelogParser.toTimeline(issue(), h)
        assertEquals("bruno lima", t.assigneeChanges.single().to)
        assertEquals("ana silva", t.initialAssignee)
    }

    @Test
    fun assigneeRemovidoViraNulo() {
        val h = historicos(
            """[{"id":"4","created":"2026-10-02T10:00:00.000-0400","author":{"name":"x"},
                "items":[{"field":"assignee","from":"ana.silva","to":null,"fromString":"Ana Silva","toString":null}]}]""",
        )
        assertNull(ChangelogParser.toTimeline(issue(), h).assigneeChanges.single().to)
    }

    @Test
    fun datasComOffsetViramInstant() {
        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), ChangelogParser.parseInstant("2026-10-05T08:00:00.000-0400"))
        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), ChangelogParser.parseInstant("2026-10-05T08:00:00.000-04:00"))
        assertEquals(Instant.parse("2026-10-05T08:00:00Z"), ChangelogParser.parseInstant("2026-10-05T08:00:00Z"))
        assertEquals(Instant.parse("2026-10-05T08:00:00Z"), ChangelogParser.parseInstant("2026-10-05T08:00:00.000+0000"))
    }

    @Test
    fun idDoEventoEUnicoPorItem() {
        val h = historicos(
            """[{"id":"5","created":"2026-10-02T10:00:00.000-0400","items":[
                {"field":"labels","fromString":"","toString":"a"},{"field":"labels","fromString":"a","toString":"a b"}]}]""",
        )
        val ids = ChangelogParser.toEvents(issue(), h).map { it.changelogId }
        assertEquals(2, ids.toSet().size)
    }
}
