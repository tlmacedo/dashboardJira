package br.sidia.painel.sync

import br.sidia.painel.domain.IssueTimeline
import br.sidia.painel.jql.CardSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

/** Persistência das issues e do changelog. */
interface IssueStore {
    /**
     * Grava a issue (atualiza se já existir) e os eventos novos; repetir a chamada não duplica nada.
     * `os_version` sempre é regravado a partir de [osVersionCustomId]: ressincronizar sem customId (nulo)
     * zera o valor existente, por desenho (o valor segue o dicionário atual).
     */
    fun upsert(issue: JsonObject, events: List<ChangeEventRecord>, instance: String, osVersionCustomId: String? = null)

    /**
     * Linhas do tempo da instância (nunca as removidas logicamente). `openOnly=true` devolve as não
     * resolvidas OU sem `resolution` (os limites são ignorados): quem tem status concluído mas não tem
     * `resolution` também vem, para o chamador contar a divergência (spec 4.2). Caso contrário, com
     * [closedFrom] e/ou [closedTo], só as resolvidas em `[closedFrom, closedTo)`; sem nenhum limite, todas.
     * Com [osVersion], só as issues cujo OS Version é igual (sem diferenciar maiúsculas). Com [filtro], só os
     * tipos da ficha e fora dos status excluídos por ela.
     */
    fun timelines(
        instance: String, closedFrom: Instant?, closedTo: Instant?, openOnly: Boolean, osVersion: String? = null,
        filtro: FiltroDeFicha? = null,
    ): List<IssueTimeline>

    /** Se alguma issue da instância tem OS Version gravado (falso quando o campo ainda não é sincronizado). */
    fun temOsVersion(instance: String): Boolean
}

/**
 * O que a ficha do card fixa sobre as issues, aplicado também na LEITURA do banco (a base guarda tudo o que a
 * sincronização trouxe, e o card só pode contar o que a ficha manda). [tipos] vazio = qualquer tipo.
 * O projeto fica de fora: na base ele é a chave (`TG`) e na ficha pode ser o nome; a instância já separa.
 */
data class FiltroDeFicha(val tipos: Set<String>, val statusExcluidos: Set<String>) {
    companion object {
        private const val CAMPO_TIPO = "issuetype"
        private const val CAMPO_STATUS = "status"

        /** Valores de `filtrosFixos[issuetype]` e `filtrosExcluidos[status]` da ficha (chave sem diferenciar maiúsculas). */
        fun de(spec: CardSpec): FiltroDeFicha = FiltroDeFicha(
            tipos = valores(spec.filtrosFixos, CAMPO_TIPO),
            statusExcluidos = valores(spec.filtrosExcluidos, CAMPO_STATUS),
        )

        private fun valores(mapa: Map<String, List<String>>, campo: String): Set<String> =
            mapa.filterKeys { it.equals(campo, ignoreCase = true) }.values.flatten().toSet()
    }
}

/** Linha leve de uma issue ativa (não removida), usada pela reconciliação. */
data class IndiceIssue(val id: Long, val jiraId: Long?, val key: String, val updated: Instant?)

/** Consultas e marcações que a reconciliação diária faz sobre as issues já gravadas. */
interface IssueIndex {
    /** Issues ativas (`removida_em IS NULL`) com `updated >= atualizadasDesde`, em ordem de chave. */
    fun indice(instance: String, atualizadasDesde: Instant): List<IndiceIssue>

    /** Remoção lógica (`removida_em = at`) das issues de [ids] (id interno) ainda ativas; devolve quantas mudaram. */
    fun marcarRemovidas(instance: String, ids: Set<Long>, at: Instant): Int

    /** A issue com este `jira_id` passa a ter [novaChave], mantendo o id interno e o histórico. */
    fun renomear(instance: String, jiraId: Long, novaChave: String)
}

/**
 * Extrai o valor do campo "OS Version" (SELECT do Jira: objeto `{value}` ou texto) do JSON da issue.
 * Espaços são normalizados; texto vazio, campo ausente ou [customId] nulo/inválido resultam em nulo.
 * Valor de outro tipo (array, número, booleano, nulo JSON, objeto sem `value` textual) também vira nulo, em
 * silêncio: um campo multisseleção, por exemplo, ficaria sempre nulo.
 * O valor não é dado pessoal.
 */
