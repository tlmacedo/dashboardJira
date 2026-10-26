package br.sidia.painel.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ArquivoEnvTest {
    @Test
    fun interpretaComentariosAspasExportEBom() {
        val m = ArquivoEnv.interpretar(
            listOf("﻿# comentário", "", "A=1", "export B = dois ", "C=\"com # hash\"", "D='x'", "E=valor # nota", "inválido", "1X=2", "F="),
        )
        assertEquals(mapOf("A" to "1", "B" to "dois", "C" to "com # hash", "D" to "x", "E" to "valor", "F" to ""), m)
    }

    @Test
    fun flagDoJiraVemSoDoArquivo() {
        val arq = mapOf("JIRA_LIVE_ENABLED" to "true", "X" to "a")
        assertEquals("true", ArquivoEnv.combinarComFlagSoDoArquivo(arq, mapOf("JIRA_LIVE_ENABLED" to "false"))["JIRA_LIVE_ENABLED"])
        val sem = ArquivoEnv.combinarComFlagSoDoArquivo(mapOf("X" to "a"), mapOf("JIRA_LIVE_ENABLED" to "true", "Y" to "b"))
        assertEquals(null, sem["JIRA_LIVE_ENABLED"])
        assertEquals("b", sem["Y"])
        val desligada = ArquivoEnv.combinarComFlagSoDoArquivo(mapOf("JIRA_LIVE_ENABLED" to "false"), mapOf("JIRA_LIVE_ENABLED" to "true"))
        assertEquals("false", desligada["JIRA_LIVE_ENABLED"])
    }

    @Test
    fun ambienteDoProcessoVenceOArquivoMasVazioNao() {
        val r = ArquivoEnv.combinar(mapOf("A" to "arq", "B" to "arq"), mapOf("A" to "env", "B" to ""))
        assertEquals("env", r["A"])
        assertEquals("arq", r["B"])
    }
}
