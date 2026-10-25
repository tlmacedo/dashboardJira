package br.sidia.painel.jira

/** Lançada quando se tenta falar com o Jira com a trava de consulta real desligada. */
class JiraLiveDisabledException(message: String) : RuntimeException(message)

/**
 * Trava de segurança: sem `JIRA_LIVE_ENABLED=true`, nenhuma requisição ao Jira pode sair.
 * O cliente chama `check()` antes de TODA requisição (inclusive antes de cada nova tentativa).
 */
class LiveGuard(private val enabled: Boolean) {
    fun check() {
        if (!enabled) {
            throw JiraLiveDisabledException("Consulta ao Jira bloqueada: JIRA_LIVE_ENABLED não está ativo")
        }
    }
}
