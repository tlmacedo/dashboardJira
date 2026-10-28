package br.sidia.painel.auditoria

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.contrato.DrillGenericoDto
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.IssueLinks
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rotas da auditoria contra SQLite real em arquivo temporário, diretório de papéis em memória e relógio fixo
 * (30/10/2026 11:00 em Manaus). Nenhuma rede, nenhum Jira; tudo fictício.
 */
class AuditoriaRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagarBanco() {
            banco.apagar()
        }

        private const val BASE = "https://jira.exemplo.invalid"
    }

    private val zona = ZoneId.of("America/Manaus")
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)
    private val producao = Settings(dashboardDevMode = false, lgpd = br.sidia.painel.lgpd.LgpdSettings(true, "TESTE-AUDITORIA"))
    private val json = Json { ignoreUnknownKeys = true }

    private fun pessoa(id: String, papel: Role, squad: String? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, null), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("pl.alfa", Role.USUARIO, "Alfa"),
            pessoa("ana", Role.USUARIO, "Alfa"),
            pessoa("bruno", Role.USUARIO, "Alfa"),
            pessoa("carla", Role.USUARIO, "Beta"),
            // Mudou de squad: Alfa até 09/10 e Beta desde 10/10.
            Pessoa(
                "mudou", "mudou",
                listOf(
                    AtribuicaoPapel(Role.USUARIO, Vigencia(null, LocalDate.parse("2026-10-09")), "Alfa"),
                    AtribuicaoPapel(Role.USUARIO, Vigencia(LocalDate.parse("2026-10-10"), null), "Beta"),
                ),
            ),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
        ),
    )

    private lateinit var ctx: AppContext
    private lateinit var gravador: SqliteIssueStore
    private var seq = 0

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(producao, banco, diretorio, relogio)
        gravador = SqliteIssueStore(requireNotNull(ctx.dataSource))
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    // ---- Fixtures ----

    private fun i(s: String) = Instant.parse(s)

    private fun ev(chave: String, campo: String, de: String?, para: String?, at: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("$chave:${++seq}:0", chave, campo, de, para, i(at), autor)

    private fun blocks(ponta: String, chave: String) =
        """{"type":{"name":"Blocks","inward":"is blocked by","outward":"blocks"},"$ponta":{"key":"$chave"}}"""

    @Suppress("LongParameterList")
    private fun issue(
        chave: String, tipo: String = "Task", status: String = "In Progress", assignee: String? = "ana", duedate: String? = "2026-10-15",
        labels: List<String> = emptyList(), done: Boolean = false, links: List<String> = emptyList(),
    ): JsonObject {
        val n = chave.substringAfter('-')
        val ass = if (assignee == null) "" else """"assignee":{"name":"$assignee"},"""
        val due = if (duedate == null) "" else """"duedate":"$duedate","""
        val res = if (done) """"resolution":{"name":"Done"},"resolutiondate":"2026-10-14T09:00:00.000-0400",""" else ""
        val rotulos = labels.joinToString(",") { "\"$it\"" }
        return Json.parseToJsonElement(
            """{"id":"3$n","key":"$chave","fields":{"summary":"Resumo $chave","project":{"key":"TG"},"issuetype":{"name":"$tipo"},
            "status":{"name":"${if (done) "Done" else status}"},$ass"reporter":{"name":"pl.alfa"},$due$res
            "created":"2026-10-01T09:00:00.000-0400","updated":"2026-10-02T09:00:00.000-0400","labels":[$rotulos],
            "issuelinks":[${links.joinToString(",")}]}}""",
        ).jsonObject
    }

    private fun gravar(j: JsonObject, eventos: List<ChangeEventRecord> = emptyList(), contexto: AppContext = ctx) {
        val chave = (j["key"] as JsonPrimitive).content
        SqliteIssueStore(requireNotNull(contexto.dataSource)).upsert(j, eventos, "samsung", null, IssueLinks.ler(chave, j), i("2026-10-30T12:00:00Z"))
    }

    /** Sete sinais em seis issues (um deles alerta), quatro pares Task × Review (um divergente) e duas concluídas. */
    private fun semearBase(contexto: AppContext = ctx) {
        gravar(
            issue("TG-1", duedate = "2026-10-20"),
            listOf(
                ev("TG-1", "duedate", "2026-10-15", "2026-10-20", "2026-10-10T12:00:00Z", "ana"),
                ev("TG-1", "description", null, null, "2026-10-11T12:00:00Z", "ana"),
            ),
            contexto,
        )
        gravar(
            issue("TG-2", assignee = "ana"),
            listOf(
                ev("TG-2", "assignee", "ana", "bruno", "2026-10-13T15:00:00Z"),
                ev("TG-2", "assignee", "bruno", "ana", "2026-10-14T15:00:00Z"),
            ),
            contexto,
        )
        gravar(issue("TG-3", assignee = "bruno"), listOf(ev("TG-3", "assignee", "ana", "bruno", "2026-10-03T12:00:00Z", "carla")), contexto)
        gravar(issue("TG-4", assignee = "bruno"), listOf(ev("TG-4", "assignee", "carla", "bruno", "2026-10-04T12:00:00Z", "bruno")), contexto)
        gravar(issue("TG-5", assignee = "bruno"), listOf(ev("TG-5", "assignee", "mudou", "bruno", "2026-10-05T12:00:00Z", "carla")), contexto)
        // Quatro pares: dois com a Review bloqueando a task e dois com a task bloqueando a Review; o último diverge.
        gravar(issue("TG-10"), emptyList(), contexto)
        gravar(issue("TG-110", "Checklist Review", links = listOf(blocks("outwardIssue", "TG-10"))), emptyList(), contexto)
        gravar(issue("TG-11", links = listOf(blocks("outwardIssue", "TG-111")), duedate = "2026-10-16"), emptyList(), contexto)
        gravar(issue("TG-111", "Checklist Review", duedate = "2026-10-16", links = listOf(blocks("inwardIssue", "TG-11"))), emptyList(), contexto)
        gravar(issue("TG-12", duedate = "2026-10-17"), emptyList(), contexto)
        gravar(issue("TG-112", "Checklist Review", duedate = "2026-10-17", links = listOf(blocks("outwardIssue", "TG-12"))), emptyList(), contexto)
        gravar(issue("TG-13", links = listOf(blocks("outwardIssue", "TG-113"))), emptyList(), contexto)
        gravar(
            issue("TG-113", "Checklist Review", duedate = "2026-10-20", links = listOf(blocks("inwardIssue", "TG-13"))),
            listOf(ev("TG-113", "duedate", "2026-10-15", "2026-10-20", "2026-10-08T12:00:00Z", "carla")),
            contexto,
        )
        // No prazo: uma resolvida em 14/10 (vence 15/10) e uma resolvida em 14/10 com vencimento 13/10 + label que justifica.
        gravar(issue("TG-21", done = true), emptyList(), contexto)
        gravar(issue("TG-22", done = true, duedate = "2026-10-13", labels = listOf("LATE_SENT")), emptyList(), contexto)
    }

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private fun rodar(bloco: suspend (suspend (String?, String) -> Resp) -> Unit) = testApplication {
        application { module(producao, ctx, autenticador) }
        val http: HttpClient = client
        bloco { usuario, caminho ->
            val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
            Resp(r.status, r.bodyAsText())
        }
    }

    private fun linhasDoAccessLog(): List<Triple<String, String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    private val sinais = "/api/auditoria/sinais"
    private val doPeriodo = "de=2026-10-01&ate=2026-10-30"

    private suspend fun auditoria(get: suspend (String?, String) -> Resp, query: String = "", usuario: String = "gerente.exemplo"): AuditoriaDto {
        val r = get(usuario, if (query.isEmpty()) sinais else "$sinais?$query")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        return json.decodeFromString(r.corpo)
    }

    // ---- Quem vê ----

    @Test
    fun soCoordenacaoEGerente() = rodar { get ->
        semearBase()
        val rotas = listOf(
            sinais, "/api/auditoria/historico/TG-1", "/api/cards/task-review",
            "/api/cartoes/AU-01/tasks?segmento=todos", "/api/cartoes/DV-01/tasks?segmento=pares",
        )
        for (u in listOf("ana", "lider.alfa", "pl.alfa", "carla")) {
            for (rota in rotas) assertEquals(HttpStatusCode.Forbidden, get(u, rota).status, "$u $rota")
        }
        for (u in listOf("coord.um", "gerente.exemplo")) {
            for (rota in rotas) assertEquals(HttpStatusCode.OK, get(u, rota).status, "$u $rota")
        }
        // Quem não tem papel vigente (id desconhecido) também não vê.
        assertEquals(HttpStatusCode.Forbidden, get("desconhecido", sinais).status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, sinais).status)
        // A negação não vaza nada: corpo vazio.
        assertEquals("", get("ana", sinais).corpo)
    }

    @Test
    fun devModeSemLogin() {
        val dev = Settings(dashboardDevMode = true)
        val ctxDev = testContextComBanco(dev, banco, diretorio, relogio)
        ctxDev.use {
            semearBase(ctxDev)
            // Sem login: o principal `dev` (GERENTE) vê as rotas, pela mesma checagem de papel.
            testApplication {
                application { module(dev, ctxDev) }
                for (rota in listOf(sinais, "/api/auditoria/historico/TG-1", "/api/cards/task-review", "/api/cartoes/AU-01/tasks?segmento=todos")) {
                    assertEquals(HttpStatusCode.OK, client.get(rota).status, rota)
                }
            }
            // Com o modo dev ligado, um USUARIO ou SQUAD_LEADER injetado continua sem ver.
            testApplication {
                application { module(dev, ctxDev, autenticador) }
                for (u in listOf("ana", "lider.alfa")) {
                    assertEquals(HttpStatusCode.Forbidden, client.get(sinais) { header("X-Teste-Usuario", u) }.status, u)
                }
            }
        }
    }

    // ---- Corpo das respostas ----

    @Test
    fun avisoFixoIndicioNaoProva() = rodar { get ->
        semearBase()
        assertEquals("INDICIO_NAO_PROVA", auditoria(get, doPeriodo).aviso)
        val h = get("coord.um", "/api/auditoria/historico/TG-1")
        assertEquals("INDICIO_NAO_PROVA", json.decodeFromString<HistoricoDto>(h.corpo).aviso)
        // Também com a base vazia.
        assertEquals("INDICIO_NAO_PROVA", auditoria(get).aviso)
    }

    @Test
    fun ordenadoPorDataSemRanking() = rodar { get ->
        semearBase()
        val r = auditoria(get, doPeriodo)
        assertEquals(7, r.total)
        assertEquals(r.sinais.sortedByDescending { it.at }.map { it.at }, r.sinais.map { it.at })
        assertEquals(r.total, r.sinais.size)
        // O DTO não tem nenhuma lista nem contagem por pessoa: só estas chaves na raiz.
        val raiz = json.parseToJsonElement(get("gerente.exemplo", "$sinais?$doPeriodo").corpo).jsonObject.keys
        assertEquals(setOf("aviso", "versaoRegras", "total", "porSinal", "sinais", "noPrazo", "avisos"), raiz)
        // `porSinal` na ordem do enum, com todos os códigos.
        assertEquals(CodigoSinal.entries.map { it.name }, r.porSinal.map { it.codigo })
        assertEquals("regras v1, labels v1", r.versaoRegras)
    }

    @Test
    fun contagensEResultados() = rodar { get ->
        semearBase()
        val r = auditoria(get, doPeriodo)
        val n = r.porSinal.associate { it.codigo to it.n }
        assertEquals(2, n["VENCIMENTO_ALTERADO_NAO_AUTORIZADO"])
        assertEquals(1, n["PASSA_BOLA"])
        assertEquals(3, n["RESPONSAVEL_TROCADO_NAO_AUTORIZADO"])
        assertEquals(1, n["VENCIMENTO_TASK_REVIEW_DIVERGENTE"])
        assertEquals(6, r.sinais.count { it.resultado == "auditoria" })
        assertEquals(1, r.sinais.count { it.resultado == "alerta" })
        val alerta = r.porSinal.single { it.codigo == "VENCIMENTO_TASK_REVIEW_DIVERGENTE" }
        assertEquals("alerta", alerta.resultado)
        assertEquals(1, alerta.gravidade)
        // Dia civil de Manaus de cada sinal.
        val passaBola = r.sinais.single { it.codigo == "PASSA_BOLA" }
        assertEquals("2026-10-14", passaBola.dia)
        assertEquals("TG-2", passaBola.key)
        assertEquals("Resumo TG-2", passaBola.resumo)
        // No prazo e ajustado lado a lado.
        assertEquals(NoPrazoDto(2, 1, 2, 0, 0.5, 1.0), r.noPrazo)
    }

    @Test
    fun semDadosRespondeVazio() = rodar { get ->
        val r = auditoria(get)
        assertEquals(0, r.total)
        assertTrue(r.sinais.isEmpty())
        assertEquals(CodigoSinal.entries.size, r.porSinal.size)
        assertTrue(r.porSinal.all { it.n == 0 })
        assertNull(r.noPrazo.taxa)
        assertNull(r.noPrazo.taxaAjustada)
        // Sem vínculos na base: aviso informativo, nunca erro.
        assertTrue(r.avisos.any { it.codigo == AvisoDto.VINCULOS_INDISPONIVEIS && it.gravidade == AvisoDto.INFORMATIVO })
        val card = json.decodeFromString<TaskReviewDto>(get("coord.um", "/api/cards/task-review").corpo)
        assertEquals(TaskReviewDto(0, 0, null, card.avisos), card)
        assertTrue(card.avisos.any { it.codigo == AvisoDto.VINCULOS_INDISPONIVEIS })
    }

    // ---- Período e filtros ----

    @Test
    fun periodoPadraoTrintaDiasEMaximo180() = rodar { get ->
        semearBase()
        gravar(issue("TG-50", assignee = "bruno"), listOf(ev("TG-50", "assignee", "ana", "bruno", "2026-09-15T12:00:00Z", "carla")))
        // Padrão: 30 dias até hoje (01/10 a 30/10): o evento de setembro fica de fora.
        val padrao = auditoria(get)
        assertEquals(7, padrao.total)
        assertTrue(padrao.sinais.none { it.key == "TG-50" })
        assertTrue(auditoria(get, "de=2026-09-01&ate=2026-10-30").sinais.any { it.key == "TG-50" })
        // `ate` é inclusivo: o dia inteiro de 03/10 (Manaus) entra.
        assertEquals(listOf("TG-3"), auditoria(get, "de=2026-10-03&ate=2026-10-03").sinais.map { it.key })
        // 180 dias passa; 181, não.
        assertEquals(HttpStatusCode.OK, get("coord.um", "$sinais?de=2026-01-01&ate=2026-06-29").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?de=2026-01-01&ate=2026-06-30").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?de=2026-10-30&ate=2026-10-01").status)
        for (ruim in listOf("de=2026-13-01", "ate=ontem", "de=2026-10-01&ate=%2B999999999-12-31", "de=2026-10-01&ate=9999-12-31")) {
            assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?$ruim").status, ruim)
        }
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?squad=NaoExiste").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?sinal=INVENTADO").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?resultado=outro").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?pessoa=Ana%20Silva%21").status)
    }

    @Test
    fun filtroPessoaPorResponsavelOuAutor() = rodar { get ->
        semearBase()
        // carla é autora de três sinais e a responsável de referência de um.
        assertEquals(setOf("TG-3", "TG-4", "TG-5", "TG-113"), auditoria(get, "$doPeriodo&pessoa=carla").sinais.map { it.key }.toSet())
        // bruno: responsável de referência no passa-bola e autor da troca de TG-4.
        assertEquals(setOf("TG-2", "TG-4"), auditoria(get, "$doPeriodo&pessoa=bruno").sinais.map { it.key }.toSet())
        // O filtro vale também para as contagens e o total.
        val r = auditoria(get, "$doPeriodo&pessoa=bruno")
        assertEquals(r.sinais.size, r.total)
        assertEquals(r.total, r.porSinal.sumOf { it.n })
    }

    @Test
    fun squadPelaDataDoEvento() = rodar { get ->
        semearBase()
        // `mudou` era do Alfa em 05/10 (evento de TG-5) e hoje é do Beta: o sinal fica no squad da data do evento.
        val alfa = auditoria(get, "$doPeriodo&squad=Alfa").sinais.map { it.key }
        val beta = auditoria(get, "$doPeriodo&squad=Beta").sinais.map { it.key }
        assertTrue("TG-5" in alfa)
        assertFalse("TG-5" in beta)
        // TG-4 troca a task de carla (Beta).
        assertTrue("TG-4" in beta)
        assertFalse("TG-4" in alfa)
    }

    @Test
    fun filtroPorSinalEResultado() = rodar { get ->
        semearBase()
        assertEquals(listOf("TG-2"), auditoria(get, "$doPeriodo&sinal=PASSA_BOLA").sinais.map { it.key })
        assertEquals(listOf("TG-113"), auditoria(get, "$doPeriodo&resultado=alerta").sinais.map { it.key })
        assertEquals(6, auditoria(get, "$doPeriodo&resultado=auditoria").total)
    }

    // ---- Drill (o mesmo tamanho do número) ----

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar { get ->
        semearBase()
        suspend fun drill(cartao: String, segmento: String, query: String = doPeriodo): DrillGenericoDto {
            val r = get("coord.um", "/api/cartoes/$cartao/tasks?segmento=$segmento&$query")
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            val dto = json.decodeFromString<DrillGenericoDto>(r.corpo)
            assertEquals(dto.total, dto.linhas.size)
            return dto
        }
        val a = auditoria(get, doPeriodo)
        assertEquals(a.total, drill("AU-01", "todos").total)
        assertEquals(a.sinais.count { it.resultado == "alerta" }, drill("AU-01", "resultado:alerta").total)
        assertEquals(a.sinais.count { it.resultado == "auditoria" }, drill("AU-01", "resultado:auditoria").total)
        for (p in a.porSinal) assertEquals(p.n, drill("AU-02", "sinal:${p.codigo}").total, p.codigo)
        assertEquals(1, drill("AU-02", "sinal:PASSA_BOLA").total)
        // Com filtro, o card e o drill continuam iguais.
        val filtrado = auditoria(get, "$doPeriodo&pessoa=carla")
        assertEquals(filtrado.total, drill("AU-01", "todos", "$doPeriodo&pessoa=carla").total)
        // No prazo.
        assertEquals(a.noPrazo.concluidas, drill("AU-03", "concluidas").total)
        assertEquals(a.noPrazo.noPrazo, drill("AU-03", "noPrazo").total)
        assertEquals(a.noPrazo.noPrazoAjustado, drill("AU-03", "noPrazoAjustado").total)
        // Task × Review.
        val card = json.decodeFromString<TaskReviewDto>(get("coord.um", "/api/cards/task-review").corpo)
        assertEquals(4, drill("DV-01", "pares", "").total)
        assertEquals(card.divergentes, drill("DV-01", "divergentes", "").total)
        assertEquals(card.pares, drill("DV-01", "pares", "").total)
        // Segmento de outro cartão ou inventado: 400 com código.
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "/api/cartoes/AU-01/tasks?segmento=sinal:PASSA_BOLA").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "/api/cartoes/AU-02/tasks?segmento=sinal:INVENTADO").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "/api/cartoes/DV-01/tasks?segmento=todos").status)
    }

    @Test
    fun drillEmOrdemNaturalDeChaves() = rodar { get ->
        semearBase()
        val dto = json.decodeFromString<DrillGenericoDto>(get("coord.um", "/api/cartoes/AU-01/tasks?segmento=todos&$doPeriodo").corpo)
        val chaves = dto.linhas.map { it.key }
        assertEquals(chaves.sortedWith(br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES), chaves)
        assertTrue(chaves.size > 3)
    }

    // ---- Card Task × Review ----

    @Test
    fun cardTaskReviewQuatroParesVinteECincoPorcento() = rodar { get ->
        semearBase()
        val r = get("coord.um", "/api/cards/task-review")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val card = json.decodeFromString<TaskReviewDto>(r.corpo)
        assertEquals(4, card.pares)
        assertEquals(1, card.divergentes)
        assertEquals(25.0, card.percentual)
        // A lista traz só o par divergente, com os dois vencimentos.
        val linha = card.divergencias.single()
        assertEquals("TG-13", linha.task)
        assertEquals("TG-113", linha.review)
        assertEquals("2026-10-15", linha.vencimentoTask)
        assertEquals("2026-10-20", linha.vencimentoReview)
        assertTrue(linha.divergente)
        // Sem a flag do Jira, não há link.
        assertNull(linha.urlTask)
        assertNull(linha.urlReview)
        // Filtros: a task de ana (Alfa) entra; o squad Beta fica sem pares.
        assertEquals(4, json.decodeFromString<TaskReviewDto>(get("coord.um", "/api/cards/task-review?squad=Alfa").corpo).pares)
        assertEquals(0, json.decodeFromString<TaskReviewDto>(get("coord.um", "/api/cards/task-review?squad=Beta").corpo).pares)
        assertEquals(4, json.decodeFromString<TaskReviewDto>(get("coord.um", "/api/cards/task-review?pessoa=ana").corpo).pares)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "/api/cards/task-review?squad=NaoExiste").status)
    }

    @Test
    fun divergenciaTrazUrlDaInstancia() {
        val contador = AtomicInteger(0)
        val provedor = AtomicInteger(0)
        fun contexto(base: String?, ligada: Boolean = true): AppContext {
            val s = Settings(
                dashboardDevMode = false, jiraLiveEnabled = ligada, lgpd = br.sidia.painel.lgpd.LgpdSettings(true, "TESTE-AUDITORIA"),
                jiraInstances = if (base == null) emptyMap() else mapOf("samsung" to JiraInstanceSettings(base, JiraAuthKind.BEARER, null, "token-ficticio-abc123")),
            )
            val fabrica = JiraClientFactory(s, { provedor.incrementAndGet(); HttpClient(MockEngine { contador.incrementAndGet(); respond("{}", HttpStatusCode.OK) }) })
            return testContextComBanco(s, banco, diretorio, relogio, fabrica).also { semearBase(it) }
        }

        fun linha(c: AppContext): TaskReviewLinhaDto {
            var saida: TaskReviewLinhaDto? = null
            testApplication {
                application { module(c.settings, c, autenticador) }
                val r = client.get("/api/cards/task-review") { header("X-Teste-Usuario", "coord.um") }
                assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
                saida = json.decodeFromString<TaskReviewDto>(r.bodyAsText()).divergencias.single()
            }
            return requireNotNull(saida)
        }

        contexto(BASE).use {
            val l = linha(it)
            assertEquals("$BASE/browse/TG-13", l.urlTask)
            assertEquals("$BASE/browse/TG-113", l.urlReview)
        }
        // Barra final não vira `//browse`; o caminho de contexto é mantido.
        contexto("$BASE/jira/").use { assertEquals("$BASE/jira/browse/TG-13", linha(it).urlTask) }
        // Base com credencial (ou http, consulta, fragmento): nenhum link, e a credencial nunca aparece.
        contexto("https://usuario:senha@jira.exemplo.invalid").use {
            val l = linha(it)
            assertNull(l.urlTask)
            assertNull(l.urlReview)
        }
        // Instância sem URL configurada: só a chave.
        contexto(null).use { assertNull(linha(it).urlTask) }
        // Flag desligada: nenhum link (o painel não aponta para o Jira enquanto o acesso está travado).
        contexto(BASE, ligada = false).use { assertNull(linha(it).urlTask) }
        // O link é montado, nunca acessado: nenhum cliente nem requisição, com a flag ligada ou desligada.
        assertEquals(0, provedor.get())
        assertEquals(0, contador.get())
    }

    // ---- Histórico campo a campo ----

    @Test
    fun historicoSoAvaliaOQueTemRegraDeAutorizacao() = rodar { get ->
        gravar(
            issue("TG-80"),
            listOf(
                ev("TG-80", "labels", null, "login", "2026-10-02T12:00:00Z", "ana"),
                ev("TG-80", "labels", "login LATE_SENT", "login", "2026-10-03T12:00:00Z", "ana"),
                ev("TG-80", "duedate", null, "2026-10-15", "2026-10-04T12:00:00Z", "ana"),
                ev("TG-80", "duedate", "15/10/2026", "2026-10-20", "2026-10-05T12:00:00Z", "ana"),
                ev("TG-80", "duedate", "2026-10-20", null, "2026-10-06T12:00:00Z", "ana"),
                ev("TG-80", "labels", "login", "login LATE_SENT", "2026-10-07T12:00:00Z", "ana"),
                ev("TG-80", "duedate", "2026-10-15", "2026-10-20", "2026-10-08T12:00:00Z", "ana"),
                ev("TG-80", "assignee", "ana", "bruno", "2026-10-09T12:00:00Z", "ana"),
            ),
        )
        val h = json.decodeFromString<HistoricoDto>(get("coord.um", "/api/auditoria/historico/TG-80").corpo)
        assertEquals(listOf(null, null, null, null, null, false, false, false), h.linhas.map { it.autorizado })
        assertEquals(listOf(null, null, null, null, null), h.linhas.take(5).map { it.motivo })
    }

    @Test
    fun historicoCampoACampoSemTextoLivre() = rodar { get ->
        semearBase()
        val r = get("gerente.exemplo", "/api/auditoria/historico/TG-1")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val h = json.decodeFromString<HistoricoDto>(r.corpo)
        assertEquals("TG-1", h.key)
        assertEquals("Resumo TG-1", h.resumo)
        assertEquals("pl.alfa", h.reporter)
        assertEquals(listOf("duedate", "description"), h.linhas.map { it.campo })
        val vencimento = h.linhas.first()
        assertEquals("2026-10-15", vencimento.de)
        assertEquals("2026-10-20", vencimento.para)
        assertTrue(vencimento.valorGuardado)
        assertEquals(false, vencimento.autorizado)
        assertEquals("PROPRIO_RESPONSAVEL", vencimento.motivo)
        // Campo fora da lista de permissão: nenhum valor, só o nome do campo e quem mexeu.
        val descricao = h.linhas.last()
        assertFalse(descricao.valorGuardado)
        assertNull(descricao.de)
        assertNull(descricao.para)
        assertNull(descricao.autorizado)
        assertEquals("ana", descricao.autor)
        // Chave fora do padrão: 400; inexistente: 404.
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/auditoria/historico/TG-1;drop").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/auditoria/historico/tg-1").status)
        assertEquals(HttpStatusCode.NotFound, get("gerente.exemplo", "/api/auditoria/historico/TG-999").status)
    }

    // ---- Auditoria de toda consulta ----

    @Test
    fun todaConsultaGravaAccessLog() = rodar { get ->
        semearBase()
        assertEquals(HttpStatusCode.OK, get("coord.um", "$sinais?sinal=PASSA_BOLA&foo=bar").status)
        assertEquals(HttpStatusCode.BadRequest, get("coord.um", "$sinais?de=2026-13-01").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "$sinais?pessoa=carla").status)
        assertEquals(HttpStatusCode.NotFound, get("coord.um", "/api/auditoria/historico/TG-999").status)
        assertEquals(HttpStatusCode.OK, get("coord.um", "/api/cartoes/AU-01/tasks?segmento=todos").status)
        val l = linhasDoAccessLog()
        assertEquals(5, l.size, l.toString())
        assertEquals(listOf(sinais, sinais, sinais, "/api/auditoria/historico/{key}", "/api/cartoes/AU-01/tasks"), l.map { it.second })
        assertEquals(listOf("coord.um", "coord.um", "ana", "coord.um", "coord.um"), l.map { it.first })
        // Ruling do Plano 11: o registro guarda só QUEM consultou o módulo; nem o sinal pedido, nem o total.
        assertTrue("200" in l[0].third && "PASSA_BOLA" !in l[0].third && "sinal" !in l[0].third, l[0].third)
        // Filtros saneados: só os conhecidos vão para o registro.
        assertFalse("foo" in l[0].third || "bar" in l[0].third, l[0].third)
        assertTrue("400" in l[1].third, l[1].third)
        assertTrue("403" in l[2].third, l[2].third)
        assertTrue("404" in l[3].third, l[3].third)
        assertFalse("total" in l[4].third, l[4].third)
        // A auditoria guarda quantidades, nunca chaves de issue.
        assertFalse(l.any { "TG-" in it.second || "TG-" in it.third }, l.toString())
    }

    // ---- Alteração em lote (I-15) ----

    @Test
    fun emLoteApareceNoSinalSoComOMinimo() = rodar { get ->
        for (k in 1..5) {
            val chave = "TG-${60 + k}"
            gravar(issue(chave, assignee = "bruno"), listOf(ev(chave, "assignee", "ana", "bruno", "2026-10-12T12:00:0${k}Z", "carla")))
        }
        val r = auditoria(get, doPeriodo)
        assertEquals(5, r.total)
        assertTrue(r.sinais.all { it.emLote == 5 }, r.sinais.map { it.emLote }.toString())
    }

    @Test
    fun quatroIssuesNaoMarcamEmLote() = rodar { get ->
        // O mínimo do modelo de `auditoria.yaml` é 5: quatro issues no mesmo minuto não são lote.
        for (k in 1..4) {
            val chave = "TG-${70 + k}"
            gravar(issue(chave, assignee = "bruno"), listOf(ev(chave, "assignee", "ana", "bruno", "2026-10-12T12:00:0${k}Z", "carla")))
        }
        val r = auditoria(get, doPeriodo)
        assertEquals(4, r.total)
        assertTrue(r.sinais.all { it.emLote == null })
    }

    // ---- Sem correção pelo painel ----

    @Test
    fun semRotaDeCorrecao() = testApplication {
        application { module(producao, ctx, autenticador) }
        startApplication()
        val raiz: RoutingNode = application.routing { }
        val todas = mutableListOf<String>()
        fun andar(r: RoutingNode) {
            todas += r.toString()
            r.children.forEach(::andar)
        }
        andar(raiz)
        val daAuditoria = todas.filter { it.startsWith("/api/auditoria") || it.startsWith("/api/cards/task-review") }
        // O teste enxerga as rotas de leitura (não é vácuo)...
        assertTrue(daAuditoria.any { "method:GET" in it && "/api/auditoria/sinais" in it }, daAuditoria.toString())
        assertTrue(daAuditoria.any { "method:GET" in it && "/api/auditoria/historico" in it }, daAuditoria.toString())
        assertTrue(daAuditoria.any { "method:GET" in it && "/api/cards/task-review" in it }, daAuditoria.toString())
        // ...e nenhuma delas altera estado (escrita no Jira pelo painel é o Plano 16, futuro).
        assertTrue(daAuditoria.none { Regex("method:(POST|PUT|PATCH|DELETE)").containsMatchIn(it) }, daAuditoria.toString())
    }
}
