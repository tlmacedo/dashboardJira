package br.sidia.painel.carga

import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.TestPaths
import br.sidia.painel.db.Database
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.pessoas.Ausencias
import br.sidia.painel.pessoas.Equipes
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.sync.SqliteIssueStore
import org.junit.jupiter.api.AfterAll
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** O gerador de dados sintéticos: determinístico, fictício, coerente com as regras e no formato da sincronização. */
class GeradorSinteticoTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun fim() = banco.apagar()
    }

    private val e = Escala.MINIMA

    private fun novoBanco(): DataSource = Database.connect(Database.urlDeArquivo(banco.novoArquivo()), poolSize = 4).also { Database.migrate(it) }

    private fun popular(ds: DataSource, semente: Long = 7L) =
        GeradorSintetico.popular(ds, semente, e.issues, e.eventosPorIssue, e.pessoas, e.worklogs)

    private fun escalar(ds: DataSource, sql: String): Long =
        ds.connection.use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) } }

    private fun resumo(ds: DataSource): String = ds.connection.use { c ->
        val md = MessageDigest.getInstance("SHA-256")
        c.createStatement().executeQuery(
            "SELECT key, status, resolution, assignee_ref, created, updated, resolved, duedate, labels FROM issue ORDER BY id",
        ).use { rs -> while (rs.next()) for (i in 1..9) md.update((rs.getString(i) ?: "-").toByteArray()) }
        c.createStatement().executeQuery("SELECT field, from_value, to_value, at FROM change_event ORDER BY id").use { rs ->
            while (rs.next()) for (i in 1..4) md.update((rs.getString(i) ?: "-").toByteArray())
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun contagensBatemComAEscala() {
        val ds = novoBanco()
        val r = popular(ds)
        assertEquals(e.issues, r.issues)
        assertEquals(e.issues.toLong(), escalar(ds, "SELECT count(*) FROM issue"))
        assertEquals(e.pessoas.toLong(), escalar(ds, "SELECT count(*) FROM person"))
        assertEquals(e.worklogs.toLong(), escalar(ds, "SELECT count(*) FROM worklog"))
        // cada issue tem pelo menos eventosPorIssue eventos (a trilha de status mais longa pode passar disso)
        assertTrue(escalar(ds, "SELECT count(*) FROM change_event") >= e.issues.toLong() * e.eventosPorIssue)
        assertEquals(escalar(ds, "SELECT count(*) FROM change_event"), r.eventos.toLong())
        assertEquals(e.issues.toLong(), escalar(ds, "SELECT count(DISTINCT issue_id) FROM change_event"))
    }

    @Test
    fun mesmaSementeMesmoBancoEOutraSementeOutroBanco() {
        val a = novoBanco().also { popular(it, 7L) }
        val b = novoBanco().also { popular(it, 7L) }
        val c = novoBanco().also { popular(it, 8L) }
        assertEquals(resumo(a), resumo(b))
        assertNotEquals(resumo(a), resumo(c))
    }

    @Test
    fun soDadoFicticio() {
        val ds = novoBanco()
        popular(ds)
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM issue WHERE key NOT GLOB 'SINT-[0-9]*'"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM issue WHERE instance <> 'samsung'"))
        assertEquals(
            0L,
            escalar(ds, "SELECT count(*) FROM issue WHERE assignee_ref IS NOT NULL AND assignee_ref NOT GLOB 'dev.sint[0-9][0-9][0-9]'"),
        )
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM person WHERE jira_ref NOT GLOB 'dev.sint[0-9][0-9][0-9]'"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM change_event WHERE author_ref NOT GLOB 'dev.sint[0-9][0-9][0-9]'"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM worklog WHERE author_ref NOT GLOB 'dev.sint[0-9][0-9][0-9]'"))
        // worklog por instância (V4): cada linha tem a instância e um id próprio, sem comentário nem texto livre
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM worklog WHERE instance IS NOT 'samsung' OR jira_id IS NULL"))
        assertEquals(e.worklogs.toLong(), escalar(ds, "SELECT count(DISTINCT jira_id) FROM worklog"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM issue WHERE raw IS NOT NULL"))
    }

    @Test
    fun coerenteComAsRegrasDoPainel() {
        val ds = novoBanco()
        popular(ds)
        val mapa = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml"))
        val statusUsados = ds.connection.use { c ->
            c.createStatement().executeQuery("SELECT DISTINCT status FROM issue").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        assertTrue(statusUsados.size >= 4, "a carga cobre os status do fluxo: $statusUsados")
        for (s in statusUsados) mapa.category(s) // lança se algum status não está no mapa
        // concluída = status done E resolução (a fonte única da regra)
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM issue WHERE status = 'Done' AND (resolution IS NULL OR resolved IS NULL)"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM issue WHERE status <> 'Done' AND (resolution IS NOT NULL OR resolved IS NOT NULL)"))
        // o último evento de status leva ao status atual
        assertEquals(
            0L,
            escalar(
                ds,
                "SELECT count(*) FROM issue i WHERE EXISTS (SELECT 1 FROM change_event e WHERE e.issue_id = i.id AND e.field = 'status') AND " +
                    "i.status <> (SELECT e.to_value FROM change_event e WHERE e.issue_id = i.id AND e.field = 'status' ORDER BY e.at DESC, e.id DESC LIMIT 1)",
            ),
        )
        // nenhum evento antes da criação nem no futuro
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM change_event e JOIN issue i ON i.id = e.issue_id WHERE e.at < i.created"))
        assertEquals(0L, escalar(ds, "SELECT count(*) FROM change_event WHERE at > ${GeradorSintetico.AGORA.toEpochMilli()}"))
        assertTrue(escalar(ds, "SELECT count(*) FROM issue WHERE duedate IS NOT NULL") > 0)
        assertTrue(escalar(ds, "SELECT count(*) FROM issue WHERE labels LIKE '%LATE_SENT%'") > 0)
    }

    @Test
    fun lotesDeSincronizacaoNoFormatoDoUpsert() {
        val ds = novoBanco()
        popular(ds)
        val lotes = GeradorSintetico.lotesDeSincronizacao(11L, 40, eventosPorIssue = 6, pessoas = e.pessoas)
        assertEquals(40, lotes.size)
        assertEquals(lotes.map { it.json.toString() }, GeradorSintetico.lotesDeSincronizacao(11L, 40, eventosPorIssue = 6, pessoas = e.pessoas).map { it.json.toString() })
        val store = SqliteIssueStore(ds)
        val antes = escalar(ds, "SELECT count(*) FROM issue")
        for (l in lotes) {
            store.upsert(l.json, l.eventos, GeradorSintetico.INSTANCIA, GeradorSintetico.CUSTOM_OS_VERSION, null, GeradorSintetico.AGORA, GeradorSintetico.IDS_DE_TRABALHO)
        }
        assertEquals(antes + 40, escalar(ds, "SELECT count(*) FROM issue"))
        val eventos = escalar(ds, "SELECT count(*) FROM change_event")
        // repetir a sincronização não duplica nada
        for (l in lotes) {
            store.upsert(l.json, l.eventos, GeradorSintetico.INSTANCIA, GeradorSintetico.CUSTOM_OS_VERSION, null, GeradorSintetico.AGORA, GeradorSintetico.IDS_DE_TRABALHO)
        }
        assertEquals(antes + 40, escalar(ds, "SELECT count(*) FROM issue"))
        assertEquals(eventos, escalar(ds, "SELECT count(*) FROM change_event"))
        // os campos de trabalho e o OS Version chegam pelo caminho real
        for (coluna in listOf("priority", "os_version", "epic_key", "story_points", "estimate_s")) {
            assertEquals(40L, escalar(ds, "SELECT count(*) FROM issue WHERE key GLOB 'SINT-S[0-9]*' AND $coluna IS NOT NULL"), coluna)
        }
        // prefixos diferentes não se sobrepõem
        val outros = GeradorSintetico.lotesDeSincronizacao(11L, 5, pessoas = e.pessoas, prefixo = "SINT-W")
        assertTrue(outros.none { o -> lotes.any { it.json["key"] == o.json["key"] } })
    }

    @Test
    fun arquivosDeEquipeCarregamNosCarregadoresReais() {
        val pasta = Files.createTempDirectory("painel-sint-")
        try {
            val (papeis, equipes, ausencias) = GeradorSintetico.arquivosDeEquipe(pasta, 60)
            val roles = RoleDirectory.fromYaml(papeis)
            val hoje = LocalDate.of(2026, 10, 28)
            assertEquals(setOf(Role.GERENTE), roles.rolesOf("gerente.sint", hoje))
            assertEquals(setOf(Role.USUARIO), roles.rolesOf("dev.sint001", hoje))
            assertEquals(setOf(Role.USUARIO, Role.SQUAD_LEADER), roles.rolesOf("lider.sint3", hoje))
            assertEquals(6, (1..60).map { roles.squadOf("dev.sint%03d".format(it), hoje) }.toSet().size)
            Equipes.fromYaml(equipes, roles)
            Ausencias.fromYaml(ausencias, roles)
        } finally {
            pasta.toFile().deleteRecursively()
        }
    }
}
