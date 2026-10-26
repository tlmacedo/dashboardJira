package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.jira.JiraClientFactory
import br.sidia.painel.jql.CardSpec
import br.sidia.painel.jql.CardSpecs
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.time.BusinessCalendar
import java.nio.file.Files

/** Dicionário de campos do modelo versionado (`jira_campos.exemplo.yaml`), igual ao usado pelos testes de fichas. */
fun camposDeTeste(): FieldDictionary = FieldDictionary.fromYaml(TestPaths.configDir.resolve("jira_campos.exemplo.yaml"))

/** Configuração de vínculos do modelo versionado (`vinculos.exemplo.yaml`). */
fun vinculosDeTeste(): br.sidia.painel.sync.VinculosConfig =
    br.sidia.painel.sync.VinculosConfig.fromYaml(TestPaths.configDir.resolve("vinculos.exemplo.yaml"))

/** Fichas versionadas (arquivos `.card.yaml` da pasta cards) por `id`, carregadas contra o dicionário informado. */
fun fichasDeTeste(campos: FieldDictionary = camposDeTeste()): Map<String, CardSpec> =
    Files.list(TestPaths.configDir.resolve("cards")).use { arquivos ->
        arquivos.filter { it.fileName.toString().endsWith(".card.yaml") }.sorted().toList()
            .map { CardSpecs.fromYaml(it, campos) }.associateBy { it.id }
    }

/** Contexto de teste sem banco (`dataSource = null`), com calendário, papéis, mapa de status, dicionário e fichas dos arquivos de exemplo. */
fun testContext(settings: Settings): AppContext {
    val campos = camposDeTeste()
    return AppContext(
        settings = settings,
        calendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings.zone),
        roles = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
        dataSource = null,
        jira = JiraClientFactory(settings),
        statusMap = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml")),
        campos = campos,
        fichas = fichasDeTeste(campos),
        vinculos = vinculosDeTeste(),
    )
}
