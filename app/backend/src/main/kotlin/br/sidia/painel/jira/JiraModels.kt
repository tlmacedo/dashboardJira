package br.sidia.painel.jira

import kotlinx.serialization.json.JsonObject

/** Erro devolvido pelo Jira. `details` é um resumo do corpo (até 300 caracteres, sem quebras de linha). */
open class JiraException(val status: Int, message: String, val details: String?) : RuntimeException(message)

/** O total de itens da busca passou do teto de segurança; a mensagem cita só o limite e o total, nunca o JQL. */
class TetoDeItensExcedidoException(val limite: Int, val total: Int) : JiraException(
    0,
    "Busca excedeu o limite de $limite itens (total informado: $total); refine o filtro",
    null,
)

/** Pedido de busca por JQL. `fields` não pode ser vazio. */
data class SearchRequest(
    val jql: String,
    val fields: List<String>,
    val expandChangelog: Boolean = false,
    val startAt: Int = 0,
    val maxResults: Int = 100,
)

/** Uma página de resultados; cada issue fica como JSON bruto. */
data class SearchPage(val total: Int, val startAt: Int, val issues: List<JsonObject>)
