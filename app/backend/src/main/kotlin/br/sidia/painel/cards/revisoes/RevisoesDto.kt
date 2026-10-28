package br.sidia.painel.cards.revisoes

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Revisões pendentes por revisor (RV-01 e RV-02): as reviews abertas de cada revisor da visão, em ordem
 * alfabética e sem ranking. [semRevisor]: reviews abertas sem responsável, só para coordenação e gerente (nulo para os demais).
 * Segmentos do drill: `revisor:<id>:pendentes|vencidas|foraDoSla` e `semRevisor`.
 */
@Serializable
data class RevisoesDto(
    val linhas: List<RevisorDto>,
    val semRevisor: Int?,
    val avisos: List<AvisoDto>,
)

/**
 * Um revisor. [vencidas]: vencimento antes de hoje. [foraDoSla]: idade em dias úteis desde a criação maior que o prazo de
 * revisão do planejamento. [idadeP50]: mediana das idades (dias úteis); nula sem review.
 */
@Serializable
data class RevisorDto(val revisor: String, val pendentes: Int, val vencidas: Int, val foraDoSla: Int, val idadeP50: Double?)
