package br.sidia.painel.sync

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

enum class RunStatus { EM_ANDAMENTO, OK, FALHA, CANCELADA }

data class RunResumo(
    val issues: Int = 0,
    val eventos: Int = 0,
    val requisicoes: Int = 0,
    val incompletas: Int = 0,
    val usuariosNaoResolvidos: Int = 0,
    val removidas: Int = 0,
)

data class RegistroDeExecucao(
    val id: Long,
    val kind: SyncKind,
    val status: RunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val resumo: RunResumo,
    val codigoErro: String?,
    val httpStatus: Int?,
)

/** Mapa chave interna do Jira (`JIRAUSER10`) para username. */
interface RepositorioDeChaves {
    fun chavesDeUsuario(instance: String): Map<String, String>
    fun gravarChavesDeUsuario(instance: String, mapa: Map<String, String>, at: Instant)
}

/** Estado da sincronização por instância: cursor, execuções, suspensão e trava entre réplicas. */
interface SyncStateStore : RepositorioDeChaves {
    fun cursor(instance: String): Instant?

    /** Monotônico (GREATEST): nunca recua. */
    fun avancarCursor(instance: String, novo: Instant)
    fun iniciar(instance: String, kind: SyncKind, at: Instant): Long
    /** Só conclui execução ainda EM_ANDAMENTO; devolve false (sem sobrescrever) se ela já foi fechada. */
    fun concluir(
        runId: Long,
        status: RunStatus,
        resumo: RunResumo,
        codigoErro: String?,
        httpStatus: Int?,
        at: Instant,
    ): Boolean

    /**
     * FALHAs depois do último OK do mesmo tipo e depois do último reinício; INTERROMPIDA, CANCELADA e
     * EM_ANDAMENTO não contam nem zeram.
     */
    fun falhasSeguidas(instance: String, kind: SyncKind): Int
    fun ultimas(instance: String, limite: Int = 20): List<RegistroDeExecucao>

    /** `instance = null` consulta as execuções sem instância (retenção, gravadas como [INSTANCIA_RETENCAO]). */
    fun ultimaPorTipo(instance: String?): Map<SyncKind, RegistroDeExecucao>
    fun suspender(instance: String, codigo: String, at: Instant)

    /** Código da suspensão ou null. */
    fun suspensa(instance: String): String?

    /** Chamado no início do app: reiniciar equivale a religar; também zera a contagem de falhas seguidas. */
    fun limparSuspensoes(): Int
    fun marcarReconciliacao(instance: String, at: Instant)
    fun ultimaReconciliacao(instance: String): Instant?

    /** `pg_try_advisory_lock` numa conexão dedicada; devolve null se outra execução já detém a trava. */
    suspend fun <T> comTrava(instance: String, bloco: suspend () -> T): T?

    /**
     * Fecha como FALHA/INTERROMPIDA (finished_at = [at]) as execuções EM_ANDAMENTO iniciadas antes de
     * [inicioDoProcesso] cuja trava da instância não está presa por nenhuma sessão (nem de outra réplica).
     */
    fun fecharOrfas(inicioDoProcesso: Instant, at: Instant): Int
}

/** Valor gravado em `sync_run.instance` para execuções sem instância (retenção). */
const val INSTANCIA_RETENCAO = "-"

private val CODIGO_VALIDO = Regex("^[A-Z0-9_]+$")

private fun validarCodigo(codigo: String?) {
    require(codigo == null || CODIGO_VALIDO.matches(codigo)) { "Código de erro inválido: só A-Z, 0-9 e _" }
}

class PostgresSyncStateStore(private val ds: DataSource) : SyncStateStore {

    private fun <T> tx(bloco: (Connection) -> T): T = ds.connection.use { c ->
        c.autoCommit = false
        try {
            bloco(c).also { c.commit() }
        } catch (e: Throwable) {
            c.rollback()
            throw e
        }
    }

    private fun ts(i: Instant) = Timestamp.from(i)

    private fun ResultSet.instantOuNulo(col: String): Instant? = getTimestamp(col)?.toInstant()

