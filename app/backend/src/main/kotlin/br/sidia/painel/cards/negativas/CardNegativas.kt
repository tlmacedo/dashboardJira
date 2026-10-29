package br.sidia.painel.cards.negativas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao

/**
 * Card Negativas: reabertas, devolvidas, canceladas, concluídas com atraso, abertas vencidas, estouro de estimativa e
 * muitas trocas, com as taxas sobre as concluídas do período (padrão 90 dias, dia de Manaus). Os eventos de status e de
 * responsável valem pela data do changelog.
 */
object CardNegativas : CardDeLeitura<NegativasDto> {
    override val id = "negativas"
    override val periodoPadraoDias: Int? = 90

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<NegativasDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val de = requireNotNull(e.filtros.de)
        val ate = requireNotNull(e.filtros.ate)
        val inicio = de.atStartOfDay(zona).toInstant()
        val fim = ate.plusDays(1).atStartOfDay(zona).toInstant()
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        fun osConfere(s: br.sidia.painel.trabalho.IssueSnapshot) = e.filtros.os == null || s.osVersion.equals(e.filtros.os, ignoreCase = true)
        val comEventos = e.store.comEventoEntre(INSTANCIA_TRABALHO, ficha, setOf("status", "assignee"), inicio, fim)
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) && osConfere(it) }
        val resolvidas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, inicio, fim, e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, comEventos, resolvidas, abertas)
        val unit = if (e.filtros.unit == TimeUnitMode.UTIL) "util" else "corrido"
        val analise = NegativasCalculator(ctx.calendar, ctx.statusMap, ctx.labelsDeAtraso)
            .analisar(comEventos, resolvidas, abertas, inicio, fim, e.escopo.visiveis, e.agora, unit)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s -> linhaDe(s, e.escopo, analise.motivos[segmento]?.get(s.key).orEmpty()) }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = NegativasDto(
        reabertas = 0, devolvidas = 0, canceladas = 0, concluidasComAtraso = 0, atrasadasSemJustificativa = 0, abertasVencidas = 0,
        estouroDeEstimativa = 0, muitasTrocas = 0, concluidas = 0, taxaReabertura = null, taxaDevolucao = null, taxaAtraso = null,
        taxaAtrasoSemJustificativa = null, limiteDaTaxa = NegativasCalculator.LIMITE_DA_TAXA, porResponsavel = emptyList(),
        unit = "util", avisos = avisos,
    )
}

/**
 * Cartões da tela Negativas (inventário `NE-01` a `NE-08` e `NE-10`). O NE-09 ("maior tempo" por status) é do Plano 10 (T10).
 * O NE-08 (taxas) abre as tasks dos NUMERADORES (as mesmas dos cartões de contagem) e a base `concluidas`.
 */
internal fun cartoesDeNegativas(): List<CartaoRegistrado> {
    val comChangelog = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    val comPrazo = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    val soIssue = listOf(FonteDoDado.ISSUE)
    fun cartao(
        n: Int, natureza: Natureza, fontes: List<FonteDoDado>, aceita: (String) -> Boolean, numeros: (NegativasDto) -> Map<String, Int>,
    ): CartaoRegistrado = CartaoDeLeitura(
        metaDeTrabalho("NE-%02d".format(n), CardNegativas.id, GrupoDeCartao.RESULTADOS, natureza, fontes, exigeChangelog = FonteDoDado.CHANGELOG in fontes),
        CardNegativas, aceita, numeros,
    )
    return listOf(
        cartao(1, Natureza.QUALIDADE, comChangelog, { it == "reabertas" }) { mapOf("reabertas" to it.reabertas) },
        cartao(2, Natureza.QUALIDADE, comChangelog, { it == "devolvidas" }) { mapOf("devolvidas" to it.devolvidas) },
        cartao(3, Natureza.FLUXO, soIssue, { it == "canceladas" }) { mapOf("canceladas" to it.canceladas) },
        cartao(4, Natureza.PRAZO, comPrazo, { it == "concluidasComAtraso" || it == "atrasadasSemJustificativa" }) {
            mapOf("concluidasComAtraso" to it.concluidasComAtraso, "atrasadasSemJustificativa" to it.atrasadasSemJustificativa)
        },
        cartao(5, Natureza.PRAZO, soIssue, { it == "abertasVencidas" }) { mapOf("abertasVencidas" to it.abertasVencidas) },
        cartao(6, Natureza.QUALIDADE, soIssue, { it == "estouroDeEstimativa" }) { mapOf("estouroDeEstimativa" to it.estouroDeEstimativa) },
        cartao(7, Natureza.QUALIDADE, comChangelog, { it == "muitasTrocas" }) { mapOf("muitasTrocas" to it.muitasTrocas) },
        cartao(8, Natureza.QUALIDADE, comChangelog + FonteDoDado.CONFIGURACAO, { it == "concluidas" || it.startsWith("taxa:") }) {
            mapOf(
                "concluidas" to it.concluidas, "taxa:reabertura" to it.reabertas, "taxa:devolucao" to it.devolvidas,
                "taxa:atraso" to it.concluidasComAtraso, "taxa:atrasoSemJustificativa" to it.atrasadasSemJustificativa,
            )
        },
        cartao(9, Natureza.QUALIDADE, comChangelog, { it.startsWith("maiorTempo:") }) { dto ->
            dto.maiorTempoPorStatus.associate { "maiorTempo:${it.valor}" to it.n }
        },
        cartao(10, Natureza.PESSOAS, comChangelog, { it.startsWith("pessoa:") }) { dto ->
            buildMap {
                for (p in dto.porResponsavel) {
                    put("pessoa:${p.pessoa}:reabertas", p.reabertas)
                    put("pessoa:${p.pessoa}:devolvidas", p.devolvidas)
                    put("pessoa:${p.pessoa}:atrasadas", p.atrasadas)
                }
            }
        },
    )
}
