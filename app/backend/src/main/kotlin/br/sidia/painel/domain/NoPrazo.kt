package br.sidia.painel.domain

import br.sidia.painel.auditoria.AuditIssue
import br.sidia.painel.auditoria.CodigoSinal
import br.sidia.painel.auditoria.Sinal
import java.time.LocalDate
import java.time.ZoneId
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

object NoPrazo {
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

        val concluidas = issues.filter { i ->
            val venc = i.duedate
            val resolvida = i.resolved
            venc != null && resolvida != null && !venc.isBefore(de) && !venc.isAfter(ate) &&
                !i.resolution.isNullOrBlank() && ehDone(statusMap, i.status)
        }
        val noPrazo = concluidas.filter { !requireNotNull(it.resolved).atZone(zone).toLocalDate().isAfter(requireNotNull(it.duedate)) }
        val chavesNoPrazo = noPrazo.map { it.key }.toSet()
        val ajustadas = concluidas.filter { it.key in chavesNoPrazo || it.labels.any(labels::justifica) }
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
