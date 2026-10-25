package br.sidia.painel.domain

import kotlin.math.floor

/** Percentis por interpolação linear (posição = p * (n - 1) sobre a lista ordenada). */
object Percentiles {
    /** Percentil [p] (0..1) de [values]; `null` se a lista for vazia. Fora de 0..1 (ou NaN) falha. */
    fun of(values: List<Double>, p: Double): Double? {
        require(p in 0.0..1.0) { "p deve estar entre 0 e 1, mas foi $p" }
        if (values.isEmpty()) return null
        val ordenados = values.sorted()
        val posicao = p * (ordenados.size - 1)
        val abaixo = floor(posicao).toInt()
        val acima = minOf(abaixo + 1, ordenados.size - 1)
        val fracao = posicao - abaixo
        return ordenados[abaixo] + (ordenados[acima] - ordenados[abaixo]) * fracao
    }

    /** Mediana (p50); `null` se a lista for vazia. */
    fun median(values: List<Double>): Double? = of(values, 0.5)
}
