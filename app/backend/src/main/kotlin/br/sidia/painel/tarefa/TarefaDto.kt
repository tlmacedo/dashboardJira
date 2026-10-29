package br.sidia.painel.tarefa

import br.sidia.painel.auditoria.LinhaHistoricoDto
import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * A gaveta universal da task (`GET /api/tarefa/{key}`, Plano 10, T5): tudo que o painel sabe de UMA issue, na visão de
 * quem pergunta. [responsavel] e as pessoas de [posse] viram nulo ("outra pessoa") quando estão fora da visão. [historico]
 * só vem para COORDENADOR e GERENTE (nulo para os demais). [linkJira] só existe com a flag do Jira ligada e a instância
 * configurada: é um texto de URL montado da configuração, nunca uma requisição.
 */
@Serializable
data class TarefaDto(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val categoria: String,
    val resolucao: String?,
    val responsavel: String?,
    val prioridade: String?,
    val criada: String,
    val atualizada: String?,
    val resolvida: String?,
    val vencimento: String?,
    val vencimentoOriginal: String?,
    val labels: List<String>,
    val campos: Map<String, String>,
    val tempoPorStatus: List<TempoStatusDto>,
    val posse: List<FracaoPosseDto>,
    val fatoresDeRisco: List<String>,
    val historico: List<LinhaHistoricoDto>?,
    val linkJira: String?,
    val avisos: List<AvisoDto>,
)

/** Dias (úteis, a unidade padrão) que a task ficou num status, somando todas as passagens por ele. */
@Serializable
data class TempoStatusDto(val status: String, val dias: Double)

/**
 * Fatia da posse da task. [pessoa] nula com [semResponsavel] falso = "outra pessoa" (fora da visão, sem nome); nula com
 * [semResponsavel] verdadeiro = o tempo em que a task não tinha responsável.
 */
@Serializable
data class FracaoPosseDto(val pessoa: String?, val semResponsavel: Boolean, val dias: Double, val fracao: Double)
