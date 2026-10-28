package br.sidia.painel.cards.plano

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
import java.util.Locale

/** Quantas semanas o plano aceita (`semanas=4..8`; o padrão é 4). */
val SEMANAS_DO_PLANO = 4..8

/**
 * Card Plano de 4 a 8 semanas: horas planejadas contra a capacidade, por pessoa e semana. Só liderança (a visão de um
 * usuário comum é a própria Carga). Filtros que valem: `squad` e `pessoa`; `semanas` fora de 4 a 8 é 400.
 */
object CardPlano : CardDeLeitura<PlanoDto> {
    override val id = "plano"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<PlanoDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val semanas = e.parametros.inteiroEntre("semanas", SEMANAS_DO_PLANO.first, SEMANAS_DO_PLANO)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS)).filter { e.escopo.permiteAtual(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val pessoas = motor.pessoas().filter { it in e.escopo.visiveis }
        val analise = PlanoCalculator(motor).calcular(pessoas, abertas, e.hoje, semanas)
        return ResultadoDeCard(analise.dto) { segmento ->
            analise.segmentos[segmento]?.map { s ->
                val h = motor.horas.de(s)
                val horas = String.format(Locale.ROOT, "Restam %.1f h%s", h.valor, if (h.assumidas) " (horas assumidas)" else "")
                linhaDe(s, e.escopo, s.duedate?.let { "$horas; vence em $it" } ?: "$horas; sem vencimento")
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = PlanoDto(semanas = emptyList(), linhas = emptyList(), avisos = avisos)
}

/** Cartão da tela Plano de 4 a 8 semanas (`PL-01`): cada célula abre as tasks que ocupam a semana da pessoa. */
internal fun cartoesDePlano(): List<CartaoRegistrado> = listOf(
    CartaoDeLeitura(
        metaDeTrabalho(
            "PL-01", CardPlano.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS,
            listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO), papeis = SO_LIDERANCA,
        ),
        CardPlano, { it.startsWith("celula:") },
    ) { dto ->
        buildMap { for (l in dto.linhas) for (c in l.celulas) put("celula:${l.pessoa}:${c.semana}", c.itens) }
    },
)
