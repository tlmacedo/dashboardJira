package br.sidia.painel.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/** Acesso ao PostgreSQL: pool de conexões e migrações. URL, usuário e senha vêm sempre de fora. */
object Database {
    /** Abre um pool HikariCP. O [DataSource] devolvido é um [HikariDataSource] (fechável). */
    fun connect(url: String, user: String?, password: String?, poolSize: Int = 10): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = url
            if (user != null) username = user
            if (password != null) this.password = password
            maximumPoolSize = poolSize
        }
        return HikariDataSource(config)
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
