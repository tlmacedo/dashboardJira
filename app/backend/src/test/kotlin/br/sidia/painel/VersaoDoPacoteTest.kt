package br.sidia.painel

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plano 13, Tarefa 5: a versão do pacote vem do recurso `versao.properties` gerado pelo Gradle. */
class VersaoDoPacoteTest {
    private fun ler(texto: String?) = VersaoDoPacote.ler(texto?.byteInputStream())

    @Test
    fun semRecursoVolta0_0_0Dev() {
        assertEquals(Versao("0.0.0-dev", "desconhecido", ""), ler(null))
    }

    @Test
    fun leOsTresCampos() {
        val v = ler("versao=2026.11.1\ncommit=abc1234\nconstruidaEm=2026-11-03T12:00:00Z\n")
        assertEquals(Versao("2026.11.1", "abc1234", "2026-11-03T12:00:00Z"), v)
    }

    @Test
    fun campoAusenteOuVazioUsaOPadrao() {
        assertEquals(Versao("2026.11.1", "desconhecido", ""), ler("versao=2026.11.1\ncommit=  \n"))
        assertEquals(Versao("0.0.0-dev", "desconhecido", ""), ler(""))
    }

    @Test
    fun rotuloComV() {
        assertEquals("v2026.11.1", Versao("2026.11.1", "abc1234", "x").rotulo)
    }

    @Test
    fun rotuloDevParaBuildLocal() {
        assertEquals("dev", Versao("0.0.0-dev", "abc1234", "").rotulo)
    }

    @Test
    fun oRecursoDoClasspathExiste() {
        // O Gradle gera build/generated/versao/versao.properties e o põe nos recursos do main.
        assertTrue(VersaoDoPacote::class.java.getResource("/versao.properties") != null, "versao.properties não está no classpath")
        assertTrue(VersaoDoPacote.atual.versao.isNotBlank())
    }

    @Test
    fun buildValidaFormatoDaVersao() {
        val gradle = Files.readString(TestPaths.repoDir.resolve("app/backend/build.gradle.kts"))
        assertTrue("""^\d{4}\.(0[1-9]|1[0-2])\.[1-9]\d*$""" in gradle, "build.gradle.kts sem a expressão da versão AAAA.MM.N")
        assertTrue("0.0.0-dev" in gradle)
        assertTrue("gerarVersaoProperties" in gradle)
        assertFalse(Regex("(?m)^version = \"\\d").containsMatchIn(gradle), "a versão não pode mais ser fixa no build")
    }
}
