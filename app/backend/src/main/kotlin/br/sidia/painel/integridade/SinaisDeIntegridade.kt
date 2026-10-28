package br.sidia.painel.integridade

import br.sidia.painel.domain.StatusMap
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.ZoneId

/**
 * Os 12 sinais da Integridade (relatório 4.13). O nome é o identificador estável: aparece no YAML, na API, no catálogo
 * e no documento de diferenças. A ordem do enum é a ordem do catálogo. Um sinal é INDÍCIO para revisão humana, nunca prova.
 */
enum class SinalIntegridade {
    RESGATE_POR_PRORROGACAO,
    TROCA_ANTES_DE_FECHAR,
    CRIADA_E_FECHADA_RAPIDO,
    REABRE_E_FECHA_RAPIDO,
    FECHAMENTO_EM_LOTE,
    VENCIMENTO_PRORROGADO,
    VENCIMENTO_TARDIO,
    PULOU_O_FLUXO,
    ESTIMATIVA_AUMENTADA,
    LABEL_CRITICIDADE_REMOVIDA,
    FECHADA_POR_OUTRO,
    FECHADA_NO_LIMITE,
}

/**
 * Uma evidência: o [sinal] numa task ([issueKey]) num instante ([at]). [pessoa] é o AUTOR do evento do changelog que dispara
 * o sinal (decisão D3 da spec); sem autor, o responsável no instante; nulo se nenhum dos dois existe. O responsável no
 * instante vai em `detalhe["responsavel"]` quando conhecido. [detalhe] só leva números e datas, nunca texto livre do Jira.
 */
data class EvidenciaIntegridade(
    val sinal: SinalIntegridade,
    val gravidade: Int,
    val issueKey: String,
    val pessoa: String?,
    val at: Instant,
    val detalhe: Map<String, String>,
)

/** Tudo que um detector precisa além das issues e das regras: o mapa de status, o fuso do painel e o calendário. */
data class ContextoIntegridade(val map: StatusMap, val zone: ZoneId, val cal: BusinessCalendar)

/** Um detector puro: transforma as issues (com todos os eventos do changelog) em evidências, sem banco nem rede. */
fun interface DetectorIntegridade {
    fun detectar(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade, ctx: ContextoIntegridade): List<EvidenciaIntegridade>
}
