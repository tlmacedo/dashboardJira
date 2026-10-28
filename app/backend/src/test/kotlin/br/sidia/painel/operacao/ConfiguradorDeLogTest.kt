package br.sidia.painel.operacao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reconfigura o Logback global; cada teste devolve a configuração padrão (JSON, só saída padrão) no fim. */
@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ConfiguradorDeLogTest {
    private val saidaOriginal: PrintStream = System.out
    private val capturada = ByteArrayOutputStream()
    private val pastas = mutableListOf<Path>()

    @BeforeEach
    fun capturar() {
        System.setOut(PrintStream(capturada, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restaurar() {
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(saidaOriginal)
        pastas.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun linhas(): List<String> = capturada.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
    private fun novaPasta(): Path = Files.createTempDirectory("painel-log-").also { pastas.add(it) }

    @Test
    fun configuradorAplicaFormatoTexto() {
        ConfiguradorDeLog.aplicar("TEXTO", null)
        capturada.reset()
        LoggerFactory.getLogger("br.sidia.painel.teste").info("linha de texto")
        val linha = linhas().single { "linha de texto" in it }
        assertFalse(linha.startsWith("{"), linha)
        assertTrue("INFO" in linha, linha)
    }

    @Test
    fun formatoTextoMascaraSegredoEImpedeQuebraDeLinha() {
        ConfiguradorDeLog.aplicar("TEXTO", null)
        capturada.reset()
        LoggerFactory.getLogger("br.sidia.painel.teste").info("usou Bearer abcdefghijklmnop\nINFO falso")
        val saida = capturada.toString(Charsets.UTF_8)
        assertFalse("abcdefghijklmnop" in saida, saida)
        assertEquals(1, linhas().size, saida)
    }

    @Test
    fun formatoInvalidoCaiEmJson() {
        ConfiguradorDeLog.aplicar("XML", null)
        capturada.reset()
        LoggerFactory.getLogger("br.sidia.painel.teste").info("linha json")
        assertTrue(linhas().single { "linha json" in it }.startsWith("{"))
    }

    @Test
    fun configuradorGravaArquivoComRotacao() {
        val pasta = novaPasta()
        ConfiguradorDeLog.aplicar("JSON", pasta)
        LoggerFactory.getLogger("br.sidia.painel.teste").info("vai para o arquivo")
        val arquivo = pasta.resolve("painel.log")
        assertTrue(Files.isRegularFile(arquivo), "painel.log deveria existir")
        val linhasDoArquivo = Files.readAllLines(arquivo).filter { it.isNotBlank() }
        assertTrue(linhasDoArquivo.any { it.startsWith("{") && "vai para o arquivo" in it }, linhasDoArquivo.toString())
        // A saída padrão continua recebendo o mesmo evento.
        assertTrue(linhas().any { "vai para o arquivo" in it })
    }

    @Test
    fun semPastaNaoCriaArquivo() {
        ConfiguradorDeLog.aplicar("JSON", null)
        LoggerFactory.getLogger("br.sidia.painel.teste").info("so na saida padrao")
        assertFalse(Files.exists(Path.of(System.getProperty("java.io.tmpdir")).resolve("painel.log")))
    }

    @Test
    fun pastaImpossivelNaoDerruba() {
        val arquivoComum = Files.createTempFile("painel-log-arquivo-", ".txt")
        try {
            ConfiguradorDeLog.aplicar("JSON", arquivoComum.resolve("dentro"))
            capturada.reset()
            LoggerFactory.getLogger("br.sidia.painel.teste").info("continua na saida")
            assertTrue(linhas().any { "continua na saida" in it })
        } finally {
            Files.deleteIfExists(arquivoComum)
        }
    }
}
