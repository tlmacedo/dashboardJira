package br.sidia.painel.cards.epicos

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta do card Épicos (EP-01). Segmentos do drill: `epico:<id>:itens`, `epico:<id>:concluidos` e
 * `epico:<id>:vencidos`, em que `<id>` é o [LinhaEpicoDto.id] (a POSIÇÃO do épico na lista ordenada por chave, sem nada derivado da chave, para a chave nunca
 * ir para o `access_log`). Itens = tasks do épico (sem os tipos de review e sem as canceladas); [minItens] é o mínimo de
 * entregas do PRÓPRIO épico no histórico para ele ter previsão.
 */
@Serializable
data class EpicosDto(
    val linhas: List<LinhaEpicoDto>,
    val minItens: Int,
    val avisos: List<AvisoDto>,
)

/**
 * Uma linha por épico, em ordem de chave. [progresso] = concluídos ÷ itens (fração de 0 a 1, seis casas). [maisAntigo] é a
 * data de criação da task aberta mais antiga. [previsaoSemanasP85] é nulo ("—") sem amostra própria: a previsão do épico
 * usa só a vazão dele, nunca a do time.
 */
@Serializable
data class LinhaEpicoDto(
    val id: String,
    val epico: String,
    val itens: Int,
    val concluidos: Int,
    val progresso: Double?,
    val maisAntigo: String?,
    val vencidos: Int,
    val pontos: Double?,
    val previsaoSemanasP85: Int?,
)
