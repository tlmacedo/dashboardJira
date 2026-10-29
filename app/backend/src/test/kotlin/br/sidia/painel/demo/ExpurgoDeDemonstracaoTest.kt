package br.sidia.painel.demo

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.config.Settings
import br.sidia.painel.lgpd.TravaDpo
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.DirecaoVinculo
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.VinculoRecord
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.testContextComBanco
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Com a flag do Jira ligada, a subida APAGA os dados fictícios da demonstração (só as linhas do manifesto: instância + prefixo + chave
 * da fixture) e nunca dado real; banco misturado sem prova de sincronização é recusado. Não escreve `sync_run`, não mexe no cursor e
 * não abre a trava do DPO. SQLite real; nenhuma rede (a flag ligada só muda as regras: nada aqui cria cliente do Jira).
 */
class ExpurgoDeDemonstracaoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()
    }

    private val zona = ZoneId.of("America/Manaus")
    private val manifesto = lerManifestoDemo("demo")
    private val relogio: Clock = Clock.fixed(manifesto.ancora.plusDays(1).atTime(12, 0).atZone(zona).toInstant(), zona)
    private val flagFalsa = Settings(dashboardDevMode = true, dashboardDemoData = true)
    private val flagVerdadeira = Settings(dashboardDevMode = true, jiraLiveEnabled = true)

    private fun contexto(): AppContext = testContextComBanco(flagFalsa, banco, clock = relogio)

    private fun comSettings(base: AppContext, s: Settings) = AppContext(
        s, base.calendar, base.roles, base.dataSource, base.jira, base.statusMap, base.campos, base.fichas, base.vinculos,
        base.labelsDeAtraso, base.regrasAuditoria, base.clock,
    )

    private fun carregarDemo(ctx: AppContext) =
        DadosDeDemonstracao(requireNotNull(ctx.dataSource), SqliteIssueStore(ctx.dataSource!!), ctx.clock, zona).carregar()

    private fun contar(ctx: AppContext, sql: String): Int = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery(sql).use { it.next(); it.getInt(1) }
    }

    private fun exec(ctx: AppContext, sql: String) = requireNotNull(ctx.dataSource).connection.use { c -> c.createStatement().use { it.executeUpdate(sql) } }

    private var seqId = 880000

    private fun issueReal(chave: String): JsonObject = buildJsonObject {
        put("id", (++seqId).toString())
        put("key", chave)
        putJsonObject("fields") {
            put("summary", "Issue real simulada")
            putJsonObject("project") { put("key", "TG") }
            putJsonObject("issuetype") { put("name", "Task") }
            putJsonObject("status") { put("name", "To Do") }
            put("created", "2026-09-01T09:00:00.000-0400")
        }
    }

    /** Dado "real": issues de prefixos e instâncias variados, com evento, worklog e vínculo, e a prova de sincronização (linha de sync_state). */
    private fun semearReal(ctx: AppContext) {
        val store = SqliteIssueStore(requireNotNull(ctx.dataSource))
        val evento = { chave: String -> listOf(ChangeEventRecord("$chave:1:0", chave, "status", "To Do", "Done", Instant.parse("2026-09-02T12:00:00Z"), "ana")) }
        val horas = listOf(WorklogRecord("5001", "ana", Instant.parse("2026-09-02T12:00:00Z"), 3600, Instant.parse("2026-09-03T12:00:00Z")))
        val vinculo = listOf(VinculoRecord("TG-1", "TG-2", "Blocks", DirecaoVinculo.OUTWARD))
        store.upsert(issueReal("TG-1"), evento("TG-1"), "samsung", worklogs = horas, vinculos = vinculo)
        store.upsert(issueReal("TG-2"), emptyList(), "samsung")
        // Mesmo prefixo da demonstração, mas FORA do manifesto: outra instância, ou a mesma instância com chave que não é da fixture.
        store.upsert(issueReal("DEMO-1"), evento("DEMO-1"), "outra")
        store.upsert(issueReal("DEMO-5000"), evento("DEMO-5000"), "samsung")
        // Prefixo da outra instância da demonstração, na instância errada.
        store.upsert(issueReal("DEMOCAT-7"), emptyList(), "samsung")
        exec(ctx, "INSERT INTO sync_state(instance, watermark, ultima_reconciliacao) VALUES ('samsung', NULL, 2000)")
    }

    private val demoTotal get() = "SELECT count(*) FROM issue WHERE (instance = 'samsung' AND key LIKE 'DEMO-%' AND key <> 'DEMO-5000') OR (instance = 'sidia' AND key LIKE 'DEMOCAT-%')"

    @Test
    fun expurgoApagaADemonstracaoPuraEOsDerivadosSoDaInstanciaDela() {
        contexto().use { demo ->
            carregarDemo(demo)
            val decisao = { inst: String, chave: String, k: String ->
                exec(demo, "INSERT INTO decisao_distribuicao(instance, tipo, issue_key, decisao, decidido_por, decidido_em, versao_regras, chave_sugestao) VALUES ('$inst', 'DISTRIBUICAO', '$chave', 'APROVADA', 'dev', 1, 'v1', '$k')")
            }
            val vinculo = { inst: String, de: String, para: String ->
                exec(demo, "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em) VALUES ('$inst', '$de', '$para', 'Blocks', 'OUTWARD', 1)")
            }
            decisao("samsung", "DEMO-3", "k1")
            decisao("outra", "DEMO-3", "k2")
            // Vínculo com UMA ponta fictícia e a outra fora das issues (ainda não sincronizada): sai; o de outra instância fica.
            vinculo("samsung", "DEMO-3", "TG-5")
            vinculo("outra", "DEMO-3", "DEMO-4")
            assertTrue(contar(demo, demoTotal) > 40)
            assertTrue(contar(demo, "SELECT count(*) FROM worklog w JOIN issue i ON i.id = w.issue_id WHERE i.key = 'DEMO-40'") > 0)

            prepararDemonstracao(comSettings(demo, flagVerdadeira))

            assertEquals(0, contar(demo, "SELECT count(*) FROM issue"))
            assertEquals(0, contar(demo, "SELECT count(*) FROM change_event"))
            assertEquals(0, contar(demo, "SELECT count(*) FROM worklog"))
            assertEquals(0, contar(demo, "SELECT count(*) FROM issue_link WHERE instance = 'samsung'"))
            assertEquals(0, contar(demo, "SELECT count(*) FROM decisao_distribuicao WHERE instance = 'samsung'"))
            assertEquals(1, contar(demo, "SELECT count(*) FROM issue_link WHERE instance = 'outra'"), "vínculo de outra instância fica")
            assertEquals(1, contar(demo, "SELECT count(*) FROM decisao_distribuicao WHERE instance = 'outra'"), "decisão de outra instância fica")
        }
    }

    @Test
    fun comSincronizacaoRealNadaEApagadoMesmoComProjetoDemoRealEmReinicios() {
        contexto().use { demo ->
            // O Jira real tem um projeto DEMO: issues DEMO-1..40 sincronizadas de verdade, com vínculo e decisão humana.
            carregarDemo(demo)
            semearReal(demo)
            exec(demo, "INSERT INTO decisao_distribuicao(instance, tipo, issue_key, decisao, decidido_por, decidido_em, versao_regras, chave_sugestao) VALUES ('samsung', 'DISTRIBUICAO', 'DEMO-3', 'APROVADA', 'ana', 1, 'v1', 'kd')")
            exec(demo, "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em) VALUES ('samsung', 'TG-5', 'DEMO-3', 'Blocks', 'OUTWARD', 1)")
            val fotos = listOf(
                "SELECT count(*) FROM issue", "SELECT count(*) FROM change_event", "SELECT count(*) FROM worklog",
                "SELECT count(*) FROM issue_link", "SELECT count(*) FROM decisao_distribuicao",
            )
            val antes = fotos.map { contar(demo, it) }
            assertTrue(contar(demo, demoTotal) > 40)
            repeat(3) {
                prepararDemonstracao(comSettings(demo, flagVerdadeira))
                assertEquals(antes, fotos.map { contar(demo, it) }, "reinício ${it + 1}: nada é apagado depois da primeira sincronização real")
            }
            assertEquals(1, contar(demo, "SELECT count(*) FROM issue_link WHERE from_key = 'TG-5' AND to_key = 'DEMO-3'"))
            assertEquals(1, contar(demo, "SELECT count(*) FROM decisao_distribuicao WHERE issue_key = 'DEMO-3'"))
        }
    }

    @Test
    fun idempotenteESemEscreverSyncRunNemMexerNoCursor() {
        contexto().use { demo ->
            carregarDemo(demo)
            semearReal(demo)
            val estado = "SELECT group_concat(instance || ':' || ifnull(watermark, 'N') || ':' || ifnull(ultima_reconciliacao, 'N')) FROM sync_state"
            fun texto(sql: String) = requireNotNull(demo.dataSource).connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getString(1) } }
            val cursor = texto(estado)
            val runs = contar(demo, "SELECT count(*) FROM sync_run")
            prepararDemonstracao(comSettings(demo, flagVerdadeira))
            val aposUma = contar(demo, "SELECT count(*) FROM issue")
            prepararDemonstracao(comSettings(demo, flagVerdadeira))
            assertEquals(aposUma, contar(demo, "SELECT count(*) FROM issue"), "subir duas vezes não muda nada")
            assertEquals(cursor, texto(estado), "o cursor (zerado pela V7) não é desfeito nem mexido")
            assertEquals(runs, contar(demo, "SELECT count(*) FROM sync_run"), "o expurgo não escreve sync_run")
        }
    }

    @Test
    fun bancoMisturadoSemProvaDeSincronizacaoERecusadoSemApagarNada() {
        contexto().use { demo ->
            carregarDemo(demo)
            // Uma issue que não é da fixture e SEM nenhuma prova de sincronização: banco desconhecido.
            SqliteIssueStore(requireNotNull(demo.dataSource)).upsert(issueReal("TG-9"), emptyList(), "samsung")
            val total = contar(demo, "SELECT count(*) FROM issue")
            val e = assertFailsWith<DemoRecusadaException> { prepararDemonstracao(comSettings(demo, flagVerdadeira)) }
            assertEquals("BANCO_DESCONHECIDO_SEM_SINCRONIZACAO", e.codigo)
            assertEquals(total, contar(demo, "SELECT count(*) FROM issue"), "recusou sem apagar às cegas")
            assertEquals(1, contar(demo, "SELECT count(*) FROM issue WHERE key = 'TG-9'"))
            assertTrue(contar(demo, demoTotal) > 40, "a demonstração também ficou (rollback)")
        }
    }

    @Test
    fun soDemonstracaoOuBancoVazioNaoRecusa() {
        contexto().use { demo ->
            carregarDemo(demo)
            prepararDemonstracao(comSettings(demo, flagVerdadeira))
            assertEquals(0, contar(demo, "SELECT count(*) FROM issue"))
        }
        contexto().use { vazio ->
            prepararDemonstracao(comSettings(vazio, flagVerdadeira))
            assertEquals(0, contar(vazio, "SELECT count(*) FROM issue"))
            assertEquals(0, contar(vazio, "SELECT count(*) FROM sync_run"))
        }
    }

    @Test
    fun retencaoEDemonstracaoNaoContamComoSincronizacaoNemComoDadoDesconhecido() {
        contexto().use { demo ->
            carregarDemo(demo)
            exec(demo, "INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('-', 'RETENCAO', 1, 'OK')")
            exec(demo, "INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('-', 1)")
            prepararDemonstracao(comSettings(demo, flagVerdadeira))
            assertEquals(0, contar(demo, "SELECT count(*) FROM issue"))
            assertEquals(1, contar(demo, "SELECT count(*) FROM sync_run"))
        }
    }

    @Test
    fun aTravaDoDpoContinuaFechadaComAFlagVerdadeira() {
        contexto().use { demo ->
            carregarDemo(demo)
            val ctx = comSettings(demo, flagVerdadeira)
            prepararDemonstracao(ctx)
            val trava = TravaDpo(ctx.settings, ctx.dataSource)
            assertFalse(trava.liberada, "flag verdadeira sem aceite: fechada, mesmo com o banco vazio e em modo dev")
        }
    }

    @Test
    fun flagVerdadeiraNuncaCarregaDemonstracao() {
        contexto().use { vazio ->
            assertFailsWith<DemoRecusadaException> { prepararDemonstracao(comSettings(vazio, Settings(dashboardDevMode = true, jiraLiveEnabled = true, dashboardDemoData = true))) }
            assertEquals(0, contar(vazio, "SELECT count(*) FROM issue"))
            // E o ambiente recusa a combinação ainda antes: demonstração exige dev e flag falsa.
            val e = assertFailsWith<IllegalArgumentException> {
                Settings.fromEnv(mapOf("DASHBOARD_DEMO_DATA" to "true", "DASHBOARD_DEV_MODE" to "true", "JIRA_LIVE_ENABLED" to "true"))
            }
            assertTrue("DASHBOARD_DEMO_DATA" in e.message.orEmpty())
        }
    }

    @Test
    fun producaoComFlagVerdadeiraTambemExpurga() {
        contexto().use { demo ->
            carregarDemo(demo)
            prepararDemonstracao(comSettings(demo, Settings(dashboardDevMode = false, jiraLiveEnabled = true)))
            assertEquals(0, contar(demo, "SELECT count(*) FROM issue"))
        }
    }

    @Test
    fun demonstracaoNaoCarregaSobreBancoQueJaTeveTentativaDeSincronizacao() {
        // I-3 do PR #50: tentativa que falhou (sem issue) + demonstração carregada + flag religada deixaria 45 issues fictícias.
        val demoPura = Settings(dashboardDevMode = true, dashboardDemoData = true)
        for (sql in listOf(
            "INSERT INTO sync_run(instance, kind, started_at, status, codigo_erro) VALUES ('samsung', 'INCREMENTAL', 1, 'FALHA', 'HTTP_401')",
            "INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('samsung', 'RECONCILIACAO', 1, 'EM_ANDAMENTO')",
            "INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('samsung', 1)",
        )) {
            contexto().use { ctx ->
                exec(ctx, sql)
                val e = assertFailsWith<DemoRecusadaException> { prepararDemonstracao(comSettings(ctx, demoPura)) }
                assertEquals("BANCO_COM_SINCRONIZACAO_REAL", e.codigo)
                assertEquals(0, contar(ctx, "SELECT count(*) FROM issue"), "nada foi carregado")
                // E o ciclo do cenário não deixa fictício com a flag ligada.
                prepararDemonstracao(comSettings(ctx, flagVerdadeira))
                assertEquals(0, contar(ctx, "SELECT count(*) FROM issue WHERE key LIKE 'DEMO%'"))
            }
        }
    }

    @Test
    fun retencaoEEstadoDaRetencaoNaoImpedemACargaDaDemonstracao() {
        contexto().use { ctx ->
            exec(ctx, "INSERT INTO sync_run(instance, kind, started_at, status) VALUES ('-', 'RETENCAO', 1, 'OK')")
            exec(ctx, "INSERT INTO sync_state(instance, falhas_desde_run_id) VALUES ('-', 1)")
            prepararDemonstracao(comSettings(ctx, flagFalsa))
            assertTrue(contar(ctx, "SELECT count(*) FROM issue") > 40)
        }
    }

    @Test
    fun demonstracaoPuraComFlagFalsaContinuaFuncionando() {
        contexto().use { ctx ->
            prepararDemonstracao(comSettings(ctx, flagFalsa))
            val n = contar(ctx, "SELECT count(*) FROM issue")
            assertTrue(n > 40, "carregou a demonstração: $n")
            prepararDemonstracao(comSettings(ctx, flagFalsa))
            assertEquals(n, contar(ctx, "SELECT count(*) FROM issue"))
        }
    }
}
