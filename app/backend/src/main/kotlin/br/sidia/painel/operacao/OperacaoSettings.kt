package br.sidia.painel.operacao

import br.sidia.painel.jira.INSTANCIAS_CONHECIDAS
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Ajustes de operação (logs, prontidão, métricas, alertas e backup). Nenhuma variável liga a sincronização nem
 * toca no Jira; vazio = padrão. Mensagens de erro citam só o NOME da variável, nunca o valor.
 */
data class OperacaoSettings(
    /** `PAINEL_LOG_FORMATO`: `JSON` (padrão) ou `TEXTO`; valor inválido cai em JSON (ver [ConfiguradorDeLog]). */
    val logFormato: String? = null,
    /** `PAINEL_LOG_DIR`: pasta do `painel.log` com rotação; nulo = só saída padrão. */
    val logDir: Path? = null,
    /** `PAINEL_DISCO_MIN_MB`: abaixo disso de espaço livre na pasta do banco o painel deixa de estar pronto (50 a 1.000.000). */
    val discoMinMb: Int = 500,
    /** `PAINEL_METRICAS_PORTA`: porta interna de `/metrics` (1024 a 65535, diferente de 8080); `0` desliga o servidor de métricas. */
    val metricasPorta: Int = 9464,
    /** `PAINEL_METRICAS_HOST`: interface da porta interna; o padrão só atende a própria máquina. */
    val metricasHost: String = "127.0.0.1",
    /** `PAINEL_ALERTAS_ARQUIVO`: arquivo de texto onde cada mudança de alerta vira uma linha; nulo = só log, métrica e rota. */
    val alertasArquivo: Path? = null,
    /**
     * `SYNC_PAT_VALIDADE_<INSTANCIA>` (aaaa-mm-dd): validade do token do Jira INFORMADA pelo operador, por instância. O painel
     * nunca consulta o Jira para descobri-la; só o alerta de PAT usa isto.
     */
    val patValidade: Map<String, LocalDate> = emptyMap(),
    /** Backup agendado do SQLite (`BACKUP_*`). */
    val backup: BackupSettings = BackupSettings(),
) {
    companion object {
        fun fromEnv(env: Map<String, String>): OperacaoSettings {
            val padrao = OperacaoSettings()
            return OperacaoSettings(
                logFormato = texto(env, "PAINEL_LOG_FORMATO"),
                logDir = caminho(env, "PAINEL_LOG_DIR"),
                discoMinMb = inteiro(env, "PAINEL_DISCO_MIN_MB", 50..1_000_000, padrao.discoMinMb),
                metricasPorta = porta(env, padrao.metricasPorta),
                metricasHost = host(env, padrao.metricasHost),
                alertasArquivo = caminho(env, "PAINEL_ALERTAS_ARQUIVO"),
                patValidade = INSTANCIAS_CONHECIDAS.sorted().mapNotNull { id ->
                    val nome = "SYNC_PAT_VALIDADE_${id.uppercase()}"
                    texto(env, nome)?.let { id to data(nome, it) }
                }.toMap(),
                backup = BackupSettings(
                    dir = caminho(env, "BACKUP_DIR"),
                    hora = hora(env, "BACKUP_HORA", padrao.backup.hora),
                    retencaoCopias = inteiro(env, "BACKUP_RETENCAO_COPIAS", 1..365, padrao.backup.retencaoCopias),
                ),
            )
        }

        private val FORMATO_HORA = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)

        private fun hora(env: Map<String, String>, nome: String, padrao: LocalTime): LocalTime =
            texto(env, nome)?.let {
                try {
                    LocalTime.parse(it, FORMATO_HORA)
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("$nome inválido (esperado HH:mm)")
                }
            } ?: padrao

        private fun data(nome: String, valor: String): LocalDate =
            try {
                LocalDate.parse(valor)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("$nome inválido (esperado aaaa-mm-dd)")
            }

        private const val PORTA_PUBLICA = 8080

        private fun porta(env: Map<String, String>, padrao: Int): Int {
            val nome = "PAINEL_METRICAS_PORTA"
            val valor = texto(env, nome)?.let {
                it.toIntOrNull() ?: throw IllegalArgumentException("$nome inválido (esperado 0 ou inteiro de 1024 a 65535)")
            } ?: return padrao
            require(valor == 0 || valor in 1024..65535) { "$nome inválido (esperado 0 ou inteiro de 1024 a 65535)" }
            require(valor != PORTA_PUBLICA) { "$nome inválido (não pode ser a porta pública do painel)" }
            return valor
        }

        private fun host(env: Map<String, String>, padrao: String): String {
            val nome = "PAINEL_METRICAS_HOST"
            val valor = texto(env, nome) ?: return padrao
            require(Regex("[A-Za-z0-9.:\\-]{1,253}").matches(valor)) { "$nome inválido (esperado nome de host ou endereço IP)" }
            return valor
        }

        internal fun inteiro(env: Map<String, String>, nome: String, faixa: IntRange, padrao: Int): Int =
            texto(env, nome)?.let {
                it.toIntOrNull()?.takeIf { n -> n in faixa }
                    ?: throw IllegalArgumentException("$nome inválido (esperado inteiro de ${faixa.first} a ${faixa.last})")
            } ?: padrao

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
