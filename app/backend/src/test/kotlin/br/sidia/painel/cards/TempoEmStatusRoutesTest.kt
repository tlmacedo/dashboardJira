package br.sidia.painel.cards

import br.sidia.painel.AppContext
import br.sidia.painel.config.Settings
import br.sidia.painel.module
import br.sidia.painel.security.AtribuicaoPapel
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.DevAuthenticator
import br.sidia.painel.security.Pessoa
import br.sidia.painel.security.Principal
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.Vigencia
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.PostgresIssueStore
import br.sidia.painel.testContextComBanco
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rotas do card "Tempo em status" contra PostgreSQL real (Testcontainers), diretório de papéis em
 * memória e relógio fixo. Nenhuma rede, nenhum Jira.
 */
class TempoEmStatusRoutesTest {
    companion object {
        private val pg = PostgreSQLContainer("postgres:16").apply { start() }

        @JvmStatic
        @AfterAll
        fun pararContainer() {
            pg.stop()
        }
    }

    private val ID_OS = "customfield_12416"

    private val zona = ZoneId.of("America/Manaus")

    // Domingo, 25/10/2026, 12:00 em Manaus.
    private val relogio: Clock = Clock.fixed(Instant.parse("2026-10-25T16:00:00Z"), zona)
    private val hoje: LocalDate = LocalDate.now(relogio)
    private val producao = Settings(dashboardDevMode = false)

    private fun pessoa(id: String, papel: Role, squad: String? = null, ate: LocalDate? = null) =
        Pessoa(id, id, listOf(AtribuicaoPapel(papel, Vigencia(null, ate), squad)))

    private val diretorio = RoleDirectory(
        listOf(
            pessoa("ana", Role.USUARIO, "Alfa"),
            pessoa("bruno", Role.USUARIO, "Alfa"),
            pessoa("lider.alfa", Role.SQUAD_LEADER, "Alfa"),
            pessoa("carla", Role.USUARIO, "Beta"),
            pessoa("coord.um", Role.COORDENADOR),
            pessoa("gerente.exemplo", Role.GERENTE),
            // Papel encerrado ontem: autenticada, porém sem papel vigente hoje.
            pessoa("ex.papel", Role.USUARIO, "Alfa", ate = LocalDate.of(2026, 10, 24)),
        ),
    )

    private lateinit var ctx: AppContext

    @BeforeTest
    fun preparar() {
        ctx = testContextComBanco(producao, pg, diretorio, relogio)
        semear()
    }

    @AfterTest
    fun encerrar() {
        ctx.close()
    }

    // ---- Dados ----

    private class Ev(val campo: String, val quando: String, val de: String?, val para: String?)

    private fun t(s: String): OffsetDateTime = OffsetDateTime.parse("2026-${s.replace(' ', 'T')}:00-04:00")

    private fun st(quando: String, de: String?, para: String) = Ev("status", quando, de, para)

    private fun resp(quando: String, de: String?, para: String) = Ev("assignee", quando, de, para)

    private fun issue(
        chave: String, tipo: String, responsavel: String?, status: String, resolucao: String?,
        criada: OffsetDateTime, resolvida: OffsetDateTime?, eventos: List<Ev>,
    ) {
        val json: JsonObject = buildJsonObject {
            put("key", chave)
            putJsonObject("fields") {
                put("summary", "Resumo de $chave")
                putJsonObject("project") { put("key", "TG") }
                putJsonObject("issuetype") { put("name", tipo) }
                putJsonObject("status") { put("name", status) }
                if (resolucao != null) putJsonObject("resolution") { put("name", resolucao) }
                if (responsavel != null) putJsonObject("assignee") { put("name", responsavel) }
                put("created", criada.toString())
                if (resolvida != null) put("resolutiondate", resolvida.toString())
                osPorChave[chave]?.let { putJsonObject(ID_OS) { put("value", it) } }
            }
        }
        val registros = eventos.mapIndexed { i, e ->
            ChangeEventRecord("$chave:h$i:0", chave, e.campo, e.de, e.para, t(e.quando).toInstant(), "autor")
        }
        PostgresIssueStore(requireNotNull(ctx.dataSource)).upsert(json, registros, "samsung", ID_OS)
    }

    // OS Version por issue: A em TG-1, TG-3, TG-5, TG-9 e TG-6 (aberta); B em TG-2 e TG-4; as demais sem valor.
    private val osA = "Android 17.0 (OneUI_9.0)"
    private val osB = "Android 16.0 (16)"
    private val osPorChave = mapOf(
        "TG-1" to osA, "TG-2" to osB, "TG-3" to osA, "TG-4" to osB, "TG-5" to osA, "TG-9" to osA, "TG-6" to osA,
    )

