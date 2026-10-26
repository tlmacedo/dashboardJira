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
    fun camposForaDaListaNaoGuardamTextoAntigoNemNovo() {
        val h = historicos(
            """[{"id":"7","created":"2026-10-03T10:00:00.000-0400","author":{"name":"Ana.Silva"},
                "items":[
                 {"field":"reporter","from":"u1","to":"u2","fromString":"Pessoa Exemplo Um","toString":"Pessoa Exemplo Dois"},
                 {"field":"description","fromString":"texto antigo livre","toString":"texto novo livre"},
                 {"field":"summary","fromString":"titulo antigo","toString":"titulo novo"},
                 {"field":"Comment","fromString":null,"toString":"comentario com dado pessoal"},
                 {"field":"status","from":"1","to":"3","fromString":"To Do","toString":"In Progress"},
                 {"field":"resolution","fromString":null,"toString":"Done"},
                 {"field":"duedate","fromString":"2026-10-01","toString":"2026-10-09"},
                 {"field":"labels","fromString":"","toString":"atraso_justificado"}
                ]}]""",
        )
        val porCampo = ChangelogParser.toEvents(issue(), h).associateBy { it.field }
        for (campo in listOf("reporter", "description", "summary", "Comment")) {
            val e = porCampo.getValue(campo)
            assertNull(e.from, campo)
            assertNull(e.to, campo)
            assertEquals("ana.silva", e.authorRef, campo)
        }
        assertEquals("In Progress", porCampo.getValue("status").to)
        assertEquals("Done", porCampo.getValue("resolution").to)
        assertEquals("2026-10-09", porCampo.getValue("duedate").to)
        assertEquals("atraso_justificado", porCampo.getValue("labels").to)
        // ids continuam estáveis por item (idempotência do sync)
        assertEquals(8, porCampo.values.map { it.changelogId }.toSet().size)
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

    private fun trocaDeResponsavel(de: String, para: String) = historicos(
        """[{"id":"7","created":"2026-10-02T10:00:00.000-0400","author":{"name":"x"},
            "items":[{"field":"assignee","from":"$de","to":"$para","fromString":"Nome De","toString":"Nome Para"}]}]""",
    )

    @Test
    fun chaveInternaTraduzidaPeloMapa() {
        val e = ChangelogParser.toEvents(
            issue(), trocaDeResponsavel("JIRAUSER10", "jirauser11"),
            mapOf("JIRAUSER10" to "ana.silva", "JIRAUSER11" to "bruno"),
        ).single()
        assertEquals("ana.silva", e.from)
        assertEquals("bruno", e.to)
    }

    @Test
    fun chaveInternaSemTraducaoFicaComOValorOriginal() {
        val e = ChangelogParser.toEvents(issue(), trocaDeResponsavel("JIRAUSER99", "Bruno")).single()
        assertEquals("JIRAUSER99", e.from) // sem normalizar: nunca casa com um username
        assertEquals("bruno", e.to)
    }

    @Test
    fun mapaNaoAlteraUsernameComum() {
        val e = ChangelogParser.toEvents(issue(), trocaDeResponsavel("Ana.Silva", "bruno"), mapOf("JIRAUSER1" to "x")).single()
        assertEquals("ana.silva", e.from)
        assertEquals("bruno", e.to)
    }
}
