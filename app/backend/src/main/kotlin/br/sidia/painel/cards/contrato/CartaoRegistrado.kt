package br.sidia.painel.cards.contrato

import br.sidia.painel.AppContext
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.security.Principal
import br.sidia.painel.sync.IssueStore
import io.ktor.http.Parameters

/** Uma linha da gaveta de tasks. [DrillRow] já esconde o responsável que está fora da visão de quem pediu. */
typealias LinhaDeDrill = DrillRow

/**
 * O que um cartão precisa para calcular: o contexto do painel, quem pergunta e os filtros da requisição (os mesmos
 * parâmetros de URL do cartão e do drill). O mesmo [parametros] vale para [CartaoRegistrado.valores] e para
 * [CartaoRegistrado.drill], que é o que garante "número do cartão = total do drill".
 */
class EntradaDeCartao(
    val ctx: AppContext,
    val store: IssueStore,
    val principal: Principal,
    val parametros: Parameters,
)

/**
 * Contrato único do cartão. Os cálculos continuam nos domínios existentes: [valores] e [drill] só os chamam, nada
 * é recalculado em dois lugares.
 *
 * - [valores] devolve o(s) número(s) do cartão por segmento (ex.: `analisadas`, `status:Em revisão`).
 * - [drill] devolve as tasks de um segmento; para todo par `(segmento, valor)` de [valores] o tamanho da lista é
 *   exatamente `valor` (invariante testada em `ContratoDeCartoesTest`). Segmento desconhecido lança um erro 400 com
 *   o código `SEGMENTO_INVALIDO`. Cartão com `temDrill = false` não é chamado em [drill].
 */
interface CartaoRegistrado {
    val meta: CartaoMeta

    /** Instância do Jira de onde as tasks do drill vêm; serve só para montar o link "Abrir no Jira". */
    val instancia: String? get() = null

    /**
     * Módulo sensível a que o cartão pertence (Integridade, Auditoria): a rota genérica do drill só o serve com a trava do DPO
     * aberta ([br.sidia.painel.lgpd.TravaDpo]). Nulo = cartão comum.
     */
    val moduloSensivel: br.sidia.painel.lgpd.ModuloSensivel? get() = null

    suspend fun valores(e: EntradaDeCartao): Map<String, Int>

    suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill>
}
