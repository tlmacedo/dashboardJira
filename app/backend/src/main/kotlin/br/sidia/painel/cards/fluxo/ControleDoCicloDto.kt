package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/**
 * Um ponto do gráfico de controle: uma task concluída. [dia] é o dia de Manaus da conclusão, [ciclo] os dias de ciclo na
 * unidade do card e [mediaMovel] a média móvel centrada no ponto (nula sem pontos suficientes). [outlier]: ciclo acima do
 * limite em dias corridos. O ponto NÃO traz pessoa nem chave; o clique abre as tasks do dia e do tipo (segmento `dia:`).
 */
@Serializable
data class PontoDeCicloDto(val dia: String, val ciclo: Double, val mediaMovel: Double?, val outlier: Boolean)

/** Um mês da série de tendência de um tipo. [p50] e [p85] são nulos sem tasks; [amostraPequena]: há tasks, mas menos que o mínimo. */
@Serializable
data class MesDoCicloDto(val mes: String, val n: Int, val p50: Double?, val p85: Double?, val amostraPequena: Boolean)

/**
 * O ciclo de UM tipo (Checklist e Review nunca se misturam): os [pontos] do gráfico de controle, as faixas [p50] e [p85] do
 * tipo, a [janela] da média móvel (20% dos pontos, ímpar, mínimo 5; nula com menos de 5 pontos) e a série mensal [meses].
 */
@Serializable
data class SerieDoTipoDto(
    val tipo: String,
    val n: Int,
    val p50: Double?,
    val p85: Double?,
    val amostraPequena: Boolean,
    val janela: Int?,
    val pontos: List<PontoDeCicloDto>,
    val meses: List<MesDoCicloDto>,
)

/**
 * Resposta do card Controle do ciclo e tendência (CT-01 e CT-02). Segmentos do drill: `tipo:<tipo>`, `dia:<aaaa-mm-dd>:<tipo>`
 * e `mes:<aaaa-mm>:<tipo>`. Com [semOutliers], ciclos acima de [limiteOutlierDias] (dias corridos) ficam de fora e o total
 * excluído vai em [outliersExcluidos]. [de] e [ate] são o período (dias de Manaus, inclusivos). Nada por pessoa.
 */
@Serializable
data class ControleDoCicloDto(
    val series: List<SerieDoTipoDto>,
    val concluidas: Int,
    val semOutliers: Boolean,
    val limiteOutlierDias: Int,
    val outliersExcluidos: Int,
    val de: String,
    val ate: String,
    val unit: String,
    val avisos: List<AvisoDto>,
)
