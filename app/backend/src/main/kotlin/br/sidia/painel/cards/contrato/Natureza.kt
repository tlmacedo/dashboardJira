package br.sidia.painel.cards.contrato

/**
 * A pergunta que o cartão responde (spec 17, seção 4). Não confundir com o grupo, que é onde o cartão mora no menu.
 */
enum class Natureza {
    /** O que há agora. */
    ESTOQUE,

    /** Como o trabalho anda. */
    FLUXO,

    /** O que vence ou atrasou. */
    PRAZO,

    /** Quanto cabe a quem (carga e capacidade). */
    PESSOAS,

    /** O dado ou o processo está suspeito. */
    QUALIDADE,
}
