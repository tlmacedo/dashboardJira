package br.sidia.painel.operacao

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Destino de uma mudança de estado de alerta (ativou ou resolveu). Implementações nunca lançam para quem avalia. */
interface SaidaDeAlertas {
    fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant)
}

/**
 * Anexa uma linha de texto por mudança de estado: `2026-10-30T08:00:00-04:00 ATIVO CRITICO SYNC_SUSPENSA samsung` (a instância
 * é omitida quando não há). Nada de dado pessoal nem de caminho. Passando de [LIMITE_BYTES], o arquivo vira `<arquivo>.1`
 * (substitui o anterior) e um novo começa. Falha de escrita só registra a CLASSE da exceção: o alerta continua nas outras saídas.
 */
class ArquivoDeAlertas(private val arquivo: Path, private val fuso: ZoneId) : SaidaDeAlertas {
    @Synchronized
    override fun mudou(alerta: Alerta, ativo: Boolean, quando: Instant) {
        try {
            arquivo.toAbsolutePath().parent?.let { Files.createDirectories(it) }
            if (Files.exists(arquivo) && Files.size(arquivo) > LIMITE_BYTES) {
                Files.move(arquivo, arquivo.resolveSibling(arquivo.fileName.toString() + ".1"), StandardCopyOption.REPLACE_EXISTING)
            }
            val hora = FORMATO.format(quando.atZone(fuso).toOffsetDateTime().withNano(0))
            val linha = buildString {
                append(hora).append(' ').append(if (ativo) "ATIVO" else "RESOLVIDO").append(' ')
                append(alerta.gravidade.name).append(' ').append(alerta.codigo.name)
                alerta.instancia?.let { append(' ').append(it) }
                append('\n')
            }
            Files.writeString(arquivo, linha, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            log.warn("Não foi possível gravar no arquivo de alertas ({})", e.javaClass.simpleName)
        }
    }

    companion object {
        const val LIMITE_BYTES = 5L * 1024 * 1024
        private val FORMATO = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        private val log = LoggerFactory.getLogger(ArquivoDeAlertas::class.java)
    }
}
