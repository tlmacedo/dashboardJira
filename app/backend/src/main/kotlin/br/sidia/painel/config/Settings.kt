package br.sidia.painel.config

import br.sidia.painel.jira.JiraAuthKind
import java.nio.file.InvalidPathException
import java.nio.file.Path
import br.sidia.painel.jira.INSTANCIAS_CONHECIDAS
import br.sidia.painel.sync.SyncSettings
import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.time.Duration.Companion.minutes

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
 *
 * É uma `data class`, então `copy` existe. Usar `copy(jiraLiveEnabled = ...)` é PERMITIDO em testes e
 * PROIBIDO em código de produção (religaria o Jira real sem passar pelo ambiente); o teste
 * `SomenteAFabricaCriaClientesJira` falha se esse padrão aparecer em `src/main`.
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
    /** Tamanho máximo do pool de conexões (`DATABASE_POOL_SIZE`, de 1 a 100). */
    val databasePoolSize: Int = 10,
    /** Instâncias do Jira configuradas (`samsung`, `sidia`); só entram quando completas. */
    val jiraInstances: Map<String, JiraInstanceSettings> = emptyMap(),
    /** Ajustes da sincronização (`SYNC_*`). Só intervalos e limites: o modo vem da flag do Jira. */
    val sync: SyncSettings = SyncSettings(),
) {
    /** Sem segredos: senha vira `***` e `usuario:senha@` e `password=` da URL são mascarados. */
    override fun toString(): String =
        "Settings(jiraLiveEnabled=$jiraLiveEnabled, dashboardDevMode=$dashboardDevMode, " +
            "retentionMonths=$retentionMonths, zone=$zone, configDir=$configDir, " +
            "databaseUrl=${mascararUrl(databaseUrl)}, staticDir=$staticDir, databaseUser=$databaseUser, " +
            "databasePassword=***, databasePoolSize=$databasePoolSize, jiraInstances=$jiraInstances)"

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
                databasePoolSize = texto(env, "DATABASE_POOL_SIZE")?.let {
                    it.toIntOrNull()?.takeIf { n -> n in 1..100 }
                        ?: throw IllegalArgumentException("DATABASE_POOL_SIZE inválido: '$it' (esperado inteiro de 1 a 100)")
                } ?: padrao.databasePoolSize,
                jiraInstances = listOfNotNull(
                    instancia(env, "samsung", "JIRA_SAMSUNG", JiraAuthKind.BEARER),
                    instancia(env, "sidia", "JIRA_SIDIA", JiraAuthKind.BASIC),
                ).toMap(),
                sync = lerSync(env),
            )
        }

        /** Lê as `SYNC_*`; vazio = padrão. Mensagens citam só o NOME da variável, nunca o valor. */
        private fun lerSync(env: Map<String, String>): SyncSettings {
            val p = SyncSettings()
            fun inteiro(nome: String, faixa: IntRange, padrao: Int): Int =
                texto(env, nome)?.let {
                    it.toIntOrNull()?.takeIf { n -> n in faixa }
                        ?: throw IllegalArgumentException("$nome inválido (esperado inteiro de ${faixa.first} a ${faixa.last})")
                } ?: padrao

            fun hora(nome: String, padrao: LocalTime): LocalTime =
                texto(env, nome)?.let {
                    try {
                        LocalTime.parse(it, FORMATO_HORA)
                    } catch (e: DateTimeException) {
                        throw IllegalArgumentException("$nome inválido (esperado HH:mm)")
                    }
                } ?: padrao

            val instancias = texto(env, "SYNC_INSTANCIAS")?.let { bruto ->
                val ids = bruto.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                require(ids.isNotEmpty() && ids.all { it in INSTANCIAS_CONHECIDAS }) {
                    "SYNC_INSTANCIAS inválido (esperado ids de instâncias conhecidas separados por vírgula)"
                }
                ids
            } ?: p.instancias
            return SyncSettings(
                instancias = instancias,
                intervalo = inteiro("SYNC_INTERVALO_MIN", 5..1440, p.intervalo.inWholeMinutes.toInt()).minutes,
                horaReconciliacao = hora("SYNC_HORA_RECONCILIACAO", p.horaReconciliacao),
                horaRetencao = hora("SYNC_HORA_RETENCAO", p.horaRetencao),
                maxItens = inteiro("SYNC_MAX_ITENS", 1..200_000, p.maxItens),
                maxRequisicoes = inteiro("SYNC_MAX_REQUISICOES", 1..20_000, p.maxRequisicoes),
                sobreposicao = inteiro("SYNC_SOBREPOSICAO_MIN", 1..60, p.sobreposicao.inWholeMinutes.toInt()).minutes,
                fusoDoServidor = texto(env, "SYNC_FUSO_SERVIDOR")?.let {
                    try {
                        ZoneId.of(it)
                    } catch (e: DateTimeException) {
                        throw IllegalArgumentException("SYNC_FUSO_SERVIDOR inválido")
                    }
                } ?: p.fusoDoServidor,
                tempoMaximo = inteiro("SYNC_TEMPO_MAX_MIN", 1..240, p.tempoMaximo.inWholeMinutes.toInt()).minutes,
            )
        }

        private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

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
