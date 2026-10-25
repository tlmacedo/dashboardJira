package br.sidia.painel.jql

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JqlBuilderTest {
    private val os = listOf("Android 16.0 (16)", "Android 17.0 (OneUI_9.0)")

    // Dicionário mínimo montado em memória; sem arquivos nem caminhos relativos.
    private val dict = FieldDictionary(
        listOf(
            FieldDef("project", FieldType.SELECT, "samsung", "projeto"),
            FieldDef("assignee", FieldType.USER, "samsung", "responsável"),
            FieldDef("status", FieldType.SELECT, "samsung", "status"),
            FieldDef("created", FieldType.DATE, "samsung", "criação"),
            FieldDef("resolved", FieldType.DATE, "samsung", "resolução"),
            FieldDef("duedate", FieldType.DATE, "samsung", "vencimento"),
            FieldDef("labels", FieldType.LABELS, "samsung", "labels"),
            FieldDef("summary", FieldType.TEXT, "samsung", "resumo"),
            FieldDef("Story Points", FieldType.NUMBER, "samsung", "pontos"),
            FieldDef("OS Version", FieldType.SELECT, "samsung", "versão do SO", os),
            FieldDef("Campo Sidia", FieldType.TEXT, "sidia", "só da sidia"),
        ),
    )

    private fun builder() = JqlBuilder(dict, "samsung")

    @Test
    fun jqlDoSquadAbertas() {
        val jql = builder()
            .inList("project", listOf("TG-Basic"))
            .users("assignee", listOf("dev.alfa", "dev.beta"))
            .notInList("status", listOf("Resolved", "Closed", "Cancelled"))
            .relativeDaysAtLeast("created", 100)
            .build()
        assertEquals(
            "project in (\"TG-Basic\") AND assignee in (\"dev.alfa\", \"dev.beta\") AND " +
                "status not in (\"Resolved\", \"Closed\", \"Cancelled\") AND created >= startOfDay(-100)",
            jql,
        )
    }

    @Test
    fun usernamesReservadosSaemEntreAspas() {
        assertEquals("assignee in (\"empty\")", builder().users("assignee", listOf("empty")).build())
        assertEquals("assignee in (\"or\")", builder().users("assignee", listOf("or")).build())
    }

    @Test
    fun dateBeforeUsaMenorQueEExigeCampoDate() {
        assertEquals("resolved < \"2026-10-25\"", builder().dateBefore("resolved", "2026-10-25").build())
        assertFailsWith<IllegalArgumentException> { builder().dateBefore("project", "2026-10-25") }
        assertFailsWith<InvalidJqlValueException> { builder().dateBefore("resolved", "x\") OR (\"") }
        assertFailsWith<UnknownFieldException> { builder().dateBefore("nada", "2026-10-25") }
    }

    @Test
    fun campoComEspaco() {
        assertEquals(
            "\"OS Version\" in (\"Android 17.0 (OneUI_9.0)\")",
            builder().inList("OS Version", listOf("Android 17.0 (OneUI_9.0)")).build(),
        )
    }

    @Test
    fun nomeDoCampoSaiCanonicoEDatasEntreAspas() {
        val jql = builder()
            .equalsTo("PROJECT", "TG-Basic")
            .dateAtLeast("CREATED", "2026-01-31")
            .dateAtMost("resolved", "2026-02-28")
            .labelsNotIn(listOf("atraso_justificado"))
            .build()
        assertEquals(
            "project = \"TG-Basic\" AND created >= \"2026-01-31\" AND resolved <= \"2026-02-28\" AND " +
                "labels not in (\"atraso_justificado\")",
            jql,
        )
        assertEquals("\"OS Version\" = \"Android 16.0 (16)\"", builder().equalsTo("os version", "Android 16.0 (16)").build())
    }

    @Test
    fun ordenacao() {
        val jql = builder()
            .inList("project", listOf("TG-Basic"))
            .orderBy("resolved")
            .orderBy("duedate", asc = false)
            .build()
        assertTrue(jql.endsWith(" ORDER BY resolved ASC, duedate DESC"), jql)
        assertEquals(
            "project in (\"TG-Basic\") ORDER BY resolved ASC, duedate DESC",
            jql,
        )
    }

    @Test
    fun orderByForaDoDicionarioFalha() {
        assertFailsWith<UnknownFieldException> { builder().inList("project", listOf("A")).orderBy("nada") }
    }

    @Test
    fun tentativaDeInjecaoFalha() {
        val ataque = "x\") OR project is not EMPTY OR (\""
        assertFailsWith<InvalidJqlValueException> { builder().users("assignee", listOf(ataque)) }
        assertFailsWith<InvalidJqlValueException> { builder().inList("project", listOf(ataque)) }
        assertFailsWith<InvalidJqlValueException> { builder().notInList("status", listOf(ataque)) }
        assertFailsWith<InvalidJqlValueException> { builder().equalsTo("project", ataque) }
        assertFailsWith<InvalidJqlValueException> { builder().equalsTo("summary", ataque) }
        assertFailsWith<InvalidJqlValueException> { builder().inList("OS Version", listOf(ataque)) }
        assertFailsWith<InvalidJqlValueException> { builder().dateAtLeast("created", ataque) }
        assertFailsWith<InvalidJqlValueException> { builder().dateAtMost("created", ataque) }
        assertFailsWith<InvalidJqlValueException> { builder().labelsNotIn(listOf(ataque)) }
    }

    @Test
    fun listaVaziaFalha() {
        val e = assertFailsWith<IllegalArgumentException> { builder().inList("project", emptyList()) }
        assertEquals("lista vazia para project", e.message)
        assertFailsWith<IllegalArgumentException> { builder().notInList("status", emptyList()) }
        assertFailsWith<IllegalArgumentException> { builder().users("assignee", emptyList()) }
        assertFailsWith<IllegalArgumentException> { builder().labelsNotIn(emptyList()) }
    }

    @Test
    fun campoForaDoDicionarioFalha() {
        assertFailsWith<UnknownFieldException> { builder().inList("inexistente", listOf("A")) }
        assertFailsWith<UnknownFieldException> { builder().equalsTo("inexistente", "A") }
        assertFailsWith<UnknownFieldException> { builder().dateAtLeast("inexistente", "2026-01-01") }
    }

    @Test
    fun valorForaDosConhecidosFalha() {
        assertFailsWith<InvalidJqlValueException> { builder().inList("OS Version", listOf("Android 99")) }
        assertFailsWith<InvalidJqlValueException> { builder().equalsTo("OS Version", "Android 99") }
    }

    @Test
    fun tipoIncompativelFalha() {
        val e1 = assertFailsWith<IllegalArgumentException> { builder().users("project", listOf("a.b")) }
        assertTrue(e1.message!!.contains("project") && e1.message!!.contains("SELECT"), e1.message)
        assertFailsWith<IllegalArgumentException> { builder().dateAtLeast("project", "2026-01-01") }
        assertFailsWith<IllegalArgumentException> { builder().dateAtMost("assignee", "2026-01-01") }
        assertFailsWith<IllegalArgumentException> { builder().relativeDaysAtLeast("status", 10) }
        assertFailsWith<IllegalArgumentException> { builder().inList("created", listOf("2026-01-01")) }
        assertFailsWith<IllegalArgumentException> { builder().notInList("Story Points", listOf("3")) }
        assertFailsWith<IllegalArgumentException> { builder().equalsTo("duedate", "2026-01-01") }
        assertFailsWith<IllegalArgumentException> { builder().equalsTo("Story Points", "3") }
    }

    @Test
    fun labelsNotInExigeCampoLabelsDoTipoLabels() {
        val semLabels = FieldDictionary(listOf(FieldDef("project", FieldType.SELECT, "samsung", "p")))
        assertFailsWith<UnknownFieldException> { JqlBuilder(semLabels, "samsung").labelsNotIn(listOf("a")) }
        val labelsTexto = FieldDictionary(listOf(FieldDef("labels", FieldType.TEXT, "samsung", "p")))
        assertFailsWith<IllegalArgumentException> { JqlBuilder(labelsTexto, "samsung").labelsNotIn(listOf("a")) }
    }

    @Test
    fun instanciaNaoEnxergaCampoDeOutra() {
        assertFailsWith<UnknownFieldException> { builder().equalsTo("Campo Sidia", "x") }
        val jql = JqlBuilder(dict, "sidia").equalsTo("Campo Sidia", "x").build()
        assertEquals("\"Campo Sidia\" = \"x\"", jql)
        assertFailsWith<UnknownFieldException> { JqlBuilder(dict, "sidia").equalsTo("project", "x") }
    }

    @Test
    fun buildSemClausulaFalha() {
        assertFailsWith<IllegalStateException> { builder().build() }
        assertFailsWith<IllegalStateException> { builder().orderBy("resolved").build() }
    }

    @Test
    fun valoresDeTextoLivreComParentesesPassam() {
        assertEquals(
            "summary = \"Falha (intermitente) no login\"",
            builder().equalsTo("summary", "Falha (intermitente) no login").build(),
        )
    }

    @Test
    fun semOperadorDeBuscaDeTexto() {
        val jql = builder().equalsTo("summary", "abc*").build()
        assertTrue(!jql.contains("~"), jql)
    }

    @Test
    fun propriedadeCaracteresPerigososNuncaQuebramAsAspas() {
        val rnd = Random(42)
        val pedacos = listOf(
            "\"", ")", "(", "\\", "~", "*", " ", "\n", "\t", "'", "x", "0", "a", ".", "-", "_",
            "“", "”", "‘", "’", "＂", "＇", "`", "´",
            "or", "empty", "null", "order", "in", "OR", "AND",
        )
        val tipograficas = "“”‘’＂＇`´"
        val aceitos = mutableMapOf<String, Int>()
        repeat(600) {
            val valor = (1..rnd.nextInt(1, 4)).joinToString("") { pedacos[rnd.nextInt(pedacos.size)] }
            // Para a data, metade dos casos usa uma data válida, senão nenhum seria aceito.
            val data = if (rnd.nextBoolean()) "2026-01-%02d".format(rnd.nextInt(1, 29)) else valor
            val tentativas = mapOf<String, () -> String>(
                "inList" to { builder().inList("project", listOf(valor)).build() },
                "notInList" to { builder().notInList("status", listOf(valor, "ok")).build() },
                "equalsTo" to { builder().equalsTo("summary", valor).build() },
                "users" to { builder().users("assignee", listOf(valor)).build() },
                "labelsNotIn" to { builder().labelsNotIn(listOf(valor)).build() },
                "dateAtLeast" to { builder().dateAtLeast("created", data).build() },
                "dateBefore" to { builder().dateBefore("created", data).build() },
            )
            for ((nome, gerar) in tentativas) {
                val saida = try {
                    gerar()
                } catch (e: InvalidJqlValueException) {
                    continue
                }
                aceitos.merge(nome, 1, Int::plus)
                assertTrue(saida.count { it == '"' } % 2 == 0, "aspas ímpares: $saida")
                assertTrue(!saida.contains('\\'), "barra na saída: $saida")
                // '~' só pode aparecer como dado dentro de aspas, nunca como operador (partes pares ficam fora das aspas).
                val fora = saida.split('"').filterIndexed { i, _ -> i % 2 == 0 }.joinToString("")
                assertTrue(!fora.contains('~'), "operador ~ na saída: $saida")
                assertTrue(saida.none { it.isISOControl() }, saida)
                assertTrue(saida.none { it in tipograficas }, "aspa tipográfica na saída: $saida")
            }
        }
        listOf("inList", "notInList", "equalsTo", "users", "labelsNotIn", "dateAtLeast", "dateBefore").forEach {
            assertTrue((aceitos[it] ?: 0) > 0, "gerador $it nunca teve caso aceito")
        }
    }
}
