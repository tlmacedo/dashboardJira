package br.sidia.painel.sync

import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jql.JqlQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Resultado de uma sincronização: issues gravadas e eventos de changelog processados (já existentes contam). */
data class SyncSummary(val issues: Int, val events: Int)

/**
 * Sincroniza issues e changelog de uma instância do Jira para o banco. Idempotente:
 * rodar de novo não duplica issues nem eventos. Quando o changelog embutido na busca vem
 * truncado (ou ausente), busca o histórico completo da issue.
 */
class Syncer(
    private val client: JiraClient,
    private val store: IssueStore,
    private val instance: String,
) {
    suspend fun run(query: JqlQuery): SyncSummary {
        // A trava do cliente (LiveGuard) falha aqui, na primeira requisição, se o Jira real estiver desligado.
        val issues = client.searchAll(query).toList()
        var eventos = 0
        for (issue in issues) {
            val historicos = if (truncado(issue)) {
                client.fullChangelog(requireNotNull(issue["key"]?.let { (it as? JsonPrimitive)?.content }) { "Issue sem chave" })
            } else {
                embutidos(issue)
            }
            val registros = ChangelogParser.toEvents(issue, historicos)
            withContext(Dispatchers.IO) { store.upsert(issue, registros, instance) }
            eventos += registros.size
        }
        return SyncSummary(issues.size, eventos)
    }

    private fun embutidos(issue: JsonObject): List<JsonObject> =
        ((issue["changelog"] as? JsonObject)?.get("histories") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /** Sem changelog embutido, ou com `total` maior que os históricos recebidos. */
    private fun truncado(issue: JsonObject): Boolean {
        val cl = issue["changelog"] as? JsonObject ?: return true
        val total = (cl["total"] as? JsonPrimitive)?.intOrNull ?: return false
        return total > embutidos(issue).size
    }
}
