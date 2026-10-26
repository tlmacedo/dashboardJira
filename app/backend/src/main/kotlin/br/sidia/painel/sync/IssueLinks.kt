package br.sidia.painel.sync

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.jql.InvalidJqlValueException
import br.sidia.painel.jql.JqlValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/** `OUTWARD` = "from [type.outward] to"; `SIMETRICO` = tipo cujo `inward` é igual ao `outward` (ex.: "relates to"). */
enum class DirecaoVinculo { OUTWARD, SIMETRICO }

/**
 * Um link do Jira normalizado: SÓ chaves e o `type.name` (nada de resumo, status ou outro dado da outra ponta).
 * [fromKey] é a ponta outward ("blocks"); em tipo simétrico, a menor chave por [ORDEM_NATURAL_DE_CHAVES].
 */
data class VinculoRecord(val fromKey: String, val toKey: String, val tipo: String, val direcao: DirecaoVinculo)

/**
 * Lê `fields.issuelinks` do JSON da issue como o Jira DC devolve. Cada item traz `type {name, inward, outward}` e
 * UMA das pontas: `outwardIssue` = "esta issue [outward] outwardIssue"; `inwardIssue` = "inwardIssue [outward] esta issue".
 * O link visto pela outra ponta vira o mesmo registro. Roda sobre o JSON recebido, antes do [SanitizadorRaw].
 */
object IssueLinks {
    private val log = LoggerFactory.getLogger(IssueLinks::class.java)

    /**
     * `null` = campo `issuelinks` ausente (ou nulo) no JSON: quem chama não mexe nos vínculos gravados.
     * Lista vazia = a issue não tem vínculos. Item malformado (sem `type.name`, sem ponta ou com as duas, chave fora do
     * padrão, vínculo da issue com ela mesma) é ignorado sem exceção; o log leva só a contagem.
     */
    fun ler(issueKey: String, issue: JsonObject): List<VinculoRecord>? {
        val itens = (issue["fields"] as? JsonObject)?.get("issuelinks") as? JsonArray ?: return null
        val saida = LinkedHashSet<VinculoRecord>()
        var ignorados = 0
        for (item in itens) {
            val v = (item as? JsonObject)?.let { converter(issueKey, it) }
            if (v == null) ignorados++ else saida += v
        }
        if (ignorados > 0) log.warn("Vínculos malformados ignorados: {}", ignorados)
        return saida.toList()
    }

    private fun converter(issueKey: String, item: JsonObject): VinculoRecord? {
        val tipoObj = item["type"] as? JsonObject
        val tipo = tipoObj?.texto("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val saindo = (item["outwardIssue"] as? JsonObject)?.texto("key")?.trim()
        val entrando = (item["inwardIssue"] as? JsonObject)?.texto("key")?.trim()
        if ((saindo == null) == (entrando == null)) return null // sem ponta ou com as duas
        val outra = chaveValida(saindo ?: entrando!!) ?: return null
        val propria = chaveValida(issueKey) ?: return null
        if (outra == propria) return null
        // outwardIssue: esta -> outra; inwardIssue: outra -> esta.
        val (de, para) = if (saindo != null) propria to outra else outra to propria
        val inward = tipoObj?.texto("inward")?.trim()
        val outward = tipoObj?.texto("outward")?.trim()
        val simetrico = inward != null && outward != null && inward.equals(outward, ignoreCase = true)
        return if (simetrico) {
            val (a, b) = if (ORDEM_NATURAL_DE_CHAVES.compare(de, para) <= 0) de to para else para to de
            VinculoRecord(a, b, tipo, DirecaoVinculo.SIMETRICO)
        } else {
            VinculoRecord(de, para, tipo, DirecaoVinculo.OUTWARD)
        }
    }

    private fun chaveValida(chave: String): String? =
        try {
            JqlValues.issueKey(chave)
        } catch (e: InvalidJqlValueException) {
            null
        }
}
