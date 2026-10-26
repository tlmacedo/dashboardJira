package br.sidia.painel.sync

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Leitura dos vínculos entre issues (tabela `issue_link`); nunca cruza instâncias. */
interface IssueLinkStore {
    /** Vínculos ativos (`removida_em IS NULL`) do [tipo] (sem diferenciar maiúsculas) na instância. */
    fun ativos(instance: String, tipo: String): List<VinculoRecord>

    /** Vínculos ativos em que a issue [key] é qualquer das duas pontas. */
    fun daIssue(instance: String, key: String): List<VinculoRecord>
}

/** Implementação sobre PostgreSQL (JDBC). Todas as consultas são parametrizadas. */
class PostgresIssueLinkStore(private val ds: DataSource) : IssueLinkStore {

    override fun ativos(instance: String, tipo: String): List<VinculoRecord> =
        ds.connection.use { con ->
            con.prepareStatement(
                "SELECT from_key, to_key, link_type, direction FROM issue_link " +
                    "WHERE instance = ? AND lower(link_type) = lower(?) AND removida_em IS NULL ORDER BY from_key, to_key, link_type",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, tipo)
                ps.executeQuery().use(::linhas)
            }
        }

    override fun daIssue(instance: String, key: String): List<VinculoRecord> =
        ds.connection.use { con ->
            con.prepareStatement(
                "SELECT from_key, to_key, link_type, direction FROM issue_link " +
                    "WHERE instance = ? AND (from_key = ? OR to_key = ?) AND removida_em IS NULL ORDER BY from_key, to_key, link_type",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, key)
                ps.setString(3, key)
                ps.executeQuery().use(::linhas)
            }
        }

    private fun linhas(rs: ResultSet): List<VinculoRecord> = buildList {
        while (rs.next()) {
            add(VinculoRecord(rs.getString(1), rs.getString(2), rs.getString(3), DirecaoVinculo.valueOf(rs.getString(4))))
        }
    }

    /**
     * A issue [antiga] passou a se chamar [nova] (mesmo `jira_id`): as pontas dos vínculos da instância acompanham,
     * na transação do chamador. Antes, saem os vínculos que colidiriam com um que já existe sob a chave nova ou que
     * virariam um vínculo da issue com ela mesma. Em tipo simétrico, ao fim as pontas voltam à ordem natural
     * (`from_key` menor que `to_key`), sem deixar duas linhas para o mesmo par.
     */
    internal fun renomearPonta(conn: Connection, instance: String, antiga: String, nova: String) {
        conn.prepareStatement(
            "DELETE FROM issue_link a WHERE a.instance = ? AND (" +
                "(a.from_key = ? AND (a.to_key = ? OR EXISTS (SELECT 1 FROM issue_link b WHERE b.instance = a.instance " +
                "AND b.from_key = ? AND b.to_key = a.to_key AND b.link_type = a.link_type))) OR " +
                "(a.to_key = ? AND (a.from_key = ? OR EXISTS (SELECT 1 FROM issue_link b WHERE b.instance = a.instance " +
                "AND b.to_key = ? AND b.from_key = a.from_key AND b.link_type = a.link_type))))",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setString(2, antiga)
            ps.setString(3, nova)
            ps.setString(4, nova)
            ps.setString(5, antiga)
            ps.setString(6, nova)
            ps.setString(7, nova)
            ps.executeUpdate()
        }
        for (coluna in listOf("from_key", "to_key")) {
            // O nome da coluna vem desta lista fixa; os valores entram só como parâmetro.
            conn.prepareStatement("UPDATE issue_link SET $coluna = ? WHERE instance = ? AND $coluna = ?").use { ps ->
                ps.setString(1, nova)
                ps.setString(2, instance)
                ps.setString(3, antiga)
                ps.executeUpdate()
            }
        }
        restaurarOrdemNatural(conn, instance, nova)
    }

    /** A issue [chave] deixou de existir sob esse nome: seus vínculos ativos ganham `removida_em`, na transação do chamador. */
    internal fun marcarRemovidosDaChave(conn: Connection, instance: String, chave: String) {
        conn.prepareStatement(
            "UPDATE issue_link SET removida_em = now() WHERE instance = ? AND removida_em IS NULL AND (from_key = ? OR to_key = ?)",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setString(2, chave)
            ps.setString(3, chave)
            ps.executeUpdate()
        }
    }

    /**
     * Vínculos simétricos com a ponta [chave] fora da ordem natural (`from_key` maior que `to_key`) voltam à ordem natural.
     * Se a linha na ordem certa já existe, a fora de ordem é apagada (e a que ficou volta a ser ativa quando a apagada era).
     */
    private fun restaurarOrdemNatural(conn: Connection, instance: String, chave: String) {
        class Linha(val de: String, val para: String, val tipo: String, val removida: Timestamp?)
        val linhas = conn.prepareStatement(
            "SELECT from_key, to_key, link_type, removida_em FROM issue_link " +
                "WHERE instance = ? AND direction = 'SIMETRICO' AND (from_key = ? OR to_key = ?)",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setString(2, chave)
            ps.setString(3, chave)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(Linha(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4))) }
            }
        }
        for (l in linhas.filter { ORDEM_NATURAL_DE_CHAVES.compare(it.de, it.para) > 0 }) {
            val jaExiste = conn.prepareStatement(
                "SELECT 1 FROM issue_link WHERE instance = ? AND from_key = ? AND to_key = ? AND link_type = ?",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, l.para)
                ps.setString(3, l.de)
                ps.setString(4, l.tipo)
                ps.executeQuery().use { it.next() }
            }
            if (jaExiste) {
                // A fora de ordem sai; se ela estava ativa, a linha certa fica ativa.
                if (l.removida == null) {
                    conn.prepareStatement(
                        "UPDATE issue_link SET removida_em = NULL WHERE instance = ? AND from_key = ? AND to_key = ? AND link_type = ?",
                    ).use { ps ->
                        ps.setString(1, instance)
                        ps.setString(2, l.para)
                        ps.setString(3, l.de)
                        ps.setString(4, l.tipo)
                        ps.executeUpdate()
                    }
                }
                conn.prepareStatement(
                    "DELETE FROM issue_link WHERE instance = ? AND from_key = ? AND to_key = ? AND link_type = ?",
                ).use { ps ->
                    ps.setString(1, instance)
                    ps.setString(2, l.de)
                    ps.setString(3, l.para)
                    ps.setString(4, l.tipo)
                    ps.executeUpdate()
                }
            } else {
                conn.prepareStatement(
                    "UPDATE issue_link SET from_key = ?, to_key = ? WHERE instance = ? AND from_key = ? AND to_key = ? AND link_type = ?",
                ).use { ps ->
                    ps.setString(1, l.para)
                    ps.setString(2, l.de)
                    ps.setString(3, instance)
                    ps.setString(4, l.de)
                    ps.setString(5, l.para)
                    ps.setString(6, l.tipo)
                    ps.executeUpdate()
                }
            }
        }
    }

    /**
     * Grava os vínculos vistos no JSON de [issueKey], na conexão e na transação do chamador (sem commit aqui).
     * Os ativos em que a issue é QUALQUER ponta e que não vieram na lista ganham `removida_em = at` (o link sumiu
     * no Jira); os da lista são inseridos ou reativados. Repetir a chamada só muda `visto_em`.
     */
    internal fun substituir(conn: Connection, instance: String, issueKey: String, vinculos: List<VinculoRecord>, at: Instant) {
        val distintos = vinculos.distinct()
        val quando = Timestamp.from(at)
        conn.prepareStatement(
            "UPDATE issue_link SET removida_em = ? WHERE instance = ? AND removida_em IS NULL " +
                "AND (from_key = ? OR to_key = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM unnest(?::text[], ?::text[], ?::text[]) AS v(f, t, ty) " +
                "WHERE v.f = issue_link.from_key AND v.t = issue_link.to_key AND v.ty = issue_link.link_type)",
        ).use { ps ->
            ps.setTimestamp(1, quando)
            ps.setString(2, instance)
            ps.setString(3, issueKey)
            ps.setString(4, issueKey)
            ps.setArray(5, conn.createArrayOf("text", distintos.map { it.fromKey }.toTypedArray()))
            ps.setArray(6, conn.createArrayOf("text", distintos.map { it.toKey }.toTypedArray()))
            ps.setArray(7, conn.createArrayOf("text", distintos.map { it.tipo }.toTypedArray()))
            ps.executeUpdate()
        }
        if (distintos.isEmpty()) return
        conn.prepareStatement(
            "INSERT INTO issue_link (instance, from_key, to_key, link_type, direction, visto_em) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (instance, from_key, to_key, link_type) DO UPDATE SET " +
                "removida_em = NULL, visto_em = EXCLUDED.visto_em, direction = EXCLUDED.direction",
        ).use { ps ->
            for (v in distintos) {
                ps.setString(1, instance)
                ps.setString(2, v.fromKey)
                ps.setString(3, v.toKey)
                ps.setString(4, v.tipo)
                ps.setString(5, v.direcao.name)
                ps.setTimestamp(6, quando)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }
}
