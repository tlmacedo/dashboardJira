package br.sidia.painel.cards

import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.security.Role

/** Tela (e rota `/api/cards/<tela>`) dos cartões de Tempo em status. */
private const val TELA_TEMPO_EM_STATUS = "tempo-em-status"

/**
 * Cartão de Tempo em status no contrato: só aponta para os cálculos de [calcular] e [drillDeTempoEmStatus] (nada é
 * recalculado). Cada cartão aceita apenas os segmentos que ele mesmo conta ([aceita]); outro segmento é 400.
 */
internal class CartaoTempoEmStatus(
    override val meta: CartaoMeta,
    /** O segmento pertence a este cartão? */
    private val aceita: (Detalhe) -> Boolean,
    /** Os números do cartão por segmento, a partir do mesmo cálculo da tela. */
    private val numeros: (TempoEmStatusDto) -> Map<String, Int>,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TEMPO_EM_STATUS

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> {
        val filtros = lerFiltros(e.parametros, hoje(e.ctx))
        return numeros(calcular(e.ctx, e.store, e.principal, filtros).dto)
    }

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        drillDeTempoEmStatus(e.ctx, e.store, e.principal, e.parametros, segmento, aceita)
}

private val TODOS_OS_PAPEIS = Role.entries.toSet()

private fun meta(n: Int, tituloChave: String) = CartaoMeta(
    id = "TS-0$n",
    grupo = GrupoDeCartao.RESULTADOS,
    natureza = Natureza.FLUXO,
    tela = TELA_TEMPO_EM_STATUS,
    tituloChave = tituloChave,
    oQueMostraChave = "card.ts0$n.oQueMostra",
    comoCalculaChave = "card.ts0$n.comoCalcula",
    fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG),
    papeis = TODOS_OS_PAPEIS,
    temDrill = true,
    exigeChangelog = true,
    exigeWorklog = false,
)

private fun segmentoDeStatus(d: Detalhe) = d is Detalhe.PorStatus

/**
 * Os sete cartões de Tempo em status (inventário `docs/analise/relatorio-cards-app-atual.md`, TS-01 a TS-07).
 * A tela mostra TS-04 e TS-05 na mesma seção (barras de mediana e P85); os dois se registram porque contam o mesmo
 * universo e abrem o mesmo recorte.
 */
internal fun cartoesDeTempoEmStatus(): List<CartaoRegistrado> = listOf(
    CartaoTempoEmStatus(meta(1, "tempo.kpi.analisadas"), { it is Detalhe.Analisadas }) { mapOf("analisadas" to it.analisadas) },
    // Percentual não tem tasks próprias: abre as analisadas, que formam o numerador e o denominador.
    CartaoTempoEmStatus(meta(2, "tempo.kpi.eficiencia"), { it is Detalhe.Analisadas }) { mapOf("analisadas" to it.analisadas) },
    CartaoTempoEmStatus(meta(3, "tempo.kpi.paradas"), { it is Detalhe.Paradas }) { mapOf("paradas" to it.paradas) },
    CartaoTempoEmStatus(meta(4, "tempo.porStatus"), ::segmentoDeStatus) { dto ->
        dto.porStatus.associate { "status:${it.status}" to it.n }
    },
    CartaoTempoEmStatus(meta(5, "card.ts05.titulo"), ::segmentoDeStatus) { dto ->
        dto.porStatus.associate { "status:${it.status}" to it.n }
    },
    CartaoTempoEmStatus(meta(6, "tempo.matriz"), { it is Detalhe.PorPessoaStatus }) { dto ->
        dto.pessoaStatus.associate { "pessoa:${it.person}:status:${it.status}" to it.n }
    },
    CartaoTempoEmStatus(meta(7, "tempo.paradas"), { it is Detalhe.Paradas }) { mapOf("paradas" to it.paradas) },
)
