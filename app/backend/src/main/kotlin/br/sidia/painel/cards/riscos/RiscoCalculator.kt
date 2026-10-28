package br.sidia.painel.cards.riscos

import br.sidia.painel.cards.negligenciadas.KindsDeNegligenciada
import br.sidia.painel.cards.negligenciadas.NegligenciadasCalculator
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.riscos.FatorDeRisco
import br.sidia.painel.riscos.RegrasDeRisco
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Faixa
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.LocalDate

/** Total de pontos, nível e os fatores que o formam (do maior para o menor ponto). */
data class Risco(val key: String, val pontos: Int, val nivel: String, val fatores: List<Pair<FatorDeRisco, Int>>)

/** A Fase 6 entrega a implementação (previsão pela capacidade); aqui nunca há previsão. */
fun interface PrevisaoDeTermino {
    fun previsao(key: String): LocalDate?
}

/**
 * Score de risco explicável de cada task aberta (pesos e limiares em `risco.yaml`, herdados do app anterior).
 * Idade: comparada com o P50/P85 do ciclo do PRÓPRIO tipo no histórico, em dias úteis (exclusivos; sem histórico do tipo
 * não pontua). Parada, sem atualização e bloqueada usam as MESMAS regras de Negligenciadas (parada e sem atualização só
 * em andamento). Vencimento (exclusivos, nesta precedência): vencida, previsão depois do vencimento, vence em breve.
 * O total é limitado a 100.
 */
class RiscoCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val regras: RegrasDeRisco,
    private val previsao: PrevisaoDeTermino = PrevisaoDeTermino { null },
) {
    companion object {
        const val TETO = 100
    }

    /** P50/P85 do ciclo (dias úteis) das concluídas positivas do [historico], por tipo. */
    fun faixasPorTipo(historico: List<IssueSnapshot>): Map<String, Faixa> {
        val ciclos = historico.filter { Fluxo.concluidaPositiva(it, map) }
            .mapNotNull { s -> Fluxo.cicloDias(s, map, cal, TimeUnitMode.UTIL)?.let { s.issueType to it } }
            .groupBy({ it.first }, { it.second })
        return Fluxo.faixasPorTipo(ciclos)
    }

    /**
     * [abertas]: as não concluídas, já no escopo. [historico]: concluídas que dão a faixa de ciclo por tipo e a mediana
     * por status. [camposDeTrabalho] falso (base sem estimativas sincronizadas) tira o fator "sem estimativa".
     */
    fun avaliar(
        abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, camposDeTrabalho: Boolean = true,
    ): List<Risco> {
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val hoje = cal.localDate(agora)
        val faixas = faixasPorTipo(historico)
        val listas = NegligenciadasCalculator(cal, map, regras.diasSemAtualizacao)
            .calcular(naoConcluidas, historico, agora, TimeUnitMode.UTIL)
        fun chaves(kind: String) = listas.getValue(kind).map { it.key }.toSet()
        val paradas = chaves(KindsDeNegligenciada.PARADAS)
        val semAtualizacao = chaves(KindsDeNegligenciada.SEM_ATUALIZACAO)
        val bloqueadas = chaves(KindsDeNegligenciada.BLOQUEADAS)

        val riscos = naoConcluidas.map { s ->
            val fatores = mutableListOf<Pair<FatorDeRisco, Int>>()
            fun soma(f: FatorDeRisco) {
                fatores += f to regras.pesos.getValue(f)
            }
            val faixa = faixas[s.issueType]
            if (faixa != null) {
                val idade = Fluxo.idadeDias(s, map, cal, TimeUnitMode.UTIL, agora)
                if (idade > faixa.p85) soma(FatorDeRisco.IDADE_ACIMA_P85) else if (idade > faixa.p50) soma(FatorDeRisco.IDADE_ACIMA_P50)
            }
            if (s.key in paradas) soma(FatorDeRisco.PARADA)
            val venc = s.duedate
            if (venc != null) {
                val prevista = previsao.previsao(s.key)
                when {
                    venc.isBefore(hoje) -> soma(FatorDeRisco.VENCIDA)
                    prevista != null && prevista.isAfter(venc) -> soma(FatorDeRisco.PREVISAO_APOS_VENCIMENTO)
                    !venc.isAfter(hoje.plusDays(regras.venceEmDias.toLong())) -> soma(FatorDeRisco.VENCE_EM_BREVE)
                }
            }
            if (s.key in bloqueadas) soma(FatorDeRisco.BLOQUEADA)
            if (foiReaberta(s)) soma(FatorDeRisco.REABERTA)
            if (s.timeline.assigneeChanges.size >= regras.trocas) soma(FatorDeRisco.MUITAS_TROCAS)
            val estimativa = s.estimateS
            val gasto = s.spentS
            if (estimativa != null && estimativa > 0 && gasto != null && gasto > regras.estouro * estimativa) soma(FatorDeRisco.ESTOURO_DE_HORAS)
            if (s.key in semAtualizacao) soma(FatorDeRisco.SEM_ATUALIZACAO)
            if (camposDeTrabalho && estimativa == null && s.storyPoints == null) soma(FatorDeRisco.SEM_ESTIMATIVA)
            val ordenados = fatores.sortedWith(compareBy({ -it.second }, { it.first.ordinal }))
            val pontos = minOf(TETO, ordenados.sumOf { it.second })
            Risco(s.key, pontos, regras.nivel(pontos), ordenados)
        }
        return riscos.sortedWith(compareBy<Risco>({ -it.pontos }).thenBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
    }

    /** Alguma vez voltou de um status concluído para outro (como o `reopen_count` do app anterior). */
    private fun foiReaberta(s: IssueSnapshot): Boolean =
        s.timeline.transitions.any { t ->
            val origem = t.from
            origem != null && map.category(origem) == StatusCategory.DONE && map.category(t.to) != StatusCategory.DONE
        }
}
