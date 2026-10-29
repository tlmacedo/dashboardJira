package br.sidia.painel

import br.sidia.painel.config.Settings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MetaTest {
    private val versao = Versao("2026.11.1", "abc1234", "2026-11-03T12:00:00Z")
    private val fim = ""","versao":"2026.11.1","rotuloDeVersao":"v2026.11.1"}"""

    private fun meta(env: Map<String, String>): String {
        var corpo = ""
        val s = Settings.fromEnv(env)
        testApplication {
            application { module(s, testContext(s), versao = versao) }
            corpo = client.get("/api/meta").bodyAsText()
        }
        return corpo
    }

    @Test
    fun metaEmProducaoNaoTemBanner() {
        assertEquals(
            """{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null$fim""",
            meta(mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE),
        )
    }

    @Test
    fun metaTemVersao() {
        assertEquals(
            """{"devMode":false,"liveEnabled":false,"zone":"America/Manaus","banner":null,"versao":"2026.11.1","rotuloDeVersao":"v2026.11.1"}""",
            meta(mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE),
        )
    }

    @Test
    fun metaEmDevTemBanner() {
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO"$fim""",
            meta(emptyMap()),
        )
    }

    @Test
    fun faixaComDadosDeDemonstracao() {
        assertEquals(
            """{"devMode":true,"liveEnabled":false,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO: dados fictícios de demonstração"$fim""",
            meta(mapOf("DASHBOARD_DEMO_DATA" to "true")),
        )
    }

    @Test
    fun metaDevComLiveTemBannerDeDadosReais() {
        assertEquals(
            """{"devMode":true,"liveEnabled":true,"zone":"America/Manaus","banner":"MODO DESENVOLVIMENTO: dados reais do Jira sem login"$fim""",
            meta(mapOf("JIRA_LIVE_ENABLED" to "true")),
        )
    }

    @Test
    fun metaSemCommitNemCaminho() {
        val corpo = meta(mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE)
        assertFalse("abc1234" in corpo, "o commit não pode sair na rota pública")
        assertFalse("2026-11-03" in corpo, "a data de build não pode sair na rota pública")
        assertFalse(Regex("""[A-Za-z]:\\|/(home|opt|var|tmp|etc|usr)\b""").containsMatchIn(corpo), "nenhum caminho na resposta")
    }

    @Test
    fun metaMantemCamposAnteriores() {
        val corpo = meta(emptyMap())
        val campos = Regex("\"([a-zA-Z]+)\":").findAll(corpo).map { it.groupValues[1] }.toList()
        assertEquals(listOf("devMode", "liveEnabled", "zone", "banner", "versao", "rotuloDeVersao"), campos)
    }

    @Test
    fun metaContinuaPublico() {
        val s = Settings.fromEnv(mapOf("DASHBOARD_DEV_MODE" to "false") + ENV_OIDC_DE_TESTE)
        testApplication {
            application { module(s, testContext(s), versao = versao) }
            // produção, sem sessão: a rota segue pública
            assertEquals(HttpStatusCode.OK, client.get("/api/meta").status)
        }
    }
}
