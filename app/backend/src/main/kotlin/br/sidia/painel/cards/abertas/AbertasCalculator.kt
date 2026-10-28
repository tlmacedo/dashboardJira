package br.sidia.painel.cards.abertas

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.time.BusinessCalendar
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant

/** Resultado do cálculo: o DTO e, por segmento, as issues que formam cada número (a gaveta abre exatamente estas). */
class AbertasAnalise(
    val dto: AbertasDto,
    val segmentos: Map<String, List<IssueSnapshot>>,
    /** Ciclo (na unidade pedida) das concluídas do histórico, por chave, para o "detalhe" da gaveta. */
    val ciclos: Map<String, Double>,
    /** Idade (na unidade pedida) de cada aberta, por chave. */
    val idades: Map<String, Double>,
)

/**
 * Card Abertas (AB-01 a AB-09). Abertas = `resolved` nulo e categoria diferente de DONE (status concluído sem
 * `resolved` vai para o aviso `DONE_SEM_RESOLUTION`). O histórico são as concluídas positivas das últimas 16 semanas
 * dentro do escopo; AB-04 compara a idade de cada issue EM ANDAMENTO com o P85 do ciclo do PRÓPRIO tipo.
 */
class AbertasCalculator(private val cal: BusinessCalendar, private val map: StatusMap) {
    companion object {
        const val SEM_PRIORIDADE = "(sem)"
        val FAIXAS_DE_IDADE = listOf("0-7", "8-14", "15-30", ">30")
        val FAIXAS_DE_VENCIMENTO = listOf("vencidas", "7d", "depois", "sem")
        private val ORDEM_DE_PRIORIDADE = listOf("Highest", "High", "Medium", "Low", "Lowest")

        fun faixaDeIdade(idade: Double): String = when {
            idade <= 7 -> "0-7"
            idade <= 14 -> "8-14"
            idade <= 30 -> "15-30"
            else -> ">30"
        }
    }

    fun calcular(
        abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode,
        temCamposDeTrabalho: Boolean = true,
    ): AbertasDto = analisar(abertas, historico, agora, unit, temCamposDeTrabalho).dto

