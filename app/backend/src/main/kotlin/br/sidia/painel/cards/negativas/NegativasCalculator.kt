package br.sidia.painel.cards.negativas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.LabelsDeAtraso
import br.sidia.painel.domain.NoPrazo
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.Transition
import br.sidia.painel.domain.TimelineBuilder
import br.sidia.painel.domain.classificar
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.temporal.ChronoUnit

/** O DTO e, por segmento, as issues que formam cada número, com o motivo que a gaveta mostra em cada linha. */
class NegativasAnalise(
    val dto: NegativasDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    /** Motivo por segmento e chave (ex.: "Reaberta em 2026-10-21: Closed para In Progress"). */
    val motivos: Map<String, Map<String, String>>,
)

/**
 * Card Negativas (NE-01 a NE-08 e NE-10). Os eventos valem pela DATA DO CHANGELOG dentro de `[de, ate)`, nunca pelo
 * `updated`:
 * - reaberta: transição de status de categoria DONE para outra categoria;
 * - devolvida: transição para um status de índice MENOR na `ordem` do fluxo (a reabertura não conta aqui; é a
 *   "reaberta"); sem `ordem` configurada fica em 0 com o aviso `ORDEM_DO_FLUXO_AUSENTE`;
 * - muitas trocas: pelo menos [trocas] mudanças de responsável no período.
 * Canceladas, concluídas com atraso e estouro saem das resolvidas no período (concluída positiva = categoria DONE,
 * `resolution` fora das negativas e `resolved`); "abertas vencidas" é o estado de hoje. O atraso usa a MESMA conta de
 * "no prazo" da Auditoria, de Fechadas e de Metas ([classificar]); atrasada sem justificativa não tem label que
 * justifique. Cada contagem por pessoa vai para o responsável NO INSTANTE do evento (ou da resolução).
 */
