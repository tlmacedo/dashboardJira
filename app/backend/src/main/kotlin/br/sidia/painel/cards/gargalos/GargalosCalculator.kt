package br.sidia.painel.cards.gargalos

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.LocalDate

/** O DTO e as tasks por segmento (`status:<nome>`, `acimaDaCapacidade`, `comFolga`). */
class GargalosAnalise(val dto: GargalosDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Gargalos e fluxo: o fluxo cumulativo de [DIAS] dias reconstruído da linha do tempo de cada issue, o estado das etapas
 * (gargalo = acima do limite de WIP, ou faixa que cresceu pelo menos max(3, 30%) em 15 dias, só em etapa ativa ou de
 * espera) e as contagens de pessoas acima da capacidade e com folga. Domínio puro; status fora do mapa propaga
 * `UnmappedStatusException`.
 */
class GargalosCalculator(private val motor: MotorDePessoas) {
    companion object {
        const val DIAS = 60
        const val JANELA_DE_CRESCIMENTO = 15
        private const val CRESCIMENTO_MINIMO = 3
        private const val CRESCIMENTO_PROPORCIONAL = 0.30
    }

    private val map = motor.statusMap
    private val zona = motor.calendario.zone

    /** O status da issue em [em] (nulo antes da criação): a última transição até o instante, ou o status inicial. */
    private fun statusEm(s: IssueSnapshot, em: Instant): String? {
        if (em < s.created) return null
        return s.timeline.transitions.sortedBy { it.at }.lastOrNull { it.at <= em }?.to ?: s.timeline.initialStatus
    }

    /**
     * [issues]: abertas e resolvidas dentro da janela, da visão. [abertas]: as abertas (base de "hoje" e das pessoas).
     * [pessoas]: as da visão que constam na equipe.
     */
    fun calcular(
        pessoas: List<String>, issues: List<IssueSnapshot>, abertas: List<IssueSnapshot>, hoje: LocalDate, agora: Instant,
    ): GargalosAnalise {
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val dias = (DIAS - 1 downTo 0).map { hoje.minusDays(it.toLong()) }

        // Status do fluxo (sem o concluído), na ordem do fluxo; os que não estão na ordem entram depois, em ordem alfabética.
        val vistos = (issues + naoConcluidas).flatMap { listOf(it.timeline.initialStatus) + it.timeline.transitions.map { t -> t.to } + it.status }
            .distinct().filter { map.category(it) != StatusCategory.DONE }
        val status = map.ordem.filter { it in vistos } + vistos.filter { it !in map.ordem }.sorted()

        val series = status.associateWith { IntArray(DIAS) }
        for (s in issues.distinctBy { it.key }) {
            dias.forEachIndexed { i, d ->
                val fimDoDia = minOf(d.plusDays(1).atStartOfDay(zona).toInstant().minusMillis(1), agora)
                val st = statusEm(s, fimDoDia) ?: return@forEachIndexed
                series[st]?.let { it[i]++ }
            }
        }

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        fun ordena(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val etapas = status.map { st ->
            val categoria = map.category(st)
            val tasks = ordena(naoConcluidas.filter { it.status == st })
            segmentos["status:$st"] = tasks
            val serie = series.getValue(st)
            val agoraValor = serie[DIAS - 1]
            val antes = serie[DIAS - 1 - JANELA_DE_CRESCIMENTO]
            val crescimento = agoraValor - antes
            val limite = motor.regras.wipPorStatus[st]
            val ativaOuEspera = categoria == StatusCategory.ACTIVE || categoria == StatusCategory.WAIT
            val motivo = when {
                !ativaOuEspera -> null
                limite != null && tasks.size > limite -> "WIP_ACIMA_DO_LIMITE"
                crescimento >= maxOf(CRESCIMENTO_MINIMO.toDouble(), CRESCIMENTO_PROPORCIONAL * antes) -> "FAIXA_CRESCEU"
                else -> null
            }
            EtapaDto(st, categoria.name, tasks.size, limite, crescimento, motivo != null, motivo)
        }

        // Pessoas acima da capacidade e com folga (as mesmas contagens da Carga, sobre o horizonte de 4 semanas).
        val acima = mutableListOf<IssueSnapshot>()
        val folga = mutableListOf<IssueSnapshot>()
        var pessoasAcima = 0
        var pessoasComFolga = 0
        for (p in pessoas) {
            val o = motor.demanda.carga(p, naoConcluidas, hoje, 4).ocupacaoHorizonte
            val minhas = naoConcluidas.filter { it.assignee == p }
            if (o is Ocupacao.Valor && o.fracao > motor.regras.ocupacaoAlta) {
                pessoasAcima++
                acima += minhas
            }
            if (o is Ocupacao.Valor && o.fracao < motor.regras.folga) {
                pessoasComFolga++
                folga += minhas
            }
        }
        segmentos["acimaDaCapacidade"] = ordena(acima)
        segmentos["comFolga"] = ordena(folga)

        val dto = GargalosDto(
            dias = dias.map { it.toString() }, faixas = status.map { FaixaFluxoDto(it, series.getValue(it).toList()) }, etapas = etapas,
            acimaDaCapacidade = pessoasAcima, comFolga = pessoasComFolga, tasksAcimaDaCapacidade = acima.size, tasksComFolga = folga.size,
            avisos = avisosDePessoas(motor, naoConcluidas, false),
        )
        return GargalosAnalise(dto, segmentos)
    }
}