    fun analisar(
        todas: List<IssueSnapshot>, historico: List<IssueSnapshot>, agora: Instant, unit: TimeUnitMode,
        temCamposDeTrabalho: Boolean = true,
    ): AbertasAnalise {
        val hoje = cal.localDate(agora)
        val avisos = mutableListOf<AvisoDto>()

        // Status concluído sem `resolved`: não é aberta (nem entrega); fica de fora e vira aviso.
        val (concluidasSemData, abertas) = todas.partition { map.category(it.status) == StatusCategory.DONE }
        if (concluidasSemData.isNotEmpty()) {
            avisos += AvisoDto(
                AvisoDto.DONE_SEM_RESOLUTION, AvisoDto.INFORMATIVO, "${concluidasSemData.size} issue(s) concluídas sem resolução",
                mapOf("n" to concluidasSemData.size.toString()),
            )
        }
        if (!temCamposDeTrabalho) {
            avisos += AvisoDto(AvisoDto.CAMPOS_DE_TRABALHO_INDISPONIVEIS, AvisoDto.INFORMATIVO, "prioridade e estimativas ainda não sincronizadas")
        }

        // Histórico: ciclo por tipo (na unidade pedida) das concluídas positivas.
        val ciclos = HashMap<String, Double>()
        val historicoPorTipo = historico.filter { Fluxo.concluidaPositiva(it, map) }.mapNotNull { s ->
            Fluxo.cicloDias(s, map, cal, unit)?.also { ciclos[s.key] = it }?.let { s }
        }.groupBy { it.issueType }
        val faixas = Fluxo.faixasPorTipo(historicoPorTipo.mapValues { (_, l) -> l.map { ciclos.getValue(it.key) } })
        avisos += Fluxo.avisosDeAmostra(faixas)

        val idades = abertas.associate { it.key to Fluxo.idadeDias(it, map, cal, unit, agora) }
        val emAndamento = abertas.filter { Fluxo.emAndamento(it, map) }
        val acima = emAndamento.filter { s ->
            val p85 = faixas[s.issueType]?.p85
            p85 != null && idades.getValue(s.key) > p85
        }
        val semHistorico = emAndamento.count { it.issueType !in faixas }
        if (semHistorico > 0) {
            avisos += AvisoDto(
                AvisoDto.SEM_HISTORICO_DO_TIPO, AvisoDto.INFORMATIVO, "$semHistorico issue(s) de tipo sem histórico",
                mapOf("n" to semHistorico.toString()),
            )
        }

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        segmentos["abertas"] = abertas
        segmentos["emAndamento"] = emAndamento
        segmentos["acimaDoP85"] = acima

        val porStatus = abertas.groupBy { it.status }.entries
            .sortedWith(compareBy<Map.Entry<String, List<IssueSnapshot>>>({ map.indiceNaOrdem(it.key) ?: Int.MAX_VALUE }, { it.key }))
        porStatus.forEach { segmentos["status:${it.key}"] = it.value }

        // Sem prioridade sincronizada em NENHUMA issue, a tabela some (com aviso); nunca vira "tudo sem prioridade".
        val porPrioridade = if (!temCamposDeTrabalho) {
            emptyList()
        } else {
            abertas.groupBy { it.priority ?: SEM_PRIORIDADE }.entries
                .sortedWith(compareBy({ ORDEM_DE_PRIORIDADE.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.key }))
        }
        porPrioridade.forEach { segmentos["prioridade:${it.key}"] = it.value }

        for (f in FAIXAS_DE_IDADE) segmentos["idade:$f"] = abertas.filter { faixaDeIdade(idades.getValue(it.key)) == f }
        for (f in FAIXAS_DE_VENCIMENTO) segmentos["vence:$f"] = abertas.filter { faixaDeVencimento(it, hoje) == f }
        historicoPorTipo.forEach { (tipo, l) -> segmentos["historico:$tipo"] = l }

        val ordenados = segmentos.mapValues { (_, l) -> l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key }) }

        val dto = AbertasDto(
            abertas = abertas.size,
            emAndamento = emAndamento.size,
            acimaDoP85 = acima.size,
            historicoPorTipo = faixas.map { (tipo, f) -> FaixaTipoDto(tipo, f.p50, f.p85, f.n) },
            porStatus = porStatus.map { ContagemDto(it.key, it.value.size) },
            porPrioridade = porPrioridade.map { ContagemDto(it.key, it.value.size) },
            porIdade = FAIXAS_DE_IDADE.map { ContagemDto(it, ordenados.getValue("idade:$it").size) },
            porVencimento = FAIXAS_DE_VENCIMENTO.map { ContagemDto(it, ordenados.getValue("vence:$it").size) },
            linhas = ordenados.getValue("abertas").map { s ->
                LinhaAbertaDto(
                    key = s.key, resumo = s.summary, tipo = s.issueType, status = s.status, prioridade = s.priority,
                    responsavel = s.assignee, idade = idades.getValue(s.key), vence = s.duedate?.toString(),
                    horasRestantes = s.remainingS?.let { it / 3600.0 },
                )
            },
            unit = if (unit == TimeUnitMode.UTIL) "util" else "corrido",
            avisos = avisos,
        )
        return AbertasAnalise(dto, ordenados, ciclos, idades)
    }

    private fun faixaDeVencimento(s: IssueSnapshot, hoje: java.time.LocalDate): String {
        val venc = s.duedate ?: return "sem"
        return when {
            venc.isBefore(hoje) -> "vencidas"
            !venc.isAfter(hoje.plusDays(7)) -> "7d"
            else -> "depois"
        }
    }
}
