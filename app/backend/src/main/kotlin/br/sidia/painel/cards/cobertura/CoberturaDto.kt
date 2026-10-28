package br.sidia.painel.cards.cobertura

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Cobertura por ausência (CB-01): as ausências das pessoas da visão que tocam `[hoje, hoje + dias]`, em ordem
 * alfabética e de data, com o trabalho que fica sem cobertura. A ausência NÃO tem motivo. Segmento do drill:
 * `ausencia:<pessoa>:<de>` (as tasks afetadas).
 */
@Serializable
data class CoberturaDto(
    val ausencias: List<CoberturaAusenciaDto>,
    val avisos: List<AvisoDto>,
    val dias: Int = 30,
)

/**
 * Uma ausência e o que ela deixa sem cobertura: as [issues] afetadas (chaves), as [horasAReplanejar], as [competencias] pedidas
 * por elas (`skill:<x>`) e quem [podemCobrir]. Sem task afetada, [podemCobrir] fica vazio.
 */
@Serializable
data class CoberturaAusenciaDto(
    val pessoa: String,
    val de: String,
    val ate: String,
    val issues: List<String>,
    val horasAReplanejar: Double,
    val competencias: List<String>,
    val podemCobrir: List<CandidatoDto>,
)

/**
 * Um candidato à cobertura: pessoa do mesmo squad com [folgaH] (capacidade menos demanda nas semanas da ausência). Os candidatos
 * saem em ORDEM ALFABÉTICA, nunca por folga (seria um ranking de disponibilidade).
 */
@Serializable
data class CandidatoDto(val pessoa: String, val folgaH: Double, val temTodasAsCompetencias: Boolean)
