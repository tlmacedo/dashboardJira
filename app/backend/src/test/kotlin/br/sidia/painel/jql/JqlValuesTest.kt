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
        assertEquals("thiago.macedo", JqlValues.username("thiago.macedo"))
        assertEquals("pedro.almeida-e", JqlValues.username("pedro.almeida-e"))
    }

    @Test
    fun usernameComEspaco() {
        assertFailsWith<InvalidJqlValueException> { JqlValues.username("thiago macedo") }
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
    fun mensagemTruncaValorGigante() {
        val e = assertFailsWith<InvalidJqlValueException> { JqlValues.username("A".repeat(100_000)) }
        assertEquals("Valor inválido para username: ".length + 80, e.message!!.length)
    }
}
