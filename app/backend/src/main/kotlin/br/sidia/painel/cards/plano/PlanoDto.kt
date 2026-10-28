package br.sidia.painel.cards.plano

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do Plano de 4 a 8 semanas (PL-01): [semanas] (segundas-feiras, a primeira é a semana atual, contada de hoje até
 * domingo) e [linhas] por pessoa em ordem alfabética. Segmentos do drill: `celula:<pessoa>:<segunda>` (as tasks que ocupam
 * a semana daquela pessoa).
 */
@Serializable
data class PlanoDto(val semanas: List<String>, val linhas: List<LinhaPlanoDto>, val avisos: List<AvisoDto>)

@Serializable
data class LinhaPlanoDto(val pessoa: String, val celulas: List<CelulaPlanoDto>)

/** [semDataH] é a parte de [planejadaH] que vem de tasks sem vencimento (espalhadas por uma regra, não por uma data). */
@Serializable
data class CelulaPlanoDto(
    val semana: String,
    val planejadaH: Double,
    val capacidadeH: Double,
    val ocupacao: OcupacaoDto,
    val itens: Int,
    val semDataH: Double,
)
