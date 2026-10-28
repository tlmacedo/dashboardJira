package br.sidia.painel.cards.previsao

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.fechadas.SemanaDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.previsao.RegrasDePrevisao
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.MonteCarlo
import br.sidia.painel.trabalho.Quando
import br.sidia.painel.trabalho.SerieSemanal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/** O DTO e, por segmento, as tasks por trás de cada número (a gaveta abre exatamente estas). */
class PrevisaoAnalise(val dto: PrevisaoDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Card Previsão. A vazão (entregas) e a chegada (criações) semanais vêm da [SerieSemanal], a MESMA função do futuro
 * "Fluxo da semana", sobre as semanas FECHADAS antes da atual. O sorteio usa só as semanas a partir da primeira com
 * atividade (os zeros anteriores à base não são histórico). Sem [RegrasDePrevisao.comChegada] só a vazão conta, como o
 * app anterior.
 */
class PrevisaoCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val regras: RegrasDePrevisao,
) {
    private val dias = listOf(2, 4, 8)

    /**
     * [abertas]: as tasks sem data de resolução no escopo (as de categoria concluído não entram no backlog).
     * [historico]: as resolvidas dentro da janela de semanas. [semente]: `sementeDoDia(dia de agora, filtros)`.
     */
    fun analisar(
        abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, semente: Long, unit: TimeUnitMode,
    ): PrevisaoAnalise {
        val hoje = cal.localDate(agora)
        val avisos = mutableListOf<AvisoDto>()
        val backlog = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val serie = SerieSemanal.calcular(
            abertas + historico, map, SerieSemanal.ultimaSemanaFechada(hoje), regras.semanasHistorico, cal.zone,
        )
        val vazao = serie.map { it.saidas.size }
        val chegada = serie.map { it.entradas.size }
        val primeira = serie.indexOfFirst { it.entradas.isNotEmpty() || it.saidas.isNotEmpty() || it.canceladas.isNotEmpty() }
        val semanasComDado = if (primeira < 0) 0 else serie.size - primeira
        val vazaoUsada = vazao.takeLast(semanasComDado)
        val chegadaUsada = chegada.takeLast(semanasComDado)
        if (vazao.all { it == 0 }) {
            avisos += AvisoDto(AvisoDto.SEM_VAZAO_HISTORICA, AvisoDto.INFORMATIVO, "nenhuma entrega nas últimas ${regras.semanasHistorico} semanas")
        }

        val mc = MonteCarlo(Random(semente))
        val quando = mc.quando(vazaoUsada, if (regras.comChegada) chegadaUsada else null, backlog.size, regras.sorteios, regras.limiteSemanas)
        val quantos = dias.map { s ->
            val (p50, p85, p95) = mc.quantos(vazaoUsada, s, regras.sorteios)
            QuantosDto(s, p50, p85, p95)
        }

        // Previsão condicional (PV-06): ciclos do histórico por tipo contra a idade de cada task em andamento.
        val positivas = historico.filter { Fluxo.concluidaPositiva(it, map) }
        val ciclosPorTipo = positivas.mapNotNull { s -> Fluxo.cicloDias(s, map, cal, unit)?.let { s.issueType to it } }
            .groupBy({ it.first }, { it.second })
        avisos += Fluxo.avisosDeAmostra(Fluxo.faixasPorTipo(ciclosPorTipo))
        val emAndamento = backlog.filter { Fluxo.emAndamento(it, map) }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val porIssue = emAndamento.map { s ->
            val idade = Fluxo.idadeDias(s, map, cal, unit, agora)
            val ciclos = ciclosPorTipo[s.issueType].orEmpty()
            val restante = mc.restanteDadaIdade(ciclos, idade)
            PrevisaoIssueDto(s.key, s.summary, s.issueType, s.status, s.assignee, idade, restante?.first, restante?.second, ciclos.count { it > idade })
        }

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        fun ordena(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        segmentos["backlog"] = ordena(backlog)
        segmentos["vazao"] = ordena(serie.flatMap { it.saidas })
        for (w in serie) {
            segmentos["vazao:${w.semana}"] = ordena(w.saidas)
            segmentos["chegada:${w.semana}"] = ordena(w.entradas)
        }
        segmentos["porIssue"] = emAndamento

        val dto = PrevisaoDto(
            versaoRegras = "previsao v${regras.versao}",
            backlog = backlog.size,
            termina = quandoDto(quando, hoje),
            confianca = regras.confianca(semanasComDado),
            semanasComDado = semanasComDado,
            semanasHistorico = regras.semanasHistorico,
            limiteSemanas = regras.limiteSemanas,
            comChegada = regras.comChegada,
            entregasNaJanela = vazao.sum(),
            vazaoSemanal = serie.map { SemanaDto(it.semana.toString(), it.saidas.size) },
            chegadaSemanal = serie.map { SemanaDto(it.semana.toString(), it.entradas.size) },
            quantos = quantos,
            porIssue = porIssue,
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido",
            avisos = avisos,
        )
        return PrevisaoAnalise(dto, segmentos)
    }

    /** Segunda-feira da semana alvo: a k-ésima semana inteira depois da semana em curso; backlog 0 (k = 0) não tem data. */
    private fun quandoDto(q: Quando, hoje: LocalDate): QuandoDto {
        val segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun data(k: Int?): String? = k?.takeIf { it > 0 }?.let { segunda.plusWeeks(it.toLong()).toString() }
        return QuandoDto(data(q.p50), data(q.p85), data(q.p95), q.p50, q.p85, q.p95)
    }
}
