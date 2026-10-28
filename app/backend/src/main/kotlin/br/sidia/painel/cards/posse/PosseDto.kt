package br.sidia.painel.cards.posse

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Posse da task (PS-01 e PS-02): cada pessoa responde pelo tempo em que ficou com a task. [unit] é `util`
 * ou `corrido`. Sem ranking: [pessoas] em ordem alfabética. Segmentos do drill: `tasks`, `transferidas` e `pessoa:<id>`.
 */
@Serializable
data class PosseDto(
    val tasks: List<PosseTaskDto>,
    val pessoas: List<PossePessoaDto>,
    val unit: String,
    val avisos: List<AvisoDto>,
)

/** Uma task e a posse dela: [transferencias] são as passagens de responsável (inclusive de e para "ninguém"). */
@Serializable
data class PosseTaskDto(val key: String, val resumo: String, val transferencias: Int, val posse: List<FracaoPosseDto>)

/**
 * A parte de uma pessoa na task. [pessoa] nulo = "outra pessoa ou sem responsável": quem está fora da visão de quem pediu
 * nunca aparece pelo nome, e as partes de todos eles se somam numa só.
 */
@Serializable
data class FracaoPosseDto(val pessoa: String?, val dias: Double, val fracao: Double)

/** Uma pessoa da visão: as tasks em que teve posse, o tempo total e as passagens de responsável que recebeu e repassou. */
@Serializable
data class PossePessoaDto(val pessoa: String, val tasks: Int, val dias: Double, val recebidas: Int, val repassadas: Int)
