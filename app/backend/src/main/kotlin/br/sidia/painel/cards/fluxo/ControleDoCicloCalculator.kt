package br.sidia.painel.cards.fluxo

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Faixa
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.LocalDate
import java.time.YearMonth

/**
 * O DTO e, por segmento, as tasks por trás de cada número. [ciclos] é o ciclo (na unidade pedida) de cada task e [dias] o
 * dia de Manaus da conclusão, ambos por chave, para o texto da gaveta.
 */
class ControleDoCicloAnalise(
    val dto: ControleDoCicloDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    val ciclos: Map<String, Double>,
    val dias: Map<String, LocalDate>,
)

/**
 * Controle do ciclo e tendência (I-02 e I-03). Cada concluída positiva com ciclo ([Fluxo.cicloDias]: da primeira entrada
 * ativa à resolução) vira um ponto (dia de conclusão em Manaus x dias de ciclo), POR TIPO: Checklist e Review nunca se
 * misturam. A média móvel segue a regra do Jira (20% dos pontos, número ímpar, mínimo 5) e é centrada em cada ponto,
 * cortada nas pontas. A série mensal dá P50, P85 e n por mês; mês com 1 a 4 tasks leva [MesDoCicloDto.amostraPequena].
 * Com [semOutliers], ciclos acima de [LIMITE_OUTLIER_DIAS] dias CORRIDOS (qualquer que seja a unidade) ficam de fora.
 */
class ControleDoCicloCalculator(private val cal: BusinessCalendar, private val map: StatusMap) {

    private class Ponto(val task: IssueSnapshot, val dia: LocalDate, val ciclo: Double, val outlier: Boolean)

    /** [resolvidas]: as resolvidas do período, no escopo (a conclusão positiva é conferida aqui). */
    fun analisar(
        resolvidas: List<IssueSnapshot>, de: LocalDate, ate: LocalDate, unit: TimeUnitMode, semOutliers: Boolean,
    ): ControleDoCicloAnalise {
        Fluxo.exigirStatusMapeados(map, resolvidas)
        val todas = resolvidas.filter { Fluxo.concluidaPositiva(it, map) }.mapNotNull { s ->
            val ciclo = Fluxo.cicloDias(s, map, cal, unit) ?: return@mapNotNull null
            val corridos = Fluxo.cicloDias(s, map, cal, TimeUnitMode.CORRIDO) ?: return@mapNotNull null
            Ponto(s, cal.localDate(requireNotNull(s.resolved)), ciclo, corridos > LIMITE_OUTLIER_DIAS)
        }
        val usadas = if (semOutliers) todas.filterNot { it.outlier } else todas
        val meses = generateSequence(YearMonth.from(de)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(ate)) }.toList()

        fun natural(l: List<Ponto>) = l.map { it.task }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        fun faixa(valores: List<Double>, p: Double) = valores.takeIf { it.isNotEmpty() }?.let { Percentiles.of(it, p) }

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val faixasPorTipo = java.util.TreeMap<String, Faixa>()
        val series = usadas.groupBy { it.task.issueType }.toSortedMap().map { (tipo, doTipo) ->
            val ordenados = doTipo.sortedWith(compareBy<Ponto> { it.task.resolved }.then(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.task.key }))
            val ciclos = ordenados.map { it.ciclo }
            val p50 = faixa(ciclos, 0.5)
            val p85 = faixa(ciclos, 0.85)
            if (p50 != null && p85 != null) faixasPorTipo[tipo] = Faixa(p50, p85, ciclos.size)
            val janela = if (ciclos.size >= Fluxo.AMOSTRA_MINIMA) janelaDaMediaMovel(ciclos.size) else null
            val pontos = ordenados.mapIndexed { i, p ->
                val media = janela?.let {
                    val h = it / 2
                    ciclos.subList(maxOf(0, i - h), minOf(ciclos.size, i + h + 1)).average()
                }
                PontoDeCicloDto(p.dia.toString(), p.ciclo, media, p.outlier)
            }
            segmentos["tipo:$tipo"] = natural(doTipo)
            for ((dia, doDia) in ordenados.groupBy { it.dia }) segmentos["dia:$dia:$tipo"] = natural(doDia)
            val porMes = ordenados.groupBy { YearMonth.from(it.dia) }
            val mesesDoTipo = meses.map { m ->
                val doMes = porMes[m].orEmpty()
                segmentos["mes:$m:$tipo"] = natural(doMes)
                val c = doMes.map { it.ciclo }
                MesDoCicloDto(m.toString(), doMes.size, faixa(c, 0.5), faixa(c, 0.85), doMes.isNotEmpty() && doMes.size < Fluxo.AMOSTRA_MINIMA)
            }
            SerieDoTipoDto(tipo, ciclos.size, p50, p85, ciclos.size < Fluxo.AMOSTRA_MINIMA, janela, pontos, mesesDoTipo)
        }
        val dto = ControleDoCicloDto(
            series = series, concluidas = usadas.size, semOutliers = semOutliers, limiteOutlierDias = LIMITE_OUTLIER_DIAS,
            outliersExcluidos = if (semOutliers) todas.size - usadas.size else 0, de = de.toString(), ate = ate.toString(),
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido", avisos = Fluxo.avisosDeAmostra(faixasPorTipo),
        )
        return ControleDoCicloAnalise(
            dto, segmentos, usadas.associate { it.task.key to it.ciclo }, usadas.associate { it.task.key to it.dia },
        )
    }

    companion object {
        /** Com `semOutliers`, ciclos acima disto (dias corridos) ficam fora: a regra do Tempo em status e do app anterior. */
        const val LIMITE_OUTLIER_DIAS = 30

        /** Menor janela da média móvel (a regra do gráfico de controle do Jira). */
        const val JANELA_MINIMA = 5

        /** 20% dos pontos, arredondado para cima, número ímpar e no mínimo [JANELA_MINIMA]. */
        fun janelaDaMediaMovel(n: Int): Int {
            val vintePorCento = (n + 4) / 5
            val impar = if (vintePorCento % 2 == 0) vintePorCento + 1 else vintePorCento
            return maxOf(JANELA_MINIMA, impar)
        }
    }
}
