package br.sidia.painel.config

import java.nio.file.Path
import java.time.DateTimeException
import java.time.ZoneId

/**
 * Configuração do painel lida do ambiente.
 * Padrões seguros: Jira real desligado, modo de desenvolvimento ligado.
 */
data class Settings(
    val jiraLiveEnabled: Boolean = false,
    val dashboardDevMode: Boolean = true,
    val retentionMonths: Int = 24,
    val zone: ZoneId = ZoneId.of("America/Manaus"),
    val configDir: Path = Path.of("config"),
    val databaseUrl: String = "jdbc:postgresql://localhost:5432/painel",
) {
    companion object {
        /** Lê as variáveis de ambiente; valor inválido lança IllegalArgumentException citando a variável. */
        fun fromEnv(env: Map<String, String> = System.getenv()): Settings {
            val padrao = Settings()
            return Settings(
                jiraLiveEnabled = booleano(env, "JIRA_LIVE_ENABLED", padrao.jiraLiveEnabled),
                dashboardDevMode = booleano(env, "DASHBOARD_DEV_MODE", padrao.dashboardDevMode),
                retentionMonths = env["RETENTION_MONTHS"]?.let {
                    it.trim().toIntOrNull()?.takeIf { n -> n > 0 }
                        ?: throw IllegalArgumentException("RETENTION_MONTHS inválido: '$it' (esperado inteiro positivo)")
                } ?: padrao.retentionMonths,
                zone = env["PAINEL_TZ"]?.let {
                    try {
                        ZoneId.of(it.trim())
                    } catch (e: DateTimeException) {
                        throw IllegalArgumentException("PAINEL_TZ inválido: '$it'", e)
                    }
                } ?: padrao.zone,
                configDir = env["PAINEL_CONFIG_DIR"]?.let { Path.of(it) } ?: padrao.configDir,
                databaseUrl = env["DATABASE_URL"] ?: padrao.databaseUrl,
            )
        }

        private fun booleano(env: Map<String, String>, nome: String, padrao: Boolean): Boolean {
            val bruto = env[nome] ?: return padrao
            return when (bruto.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("$nome inválido: '$bruto' (esperado true ou false)")
            }
        }
    }
}
