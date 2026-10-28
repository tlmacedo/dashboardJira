package br.sidia.painel.distribuicao

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.pessoas.CargaDaPessoa
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot

/** Uma task que sairia de [de] para [para]. As ocupações são do horizonte de 4 semanas (1,0 = 100%). */
data class Movimento(
    val issueKey: String,
    val de: String,
    val para: String,
    val horas: Double,
    val ocupacaoOrigemAntes: Double,
    val ocupacaoOrigemDepois: Double,
    val ocupacaoDestinoDepois: Double,
)

/**
 * Motor de rebalanceamento em MODO SOMBRA: só sugere, nunca escreve no Jira. Puro e determinístico.
 *
 * - Gatilho: a pessoa com ocupação do horizonte ACIMA de `gatilhoRebalanceamento` é origem (110% exatos não é). Quem não tem
 *   capacidade ([br.sidia.painel.pessoas.Ocupacao.SemCapacidade]) não tem razão para comparar: não é origem nem destino.
 * - Só entram as tasks ainda NÃO iniciadas (quem chama passa só elas); cada task se move uma vez, no máximo `maxMovimentos`.
 * - O destino precisa ter todas as competências que a task pede, e a ocupação dele DEPOIS do movimento precisa ficar pelo menos
 *   `histereseRebalanceamentoPp` pontos percentuais abaixo da ocupação da origem DEPOIS do movimento (sem vai e volta) e não
 *   passar do próprio gatilho (senão só trocaria o problema de lugar). Entre os destinos válidos, o de menor ocupação depois;
 *   empate pela ordem alfabética.
 * - A origem mais carregada é atendida primeiro (empate alfabético) e, nela, a task de mais horas primeiro.
 */
class Rebalanceador(private val regras: RegrasDeDistribuicao) {
    private class Estado(val pessoa: String, var demandaH: Double, val capacidadeH: Double) {
        val ocupacao: Double get() = demandaH / capacidadeH
    }

    fun calcular(
        cargas: Map<String, CargaDaPessoa>,
        naoIniciadas: Map<String, List<Pair<IssueSnapshot, Double>>>,
        competencias: Map<String, Set<String>>,
        competenciasDoItem: (IssueSnapshot) -> Set<String>,
    ): List<Movimento> {
        // Só quem tem capacidade no horizonte entra (origem ou destino); a demanda e a capacidade são as das semanas do horizonte.
        val pessoas = cargas.values.sortedBy { it.pessoa }.mapNotNull { c ->
            val capacidade = c.semanas.sumOf { it.capacidadeH }
            if (capacidade <= 0.0) null else Estado(c.pessoa, c.semanas.sumOf { it.demandaH }, capacidade)
        }
        val jaMovidas = HashSet<String>()
        val movimentos = ArrayList<Movimento>()
        val limiteH = regras.histereseRebalanceamentoPp / 100.0

        while (movimentos.size < regras.maxMovimentos) {
            val origens = pessoas.filter { it.ocupacao > regras.gatilhoRebalanceamento }
                .sortedWith(compareByDescending<Estado> { it.ocupacao }.thenBy { it.pessoa })
            val achado = origens.firstNotNullOfOrNull { origem -> proximoMovimento(origem, pessoas, naoIniciadas, competencias, competenciasDoItem, jaMovidas, limiteH) }
                ?: break
            val (m, origem, destino, horas) = achado
            origem.demandaH -= horas
            destino.demandaH += horas
            jaMovidas += m.issueKey
            movimentos += m
        }
        return movimentos
    }

    private data class Achado(val movimento: Movimento, val origem: Estado, val destino: Estado, val horas: Double)

    private fun proximoMovimento(
        origem: Estado,
        pessoas: List<Estado>,
        naoIniciadas: Map<String, List<Pair<IssueSnapshot, Double>>>,
        competencias: Map<String, Set<String>>,
        competenciasDoItem: (IssueSnapshot) -> Set<String>,
        jaMovidas: Set<String>,
        histerese: Double,
    ): Achado? {
        val fila = naoIniciadas[origem.pessoa].orEmpty().filter { it.first.key !in jaMovidas }
            .sortedWith(compareByDescending<Pair<IssueSnapshot, Double>> { it.second }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.first.key })
        for ((item, horas) in fila) {
            val exigidas = competenciasDoItem(item)
            val origemDepois = (origem.demandaH - horas) / origem.capacidadeH
            val destino = pessoas.asSequence()
                .filter { it !== origem && competencias[it.pessoa].orEmpty().containsAll(exigidas) }
                .map { it to (it.demandaH + horas) / it.capacidadeH }
                .filter { (_, depois) -> depois <= regras.gatilhoRebalanceamento && origemDepois - depois >= histerese - EPSILON }
                .sortedWith(compareBy({ it.second }, { it.first.pessoa }))
                .firstOrNull() ?: continue
            return Achado(
                Movimento(item.key, origem.pessoa, destino.first.pessoa, horas, origem.ocupacao, origemDepois, destino.second),
                origem, destino.first, horas,
            )
        }
        return null
    }

    companion object {
        private const val EPSILON = 1e-9

        /** As tasks que ainda podem mudar de mãos: "a fazer" e nunca iniciadas (nenhuma passagem por status ativo). */
        fun naoIniciadas(abertas: List<IssueSnapshot>, map: StatusMap): List<IssueSnapshot> =
            abertas.filter { map.category(it.status) == StatusCategory.TODO && Fluxo.inicioAtivo(it, map) == null }
    }
}
