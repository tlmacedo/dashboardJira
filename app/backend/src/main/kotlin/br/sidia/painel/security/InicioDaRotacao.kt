package br.sidia.painel.security

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant

/**
 * Guarda em arquivo QUANDO a rotação do `SESSION_SECRET` começou, para que reiniciar o painel não reabra a janela em que o
 * segredo anterior ainda vale (sem isto, cada reinício contaria 8 horas de novo).
 *
 * O arquivo (`<banco>.rotacao`, ao lado do banco, fora do pacote) tem duas linhas: a impressão digital SHA-256 do segredo
 * ANTERIOR e o instante de início em segundos. Se a impressão digital muda (uma nova rotação), o início volta a ser agora;
 * se a variável do segredo anterior sai, o arquivo é apagado. A impressão digital de um segredo aleatório de 32 bytes
 * não o revela. Falha de leitura ou escrita nunca derruba a subida: cai no comportamento antigo (início = agora) e avisa.
 */
object InicioDaRotacao {
    private val log = LoggerFactory.getLogger(InicioDaRotacao::class.java)

    /** O arquivo de estado da rotação que fica ao lado do [banco]. */
    fun arquivoAoLadoDe(banco: Path): Path = banco.resolveSibling(banco.fileName.toString() + ".rotacao")

    /** Início da rotação em curso; `null` quando não há segredo [anterior] (e o arquivo, se existir, é apagado). */
    fun resolver(arquivo: Path, anterior: ByteArray?, agora: Instant): Instant? {
        if (anterior == null) {
            runCatching { Files.deleteIfExists(arquivo) }
            return null
        }
        val impressao = hex(MessageDigest.getInstance("SHA-256").digest(anterior))
        lerSePertencer(arquivo, impressao)?.let { return it }
        gravar(arquivo, impressao, agora)
        return agora
    }

    private fun lerSePertencer(arquivo: Path, impressao: String): Instant? =
        try {
            if (!Files.isRegularFile(arquivo)) {
                null
            } else {
                val linhas = Files.readAllLines(arquivo)
                if (linhas.size >= 2 && linhas[0].trim() == impressao) Instant.ofEpochSecond(linhas[1].trim().toLong()) else null
            }
        } catch (e: Exception) {
            log.warn("Arquivo da rotação do segredo da sessão ilegível ({}); a janela recomeça agora", e.javaClass.simpleName)
            null
        }

    private fun gravar(arquivo: Path, impressao: String, agora: Instant) {
        try {
            arquivo.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            val temporario = arquivo.resolveSibling(arquivo.fileName.toString() + ".tmp")
            Files.writeString(temporario, impressao + "\n" + agora.epochSecond + "\n")
            runCatching { Files.setPosixFilePermissions(temporario, PosixFilePermissions.fromString("rw-------")) }
            Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            log.warn("Não foi possível gravar o início da rotação do segredo da sessão ({}); a janela reabre a cada reinício", e.javaClass.simpleName)
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
