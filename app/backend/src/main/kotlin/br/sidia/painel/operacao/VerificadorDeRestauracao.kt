package br.sidia.painel.operacao

import br.sidia.painel.db.Database
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.util.Base64
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/** Resultado da verificação: [tabelas] é nome → linhas da cópia restaurada; [codigoErro] só existe quando [ok] é falso. */
data class ResultadoDaVerificacao(val ok: Boolean, val tabelas: Map<String, Long>, val codigoErro: String?)

/** Registro que não registra nada: para verificar uma cópia quando o banco vivo não existe (recuperação de desastre). */
object SemRegistroDeBackup : BackupRunStore {
    override fun registrar(r: RegistroDeBackup) = Unit
    override fun ultima(tipo: TipoDeBackup, status: StatusDeBackup?): RegistroDeBackup? = null
    override fun porArquivo(nome: String): RegistroDeBackup? = null
}

/**
 * Prova que uma cópia do banco se RESTAURA: copia o arquivo para uma pasta TEMPORÁRIA com o nome `painel.db` (nunca toca o
 * banco vivo nem a pasta de backup), abre sem migrar e confere a integridade, as migrações (nenhuma pendente) e, tabela a
 * tabela, a contagem de linhas e o SHA-256 do conteúdo contra o arquivo da cópia. O temporário (inclusive `-wal` e `-shm`)
 * é apagado mesmo em falha. O desfecho vai para `backup_run` (`RESTAURACAO OK|FALHA`, com o NOME da cópia). A saída só
 * leva nomes de tabela e números: nenhum dado das linhas.
 *
 * Códigos: `COPIA_ALTERADA` (o SHA-256 atual difere do registrado), `INTEGRIDADE_FALHOU`, `MIGRACAO_PENDENTE` (cópia de
 * um esquema mais antigo: o runbook trata a ordem de restaurar e subir o pacote correspondente), `CONTAGEM_DIVERGENTE`,
 * `ERRO_INTERNO`. Bloqueante.
 */
