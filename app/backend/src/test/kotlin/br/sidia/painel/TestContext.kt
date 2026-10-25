package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar

/** Contexto de teste sem banco (`dataSource = null`), com calendário e papéis dos arquivos de exemplo. */
fun testContext(settings: Settings): AppContext = AppContext(
    settings = settings,
    calendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings.zone),
    roles = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
    dataSource = null,
    jira = JiraClientFactory(settings),
)
