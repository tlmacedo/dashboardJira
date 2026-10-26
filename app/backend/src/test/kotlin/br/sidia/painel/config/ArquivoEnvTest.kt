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
    fun ambienteDoProcessoVenceOArquivoMasVazioNao() {
        val r = ArquivoEnv.combinar(mapOf("A" to "arq", "B" to "arq"), mapOf("A" to "env", "B" to ""))
        assertEquals("env", r["A"])
        assertEquals("arq", r["B"])
    }
}
