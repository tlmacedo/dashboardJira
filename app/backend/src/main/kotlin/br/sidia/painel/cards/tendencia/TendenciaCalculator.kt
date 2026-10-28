package br.sidia.painel.cards.tendencia

import br.sidia.painel.auditoria.AuditTimeline
import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.AvisosDePessoas
import br.sidia.painel.cards.avisosDePessoas
import br.sidia.painel.cards.paraDto
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.pessoas.Ocupacao
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.toAuditIssue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Tendência de ocupação: para cada uma das últimas [SEMANAS] segundas-feiras reconstrói, do changelog, as issues abertas,
 * o responsável e o vencimento DAQUELE instante e aplica a mesma `Demanda` do plano, com as horas de HOJE (não há retrato
 * semanal gravado: aviso obrigatório). Domínio puro, sem ranking: pessoas na ordem recebida (alfabética).
 */
class TendenciaCalculator(private val motor: MotorDePessoas) {
    companion object {
        const val SEMANAS = 12
        private const val ULTIMAS = 4
        private const val RECORRENTE_MINIMO = 3
    }

    private val map = motor.statusMap
    private val zona = motor.calendario.zone

    /**
     * [pessoas]: as da visão que constam na equipe. [issues]: abertas e as resolvidas dentro das 12 semanas, em que alguma
     * pessoa da visão teve posse (para o passado entrar). Só as pessoas de [pessoas] aparecem no resultado.
     */
    fun calcular(pessoas: List<String>, issues: List<IssueSnapshot>, hoje: LocalDate): TendenciaDto {
        val segundaDeHoje = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val segundas = (SEMANAS - 1 downTo 0).map { segundaDeHoje.minusWeeks(it.toLong()) }
        val unicas = issues.distinctBy { it.key }
        val auditorias = unicas.associate { it.key to AuditTimeline(it.toAuditIssue(), map) }

        val ocupacoes = pessoas.associateWith { ArrayList<Ocupacao>(SEMANAS) }
        val demandas = pessoas.associateWith { ArrayList<Double>(SEMANAS) }
        for (segunda in segundas) {
            val instante = segunda.atStartOfDay(zona).toInstant()
            val abertasNaEpoca = unicas.mapNotNull { reconstruida(it, auditorias.getValue(it.key), instante) }
            for (p in pessoas) {
                val semana = motor.demanda.carga(p, abertasNaEpoca, segunda, 1).semanas.first()
                ocupacoes.getValue(p) += semana.ocupacao
                demandas.getValue(p) += semana.demandaH
            }
        }
        val linhas = pessoas.map { p ->
            val o = ocupacoes.getValue(p)
            LinhaTendenciaDto(p, o.mapIndexed { i, x -> x.paraDto(motor.regras, demandas.getValue(p)[i]) }, padrao(o))
        }
        val avisos = avisosDePessoas(motor, unicas.filter { it.resolved == null }, false) + AvisoDto(
            AvisosDePessoas.HORAS_ATUAIS_NO_PASSADO, AvisoDto.INFORMATIVO, "as semanas passadas usam as horas de hoje",
        )
        return TendenciaDto(segundas.map { it.toString() }, linhas, avisos)
    }

    /**
     * A issue como estava em [instante]: nula se ainda não existia ou já estava num status concluído; senão uma cópia com o
     * responsável, o vencimento, o status e as transições daquele momento (o que decide "iniciada" e "concluída"). O "fechada"
     * vem do STATUS da época, não do campo `resolution` (sem evento de resolução no changelog ele valeria o valor de hoje).
     */
    private fun reconstruida(s: IssueSnapshot, auditoria: AuditTimeline, instante: Instant): IssueSnapshot? {
        if (s.created > instante) return null
        val transicoes = s.timeline.transitions.filter { it.at < instante }.sortedBy { it.at }
        val status = transicoes.lastOrNull()?.to ?: s.timeline.initialStatus
        if (map.category(status) == StatusCategory.DONE) return null
        return s.copy(
            assignee = auditoria.responsavelEm(instante), duedate = auditoria.vencimentoEm(instante), status = status,
            resolved = null, resolution = null, timeline = s.timeline.copy(transitions = transicoes),
        )
    }

    /** `RECORRENTE` com [RECORRENTE_MINIMO] ou mais das últimas [ULTIMAS] semanas acima do limite alto; `PONTUAL` com 1 ou 2. */
    fun padrao(ocupacoes: List<Ocupacao>): String {
        val acima = ocupacoes.takeLast(ULTIMAS).count { it is Ocupacao.Valor && it.fracao > motor.regras.ocupacaoAlta }
        return when {
            acima >= RECORRENTE_MINIMO -> "RECORRENTE"
            acima >= 1 -> "PONTUAL"
            else -> "NORMAL"
        }
    }
}