class NegativasCalculator(
    private val cal: BusinessCalendar,
    private val map: StatusMap,
    private val labels: LabelsDeAtraso,
    private val estouro: Double = 1.25,
    private val trocas: Int = 3,
) {
    companion object {
        /** Acima desta fração a tela marca ▲ (spec do app atual: 20%). */
        const val LIMITE_DA_TAXA = 0.20
    }

    fun calcular(
        comEventos: List<IssueSnapshot>, resolvidas: List<IssueSnapshot>, abertas: List<IssueSnapshot>,
        de: Instant, ate: Instant, visiveis: Set<String>, agora: Instant, unit: String = "util",
    ): NegativasDto = analisar(comEventos, resolvidas, abertas, de, ate, visiveis, agora, unit).dto

    private class Evento(val s: IssueSnapshot, val t: Transition)

    fun analisar(
        comEventos: List<IssueSnapshot>, resolvidas: List<IssueSnapshot>, abertas: List<IssueSnapshot>,
        de: Instant, ate: Instant, visiveis: Set<String>, agora: Instant, unit: String = "util",
    ): NegativasAnalise {
        val avisos = mutableListOf<AvisoDto>()
        val motivos = LinkedHashMap<String, MutableMap<String, String>>()
        fun motivo(segmento: String, s: IssueSnapshot, texto: String) {
            motivos.getOrPut(segmento) { LinkedHashMap() }[s.key] = texto
        }
        fun dia(i: Instant) = cal.localDate(i)
        fun noPeriodo(i: Instant) = !i.isBefore(de) && i.isBefore(ate)

        // --- Eventos do changelog dentro do período ---
        val reabertas = LinkedHashMap<String, Evento>()
        val devolvidas = LinkedHashMap<String, Evento>()
        val temOrdem = map.ordem.isNotEmpty()
        for (s in comEventos) {
            for (t in s.timeline.transitions.sortedBy { it.at }) {
                val origem = t.from ?: continue
                if (!noPeriodo(t.at)) continue
                if (map.category(origem) == StatusCategory.DONE && map.category(t.to) != StatusCategory.DONE) {
                    reabertas.putIfAbsent(s.key, Evento(s, t))
                } else if (temOrdem) {
                    val de0 = map.indiceNaOrdem(origem)
                    val para = map.indiceNaOrdem(t.to)
                    if (de0 != null && para != null && para < de0) devolvidas.putIfAbsent(s.key, Evento(s, t))
                }
            }
        }
        if (!temOrdem) {
            avisos += AvisoDto(
                AvisoDto.ORDEM_DO_FLUXO_AUSENTE, AvisoDto.INFORMATIVO,
                "a ordem do fluxo não está configurada: devolvidas não podem ser calculadas",
            )
        }
        val comTrocas = comEventos.filter { s -> s.timeline.assigneeChanges.count { noPeriodo(it.at) } >= trocas }

        // --- Resolvidas no período ---
        val noIntervalo = resolvidas.filter { s -> s.resolved?.let(::noPeriodo) == true }
        val concluidas = noIntervalo.filter { Fluxo.concluidaPositiva(it, map) }
        val canceladas = noIntervalo.filter { Fluxo.cancelada(it, map) }
        val classes = concluidas.mapNotNull { s ->
            val venc = s.duedate ?: return@mapNotNull null
            s to NoPrazo.classificar(dia(requireNotNull(s.resolved)), venc, s.labels, labels)
        }
        val comAtraso = classes.filter { !it.second.noPrazo }.map { it.first }
        val atrasadasSemJustificativa = classes.filter { !it.second.ajustado }.map { it.first }
        val estourou = concluidas.filter { s ->
            val est = s.estimateS
            val gasto = s.spentS
            est != null && est > 0 && gasto != null && gasto > estouro * est
        }

        // --- Estado de hoje ---
        val hoje = dia(agora)
        val vencidas = abertas.filter { map.category(it.status) != StatusCategory.DONE && it.duedate?.isBefore(hoje) == true }

        // --- Motivos das linhas da gaveta ---
        reabertas.values.forEach { motivo("reabertas", it.s, "Reaberta em ${dia(it.t.at)}: ${it.t.from} para ${it.t.to}") }
        devolvidas.values.forEach { motivo("devolvidas", it.s, "Devolvida em ${dia(it.t.at)}: ${it.t.from} para ${it.t.to}") }
        canceladas.forEach { motivo("canceladas", it, "Cancelada em ${dia(requireNotNull(it.resolved))}: resolução ${it.resolution}") }
        for ((s, c) in classes) {
            if (c.noPrazo) continue
            val justificada = if (c.ajustado) "; atraso justificado por label" else "; sem label que justifique"
            val texto = "Vencia em ${s.duedate}, resolvida em ${dia(requireNotNull(s.resolved))} (${c.delta} dia(s) depois)$justificada"
            motivo("concluidasComAtraso", s, texto)
            if (!c.ajustado) motivo("atrasadasSemJustificativa", s, texto)
        }
        vencidas.forEach {
            motivo("abertasVencidas", it, "Vencia em ${it.duedate} (${ChronoUnit.DAYS.between(it.duedate, hoje)} dia(s) de atraso)")
        }
        estourou.forEach {
            motivo("estouroDeEstimativa", it, "Gasto ${horas(it.spentS)} h para estimativa de ${horas(it.estimateS)} h")
        }
        comTrocas.forEach { s ->
            motivo("muitasTrocas", s, "${s.timeline.assigneeChanges.count { noPeriodo(it.at) }} trocas de responsável no período")
        }
        concluidas.forEach { motivo("concluidas", it, "Resolvida em ${dia(requireNotNull(it.resolved))}") }

        // --- NE-09: o status onde cada task das quatro listas ficou MAIS tempo (na unidade pedida) ---
        val negativas = (reabertas.values.map { it.s } + devolvidas.values.map { it.s } + canceladas + comAtraso).distinctBy { it.key }
        val porMaiorTempo = LinkedHashMap<String, MutableList<IssueSnapshot>>()
        for (s in negativas) {
            val dias = LinkedHashMap<String, Double>()
            for (iv in TimelineBuilder.intervals(s.timeline, agora, map)) {
                dias.merge(iv.status, if (unit == "util") cal.businessDays(iv.start, iv.end) else cal.calendarDays(iv.start, iv.end), Double::plus)
            }
            val maior = dias.maxByOrNull { it.value } ?: continue
            porMaiorTempo.getOrPut(maior.key) { mutableListOf() } += s
            motivo("maiorTempo:${maior.key}", s, "Maior tempo em ${maior.key}: ${String.format(java.util.Locale.ROOT, "%.1f", maior.value)} dia(s)")
        }

        // --- Contagem por pessoa (posse no instante do evento) ---
        val porPessoa = HashMap<String, Triple<MutableList<IssueSnapshot>, MutableList<IssueSnapshot>, MutableList<IssueSnapshot>>>()
        fun lista(p: String) = porPessoa.getOrPut(p) { Triple(mutableListOf(), mutableListOf(), mutableListOf()) }
        reabertas.values.forEach { e -> Fluxo.responsavelEm(e.s, e.t.at)?.takeIf { it in visiveis }?.let { lista(it).first += e.s } }
        devolvidas.values.forEach { e -> Fluxo.responsavelEm(e.s, e.t.at)?.takeIf { it in visiveis }?.let { lista(it).second += e.s } }
        comAtraso.forEach { s -> Fluxo.responsavelEm(s, requireNotNull(s.resolved))?.takeIf { it in visiveis }?.let { lista(it).third += s } }

        fun ordenar(l: Collection<IssueSnapshot>) = l.distinctBy { it.key }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        segmentos["reabertas"] = ordenar(reabertas.values.map { it.s })
        segmentos["devolvidas"] = ordenar(devolvidas.values.map { it.s })
        segmentos["canceladas"] = ordenar(canceladas)
        segmentos["concluidasComAtraso"] = ordenar(comAtraso)
        segmentos["atrasadasSemJustificativa"] = ordenar(atrasadasSemJustificativa)
        segmentos["abertasVencidas"] = ordenar(vencidas)
        segmentos["estouroDeEstimativa"] = ordenar(estourou)
        segmentos["muitasTrocas"] = ordenar(comTrocas)
        segmentos["concluidas"] = ordenar(concluidas)
        // Os numeradores das taxas abrem as mesmas tasks dos cartões de contagem.
        segmentos["taxa:reabertura"] = segmentos.getValue("reabertas")
        segmentos["taxa:devolucao"] = segmentos.getValue("devolvidas")
        segmentos["taxa:atraso"] = segmentos.getValue("concluidasComAtraso")
        segmentos["taxa:atrasoSemJustificativa"] = segmentos.getValue("atrasadasSemJustificativa")
        for (status in porMaiorTempo.keys.sortedBy { it.lowercase() }) segmentos["maiorTempo:$status"] = ordenar(porMaiorTempo.getValue(status))
        for (p in porPessoa.keys.sorted()) {
            val (r, d, a) = porPessoa.getValue(p)
            segmentos["pessoa:$p:reabertas"] = ordenar(r)
            segmentos["pessoa:$p:devolvidas"] = ordenar(d)
            segmentos["pessoa:$p:atrasadas"] = ordenar(a)
        }
        val textos = motivos.toMutableMap()
        for (chave in listOf("reabertas", "devolvidas", "concluidasComAtraso", "atrasadasSemJustificativa")) {
            val destino = when (chave) {
                "reabertas" -> "taxa:reabertura"
                "devolvidas" -> "taxa:devolucao"
                "concluidasComAtraso" -> "taxa:atraso"
                else -> "taxa:atrasoSemJustificativa"
            }
            motivos[chave]?.let { textos[destino] = it }
        }
        for (p in porPessoa.keys) {
            motivos["reabertas"]?.let { textos["pessoa:$p:reabertas"] = it }
            motivos["devolvidas"]?.let { textos["pessoa:$p:devolvidas"] = it }
            motivos["concluidasComAtraso"]?.let { textos["pessoa:$p:atrasadas"] = it }
        }

        val n = concluidas.size
        fun taxa(x: Int): Double? = if (n == 0) null else x.toDouble() / n
        val dto = NegativasDto(
            reabertas = segmentos.getValue("reabertas").size,
            devolvidas = segmentos.getValue("devolvidas").size,
            canceladas = segmentos.getValue("canceladas").size,
            concluidasComAtraso = segmentos.getValue("concluidasComAtraso").size,
            atrasadasSemJustificativa = segmentos.getValue("atrasadasSemJustificativa").size,
            abertasVencidas = segmentos.getValue("abertasVencidas").size,
            estouroDeEstimativa = segmentos.getValue("estouroDeEstimativa").size,
            muitasTrocas = segmentos.getValue("muitasTrocas").size,
            concluidas = n,
            taxaReabertura = taxa(segmentos.getValue("reabertas").size),
            taxaDevolucao = taxa(segmentos.getValue("devolvidas").size),
            taxaAtraso = taxa(segmentos.getValue("concluidasComAtraso").size),
            taxaAtrasoSemJustificativa = taxa(segmentos.getValue("atrasadasSemJustificativa").size),
            limiteDaTaxa = LIMITE_DA_TAXA,
            maiorTempoPorStatus = porMaiorTempo.keys.sortedBy { it.lowercase() }.map { br.sidia.painel.cards.ContagemGrupoDto(it, porMaiorTempo.getValue(it).size) },
            porResponsavel = porPessoa.keys.sorted().map { p ->
                val (r, d, a) = porPessoa.getValue(p)
                NegativaPessoaDto(p, r.size, d.size, a.size)
            },
            unit = unit,
            avisos = avisos,
        )
        return NegativasAnalise(dto, segmentos, textos)
    }

    private fun horas(segundos: Long?): String = segundos?.let { String.format(java.util.Locale.ROOT, "%.1f", it / 3600.0) } ?: "?"
}
