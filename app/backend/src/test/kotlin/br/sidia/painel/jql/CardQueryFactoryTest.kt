package br.sidia.painel.jql

import br.sidia.painel.TestPaths
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardQueryFactoryTest {
    @TempDir
    lateinit var tmp: Path

    private val dict = FieldDictionary.fromYaml(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"))
    private val fichaPath = TestPaths.configDir.resolve("cards/tempo-em-status.card.yaml")
    private val spec get() = CardSpecs.fromYaml(fichaPath, dict)
    private val fabrica = CardQueryFactory(dict)

    private val squad = listOf("thiago.macedo", "wendell.marques")
    private val periodo = mapOf("de" to "2026-07-26", "ate" to "2026-10-24")
    private val base: Map<String, Any> = mapOf("squad" to squad, "periodo" to periodo)

    private val jqlBase = "project in (\"TG-Basic\") AND issuetype in (\"Checklist\", \"Checklist TG\", \"Checklist Review\") " +
        "AND assignee in (thiago.macedo, wendell.marques) AND status not in (\"Cancelled\", \"Canceled\", \"Dropped\") " +
        "AND resolved >= \"2026-07-26\" AND resolved <= \"2026-10-24\""

    private fun fichaTemp(campos: String = "[OS Version]", filtros: String = "project: [\"TG-Basic\"]", extra: String = ""): Path {
        val f = tmp.resolve("x.card.yaml")
        Files.writeString(
            f,
            "id: x\ntitulo: X\ninstancia: samsung\ncampos: $campos\nfiltrosFixos:\n  $filtros\n$extra",
        )
        return f
    }

    @Test
    fun fichaDoTempoEmStatusCarrega() {
        val s = spec
        assertEquals("tempo-em-status", s.id)
        assertEquals("samsung", s.instancia)
        assertTrue(s.parametros.map { it.name }.containsAll(listOf("squad", "periodo")))
        assertEquals(false, s.parametros.single { it.name == "os" }.required)
        assertTrue(s.expandChangelog)
    }

    @Test
    fun fichaComCampoForaDoDicionarioFalha() {
        assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(fichaTemp("[Inventado]"), dict) }
    }

    @Test
    fun filtroFixoForaDoDicionarioFalhaNaCarga() {
        assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(fichaTemp(filtros = "Inventado: [a]"), dict) }
    }

    @Test
    fun campoDeOutraInstanciaFalhaNaCarga() {
        val outro = FieldDictionary(
            listOf(FieldDef("project", FieldType.SELECT, "samsung", "x"), FieldDef("Extra", FieldType.TEXT, "outra", "x")),
        )
        assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(fichaTemp("[Extra]"), outro) }
    }

    @Test
    fun chaveDesconhecidaNaFichaFalhaCitandoArquivoEChave() {
        val f = fichaTemp(extra = "chaveInventada: 1\n")
        val e = assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, dict) }
        assertTrue(e.message!!.contains("x.card.yaml") && e.message!!.contains("chaveInventada"), e.message)
    }

    @Test
    fun jqlSnapshotTempoEmStatus() {
        val q = fabrica.build(spec, base)
        assertEquals(jqlBase, q.jql)
        assertEquals("samsung", q.instance)
        assertTrue(q.expandChangelog)
        assertEquals(
            listOf(
                "summary", "issuetype", "status", "assignee", "reporter", "created", "updated",
                "resolutiondate", "duedate", "labels", "parent", "project", "OS Version", "SKU Type", "Checklist Type",
            ),
            q.fields,
        )
    }

    @Test
    fun periodoAceitaLocalDateEPar() {
        val a = fabrica.build(spec, mapOf("squad" to squad, "periodo" to Periodo(LocalDate.of(2026, 7, 26), LocalDate.of(2026, 10, 24))))
        val b = fabrica.build(spec, mapOf("squad" to squad, "periodo" to (LocalDate.of(2026, 7, 26) to LocalDate.of(2026, 10, 24))))
        assertEquals(jqlBase, a.jql)
        assertEquals(jqlBase, b.jql)
    }

    @Test
    fun osOpcionalEntraQuandoInformado() {
        val q = fabrica.build(spec, base + ("os" to listOf("Android 17.0 (OneUI_9.0)")))
        assertEquals("$jqlBase AND \"OS Version\" in (\"Android 17.0 (OneUI_9.0)\")", q.jql)
    }

    @Test
    fun parametroObrigatorioAusenteFalha() {
        val e = assertFailsWith<IllegalArgumentException> { fabrica.build(spec, mapOf("squad" to squad)) }
        assertTrue(e.message!!.contains("periodo"))
    }

    @Test
    fun parametroDesconhecidoERecusado() {
        val e = assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("jql" to "x")) }
        assertTrue(e.message!!.contains("jql"))
    }

    @Test
    fun periodoInvertidoERecusado() {
        assertFailsWith<IllegalArgumentException> {
            fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to "2026-10-24", "ate" to "2026-07-26")))
        }
    }

    @Test
    fun periodoComDataInvalidaERecusado() {
        assertFailsWith<IllegalArgumentException> {
            fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to "2026-02-30", "ate" to "2026-03-01")))
        }
    }

    @Test
    fun squadVazioERecusado() {
        assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("squad" to emptyList<String>())) }
    }

    @Test
    fun squadComoTextoSoltoERecusado() {
        assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("squad" to "thiago.macedo")) }
    }

    @Test
    fun injecaoEmSquadFalha() {
        assertFailsWith<InvalidJqlValueException> {
            fabrica.build(spec, base + ("squad" to listOf("a) OR project = X OR assignee in (b")))
        }
    }

    @Test
    fun injecaoEmOsFalha() {
        assertFailsWith<InvalidJqlValueException> {
            fabrica.build(spec, base + ("os" to listOf("x\") OR project = \"Y")))
        }
    }
}
