package br.sidia.painel.lgpd

import br.sidia.painel.AppContext
import br.sidia.painel.BancoEmArquivo
import br.sidia.painel.auditoria.CodigoSinal
import br.sidia.painel.config.Settings
import br.sidia.painel.http.ErroDto
import br.sidia.painel.integridade.SinalIntegridade
import br.sidia.painel.trabalho.RespTrb
import br.sidia.painel.trabalho.novoContextoTrb1
import br.sidia.painel.trabalho.rodarTrb1
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import java.sql.Timestamp
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /api/titular/meus-dados`: o titular consulta os PRÓPRIOS dados, em contagens, papéis, categorias e acessos, sem o conteúdo de
 * sinais. Nenhum parâmetro escolhe outra pessoa. SQLite real; nenhuma rede.
 */
class TitularRoutesTest {
    companion object {
        private val banco = BancoEmArquivo()

        @JvmStatic
        @AfterAll
        fun apagar() = banco.apagar()

        private const val ROTA = "/api/titular/meus-dados"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private var ctx: AppContext? = null

    @AfterTest
    fun encerrar() {
        ctx?.close()
    }

    private fun exec(sql: String, vararg params: Any?) = requireNotNull(ctx!!.dataSource).connection.use { c ->
        c.prepareStatement(sql).use { ps ->
            params.forEachIndexed { n, p ->
                when (p) {
                    is Timestamp -> ps.setTimestamp(n + 1, p)
                    is Number -> ps.setLong(n + 1, p.toLong())
                    else -> ps.setString(n + 1, p as String?)
                }
            }
            ps.executeUpdate()
        }
    }

    private fun quando(dias: Long): Timestamp = Timestamp.from(ctx!!.clock.instant().minus(Duration.ofDays(dias)))

    /** Dados do cenário: ana tem 2 issues, é Reporter de 1, autora de 3 alterações, 2 registros de horas (2,5 h) e 2 decisões. */
    private fun semear() {
        exec("INSERT INTO issue(id, key, instance, project, assignee_ref, reporter_ref, created) VALUES (1, 'TG-1', 'samsung', 'TG', 'ana', 'lider.alfa', 1)")
        exec("INSERT INTO issue(id, key, instance, project, assignee_ref, reporter_ref, created) VALUES (2, 'TG-2', 'samsung', 'TG', 'ana', 'bruno', 1)")
        exec("INSERT INTO issue(id, key, instance, project, assignee_ref, reporter_ref, created) VALUES (3, 'TG-3', 'samsung', 'TG', 'bruno', 'ana', 1)")
        exec("INSERT INTO issue(id, key, instance, project, assignee_ref, reporter_ref, created, removida_em) VALUES (4, 'TG-4', 'samsung', 'TG', 'ana', 'ana', 1, 5)")
        for (k in 1..3) exec("INSERT INTO change_event(changelog_id, issue_id, field, at, author_ref) VALUES (?, 1, 'status', 1, 'ana')", "c$k")
        exec("INSERT INTO change_event(changelog_id, issue_id, field, at, author_ref) VALUES ('cb', 1, 'status', 1, 'bruno')")
        exec("INSERT INTO worklog(issue_id, author_ref, started, seconds, instance, jira_id) VALUES (1, 'ana', 1, 3600, 'samsung', '1')")
        exec("INSERT INTO worklog(issue_id, author_ref, started, seconds, instance, jira_id) VALUES (2, 'ana', 1, 5400, 'samsung', '2')")
        exec("INSERT INTO worklog(issue_id, author_ref, started, seconds, instance, jira_id) VALUES (3, 'bruno', 1, 7200, 'samsung', '3')")
        fun decisao(id: Int, sugerido: String?, escolhido: String?, por: String) = exec(
            "INSERT INTO decisao_distribuicao(id, instance, tipo, issue_key, sugerido, escolhido, decisao, decidido_por, decidido_em, versao_regras, chave_sugestao) " +
                "VALUES (?, 'samsung', 'DISTRIBUICAO', 'TG-1', ?, ?, 'APROVADA', ?, 1, 'v1', ?)",
            id, sugerido, escolhido, por, "k$id",
        )
        decisao(1, "ana", "bruno", "lider.alfa") // ana é a sugerida
        decisao(2, "bruno", "bruno", "ana") // ana decidiu
        decisao(3, "bruno", "davi", "lider.alfa") // não envolve ana
        exec("INSERT INTO access_log(user_id, route, filters, at) VALUES ('ana', '/api/central', '{\"resultado\":\"200\"}', ?)", quando(10))
        exec("INSERT INTO access_log(user_id, route, filters, at) VALUES ('ana', '/api/cartoes/AB-09/tasks', '{\"segmento\":\"abertas\"}', ?)", quando(2))
        exec("INSERT INTO access_log(user_id, route, filters, at) VALUES ('ana', '/api/meu-painel', '{}', ?)", quando(91)) // fora dos 90 dias
        exec("INSERT INTO access_log(user_id, route, filters, at) VALUES ('bruno', '/api/central', '{}', ?)", quando(1))
    }

    private fun rodar(settings: Settings = Settings(dashboardDevMode = false), bloco: suspend (suspend (String?, String) -> RespTrb) -> Unit) {
        val c = novoContextoTrb1(banco, emptyList(), settings).also { ctx = it }
        semear()
        rodarTrb1(c, bloco = bloco)
    }

    private fun dto(r: RespTrb) = json.decodeFromString<MeusDadosDto>(r.corpo)

    @Test
    fun devolveSoOsDadosDoPrincipal() = rodar { get ->
        val r = get("ana", ROTA)
        assertEquals(HttpStatusCode.OK, r.status, r.corpo)
        val d = dto(r)
        assertEquals("ana", d.pessoa)
        assertEquals(2, d.issuesComoResponsavel, "a issue removida do Jira não conta")
        assertEquals(1, d.issuesComoReporter)
        assertEquals(3, d.alteracoesComoAutor)
        assertEquals(2, d.worklogs)
        assertEquals(2.5, d.horasRegistradas, 1e-9)
        assertEquals(2, d.decisoesEnvolvendo)
        assertEquals(0, d.ausenciasPlanejadas)
        assertEquals("SOLICITACOES_PELO_CANAL_DO_DPO", d.aviso)
        assertTrue(d.geradoEm.startsWith("2026-10-30"), d.geradoEm)
        assertEquals(listOf("USUARIO"), d.papeis.map { it.papel })
        assertEquals("Alfa", d.papeis.single().squad)
        assertEquals(mapOf("historico" to 24, "accessLog" to 24, "decisoes" to 24, "execucoes" to 24), d.retencao)
        // Nenhum id de outra pessoa em lugar nenhum do JSON.
        for (outra in listOf("bruno", "carla", "lider.alfa", "coord.um", "gerente.exemplo")) assertFalse(outra in r.corpo, "'$outra' no JSON do titular")
    }

    @Test
    fun cadaUmVeOsProprios() = rodar { get ->
        val b = dto(get("bruno", ROTA))
        assertEquals("bruno", b.pessoa)
        assertEquals(1, b.issuesComoResponsavel)
        assertEquals(1, b.worklogs)
        assertEquals(2.0, b.horasRegistradas, 1e-9)
        assertEquals(3, b.decisoesEnvolvendo)
        assertEquals(1, b.alteracoesComoAutor)
    }

    @Test
    fun titularNaoEscolheOutraPessoa() = rodar { get ->
        for (consulta in listOf("?pessoa=bruno", "?pessoa=ana", "?user=bruno", "?x=1", "?pessoa=")) {
            val r = get("ana", "$ROTA$consulta")
            assertEquals(HttpStatusCode.BadRequest, r.status, consulta)
            assertEquals(ErroDto("PARAMETRO_NAO_ACEITO"), json.decodeFromString<ErroDto>(r.corpo), consulta)
            assertFalse("bruno" in r.corpo)
        }
        // A tentativa fica no access_log, sem o parâmetro nem o alvo.
        val log = requireNotNull(ctx!!.dataSource).connection.use { c ->
            c.createStatement().executeQuery("SELECT route, filters FROM access_log WHERE route = '$ROTA' ORDER BY id").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1) to rs.getString(2).orEmpty()) }
            }
        }
        assertEquals(5, log.size)
        assertTrue(log.all { "\"resultado\":\"400\"" in it.second && "bruno" !in it.second })
    }

    @Test
    fun acessosSoDoProprioUltimos90Dias() = rodar { get ->
        val d = dto(get("ana", ROTA))
        // Mais recente primeiro; só rota e instante, sem filtros; nada de bruno nem do acesso de 91 dias.
        assertEquals(listOf("/api/cartoes/AB-09/tasks", "/api/central"), d.acessos.map { it.rota })
        assertTrue(d.acessos.none { it.rota == "/api/meu-painel" })
        assertFalse("segmento" in get("ana", ROTA).corpo, "os filtros da consulta não voltam")
        // A própria consulta ao titular é registrada (aparece na seguinte).
        assertTrue(get("ana", ROTA).let { dto(it).acessos.any { a -> a.rota == ROTA } })
    }

    @Test
    fun semConteudoDeSinais() = rodar { get ->
        val corpo = get("gerente.exemplo", ROTA).corpo
        for (c in CodigoSinal.entries) assertFalse(c.name in corpo, c.name)
        for (c in SinalIntegridade.entries) assertFalse(c.name in corpo, c.name)
        val d = dto(get("gerente.exemplo", ROTA))
        // Só as categorias tratadas, com finalidade, quem vê e a variável de retenção.
        assertEquals(
            listOf("TEMPO_EM_STATUS", "CARDS_DE_TRABALHO", "CARGA_E_AUSENCIAS", "AUDITORIA", "INTEGRIDADE", "DECISOES"),
            d.categorias.map { it.categoria },
        )
        assertTrue(d.categorias.all { it.finalidade.isNotBlank() && it.quemVe.isNotEmpty() && it.retencaoVariavel.startsWith("RETENTION_") })
        assertEquals(listOf("GERENTE"), d.categorias.first { it.categoria == "INTEGRIDADE" }.quemVe)
    }

    @Test
    fun todoPapelPodeConsultar() = rodar { get ->
        for (u in listOf("ana", "lider.alfa", "coord.um", "gerente.exemplo")) {
            assertEquals(HttpStatusCode.OK, get(u, ROTA).status, u)
            assertEquals(u, dto(get(u, ROTA)).pessoa)
        }
        assertEquals(HttpStatusCode.Unauthorized, get(null, ROTA).status)
    }

    @Test
    fun naoDependeDaTravaDoDpo() = rodar(Settings(dashboardDevMode = false)) { get ->
        assertEquals(HttpStatusCode.OK, get("ana", ROTA).status)
        // Enquanto a Auditoria da mesma pessoa fica fechada.
        assertEquals(HttpStatusCode.Forbidden, get("gerente.exemplo", "/api/integridade").status)
    }

    @Test
    fun semDadosDevolveZeros() = rodar { get ->
        val d = dto(get("carla", ROTA))
        assertEquals("carla", d.pessoa)
        assertEquals(listOf(0, 0, 0, 0, 0, 0), listOf(d.issuesComoResponsavel, d.issuesComoReporter, d.alteracoesComoAutor, d.worklogs, d.ausenciasPlanejadas, d.decisoesEnvolvendo))
        assertEquals(0.0, d.horasRegistradas, 1e-9)
        assertTrue(d.acessos.isEmpty())
    }
}