    private fun semear() {
        // Concluídas dentro dos 90 dias até 25/10/2026.
        issue(
            "TG-1", "Checklist","ana", "Concluído", "Done", t("10-01 09:00"), t("10-07 09:00"),
            listOf(
                resp("10-02 10:00", null, "ana"), st("10-02 10:00", "A Fazer", "Em andamento"),
                st("10-05 09:00", "Em andamento", "Em revisão"), st("10-07 09:00", "Em revisão", "Concluído"),
            ),
        )
        issue(
            "TG-2", "Checklist TG","bruno", "Concluído", "Done", t("10-01 10:00"), t("10-08 15:00"),
            listOf(
                resp("10-01 10:00", null, "bruno"), st("10-01 11:00", "A Fazer", "Em andamento"),
                st("10-06 15:00", "Em andamento", "Em revisão"), st("10-08 15:00", "Em revisão", "Concluído"),
            ),
        )
        issue(
            "TG-3", "Checklist","carla", "Concluído", "Done", t("10-02 09:00"), t("10-12 10:00"),
            listOf(
                resp("10-02 09:00", null, "carla"), st("10-02 10:00", "A Fazer", "Em andamento"),
                st("10-09 10:00", "Em andamento", "Em revisão"), st("10-12 10:00", "Em revisão", "Concluído"),
            ),
        )
        // Passou de ana para bruno (os dois do Alfa).
        issue(
            "TG-4", "Checklist","bruno", "Concluído", "Done", t("10-01 09:00"), t("10-13 09:00"),
            listOf(
                resp("10-01 09:00", null, "ana"), st("10-01 09:30", "A Fazer", "Em andamento"),
                resp("10-05 09:00", "ana", "bruno"),
                st("10-08 09:00", "Em andamento", "Em revisão"), st("10-13 09:00", "Em revisão", "Concluído"),
            ),
        )
        // Responsável que não está no diretório: só coordenação e gerente.
        issue(
            "TG-5", "Checklist","externo.fora", "Concluído", "Done", t("10-02 11:00"), t("10-14 12:00"),
            listOf(
                resp("10-02 11:00", null, "externo.fora"), st("10-02 12:00", "A Fazer", "Em andamento"),
                st("10-14 12:00", "Em andamento", "Concluído"),
            ),
        )
        // Concluída em 03/08 (82 dias antes de hoje): dentro do padrão de 90 dias.
        issue(
            "TG-9", "Checklist","ana", "Concluído", "Done", t("07-31 09:00"), t("08-03 10:00"),
            listOf(
                resp("07-31 09:00", null, "ana"), st("07-31 10:00", "A Fazer", "Em andamento"),
                st("08-03 10:00", "Em andamento", "Concluído"),
            ),
        )
        // Concluída em maio: fora dos 90 dias.
        issue(
            "TG-8", "Checklist","ana", "Concluído", "Done", t("05-01 09:00"), t("05-04 10:00"),
            listOf(
                resp("05-01 09:00", null, "ana"), st("05-01 10:00", "A Fazer", "Em andamento"),
                st("05-04 10:00", "Em andamento", "Concluído"),
            ),
        )
        // Abertas paradas.
        issue(
            "TG-6", "Checklist","bruno", "Em revisão", null, t("10-01 12:00"), null,
            listOf(resp("10-01 12:00", null, "bruno"), st("10-02 09:00", "A Fazer", "Em revisão")),
        )
        issue(
            "TG-7", "Checklist","carla", "Em andamento", null, t("10-01 12:00"), null,
            listOf(resp("10-01 12:00", null, "carla"), st("10-02 09:00", "A Fazer", "Em andamento")),
        )
        // Aberta que foi da ana e hoje é do bruno: o nome do bruno não pode aparecer para a ana.
        issue(
            "TG-10", "Checklist","bruno", "Em revisão", null, t("10-01 09:00"), null,
            listOf(
                resp("10-01 09:00", null, "ana"), st("10-02 09:00", "A Fazer", "Em revisão"),
                resp("10-05 09:00", "ana", "bruno"),
            ),
        )
    }

    // ---- Infraestrutura de requisição ----

    private val autenticador = object : Authenticator {
        override suspend fun authenticate(call: ApplicationCall): Principal? =
            call.request.headers["X-Teste-Usuario"]?.let { principalFor(it, diretorio, hoje) }
    }

    private class Resp(val status: HttpStatusCode, val corpo: String)

    private inner class Cliente(private val http: HttpClient) {
        suspend fun get(usuario: String?, caminho: String): Resp {
            val r = http.get(caminho) { if (usuario != null) header("X-Teste-Usuario", usuario) }
            return Resp(r.status, r.bodyAsText())
        }

        suspend fun card(usuario: String, consulta: String = ""): TempoEmStatusDto {
            val r = get(usuario, "/api/cards/tempo-em-status$consulta")
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            return Json.decodeFromString(r.corpo)
        }

        suspend fun drill(usuario: String, kind: String, consulta: String = ""): List<DrillRow> {
            val r = get(usuario, "/api/drill/tempo-em-status?kind=${enc(kind)}$consulta")
            assertEquals(HttpStatusCode.OK, r.status, r.corpo)
            return Json.decodeFromString(r.corpo)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    private fun rodar(
        auth: Authenticator = autenticador,
        settings: Settings = producao,
        bloco: suspend Cliente.() -> Unit,
    ) = testApplication {
        // O módulo usa sempre as configurações do contexto: para o modo dev, mesmo banco e dados com outras configurações.
        val contexto = if (settings == ctx.settings) {
            ctx
        } else {
            AppContext(settings, ctx.calendar, ctx.roles, ctx.dataSource, ctx.jira, ctx.statusMap, ctx.campos, ctx.fichas, ctx.clock)
        }
        application { module(settings, contexto, auth) }
        Cliente(client).bloco()
    }

    private fun chaves(linhas: List<DrillRow>) = linhas.map { it.key }.sorted()

    private fun linhasDoAccessLog(): List<Triple<String, String, String>> =
        requireNotNull(ctx.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT user_id, route, filters FROM access_log ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(Triple(rs.getString(1), rs.getString(2), rs.getString(3).orEmpty())) }
            }
        }

    // ---- /api/me ----

    @Test
    fun meDevolveOsPapeisDoDiretorio() = rodar {
        val r = get("ana", "/api/me")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(MeDto("ana", listOf("USUARIO")), Json.decodeFromString<MeDto>(r.corpo))
        // Papel encerrado ontem: sem papel vigente, 403 sem corpo.
        val negada = get("ex.papel", "/api/me")
        assertEquals(HttpStatusCode.Forbidden, negada.status)
        assertEquals("", negada.corpo)
        // Sem login: 401.
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/me").status)
    }

