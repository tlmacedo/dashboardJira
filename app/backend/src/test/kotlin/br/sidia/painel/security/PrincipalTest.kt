package br.sidia.painel.security

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrincipalTest {
    private val hoje = LocalDate.parse("2026-10-25")

    // ids us-009/us-010 são válidos; o diretório é validado no construtor.
    private val diretorio = RoleDirectory(
        listOf(
            Pessoa("co-001", "Coordenador Ficticio", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null)))),
            Pessoa(
                "us-009",
                "Ex-Lider Ficticio",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, Vigencia(null, null), "Alfa"),
                    AtribuicaoPapel(Role.SQUAD_LEADER, Vigencia(null, hoje.minusDays(1)), "Alfa"),
                ),
            ),
            Pessoa(
                "us-010",
                "Sem Papel Vigente",
                listOf(AtribuicaoPapel(Role.USUARIO, Vigencia(null, hoje.minusDays(1)), "Alfa")),
            ),
        ),
    )

    @Test
    fun principalVemDoDiretorio() {
        // id conhecido: papéis do diretório na data
        assertEquals(setOf(Role.COORDENADOR), principalFor("co-001", diretorio, hoje).roles)
        assertEquals("co-001", principalFor("co-001", diretorio, hoje).userId)
        // id desconhecido: conjunto vazio (fail-closed)
        assertTrue(principalFor("zz-999", diretorio, hoje).roles.isEmpty())
        // papel encerrado ontem não vale hoje, mas valia ontem
        assertEquals(setOf(Role.USUARIO), principalFor("us-009", diretorio, hoje).roles)
        assertEquals(
            setOf(Role.USUARIO, Role.SQUAD_LEADER),
            principalFor("us-009", diretorio, hoje.minusDays(1)).roles,
        )
        // todos os papéis encerrados: vazio
        assertTrue(principalFor("us-010", diretorio, hoje).roles.isEmpty())
    }
}
