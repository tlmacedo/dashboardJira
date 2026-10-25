package br.sidia.painel.security

import io.ktor.server.application.ApplicationCall
import java.time.Clock
import java.time.LocalDate

/** Resolve quem está fazendo a chamada; `null` significa não autenticado. */
interface Authenticator {
    suspend fun authenticate(call: ApplicationCall): Principal?
}

/**
 * Monta o principal SEMPRE a partir do diretório de papéis na data [today].
 * Id desconhecido ou sem papel vigente resulta em conjunto vazio (nega por padrão).
 */
fun principalFor(userId: String, directory: RoleDirectory, today: LocalDate): Principal =
    Principal(userId, directory.rolesOf(userId, today))

/**
 * Modo de desenvolvimento: sem login. Se o id [devUserId] existir no diretório, os papéis vêm dele;
 * senão devolve o `Principal("dev", {GERENTE})` de desenvolvimento. Esse gerente implícito vale
 * SOMENTE em modo desenvolvimento: `installAccessControl` recusa este autenticador fora dele.
 */
class DevAuthenticator(
    private val directory: RoleDirectory? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val devUserId: String = "dev",
) : Authenticator {
    override suspend fun authenticate(call: ApplicationCall): Principal? {
        if (directory != null) {
            val principal = principalFor(devUserId, directory, LocalDate.now(clock))
            if (principal.roles.isNotEmpty()) return principal
        }
        return Principal("dev", setOf(Role.GERENTE))
    }
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
