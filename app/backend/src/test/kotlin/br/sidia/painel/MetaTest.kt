package br.sidia.painel

import br.sidia.painel.config.Settings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaTest {
    private fun meta(env: Map<String, String>): String {
        var corpo = ""
        testApplication {
            application { module(Settings.fromEnv(env)) }
            corpo = client.get("/api/meta").bodyAsText()
        }
        return corpo
    }

    @Test
    fun metaEmProducaoNaoTemBanner() {
        assertEquals(
            """{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null}""",
            meta(mapOf("DASHBOARD_DEV_MODE" to "false")),
        )
    }

    @Test
    fun metaEmDevTemBanner() {
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO"}""",
            meta(emptyMap()),
        )
    }

    @Test
    fun metaDevComLiveTemBannerDeDadosReais() {
        assertEquals(
            """{"devMode":true,"liveEnabled":true,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO: dados reais do Jira sem login"}""",
            meta(mapOf("JIRA_LIVE_ENABLED" to "true")),
        )
    }
}