    override fun cursor(instance: String): Instant? = tx { c ->
        c.prepareStatement("SELECT watermark FROM sync_state WHERE instance = ?").use { ps ->
            ps.setString(1, instance)
            ps.executeQuery().use { if (it.next()) it.instantOuNulo("watermark") else null }
        }
    }

    override fun avancarCursor(instance: String, novo: Instant) {
        tx { c ->
            c.prepareStatement(
                "INSERT INTO sync_state(instance, watermark) VALUES (?, ?) " +
                    "ON CONFLICT (instance) DO UPDATE SET watermark = GREATEST(sync_state.watermark, EXCLUDED.watermark)",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setTimestamp(2, ts(novo))
                ps.executeUpdate()
            }
        }
    }

    override fun iniciar(instance: String, kind: SyncKind, at: Instant): Long = tx { c ->
        c.prepareStatement(
            "INSERT INTO sync_run(instance, kind, started_at, status) VALUES (?, ?, ?, 'EM_ANDAMENTO') RETURNING id",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setString(2, kind.name)
            ps.setTimestamp(3, ts(at))
            ps.executeQuery().use { it.next(); it.getLong(1) }
        }
    }

    override fun concluir(
        runId: Long,
        status: RunStatus,
        resumo: RunResumo,
        codigoErro: String?,
        httpStatus: Int?,
        at: Instant,
    ): Boolean {
        validarCodigo(codigoErro)
        return tx { c ->
            c.prepareStatement(
                "UPDATE sync_run SET status = ?, finished_at = ?, issues = ?, eventos = ?, requisicoes = ?, " +
                    "incompletas = ?, usuarios_nao_resolvidos = ?, removidas = ?, codigo_erro = ?, http_status = ? " +
                    "WHERE id = ? AND status = 'EM_ANDAMENTO'",
            ).use { ps ->
                ps.setString(1, status.name)
                ps.setTimestamp(2, ts(at))
                ps.setInt(3, resumo.issues)
                ps.setInt(4, resumo.eventos)
                ps.setInt(5, resumo.requisicoes)
                ps.setInt(6, resumo.incompletas)
                ps.setInt(7, resumo.usuariosNaoResolvidos)
                ps.setInt(8, resumo.removidas)
                ps.setObject(9, codigoErro, java.sql.Types.VARCHAR)
                ps.setObject(10, httpStatus, java.sql.Types.INTEGER)
                ps.setLong(11, runId)
                ps.executeUpdate() > 0
            }
        }
    }

    override fun falhasSeguidas(instance: String, kind: SyncKind): Int = tx { c ->
        c.prepareStatement(
            "SELECT count(*) FROM sync_run WHERE instance = ? AND kind = ? AND status = 'FALHA' " +
                "AND codigo_erro IS DISTINCT FROM 'INTERROMPIDA' " +
                // Parar num limite nosso (itens, requisições, tempo) é progresso parcial com o cursor avançando, não falha.
                "AND COALESCE(codigo_erro, '') NOT IN ('TETO_ITENS', 'ORCAMENTO_REQUISICOES', 'TEMPO_ESGOTADO') " +
                "AND id > GREATEST(" +
                "COALESCE((SELECT max(id) FROM sync_run WHERE instance = ? AND kind = ? AND status = 'OK'), 0), " +
                "COALESCE((SELECT falhas_desde_run_id FROM sync_state WHERE instance = ?), 0))",
        ).use { ps ->
            ps.setString(1, instance)
            ps.setString(2, kind.name)
            ps.setString(3, instance)
            ps.setString(4, kind.name)
            ps.setString(5, instance)
            ps.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    private fun ResultSet.registro() = RegistroDeExecucao(
        id = getLong("id"),
        kind = SyncKind.valueOf(getString("kind")),
        status = RunStatus.valueOf(getString("status")),
        startedAt = getTimestamp("started_at").toInstant(),
        finishedAt = instantOuNulo("finished_at"),
        resumo = RunResumo(
            issues = getInt("issues"),
            eventos = getInt("eventos"),
            requisicoes = getInt("requisicoes"),
            incompletas = getInt("incompletas"),
            usuariosNaoResolvidos = getInt("usuarios_nao_resolvidos"),
            removidas = getInt("removidas"),
        ),
        codigoErro = getString("codigo_erro"),
        httpStatus = getInt("http_status").takeUnless { wasNull() },
    )

    override fun ultimas(instance: String, limite: Int): List<RegistroDeExecucao> = tx { c ->
        c.prepareStatement("SELECT * FROM sync_run WHERE instance = ? ORDER BY started_at DESC, id DESC LIMIT ?").use { ps ->
            ps.setString(1, instance)
            ps.setInt(2, limite)
            ps.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.registro() else null }.toList() }
        }
    }

