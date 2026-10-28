package br.sidia.painel.config

import br.sidia.painel.jira.INSTANCIAS_CONHECIDAS
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.operacao.OperacaoSettings
import br.sidia.painel.sync.SyncSettings
import java.nio.file.InvalidPathException
import java.nio.file.Path
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
    /** `DASHBOARD_DEMO_DATA`: carrega issues fictícias na subida. Só com modo dev ligado e Jira real desligado. */
    val dashboardDemoData: Boolean = false,
    val retentionMonths: Int = 24,
    /** `ACCESS_LOG_RETENTION_MONTHS`: retenção própria da auditoria (`access_log`), mínimo 12. */
    val accessLogRetentionMonths: Int = 24,
    val zone: ZoneId = ZoneId.of("America/Manaus"),
    val configDir: Path = Path.of("config"),
    /** `DATABASE_PATH`: arquivo SQLite do painel (criado na primeira subida). Sem servidor, sem usuário, sem senha. */
    val databasePath: Path = Path.of("data/painel.db"),
    /** Diretório do build do frontend (`dist`) servido em `/`. */
    val staticDir: Path = Path.of("frontend/dist"),
    /** Tamanho máximo do pool de conexões (`DATABASE_POOL_SIZE`, de 1 a 100). No SQLite só um grava por vez. */
    val databasePoolSize: Int = 4,
    /** Instâncias do Jira configuradas (`samsung`, `sidia`); só entram quando completas. */
    val jiraInstances: Map<String, JiraInstanceSettings> = emptyMap(),
    /** Ajustes da sincronização (`SYNC_*`). Só intervalos e limites: o modo vem da flag do Jira. */
    val sync: SyncSettings = SyncSettings(),
    /**
     * `DASHBOARD_ORIGEM_PUBLICA`: origem pela qual o navegador acessa o painel (`esquema://host[:porta]`, minúsculas, sem
     * barra final). Usada pela proteção CSRF; nula = compara com o `Host` da requisição. Não é segredo.
     */
    val origemPublica: String? = null,
    /** Ajustes de operação (`PAINEL_LOG_*` e demais do Plano 12); sem segredos. */
    val operacao: OperacaoSettings = OperacaoSettings(),
) {
    /** Valores que nunca podem aparecer em log (tokens das instâncias); registrados na máscara de log na subida. */
    fun valoresSecretos(): List<String> = jiraInstances.values.flatMap {
        // No Basic, o cabeçalho leva o base64 de "usuario:token"; a máscara registra também essa forma.
        listOfNotNull(it.token, it.username?.let { u -> "$u:${it.token}" })
    }.filter { it.isNotBlank() } + operacao.sessao?.textos.orEmpty()

    /** Sem segredos: o painel não guarda senha de banco; as credenciais do Jira nunca entram aqui. */
    override fun toString(): String =
        "Settings(jiraLiveEnabled=$jiraLiveEnabled, dashboardDevMode=$dashboardDevMode, " +
            "dashboardDemoData=$dashboardDemoData, " +
            "retentionMonths=$retentionMonths, accessLogRetentionMonths=$accessLogRetentionMonths, zone=$zone, configDir=$configDir, " +
            "databasePath=$databasePath, staticDir=$staticDir, " +
            "databasePoolSize=$databasePoolSize, jiraInstances=$jiraInstances, origemPublica=$origemPublica)"

    companion object {
        /**
         * Lê as variáveis de ambiente. Variável presente mas vazia (ou só espaços) = padrão.
         * Valor inválido lança IllegalArgumentException citando a variável (nunca o segredo).
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): Settings {
            val padrao = Settings()
            val jiraLiveEnabled = booleano(env, "JIRA_LIVE_ENABLED", padrao.jiraLiveEnabled)
            val dashboardDevMode = booleano(env, "DASHBOARD_DEV_MODE", padrao.dashboardDevMode)
            val dashboardDemoData = booleano(env, "DASHBOARD_DEMO_DATA", padrao.dashboardDemoData)
            // Fail-closed: dados fictícios nunca convivem com produção nem com o Jira real ligado.
            if (dashboardDemoData) {
                require(dashboardDevMode) { "DASHBOARD_DEMO_DATA exige DASHBOARD_DEV_MODE=true" }
                require(!jiraLiveEnabled) { "DASHBOARD_DEMO_DATA exige JIRA_LIVE_ENABLED=false" }
            }
            return Settings(
                jiraLiveEnabled = jiraLiveEnabled,
                dashboardDevMode = dashboardDevMode,
                dashboardDemoData = dashboardDemoData,
                // Piso de segurança: valor baixo apagaria quase todo o histórico. Mensagens citam só o NOME.
                retentionMonths = mesesDeRetencao(env, "RETENTION_MONTHS", 6, padrao.retentionMonths),
                accessLogRetentionMonths = mesesDeRetencao(
                    env, "ACCESS_LOG_RETENTION_MONTHS", 12, padrao.accessLogRetentionMonths,
                ),
                zone = texto(env, "PAINEL_TZ")?.let {
                    try {
                        ZoneId.of(it)
                    } catch (e: DateTimeException) {
                        throw IllegalArgumentException("PAINEL_TZ inválido: '$it'", e)
                    }
                } ?: padrao.zone,
                configDir = caminho(env, "PAINEL_CONFIG_DIR") ?: padrao.configDir,
                databasePath = caminho(env, "DATABASE_PATH") ?: padrao.databasePath,
                staticDir = caminho(env, "PAINEL_STATIC_DIR") ?: padrao.staticDir,
                databasePoolSize = texto(env, "DATABASE_POOL_SIZE")?.let {
                    it.toIntOrNull()?.takeIf { n -> n in 1..100 }
                        ?: throw IllegalArgumentException("DATABASE_POOL_SIZE inválido: '$it' (esperado inteiro de 1 a 100)")
                } ?: padrao.databasePoolSize,
                jiraInstances = listOfNotNull(
                    instancia(env, "samsung", "JIRA_SAMSUNG", JiraAuthKind.BEARER),
                    instancia(env, "sidia", "JIRA_SIDIA", JiraAuthKind.BASIC),
                ).toMap(),
                sync = lerSync(env),
                origemPublica = origemPublica(env, dashboardDevMode),
                operacao = OperacaoSettings.fromEnv(env),
            )
        }

        /**
         * `DASHBOARD_ORIGEM_PUBLICA`: normaliza para `esquema://host[:porta]` em minúsculas, sem barra final. Fora do modo dev
         * exige `https`. Sem caminho, consulta, fragmento nem credencial. A mensagem cita só o NOME (a URL pode ter credencial).
         */
        private fun origemPublica(env: Map<String, String>, devMode: Boolean): String? {
            val bruto = texto(env, "DASHBOARD_ORIGEM_PUBLICA") ?: return null
            val nome = "DASHBOARD_ORIGEM_PUBLICA"
            val uri = try {
                java.net.URI(bruto)
            } catch (e: java.net.URISyntaxException) {
                throw IllegalArgumentException("$nome inválida (esperado esquema://host[:porta])")
            }
            val esquema = uri.scheme?.lowercase()
            require(esquema == "http" || esquema == "https") { "$nome inválida (esquema deve ser http ou https)" }
            require(devMode || esquema == "https") { "$nome inválida (fora do modo dev precisa ser https)" }
            val host = uri.host?.lowercase()
            require(!host.isNullOrEmpty()) { "$nome inválida (host ausente)" }
            require(uri.rawUserInfo == null) { "$nome inválida (sem credencial)" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "$nome inválida (sem consulta nem fragmento)" }
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "$nome inválida (sem caminho)" }
            val porta = if (uri.port == -1) "" else ":${uri.port}"
            return "$esquema://$host$porta"
        }

        private const val MAX_MESES_DE_RETENCAO = 600

        private fun mesesDeRetencao(env: Map<String, String>, nome: String, minimo: Int, padrao: Int): Int =
            texto(env, nome)?.let {
                it.toIntOrNull()?.takeIf { n -> n in minimo..MAX_MESES_DE_RETENCAO }
                    ?: throw IllegalArgumentException("$nome inválido (esperado inteiro de $minimo a $MAX_MESES_DE_RETENCAO)")
            } ?: padrao

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
