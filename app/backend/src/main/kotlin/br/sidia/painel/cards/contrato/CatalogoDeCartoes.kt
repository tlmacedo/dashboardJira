package br.sidia.painel.cards.contrato

import br.sidia.painel.security.Role

/**
 * Reúne os cartões registrados. Id repetido derruba a subida (e o teste): dois cartões com o mesmo id fariam o
 * Mapa de cartões e o drill genérico apontarem para o lugar errado.
 */
class CatalogoDeCartoes(val cartoes: List<CartaoRegistrado>) {
    private val porId: Map<String, CartaoRegistrado>

    init {
        val repetidos = cartoes.groupBy { it.meta.id }.filterValues { it.size > 1 }.keys
        require(repetidos.isEmpty()) { "ids de cartão repetidos: ${repetidos.sorted()}" }
        porId = cartoes.associateBy { it.meta.id }
    }

    fun porId(id: String): CartaoRegistrado? = porId[id]

    /** Só o que algum dos [papeis] vê, na ordem do registro. */
    fun visiveisPara(papeis: Set<Role>): List<CartaoRegistrado> =
        cartoes.filter { c -> c.meta.papeis.any { it in papeis } }

    fun visiveisPara(papel: Role): List<CartaoRegistrado> = visiveisPara(setOf(papel))
}
