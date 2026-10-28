package br.sidia.painel.db

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.carga.Escala
import br.sidia.painel.carga.GeradorSintetico
import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jira.RetryPolicy
import br.sidia.painel.module
import br.sidia.painel.pessoas.Ausencias
import br.sidia.painel.pessoas.Equipes
import br.sidia.painel.security.Authenticator
import br.sidia.painel.security.Principal
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.security.principalFor
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.sync.SqliteIssueStore
import br.sidia.painel.sync.SyncGate
import br.sidia.painel.sync.SyncService
import br.sidia.painel.sync.SqliteSyncStateStore
import br.sidia.painel.testContextComBanco
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Situacao
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * O cache de leituras ligado: reaproveita o dado bruto, mas (1) fim de sincronização e de retenção mostram o dado novo
 * na hora, (2) nunca mistura papéis (o recorte por pessoa vem depois do cache) e (3) com o cache desligado tudo é como antes.
 */
class CacheDeLeiturasIntegracaoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    @AfterEach
    fun desligar() = CacheDeLeituras.desligar()

    private val filtro = FiltroDeFicha(setOf("checklist", "checklist tg", "checklist review"), emptySet())
    private val consulta = ConsultaDeTrabalho("samsung", filtro, Situacao.TODAS)

    private fun banco() = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4).also { Database.migrate(it) }

    private fun contagem(ds: javax.sql.DataSource, sql: String): Long =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) } }

    @Test
    fun desligadoPorPadraoAsLeiturasVeemOBancoNaHora() {
        val ds = banco()
        GeradorSintetico.popular(ds, 5L, 50, 6, 10, 50)
        val store = SqliteWorkStore(ds)
        val antes = store.snapshots(consulta).count { it.status == "Done" }
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("UPDATE issue SET status = 'Done', resolution = 'Done', resolved = created WHERE status <> 'Done'") } }
        assertTrue(store.snapshots(consulta).count { it.status == "Done" } > antes)
    }

    @Test
    fun ligadoReaproveitaAteInvalidarEDepoisVeODadoNovo() {
        val ds = banco()
        GeradorSintetico.popular(ds, 5L, 50, 6, 10, 50)
        CacheDeLeituras.configurar(Duration.ofHours(1))
        val store = SqliteWorkStore(ds)
        val primeira = store.snapshots(consulta)
        assertSame(primeira, store.snapshots(consulta), "a mesma leitura bruta é reaproveitada")
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("UPDATE issue SET status = 'Done', resolution = 'Done', resolved = created WHERE status <> 'Done'") } }
        assertSame(primeira, store.snapshots(consulta), "dentro da validade e sem invalidação, o dado antigo ainda vale")
        CacheDeLeituras.invalidar()
        val nova = store.snapshots(consulta)
        assertTrue(nova.all { it.status == "Done" }, "depois de invalidar, o dado novo")
        // consultas diferentes não se misturam
        assertEquals(0, store.snapshots(consulta.copy(situacao = Situacao.ABERTAS)).size)
        assertEquals(50, store.snapshots(consulta.copy(situacao = Situacao.RESOLVIDAS)).size)
    }

    @Test
    fun doisBancosNaoDividemOCache() {
        val a = banco().also { GeradorSintetico.popular(it, 5L, 20, 4, 10, 20) }
        val b = banco().also { GeradorSintetico.popular(it, 6L, 35, 4, 10, 20) }
        CacheDeLeituras.configurar(Duration.ofHours(1))
        assertEquals(20, SqliteWorkStore(a).snapshots(consulta).size)
        assertEquals(35, SqliteWorkStore(b).snapshots(consulta).size)
        assertEquals(20, SqliteWorkStore(a).snapshots(consulta).size)
    }

    @Test
    fun linhasDoTempoTambemSaoReaproveitadasEInvalidadas() {
        val ds = banco()
        GeradorSintetico.popular(ds, 5L, 40, 6, 10, 40)
        CacheDeLeituras.configurar(Duration.ofHours(1))
        val store = SqliteIssueStore(ds)
        val a = store.timelines("samsung", null, null, false, null, filtro)
        assertSame(a, store.timelines("samsung", null, null, false, null, filtro))
        assertNotEquals(a.size, store.timelines("samsung", null, null, true, null, filtro).size, "aberta e todas são leituras diferentes")
        ds.connection.use { c -> c.createStatement().use { it.executeUpdate("UPDATE issue SET removida_em = 1 WHERE key = 'SINT-1'") } }
        CacheDeLeituras.invalidar()
        assertEquals(a.size - 1, store.timelines("samsung", null, null, false, null, filtro).size)
    }

    @Test
    fun retencaoInvalidaOCache() {
        val ds = banco()
        GeradorSintetico.popular(ds, 5L, 30, 6, 10, 30)
        // uma issue antiga, resolvida e parada, que a retenção vai apagar
        ds.connection.use { c ->
            c.prepareStatement(
                "INSERT INTO issue(key, instance, project, issue_type, status, resolution, created, updated, resolved, labels) " +
                    "VALUES ('SINT-VELHA', 'samsung', 'TG', 'Checklist', 'Done', 'Done', ?, ?, ?, '[]')",
            ).use { ps ->
                val velha = Timestamp.from(Instant.parse("2020-01-01T12:00:00Z"))
                ps.setTimestamp(1, velha); ps.setTimestamp(2, velha); ps.setTimestamp(3, velha)
                ps.executeUpdate()
            }
        }
        CacheDeLeituras.configurar(Duration.ofHours(1))
        val store = SqliteWorkStore(ds)
        assertEquals(31, store.snapshots(consulta).size)
        val calendario = br.sidia.painel.time.BusinessCalendar.fromYaml(
            br.sidia.painel.TestPaths.configDir.resolve("calendario.exemplo.yaml"), ZoneId.of("America/Manaus"),
        )
        Retention(ds, calendario).purge(LocalDate.of(2026, 10, 28), 24)
        assertEquals(30, store.snapshots(consulta).size, "a retenção invalida e o dado apagado some das telas na hora")
    }

    @Test
    fun fimDeSincronizacaoInvalidaOCache() = runBlocking {
        val relogio = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC)
        val settings = Settings(
            jiraLiveEnabled = true,
            jiraInstances = mapOf("samsung" to JiraInstanceSettings("https://jira.exemplo.invalid", JiraAuthKind.BEARER, null, "token-de-teste-123")),
        )
        val issue = """{"id":"901","key":"TG-1","fields":{"summary":"s","project":{"key":"TG"},"issuetype":{"name":"Checklist"},
            "status":{"name":"To Do"},"created":"2026-10-01T09:00:00.000-0400","updated":"2026-10-30T09:00:00.000-0400","labels":[]},
            "changelog":{"total":0,"histories":[]}}"""
        val fabrica = JiraClientFactory(
            settings,
            {
                HttpClient(
                    MockEngine {
                        respond(
                            """{"startAt":0,"maxResults":100,"total":1,"issues":[$issue]}""", HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    },
                )
            },
            RetryPolicy(maxRetries = 0, backoff = { 1.seconds }, delay = { }),
        )
        val ctx = testContextComBanco(settings, banco, clock = relogio, jira = fabrica)
        try {
            val ds = requireNotNull(ctx.dataSource)
            CacheDeLeituras.configurar(Duration.ofHours(1))
            val store = SqliteIssueStore(ds)
            assertEquals(0, store.timelines("samsung", null, null, false, null, null).size) // guarda a leitura vazia
            val servico = SyncService(ctx, store, SqliteSyncStateStore(ds), SyncGate(true, settings.sync), relogio)
            servico.incremental("samsung")
            assertEquals(1, contagem(ds, "SELECT count(*) FROM issue"))
            assertEquals(1, store.timelines("samsung", null, null, false, null, null).size, "a telas leem o dado novo assim que a execução fecha")
        } finally {
            ctx.close()
        }
    }

    // ---------------------------------------------------------------- papéis: o recorte vem depois do cache

    private fun principalDe(roles: RoleDirectory, usuario: String): Principal = principalFor(usuario, roles, LocalDate.of(2026, 10, 28))

    @Test
    fun cadaPapelRecebeSeuProprioNumeroComOCacheLigado() {
        val e = Escala.MINIMA
        val pasta = Files.createTempDirectory("painel-cache-")
        try {
            val (papeis, equipes, ausencias) = GeradorSintetico.arquivosDeEquipe(pasta, e.pessoas)
            val roles = RoleDirectory.fromYaml(papeis)
            val settings = Settings(dashboardDevMode = false)
            val ctx: AppContext = testContextComBanco(
                settings, banco, roles = roles, clock = Clock.fixed(GeradorSintetico.AGORA, ZoneId.of("America/Manaus")),
                equipes = Equipes.fromYaml(equipes, roles), ausencias = Ausencias.fromYaml(ausencias, roles),
            )
            GeradorSintetico.popular(requireNotNull(ctx.dataSource), 7L, e.issues, e.eventosPorIssue, e.pessoas, e.worklogs)
            val autenticador = object : Authenticator {
                override suspend fun authenticate(call: ApplicationCall): Principal? =
                    call.request.headers["X-Teste-Usuario"]?.let { principalDe(roles, it) }
            }
            val usuarios = listOf("gerente.sint", "lider.sint1", "dev.sint001", "dev.sint002")
            val rotas = listOf("/api/cards/abertas", "/api/cards/negligenciadas", "/api/cards/tempo-em-status", "/api/cards/fechadas")
            testApplication {
                application { module(settings, ctx, autenticador) }
                suspend fun corpo(rota: String, usuario: String): Pair<HttpStatusCode, String> =
                    client.get(rota) { header("X-Teste-Usuario", usuario) }.let { it.status to it.bodyAsText() }

                // referência: cache desligado
                val semCache = rotas.flatMap { r -> usuarios.map { u -> (r to u) to corpo(r, u) } }.toMap()
                // cache ligado: a mesma pessoa pede duas vezes (a segunda sai do cache) e depois as outras
                CacheDeLeituras.configurar(Duration.ofHours(1))
                for (r in rotas) {
                    for (u in usuarios + usuarios.reversed()) {
                        assertEquals(semCache.getValue(r to u), corpo(r, u), "$r para $u mudou com o cache ligado")
                    }
                }
                // e o recorte existe de verdade: o gerente vê mais do que quem só vê o próprio
                val gerente = semCache.getValue("/api/cards/abertas" to "gerente.sint").second
                val dev = semCache.getValue("/api/cards/abertas" to "dev.sint001").second
                assertNotEquals(gerente, dev)
                assertFalse(semCache.values.any { it.first.value >= 500 })
            }
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }
}
