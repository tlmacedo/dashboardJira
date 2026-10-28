package br.sidia.painel.auditoria

import br.sidia.painel.db.emJson
import br.sidia.painel.db.idsEmJson
import br.sidia.painel.db.jsonParaTextos
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.sync.ParesTaskReview
import br.sidia.painel.sync.SqliteIssueLinkStore
import br.sidia.painel.sync.VinculosConfig
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** Campos do changelog que a auditoria lê para decidir quais issues entram no período. */
val CAMPOS_AUDITADOS: Set<String> = setOf("assignee", "duedate", "labels", "status", "resolution", "reporter", "timeoriginalestimate")

/**
 * Leitura do banco para a auditoria. Sempre por instância (nunca só pela chave) e sem as issues removidas
 * logicamente (`removida_em`). Nada aqui grava; os achados são calculados na consulta (sem tabela de achados).
 */
interface AuditStore {
    /** Issues com evento de [CAMPOS_AUDITADOS] em `[de, ate)`, cada uma com TODOS os seus eventos (para reconstruir o estado). */
    fun comEventos(instance: String, de: Instant, ate: Instant): List<AuditIssue>

    /** Resolvidas (com `resolution`) cujo vencimento está em `[de, ate]`; sem eventos (o "no prazo" não precisa deles). */
    fun concluidasComVencimento(instance: String, de: LocalDate, ate: LocalDate): List<AuditIssue>

    /** Pares Task × Review pelo `issue_link` (vínculo "Blocks" do 4A, nas duas direções), nunca pelo `parent_key`. */
    fun paresTaskReview(instance: String, vinculos: VinculosConfig): ParesLidos

    fun issue(instance: String, key: String): AuditIssue?
}

/** Implementação sobre SQLite (JDBC). Tudo parametrizado; listas entram como JSON em `json_each(?)`. */
class SqliteAuditStore(private val ds: DataSource) : AuditStore {

    override fun comEventos(instance: String, de: Instant, ate: Instant): List<AuditIssue> =
        ds.connection.use { con ->
            carregar(
                con, instance,
                "EXISTS (SELECT 1 FROM change_event e WHERE e.issue_id = issue.id AND e.at >= ? AND e.at < ? " +
                    "AND lower(e.field) IN (SELECT value FROM json_each(?)))",
                listOf(Timestamp.from(de), Timestamp.from(ate), CAMPOS_AUDITADOS.emJson()),
                comEventos = true,
            )
        }

    override fun concluidasComVencimento(instance: String, de: LocalDate, ate: LocalDate): List<AuditIssue> =
        ds.connection.use { con ->
            carregar(
                con, instance,
                "resolved IS NOT NULL AND resolution IS NOT NULL AND trim(resolution) <> '' " +
                    "AND duedate IS NOT NULL AND duedate >= ? AND duedate <= ?",
                listOf(de.toString(), ate.toString()),
                comEventos = false,
            )
        }

    override fun paresTaskReview(instance: String, vinculos: VinculosConfig): ParesLidos {
        val ativos = SqliteIssueLinkStore(ds).ativos(instance, vinculos.tipoDeLink)
        if (ativos.isEmpty()) return ParesLidos(emptyList(), emptyList(), 0, true)
        val chaves = ativos.flatMap { listOf(it.fromKey, it.toKey) }.toSet()
        return ds.connection.use { con ->
            // As duas pontas precisam estar ativas na instância: o mapa chave -> tipo só tem as ativas.
            val tipoDe = con.prepareStatement(
                "SELECT key, issue_type FROM issue WHERE instance = ? AND removida_em IS NULL " +
                    "AND key IN (SELECT value FROM json_each(?))",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, chaves.emJson())
                ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2).orEmpty()) } }
            }
            val resolucao = ParesTaskReview.resolver(ativos, tipoDe, vinculos)
            val dosPares = resolucao.pares.flatMap { listOf(it.task, it.review) }.toSet()
            val issues = if (dosPares.isEmpty()) {
                emptyList()
            } else {
                carregar(con, instance, "key IN (SELECT value FROM json_each(?))", listOf(dosPares.emJson()), comEventos = true)
            }
            ParesLidos(issues, resolucao.pares, resolucao.ambiguas, semVinculos = false)
        }
    }

    override fun issue(instance: String, key: String): AuditIssue? =
        ds.connection.use { con -> carregar(con, instance, "key = ?", listOf(key), comEventos = true).firstOrNull() }

    /** Linha da issue sem os eventos. */
    private class Linha(
        val id: Long, val key: String, val summary: String, val tipo: String, val pai: String?, val reporter: String?,
        val assignee: String?, val duedate: String?, val created: Instant, val resolved: Instant?, val resolution: String?,
        val status: String, val labels: List<String>,
    )

    private fun carregar(con: Connection, instance: String, filtro: String, params: List<Any>, comEventos: Boolean): List<AuditIssue> {
        val sql = "SELECT id, key, json_extract(raw, '$.fields.summary'), issue_type, parent_key, reporter_ref, assignee_ref, " +
            "duedate, created, resolved, resolution, status, labels FROM issue " +
            "WHERE instance = ? AND removida_em IS NULL AND created IS NOT NULL AND $filtro"
        val linhas = con.prepareStatement(sql).use { ps ->
            ps.setString(1, instance)
            params.forEachIndexed { k, p -> vincular(ps, k + 2, p) }
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            Linha(
                                rs.getLong(1), rs.getString(2), rs.getString(3).orEmpty(), rs.getString(4).orEmpty(), rs.getString(5),
                                rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant(),
                                rs.getTimestamp(10)?.toInstant(), rs.getString(11), rs.getString(12).orEmpty(),
                                jsonParaTextos(rs.getString(13)),
                            ),
                        )
                    }
                }
            }
        }
        if (linhas.isEmpty()) return emptyList()
        val eventos = if (comEventos) eventosDe(con, linhas) else emptyMap()
        return linhas.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key }).map { l ->
            AuditIssue(
                instance, l.key, l.summary, l.tipo, l.pai, l.reporter, l.assignee, Vencimentos.ler(l.duedate), l.created,
                l.resolved, l.resolution, l.status, l.labels, eventos[l.id].orEmpty(),
            )
        }
    }

    private fun vincular(ps: PreparedStatement, posicao: Int, valor: Any) {
        when (valor) {
            is Timestamp -> ps.setTimestamp(posicao, valor)
            is String -> ps.setString(posicao, valor)
            else -> error("tipo de parâmetro não suportado")
        }
    }

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
