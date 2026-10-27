package br.sidia.painel.sync

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.AppContext
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.JiraInstanceConfig
import br.sidia.painel.jira.JiraLiveDisabledException
import br.sidia.painel.jira.LiveGuard
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.jql.FieldDef
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.jql.FieldType
import br.sidia.painel.jql.JqlQuery
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Sincronização contra PostgreSQL real (Testcontainers) e Jira simulado por MockEngine; nenhuma rede real. */
class SyncerTest {
    companion object {
        private val pg = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            pg.apagar()
        }

        private fun fixture(nome: String) =
            checkNotNull(SyncerTest::class.java.getResource("/fixtures/dc/$nome")) { nome }.readText()
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val consulta = JqlQuery("samsung", "project = TG", listOf("summary", "status"), true)
    private val eventosEsperados = 10 // TG-1: 3, TG-2: 3, TG-3: 1, TG-4 (completo): 3

    private lateinit var ctx: AppContext
    private lateinit var store: SqliteIssueStore

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(Settings(), pg)
        store = SqliteIssueStore(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    /** Jira simulado: busca devolve a fixture; changelog de TG-4 devolve o histórico completo. */
    private fun jira(
        busca: String = fixture("search-tg-basic-changelog.json"),
        changelogs: Map<String, String> = emptyMap(),
    ): MockEngine = MockEngine { req ->
        val caminho = req.url.encodedPath
        val extra = changelogs.entries.firstOrNull { caminho.endsWith("/rest/api/2/issue/${it.key}/changelog") }
        when {
            extra != null -> respond(extra.value, HttpStatusCode.OK, json)
            caminho.endsWith("/rest/api/2/search") -> respond(busca, HttpStatusCode.OK, json)
            caminho.endsWith("/rest/api/2/issue/TG-4/changelog") -> respond(fixture("changelog-tg-4-completo.json"), HttpStatusCode.OK, json)
            else -> respond("""{"errorMessages":["nao esperado"]}""", HttpStatusCode.NotFound, json)
        }
    }

    private fun cliente(engine: MockEngine, instancia: String = "samsung") = JiraClient(
        JiraInstanceConfig(instancia, "https://jira.exemplo.invalid/", JiraAuthKind.BEARER, "abc", null),
        HttpClient(engine), LiveGuard(true), RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
    )

    private fun contar(tabela: String): Int = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM $tabela").use { it.next(); it.getInt(1) }
    }

    @Test
    fun syncerEhIdempotente() = runBlocking {
        val syncer = Syncer(cliente(jira()), store, "samsung")
        val r1 = syncer.run(consulta)
        val issues = contar("issue")
        val eventos = contar("change_event")
        val r2 = syncer.run(consulta)
        assertEquals(SyncSummary(4, eventosEsperados), r1)
        assertEquals(r1, r2)
        assertEquals(4, issues)
        assertEquals(eventosEsperados, eventos)
        assertEquals(issues, contar("issue"))
        assertEquals(eventos, contar("change_event"))
    }

    @Test
    fun syncerGravaVinculosDaFixture() = runBlocking {
        val syncer = Syncer(cliente(jira(busca = fixture("search-issuelinks.json"))), store, "samsung")
        val r1 = syncer.run(consulta)
        assertEquals(8, r1.issues)
        // Blocks TG-2->TG-1 (visto pelas duas pontas), TG-3->TG-4 (idem), TG-5->TG-6 (idem) e Relates TG-1~TG-7; o item malformado cai.
        assertEquals(4, contar("issue_link"))
        val links = SqliteIssueLinkStore(requireNotNull(ctx.dataSource))
        assertEquals(
            listOf(
                VinculoRecord("TG-2", "TG-1", "Blocks", DirecaoVinculo.OUTWARD),
                VinculoRecord("TG-3", "TG-4", "Blocks", DirecaoVinculo.OUTWARD),
                VinculoRecord("TG-5", "TG-6", "Blocks", DirecaoVinculo.OUTWARD),
            ),
            links.ativos("samsung", "blocks").sortedBy { it.fromKey },
        )
        assertEquals(
            listOf(VinculoRecord("TG-1", "TG-7", "Relates", DirecaoVinculo.SIMETRICO)),
            links.ativos("samsung", "Relates"),
        )
        // Repetir a sincronização não muda nada, e o raw não guarda os vínculos nem dados da outra ponta.
        syncer.run(consulta)
        assertEquals(4, contar("issue_link"))
        val raws = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT raw FROM issue").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertEquals(8, raws.size)
        // O nome do campo entre aspas (o resumo de TG-8 cita "issuelinks" como texto, sem aspas próprias do campo).
        assertTrue(raws.none { "\"issuelinks\":" in it || "outra ponta" in it }, "raw guarda vínculo ou dado da outra ponta")
    }

