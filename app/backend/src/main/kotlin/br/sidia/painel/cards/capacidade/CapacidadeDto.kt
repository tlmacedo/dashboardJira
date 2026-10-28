package br.sidia.painel.cards.capacidade

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Capacidade e ausências (CA-01 a CA-09), só para a liderança. Sem ranking: [mapa] e [ausencias] em ordem
 * alfabética de pessoa; [listaAtrasos] por vencimento. As ausências não têm motivo. Segmentos do drill:
 * `atrasosPrevistos` e `atrasosPorAusencia` (as tasks que vão atrasar, com e sem a ausência).
 */
@Serializable
data class CapacidadeDto(
    val versaoRegras: String,
    val alertas: List<AlertaCapacidadeDto>,
    val atrasosPrevistos: Int,
    val atrasosPorAusencia: Int,
    val jain: Double?,
    val mapa: List<LinhaMapaDto>,
    val ausencias: List<AusenciaDto>,
    val feriados: List<FeriadoDto>,
    val listaAtrasos: List<AtrasoPrevistoDto>,
    val avisos: List<AvisoDto>,
)

@Serializable
data class LinhaMapaDto(val pessoa: String, val semanas: List<CelulaMapaDto>)

/** [ausente]: a semana não tem capacidade por causa da ausência (a tela escreve "aus."), não por feriado. */
@Serializable
data class CelulaMapaDto(val semana: String, val ocupacao: OcupacaoDto, val ausente: Boolean)

/** [tipo] = `CARGA_ALTA` ou `AUSENCIA_COM_TRABALHO`; [horasAReplanejar] só no segundo (horas agendadas nos dias de ausência). */
@Serializable
data class AlertaCapacidadeDto(val pessoa: String, val tipo: String, val semana: String, val horasAReplanejar: Double?)

/** Ausência sem motivo: o painel nunca o guarda nem o mostra. */
@Serializable
data class AusenciaDto(val pessoa: String, val de: String, val ate: String, val fracao: Double)

@Serializable
data class FeriadoDto(val dia: String, val nome: String?)

/** [causa] = `AUSENCIA` (o atraso some no cenário sem ausência) ou `CARGA`; [termino] nulo = não cabe no horizonte. */
@Serializable
data class AtrasoPrevistoDto(val key: String, val pessoa: String, val vence: String, val termino: String?, val causa: String)
