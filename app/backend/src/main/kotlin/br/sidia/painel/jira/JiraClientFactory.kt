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
 * Fábrica ÚNICA de [JiraClient]: nenhum outro ponto do código de produção pode construir o cliente.
 *
 * - Com `jiraLiveEnabled=false`: não exige credencial e devolve um cliente com `LiveGuard(false)`;
 *   qualquer chamada lança [JiraLiveDisabledException]. O `HttpClient` real NÃO é criado
 *   (o cliente usa um motor que recusa toda requisição).
 * - Com `jiraLiveEnabled=true`: valida a instância antes de criar o cliente. As mensagens citam só o
 *   id da instância e o NOME da regra violada, nunca o token nem a URL.
 *
 * @param http provedor do `HttpClient` (injetável para os testes usarem MockEngine).
 */
class JiraClientFactory(
    private val settings: Settings,
    private val http: (JiraInstanceConfig) -> HttpClient = { createJiraHttpClient() },
    private val retry: RetryPolicy = RetryPolicy(),
) {
    /** Cria o cliente da instância (`samsung`, `sidia`...). Id desconhecido: [IllegalArgumentException]. */
    fun create(instanceId: String): JiraClient {
        require(instanceId in INSTANCIAS_CONHECIDAS || instanceId in settings.jiraInstances) {
            "Instância Jira desconhecida: '$instanceId'"
        }
        if (!settings.jiraLiveEnabled) {
            // Sem credencial, sem rede: o guard barra antes de qualquer requisição.
            val vazio = JiraInstanceConfig(instanceId, "https://desligado.invalid", JiraAuthKind.BEARER, "-")
            return JiraClient(vazio, HttpClient(EngineDesligado()), LiveGuard(false), retry)
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
        val host = uri.host ?: falha("url-com-host")
        if (uri.rawUserInfo != null || uri.rawAuthority?.contains('@') == true) falha("url-sem-credenciais")
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> if (host.lowercase() !in HOSTS_LOCAIS) falha("https-obrigatorio")
            else -> falha("https-obrigatorio")
        }
        if (s.token.isBlank()) falha("token-nao-vazio")
        if (s.auth == JiraAuthKind.BASIC) {
            val usuario = s.username
            if (usuario.isNullOrBlank()) falha("usuario-obrigatorio-no-basic")
            if (':' in usuario) falha("usuario-sem-dois-pontos")
        }
    }

    /** Motor que recusa toda requisição: usado quando a consulta real está desligada. */
    @OptIn(InternalAPI::class)
    private class EngineDesligado : HttpClientEngineBase("jira-desligado") {
        override val config = HttpClientEngineConfig()

        override suspend fun execute(data: HttpRequestData): HttpResponseData =
            throw JiraLiveDisabledException("Consulta ao Jira bloqueada: JIRA_LIVE_ENABLED não está ativo")
    }

    private companion object {
        val INSTANCIAS_CONHECIDAS = setOf("samsung", "sidia")
        val HOSTS_LOCAIS = setOf("localhost", "127.0.0.1", "[::1]")
    }
}
