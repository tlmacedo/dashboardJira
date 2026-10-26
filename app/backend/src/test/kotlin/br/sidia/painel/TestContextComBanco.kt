package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.db.Database
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock

/**
 * Contexto de teste COM PostgreSQL real (Testcontainers; nunca H2 nem mock). O contêiner é do chamador:
 * cada classe de teste sobe um só (`companion object`) e o para no `@AfterAll`. O schema é recriado e
 * migrado a cada chamada, então cada teste começa com banco limpo. Calendário e papéis vêm dos
 * arquivos de exemplo. Nenhuma requisição ao Jira é aberta.
 */
fun testContextComBanco(
    settings: Settings,
    container: PostgreSQLContainer,
    roles: RoleDirectory = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
    clock: Clock = Clock.system(settings.zone),
    jira: JiraClientFactory = JiraClientFactory(settings),
): AppContext {
    val campos = camposDeTeste()
    val ds = Database.connect(container.jdbcUrl, container.username, container.password, poolSize = 4)
    ds.connection.use { it.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") }
    Database.migrate(ds)
    return AppContext(
        settings = settings,
        calendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings.zone),
        roles = roles,
        dataSource = ds,
        jira = jira,
        statusMap = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml")),
        campos = campos,
        fichas = fichasDeTeste(campos),
        vinculos = vinculosDeTeste(),
        clock = clock,
    )
}

/** Apaga todas as linhas de `access_log` (limpeza entre testes que reaproveitam o contexto). */
fun AppContext.limparAccessLog() {
    requireNotNull(dataSource).connection.use { it.createStatement().execute("DELETE FROM access_log") }
}

/** Quantidade de linhas em `access_log`. */
fun AppContext.contarAccessLog(): Int =
    requireNotNull(dataSource).connection.use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM access_log").use { it.next(); it.getInt(1) }
    }
