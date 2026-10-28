package br.sidia.painel.db

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Garante um único painel por arquivo de banco: a trava do sistema operacional em `<banco>.lock` vale enquanto o
 * processo viver (e some sozinha se ele morrer). Sem isto, dois painéis sincronizariam o mesmo Jira ao mesmo tempo.
 */
class TravaDeProcesso private constructor(private val canal: FileChannel, private val trava: FileLock) : AutoCloseable {
    /** Verdadeiro enquanto esta instância ainda detém a trava do sistema operacional. */
    val valida: Boolean get() = trava.isValid && canal.isOpen

    override fun close() {
        runCatching { trava.release() }
        runCatching { canal.close() }
    }

    companion object {
        /** Lança [IllegalStateException] (sem caminho nem dados) se outro painel já usa o mesmo banco. */
        fun adquirir(arquivoDoBanco: Path): TravaDeProcesso {
            val arquivoLock = arquivoDoBanco.toAbsolutePath().resolveSibling(arquivoDoBanco.fileName.toString() + ".lock")
            arquivoLock.parent?.let { Files.createDirectories(it) }
            val canal = FileChannel.open(arquivoLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val trava = try {
                canal.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            } catch (e: Exception) {
                runCatching { canal.close() }
                throw e
            }
            if (trava == null) {
                runCatching { canal.close() }
                throw IllegalStateException("Já existe outro painel usando este banco. Feche-o antes de abrir este.")
            }
            return TravaDeProcesso(canal, trava)
        }
    }
}
