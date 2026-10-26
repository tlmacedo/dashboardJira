package br.sidia.painel.jql

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JqlValuesTest {
    private val injecao = "x\") OR project is not EMPTY OR (\""

    // --- datas ---

    @Test
    fun dataValida() = assertEquals("2026-10-24", JqlValues.isoDate("2026-10-24"))

    @Test
    fun dataImpossivel() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.isoDate("2026-13-45") }
        assertFailsWith<InvalidJqlValueException> { JqlValues.isoDate("2026-02-30") }
    }

    @Test
    fun dataComTexto() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.isoDate("ontem") }
    }

    @Test
    fun dataRejeitaFormatosAlternativos() {
        for (v in listOf("+2026-10-24", "2026-1-5", " 2026-10-24", "2026-10-24 ", "2026-10-24\n", "", "26-10-24", "٢٠٢٦-١٠-٢٤")) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar '$v'") { JqlValues.isoDate(v) }
        }
    }

    // --- username ---

    @Test
    fun usernameValido() {
        assertEquals("dev.alfa", JqlValues.username("dev.alfa"))
        assertEquals("dev.gama", JqlValues.username("dev.gama"))
        assertEquals("dev.gama-e", JqlValues.username("dev.gama-e"))
    }

    @Test
    fun usernameComEspaco() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("dev alfa") }
    }

    @Test
    fun usernameComAspas() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("x\" OR \"") }
    }

    @Test
    fun usernameVazio() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("") }
    }

    @Test
    fun usernameRejeitaMaiusculaHifenInicialE65Caracteres() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("Thiago") }
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("-thiago") }
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("a".repeat(65)) }
        assertEquals("a".repeat(64), JqlValues.username("a".repeat(64)))
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("thiago\n") }
    }

    @Test
    fun usernameRejeitaUnicodeEstranho() {
        for (v in listOf("thi​ago", "thi‮ago", "thi ago", "thi\u0000ago", "thíago")) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar unicode") { JqlValues.username(v) }
        }
    }

    // --- literal ---

    private val permitidos = listOf("Android 16.0 (16)", "Android 17.0 (OneUI_9.0)")

    @Test
    fun literalForaDaListaLanca() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.literal("Android 99", permitidos) }
        assertFailsWith<InvalidJqlValueException> { JqlValues.literal("android 16.0 (16)", permitidos) }
        assertFailsWith<InvalidJqlValueException> { JqlValues.literal(injecao, permitidos) }
    }

    @Test
    fun literalDentroDaListaVoltaComAspas() {
        assertEquals("\"Android 16.0 (16)\"", JqlValues.literal("Android 16.0 (16)", permitidos))
    }

    // --- relativeDays ---

    @Test
    fun relativeDays() {
        assertEquals("startOfDay(-100)", JqlValues.relativeDays(100))
        assertEquals("startOfDay(-1)", JqlValues.relativeDays(1))
        assertEquals("startOfDay(-3650)", JqlValues.relativeDays(3650))
        for (n in listOf(0, -5, 3651, 4000, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar $n") { JqlValues.relativeDays(n) }
        }
    }

    // --- nomes de campo ---

    @Test
    fun campoComEspacoVaiEntreAspas() {
        assertEquals("\"OS Version\"", JqlValues.quotedField("OS Version"))
        assertEquals("cf[12416]", JqlValues.quotedField("cf[12416]"))
        assertFailsWith<InvalidJqlValueException> { JqlValues.quotedField("a\"b") }
    }

    @Test
    fun campoSimpleEPorIdSaemSemAspas() {
        assertEquals("status", JqlValues.quotedField("status"))
        assertEquals("Sprint_2", JqlValues.quotedField("Sprint_2"))
        assertEquals("customfield_12345", JqlValues.quotedField("customfield_12345"))
        assertEquals("\"Data-Limite\"", JqlValues.quotedField("Data-Limite"))
        assertEquals("\"Descrição\"", JqlValues.quotedField("Descrição"))
    }

    @Test
    fun campoRejeitaPerigosos() {
        for (v in listOf("", "  ", "a\\b", "a\nb", "a\u0000b", "a\tb", "a​b")) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar '$v'") { JqlValues.quotedField(v) }
        }
        assertFailsWith<InvalidJqlValueException> { JqlValues.quotedField(injecao) }
    }

    // --- texto livre ---

    @Test
    fun safeTextRejeitaAspasEBarra() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.safeText(injecao) }
        assertFailsWith<InvalidJqlValueException> { JqlValues.safeText("a\\b") }
        assertEquals("\"Android 17.0 (OneUI_9.0)\"", JqlValues.safeText("Android 17.0 (OneUI_9.0)"))
    }

    @Test
    fun safeTextRejeitaControleEBranco() {
        for (v in listOf("", "   ", "a\nb", "a\rb", "a\tb", "a\u0000b", "a\u001Fb", "a\u007Fb")) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar controle/branco") { JqlValues.safeText(v) }
        }
    }

    @Test
    fun safeTextRejeitaUnicodeEstranho() {
        for (v in listOf("a​b", "a‎b", "a‮b", "a⁦b", "a﻿b", "a b", "a b", "a b")) {
            assertFailsWith<InvalidJqlValueException>("deveria rejeitar unicode estranho") { JqlValues.safeText(v) }
        }
        assertEquals("\"Descrição\"", JqlValues.safeText("Descrição"))
    }

    @Test
    fun safeTextLimitaA120Caracteres() {
        assertEquals("\"" + "a".repeat(120) + "\"", JqlValues.safeText("a".repeat(120)))
        assertFailsWith<InvalidJqlValueException> { JqlValues.safeText("a".repeat(121)) }
    }

    // --- desempenho ---

    @Test
    fun entradaGiganteFalhaRapido() {
        val grande = "a".repeat(100_000)
        val ms = measureTimeMillis {
            assertFailsWith<InvalidJqlValueException> { JqlValues.isoDate(grande) }
            assertFailsWith<InvalidJqlValueException> { JqlValues.username(grande) }
            assertFailsWith<InvalidJqlValueException> { JqlValues.safeText(grande) }
            assertFailsWith<InvalidJqlValueException> { JqlValues.quotedField(grande) }
            assertFailsWith<InvalidJqlValueException> { JqlValues.literal(grande, permitidos) }
        }
        assertTrue(ms < 1000, "demorou ${ms}ms")
    }

    // --- exceção ---

    @Test
    fun excecaoGuardaTipoEValor() {
        val e = assertFailsWith<InvalidJqlValueException> { JqlValues.username("x y") }
        assertEquals("username", e.kind)
        assertEquals("x y", e.value)
    }

    @Test
    fun invalidJqlValueExceptionEhIllegalArgument() {
        val e: Throwable = assertFailsWith<IllegalArgumentException> { JqlValues.username("x y") }
        assertTrue(e is InvalidJqlValueException)
        assertEquals("Valor inválido para username: x y", e.message)
    }

    @Test
    fun mensagemSemQuebraDeLinhaNemControle() {
        val e = assertFailsWith<InvalidJqlValueException> { JqlValues.username("a\nb\r\tc\u0000d") }
        assertEquals("Valor inválido para username: a?b??c?d", e.message)
        assertEquals("a\nb\r\tc\u0000d", e.value)
    }

    @Test
    fun aspasTipograficasECrasesSaoRecusadas() {
        listOf("“", "”", "‘", "’", "＂", "＇", "`", "´").forEach { q ->
            assertFailsWith<InvalidJqlValueException>("U+%04X".format(q[0].code)) { JqlValues.safeText("a${q}b") }
            assertFailsWith<InvalidJqlValueException> { JqlValues.literal("a${q}b", listOf("a${q}b")) }
            assertFailsWith<InvalidJqlValueException> { JqlValues.quotedField("Campo ${q}x") }
        }
    }

    @Test
    fun amostraNaoCortaParSubstituto() {
        val emoji = "😀"
        val v = "a".repeat(79) + emoji + "zzz"
        val a = amostra(v)
        assertEquals(79, a.length)
        assertTrue(a.none { it.isSurrogate() })
        // emoji inteiro dentro do limite continua inteiro
        assertEquals("a".repeat(78) + emoji, amostra("a".repeat(78) + emoji + "zzz"))
        assertEquals("a?b", amostra("a\nb"))
    }

    @Test
    fun mensagemTruncaValorGigante() {
        val e = assertFailsWith<InvalidJqlValueException> { JqlValues.username("A".repeat(100_000)) }
        assertEquals("Valor inválido para username: ".length + 80, e.message!!.length)
    }

    @Test
    fun dataHoraNoFusoDoServidor() {
        val instante = java.time.Instant.parse("2026-10-30T12:05:59Z")
        assertEquals("2026-10-30 08:05", JqlValues.jqlDateTime(instante, java.time.ZoneId.of("America/Manaus")))
        assertEquals("2026-10-30 12:05", JqlValues.jqlDateTime(instante, java.time.ZoneOffset.UTC))
    }

    @Test
    fun chaveDeIssueAceitaSoOFormatoDoJira() {
        assertEquals("TG-1", JqlValues.issueKey("TG-1"))
        assertEquals("TG_BASIC2-123456789", JqlValues.issueKey("TG_BASIC2-123456789"))
        listOf("tg-1", "TG-0", "TG-01", "TG-", "-1", "TG-1) or (1=1", "TG-1234567890", "1TG-1", "TG--1", "A-B-1", "", "TG-1\n")
            .forEach { assertFailsWith<InvalidJqlValueException>(it) { JqlValues.issueKey(it) } }
        // projeto com mais de 20 caracteres é recusado; com 20 passa
        assertFailsWith<InvalidJqlValueException> { JqlValues.issueKey("A".repeat(21) + "-1") }
        assertEquals("A".repeat(20) + "-1", JqlValues.issueKey("A".repeat(20) + "-1"))
    }
}
