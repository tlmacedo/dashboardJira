package br.sidia.painel.cards

import br.sidia.painel.domain.PersonStatus
import br.sidia.painel.domain.StatusStat
import br.sidia.painel.domain.StuckIssue
import kotlinx.serialization.Serializable

/**
 * Resposta do card "Tempo em status". Só carrega o que o papel de quem pediu pode ver:
 * nomes de pessoas fora da visibilidade nunca entram em [pessoaStatus] nem em [abertasParadas].
 * [unit] é `util` ou `corrido`; [avisos] traz problemas de dados, cada um com código estável e gravidade;
 * só os `bloqueante` tornam os números indisponíveis (ver [AvisoDto]).
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
    val avisos: List<AvisoDto>,
)

/**
 * Aviso de dados do card. [codigo] é estável (o frontend traduz por ele, não pela [mensagem]);
 * [gravidade] é `bloqueante` (números indisponíveis) ou `informativo` (números continuam válidos).
 * [params] leva os dados do aviso (nome do status, contagem) para o frontend montar o texto pelo código, sem
 * extrair nada da [mensagem]: `STATUS_SEM_MAPEAMENTO` traz `status` e `CONCLUSAO_DIVERGENTE` traz `n`.
 */
@Serializable
data class AvisoDto(
    val codigo: String,
    val gravidade: String,
    val mensagem: String,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        const val STATUS_SEM_MAPEAMENTO = "STATUS_SEM_MAPEAMENTO"
        /** Status concluído sem `resolution`, ou `resolution` sem status concluído (spec 4.2: Qualidade dos dados). */
        const val CONCLUSAO_DIVERGENTE = "CONCLUSAO_DIVERGENTE"
        /** Status concluído e `resolution` preenchida, mas sem data de resolução: fica fora dos números (informativo). */
        const val CONCLUIDA_SEM_DATA = "CONCLUIDA_SEM_DATA"
        const val CALCULO_INDISPONIVEL = "CALCULO_INDISPONIVEL"
        const val DATAS_INCONSISTENTES = "DATAS_INCONSISTENTES"
        const val OS_INDISPONIVEL = "OS_INDISPONIVEL"
        const val BLOQUEANTE = "bloqueante"
        const val INFORMATIVO = "informativo"
    }
}

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
