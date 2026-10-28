package br.sidia.painel.cards.negativas

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Negativas (NE-01 a NE-08 e NE-10). Cada número abre, pelo drill genérico, as MESMAS tasks que o
 * formam (segmentos: `reabertas`, `devolvidas`, `canceladas`, `concluidasComAtraso`, `atrasadasSemJustificativa`,
 * `abertasVencidas`, `estouroDeEstimativa`, `muitasTrocas`, `concluidas`, `taxa:reabertura`, `taxa:devolucao`,
 * `taxa:atraso`, `taxa:atrasoSemJustificativa` e `pessoa:<id>:reabertas|devolvidas|atrasadas`).
 *
 * Eventos (reabertura, devolução, trocas de responsável) valem pela DATA DO CHANGELOG dentro do período, nunca pelo
 * `updated` da issue. As taxas são fração de 0 a 1 sobre [concluidas] (concluídas positivas do período), nulas sem
 * concluídas; acima de [limiteDaTaxa] a tela marca ▲.
 */
@Serializable
data class NegativasDto(
    val reabertas: Int,
    val devolvidas: Int,
    val canceladas: Int,
    val concluidasComAtraso: Int,
    val atrasadasSemJustificativa: Int,
    val abertasVencidas: Int,
    val estouroDeEstimativa: Int,
    val muitasTrocas: Int,
    val concluidas: Int,
    val taxaReabertura: Double?,
    val taxaDevolucao: Double?,
    val taxaAtraso: Double?,
    val taxaAtrasoSemJustificativa: Double?,
    val limiteDaTaxa: Double,
    val porResponsavel: List<NegativaPessoaDto>,
    val unit: String,
    val avisos: List<AvisoDto>,
)

/** Contagens atribuídas a uma pessoa pela posse da task NO INSTANTE do evento (ordem alfabética, sem ranking). */
@Serializable
data class NegativaPessoaDto(val pessoa: String, val reabertas: Int, val devolvidas: Int, val atrasadas: Int)