    @Test
    fun changelogTruncadoBuscaOCompleto() = runBlocking {
        val engine = jira()
        Syncer(cliente(engine), store, "samsung").run(consulta)
        val chamadasChangelog = engine.requestHistory.filter { it.url.encodedPath.endsWith("/issue/TG-4/changelog") }
        assertEquals(1, chamadasChangelog.size)
        assertEquals(0, engine.requestHistory.count { it.url.encodedPath.endsWith("/issue/TG-1/changelog") })
        // o evento que só existe no histórico completo (id 10006) foi gravado
        val extra = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT count(*) FROM change_event WHERE changelog_id = 'TG-4:10006:0'")
                .use { it.next(); it.getInt(1) }
        }
        assertEquals(1, extra)
    }

    @Test
    fun liveDesligadoNaoFazRequisicao() = runBlocking {
        var motorAlcancado = 0
        val fabrica = JiraClientFactory(Settings(jiraLiveEnabled = false), { error("HttpClient real não deve ser criado") },
            RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }), { motorAlcancado++ })
        assertFailsWith<JiraLiveDisabledException> { Syncer(fabrica.create("samsung"), store, "samsung").run(consulta) }
        assertEquals(0, motorAlcancado)
        assertEquals(0, contar("issue"))
        fabrica.close()
    }

    @Test
    fun syncerUsaUsernameNormalizado() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        val tg1 = store.timelines("samsung", null, null, false).first { it.key == "TG-1" }
        assertEquals("ana.silva", tg1.currentAssignee)
        assertEquals("ana.silva", tg1.assigneeChanges.single().to)
        assertEquals("ana.silva", tg1.transitions.first().authorRef)
        val gravado = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT assignee_ref, reporter_ref FROM issue WHERE key = 'TG-1'")
                .use { it.next(); it.getString(1) to it.getString(2) }
        }
        assertEquals("ana.silva" to "carla.souza", gravado)
        val evento = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT to_value, author_ref FROM change_event WHERE changelog_id = 'TG-1:10001:1'")
                .use { it.next(); it.getString(1) to it.getString(2) }
        }
        assertEquals("ana.silva" to "ana.silva", evento)
    }

    @Test
    fun timelinesFiltraAbertasEFechadas() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        assertEquals(listOf("TG-3", "TG-4"), store.timelines("samsung", null, null, true).map { it.key })
        val fechadas = store.timelines("samsung", Instant.parse("2026-10-06T00:00:00Z"), Instant.parse("2026-10-07T00:00:00Z"), false)
        assertEquals(listOf("TG-2"), fechadas.map { it.key })
        assertEquals(emptyList(), store.timelines("sidia", null, null, false))
        val tg4 = store.timelines("samsung", null, null, true).first { it.key == "TG-4" }
        assertEquals("To Do", tg4.initialStatus)
        assertEquals("Migrar cadastro de equipes", tg4.summary)
        assertNotNull(tg4.transitions.firstOrNull())
    }

    // ---- Rodada 1 ----

    private fun obj(texto: String): JsonObject = Json.parseToJsonElement(texto).jsonObject

    private fun alterarIssue(texto: String, chave: String, f: (JsonObject) -> JsonObject): String {
        val raiz = obj(texto)
        val issues = raiz.getValue("issues").jsonArray.map {
            val o = it.jsonObject
            if (o.getValue("key").jsonPrimitive.content == chave) f(o) else o
        }
        return JsonObject(raiz + ("issues" to JsonArray(issues))).toString()
    }

    private fun JsonObject.comFields(vararg p: Pair<String, JsonElement>) =
        JsonObject(this + ("fields" to JsonObject(getValue("fields").jsonObject + p)))

    /** TG-3 passa a concluída, com outro responsável, outros rótulos e um evento novo. */
    private fun buscaComTg3Alterada(): String = alterarIssue(fixture("search-tg-basic-changelog.json"), "TG-3") {
        val novo = it.comFields(
            "status" to obj("""{"name":"Done"}"""),
            "resolution" to obj("""{"name":"Done"}"""),
            "resolutiondate" to JsonPrimitive("2026-10-10T09:00:00.000-0400"),
            "assignee" to obj("""{"name":"Bruno"}"""),
            "labels" to Json.parseToJsonElement("""["acesso","feito"]"""),
        )
        val historicos = novo.getValue("changelog").jsonObject.getValue("histories").jsonArray.toMutableList()
        historicos += obj(
            """{"id":"10009","created":"2026-10-10T09:00:00.000-0400","author":{"name":"bruno"},
                "items":[{"field":"status","from":"3","to":"10001","fromString":"In Progress","toString":"Done"}]}""",
        )
        JsonObject(novo + ("changelog" to JsonObject(mapOf("total" to JsonPrimitive(2), "histories" to JsonArray(historicos)))))
    }

    private fun texto(sql: String): List<String?> = requireNotNull(ctx.dataSource).connection.use { c ->
        c.createStatement().executeQuery(sql).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
    }

    @Test
    fun mesmaChaveEmInstanciasDiferentesNaoSeSobrescreve() = runBlocking {
        val consultaSidia = JqlQuery("sidia", "project = TG", listOf("summary"), true)
        Syncer(cliente(jira(), "samsung"), store, "samsung").run(consulta)
        Syncer(cliente(jira(busca = buscaComTg3Alterada()), "sidia"), store, "sidia").run(consultaSidia)
        assertEquals(8, contar("issue"))
        assertEquals(21, contar("change_event")) // 10 da samsung + 11 da sidia (TG-3 com um evento a mais)
        val sam = store.timelines("samsung", null, null, false)
        val sid = store.timelines("sidia", null, null, false)
        assertEquals(listOf("TG-1", "TG-2", "TG-3", "TG-4"), sam.map { it.key })
        assertEquals(listOf("TG-1", "TG-2", "TG-3", "TG-4"), sid.map { it.key })
        assertEquals("In Progress", sam.first { it.key == "TG-3" }.currentStatus)
        assertEquals("Done", sid.first { it.key == "TG-3" }.currentStatus)
        assertEquals(1, sam.first { it.key == "TG-3" }.transitions.size)
        assertEquals(2, sid.first { it.key == "TG-3" }.transitions.size)
    }

    @Test
    fun segundaExecucaoComIssueAlteradaAtualizaALinha() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        val r2 = Syncer(cliente(jira(busca = buscaComTg3Alterada())), store, "samsung").run(consulta)
        assertEquals(SyncSummary(4, eventosEsperados + 1), r2)
        assertEquals(4, contar("issue"))
        assertEquals(eventosEsperados + 1, contar("change_event"))
        val linha = requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery(
                "SELECT status, resolution, assignee_ref, CASE WHEN resolved IS NOT NULL THEN 'true' ELSE 'false' END, (SELECT group_concat(value, ',') FROM json_each(labels)) FROM issue WHERE key = 'TG-3'",
            ).use { it.next(); listOf(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5)) }
        }
        assertEquals(listOf("Done", "Done", "bruno", "true", "acesso,feito"), linha)
        assertEquals(
            listOf("TG-3:10005:0", "TG-3:10009:0"),
            texto("SELECT changelog_id FROM change_event WHERE changelog_id LIKE 'TG-3:%' ORDER BY at"),
        )
    }

    private fun dicionarioComOs(customId: String?) = FieldDictionary(
        listOf(FieldDef("OS Version", FieldType.SELECT, "samsung", "versão do SO", customId = customId)),
    )

    private fun osVersions() =
        texto("SELECT key || '=' || coalesce(os_version, '<nulo>') FROM issue ORDER BY key").map { requireNotNull(it) }

    @Test
    fun gravaOsVersionDoCampoCustomizado() = runBlocking {
        Syncer(cliente(jira()), store, "samsung", campos = dicionarioComOs("customfield_12416")).run(consulta)
        // Objeto SELECT, string com espaços normalizados, texto vazio = nulo, campo ausente = nulo.
        assertEquals(
            listOf("TG-1=Android 17.0 (OneUI_9.0)", "TG-2=android 16.0 (16)", "TG-3=<nulo>", "TG-4=<nulo>"),
            osVersions(),
        )
    }

    @Test
    fun aceitaCustomIdNoFormatoCf() = runBlocking {
        Syncer(cliente(jira()), store, "samsung", campos = dicionarioComOs("cf[12416]")).run(consulta)
        assertEquals("TG-1=Android 17.0 (OneUI_9.0)", osVersions().first())
    }

    @Test
    fun semCustomIdOsVersionFicaNuloSemFalhar() = runBlocking {
        Syncer(cliente(jira()), store, "samsung", campos = dicionarioComOs(null)).run(consulta)
        assertEquals(4, contar("issue"))
        assertTrue(osVersions().all { it.endsWith("=<nulo>") })
        // Sem dicionário nenhum, o mesmo.
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        assertTrue(osVersions().all { it.endsWith("=<nulo>") })
    }

    @Test
    fun resincronizarAtualizaOsVersion() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        Syncer(cliente(jira()), store, "samsung", campos = dicionarioComOs("customfield_12416")).run(consulta)
        assertEquals("TG-1=Android 17.0 (OneUI_9.0)", osVersions().first())
        assertEquals(4, contar("issue"))
    }

    @Test
    fun naoReinsereEventoMaisAntigoQueOCorteDeRetencao() = runBlocking {
        val relogio = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)
        val r = Syncer(cliente(jira()), store, "samsung", retentionMonths = 1, clock = relogio).run(consulta)
        assertEquals(eventosEsperados - 1, r.events) // o evento de 22/09 fica antes do corte de 25/09
        assertEquals(0, texto("SELECT changelog_id FROM change_event WHERE changelog_id LIKE 'TG-3:%'").size)
        assertEquals(4, contar("issue"))
    }

    @Test
    fun rawNaoGuardaDadosPessoais() = runBlocking {
        val usuario = """{"name":"Ana.Silva","key":"JIRAUSER1","displayName":"Ana Silva","emailAddress":"ana@exemplo.invalid",
            "avatarUrls":{"48x48":"https://avatar.exemplo.invalid/a.png"},"self":"https://jira.exemplo.invalid/user?u=1"}"""
        val busca = alterarIssue(fixture("search-tg-basic-changelog.json"), "TG-1") {
            it.comFields(
                "assignee" to obj(usuario), "reporter" to obj(usuario), "creator" to obj(usuario),
                "comment" to obj("""{"comments":[{"author":$usuario,"body":"ok"}]}"""),
            )
        }
        Syncer(cliente(jira(busca = busca)), store, "samsung").run(consulta)
        val raw = texto("SELECT raw FROM issue WHERE key = 'TG-1'").single().orEmpty()
        for (proibido in listOf("displayName", "emailAddress", "avatarUrls", "Ana Silva", "ana@exemplo", "avatar.exemplo", "JIRAUSER1", "\"self\"")) {
            assertFalse(proibido in raw, "raw ainda contém $proibido")
        }
        assertEquals("Ajustar tela de login", texto("SELECT raw->'fields'->>'summary' FROM issue WHERE key = 'TG-1'").single())
        assertEquals("ana.silva", texto("SELECT raw->'fields'->'assignee'->>'name' FROM issue WHERE key = 'TG-1'").single())
        assertEquals("TG-1", texto("SELECT raw->>'key' FROM issue WHERE key = 'TG-1'").single())
    }

    @Test
    fun fronteirasDeClosedFromEClosedTo() = runBlocking {
        Syncer(cliente(jira()), store, "samsung").run(consulta)
        val tg1 = Instant.parse("2026-10-05T12:00:00Z") // resolved exato de TG-1
        fun chaves(de: Instant?, ate: Instant?, aberta: Boolean = false) =
            store.timelines("samsung", de, ate, aberta).map { it.key }
        assertEquals(listOf("TG-1", "TG-2"), chaves(tg1, null)) // closedFrom é inclusivo
        assertEquals(listOf("TG-2"), chaves(tg1.plusMillis(1), null))
        assertEquals(emptyList(), chaves(null, tg1)) // closedTo é exclusivo
        assertEquals(listOf("TG-1"), chaves(null, tg1.plusMillis(1)))
        // openOnly ignora os limites
        assertEquals(listOf("TG-3", "TG-4"), chaves(tg1, tg1.plusMillis(1), aberta = true))
    }

    @Test
    fun reporterNormalizadoEChangelogAusenteBuscaOCompleto() = runBlocking {
        val original = fixture("search-tg-basic-changelog.json")
        val historicosTg1 = obj(original).getValue("issues").jsonArray.map { it.jsonObject }
            .first { it.getValue("key").jsonPrimitive.content == "TG-1" }
            .getValue("changelog").jsonObject.getValue("histories")
        val busca = alterarIssue(original, "TG-1") {
            JsonObject(it.comFields("reporter" to obj("""{"name":"  Carla.SOUZA "}""")).filterKeys { k -> k != "changelog" })
        }
        val completo = """{"startAt":0,"maxResults":100,"total":2,"values":$historicosTg1}"""
        val engine = jira(busca = busca, changelogs = mapOf("TG-1" to completo))
        Syncer(cliente(engine), store, "samsung").run(consulta)
        assertEquals(1, engine.requestHistory.count { it.url.encodedPath.endsWith("/issue/TG-1/changelog") })
        assertEquals("carla.souza", texto("SELECT reporter_ref FROM issue WHERE key = 'TG-1'").single())
        assertEquals(3, texto("SELECT changelog_id FROM change_event WHERE changelog_id LIKE 'TG-1:%'").size)
    }

    @Test
    fun dataMalformadaCitaAChaveSemVazarOPayload() {
        val busca = alterarIssue(fixture("search-tg-basic-changelog.json"), "TG-2") {
            it.comFields("created" to JsonPrimitive("ontem-segredo-xyz"))
        }
        val e = assertFailsWith<IllegalStateException> {
            runBlocking { Syncer(cliente(jira(busca = busca)), store, "samsung").run(consulta) }
        }
        assertTrue("TG-2" in e.message.orEmpty())
        assertFalse("segredo-xyz" in e.stackTraceToString())
    }

    // ---- Tarefa 4: fluxo, chaves internas e issues incompletas ----

    private fun repositorioDeChaves() = SqliteSyncStateStore(requireNotNull(ctx.dataSource))

    private fun buscaChaves() = fixture("search-jirauser-keys.json")

    private fun valor(sql: String): String? = texto(sql).singleOrNull()

    @Test
    fun assigneeComChaveInternaViraUsername() = runBlocking {
        Syncer(cliente(jira(busca = buscaChaves())), store, "samsung", chaves = repositorioDeChaves()).run(consulta)
        assertEquals("ana.silva", valor("SELECT to_value FROM change_event WHERE changelog_id = 'TG-10:20001:0'"))
        val tg10 = store.timelines("samsung", null, null, false).first { it.key == "TG-10" }
        assertEquals("ana.silva", tg10.assigneeChanges.single().to)
        assertEquals("ana.silva", tg10.currentAssignee)
    }

    @Test
    fun chaveInternaSemMapaFicaMarcadaEContada() = runBlocking {
        val r = Syncer(cliente(jira(busca = buscaChaves())), store, "samsung", chaves = repositorioDeChaves()).run(consulta)
        assertEquals("JIRAUSER99", valor("SELECT from_value FROM change_event WHERE changelog_id = 'TG-11:20002:0'"))
        assertEquals("bruno", valor("SELECT to_value FROM change_event WHERE changelog_id = 'TG-11:20002:0'"))
        assertEquals(1, r.usuariosNaoResolvidos)
    }

    @Test
    fun mapaVemDoJsonAntesDaSanitizacao() = runBlocking {
        Syncer(cliente(jira(busca = buscaChaves())), store, "samsung", chaves = repositorioDeChaves()).run(consulta)
        // o raw não guarda a key do usuário, mas o mapa persistido a conhece
        assertEquals("false", valor("SELECT CASE WHEN json_extract(raw, '$.fields.assignee.key') IS NOT NULL THEN 'true' ELSE 'false' END FROM issue WHERE key = 'TG-10'"))
        assertFalse("JIRAUSER10500" in valor("SELECT raw FROM issue WHERE key = 'TG-10'").orEmpty())
        assertEquals("ana.silva", repositorioDeChaves().chavesDeUsuario("samsung")["JIRAUSER10500"])
        assertEquals(emptyMap(), repositorioDeChaves().chavesDeUsuario("sidia"))
    }

    @Test
    fun mapaPersistidoResolveExecucaoSeguinte() = runBlocking {
        Syncer(cliente(jira(busca = buscaChaves())), store, "samsung", chaves = repositorioDeChaves()).run(consulta)
        // 2ª execução: sem o objeto com `key` e com um histórico novo que cita só a chave interna
        val semObjeto = buscaChaves().replace(""", "key": "JIRAUSER10500"""", "").replace("\"20001\"", "\"20009\"")
        assertFalse("JIRAUSER10500\"}" in semObjeto.substringBefore("\"changelog\""), "o objeto ainda tem a key")
        val r = Syncer(cliente(jira(busca = semObjeto)), store, "samsung", chaves = repositorioDeChaves()).run(consulta)
        assertEquals("ana.silva", valor("SELECT to_value FROM change_event WHERE changelog_id = 'TG-10:20009:0'"))
        assertEquals(1, r.usuariosNaoResolvidos) // só o JIRAUSER99
    }

    @Test
    fun semRepositorioDeChavesUsaSoOMapaDaIssue() = runBlocking {
        val r = Syncer(cliente(jira(busca = buscaChaves())), store, "samsung").run(consulta)
        assertEquals("ana.silva", valor("SELECT to_value FROM change_event WHERE changelog_id = 'TG-10:20001:0'"))
        assertEquals(1, r.usuariosNaoResolvidos)
    }

    @Test
    fun processaEmFluxo() = runBlocking {
        val ordem = mutableListOf<String>()
        fun issueMinima(n: Int) = """{"id":"${900 + n}","key":"TG-$n","fields":{"summary":"s$n","project":{"key":"TG"},
            "issuetype":{"name":"Task"},"status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400",
            "updated":"2026-10-0${n - 20}T09:00:00.000-0400","labels":[]},"changelog":{"total":0,"histories":[]}}"""
        val todas = (21..26).map(::issueMinima)
        val engine = MockEngine { req ->
            val inicio = obj(String(req.body.toByteArray())).getValue("startAt").jsonPrimitive.content.toInt()
            ordem += "req:$inicio"
            val pagina = todas.drop(inicio).take(2)
            respond("""{"startAt":$inicio,"maxResults":2,"total":6,"issues":[${pagina.joinToString(",")}]}""", HttpStatusCode.OK, json)
        }
        val gravadas = mutableListOf<IssueGravada>()
        val r = Syncer(cliente(engine), store, "samsung").run(consulta) { g ->
            ordem += "gravou:${g.key}"
            gravadas += g
        }
        assertEquals(
            listOf("req:0", "gravou:TG-21", "gravou:TG-22", "req:2", "gravou:TG-23", "gravou:TG-24", "req:4", "gravou:TG-25", "gravou:TG-26"),
            ordem,
        )
        assertEquals(6, r.issues)
        assertEquals((21..26).map { "TG-$it" }, gravadas.map { it.key })
        assertEquals(Instant.parse("2026-10-01T13:00:00Z"), gravadas.first().updated)
        // quando o callback roda, a issue já está commitada
        assertEquals(6, contar("issue"))
    }

    @Test
    fun aoGravarSoRodaDepoisDoCommit() = runBlocking {
        var vistaNoCallback = -1
        Syncer(cliente(jira(busca = buscaChaves())), store, "samsung").run(consulta) { g ->
            if (g.key == "TG-10") vistaNoCallback = contar("issue")
        }
        assertEquals(1, vistaNoCallback)
    }

    @Test
    fun issueSemCreatedContaComoIncompleta() = runBlocking {
        val r = Syncer(cliente(jira(busca = buscaChaves())), store, "samsung").run(consulta)
        assertEquals(1, r.incompletas)
        assertEquals(3, r.issues)
        assertEquals(3, contar("issue"))
        // continua fora das linhas do tempo, como antes
        assertEquals(listOf("TG-10", "TG-11"), store.timelines("samsung", null, null, false).map { it.key })
    }

    @Test
    fun issueSemSummaryContaComoIncompleta() = runBlocking {
        val busca = alterarIssue(fixture("search-tg-basic-changelog.json"), "TG-3") {
            JsonObject(it + ("fields" to JsonObject(it.getValue("fields").jsonObject.filterKeys { k -> k != "summary" })))
        }
        val r = Syncer(cliente(jira(busca = busca)), store, "samsung").run(consulta)
        assertEquals(1, r.incompletas)
        assertEquals(4, contar("issue"))
    }

    @Test
    fun jiraIdGravadoEReentradaLimpaRemovida() = runBlocking {
        val syncer = Syncer(cliente(jira(busca = buscaChaves())), store, "samsung")
        syncer.run(consulta)
        assertEquals("10010", valor("SELECT CAST(jira_id AS TEXT) FROM issue WHERE key = 'TG-10'"))
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeUpdate("UPDATE issue SET removida_em = (CAST(strftime('%s','now') AS INTEGER) * 1000) WHERE key = 'TG-10'")
        }
        assertNotNull(valor("SELECT CAST(removida_em AS TEXT) FROM issue WHERE key = 'TG-10'"))
        syncer.run(consulta)
        assertEquals(null, valor("SELECT CAST(removida_em AS TEXT) FROM issue WHERE key = 'TG-10'"))
        assertEquals("10010", valor("SELECT CAST(jira_id AS TEXT) FROM issue WHERE key = 'TG-10'"))
    }

    @Test
    fun issueSemIdNaoApagaOJiraIdJaGravado() = runBlocking {
        Syncer(cliente(jira(busca = buscaChaves())), store, "samsung").run(consulta)
        val semId = buscaChaves().replace("\"id\": \"10010\",", "")
        Syncer(cliente(jira(busca = semId)), store, "samsung").run(consulta)
        assertEquals("10010", valor("SELECT CAST(jira_id AS TEXT) FROM issue WHERE key = 'TG-10'"))
    }
}
