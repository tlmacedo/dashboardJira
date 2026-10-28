package br.sidia.painel.cards

import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.security.Role

/**
 * Cartão da tela Sincronização. Não há tasks por trás (o dado é o estado do sincronizador, só do gerente), então
 * `temDrill = false`: não tem números por segmento e a gaveta não abre. A tela lê o estado em `/api/sync/status`.
 */
internal class CartaoDeSincronizacao(override val meta: CartaoMeta) : CartaoRegistrado {
    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> = emptyMap()

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        throw ErroHttp(io.ktor.http.HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
}

private fun meta(n: Int, tituloChave: String) = CartaoMeta(
    id = "SY-0$n",
    grupo = GrupoDeCartao.GOVERNANCA,
    natureza = Natureza.QUALIDADE,
    tela = "sincronizacao",
    tituloChave = tituloChave,
    oQueMostraChave = "card.sy0$n.oQueMostra",
    comoCalculaChave = "card.sy0$n.comoCalcula",
    fontes = listOf(FonteDoDado.SINCRONIZACAO),
    papeis = setOf(Role.GERENTE),
    temDrill = false,
    exigeChangelog = false,
    exigeWorklog = false,
)

/** Os cartões da Sincronização: modo do sincronizador, últimas execuções por instância e retenção. */
internal fun cartoesDeSincronizacao(): List<CartaoRegistrado> = listOf(
    CartaoDeSincronizacao(meta(1, "sinc.modo")),
    CartaoDeSincronizacao(meta(2, "sinc.execucoes")),
    CartaoDeSincronizacao(meta(3, "sinc.retencao")),
)