    @Test
    fun meNoModoDesenvolvimentoDevolveODevGerente() = rodar(DevAuthenticator(clock = relogio), Settings(dashboardDevMode = true)) {
        val me = Json.decodeFromString<MeDto>(get(null, "/api/me").corpo)
        assertEquals(MeDto("dev", listOf("GERENTE")), me)
    }

    // ---- Filtro por papel ----

    // ---- Filtro OS Version ----

    private fun osNaUrl(os: String) = "&os=${enc(os)}"

    @Test
    fun filtroOsFiltraDeVerdadeNoCardENoDrill() = rodar {
        val semFiltro = card("gerente.exemplo")
        assertEquals(6, semFiltro.analisadas)
        val a = card("gerente.exemplo", "?os=${enc(osA)}")
        // Concluídas com A no período: TG-1, TG-3, TG-5, TG-9.
        assertEquals(4, a.analisadas)
        assertTrue("OS_INDISPONIVEL" !in a.avisos.map { it.codigo }, a.avisos.toString())
        val drillA = chaves(drill("gerente.exemplo", "analisadas", osNaUrl(osA)))
        assertEquals(listOf("TG-1", "TG-3", "TG-5", "TG-9"), drillA)
        assertEquals(a.analisadas, drillA.size)
        // B: TG-2 e TG-4.
        val b = card("gerente.exemplo", "?os=${enc(osB)}")
        assertEquals(listOf("TG-2", "TG-4"), chaves(drill("gerente.exemplo", "analisadas", osNaUrl(osB))))
        assertEquals(2, b.analisadas)
    }

    @Test
    fun filtroOsTambemFiltraAsAbertas() = rodar {
        // Sem filtro: TG-6, TG-7 e TG-10 paradas. Com A só TG-6; com B nenhuma aberta.
        assertEquals(3, card("gerente.exemplo").paradas)
        val a = card("gerente.exemplo", "?os=${enc(osA)}")
        assertEquals(listOf("TG-6"), a.abertasParadas.map { it.key })
        assertEquals(1, a.paradas)
        val drillA = drill("gerente.exemplo", "paradas", osNaUrl(osA))
        assertEquals(listOf("TG-6"), chaves(drillA))
        assertEquals(a.paradas, drillA.size)
        val b = card("gerente.exemplo", "?os=${enc(osB)}")
        assertEquals(0, b.paradas)
        assertEquals(emptyList(), drill("gerente.exemplo", "paradas", osNaUrl(osB)))
    }

    @Test
    fun filtroOsNormalizaEspacosComoOValorGravado() = rodar {
        // Dois espaços internos e espaços nas pontas casam com o valor gravado normalizado.
        val digitado = "  Android  17.0   (OneUI_9.0) "
        assertEquals(4, card("gerente.exemplo", "?os=${enc(digitado)}").analisadas)
        assertEquals(4, drill("gerente.exemplo", "analisadas", osNaUrl(digitado)).size)
    }

    @Test
    fun filtroOsNaoDiferenciaMaiusculas() = rodar {
        val minusculo = osA.lowercase()
        assertEquals(4, card("gerente.exemplo", "?os=${enc(minusculo)}").analisadas)
        assertEquals(4, chaves(drill("gerente.exemplo", "analisadas", osNaUrl(osA.uppercase()))).size)
    }

    @Test
    fun filtroOsComparaValorExatoSemCasarPorTrecho() = rodar {
        // "Android 17.0" é prefixo de A, mas não é igual: nada casa.
        val dto = card("gerente.exemplo", "?os=${enc("Android 17.0")}")
        assertEquals(0, dto.analisadas)
        // Curingas do LIKE não funcionam como curinga.
        assertEquals(0, card("gerente.exemplo", "?os=${enc("%")}").analisadas)
    }

    @Test
    fun filtroOsInexistenteDevolveListaVaziaSemErro() = rodar {
        val dto = card("gerente.exemplo", "?os=${enc("Android 99.0 (Inexistente)")}")
        assertEquals(0, dto.analisadas)
        assertEquals(0, dto.paradas)
        assertTrue("OS_INDISPONIVEL" !in dto.avisos.map { it.codigo }, dto.avisos.toString())
        assertEquals(emptyList(), drill("gerente.exemplo", "analisadas", osNaUrl("Android 99.0 (Inexistente)")))
    }

    @Test
    fun filtroOsInvalidoRetorna400SemCorpo() = rodar {
        val longo = get("gerente.exemplo", "/api/cards/tempo-em-status?os=${"x".repeat(101)}")
        assertEquals(HttpStatusCode.BadRequest, longo.status)
        assertEquals("", longo.corpo)
        val controle = get("gerente.exemplo", "/api/cards/tempo-em-status?os=a%00b")
        assertEquals(HttpStatusCode.BadRequest, controle.status)
        assertEquals("", controle.corpo)
        val drillRuim = get("gerente.exemplo", "/api/drill/tempo-em-status?kind=analisadas&os=a%0Ab")
        assertEquals(HttpStatusCode.BadRequest, drillRuim.status)
        assertEquals("", drillRuim.corpo)
    }

