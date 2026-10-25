package br.sidia.painel.sync

import br.sidia.painel.db.Retention
import br.sidia.painel.jira.JiraClient
import java.time.LocalDate
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.jql.JqlQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Nome do campo do Jira (no dicionário) com a versão do SO em que a task deve ser executada. */
const val CAMPO_OS_VERSION = "OS Version"

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
    private val retentionMonths: Int = 24,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val zone: java.time.ZoneId = java.time.ZoneId.of("America/Manaus"),
    /** Dicionário de campos; dele sai o customId do "OS Version" da instância (sem ele, o valor fica nulo). */
    campos: FieldDictionary? = null,
) {
    private val osVersionCustomId: String? = campos?.forInstance(instance)
        ?.takeIf { it.has(CAMPO_OS_VERSION) }?.get(CAMPO_OS_VERSION)?.customId

    suspend fun run(query: JqlQuery): SyncSummary {
        // A trava do cliente (LiveGuard) falha aqui, na primeira requisição, se o Jira real estiver desligado.
        val issues = client.searchAll(query).toList()
        val corte = Retention.limite(LocalDate.now(clock.withZone(zone)), retentionMonths, zone)
        var eventos = 0
        for (issue in issues) {
            val historicos = if (truncado(issue)) {
                client.fullChangelog(requireNotNull(issue["key"]?.let { (it as? JsonPrimitive)?.content }) { "Issue sem chave" })
            } else {
                embutidos(issue)
            }
            val chave = (issue["key"] as? JsonPrimitive)?.content ?: "?"
            // Eventos mais antigos que o corte de retenção não voltam (o Retention os apagaria de novo).
            val registros = try {
                ChangelogParser.toEvents(issue, historicos).filter { it.at >= corte }
            } catch (e: IllegalArgumentException) {
                throw dadoMalformado(chave)
            }
            try {
                withContext(Dispatchers.IO) { store.upsert(issue, registros, instance, osVersionCustomId) }
            } catch (e: IllegalArgumentException) {
                throw dadoMalformado(chave)
            } catch (e: java.time.DateTimeException) {
                throw dadoMalformado(chave)
            }
            eventos += registros.size
        }
        return SyncSummary(issues.size, eventos)
    }

    /** Só a chave da issue vai na mensagem: o valor malformado (e a causa) ficam de fora. */
    private fun dadoMalformado(chave: String) =
        IllegalStateException("Issue $chave com dado malformado (data ou usuário); sincronização interrompida")

    private fun embutidos(issue: JsonObject): List<JsonObject> =
        ((issue["changelog"] as? JsonObject)?.get("histories") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /** Sem changelog embutido, ou com `total` maior que os históricos recebidos. */
    private fun truncado(issue: JsonObject): Boolean {
        val cl = issue["changelog"] as? JsonObject ?: return true
        val total = (cl["total"] as? JsonPrimitive)?.intOrNull ?: return false
        return total > embutidos(issue).size
    }
}
