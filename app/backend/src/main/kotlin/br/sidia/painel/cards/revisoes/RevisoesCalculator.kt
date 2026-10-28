package br.sidia.painel.cards.revisoes

import br.sidia.painel.domain.ORDEM_NATURAL_DE_CHAVES
import br.sidia.painel.domain.Percentiles
import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.trabalho.IssueSnapshot
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** O DTO e as tasks por trás dos números (`revisor:<id>:<pendentes|vencidas|foraDoSla>` e `semRevisor`). */
class RevisoesAnalise(val dto: RevisoesDto, val segmentos: Map<String, List<IssueSnapshot>>)

/**
 * Revisões pendentes por revisor: as abertas dos tipos de review ([tiposReview], vindos de `vinculos.yaml`, sem diferenciar
 * maiúsculas) agrupadas pelo responsável. Vencida = vencimento antes de hoje; fora do prazo = idade em DIAS ÚTEIS desde a criação
 * maior que `slaRevisaoDias` do planejamento. Sem ranking: revisores em ordem alfabética. Domínio puro.
 */
class RevisoesCalculator(private val motor: MotorDePessoas, tiposReview: Set<String>) {
    private val tipos: Set<String> = tiposReview.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    fun calcular(visiveis: Set<String>, abertas: List<IssueSnapshot>, hoje: LocalDate, agora: Instant, veSemRevisor: Boolean): RevisoesAnalise {
        val reviews = abertas.filter {
            it.issueType.trim().lowercase(Locale.ROOT) in tipos && motor.statusMap.category(it.status) != StatusCategory.DONE
        }.sortedWith(compareBy(ORDEM_NATURAL_DE_CHAVES) { it.key })
        val idade = { s: IssueSnapshot -> if (agora.isAfter(s.created)) motor.calendario.businessDays(s.created, agora) else 0.0 }

        val segmentos = LinkedHashMap<String, List<IssueSnapshot>>()
        val linhas = reviews.filter { it.assignee in visiveis }.groupBy { it.assignee!! }.toSortedMap().map { (revisor, dele) ->
            val vencidas = dele.filter { it.duedate?.isBefore(hoje) == true }
            val fora = dele.filter { idade(it) > motor.regras.slaRevisaoDias }
            segmentos["revisor:$revisor:pendentes"] = dele
            segmentos["revisor:$revisor:vencidas"] = vencidas
            segmentos["revisor:$revisor:foraDoSla"] = fora
            RevisorDto(revisor, dele.size, vencidas.size, fora.size, Percentiles.median(dele.map(idade)))
        }
        val semRevisor = if (veSemRevisor) reviews.filter { it.assignee == null } else null
        if (semRevisor != null) segmentos["semRevisor"] = semRevisor
        return RevisoesAnalise(RevisoesDto(linhas, semRevisor?.size, emptyList()), segmentos)
    }
}