    @Test
    fun filtroOsSemDadoSincronizadoAvisaSem500() = rodar {
        // customId ainda não descoberto: nenhuma issue com os_version.
        requireNotNull(ctx.dataSource).connection.use { it.createStatement().execute("UPDATE issue SET os_version = NULL") }
        val dto = card("gerente.exemplo", "?os=${enc(osA)}")
        assertEquals(0, dto.analisadas)
        val osAviso = dto.avisos.single { it.codigo == "OS_INDISPONIVEL" }
        assertEquals("informativo", osAviso.gravidade)
        assertEquals(emptyList(), drill("gerente.exemplo", "analisadas", osNaUrl(osA)))
        // Sem o filtro, nenhum aviso de OS Version e os dados voltam.
        val semFiltro = card("gerente.exemplo")
        assertEquals(6, semFiltro.analisadas)
        assertTrue("OS_INDISPONIVEL" !in semFiltro.avisos.map { it.codigo })
    }

    @Test
    fun filtroOsNaoAmpliaAVisibilidade() = rodar {
        // A ana só vê o que é dela: TG-1, TG-4 (B) e TG-9. Com os=A, só TG-1 e TG-9.
        assertEquals(listOf("TG-1", "TG-9"), chaves(drill("ana", "analisadas", osNaUrl(osA))))
        // TG-3 (carla) e TG-5 (externo) têm o OS A, mas a ana não os enxerga.
        val dto = card("ana", "?os=${enc(osA)}")
        assertEquals(2, dto.analisadas)
        assertTrue(dto.pessoaStatus.all { it.person == "ana" })
        // O filtro tampouco burla o 403 de pessoa fora da visibilidade.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/tempo-em-status?pessoa=carla&os=${enc(osA)}").status)
    }

    @Test
    fun filtroOsVaiParaAAuditoria() = rodar {
        card("gerente.exemplo", "?os=${enc(osA)}")
        assertTrue(linhasDoAccessLog().any { "os" in it.third && "OneUI_9.0" in it.third }, linhasDoAccessLog().toString())
    }

    @Test
    fun parametroSquadVemDoDiretorio() = rodar {
        val alfa = card("gerente.exemplo", "?squad=Alfa")
        // Alfa na data: ana, bruno e lider.alfa (TG-1, TG-2, TG-4, TG-9); nunca carla nem o assignee de fora.
        assertEquals(4, alfa.analisadas)
        assertEquals(listOf("TG-1", "TG-2", "TG-4", "TG-9"), chaves(drill("gerente.exemplo", "analisadas", "&squad=Alfa")))
        assertEquals(1, card("gerente.exemplo", "?squad=Beta").analisadas)
        // Squad inexistente: 400, sem eco do valor.
        val ruim = get("gerente.exemplo", "/api/cards/tempo-em-status?squad=Inexistente")
        assertEquals(HttpStatusCode.BadRequest, ruim.status)
        assertFalse("Inexistente" in ruim.corpo)
    }

    @Test
    fun usuarioVeSoOProprio() = rodar {
        val dto = card("ana")
        // TG-1, TG-4 (teve posse antes do bruno) e TG-9.
        assertEquals(3, dto.analisadas)
        assertTrue(dto.pessoaStatus.isNotEmpty())
        assertTrue(dto.pessoaStatus.all { it.person == "ana" }, dto.pessoaStatus.toString())
    }

    @Test
    fun usuarioNaoAmpliaAVisaoComSquadDeOutro() = rodar {
        // squad=Beta é um nome válido, mas a ana não vê ninguém de lá.
        assertEquals(0, card("ana", "?squad=Beta").analisadas)
        assertEquals(emptyList(), drill("ana", "analisadas", "&squad=Beta"))
    }

    @Test
    fun squadLeaderVeSeuSquad() = rodar {
        val dto = card("lider.alfa")
        assertEquals(4, dto.analisadas)
        val chaves = chaves(drill("lider.alfa", "analisadas"))
        assertEquals(listOf("TG-1", "TG-2", "TG-4", "TG-9"), chaves)
        assertFalse("TG-3" in chaves)
        assertTrue(dto.pessoaStatus.none { it.person == "carla" || it.person == "externo.fora" })
    }

    @Test
    fun gerenteVeTodos() = rodar {
        assertEquals(6, card("gerente.exemplo").analisadas)
        assertEquals(6, card("coord.um").analisadas)
        val pessoas = card("gerente.exemplo").pessoaStatus.map { it.person }.toSet()
        assertEquals(setOf("ana", "bruno", "carla", "externo.fora"), pessoas)
    }

