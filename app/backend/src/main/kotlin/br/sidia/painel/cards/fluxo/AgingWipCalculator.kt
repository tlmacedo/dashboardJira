package br.sidia.painel.cards.fluxo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeInStatusCalculator
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant

/**
 * O DTO e, por segmento, as tasks por trás de cada número. [idades] é a idade (na unidade pedida) de cada task em
 * andamento, por chave, e [p85PorEtapa] o P85 das etapas que têm faixa (para o texto "acima do normal" da gaveta).
 */
class AgingWipAnalise(
    val dto: AgingWipDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    val idades: Map<String, Double>,
    val p85PorEtapa: Map<String, Double>,
)

/**
 * Envelhecimento por etapa (Aging WIP, I-05). As etapas são os status em andamento (ativo, espera e bloqueado), na
 * ordem do `status_map` (os que não constam na ordem vêm depois, em ordem alfabética). A idade de uma task é o tempo
 * desde a ÚLTIMA entrada no status atual ([Fluxo.diasNoStatusAtual]); a faixa da etapa é o P50 e o P85 que as
 * concluídas passaram naquele status, calculados pelo [TimeInStatusCalculator] (a mesma conta do Tempo em status). Etapa
 * com menos de [Fluxo.AMOSTRA_MINIMA] concluídas no histórico não tem faixa: ninguém fica "acima do normal" nela.
 * Status fora do mapa propaga `UnmappedStatusException`.
 */
class AgingWipCalculator(private val cal: BusinessCalendar, private val map: StatusMap) {
    private val emCurso = setOf(StatusCategory.ACTIVE, StatusCategory.WAIT, StatusCategory.BLOCKED)

    /** [abertas]: as abertas do escopo. [historico]: as resolvidas das últimas semanas, no escopo (a faixa sai das concluídas positivas). */
    fun analisar(abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode): AgingWipAnalise {
        val andamento = abertas.filter { Fluxo.emAndamento(it, map) }
        val positivas = historico.filter { Fluxo.concluidaPositiva(it, map) }
        val tempo = TimeInStatusCalculator(cal, map).compute(positivas.map { it.timeline }, emptyList(), agora, unit)
        val estatisticas = tempo.perStatus.associateBy { it.status }

        val daOrdem = map.ordem.filter { map.category(it) in emCurso }
        val extras = andamento.map { it.status }.distinct().filter { it !in daOrdem }.sorted()
        val idades = andamento.associate { it.key to Fluxo.diasNoStatusAtual(it, cal, unit, agora) }

        fun ordenadas(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val p85PorEtapa = LinkedHashMap<String, Double>()
        val avisos = mutableListOf<AvisoDto>()
        if (map.ordem.isEmpty()) {
            avisos += AvisoDto(AvisosDoFluxo.ORDEM_DO_FLUXO_AUSENTE, AvisoDto.INFORMATIVO, "ordem do fluxo ausente")
        }
        val etapas = (daOrdem + extras).map { status ->
            val tasks = andamento.filter { it.status == status }
            val estat = estatisticas[status]
            val faixa = estat?.takeIf { it.n >= Fluxo.AMOSTRA_MINIMA }?.let { FaixaDaEtapaDto(it.median, it.p85, it.n) }
            faixa?.let { p85PorEtapa[status] = it.p85 }
            val pontos = tasks.map { PontoDeIdadeDto(it.key, idades.getValue(it.key), faixa != null && idades.getValue(it.key) > faixa.p85) }
                .sortedWith(compareBy<PontoDeIdadeDto> { -it.idade }.then(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key }))
            val acima = pontos.filter { it.acima }.map { it.key }.toSet()
            segmentos["etapa:$status"] = ordenadas(tasks)
            segmentos["acima:$status"] = ordenadas(tasks.filter { it.key in acima })
            if (tasks.isNotEmpty() && faixa == null) {
                avisos += AvisoDto(
                    AvisoDto.AMOSTRA_PEQUENA, AvisoDto.INFORMATIVO, "amostra pequena para $status",
                    mapOf("tipo" to status, "n" to (estat?.n ?: 0).toString(), "minimo" to Fluxo.AMOSTRA_MINIMA.toString()),
                )
            }
            EtapaDto(status, map.category(status).name, tasks.size, acima.size, estat?.n ?: 0, faixa, pontos)
        }
        segmentos["emAndamento"] = ordenadas(andamento)
        segmentos["acimaDoNormal"] = ordenadas(etapas.flatMap { e -> segmentos.getValue("acima:${e.status}") })
        val dto = AgingWipDto(
            etapas = etapas, emAndamento = andamento.size, acimaDoNormal = segmentos.getValue("acimaDoNormal").size,
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido", avisos = avisos,
        )
        return AgingWipAnalise(dto, segmentos, idades, p85PorEtapa)
    }
}
