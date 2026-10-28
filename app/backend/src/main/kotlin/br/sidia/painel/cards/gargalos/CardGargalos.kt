package br.sidia.painel.cards.gargalos

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.capacidade.SO_LIDERANCA
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import java.util.Locale

/**
 * Card Gargalos e fluxo: fluxo cumulativo de 60 dias, estado das etapas e pessoas acima da capacidade ou com folga, dentro
 * da visão de quem consulta. Só liderança. Filtros que valem: `squad` e `pessoa`.
 */
object CardGargalos : CardDeLeitura<GargalosDto> {
    override val id = "gargalos"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<GargalosDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val inicioDaJanela = e.hoje.minusDays((GargalosCalculator.DIAS - 1).toLong()).atStartOfDay(ctx.settings.zone).toInstant()
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        val recentes = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = inicioDaJanela))
            .filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, recentes)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        val analise = GargalosCalculator(motor).calcular(pessoas, abertas + recentes, abertas, e.hoje, e.agora)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val detalhe = if (segmento.startsWith("status:")) {
                    val dias = Fluxo.diasNoStatusAtual(s, ctx.calendar, TimeUnitMode.UTIL, e.agora)
                    String.format(Locale.ROOT, "No status há %.1f dias úteis", dias)
                } else {
                    val h = motor.horas.de(s)
                    String.format(Locale.ROOT, "Restam %.1f h%s", h.valor, if (h.assumidas) " (horas assumidas)" else "")
                }
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = GargalosDto(
        dias = emptyList(), faixas = emptyList(), etapas = emptyList(), acimaDaCapacidade = 0, comFolga = 0, tasksAcimaDaCapacidade = 0,
        tasksComFolga = 0, avisos = avisos,
    )
}

/** Cartões da tela Gargalos e fluxo (`GA-01` a `GA-04`). O fluxo cumulativo é uma série reconstruída, sem tasks por trás. */
internal fun cartoesDeGargalos(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO)
    fun cartao(n: Int, natureza: Natureza, temDrill: Boolean, aceita: (String) -> Boolean, numeros: (GargalosDto) -> Map<String, Int>): CartaoRegistrado =
        CartaoDeLeitura(
            metaDeTrabalho(
                "GA-0$n", CardGargalos.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, natureza, fontes, exigeChangelog = true, temDrill = temDrill,
                papeis = SO_LIDERANCA,
            ),
            CardGargalos, aceita, numeros,
        )
    return listOf(
        cartao(1, Natureza.FLUXO, false, { false }) { emptyMap() },
        cartao(2, Natureza.FLUXO, true, { it.startsWith("status:") }) { dto -> dto.etapas.associate { "status:${it.status}" to it.wip } },
        cartao(3, Natureza.PESSOAS, true, { it == "acimaDaCapacidade" }) { mapOf("acimaDaCapacidade" to it.tasksAcimaDaCapacidade) },
        cartao(4, Natureza.PESSOAS, true, { it == "comFolga" }) { mapOf("comFolga" to it.tasksComFolga) },
    )
}
