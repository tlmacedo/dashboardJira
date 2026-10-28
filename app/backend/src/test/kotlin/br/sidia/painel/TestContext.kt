package br.sidia.painel

import br.sidia.painel.config.Settings
import br.sidia.painel.auditoria.RegrasDeAuditoria
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.sync.VinculosConfig
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

/** Labels de atraso do modelo versionado (`labels_atraso.exemplo.yaml`). */
fun labelsDeAtrasoDeTeste(): LabelsDeAtraso =
    LabelsDeAtraso.fromYaml(TestPaths.configDir.resolve("labels_atraso.exemplo.yaml"))

/** Regras da auditoria do modelo versionado (`auditoria.exemplo.yaml`), com `tiposReview` dos vínculos informados. */
fun regrasDeAuditoriaDeTeste(vinculos: VinculosConfig = vinculosDeTeste()): RegrasDeAuditoria =
    RegrasDeAuditoria.fromYaml(TestPaths.configDir.resolve("auditoria.exemplo.yaml"), vinculos)

/** Fichas versionadas (arquivos `.card.yaml` da pasta cards) por `id`, carregadas contra o dicionário informado. */
fun fichasDeTeste(campos: FieldDictionary = camposDeTeste()): Map<String, CardSpec> =
    Files.list(TestPaths.configDir.resolve("cards")).use { arquivos ->
        arquivos.filter { it.fileName.toString().endsWith(".card.yaml") }.sorted().toList()
            .map { CardSpecs.fromYaml(it, campos) }.associateBy { it.id }
    }

/** Metas do modelo versionado (`metas.exemplo.yaml`). */
fun regrasDeMetasDeTeste(): br.sidia.painel.cards.metas.RegrasDeMetas =
    br.sidia.painel.cards.metas.RegrasDeMetas.fromYaml(TestPaths.configDir.resolve("metas.exemplo.yaml"))

/** Pesos do risco do modelo versionado (`risco.exemplo.yaml`). */
fun regrasDeRiscoDeTeste(): br.sidia.painel.riscos.RegrasDeRisco =
    br.sidia.painel.riscos.RegrasDeRisco.fromYaml(TestPaths.configDir.resolve("risco.exemplo.yaml"))

/** Parâmetros da previsão do modelo versionado (`previsao.exemplo.yaml`). */
fun regrasDePrevisaoDeTeste(): br.sidia.painel.previsao.RegrasDePrevisao =
    br.sidia.painel.previsao.RegrasDePrevisao.fromYaml(TestPaths.configDir.resolve("previsao.exemplo.yaml"))

/** Regras de planejamento do modelo versionado (`planejamento.exemplo.yaml`). */
fun regrasDePlanejamentoDeTeste(): br.sidia.painel.pessoas.RegrasDePlanejamento =
    br.sidia.painel.pessoas.RegrasDePlanejamento.fromYaml(TestPaths.configDir.resolve("planejamento.exemplo.yaml"))

/** Equipes do modelo (`equipes.exemplo.yaml`), conferidas contra os papéis do modelo (`papeis.exemplo.yaml`). */
fun equipesDeTeste(): br.sidia.painel.pessoas.Equipes =
    br.sidia.painel.pessoas.Equipes.fromYaml(
        TestPaths.configDir.resolve("equipes.exemplo.yaml"), RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
    )

/** Ausências do modelo (`ausencias.exemplo.yaml`), conferidas contra os papéis do modelo. */
fun ausenciasDeTeste(): br.sidia.painel.pessoas.Ausencias =
    br.sidia.painel.pessoas.Ausencias.fromYaml(
        TestPaths.configDir.resolve("ausencias.exemplo.yaml"), RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
    )

/** Contexto de teste sem banco (`dataSource = null`), com calendário, papéis, mapa de status, dicionário e fichas dos arquivos de exemplo. */
fun testContext(settings: Settings): AppContext {
    val campos = camposDeTeste()
    val vinculos = vinculosDeTeste()
    return AppContext(
        settings = settings,
        calendar = BusinessCalendar.fromYaml(TestPaths.configDir.resolve("calendario.exemplo.yaml"), settings.zone),
        roles = RoleDirectory.fromYaml(TestPaths.configDir.resolve("papeis.exemplo.yaml")),
        dataSource = null,
        jira = JiraClientFactory(settings),
        statusMap = StatusMap.fromYaml(TestPaths.configDir.resolve("status_map.exemplo.yaml")),
        campos = campos,
        fichas = fichasDeTeste(campos),
        vinculos = vinculos,
        labelsDeAtraso = labelsDeAtrasoDeTeste(),
        regrasAuditoria = regrasDeAuditoriaDeTeste(vinculos),
        regrasDeMetas = regrasDeMetasDeTeste(),
        regrasDeRisco = regrasDeRiscoDeTeste(),
        regrasDePrevisao = regrasDePrevisaoDeTeste(),
        equipes = equipesDeTeste(),
        ausencias = ausenciasDeTeste(),
        regrasPlanejamento = regrasDePlanejamentoDeTeste(),
    )
}
