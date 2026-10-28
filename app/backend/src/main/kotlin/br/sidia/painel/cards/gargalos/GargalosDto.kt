package br.sidia.painel.cards.gargalos

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Resposta de Gargalos e fluxo (GA-01 a GA-04), só para a liderança. [dias] são os 60 últimos dias de Manaus (o último é
 * hoje) e [faixas] o fluxo cumulativo reconstruído do changelog: quantas issues estavam em cada status no fim de cada dia,
 * na ordem do fluxo (o status concluído fica de fora). [etapas] diz onde há gargalo. [acimaDaCapacidade] e [comFolga]
 * contam PESSOAS (sem ranking); as tasks na fila delas são o que a gaveta abre. Segmentos do drill: `status:<nome>` (as
 * tasks que estão hoje naquele status), `acimaDaCapacidade` e `comFolga`.
 */
@Serializable
data class GargalosDto(
    val dias: List<String>,
    val faixas: List<FaixaFluxoDto>,
    val etapas: List<EtapaDto>,
    val acimaDaCapacidade: Int,
    val comFolga: Int,
    val tasksAcimaDaCapacidade: Int,
    val tasksComFolga: Int,
    val avisos: List<AvisoDto>,
)

/** [contagens] tem uma posição por dia de [GargalosDto.dias]. */
@Serializable
data class FaixaFluxoDto(val status: String, val contagens: List<Int>)

/**
 * [wip] = tasks hoje no status; [limite] = o limite do `planejamento.yaml` (nulo se não há); [crescimento15d] = diferença
 * da faixa contra 15 dias atrás. [gargalo] só vale para etapa ativa ou de espera; [motivo] = `WIP_ACIMA_DO_LIMITE` ou
 * `FAIXA_CRESCEU` (nulo sem gargalo).
 */
@Serializable
data class EtapaDto(
    val status: String,
    val categoria: String,
    val wip: Int,
    val limite: Int?,
    val crescimento15d: Int,
    val gargalo: Boolean,
    val motivo: String?,
)