internal object OsVersionExtrator {
    private val FORMATO_CF = Regex("""cf\[(\d+)]""")

    /** `cf[123]` e `customfield_123` apontam para a chave `customfield_123` no JSON. */
    fun chaveJson(customId: String?): String? {
        if (customId == null) return null
        FORMATO_CF.matchEntire(customId)?.let { return "customfield_${it.groupValues[1]}" }
        return customId.takeIf { it.startsWith("customfield_") }
    }

    fun extrair(issue: JsonObject, customId: String?): String? {
        val chave = chaveJson(customId) ?: return null
        val campo = (issue["fields"] as? JsonObject)?.get(chave) ?: return null
        val bruto = when (campo) {
            is JsonPrimitive -> campo.takeIf { it.isString }?.content
            is JsonObject -> (campo["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        } ?: return null
        return normalizarOsVersion(bruto).ifEmpty { null }
    }
}

/** Uma ou mais sequências de espaço (inclusive Unicode, como NBSP); definição única do valor gravado e do filtro. */
private val ESPACOS_OS_VERSION = Regex("[\\s\\p{Z}]+")

/** Forma normalizada de um OS Version: sem espaços nas pontas e com os internos colapsados em um só. */
fun normalizarOsVersion(valor: String): String = valor.trim().replace(ESPACOS_OS_VERSION, " ")

/** Implementação sobre PostgreSQL (JDBC). Todas as consultas são parametrizadas. */
class PostgresIssueStore(private val ds: DataSource) : IssueStore, IssueIndex {

    override fun indice(instance: String, atualizadasDesde: Instant): List<IndiceIssue> =
        ds.connection.use { con ->
            con.prepareStatement(
                "SELECT id, jira_id, key, updated FROM issue " +
                    "WHERE instance = ? AND removida_em IS NULL AND updated >= ? ORDER BY key",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setTimestamp(2, Timestamp.from(atualizadasDesde))
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val jiraId = rs.getLong(2).takeUnless { rs.wasNull() }
                            add(IndiceIssue(rs.getLong(1), jiraId, rs.getString(3), rs.getTimestamp(4)?.toInstant()))
                        }
                    }
                }
            }
        }

    override fun marcarRemovidas(instance: String, ids: Set<Long>, at: Instant): Int {
        if (ids.isEmpty()) return 0
        return ds.connection.use { con ->
            con.prepareStatement(
                "UPDATE issue SET removida_em = ? WHERE instance = ? AND id = ANY (?) AND removida_em IS NULL",
            ).use { ps ->
                ps.setTimestamp(1, Timestamp.from(at))
                ps.setString(2, instance)
                ps.setArray(3, con.createArrayOf("bigint", ids.toTypedArray()))
                ps.executeUpdate()
            }
        }
    }

    /** Se a nova chave já pertence a outra linha, não faz nada: a rebusca do upsert resolve pelo `jira_id`. */
    override fun renomear(instance: String, jiraId: Long, novaChave: String) {
        ds.connection.use { con ->
            con.prepareStatement(
                "UPDATE issue SET key = ? WHERE instance = ? AND jira_id = ? AND key <> ? " +
                    "AND NOT EXISTS (SELECT 1 FROM issue o WHERE o.instance = ? AND o.key = ?)",
            ).use { ps ->
                ps.setString(1, novaChave)
                ps.setString(2, instance)
                ps.setLong(3, jiraId)
                ps.setString(4, novaChave)
                ps.setString(5, instance)
                ps.setString(6, novaChave)
                ps.executeUpdate()
            }
        }
    }

