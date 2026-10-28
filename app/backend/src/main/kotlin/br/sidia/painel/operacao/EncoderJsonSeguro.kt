package br.sidia.painel.operacao

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.EncoderBase
import java.time.format.DateTimeFormatter

/**
 * Uma linha JSON por evento de log, em UTF-8: `{"ts","nivel","logger","msg","requisicao"?,"exc"?}`.
 * Toda string passa pela [MascaraDeLog] ANTES de ser escrita e é escapada como JSON (aspas, barra, CR, LF e demais
 * controles viram escapes), então nenhum texto consegue quebrar a linha nem forjar outra entrada.
 * Da exceção saem só a classe, a mensagem mascarada e até [LIMITE_DA_PILHA] quadros da pilha.
 * O único campo do MDC que sai é `requisicao` (id da requisição); nenhum usuário entra no log do servidor.
 */
class EncoderJsonSeguro : EncoderBase<ILoggingEvent>() {
    override fun headerBytes(): ByteArray = ByteArray(0)

    override fun footerBytes(): ByteArray = ByteArray(0)

    override fun encode(event: ILoggingEvent): ByteArray {
        val sb = StringBuilder(256)
        sb.append("{\"ts\":")
        texto(sb, DateTimeFormatter.ISO_INSTANT.format(event.instant))
        sb.append(",\"nivel\":")
        texto(sb, event.level.toString())
        sb.append(",\"logger\":")
        texto(sb, event.loggerName)
        sb.append(",\"msg\":")
        texto(sb, MascaraDeLog.mascarar(event.formattedMessage ?: ""))
        event.mdcPropertyMap?.get(CAMPO_REQUISICAO)?.takeIf { it.isNotEmpty() }?.let {
            sb.append(",\"requisicao\":")
            texto(sb, MascaraDeLog.mascarar(it))
        }
        event.throwableProxy?.let { erro ->
            sb.append(",\"exc\":{\"classe\":")
            texto(sb, erro.className ?: "")
            sb.append(",\"msg\":")
            texto(sb, MascaraDeLog.mascarar(erro.message ?: ""))
            sb.append(",\"pilha\":[")
            val quadros = erro.stackTraceElementProxyArray.orEmpty().take(LIMITE_DA_PILHA)
            quadros.forEachIndexed { i, quadro ->
                if (i > 0) sb.append(',')
                val ste = quadro.stackTraceElement
                texto(sb, "${ste.className}.${ste.methodName}(${ste.fileName ?: "?"}:${ste.lineNumber})")
            }
            sb.append("]}")
        }
        sb.append("}\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun texto(sb: StringBuilder, valor: String) {
        sb.append('"')
        for (c in valor) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> sb.append("\\u").append("%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    companion object {
        const val CAMPO_REQUISICAO = "requisicao"
        const val LIMITE_DA_PILHA = 20
    }
}
