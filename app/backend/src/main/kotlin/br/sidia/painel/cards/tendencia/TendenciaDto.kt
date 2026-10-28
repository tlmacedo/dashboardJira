package br.sidia.painel.cards.tendencia

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.OcupacaoDto
import kotlinx.serialization.Serializable

/**
 * Resposta da Tendência de ocupação (TD-01), só para a liderança: as últimas 12 semanas (segundas-feiras, a mais antiga
 * primeiro), reconstruídas do changelog com as HORAS DE HOJE (aviso `HORAS_ATUAIS_NO_PASSADO`). Pessoas em ordem alfabética.
 */
@Serializable
data class TendenciaDto(val semanas: List<String>, val linhas: List<LinhaTendenciaDto>, val avisos: List<AvisoDto>)

/** [padrao] = `RECORRENTE` (3 ou mais das últimas 4 semanas acima do limite alto), `PONTUAL` (1 ou 2) ou `NORMAL`. */
@Serializable
data class LinhaTendenciaDto(val pessoa: String, val ocupacoes: List<OcupacaoDto>, val padrao: String)
