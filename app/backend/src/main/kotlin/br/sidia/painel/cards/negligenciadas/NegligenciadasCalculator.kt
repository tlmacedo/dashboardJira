package br.sidia.painel.cards.negligenciadas

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant

/** Os seis recortes (NG-01 a NG-06), na ordem em que a tela os mostra. */
object KindsDeNegligenciada {
    const val SEM_ATUALIZACAO = "semAtualizacao"
    const val SEM_RESPONSAVEL = "semResponsavel"
    const val VENCIDAS = "vencidas"
    const val BLOQUEADAS = "bloqueadas"
    const val PARADAS = "paradas"
    const val A_FAZER = "aFazer"
    val TODOS = listOf(SEM_ATUALIZACAO, SEM_RESPONSAVEL, VENCIDAS, BLOQUEADAS, PARADAS, A_FAZER)
}

/**
 * Card Negligenciadas. Seis listas SEPARADAS (a mesma issue pode estar em mais de uma):
 * - `semAtualizacao`: em andamento com `updated` há mais de [diasSemAtualizacao] dias corridos;
 * - `semResponsavel`: aberta sem responsável;
 * - `vencidas`: aberta com vencimento anterior a hoje (dia de Manaus);
 * - `bloqueadas`: categoria BLOCKED (ou com a flag de impedimento);
 * - `paradas`: SÓ em andamento, tempo no status atual maior que `max(minParadaDias, fatorParada × mediana do status
 *   entre as concluídas do histórico)`, e só [minParadaDias] sem mediana;
 * - `aFazer`: categoria TODO há mais de [diasAFazer] dias corridos no status.
 */
class NegligenciadasCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val diasSemAtualizacao: Int = 7,
    private val diasAFazer: Int = 30,
    private val minParadaDias: Double = 3.0,
    private val fatorParada: Double = 2.0,
) {
    /** [abertas]: só as não concluídas, já no escopo. [historico]: concluídas positivas que dão a mediana por status. */
    fun calcular(abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode): Map<String, List<IssueSnapshot>> {
        val hoje = cal.localDate(agora)
        val medianas = medianaPorStatus(historico, agora, unit)
        val emAndamento = abertas.filter { Fluxo.emAndamento(it, map) }

        val semAtualizacao = emAndamento.filter { s ->
            val updated = s.updated
            updated != null && cal.calendarDays(minOf(updated, agora), agora) > diasSemAtualizacao
        }
        val semResponsavel = abertas.filter { it.assignee == null }
        val vencidas = abertas.filter { s -> s.duedate?.isBefore(hoje) == true }
        val bloqueadas = abertas.filter { map.category(it.status) == StatusCategory.BLOCKED || it.flagged }
        val paradas = emAndamento.filter { s ->
            val dias = Fluxo.diasNoStatusAtual(s, cal, unit, agora)
            val mediana = medianas[s.status]
            val limite = if (mediana == null) minParadaDias else maxOf(minParadaDias, fatorParada * mediana)
            dias > limite
        }
        val aFazer = abertas.filter { s ->
            map.category(s.status) == StatusCategory.TODO && Fluxo.diasNoStatusAtual(s, cal, TimeUnitMode.CORRIDO, agora) > diasAFazer
        }

        val ordem = Comparator<IssueSnapshot> { a, b -> ORDEM_NATURAL_DE_CHAVES.compare(a.key, b.key) }
        return linkedMapOf(
            KindsDeNegligenciada.SEM_ATUALIZACAO to semAtualizacao.sortedWith(ordem),
            KindsDeNegligenciada.SEM_RESPONSAVEL to semResponsavel.sortedWith(ordem),
            KindsDeNegligenciada.VENCIDAS to vencidas.sortedWith(ordem),
            KindsDeNegligenciada.BLOQUEADAS to bloqueadas.sortedWith(ordem),
            KindsDeNegligenciada.PARADAS to paradas.sortedWith(ordem),
            KindsDeNegligenciada.A_FAZER to aFazer.sortedWith(ordem),
        )
    }

    /** Mediana, entre as concluídas, do tempo TOTAL que cada task passou em cada status (sem a categoria DONE). */
    private fun medianaPorStatus(historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode): Map<String, Double> {
        val porStatus = HashMap<String, MutableList<Double>>()
        for (s in historico.filter { Fluxo.concluidaPositiva(it, map) }) {
            val soma = HashMap<String, Double>()
            for (iv in TimelineBuilder.intervals(s.timeline, agora, map)) {
                if (iv.category == StatusCategory.DONE) continue
                val d = if (unit == TimeUnitMode.UTIL) cal.businessDays(iv.start, iv.end) else cal.calendarDays(iv.start, iv.end)
                soma.merge(iv.status, d, Double::plus)
            }
            soma.forEach { (status, v) -> porStatus.getOrPut(status) { mutableListOf() }.add(v) }
        }
        return porStatus.mapValues { (_, v) -> requireNotNull(Percentiles.median(v)) }
    }
}
