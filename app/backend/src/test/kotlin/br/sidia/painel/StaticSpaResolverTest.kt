package br.sidia.painel

import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testa a resolução de caminhos do servidor estático, sem subir o servidor. */
class StaticSpaResolverTest {
    private val pai: Path = Files.createTempDirectory("painel-resolver")
    private val raiz: Path = pai.resolve("dist").createDirectories().toRealPath()

    @AfterTest
    fun limpar() {
        pai.toFile().deleteRecursively()
    }

    /** Cria um link simbólico; sem privilégio (Windows) o teste é ignorado, nunca fica verde por engano. */
    private fun linkOuIgnora(link: Path, alvo: Path) {
        try {
            Files.createSymbolicLink(link, alvo)
        } catch (e: java.nio.file.FileSystemException) {
            Assumptions.assumeTrue(false, "sem permissão para criar link simbólico")
        } catch (e: UnsupportedOperationException) {
            Assumptions.assumeTrue(false, "sistema de arquivos sem link simbólico")
        }
    }

    @Test
    fun symlinkParaForaDaRaizEhNulo() {
        val fora = pai.resolve("fora.txt").also { it.writeText("fora") }
        linkOuIgnora(raiz.resolve("atalho.txt"), fora)
        assertNull(resolverDentro(raiz, listOf("atalho.txt")))
    }

    @Test
    fun symlinkDentroDaRaizResolve() {
        val dentro = raiz.resolve("real.txt").also { it.writeText("dentro") }
        linkOuIgnora(raiz.resolve("atalho.txt"), dentro)
        assertEquals(raiz.resolve("atalho.txt"), resolverDentro(raiz, listOf("atalho.txt")))
    }

    @Test
    fun arquivoOcultoEhNulo() {
        raiz.resolve(".oculto").writeText("x")
        raiz.resolve("pasta").createDirectories().resolve(".oculto").writeText("x")
        raiz.resolve(".pasta").createDirectories().resolve("arquivo.txt").writeText("x")
        assertNull(resolverDentro(raiz, listOf(".oculto")))
        assertNull(resolverDentro(raiz, listOf("pasta", ".oculto")))
        assertNull(resolverDentro(raiz, listOf(".pasta", "arquivo.txt")))
    }

    @Test
    fun caminhoInvalidoEhNulo() {
        assertNull(resolverDentro(raiz, listOf("com\u0000nul")))
        assertNull(resolverDentro(raiz, listOf("..", "fora.txt")))
        assertNull(resolverDentro(raiz, listOf("nao-existe.txt")))
    }

    @Test
    fun arquivoComumResolve() {
        raiz.resolve("index.html").writeText("x")
        assertEquals(raiz.resolve("index.html"), resolverDentro(raiz, emptyList()))
    }
}
