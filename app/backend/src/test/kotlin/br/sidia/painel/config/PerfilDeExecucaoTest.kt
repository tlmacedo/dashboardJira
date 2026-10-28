package br.sidia.painel.config

import br.sidia.painel.ENV_OIDC_DE_TESTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerfilDeExecucaoTest {
    @Test
    fun semPerfilNaoMudaNada() {
        val env = mapOf("A" to "1", "DASHBOARD_DEV_MODE" to "true")
        assertEquals(env, PerfilDeExecucao.aplicar(null, env))
        assertEquals(env, PerfilDeExecucao.aplicar("", env))
        assertEquals(env, PerfilDeExecucao.aplicar("   ", env))
        // Sem perfil o padrão do desenvolvimento continua valendo (modo dev ligado).
        assertTrue(Settings.fromEnv(PerfilDeExecucao.aplicar(null, emptyMap())).dashboardDevMode)
    }

    @Test
    fun producaoDesligaModoDevQuandoAusente() {
        val s = Settings.fromEnv(PerfilDeExecucao.aplicar(PerfilDeExecucao.PRODUCAO, ENV_OIDC_DE_TESTE))
        assertFalse(s.dashboardDevMode)
    }

    @Test
    fun producaoTrataVazioComoAusente() {
        for (vazio in listOf("", "   ")) {
            val s = Settings.fromEnv(PerfilDeExecucao.aplicar("producao", mapOf("DASHBOARD_DEV_MODE" to vazio) + ENV_OIDC_DE_TESTE))
            assertFalse(s.dashboardDevMode, "valor '$vazio'")
        }
    }

    @Test
    fun producaoRespeitaValorExplicito() {
        // Decisão registrada: quem escreve a linha de modo dev no pacote a quer (a faixa vermelha continua).
        val s = Settings.fromEnv(PerfilDeExecucao.aplicar("producao", mapOf("DASHBOARD_DEV_MODE" to "true")))
        assertTrue(s.dashboardDevMode)
        val f = Settings.fromEnv(PerfilDeExecucao.aplicar("producao", mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE))
        assertFalse(f.dashboardDevMode)
    }

    @Test
    fun perfilDesconhecidoFalhaCitandoSoONome() {
        val e = assertFailsWith<IllegalArgumentException> { PerfilDeExecucao.aplicar("segredo-qualquer", emptyMap()) }
        assertTrue(e.message!!.contains(PerfilDeExecucao.PROPRIEDADE))
        assertFalse(e.message!!.contains("segredo-qualquer"))
    }

    @Test
    fun producaoNaoMexeNaFlagDoJira() {
        val chaveJira = "JIRA_LIVE_" + "ENABLED"
        val saida = PerfilDeExecucao.aplicar("producao", emptyMap())
        assertTrue(saida.keys.none { it.startsWith("JIRA_") }, "o perfil não acrescenta variável JIRA_*")
        val comFlag = PerfilDeExecucao.aplicar("producao", mapOf(chaveJira to "false"))
        assertEquals("false", comFlag[chaveJira])
        assertEquals(setOf(chaveJira, "DASHBOARD_DEV_MODE"), comFlag.keys)
    }

    @Test
    fun producaoNaoLigaDemonstracao() {
        // Dados de demonstração exigem modo dev: o perfil de produção nunca os habilita.
        val saida = PerfilDeExecucao.aplicar("producao", ENV_OIDC_DE_TESTE)
        assertFalse(Settings.fromEnv(saida).dashboardDemoData)
    }
}