    override fun ultimaPorTipo(instance: String?): Map<SyncKind, RegistroDeExecucao> = tx { c ->
        c.prepareStatement(
            "SELECT DISTINCT ON (kind) * FROM sync_run WHERE instance = ? ORDER BY kind, started_at DESC, id DESC",
        ).use { ps ->
            ps.setString(1, instance ?: INSTANCIA_RETENCAO)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.registro() else null }.associateBy { it.kind }
            }
        }
    }

    override fun suspender(instance: String, codigo: String, at: Instant) {
        validarCodigo(codigo)
        tx { c ->
            c.prepareStatement(
                "INSERT INTO sync_state(instance, suspensa_codigo, suspensa_em) VALUES (?, ?, ?) " +
                    "ON CONFLICT (instance) DO UPDATE SET suspensa_codigo = EXCLUDED.suspensa_codigo, " +
                    "suspensa_em = EXCLUDED.suspensa_em",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setString(2, codigo)
                ps.setTimestamp(3, ts(at))
                ps.executeUpdate()
            }
        }
    }

    override fun suspensa(instance: String): String? = tx { c ->
        c.prepareStatement("SELECT suspensa_codigo FROM sync_state WHERE instance = ?").use { ps ->
            ps.setString(1, instance)
            ps.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    override fun limparSuspensoes(): Int = tx { c ->
        c.createStatement().use {
            // marcador: FALHAs com id até o maior atual deixam de contar (reinício zera a contagem)
            it.executeUpdate(
                "INSERT INTO sync_state(instance, falhas_desde_run_id) SELECT instance, max(id) FROM sync_run " +
                    "GROUP BY instance ON CONFLICT (instance) DO UPDATE SET falhas_desde_run_id = EXCLUDED.falhas_desde_run_id",
            )
            it.executeUpdate(
                "UPDATE sync_state SET suspensa_codigo = NULL, suspensa_em = NULL WHERE suspensa_codigo IS NOT NULL",
            )
        }
    }

    override fun marcarReconciliacao(instance: String, at: Instant) {
        tx { c ->
            c.prepareStatement(
                "INSERT INTO sync_state(instance, ultima_reconciliacao) VALUES (?, ?) " +
                    "ON CONFLICT (instance) DO UPDATE SET ultima_reconciliacao = EXCLUDED.ultima_reconciliacao",
            ).use { ps ->
                ps.setString(1, instance)
                ps.setTimestamp(2, ts(at))
                ps.executeUpdate()
            }
        }
    }

    override fun ultimaReconciliacao(instance: String): Instant? = tx { c ->
        c.prepareStatement("SELECT ultima_reconciliacao FROM sync_state WHERE instance = ?").use { ps ->
            ps.setString(1, instance)
            ps.executeQuery().use { if (it.next()) it.instantOuNulo("ultima_reconciliacao") else null }
        }
    }

    /**
     * A trava vale pela sessão da conexão dedicada: se o processo morrer, o PostgreSQL a solta sozinho.
     * A conexão fica reservada até o fim do bloco.
     */
    override suspend fun <T> comTrava(instance: String, bloco: suspend () -> T): T? {
        val chave = "sync:$instance"
        ds.connection.use { c ->
            c.autoCommit = true
            val obtida = c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))").use { ps ->
                ps.setString(1, chave)
                ps.executeQuery().use { it.next(); it.getBoolean(1) }
            }
            if (!obtida) return null
            var falhaDoBloco: Throwable? = null
            try {
                return bloco()
            } catch (e: Throwable) {
                falhaDoBloco = e
                throw e
            } finally {
                val liberada = try {
                    c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))").use { ps ->
                        ps.setString(1, chave)
                        ps.executeQuery().use { it.next(); it.getBoolean(1) }
                    }
                } catch (e: Exception) {
                    falhaDoBloco?.addSuppressed(e)
                    false
                }
                if (!liberada) descartar(c)
            }
        }
    }

    /** `pg_advisory_unlock`; false se a trava não foi solta (retorno falso ou exceção). */
    private fun soltarTrava(c: Connection, chave: String): Boolean =
        try {
            c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))").use { ps ->
                ps.setString(1, chave)
                ps.executeQuery().use { it.next() && it.getBoolean(1) }
            }
        } catch (e: Exception) {
            false
        }

    /** Conexão com trava possivelmente presa: não volta ao pool (evicção); sem Hikari, fecha de verdade. */
    private fun descartar(c: Connection) {
        runCatching { (ds as? com.zaxxer.hikari.HikariDataSource)?.evictConnection(c) }
        runCatching { c.abort(Runnable::run) }
    }

    /**
     * Órfã = EM_ANDAMENTO iniciada antes do início do processo e sem ninguém segurando a trava da instância.
     * A trava é testada com pg_try_advisory_lock numa sessão própria: se obtém, ninguém a segura (órfã);
     * se não, outra réplica ainda executa e a linha é preservada.
     */
    override fun fecharOrfas(inicioDoProcesso: Instant, at: Instant): Int {
        var fechadas = 0
        ds.connection.use { c ->
            c.autoCommit = true
            val instancias = c.prepareStatement(
                "SELECT DISTINCT instance FROM sync_run WHERE status = 'EM_ANDAMENTO' AND started_at < ?",
            ).use { ps ->
                ps.setTimestamp(1, ts(inicioDoProcesso))
                ps.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
            }
            for (inst in instancias) {
                val chave = "sync:$inst"
                val obtida = c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))").use { ps ->
                    ps.setString(1, chave)
                    ps.executeQuery().use { it.next(); it.getBoolean(1) }
                }
                if (!obtida) continue
                var presa = false
                try {
                    fechadas += c.prepareStatement(
                        "UPDATE sync_run SET status = 'FALHA', codigo_erro = 'INTERROMPIDA', finished_at = ? " +
                            "WHERE instance = ? AND status = 'EM_ANDAMENTO' AND started_at < ?",
                    ).use { ps ->
                        ps.setTimestamp(1, ts(at))
                        ps.setString(2, inst)
                        ps.setTimestamp(3, ts(inicioDoProcesso))
                        ps.executeUpdate()
                    }
                } finally {
                    // Soltar a trava confere o retorno: se falhar, a conexão (e a trava presa nela) é descartada.
                    presa = !soltarTrava(c, chave)
                    if (presa) descartar(c)
                }
                if (presa) break
            }
        }
        return fechadas
    }

    override fun chavesDeUsuario(instance: String): Map<String, String> = tx { c ->
        c.prepareStatement("SELECT user_key, username FROM jira_user_key WHERE instance = ?").use { ps ->
            ps.setString(1, instance)
            ps.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) to rs.getString(2) else null }.toMap()
            }
        }
    }

    override fun gravarChavesDeUsuario(instance: String, mapa: Map<String, String>, at: Instant) {
        if (mapa.isEmpty()) return
        tx { c ->
            c.prepareStatement(
                "INSERT INTO jira_user_key(instance, user_key, username, visto_em) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (instance, user_key) DO UPDATE SET username = EXCLUDED.username, visto_em = EXCLUDED.visto_em",
            ).use { ps ->
                for ((chave, username) in mapa) {
                    ps.setString(1, instance)
                    ps.setString(2, chave)
                    ps.setString(3, username)
                    ps.setTimestamp(4, ts(at))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }
}
