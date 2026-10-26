package br.sidia.painel.sync

import br.sidia.painel.AppContext
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Reconciliação diária contra PostgreSQL real (Testcontainers) e Jira simulado por MockEngine.
 * "Hoje" é 2026-10-30 em Manaus (corte de retenção: 2024-10-30 00:00 em Manaus); nenhuma rede real.
 */
class ReconciliacaoTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC)
    private val formato = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx")

    /** Estado simulado do Jira: chave -> (id numérico, updated). */
    private val noJira = LinkedHashMap<String, Pair<Long, Instant>>()
    private val corpos = CopyOnWriteArrayList<JsonObject>()

    /** Issues que existem no Jira mas que a paginação por startAt "pulou" na lista leve (só aparecem em `key in`). */
    private val ocultasNaLista = mutableSetOf<String>()
    private var ctx: AppContext? = null
    private lateinit var store: PostgresIssueStore
    private lateinit var estado: PostgresSyncStateStore

    @AfterTest
    fun encerrar() {
        ctx?.close()
        ctx = null
    }

    private fun texto(i: Instant) = i.atOffset(ZoneOffset.ofHours(-4)).format(formato)

    private fun instante(hhmmUtc: String) = Instant.parse("2026-10-30T${hhmmUtc}:00Z")

    private fun leve(chave: String, id: Long, updated: Instant) =
        """{"id":"$id","key":"$chave","fields":{"updated":"${texto(updated)}"}}"""

    private fun completa(chave: String, id: Long, updated: Instant): String {
        val n = chave.substringAfter('-')
        return """{"id":"$id","key":"$chave","fields":{"summary":"s$n","project":{"key":"TG"},"issuetype":{"name":"Task"},
            "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"${texto(updated)}","labels":[]},
            "changelog":{"total":1,"histories":[{"id":"9${n}","created":"2026-10-29T09:00:00.000-0400",
            "author":{"name":"ana"},"items":[{"field":"status","from":"1","to":"3","fromString":"To Do","toString":"In Progress"}]}]}}"""
    }

    private fun montar(live: Boolean = true, sync: SyncSettings = SyncSettings()): SyncService {
        val settings = Settings(
            jiraLiveEnabled = live,
            jiraInstances = mapOf(
                "samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123"),
            ),
            retentionMonths = 24,
            sync = sync,
        )
        val fabrica = JiraClientFactory(
            settings,
            {
                HttpClient(
                    MockEngine { req ->
                        val corpo = Json.parseToJsonElement(String(req.body.toByteArray())).jsonObject
                        corpos += corpo
                        val jql = corpo.getValue("jql").jsonPrimitive.content
                        val pedidas = Regex("\"(TG-\\d+)\"").findAll(jql).map { it.groupValues[1] }.toSet()
                        val completo = corpo["expand"] != null
                        // `updated >= "aaaa-MM-dd HH:mm"` (fuso de Manaus) restringe o universo, como o Jira faria.
                        val desde = Regex("updated >= \"(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)\"").find(jql)?.groupValues?.get(1)
                            ?.let { java.time.LocalDateTime.parse(it.replace(' ', 'T')).toInstant(ZoneOffset.ofHours(-4)) }
                        val itens = noJira.entries
                            .filter { desde == null || it.value.second >= desde }
                            .filter { if (jql.contains("key in")) it.key in pedidas else it.key !in ocultasNaLista }
                            .map { (k, v) -> if (completo) completa(k, v.first, v.second) else leve(k, v.first, v.second) }
                        respond(
                            """{"startAt":0,"maxResults":1000,"total":${itens.size},"issues":[${itens.joinToString(",")}]}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    },
                )
            },
            RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
        )
        val c = testContextComBanco(settings, pg, clock = relogio, jira = fabrica)
        ctx = c
        val ds = requireNotNull(c.dataSource)
        store = PostgresIssueStore(ds)
        estado = PostgresSyncStateStore(ds)
        return SyncService(c, store, estado, SyncGate(live, settings.sync), relogio)
    }

    /** Grava uma issue local (como o Syncer faria) com um evento próprio. */
    private fun semear(chave: String, id: Long, updated: Instant, removida: Boolean = false) {
        val n = chave.substringAfter('-')
        val issue = Json.parseToJsonElement(
            """{"id":"$id","key":"$chave","fields":{"summary":"s$n","project":{"key":"TG"},"issuetype":{"name":"Task"},
            "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"${texto(updated)}","labels":[]}}""",
        ).jsonObject
        val evento = ChangeEventRecord("e$n", chave, "status", "To Do", "In Progress", instante("12:00").minusSeconds(3600), "ana")
        store.upsert(issue, listOf(evento), "samsung")
        if (removida) {
            consulta("UPDATE issue SET removida_em = '2026-10-29T00:00:00Z' WHERE key = '$chave'")
        }
    }

    private fun consulta(sql: String) {
        requireNotNull(requireNotNull(ctx).dataSource).connection.use { it.createStatement().execute(sql) }
    }

    private fun <T> ler(sql: String, f: (java.sql.ResultSet) -> T): T =
        requireNotNull(requireNotNull(ctx).dataSource).connection.use { c ->
            c.createStatement().executeQuery(sql).use { rs -> rs.next(); f(rs) }
        }

    private fun removidaEm(chave: String): Instant? =
        ler("SELECT removida_em FROM issue WHERE key = '$chave'") { it.getTimestamp(1)?.toInstant() }

    private fun contar(sql: String): Int = ler(sql) { it.getInt(1) }

    private fun executada(r: ResultadoExecucao) = assertIs<ResultadoExecucao.Executada>(r)

    private fun semearBase(n: Int, updated: Instant = instante("10:00")) {
        for (i in 1..n) {
            semear("TG-$i", 900L + i, updated)
            noJira["TG-$i"] = (900L + i) to updated
        }
    }

    @Test
    fun issueApagadaNoJiraViraRemovida() = runBlocking {
        val s = montar()
        semearBase(4)
        noJira.remove("TG-4")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(1, r.resumo.removidas)
        assertEquals(relogio.instant(), removidaEm("TG-4"))
        assertNull(removidaEm("TG-1"))
        // Fora das linhas do tempo, mas o histórico fica até a retenção.
        assertEquals(setOf("TG-1", "TG-2", "TG-3"), store.timelines("samsung", null, null, false).map { it.key }.toSet())
        assertEquals(1, contar("SELECT count(*) FROM change_event e JOIN issue i ON i.id = e.issue_id WHERE i.key = 'TG-4'"))
        assertNotNull(estado.ultimaReconciliacao("samsung"))
    }

    @Test
    fun issueQueVoltaLimpaRemocao() = runBlocking {
        val s = montar()
        semearBase(3)
        semear("TG-5", 905, instante("10:00"), removida = true)
        noJira["TG-5"] = 905L to instante("10:00")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
        // Chave ausente da base ativa: rebuscada, e o upsert limpa removida_em.
        assertNull(removidaEm("TG-5"))
        assertEquals(1, r.resumo.issues)
    }

    @Test
    fun issueMovidaManteHistoricoPeloJiraId() = runBlocking {
        val s = montar()
        semearBase(4)
        noJira.remove("TG-4")
        noJira["TG-40"] = 904L to instante("10:00")
        val internoAntes = ler("SELECT id FROM issue WHERE key = 'TG-4'") { it.getLong(1) }
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
        assertEquals(0, contar("SELECT count(*) FROM issue WHERE key = 'TG-4'"))
        assertEquals(internoAntes, ler("SELECT id FROM issue WHERE key = 'TG-40'") { it.getLong(1) })
        assertEquals(1, contar("SELECT count(*) FROM change_event WHERE issue_id = $internoAntes"))
        assertNull(removidaEm("TG-40"))
        // Só a consulta leve: nada a rebuscar.
        assertEquals(1, corpos.size)
    }

    @Test
    fun renomearAtualizaAsPontasDoVinculo() = runBlocking {
        val s = montar()
        semearBase(4)
        consulta(
            "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em) VALUES " +
                "('samsung', 'TG-3', 'TG-4', 'Blocks', 'OUTWARD', now()), " +
                "('samsung', 'TG-4', 'TG-1', 'Relates', 'SIMETRICO', now()), " +
                "('sidia', 'TG-3', 'TG-4', 'Blocks', 'OUTWARD', now())",
        )
        noJira.remove("TG-4")
        noJira["TG-40"] = 904L to instante("10:00")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(1, contar("SELECT count(*) FROM issue_link WHERE instance = 'samsung' AND from_key = 'TG-3' AND to_key = 'TG-40'"))
        assertEquals(1, contar("SELECT count(*) FROM issue_link WHERE instance = 'samsung' AND from_key = 'TG-1' AND to_key = 'TG-40'"))
        assertEquals(0, contar("SELECT count(*) FROM issue_link WHERE instance = 'samsung' AND (from_key = 'TG-4' OR to_key = 'TG-4')"))
        // Outra instância não é tocada.
        assertEquals(1, contar("SELECT count(*) FROM issue_link WHERE instance = 'sidia' AND to_key = 'TG-4'"))
    }

    @Test
    fun renomearComVinculoJaExistenteNaChaveNovaNaoQuebra() = runBlocking {
        val s = montar()
        semearBase(4)
        // O vínculo já existe sob a chave nova (visto antes pela outra ponta) e também sob a antiga; e há um com a própria chave nova.
        consulta(
            "INSERT INTO issue_link(instance, from_key, to_key, link_type, direction, visto_em) VALUES " +
                "('samsung', 'TG-3', 'TG-4', 'Blocks', 'OUTWARD', now()), " +
                "('samsung', 'TG-3', 'TG-40', 'Blocks', 'OUTWARD', now()), " +
                "('samsung', 'TG-4', 'TG-40', 'Relates', 'SIMETRICO', now())",
        )
        noJira.remove("TG-4")
        noJira["TG-40"] = 904L to instante("10:00")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(1, contar("SELECT count(*) FROM issue_link WHERE from_key = 'TG-3' AND to_key = 'TG-40'"))
        assertEquals(0, contar("SELECT count(*) FROM issue_link WHERE from_key = to_key OR from_key = 'TG-4' OR to_key = 'TG-4'"))
    }

    @Test
    fun divergenteEhRebuscadaComChangelog() = runBlocking {
        val s = montar()
        semearBase(3)
        noJira["TG-2"] = 902L to instante("11:00")
        val eventosAntes = contar("SELECT count(*) FROM change_event")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(2, corpos.size)
        val rebusca = corpos[1]
        assertTrue(rebusca.getValue("jql").jsonPrimitive.content.contains("""key in ("TG-2")"""), rebusca.toString())
        assertEquals("changelog", rebusca.getValue("expand").jsonArray.single().jsonPrimitive.content)
        assertEquals(eventosAntes + 1, contar("SELECT count(*) FROM change_event"))
        assertEquals(instante("11:00"), ler("SELECT updated FROM issue WHERE key = 'TG-2'") { it.getTimestamp(1).toInstant() })
        assertEquals(1, r.resumo.issues)
        assertEquals(2, r.resumo.requisicoes)
        // A reconciliação não mexe no cursor do incremental.
        assertNull(estado.cursor("samsung"))
    }

    @Test
    fun rebuscaEmLotesDeCem() = runBlocking {
        val s = montar()
        semearBase(1)
        for (i in 2..151) noJira["TG-$i"] = (900L + i) to instante("10:00")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(150, r.resumo.issues)
        // 1 leve + 2 lotes (100 + 50)
        assertEquals(3, corpos.size)
        assertEquals(151, contar("SELECT count(*) FROM issue"))
    }

    private fun abortaCom(local: Int, noJiraN: Int) = runBlocking {
        val s = montar()
        semearBase(local)
        for (i in (noJiraN + 1)..local) noJira.remove("TG-$i")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("RECONCILIACAO_SUSPEITA", r.codigoErro)
        assertEquals(0, r.resumo.removidas)
        assertEquals(0, contar("SELECT count(*) FROM issue WHERE removida_em IS NOT NULL"))
        // Nada rebuscado: só a consulta leve.
        assertEquals(1, corpos.size)
        assertNull(estado.ultimaReconciliacao("samsung"))
    }

    @Test
    fun reconciliacaoAbortaQuandoJiraDevolvePoucoDemais() {
        abortaCom(local = 40, noJiraN = 19)
    }

    @Test
    fun abortaComBaseLocalPequena() {
        abortaCom(local = 3, noJiraN = 1)
    }

    @Test
    fun abortaQuandoJiraVemVazioComBaseLocal() {
        abortaCom(local = 3, noJiraN = 0)
    }

    @Test
    fun abortoNaoRenomeiaNemRebusca() = runBlocking {
        val s = montar()
        semearBase(4)
        noJira.remove("TG-3")
        noJira.remove("TG-4")
        noJira.remove("TG-2")
        noJira["TG-10"] = 901L to instante("11:00")
        noJira.remove("TG-1")
        val r = executada(s.reconciliar("samsung"))
        assertEquals("RECONCILIACAO_SUSPEITA", r.codigoErro)
        assertEquals(1, contar("SELECT count(*) FROM issue WHERE key = 'TG-1'"))
        assertEquals(0, contar("SELECT count(*) FROM issue WHERE key = 'TG-10'"))
        assertEquals(1, corpos.size)
    }

    @Test
    fun remocaoDeExatamenteMetadePassa() = runBlocking {
        val s = montar()
        semearBase(40)
        for (i in 21..40) noJira.remove("TG-$i")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(20, r.resumo.removidas)
        assertEquals(20, contar("SELECT count(*) FROM issue WHERE removida_em IS NOT NULL"))
    }

    @Test
    fun reconciliacaoNaoReinsereIssuePurgada() = runBlocking {
        val s = montar()
        semearBase(2)
        // Existe no Jira, resolvida e atualizada em 2024-01 (antes do corte): a consulta leve não a traz, e ela não volta.
        noJira["TG-77"] = 977L to Instant.parse("2024-01-10T10:00:00Z")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.issues)
        assertTrue(
            corpos[0].getValue("jql").jsonPrimitive.content.contains("""updated >= "2024-10-30 00:00""""),
            corpos[0].toString(),
        )
        assertEquals(0, contar("SELECT count(*) FROM issue WHERE key = 'TG-77'"))
        assertEquals(2, contar("SELECT count(*) FROM issue"))
    }

    @Test
    fun candidataAremocaoQueOJiraAindaTrazPorChaveNaoEhRemovida() = runBlocking {
        val s = montar()
        semearBase(4)
        // A lista leve pulou TG-4 (paginação), mas ele existe: a confirmação por chave o poupa.
        ocultasNaLista += "TG-4"
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
        assertNull(removidaEm("TG-4"))
        val confirmacao = corpos.map { it.getValue("jql").jsonPrimitive.content }.filter { it.contains("key in") && it.contains("\"TG-4\"") }
        assertEquals(1, confirmacao.size, corpos.toString())
    }

    @Test
    fun confirmacaoDeRemocaoUsaLotesDe100() = runBlocking {
        val s = montar(sync = SyncSettings(maxFracaoRemocao = 1.0))
        semearBase(230)
        for (i in 1..230) noJira.remove("TG-$i")
        noJira["TG-1000"] = 1900L to instante("10:00")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(230, r.resumo.removidas)
        // 1 lista leve + 3 confirmações (100 + 100 + 30) + 1 rebusca da TG-1000.
        val confirmacoes = corpos.map { it.getValue("jql").jsonPrimitive.content }
            .filter { it.contains("key in") && !it.contains("\"TG-1000\"") }
        assertEquals(3, confirmacoes.size, corpos.size.toString())
        assertEquals(listOf(30, 100, 100), confirmacoes.map { Regex("\"TG-\\d+\"").findAll(it).count() }.sorted())
    }

    @Test
    fun foraDoUniversoLocalNaoEhRemovida() = runBlocking {
        val s = montar()
        semearBase(2)
        // Aberta, mas com updated de 2023 (antes do corte): fora do universo, nunca marcada.
        semear("TG-50", 950, Instant.parse("2023-05-01T10:00:00Z"))
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
        assertNull(removidaEm("TG-50"))
    }

    @Test
    fun timelinesIgnoraRemovidas() {
        montar()
        semearBase(2)
        semear("TG-9", 909, instante("10:00"), removida = true)
        assertEquals(listOf("TG-1", "TG-2"), store.timelines("samsung", null, null, false).map { it.key })
        assertFalse(store.temOsVersion("samsung"))
        consulta("UPDATE issue SET os_version = 'V1' WHERE key = 'TG-9'")
        assertFalse(store.temOsVersion("samsung"))
        consulta("UPDATE issue SET os_version = 'V1' WHERE key = 'TG-1'")
        assertTrue(store.temOsVersion("samsung"))
    }

    @Test
    fun indiceSoTrazAtivasDentroDoCorte() {
        montar()
        semearBase(2)
        semear("TG-9", 909, instante("10:00"), removida = true)
        semear("TG-50", 950, Instant.parse("2023-05-01T10:00:00Z"))
        val indice = store.indice("samsung", Instant.parse("2024-10-30T04:00:00Z"))
        assertEquals(listOf("TG-1", "TG-2"), indice.map { it.key })
        assertEquals(901L, indice.first().jiraId)
    }

    @Test
    fun upsertDeIssueMovidaLiberaOJiraIdDaLinhaAntiga() {
        montar()
        semear("TG-4", 904, instante("10:00"))
        // Mesmo id do Jira, chave nova (issue movida de projeto): não pode violar uq_issue_instance_jira_id.
        semear("TG-40", 904, instante("11:00"))
        assertEquals(relogioDoBancoNaoNulo("TG-4"), true)
        assertNull(ler("SELECT jira_id FROM issue WHERE key = 'TG-4'") { it.getObject(1) })
        assertEquals(904L, ler("SELECT jira_id FROM issue WHERE key = 'TG-40'") { it.getLong(1) })
        assertNull(removidaEm("TG-40"))
        // O histórico da chave antiga continua nela.
        assertEquals(1, contar("SELECT count(*) FROM change_event e JOIN issue i ON i.id = e.issue_id WHERE i.key = 'TG-4'"))
    }

    private fun relogioDoBancoNaoNulo(chave: String) = removidaEm(chave) != null

    @Test
    fun violacaoDeUnicidadeViraCodigoRegistradoSemEco() = runBlocking {
        val s = montar()
        // Gatilho de teste: simula uma corrida que o banco rejeita com SQLState 23505.
        consulta(
            """CREATE FUNCTION falha_unico() RETURNS trigger AS ${'$'}${'$'}
            BEGIN RAISE EXCEPTION 'segredo-no-texto-da-excecao' USING ERRCODE = '23505'; END
            ${'$'}${'$'} LANGUAGE plpgsql""",
        )
        consulta("CREATE TRIGGER t_falha BEFORE INSERT ON issue FOR EACH ROW EXECUTE FUNCTION falha_unico()")
        noJira["TG-1"] = 901L to instante("10:00")
        val r = executada(s.incremental("samsung"))
        assertEquals(RunStatus.FALHA, r.status)
        assertEquals("CONFLITO_DE_UNICIDADE", r.codigoErro)
        val registrado = ler("SELECT codigo_erro FROM sync_run ORDER BY id DESC LIMIT 1") { it.getString(1) }
        assertEquals("CONFLITO_DE_UNICIDADE", registrado)
    }

    @Test
    fun reconciliacaoRecuperaIssuePuladaPelaPaginacao() = runBlocking {
        val s = montar()
        // Carga longa paginada por startAt: a página 1 trouxe A e B; durante a carga C foi editada, passou à frente
        // e a página seguinte saltou outra issue. Ao fim da carga, a base tem só A e B.
        noJira["TG-1"] = 901L to instante("09:00")
        noJira["TG-2"] = 902L to instante("09:10")
        executada(s.incremental("samsung"))
        assertEquals(2, contar("SELECT count(*) FROM issue"))
        noJira["TG-3"] = 903L to instante("09:20")
        noJira["TG-4"] = 904L to instante("09:30")
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
        assertEquals(2, r.resumo.issues)
        assertEquals(setOf("TG-1", "TG-2", "TG-3", "TG-4"), store.timelines("samsung", null, null, false).map { it.key }.toSet())
    }

    @Test
    fun jiraVazioSemBaseLocalNaoAborta() = runBlocking {
        val s = montar()
        val r = executada(s.reconciliar("samsung"))
        assertEquals(RunStatus.OK, r.status)
        assertEquals(0, r.resumo.removidas)
    }

    @Test
    fun reconciliacaoRegistraExecucaoDoTipoCerto() = runBlocking {
        val s = montar()
        semearBase(2)
        executada(s.reconciliar("samsung"))
        assertEquals(SyncKind.RECONCILIACAO, estado.ultimas("samsung").single().kind)
    }
}
