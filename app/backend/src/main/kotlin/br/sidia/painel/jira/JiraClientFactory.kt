package br.sidia.painel.jira

import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.InternalAPI
import java.net.URI
import java.net.URISyntaxException

/**
 * Fábrica ÚNICA de [JiraClient]: nenhum outro ponto do código de produção deve construir o cliente.
 * O `internal` só protege de OUTROS módulos; dentro deste módulo a garantia vem da fábrica única mais o
 * teste `SomenteAFabricaCriaClientesJira`, que varre `src/main` e falha se `JiraClient(` aparecer fora daqui.
 *
 * - Com `jiraLiveEnabled=false`: não exige credencial e devolve um cliente com `LiveGuard(false)`;
 *   qualquer chamada lança [JiraLiveDisabledException]. O `HttpClient` real NÃO é criado
 *   (o cliente usa um motor que recusa toda requisição).
 * - Com `jiraLiveEnabled=true`: valida a instância antes de criar o cliente. As mensagens citam só o
 *   id da instância e o NOME da regra violada, nunca o token nem a URL.
 *
 * O construtor primário é `internal` (injeção de `HttpClient` e do gancho de teste); o código de produção
 * usa apenas `JiraClientFactory(settings)`.
 *
 * @param http provedor do `HttpClient` (injetável para os testes usarem MockEngine).
 */
class JiraClientFactory internal constructor(
    private val settings: Settings,
    private val http: (JiraInstanceConfig) -> HttpClient,
    private val retry: RetryPolicy = RetryPolicy(),
    /** Só para testes: avisa se o motor "desligado" chegou a ser alcançado (deve ser 0 vezes). */
    private val aoAlcancarMotorDesligado: () -> Unit = {},
) {
    /** Construtor público: cliente HTTP real (CIO) e política de novas tentativas padrão. */
    constructor(settings: Settings) : this(settings, { createJiraHttpClient() })

    /**
     * Valida na subida todas as instâncias configuradas, sem criar cliente nem enviar nada.
     * Com `jiraLiveEnabled=false` não faz nada. Falha com [IllegalStateException] citando só id e regra.
     */
    fun validarInstancias() {
        if (!settings.jiraLiveEnabled) return
        settings.jiraInstances.forEach { (id, s) -> validar(id, s) }
    }

    /** Cria o cliente da instância (`samsung`, `sidia`...). Id desconhecido: [IllegalArgumentException]. */
    fun create(instanceId: String): JiraClient {
        require(instanceId in INSTANCIAS_CONHECIDAS || instanceId in settings.jiraInstances) {
            "Instância Jira desconhecida: '$instanceId'"
        }
        if (!settings.jiraLiveEnabled) {
            // Sem credencial, sem rede: o guard barra antes de qualquer requisição.
            val vazio = JiraInstanceConfig(instanceId, "https://desligado.invalid", JiraAuthKind.BEARER, "-")
            return JiraClient(vazio, HttpClient(EngineDesligado(aoAlcancarMotorDesligado)), LiveGuard(false), retry)
        }
        val s = settings.jiraInstances[instanceId]
            ?: throw IllegalStateException("Instância Jira '$instanceId' não está configurada")
        validar(instanceId, s)
        val config = JiraInstanceConfig(instanceId, s.baseUrl, s.auth, s.token, s.username)
        return JiraClient(config, http(config), LiveGuard(true), retry)
    }

    private fun validar(id: String, s: JiraInstanceSettings) {
        fun falha(regra: String): Nothing =
            throw IllegalStateException("Instância Jira '$id' inválida: regra '$regra'")

        val url = s.baseUrl
        if (url.isEmpty() || url.any { it.isWhitespace() }) falha("url-sem-espacos")
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            falha("url-valida")
        }
        if (uri.rawFragment != null || url.contains('#')) falha("url-sem-fragmento")
        if (uri.rawQuery != null || url.contains('?')) falha("url-sem-query")
        val host = uri.host ?: falha("url-com-host")
        if (uri.rawUserInfo != null || uri.rawAuthority?.contains('@') == true) falha("url-sem-credenciais")
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> if (host.lowercase() !in HOSTS_LOCAIS) falha("https-obrigatorio")
            else -> falha("https-obrigatorio")
        }
        if (s.token.isBlank()) falha("token-nao-vazio")
        // Só ASCII imprimível sem espaço (0x21-0x7E): o valor vai num cabeçalho e o Ktor o repetiria em erros.
        if (s.token.any { it.code !in 0x21..0x7E }) falha("token-sem-controle")
        if (s.auth == JiraAuthKind.BASIC) {
            val usuario = s.username
            if (usuario.isNullOrBlank()) falha("usuario-obrigatorio-no-basic")
            if (usuario.any { it.isISOControl() }) falha("usuario-sem-controle")
            if (':' in usuario) falha("usuario-sem-dois-pontos")
        }
    }

    /**
     * Motor que recusa toda requisição: usado quando a consulta real está desligada.
     * O `@OptIn(InternalAPI)` usa API interna do Ktor: se uma atualização a mudar, o BUILD quebra
     * (falha fechada), nunca o comportamento em silêncio.
     */
    @OptIn(InternalAPI::class)
    private class EngineDesligado(private val aoExecutar: () -> Unit) : HttpClientEngineBase("jira-desligado") {
        override val config = HttpClientEngineConfig()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            aoExecutar()
            throw JiraLiveDisabledException("Consulta ao Jira bloqueada: JIRA_LIVE_ENABLED não está ativo")
        }
    }

    private companion object {
        val INSTANCIAS_CONHECIDAS = setOf("samsung", "sidia")
        val HOSTS_LOCAIS = setOf("localhost", "127.0.0.1", "[::1]")
    }
}
