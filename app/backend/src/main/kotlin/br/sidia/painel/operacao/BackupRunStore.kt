package br.sidia.painel.operacao

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

/** Uma linha de `backup_run`. [arquivo] é só o NOME (`painel-aaaa-MM-dd-HHmmss.db`), nunca o caminho. */
data class RegistroDeBackup(
    val tipo: TipoDeBackup,
    val status: StatusDeBackup,
    val inicio: Instant,
    val fim: Instant?,
    val arquivo: String?,
    val tamanhoBytes: Long?,
    val sha256: String?,
    val codigoErro: String?,
)

/** Registro das cópias do banco e dos testes de restauração. */
interface BackupRunStore {
    fun registrar(r: RegistroDeBackup)

    /** A mais recente do [tipo] (e do [status], se informado), ou nula. */
    fun ultima(tipo: TipoDeBackup, status: StatusDeBackup? = null): RegistroDeBackup?

    /** O registro do BACKUP que gerou o arquivo de nome [nome], ou nulo. */
    fun porArquivo(nome: String): RegistroDeBackup?
}

class SqliteBackupRunStore(private val ds: DataSource) : BackupRunStore {
    override fun registrar(r: RegistroDeBackup) {
        require(r.arquivo == null || ('/' !in r.arquivo && '\\' !in r.arquivo)) { "registre só o nome do arquivo, nunca o caminho" }
        require(r.codigoErro == null || Regex("^[A-Z0-9_]+$").matches(r.codigoErro)) { "código de erro inválido" }
        ds.connection.use { c ->
            c.prepareStatement(
                "INSERT INTO backup_run(tipo, started_at, finished_at, status, arquivo, tamanho_bytes, sha256, codigo_erro) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setString(1, r.tipo.name)
                ps.setTimestamp(2, Timestamp.from(r.inicio))
                if (r.fim != null) ps.setTimestamp(3, Timestamp.from(r.fim)) else ps.setNull(3, java.sql.Types.INTEGER)
                ps.setString(4, r.status.name)
                ps.setString(5, r.arquivo)
                if (r.tamanhoBytes != null) ps.setLong(6, r.tamanhoBytes) else ps.setNull(6, java.sql.Types.INTEGER)
                ps.setString(7, r.sha256)
                ps.setString(8, r.codigoErro)
                ps.executeUpdate()
            }
        }
    }

    override fun ultima(tipo: TipoDeBackup, status: StatusDeBackup?): RegistroDeBackup? = ds.connection.use { c ->
        val filtro = if (status != null) " AND status = ?" else ""
        c.prepareStatement("SELECT $COLUNAS FROM backup_run WHERE tipo = ?$filtro ORDER BY started_at DESC, id DESC LIMIT 1").use { ps ->
            ps.setString(1, tipo.name)
            if (status != null) ps.setString(2, status.name)
            ps.executeQuery().use { if (it.next()) ler(it) else null }
        }
    }

    override fun porArquivo(nome: String): RegistroDeBackup? = ds.connection.use { c ->
        c.prepareStatement("SELECT $COLUNAS FROM backup_run WHERE tipo = 'BACKUP' AND arquivo = ? ORDER BY id DESC LIMIT 1").use { ps ->
            ps.setString(1, nome)
            ps.executeQuery().use { if (it.next()) ler(it) else null }
        }
    }

    private fun ler(rs: java.sql.ResultSet) = RegistroDeBackup(
        tipo = TipoDeBackup.valueOf(rs.getString("tipo")),
        status = StatusDeBackup.valueOf(rs.getString("status")),
        inicio = rs.getTimestamp("started_at").toInstant(),
        fim = rs.getTimestamp("finished_at")?.toInstant(),
        arquivo = rs.getString("arquivo"),
        tamanhoBytes = rs.getLong("tamanho_bytes").takeIf { !rs.wasNull() },
        sha256 = rs.getString("sha256"),
        codigoErro = rs.getString("codigo_erro"),
    )

    private companion object {
        const val COLUNAS = "tipo, started_at, finished_at, status, arquivo, tamanho_bytes, sha256, codigo_erro"
    }
}
