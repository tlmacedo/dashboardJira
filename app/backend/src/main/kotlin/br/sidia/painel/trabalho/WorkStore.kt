package br.sidia.painel.trabalho

import br.sidia.painel.auditoria.Vencimentos
import br.sidia.painel.db.emJson
import br.sidia.painel.db.idsEmJson
import br.sidia.painel.db.jsonParaTextos
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.ChangelogParser
import br.sidia.painel.sync.FiltroDeFicha
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.Locale
import javax.sql.DataSource

/**
 * Leitura do modelo único de issues. Sempre por instância (nunca só pela chave) e sem as issues removidas
 * logicamente (`removida_em`). Nada aqui grava.
 */
interface WorkStore {
    fun snapshots(c: ConsultaDeTrabalho): List<IssueSnapshot>

    /** Issues com evento de algum dos [campos] em `[de, ate)`, cada uma com TODOS os seus eventos. */
    fun comEventoEntre(instance: String, filtro: FiltroDeFicha?, campos: Set<String>, de: Instant, ate: Instant): List<IssueSnapshot>

    /** Alguma issue da instância tem prioridade gravada? Falso na base sincronizada antes dos campos de trabalho. */
    fun temCamposDeTrabalho(instance: String): Boolean

    /** Quem existe na base: responsáveis atuais e os de trocas anteriores (base do escopo de visibilidade). */
    fun pessoas(instance: String): Set<String>
}

/** Implementação sobre SQLite (JDBC). Tudo parametrizado; listas entram como JSON em `json_each(?)`. */
class SqliteWorkStore(private val ds: DataSource) : WorkStore {

    override fun snapshots(c: ConsultaDeTrabalho): List<IssueSnapshot> {
        val filtros = StringBuilder()
        val params = mutableListOf<Any>()
        when (c.situacao) {
            Situacao.ABERTAS -> filtros.append(" AND resolved IS NULL")
            Situacao.RESOLVIDAS -> {
                filtros.append(" AND resolved IS NOT NULL")
                c.resolvidasDe?.let { filtros.append(" AND resolved >= ?"); params += Timestamp.from(it) }
                c.resolvidasAte?.let { filtros.append(" AND resolved < ?"); params += Timestamp.from(it) }
            }
            Situacao.TODAS -> Unit
        }
        if (c.osVersion != null) {
            filtros.append(" AND lower(os_version) = lower(?)")
            params += c.osVersion
        }
        return ds.connection.use { con -> carregar(con, c.instance, c.filtro, filtros.toString(), params) }
    }

    override fun comEventoEntre(
        instance: String, filtro: FiltroDeFicha?, campos: Set<String>, de: Instant, ate: Instant,
    ): List<IssueSnapshot> {
        if (campos.isEmpty()) return emptyList()
        val filtros = " AND EXISTS (SELECT 1 FROM change_event e WHERE e.issue_id = issue.id AND e.at >= ? AND e.at < ? " +
            "AND lower(e.field) IN (SELECT value FROM json_each(?)))"
        val params = listOf<Any>(Timestamp.from(de), Timestamp.from(ate), campos.map { it.lowercase(Locale.ROOT) }.emJson())
        return ds.connection.use { con -> carregar(con, instance, filtro, filtros, params) }
    }

