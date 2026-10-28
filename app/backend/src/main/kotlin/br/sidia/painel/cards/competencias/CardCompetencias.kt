package br.sidia.painel.cards.competencias

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.Situacao
import java.util.Locale

/**
 * Card Matriz de competências: as competências (de `equipes.yaml`) das pessoas da visão, as que dependem de uma pessoa só, as
 * pedidas por tasks abertas (`skill:x`) sem ninguém da visão e as horas abertas por competência. Competências são dado de
 * pessoas: o USUARIO só enxerga a própria linha (a visão é a do escopo); os demais, a do seu papel. Filtros: squad e pessoa
 * (os de tipo, prioridade e OS recortam as tasks da demanda).
 */
object CardCompetencias : CardDeLeitura<CompetenciasDto> {
    override val id = "competencias"
    override val periodoPadraoDias: Int? = null

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CompetenciasDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas)

        val analise = CompetenciasCalculator(motor).calcular(e.escopo.visiveis, abertas)
        fun linhas(lista: List<IssueSnapshot>) = lista.map { s ->
            val pedidas = s.labels.map { it.trim().lowercase(Locale.ROOT) }.filter { it.startsWith("skill:") }.joinToString(", ")
            linhaDe(s, e.escopo, "Pede $pedidas; ${String.format(Locale.ROOT, "%.1f", motor.horas.de(s).valor)} h")
        }
        return ResultadoDeCard(analise.dto) { segmento ->
            when {
                segmento == "semNinguem" -> linhas(analise.semNinguem)
                segmento.startsWith("skill:") -> analise.porCompetencia[segmento.removePrefix("skill:")]?.let(::linhas)
                else -> null
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CompetenciasDto(
        competencias = emptyList(), pessoas = emptyList(), celulas = emptyList(), pessoaUnica = emptyList(), semNinguem = emptyList(),
        demandaH = emptyMap(), avisos = avisos,
    )
}

/**
 * Cartões da tela Matriz de competências: `CM-01` (a matriz, sem tasks por trás), `CM-02` (tasks que pedem competência sem
 * ninguém) e `CM-03` (tasks abertas por competência pedida).
 */
internal fun cartoesDeCompetencias(): List<CartaoRegistrado> {
    val fontes = listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO)
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho("CM-01", CardCompetencias.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.PESSOAS, fontes, temDrill = false),
            CardCompetencias, { false },
        ) { emptyMap() },
        CartaoDeLeitura(
            metaDeTrabalho("CM-02", CardCompetencias.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.QUALIDADE, fontes),
            CardCompetencias, { it == "semNinguem" },
        ) { dto -> mapOf("semNinguem" to dto.tasksSemNinguem) },
        CartaoDeLeitura(
            metaDeTrabalho("CM-03", CardCompetencias.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.ESTOQUE, fontes),
            CardCompetencias, { it.startsWith("skill:") },
        ) { dto -> dto.tasksPorCompetencia.mapKeys { "skill:${it.key}" } },
    )
}
