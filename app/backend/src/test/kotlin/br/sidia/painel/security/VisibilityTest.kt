package br.sidia.painel.security

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os ids do diretório são os usernames do Jira: o conjunto `todos` é o dos `assignee` das issues,
 * então a visibilidade compara os MESMOS valores dos dois lados.
 */
class VisibilityTest {
    private val hoje = LocalDate.of(2026, 10, 25)
    private val sempre = Vigencia(null, null)
    private val todos = setOf("dev.alfa1", "dev.alfa2", "dev.beta1", "dev.beta2", "lider.alfa", "coord.um")

    private val dir = RoleDirectory(
        listOf(
            Pessoa("dev.alfa1", "U1", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("dev.alfa2", "U2", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "A"))),
            Pessoa("dev.beta1", "U3", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa("dev.beta2", "U4", listOf(AtribuicaoPapel(Role.USUARIO, sempre, "B"))),
            Pessoa(
                "lider.alfa", "Lider",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, sempre, "A"),
                    AtribuicaoPapel(Role.SQUAD_LEADER, sempre, "A"),
                ),
            ),
            Pessoa("coord.um", "Coord", listOf(AtribuicaoPapel(Role.COORDENADOR, sempre, null))),
        ),
    )

    @Test
    fun usuarioVeSoOProprioUsername() {
        val p = Principal("dev.beta1", setOf(Role.USUARIO))
        assertEquals(setOf("dev.beta1"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun squadLeaderVeOProprioEOsUsernamesDoSeuSquad() {
        val p = Principal("lider.alfa", setOf(Role.USUARIO, Role.SQUAD_LEADER))
        assertEquals(setOf("lider.alfa", "dev.alfa1", "dev.alfa2"), Visibility.visibleUserIds(p, dir, hoje, todos))
    }

    @Test
    fun squadLeaderVeOSquadDaDataConsultada() {
        // dev.alfa2 saiu do Alfa em 01/07: o líder só o vê nas datas em que ele era membro.
        val dirMudanca = RoleDirectory(
            listOf(
                Pessoa("lider.alfa", "Lider", listOf(AtribuicaoPapel(Role.SQUAD_LEADER, sempre, "A"))),
                Pessoa(
                    "dev.alfa2", "U2",
                    listOf(
                        AtribuicaoPapel(Role.USUARIO, Vigencia(null, LocalDate.of(2026, 6, 30)), "A"),
                        AtribuicaoPapel(Role.USUARIO, Vigencia(LocalDate.of(2026, 7, 1), null), "B"),
                    ),
                ),
            ),
        )
        val p = Principal("lider.alfa", setOf(Role.SQUAD_LEADER))
        val usernames = setOf("lider.alfa", "dev.alfa2")
        assertEquals(usernames, Visibility.visibleUserIds(p, dirMudanca, LocalDate.of(2026, 6, 30), usernames))
        assertEquals(setOf("lider.alfa"), Visibility.visibleUserIds(p, dirMudanca, LocalDate.of(2026, 7, 1), usernames))
    }

    @Test
    fun coordenadorEGerenteVeemTodos() {
        assertEquals(todos, Visibility.visibleUserIds(Principal("coord.um", setOf(Role.COORDENADOR)), dir, hoje, todos))
        assertEquals(todos, Visibility.visibleUserIds(Principal("gerente.exemplo", setOf(Role.GERENTE)), dir, hoje, todos))
    }

    @Test
    fun assigneeForaDoDiretorioNaoApareceParaQuemNaoEhCoordenadorNemGerente() {
        // Fail-closed: o assignee "fantasma" existe nas issues mas não no diretório.
        val comFantasma = todos + "fantasma"
        val usuario = Principal("dev.alfa1", setOf(Role.USUARIO))
        val lider = Principal("lider.alfa", setOf(Role.USUARIO, Role.SQUAD_LEADER))
        assertEquals(setOf("dev.alfa1"), Visibility.visibleUserIds(usuario, dir, hoje, comFantasma))
        assertEquals(
            setOf("lider.alfa", "dev.alfa1", "dev.alfa2"),
            Visibility.visibleUserIds(lider, dir, hoje, comFantasma),
        )
        // Quem vê todos continua vendo (a decisão é do papel, não do diretório).
        assertEquals(comFantasma, Visibility.visibleUserIds(Principal("coord.um", setOf(Role.COORDENADOR)), dir, hoje, comFantasma))
    }

    @Test
    fun usuarioSemAssigneeNasIssuesNaoVeNada() {
        val p = Principal("dev.beta2", setOf(Role.USUARIO))
        assertEquals(emptySet(), Visibility.visibleUserIds(p, dir, hoje, setOf("dev.alfa1")))
    }
}
