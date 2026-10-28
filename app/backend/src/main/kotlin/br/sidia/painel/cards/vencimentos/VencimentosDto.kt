package br.sidia.painel.cards.vencimentos

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta de Vencimentos da semana (VE-01 e VE-02): o que vence nos próximos [dias] (7 ou 14), com a FOLGA REAL: os dias
 * úteis entre o término previsto pela capacidade da pessoa e o vencimento. [riscoDeAtrasar] conta as linhas em `problema`.
 * Segmentos do drill: `vencem` (todas as linhas) e `riscoDeAtrasar`.
 */
@Serializable
data class VencimentosDto(val dias: Int, val vencem: Int, val linhas: List<VencimentoDto>, val riscoDeAtrasar: Int, val avisos: List<AvisoDto>)

/**
 * [folgaDiasUteis] negativa = o término previsto cai depois do vencimento. [termino] nulo = não cabe no horizonte ou a
 * task não tem responsável (sem previsão). [estado] = `ok` (folga de 1 dia útil ou mais), `atencao` (folga zero) ou
 * `problema` (folga negativa ou sem término: "risco de atrasar").
 */
@Serializable
data class VencimentoDto(
    val key: String,
    val resumo: String,
    val pessoa: String?,
    val vence: String,
    val termino: String?,
    val folgaDiasUteis: Double?,
    val estado: String,
)
