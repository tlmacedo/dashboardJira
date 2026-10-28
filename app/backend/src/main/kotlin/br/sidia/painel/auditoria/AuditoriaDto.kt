package br.sidia.painel.auditoria

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Aviso fixo de toda resposta de auditoria: o que aparece é indício para conferir, nunca prova (spec 7.3). */
const val AVISO_INDICIO_NAO_PROVA = "INDICIO_NAO_PROVA"

/**
 * Resposta de `GET /api/auditoria/sinais`. Só coordenação e gerente recebem. Não há lista nem contagem por pessoa:
 * [sinais] é ordenada por data e chave, e [porSinal] conta por tipo de sinal.
 */
@Serializable
data class AuditoriaDto(
    val aviso: String,
    val versaoRegras: String,
    val total: Int,
    val porSinal: List<ContagemSinalDto>,
    val sinais: List<SinalDto>,
    val noPrazo: NoPrazoDto,
    val avisos: List<AvisoDto>,
)

/** [resultado] é o habitual do código (`auditoria` ou `alerta`); o de cada sinal vem em [SinalDto.resultado]. */
@Serializable
data class ContagemSinalDto(val codigo: String, val resultado: String, val gravidade: Int, val n: Int)

/**
 * Um sinal. [resumo] é o título da issue; [motivo] é o nome de `MotivoAutorizacao`; [emLote] (I-15) é o tamanho do lote
 * quando a mesma pessoa mexeu no mesmo campo, no mesmo minuto, em N issues ou mais (contexto, não prova).
 */
@Serializable
data class SinalDto(
    val codigo: String,
    val resultado: String,
    val gravidade: Int,
    val key: String,
    val resumo: String,
    val campo: String,
    val de: String?,
    val para: String?,
    val at: String,
    val dia: String,
    val autor: String?,
    val autorizado: Boolean,
    val motivo: String,
    val responsavel: String?,
    val emLote: Int? = null,
)

/** "No prazo" e "no prazo ajustado" lado a lado; as taxas são fração de 0 a 1 (nulas sem concluídas). */
@Serializable
data class NoPrazoDto(
    val concluidas: Int,
    val noPrazo: Int,
    val noPrazoAjustado: Int,
    val justificadasSobAuditoria: Int,
    val taxa: Double?,
    val taxaAjustada: Double?,
)

/** Histórico campo a campo de uma issue. Campo fora da lista de permissão sai sem valor ([LinhaHistoricoDto.valorGuardado] falso). */
@Serializable
data class HistoricoDto(
    val aviso: String,
    val key: String,
    val resumo: String,
    val reporter: String?,
    val linhas: List<LinhaHistoricoDto>,
    val avisos: List<AvisoDto>,
)

@Serializable
data class LinhaHistoricoDto(
    val at: String,
    val campo: String,
    val de: String?,
    val para: String?,
    val autor: String?,
    val autorizado: Boolean?,
    val motivo: String?,
    val valorGuardado: Boolean,
)

/** Card Task × Review: [percentual] em pontos percentuais (25.0 = 25%), nulo sem pares; [divergencias] é a lista de pares divergentes. */
@Serializable
data class TaskReviewDto(
    val pares: Int,
    val divergentes: Int,
    val percentual: Double?,
    val avisos: List<AvisoDto>,
    val divergencias: List<TaskReviewLinhaDto> = emptyList(),
)

/** [urlTask] e [urlReview] são links diretos para a issue no Jira (montados no backend, só com a flag ligada); nulos sem URL. */
@Serializable
data class TaskReviewLinhaDto(
    val task: String,
    val review: String,
    val vencimentoTask: String?,
    val vencimentoReview: String?,
    val responsavelTask: String?,
    val responsavelReview: String?,
    val divergente: Boolean,
    val urlTask: String?,
    val urlReview: String?,
)
