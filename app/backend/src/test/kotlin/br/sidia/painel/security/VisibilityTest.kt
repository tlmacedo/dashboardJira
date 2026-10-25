package br.sidia.painel.security

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibilityTest {
    private val hoje = LocalDate.of(2026, 10, 25)
    private val sempre = Vigencia(null, null)
    private val todos = setOf("u1", "u2", "u3", "u4", "lider", "coord")

    private val dir = RoleDirectory(
        listOf(
            Pessoa("u1", "U1", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("u2", "U2", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("u3", "U3", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa("u4", "U4", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa(
                "lider", "Lider",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, sempre, "A"),
                    AtribuicaoPapel(Role.SQUAD_LEADER, sempre, "A"),
                ),
            ),
            Pessoa("coord", "Coord", listOf(AtribuicaoPapel(Role.COORDENADOR, sempre, null))),
        ),
    )

    @Test
    fun usuarioVeSoOProprio() {
        val p = Principal("u3", setOf(Role.USUARIO))
        assertEquals(setOf("u3"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun squadLeaderVeOProprioESeuSquad() {
        val p = Principal("lider", setOf(Role.USUARIO, Role.SQUAD_LEADER))
        assertEquals(setOf("lider", "u1", "u2"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun coordenadorEGerenteVeemTodos() {
        assertEquals(todos, Visibility.visibleUserIds(Principal("coord", setOf(Role.COORDENADOR)), dir, hoje, todos))
        assertEquals(todos, Visibility.visibleUserIds(Principal("g", setOf(Role.GERENTE)), dir, hoje, todos))
    }
}
