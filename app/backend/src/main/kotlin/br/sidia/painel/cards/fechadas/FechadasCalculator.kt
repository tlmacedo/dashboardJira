package br.sidia.painel.cards.fechadas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.abertas.ContagemDto
import br.sidia.painel.cards.abertas.FaixaTipoDto
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.NoPrazo
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.domain.classificar
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.toAuditIssue
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** O DTO e, por segmento, as issues que formam cada número (a gaveta abre exatamente estas). */
class FechadasAnalise(
    val dto: FechadasDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    val ciclos: Map<String, Double>,
    val leads: Map<String, Double>,
    /** Dia de Manaus da resolução, por chave. */
    val diaDaResolucao: Map<String, LocalDate>,
)

/**
 * Card Fechadas (FE-01 a FE-12). Concluída = categoria DONE, `resolution` não negativa e `resolved`; a de resolução
 * negativa é cancelada (conta à parte, FE-06); DONE sem `resolution` vai para `semResolution` (Qualidade dos dados).
 * "No prazo" e "no prazo ajustado" vêm de [NoPrazo], a mesma regra da Auditoria e de Metas.
 */
class FechadasCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val labels: LabelsDeAtraso,
) {
    /**
     * [resolvidas]: as resolvidas em `[de, ate]` (dia de Manaus), para os KPIs de entrega. [candidatasNoPrazo]: as issues
     * com VENCIMENTO em `[de, ate]` (`WorkStore.candidatasNoPrazo`), para o bloco "no prazo" pela regra única.
     * [comEventoDeStatus]: issues com mudança de status no período, para achar as concluídas sem resolução.
     */
    fun calcular(
        resolvidas: List<IssueSnapshot>, candidatasNoPrazo: List<IssueSnapshot>, de: LocalDate, ate: LocalDate, agora: Instant,
        unit: TimeUnitMode, visiveis: Set<String>, comEventoDeStatus: List<IssueSnapshot> = emptyList(),
    ): FechadasDto = analisar(resolvidas, candidatasNoPrazo, comEventoDeStatus, de, ate, agora, unit, visiveis).dto

    @Suppress("LongMethod", "UNUSED_PARAMETER")
    fun analisar(
        resolvidas: List<IssueSnapshot>, candidatasNoPrazo: List<IssueSnapshot>, comEventoDeStatus: List<IssueSnapshot>,
        de: LocalDate, ate: LocalDate, agora: Instant, unit: TimeUnitMode, visiveis: Set<String>,
        camposDeFiltro: List<br.sidia.painel.sync.CampoDeFiltro> = emptyList(),
    ): FechadasAnalise {
        val zona = cal.zone
        Fluxo.exigirStatusMapeados(map, candidatasNoPrazo, resolvidas)
        val concluidas = resolvidas.filter { Fluxo.concluidaPositiva(it, map) }
        val canceladas = resolvidas.filter { Fluxo.cancelada(it, map) }
        val inicio = de.atStartOfDay(zona).toInstant()
        val fim = ate.plusDays(1).atStartOfDay(zona).toInstant()
        val semResolution = (resolvidas + comEventoDeStatus).distinctBy { it.key }.filter { s ->
            Fluxo.divergenteDoneResolution(s, map) && Fluxo.entrouEmDone(s, map)?.let { !it.isBefore(inicio) && it.isBefore(fim) } == true
        }

        val ciclos = HashMap<String, Double>()
        val leads = HashMap<String, Double>()
        for (s in concluidas) {
            Fluxo.cicloDias(s, map, cal, unit)?.let { ciclos[s.key] = it }
            Fluxo.leadDias(s, cal, unit)?.let { leads[s.key] = it }
        }
        // As candidatas de "no prazo" podem ter sido resolvidas antes do período: o dia vale para elas também (gaveta).
        val diaDaResolucao = (concluidas + candidatasNoPrazo.filter { it.resolved != null }).distinctBy { it.key }
            .associate { it.key to cal.localDate(requireNotNull(it.resolved)) }
        val comCiclo = concluidas.filter { it.key in ciclos }
        val comLead = concluidas.filter { it.key in leads }

        fun faixa(valores: List<Double>): FaixaDto? =
            if (valores.isEmpty()) null else FaixaDto(requireNotNull(Percentiles.of(valores, 0.5)), requireNotNull(Percentiles.of(valores, 0.85)), valores.size)

        // "No prazo": regra única (NoPrazo.noUniverso + classificar), com as candidatas lidas por VENCIMENTO.
        val auditPorChave = candidatasNoPrazo.associateBy { it.key }
        val prazo = NoPrazo.detalhar(candidatasNoPrazo.map { it.toAuditIssue() }, labels, map, zona, de, ate)
        fun snaps(l: List<br.sidia.painel.auditoria.AuditIssue>) = l.mapNotNull { auditPorChave[it.key] }
        val comVencimento = snaps(prazo.concluidas)
        val noPrazo = snaps(prazo.noPrazo)
        val ajustadas = snaps(prazo.noPrazoAjustado)
        val resultado = prazo.resultado

        // Semanas (segunda a domingo de Manaus). A primeira semana é cortada só se for parcial e vazia.
        val primeira = Fluxo.semanaDe(inicio, zona)
        val ultima = Fluxo.semanaDe(ate.atStartOfDay(zona).toInstant(), zona)
        var serie = Fluxo.throughputSemanal(concluidas, ultima, ChronoUnit.WEEKS.between(primeira, ultima).toInt() + 1, zona)
        if (serie.size > 1 && serie.first().first.isBefore(de) && serie.first().second == 0) serie = serie.drop(1)
        val porSemanaMap = concluidas.groupBy { Fluxo.semanaDe(requireNotNull(it.resolved), zona) }

        val porTipoCiclos = comCiclo.groupBy { it.issueType }
        val faixasPorTipo = Fluxo.faixasPorTipo(porTipoCiclos.mapValues { (_, l) -> l.map { ciclos.getValue(it.key) } })
        val porProjeto = concluidas.groupBy { it.key.substringBefore('-') }.toSortedMap()

        // Pessoas visíveis, em ordem alfabética: sem posição, sem "melhor/pior".
        val participantes = concluidas.associateWith { s ->
            buildSet {
                s.timeline.initialAssignee?.let(::add)
                s.timeline.assigneeChanges.forEach { c -> c.to?.let(::add) }
                s.timeline.currentAssignee?.let(::add)
            }
        }
        val finalizou = concluidas.groupBy { Fluxo.responsavelEm(it, requireNotNull(it.resolved)) }.filterKeys { it != null && it in visiveis }
        val pessoas = (finalizou.keys.filterNotNull() + participantes.values.flatten().filter { it in visiveis }).toSortedSet()

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        segmentos["concluidas"] = concluidas
        segmentos["comPontos"] = concluidas.filter { it.storyPoints != null }
        segmentos["ciclo"] = comCiclo
        segmentos["lead"] = comLead
        segmentos["noPrazo"] = noPrazo
        segmentos["noPrazoAjustado"] = ajustadas
        segmentos["comVencimento"] = comVencimento
        segmentos["canceladas"] = canceladas
        segmentos["semResolution"] = semResolution
        segmentos["dispersao"] = comCiclo
        for ((semana, _) in serie) segmentos["semana:$semana"] = porSemanaMap[semana].orEmpty()
        for ((tipo, l) in porTipoCiclos) segmentos["tipo:$tipo"] = l
        for ((projeto, l) in porProjeto) segmentos["projeto:$projeto"] = l
        val porCampo = br.sidia.painel.cards.agruparPorCampos(concluidas, camposDeFiltro)
        segmentos.putAll(porCampo.segmentos)
        for (p in pessoas) {
            segmentos["pessoa:$p:finalizou"] = finalizou[p].orEmpty()
            segmentos["pessoa:$p:participou"] = concluidas.filter { p in participantes.getValue(it) }
        }
        val ordenados = segmentos.mapValues { (_, l) -> l.sortedWith(Comparator { a, b -> ORDEM_NATURAL_DE_CHAVES.compare(a.key, b.key) }) }

        // FE-11: o tempo em status ATIVO enquanto a pessoa era a responsável, somado nas concluídas do período.
        val tempoAtivo = HashMap<String, Double>()
        for (s in concluidas) {
            for (iv in TimelineBuilder.intervals(s.timeline, agora, map)) {
                val dono = iv.assignee ?: continue
                if (iv.category != StatusCategory.ACTIVE || dono !in pessoas) continue
                val dias = if (unit == TimeUnitMode.UTIL) cal.businessDays(iv.start, iv.end) else cal.calendarDays(iv.start, iv.end)
                tempoAtivo.merge(dono, dias, Double::plus)
            }
        }

        val porPessoa = pessoas.map { p ->
            val dele = ordenados.getValue("pessoa:$p:finalizou")
            val pontosDele = dele.mapNotNull { it.storyPoints }
            val estimadas = dele.mapNotNull { it.estimateS }
            val gastas = dele.mapNotNull { it.spentS }
            EntregaPessoaDto(
                pessoa = p, finalizou = dele.size, participou = ordenados.getValue("pessoa:$p:participou").size,
                pontos = pontosDele.takeIf { it.isNotEmpty() }?.sum(),
                cicloP50 = Percentiles.median(dele.mapNotNull { ciclos[it.key] }),
                horasEstimadas = estimadas.takeIf { it.isNotEmpty() }?.let { it.sum() / 3600.0 },
                horasGastas = gastas.takeIf { it.isNotEmpty() }?.let { it.sum() / 3600.0 },
                tempoAtivoDias = tempoAtivo[p],
            )
        }

        // A situação do prazo de cada linha vem da MESMA classificação que dá os números (nunca uma conta à parte).
        fun situacaoDoPrazo(s: IssueSnapshot): String {
            val venc = s.duedate ?: return "semVencimento"
            val classe = NoPrazo.classificar(diaDaResolucao.getValue(s.key), venc, s.labels, labels)
            return when {
                classe.noPrazo -> "noPrazo"
                classe.ajustado -> "ajustado"
                else -> "atrasada"
            }
        }
        val avisos = mutableListOf<AvisoDto>()
        avisos += Fluxo.avisosDeAmostra(faixasPorTipo)
        if (semResolution.isNotEmpty()) {
            avisos += AvisoDto(
                AvisoDto.DONE_SEM_RESOLUTION, AvisoDto.INFORMATIVO, "${semResolution.size} issue(s) concluídas sem resolução",
                mapOf("n" to semResolution.size.toString()),
            )
        }

        val dto = FechadasDto(
            concluidas = concluidas.size,
            pontos = concluidas.mapNotNull { it.storyPoints }.takeIf { it.isNotEmpty() }?.sum(),
            comPontos = ordenados.getValue("comPontos").size,
            ciclo = faixa(comCiclo.map { ciclos.getValue(it.key) }),
            lead = faixa(comLead.map { leads.getValue(it.key) }),
            noPrazo = resultado.noPrazo,
            noPrazoAjustado = resultado.noPrazoAjustado,
            comVencimento = resultado.concluidas,
            taxaNoPrazo = resultado.taxa,
            taxaAjustada = resultado.taxaAjustada,
            canceladas = canceladas.size,
            porSemana = serie.map { (semana, _) -> SemanaDto(semana.toString(), ordenados.getValue("semana:$semana").size) },
            dispersao = comCiclo.takeIf { it.isNotEmpty() }?.let { l ->
                val v = l.map { ciclos.getValue(it.key) }
                DispersaoDto(
                    requireNotNull(Percentiles.of(v, 0.5)), requireNotNull(Percentiles.of(v, 0.85)), requireNotNull(Percentiles.of(v, 0.95)),
                    ordenados.getValue("dispersao").map { PontoCicloDto(it.key, diaDaResolucao.getValue(it.key).toString(), ciclos.getValue(it.key)) },
                )
            },
            porTipo = faixasPorTipo.map { (tipo, f) -> FaixaTipoDto(tipo, f.p50, f.p85, f.n) },
            porProjeto = porProjeto.keys.map { ContagemDto(it, ordenados.getValue("projeto:$it").size) },
            porPessoa = porPessoa,
            linhas = ordenados.getValue("concluidas").take(LIMITE_DE_LINHAS).map { s ->
                LinhaFechadaDto(
                    key = s.key, resumo = s.summary, tipo = s.issueType, responsavel = s.assignee,
                    resolvida = diaDaResolucao.getValue(s.key).toString(), ciclo = ciclos[s.key], lead = leads[s.key],
                    vence = s.duedate?.toString(), prazo = situacaoDoPrazo(s), pontos = s.storyPoints,
                    horasEstimadas = s.estimateS?.let { it / 3600.0 }, horasGastas = s.spentS?.let { it / 3600.0 },
                    realSobreEstimado = s.estimateS?.takeIf { it > 0 }?.let { est -> s.spentS?.let { it.toDouble() / est } },
                )
            },
            semResolution = semResolution.size,
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido",
            avisos = avisos,
            porCampo = porCampo.dtos,
        )
        return FechadasAnalise(dto, ordenados, ciclos, leads, diaDaResolucao)
    }

    companion object {
        /** Quantas concluídas a tabela (FE-12) leva na resposta; a contagem e a gaveta trazem todas. */
        const val LIMITE_DE_LINHAS = 500
    }
}
