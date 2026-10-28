package br.sidia.painel.cards.negligenciadas

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Negligenciadas. As seis contagens abrem, pelo drill genérico (segmento = o nome da lista em
 * [listas]), as MESMAS tasks que as formam. Cada lista leva no máximo `LIMITE_DA_LISTA` linhas; a contagem é sempre o
 * total (e a gaveta abre todas). [diasSemAtualizacao] é o limiar de "sem atualização" e de "sem movimento real".
 */
@Serializable
data class NegligenciadasDto(
    val semAtualizacao: Int,
    val semResponsavel: Int,
    val vencidas: Int,
    val bloqueadas: Int,
    val paradasNoStatus: Int,
    val aFazerAntigas: Int,
    val listas: Map<String, List<LinhaNegligenciadaDto>>,
    val diasSemAtualizacao: Int,
    val semMovimentoReal: Boolean,
    val unit: String,
    val avisos: List<AvisoDto>,
)

/**
 * Uma issue de uma das listas. [dias] é a medida da lista: dias desde a última atualização (`semAtualizacao`), desde a
 * criação (`semResponsavel`), de atraso (`vencidas`), no status atual na unidade pedida (`bloqueadas`, `paradas`) ou
 * corridos no status (`aFazer`). [responsavel] nunca é uma pessoa fora da visão de quem pediu.
 */
@Serializable
data class LinhaNegligenciadaDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val responsavel: String?,
    val vence: String?,
    val labels: List<String>,
    val dias: Double,
    val ultimoMovimento: UltimoMovimentoDto,
    val semMovimentoReal: Boolean,
)

/** Último movimento real (I-06): o campo que mudou (`criacao` se nunca mudou), o dia em Manaus e os dias corridos desde então. */
@Serializable
data class UltimoMovimentoDto(val campo: String, val dia: String, val dias: Double)