    override fun upsert(issue: JsonObject, events: List<ChangeEventRecord>, instance: String, osVersionCustomId: String?) {
        val c = CamposDaIssue.de(issue)
        val osVersion = OsVersionExtrator.extrair(issue, osVersionCustomId)
        // O changelog fica nas linhas de change_event; o raw guarda só a issue, sem dados pessoais.
        val raw = SanitizadorRaw.sanitizar(JsonObject(issue.filterKeys { it != "changelog" })).toString()
        ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                val issueId = gravarIssue(con, c, instance, raw, osVersion)
                gravarEventos(con, issueId, events)
                con.commit()
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
        }
    }

    /**
     * Issue movida ou renomeada (mesmo `jira_id`, chave nova): a linha da chave antiga é marcada como removida e
     * perde o `jira_id` (guarda o histórico), liberando o índice único antes do INSERT da chave nova.
     */
    private fun liberarJiraIdDaChaveAntiga(con: Connection, c: CamposDaIssue, instance: String) {
        val jiraId = c.jiraId ?: return
        con.prepareStatement(
            "UPDATE issue SET removida_em = now(), jira_id = NULL WHERE instance = ? AND jira_id = ? AND key <> ?",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setLong(2, jiraId)
            ps.setString(3, c.key)
            ps.executeUpdate()
        }
    }

    private fun gravarIssue(con: Connection, c: CamposDaIssue, instance: String, raw: String, osVersion: String?): Long {
        liberarJiraIdDaChaveAntiga(con, c, instance)
        val sql = """
            INSERT INTO issue (key, instance, project, issue_type, status, resolution, assignee_ref, reporter_ref,
                               created, updated, resolved, duedate, labels, parent_key, estimate_s, spent_s, raw,
                               os_version, jira_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            ON CONFLICT (instance, key) DO UPDATE SET
                project = EXCLUDED.project, issue_type = EXCLUDED.issue_type,
                status = EXCLUDED.status, resolution = EXCLUDED.resolution, assignee_ref = EXCLUDED.assignee_ref,
                reporter_ref = EXCLUDED.reporter_ref, created = EXCLUDED.created, updated = EXCLUDED.updated,
                resolved = EXCLUDED.resolved, duedate = EXCLUDED.duedate, labels = EXCLUDED.labels,
                parent_key = EXCLUDED.parent_key, estimate_s = EXCLUDED.estimate_s, spent_s = EXCLUDED.spent_s,
                raw = EXCLUDED.raw, os_version = EXCLUDED.os_version,
                jira_id = COALESCE(EXCLUDED.jira_id, issue.jira_id), removida_em = NULL
            RETURNING id
        """.trimIndent()
        con.prepareStatement(sql).use { ps ->
            ps.setString(1, c.key)
            ps.setString(2, instance)
            ps.setString(3, c.project)
            ps.setString(4, c.issueType)
            ps.setString(5, c.status)
            ps.setString(6, c.resolution)
            ps.setString(7, c.assignee)
            ps.setString(8, c.reporter)
            ps.setTimestamp(9, c.created?.let(Timestamp::from))
            ps.setTimestamp(10, c.updated?.let(Timestamp::from))
            ps.setTimestamp(11, c.resolved?.let(Timestamp::from))
            ps.setDate(12, c.duedate?.let { Date.valueOf(LocalDate.parse(it.take(10))) })
            ps.setArray(13, con.createArrayOf("text", c.labels.toTypedArray()))
            ps.setString(14, c.parentKey)
            if (c.estimateS != null) ps.setLong(15, c.estimateS) else ps.setNull(15, java.sql.Types.BIGINT)
            if (c.spentS != null) ps.setLong(16, c.spentS) else ps.setNull(16, java.sql.Types.BIGINT)
            ps.setString(17, raw)
            ps.setString(18, osVersion)
            // Id numérico do Jira (estável mesmo se a chave mudar); ausente ou não numérico fica nulo.
            if (c.jiraId != null) ps.setLong(19, c.jiraId) else ps.setNull(19, java.sql.Types.BIGINT)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "upsert da issue não devolveu o id" }
                return rs.getLong(1)
            }
        }
    }

    private fun gravarEventos(con: Connection, issueId: Long, events: List<ChangeEventRecord>) {
        if (events.isEmpty()) return
        val sql = """
            INSERT INTO change_event (changelog_id, issue_id, field, from_value, to_value, at, author_ref)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (issue_id, changelog_id) DO NOTHING
        """.trimIndent()
        con.prepareStatement(sql).use { ps ->
            for (e in events) {
                ps.setString(1, e.changelogId)
                ps.setLong(2, issueId)
                ps.setString(3, e.field)
                ps.setString(4, e.from)
                ps.setString(5, e.to)
                ps.setTimestamp(6, Timestamp.from(e.at))
                ps.setString(7, e.authorRef)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private class LinhaIssue(
        val id: Long, val key: String, val summary: String, val issueType: String, val status: String,
        val resolution: String?, val assignee: String?, val created: Instant, val resolved: Instant?,
        val labels: List<String>,
    )

    override fun temOsVersion(instance: String): Boolean =
        ds.connection.use { con ->
            con.prepareStatement("SELECT EXISTS (SELECT 1 FROM issue WHERE instance = ? AND removida_em IS NULL AND os_version IS NOT NULL)").use { ps ->
                ps.setString(1, instance)
                ps.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }

    override fun timelines(
        instance: String, closedFrom: Instant?, closedTo: Instant?, openOnly: Boolean, osVersion: String?,
        filtro: FiltroDeFicha?,
    ): List<IssueTimeline> {
        val filtros = StringBuilder("instance = ? AND removida_em IS NULL AND created IS NOT NULL")
        val params = mutableListOf<Any>(instance)
        if (filtro != null && filtro.tipos.isNotEmpty()) {
            filtros.append(" AND lower(coalesce(issue_type, '')) = ANY (?)")
            params.add(minusculas(filtro.tipos))
        }
        if (filtro != null && filtro.statusExcluidos.isNotEmpty()) {
            filtros.append(" AND lower(coalesce(status, '')) <> ALL (?)")
            params.add(minusculas(filtro.statusExcluidos))
        }
        if (osVersion != null) {
            // Comparação exata sem diferenciar maiúsculas; o valor entra só como parâmetro.
            filtros.append(" AND lower(os_version) = lower(?)")
            params += osVersion
        }
        if (openOnly) {
            // Sem resolução também vale: status concluído sem `resolution` é divergência a contar, não a esconder.
            filtros.append(" AND (resolved IS NULL OR resolution IS NULL)")
        } else {
            if (closedFrom != null) {
                filtros.append(" AND resolved >= ?")
                params += Timestamp.from(closedFrom)
            }
            if (closedTo != null) {
                filtros.append(" AND resolved < ?")
                params += Timestamp.from(closedTo)
            }
        }
        val sql = "SELECT id, key, raw->'fields'->>'summary', issue_type, status, resolution, assignee_ref, " +
            "created, resolved, labels FROM issue WHERE $filtros ORDER BY key"
        ds.connection.use { con ->
            val linhas = con.prepareStatement(sql).use { ps ->
                params.forEachIndexed { i, p ->
                    when (p) {
                        is Timestamp -> ps.setTimestamp(i + 1, p)
                        is Array<*> -> ps.setArray(i + 1, con.createArrayOf("text", p))
                        else -> ps.setString(i + 1, p as String)
                    }
                }
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            @Suppress("UNCHECKED_CAST")
                            val labels = (rs.getArray(10)?.array as? Array<String>)?.toList().orEmpty()
                            add(
                                LinhaIssue(
                                    rs.getLong(1), rs.getString(2), rs.getString(3).orEmpty(), rs.getString(4).orEmpty(),
                                    rs.getString(5).orEmpty(), rs.getString(6), rs.getString(7),
                                    rs.getTimestamp(8).toInstant(), rs.getTimestamp(9)?.toInstant(), labels,
                                ),
                            )
                        }
                    }
                }
            }
            if (linhas.isEmpty()) return emptyList()
            val eventos = eventosDe(con, linhas)
            return linhas.map { l ->
                ChangelogParser.montarTimeline(
                    key = l.key, summary = l.summary, issueType = l.issueType, created = l.created,
                    status = l.status, assignee = l.assignee, resolved = l.resolved, resolution = l.resolution,
                    labels = l.labels, events = eventos[l.id].orEmpty(),
                )
            }
        }
    }

    /** Valores em minúsculas (fixas, sem depender do idioma) como array de parâmetro, para comparar sem diferenciar maiúsculas. */
    private fun minusculas(valores: Set<String>): Array<String> =
        valores.map { it.lowercase(java.util.Locale.ROOT) }.toTypedArray()

    private fun eventosDe(con: Connection, linhas: List<LinhaIssue>): Map<Long, List<ChangeEventRecord>> {
        val chaves = linhas.associate { it.id to it.key }
        val sql = "SELECT issue_id, changelog_id, field, from_value, to_value, at, author_ref " +
            "FROM change_event WHERE issue_id = ANY (?) ORDER BY at, id"
        con.prepareStatement(sql).use { ps ->
            ps.setArray(1, con.createArrayOf("bigint", chaves.keys.toTypedArray()))
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
