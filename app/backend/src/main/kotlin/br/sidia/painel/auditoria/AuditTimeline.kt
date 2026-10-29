package br.sidia.painel.auditoria

import br.sidia.painel.domain.StatusCategory
import br.sidia.painel.domain.StatusMap
import br.sidia.painel.domain.UnmappedStatusException
import br.sidia.painel.sync.ChangeEventRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Reconstrói o estado de uma issue em qualquer instante a partir do changelog. O estado inicial de cada
 * campo é o `from` do primeiro evento dele; sem eventos, vale o valor atual da issue. Todas as consultas
 * "em" um instante olham o estado imediatamente ANTES dos eventos daquele instante (eventos com `at` menor).
 * Domínio puro: sem banco, sem rede.
 */
class AuditTimeline(val issue: AuditIssue, private val statusMap: StatusMap) {

    private val eventos: List<ChangeEventRecord> = issue.events.sortedBy { it.at }

    private fun doCampo(campo: String): List<ChangeEventRecord> = eventos.filter { it.field.equals(campo, ignoreCase = true) }

    /** Valor textual do campo antes de [at]: último `to` anterior; senão `from` do primeiro evento; sem eventos, [atual]. */
    private fun valorAntes(campo: String, at: Instant, atual: String?): String? {
        val evs = doCampo(campo)
        if (evs.isEmpty()) return atual
        return evs.lastOrNull { it.at < at }?.to ?: if (evs.any { it.at < at }) null else evs.first().from
    }

    /** Responsável imediatamente antes dos eventos de [at]. */
    fun responsavelEm(at: Instant): String? = valorAntes("assignee", at, issue.assignee)

    /** Vencimento imediatamente antes de [at]; valor em formato ilegível vira nulo (ver [vencimentosIlegiveis]). */
    fun vencimentoEm(at: Instant): LocalDate? {
        val evs = doCampo("duedate")
        if (evs.isEmpty()) return issue.duedate
        return Vencimentos.ler(valorAntes("duedate", at, null))
    }

    /** Fechada antes de [at]: categoria DONE no status ou `resolution` preenchida. Status sem mapa não fecha. */
    fun fechadaEm(at: Instant): Boolean {
        if (!valorAntes("resolution", at, issue.resolution).isNullOrBlank()) return true
        val status = valorAntes("status", at, issue.status) ?: return false
        return try {
            statusMap.category(status) == StatusCategory.DONE
        } catch (_: UnmappedStatusException) {
            false
        }
    }

    /** Início da posse contínua de [responsavelEm] (a criação, se nunca mudou); nulo se não havia responsável. */
    fun posseDesde(at: Instant): Instant? {
        val evs = doCampo("assignee")
        var atual: String? = if (evs.isEmpty()) issue.assignee else evs.first().from
        var desde: Instant = issue.created
        for (e in evs) {
            if (e.at >= at) break
            if (e.to != atual) {
                atual = e.to
                desde = e.at
            }
        }
        return if (atual == null) null else desde
    }

    /** [pessoa] foi responsável (inicial ou por troca) em algum momento antes de [antes]. */
    fun jaTeve(pessoa: String, antes: Instant): Boolean {
        val evs = doCampo("assignee")
        val inicial = if (evs.isEmpty()) issue.assignee else evs.first().from
        if (inicial == pessoa) return true
        return evs.any { it.at < antes && it.to == pessoa }
    }

    private fun emAndamento(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        return try {
            statusMap.category(status) == StatusCategory.ACTIVE
        } catch (_: UnmappedStatusException) {
            false
        }
    }

    /**
     * Quando o trabalho começou: a primeira entrada em categoria ACTIVE (o instante da transição). Se a issue já nasceu em
     * andamento (status inicial ACTIVE), vale a criação. Nulo se nunca esteve em andamento (status fora do mapa não conta).
     */
    fun inicioDoTrabalho(): Instant? {
        val transicoes = doCampo("status")
        val inicial = if (transicoes.isEmpty()) issue.status else transicoes.first().from
        if (emAndamento(inicial)) return issue.created
        return transicoes.firstOrNull { emAndamento(it.to) }?.at
    }

    val trocasDeResponsavel: List<ChangeEventRecord> get() = doCampo("assignee")
    val trocasDeEstimativa: List<ChangeEventRecord> get() = doCampo("timeoriginalestimate")
    val trocasDeVencimento: List<ChangeEventRecord> get() = doCampo("duedate")
    val transicoes: List<ChangeEventRecord> get() = doCampo("status")

    /** Cada evento de labels com o que foi adicionado e removido (labels no DC vêm separadas por espaço). */
    val mudancasDeLabels: List<Pair<ChangeEventRecord, MudancaDeLabels>>
        get() = doCampo("labels").map { e ->
            val antes = separar(e.from)
            val depois = separar(e.to)
            e to MudancaDeLabels(adicionadas = depois - antes, removidas = antes - depois)
        }

    /** Há evento do campo `reporter` (o changelog não guarda o valor, só o nome do campo). */
    val reporterAlterado: Boolean get() = doCampo("reporter").isNotEmpty()

    /** Quantidade de valores de vencimento no changelog que não são data legível. */
    val vencimentosIlegiveis: Int
        get() = doCampo("duedate").sumOf { e ->
            listOf(e.from, e.to).count { !it.isNullOrBlank() && Vencimentos.ler(it) == null }
        }

    private fun separar(texto: String?): Set<String> =
        texto?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet().orEmpty()
}
