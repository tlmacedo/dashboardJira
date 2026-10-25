package br.sidia.painel.config

import br.sidia.painel.jira.JiraAuthKind
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.DateTimeException
import java.time.ZoneId

/** Conexão com uma instância do Jira. O `token` nunca aparece em `toString`. */
data class JiraInstanceSettings(
    val baseUrl: String,
    val auth: JiraAuthKind,
    val username: String?,
    val token: String,
) {
    override fun toString() =
        "JiraInstanceSettings(baseUrl=${Settings.mascararUrl(baseUrl)}, auth=$auth, username=$username, token=***)"
}

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
    /** Diretório do build do frontend (`dist`) servido em `/`. */
    val staticDir: Path = Path.of("frontend/dist"),
    val databaseUser: String = "painel",
    /** Sem valor padrão: vem de `DATABASE_PASSWORD`. Nunca aparece em `toString`. */
    val databasePassword: String = "",
    /** Instâncias do Jira configuradas (`samsung`, `sidia`); só entram quando completas. */
    val jiraInstances: Map<String, JiraInstanceSettings> = emptyMap(),
) {
    /** Sem segredos: senha vira `***` e `usuario:senha@` e `password=` da URL são mascarados. */
    override fun toString(): String =
        "Settings(jiraLiveEnabled=$jiraLiveEnabled, dashboardDevMode=$dashboardDevMode, " +
            "retentionMonths=$retentionMonths, zone=$zone, configDir=$configDir, " +
            "databaseUrl=${mascararUrl(databaseUrl)}, staticDir=$staticDir, databaseUser=$databaseUser, " +
            "databasePassword=***, jiraInstances=$jiraInstances)"

    companion object {
        /**
         * Lê as variáveis de ambiente. Variável presente mas vazia (ou só espaços) = padrão.
         * Valor inválido lança IllegalArgumentException citando a variável (nunca o segredo).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): Settings {
            val padrao = Settings()
            return Settings(
                jiraLiveEnabled = booleano(env, "JIRA_LIVE_ENABLED", padrao.jiraLiveEnabled),
                dashboardDevMode = booleano(env, "DASHBOARD_DEV_MODE", padrao.dashboardDevMode),
                retentionMonths = texto(env, "RETENTION_MONTHS")?.let {
                    it.toIntOrNull()?.takeIf { n -> n > 0 }
                        ?: throw IllegalArgumentException("RETENTION_MONTHS inválido: '$it' (esperado inteiro positivo)")
                } ?: padrao.retentionMonths,
                zone = texto(env, "PAINEL_TZ")?.let {
                    try {
                        ZoneId.of(it)
                    } catch (e: DateTimeException) {
                        throw IllegalArgumentException("PAINEL_TZ inválido: '$it'", e)
                    }
                } ?: padrao.zone,
                configDir = caminho(env, "PAINEL_CONFIG_DIR") ?: padrao.configDir,
                databaseUrl = texto(env, "DATABASE_URL") ?: padrao.databaseUrl,
                staticDir = caminho(env, "PAINEL_STATIC_DIR") ?: padrao.staticDir,
                databaseUser = texto(env, "DATABASE_USER") ?: padrao.databaseUser,
                databasePassword = env["DATABASE_PASSWORD"]?.takeIf { it.isNotBlank() } ?: padrao.databasePassword,
                jiraInstances = listOfNotNull(
                    instancia(env, "samsung", "JIRA_SAMSUNG", JiraAuthKind.BEARER),
                    instancia(env, "sidia", "JIRA_SIDIA", JiraAuthKind.BASIC),
                ).toMap(),
            )
        }

        /** Valor sem espaços nas pontas; ausente, vazio ou só espaços = null (usar o padrão). */
        private fun texto(env: Map<String, String>, nome: String): String? =
            env[nome]?.trim()?.takeIf { it.isNotEmpty() }

        private fun caminho(env: Map<String, String>, nome: String): Path? =
            texto(env, nome)?.let {
                try {
                    Path.of(it)
                } catch (e: InvalidPathException) {
                    throw IllegalArgumentException("$nome inválido: '$it'", e)
                }
            }

        /**
         * Lê uma instância pelo prefixo (`<prefixo>_URL`, `<prefixo>_TOKEN` e, no Basic, `<prefixo>_USER`).
         * Nenhuma variável preenchida = instância ausente; parcial = erro citando só os NOMES que faltam.
         */
        private fun instancia(
            env: Map<String, String>,
            id: String,
            prefixo: String,
            auth: JiraAuthKind,
        ): Pair<String, JiraInstanceSettings>? {
            val nomes = if (auth == JiraAuthKind.BASIC) {
                listOf("${prefixo}_URL", "${prefixo}_USER", "${prefixo}_TOKEN")
            } else {
                listOf("${prefixo}_URL", "${prefixo}_TOKEN")
            }
            val valores = nomes.associateWith { n -> env[n]?.takeIf { it.isNotBlank() } }
            if (valores.values.all { it == null }) return null
            val faltam = valores.filterValues { it == null }.keys
            require(faltam.isEmpty()) {
                "Instância Jira '$id' incompleta: defina também ${faltam.joinToString(", ")}"
            }
            return id to JiraInstanceSettings(
                baseUrl = valores.getValue("${prefixo}_URL")!!.trim(),
                auth = auth,
                username = valores["${prefixo}_USER"]?.trim(),
                token = valores.getValue("${prefixo}_TOKEN")!!.trim(),
            )
        }

        // Seguro por excesso: tudo entre "://" e o ÚLTIMO "@" é tratado como credencial (senha pode ter "/" ou "?").
        private val USUARIO_SENHA_NA_URL = Regex("(?<=://)[^\\s]*@")
        private val SENHA_NA_QUERY =
            Regex("(?i)([?&;](?:sslpassword|password|passwd|pass|pwd|secret|token)=)[^&;\\s]*")

        /** Troca `usuario:senha@` e parâmetros secretos (`password=`, `pwd=`, `token=`...) da URL por `***`. */
        internal fun mascararUrl(url: String): String =
            url.replace(USUARIO_SENHA_NA_URL, "***@").replace(SENHA_NA_QUERY, "$1***")

        private fun booleano(env: Map<String, String>, nome: String, padrao: Boolean): Boolean {
            val bruto = texto(env, nome) ?: return padrao
            return when (bruto.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("$nome inválido: '$bruto' (esperado true ou false)")
            }
        }
    }
}
