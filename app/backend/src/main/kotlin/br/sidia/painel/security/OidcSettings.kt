package br.sidia.painel.security

import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/**
 * Configuração do SSO/OIDC (variáveis `OIDC_*` e `SESSION_SECRET`). Nada aqui nomeia um provedor: serve a qualquer um que
 * fale OpenID Connect com *authorization code* e PKCE. O [clientSecret] é opcional (cliente público + PKCE) e nunca aparece
 * em `toString`; o segredo da sessão ([sessao]) também não.
 */
class OidcSettings(
    /** `OIDC_ISSUER`: URL `https` do emissor; o `iss` do token precisa ser IGUAL a ela. */
    val issuer: String,
    val clientId: String,
    val clientSecret: String?,
    /** `OIDC_REDIRECT_URI`: `https://<painel>/auth/callback`, exatamente como cadastrada no provedor. */
    val redirectUri: String,
    /** `OIDC_CLAIM_USERNAME`: claim do `id_token` que traz o username do Jira (decisão da implantação, Plano 13). */
    val claimUsername: String = "preferred_username",
    val escopos: List<String> = listOf("openid", "profile"),
    /** `SESSION_SECRET` (e `SESSION_SECRET_ANTERIOR` durante a rotação). */
    val sessao: SegredosDaSessao,
    val duracaoDaSessao: Duration = Duration.ofHours(8),
) {
    override fun toString() =
        "OidcSettings(issuer=$issuer, clientId=$clientId, clientSecret=${if (clientSecret != null) "***" else "nenhum"}, " +
            "redirectUri=$redirectUri, claimUsername=$claimUsername, sessao=***)"

    companion object {
        /** O painel atende `/auth/callback` e só ele: a URI cadastrada tem de terminar nesse caminho. */
        const val CAMINHO_DO_CALLBACK = "/auth/callback"

        private val NOME_DE_CLAIM = Regex("^[A-Za-z0-9_.:-]{1,64}$")

        /**
         * Lê as variáveis. Em produção (`devMode=false`) `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_REDIRECT_URI` e
         * `SESSION_SECRET` são obrigatórios: faltar um, ou deixá-lo em branco, derruba a subida (nunca vira "ausente").
         * Em desenvolvimento, sem nenhuma delas, devolve `null` (não há login). As mensagens citam só o NOME da variável.
         */
        fun fromEnv(env: Map<String, String>, devMode: Boolean): OidcSettings? {
            fun valor(nome: String): String? = env[nome]?.trim()?.takeIf { it.isNotEmpty() }
            // Em desenvolvimento só `SESSION_SECRET` (rotação testada localmente) não liga o login: precisa de alguma OIDC_*.
            if (devMode && listOf("OIDC_ISSUER", "OIDC_CLIENT_ID", "OIDC_REDIRECT_URI").all { valor(it) == null }) return null

            fun obrigatoria(nome: String): String =
                valor(nome) ?: throw IllegalArgumentException("$nome é obrigatório em produção (login SSO/OIDC)")

            val issuer = obrigatoria("OIDC_ISSUER")
            val clientId = obrigatoria("OIDC_CLIENT_ID")
            val redirect = obrigatoria("OIDC_REDIRECT_URI")
            val segredos = SegredosDaSessao.fromEnv(env) ?: throw IllegalArgumentException("SESSION_SECRET é obrigatório em produção (assina o cookie de sessão)")
            exigirHttps("OIDC_ISSUER", issuer, semConsulta = true)
            exigirHttps("OIDC_REDIRECT_URI", redirect, semConsulta = true)
            require(URI(redirect).rawPath == CAMINHO_DO_CALLBACK) { "OIDC_REDIRECT_URI inválida (o caminho deve ser $CAMINHO_DO_CALLBACK)" }
            require(clientId.none { it.isISOControl() || it.isWhitespace() }) { "OIDC_CLIENT_ID inválido" }
            val claim = valor("OIDC_CLAIM_USERNAME") ?: "preferred_username"
            require(NOME_DE_CLAIM.matches(claim)) { "OIDC_CLAIM_USERNAME inválido (esperado o nome de uma claim)" }
            return OidcSettings(
                issuer = issuer,
                clientId = clientId,
                clientSecret = valor("OIDC_CLIENT_SECRET"),
                redirectUri = redirect,
                claimUsername = claim,
                sessao = segredos,
            )
        }

        /** `https`, com host, sem credencial na URL e sem fragmento (nem consulta, quando [semConsulta]). */
        private fun exigirHttps(nome: String, url: String, semConsulta: Boolean) {
            val uri = try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("$nome inválida (esperado URL https)")
            }
            require(uri.scheme.equals("https", ignoreCase = true)) { "$nome inválida (esperado URL https)" }
            require(!uri.host.isNullOrEmpty()) { "$nome inválida (host ausente)" }
            require(uri.rawUserInfo == null) { "$nome inválida (sem credencial na URL)" }
            require(uri.rawFragment == null && (!semConsulta || uri.rawQuery == null)) { "$nome inválida (sem consulta nem fragmento)" }
        }
    }
}
