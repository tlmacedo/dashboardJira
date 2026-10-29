package br.sidia.painel.config

import br.sidia.painel.security.SegredosDaSessao
import java.net.URI
import java.net.URISyntaxException
import java.util.Base64

/** Produção recusada na subida. Os [codigos] são estáveis e a mensagem nunca traz valor (segredo, URL ou caminho). */
class ViolacaoDeProducao(val codigos: List<String>) : IllegalStateException("Subida recusada em produção: " + codigos.joinToString(", "))

/**
 * Guardas de produção (Plano 13, T4): rodam em `main()` logo depois de `Settings.fromEnv` e ANTES de abrir banco e trava, e
 * recusam a subida com TODOS os problemas de uma vez, em ordem estável. Em modo de desenvolvimento não fazem nada.
 *
 * Lêem as variáveis de OIDC, sessão e origem pública direto do mapa [env] (não dependem do formato interno de `OidcSettings`)
 * e o resto da configuração já interpretada ([Settings]). Códigos, nesta ordem: `DEMO_EM_PRODUCAO`, `OIDC_ISSUER_SEM_HTTPS`,
 * `REDIRECT_URI_SEM_HTTPS`, `ORIGEM_PUBLICA_AUSENTE`, `REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA`, `SESSION_SECRET_CURTO`,
 * `PROXY_NAO_CONFIGURADO`, `ESCUTA_ABERTA_A_TODAS`, `CAMINHO_RELATIVO`.
 *
 * Mais `TRAVA_DPO_NAO_DECLARADA` (por último): `DASHBOARD_DPO_ACEITE=true` sem `DASHBOARD_DPO_ACEITE_REF`. Aceite ausente ou
 * false NÃO recusa nada: a trava do DPO fica fechada e o painel sobe.
 */
object GuardasDeProducao {
    fun verificar(s: Settings, env: Map<String, String>) {
        if (s.dashboardDevMode) return
        fun valor(nome: String): String? = env[nome]?.trim()?.takeIf { it.isNotEmpty() }
        val achados = mutableListOf<String>()

        if (s.dashboardDemoData) achados += "DEMO_EM_PRODUCAO"

        val issuer = valor("OIDC_ISSUER")
        if (issuer == null || !ehHttps(issuer)) achados += "OIDC_ISSUER_SEM_HTTPS"

        val redirect = valor("OIDC_REDIRECT_URI")
        if (redirect != null && !ehHttps(redirect)) achados += "REDIRECT_URI_SEM_HTTPS"

        val origem = valor("DASHBOARD_ORIGEM_PUBLICA")
        if (origem == null || !ehHttps(origem)) {
            achados += "ORIGEM_PUBLICA_AUSENTE"
        } else if (redirect != null && origemDe(redirect) != origemDe(origem)) {
            achados += "REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA"
        }

        if (!segredoDeSessaoValido(valor("SESSION_SECRET"))) achados += "SESSION_SECRET_CURTO"

        if (s.proxyConfiavel.isEmpty()) achados += "PROXY_NAO_CONFIGURADO"

        if (s.host.trim() in ESCUTA_ABERTA) achados += "ESCUTA_ABERTA_A_TODAS"

        if (!s.databasePath.isAbsolute || !s.configDir.isAbsolute || !s.staticDir.isAbsolute) achados += "CAMINHO_RELATIVO"

        // Trava do DPO: aceite ausente/false = trava FECHADA, e a subida segue (fail-closed). Aceite verdadeiro sem a referência
        // do parecer é declaração incompleta e recusa a subida (o LgpdSettings também recusa; aqui o código sai junto dos outros).
        if (valor("DASHBOARD_DPO_ACEITE")?.lowercase() == "true" && valor("DASHBOARD_DPO_ACEITE_REF") == null) {
            achados += "TRAVA_DPO_NAO_DECLARADA"
        }

        if (achados.isNotEmpty()) throw ViolacaoDeProducao(achados)
    }

    /** Todas as interfaces: vazio, `0.0.0.0`, `::` e `[::]`. */
    private val ESCUTA_ABERTA = setOf("", "0.0.0.0", "::", "[::]")

    private fun uriOuNula(texto: String): URI? = try {
        URI(texto)
    } catch (e: URISyntaxException) {
        null
    }

    private fun ehHttps(texto: String): Boolean {
        val u = uriOuNula(texto) ?: return false
        return u.scheme.equals("https", ignoreCase = true) && !u.host.isNullOrEmpty()
    }

    /** `esquema://host[:porta]` em minúsculas, com a porta padrão (443) implícita; nulo se ilegível. */
    private fun origemDe(texto: String): String? {
        val u = uriOuNula(texto) ?: return null
        val host = u.host?.lowercase() ?: return null
        val porta = if (u.port == -1 || u.port == 443) "" else ":${u.port}"
        return "${u.scheme?.lowercase()}://$host$porta"
    }

    private fun segredoDeSessaoValido(texto: String?): Boolean {
        if (texto == null) return false
        return try {
            Base64.getDecoder().decode(texto).size >= SegredosDaSessao.MINIMO_DE_BYTES
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
