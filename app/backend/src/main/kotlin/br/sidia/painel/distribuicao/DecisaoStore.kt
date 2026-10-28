package br.sidia.painel.distribuicao

import br.sidia.painel.db.emJson
import java.security.MessageDigest
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import javax.sql.DataSource

enum class Decisao { APROVADA, TROCADA, REJEITADA }

/** Os dois tipos de sugestão que o painel guarda decisões para: valem como texto na tabela e na API. */
object TipoDeSugestao {
    const val DISTRIBUICAO = "DISTRIBUICAO"
    const val REBALANCEAMENTO = "REBALANCEAMENTO"
    val todos = setOf(DISTRIBUICAO, REBALANCEAMENTO)
}

/**
 * Uma decisão do líder sobre uma sugestão. Para o rebalanceamento, [sugerido] e [escolhido] são o DESTINO sugerido e o escolhido;
 * a origem é quem tem a task hoje. [motivo] é texto livre saneado (obrigatório na troca); nunca vai para o `access_log`.
 */
data class RegistroDecisao(
    val tipo: String,
    val issueKey: String,
    val sugerido: String?,
    val escolhido: String?,
    val decisao: Decisao,
    val motivo: String?,
    val decididoPor: String,
    val decididoEm: Instant,
    val versaoRegras: String,
)

/**
 * O que o painel guarda sobre as decisões (modo sombra: nada vai ao Jira). [registrar] devolve `false` quando a sugestão já foi
 * decidida (mesma chave): é o 409. A [chaveSugestao] é o hash de [ChaveDaSugestao].
 */
interface DecisaoStore {
    fun registrar(instance: String, r: RegistroDecisao, chaveSugestao: String): Boolean

    /** Decisões em `[de, ate)`, da mais recente para a mais antiga, até [limite]; [issueKeys] restringe às tasks dadas (nulo = todas). */
    fun listar(instance: String, de: Instant, ate: Instant, issueKeys: Set<String>?, limite: Int = LIMITE_PADRAO): List<RegistroDecisao>

    /** Das [chaves] de sugestão dadas, as que já têm decisão, com o tipo de decisão. */
    fun decididas(instance: String, chaves: Collection<String>): Map<String, Decisao>

    companion object {
        const val LIMITE_PADRAO = 500
    }
}

/**
 * A chave que identifica UMA sugestão num dia: SHA-256 (hexadecimal, minúsculo) de `instância|tipo|task|sugerido|versão|dia`,
 * com `\u001f` entre os campos (um separador que não aparece nos valores) e `-` no lugar do sugerido nulo. O dia é o de Manaus
 * (`LocalDate`), então a mesma sugestão pode ser decidida de novo no dia seguinte, mas não duas vezes no mesmo dia. Estável: não
 * muda entre execuções nem versões da JVM.
 */
object ChaveDaSugestao {
    private const val SEPARADOR = "\u001f"

    fun de(instance: String, tipo: String, issueKey: String, sugerido: String?, versaoRegras: String, dia: LocalDate): String {
        val texto = listOf(instance, tipo, issueKey, sugerido ?: "-", versaoRegras, dia.toString()).joinToString(SEPARADOR)
        return MessageDigest.getInstance("SHA-256").digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Implementação sobre SQLite: tudo parametrizado; a unicidade vem da restrição da tabela. */
class SqliteDecisaoStore(private val ds: DataSource) : DecisaoStore {
    override fun registrar(instance: String, r: RegistroDecisao, chaveSugestao: String): Boolean =
        try {
            ds.connection.use { c ->
                c.prepareStatement(
                    "INSERT INTO decisao_distribuicao(instance, tipo, issue_key, sugerido, escolhido, decisao, motivo, decidido_por, " +
                        "decidido_em, versao_regras, chave_sugestao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ).use { ps ->
                    ps.setString(1, instance)
                    ps.setString(2, r.tipo)
                    ps.setString(3, r.issueKey)
                    ps.setString(4, r.sugerido)
                    ps.setString(5, r.escolhido)
                    ps.setString(6, r.decisao.name)
                    ps.setString(7, r.motivo)
                    ps.setString(8, r.decididoPor)
                    ps.setTimestamp(9, Timestamp.from(r.decididoEm))
                    ps.setString(10, r.versaoRegras)
                    ps.setString(11, chaveSugestao)
                    ps.executeUpdate()
                }
            }
            true
        } catch (e: SQLException) {
            if (violacaoDeUnicidade(e)) false else throw e
        }

    override fun listar(instance: String, de: Instant, ate: Instant, issueKeys: Set<String>?, limite: Int): List<RegistroDecisao> {
        val filtroDeChaves = if (issueKeys != null) " AND issue_key IN (SELECT value FROM json_each(?))" else ""
        return ds.connection.use { c ->
            c.prepareStatement(
                "SELECT tipo, issue_key, sugerido, escolhido, decisao, motivo, decidido_por, decidido_em, versao_regras " +
                    "FROM decisao_distribuicao WHERE instance = ? AND decidido_em >= ? AND decidido_em < ?$filtroDeChaves " +
                    "ORDER BY decidido_em DESC, id DESC LIMIT ?",
            ).use { ps ->
                var i = 1
                ps.setString(i++, instance)
                ps.setTimestamp(i++, Timestamp.from(de))
                ps.setTimestamp(i++, Timestamp.from(ate))
                if (issueKeys != null) ps.setString(i++, issueKeys.toList().emJson())
                ps.setInt(i, limite)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RegistroDecisao(
                                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Decisao.valueOf(rs.getString(5)),
                                    rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getString(9),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun decididas(instance: String, chaves: Collection<String>): Map<String, Decisao> {
        if (chaves.isEmpty()) return emptyMap()
        return ds.connection.use { c ->
            c.prepareStatement(
                "SELECT chave_sugestao, decisao FROM decisao_distribuicao WHERE instance = ? AND chave_sugestao IN (SELECT value FROM json_each(?))",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, chaves.toList().emJson())
                ps.executeQuery().use { rs ->
                    buildMap { while (rs.next()) put(rs.getString(1), Decisao.valueOf(rs.getString(2))) }
                }
            }
        }
    }

    private fun violacaoDeUnicidade(e: SQLException): Boolean {
        val codigo = (e as? org.sqlite.SQLiteException)?.resultCode ?: return false
        return codigo == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE || codigo == org.sqlite.SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY
    }
}
