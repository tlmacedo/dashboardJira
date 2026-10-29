package br.sidia.painel.ci

import br.sidia.painel.TestPaths
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O build do backend precisa de memória própria: com o padrão, `compileTestKotlin` estoura ("Not enough memory to run
 * compilation") conforme a suíte cresce. O arquivo versionado garante isso no CI e em qualquer máquina, sem `-D` manual.
 */
class GradlePropertiesTest {
    private val texto = Files.readString(TestPaths.repoDir.resolve("app/backend/gradle.properties"))
    private val linhas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.associate {
        it.substringBefore('=') to it.substringAfter('=')
    }

    private fun megabytes(valor: String): Int {
        val m = Regex("-Xmx(\\d+)([mMgG])").find(valor) ?: error("sem -Xmx em $valor")
        val n = m.groupValues[1].toInt()
        return if (m.groupValues[2].lowercase() == "g") n * 1024 else n
    }

    @Test
    fun memoriaSuficienteParaOGradleEParaOCompiladorKotlin() {
        assertTrue(megabytes(linhas.getValue("org.gradle.jvmargs")) >= 3072)
        assertTrue(megabytes(linhas.getValue("kotlin.daemon.jvmargs")) >= 3072)
    }

    @Test
    fun semCaminhoLocalNemSegredo() {
        assertFalse(Regex("[A-Za-z]:\\\\|/home/|/Users/|/root/|org\\.gradle\\.java\\.home").containsMatchIn(texto), "caminho local no gradle.properties")
        assertFalse(Regex("(?i)password|secret|token|proxyPassword").containsMatchIn(linhas.keys.joinToString("\n")))
        assertEquals(setOf("org.gradle.jvmargs", "kotlin.daemon.jvmargs"), linhas.keys, "só a memória; o resto fica como estava")
    }

    @Test
    fun ciDocumentaOArquivo() {
        val ci = Files.readString(TestPaths.repoDir.resolve("docs/operacao/ci.md"))
        assertTrue("gradle.properties" in ci && "Not enough memory" in ci)
    }
}
