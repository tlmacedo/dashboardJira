package br.sidia.painel.security

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrincipalTest {
    private val hoje = LocalDate.parse("2026-10-25")

    // ids são usernames do Jira; o diretório é validado no construtor.
    private val diretorio = RoleDirectory(
        listOf(
            Pessoa("coord.um", "Coordenador Ficticio", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
            Pessoa(
                "ex.lider",
                "Ex-Lider Ficticio",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "Alfa"),
                    AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, hoje.minusDays(1)), "Alfa"),
                ),
            ),
            Pessoa(
                "dev.sempapel",
                "Sem Papel Vigente",
                listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, hoje.minusDays(1)), "Alfa")),
            ),
        ),
    )

    @Test
    fun principalVemDoDiretorio() {
        // id conhecido: papéis do diretório na data
        assertEquals(setOf(Role.COORDENADOR), principalFor("coord.um", diretorio, hoje).roles)
        assertEquals("coord.um", principalFor("coord.um", diretorio, hoje).userId)
        // id desconhecido: conjunto vazio (fail-closed)
        assertTrue(principalFor("desconhecido", diretorio, hoje).roles.isEmpty())
        // papel encerrado ontem não vale hoje, mas valia ontem
        assertEquals(setOf(Role.USUARIO), principalFor("ex.lider", diretorio, hoje).roles)
        assertEquals(
            setOf(Role.USUARIO, Role.SQUAD_LEADER),
            principalFor("ex.lider", diretorio, hoje.minusDays(1)).roles,
        )
        // todos os papéis encerrados: vazio
        assertTrue(principalFor("dev.sempapel", diretorio, hoje).roles.isEmpty())
    }
}
