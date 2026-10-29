package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.sync.SincronizacaoReal
import br.sidia.painel.sync.SqliteIssueStore
import io.ktor.client.request.get
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.lang.reflect.Proxy
import java.sql.SQLException
import javax.sql.DataSource
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

    private val live = Settings(dashboardDevMode = true, jiraLiveEnabled = true)

    private fun aguardaCom(sql: String): Boolean {
        val corpo = metaComBanco(live) { ctx ->
            ctx.dataSource!!.connection.use { c -> c.createStatement().use { it.executeUpdate(sql) } }
        }
        return "\"aguardandoCargaDoJira\":true" in corpo
    }

    @Test
    fun tentativaQueFalhouOuEstaEmAndamentoNaoTiraOAviso() {
        assertTrue(aguardaCom("INSERT INTO sync_run(instance, kind, started_at, status, codigo_erro) VALUES ('samsung', 'INCREMENTAL', 1, 'FALHA', 'HTTP_401')"))
        assertTrue(aguardaCom("INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('samsung', 'RECONCILIACAO', 1, 'EM_ANDAMENTO')"))
        assertTrue(aguardaCom("INSERT INTO sync_run(instance, kind, started_at, status, codigo_erro) VALUES ('sidia', 'INCREMENTAL', 1, 'FALHA', 'INSTANCIA_SEM_CREDENCIAL')"))
        // Estado só de suspensão (sem marca d'água nem reconciliação) também não é carga.
        assertTrue(aguardaCom("INSERT INTO sync_state(instance, suspensa_codigo, suspensa_em) VALUES ('samsung', 'AUTH', 1)"))
    }

    @Test
    fun cargaConcluidaTiraOAviso() {
        assertFalse(aguardaCom("INSERT INTO sync_run(instance, kind, started_at, finished_at, status) VALUES ('samsung', 'INCREMENTAL', 1, 2, 'OK')"))
        assertFalse(aguardaCom("INSERT INTO sync_state(instance, watermark) VALUES ('samsung', 5)"))
    }

    @Test
    fun issueDeDadoRealTiraOAviso() {
        val issue = buildJsonObject {
            put("id", "990001")
            put("key", "TG-1")
            putJsonObject("fields") {
                put("summary", "Issue real simulada")
                putJsonObject("project") { put("key", "TG") }
                putJsonObject("issuetype") { put("name", "Task") }
                putJsonObject("status") { put("name", "To Do") }
                put("created", "2026-09-01T09:00:00.000-0400")
            }
        }
        val corpo = metaComBanco(live) { ctx -> SqliteIssueStore(ctx.dataSource!!).upsert(issue, emptyList(), "samsung") }
        assertTrue("\"aguardandoCargaDoJira\":false" in corpo, corpo)
    }

    @Test
    fun erroDeBancoNaoInventaAviso() {
        val quebrado = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DataSource::class.java)) { _, _, _ ->
            throw SQLException("indisponível")
        } as DataSource
        assertFalse(SincronizacaoReal.aguardandoPrimeiraCarga(quebrado))
        assertFalse(SincronizacaoReal.aguardandoPrimeiraCarga(null))
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
