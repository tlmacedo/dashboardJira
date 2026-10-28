package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import kotlinx.serialization.Serializable

/** Códigos de aviso do Plano 17b (texto em `cardpage.aviso.<CODIGO>`); os da Previsão e do Tempo em status ficam em [AvisoDto]. */
object AvisosDoFluxo {
    /** Poucas tasks na janela do cartão (contagem, não percentil): o número aparece com o aviso. */
    const val AMOSTRA_PEQUENA_NA_JANELA = "AMOSTRA_PEQUENA_NA_JANELA"

    /** O fluxo não tem a ordem das etapas em `status_map.yaml`: as etapas saem em ordem alfabética. */
    const val ORDEM_DO_FLUXO_AUSENTE = AvisoDto.ORDEM_DO_FLUXO_AUSENTE

    /** Bloqueio por link cuja outra ponta não está na base (fora da ficha ou de outro projeto). */
    const val BLOQUEIO_FORA_DA_BASE = "BLOQUEIO_FORA_DA_BASE"

    /** O tipo de link configurado não tem nenhum vínculo gravado: os vínculos ainda não foram sincronizados. */
    const val VINCULOS_INDISPONIVEIS = "VINCULOS_INDISPONIVEIS"
}

/**
 * Uma semana (segunda a domingo de Manaus) do fluxo. [emCurso]: a semana de hoje, ainda incompleta (não entra no sinal
 * de backlog crescendo). Cada número abre, pelo drill, as MESMAS tasks (segmentos `entrada:<segunda>`, `inicio:<segunda>`,
 * `saida:<segunda>` e `cancelada:<segunda>`). Sem nenhum dado por pessoa.
 */
@Serializable
data class SemanaDeFluxoDto(
    val semana: String,
    val entrou: Int,
    val comecou: Int,
    val saiu: Int,
    val canceladas: Int,
    val emCurso: Boolean,
)

/**
 * Resposta do card Fluxo da semana (FX-01 e FX-02): entrada (por `created`), início (primeira entrada em status ativo),
 * saída (conclusão positiva) e canceladas à parte, nas últimas [semanasNaJanela] semanas. As séries vêm da `SerieSemanal`,
 * a mesma função da Previsão. [saldoDoBacklog] = entrou - saiu - canceladas (pode ser negativo). [semanasCrescendo]
 * conta as semanas FECHADAS seguidas, até a última, em que a entrada superou a saída; com 3 ou mais [backlogCrescendo].
 * Os totais abrem os segmentos `entradas`, `iniciadas`, `saidas` e `canceladas`. [tasksNaJanela] é o `n` do cartão.
 */
@Serializable
data class FluxoDaSemanaDto(
    val semanas: List<SemanaDeFluxoDto>,
    val entrou: Int,
    val comecou: Int,
    val saiu: Int,
    val canceladas: Int,
    val saldoDoBacklog: Int,
    val semanasCrescendo: Int,
    val backlogCrescendo: Boolean,
    val semanasNaJanela: Int,
    val tasksNaJanela: Int,
    val avisos: List<AvisoDto>,
)
