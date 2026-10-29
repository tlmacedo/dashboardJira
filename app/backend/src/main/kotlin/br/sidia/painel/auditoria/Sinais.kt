package br.sidia.painel.auditoria

import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.sync.ChangeEventRecord
import br.sidia.painel.time.BusinessCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Os sinais de auditoria da spec 7.2 (11 da Fase 4 e os do Plano 11; Integridade é da Fase 7). O código é o
 * identificador estável: aparece no YAML de regras, nas respostas da API e no documento de diferenças.
 */
enum class CodigoSinal {
    VENCIMENTO_ALTERADO_NAO_AUTORIZADO,
    VENCIMENTO_REMOVIDO,
    VENCIMENTO_TASK_REVIEW_DIVERGENTE,
    RESPONSAVEL_TROCADO_NAO_AUTORIZADO,
    PASSA_BOLA,
    TROCA_PERTO_DO_VENCIMENTO,
    LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO,
    LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO,
    LABEL_TARDIA_COM_POSSE_ANTECIPADA,
    LABEL_APOS_VENCIMENTO,
    CANCELAMENTO_SUSPEITO,
    /** Estimativa original reduzida de forma relevante depois de o trabalho começar (spec 7.2; Plano 11). */
    ESTIMATIVA_REDUZIDA,
    /** Horas registradas com atraso (dias úteis de Manaus) em relação ao início declarado, ou depois da resolução (Plano 11). */
    WORKLOG_RETROATIVO,
}

private const val MAX_CORRIDOS = 1_000_000L

/** Tamanho máximo de texto ilegível guardado em [Sinal.de] / [Sinal.para]. */
internal const val LIMITE_TEXTO_SINAL = 64

/** O que o sinal significa na tela: auditoria (indício de irregularidade) ou alerta (informativo). */
enum class Resultado { AUDITORIA, ALERTA }

/**
 * Um achado da auditoria, ligado a um evento do changelog ([changelogId]). Indício, não prova. [autor] fica
 * como veio do changelog (inclusive `JIRAUSER*` não resolvido) e [responsavel] é a referência usada na avaliação.
 */
data class Sinal(
    val codigo: CodigoSinal,
    val resultado: Resultado,
    val gravidade: Int,
    val issueKey: String,
    val campo: String,
    val de: String?,
    val para: String?,
    val at: Instant,
    val autor: String?,
    val autorizado: Boolean,
    val motivo: MotivoAutorizacao,
    val responsavel: String?,
    val changelogId: String,
    /** Alteração em lote (I-15): quantas issues a mesma pessoa mexeu, no mesmo campo e minuto; nulo abaixo do mínimo. Contexto, não prova. */
    val emLote: Int? = null,
)

/** Tudo o que um [Detector] precisa além da linha do tempo da issue. */
data class ContextoDeteccao(
    val regras: RegrasDeAuditoria,
    val labels: LabelsDeAtraso,
    val autorizacao: Autorizacao,
    val calendario: BusinessCalendar,
    val statusMap: StatusMap,
) {
    /** Dia civil (fuso do calendário) de um instante. */
    fun dia(at: Instant): LocalDate = calendario.localDate(at)

    /**
     * Distância assinada de [dia] até [vencimento]: positiva quando o dia é anterior ao vencimento, negativa
     * quando é posterior. `CORRIDO` conta dias do calendário (exato, O(1)); `UTIL` conta os dias úteis entre
     * os dois (exclui o primeiro dia, inclui o último) e SATURA em `pertoDoVencimentoDias + 1`: a contagem
     * para assim que passa do limite, então o custo é limitado mesmo com datas distantes. Dois dias seguidos
     * não úteis (sábado e vencimento na sexta anterior) dão distância 0 e contam como "perto".
     */
    fun distanciaDias(dia: LocalDate, vencimento: LocalDate): Int = when (regras.unidade) {
        UnidadeDias.CORRIDO -> ChronoUnit.DAYS.between(dia, vencimento).coerceIn(-MAX_CORRIDOS, MAX_CORRIDOS).toInt()
        UnidadeDias.UTIL -> {
            val teto = regras.pertoDoVencimentoDias + 1
            val inicio = minOf(dia, vencimento)
            val fim = maxOf(dia, vencimento)
            var uteis = 0
            var d = inicio.plusDays(1)
            while (!d.isAfter(fim) && uteis < teto) {
                if (calendario.isBusinessDay(d)) uteis++
                d = d.plusDays(1)
            }
            if (dia <= vencimento) uteis else -uteis
        }
    }

    /**
     * O dia que fica [dias] antes de [vencimento] na unidade das regras: `CORRIDO` subtrai dias do calendário;
     * `UTIL` volta [dias] dias úteis (o limite de [dias] é 30, então o laço é curto).
     */
    fun diaAntes(vencimento: LocalDate, dias: Int): LocalDate = when (regras.unidade) {
        UnidadeDias.CORRIDO -> vencimento.minusDays(dias.toLong())
        UnidadeDias.UTIL -> {
            var d = vencimento
            var faltam = dias
            while (faltam > 0) {
                d = d.minusDays(1)
                if (calendario.isBusinessDay(d)) faltam--
            }
            d
        }
    }

    /** `|distância| <= pertoDoVencimentoDias`. */
    fun perto(dia: LocalDate, vencimento: LocalDate): Boolean =
        abs(distanciaDias(dia, vencimento)) <= regras.pertoDoVencimentoDias

    /**
     * Monta o sinal de [codigo] para o evento [e], ou nulo se a regra estiver inativa. Gravidade vem das regras;
     * a avaliação de autorização usa [referencia] como responsável de referência.
     */
    fun sinal(
        codigo: CodigoSinal,
        t: AuditTimeline,
        e: ChangeEventRecord,
        referencia: String?,
        resultado: Resultado = Resultado.AUDITORIA,
        de: String? = e.from,
        para: String? = e.to,
    ): Sinal? {
        val regra = regras.sinais[codigo]?.takeIf { it.ativo } ?: return null
        val avaliacao = autorizacao.avaliar(e.authorRef, t.issue.reporter, referencia, e.at)
        return Sinal(
            codigo, resultado, regra.gravidade, t.issue.key, e.field, de, para, e.at, e.authorRef,
            avaliacao.autorizado, avaliacao.motivo, referencia, e.changelogId,
        )
    }
}

/** Um detector puro: transforma a linha do tempo de uma issue em sinais. */
fun interface Detector {
    fun detectar(t: AuditTimeline, ctx: ContextoDeteccao): List<Sinal>
}
