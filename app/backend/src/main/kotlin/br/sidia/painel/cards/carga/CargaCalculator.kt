package br.sidia.painel.cards.carga

import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.cards.paraDto
import br.sidia.painel.cards.riscos.RiscoCalculator
import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.TimeUnitMode
import br.sidia.painel.pessoas.Jain
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.riscos.RegrasDeRisco
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** O DTO e, por segmento, as tasks por trás de cada número (a gaveta abre exatamente estas). */
class CargaAnalise(val dto: CargaDto, val segmentos: Map<String, List<IssueSnapshot>>, val horasAssumidasPorChave: Set<String>)

/**
 * Carga por pessoa: fila, capacidade, ocupação do horizonte de [SEMANAS] semanas e os sinais de cada um. Domínio puro sobre o
 * motor de capacidade. Nada ordena pessoas por carga: [pessoas] já vem em ordem alfabética e é a ordem das linhas.
 */
class CargaCalculator(private val motor: MotorDePessoas, private val regrasDeRisco: RegrasDeRisco) {
    companion object {
        const val SEMANAS = 4
    }

    private val map = motor.statusMap
    private val regras = motor.regras

    /**
     * [pessoas]: as da visão que constam na equipe. [abertas]: as abertas da visão. [historico]: as concluídas recentes (faixa
     * de ciclo por tipo, base de "acima do P85" e do risco). [temCampos]: a base já tem prioridade e estimativas sincronizadas.
     * [horasRegistradas30d]: horas do worklog por autor nos últimos 30 dias (nulo ou vazio = worklog não sincronizado).
     */
    fun calcular(
        pessoas: List<String>, abertas: List<IssueSnapshot>, historico: List<IssueSnapshot>, hoje: LocalDate, agora: Instant,
        temCampos: Boolean, versaoRegras: String, horasRegistradas30d: Map<String, Double>? = null,
    ): CargaAnalise {
        val naoConcluidas = abertas.filter { map.category(it.status) != StatusCategory.DONE }
        val calcRisco = RiscoCalculator(motor.calendario, map, regrasDeRisco, motor.agenda.comoPrevisao(naoConcluidas, hoje))
        val riscoPorChave = calcRisco.avaliar(naoConcluidas, historico, agora, temCampos).associateBy { it.key }
        val faixas = calcRisco.faixasPorTipo(historico)
        val fimDaJanela = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(SEMANAS.toLong()).minusDays(1)

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        fun ordena(l: List<IssueSnapshot>) = l.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val linhas = mutableListOf<LinhaCargaDto>()
        val assumidas = mutableSetOf<String>()
        val acima = mutableListOf<IssueSnapshot>()
        val folga = mutableListOf<IssueSnapshot>()
        var pessoasAcima = 0
        var pessoasComFolga = 0
        val ocupacoes = mutableListOf<Ocupacao>()

        for (p in pessoas) {
            val minhas = naoConcluidas.filter { it.assignee == p }
            val carga = motor.demanda.carga(p, naoConcluidas, hoje, SEMANAS)
            val demandaTotal = carga.semanas.sumOf { it.demandaH }
            val noAndamento = minhas.filter { Fluxo.emAndamento(it, map) }
            val vencidas = minhas.filter { it.duedate?.isBefore(hoje) == true }
            val acimaP85 = noAndamento.filter { s ->
                val f = faixas[s.issueType]
                f != null && Fluxo.idadeDias(s, map, motor.calendario, TimeUnitMode.UTIL, agora) > f.p85
            }
            val altas = minhas.filter { riscoPorChave[it.key]?.nivel == "alto" }
            minhas.filter { motor.horas.de(it).assumidas }.forEach { assumidas += it.key }

            segmentos["pessoa:$p:fila"] = ordena(minhas)
            segmentos["pessoa:$p:wip"] = ordena(noAndamento)
            segmentos["pessoa:$p:vencidas"] = ordena(vencidas)
            segmentos["pessoa:$p:acimaDoP85"] = ordena(acimaP85)
            segmentos["pessoa:$p:riscoAlto"] = ordena(altas)

            val o = carga.ocupacaoHorizonte
            ocupacoes += o
            if (o is Ocupacao.Valor && o.fracao > regras.ocupacaoAlta) {
                pessoasAcima++
                acima += minhas
            }
            if (o is Ocupacao.Valor && o.fracao < regras.folga) {
                pessoasComFolga++
                folga += minhas
            }
            linhas += LinhaCargaDto(
                pessoa = p, abertas = minhas.size, wip = noAndamento.size,
                filaH = minhas.sumOf { motor.horas.de(it).valor },
                capacidade4SemH = motor.capacidade.semanas(p, hoje, SEMANAS).sumOf { it.second },
                ocupacao = o.paraDto(regras, demandaTotal),
                ausenciaDias = motor.capacidade.diasDeAusencia(p, hoje, fimDaJanela),
                // Mapa vazio = worklog ainda não sincronizado: "indisponível" (nulo), nunca "0 h" enganoso.
                horasRegistradas30d = horasRegistradas30d?.takeIf { it.isNotEmpty() }?.let { it[p] ?: 0.0 }, acimaDoP85 = acimaP85.size, vencidas = vencidas.size, riscoAlto = altas.size,
                atrasoAcumuladoH = carga.atrasoAcumuladoH, horasAssumidas = carga.horasAssumidas,
            )
        }
        segmentos["acimaDaCapacidade"] = ordena(acima)
        segmentos["comFolga"] = ordena(folga)

        val avisos = avisosDePessoas(motor, naoConcluidas, linhas.any { it.horasAssumidas })
        val dto = CargaDto(
            versaoRegras = versaoRegras, jain = Jain.indice(ocupacoes, regras.tetoJain),
            acimaDaCapacidade = pessoasAcima, comFolga = pessoasComFolga,
            tasksAcimaDaCapacidade = segmentos.getValue("acimaDaCapacidade").size, tasksComFolga = segmentos.getValue("comFolga").size,
            linhas = linhas, avisos = avisos,
        )
        return CargaAnalise(dto, segmentos, assumidas)
    }
}
