package br.sidia.painel

import java.util.Base64

/**
 * Variáveis de login SSO/OIDC fictícias (host `.invalid`, segredo de sessão gerado de bytes fixos) para os testes que
 * sobem o painel em PRODUÇÃO (`DASHBOARD_DEV_MODE=false`): sem elas `Settings.fromEnv` falha fechado. Nada aqui é segredo real.
 */
val ENV_OIDC_DE_TESTE: Map<String, String> = mapOf(
    "OIDC_ISSUER" to "https://sso.exemplo.invalid",
    "OIDC_CLIENT_ID" to "painel-teste",
    "OIDC_REDIRECT_URI" to "https://painel.exemplo.invalid/auth/callback",
    "SESSION_SECRET" to Base64.getEncoder().encodeToString(ByteArray(48) { (it * 7 + 3).toByte() }),
)
