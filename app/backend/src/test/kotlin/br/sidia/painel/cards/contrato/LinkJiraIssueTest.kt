package br.sidia.painel.cards.contrato

import br.sidia.painel.config.JiraInstanceSettings
import br.sidia.painel.config.Settings
import br.sidia.painel.jira.JiraAuthKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Link direto para uma issue (`/browse/CHAVE`): mesmas condições do link da gaveta, só texto de URL. */
class LinkJiraIssueTest {
    private val base = "https://jira.exemplo.invalid"

    private fun ligado(url: String = base) = Settings(
        jiraLiveEnabled = true,
        jiraInstances = mapOf("samsung" to JiraInstanceSettings(url, JiraAuthKind.BEARER, null, "token-ficticio-abc123")),
    )

    @Test
    fun montaBrowseComChaveValida() {
        assertEquals("$base/browse/TG-107", LinkJira.paraIssue(ligado(), "samsung", "TG-107"))
        assertEquals("$base/jira/browse/TG-2", LinkJira.paraIssue(ligado("$base/jira/"), "samsung", "TG-2"))
    }

    @Test
    fun chaveForaDoPadraoNaoGeraLink() {
        for (ruim in listOf("tg-1", "TG-1;drop", "TG-", "../etc", "TG-1 OR 1=1", "")) {
            assertNull(LinkJira.paraIssue(ligado(), "samsung", ruim), ruim)
        }
    }

    @Test
    fun semFlagSemInstanciaOuComBaseInsegura() {
        assertNull(LinkJira.paraIssue(Settings(jiraLiveEnabled = false, jiraInstances = ligado().jiraInstances), "samsung", "TG-1"))
        assertNull(LinkJira.paraIssue(ligado(), null, "TG-1"))
        assertNull(LinkJira.paraIssue(ligado(), "sidia", "TG-1"))
        for (insegura in listOf("http://jira.exemplo.invalid", "https://u:s@jira.exemplo.invalid", "https://jira.exemplo.invalid?a=1", "https://jira.exemplo.invalid#x")) {
            assertNull(LinkJira.paraIssue(ligado(insegura), "samsung", "TG-1"), insegura)
        }
    }
}
