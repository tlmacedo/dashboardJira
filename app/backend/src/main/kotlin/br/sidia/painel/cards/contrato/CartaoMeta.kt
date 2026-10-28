package br.sidia.painel.cards.contrato

import br.sidia.painel.security.Role

/**
 * De onde o cartão tira o dado. Tudo sai do SQLite do painel; nenhum cartão fala com o Jira. A tradução do nome
 * é a chave `catalogo.fonte.<NOME>`.
 */
enum class FonteDoDado {
    /** Campos da issue (status, responsável, vencimento, resolução). */
    ISSUE,

    /** Histórico de mudanças (changelog) gravado pela sincronização. */
    CHANGELOG,

    /** Vínculos entre issues. */
    ISSUE_LINK,

    /** Registros de horas. */
    WORKLOG,

    /** Estado do sincronizador (execuções, cursor). */
    SINCRONIZACAO,

    /** Arquivos de configuração versionados (YAML). */
    CONFIGURACAO,
}

/**
 * Identidade e classe de um cartão (contrato do cartão, spec 17 seção 4). Só carrega CHAVES de tradução (nunca o
 * texto), para o catálogo servir pt-BR e inglês sem duplicar frase no backend.
 *
 * @property id estável, no formato `AA-nn` (ex.: `TS-03`)
 * @property grupo onde o cartão mora no menu
 * @property natureza a pergunta que ele responde
 * @property tela id da tela (e da rota `/api/cards/<tela>`) onde ele vive
 * @property papeis quem vê o cartão; o dado dentro dele ainda passa pela visão de cada pessoa
 * @property temDrill se o número abre uma lista de tasks (todo cartão com drill passa no teste de contrato)
 */
data class CartaoMeta(
    val id: String,
    val grupo: GrupoDeCartao,
    val natureza: Natureza,
    val tela: String,
    val tituloChave: String,
    val oQueMostraChave: String,
    val comoCalculaChave: String,
    val fontes: List<FonteDoDado>,
    val papeis: Set<Role>,
    val temDrill: Boolean,
    val exigeChangelog: Boolean,
    val exigeWorklog: Boolean,
) {
    init {
        require(FORMATO_ID.matches(id)) { "id de cartão inválido" }
        require(tela.isNotBlank()) { "cartão $id sem tela" }
        require(listOf(tituloChave, oQueMostraChave, comoCalculaChave).all { it.isNotBlank() }) { "cartão $id sem chave de texto" }
        require(papeis.isNotEmpty()) { "cartão $id sem papel que o veja" }
        require(fontes.isNotEmpty()) { "cartão $id sem fonte do dado" }
    }

    companion object {
        /** Duas letras maiúsculas, hífen e dois dígitos. */
        val FORMATO_ID = Regex("[A-Z]{2}-\\d{2}")
    }
}
