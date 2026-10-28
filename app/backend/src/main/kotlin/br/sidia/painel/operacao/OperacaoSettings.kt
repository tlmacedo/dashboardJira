package br.sidia.painel.operacao

import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Ajustes de operação (logs, prontidão, métricas, alertas e backup). Nenhuma variável liga a sincronização nem
 * toca no Jira; vazio = padrão. Mensagens de erro citam só o NOME da variável, nunca o valor.
 */
data class OperacaoSettings(
    /** `PAINEL_LOG_FORMATO`: `JSON` (padrão) ou `TEXTO`; valor inválido cai em JSON (ver [ConfiguradorDeLog]). */
    val logFormato: String? = null,
    /** `PAINEL_LOG_DIR`: pasta do `painel.log` com rotação; nulo = só saída padrão. */
    val logDir: Path? = null,
) {
    companion object {
        fun fromEnv(env: Map<String, String>): OperacaoSettings = OperacaoSettings(
            logFormato = texto(env, "PAINEL_LOG_FORMATO"),
            logDir = caminho(env, "PAINEL_LOG_DIR"),
        )

        internal fun texto(env: Map<String, String>, nome: String): String? = env[nome]?.trim()?.takeIf { it.isNotEmpty() }

        internal fun caminho(env: Map<String, String>, nome: String): Path? =
            texto(env, nome)?.let {
                try {
                    Path.of(it)
                } catch (e: InvalidPathException) {
                    throw IllegalArgumentException("$nome inválido")
                }
            }
    }
}
