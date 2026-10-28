package br.sidia.painel.cards.posse

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant

/** O tempo que [pessoa] (nulo = sem responsável) ficou com a task, a fração do total e em quantos trechos seguidos. */
data class PosseDePessoa(val pessoa: String?, val dias: Double, val fracao: Double, val trechos: Int)

/**
 * A posse de uma task inteira: as pessoas, as passagens de responsável ([transferencias]: quantas vezes o responsável mudou,
 * inclusive de e para "ninguém") e, por pessoa, quantas passagens ela [recebidas] de outra e [repassadas] a outra.
 */
class PosseDaTask(
    val posse: List<PosseDePessoa>,
    val transferencias: Int,
    val recebidas: Map<String, Int>,
    val repassadas: Map<String, Int>,
)

/**
 * Posse da task (spec mestra 6.3): cada pessoa responde pelo tempo em que ficou com a task, e as métricas por pessoa usam
 * essa fração. Parte dos intervalos de [TimelineBuilder.intervals] (status e responsável), fora dos status de categoria
 * `DONE`. Domínio puro: relógio e calendário entram por parâmetro.
 */
object PosseCalculator {
    /** Um trecho: tempo seguido com a mesma pessoa (mudar de status não o interrompe; mudar de responsável ou passar por DONE, sim). */
    private class Trecho(val pessoa: String?, var dias: Double)

    /**
     * A posse de cada pessoa até [ate] (a resolução, se houver, vale antes disso) na unidade pedida, em ordem alfabética com
     * "sem responsável" (nulo) por último. Sem tempo nenhum, lista vazia; a soma das frações é 1,0 (ou tudo 0,0 quando o
     * tempo total na unidade pedida é zero, por exemplo só fim de semana).
     */
    fun posse(s: IssueSnapshot, ate: Instant, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode): List<PosseDePessoa> =
        detalhe(s, ate, map, cal, unit).posse

    fun detalhe(s: IssueSnapshot, ate: Instant, map: StatusMap, cal: BusinessCalendar, unit: TimeUnitMode): PosseDaTask {
        val trechos = mutableListOf<Trecho>()
        var passouPorDone = false
        for (iv in TimelineBuilder.intervals(s.timeline, ate, map)) {
            if (iv.category == StatusCategory.DONE) {
                passouPorDone = true
                continue
            }
            val dias = if (unit == TimeUnitMode.UTIL) cal.businessDays(iv.start, iv.end) else cal.calendarDays(iv.start, iv.end)
            val ultimo = trechos.lastOrNull()
            if (ultimo != null && ultimo.pessoa == iv.assignee && !passouPorDone) ultimo.dias += dias else trechos += Trecho(iv.assignee, dias)
            passouPorDone = false
        }
        val total = trechos.sumOf { it.dias }
        val posse = trechos.groupBy { it.pessoa }.map { (pessoa, lista) ->
            val dias = lista.sumOf { it.dias }
            PosseDePessoa(pessoa, dias, if (total > 0.0) dias / total else 0.0, lista.size)
        }.sortedWith(compareBy(nullsLast()) { it.pessoa })

        val recebidas = HashMap<String, Int>()
        val repassadas = HashMap<String, Int>()
        var transferencias = 0
        for ((antes, depois) in trechos.zipWithNext()) {
            if (antes.pessoa == depois.pessoa) continue
            transferencias++
            antes.pessoa?.let { repassadas.merge(it, 1, Int::plus) }
            depois.pessoa?.let { recebidas.merge(it, 1, Int::plus) }
        }
        return PosseDaTask(posse, transferencias, recebidas, repassadas)
    }
}
