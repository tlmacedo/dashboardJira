package br.sidia.painel.jira

/** Lançada quando se tenta falar com o Jira com a trava de consulta real desligada. */
class JiraLiveDisabledException(message: String) : RuntimeException(message)

/**
 * Trava de segurança: sem a flag `JIRA_LIVE_ENABLED` ligada, nenhuma requisição ao Jira pode sair.
 * O cliente chama `check()` antes de TODA requisição (inclusive antes de cada nova tentativa).
 *
 * O estado é lido a cada `check()`, então a trava pode ser desligada no meio de um retry
 * (os testes usam `LiveGuard { ligado }` com uma variável mutável). A classe e o método são finais.
 */
class LiveGuard(private val enabled: () -> Boolean) {
    constructor(enabled: Boolean) : this({ enabled })

    fun check() {
        if (!enabled()) {
            throw JiraLiveDisabledException("Consulta ao Jira bloqueada: JIRA_LIVE_ENABLED não está ativo")
        }
    }
}
