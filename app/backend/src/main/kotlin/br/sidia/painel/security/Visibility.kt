package br.sidia.painel.security

import java.time.LocalDate

/** Quais pessoas cada papel pode ver. Devolve conjunto: a ordenação alfabética é de quem exibe. */
object Visibility {
    fun visibleUserIds(p: Principal, dir: RoleDirectory, today: LocalDate, allUsers: Set<String>): Set<String> {
        if (Role.COORDENADOR in p.roles || Role.GERENTE in p.roles) return allUsers
        val visiveis = mutableSetOf(p.userId)
        if (Role.SQUAD_LEADER in p.roles) {
            dir.squadsLedBy(p.userId, today).forEach { visiveis += dir.membersOf(it, today) }
        }
        return visiveis.intersect(allUsers)
    }
}
