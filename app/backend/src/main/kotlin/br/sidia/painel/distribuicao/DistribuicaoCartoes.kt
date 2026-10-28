package br.sidia.painel.distribuicao

import br.sidia.painel.cards.CODIGO_SEGMENTO_INVALIDO
import br.sidia.painel.cards.DrillRow
import br.sidia.painel.cards.ErroHttp
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.contrato.CartaoMeta
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.EntradaDeCartao
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.LinhaDeDrill
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.hoje
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.security.Role
import br.sidia.painel.trabalho.SqliteWorkStore
import io.ktor.http.HttpStatusCode

/** Quem vê a Distribuição e o Rebalanceamento: liderança (o líder de squad só com o próprio squad, pela visão do cálculo). */
private val LIDERANCA = setOf(Role.SQUAD_LEADER, Role.COORDENADOR, Role.GERENTE)

private const val TELA_DISTRIBUICAO = "distribuicao"
private const val TELA_REBALANCEAMENTO = "rebalanceamento"
private const val TELA_DECISOES = "decisoes"

/**
 * Cartão da Distribuição e do Rebalanceamento: o número e a gaveta saem da MESMA análise ([DistribuicaoServico]), com os
 * mesmos parâmetros, então o número do cartão é o total do drill. Status sem mapeamento deixa o cartão zerado (a tela avisa).
 */
private class CartaoDeDistribuicao(
    override val meta: CartaoMeta,
    private val segmentos: (AnaliseDeDistribuicao, ChavesDoDia) -> Map<String, List<DrillRow>>,
) : CartaoRegistrado {
    override val instancia: String = INSTANCIA_TRABALHO

    private fun montar(e: EntradaDeCartao): Map<String, List<DrillRow>> {
        val ds = requireNotNull(e.ctx.dataSource)
        val a = try {
            DistribuicaoServico(e.ctx, SqliteWorkStore(ds)).analisar(e.principal, hoje(e.ctx), e.ctx.clock.instant())
        } catch (x: UnmappedStatusException) {
            return emptyMap()
        }
        return segmentos(a, ChavesDoDia.de(e.ctx, a, SqliteDecisaoStore(ds)))
    }

    override suspend fun valores(e: EntradaDeCartao): Map<String, Int> {
        if (!meta.temDrill) return emptyMap()
        return montar(e).mapValues { it.value.size }
    }

    override suspend fun drill(e: EntradaDeCartao, segmento: String): List<LinhaDeDrill> =
        montar(e)[segmento] ?: throw ErroHttp(HttpStatusCode.BadRequest, CODIGO_SEGMENTO_INVALIDO)
}

/**
 * Cartões da Distribuição (DI-01 a DI-04), do Rebalanceamento (RB-01 e RB-02) e do registro de decisões (DC-01). O DI-03
 * (índice de Jain do conjunto), o DI-04 (fatores de cada candidato, em ordem alfabética) e o DC-01 (lista de decisões) não têm
 * tasks por trás: o texto do cartão diz o que ele mostra.
 */
internal fun cartoesDeDistribuicao(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun meta(id: String, tela: String, natureza: Natureza, temDrill: Boolean) =
        metaDeTrabalho(id, tela, GrupoDeCartao.ACAO, natureza, fontes, exigeChangelog = true, temDrill = temDrill, papeis = LIDERANCA)

    fun linhasDeSugestoes(a: AnaliseDeDistribuicao, pendentes: List<br.sidia.painel.trabalho.IssueSnapshot>) =
        pendentes.map { a.linha(it, detalheDePendente(it, a.sugestoes[it.key])) }

    return listOf(
        CartaoDeDistribuicao(meta("DI-01", TELA_DISTRIBUICAO, Natureza.ESTOQUE, true)) { a, _ ->
            mapOf("pendentes" to linhasDeSugestoes(a, a.pendentes))
        },
        CartaoDeDistribuicao(meta("DI-02", TELA_DISTRIBUICAO, Natureza.PESSOAS, true)) { a, chaves ->
            val semDecisao = a.pendentes.filter { a.sugestoes.getValue(it.key).sugerido != null && chaves.decididas[chaves.distribuicao.getValue(it.key)] == null }
            mapOf("semDecisao" to linhasDeSugestoes(a, semDecisao))
        },
        CartaoDeDistribuicao(meta("DI-03", TELA_DISTRIBUICAO, Natureza.PESSOAS, false)) { _, _ -> emptyMap() },
        CartaoDeDistribuicao(meta("DI-04", TELA_DISTRIBUICAO, Natureza.PESSOAS, false)) { _, _ -> emptyMap() },
        CartaoDeDistribuicao(meta("RB-01", TELA_REBALANCEAMENTO, Natureza.PESSOAS, true)) { a, _ ->
            mapOf("movimentos" to a.movimentos.map { a.linha(a.tasks.getValue(it.issueKey), detalheDeMovimento(it)) })
        },
        CartaoDeDistribuicao(meta("RB-02", TELA_REBALANCEAMENTO, Natureza.PESSOAS, true)) { a, chaves ->
            val semDecisao = a.movimentos.filter { chaves.decididas[chaves.rebalanceamento.getValue(it.issueKey)] == null }
            mapOf("semDecisao" to semDecisao.map { a.linha(a.tasks.getValue(it.issueKey), detalheDeMovimento(it)) })
        },
        // O registro de decisões (AU-01 do inventário; o id AU-01 já é da Auditoria): coordenação e gerente; sem gaveta, é uma lista de decisões.
        CartaoDeDistribuicao(
            metaDeTrabalho(
                "DC-01", TELA_DECISOES, GrupoDeCartao.GOVERNANCA, Natureza.QUALIDADE, listOf(FonteDoDado.CONFIGURACAO),
                temDrill = false, papeis = setOf(Role.COORDENADOR, Role.GERENTE),
            ),
        ) { _, _ -> emptyMap() },
    )
}

private fun detalheDeMovimento(m: Movimento): String =
    "Sugestão: de ${m.de} para ${m.para} (${String.format(java.util.Locale.ROOT, "%.1f", m.horas)} h)"
