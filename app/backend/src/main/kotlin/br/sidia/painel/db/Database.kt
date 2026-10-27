package br.sidia.painel.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

/**
 * Acesso ao SQLite embutido (um arquivo local, sem servidor): pool de conexões e migrações.
 *
 * Cada conexão nasce com: WAL (leitores não bloqueiam o gravador), `busy_timeout` (espera o lock em vez de
 * falhar), chaves estrangeiras ligadas, transações de escrita já como IMMEDIATE (sem deadlock de upgrade de lock)
 * e `synchronous=NORMAL` (seguro em WAL). Instantes são gravados como milissegundos desde 1970 (INTEGER) e datas
 * civis como texto ISO (`aaaa-mm-dd`).
 */
object Database {
    /** `jdbc:sqlite:` para um arquivo; cria a pasta se faltar. */
    fun urlDeArquivo(arquivo: Path): String {
        arquivo.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        return "jdbc:sqlite:" + arquivo.toAbsolutePath().toString().replace('\\', '/')
    }

    /** Abre um pool HikariCP. O [DataSource] devolvido é um [HikariDataSource] (fechável). */
    fun connect(url: String, poolSize: Int = 4): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = pragmas(url)
            maximumPoolSize = poolSize
            poolName = "painel-sqlite"
        }
        return HikariDataSource(config)
    }

    /** Acrescenta à URL os ajustes de cada conexão (sqlite-jdbc os lê como propriedades). */
    private fun pragmas(url: String): String {
        val separador = if ('?' in url) "&" else "?"
        return url + separador + listOf(
            "journal_mode=WAL", "busy_timeout=15000", "foreign_keys=true", "synchronous=NORMAL", "transaction_mode=IMMEDIATE",
        ).joinToString("&")
    }

    /** Aplica as migrações Flyway de `db/migration`. */
    fun migrate(ds: DataSource) {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
