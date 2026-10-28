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

    private val squad = listOf("dev.alfa", "dev.beta")
    private val periodo = mapOf("de" to "2026-07-26", "ate" to "2026-10-24")
    private val base: Map<String, Any> = mapOf("squad" to squad, "periodo" to periodo)

    private val jqlBase = "project in (\"TG-Basic\") AND issuetype in (\"Checklist\", \"Checklist TG\", \"Checklist Review\") " +
        "AND assignee in (\"dev.alfa\", \"dev.beta\") AND status not in (\"Cancelled\", \"Canceled\", \"Dropped\") " +
        "AND resolved >= \"2026-07-26\" AND resolved < \"2026-10-25\""

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
                "resolutiondate", "duedate", "labels", "parent", "project", "issuelinks",
                "OS Version", "SKU Type", "Checklist Type",
            ),
            q.fields,
        )
    }

    @Test
    fun fichaSincronizacaoPedeIssuelinks() {
        val fields = fabrica.build(fichaSync, mapOf("desde" to desde)).fields
        assertEquals(1, fields.count { it == "issuelinks" }, fields.toString())
        // A consulta leve (reconciliação) continua pedindo só a chave e o updated.
        assertEquals(listOf("key", "updated"), fabrica.buildLeve(fichaSync, mapOf("desde" to desde)).fields)
    }

    private val fichaSync get() = CardSpecs.fromYaml(TestPaths.configDir.resolve("cards/sincronizacao.card.yaml"), dict)
    private val desde = AtualizadoDesde(java.time.Instant.parse("2026-10-30T12:05:59Z"), java.time.ZoneId.of("America/Manaus"))
    private val jqlSync = "project in (\"TG-Basic\") AND issuetype in (\"Checklist\", \"Checklist TG\", " +
        "\"Checklist Review\", \"Feature check\", \"TG Feature check\", \"Task\") AND updated >= \"2026-10-30 08:05\""

    @Test
    fun fichaSincronizacaoGeraJqlEsperado() {
        val q = fabrica.build(fichaSync, mapOf("desde" to desde))
        assertEquals("$jqlSync ORDER BY updated ASC, key ASC", q.jql)
        assertTrue(q.expandChangelog)
        assertEquals("samsung", q.instance)
        // sem filtrosExcluidos: canceladas entram
        assertTrue("not in" !in q.jql)
    }

    private fun jqlDaFicha(id: String, params: Map<String, Any> = emptyMap()) =
        fabrica.build(CardSpecs.fromYaml(TestPaths.configDir.resolve("cards/$id.card.yaml"), dict), params).jql

    private val filtrosDeTrabalho = "project in (\"TG-Basic\") AND issuetype in (\"Checklist\", \"Checklist TG\", \"Checklist Review\")"

    @Test
    fun jqlSnapshotAbertas() {
        assertEquals(
            "$filtrosDeTrabalho AND status not in (\"Cancelled\", \"Canceled\", \"Dropped\")",
            jqlDaFicha("abertas"),
        )
        assertEquals(jqlDaFicha("abertas"), jqlDaFicha("negligenciadas"))
        // Os filtros opcionais entram quando informados.
        assertTrue("assignee in (\"dev.alfa\")" in jqlDaFicha("abertas", mapOf("squad" to listOf("dev.alfa"))))
    }

    @Test
    fun jqlSnapshotFechadasNaoExcluiCanceladas() {
        // As canceladas contam em "Canceladas" (FE-06): a ficha não tem filtro de exclusão de status.
        // Fechadas e Metas leem os mesmos 6 tipos da sincronização (o "no prazo" é o mesmo da Auditoria).
        val todosOsTipos = "project in (\"TG-Basic\") AND issuetype in (\"Checklist\", \"Checklist TG\", \"Checklist Review\", " +
            "\"Feature check\", \"TG Feature check\", \"Task\")"
        assertEquals(todosOsTipos, jqlDaFicha("fechadas"))
        assertEquals(todosOsTipos, jqlDaFicha("metas"))
        assertEquals(
            "$todosOsTipos AND resolved >= \"2026-08-01\" AND resolved < \"2026-10-31\"",
            jqlDaFicha("fechadas", mapOf("periodo" to mapOf("de" to "2026-08-01", "ate" to "2026-10-30"))),
        )
    }

    @Test
    fun fichaDeSincronizacaoPedeCamposNovos() {
        val fields = fabrica.build(fichaSync, mapOf("desde" to desde)).fields
        for (campo in listOf("priority", "timeestimate", "timespent", "timeoriginalestimate", "components")) {
            assertEquals(1, fields.count { it == campo }, "$campo ausente em $fields")
        }
    }

    @Test
    fun fichaSincronizacaoPedeCustomfieldQuandoHaId() {
        val d = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef(
                    "issuetype", FieldType.SELECT, "samsung", "x",
                    listOf("Checklist", "Checklist TG", "Checklist Review", "Feature check", "TG Feature check", "Task"),
                ),
                FieldDef("updated", FieldType.DATE, "samsung", "x"),
                FieldDef("key", FieldType.TEXT, "samsung", "x"),
                FieldDef("OS Version", FieldType.SELECT, "samsung", "x", customId = "cf[12416]"),
                FieldDef("SKU Type", FieldType.SELECT, "samsung", "x"),
                FieldDef("Checklist Type", FieldType.SELECT, "samsung", "x"),
                // Campos de trabalho que a ficha de sincronização também pede.
                FieldDef("priority", FieldType.SELECT, "samsung", "x"),
                FieldDef("timeoriginalestimate", FieldType.NUMBER, "samsung", "x"),
                FieldDef("timeestimate", FieldType.NUMBER, "samsung", "x"),
                FieldDef("timespent", FieldType.NUMBER, "samsung", "x"),
                FieldDef("components", FieldType.SELECT, "samsung", "x"),
                FieldDef("Story Points", FieldType.NUMBER, "samsung", "x"),
                FieldDef("Epic Link", FieldType.TEXT, "samsung", "x"),
                FieldDef("Flagged", FieldType.SELECT, "samsung", "x"),
            ),
        )
        val s = CardSpecs.fromYaml(TestPaths.configDir.resolve("cards/sincronizacao.card.yaml"), d)
        val fields = CardQueryFactory(d).build(s, mapOf("desde" to desde)).fields
        assertTrue("customfield_12416" in fields && "OS Version" !in fields, fields.toString())
    }

    @Test
    fun fichaComChavesAcrescentaKeyIn() {
        val q = fabrica.build(fichaSync, mapOf("desde" to desde, "chaves" to listOf("TG-1", "TG-20")))
        assertEquals("$jqlSync AND key in (\"TG-1\", \"TG-20\") ORDER BY updated ASC, key ASC", q.jql)
    }

    @Test
    fun parametrosDaSincronizacaoRejeitamValorRuim() {
        assertFailsWith<IllegalArgumentException> { fabrica.build(fichaSync, mapOf("desde" to "2026-10-30")) }
        assertFailsWith<IllegalArgumentException> { fabrica.build(fichaSync, emptyMap()) }
        assertFailsWith<InvalidJqlValueException> {
            fabrica.build(fichaSync, mapOf("desde" to desde, "chaves" to listOf("TG-1) or (1=1")))
        }
        assertFailsWith<IllegalArgumentException> { fabrica.build(fichaSync, mapOf("desde" to desde, "chaves" to "TG-1")) }
    }

    @Test
    fun buildLeveSoPedeKeyEUpdated() {
        val q = fabrica.buildLeve(fichaSync, mapOf("desde" to desde))
        assertEquals("$jqlSync ORDER BY updated ASC, key ASC", q.jql)
        assertEquals(listOf("key", "updated"), q.fields)
        assertEquals(false, q.expandChangelog)
    }

    @Test
    fun ordenarPorAtualizacaoExigeUpdatedEKeyNoDicionario() {
        val semKey = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("updated", FieldType.DATE, "samsung", "x"),
            ),
        )
        val f = fichaTemp(campos = "[]", extra = "ordenarPorAtualizacao: true\n")
        assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(f, semKey) }
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
        assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("squad" to "dev.alfa")) }
    }

    @Test
    fun injecaoEmSquadFalha() {
        assertFailsWith<InvalidJqlValueException> {
            fabrica.build(spec, base + ("squad" to listOf("a) OR project = X OR assignee in (b")))
        }
    }

    @Test
    fun listaAcimaDoLimiteERecusada() {
        val muitos = (1..201).map { "u$it" }
        val e = assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("squad" to muitos)) }
        assertTrue(e.message!!.contains("squad") && e.message!!.contains("200"), e.message)
        // no limite, ainda funciona
        fabrica.build(spec, base + ("squad" to (1..200).map { "u$it" }))
    }

    @Test
    fun osBemFormadoMasDesconhecidoFalhaPeloVocabulario() {
        val e = assertFailsWith<InvalidJqlValueException> {
            fabrica.build(spec, base + ("os" to listOf("Android 99.0 (OneUI_99)")))
        }
        assertEquals("literal", e.kind)
    }

    @Test
    fun osListaVaziaFalha() {
        assertFailsWith<IllegalArgumentException> { fabrica.build(spec, base + ("os" to emptyList<String>())) }
    }

    @Test
    fun periodoComDiaSemZeroFalha() {
        assertFailsWith<IllegalArgumentException> {
            fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to "2026-1-5", "ate" to "2026-10-24")))
        }
    }

    @Test
    fun periodoMisturaLocalDateEStringNosDoisExtremos() {
        val de = LocalDate.of(2026, 7, 26)
        val ate = LocalDate.of(2026, 10, 24)
        assertEquals(jqlBase, fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to de, "ate" to "2026-10-24"))).jql)
        assertEquals(jqlBase, fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to "2026-07-26", "ate" to ate))).jql)
    }

    private val parametrosPessoaETipo = "parametros:\n  - nome: p\n    tipo: pessoa\n  - nome: t\n    tipo: tipo\n"

    @Test
    fun pessoaETipoCaminhoFeliz() {
        val s = CardSpecs.fromYaml(fichaTemp(extra = parametrosPessoaETipo), dict)
        val q = fabrica.build(s, mapOf("p" to "dev.alfa", "t" to listOf("Checklist")))
        assertEquals("project in (\"TG-Basic\") AND assignee in (\"dev.alfa\") AND issuetype in (\"Checklist\")", q.jql)
    }

    @Test
    fun pessoaETipoComInjecaoFalham() {
        val s = CardSpecs.fromYaml(fichaTemp(extra = parametrosPessoaETipo), dict)
        assertFailsWith<InvalidJqlValueException> { fabrica.build(s, mapOf("p" to "a) OR x", "t" to listOf("Checklist"))) }
        assertFailsWith<InvalidJqlValueException> { fabrica.build(s, mapOf("p" to "ok", "t" to listOf("x\") OR (\"")) ) }
        assertFailsWith<IllegalArgumentException> { fabrica.build(s, mapOf("p" to listOf("ok"), "t" to listOf("Checklist"))) }
    }

    @Test
    fun filtroExcluidoForaDoDicionarioFalhaNaCarga() {
        assertFailsWith<UnknownFieldException> {
            CardSpecs.fromYaml(fichaTemp(extra = "filtrosExcluidos:\n  Inventado: [a]\n"), dict)
        }
    }

    @Test
    fun dicionarioSemResolvedFazACargaFalhar() {
        val semResolved = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("OS Version", FieldType.SELECT, "samsung", "x"),
            ),
        )
        val f = fichaTemp(extra = "parametros:\n  - nome: periodo\n    tipo: periodo\n")
        assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(f, semResolved) }
    }

    @Test
    fun camposComCaixaDiferenteSaemComNomeCanonico() {
        val s = CardSpecs.fromYaml(fichaTemp("[os version]"), dict)
        val fields = fabrica.build(s, emptyMap()).fields
        assertTrue("OS Version" in fields)
        assertTrue("os version" !in fields)
    }

    @Test
    fun campoAusenteCitaArquivoESecaoMantendoOTipo() {
        val e = assertFailsWith<UnknownFieldException> { CardSpecs.fromYaml(fichaTemp("[Inventado]"), dict) }
        assertEquals("Inventado", e.name)
        assertTrue(e.message!!.contains("x.card.yaml") && e.message!!.contains("campos"), e.message)
        val e2 = assertFailsWith<UnknownFieldException> {
            CardSpecs.fromYaml(fichaTemp(filtros = "Inventado: [a]"), dict)
        }
        assertTrue(e2.message!!.contains("filtrosFixos"), e2.message)
    }

    @Test
    fun ultimoDiaDoPeriodoEntraComoDiaSeguinteExclusivo() {
        fun jqlAte(ate: String) =
            fabrica.build(spec, mapOf("squad" to squad, "periodo" to mapOf("de" to "2026-07-26", "ate" to ate))).jql
        assertTrue(jqlAte("2026-12-31").endsWith("AND resolved < \"2027-01-01\""))
        assertFailsWith<IllegalArgumentException> { jqlAte("9999-12-31") }
    }

    @Test
    fun fichaSemFiltroFixoFalhaNaCargaCitandoArquivo() {
        val f = tmp.resolve("x.card.yaml")
        Files.writeString(f, "id: x\ntitulo: X\ninstancia: samsung\ncampos: []\nfiltrosFixos: {}\n")
        val e = assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, dict) }
        assertTrue(e.message!!.contains("x.card.yaml"), e.message)
    }

    @Test
    fun periodoApontandoParaCampoTextFalhaNaCarga() {
        val d = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("resolved", FieldType.TEXT, "samsung", "x"),
            ),
        )
        val f = fichaTemp("[]", extra = "parametros:\n  - nome: periodo\n    tipo: periodo\n")
        val e = assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, d) }
        assertTrue(e.message!!.contains("x.card.yaml") && e.message!!.contains("resolved"), e.message)
    }

    @Test
    fun squadApontandoParaCampoNaoUserNaoExiste() {
        // assignee é USER no exemplo; aqui um dicionário com assignee TEXT deve falhar na carga
        val d = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("assignee", FieldType.TEXT, "samsung", "x"),
            ),
        )
        val f = fichaTemp("[]", extra = "parametros:\n  - nome: s\n    tipo: squad\n")
        assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, d) }
    }

    @Test
    fun valorDeFiltroFixoForaDosConhecidosFalhaNaCarga() {
        val e = assertFailsWith<IllegalArgumentException> {
            CardSpecs.fromYaml(fichaTemp(filtros = "issuetype: [\"Inventado\"]"), dict)
        }
        assertTrue(e.message!!.contains("x.card.yaml"), e.message)
    }

    @Test
    fun valorDeFiltroExcluidoForaDosConhecidosFalhaNaCarga() {
        assertFailsWith<IllegalArgumentException> {
            CardSpecs.fromYaml(fichaTemp(extra = "filtrosExcluidos:\n  status: [\"Inventado\"]\n"), dict)
        }
    }

    @Test
    fun tipoEValidadoContraOsTiposDoDicionario() {
        val s = CardSpecs.fromYaml(fichaTemp(extra = parametrosPessoaETipo), dict)
        assertFailsWith<InvalidJqlValueException> { fabrica.build(s, mapOf("p" to "dev.alfa", "t" to listOf("Inventado"))) }
    }

    private fun dicionarioComCampo(nome: String, tipo: FieldType) = FieldDictionary(
        listOf(
            FieldDef("project", FieldType.SELECT, "samsung", "x"),
            FieldDef(nome, tipo, "samsung", "x"),
        ),
    )

    private fun parametroDoTipo(tipo: String) = "parametros:\n  - nome: p\n    tipo: $tipo\n"

    @Test
    fun osApontandoParaCampoDateOuUserFalhaNaCarga() {
        for (tipo in listOf(FieldType.DATE, FieldType.USER)) {
            val f = fichaTemp("[]", extra = parametroDoTipo("os"))
            val e = assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, dicionarioComCampo("OS Version", tipo)) }
            assertTrue(e.message!!.contains("x.card.yaml") && e.message!!.contains("OS Version"), e.message)
        }
    }

    @Test
    fun tipoApontandoParaCampoDateOuUserFalhaNaCarga() {
        for (tipo in listOf(FieldType.DATE, FieldType.USER)) {
            val f = fichaTemp("[]", extra = parametroDoTipo("tipo"))
            val e = assertFailsWith<IllegalArgumentException> { CardSpecs.fromYaml(f, dicionarioComCampo("issuetype", tipo)) }
            assertTrue(e.message!!.contains("x.card.yaml") && e.message!!.contains("issuetype"), e.message)
        }
    }

    @Test
    fun osETipoApontandoParaCampoTextOuSelectCarregam() {
        for (tipo in listOf(FieldType.TEXT, FieldType.SELECT)) {
            CardSpecs.fromYaml(fichaTemp("[]", extra = parametroDoTipo("os")), dicionarioComCampo("OS Version", tipo))
            CardSpecs.fromYaml(fichaTemp("[]", extra = parametroDoTipo("tipo")), dicionarioComCampo("issuetype", tipo))
        }
    }

    @Test
    fun fieldsUsaCustomfieldNoLugarDeNomeECfN() {
        val d = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("OS Version", FieldType.SELECT, "samsung", "x", customId = "cf[12416]"),
                FieldDef("SKU Type", FieldType.TEXT, "samsung", "x", customId = "customfield_10001"),
                FieldDef("Checklist Type", FieldType.TEXT, "samsung", "x"),
            ),
        )
        val s = CardSpecs.fromYaml(fichaTemp("[OS Version, SKU Type, Checklist Type]"), d)
        val fields = CardQueryFactory(d).build(s, emptyMap()).fields
        assertEquals(
            listOf(
                "summary", "issuetype", "status", "assignee", "reporter", "created", "updated",
                "resolutiondate", "duedate", "labels", "parent", "project", "issuelinks",
                "customfield_12416", "customfield_10001", "Checklist Type",
            ),
            fields,
        )
        assertTrue("cf[12416]" !in fields && "OS Version" !in fields)
    }

    @Test
    fun fieldsSemDuplicatasMantendoOsMinimos() {
        val d = FieldDictionary(
            listOf(
                FieldDef("project", FieldType.SELECT, "samsung", "x"),
                FieldDef("OS Version", FieldType.SELECT, "samsung", "x", customId = "cf[12416]"),
                FieldDef("Outro", FieldType.TEXT, "samsung", "x", customId = "customfield_12416"),
                FieldDef("labels", FieldType.LABELS, "samsung", "x"),
            ),
        )
        val s = CardSpecs.fromYaml(fichaTemp("[OS Version, os version, Outro, labels, Labels]"), d)
        val fields = CardQueryFactory(d).build(s, emptyMap()).fields
        assertEquals(fields.size, fields.map { it.lowercase() }.toSet().size, fields.toString())
        assertEquals(1, fields.count { it == "customfield_12416" })
        assertEquals(1, fields.count { it.equals("labels", ignoreCase = true) })
        assertTrue(fields.containsAll(listOf("summary", "project", "parent", "duedate")))
    }

    @Test
    fun injecaoEmOsFalha() {
        assertFailsWith<InvalidJqlValueException> {
            fabrica.build(spec, base + ("os" to listOf("x\") OR project = \"Y")))
        }
    }
}
