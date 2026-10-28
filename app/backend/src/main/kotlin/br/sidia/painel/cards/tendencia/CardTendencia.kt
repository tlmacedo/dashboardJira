package br.sidia.painel.cards.tendencia

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
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * Card Tendência de ocupação: 12 semanas reconstruídas do changelog com as horas de hoje (aviso obrigatório). Só liderança.
 * As issues entram se alguma pessoa da visão teve posse delas em algum momento; só as pessoas da visão aparecem. Filtros
 * que valem: `squad` e `pessoa`. Não tem tasks por trás (é uma série reconstruída).
 */
object CardTendencia : CardDeLeitura<TendenciaDto> {
    override val id = "tendencia"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<TendenciaDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val inicio = e.hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((TendenciaCalculator.SEMANAS - 1).toLong())
            .atStartOfDay(ctx.settings.zone).toInstant()
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS))
            .filter { e.escopo.permitePosse(it, ctx.statusMap, e.agora) }
        val recentes = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, resolvidasDe = inicio))
            .filter { e.escopo.permitePosse(it, ctx.statusMap, e.agora) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, recentes)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        return ResultadoDeCard(TendenciaCalculator(motor).calcular(pessoas, abertas + recentes, e.hoje)) { null }
    }

    override fun vazio(avisos: List<AvisoDto>) = TendenciaDto(semanas = emptyList(), linhas = emptyList(), avisos = avisos)
}

/** Cartão da tela Tendência de ocupação (`TD-01`): série reconstruída, sem tasks por trás. */
internal fun cartoesDeTendencia(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "TD-01", CardTendencia.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.CONFIGURACAO), exigeChangelog = true, temDrill = false,
            papeis = SO_LIDERANCA,
        ),
        CardTendencia, { false },
    ) { emptyMap() },
)
