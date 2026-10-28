package br.sidia.painel.operacao

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

/** Backup agendado do SQLite (`BACKUP_*`). Sem [dir], o backup está desligado. */
data class BackupSettings(
    /** `BACKUP_DIR`: pasta das cópias, FORA do repositório e fora da pasta do banco; vazio = backup desligado. */
    val dir: Path? = null,
    /** `BACKUP_HORA` (HH:mm, no fuso do painel). */
    val hora: LocalTime = LocalTime.of(2, 0),
    /** `BACKUP_RETENCAO_COPIAS` (1 a 365): quantas cópias ficam na pasta. */
    val retencaoCopias: Int = 14,
)

sealed interface ResultadoDoBackup {
    data class Ok(val arquivo: Path, val tamanhoBytes: Long, val sha256: String) : ResultadoDoBackup

    /** [codigo]: `PASTA_SEM_ESCRITA`, `DISCO_INSUFICIENTE`, `COPIA_FALHOU`, `INTEGRIDADE_FALHOU` ou `ERRO_INTERNO`. */
    data class Falha(val codigo: String) : ResultadoDoBackup
}

/** Nome exato de uma cópia do painel; só estes arquivos são candidatos à retenção. */
internal val NOME_DE_BACKUP = Regex("^painel-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.db$")

/**
 * Cópia consistente do banco SQLite para uma pasta fora do repositório. Usa `VACUUM INTO` (leitura consistente em WAL, sem
 * segurar transação de escrita) para um arquivo `.parcial`, CONFERE essa cópia (`integrity_check`, `foreign_key_check`,
 * versão do esquema) e só então calcula o SHA-256, ajusta a permissão (600 onde houver POSIX), renomeia de forma atômica
 * para `painel-aaaa-MM-dd-HHmmss.db`, registra em `backup_run` e aplica a retenção por NOME exato. Cópia que não passa na
 * conferência nunca vira `.db` nem `OK`, e nenhum `.parcial` sobra. Nada de caminho, segredo ou dado nos logs: só código,
 * tamanho e nome do arquivo.
 *
 * Bloqueante: chamar em `Dispatchers.IO`.
 */
