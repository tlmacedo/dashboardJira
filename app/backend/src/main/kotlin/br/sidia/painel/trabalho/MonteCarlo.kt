package br.sidia.painel.trabalho

import br.sidia.painel.cards.FiltrosDeCard
import br.sidia.painel.domain.Percentiles
import java.time.LocalDate
import kotlin.random.Random

/** Semanas até acabar o backlog (P50, P85, P95); `null` = mais que o limite de semanas. */
data class Quando(val p50: Int?, val p85: Int?, val p95: Int?)

/**
 * Monte Carlo sobre a vazão semanal histórica (amostra com reposição). A [random] é injetada: com a mesma semente o
 * resultado é o mesmo, o que deixa o card, o detalhamento e a recarga do dia mostrando o mesmo número.
 */
class MonteCarlo(private val random: Random) {
    /**
     * Quantas semanas até o [backlog] acabar. Por sorteio e semana: `restante += chegada sorteada - vazão sorteada`,
     * até `restante <= 0`. Sem [chegadas] (null) só a vazão conta (o app anterior). Sorteio que não converge em
     * [limiteSemanas] vale `limite + 1`; um percentil acima do limite sai `null` ("mais de N semanas"). Sem vazão
     * nenhuma (lista vazia ou só zeros) também não converge. Percentis como o `np.percentile` do app anterior
     * (interpolação linear, com truncamento).
     */
    fun quando(vazao: List<Int>, chegadas: List<Int>?, backlog: Int, sorteios: Int, limiteSemanas: Int): Quando {
        if (backlog <= 0) return Quando(0, 0, 0)
        if (vazao.isEmpty() || vazao.all { it <= 0 }) return Quando(null, null, null)
        val semanas = DoubleArray(sorteios)
        for (i in 0 until sorteios) {
            var restante = backlog.toLong()
            var resultado = limiteSemanas + 1
            for (s in 1..limiteSemanas) {
                val chegou = if (chegadas.isNullOrEmpty()) 0 else chegadas[random.nextInt(chegadas.size)]
                restante += chegou - vazao[random.nextInt(vazao.size)]
                if (restante <= 0) {
                    resultado = s
                    break
                }
            }
            semanas[i] = resultado.toDouble()
        }
        val lista = semanas.toList()
        fun percentil(p: Double): Int? = Percentiles.of(lista, p)?.takeIf { it <= limiteSemanas }?.toInt()
        return Quando(percentil(0.50), percentil(0.85), percentil(0.95))
    }

    /**
     * Quantos itens saem em [semanas] semanas. Percentis CONSERVADORES: o P85 é o 15º percentil dos totais sorteados e o
     * P95 o 5º (85% e 95% dos sorteios fizeram pelo menos isso). Sem vazão histórica, zeros.
     */
    fun quantos(vazao: List<Int>, semanas: Int, sorteios: Int): Triple<Int, Int, Int> {
        if (vazao.isEmpty()) return Triple(0, 0, 0)
        val n = maxOf(1, semanas)
        val totais = List(sorteios) {
            var soma = 0L
            repeat(n) { soma += vazao[random.nextInt(vazao.size)] }
            soma.toDouble()
        }
        fun percentil(p: Double): Int = requireNotNull(Percentiles.of(totais, p)).toInt()
        return Triple(percentil(0.50), percentil(0.15), percentil(0.05))
    }

    /**
     * Previsão condicional pela idade: dos [ciclos] históricos MAIORES que a [idade], o que faltou (`c - idade`), P50 e
     * P85 com uma casa decimal. Com menos de [minimo] ciclos mais longos, `null` (amostra pequena demais).
     */
    fun restanteDadaIdade(ciclos: List<Double>, idade: Double, minimo: Int = 5): Pair<Double, Double>? {
        val restantes = ciclos.filter { it > idade }.map { it - idade }
        if (restantes.size < minimo || restantes.isEmpty()) return null
        fun arredonda(v: Double?) = Math.round(requireNotNull(v) * 10.0) / 10.0
        return arredonda(Percentiles.of(restantes, 0.5)) to arredonda(Percentiles.of(restantes, 0.85))
    }
}

/**
 * Semente do Monte Carlo derivada do DIA (de Manaus) e dos filtros, estável entre execuções: card, detalhamento e recarga
 * no mesmo dia, com os mesmos filtros, sorteiam igual. A ordem dos tipos e das prioridades não muda a semente. É um hash
 * FNV-1a de 64 bits do texto canônico (não depende do `hashCode` da JVM).
 */
fun sementeDoDia(dia: LocalDate, filtros: FiltrosDeCard): Long {
    val texto = listOf(
        dia.toString(), filtros.squad.orEmpty(), filtros.pessoa.orEmpty(), filtros.tipos.sorted().joinToString(","),
        filtros.prioridades.sorted().joinToString(","), filtros.os.orEmpty(), filtros.de?.toString().orEmpty(),
        filtros.ate?.toString().orEmpty(), filtros.unit.name,
    ).joinToString("|")
    var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325 (base do FNV-1a de 64 bits)
    for (b in texto.toByteArray(Charsets.UTF_8)) {
        h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
    }
    return h
}
