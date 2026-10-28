package br.sidia.painel.cards.fluxo

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
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import java.time.Duration
import java.util.Locale

/**
 * Card Envelhecimento por etapa (AW-01 e AW-02). Lê as abertas do escopo (com os filtros de tipo, prioridade e OS) e o
 * histórico de 16 semanas de concluídas (só o escopo, não os filtros de tipo e prioridade, como em Negligenciadas), e
 * entrega a análise do [AgingWipCalculator]. O número "acima do normal" e o de cada etapa são o tamanho do drill.
 */
object CardAgingWip : CardDeLeitura<AgingWipDto> {
    override val id = "aging-wip"
    override val periodoPadraoDias: Int? = null

    /** Semanas de histórico que dão a faixa da etapa (as mesmas de Negligenciadas e Abertas). */
    private const val SEMANAS_DE_HISTORICO = 16L

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<AgingWipDto> {
        val ctx = e.ctx
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val historico = e.store.snapshots(
            ConsultaDeTrabalho(
                INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = e.agora.minus(Duration.ofDays(SEMANAS_DE_HISTORICO * 7)),
                osVersion = e.filtros.os,
            ),
        ).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, historico)
        val analise = AgingWipCalculator(ctx.calendar, ctx.statusMap).analisar(abertas, historico, e.agora, e.filtros.unit)
        val unidade = if (e.filtros.unit == TimeUnitMode.UTIL) "dias úteis" else "dias corridos"
        fun n(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val idade = analise.idades.getValue(s.key)
                val p85 = analise.p85PorEtapa[s.status]
                val situacao = when {
                    p85 == null -> "sem faixa (amostra pequena)"
                    idade > p85 -> "acima do normal (P85 da etapa: ${n(p85)})"
                    else -> "dentro do normal (P85 da etapa: ${n(p85)})"
                }
                linhaDe(s, e.escopo, "${n(idade)} $unidade em ${s.status}; $situacao")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = AgingWipDto(etapas = emptyList(), emAndamento = 0, acimaDoNormal = 0, unit = "util", avisos = avisos)
}

/** Cartões da tela Envelhecimento por etapa: AW-01 (uma linha por etapa, com os pontos) e AW-02 (em andamento e acima do normal). */
internal fun cartoesDeAgingWip(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    fun meta(n: Int, natureza: Natureza) =
        metaDeTrabalho("AW-%02d".format(n), CardAgingWip.id, GrupoDeCartao.TRABALHO_EM_CURSO, natureza, fontes, exigeChangelog = true)
    return listOf(
        CartaoDeLeitura(meta(1, Natureza.FLUXO), CardAgingWip, { it.startsWith("etapa:") || it.startsWith("acima:") }) { dto ->
            buildMap {
                for (e in dto.etapas) {
                    put("etapa:${e.status}", e.total)
                    put("acima:${e.status}", e.acima)
                }
            }
        },
        CartaoDeLeitura(meta(2, Natureza.FLUXO), CardAgingWip, { it == "emAndamento" || it == "acimaDoNormal" }) { dto ->
            mapOf("emAndamento" to dto.emAndamento, "acimaDoNormal" to dto.acimaDoNormal)
        },
    )
}