class ServicoDeBackup(
    private val ds: DataSource,
    private val arquivoDoBanco: Path,
    private val cfg: BackupSettings,
    private val runs: BackupRunStore,
    private val metricas: Metricas?,
    private val clock: Clock,
    private val zone: ZoneId,
    /** Só testes: espaço livre da pasta (padrão: o do volume). */
    private val livreBytes: (Path) -> Long = VerificacaoDeDisco::livreBytes,
    /** Só testes: roda depois da cópia e antes da conferência (permite corromper o `.parcial`). */
    private val aposCopiar: (Path) -> Unit = {},
) {
    fun executar(): ResultadoDoBackup {
        val dir = cfg.dir ?: return Falha("PASTA_SEM_ESCRITA")
        val inicio = clock.instant()
        var parcial: Path? = null
        val resultado: ResultadoDoBackup = try {
            copiarEConferir(dir, inicio) { parcial = it }
        } catch (e: Exception) {
            log.warn("Backup falhou ({})", e.javaClass.simpleName)
            ResultadoDoBackup.Falha("ERRO_INTERNO")
        } finally {
            // Nada de arquivo parcial na pasta de backup, em qualquer desfecho.
            parcial?.let { runCatching { Files.deleteIfExists(it) } }
        }
        registrar(inicio, resultado)
        runCatching { aplicarRetencao(dir) }.onFailure { log.warn("Retenção das cópias falhou ({})", it.javaClass.simpleName) }
        return resultado
    }

    private fun Falha(codigo: String) = ResultadoDoBackup.Falha(codigo)

    private fun copiarEConferir(dir: Path, inicio: Instant, aoCriarParcial: (Path) -> Unit): ResultadoDoBackup {
        try {
            Files.createDirectories(dir)
        } catch (e: Exception) {
            return Falha("PASTA_SEM_ESCRITA")
        }
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) return Falha("PASTA_SEM_ESCRITA")

        val necessario = ((tamanho(arquivoDoBanco) + tamanho(Path.of(arquivoDoBanco.toString() + "-wal"))) * 1.2).toLong()
        if (livreBytes(dir) < necessario) return Falha("DISCO_INSUFICIENTE")

        val nome = "painel-${NOME_FORMATO.format(inicio.atZone(zone))}.db"
        val destino = dir.resolve(nome)
        val temp = dir.resolve("$nome.parcial")
        if (Files.exists(destino)) return Falha("COPIA_FALHOU") // já existe uma cópia deste mesmo segundo
        Files.deleteIfExists(temp)
        aoCriarParcial(temp)

        try {
            ds.connection.use { c ->
                c.autoCommit = true // VACUUM não roda dentro de transação
                c.prepareStatement("VACUUM INTO ?").use { ps ->
                    ps.setString(1, temp.toAbsolutePath().toString())
                    ps.execute()
                }
            }
        } catch (e: Exception) {
            log.warn("Cópia do banco falhou ({})", e.javaClass.simpleName)
            return Falha("COPIA_FALHOU")
        }
        aposCopiar(temp)

        if (!copiaConfere(temp)) return Falha("INTEGRIDADE_FALHOU")

        val sha = sha256(temp)
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"))
        } catch (e: UnsupportedOperationException) {
            // Windows: a proteção é da pasta (icacls), documentada no runbook.
        }
        Files.move(temp, destino, StandardCopyOption.ATOMIC_MOVE)
        log.info("Backup OK ({} bytes) {}", Files.size(destino), nome)
        return ResultadoDoBackup.Ok(destino, Files.size(destino), sha)
    }

    /** `integrity_check` = ok, `foreign_key_check` vazio e a mesma versão de esquema do banco vivo. */
    private fun copiaConfere(copia: Path): Boolean =
        try {
            val versaoViva = ds.connection.use { versaoDoEsquema(it) }
            DriverManager.getConnection("jdbc:sqlite:${copia.toAbsolutePath().toString().replace('\\', '/')}?open_mode=1").use { c ->
                val integridade = c.createStatement().use { st ->
                    st.executeQuery("PRAGMA integrity_check").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
                }
                val chavesEstrangeiras = c.createStatement().use { st ->
                    st.executeQuery("PRAGMA foreign_key_check").use { it.next() }
                }
                integridade == listOf("ok") && !chavesEstrangeiras && versaoDoEsquema(c) == versaoViva
            }
        } catch (e: Exception) {
            false
        }

    private fun versaoDoEsquema(c: java.sql.Connection): String? =
        c.createStatement().use { st ->
            st.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")
                .use { if (it.next()) it.getString(1) else null }
        }

    private fun registrar(inicio: Instant, resultado: ResultadoDoBackup) {
        val fim = clock.instant()
        val registro = when (resultado) {
            is ResultadoDoBackup.Ok -> RegistroDeBackup(
                TipoDeBackup.BACKUP, StatusDeBackup.OK, inicio, fim, resultado.arquivo.fileName.toString(), resultado.tamanhoBytes, resultado.sha256, null,
            )
            is ResultadoDoBackup.Falha -> RegistroDeBackup(
                TipoDeBackup.BACKUP, StatusDeBackup.FALHA, inicio, fim, null, null, null, resultado.codigo,
            )
        }
        try {
            runs.registrar(registro)
        } catch (e: Exception) {
            log.warn("Não foi possível registrar o backup ({})", e.javaClass.simpleName) // só a classe
        }
        metricas?.backup(registro.status, registro.tamanhoBytes, fim)
        if (resultado is ResultadoDoBackup.Falha) log.warn("Backup FALHOU: {}", resultado.codigo)
    }

    /** Mantém as [BackupSettings.retencaoCopias] cópias mais recentes (o nome ordena por data) e o último OK conhecido. */
    private fun aplicarRetencao(dir: Path) {
        val copias = Files.list(dir).use { itens ->
            itens.filter { Files.isRegularFile(it) && NOME_DE_BACKUP.matches(it.fileName.toString()) }
                .map { it.fileName.toString() }.sorted(Comparator.reverseOrder()).toList()
        }
        val ultimoOk = runCatching { runs.ultima(TipoDeBackup.BACKUP, StatusDeBackup.OK)?.arquivo }.getOrNull()
        for (nome in copias.drop(cfg.retencaoCopias)) {
            if (nome == ultimoOk) continue
            Files.deleteIfExists(dir.resolve(nome))
        }
    }

    private fun tamanho(arquivo: Path): Long = if (Files.exists(arquivo)) Files.size(arquivo) else 0

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
        val log = LoggerFactory.getLogger(ServicoDeBackup::class.java)
        val NOME_FORMATO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
