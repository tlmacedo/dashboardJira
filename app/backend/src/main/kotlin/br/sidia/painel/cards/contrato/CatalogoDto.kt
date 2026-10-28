package br.sidia.painel.cards.contrato

import kotlinx.serialization.Serializable

/**
 * Um cartão do catálogo, como a tela o recebe. Só identificadores e CHAVES de tradução: nenhum texto livre, título
 * de issue nem nome de pessoa passa por aqui.
 */
@Serializable
data class CartaoCatalogoDto(
    val id: String,
    /** Id do grupo do menu (`trabalho`, `resultados`...); o nome é a chave `grupo.<id>`. */
    val grupo: String,
    val natureza: String,
    val tela: String,
    val tituloChave: String,
    val oQueMostraChave: String,
    val comoCalculaChave: String,
    val fontes: List<String>,
    val papeis: List<String>,
    val temDrill: Boolean,
    val exigeChangelog: Boolean,
    val exigeWorklog: Boolean,
)

/** Resposta de `GET /api/cartoes/catalogo`: só os cartões que o papel de quem pediu vê. */
@Serializable
data class CatalogoDto(val cartoes: List<CartaoCatalogoDto>)

/** Corpo do drill genérico (`GET /api/cartoes/{id}/tasks`). [linkJira] fica nulo até o link ser liberado (Tarefa 4). */
@Serializable
data class DrillGenericoDto(
    val titulo: String,
    val definicaoChave: String,
    val total: Int,
    val colunas: List<String>,
    val linhas: List<LinhaDeDrill>,
    val linkJira: String? = null,
)

internal fun CartaoMeta.paraDto() = CartaoCatalogoDto(
    id = id,
    grupo = grupo.id,
    natureza = natureza.name,
    tela = tela,
    tituloChave = tituloChave,
    oQueMostraChave = oQueMostraChave,
    comoCalculaChave = comoCalculaChave,
    fontes = fontes.map { it.name },
    papeis = papeis.map { it.name }.sorted(),
    temDrill = temDrill,
    exigeChangelog = exigeChangelog,
    exigeWorklog = exigeWorklog,
)
