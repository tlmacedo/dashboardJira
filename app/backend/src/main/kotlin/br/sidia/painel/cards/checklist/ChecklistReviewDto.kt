package br.sidia.painel.cards.checklist

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Códigos dos avisos próprios do Checklist × Review (o texto sai do código, no frontend). */
object AvisosDoChecklistReview {
    /** Menos pares que a amostra mínima do `checklist_review.yaml`: os números são instáveis e nenhuma frase de aprendizado sai. */
    const val PARES_EM_AMOSTRA_PEQUENA = "PARES_EM_AMOSTRA_PEQUENA"

    /** A lista de pares da resposta foi cortada (a contagem e a gaveta trazem todos). */
    const val LISTA_PARCIAL = "LISTA_PARCIAL"
}

/**
 * Resposta do card Checklist × Review. [versaoRegras] é o rótulo das regras (`checklist-review v1`). Nada aqui compara
 * pessoas: [pares] traz os responsáveis só quando estão na visão de quem pediu.
 */
@Serializable
data class ChecklistReviewDto(
    val versaoRegras: String,
    val resumo: ResumoCRDto,
    val pares: List<ParCRDto>,
    val semReview: List<SemReviewDto>,
    val avisos: List<AvisoDto>,
    /** `util` ou `corrido`: a unidade do ciclo. */
    val unit: String = "util",
)

/** Percentuais são FRAÇÕES (0,25 = 25%) sobre o número de pares; nulos sem pares. */
@Serializable
data class ResumoCRDto(
    val pares: Int,
    val semReview: Int,
    /** Pares com a review concluída (a base do ciclo e das faixas). */
    val concluidas: Int,
    val comAlerta: Int,
    val porAlerta: Map<String, Int>,
    val cicloReviewP50: Double?,
    val razaoReviewPaiP50: Double?,
    val criadaAposPaiHorasP50: Double?,
    val venceDepoisPct: Double?,
    val mesmaPessoaPct: Double?,
    val comAlertaPct: Double?,
    val duracaoXReabertura: List<FaixaDuracaoDto>,
    val labels: List<LiftDeLabelDto>,
    val diferencasDeLabels: List<DiferencaDeLabelDto>,
    val atrasosEmCadeia: Int,
    val aprendizados: List<AprendizadoDto>,
    val amostraMinima: Int,
    val limiteVenceDepois: Double,
    val limiteMesmaPessoa: Double,
    val limiteParesComAlerta: Double,
)

@Serializable
data class FaixaDuracaoDto(val faixa: String, val n: Int, val taxaReabertura: Double?, val nBaixo: Boolean)

@Serializable
data class LiftDeLabelDto(val label: String, val n: Int, val taxaReabertura: Double, val lift: Double?, val nBaixo: Boolean)

/** [codigo] é `pai:<label>` (só no pai) ou `review:<label>` (só na review). */
@Serializable
data class DiferencaDeLabelDto(val codigo: String, val pares: Int)

/** Uma frase de aprendizado: o [codigo] escolhe o texto (traduzido no frontend) e [params] preenche os marcadores. */
@Serializable
data class AprendizadoDto(val codigo: String, val params: Map<String, String> = emptyMap())

/**
 * Um par. Responsáveis só se estiverem na visão; as datas são dias de Manaus (aaaa-mm-dd). [horasAtivasReview] são
 * horas corridas em status ativo.
 */
@Serializable
data class ParCRDto(
    val pai: String,
    val review: String,
    val resumoPai: String,
    val resumoReview: String,
    val responsavelPai: String?,
    val responsavelReview: String?,
    val statusPai: String,
    val statusReview: String,
    val vencePai: String?,
    val venceReview: String?,
    val criacaoPai: String,
    val criacaoReview: String,
    val resolvidoPai: String?,
    val resolvidoReview: String?,
    val horasAtivasReview: Double?,
    val paiReaberto: Boolean,
    val alertas: List<String>,
)

@Serializable
data class SemReviewDto(val key: String, val resumo: String, val responsavel: String?, val status: String)
