package br.sidia.painel.security

import io.ktor.server.application.ApplicationCall

/** Resolve quem está fazendo a chamada; `null` significa não autenticado. */
interface Authenticator {
    suspend fun authenticate(call: ApplicationCall): Principal?
}

/** Modo de desenvolvimento: sem login, sempre o usuário "dev" com papel de gerente. */
class DevAuthenticator : Authenticator {
    override suspend fun authenticate(call: ApplicationCall): Principal? = Principal("dev", setOf(Role.GERENTE))
}

/**
 * Stub do SSO/OIDC de produção: sempre não autenticado até o provedor real ser definido.
 *
 * Ao implementar o provedor real: o `userId` vem do token validado, mas os papéis NUNCA vêm de
 * claims do token. Eles devem ser derivados do `RoleDirectory` na data de hoje
 * (`rolesOf(userId, hoje)`), para que a autorização dependa só do diretório versionado.
 */
class OidcAuthenticator : Authenticator {
    override suspend fun authenticate(call: ApplicationCall): Principal? = null
}