    @Test
    fun pessoaDeOutroDoUsuarioComumRetorna403() = rodar {
        val r = get("ana", "/api/cards/tempo-em-status?pessoa=bruno")
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals("", r.corpo)
        assertEquals(HttpStatusCode.Forbidden, get("carla", "/api/cards/tempo-em-status?pessoa=ana").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/drill/tempo-em-status?kind=pessoa:bruno:status:Em+andamento").status)
        // Dentro da visibilidade, pode.
        assertEquals(HttpStatusCode.OK, get("lider.alfa", "/api/cards/tempo-em-status?pessoa=bruno").status)
        assertEquals(HttpStatusCode.OK, get("ana", "/api/cards/tempo-em-status?pessoa=ana").status)
        // Coordenação pode pedir até quem não tem dado.
        assertEquals(0, card("coord.um", "?pessoa=ninguem.com.dado").analisadas)
    }

    @Test
    fun issueDeAssigneeForaDoDiretorioSoParaCoordenacao() = rodar {
        for (quem in listOf("coord.um", "gerente.exemplo")) {
            assertTrue("TG-5" in chaves(drill(quem, "analisadas")), quem)
        }
        for (quem in listOf("ana", "lider.alfa", "carla")) {
            assertFalse("TG-5" in chaves(drill(quem, "analisadas")), quem)
            assertFalse(card(quem).pessoaStatus.any { it.person == "externo.fora" }, quem)
        }
    }

    @Test
    fun naoExpoeNomesForaDaVisibilidade() = rodar {
        // TG-4 é da ana e hoje está com o bruno: a ana vê a task, mas não o nome do bruno.
        val linhas = drill("ana", "analisadas")
        assertEquals("ana", linhas.first { it.key == "TG-1" }.responsavel)
        assertEquals(null, linhas.first { it.key == "TG-4" }.responsavel)
        // Aberta parada que passou da ana para o bruno: aparece, mas sem o nome dele.
        val paradas = card("ana").abertasParadas
        assertEquals(listOf("TG-10"), paradas.map { it.key })
        assertEquals(null, paradas.single().assignee)
        assertEquals(listOf("TG-10"), chaves(drill("ana", "paradas")))
        assertEquals(null, drill("ana", "paradas").single().responsavel)
        // Quem pode ver o bruno vê o nome dele.
        assertEquals("bruno", card("lider.alfa").abertasParadas.first { it.key == "TG-6" }.assignee)
    }

    // ---- Filtros e período ----

    @Test
    fun unidadePadraoEUtil() = rodar {
        val util = card("gerente.exemplo")
        assertEquals("util", util.unit)
        assertEquals("util", card("gerente.exemplo", "?unit=util").unit)
        val corrido = card("gerente.exemplo", "?unit=corrido")
        assertEquals("corrido", corrido.unit)
        // Fins de semana contam só nos dias corridos.
        assertTrue(corrido.porStatus.sumOf { it.median } > util.porStatus.sumOf { it.median })
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/tempo-em-status?unit=semana").status)
    }

    @Test
    fun periodoPadraoNoventaDias() = rodar {
        // TG-8 (maio) fica fora; TG-9 (03/08, 82 dias antes) entra.
        val padrao = chaves(drill("gerente.exemplo", "analisadas"))
        assertTrue("TG-9" in padrao)
        assertFalse("TG-8" in padrao)
        // Período explícito traz a de maio; o fim é inclusivo (TG-1 foi resolvida em 07/10).
        assertEquals(7, card("gerente.exemplo", "?de=2026-01-01&ate=2026-10-25").analisadas)
        assertEquals(listOf("TG-1"), chaves(drill("gerente.exemplo", "analisadas", "&de=2026-10-01&ate=2026-10-07")))
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/tempo-em-status?de=2026-10-10&ate=2026-10-01").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/tempo-em-status?de=ontem").status)
    }

    @Test
    fun filtroPorTipoEOutliers() = rodar {
        assertEquals(listOf("TG-2"), chaves(drill("gerente.exemplo", "analisadas", "&tipo=Checklist%20TG")))
        // TG-9 tem ciclo de 3 dias e TG-5 de 12: nenhum passa de 30 dias, então semOutliers não muda nada aqui.
        assertEquals(6, card("gerente.exemplo", "?semOutliers=true").analisadas)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/cards/tempo-em-status?semOutliers=talvez").status)
    }

    // ---- Detalhamento ----

    @Test
    fun drillTemOMesmoTamanhoDoCard() = rodar {
        for (quem in listOf("gerente.exemplo", "lider.alfa", "ana", "carla")) {
            val dto = card(quem)
            assertEquals(dto.analisadas, drill(quem, "analisadas").size, quem)
            assertEquals(dto.paradas, drill(quem, "paradas").size, quem)
            assertTrue(dto.porStatus.isNotEmpty(), quem)
            for (s in dto.porStatus) {
                assertEquals(s.n, drill(quem, "status:${s.status}").size, "$quem ${s.status}")
            }
            for (p in dto.pessoaStatus) {
                assertEquals(p.n, drill(quem, "pessoa:${p.person}:status:${p.status}").size, "$quem ${p.person} ${p.status}")
            }
        }
        val gerente = card("gerente.exemplo")
        assertEquals(3, gerente.paradas)
        assertTrue(gerente.porStatus.any { it.status == "Em revisão" })
        // Status que não existe no card: lista vazia, igual a n=0.
        assertEquals(emptyList(), drill("gerente.exemplo", "status:Inexistente"))
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/drill/tempo-em-status?kind=outra").status)
        assertEquals(HttpStatusCode.BadRequest, get("gerente.exemplo", "/api/drill/tempo-em-status").status)
    }

    // ---- Dados ruins ----

    @Test
    fun statusNaoMapeadoVaiParaAvisos() = rodar {
        issue(
            "TG-11", "Checklist","bruno", "Concluído", "Done", t("10-01 09:00"), t("10-20 09:00"),
            listOf(
                resp("10-01 09:00", null, "bruno"), st("10-01 10:00", "A Fazer", "Status Maluco"),
                st("10-20 09:00", "Status Maluco", "Concluído"),
            ),
        )
        val r = get("gerente.exemplo", "/api/cards/tempo-em-status")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val dto = Json.decodeFromString<TempoEmStatusDto>(r.corpo)
        val semMapa = dto.avisos.single { it.codigo == "STATUS_SEM_MAPEAMENTO" }
        assertEquals("bloqueante", semMapa.gravidade)
        assertTrue("Status Maluco" in semMapa.mensagem, semMapa.mensagem)
        assertEquals(0, dto.analisadas)
        assertEquals(null, dto.eficienciaFluxo)
        assertEquals(0, dto.paradas)
        assertTrue(dto.porStatus.isEmpty() && dto.pessoaStatus.isEmpty() && dto.abertasParadas.isEmpty())
        assertEquals(emptyList(), drill("gerente.exemplo", "analisadas"))
        // A ana não tem posse em TG-11: nada de aviso nem de número zerado para ela.
        val ana = card("ana")
        assertTrue(ana.avisos.isEmpty(), ana.avisos.toString())
        assertEquals(3, ana.analisadas)
    }

    @Test
    fun avisoTrazParametroDoStatus() = rodar {
        issue(
            "TG-11", "Checklist", "bruno", "Concluído", "Done", t("10-01 09:00"), t("10-20 09:00"),
            listOf(
                resp("10-01 09:00", null, "bruno"), st("10-01 10:00", "A Fazer", "Revisando"),
                st("10-20 09:00", "Revisando", "Concluído"),
            ),
        )
        val dto = card("gerente.exemplo")
        val semMapa = dto.avisos.single { it.codigo == "STATUS_SEM_MAPEAMENTO" }
        assertEquals("Revisando", semMapa.params["status"])
    }

    // ---- Filtros da ficha e conclusão divergente ----

    @Test
    fun cardIgnoraTipoEStatusForaDaFicha() = rodar {
        val antes = card("ana")
        val drillAntes = chaves(drill("ana", "analisadas"))
        // Epic concluído e task Cancelled (status sem mapeamento) da ana, fechados no período, e as mesmas abertas.
        issue(
            "TG-30", "Epic", "ana", "Concluído", "Done", t("10-01 09:00"), t("10-10 09:00"),
            listOf(resp("10-01 09:00", null, "ana"), st("10-01 10:00", "A Fazer", "Em andamento"), st("10-10 09:00", "Em andamento", "Concluído")),
        )
        issue(
            "TG-31", "Checklist", "ana", "Cancelled", "Done", t("10-01 09:00"), t("10-11 09:00"),
            listOf(resp("10-01 09:00", null, "ana"), st("10-01 10:00", "A Fazer", "Em andamento"), st("10-11 09:00", "Em andamento", "Cancelled")),
        )
        issue(
            "TG-32", "Epic", "ana", "Em revisão", null, t("10-01 09:00"), null,
            listOf(resp("10-01 09:00", null, "ana"), st("10-02 09:00", "A Fazer", "Em revisão")),
        )
        issue(
            "TG-33", "Checklist", "ana", "Cancelled", null, t("10-01 09:00"), null,
            listOf(resp("10-01 09:00", null, "ana"), st("10-02 09:00", "A Fazer", "Cancelled")),
        )
        val depois = card("ana")
        assertEquals(antes.analisadas, depois.analisadas)
        assertEquals(antes.paradas, depois.paradas)
        assertTrue(depois.avisos.isEmpty(), depois.avisos.toString())
        assertEquals(drillAntes, chaves(drill("ana", "analisadas")))
        assertEquals(emptyList(), chaves(drill("ana", "status:Em revisão")).filter { it in setOf("TG-30", "TG-31", "TG-32", "TG-33") })
        assertEquals(emptyList(), chaves(drill("ana", "pessoa:ana:status:Em revisão")).filter { it in setOf("TG-30", "TG-31", "TG-32", "TG-33") })
        assertEquals(emptyList(), chaves(drill("ana", "paradas")).filter { it in setOf("TG-32", "TG-33") })
        // O gerente também não os conta.
        assertEquals(6, card("gerente.exemplo").analisadas)
    }

    @Test
    fun drillDaPropriaPessoaSemIssuesDaListaVazia() = rodar {
        // Sem nenhuma issue na base, a pessoa não aparece em lugar nenhum: a própria ana ainda recebe lista vazia.
        executarSql("DELETE FROM change_event; DELETE FROM issue")
        val r = get("ana", "/api/drill/tempo-em-status?kind=${enc("pessoa:ana:status:Em revisão")}")
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        assertEquals(emptyList(), Json.decodeFromString<List<DrillRow>>(r.corpo))
        // Outra pessoa continua negada.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/drill/tempo-em-status?kind=${enc("pessoa:bruno:status:Em revisão")}").status)
    }

    @Test
    fun concluidaSemResolutionViraAvisoDivergente() = rodar {
        val antes = card("gerente.exemplo")
        // Status Concluído (DONE) sem resolution: aparece nas duas consultas (fechada no período e sem resolution).
        issue(
            "TG-40", "Checklist", "ana", "Concluído", null, t("10-01 09:00"), t("10-15 09:00"),
            listOf(resp("10-01 09:00", null, "ana"), st("10-01 10:00", "A Fazer", "Em andamento"), st("10-15 09:00", "Em andamento", "Concluído")),
        )
        val depois = card("gerente.exemplo")
        val aviso = depois.avisos.single { it.codigo == "CONCLUSAO_DIVERGENTE" }
        assertEquals("informativo", aviso.gravidade)
        // Contada uma vez só, mesmo estando nas duas consultas.
        assertEquals("1", aviso.params["n"])
        assertEquals(antes.analisadas, depois.analisadas)
        assertEquals(antes.paradas, depois.paradas)
        assertTrue("TG-40" !in chaves(drill("gerente.exemplo", "analisadas")))
        assertTrue("TG-40" !in chaves(drill("gerente.exemplo", "paradas")))
        assertTrue(depois.abertasParadas.none { it.key == "TG-40" })
        // Quem não enxerga a ana não recebe o aviso.
        assertTrue(card("carla").avisos.none { it.codigo == "CONCLUSAO_DIVERGENTE" })
    }

    @Test
    fun resolutionSemConclusaoTambemEhDivergente() = rodar {
        // Resolution preenchida, mas o status não é DONE.
        issue(
            "TG-41", "Checklist", "ana", "Em revisão", "Done", t("10-01 09:00"), t("10-15 09:00"),
            listOf(resp("10-01 09:00", null, "ana"), st("10-02 09:00", "A Fazer", "Em revisão")),
        )
        val dto = card("gerente.exemplo")
        assertEquals("1", dto.avisos.single { it.codigo == "CONCLUSAO_DIVERGENTE" }.params["n"])
        assertEquals(6, dto.analisadas)
        assertTrue("TG-41" !in chaves(drill("gerente.exemplo", "analisadas")))
    }

    // ---- Auditoria ----

    @Test
    fun consultaEhRegistrada() = rodar {
        assertEquals(HttpStatusCode.OK, get("ana", "/api/me").status)
        assertEquals(HttpStatusCode.OK, get("ana", "/api/cards/tempo-em-status?squad=Alfa&token=segredo123").status)
        assertEquals(HttpStatusCode.OK, get("ana", "/api/drill/tempo-em-status?kind=analisadas").status)
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/tempo-em-status?pessoa=bruno").status)
        assertEquals(HttpStatusCode.Forbidden, get("ex.papel", "/api/me").status)
        val linhas = linhasDoAccessLog()
        assertEquals(5, linhas.size, linhas.toString())
        assertEquals(listOf("ana", "ana", "ana", "ana", "ex.papel"), linhas.map { it.first })
        assertEquals(
            listOf(
                "/api/me", "/api/cards/tempo-em-status", "/api/drill/tempo-em-status",
                "/api/cards/tempo-em-status", "/api/me",
            ),
            linhas.map { it.second },
        )
        assertTrue("\"squad\":\"Alfa\"" in linhas[1].third, linhas[1].third)
        assertTrue(linhas.none { "segredo123" in it.third || "token" in it.third }, linhas.toString())
        // A negada fica registrada com o filtro pedido e o resultado.
        assertTrue("\"pessoa\":\"bruno\"" in linhas[3].third && "403" in linhas[3].third, linhas[3].third)
    }

    private fun executarSql(sql: String) {
        requireNotNull(ctx.dataSource).connection.use { c -> c.createStatement().use { it.execute(sql) } }
    }

    @Test
    fun falhaDeAuditoriaFazARotaResponder503SemDados() = rodar {
        // Sem a tabela a gravação falha; o banco de issues segue funcionando.
        executarSql("ALTER TABLE access_log RENAME TO access_log_fora")
        try {
            for (caminho in listOf("/api/cards/tempo-em-status", "/api/drill/tempo-em-status?kind=analisadas", "/api/me")) {
                val r = get("gerente.exemplo", caminho)
                assertEquals(HttpStatusCode.ServiceUnavailable, r.status, caminho)
                assertEquals("", r.corpo, caminho)
            }
        } finally {
            executarSql("ALTER TABLE access_log_fora RENAME TO access_log")
        }
    }

    @Test
    fun chamadasPublicasNaoGravam() = rodar {
        assertEquals(HttpStatusCode.OK, get(null, "/api/health").status)
        assertEquals(HttpStatusCode.OK, get(null, "/api/meta").status)
        get(null, "/")
        get(null, "/assets/x.js")
        assertEquals(emptyList(), linhasDoAccessLog())
    }

    @Test
    fun modoDesenvolvimentoVeTudoERegistraODev() = rodar(DevAuthenticator(clock = relogio), Settings(dashboardDevMode = true)) {
        val dto = Json.decodeFromString<TempoEmStatusDto>(get(null, "/api/cards/tempo-em-status").corpo)
        assertEquals(6, dto.analisadas)
        assertEquals("dev", linhasDoAccessLog().single().first)
    }

    // ---- Robustez, ordenação e visibilidade (onda final) ----

    @Test
    fun datasExtremasRetornam400SemCorpo() = rodar {
        for (consulta in listOf(
            "?de=-999999999-01-01", "?ate=+999999999-12-31", "?de=+999999999-01-01&ate=+999999999-12-31",
            "?de=1900-01-01", "?ate=9999-12-31", "?de=0000-01-01&ate=2026-01-01",
        )) {
            val card = get("gerente.exemplo", "/api/cards/tempo-em-status$consulta")
            assertEquals(HttpStatusCode.BadRequest, card.status, consulta)
            assertEquals("", card.corpo, consulta)
            val drill = get("gerente.exemplo", "/api/drill/tempo-em-status?kind=analisadas&${consulta.drop(1)}")
            assertEquals(HttpStatusCode.BadRequest, drill.status, consulta)
            assertEquals("", drill.corpo, consulta)
        }
    }

    @Test
    fun drillOrdenaChavesDeFormaNatural() = rodar {
        // Abertas paradas: TG-6, TG-7 e TG-10; como texto o TG-10 viria primeiro.
        assertEquals(listOf("TG-6", "TG-7", "TG-10"), drill("gerente.exemplo", "paradas").map { it.key })
        assertEquals(listOf("TG-6", "TG-7", "TG-10"), card("gerente.exemplo").abertasParadas.map { it.key })
    }

    @Test
    fun issueComDatasInconsistentesEhIgnoradaEAvisada() = rodar {
        // Resolvida antes de ser criada.
        issue(
            "TG-20", "Checklist","ana", "Concluído", "Done", t("10-10 09:00"), t("10-05 09:00"),
            listOf(resp("10-10 09:00", null, "ana"), st("10-10 10:00", "A Fazer", "Em andamento")),
        )
        for (usuario in listOf("gerente.exemplo", "ana")) {
            val dto = card(usuario)
            val av = dto.avisos.single { it.codigo == "DATAS_INCONSISTENTES" }
            assertEquals("informativo", av.gravidade, usuario)
            assertTrue("1 issue(s)" in av.mensagem, "$usuario ${dto.avisos}")
            assertTrue(dto.avisos.none { "TG-20" in it.mensagem }, dto.avisos.toString())
            assertTrue(dto.eficienciaFluxo != null && dto.porStatus.isNotEmpty(), "o card não pode zerar")
            assertFalse("TG-20" in drill(usuario, "analisadas").map { it.key })
        }
        assertEquals(6, card("gerente.exemplo").analisadas)
        // Quem não tem posse da issue não recebe o aviso (nem sabe que ela existe).
        assertTrue(card("carla").avisos.none { it.codigo == "DATAS_INCONSISTENTES" }, card("carla").avisos.toString())
    }

    @Test
    fun taskQueMudouDeSquadNaoExpoeOColegaDoOutroSquad() = rodar {
        // Da ana (Alfa) para a carla (Beta).
        issue(
            "TG-12", "Checklist","carla", "Concluído", "Done", t("10-01 09:00"), t("10-10 09:00"),
            listOf(
                resp("10-01 09:00", null, "ana"), st("10-01 10:00", "A Fazer", "Em andamento"),
                resp("10-05 09:00", "ana", "carla"), st("10-10 09:00", "Em andamento", "Concluído"),
            ),
        )
        for (lider in listOf("lider.alfa", "ana")) {
            val dto = card(lider)
            assertTrue(dto.pessoaStatus.none { it.person == "carla" }, "$lider ${dto.pessoaStatus}")
            val linha = drill(lider, "analisadas").single { it.key == "TG-12" }
            assertEquals(null, linha.responsavel,"o responsável atual (outro squad) não pode aparecer para $lider")
            assertFalse("carla" in linha.toString(), linha.toString())
            assertEquals(HttpStatusCode.Forbidden, get(lider, "/api/drill/tempo-em-status?kind=${enc("pessoa:carla:status:Em andamento")}").status)
        }
        // O gerente vê a carla normalmente.
        assertTrue(drill("gerente.exemplo", "pessoa:carla:status:Em andamento").any { it.key == "TG-12" })
        // A carla (Beta) vê a task, mas não a ana.
        assertTrue(card("carla").pessoaStatus.none { it.person == "ana" })
    }

    @Test
    fun pessoaEmMaiusculasSeguePossibilidadesDeVisibilidade() = rodar {
        // Mesma regra com a caixa trocada: o usuário comum não vê o colega; o gerente vê, igual a minúsculas.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/cards/tempo-em-status?pessoa=BRUNO").status)
        assertEquals(HttpStatusCode.Forbidden, get("carla", "/api/cards/tempo-em-status?pessoa=Bruno").status)
        assertEquals(card("gerente.exemplo", "?pessoa=bruno"), card("gerente.exemplo", "?pessoa=BRUNO"))
        assertTrue(card("gerente.exemplo", "?pessoa=BRUNO").pessoaStatus.all { it.person == "bruno" })
        // O líder do Alfa vê o bruno em maiúsculas; o líder não vê a carla.
        assertTrue(card("lider.alfa", "?pessoa=BRUNO").analisadas > 0)
        assertEquals(HttpStatusCode.Forbidden, get("lider.alfa", "/api/cards/tempo-em-status?pessoa=CARLA").status)
    }

    @Test
    fun drillPorPessoaForaDoSquadDoLider() = rodar {
        val kind = "pessoa:carla:status:Em andamento"
        // Pessoa fora da visibilidade: 403 sem corpo.
        val r = get("lider.alfa", "/api/drill/tempo-em-status?kind=${enc(kind)}")
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals("", r.corpo)
        // Pessoa visível, mas fora do filtro atual (squad Beta): lista vazia.
        assertEquals(HttpStatusCode.OK, get("gerente.exemplo", "/api/drill/tempo-em-status?kind=${enc("pessoa:ana:status:Em andamento")}&squad=Beta").status)
        assertEquals(emptyList(), drill("gerente.exemplo", "pessoa:ana:status:Em andamento", "&squad=Beta"))
        // O usuário comum não vê o colega.
        assertEquals(HttpStatusCode.Forbidden, get("ana", "/api/drill/tempo-em-status?kind=${enc("pessoa:bruno:status:Em andamento")}").status)
    }

    @Test
    fun meIgnoraPapeisForjadosEmClaims() {
        // Autenticador que entrega um principal com papel falso (como se viesse numa claim do token).
        val forjado = object : Authenticator {
            override suspend fun authenticate(call: ApplicationCall): Principal? =
                call.request.headers["X-Teste-Usuario"]?.let { Principal(it, setOf(Role.GERENTE, Role.COORDENADOR)) }
        }
        rodar(forjado) {
            val me = Json.decodeFromString<MeDto>(get("ana", "/api/me").corpo)
            assertEquals(MeDto("ana", listOf("USUARIO")), me)
            // Quem não tem papel no diretório não ganha papel por claim: 403.
            assertEquals(HttpStatusCode.Forbidden, get("estranho", "/api/me").status)
        }
    }

    @Test
    fun negadoSemPapelComIdVazioTambemEhRegistrado() = rodar {
        assertEquals(HttpStatusCode.Forbidden, get("   ", "/api/cards/tempo-em-status?squad=Alfa").status)
        val linha = linhasDoAccessLog().single()
        assertEquals("(sem-id)", linha.first)
        assertTrue("403" in linha.third, linha.third)
    }

    @Test
    fun semLoginRetorna401() = rodar {
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/cards/tempo-em-status").status)
        assertEquals(HttpStatusCode.Unauthorized, get(null, "/api/drill/tempo-em-status?kind=analisadas").status)
    }
}
