package br.sidia.painel.cards.contrato

import br.sidia.painel.config.Settings
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.jql.InvalidJqlValueException
import br.sidia.painel.jql.JqlBuilder
import br.sidia.painel.jql.JqlValues
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * Link "Abrir no Jira" (I-18): a lista de tasks da gaveta como uma busca por chaves no navegador do Jira. É só um
 * TEXTO de URL montado aqui, no backend: nada é pedido ao Jira, e o frontend nunca monta URL nem JQL.
 *
 * Só existe com a flag do Jira ligada e a instância configurada, com de 1 a [MAX_CHAVES] chaves, em https e sem
 * credencial na base. Qualquer outra situação devolve `null` (a tela então não mostra o botão). A JQL sai do
 * [JqlBuilder] (chaves suspeitas são rejeitadas, nunca escapadas).
 */
object LinkJira {
    /** Mais chaves que isso deixa a URL longa demais: sem link, a tela avisa e oferece o CSV. */
    const val MAX_CHAVES = 100

    fun paraChaves(settings: Settings, campos: FieldDictionary, instancia: String?, chaves: List<String>): String? {
        if (!settings.jiraLiveEnabled || instancia == null) return null
        val base = settings.jiraInstances[instancia]?.baseUrl?.let(::baseSegura) ?: return null
        val unicas = chaves.distinct()
        if (unicas.isEmpty() || unicas.size > MAX_CHAVES) return null
        val jql = try {
            JqlBuilder(campos, instancia).keysIn(unicas).build()
        } catch (e: IllegalArgumentException) {
            // Chave fora do formato, ou instância sem o campo `key` no dicionário: nenhum link, em vez de um link torto.
            return null
        }
        return "$base/issues/?jql=${URLEncoder.encode(jql, Charsets.UTF_8).replace("+", "%20")}"
    }

    /**
     * Link direto para UMA issue (`<base>/browse/CHAVE`), com as mesmas condições de [paraChaves]: só com a flag ligada,
     * instância configurada, https e base sem credencial, consulta ou fragmento. A chave passa por `JqlValues.issueKey`
     * (formato fora do padrão: nenhum link). Texto de URL apenas: nada é pedido ao Jira.
     */
    fun paraIssue(settings: Settings, instancia: String?, chave: String): String? {
        if (!settings.jiraLiveEnabled || instancia == null) return null
        val base = settings.jiraInstances[instancia]?.baseUrl?.let(::baseSegura) ?: return null
        val valida = try {
            JqlValues.issueKey(chave)
        } catch (e: InvalidJqlValueException) {
            return null
        }
        return "$base/browse/$valida"
    }

    /** `https://host[:porta][/contexto]` sem barra final; nulo se não for https, tiver credencial, consulta ou fragmento. */
    private fun baseSegura(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (e: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val porta = if (uri.port == -1) "" else ":${uri.port}"
        return "https://$host$porta${uri.rawPath.orEmpty().trimEnd('/')}"
    }
}
