package br.sidia.painel.operacao

import org.junit.jupiter.api.AfterEach
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class SaidaDeAlertasTest {
    private val pastas = mutableListOf<Path>()
    private val manaus = ZoneId.of("America/Manaus")
    private val quando = Instant.parse("2026-10-30T12:00:00Z")
    private val saidaOriginal: PrintStream = System.out

    @AfterEach
    fun limpar() {
        System.setOut(saidaOriginal)
        pastas.forEach { runCatching { it.deleteRecursively() } }
    }

    private fun pasta(): Path = Files.createTempDirectory("painel-alertas-").also { pastas.add(it) }

    @Test
    fun linhaDeTextoSemDadoPessoal() {
        val arquivo = pasta().resolve("alertas.log")
        val saida = ArquivoDeAlertas(arquivo, manaus)
        saida.mudou(Alerta(CodigoAlerta.SYNC_SUSPENSA, GravidadeAlerta.CRITICO, "samsung", quando), ativo = true, quando)
        saida.mudou(Alerta(CodigoAlerta.DISCO_BAIXO, GravidadeAlerta.CRITICO, null, quando), ativo = true, quando)
        saida.mudou(Alerta(CodigoAlerta.SYNC_SUSPENSA, GravidadeAlerta.CRITICO, "samsung", quando), ativo = false, quando.plusSeconds(90))
        assertEquals(
            listOf(
                "2026-10-30T08:00:00-04:00 ATIVO CRITICO SYNC_SUSPENSA samsung",
                "2026-10-30T08:00:00-04:00 ATIVO CRITICO DISCO_BAIXO",
                "2026-10-30T08:01:30-04:00 RESOLVIDO CRITICO SYNC_SUSPENSA samsung",
            ),
            Files.readAllLines(arquivo),
        )
    }

    @Test
    fun rotacionaAcimaDe5Mb() {
        val arquivo = pasta().resolve("alertas.log")
        Files.write(arquivo, ByteArray((ArquivoDeAlertas.LIMITE_BYTES + 10).toInt()) { 'x'.code.toByte() })
        val saida = ArquivoDeAlertas(arquivo, manaus)
        saida.mudou(Alerta(CodigoAlerta.DISCO_BAIXO, GravidadeAlerta.CRITICO, null, quando), true, quando)
        val antigo = arquivo.resolveSibling("alertas.log.1")
        assertTrue(Files.size(antigo) > ArquivoDeAlertas.LIMITE_BYTES, "o conteúdo antigo foi para .1")
        assertEquals(listOf("2026-10-30T08:00:00-04:00 ATIVO CRITICO DISCO_BAIXO"), Files.readAllLines(arquivo))
        // Uma segunda rotação substitui o .1 anterior (não acumula).
        Files.write(arquivo, ByteArray((ArquivoDeAlertas.LIMITE_BYTES + 10).toInt()) { 'y'.code.toByte() })
        saida.mudou(Alerta(CodigoAlerta.DISCO_BAIXO, GravidadeAlerta.CRITICO, null, quando), false, quando)
        assertEquals('y'.code.toByte(), Files.readAllBytes(antigo)[0])
    }

    @Test
    fun falhaDeEscritaNaoDerrubaAvaliacao() {
        val saidaCapturada = ByteArrayOutputStream()
        ConfiguradorDeLog.aplicar("JSON", null)
        System.setOut(PrintStream(saidaCapturada, true, Charsets.UTF_8))
        val arquivoComum = Files.createTempFile("painel-alertas-arquivo-", ".txt")
        try {
            // O "diretório" do arquivo de alertas é um arquivo comum: a escrita é impossível.
            val saida = ArquivoDeAlertas(arquivoComum.resolve("dentro/alertas.log"), manaus)
            saida.mudou(Alerta(CodigoAlerta.DISCO_BAIXO, GravidadeAlerta.CRITICO, null, quando), true, quando)
            val log = saidaCapturada.toString(Charsets.UTF_8)
            assertTrue("Não foi possível gravar no arquivo de alertas" in log, log)
            assertFalse(arquivoComum.fileName.toString() in log, "o caminho não pode aparecer no log")
        } finally {
            Files.deleteIfExists(arquivoComum)
        }
    }
}
