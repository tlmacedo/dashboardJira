package br.sidia.painel.cards.simulador

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do Simulador de ausência (SI-01): o que atrasaria se [pessoa] se ausentasse de [de] a [ate] com a [fracao] do dia.
 * Só leitura: nada é gravado e nenhuma ausência é cadastrada. [atrasosCausados] são as chaves das tasks que não atrasavam e
 * passam a atrasar; [horasAReplanejar] = horas agendadas nesses dias × [fracao]. Segmento do drill: `atrasosCausados`.
 */
@Serializable
data class SimuladorDto(
    val pessoa: String,
    val de: String,
    val ate: String,
    val fracao: Double,
    val atrasosCausados: List<String>,
    val horasAReplanejar: Double,
    val semanas: List<ComparacaoSemanaDto>,
    val avisos: List<AvisoDto>,
)

/** A ocupação da semana ([semana] = segunda-feira) sem a ausência hipotética ([antes]) e com ela ([depois]). */
@Serializable
data class ComparacaoSemanaDto(val semana: String, val antes: OcupacaoDto, val depois: OcupacaoDto)
