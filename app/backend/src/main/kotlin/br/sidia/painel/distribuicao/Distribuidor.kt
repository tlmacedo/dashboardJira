package br.sidia.painel.distribuicao

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.pessoas.Jain
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.LocalDate

/**
 * Uma pessoa que pode receber trabalho. [cargaProjetadaH] é a carga que ela já tem no horizonte de 4 semanas e
 * [capacidadeH] a capacidade líquida do mesmo horizonte. [produtividade] é o gasto ÷ estimado das concluídas dela (bruto:
 * o motor o limita à faixa das regras). [competenciasFaltantes] e [jaTrabalhouNoEpicoOuComponente] valem para o caso de um item
 * só; quando [competencias] e [afinidades] vêm preenchidos (não nulos), o motor calcula os dois POR ITEM e ignora os fixos.
 */
data class Candidato(
    val pessoa: String,
    val cargaProjetadaH: Double,
    val capacidadeH: Double,
    val wip: Int,
    val limiteWip: Int,
    val competenciasFaltantes: Int,
    val jaTrabalhouNoEpicoOuComponente: Boolean,
    val ultimaAtribuicao: Instant?,
    val produtividade: Double,
    val competencias: Set<String>? = null,
    val afinidades: Set<String>? = null,
)

/**
 * Os termos do score de um candidato para um item: `score = carga + wip + wipCheio + competencias - bonus`. Cada termo é um
 * número positivo (o [bonus] é o que foi SUBTRAÍDO), para a tela explicar o porquê sem esconder nada.
 */
data class Fatores(
    val pessoa: String,
    val carga: Double,
    val wip: Double,
    val wipCheio: Double,
    val competencias: Double,
    val bonus: Double,
    val score: Double,
)

/** A sugestão para um item: quem ([sugerido], nulo se ninguém tem capacidade) e os [fatores] de todos, em ordem ALFABÉTICA. */
data class Sugestao(val issueKey: String, val horas: Double, val sugerido: String?, val fatores: List<Fatores>)

/** O resultado de uma rodada; [jainAntes] e [jainDepois] são do CONJUNTO elegível (nunca nota de pessoa) e nulos com menos de 2. */
data class ResultadoDistribuicao(val versaoRegras: String, val sugestoes: List<Sugestao>, val jainAntes: Double?, val jainDepois: Double?)

/**
 * Motor de distribuição em MODO SOMBRA: só sugere, nunca escreve no Jira nem muda responsável. Puro e determinístico.
 *
 * Itens do maior para o menor (horas; empate pela chave natural); a cada atribuição a carga do escolhido sobe antes do próximo
 * item (o WIP não: o item novo entra em "a fazer"). Menor score vence; empate: quem recebeu há mais tempo (ou nunca), depois
 * quem recebeu antes nesta rodada, depois a ordem alfabética. Os fatores de todos saem em ordem alfabética, nunca por score.
 * Elegível: capacidade ACIMA de `capacidadeMinimaH`. [tetoJain] limita cada ocupação no índice do conjunto.
 */
class Distribuidor(private val regras: RegrasDeDistribuicao, private val tetoJain: Double = 3.0) {
    /** Tasks "a fazer" sem responsável ou com um GERENTE (que não executa; a task espera dono), em ordem natural de chave. */
    fun pendentes(abertas: List<IssueSnapshot>, map: StatusMap, roles: RoleDirectory, hoje: LocalDate): List<IssueSnapshot> =
        abertas.filter { s ->
            map.category(s.status) == StatusCategory.TODO &&
                (s.assignee == null || Role.GERENTE in roles.rolesOf(s.assignee, hoje))
        }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })

    fun distribuir(
        itens: List<Pair<IssueSnapshot, Double>>,
        candidatos: List<Candidato>,
        competenciasDoItem: (IssueSnapshot) -> Set<String>,
        afinidadeDoItem: (IssueSnapshot) -> Set<String> = { emptySet() },
    ): ResultadoDistribuicao {
        val elegiveis = candidatos.filter { it.capacidadeH > regras.capacidadeMinimaH }.sortedBy { it.pessoa }
        val carga = elegiveis.associate { it.pessoa to it.cargaProjetadaH }.toMutableMap()
        // Ordem em que cada pessoa recebeu algo NESTA rodada (0 = ainda não recebeu): desempata depois da última atribuição real.
        val rodada = HashMap<String, Int>()
        val jainAntes = jainDe(elegiveis, carga)

        val porItem = HashMap<String, Sugestao>()
        val ordemDeDistribuicao = itens.sortedWith(compareByDescending<Pair<IssueSnapshot, Double>> { it.second }.thenBy(ORDEM_NATURAL_DE_CHAVES) { it.first.key })
        for ((item, horas) in ordemDeDistribuicao) {
            val pedidas = competenciasDoItem(item)
            val afinidadeDesteItem = afinidadeDoItem(item)
            val fatores = elegiveis.map { fatoresDe(it, carga.getValue(it.pessoa), pedidas, afinidadeDesteItem) }
            val escolhido = fatores.minWithOrNull(
                compareBy<Fatores> { arredondado(it.score) }
                    .thenBy { f -> elegiveis.first { it.pessoa == f.pessoa }.ultimaAtribuicao ?: Instant.MIN }
                    .thenBy { rodada[it.pessoa] ?: 0 }
                    .thenBy { it.pessoa },
            )?.pessoa
            if (escolhido != null) {
                carga[escolhido] = carga.getValue(escolhido) + horas
                rodada[escolhido] = rodada.size + 1
            }
            porItem[item.key] = Sugestao(item.key, horas, escolhido, fatores)
        }
        val sugestoes = itens.map { (item, _) -> porItem.getValue(item.key) }
        return ResultadoDistribuicao(regras.rotulo, sugestoes, jainAntes, jainDe(elegiveis, carga))
    }

    private fun fatoresDe(c: Candidato, carga: Double, pedidas: Set<String>, afinidadeDoItem: Set<String>): Fatores {
        val produtividade = c.produtividade.coerceIn(regras.produtividadeMin, regras.produtividadeMax)
        val faltantes = c.competencias?.let { (pedidas - it).size } ?: c.competenciasFaltantes
        val afinidade = c.afinidades?.let { it.any { a -> a in afinidadeDoItem } } ?: c.jaTrabalhouNoEpicoOuComponente
        val termoCarga = carga * produtividade / c.capacidadeH
        val termoWip = regras.pesoWip * c.wip / maxOf(1, c.limiteWip)
        val termoWipCheio = if (c.wip >= c.limiteWip) regras.penalidadeWipCheio else 0.0
        val termoCompetencia = regras.pesoCompetenciaFaltante * faltantes
        val bonus = if (afinidade) regras.bonusEpicoOuComponente else 0.0
        return Fatores(c.pessoa, termoCarga, termoWip, termoWipCheio, termoCompetencia, bonus, termoCarga + termoWip + termoWipCheio + termoCompetencia - bonus)
    }

    /** Empate de ponto flutuante não decide nada: compara o score arredondado em 1e-9. */
    private fun arredondado(score: Double): Long = Math.round(score * 1e9)

    private fun jainDe(elegiveis: List<Candidato>, carga: Map<String, Double>): Double? =
        Jain.indice(elegiveis.map { Ocupacao.Valor(carga.getValue(it.pessoa) / it.capacidadeH) }, tetoJain)
}
