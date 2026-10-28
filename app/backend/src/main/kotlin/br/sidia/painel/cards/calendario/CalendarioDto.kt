package br.sidia.painel.cards.calendario

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Período do Calendário: uma semana (7 dias) ou um mês (4 semanas, 28 dias) a partir de `inicio`. */
enum class ModoCalendario { SEMANA, MES }

/**
 * Resposta do Calendário do time (CL-01). [dias] é o cabeçalho (feriado, dia útil, capacidade do time no dia, vencimentos e
 * dia crítico) e [linhas] a grade por pessoa, em ordem alfabética: ausência (fração do dia) e vencimentos de cada dia.
 * Sem motivo de ausência. Segmentos do drill: `dia:<aaaa-mm-dd>` (as tasks que vencem naquele dia).
 */
@Serializable
data class CalendarioDto(val inicio: String, val dias: List<DiaDto>, val linhas: List<LinhaCalendarioDto>, val avisos: List<AvisoDto>)

/**
 * [feriado] = nome do feriado (vazio se o arquivo não deu nome; nulo se não é feriado). [capacidadeSquad] = capacidade
 * líquida ÷ bruta das pessoas VISÍVEIS no dia (nulo em dia não útil). [critico] = dia útil com vencimentos e
 * [capacidadeSquad] abaixo de `diaCriticoCapacidade`.
 */
@Serializable
data class DiaDto(
    val dia: String,
    val feriado: String?,
    val util: Boolean,
    val capacidadeSquad: Double?,
    val vencimentos: Int,
    val critico: Boolean,
)

@Serializable
data class LinhaCalendarioDto(val pessoa: String, val celulas: List<CelulaCalendarioDto>)

/** [ausencia]: fração do dia em que a pessoa está ausente (0 = presente, 1 = o dia todo). */
@Serializable
data class CelulaCalendarioDto(val dia: String, val ausencia: Double, val vencimentos: Int)
