package br.sidia.painel.sync

import br.sidia.painel.db.Retention
import br.sidia.painel.jira.JiraClient
import br.sidia.painel.jql.FieldDictionary
import br.sidia.painel.jql.JqlQuery
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Nome do campo do Jira (no dicionário) com a versão do SO em que a task deve ser executada. */
const val CAMPO_OS_VERSION = "OS Version"

/** Resultado de uma sincronização: issues gravadas e eventos de changelog processados (já existentes contam). */
data class SyncSummary(val issues: Int, val events: Int, val incompletas: Int = 0, val usuariosNaoResolvidos: Int = 0)

/** Issue com data ou usuário malformado; o serviço a registra como `DADO_MALFORMADO` (continua sendo um IllegalStateException). */
class DadoMalformadoException(mensagem: String) : IllegalStateException(mensagem)

/** Issue já gravada (commit feito); entregue ao `aoGravar` do [Syncer]. */
data class IssueGravada(val key: String, val updated: java.time.Instant?)

/**
 * Sincroniza issues e changelog de uma instância do Jira para o banco. Idempotente:
 * rodar de novo não duplica issues nem eventos. Quando o changelog embutido na busca vem
 * truncado (ou ausente), busca o histórico completo da issue.
 *
 * O `os_version` de cada issue vem do customId de "OS Version" em [campos]; sem dicionário ou sem customId
 * ele é gravado nulo, e ressincronizar assim zera o valor já existente (por desenho).
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
    private val chaves: RepositorioDeChaves? = null,
    private val maxItens: Int = 50_000,
) {
    private val osVersionCustomId: String? = campos?.forInstance(instance)
        ?.takeIf { it.has(CAMPO_OS_VERSION) }?.get(CAMPO_OS_VERSION)?.customId

    /**
     * Processa em fluxo: cada issue é gravada na própria transação assim que chega, e só então [aoGravar] é chamado
     * (o chamador pode avançar o cursor com segurança). A próxima página só é pedida depois disso, então uma falha
     * no meio deixa gravado tudo o que veio antes e nada é carregado inteiro em memória.
     */
    suspend fun run(query: JqlQuery, aoGravar: suspend (IssueGravada) -> Unit = {}): SyncSummary {
        val corte = Retention.limite(LocalDate.now(clock.withZone(zone)), retentionMonths, zone)
        // Mapa chave interna -> username: o persistido (execuções anteriores) mais o que cada issue trouxer.
        // Carregado antes da primeira requisição; a trava do cliente (LiveGuard) falha na busca se o Jira estiver desligado.
        val mapa = HashMap<String, String>()
        chaves?.let { r -> mapa.putAll(withContext(Dispatchers.IO) { r.chavesDeUsuario(instance) }) }
        var issues = 0
        var eventos = 0
        var incompletas = 0
        val naoResolvidos = HashSet<String>()
        client.searchAll(query, maxItens = maxItens).collect { issue ->
            val chave = (issue["key"] as? JsonPrimitive)?.content ?: "?"
            val historicos = if (truncado(issue)) {
                client.fullChangelog(requireNotNull((issue["key"] as? JsonPrimitive)?.content) { "Issue sem chave" })
            } else {
                embutidos(issue)
            }
            // O mapa sai do JSON recebido, antes da sanitização do raw (que apaga a `key` do usuário).
            val novas = ChavesDeUsuario.coletar(issue, historicos).filter { (k, v) -> mapa[k] != v }
            if (novas.isNotEmpty()) {
                mapa.putAll(novas)
                chaves?.let { r -> withContext(Dispatchers.IO) { r.gravarChavesDeUsuario(instance, novas, clock.instant()) } }
            }
            // Eventos mais antigos que o corte de retenção não voltam (o Retention os apagaria de novo).
            val registros = try {
                ChangelogParser.toEvents(issue, historicos, mapa).filter { it.at >= corte }
            } catch (e: IllegalArgumentException) {
                throw dadoMalformado(chave)
            }
            val updated = try {
                withContext(Dispatchers.IO) {
                    // Os vínculos saem do JSON recebido (nulo = sem o campo: não mexe nos gravados), na mesma transação da issue.
                    store.upsert(issue, registros, instance, osVersionCustomId, IssueLinks.ler(chave, issue), clock.instant())
                    CamposDaIssue.de(issue).updated
                }
            } catch (e: IllegalArgumentException) {
                throw dadoMalformado(chave)
            } catch (e: java.time.DateTimeException) {
                throw dadoMalformado(chave)
            }
            issues++
            eventos += registros.size
            if (incompleta(issue)) incompletas++
            registros.filter { it.field.equals("assignee", ignoreCase = true) }
                .flatMap { listOfNotNull(it.from, it.to) }
                .filter { ChavesDeUsuario.ehChaveInterna(it.trim()) }
                .mapTo(naoResolvidos) { it.trim().uppercase() }
            aoGravar(IssueGravada(chave, updated))
        }
        return SyncSummary(issues, eventos, incompletas, naoResolvidos.size)
    }

    /** Sem `created` ou sem `summary`: a issue é gravada, mas fica fora das linhas do tempo (que exigem os dois). */
    private fun incompleta(issue: JsonObject): Boolean {
        val f = issue["fields"] as? JsonObject
        return f?.texto("created") == null || f.texto("summary").isNullOrBlank()
    }

    /** Só a chave da issue vai na mensagem: o valor malformado (e a causa) ficam de fora. */
    private fun dadoMalformado(chave: String) =
        DadoMalformadoException("Issue $chave com dado malformado (data ou usuário); sincronização interrompida")

    private fun embutidos(issue: JsonObject): List<JsonObject> =
        ((issue["changelog"] as? JsonObject)?.get("histories") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /** Sem changelog embutido, ou com `total` maior que os históricos recebidos. */
    private fun truncado(issue: JsonObject): Boolean {
        val cl = issue["changelog"] as? JsonObject ?: return true
        val total = (cl["total"] as? JsonPrimitive)?.intOrNull ?: return false
        return total > embutidos(issue).size
    }
}
