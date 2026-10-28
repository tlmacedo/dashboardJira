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
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao

/**
 * Card Fluxo da semana (FX-01 e FX-02): entrada, início e saída por semana de Manaus, nas últimas 12 semanas. Lê as
 * abertas e as resolvidas desde a primeira semana da janela (o que a Previsão também lê) e entrega a análise do
 * [FluxoDaSemanaCalculator]; os números de cada barra são o tamanho do drill do mesmo segmento.
 */
object CardFluxoDaSemana : CardDeLeitura<FluxoDaSemanaDto> {
    override val id = "fluxo-da-semana"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<FluxoDaSemanaDto> {
        val ctx = e.ctx
        val zona = ctx.settings.zone
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val primeira = Fluxo.semanaDe(e.hoje.atStartOfDay(zona).toInstant(), zona).minusWeeks(FluxoDaSemanaCalculator.SEMANAS - 1L)
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
        val resolvidas = e.store.snapshots(
            ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = primeira.atStartOfDay(zona).toInstant(), osVersion = e.filtros.os),
        )
        val issues = (abertas + resolvidas).filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, issues)
        val analise = FluxoDaSemanaCalculator(ctx.calendar, ctx.statusMap).analisar(issues, e.hoje)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s -> linhaDe(s, e.escopo, detalhe(s, segmento, e)) }
        }
    }

    private fun detalhe(s: IssueSnapshot, segmento: String, e: EntradaDeCard): String {
        val cal = e.ctx.calendar
        val tipo = segmento.substringBefore(':')
        return when (tipo) {
            "entrada", "entradas" -> "Criada em ${cal.localDate(s.created)}"
            "inicio", "iniciadas" -> "Começou em ${Fluxo.inicioAtivo(s, e.ctx.statusMap)?.let(cal::localDate)}"
            "saida", "saidas" -> "Resolvida em ${s.resolved?.let(cal::localDate)}"
            else -> "Cancelada em ${s.resolved?.let(cal::localDate)} (${s.resolution})"
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = FluxoDaSemanaDto(
        semanas = emptyList(), entrou = 0, comecou = 0, saiu = 0, canceladas = 0, saldoDoBacklog = 0, semanasCrescendo = 0,
        backlogCrescendo = false, semanasNaJanela = FluxoDaSemanaCalculator.SEMANAS, tasksNaJanela = 0, avisos = avisos,
    )
}

/** Cartões da tela Fluxo da semana: FX-01 (uma barra por semana e série) e FX-02 (os totais da janela). */
internal fun cartoesDeFluxoDaSemana(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG)
    val porSemana = setOf("entrada", "inicio", "saida", "cancelada")
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("FX-01", CardFluxoDaSemana.id, GrupoDeCartao.TRABALHO_EM_CURSO, Natureza.FLUXO, fontes, exigeChangelog = true),
            CardFluxoDaSemana, { it.substringBefore(':') in porSemana && it.contains(':') },
        ) { dto ->
            buildMap {
                for (s in dto.semanas) {
                    put("entrada:${s.semana}", s.entrou)
                    put("inicio:${s.semana}", s.comecou)
                    put("saida:${s.semana}", s.saiu)
                    put("cancelada:${s.semana}", s.canceladas)
                }
            }
        },
        CartaoDeLeitura(
            metaDeTrabalho("FX-02", CardFluxoDaSemana.id, GrupoDeCartao.TRABALHO_EM_CURSO, Natureza.FLUXO, fontes, exigeChangelog = true),
            CardFluxoDaSemana, { it in setOf("entradas", "iniciadas", "saidas", "canceladas") },
        ) { dto -> mapOf("entradas" to dto.entrou, "iniciadas" to dto.comecou, "saidas" to dto.saiu, "canceladas" to dto.canceladas) },
    )
}
