package br.sidia.painel.cards.esforco

import br.sidia.painel.cards.posse.PosseDePessoa
import br.sidia.painel.pessoas.RegrasDeEsforco
import br.sidia.painel.sync.WorklogRecord
import br.sidia.painel.trabalho.IssueSnapshot
import java.util.Locale
import java.util.TreeMap

/**
 * O esforço de uma task concluída: os três componentes (peso do tipo, testes e horas) e o [indice]. [horasDoCampo]: as horas
 * vieram do tempo gasto do Jira (`timespent`), porque a task não tem worklog. [testesPonderados] é o número de testes sem
 * arredondar (na linha de uma pessoa, `testes` × fração de posse; na task é igual a [testes]).
 */
data class EsforcoTask(
    val key: String,
    val pesoTipo: Double,
    val testes: Int,
    val horas: Double,
    val indice: Double,
    val horasDoCampo: Boolean,
    val testesPonderados: Double = testes.toDouble(),
)

/**
 * Calcula o esforço (domínio puro). Índice = peso do tipo + testes × peso do teste + horas × fator da hora. As horas de cada
 * pessoa seguem a POSSE da task (spec 6.3), não o autor do worklog.
 */
class EsforcoCalculator(private val regras: RegrasDeEsforco) {
    private val prefixo = regras.prefixoTeste.trim().lowercase(Locale.ROOT)

    /**
     * Os componentes da task. Peso do tipo: o de `pesoPorTipo` (sem diferenciar maiúsculas nem espaços nas pontas) ou o padrão.
     * Testes: labels distintas (sem diferenciar maiúsculas) que começam com o prefixo e têm algo depois dele. Horas: a soma do
     * worklog; sem worklog, o `spentS` da issue (marca [EsforcoTask.horasDoCampo]); sem nenhum dos dois, zero.
     */
    fun daTask(s: IssueSnapshot, worklogs: List<WorklogRecord>): EsforcoTask {
        val tipo = s.issueType.trim()
        val pesoTipo = regras.pesoPorTipo.entries.firstOrNull { it.key.trim().equals(tipo, ignoreCase = true) }?.value ?: regras.pesoPadrao
        val testes = s.labels.map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.startsWith(prefixo) && it.length > prefixo.length }.distinct().size
        val gasto = s.spentS ?: 0L
        val (horas, doCampo) = when {
            worklogs.isNotEmpty() -> worklogs.sumOf { it.seconds } / SEGUNDOS_POR_HORA to false
            gasto > 0 -> gasto / SEGUNDOS_POR_HORA to true
            else -> 0.0 to false
        }
        val indice = pesoTipo + testes * regras.pesoTeste + horas * regras.fatorHora
        return EsforcoTask(s.key, pesoTipo, testes, horas, indice, doCampo)
    }

    /**
     * O esforço de cada pessoa (chave = pessoa, em ordem alfabética): cada componente da task × a fração de posse da pessoa,
     * somado nas tasks. A posse sem pessoa (nulo) e a fração zero não geram linha. O `testes` da linha é o arredondamento de
     * [EsforcoTask.testesPonderados].
     */
    fun porPessoa(tasks: List<Pair<EsforcoTask, List<PosseDePessoa>>>): Map<String, EsforcoTask> {
        class Soma(var peso: Double = 0.0, var testes: Double = 0.0, var horas: Double = 0.0, var indice: Double = 0.0, var campo: Boolean = false)
        val somas = TreeMap<String, Soma>()
        for ((t, posse) in tasks) {
            for (p in posse) {
                val pessoa = p.pessoa ?: continue
                if (p.fracao <= 0.0) continue
                val soma = somas.getOrPut(pessoa) { Soma() }
                soma.peso += t.pesoTipo * p.fracao
                soma.testes += t.testes * p.fracao
                soma.horas += t.horas * p.fracao
                soma.indice += t.indice * p.fracao
                soma.campo = soma.campo || t.horasDoCampo
            }
        }
        return somas.mapValues { (pessoa, s) ->
            EsforcoTask(pessoa, s.peso, Math.round(s.testes).toInt(), s.horas, s.indice, s.campo, s.testes)
        }
    }

    private companion object {
        const val SEGUNDOS_POR_HORA = 3600.0
    }
}
