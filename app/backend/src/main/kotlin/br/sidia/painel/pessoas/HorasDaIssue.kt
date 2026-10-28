package br.sidia.painel.pessoas

import br.sidia.painel.trabalho.IssueSnapshot
import java.util.Locale

/** Horas de trabalho que faltam numa issue. [assumidas] = vieram de uma regra (tipo ou padrão), não do Jira: a confiança cai. */
data class Horas(val valor: Double, val assumidas: Boolean)

/**
 * Quantas horas faltam numa issue, nesta ordem: (1) o restante do Jira; (2) a estimativa menos o registrado, no mínimo
 * `horasMinimas`; (3) as horas padrão do TIPO (YAML), assumidas; (4) `horasPadrao`, assumidas. Restante igual a zero não
 * informa (o Jira o zera ao estourar a estimativa e a task segue aberta) e cai para a regra seguinte.
 */
class HorasDaIssue(private val regras: RegrasDePlanejamento) {
    private val porTipo: Map<String, Double> = regras.horasPorTipo.mapKeys { it.key.trim().lowercase(Locale.ROOT) }

    fun de(s: IssueSnapshot): Horas {
        s.remainingS?.takeIf { it > 0 }?.let { return Horas(it / SEGUNDOS_POR_HORA, false) }
        s.estimateS?.takeIf { it > 0 }?.let { estimativa ->
            val sobra = (estimativa - (s.spentS ?: 0L)) / SEGUNDOS_POR_HORA
            return Horas(maxOf(regras.horasMinimas, sobra), false)
        }
        porTipo[s.issueType.trim().lowercase(Locale.ROOT)]?.let { return Horas(it, true) }
        return Horas(regras.horasPadrao, true)
    }

    private companion object {
        const val SEGUNDOS_POR_HORA = 3600.0
    }
}
