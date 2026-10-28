package br.sidia.painel.security

import br.sidia.painel.config.Settings
import io.ktor.server.application.ApplicationCall
import java.time.Clock
import java.time.LocalDate

/** Resolve quem está fazendo a chamada; `null` significa não autenticado. */
interface Authenticator {
    suspend fun authenticate(call: ApplicationCall): Principal?
}

/**
 * Monta o principal SEMPRE a partir do diretório de papéis na data [today].
 * O id é normalizado ([normalizarUsername]) antes da consulta. Id desconhecido, vazio ou sem papel
 * vigente resulta em conjunto vazio (nega por padrão; nunca exceção, para manter o 403).
 */
fun principalFor(userId: String, directory: RoleDirectory, today: LocalDate): Principal {
    val id = try {
        normalizarUsername(userId)
    } catch (e: IllegalArgumentException) {
        return Principal(userId.trim(), emptySet())
    }
    return Principal(id, directory.rolesOf(id, today))
}

/**
 * Modo de desenvolvimento: sem login, sempre o `Principal("dev", {GERENTE})`. Esse gerente implícito
 * vale SOMENTE em modo desenvolvimento: `installAccessControl` recusa este autenticador fora dele.
 *
 * Os parâmetros [directory], [clock] e [devUserId] existem só para testes com diretório próprio: se o
 * id [devUserId] tiver papel vigente no diretório, os papéis vêm dele. O app real não passa diretório
 * (o id "dev" é reservado em [RoleDirectory], então nunca existe no arquivo de papéis).
 */
class DevAuthenticator(
    private val directory: RoleDirectory? = null,
    // Padrão: a zona do painel (America/Manaus), nunca a do servidor; o módulo passa a zona de `settings`.
    private val clock: Clock = Clock.system(Settings().zone),
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
