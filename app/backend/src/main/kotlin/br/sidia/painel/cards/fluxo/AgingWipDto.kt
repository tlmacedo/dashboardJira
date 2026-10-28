package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** P50 e P85 históricos do tempo que as concluídas passaram na etapa ([n] tasks). Nula quando [n] não chega à amostra mínima. */
@Serializable
data class FaixaDaEtapaDto(val p50: Double, val p85: Double, val n: Int)

/**
 * Um ponto do gráfico: UMA task em andamento na etapa. [idade] é o tempo desde a última entrada no status atual, na
 * unidade do card; [acima] é "acima do normal" (idade maior que o P85 da etapa, só quando há faixa). O ponto NÃO traz
 * pessoa: a pessoa só aparece na gaveta, pela visão do papel.
 */
@Serializable
data class PontoDeIdadeDto(val key: String, val idade: Double, val acima: Boolean)

/**
 * Uma etapa do fluxo (um status em andamento): [total] tasks agora, [acima] delas acima do P85, a [faixa] histórica
 * (nula sem amostra mínima), [historicoN] concluídas que passaram por ela e os [pontos] (mais velhos primeiro).
 */
@Serializable
data class EtapaDto(
    val status: String,
    val categoria: String,
    val total: Int,
    val acima: Int,
    val historicoN: Int,
    val faixa: FaixaDaEtapaDto?,
    val pontos: List<PontoDeIdadeDto>,
)

/**
 * Resposta do card Envelhecimento por etapa (AW-01 e AW-02). As [etapas] seguem a ordem do `status_map`; espera e
 * bloqueio são etapas próprias. Segmentos do drill: `etapa:<status>`, `acima:<status>`, `emAndamento` e `acimaDoNormal`.
 * [unit] é `util` ou `corrido`.
 */
@Serializable
data class AgingWipDto(
    val etapas: List<EtapaDto>,
    val emAndamento: Int,
    val acimaDoNormal: Int,
    val unit: String,
    val avisos: List<AvisoDto>,
)
