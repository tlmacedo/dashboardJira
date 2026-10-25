package br.sidia.painel.cards

import br.sidia.painel.domain.PersonStatus
import br.sidia.painel.domain.StatusStat
import br.sidia.painel.domain.StuckIssue
import kotlinx.serialization.Serializable

/**
 * Resposta do card "Tempo em status". Só carrega o que o papel de quem pediu pode ver:
 * nomes de pessoas fora da visibilidade nunca entram em [pessoaStatus] nem em [abertasParadas].
 * [unit] é `util` ou `corrido`; [avisos] traz problemas de dados (ex.: status sem mapeamento).
 */
@Serializable
data class TempoEmStatusDto(
    val analisadas: Int,
    val eficienciaFluxo: Double?,
    val paradas: Int,
    val porStatus: List<StatusStat>,
    val pessoaStatus: List<PersonStatus>,
    val abertasParadas: List<StuckIssue>,
    val unit: String,
    val avisos: List<String>,
)

/** Linha do detalhamento (drill). [responsavel] é nulo quando a pessoa está fora da visibilidade. */
@Serializable
data class DrillRow(
    val key: String,
    val resumo: String,
    val responsavel: String?,
    val status: String,
    val detalhe: String,
)

/** Quem está logado, com os papéis do diretório na data de hoje (nunca de claims). */
@Serializable
data class MeDto(val userId: String, val roles: List<String>)
