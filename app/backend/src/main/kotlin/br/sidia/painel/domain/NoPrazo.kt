package br.sidia.painel.domain

import br.sidia.painel.auditoria.AuditIssue
import br.sidia.painel.auditoria.CodigoSinal
import br.sidia.painel.auditoria.Sinal
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Resultado de "no prazo" e "no prazo ajustado" (spec 4.2). As taxas são fração de 0 a 1 (0.75 = 75%), nulas sem
 * concluídas. [justificadasSobAuditoria]: atrasadas que só entram no ajustado por uma label que tem sinal de label
 * na auditoria; elas continuam contando no ajustado (decisão do usuário), o contador só mostra o que está em dúvida.
 */
data class NoPrazoResultado(val concluidas: Int, val noPrazo: Int, val noPrazoAjustado: Int, val justificadasSobAuditoria: Int) {
    val taxa: Double? get() = if (concluidas == 0) null else noPrazo.toDouble() / concluidas
    val taxaAjustada: Double? get() = if (concluidas == 0) null else noPrazoAjustado.toDouble() / concluidas
}

/** As issues por trás de cada número de [NoPrazoResultado] (para a gaveta de tasks do cartão). */
data class NoPrazoDetalhe(
    val concluidas: List<AuditIssue>,
    val noPrazo: List<AuditIssue>,
    val noPrazoAjustado: List<AuditIssue>,
    val justificadasSobAuditoria: List<AuditIssue>,
) {
    val resultado: NoPrazoResultado
        get() = NoPrazoResultado(concluidas.size, noPrazo.size, noPrazoAjustado.size, justificadasSobAuditoria.size)
}

/** Faixa do dia da resolução em relação ao vencimento (Δ = dia de Manaus da resolução − vencimento). */
enum class FaixaDePrazo { D3_OU_ANTES, D2, D1, D, ATRASO_1_3, ATRASO_MAIS_3 }

/** [delta] em dias; [noPrazo] = Δ ≤ 0; [ajustado] = no prazo ou com label que justifica o atraso. */
data class ClassePrazo(val delta: Int, val faixa: FaixaDePrazo, val noPrazo: Boolean, val ajustado: Boolean)

/**
 * Classifica UMA task pelo dia (de Manaus) da resolução e pelo vencimento. É a única conta de "no prazo" do painel:
 * Auditoria, Fechadas e Metas chamam esta função (direta ou por [NoPrazo.detalhar]).
 */
fun NoPrazo.classificar(resolvidaEm: LocalDate, vencimento: LocalDate, labels: List<String>, regras: LabelsDeAtraso): ClassePrazo {
    val delta = ChronoUnit.DAYS.between(vencimento, resolvidaEm).toInt()
    val faixa = when {
        delta <= -3 -> FaixaDePrazo.D3_OU_ANTES
        delta == -2 -> FaixaDePrazo.D2
        delta == -1 -> FaixaDePrazo.D1
        delta == 0 -> FaixaDePrazo.D
        delta <= 3 -> FaixaDePrazo.ATRASO_1_3
        else -> FaixaDePrazo.ATRASO_MAIS_3
    }
    val noPrazo = delta <= 0
    return ClassePrazo(delta, faixa, noPrazo, noPrazo || labels.any(regras::justifica))
}

object NoPrazo {
    /**
     * O UNIVERSO de "no prazo", único para Auditoria, Fechadas e Metas: categoria DONE E `resolution` preenchida E
     * `resolved` preenchido E resolução fora das negativas (cancelada fica fora) E tipo diferente de `Epic` E vencimento
     * (dia) em [[de], [ate]]. Quem chama passa as issues com vencimento no período, não "as resolvidas no período": uma
     * issue resolvida em 28/09 que vence em 05/10 entra em outubro nas três telas. Status sem mapeamento não conta.
     */
    @Suppress("UNUSED_PARAMETER")
    fun noUniverso(i: AuditIssue, map: StatusMap, zone: ZoneId, de: LocalDate, ate: LocalDate): Boolean {
        val venc = i.duedate ?: return false
        if (i.resolved == null || venc.isBefore(de) || venc.isAfter(ate)) return false
        val resolution = i.resolution
        if (resolution.isNullOrBlank() || map.ehResolucaoNegativa(resolution)) return false
        if (i.issueType.trim().equals("Epic", ignoreCase = true)) return false
        return ehDone(map, i.status)
    }

    private val CODIGOS_DE_LABEL = setOf(
        CodigoSinal.LABEL_APOS_FECHAMENTO_NAO_AUTORIZADO,
        CodigoSinal.LABEL_PELO_RESPONSAVEL_APOS_FECHAMENTO,
        CodigoSinal.LABEL_TARDIA_COM_POSSE_ANTECIPADA,
        CodigoSinal.LABEL_APOS_VENCIMENTO,
    )

    fun calcular(
        issues: List<AuditIssue>, labels: LabelsDeAtraso, statusMap: StatusMap, zone: ZoneId,
        de: LocalDate, ate: LocalDate, sinais: List<Sinal> = emptyList(),
    ): NoPrazoResultado = detalhar(issues, labels, statusMap, zone, de, ate, sinais).resultado

    /**
     * Universo: concluídas (categoria `DONE` E `resolution`, com data de resolução) com vencimento em [de, ate]. Status
     * sem mapeamento não conta como concluída. No prazo: dia de [zone] de `resolved` até o vencimento. Ajustado: no
     * prazo OU alguma label atual que justifica.
     */
    fun detalhar(
        issues: List<AuditIssue>, labels: LabelsDeAtraso, statusMap: StatusMap, zone: ZoneId,
        de: LocalDate, ate: LocalDate, sinais: List<Sinal> = emptyList(),
    ): NoPrazoDetalhe {
        val labelsComSinal: Map<String, Set<String>> = sinais
            .filter { it.codigo in CODIGOS_DE_LABEL && it.para != null }
            .groupBy({ it.issueKey }, { it.para!!.lowercase(Locale.ROOT) })
            .mapValues { it.value.toSet() }

        val concluidas = issues.filter { noUniverso(it, statusMap, zone, de, ate) }
        val classes = concluidas.associate { i ->
            i.key to classificar(requireNotNull(i.resolved).atZone(zone).toLocalDate(), requireNotNull(i.duedate), i.labels, labels)
        }
        val noPrazo = concluidas.filter { classes.getValue(it.key).noPrazo }
        val chavesNoPrazo = noPrazo.map { it.key }.toSet()
        val ajustadas = concluidas.filter { classes.getValue(it.key).ajustado }
        val sobAuditoria = ajustadas.filter { i ->
            i.key !in chavesNoPrazo && i.labels.any { l ->
                labels.justifica(l) && l.lowercase(Locale.ROOT) in labelsComSinal[i.key].orEmpty()
            }
        }
        return NoPrazoDetalhe(concluidas, noPrazo, ajustadas, sobAuditoria)
    }

    private fun ehDone(statusMap: StatusMap, status: String): Boolean =
        try {
            statusMap.category(status) == StatusCategory.DONE
        } catch (_: UnmappedStatusException) {
            false
        }
}
