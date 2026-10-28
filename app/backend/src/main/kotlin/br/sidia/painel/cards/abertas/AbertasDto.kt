package br.sidia.painel.cards.abertas

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Abertas. Cada contagem abre, pelo drill genérico, as MESMAS tasks que a formam (segmentos:
 * `abertas`, `emAndamento`, `acimaDoP85`, `status:<nome>`, `prioridade:<nome>`, `idade:<faixa>`, `vence:<faixa>`,
 * `historico:<tipo>`). [unit] é `util` ou `corrido`. [porPrioridade] vem vazio (com aviso) enquanto nenhuma issue da base
 * tem prioridade sincronizada.
 */
@Serializable
data class AbertasDto(
    val abertas: Int,
    val emAndamento: Int,
    val acimaDoP85: Int,
    val historicoPorTipo: List<FaixaTipoDto>,
    val porStatus: List<ContagemDto>,
    val porPrioridade: List<ContagemDto>,
    val porIdade: List<ContagemDto>,
    val porVencimento: List<ContagemDto>,
    val linhas: List<LinhaAbertaDto>,
    val unit: String,
    val avisos: List<AvisoDto>,
)

@Serializable
data class FaixaTipoDto(val tipo: String, val p50: Double, val p85: Double, val n: Int)

@Serializable
data class ContagemDto(val chave: String, val n: Int)

/** Uma issue aberta na tabela. [responsavel] é nulo quando a pessoa está fora da visão de quem pediu. */
@Serializable
data class LinhaAbertaDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val prioridade: String?,
    val responsavel: String?,
    val idade: Double,
    val vence: String?,
    val horasRestantes: Double?,
)
