package br.sidia.painel

import br.sidia.painel.config.Settings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetaTest {
    private fun meta(env: Map<String, String>): String {
        var corpo = ""
        val s = Settings.fromEnv(env)
        testApplication {
            application { module(s, testContext(s)) }
            corpo = client.get("/api/meta").bodyAsText()
        }
        return corpo
    }

    @Test
    fun metaEmProducaoNaoTemBanner() {
        assertEquals(
            """{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null,"aguardandoCargaDoJira":false}""",
            meta(mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE),
        )
    }

    @Test
    fun metaEmDevTemBanner() {
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO","aguardandoCargaDoJira":false}""",
            meta(emptyMap()),
        )
    }

    @Test
    fun faixaComDadosDeDemonstracao() {
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO: dados fictícios de demonstração","aguardandoCargaDoJira":false}""",
            meta(mapOf("DASHBOARD_DEMO_DATA" to "true")),
        )
    }

    private fun metaComBanco(s: Settings, antes: (AppContext) -> Unit = {}): String {
        var corpo = ""
        val banco = BancoEmArquivo()
        try {
            testContextComBanco(s, banco).use { ctx ->
                antes(ctx)
                testApplication {
                    application { module(s, ctx) }
                    corpo = client.get("/api/meta").bodyAsText()
                }
            }
        } finally {
            banco.apagar()
        }
        return corpo
    }

    @Test
    fun flagLigadaSemNenhumaCargaAguardaAPrimeiraCargaESemFaixaDeFicticios() {
        val corpo = metaComBanco(Settings(dashboardDevMode = true, jiraLiveEnabled = true))
        assertTrue("\"aguardandoCargaDoJira\":true" in corpo, corpo)
        assertFalse("fictícios" in corpo, "com a flag ligada não há faixa de dados fictícios")
    }

    @Test
    fun flagLigadaComSincronizacaoRealNaoAguarda() {
        val corpo = metaComBanco(Settings(dashboardDevMode = true, jiraLiveEnabled = true)) { ctx ->
            ctx.dataSource!!.connection.use { c ->
                c.createStatement().use { it.executeUpdate("INSERT INTO sync_state(instance, watermark, ultima_reconciliacao) VALUES ('samsung', NULL, 1)") }
            }
        }
        assertTrue("\"aguardandoCargaDoJira\":false" in corpo, corpo)
    }

    @Test
    fun linhaDaRetencaoNaoContaComoCarga() {
        val corpo = metaComBanco(Settings(dashboardDevMode = true, jiraLiveEnabled = true)) { ctx ->
            ctx.dataSource!!.connection.use { c ->
                c.createStatement().use { it.executeUpdate("INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('-', 1)") }
            }
        }
        assertTrue("\"aguardandoCargaDoJira\":true" in corpo, corpo)
    }

    @Test
    fun flagDesligadaNuncaAguarda() {
        assertTrue("\"aguardandoCargaDoJira\":false" in metaComBanco(Settings(dashboardDevMode = true)))
    }

    @Test
    fun metaDevComLiveTemBannerDeDadosReais() {
        assertEquals(
            """{"devMode":true,"liveEnabled":true,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO: dados reais do Jira sem login","aguardandoCargaDoJira":false}""",
            meta(mapOf("JIRA_LIVE_ENABLED" to "true")),
        )
    }
}
