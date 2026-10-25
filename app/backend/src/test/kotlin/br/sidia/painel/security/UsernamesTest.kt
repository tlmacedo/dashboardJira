package br.sidia.painel.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsernamesTest {
    @Test
    fun normalizaCaixaEEspacos() {
        assertEquals("ana.silva", normalizarUsername(" Ana.Silva "))
        assertEquals("ana.silva", normalizarUsername("ANA.SILVA"))
        // Locale.ROOT: o I maiúsculo vira i, mesmo com locale turco no servidor
        assertEquals("iara", normalizarUsername("IARA"))
    }

    @Test
    fun usernameVazioFalha() {
        for (raw in listOf("", "   ", "\t\n")) {
            val e = assertFailsWith<IllegalArgumentException> { normalizarUsername(raw) }
            assertEquals("username vazio", e.message)
        }
    }

    @Test
    fun naoValidaOFormato() {
        // o formato é do RoleDirectory/JqlValues: aqui só aparar e minúsculas
        assertEquals("a b!", normalizarUsername(" A B! "))
    }

    @Test
    fun principalUsaUsernameNormalizado() {
        val hoje = java.time.LocalDate.parse("2026-10-25")
        val diretorio = RoleDirectory(
            listOf(Pessoa("coord.um", "Coordenador Ficticio", listOf(AtribuicaoPapel(Role.COORDENADOR, Vigencia(null, null))))),
        )
        val p = principalFor("  Coord.UM ", diretorio, hoje)
        assertEquals("coord.um", p.userId)
        assertEquals(setOf(Role.COORDENADOR), p.roles)
        // id vazio: papéis vazios (fail-closed), sem exceção
        val vazio = principalFor("   ", diretorio, hoje)
        assertTrue(vazio.roles.isEmpty())
        assertEquals("", vazio.userId)
    }
}
