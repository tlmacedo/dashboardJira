package br.sidia.painel.security

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibilityTest {
    private val hoje = LocalDate.of(2026, 10, 25)
    private val sempre = Vigencia(null, null)
    private val todos = setOf("us-001", "us-002", "us-003", "us-004", "sl-001", "co-001")

    private val dir = RoleDirectory(
        listOf(
            Pessoa("us-001", "U1", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("us-002", "U2", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("us-003", "U3", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa("us-004", "U4", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa(
                "sl-001", "Lider",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, sempre, "A"),
                    AtribuicaoPapel(Role.SQUAD_LEADER, sempre, "A"),
                ),
            ),
            Pessoa("co-001", "Coord", listOf(AtribuicaoPapel(Role.COORDENADOR, sempre, null))),
        ),
    )

    @Test
    fun usuarioVeSoOProprio() {
        val p = Principal("us-003", setOf(Role.USUARIO))
        assertEquals(setOf("us-003"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun squadLeaderVeOProprioESeuSquad() {
        val p = Principal("sl-001", setOf(Role.USUARIO, Role.SQUAD_LEADER))
        assertEquals(setOf("sl-001", "us-001", "us-002"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun coordenadorEGerenteVeemTodos() {
        assertEquals(todos, Visibility.visibleUserIds(Principal("co-001", setOf(Role.COORDENADOR)), dir, hoje, todos))
        assertEquals(todos, Visibility.visibleUserIds(Principal("ge-001", setOf(Role.GERENTE)), dir, hoje, todos))
    }
}
