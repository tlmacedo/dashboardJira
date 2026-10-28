package br.sidia.painel.cards.contrato

/**
 * Onde o cartão mora: os grupos do menu (plano visual, seção 4) que têm cartões. O [id] é o mesmo do `GroupId` do
 * `nav.ts` e a chave de tradução do nome é `grupo.<id>`.
 */
enum class GrupoDeCartao(val id: String) {
    TRABALHO_EM_CURSO("trabalho"),
    RESULTADOS("resultados"),
    PRAZOS("prazos"),
    PESSOAS_E_PLANEJAMENTO("pessoas"),
    ACAO("acao"),
    AUDITORIA("auditoria"),
    GOVERNANCA("governanca"),
}