class VerificadorDeRestauracao(
    private val runs: BackupRunStore,
    private val metricas: Metricas?,
    private val clock: Clock,
    private val tmp: () -> Path = { Files.createTempDirectory("painel-restauracao-") },
) {
    fun verificar(backup: Path): ResultadoDaVerificacao {
        val inicio = clock.instant()
        val resultado = try {
            conferir(backup)
        } catch (e: Exception) {
            log.warn("Verificação da restauração falhou ({})", e.javaClass.simpleName)
            ResultadoDaVerificacao(false, emptyMap(), "ERRO_INTERNO")
        }
        val fim = clock.instant()
        try {
            runs.registrar(
                RegistroDeBackup(
                    TipoDeBackup.RESTAURACAO, if (resultado.ok) StatusDeBackup.OK else StatusDeBackup.FALHA, inicio, fim,
                    backup.fileName.toString(), runCatching { Files.size(backup) }.getOrNull(), null, resultado.codigoErro,
                ),
            )
        } catch (e: Exception) {
            log.warn("Não foi possível registrar a verificação da restauração ({})", e.javaClass.simpleName)
        }
        metricas?.restauracao(if (resultado.ok) StatusDeBackup.OK else StatusDeBackup.FALHA)
        if (resultado.ok) log.info("Restauração verificada: {} ({} tabelas)", backup.fileName, resultado.tabelas.size)
        else log.warn("Restauração FALHOU: {} ({})", backup.fileName, resultado.codigoErro)
        return resultado
    }

    @OptIn(ExperimentalPathApi::class)
    private fun conferir(backup: Path): ResultadoDaVerificacao {
        if (!Files.isRegularFile(backup)) return falha("ERRO_INTERNO")
        // 1. O arquivo é o mesmo que foi registrado?
        // Do registro (backup_run) ou, sem banco vivo, do .sha256 ao lado da cópia ("<sha>  <nome>").
        val registrado = runs.porArquivo(backup.fileName.toString())?.sha256
            ?: runCatching {
                Files.readString(backup.resolveSibling(backup.fileName.toString() + ".sha256")).trim().substringBefore(' ')
                    .takeIf { it.length == 64 }
            }.getOrNull()
        if (registrado != null && sha256(backup) != registrado) return falha("COPIA_ALTERADA")

        // 2. Restaura num temporário, sem migrar.
        val pasta = tmp()
        try {
            val restaurado = pasta.resolve("painel.db")
            Files.copy(backup, restaurado)
            // Arquivo que nem abre como banco (cabeçalho ou páginas danificados) é falha de integridade, não erro interno.
            val ds = try {
                Database.connect(Database.urlDeArquivo(restaurado), poolSize = 1)
            } catch (e: Exception) {
                return falha("INTEGRIDADE_FALHOU")
            }
            try {
                // 3. Integridade e migrações.
                val integridade = try {
                    ds.connection.use { c ->
                        c.createStatement().use { st ->
                            st.executeQuery("PRAGMA integrity_check").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
                        }
                    }
                } catch (e: java.sql.SQLException) {
                    return falha("INTEGRIDADE_FALHOU")
                }
                if (integridade != listOf("ok")) return falha("INTEGRIDADE_FALHOU")
                if (Database.migracoesPendentes(ds) != 0) return falha("MIGRACAO_PENDENTE")
                // 4. Tabela a tabela: contagem e SHA-256 do conteúdo, restaurado × cópia.
                val doRestaurado = ds.connection.use { resumoPorTabela(it) }
                val daCopia = DriverManager.getConnection("jdbc:sqlite:${backup.toAbsolutePath().toString().replace('\\', '/')}?open_mode=1")
                    .use { resumoPorTabela(it) }
                if (doRestaurado != daCopia) return falha("CONTAGEM_DIVERGENTE")
                return ResultadoDaVerificacao(true, doRestaurado.mapValues { it.value.linhas }, null)
            } finally {
                (ds as AutoCloseable).close()
            }
        } finally {
            // O temporário some em qualquer desfecho, com os auxiliares do WAL.
            runCatching { pasta.deleteRecursively() }
        }
    }

    private fun falha(codigo: String) = ResultadoDaVerificacao(false, emptyMap(), codigo)

    private data class ResumoDaTabela(val linhas: Long, val sha256: String)

    private fun resumoPorTabela(c: Connection): Map<String, ResumoDaTabela> {
        val tabelas = c.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
        }
        return tabelas.associateWith { tabela ->
            val md = MessageDigest.getInstance("SHA-256")
            var linhas = 0L
            c.createStatement().use { st ->
                val consulta = try {
                    st.executeQuery("SELECT * FROM \"$tabela\" ORDER BY rowid")
                } catch (e: java.sql.SQLException) {
                    st.executeQuery("SELECT * FROM \"$tabela\"") // tabela sem rowid
                }
                consulta.use { rs ->
                    val colunas = rs.metaData.columnCount
                    while (rs.next()) {
                        linhas++
                        for (i in 1..colunas) {
                            val valor = rs.getObject(i)
                            md.update(
                                when (valor) {
                                    null -> "\u0000"
                                    is ByteArray -> Base64.getEncoder().encodeToString(valor)
                                    else -> valor.toString()
                                }.toByteArray(Charsets.UTF_8),
                            )
                            md.update(0x1f.toByte())
                        }
                        md.update(0x1e.toByte())
                    }
                }
            }
            ResumoDaTabela(linhas, md.digest().joinToString("") { "%02x".format(it) })
        }
    }

    private fun sha256(arquivo: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(arquivo).use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val lidos = entrada.read(buffer)
                if (lidos < 0) break
                md.update(buffer, 0, lidos)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(VerificadorDeRestauracao::class.java)
    }
}
