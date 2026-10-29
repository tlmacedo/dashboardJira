package br.sidia.painel.security

import br.sidia.painel.ENV_OIDC_DE_TESTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plano 13, Tarefa 2: o username do Jira sai da claim do provedor por UMA estratégia por instalação, sem cascata.
 * Aqui só a lógica pura e a leitura da variável; o login de ponta a ponta está em `UsernamePorProvedorOidcTest`.
 */
class ExtratorDeUsernameTest {
    private fun extrair(valor: Any?, estrategia: EstrategiaDeUsername, claim: String = "preferred_username") =
        ExtratorDeUsername.extrair(mapOf(claim to valor), claim, estrategia)

    @Test
    fun claimDiretoNormaliza() {
        assertEquals("ana.silva", extrair("  Ana.Silva ", EstrategiaDeUsername.CLAIM_DIRETO))
    }

    @Test
    fun claimDiretoNaoAdivinhaNada() {
        // Um UPN com claim_direto continua UPN: não vira "ana.silva" (poderia casar com outra pessoa).
        assertEquals("ana.silva@outro.invalid", extrair("Ana.Silva@Outro.invalid", EstrategiaDeUsername.CLAIM_DIRETO))
    }

    @Test
    fun parteLocalDoEmail() {
        assertEquals("ana.silva", extrair("Ana.Silva@Empresa.INVALID", EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL))
    }

    @Test
    fun parteLocalExigeExatamenteUmArroba() {
        for (ruim in listOf("ana.silva", "a@b@c.invalid", "@empresa.invalid", " @x", "ana@@x")) {
            assertNull(extrair(ruim, EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL), ruim)
        }
    }

    @Test
    fun contaWindows() {
        assertEquals("ana.silva", extrair("EMPRESA\\Ana.Silva", EstrategiaDeUsername.CONTA_WINDOWS, "winaccountname"))
    }

    @Test
    fun contaWindowsExigeExatamenteUmaBarra() {
        for (ruim in listOf("ana.silva", "A\\B\\ana", "EMPRESA\\", "\\ana", "EMPRESA/ana")) {
            assertNull(extrair(ruim, EstrategiaDeUsername.CONTA_WINDOWS), ruim)
        }
    }

    @Test
    fun claimAusenteVaziaOuQueNaoEhTextoDaNulo() {
        for (e in EstrategiaDeUsername.entries) {
            assertNull(ExtratorDeUsername.extrair(emptyMap(), "preferred_username", e))
            assertNull(extrair("", e), "vazia $e")
            assertNull(extrair("   ", e), "só espaços $e")
            assertNull(extrair(null, e), "nula $e")
            assertNull(extrair(42, e), "número $e")
            assertNull(extrair(listOf("ana.silva"), e), "lista $e")
        }
    }

    @Test
    fun idReservadoContinuaReservadoMasNaoViraPapel() {
        // O extrator só devolve o username; quem barra o id reservado é o diretório de papéis (teste de login).
        assertEquals("admin", extrair("Admin", EstrategiaDeUsername.CLAIM_DIRETO))
        assertEquals("admin", extrair("EMPRESA\\Admin", EstrategiaDeUsername.CONTA_WINDOWS))
    }

    // ---------------------------------------------------------------- OIDC_USERNAME_ESTRATEGIA

    private fun lerEstrategia(valor: String?): EstrategiaDeUsername {
        val env = ENV_OIDC_DE_TESTE + (if (valor == null) emptyMap() else mapOf("OIDC_USERNAME_ESTRATEGIA" to valor))
        return OidcSettings.fromEnv(env, devMode = false)!!.estrategiaUsername
    }

    @Test
    fun padraoEhClaimDireto() {
        assertEquals(EstrategiaDeUsername.CLAIM_DIRETO, lerEstrategia(null))
        assertEquals(EstrategiaDeUsername.CLAIM_DIRETO, lerEstrategia("   "))
    }

    @Test
    fun lerAsTresEstrategias() {
        assertEquals(EstrategiaDeUsername.CLAIM_DIRETO, lerEstrategia("claim_direto"))
        assertEquals(EstrategiaDeUsername.PARTE_LOCAL_DO_EMAIL, lerEstrategia(" Parte_Local_Do_Email "))
        assertEquals(EstrategiaDeUsername.CONTA_WINDOWS, lerEstrategia("conta_windows"))
    }

    @Test
    fun estrategiaInvalidaNaoSobe() {
        val segredoDeTeste = "valor-que-nao-pode-vazar"
        val e = assertFailsWith<IllegalArgumentException> { lerEstrategia(segredoDeTeste) }
        assertTrue("OIDC_USERNAME_ESTRATEGIA" in e.message!!, e.message)
        assertFalse(segredoDeTeste in e.message!!, "a mensagem não pode ecoar o valor")
    }
}