    override fun temCamposDeTrabalho(instance: String): Boolean =
        ds.connection.use { con ->
            con.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM issue WHERE instance = ? AND removida_em IS NULL AND priority IS NOT NULL)",
            ).use { ps ->
                ps.setString(1, instance)
                ps.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }

    override fun pessoas(instance: String): Set<String> =
        ds.connection.use { con ->
            val sql = """
                SELECT assignee_ref FROM issue WHERE instance = ? AND removida_em IS NULL AND assignee_ref IS NOT NULL
                UNION SELECT e.from_value FROM change_event e JOIN issue i ON i.id = e.issue_id
                    WHERE i.instance = ? AND i.removida_em IS NULL AND lower(e.field) = 'assignee' AND e.from_value IS NOT NULL
                UNION SELECT e.to_value FROM change_event e JOIN issue i ON i.id = e.issue_id
                    WHERE i.instance = ? AND i.removida_em IS NULL AND lower(e.field) = 'assignee' AND e.to_value IS NOT NULL
            """.trimIndent()
            con.prepareStatement(sql).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, instance)
                ps.setString(3, instance)
                ps.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
            }
        }

    /** Linha da issue sem a linha do tempo nem os eventos. */
    private class Linha(
        val id: Long, val key: String, val summary: String, val tipo: String, val prioridade: String?, val status: String,
        val resolution: String?, val created: Instant, val updated: Instant?, val resolved: Instant?, val duedate: String?,
        val assignee: String?, val reporter: String?, val labels: List<String>, val pai: String?, val epico: String?,
        val pontos: Double?, val estimativa: Long?, val restante: Long?, val gasto: Long?, val os: String?,
        val flagged: Boolean, val componentes: List<String>,
    )

    private fun carregar(con: Connection, instance: String, filtro: FiltroDeFicha?, extra: String, extraParams: List<Any>): List<IssueSnapshot> {
        val where = StringBuilder("instance = ? AND removida_em IS NULL AND created IS NOT NULL")
        val params = mutableListOf<Any>(instance)
        if (filtro != null && filtro.tipos.isNotEmpty()) {
            where.append(" AND lower(coalesce(issue_type, '')) IN (SELECT value FROM json_each(?))")
            params += minusculas(filtro.tipos)
        }
        if (filtro != null && filtro.statusExcluidos.isNotEmpty()) {
            where.append(" AND lower(coalesce(status, '')) NOT IN (SELECT value FROM json_each(?))")
            params += minusculas(filtro.statusExcluidos)
        }
        where.append(extra)
        params.addAll(extraParams)
        val sql = "SELECT id, key, json_extract(raw, '$.fields.summary'), issue_type, priority, status, resolution, created, " +
            "updated, resolved, duedate, assignee_ref, reporter_ref, labels, parent_key, epic_key, story_points, estimate_s, " +
            "remaining_s, spent_s, os_version, flagged, components FROM issue WHERE $where"
        val linhas = con.prepareStatement(sql).use { ps ->
            params.forEachIndexed { i, p ->
                when (p) {
                    is Timestamp -> ps.setTimestamp(i + 1, p)
                    else -> ps.setString(i + 1, p as String)
                }
            }
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        fun longo(n: Int) = rs.getLong(n).takeUnless { rs.wasNull() }
                        add(
                            Linha(
                                id = rs.getLong(1), key = rs.getString(2), summary = rs.getString(3).orEmpty(),
                                tipo = rs.getString(4).orEmpty(), prioridade = rs.getString(5), status = rs.getString(6).orEmpty(),
                                resolution = rs.getString(7), created = rs.getTimestamp(8).toInstant(),
                                updated = rs.getTimestamp(9)?.toInstant(), resolved = rs.getTimestamp(10)?.toInstant(),
                                duedate = rs.getString(11), assignee = rs.getString(12), reporter = rs.getString(13),
                                labels = jsonParaTextos(rs.getString(14)), pai = rs.getString(15), epico = rs.getString(16),
                                pontos = rs.getDouble(17).takeUnless { rs.wasNull() }, estimativa = longo(18), restante = longo(19),
                                gasto = longo(20), os = rs.getString(21), flagged = rs.getInt(22) == 1,
                                componentes = jsonParaTextos(rs.getString(23)),
                            ),
                        )
                    }
                }
            }
        }
        if (linhas.isEmpty()) return emptyList()
        val eventos = eventosDe(con, linhas)
        return linhas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key }).map { l ->
            val evs = eventos[l.id].orEmpty()
            IssueSnapshot(
                instance = instance, key = l.key, summary = l.summary, issueType = l.tipo, priority = l.prioridade,
                status = l.status, resolution = l.resolution, created = l.created, updated = l.updated, resolved = l.resolved,
                duedate = Vencimentos.ler(l.duedate), assignee = l.assignee, reporter = l.reporter, labels = l.labels,
                parentKey = l.pai, epicKey = l.epico, storyPoints = l.pontos, estimateS = l.estimativa, remainingS = l.restante,
                spentS = l.gasto, osVersion = l.os, flagged = l.flagged, components = l.componentes,
                timeline = ChangelogParser.montarTimeline(
                    key = l.key, summary = l.summary, issueType = l.tipo, created = l.created, status = l.status,
                    assignee = l.assignee, resolved = l.resolved, resolution = l.resolution, labels = l.labels, events = evs,
                ),
                events = evs,
            )
        }
    }

    /** Valores em minúsculas como JSON de parâmetro (o `lower()` do SQLite só rebaixa ASCII; o resto fica como veio). */
    private fun minusculas(valores: Set<String>): String = valores.map { it.lowercase(Locale.ROOT) }.emJson()

    /** Todos os eventos das issues, em lote, ordenados por `(at, id)`. */
    private fun eventosDe(con: Connection, linhas: List<Linha>): Map<Long, List<ChangeEventRecord>> {
        val chaves = linhas.associate { it.id to it.key }
        val sql = "SELECT issue_id, changelog_id, field, from_value, to_value, at, author_ref FROM change_event " +
            "WHERE issue_id IN (SELECT value FROM json_each(?)) ORDER BY at, id"
        con.prepareStatement(sql).use { ps ->
            ps.setString(1, chaves.keys.idsEmJson())
            ps.executeQuery().use { rs ->
                val saida = HashMap<Long, MutableList<ChangeEventRecord>>()
                while (rs.next()) {
                    val id = rs.getLong(1)
                    saida.getOrPut(id) { mutableListOf() } += ChangeEventRecord(
                        changelogId = rs.getString(2), issueKey = chaves.getValue(id), field = rs.getString(3),
                        from = rs.getString(4), to = rs.getString(5), at = rs.getTimestamp(6).toInstant(),
                        authorRef = rs.getString(7),
                    )
                }
                return saida
            }
        }
    }
}
