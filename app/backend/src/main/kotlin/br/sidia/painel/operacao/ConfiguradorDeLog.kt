package br.sidia.painel.operacao

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.pattern.ThrowableProxyConverter
import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * O Logback inicia ANTES de o painel ler o arquivo de variáveis (o `main` só combina ambiente e arquivo depois). Por isso
 * o formato e a pasta de log vindos desse arquivo seriam ignorados em silêncio. [aplicar] grava as duas propriedades no
 * contexto do Logback, zera a configuração e relê o `logback.xml`. As poucas linhas anteriores saem no formato padrão (JSON).
 */
object ConfiguradorDeLog {
    private const val FORMATO_TEXTO = "TEXTO"
    private const val FORMATO_JSON = "JSON"

    /**
     * [formato]: `JSON` (padrão) ou `TEXTO`; qualquer outro valor cai em JSON. [dir]: pasta do `painel.log` (com rotação)
     * ou nulo (só saída padrão). Pasta que não pode ser criada ou escrita: aviso (sem o caminho) e segue só com a saída padrão.
     */
    fun aplicar(formato: String?, dir: Path?) {
        val contexto = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val fmt = if (formato?.trim()?.uppercase() == FORMATO_TEXTO) FORMATO_TEXTO else FORMATO_JSON
        val pasta = dir?.let { prepararPasta(it) }
        val configuracao = ConfiguradorDeLog::class.java.classLoader.getResource("logback.xml") ?: return
        contexto.reset() // limpa também as propriedades: elas são gravadas depois
        contexto.putProperty("PAINEL_LOG_FORMATO", fmt)
        if (pasta != null) {
            contexto.putProperty("PAINEL_LOG_DIR", pasta.toAbsolutePath().toString().replace('\\', '/'))
            contexto.putProperty("PAINEL_LOG_ARQUIVO_REF", "ARQUIVO")
        }
        JoranConfigurator().also { it.context = contexto }.doConfigure(configuracao)
    }

    private fun prepararPasta(dir: Path): Path? =
        try {
            Files.createDirectories(dir)
            if (Files.isDirectory(dir) && Files.isWritable(dir)) dir else null
        } catch (e: Exception) {
            null
        }.also {
            if (it == null) System.err.println("AVISO: a pasta de log (PAINEL_LOG_DIR) não pode ser usada; o log segue só na saída padrão.")
        }
}

/** Mensagem do formato TEXTO: passa pela máscara e não deixa quebra de linha forjar outra entrada. */
class MensagemSeguraConverter : ClassicConverter() {
    override fun convert(event: ILoggingEvent): String =
        MascaraDeLog.mascarar(event.formattedMessage ?: "").replace("\r", "\\r").replace("\n", "\\n")
}

/** Exceção do formato TEXTO: a pilha impressa também passa pela máscara. */
class ExcecaoSeguraConverter : ThrowableProxyConverter() {
    override fun convert(event: ILoggingEvent): String = MascaraDeLog.mascarar(super.convert(event))
}
