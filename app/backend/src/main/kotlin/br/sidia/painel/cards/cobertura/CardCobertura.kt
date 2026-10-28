package br.sidia.painel.cards.cobertura

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
import br.sidia.painel.cards.inteiroEntre
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao

/**
 * Card Cobertura por ausência: as ausências das pessoas da visão que tocam o horizonte (`dias`, 1 a 90; padrão
 * `horizonteCoberturaDias` do planejamento, 400 se fora) e o que fica sem cobertura. Só liderança. Os candidatos são do mesmo
 * squad, visíveis, em ordem alfabética. Filtros que valem: `squad` e `pessoa`. A ausência não tem motivo.
 */
object CardCobertura : CardDeLeitura<CoberturaDto> {
    override val id = "cobertura"
    override val periodoPadraoDias: Int? = null

    private val DIAS_PERMITIDOS = 1..90

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CoberturaDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val dias = e.parametros.inteiroEntre("dias", motor.regras.horizonteCoberturaDias, DIAS_PERMITIDOS)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val analise = CoberturaCalculator(motor, ctx.roles).calcular(e.escopo.visiveis, abertas, e.hoje, dias)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                linhaDe(s, e.escopo, s.duedate?.let { "Vence em $it" } ?: "Sem vencimento")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CoberturaDto(ausencias = emptyList(), avisos = avisos)
}

/** Cartão da tela Cobertura por ausência (`CB-01`): um número por ausência, `ausencia:<pessoa>:<de>` = as tasks afetadas. */
internal fun cartoesDeCobertura(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "CB-01", CardCobertura.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO), papeis = SO_LIDERANCA,
        ),
        CardCobertura, { it.startsWith("ausencia:") },
    ) { dto -> dto.ausencias.associate { "ausencia:${it.pessoa}:${it.de}" to it.issues.size } },
)
