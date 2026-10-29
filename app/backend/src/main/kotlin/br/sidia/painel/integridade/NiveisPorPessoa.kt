package br.sidia.painel.integridade

import br.sidia.painel.cards.metas.MetasDto
import br.sidia.painel.domain.Percentiles

/** Nível de indício de uma pessoa, derivado da pontuação (pontos, taxa e múltiplo em relação ao time). NÃO é ranking nem prova: cada nível exige limiares do YAML e a revisão é humana. */
enum class NivelIntegridade { ALTO, MEDIO, NORMAL }

/**
 * Uma pessoa no resumo: [pontos] (soma das gravidades das evidências em que ela é a pessoa do sinal), [concluidas] (o número real de
 * tasks concluídas no período), [taxa] = pontos ÷ max(concluídas, `concluidasMinimas`), [multiplo] = taxa ÷ mediana das taxas e
 * [graves] (evidências de gravidade 3). Sem campo de posição: a ordem da lista é sempre alfabética.
 */
data class NivelPessoa(
    val pessoa: String,
    val pontos: Int,
    val concluidas: Int,
    val taxa: Double,
    val multiplo: Double,
    val graves: Int,
    val nivel: NivelIntegridade,
)

/**
 * Resumo da Integridade do período. [pessoas] vem em ordem alfabética (todas as com pontos ou concluídas; quem expõe a lista decide
 * mostrar só alto e médio). [impactoMetasPp] é `(taxaNoPrazo − taxaOriginal) × 100` das Metas (IN-06), nulo sem os dois valores.
 */
data class ResumoIntegridade(
    val evidencias: Int,
    val issuesAfetadas: Int,
    val medianaTaxa: Double,
    val porSinal: Map<SinalIntegridade, Int>,
    val pessoas: List<NivelPessoa>,
    val impactoMetasPp: Double?,
)

/** Níveis por pessoa: taxa, mediana com piso, múltiplo e os critérios de alto e médio (todos em `integridade.yaml`). */
object NiveisPorPessoa {
    /** Gravidade a partir da qual a evidência é "grave". */
    private const val GRAVIDADE_GRAVE = 3

    /**
     * [concluidasPorPessoa] são as concluídas positivas do período por responsável. Evidência sem pessoa conta nos totais e fica
     * fora dos níveis. A mediana das taxas é só das pessoas COM concluídas (piso `pisoMediana`).
     */
    fun resumir(
        evidencias: List<EvidenciaIntegridade>, concluidasPorPessoa: Map<String, Int>, regras: RegrasDeIntegridade, metas: MetasDto?,
    ): ResumoIntegridade {
        val dePessoa = evidencias.filter { it.pessoa != null }.groupBy { it.pessoa!! }
        val ids = (dePessoa.keys + concluidasPorPessoa.keys).toSortedSet()
        fun taxa(id: String): Double {
            val pontos = dePessoa[id].orEmpty().sumOf { it.gravidade }
            return pontos.toDouble() / maxOf(concluidasPorPessoa[id] ?: 0, regras.concluidasMinimas)
        }
        val mediana = maxOf(Percentiles.median(ids.filter { (concluidasPorPessoa[it] ?: 0) > 0 }.map(::taxa)) ?: 0.0, regras.pisoMediana)

        val pessoas = ids.map { id ->
            val dela = dePessoa[id].orEmpty()
            val pontos = dela.sumOf { it.gravidade }
            val graves = dela.count { it.gravidade >= GRAVIDADE_GRAVE }
            val taxa = taxa(id)
            val multiplo = taxa / mediana
            val nivel = when {
                pontos >= regras.pontosAlto && multiplo >= regras.multiploAlto && graves >= regras.gravesAlto -> NivelIntegridade.ALTO
                pontos >= regras.pontosMedio && multiplo >= regras.multiploMedio -> NivelIntegridade.MEDIO
                else -> NivelIntegridade.NORMAL
            }
            NivelPessoa(id, pontos, concluidasPorPessoa[id] ?: 0, taxa, multiplo, graves, nivel)
        }
        val contagem = evidencias.groupingBy { it.sinal }.eachCount()
        val noPrazo = metas?.taxaNoPrazo
        val original = metas?.taxaOriginal
        return ResumoIntegridade(
            evidencias = evidencias.size,
            issuesAfetadas = evidencias.map { it.issueKey }.toSet().size,
            medianaTaxa = mediana,
            porSinal = SinalIntegridade.entries.associateWith { contagem[it] ?: 0 },
            pessoas = pessoas,
            impactoMetasPp = if (noPrazo != null && original != null) (noPrazo - original) * 100.0 else null,
        )
    }
}
