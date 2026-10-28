package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SerieSemanal
import java.time.LocalDate

/** O DTO e, por segmento, as tasks por trás de cada número (a gaveta abre exatamente estas). */
class FluxoDaSemanaAnalise(val dto: FluxoDaSemanaDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Fluxo da semana (I-01). Entradas, saídas e canceladas vêm da [SerieSemanal], a MESMA função da Previsão (nada é
 * recalculado aqui); só as "iniciadas" (primeira entrada em status ativo, de espera ou bloqueado) são contadas por
 * [Fluxo.inicioAtivo] e [Fluxo.semanaDe]. A janela termina na semana de [hoje] (em curso, incompleta); o sinal
 * "backlog crescendo" só olha as semanas FECHADAS. Status fora do mapa propaga `UnmappedStatusException`.
 */
class FluxoDaSemanaCalculator(private val cal: BusinessCalendar, private val map: StatusMap) {

    /** [issues]: as abertas e as resolvidas a partir da primeira semana da janela, já no escopo de quem consulta. */
    fun analisar(issues: List<IssueSnapshot>, hoje: LocalDate, semanas: Int = SEMANAS): FluxoDaSemanaAnalise {
        val zona = cal.zone
        val atual = Fluxo.semanaDe(hoje.atStartOfDay(zona).toInstant(), zona)
        val serie = SerieSemanal.calcular(issues, map, atual, semanas, zona)
        val iniciadas = issues.distinctBy { it.key }
            .mapNotNull { s -> Fluxo.inicioAtivo(s, map)?.let { Fluxo.semanaDe(it, zona) to s } }
            .groupBy({ it.first }, { it.second })

        fun ordenadas(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val linhas = serie.map { w ->
            val comecaram = ordenadas(iniciadas[w.semana].orEmpty())
            segmentos["entrada:${w.semana}"] = ordenadas(w.entradas)
            segmentos["inicio:${w.semana}"] = comecaram
            segmentos["saida:${w.semana}"] = ordenadas(w.saidas)
            segmentos["cancelada:${w.semana}"] = ordenadas(w.canceladas)
            SemanaDeFluxoDto(w.semana.toString(), w.entradas.size, comecaram.size, w.saidas.size, w.canceladas.size, w.semana == atual)
        }
        segmentos["entradas"] = ordenadas(serie.flatMap { it.entradas })
        segmentos["iniciadas"] = ordenadas(serie.flatMap { iniciadas[it.semana].orEmpty() })
        segmentos["saidas"] = ordenadas(serie.flatMap { it.saidas })
        segmentos["canceladas"] = ordenadas(serie.flatMap { it.canceladas })

        // Backlog crescendo: semanas FECHADAS seguidas, da mais recente para trás, em que a entrada SUPEROU a saída.
        val fechadas = linhas.filterNot { it.emCurso }
        val crescendo = fechadas.asReversed().takeWhile { it.entrou > it.saiu }.size
        val naJanela = (segmentos.getValue("entradas") + segmentos.getValue("iniciadas") + segmentos.getValue("saidas") + segmentos.getValue("canceladas"))
            .distinctBy { it.key }.size
        val avisos = if (naJanela < Fluxo.AMOSTRA_MINIMA) {
            listOf(
                AvisoDto(
                    AvisosDoFluxo.AMOSTRA_PEQUENA_NA_JANELA, AvisoDto.INFORMATIVO, "poucas tasks na janela do fluxo",
                    mapOf("n" to naJanela.toString(), "minimo" to Fluxo.AMOSTRA_MINIMA.toString()),
                ),
            )
        } else {
            emptyList()
        }
        val entrou = linhas.sumOf { it.entrou }
        val saiu = linhas.sumOf { it.saiu }
        val canceladas = linhas.sumOf { it.canceladas }
        val dto = FluxoDaSemanaDto(
            semanas = linhas, entrou = entrou, comecou = linhas.sumOf { it.comecou }, saiu = saiu, canceladas = canceladas,
            saldoDoBacklog = entrou - saiu - canceladas, semanasCrescendo = crescendo, backlogCrescendo = crescendo >= SEMANAS_DO_SINAL,
            semanasNaJanela = semanas, tasksNaJanela = naJanela, avisos = avisos,
        )
        return FluxoDaSemanaAnalise(dto, segmentos)
    }

    companion object {
        /** Quantas semanas o cartão mostra (a de hoje inclusive). */
        const val SEMANAS = 12

        /** Semanas fechadas seguidas com entrada acima da saída que acendem o sinal. */
        const val SEMANAS_DO_SINAL = 3
    }
}
